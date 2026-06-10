package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.font.MsdfFontMetrics
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.HudTextRenderer
import gg.floyd.utils.render.ItemStateRenderer
import gg.floyd.utils.render.PanelBlurPIPRenderer
import gg.floyd.utils.render.RoundRectPIPRenderer
import gg.floyd.utils.ui.rendering.PostHudOverlay
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

/**
 * Standalone toggle for the Floyd inventory HUD.
 *
 * Previously this was a buried HUD element + private `inventoryHudScale` inside [FloydHud]; it is
 * now its own module so the inventory HUD can be toggled and configured independently. All panel
 * cosmetics (background, border color/chroma/fade, corner radius, border width and frosted backdrop)
 * come from the global [FloydPanelStyle] via [HudPanel], so it matches every other Floyd panel.
 *
 * Like the scoreboard, the in-game render runs from the world-end post-HUD pass ([PostHudOverlay]),
 * straight to the main framebuffer (no PIP), so item icons can never clobber each other and the panel
 * stays visible under chat / inventory / the ClickGUI (which then blur over it).
 */
object FloydInventoryHud : Module(
    name = "Inventory HUD",
    category = Category.RENDER,
    description = "Displays the main inventory in a movable Floyd HUD.",
    toggled = true,
) {
    val inventoryHudScale by NumberSetting("Inventory HUD Scale", 1.1f, 0.5f, 5.0f, 0.05f, desc = "Inventory HUD scale.")

    // toggleable = false: the module toggle is the single on/off (no redundant inner toggle).
    private val inventoryHud by HUD("Inventory HUD", "Displays the main inventory in a movable Floyd HUD.", false, 12, 12, 1f) {
        drawInventoryHud(it)
    }

    // Reference GUI scale the panel is calibrated against. The slot grid is sized in framebuffer pixels as
    // 18 * inventoryHudScale * (guiScale / REFERENCE_GUI_SCALE) — i.e. it scales WITH Minecraft's effective
    // GUI scale (mc.window.guiScale), which itself tracks the framebuffer resolution. That makes the panel a
    // CONSTANT fraction of the screen on every platform/resolution/DPI (the whole cross-platform-scaling fix:
    // the old code used raw framebuffer px with no guiScale term, so it shrank on high-DPI displays and never
    // responded to the GUI Scale slider). REFERENCE_GUI_SCALE = 2 means "behaves exactly as before at GUI
    // scale 2, and proportionally everywhere else."
    private const val REFERENCE_GUI_SCALE = 2f

    /** One inventory slot in framebuffer pixels, normalized to [REFERENCE_GUI_SCALE] so it scales with guiScale. */
    private fun slotSizePx(): Int {
        val gs = mc.window.guiScale.coerceAtLeast(1).toFloat()
        return (18f * inventoryHudScale * gs / REFERENCE_GUI_SCALE).roundToInt().coerceAtLeast(12)
    }

    init {
        HudSizeRegistry.register("Inventory HUD") {
            val slotSize = slotSizePx()
            val pad = FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.INVENTORY).coerceAtLeast(0)
            9 * slotSize + 2 * pad to 3 * slotSize + 2 * pad
        }
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "inventoryHud" to mapOf(
            "enabled" to (enabled && inventoryHud.enabled),
            "scale" to inventoryHudScale,
            "x" to inventoryHud.x,
            "y" to inventoryHud.y,
            "hudScale" to inventoryHud.scale
        ),
        "cornerRadius" to FloydPanelStyle.cornerRadiusFor(FloydPanelStyle.PanelTarget.INVENTORY)
    )

    // HUD element callback: this NEVER draws — it only reports the panel's size for the HUD editor's drag
    // box. The actual panel (in game AND in the editor) is drawn by the single inline pass
    // [renderAtWorldEnd], so the editor and in-game use ONE rendering system (identical fonts/blur/border,
    // no overlap clobber).
    private fun GuiGraphics.drawInventoryHud(example: Boolean): Pair<Int, Int> {
        val slotSize = slotSizePx()
        val pad = FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.INVENTORY).coerceAtLeast(0)
        return (9 * slotSize + 2 * pad) to (3 * slotSize + 2 * pad)
    }

    /**
     * The single inventory-HUD render — drawn directly to the main framebuffer from the world-end post-HUD
     * pass ([PostHudOverlay]), both in game AND while the HUD editor is open (the editor just drags it). Uses
     * the HUD element's own framebuffer-pixel position/scale, so it shows regardless of any open screen.
     */
    // Stack-count text height as a fraction of one slot, so the count TRACKS the slot/item size at any
    // guiScale (a fixed px size would look tiny in the larger slots high-DPI displays now produce). 0.45 of
    // an 18-unit slot ≈ 8px, matching vanilla's count proportion.
    private const val COUNT_FONT_RATIO = 0.45f

    /**
     * Single-pass render from the world-end post-HUD pass ([PostHudOverlay]): the blaze3d SDF
     * fill/border, frosted blur and the 3D item models first, then the stack counts via the shared
     * mc.font helper ([HudTextRenderer]) — everything in framebuffer-pixel space, all pure blaze3d.
     * Geometry comes from the HUD element's framebuffer-pixel x/y/scale, so it is screen-independent.
     * Mirrors [FloydCustomScoreboard].
     */
    fun renderAtWorldEnd() {
        if (!enabled || !inventoryHud.enabled) return
        val inventory = mc.player?.inventory ?: return
        drawInventoryHudInline(inventory)
    }

    private fun drawInventoryHudInline(inventory: Inventory) {
        val target = FloydPanelStyle.PanelTarget.INVENTORY
        val scale = inventoryHud.scale
        val fx = inventoryHud.x.toFloat()
        val fy = inventoryHud.y.toFloat()

        val slotSize = slotSizePx()
        // Padding insets the slot grid from the border (so the panel's Padding setting has a visible effect).
        val pad = FloydPanelStyle.paddingFor(target).coerceAtLeast(0)
        val boxWidth = 9 * slotSize + 2 * pad
        val boxHeight = 3 * slotSize + 2 * pad
        val fw = boxWidth * scale
        val fh = boxHeight * scale
        val itemScale = slotSize / 18f

        val fill = FloydPanelStyle.backgroundColorFor(target).rgba
        val border = HudPanel.panelBorderColors(target, inventoryHud.x, inventoryHud.y)
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
        PostHudOverlay.bindMainFbo()

        // Item icons (drawn DIRECTLY, no PIP — fixes the old single-texture black-icon clobber). Each
        // item's local slot position/size (slot-grid px) maps to FRAMEBUFFER px (the same space the SDF
        // rect uses, so they co-locate): framebuffer = fx + local*scale, size = 16*itemScale*scale.
        val itemSizePx = 16f * itemScale * scale
        val itemPx = 16f * itemScale
        for (slot in 0 until 27) {
            val col = slot % 9
            val row = slot / 9
            val stack = inventory.getItem(slot + 9)
            if (stack.isEmpty) continue

            val localX = pad + col * slotSize + (slotSize - itemPx) / 2f
            val localY = pad + row * slotSize + (slotSize - itemPx) / 2f
            ItemStateRenderer.drawItemInline(stack, fx + localX * scale, fy + localY * scale, itemSizePx)
            PostHudOverlay.bindMainFbo()
        }

        // Stack counts via the shared mc.font helper (the global MSDF font) — drawn AFTER the item
        // models so the counts composite on top, in the same framebuffer-pixel space.
        // Count height tracks the slot so it stays proportional at any guiScale (see COUNT_FONT_RATIO).
        val countFontSize = slotSize * COUNT_FONT_RATIO
        val countScale = scale * countFontSize / MsdfFontMetrics.LINE_HEIGHT
        for (slot in 0 until 27) {
            val col = slot % 9
            val row = slot / 9
            val stack: ItemStack = inventory.getItem(slot + 9)
            if (stack.isEmpty || stack.count <= 1) continue

            val localX = pad + col * slotSize + (slotSize - itemPx) / 2f
            val localY = pad + row * slotSize + (slotSize - itemPx) / 2f
            // RIGHT-aligned to the item's bottom-right (vanilla placement), so the ones digit sits in a
            // fixed column — a 1-digit count and the ones digit of a 2-digit count line up instead of
            // drifting. The helper measures with the same MsdfFontMetrics floats the glyphs render with.
            val tx = fx + (localX + itemPx - 1f) * scale
            val ty = fy + (localY + itemPx - countFontSize - 1f) * scale
            HudTextRenderer.drawText(stack.count.toString(), tx, ty, countScale, 0xFFFFFFFF.toInt(), HudTextRenderer.Alignment.RIGHT)
        }
    }
}
