package gg.floyd.supercraft

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SackCraftPlannerTest {
    private val recipes=listOf(SackRecipe("ENCHANTED_NETHER_STALK",1,mapOf("NETHER_STALK" to 160)),SackRecipe("MUTANT_NETHER_STALK",1,mapOf("ENCHANTED_NETHER_STALK" to 160)))
    @Test fun `wart is compressed through both tiers and leftovers are kept`() {
        val p=SackCraftPlanner.plan(mapOf("NETHER_STALK" to 25601,"ENCHANTED_NETHER_STALK" to 160),recipes,emptySet())
        assertEquals(2L,p.remaining["MUTANT_NETHER_STALK"]);assertEquals(1L,p.remaining["NETHER_STALK"])
        assertEquals(2,p.steps.size)
    }
    @Test fun `exception stops exactly at protected tier`() {
        val p=SackCraftPlanner.plan(mapOf("NETHER_STALK" to 25600),recipes,setOf("ENCHANTED_NETHER_STALK"))
        assertEquals(mapOf("ENCHANTED_NETHER_STALK" to 160L),p.remaining)
        assertEquals(1,p.steps.size)
    }
    @Test fun `partial and unknown resources cannot be spent`() {
        val p=SackCraftPlanner.plan(mapOf("ENCHANTED_NETHER_STALK" to 159,"UNKNOWN" to 10000),recipes,emptySet())
        assertTrue(p.steps.isEmpty());assertEquals(10000L,p.remaining["UNKNOWN"])
    }
    @Test fun `count reader accepts exact stored amounts only`() {
        assertEquals(123456L,SackCraftPlanner.stored("§7Stored: §e123,456§7/1,000,000"))
        assertNull(SackCraftPlanner.stored("Capacity: 1,000,000"));assertNull(SackCraftPlanner.stored("Stored: 1.2M"))
    }
    @Test fun `multi ingredient recipe is bounded by every material`() {
        val recipe=SackRecipe("HIGHER",1,mapOf("A" to 10,"B" to 5))
        val p=SackCraftPlanner.plan(mapOf("A" to 29,"B" to 7),listOf(recipe),emptySet())
        assertEquals(1L,p.steps.single().crafts);assertEquals(19L,p.remaining["A"]);assertEquals(2L,p.remaining["B"])
    }
}
