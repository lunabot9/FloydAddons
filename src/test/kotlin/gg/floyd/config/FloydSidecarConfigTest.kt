package gg.floyd.config

import gg.floyd.features.impl.player.FloydNickHider
import gg.floyd.features.impl.render.FloydMobEsp
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydSidecarConfigTest {
    @TempDir
    lateinit var configDir: Path

    @Test
    fun `fresh Floyd sidecar templates survive first empty save`() {
        val namesPath = configDir.resolve("name-mappings.json")
        val xrayPath = configDir.resolve("xray-opaque.json")
        val mobEspPath = configDir.resolve("mob-esp.json")

        FloydNickHider.clearNameMappings()
        FloydMobEsp.clearFilters()
        try {
            FloydSidecarConfig.withConfigDirForTest(configDir) {
                FloydSidecarConfig.loadExistingSidecars()
                val state = FloydSidecarConfig.state()
                val nameTemplate = Files.readString(namesPath)
                val xrayDefaults = Files.readString(xrayPath)
                val mobEspTemplate = Files.readString(mobEspPath)

                assertEquals(configDir.toString(), state["configDir"])
                assertEquals(true, state["preserveFreshNameTemplate"])
                assertEquals(true, state["preserveFreshMobEspTemplate"])
                assertEquals(namesPath.toString(), (state["nameMappings"] as Map<*, *>)["path"])
                assertEquals(true, (state["nameMappings"] as Map<*, *>)["exists"])
                assertEquals(xrayPath.toString(), (state["xrayOpaque"] as Map<*, *>)["path"])
                assertEquals(true, (state["xrayOpaque"] as Map<*, *>)["exists"])
                assertEquals(mobEspPath.toString(), (state["mobEsp"] as Map<*, *>)["path"])
                assertEquals(true, (state["mobEsp"] as Map<*, *>)["exists"])
                assertTrue(nameTemplate.contains("ExampleIGN"))
                assertTrue(xrayDefaults.contains("minecraft:glass"))
                assertTrue(xrayDefaults.contains("minecraft:black_stained_glass_pane"))
                assertTrue(mobEspTemplate.contains("Vanquisher"))
                assertEquals(emptySet(), FloydNickHider.mappingIds())
                assertEquals(emptySet(), FloydMobEsp.nameFilterIds())
                assertEquals(emptySet(), FloydMobEsp.typeFilterIds())

                FloydSidecarConfig.saveSidecars()

                assertEquals(nameTemplate, Files.readString(namesPath))
                assertEquals(mobEspTemplate, Files.readString(mobEspPath))

                FloydNickHider.addNameMapping("Technoblade", "Pig")
                FloydMobEsp.addNameFilter("Shadow Assassin")
                FloydMobEsp.addTypeFilter("minecraft:ghast")

                FloydSidecarConfig.saveSidecars()

                val savedNames = Files.readString(namesPath)
                val savedMobEsp = Files.readString(mobEspPath)
                assertTrue(savedNames.contains("Technoblade"))
                assertTrue(savedNames.contains("Pig"))
                assertTrue(savedMobEsp.contains("Shadow Assassin"))
                assertTrue(savedMobEsp.contains("minecraft:ghast"))
            }
        } finally {
            FloydNickHider.clearNameMappings()
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `invalid Floyd sidecars clear stale runtime entries like Floyd loader`() {
        val namesPath = configDir.resolve("name-mappings.json")
        val mobEspPath = configDir.resolve("mob-esp.json")
        Files.createDirectories(configDir)
        Files.writeString(namesPath, "{ invalid")
        Files.writeString(mobEspPath, "{ invalid")

        FloydNickHider.clearNameMappings()
        FloydNickHider.addNameMapping("Technoblade", "Pig")
        FloydMobEsp.clearFilters()
        FloydMobEsp.addNameFilter("Shadow Assassin")
        FloydMobEsp.addTypeFilter("minecraft:zombie")

        try {
            FloydSidecarConfig.withConfigDirForTest(configDir) {
                FloydSidecarConfig.loadExistingSidecars()

                assertEquals(emptySet(), FloydNickHider.mappingIds())
                assertEquals(emptySet(), FloydMobEsp.nameFilterIds())
                assertEquals(emptySet(), FloydMobEsp.typeFilterIds())
            }
        } finally {
            FloydNickHider.clearNameMappings()
            FloydMobEsp.clearFilters()
        }
    }

    @Test
    fun `Floyd name mapping sidecars preserve key casing and remove case-insensitively`() {
        FloydNickHider.clearNameMappings()
        try {
            FloydNickHider.addNameMapping("Technoblade", "Pig")
            assertEquals(setOf("Technoblade"), FloydNickHider.mappingIds())
            assertTrue(FloydNickHider.removeNameMapping("technoblade"))
            assertEquals(emptySet(), FloydNickHider.mappingIds())

            FloydSidecarConfig.withConfigDirForTest(configDir) {
                Files.createDirectories(configDir)
                Files.writeString(configDir.resolve("name-mappings.json"), "{\"SomeIGN\":\"Fake\"}")
                FloydSidecarConfig.loadExistingSidecars()
                assertEquals(setOf("SomeIGN"), FloydNickHider.mappingIds())

                FloydSidecarConfig.saveSidecars()
                assertTrue(Files.readString(configDir.resolve("name-mappings.json")).contains("SomeIGN"))
            }
        } finally {
            FloydNickHider.clearNameMappings()
        }
    }

    @Test
    fun `Floyd name mapping sidecars preserve raw empty and whitespace strings`() {
        FloydNickHider.clearNameMappings()
        try {
            FloydSidecarConfig.withConfigDirForTest(configDir) {
                Files.createDirectories(configDir)
                Files.writeString(configDir.resolve("name-mappings.json"), "{\"\":\"BlankKey\",\"Whitespace\":\"   \",\"   \":\"BlankFind\"}")

                FloydSidecarConfig.loadExistingSidecars()

                assertEquals("BlankKey", FloydNickHider.nameMappings[""])
                assertEquals("   ", FloydNickHider.nameMappings["Whitespace"])
                assertEquals("BlankFind", FloydNickHider.nameMappings["   "])

                FloydSidecarConfig.saveSidecars()
                val saved = Files.readString(configDir.resolve("name-mappings.json"))
                assertTrue(saved.contains("\"\""))
                assertTrue(saved.contains("\"Whitespace\""))
                assertTrue(saved.contains("\"   \""))
            }
        } finally {
            FloydNickHider.clearNameMappings()
        }
    }

}
