package gg.floyd.features.impl.render

import gg.floyd.clickgui.FloydUiPalette
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.utils.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydUiThemeTest {
    @Test
    fun `preset previews preserve custom colors and customize copies all selected roles`() {
        val theme = FloydUiTheme
        val preset = theme.settings["Preset"] as SelectorSetting
        val previousPreset = preset.value
        val previousCustom = theme.customColors
        val colors = FloydUiPalette.roles.keys.associateWith { theme.settings[it] as ColorSetting }
        val previousColors = colors.mapValues { it.value.value }
        try {
            theme.customColors = true
            colors.getValue("Accent").value = Color(0xFF19D68C.toInt())
            assertEquals(0xFF19D68C.toInt(), theme.palette()["Accent"])
            theme.customColors = false
            preset.value = 2
            assertEquals(FloydUiPalette.presets.getValue("Glacier Blue"), theme.palette())
            theme.customColors = true
            assertEquals(0xFF19D68C.toInt(), theme.palette()["Accent"])
            (theme.settings["Customize Preset"] as ActionSetting).action()
            assertTrue(theme.customColors)
            assertEquals(FloydUiPalette.presets.getValue("Glacier Blue"), theme.palette())
        } finally {
            preset.value = previousPreset; theme.customColors = previousCustom
            colors.forEach { (name, setting) -> setting.value = previousColors.getValue(name) }
        }
    }
}
