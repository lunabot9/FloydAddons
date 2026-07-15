package gg.floyd.features.impl.pvp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloydAutoClickerTest {
    @Test
    fun `click delay randomizes cps and retains timing jitter`() {
        assertEquals(170L, FloydAutoClicker.clickDelayMs(5.0, 10.0, 0.0, 0.0))
        assertEquals(133L, FloydAutoClicker.clickDelayMs(5.0, 10.0, 0.5, 0.5))
        assertEquals(130L, FloydAutoClicker.clickDelayMs(5.0, 10.0, 1.0, 1.0))
    }

    @Test
    fun `random cps safely normalizes reversed slider values`() {
        assertEquals(5.0, FloydAutoClicker.randomizedCps(10.0, 5.0, 0.0))
        assertEquals(7.5, FloydAutoClicker.randomizedCps(10.0, 5.0, 0.5))
        assertEquals(10.0, FloydAutoClicker.randomizedCps(10.0, 5.0, 1.0))
    }

    @Test
    fun `left autoclick uses scheduled block clicks when continuous breaking is disabled`() {
        assertEquals(
            FloydAutoClicker.LeftClickAction.CLICK_BLOCK,
            FloydAutoClicker.leftClickAction(allowBreaking = false, targetingSolidBlock = true),
        )
        assertEquals(
            FloydAutoClicker.LeftClickAction.BREAK_BLOCK,
            FloydAutoClicker.leftClickAction(allowBreaking = true, targetingSolidBlock = true),
        )
        assertEquals(
            FloydAutoClicker.LeftClickAction.ATTACK,
            FloydAutoClicker.leftClickAction(allowBreaking = false, targetingSolidBlock = false),
        )
    }

    @Test
    fun `block breaking is delegated to the vanilla keybind phase`() {
        assertTrue(FloydAutoClicker.shouldFeedVanillaAttack(FloydAutoClicker.LeftClickAction.BREAK_BLOCK))
        assertFalse(FloydAutoClicker.shouldFeedVanillaAttack(FloydAutoClicker.LeftClickAction.ATTACK))
        assertFalse(FloydAutoClicker.shouldFeedVanillaAttack(FloydAutoClicker.LeftClickAction.CLICK_BLOCK))
        assertFalse(FloydAutoClicker.shouldFeedVanillaAttack(FloydAutoClicker.LeftClickAction.SKIP))
    }

    @Test
    fun `scheduled block clicks suppress vanilla continuous attack hold`() {
        assertTrue(FloydAutoClicker.shouldSuppressVanillaAttackHold(FloydAutoClicker.LeftClickAction.CLICK_BLOCK))
        assertFalse(FloydAutoClicker.shouldSuppressVanillaAttackHold(FloydAutoClicker.LeftClickAction.BREAK_BLOCK))
        assertFalse(FloydAutoClicker.shouldSuppressVanillaAttackHold(FloydAutoClicker.LeftClickAction.ATTACK))
    }

    @Test
    fun `held vanilla use key does not receive duplicate scheduled right clicks`() {
        assertFalse(FloydAutoClicker.shouldQueueRightClick(vanillaUseKeyDown = true))
        assertTrue(FloydAutoClicker.shouldQueueRightClick(vanillaUseKeyDown = false))
    }

    @Test
    fun `held identity prefers uuid then skyblock id then name`() {
        assertEquals("uuid", FloydAutoClicker.heldIdentity("uuid", "TERMINATOR", "Terminator"))
        assertEquals("TERMINATOR", FloydAutoClicker.heldIdentity(null, "TERMINATOR", "Terminator"))
        assertEquals("Terminator", FloydAutoClicker.heldIdentity(null, null, "Terminator"))
        assertNull(FloydAutoClicker.heldIdentity("", "", ""))
    }

    @Test
    fun `whitelist encoding preserves punctuation in item names`() {
        val values = linkedSetOf("Item, With Comma", "TERMINATOR")
        assertEquals(values, FloydAutoClicker.decodeWhitelist(FloydAutoClicker.encodeWhitelist(values)))
    }
}
