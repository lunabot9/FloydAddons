package floydaddons.not.dogshit.client.features.impl.misc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydDiscordPresenceTest {
    @Test
    fun `state exposes Floyd Discord service status without starting RPC`() {
        val state = FloydDiscordPresence.state()

        assertEquals(true, state["enabled"])
        assertEquals(true, state["shouldRun"])
        assertEquals(false, state["initialized"])
        assertEquals(false, state["failed"])
        assertEquals(false, state["lastShouldRun"])
        assertTrue(state["appIdConfigured"] == true)
        assertEquals("floydaddons_icon", state["largeImageKey"])
        assertEquals(false, state["callbackThreadAlive"])
    }
}
