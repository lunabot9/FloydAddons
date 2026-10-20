package gg.floyd.features.impl.pvp.experiments

/** Immutable descriptions of server-owned inventory data; never reads predicted client clicks. */
enum class ExperimentGame { CHRONOMATRON, ULTRASEQUENCER, SUPERPAIRS }
enum class ExperimentPhase { WATCH, PLAY, UNKNOWN }
data class ExperimentCard(
    val slot: Int, val item: String, val name: String, val lore: List<String> = emptyList(),
    val highlighted: Boolean = false, val number: Int? = null,
    val hidden: Boolean = false, val book: Boolean = false, val powerup: Boolean = false,
    val glint: Boolean = false,
) {
    val ultraRareBook get() = book && Regex("ultra[ -]?rare book", RegexOption.IGNORE_CASE).containsMatchIn(name + " " + lore.joinToString(" "))
    val priorityReward get() = name.replace(Regex("§."), "").trim().lowercase() in priorityRewards
    val rewardPriority get() = when { ultraRareBook -> 3; priorityReward -> 2; book -> 1; else -> 0 }
    val signature get() = "$item|$name|${lore.joinToString("\u001f")}|$highlighted|$number"
    val rewardKey get() = "$item|$name|${lore.filterNot { instruction.containsMatchIn(it) }.joinToString("\u001f")}"
    companion object {
        private val priorityRewards = setOf(
            "a beginner's guide to pesthunting", "severed pincer", "ensnared snail",
            "golden bounty", "severed hand", "gold bottle cap", "chain of the end times",
            "fateful stinger", "end stone idol", "troubled bubble", "vibrant coral",
            "octopus tendril", "nadeshiko dye",
        )
        private val instruction = Regex("^(?:click |match |you (?:found|have)|remaining |superpairs powerup)", RegexOption.IGNORE_CASE)
    }
}
data class ExperimentFrame(
    val session: String, val game: ExperimentGame, val phase: ExperimentPhase, val round: Int,
    val revision: Long, val cards: List<ExperimentCard>, val clicksLeft: Int? = null,
    val instantFind: Boolean = false,
    val earnedBonus: Int? = null, val bonusGoalRound: Int? = null,
)
sealed interface ExperimentAction {
    data class Click(val slot: Int) : ExperimentAction
    data class Finished(val closeMenu: Boolean, val round: Int) : ExperimentAction
}

/** Addon sequence routing and acknowledged Superpairs state. The caller validates every live click. */
class ExperimentSolver(private val explorationOrder: (List<Int>) -> List<Int> = { it.shuffled() }) {
    private data class Pending(val slot: Int, val signature: String, val revision: Long, val at: Long, val instant: Boolean)
    private val addon = ExperimentAddonSolver()
    private var session: String? = null
    private var phase = ExperimentPhase.UNKNOWN
    private var phaseSince = 0L
    private var pending: Pending? = null
    private var lastClickAt = Long.MIN_VALUE / 2
    private var firstFlip: Int? = null
    private var mismatch: Pair<Int, Int>? = null
    private val memory = linkedMapOf<Int, ExperimentCard>()
    private val collected = mutableSetOf<Int>()
    private val explorationRanks = mutableMapOf<Int, Int>()
    private var instantReady = false
    var halted = false; private set
    var status = "Waiting for an experiment"; private set
    var confirmedClicks = 0; private set
    var completedRound = 0; private set
    val knownCards get() = memory.size
    val learnedSequence get() = addon.learnedSequence
    val awaitingConfirmation get() = pending != null

    fun reset() {
        addon.reset()
        session = null; phase = ExperimentPhase.UNKNOWN; phaseSince = 0
        pending = null; lastClickAt = Long.MIN_VALUE / 2
        firstFlip = null; mismatch = null; memory.clear(); collected.clear(); instantReady = false
        explorationRanks.clear()
        halted = false; confirmedClicks = 0; completedRound = 0; status = "Waiting for an experiment"
    }

    fun sent(action: ExperimentAction.Click, frame: ExperimentFrame, now: Long) {
        check(pending == null && !halted)
        if (frame.game != ExperimentGame.SUPERPAIRS) { addon.sent(action, now); return }
        val card = frame.cards.first { it.slot == action.slot }
        pending = Pending(card.slot, card.signature, frame.revision, now, instantReady || frame.instantFind)
        instantReady = false
        lastClickAt = now
        status = "Waiting for server confirmation"
    }

    fun next(frame: ExperimentFrame, now: Long, delayMs: Long = 400, preferBooks: Boolean = true): ExperimentAction? {
        if (session != frame.session) { reset(); session = frame.session; phaseSince = now }
        if (halted) return null
        if (frame.game != ExperimentGame.SUPERPAIRS) {
            val action = addon.next(frame, now, delayMs)
            status = addon.status; completedRound = addon.completedRound
            return action
        }
        val cards = frame.cards.associateBy { it.slot }
        if (frame.phase != phase) { phase = frame.phase; phaseSince = now }
        if (frame.game == ExperimentGame.SUPERPAIRS) rememberPairs(cards)

        pending?.let { p ->
            val changed = frame.revision > p.revision && cards[p.slot]?.let {
                it.signature != p.signature && (frame.game != ExperimentGame.SUPERPAIRS || !it.hidden)
            } == true
            if (changed) {
                pending = null; confirmedClicks++
                if (frame.game == ExperimentGame.SUPERPAIRS) acceptPairFlip(p, cards)
            } else {
                if (now - p.at >= 5000) { halted = true; status = "No server confirmation; toggle Auto Experiments off and on to resume" }
                return null
            }
        }

        if (frame.game == ExperimentGame.SUPERPAIRS) {
            if (frame.clicksLeft == null) { status = "Waiting for remaining-click counter"; return null }
            if (frame.clicksLeft <= 0) { status = "Superpairs complete"; return ExperimentAction.Finished(false, 0) }
            if (explorationRanks.isEmpty()) explorationOrder(frame.cards.map { it.slot }).forEachIndexed { index, slot -> explorationRanks[slot] = index }
            mismatch?.let { (a, b) ->
                if (cards[a]?.hidden == true && cards[b]?.hidden == true) mismatch = null
                else { status = "Waiting for mismatched cards to turn over"; return null }
            }
            if (now - lastClickAt < delayMs || now - phaseSince < 350) return null
            val next = choosePair(cards, frame.clicksLeft, instantReady || frame.instantFind, preferBooks)
            if (next == null && confirmedClicks > 0 && cards.isNotEmpty() && (cards.values.none { it.hidden } ||
                    (preferBooks && memory.values.any { it.rewardPriority > 0 } && cards.values.filter { it.hidden && it.slot !in collected }.all { it.slot in memory }))) {
                status = "Priority reward scouting complete; opening rewards"
                return ExperimentAction.Finished(false, 0)
            }
            status = if (next == null) "No further known match" else "Superpairs: ${if (memory[next]?.book == true) "book priority" else "finding matches"}"
            return next?.let { ExperimentAction.Click(it) }
        }

        return null
    }

    private fun rememberPairs(cards: Map<Int, ExperimentCard>) {
        // Powerups take effect as soon as they are revealed. They never occupy a pair slot.
        cards.values.filter { it.powerup && !it.hidden }.forEach { collected += it.slot }
        cards.values.filter { !it.hidden && !it.powerup }.forEach { memory[it.slot] = it }
        // Previously collected pairs stay face-up. Do not confuse a newly flipped duplicate
        // with those pairs, and do not infer pairs from odd-sized groups.
        cards.values.filter { !it.hidden && !it.powerup && it.slot !in collected && it.slot != firstFlip && it.slot != pending?.slot }
            .groupBy { it.rewardKey }.values.filter { it.size % 2 == 0 }.forEach { group -> collected += group.map { it.slot } }
        firstFlip?.let { if (cards[it]?.hidden == true || it in collected) firstFlip = null }
    }

    private fun acceptPairFlip(p: Pending, cards: Map<Int, ExperimentCard>) {
        val card = cards[p.slot] ?: return
        if (card.powerup) {
            collected += card.slot
            if ((card.name + card.lore.joinToString()).contains("instant find", true)) instantReady = true
            return
        }
        memory[card.slot] = card
        if (p.instant) { collected += card.slot; return }
        val first = firstFlip
        if (first == null) firstFlip = card.slot
        else {
            if (memory[first]?.rewardKey == card.rewardKey) collected += listOf(first, card.slot)
            else mismatch = first to card.slot
            firstFlip = null
        }
    }

    private fun choosePair(cards: Map<Int, ExperimentCard>, clicks: Int, instant: Boolean, books: Boolean): Int? {
        val hidden = cards.values.filter { it.hidden && it.slot !in collected }.map { it.slot }.sortedBy { explorationRanks[it] ?: Int.MAX_VALUE }
        val unknown = hidden.filter { it !in memory }
        // Instant Find uses the same reward order as normal matching.
        if (instant) hidden.filter { maxOf(memory[it]?.rewardPriority ?: 0, cards[it]?.rewardPriority ?: 0) > 0 }
            .maxByOrNull { maxOf(memory[it]?.rewardPriority ?: 0, cards[it]?.rewardPriority ?: 0) }?.let { return it }
        // Complete a revealed book's known mate before exploring or starting another pair.
        firstFlip?.let { first ->
            val card = memory[first]
            if (card != null && card.rewardPriority > 0) hidden.firstOrNull { memory[it]?.rewardKey == card.rewardKey }?.let { return it }
        }
        // Claim the highest-priority known pair before exploring another slot.
        if (firstFlip == null && clicks >= 2) hidden.mapNotNull { memory[it] }.filter { it.rewardPriority > 0 }
            .groupBy { it.rewardKey }.values.filter { it.size >= 2 }
            .maxByOrNull { it.maxOf { card -> card.rewardPriority } }
            ?.let { return it.first().slot }
        // Scout unseen locations before spending flips on remembered non-book pairs.
        // The explicit ultra-rare marker can identify an especially valuable unseen tile.
        if (unknown.isNotEmpty()) return (if (books) unknown.firstOrNull { cards[it]?.ultraRareBook == true } else null) ?: unknown.first()
        val booksFound = memory.values.any { it.rewardPriority > 0 } || cards.values.any { it.rewardPriority > 0 }
        val bookOnly = books && booksFound
        val eligible = if (bookOnly) hidden.filter { (memory[it]?.rewardPriority ?: 0) > 0 } else hidden
        if (instant) return (if (books) eligible.maxByOrNull { memory[it]?.rewardPriority ?: 0 } else null) ?: eligible.firstOrNull()
        firstFlip?.let { first ->
            val key = memory[first]?.rewardKey
            if (!bookOnly || (memory[first]?.rewardPriority ?: 0) > 0) eligible.firstOrNull { memory[it]?.rewardKey == key }?.let { return it }
            return eligible.maxByOrNull { memory[it]?.rewardPriority ?: 0 }
        }
        val pairs = eligible.mapNotNull { memory[it] }.groupBy { it.rewardKey }.values.filter { it.size >= 2 }
        if (books) pairs.filter { it.first().rewardPriority > 0 }.maxByOrNull { it.maxOf { card -> card.rewardPriority } }?.let { return it.first().slot }
        if (clicks >= 2) pairs.firstOrNull()?.let { return it.first().slot }
        return eligible.firstOrNull()
    }
}
