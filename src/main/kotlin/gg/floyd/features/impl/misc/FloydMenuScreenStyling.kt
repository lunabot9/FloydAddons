package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
object FloydMenuScreenStyling {
    @JvmStatic
    fun shouldReplaceBackground(screen: Screen): Boolean = false

    @JvmStatic
    fun renderBackground(context: GuiGraphics, partialTick: Float) = Unit

    @JvmStatic
    fun renderOverlay(screen: Screen, context: GuiGraphics, partialTick: Float) {
        return
    }

    internal fun drawBrandBlock(title: String, width: Float, height: Float, alpha: Float) {
        val headerX = width * 0.065f
        val headerY = height * 0.07f
        drawWord("FloydAddons", headerX, headerY, 34f, alpha)
        val headerWidth = NVGRenderer.textWidth("FloydAddons", 34f, NVGRenderer.defaultFont)
        val version = "v${FloydAddonsMod.MOD_VERSION}"
        NVGRenderer.text(version, headerX + headerWidth + 10f, headerY + 18f, 12f, colorGray(210, alpha * 0.92f), NVGRenderer.defaultFont)
        NVGRenderer.text(title, headerX, headerY + 38f, 18f, colorGray(210, alpha), NVGRenderer.defaultFont)
    }

    internal fun drawWord(text: String, x: Float, y: Float, size: Float, alpha: Float) {
        NVGRenderer.text(text, x, y, size, colorGray(210, alpha), NVGRenderer.defaultFont)
    }

    private fun colorGray(gray: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        val g = gray.coerceIn(0, 255)
        return (a shl 24) or (g shl 16) or (g shl 8) or g
    }
}
