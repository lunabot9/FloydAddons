package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.HoverHandler
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Setting that lets you pick a number between a range.
 * @author Stivais, Aton
 */
@Suppress("UNCHECKED_CAST")
class NumberSetting<E>(
    name: String,
    override val default: E = 1.0 as E,
    min: Number,
    max: Number,
    increment: Number = 1,
    desc: String,
    private val unit: String = ""
) : RenderableSetting<E>(name, desc), Saving where E : Number, E : Comparable<E> {

    private val incrementDouble = increment.toDouble()
    private val minDouble = min.toDouble()
    private var maxDouble = max.toDouble()

    private val handler = HoverHandler(150)

    private var displayValue = ""

    private var sliderPercentage = 0f
        set(value) {
            if (sliderPercentage != value) {
                displayValue = getDisplay()
            }
            field = value
        }

    override var value: E = default
        set(value) {
            field = roundToIncrement(value).coerceIn(minDouble, maxDouble) as E
            sliderPercentage = ((field.toDouble() - minDouble) / (maxDouble - minDouble)).toFloat()
        }

    init {
        value = default
        displayValue = getDisplay()
    }

    private var valueDouble
        get() = value.toDouble()
        set(value) {
            this.value = value as E
        }

    private var valueInt
        get() = value.toInt()
        set(value) {
            this.value = value as E
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        if (listening) {
            val newPercentage = ((mouseX - x) / width).coerceIn(0f, 1f)
            valueDouble = minDouble + newPercentage * (maxDouble - minDouble)
            sliderPercentage = newPercentage
        }

        val valueSize = fitTextToWidth(displayValue, width * 0.38f, 10f, 6.5f)
        val valueWidth = NVGRenderer.textWidth(displayValue, valueSize, NVGRenderer.defaultFont)
        val labelMaxWidth = (width - 3f - 4f - valueWidth - 3f).coerceAtLeast(18f)
        val labelSize = fitTextToWidth(name, labelMaxWidth, 10f, 6f)

        NVGRenderer.rect(x, y, width, height, ClickGUI.settingBackgroundBright())
        if (sliderPercentage > 0f) {
            NVGRenderer.rect(x, y, sliderPercentage * width, height, ClickGUI.accentBright())
        }
        NVGRenderer.text(name, x + 3f, y + (height - labelSize) / 2f, labelSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.text(displayValue, x + width - valueWidth - 3f, y + (height - valueSize) / 2f, valueSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        return if (click.button() != 0 || !isHovered) false
        else {
            listening = true
            true
        }
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        listening = false
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (!isHovered) return false

        val amount = when (input.key) {
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_EQUAL -> incrementDouble
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_MINUS -> -incrementDouble
            else -> return false
        }

        if (valueDouble !in minDouble..maxDouble) return false
        valueDouble = (valueDouble + amount).coerceIn(minDouble, maxDouble)
        sliderPercentage = ((valueDouble - minDouble) / (maxDouble - minDouble)).toFloat()
        return true
    }

    override val isHovered: Boolean
        get() =
            isAreaHovered(lastX, lastY, width, getHeight(), true)

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)

    override fun read(element: JsonElement, gson: Gson) {
        element.asNumber?.let { value = it as E }
    }

    fun numericValue(): Double = valueDouble

    fun minNumericValue(): Double = minDouble

    fun maxNumericValue(): Double = maxDouble

    fun setNumericValue(value: Double) {
        valueDouble = value.coerceIn(minDouble, maxDouble)
    }

    fun stepNumeric(direction: Int = 1) {
        setNumericValue(valueDouble + incrementDouble * direction)
    }

    private fun roundToIncrement(x: Number): Double =
        round((x.toDouble() / incrementDouble)) * incrementDouble

    private fun getDisplay(): String =
        if (valueDouble - floor(valueDouble) == 0.0)
            "${(valueInt * 100.0).roundToInt() / 100}${unit}"
        else
            "${(valueDouble * 100.0).roundToInt() / 100.0}${unit}"
}
