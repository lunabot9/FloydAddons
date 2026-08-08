package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import kotlin.test.Test
import kotlin.test.assertIs

class FloydPanelStyleTest {
    @Test
    fun `blur toggles and kernel selectors have distinct setting names`() {
        assertIs<BooleanSetting>(FloydPanelStyle.settings["Panel Blur"])
        assertIs<SelectorSetting>(FloydPanelStyle.settings["Panel Blur Type"])

        FloydPanelStyle.PanelTarget.entries.forEach { target ->
            assertIs<BooleanSetting>(FloydPanelStyle.settings["${target.label} Blur"])
            assertIs<SelectorSetting>(FloydPanelStyle.settings["${target.label} Blur Type"])
        }
    }
}
