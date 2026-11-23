package gg.floyd.features.impl.render

/** Parses server readiness signals and deduplicates chat/tab reports of the same transition. */
internal class MiningAbilityReadyTracker {
    data class Signal(val ability: String, val source: String)
    private val readyInTab = mutableMapOf<String, Boolean>()
    private val lastAlert = mutableMapOf<String, Long>()

    fun chat(message: String, nowMs: Long): Signal? {
        val match = chatPattern.matchEntire(clean(message)) ?: return null
        val ability = canonical(match.groupValues[1])
        readyInTab[ability] = true
        return alert(ability, "chat", nowMs)
    }

    fun tab(lines: Iterable<String>, nowMs: Long): List<Signal> = buildList {
        for (line in lines.flatMap { it.lineSequence().toList() }) {
            val match = tabPattern.matchEntire(clean(line)) ?: continue
            val ability = canonical(match.groupValues[1])
            val value = match.groupValues[2].trim().trimEnd('!', '.')
            val ready = when {
                value.equals("ready", true) || value.equals("available", true) -> true
                value.equals("active", true) -> false
                value.matches(Regex("[0-9smh:.\\s]+", RegexOption.IGNORE_CASE)) && value.any(Char::isDigit) ->
                    Regex("[0-9]+(?:\\.[0-9]+)?").findAll(value).all { it.value.toDouble() == 0.0 }
                else -> continue
            }
            val previous = readyInTab.put(ability, ready)
            if (ready && previous != true) alert(ability, "tab", nowMs)?.let(::add)
        }
    }

    fun reset() { readyInTab.clear(); lastAlert.clear() }

    private fun alert(ability: String, source: String, nowMs: Long): Signal? {
        val previous = lastAlert[ability]
        if (previous != null && nowMs - previous in 0 until 2_000L) return null
        lastAlert[ability] = nowMs
        return Signal(ability, source)
    }

    companion object {
        val abilities = listOf("Pickobulus", "Mining Speed Boost", "Maniac Miner", "Tunnel Vision", "Sheer Force", "Gemstone Infusion")
        private val names = (abilities + listOf("Pickobulous", "Pikobulous")).joinToString("|") { Regex.escape(it) }
        private val chatPattern = Regex("(?:Your )?($names) (?:is now available|is now ready|is ready)[!.]?", RegexOption.IGNORE_CASE)
        private val tabPattern = Regex("($names)\\s*:\\s*(.+)", RegexOption.IGNORE_CASE)
        private fun canonical(name: String) = abilities.firstOrNull { it.equals(name, true) } ?: "Pickobulus"
        private fun clean(text: String) = text.replace(Regex("\u00A7."), "").replace('\u00A0', ' ').trim().replace(Regex("\\s+"), " ")
    }
}
