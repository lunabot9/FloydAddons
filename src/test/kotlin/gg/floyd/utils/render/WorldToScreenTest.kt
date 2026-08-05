package gg.floyd.utils.render

import org.joml.Matrix4f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldToScreenTest {

    @AfterEach
    fun tearDown() {
        setProjection(null)
    }

    @Test
    fun `behind-camera edge keeps horizontal direction`() {
        setProjection(Matrix4f())

        val right = WorldToScreen.behindCameraScreenEdge(4f, 0f, 1f)
        val left = WorldToScreen.behindCameraScreenEdge(-4f, 0f, 1f)

        assertTrue(right.x > 0f, "right-side behind-camera targets should stay on the right edge")
        assertEquals(0.98f, right.x, 1.0e-4f)
        assertTrue(left.x < 0f, "left-side behind-camera targets should stay on the left edge")
        assertEquals(-0.98f, left.x, 1.0e-4f)
    }

    @Test
    fun `directly behind target falls back to center bottom`() {
        setProjection(Matrix4f())

        val edge = WorldToScreen.behindCameraScreenEdge(0f, 0f, 1f)

        assertEquals(0f, edge.x, 1.0e-4f)
        assertEquals(-0.98f, edge.y, 1.0e-4f)
    }

    private fun setProjection(value: Matrix4f?) {
        val field = WorldToScreen::class.java.getDeclaredField("projection")
        field.isAccessible = true
        field.set(WorldToScreen, value)
    }
}
