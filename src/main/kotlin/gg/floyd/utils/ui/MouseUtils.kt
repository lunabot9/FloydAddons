package gg.floyd.utils.ui

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.features.impl.render.ClickGUIModule

private var overriddenMouse: Pair<Float, Float>? = null
private var controlMouse: Pair<Float, Float>? = null
private var controlMouseUpdatedAt = 0L
private const val controlMouseTtlMs = 750L

val mouseX: Float
    get() = overriddenMouse?.first ?: activeControlMouse()?.first ?: mc.mouseHandler.xpos().toFloat()

val mouseY: Float
    get() = overriddenMouse?.second ?: activeControlMouse()?.second ?: mc.mouseHandler.ypos().toFloat()

fun activeMouseOverride(): Pair<Float, Float>? = overriddenMouse ?: activeControlMouse()

fun hasTransientMouseOverride(): Boolean = overriddenMouse != null

fun setMouseOverride(x: Double, y: Double) {
    controlMouse = x.toFloat() to y.toFloat()
    controlMouseUpdatedAt = System.currentTimeMillis()
}

fun clearMouseOverride() {
    controlMouse = null
    controlMouseUpdatedAt = 0L
}

private fun activeControlMouse(): Pair<Float, Float>? {
    val mouse = controlMouse ?: return null
    if (System.currentTimeMillis() - controlMouseUpdatedAt <= controlMouseTtlMs) return mouse
    clearMouseOverride()
    return null
}

fun <T> withMouseOverride(x: Double, y: Double, block: () -> T): T {
    val previous = overriddenMouse
    overriddenMouse = x.toFloat() to y.toFloat()
    return try {
        block()
    } finally {
        overriddenMouse = previous
    }
}

fun isAreaHovered(x: Float, y: Float, w: Float, h: Float, scaled: Boolean = false): Boolean =
    if (scaled) mouseX / ClickGUIModule.getStandardGuiScale() in x..(x + w) && mouseY / ClickGUIModule.getStandardGuiScale() in y..(y + h)
    else mouseX in x..(x + w) && mouseY in y..(y + h)

fun isAreaHovered(x: Float, y: Float, w: Float, scaled: Boolean = false): Boolean =
    if (scaled) mouseX / ClickGUIModule.getStandardGuiScale() in x..(x + w) && mouseY / ClickGUIModule.getStandardGuiScale() >= y
    else mouseX in x..(x + w) && mouseY >= y

fun getQuadrant(): Int =
    when {
        mouseX >= mc.window.screenWidth / 2 -> if (mouseY >= mc.window.screenHeight / 2) 4 else 2
        else -> if (mouseY >= mc.window.screenHeight / 2) 3 else 1
    }
