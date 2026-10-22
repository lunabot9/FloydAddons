package gg.floyd.clickgui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydSnowFieldTest {
    @Test
    fun `flakes stay bounded through long running and resize independent coordinates`() {
        val field = FloydSnowField()
        for (time in listOf(0.0, 10.0, 1000.0, 1_000_000.0)) {
            for (index in 0 until field.count) {
                val point = field.at(index, time)
                assertTrue(point.x in 0f..1f && point.y in 0f..1f)
                assertTrue(point.radiusDp > 0f && point.opacity in 0f..1f)
                assertEquals(point, FloydSnowField().at(index, time))
            }
        }
        val first = field.at(0, 0.0)
        val next = field.at(0, .25)
        assertTrue(((next.y - first.y + 1f) % 1f) in .005f.. .02f)
    }

    @Test
    fun `hidden time does not advance snowfall and stalled frames do not jump`() {
        val clock = FloydSnowClock()
        assertEquals(0.0, clock.frame(1_000_000_000))
        assertEquals(.016, clock.frame(1_016_000_000), .000001)
        clock.pause()
        assertEquals(.016, clock.frame(61_016_000_000), .000001)
        assertEquals(.032, clock.frame(61_032_000_000), .000001)
        assertEquals(.082, clock.frame(70_000_000_000), .000001)
    }
}
