package gg.floyd.utils

import net.minecraft.world.scores.DisplaySlot

/** Stable replacement for the vanilla helper removed in Minecraft 26.2. */
fun teamDisplaySlot(color: Any?): DisplaySlot? {
    val actual = (color as? java.util.Optional<*>)?.orElse(null) ?: color
    return when ((actual as? Enum<*>)?.name) {
        "BLACK" -> DisplaySlot.TEAM_BLACK
        "DARK_BLUE" -> DisplaySlot.TEAM_DARK_BLUE
        "DARK_GREEN" -> DisplaySlot.TEAM_DARK_GREEN
        "DARK_AQUA" -> DisplaySlot.TEAM_DARK_AQUA
        "DARK_RED" -> DisplaySlot.TEAM_DARK_RED
        "DARK_PURPLE" -> DisplaySlot.TEAM_DARK_PURPLE
        "GOLD" -> DisplaySlot.TEAM_GOLD
        "GRAY" -> DisplaySlot.TEAM_GRAY
        "DARK_GRAY" -> DisplaySlot.TEAM_DARK_GRAY
        "BLUE" -> DisplaySlot.TEAM_BLUE
        "GREEN" -> DisplaySlot.TEAM_GREEN
        "AQUA" -> DisplaySlot.TEAM_AQUA
        "RED" -> DisplaySlot.TEAM_RED
        "LIGHT_PURPLE" -> DisplaySlot.TEAM_LIGHT_PURPLE
        "YELLOW" -> DisplaySlot.TEAM_YELLOW
        "WHITE" -> DisplaySlot.TEAM_WHITE
        else -> null
    }
}