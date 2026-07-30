package gg.floyd.features.impl.render

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
    val itemModels: Set<Identifier>,
    val copiedEntries: Int,
)

/**
 * Converts Hypixel's server pack into an item-only pack. The custom SkyBlock item definitions,
 * models and textures stay available, while Minecraft GUI, font, shader and panorama overrides
 * are omitted so the rest of the game remains vanilla.
 */
internal object FloydSkyBlockLivePackCache {
    private const val CACHE_PREFIX = "skyblock-live-items-"
    private const val CACHE_SUFFIX = ".zip"
    private const val HYPIXEL_ASSET_PREFIX = "assets/hypixel_skyblock/"
    private const val ITEM_DEFINITION_PREFIX = "assets/hypixel_skyblock/items/"
    private const val MAX_ENTRY_COUNT = 50_000
    private const val MAX_UNCOMPRESSED_BYTES = 128L * 1024L * 1024L

    fun sanitize(input: Path, output: Path): FloydSanitizedSkyBlockPack {
        val itemModels = linkedSetOf<Identifier>()
        var copiedEntries = 0
        var copiedBytes = 0L

        ZipFile(input.toFile()).use { source ->
            ZipOutputStream(Files.newOutputStream(output)).use { target ->
                val entries = source.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !isSafeEntryName(entry.name)) continue
                    if (entry.name != "pack.mcmeta" && !entry.name.startsWith(HYPIXEL_ASSET_PREFIX)) continue
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

                    itemModelId(entry.name)?.let(itemModels::add)
                }
            }
        }

        require(copiedEntries > 1 && itemModels.isNotEmpty()) {
            "Hypixel pack did not contain usable SkyBlock item assets"
        }
        return FloydSanitizedSkyBlockPack(output, itemModels, copiedEntries)
    }

    fun inspect(path: Path): FloydSanitizedSkyBlockPack {
        val models = linkedSetOf<Identifier>()
        var entries = 0
        ZipFile(path.toFile()).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                if (!entry.isDirectory) entries++
                itemModelId(entry.name)?.let(models::add)
            }
        }
        require(models.isNotEmpty()) { "Cached Hypixel item pack contains no item definitions" }
        return FloydSanitizedSkyBlockPack(path, models, entries)
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
