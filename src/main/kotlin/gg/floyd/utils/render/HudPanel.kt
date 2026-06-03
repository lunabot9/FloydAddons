package gg.floyd.utils.render

import gg.floyd.features.impl.render.FloydPanelStyle
import gg.floyd.features.impl.render.FloydPanelStyle.PanelTarget
import gg.floyd.utils.ChromaCache
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
     *
     * Every styling input defaults to the unified [FloydPanelStyle] globals so all Floyd panels
     * share one look: [border] defaults to the global border (color/chroma/fade), [fillColor] to the
     * global Panel Background Color, and [cornerRadius]/[outlineWidth] to the global Panel Corner
     * Radius / Panel Border Width. Callers may still pass explicit values to override (e.g. Player
     * ESP's "Border = ESP Color").
     *
     * When the global Panel Blur setting is on, a frosted (extra semi-opaque) backdrop is composited
     * behind the panel before the tinted fill so the panel reads against busy world content. A true
     * gaussian blur of the framebuffer region is not cleanly reachable through this PIP/RoundRect
     * path (see KDoc on [frostedBackdrop]); this is the graceful approximation.
     */
    fun fillPanel(
        graphics: GuiGraphics,
        x0: Int, y0: Int, x1: Int, y1: Int,
        panel: PanelTarget,
        border: BorderColors = panelBorderColors(panel, x0, y0),
        fillColor: Int = FloydPanelStyle.backgroundColorFor(panel).rgba,
        cornerRadius: Float = FloydPanelStyle.cornerRadiusFor(panel).toFloat(),
        outlineWidth: Float = FloydPanelStyle.borderWidthFor(panel).toFloat()
    ) {
        val radius = cornerRadius.coerceAtLeast(0f)
        val width = outlineWidth.coerceAtLeast(0f)

        if (FloydPanelStyle.blurFor(panel)) {
            // Strength 0..20 -> blur radius 0..8 px (sampled with a step of 2, so ~0..16 px reach).
            // Skip imperceptible blur (< 0.5px) and tiny panels (< ~45x45px) — the per-fragment kernel
            // isn't worth running there.
            val blurRadius = FloydPanelStyle.blurStrengthFor(panel).coerceIn(0, 20) * 0.4f
            val area = (x1 - x0) * (y1 - y0)
            if (blurRadius >= 0.5f && area >= 2000) {
                PanelBlurPIPRenderer.submit(
                    graphics,
                    x0, y0, x1, y1,
                    radius, radius, radius, radius,
                    blurRadius, FloydPanelStyle.blurIsBoxFor(panel)
                )
            }
        }

        RoundRectPIPRenderer.submit(
            graphics,
            x0, y0, x1, y1,
            fillColor, fillColor, fillColor, fillColor,
            radius, radius, radius, radius,
            border.topLeft, border.topRight, border.bottomRight, border.bottomLeft, width
        )
    }

    fun chromaColor(offset: Float): Int = 0xFF000000.toInt() or ChromaCache.rgbFor(offset)

    fun monochrome(color: Int): BorderColors = BorderColors(color, color, color, color)

    /**
     * The unified global panel border (color/chroma/fade) from [FloydPanelStyle], as a rotating
     * gradient seeded by the panel's top-left position so adjacent panels animate out of phase. This
     * is the default border for [fillPanel]; panels needing a custom border (e.g. an ESP-colored
     * nameplate) pass their own [BorderColors] instead.
     */
    fun panelBorderColors(panel: PanelTarget, x: Int = 0, y: Int = 0, seed: Float = 0.38f): BorderColors =
        circularBorderColors(FloydPanelStyle.borderColorFor(panel), hudRotationOffset(x, y, seed))

    /** Rotating gradient around the four corners (chroma/fade/solid per the [base] color's own settings). */
    fun circularBorderColors(base: Color, offset: Float): BorderColors =
        BorderColors(
            accentColor(base, offset),
            accentColor(base, offsetPhase(offset, 0.25f)),
            accentColor(base, offsetPhase(offset, 0.5f)),
            accentColor(base, offsetPhase(offset, 0.75f))
        )

    /** Both chroma and fade live on the [base] Color: chroma cycles, fade blends base<->fadeColor, else static. */
    fun accentColor(base: Color, offset: Float): Int {
        if (base.chroma) return chromaColor(offset)
        if (base.fade) return blendColors(base.baseRgba, base.fadeColor.baseRgba, fadeProgress(offset))
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
