package gg.floyd.clickgui.settings.impl

import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.utils.Color.Companion.darker
import gg.floyd.utils.Colors
import gg.floyd.utils.font.FontEpochCache
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent

class ActionSetting(
    name: String,
    desc: String,
    override val default: () -> Unit = {}
) : RenderableSetting<() -> Unit>(name, desc) {

    override var value: () -> Unit = default

    var action: () -> Unit by this::value

    private val textWidth = FontEpochCache { NVGRenderer.textWidth(name, 10f, NVGRenderer.defaultFont) }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        NVGRenderer.rect(x, y, width, height, ClickGUI.settingBackground())
        NVGRenderer.textCentered(
            name,
            x,
            y,
            width,
            height,
            10f,
            if (isHovered) Colors.WHITE.darker().rgba else Colors.WHITE.rgba,
            NVGRenderer.defaultFont,
            NVGRenderer.textWidth(name, 10f, NVGRenderer.defaultFont)
        )
        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            action()
            true
        }
    }
}
