package gg.floyd.utils.render

import gg.floyd.utils.Color
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shared rendering for Floyd's tinted, rounded HUD panels (scoreboard HUD, inventory HUD,
 * Player-ESP overhead nameplate). Centralises the rounded-rect fill plus the chroma / fade /
 * rotating-gradient border math so every panel looks consistent and the logic lives in one
 * place instead of being duplicated per feature.
 */
object HudPanel {

    /** Per-corner border colors for a panel (top-left, top-right, bottom-right, bottom-left). */
    data class BorderColors(val topLeft: Int, val topRight: Int, val bottomRight: Int, val bottomLeft: Int)

    /** Default panel fill: 25% black tint. */
    const val DEFAULT_FILL = 0x40000000

    /**
     * Draws a tinted rounded panel with a (possibly per-corner) border. Coordinates are in the
     * caller's current gui-pose space, so a scaled pose scales the whole panel (the
     * [RoundRectPIPRenderer] picks up the pose scale for the radius and outline width too).
     */
    fun fillPanel(
        graphics: GuiGraphics,
        x0: Int, y0: Int, x1: Int, y1: Int,
        border: BorderColors,
        fillColor: Int = DEFAULT_FILL,
        cornerRadius: Float = 0f,
        outlineWidth: Float = 2f
    ) {
        val radius = cornerRadius.coerceAtLeast(0f)
        RoundRectPIPRenderer.submit(
            graphics,
            x0, y0, x1, y1,
            fillColor, fillColor, fillColor, fillColor,
            radius, radius, radius, radius,
            border.topLeft, border.topRight, border.bottomRight, border.bottomLeft, outlineWidth
        )
    }

    fun chromaColor(offset: Float): Int {
        val hue = (((System.currentTimeMillis() % 4000) / 4000.0f) + offset) % 1.0f
        return 0xFF000000.toInt() or (java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) and 0x00FFFFFF)
    }

    fun monochrome(color: Int): BorderColors = BorderColors(color, color, color, color)

    /** Rotating gradient around the four corners (chroma/fade/solid per the [base] color settings). */
    fun circularBorderColors(base: Color, fade: Boolean, fadeColor: Color, offset: Float): BorderColors =
        BorderColors(
            accentColor(base, fade, fadeColor, offset),
            accentColor(base, fade, fadeColor, offsetPhase(offset, 0.25f)),
            accentColor(base, fade, fadeColor, offsetPhase(offset, 0.5f)),
            accentColor(base, fade, fadeColor, offsetPhase(offset, 0.75f))
        )

    /** chroma flag lives on the Color (our model); fade blends base<->fadeColor; otherwise the static color. */
    fun accentColor(base: Color, fade: Boolean, fadeColor: Color, offset: Float): Int {
        if (base.chroma) return chromaColor(offset)
        if (fade) return blendColors(base.baseRgba, fadeColor.baseRgba, fadeProgress(offset))
        return base.baseRgba
    }

    fun hudRotationOffset(x: Int, y: Int, seed: Float): Float =
        (((x * 0.00035f) + (y * 0.0002f) + seed) % 1f + 1f) % 1f

    fun offsetPhase(offset: Float, delta: Float): Float = ((offset + delta) % 1f + 1f) % 1f

    fun fadeProgress(offset: Float): Float {
        val angle = ((((System.currentTimeMillis() % 2500L) / 2500f) + offset) * (2f * PI.toFloat()))
        return ((sin(angle) + 1f) * 0.5f).coerceIn(0f, 1f)
    }

    fun blendColors(start: Int, end: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        val sa = start ushr 24 and 0xFF; val sr = start ushr 16 and 0xFF; val sg = start ushr 8 and 0xFF; val sb = start and 0xFF
        val ea = end ushr 24 and 0xFF; val er = end ushr 16 and 0xFF; val eg = end ushr 8 and 0xFF; val eb = end and 0xFF
        val a = (sa + (ea - sa) * t).roundToInt(); val r = (sr + (er - sr) * t).roundToInt()
        val g = (sg + (eg - sg) * t).roundToInt(); val b = (sb + (eb - sb) * t).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
