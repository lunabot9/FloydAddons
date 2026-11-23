package gg.floyd.features.impl.render

import kotlin.test.*

class MiningAbilityReadyTrackerTest {
    @Test fun `every supported ability recognizes the server chat message`() {
        for (ability in MiningAbilityReadyTracker.abilities) {
            val signal = MiningAbilityReadyTracker().chat("$ability is now available!", 100)
            assertEquals(ability, signal?.ability)
            assertEquals("chat", signal?.source)
        }
        assertEquals("Pickobulus", MiningAbilityReadyTracker().chat("Pikobulous is now available!", 0)?.ability)
        assertEquals("Mining Speed Boost", MiningAbilityReadyTracker().chat("\u00A76Mining Speed Boost \u00A7ais now available!", 0)?.ability)
    }

    @Test fun `unrelated player messages and ability activations do not trigger`() {
        val tracker = MiningAbilityReadyTracker()
        for (message in listOf("Player: Pickobulus is now available!", "Pickobulus is on cooldown!", "Mining Speed Boost is active!", "Other Ability is now available!")) {
            assertNull(tracker.chat(message, 0))
        }
    }

    @Test fun `tab alerts on readiness transitions without predicting cooldowns or repeating a ready row`() {
        val tracker = MiningAbilityReadyTracker()
        assertTrue(tracker.tab(listOf("Pickaxe Ability:", "Pickobulus: 31s"), 0).isEmpty())
        assertTrue(tracker.tab(listOf("Pickobulus: 1m 2s"), 1_000).isEmpty())
        assertEquals("Pickobulus", tracker.tab(listOf("Pickobulus: Ready!"), 2_000).single().ability)
        assertTrue(tracker.tab(listOf("Pickobulus: Ready!"), 20_000).isEmpty())
        assertTrue(tracker.tab(listOf("Pickobulus: 31s"), 21_000).isEmpty())
        assertEquals("Pickobulus", tracker.tab(listOf("Pickobulus: 0s"), 52_000).single().ability)
    }

    @Test fun `chat and tab reports of the same readiness event are deduplicated in both orders`() {
        val tracker = MiningAbilityReadyTracker()
        assertNotNull(tracker.chat("Sheer Force is now available!", 0))
        assertTrue(tracker.tab(listOf("Sheer Force: Ready"), 100).isEmpty())
        tracker.reset()
        assertEquals(1, tracker.tab(listOf("Sheer Force: Available!"), 0).size)
        assertNull(tracker.chat("Sheer Force is now available!", 100))
        assertNotNull(tracker.chat("Sheer Force is now available!", 30_000))
    }

    @Test fun `all tab abilities multiline data and world resets are supported`() {
        val tracker = MiningAbilityReadyTracker()
        for ((index, ability) in MiningAbilityReadyTracker.abilities.withIndex()) {
            assertEquals(ability, tracker.tab(listOf("Pickaxe Ability:\n$ability: Ready!"), index * 3_000L).single().ability)
        }
        tracker.reset()
        assertEquals(1, tracker.tab(listOf("Gemstone Infusion: Ready!"), 0).size)
    }

    @Test fun `alert fades in holds and fades out`() {
        assertEquals(0, FloydMiningAbilityNotification.opacity(0))
        assertEquals(128, FloydMiningAbilityNotification.opacity(125))
        assertEquals(255, FloydMiningAbilityNotification.opacity(250))
        assertEquals(255, FloydMiningAbilityNotification.opacity(2_250))
        assertEquals(128, FloydMiningAbilityNotification.opacity(2_625))
        assertEquals(0, FloydMiningAbilityNotification.opacity(3_000))
    }
}
