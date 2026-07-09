package gg.floyd.features.impl.pvp

import com.mojang.blaze3d.platform.InputConstants
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import org.lwjgl.glfw.GLFW
import kotlin.random.Random

object FloydLoadoutSwapper : Module(
    name = "Loadout Swapper",
    category = Category.PVP,
    description = "Runs /loadout and applies the matching Hypixel loadout from configurable hotkeys.",
) {
    private val loadoutTitlePattern = Regex("""\((\d+)/(\d+)\)\s*Loadouts?""")
    private val targetSlots = intArrayOf(14, 15, 16, 23, 24, 25, 32, 33, 34)
    private val autoClose by BooleanSetting("Auto Close", true, desc = "Closes the Loadouts menu after selecting a loadout.")
    private val swapDelay by NumberSetting("Delay", 0.0f, 0.0f, 5.0f, 0.05f, desc = "Seconds to wait before selecting the requested loadout after /loadout opens.", unit = "s")
    private val randomizationMs by NumberSetting("Randomization", 0.0f, 0.0f, 1000.0f, 10.0f, desc = "Adds 0 to N ms of extra random delay before clicking the loadout.", unit = "ms")
    private val loadoutKeys = listOf(
        loadoutKey(1, GLFW.GLFW_KEY_1),
        loadoutKey(2, GLFW.GLFW_KEY_2),
        loadoutKey(3, GLFW.GLFW_KEY_3),
        loadoutKey(4, GLFW.GLFW_KEY_4),
        loadoutKey(5, GLFW.GLFW_KEY_5),
        loadoutKey(6, GLFW.GLFW_KEY_6),
        loadoutKey(7, GLFW.GLFW_KEY_7),
        loadoutKey(8, GLFW.GLFW_KEY_8),
        loadoutKey(9, GLFW.GLFW_KEY_9),
    )

    private var pendingLoadoutIndex = -1
    private var pendingOpenTicks = -1
    private var pendingSelectAtMs = -1L
    private var pendingCloseTicks = -1

    init {
        on<TickEvent.ClientEnd> {
            val now = System.currentTimeMillis()
            val currentScreen = client.screen as? AbstractContainerScreen<*>
            val currentMenu = currentScreen?.menu as? ChestMenu

            if (pendingLoadoutIndex >= 0) {
                if (currentScreen != null && currentMenu != null && isLoadoutScreen(currentScreen.title.string)) {
                    if (pendingSelectAtMs < 0L) pendingSelectAtMs = now + configuredSelectionDelayMs()
                    if (now >= pendingSelectAtMs) {
                        if (applyLoadoutSelection(currentMenu, pendingLoadoutIndex)) {
                            clearPendingLoadoutRequest()
                        }
                    }
                } else {
                    pendingSelectAtMs = -1L
                    if (pendingOpenTicks <= 0) {
                        clearPendingLoadoutRequest()
                    } else {
                        pendingOpenTicks--
                    }
                }
            }

            if (pendingCloseTicks < 0) return@on
            if (currentScreen == null || currentMenu == null) {
                clearPendingClose()
                return@on
            }
            if (!isLoadoutScreen(currentScreen.title.string)) {
                clearPendingClose()
                return@on
            }

            if (pendingCloseTicks == 0) {
                currentScreen.onClose()
                clearPendingClose()
                return@on
            }

            pendingCloseTicks--
        }
    }

    override fun onDisable() {
        clearPendingLoadoutRequest()
        clearPendingClose()
        super.onDisable()
    }

    internal fun isLoadoutScreen(title: String): Boolean =
        loadoutTitlePattern.matches(title)

    internal fun loadoutIndexFor(key: InputConstants.Key): Int =
        loadoutKeys.indexOfFirst { setting -> setting.value == key }

    internal fun keySetting(index: Int): KeybindSetting =
        loadoutKeys[index]

    internal fun delaySetting(): NumberSetting<*> =
        settings["Delay"] as NumberSetting<*>

    internal fun randomizationSetting(): NumberSetting<*> =
        settings["Randomization"] as NumberSetting<*>

    internal fun autoCloseSetting(): BooleanSetting =
        settings["Auto Close"] as BooleanSetting

    internal fun configuredBaseDelayMs(): Long =
        (swapDelay * 1000f).toLong().coerceAtLeast(0L)

    internal fun configuredRandomizationMs(): Long =
        randomizationMs.toLong().coerceAtLeast(0L)

    internal fun configuredSelectionDelayMs(sampleRandomization: (Long) -> Long = ::sampleRandomizationMs): Long =
        configuredBaseDelayMs() + sampleRandomization(configuredRandomizationMs())

    private fun activateLoadout(index: Int) {
        val player = mc.player ?: return
        val currentScreen = mc.screen as? AbstractContainerScreen<*>
        val currentMenu = currentScreen?.menu as? ChestMenu
        if (currentScreen != null && currentMenu != null && isLoadoutScreen(currentScreen.title.string)) {
            pendingLoadoutIndex = index
            pendingOpenTicks = -1
            pendingSelectAtMs = System.currentTimeMillis() + configuredSelectionDelayMs()
            return
        }

        pendingLoadoutIndex = index
        pendingOpenTicks = 40
        pendingSelectAtMs = -1L
        player.connection.sendCommand("loadout")
    }

    private fun applyLoadoutSelection(menu: ChestMenu, loadoutIndex: Int): Boolean {
        if (targetSlots.last() >= menu.slots.size) return false
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val slotIndex = targetSlots.getOrNull(loadoutIndex) ?: return false
        val slot = menu.slots[slotIndex]
        if (!slot.hasItem()) return false

        gameMode.handleInventoryMouseClick(menu.containerId, slotIndex, 0, ClickType.PICKUP, player)
        if (autoClose) {
            pendingCloseTicks = 5
        } else {
            clearPendingClose()
        }
        return true
    }

    private fun clearPendingLoadoutRequest() {
        pendingLoadoutIndex = -1
        pendingOpenTicks = -1
        pendingSelectAtMs = -1L
    }

    private fun clearPendingClose() {
        pendingCloseTicks = -1
    }

    private fun loadoutKey(index: Int, defaultKey: Int): KeybindSetting =
        registerSetting(
            KeybindSetting(
                "Loadout $index Key",
                defaultKey,
                desc = "Key used to open /loadout and apply loadout $index."
            ).onPress {
                activateLoadout(index - 1)
            }
        )

    private fun sampleRandomizationMs(maxRandomizationMs: Long): Long =
        if (maxRandomizationMs <= 0L) 0L else Random.nextLong(maxRandomizationMs + 1L)
}
