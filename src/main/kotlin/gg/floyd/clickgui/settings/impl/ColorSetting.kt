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
            hexWidth = NVGRenderer.textWidth(field, 16f, NVGRenderer.defaultFont)
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
            hexWidth = NVGRenderer.textWidth(hexString, 16f, NVGRenderer.defaultFont)
        }

        NVGRenderer.text(name, x + 6f, y + defaultHeight / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.rect(x + width - 40f, y + defaultHeight / 2f - 10f, 34f, 20f, value.rgba, 5f)
        NVGRenderer.hollowRect(x + width - 40f, y + defaultHeight / 2f - 10f, 34f, 20f, 2f, value.withAlpha(1f).darker().rgba, 5f)

        if (!extended && !expandAnim.isAnimating()) return defaultHeight

        if (expandAnim.isAnimating()) NVGRenderer.pushScissor(x, y + defaultHeight, width, getHeight() - defaultHeight)
        // The sliders edit the base color, or the fade color while in fade-edit mode.
        val target = editTarget()
        // SATURATION AND BRIGHTNESS
        NVGRenderer.gradientRect(x + 6f, y + defaultHeight + 4f, width - 12f, 169f, Colors.WHITE.rgba, target.hsbMax().rgba, Gradient.LeftToRight, 5f)
        NVGRenderer.gradientRect(x + 6f, y + defaultHeight + 4f, width - 12f, 170f, Colors.TRANSPARENT.rgba, Colors.BLACK.rgba, Gradient.TopToBottom, 5f)

        val animatedSat = mainSliderAnim.get(mainSliderPrevSat, target.saturation, false)
        val animatedBright = mainSliderAnim.get(mainSliderPrevBright, target.brightness, false)
        val sbPointer = Pair((x + 6f + animatedSat * 220), (y + 38f + (1 - animatedBright) * 170))
        NVGRenderer.dropShadow(sbPointer.first - 8.5f, sbPointer.second - 8.5f, 17f, 17f, 2.5f, 2.5f, 9f)
        NVGRenderer.circle(sbPointer.first, sbPointer.second, 8f, Colors.WHITE.rgba)
        NVGRenderer.circle(sbPointer.first, sbPointer.second, 7f, target.withAlpha(1f).rgba)

        // HUE
        NVGRenderer.image(ClickGUI.hueImage, x + 6f, y + 212f, width - 12f, 15f, 5f)
        NVGRenderer.hollowRect(x + 6f, y + 212f, width - 12f, 15f, 1f, gray38.rgba, 5f)

        val huePos = x + 6f + hueSliderAnim.get(hueSliderPrev, target.hue, false) * 219f to y + 219f
        NVGRenderer.dropShadow(huePos.first - 8.5f, huePos.second - 8.5f, 17f, 17f, 2.5f, 2.5f, 9f)
        NVGRenderer.circle(huePos.first, huePos.second, 8f, Colors.WHITE.rgba)
        NVGRenderer.circle(huePos.first, huePos.second, 7f, target.hsbMax().withAlpha(1f).rgba)

        // ALPHA
        if (allowAlpha) {
            NVGRenderer.gradientRect(x + 6f, y + 232f, width - 12f, 15f, Colors.TRANSPARENT.rgba, target.withAlpha(1f).rgba, Gradient.LeftToRight, 5f)

            val alphaPos = Pair((x + 6f + alphaSliderAnim.get(alphaSliderPrev, target.alphaFloat, false) * 217f), y + 240f)
            NVGRenderer.dropShadow(alphaPos.first - 8.5f, alphaPos.second - 8.5f, 17f, 17f, 2.5f, 2.5f, 9f)
            NVGRenderer.circle(alphaPos.first, alphaPos.second, 8f, Colors.WHITE.darker(.5f).rgba)
            NVGRenderer.circle(alphaPos.first, alphaPos.second, 7f, Colors.WHITE.rgba)
        }

        handleColorDrag(mouseX, mouseY, x, y, width)

        if (section != null) hexString = target.hex(allowAlpha)

        // main width - text input
        val sidePadding = (width - width / 2) / 2f

        val rectX = x + sidePadding
        val actualHeight = defaultHeight + if (allowAlpha) 250f else 230f

        NVGRenderer.rect(rectX, y + actualHeight - 28f, width / 2, 24f, gray38.rgba, 4f)
        NVGRenderer.hollowRect(rectX, y + actualHeight - 28f, width / 2, 24f, 2f, ClickGUIModule.clickGUIColor.rgba, 4f)

        textInputHandler.x = rectX + (width / 4) - (hexWidth / 2)
        textInputHandler.y = y + actualHeight - 26f
        textInputHandler.width = width / 2
        textInputHandler.draw(mouseX, mouseY)

        // Chroma + Fade toggles (both live inside the picker, not as sibling settings). Mutually exclusive.
        val band = width / 2
        val gap = 4f
        val halfBtn = (band - gap) / 2f
        val toggleY = y + actualHeight + 2f
        renderToggle(rectX, toggleY, halfBtn, "Chroma", value.chroma)
        renderToggle(rectX + halfBtn + gap, toggleY, halfBtn, "Fade", value.fade)

        // When fade is on, choose whether the sliders edit the base or the (chroma-less) fade color.
        if (value.fade) {
            val editLabel = if (editingFade) "Edit: Fade" else "Edit: Base"
            renderToggle(rectX, toggleY + 24f, band, editLabel, editingFade)
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

        val actualHeight = defaultHeight + if (allowAlpha) 250f else 230f
        val rectX = lastX + (width - width / 2) / 2f
        val band = width / 2
        val gap = 4f
        val halfBtn = (band - gap) / 2f
        val toggleY = lastY + actualHeight + 2f
        // Chroma toggle (left) — mutually exclusive with fade.
        if (isAreaHovered(rectX, toggleY, halfBtn, 20f, true)) {
            value.chroma = !value.chroma
            if (value.chroma) value.fade = false
            syncEditTargetState()
            return true
        }
        // Fade toggle (right) — mutually exclusive with chroma.
        if (isAreaHovered(rectX + halfBtn + gap, toggleY, halfBtn, 20f, true)) {
            value.fade = !value.fade
            if (value.fade) value.chroma = false else editingFade = false
            syncEditTargetState()
            return true
        }
        // Edit-target row (only when fade is on): switch the sliders between the base and fade color.
        if (value.fade && isAreaHovered(rectX, toggleY + 24f, band, 20f, true)) {
            editingFade = !editingFade
            syncEditTargetState()
            return true
        }

        section = when {
            isAreaHovered(lastX + 6f, lastY + 36f, width - 12f, 170f, true) -> 0 // sat & brightness
            isAreaHovered(lastX + 6f, lastY + 212f, width - 12f, 15f, true) -> 1 // hue
            isAreaHovered(lastX + 6f, lastY + 232, width - 12f, 15f, true) && allowAlpha -> 2 // alpha
            else -> null
        }

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
    private fun extrasHeight(): Float = 26f + if (value.fade) 24f else 0f

    override fun getHeight(): Float =
        expandAnim.get(defaultHeight, defaultHeight + (if (allowAlpha) 250f else 230f) + extrasHeight(), !extended)

    /** A small labelled toggle button, highlighted when [on]. */
    private fun renderToggle(bx: Float, by: Float, bw: Float, label: String, on: Boolean) {
        NVGRenderer.rect(bx, by, bw, 20f, gray38.rgba, 4f)
        NVGRenderer.hollowRect(bx, by, bw, 20f, 2f, (if (on) ClickGUIModule.clickGUIColor else Colors.gray38).rgba, 4f)
        val lw = NVGRenderer.textWidth(label, 14f, NVGRenderer.defaultFont)
        NVGRenderer.textCentered(label, bx, by, bw, 20f, 14f, Colors.WHITE.rgba, NVGRenderer.defaultFont, lw)
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

    private fun handleColorDrag(mouseX: Float, mouseY: Float, x: Float, y: Float, width: Float) {
        val target = editTarget()
        when (section) {
            0 -> { // Saturation & Brightness
                val newSaturation = ((mouseX - (x + 6f)) / (width - 12f)).coerceIn(0f, 1f)
                val newBrightness = (1f - ((mouseY - (y + 38f)) / 170f)).coerceIn(0f, 1f)

                if (newSaturation != target.saturation || newBrightness != target.brightness) {
                    mainSliderPrevSat = mainSliderAnim.get(mainSliderPrevSat, target.saturation, false)
                    mainSliderPrevBright = mainSliderAnim.get(mainSliderPrevBright, target.brightness, false)
                    mainSliderAnim.start()

                    target.saturation = newSaturation
                    target.brightness = newBrightness
                }
            }

            1 -> { // Hue
                val newHue = ((mouseX - (x + 6f)) / (width - 12f)).coerceIn(0f, 1f)
                if (newHue != target.hue) {
                    hueSliderPrev = hueSliderAnim.get(hueSliderPrev, target.hue, false)
                    hueSliderAnim.start()
                    target.hue = newHue
                }
            }

            2 -> { // Alpha
                val newAlpha = ((mouseX - (x + 6f)) / (width - 12f)).coerceIn(0f, 1f)
                if (newAlpha != target.alphaFloat) {
                    alphaSliderPrev = alphaSliderAnim.get(alphaSliderPrev, target.alphaFloat, false)
                    alphaSliderAnim.start()
                    target.alphaFloat = newAlpha
                }
            }
        }
    }
}
