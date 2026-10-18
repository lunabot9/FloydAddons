package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydPanelStyleTest {
    @Test
    fun `fresh panel styles default to strong box blur globally and individually`() {
        val style = FloydPanelStyle
        assertTrue(style.panelBlur)
        assertEquals(20, style.panelBlurStrength)
        for (target in FloydPanelStyle.PanelTarget.entries) {
            assertTrue(style.blurFor(target))
            assertEquals(20, style.blurStrengthFor(target))
            assertTrue(style.blurIsBoxFor(target))
            assertTrue((style.settings["${target.label} Blur"] as BooleanSetting).default)
        }
        val perPanel = style.settings["Per-Panel Style"] as BooleanSetting
        val previous = perPanel.value
        try {
            perPanel.value = true
            for (target in FloydPanelStyle.PanelTarget.entries) {
                assertTrue(style.blurFor(target))
                assertEquals(20, style.blurStrengthFor(target))
                assertTrue(style.blurIsBoxFor(target))
            }
        } finally { perPanel.value = previous }
    }

    @Test
    fun `border switches preserve blur and configured widths and isolate individual panels`() {
        val style = FloydPanelStyle
        val global = style.settings["Panel Border Enabled"] as BooleanSetting
        val perPanel = style.settings["Per-Panel Style"] as BooleanSetting
        val scoreboard = style.settings["Scoreboard Border Enabled"] as BooleanSetting
        val previous = listOf(global.value, perPanel.value, scoreboard.value)
        try {
            perPanel.value = false
            val width = style.panelBorderWidth
            global.value = false
            for (target in FloydPanelStyle.PanelTarget.entries) {
                assertEquals(0, style.borderWidthFor(target))
                assertTrue(style.blurFor(target))
            }
            global.value = true
            assertEquals(width, style.borderWidthFor(FloydPanelStyle.PanelTarget.SCOREBOARD))
            perPanel.value = true
            scoreboard.value = false
            assertEquals(0, style.borderWidthFor(FloydPanelStyle.PanelTarget.SCOREBOARD))
            assertTrue(style.borderWidthFor(FloydPanelStyle.PanelTarget.INVENTORY) > 0)
            assertTrue(style.blurFor(FloydPanelStyle.PanelTarget.SCOREBOARD))
        } finally {
            global.value = previous[0]; perPanel.value = previous[1]; scoreboard.value = previous[2]
        }
    }

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
