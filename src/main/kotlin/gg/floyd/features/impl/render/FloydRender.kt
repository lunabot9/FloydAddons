package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVidMode
import java.nio.file.Files
import java.nio.file.Path

object FloydRender : Module(
    name = "General",
    category = Category.RENDER,
    description = "Floyd render settings: custom time, scoreboard, window styling, and player visuals.",
    toggled = true,
) {
    var borderlessWindowed by BooleanSetting("Borderless Window", false, desc = "Matches Floyd's borderless window toggle.")
    val windowTitle by StringSetting("Instance Title", "", 64, desc = "Custom taskbar/window title.")
    val globalCustomFont by BooleanSetting("Global Custom Font", true, desc = "Overrides the vanilla game font with Floyd's bundled font. Reload resources (F3+T) to apply.")
    val customFontFile by StringSetting("Custom Font File", "", 128, desc = "Optional .ttf in the Floyd config dir to use instead of the bundled font. Reload resources (F3+T) to apply.")

    private var lastAppliedTitle: String? = null
    private var lastAppliedBorderless = false
    private var savedWindowedX = -1
    private var savedWindowedY = -1
    private var savedWindowedWidth = 1280
    private var savedWindowedHeight = 720

    init {
        on<TickEvent.ClientEnd> {
            applyWindowTitle()
            ensureBorderlessState()
        }
    }

    /**
     * Facade kept on [FloydRender] so existing callsites compile; the Full Chat Chroma toggle now
     * lives on [FloydPanelStyle], so the chat gate reads it from there.
     */
    @JvmStatic
    fun shouldUseFullChatChroma(): Boolean = FloydPanelStyle.shouldUseFullChatChroma()

    /** Whether the bundled font should override the vanilla game font. OFF renders with the vanilla font. */
    @JvmStatic
    fun isGlobalCustomFontEnabled(): Boolean = enabled && globalCustomFont

    /**
     * Resolved path to a user-supplied .ttf inside the Floyd config dir, or null when unset/invalid.
     * Crash-safe: any resolution failure returns null so the caller falls back to the bundled font.
     */
    @JvmStatic
    fun customFontPath(): Path? {
        val name = customFontFile.trim()
        if (name.isEmpty()) return null
        return try {
            val path = FloydAddonsMod.configFile.toPath().resolve(name).normalize()
            if (Files.isRegularFile(path) && Files.isReadable(path)) path else null
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        // Full Chat Chroma moved to FloydPanelStyle; these keys mirror it so existing readers/tests
        // that probe FloydRender's chat gate keep working.
        "fullChatChroma" to FloydPanelStyle.fullChatChroma,
        "shouldUseFullChatChroma" to shouldUseFullChatChroma(),
        "globalCustomFont" to globalCustomFont,
        "isGlobalCustomFontEnabled" to isGlobalCustomFontEnabled(),
        "customFontFile" to customFontFile,
        "borderlessWindowed" to borderlessWindowed,
        "windowTitle" to windowTitle,
        "effectiveWindowTitle" to windowTitle.trim().ifEmpty { "Minecraft" }
    )

    private fun applyWindowTitle() {
        val target = windowTitle.trim().ifEmpty { "Minecraft" }
        if (lastAppliedTitle == target) return
        mc.window.setTitle(target)
        lastAppliedTitle = target
    }

    private fun ensureBorderlessState() {
        val window = mc.window
        if (window.isFullscreen) return

        if (borderlessWindowed) {
            if (lastAppliedBorderless && isBorderlessApplied()) return
            snapshotWindowedBounds()
            applyBorderless()
            lastAppliedBorderless = true
        } else if (lastAppliedBorderless || !isWindowDecorated()) {
            restoreWindowed()
            lastAppliedBorderless = false
        } else {
            snapshotWindowedBounds()
        }
    }

    @JvmStatic
    fun setBorderlessWindowed(enabled: Boolean, force: Boolean = false) {
        borderlessWindowed = enabled
        if (force) {
            lastAppliedBorderless = false
            ensureBorderlessState()
        }
    }

    private fun snapshotWindowedBounds() {
        val window = mc.window
        if (window.isFullscreen || !isWindowDecorated()) return
        savedWindowedX = window.x
        savedWindowedY = window.y
        savedWindowedWidth = window.width.coerceAtLeast(640)
        savedWindowedHeight = window.height.coerceAtLeast(480)
    }

    private fun applyBorderless() {
        val window = mc.window
        val handle = window.handle()
        val monitor = GLFW.glfwGetPrimaryMonitor()
        val mode = monitor.takeIf { it != 0L }?.let(GLFW::glfwGetVideoMode)

        val targetX: Int
        val targetY: Int
        if (monitor != 0L) {
            val mx = IntArray(1)
            val my = IntArray(1)
            GLFW.glfwGetMonitorPos(monitor, mx, my)
            targetX = mx[0]
            targetY = my[0]
        } else {
            targetX = 0
            targetY = 0
        }

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE)
        GLFW.glfwSetWindowMonitor(
            handle,
            0L,
            targetX,
            targetY,
            mode?.width() ?: savedWindowedWidth,
            mode?.height() ?: savedWindowedHeight,
            GLFW.GLFW_DONT_CARE
        )
    }

    private fun restoreWindowed() {
        val handle = mc.window.handle()
        val mode = GLFW.glfwGetPrimaryMonitor().takeIf { it != 0L }?.let(GLFW::glfwGetVideoMode)
        val maxW = (mode?.width() ?: 1920) - 64
        val maxH = (mode?.height() ?: 1080) - 64
        val targetW = savedWindowedWidth.coerceIn(640, maxW.coerceAtLeast(640))
        val targetH = savedWindowedHeight.coerceIn(480, maxH.coerceAtLeast(480))
        val targetX = savedWindowedX.takeIf { it >= 0 } ?: 50
        val targetY = savedWindowedY.takeIf { it >= 0 } ?: 50

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE)
        GLFW.glfwSetWindowMonitor(handle, 0L, targetX, targetY, targetW, targetH, GLFW.GLFW_DONT_CARE)
    }

    private fun isWindowDecorated(): Boolean =
        GLFW.glfwGetWindowAttrib(mc.window.handle(), GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE

    private fun isBorderlessApplied(): Boolean {
        if (isWindowDecorated()) return false
        val monitor = GLFW.glfwGetPrimaryMonitor()
        val mode: GLFWVidMode = monitor.takeIf { it != 0L }?.let(GLFW::glfwGetVideoMode) ?: return true

        val wx = IntArray(1)
        val wy = IntArray(1)
        val ww = IntArray(1)
        val wh = IntArray(1)
        val mx = IntArray(1)
        val my = IntArray(1)
        GLFW.glfwGetWindowPos(mc.window.handle(), wx, wy)
        GLFW.glfwGetWindowSize(mc.window.handle(), ww, wh)
        GLFW.glfwGetMonitorPos(monitor, mx, my)

        return wx[0] == mx[0] && wy[0] == my[0] && ww[0] == mode.width() && wh[0] == mode.height()
    }
}
