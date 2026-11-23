package gg.floyd.features.impl.render.itemsearch

import kotlin.test.*

class ItemSearchFormattingTest {
    @Test fun `drop fractions preserve exact decimal probabilities and conditions`() {
        assertEquals("1/100",ItemSearchFormatting.dropChance("1%").fraction)
        assertEquals("1/10,000",ItemSearchFormatting.dropChance("0.01%").fraction)
        assertEquals("3/10",ItemSearchFormatting.dropChance("30%").fraction)
        assertEquals("667/10,000",ItemSearchFormatting.dropChance("6.67%").fraction)
        assertEquals("1/1",ItemSearchFormatting.dropChance("100%").fraction)
        assertEquals("0/1",ItemSearchFormatting.dropChance("0%").fraction)
        assertEquals("2% (1/50) during event",ItemSearchFormatting.dropChance("2% during event").tooltip)
        assertNull(ItemSearchFormatting.dropChance("Unknown").fraction)
    }
    @Test fun `SkyBlock metadata remains valid modern SNBT for tooltip providers`() {
        for(item in ItemSearchCatalog.items) {
            try { net.minecraft.nbt.TagParser.parseCompoundFully(item.extraAttributes) }
            catch(failure:Exception) { fail("Invalid metadata for ${item.id}",failure) }
        }
        assertTrue(ItemSearchCatalog.byId.getValue("SCATHA;3").extraAttributes.contains("petInfo"))
    }
    @Test fun `coin labels are compact and hovers retain exact precision`() {
        assertEquals("125m",ItemSearchFormatting.compact("125000000"))
        assertEquals("100m",ItemSearchFormatting.compact("100000000"))
        assertEquals("1.25b",ItemSearchFormatting.compact("1250000000"))
        assertEquals("10k",ItemSearchFormatting.compact("10000"))
        assertEquals("125,000,000",ItemSearchFormatting.exact("125000000"))
        assertEquals("1,234.5",ItemSearchFormatting.exact("1234.5"))
        assertEquals("999",ItemSearchFormatting.compact("999"))
    }
    @Test fun `Scatha preview shows level one to one hundred stats and ability values`() {
        val pet=ItemSearchCatalog.byId.getValue("SCATHA;3")
        assertTrue(pet.name.contains("[Lvl 1-Lvl 100]"))
        assertTrue(pet.lore.any { it.contains("Mining Fortune:") && it.contains("+1.25-125") })
        assertTrue(pet.lore.any { it.contains("Mining Speed:") && it.contains("+1-100") })
        assertTrue(pet.lore.any { it.contains("+0.4-40%") })
        assertFalse((listOf(pet.name)+pet.lore).any { it.contains('{') || it.contains('?') })
    }
    @Test fun `every bundled pet template resolves without missing stats`() {
        val pets=ItemSearchCatalog.items.filter { it.name.contains("[Lvl 1-Lvl 100]") }
        assertTrue(pets.size>100)
        // Eerie's authored lore contains question marks; only template braces are invalid here.
        for(pet in pets) assertFalse((listOf(pet.name)+pet.lore).any { it.contains('{') },pet.id)
    }
}
