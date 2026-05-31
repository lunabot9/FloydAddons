package gg.floyd.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentifiersTest {

    @Test
    fun `friendly name drops namespace and title-cases words`() {
        assertEquals("Diamond Ore", Identifiers.friendlyName("minecraft:diamond_ore"))
        assertEquals("Cobblestone", Identifiers.friendlyName("minecraft:cobblestone"))
        assertEquals("Stone", Identifiers.friendlyName("stone"))
    }

    @Test
    fun `friendly name tolerates degenerate ids`() {
        assertEquals("", Identifiers.friendlyName(""))
        assertEquals("minecraft:", Identifiers.friendlyName("minecraft:"))
        assertEquals("Pillager Spawn Egg", Identifiers.friendlyName("minecraft:pillager_spawn_egg"))
    }

    @Test
    fun `search matches both raw id and friendly-name substrings`() {
        val ids = listOf("minecraft:cobblestone", "minecraft:diamond_ore")
        val tokens = Identifiers.searchTokens(ids)

        // friendly-name substring ("cobble" is part of "Cobblestone")
        assertTrue(Identifiers.matches("minecraft:cobblestone", "cobble", tokens))
        // raw-id substring
        assertTrue(Identifiers.matches("minecraft:diamond_ore", "diamond_ore", tokens))
        // friendly-name word, case-insensitive
        assertTrue(Identifiers.matches("minecraft:diamond_ore", "Ore", tokens))
        // non-match
        assertFalse(Identifiers.matches("minecraft:cobblestone", "zombie", tokens))
    }

    @Test
    fun `blank query matches everything and missing ids fall back to on-the-fly blob`() {
        val tokens = Identifiers.searchTokens(listOf("minecraft:stone"))
        assertTrue(Identifiers.matches("minecraft:stone", "   ", tokens))
        // id not in the precomputed map still matches via fallback blob
        assertTrue(Identifiers.matches("minecraft:granite", "granite", tokens))
    }

    @Test
    fun `search tokens cover every id with lowercased blobs`() {
        val ids = listOf("minecraft:Diamond_Ore")
        val tokens = Identifiers.searchTokens(ids)
        assertEquals(1, tokens.size)
        assertEquals("minecraft:diamond_ore diamond ore", tokens["minecraft:Diamond_Ore"])
    }
}
