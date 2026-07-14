package gg.floyd.utils.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.utils.ui.rendering.PostHudOverlay
import net.minecraft.client.renderer.fog.FogRenderer
import org.lwjgl.system.MemoryStack
import net.minecraft.client.gui.*
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GuiItemRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix3x2f
import org.joml.Matrix4f
import java.util.*

//? if >=26.2 {
/*class ItemStateRenderer(ignored: MultiBufferSource.BufferSource)
    : PictureInPictureRenderer<ItemStateRenderer.State>() {
*///?} else {
class ItemStateRenderer(vertexConsumers: MultiBufferSource.BufferSource)
    : PictureInPictureRenderer<ItemStateRenderer.State>(vertexConsumers) {
//?}

    private var textureView: GpuTextureView? = null
    private var lastState: State? = null

    //? if >=26.2 {
    /*override fun renderToTexture(state: State, poseStack: PoseStack, submitNodeCollector: MultiBufferSource) {
    *///?} else {
    override fun renderToTexture(state: State, poseStack: PoseStack) {
    //?}
        textureView = RenderSystem.outputColorTextureOverride
        lastState = state
        poseStack.scale(1f, -1f, -1f)

        try {
            if (state.state.itemStackRenderState().usesBlockLight()) mc.gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_3D)
            else mc.gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_FLAT)

            //? if >=26.2 {
            /*state.state.itemStackRenderState().submit(poseStack, submitNodeCollector, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0)
            *///?} else {
            val dispatcher = mc.gameRenderer.featureRenderDispatcher
            state.state.itemStackRenderState().submit(poseStack, dispatcher.submitNodeStorage, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0)
            dispatcher.renderAllFeatures()

            //?}
        } finally {
            mc.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        }
    }

    override fun blitTexture(element: State, state: GuiRenderState) {
        state.addBlitToCurrentLayer(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, TextureSetup.singleTexture(textureView!!, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR)),
                element.pose(), element.x0(), element.y0(), element.x0() + 16, element.y0() + 16,
                0.0f, 1.0f, 1.0f, 0.0f, -1, element.scissorArea(), null
            )
        )
    }

    override fun textureIsReadyToBlit(state: State): Boolean = lastState != null && lastState == state
    override fun getTranslateY(height: Int, windowScaleFactor: Int): Float = height / 2f
    override fun getRenderStateClass(): Class<State> = State::class.java
    override fun getTextureLabel(): String = "item_state"

    data class State(val state: GuiItemRenderState) : PictureInPictureRenderState {
        override fun scale(): Float = maxOf(state.pose().m00(), state.pose().m11()) * 16f
        override fun x0(): Int = state.x()
        override fun y0(): Int = state.y()
        override fun x1(): Int = state.x() + scale().toInt()
        override fun y1(): Int = state.y() + scale().toInt()
        override fun scissorArea(): ScreenRectangle? = state.scissorArea()
        override fun bounds(): ScreenRectangle? = state.bounds()
        override fun pose(): Matrix3x2f = state.pose()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is State) return false
            if (other.state.itemStackRenderState().modelIdentity != state.itemStackRenderState().modelIdentity) return false
            if (other.state.pose().m00() != state.pose().m00()) return false
            if (other.state.pose().m11() != state.pose().m11()) return false
            return true
        }

        override fun hashCode(): Int {
            return Objects.hash(state.itemStackRenderState().modelIdentity, state.pose().m00(), state.pose().m11())
        }
    }

    companion object {
        fun GuiGraphics.drawItemStack(item: ItemStack, x: Int, y: Int) {
            if (item.isEmpty) return

            val tracking = TrackingItemStackRenderState()
            mc.itemModelResolver.updateForTopItem(tracking, item, ItemDisplayContext.GUI, mc.level, mc.player, 0)

            val state = State(
                GuiItemRenderState(
                    Matrix3x2f(pose()),
                    tracking,
                    x,
                    y,
                    scissorStack.peek()
                )
            )
            guiRenderState.addPicturesInPictureState(state)
        }

        /**
         * Renders an item-stack icon DIRECTLY to the bound main framebuffer for the post-HUD pass — no
         * PIP / shared texture, so overlapping icons can't clobber each other (the old single-texture
         * black-icon bug). The caller must have the main FBO bound and
         * [com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride] set, and the main depth
         * must have been cleared this pass ([PostHudOverlay.render] does both).
         *
         * [x],[y],[size] are FRAMEBUFFER pixels — the same space the SDF panel helpers
         * ([RoundRectPIPRenderer.drawInline] / [PanelBlurPIPRenderer.drawInline]) use, and the space
         * [PostHudOverlay.applyScreenProjection] projects (ortho over the whole framebuffer in pixels).
         * The item's 16-unit GUI model box is centred at (x+size/2, y+size/2) and scaled to [size] px.
         */
        /** Single-item convenience over the inline batch. Must NOT be called between another
         * caller's [beginInlineBatch] and [flushInlineBatch] — it resets and flushes the shared batch. */
        fun drawItemInline(item: ItemStack, x: Float, y: Float, size: Float) {
            if (item.isEmpty) return
            val tracking = TrackingItemStackRenderState()
            mc.itemModelResolver.updateForTopItem(tracking, item, ItemDisplayContext.GUI, mc.level, mc.player, 0)
            beginInlineBatch()
            submitInline(tracking, x, y, size)
            flushInlineBatch()
        }

        // ---- Batched inline item pass ------------------------------------------------------------
        // ONE projection set, ONE fog swap, lighting set per GROUP (flat/3D), ONE
        // renderAllFeatures+endBatch per non-empty group — vs per-item everything (the Inventory
        // HUD's 27 items measured 786µs + 197KB per frame on the per-item path). The shared
        // PoseStack is safe across submits because the submit pipeline copies pose state into the
        // render nodes (the standard entity-render pattern: many submits share one stack, flushed
        // later). Render-thread-only, non-reentrant. Entries are pooled — steady state allocates
        // nothing. Callers may cache TrackingItemStackRenderState across frames and re-submit it
        // (the vanilla PIP cache pattern); see FloydInventoryHud's per-slot cache.

        private class InlineEntry {
            var tracking: TrackingItemStackRenderState? = null
            var x = 0f
            var y = 0f
            var size = 0f
        }

        private val inlineFlat = ArrayList<InlineEntry>()
        private val inlineLit = ArrayList<InlineEntry>()
        private var inlineFlatCount = 0
        private var inlineLitCount = 0
        private val inlinePose = PoseStack()

        fun beginInlineBatch() {
            inlineFlatCount = 0
            inlineLitCount = 0
        }

        fun submitInline(tracking: TrackingItemStackRenderState, x: Float, y: Float, size: Float) {
            val group: ArrayList<InlineEntry>
            val index: Int
            if (tracking.usesBlockLight()) {
                group = inlineLit; index = inlineLitCount++
            } else {
                group = inlineFlat; index = inlineFlatCount++
            }
            val entry = if (index < group.size) group[index] else InlineEntry().also { group.add(it) }
            entry.tracking = tracking
            entry.x = x
            entry.y = y
            entry.size = size
        }

        fun flushInlineBatch() {
            if (inlineFlatCount == 0 && inlineLitCount == 0) return
            // Framebuffer-pixel ortho over the whole main FB (same projection mc.font uses in this pass).
            PostHudOverlay.applyScreenProjection()

            // FOG FIX: at END_MAIN the bound Fog UBO is still the finite WORLD fog. The GUI item shader
            // (core/entity.fsh) ends with apply_fog(color, length(Position), ..., FogColor); our pose puts
            // the item at screen-pixel coords (length = hundreds), so the fog value saturates to 1.0 and the
            // texel is REPLACED by the fog color — items come out as flat gray/white silhouettes. Vanilla
            // GUI/PIP items render with FogMode.NONE (FogColor.a = 0 -> apply_fog is a no-op). Bind a zeroed
            // no-fog UBO for the pass, then restore the world fog so subsequent draws aren't left fog-less.
            val savedFog = RenderSystem.getShaderFog()
            RenderSystem.setShaderFog(noFogUbo())
            try {
                drawInlineGroup(inlineFlat, inlineFlatCount, Lighting.Entry.ITEMS_FLAT)
                drawInlineGroup(inlineLit, inlineLitCount, Lighting.Entry.ITEMS_3D)
            } finally {
                savedFog?.let { RenderSystem.setShaderFog(it) }
                mc.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
                PostHudOverlay.bindMainFbo()
                // Drop tracking refs even if a group draw threw, so cached states aren't pinned.
                for (entry in inlineFlat) entry.tracking = null
                for (entry in inlineLit) entry.tracking = null
            }
        }

        private fun drawInlineGroup(group: ArrayList<InlineEntry>, count: Int, lighting: Lighting.Entry) {
            if (count == 0) return
            mc.gameRenderer.lighting.setupFor(lighting)
            val dispatcher = mc.gameRenderer.featureRenderDispatcher
            //? if >=26.2 {
            /*val submitNodeStorage = net.minecraft.client.renderer.SubmitNodeStorage()
            *///?}
            for (i in 0 until count) {
                val entry = group[i]
                // A GUI item model (after its ItemDisplayContext.GUI display transform) occupies a ~1-unit
                // box centred at the origin, so under the framebuffer-pixel ortho 1 unit == 1 px and the
                // pose scale IS the on-screen size in px. (Y flipped: screen +y is down vs model +y up;
                // Z scaled to match so the iso depth stays proportional.)
                inlinePose.pushPose()
                inlinePose.translate(entry.x + entry.size / 2f, entry.y + entry.size / 2f, 0f)
                inlinePose.scale(entry.size, -entry.size, entry.size)
                //? if >=26.2 {
                /*entry.tracking!!.submit(inlinePose, submitNodeStorage, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0)
                *///?} else {
                entry.tracking!!.submit(inlinePose, dispatcher.submitNodeStorage, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0)
                //?}
                inlinePose.popPose()
            }
            //? if >=26.2 {
            /*dispatcher.renderAllFeatures(submitNodeStorage)
            *///?} else {
            dispatcher.renderAllFeatures()
            //?}
            // renderAllFeatures() only QUEUES geometry into the feature buffer source; it is not drawn
            // until the batch ends. The PIP path works because PictureInPictureRenderer.prepare() calls
            // bufferSource.endBatch() after renderToTexture. Inline we must flush it ourselves — once
            // per lighting group (the lighting UBO is read at draw time, so groups can't share a flush).
            //? if <26.2 {
                        mc.renderBuffers().bufferSource().endBatch()
            //?}
        }

        // Cached zeroed Fog UBO (built once). FogColor alpha 0 makes core/entity.fsh's apply_fog() a no-op,
        // so inline GUI items keep their texture instead of being washed to the world's fog color. Layout
        // matches FogRenderer's UBO exactly: one vec4 (color) + six distance floats (FOG_UBO_SIZE bytes).
        private var noFogUboSlice: GpuBufferSlice? = null

        private fun noFogUbo(): GpuBufferSlice {
            noFogUboSlice?.let { return it }
            val device = RenderSystem.getDevice()
            val size = FogRenderer.FOG_UBO_SIZE
            val buffer = MemoryStack.stackPush().use { stack ->
                val bb = stack.malloc(size)
                Std140Builder.intoBuffer(bb)
                    .putVec4(0f, 0f, 0f, 0f)                  // FogColor: alpha 0 -> apply_fog is a no-op
                    .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)   // environmental start/end
                    .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)   // render-distance start/end
                    .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)   // sky end / clouds end
                bb.flip()
                device.createBuffer({ "FloydAddons no-fog UBO" }, GpuBuffer.USAGE_UNIFORM, bb)
            }
            return buffer.slice(0L, size.toLong()).also { noFogUboSlice = it }
        }

        /**
         * Renders a GUI item model as a WORLD-SPACE billboard (the ESP overhead equipment icons), seeded
         * with [modelView] — the billboard basis already translated/scaled to the icon's world MVP. Unlike
         * [drawItemInline] it does NOT set a screen projection: the bound world ProjMat (proj×view) stays in
         * place so the item billboards in the world. The no-fog UBO is applied so the world fog doesn't wash
         * the icon. The ModelView STACK is pushed to identity around the submit because at END_MAIN it still
         * holds the world camera modelview (PostHudOverlay's reset hasn't run) and item sub-feature renderers
         * read it — otherwise icons swim with the camera.
         */
        fun drawItemWorld(item: ItemStack, modelView: Matrix4f) {
            if (item.isEmpty) return
            val tracking = TrackingItemStackRenderState()
            mc.itemModelResolver.updateForTopItem(tracking, item, ItemDisplayContext.GUI, mc.level, mc.player, 0)

            if (tracking.usesBlockLight()) mc.gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_3D)
            else mc.gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_FLAT)

            val savedFog = RenderSystem.getShaderFog()
            RenderSystem.setShaderFog(noFogUbo())
            val mvStack = RenderSystem.getModelViewStack()
            mvStack.pushMatrix()
            mvStack.identity()
            try {
                val pose = PoseStack()
                pose.last().pose().set(modelView)   // seed the PoseStack with the world billboard MVP
                val dispatcher = mc.gameRenderer.featureRenderDispatcher
                //? if >=26.2 {
                /*val submitNodeStorage = net.minecraft.client.renderer.SubmitNodeStorage()
                tracking.submit(pose, submitNodeStorage, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0)
                dispatcher.renderAllFeatures(submitNodeStorage)
                *///?} else {
                tracking.submit(pose, dispatcher.submitNodeStorage, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0)
                dispatcher.renderAllFeatures()
                mc.renderBuffers().bufferSource().endBatch()
                //?}
            } finally {
                mvStack.popMatrix()
                savedFog?.let { RenderSystem.setShaderFog(it) }
                mc.gameRenderer.lighting.setupFor(Lighting.Entry.LEVEL)
            }
        }
    }
}
