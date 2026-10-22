package gg.floyd.supercraft

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SupercraftMenuReadTest {
    private val names=mapOf("ENCHANTED_NETHER_STALK" to "Enchanted Nether Wart","GOLDEN_CARROT" to "Golden Carrot")
    @Test fun `server amount selects storage acknowledgement mode`() {
        assertEquals(SupercraftAmount(1111,true),SupercraftMenuRead.amount("§aCrafting 1,111 items into your sacks!"))
        assertEquals(SupercraftAmount(1,false),SupercraftMenuRead.amount("Crafting 1 item into your inventory!"))
        assertNull(SupercraftMenuRead.amount("Crafting 1.2M items into your sacks!"))
        assertNull(SupercraftMenuRead.amount("Click to craft!"))
    }
    @Test fun `known output is insufficient without an exact ingredient cost match`() {
        val recipe=SackRecipe("MUTANT_NETHER_STALK",1,mapOf("ENCHANTED_NETHER_STALK" to 160))
        val amount=SupercraftAmount(2,true)
        val cost=SupercraftMenuRead.costs("✔ 321/320 Enchanted Nether Wart",names)!!
        assertTrue(SupercraftMenuRead.agrees(recipe,amount,cost))
        assertFalse(SupercraftMenuRead.agrees(recipe,amount,mapOf("ENCHANTED_NETHER_STALK" to 160)))
        assertFalse(SupercraftMenuRead.agrees(recipe,amount,cost+("GOLDEN_CARROT" to 1)))
        assertNull(SupercraftMenuRead.costs("✔ 321/320 Unknown Ingredient",names))
    }
    @Test fun `bulk cost lore supports the server multiplier decoration`() {
        assertEquals(mapOf("ENCHANTED_NETHER_STALK" to 32L),SupercraftMenuRead.costs(" ✔ 177,889/32 (5,559x) Enchanted Nether Wart",names))
    }
}
