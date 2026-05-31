@file:Suppress("unused")

package gg.floyd.config

import com.google.gson.*
import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.logger
import gg.floyd.clickgui.settings.Saving
import gg.floyd.features.Module
import org.apache.logging.log4j.LogManager
import java.io.File

/**
 * # ModuleConfig
 *
 * This class handles saving Modules, and their settings, into a JSON format.
 */
class ModuleConfig internal constructor(file: File) {

    /**
     * Main constructor for Addons. (config/floyd/addons/{fileName})
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
                // Remap keys that moved between modules in the HUD/GUI reorg before reading them.
                // Persisted immediately so the on-disk config uses the new keys on subsequent loads.
                if (migrateMovedKeys(jsonArray)) {
                    file.bufferedWriter().use { it.write(gson.toJson(jsonArray)) }
                }
                for (modules in jsonArray) {
                    val moduleObj = modules?.asJsonObject ?: continue
                    val moduleName = moduleObj.get("name").asString
                    val canonicalModule = canonicalModuleName(moduleName)
                    val module = this@ModuleConfig.modules[canonicalModule] ?: continue
                    val enabledElement = moduleObj.get("enabled")
                    if (enabledElement != null && enabledElement.asBoolean != module.enabled) module.toggle()
                    val settingObj = moduleObj.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet() ?: continue
                    for ((key, value) in settingObj) {
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

        // Resolved directly (not via FloydAddonsMod.logger) so migration logging does not force
        // Minecraft client initialization, keeping the load/migrate path unit-testable.
        private val migrationLogger = LogManager.getLogger("FloydAddons")

        private val legacyModuleNames = mapOf(
            // Display-name renames: the module's lowercase config key changed, so old keys remap forward.
            // "Neck Hider" is the intentional canonical name; the brief "Nick Hider" build remaps back.
            "nick hider" to "neck hider",
            "render" to "general",
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
            "general" to mapOf(
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
            "general" to mapOf(
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

        /**
         * Destination of a relocated setting key: the canonical (lowercased) name of the module that
         * now owns it, and the setting key it is serialized under there ([toKey], same as the source
         * key unless the setting was also renamed).
         */
        private data class MovedKey(val toModule: String, val toKey: String)

        /**
         * Setting-name keys (as serialized under their owning module) that were relocated to a
         * different module during the HUD/GUI reorg, paired with the module that now owns them and
         * the key they are stored under there.
         *
         * The old "HUD" module owned the scoreboard appearance, the inventory HUD, and the shared
         * HUD corner radius; those settings now live on their dedicated modules. The corner radius
         * was also folded into the shared "Panel Corner Radius" on the General module (the "HUD
         * Corner Radius" setting was deleted), so it both moves module and changes key.
         */
        private val movedKeysByModule: Map<String, Map<String, MovedKey>> = mapOf(
            "hud" to mapOf(
                // scoreboard* -> Custom Scoreboard
                "Scoreboard Color" to MovedKey("custom scoreboard", "Scoreboard Color"),
                "Scoreboard Fade" to MovedKey("custom scoreboard", "Scoreboard Fade"),
                "Scoreboard Fade Color" to MovedKey("custom scoreboard", "Scoreboard Fade Color"),
                "Padding" to MovedKey("custom scoreboard", "Padding"),
                "Scoreboard HUD" to MovedKey("custom scoreboard", "Scoreboard HUD"),
                // inventory* -> Inventory HUD
                "Inventory HUD Scale" to MovedKey("inventory hud", "Inventory HUD Scale"),
                "Inventory HUD" to MovedKey("inventory hud", "Inventory HUD"),
                // hudCornerRadius -> General "Panel Corner Radius" (setting deleted, value folded in)
                "HUD Corner Radius" to MovedKey("general", "Panel Corner Radius")
            ),
            // Intermediate configs that already moved the corner radius onto the (now renamed) Render
            // module still hold the deleted "HUD Corner Radius" key; fold it into General's shared radius.
            "render" to mapOf(
                "HUD Corner Radius" to MovedKey("general", "Panel Corner Radius")
            )
            // LEGACY-CLICKGUI-SPLIT HOOK: once LegacyClickGUIModule lands (UX cleanup item 4), add a
            // "clickgui" entry here remapping the legacy styling keys (Button Text*, Button Border*,
            // GUI Border*, Chat notifications) to "legacy click gui". Absent that module the keys
            // stay on ClickGUIModule and must NOT be moved, so leave this commented until it exists.
        )

        // Tracks which (source module -> setting) remaps have already been logged so each is noted once.
        private val loggedMovedKeys: MutableSet<String> = hashSetOf()

        /**
         * Moves any persisted [movedKeysByModule] entries from their old owning module's settings
         * object into the settings object of the module that now owns them (under the new key,
         * creating the destination module entry if the config predates it). A move is skipped when
         * the destination already has an explicit value for the key, so a deliberately-set value is
         * never clobbered. Returns true if anything was remapped.
         */
        private fun migrateMovedKeys(jsonArray: JsonArray): Boolean {
            var changed = false
            val moduleObjects: MutableMap<String, JsonObject> = jsonArray.asSequence()
                .mapNotNull { it as? JsonObject }
                .filter { it.get("name")?.asString != null }
                .associateByTo(hashMapOf()) { it.get("name").asString.lowercase() }

            for ((sourceModule, moves) in movedKeysByModule) {
                val sourceObj = moduleObjects[sourceModule] ?: continue
                val sourceSettings = sourceObj.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue

                for ((settingKey, target) in moves) {
                    if (!sourceSettings.has(settingKey)) continue
                    val value = sourceSettings.get(settingKey)
                    sourceSettings.remove(settingKey)
                    changed = true

                    val targetSettings = settingsObjectFor(jsonArray, moduleObjects, target.toModule)
                    if (!targetSettings.has(target.toKey)) targetSettings.add(target.toKey, value)

                    val logKey = "$sourceModule:$settingKey"
                    if (loggedMovedKeys.add(logKey)) {
                        migrationLogger.info(
                            "Migrated config key '{}' from '{}' to '{}' as '{}'.",
                            settingKey, sourceModule, target.toModule, target.toKey
                        )
                    }
                }
            }
            return changed
        }

        /**
         * Returns the settings [JsonObject] for [targetModule], creating and appending a module
         * entry (and its empty settings object) to [jsonArray] if the config has none yet.
         */
        private fun settingsObjectFor(
            jsonArray: JsonArray,
            moduleObjects: MutableMap<String, JsonObject>,
            targetModule: String
        ): JsonObject {
            moduleObjects[targetModule]?.let { obj ->
                obj.get("settings")?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
                return JsonObject().also { obj.add("settings", it) }
            }
            // Config predates the destination module: append a fresh entry. The post-load save()
            // re-normalizes the "name" casing from the in-memory module, and load() lowercases names.
            val moduleObj = JsonObject().apply {
                addProperty("name", targetModule)
            }
            val settings = JsonObject()
            moduleObj.add("settings", settings)
            jsonArray.add(moduleObj)
            moduleObjects[targetModule] = moduleObj
            return settings
        }
    }

    override fun toString(): String {
        return "ModuleConfig(file=$file)"
    }
}
