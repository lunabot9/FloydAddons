package gg.floyd.features.impl.misc

import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.clickgui.settings.impl.HudElement
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.events.ScreenEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.clickgui.HudManager
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

/** Calculator launcher and persistent HUD toggle. */
@AlwaysActive
object FloydCalculator : Module(
    name = "Calculator",
    key = null,
    category = Category.MISC,
    description = "Opens a ClickGUI-themed calculator with an optional persistent HUD.",
) {
    private val openKey by KeybindSetting(
        "Open Calc",
        GLFW.GLFW_KEY_UNKNOWN,
        desc = "Opens the calculator screen.",
    ).onPress(::openScreen)

    internal val engine = FloydCalculatorEngine()

    internal val calculatorHud: HudElement by HUD(
        "Keep Calc On Screen",
        "Drag the calculator by its title bar. Open chat to use it while playing.",
        toggleable = true,
        x = 40,
        y = 40,
        scale = 1f,
    ) { FloydCalculatorScreen.drawCalculatorHud(this) }.independentOfModule()

    private var dragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    init {
        HudSizeRegistry.register("Keep Calc On Screen") {
            FloydCalculatorScreen.PANEL_WIDTH.toInt() to FloydCalculatorScreen.PANEL_HEIGHT.toInt()
        }
        on<ScreenEvent.Render> {
            if (dragging && (screen is FloydCalculatorScreen || calculatorHud.enabled && screen is ChatScreen)) {
                updateDrag()
            }
        }
        on<ScreenEvent.MouseClick> {
            if (calculatorHud.enabled && screen is ChatScreen && handleMouseClick(click)) cancel()
        }
        on<ScreenEvent.MouseRelease> {
            if (calculatorHud.enabled && screen is ChatScreen && stopDragging()) cancel()
        }
    }

    @JvmStatic
    fun openScreen() {
        mc.setScreen(FloydCalculatorScreen())
    }

    @JvmStatic
    fun toggleHudVisibility() {
        calculatorHud.enabled = !calculatorHud.enabled
        ModuleManager.saveConfigurations()
    }

    override fun onKeybind() = openScreen()

    override fun onEnable() {
        openScreen()
        super.onEnable()
        toggle()
    }

    internal fun drawOnScreen(context: GuiGraphics) {
        context.pose().pushMatrix()
        context.pose().scale(1f / mc.window.guiScale, 1f / mc.window.guiScale)
        calculatorHud.draw(context, false)
        context.pose().popMatrix()
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
