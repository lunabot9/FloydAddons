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
 * These button/border colour settings used to live in [ClickGUIModule], where they were orphaned
 * under the Odin GUI even though only this GUI reads them. They now have a dedicated home so the GUI's
 * settings are self-contained and discoverable; chroma/fade are configured inside each color picker
 * (the old separate fade toggles/colors were removed). Opening the module (via its keybind or toggle)
 * opens the Floyd GUI hub.
 */
@AlwaysActive
object LegacyClickGUIModule : Module(
    name = "Floyd GUI",
    description = "Customizes and opens the fullscreen Floyd GUI.",
    key = GLFW.GLFW_KEY_RIGHT_SHIFT
) {
    val buttonTextColor by ColorSetting("Button Text Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Floyd GUI button text color — toggle chroma/fade inside the picker.")
    val buttonBorderColor by ColorSetting("Button Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Floyd GUI button border color — toggle chroma/fade inside the picker.")
    val guiBorderColor by ColorSetting("GUI Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Floyd GUI panel border color — toggle chroma/fade inside the picker.")

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
        "buttonTextFade" to buttonTextColor.fade,
        "buttonBorderColor" to "#${buttonBorderColor.hex()}",
        "buttonBorderChroma" to buttonBorderColor.chroma,
        "buttonBorderFade" to buttonBorderColor.fade,
        "guiBorderColor" to "#${guiBorderColor.hex()}",
        "guiBorderChroma" to guiBorderColor.chroma,
        "guiBorderFade" to guiBorderColor.fade
    )
}
