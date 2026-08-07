package gg.floyd.clickgui.settings.impl

import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.animations.LinearAnimation
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent

/**
 * A setting intended to show or hide other settings in the GUI.
 *
 * @author Bonsai
 */
class DropdownSetting(
    name: String,
    override val default: Boolean = false,
    desc: String = ""
) : RenderableSetting<Boolean>(name, desc) {
    private companion object {
        const val TEXT_SIZE = 10f
    }

    override var value: Boolean = default
    private var enabled: Boolean by this::value

    private val toggleAnimation = LinearAnimation<Float>(200)
    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()
        val labelSize = fitTextToWidth(name, width - 26f, TEXT_SIZE, 6f)

        NVGRenderer.rect(x, y, width, height, ClickGUI.settingBackground())
        NVGRenderer.text(name, x + 4f, y + (height - labelSize) / 2f, labelSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.text(
            if (enabled) "-" else "+",
            x + width - 14f,
            y + height / 2f - 5f,
            TEXT_SIZE,
            ClickGUI.accentBright(),
            NVGRenderer.defaultFont
        )

        NVGRenderer.push()
        NVGRenderer.translate(x + width - 11f, y + height / 2f)
        NVGRenderer.rotate(toggleAnimation.get(0f, Math.PI.toFloat() / 2f, enabled))
        NVGRenderer.translate(-4f, -4f)
        NVGRenderer.image(ClickGUI.chevronImage, 0f, 0f, 8f, 8f)
        NVGRenderer.pop()

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != 0 || !isHovered) return false
        enabled = !enabled
        toggleAnimation.start()
        return true
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX, lastY, width, getHeight(), true)
}
