package gg.floyd.features.impl.pvp.experiments

/** Parse server GUI reward text, keeping advertised milestones separate from earned rewards. */
object ExperimentBonus {
    data class Progress(val earned: Int? = null, val goalRound: Int? = null)
    private val strip = Regex("§.")
    private val milestone = Regex("(?:chain|series|round)(?: of| length|:)?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val bonus = Regex("(?:\\+(\\d+)\\s+(?:Superpairs?\\s+)?clicks?|Superpairs?\\s+clicks?\\s*:?\\s*\\+?(\\d+)|(?:earned|current|bonus)(?:\\s+Superpairs?)?\\s+clicks?\\s*:?\\s*\\+?(\\d+))", RegexOption.IGNORE_CASE)
    fun read(lines: List<String>): Progress {
        var earned: Int? = null
        var goal: Int? = null
        for (raw in lines) {
            val line = strip.replace(raw, "").trim()
            val matches = bonus.findAll(line).toList()
            if (matches.size != 1) continue // Ambiguous lists are never current progress.
            val amount = matches.single().groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() } ?: continue
            val chain = milestone.find(line)?.groupValues?.get(1)?.toIntOrNull()
            if (chain != null) {
                if (amount >= 3 && chain > 0) goal = minOf(goal ?: chain, chain)
            } else if (!Regex("\\b(?:will|reach|complete|up to|possible|maximum|next|requires?|at least)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                // These lines come ONLY from the active round/status controls, never stakes/reward lists.
                earned = maxOf(earned ?: 0, amount)
            }
        }
        return Progress(earned, goal)
    }
}
