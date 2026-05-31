package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.ItemStateRenderer.Companion.drawItemStack
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt

/**
 * Standalone toggle for the Floyd inventory HUD.
 *
 * Previously this was a buried HUD element + private `inventoryHudScale` inside [FloydHud]; it is
 * now its own module so the inventory HUD can be toggled and configured independently. The shared
 * rounded-corner radius, border width and frosted backdrop come from the global panel-appearance
 * settings on [FloydRender].
 */
object FloydInventoryHud : Module(
    name = "Inventory HUD",
    category = Category.RENDER,
    description = "Displays the main inventory in a movable Floyd HUD.",
    toggled = true,
) {
    val inventoryHudScale by NumberSetting("Inventory HUD Scale", 1.1f, 0.5f, 5.0f, 0.05f, desc = "Inventory HUD scale.")

    private val inventoryHud by HUD("Inventory HUD", "Displays the main inventory in a movable Floyd HUD.", true, 12, 12, 1f) {
        drawInventoryHud(it)
    }

    init {
        HudSizeRegistry.register("Inventory HUD") {
            val slotSize = (18 * inventoryHudScale).roundToInt().coerceAtLeast(12)
            9 * slotSize to 3 * slotSize
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
        "cornerRadius" to FloydRender.panelCornerRadius
    )

    private fun GuiGraphics.drawInventoryHud(example: Boolean): Pair<Int, Int> {
        val inventory = mc.player?.inventory
        val slotSize = (18 * inventoryHudScale).roundToInt().coerceAtLeast(12)
        val width = 9 * slotSize
        val height = 3 * slotSize
        HudPanel.fillPanel(this, 0, 0, width, height, HudPanel.monochrome(HudPanel.chromaColor(0f)))

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
}
