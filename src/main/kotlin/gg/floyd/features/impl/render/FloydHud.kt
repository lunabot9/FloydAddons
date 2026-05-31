package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudManager
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.features.Category
import gg.floyd.features.Module

object FloydHud : Module(
    name = "HUD",
    category = Category.RENDER,
    description = "Floyd movable HUD editor entry point.",
    toggled = true,
) {
    private val openEditor by ActionSetting("Open HUD Editor", desc = "Opens the HUD editor when clicked.") { mc.setScreen(HudManager) }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "cornerRadius" to FloydPanelStyle.panelCornerRadius
    )
}
