package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.BooleanSetting
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydAnimationsTest {
    @Test
    fun `animation gate is disabled by default like Floyd config`() {
        assertFalse(FloydAnimations.enabled)
        assertFalse(FloydAnimations.shouldApply())
        assertFalse(FloydAnimations.shouldUseNoSwing())
        assertFalse(FloydAnimations.shouldSuppressSwingMotion())
        assertFalse(FloydAnimations.shouldUseRotateOnlySwing())
    }

    @Test
    fun `animation state exposes no swing and rotate only settings`() {
        val settings = FloydAnimations.state()["settings"] as Map<*, *>

        assertTrue(settings.containsKey("noSwing"))
        assertFalse(settings["noSwing"] as Boolean)
        assertTrue(settings.containsKey("rotateOnlySwing"))
        assertFalse(settings["rotateOnlySwing"] as Boolean)
    }

    @Test
    fun `no swing takes priority over rotate only swing`() {
        val noSwing = FloydAnimations.settings.getValue("No Swing") as BooleanSetting
        val rotateOnly = FloydAnimations.settings.getValue("Rotate-Only Swing") as BooleanSetting
        val wasEnabled = FloydAnimations.enabled
        val previousNoSwing = noSwing.enabled
        val previousRotateOnly = rotateOnly.enabled

        try {
            if (!FloydAnimations.enabled) FloydAnimations.toggle()
            noSwing.enabled = true
            rotateOnly.enabled = true

            assertTrue(FloydAnimations.shouldUseNoSwing())
            assertTrue(FloydAnimations.shouldSuppressSwingMotion())
            assertFalse(FloydAnimations.shouldUseRotateOnlySwing())
        } finally {
            noSwing.enabled = previousNoSwing
            rotateOnly.enabled = previousRotateOnly
            if (FloydAnimations.enabled != wasEnabled) FloydAnimations.toggle()
        }
    }
}
