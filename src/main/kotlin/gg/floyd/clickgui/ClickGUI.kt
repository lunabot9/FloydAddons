package gg.floyd.clickgui

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.features.Category
import gg.floyd.features.ModuleManager
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.Color
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.HoverHandler
import gg.floyd.utils.ui.animations.EaseOutAnimation
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.awt.Desktop
import java.net.URI
import kotlin.math.sign
import gg.floyd.utils.ui.mouseX as floydMouseX
import gg.floyd.utils.ui.mouseY as floydMouseY

/**
 * Renders all the modules.
 */
object ClickGUI : Screen(Component.literal("Click GUI")) {

    private val panels: ArrayList<Panel> = arrayListOf<Panel>().apply {
        ClickGUIModule.ensurePanelPositionsFit()
        for (category in Category.categories.values.filter { ModuleManager.modulesByCategory.containsKey(it) }) add(Panel(category))
    }

    private var openAnim = EaseOutAnimation(500)
    val gray38 = Color(38, 38, 38)
    val gray26 = Color(26, 26, 26)

    private const val githubUrl = "https://github.com/lunabot9/FloydAddons"
    private const val discordUrl = "https://discord.gg/FLOYD"
    // Clickable-link bounds (x, y, w, h) in the scaled GUI space, refreshed every frame by drawCommunity.
    private var communityGithubBounds = floatArrayOf(0f, 0f, 0f, 0f)
    private var communityDiscordBounds = floatArrayOf(0f, 0f, 0f, 0f)

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        NVGPIPRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            val scaledMouseX = floydMouseX / ClickGUIModule.getStandardGuiScale()
            val scaledMouseY = floydMouseY / ClickGUIModule.getStandardGuiScale()
            val searchBarX = mc.window.screenWidth / (2f * ClickGUIModule.getStandardGuiScale()) - 175f
            val searchBarY = (mc.window.screenHeight - 110f) / ClickGUIModule.getStandardGuiScale() - 20f

            NVGRenderer.scale(ClickGUIModule.getStandardGuiScale(), ClickGUIModule.getStandardGuiScale())

            drawTitle(searchBarX, searchBarY, 22f)
            SearchBar.draw(searchBarX, searchBarY, scaledMouseX, scaledMouseY)
            drawCommunity(searchBarX, searchBarY, scaledMouseX, scaledMouseY)

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
            desc.render()
            NVGRenderer.resetTextLayers()
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
        val scaledMouseX = floydMouseX / ClickGUIModule.getStandardGuiScale()
        val scaledMouseY = floydMouseY / ClickGUIModule.getStandardGuiScale()
        if (mouseButtonEvent.button() == 0 && hitCommunityLink(scaledMouseX, scaledMouseY)) return true
        SearchBar.mouseClicked(scaledMouseX, scaledMouseY, mouseButtonEvent)
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
        openAnim.start()
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

    /**
     * Renders "Join the Floyd Addons Community" below the search bar with two markdown-style text
     * links — "github" and ".gg/FLOYD" — that open the repo / Discord. Link bounds are refreshed each
     * frame for [hitCommunityLink] to hit-test in [mouseClicked].
     */
    private fun drawCommunity(searchBarX: Float, searchBarY: Float, mouseX: Float, mouseY: Float) {
        val centerX = searchBarX + 175f
        val size = 15f
        val header = "Join the Floyd Addons Community"
        val headerWidth = NVGRenderer.textWidth(header, size, NVGRenderer.defaultFont)
        val headerY = searchBarY + 40f + 8f
        NVGRenderer.text(header, centerX - headerWidth / 2f, headerY, size, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        val gap = NVGRenderer.textWidth("    ", size, NVGRenderer.defaultFont)
        val githubWidth = NVGRenderer.textWidth("github", size, NVGRenderer.defaultFont)
        val discordWidth = NVGRenderer.textWidth(".gg/FLOYD", size, NVGRenderer.defaultFont)
        val rowY = headerY + size + 3f
        val githubX = centerX - (githubWidth + gap + discordWidth) / 2f
        val discordX = githubX + githubWidth + gap

        communityGithubBounds = floatArrayOf(githubX, rowY, githubWidth, size)
        communityDiscordBounds = floatArrayOf(discordX, rowY, discordWidth, size)

        val githubColor = if (inBounds(communityGithubBounds, mouseX, mouseY)) Colors.WHITE.rgba else ClickGUIModule.guiAccentColor(0f)
        val discordColor = if (inBounds(communityDiscordBounds, mouseX, mouseY)) Colors.WHITE.rgba else ClickGUIModule.guiAccentColor(0.5f)
        NVGRenderer.text("github", githubX, rowY, size, githubColor, NVGRenderer.defaultFont)
        NVGRenderer.text(".gg/FLOYD", discordX, rowY, size, discordColor, NVGRenderer.defaultFont)
    }

    private fun inBounds(b: FloatArray, x: Float, y: Float): Boolean =
        x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3]

    /** Opens the community link under the cursor, if any. Returns true if a link was hit. */
    private fun hitCommunityLink(x: Float, y: Float): Boolean {
        val url = when {
            inBounds(communityGithubBounds, x, y) -> githubUrl
            inBounds(communityDiscordBounds, x, y) -> discordUrl
            else -> return false
        }
        runCatching { Desktop.getDesktop().browse(URI(url)) }
        return true
    }

    /** Renders the "FloydAddons" title above the search bar, tinted with the GUI accent (chroma-cycling per character). */
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
