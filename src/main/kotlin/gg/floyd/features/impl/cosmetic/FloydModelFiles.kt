package gg.floyd.features.impl.cosmetic

import com.mojang.blaze3d.platform.NativeImage
import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.utils.openDirectory
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

/** Formats Floyd's player renderer can load from disk. */
enum class ModelFileFormat {
    OBJ,
    GLB,
    UNSUPPORTED;

    companion object {
        fun of(fileName: String): ModelFileFormat = when (fileName.substringAfterLast('.', "").lowercase()) {
            "obj" -> OBJ
            "glb" -> GLB
            else -> UNSUPPORTED
        }
    }
}

/** A model file found in {@code config/floydaddons/models}. */
data class ModelFileEntry(val name: String, val fileName: String, val path: Path, val format: ModelFileFormat) {
    val texturePath: Path? get() = path.resolveSibling(fileName.substringBeforeLast('.', fileName) + ".png")
}

/**
 * User-facing model directory. The bundled models are copied here on first use so they can be
 * replaced or inspected, and any OBJ/GLB dropped in is selectable through the Player Model module.
 */
internal object FloydModelFiles {
    private val bundledFiles = listOf(
        "player_models/low_poly_tung.obj" to "low_poly_tung.obj",
        "textures/entity/player_model/low_poly_tung.png" to "low_poly_tung.png",
        "player_models/low_poly_tung.CREDITS.txt" to "low_poly_tung.CREDITS.txt",
        "player_models/tung_tung_sahur.glb" to "tung_tung_sahur.glb",
        "textures/entity/player_model/tung_tung_sahur.png" to "tung_tung_sahur.png",
        "player_models/tung_tung_sahur.CREDITS.txt" to "tung_tung_sahur.CREDITS.txt",
        "player_models/jenny_dressed.json" to "jenny_dressed.json",
        "textures/entity/player_model/jenny_dressed.png" to "jenny_dressed.png",
    )

    /** Flat white fallback, used when a model has no sidecar PNG next to it. */
    private val FALLBACK_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png")

    /** config/floydaddons/models, resolved on demand so the pure helpers stay testable. */
    val modelDir: Path get() = FloydAddonsMod.configFile.toPath().resolve("models")

    private var seeded = false
    private var cache: List<ModelFileEntry>? = null
    private var cacheAt = 0L
    private const val CACHE_MS = 5_000L
    private val textures = HashMap<String, Identifier>()
    private val models = HashMap<String, ImportedFileModel?>()
    private val failures = HashMap<String, String>()

    fun ensureSeeded() {
        if (seeded) return
        seeded = true
        runCatching {
            Files.createDirectories(modelDir)
            for ((source, fileName) in bundledFiles) {
                val target = modelDir.resolve(fileName)
                if (Files.exists(target)) continue
                val resource = "/assets/${FloydAddonsMod.MOD_ID}/$source"
                FloydModelFiles::class.java.getResourceAsStream(resource)?.use { input ->
                    Files.copy(input, target)
                }
            }
        }
    }

    fun availableFiles(): List<ModelFileEntry> {
        ensureSeeded()
        val now = System.currentTimeMillis()
        val cached = cache
        if (cached != null && now - cacheAt < CACHE_MS) return cached
        val listed = list(modelDir)
        cache = listed
        cacheAt = now
        return listed
    }

    internal fun list(directory: Path): List<ModelFileEntry> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter { it.isRegularFile() && ModelFileFormat.of(it.fileName.toString()) != ModelFileFormat.UNSUPPORTED }
                .map { ModelFileEntry(it.fileName.toString().substringBeforeLast('.'), it.fileName.toString(), it, ModelFileFormat.of(it.fileName.toString())) }
                .sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
                .toList()
        }
    }

    fun find(name: String): ModelFileEntry? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        ensureSeeded()
        val files = availableFiles()
        return files.firstOrNull { it.fileName.equals(trimmed, ignoreCase = true) }
            ?: files.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    }

    fun openFolder(): Boolean = openDirectory(modelDir)

    fun reload() {
        cache = null
        cacheAt = 0L
        textures.clear()
        models.clear()
        failures.clear()
    }

    /** Parses (and caches) the selected file, or reports why it could not be used. */
    fun modelFor(name: String): ImportedFileModel? {
        val entry = find(name) ?: return null
        val key = "${entry.fileName}|${entry.path.fileSize()}|${entry.path.getLastModifiedTime().toMillis()}"
        if (models.containsKey(key)) return models[key]
        val loaded = runCatching {
            ImportedFileModel.load(entry.path, textureFor(entry), entry.format)
        }.getOrElse { failure ->
            failures[entry.fileName] = failure.message ?: failure.javaClass.simpleName
            null
        }
        models[key] = loaded
        if (loaded != null) failures.remove(entry.fileName)
        return loaded
    }

    fun failureFor(name: String): String? = find(name)?.let { failures[it.fileName] }

    private fun textureFor(entry: ModelFileEntry): Identifier {
        val path = entry.texturePath
        if (path == null || !path.isRegularFile()) return FALLBACK_TEXTURE
        val key = path.toString()
        textures[key]?.let { return it }
        val identifier = Identifier.fromNamespaceAndPath(
            FloydAddonsMod.MOD_ID,
            "model/" + path.fileName.toString().substringBeforeLast('.').lowercase()
                .map { if (it.isLetterOrDigit() || it == '_' || it == '-' || it == '.') it else '_' }
                .joinToString("")
        )
        return runCatching {
            val image = path.inputStream().use { stream -> NativeImage.read(stream) }
            mc.textureManager.register(identifier, DynamicTexture({ "floydaddons_model" }, image))
            textures[key] = identifier
            identifier
        }.getOrDefault(FALLBACK_TEXTURE)
    }
}
