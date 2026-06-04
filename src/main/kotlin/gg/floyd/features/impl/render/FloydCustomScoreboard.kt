package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.PanelBlurPIPRenderer
import gg.floyd.utils.render.RoundRectPIPRenderer
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import gg.floyd.utils.ui.rendering.PanelPhase
import gg.floyd.utils.ui.rendering.PostHudOverlay
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import org.joml.Matrix3x2f
import org.joml.Matrix4f
import org.joml.Vector2f
import net.minecraft.client.gui.GuiGraphics
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
 * scoreboard, rendered through the global custom font (vanilla [net.minecraft.client.gui.Font]),
 * so it inherits color pass-through, glyph fallback, and overlay blur for free.
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

    // Default OFF = Floyd's smooth NanoVG font. Safe from the old multi-PIP flicker because the
    // scoreboard is now the only in-game NVG PIP (day tracker + inventory counts stay on mc.font).
    private val scoreboardHudMinecraftFont by BooleanSetting("Scoreboard Minecraft Font", false, desc = "Uses Minecraft's default font instead of Floyd's smooth NanoVG font for scoreboard text.")

    // toggleable = false: the module toggle is the single on/off (no redundant inner toggle).
    private val scoreboardHud by HUD("Scoreboard HUD", "Displays a movable Floyd-styled scoreboard.", false, 10, 80, 1f) { example ->
        drawScoreboardHud(example)
    }

    init {
        HudSizeRegistry.register("Scoreboard HUD") { 180 to 120 }
    }

    @JvmStatic
    fun shouldUseCustomScoreboard(): Boolean = enabled

    fun state(): Map<String, Any?> {
        val objective = sidebarObjective()
        return mapOf(
            "enabled" to enabled,
            "shouldUseCustomScoreboard" to shouldUseCustomScoreboard(),
            "scoreboardHud" to mapOf(
                "enabled" to (enabled && scoreboardHud.enabled),
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
                "hudScale" to scoreboardHud.scale,
                "minecraftFont" to useMinecraftScoreboardFont()
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

    private data class ScoreboardRender(val boxWidth: Int, val boxHeight: Int, val texts: List<ScoreboardText>)

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
            else enabled
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
        val brandText = styledText(brand.visualOrderText, forcedColorAt = { i -> scoreboardAccentColor(i * BRAND_LETTER_PHASE_STEP) })
        val titleWidth = textWidth(titleText)
        val brandWidth = textWidth(brandText)
        // The right-hand score-number column is intentionally omitted; size the box to the names only.
        var maxLineWidth = max(titleWidth, brandWidth)
        for (line in lines) {
            maxLineWidth = max(maxLineWidth, line.nameWidth)
        }

        val padding = FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.SCOREBOARD).coerceAtLeast(0)
        val fontSize = scoreboardTextHeight()
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

        textElements += ScoreboardText(brandText, (boxWidth - brandWidth) / 2f, brandY.toFloat())

        return ScoreboardRender(boxWidth, boxHeight, textElements)
    }

    // HUD element callback: this NEVER draws — it only reports the panel's size so the HUD editor can size
    // its drag box. Both in game AND in the editor the actual panel is drawn by the single inline pass
    // [renderAtWorldEnd] (one rendering system everywhere, so fonts/blur/borders are identical and
    // overlapping editor panels never clobber — the old deferred GuiGraphics/PIP preview is gone).
    private fun GuiGraphics.drawScoreboardHud(example: Boolean): Pair<Int, Int> {
        val r = scoreboardRender(example) ?: return 0 to 0
        return r.boxWidth to r.boxHeight
    }

    /**
     * The single scoreboard render — drawn directly to the main framebuffer from the world-end post-HUD
     * pass (PostHudOverlay), both in game AND while the HUD editor is open (the editor just drags it). Uses
     * the HUD element's own framebuffer-pixel position/scale (no GuiGraphics pose), so it shows regardless
     * of any open screen. In the editor it renders the example layout when there's no live sidebar.
     */
    fun renderAtWorldEnd(phase: PanelPhase) {
        val editor = mc.screen === gg.floyd.clickgui.HudManager
        val r = scoreboardRender(example = editor, requireVanillaSignal = false) ?: return
        drawScoreboardInline(r.boxWidth, r.boxHeight, r.texts, phase)
    }

    private fun scoreLine(name: String): ScoreLine {
        val text = styledText(Component.literal(name).visualOrderText)
        return ScoreLine(text, StyledScoreboardText.EMPTY, textWidth(text), 0f)
    }

    private fun textWidth(text: String): Float =
        if (useMinecraftScoreboardFont()) mc.font.width(text).toFloat()
        else NVGRenderer.textWidth(text, scoreboardFontSize(), NVGRenderer.activeFont())

    private fun textWidth(text: StyledScoreboardText): Float {
        var width = 0f
        for (segment in text.segments) {
            width += if (useMinecraftScoreboardFont() || segment.minecraftFont) {
                mc.font.width(segment.text).toFloat()
            } else {
                NVGRenderer.textWidth(segment.text, scoreboardFontSize(), NVGRenderer.activeFont())
            }
        }
        return width
    }

    private fun scoreboardFontSize(): Float = SCOREBOARD_FONT_SIZE

    private fun scoreboardTextHeight(): Float =
        if (useMinecraftScoreboardFont()) mc.font.lineHeight.toFloat() else scoreboardFontSize()

    /** Editor-preview / Minecraft-font path: the original deferred PIP render (GuiGraphics-based). */
    private fun GuiGraphics.drawScoreboardDeferred(boxWidth: Int, boxHeight: Int, texts: List<ScoreboardText>) {
        HudPanel.fillPanel(this, 0, 0, boxWidth, boxHeight, FloydPanelStyle.PanelTarget.SCOREBOARD, HudPanel.panelBorderColors(FloydPanelStyle.PanelTarget.SCOREBOARD, scoreboardHud.x, scoreboardHud.y))
        if (useMinecraftScoreboardFont()) {
            for (text in texts) drawMinecraftScoreboardText(text)
        } else {
            val nvgScaleMultiplier = mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio()
            NVGPIPRenderer.draw(this, 0, 0, boxWidth, boxHeight, renderScaleMultiplier = nvgScaleMultiplier) {
                for (text in texts) drawScoreboardText(text)
            }
            for (text in texts) drawScoreboardMinecraftFallbacks(text)
        }
    }

    /**
     * In-game render, drawn DIRECTLY to the main framebuffer from the world-end pass (no PIP, no
     * GuiGraphics). Geometry comes from the HUD element's own framebuffer-pixel x/y/scale, so it is
     * screen-independent (renders with any screen open). SDF bg/border draw in framebuffer space; NanoVG
     * + mc.font draw in logical (/dpr) space — the FBO is re-bound between them because the SDF blaze3d
     * render pass can retarget.
     */
    private fun drawScoreboardInline(boxWidth: Int, boxHeight: Int, texts: List<ScoreboardText>, phase: PanelPhase) {
        val target = FloydPanelStyle.PanelTarget.SCOREBOARD
        val dpr = NVGRenderer.devicePixelRatio()
        val scale = scoreboardHud.scale
        val fx = scoreboardHud.x.toFloat()
        val fy = scoreboardHud.y.toFloat()
        val fw = boxWidth * scale
        val fh = boxHeight * scale

        if (phase == PanelPhase.BACKGROUND) {
            val fill = FloydPanelStyle.backgroundColorFor(target).rgba
            val border = HudPanel.panelBorderColors(target, scoreboardHud.x, scoreboardHud.y)
            val radius = FloydPanelStyle.cornerRadiusFor(target).toFloat() * scale
            val outline = FloydPanelStyle.borderWidthFor(target).toFloat() * scale

            // Frosted blur backdrop (samples the per-frame framebuffer snapshot), then the rounded fill+border.
            if (FloydPanelStyle.blurFor(target)) {
                val blurRadius = FloydPanelStyle.blurStrengthFor(target).coerceIn(0, 20) * 0.4f
                if (blurRadius >= 0.5f && fw * fh >= 2000f) {
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
            return
        }

        // TEXT phase (NanoVG, or the optional all-mc.font mode). Runs after every panel's background, so
        // NanoVG's GL-state corruption can no longer black out a background — see PostHudOverlay.render.
        val originX = fx / dpr
        val originY = fy / dpr
        val textScale = scale / dpr
        if (useMinecraftScoreboardFont()) {
            drawScoreboardTextMc(texts, originX, originY, textScale, allSegments = true)
            return
        }
        NVGRenderer.beginFrame(mc.window.width.toFloat(), mc.window.height.toFloat())
        NVGRenderer.translate(originX, originY)
        NVGRenderer.scale(textScale, textScale)
        for (text in texts) drawScoreboardText(text)
        NVGRenderer.endFrame()
        // mc.font fallback for glyphs the NVG font can't render (small-caps are already mapped to ASCII).
        if (texts.any { t -> t.value.segments.any { it.minecraftFont } }) {
            PostHudOverlay.bindMainFbo()
            drawScoreboardTextMc(texts, originX, originY, textScale, allSegments = false)
        }
    }

    /** Draws scoreboard text via mc.font to the bound main framebuffer (logical /dpr space). */
    private fun drawScoreboardTextMc(texts: List<ScoreboardText>, originX: Float, originY: Float, textScale: Float, allSegments: Boolean) {
        PostHudOverlay.applyScreenProjection()
        val mcMatrix = Matrix4f().translate(originX, originY, 0f).scale(textScale, textScale, 1f)
        val buffer = mc.renderBuffers().bufferSource()
        val font = NVGRenderer.activeFont()
        for (text in texts) {
            var segX = text.x
            for (segment in text.value.segments) {
                if (allSegments || segment.minecraftFont) {
                    mc.font.drawInBatch(segment.text, segX, text.y, segment.color, false, mcMatrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT)
                    segX += mc.font.width(segment.text).toFloat()
                } else {
                    segX += NVGRenderer.textWidth(segment.text, scoreboardFontSize(), font)
                }
            }
        }
        buffer.endBatch()
    }

    private fun drawScoreboardText(text: ScoreboardText) {
        var segmentX = text.x
        val font = NVGRenderer.activeFont()
        for (segment in text.value.segments) {
            if (segment.minecraftFont) {
                segmentX += mc.font.width(segment.text).toFloat()
                continue
            }
            NVGRenderer.text(segment.text, segmentX, text.y, scoreboardFontSize(), segment.color, font)
            segmentX += NVGRenderer.textWidth(segment.text, scoreboardFontSize(), font)
        }
    }

    private fun GuiGraphics.drawMinecraftScoreboardText(text: ScoreboardText) {
        var segmentX = text.x
        for (segment in text.value.segments) {
            drawString(mc.font, segment.text, segmentX.roundToInt(), text.y.roundToInt(), segment.color, false)
            segmentX += mc.font.width(segment.text).toFloat()
        }
    }

    private fun GuiGraphics.drawScoreboardMinecraftFallbacks(text: ScoreboardText) {
        var segmentX = text.x
        val font = NVGRenderer.activeFont()
        for (segment in text.value.segments) {
            if (segment.minecraftFont) {
                drawString(mc.font, segment.text, segmentX.roundToInt(), text.y.roundToInt(), segment.color, false)
                segmentX += mc.font.width(segment.text).toFloat()
            } else {
                segmentX += NVGRenderer.textWidth(segment.text, scoreboardFontSize(), font)
            }
        }
    }

    private fun styledText(text: FormattedCharSequence, forcedColor: Int? = null, forcedColorAt: ((Int) -> Int)? = null): StyledScoreboardText {
        // Apply Neck/Nick Hider + server/profile-id replacement to the scoreboard text up front. The
        // vanilla swap is wired through FontMixin (Font.prepareText/width), but this scoreboard renders
        // its custom font through NVGRenderer, which never touches Font — so without this the nick swap
        // (and id hiding) would silently skip the NVG-rendered scoreboard. No-op when no replacements are
        // active; idempotent for the mc.font fallback segments (their re-pass through FontMixin finds
        // nothing left to replace). Covers both the NVG and Minecraft-font scoreboard paths uniformly.
        val source = CustomNameReplacer.replaceSequenceIfNeeded(text)
        // Collect every glyph (with its resolved color) up front so the neighbor rule can inspect the
        // adjacent glyphs. No glyph is ever dropped: each renders in the custom font (possibly after
        // NFKC-normalizing a fancy unicode glyph down to ASCII), or in the default Minecraft font when
        // it is a 'skip' glyph or is directly trapped between two skip glyphs.
        val glyphs = ArrayList<String>()
        val skip = ArrayList<Boolean>()
        val colors = ArrayList<Int>()
        source.accept { _, style, codePoint ->
            val normalized = normalizeForScoreboardFont(codePoint)
            glyphs.add(normalized ?: String(Character.toChars(codePoint)))
            skip.add(normalized == null)
            // forcedColorAt colors per visual glyph index (left→right) for the brand's letter-by-letter chroma.
            colors.add(forcedColorAt?.invoke(colors.size) ?: forcedColor ?: scoreboardStyleColor(style))
            true
        }
        if (glyphs.isEmpty()) return StyledScoreboardText(emptyList())

        // A glyph uses the default Minecraft font when it is itself a skip glyph, or when it is a normal
        // glyph directly surrounded by skip glyphs on BOTH sides (servers that wrap only certain letters
        // in custom symbols). Everything else uses the custom override font.
        val minecraftFont = BooleanArray(glyphs.size) { i ->
            skip[i] || (i > 0 && i < glyphs.size - 1 && skip[i - 1] && skip[i + 1])
        }

        val segments = mutableListOf<ScoreboardTextSegment>()
        val currentText = StringBuilder()
        var currentColor = colors[0]
        var currentMinecraftFont = minecraftFont[0]
        fun flush() {
            if (currentText.isEmpty()) return
            segments += ScoreboardTextSegment(currentText.toString(), currentColor, currentMinecraftFont)
            currentText.clear()
        }
        for (i in glyphs.indices) {
            if (currentText.isNotEmpty() && (colors[i] != currentColor || minecraftFont[i] != currentMinecraftFont)) flush()
            currentColor = colors[i]
            currentMinecraftFont = minecraftFont[i]
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
     * The string to render in the custom (NVG) font for a source code point, or null when it cannot be
     * represented there (a 'skip' glyph that must fall back to the default Minecraft font). ASCII passes
     * through unchanged; non-ASCII is NFKC-normalized (full-width digits, ligatures, etc.) and accepted
     * only when the result is pure ASCII. Normalize-only: nothing is ever dropped — a code point that
     * cannot be normalized still renders via the mc.font fallback path.
     */
    private fun normalizeForScoreboardFont(codePoint: Int): String? {
        if (codePoint in 0x20..0x7E) return String(Character.toChars(codePoint))
        // Latin "small caps" Unicode (servers love these in titles, e.g. ᴀɴᴛɪᴄʜᴇᴀᴛ) have no NFKC ASCII
        // decomposition AND don't render via mc.font here, so map them to their plain letter — the title
        // then renders in the smooth NVG font like everything else, instead of vanishing.
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

    private fun useMinecraftScoreboardFont(): Boolean = scoreboardHudMinecraftFont

    private fun sidebarObjective(): Objective? {
        val player = mc.player ?: return null
        val scoreboard = mc.level?.scoreboard ?: return null
        val team = scoreboard.getPlayersTeam(player.scoreboardName)
        val teamObjective = team?.color?.let(DisplaySlot::teamColorToSlot)?.let(scoreboard::getDisplayObjective)
        return teamObjective ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    /** Title/footer accent: the global panel border color (chroma/fade per [FloydPanelStyle]). */
    private fun scoreboardAccentColor(offset: Float): Int =
        HudPanel.accentColor(FloydPanelStyle.borderColorFor(FloydPanelStyle.PanelTarget.SCOREBOARD),
            HudPanel.offsetPhase(HudPanel.hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f), offset))

    private data class ScoreLine(val name: StyledScoreboardText, val score: StyledScoreboardText, val nameWidth: Float, val scoreWidth: Float)
    private data class ScoreboardText(val value: StyledScoreboardText, val x: Float, val y: Float)
    private data class StyledScoreboardText(val segments: List<ScoreboardTextSegment>) {
        companion object { val EMPTY = StyledScoreboardText(emptyList()) }
    }
    private data class ScoreboardTextSegment(val text: String, val color: Int, val minecraftFont: Boolean = false)
}
