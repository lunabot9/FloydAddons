package gg.floyd.features.impl.misc

import gg.floyd.features.Module
import gg.floyd.features.impl.hiders.FloydHideWatchdogMessages
import gg.floyd.features.impl.hiders.FloydModHider
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
    fun `low level hook gates follow their per-feature module`() {
        val gates = listOf(
            FloydSpoofClientBrand to FloydCompatibility::shouldSpoofClientBrand,
            FloydHideWatchdogMessages to FloydCompatibility::shouldHideWatchdogMessages,
            FloydTaskbarIconModule to FloydCompatibility::shouldApplyTaskbarIcon,
            FloydUpdateCheckerModule to FloydCompatibility::shouldCheckUpdates,
            FloydModHider to FloydCompatibility::shouldHideLoaderEntry,
        )

        for ((module, gate) in gates) {
            withModuleEnabled(module) {
                assertTrue(gate(), "Expected ${module.name} gate enabled by default")

                module.toggle()
                assertFalse(gate(), "Expected ${module.name} gate to follow its module")

                module.toggle()
                assertTrue(gate(), "Expected ${module.name} gate restored after toggle back")
            }
        }
    }

    @Test
    fun `safe hud layer routes around SkyHanni specifically`() {
        assertTrue(FloydCompatibility.shouldUseSafeHudLayer(setOf("fabricloader", "skyhanni")))
        assertFalse(FloydCompatibility.shouldUseSafeHudLayer(setOf("fabricloader", "skyblocker")))
        assertFalse(FloydCompatibility.shouldUseSafeHudLayer(emptySet()))
    }

    @Test
    fun `custom main menu hook follows the enabled module on the current port`() {
        assertTrue(FloydCompatibility.shouldUseCustomMainMenu())
    }

    @Test
    fun `current port keeps hud panels on the deferred gui layer`() {
        assertTrue(FloydCompatibility.shouldRenderHudPanelsOnGuiLayer())
    }

    private fun withModuleEnabled(module: Module, block: () -> Unit) {
        val enabled = module.enabled
        try {
            if (!module.enabled) module.toggle()
            block()
        } finally {
            if (module.enabled != enabled) module.toggle()
        }
    }
}
