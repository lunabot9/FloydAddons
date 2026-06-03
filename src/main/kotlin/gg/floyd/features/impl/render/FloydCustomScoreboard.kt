package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.RoundRectPIPRenderer
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
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

    private fun GuiGraphics.drawScoreboardHud(example: Boolean): Pair<Int, Int> {
        val objective = sidebarObjective() ?: return if (example) drawScoreboardExample() else 0 to 0
        if (!shouldDrawScoreboardHud(example, shouldUseCustomScoreboard(), objectivePresent = true)) return 0 to 0
        val scoreboard = mc.level?.scoreboard ?: return if (example) drawScoreboardExample() else 0 to 0
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
            ?: return if (example) drawScoreboardExample() else 0 to 0

        if (lines.isEmpty()) return if (example) drawScoreboardExample() else 0 to 0

        return drawScoreboardBox(objective.displayName, Component.literal("FloydAddons"), lines, example)
    }

    private fun GuiGraphics.drawScoreboardExample(): Pair<Int, Int> {
        val lines = mutableListOf(
            scoreLine("Purse: 1,234,567"),
            scoreLine("Bits: 12,345"),
            scoreLine("Location: Dungeon Hub"),
        )
        return drawScoreboardBox(Component.literal("SKYBLOCK"), Component.literal("FloydAddons"), lines, example = true)
    }

    private fun GuiGraphics.drawScoreboardBox(title: Component, brand: Component, lines: List<ScoreLine>, example: Boolean): Pair<Int, Int> {
        val titleText = styledText(title.visualOrderText)
        // The Floyd brand line tracks the panel border color (chroma / fade / solid) via the accent.
        val brandText = styledText(brand.visualOrderText, forcedColor = scoreboardAccentColor(0f))
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

        val textElements = ArrayList<ScoreboardText>(lines.size * 2 + 2)
        textElements += ScoreboardText(titleText, (boxWidth - titleWidth) / 2f, titleY.toFloat())

        var lineY = headerHeight
        for (line in lines) {
            textElements += ScoreboardText(line.name, padding.toFloat(), lineY.toFloat())
            lineY += lineHeight
        }

        textElements += ScoreboardText(brandText, (boxWidth - brandWidth) / 2f, brandY.toFloat())

        drawScoreboardPanelAndText(boxWidth, boxHeight, textElements, example)
        return boxWidth to boxHeight
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

    private fun GuiGraphics.drawScoreboardPanelAndText(boxWidth: Int, boxHeight: Int, texts: List<ScoreboardText>, example: Boolean) {
        // Editor preview (example) and the Minecraft-font mode keep the original deferred PIP path.
        if (example || useMinecraftScoreboardFont()) {
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
            return
        }

        // In-game smooth-font scoreboard → the single post-HUD pass (no PIP). Compute the panel's
        // framebuffer geometry from the live HUD pose now; PostHudOverlay replays these closures after
        // the HUD is composited. context.pose() maps local -> gui-scaled coords, so multiply by guiScale
        // to reach framebuffer pixels (the space drawInline / the bound FBO draw in). dpr maps to NanoVG's
        // logical canvas. This mirrors the old PIP's poseScale*guiScale (radius/outline) and guiScale/dpr
        // (text) math, just drawn inline instead of into an offscreen texture.
        val pose = Matrix3x2f(pose())
        val gs = mc.window.guiScale.toFloat()
        val dpr = NVGRenderer.devicePixelRatio()
        val poseScale = pose.transformDirection(Vector2f(1f, 0f)).length()
        val p0 = pose.transformPosition(Vector2f(0f, 0f))
        val p1 = pose.transformPosition(Vector2f(boxWidth.toFloat(), boxHeight.toFloat()))
        val fx = minOf(p0.x, p1.x) * gs
        val fy = minOf(p0.y, p1.y) * gs
        val fw = kotlin.math.abs(p1.x - p0.x) * gs
        val fh = kotlin.math.abs(p1.y - p0.y) * gs

        val fill = FloydPanelStyle.backgroundColorFor(FloydPanelStyle.PanelTarget.SCOREBOARD).rgba
        val border = HudPanel.panelBorderColors(FloydPanelStyle.PanelTarget.SCOREBOARD, scoreboardHud.x, scoreboardHud.y)
        val radius = FloydPanelStyle.cornerRadiusFor(FloydPanelStyle.PanelTarget.SCOREBOARD).toFloat() * poseScale * gs
        val outline = FloydPanelStyle.borderWidthFor(FloydPanelStyle.PanelTarget.SCOREBOARD).toFloat() * poseScale * gs

        PostHudOverlay.enqueue {
            RoundRectPIPRenderer.drawInline(
                fx, fy, fw, fh,
                fill, fill, fill, fill,
                radius, radius, radius, radius,
                border.topLeft, border.topRight, border.bottomRight, border.bottomLeft,
                outline
            )
        }
        val originX = p0.x * gs / dpr
        val originY = p0.y * gs / dpr
        val textScale = poseScale * gs / dpr
        PostHudOverlay.enqueue {
            NVGRenderer.beginFrame(mc.window.width.toFloat(), mc.window.height.toFloat())
            NVGRenderer.translate(originX, originY)
            NVGRenderer.scale(textScale, textScale)
            for (text in texts) drawScoreboardText(text)
            NVGRenderer.endFrame()
        }
        // Minecraft-font fallback glyphs (a server's fancy small-caps title, boxed-in symbols, etc.) drawn
        // directly to the main framebuffer via mc.font — only the segments the NVG font can't render, never
        // a whole dropped line — on top of the bg, in the same framebuffer frame as the NVG text.
        if (texts.any { t -> t.value.segments.any { it.minecraftFont } }) {
            // mc.font renders in logical (framebuffer/dpr) space — same as NanoVG, NOT the SDF's
            // framebuffer space — so use the identical /dpr transform as the NVG text above.
            val mcMatrix = Matrix4f().translate(originX, originY, 0f).scale(textScale, textScale, 1f)
            PostHudOverlay.enqueue {
                PostHudOverlay.applyScreenProjection()
                val buffer = mc.renderBuffers().bufferSource()
                val font = NVGRenderer.activeFont()
                for (text in texts) {
                    var segX = text.x
                    for (segment in text.value.segments) {
                        if (segment.minecraftFont) {
                            mc.font.drawInBatch(segment.text, segX, text.y, segment.color, false, mcMatrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT)
                            segX += mc.font.width(segment.text).toFloat()
                        } else {
                            segX += NVGRenderer.textWidth(segment.text, scoreboardFontSize(), font)
                        }
                    }
                }
                buffer.endBatch()
            }
        }
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

    private fun styledText(text: FormattedCharSequence, forcedColor: Int? = null): StyledScoreboardText {
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
            colors.add(forcedColor ?: scoreboardStyleColor(style))
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
