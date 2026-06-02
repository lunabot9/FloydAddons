package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
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

    private val scoreboardHudMinecraftFont by BooleanSetting("Scoreboard Minecraft Font", true, desc = "Uses Minecraft's default font instead of Floyd's smooth NanoVG font for scoreboard text.")

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
            "cornerRadius" to FloydPanelStyle.panelCornerRadius
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

        if (lines.size > 1) lines.removeAt(lines.lastIndex)
        if (lines.isEmpty()) return if (example) drawScoreboardExample() else 0 to 0

        return drawScoreboardBox(objective.displayName, Component.literal("FloydAddons"), lines, Component.literal(".gg/FLOYD").visualOrderText)
    }

    private fun GuiGraphics.drawScoreboardExample(): Pair<Int, Int> {
        val lines = mutableListOf(
            scoreLine("Purse: 1,234,567"),
            scoreLine("Bits: 12,345"),
            scoreLine("Location: Dungeon Hub"),
        )
        return drawScoreboardBox(Component.literal("SKYBLOCK"), Component.literal("FloydAddons"), lines, Component.literal(".gg/FLOYD").visualOrderText)
    }

    private fun GuiGraphics.drawScoreboardBox(title: Component, brand: Component, lines: List<ScoreLine>, footer: FormattedCharSequence): Pair<Int, Int> {
        val titleText = styledText(title.visualOrderText)
        val brandText = styledText(brand.visualOrderText, forcedColor = scoreboardAccentColor(0f))
        val footerText = styledText(footer, forcedColor = scoreboardAccentColor(0.5f))
        val titleWidth = textWidth(titleText)
        val brandWidth = textWidth(brandText)
        val footerWidth = textWidth(footerText)
        val colonWidth = textWidth(": ")
        var maxLineWidth = max(max(titleWidth, brandWidth), footerWidth)
        for (line in lines) {
            val width = line.nameWidth + if (line.scoreWidth > 0f) colonWidth + line.scoreWidth else 0f
            maxLineWidth = max(maxLineWidth, width)
        }

        val padding = FloydPanelStyle.panelPadding.coerceAtLeast(0)
        val fontSize = scoreboardTextHeight()
        val lineHeight = ceil(fontSize + 1f).toInt().coerceAtLeast(9)
        val titlePad = 2
        val boxWidth = ceil(maxLineWidth + padding * 2).toInt()
        // Header holds the Floyd brand line on top, then the server objective title beneath it.
        val headerHeight = padding + lineHeight * 2 + titlePad * 2
        val footerBarHeight = lineHeight + titlePad * 2 + padding
        val boxHeight = headerHeight + lines.size * lineHeight + footerBarHeight

        val textElements = ArrayList<ScoreboardText>(lines.size * 2 + 3)
        textElements += ScoreboardText(brandText, (boxWidth - brandWidth) / 2f, (padding + titlePad).toFloat())
        textElements += ScoreboardText(titleText, (boxWidth - titleWidth) / 2f, (padding + titlePad + lineHeight).toFloat())

        var lineY = headerHeight
        val scoreRight = boxWidth - padding
        for (line in lines) {
            textElements += ScoreboardText(line.name, padding.toFloat(), lineY.toFloat())
            if (line.scoreWidth > 0f) textElements += ScoreboardText(line.score, scoreRight - line.scoreWidth, lineY.toFloat())
            lineY += lineHeight
        }
        textElements += ScoreboardText(footerText, (boxWidth - footerWidth) / 2f, (lineY + titlePad).toFloat())

        drawScoreboardPanelAndText(boxWidth, boxHeight, textElements)
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

    private fun GuiGraphics.drawScoreboardPanelAndText(boxWidth: Int, boxHeight: Int, texts: List<ScoreboardText>) {
        HudPanel.fillPanel(this, 0, 0, boxWidth, boxHeight, HudPanel.panelBorderColors(scoreboardHud.x, scoreboardHud.y))
        if (useMinecraftScoreboardFont()) {
            for (text in texts) drawMinecraftScoreboardText(text)
        } else {
            NVGPIPRenderer.draw(this, 0, 0, boxWidth, boxHeight, renderScaleMultiplier = mc.window.guiScale.toFloat()) {
                for (text in texts) drawScoreboardText(text)
            }
            for (text in texts) drawScoreboardMinecraftFallbacks(text)
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
        val segments = mutableListOf<ScoreboardTextSegment>()
        val currentText = StringBuilder()
        var currentColor: Int? = null
        var currentMinecraftFont = false

        fun flush() {
            if (currentText.isEmpty()) return
            segments += ScoreboardTextSegment(currentText.toString(), currentColor ?: 0xFFFFFFFF.toInt(), currentMinecraftFont)
            currentText.clear()
        }

        text.accept { _, style, codePoint ->
            val color = forcedColor ?: scoreboardStyleColor(style)
            val minecraftFont = shouldUseMinecraftFontCodePoint(codePoint)
            if ((currentColor != null && currentColor != color) || (currentText.isNotEmpty() && currentMinecraftFont != minecraftFont)) flush()
            currentColor = color
            currentMinecraftFont = minecraftFont
            currentText.appendCodePoint(codePoint)
            true
        }
        flush()
        return StyledScoreboardText(segments)
    }

    private fun scoreboardStyleColor(style: Style): Int {
        val color = style.color?.value ?: return 0xFFFFFFFF.toInt()
        return 0xFF000000.toInt() or (color and 0x00FFFFFF)
    }

    private fun shouldUseMinecraftFontCodePoint(codePoint: Int): Boolean = codePoint !in 0x20..0x7E

    private fun useMinecraftScoreboardFont(): Boolean = true

    private fun sidebarObjective(): Objective? {
        val player = mc.player ?: return null
        val scoreboard = mc.level?.scoreboard ?: return null
        val team = scoreboard.getPlayersTeam(player.scoreboardName)
        val teamObjective = team?.color?.let(DisplaySlot::teamColorToSlot)?.let(scoreboard::getDisplayObjective)
        return teamObjective ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    /** Title/footer accent: the global panel border color (chroma/fade per [FloydPanelStyle]). */
    private fun scoreboardAccentColor(offset: Float): Int =
        HudPanel.accentColor(FloydPanelStyle.panelBorderColor,
            HudPanel.offsetPhase(HudPanel.hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f), offset))

    private data class ScoreLine(val name: StyledScoreboardText, val score: StyledScoreboardText, val nameWidth: Float, val scoreWidth: Float)
    private data class ScoreboardText(val value: StyledScoreboardText, val x: Float, val y: Float)
    private data class StyledScoreboardText(val segments: List<ScoreboardTextSegment>) {
        companion object { val EMPTY = StyledScoreboardText(emptyList()) }
    }
    private data class ScoreboardTextSegment(val text: String, val color: Int, val minecraftFont: Boolean = false)
}
