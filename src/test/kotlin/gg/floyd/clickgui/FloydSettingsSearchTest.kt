package gg.floyd.clickgui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydSettingsSearchTest {
    @Test fun `module search finds partial and spaced names without fuzzy HUD noise`() {
        assertTrue(FloydSettingsSearch.matches("hurt", "No Hurt Camera", "Enabled"))
        assertFalse(FloydSettingsSearch.matches("hurt", "Font", "Inventory HUD Font"))
        assertTrue(FloydSettingsSearch.matches("free c", "Freecam", "Enabled"))
        assertTrue(FloydSettingsSearch.matches("FREE CAM", "Freecam", "Enabled"))
        assertFalse(FloydSettingsSearch.matches("free c", "Freelook", "Camera", "Enabled"))
    }

    @Test fun `settings and descriptions are searchable across modules`() {
        assertTrue(FloydSettingsSearch.matches("speed", "Freecam", "Movement speed"))
        assertTrue(FloydSettingsSearch.matches("font shadow", "Font", "Disable Text Shadow"))
        assertFalse(FloydSettingsSearch.matches("doesnotexist", "Freecam", "Movement speed"))
    }
}
