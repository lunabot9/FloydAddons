package gg.floyd.features.impl.misc

import com.mojang.blaze3d.platform.NativeImage
import gg.floyd.FloydAddonsMod
import gg.floyd.utils.FloydPlatform
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.macosx.ObjCRuntime
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
        // macOS: GLFW window icons are a documented no-op (the dock icon is used instead), and
        // java.awt.Taskbar is ALSO a dead end — MC boots headless and AWT refuses to start under
        // -XstartOnFirstThread, so Taskbar always reports unsupported. The dock icon goes through
        // Cocoa directly instead. Everything else uses the GLFW window-icon path.
        if (FloydPlatform.isMac) {
            applyDockIconViaCocoa()
        } else {
            applyWindowIconViaGlfw()
        }
    }

    /**
     * Sets the dock tile icon by handing the 128px PNG to `NSApplication.applicationIconImage`
     * through LWJGL's Objective-C bridge. Runs via [net.minecraft.client.Minecraft.execute] on the
     * render thread, which under -XstartOnFirstThread IS the first/AppKit thread, so the AppKit
     * call is legal. The PNG goes through a temp FILE + `NSImage initWithContentsOfFile:` because
     * every call then fits LWJGL's shipped objc_msgSend invoke overloads (`initWithBytes:length:`
     * would need a 4-arg combo JNI does not generate). Allocation is alloc/init + explicit release
     * (no autorelease pool needed); setApplicationIconImage retains what it keeps. Crash-proofed:
     * any failure logs and leaves the default icon.
     */
    private fun applyDockIconViaCocoa() {
        val resource = FloydAddonsMod.mc.resourceManager.getResource(iconIds.last()).orElse(null) ?: return
        val iconFile = try {
            val png = resource.open().use { it.readBytes() }
            java.nio.file.Files.createTempFile("floydaddons-dock-icon", ".png").also {
                java.nio.file.Files.write(it, png)
                it.toFile().deleteOnExit()
            }
        } catch (_: Throwable) {
            return
        }
        FloydAddonsMod.mc.execute {
            var pathUtf8: ByteBuffer? = null
            try {
                val msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend")
                fun sel(name: String) = ObjCRuntime.sel_getUid(name)
                fun cls(name: String) = ObjCRuntime.objc_getClass(name)
                fun alloc(className: String) = JNI.invokePPP(cls(className), sel("alloc"), msgSend)
                fun release(obj: Long) = JNI.invokePPV(obj, sel("release"), msgSend)

                val nsApp = JNI.invokePPP(cls("NSApplication"), sel("sharedApplication"), msgSend)
                if (nsApp == 0L) {
                    FloydAddonsMod.logger.info("Dock icon skipped: no NSApplication")
                    return@execute
                }
                pathUtf8 = MemoryUtil.memUTF8(iconFile.toAbsolutePath().toString())
                val nsPath = JNI.invokePPPP(
                    alloc("NSString"), sel("initWithUTF8String:"), MemoryUtil.memAddress(pathUtf8), msgSend
                )
                if (nsPath == 0L) {
                    FloydAddonsMod.logger.info("Dock icon skipped: NSString init failed")
                    return@execute
                }
                val nsImage = JNI.invokePPPP(alloc("NSImage"), sel("initWithContentsOfFile:"), nsPath, msgSend)
                if (nsImage != 0L) {
                    JNI.invokePPPV(nsApp, sel("setApplicationIconImage:"), nsImage, msgSend)
                    release(nsImage)
                    FloydAddonsMod.logger.info("Applied dock icon via NSApplication.applicationIconImage")
                } else {
                    FloydAddonsMod.logger.info("Dock icon skipped: NSImage could not read the PNG")
                }
                release(nsPath)
            } catch (t: Throwable) {
                FloydAddonsMod.logger.info("Dock icon via Cocoa unavailable: {}", t.toString())
            } finally {
                pathUtf8?.let(MemoryUtil::memFree)
                runCatching { java.nio.file.Files.deleteIfExists(iconFile) }
            }
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
