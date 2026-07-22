package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object FloydCrosshairChanger : Module(
    name = "Crosshair Changer",
    category = Category.RENDER,
    description = "Replaces the vanilla crosshair with a Star of David that supports solid, chroma, or fade colors.",
    toggled = false,
) {
    private val color by ColorSetting(
        "Crosshair Color",
        Color(0xFFFFFFFF.toInt()).also { it.chroma = true },
        allowAlpha = true,
        desc = "Crosshair color. Chroma and fade are configured inside the picker."
    )
    private val size by NumberSetting(
        "Crosshair Size",
        8,
        4,
        24,
        1,
        desc = "Distance in pixels from the center to the Star of David tips."
    )
    private val thickness by NumberSetting(
        "Crosshair Thickness",
        2,
        1,
        5,
        1,
        desc = "Thickness of the Star of David outline."
    )
    private val centerGap by NumberSetting(
        "Center Gap",
        3,
        0,
        8,
        1,
        desc = "Leaves a small opening around the exact center."
    )

    @JvmStatic
    fun shouldHideVanillaCrosshair(): Boolean = enabled

    @JvmStatic
    fun shouldRenderCustomCrosshair(): Boolean {
        if (!enabled) return false
        if (mc.level == null || mc.player == null) return false
        if (mc.screen != null) return false
        return mc.options.cameraType.isFirstPerson || gg.floyd.features.impl.hiders.FloydHiders.shouldShowThirdPersonCrosshair()
    }

    @JvmStatic
    fun render(guiGraphics: net.minecraft.client.gui.GuiGraphics) {
        if (!shouldRenderCustomCrosshair()) return

        val cx = guiGraphics.guiWidth() / 2f
        val cy = guiGraphics.guiHeight() / 2f
        val radius = size.toFloat()
        val stroke = thickness
        val gap = centerGap.toFloat()
        val dx = radius * (sqrt(3.0) * 0.5).toFloat()
        val half = radius * 0.5f
        val rgba = color.rgba

        val top = Point(cx, cy - radius)
        val upperLeft = Point(cx - dx, cy + half)
        val upperRight = Point(cx + dx, cy + half)
        val bottom = Point(cx, cy + radius)
        val lowerLeft = Point(cx - dx, cy - half)
        val lowerRight = Point(cx + dx, cy - half)

        drawSegment(guiGraphics, top, upperLeft, rgba, stroke, gap)
        drawSegment(guiGraphics, upperLeft, upperRight, rgba, stroke, gap)
        drawSegment(guiGraphics, upperRight, top, rgba, stroke, gap)

        drawSegment(guiGraphics, bottom, lowerLeft, rgba, stroke, gap)
        drawSegment(guiGraphics, lowerLeft, lowerRight, rgba, stroke, gap)
        drawSegment(guiGraphics, lowerRight, bottom, rgba, stroke, gap)
    }

    private fun drawSegment(
        guiGraphics: net.minecraft.client.gui.GuiGraphics,
        start: Point,
        end: Point,
        color: Int,
        thickness: Int,
        centerGap: Float,
    ) {
        for ((trimmedStart, trimmedEnd) in trimAroundCenter(start, end, centerGap)) {
            drawLine(guiGraphics, trimmedStart, trimmedEnd, color, thickness)
        }
    }

    private fun trimAroundCenter(start: Point, end: Point, centerGap: Float): List<Pair<Point, Point>> {
        if (centerGap <= 0f) return listOf(start to end)
        val midX = (start.x + end.x) * 0.5f
        val midY = (start.y + end.y) * 0.5f
        val vx = end.x - start.x
        val vy = end.y - start.y
        val length = kotlin.math.hypot(vx.toDouble(), vy.toDouble()).toFloat()
        if (length <= centerGap * 2f) return emptyList()
        val ux = vx / length
        val uy = vy / length
        val firstEnd = Point(midX - ux * centerGap, midY - uy * centerGap)
        val secondStart = Point(midX + ux * centerGap, midY + uy * centerGap)
        return listOf(start to firstEnd, secondStart to end)
    }

    private fun drawLine(
        guiGraphics: net.minecraft.client.gui.GuiGraphics,
        start: Point,
        end: Point,
        color: Int,
        thickness: Int,
    ) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val steps = max(abs(dx), abs(dy)).roundToInt().coerceAtLeast(1)
        val radius = (thickness - 1).coerceAtLeast(0) / 2f
        for (step in 0..steps) {
            val progress = step / steps.toFloat()
            val x = start.x + dx * progress
            val y = start.y + dy * progress
            val left = (x - radius).roundToInt()
            val top = (y - radius).roundToInt()
            val right = (x + radius + 1f).roundToInt()
            val bottom = (y + radius + 1f).roundToInt()
            guiGraphics.fill(left, top, right, bottom, color)
        }
    }

    private data class Point(val x: Float, val y: Float)
}
