package gg.floyd.utils.font

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the MC-singleton-free math of [MsdfFontMetrics]: the px-per-font-unit mapping
 * (`sizePx / 9`, matching the renderer's `matrix.scale(sizePx / 9)`), the 9-unit line height and
 * the px→font-unit wrap budget. The FontSet-backed width paths need a live client and are covered
 * by the live verification matrix instead.
 */
class MsdfFontMetricsTest {

    @Test
    fun `scaleFor maps the 9-unit line onto sizePx`() {
        assertEquals(1f, MsdfFontMetrics.scaleFor(9f))
        assertEquals(2f, MsdfFontMetrics.scaleFor(18f))
        assertEquals(0.5f, MsdfFontMetrics.scaleFor(4.5f))
    }

    @Test
    fun `lineHeightPx is lineCount times sizePx`() {
        assertEquals(0f, MsdfFontMetrics.lineHeightPx(0, 16f))
        assertEquals(9f, MsdfFontMetrics.lineHeightPx(1, 9f))
        assertEquals(48f, MsdfFontMetrics.lineHeightPx(3, 16f))
    }

    @Test
    fun `wrapUnitsFor floors the px budget into font units`() {
        // 9px font: 1 px == 1 font unit.
        assertEquals(100, MsdfFontMetrics.wrapUnitsFor(100f, 9f))
        // 18px font: 2 px per unit, fractional units floor (a partial unit can't fit a glyph column).
        assertEquals(50, MsdfFontMetrics.wrapUnitsFor(101f, 18f))
    }

    @Test
    fun `wrapUnitsFor never returns a degenerate budget`() {
        assertEquals(1, MsdfFontMetrics.wrapUnitsFor(0f, 16f))
        assertEquals(1, MsdfFontMetrics.wrapUnitsFor(1f, 16f))
        assertEquals(1, MsdfFontMetrics.wrapUnitsFor(100f, 0f))
        assertEquals(1, MsdfFontMetrics.wrapUnitsFor(100f, -9f))
    }
}
