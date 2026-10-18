package gg.floyd.utils.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class FramebufferBlurCoordinatesTest {
    @Test
    fun `pip crop origin uses the same gui to physical conversion as rendering`() {
        assertEquals(-40f, pipContentOffset(screenOrigin = 20f, guiScale = 2f, devicePixelRatio = 1f))
    }

    @Test
    fun `nvg crop coordinates round trip through minecraft gui space`() {
        val guiOrigin = nvgToGuiCoordinate(nvgCoordinate = 31f, renderScale = 0.6f, guiScale = 2f)
        assertEquals(9.3f, guiOrigin)
        assertEquals(-18.6f, pipContentOffset(guiOrigin, guiScale = 2f, devicePixelRatio = 1f))
    }

    @Test
    fun `panel blur keeps a fixed sample budget and normalizes color once`() {
        val shader = checkNotNull(javaClass.getResourceAsStream("/assets/floydaddons/shaders/core/panel_blur.fsh"))
            .bufferedReader()
            .use { it.readText() }
        assertContains(shader, "for (int x = -4; x <= 4; ++x)")
        assertContains(shader, "for (int y = -4; y <= 4; ++y)")
        assertContains(shader, "acc / wsum")
    }
}
