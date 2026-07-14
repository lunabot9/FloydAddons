package gg.floyd.utils.render

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderPipelines

/**
 * A [PictureInPictureRenderer] whose per-frame offscreen textures are POOLED so that several panels of
 * the same render-state class can be drawn in one frame without clobbering each other.
 *
 * ## Why this exists
 * The vanilla base keeps a SINGLE texture per renderer instance ([PictureInPictureRenderer] holds one
 * `texture`/`textureView`), and its `prepare()` recreates+closes that texture whenever the panel size
 * changes. But each panel's blit is *deferred*: `GuiRenderer.prepare()` renders every PIP up front and
 * only submits a `BlitRenderState` referencing the renderer's `textureView`; the actual draw happens
 * later in the GUI flush. Floyd submits MANY panels of the same class per frame (the inventory HUD, the
 * scoreboard, the day tracker, and one ESP overhead nameplate *per visible player*). With more than one
 * panel of a class, the single shared texture is overwritten by the last panel (→ wrong content), and
 * on a size change the earlier panel's texture is freed (and its memory recycled+cleared for the next
 * panel) before that earlier blit draws — so overlapping Floyd panels flicker or go fully BLACK.
 *
 * This base hands every panel a distinct pooled texture that is kept alive until the end-of-frame GUI
 * flush has drawn its blit, then recycled on the next frame. It faithfully mirrors the vanilla
 * `prepare()` setup (ortho projection sized to the texture, clear, output-texture override, pose, blit)
 * so subclasses keep their existing [renderContent] bodies unchanged.
 *
 * Pool textures are recycled once per frame via [recycleAll], wired from the existing per-frame PIP
 * clear hook (after the prior frame's GUI flush, before this frame's panels are submitted).
 */
//? if >=26.2 {
/*abstract class PooledPicturePIPRenderer<T : PictureInPictureRenderState>() : PictureInPictureRenderer<T>() {
    constructor(ignored: Any?) : this()

    private val pool = ArrayDeque<Slot>()
    private val inUse = ArrayList<Slot>()
    private var frame = 0L
    private val projection = CachedOrthoProjectionMatrixBuffer(
        "Floyd Pooled PIP - ${this::class.java.simpleName}", -1000.0f, 1000.0f, true
    )

    init {
        register(this)
    }

    protected lateinit var submitNodeCollector: net.minecraft.client.renderer.SubmitNodeCollector

    protected abstract fun renderContent(state: T, poseStack: PoseStack)

    final override fun renderToTexture(
        state: T,
        poseStack: PoseStack,
        submitNodeCollector: net.minecraft.client.renderer.SubmitNodeCollector,
    ) {
        this.submitNodeCollector = submitNodeCollector
        renderContent(state, poseStack)
    }

    final override fun prepare(
        state: T,
        guiRenderState: GuiRenderState,
        featureRenderDispatcher: net.minecraft.client.renderer.feature.FeatureRenderDispatcher,
        windowScale: Int,
    ) {
        val w = (state.x1() - state.x0()) * windowScale
        val h = (state.y1() - state.y0()) * windowScale
        if (w <= 0 || h <= 0) return

        val slot = acquire(w, h)
        val savedColorOverride = RenderSystem.outputColorTextureOverride
        val savedDepthOverride = RenderSystem.outputDepthTextureOverride
        val savedProjection = RenderSystem.getProjectionMatrixBuffer()
        val savedProjectionType = RenderSystem.getProjectionType()
        val modelView = RenderSystem.getModelViewStack()

        try {
            RenderSystem.outputColorTextureOverride = slot.colorView
            RenderSystem.outputDepthTextureOverride = slot.depthView
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                slot.color, org.joml.Vector4f(0f, 0f, 0f, 0f), slot.depth, 0.0
            )
            RenderSystem.setProjectionMatrix(projection.getBuffer(w.toFloat(), h.toFloat()), ProjectionType.ORTHOGRAPHIC)

            modelView.pushMatrix()
            try {
                val pose = PoseStack()
                pose.translate(w / 2.0f, getTranslateY(h, windowScale), 0.0f)
                val scale = windowScale * state.scale()
                pose.scale(scale, scale, -scale)
                renderToTexture(state, pose, slot.submits)
                featureRenderDispatcher.renderAllFeatures(slot.submits)
            } finally {
                modelView.popMatrix()
            }
        } finally {
            RenderSystem.outputColorTextureOverride = savedColorOverride
            RenderSystem.outputDepthTextureOverride = savedDepthOverride
            if (savedProjection != null) RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType)
        }

        guiRenderState.addBlitToCurrentLayer(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(slot.colorView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                state.pose(),
                state.x0(), state.y0(), state.x1(), state.y1(),
                0.0f, 1.0f, 1.0f, 0.0f,
                -1,
                state.scissorArea(),
                null,
            )
        )
    }

    private fun acquire(w: Int, h: Int): Slot {
        val index = pool.indexOfFirst { it.width == w && it.height == h }
        val slot = if (index >= 0) pool.removeAt(index) else createSlot(w, h)
        slot.lastUsedFrame = frame
        inUse.add(slot)
        return slot
    }

    private fun createSlot(w: Int, h: Int): Slot {
        val device = RenderSystem.getDevice()
        val colorUsage = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT
        val depthUsage = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_RENDER_ATTACHMENT
        val color = device.createTexture(
            { "Floyd PIP ${getTextureLabel()} color" }, colorUsage, TextureFormat.RGBA8, w, h, 1, 1
        )
        val colorView = device.createTextureView(color)
        val depth = device.createTexture(
            { "Floyd PIP ${getTextureLabel()} depth" }, depthUsage, TextureFormat.DEPTH32, w, h, 1, 1
        )
        val depthView = device.createTextureView(depth)
        return Slot(w, h, color, colorView, depth, depthView, net.minecraft.client.renderer.SubmitNodeStorage())
    }

    private fun recycle() {
        frame++
        pool.addAll(inUse)
        inUse.clear()
        val cutoff = frame - IDLE_FRAMES_BEFORE_CLOSE
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val slot = iterator.next()
            if (slot.lastUsedFrame < cutoff) {
                slot.close()
                iterator.remove()
            }
        }
    }

    private class Slot(
        val width: Int,
        val height: Int,
        val color: GpuTexture,
        val colorView: GpuTextureView,
        val depth: GpuTexture,
        val depthView: GpuTextureView,
        val submits: net.minecraft.client.renderer.SubmitNodeStorage,
    ) {
        var lastUsedFrame = 0L

        fun close() {
            colorView.close(); color.close(); depthView.close(); depth.close()
        }
    }

    companion object {
        private const val IDLE_FRAMES_BEFORE_CLOSE = 180L
        private val instances = ArrayList<PooledPicturePIPRenderer<*>>()

        private fun register(renderer: PooledPicturePIPRenderer<*>) {
            instances.add(renderer)
        }

        @JvmStatic
        fun recycleAll() {
            for (renderer in instances) renderer.recycle()
        }
    }
}
*///?} else {
abstract class PooledPicturePIPRenderer<T : PictureInPictureRenderState>(
    bufferSource: MultiBufferSource.BufferSource
) : PictureInPictureRenderer<T>(bufferSource) {

    private val pool = ArrayDeque<Slot>()
    private val inUse = ArrayList<Slot>()
    private var frame = 0L
    private val projection = CachedOrthoProjectionMatrixBuffer(
        "Floyd Pooled PIP - ${this::class.java.simpleName}", -1000.0f, 1000.0f, true
    )

    init {
        register(this)
    }

    /** Same contract as the vanilla `renderToTexture`: draw the panel into the bound override texture. */
    protected lateinit var submitNodeCollector: net.minecraft.client.renderer.SubmitNodeCollector

    protected abstract fun renderContent(state: T, poseStack: PoseStack)

    final override fun renderToTexture(state: T, poseStack: PoseStack) = renderContent(state, poseStack)

    override fun prepare(state: T, guiRenderState: GuiRenderState, windowScale: Int) {
        val w = (state.x1() - state.x0()) * windowScale
        val h = (state.y1() - state.y0()) * windowScale
        if (w <= 0 || h <= 0) return

        val slot = acquire(w, h)
        val savedColorOverride = RenderSystem.outputColorTextureOverride
        val savedDepthOverride = RenderSystem.outputDepthTextureOverride
        val savedProjection = RenderSystem.getProjectionMatrixBuffer()
        val savedProjectionType = RenderSystem.getProjectionType()

        try {
            RenderSystem.outputColorTextureOverride = slot.colorView
            RenderSystem.outputDepthTextureOverride = slot.depthView
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(slot.color, 0, slot.depth, 1.0)
            RenderSystem.setProjectionMatrix(projection.getBuffer(w.toFloat(), h.toFloat()), ProjectionType.ORTHOGRAPHIC)

            val pose = PoseStack()
            pose.translate(w / 2.0f, getTranslateY(h, windowScale), 0.0f)
            val f = windowScale * state.scale()
            pose.scale(f, f, -f)
            renderContent(state, pose)
            bufferSource.endBatch()
        } finally {
            RenderSystem.outputColorTextureOverride = savedColorOverride
            RenderSystem.outputDepthTextureOverride = savedDepthOverride
            if (savedProjection != null) RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType)
        }

        guiRenderState.addBlitToCurrentLayer(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(slot.colorView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                state.pose(),
                state.x0(), state.y0(), state.x1(), state.y1(),
                0.0f, 1.0f, 1.0f, 0.0f,
                -1,
                state.scissorArea(),
                null
            )
        )
    }

    private fun acquire(w: Int, h: Int): Slot {
        val idx = pool.indexOfFirst { it.width == w && it.height == h }
        val slot = if (idx >= 0) pool.removeAt(idx) else createSlot(w, h)
        slot.lastUsedFrame = frame
        inUse.add(slot)
        return slot
    }

    private fun createSlot(w: Int, h: Int): Slot {
        val device = RenderSystem.getDevice()
        // Mirror vanilla PictureInPictureRenderer exactly: 26.1.2 validates that the offscreen color
        // target cleared by CommandEncoder has COPY_DST in addition to TEXTURE_BINDING+RENDER_ATTACHMENT.
        val colorUsage = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT
        val depthUsage = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_RENDER_ATTACHMENT
        val color = device.createTexture({ "Floyd PIP ${getTextureLabel()} color" }, colorUsage, TextureFormat.RGBA8, w, h, 1, 1)
        val colorView = device.createTextureView(color)
        val depth = device.createTexture({ "Floyd PIP ${getTextureLabel()} depth" }, depthUsage, TextureFormat.DEPTH32, w, h, 1, 1)
        val depthView = device.createTextureView(depth)
        return Slot(w, h, color, colorView, depth, depthView)
    }

    /**
     * Returns this renderer's textures to the free pool for reuse on the next frame. Called once per
     * frame AFTER the prior frame's GUI flush has drawn all blits, so the textures are safe to reuse.
     * Slots not used for a while are closed to bound memory (panel sizes drift with content/scale).
     */
    private fun recycle() {
        frame++
        pool.addAll(inUse)
        inUse.clear()
        val cutoff = frame - IDLE_FRAMES_BEFORE_CLOSE
        val it = pool.iterator()
        while (it.hasNext()) {
            val slot = it.next()
            if (slot.lastUsedFrame < cutoff) {
                slot.close()
                it.remove()
            }
        }
    }

    private class Slot(
        val width: Int,
        val height: Int,
        val color: GpuTexture,
        val colorView: GpuTextureView,
        val depth: GpuTexture,
        val depthView: GpuTextureView,
    ) {
        var lastUsedFrame = 0L
        fun close() {
            colorView.close(); color.close(); depthView.close(); depth.close()
        }
    }

    companion object {
        private const val IDLE_FRAMES_BEFORE_CLOSE = 180L
        private val instances = ArrayList<PooledPicturePIPRenderer<*>>()

        private fun register(renderer: PooledPicturePIPRenderer<*>) {
            // A resource reload builds a fresh GuiRenderer (and fresh PIP instances); the previous
            // instance simply stops being used, so its pool self-empties via the idle-close in recycle()
            // (its slots' lastUsedFrame stops advancing). No explicit retirement needed.
            instances.add(renderer)
        }

        /** Recycle every pooled PIP renderer's textures. Wired from the per-frame PIP clear hook. */
        @JvmStatic
        fun recycleAll() {
            for (renderer in instances) renderer.recycle()
        }
    }
}
//?}
