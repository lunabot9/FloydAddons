package gg.floyd.clickgui

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.HudElement
import gg.floyd.features.ModuleManager
import gg.floyd.features.ModuleManager.hudSettingsCache
import gg.floyd.utils.Colors
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import gg.floyd.utils.ui.activeMouseOverride
import org.lwjgl.glfw.GLFW
import kotlin.math.sign

object HudManager : Screen(Component.literal("HUD Manager")) {

    private var dragging: HudElement? = null

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

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.render(guiGraphics, mouseX, mouseY, deltaTicks)

        dragging?.let {
            it.x = (renderSpaceMouseX() + deltaX).coerceIn(0f, (mc.window.width - (it.width * it.scale)).coerceAtLeast(0f)).toInt()
            it.y = (renderSpaceMouseY() + deltaY).coerceIn(0f, (mc.window.height - (it.height * it.scale)).coerceAtLeast(0f)).toInt()
        }

        guiGraphics.pose().pushMatrix()
        val sf = mc.window.guiScale
        guiGraphics.pose().scale(1f / sf, 1f / sf)

        for (hud in hudSettingsCache) {
            if (hud.module.enabled) {
                hud.value.draw(guiGraphics, true)
                clampHudToScreen(hud)
            }
        }

        hudSettingsCache.firstOrNull { it.module.enabled && it.value.isHovered() }?.let { hoveredHud ->
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
        hudSettingsCache.firstOrNull { it.module.enabled && it.value.isHovered() }?.let { hovered ->
            hovered.value.scale = (hovered.value.scale + actualAmount).coerceIn(1f, 10f)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        hudSettingsCache.firstOrNull { it.module.enabled && it.value.isHovered() }?.let { hovered ->
            dragging = hovered.value

            deltaX = (hovered.value.x - renderSpaceMouseX())
            deltaY = (hovered.value.y - renderSpaceMouseY())
            return true
        }

        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        dragging = null
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        hudSettingsCache.firstOrNull { it.module.enabled && it.value.isHovered() }?.let { hovered ->
            when (keyEvent.key) {
                GLFW.GLFW_KEY_EQUAL -> hovered.value.scale = (hovered.value.scale + 0.1f).coerceIn(1f, 10f)
                GLFW.GLFW_KEY_MINUS -> hovered.value.scale = (hovered.value.scale - 0.1f).coerceIn(1f, 10f)
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
    }

    private fun estimatedHudSize(hud: gg.floyd.clickgui.settings.impl.HUDSetting): Pair<Int, Int> =
        HudSizeRegistry.estimate(hud.name)

    override fun isPauseScreen(): Boolean = false
}
