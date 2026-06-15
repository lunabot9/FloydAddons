package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import gg.floyd.utils.ui.mouseX as floydMouseX
import gg.floyd.utils.ui.mouseY as floydMouseY
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Floyd's custom title screen. Replaces the vanilla [net.minecraft.client.gui.screens.TitleScreen].
 *
 * Coordinate model mirrors [gg.floyd.clickgui.ClickGUI] exactly: layout and rendering happen in the
 * "window-pixel / [ClickGUIModule.getStandardGuiScale]" space (NVG drawn after a matching
 * `NVGRenderer.scale`), and hit-testing converts the raw cursor with the SAME scale
 * (`floydMouseX / scale`). The previous impl drew in that NVG space but hit-tested in MC's
 * gui-scaled space, so the button hitboxes were offset by the gui-scale factor and nothing clicked.
 */
class FloydMainMenuScreen : Screen(Component.literal("FloydAddons")) {
    private val buttons = listOf(
        MenuButton("Singleplayer", onClick = { screen -> FloydAddonsMod.mc.setScreen(SelectWorldScreen(screen)) }),
        MenuButton("Multiplayer", onClick = { screen -> FloydAddonsMod.mc.setScreen(JoinMultiplayerScreen(screen)) }),
        MenuButton("Options", onClick = { screen -> FloydAddonsMod.mc.setScreen(OptionsScreen(screen, FloydAddonsMod.mc.options)) }),
        MenuButton("Quit", onClick = { _ -> FloydAddonsMod.mc.stop() })
    )

    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        if (!FloydMenuVideoBackground.render(context)) {
            context.fill(0, 0, context.guiWidth(), context.guiHeight(), 0xFF000000.toInt())
        }
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.render(context, mouseX, mouseY, deltaTicks)

        val scale = ClickGUIModule.getStandardGuiScale()
        layout(scale)
        val cursorX = floydMouseX / scale
        val cursorY = floydMouseY / scale

        NVGPIPRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            NVGRenderer.scale(scale, scale)

            val titleX = buttons.first().x
            val titleY = titleTop
            FloydMenuScreenStyling.drawWord("FloydAddons", titleX, titleY, TITLE_SIZE, 1f)
            val titleWidth = NVGRenderer.textWidth("FloydAddons", TITLE_SIZE, NVGRenderer.defaultFont)
            NVGRenderer.text(
                "v${FloydAddonsMod.MOD_VERSION}",
                titleX + titleWidth + 12f,
                titleY + TITLE_SIZE * 0.55f,
                VERSION_SIZE,
                0xD4D4D4D4.toInt(),
                NVGRenderer.defaultFont
            )

            for (button in buttons) {
                val hovered = button.contains(cursorX, cursorY)
                val color = if (hovered) 0xFFFFFFFF.toInt() else 0xD6D6D6D6.toInt()
                NVGRenderer.text(button.label, button.x, button.y, BUTTON_SIZE, color, NVGRenderer.defaultFont)
            }
        }
    }

    /** Recomputes button rectangles in the scaled-NVG coordinate space for the current window. */
    private fun layout(scale: Float) {
        val viewWidth = FloydAddonsMod.mc.window.screenWidth / scale
        val viewHeight = FloydAddonsMod.mc.window.screenHeight / scale
        val baseX = viewWidth * 0.06f
        titleTop = viewHeight * 0.12f
        var y = titleTop + TITLE_SIZE + 34f
        for (button in buttons) {
            button.layout(baseX, y, BUTTON_SIZE)
            y += BUTTON_SIZE + 18f
        }
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (mouseButtonEvent.button() != 0) return super.mouseClicked(mouseButtonEvent, bl)
        val scale = ClickGUIModule.getStandardGuiScale()
        layout(scale)
        val x = floydMouseX / scale
        val y = floydMouseY / scale
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

    private var titleTop = 0f

    private class MenuButton(
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
            this.height = size
        }

        fun contains(mouseX: Float, mouseY: Float): Boolean =
            mouseX in (x - 6f)..(x + width + 10f) && mouseY in (y - 6f)..(y + height + 6f)
    }

    private companion object {
        const val TITLE_SIZE = 48f
        const val VERSION_SIZE = 15f
        const val BUTTON_SIZE = 34f
    }
}
