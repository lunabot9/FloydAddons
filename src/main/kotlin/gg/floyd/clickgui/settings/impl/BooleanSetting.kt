package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent

class BooleanSetting(
    name: String,
    override val default: Boolean = false,
    desc: String,
) : RenderableSetting<Boolean>(name, desc), Saving {

    override var value: Boolean = default
    var enabled: Boolean by this::value

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        NVGRenderer.rect(x, y, width, height, ClickGUI.settingBackground())
        val boxX = x + width - 12f
        val boxY = y + height / 2f - 4f
        val labelSize = fitTextToWidth(name, boxX - (x + 3f) - 4f)
        NVGRenderer.rect(boxX, boxY, 8f, 8f, if (enabled) ClickGUI.accentBright() else ClickGUI.settingBackground(), 2f)
        NVGRenderer.hollowRect(boxX, boxY, 8f, 8f, 2f, ClickGUI.accent(), 3f)
        NVGRenderer.text(name, x + 3f, y + (height - labelSize) / 2f, labelSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            enabled = !enabled
            true
        }
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 12f, lastY + getHeight() / 2f - 4f, 8f, 8f, true)

    override fun write(gson: Gson): JsonElement = JsonPrimitive(enabled)

    override fun read(element: JsonElement, gson: Gson) {
        enabled = element.asBoolean
    }
}

class RuntimeBooleanSetting(
    name: String,
    override val default: Boolean = false,
    desc: String,
) : RenderableSetting<Boolean>(name, desc) {

    override var value: Boolean = default
    var enabled: Boolean by this::value

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        NVGRenderer.rect(x, y, width, height, ClickGUI.settingBackground())
        val boxX = x + width - 12f
        val boxY = y + height / 2f - 4f
        val labelSize = fitTextToWidth(name, boxX - (x + 3f) - 4f)
        NVGRenderer.rect(boxX, boxY, 8f, 8f, if (enabled) ClickGUI.accentBright() else ClickGUI.settingBackground(), 2f)
        NVGRenderer.hollowRect(boxX, boxY, 8f, 8f, 2f, ClickGUI.accent(), 3f)
        NVGRenderer.text(name, x + 3f, y + (height - labelSize) / 2f, labelSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            enabled = !enabled
            true
        }
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 12f, lastY + getHeight() / 2f - 4f, 8f, 8f, true)
}
