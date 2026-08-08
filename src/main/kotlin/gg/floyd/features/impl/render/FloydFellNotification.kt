package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.HudSizeRegistry
import gg.floyd.events.ChatPacketEvent
import gg.floyd.events.WorldEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.drawString
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** Shows a short, movable HUD notification when SkyBlock reports that an entire tree was felled. */
object FloydFellNotification : Module(
    name = "Fell Notification",
    category = Category.RENDER,
    description = "Flashes the Woodpecker, Petalfall, or Timber tree-fell message above the crosshair.",
    toggled = false,
) {
    private const val HUD_NAME = "Fell Notification HUD"
    private const val DARK_GREEN_RGB = 0x00AA00
    private const val BRIGHT_GREEN_RGB = 0x55FF55
    private const val DISPLAY_DURATION_MS = 2_500L
    private const val FADE_DURATION_MS = 500L
    private const val DEFAULT_SCALE = 3f
    private const val DEFAULT_CROSSHAIR_GAP_PX = 48
    private const val ESTIMATED_WIDTH = 72
    private const val ESTIMATED_HEIGHT = 9

    private data class NotificationStyle(val label: String, val colorRgb: Int)

    private val stylesByMessage = linkedMapOf(
        "WOODPECKER! You felled the entire Tree!" to NotificationStyle("WOODPECKER!", DARK_GREEN_RGB),
        "PETALFALL! You felled the entire Tree!" to NotificationStyle("PETALFALL!", DARK_GREEN_RGB),
        "TIMBER! You felled the entire Tree!" to NotificationStyle("TIMBER!", BRIGHT_GREEN_RGB),
    )

    private data class ActiveNotification(
        val label: String,
        val colorRgb: Int,
        val message: String,
        val startedNanos: Long,
    )

    @Volatile
    private var activeNotification: ActiveNotification? = null
    private val triggerCount = AtomicLong()

    // Negative defaults mean "not positioned yet". The first HUD draw resolves them against the
    // actual framebuffer, keeping the label centered even when Minecraft starts fullscreen.
    private val notificationHud by HUD(
        HUD_NAME,
        "Move and resize the tree-fell notification.",
        false,
        -1,
        -1,
        DEFAULT_SCALE,
    ) { example -> drawNotification(example) }

    init {
        HudSizeRegistry.register(HUD_NAME) { ESTIMATED_WIDTH to ESTIMATED_HEIGHT }

        on<ChatPacketEvent> {
            val (label, colorRgb) = notificationForChat(value) ?: return@on
            activeNotification = ActiveNotification(label, colorRgb, value.trim(), System.nanoTime())
            triggerCount.incrementAndGet()
        }
        on<WorldEvent.Unload> { clearNotification() }
    }

    override fun onDisable() {
        clearNotification()
        super.onDisable()
    }

    fun state(): Map<String, Any?> {
        val nowNanos = System.nanoTime()
        val stored = activeNotification
        val current = stored?.takeIf { opacityForElapsedMs(elapsedMs(it, nowNanos)) > 0 }
        val elapsed = current?.let { elapsedMs(it, nowNanos) }
        return mapOf(
            "enabled" to enabled,
            "active" to (current != null),
            "text" to current?.label,
            "lastText" to stored?.label,
            "lastMessage" to stored?.message,
            "remainingMs" to (elapsed?.let { (DISPLAY_DURATION_MS - it).coerceAtLeast(0L) } ?: 0L),
            "triggerCount" to triggerCount.get(),
            "color" to stored?.colorRgb?.toHexColor(),
            "triggerColors" to stylesByMessage.mapValues { (_, style) -> style.colorRgb.toHexColor() },
            "hud" to mapOf(
                "x" to notificationHud.x,
                "y" to notificationHud.y,
                "scale" to notificationHud.scale,
            ),
        )
    }

    private fun GuiGraphics.drawNotification(example: Boolean): Pair<Int, Int> {
        val font = mc.font
        val width = stylesByMessage.values.maxOf { font.width(it.label) }
        val height = font.lineHeight
        positionDefaultHud(width, height)

        val nowNanos = System.nanoTime()
        val current = activeNotification
        val label: String
        val colorRgb: Int
        val alpha: Int
        if (example) {
            label = "TIMBER!"
            colorRgb = BRIGHT_GREEN_RGB
            alpha = 255
        } else if (current != null) {
            label = current.label
            colorRgb = current.colorRgb
            alpha = opacityForElapsedMs(elapsedMs(current, nowNanos))
            if (alpha <= 0) return width to height
        } else {
            return width to height
        }

        val textX = (width - font.width(label)) / 2
        val color = (alpha shl 24) or colorRgb
        drawString(font, label, textX, 0, color, true)
        return width to height
    }

    private fun positionDefaultHud(width: Int, height: Int) {
        if (notificationHud.x >= 0 && notificationHud.y >= 0) return
        val (defaultX, defaultY) = defaultHudPosition(
            mc.window.width,
            mc.window.height,
            width,
            height,
            notificationHud.scale,
        )
        if (notificationHud.x < 0) notificationHud.x = defaultX
        if (notificationHud.y < 0) notificationHud.y = defaultY
    }

    private fun clearNotification() {
        activeNotification = null
    }

    private fun elapsedMs(notification: ActiveNotification, nowNanos: Long): Long =
        ((nowNanos - notification.startedNanos).coerceAtLeast(0L) / 1_000_000L)

    internal fun notificationForChat(message: String): Pair<String, Int>? =
        stylesByMessage[message.trim()]?.let { it.label to it.colorRgb }

    private fun Int.toHexColor(): String = "#%06X".format(this)

    internal fun opacityForElapsedMs(elapsedMs: Long): Int = when {
        elapsedMs < 0L -> 255
        elapsedMs < DISPLAY_DURATION_MS - FADE_DURATION_MS -> 255
        elapsedMs >= DISPLAY_DURATION_MS -> 0
        else -> (((DISPLAY_DURATION_MS - elapsedMs).toDouble() / FADE_DURATION_MS) * 255.0)
            .roundToInt()
            .coerceIn(0, 255)
    }

    internal fun defaultHudPosition(
        viewportWidth: Int,
        viewportHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        scale: Float,
    ): Pair<Int, Int> {
        val renderedWidth = contentWidth * scale
        val renderedHeight = contentHeight * scale
        val x = ((viewportWidth - renderedWidth) / 2f).roundToInt().coerceAtLeast(0)
        val y = (viewportHeight / 2f - DEFAULT_CROSSHAIR_GAP_PX - renderedHeight)
            .roundToInt()
            .coerceAtLeast(0)
        return x to y
    }
}
