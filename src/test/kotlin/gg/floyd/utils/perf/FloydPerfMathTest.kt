package gg.floyd.utils.perf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloydPerfMathTest {

    @Test
    fun `nearest-rank percentile on a known distribution`() {
        val sorted = longArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        assertEquals(5, FloydPerfMath.percentile(sorted, 10, 50.0))
        assertEquals(10, FloydPerfMath.percentile(sorted, 10, 95.0))
        assertEquals(10, FloydPerfMath.percentile(sorted, 10, 99.0))
        assertEquals(1, FloydPerfMath.percentile(sorted, 10, 1.0))
    }

    @Test
    fun `percentile of single element and empty`() {
        assertEquals(42, FloydPerfMath.percentile(longArrayOf(42), 1, 50.0))
        assertEquals(42, FloydPerfMath.percentile(longArrayOf(42), 1, 99.0))
        assertEquals(0, FloydPerfMath.percentile(longArrayOf(), 0, 50.0))
    }

    @Test
    fun `percentile ignores values beyond len`() {
        val values = longArrayOf(1, 2, 3, 999, 999)
        assertEquals(3, FloydPerfMath.percentile(values, 3, 99.0))
    }

    @Test
    fun `summarizeNanosAsMs converts and orders correctly`() {
        // 1000 frames: 0..999 µs (unsorted on purpose — summarize sorts a copy).
        val values = LongArray(1000) { ((999 - it).toLong()) * 1_000 }
        val summary = FloydPerfMath.summarizeNanosAsMs(values, 1000)
        assertEquals(0.499, summary["p50"]!!, 0.001)
        assertEquals(0.949, summary["p95"]!!, 0.001)
        assertEquals(0.989, summary["p99"]!!, 0.001)
        assertEquals(0.999, summary["max"]!!, 0.0001)
        assertEquals(0.0, summary["min"]!!, 0.0001)
        assertEquals(0.4995, summary["avg"]!!, 0.001)
    }

    @Test
    fun `histogram buckets are monotone and bounded`() {
        var lastBucket = -1
        var ns = 1L
        while (ns in 1 until 20_000_000_000L) {
            val bucket = FloydPerfMath.bucketFor(ns)
            assertTrue(bucket >= lastBucket, "bucketFor must be monotone (ns=$ns)")
            assertTrue(bucket in 0 until FloydPerfMath.HISTOGRAM_BUCKETS)
            lastBucket = bucket
            ns += maxOf(1, ns / 7)
        }
    }

    @Test
    fun `histogram bucket upper bound contains its values`() {
        for (ns in longArrayOf(1, 500, 1024, 5_000, 100_000, 2_000_000, 16_700_000, 1_000_000_000)) {
            val bucket = FloydPerfMath.bucketFor(ns)
            val upper = FloydPerfMath.bucketUpperNs(bucket)
            assertTrue(ns < upper, "ns=$ns must be < upper=$upper of bucket $bucket")
            // Quarter-octave resolution: upper bound within ~+31% of the value above 1µs.
            if (ns >= 1024) assertTrue(upper <= ns * 2, "upper=$upper too coarse for ns=$ns")
        }
    }

    @Test
    fun `histogram percentile returns bucket upper bounds at the right ranks`() {
        val histogram = IntArray(FloydPerfMath.HISTOGRAM_BUCKETS)
        // 90 frames at ~100µs, 10 frames at ~10ms.
        val fast = FloydPerfMath.bucketFor(100_000)
        val slow = FloydPerfMath.bucketFor(10_000_000)
        histogram[fast] = 90
        histogram[slow] = 10
        val p50 = FloydPerfMath.histogramPercentileNs(histogram, 100, 50.0)
        val p99 = FloydPerfMath.histogramPercentileNs(histogram, 100, 99.0)
        assertEquals(FloydPerfMath.bucketUpperNs(fast), p50)
        assertEquals(FloydPerfMath.bucketUpperNs(slow), p99)
        assertEquals(0, FloydPerfMath.histogramPercentileNs(histogram, 0, 50.0))
    }

    @Test
    fun `window records frames and sections per frame`() {
        val window = FloydPerf.Window(sections = true, maxFrames = 100, targetNanos = Long.MAX_VALUE)

        // Frame 1: section runs twice; Frame 2: section absent; Frame 3: runs once.
        window.record("Esp.Extract", 1_000, 64)
        window.record("Esp.Extract", 2_000, 64)
        window.closeFrame(16_000_000, 1024)
        window.closeFrame(17_000_000, 2048)
        window.record("Esp.Extract", 5_000, 128)
        window.closeFrame(18_000_000, 4096)

        assertEquals(3, window.frames)
        assertEquals(16_000_000, window.frameNs[0])
        assertEquals(4096, window.frameAllocBytes[2])

        val acc = window.sectionAccs.getValue("Esp.Extract")
        assertEquals(2, acc.framesSeen)
        assertEquals(3, acc.totalCalls)
        assertEquals(8_000, acc.totalNs)
        assertEquals(256, acc.totalBytes)
        assertEquals(5_000, acc.maxFrameNs)
        assertEquals(2, acc.histogram.sum())
    }

    @Test
    fun `section summary percentiles only cover frames seen`() {
        val acc = FloydPerf.SectionAcc()
        // Seen in 2 of 10 frames: 100µs and 300µs.
        acc.curNs = 100_000; acc.curCalls = 1; acc.closeFrame()
        acc.closeFrame() // no calls — not recorded
        acc.curNs = 300_000; acc.curCalls = 1; acc.closeFrame()

        val summary = FloydPerfMath.summarizeSection(acc, totalFrames = 10, durationSec = 1.0)
        assertEquals(2, summary["framesSeen"])
        val p50 = summary["p50us"] as Double
        assertTrue(p50 >= 100.0 && p50 <= 131.0, "p50us=$p50 should be the ~100us bucket upper bound")
        val p95 = summary["p95us"] as Double
        assertTrue(p95 >= 300.0 && p95 <= 393.0, "p95us=$p95 should be the ~300us bucket upper bound")
        assertEquals(300.0, summary["maxUs"] as Double, 0.01)
        assertEquals(1.0, summary["callsPerFrame"] as Double, 0.001)
        assertEquals(0.4, summary["totalMs"] as Double, 0.001)
    }

    @Test
    fun `window stops recording at maxFrames`() {
        val window = FloydPerf.Window(sections = false, maxFrames = 2, targetNanos = Long.MAX_VALUE)
        window.closeFrame(1, 0)
        window.closeFrame(2, 0)
        window.closeFrame(3, 0)
        assertEquals(2, window.frames)
    }

    @Test
    fun `negative alloc deltas clamp to zero`() {
        val window = FloydPerf.Window(sections = true, maxFrames = 4, targetNanos = Long.MAX_VALUE)
        window.record("X", 100, -50)
        window.closeFrame(1_000, -512)
        assertEquals(0, window.frameAllocBytes[0])
        assertEquals(0, window.sectionAccs.getValue("X").totalBytes)
    }

    @Test
    fun `counter deltas subtract the window-start snapshot`() {
        val before = mapOf("xrayIsOpaqueCalls" to 1000L, "blockSearchChunkScans" to 5L)
        val after = mapOf("xrayIsOpaqueCalls" to 6000L, "blockSearchChunkScans" to 5L)
        val deltas = FloydPerfCounters.deltas(before, after)
        assertEquals(5000L, deltas["xrayIsOpaqueCalls"])
        assertEquals(0L, deltas["blockSearchChunkScans"])
    }

    @Test
    fun `summarize builds a complete payload`() {
        val window = FloydPerf.Window(sections = true, maxFrames = 10, targetNanos = 1_000_000_000)
        window.startNanos = 0
        window.endNanos = 1_000_000_000
        window.startAlloc = 0
        window.endAlloc = 50_000_000
        window.counters0 = mapOf("xrayIsOpaqueCalls" to 0L)
        window.counters1 = mapOf("xrayIsOpaqueCalls" to 123L)
        repeat(5) {
            window.record("A", 200_000, 1024)
            window.closeFrame(10_000_000, 100_000)
        }

        val payload = FloydPerf.summarize(window, includeSections = true)
        assertEquals(5, payload["frames"])
        assertEquals(5.0, payload["fps"] as Double, 0.01)
        @Suppress("UNCHECKED_CAST")
        val frameMs = payload["frameMs"] as Map<String, Double>
        assertEquals(10.0, frameMs["p50"]!!, 0.01)
        @Suppress("UNCHECKED_CAST")
        val sections = payload["sections"] as Map<String, Map<String, Any?>>
        assertTrue("A" in sections)
        assertEquals(5, sections.getValue("A")["framesSeen"])
        @Suppress("UNCHECKED_CAST")
        val counters = payload["counters"] as Map<String, Long>
        assertEquals(123L, counters["xrayIsOpaqueCalls"])
    }

    @Test
    fun `startWindow rejects NaN and busy states, cancel unblocks`() {
        // NaN must be rejected up front (NaN comparisons are all false — coerceIn passes it through).
        assertThrows(IllegalArgumentException::class.java) { FloydPerf.startWindow(Double.NaN, false) }

        val first = FloydPerf.startWindow(5.0, false)
        assertThrows(IllegalArgumentException::class.java) { FloydPerf.startWindow(5.0, false) }
        // Cancelling the pending window frees the slot immediately.
        FloydPerf.cancel(first)
        assertTrue(first.future.isCompletedExceptionally)
        val second = FloydPerf.startWindow(5.0, false)
        FloydPerf.cancel(second) // leave the harness clean for other tests
    }
}
