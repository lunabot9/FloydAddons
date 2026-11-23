package gg.floyd.utils.font

import java.lang.reflect.Proxy
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.util.RandomSource
import kotlin.test.Test
import kotlin.test.assertSame

class FloydLiveSymbolFallbackTest {
    @Test fun `pinned letters keep custom font while moon location PUA and supplementary icons use live glyphs`() {
        fun glyph() = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BakedGlyph::class.java)) { _, _, _ -> null } as BakedGlyph
        val customGlyph = glyph()
        val liveGlyph = glyph()
        fun source(value: BakedGlyph) = object : GlyphSource {
            override fun getGlyph(codepoint: Int) = value
            override fun getRandomGlyph(random: RandomSource, codepoint: Int) = value
        }
        val combined = FloydFonts.withLiveSymbols(source(customGlyph), source(liveGlyph))
        for (cp in listOf('A'.code, '7'.code, 0x00E9)) assertSame(customGlyph, combined.getGlyph(cp))
        for (cp in listOf(0x263D, 0x23E3, 0xE001, 0x1F600)) assertSame(liveGlyph, combined.getGlyph(cp))
    }
}
