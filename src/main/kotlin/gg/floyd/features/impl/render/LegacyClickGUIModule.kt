package gg.floyd.features.impl.render

import gg.floyd.Branding
import gg.floyd.clickgui.FloydOneConfig
import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.impl.*
import gg.floyd.features.Module
import gg.floyd.utils.Color
import org.lwjgl.glfw.GLFW

/** Retains the old config identity and colors while opening the primary OneConfig UI. */
@AlwaysActive
object LegacyClickGUIModule : Module(
    name = Branding.LEGACY_GUI_NAME,
    description = Branding.LEGACY_GUI_DESCRIPTION,
    key = GLFW.GLFW_KEY_N
) {
    override val visibleInGui: Boolean = false
    val buttonTextColor by ColorSetting("Button Text Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Floyd GUI button text color — toggle chroma/fade inside the picker.")
    val buttonBorderColor by ColorSetting("Button Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Floyd GUI button border color — toggle chroma/fade inside the picker.")
    val guiBorderColor by ColorSetting("GUI Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Floyd GUI panel border color — toggle chroma/fade inside the picker.")

    override fun onKeybind() {
        FloydOneConfig.open(openingKey = (settings["Keybind"] as KeybindSetting).value.value)
    }

    override fun onEnable() {
        FloydOneConfig.open()
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
