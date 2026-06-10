package gg.floyd.utils.font

import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.network.chat.Style
import org.joml.Matrix4f
import kotlin.math.max
import kotlin.math.min

/**
 * MSDF-baked glyph bound to a [MsdfAtlas.Page] and the [MsdfPipelines] pipelines. The quad
 * extents ([left]/[right]/[up]/[down], relative to the pen position with the baseline at y+7)
 * cover the full padded atlas cell, so reported bounds always match the rendered quad.
 * Vertex emission reimplements vanilla `BakedSheetGlyph.renderChar` semantics exactly.
 */
class FloydMsdfGlyph(
    private val glyphInfo: GlyphInfo,
    private val page: MsdfAtlas.Page,
    private val u0: Float,
    private val u1: Float,
    private val v0: Float,
    private val v1: Float,
    internal val left: Float,
    internal val right: Float,
    internal val up: Float,
    internal val down: Float,
) : BakedGlyph {

    override fun info(): GlyphInfo = glyphInfo

    override fun createGlyph(
        x: Float,
        y: Float,
        color: Int,
        shadowColor: Int,
        style: Style,
        boldOffset: Float,
        shadowOffset: Float,
    ): TextRenderable.Styled = FloydMsdfRenderable(this, x, y, color, shadowColor, style, boldOffset, shadowOffset)

    internal fun shearTop(): Float = 1.0F - 0.25F * up

    internal fun shearBottom(): Float = 1.0F - 0.25F * down

    internal fun textureView(): GpuTextureView = page.getTextureView()

    internal fun renderTypeFor(displayMode: Font.DisplayMode): RenderType = when (displayMode) {
        Font.DisplayMode.NORMAL -> page.normalType
        Font.DisplayMode.SEE_THROUGH -> page.seeThroughType
        Font.DisplayMode.POLYGON_OFFSET -> page.polygonOffsetType
    }

    internal fun renderQuad(
        italic: Boolean,
        glyphX: Float,
        glyphY: Float,
        z: Float,
        pose: Matrix4f,
        buffer: VertexConsumer,
        color: Int,
        bold: Boolean,
        light: Int,
    ) {
        val x0 = glyphX + left
        val x1 = glyphX + right
        val y0 = glyphY + up
        val y1 = glyphY + down
        val shearTop = if (italic) shearTop() else 0.0F
        val shearBottom = if (italic) shearBottom() else 0.0F
        val thickness = extraThickness(bold)
        buffer.addVertex(pose, x0 + shearTop - thickness, y0 - thickness, z).setColor(color).setUv(u0, v0).setLight(light)
        buffer.addVertex(pose, x0 + shearBottom - thickness, y1 + thickness, z).setColor(color).setUv(u0, v1).setLight(light)
        buffer.addVertex(pose, x1 + shearBottom + thickness, y1 + thickness, z).setColor(color).setUv(u1, v1).setLight(light)
        buffer.addVertex(pose, x1 + shearTop + thickness, y0 - thickness, z).setColor(color).setUv(u1, v0).setLight(light)
    }
}

class FloydMsdfRenderable internal constructor(
    private val glyph: FloydMsdfGlyph,
    private val x: Float,
    private val y: Float,
    private val color: Int,
    private val shadowColor: Int,
    private val textStyle: Style,
    private val boldOffset: Float,
    private val shadowOffset: Float,
) : TextRenderable.Styled {

    private fun hasShadow(): Boolean = shadowColor != 0

    override fun style(): Style = textStyle

    override fun left(): Float =
        x + glyph.left +
            (if (textStyle.isItalic) min(glyph.shearTop(), glyph.shearBottom()) else 0.0F) -
            extraThickness(textStyle.isBold)

    override fun top(): Float = y + glyph.up - extraThickness(textStyle.isBold)

    override fun right(): Float =
        x + glyph.right +
            (if (hasShadow()) shadowOffset else 0.0F) +
            (if (textStyle.isItalic) max(glyph.shearTop(), glyph.shearBottom()) else 0.0F) +
            extraThickness(textStyle.isBold)

    override fun bottom(): Float =
        y + glyph.down + (if (hasShadow()) shadowOffset else 0.0F) + extraThickness(textStyle.isBold)

    /**
     * Hit-test extents (ActiveTextCollector.findElementUnderCursor). The visual extents above are
     * the FULL padded atlas cell — inheriting TextRenderable.Styled's defaults (activeLeft()=left()
     * etc.) would fatten chat click/hover hitboxes far past the glyph's advance. Vanilla's
     * BakedSheetGlyph.GlyphInstance overrides only activeRight() = x + advance(bold) and inherits
     * tight bearing-based boxes for the other three edges; our tight equivalent is the pen box
     * vanilla EmptyArea uses for advances without sprites: [x, x+advance] x [y, y+9] (EmptyArea
     * with DEFAULT_ASCENT=7/DEFAULT_HEIGHT=9: activeLeft=x, activeTop=y+7-7=y, activeBottom=y+9).
     */
    override fun activeLeft(): Float = x

    override fun activeTop(): Float = y

    override fun activeRight(): Float = x + glyph.info().getAdvance(textStyle.isBold)

    override fun activeBottom(): Float = y + 9.0F

    override fun render(pose: Matrix4f, buffer: VertexConsumer, light: Int, gui: Boolean) {
        val italic = textStyle.isItalic
        val bold = textStyle.isBold
        val boldZ = if (gui) 0.0F else Z_FIGHTER
        var mainZ = 0.0F
        if (hasShadow()) {
            glyph.renderQuad(italic, x + shadowOffset, y + shadowOffset, 0.0F, pose, buffer, shadowColor, bold, light)
            if (bold) {
                glyph.renderQuad(italic, x + boldOffset + shadowOffset, y + shadowOffset, boldZ, pose, buffer, shadowColor, true, light)
            }
            mainZ = if (gui) 0.0F else 0.03F
        }
        glyph.renderQuad(italic, x, y, mainZ, pose, buffer, color, bold, light)
        if (bold) {
            glyph.renderQuad(italic, x + boldOffset, y, mainZ + boldZ, pose, buffer, color, true, light)
        }
    }

    override fun renderType(displayMode: Font.DisplayMode): RenderType = glyph.renderTypeFor(displayMode)

    override fun textureView(): GpuTextureView = glyph.textureView()

    override fun guiPipeline(): RenderPipeline = MsdfPipelines.GUI
}

private const val Z_FIGHTER = 0.001F

private fun extraThickness(bold: Boolean): Float = if (bold) 0.1F else 0.0F
