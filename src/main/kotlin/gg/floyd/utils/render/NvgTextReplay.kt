package gg.floyd.utils.render

import com.mojang.blaze3d.systems.RenderSystem
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.utils.font.MsdfFontMetrics
import gg.floyd.utils.perf.FloydPerf
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import org.joml.Matrix4f
import kotlin.math.round

/**
 * One deferred NanoVG text call captured by [gg.floyd.utils.ui.rendering.NVGRenderer] for mc.font
 * replay. All geometry is in the NVG FRAME space of the capture (logical points — `nvgBeginFrame`
 * divides the slot's physical px by the devicePixelRatio):
 *  - [x]/[y] are the call's local coordinates ([y] = the em-box TOP, NVG `ALIGN_TOP`; the legacy
 *    `text()` +0.5f fudge is baked in at capture so replay reproduces the old pixel position),
 *  - [transform] is the `nvgCurrentTransform` 2x3 at call time (maps local → frame space; the
 *    ClickGUI only ever uses translate/scale, so text stays axis-aligned),
 *  - [scissor] is the frame-space `[x0, y0, x1, y1]` intersection of the scissor stack at call
 *    time (transformed at push time, exactly what `nvgScissor` clips to), null = unclipped,
 *  - [wrapWidth] > 0 marks a `drawWrappedString` capture: replay re-wraps via `Font.split` (the
 *    same splitter `wrappedTextBounds` now sizes boxes with) and draws line by line, advancing
 *    9·[lineHeight] font units per line (= size·lineHeight px).
 */
class DeferredNvgText(
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Int,
    val transform: FloatArray,
    val scissor: FloatArray?,
    val layer: Int,
    val wrapWidth: Float = 0f,
    val lineHeight: Float = 1f,
)

/**
 * Replays the ClickGUI's deferred NanoVG text into the live PIP slot texture via
 * `mc.font.drawInBatch` — the in-PIP replay venue of design D7 step 5 (the PRIMARY venue per the
 * adversarial review: NanoVG buffers every shape until `nvgEndFrame`, so text must be drawn after
 * the shape flush of its layer yet below the next layer's shapes, which only baking into the PIP
 * texture between NVG sub-frames can express. GuiGraphics-submitted replay is z-broken for
 * layer 0: one PIP composites all text above the single blit, and `submitText`'s node search lands
 * layer-0 text above the blit's node).
 *
 * Runs inside `NVGPIPRenderer.renderContent`, where `RenderSystem.outputColor/DepthTextureOverride`
 * still point at the pooled slot: `RenderType.draw` picks the override up, so the glyph render
 * passes land in the slot texture, and the slot-ortho projection set by
 * `PooledPicturePIPRenderer.prepare` (physical px, y-down) is still current — nothing in an NVG
 * sub-frame touches RenderSystem's matrices. Glyphs draw with `DisplayMode.SEE_THROUGH` +
 * `FULL_BRIGHT` (the [HudTextRenderer] precedent): no depth interaction, pure painter's order.
 * Blending self-premultiplies: a straight-alpha TRANSLUCENT draw into the cleared slot leaves
 * (C·a, a) — exactly what the blit's GUI_TEXTURED_PREMULTIPLIED_ALPHA expects, so text over
 * transparent slot regions (title/community rows) composites correctly.
 *
 * Replay pose (framebuffer px = nvgTransform × dpr):
 * `pose = scaleLocal(dpr) ∘ capturedTransform ∘ translate(x, y + VERTICAL_ANCHOR·size) ∘ scale(size/9)`
 * — glyphs are built in vanilla's 9-unit line space, `size/9` maps them to NVG-frame px at the
 * call's size (the same `MsdfFontMetrics.scaleFor` mapping the flipped `textWidth` measures with,
 * so measurement cannot shear from rendering), the captured 2x3 maps to frame space, and ×dpr
 * converts to the slot's physical px. This resolves D10's open item the way P3 resolved it for the
 * HUD panels: NO size normalization — ClickGUI text inherits the global font (vanilla bitmap when
 * `globalCustomFont` is OFF) and its `fontDisplaySize` coupling, with widths and rendering moving
 * together by construction.
 */
object NvgTextReplay {

    /**
     * Vertical re-anchor from NVG `ALIGN_TOP` to mc.font's line top, in px per size-px.
     *
     * NVG (fontstash) `ALIGN_TOP` puts the y argument at the em-box top and the baseline at
     * `y + ascent/(ascent − descent) · size` (hhea metrics under fontstash's normalization). The
     * bundled `assets/floydaddons/font.ttf` (Inter 28pt SemiBold) has hhea ascent 1984, descent
     * −494 → 1984/2478 ≈ 0.80065. mc.font draws the baseline at row 7 of its 9-unit line, i.e.
     * `(7/9)·size` below the draw origin. Matching baselines:
     * `y_mc = y_nvg + (1984/2478 − 7/9)·size` ≈ `y_nvg + 0.0229·size` (≈0.37px at size 16).
     * Calibrated for the bundled font; a custom global TTF shifts the *rendered* ascent but the
     * old NVG anchor it is measured against also used the bundled font, so the constant stays.
     */
    const val VERTICAL_ANCHOR: Float = 1984f / 2478f - 7f / 9f

    /**
     * Whether NVGRenderer initialized with text deferral live (false = `FLOYD_NVG_TEXT=1` legacy
     * NVG text). Null until the NVG context exists — kept here so `/state` can report it without
     * forcing NVGRenderer's GL init.
     */
    var deferralActive: Boolean? = null
        internal set

    /** Render-thread working counts for the current PIP frame, keyed by capture layer. */
    private val frameCounts = LinkedHashMap<Int, Int>()

    /** Immutable snapshot published after each [replay] — what the bridge thread reads. */
    @Volatile
    private var publishedCounts: Map<Int, Int> = emptyMap()

    /** Resets the per-frame debug counters; called at the top of each NVG PIP frame. */
    fun beginFrameCounts() {
        frameCounts.clear()
        publishedCounts = emptyMap()
    }

    /** Debug payload for the 38769 bridge `/state` (counts are from the most recent PIP frame). */
    fun debugState(): Map<String, Any?> = mapOf(
        "deferralActive" to deferralActive,
        "replayedByLayer" to publishedCounts,
    )

    /**
     * Draws [runs] into the currently-overridden PIP slot ([slotWidth]×[slotHeight] physical px)
     * in capture order. Consecutive runs sharing a scissor rect flush as one batch; the scissor is
     * applied through [RenderSystem.enableScissorForRenderTypeDraws], the official clip for
     * `RenderType.draw` passes (a raw GL scissor would be reset by the pass's per-draw setup,
     * which force-disables the scissor test when the pass has none).
     */
    fun replay(runs: List<DeferredNvgText>, slotWidth: Int, slotHeight: Int, bufferSource: MultiBufferSource.BufferSource) {
        if (runs.isEmpty()) return
        FloydPerf.section("ClickGUI.textReplay") { replayInner(runs, slotWidth, slotHeight, bufferSource) }
    }

    private fun replayInner(runs: List<DeferredNvgText>, slotWidth: Int, slotHeight: Int, bufferSource: MultiBufferSource.BufferSource) {
        val dpr = NVGRenderer.devicePixelRatio()

        // Glyph quads flush through RenderSystem's MODELVIEW; force identity for the replay (the
        // pose carries the full transform) and restore after — the PostHudOverlay precedent.
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.identity()
        RenderSystem.disableScissorForRenderTypeDraws() // defensive: unclipped groups rely on it being off
        try {
            var i = 0
            while (i < runs.size) {
                val scissor = runs[i].scissor
                var end = i + 1
                while (end < runs.size && sameScissor(runs[end].scissor, scissor)) end++
                val glRect = glScissorRect(scissor, dpr, slotWidth, slotHeight)
                if (glRect == null) {
                    // Fully clipped — skip the whole group.
                    i = end
                    continue
                }
                if (glRect.isNotEmpty()) RenderSystem.enableScissorForRenderTypeDraws(glRect[0], glRect[1], glRect[2], glRect[3])
                for (k in i until end) draw(runs[k], dpr, bufferSource)
                bufferSource.endBatch()
                RenderSystem.disableScissorForRenderTypeDraws()
                i = end
            }
        } finally {
            RenderSystem.disableScissorForRenderTypeDraws()
            modelView.popMatrix()
            publishedCounts = LinkedHashMap(frameCounts)
        }
    }

    private fun draw(run: DeferredNvgText, dpr: Float, bufferSource: MultiBufferSource.BufferSource) {
        val t = run.transform
        // Column-major: col0 = (a, b), col1 = (c, d), col3 = (e, f) — the NVG 2x3 affine.
        val pose = Matrix4f(
            t[0], t[1], 0f, 0f,
            t[2], t[3], 0f, 0f,
            0f, 0f, 1f, 0f,
            t[4], t[5], 0f, 1f,
        )
            .scaleLocal(dpr, dpr, 1f)
            .translate(run.x, run.y + VERTICAL_ANCHOR * run.size, 0f)
            .scale(MsdfFontMetrics.scaleFor(run.size), MsdfFontMetrics.scaleFor(run.size), 1f)

        if (run.wrapWidth <= 0f) {
            mc.font.drawInBatch(
                run.text, 0f, 0f, run.color, false, pose, bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT
            )
        } else {
            val lines = mc.font.split(Component.literal(run.text), MsdfFontMetrics.wrapUnitsFor(run.wrapWidth, run.size))
            var lineY = 0f
            for (line in lines) {
                mc.font.drawInBatch(
                    line, 0f, lineY, run.color, false, pose, bufferSource,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT
                )
                lineY += MsdfFontMetrics.LINE_HEIGHT * run.lineHeight
            }
        }
        frameCounts.merge(run.layer, 1, Int::plus)
    }

    private fun sameScissor(a: FloatArray?, b: FloatArray?): Boolean =
        a === b || (a != null && b != null && a.contentEquals(b))

    /**
     * Converts a frame-space scissor rect to GL scissor coords inside the slot texture:
     * physical px = frame px × [dpr]; GL's scissor origin is the slot's BOTTOM-left while the NVG
     * frame (and the slot ortho) run y-down from the top, so y flips by [slotHeight].
     *
     * Returns `[x, y, w, h]` for `enableScissorForRenderTypeDraws`, an EMPTY array for
     * "no scissor", or null when the clip is empty (callers skip the draws entirely).
     */
    internal fun glScissorRect(scissor: FloatArray?, dpr: Float, slotWidth: Int, slotHeight: Int): IntArray? {
        if (scissor == null) return IntArray(0)
        val x0 = round(scissor[0] * dpr).toInt().coerceIn(0, slotWidth)
        val x1 = round(scissor[2] * dpr).toInt().coerceIn(0, slotWidth)
        val y0 = round(scissor[1] * dpr).toInt().coerceIn(0, slotHeight)
        val y1 = round(scissor[3] * dpr).toInt().coerceIn(0, slotHeight)
        if (x1 <= x0 || y1 <= y0) return null
        return intArrayOf(x0, slotHeight - y1, x1 - x0, y1 - y0)
    }
}
