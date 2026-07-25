package gg.floyd.features.impl.render

import gg.floyd.clickgui.Panel
import gg.floyd.utils.Color
import gg.floyd.utils.Color.Companion.brighter
import kotlin.test.Test
import kotlin.test.assertEquals

class ClickGUIModuleTest {
    @Test
    fun `hover brightens the displayed accent instead of the configured base hue`() {
        val displayedPurple = 0xFF9C27B0.toInt()

        assertEquals(Color(displayedPurple).brighter().rgba, ClickGUIModule.hoveredAccentColor(displayedPurple))
    }

    @Test
    fun `bootstrap gui scale stays stable before the Minecraft window exists`() {
        assertEquals(1f, ClickGUIModule.standardGuiScaleFor(1920f, 1080f, 1f))
        assertEquals(1920f, ClickGUIModule.availableWidthForLayout(1920f, 1080f, 1f))
    }

    @Test
    fun `layout width respects the capped gui scale on larger windows`() {
        assertEquals(1.5f, ClickGUIModule.standardGuiScaleFor(3840f, 2160f, 1f))
        assertEquals(2560f, ClickGUIModule.availableWidthForLayout(3840f, 2160f, 1f))
    }

    @Test
    fun `short windows scale the click gui down to keep its content visible`() {
        assertEquals(0.6f, ClickGUIModule.standardGuiScaleFor(1920f, 600f, 1f))
        assertEquals(3200f, ClickGUIModule.availableWidthForLayout(1920f, 600f, 1f), 0.001f)

        assertEquals(0.5f, ClickGUIModule.standardGuiScaleFor(854f, 480f, 1f))
        assertEquals(1708f, ClickGUIModule.availableWidthForLayout(854f, 480f, 1f))
    }

    @Test
    fun `retina logical points are not double-penalized`() {
        assertEquals(0.8f, ClickGUIModule.standardGuiScaleFor(1512f, 982f, 2f))
        assertEquals(1890f, ClickGUIModule.availableWidthForLayout(1512f, 982f, 2f))
    }

    @Test
    fun `screenshot layout is centered inside narrow virtual widths`() {
        val centered = ClickGUIModule.centeredPanelLayout(
            linkedMapOf(
                "render" to ClickGUIModule.PanelData(x = 6f, y = 31f, extended = true),
                "pvp" to ClickGUIModule.PanelData(x = 1356f, y = 31f, extended = true),
            ),
            availableWidth = 1708f,
        )

        val minX = centered.minOf { it.value.x }
        val maxRight = centered.maxOf { it.value.x + Panel.WIDTH }
        assertEquals(59f, minX)
        assertEquals(1649f, maxRight)
    }
}
