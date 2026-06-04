package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.render.HudPanel
import gg.floyd.utils.render.PanelBlurPIPRenderer
import gg.floyd.utils.render.RoundRectPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import gg.floyd.utils.ui.rendering.PanelPhase
import gg.floyd.utils.ui.rendering.PostHudOverlay
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.ceil

/**
 * Standalone toggle for the Floyd day tracker HUD.
 *
 * Draws the current server day (derived from the level's day-time) in a movable Floyd HUD. All
 * panel cosmetics (background, border color/chroma/fade, corner radius, border width and frosted
 * backdrop) come from the global [FloydPanelStyle] via [HudPanel], so it inherits the unified style.
 *
 * The module owns its own [HUD] element (mirroring [FloydInventoryHud]) so the day tracker can be
 * toggled and positioned independently without sharing a HUDSetting with another module (which
 * would double-register it in the manager's HUD cache and double-draw the panel).
 *
 * Like the scoreboard, the in-game render runs from the world-end post-HUD pass ([PostHudOverlay])
 * so the panel stays visible under chat / inventory / the ClickGUI (which then blur over it).
 */
object FloydDayTrackerModule : Module(
    name = "Day Tracker",
    category = Category.RENDER,
    description = "Displays the current server day in a movable Floyd HUD.",
    toggled = false,
) {
    // toggleable = false: this module's only purpose is the HUD, so the module's own on/off is the
    // single switch (no redundant inner toggle); the HUD stays draggable via the move icon.
    private val dayTrackerHud by HUD("Day Tracker", "Displays the current server day in a movable Floyd HUD.", false, 24, 180, 1f) {
        drawDayTrackerHud(it)
    }

    // NanoVG font size for the label — matches the scoreboard's vector text for a consistent, crisp look.
    private const val DAY_TRACKER_FONT_SIZE = 9f

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "dayTracker" to mapOf(
            "enabled" to (enabled && dayTrackerHud.enabled),
            "x" to dayTrackerHud.x,
            "y" to dayTrackerHud.y,
            "scale" to dayTrackerHud.scale,
            "day" to currentServerDay(),
            "label" to currentServerDayLabel()
        ),
        "cornerRadius" to FloydPanelStyle.cornerRadiusFor(FloydPanelStyle.PanelTarget.DAY_TRACKER)
    )

    private fun currentServerDay(): Long? = mc.level?.let { (it.dayTime / 24000L) + 1L }

    private fun currentServerDayLabel(): String? = currentServerDay()?.let { "Day $it" }

    private data class DayTrackerLayout(val label: String, val width: Int, val height: Int)

    /**
     * Pure layout (NO GuiGraphics, no drawing) so it can run screen-independently from the world-end
     * pass. Returns null when there is nothing to draw; with [example] true, returns placeholder
     * content for the HUD editor. The box is sized in LOCAL (pre-scale) units exactly as the original
     * deferred draw was, so the SDF / mc.font geometry derived from it stays pixel-identical.
     */
    private fun dayTrackerLayout(example: Boolean): DayTrackerLayout? {
        val label = currentServerDayLabel() ?: if (example) "Day 1" else return null
        val padding = FloydPanelStyle.paddingFor(FloydPanelStyle.PanelTarget.DAY_TRACKER).coerceAtLeast(0)
        // Size to the NanoVG font (the same crisp vector renderer the scoreboard uses) since the label is
        // drawn with NanoVG in the TEXT phase. textWidth is safe outside a frame (the scoreboard measures
        // the same way during layout).
        val font = NVGRenderer.activeFont()
        val width = ceil(NVGRenderer.textWidth(label, DAY_TRACKER_FONT_SIZE, font)).toInt() + padding * 2
        val height = ceil(DAY_TRACKER_FONT_SIZE).toInt() + padding * 2
        return DayTrackerLayout(label, width, height)
    }

    // HUD element callback: this NEVER draws — it only reports the panel's size for the HUD editor's drag
    // box. The actual panel (in game AND in the editor) is drawn by the single inline pass
    // [renderAtWorldEnd], so the editor and in-game share ONE rendering system. Mirrors
    // [FloydCustomScoreboard.drawScoreboardHud].
    private fun GuiGraphics.drawDayTrackerHud(example: Boolean): Pair<Int, Int> {
        val layout = dayTrackerLayout(example) ?: return 0 to 0
        return layout.width to layout.height
    }

    /**
     * The single day-tracker render — drawn directly to the main framebuffer from the world-end post-HUD
     * pass ([PostHudOverlay]), both in game AND while the HUD editor is open (the editor just drags it).
     * Uses the HUD element's framebuffer-pixel position/scale, so it shows regardless of any open screen.
     */
    fun renderAtWorldEnd(phase: PanelPhase) {
        if (!enabled || !dayTrackerHud.enabled) return
        val editor = mc.screen === gg.floyd.clickgui.HudManager
        val layout = dayTrackerLayout(editor) ?: return
        drawDayTrackerInline(layout, phase)
    }

    /**
     * In-game render, drawn DIRECTLY to the main framebuffer from the world-end pass (no PIP, no
     * GuiGraphics). Geometry comes from the HUD element's own framebuffer-pixel x/y/scale, so it is
     * screen-independent (renders with any screen open). SDF bg/border draw in framebuffer space; the
     * mc.font label draws in logical (/dpr) space — the FBO is re-bound between them because the SDF
     * blaze3d render pass can retarget. Mirrors [FloydCustomScoreboard.drawScoreboardInline].
     */
    private fun drawDayTrackerInline(layout: DayTrackerLayout, phase: PanelPhase) {
        val target = FloydPanelStyle.PanelTarget.DAY_TRACKER
        val dpr = NVGRenderer.devicePixelRatio()
        val scale = dayTrackerHud.scale
        val fx = dayTrackerHud.x.toFloat()
        val fy = dayTrackerHud.y.toFloat()
        val fw = layout.width * scale
        val fh = layout.height * scale

        if (phase == PanelPhase.BACKGROUND) {
            val fill = FloydPanelStyle.backgroundColorFor(target).rgba
            val border = HudPanel.panelBorderColors(target, dayTrackerHud.x, dayTrackerHud.y)
            val radius = FloydPanelStyle.cornerRadiusFor(target).toFloat() * scale
            val outline = FloydPanelStyle.borderWidthFor(target).toFloat() * scale

            // Frosted blur backdrop (samples the per-frame framebuffer snapshot), then the rounded fill+border.
            if (FloydPanelStyle.blurFor(target)) {
                val blurRadius = FloydPanelStyle.blurStrengthFor(target).coerceIn(0, 20) * 0.4f
                if (blurRadius >= 0.5f && fw * fh >= 2000f) {
                    PanelBlurPIPRenderer.drawInline(fx, fy, fw, fh, radius, radius, radius, radius, blurRadius, FloydPanelStyle.blurIsBoxFor(target))
                    PostHudOverlay.bindMainFbo()
                }
            }
            RoundRectPIPRenderer.drawInline(
                fx, fy, fw, fh,
                fill, fill, fill, fill,
                radius, radius, radius, radius,
                border.topLeft, border.topRight, border.bottomRight, border.bottomLeft,
                outline
            )
            return
        }

        // TEXT phase: the label via NanoVG (crisp vector text, same renderer as the scoreboard), logical
        // (/dpr) space. Safe now because ALL panel backgrounds were already drawn — see PostHudOverlay.
        val padding = FloydPanelStyle.paddingFor(target).coerceAtLeast(0)
        val originX = fx / dpr
        val originY = fy / dpr
        val textScale = scale / dpr
        NVGRenderer.beginFrame(mc.window.width.toFloat(), mc.window.height.toFloat())
        NVGRenderer.translate(originX, originY)
        NVGRenderer.scale(textScale, textScale)
        NVGRenderer.text(layout.label, padding.toFloat(), padding.toFloat(), DAY_TRACKER_FONT_SIZE, 0xFFFFFFFF.toInt(), NVGRenderer.activeFont())
        NVGRenderer.endFrame()
    }
}
