package gg.floyd.utils.font

/**
 * Pure cell-grid packing math for [MsdfAtlas]: fixed-size cells laid out row-major on
 * [pageSize]x[pageSize] pages with [gutter]-px separation on every side (including the page
 * borders), capped at [maxPages]. Holds no GPU state so placement/gutter/cap behavior is unit
 * testable without GL.
 */
class MsdfShelfPacker(
    private val pageSize: Int,
    private val cellSize: Int,
    private val gutter: Int,
    private val maxPages: Int,
) {
    val cellsPerAxis: Int = (pageSize - gutter) / (cellSize + gutter)
    val cellsPerPage: Int = cellsPerAxis * cellsPerAxis

    private var placed = 0

    /** Number of pages any placement has landed on so far. */
    fun pageCount(): Int = (placed + cellsPerPage - 1) / cellsPerPage

    /** Cells used on page [page] (0 when the page was never reached). */
    fun usedOnPage(page: Int): Int =
        (placed - page * cellsPerPage).coerceIn(0, cellsPerPage)

    /** Next cell placement, or null once [maxPages] pages are full (the page cap). */
    fun place(): Placement? {
        if (placed >= cellsPerPage * maxPages) return null
        val page = placed / cellsPerPage
        val index = placed % cellsPerPage
        placed++
        return Placement(
            page,
            gutter + (index % cellsPerAxis) * (cellSize + gutter),
            gutter + (index / cellsPerAxis) * (cellSize + gutter),
        )
    }

    class Placement(val page: Int, val x: Int, val y: Int)
}
