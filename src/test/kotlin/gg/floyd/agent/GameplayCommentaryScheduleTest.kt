package gg.floyd.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class GameplayCommentaryScheduleTest {
    @Test fun waitsForIntervalAndNewActivityAndConsumesThatActivity() {
        val s = GameplayCommentarySchedule { it }
        assertFalse(s.poll(1000, 60000, true, "a"))
        assertFalse(s.poll(61000, 60000, true, "a"))
        assertTrue(s.poll(61001, 60000, true, "b"))
        assertFalse(s.poll(121002, 60000, true, "b"))
        assertTrue(s.poll(121003, 60000, true, "c"))
    }
    @Test fun busyDisconnectedOrMenuStateDefersWithoutAQueuedBurst() {
        val s = GameplayCommentarySchedule { it }
        s.poll(1000, 60000, true, "a")
        assertFalse(s.poll(70000, 60000, false, "b"))
        assertFalse(s.poll(70001, 60000, true, "c"))
        assertTrue(s.poll(85000, 60000, true, "d"))
    }
    @Test fun userStopWorldJoinOrModelChangeResetsTheFullCooldown() {
        val s = GameplayCommentarySchedule { it }
        s.poll(1000, 60000, true, "a")
        s.poll(5000, 60000, true, "b")
        s.defer(60000, 60000)
        assertFalse(s.poll(61000, 60000, true, "b"))
        assertFalse(s.poll(120000, 60000, true, "b"))
        assertTrue(s.poll(120001, 60000, true, "c"))
    }
    @Test fun changingFrequencyReschedulesInsteadOfImmediatelySpeaking() {
        val s = GameplayCommentarySchedule { it }
        s.poll(1000, 180000, true, "a")
        assertFalse(s.poll(180000, 60000, true, "b"))
        assertEquals(240000, s.nextAt)
    }
    @Test fun naturalJitterRemainsWithinItsBounds() {
        val s = GameplayCommentarySchedule()
        repeat(30) {
            s.defer(1000, 180000)
            assertTrue(s.nextAt in 154000L..226000L)
        }
    }
}
