package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudManager
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.clickgui.settings.impl.HudElement
import gg.floyd.clickgui.settings.impl.ListSetting
import gg.floyd.events.ChatPacketEvent
import gg.floyd.events.ScreenEvent
import gg.floyd.events.WorldEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.mixin.accessors.SackContainerLayoutAccessor
import gg.floyd.supercraft.SackCraftPlanner
import gg.floyd.supercraft.SackSupercraft
import gg.floyd.utils.Color
import gg.floyd.utils.font.ClickGuiFont
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.hollowFill
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.drawString
import net.minecraft.client.gui.renderItem
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import java.util.Locale
import java.util.Optional

object FloydSackTracker : Module(
    name = "Sack Tracker",
    category = Category.RENDER,
    description = "Shift-click items in a sack to toggle their counts on a movable HUD.",
    toggled = true,
) {
    private val tracked by ListSetting("Tracked Items", linkedSetOf<String>()).hide()
    private data class Amount(val count: Long, val text: String = String.format(Locale.US, "%,d", count))
    private val amounts = mutableMapOf<String, Amount>()
    private val icons = mutableMapOf<String, ItemStack>()
    private val sackChange = Regex("""\s*([+-][\d,]+) (.+) \(([^()]+)\)\s*""")
    private val target = FloydPanelStyle.PanelTarget.SACK_TRACKER
    private const val HEADER_HEIGHT = 14
    private const val ROW_HEIGHT = 19
    private const val REMOVE_SIZE = 14
    private const val REMOVE_GAP = 7
    private const val EMPTY_HINT = "Shift-click an item in a sack"

    private val hud: HudElement by HUD("Sack Tracker", description, false, 390, 90, 2f) { drawTracker(it) }

    private fun GuiGraphics.drawTracker(example: Boolean): Pair<Int, Int> {
        val layout = layout(example) ?: return 0 to 0
        HudPanel.fillPanel(this, 0, 0, layout.width, layout.height, target, HudPanel.panelBorderColors(target, hud.x, hud.y))
        val font = ClickGuiFont.font
        val hoveredRow = if (removeControlsActive(example)) {
            val (x, y) = mousePoint()
            layout.removeRowAt(x, y)
        } else null
        drawString(font, name, layout.titleX(font.width(name)), layout.padding, 0xFFE7EEF5.toInt(), false)
        if (layout.rows.isEmpty()) {
            drawString(font, EMPTY_HINT, layout.padding, layout.rowY(0), 0xFFB7C5D4.toInt(), false)
        }
        layout.rows.forEachIndexed { index, row ->
            val y = layout.rowY(index)
            renderItem(icons.getOrPut(row.id) { SackSupercraft.targetIcon(row.id) }, layout.padding, y)
            drawString(font, row.name, layout.padding + 21, y + 4, 0xFFE7EEF5.toInt(), false)
            drawString(font, row.amount, layout.amountRight - font.width(row.amount), y + 4, 0xFF9DDFEF.toInt(), false)
            val x = layout.removeX
            val buttonY = layout.removeY(index)
            val hovered = hoveredRow == index
            fill(x, buttonY, x + REMOVE_SIZE, buttonY + REMOVE_SIZE,
                if (hovered) 0xFFFFADAD.toInt() else 0xFFAA686F.toInt())
            fill(x + 1, buttonY + 1, x + REMOVE_SIZE - 1, buttonY + REMOVE_SIZE - 1,
                if (hovered) 0xFF9C434D.toInt() else 0xFF5B3038.toInt())
            val center = REMOVE_SIZE / 2f
            pose().pushMatrix()
            pose().translate(x + center, buttonY + center)
            pose().rotate((Math.PI / 4).toFloat())
            val iconColor = 0xFFFFF0F0.toInt()
            fill(-3, -1, 3, 1, iconColor)
            fill(-1, -3, 1, 3, iconColor)
            pose().popMatrix()
        }
        return layout.width to layout.height
    }

    private data class Row(val id: String, val name: String, val amount: String)
    private data class Layout(val rows: List<Row>, val width: Int, val height: Int, val padding: Int) {
        val removeX get() = width - padding - REMOVE_SIZE
        val amountRight get() = removeX - REMOVE_GAP
        fun titleX(titleWidth: Int) = (width - titleWidth) / 2
        fun rowY(index: Int) = padding + HEADER_HEIGHT + index * ROW_HEIGHT
        fun removeY(index: Int) = rowY(index) + (16 - REMOVE_SIZE) / 2
        fun removeRowAt(x: Float, y: Float): Int? {
            if (x < removeX || x >= removeX + REMOVE_SIZE || y < removeY(0)) return null
            val index = ((y - removeY(0)) / ROW_HEIGHT).toInt()
            return index.takeIf { it in rows.indices && y < removeY(it) + REMOVE_SIZE }
        }
    }

    init {
        HudSizeRegistry.register(name) { layout(true)?.let { it.width to it.height } ?: (0 to 0) }

        on<ScreenEvent.MouseClick> {
            val editor = screen === HudManager
            if (click.button() == 0 && tracked.isNotEmpty() && removeControlsActive(editor)) {
                val layout = layout(editor)
                val (x, y) = mousePoint(click.x().toFloat() * mc.window.guiScale, click.y().toFloat() * mc.window.guiScale)
                val row = layout?.removeRowAt(x, y)
                if (row != null) {
                    untrack(setOf(layout.rows[row].id))
                    ModuleManager.saveConfigurations()
                    cancel()
                    return@on
                }
            }
            if (click.button() != 0 || !click.hasShiftDown()) return@on
            if (!SackSupercraft.allowed()) return@on
            val chest = screen as? AbstractContainerScreen<*> ?: return@on
            val menu = chest.menu as? ChestMenu ?: return@on
            val sack = SackSupercraft.sackName(chest.title.string) ?: return@on
            val bounds = chest as SackContainerLayoutAccessor
            val x = click.x() - bounds.`floyd$sackLeft`()
            val y = click.y() - bounds.`floyd$sackTop`()
            val slot = menu.slots.take(menu.rowCount * 9).firstOrNull {
                x >= it.x && x < it.x + 16 && y >= it.y && y < it.y + 16
            } ?: return@on
            val stock = SackSupercraft.readStock(sack, slot.item)
            if (stock.isEmpty()) return@on
            if (tracked.containsAll(stock.keys)) {
                untrack(stock.keys)
            } else {
                tracked.addAll(stock.keys)
                update(stock, slot.item)
            }
            ModuleManager.saveConfigurations()
            cancel()
        }

        on<ScreenEvent.RenderAfter> {
            if (tracked.isEmpty()) return@on
            val chest = screen as? AbstractContainerScreen<*> ?: return@on
            val menu = chest.menu as? ChestMenu ?: return@on
            if (SackSupercraft.sackName(chest.title.string) == null) return@on
            val bounds = chest as SackContainerLayoutAccessor
            for (index in 0 until menu.rowCount * 9) {
                val slot = menu.slots[index]
                if (slot.item.isEmpty || SackSupercraft.itemId(slot.item) !in tracked) continue
                guiGraphics.hollowFill(bounds.`floyd$sackLeft`() + slot.x - 1, bounds.`floyd$sackTop`() + slot.y - 1, 18, 18, 1, Color(0xFF9DDFEF.toInt()))
            }
        }

        on<ChatPacketEvent> {
            if (amounts.isNotEmpty() && value.startsWith("[Sacks]")) applyChanges(component)
        }
        on<WorldEvent.Unload> { amounts.clear(); icons.clear() }
    }

    override fun onDisable() {
        super.onDisable()
        amounts.clear()
        icons.clear()
    }

    private fun untrack(ids: Set<String>) {
        tracked.removeAll(ids)
        amounts.keys.removeAll(ids)
        icons.keys.removeAll(ids)
    }

    // A server snapshot supersedes chat deltas and resynchronizes previously unknown counts.
    fun serverSlots(containerId: Int, firstSlot: Int, stacks: List<ItemStack>) {
        if (!enabled || tracked.isEmpty()) return
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        val menu = screen.menu as? ChestMenu ?: return
        if (menu.containerId != containerId || firstSlot !in 0 until menu.rowCount * 9) return
        val sack = SackSupercraft.sackName(screen.title.string) ?: return
        for (index in 0 until minOf(stacks.size, menu.rowCount * 9 - firstSlot)) {
            val stack = stacks[index]
            update(SackSupercraft.readStock(sack, stack), stack)
        }
    }

    private fun update(stock: Map<String, Long>, stack: ItemStack) {
        for ((id, count) in stock) {
            if (id !in tracked) continue
            if (amounts[id]?.count != count) amounts[id] = Amount(count)
            if (stock.size == 1) icons[id] = stack.copyWithCount(1)
        }
    }

    private fun applyChanges(component: Component) {
        val details = mutableSetOf<String>()
        component.visit({ style, _ ->
            val hover = style.hoverEvent as? HoverEvent.ShowText
            val text = hover?.value()?.string
            if (text != null && (text.startsWith("Added") || text.startsWith("Removed"))) details += text
            Optional.empty<Unit>()
        }, Style.EMPTY)
        if (details.isEmpty()) return

        // While viewing an item, its slot updates own the count. The same changes may also appear in chat.
        val visible = visibleStock()
        val changes = mutableMapOf<String, Long>()
        for (detail in details) {
            val reported = mutableSetOf<String>()
            for (line in detail.lineSequence()) {
                val match = sackChange.matchEntire(line) ?: continue
                val (deltaText, item, sacks) = match.destructured
                val delta = deltaText.replace(",", "").toLongOrNull()
                    ?.takeIf { it in -1_000_000_000_000L..1_000_000_000_000L } ?: continue
                val itemName = SackCraftPlanner.displayName(item)
                val sackNames = sacks.split(", ")
                val id = tracked.singleOrNull { key ->
                    SackSupercraft.displayName(key).equals(itemName, ignoreCase = true) &&
                        sackNames.any { SackSupercraft.itemBelongsToSack(it, key) }
                } ?: continue
                reported += id
                if (id !in visible) changes.merge(id, delta, Long::plus)
            }
            if ("other items" in detail) amounts.keys.retainAll(visible + reported)
        }
        for ((id, delta) in changes) {
            val old = amounts[id]?.count ?: continue
            val updated = old + delta
            if (updated >= 0) amounts[id] = Amount(updated) else amounts.remove(id)
        }
    }

    private fun visibleStock(): Set<String> {
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return emptySet()
        val menu = screen.menu as? ChestMenu ?: return emptySet()
        val sack = SackSupercraft.sackName(screen.title.string) ?: return emptySet()
        return menu.slots.take(menu.rowCount * 9).flatMapTo(mutableSetOf()) {
            SackSupercraft.readStock(sack, it.item).keys
        }
    }

    private fun layout(example: Boolean): Layout? {
        if (!example && (tracked.isEmpty() || !SackSupercraft.allowed())) return null
        val rows = tracked.map { id ->
            Row(id, SackSupercraft.displayName(id), amounts[id]?.text ?: "—")
        }
        val font = ClickGuiFont.font
        val padding = maxOf(5, FloydPanelStyle.paddingFor(target))
        val contentWidth = rows.maxOfOrNull { 21 + font.width(it.name) + 12 + font.width(it.amount) + REMOVE_GAP + REMOVE_SIZE }
            ?: font.width(EMPTY_HINT)
        val width = maxOf(font.width(name), contentWidth)
        return Layout(rows, width + padding * 2,
            padding * 2 + HEADER_HEIGHT + maxOf(1, rows.size) * ROW_HEIGHT, padding)
    }

    private fun removeControlsActive(editor: Boolean) =
        enabled && (editor || mc.screen is ChatScreen && hud.enabled && !mc.options.hideGui)

    private fun mousePoint(x: Float = HudManager.renderSpaceMouseX(), y: Float = HudManager.renderSpaceMouseY()) =
        (x - hud.renderedX) / hud.scale to (y - hud.renderedY) / hud.scale

    fun state(): Map<String, Any?> {
        val editor = mc.screen === HudManager
        val layout = if (editor || enabled && hud.enabled && !mc.options.hideGui) layout(editor) else null
        val titleWidth = if (layout == null) 0 else ClickGuiFont.font.width(name)
        return mapOf(
            "enabled" to enabled,
            "tracked" to tracked.toList(),
            "amounts" to amounts.mapValues { it.value.count },
            "x" to hud.renderedX,
            "y" to hud.renderedY,
            "scale" to hud.scale,
            "width" to hud.width,
            "height" to hud.height,
            "titleX" to layout?.titleX(titleWidth),
            "titleWidth" to titleWidth,
            "removeButtons" to layout?.rows?.indices?.map { index ->
                mapOf("x" to layout.removeX, "y" to layout.removeY(index), "size" to REMOVE_SIZE)
            }.orEmpty(),
            "removeControlsActive" to removeControlsActive(editor),
        )
    }
}
