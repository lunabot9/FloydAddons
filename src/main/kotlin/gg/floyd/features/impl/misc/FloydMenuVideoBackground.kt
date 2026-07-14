package gg.floyd.features.impl.misc

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import gg.floyd.FloydAddonsMod
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.client.gui.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.jcodec.api.FrameGrab
import org.jcodec.common.io.NIOUtils
import org.jcodec.scale.AWTUtil
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Renders the configured media (MP4 / frame-sequence zip / still image) as a looping fullscreen
 * background behind Floyd's custom menus and the vanilla default-background screens.
 *
 * Decode model:
 *  - The MP4 is decoded ONCE into an on-disk JPEG frame cache, sampled to [TARGET_FPS] using the
 *    source's real frame rate so playback speed is correct (a fixed every-2nd-frame guess made a
 *    60 fps source play at half speed). The per-frame duration is stored alongside the cache.
 *  - At runtime nothing but the frame *file list* is held; a background coroutine streams the single
 *    currently-visible frame off disk, decodes it into a double-buffered NativeImage, and the render
 *    thread uploads one [DynamicTexture]. Resident memory is ~2 decoded frames, not the whole clip.
 *  - When no menu is on screen the decoder stops and the GPU texture + decoded image are freed, so an
 *    in-game session pays nothing (the OS keeps the JPEG cache warm for an instant re-open).
 */
object FloydMenuVideoBackground {
    private const val TARGET_FPS = 30
    /** ~30 s loop at 30 fps; bounds the on-disk cache. */
    private const val MAX_FRAMES = 900
    /** Decoded/cached frame size; upscaled with LINEAR filtering to fill the screen. */
    private const val MAX_WIDTH = 1280
    private const val MAX_HEIGHT = 720
    private const val JPEG_QUALITY = 0.92f
    /** Bump when decode params change so stale caches are rebuilt. */
    private const val CACHE_VERSION = "v3"
    private const val META_FILE = "meta.txt"
    /** How long after the last [render] before we treat the menu as gone and free GPU state. */
    private const val IDLE_GRACE_MS = 400L

    private val textureId = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "menu/background")

    @Volatile private var loaded: Loaded? = null
    /** Path+mtime key of the media currently loaded OR being loaded; set on the render thread. */
    @Volatile private var currentKey: String? = null

    // decoded double-buffer (guarded by decodeLock)
    private val decodeLock = Any()
    private var readyImage: NativeImage? = null
    private var readyIndex = -1
    private var decoderJob: Job? = null

    // GPU texture (render thread only)
    private var texture: LinearMenuTexture? = null
    private var uploadedIndex = -1

    @Volatile private var lastRenderMs = 0L

    private class Loaded(val frameCount: Int, val frameDurationMs: Long, val loader: (Int) -> ByteArray?) {
        val loopMs: Long = (frameCount * frameDurationMs).coerceAtLeast(frameDurationMs)
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
     * Runs every client tick while the module is enabled. When no replaced-background menu has
     * rendered recently, stop the decoder and free GPU/decoded state. [render] restarts the decoder
     * via [ensureDecoder] when a menu returns.
     */
    @JvmStatic
    fun tick() {
        if (System.currentTimeMillis() - lastRenderMs < IDLE_GRACE_MS) return
        decoderJob?.cancel()
        decoderJob = null
        if (texture == null && readyImage == null) return
        releaseGpuState()
    }

    /**
     * Disable path: stop the decoder and free GPU/decoded state immediately (the per-tick freer is
     * unsubscribed with the module, so it can't do this). Keeps the warm frame-list cache + key so
     * re-enabling resumes from disk in a frame or two instead of re-listing the whole clip.
     */
    @JvmStatic
    fun shutdown() {
        decoderJob?.cancel()
        decoderJob = null
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
        if (currentKey != key) return
        if (result != null && result.frameCount > 0) loaded = result
    }

    private fun loadMp4(path: Path): Loaded {
        val cacheDir = mp4CacheDirectory(path)
        Files.createDirectories(cacheDir)
        val frameDurationMs = if (!directoryHasFrames(cacheDir)) decodeMp4ToCache(path, cacheDir)
        else readCachedFrameDuration(cacheDir)
        val framePaths = listFramePaths(cacheDir)
        // Stream frames off disk so the whole clip never sits in RAM.
        return Loaded(framePaths.size, frameDurationMs) { i -> framePaths.getOrNull(i)?.let(Files::readAllBytes) }
    }

    /** @return the per-frame duration (ms) for correct-speed playback. */
    private fun decodeMp4ToCache(path: Path, cacheDir: Path): Long {
        NIOUtils.readableChannel(path.toFile()).use { channel ->
            val grab = FrameGrab.createFrameGrab(channel)
            val meta = grab.videoTrack.meta
            val sourceFps = if (meta.totalFrames > 0 && meta.totalDuration > 0.0) meta.totalFrames / meta.totalDuration else 30.0
            val sampleEvery = max(1, (sourceFps / TARGET_FPS).roundToInt())
            val frameDurationMs = (1000.0 * sampleEvery / sourceFps).roundToLong().coerceAtLeast(1L)
            var source = 0
            var written = 0
            while (written < MAX_FRAMES) {
                val picture = runCatching { grab.nativeFrame }.getOrNull() ?: break
                if (source++ % sampleEvery != 0) continue
                val jpg = toJpegCompatible(scaleFrame(AWTUtil.toBufferedImage(picture)))
                writeJpeg(jpg, cacheDir.resolve("frame_${(written + 1).toString().padStart(4, '0')}.jpg").toFile())
                written++
            }
            Files.writeString(cacheDir.resolve(META_FILE), frameDurationMs.toString())
            return frameDurationMs
        }
    }

    private fun readCachedFrameDuration(cacheDir: Path): Long =
        runCatching { Files.readString(cacheDir.resolve(META_FILE)).trim().toLong() }
            .getOrDefault((1000L / TARGET_FPS))

    private fun loadZip(path: Path): Loaded {
        val frames = java.util.zip.ZipFile(path.toFile()).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") } }
                .sortedBy { it.name }
                .take(MAX_FRAMES)
                .map { entry -> zip.getInputStream(entry).use { it.readBytes() } }
                .toList()
        }
        return Loaded(frames.size, 1000L / TARGET_FPS) { i -> frames.getOrNull(i) }
    }

    private fun loadStill(path: Path): Loaded {
        val bytes = Files.readAllBytes(path)
        return Loaded(1, 1000L / TARGET_FPS) { bytes }
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
                if (set == null || set.frameCount == 0 || !visible) {
                    lastDecoded = -1
                    delay(120)
                    continue
                }
                val idx = indexForNow(set)
                if (idx != lastDecoded) {
                    val image = runCatching { set.loader(idx)?.let(::decodeFrame) }.getOrNull()
                    if (image != null) {
                        synchronized(decodeLock) {
                            readyImage?.close()
                            readyImage = image
                            readyIndex = idx
                        }
                        lastDecoded = idx
                    }
                }
                if (set.frameCount <= 1) delay(250) else delay(4)
            }
        }
    }

    private fun indexForNow(set: Loaded): Int {
        val elapsed = (System.currentTimeMillis() % set.loopMs).coerceAtLeast(0L)
        return min(set.frameCount - 1, (elapsed / set.frameDurationMs).toInt())
    }

    private fun decodeFrame(bytes: ByteArray): NativeImage =
        runCatching { ByteArrayInputStream(bytes).use(NativeImage::read) }
            .getOrElse { bufferedToNativeImage(ImageIO.read(ByteArrayInputStream(bytes))) }

    /** Render-thread: copy the latest decoded frame into the GPU texture if it changed. */
    private fun uploadCurrentFrame() {
        synchronized(decodeLock) {
            val image = readyImage ?: return
            if (readyIndex == uploadedIndex && texture != null) return
            if (texture != null && (texture?.pixels?.width != image.width || texture?.pixels?.height != image.height)) {
                releaseGpuState()
            }
            val tex = texture ?: createTexture(image.width, image.height).also { texture = it }
            tex.pixels?.copyFrom(image)
            tex.upload()
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
        Files.list(path).use { stream -> stream.anyMatch(::isFrameFile) }

    private fun listFramePaths(cacheDir: Path): List<Path> =
        Files.list(cacheDir).use { stream ->
            stream.filter(::isFrameFile).sorted().limit(MAX_FRAMES.toLong()).toList()
        }

    private fun isFrameFile(p: Path): Boolean =
        Files.isRegularFile(p) &&
            p.fileName.toString().lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") }

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

    private fun writeJpeg(image: BufferedImage, file: java.io.File) {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        try {
            javax.imageio.stream.FileImageOutputStream(file).use { out ->
                writer.output = out
                val params = writer.defaultWriteParam
                if (params.canWriteCompressed()) {
                    params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    params.compressionQuality = JPEG_QUALITY
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
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
