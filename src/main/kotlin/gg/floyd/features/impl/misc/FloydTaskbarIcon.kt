package gg.floyd.features.impl.misc

import com.mojang.blaze3d.platform.NativeImage
import gg.floyd.FloydAddonsMod
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

object FloydTaskbarIcon {
    private val iconIds = listOf(
        Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "icons/taskbar_icon_16x16.png"),
        Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "icons/taskbar_icon_32x32.png"),
        Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "icons/taskbar_icon_48x48.png"),
        Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "icons/taskbar_icon_128x128.png"),
    )

    private var applied = false

    fun applyOnce() {
        if (applied || !FloydCompatibility.shouldApplyTaskbarIcon()) return
        applied = true
        // macOS: GLFW window icons are a documented no-op (the dock icon is used instead), so the
        // FloydAddons icon has to go through java.awt.Taskbar. Everything else uses the GLFW path.
        if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
            applyDockIconViaAwt()
        } else {
            applyWindowIconViaGlfw()
        }
    }

    /**
     * Sets the dock icon on macOS via [java.awt.Taskbar]. Runs on its own daemon thread (never the
     * render/main thread, which GLFW owns under -XstartOnFirstThread) and swallows every Throwable so
     * an AWT/headless quirk can never destabilise the client. The image is read with headless-safe
     * ImageIO so loading it touches no display subsystem.
     */
    private fun applyDockIconViaAwt() {
        val image = loadAwtImage() ?: return
        Thread({
            try {
                if (!java.awt.Taskbar.isTaskbarSupported()) return@Thread
                val taskbar = java.awt.Taskbar.getTaskbar()
                if (!taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) return@Thread
                taskbar.iconImage = image
                FloydAddonsMod.logger.info("Applied dock icon via java.awt.Taskbar")
            } catch (t: Throwable) {
                FloydAddonsMod.logger.debug("Dock icon via java.awt.Taskbar unavailable: {}", t.message)
            }
        }, "FloydAddons-DockIcon").apply {
            isDaemon = true
            start()
        }
    }

    /** Reads the largest icon (128x128) as a headless-safe AWT image, or null if unreadable. */
    private fun loadAwtImage(): java.awt.image.BufferedImage? {
        val resource = FloydAddonsMod.mc.resourceManager.getResource(iconIds.last()).orElse(null) ?: return null
        return try {
            resource.open().use { javax.imageio.ImageIO.read(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyWindowIconViaGlfw() {
        val window = FloydAddonsMod.mc.window
        val loaded = iconIds.mapNotNull(::loadIcon)
        if (loaded.isEmpty()) return

        FloydAddonsMod.mc.execute {
            val images = GLFWImage.malloc(loaded.size)
            try {
                loaded.forEachIndexed { index, icon ->
                    images.position(index)
                    images.width(icon.width)
                    images.height(icon.height)
                    images.pixels(icon.pixels)
                }
                images.position(0)
                try {
                    GLFW.glfwSetWindowIcon(window.handle(), images)
                } catch (_: Exception) {
                    // Match Floyd's failure mode: leave the default icon if GLFW rejects the update.
                }
            } finally {
                images.free()
                loaded.forEach { MemoryUtil.memFree(it.pixels) }
            }
        }
    }

    private fun loadIcon(id: Identifier): LoadedIcon? {
        val resource = FloydAddonsMod.mc.resourceManager.getResource(id).orElse(null) ?: return null
        return try {
            resource.open().use { stream ->
                NativeImage.read(stream).use { image ->
                    LoadedIcon(image.width, image.height, image.toRgbaBuffer())
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun NativeImage.toRgbaBuffer(): ByteBuffer {
        val pixels = pixelsABGR
        val buffer = MemoryUtil.memAlloc(pixels.size * 4)
        for (pixel in pixels) {
            buffer.put(pixel.toByte())
            buffer.put((pixel shr 8).toByte())
            buffer.put((pixel shr 16).toByte())
            buffer.put((pixel shr 24).toByte())
        }
        buffer.flip()
        return buffer
    }

    private data class LoadedIcon(val width: Int, val height: Int, val pixels: ByteBuffer)
}
