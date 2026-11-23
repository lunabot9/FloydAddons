package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.events.ScreenEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.itemsearch.ItemSearchBrowser
import gg.floyd.features.impl.render.itemsearch.ItemSearchCatalog
import gg.floyd.features.impl.render.itemsearch.ItemSearchDetail
import gg.floyd.features.impl.render.itemsearch.ItemSearchPanel
import gg.floyd.mixin.accessors.SackContainerLayoutAccessor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW
import java.lang.ref.WeakReference

object FloydItemSearch : Module("Item Search", category = Category.RENDER,
    description = "Search all SkyBlock items without REI. Inventory sidebar, lore tooltips and crafting recipes. /fa items opens the browser.") {
    private val inventoryPanel by BooleanSetting("Inventory Panel", true, desc = "Show the item browser at the right edge of the screen in inventories and chests. Ctrl+F focuses search.")
    private val openKey by KeybindSetting("Open Browser", GLFW.GLFW_KEY_UNKNOWN, desc = "Open the standalone SkyBlock item browser.").onPress { openBrowser() }
    private val openAction by ActionSetting("Browse Items", "Open the standalone item browser.") { openBrowser() }
    private var host = WeakReference<Screen>(null)
    private var panel: ItemSearchPanel? = null
    private var panelVisible = false

    fun openBrowser() {
        if (!enabled) { toggle(); ModuleManager.saveConfigurations() }
        mc.setScreen(ItemSearchBrowser(mc.screen))
    }

    private fun sidebar(screen: Screen): ItemSearchPanel? {
        if (!inventoryPanel || screen !is AbstractContainerScreen<*>) return null
        val access = screen as SackContainerLayoutAccessor
        val containerRight = access.`floyd$sackLeft`() + access.`floyd$sackWidth`()
        val width = minOf(158, screen.width - containerRight - 16)
        // At large GUI scales there is no safe space beside the inventory. /fa items
        // and Open Browser still provide the full browser without covering slots.
        if (width < 76 || screen.height < 115) return null
        val x = screen.width - width - 8
        val top = 8
        val height = screen.height - 16
        if (host.get() !== screen || panel?.x != x || panel?.width != width || panel?.height != height) {
            host = WeakReference(screen)
            panel = ItemSearchPanel(x, top, width, height, false)
        }
        return panel
    }

    init {
        on<ScreenEvent.RenderOverlay> {
            val current = sidebar(screen)
            panelVisible = current != null
            current?.render(guiGraphics, mouseX, mouseY)
        }
        on<ScreenEvent.MouseClick> {
            sidebar(screen)?.let { if (it.click(click, screen)) cancel() }
        }
        on<ScreenEvent.KeyPress> { sidebar(screen)?.let { if (it.key(input)) cancel() } }
        on<ScreenEvent.CharType> { sidebar(screen)?.let { if (it.character(input)) cancel() } }
        on<ScreenEvent.MouseScroll> { sidebar(screen)?.let { if (it.scroll(x, y, vertical)) cancel() } }
        on<ScreenEvent.Close> { panelVisible = false; panel = null; host.clear() }
    }

    override fun onDisable() {
        panelVisible = false
        panel = null
        host.clear()
        ItemSearchCatalog.clearIcons()
        super.onDisable()
    }

    fun state(): Map<String, Any?> {
        val browser = mc.screen as? ItemSearchBrowser
        val detail = mc.screen as? ItemSearchDetail
        return mapOf("enabled" to enabled, "inventoryPanelVisible" to (enabled && panelVisible && mc.screen is AbstractContainerScreen<*>),
            "browserOpen" to (browser != null), "reiRequired" to false,
            "detailItem" to detail?.entry?.id,
            "detail" to detail?.state(),
            "panel" to (browser?.panel?.state() ?: panel?.state()))
    }
}
