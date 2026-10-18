package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.events.WorldEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.drawString
import kotlin.math.roundToInt

/** Floyd-only module toggle toasts; does not replace or change the existing chat notification option. */
object FloydNotifier : Module(
    name = "Notifier",
    category = Category.RENDER,
    description = "Shows animated module toggle notifications in the bottom-right corner.",
    toggled = true,
) {
    private val duration by NumberSetting("Duration", 3f, 1f, 10f, .5f,
        desc = "How long each toggle notification stays on screen.", unit = "s")
    private val maximumVisible by NumberSetting("Max Visible", 3, 1, 5, 1,
        desc = "Maximum number of notification cards shown at once.")
    private val showInMenus by BooleanSetting("Show In Menus", true,
        desc = "Show notifications for toggles made while a menu is open.")
    private val notifications = NotifierQueue { System.nanoTime() / 1_000_000L }

    init { on<WorldEvent.Unload> { notifications.clear() } }

    override fun onDisable() {
        notifications.clear()
        super.onDisable()
    }

    fun onModuleToggled(module: Module) {
        if (!enabled || mc.level == null || mc.player == null) return
        if (module === this || !module.visibleInGui || module === ClickGUIModule) return
        if (!showInMenus && mc.screen != null) return
        notifications.add(module.name, module.enabled, (duration * 1_000).toLong(), maximumVisible)
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "position" to "bottom-right",
        "durationSeconds" to duration,
        "maxVisible" to maximumVisible,
        "showInMenus" to showInMenus,
        "triggerCount" to notifications.triggerCount,
        "active" to notifications.visible(maximumVisible).map {
            mapOf("module" to it.entry.name, "enabled" to it.entry.enabled,
                "text" to if (it.entry.enabled) "Enabled" else "Disabled",
                "opacity" to it.opacity, "progress" to it.progress)
        },
    )

    fun render(graphics: GuiGraphics) {
        if (!enabled || mc.options.hideGui) return
        val cards = notifications.visible(maximumVisible)
        if (cards.isEmpty()) return
        val palette = FloydUiTheme.palette()
        val width = minOf(188, graphics.guiWidth() - 16).coerceAtLeast(48)
        val height = 38
        cards.forEachIndexed { index, card ->
            val alpha = card.opacity
            if (alpha <= .01f) return@forEachIndexed
            val x = graphics.guiWidth() - width - 8 + ((1f - alpha) * (width + 10)).roundToInt()
            val y = graphics.guiHeight() - 8 - height - index * (height + 5)
            if (y < 4) return@forEachIndexed
            val accent = if (card.entry.enabled) palette["Accent"] else 0xFFFF7E7E.toInt()
            val background = alphaColor(palette["Card Background"], alpha * .96f)
            val accentColor = alphaColor(accent, alpha)
            val trackColor = alphaColor(palette["Control Track"], alpha * .4f)
            NVGPIPRenderer.draw(graphics, x, y, width, height,
                renderScaleMultiplier = mc.window.guiScale.toFloat() / NVGRenderer.devicePixelRatio(),
                localCoordinates = true) {
                NVGRenderer.rect(0f, 0f, width.toFloat(), height.toFloat(), background, 6f)
                NVGRenderer.rect(8f, 10f, 16f, 16f, alphaColor(accent, alpha * .18f), 8f)
                // Draw the status mark around the badge's exact centre (16, 18),
                // independent of the selected font's glyph width and baseline.
                NVGRenderer.rect(12.5f, 17.25f, 7f, 1.5f, accentColor, .75f)
                if (card.entry.enabled) NVGRenderer.rect(15.25f, 14.5f, 1.5f, 7f, accentColor, .75f)
                NVGRenderer.rect(8f, height - 4f, width - 16f, 1.5f, trackColor, .75f)
                NVGRenderer.rect(8f, height - 4f, (width - 16f) * card.progress, 1.5f, accentColor, .75f)
            }
            val label = mc.font.plainSubstrByWidth(card.entry.name, width - 38)
            graphics.drawString(mc.font, label, x + 31, y + 7,
                alphaColor(palette["Primary Text"], alpha), false)
            graphics.drawString(mc.font, if (card.entry.enabled) "Enabled" else "Disabled", x + 31, y + 20,
                alphaColor(palette["Secondary Text"], alpha), false)
        }
    }

    private fun alphaColor(color: Int, opacity: Float): Int =
        (color and 0x00FFFFFF) or ((((color ushr 24) * opacity).roundToInt().coerceIn(0, 255)) shl 24)
}
