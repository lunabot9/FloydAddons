package gg.floyd.utils.ui.rendering

import com.mojang.blaze3d.opengl.GlStateManager
import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.impl.render.FloydFont
import gg.floyd.utils.Color.Companion.alpha
import gg.floyd.utils.Color.Companion.blue
import gg.floyd.utils.Color.Companion.green
import gg.floyd.utils.Color.Companion.red
import gg.floyd.utils.font.MsdfFontMetrics
import gg.floyd.utils.render.DeferredNvgText
import gg.floyd.utils.render.NvgTextReplay
import net.minecraft.resources.Identifier
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoSVG.*
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.*
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL33C
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object NVGRenderer {

    private val nvgPaint = NVGPaint.malloc()
    private val nvgColor = NVGColor.malloc()
    private val nvgColor2: NVGColor = NVGColor.malloc()

    val defaultFont by lazy(LazyThreadSafetyMode.NONE) {
        Font("Default", mc.resourceManager.getResource(Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "font.ttf")).get().open())
    }

    fun activeFont(): Font {
        val path = FloydFont.customFontPath()
        if (FloydFont.isGlobalCustomFontEnabled() && path != null) {
            return runCatching {
                Font("Custom:${path.toAbsolutePath()}:${Files.getLastModifiedTime(path).toMillis()}", Files.newInputStream(path))
            }.getOrDefault(defaultFont)
        }
        return defaultFont
    }

    private val fontMap = HashMap<Font, NVGFont>()
    private val fontBounds = FloatArray(4)

    private val images = HashMap<Image, NVGImage>()

    private var scissor: Scissor? = null
    private var drawing: Boolean = false
    private var vg = -1L

    /**
     * Escape hatch (design D7 step 5): `FLOYD_NVG_TEXT=1` restores the legacy NanoVG text path —
     * rendering (`text`/`textShadow`/`drawWrappedString`) AND measurement
     * (`textWidth`/`wrappedTextBounds`) flip as ONE unit, or layouts would shear against the
     * renderer (the D6 invariant). Read once here; deleted with the NVG text code in step 7.
     */
    private val legacyNvgText: Boolean = System.getenv("FLOYD_NVG_TEXT") == "1"

    /** True when text calls are deferred for mc.font replay (the default; see [DeferredNvgText]). */
    val deferringText: Boolean get() = !legacyNvgText

    /**
     * Deferred text queue for the current PIP frame, drained per layer by NVGPIPRenderer.
     * Records are POOLED: [drainDeferredText] ping-pongs two lists and recycles the previously
     * drained batch (the replay consumes a batch synchronously before the next drain can happen),
     * so steady-state text capture allocates nothing.
     */
    private var deferredText = ArrayList<DeferredNvgText>()
    private var consumedText = ArrayList<DeferredNvgText>()
    private val deferredPool = ArrayList<DeferredNvgText>()

    /**
     * Current text layer: 0 = GUI-level text drawn before any panel (title/search bar/community),
     * each base panel gets the next index via [nextTextLayer], dragged panel + tooltip = topmost.
     */
    private var textLayer = 0

    /**
     * Sub-frame split hook installed by NVGPIPRenderer while its frame is live: invoked at every
     * layer boundary ([nextTextLayer]), it ends the current NVG sub-frame, replays the closed
     * layer's text into the PIP slot, and opens the next sub-frame (restoring the caller's live
     * transform — guiScale + open-anim — and scissor stack).
     */
    internal var layerBoundary: (() -> Unit)? = null

    init {
        // nvgCreate builds the fontstash atlas texture with RAW binds (bind new tex -> upload ->
        // bind 0) on whatever unit is raw-active — run it unit-pinned + cache-resynced or the
        // stale cache false-skips the next bind of whatever it still claims (the MSDF glyph-atlas
        // page during the first ClickGUI frame: every glyph first-measured that frame uploaded
        // into nothing and stayed a permanently black cell).
        vg = withCoherentTextureUnit { nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES) }
        require(vg != -1L) { "Failed to initialize NanoVG" }
        NvgTextReplay.deferralActive = !legacyNvgText
        if (legacyNvgText) {
            FloydAddonsMod.logger.warn(
                "[NVGRenderer] FLOYD_NVG_TEXT=1 — legacy NanoVG text rendering AND measurement active (MSDF deferral bypassed)"
            )
        }
    }

    fun devicePixelRatio(): Float =
        if (mc.window.screenWidth == 0) 1f else (mc.window.width / mc.window.screenWidth).toFloat()

    fun beginFrame(width: Float, height: Float) {
        if (drawing) throw IllegalStateException("[NVGRenderer] Already drawing, but called beginFrame")

        val dpr = devicePixelRatio()

        nvgBeginFrame(vg, width / dpr, height / dpr, dpr)
        nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
        drawing = true
    }

    fun endFrame() {
        if (!drawing) throw IllegalStateException("[NVGRenderer] Not drawing, but called endFrame")
        nvgEndFrame(vg)

        drawing = false
    }

    /**
     * Closes the current text layer and opens the next (design D7 step 6 + its per-panel
     * CORRECTION). With deferral live this triggers NVGPIPRenderer's sub-frame split via
     * [layerBoundary]: everything queued so far bakes into the PIP slot BELOW any shapes drawn
     * after this call, and the new layer's own text replays above them. ClickGUI brackets each
     * base panel with this (so a panel overlapping another at rest occludes the lower panel's
     * replayed text) and calls it once more for the topmost layer (dragged panel + tooltip).
     * A no-op on the legacy NVG-text path (immediate draws layer naturally by paint order).
     */
    fun nextTextLayer() {
        if (legacyNvgText) return
        textLayer++
        layerBoundary?.invoke()
    }

    /** Resets the layer counter — end of the ClickGUI pass, or re-arming an aborted PIP frame. */
    fun resetTextLayers() {
        textLayer = 0
    }

    /** True when text runs are queued awaiting replay (an empty layer's boundary is skippable). */
    internal val hasDeferredText: Boolean get() = deferredText.isNotEmpty()

    /**
     * Removes and returns everything queued so far (capture order preserved). The returned list
     * is only valid until the NEXT drain — it is recycled into the record pool then (the replay
     * consumes a drained batch synchronously, so nothing can still hold it).
     */
    internal fun drainDeferredText(): List<DeferredNvgText> {
        if (consumedText.isNotEmpty()) {
            deferredPool.addAll(consumedText)
            consumedText.clear()
        }
        if (deferredText.isEmpty()) return emptyList()
        val drained = deferredText
        deferredText = consumedText
        consumedText = drained
        return drained
    }

    private val transformScratch = FloatArray(6)

    /**
     * The live NVG 2x3 transform (for NVGPIPRenderer's sub-frame transform save/restore). Returns
     * a SHARED scratch array — consume it before the next call (the boundary lambda does).
     */
    internal fun currentTransform(): FloatArray = transformScratch.also { nvgCurrentTransform(vg, it) }

    /** Premultiplies [t] onto the current transform — after `beginFrame` (identity) this SETS it. */
    internal fun applyTransform(t: FloatArray) = nvgTransform(vg, t[0], t[1], t[2], t[3], t[4], t[5])

    /** Re-asserts the Kotlin scissor stack on a fresh NVG sub-frame (normally empty at the split). */
    internal fun reapplyScissor() {
        scissor?.applyScissor()
    }

    fun push() = nvgSave(vg)

    fun pop() = nvgRestore(vg)

    fun scale(x: Float, y: Float) = nvgScale(vg, x, y)

    fun translate(x: Float, y: Float) = nvgTranslate(vg, x, y)

    fun rotate(amount: Float) = nvgRotate(vg, amount)

    fun globalAlpha(amount: Float) = nvgGlobalAlpha(vg, amount.coerceIn(0f, 1f))

    fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        scissor = Scissor(scissor, x, y, w + x, h + y, scissorFrameRect(x, y, x + w, y + h))
        scissor?.applyScissor()
    }

    /**
     * The pushed rect in NVG FRAME space — transformed by the CURRENT transform exactly as
     * `nvgScissor` transforms it — intersected with the enclosing scissor's frame rect. Every
     * ClickGUI transform is translate/scale-only (axis-aligned), so a rect maps to a rect and the
     * nested intersection is lossless; deferred text captures this for the GL-scissor replay.
     */
    private fun scissorFrameRect(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        val t = FloatArray(6)
        nvgCurrentTransform(vg, t)
        val ax = t[0] * x0 + t[2] * y0 + t[4]
        val ay = t[1] * x0 + t[3] * y0 + t[5]
        val bx = t[0] * x1 + t[2] * y1 + t[4]
        val by = t[1] * x1 + t[3] * y1 + t[5]
        val rect = floatArrayOf(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by))
        scissor?.frameRect?.let { outer ->
            rect[0] = max(rect[0], outer[0])
            rect[1] = max(rect[1], outer[1])
            rect[2] = min(rect[2], outer[2])
            rect[3] = min(rect[3], outer[3])
        }
        return rect
    }

    fun popScissor() {
        nvgResetScissor(vg)
        scissor = scissor?.previous
        scissor?.applyScissor()
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int) {
        nvgBeginPath(vg)
        nvgMoveTo(vg, x1, y1)
        nvgLineTo(vg, x2, y2)
        nvgStrokeWidth(vg, thickness)
        color(color)
        nvgStrokeColor(vg, nvgColor)
        nvgStroke(vg)
    }

    fun drawHalfRoundedRect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float, roundTop: Boolean) {
        nvgBeginPath(vg)

        if (roundTop) {
            nvgMoveTo(vg, x, y + h)
            nvgLineTo(vg, x + w, y + h)
            nvgLineTo(vg, x + w, y + radius)
            nvgArcTo(vg, x + w, y, x + w - radius, y, radius)
            nvgLineTo(vg, x + radius, y)
            nvgArcTo(vg, x, y, x, y + radius, radius)
            nvgLineTo(vg, x, y + h)
        } else {
            nvgMoveTo(vg, x, y)
            nvgLineTo(vg, x + w, y)
            nvgLineTo(vg, x + w, y + h - radius)
            nvgArcTo(vg, x + w, y + h, x + w - radius, y + h, radius)
            nvgLineTo(vg, x + radius, y + h)
            nvgArcTo(vg, x, y + h, x, y + h - radius, radius)
            nvgLineTo(vg, x, y)
        }

        nvgClosePath(vg)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float) {
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h + .5f, radius)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        nvgBeginPath(vg)
        nvgRect(vg, x, y, w, h + .5f)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun hollowRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int, radius: Float) {
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h, radius)
        nvgStrokeWidth(vg, thickness)
        nvgPathWinding(vg, NVG_HOLE)
        color(color)
        nvgStrokeColor(vg, nvgColor)
        nvgStroke(vg)
    }

    fun gradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color1: Int,
        color2: Int,
        gradient: Gradient,
        radius: Float
    ) {
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h, radius)
        gradient(color1, color2, x, y, w, h, gradient)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun dropShadow(x: Float, y: Float, width: Float, height: Float, blur: Float, spread: Float, radius: Float) {
        nvgRGBA(0, 0, 0, 125, nvgColor)
        nvgRGBA(0, 0, 0, 0, nvgColor2)

        nvgBoxGradient(
            vg,
            x - spread,
            y - spread,
            width + 2 * spread,
            height + 2 * spread,
            radius + spread,
            blur,
            nvgColor,
            nvgColor2,
            nvgPaint
        )
        nvgBeginPath(vg)
        nvgRoundedRect(
            vg,
            x - spread - blur,
            y - spread - blur,
            width + 2 * spread + 2 * blur,
            height + 2 * spread + 2 * blur,
            radius + spread
        )
        nvgRoundedRect(vg, x, y, width, height, radius)
        nvgPathWinding(vg, NVG_HOLE)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun circle(x: Float, y: Float, radius: Float, color: Int) {
        nvgBeginPath(vg)
        nvgCircle(vg, x, y, radius)
        color(color)
        nvgFillColor(vg, nvgColor)
        nvgFill(vg)
    }

    fun text(text: String, x: Float, y: Float, size: Float, color: Int, font: Font) {
        if (legacyNvgText) {
            nvgFontSize(vg, size)
            nvgFontFaceId(vg, getFontID(font))
            color(color)
            nvgFillColor(vg, nvgColor)
            nvgText(vg, x, y + .5f, text)
            return
        }
        // Legacy parity: the NVG path drew the em-box top at y + .5f — bake the same anchor in.
        deferText(text, x, y + .5f, size, color)
    }

    fun textShadow(text: String, x: Float, y: Float, size: Float, color: Int, font: Font) {
        if (legacyNvgText) {
            nvgFontFaceId(vg, getFontID(font))
            nvgFontSize(vg, size)
            color(-16777216)
            nvgFillColor(vg, nvgColor)
            nvgText(vg, round(x + 2f), round(y + 2f), text)

            color(color)
            nvgFillColor(vg, nvgColor)
            nvgText(vg, round(x), round(y), text)
            return
        }
        deferText(text, round(x + 2f), round(y + 2f), size, -16777216)
        deferText(text, round(x), round(y), size, color)
    }

    fun textWidth(text: String, size: Float, font: Font): Float {
        if (legacyNvgText) {
            nvgFontSize(vg, size)
            nvgFontFaceId(vg, getFontID(font))
            return nvgTextBounds(vg, 0f, 0f, text, fontBounds)
        }
        // Float widths from the live FontSet (design D6) at the replay's exact size/9 mapping.
        return MsdfFontMetrics.width(text, size)
    }

    fun drawWrappedString(
        text: String,
        x: Float,
        y: Float,
        w: Float,
        size: Float,
        color: Int,
        font: Font,
        lineHeight: Float = 1f
    ) {
        if (legacyNvgText) {
            nvgFontSize(vg, size)
            nvgFontFaceId(vg, getFontID(font))
            nvgTextLineHeight(vg, lineHeight)
            color(color)
            nvgFillColor(vg, nvgColor)
            nvgTextBox(vg, x, y, w, text)
            return
        }
        deferText(text, x, y, size, color, wrapWidth = w, lineHeight = lineHeight)
    }

    fun wrappedTextBounds(
        text: String,
        w: Float,
        size: Float,
        font: Font,
        lineHeight: Float = 1f
    ): FloatArray {
        if (legacyNvgText) {
            val bounds = FloatArray(4)
            nvgFontSize(vg, size)
            nvgFontFaceId(vg, getFontID(font))
            nvgTextLineHeight(vg, lineHeight)
            nvgTextBoxBounds(vg, 0f, 0f, w, text, bounds)
            return bounds // [minX, minY, maxX, maxY]
        }
        // Same Font.split wrapping the replay draws with, so the sized box always contains the
        // drawn text; lines advance size·lineHeight px (the replay's 9·lineHeight font units).
        val bounds = MsdfFontMetrics.wrappedBounds(text, w, size)
        val height = if (bounds.lineCount == 0) 0f else (bounds.lineCount - 1) * size * lineHeight + size
        return floatArrayOf(0f, 0f, bounds.width, height)
    }

    /** Queues one text run for the in-PIP mc.font replay; see [DeferredNvgText] for the spaces. */
    private fun deferText(
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        wrapWidth: Float = 0f,
        lineHeight: Float = 1f
    ) {
        val record = if (deferredPool.isNotEmpty()) deferredPool.removeAt(deferredPool.size - 1) else DeferredNvgText()
        nvgCurrentTransform(vg, record.transform)
        record.set(text, x, y, size, color, scissor?.frameRect, textLayer, wrapWidth, lineHeight)
        deferredText.add(record)
    }

    /**
     * Runs [block] (raw NVG/GL texture binds — image/font creation binds on whatever unit is
     * RAW-active, invisible to GlStateManager) pinned to unit 0, then re-zeros unit 0's raw
     * binding AND GlStateManager's cache so they agree again. Without this, the stale cache
     * false-skips a later bind of the same texture — glyph draws then sample the wrong texture
     * and glyph-atlas uploads write into whatever stayed raw-bound (permanent atlas corruption).
     */
    private inline fun <T> withCoherentTextureUnit(block: () -> T): T {
        // Toggle defeats the cached no-op: ends with active unit 0 in BOTH raw GL and the cache.
        GlStateManager._activeTexture(GL13C.GL_TEXTURE1)
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0)
        try {
            return block()
        } finally {
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0) // raw zero (cache-skip-proof)
            GlStateManager._bindTexture(0) // cache zero (no-ops if already 0 — raw is 0 either way)
        }
    }

    fun createNVGImage(textureId: Int, textureWidth: Int, textureHeight: Int): Int = withCoherentTextureUnit {
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, textureId)
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST)
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST)
        nvglCreateImageFromHandle(vg, textureId, textureWidth, textureHeight, NVG_IMAGE_NEAREST or NVG_IMAGE_NODELETE)
    }

    fun image(image: Int, textureWidth: Int, textureHeight: Int, subX: Int, subY: Int, subW: Int, subH: Int, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        if (image == -1) return

        val sx = subX.toFloat() / textureWidth
        val sy = subY.toFloat() / textureHeight
        val sw = subW.toFloat() / textureWidth
        val sh = subH.toFloat() / textureHeight

        val iw = w / sw
        val ih = h / sh
        val ix = x - iw * sx
        val iy = y - ih * sy

        nvgImagePattern(vg, ix, iy, iw, ih, 0f, image, 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h + .5f, radius)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun image(image: Image, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        nvgImagePattern(vg, x, y, w, h, 0f, getImage(image), 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x, y, w, h + .5f, radius)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun image(image: Image, x: Float, y: Float, w: Float, h: Float) {
        nvgImagePattern(vg, x, y, w, h, 0f, getImage(image), 1f, nvgPaint)
        nvgBeginPath(vg)
        nvgRect(vg, x, y, w, h + .5f)
        nvgFillPaint(vg, nvgPaint)
        nvgFill(vg)
    }

    fun createImage(resourcePath: String): Image {
        val image = images.keys.find { it.identifier == resourcePath } ?: Image(resourcePath)
        if (image.isSVG) images.getOrPut(image) { NVGImage(0, loadSVG(image)) }.count++
        else images.getOrPut(image) { NVGImage(0, loadImage(image)) }.count++
        return image
    }

    // lowers reference count by 1, if it reaches 0 it gets deleted from mem
    fun deleteImage(image: Image) {
        val nvgImage = images[image] ?: return
        nvgImage.count--
        if (nvgImage.count == 0) {
            nvgDeleteImage(vg, nvgImage.nvg)
            images.remove(image)
        }
    }

    private fun getImage(image: Image): Int {
        return images[image]?.nvg ?: throw IllegalStateException("Image (${image.identifier}) doesn't exist")
    }

    private fun loadImage(image: Image): Int {
        val w = IntArray(1)
        val h = IntArray(1)
        val channels = IntArray(1)
        val buffer = stbi_load_from_memory(
            image.buffer(),
            w,
            h,
            channels,
            4
        ) ?: throw NullPointerException("Failed to load image: ${image.identifier}")
        return withCoherentTextureUnit { nvgCreateImageRGBA(vg, w[0], h[0], 0, buffer) }
    }

    private fun loadSVG(image: Image): Int {
        val vec = image.stream.use { it.bufferedReader().readText() }
        val svg = nsvgParse(vec, "px", 96f) ?: throw IllegalStateException("Failed to parse ${image.identifier}")

        val width = svg.width().toInt()
        val height = svg.height().toInt()
        val buffer = memAlloc(width * height * 4)

        try {
            val rasterizer = nsvgCreateRasterizer()
            nsvgRasterize(rasterizer, svg, 0f, 0f, 1f, buffer, width, height, width * 4)
            val nvgImage = withCoherentTextureUnit { nvgCreateImageRGBA(vg, width, height, 0, buffer) }
            nsvgDeleteRasterizer(rasterizer)
            return nvgImage
        } finally {
            nsvgDelete(svg)
            memFree(buffer)
        }
    }

    private fun color(color: Int) {
        nvgRGBA(color.red.toByte(), color.green.toByte(), color.blue.toByte(), color.alpha.toByte(), nvgColor)
    }

    private fun color(color1: Int, color2: Int) {
        nvgRGBA(color1.red.toByte(), color1.green.toByte(), color1.blue.toByte(), color1.alpha.toByte(), nvgColor)
        nvgRGBA(color2.red.toByte(), color2.green.toByte(), color2.blue.toByte(), color2.alpha.toByte(), nvgColor2)
    }

    private fun gradient(color1: Int, color2: Int, x: Float, y: Float, w: Float, h: Float, direction: Gradient) {
        color(color1, color2)
        when (direction) {
            Gradient.LeftToRight -> nvgLinearGradient(vg, x, y, x + w, y, nvgColor, nvgColor2, nvgPaint)
            Gradient.TopToBottom -> nvgLinearGradient(vg, x, y, x, y + h, nvgColor, nvgColor2, nvgPaint)
        }
    }

    private fun getFontID(font: Font): Int {
        return fontMap.getOrPut(font) {
            val buffer = font.buffer()
            NVGFont(nvgCreateFontMem(vg, font.name, buffer, false), buffer)
        }.id
    }

    private class Scissor(
        val previous: Scissor?,
        val x: Float,
        val y: Float,
        val maxX: Float,
        val maxY: Float,
        /** Pre-intersected frame-space rect `[x0, y0, x1, y1]` captured at push time. */
        val frameRect: FloatArray
    ) {
        fun applyScissor() {
            if (previous == null) nvgScissor(vg, x, y, maxX - x, maxY - y)
            else {
                val x = max(x, previous.x)
                val y = max(y, previous.y)
                val width = max(0f, (min(maxX, previous.maxX) - x))
                val height = max(0f, (min(maxY, previous.maxY) - y))
                nvgScissor(vg, x, y, width, height)
            }
        }
    }

    private data class NVGImage(var count: Int, val nvg: Int)
    private data class NVGFont(val id: Int, val buffer: ByteBuffer)
}
