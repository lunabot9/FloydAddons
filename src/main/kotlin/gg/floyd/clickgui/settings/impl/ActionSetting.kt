package gg.floyd.clickgui.settings.impl

import gg.floyd.clickgui.ClickGUI.gray38
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.features.impl.render.ClickGUIModule
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

    private val textWidth = FontEpochCache { NVGRenderer.textWidth(name, 16f, NVGRenderer.defaultFont) }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        NVGRenderer.rect(x + 4f, y + height / 2f - 13f, width - 8f, 26f, gray38.rgba, 6f)
        NVGRenderer.hollowRect(x + 4f, y + height / 2f - 13f, width - 8f, 26f, 2f, ClickGUIModule.clickGUIColor.rgba, 6f)
        NVGRenderer.textCentered(
            name,
            x + 4f,
            y + height / 2f - 13f,
            width - 8f,
            26f,
            16f,
            if (isHovered) Colors.WHITE.darker().rgba else Colors.WHITE.rgba,
            NVGRenderer.defaultFont,
            textWidth.get()
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
