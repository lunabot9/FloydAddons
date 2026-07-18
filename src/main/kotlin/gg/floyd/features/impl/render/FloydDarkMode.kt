package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt

/**
 * Darkens only the rendered world before HUD elements and screens draw.
 */
object FloydDarkMode : Module(
    name = "Dark Mode",
    category = Category.RENDER,
    description = "Tints the final screen darker.",
    toggled = false,
) {
    val darkness by NumberSetting(
        "Darkness",
        45f,
        0f,
        80f,
        1f,
        desc = "How strongly the screen is darkened.",
        unit = "%"
    )

    @JvmStatic
    fun renderHudBackdrop(guiGraphics: GuiGraphics) {
        if (!enabled) return
        val color = overlayColor(darkness)
        if ((color ushr 24) == 0) return
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), color)
    }

    internal fun overlayColor(percent: Float): Int {
        val alpha = ((percent.coerceIn(0f, 100f) / 100f) * 255f).roundToInt().coerceIn(0, 255)
        return alpha shl 24
    }
}
