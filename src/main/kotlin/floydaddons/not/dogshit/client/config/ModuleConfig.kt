@file:Suppress("unused")

package floydaddons.not.dogshit.client.config

import com.google.gson.*
import floydaddons.not.dogshit.client.FloydAddonsMod
import floydaddons.not.dogshit.client.FloydAddonsMod.logger
import floydaddons.not.dogshit.client.clickgui.settings.Saving
import floydaddons.not.dogshit.client.features.Module
import floydaddons.not.dogshit.client.features.impl.render.ClickGUIModule
import floydaddons.not.dogshit.client.features.impl.render.FloydCustomScoreboard
import floydaddons.not.dogshit.client.features.impl.render.FloydTimeChanger
import floydaddons.not.dogshit.client.features.impl.hiders.FloydDisableArrows
import floydaddons.not.dogshit.client.features.impl.hiders.FloydDisableHungerBar
import floydaddons.not.dogshit.client.features.impl.hiders.FloydHideEntityFire
import floydaddons.not.dogshit.client.features.impl.hiders.FloydHidePotionEffects
import floydaddons.not.dogshit.client.features.impl.hiders.FloydHideWatchdogMessages
import floydaddons.not.dogshit.client.features.impl.hiders.FloydModHider
import floydaddons.not.dogshit.client.features.impl.hiders.FloydNoArmor
import floydaddons.not.dogshit.client.features.impl.hiders.FloydNoHurtCamera
import floydaddons.not.dogshit.client.features.impl.hiders.FloydProfileIdHider
import floydaddons.not.dogshit.client.features.impl.hiders.FloydRemoveExplosionParticles
import floydaddons.not.dogshit.client.features.impl.hiders.FloydRemoveFallingBlocks
import floydaddons.not.dogshit.client.features.impl.hiders.FloydRemoveFireOverlay
import floydaddons.not.dogshit.client.features.impl.hiders.FloydRemoveTabPing
import floydaddons.not.dogshit.client.features.impl.hiders.FloydServerIdHider
import floydaddons.not.dogshit.client.features.impl.hiders.FloydThirdPersonCrosshair
import floydaddons.not.dogshit.client.features.impl.misc.FloydCustomMainMenu
import floydaddons.not.dogshit.client.features.impl.misc.FloydSpoofClientBrand
import floydaddons.not.dogshit.client.features.impl.misc.FloydTaskbarIconModule
import floydaddons.not.dogshit.client.features.impl.misc.FloydUpdateCheckerModule
import java.io.File

/**
 * # ModuleConfig
 *
 * This class handles saving Modules, and their settings, into a JSON format.
 */
class ModuleConfig internal constructor(file: File) {

    /**
     * Main constructor for Addons. (config/odin/addons/{fileName})
     */
    constructor(fileName: String) : this(File(FloydAddonsMod.configFile, "addons/$fileName"))

    // key is module name in lowercase
    internal val modules: HashMap<String, Module> = hashMapOf()

    private val file: File = file.apply {
        try {
            parentFile.mkdirs()
            createNewFile()
        } catch (e: Exception) {
            logger.error("Error initializing module config", e)
        }
    }

    /**
     * Loads configuration from file, into [modules].
     */
    fun load() {
        try {
            with(file.bufferedReader().use { it.readText() }) {
                if (isEmpty()) return

                val jsonArray = JsonParser.parseString(this).asJsonArray ?: return
                for (modules in jsonArray) {
                    val moduleObj = modules?.asJsonObject ?: continue
                    val moduleName = moduleObj.get("name").asString
                    val canonicalModule = canonicalModuleName(moduleName)
                    if (canonicalModule == "floyd compatibility") {
                        val compatEnabled = moduleObj.get("enabled")?.asBoolean ?: true
                        val settingObj = moduleObj.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet().orEmpty()
                        for ((key, value) in settingObj) {
                            when (key.lowercase()) {
                                "spoof client brand" -> if (value.asBoolean != FloydSpoofClientBrand.enabled) FloydSpoofClientBrand.toggle()
                                "custom main menu" -> if (value.asBoolean != FloydCustomMainMenu.enabled) FloydCustomMainMenu.toggle()
                                "taskbar icon" -> if (value.asBoolean != FloydTaskbarIconModule.enabled) FloydTaskbarIconModule.toggle()
                                "update checker" -> if (value.asBoolean != FloydUpdateCheckerModule.enabled) FloydUpdateCheckerModule.toggle()
                                "hide watchdog message", "hide watchdog messages" -> if (value.asBoolean != FloydHideWatchdogMessages.enabled) FloydHideWatchdogMessages.toggle()
                                "hide loader entry" -> if (value.asBoolean != FloydModHider.enabled) FloydModHider.toggle()
                            }
                        }
                        if (!compatEnabled) {
                            if (FloydSpoofClientBrand.enabled) FloydSpoofClientBrand.toggle()
                            if (FloydCustomMainMenu.enabled) FloydCustomMainMenu.toggle()
                            if (FloydTaskbarIconModule.enabled) FloydTaskbarIconModule.toggle()
                            if (FloydUpdateCheckerModule.enabled) FloydUpdateCheckerModule.toggle()
                            if (FloydHideWatchdogMessages.enabled) FloydHideWatchdogMessages.toggle()
                            if (FloydModHider.enabled) FloydModHider.toggle()
                        }
                        continue
                    }
                    val module = this@ModuleConfig.modules[canonicalModule] ?: continue
                    if (moduleObj.get("enabled").asBoolean != module.enabled) module.toggle()
                    val settingObj = moduleObj.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet() ?: continue
                    for ((key, value) in settingObj) {
                        if (canonicalModule == "hiders") {
                            when (key.lowercase()) {
                                "no hurt camera" -> if (value.asBoolean != FloydNoHurtCamera.enabled) FloydNoHurtCamera.toggle()
                                "remove fire overlay" -> if (value.asBoolean != FloydRemoveFireOverlay.enabled) FloydRemoveFireOverlay.toggle()
                                "disable hunger bar" -> if (value.asBoolean != FloydDisableHungerBar.enabled) FloydDisableHungerBar.toggle()
                                "hide potion effects" -> if (value.asBoolean != FloydHidePotionEffects.enabled) FloydHidePotionEffects.toggle()
                                "3rd person crosshair" -> if (value.asBoolean != FloydThirdPersonCrosshair.enabled) FloydThirdPersonCrosshair.toggle()
                                "hide entity fire" -> if (value.asBoolean != FloydHideEntityFire.enabled) FloydHideEntityFire.toggle()
                                "disable arrows" -> if (value.asBoolean != FloydDisableArrows.enabled) FloydDisableArrows.toggle()
                                "remove falling blocks" -> if (value.asBoolean != FloydRemoveFallingBlocks.enabled) FloydRemoveFallingBlocks.toggle()
                                "no explosion particles" -> if (value.asBoolean != FloydRemoveExplosionParticles.enabled) FloydRemoveExplosionParticles.toggle()
                                "remove tab ping" -> if (value.asBoolean != FloydRemoveTabPing.enabled) FloydRemoveTabPing.toggle()
                                "server id hider" -> if (value.asBoolean != FloydServerIdHider.enabled) FloydServerIdHider.toggle()
                                "profile id hider" -> if (value.asBoolean != FloydProfileIdHider.enabled) FloydProfileIdHider.toggle()
                                "target" -> {
                                    val target = value.asString
                                    (FloydNoArmor.settings["Target"] as? Saving)?.read(value, gson)
                                    if (!target.equals("Off", ignoreCase = true) && !FloydNoArmor.enabled) FloydNoArmor.toggle()
                                }
                            }
                            continue
                        }
                        if (canonicalModule == "render") {
                            when (key.lowercase()) {
                                "custom time" -> {
                                    if (value.asBoolean != FloydTimeChanger.enabled) FloydTimeChanger.toggle()
                                    continue
                                }
                                "custom time value" -> {
                                    (this@ModuleConfig.modules["time changer"]?.settings?.get("Time") as? Saving)?.apply { read(value, gson) }
                                    continue
                                }
                                "custom scoreboard" -> {
                                    if (value.asBoolean != FloydCustomScoreboard.enabled) FloydCustomScoreboard.toggle()
                                    continue
                                }
                                "window title" -> {
                                    (ClickGUIModule.settings["Instance Title"] as? Saving)?.apply { read(value, gson) }
                                    continue
                                }
                            }
                        }
                        val settingModuleName = canonicalSettingModuleName(canonicalModule, key)
                        val settingModule = this@ModuleConfig.modules[settingModuleName] ?: continue
                        val settingKey = canonicalSettingName(settingModuleName, key)
                        (settingModule.settings[settingKey] as? Saving)?.apply { read(value ?: continue, gson) }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error initializing module config", e)
        }
    }

    /**
     * Saves configuration to files, from [modules].
     */
    fun save() {
        try {
            // reason doing this is better is that
            // using like a custom serializer leaves 'null' in settings that don't save
            // code looks hideous tho, but it fully works
            val jsonArray = JsonArray().apply {
                for ((_, module) in modules) {
                    add(JsonObject().apply {
                        add("name", JsonPrimitive(module.name))
                        add("enabled", JsonPrimitive(module.enabled))
                        add("settings", JsonObject().apply {
                            for ((name, setting) in module.settings) {
                                if (setting is Saving) add(name, setting.write(gson))
                            }
                        })
                    })
                }
            }
            file.bufferedWriter().use { it.write(gson.toJson(jsonArray)) }
        } catch (e: Exception) {
            logger.error("Error saving module config.", e)
        }
    }

    fun state(): Map<String, Any?> = mapOf(
        "file" to file.path,
        "exists" to file.exists(),
        "sizeBytes" to file.length(),
        "moduleCount" to modules.size
    )

    private companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        private val legacyModuleNames = mapOf(
            "nick hider" to "neck hider",
            "skin" to "custom skin",
            "cape" to "custom cape"
        )

        private val legacySettingNames = mapOf(
            "camera" to mapOf(
                "freecam speed" to "Speed",
                "freelook distance" to "Distance",
                "disable front camera" to "Disable Front Cam",
                "disable back camera" to "Disable Back Cam",
                "scroll f5 distance" to "Scrolling Changes Distance",
                "reset f5 distance" to "Reset F5 Scrolling",
                "f5 distance" to "Camera Distance"
            ),
            "animations" to mapOf(
                "position x" to "Pos X",
                "position y" to "Pos Y",
                "position z" to "Pos Z",
                "rotation x" to "Rot X",
                "rotation y" to "Rot Y",
                "rotation z" to "Rot Z",
                "hide empty hand" to "Hide Hand"
            ),
            "render" to mapOf(
                "custom time" to "Time Changer",
                "custom time value" to "Time",
                "borderless windowed" to "Borderless Window",
                "window title" to "Instance Title"
            ),
            "mob esp" to mapOf(
                "default color" to "Default ESP Color",
                "stalk color" to "Tracer Color",
                "stalk tracer color" to "Tracer Color"
            ),
            "hiders" to mapOf(
                "third person crosshair" to "3rd Person Crosshair",
                "remove explosion particles" to "No Explosion Particles",
                "no armor mode" to "Target"
            ),
            "neck hider" to mapOf(
                "nickname" to "Default Nick"
            ),
            "custom skin" to mapOf(
                "selected skin" to "Skin"
            ),
            "custom cape" to mapOf(
                "selected cape" to "Image"
            ),
            "player size" to mapOf(
                "scale x" to "X",
                "scale y" to "Y",
                "scale z" to "Z",
                "size target" to "Target"
            ),
            "cone hat" to mapOf(
                "selected image" to "Image",
                "rotation speed" to "Spin Speed"
            )
        )

        private val legacySettingModuleNames = mapOf(
            "neck hider" to mapOf(
                "server id hider" to "hiders",
                "profile id hider" to "hiders"
            ),
            "render" to mapOf(
                "server id hider" to "hiders",
                "profile id hider" to "hiders"
            )
        )

        private fun canonicalModuleName(name: String): String =
            legacyModuleNames[name.lowercase()] ?: name.lowercase()

        private fun canonicalSettingModuleName(moduleName: String, settingName: String): String =
            legacySettingModuleNames[moduleName]?.get(settingName.lowercase()) ?: moduleName

        private fun canonicalSettingName(moduleName: String, settingName: String): String =
            legacySettingNames[moduleName]?.get(settingName.lowercase()) ?: settingName
    }

    override fun toString(): String {
        return "ModuleConfig(file=$file)"
    }
}
