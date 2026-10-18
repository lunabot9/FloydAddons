package gg.floyd.clickgui

import com.mojang.blaze3d.platform.InputConstants
import gg.floyd.Branding
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.Setting
import gg.floyd.clickgui.settings.impl.*
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.LegacyClickGUIModule
import gg.floyd.features.impl.render.FloydUiTheme
import gg.floyd.utils.Color
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.oneconfig.internal.ui.components.settings.bumpResetEpoch

/** Floyd-owned settings screen using shared controls over the same live settings as the panel GUI. */
object FloydOneConfig {
    private const val ID = "floydaddons"
    private var registered = false
    private var optionCount = 0
    private val refreshOptions = mutableListOf<() -> Unit>()
    private val screen by lazy { FloydSettingsScreen() }
    private var configTree: Tree? = null
    private var themeTree: Tree? = null
    private val categoryTrees = mutableMapOf<String, Tree>()
    private var suppressedOpeningSounds = 0
    private var suppressedClosingSounds = 0
    private var suppressedCompatOverlays = 0L
    private var settingsScrollEvents = 0L
    private var preparingSettings = false
    private var settingsWarmupVisits = 0
    private val actionButtons = linkedMapOf<String, String>()
    private val inlineFileSelectors = linkedMapOf<String, StringSetting>()

    @JvmStatic
    fun recordSettingsScroll() { settingsScrollEvents++ }

    @JvmStatic
    fun recordSuppressedCompatOverlay() { suppressedCompatOverlays++ }

    fun isFloydSettingsScreen(): Boolean = mc.screen is FloydSettingsScreen

    private fun refreshVisibility() {
        ownProperties().forEach { it.revaluateDisplay() }
    }

    /** An action can update several underlying settings without calling their UI properties. */
    fun refreshSettings() {
        ownProperties().forEach {
            it.revaluateDisplay()
            bumpResetEpoch(it)
        }
    }
    private fun ownProperties(): List<Property<*>> = listOfNotNull(configTree, themeTree)
        .flatMap { it.map.values.filterIsInstance<Property<*>>() }
    fun themeSettingsTree(): Tree = checkNotNull(themeTree)

    @JvmStatic
    fun recordSuppressedOpeningSound() { suppressedOpeningSounds++ }

    @JvmStatic
    fun recordSuppressedClosingSound() { suppressedClosingSounds++ }

    fun isFloydMenuRequested(): Boolean = isFloydSettingsScreen()

    fun searchTree(query: String): Tree {
        val matches = checkNotNull(configTree).map.filterValues { node ->
            FloydSettingsSearch.matches(query, node.title?.toString(), node.description?.toString(),
                node.getMetadata<String>("subcategory"), node.getMetadata<String>("category"))
        }
        return Tree("$ID.search", Branding.DISPLAY_NAME, null, matches)
    }

    @JvmStatic
    fun beginSettingsWarmup() { preparingSettings = registered }
    @JvmStatic
    fun endSettingsWarmup() { preparingSettings = false }
    @JvmStatic
    fun shouldWarmSettings(): Boolean = preparingSettings
    @JvmStatic
    fun recordSettingsWarmup() {
        settingsWarmupVisits++
        FloydSettingsNavigation.warmupScrollIndex = ((settingsWarmupVisits - 1) % 3) * 8
    }

    fun isFloydPage(): Boolean = isFloydSettingsScreen() || preparingSettings

    fun categoryTree(category: String): Tree = categoryTrees.getOrPut(category) {
        val root = checkNotNull(configTree)
        Tree("$ID.${slug(category)}", Branding.DISPLAY_NAME, null,
            root.map.filterValues { it.getMetadata<String>("category") == category })
    }

    fun initialize() {
        // Older builds exposed this tree through OneConfig. Remove only that legacy entry.
        org.polyfrost.oneconfig.api.config.v1.CompatSnapshots.untrack(ID)
        org.polyfrost.oneconfig.api.config.v1.ConfigManager.active().unregister(ID)
        register()
        org.polyfrost.oneconfig.internal.ui.themes.ThemeRegistry.init()
        screen.queueWarmup()
    }

    fun open(openingKey: Int? = null) { mc.setScreen(createScreen(openingKey)) }

    fun createScreen(openingKey: Int? = null): Screen {
        register()
        screen.prepareOpen(openingKey)
        return screen
    }

    fun state(): Map<String, Any?> = mapOf(
        "registered" to registered,
        "optionCount" to optionCount,
        "primary" to "floyd",
        "isolatedScreen" to true,
        "oneConfigConfigIds" to org.polyfrost.oneconfig.api.config.v1.ConfigManager.active().trees().map { it.getID() },
        "registeredWithOneConfig" to org.polyfrost.oneconfig.api.config.v1.ConfigManager.active().trees().any { it.getID() == ID },
        "floydScreenActive" to isFloydSettingsScreen(),
        "openingProgress" to screen.openingProgress,
        "openingAnimationFrames" to screen.openingAnimationFrames,
        "openingAnimationMs" to 250,
        "closingRequested" to screen.closingRequested,
        "closingAnimationMs" to 160,
        "upstreamSearchQuery" to org.polyfrost.oneconfig.internal.ui.shell.ShellState.searchQuery,
        "upstreamLastRoute" to org.polyfrost.oneconfig.internal.ui.shell.ShellState.lastRoute?.toString(),
        "upstreamTheme" to org.polyfrost.oneconfig.internal.ThemeConfig.activeTheme,
        "upstreamAccent" to "%08X".format(org.polyfrost.oneconfig.internal.ThemeConfig.accentColor.rawArgb),
        "secondary" to "clickgui",
        "primaryKey" to (LegacyClickGUIModule.settings["Keybind"] as? KeybindSetting)?.value?.name,
        "secondaryKey" to (gg.floyd.features.impl.render.ClickGUIModule.settings["Open GUI Key"] as? KeybindSetting)?.value?.name,
        "upstreamMenuKeys" to OneConfigConfig.oneConfigKeybind.keyCodes?.toList(),
        "screen" to mc.screen?.javaClass?.name,
        "theme" to FloydOneConfigTheme.state(),
        "sidebarCategories" to FloydSettingsNavigation.categories,
        "selectedCategory" to FloydSettingsNavigation.selectedCategory,
        "themeEditorOpen" to FloydSettingsNavigation.themeEditorOpen,
        "themeEditorLocation" to "sidebar-bottom-left",
        "themeListedAsFeature" to (configTree?.map?.values?.any { it.getMetadata<String>("subcategory") == FloydUiTheme.name } == true),
        "themeEditorHasEnabledControl" to (themeTree?.map?.values?.any { it.title?.toString() == "Enabled" } == true),
        "themeEditorHasKeybindControl" to (themeTree?.map?.values?.any { it.title?.toString() == "Keybind" } == true),
        "highlightOffsetDp" to FloydSettingsNavigation.highlightOffsetDp,
        "highlightAnimationFrames" to FloydSettingsNavigation.highlightAnimationFrames,
        "highlightTargetDp" to FloydSettingsNavigation.highlightTargetDp,
        "themeButtonOffsetDp" to FloydSettingsNavigation.themeButtonOffsetDp,
        "presetHighlightOffsetDp" to FloydSettingsNavigation.presetHighlightOffsetDp,
        "presetHighlightAnimationFrames" to FloydSettingsNavigation.presetHighlightAnimationFrames,
        "selectionAnimationMs" to 220,
        "ambientMusicEnabled" to false,
        "upstreamAmbientMusicEnabled" to OneConfigConfig.enableUIAmbience,
        "ambientMusicPlaying" to ambientMusicPlaying(),
        "upstreamAmbienceRequested" to upstreamAmbienceRequested(),
        "openingSoundEnabled" to false,
        "suppressedOpeningSounds" to suppressedOpeningSounds,
        "closingSoundEnabled" to false,
        "suppressedClosingSounds" to suppressedClosingSounds,
        "searchQuery" to FloydSettingsNavigation.searchQuery,
        "searchResultCount" to searchTree(FloydSettingsNavigation.searchQuery).map.size,
        "hudPreviewEnabled" to org.polyfrost.oneconfig.api.hud.v1.HudManager.isConfigUiOpen,
        "hudForcedInScreens" to org.polyfrost.oneconfig.api.hud.v1.HudManager.overrideShowInScreens,
        "odinLoaded" to net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("odin"),
        "compatHudOverlaysSuppressed" to isFloydSettingsScreen(),
        "suppressedCompatOverlayFrames" to suppressedCompatOverlays,
        "scrollSpeedMultiplier" to 0.25f,
        "settingsScrollEvents" to settingsScrollEvents,
        "scrollPrefetchAheadDp" to 1200,
        "settingsWarmupVisits" to settingsWarmupVisits,
        "firstVisibleRow" to FloydSettingsNavigation.firstVisibleRow,
        "scrollOffset" to FloydSettingsNavigation.scrollOffset,
        "actionButtonLabels" to actionButtons.toMap(),
        "inlineFileSelectors" to inlineFileSelectors.mapValues { it.value.value },
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
            val previous = module.settings.values.filterIsInstance<ActionSetting>().firstOrNull { it.name.startsWith("Previous ") }
            val next = module.settings.values.filterIsInstance<ActionSetting>().firstOrNull { it.name.startsWith("Next ") }
            val file = module.settings.values.filterIsInstance<StringSetting>().firstOrNull { it.name in listOf("Image", "Skin", "Font") }
            val inlineCycler = file != null && previous != null && next != null
            module.settings.values.forEach { setting ->
                if (inlineCycler && (setting === previous || setting === next)) return@forEach
                val prop = adapt("${prefix}_${slug(setting.name)}", setting) ?: return@forEach
                if (inlineCycler && setting === file) {
                    prop.addMetadata("visualizer", FloydFileSelectorVisualizer(previous!!, next!!))
                    inlineFileSelectors[module.name] = file!!
                }
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
        // Keep this adapter private to Floyd's screen. Do not register a OneConfig mod/config.
        configTree = tree
        themeTree = Tree("$ID.theme-editor", "Theme", null, null).also { editor ->
            FloydUiTheme.settings.values.filter { it.name != "Keybind" && it.name != "Preset" }.forEach { setting ->
                val prop = adapt("ui_theme_${slug(setting.name)}", setting) ?: return@forEach
                prop.addMetadata("category", "Theme")
                prop.addMetadata("subcategory", "Custom Colors")
                prop.addDisplayCondition { if (setting.isVisible) Property.Display.SHOWN else Property.Display.HIDDEN }
                editor.put(prop)
                if (setting is ColorSetting) addColorAnimation(editor, "ui_theme", FloydUiTheme, setting)
            }
            editor.map.values.forEach { node ->
                node.addMetadata("category", "Theme")
                node.addMetadata("subcategory", "Custom Colors")
            }
        }
        optionCount = tree.map.size
        registered = true
    }

    private fun upstreamAmbienceRequested(): Boolean? = runCatching {
        org.polyfrost.oneconfig.internal.ui.sound.UiSounds::class.java
            .getDeclaredField("ambienceActive").apply { isAccessible = true }.getBoolean(null)
    }.getOrNull()

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
        Properties.functional({ getter() }, { setter(it); refreshVisibility() }, id, title, description).apply {
            addMetadata("visualizer", visualizer)
            addMetadata("default", default)
        }

    private fun actionProperty(id: String, title: String, description: String, action: () -> Unit): Property<Runnable> =
        Properties.simple(id, title, description, Runnable { action(); refreshSettings() }).apply {
            addMetadata("visualizer", Visualizer.ButtonVisualizer::class.java)
            val verb = title.substringBefore(' ')
            val label = when (verb) {
                "Open", "Reload", "Next", "Previous", "List", "Reset", "Clear", "Pick", "Toggle", "Stalk", "Customize" -> verb
                "Sign" -> if (title.startsWith("Sign in")) "Sign in" else "Sign out"
                "Check" -> "Check"
                "Test" -> "Test"
                "Preview" -> "Preview"
                "Install" -> "Install"
                "Edit" -> "Edit"
                "Click" -> "Open" // Secondary Click GUI screen.
                else -> "Run"
            }
            addMetadata("text", label)
            actionButtons[title] = label
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
