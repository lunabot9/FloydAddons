package gg.floyd.features.impl.render.itemsearch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemSearchQueryTest {
    private fun matches(query: String) = ItemSearchQuery(query).matches("enchanted diamond sword", "enchanted_diamond_sword", "damage: +100 legendary sword")
    @Test fun `words are case insensitive and require all terms`() {
        assertTrue(matches("DIAMOND enchanted"))
        assertTrue(matches("@enchanted_diamond"))
        assertFalse(matches("diamond bow"))
        assertTrue(matches(""))
    }
    @Test fun `lore is opt in and exclusions apply to their own field`() {
        assertFalse(matches("legendary"))
        assertTrue(matches("#legendary !bow"))
        assertFalse(matches("diamond !#legendary"))
        assertFalse(matches("!@enchanted"))
    }
    @Test fun `phrases and alternatives preserve grouping`() {
        assertTrue(matches("\"diamond sword\""))
        assertFalse(matches("\"sword diamond\""))
        assertTrue(matches("bow | diamond !bow"))
        assertFalse(matches("bow | diamond !sword"))
        assertFalse(matches("bow |"))
        assertTrue(matches("#\"legendary sword\""))
    }
}
