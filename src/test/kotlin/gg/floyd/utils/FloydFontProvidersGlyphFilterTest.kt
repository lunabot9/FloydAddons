package gg.floyd.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydFontProvidersGlyphFilterTest {
    @Test
    fun `custom font glyph filter keeps readable latin ranges`() {
        assertTrue(FloydFontProviders.shouldUseCustomFontGlyph('A'.code))
        assertTrue(FloydFontProviders.shouldUseCustomFontGlyph(0x00E9))
        assertTrue(FloydFontProviders.shouldUseCustomFontGlyph(0x2014))
    }

    @Test
    fun `custom font glyph filter yields special glyph ranges to fallback providers`() {
        assertFalse(FloydFontProviders.shouldUseCustomFontGlyph(0xE001))
        assertFalse(FloydFontProviders.shouldUseCustomFontGlyph(0x2728))
        assertFalse(FloydFontProviders.shouldUseCustomFontGlyph(0x1F31F))
    }
}
