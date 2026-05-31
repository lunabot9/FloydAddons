package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod
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
    val clickGUIColor by ColorSetting("Color", Color(50, 150, 220), desc = "The color of the Click GUI.")
    val clickGUIChroma by BooleanSetting("GUI Chroma", false, desc = "Cycles the Click GUI accent color through chroma.")

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
        "chroma" to clickGUIChroma,
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
        if (hasMissing || hasOffscreen) resetPositions()
    }

    fun resetPositions() {
        val activeCategories = Category.categories.values.toList()
        val availableWidth = mc.window.screenWidth / getStandardGuiScale()
        val gap = 20f
        val rowGap = 14f
        var x = 10f
        var y = 10f
        var rowMaxHeight = 0f
        activeCategories.forEach { category ->
            // Wrap to a new row when the panel would extend past the right edge, so no panel is ever
            // pushed off-screen regardless of window width or how many categories exist.
            if (x > 10f && x + Panel.WIDTH > availableWidth) {
                x = 10f
                y += rowMaxHeight + rowGap
                rowMaxHeight = 0f
            }
            val setting = panelSetting.getOrPut(category.name) { PanelData() }
            setting.x = x
            setting.y = y
            setting.extended = true
            rowMaxHeight = max(rowMaxHeight, estimatedPanelHeight(category))
            x += Panel.WIDTH + gap
        }
    }

    /** Rough extended-panel height (header + one row per module) used for default row-wrapping spacing. */
    private fun estimatedPanelHeight(category: Category): Float {
        val moduleCount = ModuleManager.modulesByCategory[category]?.size ?: 0
        return Panel.HEIGHT + moduleCount * MODULE_ROW_HEIGHT
    }

    private const val MODULE_ROW_HEIGHT = 16f

    /**
     * The Click GUI accent color. When [clickGUIChroma] is enabled it chroma-cycles
     * (reusing the memoized [ChromaCache] so the rainbow hue is computed at most once
     * per frame per [offset]); otherwise it is the static [clickGUIColor].
     */
    fun guiAccentColor(offset: Float = 0f): Int =
        if (clickGUIChroma) 0xFF000000.toInt() or ChromaCache.rgbFor(offset) else clickGUIColor.rgba

    fun getStandardGuiScale(): Float {
        val verticalScale = (mc.window.screenHeight.toFloat() / 1080f) / NVGRenderer.devicePixelRatio()
        val horizontalScale = (mc.window.screenWidth.toFloat() / 1920f) / NVGRenderer.devicePixelRatio()
        return round(max(verticalScale, horizontalScale).coerceIn(1f, 1.5f) * 10f) / 10f
    }
}
