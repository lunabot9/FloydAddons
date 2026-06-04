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
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.ItemStateRenderer.Companion.drawItemStack
import gg.floyd.utils.render.ItemStateRenderer.Companion.drawItemWorld
import gg.floyd.utils.render.RoundRectPIPRenderer
import gg.floyd.utils.render.drawTracer
import gg.floyd.utils.render.drawWireFrameBox
import gg.floyd.utils.renderBoundingBox
import gg.floyd.utils.renderPos
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf

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

    // Overhead nameplate tuning (local font-px; the GPU perspective scales the world billboard):
    private const val OVERHEAD_ICON_GAP = 2          // gap between equipment icons on the single row
    private const val OVERHEAD_TEXT_ICON_GAP = 4     // gap between the health text and the first icon
    // World height above the player's bounding box where the plate's bottom edge floats (above the nametag).
    private const val OVERHEAD_ANCHOR_OFFSET = 0.6
    // Fixed world size of one local font-pixel (vanilla nametag 1/40 == 0.025; renderQueuedTexts uses the
    // same). [overheadScale] multiplies it. NO per-frame/per-player scaling, smoothing, quantize or easing —
    // pure GPU perspective sizes the billboard, so it is correct on the FIRST frame a player appears (this
    // is the entire fix for "starts huge then shrinks").
    private const val OVERHEAD_WORLD_PX = 0.025f

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
                if (boxes) drawWireFrameBox(other.renderBoundingBox, c, thickness = 1.5f, depth = false)
                if (tracers) drawTracer(other.renderPos.add(0.0, other.bbHeight / 2.0, 0.0), c, depth = false, thickness = 1.5f)
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
     * WORLD-SPACE billboard overhead, drawn from RenderUtils' RenderEvent.Last (END_MAIN) pass with the
     * world PoseStack + camera + camera rotation — the same basis renderQueuedTexts billboards nametag
     * text with. The plate is a constant-world-size object over each player; pure GPU perspective sizes it,
     * so a freshly-appearing player draws at the steady size on its FIRST frame (no "starts huge"). There is
     * no screen projection, pxPerBlock, smoothing, quantize or easing anywhere — only the fixed world S.
     */
    fun renderOverheadBillboard(pose: PoseStack, camera: Vec3, cameraRotation: Quaternionf, bufferSource: MultiBufferSource.BufferSource) {
        if (!enabled) return
        if (display != 0 && display != 2) return
        if (!showHealth && !showEquipment) return
        val self = mc.player ?: return

        // Equipment icons are real item models drawn through the vanilla item path, which depth-tests
        // against the world — so they'd vanish behind any block between you and the player, while the
        // rounded plate (no-depth SDF) and the health text (see-through) sit on top. Clear the main depth
        // once here so the icons composite on top of the plate like the rest of it. Safe: the first-person
        // hand renders after this (END_MAIN) and always draws on top regardless of prior depth.
        if (showEquipment) {
            mc.mainRenderTarget.depthTexture?.let { depth ->
                RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depth, 1.0)
            }
        }

        // Farthest-first so a nearer plate composits on top of a farther one when they overlap.
        for (player in otherPlayers().sortedByDescending { it.distanceToSqr(self) }) {
            if (player.distanceToSqr(self) > 64.0 * 64.0) continue
            val anchor = player.renderPos.add(0.0, player.bbHeight + OVERHEAD_ANCHOR_OFFSET, 0.0)
            drawOverheadBillboard(pose, player, anchor, camera, cameraRotation, bufferSource)
        }
    }

    private fun drawOverheadBillboard(
        pose: PoseStack, player: AbstractClientPlayer, anchor: Vec3, camera: Vec3, cameraRotation: Quaternionf,
        bufferSource: MultiBufferSource.BufferSource
    ) {
        val target = FloydPanelStyle.PanelTarget.ESP_OVERHEAD
        // The overhead is a COMPACT floating nameplate, not a full HUD panel. The shared panel-style
        // padding/radius are tuned for big screen panels (scoreboard/inventory) and look chunky/over-padded
        // on this small world plate, so halve them here for a tighter nameplate (clamped to a sane minimum).
        val pad = (FloydPanelStyle.paddingFor(target) / 2).coerceAtLeast(1)
        val hpText = if (showHealth) "$HEART ${player.health.toInt()}" else null
        val hpWidth = hpText?.let { mc.font.width(it) } ?: 0
        val items = if (showEquipment) equipmentOf(player).filter { !it.isEmpty } else emptyList()

        val dims = overheadDimensions(hpWidth, items.size, pad, mc.font.lineHeight)
        if (dims.panelWidth <= 0) return

        val s = OVERHEAD_WORLD_PX * overheadScale

        // Billboard BASIS = world PoseStack (identity model) . translate(anchor-camera) . rotate(camRot)
        // . scale(s,-s,s). After this 1 local unit == 1 font-px, +x screen-right, +y screen-DOWN, origin ==
        // anchor. Identical recipe to RenderUtils.renderQueuedTexts.
        val basis = Matrix4f(pose.last().pose())
            .translate((anchor.x - camera.x).toFloat(), (anchor.y - camera.y).toFloat(), (anchor.z - camera.z).toFloat())
            .rotate(cameraRotation)
            .scale(s, -s, s)

        // The health text below billboards correctly because mc.font bakes `basis` into its vertices and
        // then bufferSource.endBatch() multiplies by RenderSystem.getModelViewMatrix() (the world camera
        // VIEW rotation) at flush time. The rect (drawWorld sets DynamicTransforms.ModelViewMat directly)
        // and the item icons (drawItemWorld pushes the modelview to identity) DON'T get that view matrix
        // applied for free, so they were missing the camera rotation and swam around the camera. Fold the
        // same view matrix into their transforms up front so all three share one world transform: this is
        // the matrix the text's flush applies, captured once here.
        val viewBasis = Matrix4f(RenderSystem.getModelViewMatrix()).mul(basis)

        // Local panel rect: bottom edge on the anchor -> top-left = (-w/2, -h).
        val left = (-dims.panelWidth / 2).toFloat()
        val top = (-dims.panelHeight).toFloat()

        val fill = FloydPanelStyle.backgroundColorFor(target).rgba
        // "Border = ESP Color" tints with this player's ESP color (honouring chroma); else the global panel
        // border, seeded by the (stable) world anchor so adjacent plates animate out of phase.
        val border = if (borderIsEspColor) HudPanel.monochrome(color.rgba)
            else HudPanel.panelBorderColors(target, anchor.x.toInt(), anchor.z.toInt())
        val radius = (FloydPanelStyle.cornerRadiusFor(target) / 2f).coerceAtLeast(1f)
        // Thinner border too — the full HUD border width reads as a heavy, chunky outline on the small plate.
        val outline = (FloydPanelStyle.borderWidthFor(target) / 2f).coerceAtLeast(1f)

        // 1) Rounded panel + border (world MVP = view×basis translated to the rect's local top-left).
        RoundRectPIPRenderer.drawWorld(
            Matrix4f(viewBasis).translate(left, top, 0f),
            dims.panelWidth.toFloat(), dims.panelHeight.toFloat(),
            fill, fill, fill, fill,
            radius, radius, radius, radius,
            border.topLeft, border.topRight, border.bottomRight, border.bottomLeft,
            outline
        )

        // ---- Content row (font-px local space, +y DOWN; the basis -y flip already matches glyph layout). ----
        val rowHeight = if (items.isNotEmpty()) ICON_SIZE else mc.font.lineHeight
        val rowTop = top + pad

        // 2) Heart + health text — same recipe as renderQueuedTexts. mc.font keeps the heart glyph.
        if (hpText != null) {
            val tyLocal = rowTop + (rowHeight - mc.font.lineHeight) / 2f
            mc.font.drawInBatch(
                hpText, left + pad, tyLocal, Colors.MINECRAFT_RED.rgba, true,
                basis, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT
            )
            bufferSource.endBatch()   // composit this plate's text immediately over its own rect
        }

        // 3) Equipment icons — each centered in an ICON_SIZE local box, under the same basis. The
        //    scale(+,-,+) inside drawItemWorld's MVP undoes the basis -y flip so the item is upright.
        if (items.isNotEmpty()) {
            val gapAfterText = if (hpText != null) OVERHEAD_TEXT_ICON_GAP else 0
            var cxLocal = left + pad + hpWidth + gapAfterText
            val iconTopLocal = rowTop + (rowHeight - ICON_SIZE) / 2f
            for (stack in items) {
                val iconMv = Matrix4f(viewBasis)
                    .translate(cxLocal + ICON_SIZE / 2f, iconTopLocal + ICON_SIZE / 2f, 0f)
                    .scale(ICON_SIZE.toFloat(), -ICON_SIZE.toFloat(), ICON_SIZE.toFloat())
                drawItemWorld(stack, iconMv)
                cxLocal += ICON_SIZE + OVERHEAD_ICON_GAP
            }
        }
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
