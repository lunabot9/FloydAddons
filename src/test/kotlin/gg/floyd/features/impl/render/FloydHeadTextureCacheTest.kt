package gg.floyd.features.impl.render

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloydHeadTextureCacheTest {
    private fun property(url: String) = Base64.getEncoder().encodeToString("""{"textures":{"SKIN":{"url":"$url"}}}""".toByteArray())
    @Test fun `head textures use the original mojang texture asset`() {
        val hash = "5e8e20f1534c2a9d940bac97c4c4b29b68db6a286ad9c92ae85aee78e0486043"
        assertEquals("https://textures.minecraft.net/texture/$hash", FloydHeadTextureCache.textureUrl(property("http://textures.minecraft.net/texture/$hash")).toString())
    }
    @Test fun `malformed or unrelated texture properties stay on the existing renderer`() {
        assertNull(FloydHeadTextureCache.textureUrl("not base64"))
        assertNull(FloydHeadTextureCache.textureUrl(property("https://example.com/texture/" + "a".repeat(64))))
        assertNull(FloydHeadTextureCache.textureUrl(property("https://textures.minecraft.net/not-a-texture")))
    }
}
