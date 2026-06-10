package gg.floyd.utils.perf

import java.lang.management.ManagementFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.LongAdder

/**
 * Frame-time / per-feature-section / allocation measurement harness, exposed as GET /perf on the
 * FloydLocalControl bridge.
 *
 * Threading model: [onFrameStart] (the Minecraft.runTick HEAD mixin) and all section recording run
 * on the render thread — which is also the client main thread. A sampling window is ARMED from an
 * HTTP thread via [startWindow]; the render thread promotes it at the next frame boundary, records
 * into preallocated/fixed-size structures, and completes the future with the raw [Window]. The
 * HTTP thread then computes percentiles via [summarize]. Arm/promote/finish/cancel all run under
 * this object's monitor (rare, never per-frame); the per-frame path is volatile reads only, and
 * the idle path (no window) is two volatile null-checks.
 *
 * Frame timing is HEAD-to-HEAD of Minecraft.runTick: the full frame period including swap/vsync
 * wait, i.e. 1/fps. Whole-frame times/allocs use exact preallocated arrays (built on the HTTP
 * thread, outside the measurement). Per-SECTION per-frame times use fixed-size quarter-octave log
 * histograms (~0.5 KB/section, allocated lazily at ~1 KB first sight): section percentiles are
 * bucket upper bounds (≤ ~9% high), while section max/totals are exact. This keeps the probes from
 * injecting measurable allocation into the window they are measuring.
 *
 * Section semantics: INCLUSIVE wall time + allocated bytes. Sections may nest (PostHud.* children
 * nest under PostHud.total; ClickGUI.textReplay under ClickGUI.nvgPip) — do not sum a parent with
 * its children.
 */
object FloydPerf {

    private val threadMx: com.sun.management.ThreadMXBean? =
        ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean

    @JvmStatic
    val allocSupported: Boolean =
        runCatching { threadMx?.isThreadAllocatedMemorySupported == true }.getOrDefault(false)

    /** Fast static gate read by every section wrap site (incl. the EventBus invoker loop). */
    @JvmStatic
    @Volatile
    var sectionsArmed: Boolean = false
        private set

    const val NOT_RECORDING = -1

    @Volatile private var pendingWindow: Window? = null
    @Volatile private var activeWindow: Window? = null
    @Volatile private var renderThread: Thread? = null

    // Render-thread-only state.
    private var lastFrameStartNanos = 0L
    private var lastFrameStartAlloc = 0L

    // Render-thread-only section nesting stack (sections are inclusive; nesting is allowed).
    private const val MAX_DEPTH = 64
    private val stackT0 = LongArray(MAX_DEPTH)
    private val stackA0 = LongArray(MAX_DEPTH)
    private var stackDepth = 0

    private fun currentAlloc(): Long =
        if (allocSupported) threadMx!!.currentThreadAllocatedBytes else 0L

    /**
     * Arms a sampling window; the render thread promotes it at the next frame boundary and the
     * window's future completes with the window itself once [seconds] have elapsed (or the frame
     * cap is hit). Throws [IllegalArgumentException] ("perf_busy") if a live (non-cancelled)
     * window is already pending or active.
     */
    @Synchronized
    fun startWindow(seconds: Double, sections: Boolean): Window {
        if (!seconds.isFinite()) throw IllegalArgumentException("seconds_out_of_range")
        val pending = pendingWindow
        val active = activeWindow
        if ((pending != null && !pending.cancelled) || (active != null && !active.cancelled)) {
            throw IllegalArgumentException("perf_busy")
        }
        if (pending?.cancelled == true) pendingWindow = null
        val clamped = seconds.coerceIn(1.0, 120.0)
        val maxFrames = (clamped * 2000).toInt().coerceAtMost(120_000)
        val window = Window(sections, maxFrames, (clamped * 1e9).toLong())
        pendingWindow = window
        return window
    }

    /**
     * Cancels exactly [window] (HTTP-side timeout recovery). A cancelled-but-still-active window
     * is reaped by the render thread at its next frame boundary and does not block [startWindow].
     */
    @Synchronized
    fun cancel(window: Window) {
        window.cancelled = true
        if (pendingWindow === window) pendingWindow = null
        if (activeWindow === window) sectionsArmed = false
        window.future.completeExceptionally(IllegalStateException("perf_cancelled"))
    }

    /** Frame boundary — called at Minecraft.runTick HEAD by PerfFrameMixin, render thread only. */
    @JvmStatic
    fun onFrameStart() {
        val active = activeWindow
        if (active == null) {
            if (pendingWindow != null) promote()
            return
        }
        if (active.cancelled) {
            synchronized(this) {
                if (activeWindow === active) {
                    activeWindow = null
                    sectionsArmed = false
                }
            }
            return
        }
        val now = System.nanoTime()
        active.closeFrame(now - lastFrameStartNanos, currentAlloc() - lastFrameStartAlloc)
        if (now - active.startNanos >= active.targetNanos || active.frames >= active.maxFrames) {
            finish(active, now)
            return
        }
        lastFrameStartNanos = now
        lastFrameStartAlloc = currentAlloc()
    }

    private fun promote() {
        synchronized(this) {
            val pending = pendingWindow ?: return
            pendingWindow = null
            if (pending.cancelled) return
            if (renderThread == null) renderThread = Thread.currentThread()
            val now = System.nanoTime()
            pending.startNanos = now
            pending.startAlloc = currentAlloc()
            pending.gcCount0 = gcCount()
            pending.gcTimeMs0 = gcTimeMs()
            pending.counters0 = FloydPerfCounters.snapshot()
            stackDepth = 0
            lastFrameStartNanos = now
            lastFrameStartAlloc = pending.startAlloc
            // Publish active last; sections only arm once the window is fully initialized.
            activeWindow = pending
            if (pending.sections) sectionsArmed = true
        }
    }

    private fun finish(window: Window, now: Long) {
        synchronized(this) {
            sectionsArmed = false
            if (activeWindow === window) activeWindow = null
        }
        window.endNanos = now
        window.endAlloc = currentAlloc()
        window.gcCount1 = gcCount()
        window.gcTimeMs1 = gcTimeMs()
        window.counters1 = FloydPerfCounters.snapshot()
        window.future.complete(window) // no-op if cancel() already completed it exceptionally
    }

    /**
     * Begins a section; returns a depth token, or [NOT_RECORDING] when no sections window is active
     * or the caller is not the render thread. Always pair with [sectionEnd] in a finally block —
     * the token-based depth restore self-heals if an inner end is skipped by an exception.
     */
    @JvmStatic
    fun sectionBegin(): Int {
        val window = activeWindow ?: return NOT_RECORDING
        if (!window.sections || Thread.currentThread() !== renderThread) return NOT_RECORDING
        val depth = stackDepth
        if (depth >= MAX_DEPTH) return NOT_RECORDING
        stackT0[depth] = System.nanoTime()
        stackA0[depth] = currentAlloc()
        stackDepth = depth + 1
        return depth
    }

    @JvmStatic
    fun sectionEnd(label: String, token: Int) {
        if (token < 0) return
        stackDepth = token
        val window = activeWindow ?: return
        window.record(label, System.nanoTime() - stackT0[token], currentAlloc() - stackA0[token])
    }

    /** Inclusive-time section wrap for Kotlin call sites. */
    inline fun <T> section(label: String, block: () -> T): T {
        if (!sectionsArmed) return block()
        val token = sectionBegin()
        try {
            return block()
        } finally {
            sectionEnd(label, token)
        }
    }

    private fun gcCount(): Long = ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionCount.coerceAtLeast(0) }
    private fun gcTimeMs(): Long = ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0) }

    /**
     * One sampling window's raw data. All recording happens on the render thread; the HTTP thread
     * only touches it after future completion. Pure JVM (no MC/GL deps) so it is unit-testable.
     * The exact whole-frame arrays are allocated here, i.e. on the ARMING (HTTP) thread, outside
     * the measured window.
     */
    class Window(val sections: Boolean, val maxFrames: Int, val targetNanos: Long) {
        val future = CompletableFuture<Window>()
        @Volatile var cancelled = false

        var startNanos = 0L
        var endNanos = 0L
        var startAlloc = 0L
        var endAlloc = 0L
        var gcCount0 = 0L; var gcCount1 = 0L
        var gcTimeMs0 = 0L; var gcTimeMs1 = 0L
        var counters0: Map<String, Long> = emptyMap()
        var counters1: Map<String, Long> = emptyMap()

        val frameNs = LongArray(maxFrames)
        val frameAllocBytes = LongArray(maxFrames)
        var frames = 0

        val sectionAccs = HashMap<String, SectionAcc>()

        fun record(label: String, ns: Long, bytes: Long) {
            val acc = sectionAccs.getOrPut(label) { SectionAcc() }
            acc.curNs += ns
            acc.curBytes += bytes.coerceAtLeast(0)
            acc.curCalls++
        }

        fun closeFrame(frameTimeNs: Long, allocBytes: Long) {
            if (frames >= maxFrames) return
            val i = frames
            frameNs[i] = frameTimeNs
            frameAllocBytes[i] = allocBytes.coerceAtLeast(0)
            if (sections) {
                for (acc in sectionAccs.values) acc.closeFrame()
            }
            frames = i + 1
        }
    }

    /**
     * Per-section accumulator: fixed-size log histogram of per-frame inclusive ns + exact totals
     * and max. ~1 KB total, so lazy first-sight allocation cannot distort the measured window.
     */
    class SectionAcc {
        val histogram = IntArray(FloydPerfMath.HISTOGRAM_BUCKETS)
        var curNs = 0L
        var curBytes = 0L
        var curCalls = 0
        var totalNs = 0L
        var totalBytes = 0L
        var totalCalls = 0L
        var framesSeen = 0
        var maxFrameNs = 0L

        fun closeFrame() {
            if (curCalls == 0) return
            histogram[FloydPerfMath.bucketFor(curNs)]++
            if (curNs > maxFrameNs) maxFrameNs = curNs
            totalNs += curNs
            totalBytes += curBytes
            totalCalls += curCalls
            framesSeen++
            curNs = 0; curBytes = 0; curCalls = 0
        }
    }

    /** Builds the /perf JSON payload from a completed window. Runs OFF the render thread. */
    fun summarize(window: Window, includeSections: Boolean): Map<String, Any?> {
        val frames = window.frames
        val durationNs = (window.endNanos - window.startNanos).coerceAtLeast(1)
        val durationSec = durationNs / 1e9

        val root = linkedMapOf<String, Any?>(
            "ok" to true,
            "durationMs" to durationNs / 1e6,
            "frames" to frames,
            "fps" to frames / durationSec,
            "frameMs" to FloydPerfMath.summarizeNanosAsMs(window.frameNs, frames),
            "alloc" to linkedMapOf(
                "supported" to allocSupported,
                "renderThreadBytesPerSecond" to ((window.endAlloc - window.startAlloc) / durationSec).toLong(),
                "renderThreadTotalMB" to (window.endAlloc - window.startAlloc) / 1e6,
                "bytesPerFrame" to FloydPerfMath.summarizeLongs(window.frameAllocBytes, frames)
            ),
            "gc" to mapOf(
                "collections" to (window.gcCount1 - window.gcCount0),
                "timeMs" to (window.gcTimeMs1 - window.gcTimeMs0)
            ),
            "counters" to FloydPerfCounters.deltas(window.counters0, window.counters1)
        )

        if (includeSections) {
            root["sectionsNote"] = "Inclusive times — PostHud.* nests under PostHud.total, ClickGUI.textReplay under " +
                "ClickGUI.nvgPip; do not sum parents with children. p50/p95/p99 are log-bucket upper bounds (<= ~9% high); max is exact."
            root["sections"] = window.sectionAccs.entries
                .sortedByDescending { it.value.totalNs }
                .associate { (label, acc) ->
                    label to FloydPerfMath.summarizeSection(acc, frames, durationSec)
                }
        }
        return root
    }
}

/**
 * Always-on cross-thread counters for hot paths where per-call nanoTime would distort the
 * measurement (per-block worker-thread work, per-packet patches). LongAdder increments are a few
 * ns uncontended and scale across Sodium's section-compile workers. /perf reports window deltas.
 */
object FloydPerfCounters {
    @JvmField val xrayIsOpaqueCalls = LongAdder()
    @JvmField val blockSearchBlockChanges = LongAdder()
    @JvmField val blockSearchChunkScans = LongAdder()

    fun snapshot(): Map<String, Long> = mapOf(
        "xrayIsOpaqueCalls" to xrayIsOpaqueCalls.sum(),
        "blockSearchBlockChanges" to blockSearchBlockChanges.sum(),
        "blockSearchChunkScans" to blockSearchChunkScans.sum()
    )

    fun deltas(before: Map<String, Long>, after: Map<String, Long>): Map<String, Long> =
        after.mapValues { (key, value) -> value - (before[key] ?: 0L) }
}

/** Pure percentile/summary math — unit-tested without any MC dependency. */
internal object FloydPerfMath {

    /**
     * Quarter-octave log2 histogram over per-frame section nanos: bucket 0 covers [0, 1280ns);
     * from 2^10 ns (~1 us) upward each octave splits into 4 buckets. 96 buckets reach ~17 s.
     */
    const val HISTOGRAM_BUCKETS = 96

    fun bucketFor(ns: Long): Int {
        if (ns < 1024) return 0
        val octave = 63 - java.lang.Long.numberOfLeadingZeros(ns)
        val quarter = ((ns ushr (octave - 2)) and 3L).toInt()
        return ((octave - 10) * 4 + quarter).coerceIn(0, HISTOGRAM_BUCKETS - 1)
    }

    /** Upper bound (exclusive) of bucket [index] in ns. */
    fun bucketUpperNs(index: Int): Long {
        val octave = index / 4 + 10
        val quarter = index % 4
        return (1L shl (octave - 2)) * (4 + quarter + 1)
    }

    fun histogramPercentileNs(histogram: IntArray, count: Int, p: Double): Long {
        if (count <= 0) return 0
        val rank = Math.ceil(p / 100.0 * count).toInt().coerceIn(1, count)
        var cumulative = 0
        for (i in histogram.indices) {
            cumulative += histogram[i]
            if (cumulative >= rank) return bucketUpperNs(i)
        }
        return bucketUpperNs(histogram.size - 1)
    }

    /** Nearest-rank percentile over the first [len] values of [sorted] (ascending). */
    fun percentile(sorted: LongArray, len: Int, p: Double): Long {
        if (len <= 0) return 0
        val rank = Math.ceil(p / 100.0 * len).toInt().coerceIn(1, len)
        return sorted[rank - 1]
    }

    fun summarizeNanosAsMs(values: LongArray, len: Int): Map<String, Double> {
        if (len <= 0) return mapOf("p50" to 0.0, "p95" to 0.0, "p99" to 0.0, "max" to 0.0, "min" to 0.0, "avg" to 0.0)
        val sorted = values.copyOf(len).also { it.sort() }
        var sum = 0L
        for (i in 0 until len) sum += sorted[i]
        return linkedMapOf(
            "p50" to percentile(sorted, len, 50.0) / 1e6,
            "p95" to percentile(sorted, len, 95.0) / 1e6,
            "p99" to percentile(sorted, len, 99.0) / 1e6,
            "max" to sorted[len - 1] / 1e6,
            "min" to sorted[0] / 1e6,
            "avg" to sum.toDouble() / len / 1e6
        )
    }

    fun summarizeLongs(values: LongArray, len: Int): Map<String, Long> {
        if (len <= 0) return mapOf("p50" to 0L, "p95" to 0L, "p99" to 0L, "max" to 0L)
        val sorted = values.copyOf(len).also { it.sort() }
        return linkedMapOf(
            "p50" to percentile(sorted, len, 50.0),
            "p95" to percentile(sorted, len, 95.0),
            "p99" to percentile(sorted, len, 99.0),
            "max" to sorted[len - 1]
        )
    }

    /**
     * Section summary: percentiles cover frames the section actually RAN in (framesSeen), as
     * log-bucket upper bounds; max/totals are exact; per-second figures are over the whole window.
     */
    fun summarizeSection(acc: FloydPerf.SectionAcc, totalFrames: Int, durationSec: Double): Map<String, Any?> {
        val seen = acc.framesSeen
        return linkedMapOf(
            "p50us" to histogramPercentileNs(acc.histogram, seen, 50.0) / 1e3,
            "p95us" to histogramPercentileNs(acc.histogram, seen, 95.0) / 1e3,
            "p99us" to histogramPercentileNs(acc.histogram, seen, 99.0) / 1e3,
            "maxUs" to acc.maxFrameNs / 1e3,
            "framesSeen" to seen,
            "totalFrames" to totalFrames,
            "callsPerFrame" to if (seen > 0) acc.totalCalls.toDouble() / seen else 0.0,
            "bytesPerFrame" to if (seen > 0) acc.totalBytes / seen else 0L,
            "totalMs" to acc.totalNs / 1e6,
            "msPerSecond" to acc.totalNs / 1e6 / durationSec
        )
    }
}
