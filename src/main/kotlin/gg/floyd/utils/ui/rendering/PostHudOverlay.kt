package gg.floyd.utils.ui.rendering

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import gg.floyd.FloydAddonsMod.mc
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL33C

/**
 * The single immediate post-HUD render pass for Floyd's custom panels.
 *
 * Runs at the RETURN of `GameRenderer.render`, the first frame point where the main framebuffer holds
 * the world + the fully-composited vanilla HUD. Panels [enqueue] a draw during the deferred HUD pass
 * (computing their framebuffer geometry from the live pose), and this pass replays them — directly to
 * the main framebuffer, in painter's order, with NO per-panel offscreen texture. That is what makes
 * overlapping Floyd panels rock-constant: a panel's frosted blur correctly samples world + HUD + every
 * earlier panel, borders never wash out, and item icons can't clobber each other (no per-panel PIPs).
 *
 * Each queued draw is a self-contained closure: a rounded-rect/blur SDF draw (blaze3d render pass to
 * the main color view) or a NanoVG text draw (raw GL to the bound FBO). The FBO is re-bound before
 * every closure so a blaze3d render pass that retargets internally can't leave the next NanoVG draw
 * pointed at the wrong framebuffer.
 */
object PostHudOverlay {

    private val queue = ArrayList<() -> Unit>()

    private val screenProjection = CachedOrthoProjectionMatrixBuffer("FloydAddons PostHUD", -1000f, 1000f, true)

    /** Enqueue a panel draw (framebuffer-pixel coords baked in) for this frame's post-HUD pass. */
    fun enqueue(draw: () -> Unit) {
        queue.add(draw)
    }

    /**
     * Sets the orthographic projection over the whole main framebuffer (0,0)-(width,height) in pixels.
     * Call inside an enqueued closure before any blaze3d/vanilla draw (item icons, mc.font fallbacks)
     * that relies on [RenderSystem.getProjectionMatrix] rather than carrying its own.
     */
    fun applyScreenProjection() {
        val t = mc.mainRenderTarget
        RenderSystem.setProjectionMatrix(
            screenProjection.getBuffer(t.width.toFloat(), t.height.toFloat()),
            ProjectionType.ORTHOGRAPHIC
        )
    }

    fun render() {
        if (queue.isEmpty()) return
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) {
            queue.clear()
            return
        }
        RenderSystem.assertOnRenderThread()

        val target = mc.mainRenderTarget
        val dsa = (RenderSystem.getDevice() as? GlDevice)?.directStateAccess()
        val colorTex = target.colorTexture as? GlTexture
        if (dsa == null || colorTex == null) {
            queue.clear()
            return
        }
        val fbo = colorTex.getFbo(dsa, target.depthTexture as? GlTexture)

        // Redirect vanilla blaze3d rendering (mc.font glyphs, item models) to the main framebuffer for
        // this pass — the same RenderSystem override PictureInPictureRenderer uses to retarget vanilla
        // draws. NanoVG (raw GL) and our SDF render pass ignore it; mc.font / items respect it.
        RenderSystem.outputColorTextureOverride = target.colorTextureView
        RenderSystem.outputDepthTextureOverride = target.depthTextureView

        for (draw in queue) {
            // Re-bind the main framebuffer before each panel: a blaze3d SDF render pass can retarget
            // internally, so a following NanoVG draw must be re-pointed at the main FBO.
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, fbo)
            GlStateManager._viewport(0, 0, target.width, target.height)
            GL33C.glBindSampler(0, 0)
            draw()
        }
        queue.clear()

        RenderSystem.outputColorTextureOverride = null
        RenderSystem.outputDepthTextureOverride = null

        // Restore the GL state NanoVG / the render pass leave dirty so MC's next frame starts clean.
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
