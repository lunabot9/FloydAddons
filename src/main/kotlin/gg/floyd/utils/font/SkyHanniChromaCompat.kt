package gg.floyd.utils.font

import java.awt.Color.HSBtoRGB
import kotlin.math.max

/**
 * CPU fallback for SkyHanni's named `chroma` text color when Floyd's MSDF glyphs are active.
 *
 * SkyHanni normally turns the marker white and swaps vanilla BakedSheetGlyph render types to its
 * chroma shader. Floyd's [FloydMsdfRenderable] is intentionally not a BakedSheetGlyph instance,
 * so that render-type hook cannot see it. Resolve the marker to a moving vertex color here rather
 * than leaving SkyHanni's white sentinel on screen.
 */
internal object SkyHanniChromaCompat {
    private const val MARKER_NAME = "chroma"
    private const val DEFAULT_SIZE_PERCENT = 30f
    private const val DEFAULT_SPEED = 6f
    private const val DEFAULT_SATURATION = 0.75f

    fun isMarker(colorName: String?): Boolean = colorName == MARKER_NAME

    fun resolveArgb(
        colorName: String?,
        fallbackArgb: Int,
        glyphX: Float,
        glyphY: Float,
        displayWidth: Int,
        ticks: Float,
    ): Int {
        if (!isMarker(colorName)) return fallbackArgb

        val chromaSize = max(1f, displayWidth * DEFAULT_SIZE_PERCENT / 100f)
        val phase = ((glyphX - glyphY) / chromaSize) - (ticks * DEFAULT_SPEED / 360f)
        val hue = ((phase % 1f) + 1f) % 1f
        val alpha = fallbackArgb and 0xFF000000.toInt()
        val rgb = HSBtoRGB(hue, DEFAULT_SATURATION, 1f) and 0x00FFFFFF
        return alpha or rgb
    }
}
