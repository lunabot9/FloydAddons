package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import gg.floyd.clickgui.ClickGUI.gray38
import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.Color
import gg.floyd.utils.Color.Companion.brighter
import gg.floyd.utils.Colors
import gg.floyd.utils.font.FontEpochCache
import gg.floyd.utils.ui.HoverHandler
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

    private val elementWidths = FontEpochCache { options.map { NVGRenderer.textWidth(it, 16f, NVGRenderer.defaultFont) } }
    private val settingAnim = EaseInOutAnimation(200)
    private val hover = HoverHandler(150)
    private val defaultHeight = Panel.HEIGHT
    private val maxVisibleOptions = 8
    private val searchHeight = 24f
    private var extended = false
    private var scrollIndex = 0
    private var searchText = ""
    private val search = TextInputHandler(
        textProvider = { searchText },
        textSetter = {
            searchText = it
            scrollIndex = 0
        }
    )

    private val color: Color get() = gray38.brighter(1 + hover.percent() / 500f)

    private fun isSettingHovered(index: Int): Boolean =
        isAreaHovered(lastX, lastY + optionsStartOffset() + 32f * index, width, 32f, true)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        description = descriptionForHover(x, y, mouseX, mouseY)
        super.render(x, y, mouseX, mouseY)

        val widths = elementWidths.get()
        val currentWidth = widths[index]

        hover.handle(x + width - 20f - currentWidth, y + defaultHeight / 2f - 10f, currentWidth + 12f, 22f, true)
        NVGRenderer.rect(x + width - 20f - currentWidth, y + defaultHeight / 2f - 10f, currentWidth + 12f, 20f, color.rgba, 5f)
        NVGRenderer.hollowRect(x + width - 20f - currentWidth, y + defaultHeight / 2f - 10f, currentWidth + 12f, 20f, 1.5f, ClickGUIModule.clickGUIColor.rgba, 5f)

        NVGRenderer.text(name, x + 6f, y + defaultHeight / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.text(selected, x + width - 14f - currentWidth, y + defaultHeight / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        if (!extended && !settingAnim.isAnimating()) return defaultHeight

        val displayHeight = getHeight()
        if (settingAnim.isAnimating()) NVGRenderer.pushScissor(x, y, width, displayHeight)

        val visibleOptions = visibleOptions()
        val visibleCount = visibleOptions.size
        val dropdownHeight = searchOffset() + visibleCount * 32f
        NVGRenderer.rect(x + 6, y + 37f, width - 12f, dropdownHeight, gray38.rgba, 5f)

        if (searchEnabled()) {
            val searchY = y + 40f
            NVGRenderer.rect(x + 10f, searchY, width - 20f, searchHeight - 6f, Colors.gray38.rgba, 4f)
            NVGRenderer.hollowRect(x + 10f, searchY, width - 20f, searchHeight - 6f, 1.2f, ClickGUIModule.clickGUIColor.rgba, 4f)
            if (searchText.isEmpty())
                NVGRenderer.text("Search mobs…", x + 14f, searchY + 2f, 14f, Colors.MINECRAFT_GRAY.rgba, NVGRenderer.defaultFont)
            search.x = x + 14f
            search.y = searchY + 1f
            search.width = width - 28f
            search.height = searchHeight - 8f
            search.draw(mouseX, mouseY)
        }

        for (i in 0 until visibleCount) {
            val option = visibleOptions[i]
            val optionIndex = options.indexOf(option)
            val optionY = y + optionsStartOffset() + 32 * i
            if (i != visibleCount - 1) NVGRenderer.line(x + 18f, optionY + 32, x + width - 12f, optionY + 32, 1.5f, Colors.MINECRAFT_DARK_GRAY.rgba)
            NVGRenderer.textCentered(option, x + 6f, optionY, width - 12f, 32f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont, widths[optionIndex])
            if (isSettingHovered(i)) NVGRenderer.hollowRect(x + 6, optionY, width - 12f, 32f, 1.5f, ClickGUIModule.clickGUIColor.rgba, 4f)
        }
        if (settingAnim.isAnimating()) NVGRenderer.popScissor()

        return displayHeight
    }

    private fun descriptionForHover(x: Float, y: Float, mouseX: Float, mouseY: Float): String {
        if (extended && mouseX >= x + 6f && mouseX <= x + width - 6f) {
            val visibleIndex = ((mouseY - (y + optionsStartOffset())) / 32f).toInt()
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
        if (!extended || !isAreaHovered(lastX, lastY + optionsStartOffset(), width, visibleOptionCount() * 32f, true)) return false
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
        settingAnim.get(defaultHeight, visibleOptionCount() * 32f + searchOffset() + 44, !extended)

    private fun searchEnabled(): Boolean = options.size > maxVisibleOptions

    private fun searchOffset(): Float = if (searchEnabled()) searchHeight else 0f

    private fun optionsStartOffset(): Float = 38f + searchOffset()

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
}
