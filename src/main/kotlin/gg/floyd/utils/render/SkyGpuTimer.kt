package gg.floyd.utils.render

import org.lwjgl.opengl.GL33C

/** Explicit local-control profiling only; never waits for the GPU or runs during normal play. */
object SkyGpuTimer {
    private val pending = ArrayDeque<Int>()
    private val samples = mutableListOf<Double>()
    private var remaining = 0
    private var active = 0

    fun start(frames: Int) {
        pending.forEach(GL33C::glDeleteQueries)
        pending.clear()
        samples.clear()
        remaining = frames.coerceIn(1, 240)
    }

    fun begin() {
        while (pending.isNotEmpty() && GL33C.glGetQueryObjecti(pending.first(), GL33C.GL_QUERY_RESULT_AVAILABLE) != 0) {
            val id = pending.removeFirst()
            samples += GL33C.glGetQueryObjecti64(id, GL33C.GL_QUERY_RESULT) / 1_000_000.0
            GL33C.glDeleteQueries(id)
        }
        if (remaining <= 0 || pending.size >= 8 || GL33C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL33C.GL_CURRENT_QUERY) != 0) return
        active = GL33C.glGenQueries()
        GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, active)
        remaining--
    }

    fun end() {
        if (active == 0) return
        GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED)
        pending.addLast(active)
        active = 0
    }

    fun state(): Map<String, Any?> {
        val sorted = samples.sorted()
        return mapOf("complete" to (remaining == 0 && pending.isEmpty()), "samples" to samples.size,
            "meanMs" to samples.takeIf { it.isNotEmpty() }?.average(),
            "medianMs" to sorted.getOrNull(sorted.size / 2),
            "p95Ms" to sorted.getOrNull(((sorted.size - 1) * .95).toInt()))
    }
}
