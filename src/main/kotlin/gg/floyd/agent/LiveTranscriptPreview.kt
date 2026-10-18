package gg.floyd.agent

/** Reveal recognized words individually, retaining the stable prefix when Whisper revises a draft. */
internal class LiveTranscriptPreview(private val clock: () -> Long = System::currentTimeMillis) {
    private var words = emptyList<String>()
    private var shown = 0
    private var revealedAt = 0L

    @Synchronized fun submit(draft: String) {
        val next = draft.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (next == words) return
        val common = words.zip(next).takeWhile { (a, b) -> a == b }.size
        shown = minOf(shown, common)
        words = next
        if (shown == 0 && words.isNotEmpty()) shown = 1
        revealedAt = clock()
    }

    @Synchronized fun text(): String {
        val now = clock()
        val steps = ((now - revealedAt) / 65L).toInt().coerceAtLeast(0)
        shown = minOf(words.size, shown + steps)
        if (steps > 0) revealedAt += steps * 65L
        return words.take(shown).joinToString(" ")
    }
}
