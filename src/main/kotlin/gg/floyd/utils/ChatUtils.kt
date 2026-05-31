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
    mc.execute { mc.gui.chat.addMessage(text) }
}

fun modMessage(message: Component, prefix: String = DEFAULT_PREFIX, chatStyle: Style? = null) {
    val text = prefixComponent(prefix).append(message)
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui.chat.addMessage(text) }
}

private fun prefixComponent(prefix: String): MutableComponent =
    if (prefix == DEFAULT_PREFIX) defaultPrefix(prefix)
    else Component.literal(prefix)

private fun defaultPrefix(text: String): MutableComponent =
    Component.literal(text)
        .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
