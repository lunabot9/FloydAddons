package gg.floyd.features.impl.cosmetic

import kotlin.test.Test
import kotlin.test.assertTrue

class JennyJiggleMotionTest {
    @Test
    fun `spring follows directional movement and remains bounded`() {
        val spring = JennyJiggleSpring()
        repeat(30) { spring.step(JennyJiggleOffset(0.04f, -0.03f, 0.045f), 1f / 60f) }
        val moved = spring.current()

        assertTrue(moved.x > 0f)
        assertTrue(moved.y < 0f)
        assertTrue(moved.z > 0f)
        assertTrue(moved.x <= 0.075f && moved.y >= -0.052f && moved.z <= 0.075f)
    }

    @Test
    fun `spring keeps settling after movement stops`() {
        val spring = JennyJiggleSpring()
        repeat(12) { spring.step(JennyJiggleOffset(z = 0.045f), 1f / 60f) }
        val atRelease = spring.current().z
        val firstSettlingFrame = spring.step(JennyJiggleOffset(), 1f / 60f).z
        repeat(180) { spring.step(JennyJiggleOffset(), 1f / 60f) }

        assertTrue(firstSettlingFrame != atRelease)
        assertTrue(kotlin.math.abs(spring.current().z) < 0.001f)
    }
}
