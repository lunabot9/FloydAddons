package gg.floyd.features.impl.render

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal data class FloydSanitizedSkyBlockPack(
    val path: Path,
    val baseModels: Map<Identifier, Identifier>,
    val copiedEntries: Int,
    val headSkins: Map<Identifier, Identifier> = emptyMap(),
)

/**
 * Converts Hypixel's server pack into a compatibility-focused cache. Item definitions and model
 * parents are retained so new custom IDs can be mapped back to vanilla models, and font resources
 * are preserved so SkyBlock glyph icons still render. Unrelated textures and GUI overrides are
 * omitted.
 */
internal object FloydSkyBlockLivePackCache {
    private const val CACHE_PREFIX = "skyblock-live-items-"
    private const val CACHE_SUFFIX = ".zip"
    private const val ITEM_DEFINITION_PREFIX = "assets/hypixel_skyblock/items/"
    private const val MODEL_DEFINITION_PREFIX = "assets/hypixel_skyblock/models/"
    private const val FONT_DIRECTORY_SEGMENT = "/font/"
    private const val FONT_TEXTURE_DIRECTORY_SEGMENT = "/textures/font/"
    private const val MAX_ENTRY_COUNT = 50_000
    private const val MAX_UNCOMPRESSED_BYTES = 128L * 1024L * 1024L

    fun sanitize(input: Path, output: Path): FloydSanitizedSkyBlockPack {
        var copiedEntries = 0
        var copiedBytes = 0L
        // Preserve the skin textures that `minecraft:head` item definitions reference so new
        // SkyBlock player heads keep rendering their intended texture. Without these the head
        // item defs point at missing resources and render as a null/empty head. Everything else
        // non-metadata is still stripped.
        val neededHeadTextures = headTexturePaths(input)

        ZipFile(input.toFile()).use { source ->
            ZipOutputStream(Files.newOutputStream(output)).use { target ->
                val entries = source.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !isSafeEntryName(entry.name)) continue
                    if (!isMetadataEntry(entry.name) && !neededHeadTextures.contains(entry.name)) continue
                    if (++copiedEntries > MAX_ENTRY_COUNT) error("Hypixel item pack contains too many entries")

                    target.putNextEntry(ZipEntry(entry.name))
                    source.getInputStream(entry).use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            copiedBytes += read
                            if (copiedBytes > MAX_UNCOMPRESSED_BYTES) {
                                error("Hypixel item pack expands beyond the safety limit")
                            }
                            target.write(buffer, 0, read)
                        }
                    }
                    target.closeEntry()
                }
            }
        }

        return inspect(output)
    }

    fun inspect(path: Path): FloydSanitizedSkyBlockPack {
        val itemDefinitions = linkedMapOf<Identifier, Identifier>()
        val modelParents = linkedMapOf<Identifier, Identifier>()
        val headSkins = linkedMapOf<Identifier, Identifier>()
        var entries = 0
        ZipFile(path.toFile()).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                if (!entry.isDirectory) entries++
                if (entry.isDirectory || !entry.name.endsWith(".json")) continue

                itemModelId(entry.name)?.let { itemId ->
                    val root = zip.getInputStream(entry).reader().use(JsonParser::parseReader)
                    findModelReference(root)?.let { itemDefinitions[itemId] = it }
                    findHeadTexture(root)?.let { headSkins[itemId] = it }
                }
                modelDefinitionId(entry.name)?.let { modelId ->
                    val root = zip.getInputStream(entry).reader().use(JsonParser::parseReader)
                    root.takeIf(JsonElement::isJsonObject)
                        ?.asJsonObject
                        ?.get("parent")
                        ?.takeIf(JsonElement::isJsonPrimitive)
                        ?.asString
                        ?.let(Identifier::tryParse)
                        ?.let { modelParents[modelId] = it }
                }
            }
        }

        val baseModels = itemDefinitions.mapNotNull { (itemId, modelId) ->
            resolveVanillaParent(modelId, modelParents)?.let { itemId to it }
        }.toMap()
        require(baseModels.isNotEmpty()) { "Cached Hypixel item pack contains no resolvable vanilla item parents" }
        return FloydSanitizedSkyBlockPack(path, baseModels, entries, headSkins)
    }

    fun latest(cacheDir: Path): FloydSanitizedSkyBlockPack? {
        if (!Files.isDirectory(cacheDir)) return null
        return Files.list(cacheDir).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().startsWith(CACHE_PREFIX) }
                .filter { it.fileName.toString().endsWith(CACHE_SUFFIX) }
                .sorted { first, second ->
                    Files.getLastModifiedTime(second).compareTo(Files.getLastModifiedTime(first))
                }
                .map { runCatching { inspect(it) }.getOrNull() }
                .filter { it != null }
                .findFirst()
                .orElse(null)
        }
    }

    fun target(cacheDir: Path, key: String): Path =
        cacheDir.resolve("$CACHE_PREFIX$key$CACHE_SUFFIX")

    private fun itemModelId(entryName: String): Identifier? {
        if (!entryName.startsWith(ITEM_DEFINITION_PREFIX) || !entryName.endsWith(".json")) return null
        val path = entryName.removePrefix(ITEM_DEFINITION_PREFIX).removeSuffix(".json")
        return Identifier.tryParse("hypixel_skyblock:$path")
    }

    private fun modelDefinitionId(entryName: String): Identifier? {
        if (!entryName.startsWith(MODEL_DEFINITION_PREFIX) || !entryName.endsWith(".json")) return null
        val path = entryName.removePrefix(MODEL_DEFINITION_PREFIX).removeSuffix(".json")
        return Identifier.tryParse("hypixel_skyblock:$path")
    }

    private fun findModelReference(element: JsonElement): Identifier? {
        if (element.isJsonObject) {
            val objectValue = element.asJsonObject
            objectValue.get("model")
                ?.takeIf(JsonElement::isJsonPrimitive)
                ?.asString
                ?.let(Identifier::tryParse)
                ?.let { return it }
            for ((_, value) in objectValue.entrySet()) {
                findModelReference(value)?.let { return it }
            }
        } else if (element.isJsonArray) {
            for (value in element.asJsonArray) {
                findModelReference(value)?.let { return it }
            }
        }
        return null
    }

    private fun headTexturePaths(input: Path): Set<String> {
        ZipFile(input.toFile()).use { zip ->
            val needed = linkedSetOf<String>()
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                if (entry.isDirectory) continue
                if (!entry.name.startsWith(ITEM_DEFINITION_PREFIX) || !entry.name.endsWith(".json")) continue
                val root = zip.getInputStream(entry).reader().use(JsonParser::parseReader)
                val texture = findHeadTexture(root) ?: continue
                val resourcePath = textureResourcePath(texture) ?: continue
                if (zip.getEntry(resourcePath) != null) needed.add(resourcePath)
            }
            return needed
        }
    }

    private fun findHeadTexture(element: JsonElement): Identifier? {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.get("type")?.takeIf(JsonElement::isJsonPrimitive)?.asString == "minecraft:special") {
                val inner = obj.get("model")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
                if (inner?.get("type")?.takeIf(JsonElement::isJsonPrimitive)?.asString == "minecraft:head") {
                    inner.get("texture")
                        ?.takeIf(JsonElement::isJsonPrimitive)
                        ?.asString
                        ?.let(Identifier::tryParse)
                        ?.let { return it }
                }
            }
            for ((_, value) in obj.entrySet()) findHeadTexture(value)?.let { return it }
        } else if (element.isJsonArray) {
            for (value in element.asJsonArray) findHeadTexture(value)?.let { return it }
        }
        return null
    }

    private fun textureResourcePath(texture: Identifier): String? {
        if (texture.namespace != "hypixel_skyblock" && texture.namespace != "minecraft") return null
        return "assets/${texture.namespace}/textures/${texture.path}.png"
    }

    private fun resolveVanillaParent(
        initialModel: Identifier,
        modelParents: Map<Identifier, Identifier>,
    ): Identifier? {
        var current = initialModel
        val visited = hashSetOf<Identifier>()
        repeat(32) {
            if (!visited.add(current)) return null
            if (current.namespace == "minecraft") return current
            current = modelParents[current] ?: return null
        }
        return null
    }

    private fun isMetadataEntry(name: String): Boolean =
        name == "pack.mcmeta" ||
            name.contains(FONT_DIRECTORY_SEGMENT) ||
            name.contains(FONT_TEXTURE_DIRECTORY_SEGMENT) ||
            (name.startsWith(ITEM_DEFINITION_PREFIX) && name.endsWith(".json")) ||
            (name.startsWith(MODEL_DEFINITION_PREFIX) && name.endsWith(".json"))

    private fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith("/") &&
            !name.contains('\\') &&
            name.split('/').none { it == ".." }
}

internal object FloydSkyBlockLivePackDownloader {
    private const val MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<FloydSanitizedSkyBlockPack>>()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun fetch(url: String, expectedSha1: String, cacheDir: Path): CompletableFuture<FloydSanitizedSkyBlockPack> {
        val uri = validatedUri(url)
        val key = expectedSha1.lowercase().takeIf { it.matches(Regex("[0-9a-f]{40}")) }
            ?: sha256(url.toByteArray()).take(40)
        val target = FloydSkyBlockLivePackCache.target(cacheDir, key)
        if (Files.isRegularFile(target)) {
            return CompletableFuture.completedFuture(FloydSkyBlockLivePackCache.inspect(target))
        }

        return inFlight.computeIfAbsent(key) {
            Files.createDirectories(cacheDir)
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "FloydAddons SkyBlock item compatibility")
                .GET()
                .build()

            http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply { response ->
                require(response.statusCode() in 200..299) {
                    "Hypixel item pack download failed with HTTP ${response.statusCode()}"
                }
                val bytes = response.body()
                require(bytes.size <= MAX_DOWNLOAD_BYTES) { "Hypixel item pack exceeds the download limit" }
                if (expectedSha1.matches(Regex("[0-9a-fA-F]{40}"))) {
                    require(sha1(bytes).equals(expectedSha1, ignoreCase = true)) {
                        "Hypixel item pack SHA-1 did not match the server packet"
                    }
                }

                val raw = Files.createTempFile(cacheDir, ".skyblock-live-raw-", ".zip")
                val sanitized = Files.createTempFile(cacheDir, ".skyblock-live-sanitized-", ".zip")
                try {
                    Files.write(raw, bytes)
                    val result = FloydSkyBlockLivePackCache.sanitize(raw, sanitized)
                    Files.move(
                        sanitized,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    result.copy(path = target)
                } finally {
                    Files.deleteIfExists(raw)
                    Files.deleteIfExists(sanitized)
                }
            }.whenComplete { _, _ -> inFlight.remove(key) }
        }
    }

    private fun validatedUri(url: String): URI {
        val uri = URI.create(url)
        val host = uri.host?.lowercase() ?: error("Hypixel item pack URL has no host")
        require(uri.scheme.equals("https", ignoreCase = true)) { "Hypixel item pack URL must use HTTPS" }
        require(host == "hypixel.net" || host.endsWith(".hypixel.net")) {
            "Refusing non-Hypixel item pack host"
        }
        return uri
    }

    private fun sha1(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes))

    private fun sha256(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
