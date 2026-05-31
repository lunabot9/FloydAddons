package gg.floyd.features.impl.cosmetic

import gg.floyd.features.impl.player.FloydPlayerSizeControls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydSkinTest {
    @Test
    fun `player size toggle mirrors Floyd module entry`() {
        assertFalse(FloydPlayerSizeControls.isActive(1.0f, 1.0f, 1.0f))
        assertEquals(2.0f, FloydPlayerSizeControls.toggledScale(1.0f, 1.0f, 1.0f))

        assertTrue(FloydPlayerSizeControls.isActive(2.0f, 2.0f, 2.0f))
        assertEquals(1.0f, FloydPlayerSizeControls.toggledScale(2.0f, 2.0f, 2.0f))

        assertTrue(FloydPlayerSizeControls.isActive(1.0f, -0.5f, 1.0f))
        assertEquals(1.0f, FloydPlayerSizeControls.toggledScale(1.0f, -0.5f, 1.0f))
    }
}
