package floydaddons.not.dogshit.client.clickgui

import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.ModuleManager
import floydaddons.not.dogshit.client.features.impl.render.ClickGUIModule
import floydaddons.not.dogshit.client.utils.Color
import floydaddons.not.dogshit.client.utils.Colors
import floydaddons.not.dogshit.client.utils.ui.HoverHandler
import floydaddons.not.dogshit.client.utils.ui.animations.EaseOutAnimation
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGPIPRenderer
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.sign
import floydaddons.not.dogshit.client.utils.ui.mouseX as odinMouseX
import floydaddons.not.dogshit.client.utils.ui.mouseY as odinMouseY

/**
 * Renders all the modules.
 */
object ClickGUI : Screen(Component.literal("Click GUI")) {

    private val panels: ArrayList<Panel> = arrayListOf()

    private var openAnim = EaseOutAnimation(500)
    val gray38 = Color(38, 38, 38)
    val gray26 = Color(26, 26, 26)

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        ensurePanels()
        if (!shouldUpdateGuiInteractions()) {
            clearInteractionState()
        }

        NVGPIPRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            val scaledMouseX = odinMouseX / ClickGUIModule.getStandardGuiScale()
            val scaledMouseY = odinMouseY / ClickGUIModule.getStandardGuiScale()
            val searchBarX = mc.window.screenWidth / (2f * ClickGUIModule.getStandardGuiScale()) - 175f
            val searchBarY = (mc.window.screenHeight - 110f) / ClickGUIModule.getStandardGuiScale() - 20f

            NVGRenderer.scale(ClickGUIModule.getStandardGuiScale(), ClickGUIModule.getStandardGuiScale())

            drawTitle(searchBarX, searchBarY, 22f)
            SearchBar.draw(searchBarX, searchBarY, scaledMouseX, scaledMouseY)

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
                if (panel != draggedPanel) panel.draw(scaledMouseX, scaledMouseY)
            }

            draggedPanel?.draw(scaledMouseX, scaledMouseY)

            desc.render()
        }
        super.render(context, mouseX, mouseY, deltaTicks)
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
        val scaledMouseX = odinMouseX / ClickGUIModule.getStandardGuiScale()
        val scaledMouseY = odinMouseY / ClickGUIModule.getStandardGuiScale()
        SearchBar.mouseClicked(scaledMouseX, scaledMouseY, mouseButtonEvent)
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].mouseClicked(scaledMouseX, scaledMouseY, mouseButtonEvent)) return true
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        SearchBar.mouseReleased()
        panels.forEach { it.mouseReleased(mouseButtonEvent) }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        SearchBar.keyTyped(characterEvent)
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].keyTyped(characterEvent)) return true
        }
        return super.charTyped(characterEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        SearchBar.keyPressed(keyEvent)
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].keyPressed(keyEvent)) return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun init() {
        rebuildPanels()
        openAnim.start()
        super.init()
    }

    override fun onClose() {
        clearInteractionState()
        saveConfigurationState()
        super.onClose()
    }

    override fun removed() {
        clearInteractionState()
        saveConfigurationState()
        super.removed()
    }

    private fun saveConfigurationState() {
        ModuleManager.saveConfigurations()
    }

    private fun clearInteractionState() {
        SearchBar.mouseReleased()
        panels.forEach { it.clearInteractionState() }
    }

    private fun ensurePanels() {
        val activeCategories = Category.categories.values.filter { category ->
            ModuleManager.modulesByCategory[category]?.any { it.visibleInGui } == true
        }
        if (panels.size != activeCategories.size) rebuildPanels()
    }

    private fun rebuildPanels() {
        ClickGUIModule.ensurePanelPositionsFit()
        panels.clear()
        for (category in Category.categories.values.filter { cat -> ModuleManager.modulesByCategory[cat]?.any { it.visibleInGui } == true }) {
            panels += Panel(category)
        }
    }

    private fun shouldUpdateGuiInteractions(): Boolean {
        if (!mc.isWindowActive) return false
        if (mc.window.screenWidth <= 0 || mc.window.screenHeight <= 0) return false
        return true
    }

    override fun isPauseScreen(): Boolean = false

    private var desc = Description("", 0f, 0f, HoverHandler(150))

    /** Sets the description without creating a new data class which isn't optimal */
    fun setDescription(text: String, x: Float, y: Float, hoverHandler: HoverHandler) {
        desc.text = text
        desc.x = x
        desc.y = y
        desc.hoverHandler = hoverHandler
    }

    data class Description(var text: String, var x: Float, var y: Float, var hoverHandler: HoverHandler) {

        fun render() {
            if (text.isEmpty() || hoverHandler.percent() < 100) return
            val area = NVGRenderer.wrappedTextBounds(text, 300f, 16f, NVGRenderer.defaultFont)
            NVGRenderer.rect(x, y, area[2] - area[0] + 16f, area[3] - area[1] + 16f, gray38.rgba, 5f)
            NVGRenderer.hollowRect(
                x,
                y,
                area[2] - area[0] + 16f,
                area[3] - area[1] + 16f,
                1.5f,
                ClickGUIModule.guiAccentColor(),
                5f
            )
            NVGRenderer.drawWrappedString(text, x + 8f, y + 8f, 300f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        }
    }

    private fun drawTitle(x: Float, searchBarY: Float, size: Float) {
        val title = "FloydAddons"
        val titleWidth = NVGRenderer.textWidth(title, size, NVGRenderer.defaultFont)
        var cursorX = x + 175f - titleWidth / 2f
        val titleY = searchBarY - size - 4f
        for (char in title) {
            val text = char.toString()
            val offset = 1f - ((cursorX - (x + 175f - titleWidth / 2f)) / titleWidth)
            NVGRenderer.text(text, cursorX, titleY, size, ClickGUIModule.guiAccentColor(offset), NVGRenderer.defaultFont)
            cursorX += NVGRenderer.textWidth(text, size, NVGRenderer.defaultFont)
        }
    }

    val movementImage = NVGRenderer.createImage("/assets/floydaddons/MovementIcon.svg")
    val hueImage = NVGRenderer.createImage("/assets/floydaddons/HueGradient.png")
    val chevronImage = NVGRenderer.createImage("/assets/floydaddons/chevron.svg")
}
