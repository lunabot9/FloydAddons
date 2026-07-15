package gg.floyd.clickgui.settings.impl

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudManager
import gg.floyd.utils.Colors
import gg.floyd.utils.render.hollowFill
import net.minecraft.client.gui.*

open class HudElement(
    var x: Int,
    var y: Int,
    var scale: Float,
    var enabled: Boolean = true,
    val render: GuiGraphics.(Boolean) -> Pair<Int, Int> = { _ -> 0 to 0 }
) {
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private var lastDrawX: Int = x
    private var lastDrawY: Int = y

    val renderedX: Float get() = lastDrawX.toFloat()
    val renderedY: Float get() = lastDrawY.toFloat()
    val renderedWidth: Float get() = width * scale
    val renderedHeight: Float get() = height * scale

    fun draw(context: GuiGraphics, example: Boolean) {
        // A fullscreen toggle briefly reports one or more intermediate framebuffer sizes. Keep the
        // user's persisted position intact and clamp only this frame's draw position; otherwise that
        // transient small size permanently overwrites x/y and the HUD cannot return to its original
        // fullscreen location.
        lastDrawX = visibleX((width * scale).coerceAtLeast(0f)).toInt()
        lastDrawY = visibleY((height * scale).coerceAtLeast(0f)).toInt()
        context.pose().pushMatrix()
        context.pose().translate(lastDrawX.toFloat(), lastDrawY.toFloat())

        context.pose().scale(scale, scale)
        val (width, height) = context.render(example).let { (w, h) -> w to h }

        context.pose().popMatrix()
        if (example) context.hollowFill(lastDrawX - 1, lastDrawY - 1, (width * scale).toInt(), (height * scale).toInt(), if (isHovered()) 2 else 1, Colors.WHITE)

        this.width = width
        this.height = height

    }

    fun visibleX(renderedWidth: Float): Float = visibleHudCoordinate(x.toFloat(), mc.window.width.toFloat(), renderedWidth)

    fun visibleY(renderedHeight: Float): Float = visibleHudCoordinate(y.toFloat(), mc.window.height.toFloat(), renderedHeight)

    fun isHovered(): Boolean {
        // HUD elements are positioned/rendered in framebuffer-pixel space (the in-game and
        // editor render both draw under pose().scale(1/guiScale) from the default gui-scaled
        // pose, which collapses one HUD unit onto one framebuffer pixel). Hit-test the mouse
        // in that exact same space so render and hit-test can never diverge across guiScale or
        // devicePixelRatio. See HudManager.mouseX/mouseY.
        val mx = HudManager.renderSpaceMouseX()
        val my = HudManager.renderSpaceMouseY()
        return mx in lastDrawX.toFloat()..(lastDrawX + width * scale) && my in lastDrawY.toFloat()..(lastDrawY + height * scale)
    }

    internal fun resizeCornerAt(mouseX: Float, mouseY: Float, radius: Float = RESIZE_HANDLE_RADIUS): HudResizeCorner? {
        if (width <= 0 || height <= 0) return null
        val left = renderedX
        val top = renderedY
        val right = left + renderedWidth
        val bottom = top + renderedHeight
        return HudResizeCorner.entries.firstOrNull { corner ->
            val cornerX = if (corner.isRight) right else left
            val cornerY = if (corner.isBottom) bottom else top
            mouseX in (cornerX - radius)..(cornerX + radius) &&
                mouseY in (cornerY - radius)..(cornerY + radius)
        }
    }

    companion object {
        const val RESIZE_HANDLE_RADIUS = 7f
    }
}

internal enum class HudResizeCorner(val isRight: Boolean, val isBottom: Boolean) {
    TOP_LEFT(false, false),
    TOP_RIGHT(true, false),
    BOTTOM_LEFT(false, true),
    BOTTOM_RIGHT(true, true),
}

internal data class HudResizeResult(val x: Float, val y: Float, val scale: Float)

/** Uniformly resizes a HUD while keeping the corner opposite [corner] fixed. */
internal fun resizeHudFromCorner(
    startX: Float,
    startY: Float,
    width: Float,
    height: Float,
    startScale: Float,
    corner: HudResizeCorner,
    mouseDeltaX: Float,
    mouseDeltaY: Float,
    minScale: Float = 0.5f,
    maxScale: Float = 10f,
): HudResizeResult {
    if (width <= 0f || height <= 0f) return HudResizeResult(startX, startY, startScale)

    val directionX = if (corner.isRight) width else -width
    val directionY = if (corner.isBottom) height else -height
    val projection = (mouseDeltaX * directionX + mouseDeltaY * directionY) /
        (width * width + height * height)
    val scale = (startScale + projection).coerceIn(minScale, maxScale)
    val startRight = startX + width * startScale
    val startBottom = startY + height * startScale
    val x = if (corner.isRight) startX else startRight - width * scale
    val y = if (corner.isBottom) startY else startBottom - height * scale
    return HudResizeResult(x, y, scale)
}

internal fun visibleHudCoordinate(stored: Float, viewportSize: Float, renderedSize: Float): Float =
    stored.coerceIn(0f, (viewportSize - renderedSize).coerceAtLeast(0f))
