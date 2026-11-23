package gg.floyd.features.impl.pvp.experiments

import kotlin.test.*

class ExperimentTableTargetPolicyTest {
    @Test fun continuationAcceptsTheAimedOuterFurnitureHitbox() {
        // Captured geometry: real crosshair hitbox versus the Experimentation Table label.
        assertTrue(ExperimentTableTargetPolicy.contains(.45509, -.09173, -1.93029, true))
    }
    @Test fun initialOpeningRemainsConservative() {
        assertFalse(ExperimentTableTargetPolicy.contains(.45509, -.09173, -1.93029, false))
        assertTrue(ExperimentTableTargetPolicy.contains(.4, -.5, .4, false))
    }
    @Test fun nearbyHologramsAndDistantFurnitureRemainExcluded() {
        assertFalse(ExperimentTableTargetPolicy.contains(.4, .3, .4, true))
        assertFalse(ExperimentTableTargetPolicy.contains(3.0, -.5, .4, true))
        assertFalse(ExperimentTableTargetPolicy.contains(.4, -3.2, .4, true))
    }
}
