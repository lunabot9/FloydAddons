package gg.floyd.features.impl.misc

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.systems.RenderSystem
import gg.floyd.utils.render.PooledPicturePIPRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import org.joml.Matrix3x2f
import org.joml.Vector2f
import kotlin.math.roundToInt

class FloydMenuShaderPIPRenderer(
    //? if >=26.2 {
    /*ignored: Any?,
    *///?} else {
    private val menuBufferSource: MultiBufferSource.BufferSource,
    //?}
) : PooledPicturePIPRenderer<FloydMenuShaderPIPRenderer.State>(
    //? if >=26.2 {
    /*ignored
    *///?} else {
    menuBufferSource
    //?}
) {

    override fun getRenderStateClass(): Class<State> = State::class.java
    //? if <26.2 {
    override fun pipSampler(): GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    //?}
    //? if >=26.2 {
    /*// 26.2 uses the base renderer behavior here.
    *///?} else {
    // This renderer writes directly with GL and has no vertices of its own in bufferSource. The
    // source is shared with the rest of the GUI, so flushing it while this PIP target is bound would
    // steal vanilla widget/font batches from the main framebuffer and make them flicker black.
    override fun shouldEndBatchAfterRenderContent(): Boolean = false
    //?}

    override fun renderContent(state: State, poseStack: PoseStack) {
        FloydMenuVideoBackground.renderIntoCurrentPip(state.width, state.height, state.time)
    }

    override fun getTextureLabel(): String = "FloydAddons Menu Shader PIP"

    class State(
        private val left: Int,
        private val top: Int,
        val width: Int,
        val height: Int,
        val time: Float,
        private val scissorArea: ScreenRectangle?,
        private val bounds: ScreenRectangle?,
    ) : PictureInPictureRenderState {
        override fun x0() = left
        override fun y0() = top
        override fun x1() = left + width
        override fun y1() = top + height
        override fun scale() = 1f
        override fun scissorArea() = scissorArea
        override fun bounds() = bounds
    }

    companion object {
        fun submit(context: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, time: Float) {
            val scissor = context.scissorStack.peek()
            val pose = Matrix3x2f(context.pose())
            val p0 = pose.transformPosition(Vector2f(x0.toFloat(), y0.toFloat()))
            val p1 = pose.transformPosition(Vector2f(x1.toFloat(), y1.toFloat()))

            val screenLeft = minOf(p0.x, p1.x).roundToInt()
            val screenTop = minOf(p0.y, p1.y).roundToInt()
            val screenWidth = maxOf(p0.x, p1.x).roundToInt() - screenLeft
            val screenHeight = maxOf(p0.y, p1.y).roundToInt() - screenTop
            if (screenWidth <= 0 || screenHeight <= 0) return

            val screenRect = ScreenRectangle(screenLeft, screenTop, screenWidth, screenHeight)
            val bounds = if (scissor != null) scissor.intersection(screenRect) else screenRect

            context.guiRenderState.addPicturesInPictureState(
                State(screenLeft, screenTop, screenWidth, screenHeight, time, scissor, bounds)
            )
        }
    }
}
