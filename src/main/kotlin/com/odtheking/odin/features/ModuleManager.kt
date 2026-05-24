@file:Suppress("unused")

package com.odtheking.odin.features

import com.odtheking.odin.FloydAddonsMod
import com.odtheking.odin.FloydAddonsMod.mc
import com.odtheking.odin.clickgui.HudManager
import com.odtheking.odin.clickgui.settings.impl.HUDSetting
import com.odtheking.odin.clickgui.settings.impl.KeybindSetting
import com.odtheking.odin.config.FloydSidecarConfig
import com.odtheking.odin.config.ModuleConfig
import com.odtheking.odin.events.InputEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.ModuleManager.configs
import com.odtheking.odin.features.impl.camera.FloydCamera
import com.odtheking.odin.features.impl.cosmetic.FloydCape
import com.odtheking.odin.features.impl.cosmetic.FloydConeHat
import com.odtheking.odin.features.impl.hiders.FloydHiders
import com.odtheking.odin.features.impl.misc.FloydCompatibility
import com.odtheking.odin.features.impl.misc.FloydDiscordPresence
import com.odtheking.odin.features.impl.misc.FloydLocalControl
import com.odtheking.odin.features.impl.player.FloydNickHider
import com.odtheking.odin.features.impl.player.FloydPlayerSize
import com.odtheking.odin.features.impl.cosmetic.FloydSkin
import com.odtheking.odin.features.impl.render.FloydHud
import com.odtheking.odin.features.impl.render.FloydMobEsp
import com.odtheking.odin.features.impl.render.ClickGUIModule
import com.odtheking.odin.features.impl.render.FloydAnimations
import com.odtheking.odin.features.impl.render.FloydRender
import com.odtheking.odin.features.impl.render.FloydXray
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.Identifier
import net.minecraft.resources.Identifier.fromNamespaceAndPath
import java.io.File

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
            FloydRender, FloydXray, FloydAnimations, FloydHud, FloydMobEsp,
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
                    is KeybindSetting -> keybindSettingsCache.add(setting)
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
