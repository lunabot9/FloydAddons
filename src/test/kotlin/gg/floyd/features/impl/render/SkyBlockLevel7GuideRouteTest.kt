package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkyBlockLevel7GuideRouteTest {
    @Test
    fun `route contains every supplied step exactly once and every detector is documented`() {
        val steps = SkyBlockLevel7GuideRoute.steps

        assertEquals(119, steps.size)
        assertEquals((1..119).toList(), steps.map { it.number })
        assertTrue(steps.all { it.label.isNotBlank() })
        assertTrue(steps.all { it.instruction.isNotBlank() })
        assertTrue(steps.all { it.instruction.length > it.label.length })
        assertTrue(steps.all { it.detection.isNotBlank() })
        assertEquals("Farm fields", steps.first().label)
        assertEquals(
            "In the Hub farm, break wheat, carrots, or potatoes until at least one crop enters your inventory.",
            steps.first().instruction,
        )
        assertEquals("Add Plumber Joe contact", steps.last().label)
        assertTrue(steps.last().instruction.contains("right-click", ignoreCase = true))
    }

    @Test
    fun `crop acquisition and shop sale use inventory deltas rather than menu opens alone`() {
        val crops = listOf(SkyBlockGuideItem("WHEAT", "Wheat", 64))
        assertTrue(
            SkyBlockLevel7GuideRoute.isComplete(
                1,
                SkyBlockGuideObservation(inventory = crops),
                SkyBlockGuideBaseline(),
            )
        )
        assertFalse(
            SkyBlockLevel7GuideRoute.isComplete(
                2,
                SkyBlockGuideObservation(screenTitle = "Alchemist", inventory = crops),
                SkyBlockGuideBaseline(inventory = crops),
            )
        )
        assertTrue(
            SkyBlockLevel7GuideRoute.isComplete(
                2,
                SkyBlockGuideObservation(screenTitle = "Alchemist", inventory = emptyList()),
                SkyBlockGuideBaseline(inventory = crops),
            )
        )
    }

    @Test
    fun `skill detector accepts Hypixel roman numeral level-up messages`() {
        assertEquals("farming" to 10, SkyBlockLevel7GuideRoute.parseSkillLevel("SKILL LEVEL UP Farming X"))
        assertEquals("mining" to 12, SkyBlockLevel7GuideRoute.parseSkillLevel("Mining XII"))
        assertEquals("foraging" to 12, SkyBlockLevel7GuideRoute.parseSkillLevel("Foraging 12"))
        assertEquals(12, SkyBlockLevel7GuideRoute.romanToInt("XII"))
    }

    @Test
    fun `purse detector accepts commas decimals and compact suffixes`() {
        assertEquals(175_250L, SkyBlockLevel7GuideRoute.parsePurse(listOf("Purse: 175,250")))
        assertEquals(175_500L, SkyBlockLevel7GuideRoute.parsePurse(listOf("Purse: 175.5k")))
        assertEquals(2_100_000L, SkyBlockLevel7GuideRoute.parsePurse(listOf("Piggy: 2.1M")))
    }

    @Test
    fun `Treecap coin step waits for and respects the live lowest BIN`() {
        val baseline = SkyBlockGuideBaseline()
        assertFalse(
            SkyBlockLevel7GuideRoute.isComplete(
                47,
                SkyBlockGuideObservation(purse = 999_999, treecapLowestBin = null),
                baseline,
            )
        )
        assertFalse(
            SkyBlockLevel7GuideRoute.isComplete(
                47,
                SkyBlockGuideObservation(purse = 219_999, treecapLowestBin = 220_000),
                baseline,
            )
        )
        assertTrue(
            SkyBlockLevel7GuideRoute.isComplete(
                47,
                SkyBlockGuideObservation(purse = 220_000, treecapLowestBin = 220_000),
                baseline,
            )
        )
    }

    @Test
    fun `Lumber Jack reward does not reuse an axe bought earlier in the route`() {
        val existing = listOf(SkyBlockGuideItem("PROMISING_AXE", "Promising Axe"))
        assertFalse(
            SkyBlockLevel7GuideRoute.isComplete(
                57,
                SkyBlockGuideObservation(inventory = existing),
                SkyBlockGuideBaseline(inventory = existing),
            )
        )
        assertTrue(
            SkyBlockLevel7GuideRoute.isComplete(
                57,
                SkyBlockGuideObservation(inventory = listOf(existing.single().copy(count = 2))),
                SkyBlockGuideBaseline(inventory = existing),
            )
        )
    }

    @Test
    fun `merchant steps require the complete explicit shopping lists`() {
        val axes = listOf(
            SkyBlockGuideItem("STICK", "Stick", 32),
            SkyBlockGuideItem("ROOKIE_AXE", "Rookie Axe"),
            SkyBlockGuideItem("PROMISING_AXE", "Promising Axe"),
            SkyBlockGuideItem("SWEET_AXE", "Sweet Axe"),
            SkyBlockGuideItem("EFFICIENT_AXE", "Efficient Axe"),
        )
        assertTrue(SkyBlockLevel7GuideRoute.isComplete(10, SkyBlockGuideObservation(inventory = axes), SkyBlockGuideBaseline()))
        assertFalse(SkyBlockLevel7GuideRoute.isComplete(10, SkyBlockGuideObservation(inventory = axes.dropLast(1)), SkyBlockGuideBaseline()))

        val weapons = listOf(
            "Undead Sword", "End Sword", "Spider Sword", "Wither Bow",
        ).map { SkyBlockGuideItem(it.uppercase().replace(' ', '_'), it) }
        assertTrue(SkyBlockLevel7GuideRoute.isComplete(13, SkyBlockGuideObservation(inventory = weapons), SkyBlockGuideBaseline()))
        assertFalse(SkyBlockLevel7GuideRoute.isComplete(13, SkyBlockGuideObservation(inventory = weapons.dropLast(1)), SkyBlockGuideBaseline()))

        val miningSupplies = listOf(
            SkyBlockGuideItem("ROOKIE_PICKAXE", "Rookie Pickaxe"),
            SkyBlockGuideItem("PROMISING_PICKAXE", "Promising Pickaxe"),
            SkyBlockGuideItem("COAL_BLOCK", "Block of Coal", 2),
            SkyBlockGuideItem("GOLD_BLOCK", "Block of Gold", 2),
            SkyBlockGuideItem("GOLD_INGOT", "Gold Ingot"),
        )
        assertTrue(SkyBlockLevel7GuideRoute.isComplete(18, SkyBlockGuideObservation(inventory = miningSupplies), SkyBlockGuideBaseline()))
    }

    @Test
    fun `museum route obtains Rogue Sword before donating and ignores non museum inventory loss`() {
        val steps = SkyBlockLevel7GuideRoute.steps
        val weaponsInstruction = steps.single { it.number == 13 }.instruction
        val miningInstruction = steps.single { it.number == 18 }.instruction
        val donationInstruction = steps.single { it.number == 37 }.instruction

        assertTrue(weaponsInstruction.contains("skip the vanilla Diamond Sword and Bow", ignoreCase = true))
        assertTrue(miningInstruction.contains("skip the Golden Pickaxe", ignoreCase = true))
        assertTrue(donationInstruction.contains("Do not donate the Golden Shovel", ignoreCase = true))
        assertTrue(steps.single { it.number == 35 }.instruction.contains("Jamie", ignoreCase = true))
        assertTrue(SkyBlockLevel7GuideRoute.isComplete(
            35,
            SkyBlockGuideObservation(inventory = listOf(SkyBlockGuideItem("ROGUE_SWORD", "Rogue Sword"))),
            SkyBlockGuideBaseline(),
        ))

        val rogueSword = SkyBlockGuideItem("ROGUE_SWORD", "Rogue Sword")
        assertTrue(SkyBlockLevel7GuideRoute.isComplete(
            37,
            SkyBlockGuideObservation(screenTitle = "Museum", inventory = emptyList()),
            SkyBlockGuideBaseline(inventory = listOf(rogueSword)),
        ))
        assertFalse(SkyBlockLevel7GuideRoute.isComplete(
            37,
            SkyBlockGuideObservation(screenTitle = "Museum", inventory = emptyList()),
            SkyBlockGuideBaseline(inventory = listOf(
                SkyBlockGuideItem("DIAMOND_SWORD", "Diamond Sword"),
                SkyBlockGuideItem("GOLD_PICKAXE", "Golden Pickaxe"),
            )),
        ))
    }

    @Test
    fun `precise route does not skip Charlie completion from equipped trousers alone`() {
        val wearingTrousers = SkyBlockGuideObservation(
            armor = mapOf("legs" to SkyBlockGuideItem("CHARLIE_TROUSERS", "Charlie's Trousers")),
        )
        assertFalse(SkyBlockLevel7GuideRoute.isComplete(67, wearingTrousers, SkyBlockGuideBaseline()))
        assertTrue(
            SkyBlockLevel7GuideRoute.isComplete(
                67,
                SkyBlockGuideObservation(chat = "QUEST COMPLETE: Into the Woods"),
                SkyBlockGuideBaseline(),
            )
        )
    }

    @Test
    fun `harp step requires all eleven songs at the current ninety percent threshold`() {
        val songs = listOf(
            "Hymn To The Joy", "Frère Jacques", "Amazing Grace", "Brahm's Lullaby",
            "Happy Birthday To You", "Greensleeves", "Geothermy?", "Minuet",
            "Joy To The World", "Godly Imagination", "La Vie En Rose",
        ).map { SkyBlockGuideItem("SONG", it, lore = listOf("Best: 90%!")) }

        assertTrue(
            SkyBlockLevel7GuideRoute.isComplete(
                98,
                SkyBlockGuideObservation(screenTitle = "Melody", screenItems = songs),
                SkyBlockGuideBaseline(),
            )
        )
        val incomplete = songs.toMutableList().also { it[10] = it[10].copy(lore = listOf("Best: 89%")) }
        assertFalse(
            SkyBlockLevel7GuideRoute.isComplete(
                98,
                SkyBlockGuideObservation(screenTitle = "Melody", screenItems = incomplete),
                SkyBlockGuideBaseline(),
            )
        )
    }

    @Test
    fun `contact steps recover from either chat confirmation or existing Abiphone menu state`() {
        assertTrue(
            SkyBlockLevel7GuideRoute.isComplete(
                109,
                SkyBlockGuideObservation(chat = "✆ Alda has been added to your Abiphone's contacts!"),
                SkyBlockGuideBaseline(),
            )
        )
        assertTrue(
            SkyBlockLevel7GuideRoute.isComplete(
                119,
                SkyBlockGuideObservation(
                    screenTitle = "Abiphone Contacts",
                    screenItems = listOf(SkyBlockGuideItem("NPC", "Plumber Joe")),
                ),
                SkyBlockGuideBaseline(),
            )
        )
    }

    @Test
    fun `all route lookups are available`() {
        (1..119).forEach { assertNotNull(SkyBlockLevel7GuideRoute.step(it)) }
        assertEquals(null, SkyBlockLevel7GuideRoute.step(120))
    }
}
