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
    fun `small windows keep their real layout width`() {
        assertEquals(1f, ClickGUIModule.standardGuiScaleFor(854f, 480f, 1f))
        assertEquals(854f, ClickGUIModule.availableWidthForLayout(854f, 480f, 1f))
    }
}
