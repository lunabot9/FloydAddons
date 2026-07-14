package net.minecraft.client.gui

import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

typealias GuiGraphics = GuiGraphicsExtractor

fun GuiGraphics.drawString(font: Font, text: String, x: Int, y: Int, color: Int, shadow: Boolean = false): Int {
    text(font, text, x, y, color, shadow)
    return x + font.width(text)
}

fun GuiGraphics.drawString(font: Font, text: FormattedCharSequence, x: Int, y: Int, color: Int, shadow: Boolean = false): Int {
    text(font, text, x, y, color, shadow)
    return x + font.width(text)
}

fun GuiGraphics.drawString(font: Font, text: Component, x: Int, y: Int, color: Int, shadow: Boolean = false): Int {
    text(font, text, x, y, color, shadow)
    return x + font.width(text)
}

fun GuiGraphics.drawWordWrap(font: Font, text: Component, x: Int, y: Int, width: Int, color: Int) {
    textWithWordWrap(font, text, x, y, width, color)
}

fun GuiGraphics.renderItem(item: ItemStack, x: Int, y: Int) {
    item(item, x, y)
}

fun GuiGraphics.renderItem(item: ItemStack, x: Int, y: Int, seed: Int) {
    item(item, x, y, seed)
}

fun GuiGraphics.renderItem(entity: LivingEntity, item: ItemStack, x: Int, y: Int, seed: Int) {
    item(entity, item, x, y, seed)
}
