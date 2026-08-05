package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.TextInputHandler
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

class StringSetting(
    name: String,
    override val default: String = "",
    private var length: Int = 32,
    desc: String
) : RenderableSetting<String>(name, desc), Saving {

    override var value: String = default
        set(value) {
            field = if (value.length <= length) value else return
        }

    private val textInputHandler = TextInputHandler(
        textProvider = { value },
        textSetter = { value = it }
    )

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)

        val display = when {
            listening -> "${value}_"
            value.isBlank() -> "$name..."
            else -> value
        }
        val displayWidth = NVGRenderer.textWidth(display, 10f, NVGRenderer.defaultFont)

        NVGRenderer.rect(x, y, width, getHeight(), ClickGUI.settingBackground())
        NVGRenderer.textCentered(
            display,
            x,
            y,
            width,
            getHeight(),
            10f,
            if (value.isBlank() && !listening) ClickGUI.oringoTextMuted.rgba else Colors.WHITE.rgba,
            NVGRenderer.defaultFont,
            displayWidth
        )

        return getHeight()
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() == 0) textInputHandler.mouseClicked(mouseX, mouseY, click)
        else false
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        textInputHandler.mouseReleased()
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        return textInputHandler.keyPressed(input)
    }

    override fun keyTyped(input: CharacterEvent): Boolean {
        return textInputHandler.keyTyped(input)
    }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement, gson: Gson) {
        element.asString?.let {
            if (it.length > length) length = it.length
            value = it
        }
    }
}
