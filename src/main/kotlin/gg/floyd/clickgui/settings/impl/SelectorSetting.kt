package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
import gg.floyd.utils.Color
import gg.floyd.utils.Colors
import gg.floyd.utils.font.FontEpochCache
import gg.floyd.utils.ui.TextInputHandler
import gg.floyd.utils.ui.animations.EaseInOutAnimation
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

class SelectorSetting(
    name: String,
    default: String,
    private var options: List<String>,
    desc: String,
    private val optionDescriptions: Map<String, String> = emptyMap()
) : RenderableSetting<Int>(name, desc), Saving {
    private companion object {
        const val TEXT_SIZE = 10f
        const val SEARCH_TEXT_SIZE = 9f
        const val VALUE_TEXT_MIN = 7f
    }

    private val baseDescription = desc

    override val default: Int = optionIndex(default)

    override var value: Int
        get() = index
        set(value) {
            index = value
        }

    private var index: Int = optionIndex(default)
        set(value) {
            field = if (value > options.size - 1) 0 else if (value < 0) options.size - 1 else value
        }

    private var selected: String
        get() = options[index]
        set(value) {
            index = optionIndex(value)
        }

    private val elementWidths = FontEpochCache { options.map { NVGRenderer.textWidth(it, TEXT_SIZE, NVGRenderer.defaultFont) } }
    private val settingAnim = EaseInOutAnimation(200)
    private val defaultHeight = Panel.HEIGHT
    private val maxVisibleOptions = 8
    private val searchHeight = 18f
    private var extended = false
    private var scrollIndex = 0
    private var searchText = ""
    private val search = TextInputHandler(
        textProvider = { searchText },
        textSetter = {
            searchText = it
            scrollIndex = 0
        }
    ).apply {
        fontSizeOverride = SEARCH_TEXT_SIZE
        textPaddingX = 4f
        textPaddingY = 4f
    }

    private fun isSettingHovered(index: Int): Boolean =
        isAreaHovered(lastX, lastY + optionsStartOffset() + Panel.HEIGHT * index, width, Panel.HEIGHT, true)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        description = descriptionForHover(x, y, mouseX, mouseY)
        super.render(x, y, mouseX, mouseY)

        val widths = elementWidths.get()
        val maxValueWidth = (width * 0.44f).coerceAtLeast(18f)
        val selectedTextSize = fittedValueTextSize(selected, maxValueWidth)
        val currentWidth = NVGRenderer.textWidth(selected, selectedTextSize, NVGRenderer.defaultFont)
        val labelMaxWidth = (width - 4f - 4f - currentWidth - 6f).coerceAtLeast(18f)
        val labelSize = fitTextToWidth(name, labelMaxWidth, TEXT_SIZE, 6f)

        NVGRenderer.rect(x, y, width, defaultHeight, ClickGUI.settingBackground())
        NVGRenderer.text(name, x + 4f, y + (defaultHeight - labelSize) / 2f, labelSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.text(selected, x + width - 6f - currentWidth, y + (defaultHeight - selectedTextSize) / 2f, selectedTextSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        if (!extended && !settingAnim.isAnimating()) return defaultHeight

        val displayHeight = getHeight()
        if (settingAnim.isAnimating()) NVGRenderer.pushScissor(x, y, width, displayHeight)

        val visibleOptions = visibleOptions()
        val visibleCount = visibleOptions.size
        val dropdownHeight = searchOffset() + visibleCount * Panel.HEIGHT
        NVGRenderer.rect(x, y + defaultHeight, width, dropdownHeight, ClickGUI.bodyBackground())

        if (searchEnabled()) {
            val searchY = y + defaultHeight + 2f
            NVGRenderer.rect(x + 4f, searchY, width - 8f, searchHeight, ClickGUI.settingBackground())
            if (searchText.isEmpty() && !search.isListening)
                NVGRenderer.text("Search...", x + 8f, searchY + 4f, SEARCH_TEXT_SIZE, Colors.MINECRAFT_GRAY.rgba, NVGRenderer.defaultFont)
            search.x = x + 4f
            search.y = searchY
            search.width = width - 8f
            search.height = searchHeight
            search.draw(mouseX, mouseY)
        }

        for (i in 0 until visibleCount) {
            val option = visibleOptions[i]
            val optionIndex = options.indexOf(option)
            val optionY = y + optionsStartOffset() + Panel.HEIGHT * i
            val selectedFill = if (optionIndex == index) ClickGUI.accentDark() else ClickGUI.settingBackground()
            NVGRenderer.rect(x, optionY, width, Panel.HEIGHT, selectedFill)
            NVGRenderer.textCentered(option, x, optionY, width, Panel.HEIGHT, TEXT_SIZE, Colors.WHITE.rgba, NVGRenderer.defaultFont, widths[optionIndex])
            if (isSettingHovered(i)) {
                NVGRenderer.hollowRect(x + 0.5f, optionY + 0.5f, width - 1f, Panel.HEIGHT - 1f, 1f, ClickGUI.accent(), 0f)
            }
        }
        if (settingAnim.isAnimating()) NVGRenderer.popScissor()

        return displayHeight
    }

    private fun descriptionForHover(x: Float, y: Float, mouseX: Float, mouseY: Float): String {
        if (extended && mouseX >= x + 6f && mouseX <= x + width - 6f) {
            val visibleIndex = ((mouseY - (y + optionsStartOffset())) / Panel.HEIGHT).toInt()
            val visibleOptions = visibleOptions()
            if (visibleIndex in visibleOptions.indices && mouseY >= y + optionsStartOffset())
                return optionDescriptions[visibleOptions[visibleIndex]] ?: baseDescription
        }

        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + defaultHeight)
            return optionDescriptions[selected] ?: baseDescription

        return baseDescription
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() == 0) {
            if (isHovered) {
                settingAnim.start()
                extended = !extended
                return true
            }

            if (!extended) return false
            if (searchEnabled() && search.mouseClicked(mouseX, mouseY, click)) return true

            val visibleOptions = visibleOptions()
            for (visibleIndex in visibleOptions.indices) {
                if (isSettingHovered(visibleIndex)) {
                    settingAnim.start()
                    selected = visibleOptions[visibleIndex]
                    extended = false
                    return true
                }
            }
        } else if (click.button() == 1) {
            if (isHovered) {
                index++
                return true
            }
        }
        return false
    }

    override fun mouseScrolled(amount: Int): Boolean {
        if (!extended || !isAreaHovered(lastX, lastY + optionsStartOffset(), width, visibleOptionCount() * Panel.HEIGHT, true)) return false
        val direction = when {
            amount > 0 -> -1
            amount < 0 -> 1
            else -> 0
        }
        scrollIndex = (scrollIndex + direction).coerceIn(0, (matchingOptions().size - visibleOptionCount()).coerceAtLeast(0))
        return direction != 0
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        search.mouseReleased()
    }

    override fun keyTyped(input: CharacterEvent): Boolean = extended && searchEnabled() && search.keyTyped(input)

    override fun keyPressed(input: KeyEvent): Boolean = extended && searchEnabled() && search.keyPressed(input)

    private fun optionIndex(string: String): Int =
        options.map { it.lowercase() }.indexOf(string.lowercase()).coerceIn(0, options.size - 1)

    override val isHovered: Boolean get() = isAreaHovered(lastX, lastY, width, defaultHeight, true)

    override fun getHeight(): Float =
        settingAnim.get(defaultHeight, defaultHeight + visibleOptionCount() * Panel.HEIGHT + searchOffset(), !extended)

    private fun searchEnabled(): Boolean = options.size > maxVisibleOptions

    private fun searchOffset(): Float = if (searchEnabled()) searchHeight + 4f else 0f

    private fun optionsStartOffset(): Float = defaultHeight + searchOffset()

    private fun matchingOptions(): List<String> =
        if (searchText.isBlank()) options else options.filter { it.contains(searchText, ignoreCase = true) }

    private fun visibleOptionCount(): Int = matchingOptions().size.coerceAtMost(maxVisibleOptions)

    private fun visibleOptions(): List<String> = matchingOptions().drop(scrollIndex).take(maxVisibleOptions)

    override fun write(gson: Gson): JsonElement = JsonPrimitive(selected)

    override fun read(element: JsonElement, gson: Gson) {
        element.asString?.let { selected = it }
    }

    fun selectedOption(): String = selected

    fun optionLabels(): List<String> = options.toList()

    private fun fittedValueTextSize(text: String, maxWidth: Float): Float {
        var size = TEXT_SIZE
        while (size > VALUE_TEXT_MIN && NVGRenderer.textWidth(text, size, NVGRenderer.defaultFont) > maxWidth) {
            size -= 0.5f
        }
        return size.coerceAtLeast(VALUE_TEXT_MIN)
    }
}
