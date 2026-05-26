package floydaddons.not.dogshit.client.clickgui.settings.impl

import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.utils.Colors
import floydaddons.not.dogshit.client.utils.render.hollowFill
import floydaddons.not.dogshit.client.utils.ui.isAreaHovered
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt

open class HudElement(
    var x: Int,
    var y: Int,
    var scale: Float,
    var enabled: Boolean = true,
    val anchorRight: Boolean = false,
    val render: GuiGraphics.(Boolean) -> Pair<Int, Int> = { _ -> 0 to 0 }
) {
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var screenX: Int = x
        private set
    var screenY: Int = y
        private set
    private var lastScreenWidth: Int = -1
    private var lastScreenHeight: Int = -1

    fun copy(): HudElement = HudElement(x, y, scale, enabled, anchorRight, render).also {
        it.width = width
        it.height = height
        it.screenX = screenX
        it.screenY = screenY
        it.lastScreenWidth = lastScreenWidth
        it.lastScreenHeight = lastScreenHeight
    }

    fun draw(context: GuiGraphics, example: Boolean) {
        val previousWidth = width
        val screenWidth = mc.window.screenWidth
        val screenHeight = mc.window.screenHeight
        screenX = x
        screenY = y
        if (screenWidth > 0 && screenHeight > 0 && width > 0 && height > 0 && lastScreenWidth > 0 && lastScreenHeight > 0) {
            if (screenWidth != lastScreenWidth || screenHeight != lastScreenHeight) {
                preserveScreenAnchor(screenWidth)
            }
            clampToScreen(screenWidth, screenHeight, width, height)
        }

        context.pose().pushMatrix()
        context.pose().translate(screenX.toFloat(), screenY.toFloat())

        context.pose().scale(scale, scale)
        val (width, height) = context.render(example).let { (w, h) -> w to h }

        context.pose().popMatrix()
        if (anchorRight && previousWidth > 0 && previousWidth != width) {
            screenX += ((previousWidth - width) * scale).roundToInt()
        }
        clampToScreen(screenWidth, screenHeight, width, height)
        lastScreenWidth = screenWidth
        lastScreenHeight = screenHeight
        if (example) context.hollowFill(screenX - 1, screenY - 1, (width * scale).toInt(), (height * scale).toInt(), if (isHovered()) 2 else 1, Colors.WHITE)

        this.width = width
        this.height = height
    }

    private fun preserveScreenAnchor(screenWidth: Int) {
        val scaledWidth = (width * scale).roundToInt()
        val preserveRight = anchorRight || x + scaledWidth / 2 >= lastScreenWidth / 2

        screenX = if (preserveRight) {
            val rightMargin = (lastScreenWidth - (x + scaledWidth)).coerceAtLeast(0)
            screenWidth - scaledWidth - rightMargin
        } else {
            x
        }
        screenY = y
    }

    private fun clampToScreen(screenWidth: Int, screenHeight: Int, width: Int = this.width, height: Int = this.height) {
        screenX = screenX.coerceIn(0, (screenWidth - (width * scale).roundToInt()).coerceAtLeast(0))
        screenY = screenY.coerceIn(0, (screenHeight - (height * scale).roundToInt()).coerceAtLeast(0))
    }

    fun isHovered(): Boolean = isAreaHovered(screenX.toFloat(), screenY.toFloat(), width * scale, height * scale)
}
