package gg.floyd.utils.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.utils.font.MsdfFontMetrics
import gg.floyd.utils.ui.rendering.PostHudOverlay
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import org.joml.Matrix4f

/**
 * Shared mc.font text path for the Floyd HUD panels, generalized from the custom scoreboard's
 * Minecraft-font TEXT mode: [PostHudOverlay.applyScreenProjection] + a translate/scale [Matrix4f]
 * pose + `mc.font.drawInBatch` + one `endBatch` per call, then [PostHudOverlay.bindMainFbo] so the
 * next inline draw of the world-end pass starts from the main framebuffer again. With the global
 * MSDF provider live, mc.font IS the smooth custom font, so this replaces the panels' NanoVG text.
 *
 * Coordinates are FRAMEBUFFER pixels — the same space [RoundRectPIPRenderer.drawInline] /
 * [ItemStateRenderer.drawItemInline] draw in and [PostHudOverlay.applyScreenProjection] projects —
 * so panel text co-locates with the panel's SDF background at any devicePixelRatio. [Segment]
 * advances accumulate as [MsdfFontMetrics.unitWidth] un-ceiled floats (the same live-FontSet
 * advances the glyphs render with), so multi-segment lines butt exactly and the scoreboard's
 * layout widths can never drift from rendering.
 *
 * Text is fed to `Font.prepareText`, so [gg.floyd.mixin.mixins.FontMixin]'s CustomNameReplacer /
 * shadow hooks apply; callers that MEASURE text for layout must run CustomNameReplacer up front
 * (the replacement is idempotent, so the re-pass at draw time finds nothing left to replace).
 */
object HudTextRenderer {

    /** One run of same-colored text; per-segment color ints carry per-char chroma and style colors. */
    data class Segment(val text: String, val color: Int)

    enum class Alignment { LEFT, RIGHT }

    /**
     * Draws [text] in one color. [x]/[y] are the framebuffer-pixel top-left (or top-RIGHT edge with
     * [Alignment.RIGHT]); [scale] is framebuffer px per font unit, i.e. `panelScale * sizePx / 9`.
     */
    fun drawText(
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        color: Int,
        alignment: Alignment = Alignment.LEFT,
        displayMode: Font.DisplayMode = Font.DisplayMode.SEE_THROUGH,
        light: Int = LightTexture.FULL_BRIGHT
    ) = drawSegments(listOf(Segment(text, color)), x, y, scale, alignment, displayMode, light)

    /** Draws [segments] as one line, advancing by their un-ceiled float widths. See [drawText]. */
    fun drawSegments(
        segments: List<Segment>,
        x: Float,
        y: Float,
        scale: Float,
        alignment: Alignment = Alignment.LEFT,
        displayMode: Font.DisplayMode = Font.DisplayMode.SEE_THROUGH,
        light: Int = LightTexture.FULL_BRIGHT
    ) {
        if (segments.isEmpty() || scale <= 0f) return
        PostHudOverlay.applyScreenProjection()
        val pose = Matrix4f().translate(x, y, 0f).scale(scale, scale, 1f)
        val buffer = mc.renderBuffers().bufferSource()
        var segX = when (alignment) {
            Alignment.LEFT -> 0f
            Alignment.RIGHT -> -segments.fold(0f) { acc, s -> acc + MsdfFontMetrics.unitWidth(s.text) }
        }
        for (segment in segments) {
            mc.font.drawInBatch(segment.text, segX, 0f, segment.color, false, pose, buffer, displayMode, 0, light)
            segX += MsdfFontMetrics.unitWidth(segment.text)
        }
        buffer.endBatch()
        PostHudOverlay.bindMainFbo()
    }
}
