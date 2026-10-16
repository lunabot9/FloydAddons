package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloydFellNotificationTest {
    @Test
    fun `exact Timber Helix tree-fell chat message selects its label in dark red`() {
        assertEquals(
            "TIMBER!" to 0xAA0000,
            FloydFellNotification.notificationForChat("TIMBER! You felled the entire Helix Tree!"),
        )
        assertEquals(
            "TIMBER!" to 0xAA0000,
            FloydFellNotification.notificationForChat("  TIMBER! You felled the entire Helix Tree!  "),
        )
    }

    @Test
    fun `other tree-fell and near-miss chat messages do not trigger the notification`() {
        assertNull(FloydFellNotification.notificationForChat("WOODPECKER! You felled the entire Tree!"))
        assertNull(FloydFellNotification.notificationForChat("PETALFALL! You felled the entire Tree!"))
        assertNull(FloydFellNotification.notificationForChat("TIMBER! You felled the entire Tree!"))
        assertNull(FloydFellNotification.notificationForChat("TIMBER! You felled the entire Helix tree!"))
        assertNull(FloydFellNotification.notificationForChat("TIMBER! You felled the entire tree!"))
        assertNull(FloydFellNotification.notificationForChat("TIMBER! You felled a Tree!"))
        assertNull(FloydFellNotification.notificationForChat("WOODPECKER!"))
    }

    @Test
    fun `notification remains solid before fading out`() {
        assertEquals(255, FloydFellNotification.opacityForElapsedMs(0))
        assertEquals(255, FloydFellNotification.opacityForElapsedMs(1_999))
        assertEquals(128, FloydFellNotification.opacityForElapsedMs(2_250))
        assertEquals(0, FloydFellNotification.opacityForElapsedMs(2_500))
        assertEquals(0, FloydFellNotification.opacityForElapsedMs(10_000))
    }

    @Test
    fun `default HUD position is centered with a gap above the crosshair`() {
        assertEquals(
            855 to 465,
            FloydFellNotification.defaultHudPosition(
                viewportWidth = 1_920,
                viewportHeight = 1_080,
                contentWidth = 70,
                contentHeight = 9,
                scale = 3f,
            ),
        )
    }
}
