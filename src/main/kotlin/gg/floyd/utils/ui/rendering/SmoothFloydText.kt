package gg.floyd.utils.ui.rendering

import gg.floyd.features.impl.render.FloydFont
import net.minecraft.client.gui.GuiGraphics
import org.joml.Vector2f
import kotlin.math.ceil
import kotlin.math.max

/**
 * NanoVG-backed text for Floyd-owned GUI/HUD call sites that still go through Minecraft's
 * GuiGraphics.drawString APIs. This keeps vanilla/global text on Minecraft's Font renderer while
 * letting Floyd panels use the same smooth font path as the column ClickGUI.
 */
object SmoothFloydText {
    private const val FONT_SIZE = 9f
    private const val LINE_HEIGHT = 9f
    private const val PADDING = 4

    @JvmStatic
    fun shouldUseForFloydCaller(): Boolean {
        if (!FloydFont.isGlobalCustomFontEnabled()) return false
        val stack = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { frames ->
            frames.anyMatch { frame ->
                val name = frame.declaringClass.name
                name.startsWith("gg.floyd.") &&
                    name != SmoothFloydText::class.java.name &&
                    !name.startsWith("gg.floyd.mixin.") &&
                    !name.startsWith("gg.floyd.utils.ui.rendering.NVG")
            }
        }
        return stack
    }

    @JvmStatic
    fun drawString(context: GuiGraphics, text: String?, x: Int, y: Int, color: Int, shadow: Boolean): Boolean {
        if (text.isNullOrEmpty()) return true
        if (!shouldUseForFloydCaller()) return false

        val pose = context.pose()
        val p = Vector2f(x.toFloat(), y.toFloat())
        pose.transformPosition(p)
        val sx = pose.m00()
        val sy = pose.m11()
        val scale = max(kotlin.math.abs(sx), kotlin.math.abs(sy)).takeIf { it > 0f } ?: 1f
        val size = FONT_SIZE * scale
        val width = ceil(NVGRenderer.textWidth(text, size, NVGRenderer.defaultFont)).toInt() + PADDING * 2 + if (shadow) 3 else 0
        val height = ceil(LINE_HEIGHT * scale).toInt() + PADDING * 2 + if (shadow) 3 else 0
        val boundsX = kotlin.math.floor(p.x).toInt() - PADDING
        val boundsY = kotlin.math.floor(p.y).toInt() - PADDING
        val localX = PADDING.toFloat()
        val localY = PADDING.toFloat()

        // NVGPIPRenderer renders into the PiP texture's local coordinate space, then Minecraft
        // blits that texture at (boundsX, boundsY). The previous version drew at absolute screen
        // coordinates inside a tiny per-text PiP texture, so most text was clipped away and only
        // near-origin labels survived in the corner.
        NVGPIPRenderer.draw(context, boundsX, boundsY, width, height) {
            if (shadow) NVGRenderer.textShadow(text, localX, localY, size, color, NVGRenderer.defaultFont)
            else NVGRenderer.text(text, localX, localY, size, color, NVGRenderer.defaultFont)
        }
        return true
    }

    @JvmStatic
    fun textWidth(text: String?): Float = if (text.isNullOrEmpty()) 0f else NVGRenderer.textWidth(text, FONT_SIZE, NVGRenderer.defaultFont)
}
