package com.odtheking.odin.utils

import com.odtheking.odin.FloydAddonsMod.mc
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

fun modMessage(message: Any?, prefix: String = "§3FloydAddons §8»§r ", chatStyle: Style? = null) {
    val text = Component.literal("$prefix$message")
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui.chat.addMessage(text) }
}

fun modMessage(message: Component, prefix: String = "§3FloydAddons §8»§r ", chatStyle: Style? = null) {
    val text = Component.literal(prefix).append(message)
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui.chat.addMessage(text) }
}
