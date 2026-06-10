package gg.floyd.utils.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MsdfEncodeTest {

    private fun encodeUnsigned(value: Float): Int = encodeMsdfChannel(value).toInt() and 0xFF

    @Test
    fun `the glyph edge at 0point5 maps to 128`() {
        // The shader edge test is `sd - 0.5`; the encode must keep the 0.5 midpoint at 128.
        assertEquals(128, encodeUnsigned(0.5f))
    }

    @Test
    fun `unclamped msdfgen output across the spike range clamps to 0 and 255`() {
        // Spike-observed raw msdfgen output spans roughly -2.9..4.1 (D5: encode clamps verbatim).
        assertEquals(0, encodeUnsigned(-2.9f))
        assertEquals(0, encodeUnsigned(-0.0001f))
        assertEquals(255, encodeUnsigned(1.0001f))
        assertEquals(255, encodeUnsigned(4.1f))
        assertEquals(0, encodeUnsigned(Float.NEGATIVE_INFINITY))
        assertEquals(255, encodeUnsigned(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `in range values quantize by rounding`() {
        assertEquals(0, encodeUnsigned(0.0f))
        assertEquals(255, encodeUnsigned(1.0f))
        assertEquals(64, encodeUnsigned(0.25f))
        assertEquals(191, encodeUnsigned(0.75f))
    }

    @Test
    fun `encode is monotonic over the spike range`() {
        var previous = -1
        var value = -2.9f
        while (value <= 4.1f) {
            val encoded = encodeUnsigned(value)
            assertTrue(encoded >= previous, "encode($value)=$encoded < $previous")
            previous = encoded
            value += 0.01f
        }
        assertEquals(255, previous)
    }
}
