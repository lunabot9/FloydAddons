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
import gg.floyd.utils.render.WorldToScreen
import gg.floyd.utils.render.drawTracer
import gg.floyd.utils.render.drawWireFrameBox
import gg.floyd.utils.renderBoundingBox
import gg.floyd.utils.renderPos
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

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
    private val showEquipment by BooleanSetting("Show Equipment", true, desc = "Shows each player's equipped item icons.")

    // Helmet, chestplate, leggings, boots, main hand, off hand.
    private val equipmentSlots = arrayOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
    )

    private const val HEART = "❤"
    private const val ICON_SIZE = 16
    private const val ICON_SPACING = 18

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
            // World overlay (boxes + tracers) only for Overhead (0) or Both (2).
            if (display != 0 && display != 2) return@on
            if (mc.player == null) return@on
            for (other in otherPlayers()) {
                val c = color
                if (boxes) drawWireFrameBox(other.renderBoundingBox, c, thickness = 2f, depth = false)
                if (tracers) drawTracer(other.renderPos.add(0.0, other.bbHeight / 2.0, 0.0), c, depth = false, thickness = 2f)
            }
        }
    }

    private fun otherPlayers(): List<AbstractClientPlayer> {
        val self = mc.player ?: return emptyList()
        return mc.level?.players()?.filter { it !== self && !it.isSpectator } ?: emptyList()
    }

    private fun equipmentOf(player: AbstractClientPlayer): List<ItemStack> =
        equipmentSlots.map { player.getItemBySlot(it) }

    /** Enables/disables the all-players ESP. Returns the new enabled state. */
    fun stalkAll(): Boolean {
        toggle()
        return enabled
    }

    /**
     * Screen-space overhead ESP: a heart + health number and a row of equipment item icons
     * rendered well above each player's head (above the vanilla/server nametag). Drawn in the
     * HUD pass because item icons require GuiGraphics; positions are projected from world space
     * through the bob-stable render matrices so they track players without view-bob wobble.
     */
    fun drawOverheadOverlay(graphics: GuiGraphics) {
        if (!enabled) return
        if (display != 0 && display != 2) return
        if (!showHealth && !showEquipment) return
        val self = mc.player ?: return

        for (player in otherPlayers()) {
            if (player.distanceToSqr(self) > 64.0 * 64.0) continue
            // Project a point clearly above the head + nametag so the overlay does not overlap it.
            val anchorY = player.bbHeight + 1.2
            val screen = WorldToScreen.project(player.renderPos.add(0.0, anchorY, 0.0)) ?: continue
            drawOverheadEntry(graphics, player, screen.x, screen.y)
        }
    }

    private fun drawOverheadEntry(graphics: GuiGraphics, player: AbstractClientPlayer, centerX: Float, baseY: Float) {
        var rowY = baseY

        if (showHealth) {
            val text = "$HEART ${player.health.toInt()}"
            val textWidth = mc.font.width(text)
            val textX = (centerX - textWidth / 2f).toInt()
            graphics.drawString(mc.font, text, textX, rowY.toInt(), Colors.MINECRAFT_RED.rgba, true)
            rowY += mc.font.lineHeight + 2
        }

        if (showEquipment) {
            val items = equipmentOf(player)
            val present = items.count { !it.isEmpty }
            if (present > 0) {
                val totalWidth = items.size * ICON_SPACING - (ICON_SPACING - ICON_SIZE)
                var iconX = (centerX - totalWidth / 2f)
                for (stack in items) {
                    if (!stack.isEmpty) {
                        graphics.pose().pushMatrix()
                        graphics.pose().translate(iconX, rowY)
                        graphics.drawItemStack(stack, 0, 0)
                        graphics.pose().popMatrix()
                    }
                    iconX += ICON_SPACING
                }
            }
        }
    }

    private fun GuiGraphics.drawPlayerList(example: Boolean): Pair<Int, Int> {
        if (!enabled || (display != 1 && display != 2)) return 0 to 0
        val self = mc.player
        val players = if (example || self == null) emptyList()
        else otherPlayers().sortedBy { it.distanceToSqr(self) }.take(12)

        val rowH = 22
        val width = 200
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
            drawString(mc.font, p.name.string, 6, y, 0xFFFFFFFF.toInt(), true)

            // Heart + health number.
            val health = "$HEART ${p.health.toInt()}"
            drawString(mc.font, health, 6, y + 10, Colors.MINECRAFT_RED.rgba, true)

            // The SAME six equipment icons as the overhead entry, right-aligned.
            val items = equipmentOf(p)
            var ax = width - 4 - ICON_SIZE
            for (i in items.indices.reversed()) {
                val piece = items[i]
                if (!piece.isEmpty) {
                    pose().pushMatrix()
                    pose().translate(ax.toFloat(), (y + 2).toFloat())
                    drawItemStack(piece, 0, 0)
                    pose().popMatrix()
                }
                ax -= ICON_SPACING
            }
            y += rowH
        }
        return width to height
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "display" to display,
        "playerCount" to otherPlayers().size
    )
}
