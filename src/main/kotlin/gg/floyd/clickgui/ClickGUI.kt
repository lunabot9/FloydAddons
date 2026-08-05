package gg.floyd.clickgui

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.Color
import gg.floyd.utils.Color.Companion.brighter
import gg.floyd.utils.Color.Companion.darker
import gg.floyd.utils.Color.Companion.withAlpha
import gg.floyd.utils.ui.animations.EaseOutAnimation
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import gg.floyd.utils.render.nvgToGuiCoordinate
import net.minecraft.client.gui.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sign
import gg.floyd.utils.ui.mouseX as floydMouseX
import gg.floyd.utils.ui.mouseY as floydMouseY

/**
 * Renders all the modules.
 */
object ClickGUI : Screen(Component.literal("Click GUI")) {
    private val panels: ArrayList<Panel> = arrayListOf()

    private var openAnim = EaseOutAnimation(500)
    val gray38 = Color(38, 38, 38)
    val gray26 = Color(26, 26, 26)
    val oringoPanelRaised = Color(37, 37, 37)
    val oringoPanelBright = Color(52, 52, 52)
    val oringoTextMuted = Color(143, 143, 143)
    private val shadowStripe = Color(0, 0, 0, 40 / 255f)

    fun accent(alpha: Float = 1f): Int = Color(ClickGUIModule.guiAccentColor()).withAlpha(alpha).rgba
    fun accentDark(): Int = Color(accent()).darker().rgba
    fun accentBright(): Int = Color(accent()).brighter().rgba
    fun bodyBackground(): Int = gray26.rgba
    fun settingBackground(): Int = oringoPanelRaised.rgba
    fun settingBackgroundBright(): Int = oringoPanelBright.rgba
    fun shadowStripeColor(): Int = shadowStripe.rgba
    fun rowFill(active: Boolean): Int = if (active) accent() else bodyBackground()
    fun drawChrome(x: Float, y: Float, width: Float, height: Float, radius: Float, hovered: Boolean = false, accented: Boolean = false) {
        NVGRenderer.rect(x, y, width, height, if (hovered) settingBackgroundBright() else settingBackground(), radius)
    }

    override fun extractRenderState(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val guiScale = ClickGUIModule.getStandardGuiScale()
        val minecraftGuiScale = mc.window.guiScale.toFloat()
        val renderScale = ClickGUIModule.getClickGuiRenderScale()
        val bounds = computeRenderBounds(
            context.guiWidth(),
            context.guiHeight(),
            renderScale,
            minecraftGuiScale,
        )

        NVGPIPRenderer.draw(context, bounds.left, bounds.top, bounds.width, bounds.height) {
            NVGRenderer.withImmediateText {
                val scaledMouseX = floydMouseX / renderScale
                val scaledMouseY = floydMouseY / renderScale

                NVGRenderer.scale(renderScale, renderScale)

                if (openAnim.isAnimating()) {
                    val scale = openAnim.get(0f, 1f)

                    val centerX = context.guiWidth().toFloat()
                    val centerY = context.guiHeight().toFloat()
                    NVGRenderer.translate(centerX, centerY)
                    NVGRenderer.scale(scale, scale)
                    NVGRenderer.translate(-centerX, -centerY)
                }

                val draggedPanel = panels.firstOrNull { it.dragging }
                for (panel in panels) {
                    if (panel == draggedPanel) continue
                    // Per-panel text layer (D7 step 6 CORRECTION): bake everything queued so far —
                    // the GUI-level text above (layer 0) and lower panels' text — into the PIP slot
                    // BELOW this panel's shapes, so panels overlapping at rest occlude correctly.
                    NVGRenderer.nextTextLayer()
                    panel.draw(scaledMouseX, scaledMouseY)
                }

                // Topmost layers: dragged panel, then the tooltip — each behind its own boundary so
                // the dragged panel's replayed text bakes BELOW the tooltip's box (otherwise it would
                // composite over a live tooltip, the same bleed the per-panel boundaries fix at rest).
                // Empty layers skip their boundary, so this is free when nothing is dragged.
                NVGRenderer.nextTextLayer()
                draggedPanel?.draw(scaledMouseX, scaledMouseY)

                NVGRenderer.nextTextLayer()
                SearchBar.draw(context.guiWidth().toFloat(), context.guiHeight().toFloat(), scaledMouseX, scaledMouseY)

                NVGRenderer.nextTextLayer()
                NVGRenderer.resetTextLayers()
            }
        }
        super.extractRenderState(context, mouseX, mouseY, deltaTicks)
    }

    private fun computeRenderBounds(
        guiWidth: Int,
        guiHeight: Int,
        renderScale: Float,
        minecraftGuiScale: Float,
    ): RenderBounds {
        if (openAnim.isAnimating()) {
            return RenderBounds().apply {
                minX = 0f
                minY = 0f
                maxX = guiWidth.toFloat()
                maxY = guiHeight.toFloat()
            }
        }
        val bounds = RenderBounds()
        for (panel in panels) {
            val panelBounds = panel.predictedBounds("")
            bounds.include(panelBounds[0], panelBounds[1], panelBounds[2], panelBounds[3])
        }
        val footerBounds = SearchBar.predictedBounds(guiWidth.toFloat(), guiHeight.toFloat())
        bounds.include(footerBounds[0], footerBounds[1], footerBounds[2], footerBounds[3])
        return bounds
            .inflate(12f)
            .toGuiCoordinates(renderScale, minecraftGuiScale)
            .clampTo(guiWidth.toFloat(), guiHeight.toFloat())
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        val actualAmount = (verticalAmount.sign * 16).toInt()
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].handleScroll(actualAmount)) return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(
        mouseButtonEvent: MouseButtonEvent,
        bl: Boolean
    ): Boolean {
        val scaledMouseX = floydMouseX / ClickGUIModule.getClickGuiRenderScale()
        val scaledMouseY = floydMouseY / ClickGUIModule.getClickGuiRenderScale()
        if (SearchBar.mouseClicked(scaledMouseX, scaledMouseY, mouseButtonEvent)) return true
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].mouseClicked(scaledMouseX, scaledMouseY, mouseButtonEvent)) return true
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        SearchBar.mouseReleased()
        for (i in panels.size - 1 downTo 0) {
            panels[i].mouseReleased(mouseButtonEvent)
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (SearchBar.keyTyped(characterEvent)) return true
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].keyTyped(characterEvent)) return true
        }
        return super.charTyped(characterEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (SearchBar.keyPressed(keyEvent)) return true
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].keyPressed(keyEvent)) return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun init() {
        ClickGUIModule.ensurePanelPositionsFit()
        openAnim.start()
        panels.clear()
        panels.addAll(Panel.createActivePanels())
        super.init()
    }

    override fun onClose() {
        saveConfigurationState()
        super.onClose()
    }

    override fun removed() {
        saveConfigurationState()
        super.removed()
    }

    private fun saveConfigurationState() {
        for (panel in panels.filter { it.panelSetting.extended }.reversed()) {
            for (moduleButton in panel.moduleButtons.filter { it.extended }) {
                for (setting in moduleButton.representableSettings) {
                    if (setting is ColorSetting) setting.section = null
                    setting.listening = false
                }
            }
        }

        ModuleManager.saveConfigurations()
    }

    override fun isPauseScreen(): Boolean = false

    private class RenderBounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        val width: Int get() = maxOf(1, ceil(maxX - minX).toInt())
        val height: Int get() = maxOf(1, ceil(maxY - minY).toInt())
        val left: Int get() = floor(minX).toInt()
        val top: Int get() = floor(minY).toInt()

        fun include(x0: Float, y0: Float, x1: Float, y1: Float) {
            minX = minOf(minX, x0)
            minY = minOf(minY, y0)
            maxX = maxOf(maxX, x1)
            maxY = maxOf(maxY, y1)
        }

        fun inflate(padding: Float): RenderBounds {
            minX -= padding
            minY -= padding
            maxX += padding
            maxY += padding
            return this
        }

        fun toGuiCoordinates(renderScale: Float, minecraftGuiScale: Float): RenderBounds {
            minX = nvgToGuiCoordinate(minX, renderScale, minecraftGuiScale)
            minY = nvgToGuiCoordinate(minY, renderScale, minecraftGuiScale)
            maxX = nvgToGuiCoordinate(maxX, renderScale, minecraftGuiScale)
            maxY = nvgToGuiCoordinate(maxY, renderScale, minecraftGuiScale)
            return this
        }

        fun clampTo(maxWidth: Float, maxHeight: Float): RenderBounds {
            minX = minX.coerceIn(0f, maxWidth)
            minY = minY.coerceIn(0f, maxHeight)
            maxX = maxX.coerceIn(minX + 1f, maxWidth)
            maxY = maxY.coerceIn(minY + 1f, maxHeight)
            return this
        }
    }

    val movementImage = NVGRenderer.createImage("/assets/floydaddons/MovementIcon.svg")
    val hueImage = NVGRenderer.createImage("/assets/floydaddons/HueGradient.png")
    val chevronImage = NVGRenderer.createImage("/assets/floydaddons/chevron.svg")
}
