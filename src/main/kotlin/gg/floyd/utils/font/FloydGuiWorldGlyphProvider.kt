package gg.floyd.utils.font

import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.font.UnbakedGlyph
import gg.floyd.utils.FloydTtfGlyph

/** Retains the vanilla baked glyph and attaches an MSDF rendering counterpart at bake time. */
class FloydGuiWorldGlyphProvider(
    private val standard: GlyphProvider,
    private val msdf: GlyphProvider,
) : GlyphProvider {
    override fun getGlyph(codepoint: Int): UnbakedGlyph? {
        val worldGlyph = standard.getGlyph(codepoint) ?: return null
        val guiGlyph = msdf.getGlyph(codepoint) ?: return worldGlyph
        return object : UnbakedGlyph {
            override fun info() = worldGlyph.info()
            override fun bake(stitcher: UnbakedGlyph.Stitcher) = worldGlyph.bake(stitcher).also { baked ->
                if (baked is FloydTtfGlyph) {
                    val gui = guiGlyph.bake(stitcher)
                    if (gui is FloydMsdfGlyph) baked.`floyd$setGuiGlyph`(gui)
                }
            }
        }
    }

    override fun getSupportedGlyphs() = standard.supportedGlyphs

    override fun close() {
        try { standard.close() } finally { msdf.close() }
    }
}
