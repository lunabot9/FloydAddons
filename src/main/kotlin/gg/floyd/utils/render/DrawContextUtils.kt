package gg.floyd.utils.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.utils.Color
import gg.floyd.utils.Colors
import net.minecraft.client.gui.*
import net.minecraft.util.FormattedCharSequence

fun GuiGraphics.text(text: String, x: Int, y: Int, color: Color = Colors.WHITE, shadow: Boolean = true) {
    drawString(mc.font, text, x, y, color.rgba, shadow)
}

fun GuiGraphics.text(text: FormattedCharSequence, x: Int, y: Int, color: Color = Colors.WHITE, shadow: Boolean = true) {
    drawString(mc.font, text, x, y, color.rgba, shadow)
}

fun GuiGraphics.hollowFill(x: Int, y: Int, width: Int, height: Int, thickness: Int, color: Color) {
    fill(x, y, x + width, y + thickness, color.rgba)
    fill(x, y + height - thickness, x + width, y + height, color.rgba)
    fill(x, y + thickness, x + thickness, y + height - thickness, color.rgba)
    fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color.rgba)
}
