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
            // Invalidate the cached label width; render() recomputes it lazily (line ~66). Computing it
            // here would touch NVGRenderer during config load — before the GL context exists — and crash.
            keyNameWidth = -1f
            if (!suppressSync && !KeybindSync.isSyncing()) KeybindSync.syncFromSetting(this, newKey)
        }
    var onPress: (() -> Unit)? = null
    private var keyNameWidth = -1f

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
        val labelWidth = NVGRenderer.textWidth("Key", LABEL_TEXT_SIZE, NVGRenderer.defaultFont)
        val maxValueWidth = (width - labelWidth - 10f).coerceAtLeast(18f)
        val valueTextSize = fittedTextSize(valueText, VALUE_TEXT_MAX, VALUE_TEXT_MIN, maxValueWidth)
        keyNameWidth = NVGRenderer.textWidth(valueText, valueTextSize, NVGRenderer.defaultFont)
        NVGRenderer.rect(x, y, width, height, ClickGUI.settingBackground())
        NVGRenderer.text("Key", x + 3f, y + height / 2f - 5f, LABEL_TEXT_SIZE, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.text(valueText, x + width - keyNameWidth - 3f, y + height / 2f - 4f, valueTextSize, ClickGUI.oringoTextMuted.rgba, NVGRenderer.defaultFont)

        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (listening) {
            value = InputConstants.Type.MOUSE.getOrCreate(click.button())
            listening = false
            return true
        } else {
            val valueText = "[${value.displayName.string}]"
            val labelWidth = NVGRenderer.textWidth("Key", LABEL_TEXT_SIZE, NVGRenderer.defaultFont)
            val maxValueWidth = (width - labelWidth - 10f).coerceAtLeast(18f)
            val hitTextSize = fittedTextSize(valueText, VALUE_TEXT_MAX, VALUE_TEXT_MIN, maxValueWidth)
            val hitWidth = NVGRenderer.textWidth(valueText, hitTextSize, NVGRenderer.defaultFont)
            val rectX = lastX + width - hitWidth - 4f
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

    private fun fittedTextSize(text: String, start: Float, min: Float, maxWidth: Float): Float {
        var size = start
        while (size > min && NVGRenderer.textWidth(text, size, NVGRenderer.defaultFont) > maxWidth) {
            size -= 0.5f
        }
        return size.coerceAtLeast(min)
    }

    companion object {
        private const val LABEL_TEXT_SIZE = 10f
        private const val VALUE_TEXT_MAX = 9f
        private const val VALUE_TEXT_MIN = 7f

        fun InputConstants.Key.isDown(): Boolean {
            val window = mc.window
            return if (value > 7) InputConstants.isKeyDown(window, value)
            else GLFW.glfwGetMouseButton(window.handle(), value) == GLFW.GLFW_PRESS
        }
    }
}
