package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.events.core.onReceive
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.misc.FloydCompatibility
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import gg.floyd.utils.font.MsdfFontMetrics
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.HudTextRenderer
import gg.floyd.utils.render.PanelBlurPIPRenderer
import gg.floyd.utils.render.RoundRectPIPRenderer
import gg.floyd.utils.ui.rendering.PostHudOverlay
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.*
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Standalone toggle for the Floyd custom scoreboard.
 *
 * When enabled, the vanilla sidebar is replaced by this module's rounded, gradient-bordered
 * scoreboard. Text renders through the global mc.font (the MSDF custom-font provider), via the
 * shared [HudTextRenderer] world-end helper, so it inherits per-codepoint glyph fallback and the
 * exact float advances the layout measures with ([MsdfFontMetrics]).
 *
 * Previously this was a buried `Custom Scoreboard` BooleanSetting inside [FloydRender]; it is now
 * its own module and owns the movable scoreboard HUD element. All cosmetics (background, border
 * color/chroma/fade, corner radius, border width and internal padding) come from the global
 * [FloydPanelStyle] via [HudPanel], so the scoreboard matches every other Floyd panel.
 */
object FloydCustomScoreboard : Module(
    name = "Custom Scoreboard",
    category = Category.RENDER,
    description = "Replaces the vanilla scoreboard with Floyd's rounded, gradient-bordered HUD scoreboard.",
    toggled = false,
) {
    private const val SCOREBOARD_FONT_SIZE = 9f
    // Per-letter phase step for the "FloydAddons" brand line so it sweeps left→right letter-by-letter
    // using the panel's OWN border color (chroma rainbow / base↔fade gradient / flat solid).
    private const val BRAND_LETTER_PHASE_STEP = 0.04f
    private val vanillaScoreboardWouldRender = AtomicBoolean(false)

    // toggleable = false: the module toggle is the single on/off (no redundant inner toggle).
    private val scoreboardHud by HUD("Scoreboard HUD", "Displays a movable Floyd-styled scoreboard.", false, 10, 80, 1f) { example ->
        drawScoreboardHud(example)
    }

    init {
        // The editor drag box must match the rendered panel, so the estimate comes from the same
        // layout (and therefore the same MsdfFontMetrics widths) the world-end render draws with.
        HudSizeRegistry.register("Scoreboard HUD") {
            scoreboardRender(example = true, requireVanillaSignal = false)
                ?.let { it.boxWidth to it.boxHeight } ?: (180 to 120)
        }

        // Layout-cache invalidation: every packet that can change sidebar content/teams/display.
        // These fire on the Netty thread (see the rebuild window in the cache policy above).
        onReceive<ClientboundSetScorePacket> { invalidateLayout() }
        onReceive<ClientboundResetScorePacket> { invalidateLayout() }
        onReceive<ClientboundSetObjectivePacket> { invalidateLayout() }
        onReceive<ClientboundSetDisplayObjectivePacket> { invalidateLayout() }
        onReceive<ClientboundSetPlayerTeamPacket> { invalidateLayout() }
    }

    override fun onEnable() {
        super.onEnable()
        cachedLayoutEpoch = -1L
        cachedLayout = null
    }

    @JvmStatic
    @JvmOverloads
    fun shouldUseCustomScoreboard(moduleEnabled: Boolean = enabled): Boolean = moduleEnabled

    fun state(): Map<String, Any?> {
        val objective = sidebarObjective()
        return mapOf(
            "enabled" to enabled,
            "shouldUseCustomScoreboard" to shouldUseCustomScoreboard(),
            "scoreboardHud" to mapOf(
                "enabled" to (shouldUseCustomScoreboard() && scoreboardHud.enabled),
                "sidebarObjective" to objective?.name,
                "vanillaWouldRender" to vanillaScoreboardWouldRender.get(),
                "wouldRender" to shouldDrawScoreboardHud(
                    example = false,
                    customScoreboard = shouldUseCustomScoreboard(),
                    objectivePresent = objective != null,
                    consumeVanillaSignal = false
                ),
                "x" to scoreboardHud.x,
                "y" to scoreboardHud.y,
                "hudScale" to scoreboardHud.scale
            ),
            "cornerRadius" to FloydPanelStyle.cornerRadiusFor(FloydPanelStyle.PanelTarget.SCOREBOARD)
        )
    }

    @JvmStatic
    fun markVanillaScoreboardWouldRender() {
        vanillaScoreboardWouldRender.set(true)
    }

    @JvmStatic
    fun resetVanillaScoreboardWouldRender() {
        vanillaScoreboardWouldRender.set(false)
    }

    internal fun shouldDrawScoreboardHud(
        example: Boolean,
        customScoreboard: Boolean,
        objectivePresent: Boolean,
        moduleEnabled: Boolean = enabled,
        hudEnabled: Boolean = scoreboardHud.enabled,
        consumeVanillaSignal: Boolean = true
    ): Boolean {
        if (example) return objectivePresent
        if (!moduleEnabled || !hudEnabled || !customScoreboard || !objectivePresent) {
            if (consumeVanillaSignal) vanillaScoreboardWouldRender.set(false)
            return false
        }
        return vanillaScoreboardWouldRender.get()
    }

    /**
     * [brandGlyphs] is kept so the brand line's letter-by-letter chroma can be RECOLORED per frame
     * from the cached layout without re-running the full layout (the brand element is always
     * [texts].last()); width is color-independent, so geometry stays valid.
     */
    private data class ScoreboardRender(val boxWidth: Int, val boxHeight: Int, val texts: List<ScoreboardText>, val brandGlyphs: List<String>)

    // ---- Layout cache -----------------------------------------------------------------------------
    // The full layout (sort + per-glyph normalization/String building + width measurement) used to run
    // TWICE per frame (world-end draw + HUD-editor size callback): measured 459µs + 233KB per frame in
    // the world-end pass alone. Sidebar content only changes on scoreboard/team packets, so the layout
    // is cached and invalidated by a packet-driven epoch. Packet events fire on the NETTY thread
    // before the main thread applies the data, so an epoch bump also opens a short rebuild window
    // (REBUILD_WINDOW_MS) to catch the apply landing a frame later; a 1 Hz fallback rebuild covers
    // rare driftwithout packets (nick-hider/replacer config edits).
    private val layoutEpoch = java.util.concurrent.atomic.AtomicLong()
    private var cachedLayoutEpoch = -1L
    private var cachedLayout: ScoreboardRender? = null
    // The Font the cached layout was measured with: a per-panel font toggle swaps the instance
    // (FloydFont.panelFont), and the cached widths must never be drawn with a different font.
    private var cachedLayoutFont: net.minecraft.client.gui.Font? = null
    private var lastLayoutMs = 0L
    private var rebuildWindowEndMs = 0L
    // 500ms: the Netty-thread epoch bump precedes the main-thread packet apply; on a laggy
    // integrated/remote server the apply can land >150ms later, which would have left stale lines
    // until the 1Hz fallback. Rebuilds are ~0.4ms, so a generous window is nearly free.
    private const val REBUILD_WINDOW_MS = 500L
    private const val FALLBACK_REBUILD_MS = 1000L

    private fun invalidateLayout() {
        layoutEpoch.incrementAndGet()
    }

    /**
     * Pure layout (NO GuiGraphics, no drawing) so it can run screen-independently from the world-end
     * pass. Returns null when there is nothing to draw; with [example] true, returns placeholder content
     * for the HUD editor.
     */
    private fun scoreboardRender(example: Boolean, requireVanillaSignal: Boolean = true): ScoreboardRender? {
        val objective = sidebarObjective() ?: return if (example) exampleScoreboardRender() else null
        // The vanilla-would-render signal is set during the HUD render, which is skipped while a screen is
        // open — so the world-end path (renderAtWorldEnd) gates only on module-enabled + objective so the
        // scoreboard stays visible with chat / inventory / the ClickGUI open.
        val show = if (example) true
            else if (requireVanillaSignal) shouldDrawScoreboardHud(false, shouldUseCustomScoreboard(), objectivePresent = true)
            else shouldUseCustomScoreboard()
        if (!show) return null
        val scoreboard = mc.level?.scoreboard ?: return if (example) exampleScoreboardRender() else null
        val lines = mc.level?.scoreboard?.listPlayerScores(objective)
            ?.asSequence()
            ?.filterNot(PlayerScoreEntry::isHidden)
            ?.sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() })
            ?.take(15)
            ?.map { entry ->
                val team = scoreboard.getPlayersTeam(entry.owner())
                val name = styledText(PlayerTeam.formatNameForTeam(team, entry.ownerName()).visualOrderText)
                val score = styledText(entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)).visualOrderText)
                ScoreLine(name, score, textWidth(name), textWidth(score))
            }
            ?.toMutableList()
            ?: return if (example) exampleScoreboardRender() else null

        if (lines.isEmpty()) return if (example) exampleScoreboardRender() else null

        return buildScoreboardLayout(objective.displayName, Component.literal("FloydAddons"), lines)
    }

    private fun exampleScoreboardRender(): ScoreboardRender =
        buildScoreboardLayout(
            Component.literal("SKYBLOCK"), Component.literal("FloydAddons"),
            mutableListOf(scoreLine("Purse: 1,234,567"), scoreLine("Bits: 12,345"), scoreLine("Location: Dungeon Hub"))
        )

    private fun buildScoreboardLayout(title: Component, brand: Component, lines: List<ScoreLine>): ScoreboardRender {
        val titleText = styledText(title.visualOrderText)
        // The Floyd brand line sweeps left→right letter-by-letter using the panel's OWN border color
        // (chroma -> rainbow flow, fade -> base↔fade gradient, solid -> flat color), so it always matches
        // the configured scoreboard border. Each letter samples the border accent at a phase offset by its
        // visual index; when the border is solid the offset is ignored so every letter is the same color.
        // The glyphs are kept on the layout so the draw path can RECOLOR them per frame from the cache.
        val brandGlyphs = collectGlyphs(brand.visualOrderText)
        val brandText = brandSegments(brandGlyphs)
        val titleWidth = textWidth(titleText)
        val brandWidth = textWidth(brandText)
        // The right-hand score-number column is intentionally omitted; size the box to the names only.
        var maxLineWidth = max(titleWidth, brandWidth)
        for (line in lines) {
            maxLineWidth = max(maxLineWidth, line.nameWidth)
        }

        val padding = FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.SCOREBOARD).coerceAtLeast(0)
        val fontSize = SCOREBOARD_FONT_SIZE
        val lineHeight = ceil(fontSize + 1f).toInt().coerceAtLeast(9)
        val titlePad = 2
        val boxWidth = ceil(maxLineWidth + padding * 2).toInt()
        // Server objective title sits on top; the Floyd brand line is anchored to the very bottom.
        val titleY = padding + titlePad
        val headerHeight = titleY + lineHeight + titlePad
        val brandY = headerHeight + lines.size * lineHeight + titlePad
        val boxHeight = brandY + lineHeight + padding

        val textElements = ArrayList<ScoreboardText>(lines.size + 2)
        textElements += ScoreboardText(titleText, (boxWidth - titleWidth) / 2f, titleY.toFloat())

        var lineY = headerHeight
        for (line in lines) {
            textElements += ScoreboardText(line.name, padding.toFloat(), lineY.toFloat())
            lineY += lineHeight
        }

        // Brand is ALWAYS the last element — the cached-draw path relies on this to recolor it.
        textElements += ScoreboardText(brandText, (boxWidth - brandWidth) / 2f, brandY.toFloat())

        return ScoreboardRender(boxWidth, boxHeight, textElements, brandGlyphs)
    }

    /** The normalized glyph strings of [text] (the same normalization [styledText] applies), colorless. */
    private fun collectGlyphs(text: FormattedCharSequence): List<String> {
        val glyphs = ArrayList<String>()
        CustomNameReplacer.replaceSequenceIfNeeded(text).accept { _, _, codePoint ->
            glyphs.add(normalizeForScoreboardFont(codePoint) ?: String(Character.toChars(codePoint)))
            true
        }
        return glyphs
    }

    /**
     * Builds the brand line's per-letter chroma segments from cached glyphs — the per-frame recolor
     * path (same merge logic as [styledText], same accent sampling as the original forcedColorAt).
     */
    private fun brandSegments(glyphs: List<String>): StyledScoreboardText {
        return StyledScoreboardText(scoreboardBrandSegments(glyphs) { index ->
            scoreboardAccentColor(index * BRAND_LETTER_PHASE_STEP)
        })
    }

    /**
     * Keeps the brand's text-run boundaries invariant while fade/chroma colors animate. Merging
     * equal adjacent colors changes kerning at fade endpoints and makes later glyphs visibly jump.
     */
    internal fun scoreboardBrandSegments(
        glyphs: List<String>,
        colorAt: (Int) -> Int,
    ): List<HudTextRenderer.Segment> = glyphs.mapIndexed { index, glyph ->
        HudTextRenderer.Segment(glyph, colorAt(index))
    }

    // HUD element callback: the default path only reports the panel's size because the real draw happens
    // from the world-end inline pass. In safe HUD-layer mode (used for SkyHanni compatibility), it
    // draws the scoreboard here instead so Floyd stays off the shared post-world override path.
    private fun GuiGraphics.drawScoreboardHud(example: Boolean): Pair<Int, Int> {
        val r = when {
            example -> scoreboardRender(example = true, requireVanillaSignal = false)
            else -> cachedLayout ?: scoreboardRender(example = false, requireVanillaSignal = false)
        } ?: return 0 to 0
        drawScoreboardGui(r)
        return r.boxWidth to r.boxHeight
    }

    /** Deferred GuiGraphics path used by Minecraft 26.1 for both editor previews and the live HUD. */
    private fun GuiGraphics.drawScoreboardGui(r: ScoreboardRender) {
        val target = FloydPanelStyle.PanelTarget.SCOREBOARD
        val multiplier = mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio()
        NVGPIPRenderer.draw(this, 0, 0, r.boxWidth, r.boxHeight, multiplier, localCoordinates = true, backdropBlur = HudPanel.nvgBlur(r.boxWidth, r.boxHeight, target)) {
            HudPanel.drawNvgPanel(
                r.boxWidth,
                r.boxHeight,
                target,
                HudPanel.panelBorderColors(target, scoreboardHud.x, scoreboardHud.y),
            )
        }

        // Match the working 1.21.11 renderer: NanoVG owns only the panel surface. Text must go
        // through Minecraft's Font pipeline so explicit Hypixel sprite-font ids survive. Sending
        // these runs through NanoVG renders private-use icons as narrow bars from the TTF instead.
        val font = panelFont()
        fun drawLine(text: ScoreboardText, segments: List<HudTextRenderer.Segment>) {
            var segmentX = text.x
            for (segment in segments) {
                val formatted = segment.formatted()
                drawString(font, formatted, segmentX.roundToInt(), text.y.roundToInt(), segment.color, false)
                segmentX += MsdfFontMetrics.unitWidth(formatted, font)
            }
        }
        for (i in 0 until r.texts.size - 1) {
            val text = r.texts[i]
            drawLine(text, text.value.segments)
        }
        val brand = r.texts.last()
        drawLine(brand, brandSegments(r.brandGlyphs).segments)
    }

    /**
     * The single scoreboard render — drawn directly to the main framebuffer from the world-end post-HUD
     * pass (PostHudOverlay), both in game AND while the HUD editor is open (the editor just drags it). Uses
     * the HUD element's own framebuffer-pixel position/scale (no GuiGraphics pose), so it shows regardless
     * of any open screen. In the editor it renders the example layout when there's no live sidebar.
     */
    fun renderAtWorldEnd() {
        val editor = mc.screen === gg.floyd.clickgui.HudManager
        if (editor) {
            // Editor preview (example layout when no live sidebar): uncached — transient screen.
            val r = scoreboardRender(example = true, requireVanillaSignal = false) ?: return
            drawScoreboardInline(r, allowBlur = true)
            return
        }
        // The enabled gate used to live inside the per-frame scoreboardRender; with the cache it
        // must be HERE or a disable keeps ghost-drawing the stale layout (alongside the returning
        // vanilla sidebar) until the 1 Hz fallback. Also releases the cached layout on disable.
        if (!shouldUseCustomScoreboard()) {
            cachedLayout = null
            cachedLayoutEpoch = -1L
            cachedLayoutFont = null
            return
        }
        val now = System.currentTimeMillis()
        val epoch = layoutEpoch.get()
        val font = panelFont()
        if (epoch != cachedLayoutEpoch || font !== cachedLayoutFont || now < rebuildWindowEndMs || now - lastLayoutMs > FALLBACK_REBUILD_MS) {
            if (epoch != cachedLayoutEpoch) rebuildWindowEndMs = now + REBUILD_WINDOW_MS
            cachedLayoutEpoch = epoch
            cachedLayoutFont = font
            lastLayoutMs = now
            cachedLayout = scoreboardRender(example = false, requireVanillaSignal = false)
        }
        drawScoreboardInline(cachedLayout ?: return, allowBlur = true)
    }

    private fun scoreLine(name: String): ScoreLine {
        val text = styledText(Component.literal(name).visualOrderText)
        return ScoreLine(text, StyledScoreboardText.EMPTY, textWidth(text), 0f)
    }

    /** The per-toggle font selection (custom vs pinned vanilla); layout and draw must agree. */
    private fun panelFont() = FloydFont.panelFont(FloydFont.PanelFont.SCOREBOARD)

    private fun textWidth(text: String): Float =
        if (FloydFont.usesCustomFont(FloydFont.PanelFont.SCOREBOARD)) {
            NVGRenderer.textWidth(text, SCOREBOARD_FONT_SIZE, NVGRenderer.activeFont())
        } else {
            MsdfFontMetrics.width(text, SCOREBOARD_FONT_SIZE, panelFont())
        }

    private fun textWidth(text: StyledScoreboardText): Float {
        var width = 0f
        val font = panelFont()
        for (segment in text.segments) {
            width += MsdfFontMetrics.unitWidth(segment.formatted(), font) * (SCOREBOARD_FONT_SIZE / MsdfFontMetrics.LINE_HEIGHT)
        }
        return width
    }

    /**
     * In-game render, drawn DIRECTLY to the main framebuffer from the world-end pass (no PIP, no
     * GuiGraphics). Geometry comes from the HUD element's own framebuffer-pixel x/y/scale, so it is
     * screen-independent (renders with any screen open). Background (SDF fill/border + frosted blur)
     * and the mc.font text all draw in framebuffer-pixel space; [HudTextRenderer] re-applies the
     * screen projection and re-binds the FBO around its batch because a blaze3d render pass can
     * retarget.
     */
    private fun drawScoreboardInline(r: ScoreboardRender, allowBlur: Boolean) {
        val texts = r.texts
        val target = FloydPanelStyle.PanelTarget.SCOREBOARD
        val scale = scoreboardHud.scale
        val fw = r.boxWidth * scale
        val fh = r.boxHeight * scale
        // Keep the panel inside the visible framebuffer: sidebar content changes width per line
        // (locations, growing scores), and a panel parked near the right/bottom edge used to grow
        // straight off-screen. Clamping the DRAW position pins the on-screen edge so the panel
        // expands toward the free space instead; the stored position is untouched, so it returns
        // to where the user dragged it once the content shrinks back. (The HUD editor's drag box
        // keeps the stored position — only the rendered panel shifts, and only while it would
        // otherwise overflow.)
        val fx = scoreboardHud.x.toFloat().coerceAtMost(mc.window.width - fw).coerceAtLeast(0f)
        val fy = scoreboardHud.y.toFloat().coerceAtMost(mc.window.height - fh).coerceAtLeast(0f)

        val fill = FloydPanelStyle.backgroundColorFor(target).rgba
        // STORED position, not the clamped fx/fy: the brand line's per-letter chroma
        // (scoreboardAccentColor) phases off the stored position too, and the two must stay
        // locked or the border and brand sweep out of sync while the panel is edge-clamped.
        val border = HudPanel.panelBorderColors(target, scoreboardHud.x, scoreboardHud.y)
        val radius = FloydPanelStyle.cornerRadiusFor(target).toFloat() * scale
        val outline = FloydPanelStyle.borderWidthFor(target).toFloat() * scale

        // Frosted blur backdrop (samples the per-frame framebuffer snapshot), then the rounded fill+border.
        if (allowBlur && FloydPanelStyle.blurFor(target)) {
            val blurRadius = FloydPanelStyle.blurStrengthFor(target).coerceIn(0, 20) * 0.4f
            if (blurRadius >= 0.5f) {
                PanelBlurPIPRenderer.drawInline(fx, fy, fw, fh, radius, radius, radius, radius, blurRadius, FloydPanelStyle.blurIsBoxFor(target))
                PostHudOverlay.bindMainFbo()
            }
        }
        RoundRectPIPRenderer.drawInline(
            fx, fy, fw, fh,
            fill, fill, fill, fill,
            radius, radius, radius, radius,
            border.topLeft, border.topRight, border.bottomRight, border.bottomLeft,
            outline
        )

        // Text via the shared mc.font helper, all queued into ONE batch flush (this loop used to
        // endBatch+rebind per line — ~17 flushes/frame). Layout positions are in local panel units
        // (= 9px font units, SCOREBOARD_FONT_SIZE = 9), so one font unit maps to [scale] framebuffer
        // px. The brand line (always last) is recolored per frame from the cached glyphs so its
        // letter-by-letter chroma keeps animating while the layout itself stays cached.
        val font = panelFont()
        for (i in 0 until texts.size - 1) {
            val text = texts[i]
            HudTextRenderer.drawSegmentsDeferred(text.value.segments, fx + text.x * scale, fy + text.y * scale, scale, font = font)
        }
        val brand = texts.last()
        HudTextRenderer.drawSegmentsDeferred(brandSegments(r.brandGlyphs).segments, fx + brand.x * scale, fy + brand.y * scale, scale, font = font)
        HudTextRenderer.flushDeferred()
    }

    internal fun scoreboardSegments(text: FormattedCharSequence): List<HudTextRenderer.Segment> =
        styledText(text).segments

    private fun styledText(text: FormattedCharSequence, forcedColor: Int? = null, forcedColorAt: ((Int) -> Int)? = null): StyledScoreboardText {
        // Apply Neck/Nick Hider + server/profile-id replacement to the scoreboard text up front. The
        // draw path re-passes through FontMixin (Font.prepareText) where the replacement is idempotent,
        // but the LAYOUT widths are measured via MsdfFontMetrics/StringSplitter, which FontMixin does
        // not hook — so without this upfront pass the box would be sized for the unreplaced text.
        val source = CustomNameReplacer.replaceSequenceIfNeeded(text)
        // Collect every glyph with its resolved color. No glyph is ever dropped: fancy unicode is
        // NFKC/small-caps-normalized down to ASCII where possible (so it renders in the smooth custom
        // font); anything else passes through unchanged and renders via mc.font's per-codepoint
        // fallback (unifont), measured with exactly the advances it renders with.
        val glyphs = ArrayList<String>()
        val colors = ArrayList<Int>()
        val styles = ArrayList<Style>()
        source.accept { _, style, codePoint ->
            glyphs.add(normalizeForScoreboardFont(codePoint) ?: String(Character.toChars(codePoint)))
            // forcedColorAt colors per visual glyph index (left→right) for the brand's letter-by-letter chroma.
            colors.add(forcedColorAt?.invoke(colors.size) ?: forcedColor ?: scoreboardStyleColor(style))
            styles.add(style)
            true
        }
        if (glyphs.isEmpty()) return StyledScoreboardText(emptyList())

        val segments = mutableListOf<HudTextRenderer.Segment>()
        val currentText = StringBuilder()
        var currentColor = colors[0]
        var currentStyle = styles[0]
        fun flush() {
            if (currentText.isEmpty()) return
            segments += HudTextRenderer.Segment(currentText.toString(), currentColor, currentStyle)
            currentText.clear()
        }
        for (i in glyphs.indices) {
            if (currentText.isNotEmpty() && (colors[i] != currentColor || styles[i] != currentStyle)) flush()
            currentColor = colors[i]
            currentStyle = styles[i]
            currentText.append(glyphs[i])
        }
        flush()
        return StyledScoreboardText(segments)
    }

    private fun scoreboardStyleColor(style: Style): Int {
        val color = style.color?.value ?: return 0xFFFFFFFF.toInt()
        return 0xFF000000.toInt() or (color and 0x00FFFFFF)
    }

    /**
     * The ASCII string a source code point normalizes to (so it renders in the smooth custom font),
     * or null when it has no ASCII form. ASCII passes through unchanged; non-ASCII is NFKC-normalized
     * (full-width digits, ligatures, etc.) and accepted only when the result is pure ASCII.
     * Normalize-only: nothing is ever dropped — a code point that cannot be normalized passes through
     * verbatim and renders via mc.font's per-codepoint fallback (unifont).
     */
    private fun normalizeForScoreboardFont(codePoint: Int): String? {
        if (codePoint in 0x20..0x7E) return String(Character.toChars(codePoint))
        // Latin "small caps" Unicode (servers love these in titles, e.g. ᴀɴᴛɪᴄʜᴇᴀᴛ) have no NFKC ASCII
        // decomposition and would fall back to unifont, so map them to their plain letter — the title
        // then renders in the smooth custom font like everything else.
        SMALL_CAPS_TO_ASCII[codePoint]?.let { return it.toString() }
        val normalized = runCatching {
            Normalizer.normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFKC)
        }.getOrNull()
        if (!normalized.isNullOrEmpty() && normalized.all { it.code in 0x20..0x7E }) return normalized
        return null
    }

    private val SMALL_CAPS_TO_ASCII: Map<Int, Char> = mapOf(
        0x1D00 to 'A', 0x0299 to 'B', 0x1D04 to 'C', 0x1D05 to 'D', 0x1D07 to 'E',
        0xA730 to 'F', 0x0262 to 'G', 0x029C to 'H', 0x026A to 'I', 0x1D0A to 'J',
        0x1D0B to 'K', 0x029F to 'L', 0x1D0D to 'M', 0x0274 to 'N', 0x1D0F to 'O',
        0x1D18 to 'P', 0xA7AF to 'Q', 0x0280 to 'R', 0xA731 to 'S', 0x1D1B to 'T',
        0x1D1C to 'U', 0x1D20 to 'V', 0x1D21 to 'W', 0x028F to 'Y', 0x1D22 to 'Z'
    )

    private fun sidebarObjective(): Objective? {
        val player = mc.player ?: return null
        val scoreboard = mc.level?.scoreboard ?: return null
        val team = scoreboard.getPlayersTeam(player.scoreboardName)
        val teamObjective = team?.color?.let { gg.floyd.utils.teamDisplaySlot(it) }?.let(scoreboard::getDisplayObjective)
        return teamObjective ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    /** Title/footer accent: the global panel border color (chroma/fade per [FloydPanelStyle]). */
    private fun scoreboardAccentColor(offset: Float): Int =
        HudPanel.accentColor(FloydPanelStyle.borderColorFor(FloydPanelStyle.PanelTarget.SCOREBOARD),
            HudPanel.offsetPhase(HudPanel.hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f), offset))

    private data class ScoreLine(val name: StyledScoreboardText, val score: StyledScoreboardText, val nameWidth: Float, val scoreWidth: Float)
    private data class ScoreboardText(val value: StyledScoreboardText, val x: Float, val y: Float)
    private data class StyledScoreboardText(val segments: List<HudTextRenderer.Segment>) {
        companion object { val EMPTY = StyledScoreboardText(emptyList()) }
    }
}
