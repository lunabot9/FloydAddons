package gg.floyd.utils.ui.rendering

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.impl.render.FloydCustomScoreboard
import gg.floyd.features.impl.render.FloydDayTrackerModule
import gg.floyd.features.impl.render.FloydInventoryHud
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL33C

/**
 * The single immediate Floyd-panel render pass, run at the WORLD→HUD boundary
 * ([gg.floyd.events.RenderEvent.Last] / WorldRenderEvents.END_MAIN) — after the world is in the main
 * framebuffer but BEFORE the vanilla HUD and any open screen render. Drawing here:
 *  - makes the panels render regardless of any open screen (they don't vanish on chat/inventory/GUI),
 *  - puts them UNDER the HUD and any GUI, so an open ClickGUI's blur composites over them,
 *  - keeps them in ONE painter's-order pass straight to the framebuffer (no per-panel PIP texture), so
 *    overlapping panels never clobber each other (the whole point of this rewrite).
 *
 * Each panel draws directly (SDF rounded-rect in framebuffer-pixel space; NanoVG + mc.font + item models
 * in logical /dpr space, via [RenderSystem.outputColorTextureOverride] which retargets vanilla draws to
 * the main framebuffer). The FBO is re-bound between heterogeneous draws ([bindMainFbo]) because a
 * blaze3d render pass can retarget internally.
 */
/**
 * Which half of a panel is being drawn in the two-phase post-HUD pass. BACKGROUND = blaze3d (SDF fill +
 * border, frosted blur, inventory item models); TEXT = NanoVG vector text. All backgrounds run before any
 * text so NanoVG's GL-state corruption never precedes a blaze3d background. See [PostHudOverlay.render].
 */
enum class PanelPhase { BACKGROUND, TEXT }

object PostHudOverlay {

    private val screenProjection = CachedOrthoProjectionMatrixBuffer("FloydAddons PostHUD", -1000f, 1000f, true)
    private var boundFbo = 0

    // Per-frame snapshot of the main color, so a panel's frosted blur can SAMPLE the backdrop while the
    // pass WRITES the panel to the main framebuffer (sampling+writing the same texture is feedback).
    private var blurColor: GpuTexture? = null
    private var blurColorView: GpuTextureView? = null
    private var blurW = 0
    private var blurH = 0

    /** The framebuffer snapshot for panel blur to sample (null until [render] has snapshotted this frame). */
    fun blurSourceView(): GpuTextureView? = blurColorView

    private fun snapshotForBlur(target: com.mojang.blaze3d.pipeline.RenderTarget) {
        if (blurColor == null || blurW != target.width || blurH != target.height) {
            blurColorView?.close(); blurColor?.close()
            val device = RenderSystem.getDevice()
            // COPY_DST (copy destination for the framebuffer snapshot) + TEXTURE_BINDING (sampled by the blur).
            val usage = GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING
            val tex = device.createTexture({ "FloydAddons PostHUD blur source" }, usage, TextureFormat.RGBA8, target.width, target.height, 1, 1)
            blurColor = tex
            blurColorView = device.createTextureView(tex)
            blurW = target.width
            blurH = target.height
        }
        val src = target.colorTexture ?: return
        val dst = blurColor ?: return
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(src, dst, 0, 0, 0, 0, 0, target.width, target.height)
    }

    /** Ortho over the whole main framebuffer in pixels — for vanilla draws (mc.font, items) that rely on it. */
    fun applyScreenProjection() {
        val t = mc.mainRenderTarget
        RenderSystem.setProjectionMatrix(
            screenProjection.getBuffer(t.width.toFloat(), t.height.toFloat()),
            ProjectionType.ORTHOGRAPHIC
        )
    }

    /** Re-bind the main framebuffer + viewport (a blaze3d SDF render pass can retarget); call between draws. */
    fun bindMainFbo() {
        if (boundFbo == 0) return
        val t = mc.mainRenderTarget
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, boundFbo)
        GlStateManager._viewport(0, 0, t.width, t.height)
        GL33C.glBindSampler(0, 0)
    }

    /**
     * Re-sync GL state between panels so one panel can't corrupt the next. A panel that draws text
     * through NanoVG (raw OpenGL — the scoreboard, and the ESP overhead in NanoVG-font mode) mutates GL
     * state behind blaze3d's *cached* state tracker; without resyncing, the NEXT panel's blaze3d render
     * passes inherit the stale cache — the frosted blur samples a black/wrong texture and the rounded SDF
     * fill silently drops (depth-test left on / blend left off). Mirrors the reset the PIP NanoVG path
     * ([gg.floyd.utils.ui.rendering.NVGPIPRenderer]) does around its frame, but rebinds the MAIN
     * framebuffer (not 0) so the next panel keeps drawing into it. Cheap; safe to call after every panel.
     */
    fun resetBetweenPanels() {
        if (boundFbo == 0) return
        val t = mc.mainRenderTarget
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, boundFbo)
        GlStateManager._viewport(0, 0, t.width, t.height)
        GL33C.glActiveTexture(GL13C.GL_TEXTURE0)
        GL33C.glBindSampler(0, 0)
        GlStateManager._disableScissorTest()
        GlStateManager._depthMask(true)
        GlStateManager._colorMask(true, true, true, true)
        GlStateManager._disableDepthTest()
        GlStateManager._disableCull()
        GlStateManager._enableBlend()
        GlStateManager._blendFuncSeparate(770, 771, 1, 0)
    }

    @JvmStatic
    fun render() {
        if (mc.level == null || mc.player == null || mc.options.hideGui) return
        RenderSystem.assertOnRenderThread()

        val target = mc.mainRenderTarget
        val dsa = (RenderSystem.getDevice() as? GlDevice)?.directStateAccess() ?: return
        val colorTex = target.colorTexture as? GlTexture ?: return
        boundFbo = colorTex.getFbo(dsa, target.depthTexture as? GlTexture)

        // Retarget vanilla blaze3d draws (mc.font glyphs, item models) to the main framebuffer — the same
        // override PictureInPictureRenderer uses. NanoVG (raw GL) + our SDF pass ignore it.
        RenderSystem.outputColorTextureOverride = target.colorTextureView
        RenderSystem.outputDepthTextureOverride = target.depthTextureView

        // Snapshot the framebuffer (world, here) so panel blur samples it instead of the FB it writes to.
        snapshotForBlur(target)

        // Clear the main depth to far (1.0) ONCE this pass so 3D item models (drawn via the feature
        // render dispatcher with depth-test/-write on) aren't rejected by leftover world+GUI depth — the
        // same fresh-far depth a PIP gives items. Depth-only clear; the color (the rendered world) is
        // untouched. Safe at END_MAIN: nothing world-renders after this point, and the main depth is
        // re-cleared at the start of the next frame's world pass; the SDF rect/blur and mc.font/NanoVG
        // draws are depth-test-off, so a cleared depth is harmless to them.
        target.depthTexture?.let { depth ->
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depth, 1.0)
        }
        bindMainFbo()

        // Item icons (3D item models) and mc.font text in this pass flush through RenderSystem's MODELVIEW
        // matrix, which at END_MAIN is still the WORLD CAMERA view (the world was just rendered). Without
        // resetting it, the icons/text are transformed by the camera — they swim/spin around the player as
        // the view rotates instead of sitting still in their 2D panel. The SDF rect/blur use their own
        // explicit transform matrices (Matrix4f().translation), so they're unaffected. Reset the modelview
        // to identity for the whole panel pass; pop it back after so the vanilla HUD / screens draw normally.
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.identity()

        // Draw each Floyd panel directly to the framebuffer. ORDER MATTERS: NanoVG (the scoreboard's
        // smooth-font text, and the ESP overhead in NanoVG-font mode) is RAW OpenGL — it mutates GL state
        // behind blaze3d's cached state tracker, and that corruption can't be fully undone mid-frame
        // (the frosted blur of a *later* panel then samples black and its SDF fill silently drops; only
        // MC's next-frame setup truly recovers it). The item-model + mc.font panels are pure blaze3d
        // (cache-consistent), so they're safe to draw first. So: all blaze3d panels first, the NanoVG
        // scoreboard LAST, then the end-of-pass reset below cleans up before the vanilla HUD. resetBetweenPanels()
        // keeps state tidy between them (and best-effort isolates the ESP's optional NanoVG mode).
        // TWO PHASES so every panel can use NanoVG text without the multi-NanoVG black-box bug. NanoVG is
        // raw GL: it corrupts blaze3d's cached state, so any blaze3d draw AFTER a NanoVG draw can render
        // black. We therefore draw EVERY panel's blaze3d BACKGROUND first (SDF fill/border, frosted blur,
        // and the inventory's 3D item models) while the state is clean, then EVERY panel's NanoVG TEXT
        // last — after which nothing blaze3d runs in this pass, so the corruption is harmless. (Previously
        // only the scoreboard could be NanoVG, and only by being drawn dead last.)
        // NOTE: the ESP overhead is NOT drawn here — it's a true world-space billboard from RenderUtils'
        // RenderEvent.Last pass; this screen-space pass only owns the 2D HUD panels.
        FloydInventoryHud.renderAtWorldEnd(PanelPhase.BACKGROUND)
        resetBetweenPanels()
        FloydDayTrackerModule.renderAtWorldEnd(PanelPhase.BACKGROUND)
        resetBetweenPanels()
        FloydCustomScoreboard.renderAtWorldEnd(PanelPhase.BACKGROUND)
        resetBetweenPanels()

        FloydInventoryHud.renderAtWorldEnd(PanelPhase.TEXT)
        FloydDayTrackerModule.renderAtWorldEnd(PanelPhase.TEXT)
        FloydCustomScoreboard.renderAtWorldEnd(PanelPhase.TEXT)
        resetBetweenPanels()

        modelView.popMatrix()

        RenderSystem.outputColorTextureOverride = null
        RenderSystem.outputDepthTextureOverride = null
        boundFbo = 0

        // Restore the GL state NanoVG / the render pass leave dirty so MC's next draw is clean.
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0)
        GlStateManager._disableScissorTest()
        GlStateManager._depthMask(true)
        GlStateManager._colorMask(true, true, true, true)
        GlStateManager._disableDepthTest()
        GlStateManager._disableCull()
        GlStateManager._enableBlend()
        GlStateManager._blendFuncSeparate(770, 771, 1, 0)
        GL33C.glActiveTexture(GL13C.GL_TEXTURE0)
    }
}
