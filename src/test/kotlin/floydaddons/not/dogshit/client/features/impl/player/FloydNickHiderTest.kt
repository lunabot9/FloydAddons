package floydaddons.not.dogshit.client.features.impl.player

import floydaddons.not.dogshit.client.clickgui.settings.impl.BooleanSetting
import floydaddons.not.dogshit.client.features.impl.hiders.FloydHiders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minecraft.network.chat.Component

class FloydNickHiderTest {
    @Test
    fun `self nickname setter preserves Floyd empty-string guard`() {
        val previous = FloydNickHider.nickname
        try {
            FloydNickHider.nickname = "George Floyd"

            FloydNickHider.setSelfNickname("   ")
            assertEquals("   ", FloydNickHider.nickname)

            FloydNickHider.setSelfNickname("")
            assertEquals("   ", FloydNickHider.nickname)
        } finally {
            FloydNickHider.nickname = previous
        }
    }

    @Test
    fun `replacement gate follows Floyd toggles instead of nickname blankness`() {
        withNickHiderState {
            FloydNickHider.nickname = "   "
            if (!FloydNickHider.enabled) FloydNickHider.toggle()
            hiderBool("Server ID Hider").enabled = false
            hiderBool("Profile ID Hider").enabled = false

            assertTrue(FloydNickHider.hasReplacements())
        }
    }

    @Test
    fun `name mappings can replace with whitespace like Floyd`() {
        val replaced = FloydNickHider.replaceInComponentForTest(
            Component.literal("hi Technoblade bye"),
            "Technoblade",
            "   "
        )

        assertEquals("hi     bye", replaced.string)
    }

    @Test
    fun `name mappings summary uses Floyd list format`() {
        withNickHiderState {
            FloydNickHider.nickname = "George Floyd"

            assertEquals(
                "--- Name Mappings ---\nDefault nick: George Floyd\nNo player mappings configured.",
                FloydNickHider.nameMappingsSummary()
            )

            FloydNickHider.addNameMapping("Technoblade", "Pig")

            assertEquals(
                "--- Name Mappings ---\nDefault nick: George Floyd\nTechnoblade → Pig",
                FloydNickHider.nameMappingsSummary()
            )
        }
    }

    @Test
    fun `server id debug summary preserves Floyd command header and empty-cache wording`() {
        withNickHiderState {
            hiderBool("Server ID Hider").enabled = false

            val summary = FloydNickHider.debugSummary()

            assertTrue(summary.contains("--- Server ID Hider Debug ---"))
            assertTrue(summary.contains("Enabled: false"))
            assertTrue(summary.contains("Replacement: fL0YD"))
            assertTrue(summary.contains("Current server: (none detected)"))
            assertTrue(summary.contains("Accumulated IDs: (none - nothing will be hidden)"))
            assertTrue(summary.contains("Known tab UUID: (none)"))
            assertTrue(summary.contains("Rapid scan: inactive"))
        }
    }

    private fun hiderBool(name: String): BooleanSetting =
        FloydHiders.settings[name] as? BooleanSetting ?: error("Missing Hiders BooleanSetting: $name")

    private fun withNickHiderState(block: () -> Unit) {
        val nickname = FloydNickHider.nickname
        val enabled = FloydNickHider.enabled
        val serverId = hiderBool("Server ID Hider").enabled
        val profileId = hiderBool("Profile ID Hider").enabled
        val mappings = FloydNickHider.nameMappings.toMap()
        try {
            FloydNickHider.clearNameMappings()
            block()
        } finally {
            FloydNickHider.nickname = nickname
            if (FloydNickHider.enabled != enabled) FloydNickHider.toggle()
            hiderBool("Server ID Hider").enabled = serverId
            hiderBool("Profile ID Hider").enabled = profileId
            FloydNickHider.clearNameMappings()
            for ((realName, fakeName) in mappings) FloydNickHider.addNameMapping(realName, fakeName)
        }
    }
}
