package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals

class ClickGUIModuleTest {
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
}
