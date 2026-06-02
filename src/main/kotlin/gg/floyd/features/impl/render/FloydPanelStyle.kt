package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.AlwaysActive
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
 * color, chroma/fade, corner radius and border width from here, so the whole HUD has one unified,
 * configurable look instead of each feature duplicating the same color/fade/padding settings. The
 * panel-cosmetic settings (corner radius, border width, blur, and the chat-wide chroma toggle) used
 * to be buried on the General module ([FloydRender]); they now live here, and the per-panel
 * scoreboard / overhead color+fade+padding settings were deleted in favour of these globals.
 *
 * [AlwaysActive] so the panel style is always consulted even when no module is "on"; it carries no
 * runtime event handlers (it is purely a settings holder), and it does NOT touch GL at init.
 */
@AlwaysActive
object FloydPanelStyle : Module(
    name = "Panel Style",
    category = Category.RENDER,
    description = "Global look for every Floyd border+background panel: background, border, corner radius, blur.",
    toggled = true,
) {
    val panelCornerRadius by NumberSetting("Panel Corner Radius", 4, 0, 20, 1, desc = "Default rounded corner radius for every Floyd border+background panel.")
    val panelBorderWidth by NumberSetting("Panel Border Width", 2, 0, 6, 1, desc = "Default outline width for every Floyd border+background panel.")
    val panelPadding by NumberSetting("Panel Padding", 6, 0, 16, 1, desc = "Default internal padding between a panel's border and its contents.")

    // Background fill keeps the historic translucent feel (~25% black) as the default; alpha is
    // editable in the picker so the tint can be lightened/darkened or made opaque.
    val panelBackgroundColor by ColorSetting("Panel Background Color", Color(HudPanel.DEFAULT_FILL), allowAlpha = true, desc = "Fill color (with opacity) behind every Floyd panel.")
    val panelBorderColor by ColorSetting("Panel Border Color", Color(0xFFFFFFFF.toInt()).also { it.chroma = true }, desc = "Outline color for every Floyd panel — chroma and fade are configured inside the picker.")

    val panelBlur by BooleanSetting("Panel Blur", false, desc = "Renders a real blurred backdrop of the world behind every Floyd panel.")
    val panelBlurStrength by NumberSetting("Panel Blur Strength", 6, 0, 20, 1, desc = "Blur radius of the backdrop behind Floyd panels.")
    private val blurTypes = listOf("Gaussian", "Box")
    val panelBlurType by SelectorSetting("Panel Blur Type", "Gaussian", blurTypes, desc = "Blur kernel used for the panel backdrop.")

    /** Selected blur-kernel name ([panelBlurType] holds the option index). */
    fun panelBlurTypeName(): String = blurTypes.getOrElse(panelBlurType) { "Gaussian" }

    /** Whether the box kernel is selected (vs the gaussian kernel). */
    fun panelBlurIsBox(): Boolean = panelBlurTypeName() == "Box"

    val fullChatChroma by BooleanSetting("Full Chat Chroma", false, desc = "Cycles all visible chat text through chroma.")

    /** Whether all visible chat text should cycle through chroma. */
    @JvmStatic
    fun shouldUseFullChatChroma(): Boolean = fullChatChroma

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "panelCornerRadius" to panelCornerRadius,
        "panelBorderWidth" to panelBorderWidth,
        "panelPadding" to panelPadding,
        "panelBackgroundColor" to panelBackgroundColor.rgba,
        "panelBorderColor" to panelBorderColor.rgba,
        "panelBorderChroma" to panelBorderColor.chroma,
        "panelBorderFade" to panelBorderColor.fade,
        "panelBlur" to panelBlur,
        "panelBlurStrength" to panelBlurStrength,
        "panelBlurType" to panelBlurTypeName(),
        "fullChatChroma" to fullChatChroma,
        "shouldUseFullChatChroma" to shouldUseFullChatChroma()
    )
}
