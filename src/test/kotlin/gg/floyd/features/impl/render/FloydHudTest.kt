package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydHudTest {
    @Test
    fun `custom scoreboard follows the module toggle`() {
        assertTrue(FloydCustomScoreboard.shouldUseCustomScoreboard(moduleEnabled = true))
        assertFalse(FloydCustomScoreboard.shouldUseCustomScoreboard(moduleEnabled = false))
    }

    @Test
    fun `custom scoreboard keeps drawing after vanilla sidebar signal`() {
        FloydCustomScoreboard.resetVanillaScoreboardWouldRender()

        assertFalse(
            FloydCustomScoreboard.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )

        FloydCustomScoreboard.markVanillaScoreboardWouldRender()
        assertTrue(
            FloydCustomScoreboard.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
        assertTrue(
            FloydCustomScoreboard.shouldDrawScoreboardHud(
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
        FloydCustomScoreboard.resetVanillaScoreboardWouldRender()
        FloydCustomScoreboard.markVanillaScoreboardWouldRender()

        assertFalse(
            FloydCustomScoreboard.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = false,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
        assertFalse(
            FloydCustomScoreboard.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )

        FloydCustomScoreboard.markVanillaScoreboardWouldRender()
        assertFalse(
            FloydCustomScoreboard.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = false,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
    }
}
