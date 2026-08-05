package gg.floyd.features.impl.render

import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
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
}
