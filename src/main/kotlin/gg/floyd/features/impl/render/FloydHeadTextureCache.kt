package gg.floyd.features.impl.render

import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.platform.NativeImage
import gg.floyd.FloydAddonsMod
import kotlinx.coroutines.*
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerSkin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.Base64
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** Item skulls need the original PNG alpha, not the opaque base layer forced on player bodies. */
object FloydHeadTextureCache {
    private class Entry { @Volatile var patch: PlayerSkin.Patch? = null; @Volatile var transparentPixels = 0; @Volatile var failed = false }
    private val entries = ConcurrentHashMap<String, Entry>()
    private val urls = ConcurrentHashMap<String, Optional<URI>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(4))
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    @JvmStatic fun patch(profile: GameProfile?): PlayerSkin.Patch? {
        val value = profile?.properties?.get("textures")?.firstOrNull()?.value ?: return null
        if (entries.size >= 1024 && urls[value] == null) return null
        val uri = urls.computeIfAbsent(value) { Optional.ofNullable(textureUrl(it)) }.orElse(null) ?: return null
        val hash = uri.path.substringAfterLast('/')
        return entries.computeIfAbsent(hash) {
            Entry().also { entry -> scope.launch { load(uri, hash, entry) } }
        }.patch
    }

    internal fun textureUrl(value: String): URI? = runCatching {
        if (value.length > 16384) return null
        val skin = JsonParser.parseString(String(Base64.getDecoder().decode(value), Charsets.UTF_8))
            .asJsonObject.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").asString
        val uri = URI(skin)
        if (uri.host != "textures.minecraft.net" || uri.scheme !in setOf("http", "https") ||
            !Regex("/texture/[a-fA-F0-9]{32,128}").matches(uri.path) || uri.query != null || uri.fragment != null || uri.userInfo != null || uri.port != -1) null
        else URI("https", "textures.minecraft.net", uri.path, null)
    }.getOrNull()

    private suspend fun load(uri: URI, hash: String, entry: Entry) {
        runCatching {
            val dir = FloydAddonsMod.configFile.toPath().resolve("head-textures")
            Files.createDirectories(dir)
            val file = dir.resolve("$hash.png")
            val bytes = if (Files.exists(file)) Files.readAllBytes(file) else {
                val response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofInputStream())
                require(response.statusCode() == 200)
                response.body().use { it.readNBytes(1024 * 1024 + 1) }.also {
                    require(it.size <= 1024 * 1024); Files.write(file, it)
                }
            }
            var image = NativeImage.read(bytes.inputStream())
            require(image.width == 64 && image.height in setOf(32, 64)) { "Unsupported skull skin size" }
            if (image.height == 32) {
                val legacy = image
                image = NativeImage(64, 64, true)
                for (y in 0 until 32) for (x in 0 until 64) image.setPixel(x, y, legacy.getPixel(x, y))
                legacy.close()
            }
            entry.transparentPixels = (0 until 16).sumOf { y -> (0 until 32).count { x -> image.getPixel(x, y).ushr(24) == 0 } }
            FloydAddonsMod.mc.execute {
                val texture = Identifier.fromNamespaceAndPath("floydaddons", "head_alpha/$hash")
                FloydAddonsMod.mc.textureManager.register(texture, DynamicTexture({ "Floyd transparent item head" }, image))
                entry.patch = PlayerSkin.Patch(Optional.of(ClientAsset.ResourceTexture(texture, texture)), Optional.empty(), Optional.empty(), Optional.empty())
            }
        }.onFailure { entry.failed = true; FloydAddonsMod.logger.warn("Could not preserve item-head alpha for {}", hash, it) }
    }
    fun state(): Map<String, Any?> = mapOf("loaded" to entries.values.count { it.patch != null },
        "transparentHeadPixels" to entries.values.sumOf { it.transparentPixels }, "failures" to entries.values.count { it.failed })
}
