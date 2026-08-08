package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
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
    ).apply {
        fontSizeOverride = 10f
        textPaddingX = 3f
        textPaddingY = 2f
    }

    internal val isEditing: Boolean get() = textInputHandler.isListening

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        updateTextInputLayout(x, y)

        NVGRenderer.rect(x, y, width, getHeight(), ClickGUI.settingBackground())
        textInputHandler.draw(mouseX, mouseY)
        if (value.isBlank() && !textInputHandler.isListening) {
            val placeholder = "$name..."
            NVGRenderer.textCentered(
                placeholder,
                x,
                y,
                width,
                getHeight(),
                10f,
                ClickGUI.oringoTextMuted.rgba,
                NVGRenderer.defaultFont,
                NVGRenderer.textWidth(placeholder, 10f, NVGRenderer.defaultFont)
            )
        }

        return getHeight()
    }

    internal fun updateTextInputLayout(x: Float, y: Float) {
        textInputHandler.x = x
        textInputHandler.y = y
        textInputHandler.width = width
        textInputHandler.height = getHeight()
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
