package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import gg.floyd.utils.ui.mouseX as floydMouseX
import gg.floyd.utils.ui.mouseY as floydMouseY
import net.minecraft.client.gui.*
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
        MenuButton("Singleplayer", ButtonStyle.PRIMARY, onClick = { screen -> FloydAddonsMod.mc.setScreen(SelectWorldScreen(screen)) }),
        MenuButton("Multiplayer", ButtonStyle.PRIMARY, onClick = { screen -> FloydAddonsMod.mc.setScreen(JoinMultiplayerScreen(screen)) }),
        MenuButton("Options", ButtonStyle.SECONDARY, onClick = { screen -> FloydAddonsMod.mc.setScreen(OptionsScreen(screen, FloydAddonsMod.mc.options, false)) }),
        MenuButton("Quit", ButtonStyle.SECONDARY, onClick = { _ -> FloydAddonsMod.mc.stop() })
    )

    override fun extractBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), 0xFF050913.toInt())
    }

    override fun extractRenderState(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks)

        val scale = ClickGUIModule.getStandardGuiScale()
        val time = (System.currentTimeMillis() % 1_200_000L) / 1000f
        layout(scale)
        val cursorX = floydMouseX / scale
        val cursorY = floydMouseY / scale

        NVGPIPRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            NVGRenderer.scale(scale, scale)
            FloydMenuVideoBackground.render(
                width = FloydAddonsMod.mc.window.screenWidth / scale,
                height = FloydAddonsMod.mc.window.screenHeight / scale,
                time = time
            )

            drawPanels(time, cursorX, cursorY)

            val titleWidth = NVGRenderer.textWidth(TITLE_TEXT, TITLE_SIZE, NVGRenderer.defaultFont)
            NVGRenderer.textImmediate(TITLE_TEXT, titleX - titleWidth * 0.5f, titleY, TITLE_SIZE, 0xFFD2D2D2.toInt(), NVGRenderer.defaultFont)

            for (button in buttons) {
                val hovered = button.contains(cursorX, cursorY)
                val color = when (button.style) {
                    ButtonStyle.PRIMARY -> if (hovered) 0xFFFFFFFF.toInt() else 0xE8ECECEC.toInt()
                    ButtonStyle.SECONDARY -> if (hovered) 0xFFFFFFFF.toInt() else 0xCFCFCFCF.toInt()
                }
                NVGRenderer.textImmediate(button.label, button.labelX, button.labelY, button.textSize, color, NVGRenderer.defaultFont)
            }

            NVGRenderer.textImmediate(
                "Floyd Addons v${FloydAddonsMod.MOD_VERSION}",
                footerX - NVGRenderer.textWidth("Floyd Addons v${FloydAddonsMod.MOD_VERSION}", FOOTER_SIZE, NVGRenderer.defaultFont) * 0.5f,
                footerY,
                FOOTER_SIZE,
                0x8ED4D4D4.toInt(),
                NVGRenderer.defaultFont
            )
        }
    }

    /** Recomputes button rectangles in the scaled-NVG coordinate space for the current window. */
    private fun layout(scale: Float) {
        val viewWidth = FloydAddonsMod.mc.window.screenWidth / scale
        val viewHeight = FloydAddonsMod.mc.window.screenHeight / scale

        val panelWidth = minOf(320f, viewWidth * 0.36f).coerceAtLeast(240f)
        val primaryHeight = 30f
        val primaryGap = 14f
        val primaryLeft = (viewWidth - panelWidth) * 0.5f
        titleX = primaryLeft + panelWidth * 0.5f

        val primaryTop = viewHeight * 0.44f
        titleY = primaryTop - TITLE_SIZE - 18f
        buttons[0].layout(primaryLeft, primaryTop, panelWidth, primaryHeight)
        buttons[1].layout(primaryLeft, primaryTop + primaryHeight + primaryGap, panelWidth, primaryHeight)

        val secondaryWidth = 66f
        val secondaryHeight = 22f
        val secondaryGap = 18f
        val secondaryTop = buttons[1].y + buttons[1].height + 16f
        val secondaryLeft = titleX - secondaryWidth - secondaryGap * 0.5f
        buttons[2].layout(secondaryLeft, secondaryTop, secondaryWidth, secondaryHeight)
        buttons[3].layout(secondaryLeft + secondaryWidth + secondaryGap, secondaryTop, secondaryWidth, secondaryHeight)

        footerX = titleX
        footerY = viewHeight - 26f
    }

    private fun drawPanels(time: Float, cursorX: Float, cursorY: Float) {
        for (button in buttons) {
            val hovered = button.contains(cursorX, cursorY)
            val pulse = ((kotlin.math.sin(time * 2.1f + button.phase) * 0.5f) + 0.5f)
            val fillAlpha = when (button.style) {
                ButtonStyle.PRIMARY -> if (hovered) 64 else (38 + pulse * 14f).toInt()
                ButtonStyle.SECONDARY -> if (hovered) 52 else (24 + pulse * 10f).toInt()
            }
            val borderGray = if (hovered) 236 else if (button.style == ButtonStyle.PRIMARY) 154 else 126
            drawBox(
                button.x,
                button.y,
                button.width,
                button.height,
                argb(fillAlpha, 255, 255, 255),
                argb(hovered.then(166, borderGray), borderGray, borderGray, borderGray)
            )
        }
    }

    private fun drawBox(x: Float, y: Float, width: Float, height: Float, fillColor: Int, borderColor: Int) {
        val radius = if (height >= 30f) 5f else 4f
        NVGRenderer.rect(x, y, width, height, fillColor, radius)
        NVGRenderer.hollowRect(x, y, width, height, 1.1f, borderColor, radius)
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

    private var titleX = 0f
    private var titleY = 0f
    private var footerX = 0f
    private var footerY = 0f

    private class MenuButton(
        val label: String,
        val style: ButtonStyle,
        val onClick: (FloydMainMenuScreen) -> Unit,
        var x: Float = 0f,
        var y: Float = 0f,
        var width: Float = 0f,
        var height: Float = 0f,
        var labelX: Float = 0f,
        var labelY: Float = 0f
    ) {
        val textSize: Float
            get() = if (style == ButtonStyle.PRIMARY) PRIMARY_TEXT_SIZE else SECONDARY_TEXT_SIZE

        val phase: Float = label.fold(0) { acc, c -> acc + c.code }.toFloat() * 0.011f

        fun layout(x: Float, y: Float, width: Float, height: Float) {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            val labelWidth = NVGRenderer.textWidth(label, textSize, NVGRenderer.defaultFont)
            labelX = x + (width - labelWidth) * 0.5f
            labelY = y + (height - textSize) * 0.5f + if (style == ButtonStyle.PRIMARY) 1f else 1.5f
        }

        fun contains(mouseX: Float, mouseY: Float): Boolean =
            mouseX in x..(x + width) && mouseY in y..(y + height)
    }

    private enum class ButtonStyle { PRIMARY, SECONDARY }

    private companion object {
        const val TITLE_SIZE = 44f
        const val PRIMARY_TEXT_SIZE = 16f
        const val SECONDARY_TEXT_SIZE = 10.5f
        const val FOOTER_SIZE = 12f
        const val TITLE_TEXT = "Floyd Addons"

        private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
            (alpha.coerceIn(0, 255) shl 24) or
                (red.coerceIn(0, 255) shl 16) or
                (green.coerceIn(0, 255) shl 8) or
                blue.coerceIn(0, 255)

        private fun Boolean.then(ifTrue: Int, ifFalse: Int): Int = if (this) ifTrue else ifFalse
    }
}
