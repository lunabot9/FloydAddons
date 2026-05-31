package gg.floyd.features.impl.pvp

import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.Items

/**
 * Keeps a Totem of Undying in the offhand. When the offhand has no totem and one
 * is available in the inventory, it is moved into the offhand after a configurable
 * delay (0s instant .. 5s slow).
 */
object FloydAutoTotem : Module(
    name = "Auto Totem",
    category = Category.PVP,
    description = "Automatically moves a Totem of Undying into your offhand after a configurable delay.",
) {
    private val delay by NumberSetting("Delay", 0.0f, 0.0f, 5.0f, 0.05f, desc = "Seconds before re-equipping a totem (0 = instant).", unit = "s")

    // Screen-handler slot indices for the player's own inventory menu.
    private const val OFFHAND_SLOT = 45
    private val SEARCH_SLOTS = 9..44 // main inventory (9-35) + hotbar (36-44)

    private var ticksWithoutTotem = 0

    init {
        on<TickEvent.ClientEnd> {
            if (!enabled) return@on
            val player = mc.player ?: return@on
            val gameMode = mc.gameMode ?: return@on

            // Only act on the player's own inventory menu; never fight an open chest/container.
            if (player.containerMenu !== player.inventoryMenu) {
                ticksWithoutTotem = 0
                return@on
            }

            if (player.offhandItem.item == Items.TOTEM_OF_UNDYING) {
                ticksWithoutTotem = 0
                return@on
            }

            if (++ticksWithoutTotem < (delay * 20f).toInt()) return@on
            ticksWithoutTotem = 0

            val menu = player.inventoryMenu
            val totemSlot = SEARCH_SLOTS.firstOrNull { i ->
                val stack = menu.slots[i].item
                !stack.isEmpty && stack.item == Items.TOTEM_OF_UNDYING
            } ?: return@on

            // Pick up the totem, drop it into the offhand, then return whatever was in the offhand.
            val containerId = menu.containerId
            gameMode.handleInventoryMouseClick(containerId, totemSlot, 0, ClickType.PICKUP, player)
            gameMode.handleInventoryMouseClick(containerId, OFFHAND_SLOT, 0, ClickType.PICKUP, player)
            gameMode.handleInventoryMouseClick(containerId, totemSlot, 0, ClickType.PICKUP, player)
        }
    }
}
