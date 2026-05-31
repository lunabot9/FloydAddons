package gg.floyd.features.impl.render

import gg.floyd.clickgui.LegacyFloydClickGUI
import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.impl.*
import gg.floyd.features.Module
import gg.floyd.utils.Color
import org.lwjgl.glfw.GLFW

/**
 * Owns the styling settings for the legacy fullscreen Floyd GUI ([LegacyFloydClickGUI]).
 *
 * These nine button/border colour and fade settings used to live in [ClickGUIModule], where they
 * were orphaned under the Odin GUI even though only the legacy GUI reads them. They now have a
 * dedicated home so the legacy GUI's settings are self-contained and discoverable. Opening the
 * module (via its keybind or toggle) opens the legacy GUI hub.
 */
@AlwaysActive
object LegacyClickGUIModule : Module(
    name = "Legacy Click GUI",
    description = "Customizes and opens the legacy fullscreen Floyd GUI.",
    key = GLFW.GLFW_KEY_RIGHT_SHIFT
) {
    val buttonTextColor by ColorSetting("Button Text Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Legacy Floyd GUI button text color (toggle chroma inside the picker).")
    val buttonTextFadeColor by ColorSetting("Button Text Fade Color", Color(0xFFFF55FF.toInt()), desc = "Legacy Floyd GUI button text fade color.")
    val buttonTextFade by BooleanSetting("Button Text Fade", false, desc = "Fades legacy Floyd GUI button text between two colors.")
    val buttonBorderColor by ColorSetting("Button Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Legacy Floyd GUI button border color (toggle chroma inside the picker).")
    val buttonBorderFadeColor by ColorSetting("Button Border Fade Color", Color(0xFF55FFFF.toInt()), desc = "Legacy Floyd GUI button border fade color.")
    val buttonBorderFade by BooleanSetting("Button Border Fade", false, desc = "Fades legacy Floyd GUI button borders between two colors.")
    val guiBorderColor by ColorSetting("GUI Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Legacy Floyd GUI panel border color (toggle chroma inside the picker).")
    val guiBorderFadeColor by ColorSetting("GUI Border Fade Color", Color(0xFF5555FF.toInt()), desc = "Legacy Floyd GUI panel border fade color.")
    val guiBorderFade by BooleanSetting("GUI Border Fade", false, desc = "Fades legacy Floyd GUI panel borders between two colors.")

    override fun onKeybind() {
        toggle()
    }

    override fun onEnable() {
        mc.setScreen(LegacyFloydClickGUI.openHub())
        super.onEnable()
        toggle()
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "buttonTextColor" to "#${buttonTextColor.hex()}",
        "buttonTextChroma" to buttonTextColor.chroma,
        "buttonTextFadeColor" to "#${buttonTextFadeColor.hex()}",
        "buttonTextFade" to buttonTextFade,
        "buttonBorderColor" to "#${buttonBorderColor.hex()}",
        "buttonBorderChroma" to buttonBorderColor.chroma,
        "buttonBorderFadeColor" to "#${buttonBorderFadeColor.hex()}",
        "buttonBorderFade" to buttonBorderFade,
        "guiBorderColor" to "#${guiBorderColor.hex()}",
        "guiBorderChroma" to guiBorderColor.chroma,
        "guiBorderFadeColor" to "#${guiBorderFadeColor.hex()}",
        "guiBorderFade" to guiBorderFade
    )
}
