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
    fun `panel blur uses the v2 1 kernel and normalizes color once`() {
        val shader = checkNotNull(javaClass.getResourceAsStream("/assets/floydaddons/shaders/core/panel_blur.fsh"))
            .bufferedReader()
            .use { it.readText() }
        assertContains(shader, "for (float dx = -radius; dx <= radius; dx += 2.0)")
        assertContains(shader, "for (float dy = -radius; dy <= radius; dy += 2.0)")
        assertContains(shader, "acc / wsum")
    }
}
