package com.odtheking.odin.features.impl.misc

import com.mojang.blaze3d.platform.NativeImage
import com.odtheking.odin.FloydAddonsMod
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
        if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
            applied = true
            return
        }

        val window = FloydAddonsMod.mc.window
        val loaded = iconIds.mapNotNull(::loadIcon)
        if (loaded.isEmpty()) return

        applied = true
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
