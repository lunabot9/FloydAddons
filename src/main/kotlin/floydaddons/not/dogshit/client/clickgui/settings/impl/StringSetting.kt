package floydaddons.not.dogshit.client.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import floydaddons.not.dogshit.client.clickgui.ClickGUI.gray38
import floydaddons.not.dogshit.client.clickgui.Panel
import floydaddons.not.dogshit.client.clickgui.settings.RenderableSetting
import floydaddons.not.dogshit.client.clickgui.settings.Saving
import floydaddons.not.dogshit.client.features.impl.render.ClickGUIModule
import floydaddons.not.dogshit.client.utils.Colors
import floydaddons.not.dogshit.client.utils.ui.TextInputHandler
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGRenderer
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

        val rectStartX = x + 6f

        NVGRenderer.text(name, rectStartX, y + 5f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        NVGRenderer.rect(rectStartX, y + getHeight() - 35f, width - 12f, 30f, gray38.rgba, 4f)
        NVGRenderer.hollowRect(rectStartX, y + getHeight() - 35f, width - 12f, 30f, 2f, ClickGUIModule.guiAccentColor(), 4f)

        textInputHandler.x = rectStartX
        textInputHandler.y = y + getHeight() - 30f
        textInputHandler.width = width - 16f
        textInputHandler.draw(mouseX, mouseY)

        return getHeight()
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() == 0) textInputHandler.mouseClicked(mouseX, mouseY, click)
        else false
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        textInputHandler.mouseReleased()
    }

    override fun clearInteractionState() {
        super.clearInteractionState()
        textInputHandler.mouseReleased()
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        return textInputHandler.keyPressed(input)
    }

    override fun keyTyped(input: CharacterEvent): Boolean {
        return textInputHandler.keyTyped(input)
    }

    override fun getHeight(): Float = Panel.HEIGHT + 28f

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement, gson: Gson) {
        element.asString?.let {
            if (it.length > length) length = it.length
            value = it
        }
    }
}
