package gg.floyd.features.impl.player

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.features.Module
import gg.floyd.features.impl.hiders.FloydProfileIdHider
import gg.floyd.features.impl.hiders.FloydServerIdHider
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
            bool("Enabled").enabled = true
            setHider("Server ID Hider", false)
            setHider("Profile ID Hider", false)

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
            setHider("Server ID Hider", false)

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

    private fun bool(name: String): BooleanSetting =
        FloydNickHider.settings[name] as? BooleanSetting ?: error("Missing BooleanSetting: $name")

    private fun hiderModule(name: String): Module = when (name) {
        "Server ID Hider" -> FloydServerIdHider
        "Profile ID Hider" -> FloydProfileIdHider
        else -> error("Unknown hider module: $name")
    }

    private fun setHider(name: String, value: Boolean) {
        val module = hiderModule(name)
        if (module.enabled != value) module.toggle()
    }

    private fun withNickHiderState(block: () -> Unit) {
        val nickname = FloydNickHider.nickname
        val enabled = bool("Enabled").enabled
        val serverId = FloydServerIdHider.enabled
        val profileId = FloydProfileIdHider.enabled
        val mappings = FloydNickHider.nameMappings.toMap()
        try {
            FloydNickHider.clearNameMappings()
            block()
        } finally {
            FloydNickHider.nickname = nickname
            bool("Enabled").enabled = enabled
            setHider("Server ID Hider", serverId)
            setHider("Profile ID Hider", profileId)
            FloydNickHider.clearNameMappings()
            for ((realName, fakeName) in mappings) FloydNickHider.addNameMapping(realName, fakeName)
        }
    }
}
