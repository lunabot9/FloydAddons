package gg.floyd.features.impl.misc

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import gg.floyd.FloydAddonsMod
import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.jcodec.api.awt.AWTFrameGrab
import org.jcodec.common.io.NIOUtils
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.awt.Graphics2D
import java.io.File
import javax.imageio.ImageIO
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension
import kotlin.math.max
import kotlin.math.min

object FloydMenuVideoBackground {
    private val textureId = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "menu/background")
    private val decodeRunning = AtomicBoolean(false)
    private val configuredPath = AtomicReference<Path?>()
    private var texture: LinearMenuTexture? = null
    private var imageFallback: NativeImage? = null
    private var lastRenderMs = 0L
    private var frameSet: VideoFrameSet? = null
    private var uploadedFrameIndex = -1

    @JvmStatic
    fun render(context: GuiGraphics, partialTick: Float) {
        val path = resolveMediaPath() ?: return
        lastRenderMs = System.currentTimeMillis()
        ensureLoaded(path)
        uploadFrameForTime()
        if (texture == null) return
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            0,
            0,
            0.0f,
            0.0f,
            context.guiWidth(),
            context.guiHeight(),
            context.guiWidth(),
            context.guiHeight()
        )
    }

    @JvmStatic
    fun hasMedia(): Boolean = resolveMediaPath() != null

    @JvmStatic
    fun tick() {
        if (!decodeRunning.get()) return
        val currentScreen = FloydAddonsMod.mc.screen
        val menuVisible = currentScreen is FloydMainMenuScreen || (currentScreen != null && FloydMenuScreenStyling.shouldReplaceBackground(currentScreen))
        if (menuVisible) return
        if (System.currentTimeMillis() - lastRenderMs < 250L) return
        reset()
    }

    private fun resolveMediaPath(): Path? {
        val configured = FloydCustomMainMenu.configuredMediaPath()
        if (configured != null && Files.isRegularFile(configured)) return configured
        val configDir = FloydAddonsMod.configFile.toPath()
        val mp4 = configDir.resolve("mainmenu.mp4")
        if (Files.isRegularFile(mp4)) return mp4
        val png = configDir.resolve("mainmenu.png")
        if (Files.isRegularFile(png)) return png
        return null
    }

    private fun ensureLoaded(path: Path) {
        if (path == configuredPath.get() && (texture != null || decodeRunning.get())) return
        reset()
        configuredPath.set(path)
        when {
            path.extension.equals("zip", true) -> startZipSequenceDecode(path)
            path.extension.equals("mp4", true) -> startMp4FrameCacheBuild(path)
            else -> loadStillImage(path)
        }
    }

    private fun reset() {
        decodeRunning.set(false)
        frameSet?.close()
        frameSet = null
        imageFallback?.close()
        imageFallback = null
        texture = null
        configuredPath.set(null)
        uploadedFrameIndex = -1
    }

    private fun startMp4FrameCacheBuild(path: Path) {
        decodeRunning.set(true)
        FloydAddonsMod.scope.launch {
            runCatching {
                val cacheDir = mp4CacheDirectory(path)
                Files.createDirectories(cacheDir)
                if (!directoryHasFrames(cacheDir)) {
                    NIOUtils.readableChannel(path.toFile()).use { channel ->
                        val grab = AWTFrameGrab.createAWTFrameGrab(channel)
                        var sampledFrames = 0
                        var writtenFrames = 0
                        while (decodeRunning.get()) {
                            val frame = runCatching { grab.getFrame() }.getOrNull() ?: break
                            if (sampledFrames++ % 2 != 0) continue
                            val scaled = scaleFrame(frame)
                            val output = cacheDir.resolve("frame_${(writtenFrames + 1).toString().padStart(3, '0')}.jpg").toFile()
                            ImageIO.write(toJpegCompatible(scaled), "jpg", output)
                            writtenFrames++
                            if (writtenFrames >= 180) break
                        }
                    }
                }
                loadFrameSetFromDirectory(cacheDir, 67L)
            }.onFailure {
                FloydAddonsMod.logger.warn("[FloydMenuVideoBackground] Failed to build menu JPG frames from video: $path", it)
                if (texture == null) loadStillFallback(path)
            }
            decodeRunning.set(false)
        }
    }

    private fun startZipSequenceDecode(path: Path) {
        decodeRunning.set(true)
        FloydAddonsMod.scope.launch {
            runCatching {
                ZipFile(path.toFile()).use { zip ->
                    val frameEntries = zip.entries().asSequence()
                        .filter { !it.isDirectory }
                        .filter { entry ->
                            val name = entry.name.lowercase()
                            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                        }
                        .sortedBy { it.name }
                        .take(180)
                        .toList()

                    val cachedFrames = ArrayList<NativeImage>(frameEntries.size)
                    for (entry in frameEntries) {
                        if (!decodeRunning.get()) break
                        zip.getInputStream(entry).use { input ->
                            readFrameImage(input, entry.name)?.also(cachedFrames::add)
                        }
                    }
                    if (cachedFrames.isNotEmpty()) {
                        frameSet = VideoFrameSet(cachedFrames, 67L)
                    } else {
                        loadStillFallback(path)
                    }
                }
            }.onFailure {
                FloydAddonsMod.logger.warn("[FloydMenuVideoBackground] Failed to load menu frame zip: $path", it)
                if (texture == null) loadStillFallback(path)
            }
            decodeRunning.set(false)
        }
    }

    private fun loadFrameSetFromDirectory(path: Path, frameDurationMs: Long) {
        val framePaths = Files.list(path).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter {
                    val name = it.fileName.toString().lowercase()
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                }
                .sorted()
                .limit(180)
                .toList()
        }
        val cachedFrames = ArrayList<NativeImage>(framePaths.size)
        for (framePath in framePaths) {
            framePath.inputStream().use { input ->
                readFrameImage(input, framePath.fileName.toString())?.also(cachedFrames::add)
            }
        }
        if (cachedFrames.isNotEmpty()) frameSet = VideoFrameSet(cachedFrames, frameDurationMs)
    }

    private fun loadStillFallback(path: Path) {
        val siblingPng = path.resolveSibling(path.fileName.toString().substringBeforeLast('.') + ".png")
        if (Files.isRegularFile(siblingPng)) loadStillImage(siblingPng)
    }

    private fun loadStillImage(path: Path) {
        runCatching {
            path.inputStream().use(NativeImage::read).also { image ->
                imageFallback = image
                val dynamicTexture = LinearMenuTexture({ "floydaddons_menu_background" }, image)
                texture = dynamicTexture
                FloydAddonsMod.mc.textureManager.register(textureId, dynamicTexture)
            }
        }.onFailure {
            FloydAddonsMod.logger.warn("[FloydMenuVideoBackground] Failed to load menu background image: $path", it)
        }
    }

    private fun uploadFrameForTime() {
        val set = frameSet ?: return
        val frameCount = set.frames.size
        if (frameCount == 0) return
        val elapsedMs = (System.currentTimeMillis() % set.loopDurationMs).coerceAtLeast(0L)
        val frameIndex = min(frameCount - 1, (elapsedMs / set.frameDurationMs).toInt())
        if (frameIndex == uploadedFrameIndex && texture != null) return
        val frame = set.frames[frameIndex]
        if (texture == null) {
            val dynamicTexture = LinearMenuTexture({ "floydaddons_menu_background" }, NativeImage(frame.format(), frame.width, frame.height, false).also { it.copyFrom(frame) })
            texture = dynamicTexture
            FloydAddonsMod.mc.textureManager.register(textureId, dynamicTexture)
        } else {
            texture?.pixels?.copyFrom(frame)
            texture?.upload()
        }
        uploadedFrameIndex = frameIndex
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

    private fun readFrameImage(input: java.io.InputStream, name: String): NativeImage? {
        val lower = name.lowercase()
        return if (lower.endsWith(".png")) {
            input.use(NativeImage::read)?.let { frame ->
                scaleNativeImage(frame).also { frame.close() }
            }
        } else {
            val buffered = ImageIO.read(input) ?: return null
            bufferedToNativeImage(scaleFrame(buffered))
        }
    }

    private fun scaleNativeImage(image: NativeImage): NativeImage {
        val scaled = scaleFrame(nativeImageToBuffered(image))
        return bufferedToNativeImage(scaled)
    }

    private fun nativeImageToBuffered(image: NativeImage): BufferedImage {
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                buffered.setRGB(x, y, image.getPixel(x, y))
            }
        }
        return buffered
    }

    private fun toJpegCompatible(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_RGB) return image
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics: Graphics2D = rgb.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return rgb
    }

    private fun directoryHasFrames(path: Path): Boolean =
        Files.list(path).use { stream ->
            stream.anyMatch {
                val name = it.fileName.toString().lowercase()
                Files.isRegularFile(it) && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))
            }
        }

    private fun mp4CacheDirectory(path: Path): Path =
        FloydAddonsMod.configFile.toPath()
            .resolve("menu-frame-cache")
            .resolve(path.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    private fun scaleFrame(image: BufferedImage): BufferedImage {
        val maxWidth = 960
        val maxHeight = 540
        if (image.width <= maxWidth && image.height <= maxHeight) return image
        val scale = min(maxWidth / image.width.toFloat(), maxHeight / image.height.toFloat())
        val width = max(1, (image.width * scale).toInt())
        val height = max(1, (image.height * scale).toInt())
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics: Graphics2D = scaled.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(image, 0, 0, width, height, null)
        graphics.dispose()
        return scaled
    }

    private class VideoFrameSet(
        val frames: List<NativeImage>,
        val frameDurationMs: Long
    ) {
        val loopDurationMs: Long = (frames.size * frameDurationMs).coerceAtLeast(frameDurationMs)

        fun close() {
            frames.forEach(NativeImage::close)
        }
    }

    private class LinearMenuTexture(label: () -> String, image: NativeImage) : DynamicTexture(label, image) {
        init {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        }
    }
}
