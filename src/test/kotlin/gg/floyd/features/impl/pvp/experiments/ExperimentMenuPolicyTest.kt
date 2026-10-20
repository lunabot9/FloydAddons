package gg.floyd.features.impl.pvp.experiments

import kotlin.test.*

class ExperimentMenuPolicyTest {
    @Test fun ironmanConsumesOwnedTitanicWithoutABazaarPrice() {
        val owned = ExperimentCard(14, "minecraft:experience_bottle", "Titanic Experience Bottle", listOf(
            "Cost", "Titanic Experience Bottle \u2714", "Click to consume!"))
        assertEquals(ExperimentMenuPolicy.BottleAction.OWNED, ExperimentMenuPolicy.bottleAction(owned, 1000000.0))
        assertEquals(ExperimentMenuPolicy.BottleAction.UNAVAILABLE, ExperimentMenuPolicy.bottleAction(owned.copy(lore = listOf(
            "Cost", "Titanic Experience Bottle \u2716", "Could not find this item in your", "inventory!")), 1000000.0))
        val paid = owned.copy(lore = listOf("Bazaar Price", "597,989.3 Coins", "Click to buy now!"))
        assertEquals(ExperimentMenuPolicy.BottleAction.BAZAAR, ExperimentMenuPolicy.bottleAction(paid, 1000000.0))
        assertEquals(ExperimentMenuPolicy.BottleAction.OVER_LIMIT, ExperimentMenuPolicy.bottleAction(paid, 500000.0))
    }
    @Test fun focusReturnClickIsIgnoredButLaterManualInputStillWorks() {
        val guard = ExperimentFocusGuard()
        guard.reset(true, 0)
        assertFalse(guard.ignoreClick(true, 1000))
        guard.observe(false, 1100)
        guard.observe(true, 2500)
        assertTrue(guard.ignoreClick(true, 2510))
        assertFalse(guard.ignoreClick(true, 3000))
    }
    @Test fun readsEachRenewalPriceAndDailyLimitFromTheLiveLore() {
        val first = ExperimentCard(31, "minecraft:blaze_powder", "Renew Experiments", listOf(
            "Purchase 1 additional charge for", "the Experimentation Table.", "You've renewed 0/3 charges today!",
            "Costs", "50 XP Levels", "150 Bits", "Click to purchase!"))
        assertEquals(3, ExperimentMenuPolicy.renewalsRemaining(first))
        assertEquals(50, ExperimentMenuPolicy.levels(first)); assertEquals(150, ExperimentMenuPolicy.bits(first))
        val next = first.copy(lore = listOf("You've renewed 1/3 charges today!", "100 XP Levels", "300 Bits", "Click to purchase!"))
        assertEquals(2, ExperimentMenuPolicy.renewalsRemaining(next)); assertEquals(100, ExperimentMenuPolicy.levels(next)); assertEquals(300, ExperimentMenuPolicy.bits(next))
        assertEquals(0, ExperimentMenuPolicy.renewalsRemaining(first.copy(lore = listOf("You've renewed 3/3 charges today!"))))
        assertTrue(ExperimentMenuPolicy.insufficientBits(first.copy(lore = listOf("You don't have enough Bits!"))))
    }
    @Test fun choosesHighestUnlockedEvenWhenXpIsInsufficient() {
        val low = ExperimentCard(21, "dye", "Beginner Experiment", listOf("Starting cost: 25 XP Levels", "Click to play!"))
        val high = ExperimentCard(22, "dye", "High Experiment", listOf("Starting cost: 50 XP Levels", "Not enough experience!"))
        val locked = ExperimentCard(32, "dye", "Metaphysical Experiment", listOf("Starting cost: 350 XP Levels", "Enchanting level too low!"))
        assertEquals(high, ExperimentMenuPolicy.highestUnlocked(listOf(low, high, locked)))
        assertEquals(50, ExperimentMenuPolicy.levels(high))
    }
    @Test fun parsesLiveHypixelChargesAndBottlePrice() {
        assertEquals(3, ExperimentMenuPolicy.charges(ExperimentCard(22, "head", "Superpairs", listOf("Charges: 3/3"))))
        val bottle = ExperimentCard(14, "bottle", "Titanic Experience Bottle", listOf("Your Exp Level: 19", "Level When Applied: 236", "Bazaar Price", "597,989.3 Coins", "Click to buy now!"))
        assertEquals(597989.3, ExperimentMenuPolicy.bottlePrice(bottle))
        assertNull(ExperimentMenuPolicy.levels(bottle))
        assertNull(ExperimentMenuPolicy.bottlePrice(bottle.copy(lore = listOf("Unknown price"))))
    }
}
