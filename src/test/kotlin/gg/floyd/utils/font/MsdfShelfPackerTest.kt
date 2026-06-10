package gg.floyd.utils.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MsdfShelfPackerTest {

    @Test
    fun `placements start at the gutter and are separated by cell plus gutter`() {
        val packer = MsdfShelfPacker(pageSize = 10, cellSize = 3, gutter = 1, maxPages = 2)
        assertEquals(2, packer.cellsPerAxis)

        val first = assertNotNull(packer.place())
        assertEquals(0, first.page)
        assertEquals(1, first.x)
        assertEquals(1, first.y)

        val second = assertNotNull(packer.place())
        assertEquals(1 + 3 + 1, second.x)
        assertEquals(1, second.y)

        val third = assertNotNull(packer.place())
        assertEquals(1, third.x)
        assertEquals(1 + 3 + 1, third.y)
    }

    @Test
    fun `every cell stays inside the page with a trailing gutter`() {
        val packer = MsdfShelfPacker(pageSize = 10, cellSize = 3, gutter = 1, maxPages = 2)
        while (true) {
            val placement = packer.place() ?: break
            assertTrue(placement.x >= 1 && placement.y >= 1, "leading gutter")
            assertTrue(placement.x + 3 + 1 <= 10, "trailing gutter x at ${placement.x}")
            assertTrue(placement.y + 3 + 1 <= 10, "trailing gutter y at ${placement.y}")
        }
    }

    @Test
    fun `pages roll over when full and the cap returns null`() {
        val packer = MsdfShelfPacker(pageSize = 10, cellSize = 3, gutter = 1, maxPages = 2)
        repeat(4) { assertEquals(0, assertNotNull(packer.place()).page) }
        assertEquals(1, packer.pageCount())
        val onSecondPage = assertNotNull(packer.place())
        assertEquals(1, onSecondPage.page)
        assertEquals(1, onSecondPage.x)
        assertEquals(1, onSecondPage.y)
        repeat(3) { assertNotNull(packer.place()) }
        assertEquals(2, packer.pageCount())
        // At the cap (R5): place() must return null, never throw.
        assertNull(packer.place())
        assertNull(packer.place())
    }

    @Test
    fun `usedOnPage reports per page occupancy`() {
        val packer = MsdfShelfPacker(pageSize = 10, cellSize = 3, gutter = 1, maxPages = 2)
        assertEquals(0, packer.usedOnPage(0))
        repeat(5) { packer.place() }
        assertEquals(4, packer.usedOnPage(0))
        assertEquals(1, packer.usedOnPage(1))
    }

    @Test
    fun `a codepoint memo in front of the monotonic packer reuses cells on re-bake`() {
        // Models MsdfGlyphProvider.bakedMemo: FontSet.reload with a surviving provider re-bakes
        // every codepoint; the memo must return the SAME cell instead of advancing the packer.
        val packer = MsdfShelfPacker(pageSize = 10, cellSize = 3, gutter = 1, maxPages = 2)
        val memo = HashMap<Int, MsdfShelfPacker.Placement>()
        fun bake(codepoint: Int): MsdfShelfPacker.Placement =
            memo.getOrPut(codepoint) { assertNotNull(packer.place(), "cap reached for U+$codepoint") }

        val firstBake = (0 until 6).map { bake(it) }
        assertEquals(6, packer.usedOnPage(0) + packer.usedOnPage(1))

        // Re-bake the full working set several times (repeated FontOption toggles).
        repeat(5) {
            val reBake = (0 until 6).map { bake(it) }
            assertEquals(firstBake, reBake, "re-bake must reuse the original placements")
        }
        // The packer never advanced: no leak toward the page cap.
        assertEquals(6, packer.usedOnPage(0) + packer.usedOnPage(1))
        // And the capacity freed by reuse is still placeable (8 cells total here).
        assertNotNull(packer.place())
        assertNotNull(packer.place())
        assertNull(packer.place())
    }

    @Test
    fun `production atlas constants pack 30x30 cells with gutters inside 1024`() {
        val packer = MsdfShelfPacker(
            pageSize = MsdfAtlas.PAGE_SIZE,
            cellSize = MsdfAtlas.CELL_SIZE,
            gutter = MsdfAtlas.GUTTER,
            maxPages = MsdfAtlas.MAX_PAGES,
        )
        assertEquals(30, packer.cellsPerAxis)
        assertEquals(900, packer.cellsPerPage)
        var count = 0
        var last: MsdfShelfPacker.Placement? = null
        while (true) {
            val placement = packer.place() ?: break
            assertTrue(placement.x + MsdfAtlas.CELL_SIZE + MsdfAtlas.GUTTER <= MsdfAtlas.PAGE_SIZE)
            assertTrue(placement.y + MsdfAtlas.CELL_SIZE + MsdfAtlas.GUTTER <= MsdfAtlas.PAGE_SIZE)
            last = placement
            count++
        }
        assertEquals(900 * MsdfAtlas.MAX_PAGES, count)
        assertEquals(MsdfAtlas.MAX_PAGES - 1, assertNotNull(last).page)
    }
}
