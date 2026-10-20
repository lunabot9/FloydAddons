package gg.floyd.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class PetConversationTest {
    @Test fun rotatesAllThreeAndStopsAtTheLimit() {
        val c = PetConversation(); c.start(3, "a castle")
        val first = c.next(0, true)!!; assertEquals(0, first.speaker)
        assertNull(c.next(0, true))
        c.complete("Verity", "Build a tower.", 100)
        assertNull(c.next(1599, true))
        val second = c.next(1600, true)!!; assertEquals(1, second.speaker)
        kotlin.test.assertTrue(second.context.contains("Verity: Build a tower."))
        c.complete("Randy", "Nice tower.", 2000)
        assertEquals(2, c.next(3500, true)!!.speaker)
        c.complete("Tom Pearl", "Brown bricks.", 4000)
        assertFalse(c.active); assertEquals(3, c.completed); assertNull(c.next(9000, true))
    }
    @Test fun busyPausesAndStopDiscardsPendingContinuation() {
        val c = PetConversation(); c.start(6, "a house")
        assertNull(c.next(0, false)); c.next(0, true)
        c.stop(); c.complete("Verity", "Stale text", 10)
        assertEquals(0, c.completed); assertNull(c.next(2000, true))
        c.start(3, "a new topic")
        assertEquals("Topic: a new topic\n", c.next(2000, true)!!.context)
    }
}
