package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.impl.*
import gg.floyd.features.Category
import gg.floyd.features.Module
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
        val hasOffscreen = activeCategories.any { category ->
            panelSetting[category.name]?.let { it.x < 0f || it.x + Panel.WIDTH > availableWidth } ?: true
        }
        val hasWrappedDefaults = activeCategories.any { category ->
            panelSetting[category.name]?.let { it.y > 10f } ?: true
        }
        if (hasMissing || hasOffscreen || hasWrappedDefaults) resetPositions()
    }

    fun resetPositions() {
        val activeCategories = Category.categories.values.toList()
        val availableWidth = mc.window.screenWidth / getStandardGuiScale()
        val gap = topRowPanelGap(availableWidth, activeCategories.size)
        activeCategories.forEachIndexed { index, category ->
            val setting = panelSetting.getOrPut(category.name) { PanelData() }
            setting.x = 10f + (Panel.WIDTH + gap) * index
            setting.y = 10f
            setting.extended = true
        }
    }

    private fun topRowPanelGap(availableWidth: Float, panelCount: Int): Float {
        if (panelCount <= 1) return 20f
        val defaultGap = 20f
        val defaultWidth = 20f + Panel.WIDTH * panelCount + defaultGap * (panelCount - 1)
        if (defaultWidth <= availableWidth) return defaultGap
        return ((availableWidth - 20f - Panel.WIDTH * panelCount) / (panelCount - 1)).coerceAtLeast(4f)
    }

    fun getStandardGuiScale(): Float {
        val verticalScale = (mc.window.screenHeight.toFloat() / 1080f) / NVGRenderer.devicePixelRatio()
        val horizontalScale = (mc.window.screenWidth.toFloat() / 1920f) / NVGRenderer.devicePixelRatio()
        return round(max(verticalScale, horizontalScale).coerceIn(1f, 1.5f) * 10f) / 10f
    }
}
