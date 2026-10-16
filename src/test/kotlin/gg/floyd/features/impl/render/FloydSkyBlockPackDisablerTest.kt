package gg.floyd.features.impl.render

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FloydSkyBlockPackDisablerTest {
    @Test
    fun `matches official SkyBlock packs and leaves other games alone`() {
        assertTrue(FloydSkyBlockPackPolicy.isOfficialSkyBlockPack("https://resourcepacks.hypixel.net/SkyBlock/test/84.zip"))
        assertFalse(FloydSkyBlockPackPolicy.isOfficialSkyBlockPack("https://assets.hypixel.net/bedwars/pack.zip"))
        assertFalse(FloydSkyBlockPackPolicy.isOfficialSkyBlockPack("https://example.com/SkyBlock/pack.zip"))
    }

    @Test
    fun `unknown menu model stays intact instead of becoming paper or a generic model parent`() {
        val current = Identifier.parse("hypixel_skyblock:item/island_relevant/garden/pests/salty_nozzle")
        for (skyBlockId in listOf(null, "SALTY_NOZZLE", "FUTURE_ITEM")) {
            assertEquals(current, FloydSkyBlockItemModelPolicy.resolveBaseModel(current, skyBlockId, emptyMap()))
        }
    }

    @Test
    fun `known legacy item still reverts to its mapped vanilla model`() {
        val current = Identifier.parse("hypixel_skyblock:item/weapon/aspect_of_the_void")
        val vanilla = Identifier.parse("minecraft:diamond_shovel")
        assertEquals(vanilla, FloydSkyBlockItemModelPolicy.resolveBaseModel(
            current, "ASPECT_OF_THE_VOID", mapOf("ASPECT_OF_THE_VOID" to vanilla),
        ))
    }

    @Test
    fun `quiver arrow reverts even without an item id`() {
        val current = Identifier.parse("hypixel_skyblock:item/quiver_arrow")
        val arrow = Identifier.parse("minecraft:arrow")
        assertEquals(arrow, FloydSkyBlockItemModelPolicy.resolveBaseModel(current, null, emptyMap(), arrow))
    }

    @Test
    fun `missing item model passes through for synthetic cross mod HUD previews`() {
        assertFalse(FloydSkyBlockItemModelPolicy.shouldReplaceCurrentModel(null, true, false))
        assertFalse(FloydSkyBlockItemModelPolicy.shouldReplaceCurrentModel(Identifier.parse("minecraft:paper"), true, false))
        assertFalse(FloydSkyBlockItemModelPolicy.shouldReplaceCurrentModel(Identifier.parse("hypixel_skyblock:test"), false, false))
        assertTrue(FloydSkyBlockItemModelPolicy.shouldReplaceCurrentModel(Identifier.parse("hypixel_skyblock:test"), true, false))
    }

    @Test
    fun `Noamm item data does not invent paper or bottle replacements for new nozzle controls`() {
        val data = javaClass.getResourceAsStream("/floyd_skyblock_items.json")!!.reader().use(JsonParser::parseReader).asJsonObject
        for (id in listOf("BASIC_NOZZLE", "JUICY_NOZZLE", "SALTY_NOZZLE", "JUICY_SPRAYONATOR", "SALTY_SPRAYONATOR")) {
            assertFalse(data.has(id), "$id must use its complete original pack model")
        }
        for ((id, model) in mapOf("ASPECT_OF_THE_VOID" to "minecraft:diamond_shovel", "TERMINATOR" to "minecraft:bow", "SUPERBOOM_TNT" to "minecraft:tnt")) {
            assertEquals(model, data[id].asJsonObject["model"].asString)
        }
        assertNotNull(data["SPRAYONATOR"].asJsonObject["texture"])
    }

    @Test
    fun `bundled pack includes base models and every custom model referenced by item definitions`() {
        val entries = bundledEntries()
        assertTrue(entries.keys.count { it.startsWith("assets/hypixel_skyblock/models/") } > 1000)
        for ((path, content) in entries) {
            if (!path.startsWith("assets/hypixel_skyblock/items/") || !path.endsWith(".json")) continue
            val root = JsonParser.parseString(content.toString(Charsets.UTF_8))
            for (model in modelReferences(root)) {
                if (!model.startsWith("hypixel_skyblock:")) continue
                val modelPath = "assets/hypixel_skyblock/models/${model.substringAfter(':')}.json"
                assertTrue(entries.containsKey(modelPath), "$path references absent $modelPath")
            }
        }
    }

    @Test
    fun `bundled fallback retains new salty sprayonator and nozzle definitions with head textures`() {
        val entries = bundledEntries()
        for (name in listOf("basic_nozzle", "juicy_nozzle", "salty_nozzle", "salty_sprayonator")) {
            assertNotNull(entries["assets/hypixel_skyblock/items/item/island_relevant/garden/pests/$name.json"])
            assertNotNull(entries["assets/hypixel_skyblock/textures/entity/uncategorized/$name.png"])
        }
        for (name in listOf("salty_sprayonator_basic_nozzle", "salty_sprayonator_juicy_nozzle")) {
            assertNotNull(entries["assets/hypixel_skyblock/textures/entity/uncategorized/$name.png"])
        }
    }

    @Test
    fun `old legacy-only cached pack is rejected while complete fallback is accepted`() {
        val legacy = Files.createTempFile("floyd-legacy-pack", ".zip")
        val complete = Files.createTempFile("floyd-complete-pack", ".zip")
        try {
            ZipOutputStream(Files.newOutputStream(legacy)).use {
                it.putNextEntry(ZipEntry("assets/hypixel_skyblock/items/test.json"))
                it.write("{}".toByteArray())
                it.closeEntry()
            }
            javaClass.getResourceAsStream("/floyd_skyblock_pack_fallback.zip")!!.use { Files.copy(it, complete, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            assertFalse(FloydSkyBlockPackContentPolicy.hasCompleteItemAssets(legacy))
            assertTrue(FloydSkyBlockPackContentPolicy.hasCompleteItemAssets(complete))
        } finally {
            Files.deleteIfExists(legacy)
            Files.deleteIfExists(complete)
        }
    }

    @Test
    fun `complete cached server pack receives missing nozzle assets without overriding live files`() {
        val cached = Files.createTempFile("floyd-cached-official", ".zip")
        var merged = cached
        val preserved = "assets/hypixel_skyblock/models/item/live.json"
        val nozzle = "assets/hypixel_skyblock/items/item/island_relevant/garden/pests/salty_nozzle.json"
        val nozzleTexture = "assets/hypixel_skyblock/textures/entity/uncategorized/salty_nozzle.png"
        // Simulate a newer live definition for an existing bundled item: it must take precedence.
        val liveOverride = "assets/hypixel_skyblock/items/item/island_relevant/garden/pests/basic_nozzle.json"
        try {
            ZipOutputStream(Files.newOutputStream(cached)).use { zip ->
                for ((name, body) in mapOf("pack.mcmeta" to "live metadata", preserved to "{}", liveOverride to "live override")) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(body.toByteArray())
                    zip.closeEntry()
                }
            }
            assertTrue(FloydSkyBlockPackContentPolicy.hasCompleteItemAssets(cached))
            merged = FloydSkyBlockPackContentPolicy.supplementMissingAssets(
                cached, javaClass.getResourceAsStream("/floyd_skyblock_pack_fallback.zip")!!,
            )
            ZipFile(merged.toFile()).use { zip ->
                assertNotNull(zip.getEntry(nozzle))
                assertNotNull(zip.getEntry(nozzleTexture))
                assertEquals("live override", zip.getInputStream(zip.getEntry(liveOverride)).reader().readText())
                assertEquals("live metadata", zip.getInputStream(zip.getEntry("pack.mcmeta")).reader().readText())
            }
            // The active cache can remain open during reload; supplementation must not modify it.
            ZipFile(cached.toFile()).use { assertEquals(3, it.size()) }
            assertEquals(merged, FloydSkyBlockPackContentPolicy.supplementMissingAssets(
                merged, javaClass.getResourceAsStream("/floyd_skyblock_pack_fallback.zip")!!,
            ))
        } finally {
            if (merged != cached) Files.deleteIfExists(merged)
            Files.deleteIfExists(cached)
        }
    }

    private fun bundledEntries(): Map<String, ByteArray> = buildMap {
        ZipInputStream(javaClass.getResourceAsStream("/floyd_skyblock_pack_fallback.zip")!!).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) put(entry.name, zip.readBytes())
            }
        }
    }

    private fun modelReferences(element: JsonElement): List<String> = buildList {
        if (element.isJsonObject) {
            for ((key, value) in element.asJsonObject.entrySet()) {
                if (key == "model" && value.isJsonPrimitive) add(value.asString)
                else addAll(modelReferences(value))
            }
        } else if (element.isJsonArray) {
            for (value in element.asJsonArray) addAll(modelReferences(value))
        }
    }
}
