package gg.floyd.features.impl.misc

import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.clickgui.settings.impl.HudElement
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.events.ScreenEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.clickgui.HudManager
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

/** HUD-only calculator controlled by the module toggle, keybind, or chat commands. */
object FloydCalculator : Module(
    name = "Calculator",
    key = null,
    category = Category.MISC,
    description = "Opens an on-screen calculator. It can also be opened with /calc or /calculator.",
) {
    private val openKey by KeybindSetting(
        "Open Calc",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Toggles the on-screen calculator.",
    ).onPress(::toggleVisibility)

    internal val engine = FloydCalculatorEngine()

    internal val calculatorHud: HudElement by HUD(
        "Calculator HUD",
        "Drag the calculator by its title bar. Open chat to use it while playing.",
        toggleable = false,
        x = 40,
        y = 40,
        scale = 1f,
    ) { FloydCalculatorScreen.drawCalculatorHud(this) }

    private var dragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    init {
        HudSizeRegistry.register("Calculator HUD") {
            FloydCalculatorScreen.PANEL_WIDTH.toInt() to FloydCalculatorScreen.PANEL_HEIGHT.toInt()
        }
        on<ScreenEvent.Render> {
            if (dragging && screen is ChatScreen) {
                updateDrag()
            }
        }
        on<ScreenEvent.MouseClick> {
            if (screen is ChatScreen && handleMouseClick(click)) cancel()
        }
        on<ScreenEvent.MouseRelease> {
            if (screen is ChatScreen && stopDragging()) cancel()
        }
    }

    @JvmStatic
    fun toggleVisibility() {
        toggle()
        ModuleManager.saveConfigurations()
    }

    override fun onDisable() {
        dragging = false
        super.onDisable()
    }

    internal fun handleMouseClick(click: MouseButtonEvent): Boolean {
        if (click.button() != 0) return false
        val mouseX = HudManager.renderSpaceMouseX()
        val mouseY = HudManager.renderSpaceMouseY()
        val panelX = calculatorHud.visibleX(FloydCalculatorScreen.PANEL_WIDTH * calculatorHud.scale)
        val panelY = calculatorHud.visibleY(FloydCalculatorScreen.PANEL_HEIGHT * calculatorHud.scale)
        val localX = (mouseX - panelX) / calculatorHud.scale
        val localY = (mouseY - panelY) / calculatorHud.scale

        if (FloydCalculatorScreen.pressAt(localX, localY)) return true
        if (localX in 0f..FloydCalculatorScreen.PANEL_WIDTH && localY in 0f..FloydCalculatorScreen.DRAG_BAR_HEIGHT) {
            dragging = true
            dragOffsetX = panelX - mouseX
            dragOffsetY = panelY - mouseY
            return true
        }
        return false
    }

    internal fun stopDragging(): Boolean {
        if (!dragging) return false
        dragging = false
        ModuleManager.saveConfigurations()
        return true
    }

    private fun updateDrag() {
        val width = FloydCalculatorScreen.PANEL_WIDTH * calculatorHud.scale
        val height = FloydCalculatorScreen.PANEL_HEIGHT * calculatorHud.scale
        calculatorHud.x = (HudManager.renderSpaceMouseX() + dragOffsetX)
            .coerceIn(0f, (mc.window.width - width).coerceAtLeast(0f)).toInt()
        calculatorHud.y = (HudManager.renderSpaceMouseY() + dragOffsetY)
            .coerceIn(0f, (mc.window.height - height).coerceAtLeast(0f)).toInt()
    }
}
