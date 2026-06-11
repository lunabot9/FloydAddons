package gg.floyd.config

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Color
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleConfigTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `legacy Nick Hider config entries load into Floyd GUI Neck Hider module`() {
        val module = LegacyNamedModule(name = "Neck Hider")
        loadLegacyConfigEntry("Nick Hider", module)

        // Neck Hider's inner "Enabled" was collapsed into the module toggle (the module IS the
        // nick-replacement switch now): the legacy "Enabled" folds into module.enabled via
        // migrateCollapsedEnabledToggles instead of landing on a separate setting.
        assertTrue(module.enabled)
        assertFalse(module.featureEnabled)
        assertEquals("Neck Hider", module.name)
    }

    @Test
    fun `legacy Neck Hider id toggles load into Floyd GUI Hiders module`() {
        val neckHider = LegacyNeckHiderModule()
        val hiders = LegacyHidersModule()
        loadLegacyConfigEntry(
            "Neck Hider",
            listOf(neckHider, hiders),
            """
            {
              "Server ID Hider": true,
              "Profile ID Hider": false
            }
            """.trimIndent()
        )

        assertTrue(hiders.serverIdHider)
        assertEquals(false, hiders.profileIdHider)
    }

    @Test
    fun `legacy Render id toggles load into Floyd GUI Hiders module`() {
        val general = LegacyGeneralModule()
        val hiders = LegacyHidersModule()
        loadLegacyConfigEntry(
            "Render",
            listOf(general, hiders),
            """
            {
              "Server ID Hider": true,
              "Profile ID Hider": false
            }
            """.trimIndent()
        )

        assertTrue(hiders.serverIdHider)
        assertEquals(false, hiders.profileIdHider)
    }

    @Test
    fun `legacy cosmetic config names load into Floyd GUI module names`() {
        val skin = LegacyCustomSkinModule()
        loadLegacyConfigEntry(
            "Skin",
            skin,
            """
            {
              "Enabled": true,
              "Selected Skin": "legacy-skin.png"
            }
            """.trimIndent()
        )

        assertTrue(skin.enabled)
        assertTrue(skin.featureEnabled)
        assertEquals("legacy-skin.png", skin.skin)
        assertEquals("Custom Skin", skin.name)

        val cape = LegacyCustomCapeModule()
        loadLegacyConfigEntry(
            "Cape",
            cape,
            """
            {
              "Enabled": true,
              "Selected Cape": "legacy-cape.png"
            }
            """.trimIndent()
        )

        // Custom Cape's inner "Enabled" was collapsed into the module toggle: the legacy "Enabled"
        // now folds into module.enabled (migrateCollapsedEnabledToggles), so the module is enabled
        // rather than a separate featureEnabled setting.
        assertTrue(cape.enabled)
        assertEquals("legacy-cape.png", cape.image)
        assertEquals("Custom Cape", cape.name)
    }

    @Test
    fun `legacy animation and camera setting names load into Floyd GUI setting names`() {
        val camera = LegacyCameraModule()
        loadLegacyConfigEntry(
            "Camera",
            camera,
            """
            {
              "Freecam Speed": 3.5,
              "Freelook Distance": 7.0,
              "Disable Front Camera": true,
              "Disable Back Camera": true,
              "Scroll F5 Distance": true,
              "Reset F5 Distance": true,
              "F5 Distance": 9.0
            }
            """.trimIndent()
        )

        assertEquals(3.5f, camera.speed)
        assertEquals(7.0f, camera.distance)
        assertTrue(camera.disableFrontCam)
        assertTrue(camera.disableBackCam)
        assertTrue(camera.scrollingChangesDistance)
        assertTrue(camera.resetF5Scrolling)
        assertEquals(9.0f, camera.cameraDistance)

        val animations = LegacyAnimationsModule()
        loadLegacyConfigEntry(
            "Animations",
            animations,
            """
            {
              "Position X": 10,
              "Position Y": 11,
              "Position Z": 12,
              "Rotation X": 13,
              "Rotation Y": 14,
              "Rotation Z": 15,
              "Hide Empty Hand": true
            }
            """.trimIndent()
        )

        assertEquals(10, animations.posX)
        assertEquals(11, animations.posY)
        assertEquals(12, animations.posZ)
        assertEquals(13, animations.rotX)
        assertEquals(14, animations.rotY)
        assertEquals(15, animations.rotZ)
        assertTrue(animations.hideHand)
    }

    @Test
    fun `legacy render setting names load into Floyd GUI setting names`() {
        // Window styling (Borderless / Instance Title) was un-nested off the dissolved Render/General
        // module onto the Misc "Window" module, so those keys now migrate there; the time-changer keys
        // still resolve onto the General-derived doubles via the legacy setting-name aliases.
        val render = LegacyGeneralModule()
        val window = LegacyWindowModule()
        loadLegacyConfigEntry(
            "Render",
            listOf(render, window),
            """
            {
              "Custom Time": true,
              "Custom Time Value": 42.0,
              "Borderless Windowed": true,
              "Window Title": "Floyd Instance"
            }
            """.trimIndent()
        )

        assertTrue(render.timeChanger)
        assertEquals(42.0f, render.time)
        assertTrue(window.borderlessWindow)
        assertEquals("Floyd Instance", window.instanceTitle)

        val mobEsp = LegacyMobEspModule()
        loadLegacyConfigEntry(
            "Mob ESP",
            mobEsp,
            """
            {
              "Default Color": "#112233FF",
              "Stalk Color": "#445566FF"
            }
            """.trimIndent()
        )

        assertEquals(Color("112233FF"), mobEsp.defaultEspColor)
        assertEquals(Color("445566FF"), mobEsp.tracerColor)
    }

    @Test
    fun `legacy player setting names load into Floyd GUI setting names`() {
        val neckHider = LegacyNeckHiderModule()
        loadLegacyConfigEntry(
            "Neck Hider",
            neckHider,
            """
            {
              "Nickname": "Legacy Nick"
            }
            """.trimIndent()
        )

        assertEquals("Legacy Nick", neckHider.defaultNick)

        val playerSize = LegacyPlayerSizeModule()
        loadLegacyConfigEntry(
            "Player Size",
            playerSize,
            """
            {
              "Scale X": 2.0,
              "Scale Y": 3.0,
              "Scale Z": 4.0,
              "Size Target": "All"
            }
            """.trimIndent()
        )

        assertEquals(2.0f, playerSize.x)
        assertEquals(3.0f, playerSize.y)
        assertEquals(4.0f, playerSize.z)
        assertEquals(2, playerSize.target)
    }

    @Test
    fun `legacy cone hat rotation speed setting loads into Floyd GUI spin speed label`() {
        val coneHat = LegacyConeHatModule()
        loadLegacyConfigEntry(
            "Cone Hat",
            coneHat,
            """
            {
              "Selected Image": "legacy-cone.png",
              "Rotation Speed": 123.0
            }
            """.trimIndent()
        )

        assertEquals("legacy-cone.png", coneHat.image)
        assertEquals(123.0f, coneHat.spinSpeed)
    }

    @Test
    fun `legacy hiders no armor mode setting loads into Floyd GUI target label`() {
        val hiders = LegacyHidersModule()
        loadLegacyConfigEntry(
            "Hiders",
            hiders,
            """
            {
              "Third Person Crosshair": true,
              "Remove Explosion Particles": true,
              "No Armor Mode": "Self"
            }
            """.trimIndent()
        )

        assertTrue(hiders.thirdPersonCrosshair)
        assertTrue(hiders.noExplosionParticles)
        assertEquals(1, hiders.target)
    }

    @Test
    fun `moved HUD keys migrate from legacy hud module into Panel Style and Inventory HUD`() {
        val panelStyle = MovedPanelStyleModule()
        val inventory = MovedInventoryModule()
        val configPath = tempDir.resolve("floydaddons-config.json")
        java.nio.file.Files.writeString(
            configPath,
            """
            [
              {
                "name": "HUD",
                "enabled": true,
                "settings": {
                  "Scoreboard Color": "#112233FF",
                  "Scoreboard Fade": true,
                  "Padding": 11,
                  "Inventory HUD Scale": 2.5,
                  "HUD Corner Radius": 9
                }
              }
            ]
            """.trimIndent()
        )
        val config = ModuleConfig(configPath.toFile())
        for (module in listOf(panelStyle, inventory)) config.modules[module.name.lowercase()] = module

        config.load()

        // Legacy scoreboard cosmetics fold into the unified Panel Style; fade now lives on the border color.
        assertEquals(Color("112233FF").baseRgba, panelStyle.borderColor.baseRgba)
        assertTrue(panelStyle.borderColor.fade)
        assertEquals(11, panelStyle.padding)
        assertEquals(9, panelStyle.cornerRadius)
        // Inventory scale is not a panel cosmetic and stays on the Inventory HUD module.
        assertEquals(2.5f, inventory.scale)

        val rewritten = java.nio.file.Files.readString(configPath)
        assertTrue("Scoreboard Color" !in jsonForModule(rewritten, "HUD"))
        assertTrue("Inventory HUD Scale" !in jsonForModule(rewritten, "HUD"))
        assertTrue("HUD Corner Radius" !in jsonForModule(rewritten, "HUD"))
    }

    @Test
    fun `per-panel cosmetics migrate into the unified Panel Style module`() {
        val panelStyle = MovedPanelStyleModule()
        val inventory = MovedInventoryModule()
        val configPath = tempDir.resolve("floydaddons-config.json")
        java.nio.file.Files.writeString(
            configPath,
            """
            [
              {
                "name": "Custom Scoreboard",
                "enabled": false,
                "settings": { "Padding": 5 }
              },
              {
                "name": "Inventory HUD",
                "enabled": false,
                "settings": { "Inventory HUD Scale": 3.0 }
              },
              {
                "name": "Render",
                "enabled": false,
                "settings": { "HUD Corner Radius": 7, "Full Chat Chroma": true }
              },
              {
                "name": "Player ESP",
                "enabled": false,
                "settings": { "Overhead Fade": true }
              }
            ]
            """.trimIndent()
        )
        val config = ModuleConfig(configPath.toFile())
        for (module in listOf(panelStyle, inventory)) config.modules[module.name.lowercase()] = module

        config.load()

        assertEquals(5, panelStyle.padding)
        assertEquals(7, panelStyle.cornerRadius)
        assertTrue(panelStyle.borderColor.fade)
        assertEquals(3.0f, inventory.scale)
    }

    @Test
    fun `panel border chroma fade and fade color fold into the Panel Border Color picker`() {
        val panelStyle = MovedPanelStyleModule()
        val configPath = tempDir.resolve("floydaddons-config.json")
        java.nio.file.Files.writeString(
            configPath,
            """
            [
              {
                "name": "Panel Style",
                "enabled": true,
                "settings": {
                  "Panel Border Color": { "hex": "#FF0000FF", "chroma": false },
                  "Border Fade": true,
                  "Border Fade Color": { "hex": "#00FF00FF", "chroma": false }
                }
              }
            ]
            """.trimIndent()
        )
        val config = ModuleConfig(configPath.toFile())
        config.modules[panelStyle.name.lowercase()] = panelStyle

        config.load()

        assertEquals(Color("FF0000FF").baseRgba, panelStyle.borderColor.baseRgba)
        assertTrue(panelStyle.borderColor.fade)
        assertFalse(panelStyle.borderColor.chroma)
        assertEquals(Color("00FF00FF").baseRgba, panelStyle.borderColor.fadeColor.baseRgba)

        // The sibling keys are gone from the rewritten config.
        val rewritten = java.nio.file.Files.readString(configPath)
        assertTrue("Border Fade" !in jsonForModule(rewritten, "Panel Style"))
        assertTrue("Border Fade Color" !in jsonForModule(rewritten, "Panel Style"))
    }

    @Test
    fun `panel border chroma wins over fade when both were set`() {
        val panelStyle = MovedPanelStyleModule()
        val configPath = tempDir.resolve("floydaddons-config.json")
        java.nio.file.Files.writeString(
            configPath,
            """
            [
              {
                "name": "Panel Style",
                "enabled": true,
                "settings": {
                  "Panel Border Color": { "hex": "#FFFFFFFF", "chroma": false },
                  "Border Chroma": true,
                  "Border Fade": true
                }
              }
            ]
            """.trimIndent()
        )
        val config = ModuleConfig(configPath.toFile())
        config.modules[panelStyle.name.lowercase()] = panelStyle

        config.load()

        assertTrue(panelStyle.borderColor.chroma)
        assertFalse(panelStyle.borderColor.fade)
    }

    private fun jsonForModule(configJson: String, moduleName: String): String {
        val array = com.google.gson.JsonParser.parseString(configJson).asJsonArray
        val obj = array.first { it.asJsonObject.get("name").asString == moduleName }.asJsonObject
        return obj.toString()
    }

    private fun loadLegacyConfigEntry(
        legacyName: String,
        module: Module,
        settingsJson: String = """{ "Enabled": true }"""
    ) = loadLegacyConfigEntry(legacyName, listOf(module), settingsJson)

    private fun loadLegacyConfigEntry(
        legacyName: String,
        modules: List<Module>,
        settingsJson: String = """{ "Enabled": true }"""
    ) {
        val configPath = tempDir.resolve("floydaddons-config.json")
        java.nio.file.Files.writeString(
            configPath,
            """
            [
              {
                "name": "$legacyName",
                "enabled": true,
                "settings": $settingsJson
              }
            ]
            """.trimIndent()
        )
        val config = ModuleConfig(configPath.toFile())
        for (module in modules) config.modules[module.name.lowercase()] = module

        config.load()
    }

    private class LegacyNamedModule(name: String) : Module(
        name = name,
        category = Category.PLAYER,
        description = "Test module for legacy config alias.",
        toggled = false
    ) {
        val featureEnabled by BooleanSetting("Enabled", false, desc = "Test setting.")
    }

    private class LegacyCameraModule : Module(
        name = "Camera",
        category = Category.CAMERA,
        description = "Test module for legacy camera setting aliases.",
        toggled = false
    ) {
        val speed by NumberSetting("Speed", 1.0f, 0.1f, 10.0f, 0.1f, desc = "Test speed.")
        val distance by NumberSetting("Distance", 4.0f, 1.0f, 20.0f, 0.5f, desc = "Test distance.")
        val disableFrontCam by BooleanSetting("Disable Front Cam", false, desc = "Test front.")
        val disableBackCam by BooleanSetting("Disable Back Cam", false, desc = "Test back.")
        val scrollingChangesDistance by BooleanSetting("Scrolling Changes Distance", false, desc = "Test scroll.")
        val resetF5Scrolling by BooleanSetting("Reset F5 Scrolling", false, desc = "Test reset.")
        val cameraDistance by NumberSetting("Camera Distance", 4.0f, 1.0f, 20.0f, 0.5f, desc = "Test camera distance.")
    }

    private class LegacyCustomSkinModule : Module(
        name = "Custom Skin",
        category = Category.COSMETIC,
        description = "Test module for legacy skin setting aliases.",
        toggled = false
    ) {
        val featureEnabled by BooleanSetting("Enabled", false, desc = "Test setting.")
        val skin by gg.floyd.clickgui.settings.impl.StringSetting("Skin", "", 96, desc = "Test skin.")
    }

    private class LegacyCustomCapeModule : Module(
        name = "Custom Cape",
        category = Category.COSMETIC,
        description = "Test module for legacy cape setting aliases.",
        toggled = false
    ) {
        val featureEnabled by BooleanSetting("Enabled", false, desc = "Test setting.")
        val image by gg.floyd.clickgui.settings.impl.StringSetting("Image", "", 96, desc = "Test image.")
    }

    private class LegacyAnimationsModule : Module(
        name = "Animations",
        category = Category.RENDER,
        description = "Test module for legacy animation setting aliases.",
        toggled = false
    ) {
        val posX by NumberSetting("Pos X", 0, -150, 150, 1, desc = "Test pos.")
        val posY by NumberSetting("Pos Y", 0, -150, 150, 1, desc = "Test pos.")
        val posZ by NumberSetting("Pos Z", 0, -150, 50, 1, desc = "Test pos.")
        val rotX by NumberSetting("Rot X", 0, -180, 180, 1, desc = "Test rot.")
        val rotY by NumberSetting("Rot Y", 0, -180, 180, 1, desc = "Test rot.")
        val rotZ by NumberSetting("Rot Z", 0, -180, 180, 1, desc = "Test rot.")
        val hideHand by BooleanSetting("Hide Hand", false, desc = "Test hide.")
    }

    private class LegacyGeneralModule : Module(
        name = "General",
        category = Category.RENDER,
        description = "Test module for legacy render setting aliases.",
        toggled = false
    ) {
        val timeChanger by BooleanSetting("Time Changer", false, desc = "Test time.")
        val time by NumberSetting("Time", 50.0f, 0.0f, 100.0f, 1.0f, desc = "Test time value.")
    }

    private class LegacyWindowModule : Module(
        name = "Window",
        category = Category.MISC,
        description = "Test module for migrated window styling settings.",
        toggled = false
    ) {
        val borderlessWindow by BooleanSetting("Borderless Window", false, desc = "Test borderless.")
        val instanceTitle by gg.floyd.clickgui.settings.impl.StringSetting("Instance Title", "", 64, desc = "Test title.")
    }

    private class LegacyMobEspModule : Module(
        name = "Mob ESP",
        category = Category.RENDER,
        description = "Test module for legacy mob ESP setting aliases.",
        toggled = false
    ) {
        val defaultEspColor by ColorSetting("Default ESP Color", Color(0xFFFFFFFF.toInt()), desc = "Test color.")
        val tracerColor by ColorSetting("Tracer Color", Color(0xFFFFFFFF.toInt()), desc = "Test color.")
    }

    private class LegacyNeckHiderModule : Module(
        name = "Neck Hider",
        category = Category.PLAYER,
        description = "Test module for legacy hider setting aliases.",
        toggled = false
    ) {
        val defaultNick by gg.floyd.clickgui.settings.impl.StringSetting("Default Nick", "", 64, desc = "Test nick.")
    }

    private class LegacyHidersModule : Module(
        name = "Hiders",
        category = Category.HIDERS,
        description = "Test module for legacy hiders setting aliases.",
        toggled = false
    ) {
        val thirdPersonCrosshair by BooleanSetting("3rd Person Crosshair", false, desc = "Test crosshair.")
        val noExplosionParticles by BooleanSetting("No Explosion Particles", false, desc = "Test particles.")
        val serverIdHider by BooleanSetting("Server ID Hider", false, desc = "Test server ID.")
        val profileIdHider by BooleanSetting("Profile ID Hider", true, desc = "Test profile ID.")
        val target by SelectorSetting("Target", "Off", listOf("Off", "Self", "Others", "All"), desc = "Test target.")
    }

    private class LegacyPlayerSizeModule : Module(
        name = "Player Size",
        category = Category.PLAYER,
        description = "Test module for legacy player-size setting aliases.",
        toggled = false
    ) {
        val x by NumberSetting("X", 1.0f, -1.0f, 5.0f, 0.05f, desc = "Test x.")
        val y by NumberSetting("Y", 1.0f, -1.0f, 5.0f, 0.05f, desc = "Test y.")
        val z by NumberSetting("Z", 1.0f, -1.0f, 5.0f, 0.05f, desc = "Test z.")
        val target by SelectorSetting("Target", "Self", listOf("Self", "Real Players", "All"), desc = "Test target.")
    }

    private class LegacyConeHatModule : Module(
        name = "Cone Hat",
        category = Category.COSMETIC,
        description = "Test module for legacy cone setting aliases.",
        toggled = false
    ) {
        val image by gg.floyd.clickgui.settings.impl.StringSetting("Image", "", 96, desc = "Test image.")
        val spinSpeed by NumberSetting("Spin Speed", 0.0f, 0.0f, 360.0f, 1.0f, desc = "Test spin.")
    }

    private class MovedInventoryModule : Module(
        name = "Inventory HUD",
        category = Category.RENDER,
        description = "Test module for migrated inventory settings.",
        toggled = false
    ) {
        val scale by NumberSetting("Inventory HUD Scale", 1.1f, 0.5f, 5.0f, 0.05f, desc = "Test scale.")
    }

    /** Mirrors the unified [gg.floyd.features.impl.render.FloydPanelStyle] settings under test. */
    private class MovedPanelStyleModule : Module(
        name = "Panel Style",
        category = Category.RENDER,
        description = "Test module for migrated unified panel cosmetics.",
        toggled = true
    ) {
        val cornerRadius by NumberSetting("Panel Corner Radius", 4, 0, 20, 1, desc = "Test corner radius.")
        val borderColor by ColorSetting("Panel Border Color", Color(0xFFFFFFFF.toInt()), desc = "Test border color.")
        val padding by NumberSetting("Panel Padding", 6, 0, 16, 1, desc = "Test padding.")
    }
}
