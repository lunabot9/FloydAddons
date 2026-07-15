package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.Setting.Companion.withDependency
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Color
import gg.floyd.utils.render.HudPanel

/**
 * Owns the cosmetics shared by every Floyd border+background panel.
 *
 * Each Floyd panel that routes through [HudPanel.fillPanel] reads its background color, border
 * color, chroma/fade, corner radius, border width, padding and blur from here. By default ("Per-Panel
 * Style" off) every panel reads ONE shared global set, so the whole HUD has a unified look. Turning
 * "Per-Panel Style" on reveals a "Configure Panel" selector and a per-panel copy of every setting, so
 * each panel (scoreboard, inventory HUD, day tracker, calculator, ESP overhead) can be styled independently while
 * still using the same settings UI — the selector just retargets which panel the settings edit.
 *
 * [AlwaysActive] so the panel style is always consulted even when no module is "on"; it carries no
 * runtime event handlers (it is purely a settings holder), and it does NOT touch GL at init.
 */
@AlwaysActive
object FloydPanelStyle : Module(
    name = "Panel Style",
    category = Category.RENDER,
    description = "Global look for every Floyd border+background panel: background, border, corner radius, blur — globally or per panel.",
    toggled = true,
) {
    /** The Floyd panels that can be styled independently. [label] doubles as the selector option text. */
    enum class PanelTarget(val label: String) {
        SCOREBOARD("Scoreboard"),
        INVENTORY("Inventory HUD"),
        DAY_TRACKER("Day Tracker"),
        CALCULATOR("Calculator"),
        ESP_OVERHEAD("ESP Overhead"),
    }

    private val blurTypes = listOf("Gaussian", "Box")

    // When off, every panel uses the global set below (the historic unified look). When on, each panel
    // uses its own set, chosen via [configurePanel].
    val perPanelStyle by BooleanSetting("Per-Panel Style", false, desc = "Style each Floyd panel independently. Off = one shared look for every panel.")
    private val configurePanel by SelectorSetting("Configure Panel", PanelTarget.entries.first().label, PanelTarget.entries.map { it.label }, desc = "Which panel the settings below configure. Each panel keeps its own values.")
        .withDependency { perPanelStyle }

    // ---- Global set (shown when Per-Panel Style is off; the source of truth for every panel then) ----
    val panelCornerRadius by NumberSetting("Panel Corner Radius", 4, 0, 20, 1, desc = "Default rounded corner radius for every Floyd panel.").withDependency { !perPanelStyle }
    val panelBorderWidth by NumberSetting("Panel Border Width", 2, 0, 6, 1, desc = "Default outline width for every Floyd panel.").withDependency { !perPanelStyle }
    val panelPadding by NumberSetting("Panel Padding", 6, 0, 16, 1, desc = "Default internal padding between a panel's border and its contents.").withDependency { !perPanelStyle }
    val panelBackgroundColor by ColorSetting("Panel Background Color", Color(HudPanel.DEFAULT_FILL), allowAlpha = true, desc = "Fill color (with opacity) behind every Floyd panel.").withDependency { !perPanelStyle }
    val panelBorderColor by ColorSetting("Panel Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Outline color for every Floyd panel — chroma and fade are configured inside the picker.").withDependency { !perPanelStyle }
    val panelBlur by BooleanSetting("Panel Blur", false, desc = "Renders a real blurred backdrop of the world behind every Floyd panel.").withDependency { !perPanelStyle }
    val panelBlurStrength by NumberSetting("Panel Blur Strength", 6, 0, 20, 1, desc = "Blur radius of the backdrop behind Floyd panels.").withDependency { !perPanelStyle }
    private val panelBlurType by SelectorSetting("Panel Blur Type", "Gaussian", blurTypes, desc = "Blur kernel used for the panel backdrop.").withDependency { !perPanelStyle }

    val fullChatChroma by BooleanSetting("Full Chat Chroma", false, desc = "Cycles all visible chat text through chroma.")

    // ---- Per-panel sets (built once; each shown only while its panel is the selected target) ----
    private class PanelSet(
        val cornerRadius: NumberSetting<Int>,
        val borderWidth: NumberSetting<Int>,
        val padding: NumberSetting<Int>,
        val background: ColorSetting,
        val border: ColorSetting,
        val blur: BooleanSetting,
        val blurStrength: NumberSetting<Int>,
        val blurType: SelectorSetting,
    )

    private val panelSets: Map<PanelTarget, PanelSet> = PanelTarget.entries.associateWith { target ->
        val visible = { perPanelStyle && currentTarget() == target }
        PanelSet(
            cornerRadius = registerSetting(NumberSetting("${target.label} Corner Radius", 4, 0, 20, 1, desc = "Rounded corner radius for the ${target.label} panel.")).withDependency(visible),
            borderWidth = registerSetting(NumberSetting("${target.label} Border Width", 2, 0, 6, 1, desc = "Outline width for the ${target.label} panel.")).withDependency(visible),
            padding = registerSetting(NumberSetting("${target.label} Padding", 6, 0, 16, 1, desc = "Internal padding for the ${target.label} panel.")).withDependency(visible),
            background = registerSetting(ColorSetting("${target.label} Background Color", Color(HudPanel.DEFAULT_FILL), allowAlpha = true, desc = "Fill color behind the ${target.label} panel.")).withDependency(visible),
            border = registerSetting(ColorSetting("${target.label} Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Outline color for the ${target.label} panel — chroma/fade inside the picker.")).withDependency(visible),
            blur = registerSetting(BooleanSetting("${target.label} Blur", false, desc = "Blurred backdrop behind the ${target.label} panel.")).withDependency(visible),
            blurStrength = registerSetting(NumberSetting("${target.label} Blur Strength", 6, 0, 20, 1, desc = "Blur radius behind the ${target.label} panel.")).withDependency(visible),
            blurType = registerSetting(SelectorSetting("${target.label} Blur Type", "Gaussian", blurTypes, desc = "Blur kernel for the ${target.label} panel.")).withDependency(visible),
        )
    }

    private fun currentTarget(): PanelTarget = PanelTarget.entries.getOrElse(configurePanel) { PanelTarget.SCOREBOARD }

    // ---- Resolved per-panel accessors. When Per-Panel Style is off, every panel returns the global
    // value (byte-identical to the previous single-set behavior); when on, each returns its own set. ----
    fun cornerRadiusFor(target: PanelTarget): Int = if (!perPanelStyle) panelCornerRadius else panelSets.getValue(target).cornerRadius.value
    fun borderWidthFor(target: PanelTarget): Int = if (!perPanelStyle) panelBorderWidth else panelSets.getValue(target).borderWidth.value
    fun paddingFor(target: PanelTarget): Int = if (!perPanelStyle) panelPadding else panelSets.getValue(target).padding.value
    fun backgroundColorFor(target: PanelTarget): Color = if (!perPanelStyle) panelBackgroundColor else panelSets.getValue(target).background.value
    fun borderColorFor(target: PanelTarget): Color = if (!perPanelStyle) panelBorderColor else panelSets.getValue(target).border.value
    fun blurFor(target: PanelTarget): Boolean = if (!perPanelStyle) panelBlur else panelSets.getValue(target).blur.value
    fun blurStrengthFor(target: PanelTarget): Int = if (!perPanelStyle) panelBlurStrength else panelSets.getValue(target).blurStrength.value
    fun blurIsBoxFor(target: PanelTarget): Boolean =
        blurTypes.getOrElse(if (!perPanelStyle) panelBlurType else panelSets.getValue(target).blurType.value) { "Gaussian" } == "Box"

    /** Whether all visible chat text should cycle through chroma. */
    @JvmStatic
    fun shouldUseFullChatChroma(): Boolean = fullChatChroma

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "perPanelStyle" to perPanelStyle,
        "configurePanel" to currentTarget().label,
        "panelCornerRadius" to panelCornerRadius,
        "panelBorderWidth" to panelBorderWidth,
        "panelPadding" to panelPadding,
        "panelBackgroundColor" to panelBackgroundColor.rgba,
        "panelBorderColor" to panelBorderColor.rgba,
        "panelBorderChroma" to panelBorderColor.chroma,
        "panelBorderFade" to panelBorderColor.fade,
        "panelBlur" to panelBlur,
        "panelBlurStrength" to panelBlurStrength,
        "fullChatChroma" to fullChatChroma,
        "shouldUseFullChatChroma" to shouldUseFullChatChroma()
    )
}
