package floydaddons.not.dogshit.client.clickgui

import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.clickgui.settings.impl.HUDSetting
import floydaddons.not.dogshit.client.clickgui.settings.impl.HudElement
import floydaddons.not.dogshit.client.features.ModuleManager
import floydaddons.not.dogshit.client.features.ModuleManager.hudSettingsCache
import floydaddons.not.dogshit.client.features.impl.render.FloydHud
import floydaddons.not.dogshit.client.utils.Colors
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.ceil
import kotlin.math.roundToInt
import floydaddons.not.dogshit.client.utils.ui.mouseX as odinMouseX
import floydaddons.not.dogshit.client.utils.ui.mouseY as odinMouseY

object HudManager : Screen(Component.literal("HUD Manager")) {

    private const val RESIZE_HANDLE_SIZE = 9f
    private const val RESIZE_MIN_SCALE = 0.5f

    private val resizableHudNames = setOf("Inventory HUD", "Scoreboard HUD")

    private var dragging: HudElement? = null
    private var resizing: ResizeSession? = null

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

        if (!shouldUpdateHudLayout()) {
            clearInteractionState()
            return
        }

        dragging?.let {
            it.x = (odinMouseX + deltaX).coerceIn(0f, mc.window.screenWidth - ceil(it.width * it.scale.toDouble()).toFloat()).roundToInt()
            it.y = (odinMouseY + deltaY).coerceIn(0f, mc.window.screenHeight - ceil(it.height * it.scale.toDouble()).toFloat()).roundToInt()
        }
        resizing?.let { session ->
            applyResize(session, odinMouseX, odinMouseY)
        }

        guiGraphics.pose().pushMatrix()
        val sf = mc.window.guiScale
        guiGraphics.pose().scale(1f / sf, 1f / sf)

        for (hud in hudSettingsCache) {
            if (hud.module.enabled) {
                hud.value.draw(guiGraphics, true)
                clampHudToScreen(hud)
                if (hud.module.enabled && (hud.value.isHovered() || resizing?.hud === hud.value)) {
                    drawResizeHandles(guiGraphics, hud)
                }
            }
        }

        hudSettingsCache.firstOrNull { it.module.enabled && it.value.isHovered() }?.let { hoveredHud ->
            guiGraphics.pose().pushMatrix()
            val tooltipWidth = maxOf(mc.font.width(hoveredHud.name).toFloat(), 150f) * 2f
            guiGraphics.pose().translate(
                (hoveredHud.value.screenX - tooltipWidth - 10f).coerceAtLeast(0f),
                hoveredHud.value.screenY.toFloat(),
            )
            guiGraphics.pose().scale(2f, 2f)
            guiGraphics.drawString(mc.font, hoveredHud.name, 0, 0, Colors.WHITE.rgba)
            guiGraphics.drawWordWrap(mc.font, Component.literal(hoveredHud.description), 0, 10, 150, Colors.WHITE.rgba)
            guiGraphics.pose().popMatrix()
        }

        guiGraphics.pose().popMatrix()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (mouseButtonEvent.button() != 0) return super.mouseClicked(mouseButtonEvent, bl)

        hudSettingsCache.firstOrNull { it.module.enabled && it.value.isHovered() }?.let { hovered ->
            val handle = if (canResize(hovered) && hovered.value.width > 0 && hovered.value.height > 0) {
                hitResizeHandle(hovered.value, odinMouseX, odinMouseY)
            } else null

            if (handle != null) {
                resizing = ResizeSession(
                    hud = hovered.value,
                    corner = handle,
                    startX = hovered.value.screenX,
                    startY = hovered.value.screenY,
                    startScale = hovered.value.scale,
                    baseWidth = hovered.value.width.takeIf { it > 0 }?.toFloat() ?: 1f,
                    baseHeight = hovered.value.height.takeIf { it > 0 }?.toFloat() ?: 1f
                )
                dragging = null
                return true
            }

            dragging = hovered.value
            deltaX = (hovered.value.screenX - odinMouseX)
            deltaY = (hovered.value.screenY - odinMouseY)
            resizing = null
            return true
        }

        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        clearInteractionState()
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return super.keyPressed(keyEvent)
    }

    override fun onClose() {
        clearInteractionState()
        ModuleManager.saveConfigurations()
        super.onClose()
    }

    override fun removed() {
        clearInteractionState()
        ModuleManager.saveConfigurations()
        super.removed()
    }

    fun resetHUDS() {
        hudSettingsCache.forEach {
            it.value.x = it.default.x
            it.value.y = it.default.y
            it.value.scale = it.default.scale
            it.value.enabled = it.default.enabled
        }
    }

    private fun clampHudToScreen(hud: HUDSetting) {
        val (estimatedWidth, estimatedHeight) = estimatedHudSize(hud)
        val width = ceil((hud.value.width.takeIf { it > 0 } ?: estimatedWidth) * hud.value.scale).toInt()
        val height = ceil((hud.value.height.takeIf { it > 0 } ?: estimatedHeight) * hud.value.scale).toInt()
        hud.value.x = hud.value.x.coerceIn(0, (mc.window.screenWidth - width).coerceAtLeast(0))
        hud.value.y = hud.value.y.coerceIn(0, (mc.window.screenHeight - height).coerceAtLeast(0))
    }

    private fun estimatedHudSize(hud: HUDSetting): Pair<Int, Int> = when (hud.name) {
        "Inventory HUD" -> {
            val slotSize = (18 * FloydHud.inventoryHudScale).roundToInt().coerceAtLeast(12)
            9 * slotSize to 3 * slotSize
        }
        "Day Tracker" -> 120 to 32
        "Scoreboard HUD" -> {
            val scale = FloydHud.scoreboardHudScale
            (180 * scale).roundToInt() to (120 * scale).roundToInt()
        }
        else -> 120 to 40
    }

    private fun shouldUpdateHudLayout(): Boolean {
        if (!mc.isWindowActive) return false
        return mc.window.screenWidth > 0 && mc.window.screenHeight > 0
    }

    private fun clearInteractionState() {
        dragging = null
        resizing = null
        deltaX = 0f
        deltaY = 0f
    }

    private fun canResize(hud: HUDSetting): Boolean = hud.name in resizableHudNames

    private fun hitResizeHandle(hud: HudElement, mouseX: Float, mouseY: Float): ResizeCorner? {
        val left = hud.screenX.toFloat()
        val top = hud.screenY.toFloat()
        val right = left + hud.width * hud.scale
        val bottom = top + hud.height * hud.scale
        val handle = RESIZE_HANDLE_SIZE

        return when {
            isPointInRect(mouseX, mouseY, left, top, left + handle, top + handle) -> ResizeCorner.TOP_LEFT
            isPointInRect(mouseX, mouseY, right - handle, top, right, top + handle) -> ResizeCorner.TOP_RIGHT
            isPointInRect(mouseX, mouseY, left, bottom - handle, left + handle, bottom) -> ResizeCorner.BOTTOM_LEFT
            isPointInRect(mouseX, mouseY, right - handle, bottom - handle, right, bottom) -> ResizeCorner.BOTTOM_RIGHT
            else -> null
        }
    }

    private fun drawResizeHandles(guiGraphics: GuiGraphics, hud: HUDSetting) {
        if (!canResize(hud)) return

        val left = hud.value.screenX.toFloat()
        val top = hud.value.screenY.toFloat()
        val right = left + hud.value.width * hud.value.scale
        val bottom = top + hud.value.height * hud.value.scale
        val handle = RESIZE_HANDLE_SIZE
        val active = resizing?.hud === hud.value

        drawHandle(guiGraphics, left, top, handle, active)
        drawHandle(guiGraphics, right - handle, top, handle, active)
        drawHandle(guiGraphics, left, bottom - handle, handle, active)
        drawHandle(guiGraphics, right - handle, bottom - handle, handle, active)
    }

    private fun drawHandle(guiGraphics: GuiGraphics, x: Float, y: Float, size: Float, active: Boolean) {
        val outer = if (active) 0xFFFFFFFF.toInt() else 0xCCFFFFFF.toInt()
        val inner = if (active) 0xFF9AE6A1.toInt() else 0xCCB6F7BA.toInt()
        guiGraphics.fill(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt(), outer)
        guiGraphics.fill((x + 1).toInt(), (y + 1).toInt(), (x + size - 1).toInt(), (y + size - 1).toInt(), inner)
    }

    private fun applyResize(session: ResizeSession, mouseX: Float, mouseY: Float) {
        val baseWidth = session.baseWidth.coerceAtLeast(1f)
        val baseHeight = session.baseHeight.coerceAtLeast(1f)
        val originalRight = session.startX + baseWidth * session.startScale
        val originalBottom = session.startY + baseHeight * session.startScale

        val scale = when (session.corner) {
            ResizeCorner.TOP_LEFT -> maxOf((originalRight - mouseX) / baseWidth, (originalBottom - mouseY) / baseHeight)
            ResizeCorner.TOP_RIGHT -> maxOf((mouseX - session.startX) / baseWidth, (originalBottom - mouseY) / baseHeight)
            ResizeCorner.BOTTOM_LEFT -> maxOf((originalRight - mouseX) / baseWidth, (mouseY - session.startY) / baseHeight)
            ResizeCorner.BOTTOM_RIGHT -> maxOf((mouseX - session.startX) / baseWidth, (mouseY - session.startY) / baseHeight)
        }.coerceAtLeast(RESIZE_MIN_SCALE)

        session.hud.scale = scale
        when (session.corner) {
            ResizeCorner.TOP_LEFT -> {
                session.hud.x = (originalRight - baseWidth * scale).roundToInt()
                session.hud.y = (originalBottom - baseHeight * scale).roundToInt()
            }
            ResizeCorner.TOP_RIGHT -> {
                session.hud.x = session.startX
                session.hud.y = (originalBottom - baseHeight * scale).roundToInt()
            }
            ResizeCorner.BOTTOM_LEFT -> {
                session.hud.x = (originalRight - baseWidth * scale).roundToInt()
                session.hud.y = session.startY
            }
            ResizeCorner.BOTTOM_RIGHT -> {
                session.hud.x = session.startX
                session.hud.y = session.startY
            }
        }
    }

    private fun isPointInRect(mouseX: Float, mouseY: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom
    }

    private data class ResizeSession(
        val hud: HudElement,
        val corner: ResizeCorner,
        val startX: Int,
        val startY: Int,
        val startScale: Float,
        val baseWidth: Float,
        val baseHeight: Float
    )

    private enum class ResizeCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    override fun isPauseScreen(): Boolean = false
}
