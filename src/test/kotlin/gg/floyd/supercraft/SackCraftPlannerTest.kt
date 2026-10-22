package gg.floyd.supercraft

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SackCraftPlannerTest {
    @Test fun `missing or abbreviated gemstone tier counts never fall back to weighted total`() {
        assertTrue(SackCraftPlanner.sackStock("ROUGH_RUBY_GEM","Stored: 62,690/18.3M",true).isEmpty())
        assertTrue(SackCraftPlanner.sackStock("ROUGH_RUBY_GEM","Rough: 1.2k\nFlawed: 80\nFine: 7\nStored: 62,690/18.3M",true).isEmpty())
        assertEquals(mapOf("WHEAT" to 160L),SackCraftPlanner.sackStock("WHEAT","Stored: 160/20k",false))
    }
    @Test fun `gemstone sack counts each grade instead of spending weighted stored total as rough`() {
        val tooltip="Topaz Gemstones\nRough: 50 (50)\nFlawed: 223 (17,840)\nFine: 7 (44,800)\nStored: 62,690/18.3M"
        val stock=SackCraftPlanner.sackStock("ROUGH_TOPAZ_GEM",tooltip,true)
        assertEquals(mapOf("ROUGH_TOPAZ_GEM" to 50L,"FLAWED_TOPAZ_GEM" to 223L,"FINE_TOPAZ_GEM" to 7L),stock)
        val recipes=listOf(SackRecipe("FLAWED_TOPAZ_GEM",1,mapOf("ROUGH_TOPAZ_GEM" to 80)),SackRecipe("FINE_TOPAZ_GEM",1,mapOf("FLAWED_TOPAZ_GEM" to 80)))
        val plan=SackCraftPlanner.plan(stock,recipes,emptySet())
        assertEquals(listOf("FINE_TOPAZ_GEM"),plan.steps.map { it.recipe.output })
        assertEquals(mapOf("ROUGH_TOPAZ_GEM" to 50L,"FLAWED_TOPAZ_GEM" to 63L,"FINE_TOPAZ_GEM" to 9L),plan.remaining)
    }
    @Test fun `normal combat sack upgrades chili peppers directly into stuffed peppers`() {
        val recipe=SackRecipe("STUFFED_CHILI_PEPPER",1,mapOf("CHILI_PEPPER" to 160))
        val chosen=SackCraftPlanner.directRecipes(setOf("CHILI_PEPPER"),listOf(recipe))
        assertEquals(listOf(recipe),chosen)
        assertEquals(mapOf("CHILI_PEPPER" to 13L,"STUFFED_CHILI_PEPPER" to 6L),SackCraftPlanner.planDirect(mapOf("CHILI_PEPPER" to 973L),chosen,emptySet()).remaining)
        assertTrue(SackCraftPlanner.planDirect(mapOf("CHILI_PEPPER" to 973L),chosen,setOf("STUFFED_CHILI_PEPPER")).steps.isEmpty())
    }
    @Test fun `gemstone compression stops at fine even when flawless recipe exists`() {
        val recipes=listOf(
            SackRecipe("FLAWED_RUBY_GEM",1,mapOf("ROUGH_RUBY_GEM" to 80)),
            SackRecipe("FINE_RUBY_GEM",1,mapOf("FLAWED_RUBY_GEM" to 80)),
            SackRecipe("FLAWLESS_RUBY_GEM",1,mapOf("FINE_RUBY_GEM" to 80)))
        val eligible=SackCraftPlanner.familyRecipes(recipes)
        val plan=SackCraftPlanner.plan(mapOf("ROUGH_RUBY_GEM" to 6401,"FLAWED_RUBY_GEM" to 80,"FINE_RUBY_GEM" to 80),eligible,emptySet())
        assertEquals(mapOf("ROUGH_RUBY_GEM" to 1L,"FINE_RUBY_GEM" to 82L),plan.remaining)
        assertEquals(setOf("FINE_RUBY_GEM"),SackCraftPlanner.upgradeTargets(setOf("ROUGH_RUBY_GEM","FLAWED_RUBY_GEM","FINE_RUBY_GEM"),eligible,setOf("ROUGH_RUBY_GEM","FLAWED_RUBY_GEM","FINE_RUBY_GEM","FLAWLESS_RUBY_GEM")))
    }
    @Test fun `command display names remove decorative private use glyphs without changing names`() {
        assertEquals("Flawless Ruby Gemstone",SackCraftPlanner.displayName("§5\uE010 Flawless Ruby Gemstone "))
        assertEquals("Enchanted Sunflower",SackCraftPlanner.displayName("\uDB80\uDC00 Enchanted Sunflower"))
        assertEquals("Jack o' Lantern",SackCraftPlanner.displayName("Jack o' Lantern"))
    }
    private val recipes=listOf(SackRecipe("ENCHANTED_NETHER_STALK",1,mapOf("NETHER_STALK" to 160)),SackRecipe("MUTANT_NETHER_STALK",1,mapOf("ENCHANTED_NETHER_STALK" to 160)))
    @Test fun `wart is compressed through both tiers and leftovers are kept`() {
        val p=SackCraftPlanner.plan(mapOf("NETHER_STALK" to 25601,"ENCHANTED_NETHER_STALK" to 160),recipes,emptySet())
        assertEquals(2L,p.remaining["MUTANT_NETHER_STALK"]);assertEquals(1L,p.remaining["NETHER_STALK"])
        assertEquals(2,p.steps.size)
    }
    @Test fun `legacy input selection remains protected and cannot be newly produced`() {
        val p=SackCraftPlanner.plan(mapOf("NETHER_STALK" to 25600),recipes,setOf("ENCHANTED_NETHER_STALK"))
        assertEquals(mapOf("NETHER_STALK" to 25600L),p.remaining)
        assertTrue(p.steps.isEmpty())
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
    @Test fun `ignored red mushroom block is never produced while lower compression remains allowed`() {
        val lower=SackRecipe("ENCHANTED_RED_MUSHROOM",1,mapOf("RED_MUSHROOM" to 160))
        val upper=SackRecipe("ENCHANTED_HUGE_MUSHROOM_2",1,mapOf("ENCHANTED_RED_MUSHROOM" to 160))
        val plan=SackCraftPlanner.plan(mapOf("RED_MUSHROOM" to 25600),listOf(lower,upper),setOf(upper.output))
        assertEquals(mapOf(lower.output to 160L),plan.remaining)
        assertFalse(SackCraftPlanner.allowed(upper,setOf(upper.output)))
        assertEquals(listOf(lower),plan.steps.map { it.recipe })
    }
    @Test fun `normal crops use direct counterparts without bread oil or a second tier`() {
        val raw=listOf("WHEAT","DOUBLE_PLANT","SUGAR_CANE","NETHER_STALK")
        val enchanted=listOf("ENCHANTED_WHEAT","ENCHANTED_SUNFLOWER","ENCHANTED_SUGAR","ENCHANTED_NETHER_STALK")
        val direct=raw.zip(enchanted).map { (input,output)->SackRecipe(output,1,mapOf(input to 160)) }
        val pool=direct+listOf(SackRecipe("ENCHANTED_BREAD",1,mapOf("WHEAT" to 60)),SackRecipe("SUNFLOWER_OIL",1,mapOf("ENCHANTED_SUNFLOWER" to 9)),SackRecipe("COMPACTED_SUNFLOWER",1,mapOf("ENCHANTED_SUNFLOWER" to 160)),recipes.last())
        val chosen=SackCraftPlanner.directRecipes(raw.toSet(),pool)
        val plan=SackCraftPlanner.planDirect(raw.associateWith { 25600L },chosen,emptySet())
        assertEquals(enchanted.toSet(),chosen.map { it.output }.toSet())
        assertEquals(enchanted.associateWith { 160L },plan.remaining)
        assertEquals(4,plan.steps.size)
        assertFalse(SackCraftPlanner.familyRecipes(pool).any { it.output in setOf("ENCHANTED_BREAD","SUNFLOWER_OIL") })
    }
    @Test fun `normal gemstone steps spend only each initial grade before lower outputs are produced`() {
        val pool=listOf(SackRecipe("FLAWED_RUBY_GEM",1,mapOf("ROUGH_RUBY_GEM" to 80)),SackRecipe("FINE_RUBY_GEM",1,mapOf("FLAWED_RUBY_GEM" to 80)),SackRecipe("FLAWLESS_RUBY_GEM",1,mapOf("FINE_RUBY_GEM" to 80)))
        val plan=SackCraftPlanner.planDirect(mapOf("ROUGH_RUBY_GEM" to 6400,"FLAWED_RUBY_GEM" to 80,"FINE_RUBY_GEM" to 80),pool,emptySet())
        assertEquals(listOf("FLAWLESS_RUBY_GEM","FINE_RUBY_GEM","FLAWED_RUBY_GEM"),plan.steps.map { it.recipe.output })
        assertEquals(mapOf("FLAWLESS_RUBY_GEM" to 1L,"FINE_RUBY_GEM" to 1L,"FLAWED_RUBY_GEM" to 80L),plan.remaining)
    }
    @Test fun `terminal target traversal rejects unrelated equipment and dangling cycles`() {
        val pool=listOf(SackRecipe("MID",1,mapOf("RAW" to 160)),SackRecipe("FINAL",1,mapOf("MID" to 160)),SackRecipe("SWORD",1,mapOf("FINAL" to 2)),SackRecipe("LOOP_A",1,mapOf("LOOP_B" to 2)),SackRecipe("LOOP_B",1,mapOf("LOOP_A" to 2)))
        assertEquals(setOf("FINAL"),SackCraftPlanner.upgradeTargets(setOf("RAW","LOOP_A"),pool,setOf("RAW","MID","FINAL","LOOP_A","LOOP_B")))
    }
    @Test fun `real sack titles normalize tiers and explicit gemstone plural alias only`() {
        val names=setOf("Gemstone","Agronomy","Enchanted Agronomy","Dwarven")
        assertEquals("Gemstone",SackCraftPlanner.sackName("Gemstones Sack",names))
        assertEquals("Gemstone",SackCraftPlanner.sackName("Large Gemstones Sacks",names))
        assertEquals("Enchanted Agronomy",SackCraftPlanner.sackName("Small Enchanted Agronomy Sack",names))
        assertEquals("Dwarven",SackCraftPlanner.sackName("Dwarven Mines Sack",names))
        assertNull(SackCraftPlanner.sackName("Sack of Sacks",names))
    }
}
