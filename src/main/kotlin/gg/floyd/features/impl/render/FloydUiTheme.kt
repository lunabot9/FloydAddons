package gg.floyd.features.impl.render

import gg.floyd.clickgui.FloydUiPalette
import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.Setting.Companion.withDependency
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Color

/** These colors belong only to Floyd's settings screen, never OneConfig's global theme. */
@AlwaysActive
object FloydUiTheme : Module(
    name = "UI Theme",
    category = Category.RENDER,
    description = "Choose a Floyd UI palette or customize each color. Only affects the Floyd Addons menu.",
    toggled = true,
) {
    // Keep the existing persisted module id/settings, but theme editing is its own menu page.
    override val visibleInGui: Boolean = false
    private val preset by SelectorSetting("Preset", FloydUiPalette.DEFAULT_PRESET, FloydUiPalette.presets.keys.toList(),
        desc = "Six complete palettes. Turn Custom Colors off to preview the selected preset.")
    var customColors by BooleanSetting("Custom Colors", false,
        desc = "Use your own colors below. Off uses the selected preset and keeps your custom colors saved.")
    var snowfall by BooleanSetting("Snowfall", false,
        desc = "Gentle snow inside Floyd's settings GUI. Off by default; only animates while the GUI is visible.")
    var mainMenuSnowfall by BooleanSetting("Main Menu Snowfall", true,
        desc = "Depth-layered snow in the animated title-screen background, behind the menu buttons.")
    private val customizePreset by ActionSetting("Customize Preset",
        desc = "Copy the selected preset into the color pickers and turn Custom Colors on. Replaces your custom colors.") {
        customizeSelectedPreset()
    }
    private val colors = FloydUiPalette.roles.mapValues { (name, description) ->
        registerSetting(ColorSetting(name, Color(FloydUiPalette.default[name]), allowAlpha = true,
            desc = "$description Changes apply live to Floyd's menu.")).withDependency { customColors }
    }

    fun presetName(): String = FloydUiPalette.presets.keys.elementAtOrElse(preset) { FloydUiPalette.DEFAULT_PRESET }
    fun selectPreset(index: Int) {
        (settings["Preset"] as SelectorSetting).value = index
        customColors = false
    }
    private fun selectedPalette(): FloydUiPalette = FloydUiPalette.presets.getValue(presetName())
    private fun customizeSelectedPreset() {
        val palette = selectedPalette()
        colors.forEach { (name, setting) -> setting.value = Color(palette[name]) }
        customColors = true
    }
    fun palette(): FloydUiPalette = if (!customColors) selectedPalette()
        else FloydUiPalette(colors.mapValues { (_, setting) -> setting.value.rgba })

    fun state(): Map<String, Any?> = mapOf(
        "preset" to presetName(),
        "presets" to FloydUiPalette.presets.keys.toList(),
        "customColors" to customColors,
        "isolated" to true,
        "colors" to palette().colors.mapValues { (_, value) -> "#%08X".format(value) },
    )
}
