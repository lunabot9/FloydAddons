package gg.floyd.utils.ui.rendering

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.impl.render.FloydCustomScoreboard
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
object PostHudOverlay {

    private val screenProjection = CachedOrthoProjectionMatrixBuffer("FloydAddons PostHUD", -1000f, 1000f, true)
    private var boundFbo = 0

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
        bindMainFbo()

        // Draw each Floyd panel directly, in painter's order. (Inventory / day-tracker / ESP overhead
        // migrate here next.)
        FloydCustomScoreboard.renderAtWorldEnd()

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
