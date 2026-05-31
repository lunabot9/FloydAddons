package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.render.HudPanel
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

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
    private val vanillaScoreboardWouldRender = AtomicBoolean(false)

    private val scoreboardHud by HUD("Scoreboard HUD", "Displays a movable Floyd-styled scoreboard.", true, 10, 80, 1f) { example ->
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
                "hudScale" to scoreboardHud.scale
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
                val name = PlayerTeam.formatNameForTeam(team, entry.ownerName()).visualOrderText
                val score = entry.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT))
                ScoreLine(name, score, mc.font.width(score))
            }
            ?.toMutableList()
            ?: return if (example) drawScoreboardExample() else 0 to 0

        if (lines.size > 1) lines.removeAt(lines.lastIndex)
        if (lines.isEmpty()) return if (example) drawScoreboardExample() else 0 to 0

        return drawScoreboardBox(objective.displayName, lines, Component.literal("FloydAddons").visualOrderText)
    }

    private fun GuiGraphics.drawScoreboardExample(): Pair<Int, Int> {
        val lines = mutableListOf(
            ScoreLine(Component.literal("Purse: 1,234,567").visualOrderText, Component.empty(), 0),
            ScoreLine(Component.literal("Bits: 12,345").visualOrderText, Component.empty(), 0),
            ScoreLine(Component.literal("Location: Dungeon Hub").visualOrderText, Component.empty(), 0),
        )
        return drawScoreboardBox(Component.literal("SKYBLOCK"), lines, Component.literal("FloydAddons").visualOrderText)
    }

    private fun GuiGraphics.drawScoreboardBox(title: Component, lines: List<ScoreLine>, footer: FormattedCharSequence): Pair<Int, Int> {
        val titleWidth = mc.font.width(title)
        val footerWidth = mc.font.width(footer)
        val colonWidth = mc.font.width(": ")
        var maxLineWidth = max(titleWidth, footerWidth)
        for (line in lines) {
            val width = mc.font.width(line.name) + if (line.scoreWidth > 0) colonWidth + line.scoreWidth else 0
            maxLineWidth = max(maxLineWidth, width)
        }

        val padding = FloydPanelStyle.panelPadding.coerceAtLeast(0)
        val lineHeight = 9
        val titlePad = 2
        val boxWidth = maxLineWidth + padding * 2
        val titleBarHeight = padding + lineHeight + titlePad * 2
        val footerBarHeight = lineHeight + titlePad * 2 + padding
        val boxHeight = titleBarHeight + lines.size * lineHeight + footerBarHeight

        HudPanel.fillPanel(this, 0, 0, boxWidth, boxHeight, HudPanel.panelBorderColors(scoreboardHud.x, scoreboardHud.y))
        drawString(mc.font, title, (boxWidth - titleWidth) / 2, padding + titlePad, scoreboardAccentColor(0f), true)

        var lineY = titleBarHeight
        val scoreRight = boxWidth - padding
        for (line in lines) {
            drawString(mc.font, line.name, padding, lineY, 0xFFFFFFFF.toInt(), false)
            if (line.scoreWidth > 0) drawString(mc.font, line.score, scoreRight - line.scoreWidth, lineY, 0xFFFFFFFF.toInt(), false)
            lineY += lineHeight
        }

        drawString(mc.font, footer, (boxWidth - footerWidth) / 2, lineY + titlePad, scoreboardAccentColor(0.5f), true)
        return boxWidth to boxHeight
    }

    private fun sidebarObjective(): Objective? {
        val player = mc.player ?: return null
        val scoreboard = mc.level?.scoreboard ?: return null
        val team = scoreboard.getPlayersTeam(player.scoreboardName)
        val teamObjective = team?.color?.let(DisplaySlot::teamColorToSlot)?.let(scoreboard::getDisplayObjective)
        return teamObjective ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    /** Title/footer accent: the global panel border color (chroma/fade per [FloydPanelStyle]). */
    private fun scoreboardAccentColor(offset: Float): Int =
        HudPanel.accentColor(FloydPanelStyle.effectiveBorderColor(), FloydPanelStyle.borderFade, FloydPanelStyle.borderFadeColor,
            HudPanel.offsetPhase(HudPanel.hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f), offset))

    private data class ScoreLine(val name: FormattedCharSequence, val score: Component, val scoreWidth: Int)
}
