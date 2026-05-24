package com.odtheking.odin.features.impl.render

import com.odtheking.odin.FloydAddonsMod
import com.odtheking.odin.clickgui.ClickGUI
import com.odtheking.odin.clickgui.HudManager
import com.odtheking.odin.clickgui.Panel
import com.odtheking.odin.clickgui.settings.AlwaysActive
import com.odtheking.odin.clickgui.settings.impl.*
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.ui.rendering.NVGRenderer
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.round

@AlwaysActive
object ClickGUIModule : Module(
    name = "Click GUI",
    description = "Allows you to customize the UI.",
    key = GLFW.GLFW_KEY_RIGHT_SHIFT
) {
    val enableNotification by BooleanSetting("Chat notifications", true, desc = "Sends a message when you toggle a module with a keybind")
    val clickGUIColor by ColorSetting("Color", Color(50, 150, 220), desc = "The color of the Click GUI.")
    val buttonTextColor by ColorSetting("Button Text Color", Color(0xFFFFFFFF.toInt()), desc = "Legacy Floyd GUI button text color.")
    val buttonTextChroma by BooleanSetting("Button Text Chroma", true, desc = "Cycles legacy Floyd GUI button text through chroma.")
    val buttonTextFadeColor by ColorSetting("Button Text Fade Color", Color(0xFFFF55FF.toInt()), desc = "Legacy Floyd GUI button text fade color.")
    val buttonTextFade by BooleanSetting("Button Text Fade", false, desc = "Fades legacy Floyd GUI button text between two colors.")
    val buttonBorderColor by ColorSetting("Button Border Color", Color(0xFFFFFFFF.toInt()), desc = "Legacy Floyd GUI button border color.")
    val buttonBorderChroma by BooleanSetting("Button Border Chroma", true, desc = "Cycles legacy Floyd GUI button borders through chroma.")
    val buttonBorderFadeColor by ColorSetting("Button Border Fade Color", Color(0xFF55FFFF.toInt()), desc = "Legacy Floyd GUI button border fade color.")
    val buttonBorderFade by BooleanSetting("Button Border Fade", false, desc = "Fades legacy Floyd GUI button borders between two colors.")
    val guiBorderColor by ColorSetting("GUI Border Color", Color(0xFFFFFFFF.toInt()), desc = "Legacy Floyd GUI panel border color.")
    val guiBorderChroma by BooleanSetting("GUI Border Chroma", true, desc = "Cycles legacy Floyd GUI panel borders through chroma.")
    val guiBorderFadeColor by ColorSetting("GUI Border Fade Color", Color(0xFF5555FF.toInt()), desc = "Legacy Floyd GUI panel border fade color.")
    val guiBorderFade by BooleanSetting("GUI Border Fade", false, desc = "Fades legacy Floyd GUI panel borders between two colors.")

    val roundedPanelBottom by BooleanSetting("Rounded Panel Bottoms", true, desc = "Whether to extend panels to make them rounded at the bottom.")
    private val openGuiKey by KeybindSetting("Open Legacy GUI", GLFW.GLFW_KEY_N, desc = "FloydAddons legacy GUI key.").onPress {
        mc.setScreen(com.odtheking.odin.clickgui.LegacyFloydClickGUI.openHub())
    }

    private val action by ActionSetting("Open HUD Editor", desc = "Opens the HUD editor when clicked.") { mc.setScreen(HudManager) }

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
        "legacyButtonTextColor" to "#${buttonTextColor.hex()}",
        "legacyButtonTextChroma" to buttonTextChroma,
        "legacyButtonTextFadeColor" to "#${buttonTextFadeColor.hex()}",
        "legacyButtonTextFade" to buttonTextFade,
        "legacyButtonBorderColor" to "#${buttonBorderColor.hex()}",
        "legacyButtonBorderChroma" to buttonBorderChroma,
        "legacyButtonBorderFadeColor" to "#${buttonBorderFadeColor.hex()}",
        "legacyButtonBorderFade" to buttonBorderFade,
        "legacyGuiBorderColor" to "#${guiBorderColor.hex()}",
        "legacyGuiBorderChroma" to guiBorderChroma,
        "legacyGuiBorderFadeColor" to "#${guiBorderFadeColor.hex()}",
        "legacyGuiBorderFade" to guiBorderFade,
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
