package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydSparklingCritterEspTest {
    @Test
    fun `formatted sparkling critter labels are detected locally`() {
        assertTrue(FloydSparklingCritterEsp.isSparklingCritterLabelText("§6SPARKLING §cSolsnatcher"))
        assertTrue(FloydSparklingCritterEsp.isSparklingCritterLabelText("Sparkling Hermit Crab"))
        assertFalse(FloydSparklingCritterEsp.isSparklingCritterLabelText("Solsnatcher"))
        assertEquals("SPARKLING Solsnatcher", FloydSparklingCritterEsp.sparklingCritterName("§6SPARKLING §cSolsnatcher"))
        assertEquals(
            "SPARKLING Solsnatcher!",
            FloydSparklingCritterEsp.sparklingCritterName(
                "{\"text\":\"SPARKLING Solsnatcher\",\"color\":\"gold\",\"extra\":[{\"text\":\"!\"}]}"
            )
        )
    }

    @Test
    fun `configured render distance uses the full loaded chunk boundary`() {
        assertTrue(FloydSparklingCritterEsp.isWithinRenderDistance(15, 15, 79, -64, 4))
        assertTrue(FloydSparklingCritterEsp.isWithinRenderDistance(-1, -1, -80, 63, 4))
        assertFalse(FloydSparklingCritterEsp.isWithinRenderDistance(15, 15, 80, 0, 4))
        assertFalse(FloydSparklingCritterEsp.isWithinRenderDistance(-1, -1, -81, 0, 4))
    }

    @Test
    fun `nearby sparkling label pairs to its mob but rejects unrelated mobs`() {
        assertTrue(FloydSparklingCritterEsp.isNearCritterLabel(-153.0, 69.0, -155.0, -153.0, 71.0, -154.0))
        assertFalse(FloydSparklingCritterEsp.isNearCritterLabel(-153.0, 69.0, -158.0, -153.0, 71.0, -154.0))
    }

    @Test
    fun `fallback box and local notification include the detected critter location`() {
        val box = FloydSparklingCritterEsp.critterNametagBox(-153.0, 71.0, -156.0)

        assertEquals(-153.45, box.minX)
        assertEquals(69.0, box.minY)
        assertEquals(-156.45, box.minZ)
        assertEquals(-152.55, box.maxX)
        assertEquals(71.0, box.maxY)
        assertEquals(-155.55, box.maxZ)
        assertEquals(
            "§6Sparkling critter detected: §fSPARKLING Solsnatcher §7at §f-153, 69, -156",
            FloydSparklingCritterEsp.detectionMessage("SPARKLING Solsnatcher", -153, 69, -156)
        )
    }
}
