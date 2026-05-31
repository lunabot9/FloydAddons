package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydHudTest {
    @Test
    fun `custom scoreboard keeps drawing after vanilla sidebar signal`() {
        FloydHud.resetVanillaScoreboardWouldRender()

        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )

        FloydHud.markVanillaScoreboardWouldRender()
        assertTrue(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
        assertTrue(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
    }

    @Test
    fun `scoreboard gate still blocks disabled custom scoreboard or missing objective`() {
        FloydHud.resetVanillaScoreboardWouldRender()
        FloydHud.markVanillaScoreboardWouldRender()

        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = false,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )

        FloydHud.markVanillaScoreboardWouldRender()
        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = false,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
    }
}
