package com.odtheking.odin.clickgui

import com.odtheking.odin.FloydAddonsMod
import com.odtheking.odin.FloydAddonsMod.mc
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.odtheking.odin.clickgui.settings.Setting
import com.odtheking.odin.clickgui.settings.impl.ActionSetting
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.HUDSetting
import com.odtheking.odin.clickgui.settings.impl.KeybindSetting
import com.odtheking.odin.clickgui.settings.impl.MapSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.clickgui.settings.impl.RuntimeBooleanSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.clickgui.settings.impl.StringSetting
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.Module
import com.odtheking.odin.features.ModuleManager
import com.odtheking.odin.features.impl.camera.FloydCamera
import com.odtheking.odin.features.impl.cosmetic.FloydCape
import com.odtheking.odin.features.impl.cosmetic.FloydConeHat
import com.odtheking.odin.features.impl.cosmetic.FloydSkin
import com.odtheking.odin.features.impl.hiders.FloydHiders
import com.odtheking.odin.features.impl.misc.FloydCompatibility
import com.odtheking.odin.features.impl.misc.FloydDiscordPresence
import com.odtheking.odin.features.impl.misc.FloydLocalControl
import com.odtheking.odin.features.impl.player.FloydNickHider
import com.odtheking.odin.features.impl.player.FloydPlayerSize
import com.odtheking.odin.features.impl.render.ClickGUIModule
import com.odtheking.odin.features.impl.render.FloydAnimations
import com.odtheking.odin.features.impl.render.FloydHud
import com.odtheking.odin.features.impl.render.FloydMobEsp
import com.odtheking.odin.features.impl.render.FloydRender
import com.odtheking.odin.features.impl.render.FloydXray
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.modMessage
import com.odtheking.odin.utils.ui.activeMouseOverride
import com.odtheking.odin.utils.ui.clearMouseOverride
import com.odtheking.odin.utils.ui.hasTransientMouseOverride
import com.odtheking.odin.utils.ui.mouseX as odinMouseX
import com.odtheking.odin.utils.ui.mouseY as odinMouseY
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SpawnEggItem
import java.awt.Color.HSBtoRGB
import java.awt.Color.RGBtoHSB
import java.awt.Desktop
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.lwjgl.glfw.GLFW

object LegacyFloydClickGUI : Screen(Component.literal("FloydAddons")) {
    private const val hubPanelWidth = 480
    private const val hubPanelHeight = 270
    private const val hubBackgroundTextureSize = 1024
    private const val borderThickness = 1
    private const val fadeDurationMs = 120L
    private const val dragBarHeight = 22
    private const val buttonHeight = 18
    private const val rowHeight = 18
    private const val rowGap = 5
    private const val styleButtonWidth = 64
    private const val clickGuiButtonWidth = 74
    private const val v2ButtonWidth = 74
    private const val chromaSegmentsPerEdge = 16
    private const val colorPickerWidth = 360
    private const val colorPickerHeight = 288
    private const val colorPickerSvSize = 128
    private const val colorPickerHueWidth = 14
    private const val colorPickerHueHeight = 128
    private const val colorPickerPreviewSize = 28
    private const val colorPickerPreviewGap = 6
    private const val guiStylePanelWidth = 260
    private const val guiStylePanelHeight = 140
    private const val guiStyleLabelWidth = 108
    private const val guiStylePreviewWidth = 26
    private const val guiStylePickWidth = 36
    private const val guiStyleRowHeight = 18
    private const val guiStyleRowSpacing = 24
    private const val moduleBrowserHeaderHeight = 24
    private const val moduleBrowserRowHeight = 20
    private const val moduleBrowserSearchWidth = 200
    private const val moduleBrowserSearchHeight = 22
    private const val moduleBrowserPanelMinWidth = 100
    private const val moduleBrowserPanelGap = 6
    private const val moduleBrowserPanelTop = 30
    private const val moduleBrowserMaxPanelContentHeight = 240
    private const val modulePopupTitleHeight = 20
    private const val modulePopupRowHeight = 18
    private const val modulePopupSliderRowHeight = 28
    private const val modulePopupColorExpandedHeight = 160
    private const val modulePopupColorSvSize = 100
    private const val modulePopupColorHueWidth = 10
    private const val modulePopupFilterMaxVisibleHeight = 180
    private const val modulePopupFilterEntryHeight = 16
    private const val modulePopupPadding = 6
    private const val modulePopupMinWidth = 180
    private const val modulePopupFilterMinWidth = 250
    private const val skinDropdownRowHeight = 16
    private const val skinDropdownMaxVisible = 5
    private const val skinPanelWidth = 320
    private const val skinPanelHeight = 260
    private const val skinControlWidth = 220
    private const val skinRowHeight = 20
    private const val skinRowSpacing = 26
    private const val coneControlsWidth = 310
    private const val conePreviewWidth = 140
    private const val coneFullControlWidth = 220
    private const val coneSliderWidth = 166
    private const val coneInputWidth = 50
    private const val coneInputGap = 4
    private const val coneDropdownWidth = 148
    private const val coneDropdownRowHeight = 16
    private const val coneDropdownMaxVisible = 5
    private const val mobFilterPanelWidth = 360
    private const val mobFilterPanelHeight = 320
    private const val mobFilterEntryHeight = 20
    private const val mobFilterButtonWidth = 18
    private const val mobFilterButtonHeight = 16
    private const val mobFilterColorSquareSize = 10
    private const val mobFilterInlinePickerHeight = 110
    private const val mobFilterInlineSvSize = 80
    private const val mobFilterInlineHueWidth = 8
    private const val mobFilterInlineHueHeight = 80
    private const val xrayEditorPanelWidth = 340
    private const val xrayEditorPanelHeight = 300
    private const val xrayEditorEntryHeight = 20
    private const val xrayEditorButtonWidth = 18
    private const val xrayEditorButtonHeight = 16
    private const val nameMappingPanelWidth = 360
    private const val nameMappingPanelHeight = 300
    private const val nameMappingRowHeight = 16
    private const val nameMappingRowSpacing = 18
    private const val nameMappingButtonSize = 14
    private const val animationsPanelWidth = 320
    private const val animationsPanelHeight = 400
    private const val animationsRowHeight = 20
    private const val animationsRowSpacing = 24
    private const val animationsFullWidth = 220
    private const val cameraPanelWidth = 320
    private const val cameraPanelHeight = 340
    private const val cameraRowHeight = 20
    private const val cameraRowSpacing = 26
    private const val cameraFullWidth = 220
    private const val cameraHalfWidth = 108
    private const val cameraPairGap = 4
    private const val renderPanelWidth = 320
    private const val renderPanelHeight = 420
    private const val renderRowHeight = 20
    private const val renderRowSpacing = 26
    private const val renderFullWidth = 220
    private const val renderMainWidth = 148
    private const val renderSecondaryWidth = 68
    private const val renderHalfWidth = 108
    private const val renderPairGap = 4
    private const val mobEspPanelWidth = 320
    private const val mobEspPanelHeight = 320
    private const val mobEspRowHeight = 20
    private const val mobEspRowSpacing = 26
    private const val mobEspFullWidth = 220
    private const val mobEspSecondaryWidth = 68
    private const val mobEspPairGap = 4
    private const val mobEspColorPreviewSize = 16
    private const val cosmeticPanelWidth = 240
    private const val cosmeticPanelHeight = 300
    private const val cosmeticControlWidth = 220
    private const val cosmeticMainWidth = 148
    private const val cosmeticSecondaryWidth = 68
    private const val cosmeticPairGap = 4
    private const val cosmeticRowHeight = 20
    private const val cosmeticRowSpacing = 26
    private const val hidersPanelWidth = 320
    private const val hidersPanelHeight = 340
    private const val hidersRowHeight = 20
    private const val hidersRowSpacing = 24
    private const val hidersFullWidth = 240
    private const val nickPanelWidth = 260
    private const val nickPanelHeight = 150
    private const val nickFullWidth = 220
    private const val githubUrl = "https://github.com/lunabot9/FloydAddons"
    private const val githubHeader = "Check out FloydAddons on GitHub!"
    private val legacyClickGuiPanelConfigPath: Path = FloydAddonsMod.configFile.toPath().resolve("clickgui-panels.json")
    private val legacyClickGuiPanelGson = GsonBuilder().setPrettyPrinting().create()
    private val legacyClickGuiPanelType = object : TypeToken<Map<String, List<Int>>>() {}.type
    private val nickPresets = listOf("George Floyd", "Floyd", "Dream", "Technoblade", "Steve", "Alex")

    private val background = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "textures/gui/floydbg.png")
    private val labels = listOf("Cosmetic", "Render", "Neck Hider", "Camera")
    private val labelHover = MutableList(labels.size) { 0f }

    private var openStartMs = 0L
    private var closeStartMs = 0L
    private var closing = false
    private var panelX = 0
    private var panelY = 0
    private var dragging = false
    private var dragStartMouseX = 0.0
    private var dragStartMouseY = 0.0
    private var dragStartPanelX = 0
    private var dragStartPanelY = 0
    private var styleButton = Rect.ZERO
    private var hudButton = Rect.ZERO
    private var clickGuiButton = Rect.ZERO
    private var v2Button = Rect.ZERO
    private var linkBounds = Rect.ZERO
    private var labelBounds = emptyList<Rect>()
    private var pageBackButton = Rect.ZERO
    private var pageDoneButton = Rect.ZERO
    private var pageRows = emptyList<HitRow>()
    private var pageScroll = 0
    private var editorSaveButton = Rect.ZERO
    private var editorCancelButton = Rect.ZERO
    private var hoverX = 0.0
    private var hoverY = 0.0
    private var eventMousePoint: Pair<Double, Double>? = null
    private var eventMouseUpdatedAt = 0L
    private var textEditor: TextEditor? = null
    private var colorPicker: ColorPickerEditor? = null
    private var skinDropdownOpen = false
    private var skinDropdownScroll = 0
    private var skinDropdownButton = Rect.ZERO
    private var skinDropdownList = Rect.ZERO
    private var skinSettingsHitEntries = emptyList<SkinSettingsHitEntry>()
    private var capePrevButton = Rect.ZERO
    private var capeNextButton = Rect.ZERO
    private var capeOpenFolderButton = Rect.ZERO
    private var coneRows = emptyList<ConeControlRow>()
    private var coneInputBoxes = emptyList<Rect>()
    private var coneDropdownButton = Rect.ZERO
    private var coneDropdownList = Rect.ZERO
    private var coneOpenFolderButton = Rect.ZERO
    private var coneDropdownOpen = false
    private var coneDropdownScroll = 0
    private var coneEditingIndex = -1
    private var coneEditBuffer = ""
    private var mobFilterHitEntries = emptyList<MobFilterHitEntry>()
    private var mobFilterScroll = 0
    private var expandedMobFilterColor: MobFilterColorTarget? = null
    private var mobFilterPickerHue = 0f
    private var mobFilterPickerSaturation = 1f
    private var mobFilterPickerBrightness = 1f
    private var mobFilterPickerDrag: MobFilterHitKind? = null
    private var xrayHitEntries = emptyList<XrayHitEntry>()
    private var xrayEditorScroll = 0
    private var nameMappingHitEntries = emptyList<NameMappingHitEntry>()
    private var nameMappingScroll = 0
    private var addingMappingName: String? = null
    private var addingMappingBuffer = ""
    private val revealedMappingNames = mutableSetOf<String>()
    private var animationHitEntries = emptyList<AnimationHitEntry>()
    private var activeAnimationSlider: AnimationSliderTarget? = null
    private var cameraHitEntries = emptyList<CameraHitEntry>()
    private var activeCameraSlider: CameraSliderTarget? = null
    private var renderHitEntries = emptyList<RenderHitEntry>()
    private var activeRenderSlider: RenderSliderTarget? = null
    private var renderTitleFocused = false
    private var mobEspHitEntries = emptyList<MobEspHitEntry>()
    private var cosmeticHitEntries = emptyList<CosmeticHitEntry>()
    private var activeCosmeticSlider: CosmeticSliderTarget? = null
    private var hidersHitEntries = emptyList<HidersHitEntry>()
    private var nickHiderHitEntries = emptyList<NickHiderHitEntry>()
    private var guiStyleHitEntries = emptyList<GuiStyleHitEntry>()
    private var moduleBrowserHitEntries = emptyList<ModuleBrowserHitEntry>()
    private var moduleBrowserHeaderHitEntries = emptyList<ModuleBrowserHeaderHitEntry>()
    private var moduleBrowserSearchBounds = Rect.ZERO
    private var moduleBrowserSearchFocused = false
    private var moduleBrowserSearchQuery = ""
    private val moduleBrowserPanelStates = mutableMapOf<Category, ModuleBrowserPanelState>()
    private var draggingModuleBrowserCategory: Category? = null
    private var moduleBrowserDragOffsetX = 0
    private var moduleBrowserDragOffsetY = 0
    private var modulePopup: ModulePopup? = null
    private var modulePopupHitEntries = emptyList<ModulePopupHitEntry>()
    private var modulePopupExtraHitEntries = emptyList<ModulePopupExtraHitEntry>()
    private var modulePopupPlayerHitEntries = emptyList<ModulePopupPlayerHitEntry>()
    private var modulePopupXrayHitEntries = emptyList<ModulePopupXrayHitEntry>()
    private var modulePopupMobFilterHitEntries = emptyList<MobFilterHitEntry>()
    private var modulePopupNameMappingHitEntries = emptyList<NameMappingHitEntry>()
    private var expandedModulePopupExtra: ModulePopupExtraKind? = null
    private var draggingModulePopup = false
    private var activeModulePopupSlider: ModulePopupSliderTarget? = null
    private var expandedModulePopupColor: ColorSetting? = null
    private var activeModulePopupColorDrag: ColorPickerDrag? = null
    private var modulePopupEditingFadeColor = false
    private var activeModulePopupString: ModulePopupStringTarget? = null
    private var modulePopupStringBuffer = ""
    private var activeModulePopupActionInput: ModulePopupExtraKind? = null
    private var modulePopupActionInputBuffer = ""
    private var modulePopupMappingOriginalBuffer = ""
    private var modulePopupMappingFakeBuffer = ""
    private var modulePopupMappingFocusedField: ModulePopupMappingField? = null
    private var modulePopupDragOffsetX = 0
    private var modulePopupDragOffsetY = 0
    private val modulePopupExtraScrolls = mutableMapOf<ModulePopupExtraKind, Int>()
    private var modulePopupExpandedExtraBounds = Rect.ZERO
    private var modulePopupExpandedExtraContentRows = 0
    private var modulePopupExpandedExtraVisibleRows = 0
    private var lastModuleBrowserClickDebug: Map<String, Any?>? = null
    private val pageReturnOverrides = mutableMapOf<Page, Page>()
    private var nickInputBounds = Rect.ZERO
    private var nickInputFocused = false
    private var pendingMappingReal: String? = null
    private var currentPage = Page.HUB
        set(value) {
            val previousWidth = panelWidth()
            val previousHeight = panelHeight()
            field = value
            pageScroll = 0
            skinDropdownOpen = false
            skinDropdownScroll = 0
            skinSettingsHitEntries = emptyList()
            coneDropdownOpen = false
            coneDropdownScroll = 0
            coneEditingIndex = -1
            coneEditBuffer = ""
            mobFilterScroll = 0
            expandedMobFilterColor = null
            mobFilterPickerDrag = null
            xrayEditorScroll = 0
            nameMappingScroll = 0
            addingMappingName = null
            addingMappingBuffer = ""
            activeAnimationSlider = null
            activeCameraSlider = null
            activeRenderSlider = null
            renderTitleFocused = false
            mobEspHitEntries = emptyList()
            cosmeticHitEntries = emptyList()
            activeCosmeticSlider = null
            hidersHitEntries = emptyList()
            nickHiderHitEntries = emptyList()
            guiStyleHitEntries = emptyList()
            moduleBrowserHitEntries = emptyList()
            moduleBrowserHeaderHitEntries = emptyList()
            moduleBrowserSearchBounds = Rect.ZERO
            moduleBrowserSearchFocused = false
            draggingModuleBrowserCategory = null
            modulePopupHitEntries = emptyList()
            modulePopupExtraHitEntries = emptyList()
            modulePopupPlayerHitEntries = emptyList()
            modulePopupXrayHitEntries = emptyList()
            modulePopupMobFilterHitEntries = emptyList()
            modulePopupNameMappingHitEntries = emptyList()
            expandedModulePopupExtra = null
            draggingModulePopup = false
            activeModulePopupSlider = null
            expandedModulePopupColor = null
            activeModulePopupColorDrag = null
            activeModulePopupString = null
            modulePopupStringBuffer = ""
            if (value != Page.CLICK_GUI) {
                modulePopup = null
                modulePopupMappingFocusedField = null
                modulePopupMappingOriginalBuffer = ""
                modulePopupMappingFakeBuffer = ""
            }
            nickInputBounds = Rect.ZERO
            nickInputFocused = value == Page.NICK_HIDER
            if (width > 0 && height > 0) {
                panelX = (panelX + (previousWidth - panelWidth()) / 2).coerceIn(0, max(0, width - panelWidth()))
                panelY = (panelY + (previousHeight - panelHeight()) / 2).coerceIn(0, max(0, height - panelHeight()))
            }
            if (value == Page.HUB || value == Page.CLICK_GUI) pageReturnOverrides.clear()
        }

    override fun init() {
        openStartMs = System.currentTimeMillis()
        closeStartMs = 0L
        closing = false
        panelX = (width - panelWidth()) / 2
        panelY = (height - panelHeight()) / 2
        dragging = false
        currentPage = Page.HUB
        labelHover.indices.forEach { labelHover[it] = 0f }
        textEditor = null
        colorPicker = null
        skinDropdownOpen = false
        skinDropdownScroll = 0
        skinSettingsHitEntries = emptyList()
        coneDropdownOpen = false
        coneDropdownScroll = 0
        coneEditingIndex = -1
        coneEditBuffer = ""
        mobFilterScroll = 0
        expandedMobFilterColor = null
        mobFilterPickerDrag = null
        xrayEditorScroll = 0
        nameMappingScroll = 0
        addingMappingName = null
        addingMappingBuffer = ""
        activeAnimationSlider = null
        activeCameraSlider = null
        activeRenderSlider = null
        renderTitleFocused = false
        mobEspHitEntries = emptyList()
        cosmeticHitEntries = emptyList()
        activeCosmeticSlider = null
        hidersHitEntries = emptyList()
        nickHiderHitEntries = emptyList()
        guiStyleHitEntries = emptyList()
        moduleBrowserHitEntries = emptyList()
        moduleBrowserHeaderHitEntries = emptyList()
        moduleBrowserSearchBounds = Rect.ZERO
        moduleBrowserSearchFocused = false
        draggingModuleBrowserCategory = null
        modulePopup = null
        modulePopupHitEntries = emptyList()
        modulePopupExtraHitEntries = emptyList()
        modulePopupPlayerHitEntries = emptyList()
        modulePopupXrayHitEntries = emptyList()
        modulePopupMobFilterHitEntries = emptyList()
        modulePopupNameMappingHitEntries = emptyList()
        expandedModulePopupExtra = null
        draggingModulePopup = false
        modulePopupMappingFocusedField = null
        modulePopupMappingOriginalBuffer = ""
        modulePopupMappingFakeBuffer = ""
        pageReturnOverrides.clear()
        nickInputBounds = Rect.ZERO
        nickInputFocused = false
        pendingMappingReal = null
    }

    fun openHub(): LegacyFloydClickGUI {
        openStartMs = System.currentTimeMillis()
        closeStartMs = 0L
        closing = false
        currentPage = Page.HUB
        dragging = false
        textEditor = null
        colorPicker = null
        skinDropdownOpen = false
        skinDropdownScroll = 0
        skinSettingsHitEntries = emptyList()
        coneDropdownOpen = false
        coneDropdownScroll = 0
        coneEditingIndex = -1
        coneEditBuffer = ""
        mobFilterScroll = 0
        expandedMobFilterColor = null
        mobFilterPickerDrag = null
        xrayEditorScroll = 0
        nameMappingScroll = 0
        addingMappingName = null
        addingMappingBuffer = ""
        activeAnimationSlider = null
        activeCameraSlider = null
        activeRenderSlider = null
        renderTitleFocused = false
        mobEspHitEntries = emptyList()
        cosmeticHitEntries = emptyList()
        activeCosmeticSlider = null
        hidersHitEntries = emptyList()
        nickHiderHitEntries = emptyList()
        guiStyleHitEntries = emptyList()
        moduleBrowserHitEntries = emptyList()
        moduleBrowserHeaderHitEntries = emptyList()
        moduleBrowserSearchBounds = Rect.ZERO
        moduleBrowserSearchFocused = false
        draggingModuleBrowserCategory = null
        modulePopup = null
        modulePopupHitEntries = emptyList()
        modulePopupExtraHitEntries = emptyList()
        modulePopupPlayerHitEntries = emptyList()
        modulePopupXrayHitEntries = emptyList()
        modulePopupMobFilterHitEntries = emptyList()
        modulePopupNameMappingHitEntries = emptyList()
        expandedModulePopupExtra = null
        draggingModulePopup = false
        modulePopupMappingFocusedField = null
        modulePopupMappingOriginalBuffer = ""
        modulePopupMappingFakeBuffer = ""
        pageReturnOverrides.clear()
        nickInputBounds = Rect.ZERO
        nickInputFocused = false
        pendingMappingReal = null
        return this
    }

    fun debugState(): Map<String, Any?> = mapOf(
        "page" to currentPage.name,
        "panel" to rectState(Rect(panelX, panelY, panelWidth(), panelHeight())),
        "labels" to labels.zip(labelBounds).associate { (label, bounds) -> label to rectState(bounds) },
        "rows" to debugPageRows(),
        "scroll" to pageScroll,
        "backButton" to rectState(pageBackButton),
        "doneButton" to rectState(pageDoneButton),
        "hubButtons" to mapOf(
            "editHud" to rectState(hudButton),
            "clickGui" to rectState(clickGuiButton),
            "v2" to rectState(v2Button),
            "editUi" to rectState(styleButton)
        ),
        "textEditor" to textEditor?.title,
        "colorPicker" to colorPicker?.title,
        "colorPickerBounds" to colorPicker?.let {
            mapOf(
                "modal" to rectState(colorPickerRect()),
                "sv" to rectState(colorPickerSvRect()),
                "hue" to rectState(colorPickerHueRect()),
                "apply" to rectState(colorPickerApplyButton()),
                "cancel" to rectState(colorPickerCancelButton()),
                "chroma" to rectState(colorPickerChromaButton()),
                "fade" to rectState(colorPickerFadeButton()),
                "editTarget" to rectState(colorPickerEditTargetButton()),
                "basePreview" to rectState(colorPickerBasePreviewRect()),
                "fadePreview" to rectState(colorPickerFadePreviewRect())
            )
        },
        "skinDropdownOpen" to skinDropdownOpen,
        "skinDropdownButton" to rectState(skinDropdownButton),
        "skinDropdownBounds" to rectState(skinDropdownList),
        "skinDropdownScroll" to skinDropdownScroll,
        "skinDropdownItems" to skinDropdownVisibleItems(),
        "skinSettingsEditor" to mapOf(
            "entries" to skinSettingsHitEntries.map {
                mapOf("kind" to it.kind.name, "settingName" to it.settingName, "bounds" to rectState(it.bounds))
            }
        ),
        "capeButtons" to mapOf(
            "previous" to rectState(capePrevButton),
            "next" to rectState(capeNextButton),
            "openFolder" to rectState(capeOpenFolderButton)
        ),
        "coneControls" to mapOf(
            "sliders" to coneRows.map { row -> rectState(row.slider) },
            "inputs" to coneInputBoxes.map(::rectState),
            "dropdownButton" to rectState(coneDropdownButton),
            "dropdownBounds" to rectState(coneDropdownList),
            "dropdownOpen" to coneDropdownOpen,
            "dropdownScroll" to coneDropdownScroll,
            "dropdownItems" to coneDropdownVisibleItems(),
            "openFolder" to rectState(coneOpenFolderButton),
            "editingIndex" to coneEditingIndex,
            "editBuffer" to coneEditBuffer
        ),
        "mobFilterEditor" to mapOf(
            "entries" to mobFilterHitEntries.map {
                mapOf("kind" to it.kind.name, "key" to it.key, "bounds" to rectState(it.bounds))
            },
            "scroll" to mobFilterScroll,
            "expandedColor" to expandedMobFilterColor?.let { mapOf("key" to it.key, "isName" to it.isName) },
            "picker" to mapOf(
                "hue" to mobFilterPickerHue,
                "saturation" to mobFilterPickerSaturation,
                "brightness" to mobFilterPickerBrightness,
                "drag" to mobFilterPickerDrag?.name
            )
        ),
        "xrayEditor" to mapOf(
            "entries" to xrayHitEntries.map {
                mapOf("add" to it.add, "blockId" to it.blockId, "bounds" to rectState(it.bounds))
            },
            "scroll" to xrayEditorScroll
        ),
        "nameMappingEditor" to mapOf(
            "entries" to nameMappingHitEntries.map {
                mapOf("kind" to it.kind.name, "realName" to it.realName, "bounds" to rectState(it.bounds))
            },
            "scroll" to nameMappingScroll,
            "addingPlayerName" to addingMappingName,
            "addBuffer" to addingMappingBuffer
        ),
        "animationsEditor" to mapOf(
            "entries" to animationHitEntries.map {
                mapOf("kind" to it.kind.name, "settingName" to it.settingName, "bounds" to rectState(it.bounds))
            },
            "activeSlider" to activeAnimationSlider?.spec?.settingName
        ),
        "cameraEditor" to mapOf(
            "entries" to cameraHitEntries.map {
                mapOf("kind" to it.kind.name, "settingName" to it.settingName, "bounds" to rectState(it.bounds))
            },
            "activeSlider" to activeCameraSlider?.spec?.settingName
        ),
        "renderEditor" to mapOf(
            "entries" to renderHitEntries.map {
                mapOf("kind" to it.kind.name, "settingName" to it.settingName, "bounds" to rectState(it.bounds))
            },
            "activeSlider" to activeRenderSlider?.spec?.settingName,
            "titleFocused" to renderTitleFocused
        ),
        "mobEspEditor" to mapOf(
            "entries" to mobEspHitEntries.map {
                mapOf("kind" to it.kind.name, "settingName" to it.settingName, "bounds" to rectState(it.bounds))
            }
        ),
        "cosmeticEditor" to mapOf(
            "entries" to cosmeticHitEntries.map {
                mapOf("kind" to it.kind.name, "settingName" to it.settingName, "bounds" to rectState(it.bounds))
            },
            "activeSlider" to activeCosmeticSlider?.spec?.settingName
        ),
        "hidersEditor" to mapOf(
            "entries" to hidersHitEntries.map {
                mapOf("kind" to it.kind.name, "settingName" to it.settingName, "bounds" to rectState(it.bounds))
            }
        ),
        "nickHiderEditor" to mapOf(
            "entries" to nickHiderHitEntries.map {
                mapOf("kind" to it.kind.name, "bounds" to rectState(it.bounds))
            },
            "input" to rectState(nickInputBounds),
            "focused" to nickInputFocused
        ),
        "guiStyleEditor" to mapOf(
            "entries" to guiStyleHitEntries.map {
                mapOf("target" to it.target.name, "bounds" to rectState(it.bounds))
            },
            "buttonText" to legacyStyleState(StyleTarget.TEXT),
            "buttonBorder" to legacyStyleState(StyleTarget.BUTTON_BORDER),
            "guiBorder" to legacyStyleState(StyleTarget.GUI_BORDER)
        ),
        "moduleBrowser" to mapOf(
            "search" to mapOf(
                "query" to moduleBrowserSearchQuery,
                "focused" to moduleBrowserSearchFocused,
                "bounds" to rectState(moduleBrowserSearchBounds)
            ),
            "panelConfigPath" to legacyClickGuiPanelConfigPath.toString(),
            "headers" to moduleBrowserHeaderHitEntries.map {
                val state = moduleBrowserPanelStates[it.category]
                mapOf(
                    "category" to it.category.name,
                    "bounds" to rectState(it.bounds),
                    "collapsed" to (state?.collapsed ?: false),
                    "scroll" to (state?.scroll ?: 0)
                )
            },
            "entries" to moduleBrowserHitEntries.map {
                mapOf(
                    "category" to it.category.name,
                    "module" to it.module.name,
                    "displayName" to it.entry.label,
                    "legacyKind" to it.entry.kind.name,
                    "enabled" to it.entry.enabled(),
                    "settingsPage" to it.settingsPage?.name,
                    "popupContentAvailable" to modulePopupContentAvailable(it.entry),
                    "bounds" to rectState(it.bounds)
                )
            },
            "lastClick" to lastModuleBrowserClickDebug
        ),
        "modulePopup" to modulePopup?.let { popup ->
            mapOf(
                "module" to popup.module.name,
                "displayName" to modulePopupTitle(popup),
                "bounds" to rectState(popup.bounds),
                "playerPreviewBounds" to rectState(modulePopupPlayerPreviewBounds(popup)),
                "expandedColor" to expandedModulePopupColor?.name,
                "colorDrag" to activeModulePopupColorDrag?.name,
                "expandedExtra" to expandedModulePopupExtra?.name,
                "expandedExtraScroll" to (expandedModulePopupExtra?.let { modulePopupExtraScroll(it) }),
                "editingString" to activeModulePopupString?.setting?.name,
            "stringBuffer" to modulePopupStringBuffer.takeIf { activeModulePopupString != null },
            "actionInput" to activeModulePopupActionInput?.name,
            "actionInputBuffer" to modulePopupActionInputBuffer.takeIf { activeModulePopupActionInput != null },
            "mappingInput" to mapOf(
                "original" to modulePopupMappingOriginalBuffer,
                "fake" to modulePopupMappingFakeBuffer,
                "focused" to modulePopupMappingFocusedField?.name
            ),
            "extraEntries" to modulePopupExtraHitEntries.map {
                    mapOf("kind" to it.kind.name, "label" to it.label, "bounds" to rectState(it.bounds))
                },
            "playerEntries" to modulePopupPlayerHitEntries.map {
                    mapOf("target" to it.playerName, "submit" to it.submit, "bounds" to rectState(it.bounds))
                },
                "xrayEntries" to modulePopupXrayHitEntries.map {
                    mapOf("block" to it.blockId, "add" to it.add, "action" to it.action?.name, "submit" to it.submit, "bounds" to rectState(it.bounds))
                },
                "mobFilterEntries" to modulePopupMobFilterHitEntries.map {
                    mapOf("key" to it.key, "kind" to it.kind.name, "submit" to it.submit, "bounds" to rectState(it.bounds))
                },
                "nameMappingEntries" to modulePopupNameMappingHitEntries.map {
                    mapOf("realName" to it.realName, "kind" to it.kind.name, "bounds" to rectState(it.bounds))
                },
                "entries" to modulePopupHitEntries.map {
                    mapOf(
                        "kind" to it.kind.name,
                        "settingName" to it.setting.name,
                        "value" to modulePopupDebugValue(it.setting),
                        "options" to modulePopupDebugOptions(it.setting),
                        "chroma" to modulePopupDebugChroma(it.setting),
                        "fade" to modulePopupDebugFade(it.setting),
                        "editingFade" to ((it.setting === expandedModulePopupColor) && modulePopupEditingFadeColor),
                        "bounds" to rectState(it.bounds)
                    )
                }
            )
        },
        "editorSaveButton" to rectState(editorSaveButton),
        "editorCancelButton" to rectState(editorCancelButton),
        "hover" to mapOf("x" to hoverX, "y" to hoverY),
        "mouseOverride" to (activeMouseOverride()?.let { mapOf("x" to it.first, "y" to it.second) }),
        "physicalMouse" to mapOf("x" to mc.mouseHandler.xpos(), "y" to mc.mouseHandler.ypos())
    )

    private fun debugPageRows(): List<Map<String, Any?>> {
        if (currentPage == Page.HUB || currentPage == Page.CLICK_GUI) return emptyList()
        val contentWidth = 240
        val contentLeft = panelX + (panelWidth() - contentWidth) / 2
        val contentTop = panelY + 26
        val laidOutRows = layoutRows(rowsFor(currentPage), contentLeft, contentTop, contentWidth)
        val visibleLines = pageScroll until pageScroll + visibleRowCount()
        return laidOutRows
            .filter { it.line in visibleLines }
            .map { row ->
                mapOf(
                    "label" to row.row.label(),
                    "kind" to row.row.kind.name,
                    "bounds" to rectState(row.bounds)
                )
            }
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val mouseOverride = activeMouseOverride()
        val hoverPoint = mouseOverride?.let { it.first.toDouble() to it.second.toDouble() }
            ?: activeEventMousePoint()
            ?: bestGuiMousePoint(mouseX.toDouble(), mouseY.toDouble())
        hoverX = hoverPoint.first
        hoverY = hoverPoint.second
        val now = System.currentTimeMillis()
        val closeProgress = if (closing) ((now - closeStartMs) / fadeDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
        if (closing && closeProgress >= 1f) {
            mc.setScreen(null)
            return
        }
        val alpha = if (closing) 1f - closeProgress else ((now - openStartMs) / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
        if (alpha <= 0f) return
        if (currentPage == Page.CLICK_GUI) {
            context.fill(0, 0, width, height, applyAlpha(0x88000000.toInt(), alpha))
            drawModuleBrowserPage(context, alpha)
            super.render(context, mouseX, mouseY, deltaTicks)
            return
        }
        val scale = lerp(0.85f, 1f, alpha)
        val centerX = panelX + panelWidth() / 2
        val centerY = panelY + panelHeight() / 2

        context.pose().pushMatrix()
        context.pose().translate(centerX.toFloat(), centerY.toFloat())
        context.pose().scale(scale, scale)
        context.pose().translate(-centerX.toFloat(), -centerY.toFloat())

        val left = panelX
        val top = panelY
        val right = left + panelWidth()
        val bottom = top + panelHeight()
        drawChromaBorder(context, left - borderThickness, top - borderThickness, right + borderThickness, bottom + borderThickness, alpha)
        if (currentPage == Page.HUB) {
            drawStretchBackground(context, left, top, panelWidth(), panelHeight(), alpha)
        } else {
            context.fill(left, top, right, bottom, applyAlpha(0xAA000000.toInt(), alpha))
        }

        if (currentPage == Page.HUB) {
            hudButton = Rect(left + 6, top + 4, styleButtonWidth, buttonHeight)
            clickGuiButton = Rect(left + 6, hudButton.bottom + 4, clickGuiButtonWidth, buttonHeight)
            v2Button = Rect(left + 6, clickGuiButton.bottom + 4, v2ButtonWidth, buttonHeight)
            styleButton = Rect(right - styleButtonWidth - 6, top + 4, styleButtonWidth, buttonHeight)
            drawButton(context, hudButton, "Edit HUD", alpha)
            drawButton(context, clickGuiButton, "ClickGUI", alpha)
            drawButton(context, v2Button, "Open V2 UI", alpha)
            drawButton(context, styleButton, "Edit UI", alpha)
            drawTitle(context, centerX, top + scaleY(20), alpha)
        } else {
            hudButton = Rect.ZERO
            clickGuiButton = Rect.ZERO
            v2Button = Rect.ZERO
            styleButton = Rect.ZERO
        }

        context.pose().popMatrix()

        if (currentPage == Page.HUB) {
            drawCentralLabels(context, left, top, alpha)
            drawGithub(context, left, bottom, alpha)
            pageRows = emptyList()
            pageBackButton = Rect.ZERO
            pageDoneButton = Rect.ZERO
        } else {
            labelBounds = emptyList()
            linkBounds = Rect.ZERO
            drawPage(context, left, top, bottom, alpha)
        }

        textEditor?.let { drawTextEditor(context, left, top, alpha, it) }
        colorPicker?.let { drawColorPicker(context, alpha, it) }

        super.render(context, mouseX, mouseY, deltaTicks)
    }

    override fun onClose() {
        if (closing) return
        finishModulePopupStringEdit()
        if (currentPage == Page.CLICK_GUI) saveModuleBrowserPanelStates()
        ModuleManager.saveConfigurations()
        closing = true
        closeStartMs = System.currentTimeMillis()
    }

    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, ignoresInput: Boolean): Boolean {
        if (!hasTransientMouseOverride()) clearMouseOverride()
        val button = mouseButtonEvent.button()
        if (button != 0 && button != 1) return super.mouseClicked(mouseButtonEvent, ignoresInput)
        val points = clickPoints(mouseButtonEvent)
        val mx = points.first().first
        val my = points.first().second
        rememberEventMouse(mx, my)
        val clickedRow = pageRows.firstOrNull { row -> points.any { row.bounds.contains(it.first, it.second) } }
        val editor = textEditor
        val picker = colorPicker

        if (button == 0 && activeModulePopupString != null) finishModulePopupStringEdit()

        if (picker != null) {
            if (button == 0 && points.any { handleColorPickerClick(picker, it.first, it.second) }) return true
            if (button == 0) return true
            return super.mouseClicked(mouseButtonEvent, ignoresInput)
        }

        if (editor != null) {
            when {
                button == 0 && points.any { editorSaveButton.contains(it.first, it.second) } -> {
                    saveTextEditor(editor)
                    return true
                }
                button == 0 && points.any { editorCancelButton.contains(it.first, it.second) } -> {
                    textEditor = null
                    pendingMappingReal = null
                    return true
                }
                button == 0 -> return true
                else -> return super.mouseClicked(mouseButtonEvent, ignoresInput)
            }
        }

        if (skinDropdownOpen && currentPage == Page.SKIN && button == 0) {
            if (points.any { handleSkinDropdownClick(it.first, it.second) }) return true
            skinDropdownOpen = false
            return true
        }

        if (currentPage == Page.SKIN && button == 0) {
            points.firstOrNull { point -> handleSkinSettingsClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.CONE_HAT && button == 0) {
            if (coneDropdownOpen) {
                if (points.any { handleConeDropdownClick(it.first, it.second) }) return true
                coneDropdownOpen = false
                return true
            }
            points.firstOrNull { point -> handleConePageClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.MOB_ESP_FILTERS && button == 0) {
            points.firstOrNull { point -> handleMobFilterEditorClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.MOB_ESP && button == 0) {
            points.firstOrNull { point -> handleMobEspEditorClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.XRAY && button == 0) {
            points.firstOrNull { point -> handleXrayEditorClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.NAME_MAPPINGS && button == 0) {
            points.firstOrNull { point -> handleNameMappingEditorClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.ANIMATIONS && button == 0) {
            points.firstOrNull { point -> handleAnimationsEditorClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.CAMERA && button == 0) {
            points.firstOrNull { point -> handleCameraEditorClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.RENDER && button == 0) {
            points.firstOrNull { point -> handleRenderEditorClick(point.first, point.second) }?.let { return true }
            renderTitleFocused = false
        }

        if (currentPage == Page.COSMETIC && button == 0) {
            points.firstOrNull { point -> handleCosmeticEditorClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.HIDERS && button == 0) {
            points.firstOrNull { point -> handleHidersEditorClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.NICK_HIDER && button == 0) {
            points.firstOrNull { point -> handleNickHiderEditorClick(point.first, point.second) }?.let { return true }
            nickInputFocused = false
        }

        if (currentPage == Page.GUI_STYLE && button == 0) {
            points.firstOrNull { point -> handleGuiStyleClick(point.first, point.second) }?.let { return true }
        }

        if (currentPage == Page.CLICK_GUI && (button == 0 || button == 1)) {
            points.firstOrNull { point -> handleModulePopupClick(point.first, point.second, button) }?.let { return true }
            if (button == 0 && modulePopup != null && points.any { point -> !modulePopupBoundsContains(point.first, point.second) }) {
                closeModulePopup()
            }
            points.firstOrNull { point -> handleModuleBrowserChromeClick(point.first, point.second, button) }?.let { return true }
            points.firstOrNull { point -> handleModuleBrowserClick(point.first, point.second, button) }?.let { return true }
            if (button == 0 && modulePopup != null) {
                closeModulePopup()
                return true
            }
        }

        when {
            button == 0 && points.any { styleButton.contains(it.first, it.second) } -> {
                currentPage = Page.GUI_STYLE
                return true
            }
            button == 0 && points.any { hudButton.contains(it.first, it.second) } -> {
                mc.setScreen(HudManager)
                return true
            }
            button == 0 && points.any { clickGuiButton.contains(it.first, it.second) } -> {
                currentPage = Page.CLICK_GUI
                return true
            }
            button == 0 && points.any { v2Button.contains(it.first, it.second) } -> {
                mc.setScreen(ClickGUI)
                return true
            }
            button == 0 && points.any { linkBounds.contains(it.first, it.second) } -> {
                runCatching { Desktop.getDesktop().browse(URI(githubUrl)) }
                return true
            }
            button == 0 && points.any { pageBackButton.contains(it.first, it.second) } -> {
                currentPage = pageParent(currentPage)
                return true
            }
            button == 0 && points.any { pageDoneButton.contains(it.first, it.second) } -> {
                currentPage = pageParent(currentPage)
                return true
            }
            currentPage == Page.CAPE && button == 0 && points.any { capePrevButton.contains(it.first, it.second) } -> {
                FloydCape.cycleCapeFile(-1)
                return true
            }
            currentPage == Page.CAPE && button == 0 && points.any { capeNextButton.contains(it.first, it.second) } -> {
                FloydCape.cycleCapeFile(1)
                return true
            }
            currentPage == Page.CAPE && button == 0 && points.any { capeOpenFolderButton.contains(it.first, it.second) } -> {
                moduleAction(FloydCape, "Open Cape Folder")?.action()
                ModuleManager.saveConfigurations()
                return true
            }
            clickedRow != null -> {
                clickedRow.action(button)
                return true
            }
            button == 0 && mx >= panelX && mx <= panelX + panelWidth() && my >= panelY && my <= panelY + dragBarHeight -> {
                dragging = true
                dragStartMouseX = mx
                dragStartMouseY = my
                dragStartPanelX = panelX
                dragStartPanelY = panelY
                return true
            }
        }

        val label = if (button == 0) labelBounds.indexOfFirst { rect -> points.any { rect.contains(it.first, it.second) } } else -1
        if (label >= 0) {
            openLabel(labels[label])
            return true
        }

        return super.mouseClicked(mouseButtonEvent, ignoresInput)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        textEditor?.let { editor ->
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    textEditor = null
                    pendingMappingReal = null
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> saveTextEditor(editor)
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (editor.value.isNotEmpty()) editor.value = editor.value.dropLast(1)
                }
                GLFW.GLFW_KEY_DELETE -> editor.value = ""
                else -> return super.keyPressed(keyEvent)
            }
            return true
        }
        if (currentPage == Page.NICK_HIDER && nickInputFocused) {
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> nickInputFocused = false
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (FloydNickHider.nickname.isNotEmpty()) {
                        FloydNickHider.nickname = FloydNickHider.nickname.dropLast(1)
                        ModuleManager.saveConfigurations()
                    }
                }
                GLFW.GLFW_KEY_DELETE -> {
                    FloydNickHider.nickname = ""
                    ModuleManager.saveConfigurations()
                }
                else -> return true
            }
            return true
        }
        if (currentPage == Page.RENDER && renderTitleFocused) {
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> renderTitleFocused = false
                GLFW.GLFW_KEY_BACKSPACE -> {
                    val setting = stringSetting(FloydRender, "Instance Title") ?: return true
                    if (setting.value.isNotEmpty()) {
                        setting.value = setting.value.dropLast(1)
                        ModuleManager.saveConfigurations()
                    }
                }
                GLFW.GLFW_KEY_DELETE -> {
                    stringSetting(FloydRender, "Instance Title")?.value = ""
                    ModuleManager.saveConfigurations()
                }
                else -> return true
            }
            return true
        }
        if (currentPage == Page.CONE_HAT && coneEditingIndex >= 0) {
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE -> coneEditingIndex = -1
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> applyConeEdit()
                GLFW.GLFW_KEY_BACKSPACE -> if (coneEditBuffer.isNotEmpty()) coneEditBuffer = coneEditBuffer.dropLast(1)
                GLFW.GLFW_KEY_DELETE -> coneEditBuffer = ""
                else -> return true
            }
            return true
        }
        if (currentPage == Page.NAME_MAPPINGS && addingMappingName != null) {
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE -> clearInlineNameMappingAdd()
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> saveInlineNameMappingAdd()
                GLFW.GLFW_KEY_BACKSPACE -> if (addingMappingBuffer.isNotEmpty()) addingMappingBuffer = addingMappingBuffer.dropLast(1)
                GLFW.GLFW_KEY_DELETE -> addingMappingBuffer = ""
                else -> return true
            }
            return true
        }
        activeModulePopupString?.let {
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    activeModulePopupString = null
                    modulePopupStringBuffer = ""
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> finishModulePopupStringEdit()
                GLFW.GLFW_KEY_BACKSPACE -> if (modulePopupStringBuffer.isNotEmpty()) modulePopupStringBuffer = modulePopupStringBuffer.dropLast(1)
                GLFW.GLFW_KEY_DELETE -> modulePopupStringBuffer = ""
                else -> return true
            }
            return true
        }
        activeModulePopupActionInput?.let {
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    activeModulePopupActionInput = null
                    modulePopupActionInputBuffer = ""
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> submitModulePopupActionInput()
                GLFW.GLFW_KEY_BACKSPACE -> if (modulePopupActionInputBuffer.isNotEmpty()) {
                    modulePopupActionInputBuffer = modulePopupActionInputBuffer.dropLast(1)
                    resetModulePopupActionScroll(it)
                }
                GLFW.GLFW_KEY_DELETE -> {
                    modulePopupActionInputBuffer = ""
                    resetModulePopupActionScroll(it)
                }
                else -> return true
            }
            return true
        }
        modulePopupMappingFocusedField?.let { field ->
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE -> modulePopupMappingFocusedField = null
                GLFW.GLFW_KEY_TAB -> modulePopupMappingFocusedField = if (field == ModulePopupMappingField.ORIGINAL) ModulePopupMappingField.FAKE else ModulePopupMappingField.ORIGINAL
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> submitModulePopupNameMappingInput()
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (field == ModulePopupMappingField.ORIGINAL && modulePopupMappingOriginalBuffer.isNotEmpty()) {
                        modulePopupMappingOriginalBuffer = modulePopupMappingOriginalBuffer.dropLast(1)
                        resetModulePopupNameMappingScroll()
                    } else if (field == ModulePopupMappingField.FAKE && modulePopupMappingFakeBuffer.isNotEmpty()) {
                        modulePopupMappingFakeBuffer = modulePopupMappingFakeBuffer.dropLast(1)
                    }
                }
                GLFW.GLFW_KEY_DELETE -> {
                    if (field == ModulePopupMappingField.ORIGINAL) {
                        modulePopupMappingOriginalBuffer = ""
                        resetModulePopupNameMappingScroll()
                    } else {
                        modulePopupMappingFakeBuffer = ""
                    }
                }
                else -> return true
            }
            return true
        }
        if (currentPage == Page.CLICK_GUI && moduleBrowserSearchFocused) {
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> moduleBrowserSearchFocused = false
                GLFW.GLFW_KEY_BACKSPACE -> if (moduleBrowserSearchQuery.isNotEmpty()) moduleBrowserSearchQuery = moduleBrowserSearchQuery.dropLast(1)
                GLFW.GLFW_KEY_DELETE -> moduleBrowserSearchQuery = ""
                else -> return true
            }
            clampModuleBrowserScrolls()
            return true
        }
        colorPicker?.let { picker ->
            when (keyEvent.key) {
                GLFW.GLFW_KEY_ESCAPE -> colorPicker = null
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> applyColorPicker(picker)
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (picker.hex.isNotEmpty()) {
                        picker.hex = picker.hex.dropLast(1)
                        syncColorPickerFromHex(picker)
                    }
                }
                GLFW.GLFW_KEY_DELETE -> picker.hex = ""
                else -> return super.keyPressed(keyEvent)
            }
            return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        textEditor?.let { editor ->
            if (!characterEvent.isAllowedChatCharacter) return true
            val appended = editor.value + characterEvent.codepointAsString()
            editor.value = if (appended.length <= editor.maxLength) appended else appended.take(editor.maxLength)
            return true
        }
        if (currentPage == Page.NICK_HIDER && nickInputFocused) {
            if (!characterEvent.isAllowedChatCharacter) return true
            val appended = FloydNickHider.nickname + characterEvent.codepointAsString()
            FloydNickHider.nickname = appended.take(32)
            ModuleManager.saveConfigurations()
            return true
        }
        if (currentPage == Page.RENDER && renderTitleFocused) {
            if (!characterEvent.isAllowedChatCharacter) return true
            val setting = stringSetting(FloydRender, "Instance Title") ?: return true
            val appended = setting.value + characterEvent.codepointAsString()
            if (appended.length <= 64) {
                setting.value = appended
                ModuleManager.saveConfigurations()
            }
            return true
        }
        if (currentPage == Page.CONE_HAT && coneEditingIndex >= 0) {
            val char = characterEvent.codepointAsString().firstOrNull() ?: return true
            if (char == '-' || char == '.' || char in '0'..'9') coneEditBuffer += char
            return true
        }
        if (currentPage == Page.NAME_MAPPINGS && addingMappingName != null) {
            if (!characterEvent.isAllowedChatCharacter) return true
            val appended = addingMappingBuffer + characterEvent.codepointAsString()
            addingMappingBuffer = appended.take(32)
            return true
        }
        activeModulePopupString?.let {
            if (!characterEvent.isAllowedChatCharacter) return true
            modulePopupStringBuffer += characterEvent.codepointAsString()
            return true
        }
        activeModulePopupActionInput?.let {
            if (!characterEvent.isAllowedChatCharacter) return true
            val appended = modulePopupActionInputBuffer + characterEvent.codepointAsString()
            modulePopupActionInputBuffer = appended.take(64)
            resetModulePopupActionScroll(it)
            return true
        }
        modulePopupMappingFocusedField?.let { field ->
            if (!characterEvent.isAllowedChatCharacter) return true
            val appended = if (field == ModulePopupMappingField.ORIGINAL) modulePopupMappingOriginalBuffer + characterEvent.codepointAsString()
            else modulePopupMappingFakeBuffer + characterEvent.codepointAsString()
            if (field == ModulePopupMappingField.ORIGINAL) {
                modulePopupMappingOriginalBuffer = appended.take(32)
                resetModulePopupNameMappingScroll()
            } else {
                modulePopupMappingFakeBuffer = appended.take(32)
            }
            return true
        }
        if (currentPage == Page.CLICK_GUI && moduleBrowserSearchFocused) {
            if (!characterEvent.isAllowedChatCharacter) return true
            val appended = moduleBrowserSearchQuery + characterEvent.codepointAsString()
            moduleBrowserSearchQuery = appended.take(64)
            clampModuleBrowserScrolls()
            return true
        }
        colorPicker?.let { picker ->
            val char = characterEvent.codepointAsString().firstOrNull() ?: return true
            if (char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F') {
                if (picker.hex.length < 6) {
                    picker.hex += char.uppercaseChar()
                    syncColorPickerFromHex(picker)
                }
            }
            return true
        }
        return super.charTyped(characterEvent)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        if (!hasTransientMouseOverride()) clearMouseOverride()
        val point = bestGuiMousePoint(mouseX, mouseY)
        rememberEventMouse(point.first, point.second)
        super.mouseMoved(mouseX, mouseY)
    }

    private fun rememberEventMouse(x: Double, y: Double) {
        eventMousePoint = x to y
        eventMouseUpdatedAt = System.currentTimeMillis()
        hoverX = x
        hoverY = y
    }

    private fun activeEventMousePoint(): Pair<Double, Double>? {
        val point = eventMousePoint ?: return null
        if (System.currentTimeMillis() - eventMouseUpdatedAt <= 1000L) return point
        eventMousePoint = null
        eventMouseUpdatedAt = 0L
        return null
    }

    private fun clickPoints(mouseButtonEvent: MouseButtonEvent): List<Pair<Double, Double>> {
        val eventX = mouseButtonEvent.x()
        val eventY = mouseButtonEvent.y()
        val overrideX = odinMouseX.toDouble()
        val overrideY = odinMouseY.toDouble()
        val points = mutableListOf<Pair<Double, Double>>()
        val mouseOverride = activeMouseOverride()
        if (mouseOverride != null) {
            points += mouseOverride.first.toDouble() to mouseOverride.second.toDouble()
            points += eventX to eventY
            points += overrideX to overrideY
        } else {
            points += bestGuiMousePoint(eventX, eventY)
            points += guiMouseCandidates(eventX, eventY)
            points += eventX to eventY
            points += hoverX to hoverY
            activeEventMousePoint()?.let { points += it }
            physicalMouseGuiPoint()?.let { points += it }
        }
        return points.distinct()
    }

    private fun bestGuiMousePoint(x: Double, y: Double): Pair<Double, Double> {
        val candidates = guiMouseCandidates(x, y)
        val hitRects = currentMouseHitRects()
        return candidates.firstOrNull { point -> hitRects.any { it.contains(point.first, point.second) } }
            ?: candidates.firstOrNull { (candidateX, candidateY) -> candidateX in 0.0..width.toDouble() && candidateY in 0.0..height.toDouble() }
            ?: candidates.first()
    }

    private fun guiMouseCandidates(x: Double, y: Double): List<Pair<Double, Double>> {
        val candidates = mutableListOf<Pair<Double, Double>>()
        candidates += normalizedMousePoint(x, y)
        candidates += x to y
        if (mc.window.width != mc.window.guiScaledWidth || mc.window.height != mc.window.guiScaledHeight) {
            candidates += rawToGuiScaled(x, y)
        }
        return candidates.distinct()
    }

    private fun normalizedMousePoint(x: Double, y: Double): Pair<Double, Double> =
        if (looksLikeRawWindowMouse(x, y)) rawToGuiScaled(x, y) else x to y

    private fun looksLikeRawWindowMouse(x: Double, y: Double): Boolean {
        val window = mc.window
        if (window.width == window.guiScaledWidth && window.height == window.guiScaledHeight) return false
        if (x > width || y > height) return true
        val physicalX = mc.mouseHandler.xpos()
        val physicalY = mc.mouseHandler.ypos()
        return abs(x - physicalX) < 1.0 && abs(y - physicalY) < 1.0
    }

    private fun rawToGuiScaled(x: Double, y: Double): Pair<Double, Double> =
        x * mc.window.guiScaledWidth / mc.window.width to y * mc.window.guiScaledHeight / mc.window.height

    private fun physicalMouseGuiPoint(): Pair<Double, Double>? {
        val x = mc.mouseHandler.xpos()
        val y = mc.mouseHandler.ypos()
        if (x == 0.0 && y == 0.0) return null
        if (x < 0.0 || y < 0.0 || x > mc.window.width || y > mc.window.height) return null
        return rawToGuiScaled(x, y)
    }

    private fun currentMouseHitRects(): List<Rect> = buildList {
        add(styleButton)
        add(hudButton)
        add(clickGuiButton)
        add(v2Button)
        add(linkBounds)
        add(pageBackButton)
        add(pageDoneButton)
        add(editorSaveButton)
        add(editorCancelButton)
        add(skinDropdownButton)
        add(skinDropdownList)
        add(capePrevButton)
        add(capeNextButton)
        add(capeOpenFolderButton)
        add(coneDropdownButton)
        add(coneDropdownList)
        add(coneOpenFolderButton)
        add(nickInputBounds)
        add(moduleBrowserSearchBounds)
        addAll(labelBounds)
        addAll(pageRows.map { it.bounds })
        addAll(skinSettingsHitEntries.map { it.bounds })
        addAll(coneRows.flatMap { listOf(it.slider, it.input) })
        addAll(coneInputBoxes)
        addAll(mobFilterHitEntries.map { it.bounds })
        addAll(xrayHitEntries.map { it.bounds })
        addAll(nameMappingHitEntries.map { it.bounds })
        addAll(animationHitEntries.map { it.bounds })
        addAll(cameraHitEntries.map { it.bounds })
        addAll(renderHitEntries.map { it.bounds })
        addAll(mobEspHitEntries.map { it.bounds })
        addAll(cosmeticHitEntries.map { it.bounds })
        addAll(hidersHitEntries.map { it.bounds })
        addAll(nickHiderHitEntries.map { it.bounds })
        addAll(guiStyleHitEntries.map { it.bounds })
        addAll(moduleBrowserHeaderHitEntries.map { it.bounds })
        addAll(moduleBrowserHitEntries.map { it.bounds })
        addAll(modulePopupHitEntries.map { it.bounds })
        addAll(modulePopupExtraHitEntries.map { it.bounds })
        addAll(modulePopupPlayerHitEntries.map { it.bounds })
        addAll(modulePopupXrayHitEntries.map { it.bounds })
        addAll(modulePopupMobFilterHitEntries.map { it.bounds })
        addAll(modulePopupNameMappingHitEntries.map { it.bounds })
        modulePopup?.let { add(it.bounds) }
        colorPicker?.let {
            add(colorPickerRect())
            add(colorPickerSvRect())
            add(colorPickerHueRect())
            add(colorPickerApplyButton())
            add(colorPickerCancelButton())
            add(colorPickerChromaButton())
            add(colorPickerFadeButton())
            add(colorPickerEditTargetButton())
            add(colorPickerBasePreviewRect())
            add(colorPickerFadePreviewRect())
        }
    }.filter { it.width > 0 && it.height > 0 }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        clickPoints(mouseButtonEvent).firstOrNull()?.let { rememberEventMouse(it.first, it.second) }
        colorPicker?.let { picker ->
            val point = clickPoints(mouseButtonEvent).first()
            when (picker.dragTarget) {
                ColorPickerDrag.SV -> updateColorPickerSv(picker, point.first - colorPickerSvRect().left, point.second - colorPickerSvRect().top)
                ColorPickerDrag.HUE -> updateColorPickerHue(picker, point.second - colorPickerHueRect().top)
                null -> return@let
            }
            return true
        }
        if (currentPage == Page.MOB_ESP_FILTERS && mouseButtonEvent.button() == 0 && mobFilterPickerDrag != null) {
            val point = clickPoints(mouseButtonEvent).first()
            updateMobFilterInlinePickerDrag(point.first, point.second)
            return true
        }
        if (currentPage == Page.ANIMATIONS && mouseButtonEvent.button() == 0 && activeAnimationSlider != null) {
            updateAnimationSlider(activeAnimationSlider ?: return true, clickPoints(mouseButtonEvent).first().first)
            return true
        }
        if (currentPage == Page.CAMERA && mouseButtonEvent.button() == 0 && activeCameraSlider != null) {
            updateCameraSlider(activeCameraSlider ?: return true, clickPoints(mouseButtonEvent).first().first)
            return true
        }
        if (currentPage == Page.RENDER && mouseButtonEvent.button() == 0 && activeRenderSlider != null) {
            updateRenderSlider(activeRenderSlider ?: return true, clickPoints(mouseButtonEvent).first().first)
            return true
        }
        if (currentPage == Page.COSMETIC && mouseButtonEvent.button() == 0 && activeCosmeticSlider != null) {
            updateCosmeticSlider(activeCosmeticSlider ?: return true, clickPoints(mouseButtonEvent).first().first)
            return true
        }
        if (currentPage == Page.CLICK_GUI && mouseButtonEvent.button() == 0 && activeModulePopupSlider != null) {
            updateModulePopupSlider(activeModulePopupSlider ?: return true, clickPoints(mouseButtonEvent).first().first)
            return true
        }
        if (currentPage == Page.CLICK_GUI && mouseButtonEvent.button() == 0 && activeModulePopupColorDrag != null) {
            val point = clickPoints(mouseButtonEvent).first()
            updateModulePopupColorDrag(point.first, point.second)
            return true
        }
        draggingModuleBrowserCategory?.let { category ->
            if (currentPage == Page.CLICK_GUI && mouseButtonEvent.button() == 0) {
                val state = moduleBrowserPanelState(category)
                val point = clickPoints(mouseButtonEvent).first()
                val panelWidth = moduleBrowserPanelWidth(category)
                state.x = (point.first.roundToInt() - moduleBrowserDragOffsetX).coerceIn(0, max(0, width - panelWidth))
                state.y = (point.second.roundToInt() - moduleBrowserDragOffsetY).coerceIn(moduleBrowserSearchHeight + 6, max(moduleBrowserSearchHeight + 6, height - moduleBrowserHeaderHeight))
                return true
            }
        }
        if (draggingModulePopup && currentPage == Page.CLICK_GUI && mouseButtonEvent.button() == 0) {
            val popup = modulePopup ?: return true
            val point = clickPoints(mouseButtonEvent).first()
            popup.bounds = Rect(
                (point.first.roundToInt() - modulePopupDragOffsetX).coerceIn(0, max(0, width - popup.bounds.width)),
                (point.second.roundToInt() - modulePopupDragOffsetY).coerceIn(0, max(0, height - popup.bounds.height)),
                popup.bounds.width,
                popup.bounds.height
            )
            return true
        }
        if (dragging && mouseButtonEvent.button() == 0) {
            panelX = (dragStartPanelX + (mouseButtonEvent.x() - dragStartMouseX).roundToInt()).coerceIn(0, max(0, width - panelWidth()))
            panelY = (dragStartPanelY + (mouseButtonEvent.y() - dragStartMouseY).roundToInt()).coerceIn(0, max(0, height - panelHeight()))
            return true
        }
        return super.mouseDragged(mouseButtonEvent, deltaX, deltaY)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        colorPicker?.let { picker ->
            if (mouseButtonEvent.button() == 0 && picker.dragTarget != null) {
                picker.dragTarget = null
                return true
            }
        }
        if (mouseButtonEvent.button() == 0 && mobFilterPickerDrag != null) {
            mobFilterPickerDrag = null
            return true
        }
        if (mouseButtonEvent.button() == 0 && activeAnimationSlider != null) {
            activeAnimationSlider = null
            return true
        }
        if (mouseButtonEvent.button() == 0 && activeCameraSlider != null) {
            activeCameraSlider = null
            return true
        }
        if (mouseButtonEvent.button() == 0 && activeRenderSlider != null) {
            activeRenderSlider = null
            return true
        }
        if (mouseButtonEvent.button() == 0 && activeCosmeticSlider != null) {
            activeCosmeticSlider = null
            return true
        }
        if (mouseButtonEvent.button() == 0 && activeModulePopupSlider != null) {
            activeModulePopupSlider = null
            return true
        }
        if (mouseButtonEvent.button() == 0 && activeModulePopupColorDrag != null) {
            activeModulePopupColorDrag = null
            return true
        }
        if (mouseButtonEvent.button() == 0 && draggingModuleBrowserCategory != null) {
            draggingModuleBrowserCategory = null
            saveModuleBrowserPanelStates()
            return true
        }
        if (mouseButtonEvent.button() == 0 && draggingModulePopup) {
            draggingModulePopup = false
            return true
        }
        if (mouseButtonEvent.button() == 0 && dragging) {
            dragging = false
            return true
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (currentPage == Page.HUB || textEditor != null || colorPicker != null) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        if (currentPage == Page.CLICK_GUI) {
            modulePopup?.let { popup ->
                if (popup.bounds.contains(mouseX, mouseY)) {
                    val extra = expandedModulePopupExtra
                    if (extra != null && modulePopupExpandedExtraContentRows > modulePopupExpandedExtraVisibleRows) {
                        val maxScroll = modulePopupExpandedExtraContentRows - modulePopupExpandedExtraVisibleRows
                        val next = modulePopupExtraScroll(extra) - verticalAmount.roundToInt()
                        modulePopupExtraScrolls[extra] = next.coerceIn(0, maxScroll)
                    }
                    return true
                }
            }
            val extra = expandedModulePopupExtra
            if (
                extra != null &&
                modulePopupExpandedExtraBounds.contains(mouseX, mouseY) &&
                modulePopupExpandedExtraContentRows > modulePopupExpandedExtraVisibleRows
            ) {
                val maxScroll = modulePopupExpandedExtraContentRows - modulePopupExpandedExtraVisibleRows
                val next = modulePopupExtraScroll(extra) - verticalAmount.roundToInt()
                modulePopupExtraScrolls[extra] = next.coerceIn(0, maxScroll)
                return true
            }
            val header = moduleBrowserHeaderHitEntries.firstOrNull { hit ->
                val state = moduleBrowserPanelState(hit.category)
                val height = moduleBrowserPanelHeight(hit.category)
                mouseX >= state.x && mouseX <= state.x + moduleBrowserPanelWidth(hit.category) && mouseY >= state.y && mouseY <= state.y + height
            } ?: return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
            val state = moduleBrowserPanelState(header.category)
            state.scroll = (state.scroll - (verticalAmount * moduleBrowserRowHeight * 2).roundToInt()).coerceIn(0, moduleBrowserMaxScroll(header.category))
            return true
        }
        if (currentPage == Page.SKIN && skinDropdownOpen && skinDropdownList.contains(mouseX, mouseY)) {
            val maxScroll = max(0, FloydSkin.availableSkinFiles().size - skinDropdownMaxVisible)
            skinDropdownScroll = (skinDropdownScroll - verticalAmount.roundToInt()).coerceIn(0, maxScroll)
            return true
        }
        if (currentPage == Page.CONE_HAT && coneDropdownOpen && coneDropdownList.contains(mouseX, mouseY)) {
            val maxScroll = max(0, FloydConeHat.availableImageFiles().size - coneDropdownMaxVisible)
            coneDropdownScroll = (coneDropdownScroll - verticalAmount.roundToInt()).coerceIn(0, maxScroll)
            return true
        }
        if (currentPage == Page.MOB_ESP_FILTERS) {
            val maxScroll = max(0, mobFilterContentHeight() - mobFilterContentArea().height)
            mobFilterScroll = (mobFilterScroll - (verticalAmount * mobFilterEntryHeight * 3).roundToInt()).coerceIn(0, maxScroll)
            return true
        }
        if (currentPage == Page.XRAY) {
            val maxScroll = max(0, xrayEditorContentHeight() - xrayEditorContentArea().height)
            xrayEditorScroll = (xrayEditorScroll - (verticalAmount * xrayEditorEntryHeight * 3).roundToInt()).coerceIn(0, maxScroll)
            return true
        }
        if (currentPage == Page.NAME_MAPPINGS) {
            val maxScroll = max(0, nameMappingContentHeight() - nameMappingContentArea().height)
            nameMappingScroll = (nameMappingScroll - (verticalAmount * nameMappingRowSpacing * 3).roundToInt()).coerceIn(0, maxScroll)
            return true
        }
        val totalLines = layoutRows(rowsFor(currentPage), 0, 0, 240).maxOfOrNull { it.line + 1 } ?: 0
        val maxScroll = max(0, totalLines - visibleRowCount())
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        pageScroll = (pageScroll - verticalAmount.roundToInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun isPauseScreen(): Boolean = false

    private fun panelWidth(): Int = when (currentPage) {
        Page.HUB -> hubPanelWidth
        Page.SKIN -> skinPanelWidth
        Page.CAPE -> 240
        Page.CONE_HAT -> coneControlsWidth + conePreviewWidth
        Page.MOB_ESP_FILTERS -> mobFilterPanelWidth
        Page.XRAY -> xrayEditorPanelWidth
        Page.NAME_MAPPINGS -> nameMappingPanelWidth
        Page.ANIMATIONS -> animationsPanelWidth
        Page.CAMERA -> cameraPanelWidth
        Page.RENDER -> renderPanelWidth
        Page.MOB_ESP -> mobEspPanelWidth
        Page.COSMETIC -> cosmeticPanelWidth
        Page.PLAYER_SIZE -> cosmeticPanelWidth
        Page.HIDERS -> hidersPanelWidth
        Page.NICK_HIDER -> nickPanelWidth
        Page.GUI_STYLE -> guiStylePanelWidth
        Page.CLICK_GUI -> width
    }

    private fun panelHeight(): Int = when (currentPage) {
        Page.HUB -> hubPanelHeight
        Page.SKIN -> skinPanelHeight
        Page.CAPE -> 140
        Page.CONE_HAT -> 246
        Page.MOB_ESP_FILTERS -> mobFilterPanelHeight
        Page.XRAY -> xrayEditorPanelHeight
        Page.NAME_MAPPINGS -> nameMappingPanelHeight
        Page.RENDER -> renderPanelHeight
        Page.MOB_ESP -> mobEspPanelHeight
        Page.COSMETIC -> cosmeticPanelHeight
        Page.PLAYER_SIZE -> 220
        Page.HIDERS -> hidersPanelHeight
        Page.NICK_HIDER -> nickPanelHeight
        Page.GUI_STYLE -> guiStylePanelHeight
        Page.CLICK_GUI -> height
        Page.ANIMATIONS -> animationsPanelHeight
        Page.CAMERA -> cameraPanelHeight
    }

    private fun openLabel(label: String) {
        currentPage = when (label) {
            "Cosmetic" -> Page.COSMETIC
            "Render" -> Page.RENDER
            "Neck Hider" -> Page.NICK_HIDER
            "Camera" -> Page.CAMERA
            else -> Page.HUB
        }
    }

    private fun drawPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        if (currentPage == Page.CAPE) {
            drawCapePage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.CONE_HAT) {
            drawConeHatPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.SKIN) {
            drawSkinSettingsPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.MOB_ESP_FILTERS) {
            drawMobFilterEditorPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.MOB_ESP) {
            drawMobEspPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.COSMETIC) {
            drawCosmeticPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.XRAY) {
            drawXrayEditorPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.NAME_MAPPINGS) {
            drawNameMappingsEditorPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.ANIMATIONS) {
            drawAnimationsPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.CAMERA) {
            drawCameraPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.RENDER) {
            drawRenderPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.HIDERS) {
            drawHidersPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.NICK_HIDER) {
            drawNickHiderPage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.GUI_STYLE) {
            drawGuiStylePage(context, left, top, bottom, alpha)
            return
        }
        if (currentPage == Page.CLICK_GUI) {
            drawModuleBrowserPage(context, alpha)
            return
        }
        val title = pageTitle(currentPage)
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0.08f), alpha), true)

        capePrevButton = Rect.ZERO
        capeNextButton = Rect.ZERO
        capeOpenFolderButton = Rect.ZERO
        skinSettingsHitEntries = emptyList()
        coneRows = emptyList()
        coneInputBoxes = emptyList()
        coneDropdownButton = Rect.ZERO
        coneDropdownList = Rect.ZERO
        coneOpenFolderButton = Rect.ZERO
        xrayHitEntries = emptyList()
        nameMappingHitEntries = emptyList()
        animationHitEntries = emptyList()
        cameraHitEntries = emptyList()
        renderHitEntries = emptyList()
        mobEspHitEntries = emptyList()
        cosmeticHitEntries = emptyList()
        hidersHitEntries = emptyList()
        nickHiderHitEntries = emptyList()
        val rows = rowsFor(currentPage)
        val contentTop = top + 26
        val contentWidth = 240
        val contentLeft = left + (panelWidth() - contentWidth) / 2
        val laidOutRows = layoutRows(rows, contentLeft, contentTop, contentWidth)
        val totalLines = laidOutRows.maxOfOrNull { it.line + 1 } ?: 0
        pageScroll = pageScroll.coerceIn(0, max(0, totalLines - visibleRowCount()))
        val visibleRows = laidOutRows.filter { it.line in pageScroll until pageScroll + visibleRowCount() }
        val bounds = mutableListOf<HitRow>()
        skinDropdownButton = Rect.ZERO
        skinDropdownList = Rect.ZERO
        for (laidOut in visibleRows) {
            val row = laidOut.row
            val rect = laidOut.bounds
            when (row.kind) {
                RowKind.HEADER -> drawSectionHeader(context, rect, row.label(), alpha)
                RowKind.BUTTON -> {
                    bounds += HitRow(rect, row.label(), row.kind, row.action)
                    if (row.role == RowRole.SKIN_DROPDOWN) skinDropdownButton = rect
                    drawButton(context, rect, row.label(), alpha)
                }
            }
        }
        pageRows = bounds

        if (currentPage == Page.SKIN) {
            drawSkinHint(context, left, bottom, alpha)
            drawSkinDropdown(context, alpha)
        }

        if (totalLines > visibleRowCount()) {
            val indicator = "${pageScroll + 1}-${min(totalLines, pageScroll + visibleRowCount())} / $totalLines"
            context.drawString(mc.font, indicator, left + (panelWidth() - mc.font.width(indicator)) / 2, bottom - 51, applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
        }

        pageBackButton = Rect(left + 96, bottom - 30, 82, buttonHeight)
        pageDoneButton = Rect(left + panelWidth() - 178, bottom - 30, 82, buttonHeight)
        drawButton(context, pageBackButton, "Back", alpha)
        drawButton(context, pageDoneButton, "Done", alpha)
    }

    private fun visibleRowCount(): Int = when (currentPage) {
        Page.HUB -> 0
        else -> max(1, (panelHeight() - 78) / (rowHeight + rowGap))
    }

    private fun layoutRows(rows: List<LegacyRow>, contentLeft: Int, contentTop: Int, contentWidth: Int): List<LaidOutRow> {
        val halfGap = 4
        val halfWidth = (contentWidth - halfGap) / 2
        val laidOut = mutableListOf<LaidOutRow>()
        var line = 0
        var waitingForRight = false

        for (row in rows) {
            when (row.layout) {
                RowLayout.FULL -> {
                    if (waitingForRight) {
                        line++
                        waitingForRight = false
                    }
                    laidOut += LaidOutRow(row, line, Rect(contentLeft, contentTop + line * (rowHeight + rowGap), contentWidth, rowHeight))
                    line++
                }
                RowLayout.LEFT -> {
                    if (waitingForRight) line++
                    laidOut += LaidOutRow(row, line, Rect(contentLeft, contentTop + line * (rowHeight + rowGap), halfWidth, rowHeight))
                    waitingForRight = true
                }
                RowLayout.RIGHT -> {
                    laidOut += LaidOutRow(row, line, Rect(contentLeft + halfWidth + halfGap, contentTop + line * (rowHeight + rowGap), halfWidth, rowHeight))
                    line++
                    waitingForRight = false
                }
            }
        }
        return laidOut
    }

    private fun drawSectionHeader(context: GuiGraphics, rect: Rect, label: String, alpha: Float) {
        val textWidth = mc.font.width(label)
        val textX = rect.left + (rect.width - textWidth) / 2
        val textY = rect.top + (rect.height - mc.font.lineHeight) / 2
        val lineY = rect.top + rect.height / 2
        val lineColor = applyAlpha(0xFF555555.toInt(), alpha)
        context.fill(rect.left, lineY, textX - 4, lineY + 1, lineColor)
        context.fill(textX + textWidth + 4, lineY, rect.right, lineY + 1, lineColor)
        context.drawString(mc.font, label, textX, textY, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun drawTextEditor(context: GuiGraphics, left: Int, top: Int, alpha: Float, editor: TextEditor) {
        val modal = Rect(left + 18, top + 92, panelWidth() - 36, 100)
        context.fill(modal.left, modal.top, modal.right, modal.bottom, applyAlpha(0xDD000000.toInt(), alpha))
        drawChromaBorder(context, modal.left - 1, modal.top - 1, modal.right + 1, modal.bottom + 1, alpha)

        context.drawString(mc.font, editor.title, modal.left + (modal.width - mc.font.width(editor.title)) / 2, modal.top + 8, applyAlpha(chromaColor(0.12f), alpha), true)

        val input = Rect(modal.left + 12, modal.top + 31, modal.width - 24, 20)
        context.fill(input.left, input.top, input.right, input.bottom, applyAlpha(0xFF111111.toInt(), alpha))
        drawChromaBorder(context, input.left - 1, input.top - 1, input.right + 1, input.bottom + 1, alpha)
        val display = editor.value.ifEmpty { editor.placeholder }
        val color = if (editor.value.isEmpty()) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
        val trimmed = mc.font.plainSubstrByWidth(display, input.width - 10)
        context.drawString(mc.font, trimmed, input.left + 5, input.top + (input.height - mc.font.lineHeight) / 2, applyAlpha(color, alpha), true)
        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            val caretX = input.left + 5 + mc.font.width(trimmed)
            context.fill(caretX, input.top + 4, caretX + 1, input.bottom - 4, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        }

        val hint = editor.hint
        context.drawString(mc.font, hint, modal.left + (modal.width - mc.font.width(hint)) / 2, modal.top + 56, applyAlpha(0xFFAAAAAA.toInt(), alpha), true)

        editorSaveButton = Rect(modal.left + 56, modal.bottom - 24, 72, buttonHeight)
        editorCancelButton = Rect(modal.right - 128, modal.bottom - 24, 72, buttonHeight)
        drawButton(context, editorSaveButton, "Save", alpha)
        drawButton(context, editorCancelButton, "Cancel", alpha)
    }

    private fun drawColorPicker(context: GuiGraphics, alpha: Float, picker: ColorPickerEditor) {
        val modal = colorPickerRect()
        context.fill(modal.left, modal.top, modal.right, modal.bottom, applyAlpha(0xCC000000.toInt(), alpha))
        drawChromaBorder(context, modal.left - 1, modal.top - 1, modal.right + 1, modal.bottom + 1, alpha)

        val title = "${picker.title} Picker"
        context.drawString(mc.font, title, modal.left + (modal.width - mc.font.width(title)) / 2, modal.top + 6, applyAlpha(chromaColor(0f), alpha), true)

        drawColorPickerSv(context, picker, alpha)
        drawColorPickerHue(context, picker, alpha)
        drawColorPickerPreview(context, picker, alpha)
        drawColorPickerHex(context, picker, alpha)

        drawButton(context, colorPickerChromaButton(), "Chroma: ${onOff(picker.chromaEnabled)}", alpha)
        if (picker.fadeSupported) {
            drawButton(context, colorPickerFadeButton(), "Fade: ${onOff(picker.fadeEnabled)}", alpha)
            drawButton(context, colorPickerEditTargetButton(), if (picker.editingFade) "Editing: Fade" else "Editing: Base", alpha)
        }
        drawButton(context, colorPickerApplyButton(), "Apply", alpha)
        drawButton(context, colorPickerCancelButton(), "Cancel", alpha)
    }

    private fun drawColorPickerSv(context: GuiGraphics, picker: ColorPickerEditor, alpha: Float) {
        val rect = colorPickerSvRect()
        if (picker.chromaEnabled) {
            context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(0xFF222222.toInt(), alpha))
            context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(chromaColor(0f), alpha * 0.15f))
        } else {
            for (x in 0 until rect.width) {
                val saturation = x / (rect.width - 1).toFloat()
                val topColor = applyAlpha(HSBtoRGB(picker.activeHue(), saturation, 1f) or 0xFF000000.toInt(), alpha)
                context.fillGradient(rect.left + x, rect.top, rect.left + x + 1, rect.bottom, topColor, applyAlpha(0xFF000000.toInt(), alpha))
            }
            val markerX = rect.left + (picker.activeSaturation() * (rect.width - 1)).roundToInt()
            val markerY = rect.top + ((1f - picker.activeBrightness()) * (rect.height - 1)).roundToInt()
            context.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, applyAlpha(0xFF000000.toInt(), alpha))
            context.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        }
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
    }

    private fun drawColorPickerHue(context: GuiGraphics, picker: ColorPickerEditor, alpha: Float) {
        val rect = colorPickerHueRect()
        if (picker.chromaEnabled) {
            context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(0xFF222222.toInt(), alpha))
            context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(chromaColor(0f), alpha * 0.15f))
        } else {
            for (y in 0 until rect.height) {
                val hue = y / (rect.height - 1).toFloat()
                context.fill(rect.left, rect.top + y, rect.right, rect.top + y + 1, applyAlpha(HSBtoRGB(hue, 1f, 1f) or 0xFF000000.toInt(), alpha))
            }
            val markerY = rect.top + (picker.activeHue() * (rect.height - 1)).roundToInt()
            context.fill(rect.left - 2, markerY - 1, rect.right + 2, markerY + 2, applyAlpha(0xFF000000.toInt(), alpha))
            context.fill(rect.left - 1, markerY, rect.right + 1, markerY + 1, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        }
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
    }

    private fun drawColorPickerPreview(context: GuiGraphics, picker: ColorPickerEditor, alpha: Float) {
        val rect = colorPickerPreviewRect()
        val color = if (picker.chromaEnabled) chromaColor(0f) else picker.currentColor()
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(color, alpha))
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
        if (picker.fadeSupported) {
            val base = colorPickerBasePreviewRect()
            val fade = colorPickerFadePreviewRect()
            context.fill(base.left, base.top, base.right, base.bottom, applyAlpha(picker.baseColor(), alpha))
            drawChromaBorder(context, base.left - 1, base.top - 1, base.right + 1, base.bottom + 1, alpha)
            context.fill(fade.left, fade.top, fade.right, fade.bottom, applyAlpha(picker.fadeColor(), alpha))
            drawChromaBorder(context, fade.left - 1, fade.top - 1, fade.right + 1, fade.bottom + 1, alpha)
            val label = if (picker.editingFade) "Editing Fade" else "Editing Base"
            context.drawString(mc.font, label, fade.right + colorPickerPreviewGap, fade.top + (fade.height - mc.font.lineHeight) / 2, legacyTextColor(0f, alpha), true)
        }
    }

    private fun drawColorPickerHex(context: GuiGraphics, picker: ColorPickerEditor, alpha: Float) {
        val rect = colorPickerHexRect()
        val hashWidth = mc.font.width("#") + 4
        context.fill(rect.left - hashWidth - 2, rect.top - 2, rect.right + 2, rect.bottom + 2, applyAlpha(0xFF000000.toInt(), alpha))
        drawChromaBorder(context, rect.left - hashWidth - 3, rect.top - 3, rect.right + 3, rect.bottom + 3, alpha)
        context.drawString(mc.font, "#", rect.left - hashWidth + 2, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(0xFF888888.toInt(), alpha), true)
        val display = picker.hex.ifBlank { "RRGGBB" }
        val color = if (picker.hex.isBlank()) 0xFF777777.toInt() else 0xFFFFFFFF.toInt()
        context.drawString(mc.font, display, rect.left + 2, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(color, alpha), true)
        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            val caretX = rect.left + 2 + mc.font.width(picker.hex)
            context.fill(caretX, rect.top + 3, caretX + 1, rect.bottom - 3, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        }
    }

    private fun drawSkinHint(context: GuiGraphics, left: Int, bottom: Int, alpha: Float) {
        val hint = "Drop any .png skin file into the skin folder."
        context.drawString(
            mc.font,
            hint,
            left + (panelWidth() - mc.font.width(hint)) / 2,
            bottom - 52,
            applyAlpha(0xFF888888.toInt(), alpha),
            true
        )
    }

    private fun drawSkinSettingsPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<SkinSettingsHitEntry>()
        val controlLeft = left + (panelWidth() - skinControlWidth) / 2
        val controlTop = top + 25

        fun button(row: Int, label: String, settingName: String, kind: SkinSettingsHitKind) {
            val rect = Rect(controlLeft, controlTop + row * skinRowSpacing, skinControlWidth, skinRowHeight)
            drawButton(context, rect, label, alpha)
            hits += SkinSettingsHitEntry(rect, settingName, kind)
        }

        button(0, "Custom Skin: ${onOff(booleanSetting(FloydSkin, "Custom Skin")?.enabled ?: false)}", "Custom Skin", SkinSettingsHitKind.TOGGLE)
        button(1, "Apply to me: ${onOff(booleanSetting(FloydSkin, "Self")?.enabled ?: false)}", "Self", SkinSettingsHitKind.TOGGLE)
        button(2, "Others: ${onOff(booleanSetting(FloydSkin, "Others")?.enabled ?: false)}", "Others", SkinSettingsHitKind.TOGGLE)
        button(3, "Open skin folder", "Open Skin Folder", SkinSettingsHitKind.OPEN_FOLDER)

        skinDropdownButton = Rect(controlLeft, controlTop + skinRowSpacing * 4, skinControlWidth, 16)
        drawSkinDropdownButton(context, alpha)
        hits += SkinSettingsHitEntry(skinDropdownButton, "Skin", SkinSettingsHitKind.DROPDOWN)

        drawSkinDropdown(context, alpha)

        val hint = "Drop any .png skin file into the skin folder."
        context.drawString(mc.font, hint, left + (panelWidth() - mc.font.width(hint)) / 2, bottom - 50, applyAlpha(0xFF888888.toInt(), alpha), true)

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, skinRowHeight)
        drawButton(context, pageDoneButton, "Done", alpha)

        skinSettingsHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawSkinDropdownButton(context: GuiGraphics, alpha: Float) {
        val hovered = skinDropdownButton.contains(hoverX, hoverY)
        context.fill(skinDropdownButton.left, skinDropdownButton.top, skinDropdownButton.right, skinDropdownButton.bottom, applyAlpha(if (hovered) 0xFF444444.toInt() else 0xFF333333.toInt(), alpha))
        drawChromaBorder(context, skinDropdownButton.left - 1, skinDropdownButton.top - 1, skinDropdownButton.right + 1, skinDropdownButton.bottom + 1, alpha)

        val selected = stringSetting(FloydSkin, "Skin")?.value?.ifBlank { "No skin selected" } ?: "No skin selected"
        val arrow = if (skinDropdownOpen) " ^" else " v"
        val display = mc.font.plainSubstrByWidth(selected, skinDropdownButton.width - mc.font.width(arrow) - 8) + arrow
        context.drawString(mc.font, display, skinDropdownButton.left + (skinDropdownButton.width - mc.font.width(display)) / 2, skinDropdownButton.top + (skinDropdownButton.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun drawSkinDropdown(context: GuiGraphics, alpha: Float) {
        if (skinDropdownButton == Rect.ZERO) {
            skinDropdownOpen = false
            return
        }
        val skins = FloydSkin.availableSkinFiles()
        skinDropdownScroll = skinDropdownScroll.coerceIn(0, max(0, skins.size - skinDropdownMaxVisible))
        if (!skinDropdownOpen) return

        val visibleCount = min(if (skins.isEmpty()) 1 else skins.size, skinDropdownMaxVisible)
        val listTop = skinDropdownButton.bottom + 2
        skinDropdownList = Rect(skinDropdownButton.left, listTop, skinDropdownButton.width, visibleCount * skinDropdownRowHeight)
        context.fill(skinDropdownList.left, skinDropdownList.top, skinDropdownList.right, skinDropdownList.bottom, applyAlpha(0xDD000000.toInt(), alpha))
        drawChromaBorder(context, skinDropdownList.left - 1, skinDropdownList.top - 1, skinDropdownList.right + 1, skinDropdownList.bottom + 1, alpha)

        if (skins.isEmpty()) {
            val empty = "No skins found"
            context.drawString(
                mc.font,
                empty,
                skinDropdownList.left + (skinDropdownList.width - mc.font.width(empty)) / 2,
                skinDropdownList.top + (skinDropdownRowHeight - mc.font.lineHeight) / 2,
                applyAlpha(0xFF888888.toInt(), alpha),
                true
            )
            return
        }

        val selected = FloydSkin.selectedSkin
        for ((visibleIndex, skin) in skinDropdownVisibleItems().withIndex()) {
            val rowTop = skinDropdownList.top + visibleIndex * skinDropdownRowHeight
            val row = Rect(skinDropdownList.left, rowTop, skinDropdownList.width, skinDropdownRowHeight)
            val hovered = row.contains(hoverX, hoverY)
            val active = skin.equals(selected, ignoreCase = true)
            when {
                hovered -> context.fill(row.left + 1, row.top, row.right - 1, row.bottom, applyAlpha(0xFF555555.toInt(), alpha))
                active -> context.fill(row.left + 1, row.top, row.right - 1, row.bottom, applyAlpha(0xFF3A3A3A.toInt(), alpha))
            }
            val display = mc.font.plainSubstrByWidth(skin, row.width - 10)
            val color = if (active) chromaColor(0f) else 0xFFCCCCCC.toInt()
            context.drawString(mc.font, display, row.left + 5, row.top + (row.height - mc.font.lineHeight) / 2, applyAlpha(color, alpha), true)
        }

        if (skins.size > skinDropdownMaxVisible) {
            val scrollHeight = max(4, (skinDropdownMaxVisible * skinDropdownList.height) / skins.size)
            val denominator = max(1, skins.size - skinDropdownMaxVisible)
            val scrollY = skinDropdownList.top + (skinDropdownScroll * (skinDropdownList.height - scrollHeight)) / denominator
            context.fill(skinDropdownList.right - 3, scrollY, skinDropdownList.right - 1, scrollY + scrollHeight, applyAlpha(0xFF888888.toInt(), alpha))
        }
    }

    private fun drawCapePage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val title = "Cape"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 14, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)

        val rowY = top + 50
        capePrevButton = Rect(left + 16, rowY, 30, 18)
        capeNextButton = Rect(left + panelWidth() - 46, rowY, 30, 18)
        capeOpenFolderButton = Rect(left + (panelWidth() - 110) / 2, rowY + 26, 110, 18)

        val capes = FloydCape.availableCapeFiles()
        val current = FloydCape.selectedCape.takeIf { it.isNotBlank() } ?: capes.firstOrNull() ?: "None"
        val display = mc.font.plainSubstrByWidth(current, panelWidth() - 104)
        context.drawString(
            mc.font,
            display,
            left + (panelWidth() - mc.font.width(display)) / 2,
            rowY + (18 - mc.font.lineHeight) / 2,
            applyAlpha(0xFFFFFFFF.toInt(), alpha),
            true
        )

        drawButton(context, capePrevButton, "<", alpha)
        drawButton(context, capeNextButton, ">", alpha)
        drawButton(context, capeOpenFolderButton, "Open Folder", alpha)

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 80) / 2, bottom - 28, 80, 18)
        drawButton(context, pageDoneButton, "Done", alpha)
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawConeHatPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val divX = left + coneControlsWidth
        drawChromaBorder(context, divX - 1, top - 1, divX, bottom + 1, alpha)

        val title = "Cone Hat Config"
        context.drawString(mc.font, title, left + (coneControlsWidth - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)

        val specs = coneSpecs()
        val controlLeft = left + (coneControlsWidth - coneFullControlWidth) / 2
        val rows = mutableListOf<ConeControlRow>()
        val inputs = mutableListOf<Rect>()
        for ((index, spec) in specs.withIndex()) {
            val y = coneRowY(top, index)
            val slider = Rect(controlLeft, y, coneSliderWidth, 20)
            val input = Rect(slider.right + coneInputGap, y, coneInputWidth, 20)
            rows += ConeControlRow(spec, slider, input)
            inputs += input
            drawConeSliderRow(context, index, spec, slider, input, alpha)
        }
        coneRows = rows
        coneInputBoxes = inputs

        coneDropdownButton = Rect(controlLeft, coneRowY(top, specs.size), coneDropdownWidth, 20)
        coneOpenFolderButton = Rect(coneDropdownButton.right + coneInputGap, coneDropdownButton.top, coneFullControlWidth - coneDropdownWidth - coneInputGap, 20)
        drawConeDropdownButton(context, alpha)
        drawButton(context, coneOpenFolderButton, "Open folder", alpha)

        val hint = "Drop .png into cone-hats folder."
        context.drawString(mc.font, hint, left + (coneControlsWidth - mc.font.width(hint)) / 2, bottom - 48, applyAlpha(0xFF888888.toInt(), alpha), true)

        val preview = "Preview"
        context.drawString(mc.font, preview, divX + (conePreviewWidth - mc.font.width(preview)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)
        mc.player?.let { player ->
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                context,
                divX + 4,
                top + 20,
                left + panelWidth() - 4,
                bottom - 35,
                35,
                0.0625f,
                hoverX.toFloat(),
                hoverY.toFloat(),
                player
            )
        }

        if (coneDropdownOpen) drawConeDropdownList(context, alpha)

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (coneControlsWidth - 100) / 2, bottom - 30, 100, 20)
        drawButton(context, pageDoneButton, "Done", alpha)
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawConeSliderRow(context: GuiGraphics, index: Int, spec: ConeSpec, slider: Rect, input: Rect, alpha: Float) {
        val value = numberSetting(FloydConeHat, spec.settingName)?.numericValue() ?: spec.min
        val percent = ((value - spec.min) / (spec.max - spec.min)).coerceIn(0.0, 1.0)
        val hovered = slider.contains(hoverX, hoverY)
        context.fill(slider.left, slider.top, slider.right, slider.bottom, applyAlpha(if (hovered) 0xFF666666.toInt() else 0xFF555555.toInt(), alpha))
        val fillWidth = ((slider.width - 4) * percent).roundToInt()
        context.fill(slider.left + 2, slider.top + 2, slider.left + 2 + fillWidth, slider.bottom - 2, applyAlpha(0xFF888888.toInt(), alpha))
        drawChromaBorder(context, slider.left - 1, slider.top - 1, slider.right + 1, slider.bottom + 1, alpha)
        val label = "${spec.label}: ${spec.format(value)}"
        context.drawString(mc.font, label, slider.left + (slider.width - mc.font.width(label)) / 2, slider.top + (slider.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)

        val editing = coneEditingIndex == index
        context.fill(input.left, input.top, input.right, input.bottom, applyAlpha(if (editing) 0xFF222222.toInt() else 0xFF333333.toInt(), alpha))
        if (editing) drawChromaBorder(context, input.left - 1, input.top - 1, input.right + 1, input.bottom + 1, alpha)
        else drawThinBorder(context, input, applyAlpha(0xFF555555.toInt(), alpha))
        val text = if (editing) coneEditBuffer else spec.inputFormat(value)
        val display = mc.font.plainSubstrByWidth(text, input.width - 6)
        context.drawString(mc.font, display, input.right - 4 - mc.font.width(display), input.top + (input.height - mc.font.lineHeight) / 2, applyAlpha(0xFFCCCCCC.toInt(), alpha), true)
        if (editing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            val cursorX = input.right - 4
            context.fill(cursorX, input.top + 3, cursorX + 1, input.bottom - 3, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        }
    }

    private fun drawConeDropdownButton(context: GuiGraphics, alpha: Float) {
        val hovered = coneDropdownButton.contains(hoverX, hoverY)
        context.fill(coneDropdownButton.left, coneDropdownButton.top, coneDropdownButton.right, coneDropdownButton.bottom, applyAlpha(if (hovered) 0xFF444444.toInt() else 0xFF333333.toInt(), alpha))
        drawChromaBorder(context, coneDropdownButton.left - 1, coneDropdownButton.top - 1, coneDropdownButton.right + 1, coneDropdownButton.bottom + 1, alpha)
        val selected = FloydConeHat.selectedImage.ifBlank { "Default (Floyd.png)" }
        val arrow = if (coneDropdownOpen) " ^" else " v"
        val display = mc.font.plainSubstrByWidth(selected, coneDropdownButton.width - mc.font.width(arrow) - 8) + arrow
        context.drawString(mc.font, display, coneDropdownButton.left + (coneDropdownButton.width - mc.font.width(display)) / 2, coneDropdownButton.top + (coneDropdownButton.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun drawConeDropdownList(context: GuiGraphics, alpha: Float) {
        val images = FloydConeHat.availableImageFiles()
        coneDropdownScroll = coneDropdownScroll.coerceIn(0, max(0, images.size - coneDropdownMaxVisible))
        val visibleCount = min(if (images.isEmpty()) 1 else images.size, coneDropdownMaxVisible)
        coneDropdownList = Rect(coneDropdownButton.left, coneDropdownButton.bottom + 2, coneDropdownButton.width, visibleCount * coneDropdownRowHeight)
        context.fill(coneDropdownList.left, coneDropdownList.top, coneDropdownList.right, coneDropdownList.bottom, applyAlpha(0xEE000000.toInt(), alpha))
        drawChromaBorder(context, coneDropdownList.left - 1, coneDropdownList.top - 1, coneDropdownList.right + 1, coneDropdownList.bottom + 1, alpha)
        if (images.isEmpty()) {
            val empty = "No images found"
            context.drawString(mc.font, empty, coneDropdownList.left + (coneDropdownList.width - mc.font.width(empty)) / 2, coneDropdownList.top + (coneDropdownRowHeight - mc.font.lineHeight) / 2, applyAlpha(0xFF888888.toInt(), alpha), true)
            return
        }
        for ((visibleIndex, image) in coneDropdownVisibleItems().withIndex()) {
            val row = Rect(coneDropdownList.left, coneDropdownList.top + visibleIndex * coneDropdownRowHeight, coneDropdownList.width, coneDropdownRowHeight)
            val selected = image.equals(FloydConeHat.selectedImage, ignoreCase = true)
            when {
                row.contains(hoverX, hoverY) -> context.fill(row.left + 1, row.top, row.right - 1, row.bottom, applyAlpha(0xFF555555.toInt(), alpha))
                selected -> context.fill(row.left + 1, row.top, row.right - 1, row.bottom, applyAlpha(0xFF3A3A3A.toInt(), alpha))
            }
            val display = mc.font.plainSubstrByWidth(image, row.width - 10)
            context.drawString(mc.font, display, row.left + 5, row.top + (row.height - mc.font.lineHeight) / 2, applyAlpha(if (selected) chromaColor(0f) else 0xFFCCCCCC.toInt(), alpha), true)
        }
    }

    private fun drawThinBorder(context: GuiGraphics, rect: Rect, color: Int) {
        context.fill(rect.left, rect.top, rect.right, rect.top + 1, color)
        context.fill(rect.left, rect.bottom - 1, rect.right, rect.bottom, color)
        context.fill(rect.left, rect.top, rect.left + 1, rect.bottom, color)
        context.fill(rect.right - 1, rect.top, rect.right, rect.bottom, color)
    }

    private fun drawMobFilterEditorPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val title = "Mob ESP Filters"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)

        val content = mobFilterContentArea()
        val maxScroll = max(0, mobFilterContentHeight() - content.height)
        mobFilterScroll = mobFilterScroll.coerceIn(0, maxScroll)
        val hits = mutableListOf<MobFilterHitEntry>()
        var y = content.top + 4 - mobFilterScroll

        val addGap = 6
        val addWidth = (content.width - addGap - 8) / 2
        val addName = Rect(content.left + 4, y, addWidth, mobFilterButtonHeight)
        val addType = Rect(addName.right + addGap, y, addWidth, mobFilterButtonHeight)
        if (addName.bottom >= content.top && addName.top <= content.bottom) {
            drawButton(context, addName, "Add Name...", alpha)
            hits += MobFilterHitEntry(addName, "", MobFilterHitKind.ADD_MANUAL_NAME)
        }
        if (addType.bottom >= content.top && addType.top <= content.bottom) {
            drawButton(context, addType, "Add Type...", alpha)
            hits += MobFilterHitEntry(addType, "", MobFilterHitKind.ADD_MANUAL_TYPE)
        }
        y += mobFilterEntryHeight + 4

        fun header(label: String, offset: Float = 0f) {
            context.drawString(mc.font, label, content.left + 2, y + 2, applyAlpha(chromaColor(offset), alpha), true)
            y += mobFilterEntryHeight
        }

        val activeNames = FloydMobEsp.nameFilterIds().toList()
        val activeTypes = FloydMobEsp.typeFilterIds().toList()
        if (activeNames.isNotEmpty()) {
            header("Active Names", 0f)
            for (name in activeNames) y = drawMobFilterEntry(context, content, y, name, MobFilterHitKind.REMOVE_NAME, "-", alpha, hits, colorTarget = MobFilterColorTarget(name, true))
        }
        if (activeTypes.isNotEmpty()) {
            y += 4
            header("Active Types", 0.15f)
            for (type in activeTypes) y = drawMobFilterEntry(context, content, y, type, MobFilterHitKind.REMOVE_TYPE, "-", alpha, hits, colorTarget = MobFilterColorTarget(type, false))
        }

        y += 8
        header("Nearby Entities", 0.3f)
        for (name in FloydMobEsp.nearbyNameSuggestions()) y = drawMobFilterEntry(context, content, y, name, MobFilterHitKind.ADD_NAME, "+", alpha, hits)

        y += 4
        header("Nearby Types", 0.45f)
        for (type in FloydMobEsp.nearbyTypeSuggestions()) y = drawMobFilterEntry(context, content, y, type, MobFilterHitKind.ADD_TYPE, "+", alpha, hits)

        mobFilterHitEntries = hits
        if (maxScroll > 0) {
            val scrollHeight = max(10, content.height * content.height / mobFilterContentHeight())
            val scrollY = content.top + (mobFilterScroll * (content.height - scrollHeight)) / max(1, maxScroll)
            context.fill(content.right - 3, scrollY, content.right - 1, scrollY + scrollHeight, applyAlpha(0xFF888888.toInt(), alpha))
        }

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, 20)
        drawButton(context, pageDoneButton, "Done", alpha)
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawMobEspPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<MobEspHitEntry>()
        val controlLeft = left + (panelWidth() - mobEspFullWidth) / 2

        drawMobEspHeader(context, controlLeft, mobEspRowY(top, 0), "Toggles", alpha)
        drawMobEspFullButton(context, hits, controlLeft, top, 1, "Tracers: ${onOff(booleanSetting(FloydMobEsp, "Tracers")?.enabled ?: false)}", "Tracers", MobEspHitKind.TOGGLE, alpha)
        drawMobEspFullButton(context, hits, controlLeft, top, 2, "Hitboxes: ${onOff(booleanSetting(FloydMobEsp, "Hitboxes")?.enabled ?: false)}", "Hitboxes", MobEspHitKind.TOGGLE, alpha)
        drawMobEspFullButton(context, hits, controlLeft, top, 3, "Star Mobs (*): ${onOff(booleanSetting(FloydMobEsp, "Star Mobs")?.enabled ?: false)}", "Star Mobs", MobEspHitKind.TOGGLE, alpha)

        drawMobEspHeader(context, controlLeft, mobEspRowY(top, 4), "Colors", alpha)
        drawMobEspColorRow(context, hits, controlLeft, mobEspRowY(top, 5), "Default ESP Color", "Default ESP Color", "Default Chroma", alpha)
        drawMobEspColorRow(context, hits, controlLeft, mobEspRowY(top, 6), "Stalk Tracer Color", "Tracer Color", "Stalk Chroma", alpha)

        drawMobEspFullButton(context, hits, controlLeft, top, 7, "Edit Filters", "Edit Filters", MobEspHitKind.NAV_FILTERS, alpha)

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, mobEspRowHeight)
        drawButton(context, pageDoneButton, "Done", alpha)
        val title = "Mob ESP Config"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)

        mobEspHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawMobEspFullButton(context: GuiGraphics, hits: MutableList<MobEspHitEntry>, left: Int, top: Int, row: Int, label: String, settingName: String, kind: MobEspHitKind, alpha: Float) {
        val rect = Rect(left, mobEspRowY(top, row), mobEspFullWidth, mobEspRowHeight)
        drawButton(context, rect, label, alpha)
        hits += MobEspHitEntry(rect, settingName, kind)
    }

    private fun drawMobEspColorRow(context: GuiGraphics, hits: MutableList<MobEspHitEntry>, left: Int, y: Int, label: String, settingName: String, chromaSettingName: String, alpha: Float) {
        context.drawString(mc.font, label, left, y + (mobEspRowHeight - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)

        val preview = Rect(left + mobEspFullWidth - mobEspSecondaryWidth - mobEspColorPreviewSize - mobEspPairGap, y + (mobEspRowHeight - mobEspColorPreviewSize) / 2, mobEspColorPreviewSize, mobEspColorPreviewSize)
        val chroma = booleanSetting(FloydMobEsp, chromaSettingName)?.enabled ?: false
        val color = if (chroma) chromaColor(0f) else colorSetting(FloydMobEsp, settingName)?.value?.rgba ?: 0xFFFFFFFF.toInt()
        context.fill(preview.left, preview.top, preview.right, preview.bottom, applyAlpha(color, alpha))
        drawThinBorder(context, preview, applyAlpha(0xFFAAAAAA.toInt(), alpha))

        val pick = Rect(left + mobEspFullWidth - mobEspSecondaryWidth, y, mobEspSecondaryWidth, mobEspRowHeight)
        drawButton(context, pick, "Pick", alpha)
        hits += MobEspHitEntry(pick, settingName, MobEspHitKind.COLOR_PICK)
    }

    private fun drawMobEspHeader(context: GuiGraphics, left: Int, y: Int, text: String, alpha: Float) {
        val textWidth = mc.font.width(text)
        val x = left + (mobEspFullWidth - textWidth) / 2
        val textY = y + (mobEspRowHeight - mc.font.lineHeight) / 2
        val lineY = textY + mc.font.lineHeight / 2
        val color = applyAlpha(0xFF555555.toInt(), alpha)
        context.fill(left, lineY, x - 4, lineY + 1, color)
        context.fill(x + textWidth + 4, lineY, left + mobEspFullWidth, lineY + 1, color)
        context.drawString(mc.font, text, x, textY, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun mobEspRowY(top: Int, row: Int): Int = top + 26 + row * mobEspRowSpacing

    private fun drawCosmeticPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<CosmeticHitEntry>()
        val controlLeft = left + (panelWidth() - cosmeticControlWidth) / 2
        val baseTop = top + 36

        drawCosmeticHeader(context, controlLeft, baseTop, "Cosmetics", alpha)
        drawCosmeticPair(context, hits, controlLeft, baseTop, 1, "Custom Skin: ${onOff(booleanSetting(FloydSkin, "Custom Skin")?.enabled ?: false)}", "Custom Skin", CosmeticHitKind.TOGGLE_SKIN, Page.SKIN, alpha)
        drawCosmeticPair(context, hits, controlLeft, baseTop, 2, "Cape: ${onOff(booleanSetting(FloydCape, "Enabled")?.enabled ?: false)}", "Cape", CosmeticHitKind.TOGGLE_CAPE, Page.CAPE, alpha)
        drawCosmeticPair(context, hits, controlLeft, baseTop, 3, "Cone Hat: ${onOff(booleanSetting(FloydConeHat, "Enabled")?.enabled ?: false)}", "Cone Hat", CosmeticHitKind.TOGGLE_CONE, Page.CONE_HAT, alpha)

        drawCosmeticHeader(context, controlLeft, baseTop + cosmeticRowSpacing * 4 + 8, "Player Size", alpha)
        val target = Rect(controlLeft, baseTop + cosmeticRowSpacing * 5, cosmeticControlWidth, cosmeticRowHeight)
        drawButton(context, target, "Target: ${playerSizeTargetDisplay()}", alpha)
        hits += CosmeticHitEntry(target, "Target", CosmeticHitKind.TARGET)

        cosmeticSliderSpecs().forEachIndexed { index, spec ->
            val rect = Rect(controlLeft, baseTop + cosmeticRowSpacing * (index + 6), cosmeticControlWidth, cosmeticRowHeight)
            drawCosmeticSlider(context, rect, spec, alpha)
            hits += CosmeticHitEntry(rect, spec.settingName, CosmeticHitKind.SLIDER)
        }

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 24, 100, 20)
        drawButton(context, pageDoneButton, "Done", alpha)

        cosmeticHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawCosmeticPair(context: GuiGraphics, hits: MutableList<CosmeticHitEntry>, left: Int, top: Int, row: Int, label: String, settingName: String, kind: CosmeticHitKind, configPage: Page, alpha: Float) {
        val y = top + cosmeticRowSpacing * row
        val main = Rect(left, y, cosmeticMainWidth, cosmeticRowHeight)
        val config = Rect(left + cosmeticMainWidth + cosmeticPairGap, y, cosmeticSecondaryWidth, cosmeticRowHeight)
        drawButton(context, main, label, alpha)
        drawButton(context, config, "Config", alpha)
        hits += CosmeticHitEntry(main, settingName, kind)
        hits += CosmeticHitEntry(config, configPage.name, CosmeticHitKind.NAV_CONFIG)
    }

    private fun drawCosmeticHeader(context: GuiGraphics, left: Int, y: Int, text: String, alpha: Float) {
        val textWidth = mc.font.width(text)
        val x = left + (cosmeticControlWidth - textWidth) / 2
        val textY = y + (cosmeticRowHeight - mc.font.lineHeight) / 2
        val lineY = textY + mc.font.lineHeight / 2
        val color = applyAlpha(0xFF555555.toInt(), alpha)
        context.fill(left, lineY, x - 4, lineY + 1, color)
        context.fill(x + textWidth + 4, lineY, left + cosmeticControlWidth, lineY + 1, color)
        context.drawString(mc.font, text, x, textY, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun drawCosmeticSlider(context: GuiGraphics, rect: Rect, spec: CosmeticSliderSpec, alpha: Float) {
        val value = numberSetting(FloydPlayerSize, spec.settingName)?.numericValue() ?: 1.0
        val pct = ((value - spec.min) / (spec.max - spec.min)).coerceIn(0.0, 1.0)
        val hovered = rect.contains(hoverX, hoverY)
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(if (hovered) 0xFF666666.toInt() else 0xFF555555.toInt(), alpha))
        val fillWidth = ((rect.width - 4) * pct).roundToInt()
        context.fill(rect.left + 2, rect.top + 2, rect.left + 2 + fillWidth, rect.bottom - 2, applyAlpha(0xFF888888.toInt(), alpha))
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
        val label = "${spec.label}: ${oneDecimal(value)}"
        context.drawString(mc.font, label, rect.left + (rect.width - mc.font.width(label)) / 2, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun cosmeticSliderSpecs(): List<CosmeticSliderSpec> = listOf(
        CosmeticSliderSpec("X", "Size X", -1.0, 5.0),
        CosmeticSliderSpec("Y", "Size Y", -1.0, 5.0),
        CosmeticSliderSpec("Z", "Size Z", -1.0, 5.0)
    )

    private fun playerSizeTargetDisplay(): String {
        val labels = listOf("Self", "Real Players", "All")
        return labels.getOrNull(selectorSetting(FloydPlayerSize, "Target")?.value ?: 0) ?: "Self"
    }

    private fun drawMobFilterEntry(
        context: GuiGraphics,
        content: Rect,
        y: Int,
        key: String,
        kind: MobFilterHitKind,
        buttonLabel: String,
        alpha: Float,
        hits: MutableList<MobFilterHitEntry>,
        colorTarget: MobFilterColorTarget? = null,
        rowHeight: Int = mobFilterEntryHeight,
        buttonHeight: Int = mobFilterButtonHeight
    ): Int {
        val visible = y + rowHeight >= content.top && y <= content.bottom
        if (visible) {
            val button = Rect(content.right - mobFilterButtonWidth - 2, y + (rowHeight - buttonHeight) / 2, mobFilterButtonWidth, buttonHeight)
            val buttonColor = when {
                kind == MobFilterHitKind.ADD_NAME || kind == MobFilterHitKind.ADD_TYPE -> if (button.contains(hoverX, hoverY)) 0xFF339933.toInt() else 0xFF337733.toInt()
                else -> if (button.contains(hoverX, hoverY)) 0xFF993333.toInt() else 0xFF773333.toInt()
            }
            context.fill(button.left, button.top, button.right, button.bottom, applyAlpha(buttonColor, alpha))
            drawThinBorder(context, button, applyAlpha(0xFFAAAAAA.toInt(), alpha))
            context.drawString(mc.font, buttonLabel, button.left + (button.width - mc.font.width(buttonLabel)) / 2, button.top + (button.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
            hits += MobFilterHitEntry(button, key, kind)

            val iconSize = min(16, rowHeight)
            val icon = Rect(content.left + 2, y + (rowHeight - iconSize) / 2, iconSize, iconSize)
            drawMobFilterIcon(context, icon, key, kind, alpha)

            val textLeft = if (colorTarget != null) {
                val colorSquare = Rect(icon.right + 4, y + (rowHeight - mobFilterColorSquareSize) / 2, mobFilterColorSquareSize, mobFilterColorSquareSize)
                val color = mobFilterInlineColor(colorTarget)
                context.fill(colorSquare.left, colorSquare.top, colorSquare.right, colorSquare.bottom, applyAlpha(if (color.chroma) chromaColor(0f) else color.argb, alpha))
                drawThinBorder(context, colorSquare, applyAlpha(0xFFAAAAAA.toInt(), alpha))
                hits += MobFilterHitEntry(colorSquare, key, MobFilterHitKind.COLOR)
                colorSquare.right + 4
            } else {
                icon.right + 4
            }
            val maxTextWidth = button.left - textLeft - 4
            val text = mc.font.plainSubstrByWidth(key, maxTextWidth)
            context.drawString(mc.font, text, textLeft, y + (rowHeight - mc.font.lineHeight) / 2, applyAlpha(0xFFCCCCCC.toInt(), alpha), true)
        }
        val nextY = y + rowHeight
        return if (colorTarget != null && expandedMobFilterColor == colorTarget) {
            drawMobFilterInlineColorPicker(context, content, nextY, colorTarget, alpha, hits)
        } else {
            nextY
        }
    }

    private fun drawMobFilterIcon(context: GuiGraphics, rect: Rect, key: String, kind: MobFilterHitKind, alpha: Float) {
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(0xFF333333.toInt(), alpha))
        drawThinBorder(context, rect, applyAlpha(0xFF555555.toInt(), alpha))
        val stack = when (kind) {
            MobFilterHitKind.ADD_TYPE, MobFilterHitKind.REMOVE_TYPE -> {
                val entityType = runCatching { BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(key)) }.getOrNull()
                entityType?.let { SpawnEggItem.byId(it)?.defaultInstance } ?: ItemStack(Items.PLAYER_HEAD)
            }
            else -> ItemStack(Items.PLAYER_HEAD)
        }
        context.renderItem(stack, rect.left, rect.top)
    }

    private fun drawMobFilterInlineColorPicker(
        context: GuiGraphics,
        content: Rect,
        y: Int,
        target: MobFilterColorTarget,
        alpha: Float,
        hits: MutableList<MobFilterHitEntry>
    ): Int {
        val color = mobFilterInlineColor(target)
        val sv = Rect(content.left + 6, y + 4, mobFilterInlineSvSize, mobFilterInlineSvSize)
        val hue = Rect(sv.right + 6, sv.top, mobFilterInlineHueWidth, mobFilterInlineHueHeight)
        if (y + mobFilterInlinePickerHeight >= content.top && y <= content.bottom) {
            if (color.chroma) {
                context.fill(sv.left, sv.top, sv.right, sv.bottom, applyAlpha(0xFF222222.toInt(), alpha))
                context.fill(sv.left, sv.top, sv.right, sv.bottom, applyAlpha(chromaColor(0f), alpha * 0.15f))
                context.fill(hue.left, hue.top, hue.right, hue.bottom, applyAlpha(0xFF222222.toInt(), alpha))
                context.fill(hue.left, hue.top, hue.right, hue.bottom, applyAlpha(chromaColor(0f), alpha * 0.15f))
            } else {
                for (x in 0 until sv.width) {
                    val saturation = x / (sv.width - 1).toFloat()
                    val topColor = applyAlpha(HSBtoRGB(mobFilterPickerHue, saturation, 1f) or 0xFF000000.toInt(), alpha)
                    context.fillGradient(sv.left + x, sv.top, sv.left + x + 1, sv.bottom, topColor, applyAlpha(0xFF000000.toInt(), alpha))
                }
                val markerX = sv.left + (mobFilterPickerSaturation * (sv.width - 1)).roundToInt()
                val markerY = sv.top + ((1f - mobFilterPickerBrightness) * (sv.height - 1)).roundToInt()
                context.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, applyAlpha(0xFF000000.toInt(), alpha))
                context.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, applyAlpha(0xFFFFFFFF.toInt(), alpha))
                for (yy in 0 until hue.height) {
                    val hueValue = yy / (hue.height - 1).toFloat()
                    context.fill(hue.left, hue.top + yy, hue.right, hue.top + yy + 1, applyAlpha(HSBtoRGB(hueValue, 1f, 1f) or 0xFF000000.toInt(), alpha))
                }
                val hueMarker = hue.top + (mobFilterPickerHue * (hue.height - 1)).roundToInt()
                context.fill(hue.left - 1, hueMarker - 1, hue.right + 1, hueMarker + 2, applyAlpha(0xFFFFFFFF.toInt(), alpha))
            }
            drawThinBorder(context, sv, applyAlpha(0xFFAAAAAA.toInt(), alpha))
            drawThinBorder(context, hue, applyAlpha(0xFFAAAAAA.toInt(), alpha))

            val infoY = sv.bottom + 4
            val previewColor = if (color.chroma) chromaColor(0f) else HSBtoRGB(mobFilterPickerHue, mobFilterPickerSaturation, mobFilterPickerBrightness) or 0xFF000000.toInt()
            context.fill(sv.left, infoY, sv.left + 12, infoY + 12, applyAlpha(previewColor, alpha))
            drawThinBorder(context, Rect(sv.left, infoY, 12, 12), applyAlpha(0xFFAAAAAA.toInt(), alpha))
            val hex = "#${colorToHex(previewColor)}"
            context.drawString(mc.font, hex, sv.left + 16, infoY + 2, applyAlpha(0xFFCCCCCC.toInt(), alpha), true)
            val chromaLabel = "Chroma: ${onOff(color.chroma)}"
            val chroma = Rect(content.right - mc.font.width(chromaLabel) - 8, infoY, mc.font.width(chromaLabel) + 4, 12)
            context.drawString(mc.font, chromaLabel, chroma.left, chroma.top + 2, applyAlpha(if (chroma.contains(hoverX, hoverY)) chromaColor(0f) else 0xFFAAAAAA.toInt(), alpha), true)

            hits += MobFilterHitEntry(sv, target.key, MobFilterHitKind.PICKER_SV)
            hits += MobFilterHitEntry(hue, target.key, MobFilterHitKind.PICKER_HUE)
            hits += MobFilterHitEntry(chroma, target.key, MobFilterHitKind.PICKER_CHROMA)
        }
        return y + mobFilterInlinePickerHeight
    }

    private fun mobFilterContentArea(): Rect =
        Rect(panelX + 4, panelY + 22, panelWidth() - 8, panelHeight() - 60)

    private fun mobFilterContentHeight(): Int {
        var entries = 2 + 1 + FloydMobEsp.nearbyNameSuggestions().size + 1 + FloydMobEsp.nearbyTypeSuggestions().size
        if (FloydMobEsp.nameFilterIds().isNotEmpty()) entries += 1 + FloydMobEsp.nameFilterIds().size
        if (FloydMobEsp.typeFilterIds().isNotEmpty()) entries += 1 + FloydMobEsp.typeFilterIds().size
        val pickerHeight = if (expandedMobFilterColor != null) mobFilterInlinePickerHeight else 0
        return entries * mobFilterEntryHeight + pickerHeight + 20
    }

    private fun drawXrayEditorPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val title = "X-Ray Blocks"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)

        val content = xrayEditorContentArea()
        val maxScroll = max(0, xrayEditorContentHeight() - content.height)
        xrayEditorScroll = xrayEditorScroll.coerceIn(0, maxScroll)
        val hits = mutableListOf<XrayHitEntry>()
        var y = content.top + 4 - xrayEditorScroll
        val active = FloydXray.opaqueBlockIds().sorted()
        val nearby = nearbyBlockSuggestions().filterNot { active.contains(it) }

        if (active.isNotEmpty()) {
            context.drawString(mc.font, "Active Blocks", content.left + 2, y + 2, applyAlpha(chromaColor(0f), alpha), true)
            y += xrayEditorEntryHeight
            for (id in active) y = drawXrayEditorEntry(context, content, y, id, add = false, alpha, hits)
        }

        y += 4
        context.drawString(mc.font, "Nearby Blocks", content.left + 2, y + 2, applyAlpha(chromaColor(0.25f), alpha), true)
        y += xrayEditorEntryHeight
        for (id in nearby) y = drawXrayEditorEntry(context, content, y, id, add = true, alpha, hits)

        xrayHitEntries = hits
        if (maxScroll > 0) {
            val scrollHeight = max(10, content.height * content.height / xrayEditorContentHeight())
            val scrollY = content.top + (xrayEditorScroll * (content.height - scrollHeight)) / max(1, maxScroll)
            context.fill(content.right - 3, scrollY, content.right - 1, scrollY + scrollHeight, applyAlpha(0xFF888888.toInt(), alpha))
        }

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, 20)
        drawButton(context, pageDoneButton, "Done", alpha)
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawXrayEditorEntry(
        context: GuiGraphics,
        content: Rect,
        y: Int,
        blockId: String,
        add: Boolean,
        alpha: Float,
        hits: MutableList<XrayHitEntry>
    ): Int {
        val visible = y + xrayEditorEntryHeight >= content.top && y <= content.bottom
        if (visible) {
            val button = Rect(content.right - xrayEditorButtonWidth - 2, y + 2, xrayEditorButtonWidth, xrayEditorButtonHeight)
            val fill = when {
                add && button.contains(hoverX, hoverY) -> 0xFF339933.toInt()
                add -> 0xFF337733.toInt()
                button.contains(hoverX, hoverY) -> 0xFF993333.toInt()
                else -> 0xFF773333.toInt()
            }
            context.fill(button.left, button.top, button.right, button.bottom, applyAlpha(fill, alpha))
            drawThinBorder(context, button, applyAlpha(0xFFAAAAAA.toInt(), alpha))
            val buttonLabel = if (add) "+" else "-"
            context.drawString(mc.font, buttonLabel, button.left + (button.width - mc.font.width(buttonLabel)) / 2, button.top + (button.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
            hits += XrayHitEntry(button, blockId, add)

            val icon = Rect(content.left + 2, y + 2, 16, 16)
            context.fill(icon.left, icon.top, icon.right, icon.bottom, applyAlpha(0xFF333333.toInt(), alpha))
            drawThinBorder(context, icon, applyAlpha(0xFF555555.toInt(), alpha))
            context.drawString(mc.font, "B", icon.left + (icon.width - mc.font.width("B")) / 2, icon.top + (icon.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0.1f), alpha), true)

            val text = mc.font.plainSubstrByWidth(blockId, button.left - icon.right - 8)
            context.drawString(mc.font, text, icon.right + 4, y + (xrayEditorEntryHeight - mc.font.lineHeight) / 2, applyAlpha(if (add) 0xFFAAAAAA.toInt() else 0xFFCCCCCC.toInt(), alpha), true)
        }
        return y + xrayEditorEntryHeight
    }

    private fun xrayEditorContentArea(): Rect =
        Rect(panelX + 4, panelY + 22, panelWidth() - 8, panelHeight() - 60)

    private fun xrayEditorContentHeight(): Int {
        val active = FloydXray.opaqueBlockIds()
        val nearby = nearbyBlockSuggestions().filterNot { active.contains(it) }
        var entries = 1 + nearby.size
        if (active.isNotEmpty()) entries += 1 + active.size
        return entries * xrayEditorEntryHeight + 16
    }

    private fun drawNameMappingsEditorPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val title = "Name Mappings"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)

        val content = nameMappingContentArea()
        val maxScroll = max(0, nameMappingContentHeight() - content.height)
        nameMappingScroll = nameMappingScroll.coerceIn(0, maxScroll)
        val hits = mutableListOf<NameMappingHitEntry>()
        var y = content.top - nameMappingScroll
        val activeMappings = FloydNickHider.nameMappings.entries.sortedBy { it.key.lowercase() }
        val onlinePlayers = onlinePlayerSuggestions()
            .filterNot { name -> FloydNickHider.nameMappings.keys.any { it.equals(name, ignoreCase = true) } }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        val addManual = Rect(content.left + 4, y, content.width - 8, nameMappingRowHeight)
        if (addManual.bottom >= content.top && addManual.top <= content.bottom) {
            drawButton(context, addManual, "Add Mapping...", alpha)
            hits += NameMappingHitEntry(addManual, "", NameMappingHitKind.ADD_MANUAL)
        }
        y += nameMappingRowSpacing

        if (activeMappings.isNotEmpty()) {
            drawClippedNameMappingText(context, "Active Mappings", content.left, y + 2, content, applyAlpha(chromaColor(0.2f), alpha))
            y += nameMappingRowHeight + 4
            for ((real, fake) in activeMappings) {
                y = drawNameMappingEntry(context, content, y, real, fake, NameMappingHitKind.REMOVE, alpha, hits)
            }
            y += 8
        }

        drawClippedNameMappingText(context, "Online Players", content.left, y + 2, content, applyAlpha(chromaColor(0.5f), alpha))
        y += nameMappingRowHeight + 4
        if (onlinePlayers.isEmpty()) {
            drawClippedNameMappingText(context, "No unmapped players online", content.left + 4, y + 2, content, applyAlpha(0xFF888888.toInt(), alpha))
            y += nameMappingRowSpacing
        } else {
            for (name in onlinePlayers) {
                y = drawNameMappingEntry(context, content, y, name, null, NameMappingHitKind.ADD, alpha, hits)
                if (name.equals(addingMappingName, ignoreCase = true)) {
                    y = drawNameMappingInlineAdd(context, content, y, name, alpha, hits)
                }
            }
        }

        nameMappingHitEntries = hits
        if (maxScroll > 0) {
            val scrollHeight = max(10, content.height * content.height / nameMappingContentHeight())
            val scrollY = content.top + (nameMappingScroll * (content.height - scrollHeight)) / max(1, maxScroll)
            context.fill(content.right - 3, scrollY, content.right - 1, scrollY + scrollHeight, applyAlpha(0xFF888888.toInt(), alpha))
        }

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, 20)
        drawButton(context, pageDoneButton, "Done", alpha)
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawNameMappingEntry(
        context: GuiGraphics,
        content: Rect,
        y: Int,
        realName: String,
        fakeName: String?,
        kind: NameMappingHitKind,
        alpha: Float,
        hits: MutableList<NameMappingHitEntry>
    ): Int {
        val visible = y + nameMappingRowHeight >= content.top && y <= content.bottom
        if (visible) {
            val button = Rect(content.right - nameMappingButtonSize - 4, y + (nameMappingRowHeight - nameMappingButtonSize) / 2, nameMappingButtonSize, nameMappingButtonSize)
            val buttonFill = when {
                kind == NameMappingHitKind.ADD && button.contains(hoverX, hoverY) -> 0xFF33AA33.toInt()
                kind == NameMappingHitKind.ADD -> 0xFF338833.toInt()
                button.contains(hoverX, hoverY) -> 0xFFAA3333.toInt()
                else -> 0xFF883333.toInt()
            }
            context.fill(button.left, button.top, button.right, button.bottom, applyAlpha(buttonFill, alpha))
            drawThinBorder(context, button, applyAlpha(0xFFAAAAAA.toInt(), alpha))
            val buttonLabel = if (kind == NameMappingHitKind.ADD) "+" else "-"
            context.drawString(mc.font, buttonLabel, button.left + (button.width - mc.font.width(buttonLabel)) / 2, button.top + (button.height - mc.font.lineHeight) / 2, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)
            hits += NameMappingHitEntry(button, realName, kind)

            val head = Rect(content.left + 2, y + (nameMappingRowHeight - 8) / 2, 8, 8)
            context.fill(head.left, head.top, head.right, head.bottom, applyAlpha(0xFF333333.toInt(), alpha))
            drawThinBorder(context, head, applyAlpha(0xFF555555.toInt(), alpha))

            val labelLeft = content.left + 14
            val labelRight = button.left - 4
            val label = if (kind == NameMappingHitKind.REMOVE) {
                val displayedReal = if (revealedMappingNames.contains(realName)) realName else "*****"
                "$displayedReal -> ${fakeName.orEmpty()}"
            } else {
                realName
            }
            val display = mc.font.plainSubstrByWidth(label, labelRight - labelLeft)
            val textTop = y + (nameMappingRowHeight - mc.font.lineHeight) / 2
            val color = if (kind == NameMappingHitKind.ADD) 0xFFCCCCCC.toInt() else 0xFFFFFFFF.toInt()
            drawClippedNameMappingText(context, display, labelLeft, textTop, content, applyAlpha(color, alpha))
            if (kind == NameMappingHitKind.REMOVE) {
                hits += NameMappingHitEntry(Rect(labelLeft, textTop, max(0, labelRight - labelLeft), mc.font.lineHeight), realName, NameMappingHitKind.REVEAL)
            }
        }
        return y + nameMappingRowSpacing
    }

    private fun drawNameMappingInlineAdd(
        context: GuiGraphics,
        content: Rect,
        y: Int,
        realName: String,
        alpha: Float,
        hits: MutableList<NameMappingHitEntry>
    ): Int {
        val visible = y + nameMappingRowHeight >= content.top && y <= content.bottom
        if (visible) {
            val field = Rect(content.left + 14, y, content.width - 80, nameMappingRowHeight)
            val save = Rect(field.right + 4, y, 50, nameMappingRowHeight)
            context.fill(field.left, field.top, field.right, field.bottom, applyAlpha(0xFF000000.toInt(), alpha))
            drawChromaBorder(context, field.left - 1, field.top - 1, field.right + 1, field.bottom + 1, alpha)
            val display = mc.font.plainSubstrByWidth(addingMappingBuffer, field.width - 6)
            context.drawString(mc.font, display, field.left + 3, field.top + (field.height - mc.font.lineHeight) / 2, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)
            if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                val cursorX = field.left + 3 + mc.font.width(display)
                context.fill(cursorX, field.top + 3, cursorX + 1, field.bottom - 3, applyAlpha(0xFFFFFFFF.toInt(), alpha))
            }
            drawButton(context, save, "Save", alpha)
            hits += NameMappingHitEntry(field, realName, NameMappingHitKind.ADD_TEXT)
            hits += NameMappingHitEntry(save, realName, NameMappingHitKind.SAVE_ADD)
        }
        return y + nameMappingRowSpacing
    }

    private fun drawClippedNameMappingText(context: GuiGraphics, text: String, x: Int, y: Int, content: Rect, color: Int) {
        if (y + mc.font.lineHeight < content.top || y > content.bottom) return
        context.drawString(mc.font, text, x, y, color, true)
    }

    private fun nameMappingContentArea(): Rect =
        Rect(panelX + 8, panelY + 22, panelWidth() - 16, panelHeight() - 56)

    private fun nameMappingContentHeight(): Int {
        val activeCount = FloydNickHider.nameMappings.size
        val onlineCount = onlinePlayerSuggestions()
            .count { name -> FloydNickHider.nameMappings.keys.none { it.equals(name, ignoreCase = true) } }
        var height = nameMappingRowSpacing
        if (activeCount > 0) height += nameMappingRowHeight + 4 + activeCount * nameMappingRowSpacing + 8
        height += nameMappingRowHeight + 4
        height += max(1, onlineCount) * nameMappingRowSpacing
        if (addingMappingName != null && onlineCount > 0) height += nameMappingRowSpacing
        return height
    }

    private fun drawAnimationsPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<AnimationHitEntry>()
        val controlLeft = left + (panelWidth() - animationsFullWidth) / 2

        val enabled = Rect(controlLeft, animationRowY(top, 0), animationsFullWidth, animationsRowHeight)
        drawButton(context, enabled, "Animations: ${onOff(FloydAnimations.enabled)}", alpha)
        hits += AnimationHitEntry(enabled, "", AnimationHitKind.TOGGLE_MODULE)

        animationSliderSpecs().forEachIndexed { index, spec ->
            val rect = Rect(controlLeft, animationRowY(top, index + 1), animationsFullWidth, animationsRowHeight)
            drawAnimationSlider(context, rect, spec, alpha)
            hits += AnimationHitEntry(rect, spec.settingName, AnimationHitKind.SLIDER)
        }

        val cancelReEquip = Rect(controlLeft, animationRowY(top, 9), animationsFullWidth, animationsRowHeight)
        drawButton(context, cancelReEquip, "Cancel Re-Equip: ${onOff(booleanSetting(FloydAnimations, "Cancel Re-Equip")?.enabled ?: false)}", alpha)
        hits += AnimationHitEntry(cancelReEquip, "Cancel Re-Equip", AnimationHitKind.TOGGLE_SETTING)

        val hideHand = Rect(controlLeft, animationRowY(top, 10), animationsFullWidth, animationsRowHeight)
        drawButton(context, hideHand, "Hide Hand: ${onOff(booleanSetting(FloydAnimations, "Hide Hand")?.enabled ?: false)}", alpha)
        hits += AnimationHitEntry(hideHand, "Hide Hand", AnimationHitKind.TOGGLE_SETTING)

        val classicClick = Rect(controlLeft, animationRowY(top, 11), animationsFullWidth, animationsRowHeight)
        drawButton(context, classicClick, "Classic Click: ${onOff(booleanSetting(FloydAnimations, "Classic Click")?.enabled ?: false)}", alpha)
        hits += AnimationHitEntry(classicClick, "Classic Click", AnimationHitKind.TOGGLE_SETTING)

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, animationsRowHeight)
        drawButton(context, pageDoneButton, "Done", alpha)

        val title = "Animations"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)
        animationHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawAnimationSlider(context: GuiGraphics, rect: Rect, spec: AnimationSliderSpec, alpha: Float) {
        val value = numberSetting(FloydAnimations, spec.settingName)?.numericValue() ?: spec.min
        val pct = ((value - spec.min) / (spec.max - spec.min)).coerceIn(0.0, 1.0)
        val hovered = rect.contains(hoverX, hoverY)
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(if (hovered) 0xFF666666.toInt() else 0xFF555555.toInt(), alpha))
        val fillWidth = ((rect.width - 4) * pct).roundToInt()
        context.fill(rect.left + 2, rect.top + 2, rect.left + 2 + fillWidth, rect.bottom - 2, applyAlpha(0xFF888888.toInt(), alpha))
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
        val label = "${spec.label}: ${spec.format(value)}"
        context.drawString(mc.font, label, rect.left + (rect.width - mc.font.width(label)) / 2, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun animationRowY(top: Int, row: Int): Int = top + 26 + row * animationsRowSpacing

    private fun animationSliderSpecs(): List<AnimationSliderSpec> = listOf(
        AnimationSliderSpec("Pos X", "Pos X", -150.0, 150.0) { it.roundToInt().toString() },
        AnimationSliderSpec("Pos Y", "Pos Y", -150.0, 150.0) { it.roundToInt().toString() },
        AnimationSliderSpec("Pos Z", "Pos Z", -150.0, 50.0) { it.roundToInt().toString() },
        AnimationSliderSpec("Rot X", "Rot X", -180.0, 180.0) { it.roundToInt().toString() },
        AnimationSliderSpec("Rot Y", "Rot Y", -180.0, 180.0) { it.roundToInt().toString() },
        AnimationSliderSpec("Rot Z", "Rot Z", -180.0, 180.0) { it.roundToInt().toString() },
        AnimationSliderSpec("Scale", "Scale", 0.1, 2.0) { "%.2f".format(it) },
        AnimationSliderSpec("Swing Duration", "Swing Duration", 1.0, 100.0) { it.roundToInt().toString() }
    )

    private fun drawCameraPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<CameraHitEntry>()
        val controlLeft = left + (panelWidth() - cameraFullWidth) / 2

        drawCameraHeader(context, controlLeft, cameraRowY(top, 0), "Freecam", alpha)
        val freecam = Rect(controlLeft, cameraRowY(top, 1), cameraHalfWidth, cameraRowHeight)
        drawButton(context, freecam, "Freecam: ${onOff(runtimeBooleanSetting(FloydCamera, "Freecam")?.enabled ?: false)}", alpha)
        hits += CameraHitEntry(freecam, "Freecam", CameraHitKind.RUNTIME_TOGGLE)
        val speed = cameraSliderSpecs().first { it.settingName == "Speed" }
        val speedRect = Rect(controlLeft + cameraHalfWidth + cameraPairGap, cameraRowY(top, 1), cameraHalfWidth, cameraRowHeight)
        drawCameraSlider(context, speedRect, speed, alpha)
        hits += CameraHitEntry(speedRect, speed.settingName, CameraHitKind.SLIDER)

        drawCameraHeader(context, controlLeft, cameraRowY(top, 2), "Freelook", alpha)
        val freelook = Rect(controlLeft, cameraRowY(top, 3), cameraHalfWidth, cameraRowHeight)
        drawButton(context, freelook, "Freelook: ${onOff(runtimeBooleanSetting(FloydCamera, "Freelook")?.enabled ?: false)}", alpha)
        hits += CameraHitEntry(freelook, "Freelook", CameraHitKind.RUNTIME_TOGGLE)
        val distance = cameraSliderSpecs().first { it.settingName == "Distance" }
        val distanceRect = Rect(controlLeft + cameraHalfWidth + cameraPairGap, cameraRowY(top, 3), cameraHalfWidth, cameraRowHeight)
        drawCameraSlider(context, distanceRect, distance, alpha)
        hits += CameraHitEntry(distanceRect, distance.settingName, CameraHitKind.SLIDER)

        drawCameraHeader(context, controlLeft, cameraRowY(top, 4), "F5 Customizer", alpha)
        val disableFront = Rect(controlLeft, cameraRowY(top, 5), cameraHalfWidth, cameraRowHeight)
        drawButton(context, disableFront, "Disable Front: ${onOff(booleanSetting(FloydCamera, "Disable Front Cam")?.enabled ?: false)}", alpha)
        hits += CameraHitEntry(disableFront, "Disable Front Cam", CameraHitKind.BOOLEAN_TOGGLE)
        val disableBack = Rect(controlLeft + cameraHalfWidth + cameraPairGap, cameraRowY(top, 5), cameraHalfWidth, cameraRowHeight)
        drawButton(context, disableBack, "Disable Back: ${onOff(booleanSetting(FloydCamera, "Disable Back Cam")?.enabled ?: false)}", alpha)
        hits += CameraHitEntry(disableBack, "Disable Back Cam", CameraHitKind.BOOLEAN_TOGGLE)

        val noClip = Rect(controlLeft, cameraRowY(top, 6), cameraFullWidth, cameraRowHeight)
        drawButton(context, noClip, "Ignore Block Collisions: ${onOff(booleanSetting(FloydCamera, "No Third-Person Clipping")?.enabled ?: false)}", alpha)
        hits += CameraHitEntry(noClip, "No Third-Person Clipping", CameraHitKind.BOOLEAN_TOGGLE)
        val scroll = Rect(controlLeft, cameraRowY(top, 7), cameraFullWidth, cameraRowHeight)
        drawButton(context, scroll, "Scrolling Changes Distance: ${onOff(booleanSetting(FloydCamera, "Scrolling Changes Distance")?.enabled ?: false)}", alpha)
        hits += CameraHitEntry(scroll, "Scrolling Changes Distance", CameraHitKind.BOOLEAN_TOGGLE)
        val reset = Rect(controlLeft, cameraRowY(top, 8), cameraFullWidth, cameraRowHeight)
        drawButton(context, reset, "Reset F5 Scrolling: ${onOff(booleanSetting(FloydCamera, "Reset F5 Scrolling")?.enabled ?: false)}", alpha)
        hits += CameraHitEntry(reset, "Reset F5 Scrolling", CameraHitKind.BOOLEAN_TOGGLE)

        val f5 = cameraSliderSpecs().first { it.settingName == "Camera Distance" }
        val f5Rect = Rect(controlLeft, cameraRowY(top, 9), cameraFullWidth, cameraRowHeight)
        drawCameraSlider(context, f5Rect, f5, alpha)
        hits += CameraHitEntry(f5Rect, f5.settingName, CameraHitKind.SLIDER)

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, cameraRowHeight)
        drawButton(context, pageDoneButton, "Done", alpha)
        val title = "Camera"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)
        cameraHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawCameraHeader(context: GuiGraphics, left: Int, y: Int, text: String, alpha: Float) {
        val textWidth = mc.font.width(text)
        val x = left + (cameraFullWidth - textWidth) / 2
        val textY = y + (cameraRowHeight - mc.font.lineHeight) / 2
        val lineY = textY + mc.font.lineHeight / 2
        val color = applyAlpha(0xFF555555.toInt(), alpha)
        context.fill(left, lineY, x - 4, lineY + 1, color)
        context.fill(x + textWidth + 4, lineY, left + cameraFullWidth, lineY + 1, color)
        context.drawString(mc.font, text, x, textY, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun drawCameraSlider(context: GuiGraphics, rect: Rect, spec: CameraSliderSpec, alpha: Float) {
        val value = numberSetting(FloydCamera, spec.settingName)?.numericValue() ?: spec.min
        val pct = ((value - spec.min) / (spec.max - spec.min)).coerceIn(0.0, 1.0)
        val hovered = rect.contains(hoverX, hoverY)
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(if (hovered) 0xFF666666.toInt() else 0xFF555555.toInt(), alpha))
        val fillWidth = ((rect.width - 4) * pct).roundToInt()
        context.fill(rect.left + 2, rect.top + 2, rect.left + 2 + fillWidth, rect.bottom - 2, applyAlpha(0xFF888888.toInt(), alpha))
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
        val label = "${spec.label}: ${spec.format(value)}"
        val display = mc.font.plainSubstrByWidth(label, rect.width - 6)
        context.drawString(mc.font, display, rect.left + (rect.width - mc.font.width(display)) / 2, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun cameraRowY(top: Int, row: Int): Int = top + 26 + row * cameraRowSpacing

    private fun cameraSliderSpecs(): List<CameraSliderSpec> = listOf(
        CameraSliderSpec("Speed", "Speed", 0.1, 10.0) { oneDecimal(it) },
        CameraSliderSpec("Distance", "Dist", 1.0, 20.0) { oneDecimal(it) },
        CameraSliderSpec("Camera Distance", "F5 Distance", 1.0, 20.0) { oneDecimal(it) }
    )

    private fun drawRenderPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<RenderHitEntry>()
        val controlLeft = left + (panelWidth() - renderFullWidth) / 2

        renderFullButton(context, hits, controlLeft, top, 0, "Server ID Hider: ${onOff(booleanSetting(FloydHiders, "Server ID Hider")?.enabled ?: false)}", "Server ID Hider", RenderHitKind.BOOLEAN_TOGGLE, alpha)
        renderFullButton(context, hits, controlLeft, top, 1, "Profile ID Hider: ${onOff(booleanSetting(FloydHiders, "Profile ID Hider")?.enabled ?: false)}", "Profile ID Hider", RenderHitKind.BOOLEAN_TOGGLE, alpha)

        drawRenderHeader(context, controlLeft, renderRowY(top, 2), "X-Ray", alpha)
        renderFullButton(context, hits, controlLeft, top, 3, "X-Ray: ${onOff(booleanSetting(FloydXray, "Enabled")?.enabled ?: false)}", "X-Ray", RenderHitKind.XRAY_TOGGLE, alpha)
        val opacity = renderSliderSpecs().first { it.settingName == "Opacity" }
        val opacityRect = Rect(controlLeft, renderRowY(top, 4), renderFullWidth, renderRowHeight)
        drawRenderSlider(context, opacityRect, opacity, alpha)
        hits += RenderHitEntry(opacityRect, opacity.settingName, RenderHitKind.SLIDER)
        val editBlocks = Rect(controlLeft, renderRowY(top, 5), renderHalfWidth, renderRowHeight)
        drawButton(context, editBlocks, "Edit Blocks", alpha)
        hits += RenderHitEntry(editBlocks, "Edit Blocks", RenderHitKind.NAV_XRAY)
        val reloadBlocks = Rect(controlLeft + renderHalfWidth + renderPairGap, renderRowY(top, 5), renderHalfWidth, renderRowHeight)
        drawButton(context, reloadBlocks, "Reload Blocks", alpha)
        hits += RenderHitEntry(reloadBlocks, "Reload Blocks", RenderHitKind.RELOAD_XRAY)

        drawRenderHeader(context, controlLeft, renderRowY(top, 6), "Mob ESP", alpha)
        val mobToggle = Rect(controlLeft, renderRowY(top, 7), renderMainWidth, renderRowHeight)
        drawButton(context, mobToggle, "Mob ESP: ${onOff(FloydMobEsp.enabled)}", alpha)
        hits += RenderHitEntry(mobToggle, "Mob ESP", RenderHitKind.MODULE_TOGGLE)
        val mobConfig = Rect(controlLeft + renderMainWidth + renderPairGap, renderRowY(top, 7), renderSecondaryWidth, renderRowHeight)
        drawButton(context, mobConfig, "Config", alpha)
        hits += RenderHitEntry(mobConfig, "Config", RenderHitKind.NAV_MOB_ESP)

        drawRenderHeader(context, controlLeft, renderRowY(top, 8), "Other", alpha)
        val hiders = Rect(controlLeft, renderRowY(top, 9), renderHalfWidth, renderRowHeight)
        drawButton(context, hiders, "Hiders", alpha)
        hits += RenderHitEntry(hiders, "Hiders", RenderHitKind.NAV_HIDERS)
        val animations = Rect(controlLeft + renderHalfWidth + renderPairGap, renderRowY(top, 9), renderHalfWidth, renderRowHeight)
        drawButton(context, animations, "Attack Animation", alpha)
        hits += RenderHitEntry(animations, "Attack Animation", RenderHitKind.NAV_ANIMATIONS)
        val timeToggle = Rect(controlLeft, renderRowY(top, 10), renderHalfWidth, renderRowHeight)
        drawButton(context, timeToggle, "Time Changer: ${onOff(booleanSetting(FloydRender, "Time Changer")?.enabled ?: false)}", alpha)
        hits += RenderHitEntry(timeToggle, "Time Changer", RenderHitKind.BOOLEAN_TOGGLE)
        val time = renderSliderSpecs().first { it.settingName == "Time" }
        val timeRect = Rect(controlLeft + renderHalfWidth + renderPairGap, renderRowY(top, 10), renderHalfWidth, renderRowHeight)
        drawRenderSlider(context, timeRect, time, alpha)
        hits += RenderHitEntry(timeRect, time.settingName, RenderHitKind.SLIDER)

        val stalk = Rect(controlLeft, renderRowY(top, 11), renderFullWidth, renderRowHeight)
        drawButton(context, stalk, renderStalkLabel(), alpha)
        hits += RenderHitEntry(stalk, "Stalk", RenderHitKind.STALK)
        renderFullButton(context, hits, controlLeft, top, 12, "Borderless Window: ${onOff(booleanSetting(FloydRender, "Borderless Window")?.enabled ?: false)}", "Borderless Window", RenderHitKind.BORDERLESS, alpha)

        val title = Rect(controlLeft, renderRowY(top, 13), renderFullWidth, renderRowHeight)
        drawRenderTitleField(context, title, alpha)
        hits += RenderHitEntry(title, "Instance Title", RenderHitKind.TITLE_FIELD)

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, renderRowHeight)
        drawButton(context, pageDoneButton, "Done", alpha)
        val pageTitle = "Render"
        context.drawString(mc.font, pageTitle, left + (panelWidth() - mc.font.width(pageTitle)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)
        renderHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun renderFullButton(context: GuiGraphics, hits: MutableList<RenderHitEntry>, left: Int, top: Int, row: Int, label: String, settingName: String, kind: RenderHitKind, alpha: Float) {
        val rect = Rect(left, renderRowY(top, row), renderFullWidth, renderRowHeight)
        drawButton(context, rect, label, alpha)
        hits += RenderHitEntry(rect, settingName, kind)
    }

    private fun drawRenderHeader(context: GuiGraphics, left: Int, y: Int, text: String, alpha: Float) {
        val textWidth = mc.font.width(text)
        val x = left + (renderFullWidth - textWidth) / 2
        val textY = y + (renderRowHeight - mc.font.lineHeight) / 2
        val lineY = textY + mc.font.lineHeight / 2
        val color = applyAlpha(0xFF555555.toInt(), alpha)
        context.fill(left, lineY, x - 4, lineY + 1, color)
        context.fill(x + textWidth + 4, lineY, left + renderFullWidth, lineY + 1, color)
        context.drawString(mc.font, text, x, textY, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun drawRenderSlider(context: GuiGraphics, rect: Rect, spec: RenderSliderSpec, alpha: Float) {
        val value = numberSetting(spec.module, spec.settingName)?.numericValue() ?: spec.min
        val pct = ((value - spec.min) / (spec.max - spec.min)).coerceIn(0.0, 1.0)
        val hovered = rect.contains(hoverX, hoverY)
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(if (hovered) 0xFF666666.toInt() else 0xFF555555.toInt(), alpha))
        val fillWidth = ((rect.width - 4) * pct).roundToInt()
        context.fill(rect.left + 2, rect.top + 2, rect.left + 2 + fillWidth, rect.bottom - 2, applyAlpha(0xFF888888.toInt(), alpha))
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
        val label = "${spec.label}: ${spec.format(value)}"
        val display = mc.font.plainSubstrByWidth(label, rect.width - 6)
        context.drawString(mc.font, display, rect.left + (rect.width - mc.font.width(display)) / 2, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
    }

    private fun drawRenderTitleField(context: GuiGraphics, rect: Rect, alpha: Float) {
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(if (renderTitleFocused) 0xFF222222.toInt() else 0xFF000000.toInt(), alpha))
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
        val value = stringSetting(FloydRender, "Instance Title")?.value.orEmpty()
        val display = if (value.isBlank() && !renderTitleFocused) "Instance name / taskbar title" else value
        val trimmed = mc.font.plainSubstrByWidth(display, rect.width - 8)
        val color = if (value.isBlank() && !renderTitleFocused) 0xFF777777.toInt() else 0xFFCCCCCC.toInt()
        context.drawString(mc.font, trimmed, rect.left + 4, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(color, alpha), true)
        if (renderTitleFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            val cursorX = (rect.left + 4 + mc.font.width(trimmed)).coerceAtMost(rect.right - 4)
            context.fill(cursorX, rect.top + 3, cursorX + 1, rect.bottom - 3, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        }
    }

    private fun renderRowY(top: Int, row: Int): Int = top + 26 + row * renderRowSpacing

    private fun renderSliderSpecs(): List<RenderSliderSpec> = listOf(
        RenderSliderSpec(FloydXray, "Opacity", "X-Ray Opacity", 0.05, 1.0) { "${(it * 100).roundToInt()}%" },
        RenderSliderSpec(FloydRender, "Time", "Time", 0.0, 100.0) { "${it.roundToInt()}%" }
    )

    private fun renderStalkLabel(): String =
        if (FloydMobEsp.stalkEnabled()) "Stalk: ${FloydMobEsp.stalkTarget()} (disable)"
        else "Stalk: OFF (use /fa stalk <name>)"

    private fun drawHidersPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<HidersHitEntry>()
        val controlLeft = left + (panelWidth() - hidersFullWidth) / 2

        hidersButtonSpecs().forEachIndexed { index, spec ->
            val rect = Rect(controlLeft, hidersRowY(top, index), hidersFullWidth, hidersRowHeight)
            val label = if (spec.settingName == "Target") {
                "No Armor: ${noArmorDisplay()}"
            } else {
                "${spec.label}: ${onOff(booleanSetting(FloydHiders, spec.settingName)?.enabled ?: false)}"
            }
            drawButton(context, rect, label, alpha)
            hits += HidersHitEntry(rect, spec.settingName, if (spec.settingName == "Target") HidersHitKind.NO_ARMOR else HidersHitKind.TOGGLE)
        }

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, hidersRowHeight)
        drawButton(context, pageDoneButton, "Done", alpha)
        val title = "Hiders"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, applyAlpha(chromaColor(0f), alpha), true)

        hidersHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun hidersRowY(top: Int, row: Int): Int = top + 28 + row * hidersRowSpacing

    private fun hidersButtonSpecs(): List<HidersButtonSpec> = listOf(
        HidersButtonSpec("No Hurt Camera", "No Hurt Camera"),
        HidersButtonSpec("Remove Fire Overlay", "Remove Fire Overlay"),
        HidersButtonSpec("Hide Entity Fire", "Hide Entity Fire"),
        HidersButtonSpec("Disable Arrows", "Disable Arrows"),
        HidersButtonSpec("No Explosion Particles", "No Explosion Particles"),
        HidersButtonSpec("Disable Hunger Bar", "Disable Hunger Bar"),
        HidersButtonSpec("Hide Potion Effects", "Hide Potion Effects"),
        HidersButtonSpec("3rd Person Crosshair", "3rd Person Crosshair"),
        HidersButtonSpec("Remove Falling Blocks", "Remove Falling Blocks"),
        HidersButtonSpec("Remove Tab Ping", "Remove Tab Ping"),
        HidersButtonSpec("Target", "No Armor")
    )

    private fun noArmorDisplay(): String {
        val labels = listOf("OFF", "SELF", "OTHERS", "ALL")
        return labels.getOrNull(selectorSetting(FloydHiders, "Target")?.value ?: 0) ?: "OFF"
    }

    private fun drawNickHiderPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<NickHiderHitEntry>()
        val controlLeft = left + (panelWidth() - nickFullWidth) / 2

        val title = "Neck Hider"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 4, applyAlpha(chromaColor(0f), alpha), true)

        nickInputBounds = Rect(controlLeft, top + 16, nickFullWidth, 20)
        drawNickInput(context, nickInputBounds, alpha)

        val toggle = Rect(controlLeft, top + 40, nickFullWidth, 20)
        drawButton(context, toggle, "Neck Hider: ${onOff(booleanSetting(FloydNickHider, "Enabled")?.enabled ?: false)}", alpha)
        hits += NickHiderHitEntry(toggle, NickHiderHitKind.TOGGLE)

        val editNames = Rect(controlLeft, top + 68, 107, 20)
        val reloadNames = Rect(controlLeft + 113, top + 68, 107, 20)
        drawButton(context, editNames, "Edit Names", alpha)
        drawButton(context, reloadNames, "Reload Names", alpha)
        hits += NickHiderHitEntry(editNames, NickHiderHitKind.EDIT_NAMES)
        hits += NickHiderHitEntry(reloadNames, NickHiderHitKind.RELOAD_NAMES)

        val playerSize = Rect(controlLeft, top + 96, nickFullWidth, 20)
        drawButton(context, playerSize, "Player Size", alpha)
        hits += NickHiderHitEntry(playerSize, NickHiderHitKind.PLAYER_SIZE)

        val hint = "Use 'Edit Names' to manage player nicknames"
        context.drawString(mc.font, hint, left + (panelWidth() - mc.font.width(hint)) / 2, top + 124, applyAlpha(0xFFAAAAAA.toInt(), alpha), true)

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 100) / 2, bottom - 30, 100, 20)
        drawButton(context, pageDoneButton, "Done", alpha)

        nickHiderHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawNickInput(context: GuiGraphics, rect: Rect, alpha: Float) {
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(if (nickInputFocused) 0xFF222222.toInt() else 0xFF111111.toInt(), alpha))
        drawChromaBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
        val value = FloydNickHider.nickname
        val display = if (value.isBlank() && !nickInputFocused) "Default nickname" else value
        val trimmed = mc.font.plainSubstrByWidth(display, rect.width - 8)
        val color = if (value.isBlank() && !nickInputFocused) 0xFF777777.toInt() else 0xFFFFFFFF.toInt()
        context.drawString(mc.font, trimmed, rect.left + 4, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(color, alpha), true)
        if (nickInputFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            val cursorX = (rect.left + 4 + mc.font.width(trimmed)).coerceAtMost(rect.right - 4)
            context.fill(cursorX, rect.top + 3, cursorX + 1, rect.bottom - 3, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        }
    }

    private fun drawGuiStylePage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float) {
        val hits = mutableListOf<GuiStyleHitEntry>()
        val title = "GUI Style"
        context.drawString(mc.font, title, left + (panelWidth() - mc.font.width(title)) / 2, top + 6, legacyTextColor(0f, alpha), true)

        guiStyleTargets().forEachIndexed { index, target ->
            val y = top + 22 + index * guiStyleRowSpacing
            val labelX = left + 14
            context.drawString(mc.font, target.label, labelX, y + (guiStyleRowHeight - mc.font.lineHeight) / 2, legacyTextColor(0f, alpha), true)
            val preview = Rect(labelX + guiStyleLabelWidth + 6, y, guiStylePreviewWidth, guiStyleRowHeight)
            context.fill(preview.left, preview.top, preview.right, preview.bottom, applyAlpha(0xFF222222.toInt(), alpha))
            context.fill(preview.left + 1, preview.top + 1, preview.right - 1, preview.bottom - 1, applyAlpha(legacyStyleColor(target, 0f), alpha))
            drawButtonBorder(context, preview.left, preview.top, preview.right, preview.bottom, alpha)
            val pick = Rect(preview.right + 8, y, guiStylePickWidth, guiStyleRowHeight)
            drawButton(context, pick, "Pick", alpha)
            hits += GuiStyleHitEntry(pick, target)
        }

        pageBackButton = Rect.ZERO
        pageDoneButton = Rect(left + (panelWidth() - 96) / 2, bottom - 26, 96, guiStyleRowHeight)
        drawButton(context, pageDoneButton, "Done", alpha)
        guiStyleHitEntries = hits
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun guiStyleTargets(): List<StyleTarget> = listOf(StyleTarget.TEXT, StyleTarget.BUTTON_BORDER, StyleTarget.GUI_BORDER)

    private fun drawModuleBrowserPage(context: GuiGraphics, alpha: Float) {
        val hits = mutableListOf<ModuleBrowserHitEntry>()
        val headerHits = mutableListOf<ModuleBrowserHeaderHitEntry>()
        val categories = moduleBrowserCategories()
        initializeModuleBrowserPanelStates(categories)
        drawModuleBrowserSearch(context, alpha)
        for (category in categories) {
            val state = moduleBrowserPanelState(category)
            val entries = moduleBrowserEntries(category)
            val panelWidth = moduleBrowserPanelWidth(category)
            val contentHeight = entries.size * moduleBrowserRowHeight
            val visibleContentHeight = if (state.collapsed) 0 else min(contentHeight, moduleBrowserMaxPanelContentHeight)
            val panelHeight = moduleBrowserHeaderHeight + visibleContentHeight
            val right = state.x + panelWidth
            val bottom = state.y + panelHeight
            context.fill(state.x, state.y, right, bottom, applyAlpha(0xEE111111.toInt(), alpha))
            drawButtonBorder(context, state.x - 1, state.y - 1, right + 1, bottom + 1, alpha)
            val header = Rect(state.x, state.y, panelWidth, moduleBrowserHeaderHeight)
            context.fill(header.left, header.top, header.right, header.bottom, applyAlpha(legacyStyleColor(StyleTarget.BUTTON_BORDER, 0f), alpha))
            val headerLabel = category.name + if (state.collapsed) " [+]" else ""
            val headerColor = if (header.contains(hoverX, hoverY)) legacyTextColor(0.1f, alpha) else applyAlpha(0xFFFFFFFF.toInt(), alpha)
            context.drawString(mc.font, mc.font.plainSubstrByWidth(headerLabel, panelWidth - 12), header.left + 6, header.top + (moduleBrowserHeaderHeight - mc.font.lineHeight) / 2, headerColor, true)
            headerHits += ModuleBrowserHeaderHitEntry(header, category)

            if (!state.collapsed && entries.isNotEmpty()) {
                state.scroll = state.scroll.coerceIn(0, moduleBrowserMaxScroll(category))
                context.enableScissor(state.x, state.y + moduleBrowserHeaderHeight, right, bottom)
                var rowY = state.y + moduleBrowserHeaderHeight - state.scroll
                for (entry in entries) {
                    val row = Rect(state.x, rowY, panelWidth, moduleBrowserRowHeight)
                    val visible = row.bottom > state.y + moduleBrowserHeaderHeight && row.top < bottom
                    if (visible) {
                        val hover = row.contains(hoverX, hoverY)
                        val enabled = entry.enabled()
                        context.fill(row.left, row.top, row.right, row.bottom, applyAlpha(if (hover) 0xFF1A1A1A.toInt() else 0x00000000, alpha))
                        val labelColor = if (hover || enabled) legacyTextColor(0.15f, alpha) else applyAlpha(0xFFFFFFFF.toInt(), alpha)
                        val label = mc.font.plainSubstrByWidth(entry.label, panelWidth - 24)
                        context.drawString(mc.font, label, row.left + 8, row.top + (row.height - mc.font.lineHeight) / 2, labelColor, true)
                        val dotSize = 5
                        val dotColor = if (enabled) legacyStyleColor(StyleTarget.TEXT, 0.15f) else 0xFF555555.toInt()
                        context.fill(row.right - dotSize - 8, row.top + (row.height - dotSize) / 2, row.right - 8, row.top + (row.height + dotSize) / 2, applyAlpha(dotColor, alpha))
                        hits += ModuleBrowserHitEntry(row, category, entry, legacyModuleBrowserSettingsPage(entry))
                    }
                    rowY += moduleBrowserRowHeight
                }
                context.disableScissor()
            } else if (!state.collapsed && entries.isEmpty()) {
                val empty = "No matches"
                context.drawString(mc.font, empty, state.x + (panelWidth - mc.font.width(empty)) / 2, state.y + moduleBrowserHeaderHeight + 6, applyAlpha(0xFF888888.toInt(), alpha), true)
            }
        }
        modulePopup?.let {
            drawModulePopup(context, alpha, it)
            drawModulePopupPlayerPreview(context, alpha, it)
        }
        if (moduleBrowserSearchQuery.isNotBlank()) {
            val hint = "Search: ${moduleBrowserSearchQuery}"
            context.drawString(mc.font, mc.font.plainSubstrByWidth(hint, width - 12), 6, height - 34, applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
        }
        drawModuleBrowserBottomTitle(context, alpha)
        moduleBrowserHitEntries = hits
        moduleBrowserHeaderHitEntries = headerHits
        pageBackButton = Rect.ZERO
        pageDoneButton = Rect.ZERO
        pageRows = emptyList()
        labelBounds = emptyList()
        linkBounds = Rect.ZERO
    }

    private fun drawModuleBrowserBottomTitle(context: GuiGraphics, alpha: Float) {
        val title = "FloydAddons"
        val textScale = 2.0f
        val titleWidth = (mc.font.width(title) * textScale).roundToInt()
        val titleX = (width - titleWidth) / 2f
        val titleY = height - 20f
        context.pose().pushMatrix()
        context.pose().translate(titleX, titleY)
        context.pose().scale(textScale, textScale)
        context.drawString(mc.font, title, 0, 0, legacyTextColor(0f, alpha), true)
        context.pose().popMatrix()
    }

    private fun drawModulePopup(context: GuiGraphics, alpha: Float, popup: ModulePopup) {
        val settings = popupVisibleSettings(popup)
        val extras = modulePopupExtras(popup)
        popup.bounds = popup.bounds.copy(width = modulePopupWidth(popup), height = modulePopupHeight(popup))
        if (popup.bounds.bottom > height) {
            popup.bounds = popup.bounds.copy(top = max(4, height - popup.bounds.height - 4))
        }
        val rect = popup.bounds
        val right = rect.right
        val bottom = rect.bottom
        context.fill(rect.left, rect.top, right, bottom, applyAlpha(0xEE111111.toInt(), alpha))
        drawButtonBorder(context, rect.left - 1, rect.top - 1, right + 1, bottom + 1, alpha)
        val title = Rect(rect.left, rect.top, rect.width, modulePopupTitleHeight)
        context.fill(title.left, title.top, title.right, title.bottom, applyAlpha(legacyStyleColor(StyleTarget.BUTTON_BORDER, 0f), alpha))
        context.drawString(mc.font, mc.font.plainSubstrByWidth(modulePopupTitle(popup), rect.width - 12), title.left + 6, title.top + (title.height - mc.font.lineHeight) / 2, legacyTextColor(0f, alpha), true)

        val hits = mutableListOf<ModulePopupHitEntry>()
        val extraHits = mutableListOf<ModulePopupExtraHitEntry>()
        val playerHits = mutableListOf<ModulePopupPlayerHitEntry>()
        val xrayHits = mutableListOf<ModulePopupXrayHitEntry>()
        val mobHits = mutableListOf<MobFilterHitEntry>()
        val nameHits = mutableListOf<NameMappingHitEntry>()
        modulePopupExpandedExtraBounds = Rect.ZERO
        modulePopupExpandedExtraContentRows = 0
        modulePopupExpandedExtraVisibleRows = 0
        var y = rect.top + modulePopupTitleHeight
        if (modulePopupExtrasBeforeSettings(popup)) {
            for (extra in extras) {
                val height = modulePopupExtraHeight(extra)
                val row = Rect(rect.left, y, rect.width, height)
                drawModulePopupExtra(context, extra, row, alpha, playerHits, xrayHits, mobHits, nameHits)
                extraHits += ModulePopupExtraHitEntry(Rect(row.left, row.top, row.width, modulePopupRowHeight), extra.label, extra.kind)
                y += height
            }
        }
        if (settings.isEmpty() && extras.isEmpty()) {
            val empty = "No settings"
            context.drawString(mc.font, empty, rect.left + (rect.width - mc.font.width(empty)) / 2, y + 6, applyAlpha(0xFF888888.toInt(), alpha), true)
        } else if (settings.isNotEmpty()) {
            for (setting in settings) {
                val rowHeight = modulePopupSettingHeight(setting)
                val row = Rect(rect.left, y, rect.width, rowHeight)
                drawModulePopupSetting(context, popup, setting, row, alpha)
                hits += ModulePopupHitEntry(row, setting, modulePopupHitKind(setting))
                y += rowHeight
            }
        }
        if (!modulePopupExtrasBeforeSettings(popup)) {
            for (extra in extras) {
                val height = modulePopupExtraHeight(extra)
                val row = Rect(rect.left, y, rect.width, height)
                drawModulePopupExtra(context, extra, row, alpha, playerHits, xrayHits, mobHits, nameHits)
                extraHits += ModulePopupExtraHitEntry(Rect(row.left, row.top, row.width, modulePopupRowHeight), extra.label, extra.kind)
                y += height
            }
        }
        modulePopupHitEntries = hits
        modulePopupExtraHitEntries = extraHits
        modulePopupPlayerHitEntries = playerHits
        modulePopupXrayHitEntries = xrayHits
        modulePopupMobFilterHitEntries = mobHits
        modulePopupNameMappingHitEntries = nameHits
    }

    private fun drawModulePopupPlayerPreview(context: GuiGraphics, alpha: Float, popup: ModulePopup) {
        if (!modulePopupHasPlayerPreview(popup)) return
        val player = mc.player ?: return
        val rect = modulePopupPlayerPreviewBounds(popup)
        if (rect == Rect.ZERO) return
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(0xCC000000.toInt(), alpha))
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            context,
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            25,
            0.0625f,
            hoverX.toFloat(),
            hoverY.toFloat(),
            player
        )
        drawButtonBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
    }

    private fun drawModulePopupExtra(
        context: GuiGraphics,
        extra: ModulePopupExtra,
        row: Rect,
        alpha: Float,
        playerHits: MutableList<ModulePopupPlayerHitEntry>,
        xrayHits: MutableList<ModulePopupXrayHitEntry>,
        mobHits: MutableList<MobFilterHitEntry>,
        nameHits: MutableList<NameMappingHitEntry>
    ) {
        val collapsedRow = Rect(row.left, row.top, row.width, modulePopupRowHeight)
        val hover = collapsedRow.contains(hoverX, hoverY)
        if (hover) context.fill(collapsedRow.left, collapsedRow.top, collapsedRow.right, collapsedRow.bottom, applyAlpha(0xFF1A1A1A.toInt(), alpha))
        val expanded = expandedModulePopupExtra == extra.kind
        val expandable = extra.kind == ModulePopupExtraKind.STALK_TARGET || extra.kind == ModulePopupExtraKind.XRAY_BLOCKS || extra.kind == ModulePopupExtraKind.MOB_FILTERS || extra.kind == ModulePopupExtraKind.NAME_MAPPINGS
        val label = "[${extra.label}${if (expandable) if (expanded) " v" else " >" else ""}]"
        context.drawString(
            mc.font,
            mc.font.plainSubstrByWidth(label, collapsedRow.width - 16),
            collapsedRow.left + 8,
            collapsedRow.top + (collapsedRow.height - mc.font.lineHeight) / 2,
            if (hover) legacyTextColor(0.15f, alpha) else applyAlpha(0xFFAAAAAA.toInt(), alpha),
            true
        )
        if (expanded && extra.kind == ModulePopupExtraKind.STALK_TARGET) {
            drawModulePopupScrolledExtra(context, extra.kind, row.left, row.top + modulePopupRowHeight, row.width, alpha) {
                drawModulePopupPlayerPicker(context, row.left, row.top + modulePopupRowHeight, row.width, alpha, playerHits)
            }
        }
        if (expanded && extra.kind == ModulePopupExtraKind.XRAY_BLOCKS) {
            drawModulePopupScrolledExtra(context, extra.kind, row.left, row.top + modulePopupRowHeight, row.width, alpha) {
                drawModulePopupXrayBlocks(context, row.left, row.top + modulePopupRowHeight, row.width, alpha, xrayHits)
            }
        }
        if (expanded && extra.kind == ModulePopupExtraKind.MOB_FILTERS) {
            drawModulePopupScrolledExtra(context, extra.kind, row.left, row.top + modulePopupRowHeight, row.width, alpha) {
                drawModulePopupMobFilters(context, row.left, row.top + modulePopupRowHeight, row.width, alpha, mobHits)
            }
        }
        if (expanded && extra.kind == ModulePopupExtraKind.NAME_MAPPINGS) {
            drawModulePopupScrolledExtra(context, extra.kind, row.left, row.top + modulePopupRowHeight, row.width, alpha) {
                drawModulePopupNameMappings(context, row.left, row.top + modulePopupRowHeight, row.width, alpha, nameHits)
            }
        }
    }

    private fun drawModulePopupScrolledExtra(
        context: GuiGraphics,
        kind: ModulePopupExtraKind,
        left: Int,
        top: Int,
        width: Int,
        alpha: Float,
        draw: () -> Unit
    ) {
        val totalRows = modulePopupExtraContentRowCount(kind)
        val visibleRows = modulePopupExtraVisibleRows(kind, totalRows)
        val bounds = Rect(left, top, width, modulePopupExtraVisibleHeight(kind, totalRows))
        modulePopupExpandedExtraBounds = bounds
        modulePopupExpandedExtraContentRows = totalRows
        modulePopupExpandedExtraVisibleRows = visibleRows
        modulePopupExtraScrolls[kind] = modulePopupExtraScroll(kind).coerceIn(0, max(0, totalRows - visibleRows))
        context.enableScissor(bounds.left, bounds.top, bounds.right, bounds.bottom)
        draw()
        context.disableScissor()
        if (totalRows > visibleRows) {
            val thumbHeight = max(10, (bounds.height * visibleRows / totalRows.toFloat()).roundToInt())
            val maxThumbTravel = max(1, bounds.height - thumbHeight)
            val thumbTop = bounds.top + (maxThumbTravel * modulePopupExtraScroll(kind) / max(1, totalRows - visibleRows).toFloat()).roundToInt()
            val scrollbar = Rect(bounds.right - 4, thumbTop, 2, thumbHeight)
            context.fill(scrollbar.left, scrollbar.top, scrollbar.right, scrollbar.bottom, applyAlpha(legacyStyleColor(StyleTarget.BUTTON_BORDER, 0f), alpha))
        }
    }

    private fun drawModulePopupPlayerPicker(
        context: GuiGraphics,
        left: Int,
        top: Int,
        width: Int,
        alpha: Float,
        playerHits: MutableList<ModulePopupPlayerHitEntry>
    ) {
        val rows = listOf<String?>(null) +
            stalkTargetSuggestions()
                .drop(modulePopupExtraScroll(ModulePopupExtraKind.STALK_TARGET))
                .take(modulePopupExtraScrollableRowLimit(ModulePopupExtraKind.STALK_TARGET, pinnedRows = 1))
        var y = top
        rows.forEach { playerName ->
            val rowHeight = if (playerName == null) modulePopupRowHeight else modulePopupFilterEntryHeight
            val row = Rect(left + 4, y, width - 8, rowHeight)
            val hover = row.contains(hoverX, hoverY)
            if (hover) context.fill(row.left, row.top, row.right, row.bottom, applyAlpha(0xFF222222.toInt(), alpha))
            val selected = playerName != null && playerName.equals(FloydMobEsp.stalkTarget(), ignoreCase = true)
            if (playerName == null) {
                drawModulePopupActionInput(
                    context = context,
                    row = row,
                    kind = ModulePopupExtraKind.STALK_TARGET,
                    placeholder = "Target...",
                    alpha = alpha,
                    playerHits = playerHits
                )
                y += modulePopupRowHeight
                return@forEach
            }
            val label = if (selected) "$playerName *" else playerName
            context.drawString(
                mc.font,
                mc.font.plainSubstrByWidth(label, row.width - 28),
                row.left + 6,
                row.top + (row.height - mc.font.lineHeight) / 2,
                when {
                    selected -> legacyTextColor(0.1f, alpha)
                    hover -> legacyTextColor(0.15f, alpha)
                    else -> applyAlpha(0xFFAAAAAA.toInt(), alpha)
                },
                true
            )
            val action = "+"
            context.drawString(mc.font, action, row.right - mc.font.width(action) - 6, row.top + (row.height - mc.font.lineHeight) / 2, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)
            playerHits += ModulePopupPlayerHitEntry(row, playerName)
            y += modulePopupFilterEntryHeight
        }
    }

    private fun drawModulePopupXrayBlocks(
        context: GuiGraphics,
        left: Int,
        top: Int,
        width: Int,
        alpha: Float,
        xrayHits: MutableList<ModulePopupXrayHitEntry>
    ) {
        val activeBlocks = FloydXray.opaqueBlockIds()
        val pinnedRows = listOf(ModulePopupXrayDisplayRow(ModulePopupXrayRowKind.ACTION, action = ModulePopupExtraKind.XRAY_ADD_BLOCK, label = "Add Block..."))
        val scrollRows = buildList {
            val query = modulePopupActionQuery(ModulePopupExtraKind.XRAY_ADD_BLOCK)
            val active = activeBlocks.sorted().filterModulePopupQuery(query)
            val nearby = nearbyBlockSuggestions().filterNot { activeBlocks.contains(it) }.filterModulePopupQuery(query)
            if (active.isNotEmpty()) {
                add(ModulePopupXrayDisplayRow(ModulePopupXrayRowKind.HEADER, label = "Active Blocks"))
                active.forEach { add(ModulePopupXrayDisplayRow(ModulePopupXrayRowKind.BLOCK, blockId = it, add = false)) }
            }
            add(ModulePopupXrayDisplayRow(ModulePopupXrayRowKind.HEADER, label = "Nearby Blocks"))
            if (nearby.isEmpty()) add(ModulePopupXrayDisplayRow(ModulePopupXrayRowKind.EMPTY, label = if (query.isBlank()) "No nearby suggestions" else "No matches"))
            else nearby.forEach { add(ModulePopupXrayDisplayRow(ModulePopupXrayRowKind.BLOCK, blockId = it, add = true)) }
        }
        val rows = pinnedRows + scrollRows
            .drop(modulePopupExtraScroll(ModulePopupExtraKind.XRAY_BLOCKS))
            .take(modulePopupExtraScrollableRowLimit(ModulePopupExtraKind.XRAY_BLOCKS, pinnedRows.size))
        var y = top
        for (row in rows) {
            y = when (row.kind) {
                ModulePopupXrayRowKind.ACTION -> drawModulePopupActionRow(context, left, y, width, row.label, row.action ?: ModulePopupExtraKind.XRAY_ADD_BLOCK, alpha, xrayHits = xrayHits)
                ModulePopupXrayRowKind.HEADER -> drawModulePopupXrayHeader(context, left, y, width, row.label, alpha)
                ModulePopupXrayRowKind.EMPTY -> {
                    val emptyRow = Rect(left + 4, y, width - 8, modulePopupFilterEntryHeight)
                    context.drawString(mc.font, row.label, emptyRow.left + 6, emptyRow.top + (emptyRow.height - mc.font.lineHeight) / 2, applyAlpha(0xFF888888.toInt(), alpha), true)
                    y + modulePopupFilterEntryHeight
                }
                ModulePopupXrayRowKind.BLOCK -> drawModulePopupXrayRow(context, left, y, width, row.blockId, row.add, alpha, xrayHits)
            }
        }
    }

    private fun drawModulePopupXrayHeader(context: GuiGraphics, left: Int, y: Int, width: Int, label: String, alpha: Float): Int {
        context.drawString(mc.font, label, left + 8, y + (modulePopupFilterEntryHeight - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
        return y + modulePopupFilterEntryHeight
    }

    private fun drawModulePopupXrayRow(
        context: GuiGraphics,
        left: Int,
        y: Int,
        width: Int,
        blockId: String,
        add: Boolean,
        alpha: Float,
        xrayHits: MutableList<ModulePopupXrayHitEntry>
    ): Int {
        val row = Rect(left + 4, y, width - 8, modulePopupFilterEntryHeight)
        val button = Rect(row.right - 18, row.top + 1, 16, 14)
        val hover = button.contains(hoverX, hoverY)
        context.fill(button.left, button.top, button.right, button.bottom, applyAlpha(when {
            add && hover -> 0xFF339933.toInt()
            add -> 0xFF337733.toInt()
            hover -> 0xFF993333.toInt()
            else -> 0xFF773333.toInt()
        }, alpha))
        drawThinBorder(context, button, applyAlpha(0xFFAAAAAA.toInt(), alpha))
        val buttonLabel = if (add) "+" else "-"
        context.drawString(mc.font, buttonLabel, button.left + (button.width - mc.font.width(buttonLabel)) / 2, button.top + (button.height - mc.font.lineHeight) / 2, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)
        context.drawString(mc.font, mc.font.plainSubstrByWidth(blockId, button.left - row.left - 12), row.left + 6, row.top + (row.height - mc.font.lineHeight) / 2, applyAlpha(if (add) 0xFFAAAAAA.toInt() else 0xFFCCCCCC.toInt(), alpha), true)
        xrayHits += ModulePopupXrayHitEntry(button, blockId, add)
        return y + modulePopupFilterEntryHeight
    }

    private fun drawModulePopupMobFilters(
        context: GuiGraphics,
        left: Int,
        top: Int,
        width: Int,
        alpha: Float,
        mobHits: MutableList<MobFilterHitEntry>
    ) {
        val content = Rect(left + 4, top, width - 8, modulePopupExtraVisibleHeight(ModulePopupExtraKind.MOB_FILTERS, modulePopupExtraContentRowCount(ModulePopupExtraKind.MOB_FILTERS)))
        var y = content.top
        val nameQuery = modulePopupActionQuery(ModulePopupExtraKind.MOB_ADD_NAME)
        val typeQuery = modulePopupActionQuery(ModulePopupExtraKind.MOB_ADD_TYPE)
        val activeNames = FloydMobEsp.nameFilterIds().filterModulePopupQuery(nameQuery)
        val activeTypes = FloydMobEsp.typeFilterIds().filterModulePopupQuery(typeQuery)
        val nearbyNames = FloydMobEsp.nearbyNameSuggestions().filterModulePopupQuery(nameQuery)
        val nearbyTypes = FloydMobEsp.nearbyTypeSuggestions().filterModulePopupQuery(typeQuery)
        val pinnedRows = listOf(
            ModulePopupMobDisplayRow(ModulePopupMobRowKind.ACTION, "Add Name...", action = ModulePopupExtraKind.MOB_ADD_NAME),
            ModulePopupMobDisplayRow(ModulePopupMobRowKind.ACTION, "Add Type...", action = ModulePopupExtraKind.MOB_ADD_TYPE)
        )
        val scrollRows = buildList {
            if (activeNames.isNotEmpty()) {
                add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.HEADER, "Active Names"))
                activeNames.forEach { add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.ENTRY, it, MobFilterHitKind.REMOVE_NAME, "-", MobFilterColorTarget(it, true))) }
            }
            if (activeTypes.isNotEmpty()) {
                add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.HEADER, "Active Types"))
                activeTypes.forEach { add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.ENTRY, it, MobFilterHitKind.REMOVE_TYPE, "-", MobFilterColorTarget(it, false))) }
            }
            add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.HEADER, "Nearby Names"))
            if (nearbyNames.isEmpty()) add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.EMPTY, if (nameQuery.isBlank()) "No nearby names" else "No matching names"))
            else nearbyNames.forEach { add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.ENTRY, it, MobFilterHitKind.ADD_NAME, "+")) }
            add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.HEADER, "Nearby Types"))
            if (nearbyTypes.isEmpty()) add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.EMPTY, if (typeQuery.isBlank()) "No nearby types" else "No matching types"))
            else nearbyTypes.forEach { add(ModulePopupMobDisplayRow(ModulePopupMobRowKind.ENTRY, it, MobFilterHitKind.ADD_TYPE, "+")) }
        }
        val rows = pinnedRows + scrollRows
            .drop(modulePopupExtraScroll(ModulePopupExtraKind.MOB_FILTERS))
            .take(modulePopupExtraScrollableRowLimit(ModulePopupExtraKind.MOB_FILTERS, pinnedRows.size))
        for (row in rows) {
            y = when (row.kind) {
                ModulePopupMobRowKind.ACTION -> drawModulePopupActionRow(context, left, y, width, row.label, row.action ?: ModulePopupExtraKind.MOB_ADD_NAME, alpha, mobHits = mobHits)
                ModulePopupMobRowKind.HEADER -> drawModulePopupMobHeader(context, content.left, y, row.label, alpha)
                ModulePopupMobRowKind.EMPTY -> drawModulePopupMobEmpty(context, content.left, y, row.label, alpha)
                ModulePopupMobRowKind.ENTRY -> drawMobFilterEntry(
                    context,
                    content,
                    y,
                    row.label,
                    row.hitKind ?: MobFilterHitKind.ADD_NAME,
                    row.buttonLabel,
                    alpha,
                    mobHits,
                    row.colorTarget,
                    rowHeight = modulePopupFilterEntryHeight,
                    buttonHeight = 14
                )
            }
        }
    }

    private fun drawModulePopupMobHeader(context: GuiGraphics, left: Int, y: Int, label: String, alpha: Float): Int {
        context.drawString(mc.font, label, left + 4, y + (modulePopupFilterEntryHeight - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0f), alpha), true)
        return y + modulePopupFilterEntryHeight
    }

    private fun drawModulePopupMobEmpty(context: GuiGraphics, left: Int, y: Int, label: String, alpha: Float): Int {
        context.drawString(mc.font, label, left + 4, y + (modulePopupFilterEntryHeight - mc.font.lineHeight) / 2, applyAlpha(0xFF888888.toInt(), alpha), true)
        return y + modulePopupFilterEntryHeight
    }

    private fun drawModulePopupActionRow(
        context: GuiGraphics,
        left: Int,
        y: Int,
        width: Int,
        label: String,
        kind: ModulePopupExtraKind,
        alpha: Float,
        xrayHits: MutableList<ModulePopupXrayHitEntry>? = null,
        mobHits: MutableList<MobFilterHitEntry>? = null,
        nameHits: MutableList<NameMappingHitEntry>? = null
    ): Int {
        val row = Rect(left + 4, y, width - 8, modulePopupRowHeight)
        val hover = row.contains(hoverX, hoverY)
        if (hover) context.fill(row.left, row.top, row.right, row.bottom, applyAlpha(0xFF222222.toInt(), alpha))
        if (kind == ModulePopupExtraKind.NAME_ADD_MAPPING) {
            drawModulePopupMappingInputRow(context, row, alpha, nameHits)
            return y + modulePopupRowHeight
        }
        if (kind in modulePopupInputActionKinds) {
            drawModulePopupActionInput(
                context = context,
                row = row,
                kind = kind,
                placeholder = modulePopupActionPlaceholder(kind),
                alpha = alpha,
                xrayHits = xrayHits,
                mobHits = mobHits
            )
            return y + modulePopupRowHeight
        }
        when (kind) {
            ModulePopupExtraKind.XRAY_ADD_BLOCK -> xrayHits?.add(ModulePopupXrayHitEntry(row, "", add = true, action = kind))
            ModulePopupExtraKind.MOB_ADD_NAME -> mobHits?.add(MobFilterHitEntry(row, "", MobFilterHitKind.ADD_MANUAL_NAME))
            ModulePopupExtraKind.MOB_ADD_TYPE -> mobHits?.add(MobFilterHitEntry(row, "", MobFilterHitKind.ADD_MANUAL_TYPE))
            else -> Unit
        }
        val focused = activeModulePopupActionInput == kind
        val display = if (focused || modulePopupActionInputBuffer.isNotEmpty() && activeModulePopupActionInput == kind) {
            val suffix = if (focused) "_" else ""
            modulePopupActionInputBuffer.ifBlank { modulePopupActionPlaceholder(kind) } + suffix
        } else {
            "[$label]"
        }
        context.drawString(mc.font, mc.font.plainSubstrByWidth(display, row.width - 30), row.left + 6, row.top + (row.height - mc.font.lineHeight) / 2, if (hover || focused) legacyTextColor(0.15f, alpha) else applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
        if (kind in modulePopupInputActionKinds) {
            val plus = "+"
            context.drawString(mc.font, plus, row.right - mc.font.width(plus) - 8, row.top + (row.height - mc.font.lineHeight) / 2, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)
        }
        return y + modulePopupRowHeight
    }

    private fun drawModulePopupActionInput(
        context: GuiGraphics,
        row: Rect,
        kind: ModulePopupExtraKind,
        placeholder: String,
        alpha: Float,
        xrayHits: MutableList<ModulePopupXrayHitEntry>? = null,
        mobHits: MutableList<MobFilterHitEntry>? = null,
        playerHits: MutableList<ModulePopupPlayerHitEntry>? = null
    ) {
        val addButton = Rect(row.right - 22, row.top + 2, 18, row.height - 4)
        val field = Rect(row.left + 4, row.top + 2, max(30, addButton.left - row.left - 8), row.height - 4)
        val focused = activeModulePopupActionInput == kind
        val fieldHover = field.contains(hoverX, hoverY)
        val addHover = addButton.contains(hoverX, hoverY)
        val canAdd = focused && modulePopupActionInputBuffer.isNotBlank()

        context.fill(field.left, field.top, field.right, field.bottom, applyAlpha(if (focused) 0xFF222222.toInt() else 0xFF0A0A0A.toInt(), alpha))
        drawThinBorder(context, field, applyAlpha(if (focused || fieldHover) legacyStyleColor(StyleTarget.TEXT, 0.2f) else legacyStyleColor(StyleTarget.BUTTON_BORDER, 0f), alpha))

        val display = if (focused) modulePopupActionInputBuffer.ifBlank { placeholder } + "_" else placeholder
        val color = when {
            focused || modulePopupActionInputBuffer.isNotBlank() -> 0xFFFFFFFF.toInt()
            fieldHover -> legacyStyleColor(StyleTarget.TEXT, 0.15f)
            else -> 0xFF888888.toInt()
        }
        context.drawString(mc.font, mc.font.plainSubstrByWidth(display, field.width - 6), field.left + 3, field.top + (field.height - mc.font.lineHeight) / 2, applyAlpha(color, alpha), true)

        context.fill(addButton.left, addButton.top, addButton.right, addButton.bottom, applyAlpha(when {
            canAdd && addHover -> 0xFF339933.toInt()
            canAdd -> 0xFF337733.toInt()
            addHover -> 0xFF555555.toInt()
            else -> 0xFF333333.toInt()
        }, alpha))
        drawThinBorder(context, addButton, applyAlpha(0xFFAAAAAA.toInt(), alpha))
        context.drawString(mc.font, "+", addButton.left + (addButton.width - mc.font.width("+")) / 2, addButton.top + (addButton.height - mc.font.lineHeight) / 2, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)

        when (kind) {
            ModulePopupExtraKind.XRAY_ADD_BLOCK -> {
                xrayHits?.add(ModulePopupXrayHitEntry(field, "", add = true, action = kind))
                xrayHits?.add(ModulePopupXrayHitEntry(addButton, "", add = true, action = kind, submit = true))
            }
            ModulePopupExtraKind.MOB_ADD_NAME -> {
                mobHits?.add(MobFilterHitEntry(field, "", MobFilterHitKind.ADD_MANUAL_NAME))
                mobHits?.add(MobFilterHitEntry(addButton, "", MobFilterHitKind.ADD_MANUAL_NAME, submit = true))
            }
            ModulePopupExtraKind.MOB_ADD_TYPE -> {
                mobHits?.add(MobFilterHitEntry(field, "", MobFilterHitKind.ADD_MANUAL_TYPE))
                mobHits?.add(MobFilterHitEntry(addButton, "", MobFilterHitKind.ADD_MANUAL_TYPE, submit = true))
            }
            ModulePopupExtraKind.STALK_TARGET -> {
                playerHits?.add(ModulePopupPlayerHitEntry(field, null))
                playerHits?.add(ModulePopupPlayerHitEntry(addButton, null, submit = true))
            }
            else -> Unit
        }
    }

    private fun drawModulePopupMappingInputRow(
        context: GuiGraphics,
        row: Rect,
        alpha: Float,
        nameHits: MutableList<NameMappingHitEntry>?
    ) {
        val plusWidth = 16
        val arrow = "->"
        val arrowWidth = mc.font.width(arrow) + 6
        val fieldWidth = max(30, (row.width - plusWidth - arrowWidth - 14) / 2)
        val original = Rect(row.left + 4, row.top + 2, fieldWidth, row.height - 4)
        val fake = Rect(original.right + arrowWidth, row.top + 2, fieldWidth, row.height - 4)
        val plus = Rect(row.right - plusWidth - 4, row.top + 2, plusWidth, row.height - 4)
        drawModulePopupMappingField(context, original, modulePopupMappingOriginalBuffer, "Original name...", modulePopupMappingFocusedField == ModulePopupMappingField.ORIGINAL, alpha)
        context.drawString(mc.font, arrow, original.right + 3, row.top + (row.height - mc.font.lineHeight) / 2, applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
        drawModulePopupMappingField(context, fake, modulePopupMappingFakeBuffer, "New name...", modulePopupMappingFocusedField == ModulePopupMappingField.FAKE, alpha)
        val canAdd = modulePopupMappingOriginalBuffer.isNotBlank() && modulePopupMappingFakeBuffer.isNotBlank()
        val plusHover = plus.contains(hoverX, hoverY)
        context.fill(plus.left, plus.top, plus.right, plus.bottom, applyAlpha(when {
            canAdd && plusHover -> 0xFF339933.toInt()
            canAdd -> 0xFF337733.toInt()
            plusHover -> 0xFF555555.toInt()
            else -> 0xFF333333.toInt()
        }, alpha))
        drawThinBorder(context, plus, applyAlpha(0xFFAAAAAA.toInt(), alpha))
        context.drawString(mc.font, "+", plus.left + (plus.width - mc.font.width("+")) / 2, plus.top + (plus.height - mc.font.lineHeight) / 2, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)
        nameHits?.add(NameMappingHitEntry(original, "", NameMappingHitKind.ADD_MANUAL_ORIGINAL))
        nameHits?.add(NameMappingHitEntry(fake, "", NameMappingHitKind.ADD_MANUAL_FAKE))
        nameHits?.add(NameMappingHitEntry(plus, "", NameMappingHitKind.ADD_MANUAL_SAVE))
    }

    private fun drawModulePopupMappingField(context: GuiGraphics, rect: Rect, value: String, placeholder: String, focused: Boolean, alpha: Float) {
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(if (focused) 0xFF222222.toInt() else 0xFF0A0A0A.toInt(), alpha))
        drawThinBorder(context, rect, applyAlpha(if (focused) legacyStyleColor(StyleTarget.TEXT, 0.2f) else legacyStyleColor(StyleTarget.BUTTON_BORDER, 0f), alpha))
        val display = if (value.isBlank() && !focused) placeholder else value + if (focused) "_" else ""
        val color = if (value.isBlank() && !focused) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
        context.drawString(mc.font, mc.font.plainSubstrByWidth(display, rect.width - 6), rect.left + 3, rect.top + (rect.height - mc.font.lineHeight) / 2, applyAlpha(color, alpha), true)
    }

    private val modulePopupInputActionKinds = setOf(
        ModulePopupExtraKind.XRAY_ADD_BLOCK,
        ModulePopupExtraKind.MOB_ADD_NAME,
        ModulePopupExtraKind.MOB_ADD_TYPE,
        ModulePopupExtraKind.STALK_TARGET
    )

    private fun modulePopupActionPlaceholder(kind: ModulePopupExtraKind): String = when (kind) {
        ModulePopupExtraKind.XRAY_ADD_BLOCK -> "Filter..."
        ModulePopupExtraKind.MOB_ADD_NAME -> "Name filter..."
        ModulePopupExtraKind.MOB_ADD_TYPE -> "Type filter..."
        ModulePopupExtraKind.STALK_TARGET -> "Target..."
        else -> "Filter..."
    }

    private fun stalkTargetSuggestions(): List<String> {
        val query = modulePopupActionQuery(ModulePopupExtraKind.STALK_TARGET)
        val mappings = FloydNickHider.nameMappings
        return onlinePlayerSuggestions().filter { name ->
            query.isBlank() ||
                name.contains(query, ignoreCase = true) ||
                mappings[name]?.contains(query, ignoreCase = true) == true
        }
    }

    private fun drawModulePopupNameMappings(
        context: GuiGraphics,
        left: Int,
        top: Int,
        width: Int,
        alpha: Float,
        nameHits: MutableList<NameMappingHitEntry>
    ) {
        val content = Rect(left + 4, top, width - 8, modulePopupExtraVisibleHeight(ModulePopupExtraKind.NAME_MAPPINGS, modulePopupExtraContentRowCount(ModulePopupExtraKind.NAME_MAPPINGS)))
        var y = content.top
        val query = modulePopupMappingOriginalBuffer.trim()
        val mappings = FloydNickHider.nameMappings.entries
            .filter { (real, _) -> query.isBlank() || real.contains(query, ignoreCase = true) }
            .sortedBy { it.key.lowercase() }
        val onlinePlayers = onlinePlayerSuggestions()
            .filterNot { name -> FloydNickHider.nameMappings.keys.any { it.equals(name, ignoreCase = true) } }
            .filter { name -> query.isBlank() || name.contains(query, ignoreCase = true) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        val pinnedRows = listOf(ModulePopupNameDisplayRow(ModulePopupNameRowKind.ACTION, "Add Mapping...", kind = NameMappingHitKind.ADD_MANUAL))
        val scrollRows = buildList {
            if (mappings.isNotEmpty()) {
                add(ModulePopupNameDisplayRow(ModulePopupNameRowKind.HEADER, "Active Mappings"))
                mappings.forEach { (real, fake) -> add(ModulePopupNameDisplayRow(ModulePopupNameRowKind.ENTRY, real, fake, NameMappingHitKind.REMOVE)) }
            }
            add(ModulePopupNameDisplayRow(ModulePopupNameRowKind.HEADER, "Online Players"))
            if (onlinePlayers.isEmpty()) {
                add(ModulePopupNameDisplayRow(ModulePopupNameRowKind.EMPTY, if (query.isBlank()) "No unmapped players online" else "No matching players"))
            } else {
                onlinePlayers.forEach { name ->
                    add(ModulePopupNameDisplayRow(ModulePopupNameRowKind.ENTRY, name, kind = NameMappingHitKind.ADD))
                    if (name.equals(addingMappingName, ignoreCase = true)) add(ModulePopupNameDisplayRow(ModulePopupNameRowKind.INLINE_ADD, name))
                }
            }
        }
        val rows = pinnedRows + scrollRows
            .drop(modulePopupExtraScroll(ModulePopupExtraKind.NAME_MAPPINGS))
            .take(modulePopupExtraScrollableRowLimit(ModulePopupExtraKind.NAME_MAPPINGS, pinnedRows.size))
        for (row in rows) {
            y = when (row.rowKind) {
                ModulePopupNameRowKind.ACTION -> drawModulePopupActionRow(context, left, y, width, row.real, ModulePopupExtraKind.NAME_ADD_MAPPING, alpha, nameHits = nameHits)
                ModulePopupNameRowKind.HEADER -> drawModulePopupNameHeader(context, content.left, y, row.real, alpha)
                ModulePopupNameRowKind.EMPTY -> drawModulePopupNameEmpty(context, content.left, y, row.real, alpha)
                ModulePopupNameRowKind.ENTRY -> drawNameMappingEntry(context, content, y, row.real, row.fake, row.kind ?: NameMappingHitKind.ADD, alpha, nameHits)
                ModulePopupNameRowKind.INLINE_ADD -> drawNameMappingInlineAdd(context, content, y, row.real, alpha, nameHits)
            }
        }
    }

    private fun drawModulePopupNameHeader(context: GuiGraphics, left: Int, y: Int, label: String, alpha: Float): Int {
        context.drawString(mc.font, label, left + 4, y + (nameMappingRowHeight - mc.font.lineHeight) / 2, applyAlpha(chromaColor(0.2f), alpha), true)
        return y + nameMappingRowHeight
    }

    private fun drawModulePopupNameEmpty(context: GuiGraphics, left: Int, y: Int, label: String, alpha: Float): Int {
        context.drawString(mc.font, label, left + 4, y + (nameMappingRowHeight - mc.font.lineHeight) / 2, applyAlpha(0xFF888888.toInt(), alpha), true)
        return y + nameMappingRowHeight
    }

    private fun drawModulePopupSetting(context: GuiGraphics, popup: ModulePopup, setting: Setting<*>, row: Rect, alpha: Float) {
        val hover = row.contains(hoverX, hoverY)
        if (hover) context.fill(row.left, row.top, row.right, row.bottom, applyAlpha(0xFF1A1A1A.toInt(), alpha))
        val labelColor = if (hover) legacyTextColor(0.15f, alpha) else applyAlpha(0xFFFFFFFF.toInt(), alpha)
        val label = mc.font.plainSubstrByWidth(modulePopupSettingLabel(popup, setting), row.width - 16)
        when (setting) {
            is BooleanSetting -> {
                context.drawString(mc.font, label, row.left + 8, row.top + (row.height - mc.font.lineHeight) / 2, labelColor, true)
                drawPopupDot(context, row, setting.enabled, alpha)
            }
            is RuntimeBooleanSetting -> {
                context.drawString(mc.font, label, row.left + 8, row.top + (row.height - mc.font.lineHeight) / 2, labelColor, true)
                drawPopupDot(context, row, setting.enabled, alpha)
            }
            is NumberSetting<*> -> {
                context.drawString(mc.font, "$label: ${formatPopupNumber(setting.numericValue())}", row.left + 8, row.top + 2, labelColor, true)
                val bar = modulePopupSliderBounds(row)
                val min = setting.minNumericValue()
                val max = setting.maxNumericValue()
                val percentage = if (max > min) ((setting.numericValue() - min) / (max - min)).coerceIn(0.0, 1.0) else 0.0
                context.fill(bar.left, bar.top, bar.right, bar.bottom, applyAlpha(0xFF333333.toInt(), alpha))
                context.fill(bar.left, bar.top, bar.left + (bar.width * percentage).roundToInt(), bar.bottom, applyAlpha(legacyStyleColor(StyleTarget.BUTTON_BORDER, 0f), alpha))
            }
            is SelectorSetting -> {
                context.drawString(mc.font, label, row.left + 8, row.top + (row.height - mc.font.lineHeight) / 2, labelColor, true)
                val value = mc.font.plainSubstrByWidth(setting.selectedOption(), row.width / 2)
                context.drawString(mc.font, value, row.right - mc.font.width(value) - 8, row.top + (row.height - mc.font.lineHeight) / 2, applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
            }
            is StringSetting -> {
                if (legacyCycleStringOptions(setting) != null) {
                    context.drawString(mc.font, label, row.left + 8, row.top + (row.height - mc.font.lineHeight) / 2, labelColor, true)
                    var value = setting.value.ifBlank { "None" }
                    val maxValueWidth = max(20, row.width - mc.font.width(label) - 30)
                    while (mc.font.width(value) > maxValueWidth && value.length > 3) value = value.dropLast(1)
                    context.drawString(mc.font, value, row.right - mc.font.width(value) - 8, row.top + (row.height - mc.font.lineHeight) / 2, if (hover) labelColor else applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
                } else {
                    val editing = activeModulePopupString?.setting === setting
                    var rawValue = if (editing) modulePopupStringBuffer else setting.value.ifBlank { "<empty>" }
                    val suffix = if (editing) "_" else ""
                    val maxValueWidth = max(20, row.width - mc.font.width("$label:") - 28)
                    while (mc.font.width(rawValue + suffix) > maxValueWidth && rawValue.isNotEmpty()) rawValue = rawValue.drop(1)
                    val value = rawValue + suffix
                    context.drawString(mc.font, "$label:", row.left + 8, row.top + (row.height - mc.font.lineHeight) / 2, labelColor, true)
                    val valueX = row.left + 8 + mc.font.width("$label:") + 4
                    context.drawString(mc.font, value, valueX, row.top + (row.height - mc.font.lineHeight) / 2, if (editing) applyAlpha(0xFFFFFFFF.toInt(), alpha) else applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
                    if (editing) context.fill(valueX, row.bottom - 2, row.right - 8, row.bottom - 1, applyAlpha(legacyStyleColor(StyleTarget.BUTTON_BORDER, 0f), alpha))
                }
            }
            is ColorSetting -> {
                context.drawString(mc.font, label, row.left + 8, row.top + (modulePopupRowHeight - mc.font.lineHeight) / 2, labelColor, true)
                val sq = Rect(row.right - 20, row.top + (modulePopupRowHeight - 10) / 2, 10, 10)
                context.fill(sq.left, sq.top, sq.right, sq.bottom, applyAlpha(setting.value.rgba, alpha))
                drawButtonBorder(context, sq.left - 1, sq.top - 1, sq.right + 1, sq.bottom + 1, alpha)
                if (expandedModulePopupColor === setting) drawModulePopupInlineColorPicker(context, setting, row, alpha)
            }
            is ActionSetting -> {
                val actionLabel = "[${modulePopupSettingLabel(popup, setting)}]"
                context.drawString(mc.font, mc.font.plainSubstrByWidth(actionLabel, row.width - 16), row.left + 8, row.top + (row.height - mc.font.lineHeight) / 2, if (hover) legacyTextColor(0.15f, alpha) else applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
            }
            else -> {
                context.drawString(mc.font, "$label: ${popupSettingSummary(setting)}", row.left + 8, row.top + (row.height - mc.font.lineHeight) / 2, applyAlpha(0xFF888888.toInt(), alpha), true)
            }
        }
        if (setting !is ActionSetting) {
            val hint = when (setting) {
                is NumberSetting<*> -> "+/-"
                is ColorSetting -> "Pick"
                is StringSetting -> "Edit"
                is SelectorSetting -> "Cycle"
                else -> ""
            }
            if (hint.isNotEmpty()) context.drawString(mc.font, hint, row.right - mc.font.width(hint) - 8, row.top + 2, applyAlpha(0xFF777777.toInt(), alpha), true)
        }
    }

    private fun drawModulePopupInlineColorPicker(context: GuiGraphics, setting: ColorSetting, row: Rect, alpha: Float) {
        val sv = modulePopupInlineColorSvBounds(row)
        val hue = modulePopupInlineColorHueBounds(row)
        val activeColor = modulePopupActiveColor(setting)
        for (x in 0 until sv.width) {
            val saturation = x / (sv.width - 1).toFloat()
            val topColor = applyAlpha(HSBtoRGB(activeColor.hue, saturation, 1f) or 0xFF000000.toInt(), alpha)
            context.fillGradient(sv.left + x, sv.top, sv.left + x + 1, sv.bottom, topColor, applyAlpha(0xFF000000.toInt(), alpha))
        }
        val markerX = sv.left + (activeColor.saturation * (sv.width - 1)).roundToInt()
        val markerY = sv.top + ((1f - activeColor.brightness) * (sv.height - 1)).roundToInt()
        context.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, applyAlpha(0xFF000000.toInt(), alpha))
        context.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        drawButtonBorder(context, sv.left - 1, sv.top - 1, sv.right + 1, sv.bottom + 1, alpha)

        for (y in 0 until hue.height) {
            val hueValue = y / (hue.height - 1).toFloat()
            context.fill(hue.left, hue.top + y, hue.right, hue.top + y + 1, applyAlpha(HSBtoRGB(hueValue, 1f, 1f) or 0xFF000000.toInt(), alpha))
        }
        val hueMarkerY = hue.top + (activeColor.hue * (hue.height - 1)).roundToInt()
        context.fill(hue.left - 2, hueMarkerY - 1, hue.right + 2, hueMarkerY + 2, applyAlpha(0xFF000000.toInt(), alpha))
        context.fill(hue.left - 1, hueMarkerY, hue.right + 1, hueMarkerY + 1, applyAlpha(0xFFFFFFFF.toInt(), alpha))
        drawButtonBorder(context, hue.left - 1, hue.top - 1, hue.right + 1, hue.bottom + 1, alpha)

        val basePreview = modulePopupInlineColorBasePreviewBounds(row)
        context.fill(basePreview.left, basePreview.top, basePreview.right, basePreview.bottom, applyAlpha(setting.value.rgba, alpha))
        drawButtonBorder(context, basePreview.left - 1, basePreview.top - 1, basePreview.right + 1, basePreview.bottom + 1, if (modulePopupEditingFadeColor) alpha * 0.45f else alpha)
        modulePopupFadeColorSetting(setting)?.let { fadeColor ->
            val fadePreview = modulePopupInlineColorFadePreviewBounds(row)
            context.fill(fadePreview.left, fadePreview.top, fadePreview.right, fadePreview.bottom, applyAlpha(fadeColor.value.rgba, alpha))
            drawButtonBorder(context, fadePreview.left - 1, fadePreview.top - 1, fadePreview.right + 1, fadePreview.bottom + 1, if (modulePopupEditingFadeColor) alpha else alpha * 0.45f)
        }
        val hex = "#${activeColor.hex(includeAlpha = false)}"
        context.drawString(mc.font, hex, basePreview.right + 6, basePreview.top + (basePreview.height - mc.font.lineHeight) / 2, applyAlpha(0xFFAAAAAA.toInt(), alpha), true)
        modulePopupChromaSetting(setting)?.let { chroma ->
            val button = modulePopupInlineColorChromaBounds(row)
            drawButton(context, button, "Chroma: ${onOff(chroma.enabled)}", alpha)
        }
        modulePopupFadeToggleSetting(setting)?.let { fade ->
            val button = modulePopupInlineColorFadeBounds(row)
            drawButton(context, button, "Fade: ${onOff(fade.enabled)}", alpha)
            val edit = modulePopupInlineColorEditFadeBounds(row)
            drawButton(context, edit, if (modulePopupEditingFadeColor) "Editing: Fade" else "Editing: Base", alpha)
        }
    }

    private fun drawPopupDot(context: GuiGraphics, row: Rect, enabled: Boolean, alpha: Float) {
        val dotSize = 5
        val color = if (enabled) legacyStyleColor(StyleTarget.TEXT, 0.15f) else 0xFF555555.toInt()
        context.fill(row.right - dotSize - 8, row.top + (row.height - dotSize) / 2, row.right - 8, row.top + (row.height + dotSize) / 2, applyAlpha(color, alpha))
    }

    private fun popupVisibleSettings(module: Module): List<Setting<*>> =
        module.settings.values.filter { it.isVisible && !modulePopupHiddenSetting(module, it) }

    private fun popupVisibleSettings(popup: ModulePopup): List<Setting<*>> =
        popup.entry?.let(::popupVisibleSettings) ?: popupVisibleSettings(popup.module)

    private fun popupVisibleSettings(entry: LegacyModuleBrowserEntry): List<Setting<*>> =
        when (entry.kind) {
            LegacyModuleBrowserKind.MODULE ->
                when (entry.module) {
                    FloydXray ->
                        listOfNotNull(numberSetting(FloydXray, "Opacity"))
                    FloydMobEsp ->
                        listOfNotNull(
                            booleanSetting(FloydMobEsp, "Tracers"),
                            booleanSetting(FloydMobEsp, "Hitboxes"),
                            booleanSetting(FloydMobEsp, "Star Mobs"),
                            colorSetting(FloydMobEsp, "Default ESP Color")
                        )
                    else -> popupVisibleSettings(entry.module)
                }
            LegacyModuleBrowserKind.RENDER_BOOLEAN ->
                when (entry.settingName) {
                    "Time Changer" -> listOfNotNull(numberSetting(FloydRender, "Time"))
                    else -> emptyList()
                }
            LegacyModuleBrowserKind.RENDER_STALK ->
                listOfNotNull(colorSetting(FloydMobEsp, "Tracer Color"))
            LegacyModuleBrowserKind.CAMERA_FREECAM ->
                listOfNotNull(numberSetting(FloydCamera, "Speed"))
            LegacyModuleBrowserKind.CAMERA_FREELOOK ->
                listOfNotNull(numberSetting(FloydCamera, "Distance"))
            LegacyModuleBrowserKind.CAMERA_F5 ->
                listOfNotNull(
                    booleanSetting(FloydCamera, "Disable Front Cam"),
                    booleanSetting(FloydCamera, "Disable Back Cam"),
                    booleanSetting(FloydCamera, "No Third-Person Clipping"),
                    booleanSetting(FloydCamera, "Scrolling Changes Distance"),
                    booleanSetting(FloydCamera, "Reset F5 Scrolling"),
                    numberSetting(FloydCamera, "Camera Distance")
                )
            LegacyModuleBrowserKind.HIDER_NO_ARMOR ->
                listOfNotNull(selectorSetting(FloydHiders, "Target"))
            LegacyModuleBrowserKind.RENDER_HUD ->
                emptyList()
            LegacyModuleBrowserKind.RENDER_BORDERLESS ->
                emptyList()
            LegacyModuleBrowserKind.RENDER_INSTANCE_NAME ->
                listOfNotNull(stringSetting(FloydRender, "Instance Title"))
            LegacyModuleBrowserKind.RENDER_GUI_STYLE ->
                listOfNotNull(
                    colorSetting(ClickGUIModule, "Button Text Color"),
                    colorSetting(ClickGUIModule, "Button Border Color"),
                    colorSetting(ClickGUIModule, "GUI Border Color")
                )
            LegacyModuleBrowserKind.RENDER_ANIMATIONS ->
                popupVisibleSettings(FloydAnimations)
            LegacyModuleBrowserKind.PLAYER_CAPE ->
                listOfNotNull(
                    stringSetting(FloydCape, "Image"),
                    moduleAction(FloydCape, "Open Cape Folder")
                )
            LegacyModuleBrowserKind.PLAYER_CONE_HAT ->
                listOfNotNull(
                    numberSetting(FloydConeHat, "Height"),
                    numberSetting(FloydConeHat, "Radius"),
                    numberSetting(FloydConeHat, "Y Offset"),
                    numberSetting(FloydConeHat, "Rotation"),
                    numberSetting(FloydConeHat, "Spin Speed"),
                    stringSetting(FloydConeHat, "Image"),
                    moduleAction(FloydConeHat, "Open Cone Folder")
                )
            LegacyModuleBrowserKind.PLAYER_NICK_HIDER ->
                listOfNotNull(stringSetting(FloydNickHider, "Default Nick"))
            LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN ->
                listOfNotNull(
                    booleanSetting(FloydSkin, "Self"),
                    booleanSetting(FloydSkin, "Others"),
                    stringSetting(FloydSkin, "Skin"),
                    moduleAction(FloydSkin, "Open Skin Folder")
                )
            LegacyModuleBrowserKind.PLAYER_SIZE ->
                listOfNotNull(
                    selectorSetting(FloydPlayerSize, "Target"),
                    numberSetting(FloydPlayerSize, "X"),
                    numberSetting(FloydPlayerSize, "Y"),
                    numberSetting(FloydPlayerSize, "Z")
                )
            else -> popupVisibleSettings(entry.module)
        }

    private fun modulePopupHiddenSetting(module: Module, setting: Setting<*>): Boolean =
        setting is KeybindSetting ||
            setting is MapSetting<*, *, *> ||
            setting is HUDSetting ||
            setting.name.endsWith("Key") ||
            setting.name == "Keybind" ||
            setting.name == "Panel Settings" ||
            (module === FloydXray && setting.name in setOf("Opaque Block", "Add Opaque Block", "Remove Opaque Block", "List Opaque Blocks", "Clear Opaque Blocks")) ||
            (module === FloydMobEsp && setting.name in setOf("Default Chroma", "Stalk Chroma")) ||
            (module === FloydMobEsp && setting.name in setOf(
                "Name Filter",
                "Type Filter",
                "Filter Color",
                "Filter Chroma",
                "Add Name Filter",
                "Remove Name Filter",
                "Color Name Filter",
                "Add Type Filter",
                "Remove Type Filter",
                "Color Type Filter",
                "List Filters",
                "Clear Filters",
                "Add Looked At Name",
                "Add Looked At Type"
            ))

    private fun modulePopupExtras(module: Module): List<ModulePopupExtra> = when (module) {
        FloydXray -> listOf(ModulePopupExtra("Edit Blocks", ModulePopupExtraKind.XRAY_BLOCKS))
        FloydMobEsp -> listOf(ModulePopupExtra("Edit Filters", ModulePopupExtraKind.MOB_FILTERS))
        FloydNickHider -> listOf(
            ModulePopupExtra("Edit Names", ModulePopupExtraKind.NAME_MAPPINGS),
            ModulePopupExtra("Reload Names", ModulePopupExtraKind.RELOAD_NAMES)
        )
        FloydHud -> listOf(ModulePopupExtra("Edit Layout", ModulePopupExtraKind.HUD_LAYOUT))
        else -> emptyList()
    }

    private fun modulePopupExtras(popup: ModulePopup): List<ModulePopupExtra> =
        popup.entry?.let(::modulePopupExtras) ?: modulePopupExtras(popup.module)

    private fun modulePopupExtrasBeforeSettings(popup: ModulePopup): Boolean =
        popup.entry?.kind == LegacyModuleBrowserKind.RENDER_STALK

    private fun modulePopupExtras(entry: LegacyModuleBrowserEntry): List<ModulePopupExtra> =
        when (entry.kind) {
            LegacyModuleBrowserKind.RENDER_STALK ->
                listOf(ModulePopupExtra("Target: ${FloydMobEsp.stalkTarget().ifBlank { "<none>" }}", ModulePopupExtraKind.STALK_TARGET))
            LegacyModuleBrowserKind.RENDER_HUD,
            LegacyModuleBrowserKind.RENDER_BOOLEAN ->
                if (entry.label in setOf("Inventory HUD", "Custom Scoreboard")) listOf(ModulePopupExtra("Edit Layout", ModulePopupExtraKind.HUD_LAYOUT)) else emptyList()
            else -> modulePopupExtras(entry.module)
        }

    private fun modulePopupTitle(popup: ModulePopup): String =
        popup.entry?.label ?: legacyFloydModuleLabel(popup.module)

    private fun modulePopupSettingLabel(popup: ModulePopup, setting: Setting<*>): String =
        when (popup.entry?.kind) {
            LegacyModuleBrowserKind.RENDER_GUI_STYLE -> when (setting.name) {
                "Button Text Color" -> "Text Color"
                else -> setting.name
            }
            LegacyModuleBrowserKind.PLAYER_CAPE -> when (setting.name) {
                "Open Cape Folder" -> "Open Folder"
                else -> setting.name
            }
            LegacyModuleBrowserKind.PLAYER_CONE_HAT -> when (setting.name) {
                "Open Cone Folder" -> "Open Folder"
                else -> setting.name
            }
            LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN -> when (setting.name) {
                "Open Skin Folder" -> "Open Folder"
                else -> setting.name
            }
            else -> setting.name
        }

    private fun modulePopupWidth(popup: ModulePopup): Int {
        val maxLabel = (listOf(modulePopupTitle(popup)) + popupVisibleSettings(popup).map { modulePopupSettingLabel(popup, it) } + modulePopupExtras(popup).map { it.label })
            .maxOfOrNull { mc.font.width(it) + 80 }
            ?: modulePopupMinWidth
        val minWidth = if (modulePopupHasFilterContent(popup)) modulePopupFilterMinWidth else modulePopupMinWidth
        return max(maxLabel, minWidth)
    }

    private fun modulePopupHasFilterContent(popup: ModulePopup): Boolean =
        modulePopupExtras(popup).any { extra ->
            extra.kind in setOf(
                ModulePopupExtraKind.STALK_TARGET,
                ModulePopupExtraKind.XRAY_BLOCKS,
                ModulePopupExtraKind.MOB_FILTERS,
                ModulePopupExtraKind.NAME_MAPPINGS
            )
        }

    private fun modulePopupHeight(popup: ModulePopup): Int =
        modulePopupTitleHeight +
            modulePopupExtras(popup).sumOf { modulePopupExtraHeight(it) } +
            popupVisibleSettings(popup).sumOf { modulePopupSettingHeight(it) } +
            modulePopupPadding

    private fun modulePopupExtraHeight(extra: ModulePopupExtra): Int =
        if (expandedModulePopupExtra == extra.kind) {
            when (extra.kind) {
                ModulePopupExtraKind.STALK_TARGET ->
                    modulePopupRowHeight + modulePopupExtraVisibleHeight(extra.kind, modulePopupExtraContentRowCount(extra.kind))
                ModulePopupExtraKind.XRAY_BLOCKS ->
                    modulePopupRowHeight + modulePopupExtraVisibleHeight(extra.kind, modulePopupExtraContentRowCount(extra.kind))
                ModulePopupExtraKind.MOB_FILTERS ->
                    modulePopupRowHeight + modulePopupExtraVisibleHeight(extra.kind, modulePopupExtraContentRowCount(extra.kind))
                ModulePopupExtraKind.NAME_MAPPINGS ->
                    modulePopupRowHeight + modulePopupExtraVisibleHeight(extra.kind, modulePopupExtraContentRowCount(extra.kind))
                else -> modulePopupRowHeight
            }
        } else {
            modulePopupRowHeight
        }

    private fun modulePopupExtraScroll(kind: ModulePopupExtraKind): Int =
        modulePopupExtraScrolls[kind] ?: 0

    private fun modulePopupExtraVisibleRows(kind: ModulePopupExtraKind, totalRows: Int): Int =
        min(totalRows, modulePopupPinnedRows(kind) + modulePopupExtraScrollableRowLimit(kind, modulePopupPinnedRows(kind))).coerceAtLeast(1)

    private fun modulePopupExtraVisibleHeight(kind: ModulePopupExtraKind, totalRows: Int): Int {
        val visibleRows = modulePopupExtraVisibleRows(kind, totalRows)
        val scrollableRows = max(0, min(totalRows - modulePopupPinnedRows(kind), visibleRows - modulePopupPinnedRows(kind)))
        return when (kind) {
            ModulePopupExtraKind.STALK_TARGET,
            ModulePopupExtraKind.XRAY_BLOCKS,
            ModulePopupExtraKind.NAME_MAPPINGS -> modulePopupRowHeight + min(modulePopupFilterMaxVisibleHeight, scrollableRows * modulePopupFilterEntryHeight)
            ModulePopupExtraKind.MOB_FILTERS -> min(visibleRows, 2) * modulePopupRowHeight + min(modulePopupFilterMaxVisibleHeight, scrollableRows * modulePopupFilterEntryHeight)
            else -> visibleRows * modulePopupRowHeight
        }
    }

    private fun modulePopupPinnedRows(kind: ModulePopupExtraKind): Int = when (kind) {
        ModulePopupExtraKind.MOB_FILTERS -> 2
        ModulePopupExtraKind.STALK_TARGET,
        ModulePopupExtraKind.XRAY_BLOCKS,
        ModulePopupExtraKind.NAME_MAPPINGS -> 1
        else -> 0
    }

    private fun modulePopupExtraScrollableRowLimit(kind: ModulePopupExtraKind, pinnedRows: Int): Int =
        when (kind) {
            ModulePopupExtraKind.STALK_TARGET,
            ModulePopupExtraKind.XRAY_BLOCKS,
            ModulePopupExtraKind.MOB_FILTERS,
            ModulePopupExtraKind.NAME_MAPPINGS -> (modulePopupFilterMaxVisibleHeight + modulePopupFilterEntryHeight - 1) / modulePopupFilterEntryHeight
            else -> Int.MAX_VALUE
        }.coerceAtLeast(0)

    private fun modulePopupExtraContentRowCount(kind: ModulePopupExtraKind): Int = when (kind) {
        ModulePopupExtraKind.STALK_TARGET -> 1 + stalkTargetSuggestions().size
        ModulePopupExtraKind.XRAY_BLOCKS -> 1 + modulePopupXrayBlockRowCount()
        ModulePopupExtraKind.MOB_FILTERS -> modulePopupMobFilterRowCount()
        ModulePopupExtraKind.NAME_MAPPINGS -> modulePopupNameMappingRowCount()
        else -> 1
    }

    private fun modulePopupXrayBlockRowCount(): Int {
        val query = modulePopupActionQuery(ModulePopupExtraKind.XRAY_ADD_BLOCK)
        val active = FloydXray.opaqueBlockIds().sorted().filterModulePopupQuery(query)
        val nearby = nearbyBlockSuggestions().filterNot { FloydXray.opaqueBlockIds().contains(it) }.filterModulePopupQuery(query)
        return (if (active.isNotEmpty()) 1 + active.size else 0) + 1 + max(1, nearby.size)
    }

    private fun modulePopupMobFilterRowCount(): Int {
        val nameQuery = modulePopupActionQuery(ModulePopupExtraKind.MOB_ADD_NAME)
        val typeQuery = modulePopupActionQuery(ModulePopupExtraKind.MOB_ADD_TYPE)
        val activeNames = FloydMobEsp.nameFilterIds().filterModulePopupQuery(nameQuery)
        val activeTypes = FloydMobEsp.typeFilterIds().filterModulePopupQuery(typeQuery)
        val nearbyNames = FloydMobEsp.nearbyNameSuggestions().filterModulePopupQuery(nameQuery)
        val nearbyTypes = FloydMobEsp.nearbyTypeSuggestions().filterModulePopupQuery(typeQuery)
        var rows = 1 + max(1, nearbyNames.size) + 1 + max(1, nearbyTypes.size)
        rows += 2
        if (activeNames.isNotEmpty()) rows += 1 + activeNames.size
        if (activeTypes.isNotEmpty()) rows += 1 + activeTypes.size
        return rows
    }

    private fun modulePopupActionQuery(vararg kinds: ModulePopupExtraKind): String =
        if (activeModulePopupActionInput in kinds) modulePopupActionInputBuffer.trim() else ""

    private fun Iterable<String>.filterModulePopupQuery(query: String): List<String> =
        if (query.isBlank()) toList() else filter { it.contains(query, ignoreCase = true) }

    private fun modulePopupNameMappingRowCount(): Int {
        val mappings = FloydNickHider.nameMappings.entries
        val onlinePlayers = onlinePlayerSuggestions()
            .filterNot { name -> FloydNickHider.nameMappings.keys.any { it.equals(name, ignoreCase = true) } }
        var rows = 2 + max(1, onlinePlayers.size)
        if (mappings.isNotEmpty()) rows += 1 + mappings.size
        if (addingMappingName != null && onlinePlayers.any { it.equals(addingMappingName, ignoreCase = true) }) rows += 1
        return rows
    }

    private fun modulePopupMobFilterContentHeight(): Int {
        val activeNames = FloydMobEsp.nameFilterIds()
        val activeTypes = FloydMobEsp.typeFilterIds()
        val displayedColor = expandedMobFilterColor?.let { target ->
            (target.isName && activeNames.any { it.equals(target.key, ignoreCase = true) }) ||
                (!target.isName && activeTypes.any { it.equals(target.key, ignoreCase = true) })
        } == true
        return modulePopupMobFilterRowCount() * mobFilterEntryHeight + if (displayedColor) mobFilterInlinePickerHeight else 0
    }

    private fun modulePopupNameMappingContentHeight(): Int {
        val mappings = FloydNickHider.nameMappings.entries
        val onlinePlayers = onlinePlayerSuggestions()
            .filterNot { name -> FloydNickHider.nameMappings.keys.any { it.equals(name, ignoreCase = true) } }
        var rows = 1 + max(1, onlinePlayers.size)
        if (mappings.isNotEmpty()) rows += 1 + mappings.size
        if (addingMappingName != null && onlinePlayers.any { it.equals(addingMappingName, ignoreCase = true) }) rows += 1
        return rows * nameMappingRowSpacing
    }

    private fun modulePopupSettingHeight(setting: Setting<*>): Int =
        when {
            setting is NumberSetting<*> -> modulePopupSliderRowHeight
            setting is ColorSetting && expandedModulePopupColor === setting -> modulePopupColorExpandedHeight
            else -> modulePopupRowHeight
        }

    private fun modulePopupSliderBounds(row: Rect): Rect =
        Rect(row.left + 8, row.bottom - 8, row.width - 16, 4)

    private fun modulePopupInlineColorSvBounds(row: Rect): Rect =
        Rect(row.left + 8, row.top + modulePopupRowHeight + 6, modulePopupColorSvSize, modulePopupColorSvSize)

    private fun modulePopupInlineColorHueBounds(row: Rect): Rect =
        modulePopupInlineColorSvBounds(row).let { Rect(it.right + 8, it.top, modulePopupColorHueWidth, modulePopupColorSvSize) }

    private fun modulePopupInlineColorBasePreviewBounds(row: Rect): Rect =
        modulePopupInlineColorSvBounds(row).let { sv -> Rect(sv.left, sv.bottom + 6, 16, 16) }

    private fun modulePopupInlineColorFadePreviewBounds(row: Rect): Rect =
        modulePopupInlineColorBasePreviewBounds(row).let { Rect(it.right + 6, it.top, 16, 16) }

    private fun modulePopupInlineColorChromaBounds(row: Rect): Rect =
        modulePopupInlineColorSvBounds(row).let { sv -> Rect(row.right - 82, sv.bottom + 6, 74, 18) }

    private fun modulePopupInlineColorFadeBounds(row: Rect): Rect =
        modulePopupInlineColorChromaBounds(row).let { Rect(row.left + 8, it.bottom + 4, 74, 18) }

    private fun modulePopupInlineColorEditFadeBounds(row: Rect): Rect =
        modulePopupInlineColorFadeBounds(row).let { Rect(it.right + 6, it.top, 96, 18) }

    private fun updateModulePopupSlider(target: ModulePopupSliderTarget, x: Double) {
        val min = target.setting.minNumericValue()
        val max = target.setting.maxNumericValue()
        if (max <= min || target.bounds.width <= 0) return
        val percentage = ((x - target.bounds.left) / target.bounds.width).coerceIn(0.0, 1.0)
        target.setting.setNumericValue(min + percentage * (max - min))
        ModuleManager.saveConfigurations()
    }

    private fun updateModulePopupColorDrag(x: Double, y: Double) {
        val setting = expandedModulePopupColor ?: return
        if (modulePopupChromaSetting(setting)?.enabled == true) return
        val row = modulePopupHitEntries.firstOrNull { it.setting === setting }?.bounds ?: return
        val activeColor = modulePopupActiveColor(setting)
        when (activeModulePopupColorDrag) {
            ColorPickerDrag.SV -> {
                val rect = modulePopupInlineColorSvBounds(row)
                activeColor.saturation = ((x - rect.left) / (rect.width - 1)).toFloat().coerceIn(0f, 1f)
                activeColor.brightness = (1.0 - (y - rect.top) / (rect.height - 1)).toFloat().coerceIn(0f, 1f)
            }
            ColorPickerDrag.HUE -> {
                val rect = modulePopupInlineColorHueBounds(row)
                activeColor.hue = ((y - rect.top) / (rect.height - 1)).toFloat().coerceIn(0f, 1f)
            }
            null -> return
        }
        ModuleManager.saveConfigurations()
    }

    private fun toggleModulePopupChroma(setting: ColorSetting) {
        val chroma = modulePopupChromaSetting(setting) ?: return
        val enabled = !chroma.enabled
        chroma.enabled = enabled
        if (enabled) modulePopupFadeToggleSetting(setting)?.enabled = false
        ModuleManager.saveConfigurations()
    }

    private fun toggleModulePopupFade(setting: ColorSetting) {
        val fade = modulePopupFadeToggleSetting(setting) ?: return
        val enabled = !fade.enabled
        fade.enabled = enabled
        if (enabled) modulePopupChromaSetting(setting)?.enabled = false
        ModuleManager.saveConfigurations()
    }

    private fun modulePopupActiveColor(setting: ColorSetting): Color =
        if (modulePopupEditingFadeColor) modulePopupFadeColorSetting(setting)?.value ?: setting.value else setting.value

    private fun modulePopupChromaSetting(setting: ColorSetting): BooleanSetting? =
        when (setting.name) {
            "Default ESP Color" -> booleanSetting(FloydMobEsp, "Default Chroma")
            "Tracer Color" -> booleanSetting(FloydMobEsp, "Stalk Chroma")
            "Button Text Color" -> booleanSetting(ClickGUIModule, "Button Text Chroma")
            "Button Border Color" -> booleanSetting(ClickGUIModule, "Button Border Chroma")
            "GUI Border Color" -> booleanSetting(ClickGUIModule, "GUI Border Chroma")
            else -> null
        }

    private fun modulePopupFadeColorSetting(setting: ColorSetting): ColorSetting? =
        when (setting.name) {
            "Button Text Color" -> colorSetting(ClickGUIModule, "Button Text Fade Color")
            "Button Border Color" -> colorSetting(ClickGUIModule, "Button Border Fade Color")
            "GUI Border Color" -> colorSetting(ClickGUIModule, "GUI Border Fade Color")
            else -> null
        }

    private fun modulePopupFadeToggleSetting(setting: ColorSetting): BooleanSetting? =
        when (setting.name) {
            "Button Text Color" -> booleanSetting(ClickGUIModule, "Button Text Fade")
            "Button Border Color" -> booleanSetting(ClickGUIModule, "Button Border Fade")
            "GUI Border Color" -> booleanSetting(ClickGUIModule, "GUI Border Fade")
            else -> null
        }

    private fun modulePopupHitKind(setting: Setting<*>): ModulePopupHitKind = when (setting) {
        is BooleanSetting, is RuntimeBooleanSetting -> ModulePopupHitKind.TOGGLE
        is NumberSetting<*> -> ModulePopupHitKind.NUMBER
        is SelectorSetting -> ModulePopupHitKind.SELECTOR
        is StringSetting -> if (legacyCycleStringOptions(setting) != null) ModulePopupHitKind.CYCLE_STRING else ModulePopupHitKind.STRING
        is ColorSetting -> ModulePopupHitKind.COLOR
        is ActionSetting -> ModulePopupHitKind.ACTION
        else -> ModulePopupHitKind.UNSUPPORTED
    }

    private fun popupSettingSummary(setting: Setting<*>): String =
        mc.font.plainSubstrByWidth(setting.value?.toString() ?: "", 80)

    private fun formatPopupNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.2f".format(value)

    private fun modulePopupDebugValue(setting: Setting<*>): Any? = when (setting) {
        is BooleanSetting -> setting.enabled
        is RuntimeBooleanSetting -> setting.enabled
        is NumberSetting<*> -> setting.numericValue()
        is SelectorSetting -> setting.selectedOption()
        is StringSetting -> setting.value
        is ColorSetting -> "#${setting.value.hex(includeAlpha = true)}"
        else -> null
    }

    private fun modulePopupDebugOptions(setting: Setting<*>): List<String>? =
        (setting as? SelectorSetting)?.optionLabels() ?: (setting as? StringSetting)?.let(::legacyCycleStringOptions)

    private fun modulePopupDebugChroma(setting: Setting<*>): Boolean? =
        (setting as? ColorSetting)?.let { modulePopupChromaSetting(it)?.enabled }

    private fun modulePopupDebugFade(setting: Setting<*>): Boolean? =
        (setting as? ColorSetting)?.let { modulePopupFadeToggleSetting(it)?.enabled }

    private fun legacyCycleStringOptions(setting: StringSetting): List<String>? =
        when (setting) {
            stringSetting(FloydCape, "Image") -> FloydCape.availableCapeFiles()
            stringSetting(FloydConeHat, "Image") -> FloydConeHat.availableImageFiles()
            stringSetting(FloydSkin, "Skin") -> FloydSkin.availableSkinFiles()
            else -> null
        }

    private fun cycleLegacyStringSetting(setting: StringSetting) {
        val options = legacyCycleStringOptions(setting).orEmpty()
        if (options.isEmpty()) return
        val current = options.indexOfFirst { it.equals(setting.value, ignoreCase = true) }.takeIf { it >= 0 } ?: -1
        val next = options[(current + 1).floorMod(options.size)]
        when (setting) {
            stringSetting(FloydCape, "Image") -> FloydCape.selectCape(next)
            stringSetting(FloydConeHat, "Image") -> FloydConeHat.selectImage(next)
            stringSetting(FloydSkin, "Skin") -> FloydSkin.selectSkin(next)
            else -> {
                setting.value = next
                ModuleManager.saveConfigurations()
            }
        }
    }

    private fun moduleBrowserCategories(): List<Category> =
        listOf(Category.RENDER, Category.HIDERS, Category.PLAYER, Category.CAMERA)

    private fun drawModuleBrowserSearch(context: GuiGraphics, alpha: Float) {
        val x = (width - moduleBrowserSearchWidth) / 2
        val y = 4
        moduleBrowserSearchBounds = Rect(x, y, moduleBrowserSearchWidth, moduleBrowserSearchHeight)
        context.fill(x, y, x + moduleBrowserSearchWidth, y + moduleBrowserSearchHeight, applyAlpha(0xFF111111.toInt(), alpha))
        drawButtonBorder(context, x - 1, y - 1, x + moduleBrowserSearchWidth + 1, y + moduleBrowserSearchHeight + 1, alpha)
        val display = if (moduleBrowserSearchQuery.isEmpty() && !moduleBrowserSearchFocused) "Search..." else moduleBrowserSearchQuery + if (moduleBrowserSearchFocused) "_" else ""
        val color = if (moduleBrowserSearchQuery.isEmpty() && !moduleBrowserSearchFocused) 0xFF888888.toInt() else 0xFFFFFFFF.toInt()
        context.drawString(mc.font, mc.font.plainSubstrByWidth(display, moduleBrowserSearchWidth - 12), x + 6, y + (moduleBrowserSearchHeight - mc.font.lineHeight) / 2, applyAlpha(color, alpha), true)
    }

    private fun initializeModuleBrowserPanelStates(categories: List<Category>) {
        loadModuleBrowserPanelStates()
        val widths = categories.associateWith(::moduleBrowserPanelWidth)
        for ((index, category) in categories.withIndex()) {
            val defaultX = moduleBrowserDefaultPanelX(index, widths[category] ?: moduleBrowserPanelMinWidth)
            moduleBrowserPanelStates.getOrPut(category) {
                ModuleBrowserPanelState(defaultX, moduleBrowserPanelTop)
            }
        }
    }

    private fun moduleBrowserDefaultPanelX(index: Int, panelWidth: Int): Int =
        (10 + index * 210).coerceIn(0, max(0, width - panelWidth))

    private fun loadModuleBrowserPanelStates() {
        if (moduleBrowserPanelStates.isNotEmpty() || !Files.isRegularFile(legacyClickGuiPanelConfigPath)) return
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val data = legacyClickGuiPanelGson.fromJson<Map<String, List<Int>>>(
                Files.readString(legacyClickGuiPanelConfigPath),
                legacyClickGuiPanelType
            ) ?: return
            moduleBrowserCategories().forEachIndexed { index, category ->
                val raw = data[category.name] ?: data[category.name.uppercase()] ?: return@forEachIndexed
                if (raw.size < 3) return@forEachIndexed
                val panelWidth = moduleBrowserPanelWidth(category)
                moduleBrowserPanelStates[category] = ModuleBrowserPanelState(
                    x = raw[0].coerceIn(0, max(0, width - panelWidth)),
                    y = raw[1].coerceIn(0, max(0, height - moduleBrowserHeaderHeight)),
                    collapsed = raw[2] != 0,
                    scroll = 0
                )
            }
        }.onFailure { FloydAddonsMod.logger.warn("Failed to load legacy ClickGUI panel state", it) }
    }

    private fun saveModuleBrowserPanelStates() {
        runCatching {
            Files.createDirectories(legacyClickGuiPanelConfigPath.parent)
            val data = linkedMapOf<String, List<Int>>()
            moduleBrowserCategories().forEachIndexed { index, category ->
                val state = moduleBrowserPanelStates[category]
                    ?: ModuleBrowserPanelState(moduleBrowserDefaultPanelX(index, moduleBrowserPanelWidth(category)), moduleBrowserPanelTop)
                data[category.name] = listOf(state.x, state.y, if (state.collapsed) 1 else 0)
            }
            Files.writeString(legacyClickGuiPanelConfigPath, legacyClickGuiPanelGson.toJson(data), StandardCharsets.UTF_8)
        }.onFailure { FloydAddonsMod.logger.warn("Failed to save legacy ClickGUI panel state", it) }
    }

    private fun moduleBrowserPanelWidth(category: Category): Int {
        val entries = moduleBrowserEntries(category)
        val maxTextWidth = (listOf(category.name) + entries.map { it.label })
            .maxOfOrNull { mc.font.width(it) } ?: mc.font.width(category.name)
        return max(moduleBrowserPanelMinWidth, maxTextWidth + 30)
    }

    private fun moduleBrowserEntries(category: Category): List<LegacyModuleBrowserEntry> {
        val query = normalizedModuleBrowserSearch(moduleBrowserSearchQuery)
        return legacyFloydEntriesFor(category)
            .filter { entry ->
                query.isEmpty() ||
                    normalizedModuleBrowserSearch(entry.module.name).contains(query) ||
                    normalizedModuleBrowserSearch(entry.label).contains(query) ||
                    normalizedModuleBrowserSearch(category.name).contains(query)
            }
    }

    private fun moduleBrowserModules(category: Category): List<Module> =
        moduleBrowserEntries(category).map { it.module }

    private fun legacyFloydModuleLabel(module: Module): String =
        when (module) {
            FloydCape -> "Cape"
            else -> module.name
        }

    private fun legacyFloydEntriesFor(category: Category): List<LegacyModuleBrowserEntry> =
        when (category) {
            Category.RENDER -> listOf(
                moduleEntry(FloydXray),
                moduleEntry(FloydMobEsp),
                LegacyModuleBrowserEntry(FloydHiders, "Profile ID Hider", LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN, "Profile ID Hider"),
                LegacyModuleBrowserEntry(FloydHiders, "Server ID Hider", LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN, "Server ID Hider"),
                LegacyModuleBrowserEntry(FloydRender, "Time Changer", LegacyModuleBrowserKind.RENDER_BOOLEAN, "Time Changer"),
                LegacyModuleBrowserEntry(FloydMobEsp, "Stalk Player", LegacyModuleBrowserKind.RENDER_STALK),
                LegacyModuleBrowserEntry(FloydHud, "Inventory HUD", LegacyModuleBrowserKind.RENDER_HUD, "Inventory HUD"),
                LegacyModuleBrowserEntry(FloydRender, "Custom Scoreboard", LegacyModuleBrowserKind.RENDER_BOOLEAN, "Custom Scoreboard"),
                LegacyModuleBrowserEntry(FloydRender, "Borderless Window", LegacyModuleBrowserKind.RENDER_BORDERLESS, "Borderless Window"),
                LegacyModuleBrowserEntry(FloydRender, "Instance Name", LegacyModuleBrowserKind.RENDER_INSTANCE_NAME, "Instance Title"),
                LegacyModuleBrowserEntry(ClickGUIModule, "GUI Style", LegacyModuleBrowserKind.RENDER_GUI_STYLE),
                LegacyModuleBrowserEntry(FloydAnimations, "Attack Animation", LegacyModuleBrowserKind.RENDER_ANIMATIONS)
            )
            Category.HIDERS -> listOf(
                hiderEntry("No Hurt Camera"),
                hiderEntry("Remove Fire Overlay"),
                hiderEntry("Disable Hunger Bar"),
                hiderEntry("Hide Potion Effects"),
                hiderEntry("3rd Person Crosshair"),
                hiderEntry("Hide Entity Fire"),
                hiderEntry("Disable Arrows"),
                hiderEntry("Remove Falling Blocks"),
                hiderEntry("No Explosion Particles"),
                hiderEntry("Remove Tab Ping"),
                LegacyModuleBrowserEntry(FloydHiders, "No Armor", LegacyModuleBrowserKind.HIDER_NO_ARMOR, "Target")
            )
            Category.PLAYER -> listOf(
                LegacyModuleBrowserEntry(FloydCape, "Cape", LegacyModuleBrowserKind.PLAYER_CAPE, "Enabled"),
                LegacyModuleBrowserEntry(FloydConeHat, "Cone Hat", LegacyModuleBrowserKind.PLAYER_CONE_HAT, "Enabled"),
                LegacyModuleBrowserEntry(FloydNickHider, "Neck Hider", LegacyModuleBrowserKind.PLAYER_NICK_HIDER, "Enabled"),
                LegacyModuleBrowserEntry(FloydSkin, "Custom Skin", LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN, "Custom Skin"),
                LegacyModuleBrowserEntry(FloydPlayerSize, "Player Size", LegacyModuleBrowserKind.PLAYER_SIZE)
            )
            Category.CAMERA -> listOf(
                LegacyModuleBrowserEntry(FloydCamera, "Freecam", LegacyModuleBrowserKind.CAMERA_FREECAM),
                LegacyModuleBrowserEntry(FloydCamera, "Freelook", LegacyModuleBrowserKind.CAMERA_FREELOOK),
                LegacyModuleBrowserEntry(FloydCamera, "F5 Customizer", LegacyModuleBrowserKind.CAMERA_F5)
            )
            else -> emptyList()
        }.filter { entry -> ModuleManager.modules.containsValue(entry.module) }

    private fun moduleEntry(module: Module): LegacyModuleBrowserEntry =
        LegacyModuleBrowserEntry(module, legacyFloydModuleLabel(module), LegacyModuleBrowserKind.MODULE)

    private fun hiderEntry(settingName: String): LegacyModuleBrowserEntry =
        LegacyModuleBrowserEntry(FloydHiders, settingName, LegacyModuleBrowserKind.HIDER_BOOLEAN, settingName)

    private fun legacyModuleBrowserSettingsPage(entry: LegacyModuleBrowserEntry): Page? =
        when (entry.kind) {
            LegacyModuleBrowserKind.HIDER_NO_ARMOR -> Page.HIDERS
            LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN -> Page.HIDERS
            LegacyModuleBrowserKind.RENDER_BOOLEAN,
            LegacyModuleBrowserKind.RENDER_BORDERLESS,
            LegacyModuleBrowserKind.RENDER_INSTANCE_NAME -> Page.RENDER
            LegacyModuleBrowserKind.RENDER_STALK -> Page.MOB_ESP
            LegacyModuleBrowserKind.RENDER_HUD -> null
            LegacyModuleBrowserKind.RENDER_GUI_STYLE -> Page.GUI_STYLE
            LegacyModuleBrowserKind.RENDER_ANIMATIONS -> Page.ANIMATIONS
            LegacyModuleBrowserKind.PLAYER_CAPE -> Page.CAPE
            LegacyModuleBrowserKind.PLAYER_CONE_HAT -> Page.CONE_HAT
            LegacyModuleBrowserKind.PLAYER_NICK_HIDER -> Page.NICK_HIDER
            LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN -> Page.SKIN
            LegacyModuleBrowserKind.PLAYER_SIZE -> Page.PLAYER_SIZE
            else -> moduleSettingsPage(entry.module)
        }

    private fun normalizedModuleBrowserSearch(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun moduleBrowserPanelState(category: Category): ModuleBrowserPanelState =
        moduleBrowserPanelStates.getOrPut(category) { ModuleBrowserPanelState(6, moduleBrowserPanelTop) }

    private fun moduleBrowserPanelHeight(category: Category): Int {
        val state = moduleBrowserPanelState(category)
        val contentHeight = moduleBrowserEntries(category).size * moduleBrowserRowHeight
        return moduleBrowserHeaderHeight + if (state.collapsed) 0 else min(contentHeight, moduleBrowserMaxPanelContentHeight)
    }

    private fun moduleBrowserMaxScroll(category: Category): Int =
        max(0, moduleBrowserEntries(category).size * moduleBrowserRowHeight - moduleBrowserMaxPanelContentHeight)

    private fun clampModuleBrowserScrolls() {
        moduleBrowserPanelStates.forEach { (category, state) ->
            state.scroll = state.scroll.coerceIn(0, moduleBrowserMaxScroll(category))
        }
    }

    private fun moduleSettingsPage(module: Module): Page? = when (module) {
        ClickGUIModule -> Page.GUI_STYLE
        FloydRender -> Page.RENDER
        FloydXray -> Page.XRAY
        FloydAnimations -> Page.ANIMATIONS
        FloydMobEsp -> Page.MOB_ESP
        FloydHud -> null
        FloydHiders -> Page.HIDERS
        FloydNickHider -> Page.NICK_HIDER
        FloydPlayerSize -> Page.PLAYER_SIZE
        FloydCamera -> Page.CAMERA
        FloydSkin -> Page.SKIN
        FloydCape -> Page.CAPE
        FloydConeHat -> Page.CONE_HAT
        else -> null
    }

    private fun rowsFor(page: Page): List<LegacyRow> = when (page) {
        Page.HUB -> emptyList()
        Page.GUI_STYLE -> emptyList()
        Page.CLICK_GUI -> emptyList()
        Page.CAPE -> emptyList()
        Page.CONE_HAT -> emptyList()
        Page.XRAY -> emptyList()
        Page.RENDER -> listOf(
            toggleSettingRow(FloydHiders, "Server ID Hider", "Server ID Hider"),
            toggleSettingRow(FloydHiders, "Profile ID Hider", "Profile ID Hider"),
            headerRow("X-Ray"),
            toggleSettingRow(FloydXray, "Enabled", "X-Ray"),
            numberRow(FloydXray, "Opacity", "X-Ray Opacity") { "${(it * 100).roundToInt()}%" },
            actionRow("Edit Blocks", RowLayout.LEFT) { currentPage = Page.XRAY },
            actionRow("Reload Blocks", RowLayout.RIGHT) {
                ModuleManager.loadConfigurations()
                FloydXray.rebuildChunks()
                modMessage("Reloaded xray opaque blocks.")
            },
            headerRow("Mob ESP"),
            toggleModuleRow(FloydMobEsp, "Mob ESP", RowLayout.LEFT),
            navRow("Config", Page.MOB_ESP, RowLayout.RIGHT),
            headerRow("Other"),
            navRow("Hiders", Page.HIDERS, RowLayout.LEFT),
            navRow("Attack Animation", Page.ANIMATIONS, RowLayout.RIGHT),
            toggleSettingRow(FloydRender, "Time Changer", "Time Changer", RowLayout.LEFT),
            numberRow(FloydRender, "Time", "Time", RowLayout.RIGHT) { "${it.roundToInt()}%" },
            stalkRow(),
            toggleSettingRow(FloydRender, "Borderless Window", "Borderless Window"),
            actionRow({ "Window Title: ${stringSetting(FloydRender, "Instance Title")?.value?.ifBlank { "(default)" } ?: "?"}" }) {
                openWindowTitleEditor()
            }
        )
        Page.HIDERS -> listOf(
            toggleSettingRow(FloydHiders, "No Hurt Camera", "No Hurt Camera"),
            toggleSettingRow(FloydHiders, "Remove Fire Overlay", "Remove Fire Overlay"),
            toggleSettingRow(FloydHiders, "Hide Entity Fire", "Hide Entity Fire"),
            toggleSettingRow(FloydHiders, "Disable Arrows", "Disable Arrows"),
            toggleSettingRow(FloydHiders, "No Explosion Particles", "No Explosion Particles"),
            toggleSettingRow(FloydHiders, "Disable Hunger Bar", "Disable Hunger Bar"),
            toggleSettingRow(FloydHiders, "Hide Potion Effects", "Hide Potion Effects"),
            toggleSettingRow(FloydHiders, "3rd Person Crosshair", "3rd Person Crosshair"),
            toggleSettingRow(FloydHiders, "Remove Falling Blocks", "Remove Falling Blocks"),
            toggleSettingRow(FloydHiders, "Remove Tab Ping", "Remove Tab Ping"),
            selectorRow(FloydHiders, "Target", "No Armor", listOf("OFF", "SELF", "OTHERS", "ALL"))
        )
        Page.CAMERA -> listOf(
            headerRow("Freecam"),
            runtimeToggleRow(FloydCamera, "Freecam", "Freecam", RowLayout.LEFT) { FloydCamera.toggleFreecam() },
            numberRow(FloydCamera, "Speed", "Speed", RowLayout.RIGHT) { oneDecimal(it) },
            headerRow("Freelook"),
            runtimeToggleRow(FloydCamera, "Freelook", "Freelook", RowLayout.LEFT) { FloydCamera.toggleFreelook() },
            numberRow(FloydCamera, "Distance", "Dist", RowLayout.RIGHT) { oneDecimal(it) },
            headerRow("F5 Customizer"),
            toggleSettingRow(FloydCamera, "Disable Front Cam", "Disable Front", RowLayout.LEFT),
            toggleSettingRow(FloydCamera, "Disable Back Cam", "Disable Back", RowLayout.RIGHT),
            toggleSettingRow(FloydCamera, "No Third-Person Clipping", "Ignore Block Collisions"),
            toggleSettingRow(FloydCamera, "Scrolling Changes Distance", "Scrolling Changes Distance"),
            toggleSettingRow(FloydCamera, "Reset F5 Scrolling", "Reset F5 Scrolling"),
            numberRow(FloydCamera, "Camera Distance", "F5 Distance") { oneDecimal(it) }
        )
        Page.COSMETIC -> listOf(
            headerRow("Cosmetics"),
            toggleSettingRow(FloydSkin, "Custom Skin", "Custom Skin", RowLayout.LEFT),
            navRow("Config", Page.SKIN, RowLayout.RIGHT),
            toggleSettingRow(FloydCape, "Enabled", "Cape", RowLayout.LEFT),
            navRow("Config", Page.CAPE, RowLayout.RIGHT),
            toggleSettingRow(FloydConeHat, "Enabled", "Cone Hat", RowLayout.LEFT),
            navRow("Config", Page.CONE_HAT, RowLayout.RIGHT),
            headerRow("Player Size"),
            selectorRow(FloydPlayerSize, "Target", "Target", listOf("Self", "Real Players", "All")),
            numberRow(FloydPlayerSize, "X", "Size X") { oneDecimal(it) },
            numberRow(FloydPlayerSize, "Y", "Size Y") { oneDecimal(it) },
            numberRow(FloydPlayerSize, "Z", "Size Z") { oneDecimal(it) }
        )
        Page.NICK_HIDER -> listOf(
            toggleSettingRow(FloydNickHider, "Enabled", "Nick Hider"),
            actionRow("Name Mappings") { currentPage = Page.NAME_MAPPINGS },
            actionRow("Reload Names") {
                ModuleManager.loadConfigurations()
                modMessage("Reloaded name mappings.")
            },
            defaultNickRow(),
            actionRow("Edit Default Nick") { openDefaultNickEditor() },
            navRow("Player Size", Page.PLAYER_SIZE)
        )
        Page.SKIN -> listOf(
            headerRow("Skin"),
            toggleSettingRow(FloydSkin, "Custom Skin", "Custom Skin", RowLayout.LEFT),
            actionSettingRow(FloydSkin, "Open Skin Folder", "Open Folder", RowLayout.RIGHT),
            toggleSettingRow(FloydSkin, "Self", "Apply to me", RowLayout.LEFT),
            toggleSettingRow(FloydSkin, "Others", "Others", RowLayout.RIGHT),
            skinSelectionRow()
        )
        Page.MOB_ESP -> listOf(
            headerRow("Toggles"),
            toggleSettingRow(FloydMobEsp, "Tracers", "Tracers"),
            toggleSettingRow(FloydMobEsp, "Hitboxes", "Hitboxes"),
            toggleSettingRow(FloydMobEsp, "Star Mobs", "Star Mobs"),
            headerRow("Colors"),
            colorRow(FloydMobEsp, "Default ESP Color", "Default ESP Color", RowLayout.LEFT),
            toggleSettingRow(FloydMobEsp, "Default Chroma", "Chroma", RowLayout.RIGHT),
            colorRow(FloydMobEsp, "Tracer Color", "Stalk Tracer Color", RowLayout.LEFT),
            toggleSettingRow(FloydMobEsp, "Stalk Chroma", "Chroma", RowLayout.RIGHT),
            navRow("Edit Filters", Page.MOB_ESP_FILTERS)
        )
        Page.MOB_ESP_FILTERS -> listOf(
            headerRow("Filters"),
            actionSettingRow(FloydMobEsp, "Add Looked At Name", "Add Name", RowLayout.LEFT),
            actionSettingRow(FloydMobEsp, "Add Looked At Type", "Add Type", RowLayout.RIGHT),
            actionSettingRow(FloydMobEsp, "List Filters", "List Filters", RowLayout.LEFT),
            actionSettingRow(FloydMobEsp, "Clear Filters", "Clear Filters", RowLayout.RIGHT)
        )
        Page.ANIMATIONS -> listOf(
            toggleModuleRow(FloydAnimations, "Attack Animation"),
            headerRow("Position"),
            numberRow(FloydAnimations, "Pos X", "Pos X") { it.roundToInt().toString() },
            numberRow(FloydAnimations, "Pos Y", "Pos Y") { it.roundToInt().toString() },
            numberRow(FloydAnimations, "Pos Z", "Pos Z") { it.roundToInt().toString() },
            headerRow("Rotation"),
            numberRow(FloydAnimations, "Rot X", "Rot X") { it.roundToInt().toString() },
            numberRow(FloydAnimations, "Rot Y", "Rot Y") { it.roundToInt().toString() },
            numberRow(FloydAnimations, "Rot Z", "Rot Z") { it.roundToInt().toString() },
            headerRow("Other"),
            numberRow(FloydAnimations, "Scale", "Scale") { twoDecimal(it) },
            numberRow(FloydAnimations, "Swing Duration", "Swing Duration") { it.roundToInt().toString() },
            toggleSettingRow(FloydAnimations, "Cancel Re-Equip", "Cancel Re-Equip"),
            toggleSettingRow(FloydAnimations, "Hide Hand", "Hide Hand"),
            toggleSettingRow(FloydAnimations, "Classic Click", "Classic Click")
        )
        Page.PLAYER_SIZE -> listOf(
            toggleModuleRow(FloydPlayerSize, "Player Size"),
            headerRow("Player Size"),
            selectorRow(FloydPlayerSize, "Target", "Target", listOf("SELF", "REAL PLAYERS", "ALL")),
            numberRow(FloydPlayerSize, "X", "Size X") { oneDecimal(it) },
            numberRow(FloydPlayerSize, "Y", "Size Y") { oneDecimal(it) },
            numberRow(FloydPlayerSize, "Z", "Size Z") { oneDecimal(it) }
        )
        Page.NAME_MAPPINGS -> listOf(
            defaultNickRow(),
            actionRow("Edit Default Nick") { openDefaultNickEditor() },
            actionRow("Add Mapping...") { openMappingRealEditor() },
            headerRow("Mappings"),
            *nameMappingRows().toTypedArray()
        )
    }

    private fun openDefaultNickEditor() {
        textEditor = TextEditor(
            title = "Default Nick",
            value = FloydNickHider.nickname,
            placeholder = "Replacement name",
            hint = "Enter saves, Esc cancels",
            maxLength = 32
        ) { value ->
            FloydNickHider.setSelfNickname(value)
            ModuleManager.saveConfigurations()
        }
    }

    private fun openMappingRealEditor() {
        clearInlineNameMappingAdd()
        modulePopupMappingFocusedField = null
        modulePopupMappingOriginalBuffer = ""
        modulePopupMappingFakeBuffer = ""
        textEditor = TextEditor(
            title = "Map Real Name",
            value = "",
            placeholder = "Real player name",
            hint = "Enter the real IGN",
            maxLength = 32
        ) { value ->
            if (value.isNotBlank()) {
                pendingMappingReal = value.trim()
                openMappingFakeEditor(value.trim())
            }
        }
    }

    private fun openMappingFakeEditor(realName: String) {
        textEditor = TextEditor(
            title = "Fake Name for $realName",
            value = FloydNickHider.nickname,
            placeholder = "Fake display name",
            hint = "Enter saves mapping",
            maxLength = 32
        ) { value ->
            val real = pendingMappingReal ?: realName
            if (real.isNotBlank() && value.isNotBlank()) {
                FloydNickHider.addNameMapping(real, value.trim())
                ModuleManager.saveConfigurations()
            }
            pendingMappingReal = null
        }
    }

    private fun openXrayBlockEditor() {
        textEditor = TextEditor(
            title = "Add X-Ray Block",
            value = "",
            placeholder = "minecraft:glass",
            hint = "Enter saves block ID",
            maxLength = 96
        ) { value ->
            val blockId = runCatching { FloydXray.validOpaqueBlockId(value.trim()) }.getOrElse {
                modMessage(it.message ?: "Invalid block ID.")
                return@TextEditor
            }
            FloydXray.addOpaqueBlock(blockId)
            ModuleManager.saveConfigurations()
        }
    }

    private fun openMobFilterNameEditor() {
        textEditor = TextEditor(
            title = "Add Mob Name Filter",
            value = "",
            placeholder = "Zombie",
            hint = "Enter saves mob name",
            maxLength = 64
        ) { value ->
            val name = runCatching { FloydMobEsp.validUserNameFilter(value) }.getOrElse {
                modMessage(it.message ?: "Invalid mob name.")
                return@TextEditor
            }
            FloydMobEsp.addNameFilter(name)
            ModuleManager.saveConfigurations()
        }
    }

    private fun openMobFilterTypeEditor() {
        textEditor = TextEditor(
            title = "Add Mob Type Filter",
            value = "",
            placeholder = "minecraft:zombie",
            hint = "Enter saves entity type ID",
            maxLength = 96
        ) { value ->
            val type = runCatching { FloydMobEsp.validTypeFilterId(value.trim()) }.getOrElse {
                modMessage(it.message ?: "Invalid entity type ID.")
                return@TextEditor
            }
            FloydMobEsp.addTypeFilter(type)
            ModuleManager.saveConfigurations()
        }
    }

    private fun openWindowTitleEditor() {
        val setting = stringSetting(FloydRender, "Instance Title") ?: return
        textEditor = TextEditor(
            title = "Window Title",
            value = setting.value,
            placeholder = "Instance name / taskbar title",
            hint = "Empty restores Minecraft title",
            maxLength = 64
        ) { value ->
            setting.value = value
            ModuleManager.saveConfigurations()
        }
    }

    private fun openColorEditor(module: Module, settingName: String, title: String) {
        val setting = colorSetting(module, settingName) ?: return
        val chromaName = chromaSettingName(module, settingName)
        val chromaSetting = chromaName?.let { booleanSetting(module, it) }
        val hsb = RGBtoHSB(setting.value.red, setting.value.green, setting.value.blue, FloatArray(3))
        textEditor = null
        colorPicker = ColorPickerEditor(
            title = title,
            module = module,
            settingName = settingName,
            chromaSettingName = chromaName,
            originalColor = setting.value.copy(),
            originalChroma = chromaSetting?.enabled ?: false,
            hue = hsb[0],
            saturation = hsb[1],
            brightness = hsb[2],
            hex = setting.value.hex(includeAlpha = false),
            chromaEnabled = chromaSetting?.enabled ?: false
        )
    }

    private fun openStyleColorEditor(target: StyleTarget) {
        val setting = colorSetting(ClickGUIModule, target.colorSetting) ?: return
        val fadeSetting = colorSetting(ClickGUIModule, target.fadeColorSetting)
        val hsb = RGBtoHSB(setting.value.red, setting.value.green, setting.value.blue, FloatArray(3))
        val fade = fadeSetting?.value ?: setting.value
        val fadeHsb = RGBtoHSB(fade.red, fade.green, fade.blue, FloatArray(3))
        textEditor = null
        colorPicker = ColorPickerEditor(
            title = target.label,
            module = ClickGUIModule,
            settingName = target.colorSetting,
            chromaSettingName = target.chromaSetting,
            originalColor = setting.value.copy(),
            originalChroma = booleanSetting(ClickGUIModule, target.chromaSetting)?.enabled ?: false,
            hue = hsb[0],
            saturation = hsb[1],
            brightness = hsb[2],
            hex = setting.value.hex(includeAlpha = false),
            chromaEnabled = booleanSetting(ClickGUIModule, target.chromaSetting)?.enabled ?: false,
            fadeSupported = true,
            fadeSettingName = target.fadeColorSetting,
            fadeEnabledSettingName = target.fadeSetting,
            originalFadeColor = fade.copy(),
            originalFadeEnabled = booleanSetting(ClickGUIModule, target.fadeSetting)?.enabled ?: false,
            fadeHue = fadeHsb[0],
            fadeSaturation = fadeHsb[1],
            fadeBrightness = fadeHsb[2]
        )
    }

    private fun handleColorPickerClick(picker: ColorPickerEditor, x: Double, y: Double): Boolean {
        when {
            colorPickerApplyButton().contains(x, y) -> {
                applyColorPicker(picker)
                return true
            }
            colorPickerCancelButton().contains(x, y) -> {
                cancelColorPicker(picker)
                return true
            }
            colorPickerChromaButton().contains(x, y) -> {
                picker.chromaEnabled = !picker.chromaEnabled
                if (picker.chromaEnabled && picker.fadeSupported) picker.fadeEnabled = false
                return true
            }
            picker.fadeSupported && colorPickerFadeButton().contains(x, y) -> {
                picker.fadeEnabled = !picker.fadeEnabled
                if (picker.fadeEnabled) picker.chromaEnabled = false
                return true
            }
            picker.fadeSupported && colorPickerEditTargetButton().contains(x, y) -> {
                picker.editingFade = !picker.editingFade
                updateColorPickerHex(picker)
                return true
            }
            picker.fadeSupported && colorPickerBasePreviewRect().contains(x, y) -> {
                picker.editingFade = false
                updateColorPickerHex(picker)
                return true
            }
            picker.fadeSupported && colorPickerFadePreviewRect().contains(x, y) -> {
                picker.editingFade = true
                updateColorPickerHex(picker)
                return true
            }
            !picker.chromaEnabled && colorPickerSvRect().contains(x, y) -> {
                picker.dragTarget = ColorPickerDrag.SV
                updateColorPickerSv(picker, x - colorPickerSvRect().left, y - colorPickerSvRect().top)
                return true
            }
            !picker.chromaEnabled && colorPickerHueRect().contains(x, y) -> {
                picker.dragTarget = ColorPickerDrag.HUE
                updateColorPickerHue(picker, y - colorPickerHueRect().top)
                return true
            }
            colorPickerRect().contains(x, y) -> return true
            else -> return false
        }
    }

    private fun updateColorPickerSv(picker: ColorPickerEditor, localX: Double, localY: Double) {
        if (picker.editingFade) {
            picker.fadeSaturation = (localX / (colorPickerSvSize - 1)).toFloat().coerceIn(0f, 1f)
            picker.fadeBrightness = (1.0 - localY / (colorPickerSvSize - 1)).toFloat().coerceIn(0f, 1f)
        } else {
            picker.saturation = (localX / (colorPickerSvSize - 1)).toFloat().coerceIn(0f, 1f)
            picker.brightness = (1.0 - localY / (colorPickerSvSize - 1)).toFloat().coerceIn(0f, 1f)
        }
        updateColorPickerHex(picker)
    }

    private fun updateColorPickerHue(picker: ColorPickerEditor, localY: Double) {
        if (picker.editingFade) picker.fadeHue = (localY / (colorPickerHueHeight - 1)).toFloat().coerceIn(0f, 1f)
        else picker.hue = (localY / (colorPickerHueHeight - 1)).toFloat().coerceIn(0f, 1f)
        updateColorPickerHex(picker)
    }

    private fun updateColorPickerHex(picker: ColorPickerEditor) {
        picker.hex = colorToHex(picker.currentColor())
    }

    private fun syncColorPickerFromHex(picker: ColorPickerEditor) {
        val parsed = parseHexColor(picker.hex) ?: return
        val hsb = RGBtoHSB((parsed ushr 16) and 0xFF, (parsed ushr 8) and 0xFF, parsed and 0xFF, FloatArray(3))
        if (picker.editingFade) {
            picker.fadeHue = hsb[0]
            picker.fadeSaturation = hsb[1]
            picker.fadeBrightness = hsb[2]
        } else {
            picker.hue = hsb[0]
            picker.saturation = hsb[1]
            picker.brightness = hsb[2]
        }
    }

    private fun applyColorPicker(picker: ColorPickerEditor) {
        val setting = colorSetting(picker.module, picker.settingName) ?: return
        setting.value = Color(picker.currentColor())
        picker.chromaSettingName?.let { name ->
            booleanSetting(picker.module, name)?.enabled = picker.chromaEnabled
        }
        picker.fadeSettingName?.let { name ->
            colorSetting(picker.module, name)?.value = Color(picker.fadeColor())
        }
        picker.fadeEnabledSettingName?.let { name ->
            booleanSetting(picker.module, name)?.enabled = picker.fadeEnabled
        }
        ModuleManager.saveConfigurations()
        colorPicker = null
    }

    private fun cancelColorPicker(picker: ColorPickerEditor) {
        colorSetting(picker.module, picker.settingName)?.value = picker.originalColor.copy()
        picker.chromaSettingName?.let { name ->
            booleanSetting(picker.module, name)?.enabled = picker.originalChroma
        }
        picker.fadeSettingName?.let { name ->
            colorSetting(picker.module, name)?.value = picker.originalFadeColor.copy()
        }
        picker.fadeEnabledSettingName?.let { name ->
            booleanSetting(picker.module, name)?.enabled = picker.originalFadeEnabled
        }
        colorPicker = null
    }

    private fun chromaSettingName(module: Module, settingName: String): String? =
        when {
            module === FloydMobEsp && settingName == "Default ESP Color" -> "Default Chroma"
            module === FloydMobEsp && settingName == "Tracer Color" -> "Stalk Chroma"
            else -> null
        }

    private fun colorPickerRect(): Rect = Rect((width - colorPickerWidth) / 2, (height - colorPickerHeight) / 2, colorPickerWidth, colorPickerHeight)
    private fun colorPickerSvRect(): Rect = colorPickerRect().let { Rect(it.left + 16, it.top + 26, colorPickerSvSize, colorPickerSvSize) }
    private fun colorPickerHueRect(): Rect = colorPickerSvRect().let { Rect(it.right + 12, it.top, colorPickerHueWidth, colorPickerHueHeight) }
    private fun colorPickerPreviewRect(): Rect = colorPickerHueRect().let { Rect(it.right + 12, it.top, colorPickerPreviewSize, colorPickerPreviewSize) }
    private fun colorPickerBasePreviewRect(): Rect = colorPickerPreviewRect().let { Rect(it.left, it.bottom + colorPickerPreviewGap, colorPickerPreviewSize, colorPickerPreviewSize) }
    private fun colorPickerFadePreviewRect(): Rect = colorPickerBasePreviewRect().let { Rect(it.right + colorPickerPreviewGap, it.top, colorPickerPreviewSize, colorPickerPreviewSize) }
    private fun colorPickerHexRect(): Rect = colorPickerRect().let { rect ->
        val hashWidth = mc.font.width("#") + 4
        Rect(rect.left + 16 + hashWidth, rect.bottom - 74, 76, 18)
    }
    private fun colorPickerChromaButton(): Rect = colorPickerHexRect().let { Rect(it.right + 10, it.top, 74, 18) }
    private fun colorPickerFadeButton(): Rect = colorPickerChromaButton().let { Rect(it.right + 6, it.top, 74, 18) }
    private fun colorPickerEditTargetButton(): Rect = colorPickerFadeButton().let { Rect(it.right + 6, it.top, 96, 18) }
    private fun colorPickerApplyButton(): Rect = colorPickerRect().let { Rect(it.left + 16, it.bottom - 34, 96, 18) }
    private fun colorPickerCancelButton(): Rect = colorPickerRect().let { Rect(it.right - 112, it.bottom - 34, 96, 18) }

    private fun colorToHex(color: Int): String = "%06X".format(color and 0x00FFFFFF)

    private fun parseHexColor(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (!hex.matches(Regex("[0-9a-fA-F]{6}"))) return null
        return 0xFF000000.toInt() or hex.toInt(16)
    }

    private fun saveTextEditor(editor: TextEditor) {
        val before = textEditor
        editor.onSave(editor.value.trim())
        if (textEditor === before) textEditor = null
    }

    private fun defaultNickRow(): LegacyRow =
        LegacyRow(label = { "Default Nick: ${FloydNickHider.nickname}" }) { button ->
            val current = nickPresets.indexOfFirst { it.equals(FloydNickHider.nickname, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
            val next = if (button == 1) current - 1 else current + 1
            FloydNickHider.nickname = nickPresets[next.floorMod(nickPresets.size)]
            ModuleManager.saveConfigurations()
        }

    private fun skinSelectionRow(): LegacyRow =
        LegacyRow(label = {
            val selected = stringSetting(FloydSkin, "Skin")?.value?.ifBlank { "No skin selected" } ?: "?"
            val arrow = if (skinDropdownOpen) "^" else "v"
            "Skin: $selected $arrow"
        }, role = RowRole.SKIN_DROPDOWN) {
            FloydSkin.availableSkinFiles()
            skinDropdownScroll = skinDropdownScroll.coerceIn(0, max(0, FloydSkin.availableSkinFiles().size - skinDropdownMaxVisible))
            skinDropdownOpen = !skinDropdownOpen
        }

    private fun skinDropdownVisibleItems(): List<String> =
        FloydSkin.availableSkinFiles()
            .drop(skinDropdownScroll)
            .take(skinDropdownMaxVisible)

    private fun handleSkinDropdownClick(x: Double, y: Double): Boolean {
        if (!skinDropdownList.contains(x, y)) return false
        val skins = FloydSkin.availableSkinFiles()
        if (skins.isEmpty()) {
            skinDropdownOpen = false
            return true
        }
        val index = ((y - skinDropdownList.top) / skinDropdownRowHeight).toInt() + skinDropdownScroll
        val skin = skins.getOrNull(index) ?: return true
        FloydSkin.selectSkin(skin)
        skinDropdownOpen = false
        return true
    }

    private fun handleSkinSettingsClick(x: Double, y: Double): Boolean {
        val hit = skinSettingsHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        when (hit.kind) {
            SkinSettingsHitKind.TOGGLE -> toggleBoolean(FloydSkin, hit.settingName)
            SkinSettingsHitKind.OPEN_FOLDER -> {
                moduleAction(FloydSkin, "Open Skin Folder")?.action()
                ModuleManager.saveConfigurations()
            }
            SkinSettingsHitKind.DROPDOWN -> {
                FloydSkin.availableSkinFiles()
                skinDropdownScroll = skinDropdownScroll.coerceIn(0, max(0, FloydSkin.availableSkinFiles().size - skinDropdownMaxVisible))
                skinDropdownOpen = !skinDropdownOpen
            }
        }
        return true
    }

    private fun coneSpecs(): List<ConeSpec> = listOf(
        ConeSpec("Height", "Height", 0.1, 1.5, { "%.2f".format(it) }, { "%.2f".format(it) }),
        ConeSpec("Radius", "Radius", 0.05, 0.8, { "%.2f".format(it) }, { "%.2f".format(it) }),
        ConeSpec("Y Offset", "Y Offset", -1.5, 0.5, { "%.2f".format(it) }, { "%.2f".format(it) }),
        ConeSpec("Rotation", "Rotation", 0.0, 360.0, { "${it.roundToInt()}deg" }, { it.roundToInt().toString() }),
        ConeSpec("Spin Speed", "Spin", 0.0, 360.0, { "%.1fdeg/s".format(it) }, { "%.1f".format(it) })
    )

    private fun coneRowY(top: Int, row: Int): Int = top + 26 + row * 26

    private fun coneDropdownVisibleItems(): List<String> =
        FloydConeHat.availableImageFiles()
            .drop(coneDropdownScroll)
            .take(coneDropdownMaxVisible)

    private fun handleConePageClick(x: Double, y: Double): Boolean {
        if (coneEditingIndex >= 0 && coneInputBoxes.getOrNull(coneEditingIndex)?.contains(x, y) != true) applyConeEdit()
        coneInputBoxes.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { index ->
            startConeEdit(index)
            return true
        }
        coneRows.firstOrNull { it.slider.contains(x, y) }?.let { row ->
            val percent = ((x - row.slider.left) / row.slider.width).coerceIn(0.0, 1.0)
            val value = row.spec.min + percent * (row.spec.max - row.spec.min)
            FloydConeHat.setSetting(row.spec.settingName, value)
            return true
        }
        if (coneDropdownButton.contains(x, y)) {
            FloydConeHat.availableImageFiles()
            coneDropdownScroll = coneDropdownScroll.coerceIn(0, max(0, FloydConeHat.availableImageFiles().size - coneDropdownMaxVisible))
            coneDropdownOpen = !coneDropdownOpen
            return true
        }
        if (coneOpenFolderButton.contains(x, y)) {
            moduleAction(FloydConeHat, "Open Cone Folder")?.action()
            ModuleManager.saveConfigurations()
            return true
        }
        return false
    }

    private fun handleConeDropdownClick(x: Double, y: Double): Boolean {
        if (!coneDropdownList.contains(x, y)) return false
        val images = FloydConeHat.availableImageFiles()
        if (images.isEmpty()) {
            coneDropdownOpen = false
            return true
        }
        val index = ((y - coneDropdownList.top) / coneDropdownRowHeight).toInt() + coneDropdownScroll
        val image = images.getOrNull(index) ?: return true
        FloydConeHat.selectImage(image)
        coneDropdownOpen = false
        return true
    }

    private fun startConeEdit(index: Int) {
        coneEditingIndex = index
        val spec = coneSpecs().getOrNull(index) ?: return
        val value = numberSetting(FloydConeHat, spec.settingName)?.numericValue() ?: spec.min
        coneEditBuffer = spec.inputFormat(value)
    }

    private fun applyConeEdit() {
        val index = coneEditingIndex
        val spec = coneSpecs().getOrNull(index) ?: run {
            coneEditingIndex = -1
            coneEditBuffer = ""
            return
        }
        coneEditBuffer.toDoubleOrNull()?.let { FloydConeHat.setSetting(spec.settingName, it) }
        coneEditingIndex = -1
        coneEditBuffer = ""
    }

    private fun handleMobFilterEditorClick(x: Double, y: Double): Boolean {
        val hit = mobFilterHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        when (hit.kind) {
            MobFilterHitKind.ADD_MANUAL_NAME -> {
                if (currentPage == Page.MOB_ESP_FILTERS) {
                    openMobFilterNameEditor()
                    return true
                }
                if (hit.submit) submitModulePopupActionInput(ModulePopupExtraKind.MOB_ADD_NAME)
                else focusModulePopupActionInput(ModulePopupExtraKind.MOB_ADD_NAME)
                return true
            }
            MobFilterHitKind.ADD_MANUAL_TYPE -> {
                if (currentPage == Page.MOB_ESP_FILTERS) {
                    openMobFilterTypeEditor()
                    return true
                }
                if (hit.submit) submitModulePopupActionInput(ModulePopupExtraKind.MOB_ADD_TYPE)
                else focusModulePopupActionInput(ModulePopupExtraKind.MOB_ADD_TYPE)
                return true
            }
            MobFilterHitKind.ADD_NAME -> FloydMobEsp.addNameFilter(FloydMobEsp.validUserNameFilter(hit.key))
            MobFilterHitKind.REMOVE_NAME -> {
                FloydMobEsp.removeNameFilter(hit.key)
                if (expandedMobFilterColor?.key.equals(hit.key, ignoreCase = true)) expandedMobFilterColor = null
            }
            MobFilterHitKind.ADD_TYPE -> FloydMobEsp.addTypeFilter(FloydMobEsp.validTypeFilterId(hit.key))
            MobFilterHitKind.REMOVE_TYPE -> {
                FloydMobEsp.removeUserTypeFilter(hit.key)
                if (expandedMobFilterColor?.key.equals(hit.key, ignoreCase = true)) expandedMobFilterColor = null
            }
            MobFilterHitKind.COLOR -> toggleMobFilterInlineColor(hit.key)
            MobFilterHitKind.PICKER_SV -> {
                if (mobFilterInlineColor(expandedMobFilterColor ?: return false).chroma) return true
                mobFilterPickerDrag = MobFilterHitKind.PICKER_SV
                updateMobFilterInlinePicker(hit.bounds, x, y)
            }
            MobFilterHitKind.PICKER_HUE -> {
                if (mobFilterInlineColor(expandedMobFilterColor ?: return false).chroma) return true
                mobFilterPickerDrag = MobFilterHitKind.PICKER_HUE
                updateMobFilterInlinePicker(hit.bounds, x, y)
            }
            MobFilterHitKind.PICKER_CHROMA -> {
                val target = expandedMobFilterColor ?: return true
                val current = mobFilterInlineColor(target)
                saveMobFilterInlineColor(target, current.argb, !current.chroma)
            }
        }
        if (hit.kind == MobFilterHitKind.ADD_NAME || hit.kind == MobFilterHitKind.REMOVE_NAME || hit.kind == MobFilterHitKind.ADD_TYPE || hit.kind == MobFilterHitKind.REMOVE_TYPE) {
            ModuleManager.saveConfigurations()
        }
        return true
    }

    private fun handleMobEspEditorClick(x: Double, y: Double): Boolean {
        val hit = mobEspHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        when (hit.kind) {
            MobEspHitKind.TOGGLE -> {
                booleanSetting(FloydMobEsp, hit.settingName)?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            MobEspHitKind.COLOR_PICK -> openColorEditor(
                FloydMobEsp,
                hit.settingName,
                if (hit.settingName == "Tracer Color") "Stalk Tracer" else "Default ESP"
            )
            MobEspHitKind.NAV_FILTERS -> currentPage = Page.MOB_ESP_FILTERS
        }
        return true
    }

    private fun handleCosmeticEditorClick(x: Double, y: Double): Boolean {
        val hit = cosmeticHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        when (hit.kind) {
            CosmeticHitKind.TOGGLE_SKIN -> toggleBoolean(FloydSkin, "Custom Skin")
            CosmeticHitKind.TOGGLE_CAPE -> toggleBoolean(FloydCape, "Enabled")
            CosmeticHitKind.TOGGLE_CONE -> toggleBoolean(FloydConeHat, "Enabled")
            CosmeticHitKind.NAV_CONFIG -> currentPage = Page.valueOf(hit.settingName)
            CosmeticHitKind.TARGET -> {
                selectorSetting(FloydPlayerSize, "Target")?.let { setting ->
                    setting.value = setting.value + 1
                    ModuleManager.saveConfigurations()
                }
            }
            CosmeticHitKind.SLIDER -> {
                val spec = cosmeticSliderSpecs().firstOrNull { it.settingName == hit.settingName } ?: return true
                val target = CosmeticSliderTarget(spec, hit.bounds)
                activeCosmeticSlider = target
                updateCosmeticSlider(target, x)
            }
        }
        return true
    }

    private fun toggleBoolean(module: Module, settingName: String) {
        booleanSetting(module, settingName)?.let { setting ->
            setting.enabled = !setting.enabled
            ModuleManager.saveConfigurations()
        }
    }

    private fun updateCosmeticSlider(target: CosmeticSliderTarget, x: Double) {
        val pct = ((x - target.bounds.left) / target.bounds.width).coerceIn(0.0, 1.0)
        val value = target.spec.min + pct * (target.spec.max - target.spec.min)
        numberSetting(FloydPlayerSize, target.spec.settingName)?.setNumericValue(value)
        ModuleManager.saveConfigurations()
    }

    private fun toggleMobFilterInlineColor(key: String) {
        val target = FloydMobEsp.nameFilterIds().firstOrNull { it.equals(key, ignoreCase = true) }?.let { MobFilterColorTarget(it, true) }
            ?: FloydMobEsp.typeFilterIds().firstOrNull { it.equals(key, ignoreCase = true) }?.let { MobFilterColorTarget(it, false) }
            ?: return
        if (expandedMobFilterColor == target) {
            expandedMobFilterColor = null
            mobFilterPickerDrag = null
            return
        }
        expandedMobFilterColor = target
        val color = mobFilterInlineColor(target)
        syncMobFilterPickerFromColor(color.argb)
    }

    private fun mobFilterInlineColor(target: MobFilterColorTarget): MobFilterInlineColor {
        val summary = if (target.isName) FloydMobEsp.colorSummaryForName(target.key) else FloydMobEsp.colorSummaryForType(target.key)
        val summaryHex = summary.substringAfter("#", "").substringBefore(" ").takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) }
        val argb = summaryHex?.let { 0xFF000000.toInt() or it.toInt(16) }
            ?: (colorSetting(FloydMobEsp, "Default ESP Color")?.value?.rgba ?: 0xFFFFFFFF.toInt())
        val chroma = summary.contains("chroma", ignoreCase = true)
            || (summary == "default" && (booleanSetting(FloydMobEsp, "Default Chroma")?.enabled == true))
        return MobFilterInlineColor(argb, chroma)
    }

    private fun saveMobFilterInlineColor(target: MobFilterColorTarget, argb: Int, chroma: Boolean) {
        if (target.isName) FloydMobEsp.setNameFilterColor(target.key, colorToHex(argb), chroma)
        else FloydMobEsp.setTypeFilterColor(target.key, colorToHex(argb), chroma)
        ModuleManager.saveConfigurations()
    }

    private fun syncMobFilterPickerFromColor(argb: Int) {
        val hsb = RGBtoHSB((argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, argb and 0xFF, FloatArray(3))
        mobFilterPickerHue = hsb[0]
        mobFilterPickerSaturation = hsb[1]
        mobFilterPickerBrightness = hsb[2]
    }

    private fun updateMobFilterInlinePicker(bounds: Rect, x: Double, y: Double) {
        val target = expandedMobFilterColor ?: return
        when (mobFilterPickerDrag) {
            MobFilterHitKind.PICKER_SV -> {
                mobFilterPickerSaturation = ((x - bounds.left) / (bounds.width - 1)).toFloat().coerceIn(0f, 1f)
                mobFilterPickerBrightness = (1.0 - (y - bounds.top) / (bounds.height - 1)).toFloat().coerceIn(0f, 1f)
            }
            MobFilterHitKind.PICKER_HUE -> {
                mobFilterPickerHue = ((y - bounds.top) / (bounds.height - 1)).toFloat().coerceIn(0f, 1f)
            }
            else -> return
        }
        saveMobFilterInlineColor(target, HSBtoRGB(mobFilterPickerHue, mobFilterPickerSaturation, mobFilterPickerBrightness) or 0xFF000000.toInt(), chroma = false)
    }

    private fun updateMobFilterInlinePickerDrag(x: Double, y: Double) {
        val kind = mobFilterPickerDrag ?: return
        val hit = mobFilterHitEntries.firstOrNull { it.kind == kind } ?: return
        updateMobFilterInlinePicker(hit.bounds, x, y)
    }

    private fun handleXrayEditorClick(x: Double, y: Double): Boolean {
        val hit = xrayHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        if (hit.add) FloydXray.addOpaqueBlock(hit.blockId) else FloydXray.removeOpaqueBlock(hit.blockId)
        ModuleManager.saveConfigurations()
        return true
    }

    private fun handleNameMappingEditorClick(x: Double, y: Double): Boolean {
        val hit = nameMappingHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        when (hit.kind) {
            NameMappingHitKind.ADD_MANUAL -> openMappingRealEditor()
            NameMappingHitKind.ADD_MANUAL_ORIGINAL -> {
                modulePopupMappingFocusedField = ModulePopupMappingField.ORIGINAL
                activeModulePopupActionInput = null
                modulePopupActionInputBuffer = ""
            }
            NameMappingHitKind.ADD_MANUAL_FAKE -> {
                modulePopupMappingFocusedField = ModulePopupMappingField.FAKE
                activeModulePopupActionInput = null
                modulePopupActionInputBuffer = ""
            }
            NameMappingHitKind.ADD_MANUAL_SAVE -> submitModulePopupNameMappingInput()
            NameMappingHitKind.ADD -> {
                addingMappingName = hit.realName
                addingMappingBuffer = ""
            }
            NameMappingHitKind.ADD_TEXT -> Unit
            NameMappingHitKind.SAVE_ADD -> saveInlineNameMappingAdd()
            NameMappingHitKind.REMOVE -> {
                FloydNickHider.removeNameMapping(hit.realName)
                ModuleManager.saveConfigurations()
                revealedMappingNames.remove(hit.realName)
                nameMappingScroll = nameMappingScroll.coerceIn(0, max(0, nameMappingContentHeight() - nameMappingContentArea().height))
            }
            NameMappingHitKind.REVEAL -> {
                if (!revealedMappingNames.remove(hit.realName)) revealedMappingNames.add(hit.realName)
            }
        }
        return true
    }

    private fun submitModulePopupNameMappingInput() {
        val real = modulePopupMappingOriginalBuffer.trim()
        val fake = modulePopupMappingFakeBuffer.trim()
        if (real.isBlank() || fake.isBlank()) return
        FloydNickHider.addNameMapping(real, fake)
        ModuleManager.saveConfigurations()
        revealedMappingNames.remove(real)
        modulePopupMappingOriginalBuffer = ""
        modulePopupMappingFakeBuffer = ""
        modulePopupMappingFocusedField = ModulePopupMappingField.ORIGINAL
        modulePopupExtraScrolls[ModulePopupExtraKind.NAME_MAPPINGS] = 0
    }

    private fun saveInlineNameMappingAdd() {
        val real = addingMappingName ?: return
        val fake = addingMappingBuffer.trim()
        if (fake.isNotEmpty()) {
            FloydNickHider.addNameMapping(real, fake)
            ModuleManager.saveConfigurations()
            revealedMappingNames.remove(real)
        }
        clearInlineNameMappingAdd()
        nameMappingScroll = nameMappingScroll.coerceIn(0, max(0, nameMappingContentHeight() - nameMappingContentArea().height))
    }

    private fun clearInlineNameMappingAdd() {
        addingMappingName = null
        addingMappingBuffer = ""
    }

    private fun handleAnimationsEditorClick(x: Double, y: Double): Boolean {
        val hit = animationHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        when (hit.kind) {
            AnimationHitKind.TOGGLE_MODULE -> {
                FloydAnimations.toggle()
                ModuleManager.saveConfigurations()
            }
            AnimationHitKind.TOGGLE_SETTING -> {
                booleanSetting(FloydAnimations, hit.settingName)?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            AnimationHitKind.SLIDER -> {
                val spec = animationSliderSpecs().firstOrNull { it.settingName == hit.settingName } ?: return true
                val target = AnimationSliderTarget(spec, hit.bounds)
                activeAnimationSlider = target
                updateAnimationSlider(target, x)
            }
        }
        return true
    }

    private fun updateAnimationSlider(target: AnimationSliderTarget, x: Double) {
        val pct = ((x - target.bounds.left) / target.bounds.width).coerceIn(0.0, 1.0)
        val value = target.spec.min + pct * (target.spec.max - target.spec.min)
        numberSetting(FloydAnimations, target.spec.settingName)?.setNumericValue(value)
        ModuleManager.saveConfigurations()
    }

    private fun handleCameraEditorClick(x: Double, y: Double): Boolean {
        val hit = cameraHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        when (hit.kind) {
            CameraHitKind.RUNTIME_TOGGLE -> {
                when (hit.settingName) {
                    "Freecam" -> FloydCamera.toggleFreecam()
                    "Freelook" -> FloydCamera.toggleFreelook()
                }
                ModuleManager.saveConfigurations()
            }
            CameraHitKind.BOOLEAN_TOGGLE -> {
                booleanSetting(FloydCamera, hit.settingName)?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            CameraHitKind.SLIDER -> {
                val spec = cameraSliderSpecs().firstOrNull { it.settingName == hit.settingName } ?: return true
                val target = CameraSliderTarget(spec, hit.bounds)
                activeCameraSlider = target
                updateCameraSlider(target, x)
            }
        }
        return true
    }

    private fun updateCameraSlider(target: CameraSliderTarget, x: Double) {
        val pct = ((x - target.bounds.left) / target.bounds.width).coerceIn(0.0, 1.0)
        val value = target.spec.min + pct * (target.spec.max - target.spec.min)
        numberSetting(FloydCamera, target.spec.settingName)?.setNumericValue(value)
        ModuleManager.saveConfigurations()
    }

    private fun handleRenderEditorClick(x: Double, y: Double): Boolean {
        val hit = renderHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        renderTitleFocused = hit.kind == RenderHitKind.TITLE_FIELD
        when (hit.kind) {
            RenderHitKind.BOOLEAN_TOGGLE -> {
                val module = if (hit.settingName == "Server ID Hider" || hit.settingName == "Profile ID Hider") FloydHiders else FloydRender
                booleanSetting(module, hit.settingName)?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            RenderHitKind.XRAY_TOGGLE -> {
                booleanSetting(FloydXray, "Enabled")?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            RenderHitKind.MODULE_TOGGLE -> {
                FloydMobEsp.toggle()
                ModuleManager.saveConfigurations()
            }
            RenderHitKind.SLIDER -> {
                val spec = renderSliderSpecs().firstOrNull { it.settingName == hit.settingName } ?: return true
                val target = RenderSliderTarget(spec, hit.bounds)
                activeRenderSlider = target
                updateRenderSlider(target, x)
            }
            RenderHitKind.NAV_XRAY -> currentPage = Page.XRAY
            RenderHitKind.RELOAD_XRAY -> {
                ModuleManager.loadConfigurations()
                FloydXray.rebuildChunks()
                modMessage("Reloaded xray opaque blocks.")
            }
            RenderHitKind.NAV_MOB_ESP -> currentPage = Page.MOB_ESP
            RenderHitKind.NAV_HIDERS -> currentPage = Page.HIDERS
            RenderHitKind.NAV_ANIMATIONS -> currentPage = Page.ANIMATIONS
            RenderHitKind.STALK -> {
                val previous = FloydMobEsp.stopStalk()
                modMessage(if (previous == null) "Usage: /fa stalk <name>" else "Stopped stalking $previous")
            }
            RenderHitKind.BORDERLESS -> {
                FloydRender.setBorderlessWindowed(!(booleanSetting(FloydRender, "Borderless Window")?.enabled ?: false), force = true)
                ModuleManager.saveConfigurations()
            }
            RenderHitKind.TITLE_FIELD -> {
                renderTitleFocused = true
            }
        }
        return true
    }

    private fun updateRenderSlider(target: RenderSliderTarget, x: Double) {
        val pct = ((x - target.bounds.left) / target.bounds.width).coerceIn(0.0, 1.0)
        val value = target.spec.min + pct * (target.spec.max - target.spec.min)
        numberSetting(target.spec.module, target.spec.settingName)?.setNumericValue(value)
        ModuleManager.saveConfigurations()
    }

    private fun handleHidersEditorClick(x: Double, y: Double): Boolean {
        val hit = hidersHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        when (hit.kind) {
            HidersHitKind.TOGGLE -> {
                booleanSetting(FloydHiders, hit.settingName)?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            HidersHitKind.NO_ARMOR -> {
                selectorSetting(FloydHiders, "Target")?.let { setting ->
                    setting.value = setting.value + 1
                    ModuleManager.saveConfigurations()
                }
            }
        }
        return true
    }

    private fun handleNickHiderEditorClick(x: Double, y: Double): Boolean {
        if (nickInputBounds.contains(x, y)) {
            nickInputFocused = true
            return true
        }
        val hit = nickHiderHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        nickInputFocused = false
        when (hit.kind) {
            NickHiderHitKind.TOGGLE -> {
                booleanSetting(FloydNickHider, "Enabled")?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            NickHiderHitKind.EDIT_NAMES -> currentPage = Page.NAME_MAPPINGS
            NickHiderHitKind.PLAYER_SIZE -> currentPage = Page.PLAYER_SIZE
            NickHiderHitKind.RELOAD_NAMES -> {
                ModuleManager.loadConfigurations()
                modMessage("Reloaded name mappings.")
            }
        }
        return true
    }

    private fun handleGuiStyleClick(x: Double, y: Double): Boolean {
        val hit = guiStyleHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        openStyleColorEditor(hit.target)
        return true
    }

    private fun handleModuleBrowserChromeClick(x: Double, y: Double, button: Int): Boolean {
        if (button == 0 && moduleBrowserSearchBounds.contains(x, y)) {
            moduleBrowserSearchFocused = true
            return true
        }
        if (button == 0) moduleBrowserSearchFocused = false
        val header = moduleBrowserHeaderHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        val state = moduleBrowserPanelState(header.category)
        if (button == 1) {
            state.collapsed = !state.collapsed
            state.scroll = state.scroll.coerceIn(0, moduleBrowserMaxScroll(header.category))
            saveModuleBrowserPanelStates()
        } else {
            draggingModuleBrowserCategory = header.category
            moduleBrowserDragOffsetX = (x - state.x).roundToInt()
            moduleBrowserDragOffsetY = (y - state.y).roundToInt()
        }
        return true
    }

    private fun handleModuleBrowserClick(x: Double, y: Double, button: Int): Boolean {
        val hit = moduleBrowserHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return false
        val module = hit.module
        lastModuleBrowserClickDebug = mapOf(
            "x" to x,
            "y" to y,
            "button" to button,
            "displayName" to hit.entry.label,
            "kind" to hit.entry.kind.name,
            "isPlayerVirtual" to hit.entry.kind.isPlayerVirtual,
            "isRenderVirtual" to hit.entry.kind.isRenderVirtual,
            "isCameraVirtual" to hit.entry.kind.isCameraVirtual,
            "contentAvailable" to modulePopupContentAvailable(hit.entry)
        )
        if (button == 0) handleLegacyModuleBrowserPrimaryClick(hit.entry)
        if (hit.entry.kind.isCameraVirtual) {
            if (modulePopupContentAvailable(hit.entry)) openModulePopup(module, hit.bounds, hit.entry)
        } else if (hit.entry.kind == LegacyModuleBrowserKind.HIDER_NO_ARMOR) {
            if (modulePopupContentAvailable(hit.entry)) openModulePopup(module, hit.bounds, hit.entry)
        } else if (hit.entry.kind == LegacyModuleBrowserKind.HIDER_BOOLEAN) {
            return true
        } else if (hit.entry.kind == LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN) {
            return true
        } else if (hit.entry.kind.isRenderVirtual || hit.entry.kind.isPlayerVirtual) {
            if (modulePopupContentAvailable(hit.entry)) openModulePopup(module, hit.bounds, hit.entry)
        } else if (hit.entry.kind == LegacyModuleBrowserKind.MODULE && modulePopupContentAvailable(hit.entry)) {
            openModulePopup(module, hit.bounds, hit.entry)
        } else if (popupVisibleSettings(module).isNotEmpty()) {
            openModulePopup(module, hit.bounds)
        } else {
            when {
                module === FloydHud -> mc.setScreen(HudManager)
                hit.settingsPage != null -> openModuleBrowserSettings(hit.settingsPage)
            }
        }
        return true
    }

    private fun handleLegacyModuleBrowserPrimaryClick(entry: LegacyModuleBrowserEntry) {
        when (entry.kind) {
            LegacyModuleBrowserKind.MODULE -> {
                val module = entry.module
                when (module) {
                    FloydXray -> {
                        val next = !FloydXray.isActive()
                        if (!FloydXray.enabled) FloydXray.toggle()
                        FloydXray.xrayEnabled = next
                        FloydXray.rebuildChunks()
                        ModuleManager.saveConfigurations()
                    }
                    FloydMobEsp -> {
                        FloydMobEsp.toggle()
                        ModuleManager.saveConfigurations()
                    }
                    else -> if (module !== ClickGUIModule && !module.alwaysActive) {
                        module.toggle()
                        ModuleManager.saveConfigurations()
                    }
                }
            }
            LegacyModuleBrowserKind.CAMERA_FREECAM -> {
                if (!FloydCamera.enabled) FloydCamera.toggle()
                FloydCamera.toggleFreecam()
                ModuleManager.saveConfigurations()
            }
            LegacyModuleBrowserKind.CAMERA_FREELOOK -> {
                if (!FloydCamera.enabled) FloydCamera.toggle()
                FloydCamera.toggleFreelook()
                ModuleManager.saveConfigurations()
            }
            LegacyModuleBrowserKind.CAMERA_F5 -> Unit
            LegacyModuleBrowserKind.HIDER_BOOLEAN -> {
                val setting = entry.settingName?.let { booleanSetting(FloydHiders, it) } ?: return
                setting.enabled = !setting.enabled
                ModuleManager.saveConfigurations()
            }
            LegacyModuleBrowserKind.HIDER_NO_ARMOR -> {
                selectorSetting(FloydHiders, "Target")?.let { setting ->
                    setting.value = if (setting.value == 0) 1 else 0
                    ModuleManager.saveConfigurations()
                }
            }
            LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN -> {
                val setting = entry.settingName?.let { booleanSetting(FloydHiders, it) } ?: return
                setting.enabled = !setting.enabled
                ModuleManager.saveConfigurations()
            }
            LegacyModuleBrowserKind.RENDER_BOOLEAN -> {
                val setting = entry.settingName?.let { booleanSetting(FloydRender, it) } ?: return
                setting.enabled = !setting.enabled
                ModuleManager.saveConfigurations()
            }
            LegacyModuleBrowserKind.RENDER_STALK -> {
                if (FloydMobEsp.stalkEnabled()) {
                    FloydMobEsp.stopStalk()
                    ModuleManager.saveConfigurations()
                }
            }
            LegacyModuleBrowserKind.RENDER_HUD -> {
                val setting = entry.settingName?.let { hudSetting(FloydHud, it) } ?: return
                if (!FloydHud.enabled) FloydHud.toggle()
                setting.value.enabled = !setting.value.enabled
                ModuleManager.saveConfigurations()
            }
            LegacyModuleBrowserKind.RENDER_BORDERLESS -> {
                val next = !(booleanSetting(FloydRender, "Borderless Window")?.enabled ?: false)
                FloydRender.setBorderlessWindowed(next, force = true)
                ModuleManager.saveConfigurations()
            }
            LegacyModuleBrowserKind.RENDER_INSTANCE_NAME -> {
                stringSetting(FloydRender, "Instance Title")?.let { setting ->
                    if (setting.value.isNotEmpty()) {
                        setting.value = ""
                        ModuleManager.saveConfigurations()
                    }
                }
            }
            LegacyModuleBrowserKind.RENDER_GUI_STYLE -> Unit
            LegacyModuleBrowserKind.RENDER_ANIMATIONS -> {
                if (!FloydAnimations.alwaysActive) {
                    FloydAnimations.toggle()
                    ModuleManager.saveConfigurations()
                }
            }
            LegacyModuleBrowserKind.PLAYER_CAPE -> {
                if (!FloydCape.enabled) FloydCape.toggle()
                booleanSetting(FloydCape, "Enabled")?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            LegacyModuleBrowserKind.PLAYER_CONE_HAT -> {
                if (!FloydConeHat.enabled) FloydConeHat.toggle()
                booleanSetting(FloydConeHat, "Enabled")?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            LegacyModuleBrowserKind.PLAYER_NICK_HIDER -> {
                if (!FloydNickHider.enabled) FloydNickHider.toggle()
                booleanSetting(FloydNickHider, "Enabled")?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN -> {
                if (!FloydSkin.enabled) FloydSkin.toggle()
                booleanSetting(FloydSkin, "Custom Skin")?.let { setting ->
                    setting.enabled = !setting.enabled
                    ModuleManager.saveConfigurations()
                }
            }
            LegacyModuleBrowserKind.PLAYER_SIZE -> {
                if (!FloydPlayerSize.enabled) FloydPlayerSize.toggle()
                FloydPlayerSize.togglePlayerSize()
                ModuleManager.saveConfigurations()
            }
        }
    }

    private fun handleModulePopupClick(x: Double, y: Double, button: Int): Boolean {
        val popup = modulePopup ?: return false
        if (!popup.bounds.contains(x, y)) return false
        if (button != 0) return true
        if (y <= popup.bounds.top + modulePopupTitleHeight) {
            draggingModulePopup = true
            modulePopupDragOffsetX = (x - popup.bounds.left).roundToInt()
            modulePopupDragOffsetY = (y - popup.bounds.top).roundToInt()
            return true
        }
        modulePopupExtraHitEntries.firstOrNull { it.bounds.contains(x, y) }?.let { extra ->
            handleModulePopupExtra(extra.kind)
            return true
        }
        modulePopupXrayHitEntries.firstOrNull { it.bounds.contains(x, y) }?.let { hit ->
            handleModulePopupXrayBlock(hit)
            return true
        }
        modulePopupMobFilterHitEntries.firstOrNull { it.bounds.contains(x, y) }?.let { hit ->
            handleModulePopupMobFilter(hit, x, y)
            return true
        }
        modulePopupNameMappingHitEntries.firstOrNull { it.bounds.contains(x, y) }?.let { hit ->
            handleModulePopupNameMapping(hit, x, y)
            return true
        }
        modulePopupPlayerHitEntries.firstOrNull { it.bounds.contains(x, y) }?.let { hit ->
            handleModulePopupPlayerPick(hit)
            return true
        }
        val hit = modulePopupHitEntries.firstOrNull { it.bounds.contains(x, y) } ?: return true
        when (hit.kind) {
            ModulePopupHitKind.TOGGLE -> {
                when (val setting = hit.setting) {
                    is BooleanSetting -> setting.enabled = !setting.enabled
                    is RuntimeBooleanSetting -> setting.enabled = !setting.enabled
                }
                ModuleManager.saveConfigurations()
            }
            ModulePopupHitKind.NUMBER -> {
                val setting = hit.setting as? NumberSetting<*> ?: return true
                if (button == 0) {
                    val target = ModulePopupSliderTarget(setting, modulePopupSliderBounds(hit.bounds))
                    activeModulePopupSlider = target
                    updateModulePopupSlider(target, x)
                } else if (button == 1) {
                    setting.stepNumeric(-1)
                    ModuleManager.saveConfigurations()
                }
            }
            ModulePopupHitKind.SELECTOR -> {
                val setting = hit.setting as? SelectorSetting ?: return true
                setting.value += if (button == 1) -1 else 1
                ModuleManager.saveConfigurations()
            }
            ModulePopupHitKind.CYCLE_STRING -> {
                val setting = hit.setting as? StringSetting ?: return true
                cycleLegacyStringSetting(setting)
            }
            ModulePopupHitKind.STRING -> {
                val setting = hit.setting as? StringSetting ?: return true
                activeModulePopupString = ModulePopupStringTarget(setting)
                modulePopupStringBuffer = setting.value
                moduleBrowserSearchFocused = false
            }
            ModulePopupHitKind.COLOR -> {
                val setting = hit.setting as? ColorSetting ?: return true
                if (expandedModulePopupColor === setting) {
                    when {
                        modulePopupInlineColorSvBounds(hit.bounds).contains(x, y) -> {
                            if (modulePopupChromaSetting(setting)?.enabled == true) return true
                            activeModulePopupColorDrag = ColorPickerDrag.SV
                            updateModulePopupColorDrag(x, y)
                        }
                        modulePopupInlineColorHueBounds(hit.bounds).contains(x, y) -> {
                            if (modulePopupChromaSetting(setting)?.enabled == true) return true
                            activeModulePopupColorDrag = ColorPickerDrag.HUE
                            updateModulePopupColorDrag(x, y)
                        }
                        modulePopupInlineColorChromaBounds(hit.bounds).contains(x, y) -> {
                            toggleModulePopupChroma(setting)
                        }
                        modulePopupInlineColorFadeBounds(hit.bounds).contains(x, y) -> {
                            toggleModulePopupFade(setting)
                        }
                        modulePopupInlineColorEditFadeBounds(hit.bounds).contains(x, y) -> {
                            if (modulePopupFadeColorSetting(setting) != null) modulePopupEditingFadeColor = !modulePopupEditingFadeColor
                        }
                        modulePopupInlineColorBasePreviewBounds(hit.bounds).contains(x, y) -> {
                            modulePopupEditingFadeColor = false
                        }
                        modulePopupInlineColorFadePreviewBounds(hit.bounds).contains(x, y) -> {
                            if (modulePopupFadeColorSetting(setting) != null) modulePopupEditingFadeColor = true
                        }
                        y < hit.bounds.top + modulePopupRowHeight -> {
                            expandedModulePopupColor = null
                            activeModulePopupColorDrag = null
                            modulePopupEditingFadeColor = false
                        }
                    }
                } else {
                    expandedModulePopupColor = setting
                    activeModulePopupColorDrag = null
                    modulePopupEditingFadeColor = false
                }
            }
            ModulePopupHitKind.ACTION -> {
                (hit.setting as? ActionSetting)?.action?.invoke()
                ModuleManager.saveConfigurations()
            }
            ModulePopupHitKind.UNSUPPORTED -> Unit
        }
        return true
    }

    private fun modulePopupBoundsContains(x: Double, y: Double): Boolean =
        modulePopup?.bounds?.contains(x, y) == true

    private fun closeModulePopup() {
        modulePopup = null
        modulePopupHitEntries = emptyList()
        modulePopupExtraHitEntries = emptyList()
        modulePopupPlayerHitEntries = emptyList()
        modulePopupXrayHitEntries = emptyList()
        modulePopupMobFilterHitEntries = emptyList()
        modulePopupNameMappingHitEntries = emptyList()
        expandedModulePopupExtra = null
        draggingModulePopup = false
        activeModulePopupSlider = null
        expandedModulePopupColor = null
        activeModulePopupColorDrag = null
        modulePopupEditingFadeColor = false
        activeModulePopupString = null
        modulePopupStringBuffer = ""
        activeModulePopupActionInput = null
        modulePopupActionInputBuffer = ""
        modulePopupMappingOriginalBuffer = ""
        modulePopupMappingFakeBuffer = ""
        modulePopupMappingFocusedField = null
        modulePopupExtraScrolls.clear()
        modulePopupExpandedExtraBounds = Rect.ZERO
        modulePopupExpandedExtraContentRows = 0
        modulePopupExpandedExtraVisibleRows = 0
    }

    private fun finishModulePopupStringEdit() {
        val target = activeModulePopupString ?: return
        if (modulePopupStringBuffer.isNotEmpty()) {
            target.setting.value = modulePopupStringBuffer
            ModuleManager.saveConfigurations()
        }
        activeModulePopupString = null
        modulePopupStringBuffer = ""
    }

    private fun handleModulePopupExtra(kind: ModulePopupExtraKind) {
        when (kind) {
            ModulePopupExtraKind.XRAY_BLOCKS -> {
                expandedModulePopupExtra = if (expandedModulePopupExtra == kind) null else kind
                modulePopupExtraScrolls[kind] = 0
            }
            ModulePopupExtraKind.MOB_FILTERS -> {
                expandedModulePopupExtra = if (expandedModulePopupExtra == kind) null else kind
                modulePopupExtraScrolls[kind] = 0
            }
            ModulePopupExtraKind.NAME_MAPPINGS -> {
                expandedModulePopupExtra = if (expandedModulePopupExtra == kind) null else kind
                modulePopupExtraScrolls[kind] = 0
            }
            ModulePopupExtraKind.STALK_TARGET -> {
                expandedModulePopupExtra = if (expandedModulePopupExtra == kind) null else kind
                modulePopupExtraScrolls[kind] = 0
            }
            ModulePopupExtraKind.RELOAD_NAMES -> {
                ModuleManager.loadConfigurations()
                modMessage("Reloaded name mappings.")
            }
            ModulePopupExtraKind.HUD_LAYOUT -> mc.setScreen(HudManager)
            ModulePopupExtraKind.XRAY_ADD_BLOCK,
            ModulePopupExtraKind.MOB_ADD_NAME,
            ModulePopupExtraKind.MOB_ADD_TYPE,
            ModulePopupExtraKind.NAME_ADD_MAPPING -> Unit
        }
    }

    private fun handleModulePopupNameMapping(hit: NameMappingHitEntry, x: Double, y: Double) {
        nameMappingHitEntries = modulePopupNameMappingHitEntries
        if (handleNameMappingEditorClick(x, y)) {
            modulePopupNameMappingHitEntries = nameMappingHitEntries
        }
    }

    private fun handleModulePopupMobFilter(hit: MobFilterHitEntry, x: Double, y: Double) {
        mobFilterHitEntries = modulePopupMobFilterHitEntries
        if (handleMobFilterEditorClick(x, y)) {
            modulePopupMobFilterHitEntries = mobFilterHitEntries
        }
    }

    private fun focusOrSubmitModulePopupActionInput(kind: ModulePopupExtraKind) {
        if (activeModulePopupActionInput == kind && modulePopupActionInputBuffer.isNotBlank()) {
            submitModulePopupActionInput(kind)
        } else {
            focusModulePopupActionInput(kind, clear = true)
        }
    }

    private fun focusModulePopupActionInput(kind: ModulePopupExtraKind, clear: Boolean = false) {
        if (activeModulePopupActionInput != kind || clear) {
            modulePopupActionInputBuffer = ""
            resetModulePopupActionScroll(kind)
        }
        activeModulePopupActionInput = kind
        moduleBrowserSearchFocused = false
        activeModulePopupString = null
        modulePopupStringBuffer = ""
    }

    private fun resetModulePopupActionScroll(kind: ModulePopupExtraKind) {
        when (kind) {
            ModulePopupExtraKind.XRAY_ADD_BLOCK -> modulePopupExtraScrolls[ModulePopupExtraKind.XRAY_BLOCKS] = 0
            ModulePopupExtraKind.MOB_ADD_NAME,
            ModulePopupExtraKind.MOB_ADD_TYPE -> modulePopupExtraScrolls[ModulePopupExtraKind.MOB_FILTERS] = 0
            ModulePopupExtraKind.STALK_TARGET -> modulePopupExtraScrolls[ModulePopupExtraKind.STALK_TARGET] = 0
            else -> Unit
        }
    }

    private fun resetModulePopupNameMappingScroll() {
        modulePopupExtraScrolls[ModulePopupExtraKind.NAME_MAPPINGS] = 0
    }

    private fun submitModulePopupActionInput(kind: ModulePopupExtraKind? = activeModulePopupActionInput) {
        val resolvedKind = kind ?: return
        activeModulePopupActionInput = resolvedKind
        val value = modulePopupActionInputBuffer.trim()
        if (value.isBlank()) return
        when (resolvedKind) {
            ModulePopupExtraKind.XRAY_ADD_BLOCK -> {
                runCatching { FloydXray.validOpaqueBlockId(value) }.getOrNull()?.let { FloydXray.addOpaqueBlock(it) }
                modulePopupExtraScrolls[ModulePopupExtraKind.XRAY_BLOCKS] = 0
            }
            ModulePopupExtraKind.MOB_ADD_NAME -> {
                FloydMobEsp.addNameFilter(FloydMobEsp.validUserNameFilter(value))
                modulePopupExtraScrolls[ModulePopupExtraKind.MOB_FILTERS] = 0
            }
            ModulePopupExtraKind.MOB_ADD_TYPE -> {
                FloydMobEsp.addTypeFilter(FloydMobEsp.validTypeFilterId(value))
                modulePopupExtraScrolls[ModulePopupExtraKind.MOB_FILTERS] = 0
            }
            ModulePopupExtraKind.STALK_TARGET -> {
                FloydMobEsp.stalk(value)
                modulePopupExtraScrolls[ModulePopupExtraKind.STALK_TARGET] = 0
            }
            else -> return
        }
        ModuleManager.saveConfigurations()
        modulePopupActionInputBuffer = ""
        activeModulePopupActionInput = resolvedKind
    }

    private fun handleModulePopupXrayBlock(hit: ModulePopupXrayHitEntry) {
        if (hit.action == ModulePopupExtraKind.XRAY_ADD_BLOCK) {
            if (hit.submit) submitModulePopupActionInput(ModulePopupExtraKind.XRAY_ADD_BLOCK)
            else focusModulePopupActionInput(ModulePopupExtraKind.XRAY_ADD_BLOCK)
            return
        }
        if (hit.add) FloydXray.addOpaqueBlock(hit.blockId) else FloydXray.removeOpaqueBlock(hit.blockId)
        ModuleManager.saveConfigurations()
    }

    private fun handleModulePopupPlayerPick(hit: ModulePopupPlayerHitEntry) {
        val playerName = hit.playerName
        if (playerName == null) {
            if (hit.submit) submitModulePopupActionInput(ModulePopupExtraKind.STALK_TARGET)
            else focusModulePopupActionInput(ModulePopupExtraKind.STALK_TARGET)
        } else {
            FloydMobEsp.stalk(playerName)
            ModuleManager.saveConfigurations()
        }
    }

    private fun modulePopupContentAvailable(entry: LegacyModuleBrowserEntry): Boolean =
        popupVisibleSettings(entry).isNotEmpty() || modulePopupExtras(entry).isNotEmpty()

    private fun modulePopupHasPlayerPreview(popup: ModulePopup): Boolean =
        popup.entry?.kind in setOf(
            LegacyModuleBrowserKind.PLAYER_CAPE,
            LegacyModuleBrowserKind.PLAYER_CONE_HAT,
            LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN,
            LegacyModuleBrowserKind.PLAYER_SIZE
        )

    private fun modulePopupPlayerPreviewBounds(popup: ModulePopup): Rect {
        if (!modulePopupHasPlayerPreview(popup)) return Rect.ZERO
        val popupBounds = popup.bounds
        val previewWidth = 60
        val previewHeight = 120
        var x = popupBounds.right + 4
        if (x + previewWidth > width) x = popupBounds.left - previewWidth - 4
        var y = popupBounds.top
        if (y + previewHeight > height) y = height - previewHeight - 4
        if (y < 4) y = 4
        return Rect(x.coerceAtLeast(0), y, previewWidth, previewHeight)
    }

    private fun isSameModulePopupTarget(popup: ModulePopup, module: Module, entry: LegacyModuleBrowserEntry?): Boolean =
        if (popup.entry != null || entry != null) {
            popup.entry?.kind == entry?.kind && popup.entry?.label == entry?.label
        } else {
            popup.module === module
        }

    private fun openModulePopup(module: Module, rowBounds: Rect, entry: LegacyModuleBrowserEntry? = null) {
        modulePopup?.let { current ->
            if (isSameModulePopupTarget(current, module, entry)) {
                closeModulePopup()
                return
            }
        }
        val popup = ModulePopup(module, Rect.ZERO, entry)
        val popupWidth = modulePopupWidth(popup)
        val popupHeight = modulePopupHeight(popup)
        var x = rowBounds.right + 4
        if (x + popupWidth > width) x = rowBounds.left - popupWidth - 4
        var y = rowBounds.top
        if (y + popupHeight > height) y = height - popupHeight - 4
        if (y < 4) y = 4
        modulePopup = popup.copy(bounds = Rect(x.coerceAtLeast(0), y, popupWidth, popupHeight))
        modulePopupHitEntries = emptyList()
        modulePopupPlayerHitEntries = emptyList()
        modulePopupXrayHitEntries = emptyList()
        modulePopupMobFilterHitEntries = emptyList()
        modulePopupNameMappingHitEntries = emptyList()
        expandedModulePopupColor = null
        activeModulePopupColorDrag = null
        modulePopupEditingFadeColor = false
        expandedModulePopupExtra = null
        activeModulePopupString = null
        modulePopupStringBuffer = ""
        activeModulePopupActionInput = null
        modulePopupActionInputBuffer = ""
        modulePopupMappingOriginalBuffer = ""
        modulePopupMappingFakeBuffer = ""
        modulePopupMappingFocusedField = null
        modulePopupExtraScrolls.clear()
    }

    private fun openModuleBrowserSettings(page: Page) {
        modulePopup = null
        modulePopupPlayerHitEntries = emptyList()
        modulePopupXrayHitEntries = emptyList()
        modulePopupMobFilterHitEntries = emptyList()
        modulePopupNameMappingHitEntries = emptyList()
        expandedModulePopupExtra = null
        pageReturnOverrides[page] = Page.CLICK_GUI
        currentPage = page
    }

    private fun openStalkTargetEditor() {
        val suggestions = onlinePlayerSuggestions()
        textEditor = TextEditor(
            title = "Stalk Target",
            value = FloydMobEsp.stalkTarget(),
            placeholder = suggestions.firstOrNull() ?: "Player name",
            hint = if (suggestions.isEmpty()) "Empty disables stalk" else "Online: ${suggestions.take(3).joinToString(", ")}",
            maxLength = 32
        ) { value ->
            if (value.isBlank()) FloydMobEsp.stopStalk() else FloydMobEsp.stalk(value.trim())
            ModuleManager.saveConfigurations()
        }
        closeModulePopup()
    }

    private fun xrayEditorRows(): List<LegacyRow> {
        val active = FloydXray.opaqueBlockIds().sorted()
        val nearby = nearbyBlockSuggestions().filterNot { active.contains(it) }
        val rows = mutableListOf<LegacyRow>()
        if (active.isEmpty()) {
            rows += actionRow("Active Blocks: none") { modMessage("No xray opaque blocks configured.") }
        } else {
            rows += active.map { id ->
                actionRow("Remove $id") {
                    FloydXray.removeOpaqueBlock(id)
                    ModuleManager.saveConfigurations()
                }
            }
        }
        if (nearby.isEmpty()) {
            rows += actionRow("Nearby Blocks: none") { modMessage("No nearby block suggestions found.") }
        } else {
            rows += nearby.map { id ->
                actionRow("Add $id") {
                    FloydXray.addOpaqueBlock(id)
                    ModuleManager.saveConfigurations()
                }
            }
        }
        return rows
    }

    private fun stalkRow(): LegacyRow =
        actionRow({
            if (FloydMobEsp.stalkEnabled()) "Stalk: ${FloydMobEsp.stalkTarget()} (disable)"
            else "Stalk: OFF (use /fa stalk <name>)"
        }) {
            val previous = FloydMobEsp.stopStalk()
            modMessage(if (previous == null) "Usage: /fa stalk <name>" else "Stopped stalking $previous")
        }

    private fun nameMappingRows(): List<LegacyRow> {
        val rows = mutableListOf<LegacyRow>()
        val mappings = FloydNickHider.nameMappings.entries.sortedBy { it.key.lowercase() }
        if (mappings.isEmpty()) {
            rows += actionRow("Active Mappings: none") { modMessage("No name mappings configured.") }
        } else {
            rows += mappings.map { (real, fake) ->
                actionRow("Remove $real -> $fake") {
                    FloydNickHider.removeNameMapping(real)
                    ModuleManager.saveConfigurations()
                }
            }
        }
        val suggestions = onlinePlayerSuggestions()
            .filterNot { name -> FloydNickHider.nameMappings.keys.any { it.equals(name, ignoreCase = true) } }
        if (suggestions.isEmpty()) {
            rows += actionRow("Online Players: none") { modMessage("No online player suggestions found.") }
        } else {
            rows += suggestions.map { real ->
                actionRow("Map $real -> ${FloydNickHider.nickname}") {
                    FloydNickHider.addNameMapping(real, FloydNickHider.nickname)
                    ModuleManager.saveConfigurations()
                }
            }
        }
        return rows
    }

    private fun toggleModuleRow(module: Module, label: String, layout: RowLayout = RowLayout.FULL): LegacyRow =
        actionRow({ "$label: ${onOff(module.enabled)}" }, layout) {
            module.toggle()
            ModuleManager.saveConfigurations()
        }

    private fun toggleSettingRow(module: Module, settingName: String, label: String, layout: RowLayout = RowLayout.FULL): LegacyRow =
        actionRow({ "$label: ${onOff(booleanSetting(module, settingName)?.enabled ?: false)}" }, layout) {
            val setting = booleanSetting(module, settingName) ?: return@actionRow
            setting.enabled = !setting.enabled
            ModuleManager.saveConfigurations()
        }

    private fun runtimeToggleRow(module: Module, settingName: String, label: String, layout: RowLayout = RowLayout.FULL, toggle: () -> Unit): LegacyRow =
        actionRow({ "$label: ${onOff(runtimeBooleanSetting(module, settingName)?.enabled ?: false)}" }, layout) {
            if (!module.enabled) module.toggle()
            toggle()
            ModuleManager.saveConfigurations()
        }

    private fun selectorRow(module: Module, settingName: String, label: String, labels: List<String>, layout: RowLayout = RowLayout.FULL): LegacyRow =
        actionRow({
            val setting = selectorSetting(module, settingName)
            "$label: ${labels.getOrNull(setting?.value ?: 0) ?: "UNKNOWN"}"
        }, layout) {
            val setting = selectorSetting(module, settingName) ?: return@actionRow
            setting.value = setting.value + 1
            ModuleManager.saveConfigurations()
        }

    private fun numberRow(module: Module, settingName: String, label: String, layout: RowLayout = RowLayout.FULL, format: (Double) -> String): LegacyRow =
        LegacyRow(label = {
            val setting = numberSetting(module, settingName)
            "$label: ${setting?.numericValue()?.let(format) ?: "?"}"
        }, layout = layout) { button ->
            val setting = numberSetting(module, settingName) ?: return@LegacyRow
            setting.stepNumeric(if (button == 1) -1 else 1)
            ModuleManager.saveConfigurations()
        }

    private fun colorRow(module: Module, settingName: String, label: String, layout: RowLayout = RowLayout.FULL): LegacyRow =
        actionRow({
            val setting = colorSetting(module, settingName)
            "$label: ${setting?.value?.hex(includeAlpha = false)?.let { "#$it" } ?: "?"}"
        }, layout) {
            openColorEditor(module, settingName, label)
        }

    private fun actionSettingRow(module: Module, settingName: String, label: String, layout: RowLayout = RowLayout.FULL): LegacyRow =
        actionRow({ label }, layout) {
            val setting = module.settings[settingName] as? ActionSetting ?: return@actionRow
            setting.action()
            ModuleManager.saveConfigurations()
        }

    private fun navRow(label: String, page: Page, layout: RowLayout = RowLayout.FULL): LegacyRow = actionRow(label, layout) { currentPage = page }

    private fun actionRow(label: String, layout: RowLayout = RowLayout.FULL, action: () -> Unit): LegacyRow = actionRow({ label }, layout, action)
    private fun actionRow(label: () -> String, layout: RowLayout = RowLayout.FULL, action: () -> Unit): LegacyRow = LegacyRow(label = label, layout = layout) { action() }
    private fun headerRow(label: String): LegacyRow = LegacyRow(label = { label }, kind = RowKind.HEADER) {}

    private fun booleanSetting(module: Module, name: String): BooleanSetting? = module.settings[name] as? BooleanSetting
    private fun runtimeBooleanSetting(module: Module, name: String): RuntimeBooleanSetting? = module.settings[name] as? RuntimeBooleanSetting
    private fun selectorSetting(module: Module, name: String): SelectorSetting? = module.settings[name] as? SelectorSetting
    private fun numberSetting(module: Module, name: String): NumberSetting<*>? = module.settings[name] as? NumberSetting<*>
    private fun stringSetting(module: Module, name: String): StringSetting? = module.settings[name] as? StringSetting
    private fun colorSetting(module: Module, name: String): ColorSetting? = module.settings[name] as? ColorSetting
    private fun hudSetting(module: Module, name: String): HUDSetting? = module.settings[name] as? HUDSetting
    private fun moduleAction(module: Module, name: String): ActionSetting? = module.settings[name] as? ActionSetting
    private fun onOff(enabled: Boolean): String = if (enabled) "ON" else "OFF"
    private fun oneDecimal(value: Double): String = "%.1f".format(value)
    private fun twoDecimal(value: Double): String = "%.2f".format(value)

    private fun nearbyBlockSuggestions(): List<String> {
        val player = mc.player ?: return emptyList()
        val level = mc.level ?: return emptyList()
        val blocks = linkedSetOf<String>()
        val center = player.blockPosition()
        for (x in -8..8) {
            for (y in -8..8) {
                for (z in -8..8) {
                    val state = level.getBlockState(BlockPos(center.x + x, center.y + y, center.z + z))
                    if (!state.isAir) blocks.add(BuiltInRegistries.BLOCK.getKey(state.block).toString())
                }
            }
        }
        return blocks.toList()
    }

    private fun onlinePlayerSuggestions(): List<String> {
        val names = linkedSetOf<String>()
        mc.connection?.onlinePlayers?.forEach { entry ->
            val name = entry.profile.name
            if (name.matches(Regex("[a-zA-Z0-9_]{3,16}")) && !name.equals(mc.user.name, ignoreCase = true)) names.add(name)
        }
        if (names.isEmpty()) {
            mc.level?.players()?.mapTo(names) { it.name.string }
            names.removeIf { it.equals(mc.user.name, ignoreCase = true) }
        }
        return names.toList()
    }

    private fun Int.floorMod(size: Int): Int = ((this % size) + size) % size

    private fun pageTitle(page: Page): String = when (page) {
        Page.HUB -> "FloydAddons"
        Page.RENDER -> "Render"
        Page.HIDERS -> "Hiders"
        Page.CAMERA -> "Camera"
        Page.COSMETIC -> "Cosmetic"
        Page.CAPE -> "Cape Config"
        Page.CONE_HAT -> "Cone Hat Config"
        Page.NICK_HIDER -> "Neck Hider"
        Page.SKIN -> "Skin"
        Page.MOB_ESP -> "Mob ESP Config"
        Page.MOB_ESP_FILTERS -> "Mob ESP Filters"
        Page.ANIMATIONS -> "Attack Animation"
        Page.XRAY -> "X-Ray Blocks"
        Page.PLAYER_SIZE -> "Player Size"
        Page.NAME_MAPPINGS -> "Name Mappings"
        Page.GUI_STYLE -> "GUI Style"
        Page.CLICK_GUI -> "ClickGUI"
    }

    private fun pageParent(page: Page): Page = pageReturnOverrides[page] ?: when (page) {
        Page.HIDERS, Page.MOB_ESP, Page.ANIMATIONS, Page.XRAY -> Page.RENDER
        Page.MOB_ESP_FILTERS -> Page.MOB_ESP
        Page.SKIN, Page.CAPE, Page.CONE_HAT -> Page.COSMETIC
        Page.PLAYER_SIZE, Page.NAME_MAPPINGS -> Page.NICK_HIDER
        Page.GUI_STYLE -> Page.HUB
        Page.CLICK_GUI -> Page.HUB
        else -> Page.HUB
    }

    private fun drawStretchBackground(context: GuiGraphics, x: Int, y: Int, w: Int, h: Int, alpha: Float) {
        val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        context.fill(x, y, x + w, y + h, a shl 24)
        context.blit(RenderPipelines.GUI_TEXTURED, background, x, y, 0f, 0f, w, h, hubBackgroundTextureSize, hubBackgroundTextureSize)
    }

    private fun drawTitle(context: GuiGraphics, centerX: Int, y: Int, alpha: Float) {
        val title = "FloydAddons"
        val titleScale = 1.75f
        context.pose().pushMatrix()
        context.pose().scale(titleScale, titleScale)
        val scaledCenterX = (centerX / titleScale).roundToInt()
        val scaledY = (y / titleScale).roundToInt()
        val titleWidth = mc.font.width(title)
        var x = scaledCenterX - titleWidth / 2
        for (char in title) {
            val text = char.toString()
            val offset = 1f - ((x - (scaledCenterX - titleWidth / 2f)) / titleWidth)
            context.drawString(mc.font, text, x, scaledY, applyAlpha(chromaColor(offset), alpha), true)
            x += mc.font.width(text)
        }
        context.pose().popMatrix()
    }

    private fun drawCentralLabels(context: GuiGraphics, left: Int, top: Int, alpha: Float) {
        val labelScale = 2.52f
        val fontHeight = mc.font.lineHeight
        val spacing = (fontHeight * labelScale + scaleY(20)).roundToInt()
        val groupHeight = labels.size * spacing
        val baseY = top + (panelHeight() - groupHeight) / 2
        val bounds = mutableListOf<Rect>()

        for ((index, label) in labels.withIndex()) {
            val labelWidth = mc.font.width(label)
            val drawX = left + ((panelWidth() - labelWidth * labelScale) / 2f).roundToInt()
            val drawY = baseY + index * spacing
            val rect = Rect(drawX, drawY, (labelWidth * labelScale).roundToInt(), (fontHeight * labelScale).roundToInt())
            bounds += rect

            val hovered = rect.contains(hoverX, hoverY)
            val target = if (hovered) 1f else 0f
            labelHover[index] += (target - labelHover[index]) * 0.2f
            val baseColor = applyAlpha(0xFFFFFFFF.toInt(), alpha)
            val hoverColor = applyAlpha(chromaColor(0f), alpha)
            val blended = blendColors(baseColor, hoverColor, labelHover[index])

            context.pose().pushMatrix()
            context.pose().translate(drawX.toFloat(), drawY.toFloat())
            context.pose().scale(labelScale, labelScale)
            context.drawString(mc.font, label, 0, 0, blended, true)
            if (labelHover[index] > 0.01f) {
                val underlineY = fontHeight + 1
                val fadeLength = max(1, (labelWidth * 0.35f).roundToInt())
                for (x in 0 until labelWidth) {
                    val edge = when {
                        x < fadeLength -> x / fadeLength.toFloat()
                        x > labelWidth - fadeLength -> (labelWidth - x) / fadeLength.toFloat()
                        else -> 1f
                    }.coerceIn(0f, 1f)
                    context.fill(x, underlineY, x + 1, underlineY + 1, applyAlpha(blended, labelHover[index] * edge))
                }
            }
            context.pose().popMatrix()
        }
        labelBounds = bounds
    }

    private fun drawGithub(context: GuiGraphics, left: Int, bottom: Int, alpha: Float) {
        val centerX = left + panelWidth() / 2
        val headerY = bottom - scaleY(34) - 15
        val linkY = headerY + mc.font.lineHeight + 5
        val headerX = centerX - mc.font.width(githubHeader) / 2
        val linkX = centerX - mc.font.width(githubUrl) / 2
        context.drawString(mc.font, githubHeader, headerX, headerY, applyAlpha(0xFFFFFFFF.toInt(), alpha), true)
        context.drawString(mc.font, githubUrl, linkX, linkY, applyAlpha(chromaColor(0f), alpha), true)
        linkBounds = Rect(linkX, linkY, mc.font.width(githubUrl), mc.font.lineHeight)
    }

    private fun drawButton(context: GuiGraphics, rect: Rect, label: String, alpha: Float) {
        val hover = rect.contains(hoverX, hoverY)
        context.fill(rect.left, rect.top, rect.right, rect.bottom, applyAlpha(if (hover) 0xFF666666.toInt() else 0xFF4A4A4A.toInt(), alpha))
        drawButtonBorder(context, rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, alpha)
        context.drawString(
            mc.font,
            label,
            rect.left + (rect.width - mc.font.width(label)) / 2,
            rect.top + (rect.height - mc.font.lineHeight) / 2,
            legacyTextColor(0f, alpha),
            true
        )
    }

    private fun drawChromaBorder(context: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int, alpha: Float) {
        drawStyledBorder(context, left, top, right, bottom, alpha, StyleTarget.GUI_BORDER)
    }

    private fun drawButtonBorder(context: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int, alpha: Float) {
        drawStyledBorder(context, left, top, right, bottom, alpha, StyleTarget.BUTTON_BORDER)
    }

    private fun drawStyledBorder(context: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int, alpha: Float, target: StyleTarget) {
        val width = right - left
        val height = bottom - top
        val perimeter = width * 2 + height * 2
        if (perimeter <= 0) return
        var pos = 0
        var step = max(1, width / chromaSegmentsPerEdge)
        var x = 0
        while (x < width) {
            val segmentWidth = min(step, width - x)
            context.fill(left + x, top, left + x + segmentWidth, top + borderThickness, applyAlpha(legacyStyleColor(target, pos / perimeter.toFloat()), alpha))
            x += step
            pos += step
        }
        step = max(1, height / chromaSegmentsPerEdge)
        var y = 0
        while (y < height) {
            val segmentHeight = min(step, height - y)
            context.fill(right - borderThickness, top + y, right, top + y + segmentHeight, applyAlpha(legacyStyleColor(target, pos / perimeter.toFloat()), alpha))
            y += step
            pos += step
        }
        step = max(1, width / chromaSegmentsPerEdge)
        x = width - 1
        while (x >= 0) {
            val segmentWidth = min(step, x + 1)
            context.fill(left + x - segmentWidth + 1, bottom - borderThickness, left + x + 1, bottom, applyAlpha(legacyStyleColor(target, pos / perimeter.toFloat()), alpha))
            x -= step
            pos += step
        }
        step = max(1, height / chromaSegmentsPerEdge)
        y = height - 1
        while (y >= 0) {
            val segmentHeight = min(step, y + 1)
            context.fill(left, top + y - segmentHeight + 1, left + borderThickness, top + y + 1, applyAlpha(legacyStyleColor(target, pos / perimeter.toFloat()), alpha))
            y -= step
            pos += step
        }
    }

    private fun legacyTextColor(offset: Float, alpha: Float): Int = applyAlpha(legacyStyleColor(StyleTarget.TEXT, offset), alpha)

    private fun legacyStyleColor(target: StyleTarget, offset: Float): Int {
        val chroma = booleanSetting(ClickGUIModule, target.chromaSetting)?.enabled ?: true
        if (chroma) return chromaColor(offset)
        val base = colorSetting(ClickGUIModule, target.colorSetting)?.value?.rgba ?: 0xFFFFFFFF.toInt()
        val fade = booleanSetting(ClickGUIModule, target.fadeSetting)?.enabled ?: false
        if (!fade) return base
        val fadeColor = colorSetting(ClickGUIModule, target.fadeColorSetting)?.value?.rgba ?: base
        val t = ((kotlin.math.sin((((System.currentTimeMillis() % 2500L) / 2500f) + offset) * Math.PI * 2.0) + 1.0) / 2.0).toFloat()
        return blendColors(base, fadeColor, t)
    }

    private fun legacyStyleState(target: StyleTarget): Map<String, Any?> = mapOf(
        "color" to "#${colorSetting(ClickGUIModule, target.colorSetting)?.value?.hex(includeAlpha = false)}",
        "chroma" to (booleanSetting(ClickGUIModule, target.chromaSetting)?.enabled ?: false),
        "fadeColor" to "#${colorSetting(ClickGUIModule, target.fadeColorSetting)?.value?.hex(includeAlpha = false)}",
        "fade" to (booleanSetting(ClickGUIModule, target.fadeSetting)?.enabled ?: false)
    )

    private fun chromaColor(offset: Float): Int {
        val hue = (((System.currentTimeMillis() % 2500L) / 2500f) + offset).mod(1f)
        return 0xFF000000.toInt() or (HSBtoRGB(hue, 1f, 1f) and 0x00FFFFFF)
    }

    private fun scaleY(value: Int): Int = (value * (270f / 500f)).roundToInt()
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (((color ushr 24) and 0xFF) * alpha.coerceIn(0f, 1f)).roundToInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    private fun blendColors(from: Int, to: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val a = (((from ushr 24) and 0xFF) + (((to ushr 24) and 0xFF) - ((from ushr 24) and 0xFF)) * clamped).roundToInt()
        val r = (((from ushr 16) and 0xFF) + (((to ushr 16) and 0xFF) - ((from ushr 16) and 0xFF)) * clamped).roundToInt()
        val g = (((from ushr 8) and 0xFF) + (((to ushr 8) and 0xFF) - ((from ushr 8) and 0xFF)) * clamped).roundToInt()
        val b = ((from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * clamped).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun rectState(rect: Rect): Map<String, Int> = mapOf(
        "left" to rect.left,
        "top" to rect.top,
        "right" to rect.right,
        "bottom" to rect.bottom,
        "width" to rect.width,
        "height" to rect.height
    )

    private enum class Page {
        HUB,
        RENDER,
        HIDERS,
        CAMERA,
        COSMETIC,
        CAPE,
        CONE_HAT,
        NICK_HIDER,
        SKIN,
        MOB_ESP,
        MOB_ESP_FILTERS,
        ANIMATIONS,
        XRAY,
        PLAYER_SIZE,
        NAME_MAPPINGS,
        GUI_STYLE,
        CLICK_GUI
    }

    private enum class RowKind {
        BUTTON,
        HEADER
    }

    private enum class RowRole {
        NORMAL,
        SKIN_DROPDOWN
    }

    private enum class RowLayout {
        FULL,
        LEFT,
        RIGHT
    }

    private data class LegacyRow(
        val label: () -> String,
        val kind: RowKind = RowKind.BUTTON,
        val layout: RowLayout = RowLayout.FULL,
        val role: RowRole = RowRole.NORMAL,
        val action: (button: Int) -> Unit
    )
    private data class LaidOutRow(val row: LegacyRow, val line: Int, val bounds: Rect)
    private data class HitRow(val bounds: Rect, val label: String, val kind: RowKind, val action: (button: Int) -> Unit)
    private data class SkinSettingsHitEntry(val bounds: Rect, val settingName: String, val kind: SkinSettingsHitKind)
    private data class ConeSpec(
        val settingName: String,
        val label: String,
        val min: Double,
        val max: Double,
        val format: (Double) -> String,
        val inputFormat: (Double) -> String
    )
    private data class ConeControlRow(val spec: ConeSpec, val slider: Rect, val input: Rect)
    private data class MobFilterHitEntry(val bounds: Rect, val key: String, val kind: MobFilterHitKind, val submit: Boolean = false)
    private data class MobFilterColorTarget(val key: String, val isName: Boolean)
    private data class MobFilterInlineColor(val argb: Int, val chroma: Boolean)
    private data class MobEspHitEntry(val bounds: Rect, val settingName: String, val kind: MobEspHitKind)
    private data class CosmeticSliderSpec(val settingName: String, val label: String, val min: Double, val max: Double)
    private data class CosmeticSliderTarget(val spec: CosmeticSliderSpec, val bounds: Rect)
    private data class CosmeticHitEntry(val bounds: Rect, val settingName: String, val kind: CosmeticHitKind)
    private data class XrayHitEntry(val bounds: Rect, val blockId: String, val add: Boolean)
    private data class NameMappingHitEntry(val bounds: Rect, val realName: String, val kind: NameMappingHitKind)
    private data class AnimationSliderSpec(val settingName: String, val label: String, val min: Double, val max: Double, val format: (Double) -> String)
    private data class AnimationSliderTarget(val spec: AnimationSliderSpec, val bounds: Rect)
    private data class AnimationHitEntry(val bounds: Rect, val settingName: String, val kind: AnimationHitKind)
    private data class CameraSliderSpec(val settingName: String, val label: String, val min: Double, val max: Double, val format: (Double) -> String)
    private data class CameraSliderTarget(val spec: CameraSliderSpec, val bounds: Rect)
    private data class CameraHitEntry(val bounds: Rect, val settingName: String, val kind: CameraHitKind)
    private data class RenderSliderSpec(val module: Module, val settingName: String, val label: String, val min: Double, val max: Double, val format: (Double) -> String)
    private data class RenderSliderTarget(val spec: RenderSliderSpec, val bounds: Rect)
    private data class RenderHitEntry(val bounds: Rect, val settingName: String, val kind: RenderHitKind)
    private data class HidersButtonSpec(val settingName: String, val label: String)
    private data class HidersHitEntry(val bounds: Rect, val settingName: String, val kind: HidersHitKind)
    private data class NickHiderHitEntry(val bounds: Rect, val kind: NickHiderHitKind)
    private data class GuiStyleHitEntry(val bounds: Rect, val target: StyleTarget)
    private data class ModuleBrowserHitEntry(val bounds: Rect, val category: Category, val entry: LegacyModuleBrowserEntry, val settingsPage: Page?) {
        val module: Module get() = entry.module
    }
    private data class LegacyModuleBrowserEntry(
        val module: Module,
        val label: String,
        val kind: LegacyModuleBrowserKind,
        val settingName: String? = null
    ) {
        fun enabled(): Boolean = when (kind) {
            LegacyModuleBrowserKind.MODULE ->
                when (module) {
                    FloydXray -> FloydXray.isActive()
                    FloydMobEsp -> FloydMobEsp.enabled
                    else -> module.enabled || module.alwaysActive
                }
            LegacyModuleBrowserKind.CAMERA_FREECAM -> FloydCamera.freecamActive()
            LegacyModuleBrowserKind.CAMERA_FREELOOK -> FloydCamera.freelookActive()
            LegacyModuleBrowserKind.CAMERA_F5 ->
                FloydCamera.f5DisableFront ||
                    FloydCamera.f5DisableBack ||
                    FloydCamera.f5NoClip ||
                    FloydCamera.f5ScrollEnabled ||
                    FloydCamera.f5ResetOnToggle ||
                    FloydCamera.f5Distance != 4.0f
            LegacyModuleBrowserKind.HIDER_BOOLEAN ->
                settingName?.let { FloydHiders.settings[it] as? BooleanSetting }?.enabled == true
            LegacyModuleBrowserKind.HIDER_NO_ARMOR ->
                (FloydHiders.settings["Target"] as? SelectorSetting)?.value != 0
            LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN ->
                settingName?.let { FloydHiders.settings[it] as? BooleanSetting }?.enabled == true
            LegacyModuleBrowserKind.RENDER_BOOLEAN ->
                settingName?.let { FloydRender.settings[it] as? BooleanSetting }?.enabled == true
            LegacyModuleBrowserKind.RENDER_STALK -> FloydMobEsp.stalkEnabled()
            LegacyModuleBrowserKind.RENDER_HUD ->
                settingName?.let { FloydHud.settings[it] as? HUDSetting }?.isEnabled == true
            LegacyModuleBrowserKind.RENDER_BORDERLESS ->
                (FloydRender.settings["Borderless Window"] as? BooleanSetting)?.enabled == true
            LegacyModuleBrowserKind.RENDER_INSTANCE_NAME ->
                stringSetting(FloydRender, "Instance Title")?.value?.isNotBlank() == true
            LegacyModuleBrowserKind.RENDER_GUI_STYLE ->
                booleanSetting(ClickGUIModule, "Button Text Chroma")?.enabled == true ||
                    booleanSetting(ClickGUIModule, "Button Border Chroma")?.enabled == true ||
                    booleanSetting(ClickGUIModule, "GUI Border Chroma")?.enabled == true ||
                    booleanSetting(ClickGUIModule, "Button Text Fade")?.enabled == true ||
                    booleanSetting(ClickGUIModule, "Button Border Fade")?.enabled == true ||
                    booleanSetting(ClickGUIModule, "GUI Border Fade")?.enabled == true
            LegacyModuleBrowserKind.RENDER_ANIMATIONS -> FloydAnimations.enabled
            LegacyModuleBrowserKind.PLAYER_CAPE -> FloydCape.isActive()
            LegacyModuleBrowserKind.PLAYER_CONE_HAT -> FloydConeHat.isActive()
            LegacyModuleBrowserKind.PLAYER_NICK_HIDER ->
                booleanSetting(FloydNickHider, "Enabled")?.enabled == true
            LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN ->
                booleanSetting(FloydSkin, "Custom Skin")?.enabled == true
            LegacyModuleBrowserKind.PLAYER_SIZE -> FloydPlayerSize.playerSizeActive()
        }
    }
    private data class ModuleBrowserHeaderHitEntry(val bounds: Rect, val category: Category)
    private data class ModuleBrowserPanelState(var x: Int, var y: Int, var collapsed: Boolean = false, var scroll: Int = 0)
    private data class ModulePopup(val module: Module, var bounds: Rect, val entry: LegacyModuleBrowserEntry? = null)
    private data class ModulePopupHitEntry(val bounds: Rect, val setting: Setting<*>, val kind: ModulePopupHitKind)
    private data class ModulePopupSliderTarget(val setting: NumberSetting<*>, val bounds: Rect)
    private data class ModulePopupStringTarget(val setting: StringSetting)
    private data class ModulePopupExtra(val label: String, val kind: ModulePopupExtraKind)
    private data class ModulePopupExtraHitEntry(val bounds: Rect, val label: String, val kind: ModulePopupExtraKind)
    private data class ModulePopupPlayerHitEntry(val bounds: Rect, val playerName: String?, val submit: Boolean = false)
    private data class ModulePopupXrayHitEntry(
        val bounds: Rect,
        val blockId: String,
        val add: Boolean,
        val action: ModulePopupExtraKind? = null,
        val submit: Boolean = false
    )
    private data class ModulePopupXrayDisplayRow(
        val kind: ModulePopupXrayRowKind,
        val blockId: String = "",
        val add: Boolean = false,
        val action: ModulePopupExtraKind? = null,
        val label: String = ""
    )
    private data class ModulePopupMobDisplayRow(
        val kind: ModulePopupMobRowKind,
        val label: String,
        val hitKind: MobFilterHitKind? = null,
        val buttonLabel: String = "",
        val colorTarget: MobFilterColorTarget? = null,
        val action: ModulePopupExtraKind? = null
    )
    private data class ModulePopupNameDisplayRow(
        val rowKind: ModulePopupNameRowKind,
        val real: String,
        val fake: String? = null,
        val kind: NameMappingHitKind? = null
    )
    private data class TextEditor(
        val title: String,
        var value: String,
        val placeholder: String,
        val hint: String,
        val maxLength: Int,
        val onSave: (String) -> Unit
    )
    private data class ColorPickerEditor(
        val title: String,
        val module: Module,
        val settingName: String,
        val chromaSettingName: String?,
        val originalColor: Color,
        val originalChroma: Boolean,
        var hue: Float,
        var saturation: Float,
        var brightness: Float,
        var hex: String,
        var chromaEnabled: Boolean,
        val fadeSupported: Boolean = false,
        val fadeSettingName: String? = null,
        val fadeEnabledSettingName: String? = null,
        val originalFadeColor: Color = originalColor.copy(),
        val originalFadeEnabled: Boolean = false,
        var fadeHue: Float = hue,
        var fadeSaturation: Float = saturation,
        var fadeBrightness: Float = brightness,
        var fadeEnabled: Boolean = originalFadeEnabled,
        var editingFade: Boolean = false,
        var dragTarget: ColorPickerDrag? = null
    ) {
        fun baseColor(): Int = HSBtoRGB(hue, saturation, brightness) or 0xFF000000.toInt()
        fun fadeColor(): Int = HSBtoRGB(fadeHue, fadeSaturation, fadeBrightness) or 0xFF000000.toInt()
        fun currentColor(): Int = if (editingFade) fadeColor() else baseColor()
        fun activeHue(): Float = if (editingFade) fadeHue else hue
        fun activeSaturation(): Float = if (editingFade) fadeSaturation else saturation
        fun activeBrightness(): Float = if (editingFade) fadeBrightness else brightness
    }

    private enum class ColorPickerDrag {
        SV,
        HUE
    }

    private enum class ModulePopupMappingField {
        ORIGINAL,
        FAKE
    }

    private enum class StyleTarget(
        val label: String,
        val colorSetting: String,
        val chromaSetting: String,
        val fadeColorSetting: String,
        val fadeSetting: String
    ) {
        TEXT("Button Text", "Button Text Color", "Button Text Chroma", "Button Text Fade Color", "Button Text Fade"),
        BUTTON_BORDER("Button Border", "Button Border Color", "Button Border Chroma", "Button Border Fade Color", "Button Border Fade"),
        GUI_BORDER("GUI Border", "GUI Border Color", "GUI Border Chroma", "GUI Border Fade Color", "GUI Border Fade")
    }

    private enum class MobFilterHitKind {
        ADD_MANUAL_NAME,
        ADD_MANUAL_TYPE,
        ADD_NAME,
        REMOVE_NAME,
        ADD_TYPE,
        REMOVE_TYPE,
        COLOR,
        PICKER_SV,
        PICKER_HUE,
        PICKER_CHROMA
    }

    private enum class MobEspHitKind {
        TOGGLE,
        COLOR_PICK,
        NAV_FILTERS
    }

    private enum class SkinSettingsHitKind {
        TOGGLE,
        OPEN_FOLDER,
        DROPDOWN
    }

    private enum class CosmeticHitKind {
        TOGGLE_SKIN,
        TOGGLE_CAPE,
        TOGGLE_CONE,
        NAV_CONFIG,
        TARGET,
        SLIDER
    }

    private enum class NameMappingHitKind {
        ADD_MANUAL,
        ADD_MANUAL_ORIGINAL,
        ADD_MANUAL_FAKE,
        ADD_MANUAL_SAVE,
        ADD,
        ADD_TEXT,
        SAVE_ADD,
        REMOVE,
        REVEAL
    }

    private enum class AnimationHitKind {
        TOGGLE_MODULE,
        TOGGLE_SETTING,
        SLIDER
    }

    private enum class CameraHitKind {
        RUNTIME_TOGGLE,
        BOOLEAN_TOGGLE,
        SLIDER
    }

    private enum class RenderHitKind {
        BOOLEAN_TOGGLE,
        XRAY_TOGGLE,
        MODULE_TOGGLE,
        SLIDER,
        NAV_XRAY,
        RELOAD_XRAY,
        NAV_MOB_ESP,
        NAV_HIDERS,
        NAV_ANIMATIONS,
        STALK,
        BORDERLESS,
        TITLE_FIELD
    }

    private enum class HidersHitKind {
        TOGGLE,
        NO_ARMOR
    }

    private enum class NickHiderHitKind {
        TOGGLE,
        EDIT_NAMES,
        PLAYER_SIZE,
        RELOAD_NAMES
    }

    private enum class ModulePopupHitKind {
        TOGGLE,
        NUMBER,
        SELECTOR,
        CYCLE_STRING,
        STRING,
        COLOR,
        ACTION,
        UNSUPPORTED
    }

    private enum class ModulePopupXrayRowKind {
        ACTION,
        HEADER,
        EMPTY,
        BLOCK
    }

    private enum class ModulePopupMobRowKind {
        ACTION,
        HEADER,
        EMPTY,
        ENTRY
    }

    private enum class ModulePopupNameRowKind {
        ACTION,
        HEADER,
        EMPTY,
        ENTRY,
        INLINE_ADD
    }

    private enum class ModulePopupExtraKind {
        XRAY_BLOCKS,
        XRAY_ADD_BLOCK,
        MOB_FILTERS,
        MOB_ADD_NAME,
        MOB_ADD_TYPE,
        NAME_MAPPINGS,
        NAME_ADD_MAPPING,
        STALK_TARGET,
        RELOAD_NAMES,
        HUD_LAYOUT
    }

    private enum class LegacyModuleBrowserKind {
        MODULE,
        CAMERA_FREECAM,
        CAMERA_FREELOOK,
        CAMERA_F5,
        HIDER_BOOLEAN,
        HIDER_NO_ARMOR,
        RENDER_HIDER_BOOLEAN,
        RENDER_BOOLEAN,
        RENDER_STALK,
        RENDER_HUD,
        RENDER_BORDERLESS,
        RENDER_INSTANCE_NAME,
        RENDER_GUI_STYLE,
        RENDER_ANIMATIONS,
        PLAYER_CAPE,
        PLAYER_CONE_HAT,
        PLAYER_NICK_HIDER,
        PLAYER_CUSTOM_SKIN,
        PLAYER_SIZE;

        val isRenderVirtual: Boolean
            get() = this in setOf(
                RENDER_HIDER_BOOLEAN,
                RENDER_BOOLEAN,
                RENDER_STALK,
                RENDER_HUD,
                RENDER_BORDERLESS,
                RENDER_INSTANCE_NAME,
                RENDER_GUI_STYLE,
                RENDER_ANIMATIONS
            )

        val isPlayerVirtual: Boolean
            get() = this in setOf(
                PLAYER_CAPE,
                PLAYER_CONE_HAT,
                PLAYER_NICK_HIDER,
                PLAYER_CUSTOM_SKIN,
                PLAYER_SIZE
            )

        val isCameraVirtual: Boolean
            get() = this in setOf(
                CAMERA_FREECAM,
                CAMERA_FREELOOK,
                CAMERA_F5
            )
    }

    private data class Rect(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
        fun contains(x: Double, y: Double): Boolean = x >= left && x <= right && y >= top && y <= bottom

        companion object {
            val ZERO = Rect(0, 0, 0, 0)
        }
    }
}
