package gg.floyd.agent

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.impl.render.FloydUiTheme
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics

/** Chat follows the main menu theme without separate appearance controls. */
internal object AgentChatStyle {
    val palette get() = FloydUiTheme.palette()
    fun panel(g: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        NVGPIPRenderer.draw(g, x, y, width, height,
            renderScaleMultiplier = mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio(),
            localCoordinates = true, outerPadding = 8) {
            NVGRenderer.rect(0f, 0f, width.toFloat(), height.toFloat(), palette["Page Background"] or 0xFF000000.toInt(), 9f)
            NVGRenderer.outerShadow(0f, 0f, width.toFloat(), height.toFloat(), 9f)
        }
    }
}
