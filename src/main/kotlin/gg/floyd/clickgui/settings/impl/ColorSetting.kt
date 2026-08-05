package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.ClickGUI.gray38
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.Color
import gg.floyd.utils.Color.Companion.darker
import gg.floyd.utils.Color.Companion.hsbMax
import gg.floyd.utils.Color.Companion.withAlpha
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.TextInputHandler
import gg.floyd.utils.ui.animations.EaseInOutAnimation
import gg.floyd.utils.ui.animations.LinearAnimation
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.Gradient
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

class ColorSetting(
    name: String,
    override val default: Color,
    private var allowAlpha: Boolean = false,
    desc: String
) : RenderableSetting<Color>(name, desc), Saving {
    private companion object {
        const val HEADER_TEXT_SIZE = 10f
        const val HEADER_TEXT_MIN = 7f
        const val TOGGLE_TEXT_SIZE = 7.25f
        const val TOGGLE_TEXT_MIN = 6f
        const val HEX_TEXT_SIZE = 9f
        const val PICKER_HEIGHT = 116f
        const val BAR_HEIGHT = 10f
        const val HEX_BOX_HEIGHT = 18f
        const val TOGGLE_HEIGHT = 16f
        const val PICKER_PADDING = 6f
        const val SECTION_GAP = 6f
        const val TOGGLE_GAP = 4f
        const val TOGGLE_ROW_GAP = 5f
        const val PICKER_RADIUS = 6f
    }

    override var value: Color = default.copy()

    private val expandAnim = EaseInOutAnimation(200)
    private val defaultHeight = Panel.HEIGHT
    private var extended = false

    private val mainSliderAnim = LinearAnimation<Float>(100)
    private var mainSliderPrevSat = 0f
    private var mainSliderPrevBright = 0f

    private val hueSliderAnim = LinearAnimation<Float>(100)
    private var hueSliderPrev = 0f

    private val alphaSliderAnim = LinearAnimation<Float>(100)
    private var alphaSliderPrev = 0f

    var section: Int? = null

    /** When [value] has [Color.fade] on, whether the sliders edit the fade color instead of the base. */
    private var editingFade = false

    /** The color the sliders / hex input currently edit: the fade color while in fade-edit mode, else base. */
    private fun editTarget(): Color = if (editingFade && value.fade) value.fadeColor else value

    private var hexString = value.hex(allowAlpha)
        set(value) {
            if (value == field) return
            field = value
            hexWidth = NVGRenderer.textWidth(field, HEX_TEXT_SIZE, NVGRenderer.defaultFont)
        }

    private var hexWidth = -1f

    private val textInputHandler = TextInputHandler(
        textProvider = { textInputValue },
        textSetter = { textInputValue = it }
    )

    private var textInputValue
        get() = hexString
        set(textValue) {
            if (textValue.length > 8 && allowAlpha) return
            if (textValue.length > 6 && !allowAlpha) return
            hexString = textValue.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }

            if (hexString.length == 8 && allowAlpha || hexString.length == 6 && !allowAlpha)
                editTarget().applyRgba(Color(if (allowAlpha) hexString else hexString.padEnd(8, 'F')).baseRgba)
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        if (hexWidth < 0) {
            hexString = value.hex(allowAlpha)
            hexWidth = NVGRenderer.textWidth(hexString, HEX_TEXT_SIZE, NVGRenderer.defaultFont)
        }

        val previewW = 14f
        val previewH = 9f
        val previewX = x + width - previewW - 5f
        val labelMaxWidth = (previewX - (x + 4f) - 4f).coerceAtLeast(16f)
        val labelSize = fittedHeaderTextSize(name, labelMaxWidth)
        NVGRenderer.text(name, x + 4f, y + defaultHeight / 2f - 4f, labelSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.rect(previewX, y + defaultHeight / 2f - previewH / 2f, previewW, previewH, value.rgba, 3f)
        NVGRenderer.hollowRect(previewX, y + defaultHeight / 2f - previewH / 2f, previewW, previewH, 1.25f, value.withAlpha(1f).darker().rgba, 3f)

        if (!extended && !expandAnim.isAnimating()) return defaultHeight

        if (expandAnim.isAnimating()) NVGRenderer.pushScissor(x, y + defaultHeight, width, getHeight() - defaultHeight)
        val layout = layout(x, y, width)
        handleColorDrag(mouseX, mouseY, layout)
        // The sliders edit the base color, or the fade color while in fade-edit mode.
        val target = editTarget()
        // SATURATION AND BRIGHTNESS
        NVGRenderer.gradientRect(layout.pickerX, layout.pickerY, layout.pickerWidth, PICKER_HEIGHT, Colors.WHITE.rgba, target.hsbMax().rgba, Gradient.LeftToRight, 4f)
        NVGRenderer.gradientRect(layout.pickerX, layout.pickerY, layout.pickerWidth, PICKER_HEIGHT, Colors.TRANSPARENT.rgba, Colors.BLACK.rgba, Gradient.TopToBottom, 4f)

        val satForRender = if (section == 0) target.saturation else mainSliderAnim.get(mainSliderPrevSat, target.saturation, false)
        val brightForRender = if (section == 0) target.brightness else mainSliderAnim.get(mainSliderPrevBright, target.brightness, false)
        val sbPointer = Pair(
            (layout.pickerX + satForRender * layout.pickerWidth).coerceIn(layout.pickerX + PICKER_RADIUS, layout.pickerX + layout.pickerWidth - PICKER_RADIUS),
            (layout.pickerY + (1f - brightForRender) * PICKER_HEIGHT).coerceIn(layout.pickerY + PICKER_RADIUS, layout.pickerY + PICKER_HEIGHT - PICKER_RADIUS)
        )
        NVGRenderer.dropShadow(sbPointer.first - 6.5f, sbPointer.second - 6.5f, 13f, 13f, 2f, 2f, 7f)
        NVGRenderer.circle(sbPointer.first, sbPointer.second, PICKER_RADIUS, Colors.WHITE.rgba)
        NVGRenderer.circle(sbPointer.first, sbPointer.second, PICKER_RADIUS - 1f, target.withAlpha(1f).rgba)

        // HUE
        NVGRenderer.image(ClickGUI.hueImage, layout.pickerX, layout.hueY, layout.pickerWidth, BAR_HEIGHT, 4f)
        NVGRenderer.hollowRect(layout.pickerX, layout.hueY, layout.pickerWidth, BAR_HEIGHT, 1f, gray38.rgba, 4f)

        val hueForRender = if (section == 1) target.hue else hueSliderAnim.get(hueSliderPrev, target.hue, false)
        val huePos = (layout.pickerX + hueForRender * layout.pickerWidth).coerceIn(layout.pickerX + PICKER_RADIUS, layout.pickerX + layout.pickerWidth - PICKER_RADIUS) to (layout.hueY + BAR_HEIGHT / 2f)
        NVGRenderer.dropShadow(huePos.first - 6.5f, huePos.second - 6.5f, 13f, 13f, 2f, 2f, 7f)
        NVGRenderer.circle(huePos.first, huePos.second, PICKER_RADIUS, Colors.WHITE.rgba)
        NVGRenderer.circle(huePos.first, huePos.second, PICKER_RADIUS - 1f, target.hsbMax().withAlpha(1f).rgba)

        // ALPHA
        if (allowAlpha) {
            NVGRenderer.gradientRect(layout.pickerX, layout.alphaY, layout.pickerWidth, BAR_HEIGHT, Colors.TRANSPARENT.rgba, target.withAlpha(1f).rgba, Gradient.LeftToRight, 4f)

            val alphaForRender = if (section == 2) target.alphaFloat else alphaSliderAnim.get(alphaSliderPrev, target.alphaFloat, false)
            val alphaPos = Pair(
                (layout.pickerX + alphaForRender * layout.pickerWidth).coerceIn(layout.pickerX + PICKER_RADIUS, layout.pickerX + layout.pickerWidth - PICKER_RADIUS),
                layout.alphaY + BAR_HEIGHT / 2f
            )
            NVGRenderer.dropShadow(alphaPos.first - 6.5f, alphaPos.second - 6.5f, 13f, 13f, 2f, 2f, 7f)
            NVGRenderer.circle(alphaPos.first, alphaPos.second, PICKER_RADIUS, Colors.WHITE.darker(.5f).rgba)
            NVGRenderer.circle(alphaPos.first, alphaPos.second, PICKER_RADIUS - 1f, Colors.WHITE.rgba)
        }

        if (section != null) hexString = target.hex(allowAlpha)

        // main width - text input
        NVGRenderer.rect(layout.hexX, layout.hexY, layout.hexWidth, HEX_BOX_HEIGHT, gray38.rgba, 4f)
        NVGRenderer.hollowRect(layout.hexX, layout.hexY, layout.hexWidth, HEX_BOX_HEIGHT, 1.5f, ClickGUIModule.clickGUIColor.rgba, 4f)

        textInputHandler.x = layout.hexX + layout.hexWidth / 2f - hexWidth / 2f
        textInputHandler.y = layout.hexY + 1f
        textInputHandler.width = layout.hexWidth
        textInputHandler.height = HEX_BOX_HEIGHT
        textInputHandler.fontSizeOverride = HEX_TEXT_SIZE
        textInputHandler.textPaddingY = 3f
        textInputHandler.draw(mouseX, mouseY)

        // Chroma + Fade toggles (both live inside the picker, not as sibling settings). Mutually exclusive.
        val band = layout.hexWidth
        val gap = TOGGLE_GAP
        val halfBtn = (band - gap) / 2f
        renderToggle(layout.hexX, layout.toggleY, halfBtn, "Chroma", value.chroma)
        renderToggle(layout.hexX + halfBtn + gap, layout.toggleY, halfBtn, "Fade", value.fade)

        // When fade is on, choose whether the sliders edit the base or the (chroma-less) fade color.
        if (value.fade) {
            val editLabel = if (editingFade) "Edit: Fade" else "Edit: Base"
            renderToggle(layout.hexX, layout.toggleY + TOGGLE_HEIGHT + TOGGLE_ROW_GAP, band, editLabel, editingFade)
        }

        if (expandAnim.isAnimating()) NVGRenderer.popScissor()
        return getHeight()
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (isHovered) {
            expandAnim.start()
            extended = !extended
            return true
        }

        if (!extended) return false
        textInputHandler.mouseClicked(mouseX, mouseY, click)

        val layout = layout(lastX, lastY, width)
        val band = layout.hexWidth
        val gap = TOGGLE_GAP
        val halfBtn = (band - gap) / 2f
        // Chroma toggle (left) — mutually exclusive with fade.
        if (contains(mouseX, mouseY, layout.hexX, layout.toggleY, halfBtn, TOGGLE_HEIGHT)) {
            value.chroma = !value.chroma
            if (value.chroma) value.fade = false
            syncEditTargetState()
            return true
        }
        // Fade toggle (right) — mutually exclusive with chroma.
        if (contains(mouseX, mouseY, layout.hexX + halfBtn + gap, layout.toggleY, halfBtn, TOGGLE_HEIGHT)) {
            value.fade = !value.fade
            if (value.fade) value.chroma = false else editingFade = false
            syncEditTargetState()
            return true
        }
        // Edit-target row (only when fade is on): switch the sliders between the base and fade color.
        if (value.fade && contains(mouseX, mouseY, layout.hexX, layout.toggleY + TOGGLE_HEIGHT + TOGGLE_ROW_GAP, band, TOGGLE_HEIGHT)) {
            editingFade = !editingFade
            syncEditTargetState()
            return true
        }

        section = when {
            contains(mouseX, mouseY, layout.pickerX, layout.pickerY, layout.pickerWidth, PICKER_HEIGHT) -> 0 // sat & brightness
            contains(mouseX, mouseY, layout.pickerX, layout.hueY, layout.pickerWidth, BAR_HEIGHT) -> 1 // hue
            contains(mouseX, mouseY, layout.pickerX, layout.alphaY, layout.pickerWidth, BAR_HEIGHT) && allowAlpha -> 2 // alpha
            else -> null
        }

        if (section != null) handleColorDrag(mouseX, mouseY, layout)
        return section != null
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        textInputHandler.mouseReleased()
        section = null
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        return if (extended) textInputHandler.keyPressed(input)
        else false
    }

    override fun keyTyped(input: CharacterEvent): Boolean {
        return if (extended) textInputHandler.keyTyped(input)
        else false
    }

    /** Extra height below the hex input: the toggle row, plus the fade-edit row when fade is on. */
    private fun extrasHeight(): Float = TOGGLE_HEIGHT + 4f + if (value.fade) TOGGLE_HEIGHT + TOGGLE_ROW_GAP else 0f

    private fun contentHeight(): Float {
        val alphaSectionHeight = if (allowAlpha) BAR_HEIGHT + SECTION_GAP else 0f
        return defaultHeight + 4f + PICKER_HEIGHT + SECTION_GAP + BAR_HEIGHT + SECTION_GAP + alphaSectionHeight + HEX_BOX_HEIGHT
    }

    private fun layout(x: Float, y: Float, width: Float): PickerLayout {
        val pickerX = x + PICKER_PADDING
        val pickerWidth = width - PICKER_PADDING * 2f
        val pickerY = y + defaultHeight + 4f
        val hueY = pickerY + PICKER_HEIGHT + SECTION_GAP
        val alphaY = hueY + BAR_HEIGHT + SECTION_GAP
        val hexY = if (allowAlpha) alphaY + BAR_HEIGHT + SECTION_GAP else hueY + BAR_HEIGHT + SECTION_GAP
        val hexWidth = width / 2f
        val hexX = x + (width - hexWidth) / 2f
        val toggleY = y + contentHeight() + 2f
        return PickerLayout(pickerX, pickerWidth, pickerY, hueY, alphaY, hexX, hexWidth, hexY, toggleY)
    }

    override fun getHeight(): Float =
        expandAnim.get(defaultHeight, contentHeight() + extrasHeight(), !extended)

    /** A small labelled toggle button, highlighted when [on]. */
    private fun renderToggle(bx: Float, by: Float, bw: Float, label: String, on: Boolean) {
        NVGRenderer.rect(bx, by, bw, TOGGLE_HEIGHT, gray38.rgba, 4f)
        NVGRenderer.hollowRect(bx, by, bw, TOGGLE_HEIGHT, 1.5f, (if (on) ClickGUIModule.clickGUIColor else Colors.gray38).rgba, 4f)
        val size = fittedTextSize(label, bw - 8f, TOGGLE_TEXT_SIZE, TOGGLE_TEXT_MIN)
        val lw = NVGRenderer.textWidth(label, size, NVGRenderer.defaultFont)
        NVGRenderer.textCentered(label, bx, by, bw, TOGGLE_HEIGHT, size, Colors.WHITE.rgba, NVGRenderer.defaultFont, lw)
    }

    /** Re-seed the slider animations + hex box to the active edit target so switching never jumps. */
    private fun syncEditTargetState() {
        val t = editTarget()
        mainSliderPrevSat = t.saturation
        mainSliderPrevBright = t.brightness
        hueSliderPrev = t.hue
        alphaSliderPrev = t.alphaFloat
        hexString = t.hex(allowAlpha)
    }

    override val isHovered: Boolean
        get() = isAreaHovered(
            lastX + width - 40f,
            lastY + defaultHeight / 2f - 10f,
            34f,
            20f,
            true
        )

    override fun write(gson: Gson): JsonElement = gson.toJsonTree(value, Color::class.java)

    override fun read(element: JsonElement, gson: Gson) {
        value = gson.fromJson(element, Color::class.java) ?: default.copy()
    }

    private fun handleColorDrag(mouseX: Float, mouseY: Float, layout: PickerLayout) {
        val target = editTarget()
        when (section) {
            0 -> { // Saturation & Brightness
                val newSaturation = ((mouseX - layout.pickerX) / layout.pickerWidth).coerceIn(0f, 1f)
                val newBrightness = (1f - ((mouseY - layout.pickerY) / PICKER_HEIGHT)).coerceIn(0f, 1f)

                if (newSaturation != target.saturation || newBrightness != target.brightness) {
                    mainSliderPrevSat = mainSliderAnim.get(mainSliderPrevSat, target.saturation, false)
                    mainSliderPrevBright = mainSliderAnim.get(mainSliderPrevBright, target.brightness, false)
                    mainSliderAnim.start()

                    target.saturation = newSaturation
                    target.brightness = newBrightness
                }
            }

            1 -> { // Hue
                val newHue = ((mouseX - layout.pickerX) / layout.pickerWidth).coerceIn(0f, 1f)
                if (newHue != target.hue) {
                    hueSliderPrev = hueSliderAnim.get(hueSliderPrev, target.hue, false)
                    hueSliderAnim.start()
                    target.hue = newHue
                }
            }

            2 -> { // Alpha
                val newAlpha = ((mouseX - layout.pickerX) / layout.pickerWidth).coerceIn(0f, 1f)
                if (newAlpha != target.alphaFloat) {
                    alphaSliderPrev = alphaSliderAnim.get(alphaSliderPrev, target.alphaFloat, false)
                    alphaSliderAnim.start()
                    target.alphaFloat = newAlpha
                }
            }
        }
    }

    private fun fittedHeaderTextSize(text: String, maxWidth: Float): Float {
        return fittedTextSize(text, maxWidth, HEADER_TEXT_SIZE, HEADER_TEXT_MIN)
    }

    private fun fittedTextSize(text: String, maxWidth: Float, start: Float, minimum: Float): Float {
        var size = start
        while (size > minimum && NVGRenderer.textWidth(text, size, NVGRenderer.defaultFont) > maxWidth) {
            size -= 0.5f
        }
        return size.coerceAtLeast(minimum)
    }

    private fun contains(mouseX: Float, mouseY: Float, x: Float, y: Float, width: Float, height: Float): Boolean {
        return mouseX in x..(x + width) && mouseY in y..(y + height)
    }

    private data class PickerLayout(
        val pickerX: Float,
        val pickerWidth: Float,
        val pickerY: Float,
        val hueY: Float,
        val alphaY: Float,
        val hexX: Float,
        val hexWidth: Float,
        val hexY: Float,
        val toggleY: Float
    )
}
