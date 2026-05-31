package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Color
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.ItemStateRenderer.Companion.drawItemStack
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
import kotlin.math.roundToInt

object FloydHud : Module(
    name = "HUD",
    category = Category.RENDER,
    description = "Floyd inventory HUD, scoreboard HUD, and movable HUD editors.",
    toggled = true,
) {
    private val vanillaScoreboardWouldRender = AtomicBoolean(false)

    val inventoryHudScale by NumberSetting("Inventory HUD Scale", 1.1f, 0.5f, 5.0f, 0.05f, desc = "Inventory HUD scale.")
    private val hudCornerRadius by NumberSetting("HUD Corner Radius", 0, 0, 12, 1, desc = "Rounded corner radius for Floyd HUD panels.")
    private val scoreboardHudColor by ColorSetting("Scoreboard Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Scoreboard border + footer accent (toggle chroma inside the picker).")
    private val scoreboardHudFade by BooleanSetting("Scoreboard Fade", false, desc = "Fades the scoreboard accent between two colors.")
    private val scoreboardHudFadeColor by ColorSetting("Scoreboard Fade Color", Color(0xFF55FFFF.toInt()), desc = "Secondary color for the scoreboard fade.")
    private val scoreboardHudCornerRadius by NumberSetting("Corner Radius", 0, 0, 20, 1, desc = "Rounded corner radius for the scoreboard HUD panel.")
    private val scoreboardHudPadding by NumberSetting("Padding", 7, 0, 16, 1, desc = "Internal padding between the scoreboard border and its text.")

    private val inventoryHud by HUD("Inventory HUD", "Displays the main inventory in a movable Floyd HUD.", true, 12, 12, 1f) {
        drawInventoryHud(it)
    }

    private val scoreboardHud by HUD("Scoreboard HUD", "Displays a movable Floyd-styled scoreboard.", true, 10, 80, 1f) { example ->
        drawScoreboardHud(example)
    }

    fun state(): Map<String, Any?> {
        val objective = sidebarObjective()
        return mapOf(
            "enabled" to enabled,
            "inventoryHud" to mapOf(
                "enabled" to (enabled && inventoryHud.enabled),
                "scale" to inventoryHudScale,
                "x" to inventoryHud.x,
                "y" to inventoryHud.y,
                "hudScale" to inventoryHud.scale
            ),
            "scoreboardHud" to mapOf(
                "enabled" to (enabled && scoreboardHud.enabled),
                "customScoreboard" to FloydCustomScoreboard.shouldUseCustomScoreboard(),
                "sidebarObjective" to objective?.name,
                "vanillaWouldRender" to vanillaScoreboardWouldRender.get(),
                "wouldRender" to shouldDrawScoreboardHud(
                    example = false,
                    customScoreboard = FloydCustomScoreboard.shouldUseCustomScoreboard(),
                    objectivePresent = objective != null,
                    consumeVanillaSignal = false
                ),
                "x" to scoreboardHud.x,
                "y" to scoreboardHud.y,
                "hudScale" to scoreboardHud.scale
            ),
            "cornerRadius" to hudCornerRadius
        )
    }

    @JvmStatic
    fun markVanillaScoreboardWouldRender() {
        vanillaScoreboardWouldRender.set(true)
    }

    internal fun resetVanillaScoreboardWouldRender() {
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

    private fun GuiGraphics.drawInventoryHud(example: Boolean): Pair<Int, Int> {
        val inventory = mc.player?.inventory
        val slotSize = (18 * inventoryHudScale).roundToInt().coerceAtLeast(12)
        val width = 9 * slotSize
        val height = 3 * slotSize
        HudPanel.fillPanel(this, 0, 0, width, height, HudPanel.monochrome(HudPanel.chromaColor(0f)), cornerRadius = hudCornerRadius.toFloat())

        if (inventory != null) {
            for (slot in 0 until 27) {
                val col = slot % 9
                val row = slot / 9
                val stack = inventory.getItem(slot + 9)
                if (stack.isEmpty) continue

                pose().pushMatrix()
                val itemScale = minOf(1f, slotSize / 16f)
                val x = col * slotSize + (slotSize - 16 * itemScale) / 2f
                val y = row * slotSize + (slotSize - 16 * itemScale) / 2f
                pose().translate(x, y)
                pose().scale(itemScale, itemScale)
                drawItemStack(stack, 0, 0)
                pose().popMatrix()

                if (stack.count > 1) {
                    val count = stack.count.toString()
                    val tx = (x + (slotSize - mc.font.width(count)) / 2f + 1).toInt()
                    val ty = (y + slotSize - mc.font.lineHeight - 3).toInt()
                    drawString(mc.font, count, tx, ty, 0xFFFFFFFF.toInt(), true)
                }
            }
        }
        return width to height
    }

    private fun GuiGraphics.drawScoreboardHud(example: Boolean): Pair<Int, Int> {
        val objective = sidebarObjective() ?: return if (example) drawScoreboardExample() else 0 to 0
        if (!shouldDrawScoreboardHud(example, FloydCustomScoreboard.shouldUseCustomScoreboard(), objectivePresent = true)) return 0 to 0
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

        val padding = scoreboardHudPadding.coerceAtLeast(0)
        val lineHeight = 9
        val titlePad = 2
        val boxWidth = maxLineWidth + padding * 2
        val titleBarHeight = padding + lineHeight + titlePad * 2
        val footerBarHeight = lineHeight + titlePad * 2 + padding
        val boxHeight = titleBarHeight + lines.size * lineHeight + footerBarHeight

        HudPanel.fillPanel(this, 0, 0, boxWidth, boxHeight, scoreboardHudBorderColors(), cornerRadius = scoreboardHudCornerRadius.toFloat())
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

    /** Rotating gradient border for the scoreboard panel (chroma/fade/solid per the Color settings). */
    private fun scoreboardHudBorderColors(): HudPanel.BorderColors =
        HudPanel.circularBorderColors(scoreboardHudColor, scoreboardHudFade, scoreboardHudFadeColor,
            HudPanel.hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f))

    private fun scoreboardAccentColor(offset: Float): Int =
        HudPanel.accentColor(scoreboardHudColor, scoreboardHudFade, scoreboardHudFadeColor,
            HudPanel.offsetPhase(HudPanel.hudRotationOffset(scoreboardHud.x, scoreboardHud.y, 0.38f), offset))

    private data class ScoreLine(val name: FormattedCharSequence, val score: Component, val scoreWidth: Int)
}
