package gg.floyd.features.impl.pvp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydPlayerEspTest {

    @Test
    fun `overhead plate grows as the player gets closer`() {
        // pxPerBlock rises as the player approaches; the plate scale must rise with it (the bug was
        // the inverse: it shrank as you got closer).
        val near = FloydPlayerEsp.overheadScaleFactor(pxPerBlock = 80f, scaleMultiplier = 1f)
        val far = FloydPlayerEsp.overheadScaleFactor(pxPerBlock = 20f, scaleMultiplier = 1f)
        assertTrue(near > far, "plate must be larger when nearer (got near=$near far=$far)")
    }

    @Test
    fun `overhead scale has no floor and tracks distance proportionally`() {
        // 4x closer (4x the gui-px-per-block) => exactly 4x the scale: no min/max cap.
        val close = FloydPlayerEsp.overheadScaleFactor(pxPerBlock = 80f, scaleMultiplier = 1f)
        val veryFar = FloydPlayerEsp.overheadScaleFactor(pxPerBlock = 20f, scaleMultiplier = 1f)
        assertTrue(abs(close - 4f * veryFar) < 1e-4f, "scale must be proportional to pxPerBlock")
        assertTrue(FloydPlayerEsp.overheadScaleFactor(2f, 1f) > 0f, "tiny-but-positive depth still scales (no floor)")
    }

    @Test
    fun `overhead scale setting is a linear multiplier`() {
        val base = FloydPlayerEsp.overheadScaleFactor(pxPerBlock = 40f, scaleMultiplier = 1f)
        val doubled = FloydPlayerEsp.overheadScaleFactor(pxPerBlock = 40f, scaleMultiplier = 2f)
        assertTrue(abs(doubled - 2f * base) < 1e-4f, "Scale=2 must be exactly twice Scale=1")
    }

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
