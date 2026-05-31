package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertFalse

class FloydAnimationsTest {
    @Test
    fun `animation gate is disabled by default like Floyd config`() {
        assertFalse(FloydAnimations.enabled)
        assertFalse(FloydAnimations.shouldApply())
    }
}
