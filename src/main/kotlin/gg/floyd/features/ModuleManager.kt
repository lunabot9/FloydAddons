@file:Suppress("unused")

package gg.floyd.features

import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudManager
import gg.floyd.clickgui.settings.impl.HUDSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.config.FloydSidecarConfig
import gg.floyd.config.ModuleConfig
import gg.floyd.events.InputEvent
import gg.floyd.events.core.on
import gg.floyd.features.ModuleManager.configs
import gg.floyd.features.impl.camera.FloydCamera
import gg.floyd.features.impl.camera.FloydFreecam
import gg.floyd.features.impl.camera.FloydFreelook
import gg.floyd.features.impl.camera.FloydF5Customizer
import gg.floyd.features.impl.cosmetic.FloydCape
import gg.floyd.features.impl.cosmetic.FloydConeHat
import gg.floyd.features.impl.hiders.FloydNoHurtCamera
import gg.floyd.features.impl.hiders.FloydRemoveFireOverlay
import gg.floyd.features.impl.hiders.FloydDisableHungerBar
import gg.floyd.features.impl.hiders.FloydHidePotionEffects
import gg.floyd.features.impl.hiders.FloydThirdPersonCrosshair
import gg.floyd.features.impl.hiders.FloydHideEntityFire
import gg.floyd.features.impl.hiders.FloydDisableArrows
import gg.floyd.features.impl.hiders.FloydRemoveFallingBlocks
import gg.floyd.features.impl.hiders.FloydRemoveExplosionParticles
import gg.floyd.features.impl.hiders.FloydRemoveTabPing
import gg.floyd.features.impl.hiders.FloydServerIdHider
import gg.floyd.features.impl.hiders.FloydProfileIdHider
import gg.floyd.features.impl.hiders.FloydHideWatchdogMessages
import gg.floyd.features.impl.hiders.FloydModHider
import gg.floyd.features.impl.hiders.FloydNoArmor
import gg.floyd.features.impl.misc.FloydSpoofClientBrand
import gg.floyd.features.impl.misc.FloydCustomMainMenu
import gg.floyd.features.impl.misc.FloydTaskbarIconModule
import gg.floyd.features.impl.misc.FloydUpdateCheckerModule
import gg.floyd.features.impl.misc.FloydDiscordPresence
import gg.floyd.features.impl.misc.FloydLocalControl
import gg.floyd.features.impl.player.FloydNickHider
import gg.floyd.features.impl.player.FloydPlayerSize
import gg.floyd.features.impl.pvp.FloydAutoTotem
import gg.floyd.features.impl.pvp.FloydPlayerEsp
import gg.floyd.features.impl.cosmetic.FloydSkin
import gg.floyd.features.impl.render.FloydBlockSearch
import gg.floyd.features.impl.render.FloydCustomScoreboard
import gg.floyd.features.impl.render.FloydHubMap
import gg.floyd.features.impl.render.FloydTimeChanger
import gg.floyd.features.impl.render.FloydHud
import gg.floyd.features.impl.render.FloydInventoryHud
import gg.floyd.features.impl.render.FloydMobEsp
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.features.impl.render.LegacyClickGUIModule
import gg.floyd.features.impl.render.FloydAnimations
import gg.floyd.features.impl.render.FloydRender
import gg.floyd.features.impl.render.FloydXray
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
     * Map containing all modules in Floyd,
     * where the key is the modules name in lowercase.
     */
    val modules: HashMap<String, Module> = linkedMapOf()

    /**
     * Map containing all modules under their category.
     */
    val modulesByCategory: HashMap<Category, ArrayList<Module>> = hashMapOf()

    /**
     * List of all configurations handled by Floyd.
     */
    val configs: ArrayList<ModuleConfig> = arrayListOf()

    val keybindSettingsCache: ArrayList<KeybindSetting> = arrayListOf()
    val hudSettingsCache: ArrayList<HUDSetting> = arrayListOf()

    private val HUD_LAYER: Identifier = fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "floydaddons_hud")

    init {
        registerModules(config = ModuleConfig(file = File(FloydAddonsMod.configFile, "floydaddons-config.json")),
            ClickGUIModule, LegacyClickGUIModule,

            // FloydAddons feature groups.
            FloydRender, FloydXray, FloydAnimations, FloydHud, FloydInventoryHud, FloydCustomScoreboard, FloydTimeChanger, FloydHubMap, FloydMobEsp, FloydBlockSearch,
            // Hiders (each feature is its own module).
            FloydNoHurtCamera, FloydRemoveFireOverlay, FloydDisableHungerBar, FloydHidePotionEffects, FloydThirdPersonCrosshair,
            FloydHideEntityFire, FloydDisableArrows, FloydRemoveFallingBlocks, FloydRemoveExplosionParticles, FloydRemoveTabPing,
            FloydServerIdHider, FloydProfileIdHider, FloydHideWatchdogMessages, FloydModHider, FloydNoArmor,
            FloydNickHider, FloydPlayerSize,
            // Camera (each feature is its own module).
            FloydFreecam, FloydFreelook, FloydF5Customizer,
            FloydSkin, FloydCape, FloydConeHat,
            FloydAutoTotem, FloydPlayerEsp,
            FloydDiscordPresence, FloydLocalControl,
            // Misc compatibility (each feature is its own module).
            FloydSpoofClientBrand, FloydCustomMainMenu, FloydTaskbarIconModule, FloydUpdateCheckerModule,
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
        // Freecam/Freelook are transient: never persist as active across restarts.
        FloydCamera.resetTransientModes()
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
        FloydCamera.resetTransientModes()
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

        // World-anchored overhead ESP overlay, drawn in plain gui-scaled space.
        gg.floyd.features.impl.pvp.FloydPlayerEsp.drawOverheadOverlay(guiGraphics)

        guiGraphics.pose().pushMatrix()
        val sf = mc.window.guiScale
        guiGraphics.pose().scale(1f / sf, 1f / sf)
        for (hudSettings in hudSettingsCache) {
            if (hudSettings.isEnabled) hudSettings.value.draw(guiGraphics, false)
        }
        guiGraphics.pose().popMatrix()
    }
}
