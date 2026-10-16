package gg.floyd.clickgui

import com.mojang.blaze3d.platform.InputConstants
import gg.floyd.Branding
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.Setting
import gg.floyd.clickgui.settings.impl.*
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.LegacyClickGUIModule
import gg.floyd.utils.Color
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.config.v1.CompatSnapshots
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute

/** The upstream OneConfig screen edits the same live settings as the panel GUI. */
object FloydOneConfig {
    private const val ID = "floydaddons"
    private var registered = false
    private var optionCount = 0
    private val refreshOptions = mutableListOf<() -> Unit>()
    private var floydScreenRequested = false
    private var configTree: Tree? = null
    private val categoryTrees = mutableMapOf<String, Tree>()
    private var suppressedOpeningSounds = 0
    private var suppressedClosingSounds = 0
    private var suppressedCompatOverlays = 0L

    @JvmStatic
    fun recordSuppressedCompatOverlay() { suppressedCompatOverlays++ }

    // Navigation resolves after the first frame. Use the opening request here so
    // editing examples cannot leak into the background snapshot before that happens.
    fun isFloydSettingsScreen(): Boolean = floydScreenRequested &&
        mc.screen?.javaClass?.name == "org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen"

    @JvmStatic
    fun recordSuppressedOpeningSound() { suppressedOpeningSounds++ }

    @JvmStatic
    fun recordSuppressedClosingSound() { suppressedClosingSounds++ }

    fun isFloydMenuRequested(): Boolean = floydScreenRequested

    fun searchTree(query: String): Tree {
        val matches = checkNotNull(configTree).map.filterValues { node ->
            FloydSettingsSearch.matches(query, node.title?.toString(), node.description?.toString(),
                node.getMetadata<String>("subcategory"), node.getMetadata<String>("category"))
        }
        return Tree("$ID.search", Branding.DISPLAY_NAME, null, matches)
    }

    fun isFloydPage(): Boolean = floydScreenRequested && (
        org.polyfrost.oneconfig.internal.ui.shell.ShellState.awaitingInitialRoute ||
            (org.polyfrost.oneconfig.internal.ui.shell.LocalNavController.wrapper.currentRoute as? ModConfigRoute)?.id == ID
        )

    fun categoryTree(category: String): Tree = categoryTrees.getOrPut(category) {
        val root = checkNotNull(configTree)
        Tree("$ID.${slug(category)}", Branding.DISPLAY_NAME, null,
            root.map.filterValues { it.getMetadata<String>("category") == category })
    }

    fun initialize() {
        disableAmbientMusic()
        FloydOneConfigTheme.apply()
        // Reserve Right Shift for the panel GUI. Leave any other upstream binding alone.
        val nativeBind = OneConfigConfig.oneConfigKeybind
        if (nativeBind.keyCodes?.contentEquals(intArrayOf(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)) == true) {
            nativeBind.keyCodes = null
            KeybindManager.refreshMinecraftBinding(nativeBind)
            OneConfigConfig.INSTANCE.save()
        }
        register()
    }

    fun open() {
        disableAmbientMusic()
        floydScreenRequested = true
        FloydOneConfigTheme.apply()
        register()
        OneConfigUI.open(ModConfigRoute(ID, "Render"))
    }

    fun createScreen(): Screen {
        disableAmbientMusic()
        floydScreenRequested = true
        FloydOneConfigTheme.apply()
        register()
        return OneConfigUI.createScreen(ModConfigRoute(ID, "Render")) as? Screen
            ?: error("Could not create the settings screen")
    }

    fun state(): Map<String, Any?> = mapOf(
        "registered" to registered,
        "optionCount" to optionCount,
        "primary" to "oneconfig",
        "secondary" to "clickgui",
        "primaryKey" to (LegacyClickGUIModule.settings["Keybind"] as? KeybindSetting)?.value?.name,
        "secondaryKey" to (gg.floyd.features.impl.render.ClickGUIModule.settings["Open GUI Key"] as? KeybindSetting)?.value?.name,
        "upstreamMenuKeys" to OneConfigConfig.oneConfigKeybind.keyCodes?.toList(),
        "screen" to mc.screen?.javaClass?.name,
        "theme" to FloydOneConfigTheme.state(),
        "sidebarCategories" to FloydSettingsNavigation.categories,
        "selectedCategory" to FloydSettingsNavigation.selectedCategory,
        "highlightOffsetDp" to FloydSettingsNavigation.highlightOffsetDp,
        "highlightAnimationFrames" to FloydSettingsNavigation.highlightAnimationFrames,
        "ambientMusicEnabled" to OneConfigConfig.enableUIAmbience,
        "ambientMusicPlaying" to ambientMusicPlaying(),
        "openingSoundEnabled" to false,
        "suppressedOpeningSounds" to suppressedOpeningSounds,
        "closingSoundEnabled" to false,
        "suppressedClosingSounds" to suppressedClosingSounds,
        "searchQuery" to org.polyfrost.oneconfig.internal.ui.shell.ShellState.searchQuery,
        "searchResultCount" to searchTree(org.polyfrost.oneconfig.internal.ui.shell.ShellState.searchQuery).map.size,
        "hudPreviewEnabled" to org.polyfrost.oneconfig.api.hud.v1.HudManager.isConfigUiOpen,
        "hudForcedInScreens" to org.polyfrost.oneconfig.api.hud.v1.HudManager.overrideShowInScreens,
        "odinLoaded" to net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("odin"),
        "compatHudOverlaysSuppressed" to isFloydSettingsScreen(),
        "suppressedCompatOverlayFrames" to suppressedCompatOverlays,
    )

    private fun register() {
        if (registered) {
            refreshOptions.forEach { it() }
            return
        }
        val tree = Tree(ID, Branding.DISPLAY_NAME, "Configure modules, keybinds, and HUD elements.", null)
        tree.addMetadata("mod_id", ID)
        tree.addMetadata("icon", "assets/floydaddons/icons/taskbar_icon_128x128.png")
        tree.addMetadata("category", Config.Category.QOL)
        tree.addMetadata("custom_save", Runnable { ModuleManager.saveConfigurations() })
        tree.addMetadata("version", Branding.VERSION)
        button(tree, "edit_hud", "Edit HUD", "Move and resize your HUD elements.") { mc.setScreen(HudManager) }
        button(tree, "click_gui", "Click GUI", "Open the secondary panel menu (Right Shift).") { mc.setScreen(ClickGUI) }
        (LegacyClickGUIModule.settings["Keybind"] as? KeybindSetting)?.let { setting ->
            tree.put(adapt("open_oneconfig", setting, "Open Floyd Addons")!!.apply {
                addMetadata("category", "Render")
                addMetadata("subcategory", "Menus")
            })
        }
        ModuleManager.modules.values.forEach { module ->
            if (!module.visibleInGui || module === LegacyClickGUIModule) return@forEach
            val prefix = slug(module.name)
            val toggle = property("${prefix}_enabled", "Enabled", module.description, module.enabled,
                { module.enabled }, { if (it != module.enabled) module.toggle() }, Visualizer.SwitchVisualizer::class.java)
            group(toggle, module)
            tree.put(toggle)
            module.settings.values.forEach { setting ->
                val prop = adapt("${prefix}_${slug(setting.name)}", setting) ?: return@forEach
                group(prop, module)
                prop.addDisplayCondition { if (setting.isVisible) Property.Display.SHOWN else Property.Display.HIDDEN }
                tree.put(prop)
                if (setting is ColorSetting) addColorAnimation(tree, prefix, module, setting)
                if (setting is ExtendedSearchableListSetting) addListActions(tree, prefix, module, setting)
                if (setting is HUDSetting) {
                    button(tree, "${prefix}_${slug(setting.name)}_layout", "Edit ${setting.name} layout", setting.description,
                        module.category.name, module.name) { mc.setScreen(HudManager) }
                }
            }
        }
        // OneConfig's compat bridge provides profiles/resets while Floyd retains its own storage.
        CompatSnapshots.register(tree)
        configTree = tree
        optionCount = tree.map.size
        registered = true
    }

    private fun disableAmbientMusic() {
        val changed = OneConfigConfig.enableUIAmbience || OneConfigConfig.showFirstLaunchMessage
        OneConfigConfig.enableUIAmbience = false
        OneConfigConfig.showFirstLaunchMessage = false
        org.polyfrost.oneconfig.internal.ui.sound.UiSounds.refreshAmbience()
        if (changed) OneConfigConfig.INSTANCE.save()
    }

    private fun ambientMusicPlaying(): Boolean? = runCatching {
        val serviceField = org.polyfrost.oneconfig.internal.ui.sound.UiSounds::class.java
            .getDeclaredField("service\$delegate").apply { isAccessible = true }
        val service = (serviceField.get(null) as Lazy<*>).value ?: return@runCatching false
        val loop = service.javaClass.getDeclaredField("ambience").apply { isAccessible = true }.get(service)
            as? net.minecraft.client.resources.sounds.SoundInstance ?: return@runCatching false
        mc.soundManager.isActive(loop)
    }.getOrNull()

    private fun adapt(id: String, setting: Setting<*>, title: String = setting.name): Property<*>? = when (setting) {
        is BooleanSetting -> property(id, title, setting.description, setting.default,
            { setting.value }, { setting.value = it }, Visualizer.SwitchVisualizer::class.java)
        is DropdownSetting -> property(id, title, setting.description, setting.default,
            { setting.value }, { setting.value = it }, Visualizer.SwitchVisualizer::class.java)
        is NumberSetting<*> -> property<Number>(id, title, setting.description, setting.default,
            { setting.value }, { setting.setFromNumber(it) }, Visualizer.SliderVisualizer::class.java).apply {
                addMetadata("min", setting.minimum.toFloat())
                addMetadata("max", setting.maximum.toFloat())
                addMetadata("step", setting.step.toFloat())
            }
        is SelectorSetting -> property(id, title, setting.description, setting.default,
            { setting.value }, { setting.value = it }, Visualizer.DropdownVisualizer::class.java).apply {
                addMetadata("options", setting.optionLabels().toTypedArray())
            }
        is StringSetting -> property(id, title, setting.description, setting.default,
            { setting.value }, { setting.value = it }, Visualizer.TextVisualizer::class.java)
        is ColorSetting -> property(id, title, setting.description, setting.default.baseRgba,
            { setting.value.baseRgba }, { rgba ->
                val current = setting.value
                setting.value = Color(rgba).also { it.chroma = current.chroma; it.fade = current.fade; it.fadeColor = current.fadeColor.copy() }
            }, Visualizer.ColorVisualizer::class.java)
        is KeybindSetting -> property(id, title, setting.description, keybind(setting.default),
            { keybind(setting.value) }, { bind ->
                setting.value = when {
                    bind.mouseBtns?.isNotEmpty() == true -> InputConstants.Type.MOUSE.getOrCreate(bind.mouseBtns!!.first())
                    bind.keyCodes?.isNotEmpty() == true -> InputConstants.Type.KEYSYM.getOrCreate(bind.keyCodes!!.first())
                    else -> InputConstants.UNKNOWN
                }
            }, Visualizer.KeybindVisualizer::class.java)
            .apply { addMetadata("oc_no_mc_mirror", true) }
        is ActionSetting -> actionProperty(id, title, setting.description) { setting.action() }
        is HUDSetting -> property(id, title, setting.description, setting.default.enabled,
            { setting.value.enabled }, { setting.value.enabled = it }, Visualizer.SwitchVisualizer::class.java)
        is SearchableListSetting -> property(id, title, setting.description, emptyArray<String>(),
            { setting.selectedOptions().toTypedArray() }, { setting.replaceSelection(it.toSet()) },
            Visualizer.MultiSelectDropdownVisualizer::class.java).apply {
                addMetadata("options", setting.availableOptions().toTypedArray())
                refreshOptions += { addMetadata("options", setting.availableOptions().toTypedArray()) }
            }
        else -> null // Storage-only maps/lists have their own editable view settings.
    }

    private fun addColorAnimation(tree: Tree, prefix: String, module: Module, setting: ColorSetting) {
        val id = "${prefix}_${slug(setting.name)}"
        val chroma = property("${id}_chroma", "${setting.name} chroma", "Cycle this color through the rainbow.", setting.default.chroma,
            { setting.value.chroma }, { setting.value.chroma = it; if (it) setting.value.fade = false }, Visualizer.SwitchVisualizer::class.java)
        val fade = property("${id}_fade", "${setting.name} fade", "Fade between this color and its secondary color.", setting.default.fade,
            { setting.value.fade }, { setting.value.fade = it; if (it) setting.value.chroma = false }, Visualizer.SwitchVisualizer::class.java)
        val fadeColor = property("${id}_fade_color", "${setting.name} fade color", "Secondary fade color.", setting.default.fadeColor.baseRgba,
            { setting.value.fadeColor.baseRgba }, { setting.value.fadeColor = Color(it) }, Visualizer.ColorVisualizer::class.java)
        listOf(chroma, fade, fadeColor).forEach { prop ->
            group(prop, module)
            prop.addDisplayCondition { if (setting.isVisible) Property.Display.SHOWN else Property.Display.HIDDEN }
            tree.put(prop)
        }
    }

    private fun addListActions(tree: Tree, prefix: String, module: Module, setting: ExtendedSearchableListSetting) {
        val id = "${prefix}_${slug(setting.name)}"
        if (setting.canPickFromWorld) button(tree, "${id}_pick", "Pick from World", "Add the target under your crosshair.",
            module.category.name, module.name) { setting.pickFromWorld() }
        if (setting.canClearAll) button(tree, "${id}_clear", "Clear ${setting.name}", "Clear this selection.",
            module.category.name, module.name) { setting.clearAll() }
        if (!setting.supportsColors) return
        val targets = setting.availableOptions()
        if (targets.isEmpty()) return
        var targetIndex = 0
        val target = property("${id}_color_target", "Filter color target", "Choose the filter whose highlight color you want to edit.", 0,
            { targetIndex }, { targetIndex = it.coerceIn(targets.indices) }, Visualizer.DropdownVisualizer::class.java).apply {
                addMetadata("options", targets.toTypedArray())
            }
        val color = property("${id}_target_color", "Filter color", "Highlight color for the chosen filter.", 0xFFFFFFFF.toInt(),
            { (setting.optionColor(targets[targetIndex])?.removePrefix("#")?.toLongOrNull(16)?.toInt() ?: 0xFFFFFF) or 0xFF000000.toInt() },
            { setting.setOptionColor(targets[targetIndex], "%06X".format(it and 0xFFFFFF)) }, Visualizer.ColorVisualizer::class.java)
        listOf(target, color).forEach { prop ->
            group(prop, module)
            prop.addDisplayCondition { if (setting.isVisible) Property.Display.SHOWN else Property.Display.HIDDEN }
            tree.put(prop)
        }
    }

    private fun keybind(key: InputConstants.Key): OneConfigKeybind = OneConfigKeybind(
        if (key.type == InputConstants.Type.KEYSYM && key.value >= 0) intArrayOf(key.value) else null,
        if (key.type == InputConstants.Type.MOUSE) intArrayOf(key.value) else null,
        0, 0L, { false },
    )

    private fun <T : Any> property(id: String, title: String, description: String, default: T,
        getter: () -> T, setter: (T) -> Unit, visualizer: Class<out Visualizer>): Property<T> =
        Properties.functional({ getter() }, { setter(it) }, id, title, description).apply {
            addMetadata("visualizer", visualizer)
            addMetadata("default", default)
        }

    private fun actionProperty(id: String, title: String, description: String, action: () -> Unit): Property<Runnable> =
        Properties.simple(id, title, description, Runnable(action)).apply {
            addMetadata("visualizer", Visualizer.ButtonVisualizer::class.java)
            addMetadata("text", "Open")
        }

    private fun button(tree: Tree, id: String, title: String, description: String,
        category: String = "Render", subcategory: String = "Menus", action: () -> Unit) {
        tree.put(actionProperty(id, title, description, action).apply {
            addMetadata("category", category)
            addMetadata("subcategory", subcategory)
        })
    }

    private fun group(prop: Property<*>, module: Module) {
        prop.addMetadata("category", module.category.name)
        prop.addMetadata("subcategory", module.name)
    }

    private fun slug(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
