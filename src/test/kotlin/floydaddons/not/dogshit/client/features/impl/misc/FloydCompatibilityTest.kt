package floydaddons.not.dogshit.client.features.impl.misc

import floydaddons.not.dogshit.client.clickgui.settings.impl.BooleanSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class FloydCompatibilityTest {
    @Test
    fun `update checker is enabled by default like Floyd startup`() {
        assertTrue(FloydCompatibility.shouldCheckUpdates())
    }

    @Test
    fun `update checker semver comparison matches Floyd release tags`() {
        assertEquals(listOf(1, 2, 3), FloydUpdateChecker.parseSemver("1.2.3").toList())
        assertEquals(listOf(1, 2, 0), FloydUpdateChecker.parseSemver("1.2").toList())
        assertEquals(listOf(1, 0, 3), FloydUpdateChecker.parseSemver("1.bad.3").toList())
        assertEquals(listOf(1, 2, 3), FloydUpdateChecker.parseSemver("1.2.3.4").toList())

        assertTrue(FloydUpdateChecker.compareSemver("1.2.4", "1.2.3") > 0)
        assertTrue(FloydUpdateChecker.compareSemver("1.3.0", "1.2.99") > 0)
        assertTrue(FloydUpdateChecker.compareSemver("2.0.0", "1.99.99") > 0)
        assertEquals(0, FloydUpdateChecker.compareSemver("1.2", "1.2.0"))
        assertTrue(FloydUpdateChecker.compareSemver("1.2.3", "1.2.4") < 0)
    }

    @Test
    fun `state exposes Floyd low-level hook switches`() {
        val state = FloydCompatibility.state()

        assertEquals(true, state["enabled"])
        assertEquals(true, state["spoofClientBrand"])
        assertEquals(true, state["hideWatchdogMessages"])
        assertEquals(true, state["customMainMenu"])
        assertEquals(true, state["taskbarIcon"])
        assertEquals(true, state["updateChecker"])
        assertEquals(true, state["hideLoaderEntry"])
        assertEquals(true, state["shouldSpoofClientBrand"])
        assertEquals(true, state["shouldHideWatchdogMessages"])
        assertEquals(true, state["shouldUseCustomMainMenu"])
        assertEquals(true, state["shouldApplyTaskbarIcon"])
        assertEquals(true, state["shouldCheckUpdates"])
        assertEquals(true, state["shouldHideLoaderEntry"])

        val updateCheckerState = state["updateCheckerState"]
        assertTrue(updateCheckerState is Map<*, *>)
        assertNotNull(updateCheckerState["initialized"])
        assertNotNull(updateCheckerState["checked"])
        assertNotNull(updateCheckerState["pendingMessage"])
        assertEquals("https://api.github.com/repos/lunabot9/FloydAddons/releases", updateCheckerState["releaseApi"])
    }

    @Test
    fun `low level hook gates require both module and Floyd switch enabled`() {
        withCompatibilityState {
            val gates = listOf(
                "Spoof Client Brand" to FloydCompatibility::shouldSpoofClientBrand,
                "Hide Watchdog Messages" to FloydCompatibility::shouldHideWatchdogMessages,
                "Custom Main Menu" to FloydCompatibility::shouldUseCustomMainMenu,
                "Taskbar Icon" to FloydCompatibility::shouldApplyTaskbarIcon,
                "Update Checker" to FloydCompatibility::shouldCheckUpdates,
                "Hide Loader Entry" to FloydCompatibility::shouldHideLoaderEntry,
            )

            for ((settingName, gate) in gates) {
                assertTrue(gate(), "Expected $settingName gate enabled by default")

                bool(settingName).enabled = false
                assertFalse(gate(), "Expected $settingName gate to follow its Floyd switch")

                bool(settingName).enabled = true
                assertTrue(gate(), "Expected $settingName gate restored after switch reset")
            }

            FloydCompatibility.toggle()
            for ((settingName, gate) in gates) {
                assertFalse(gate(), "Expected $settingName gate disabled by outer Odin module")
            }
        }
    }

    private fun bool(name: String): BooleanSetting =
        FloydCompatibility.settings[name] as? BooleanSetting ?: error("Missing BooleanSetting: $name")

    private fun withCompatibilityState(block: () -> Unit) {
        val enabled = FloydCompatibility.enabled
        val settingValues = FloydCompatibility.settings.mapValues { (_, setting) ->
            (setting as? BooleanSetting)?.enabled
        }
        try {
            if (!FloydCompatibility.enabled) FloydCompatibility.toggle()
            for ((name, value) in settingValues) if (value != null) bool(name).enabled = true
            block()
        } finally {
            if (FloydCompatibility.enabled != enabled) FloydCompatibility.toggle()
            for ((name, value) in settingValues) if (value != null) bool(name).enabled = value
        }
    }
}
