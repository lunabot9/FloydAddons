package gg.floyd.clickgui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Draw-only overlay: deliberately has no pointer or focus handlers. */
@Composable
internal fun FloydSnowfall(enabled: Boolean, active: Boolean, color: Color, alpha: Float, elapsed: Double, modifier: Modifier) {
    val field = remember { FloydSnowField() }
    if (!enabled || !active) {
        SideEffect { FloydSnowfallDiagnostics.stop() }
        return
    }
    Canvas(modifier) {
        for (index in 0 until field.count) {
            val flake = field.at(index, elapsed)
            val point = Offset(flake.x * size.width, flake.y * size.height)
            val radius = flake.radiusDp.dp.toPx()
            val opacity = flake.opacity * alpha
            drawCircle(color.copy(alpha = opacity * .1f), radius * 2.1f, point)
            drawCircle(color.copy(alpha = opacity), radius, point)
        }
        FloydSnowfallDiagnostics.rendered(field.count, elapsed)
    }
}

/** Included by the existing theme state; no diagnostic code runs an animation. */
object FloydSnowfallDiagnostics {
    @Volatile private var active = false
    @Volatile private var particles = 0
    @Volatile private var renderedFrames = 0L
    @Volatile private var elapsedSeconds = 0.0
    internal fun rendered(count: Int, elapsed: Double) {
        active = true; particles = count; renderedFrames++; elapsedSeconds = elapsed
    }
    fun stop() { active = false; particles = 0 }
    fun state(enabled: Boolean): Map<String, Any> = mapOf("enabled" to enabled, "active" to active,
        "particles" to particles, "renderedFrames" to renderedFrames, "elapsedSeconds" to elapsedSeconds)
}
