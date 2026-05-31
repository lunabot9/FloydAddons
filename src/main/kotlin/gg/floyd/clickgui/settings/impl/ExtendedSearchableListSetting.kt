package gg.floyd.clickgui.settings.impl

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.Panel
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.Color
import gg.floyd.utils.Color.Companion.withAlpha
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.TextInputHandler
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.Image
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.max

/**
 * A richer [SearchableListSetting]: it adds, per row, a 16px icon (gray placeholder reserved
 * when none is available), a friendly display name in place of the raw id, a filled/empty
 * checkbox for the multi-select affordance, and an optional color swatch.
 *
 * Interactions over the base's left-click toggle:
 *  - right-click a row -> opens an INLINE hex color editor row (no separate modal); typing a
 *    full 6-digit hex applies it through [onColorChange].
 *  - Alt + left-click a row -> [onAltClick] (the "add looked-at" equivalent).
 *
 * When [showActionsRow] is set, a fixed bar is drawn BELOW the scroll viewport (outside the
 * scrollable area, so a scroll-over-button cannot fire a click) with "Pick from World" /
 * "Clear All", wired to [onAltClick] (for the looked-at target) and [onClearAll].
 *
 * Everything else (expand/collapse animation, search box + filtering, scroll-wheel consumption,
 * scissor clipping and the scrollbar) is inherited unchanged from [SearchableListSetting].
 */
class ExtendedSearchableListSetting(
    name: String,
    optionsProvider: () -> List<String>,
    selectedProvider: () -> Set<String>,
    onToggle: (String) -> Unit,
    desc: String,
    private val displayNameProvider: ((String) -> String)? = null,
    private val iconProvider: ((String) -> Image?)? = null,
    private val colorProvider: ((String) -> String?)? = null,
    private val onColorChange: ((String, String) -> Unit)? = null,
    private val onAltClick: ((String) -> Unit)? = null,
    private val onClearAll: (() -> Unit)? = null,
    private val showActionsRow: Boolean = false,
) : SearchableListSetting(name, optionsProvider, selectedProvider, onToggle, desc) {

    private val actionsH = 22f

    // The id whose inline color editor is open, or null when none is. The editor row renders
    // directly under the viewport (and above the actions bar).
    private var colorEditId: String? = null
    private var colorText = ""
    private val colorInput = TextInputHandler(textProvider = { colorText }, textSetter = { colorText = it.filter { c -> c in HEX_CHARS }.take(6) })

    override fun fullHeight(): Float =
        headerH + searchH + viewportH + pad +
            (if (colorEditId != null) rowH + 4f else 0f) +
            (if (showActionsRow) actionsH + 4f else 0f)

    /**
     * Like the base filter, but with currently-selected options stably hoisted to the TOP:
     * the selected block keeps its existing relative order, followed by the unselected block in
     * theirs. This is the single ordering chokepoint shared by both the render loop and the
     * click hit-testing (which indexes into this same list), so rows and clicks stay aligned.
     */
    override fun matching(all: List<String>): List<String> {
        val filtered = super.matching(all)
        val selected = selectedProvider()
        if (selected.isEmpty()) return filtered
        // Two stable passes: `filter` preserves input order within each block.
        return filtered.filter { it in selected } + filtered.filter { it !in selected }
    }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        // Shared hover/description + lastX/lastY bookkeeping (the base's render body is fully
        // replaced below so we can add icons, friendly names, checkbox, swatch, inline color
        // editor and the actions bar).
        renderBase(x, y)

        val selected = selectedProvider()
        NVGRenderer.text(name, x + 6f, y + headerH / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        val countLabel = "${selected.size} selected"
        val countWidth = NVGRenderer.textWidth(countLabel, 14f, NVGRenderer.defaultFont)
        NVGRenderer.text(countLabel, x + width - 6f - countWidth, y + headerH / 2f - 7f, 14f, ClickGUIModule.clickGUIColor.rgba, NVGRenderer.defaultFont)

        if (!extended && !expandAnim.isAnimating()) return headerH

        if (expandAnim.isAnimating()) NVGRenderer.pushScissor(x, y + headerH, width, getHeight() - headerH)

        // Search box.
        val searchY = y + headerH + 2f
        NVGRenderer.rect(x + 6f, searchY, width - 12f, searchH - 4f, Colors.gray38.rgba, 4f)
        NVGRenderer.hollowRect(x + 6f, searchY, width - 12f, searchH - 4f, 1.5f, ClickGUIModule.clickGUIColor.rgba, 4f)
        if (searchText.isEmpty()) NVGRenderer.text("Search…", x + 10f, searchY + 3f, 14f, Colors.MINECRAFT_GRAY.rgba, NVGRenderer.defaultFont)
        search.x = x + 10f
        search.y = searchY + 2f
        search.width = width - 20f
        search.height = searchH - 8f
        search.draw(mouseX, mouseY)

        // Viewport.
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
            if (rowY + rowH >= viewportY && rowY <= viewportY + viewportH) drawRow(x, rowY, option, option in selected)
            rowY += rowH
        }
        NVGRenderer.popScissor()

        // Scrollbar.
        if (contentH > viewportH) {
            val trackX = x + width - 5f
            val maxScroll = contentH - viewportH
            val thumbH = (viewportH * (viewportH / contentH)).coerceAtLeast(12f)
            val thumbY = viewportY + (scrollOffset / maxScroll) * (viewportH - thumbH)
            NVGRenderer.rect(trackX, viewportY, 3f, viewportH, Colors.gray38.rgba, 2f)
            NVGRenderer.rect(trackX, thumbY, 3f, thumbH, ClickGUIModule.clickGUIColor.rgba, 2f)
        }

        var belowY = viewportY + viewportH

        // Inline color editor (between viewport and actions bar). No modal.
        colorEditId?.let { id ->
            val editY = belowY + 2f
            NVGRenderer.rect(x + 6f, editY, width - 12f, rowH, Colors.gray38.rgba, 4f)
            NVGRenderer.hollowRect(x + 6f, editY, width - 12f, rowH, 1.5f, ClickGUIModule.clickGUIColor.rgba, 4f)
            // Live preview swatch of whatever is currently typed.
            val preview = if (colorText.length == 6) runCatching { Color("${colorText}FF") }.getOrNull() else null
            NVGRenderer.rect(x + 10f, editY + 4f, 10f, 10f, (preview ?: Colors.gray38).rgba, 2f)
            colorInput.x = x + 26f
            colorInput.y = editY + 2f
            colorInput.width = width - 40f
            colorInput.height = rowH - 4f
            colorInput.draw(mouseX, mouseY)
            belowY = editY + rowH
        }

        // Actions bar, fixed below the scroll area (outside it, so scrolling over a button cannot
        // fire a click). Only the wired actions are drawn.
        if (showActionsRow) {
            for ((rect, label) in actionButtons(x, belowY + 2f)) drawActionButton(rect.x, rect.y, rect.w, label)
        }

        if (expandAnim.isAnimating()) NVGRenderer.popScissor()
        return getHeight()
    }

    private data class ButtonRect(val x: Float, val y: Float, val w: Float)

    /** The (rect, label) of every wired actions-bar button, laid out left-to-right at [barY]. */
    private fun actionButtons(x: Float, barY: Float): List<Pair<ButtonRect, String>> {
        val labels = buildList {
            if (onAltClick != null) add("Pick from World")
            if (onClearAll != null) add("Clear All")
        }
        if (labels.isEmpty()) return emptyList()
        val gap = 4f
        val w = (width - 12f - gap * (labels.size - 1)) / labels.size
        return labels.mapIndexed { i, label -> ButtonRect(x + 6f + i * (w + gap), barY, w) to label }
    }

    private fun drawRow(x: Float, rowY: Float, id: String, rowSelected: Boolean) {
        if (rowSelected) NVGRenderer.rect(x + 6f, rowY, width - 12f, rowH, ClickGUIModule.clickGUIColor.withAlpha(0.3f).rgba, 3f)
        if (isAreaHovered(x + 6f, rowY, width - 18f, rowH, true)) NVGRenderer.hollowRect(x + 6f, rowY, width - 12f, rowH, 1f, ClickGUIModule.clickGUIColor.rgba, 3f)

        // Icon box (always reserved; gray placeholder when no image is available).
        val iconX = x + 10f
        val iconY = rowY + 1f
        val image = iconProvider?.invoke(id)
        if (image != null) NVGRenderer.image(image, iconX, iconY, ICON, ICON, 2f)
        else NVGRenderer.rect(iconX, iconY, ICON, ICON, Colors.gray38.rgba, 2f)

        // Friendly display name.
        val label = displayNameProvider?.invoke(id) ?: id
        NVGRenderer.text(label, iconX + ICON + 6f, rowY + 3f, 14f, if (rowSelected) Colors.WHITE.rgba else Colors.MINECRAFT_GRAY.rgba, NVGRenderer.defaultFont)

        // Trailing widgets: checkbox at the far right, color swatch just left of it when available.
        val checkX = x + width - 16f - 8f
        val checkY = rowY + (rowH - CHECK) / 2f
        NVGRenderer.hollowRect(checkX, checkY, CHECK, CHECK, 1.5f, ClickGUIModule.clickGUIColor.rgba, 2f)
        if (rowSelected) NVGRenderer.rect(checkX + 2f, checkY + 2f, CHECK - 4f, CHECK - 4f, ClickGUIModule.clickGUIColor.rgba, 1f)

        if (colorProvider != null) {
            val swatch = colorProvider.invoke(id)?.let { hex -> runCatching { Color("${hex.removePrefix("#").take(6).padEnd(6, '0')}FF") }.getOrNull() }
            val swatchX = checkX - SWATCH - 6f
            val swatchY = rowY + (rowH - SWATCH) / 2f
            NVGRenderer.rect(swatchX, swatchY, SWATCH, SWATCH, (swatch ?: Colors.gray38).rgba, 2f)
        }
    }

    private fun drawActionButton(x: Float, y: Float, w: Float, label: String) {
        val hovered = isAreaHovered(x, y, w, actionsH, true)
        NVGRenderer.rect(x, y, w, actionsH, Colors.gray38.rgba, 4f)
        NVGRenderer.hollowRect(x, y, w, actionsH, 1.5f, (if (hovered) ClickGUIModule.clickGUIColor else Colors.gray38).rgba, 4f)
        val lw = NVGRenderer.textWidth(label, 14f, NVGRenderer.defaultFont)
        NVGRenderer.text(label, x + (w - lw) / 2f, y + actionsH / 2f - 7f, 14f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (isHovered) {
            expandAnim.start()
            extended = !extended
            return true
        }
        if (!extended) return false
        if (colorEditId != null && colorInput.mouseClicked(mouseX, mouseY, click)) return true
        if (search.mouseClicked(mouseX, mouseY, click)) return true

        val rows = matching(optionsProvider())
        val viewportY = lastY + headerH + searchH
        val contentH = rows.size * rowH

        // Scrollbar drag.
        if (contentH > viewportH && isAreaHovered(lastX + width - 6f, viewportY, 6f, viewportH, true)) {
            draggingScrollbar = true
            return true
        }

        // Row interactions (left-click toggle / Alt+left-click looked-at / right-click color editor).
        if (isAreaHovered(lastX + 6f, viewportY, width - 18f, viewportH, true)) {
            val index = ((mouseY - viewportY + scrollOffset) / rowH).toInt()
            val id = rows.getOrNull(index)
            if (id != null) {
                when {
                    click.button() == 1 -> {
                        openColorEditor(id)
                        return true
                    }
                    click.button() == 0 && isAltDown() -> {
                        onAltClick?.invoke(id)
                        return true
                    }
                    click.button() == 0 -> {
                        onToggle(id)
                        return true
                    }
                }
            }
        }

        var belowY = viewportY + viewportH

        // Inline color editor clicks already consumed above via colorInput; advance past its row.
        if (colorEditId != null) belowY += 2f + rowH

        // Actions bar (below the viewport, outside the scroll area).
        if (showActionsRow && click.button() == 0) {
            for ((rect, label) in actionButtons(lastX, belowY + 2f)) {
                if (isAreaHovered(rect.x, rect.y, rect.w, actionsH, true)) {
                    when (label) {
                        "Pick from World" -> onAltClick?.invoke("")
                        "Clear All" -> onClearAll?.invoke()
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun openColorEditor(id: String) {
        colorEditId = id
        colorText = colorProvider?.invoke(id)?.removePrefix("#")?.take(6)?.filter { it in HEX_CHARS } ?: ""
        expandAnim.start()
    }

    override fun mouseReleased(click: MouseButtonEvent) {
        super.mouseReleased(click)
        colorInput.mouseReleased()
    }

    override fun keyTyped(input: CharacterEvent): Boolean {
        if (!extended) return false
        if (colorEditId != null && colorInput.keyTyped(input)) {
            applyColorIfComplete()
            return true
        }
        return super.keyTyped(input)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (!extended) return false
        if (colorEditId != null && colorInput.keyPressed(input)) {
            applyColorIfComplete()
            return true
        }
        return super.keyPressed(input)
    }

    private fun applyColorIfComplete() {
        val id = colorEditId ?: return
        if (colorText.length == 6) onColorChange?.invoke(id, colorText)
    }

    private fun isAltDown(): Boolean {
        val handle = mc.window.handle()
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
    }

    private companion object {
        const val ICON = 16f
        const val CHECK = 12f
        const val SWATCH = 8f
        const val HEX_CHARS = "0123456789abcdefABCDEF"
    }
}
