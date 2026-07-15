package gg.floyd.commands

import com.github.stivais.commodore.Commodore
import gg.floyd.features.impl.pvp.FloydAutoClicker
import gg.floyd.features.impl.pvp.FloydAutoClicker.ClickSide
import gg.floyd.utils.errorMessage
import gg.floyd.utils.infoMessage
import gg.floyd.utils.modMessage
import gg.floyd.utils.successMessage

val autoClickerCommand = Commodore("ac") {
    literal("add") { addHeldItemCommands() }
    literal("a") { addHeldItemCommands() }
    literal("remove") { removeHeldItemCommands() }
    literal("r") { removeHeldItemCommands() }
    literal("clear") {
        literal("left").runs { FloydAutoClicker.clearWhitelist(ClickSide.LEFT); modMessage("Left whitelist cleared.") }
        literal("right").runs { FloydAutoClicker.clearWhitelist(ClickSide.RIGHT); modMessage("Right whitelist cleared.") }
        literal("all").runs { FloydAutoClicker.clearWhitelist(null); modMessage("All Auto Clicker whitelists cleared.") }
    }
    literal("list").runs { modMessage(FloydAutoClicker.whitelistSummary()) }
    runs { modMessage(FloydAutoClicker.whitelistSummary()) }
}

private fun com.github.stivais.commodore.nodes.LiteralNode.addHeldItemCommands() {
    sideCommands { side ->
        val item = FloydAutoClicker.heldIdentity() ?: return@sideCommands errorMessage("Hold an item to whitelist.")
        val added = FloydAutoClicker.addWhitelist(side, item)
        if (added) successMessage("Auto Clicker: Added \"$item\" to the ${side.label()} whitelist.")
        else infoMessage("Auto Clicker: \"$item\" is already in the ${side.label()} whitelist.")
    }
}

private fun com.github.stivais.commodore.nodes.LiteralNode.removeHeldItemCommands() {
    sideCommands { side ->
        val item = FloydAutoClicker.heldIdentity() ?: return@sideCommands errorMessage("Hold an item to remove from the whitelist.")
        val removed = FloydAutoClicker.removeWhitelist(side, item)
        if (removed) successMessage("Auto Clicker: Removed \"$item\" from the ${side.label()} whitelist.")
        else infoMessage("Auto Clicker: \"$item\" is not in the ${side.label()} whitelist.")
    }
}

private fun com.github.stivais.commodore.nodes.LiteralNode.sideCommands(action: (ClickSide) -> Unit) {
    literal("left").runs { action(ClickSide.LEFT) }
    literal("right").runs { action(ClickSide.RIGHT) }
}

private fun ClickSide.label(): String = name.lowercase()
