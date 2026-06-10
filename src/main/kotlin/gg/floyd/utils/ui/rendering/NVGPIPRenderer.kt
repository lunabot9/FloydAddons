package gg.floyd.utils.ui.rendering

import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import gg.floyd.FloydAddonsMod
import gg.floyd.utils.perf.FloydPerf
import gg.floyd.utils.render.NvgTextReplay
import gg.floyd.utils.render.PooledPicturePIPRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import org.joml.Matrix3x2f
import org.joml.Vector2f
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL33C
import kotlin.math.roundToInt

class NVGPIPRenderer(vertexConsumers: MultiBufferSource.BufferSource) : PooledPicturePIPRenderer<NVGPIPRenderer.NVGRenderState>(vertexConsumers) {

    /**
     * Renders the ClickGUI into the pooled PIP slot as NVG SHAPE sub-frames interleaved with
     * in-PIP mc.font TEXT replays (design D7 steps 5-6 + the per-panel CORRECTION):
     *
     *   sub-frame 0 (GUI-level shapes — title/search bar/community; text calls queue) →
     *   [per panel: endFrame → rebind slot FBO → replay the closed layer's text into the slot →
     *   next sub-frame, opened when ClickGUI calls [NVGRenderer.nextTextLayer]] → final topmost
     *   sub-frame (dragged panel + tooltip shapes) → endFrame → replay the topmost text.
     *
     * NanoVG buffers all paths until `nvgEndFrame`, so each layer's text must bake into the slot
     * texture AFTER that layer's shape flush and BEFORE the next layer's — this in-PIP venue is
     * the only structure that z-orders correctly (and keeps ClickGUI text under toasts/F3, which
     * submit above the screen stratum). Per-panel layers (not just base/top) because BASE panels
     * may overlap at rest: with a shared base layer the lower panel's replayed text bled above
     * the upper panel's shapes. The FBO/viewport/sampler dance re-runs before every NVG
     * sub-frame because the replay's blaze3d render passes retarget and bind samplers.
     */
    override fun renderContent(state: NVGRenderState, poseStack: PoseStack) {
        FloydPerf.section("ClickGUI.nvgPip") { renderContentInner(state) }
    }

    private fun renderContentInner(state: NVGRenderState) {
        val slot = bindSlotTarget() ?: return

        NvgTextReplay.beginFrameCounts()
        NVGRenderer.drainDeferredText() // drop anything stale from an aborted frame
        NVGRenderer.resetTextLayers() // and re-arm the layer counter if that frame died mid-pass

        if (NVGRenderer.deferringText && state.renderScale != 1f && !warnedRenderScale) {
            warnedRenderScale = true
            FloydAddonsMod.logger.warn(
                "[NVGPIPRenderer] renderScale=${state.renderScale} != 1 — the deferred-text replay assumes the " +
                    "ClickGUI's identity pose (captured transforms still include the scale; verify visually)"
            )
        }

        NVGRenderer.beginFrame(slot.width.toFloat(), slot.height.toFloat())
        if (state.renderScale != 1f) NVGRenderer.scale(state.renderScale, state.renderScale)

        NVGRenderer.layerBoundary = boundary@{
            // Sub-frame split: flush the closed layer's shapes, bake its text into the slot, then
            // open the next sub-frame with the caller's live transform (guiScale + open-anim)
            // restored — nvgBeginFrame resets the transform stack and scissor. Skipped when the
            // closed layer queued no text: an empty replay cannot bleed over later shapes, so the
            // split would be pure cost (endFrame/beginFrame + state resync + sacrificial quad).
            if (!NVGRenderer.hasDeferredText) return@boundary
            val transform = NVGRenderer.currentTransform()
            NVGRenderer.endFrame()
            resyncBlazeStateAfterNvg()
            NvgTextReplay.replay(NVGRenderer.drainDeferredText(), slot.width, slot.height, bufferSource)
            bindSlotTarget()
            NVGRenderer.beginFrame(slot.width.toFloat(), slot.height.toFloat())
            NVGRenderer.applyTransform(transform)
            NVGRenderer.reapplyScissor()
        }
        try {
            state.renderContent()
        } finally {
            NVGRenderer.layerBoundary = null
        }
        NVGRenderer.endFrame()

        resyncBlazeStateAfterNvg()
        NvgTextReplay.replay(NVGRenderer.drainDeferredText(), slot.width, slot.height, bufferSource)
        NvgTextReplay.publishFrameCounts()
    }

    private class SlotTarget(val width: Int, val height: Int)

    /**
     * Binds the pooled slot's FBO + viewport and unbinds the unit-0 sampler — the raw-GL setup
     * NanoVG needs (it bypasses blaze3d, so the overridden output texture means nothing to it).
     * Re-run before EVERY `nvgBeginFrame`: the interleaved replay's render passes rebind both.
     */
    private fun bindSlotTarget(): SlotTarget? {
        val colorTex = RenderSystem.outputColorTextureOverride ?: return null
        val bufferManager = (RenderSystem.getDevice() as? GlDevice)?.directStateAccess() ?: return null
        val glDepthTex = (RenderSystem.outputDepthTextureOverride?.texture() as? GlTexture) ?: return null

        val width = colorTex.getWidth(0)
        val height = colorTex.getHeight(0)
        (colorTex.texture() as? GlTexture)?.getFbo(bufferManager, glDepthTex)?.apply {
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, this)
            GlStateManager._viewport(0, 0, width, height)
        }

        GL33C.glBindSampler(0, 0)
        return SlotTarget(width, height)
    }

    /**
     * NanoVG mutates GL with RAW calls behind BOTH cache layers blaze3d trusts — GlStateManager's
     * state cache and GlCommandEncoder's `lastProgram` — so after `nvgEndFrame` raw GL holds
     * NanoVG's state (program 0, texture-2D 0 on unit 0, NVG's blend funcs, depth/cull/scissor
     * off) while the caches still claim whatever blaze3d set last. Re-asserting the desired state
     * through GlStateManager is NOT a resync: every cached setter no-ops when its cache already
     * matches, leaving raw GL untouched. That silently killed the deferred-text replay:
     * `trySetup` skipped `glUseProgram` because the encoder's `lastProgram` was already the text
     * program (PostHudOverlay's panel text runs every frame before the PIPs), and skipped the
     * glyph-atlas `_bindTexture` because the unit-0 cache still claimed it — so the glyph passes
     * drew with program 0 / texture 0 into the void while the CPU-side counters kept counting.
     * Therefore:
     *  1. force-DESYNC each cached state NanoVG raw-mutates (set the opposite value first so the
     *     final set really reaches GL), and
     *  2. flush one degenerate, fully-transparent debug quad so the encoder's `lastProgram` flips
     *     to a non-text pipeline — the first real replay batch then re-binds its program for real.
     */
    private fun resyncBlazeStateAfterNvg() {
        // Texture units: NVG's flush raw-binds its atlases on unit 0; NVG image/font CREATION
        // (mid-renderContent) raw-binds on whatever unit happened to be raw-active. Force the
        // active-unit cache coherent (toggle defeats the cached no-op), then ZERO both the raw
        // binding and the cache of every GUI-relevant unit — a stale cache claiming a texture
        // that raw GL no longer has bound false-skips later binds, which makes glyph draws
        // sample the wrong texture AND, worse, misdirects glyph-atlas writeToTexture uploads
        // into whatever is raw-bound (permanently blacking out the baked cells).
        GlStateManager._activeTexture(GL13C.GL_TEXTURE1)
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0)
        for (unit in RESYNC_TEXTURE_UNITS - 1 downTo 0) {
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + unit)
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0)
            GlStateManager._bindTexture(0)
        }
        // Boolean states NVG raw-toggled: blend left ON, depth/cull/scissor left OFF.
        GlStateManager._disableBlend()
        GlStateManager._enableBlend()
        GlStateManager._enableDepthTest()
        GlStateManager._disableDepthTest()
        GlStateManager._enableCull()
        GlStateManager._disableCull()
        GlStateManager._enableScissorTest()
        GlStateManager._disableScissorTest()
        // Blend func: NVG leaves (ONE, ONE_MINUS_SRC_ALPHA) on both channels; route through a
        // throwaway value so the final (SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO) really executes.
        GlStateManager._blendFuncSeparate(1, 0, 1, 0)
        GlStateManager._blendFuncSeparate(770, 771, 1, 0)
        // lastProgram flip: a zero-area invisible quad through a pipeline no text batch uses. Its
        // RenderType.draw opens a fresh render pass (which also nulls the encoder's lastPipeline),
        // leaving lastProgram = debug-quads, so the replay's glyph draws always re-bind for real.
        val sacrificial = bufferSource.getBuffer(RenderTypes.debugQuads())
        repeat(4) { sacrificial.addVertex(0f, 0f, 0f).setColor(0, 0, 0, 0) }
        bufferSource.endBatch()
    }

    override fun getTranslateY(height: Int, windowScaleFactor: Int): Float = height / 2f
    override fun getRenderStateClass(): Class<NVGRenderState> = NVGRenderState::class.java
    override fun getTextureLabel(): String = "nvg_renderer"

    data class NVGRenderState(
        private val x: Int,
        private val y: Int,
        private val width: Int,
        private val height: Int,
        val renderScale: Float,
        private val poseMatrix: Matrix3x2f,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle?,
        val renderContent: () -> Unit
    ) : PictureInPictureRenderState {

        override fun scale(): Float = 1f
        override fun x0(): Int = x
        override fun y0(): Int = y
        override fun x1(): Int = x + width
        override fun y1(): Int = y + height
        override fun scissorArea(): ScreenRectangle? = scissor
        override fun bounds(): ScreenRectangle? = bounds
    }

    companion object {
        /** GUI-relevant texture units to zero in [resyncBlazeStateAfterNvg] (Sampler0..Sampler2 + 1 spare). */
        private const val RESYNC_TEXTURE_UNITS = 4

        /** One process-wide soft assert that the ClickGUI's PIP pose stays identity (renderScale 1). */
        private var warnedRenderScale = false

        /**
         * Draw NVG content as a special GUI element.
         *
         * @param context The GuiGraphics to draw to
         * @param x The x position
         * @param y The y position
         * @param width The width of the rendering area
         * @param height The height of the rendering area
         * @param renderContent A lambda that draws the NVG content
         */
        fun draw(
            context: GuiGraphics,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            renderScaleMultiplier: Float = 1f,
            renderContent: () -> Unit
        ) {
            val scissor = context.scissorStack.peek()
            val pose = Matrix3x2f(context.pose())
            val p0 = pose.transformPosition(Vector2f(x.toFloat(), y.toFloat()))
            val p1 = pose.transformPosition(Vector2f((x + width).toFloat(), (y + height).toFloat()))
            if (!p0.x.isFinite() || !p0.y.isFinite() || !p1.x.isFinite() || !p1.y.isFinite()) return
            val screenLeft = minOf(p0.x, p1.x).roundToInt()
            val screenTop = minOf(p0.y, p1.y).roundToInt()
            val screenWidth = maxOf(p0.x, p1.x).roundToInt() - screenLeft
            val screenHeight = maxOf(p0.y, p1.y).roundToInt() - screenTop
            val renderScale = pose.transformDirection(Vector2f(1f, 0f)).length() * renderScaleMultiplier
            if (!renderScale.isFinite()) return
            val bounds = createBounds(screenLeft, screenTop, screenLeft + screenWidth, screenTop + screenHeight, scissor)

            val state = NVGRenderState(
                screenLeft, screenTop, screenWidth, screenHeight,
                renderScale,
                pose, scissor, bounds,
                renderContent
            )
            context.guiRenderState.submitPicturesInPictureState(state)
        }

        private fun createBounds(x0: Int, y0: Int, x1: Int, y1: Int, scissorArea: ScreenRectangle?): ScreenRectangle? {
            val screenRect = ScreenRectangle(x0, y0, x1 - x0, y1 - y0)
            return if (scissorArea != null) scissorArea.intersection(screenRect) else screenRect
        }
    }
}

