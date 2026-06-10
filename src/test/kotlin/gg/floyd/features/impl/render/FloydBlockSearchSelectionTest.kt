package gg.floyd.features.impl.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class FloydBlockSearchSelectionTest {

    private fun assertSelectsKSmallest(dist: LongArray, k: Int) {
        val len = dist.size
        val pos = LongArray(len) { it.toLong() } // identity payload to verify tandem swaps
        val expected = dist.sorted().take(k).sorted()
        val distCopy = dist.copyOf()

        FloydBlockSearchSelection.quickselectK(distCopy, pos, len, k)

        val selected = distCopy.take(k).sorted()
        assertEquals(expected, selected, "k smallest distances must be selected")
        // Tandem invariant: each payload still pairs with its original distance.
        for (i in 0 until len) {
            assertEquals(dist[pos[i].toInt()], distCopy[i], "pos[$i] must still pair with its distance")
        }
    }

    @Test
    fun `selects k smallest on random input`() {
        val rng = Random(42)
        assertSelectsKSmallest(LongArray(5000) { rng.nextLong(0, 1_000_000) }, 500)
    }

    @Test
    fun `near-sorted chunk-major input (the real fill pattern)`() {
        // Ascending runs with jitter — what chunk-major iteration produces.
        val rng = Random(7)
        val dist = LongArray(20_000) { (it / 100).toLong() * 1000 + rng.nextLong(0, 100) }
        assertSelectsKSmallest(dist, 10_000)
    }

    @Test
    fun `heavy duplicates (shell radii)`() {
        val rng = Random(3)
        assertSelectsKSmallest(LongArray(10_000) { rng.nextLong(0, 16) }, 1_000)
    }

    @Test
    fun `descending input`() {
        assertSelectsKSmallest(LongArray(1000) { (1000 - it).toLong() }, 100)
    }

    @Test
    fun `k equals or exceeds len is a no-op`() {
        val dist = longArrayOf(5, 3, 1)
        val pos = longArrayOf(0, 1, 2)
        FloydBlockSearchSelection.quickselectK(dist, pos, 3, 3)
        assertTrue(dist.contentEquals(longArrayOf(5, 3, 1)))
        FloydBlockSearchSelection.quickselectK(dist, pos, 3, 5)
        assertTrue(dist.contentEquals(longArrayOf(5, 3, 1)))
    }

    @Test
    fun `k of 1 and len boundary`() {
        assertSelectsKSmallest(longArrayOf(9, 2, 7, 4, 4, 11), 1)
        assertSelectsKSmallest(longArrayOf(9, 2, 7, 4, 4, 11), 5)
    }

    @Test
    fun `all-equal distances`() {
        assertSelectsKSmallest(LongArray(5000) { 77L }, 1000)
    }

    @Test
    fun `len ignores trailing array capacity`() {
        // Arrays are retained scratch larger than len; values past len must not be selected.
        val dist = longArrayOf(10, 20, 30, 0, 0, 0)
        val pos = LongArray(6) { it.toLong() }
        FloydBlockSearchSelection.quickselectK(dist, pos, 3, 2)
        assertEquals(listOf(10L, 20L), dist.take(2).sorted())
    }
}
