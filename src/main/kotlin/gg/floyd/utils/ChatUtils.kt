package gg.floyd.utils

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

private const val DEFAULT_PREFIX = "FloydAddons"

fun modMessage(message: Any?, prefix: String = DEFAULT_PREFIX, chatStyle: Style? = null) {
    val text = prefixComponent(prefix).append(Component.literal(message.toString()))
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui.chat.addClientSystemMessage(text) }
}

fun modMessage(message: Component, prefix: String = DEFAULT_PREFIX, chatStyle: Style? = null) {
    val text = prefixComponent(prefix).append(message)
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui.chat.addClientSystemMessage(text) }
}

/**
 * Standard module toggle line. Prints "<name> §aenabled." or "<name> §cdisabled."
 * behind the default "FloydAddons »" prefix so every module formats toggles identically.
 */
fun moduleToggle(name: String, enabled: Boolean) =
    modMessage(name + if (enabled) " §aenabled." else " §cdisabled.")

/**
 * Green status line for successful actions.
 */
fun successMessage(message: Any?) = modMessage("§a$message")

/**
 * Neutral informational status line.
 */
fun infoMessage(message: Any?) = modMessage(message)

/**
 * Red status line for failures/errors.
 */
fun errorMessage(message: Any?) = modMessage("§c$message")

private fun prefixComponent(prefix: String): MutableComponent =
    if (prefix == DEFAULT_PREFIX) defaultPrefix(prefix)
    else Component.literal(prefix)

private fun defaultPrefix(text: String): MutableComponent =
    Component.literal(text)
        .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
