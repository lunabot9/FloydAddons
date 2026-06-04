package gg.floyd.features.impl.pvp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydPlayerEspTest {

    // The overhead nameplate is now a world-space billboard sized purely by GPU perspective (no manual
    // screen-scale / smoothing / quantize), so the old overheadScaleFactor/quantizeScale/stickyQuantize
    // helpers are gone. Only the pure single-row layout math remains under test.

    @Test
    fun `overhead layout is a single row of text plus icons`() {
        val dims = FloydPlayerEsp.overheadDimensions(hpWidth = 20, iconCount = 6, padding = 4, fontLineHeight = 9)
        // Single row: height is the 16px icon row plus padding on both sides — NOT health stacked above icons.
        assertEquals(16 + 2 * 4, dims.panelHeight)
        // Width is health text + text/icon gap + six 16px icons with 2px gaps, plus padding.
        assertEquals(20 + 4 + (6 * 16 + 5 * 2) + 2 * 4, dims.panelWidth)
    }

    @Test
    fun `overhead padding expands the panel symmetrically`() {
        val tight = FloydPlayerEsp.overheadDimensions(hpWidth = 20, iconCount = 6, padding = 4, fontLineHeight = 9)
        val loose = FloydPlayerEsp.overheadDimensions(hpWidth = 20, iconCount = 6, padding = 8, fontLineHeight = 9)
        assertEquals(tight.panelWidth + 2 * 4, loose.panelWidth)
        assertEquals(tight.panelHeight + 2 * 4, loose.panelHeight)
    }

    @Test
    fun `overhead health-only row uses the font line height`() {
        val dims = FloydPlayerEsp.overheadDimensions(hpWidth = 20, iconCount = 0, padding = 4, fontLineHeight = 9)
        assertEquals(9 + 2 * 4, dims.panelHeight)
        assertEquals(20 + 2 * 4, dims.panelWidth, "no text/icon gap when there are no icons")
    }

    @Test
    fun `overhead plate collapses to nothing when empty`() {
        val dims = FloydPlayerEsp.overheadDimensions(hpWidth = 0, iconCount = 0, padding = 4, fontLineHeight = 9)
        assertEquals(0, dims.panelWidth)
        assertEquals(0, dims.panelHeight)
    }
}
