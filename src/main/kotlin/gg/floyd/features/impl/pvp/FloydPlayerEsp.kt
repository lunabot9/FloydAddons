package gg.floyd.features.impl.pvp

import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.events.RenderEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Color
import gg.floyd.utils.Colors
import gg.floyd.utils.modMessage
import gg.floyd.utils.render.HudPanel
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
import kotlin.math.abs

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

    // Overhead nameplate: a panel pinned a fixed distance above the head that scales with distance
    // exactly like the ESP box (it is a physical, constant-world-size object — like a real nametag).
    private val overheadScale by NumberSetting("Overhead Scale", 1.0f, 0.25f, 5.0f, 0.05f, desc = "Multiplier on the overhead nameplate's world size. It still shrinks with distance like a real nametag.")
    private val overheadPadding by NumberSetting("Overhead Padding", 4, 0, 16, 1, desc = "Internal padding between the overhead panel border and its contents.")
    private val overheadCornerRadius by NumberSetting("Overhead Corner Radius", 4, 0, 16, 1, desc = "Rounded corner radius for the overhead nameplate panel.")
    private val overheadFade by BooleanSetting("Overhead Fade", false, desc = "Fades the overhead panel border between the ESP color and a second color.")
    private val overheadFadeColor by ColorSetting("Overhead Fade Color", Color(0xFF55FFFF.toInt()), desc = "Secondary color for the overhead border fade.")

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

    // Overhead nameplate tuning (local px, before the per-frame perspective scale):
    private const val OVERHEAD_ICON_GAP = 2          // gap between equipment icons on the single row
    private const val OVERHEAD_TEXT_ICON_GAP = 4     // gap between the health text and the first icon
    // Reference panel height (= ICON_SIZE + 2 * default padding) and the world height it maps to at
    // Scale 1. Together they set the baseline: a default-padded plate renders ~OVERHEAD_WORLD_HEIGHT
    // blocks tall on screen, then shrinks/grows with distance like the ESP box. No cap.
    private const val OVERHEAD_REF_PX = 24f
    private const val OVERHEAD_WORLD_HEIGHT = 0.55f
    // World height above the player's bounding box where the plate's bottom edge floats (above the nametag).
    private const val OVERHEAD_ANCHOR_OFFSET = 0.6

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
     * Screen-space overhead nameplate: a SINGLE row of heart + health and the equipped-item icons,
     * inside a tinted panel bordered with the ESP color. The plate is pinned a fixed world distance
     * above the head and scaled per-frame by the SAME perspective factor the ESP box draws at, so it
     * behaves like a physical, constant-world-size object floating over the player (a real nametag):
     * it shrinks as you back away and grows as you approach, with no min/max cap. The [overheadScale]
     * setting multiplies that world size.
     *
     * Drawn in the HUD pass (item icons need GuiGraphics) but in plain gui-scaled space, and the
     * perspective factor is read straight from the bob-stable render matrices via [WorldToScreen],
     * so the plate tracks heads without view-bob wobble.
     */
    fun drawOverheadOverlay(graphics: GuiGraphics) {
        if (!enabled) return
        if (display != 0 && display != 2) return
        if (!showHealth && !showEquipment) return
        val self = mc.player ?: return

        for (player in otherPlayers()) {
            if (player.distanceToSqr(self) > 64.0 * 64.0) continue
            // Anchor = the plate's bottom-center, a fixed world distance above the head (above the nametag).
            val anchor = player.renderPos.add(0.0, player.bbHeight + OVERHEAD_ANCHOR_OFFSET, 0.0)
            val anchorScreen = WorldToScreen.project(anchor) ?: continue
            // Project a point one world-block higher: the gui-pixel delta is exactly how many pixels
            // one block spans at this depth — i.e. the same perspective scale the ESP box draws at.
            val aboveScreen = WorldToScreen.project(anchor.add(0.0, 1.0, 0.0)) ?: continue
            val pxPerBlock = abs(anchorScreen.y - aboveScreen.y)
            if (pxPerBlock <= 0.01f) continue
            drawOverheadEntry(graphics, player, anchorScreen.x, anchorScreen.y, overheadScaleFactor(pxPerBlock, overheadScale))
        }
    }

    private fun drawOverheadEntry(graphics: GuiGraphics, player: AbstractClientPlayer, anchorX: Float, anchorY: Float, scale: Float) {
        val pad = overheadPadding.coerceAtLeast(0)
        val hpText = if (showHealth) "$HEART ${player.health.toInt()}" else null
        val hpWidth = hpText?.let { mc.font.width(it) } ?: 0
        val items = if (showEquipment) equipmentOf(player).filter { !it.isEmpty } else emptyList()

        val dims = overheadDimensions(hpWidth, items.size, pad, mc.font.lineHeight)
        if (dims.panelWidth <= 0) return

        // Plate bottom-center sits at the anchor and extends upward; everything inside the pushed
        // pose (panel, text, icons) is scaled together by the per-frame perspective factor.
        val left = -dims.panelWidth / 2
        val top = -dims.panelHeight
        graphics.pose().pushMatrix()
        graphics.pose().translate(anchorX, anchorY)
        graphics.pose().scale(scale, scale)

        val border = HudPanel.circularBorderColors(color, overheadFade, overheadFadeColor, 0f)
        HudPanel.fillPanel(graphics, left, top, left + dims.panelWidth, top + dims.panelHeight, border, cornerRadius = overheadCornerRadius.toFloat())

        val rowHeight = if (items.isNotEmpty()) ICON_SIZE else mc.font.lineHeight
        val rowTop = top + pad
        var cx = left + pad
        if (hpText != null) {
            val ty = rowTop + (rowHeight - mc.font.lineHeight) / 2
            graphics.drawString(mc.font, hpText, cx, ty, Colors.MINECRAFT_RED.rgba, true)
            cx += hpWidth + if (items.isNotEmpty()) OVERHEAD_TEXT_ICON_GAP else 0
        }
        val iconTop = rowTop + (rowHeight - ICON_SIZE) / 2
        for (stack in items) {
            graphics.drawItemStack(stack, cx, iconTop)
            cx += ICON_SIZE + OVERHEAD_ICON_GAP
        }

        graphics.pose().popMatrix()
    }

    /**
     * Per-frame perspective scale for the overhead plate. [pxPerBlock] is how many gui pixels one
     * world block spans at the plate's depth (measured from the real render matrices), so the result
     * grows as the player approaches and shrinks as they recede — never capped. [scaleMultiplier] is
     * the user's [overheadScale] setting. Pure for testing the closer-is-bigger invariant.
     */
    internal fun overheadScaleFactor(pxPerBlock: Float, scaleMultiplier: Float): Float =
        pxPerBlock * (OVERHEAD_WORLD_HEIGHT / OVERHEAD_REF_PX) * scaleMultiplier

    /** Single-row panel dimensions (local px) for the overhead plate. Pure for layout testing. */
    internal fun overheadDimensions(hpWidth: Int, iconCount: Int, padding: Int, fontLineHeight: Int): OverheadDimensions {
        val gap = if (hpWidth > 0 && iconCount > 0) OVERHEAD_TEXT_ICON_GAP else 0
        val iconsWidth = if (iconCount <= 0) 0 else iconCount * ICON_SIZE + (iconCount - 1) * OVERHEAD_ICON_GAP
        val contentWidth = hpWidth + gap + iconsWidth
        if (contentWidth <= 0) return OverheadDimensions(0, 0)
        val rowHeight = if (iconCount > 0) ICON_SIZE else fontLineHeight
        return OverheadDimensions(contentWidth + padding * 2, rowHeight + padding * 2)
    }

    internal data class OverheadDimensions(val panelWidth: Int, val panelHeight: Int)

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
