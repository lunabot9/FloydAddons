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
import gg.floyd.features.impl.render.FloydPanelStyle
import gg.floyd.utils.Colors
import gg.floyd.utils.RealPlayerFilter
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
    private val realPlayersOnly by BooleanSetting("Real Players Only", true, desc = "Hides server-faked NPC \"players\" (command holograms, decorated names) that aren't in the tab player list.")

    // Overhead nameplate: a panel pinned a fixed distance above the head that scales with distance
    // exactly like the ESP box (it is a physical, constant-world-size object — like a real nametag).
    // Background, corner radius, border width and padding come from the global FloydPanelStyle; only
    // the optional "Border = ESP Color" override is kept here so the nameplate border can track the
    // ESP color instead of the global panel border.
    private val overheadScale by NumberSetting("Overhead Scale", 1.0f, 0.25f, 5.0f, 0.05f, desc = "Multiplier on the overhead nameplate's world size. It still shrinks with distance like a real nametag.")
    private val borderIsEspColor by BooleanSetting("Border = ESP Color", true, desc = "Use the ESP color for the overhead nameplate border instead of the global panel border.")

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
    // Sub-pixel plates are invisible AND would allocate a degenerate 0-size GPU texture (OpenGL error
    // 1281). Skip below this on-screen size. Not a scaling cap — just a "too far to see" cull.
    private const val OVERHEAD_MIN_PX = 2f
    // GPU safety: keep the panel texture well under the driver's max texture size. Only reached at
    // point-blank range; does not affect the scaling at normal distances.
    private const val OVERHEAD_MAX_PX = 4000f
    // Time constant (seconds) for the per-player low-pass on the overhead plate's perspective scale.
    // The raw depth-derived size jitters frame-to-frame while jumping in place / walking toward or away
    // from a player (the jump arc + view bob wobble the eye-space depth), which reads as the plate
    // snapping bigger/smaller. Smoothing the SIZE only (never the position) animates it cleanly. ~0.14s
    // kills the flicker while staying responsive to real approach.
    private const val OVERHEAD_SCALE_TAU = 0.14f
    // Coarse geometric grid (~8% per level) the plate's on-screen size snaps to. Holding the size on a
    // grid level keeps the rounded panel's integer pixel dimensions identical frame-to-frame, so the
    // border/text/icons stop re-rasterizing (the "flickering/redrawing" while walking or jumping in
    // place); the plate only re-rasters during a rare, eased transition when you move far enough to
    // cross a level. Hysteresis (in [stickyQuantize]) prevents boundary chatter; OVERHEAD_SCALE_TAU
    // eases each step so it animates as a clean monotonic zoom instead of popping.
    private const val OVERHEAD_SCALE_STEP = 1.08f
    private val overheadScaleSmooth = HashMap<java.util.UUID, Float>()
    private val overheadLevel = HashMap<java.util.UUID, Float>()
    private var lastOverheadScaleMs = 0L

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
        return mc.level?.players()?.filter {
            it !== self && !it.isSpectator && (!realPlayersOnly || RealPlayerFilter.isRealPlayer(it))
        } ?: emptyList()
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

        // Per-frame low-pass coefficient (time-constant based, so it is frame-rate independent).
        val now = System.currentTimeMillis()
        val dt = if (lastOverheadScaleMs == 0L) 0.016f else (now - lastOverheadScaleMs).coerceIn(1L, 100L) / 1000f
        lastOverheadScaleMs = now
        val alpha = (1f - kotlin.math.exp(-dt / OVERHEAD_SCALE_TAU)).coerceIn(0f, 1f)
        val seen = HashSet<java.util.UUID>()

        // Farthest-first so a nearer player's plate always draws on top of a farther one when they overlap.
        for (player in otherPlayers().sortedByDescending { it.distanceToSqr(self) }) {
            if (player.distanceToSqr(self) > 64.0 * 64.0) continue
            // Anchor = the plate's bottom-center, a fixed world distance above the head (above the nametag).
            val anchor = player.renderPos.add(0.0, player.bbHeight + OVERHEAD_ANCHOR_OFFSET, 0.0)
            val anchorScreen = WorldToScreen.project(anchor) ?: continue
            // Perspective scale from the anchor's eye-space depth, NOT by projecting a vertical world
            // offset: a `+1` block offset foreshortens to ~0 px when looking up/down (size breaks
            // above/below) and oscillates under view-bob while moving/jumping (flicker). Depth-based
            // scale is view-pitch-independent and bob-steady.
            val target = WorldToScreen.screenScale(anchor) ?: continue
            if (target <= 0.01f) continue
            // Snap the on-screen size to a coarse geometric grid (with hysteresis) so the rounded panel's
            // integer pixel dimensions hold steady between rare, eased steps instead of re-rasterizing
            // (flickering) every frame as the depth drifts while walking toward/away or jumping in place.
            val level = stickyQuantize(target, overheadLevel[player.uuid] ?: 0f, OVERHEAD_SCALE_STEP)
            overheadLevel[player.uuid] = level
            // Ease the rendered size toward the committed grid level so a level change animates as a clean
            // monotonic zoom instead of popping; snap once settled so it holds pixel-identical. The
            // POSITION (anchorScreen) is never smoothed, so the plate still tracks the head precisely.
            val prev = overheadScaleSmooth[player.uuid]
            val pxPerBlock = if (prev == null) level else {
                val next = prev + (level - prev) * alpha
                if (kotlin.math.abs(level - next) < level * 0.005f) level else next
            }
            overheadScaleSmooth[player.uuid] = pxPerBlock
            seen.add(player.uuid)
            drawOverheadEntry(graphics, player, anchorScreen.x, anchorScreen.y, overheadScaleFactor(pxPerBlock, overheadScale))
        }
        // Drop smoothing state for players no longer drawn so the maps can't grow unbounded.
        if (overheadScaleSmooth.size != seen.size) {
            overheadScaleSmooth.keys.retainAll(seen)
            overheadLevel.keys.retainAll(seen)
        }
    }

    private fun drawOverheadEntry(graphics: GuiGraphics, player: AbstractClientPlayer, anchorX: Float, anchorY: Float, scale: Float) {
        val pad = FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.ESP_OVERHEAD).coerceAtLeast(0)
        val hpText = if (showHealth) "$HEART ${player.health.toInt()}" else null
        val hpWidth = hpText?.let { mc.font.width(it) } ?: 0
        val items = if (showEquipment) equipmentOf(player).filter { !it.isEmpty } else emptyList()

        val dims = overheadDimensions(hpWidth, items.size, pad, mc.font.lineHeight)
        if (dims.panelWidth <= 0) return

        // Per-frame on-screen size of the plate (the panel extends UP from the anchor).
        val scaledW = scale * dims.panelWidth
        val scaledH = scale * dims.panelHeight
        if (scaledW < OVERHEAD_MIN_PX || scaledH < OVERHEAD_MIN_PX) return
        // Cull plates entirely off-screen so we don't render (and texture-allocate) a plate per
        // off-screen player on a crowded server.
        val guiW = mc.window.guiScaledWidth.toFloat()
        val guiH = mc.window.guiScaledHeight.toFloat()
        val halfW = scaledW / 2f
        if (anchorX + halfW < 0f || anchorX - halfW > guiW || anchorY < 0f || anchorY - scaledH > guiH) return
        // Clamp only when the texture would approach the GPU's max size (point-blank); preserves aspect.
        val drawScale = if (scaledW > OVERHEAD_MAX_PX || scaledH > OVERHEAD_MAX_PX)
            scale * (OVERHEAD_MAX_PX / maxOf(scaledW, scaledH)) else scale

        // Plate bottom-center sits at the anchor and extends upward; everything inside the pushed
        // pose (panel, text, icons) is scaled together by the per-frame perspective factor.
        val left = -dims.panelWidth / 2
        val top = -dims.panelHeight
        graphics.pose().pushMatrix()
        graphics.pose().translate(anchorX, anchorY)
        graphics.pose().scale(drawScale, drawScale)

        // "Border = ESP Color" tints the nameplate border with this player's ESP color (honouring its
        // chroma flag); otherwise it falls back to the global FloydPanelStyle border.
        if (borderIsEspColor) {
            HudPanel.fillPanel(graphics, left, top, left + dims.panelWidth, top + dims.panelHeight, FloydPanelStyle.PanelTarget.ESP_OVERHEAD, HudPanel.monochrome(color.rgba))
        } else {
            HudPanel.fillPanel(graphics, left, top, left + dims.panelWidth, top + dims.panelHeight, FloydPanelStyle.PanelTarget.ESP_OVERHEAD)
        }

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

    /**
     * Snaps a positive on-screen size to the nearest level of the geometric grid `step^n`. Keeping the
     * plate's size on a grid level makes its integer pixel dimensions identical frame-to-frame, so the
     * rounded panel/border stops re-rasterizing as the depth drifts. Pure for testing.
     */
    internal fun quantizeScale(value: Float, step: Float): Float {
        if (value <= 0f || step <= 1f) return value
        val n = Math.round(Math.log(value.toDouble()) / Math.log(step.toDouble()))
        return Math.pow(step.toDouble(), n.toDouble()).toFloat()
    }

    /**
     * [quantizeScale] with hysteresis: holds the previously committed grid level until [value] drifts
     * more than ~70% of a step away from it (in log space), then snaps to the new nearest level. The
     * dead-band stops residual depth jitter near a level boundary from flipping the plate size back and
     * forth (chatter) while standing still or jumping in place. Pure for testing.
     */
    internal fun stickyQuantize(value: Float, prevLevel: Float, step: Float): Float {
        if (prevLevel <= 0f) return quantizeScale(value, step)
        if (value <= 0f || step <= 1f) return prevLevel
        val logRatio = Math.log((value / prevLevel).toDouble())
        if (Math.abs(logRatio) < Math.log(step.toDouble()) * 0.7) return prevLevel
        return quantizeScale(value, step)
    }

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
