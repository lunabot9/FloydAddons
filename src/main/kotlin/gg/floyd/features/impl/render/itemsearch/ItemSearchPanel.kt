package gg.floyd.features.impl.render.itemsearch

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.impl.render.FloydUiTheme
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.*
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.core.component.DataComponents
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil

/** One layout and hit-test implementation shared by the sidebar and full browser. */
internal class ItemSearchPanel(val x: Int, val y: Int, val width: Int, val height: Int, focus: Boolean) {
    companion object { private var rememberedQuery = "" }
    private val font get() = mc.font
    private val columns = maxOf(1, (width - 12) / 22)
    private val rows = maxOf(1, (height - 83) / 22)
    private val capacity = columns * rows
    private var page = 0
    private var query = ""
    private var results = emptyList<SearchItem>()
    private var hoveredItem: SearchItem? = null
    private val input = EditBox(font, x + 10, y + 28, width - 20, 10, Component.literal("Search SkyBlock items"))
    private val gridY = y + 59

    init {
        val initialQuery = rememberedQuery
        input.setMaxLength(160)
        input.setBordered(false)
        input.setHint(Component.literal("Search items..."))
        input.setResponder { update(it) }
        input.value = initialQuery
        input.isFocused = focus
        update(input.value)
    }
    private fun update(text: String) {
        rememberedQuery = text
        query = text
        page = 0
        results = ItemSearchCatalog.search(text)
    }
    private val pages get() = maxOf(1, ceil(results.size.toDouble() / capacity).toInt())
    private fun inside(mx: Double, my: Double) = mx >= x && mx < x + width && my >= y && my < y + height
    private fun visible() = results.drop(page * capacity).take(capacity)
    private fun tileX(i: Int) = x + (width - columns * 22) / 2 + (i % columns) * 22
    private fun tileY(i: Int) = gridY + (i / columns) * 22
    private fun hit(i: Int, mx: Double, my: Double) = mx >= tileX(i) && mx < tileX(i) + 21 && my >= tileY(i) && my < tileY(i) + 21

    fun render(g: GuiGraphics, mx: Int, my: Int) {
        g.nextStratum()
        val palette = FloydUiTheme.palette()
        val visible = visible()
        hoveredItem = null
        NVGPIPRenderer.draw(g, x, y, width, height,
            mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio(), localCoordinates = true) {
            NVGRenderer.rect(0f, 0f, width.toFloat(), height.toFloat(), 0x80000000.toInt(), 8f)
            NVGRenderer.rect(6f, 23f, width - 12f, 18f, 0x66000000, 4f)
            NVGRenderer.hollowRect(6.5f, 23.5f, width - 13f, 17f, 1f,
                palette[if (input.isFocused) "Accent" else "Border"], 4f)
            visible.forEachIndexed { i, _ ->
                val hovered = hit(i, mx.toDouble(), my.toDouble())
                NVGRenderer.rect((tileX(i) - x).toFloat(), (tileY(i) - y).toFloat(), 21f, 21f,
                    if (hovered) 0x80555555.toInt() else 0x66000000, 4f)
            }
        }
        g.nextStratum()
        g.drawString(font, "Item Search", x + 7, y + 8, palette["Primary Text"])
        g.drawString(font, "?", x + width - 13, y + 8, palette["Accent"])
        input.setTextColor(palette["Primary Text"])
        input.extractRenderState(g, mx, my, 0f)
        g.drawString(font, "${results.size} ${if (results.size == 1) "item" else "items"}", x + 7, y + 46, palette["Secondary Text"])
        visible.forEachIndexed { i, entry ->
            val hovered = hit(i, mx.toDouble(), my.toDouble())
            g.renderItem(ItemSearchCatalog.icon(entry), tileX(i) + 2, tileY(i) + 2)
            if (hovered) { hoveredItem = entry; tooltip(g, entry, mx, my) }
        }
        if (results.isEmpty()) g.drawString(font, "No matches", x + 7, gridY + 8, palette["Secondary Text"])
        val footer = "< ${page + 1}/$pages >"
        g.drawString(font, footer, x + (width - font.width(footer)) / 2, y + height - 14, palette["Accent"])
        if (mx >= x + width - 17 && my in y + 4..y + 20 && inside(mx.toDouble(), my.toDouble())) {
            g.setComponentTooltipForNextFrame(font, listOf("SkyBlock item search", "Words match names and IDs", "#word searches lore; @word searches IDs", "!word excludes; | separates alternatives", "\"two words\" matches a phrase", "Ctrl+F: focus  /  Esc: unfocus", "Scroll or click arrows to change page", "Click an item for lore and crafting recipe", "Catalog: NotEnoughUpdates-REPO").map(Component::literal), mx, my)
        }
    }
    private fun tooltip(g: GuiGraphics, item: SearchItem, mx: Int, my: Int) {
        g.setTooltipForNextFrame(font,ItemSearchCatalog.icon(item),mx,my)
    }
    fun click(event: MouseButtonEvent, parent: Screen): Boolean {
        if (!inside(event.x(), event.y())) { input.isFocused = false; return false }
        if (event.button() != 0 && event.button() != 1) return true
        if (event.y() >= y + 23 && event.y() < y + 41) {
            input.isFocused = true
            if (event.button() == 1) input.value = "" else input.mouseClicked(event, false)
        } else {
            input.isFocused = false
            if (event.y() >= y + height - 22) page = (page + if (event.x() < x + width / 2) -1 else 1).coerceIn(0, pages - 1)
            else visible().forEachIndexed { i, entry -> if (hit(i, event.x(), event.y()) && ItemAcquisition.canOpen(entry)) mc.setScreen(ItemSearchDetail(parent, entry)) }
        }
        return true
    }
    fun key(event: KeyEvent): Boolean {
        if (event.key == GLFW.GLFW_KEY_F && event.modifiers and GLFW.GLFW_MOD_CONTROL != 0) { input.isFocused = true; return true }
        if (!input.isFocused) return false
        if (event.key == GLFW.GLFW_KEY_ESCAPE || event.key == GLFW.GLFW_KEY_ENTER) input.isFocused = false
        else input.keyPressed(event)
        // Consume inventory close keys while typing, including E.
        return true
    }
    fun character(event: CharacterEvent) = input.isFocused && input.charTyped(event)
    fun scroll(mx: Double, my: Double, amount: Double): Boolean {
        if (!inside(mx, my)) return false
        if (amount != 0.0) page = (page + if (amount > 0) -1 else 1).coerceIn(0, pages - 1)
        return true
    }
    fun state() = mapOf("query" to query, "matches" to results.size, "catalogSize" to ItemSearchCatalog.items.size,
        "cornerRadius" to 8, "accentColor" to "#%08X".format(FloydUiTheme.palette()["Accent"]),
        "headerVisible" to false, "backgroundColor" to "#80000000",
        "hoveredItem" to hoveredItem?.id, "tooltipLoreLines" to (hoveredItem?.lore?.size ?: 0),"nativeItemTooltips" to true,
        "themePreset" to FloydUiTheme.presetName(), "rightInset" to mc.window.guiScaledWidth - x - width,
        "page" to page, "pages" to pages, "focused" to input.isFocused, "x" to x, "y" to y, "width" to width, "height" to height,
        "searchX" to input.x, "searchY" to input.y, "tiles" to visible().mapIndexed { i, entry -> mapOf("id" to entry.id, "x" to tileX(i), "y" to tileY(i),
            "dyeColor" to ItemSearchCatalog.icon(entry).get(DataComponents.DYED_COLOR)?.rgb(),"canOpen" to ItemAcquisition.canOpen(entry)) })
}

internal class ItemSearchBrowser(private val parent: Screen?) : Screen(Component.literal("SkyBlock Item Search")) {
    lateinit var panel: ItemSearchPanel
        private set
    override fun init() {
        val w = minOf(384, width - 20)
        panel = ItemSearchPanel((width - w) / 2, 10, w, height - 20, true)
    }
    override fun extractBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) { g.fill(0, 0, width, height, 0x55000000) }
    override fun extractRenderState(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) = panel.render(g, mouseX, mouseY)
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean) = panel.click(event, this) || super.mouseClicked(event, doubleClick)
    override fun keyPressed(event: KeyEvent) = panel.key(event) || super.keyPressed(event)
    override fun charTyped(event: CharacterEvent) = panel.character(event) || super.charTyped(event)
    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double) = panel.scroll(x, y, vertical)
    override fun onClose() { mc.setScreen(parent) }
    override fun isPauseScreen() = false
}
