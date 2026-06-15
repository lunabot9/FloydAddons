package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class FloydMainMenuScreen : Screen(Component.literal("FloydAddons")) {
    private val buttons = listOf(
        MenuButton("Singleplayer", onClick = { screen -> FloydAddonsMod.mc.setScreen(SelectWorldScreen(screen)) }),
        MenuButton("Multiplayer", onClick = { screen -> FloydAddonsMod.mc.setScreen(JoinMultiplayerScreen(screen)) }),
        MenuButton("Options", onClick = { screen -> FloydAddonsMod.mc.setScreen(OptionsScreen(screen, FloydAddonsMod.mc.options)) }),
        MenuButton("Quit", onClick = { _ -> FloydAddonsMod.mc.stop() })
    )

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val baseX = context.guiWidth() * 0.12f
        val titleY = context.guiHeight() * 0.14f
        val buttonStartY = titleY + 78f
        val version = "v${FloydAddonsMod.MOD_VERSION}"

        var y = buttonStartY
        for (button in buttons) {
            button.layout(baseX, y, 30f)
            y += 50f
        }

        NVGPIPRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            FloydMenuScreenStyling.drawWord("FloydAddons", baseX, titleY, 42f, 1f)
            val titleWidth = NVGRenderer.textWidth("FloydAddons", 42f, NVGRenderer.defaultFont)
            NVGRenderer.text(version, baseX + titleWidth + 12f, titleY + 24f, 13f, 0xD4D4D4D4.toInt(), NVGRenderer.defaultFont)
            for (button in buttons) {
                val hovered = button.contains(mouseX.toFloat(), mouseY.toFloat())
                val color = if (hovered) 0xFFFFFFFF.toInt() else 0xD6D6D6D6.toInt()
                NVGRenderer.text(button.label, button.x, button.y, 30f, color, NVGRenderer.defaultFont)
            }
        }
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (mouseButtonEvent.button() != 0) return super.mouseClicked(mouseButtonEvent, bl)
        val x = guiMouseX(mouseButtonEvent.x())
        val y = guiMouseY(mouseButtonEvent.y())
        val hit = buttons.firstOrNull { it.contains(x, y) } ?: return super.mouseClicked(mouseButtonEvent, bl)
        hit.onClick(this)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == 256) return true
        return super.keyPressed(event)
    }

    override fun shouldCloseOnEsc(): Boolean = false
    override fun isPauseScreen(): Boolean = false
    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) = Unit

    private fun guiMouseX(rawX: Double): Float {
        val screenWidth = minecraft.window.screenWidth
        return if (screenWidth == 0) rawX.toFloat() else (rawX * this.width / screenWidth).toFloat()
    }

    private fun guiMouseY(rawY: Double): Float {
        val screenHeight = minecraft.window.screenHeight
        return if (screenHeight == 0) rawY.toFloat() else (rawY * this.height / screenHeight).toFloat()
    }

    private data class MenuButton(
        val label: String,
        val onClick: (FloydMainMenuScreen) -> Unit,
        var x: Float = 0f,
        var y: Float = 0f,
        var width: Float = 0f,
        var height: Float = 0f
    ) {
        fun layout(x: Float, y: Float, size: Float) {
            this.x = x
            this.y = y
            this.width = NVGRenderer.textWidth(label, size, NVGRenderer.defaultFont)
            this.height = size + 10f
        }

        fun contains(mouseX: Float, mouseY: Float): Boolean =
            mouseX in (x - 6f)..(x + width + 8f) && mouseY in (y - 6f)..(y + height)
    }
}
