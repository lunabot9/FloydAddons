package gg.floyd.clickgui.settings.impl

import kotlin.test.Test
import kotlin.test.assertEquals

class HudViewportPositionTest {
    @Test
    fun `temporary smaller viewport clamps only the draw coordinate`() {
        val storedFullscreenX = 1_500f

        val windowedDrawX = visibleHudCoordinate(storedFullscreenX, viewportSize = 854f, renderedSize = 360f)
        val fullscreenDrawX = visibleHudCoordinate(storedFullscreenX, viewportSize = 1_920f, renderedSize = 360f)

        assertEquals(494f, windowedDrawX)
        assertEquals(storedFullscreenX, fullscreenDrawX)
    }

    @Test
    fun `oversized hud draws from the viewport origin without changing its stored position`() {
        assertEquals(0f, visibleHudCoordinate(stored = 700f, viewportSize = 320f, renderedSize = 500f))
    }
}
