package gg.floyd.features.impl.render

import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.impl.*
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.ChromaCache
import gg.floyd.utils.Color
import gg.floyd.utils.ui.rendering.NVGRenderer
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.round

@AlwaysActive
object ClickGUIModule : Module(
    name = "Click GUI",
    description = "Allows you to customize the UI."
) {
    val enableNotification by BooleanSetting("Chat notifications", true, desc = "Sends a message when you toggle a module with a keybind")
    val clickGUIColor by ColorSetting("Color", Color(50, 150, 220).also { it.chroma = true }, desc = "The color of the Click GUI — toggle chroma/fade inside the picker.")

    val roundedPanelBottom by BooleanSetting("Rounded Panel Bottoms", true, desc = "Whether to extend panels to make them rounded at the bottom.")
    private val openGuiKey by KeybindSetting("Open GUI Key", GLFW.GLFW_KEY_N, desc = "FloydAddons alternate GUI key.").onPress {
        mc.setScreen(ClickGUI)
    }

    override fun onKeybind() {
        toggle()
    }

    override fun onEnable() {
        mc.setScreen(ClickGUI)
        super.onEnable()
        toggle()
    }

    val panelSetting by MapSetting("Panel Settings", mutableMapOf<String, PanelData>())
    data class PanelData(var x: Float = 10f, var y: Float = 10f, var extended: Boolean = true)

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "chatNotifications" to enableNotification,
        "color" to "#${clickGUIColor.hex()}",
        "chroma" to clickGUIColor.chroma,
        "roundedPanelBottoms" to roundedPanelBottom,
        "panelCount" to panelSetting.size,
        "panels" to panelSetting.mapValues { (_, panel) ->
            mapOf(
                "x" to panel.x,
                "y" to panel.y,
                "extended" to panel.extended
            )
        }
    )

    fun ensurePanelPositionsFit() {
        val activeCategories = Category.categories.values.toList()
        val availableWidth = mc.window.screenWidth / getStandardGuiScale()
        val hasMissing = activeCategories.any { panelSetting[it.name] == null }
        // Any panel whose right edge falls past the window means the layout no longer fits (e.g. the
        // window shrank, or categories changed) — re-flow. Wrapped rows (y > 10) are NOT a trigger;
        // they are a valid multi-row layout and may also be the user's own dragged arrangement.
        val hasOffscreen = activeCategories.any { category ->
            panelSetting[category.name]?.let { it.x < 0f || it.x + Panel.WIDTH > availableWidth } ?: true
        }
        val hasStackedDefaults = activeCategories
            .mapNotNull { category -> panelSetting[category.name]?.let { category.name to it } }
            .groupBy { (_, panel) -> panel.x to panel.y }
            .values
            .any { entries -> entries.size > 1 }
        if (hasMissing || hasOffscreen || hasStackedDefaults) resetPositions()
    }

    fun resetPositions() {
        defaultPanelLayout().forEach { (categoryName, layout) ->
            val setting = panelSetting.getOrPut(categoryName) { PanelData() }
            setting.x = layout.x
            setting.y = layout.y
            setting.extended = true
        }
    }

    fun defaultPanelLayout(): Map<String, PanelData> {
        val activeCategories = Category.categories.values.toList()
        val availableWidth = mc.window.screenWidth / getStandardGuiScale()
        val gap = 20f
        val rowGap = 14f
        var x = 10f
        var y = 10f
        var rowMaxHeight = 0f
        val layout = linkedMapOf<String, PanelData>()
        activeCategories.forEach { category ->
            // Wrap to a new row when the panel would extend past the right edge, so no panel is ever
            // pushed off-screen regardless of window width or how many categories exist.
            if (x > 10f && x + Panel.WIDTH > availableWidth) {
                x = 10f
                y += rowMaxHeight + rowGap
                rowMaxHeight = 0f
            }
            layout[category.name] = PanelData(x = x, y = y, extended = true)
            rowMaxHeight = max(rowMaxHeight, estimatedPanelHeight(category))
            x += Panel.WIDTH + gap
        }
        return layout
    }

    /** Rough extended-panel height (header + one row per module) used for default row-wrapping spacing. */
    private fun estimatedPanelHeight(category: Category): Float {
        val moduleCount = ModuleManager.modulesByCategory[category]?.size ?: 0
        return Panel.HEIGHT + moduleCount * MODULE_ROW_HEIGHT
    }

    private const val MODULE_ROW_HEIGHT = 16f

    /**
     * The Click GUI accent color. When [clickGUIColor] has chroma enabled (inside the color picker) it
     * chroma-cycles (reusing the memoized [ChromaCache] so the rainbow hue is computed at most once per
     * frame per [offset]); otherwise it is the static [clickGUIColor]. Chroma/fade now live in the
     * picker, so there is no separate "GUI Chroma" toggle.
     */
    fun guiAccentColor(offset: Float = 0f): Int =
        if (clickGUIColor.chroma) 0xFF000000.toInt() or ChromaCache.rgbFor(offset) else clickGUIColor.rgba

    fun getStandardGuiScale(): Float {
        val verticalScale = (mc.window.screenHeight.toFloat() / 1080f) / NVGRenderer.devicePixelRatio()
        val horizontalScale = (mc.window.screenWidth.toFloat() / 1920f) / NVGRenderer.devicePixelRatio()
        return round(max(verticalScale, horizontalScale).coerceIn(1f, 1.5f) * 10f) / 10f
    }
}
