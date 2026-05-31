package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydMobEspSidecarTest {
    @Test
    fun `fresh Mob ESP filters start empty like Floyd`() {
        assertEquals(emptySet(), FloydMobEsp.nameFilterIds())
        assertEquals(emptySet(), FloydMobEsp.typeFilterIds())
        assertTrue("shadow assassin" in FloydMobEsp.knownMinibossNameIds())
    }

    @Test
    fun `empty sidecar load clears existing filters`() {
        try {
            FloydMobEsp.loadSidecarEntries(
                listOf(
                    mapOf("name" to "temporary npc", "color" to "#55FF55", "chroma" to "true"),
                    mapOf("mob" to "minecraft:ghast")
                )
            )
            assertTrue("temporary npc" in FloydMobEsp.nameFilterIds())
            assertTrue("minecraft:ghast" in FloydMobEsp.typeFilterIds())

            FloydMobEsp.loadSidecarEntries(emptyList())

            assertFalse("temporary npc" in FloydMobEsp.nameFilterIds())
            assertFalse("minecraft:ghast" in FloydMobEsp.typeFilterIds())
        } finally {
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `invalid sidecar filter colors fall back to white like Floyd`() {
        try {
            FloydMobEsp.loadSidecarEntries(
                listOf(
                    mapOf("name" to "temporary npc", "color" to "not-a-color", "chroma" to "true"),
                    mapOf("mob" to "minecraft:ghast", "color" to "#12", "chroma" to "TRUE")
                )
            )

            assertEquals("#FFFFFF chroma", FloydMobEsp.colorSummaryForName("temporary npc"))
            assertEquals("#FFFFFF chroma", FloydMobEsp.colorSummaryForType("minecraft:ghast"))
        } finally {
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `Mob ESP filter colors trim before hash stripping like Floyd`() {
        try {
            FloydMobEsp.loadSidecarEntries(
                listOf(
                    mapOf("name" to "temporary npc", "color" to " #55FF55 ", "chroma" to "true")
                )
            )

            assertEquals("#55FF55 chroma", FloydMobEsp.colorSummaryForName("temporary npc"))
            FloydMobEsp.setNameFilterColor("temporary npc", " #AA00AA ", false)
            assertEquals("#AA00AA", FloydMobEsp.colorSummaryForName("temporary npc"))
        } finally {
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `Mob ESP name filters preserve user casing like Floyd raw entries`() {
        try {
            assertTrue(FloydMobEsp.addNameFilter("Shadow Assassin"))
            assertEquals(setOf("Shadow Assassin"), FloydMobEsp.nameFilterIds())
            assertTrue(FloydMobEsp.addNameFilter("shadow assassin"))
            assertEquals(setOf("Shadow Assassin"), FloydMobEsp.nameFilterIds())

            FloydMobEsp.setNameFilterColor("shadow assassin", "55FF55", true)
            val entries = FloydMobEsp.sidecarEntries()
            assertEquals(listOf("Shadow Assassin", "shadow assassin"), entries.map { it.getValue("name") })
            assertEquals("#55FF55", entries.first().getValue("color"))
            assertEquals("#55FF55 chroma", FloydMobEsp.colorSummaryForName("Shadow Assassin"))
        } finally {
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `Mob ESP sidecar entries preserve raw duplicates like Floyd`() {
        try {
            FloydMobEsp.loadSidecarEntries(
                listOf(
                    mapOf("name" to "Shadow Assassin"),
                    mapOf("name" to "shadow assassin", "color" to "#AA00AA"),
                    mapOf("mob" to "minecraft:zombie"),
                    mapOf("mob" to "minecraft:zombie", "chroma" to "true")
                )
            )

            assertEquals(setOf("Shadow Assassin"), FloydMobEsp.nameFilterIds())
            assertEquals(setOf("minecraft:zombie"), FloydMobEsp.typeFilterIds())
            assertEquals(4, FloydMobEsp.sidecarEntries().size)
            assertEquals(listOf("Shadow Assassin", "shadow assassin"), FloydMobEsp.sidecarEntries().mapNotNull { it["name"] })

            assertTrue(FloydMobEsp.removeNameFilter("shadow assassin"))
            assertEquals(emptySet(), FloydMobEsp.nameFilterIds())
            assertEquals(listOf("minecraft:zombie", "minecraft:zombie"), FloydMobEsp.sidecarEntries().mapNotNull { it["mob"] })
        } finally {
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `Mob ESP editor name filter input trims and rejects blanks like Floyd GUI search add`() {
        assertEquals("Shadow Assassin", FloydMobEsp.validUserNameFilter("  Shadow Assassin  "))

        assertFailsWith<IllegalArgumentException> {
            FloydMobEsp.validUserNameFilter("   ")
        }
    }

    @Test
    fun `user Mob ESP type filter ids are validated like Floyd command identifiers`() {
        assertEquals("minecraft:zombie", FloydMobEsp.validTypeFilterId("minecraft:zombie"))
        assertEquals("minecraft:zombie", FloydMobEsp.validTypeFilterId("Minecraft:Zombie"))

        assertFailsWith<IllegalArgumentException> {
            FloydMobEsp.validTypeFilterId("not an entity id")
        }
        assertFailsWith<IllegalArgumentException> {
            FloydMobEsp.validTypeFilterId("bad:id:extra")
        }
    }

    @Test
    fun `user Mob ESP type filter removal validates ids like Floyd command identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            FloydMobEsp.removeUserTypeFilter("not an entity id")
        }
        assertFailsWith<IllegalArgumentException> {
            FloydMobEsp.removeUserTypeFilter("bad:id:extra")
        }
    }

    @Test
    fun `user Mob ESP type filter color validates ids like Floyd command identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            FloydMobEsp.setUserTypeFilterColor("not an entity id", "55FF55", false)
        }
        assertFailsWith<IllegalArgumentException> {
            FloydMobEsp.setUserTypeFilterColor("bad:id:extra", "55FF55", false)
        }
    }

    @Test
    fun `removing Mob ESP filters clears stale colors like Floyd raw entries`() {
        try {
            FloydMobEsp.addNameFilter("Shadow Assassin")
            FloydMobEsp.setNameFilterColor("Shadow Assassin", "55FF55", true)
            assertEquals("#55FF55 chroma", FloydMobEsp.colorSummaryForName("shadow assassin"))

            assertTrue(FloydMobEsp.removeNameFilter("shadow assassin"))
            FloydMobEsp.addNameFilter("Shadow Assassin")
            assertEquals("default", FloydMobEsp.colorSummaryForName("shadow assassin"))

            FloydMobEsp.addTypeFilter("minecraft:zombie")
            FloydMobEsp.setTypeFilterColor("minecraft:zombie", "AA00AA", false)
            assertEquals("#AA00AA", FloydMobEsp.colorSummaryForType("minecraft:zombie"))

            assertTrue(FloydMobEsp.removeTypeFilter("minecraft:zombie"))
            FloydMobEsp.addTypeFilter("minecraft:zombie")
            assertEquals("default", FloydMobEsp.colorSummaryForType("minecraft:zombie"))
        } finally {
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `Mob ESP list summary uses Floyd command wording`() {
        try {
            FloydMobEsp.addNameFilter("Shadow Assassin")
            FloydMobEsp.setNameFilterColor("Shadow Assassin", "55FF55", true)
            FloydMobEsp.addTypeFilter("minecraft:zombie")

            val summary = FloydMobEsp.filterListSummary()

            assertEquals(
                "--- Mob ESP Filters ---\nname: Shadow Assassin\ntype: minecraft:zombie\nStar mobs: OFF",
                summary
            )

            FloydMobEsp.clearFilters()
            assertEquals(
                "--- Mob ESP Filters ---\nNo filters configured.\nStar mobs: OFF",
                FloydMobEsp.filterListSummary()
            )
        } finally {
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `Mob ESP toggle summary uses Floyd command wording`() {
        val wasEnabled = FloydMobEsp.enabled
        try {
            if (FloydMobEsp.enabled) FloydMobEsp.toggle()
            assertEquals("Mob ESP disabled", FloydMobEsp.toggleSummary())

            FloydMobEsp.addNameFilter("Shadow Assassin")
            FloydMobEsp.addTypeFilter("minecraft:zombie")
            FloydMobEsp.toggle()

            assertEquals("Mob ESP enabled (1 names, 1 types)", FloydMobEsp.toggleSummary())
        } finally {
            FloydMobEsp.clearFilters()
            if (FloydMobEsp.enabled != wasEnabled) FloydMobEsp.toggle()
        }
    }

    @Test
    fun `stalk target blank input disables runtime stalk state`() {
        FloydMobEsp.stalk("PlayerOne")
        assertTrue(FloydMobEsp.stalkEnabled())
        assertEquals("PlayerOne", FloydMobEsp.stalkTarget())
        assertEquals("PlayerOne", FloydMobEsp.stopStalk())
        assertFalse(FloydMobEsp.stalkEnabled())
        assertEquals(null, FloydMobEsp.stopStalk())

        FloydMobEsp.stalk("   ")
        assertFalse(FloydMobEsp.stalkEnabled())
        assertEquals("", FloydMobEsp.stalkTarget())
        assertEquals(null, FloydMobEsp.stopStalk())
    }
}
