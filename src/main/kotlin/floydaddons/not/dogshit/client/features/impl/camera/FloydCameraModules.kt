package floydaddons.not.dogshit.client.features.impl.camera

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import floydaddons.not.dogshit.client.clickgui.ClickGUI.gray38
import floydaddons.not.dogshit.client.clickgui.settings.RenderableSetting
import floydaddons.not.dogshit.client.clickgui.settings.Saving
import floydaddons.not.dogshit.client.clickgui.settings.impl.KeybindSetting
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module
import floydaddons.not.dogshit.client.utils.Colors
import floydaddons.not.dogshit.client.features.impl.render.ClickGUIModule
import floydaddons.not.dogshit.client.utils.ui.isAreaHovered
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.floor
import kotlin.math.roundToInt

private class BoundBooleanSetting(
    name: String,
    private val getter: () -> Boolean,
    private val setter: (Boolean) -> Unit,
    desc: String
) : RenderableSetting<Boolean>(name, desc), Saving {
    override val default: Boolean = getter()
    override var value: Boolean
        get() = getter()
        set(value) = setter(value)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()
        NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        val enabled = value
        NVGRenderer.rect(x + width - 40f, y + height / 2f - 10f, 34f, 20f, gray38.rgba, 9f)
        if (enabled) {
            NVGRenderer.rect(
                x + width - 40f,
                y + height / 2f - 10f,
                34f,
                20f,
                ClickGUIModule.guiAccentColor(),
                9f
            )
        }
        NVGRenderer.hollowRect(x + width - 40f, y + height / 2f - 10f, 34f, 20f, 2f, ClickGUIModule.guiAccentColor(), 9f)
        NVGRenderer.circle(x + width - if (enabled) 16f else 30f, y + height / 2f, 6f, Colors.WHITE.rgba)
        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != 0 || !isHovered) return false
        value = !value
        return true
    }

    override val isHovered: Boolean
        get() = isAreaHovered(lastX + width - 43f, lastY + getHeight() / 2f - 10f, 34f, 20f, true)

    override fun read(element: JsonElement, gson: Gson) {
        value = element.asBoolean
    }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)
}

private class BoundNumberSetting(
    name: String,
    private val getter: () -> Float,
    private val setter: (Float) -> Unit,
    private val min: Float,
    private val max: Float,
    private val step: Float,
    desc: String,
) : RenderableSetting<Float>(name, desc), Saving {
    private var valueWidth = -1f
    private var displayValue = ""
    private var sliderPercentage = 0f
    private var dragging = false

    override val default: Float = getter()
    override var value: Float
        get() = getter()
        set(value) {
            setter(value.coerceIn(min, max))
            valueWidth = -1f
            sliderPercentage = ((getter() - min) / (max - min)).coerceIn(0f, 1f)
            displayValue = getDisplay(getter())
        }

    init {
        value = default
        displayValue = getDisplay(getter())
    }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()
        val current = getter()
        sliderPercentage = ((current - min) / (max - min)).coerceIn(0f, 1f)
        val nextDisplay = getDisplay(current)
        if (displayValue != nextDisplay) {
            displayValue = nextDisplay
            valueWidth = -1f
        }

        if (dragging) {
            val newPercentage = ((mouseX - (x + 6f)) / (width - 12f)).coerceIn(0f, 1f)
            value = min + newPercentage * (max - min)
        }

        if (valueWidth < 0) valueWidth = NVGRenderer.textWidth(displayValue, 16f, NVGRenderer.defaultFont)
        NVGRenderer.text(name, x + 6f, y + height / 2f - 15f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.text(displayValue, x + width - valueWidth - 4f, y + height / 2f - 15f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        NVGRenderer.rect(x + 6f, y + 24f, width - 12f, 8f, gray38.rgba, 3f)
        NVGRenderer.rect(x + 6f, y + 24f, sliderPercentage * (width - 12f), 8f, ClickGUIModule.guiAccentColor(), 3f)
        NVGRenderer.circle(x + 6f + sliderPercentage * (width - 12f), y + 28f, 7f, Colors.WHITE.rgba)
        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != 0 || !isHovered) return false
        dragging = true
        updateFromMouse(mouseX)
        return true
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        dragging = false
    }

    override fun keyPressed(input: net.minecraft.client.input.KeyEvent): Boolean {
        if (!isHovered) return false
        when (input.key) {
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_EQUAL -> value = getter() + step
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_MINUS -> value = getter() - step
            else -> return false
        }
        return true
    }

    override val isHovered: Boolean
        get() = isAreaHovered(lastX, lastY + getHeight() / 2, width, getHeight() / 2, true)

    override fun read(element: JsonElement, gson: Gson) {
        value = element.asNumber.toFloat()
    }

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value)

    override fun getHeight(): Float = 40f

    private fun updateFromMouse(mouseX: Float) {
        val newPercentage = ((mouseX - (lastX + 6f)) / (width - 12f)).coerceIn(0f, 1f)
        value = min + newPercentage * (max - min)
    }

    private fun getDisplay(current: Float): String {
        val currentDouble = current.toDouble()
        return if (currentDouble - floor(currentDouble) == 0.0) "${current.roundToInt()}" else "${(current * 100f).roundToInt() / 100f}"
    }
}

object FloydFreecamModule : Module(
    name = "Freecam",
    category = Category.CAMERA,
    description = "Detached spectator-style camera.",
) {
    private val toggleKey by KeybindSetting("Toggle Freecam", GLFW.GLFW_KEY_UNKNOWN, desc = "Floyd freecam toggle key.").onPress { toggle() }
    private val speed by BoundNumberSetting(
        "Speed",
        getter = { FloydCamera.freecamSpeed },
        setter = { FloydCamera.freecamSpeed = it },
        min = 0.1f,
        max = 10.0f,
        step = 0.1f,
        desc = "Movement speed for freecam."
    )

    override fun onEnable() {
        if (!FloydCamera.freecamActive()) FloydCamera.toggleFreecam()
        super.onEnable()
    }

    override fun onDisable() {
        if (FloydCamera.freecamActive()) FloydCamera.toggleFreecam()
        super.onDisable()
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "toggleKey" to toggleKey.displayName.string,
        "speed" to speed
    )
}

object FloydFreelookModule : Module(
    name = "Freelook",
    category = Category.CAMERA,
    description = "Orbit camera around the player.",
) {
    private val toggleKey by KeybindSetting("Toggle Freelook", GLFW.GLFW_KEY_V, desc = "Floyd freelook toggle key.").onPress { toggle() }
    private val distance by BoundNumberSetting(
        "Distance",
        getter = { FloydCamera.freelookDistance },
        setter = { FloydCamera.freelookDistance = it },
        min = 1.0f,
        max = 20.0f,
        step = 0.5f,
        desc = "Third-person freelook distance."
    )

    override fun onEnable() {
        if (!FloydCamera.freelookActive()) FloydCamera.toggleFreelook()
        super.onEnable()
    }

    override fun onDisable() {
        if (FloydCamera.freelookActive()) FloydCamera.toggleFreelook()
        super.onDisable()
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "toggleKey" to toggleKey.displayName.string,
        "distance" to distance
    )
}

object FloydF5CustomizerModule : Module(
    name = "F5 Customizer",
    category = Category.CAMERA,
    description = "Third-person camera distance and cycling controls.",
    toggled = true,
) {
    private val disableFront by BoundBooleanSetting("Disable Front Cam", getter = { FloydCamera.f5DisableFront }, setter = { FloydCamera.f5DisableFront = it }, desc = "Skips front-facing third person when cycling camera.")
    private val disableBack by BoundBooleanSetting("Disable Back Cam", getter = { FloydCamera.f5DisableBack }, setter = { FloydCamera.f5DisableBack = it }, desc = "Skips back-facing third person when cycling camera.")
    private val noClip by BoundBooleanSetting("No Third-Person Clipping", getter = { FloydCamera.f5NoClip }, setter = { FloydCamera.f5NoClip = it }, desc = "Stops third-person camera distance from being clipped by blocks.")
    private val scrollEnabled by BoundBooleanSetting("Scrolling Changes Distance", getter = { FloydCamera.f5ScrollEnabled }, setter = { FloydCamera.f5ScrollEnabled = it }, desc = "Mouse wheel changes third-person camera distance.")
    private val resetOnToggle by BoundBooleanSetting("Reset F5 Scrolling", getter = { FloydCamera.f5ResetOnToggle }, setter = { FloydCamera.f5ResetOnToggle = it }, desc = "Resets third-person camera distance when cycling camera.")
    private val distance by BoundNumberSetting(
        "Camera Distance",
        getter = { FloydCamera.f5Distance },
        setter = { FloydCamera.f5Distance = it },
        min = 1.0f,
        max = 20.0f,
        step = 0.5f,
        desc = "Third-person camera distance."
    )

    override fun onEnable() {
        FloydCamera.setF5CustomizerEnabled(true)
        super.onEnable()
    }

    override fun onDisable() {
        FloydCamera.setF5CustomizerEnabled(false)
        super.onDisable()
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "disableFront" to disableFront,
        "disableBack" to disableBack,
        "noClip" to noClip,
        "scrollEnabled" to scrollEnabled,
        "resetOnToggle" to resetOnToggle,
        "distance" to distance
    )
}

