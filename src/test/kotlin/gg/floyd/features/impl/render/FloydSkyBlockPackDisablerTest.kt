package gg.floyd.features.impl.render

import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FloydSkyBlockPackDisablerTest {
    @Test
    fun `matches official Hypixel SkyBlock pack URLs`() {
        assertTrue(FloydSkyBlockPackPolicy.isOfficialSkyBlockPack("https://assets.hypixel.net/SkyBlock/pack.zip"))
        assertTrue(FloydSkyBlockPackPolicy.isOfficialSkyBlockPack("https://HYPIXEL.NET/resources/skyblock-pack.zip"))
        assertTrue(
            FloydSkyBlockPackPolicy.isOfficialSkyBlockPack(
                "https://resourcepacks.hypixel.net/SkyBlock/5c59e0a9-9865-4d4e-91d2-915515672cbd/84.zip"
            )
        )
    }

    @Test
    fun `does not block unrelated server packs`() {
        assertFalse(FloydSkyBlockPackPolicy.isOfficialSkyBlockPack("https://assets.hypixel.net/bedwars/pack.zip"))
        assertFalse(FloydSkyBlockPackPolicy.isOfficialSkyBlockPack("https://example.com/SkyBlock/pack.zip"))
        assertFalse(FloydSkyBlockPackPolicy.isOfficialSkyBlockPack("https://example.com/pack.zip"))
    }

    @Test
    fun `bundled compatibility data covers the currently broken aspect of the void model`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val aspectOfTheVoid = JsonParser.parseReader(reader).asJsonObject["ASPECT_OF_THE_VOID"]
            assertNotNull(aspectOfTheVoid)
            assertTrue(aspectOfTheVoid.asJsonObject["model"].asString.startsWith("minecraft:"))
        }

        assertNotNull(javaClass.getResource("/floyd_skyblock_pack_fallback.zip"))
    }

    @Test
    fun `bundled item table still carries sprayonator variants used by the upstream fallback path`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val json = JsonParser.parseReader(reader).asJsonObject
            assertNotNull(json["SPRAYONATOR"])
            assertTrue(json["SPRAYONATOR"].asJsonObject["model"].asString.startsWith("minecraft:"))
        }
    }

    @Test
    fun `bundled item table covers safari and critter ids that recently regressed to null`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val json = JsonParser.parseReader(reader).asJsonObject
            val requiredIds = listOf(
                "SAFARI_BELT",
                "SAFARI_BELT_COMMON",
                "SAFARI_BELT_UNCOMMON",
                "SAFARI_BELT_RARE",
                "SAFARI_BELT_EPIC",
                "SAFARI_BELT_LEGENDARY",
                "SAFARI_BELT_MYTHIC",
                "RAINBOW_FEATHER",
                "CRITTER_CAPSULE",
                "BAG_OF_SEEDS",
                "YOGI_BERRY",
                "WRIGGLEWORM",
                "SOOTHING_INCENSE",
                "SHINING_COIN",
                "BASIC_NOZZLE",
                "JUICY_NOZZLE",
                "SALTY_NOZZLE",
                "ARCHITECTS_FIRST_DRAFT",
                "ARCHITECT_FIRST_DRAFT",
                "LUMBERJACK_RING",
                "LUMBERJACK_TALISMAN",
                "TORRHUS_ARTIFACT",
                "TORRHUS_BELT",
                "TORRHUS_RING",
                "TORRHUS_TALISMAN",
                "HONEYCOMB_TALISMAN",
                "HONEYCOMB_RING",
                "HONEYCOMB_ARTIFACT",
                "HONEYCOMB_NECKLACE",
                "HONEY_POT_BEHEMOTH",
                "BEHEMOTH_POT_OF_HONEYCOMB",
            )

            for (skyBlockId in requiredIds) {
                val fallback = json[skyBlockId]
                assertNotNull(fallback, "Missing bundled fallback for $skyBlockId")
                assertTrue(
                    fallback.asJsonObject["model"].asString.startsWith("minecraft:"),
                    "Fallback for $skyBlockId should resolve to a vanilla item model"
                )
            }
        }
    }

    @Test
    fun `bundled item table covers newer legacy fallback items from the refreshed pack`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val json = JsonParser.parseReader(reader).asJsonObject
            val requiredIds = listOf(
                "FLINT_ARROW",
                "BONEMERANG",
                "SPIRIT_SHORTBOW",
                "DIVANS_DRILL",
                "STONK",
            )

            for (skyBlockId in requiredIds) {
                val fallback = json[skyBlockId]
                assertNotNull(fallback, "Missing bundled fallback for $skyBlockId")
                assertTrue(
                    fallback.asJsonObject["model"].asString.startsWith("minecraft:"),
                    "Fallback for $skyBlockId should resolve to a vanilla item model"
                )
            }
        }
    }

    @Test
    fun `bundled item table uses stable vanilla item models for shortbows and superboom`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val json = JsonParser.parseReader(reader).asJsonObject
            val expectedModels = mapOf(
                "ARTISANAL_SHORTBOW" to "minecraft:bow",
                "DRAGON_SHORTBOW" to "minecraft:bow",
                "JUJU_SHORTBOW" to "minecraft:bow",
                "MACHINE_GUN_SHORTBOW" to "minecraft:bow",
                "MOSQUITO_SHORTBOW" to "minecraft:bow",
                "SPIDER_SHORTBOW" to "minecraft:bow",
                "SPIRIT_SHORTBOW" to "minecraft:bow",
                "TERMINATOR" to "minecraft:bow",
                "SUPERBOOM_TNT" to "minecraft:tnt",
            )

            for ((skyBlockId, expectedModel) in expectedModels) {
                val fallback = json[skyBlockId]
                assertNotNull(fallback, "Missing bundled fallback for $skyBlockId")
                assertEquals(
                    expectedModel,
                    fallback.asJsonObject["model"].asString,
                    "Fallback for $skyBlockId should point to a stable vanilla item model"
                )
            }
        }
    }

    @Test
    fun `bundled item table uses honeycomb fallbacks for honey accessories`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val json = JsonParser.parseReader(reader).asJsonObject
            val honeyAccessories = listOf(
                "HONEYCOMB_TALISMAN",
                "HONEYCOMB_RING",
                "HONEYCOMB_ARTIFACT",
                "HONEYCOMB_NECKLACE",
            )

            for (skyBlockId in honeyAccessories) {
                val fallback = json[skyBlockId]
                assertNotNull(fallback, "Missing bundled fallback for $skyBlockId")
                assertEquals(
                    "minecraft:honeycomb",
                    fallback.asJsonObject["model"].asString,
                    "Fallback for $skyBlockId should point to honeycomb instead of paper"
                )
            }
        }
    }

    @Test
    fun `bundled item table uses map fallbacks for architect first draft variants`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val json = JsonParser.parseReader(reader).asJsonObject
            val architectDrafts = listOf(
                "ARCHITECTS_FIRST_DRAFT",
                "ARCHITECT_FIRST_DRAFT",
            )

            for (skyBlockId in architectDrafts) {
                val fallback = json[skyBlockId]
                assertNotNull(fallback, "Missing bundled fallback for $skyBlockId")
                assertEquals(
                    "minecraft:map",
                    fallback.asJsonObject["model"].asString,
                    "Fallback for $skyBlockId should point to map instead of paper"
                )
            }
        }
    }

    @Test
    fun `bundled item table uses stable fallbacks for lumberjack and torrhus accessories`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val json = JsonParser.parseReader(reader).asJsonObject
            val expectedModels = mapOf(
                "LUMBERJACK_RING" to "minecraft:wooden_axe",
                "LUMBERJACK_TALISMAN" to "minecraft:wooden_axe",
                "TORRHUS_ARTIFACT" to "minecraft:leather",
                "TORRHUS_BELT" to "minecraft:leather",
                "TORRHUS_RING" to "minecraft:leather",
                "TORRHUS_TALISMAN" to "minecraft:leather",
            )

            for ((skyBlockId, expectedModel) in expectedModels) {
                val fallback = json[skyBlockId]
                assertNotNull(fallback, "Missing bundled fallback for $skyBlockId")
                assertEquals(
                    expectedModel,
                    fallback.asJsonObject["model"].asString,
                    "Fallback for $skyBlockId should point to a stable vanilla item model"
                )
            }
        }
    }

    @Test
    fun `bundled fallback zip contains merged legacy item definitions`() {
        val resource = javaClass.getResourceAsStream("/floyd_skyblock_pack_fallback.zip")
        assertNotNull(resource)

        ZipInputStream(resource).use { zip ->
            var foundMergedBonemerang = false
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "assets/hypixel_skyblock/items/item/island_relevant/dungeons/bonemerang.json") {
                    foundMergedBonemerang = true
                    break
                }
            }
            assertTrue(foundMergedBonemerang, "Expected merged legacy bonemerang item definition in bundled fallback zip")
        }
    }

    @Test
    fun `bundled item table covers new legacy head models through the local fallback pack`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")
        assertNotNull(data)
        data.reader().use { reader ->
            val json = JsonParser.parseReader(reader).asJsonObject
            val fallback = json["ABIPHONE_X_BLUE"]
            assertNotNull(fallback, "Missing bundled fallback for ABIPHONE_X_BLUE")
            assertEquals(
                "hypixel_skyblock:item/abiphones/x/abiphone_x_blue",
                fallback.asJsonObject["model"].asString,
            )
        }
    }

    @Test
    fun `prefers cached live pack base model when bundled table lacks a new item`() {
        val currentModel = Identifier.parse("hypixel_skyblock:item/boosters/common/booster_foraging_wisdom")
        val resolved = FloydSkyBlockItemModelPolicy.resolveBaseModel(
            currentModel = currentModel,
            skyBlockId = null,
            liveModels = mapOf(currentModel to Identifier.parse("minecraft:paper")),
            knownModels = emptyMap(),
            vanillaItemModel = Identifier.parse("hypixel_skyblock:item/unknown"),
        )

        assertEquals(Identifier.parse("minecraft:paper"), resolved)
    }

    @Test
    fun `prefers cached live pack base model over bundled paper placeholders`() {
        val currentModel = Identifier.parse("hypixel_skyblock:item/island_relevant/garden/pests/basic_nozzle")
        val resolved = FloydSkyBlockItemModelPolicy.resolveBaseModel(
            currentModel = currentModel,
            skyBlockId = "BASIC_NOZZLE",
            liveModels = mapOf(currentModel to Identifier.parse("minecraft:glass_bottle")),
            knownModels = mapOf("BASIC_NOZZLE" to Identifier.parse("minecraft:glass_bottle")),
            vanillaItemModel = Identifier.parse("hypixel_skyblock:item/unknown"),
        )

        assertEquals(Identifier.parse("minecraft:glass_bottle"), resolved)
    }
}
