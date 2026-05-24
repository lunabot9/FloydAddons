package com.odtheking.odin.clickgui

import com.odtheking.odin.FloydAddonsMod.mc
import com.odtheking.odin.clickgui.settings.impl.HudElement
import com.odtheking.odin.features.ModuleManager
import com.odtheking.odin.features.ModuleManager.hudSettingsCache
import com.odtheking.odin.features.impl.render.FloydHud
import com.odtheking.odin.utils.Colors
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt
import kotlin.math.sign
import com.odtheking.odin.utils.ui.mouseX as odinMouseX
import com.odtheking.odin.utils.ui.mouseY as odinMouseY

object HudManager : Screen(Component.literal("HUD Manager")) {

    private var dragging: HudElement? = null

    private var deltaX = 0f
    private var deltaY = 0f

    override fun init() {
        for (hud in hudSettingsCache) {
            clampHudToScreen(hud)
        }
        super.init()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.render(guiGraphics, mouseX, mouseY, deltaTicks)

        dragging?.let {
            it.x = (odinMouseX + deltaX).coerceIn(0f, (mc.window.screenWidth - (it.width * it.scale))).toInt()
            it.y = (odinMouseY + deltaY).coerceIn(0f, (mc.window.screenHeight - (it.height * it.scale))).toInt()
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

            deltaX = (hovered.value.x - odinMouseX)
            deltaY = (hovered.value.y - odinMouseY)
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

    private fun clampHudToScreen(hud: com.odtheking.odin.clickgui.settings.impl.HUDSetting) {
        val (estimatedWidth, estimatedHeight) = estimatedHudSize(hud)
        val width = (hud.value.width.takeIf { it > 0 } ?: estimatedWidth) * hud.value.scale
        val height = (hud.value.height.takeIf { it > 0 } ?: estimatedHeight) * hud.value.scale
        hud.value.x = hud.value.x.coerceIn(0, (mc.window.screenWidth - width).toInt().coerceAtLeast(0))
        hud.value.y = hud.value.y.coerceIn(0, (mc.window.screenHeight - height).toInt().coerceAtLeast(0))
    }

    private fun estimatedHudSize(hud: com.odtheking.odin.clickgui.settings.impl.HUDSetting): Pair<Int, Int> = when (hud.name) {
        "Inventory HUD" -> {
            val slotSize = (18 * FloydHud.inventoryHudScale).roundToInt().coerceAtLeast(12)
            9 * slotSize to 3 * slotSize
        }
        "Scoreboard HUD" -> 180 to 120
        else -> 120 to 40
    }

    override fun isPauseScreen(): Boolean = false
}
