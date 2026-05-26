package floydaddons.not.dogshit.client.utils.ui.rendering

import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlDevice
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Matrix3x2f
import org.joml.Vector2f
import org.lwjgl.opengl.GL33C
import kotlin.math.roundToInt

class NVGPIPRenderer(vertexConsumers: MultiBufferSource.BufferSource) : PictureInPictureRenderer<NVGPIPRenderer.NVGRenderState>(vertexConsumers) {

    override fun renderToTexture(state: NVGRenderState, poseStack: PoseStack) {
        val colorTex = RenderSystem.outputColorTextureOverride ?: return
        val bufferManager = (RenderSystem.getDevice() as? GlDevice)?.directStateAccess() ?: return
        val glDepthTex = (RenderSystem.outputDepthTextureOverride?.texture() as? GlTexture) ?: return

        val (width, height) = colorTex.let { it.getWidth(0) to it.getHeight(0) }
        (colorTex.texture() as? GlTexture)?.getFbo(bufferManager, glDepthTex)?.apply {
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, this)
            GlStateManager._viewport(0, 0, width, height)
        }

        GL33C.glBindSampler(0, 0)
        NVGRenderer.beginFrame(width.toFloat(), height.toFloat())
        if (state.renderScale != 1f) NVGRenderer.scale(state.renderScale, state.renderScale)
        state.renderContent()
        NVGRenderer.endFrame()

        GlStateManager._disableDepthTest()
        GlStateManager._disableCull()
        GlStateManager._enableBlend()
        GlStateManager._blendFuncSeparate(770, 771, 1, 0)
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
            val screenLeft = minOf(p0.x, p1.x).roundToInt()
            val screenTop = minOf(p0.y, p1.y).roundToInt()
            val screenWidth = maxOf(p0.x, p1.x).roundToInt() - screenLeft
            val screenHeight = maxOf(p0.y, p1.y).roundToInt() - screenTop
            val renderScale = pose.transformDirection(Vector2f(1f, 0f)).length() * renderScaleMultiplier
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

