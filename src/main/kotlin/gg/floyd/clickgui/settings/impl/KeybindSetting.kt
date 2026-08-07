package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.mojang.blaze3d.platform.InputConstants
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
import gg.floyd.keybind.KeybindSync
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.KeyMapping
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

class KeybindSetting(
    name: String,
    override val default: InputConstants.Key,
    desc: String
) : RenderableSetting<InputConstants.Key>(name, desc), Saving {
    constructor(name: String, defaultKeyCode: Int, desc: String = "") : this(name, InputConstants.Type.KEYSYM.getOrCreate(defaultKeyCode), desc)

    override var value: InputConstants.Key = default
        set(newKey) {
            if (newKey == field) return
            field = newKey
            if (!suppressSync && !KeybindSync.isSyncing()) KeybindSync.syncFromSetting(this, newKey)
        }
    var onPress: (() -> Unit)? = null

    /** The vanilla [KeyMapping] this setting is mirrored to, if [KeybindSync.register] has run. */
    internal var keyMapping: KeyMapping? = null
        private set

    /** When true, value-setter sync to the vanilla binding is suppressed (used by [applyExternalKey]). */
    private var suppressSync = false

    /** Wires this setting to its vanilla [KeyMapping]. Called by [KeybindSync.register]. */
    fun bindKeyMapping(mapping: KeyMapping) {
        keyMapping = mapping
    }

    /**
     * Applies a key that originated from the vanilla Controls screen, without pushing it back to the
     * vanilla binding (that would loop). Called by [KeybindSync.syncFromBinding].
     */
    fun applyExternalKey(newKey: InputConstants.Key) {
        suppressSync = true
        try {
            value = newKey
        } finally {
            suppressSync = false
        }
    }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()

        val valueText = if (listening) "[...]" else "[${value.displayName.string}]"
        val layout = textLayout(valueText)
        NVGRenderer.rect(x, y, width, height, ClickGUI.settingBackground())
        NVGRenderer.text(name, x + 3f, y + (height - layout.labelSize) / 2f, layout.labelSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.text(valueText, x + width - layout.valueWidth - 3f, y + (height - layout.valueSize) / 2f, layout.valueSize, ClickGUI.oringoTextMuted.rgba, NVGRenderer.defaultFont)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (listening) {
            value = InputConstants.Type.MOUSE.getOrCreate(click.button())
            listening = false
            return true
        } else {
            val valueText = "[${value.displayName.string}]"
            val rectX = lastX + width - textLayout(valueText).valueWidth - 4f
            if (click.button() == 0 && mouseX in rectX..(lastX + width) && mouseY in lastY..(lastY + getHeight())) {
                listening = true
                return true
            }
        }
        return false
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (!listening) return false

        when (input.key) {
            GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE -> value = InputConstants.UNKNOWN
            GLFW.GLFW_KEY_ENTER -> listening = false
            else -> value = InputConstants.getKey(input)
        }

        listening = false
        return true
    }

    fun onPress(block: () -> Unit): KeybindSetting {
        onPress = block
        return this
    }



    override val isHovered: Boolean
        get() = isAreaHovered(lastX, lastY, width, getHeight(), true)

    override fun write(gson: Gson): JsonElement = JsonPrimitive(value.name)

    override fun read(element: JsonElement, gson: Gson) {
        element.asString?.let { value = InputConstants.getKey(it) }
    }

    override fun reset() {
        value = default
    }

    private fun textLayout(valueText: String): TextLayout {
        val valueSize = fitTextToWidth(valueText, width * VALUE_WIDTH_FRACTION, VALUE_TEXT_MAX, VALUE_TEXT_MIN)
        val valueWidth = NVGRenderer.textWidth(valueText, valueSize, NVGRenderer.defaultFont)
        val labelMaxWidth = (width - 3f - TEXT_GAP - valueWidth - 3f).coerceAtLeast(18f)
        val labelSize = fitTextToWidth(name, labelMaxWidth, LABEL_TEXT_SIZE, LABEL_TEXT_MIN)
        return TextLayout(labelSize, valueSize, valueWidth)
    }

    private data class TextLayout(val labelSize: Float, val valueSize: Float, val valueWidth: Float)

    companion object {
        private const val LABEL_TEXT_SIZE = 10f
        private const val LABEL_TEXT_MIN = 6f
        private const val VALUE_TEXT_MAX = 9f
        private const val VALUE_TEXT_MIN = 6.5f
        private const val VALUE_WIDTH_FRACTION = 0.44f
        private const val TEXT_GAP = 4f

        fun InputConstants.Key.isDown(): Boolean {
            val window = mc.window
            return if (value > 7) InputConstants.isKeyDown(window, value)
            else GLFW.glfwGetMouseButton(window.handle(), value) == GLFW.GLFW_PRESS
        }
    }
}
