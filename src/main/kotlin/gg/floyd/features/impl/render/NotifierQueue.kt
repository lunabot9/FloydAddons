package gg.floyd.features.impl.render

/** Clock-injected and render-independent so bursts, expiry and animation boundaries are deterministic. */
internal class NotifierQueue(private val clockMs: () -> Long) {
    data class Entry(val name: String, val enabled: Boolean, val startedMs: Long, val durationMs: Long)
    data class Visible(val entry: Entry, val opacity: Float, val progress: Float)

    private val entries = ArrayList<Entry>()
    var triggerCount: Long = 0
        private set

    fun add(name: String, enabled: Boolean, durationMs: Long, capacity: Int) {
        val now = clockMs()
        prune(now)
        // A rapidly repeated key updates its card instead of filling the screen with duplicates.
        entries.removeAll { it.name == name }
        entries.add(Entry(name, enabled, now, durationMs.coerceAtLeast(500L)))
        trim(capacity)
        triggerCount++
    }

    fun visible(capacity: Int): List<Visible> {
        val now = clockMs()
        prune(now)
        trim(capacity)
        return entries.asReversed().map { entry ->
            val age = (now - entry.startedMs).coerceAtLeast(0L)
            val entering = (age / ANIMATION_MS.toFloat()).coerceIn(0f, 1f)
            val exiting = ((entry.durationMs - age) / ANIMATION_MS.toFloat()).coerceIn(0f, 1f)
            val fraction = minOf(entering, exiting)
            val ease = fraction * fraction * (3f - 2f * fraction)
            Visible(entry, ease, (1f - age.toFloat() / entry.durationMs).coerceIn(0f, 1f))
        }
    }

    fun clear() = entries.clear()

    private fun prune(now: Long) { entries.removeAll { now - it.startedMs >= it.durationMs } }
    private fun trim(capacity: Int) {
        while (entries.size > capacity.coerceIn(1, 5)) entries.removeAt(0)
    }

    companion object { const val ANIMATION_MS = 220L }
}
