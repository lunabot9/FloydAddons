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
        NVGRenderer.text(name, x + 6f, y + height / 2f - 8f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

        val iconX = x + width - 30f
        val iconY = y + height / 2f - 12f
        hoverHandler.handle(iconX, iconY, 24f, 24f, true)

        val imageSize = 24f + (6f * hoverHandler.percent() / 100f)
        val offset = (imageSize - 24f) / 2f

        NVGRenderer.image(ClickGUI.movementImage, iconX - offset, iconY - offset, imageSize, imageSize)

        if (toggleable) {
            val hovered = isAreaHovered(lastX + width - 70f, lastY + getHeight() / 2f - 10f, 34f, 20f, true)
            NVGRenderer.rect(x + width - 70f, y + height / 2f - 10f, 34f, 20f, if (hovered) gray38.brighter().rgba else gray38.rgba, 9f)

            if (value.enabled || toggleAnimation.isAnimating()) {
                val color = ClickGUIModule.clickGUIColor
                NVGRenderer.rect(
                    x + width - 70f,
                    y + height / 2f - 10f,
                    toggleAnimation.get(34f, 9f, value.enabled),
                    20f,
                    if (hovered) color.brighter().rgba else color.rgba,
                    9f
                )
            }

            NVGRenderer.hollowRect(x + width - 70f, y + height / 2f - 10f, 34f, 20f, 2f, ClickGUIModule.clickGUIColor.rgba, 9f)
            NVGRenderer.circle(x + width - toggleAnimation.get(30f, 14f, !value.enabled) - 30f, y + height / 2f, 6f, Colors.WHITE.rgba)
        }
        return height
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, click: MouseButtonEvent): Boolean {
        if (click.button() != 0) return false
        val moveX = lastX + width - 30f
        val moveY = lastY + getHeight() / 2f - 12f
        val toggleX = lastX + width - 70f
        val toggleY = lastY + getHeight() / 2f - 10f
        return if (mouseX in moveX..(moveX + 24f) && mouseY in moveY..(moveY + 24f)) {
            mc.setScreen(HudManager)
            true
        } else if (toggleable && mouseX in toggleX..(toggleX + 34f) && mouseY in toggleY..(toggleY + 20f)) {
            toggleAnimation.start()
            value.enabled = !value.enabled
            true

        } else false
    }

    override val isHovered: Boolean get() = isAreaHovered(lastX + width - 30F, lastY + getHeight() / 2f - 12f, 24f, 24f, true)

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
