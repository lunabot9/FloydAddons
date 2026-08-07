package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.ClickGUI
import gg.floyd.clickgui.ClickGUI.gray38
import gg.floyd.clickgui.HudManager
import gg.floyd.clickgui.settings.RenderableSetting
import gg.floyd.clickgui.settings.Saving
import gg.floyd.features.Module
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.Color.Companion.brighter
import gg.floyd.utils.Colors
import gg.floyd.utils.ui.HoverHandler
import gg.floyd.utils.ui.animations.LinearAnimation
import gg.floyd.utils.ui.isAreaHovered
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.*
import net.minecraft.client.input.MouseButtonEvent

class HUDSetting(
    name: String,
    hud: HudElement,
    private val toggleable: Boolean = false,
    description: String,
    val module: Module,
) : RenderableSetting<HudElement>(name, description), Saving {
    private companion object {
        const val LABEL_TEXT_SIZE = 10f
        const val ICON_BOX = 16f
        const val TOGGLE_W = 28f
        const val TOGGLE_H = 16f
    }

    constructor(
        name: String,
        x: Int,
        y: Int,
        scale: Float,
        toggleable: Boolean,
        description: String,
        module: Module,
        draw: GuiGraphics.(Boolean) -> Pair<Int, Int>
    ) : this(name, HudElement(x, y, scale, !toggleable, draw), toggleable, description, module)

    override val default: HudElement = hud
    override var value: HudElement = default

    private var requireModuleEnabled = true

    val isEnabled: Boolean get() = (!requireModuleEnabled || module.enabled) && value.enabled
    /**
     * The Edit HUD tab is a layout workspace, so every registered element stays available there
     * even when its module or own visibility toggle is currently off. This lets users position and
     * resize the complete HUD before enabling features; normal in-game rendering still uses
     * [isEnabled] and therefore preserves all module/toggle visibility rules.
     */
    val isAvailableInEditor: Boolean get() = true

    /** Allows an always-active utility HUD to use its own toggle instead of the module toggle. */
    fun independentOfModule(): HUDSetting {
        requireModuleEnabled = false
        return this
    }

    private val toggleAnimation = LinearAnimation<Float>(200)
    private val hoverHandler = HoverHandler(150)

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float): Float {
        super.render(x, y, mouseX, mouseY)
        val height = getHeight()
        val controlsX = x + width - if (toggleable) 54f else 20f
        val labelSize = fitTextToWidth(name, controlsX - (x + 4f) - 4f, LABEL_TEXT_SIZE, 6f)
        NVGRenderer.text(name, x + 4f, y + (height - labelSize) / 2f, labelSize, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        val iconX = x + width - 20f
        val iconY = y + height / 2f - ICON_BOX / 2f
        hoverHandler.handle(iconX, iconY, ICON_BOX, ICON_BOX, true)

        val imageSize = ICON_BOX + (3f * hoverHandler.percent() / 100f)
        val offset = (imageSize - ICON_BOX) / 2f

        NVGRenderer.image(ClickGUI.movementImage, iconX - offset, iconY - offset, imageSize, imageSize)

        if (toggleable) {
            val hovered = isAreaHovered(lastX + width - 54f, lastY + getHeight() / 2f - TOGGLE_H / 2f, TOGGLE_W, TOGGLE_H, true)
            NVGRenderer.rect(x + width - 54f, y + height / 2f - TOGGLE_H / 2f, TOGGLE_W, TOGGLE_H, if (hovered) gray38.brighter().rgba else gray38.rgba, 7f)

            if (value.enabled || toggleAnimation.isAnimating()) {
                val accent = ClickGUIModule.guiAccentColor()
                NVGRenderer.rect(
                    x + width - 54f,
                    y + height / 2f - TOGGLE_H / 2f,
                    toggleAnimation.get(TOGGLE_W, 8f, value.enabled),
                    TOGGLE_H,
                    if (hovered) ClickGUIModule.hoveredAccentColor(accent) else accent,
                    7f
                )
            }

            NVGRenderer.hollowRect(x + width - 54f, y + height / 2f - TOGGLE_H / 2f, TOGGLE_W, TOGGLE_H, 1.5f, ClickGUIModule.guiAccentColor(), 7f)
            NVGRenderer.circle(x + width - toggleAnimation.get(24f, 12f, !value.enabled) - 26f, y + height / 2f, 4.5f, Colors.WHITE.rgba)
        }
        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != 0) return false
        val moveX = lastX + width - 20f
        val moveY = lastY + getHeight() / 2f - ICON_BOX / 2f
        val toggleX = lastX + width - 54f
        val toggleY = lastY + getHeight() / 2f - TOGGLE_H / 2f
        return if (mouseX in moveX..(moveX + ICON_BOX) && mouseY in moveY..(moveY + ICON_BOX)) {
            mc.setScreen(HudManager)
            true
        } else if (toggleable && mouseX in toggleX..(toggleX + TOGGLE_W) && mouseY in toggleY..(toggleY + TOGGLE_H)) {
            toggleAnimation.start()
            value.enabled = !value.enabled
            true

        } else false
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 20f, lastY + getHeight() / 2f - ICON_BOX / 2f, ICON_BOX, ICON_BOX, true)

    override fun write(gson: Gson): JsonElement = JsonObject().apply {
        addProperty("x", value.x)
        addProperty("y", value.y)
        addProperty("scale", value.scale)
        addProperty("enabled", value.enabled)
    }

    override fun read(element: JsonElement, gson: Gson) {
        if (element !is JsonObject) return
        value.x = element.get("x")?.asInt ?: value.x
        value.y = element.get("y")?.asInt ?: value.y
        value.scale = element.get("scale")?.asFloat ?: value.scale
        value.enabled = if (toggleable) element.get("enabled")?.asBoolean ?: value.enabled else true
    }
}
