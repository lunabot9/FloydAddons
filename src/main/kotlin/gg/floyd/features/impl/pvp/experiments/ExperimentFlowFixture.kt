package gg.floyd.features.impl.pvp.experiments

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/** Full session QA: every table reopening must cross the real use-block connection. */
object ExperimentFlowFixture {
    var active = false; private set
    private var stage = 0
    private var tableOpens = 0
    private var claims = 0
    private var invalidClicks = 0
    private var tablePos: BlockPos? = null
    private var installed = false
    fun state() = mapOf("active" to active, "stage" to stage, "tableOpens" to tableOpens, "claims" to claims, "invalidClicks" to invalidClicks)
    fun open() {
        val mc = Minecraft.getInstance()
        val server = mc.singleplayerServer ?: error("Integrated-server QA only")
        val uuid = mc.player?.uuid ?: error("No player")
        active = true; stage = 0; tableOpens = 0; claims = 0; invalidClicks = 0
        ExperimentFixture.table(false) // Red cooldown text must not prevent continuation of an active set.
        if (!installed) {
            installed = true
            UseBlockCallback.EVENT.register { player, _, _, hit ->
                if (active && player is ServerPlayer && hit.blockPos == tablePos) {
                    openTable(player); InteractionResult.SUCCESS
                } else InteractionResult.PASS
            }
        }
        server.execute {
            val player = server.playerList.getPlayer(uuid) ?: return@execute
            tablePos = player.blockPosition().offset(0, 0, 2)
            openTable(player)
        }
    }
    private fun item(item: Item, name: String, vararg lore: String) = ItemStack(item).also {
        it.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal)))
    }
    private fun openTable(owner: ServerPlayer) {
        tableOpens++
        val contents = SimpleContainer(54)
        val charge = if (stage >= 3) 0 else 1
        contents.setItem(22, item(Items.PLAYER_HEAD, "Superpairs", "Charges: $charge/3", if (stage == 2) "Click to play!" else "Play add-ons first!"))
        if (stage < 2) contents.setItem(if (stage == 0) 29 else 33,
            item(Items.PLAYER_HEAD, if (stage == 0) "Chronomatron" else "Ultrasequencer", "Click to play!"))
        owner.openMenu(SimpleMenuProvider({ id, inventory, _ ->
            object : ChestMenu(MenuType.GENERIC_9x6, id, inventory, contents, 6) {
                override fun clicked(slot: Int, button: Int, input: ContainerInput, player: Player) {
                    val expected = when (stage) { 0 -> 29; 1 -> 33; 2 -> 22; else -> -1 }
                    if (slot == expected && button == 0 && input == ContainerInput.PICKUP) {
                        ExperimentFixture.open(ExperimentGame.entries[stage].name, 3)
                    } else { invalidClicks++; sendAllDataToRemote() }
                }
            }
        }, Component.literal("Experimentation Table")))
    }
    fun addonResult(owner: ServerPlayer, game: ExperimentGame) {
        val contents = SimpleContainer(27)
        contents.setItem(11, item(Items.PLAYER_HEAD, game.name, "Bonus: +3 clicks", "Click to claim rewards!"))
        owner.openMenu(SimpleMenuProvider({ id, inventory, _ ->
            object : ChestMenu(MenuType.GENERIC_9x3, id, inventory, contents, 3) {
                override fun clicked(slot: Int, button: Int, input: ContainerInput, player: Player) {
                    if (slot == 11 && button == 0 && input == ContainerInput.PICKUP) { claimed(game); owner.closeContainer() }
                    else { invalidClicks++; sendAllDataToRemote() }
                }
            }
        }, Component.literal("Experiment Over")))
    }
    fun claimed(game: ExperimentGame) {
        if (!active) return
        if (game.ordinal != stage) { invalidClicks++; return }
        claims++; stage++
    }
}
