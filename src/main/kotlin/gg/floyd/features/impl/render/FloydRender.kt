package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.impl.misc.FloydWindowModule
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVidMode
import java.nio.file.Path

/**
 * Unregistered backing object for Floyd's window/render runtime helpers.
 *
 * This used to be a single mega-[Module][gg.floyd.features.Module] (`General`/`Render`) that owned
 * every Floyd render setting. Each setting has since been un-nested onto its own top-level module
 * (panel cosmetics + full-chat-chroma -> [FloydPanelStyle], global font -> [FloydFont], the window
 * styling toggles -> [FloydWindowModule]); this object is no longer registered with the
 * [ModuleManager][gg.floyd.features.ModuleManager] and shows nothing in the GUI. It survives only as
 * a facade so existing callsites/tests keep compiling: it holds the window borderless/title runtime
 * state and exposes facade methods that READ the new modules.
 *
 * GL/GLFW is only ever touched from [tickWindowState] (driven by [FloydWindowModule]'s per-tick
 * handler) and from [setBorderlessWindowed] — never from a class init, config load, or a setting
 * value-setter, so it cannot fire before a GL context exists.
 */
object FloydRender {

    /** Whether the window-styling feature is on; reads the new [FloydWindowModule] toggle. */
    @JvmStatic
    val enabled: Boolean get() = FloydWindowModule.enabled

    /** Borderless toggle, now owned by [FloydWindowModule]. */
    var borderlessWindowed: Boolean
        get() = FloydWindowModule.borderlessWindowed
        private set(value) { FloydWindowModule.borderlessWindowed = value }

    /** Custom window/taskbar title, now owned by [FloydWindowModule]. */
    val windowTitle: String get() = FloydWindowModule.windowTitle

    private var lastAppliedTitle: String? = null
    private var lastAppliedBorderless = false
    private var savedWindowedX = -1
    private var savedWindowedY = -1
    private var savedWindowedWidth = 1280
    private var savedWindowedHeight = 720

    /**
     * Per-tick driver invoked from [FloydWindowModule]'s client-tick handler. Safe to touch GLFW
     * here because the client tick only fires once the window/GL context exists.
     */
    @JvmStatic
    fun tickWindowState() {
        applyWindowTitle()
        ensureBorderlessState()
    }

    /**
     * Facade kept on [FloydRender] so existing callsites compile; the Full Chat Chroma toggle now
     * lives on [FloydPanelStyle], so the chat gate reads it from there.
     */
    @JvmStatic
    fun shouldUseFullChatChroma(): Boolean = FloydPanelStyle.shouldUseFullChatChroma()

    /**
     * Facade kept on [FloydRender] so existing callsites compile; the global custom-font toggle now
     * lives on [FloydFont], so the font provider path reads it from there.
     */
    @JvmStatic
    fun isGlobalCustomFontEnabled(): Boolean = FloydFont.isGlobalCustomFontEnabled()

    /**
     * Facade kept on [FloydRender] so existing callsites compile; the selected .ttf now lives on
     * [FloydFont], so the font provider path resolves it from there. Crash-safe: returns null when
     * unset/invalid so the caller falls back to the bundled font.
     */
    @JvmStatic
    fun customFontPath(): Path? = FloydFont.customFontPath()

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        // Full Chat Chroma moved to FloydPanelStyle; these keys mirror it so existing readers/tests
        // that probe FloydRender's chat gate keep working.
        "fullChatChroma" to FloydPanelStyle.fullChatChroma,
        "shouldUseFullChatChroma" to shouldUseFullChatChroma(),
        // Global custom font moved to FloydFont; these keys mirror it so existing readers keep working.
        "globalCustomFont" to FloydFont.globalCustomFont,
        "isGlobalCustomFontEnabled" to isGlobalCustomFontEnabled(),
        "customFontFile" to FloydFont.selectedFont,
        // Window styling moved to FloydWindowModule; these keys mirror it so existing readers keep working.
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
