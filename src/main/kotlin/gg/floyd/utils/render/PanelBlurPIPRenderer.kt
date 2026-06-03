package gg.floyd.utils.render

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.DynamicUniformStorage
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.*
import java.util.*
import kotlin.math.roundToInt

/**
 * Renders a per-panel frosted blur: samples the main framebuffer behind the panel and box/gaussian
 * blurs it inside a rounded-rect mask, drawn through [CustomRenderPipelines.PIPELINE_PANEL_BLUR].
 *
 * Mirrors [RoundRectPIPRenderer]'s command-encoder / Std140 UBO pattern, but binds the main render
 * target's color texture as the `In` sampler. The blur is rendered into the PIP's own offscreen
 * texture (via `RenderSystem.outputColorTextureOverride`) while sampling the main target — different
 * targets, so there is no feedback loop. The output alpha is the rounded mask so the existing
 * translucent panel fill + border composite cleanly on top.
 */
class PanelBlurPIPRenderer(bufferSource: MultiBufferSource.BufferSource)
    : PooledPicturePIPRenderer<PanelBlurPIPRenderer.State>(bufferSource) {

    override fun getRenderStateClass(): Class<State> = State::class.java

    override fun renderContent(state: State, poseStack: PoseStack) {
        val w = state.width * state.scale
        val h = state.height * state.scale

        val mainTarget = Minecraft.getInstance().mainRenderTarget
        val screenW = mainTarget.width.toFloat()
        val screenH = mainTarget.height.toFloat()
        val originX = state.x * state.scale
        val originY = state.y * state.scale

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        builder.addVertex(0f, 0f, 0f).setColor(-1)
        builder.addVertex(0f, h, 0f).setColor(-1)
        builder.addVertex(w, h, 0f).setColor(-1)
        builder.addVertex(w, 0f, 0f).setColor(-1)
        val mesh = builder.buildOrThrow()

        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrix(), Vector4f(1f, 1f, 1f, 1f), Vector3f(), Matrix4f()
        )

        val uniformBuffer = uniformStorage.writeUniform { buffer ->
            Std140Builder.intoBuffer(buffer)
                .putVec4(w * 0.5f, h * 0.5f, w, h)                                                          // u_Rect
                .putVec4(state.topLeftRadius * state.scale, state.topRightRadius * state.scale, state.bottomRightRadius * state.scale, state.bottomLeftRadius * state.scale) // u_Radii
                .putVec4(screenW, screenH, originX, originY)                                                // u_Screen
                .putVec4(state.blurRadius, if (state.boxKernel) 1f else 0f, 0f, 0f)                         // u_Blur
        }

        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        val vertexBuffer = CustomRenderPipelines.PIPELINE_PANEL_BLUR.vertexFormat.uploadImmediateVertexBuffer(mesh.vertexBuffer())
        val indexStorage = RenderSystem.getSequentialBuffer(mesh.drawState().mode())
        val indexBuffer = indexStorage.getBuffer(mesh.drawState().indexCount())
        val renderTarget = mainTarget

        mesh.use {
            (RenderSystem.outputColorTextureOverride ?: renderTarget.colorTextureView)?.let { gpuTextureView ->
                RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    { "FloydAddons Panel Blur" }, gpuTextureView, OptionalInt.empty(),
                    if (renderTarget.useDepth) Objects.requireNonNullElse(RenderSystem.outputDepthTextureOverride, renderTarget.depthTextureView) else null,
                    OptionalDouble.empty()
                )
            }?.use { pass ->
                pass.setPipeline(CustomRenderPipelines.PIPELINE_PANEL_BLUR)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("DynamicTransforms", dynamicTransforms)
                pass.setUniform("u", uniformBuffer)
                pass.bindTexture("Sampler0", mainTarget.colorTextureView, sampler)
                pass.setVertexBuffer(0, vertexBuffer)
                pass.setIndexBuffer(indexBuffer, indexStorage.type())
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
            }
        }
    }

    override fun getTextureLabel(): String = "FloydAddons Panel Blur PIP"

    class State(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val topLeftRadius: Float,
        val topRightRadius: Float,
        val bottomRightRadius: Float,
        val bottomLeftRadius: Float,
        val blurRadius: Float,
        val boxKernel: Boolean,
        private val scissorArea: ScreenRectangle?,
        private val bounds: ScreenRectangle?
    ) : PictureInPictureRenderState {

        val scale = Minecraft.getInstance().window.guiScale.toFloat()

        override fun x0() = x
        override fun y0() = y
        override fun x1() = x + width
        override fun y1() = y + height
        override fun scale() = 1f
        override fun scissorArea() = scissorArea
        override fun bounds() = bounds
    }

    companion object {
        private val uniformStorage = DynamicUniformStorage<DynamicUniformStorage.DynamicUniform>(
            "FloydAddons Panel Blur UBO",
            Std140SizeCalculator()
                .putVec4() // u_Rect
                .putVec4() // u_Radii
                .putVec4() // u_Screen
                .putVec4() // u_Blur
                .get(),
            4
        )

        fun clear() = uniformStorage.endFrame()

        fun submit(
            context: GuiGraphics,
            x0: Int, y0: Int, x1: Int, y1: Int,
            topLeftRadius: Float, topRightRadius: Float, bottomRightRadius: Float, bottomLeftRadius: Float,
            blurRadius: Float, boxKernel: Boolean
        ) {
            val scissor = context.scissorStack.peek()
            val pose = Matrix3x2f(context.pose())

            val p0 = pose.transformPosition(Vector2f(x0.toFloat(), y0.toFloat()))
            val p1 = pose.transformPosition(Vector2f(x1.toFloat(), y1.toFloat()))

            val screenLeft = minOf(p0.x, p1.x).roundToInt()
            val screenTop = minOf(p0.y, p1.y).roundToInt()
            val screenW = maxOf(p0.x, p1.x).roundToInt() - screenLeft
            val screenH = maxOf(p0.y, p1.y).roundToInt() - screenTop

            // Skip the framebuffer blur where the panel overlaps the vanilla bottom-center HUD (hotbar,
            // health, hunger, xp). A PIP blur can only sample the WORLD (the GUI isn't in the framebuffer
            // yet when PIPs render), so over the bright hotbar the dark world reads as a black box. Dropping
            // the blur there leaves the panel's translucent fill + border, which composites cleanly over the
            // hotbar — "pass through" the hotbar instead of covering it with a dark blur. (screen* are in
            // gui-scaled coords, matching guiScaledWidth/Height, because the HUD pose prescales by 1/guiScale.)
            val window = Minecraft.getInstance().window
            val centerX = window.guiScaledWidth / 2
            val hudLeft = centerX - 95
            val hudRight = centerX + 95
            val hudTop = window.guiScaledHeight - 42
            val overlapsBottomHud = screenLeft < hudRight && screenLeft + screenW > hudLeft &&
                screenTop < window.guiScaledHeight && screenTop + screenH > hudTop
            if (overlapsBottomHud) return

            val poseScale = pose.transformDirection(Vector2f(1f, 0f)).length()

            val screenRect = ScreenRectangle(screenLeft, screenTop, screenW, screenH)
            val bounds = if (scissor != null) scissor.intersection(screenRect) else screenRect

            context.guiRenderState.submitPicturesInPictureState(
                State(
                    screenLeft, screenTop, screenW, screenH,
                    topLeftRadius * poseScale, topRightRadius * poseScale,
                    bottomRightRadius * poseScale, bottomLeftRadius * poseScale,
                    blurRadius, boxKernel,
                    scissor, bounds
                )
            )
        }
    }
}
