package gg.floyd.features.impl.render

import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.impl.*
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.ChromaCache
import gg.floyd.utils.Color
import gg.floyd.utils.render.HudPanel
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.round

@AlwaysActive
object ClickGUIModule : Module(
    name = "Click GUI",
    description = "Allows you to customize the UI."
) {
    private const val BOOTSTRAP_SCREEN_WIDTH = 1920f
    private const val BOOTSTRAP_SCREEN_HEIGHT = 1080f
    private const val BOOTSTRAP_DEVICE_PIXEL_RATIO = 1f
    private const val SCREENSHOT_LAYOUT_TOP = 31f
    private const val POSITION_MATCH_TOLERANCE = 0.5f

    val enableNotification by BooleanSetting("Chat notifications", true, desc = "Sends a message when you toggle a module with a keybind")
    val clickGUIColor by ColorSetting("Color", Color(50, 150, 220).also { it.chroma = true }, desc = "The color of the Click GUI — toggle chroma/fade inside the picker.")

    val roundedPanelBottom by BooleanSetting("Rounded Panel Bottoms", true, desc = "Whether to extend panels to make them rounded at the bottom.")
    private val openGuiKey by KeybindSetting("Open GUI Key", GLFW.GLFW_KEY_N, desc = "FloydAddons alternate GUI key.").onPress {
        mc.setScreen(ClickGUI)
    }

    override fun onKeybind() {
        toggle()
    }

    override fun onEnable() {
        mc.setScreen(ClickGUI)
        super.onEnable()
        toggle()
    }

    val panelSetting by MapSetting("Panel Settings", mutableMapOf<String, PanelData>())
    data class PanelData(var x: Float = 10f, var y: Float = 10f, var extended: Boolean = true)

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "chatNotifications" to enableNotification,
        "color" to "#${clickGUIColor.hex()}",
        "chroma" to clickGUIColor.chroma,
        "roundedPanelBottoms" to roundedPanelBottom,
        "panelCount" to panelSetting.size,
        "panels" to panelSetting.mapValues { (_, panel) ->
            mapOf(
                "x" to panel.x,
                "y" to panel.y,
                "extended" to panel.extended
            )
        }
    )

    fun ensurePanelPositionsFit() {
        migrateLegacyPanelNames()
        val activeCategories = Category.categories.values.toList()
        val availableWidth = currentAvailableWidth()
        val wrappedFallbackLayout = wrappedPanelLayout(activeCategories, availableWidth)
        val distinctRows = activeCategories
            .mapNotNull { category -> panelSetting[category.name]?.y }
            .distinct()
        val hasMissing = activeCategories.any { panelSetting[it.name] == null }
        // Any panel whose right edge falls past the window means the layout no longer fits (e.g. the
        // window shrank, or categories changed) — re-flow. Wrapped rows (y > 10) are NOT a trigger;
        // they are a valid multi-row layout and may also be the user's own dragged arrangement.
        val hasOffscreen = activeCategories.any { category ->
            panelSetting[category.name]?.let { it.x < 0f || it.x + Panel.WIDTH > availableWidth } ?: true
        }
        val usesWrappedFallback = activeCategories.all { category ->
            val current = panelSetting[category.name] ?: return@all false
            val wrapped = wrappedFallbackLayout[category.name] ?: return@all false
            positionsMatch(current, wrapped)
        }
        val usesLegacyWrappedRows = distinctRows.size > 1 && activeCategories.all { category ->
            val current = panelSetting[category.name] ?: return@all false
            alignsToWrappedColumn(current.x)
        }
        val hasStackedDefaults = activeCategories
            .mapNotNull { category -> panelSetting[category.name]?.let { category.name to it } }
            .groupBy { (_, panel) -> panel.x to panel.y }
            .values
            .any { entries -> entries.size > 1 }
        if (hasMissing || hasOffscreen || hasStackedDefaults || usesWrappedFallback || usesLegacyWrappedRows) resetPositions()
    }

    private fun migrateLegacyPanelNames() {
        val legacyPvP = panelSetting.remove("PvP") ?: return
        panelSetting.putIfAbsent(Category.PVP.name, legacyPvP)
    }

    fun resetPositions() {
        defaultPanelLayout().forEach { (categoryName, layout) ->
            val setting = panelSetting.getOrPut(categoryName) { PanelData() }
            setting.x = layout.x
            setting.y = layout.y
            setting.extended = true
        }
    }

    fun defaultPanelLayout(): Map<String, PanelData> {
        val activeCategories = Category.categories.values.toList()
        return screenshotPanelLayout(activeCategories)
    }

    private fun screenshotPanelLayout(activeCategories: List<Category>): Map<String, PanelData> {
        val template = linkedMapOf(
            Category.RENDER.name to PanelData(x = 6f, y = SCREENSHOT_LAYOUT_TOP, extended = true),
            Category.HIDERS.name to PanelData(x = 266f, y = SCREENSHOT_LAYOUT_TOP, extended = true),
            Category.PLAYER.name to PanelData(x = 526f, y = SCREENSHOT_LAYOUT_TOP, extended = true),
            Category.COSMETIC.name to PanelData(x = 796f, y = SCREENSHOT_LAYOUT_TOP, extended = true),
            Category.CAMERA.name to PanelData(x = 1086f, y = SCREENSHOT_LAYOUT_TOP, extended = true),
            Category.MISC.name to PanelData(x = 1356f, y = SCREENSHOT_LAYOUT_TOP, extended = true),
            Category.PVP.name to PanelData(x = 1646f, y = SCREENSHOT_LAYOUT_TOP, extended = true),
        )
        val layout = linkedMapOf<String, PanelData>()
        activeCategories.forEach { category ->
            layout[category.name] = template[category.name]?.copy() ?: PanelData(10f, SCREENSHOT_LAYOUT_TOP, true)
        }
        return layout
    }

    private fun wrappedPanelLayout(activeCategories: List<Category>, availableWidth: Float): Map<String, PanelData> {
        val gap = 20f
        val rowGap = 14f
        var x = 10f
        var y = 10f
        var rowMaxHeight = 0f
        val layout = linkedMapOf<String, PanelData>()
        activeCategories.forEach { category ->
            // Wrap to a new row when the panel would extend past the right edge, so no panel is ever
            // pushed off-screen regardless of window width or how many categories exist.
            if (x > 10f && x + Panel.WIDTH > availableWidth) {
                x = 10f
                y += rowMaxHeight + rowGap
                rowMaxHeight = 0f
            }
            layout[category.name] = PanelData(x = x, y = y, extended = true)
            rowMaxHeight = max(rowMaxHeight, estimatedPanelHeight(category))
            x += Panel.WIDTH + gap
        }
        return layout
    }

    private fun positionsMatch(current: PanelData, expected: PanelData): Boolean =
        kotlin.math.abs(current.x - expected.x) <= POSITION_MATCH_TOLERANCE &&
            kotlin.math.abs(current.y - expected.y) <= POSITION_MATCH_TOLERANCE

    private fun alignsToWrappedColumn(x: Float): Boolean {
        val columnStep = Panel.WIDTH + 20f
        val columnIndex = (x - 10f) / columnStep
        return kotlin.math.abs(columnIndex - round(columnIndex)) <= POSITION_MATCH_TOLERANCE / columnStep
    }

    /** Rough extended-panel height (header + one row per module) used for default row-wrapping spacing. */
    private fun estimatedPanelHeight(category: Category): Float {
        val moduleCount = ModuleManager.modulesByCategory[category]?.size ?: 0
        return Panel.HEIGHT + moduleCount * MODULE_ROW_HEIGHT
    }

    private const val MODULE_ROW_HEIGHT = 16f

    /**
     * The Click GUI accent color, honoring EVERY picker mode at [offset]: chroma cycles (the
     * memoized [ChromaCache] computes the rainbow hue at most once per frame per [offset]), fade
     * blends base↔fadeColor on the same 2500ms sine every other Floyd surface uses
     * ([HudPanel.fadeProgress]), otherwise the static color. Fade used to be dropped here (only
     * [Color.rgba]'s phase-0 fade leaked through the else-branch), so offset consumers like the
     * title letters never followed the picker's fade mode. Chroma/fade live in the picker, so
     * there is no separate "GUI Chroma" toggle.
     */
    fun guiAccentColor(offset: Float = 0f): Int = when {
        clickGUIColor.chroma -> 0xFF000000.toInt() or ChromaCache.rgbFor(offset)
        clickGUIColor.fade -> HudPanel.blendColors(
            clickGUIColor.baseRgba,
            clickGUIColor.fadeColor.baseRgba,
            HudPanel.fadeProgress(offset)
        )
        else -> clickGUIColor.rgba
    }

    private fun currentAvailableWidth(): Float {
        val screenWidth = currentScreenWidth()
        val screenHeight = currentScreenHeight()
        val devicePixelRatio = currentDevicePixelRatio(screenWidth)
        return availableWidthForLayout(screenWidth, screenHeight, devicePixelRatio)
    }

    fun getStandardGuiScale(): Float {
        val screenWidth = currentScreenWidth()
        val screenHeight = currentScreenHeight()
        val devicePixelRatio = currentDevicePixelRatio(screenWidth)
        return standardGuiScaleFor(screenWidth, screenHeight, devicePixelRatio)
    }

    private fun currentScreenWidth(): Float =
        runCatching { mc.window.screenWidth.toFloat() }
            .getOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: BOOTSTRAP_SCREEN_WIDTH

    private fun currentScreenHeight(): Float =
        runCatching { mc.window.screenHeight.toFloat() }
            .getOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: BOOTSTRAP_SCREEN_HEIGHT

    private fun currentDevicePixelRatio(screenWidth: Float): Float {
        val windowWidth = runCatching { mc.window.width.toFloat() }
            .getOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: return BOOTSTRAP_DEVICE_PIXEL_RATIO
        return (windowWidth / screenWidth)
            .takeUnless { !it.isFinite() || it <= 0f }
            ?: BOOTSTRAP_DEVICE_PIXEL_RATIO
    }

    internal fun standardGuiScaleFor(screenWidth: Float, screenHeight: Float, devicePixelRatio: Float): Float {
        val verticalScale = (screenHeight / BOOTSTRAP_SCREEN_HEIGHT) / devicePixelRatio
        val horizontalScale = (screenWidth / BOOTSTRAP_SCREEN_WIDTH) / devicePixelRatio
        return round(max(verticalScale, horizontalScale).coerceIn(1f, 1.5f) * 10f) / 10f
    }

    internal fun availableWidthForLayout(screenWidth: Float, screenHeight: Float, devicePixelRatio: Float): Float =
        screenWidth / standardGuiScaleFor(screenWidth, screenHeight, devicePixelRatio)
}
