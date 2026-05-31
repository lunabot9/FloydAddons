package gg.floyd.features.impl.render

import gg.floyd.features.Category
import gg.floyd.features.Module

/**
 * Standalone toggle for the Floyd custom scoreboard.
 *
 * When enabled, the vanilla sidebar is replaced by [FloydHud]'s rounded, gradient-bordered
 * scoreboard, rendered through the global custom font (vanilla [net.minecraft.client.gui.Font]),
 * so it inherits color pass-through, glyph fallback, and overlay blur for free.
 *
 * Previously this was a buried `Custom Scoreboard` BooleanSetting inside [FloydRender]; it is now
 * its own module so it can be toggled without enabling all of Render.
 */
object FloydCustomScoreboard : Module(
    name = "Custom Scoreboard",
    category = Category.RENDER,
    description = "Replaces the vanilla scoreboard with Floyd's rounded, gradient-bordered HUD scoreboard.",
    toggled = false,
) {
    @JvmStatic
    fun shouldUseCustomScoreboard(): Boolean = enabled

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "shouldUseCustomScoreboard" to shouldUseCustomScoreboard()
    )
}
