package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.HudManager
import gg.floyd.features.impl.render.FloydPanelStyle
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics

/** Renderer and input geometry for the HUD-only calculator. */
object FloydCalculatorScreen {
        internal const val PANEL_WIDTH = 330f
        internal const val PANEL_HEIGHT = 494f
        internal const val DRAG_BAR_HEIGHT = 72f

        private const val PANEL_WIDTH_INT = 330
        private const val PANEL_HEIGHT_INT = 494
        private const val NUMBER_BUTTON_COLOR = 0xF0353535.toInt()
        private const val FUNCTION_BUTTON_COLOR = 0xF0272727.toInt()
        private const val OPERATOR_BUTTON_COLOR = 0xF02E2E2E.toInt()
        private const val TEXT_PRIMARY = 0xFFF4F4F4.toInt()
        private const val TEXT_MUTED = 0xFFAAAAAA.toInt()
        private const val HISTORY_DRAWER_HEIGHT = 300f
        private const val HISTORY_ENTRY_HEIGHT = 54f

        private var historyOpen = false
        private var historyProgress = 0f
        private var lastHistoryAnimationNanos = System.nanoTime()

        private val BUTTON_ROWS = listOf(
            listOf(
                ButtonSlot(CalculatorKey.PERCENT, 0), ButtonSlot(CalculatorKey.CE, 1),
                ButtonSlot(CalculatorKey.C, 2), ButtonSlot(CalculatorKey.BACKSPACE, 3),
            ),
            listOf(ButtonSlot(CalculatorKey.SQUARE, 0, 3), ButtonSlot(CalculatorKey.DIVIDE, 3)),
            listOf(
                ButtonSlot(CalculatorKey.SEVEN, 0), ButtonSlot(CalculatorKey.EIGHT, 1),
                ButtonSlot(CalculatorKey.NINE, 2), ButtonSlot(CalculatorKey.MULTIPLY, 3),
            ),
            listOf(
                ButtonSlot(CalculatorKey.FOUR, 0), ButtonSlot(CalculatorKey.FIVE, 1),
                ButtonSlot(CalculatorKey.SIX, 2), ButtonSlot(CalculatorKey.SUBTRACT, 3),
            ),
            listOf(
                ButtonSlot(CalculatorKey.ONE, 0), ButtonSlot(CalculatorKey.TWO, 1),
                ButtonSlot(CalculatorKey.THREE, 2), ButtonSlot(CalculatorKey.ADD, 3),
            ),
            listOf(
                ButtonSlot(CalculatorKey.ZERO, 0, 2), ButtonSlot(CalculatorKey.DECIMAL, 2),
                ButtonSlot(CalculatorKey.EQUALS, 3),
            ),
        )

        internal fun drawCalculatorHud(context: GuiGraphics): Pair<Int, Int> {
            val hud = FloydCalculator.calculatorHud
            val panelX = hud.visibleX(PANEL_WIDTH * hud.scale)
            val panelY = hud.visibleY(PANEL_HEIGHT * hud.scale)
            val mouseX = (HudManager.renderSpaceMouseX() - panelX) / hud.scale
            val mouseY = (HudManager.renderSpaceMouseY() - panelY) / hud.scale
            val multiplier = FloydAddonsMod.mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio()
            val target = FloydPanelStyle.PanelTarget.CALCULATOR

            NVGPIPRenderer.draw(
                context,
                0,
                0,
                PANEL_WIDTH_INT,
                PANEL_HEIGHT_INT,
                multiplier,
                localCoordinates = true,
                backdropBlur = HudPanel.nvgBlur(PANEL_WIDTH_INT, PANEL_HEIGHT_INT, target),
            ) {
                drawCalculator(mouseX, mouseY)
                NVGRenderer.resetTextLayers()
            }
            return PANEL_WIDTH_INT to PANEL_HEIGHT_INT
        }

        internal fun pressAt(mouseX: Float, mouseY: Float): Boolean {
            if (historyButtonBounds().contains(mouseX, mouseY)) {
                historyOpen = !historyOpen
                lastHistoryAnimationNanos = System.nanoTime()
                return true
            }

            val drawerTop = historyDrawerTop()
            if (historyProgress > 0.01f && mouseY >= drawerTop && mouseX in 0f..PANEL_WIDTH) {
                val inset = panelInset()
                val clearBounds = Bounds(PANEL_WIDTH - inset - 58f, drawerTop + 12f, 58f, 28f)
                if (clearBounds.contains(mouseX, mouseY) && FloydCalculator.engine.history.isNotEmpty()) {
                    FloydCalculator.engine.clearHistory()
                    return true
                }
                FloydCalculator.engine.history.take(4).forEachIndexed { index, entry ->
                    val entryBounds = Bounds(inset, drawerTop + 52f + index * HISTORY_ENTRY_HEIGHT, PANEL_WIDTH - inset * 2f, HISTORY_ENTRY_HEIGHT - 4f)
                    if (entryBounds.contains(mouseX, mouseY)) {
                        FloydCalculator.engine.recallHistory(entry)
                        historyOpen = false
                        lastHistoryAnimationNanos = System.nanoTime()
                        return true
                    }
                }
                return true
            }

            val button = calculatorButtons().firstOrNull { it.bounds.contains(mouseX, mouseY) } ?: return false
            press(button.key)
            return true
        }

        private fun drawCalculator(mouseX: Float, mouseY: Float) {
            val engine = FloydCalculator.engine
            val font = NVGRenderer.activeFont()
            val target = FloydPanelStyle.PanelTarget.CALCULATOR
            val hud = FloydCalculator.calculatorHud
            // Resolve one Panel Style color for the entire calculator. Chroma/fade may change this
            // uniform color over time, but individual controls never receive different phases.
            val accent = HudPanel.accentColor(
                FloydPanelStyle.borderColorFor(target),
                HudPanel.hudRotationOffset(hud.x, hud.y, 0.38f),
            )
            val panelFill = FloydPanelStyle.backgroundColorFor(target).rgba
            val historyFill = compositeOver(panelFill, 0xFF202020.toInt())
            val panelRadius = FloydPanelStyle.cornerRadiusFor(target).toFloat().coerceAtLeast(0f)
            val panelStroke = FloydPanelStyle.borderWidthFor(target).toFloat().coerceAtLeast(0f)
            val inset = panelInset()

            NVGRenderer.rect(0f, 0f, PANEL_WIDTH, PANEL_HEIGHT, panelFill, panelRadius)
            drawCalculatorOutline(accent, panelRadius, panelStroke)

            NVGRenderer.text("Calculator", inset + 4f, 15f, 17f, accent, font)
            NVGRenderer.text("Standard", inset + 4f, 45f, 21f, TEXT_PRIMARY, font)
            drawHistoryButton(mouseX, mouseY, accent, panelFill)

            if (engine.expression.isNotEmpty()) {
                drawRightAligned(engine.expression, 78f, 13f, TEXT_MUTED)
            }
            val displaySize = when {
                engine.display.length <= 11 -> 46f
                engine.display.length <= 16 -> 35f
                else -> 25f
            }
            drawRightAligned(engine.display, 99f, displaySize, TEXT_PRIMARY)
            NVGRenderer.line(inset, 151f, PANEL_WIDTH - inset, 151f, 1f, withAlpha(accent, 150))

            for (button in calculatorButtons()) {
                val hovered = button.bounds.contains(mouseX, mouseY)
                val base = when (button.kind) {
                    ButtonKind.NUMBER -> NUMBER_BUTTON_COLOR
                    ButtonKind.FUNCTION -> FUNCTION_BUTTON_COLOR
                    ButtonKind.OPERATOR -> OPERATOR_BUTTON_COLOR
                }
                val fill = when {
                    hovered -> blend(base, accent, 0.28f)
                    else -> base
                }
                NVGRenderer.rect(button.bounds.x, button.bounds.y, button.bounds.width, button.bounds.height, fill, 7f)
                NVGRenderer.textCentered(button.key.label, button.bounds.x, button.bounds.y, button.bounds.width, button.bounds.height, button.key.textSize, TEXT_PRIMARY, font)
            }

            val progress = updateHistoryProgress()
            if (progress > 0.001f) {
                drawHistoryDrawer(mouseX, mouseY, progress, accent, font, historyFill, panelRadius)
                // The drawer covers the original outline. Repaint it last so the history
                // panel follows the calculator's curved bottom and retains both side borders.
                drawCalculatorOutline(accent, panelRadius, panelStroke)
            }
        }

        private fun drawCalculatorOutline(color: Int, radius: Float, stroke: Float) {
            if (stroke <= 0f) return
            val bounds = calculatorOutlineBounds(PANEL_WIDTH, PANEL_HEIGHT, stroke)
            NVGRenderer.hollowRect(
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                stroke,
                color,
                (radius - bounds.x).coerceAtLeast(0f),
            )
        }

        private fun drawHistoryButton(mouseX: Float, mouseY: Float, accent: Int, panelFill: Int) {
            val bounds = historyButtonBounds()
            if (bounds.contains(mouseX, mouseY)) {
                NVGRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height, FUNCTION_BUTTON_COLOR, 7f)
            }
            val centerX = bounds.x + bounds.width / 2f
            val centerY = bounds.y + bounds.height / 2f
            NVGRenderer.circle(centerX, centerY, 9f, accent)
            NVGRenderer.circle(centerX, centerY, 7f, panelFill)
            NVGRenderer.line(centerX, centerY, centerX, centerY - 4.5f, 1.7f, accent)
            NVGRenderer.line(centerX, centerY, centerX + 4f, centerY + 2.5f, 1.7f, accent)
        }

        private fun drawHistoryDrawer(
            mouseX: Float,
            mouseY: Float,
            progress: Float,
            accent: Int,
            font: gg.floyd.utils.ui.rendering.Font,
            panelFill: Int,
            panelRadius: Float,
        ) {
            val drawerTop = PANEL_HEIGHT - HISTORY_DRAWER_HEIGHT * smoothStep(progress)
            NVGRenderer.pushScissor(0f, 0f, PANEL_WIDTH, PANEL_HEIGHT)
            NVGRenderer.rect(0f, drawerTop, PANEL_WIDTH, HISTORY_DRAWER_HEIGHT, panelFill, panelRadius)
            // Fill the rounded top corners back in; only the bottom follows the outer curve.
            NVGRenderer.rect(0f, drawerTop, PANEL_WIDTH, panelRadius, panelFill, 0f)
            NVGRenderer.line(2f, drawerTop, PANEL_WIDTH - 2f, drawerTop, 2f, accent)
            val inset = panelInset()
            NVGRenderer.text("History", inset + 4f, drawerTop + 17f, 18f, TEXT_PRIMARY, font)

            val history = FloydCalculator.engine.history
            val clearBounds = Bounds(PANEL_WIDTH - inset - 58f, drawerTop + 12f, 58f, 28f)
            if (history.isNotEmpty()) {
                if (clearBounds.contains(mouseX, mouseY)) {
                    NVGRenderer.rect(clearBounds.x, clearBounds.y, clearBounds.width, clearBounds.height, FUNCTION_BUTTON_COLOR, 6f)
                }
                NVGRenderer.textCentered("Clear", clearBounds.x, clearBounds.y, clearBounds.width, clearBounds.height, 13f, accent, font)
            }

            if (history.isEmpty()) {
                NVGRenderer.textCentered("There's no history yet", inset, drawerTop + 115f, PANEL_WIDTH - inset * 2f, 30f, 15f, TEXT_MUTED, font)
            } else {
                history.take(4).forEachIndexed { index, entry ->
                    val bounds = Bounds(inset, drawerTop + 52f + index * HISTORY_ENTRY_HEIGHT, PANEL_WIDTH - inset * 2f, HISTORY_ENTRY_HEIGHT - 4f)
                    if (bounds.contains(mouseX, mouseY)) {
                        NVGRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height, FUNCTION_BUTTON_COLOR, 7f)
                    }
                    NVGRenderer.text(entry.expression, bounds.x + 8f, bounds.y + 6f, 12f, TEXT_MUTED, font)
                    val resultWidth = NVGRenderer.textWidth(entry.result, 20f, font)
                    NVGRenderer.text(entry.result, bounds.x + bounds.width - resultWidth - 8f, bounds.y + 25f, 20f, TEXT_PRIMARY, font)
                }
            }
            NVGRenderer.popScissor()
        }

        private fun updateHistoryProgress(): Float {
            val now = System.nanoTime()
            val deltaSeconds = ((now - lastHistoryAnimationNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastHistoryAnimationNanos = now
            val target = if (historyOpen) 1f else 0f
            val step = deltaSeconds * 7f
            historyProgress = when {
                historyProgress < target -> (historyProgress + step).coerceAtMost(target)
                historyProgress > target -> (historyProgress - step).coerceAtLeast(target)
                else -> historyProgress
            }
            return historyProgress
        }

        private fun historyDrawerTop(): Float = PANEL_HEIGHT - HISTORY_DRAWER_HEIGHT * smoothStep(historyProgress)

        private fun smoothStep(value: Float): Float {
            val t = value.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private fun drawRightAligned(text: String, y: Float, size: Float, color: Int) {
            val font = NVGRenderer.activeFont()
            NVGRenderer.text(text, PANEL_WIDTH - panelInset() - 4f - NVGRenderer.textWidth(text, size, font), y, size, color, font)
        }

        private fun calculatorButtons(): List<CalculatorButton> {
            val left = panelInset()
            val top = 160f
            val gap = 4f
            val width = (PANEL_WIDTH - 28f - gap * 3f) / 4f
            val height = 50f
            return BUTTON_ROWS.flatMapIndexed { row, slots ->
                slots.map { slot ->
                    val key = slot.key
                    CalculatorButton(
                        key,
                        when {
                            key == CalculatorKey.EQUALS -> ButtonKind.NUMBER
                            key.operation != null -> ButtonKind.OPERATOR
                            key.digit != null || key == CalculatorKey.DECIMAL -> ButtonKind.NUMBER
                            else -> ButtonKind.FUNCTION
                        },
                        Bounds(
                            left + slot.column * (width + gap),
                            top + row * (height + gap),
                            width * slot.span + gap * (slot.span - 1),
                            height,
                        ),
                    )
                }
            }
        }

        private fun panelInset(): Float =
            FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.CALCULATOR).coerceIn(0, 16) + 8f

        private fun historyButtonBounds(): Bounds = Bounds(PANEL_WIDTH - panelInset() - 30f, 10f, 30f, 30f)

        private fun press(key: CalculatorKey) {
            val engine = FloydCalculator.engine
            key.digit?.let { engine.digit(it); return }
            key.operation?.let { engine.binary(it); return }
            when (key) {
                CalculatorKey.PERCENT -> engine.percent()
                CalculatorKey.CE -> engine.clearEntry()
                CalculatorKey.C -> engine.clearAll()
                CalculatorKey.BACKSPACE -> engine.backspace()
                CalculatorKey.SQUARE -> engine.square()
                CalculatorKey.DECIMAL -> engine.decimal()
                CalculatorKey.EQUALS -> engine.equals()
                else -> Unit
            }
        }

        private data class Bounds(val x: Float, val y: Float, val width: Float, val height: Float) {
            fun contains(px: Float, py: Float): Boolean = px in x..(x + width) && py in y..(y + height)
        }

        private data class CalculatorButton(val key: CalculatorKey, val kind: ButtonKind, val bounds: Bounds)
        private data class ButtonSlot(val key: CalculatorKey, val column: Int, val span: Int = 1)
        private enum class ButtonKind { NUMBER, FUNCTION, OPERATOR }

        private enum class CalculatorKey(
            val label: String,
            val digit: Int? = null,
            val operation: CalculatorBinaryOperation? = null,
            val textSize: Float = 19f,
        ) {
            PERCENT("%"), CE("CE", textSize = 14f), C("C"), BACKSPACE("⌫"),
            SQUARE("x²"), DIVIDE("÷", operation = CalculatorBinaryOperation.DIVIDE, textSize = 23f),
            SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9), MULTIPLY("×", operation = CalculatorBinaryOperation.MULTIPLY, textSize = 23f),
            FOUR("4", 4), FIVE("5", 5), SIX("6", 6), SUBTRACT("−", operation = CalculatorBinaryOperation.SUBTRACT, textSize = 23f),
            ONE("1", 1), TWO("2", 2), THREE("3", 3), ADD("+", operation = CalculatorBinaryOperation.ADD, textSize = 23f),
            ZERO("0", 0), DECIMAL("."), EQUALS("=", textSize = 23f),
        }

        private fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

        private fun blend(from: Int, to: Int, amount: Float): Int {
            val t = amount.coerceIn(0f, 1f)
            fun channel(shift: Int): Int {
                val a = (from ushr shift) and 0xFF
                val b = (to ushr shift) and 0xFF
                return (a + (b - a) * t).toInt().coerceIn(0, 255)
            }
            return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        /** Makes a popup surface opaque while preserving the configured panel tint and opacity. */
        private fun compositeOver(foreground: Int, background: Int): Int {
            val alpha = ((foreground ushr 24) and 0xFF) / 255f
            fun channel(shift: Int): Int {
                val front = (foreground ushr shift) and 0xFF
                val back = (background ushr shift) and 0xFF
                return (back + (front - back) * alpha).toInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
}

internal data class CalculatorOutlineBounds(val x: Float, val y: Float, val width: Float, val height: Float)

internal fun calculatorOutlineBounds(width: Float, height: Float, stroke: Float): CalculatorOutlineBounds {
    // Keep one half-pixel of antialiasing inside the PIP. A stroke centred exactly on the texture's
    // right/bottom boundary loses those edge samples when the PIP is composited.
    val inset = stroke.coerceAtLeast(0f) * 0.5f + 1f
    return CalculatorOutlineBounds(inset, inset, width - inset * 2f, height - inset * 2f)
}
