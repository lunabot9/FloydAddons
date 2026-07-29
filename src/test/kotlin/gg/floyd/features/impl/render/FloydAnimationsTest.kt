package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydAnimationsTest {
    @Test
    fun `animation gate is disabled by default like Floyd config`() {
        assertFalse(FloydAnimations.enabled)
        assertFalse(FloydAnimations.shouldApply())
        assertFalse(FloydAnimations.shouldUseRotateOnlySwing())
    }

    @Test
    fun `animation state exposes rotate only swing setting`() {
        val settings = FloydAnimations.state()["settings"] as Map<*, *>

        assertTrue(settings.containsKey("rotateOnlySwing"))
        assertFalse(settings["rotateOnlySwing"] as Boolean)
    }
}
