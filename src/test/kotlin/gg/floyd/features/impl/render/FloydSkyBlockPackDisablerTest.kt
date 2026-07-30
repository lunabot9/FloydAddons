package gg.floyd.features.impl.render

import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
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
    fun `unknown alpha item ids fall back to their base vanilla item model`() {
        val vanillaModel = Identifier.parse("minecraft:diamond_sword")

        assertEquals(
            vanillaModel,
            FloydSkyBlockItemModelPolicy.resolveBaseModel(
                skyBlockId = "NEW_ALPHA_ITEM_NOT_IN_THE_BUNDLED_TABLE",
                knownModels = emptyMap(),
                vanillaItemModel = vanillaModel,
            ),
        )
    }

    @Test
    fun `live pack sanitizer keeps Hypixel item assets and removes global Minecraft overrides`() {
        val input = Files.createTempFile("floyd-live-pack-input", ".zip")
        val output = Files.createTempFile("floyd-live-pack-output", ".zip")
        ZipOutputStream(Files.newOutputStream(input)).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
            entry("pack.mcmeta", """{"pack":{"pack_format":84,"description":"Hypixel SkyBlock"}}""")
            entry(
                "assets/hypixel_skyblock/items/item/alpha/new_food.json",
                """{"model":{"type":"minecraft:model","model":"hypixel_skyblock:item/alpha/new_food"}}""",
            )
            entry(
                "assets/hypixel_skyblock/models/item/alpha/new_food.json",
                """{"parent":"item/paper","textures":{"layer0":"hypixel_skyblock:item/alpha/new_food"}}""",
            )
            entry("assets/hypixel_skyblock/textures/item/alpha/new_food.png", "fake-png")
            entry("assets/minecraft/textures/gui/title/background/panorama_0.png", "unwanted")
            entry("assets/minecraft/font/default.json", "unwanted")
        }

        val result = FloydSkyBlockLivePackCache.sanitize(input, output)

        ZipFile(output.toFile()).use { zip ->
            assertNotNull(zip.getEntry("pack.mcmeta"))
            assertNotNull(zip.getEntry("assets/hypixel_skyblock/items/item/alpha/new_food.json"))
            assertNotNull(zip.getEntry("assets/hypixel_skyblock/models/item/alpha/new_food.json"))
            assertNotNull(zip.getEntry("assets/hypixel_skyblock/textures/item/alpha/new_food.png"))
            assertEquals(null, zip.getEntry("assets/minecraft/textures/gui/title/background/panorama_0.png"))
            assertEquals(null, zip.getEntry("assets/minecraft/font/default.json"))
        }
        assertEquals(setOf(Identifier.parse("hypixel_skyblock:item/alpha/new_food")), result.itemModels)
    }

    @Test
    fun `reloading repository does not overwrite its mounted fallback zip`() {
        val target = Files.createTempDirectory("floyd-pack-test").resolve("fallback.zip")
        javaClass.getResourceAsStream("/floyd_skyblock_pack_fallback.zip")!!.use {
            FloydSkyBlockPackMaterializer.materialize(it, target)
        }
        val mountedTimestamp = FileTime.fromMillis(1_000_000L)
        Files.setLastModifiedTime(target, mountedTimestamp)

        javaClass.getResourceAsStream("/floyd_skyblock_pack_fallback.zip")!!.use { input ->
            FloydSkyBlockPackMaterializer.materialize(input, target)
        }

        assertTrue(Files.getLastModifiedTime(target) == mountedTimestamp)
    }
}
