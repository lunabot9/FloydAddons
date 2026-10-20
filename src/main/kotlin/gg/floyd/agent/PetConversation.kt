package gg.floyd.agent

/** A bounded exchange with one outstanding turn; never starts provider work itself. */
internal class PetConversation {
    data class Turn(val speaker: Int, val context: String)
    @Volatile var active = false; private set
    @Volatile var completed = 0; private set
    @Volatile var limit = 0; private set
    private var waiting = false
    private var nextAt = 0L
    private var topic = ""
    private val lines = mutableListOf<String>()
    @Synchronized fun start(turns: Int, subject: String) {
        limit = turns.coerceIn(3, 12); completed = 0; waiting = false; nextAt = 0
        topic = subject.trim().take(200); lines.clear(); active = true
    }
    @Synchronized fun next(now: Long, allowed: Boolean): Turn? {
        if (!active || waiting || !allowed || now < nextAt) return null
        waiting = true
        return Turn(completed % 3, "Topic: $topic\n" + lines.joinToString("\n"))
    }
    @Synchronized fun complete(name: String, text: String, now: Long) {
        if (!active || !waiting) return
        lines += "$name: ${text.take(600)}"; completed++; waiting = false; nextAt = now + 1500
        if (completed >= limit) active = false
    }
    @Synchronized fun stop() { active = false; waiting = false }
}
