package com.odtheking.odin.utils

import com.odtheking.odin.FloydAddonsMod.mc
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

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
    if (prefix == DEFAULT_PREFIX) chromaPrefix(prefix)
    else Component.literal(prefix)

private fun chromaPrefix(text: String): MutableComponent {
    val now = System.currentTimeMillis()
    val prefix = Component.literal("")
    text.forEachIndexed { index, ch ->
        val hue = ((now / 24.0) + index * 17.0) % 360.0 / 360.0
        val rgb = java.awt.Color.HSBtoRGB(hue.toFloat(), 0.9f, 1f) and 0x00FFFFFF
        prefix.append(
            Component.literal(ch.toString()).withStyle { style ->
                style.withColor(TextColor.fromRgb(rgb))
            }
        )
    }
    return prefix.append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
}
