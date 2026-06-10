package gg.floyd.utils.render

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the MC-free math of [NvgTextReplay]: the frame-space→GL scissor conversion (×dpr,
 * bottom-left y-flip, slot clamping, empty-clip rejection) and the NVG→mc.font vertical anchor.
 * The drawInBatch replay itself needs a live client (P4 verification matrix).
 */
class NvgTextReplayTest {

    @Test
    fun `null scissor means unclipped`() {
        assertContentEquals(IntArray(0), NvgTextReplay.glScissorRect(null, 2f, 800, 600))
    }

    @Test
    fun `scissor flips to GL bottom-left origin and scales by dpr`() {
        // Frame-space rect x 10..110, y 20..70 at dpr 2 in a 800x600 slot:
        // physical x 20..220, y(top-down) 40..140 -> GL y = 600 - 140 = 460.
        assertContentEquals(
            intArrayOf(20, 460, 200, 100),
            NvgTextReplay.glScissorRect(floatArrayOf(10f, 20f, 110f, 70f), 2f, 800, 600)
        )
    }

    @Test
    fun `scissor clamps to the slot and rejects empty clips`() {
        // Extends past the right/bottom edge -> clamped to the slot.
        assertContentEquals(
            intArrayOf(100, 0, 700, 550),
            NvgTextReplay.glScissorRect(floatArrayOf(100f, 50f, 900f, 700f), 1f, 800, 600)
        )
        // Entirely off-slot or inverted -> null (callers skip the draws).
        assertNull(NvgTextReplay.glScissorRect(floatArrayOf(900f, 50f, 950f, 100f), 1f, 800, 600))
        assertNull(NvgTextReplay.glScissorRect(floatArrayOf(50f, 50f, 40f, 100f), 1f, 800, 600))
        // Degenerate after the expand-anim intersection (zero height).
        assertNull(NvgTextReplay.glScissorRect(floatArrayOf(10f, 30f, 60f, 30f), 1f, 800, 600))
    }

    @Test
    fun `vertical anchor matches the bundled font's hhea ascent minus the mc baseline row`() {
        // Inter 28pt SemiBold: hhea ascent 1984, descent -494; mc baseline at 7 of the 9-unit line.
        assertEquals(1984f / 2478f - 7f / 9f, NvgTextReplay.VERTICAL_ANCHOR)
        // Sanity: the re-anchor is sub-pixel at ClickGUI sizes (<= 22px).
        assertTrue(NvgTextReplay.VERTICAL_ANCHOR * 22f < 1f)
    }
}
