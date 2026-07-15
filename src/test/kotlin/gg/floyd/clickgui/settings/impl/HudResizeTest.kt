package gg.floyd.clickgui.settings.impl

import kotlin.test.Test
import kotlin.test.assertEquals

class HudResizeTest {
    @Test
    fun `bottom right resize keeps top left anchored`() {
        val result = resizeHudFromCorner(
            startX = 100f,
            startY = 80f,
            width = 200f,
            height = 100f,
            startScale = 1f,
            corner = HudResizeCorner.BOTTOM_RIGHT,
            mouseDeltaX = 100f,
            mouseDeltaY = 50f,
        )

        assertEquals(1.5f, result.scale, 0.001f)
        assertEquals(100f, result.x, 0.001f)
        assertEquals(80f, result.y, 0.001f)
    }

    @Test
    fun `top left resize keeps bottom right anchored`() {
        val result = resizeHudFromCorner(
            startX = 100f,
            startY = 80f,
            width = 200f,
            height = 100f,
            startScale = 1f,
            corner = HudResizeCorner.TOP_LEFT,
            mouseDeltaX = -100f,
            mouseDeltaY = -50f,
        )

        assertEquals(1.5f, result.scale, 0.001f)
        assertEquals(0f, result.x, 0.001f)
        assertEquals(30f, result.y, 0.001f)
        assertEquals(300f, result.x + 200f * result.scale, 0.001f)
        assertEquals(180f, result.y + 100f * result.scale, 0.001f)
    }

    @Test
    fun `resize clamps to supported scale range`() {
        val result = resizeHudFromCorner(
            startX = 0f,
            startY = 0f,
            width = 100f,
            height = 100f,
            startScale = 1f,
            corner = HudResizeCorner.BOTTOM_RIGHT,
            mouseDeltaX = -1_000f,
            mouseDeltaY = -1_000f,
        )

        assertEquals(0.5f, result.scale, 0.001f)
    }

    @Test
    fun `every resize corner preserves its opposite anchor`() {
        val startX = 120f
        val startY = 90f
        val width = 160f
        val height = 80f
        val startScale = 1f
        val startRight = startX + width
        val startBottom = startY + height

        for (corner in HudResizeCorner.entries) {
            val dx = if (corner.isRight) 40f else -40f
            val dy = if (corner.isBottom) 20f else -20f
            val result = resizeHudFromCorner(
                startX, startY, width, height, startScale, corner, dx, dy,
            )

            if (corner.isRight) assertEquals(startX, result.x, 0.001f)
            else assertEquals(startRight, result.x + width * result.scale, 0.001f)
            if (corner.isBottom) assertEquals(startY, result.y, 0.001f)
            else assertEquals(startBottom, result.y + height * result.scale, 0.001f)
        }
    }
}
