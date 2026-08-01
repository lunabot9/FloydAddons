package gg.floyd.features.impl.misc

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen

class FloydSelectWorldScreen(parent: Screen) : SelectWorldScreen(parent) {
    override fun extractBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), 0xFF050913.toInt())
    }

    override fun extractRenderState(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        FloydMenuVideoBackground.render(context)
        super.extractRenderState(context, mouseX, mouseY, partialTick)
    }
}
