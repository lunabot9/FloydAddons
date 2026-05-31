package gg.floyd.features.impl.pvp

import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.events.RenderEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Colors
import gg.floyd.utils.modMessage
import gg.floyd.utils.render.ItemStateRenderer.Companion.drawItemStack
import gg.floyd.utils.render.drawText
import gg.floyd.utils.render.drawTracer
import gg.floyd.utils.render.drawWireFrameBox
import gg.floyd.utils.renderBoundingBox
import gg.floyd.utils.renderPos
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.EquipmentSlot

/**
 * ESP scoped to other players. Shows health and equipped items either as an
 * over-head world overlay, a movable HUD list, both, or none. "All players" is
 * the natural scope: enabling the module = `/fa stalk all`.
 */
object FloydPlayerEsp : Module(
    name = "Player ESP",
    category = Category.PVP,
    description = "Highlights other players with health and equipped items (world overlay and/or HUD list).",
) {
    // 0 = Overhead, 1 = HUD List, 2 = Both, 3 = None
    private val display by SelectorSetting("Display", "Overhead", listOf("Overhead", "HUD List", "Both", "None"), desc = "How other players are shown.")
    private val color by ColorSetting("Color", Colors.ACCENT.copy(), desc = "ESP color (toggle chroma inside the picker).")
    private val boxes by BooleanSetting("Boxes", true, desc = "Draws a box around each player.")
    private val tracers by BooleanSetting("Tracers", false, desc = "Draws a tracer line to each player.")
    private val showHealth by BooleanSetting("Show Health", true, desc = "Shows each player's health.")
    private val showHeldItem by BooleanSetting("Show Held Item", true, desc = "Shows each player's held item.")

    private val stalkAllAction by ActionSetting("Stalk All Players", desc = "Toggles Player ESP for all players (same as /fa stalk all).") {
        val on = stalkAll()
        modMessage(if (on) "Player ESP: stalking all players" else "Player ESP: stopped")
    }

    private val listHud by HUD("Player ESP List", "Movable list of nearby players, their health and gear.", true, 12, 60, 1f) { example ->
        drawPlayerList(example)
    }

    init {
        on<RenderEvent.Extract> {
            if (!enabled) return@on
            // World overlay only for Overhead (0) or Both (2).
            if (display != 0 && display != 2) return@on
            if (mc.player == null) return@on
            for (other in otherPlayers()) {
                val c = color
                if (boxes) drawWireFrameBox(other.renderBoundingBox, c, thickness = 2f, depth = false)
                if (tracers) drawTracer(other.renderPos.add(0.0, other.bbHeight / 2.0, 0.0), c, depth = false, thickness = 2f)
                val label = overheadLabel(other)
                if (label.isNotEmpty()) {
                    drawText(label, other.renderPos.add(0.0, other.bbHeight + 0.5, 0.0), 1f, depth = false)
                }
            }
        }
    }

    private fun otherPlayers(): List<AbstractClientPlayer> {
        val self = mc.player ?: return emptyList()
        return mc.level?.players()?.filter { it !== self && !it.isSpectator } ?: emptyList()
    }

    private fun overheadLabel(player: AbstractClientPlayer): String {
        val parts = mutableListOf<String>()
        if (showHealth) parts.add("${player.health.toInt()}/${player.maxHealth.toInt()} HP")
        if (showHeldItem) {
            val held = player.mainHandItem
            if (!held.isEmpty) parts.add(held.hoverName.string)
        }
        return parts.joinToString("  ")
    }

    /** Enables/disables the all-players ESP. Returns the new enabled state. */
    fun stalkAll(): Boolean {
        toggle()
        return enabled
    }

    private fun GuiGraphics.drawPlayerList(example: Boolean): Pair<Int, Int> {
        if (!enabled || (display != 1 && display != 2)) return 0 to 0
        val self = mc.player
        val players = if (example || self == null) emptyList()
        else otherPlayers().sortedBy { it.distanceToSqr(self) }.take(12)

        val rowH = 20
        val width = 190
        val rows = if (players.isEmpty()) 1 else players.size
        val height = rows * rowH + 4

        fill(0, 0, width, height, 0x90000000.toInt())
        fill(0, 0, 2, height, color.rgba)

        if (players.isEmpty()) {
            drawString(mc.font, "Player ESP — no players", 6, height / 2 - 4, color.rgba, true)
            return width to height
        }

        var y = 2
        for (p in players) {
            // Held item icon
            val held = p.mainHandItem
            if (!held.isEmpty) {
                pose().pushMatrix()
                pose().translate(4f, (y + 1).toFloat())
                drawItemStack(held, 0, 0)
                pose().popMatrix()
            }
            drawString(mc.font, p.name.string, 24, y, 0xFFFFFFFF.toInt(), true)

            // Health bar
            val ratio = (p.health / p.maxHealth.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val barX = 24
            val barY = y + 11
            val barW = 55
            fill(barX, barY, barX + barW, barY + 5, 0xFF333333.toInt())
            fill(barX, barY, barX + (barW * ratio).toInt(), barY + 5, healthColor(ratio))
            drawString(mc.font, "${p.health.toInt()}/${p.maxHealth.toInt()}", barX + barW + 4, y + 8, 0xFFFFFFFF.toInt(), true)

            // Armor icons on the right
            var ax = width - 4 - 16
            for (slot in arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                val piece = p.getItemBySlot(slot)
                if (!piece.isEmpty) {
                    pose().pushMatrix()
                    pose().translate(ax.toFloat(), (y + 1).toFloat())
                    drawItemStack(piece, 0, 0)
                    pose().popMatrix()
                    ax -= 17
                }
            }
            y += rowH
        }
        return width to height
    }

    private fun healthColor(ratio: Float): Int {
        val red = ((1f - ratio) * 255).toInt().coerceIn(0, 255)
        val green = (ratio * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8)
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "display" to display,
        "playerCount" to otherPlayers().size
    )
}
