package gg.floyd.features.impl.misc

import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.utils.render.PooledPicturePIPRenderer
import gg.floyd.utils.ui.rendering.DirectStateAccessCompat
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL14C
import org.lwjgl.opengl.GL15C
import org.lwjgl.opengl.GL20C
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL33C
import java.nio.FloatBuffer
import org.joml.Matrix4fc

/**
 * Fullscreen landscape shader background for Floyd's custom menu flow.
 *
 * This runs as a tiny raw-GL fullscreen pass so the exact shader can stay backend-agnostic across
 * 26.1, 26.1.2, and 26.2 while still exposing live module settings as uniforms.
 */
object FloydMenuVideoBackground {
    private const val RESYNC_TEXTURE_UNITS = 4

    private var program = 0
    private var vao = 0
    private var vbo = 0
    private var initialized = false
    private var initFailed = false
    //? if >=26.2 {
    private var fbo26 = 0
    //?}

    private var timeUniform = -1
    private var resolutionUniform = -1
    private var skyTopUniform = -1
    private var skyHorizonUniform = -1
    private var grassPrimaryUniform = -1
    private var grassSecondaryUniform = -1
    private var sunUniform = -1
    private var fogUniform = -1
    private var postFxUniform = -1
    private var speedUniform = -1
    private var worldSkyModeUniform = -1
    private var inverseSkyMatrixUniform = -1
    private val inverseSkyMatrixBuffer = BufferUtils.createFloatBuffer(16)
    private var skyFramebuffer = 0
    private var softFramebuffer = 0
    private var softTexture = 0
    private var softWidth = 0
    private var softHeight = 0
    private var skyLayerUniform = -1

    /** Draw before terrain into a color-only target, leaving world depth untouched. */
    internal fun renderWorldSky(inverseProjectionView: Matrix4fc, style: Int, speed: Float, quality: Int = 1): Boolean {
        val target = mc.mainRenderTarget
        val color = target.colorTexture as? GlTexture ?: return false
        val previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING)
        val previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING)
        val previousDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK)
        try {
            if (skyFramebuffer == 0) skyFramebuffer = GL30C.glGenFramebuffers()
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, skyFramebuffer)
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, color.glId(), 0)
            if (GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) return false
            GL11C.glDepthMask(false)
            val time = menuTimeSeconds()
            // Golden Aurora is already lightweight; preserve its original native-resolution look.
            if (quality >= 2 || style == 1 || style == 3 || style == 4) return renderIntoBoundTarget(target.width, target.height, time, style, inverseProjectionView, speed)
            val scale = if (quality == 0) 0.33f else 0.5f
            val width = (target.width * scale).toInt().coerceAtLeast(1)
            val height = (target.height * scale).toInt().coerceAtLeast(1)
            ensureSoftTarget(width, height)
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, softFramebuffer)
            if (!renderIntoBoundTarget(width, height, time, style, inverseProjectionView, speed, 1)) return false
            // Linear reconstruction of the soft layer needs no texture/sampler binding changes.
            val scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST)
            GlStateManager._disableScissorTest()
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, softFramebuffer)
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, skyFramebuffer)
            GL30C.glBlitFramebuffer(0, 0, width, height, 0, 0, target.width, target.height, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_LINEAR)
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, skyFramebuffer)
            restoreCapability(GL11C.GL_SCISSOR_TEST, scissor)
            return renderIntoBoundTarget(target.width, target.height, time, style, inverseProjectionView, speed, 2)
        } finally {
            GL11C.glDepthMask(previousDepthMask)
            GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw)
            GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead)
        }
    }

    /** Allocation only on resize. Restore raw texture binding without disturbing Blaze3D's cache. */
    private fun ensureSoftTarget(width: Int, height: Int) {
        if (softTexture != 0 && width == softWidth && height == softHeight) return
        val binding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)
        if (softTexture == 0) softTexture = GL11C.glGenTextures()
        if (softFramebuffer == 0) softFramebuffer = GL30C.glGenFramebuffers()
        try {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, softTexture)
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, width, height, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR)
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, softFramebuffer)
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, softTexture, 0)
            check(GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) == GL30C.GL_FRAMEBUFFER_COMPLETE)
            softWidth = width; softHeight = height
        } finally { GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, binding) }
    }

    @JvmStatic
    fun render(context: GuiGraphics): Boolean {
        val guiWidth = context.guiWidth()
        val guiHeight = context.guiHeight()
        if (guiWidth <= 0 || guiHeight <= 0) return false
        FloydMenuShaderPIPRenderer.submit(context, 0, 0, guiWidth, guiHeight, menuTimeSeconds())
        return true
    }

    @JvmStatic
    fun renderDirect(context: GuiGraphics): Boolean {
        val guiWidth = context.guiWidth()
        val guiHeight = context.guiHeight()
        if (guiWidth <= 0 || guiHeight <= 0) return false
        return renderIntoBoundTarget(guiWidth, guiHeight, menuTimeSeconds())
    }

    internal fun renderIntoCurrentPip(width: Int, height: Int, time: Float): Boolean {
        if (width <= 0 || height <= 0 || initFailed) return false
        if (!ensureInitialized()) return false

        val colorTex = com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride ?: return false
        val targetWidth = colorTex.getWidth(0)
        val targetHeight = colorTex.getHeight(0)
        if (targetWidth <= 0 || targetHeight <= 0) return false
        val previousProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)
        val previousVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING)
        val previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING)
        val previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE)
        val previousTexture2d = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)
        val viewport = IntArray(4)
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport)
        val blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND)
        val depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST)
        val cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE)
        val scissorEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST)

        runCatching {
            //? if >=26.2 {
            val glColor = (colorTex.texture() as? GlTexture) ?: return false
            val glDepth = com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride?.texture() as? GlTexture
            if (fbo26 == 0) fbo26 = GL33C.glGenFramebuffers()
            // The 26.2 command encoder tracks framebuffer binds behind GlStateManager's cache.
            // Force a cache miss first so both the bind into our temp FBO and the bind back out
            // below definitely hit raw GL every frame instead of being skipped as stale no-ops.
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0)
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, fbo26)
            GL33C.glFramebufferTexture2D(GlConst.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, glColor.glId(), 0)
            if (glDepth != null) {
                GL33C.glFramebufferTexture2D(GlConst.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, glDepth.glId(), 0)
            } else {
                GL33C.glFramebufferTexture2D(GlConst.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, 0, 0)
            }
            //?} else {
            val bufferManager = DirectStateAccessCompat.directStateAccess() ?: return false
            val glDepthTex = com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride?.texture() as? GlTexture
            val framebuffer = ((colorTex.texture() as? GlTexture)?.getFbo(bufferManager, glDepthTex)) ?: return false
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, framebuffer)
            //?}
            GL11C.glViewport(0, 0, targetWidth, targetHeight)
            GL11C.glDisable(GL11C.GL_DEPTH_TEST)
            GL11C.glDisable(GL11C.GL_CULL_FACE)
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST)
            GL11C.glEnable(GL11C.GL_BLEND)
            GL11C.glBlendFunc(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA)

            GL20C.glUseProgram(program)
            GL30C.glBindVertexArray(vao)

            GL20C.glUniform1i(skyLayerUniform, 0)
            GL20C.glUniform1i(worldSkyModeUniform, 0)
            GL20C.glUniform1f(timeUniform, time)
            GL20C.glUniform2f(resolutionUniform, targetWidth.toFloat(), targetHeight.toFloat())
            GL20C.glUniform1f(speedUniform, FloydCustomMainMenu.backgroundSpeed)

            val skyTop = rgb(FloydCustomMainMenu.skyTopColor.rgba)
            val skyHorizon = rgb(FloydCustomMainMenu.skyHorizonColor.rgba)
            val grassPrimary = rgb(FloydCustomMainMenu.grassPrimaryColor.rgba)
            val grassSecondary = rgb(FloydCustomMainMenu.grassSecondaryColor.rgba)
            val sun = rgb(FloydCustomMainMenu.sunColor.rgba)
            val fog = rgb(FloydCustomMainMenu.fogColor.rgba)

            GL20C.glUniform3f(skyTopUniform, skyTop[0], skyTop[1], skyTop[2])
            GL20C.glUniform3f(skyHorizonUniform, skyHorizon[0], skyHorizon[1], skyHorizon[2])
            GL20C.glUniform3f(grassPrimaryUniform, grassPrimary[0], grassPrimary[1], grassPrimary[2])
            GL20C.glUniform3f(grassSecondaryUniform, grassSecondary[0], grassSecondary[1], grassSecondary[2])
            GL20C.glUniform3f(sunUniform, sun[0], sun[1], sun[2])
            GL20C.glUniform3f(fogUniform, fog[0], fog[1], fog[2])
            GL20C.glUniform4f(
                postFxUniform,
                FloydCustomMainMenu.backgroundContrast,
                FloydCustomMainMenu.backgroundSaturation,
                FloydCustomMainMenu.backgroundBrightness,
                FloydCustomMainMenu.backgroundVignette
            )

            GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, 0, 4)
            resyncBlazeStateAfterRawPass()
        }.onFailure { error ->
            initFailed = true
            FloydAddonsMod.logger.error("Failed to render custom menu landscape shader", error)
        }

        GL20C.glUseProgram(previousProgram)
        GL30C.glBindVertexArray(previousVao)
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, previousArrayBuffer)
        //? if >=26.2 {
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0)
        //?}
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, previousFramebuffer)
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0)
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture2d)
        GL13C.glActiveTexture(previousActiveTexture)
        GL33C.glBindSampler(0, 0)
        GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        restoreCapability(GL11C.GL_BLEND, blendEnabled)
        restoreCapability(GL11C.GL_DEPTH_TEST, depthEnabled)
        restoreCapability(GL11C.GL_CULL_FACE, cullEnabled)
        restoreCapability(GL11C.GL_SCISSOR_TEST, scissorEnabled)
        return !initFailed
    }

    private fun renderIntoBoundTarget(
        targetWidth: Int, targetHeight: Int, time: Float,
        worldSkyMode: Int = 0, inverseProjectionView: Matrix4fc? = null, animationSpeed: Float = 1f, skyLayer: Int = 0,
    ): Boolean {
        if (targetWidth <= 0 || targetHeight <= 0 || initFailed) return false
        if (!ensureInitialized()) return false

        val previousProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)
        val previousVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING)
        val previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING)
        val previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE)
        val previousTexture2d = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)
        val viewport = IntArray(4)
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport)
        val blendSourceRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB)
        val blendDestRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB)
        val blendSourceAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA)
        val blendDestAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA)
        val blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND)
        val depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST)
        val cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE)
        val scissorEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST)

        runCatching {
            GL11C.glViewport(0, 0, targetWidth, targetHeight)
            if (worldSkyMode != 0) {
                // World renderers cache texture/sampler bindings across passes. This shader
                // samples no textures, so leave those bindings and their caches untouched.
                GlStateManager._disableDepthTest()
                GlStateManager._disableCull()
                GlStateManager._disableScissorTest()
                restoreCapability(GL11C.GL_BLEND, skyLayer == 2)
                if (skyLayer == 2) GlStateManager._blendFuncSeparate(GL11C.GL_DST_ALPHA, GL11C.GL_ONE, GL11C.GL_ONE, GL11C.GL_ZERO)
            } else {
                GL11C.glDisable(GL11C.GL_DEPTH_TEST)
                GL11C.glDisable(GL11C.GL_CULL_FACE)
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST)
                GL11C.glEnable(GL11C.GL_BLEND)
                GL11C.glBlendFunc(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA)
            }

            GL20C.glUseProgram(program)
            GL30C.glBindVertexArray(vao)

            GL20C.glUniform1i(skyLayerUniform, skyLayer)
            GL20C.glUniform1i(worldSkyModeUniform, worldSkyMode)
            if (inverseProjectionView != null) {
                inverseProjectionView.get(inverseSkyMatrixBuffer)
                GL20C.glUniformMatrix4fv(inverseSkyMatrixUniform, false, inverseSkyMatrixBuffer)
            }
            GL20C.glUniform1f(timeUniform, time)
            GL20C.glUniform2f(resolutionUniform, targetWidth.toFloat(), targetHeight.toFloat())
            GL20C.glUniform1f(speedUniform, if (worldSkyMode >= 2) animationSpeed else FloydCustomMainMenu.backgroundSpeed * animationSpeed)

            val skyTop = rgb(FloydCustomMainMenu.skyTopColor.rgba)
            val skyHorizon = rgb(FloydCustomMainMenu.skyHorizonColor.rgba)
            val grassPrimary = rgb(FloydCustomMainMenu.grassPrimaryColor.rgba)
            val grassSecondary = rgb(FloydCustomMainMenu.grassSecondaryColor.rgba)
            val sun = rgb(FloydCustomMainMenu.sunColor.rgba)
            val fog = rgb(FloydCustomMainMenu.fogColor.rgba)

            GL20C.glUniform3f(skyTopUniform, skyTop[0], skyTop[1], skyTop[2])
            GL20C.glUniform3f(skyHorizonUniform, skyHorizon[0], skyHorizon[1], skyHorizon[2])
            GL20C.glUniform3f(grassPrimaryUniform, grassPrimary[0], grassPrimary[1], grassPrimary[2])
            GL20C.glUniform3f(grassSecondaryUniform, grassSecondary[0], grassSecondary[1], grassSecondary[2])
            GL20C.glUniform3f(sunUniform, sun[0], sun[1], sun[2])
            GL20C.glUniform3f(fogUniform, fog[0], fog[1], fog[2])
            GL20C.glUniform4f(
                postFxUniform,
                FloydCustomMainMenu.backgroundContrast,
                FloydCustomMainMenu.backgroundSaturation,
                FloydCustomMainMenu.backgroundBrightness,
                FloydCustomMainMenu.backgroundVignette
            )

            GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP, 0, 4)
            if (worldSkyMode == 0) resyncBlazeStateAfterRawPass()
        }.onFailure { error ->
            initFailed = true
            FloydAddonsMod.logger.error("Failed to render direct custom menu landscape shader", error)
        }

        GL20C.glUseProgram(previousProgram)
        GL30C.glBindVertexArray(previousVao)
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, previousArrayBuffer)
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, previousFramebuffer)
        if (worldSkyMode == 0) {
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0)
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture2d)
            GL13C.glActiveTexture(previousActiveTexture)
            GL33C.glBindSampler(0, 0)
        }
        GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        GlStateManager._blendFuncSeparate(blendSourceRgb, blendDestRgb, blendSourceAlpha, blendDestAlpha)
        restoreCapability(GL11C.GL_BLEND, blendEnabled)
        restoreCapability(GL11C.GL_DEPTH_TEST, depthEnabled)
        restoreCapability(GL11C.GL_CULL_FACE, cullEnabled)
        restoreCapability(GL11C.GL_SCISSOR_TEST, scissorEnabled)
        return !initFailed
    }

    @JvmStatic
    fun tick() = Unit

    @JvmStatic
    fun shutdown() {
        if (!initialized) return
        runCatching {
            if (skyFramebuffer != 0) GL30C.glDeleteFramebuffers(skyFramebuffer)
            skyFramebuffer = 0
            if (softFramebuffer != 0) GL30C.glDeleteFramebuffers(softFramebuffer)
            if (softTexture != 0) GL11C.glDeleteTextures(softTexture)
            softFramebuffer = 0; softTexture = 0; softWidth = 0; softHeight = 0
            //? if >=26.2 {
            if (fbo26 != 0) GL30C.glDeleteFramebuffers(fbo26)
            //?}
            if (program != 0) GL20C.glDeleteProgram(program)
            if (vbo != 0) GL15C.glDeleteBuffers(vbo)
            if (vao != 0) GL30C.glDeleteVertexArrays(vao)
        }
        initialized = false
        program = 0
        vao = 0
        vbo = 0
        //? if >=26.2 {
        fbo26 = 0
        //?}
    }

    private fun menuTimeSeconds(): Float = (System.currentTimeMillis() % 1_200_000L) / 1000f

    private fun rgb(argb: Int): FloatArray = floatArrayOf(
        ((argb ushr 16) and 0xFF) / 255f,
        ((argb ushr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f
    )

    private fun restoreCapability(cap: Int, enabled: Boolean) {
        // Keep Blaze3D's cache consistent as well as restoring the actual GL state.
        when (cap) {
            GL11C.GL_DEPTH_TEST -> if (enabled) GlStateManager._enableDepthTest() else GlStateManager._disableDepthTest()
            GL11C.GL_CULL_FACE -> if (enabled) GlStateManager._enableCull() else GlStateManager._disableCull()
            GL11C.GL_SCISSOR_TEST -> if (enabled) GlStateManager._enableScissorTest() else GlStateManager._disableScissorTest()
            GL11C.GL_BLEND -> {
                //? if >=26.2 {
                /*if (enabled) GlStateManager._enableBlend(0) else GlStateManager._disableBlend(0)
                *///?} else {
                if (enabled) GlStateManager._enableBlend() else GlStateManager._disableBlend()
                //?}
            }
        }
    }

    /**
     * Raw GL mutates state behind Blaze3D's caches on 26.2. Re-assert through GlStateManager with
     * forced cache misses so the later GUI render passes do not skip real GL binds and present black.
     */
    private fun resyncBlazeStateAfterRawPass() {
        GlStateManager._activeTexture(GL13C.GL_TEXTURE1)
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0)
        for (unit in RESYNC_TEXTURE_UNITS - 1 downTo 0) {
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + unit)
            GL33C.glBindSampler(unit, 0)
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0)
            GlStateManager._bindTexture(0)
        }
        //? if >=26.2 {
        /*GlStateManager._disableBlend(0)
        *///?} else {
        GlStateManager._disableBlend()
        //?}
        //? if >=26.2 {
        /*GlStateManager._enableBlend(0)
        *///?} else {
        GlStateManager._enableBlend()
        //?}
        GlStateManager._enableDepthTest()
        GlStateManager._disableDepthTest()
        GlStateManager._enableCull()
        GlStateManager._disableCull()
        GlStateManager._enableScissorTest()
        GlStateManager._disableScissorTest()
        GlStateManager._blendFuncSeparate(1, 0, 1, 0)
        GlStateManager._blendFuncSeparate(770, 771, 1, 0)
        for (unit in 0 until RESYNC_TEXTURE_UNITS) {
            GL33C.glBindSampler(unit, 0)
        }
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0)
    }

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        return runCatching {
            val vertexSource = readShader("/assets/floydaddons/shaders/core/menu_landscape.vsh")
            val fragmentSource = readShader("/assets/floydaddons/shaders/core/menu_landscape.fsh")

            val vertexShader = compileShader(GL20C.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = compileShader(GL20C.GL_FRAGMENT_SHADER, fragmentSource)

            program = GL20C.glCreateProgram()
            GL20C.glAttachShader(program, vertexShader)
            GL20C.glAttachShader(program, fragmentShader)
            GL20C.glLinkProgram(program)
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                error("Program link failed: ${GL20C.glGetProgramInfoLog(program)}")
            }
            GL20C.glDeleteShader(vertexShader)
            GL20C.glDeleteShader(fragmentShader)

            val vertices = floatArrayOf(
                -1f, -1f, 0f,
                1f, -1f, 0f,
                -1f, 1f, 0f,
                1f, 1f, 0f
            )
            val vertexBuffer: FloatBuffer = BufferUtils.createFloatBuffer(vertices.size)
            vertexBuffer.put(vertices).flip()

            vao = GL30C.glGenVertexArrays()
            vbo = GL15C.glGenBuffers()
            GL30C.glBindVertexArray(vao)
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo)
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, vertexBuffer, GL15C.GL_STATIC_DRAW)
            GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0L)
            GL20C.glEnableVertexAttribArray(0)
            GL30C.glBindVertexArray(0)
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0)

            timeUniform = GL20C.glGetUniformLocation(program, "u_time")
            resolutionUniform = GL20C.glGetUniformLocation(program, "u_resolution")
            speedUniform = GL20C.glGetUniformLocation(program, "u_speed")
            skyLayerUniform = GL20C.glGetUniformLocation(program, "u_skyLayer")
            worldSkyModeUniform = GL20C.glGetUniformLocation(program, "u_worldSkyMode")
            inverseSkyMatrixUniform = GL20C.glGetUniformLocation(program, "u_inverseSkyMatrix")
            skyTopUniform = GL20C.glGetUniformLocation(program, "u_skyTopColor")
            skyHorizonUniform = GL20C.glGetUniformLocation(program, "u_skyHorizonColor")
            grassPrimaryUniform = GL20C.glGetUniformLocation(program, "u_grassPrimaryColor")
            grassSecondaryUniform = GL20C.glGetUniformLocation(program, "u_grassSecondaryColor")
            sunUniform = GL20C.glGetUniformLocation(program, "u_sunColor")
            fogUniform = GL20C.glGetUniformLocation(program, "u_fogColor")
            postFxUniform = GL20C.glGetUniformLocation(program, "u_postFx")
            initialized = true
            true
        }.getOrElse { error ->
            initFailed = true
            FloydAddonsMod.logger.error("Failed to initialize custom menu landscape shader", error)
            false
        }
    }

    private fun readShader(path: String): String =
        checkNotNull(FloydMenuVideoBackground::class.java.getResourceAsStream(path)) { "Missing shader resource $path" }
            .bufferedReader()
            .use { it.readText() }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GL20C.glCreateShader(type)
        GL20C.glShaderSource(shader, source)
        GL20C.glCompileShader(shader)
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            val kind = if (type == GL20C.GL_VERTEX_SHADER) "vertex" else "fragment"
            error("$kind shader compile failed: ${GL20C.glGetShaderInfoLog(shader)}")
        }
        return shader
    }
}
