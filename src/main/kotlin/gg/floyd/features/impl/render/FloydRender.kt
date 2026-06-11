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
    // A flag, not a >=0 coordinate sentinel: monitors left/above the primary have NEGATIVE screen
    // coordinates (Windows/X11 multi-monitor), which are perfectly valid saved positions.
    private var hasSavedWindowedBounds = false
    private var savedWindowedX = 0
    private var savedWindowedY = 0
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
        // GLFW SCREEN coordinates — NOT mc.window.width/height, which are FRAMEBUFFER pixels and
        // 2x the screen size on retina/HiDPI displays (restoring those doubled the window).
        val handle = window.handle()
        val x = IntArray(1)
        val y = IntArray(1)
        val w = IntArray(1)
        val h = IntArray(1)
        GLFW.glfwGetWindowPos(handle, x, y)
        GLFW.glfwGetWindowSize(handle, w, h)
        if (w[0] <= 0 || h[0] <= 0) return
        savedWindowedX = x[0]
        savedWindowedY = y[0]
        savedWindowedWidth = w[0].coerceAtLeast(640)
        savedWindowedHeight = h[0].coerceAtLeast(480)
        hasSavedWindowedBounds = true
    }

    /**
     * The monitor the window currently overlaps most (windowed-mode geometry, screen coordinates),
     * falling back to the primary. Borderless targets THIS monitor — the old primary-only path
     * yanked a window living on a secondary display across to the primary, and mishandled the
     * negative origins of monitors left/above the primary.
     */
    private fun monitorUnderWindow(handle: Long): Long {
        val primary = GLFW.glfwGetPrimaryMonitor()
        val monitors = GLFW.glfwGetMonitors() ?: return primary
        if (monitors.limit() <= 1) return primary
        val wx = IntArray(1)
        val wy = IntArray(1)
        val ww = IntArray(1)
        val wh = IntArray(1)
        GLFW.glfwGetWindowPos(handle, wx, wy)
        GLFW.glfwGetWindowSize(handle, ww, wh)
        var best = 0L
        var bestArea = 0L
        val mx = IntArray(1)
        val my = IntArray(1)
        for (i in 0 until monitors.limit()) {
            val monitor = monitors.get(i)
            val mode = GLFW.glfwGetVideoMode(monitor) ?: continue
            GLFW.glfwGetMonitorPos(monitor, mx, my)
            val overlapW = (minOf(wx[0] + ww[0], mx[0] + mode.width()) - maxOf(wx[0], mx[0])).coerceAtLeast(0)
            val overlapH = (minOf(wy[0] + wh[0], my[0] + mode.height()) - maxOf(wy[0], my[0])).coerceAtLeast(0)
            val area = overlapW.toLong() * overlapH
            if (area > bestArea) {
                bestArea = area
                best = monitor
            }
        }
        return if (best != 0L) best else primary
    }

    private fun applyBorderless() {
        val window = mc.window
        val handle = window.handle()
        val monitor = monitorUnderWindow(handle)
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

        // A maximized window carries its maximized frame state through attrib/geometry changes on
        // Windows, leaving a mis-sized client area — restore first so the resize lands cleanly.
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
            GLFW.glfwRestoreWindow(handle)
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
        val monitor = monitorUnderWindow(handle)
        val mode = monitor.takeIf { it != 0L }?.let(GLFW::glfwGetVideoMode)
        // Clamp to the FULL monitor size: the saved bounds were a real windowed geometry, so any
        // size that fit before fits now — the old 64px safety margin silently shrank a
        // maximized-fit window (e.g. 1920-wide on a 1920 monitor) on every borderless round-trip.
        val maxW = mode?.width() ?: Int.MAX_VALUE
        val maxH = mode?.height() ?: Int.MAX_VALUE
        val targetW = savedWindowedWidth.coerceIn(640, maxW.coerceAtLeast(640))
        val targetH = savedWindowedHeight.coerceIn(480, maxH.coerceAtLeast(480))
        // No saved bounds -> a spot ON THE CURRENT MONITOR (origin + 50): monitor origins are
        // negative left/above the primary, so a fixed (50,50) could land on another display.
        val mx = IntArray(1)
        val my = IntArray(1)
        if (monitor != 0L) GLFW.glfwGetMonitorPos(monitor, mx, my)
        val targetX = if (hasSavedWindowedBounds) savedWindowedX else mx[0] + 50
        val targetY = if (hasSavedWindowedBounds) savedWindowedY else my[0] + 50

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE)
        GLFW.glfwSetWindowMonitor(handle, 0L, targetX, targetY, targetW, targetH, GLFW.GLFW_DONT_CARE)
    }

    private fun isWindowDecorated(): Boolean =
        GLFW.glfwGetWindowAttrib(mc.window.handle(), GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE

    private fun isBorderlessApplied(): Boolean {
        if (isWindowDecorated()) return false
        val handle = mc.window.handle()
        val monitor = monitorUnderWindow(handle)
        val mode: GLFWVidMode = monitor.takeIf { it != 0L }?.let(GLFW::glfwGetVideoMode) ?: return true

        val wx = IntArray(1)
        val wy = IntArray(1)
        val ww = IntArray(1)
        val wh = IntArray(1)
        val mx = IntArray(1)
        val my = IntArray(1)
        GLFW.glfwGetWindowPos(handle, wx, wy)
        GLFW.glfwGetWindowSize(handle, ww, wh)
        GLFW.glfwGetMonitorPos(monitor, mx, my)

        return wx[0] == mx[0] && wy[0] == my[0] && ww[0] == mode.width() && wh[0] == mode.height()
    }
}
