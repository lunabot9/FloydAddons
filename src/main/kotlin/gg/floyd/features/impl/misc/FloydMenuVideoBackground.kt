package gg.floyd.features.impl.misc

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import gg.floyd.FloydAddonsMod
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.jcodec.api.awt.AWTFrameGrab
import org.jcodec.common.io.NIOUtils
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.math.max
import kotlin.math.min

/**
 * Renders the configured media (MP4 / frame-sequence zip / still image) as a looping fullscreen
 * background behind Floyd's custom menus and the vanilla default-background screens.
 *
 * Memory model (the perf-critical part — the previous impl held every decoded frame as a NativeImage
 * forever, ~360 MB resident that was never freed once decoded):
 *  - Frames live in RAM only as their COMPRESSED JPEG/PNG bytes (~20 MB for a 40 s loop), decoded
 *    once from the MP4 into an on-disk cache so re-launches skip the slow jcodec pass.
 *  - A single background coroutine JIT-decodes only the *currently visible* frame into a
 *    double-buffered NativeImage; the render thread uploads it to one [DynamicTexture].
 *  - When no menu is on screen the decoder idles, and the GPU texture + decoded NativeImage are
 *    freed ([tick]) so an in-game session pays nothing but the small resident byte cache.
 */
object FloydMenuVideoBackground {
    private const val TARGET_FPS = 15
    private const val FRAME_DURATION_MS = 1000L / TARGET_FPS
    /** Cap the loop length so the resident compressed-byte cache stays bounded (~20-25 MB). */
    private const val MAX_FRAMES = 600
    /** Max decoded frame size; upscaled with LINEAR filtering to fill the screen. */
    private const val MAX_WIDTH = 960
    private const val MAX_HEIGHT = 540
    /** Bump when the decode parameters change so stale on-disk caches are re-decoded. */
    private const val CACHE_VERSION = "v2"
    /** How long after the last [render] before we treat the menu as gone and free GPU state. */
    private const val IDLE_GRACE_MS = 400L

    private val textureId = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "menu/background")

    // --- resident compressed frames (published by the loader coroutine, read everywhere) ---
    @Volatile private var loaded: Loaded? = null
    /** Path+mtime key of the media currently loaded OR being loaded; set on the render thread. */
    @Volatile private var currentKey: String? = null

    // --- decoded double-buffer (guarded by decodeLock) ---
    private val decodeLock = Any()
    private var readyImage: NativeImage? = null
    private var readyIndex = -1
    private var decoderJob: Job? = null

    // --- GPU texture (render thread only) ---
    private var texture: LinearMenuTexture? = null
    private var uploadedIndex = -1

    @Volatile private var lastRenderMs = 0L

    private class Loaded(val frames: List<ByteArray>, val frameDurationMs: Long) {
        val loopMs: Long = (frames.size * frameDurationMs).coerceAtLeast(frameDurationMs)
    }

    /** @return true if a media frame was drawn (caller may fall back to vanilla if false). */
    @JvmStatic
    fun render(context: GuiGraphics): Boolean {
        val path = resolveMediaPath() ?: return false
        lastRenderMs = System.currentTimeMillis()
        ensureLoaded(path)
        ensureDecoder()
        uploadCurrentFrame()
        if (texture == null) return false
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            0, 0,
            0.0f, 0.0f,
            context.guiWidth(), context.guiHeight(),
            context.guiWidth(), context.guiHeight()
        )
        return true
    }

    @JvmStatic
    fun hasMedia(): Boolean = resolveMediaPath() != null

    /**
     * Runs every client tick. When no replaced-background menu has rendered recently, free the GPU
     * texture and the decoded frame (the decoder coroutine idles itself off [lastRenderMs]). The
     * compressed byte cache stays resident so re-opening a menu is instant.
     */
    @JvmStatic
    fun tick() {
        if (System.currentTimeMillis() - lastRenderMs < IDLE_GRACE_MS) return
        // No menu on screen: stop the decoder coroutine and free GPU/decoded state so an in-game
        // session pays nothing. render() restarts the decoder via ensureDecoder when a menu returns.
        decoderJob?.cancel()
        decoderJob = null
        if (texture == null && readyImage == null) return
        releaseGpuState()
    }

    private fun releaseGpuState() {
        runCatching { FloydAddonsMod.mc.textureManager.release(textureId) }
        texture = null
        uploadedIndex = -1
        synchronized(decodeLock) {
            readyImage?.close()
            readyImage = null
            readyIndex = -1
        }
    }

    // ---------------------------------------------------------------- loading ----

    private fun ensureLoaded(path: Path) {
        val key = mediaKey(path)
        if (key == currentKey) return
        currentKey = key
        // New media selected: drop the old resident frames + GPU state and load fresh.
        loaded = null
        releaseGpuState()
        FloydAddonsMod.scope.launch { loadMedia(path, key) }
    }

    private fun loadMedia(path: Path, key: String) {
        val result = runCatching {
            when {
                path.extension.equals("mp4", true) -> loadMp4(path)
                path.extension.equals("zip", true) -> loadZip(path)
                else -> loadStill(path)
            }
        }.getOrElse {
            FloydAddonsMod.logger.warn("[FloydMenuVideoBackground] Failed to load menu media: $path", it)
            null
        }
        // A newer selection superseded us while decoding — discard.
        if (currentKey != key) return
        if (result != null && result.frames.isNotEmpty()) loaded = result
    }

    private fun loadMp4(path: Path): Loaded {
        val cacheDir = mp4CacheDirectory(path)
        Files.createDirectories(cacheDir)
        if (!directoryHasFrames(cacheDir)) decodeMp4ToCache(path, cacheDir)
        return Loaded(readFrameBytes(cacheDir), FRAME_DURATION_MS)
    }

    private fun decodeMp4ToCache(path: Path, cacheDir: Path) {
        NIOUtils.readableChannel(path.toFile()).use { channel ->
            val grab = AWTFrameGrab.createAWTFrameGrab(channel)
            var sampled = 0
            var written = 0
            while (written < MAX_FRAMES) {
                val frame = runCatching { grab.getFrame() }.getOrNull() ?: break
                // jcodec gives ~source-fps frames; keep every other one (~30 fps source -> ~15 fps).
                if (sampled++ % 2 != 0) continue
                val jpg = toJpegCompatible(scaleFrame(frame))
                val out = cacheDir.resolve("frame_${(written + 1).toString().padStart(4, '0')}.jpg").toFile()
                ImageIO.write(jpg, "jpg", out)
                written++
            }
        }
    }

    private fun loadZip(path: Path): Loaded {
        ZipFile(path.toFile()).use { zip ->
            val frames = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") } }
                .sortedBy { it.name }
                .take(MAX_FRAMES)
                .map { entry -> zip.getInputStream(entry).use { it.readBytes() } }
                .toList()
            return Loaded(frames, FRAME_DURATION_MS)
        }
    }

    private fun loadStill(path: Path): Loaded =
        Loaded(listOf(Files.readAllBytes(path)), FRAME_DURATION_MS)

    private fun readFrameBytes(cacheDir: Path): List<ByteArray> =
        Files.list(cacheDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") } }
                .sorted()
                .limit(MAX_FRAMES.toLong())
                .map { Files.readAllBytes(it) }
                .toList()
        }

    // ---------------------------------------------------------------- decoding ----

    private fun ensureDecoder() {
        val job = decoderJob
        if (job != null && job.isActive) return
        decoderJob = FloydAddonsMod.scope.launch {
            var lastDecoded = -1
            while (isActive) {
                val set = loaded
                val visible = System.currentTimeMillis() - lastRenderMs < IDLE_GRACE_MS
                if (set == null || set.frames.isEmpty() || !visible) {
                    lastDecoded = -1
                    delay(120)
                    continue
                }
                val idx = indexForNow(set)
                if (idx != lastDecoded) {
                    val image = runCatching { decodeFrame(set.frames[idx]) }.getOrNull()
                    if (image != null) {
                        synchronized(decodeLock) {
                            readyImage?.close()
                            readyImage = image
                            readyIndex = idx
                        }
                        lastDecoded = idx
                    }
                }
                // Single-frame stills need no further work; otherwise pace to the frame rate.
                if (set.frames.size <= 1) delay(250) else delay(8)
            }
        }
    }

    private fun indexForNow(set: Loaded): Int {
        val elapsed = (System.currentTimeMillis() % set.loopMs).coerceAtLeast(0L)
        return min(set.frames.size - 1, (elapsed / set.frameDurationMs).toInt())
    }

    private fun decodeFrame(bytes: ByteArray): NativeImage =
        runCatching { ByteArrayInputStream(bytes).use(NativeImage::read) }
            .getOrElse { bufferedToNativeImage(ImageIO.read(ByteArrayInputStream(bytes))) }

    /** Render-thread: copy the latest decoded frame into the GPU texture if it changed. */
    private fun uploadCurrentFrame() {
        synchronized(decodeLock) {
            val image = readyImage ?: return
            if (readyIndex == uploadedIndex && texture != null) return
            val tex = texture ?: createTexture(image.width, image.height).also { texture = it }
            if (tex.pixels?.width != image.width || tex.pixels?.height != image.height) {
                releaseGpuState()
                texture = createTexture(image.width, image.height)
            }
            texture?.pixels?.copyFrom(image)
            texture?.upload()
            uploadedIndex = readyIndex
        }
    }

    private fun createTexture(width: Int, height: Int): LinearMenuTexture {
        val tex = LinearMenuTexture({ "floydaddons_menu_background" }, NativeImage(width, height, false))
        FloydAddonsMod.mc.textureManager.register(textureId, tex)
        return tex
    }

    // ---------------------------------------------------------------- media path ----

    private fun resolveMediaPath(): Path? {
        val configured = FloydCustomMainMenu.configuredMediaPath()
        if (configured != null && Files.isRegularFile(configured)) return configured
        val configDir = FloydAddonsMod.configFile.toPath()
        for (name in listOf("mainmenu.mp4", "mainmenu.zip", "mainmenu.png")) {
            val candidate = configDir.resolve(name)
            if (Files.isRegularFile(candidate)) return candidate
        }
        return null
    }

    private fun mediaKey(path: Path): String =
        "$path|${runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)}"

    private fun mp4CacheDirectory(path: Path): Path =
        FloydAddonsMod.configFile.toPath()
            .resolve("menu-frame-cache")
            .resolve("${CACHE_VERSION}_${path.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_")}")

    private fun directoryHasFrames(path: Path): Boolean =
        Files.list(path).use { stream ->
            stream.anyMatch {
                Files.isRegularFile(it) &&
                    it.fileName.toString().lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") }
            }
        }

    // ---------------------------------------------------------------- image utils ----

    private fun scaleFrame(image: BufferedImage): BufferedImage {
        if (image.width <= MAX_WIDTH && image.height <= MAX_HEIGHT) return image
        val scale = min(MAX_WIDTH / image.width.toFloat(), MAX_HEIGHT / image.height.toFloat())
        val width = max(1, (image.width * scale).toInt())
        val height = max(1, (image.height * scale).toInt())
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics: Graphics2D = scaled.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(image, 0, 0, width, height, null)
        graphics.dispose()
        return scaled
    }

    private fun toJpegCompatible(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_RGB) return image
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics: Graphics2D = rgb.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return rgb
    }

    private fun bufferedToNativeImage(image: BufferedImage): NativeImage {
        val native = NativeImage(max(1, image.width), max(1, image.height), false)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                native.setPixel(x, y, image.getRGB(x, y))
            }
        }
        return native
    }

    private class LinearMenuTexture(label: () -> String, image: NativeImage) : DynamicTexture(label, image) {
        init {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        }
    }
}
