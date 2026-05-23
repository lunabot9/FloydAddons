package com.odtheking.odin.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydMobEspDebugTest {
    @Test
    fun `debug labels expire after requested duration`() {
        FloydMobEsp.enableDebugLabels(nowMillis = 1_000L, durationMs = 10_000L)

        assertTrue(FloydMobEsp.debugLabelsActive(nowMillis = 10_999L))
        assertFalse(FloydMobEsp.debugLabelsActive(nowMillis = 11_001L))
        assertFalse(FloydMobEsp.debugLabelsActive(nowMillis = 11_002L))
    }

    @Test
    fun `star mob labels include Floyd miniboss names without explicit filters`() {
        assertTrue(FloydMobEsp.isStarMobLabelText("§6Shadow Assassin"))
        assertTrue(FloydMobEsp.isStarMobLabelText("✯ Skeletor"))
        assertFalse(FloydMobEsp.isStarMobLabelText("✪ Skeletor"))
        assertFalse(FloydMobEsp.isStarMobLabelText("Zombie"))
    }

    @Test
    fun `debug and reload summaries preserve Floyd command wording`() {
        try {
            FloydMobEsp.clearFilters()

            val summary = FloydMobEsp.debugSummary()

            assertTrue(summary.contains("--- Mob ESP Debug --- (in-world labels for 10s)"))
            assertTrue(summary.contains("Enabled: false HasFilters: false"))
            assertTrue(summary.contains("Names: [] Types: []"))
            assertEquals("Mob ESP config reloaded", FloydMobEsp.reloadSummary())
        } finally {
            FloydMobEsp.clearFilters()
        }
    }
}
