// SOURCE: https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/noammaddons/features/impl/misc/DamageSplash.kt
package noammaddons.features.impl.misc

import net.minecraft.network.play.server.S0FPacketSpawnMob
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import noammaddons.events.MainThreadPacketRecivedEvent
import noammaddons.features.Feature
import noammaddons.ui.config.core.impl.ToggleSetting
import noammaddons.utils.ChatUtils.addColor
import noammaddons.utils.ChatUtils.removeFormatting
import noammaddons.utils.LocationUtils.inBoss
import noammaddons.utils.LocationUtils.inDungeon
import noammaddons.utils.LocationUtils.inSkyblock
import noammaddons.utils.NumbersUtils.format
import noammaddons.utils.Utils.containsOneOf

object DamageSplash: Feature() {
    private val uppercase by ToggleSetting("Uppercase Damage Formatting")
    private val disableinBoss by ToggleSetting("Disable in Dungeon Boss Room")
    private val disableinClear by ToggleSetting("Disable in Dungeon Clear")

    val damageRegex = Regex("[✧✯]?(\\d{1,3}(?:,\\d{3})*[⚔+✧❤♞☄✷ﬗ✯]*)")

    @SubscribeEvent
    fun handleCustomDamageSplash(event: MainThreadPacketRecivedEvent.Pre) {
        if (!inSkyblock) return
        val packet = event.packet as? S0FPacketSpawnMob ?: return
        if (packet.entityType != 30) return // Armor stand
        val nameData = packet.func_149027_c()?.find { it.getObject().toString().contains("§") } ?: return
        val name = "${nameData.getObject()}".removeFormatting()
        val damage = damageRegex.matchEntire(name)?.destructured?.component1() ?: return
        if (inBoss && disableinBoss) return event.setCanceled(true)
        if (inDungeon && !inBoss && disableinClear) return event.setCanceled(true)
        val formatted = if (uppercase) format(damage).uppercase() else format(damage)
        val newName = if (name.containsOneOf("✧", "✯")) "&f✧${addRandomColorCodes(formatted)}&f✧"
        else "&3${formatted}"
        nameData.setObject(newName.addColor())
    }

    private fun addRandomColorCodes(inputString: String): String {
        val colorCodes = listOf("§6", "§c", "§e", "§f")
        val result = StringBuilder()
        var lastColor: String? = null
        for (char in inputString.removeFormatting()) {
            val availableColors = colorCodes.filter { it != lastColor }
            val randomColor = availableColors.random()
            result.append(randomColor).append(char).append("§r")
            lastColor = randomColor
        }
        return result.toString()
    }
}
