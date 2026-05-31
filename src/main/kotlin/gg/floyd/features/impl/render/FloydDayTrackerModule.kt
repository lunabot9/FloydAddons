package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Colors
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt

/**
 * Standalone toggle for the Floyd day tracker HUD.
 *
 * Draws the current server day (derived from the level's day-time) in a movable Floyd HUD. The
 * shared rounded-corner radius, border width and frosted backdrop come from the global panel
 * appearance settings on [FloydRender] via [HudPanel], so it inherits the unified panel style.
 *
 * The module owns its own [HUD] element (mirroring [FloydInventoryHud]) so the day tracker can be
 * toggled and positioned independently without sharing a HUDSetting with another module (which
 * would double-register it in the manager's HUD cache and double-draw the panel).
 */
object FloydDayTrackerModule : Module(
    name = "Day Tracker",
    category = Category.RENDER,
    description = "Displays the current server day in a movable Floyd HUD.",
    toggled = false,
) {
    private val dayTrackerHud by HUD("Day Tracker", "Displays the current server day in a movable Floyd HUD.", true, 24, 180, 1f) {
        drawDayTrackerHud(it)
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "dayTracker" to mapOf(
            "enabled" to (enabled && dayTrackerHud.enabled),
            "x" to dayTrackerHud.x,
            "y" to dayTrackerHud.y,
            "scale" to dayTrackerHud.scale,
            "day" to currentServerDay(),
            "label" to currentServerDayLabel()
        ),
        "cornerRadius" to FloydRender.panelCornerRadius
    )

    private fun currentServerDay(): Long? = mc.level?.let { (it.dayTime / 24000L) + 1L }

    private fun currentServerDayLabel(): String? = currentServerDay()?.let { "Day $it" }

    private fun GuiGraphics.drawDayTrackerHud(example: Boolean): Pair<Int, Int> {
        val label = currentServerDayLabel() ?: if (example) "Day 1" else return 0 to 0

        val paddingX = 6f
        val paddingY = 6f
        val fontSize = 12f
        val width = (NVGRenderer.textWidth(label, fontSize, NVGRenderer.defaultFont) + paddingX * 2f).roundToInt()
        val height = (fontSize + paddingY * 2f).roundToInt()

        HudPanel.fillPanel(this, 0, 0, width, height, HudPanel.monochrome(0xFFFFFFFF.toInt()))

        NVGPIPRenderer.draw(this, 0, 0, width, height) {
            NVGRenderer.text(label, paddingX, paddingY, fontSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        }
        return width to height
    }
}
