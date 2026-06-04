package gg.floyd.utils.render

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import net.minecraft.client.renderer.DynamicUniformStorage
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.*
import java.util.*
import kotlin.math.roundToInt

class RoundRectPIPRenderer(bufferSource: MultiBufferSource.BufferSource)
    : PooledPicturePIPRenderer<RoundRectPIPRenderer.State>(bufferSource) {

    override fun getRenderStateClass(): Class<State> = State::class.java

    override fun renderContent(state: State, poseStack: PoseStack) {
        val w = state.width * state.scale
        val h = state.height * state.scale

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        builder.addVertex(0f, 0f, 0f).setColor(state.topLeftColor)
        builder.addVertex(0f, h, 0f).setColor(state.bottomLeftColor)
        builder.addVertex(w, h, 0f).setColor(state.bottomRightColor)
        builder.addVertex(w, 0f, 0f).setColor(state.topRightColor)
        val mesh = builder.buildOrThrow()

        val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrix(), Vector4f(1f, 1f, 1f, 1f), Vector3f(), Matrix4f()
        )

        val uniformBuffer = uniformStorage.writeUniform { buffer ->
            Std140Builder.intoBuffer(buffer)
                .putVec4(w * 0.5f, h * 0.5f, w, h)                                          // u_Rect
                .putVec4(state.topLeftRadius * state.scale, state.topRightRadius * state.scale, state.bottomRightRadius * state.scale, state.bottomLeftRadius * state.scale) // u_Radii
                .putVec4(state.outlineTopLeftRed, state.outlineTopLeftGreen, state.outlineTopLeftBlue, state.outlineTopLeftAlpha)
                .putVec4(state.outlineTopRightRed, state.outlineTopRightGreen, state.outlineTopRightBlue, state.outlineTopRightAlpha)
                .putVec4(state.outlineBottomRightRed, state.outlineBottomRightGreen, state.outlineBottomRightBlue, state.outlineBottomRightAlpha)
                .putVec4(state.outlineBottomLeftRed, state.outlineBottomLeftGreen, state.outlineBottomLeftBlue, state.outlineBottomLeftAlpha)
                .putVec4(state.outlineWidth * state.scale, 0f, 0f, 0f)                       // u_OutlineWidth (std140 padded)
        }

        val vertexBuffer = CustomRenderPipelines.PIPELINE_ROUND_RECT.vertexFormat.uploadImmediateVertexBuffer(mesh.vertexBuffer())
        val indexStorage = RenderSystem.getSequentialBuffer(mesh.drawState().mode())
        val indexBuffer = indexStorage.getBuffer(mesh.drawState().indexCount())
        val renderTarget = Minecraft.getInstance().mainRenderTarget

        mesh.use {
            (RenderSystem.outputColorTextureOverride ?: renderTarget.colorTextureView)?.let { gpuTextureView ->
                RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    { "FloydAddons Rounded Rectangle" }, gpuTextureView, OptionalInt.empty(),
                    if (renderTarget.useDepth) Objects.requireNonNullElse(RenderSystem.outputDepthTextureOverride, renderTarget.depthTextureView) else null,
                    OptionalDouble.empty()
                )
            }?.use { pass ->
                pass.setPipeline(CustomRenderPipelines.PIPELINE_ROUND_RECT)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("DynamicTransforms", dynamicTransforms)
                pass.setUniform("u", uniformBuffer)
                pass.setVertexBuffer(0, vertexBuffer)
                pass.setIndexBuffer(indexBuffer, indexStorage.type())
                pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
            }
        }
    }

    override fun getTextureLabel(): String = "FloydAddons Rounded Rectangle PIP"

    class State(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val topLeftColor: Int,
        val topRightColor: Int,
        val bottomRightColor: Int,
        val bottomLeftColor: Int,
        val topLeftRadius: Float,
        val topRightRadius: Float,
        val bottomRightRadius: Float,
        val bottomLeftRadius: Float,
        val outlineTopLeftColor: Int,
        val outlineTopRightColor: Int,
        val outlineBottomRightColor: Int,
        val outlineBottomLeftColor: Int,
        val outlineWidth: Float,
        private val scissorArea: ScreenRectangle?,
        private val bounds: ScreenRectangle?
    ) : PictureInPictureRenderState {

        val scale = Minecraft.getInstance().window.guiScale.toFloat()

        val outlineTopLeftRed = (outlineTopLeftColor shr 16 and 0xFF) / 255f
        val outlineTopLeftGreen = (outlineTopLeftColor shr 8 and 0xFF) / 255f
        val outlineTopLeftBlue = (outlineTopLeftColor and 0xFF) / 255f
        val outlineTopLeftAlpha = (outlineTopLeftColor shr 24 and 0xFF) / 255f
        val outlineTopRightRed = (outlineTopRightColor shr 16 and 0xFF) / 255f
        val outlineTopRightGreen = (outlineTopRightColor shr 8 and 0xFF) / 255f
        val outlineTopRightBlue = (outlineTopRightColor and 0xFF) / 255f
        val outlineTopRightAlpha = (outlineTopRightColor shr 24 and 0xFF) / 255f
        val outlineBottomRightRed = (outlineBottomRightColor shr 16 and 0xFF) / 255f
        val outlineBottomRightGreen = (outlineBottomRightColor shr 8 and 0xFF) / 255f
        val outlineBottomRightBlue = (outlineBottomRightColor and 0xFF) / 255f
        val outlineBottomRightAlpha = (outlineBottomRightColor shr 24 and 0xFF) / 255f
        val outlineBottomLeftRed = (outlineBottomLeftColor shr 16 and 0xFF) / 255f
        val outlineBottomLeftGreen = (outlineBottomLeftColor shr 8 and 0xFF) / 255f
        val outlineBottomLeftBlue = (outlineBottomLeftColor and 0xFF) / 255f
        val outlineBottomLeftAlpha = (outlineBottomLeftColor shr 24 and 0xFF) / 255f

        override fun x0() = x
        override fun y0() = y
        override fun x1() = x + width
        override fun y1() = y + height
        override fun scale() = 1f
        override fun scissorArea() = scissorArea
        override fun bounds() = bounds

        fun visuallyEquals(other: State?): Boolean {
            if (other == null) return false
            return width == other.width &&
                height == other.height &&
                topLeftColor == other.topLeftColor &&
                topRightColor == other.topRightColor &&
                bottomRightColor == other.bottomRightColor &&
                bottomLeftColor == other.bottomLeftColor &&
                topLeftRadius == other.topLeftRadius &&
                topRightRadius == other.topRightRadius &&
                bottomRightRadius == other.bottomRightRadius &&
                bottomLeftRadius == other.bottomLeftRadius &&
                outlineTopLeftColor == other.outlineTopLeftColor &&
                outlineTopRightColor == other.outlineTopRightColor &&
                outlineBottomRightColor == other.outlineBottomRightColor &&
                outlineBottomLeftColor == other.outlineBottomLeftColor &&
                outlineWidth == other.outlineWidth &&
                scale == other.scale
        }
    }

    companion object {
        private val uniformStorage = DynamicUniformStorage<DynamicUniformStorage.DynamicUniform>(
            "FloydAddons Rounded Rectangle UBO",
            Std140SizeCalculator()
                .putVec4() // u_Rect
                .putVec4() // u_Radii
                .putVec4() // u_OutlineTopLeftColor
                .putVec4() // u_OutlineTopRightColor
                .putVec4() // u_OutlineBottomRightColor
                .putVec4() // u_OutlineBottomLeftColor
                .putVec4() // u_OutlineWidth (std140 padded)
                .get(),
            4
        )

        fun clear() = uniformStorage.endFrame()

        // Screen-sized ortho for the inline (no-PIP) draw path: maps (0,0)-(fbW,fbH) framebuffer pixels.
        private val inlineProjection = CachedOrthoProjectionMatrixBuffer("FloydAddons RoundRect Inline", -1000f, 1000f, true)

        private fun rf(c: Int) = (c ushr 16 and 0xFF) / 255f
        private fun gf(c: Int) = (c ushr 8 and 0xFF) / 255f
        private fun bf(c: Int) = (c and 0xFF) / 255f
        private fun af(c: Int) = (c ushr 24 and 0xFF) / 255f

        /**
         * Draws a rounded rect (fill + per-corner SDF border) DIRECTLY to the currently-bound main
         * framebuffer, in framebuffer-pixel coordinates, with no PIP / offscreen texture. For the
         * post-HUD immediate panel pass: panels drawn this way composite in painter's order and never
         * clobber each other. The caller must have the main framebuffer bound. [x],[y],[w],[h],
         * radii and [outlineWidth] are final framebuffer pixels.
         */
        fun drawInline(
            x: Float, y: Float, w: Float, h: Float,
            fillTL: Int, fillTR: Int, fillBR: Int, fillBL: Int,
            radTL: Float, radTR: Float, radBR: Float, radBL: Float,
            outTL: Int, outTR: Int, outBR: Int, outBL: Int,
            outlineWidth: Float
        ) {
            if (w <= 0f || h <= 0f) return
            val target = Minecraft.getInstance().mainRenderTarget
            RenderSystem.setProjectionMatrix(
                inlineProjection.getBuffer(target.width.toFloat(), target.height.toFloat()),
                ProjectionType.ORTHOGRAPHIC
            )

            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            builder.addVertex(0f, 0f, 0f).setColor(fillTL)
            builder.addVertex(0f, h, 0f).setColor(fillBL)
            builder.addVertex(w, h, 0f).setColor(fillBR)
            builder.addVertex(w, 0f, 0f).setColor(fillTR)
            val mesh = builder.buildOrThrow()

            // Mesh is local (0,0)-(w,h); the modelview translates it to the panel's framebuffer origin.
            val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                Matrix4f().translation(x, y, 0f), Vector4f(1f, 1f, 1f, 1f), Vector3f(), Matrix4f()
            )

            val uniformBuffer = uniformStorage.writeUniform { buffer ->
                Std140Builder.intoBuffer(buffer)
                    .putVec4(w * 0.5f, h * 0.5f, w, h)
                    .putVec4(radTL, radTR, radBR, radBL)
                    .putVec4(rf(outTL), gf(outTL), bf(outTL), af(outTL))
                    .putVec4(rf(outTR), gf(outTR), bf(outTR), af(outTR))
                    .putVec4(rf(outBR), gf(outBR), bf(outBR), af(outBR))
                    .putVec4(rf(outBL), gf(outBL), bf(outBL), af(outBL))
                    .putVec4(outlineWidth, 0f, 0f, 0f)
            }

            val vertexBuffer = CustomRenderPipelines.PIPELINE_ROUND_RECT.vertexFormat.uploadImmediateVertexBuffer(mesh.vertexBuffer())
            val indexStorage = RenderSystem.getSequentialBuffer(mesh.drawState().mode())
            val indexBuffer = indexStorage.getBuffer(mesh.drawState().indexCount())

            mesh.use {
                target.colorTextureView?.let { gpuTextureView ->
                    RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        { "FloydAddons RoundRect Inline" }, gpuTextureView, OptionalInt.empty(),
                        if (target.useDepth) target.depthTextureView else null,
                        OptionalDouble.empty()
                    )
                }?.use { pass ->
                    pass.setPipeline(CustomRenderPipelines.PIPELINE_ROUND_RECT)
                    RenderSystem.bindDefaultUniforms(pass)
                    pass.setUniform("DynamicTransforms", dynamicTransforms)
                    pass.setUniform("u", uniformBuffer)
                    pass.setVertexBuffer(0, vertexBuffer)
                    pass.setIndexBuffer(indexBuffer, indexStorage.type())
                    pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
                }
            }
        }

        /**
         * WORLD-MVP variant of [drawInline] for a 3D billboard (the ESP overhead nameplate). Unlike
         * drawInline it does NOT override the projection — it leaves the bound world ProjMat (proj×view)
         * intact and passes [modelView] (the billboard basis translated to the rect's local top-left) as the
         * DynamicTransforms ModelViewMat. The SDF rounded mask is computed entirely in local f_Position vs
         * u_Rect space, so it is byte-identical to drawInline; only the transform differs.
         */
        fun drawWorld(
            modelView: Matrix4f,
            w: Float, h: Float,
            fillTL: Int, fillTR: Int, fillBR: Int, fillBL: Int,
            radTL: Float, radTR: Float, radBR: Float, radBL: Float,
            outTL: Int, outTR: Int, outBR: Int, outBL: Int,
            outlineWidth: Float
        ) {
            if (w <= 0f || h <= 0f) return

            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            builder.addVertex(0f, 0f, 0f).setColor(fillTL)
            builder.addVertex(0f, h, 0f).setColor(fillBL)
            builder.addVertex(w, h, 0f).setColor(fillBR)
            builder.addVertex(w, 0f, 0f).setColor(fillTR)
            val mesh = builder.buildOrThrow()

            val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                modelView, Vector4f(1f, 1f, 1f, 1f), Vector3f(), Matrix4f()
            )

            val uniformBuffer = uniformStorage.writeUniform { buffer ->
                Std140Builder.intoBuffer(buffer)
                    .putVec4(w * 0.5f, h * 0.5f, w, h)
                    .putVec4(radTL, radTR, radBR, radBL)
                    .putVec4(rf(outTL), gf(outTL), bf(outTL), af(outTL))
                    .putVec4(rf(outTR), gf(outTR), bf(outTR), af(outTR))
                    .putVec4(rf(outBR), gf(outBR), bf(outBR), af(outBR))
                    .putVec4(rf(outBL), gf(outBL), bf(outBL), af(outBL))
                    .putVec4(outlineWidth, 0f, 0f, 0f)
            }

            val vertexBuffer = CustomRenderPipelines.PIPELINE_ROUND_RECT.vertexFormat.uploadImmediateVertexBuffer(mesh.vertexBuffer())
            val indexStorage = RenderSystem.getSequentialBuffer(mesh.drawState().mode())
            val indexBuffer = indexStorage.getBuffer(mesh.drawState().indexCount())
            val target = Minecraft.getInstance().mainRenderTarget

            mesh.use {
                (RenderSystem.outputColorTextureOverride ?: target.colorTextureView)?.let { gpuTextureView ->
                    RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        { "FloydAddons RoundRect World" }, gpuTextureView, OptionalInt.empty(),
                        if (target.useDepth) Objects.requireNonNullElse(RenderSystem.outputDepthTextureOverride, target.depthTextureView) else null,
                        OptionalDouble.empty()
                    )
                }?.use { pass ->
                    pass.setPipeline(CustomRenderPipelines.PIPELINE_ROUND_RECT)
                    RenderSystem.bindDefaultUniforms(pass)
                    pass.setUniform("DynamicTransforms", dynamicTransforms)
                    pass.setUniform("u", uniformBuffer)
                    pass.setVertexBuffer(0, vertexBuffer)
                    pass.setIndexBuffer(indexBuffer, indexStorage.type())
                    pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
                }
            }
        }

        fun submit(
            context: GuiGraphics,
            x0: Int, y0: Int, x1: Int, y1: Int,
            topLeftColor: Int, topRightColor: Int, bottomRightColor: Int, bottomLeftColor: Int,
            topLeftRadius: Float, topRightRadius: Float, bottomRightRadius: Float, bottomLeftRadius: Float,
            outlineTopLeftColor: Int, outlineTopRightColor: Int, outlineBottomRightColor: Int, outlineBottomLeftColor: Int,
            outlineWidth: Float
        ) {
            val scissor = context.scissorStack.peek()
            val pose = Matrix3x2f(context.pose())

            val p0 = pose.transformPosition(Vector2f(x0.toFloat(), y0.toFloat()))
            val p1 = pose.transformPosition(Vector2f(x1.toFloat(), y1.toFloat()))

            val screenLeft  = minOf(p0.x, p1.x).roundToInt()
            val screenTop   = minOf(p0.y, p1.y).roundToInt()
            val screenW     = maxOf(p0.x, p1.x).roundToInt() - screenLeft
            val screenH     = maxOf(p0.y, p1.y).roundToInt() - screenTop

            val poseScale   = pose.transformDirection(Vector2f(1f, 0f)).length()

            val screenRect = ScreenRectangle(screenLeft, screenTop, screenW, screenH)
            val bounds = if (scissor != null) scissor.intersection(screenRect) else screenRect

            context.guiRenderState.submitPicturesInPictureState(
                State(
                    screenLeft, screenTop, screenW, screenH,
                    topLeftColor, topRightColor, bottomRightColor, bottomLeftColor,
                    topLeftRadius * poseScale, topRightRadius * poseScale,
                    bottomRightRadius * poseScale, bottomLeftRadius * poseScale,
                    outlineTopLeftColor, outlineTopRightColor, outlineBottomRightColor, outlineBottomLeftColor, outlineWidth * poseScale,
                    scissor, bounds
                )
            )
        }
    }
}
