package gg.floyd.features.impl.pvp.experiments

import kotlin.test.*

class ExperimentSolverTest {
    private fun tile(slot: Int, glow: Boolean = false, name: String = "Color") = ExperimentCard(slot, "glass", name, highlighted = glow)
    private fun frame(round: Int = 2, phase: ExperimentPhase = ExperimentPhase.WATCH, revision: Long = 1,
        cards: List<ExperimentCard> = listOf(tile(11), tile(12)), bonus: Int? = null, goal: Int? = null) =
        ExperimentFrame("window-1", ExperimentGame.CHRONOMATRON, phase, round, revision, cards, earnedBonus = bonus, bonusGoalRound = goal)

    @Test fun chronomatronKeepsHistoryAndAddsOneGlowingNotePerClockTurn() {
        val solver = ExperimentSolver { it }
        var now = 0L
        for (round in 1..12) {
            val blank = listOf(tile(11), tile(20), tile(13), tile(22))
            solver.next(frame(round, revision = round * 10L, cards = blank, goal = 12), now)
            val glowSlot = if (round % 3 == 0) 13 else 11
            val play = frame(round, ExperimentPhase.PLAY, round * 10L + 1,
                blank.map { it.copy(glint = it.slot == glowSlot || it.slot == glowSlot + 9) }, goal = 12)
            solver.next(play, now + 100)
            assertEquals(round, solver.learnedSequence.size)
            for (expected in solver.learnedSequence) {
                now += 500
                val click = solver.next(play, now) as ExperimentAction.Click
                assertEquals(expected, click.slot)
                solver.sent(click, play, now)
            }
            assertNull(solver.next(play, now + 100)) // No target-slot changes are required between addon inputs.
            assertEquals(round, solver.learnedSequence.size) // Persistent glint cannot append twice.
            now += 1000
        }
        assertEquals(ExperimentAction.Finished(true, 12), solver.next(frame(13, goal = 12), now))
    }
    @Test fun roundCompletionSurvivesSplitWatchAndRoundCounterUpdates() {
        val solver = ExperimentSolver { it }
        solver.next(frame(1, goal = 1), 0)
        val play = frame(1, ExperimentPhase.PLAY, cards = listOf(tile(11).copy(glint = true)), goal = 1)
        solver.next(play, 100)
        val click = solver.next(play, 500) as ExperimentAction.Click
        solver.sent(click, play, 500)
        assertNull(solver.next(frame(1, goal = 1), 600)) // Phase update arrived before the round counter.
        assertEquals(ExperimentAction.Finished(true, 1), solver.next(frame(2, goal = 1), 700))
    }
    @Test fun missingChronomatronHistoryDoesNotGuessClicksOrClose() {
        val solver = ExperimentSolver { it }
        solver.next(frame(9, goal = 9), 0)
        assertNull(solver.next(frame(9, ExperimentPhase.PLAY, cards = listOf(tile(11).copy(glint = true)), goal = 9), 500))
        assertTrue(solver.learnedSequence.isEmpty())
        assertNull(solver.next(frame(10, goal = 9), 1000))
    }
    @Test fun ultrasequencerUsesStackCountAndRetainsTheBoardDuringInput() {
        assertEquals(2, ExperimentRoundReader.number("99", 2))
        assertNull(ExperimentRoundReader.number("Timer: 3s", 3))
        val reader = ExperimentRoundReader()
        val cards = listOf(tile(19).copy(number = 2), tile(28).copy(number = 1), tile(11).copy(number = 3))
        assertEquals(3, reader.read(ExperimentGame.ULTRASEQUENCER, ExperimentPhase.WATCH, null, cards))
        assertEquals(3, reader.read(ExperimentGame.ULTRASEQUENCER, ExperimentPhase.PLAY, null, emptyList()))
        val solver = ExperimentSolver { it }
        val watch = ExperimentFrame("ultra", ExperimentGame.ULTRASEQUENCER, ExperimentPhase.WATCH, 3, 1, cards)
        solver.next(watch, 0)
        val play = watch.copy(phase = ExperimentPhase.PLAY, revision = 2, cards = cards.map { it.copy(number = null) })
        solver.next(play, 100)
        for ((i, slot) in listOf(28, 19, 11).withIndex()) {
            val time = 500L + i * 500
            val click = solver.next(play, time) as ExperimentAction.Click
            assertEquals(slot, click.slot); solver.sent(click, play, time)
        }
        assertNull(solver.next(play, 2500))
        assertFalse(solver.awaitingConfirmation)
    }
    @Test fun advertisedRewardIsNotEarnedProgress() {
        assertEquals(ExperimentBonus.Progress(2, 12), ExperimentBonus.read(listOf("Superpairs clicks: +2", "Chain of 12: +3 Superpairs clicks")))
        assertNull(ExperimentBonus.read(listOf("Complete round 12 for +3 Superpairs clicks")).earned)
        assertEquals(ExperimentBonus.Progress(null, 9), ExperimentBonus.read(listOf("Chain of 9: +3 Clicks")))
    }
    @Test fun newWindowDoesNotReuseChronomatronHistory() {
        val solver = ExperimentSolver { it }
        solver.next(frame(1), 0)
        solver.next(frame(1, ExperimentPhase.PLAY, cards = listOf(tile(11).copy(glint = true))), 100)
        assertEquals(listOf(11), solver.learnedSequence)
        solver.next(frame(1).copy(session = "window-2"), 200)
        assertTrue(solver.learnedSequence.isEmpty())
    }
    @Test fun superpairsStillRequiresARevealAndStopsOnTimeoutWithoutRetrying() {
        val solver = ExperimentSolver { it }
        val pair = ExperimentFrame("pairs", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(hidden(10), hidden(11)), clicksLeft = 4)
        solver.next(pair, 0)
        val click = solver.next(pair, 500) as ExperimentAction.Click
        solver.sent(click, pair, 500)
        assertNull(solver.next(pair.copy(revision = 2), 1200))
        assertTrue(solver.awaitingConfirmation)
        assertNull(solver.next(pair, 5500))
        assertTrue(solver.halted)
    }
    private fun hidden(slot: Int) = ExperimentCard(slot, "pane", "?", hidden = true)
    private fun book(slot: Int, enchant: String) = ExperimentCard(slot, "book", "Enchanted Book", listOf(enchant), book = true)
    @Test fun revealedBookClaimsKnownMateBeforeContinuingShuffledExploration() {
        val s = ExperimentSolver { it.reversed() }
        var f = ExperimentFrame("shuffled", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            (10..14).map(::hidden), clicksLeft = 12)
        s.next(f, 0)
        fun reveal(now: Long, slot: Int, card: ExperimentCard) {
            val action = s.next(f, now) as ExperimentAction.Click; assertEquals(slot, action.slot); s.sent(action, f, now)
            f = f.copy(revision = f.revision + 1, cards = f.cards.map { if (it.slot == slot) card else it })
            s.next(f, now + 1)
        }
        reveal(400, 14, book(14, "Sharpness VII"))
        reveal(800, 13, book(13, "Growth VI"))
        f = f.copy(revision = f.revision + 1, cards = (10..14).map(::hidden))
        reveal(1300, 12, book(12, "Sharpness VII"))
        reveal(1800, 14, book(14, "Sharpness VII"))
        assertEquals(ExperimentAction.Click(11), s.next(f, 2300)) // Resume scouting once the book is collected.
    }
    @Test fun ultraRarePairsBeatOtherBooksAndNonBooksAreLeftOnceBooksWereFound() {
        val s = ExperimentSolver { it }
        fun rare(slot: Int) = book(slot, "Sharpness VII").copy(lore = listOf("XX ULTRA-RARE BOOK! XX", "Sharpness VII"))
        fun ordinary(slot: Int) = ExperimentCard(slot, "bottle", "100k XP")
        var f = ExperimentFrame("rare", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(book(10, "Growth VI"), hidden(11), rare(12), hidden(13), ordinary(14), hidden(15)), clicksLeft = 12)
        s.next(f, 0)
        f = f.copy(revision = 2, cards = listOf(hidden(10), book(11, "Growth VI"), hidden(12), rare(13), hidden(14), ordinary(15)))
        s.next(f, 100)
        f = f.copy(revision = 3, cards = (10..15).map(::hidden))
        var action = s.next(f, 500) as ExperimentAction.Click
        assertEquals(12, action.slot); s.sent(action, f, 500)
        f = f.copy(revision = 4, cards = f.cards.map { if (it.slot == 12) rare(12) else it })
        s.next(f, 501)
        action = s.next(f, 1000) as ExperimentAction.Click
        assertEquals(13, action.slot); s.sent(action, f, 1000)
        f = f.copy(revision = 5, cards = f.cards.map { if (it.slot == 13) rare(13) else it })
        s.next(f, 1001)
        action = s.next(f, 1500) as ExperimentAction.Click
        assertEquals(10, action.slot); s.sent(action, f, 1500)
        f = f.copy(revision = 6, cards = f.cards.map { if (it.slot == 10) book(10, "Growth VI") else it })
        s.next(f, 1501)
        action = s.next(f, 2000) as ExperimentAction.Click
        assertEquals(11, action.slot); s.sent(action, f, 2000)
        f = f.copy(revision = 7, cards = f.cards.map { if (it.slot == 11) book(11, "Growth VI") else it })
        assertEquals(ExperimentAction.Finished(false, 0), s.next(f, 2500)) // Do not deliberately collect the known XP pair.
    }
    @Test fun instantFindTargetsKnownUltraRareBookBeforeUnseenCards() {
        val s = ExperimentSolver { it }
        val rare = book(11, "Sharpness VII").copy(lore = listOf("ULTRA-RARE BOOK!", "Sharpness VII"))
        val f = ExperimentFrame("instant-book", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(book(10, "Growth VI"), rare, hidden(12), hidden(13)), clicksLeft = 8)
        s.next(f, 0)
        assertEquals(ExperimentAction.Click(11), s.next(f.copy(revision = 2, cards = (10..13).map(::hidden), instantFind = true), 500))
    }
    @Test fun knownBookPairIsStartedBeforeUnseenSlots() {
        val s = ExperimentSolver { it }
        var f = ExperimentFrame("known-book", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(book(10, "Growth VI"), hidden(11), hidden(12), hidden(13)), clicksLeft = 8)
        s.next(f, 0)
        f = f.copy(revision = 2, cards = listOf(hidden(10), book(11, "Growth VI"), hidden(12), hidden(13)))
        s.next(f, 100)
        f = f.copy(revision = 3, cards = (10..13).map(::hidden))
        val click = s.next(f, 500) as ExperimentAction.Click
        assertEquals(10, click.slot)
        s.sent(click, f, 500)
        assertNull(s.next(f, 900)) // Do not send the second click before the server reveals the first.
        f = f.copy(revision = 4, cards = listOf(book(10, "Growth VI"), hidden(11), hidden(12), hidden(13)))
        assertEquals(ExperimentAction.Click(11), s.next(f, 901))
    }
    @Test fun namedRareRewardsAreRecognizedWithoutRngMeterLore() {
        val names = listOf("A Beginner's Guide to Pesthunting", "Severed Pincer", "Ensnared Snail",
            "Golden Bounty", "Severed Hand", "Gold Bottle Cap", "Chain of the End Times",
            "Fateful Stinger", "End Stone Idol", "Troubled Bubble", "Vibrant Coral", "Octopus Tendril", "Nadeshiko Dye")
        for (name in names) {
            val card = ExperimentCard(10, "player_head", name)
            assertTrue(card.priorityReward, name)
            assertEquals(2, card.rewardPriority)
            assertTrue(card.copy(name = "§6$name").priorityReward)
        }
        assertFalse(ExperimentCard(10, "player_head", "Golden Bounty Fragment").priorityReward)
    }
    @Test fun selectedRarePairsBeatOrdinaryBooksAndXp() {
        val s = ExperimentSolver { it }
        fun rare(slot: Int) = ExperimentCard(slot, "player_head", "Nadeshiko Dye")
        fun xp(slot: Int) = ExperimentCard(slot, "bottle", "100k XP")
        var f = ExperimentFrame("rare-loot", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(book(10, "Growth VI"), hidden(11), rare(12), hidden(13), xp(14), hidden(15)), clicksLeft = 12)
        s.next(f, 0)
        f = f.copy(revision = 2, cards = listOf(hidden(10), book(11, "Growth VI"), hidden(12), rare(13), hidden(14), xp(15)))
        s.next(f, 100)
        f = f.copy(revision = 3, cards = (10..15).map(::hidden))
        fun collect(now: Long, slot: Int, card: ExperimentCard) {
            val click = s.next(f, now) as ExperimentAction.Click
            assertEquals(slot, click.slot); s.sent(click, f, now)
            f = f.copy(revision = f.revision + 1, cards = f.cards.map { if (it.slot == slot) card else it })
            s.next(f, now + 1)
        }
        collect(500, 12, rare(12)); collect(1000, 13, rare(13))
        collect(1500, 10, book(10, "Growth VI")); collect(2000, 11, book(11, "Growth VI"))
        assertEquals(ExperimentAction.Finished(false, 0), s.next(f, 2500))
    }
    @Test fun instantFindUsesSelectedRareRewardWhenNoBookIsKnown() {
        val s = ExperimentSolver { it }
        val f = ExperimentFrame("instant-rare", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(hidden(10), ExperimentCard(11, "player_head", "End Stone Idol"), hidden(12)), clicksLeft = 8)
        s.next(f, 0)
        assertEquals(ExperimentAction.Click(11), s.next(f.copy(revision = 2, cards = (10..12).map(::hidden), instantFind = true), 500))
    }
    @Test fun normalPairsAndInstantFindShareUltraRareThenRareThenBookOrder() {
        fun reward(slot: Int) = when (slot) {
            10, 11 -> book(slot, "Growth VI")
            12, 13 -> ExperimentCard(slot, "player_head", "Golden Bounty")
            else -> book(slot, "Sharpness VII").copy(lore = listOf("ULTRA-RARE BOOK!", "Sharpness VII"))
        }
        fun learn(s: ExperimentSolver): ExperimentFrame {
            var f = ExperimentFrame("priority-order", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
                (10..15).map { if (it % 2 == 0) reward(it) else hidden(it) } + hidden(16), clicksLeft = 20)
            s.next(f, 0)
            f = f.copy(revision = 2, cards = (10..15).map { if (it % 2 == 1) reward(it) else hidden(it) } + hidden(16))
            s.next(f, 100)
            return f.copy(revision = 3, cards = (10..16).map(::hidden))
        }
        val s = ExperimentSolver { it }; var f = learn(s)
        for ((index, slot) in listOf(14, 15, 12, 13, 10, 11).withIndex()) {
            val now = 500L + index * 500L
            val click = s.next(f, now) as ExperimentAction.Click
            assertEquals(slot, click.slot); s.sent(click, f, now)
            f = f.copy(revision = f.revision + 1, cards = f.cards.map { if (it.slot == slot) reward(slot) else it })
            s.next(f, now + 1)
        }
        for ((remaining, expected) in listOf((10..16).toList() to 14, (10..13).toList() + 16 to 12, listOf(10, 11, 16) to 10)) {
            val instantSolver = ExperimentSolver { it }; val learned = learn(instantSolver)
            assertEquals(ExperimentAction.Click(expected), instantSolver.next(learned.copy(cards = remaining.map(::hidden), instantFind = true), 500))
        }
    }
    @Test fun spendsFinalClicksOnKnownHiddenCardsInsteadOfStalling() {
        val s = ExperimentSolver { it }
        var f = ExperimentFrame("fallback", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(hidden(10), hidden(11)), clicksLeft = 4)
        s.next(f, 0)
        for ((time, slot) in listOf(400L to 10, 800L to 11)) {
            val action = s.next(f, time) as ExperimentAction.Click; assertEquals(slot, action.slot); s.sent(action, f, time)
            f = f.copy(revision = f.revision + 1, cards = f.cards.map { if (it.slot == slot) ExperimentCard(slot, "bottle", "XP $slot") else it })
            s.next(f, time + 1)
        }
        f = f.copy(revision = f.revision + 1, cards = listOf(hidden(10), hidden(11)), clicksLeft = 1)
        val last = s.next(f, 1300) as ExperimentAction.Click; assertEquals(10, last.slot)
        s.sent(last, f, 1300)
        f = f.copy(revision = f.revision + 1, cards = listOf(ExperimentCard(10, "bottle", "XP 10"), hidden(11)), clicksLeft = 0)
        assertEquals(ExperimentAction.Finished(false, 0), s.next(f, 1400))
    }
    @Test fun instantFeatherContinuesWithoutReclickingAndPreservesHalfFinishedPair() {
        val s = ExperimentSolver { it }
        var f = ExperimentFrame("powerup", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            (10..14).map(::hidden), clicksLeft = 5)
        s.next(f, 0)
        fun reveal(now: Long, slot: Int, card: ExperimentCard) {
            val action = s.next(f, now) as ExperimentAction.Click
            assertEquals(slot, action.slot); s.sent(action, f, now)
            f = f.copy(revision = f.revision + 1, cards = f.cards.map { if (it.slot == slot) card else it })
            s.next(f, now + 1)
        }
        reveal(400, 10, book(10, "Sharpness VII"))
        reveal(800, 11, ExperimentCard(11, "feather", "Gained +3 Clicks", listOf("Instant powerup!"), powerup = true))
        assertEquals(ExperimentAction.Click(12), s.next(f, 1200)) // Never click feather 11 twice.
        reveal(1300, 12, book(12, "Sharpness VII"))
        assertEquals(ExperimentAction.Click(13), s.next(f, 1800)) // Original first card was matched.
    }
    @Test fun instantFindAppliesToTheNextCardWithoutASecondPowerupClick() {
        val s = ExperimentSolver { it }
        var f = ExperimentFrame("instant", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(hidden(20), hidden(21), hidden(22), hidden(23)), clicksLeft = 4)
        s.next(f, 0)
        val reveal = s.next(f, 400) as ExperimentAction.Click; assertEquals(20, reveal.slot); s.sent(reveal, f, 400)
        f = f.copy(revision = 2, cards = listOf(ExperimentCard(20, "feather", "Instant Find", powerup = true), hidden(21), hidden(22), hidden(23)))
        s.next(f, 401)
        val reward = s.next(f, 800) as ExperimentAction.Click; assertEquals(21, reward.slot); s.sent(reward, f, 800)
        f = f.copy(revision = 3, cards = listOf(f.cards[0], book(21, "Sharpness VII"), hidden(22), hidden(23)))
        s.next(f, 801)
        val next = s.next(f, 1200) as ExperimentAction.Click; assertEquals(22, next.slot); s.sent(next, f, 1200)
        f = f.copy(revision = 4, cards = listOf(f.cards[0], f.cards[1], book(22, "Growth VI"), hidden(23)))
        s.next(f, 1201)
        assertEquals(ExperimentAction.Click(23), s.next(f, 1600)) // Instant reward was not left as the first half of a mismatched pair.
    }
    @Test fun finalBoardRevealDoesNotTriggerPowerupClaims() {
        val s = ExperimentSolver { it }
        val f = ExperimentFrame("over", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            listOf(ExperimentCard(20, "feather", "Gained +3 Clicks", powerup = true), book(21, "Growth VI")), clicksLeft = 0)
        assertEquals(ExperimentAction.Finished(false, 0), s.next(f, 0))
    }
    @Test fun superpairsWaitsForMismatchToHideAndPrioritizesKnownBooks() {
        val s = ExperimentSolver { it }
        var f = ExperimentFrame("pairs", ExperimentGame.SUPERPAIRS, ExperimentPhase.PLAY, 0, 1,
            (10..15).map(::hidden), clicksLeft = 12)
        s.next(f, 0)
        fun click(now: Long, expected: Int, reveal: ExperimentCard) {
            val action = s.next(f, now) as ExperimentAction.Click; assertEquals(expected, action.slot)
            s.sent(action, f, now)
            f = f.copy(revision = f.revision + 1, cards = f.cards.map { if (it.slot == expected) reveal else it }, clicksLeft = f.clicksLeft!! - 1)
            s.next(f, now + 1)
        }
        click(400, 10, book(10, "Sharpness VII"))
        click(800, 11, book(11, "Growth VI"))
        assertNull(s.next(f, 1300)) // Two different enchantments with the same book icon are not a match.
        f = f.copy(revision = f.revision + 1, cards = (10..15).map(::hidden))
        click(1400, 12, book(12, "Sharpness VII"))
        click(1800, 10, book(10, "Sharpness VII")) // Complete the book before exploring another slot.
        assertEquals(ExperimentAction.Click(13), s.next(f, 2300))
    }
}
