package gg.floyd.features.impl.render

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
    fun `hypixel star labels prefer the preceding base entity from live dungeon data`() {
        assertEquals(1_075_815, FloydMobEsp.starLabelBaseEntityId(1_075_816))
        assertEquals(1_075_823, FloydMobEsp.starLabelBaseEntityId(1_075_824))
    }

    @Test
    fun `star label fallback accepts the live one block offset but rejects unrelated mobs`() {
        assertTrue(FloydMobEsp.isNearArmorStandLabel(-153.0, 69.0, -155.0, -153.0, 71.0, -154.0))
        assertFalse(FloydMobEsp.isNearArmorStandLabel(-153.0, 69.0, -158.0, -153.0, 71.0, -154.0))
    }

    @Test
    fun `star nametag directly creates a mob sized box beneath the live label position`() {
        val box = FloydMobEsp.starNametagBox(-153.0, 71.0, -156.0)

        assertEquals(-153.45, box.minX)
        assertEquals(69.0, box.minY)
        assertEquals(-156.45, box.minZ)
        assertEquals(-152.55, box.maxX)
        assertEquals(71.0, box.maxY)
        assertEquals(-155.55, box.maxZ)
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
