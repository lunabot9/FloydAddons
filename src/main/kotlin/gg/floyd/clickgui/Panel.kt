package gg.floyd.clickgui

import gg.floyd.clickgui.settings.ModuleButton
import gg.floyd.features.Category
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.Colors
import gg.floyd.utils.font.FontEpochCache
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

/**
 * Renders all the panels.
 *
 * Backend made by Aton, with some changes
 * Design mostly made by Stivais
 *
 * @author Stivais, Aton
 * @see [ModuleButton]
 */
class Panel(private val category: Category) {

    val panelSetting = ClickGUIModule.panelSetting[category.name] ?: throw IllegalStateException("Panel setting for category $category is not initialized")
    private val unsortedModuleButtons = ModuleManager.modulesByCategory[category]
        ?.filter { it.visibleInGui }
        ?.map { ModuleButton(it, this@Panel) } ?: listOf()

    // Width-derived state re-measures when the font epoch changes (mid-session font reloads move
    // every advance); the buttons themselves stay session-lived, only the order is recomputed.
    private val sortedModuleButtons = FontEpochCache {
        unsortedModuleButtons.sortedByDescending { NVGRenderer.textWidth(it.module.name, 9.5f, NVGRenderer.defaultFont) }
    }
    val moduleButtons: List<ModuleButton> get() = sortedModuleButtons.get()
    private val visibleModuleButtons: List<ModuleButton>
        get() {
            val search = SearchBar.currentSearch
            return if (search.isBlank()) moduleButtons else moduleButtons.filter { it.module.name.contains(search, true) }
        }
    private val lastModuleButton get() = moduleButtons.lastOrNull()

    private var previousHeight = 0f
    private var scrollOffset = 0f
    var dragging = false
        private set
    private var deltaX = 0f
    private var deltaY = 0f

    fun draw(mouseX: Float, mouseY: Float) {
        if (dragging) {
            panelSetting.x = deltaX + mouseX
            panelSetting.y = deltaY + mouseY
        }

        val panelBottom = previousHeight.coerceAtLeast(HEADER_HEIGHT + FOOTER_HEIGHT)
        if (panelSetting.extended) {
            NVGRenderer.rect(panelSetting.x - 1f, panelSetting.y + panelBottom, WIDTH + 2f, FOOTER_HEIGHT, ClickGUI.accentDark())
        }
        NVGRenderer.rect(panelSetting.x, panelSetting.y + 3f, WIDTH, HEADER_HEIGHT, ClickGUI.accent())
        for (offset in 1..3) {
            NVGRenderer.rect(panelSetting.x, panelSetting.y + offset, WIDTH, HEADER_HEIGHT, ClickGUI.shadowStripeColor())
        }
        NVGRenderer.rect(panelSetting.x - 1f, panelSetting.y, WIDTH + 2f, HEADER_HEIGHT, ClickGUI.accentDark())
        NVGRenderer.textCentered(
            category.name,
            panelSetting.x,
            panelSetting.y,
            WIDTH,
            HEADER_HEIGHT,
            11f,
            Colors.WHITE.rgba,
            NVGRenderer.defaultFont,
            NVGRenderer.textWidth(category.name, 11f, NVGRenderer.defaultFont),
        )

        if (scrollOffset != 0f) NVGRenderer.pushScissor(
            panelSetting.x,
            panelSetting.y + HEADER_HEIGHT + 3f,
            WIDTH,
            previousHeight - HEADER_HEIGHT
        )

        var startY = scrollOffset + HEADER_HEIGHT + 3f
        if (panelSetting.extended) {
            val visibleButtons = visibleModuleButtons
            val lastVisibleButton = visibleButtons.lastOrNull()
            for (button in visibleButtons) {
                startY += button.draw(panelSetting.x, startY + panelSetting.y, mouseX, mouseY, button == lastVisibleButton)
            }
        }
        previousHeight = startY

        if (scrollOffset != 0f) NVGRenderer.popScissor()
    }

    fun handleScroll(amount: Int): Boolean {
        if (!isMouseOverExtended) return false
        // Offer the scroll to a hovered setting (e.g. a SearchableListSetting) before scrolling the panel.
        if (panelSetting.extended) {
            for (button in visibleModuleButtons) {
                if (button.mouseScrolled(amount)) return true
            }
        }
        scrollOffset = (scrollOffset + amount).coerceIn((-previousHeight + scrollOffset + HEADER_HEIGHT + 24f).coerceAtMost(0f), 0f)
        return true
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (isAreaHovered(panelSetting.x, panelSetting.y, WIDTH, HEADER_HEIGHT, true)) {
            if (click.button() == 0) {
                deltaX = (panelSetting.x - mouseX)
                deltaY = (panelSetting.y - mouseY)
                dragging = true
                return true
            } else if (click.button() == 1) {
                panelSetting.extended = !panelSetting.extended
                return true
            }
        } else if (isMouseOverExtended) {
            return visibleModuleButtons.reversed().any {
                it.mouseClicked(mouseX, mouseY, click)
            }
        }
        return false
    }

    fun mouseReleased(click: MouseButtonEvent) {
        dragging = false

        if (panelSetting.extended)
            visibleModuleButtons.reversed().forEach {
                it.mouseReleased(click)
            }
    }

    fun keyTyped(input: CharacterEvent): Boolean {
        if (!panelSetting.extended) return false

        return moduleButtons.reversed().any { it.keyTyped(input) }
    }

    fun keyPressed(input: KeyEvent): Boolean {
        if (!panelSetting.extended) return false

        return moduleButtons.reversed().any { it.keyPressed(input) }
    }

    fun predictedBounds(search: String): FloatArray {
        var estimatedHeight = HEADER_HEIGHT + 3f
        if (panelSetting.extended) {
            for (button in visibleModuleButtons) {
                estimatedHeight += button.predictedHeight()
            }
        }
        val contentHeight = previousHeight.takeIf { it > 0f } ?: estimatedHeight
        val bottom = panelSetting.y + contentHeight.coerceAtLeast(HEADER_HEIGHT) + FOOTER_HEIGHT
        return floatArrayOf(panelSetting.x, panelSetting.y, panelSetting.x + WIDTH, bottom)
    }

    private inline val isMouseOverExtended
        get() = panelSetting.extended && isAreaHovered(
            panelSetting.x,
            panelSetting.y,
            WIDTH,
            previousHeight.coerceAtLeast(HEADER_HEIGHT + FOOTER_HEIGHT),
            true
        )

    companion object {
        const val WIDTH = 118f
        const val HEIGHT = 14f
        const val HEADER_HEIGHT = 18f
        const val FOOTER_HEIGHT = 4f

        fun createActivePanels(): List<Panel> {
            ClickGUIModule.ensurePanelPositionsFit()
            return Category.categories.values
                .filter { ModuleManager.modulesByCategory.containsKey(it) }
                .map { Panel(it) }
        }
    }
}
