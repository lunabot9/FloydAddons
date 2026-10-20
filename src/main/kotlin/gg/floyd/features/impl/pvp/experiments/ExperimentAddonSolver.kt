package gg.floyd.features.impl.pvp.experiments

/**
 * Sequence handling adapted from skies-starred/OdinClient, commit 4df18c5.
 * Copyright (c) 2026, Starred. BSD-3-Clause; see META-INF/licenses/OdinClient-AutoExperiments.txt.
 * The caller retains Floyd's normal container click transport and validates each live menu.
 */
class ExperimentAddonSolver {
    private val order = mutableListOf<Int>()
    private var round = 0
    private var phase = ExperimentPhase.UNKNOWN
    private var cursor = 0
    private var submittedRound = 0
    private var playSince = 0L
    private var lastClick = Long.MIN_VALUE / 2
    var completedRound = 0; private set
    var status = "Waiting for an add-on"; private set
    val learnedSequence get() = order.toList()

    fun reset() {
        order.clear(); round = 0; phase = ExperimentPhase.UNKNOWN; cursor = 0; submittedRound = 0
        completedRound = 0; playSince = 0; lastClick = Long.MIN_VALUE / 2
        status = "Waiting for an add-on"
    }

    fun sent(action: ExperimentAction.Click, now: Long) {
        check(phase == ExperimentPhase.PLAY && order.getOrNull(cursor) == action.slot)
        cursor++; lastClick = now
        if (cursor == order.size) submittedRound = round
    }

    fun next(frame: ExperimentFrame, now: Long, delayMs: Long): ExperimentAction? {
        if (frame.phase == ExperimentPhase.UNKNOWN || frame.round <= 0) {
            status = "Waiting for a recognized game phase"; return null
        }
        val previousPhase = phase
        if (frame.phase == ExperimentPhase.WATCH) {
            if (submittedRound > 0 && frame.round > submittedRound) {
                completedRound = submittedRound; submittedRound = 0
            }
            // Closing still requires the server to advance beyond the completed target.
            val reached = frame.earnedBonus?.let { it >= 3 } ?: (frame.bonusGoalRound?.let { completedRound >= it } == true)
            if (reached && completedRound > 0) {
                status = "Earned +3 Superpairs clicks"
                return ExperimentAction.Finished(true, completedRound)
            }
            if (frame.round != round || previousPhase != ExperimentPhase.WATCH) cursor = 0
            round = frame.round; phase = frame.phase
            if (frame.game == ExperimentGame.ULTRASEQUENCER) {
                val numbered = frame.cards.filter { it.number != null }.sortedBy { it.number }
                if (numbered.map { it.number } == (1..round).toList()) {
                    order.clear(); order += numbered.map { it.slot }
                }
            }
            // Chronomatron keeps earlier notes. Its next note glows at the CLOCK transition.
            status = "Remembering round $round (${order.size}/$round)"
            return null
        }
        if (previousPhase == ExperimentPhase.PLAY && frame.round != round) {
            status = "Waiting for the next server watch phase"; return null
        }
        if (previousPhase != ExperimentPhase.PLAY) { cursor = 0; playSince = now }
        round = frame.round; phase = frame.phase
        if (frame.game == ExperimentGame.CHRONOMATRON && order.size == round - 1) {
            frame.cards.filter { it.slot in 10..43 && it.glint }.minByOrNull { it.slot }?.let { order += it.slot }
        }
        if (order.size != round) { status = "Incomplete pattern; no clicks sent"; return null }
        if (cursor >= order.size) { status = "Round entered; waiting for the next server round"; return null }
        if (now - lastClick < delayMs || now - playSince < 350) return null
        val slot = order[cursor]
        if (frame.cards.none { it.slot == slot }) { status = "Expected tile is missing; no clicks sent"; return null }
        status = "Entering round $round: ${cursor + 1}/${order.size}"
        return ExperimentAction.Click(slot)
    }
}
