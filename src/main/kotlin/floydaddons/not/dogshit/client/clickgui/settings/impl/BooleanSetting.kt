package floydaddons.not.dogshit.client.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import floydaddons.not.dogshit.client.clickgui.ClickGUI.gray38
import floydaddons.not.dogshit.client.clickgui.settings.RenderableSetting
import floydaddons.not.dogshit.client.clickgui.settings.Saving
import floydaddons.not.dogshit.client.features.impl.render.ClickGUIModule
import floydaddons.not.dogshit.client.utils.Color
import floydaddons.not.dogshit.client.utils.Color.Companion.brighter
import floydaddons.not.dogshit.client.utils.Colors
import floydaddons.not.dogshit.client.utils.ui.animations.LinearAnimation
import floydaddons.not.dogshit.client.utils.ui.isAreaHovered
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent

class BooleanSetting(
    name: String,
    override val default: Boolean = false,
    desc: String,
) : RenderableSetting<Boolean>(name, desc), Saving {

    override var value: Boolean = default
    var enabled: Boolean by this::value

    private val toggleAnimation = LinearAnimation<Float>(200)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        NVGRenderer.rect(x + width - 40f, y + height / 2f - 10f, 34f, 20f, if (isHovered) gray38.brighter().rgba else gray38.rgba, 9f)

        if (enabled || toggleAnimation.isAnimating()) {
            val color = Color(ClickGUIModule.guiAccentColor())
            NVGRenderer.rect(
                x + width - 40f,
                y + height / 2f - 10f,
                toggleAnimation.get(34f, 9f, enabled),
                20f,
                if (isHovered) color.brighter().rgba else color.rgba,
                9f
            )
        }

        NVGRenderer.hollowRect(
            x + width - 40f,
            y + height / 2f - 10f,
            34f,
            20f,
            2f,
            ClickGUIModule.guiAccentColor(),
            9f
        )
        NVGRenderer.circle(x + width - toggleAnimation.get(30f, 14f, !enabled), y + height / 2f, 6f, Colors.WHITE.rgba)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            toggleAnimation.start()
            enabled = !enabled
            true
        }
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 43f, lastY + getHeight() / 2f - 10f, 34f, 20f, true)

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

    private val toggleAnimation = LinearAnimation<Float>(200)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        NVGRenderer.rect(x + width - 40f, y + height / 2f - 10f, 34f, 20f, if (isHovered) gray38.brighter().rgba else gray38.rgba, 9f)

        if (enabled || toggleAnimation.isAnimating()) {
            val color = Color(ClickGUIModule.guiAccentColor())
            NVGRenderer.rect(
                x + width - 40f,
                y + height / 2f - 10f,
                toggleAnimation.get(34f, 9f, enabled),
                20f,
                if (isHovered) color.brighter().rgba else color.rgba,
                9f
            )
        }

        NVGRenderer.hollowRect(
            x + width - 40f,
            y + height / 2f - 10f,
            34f,
            20f,
            2f,
            ClickGUIModule.guiAccentColor(),
            9f
        )
        NVGRenderer.circle(x + width - toggleAnimation.get(30f, 14f, !enabled), y + height / 2f, 6f, Colors.WHITE.rgba)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            toggleAnimation.start()
            enabled = !enabled
            true
        }
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 43f, lastY + getHeight() / 2f - 10f, 34f, 20f, true)
}
