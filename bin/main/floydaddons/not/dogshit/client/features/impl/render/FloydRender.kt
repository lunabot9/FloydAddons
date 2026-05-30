package floydaddons.not.dogshit.client.features.impl.render

import floydaddons.not.dogshit.client.events.TickEvent
import floydaddons.not.dogshit.client.events.core.on
import floydaddons.not.dogshit.client.features.Module
import floydaddons.not.dogshit.client.clickgui.settings.impl.BooleanSetting
import floydaddons.not.dogshit.client.features.Category
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVidMode

object FloydRender : Module(
    name = "Render",
    category = Category.RENDER,
    description = "Floyd render settings: full chat chroma, borderless fullscreen, and player visuals.",
    toggled = true,
) {
    val fullChatChroma by BooleanSetting("Full Chat Chroma", false, desc = "Cycles all visible chat text through chroma.")
    var borderlessWindowed by BooleanSetting("Borderless Window", false, desc = "Matches Floyd's borderless window toggle.")

    private var lastAppliedBorderless = false
    private var savedWindowedX = -1
    private var savedWindowedY = -1
    private var savedWindowedWidth = 1280
    private var savedWindowedHeight = 720

    init {
        on<TickEvent.ClientEnd> {
            ensureBorderlessState()
        }
    }

    @JvmStatic
    fun shouldUseFullChatChroma(): Boolean = enabled && fullChatChroma

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "fullChatChroma" to fullChatChroma,
        "shouldUseFullChatChroma" to shouldUseFullChatChroma(),
        "borderlessWindowed" to borderlessWindowed
    )

    private fun ensureBorderlessState() {
        val window = mc.window

        if (borderlessWindowed) {
            if (window.isFullscreen) {
                window.toggleFullScreen()
                lastAppliedBorderless = false
                return
            }
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
