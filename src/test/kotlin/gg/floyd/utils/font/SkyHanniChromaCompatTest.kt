package gg.floyd.utils.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SkyHanniChromaCompatTest {

    @Test
    fun `ordinary text colors pass through unchanged`() {
        val original = 0xCC55AAFF.toInt()

        assertEquals(
            original,
            SkyHanniChromaCompat.resolveArgb(
                colorName = null,
                fallbackArgb = original,
                glyphX = 12f,
                glyphY = 34f,
                displayWidth = 1920,
                ticks = 0f,
            ),
        )
        assertEquals(
            original,
            SkyHanniChromaCompat.resolveArgb(
                colorName = "white",
                fallbackArgb = original,
                glyphX = 12f,
                glyphY = 34f,
                displayWidth = 1920,
                ticks = 0f,
            ),
        )
    }

    @Test
    fun `SkyHanni chroma marker becomes animated color and keeps alpha`() {
        val fallback = 0x7FFFFFFF
        val first = SkyHanniChromaCompat.resolveArgb(
            colorName = "chroma",
            fallbackArgb = fallback,
            glyphX = 40f,
            glyphY = 10f,
            displayWidth = 1920,
            ticks = 0f,
        )
        val later = SkyHanniChromaCompat.resolveArgb(
            colorName = "chroma",
            fallbackArgb = fallback,
            glyphX = 40f,
            glyphY = 10f,
            displayWidth = 1920,
            ticks = 30f,
        )

        assertEquals(0x7F000000, first and 0xFF000000.toInt())
        assertEquals(0x7F000000, later and 0xFF000000.toInt())
        assertNotEquals(fallback, first)
        assertNotEquals(first, later)
    }
}
