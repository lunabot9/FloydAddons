package gg.floyd.features.impl.render

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
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
