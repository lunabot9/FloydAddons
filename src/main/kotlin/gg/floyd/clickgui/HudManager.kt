package gg.floyd.clickgui

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.HudElement
import gg.floyd.clickgui.settings.impl.HudResizeCorner
import gg.floyd.clickgui.settings.impl.resizeHudFromCorner
import gg.floyd.features.ModuleManager
import gg.floyd.features.ModuleManager.hudSettingsCache
import gg.floyd.utils.Colors
import net.minecraft.client.gui.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import gg.floyd.utils.ui.activeMouseOverride
import org.lwjgl.glfw.GLFW
import kotlin.math.sign

object HudManager : Screen(Component.literal("HUD Manager")) {

    private var dragging: HudElement? = null
    private var resizing: ResizeGesture? = null

    private var deltaX = 0f
    private var deltaY = 0f

    /**
     * Mouse position in the same coordinate space HUD elements are positioned and rendered in.
     *
     * Both the editor (here) and the in-game HUD (ModuleManager.render) draw elements under
     * pose().scale(1 / guiScale) starting from the default gui-scaled pose, which maps one HUD
     * unit onto one framebuffer pixel. mc.mouseHandler.xpos()/ypos() are reported in logical
     * window points (range 0..screenWidth, see MouseHandler.getScaledXPos which divides by
     * getScreenWidth()), so we scale by the device-pixel ratio (width / screenWidth) to land in
     * framebuffer-pixel space. This is divergence-proof: both render and hit-test are anchored to
     * the framebuffer, independent of guiScale (auto or fixed) and devicePixelRatio.
     *
     * When the local-control test harness supplies a synthetic mouse override, that point is in
     * standard gui-scaled space (see FloydLocalControl.controlMousePoint), so it is converted with
     * the gui-scaled -> framebuffer ratio (width / guiScaledWidth) instead.
     */
    fun renderSpaceMouseX(): Float {
        activeMouseOverride()?.let { (overrideX, _) ->
            val guiScaledWidth = mc.window.guiScaledWidth
            return if (guiScaledWidth == 0) overrideX else overrideX * mc.window.width / guiScaledWidth
        }
        val screenWidth = mc.window.screenWidth
        if (screenWidth == 0) return mc.mouseHandler.xpos().toFloat()
        return (mc.mouseHandler.xpos() * mc.window.width / screenWidth).toFloat()
    }

    fun renderSpaceMouseY(): Float {
        activeMouseOverride()?.let { (_, overrideY) ->
            val guiScaledHeight = mc.window.guiScaledHeight
            return if (guiScaledHeight == 0) overrideY else overrideY * mc.window.height / guiScaledHeight
        }
        val screenHeight = mc.window.screenHeight
        if (screenHeight == 0) return mc.mouseHandler.ypos().toFloat()
        return (mc.mouseHandler.ypos() * mc.window.height / screenHeight).toFloat()
    }

    override fun init() {
        for (hud in hudSettingsCache) {
            clampHudToScreen(hud)
        }
        super.init()
    }

    /**
     * The Floyd HUD panels are drawn straight to the framebuffer by the single inline pass (PostHudOverlay,
     * via the GameRenderer hook) BEFORE this editor screen renders — the SAME path used in game. The default
     * blur+dim screen background would therefore dim the panels too and make the editor look different from
     * the real HUD. Override it to a clear background so the editor is WYSIWYG (panels render exactly as in
     * game, with one rendering system); the free cursor + per-panel hover tooltip indicate edit mode.
     */
    override fun extractBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // intentionally empty — no dim/blur over the inline-rendered panels
    }

    override fun extractRenderState(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, deltaTicks)

        dragging?.let {
            it.x = (renderSpaceMouseX() + deltaX).coerceIn(0f, (mc.window.width - (it.width * it.scale)).coerceAtLeast(0f)).toInt()
            it.y = (renderSpaceMouseY() + deltaY).coerceIn(0f, (mc.window.height - (it.height * it.scale)).coerceAtLeast(0f)).toInt()
            clampOutOfHotbar(it, it.width * it.scale, it.height * it.scale)
        }
        resizing?.let { gesture ->
            val result = resizeHudFromCorner(
                startX = gesture.startX,
                startY = gesture.startY,
                width = gesture.width,
                height = gesture.height,
                startScale = gesture.startScale,
                corner = gesture.corner,
                mouseDeltaX = renderSpaceMouseX() - gesture.startMouseX,
                mouseDeltaY = renderSpaceMouseY() - gesture.startMouseY,
            )
            gesture.element.scale = result.scale
            gesture.element.x = result.x.toInt()
            gesture.element.y = result.y.toInt()
            clampHudToScreen(gesture.setting)
        }

        guiGraphics.pose().pushMatrix()
        val sf = mc.window.guiScale
        guiGraphics.pose().scale(1f / sf, 1f / sf)

        for (hud in hudSettingsCache) {
            if (hud.isAvailableInEditor) {
                hud.value.draw(guiGraphics, true)
                clampHudToScreen(hud)
                drawResizeHandles(guiGraphics, hud.value)
            }
        }

        hudSettingsCache.firstOrNull { it.isAvailableInEditor && it.value.isHovered() }?.let { hoveredHud ->
            guiGraphics.pose().pushMatrix()
            guiGraphics.pose().translate(
                (hoveredHud.value.x + hoveredHud.value.width * hoveredHud.value.scale + 10f),
                hoveredHud.value.y.toFloat(),
            )
            guiGraphics.pose().scale(2f, 2f)
            guiGraphics.drawString(mc.font, hoveredHud.name, 0, 0, Colors.WHITE.rgba)
            guiGraphics.drawWordWrap(mc.font, Component.literal(hoveredHud.description), 0, 10, 150, Colors.WHITE.rgba)
            guiGraphics.pose().popMatrix()
        }

        guiGraphics.pose().popMatrix()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val actualAmount = verticalAmount.sign.toFloat() * 0.2f
        hudSettingsCache.firstOrNull { it.isAvailableInEditor && it.value.isHovered() }?.let { hovered ->
            hovered.value.scale = (hovered.value.scale + actualAmount).coerceIn(0.5f, 10f)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (mouseButtonEvent.button() != 0) return super.mouseClicked(mouseButtonEvent, bl)
        val mouseX = renderSpaceMouseX()
        val mouseY = renderSpaceMouseY()

        hudSettingsCache.firstNotNullOfOrNull { setting ->
            if (!setting.isAvailableInEditor) return@firstNotNullOfOrNull null
            setting.value.resizeCornerAt(mouseX, mouseY)?.let { corner -> setting to corner }
        }?.let { (setting, corner) ->
            val element = setting.value
            resizing = ResizeGesture(
                setting = setting,
                element = element,
                corner = corner,
                startMouseX = mouseX,
                startMouseY = mouseY,
                startX = element.renderedX,
                startY = element.renderedY,
                startScale = element.scale,
                width = element.width.toFloat(),
                height = element.height.toFloat(),
            )
            return true
        }

        hudSettingsCache.firstOrNull { it.isAvailableInEditor && it.value.isHovered() }?.let { hovered ->
            dragging = hovered.value

            deltaX = hovered.value.renderedX - mouseX
            deltaY = hovered.value.renderedY - mouseY
            return true
        }

        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        dragging = null
        resizing = null
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        hudSettingsCache.firstOrNull { it.isAvailableInEditor && it.value.isHovered() }?.let { hovered ->
            when (keyEvent.key) {
                GLFW.GLFW_KEY_EQUAL -> hovered.value.scale = (hovered.value.scale + 0.1f).coerceIn(0.5f, 10f)
                GLFW.GLFW_KEY_MINUS -> hovered.value.scale = (hovered.value.scale - 0.1f).coerceIn(0.5f, 10f)
                GLFW.GLFW_KEY_RIGHT -> hovered.value.x += 10
                GLFW.GLFW_KEY_LEFT -> hovered.value.x -= 10
                GLFW.GLFW_KEY_UP -> hovered.value.y -= 10
                GLFW.GLFW_KEY_DOWN -> hovered.value.y += 10
            }
        }

        return super.keyPressed(keyEvent)
    }

    override fun onClose() {
        ModuleManager.saveConfigurations()
        super.onClose()
    }

    override fun removed() {
        ModuleManager.saveConfigurations()
        super.removed()
    }

    fun resetHUDS() {
        hudSettingsCache.forEach {
            it.value.x = 10
            it.value.y = 10
            it.value.scale = 2f
        }
    }

    private fun clampHudToScreen(hud: gg.floyd.clickgui.settings.impl.HUDSetting) {
        val (estimatedWidth, estimatedHeight) = estimatedHudSize(hud)
        val width = (hud.value.width.takeIf { it > 0 } ?: estimatedWidth) * hud.value.scale
        val height = (hud.value.height.takeIf { it > 0 } ?: estimatedHeight) * hud.value.scale
        // HUD coordinates live in framebuffer-pixel space (render uses scale(1/guiScale) from the
        // gui-scaled pose), so clamp against the framebuffer dimensions, not the logical window.
        hud.value.x = hud.value.x.coerceIn(0, (mc.window.width - width).toInt().coerceAtLeast(0))
        hud.value.y = hud.value.y.coerceIn(0, (mc.window.height - height).toInt().coerceAtLeast(0))
        clampOutOfHotbar(hud.value, width, height)
    }

    /**
     * Pushes a HUD element up out of the vanilla bottom-center HUD (hotbar / health / hunger / xp) so a
     * Floyd panel can never sit over the hotbar. A panel over the hotbar can't show a real frosted blur
     * (the GUI isn't in the framebuffer when the blur samples it) and would read as an empty/dark box,
     * so we hard-prevent positioning it there instead. Only the centered hotbar strip is reserved — the
     * bottom corners stay usable. Coordinates are framebuffer pixels (gui-scaled units * guiScale).
     */
    private fun clampOutOfHotbar(element: HudElement, fbWidth: Float, fbHeight: Float) {
        if (fbWidth <= 0f || fbHeight <= 0f) return
        val gs = mc.window.guiScale.toFloat()
        val centerX = mc.window.width / 2f
        val hotbarLeft = centerX - 95f * gs
        val hotbarRight = centerX + 95f * gs
        val hotbarTop = mc.window.height - 42f * gs
        val overlapsHotbarX = element.x < hotbarRight && element.x + fbWidth > hotbarLeft
        val reachesHotbar = element.y + fbHeight > hotbarTop
        if (overlapsHotbarX && reachesHotbar) {
            element.y = (hotbarTop - fbHeight).coerceAtLeast(0f).toInt()
        }
    }

    private fun estimatedHudSize(hud: gg.floyd.clickgui.settings.impl.HUDSetting): Pair<Int, Int> =
        HudSizeRegistry.estimate(hud.name)

    private fun drawResizeHandles(context: GuiGraphics, element: HudElement) {
        if (element.width <= 0 || element.height <= 0) return
        val radius = HudElement.RESIZE_HANDLE_RADIUS.toInt()
        val left = element.renderedX.toInt()
        val top = element.renderedY.toInt()
        val right = (element.renderedX + element.renderedWidth).toInt()
        val bottom = (element.renderedY + element.renderedHeight).toInt()
        for ((x, y) in listOf(left to top, right to top, left to bottom, right to bottom)) {
            context.fill(x - radius, y - radius, x + radius, y + radius, Colors.WHITE.rgba)
            context.fill(x - radius + 2, y - radius + 2, x + radius - 2, y + radius - 2, 0xFF202020.toInt())
        }
    }

    private data class ResizeGesture(
        val setting: gg.floyd.clickgui.settings.impl.HUDSetting,
        val element: HudElement,
        val corner: HudResizeCorner,
        val startMouseX: Float,
        val startMouseY: Float,
        val startX: Float,
        val startY: Float,
        val startScale: Float,
        val width: Float,
        val height: Float,
    )

    override fun isPauseScreen(): Boolean = false
}
