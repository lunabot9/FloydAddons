package gg.floyd.clickgui.settings.impl

import gg.floyd.clickgui.Panel
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.Color.Companion.withAlpha
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.TextInputHandler
import gg.floyd.utils.ui.animations.EaseInOutAnimation
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.max

/**
 * A searchable, scrollable multi-select list. It is a pure VIEW over an external
 * collection: [optionsProvider] supplies all candidates (e.g. every vanilla mob/block id),
 * [isSelected] reports membership, and [onToggle] adds/removes. Selection state therefore
 * lives in (and is persisted by) the owning module, not this widget.
 *
 * Click the header to expand. Type to filter. Mouse-wheel or drag the scrollbar to scroll.
 */
open class SearchableListSetting(
    name: String,
    protected val optionsProvider: () -> List<String>,
    protected val selectedProvider: () -> Set<String>,
    protected val onToggle: (String) -> Unit,
    desc: String
) : RenderableSetting<Unit>(name, desc) {

    override val default: Unit = Unit
    override var value: Unit = Unit

    protected val expandAnim = EaseInOutAnimation(200)
    protected var extended = false

    protected var searchText = ""
    protected val search = TextInputHandler(textProvider = { searchText }, textSetter = { searchText = it })

    protected var scrollOffset = 0f
    protected var draggingScrollbar = false

    protected val headerH = Panel.HEIGHT
    protected val searchH = 24f
    protected val rowH = 18f
    protected val maxRows = 7
    protected val viewportH = maxRows * rowH
    protected val pad = 6f

    protected open fun fullHeight(): Float = headerH + searchH + viewportH + pad

    override fun getHeight(): Float = expandAnim.get(headerH, fullHeight(), !extended)

    override val isHovered get() = isAreaHovered(lastX, lastY, width, headerH, true)

    protected fun matching(all: List<String>): List<String> =
        if (searchText.isBlank()) all else all.filter { it.contains(searchText, true) }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)

        // Snapshot the selected set once per frame (callers' set is cheap; avoids O(n^2) membership rebuilds).
        val selected = selectedProvider()
        NVGRenderer.text(name, x + 6f, y + headerH / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        val countLabel = "${selected.size} selected"
        val countWidth = NVGRenderer.textWidth(countLabel, 14f, NVGRenderer.defaultFont)
        NVGRenderer.text(countLabel, x + width - 6f - countWidth, y + headerH / 2f - 7f, 14f, ClickGUIModule.clickGUIColor.rgba, NVGRenderer.defaultFont)

        // Collapsed: only the header (+count) is drawn; the full option list is never built.
        if (!extended && !expandAnim.isAnimating()) return headerH

        if (expandAnim.isAnimating()) NVGRenderer.pushScissor(x, y + headerH, width, getHeight() - headerH)

        // Search box
        val searchY = y + headerH + 2f
        NVGRenderer.rect(x + 6f, searchY, width - 12f, searchH - 4f, Colors.gray38.rgba, 4f)
        NVGRenderer.hollowRect(x + 6f, searchY, width - 12f, searchH - 4f, 1.5f, ClickGUIModule.clickGUIColor.rgba, 4f)
        if (searchText.isEmpty()) NVGRenderer.text("Search…", x + 10f, searchY + 3f, 14f, Colors.MINECRAFT_GRAY.rgba, NVGRenderer.defaultFont)
        search.x = x + 10f
        search.y = searchY + 2f
        search.width = width - 20f
        search.height = searchH - 8f
        search.draw(mouseX, mouseY)

        // Viewport (option list built only while expanded)
        val viewportY = y + headerH + searchH
        val rows = matching(optionsProvider())
        val contentH = rows.size * rowH
        scrollOffset = scrollOffset.coerceIn(0f, max(0f, contentH - viewportH))

        if (draggingScrollbar && contentH > viewportH) {
            val rel = ((mouseY - viewportY) / viewportH).coerceIn(0f, 1f)
            scrollOffset = rel * (contentH - viewportH)
        }

        NVGRenderer.pushScissor(x + 6f, viewportY, width - 12f, viewportH)
        var rowY = viewportY - scrollOffset
        for (option in rows) {
            if (rowY + rowH >= viewportY && rowY <= viewportY + viewportH) {
                val rowSelected = option in selected
                if (rowSelected) NVGRenderer.rect(x + 6f, rowY, width - 12f, rowH, ClickGUIModule.clickGUIColor.withAlpha(0.3f).rgba, 3f)
                if (isAreaHovered(x + 6f, rowY, width - 18f, rowH, true)) NVGRenderer.hollowRect(x + 6f, rowY, width - 12f, rowH, 1f, ClickGUIModule.clickGUIColor.rgba, 3f)
                NVGRenderer.text(option, x + 12f, rowY + 3f, 14f, if (rowSelected) Colors.WHITE.rgba else Colors.MINECRAFT_GRAY.rgba, NVGRenderer.defaultFont)
            }
            rowY += rowH
        }
        NVGRenderer.popScissor()

        // Scrollbar
        if (contentH > viewportH) {
            val trackX = x + width - 5f
            val maxScroll = contentH - viewportH
            val thumbH = (viewportH * (viewportH / contentH)).coerceAtLeast(12f)
            val thumbY = viewportY + (scrollOffset / maxScroll) * (viewportH - thumbH)
            NVGRenderer.rect(trackX, viewportY, 3f, viewportH, Colors.gray38.rgba, 2f)
            NVGRenderer.rect(trackX, thumbY, 3f, thumbH, ClickGUIModule.clickGUIColor.rgba, 2f)
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
        if (search.mouseClicked(mouseX, mouseY, click)) return true

        val all = optionsProvider()
        val rows = matching(all)
        val viewportY = lastY + headerH + searchH
        val contentH = rows.size * rowH

        // Scrollbar drag.
        if (contentH > viewportH && isAreaHovered(lastX + width - 6f, viewportY, 6f, viewportH, true)) {
            draggingScrollbar = true
            return true
        }
        // Row toggle.
        if (click.button() == 0 && isAreaHovered(lastX + 6f, viewportY, width - 18f, viewportH, true)) {
            val index = ((mouseY - viewportY + scrollOffset) / rowH).toInt()
            rows.getOrNull(index)?.let { onToggle(it); return true }
        }
        return false
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        draggingScrollbar = false
        search.mouseReleased()
    }

    override fun mouseScrolled(amount: Int): Boolean {
        if (!extended) return false
        if (!isAreaHovered(lastX, lastY + headerH, width, getHeight() - headerH, true)) return false
        val contentH = matching(optionsProvider()).size * rowH
        if (contentH <= viewportH) return false
        scrollOffset = (scrollOffset - amount).coerceIn(0f, contentH - viewportH)
        return true
    }

    override fun keyTyped(input: CharacterEvent): Boolean {
        if (!extended) return false
        return search.keyTyped(input)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (!extended) return false
        return search.keyPressed(input)
    }
}
