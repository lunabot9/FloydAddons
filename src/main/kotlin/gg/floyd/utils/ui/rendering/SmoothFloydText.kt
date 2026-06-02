package gg.floyd.utils.ui.rendering

import gg.floyd.features.impl.render.FloydFont
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.ceil

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

        val size = FONT_SIZE
        val width = ceil(NVGRenderer.textWidth(text, size, NVGRenderer.defaultFont)).toInt() + PADDING * 2 + if (shadow) 3 else 0
        val height = ceil(LINE_HEIGHT).toInt() + PADDING * 2 + if (shadow) 3 else 0
        val localX = PADDING.toFloat()
        val localY = PADDING.toFloat()

        // Important: PictureInPictureRenderer applies the captured GuiGraphics pose again when it
        // blits the generated texture. Therefore x/y/width/height must stay in the caller's local
        // GUI coordinate space, and the NVG text inside the texture must be drawn at local PiP
        // coordinates. Pre-transforming x/y or scaling the font here double-applies HUD/GUI scale,
        // which makes text appear tiny/missing or spreads letters across the screen.
        NVGPIPRenderer.draw(context, x - PADDING, y - PADDING, width, height) {
            if (shadow) NVGRenderer.textShadow(text, localX, localY, size, color, NVGRenderer.defaultFont)
            else NVGRenderer.text(text, localX, localY, size, color, NVGRenderer.defaultFont)
        }
        return true
    }

    @JvmStatic
    fun textWidth(text: String?): Float = if (text.isNullOrEmpty()) 0f else NVGRenderer.textWidth(text, FONT_SIZE, NVGRenderer.defaultFont)
}
