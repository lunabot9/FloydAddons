package gg.floyd.features.impl.pvp.experiments

import kotlin.test.*

class ExperimentTableStatusTest {
    private val title = ExperimentTableStatus.Line("Experimentation Table", yellow = true)
    @Test fun yellowTableIsAvailableButCooldownOverridesIt() {
        assertEquals(ExperimentTableStatus.Availability.READY, ExperimentTableStatus.read(listOf(title)))
        for (line in listOf(
            ExperimentTableStatus.Line("12h 05m", red = true),
            ExperimentTableStatus.Line("Next experiment in 12:05:02"),
            ExperimentTableStatus.Line("No available sessions"),
            ExperimentTableStatus.Line("0 experiments available"),
            ExperimentTableStatus.Line("Unavailable", red = true))) {
            assertEquals(ExperimentTableStatus.Availability.COOLDOWN, ExperimentTableStatus.read(listOf(title, line)))
        }
    }
    @Test fun unrelatedYellowLabelsNeverOpenAnything() {
        assertEquals(ExperimentTableStatus.Availability.UNKNOWN, ExperimentTableStatus.read(listOf(ExperimentTableStatus.Line("Click to open Chest", yellow = true))))
        assertEquals(ExperimentTableStatus.Availability.UNKNOWN, ExperimentTableStatus.read(listOf(ExperimentTableStatus.Line("Experimentation Table"))))
    }
}
