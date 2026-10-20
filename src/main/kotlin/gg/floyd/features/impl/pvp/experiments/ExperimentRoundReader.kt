package gg.floyd.features.impl.pvp.experiments

/** Retain the server's observed round while Ultrasequencer hides its numbered tiles. */
class ExperimentRoundReader {
    companion object {
        fun number(name: String, count: Int): Int? = if (name.toIntOrNull() != null && count > 0) count else null
    }
    private var round = 0
    fun reset() { round = 0 }
    fun read(game: ExperimentGame, phase: ExperimentPhase, explicit: Int?, cards: List<ExperimentCard>): Int {
        if (explicit != null && explicit > 0) round = explicit
        else if (game == ExperimentGame.ULTRASEQUENCER && phase == ExperimentPhase.WATCH) {
            val numbers = cards.mapNotNull { it.number }.sorted()
            if (numbers.isNotEmpty() && numbers == (1..numbers.size).toList()) round = maxOf(round, numbers.size)
        }
        return round
    }
}
