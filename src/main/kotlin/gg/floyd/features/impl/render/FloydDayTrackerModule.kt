package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.render.HudPanel
import net.minecraft.client.gui.GuiGraphics

/**
 * Standalone toggle for the Floyd day tracker HUD.
 *
 * Draws the current server day (derived from the level's day-time) in a movable Floyd HUD. All
 * panel cosmetics (background, border color/chroma/fade, corner radius, border width and frosted
 * backdrop) come from the global [FloydPanelStyle] via [HudPanel], so it inherits the unified style.
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
    // toggleable = false: this module's only purpose is the HUD, so the module's own on/off is the
    // single switch (no redundant inner toggle); the HUD stays draggable via the move icon.
    private val dayTrackerHud by HUD("Day Tracker", "Displays the current server day in a movable Floyd HUD.", false, 24, 180, 1f) {
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
        "cornerRadius" to FloydPanelStyle.cornerRadiusFor(FloydPanelStyle.PanelTarget.DAY_TRACKER)
    )

    private fun currentServerDay(): Long? = mc.level?.let { (it.dayTime / 24000L) + 1L }

    private fun currentServerDayLabel(): String? = currentServerDay()?.let { "Day $it" }

    private fun GuiGraphics.drawDayTrackerHud(example: Boolean): Pair<Int, Int> {
        val label = currentServerDayLabel() ?: if (example) "Day 1" else return 0 to 0

        // Drawn with mc.font (drawString), NOT a second NVG PIP: PictureInPictureRenderer keeps one
        // shared backing texture per render-state class, so a 2nd NVGPIPRenderer panel in the same frame
        // fights the Custom Scoreboard's PIP (last-writer-wins) and flickers the panel border. The label
        // is one short string, so the mc.font path looks fine and stays PIP-free. (With the global custom
        // font on, mc.font is already the Floyd font, so it still reads as the custom look.)
        val padding = FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.DAY_TRACKER).coerceAtLeast(0)
        val width = mc.font.width(label) + padding * 2
        val height = mc.font.lineHeight + padding * 2

        HudPanel.fillPanel(this, 0, 0, width, height, FloydPanelStyle.PanelTarget.DAY_TRACKER)
        drawString(mc.font, label, padding, padding, 0xFFFFFFFF.toInt(), true)
        return width to height
    }
}
