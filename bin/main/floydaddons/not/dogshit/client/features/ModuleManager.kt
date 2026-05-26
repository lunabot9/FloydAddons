@file:Suppress("unused")

package floydaddons.not.dogshit.client.features

import floydaddons.not.dogshit.client.FloydAddonsMod
import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.clickgui.HudManager
import floydaddons.not.dogshit.client.clickgui.settings.impl.HUDSetting
import floydaddons.not.dogshit.client.clickgui.settings.impl.KeybindSetting
import floydaddons.not.dogshit.client.config.FloydSidecarConfig
import floydaddons.not.dogshit.client.config.ModuleConfig
import floydaddons.not.dogshit.client.events.InputEvent
import floydaddons.not.dogshit.client.events.core.on
import floydaddons.not.dogshit.client.keybind.KeybindSync
import floydaddons.not.dogshit.client.features.ModuleManager.configs
import floydaddons.not.dogshit.client.features.impl.camera.FloydCamera
import floydaddons.not.dogshit.client.features.impl.cosmetic.FloydCape
import floydaddons.not.dogshit.client.features.impl.cosmetic.FloydConeHat
import floydaddons.not.dogshit.client.features.impl.hiders.FloydHiders
import floydaddons.not.dogshit.client.features.impl.misc.FloydCompatibility
import floydaddons.not.dogshit.client.features.impl.misc.FloydDiscordPresence
import floydaddons.not.dogshit.client.features.impl.misc.FloydLocalControl
import floydaddons.not.dogshit.client.features.impl.player.FloydNickHider
import floydaddons.not.dogshit.client.features.impl.player.FloydPlayerSize
import floydaddons.not.dogshit.client.features.impl.cosmetic.FloydSkin
import floydaddons.not.dogshit.client.features.impl.render.FloydHud
import floydaddons.not.dogshit.client.features.impl.render.FloydMobEsp
import floydaddons.not.dogshit.client.features.impl.render.ClickGUIModule
import floydaddons.not.dogshit.client.features.impl.render.FloydAnimations
import floydaddons.not.dogshit.client.features.impl.render.FloydHubMap
import floydaddons.not.dogshit.client.features.impl.render.FloydRender
import floydaddons.not.dogshit.client.features.impl.render.FloydXray
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.Identifier
import net.minecraft.resources.Identifier.fromNamespaceAndPath
import java.io.File
import java.util.Locale

/**
 * # Module Manager
 *
 * This object stores all [Modules][Module] and provides functionality to [HUDs][Module.HUD]
 */
object ModuleManager {

    /**
     * Map containing all modules in Odin,
     * where the key is the modules name in lowercase.
     */
    val modules: HashMap<String, Module> = linkedMapOf()

    /**
     * Map containing all modules under their category.
     */
    val modulesByCategory: HashMap<Category, ArrayList<Module>> = hashMapOf()

    /**
     * List of all configurations handled by Odin.
     */
    val configs: ArrayList<ModuleConfig> = arrayListOf()

    val keybindSettingsCache: ArrayList<KeybindSetting> = arrayListOf()
    val hudSettingsCache: ArrayList<HUDSetting> = arrayListOf()

    private val HUD_LAYER: Identifier = fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "floydaddons_hud")

    init {
        registerModules(config = ModuleConfig(file = File(FloydAddonsMod.configFile, "floydaddons-config.json")),
            ClickGUIModule,

            // FloydAddons feature groups. The vendored Floyd source under
            // vendor/floydaddons-fabric is the source-of-truth for behavior.
            FloydRender, FloydXray, FloydAnimations, FloydHud, FloydMobEsp, FloydHubMap,
            FloydHiders,
            FloydNickHider, FloydPlayerSize,
            FloydCamera,
            FloydSkin, FloydCape, FloydConeHat,
            FloydDiscordPresence, FloydLocalControl, FloydCompatibility,
        )

        // hashmap, but would need to keep track when setting values change
        on<InputEvent> {
            for (setting in keybindSettingsCache) {
                if (setting.value.value == key.value) setting.onPress?.invoke()
            }
        }

        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, HUD_LAYER, ModuleManager::render)
    }

    /**
     * Registers modules to the [ModuleManager] and initializes them.
     *
     * @param config the config the [Module] is saved to,
     * it is recommended that each unique mod that uses this has its own config
     */
    fun registerModules(config: ModuleConfig, vararg modules: Module) {
        for (module in modules) {
            val lowercase = module.name.lowercase()
            config.modules[lowercase] = module
            this.modules[lowercase] = module
            this.modulesByCategory.getOrPut(module.category) { arrayListOf() }.add(module)

            module.key?.let { keybind ->
                val setting = KeybindSetting("Keybind", keybind, "Toggles this module.")
                setting.onPress = module::onKeybind
                module.registerSetting(setting)
            }

            for ((_, setting) in module.settings) {
                when (setting) {
                    is KeybindSetting -> {
                        keybindSettingsCache.add(setting)
                        KeybindSync.register(setting, keybindTranslationKey(module, setting))
                    }
                    is HUDSetting -> hudSettingsCache.add(setting)
                }
            }
        }
        configs.add(config)
        config.load()
        FloydSidecarConfig.loadExistingSidecars()
        FloydDiscordPresence.startIfEnabled()
        FloydLocalControl.startIfEnabled()
        config.save()
        FloydSidecarConfig.saveSidecars()
    }

    private fun keybindTranslationKey(module: Module, setting: KeybindSetting): String = when {
        module === ClickGUIModule && setting.name == "Keybind" -> "key.floydaddons.click_gui"
        setting.name == "Open GUI Key" -> "key.floydaddons.open_gui"
        setting.name == "Toggle X-Ray" -> "key.floydaddons.toggle_xray"
        setting.name == "Toggle Freecam" -> "key.floydaddons.toggle_freecam"
        setting.name == "Toggle Freelook" -> "key.floydaddons.toggle_freelook"
        setting.name == "Toggle Mob ESP" -> "key.floydaddons.toggle_mob_esp"
        else -> {
            val slug = module.name.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
            "key.floydaddons.module.$slug"
        }
    }

    /**
     * Loads all [configs] from disk, into the respective modules.
     */
    fun loadConfigurations() {
        for (config in configs) {
            config.load()
        }
        FloydSidecarConfig.loadExistingSidecars()
    }

    /**
     * Saves all [configs] to disk, from the respective modules.
     */
    fun saveConfigurations() {
        for (config in configs) {
            config.save()
        }
        FloydSidecarConfig.saveSidecars()
    }

    fun state(): Map<String, Any?> = mapOf(
        "moduleCount" to modules.size,
        "categoryCount" to Category.categories.size,
        "configCount" to configs.size,
        "configs" to configs.map { it.state() },
        "categories" to Category.categories.values.map { category ->
            val categoryModules = modulesByCategory[category].orEmpty()
            mapOf(
                "name" to category.name,
                "moduleCount" to categoryModules.size,
                "modules" to categoryModules.map { module ->
                    mapOf(
                        "name" to module.name,
                        "enabled" to module.enabled,
                        "settingCount" to module.settings.size
                    )
                }
            )
        }
    )

    fun render(guiGraphics: GuiGraphics, tickCounter: DeltaTracker) {
        if (mc.level == null || mc.player == null || mc.screen == HudManager || mc.options.hideGui) return

        guiGraphics.pose().pushMatrix()
        val sf = mc.window.guiScale
        guiGraphics.pose().scale(1f / sf, 1f / sf)
        for (hudSettings in hudSettingsCache) {
            if (hudSettings.isEnabled) hudSettings.value.draw(guiGraphics, false)
        }
        guiGraphics.pose().popMatrix()
    }
}
