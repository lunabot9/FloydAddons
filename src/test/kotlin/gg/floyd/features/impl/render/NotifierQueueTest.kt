package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotifierQueueTest {
    @Test
    fun `bounded burst keeps newest cards and replaces repeated toggles`() {
        var now = 0L
        val queue = NotifierQueue { now }
        queue.add("A", true, 3_000L, 2)
        queue.add("B", true, 3_000L, 2)
        queue.add("C", true, 3_000L, 2)
        assertEquals(listOf("C", "B"), queue.visible(2).map { it.entry.name })
        now = 100
        queue.add("B", false, 3_000L, 2)
        assertEquals(listOf("B", "C"), queue.visible(2).map { it.entry.name })
        assertFalse(queue.visible(2).first().entry.enabled)
        assertEquals(4L, queue.triggerCount)
    }

    @Test
    fun `slide and opacity ease in and out and expired entries disappear`() {
        var now = 0L
        val queue = NotifierQueue { now }
        queue.add("Freecam", true, 3_000L, 3)
        assertEquals(0f, queue.visible(3).single().opacity)
        now = 110
        assertEquals(.5f, queue.visible(3).single().opacity, .001f)
        now = 220
        assertEquals(1f, queue.visible(3).single().opacity)
        now = 1_500
        assertEquals(.5f, queue.visible(3).single().progress, .001f)
        now = 2_890
        assertEquals(.5f, queue.visible(3).single().opacity, .001f)
        now = 3_000
        assertTrue(queue.visible(3).isEmpty())
    }

    @Test
    fun `capacity changes clear stale entries and clearing drops the queue`() {
        var now = 0L
        val queue = NotifierQueue { now }
        repeat(5) { queue.add("Module $it", true, 1_000L, 5) }
        assertEquals(1, queue.visible(1).size)
        queue.clear()
        assertTrue(queue.visible(5).isEmpty())
        queue.add("New", true, 1_000L, 3)
        now = 1_100
        queue.add("Other", false, 1_000L, 3)
        assertEquals(listOf("Other"), queue.visible(3).map { it.entry.name })
    }
}
