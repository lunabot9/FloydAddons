package gg.floyd.utils.render

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
//? if <26.2 {
import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
//?}
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
//? if <26.2 {
import com.mojang.blaze3d.vertex.Tesselator
//?}
import com.mojang.blaze3d.vertex.VertexFormat
import gg.floyd.FloydAddonsMod
//? if <26.2 {
import gg.floyd.utils.ui.rendering.DirectStateAccessCompat
//?}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.*
import gg.floyd.utils.ui.rendering.PostHudOverlay
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import net.minecraft.client.renderer.DynamicUniformStorage
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.*
//? if <26.2 {
import org.lwjgl.opengl.GL33C
//?}
import java.util.*
import kotlin.math.roundToInt

/**
 * Renders a per-panel frosted blur: samples the main framebuffer behind the panel and box/gaussian
 * blurs it inside a rounded-rect mask.
 *
 * PIP-backed panels use a direct OpenGL pass on 26.1 because its command encoder accepts the
 * equivalent render pass without writing to the attachment. The post-HUD fallback still uses
 * [CustomRenderPipelines.PIPELINE_PANEL_BLUR]. Both paths write into a panel texture while sampling
 * the distinct main target, avoiding feedback; their rounded output alpha lets the existing tinted
 * fill and border composite cleanly on top.
 */
class PanelBlurPIPRenderer(bufferSource: MultiBufferSource.BufferSource)
    : PooledPicturePIPRenderer<PanelBlurPIPRenderer.State>(bufferSource) {

    override fun getRenderStateClass(): Class<State> = State::class.java

    override fun renderContent(state: State, poseStack: PoseStack) {
        // 26.2 uses the translucent panel fill as the safe backend-neutral fallback.
        //? if <26.2 {
        val output = RenderSystem.outputColorTextureOverride ?: return
        val outputTexture = output.texture() as? GlTexture ?: return
        val directStateAccess = DirectStateAccessCompat.directStateAccess() ?: return
        val outputDepth = RenderSystem.outputDepthTextureOverride?.texture() as? GlTexture
        val previousFramebuffer = GL33C.glGetInteger(GL33C.GL_FRAMEBUFFER_BINDING)
        val previousViewport = IntArray(4).also { GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, it) }
        val w = (state.width * state.scale).roundToInt()
        val h = (state.height * state.scale).roundToInt()

        try {
            val outputFramebuffer = outputTexture.getFbo(directStateAccess, outputDepth)
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0)
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, outputFramebuffer)
            GL33C.glViewport(0, 0, w, h)
            drawRawBlur(
                state.x * state.scale,
                state.y * state.scale,
                w,
                h,
                state.topLeftRadius * state.scale,
                state.topRightRadius * state.scale,
                state.bottomRightRadius * state.scale,
                state.bottomLeftRadius * state.scale,
                state.blurRadius,
                state.boxKernel,
            )
        } finally {
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0)
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, previousFramebuffer)
            GL33C.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
        }
        //?}
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

        /**
         * Paints the v2.1.0 framebuffer blur directly into the PIP texture currently selected by
         * [RenderSystem.outputColorTextureOverride]. Minecraft 26.1.2 drops the separate blur-PIP
         * blit, so HUD panels call this before NanoVG composites their fill and border in the same
         * working PIP slot.
         */
        fun drawIntoCurrentPip(
            screenX: Int,
            screenY: Int,
            width: Int,
            height: Int,
            cornerRadius: Float,
            blurRadius: Float,
            boxKernel: Boolean,
        ) {
            //? if >=26.2 {
            /*return
            *///?}
            //? if <26.2 {
            if (width <= 0 || height <= 0 || blurRadius < 0.5f) return
            if (RenderSystem.outputColorTextureOverride == null) return
            val guiScale = Minecraft.getInstance().window.guiScale.toFloat()
            drawRawBlur(
                screenX * guiScale,
                screenY * guiScale,
                width,
                height,
                cornerRadius,
                cornerRadius,
                cornerRadius,
                cornerRadius,
                blurRadius,
                boxKernel,
            )
            //?}
        }

        private val inlineProjection = CachedOrthoProjectionMatrixBuffer("FloydAddons PanelBlur Inline", -1000f, 1000f, true)


        /**
         * Draws the frosted blur DIRECTLY to the main framebuffer (the post-HUD pass), in framebuffer
         * pixels. Samples [PostHudOverlay.blurSourceView] (a per-frame snapshot of the framebuffer) so it
         * never reads the same texture it writes. No-op until the snapshot exists this frame.
         */
        fun drawInline(
            x: Float, y: Float, w: Float, h: Float,
            radTL: Float, radTR: Float, radBR: Float, radBL: Float,
            blurRadius: Float, boxKernel: Boolean
        ) {
            //? if >=26.2 {
            /*return
            *///?}
            //? if <26.2 {
            if (w <= 0f || h <= 0f) return
            val source = PostHudOverlay.blurSourceView() ?: return
            val target = Minecraft.getInstance().mainRenderTarget
            RenderSystem.setProjectionMatrix(
                inlineProjection.getBuffer(target.width.toFloat(), target.height.toFloat()),
                ProjectionType.ORTHOGRAPHIC
            )

            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            builder.addVertex(0f, 0f, 0f).setColor(-1)
            builder.addVertex(0f, h, 0f).setColor(-1)
            builder.addVertex(w, h, 0f).setColor(-1)
            builder.addVertex(w, 0f, 0f).setColor(-1)
            val mesh = builder.buildOrThrow()

            val dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                Matrix4f().translation(x, y, 0f), Vector4f(1f, 1f, 1f, 1f), Vector3f(), Matrix4f()
            )

            val uniformBuffer = uniformStorage.writeUniform { buffer ->
                Std140Builder.intoBuffer(buffer)
                    .putVec4(w * 0.5f, h * 0.5f, w, h)                                    // u_Rect
                    .putVec4(radTL, radTR, radBR, radBL)                                  // u_Radii
                    .putVec4(target.width.toFloat(), target.height.toFloat(), x, y)       // u_Screen
                    .putVec4(blurRadius, if (boxKernel) 1f else 0f, 0f, 0f)               // u_Blur
            }

            val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            val vertexBuffer = CustomRenderPipelines.PIPELINE_PANEL_BLUR.vertexFormat.uploadImmediateVertexBuffer(mesh.vertexBuffer())
            val indexStorage = RenderSystem.getSequentialBuffer(mesh.drawState().mode())
            val indexBuffer = indexStorage.getBuffer(mesh.drawState().indexCount())

            mesh.use {
                target.colorTextureView?.let { gpuTextureView ->
                    RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        { "FloydAddons Panel Blur Inline" }, gpuTextureView, OptionalInt.empty(),
                        if (target.useDepth) target.depthTextureView else null,
                        OptionalDouble.empty()
                    )
                }?.use { pass ->
                    pass.setPipeline(CustomRenderPipelines.PIPELINE_PANEL_BLUR)
                    RenderSystem.bindDefaultUniforms(pass)
                    pass.setUniform("DynamicTransforms", dynamicTransforms)
                    pass.setUniform("u", uniformBuffer)
                    pass.bindTexture("Sampler0", source, sampler)
                    pass.setVertexBuffer(0, vertexBuffer)
                    pass.setIndexBuffer(indexBuffer, indexStorage.type())
                    pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1)
                }
            }
            //?}
        }

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

            context.guiRenderState.addPicturesInPictureState(
                State(
                    screenLeft, screenTop, screenW, screenH,
                    topLeftRadius * poseScale, topRightRadius * poseScale,
                    bottomRightRadius * poseScale, bottomLeftRadius * poseScale,
                    blurRadius, boxKernel,
                    scissor, bounds
                )
            )
        }

        //? if <26.2 {
        private var rawProgram = 0
        private var rawVao = 0
        private var rawSampler = 0
        private var rawInitFailed = false
        private var rawFailureReported = false
        private var screenSizeUniform = -1
        private var panelOriginUniform = -1
        private var panelSizeUniform = -1
        private var radiiUniform = -1
        private var blurRadiusUniform = -1
        private var boxKernelUniform = -1
        private var sourceUniform = -1

        /**
         * Draws the sampled world backdrop into the already-bound panel texture. The 26.1 command
         * encoder accepted the old panel-blur pass but never wrote a pixel to its PIP attachment;
         * this small raw-GL pass uses the same proven FBO path as NanoVG and restores every state it
         * changes before returning.
         */
        private fun drawRawBlur(
            originX: Float,
            originY: Float,
            width: Int,
            height: Int,
            radiusTopLeft: Float,
            radiusTopRight: Float,
            radiusBottomRight: Float,
            radiusBottomLeft: Float,
            blurRadius: Float,
            boxKernel: Boolean,
        ) {
            if (width <= 0 || height <= 0 || blurRadius < 0.5f || !ensureRawBlurInitialized()) return
            val mainTarget = Minecraft.getInstance().mainRenderTarget
            val source = mainTarget.colorTexture as? GlTexture ?: return

            val previousProgram = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM)
            val previousVao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING)
            val previousActiveTexture = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE)
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0)
            val previousTexture = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D)
            val previousSampler = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING)
            val blendEnabled = GL33C.glIsEnabled(GL33C.GL_BLEND)
            val depthEnabled = GL33C.glIsEnabled(GL33C.GL_DEPTH_TEST)
            val cullEnabled = GL33C.glIsEnabled(GL33C.GL_CULL_FACE)
            val scissorEnabled = GL33C.glIsEnabled(GL33C.GL_SCISSOR_TEST)

            runCatching {
                GL33C.glDisable(GL33C.GL_BLEND)
                GL33C.glDisable(GL33C.GL_DEPTH_TEST)
                GL33C.glDisable(GL33C.GL_CULL_FACE)
                GL33C.glDisable(GL33C.GL_SCISSOR_TEST)
                GL33C.glDepthMask(false)
                GL33C.glColorMask(true, true, true, true)

                GL33C.glUseProgram(rawProgram)
                GL33C.glBindVertexArray(rawVao)
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, source.glId())
                GL33C.glBindSampler(0, rawSampler)
                GL33C.glUniform2f(screenSizeUniform, mainTarget.width.toFloat(), mainTarget.height.toFloat())
                GL33C.glUniform2f(panelOriginUniform, originX, originY)
                GL33C.glUniform2f(panelSizeUniform, width.toFloat(), height.toFloat())
                GL33C.glUniform4f(
                    radiiUniform,
                    radiusTopLeft,
                    radiusTopRight,
                    radiusBottomRight,
                    radiusBottomLeft,
                )
                GL33C.glUniform1f(blurRadiusUniform, blurRadius.coerceAtMost(20f))
                GL33C.glUniform1i(boxKernelUniform, if (boxKernel) 1 else 0)
                GL33C.glUniform1i(sourceUniform, 0)
                GL33C.glDrawArrays(GL33C.GL_TRIANGLE_STRIP, 0, 4)
            }.onFailure { error ->
                if (!rawFailureReported) {
                    rawFailureReported = true
                    FloydAddonsMod.logger.error("Failed to draw Floyd panel backdrop blur", error)
                }
            }

            GL33C.glUseProgram(previousProgram)
            GL33C.glBindVertexArray(previousVao)
            GL33C.glBindSampler(0, previousSampler)
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, previousTexture)
            GL33C.glActiveTexture(previousActiveTexture)
            GL33C.glDepthMask(true)
            if (blendEnabled) GL33C.glEnable(GL33C.GL_BLEND) else GL33C.glDisable(GL33C.GL_BLEND)
            if (depthEnabled) GL33C.glEnable(GL33C.GL_DEPTH_TEST) else GL33C.glDisable(GL33C.GL_DEPTH_TEST)
            if (cullEnabled) GL33C.glEnable(GL33C.GL_CULL_FACE) else GL33C.glDisable(GL33C.GL_CULL_FACE)
            if (scissorEnabled) GL33C.glEnable(GL33C.GL_SCISSOR_TEST) else GL33C.glDisable(GL33C.GL_SCISSOR_TEST)
        }

        private fun ensureRawBlurInitialized(): Boolean {
            if (rawProgram != 0) return true
            if (rawInitFailed) return false
            return runCatching {
                val vertexShader = compileRawShader(GL33C.GL_VERTEX_SHADER, RAW_VERTEX_SHADER)
                val fragmentShader = compileRawShader(GL33C.GL_FRAGMENT_SHADER, RAW_FRAGMENT_SHADER)
                rawProgram = GL33C.glCreateProgram()
                GL33C.glAttachShader(rawProgram, vertexShader)
                GL33C.glAttachShader(rawProgram, fragmentShader)
                GL33C.glLinkProgram(rawProgram)
                if (GL33C.glGetProgrami(rawProgram, GL33C.GL_LINK_STATUS) == GL33C.GL_FALSE) {
                    error("program link failed: ${GL33C.glGetProgramInfoLog(rawProgram)}")
                }
                GL33C.glDeleteShader(vertexShader)
                GL33C.glDeleteShader(fragmentShader)

                rawVao = GL33C.glGenVertexArrays()
                rawSampler = GL33C.glGenSamplers()
                GL33C.glSamplerParameteri(rawSampler, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR)
                GL33C.glSamplerParameteri(rawSampler, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR)
                GL33C.glSamplerParameteri(rawSampler, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE)
                GL33C.glSamplerParameteri(rawSampler, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE)

                screenSizeUniform = GL33C.glGetUniformLocation(rawProgram, "u_ScreenSize")
                panelOriginUniform = GL33C.glGetUniformLocation(rawProgram, "u_PanelOrigin")
                panelSizeUniform = GL33C.glGetUniformLocation(rawProgram, "u_PanelSize")
                radiiUniform = GL33C.glGetUniformLocation(rawProgram, "u_Radii")
                blurRadiusUniform = GL33C.glGetUniformLocation(rawProgram, "u_BlurRadius")
                boxKernelUniform = GL33C.glGetUniformLocation(rawProgram, "u_BoxKernel")
                sourceUniform = GL33C.glGetUniformLocation(rawProgram, "u_Source")
                true
            }.getOrElse { error ->
                rawInitFailed = true
                FloydAddonsMod.logger.error("Failed to initialize Floyd panel backdrop blur", error)
                false
            }
        }

        private fun compileRawShader(type: Int, source: String): Int {
            val shader = GL33C.glCreateShader(type)
            GL33C.glShaderSource(shader, source)
            GL33C.glCompileShader(shader)
            if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == GL33C.GL_FALSE) {
                error("shader compile failed: ${GL33C.glGetShaderInfoLog(shader)}")
            }
            return shader
        }

        private val RAW_VERTEX_SHADER = """
            #version 330 core

            uniform vec2 u_PanelSize;
            out vec2 v_Position;

            void main() {
                vec2 corners[4] = vec2[](
                    vec2(0.0, 0.0),
                    vec2(0.0, 1.0),
                    vec2(1.0, 0.0),
                    vec2(1.0, 1.0)
                );
                vec2 corner = corners[gl_VertexID];
                v_Position = corner * u_PanelSize;
                gl_Position = vec4(corner.x * 2.0 - 1.0, 1.0 - corner.y * 2.0, 0.0, 1.0);
            }
        """.trimIndent()

        private val RAW_FRAGMENT_SHADER = """
            #version 330 core

            uniform sampler2D u_Source;
            uniform vec2 u_ScreenSize;
            uniform vec2 u_PanelOrigin;
            uniform vec2 u_PanelSize;
            uniform vec4 u_Radii;
            uniform float u_BlurRadius;
            uniform int u_BoxKernel;

            in vec2 v_Position;
            out vec4 fragColor;

            float cornerRadius(vec2 p, vec4 radii) {
                float sx = step(0.0, p.x);
                float sy = step(0.0, p.y);
                float top = mix(radii.x, radii.y, sx);
                float bottom = mix(radii.w, radii.z, sx);
                return mix(top, bottom, sy);
            }

            float roundedRectSdf(vec2 p, vec2 halfSize, float radius) {
                vec2 q = abs(p) - halfSize + radius;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            void main() {
                vec2 halfSize = u_PanelSize * 0.5;
                vec2 p = v_Position - halfSize;
                float radius = clamp(cornerRadius(p, u_Radii), 0.0, min(halfSize.x, halfSize.y));
                float distanceToEdge = roundedRectSdf(p, halfSize, radius);
                float mask = 1.0 - smoothstep(0.0, max(fwidth(distanceToEdge), 0.75), distanceToEdge);
                if (mask <= 0.0) {
                    fragColor = vec4(0.0);
                    return;
                }

                vec2 uv = (u_PanelOrigin + v_Position) / u_ScreenSize;
                uv.y = 1.0 - uv.y;
                vec2 texel = 1.0 / u_ScreenSize;
                float sigma = max(u_BlurRadius * 0.5, 0.0001);
                vec3 accumulated = vec3(0.0);
                float weightSum = 0.0;

                for (int sampleX = -20; sampleX <= 20; sampleX += 2) {
                    for (int sampleY = -20; sampleY <= 20; sampleY += 2) {
                        vec2 offset = vec2(float(sampleX), float(sampleY));
                        if (abs(offset.x) > u_BlurRadius || abs(offset.y) > u_BlurRadius) continue;
                        float weight = u_BoxKernel != 0
                            ? 1.0
                            : exp(-dot(offset, offset) / (2.0 * sigma * sigma));
                        accumulated += texture(u_Source, uv + offset * texel).rgb * weight;
                        weightSum += weight;
                    }
                }

                vec3 color = weightSum > 0.0 ? accumulated / weightSum : texture(u_Source, uv).rgb;
                fragColor = vec4(color * mask, mask);
            }
        """.trimIndent()
        //?}
    }
}
