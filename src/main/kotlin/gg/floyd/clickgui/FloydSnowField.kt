package gg.floyd.clickgui

import kotlin.math.sin
import kotlin.random.Random

/** Normalized coordinates keep the field stable when the menu scale changes. */
internal class FloydSnowField(seed: Int = 0xF10D, val count: Int = 56) {
    private data class Seed(val x: Double, val y: Double, val speed: Double, val drift: Double,
        val phase: Double, val radius: Float, val opacity: Float)
    internal data class Flake(val x: Float, val y: Float, val radiusDp: Float, val opacity: Float)
    private val flakes = Random(seed).let { random -> List(count) {
        Seed(random.nextDouble(), random.nextDouble(), random.nextDouble(.026, .059),
            random.nextDouble(.002, .006), random.nextDouble(0.0, Math.PI * 2),
            random.nextDouble(.8, 1.7).toFloat(), random.nextDouble(.17, .42).toFloat())
    } }

    fun at(index: Int, elapsed: Double): Flake {
        val flake = flakes[index]
        val x = flake.x + elapsed * flake.drift + sin(elapsed * .5 + flake.phase) * .012
        val y = flake.y + elapsed * flake.speed
        return Flake(wrap(x), wrap(y), flake.radius, flake.opacity)
    }
    private fun wrap(value: Double) = ((value % 1 + 1) % 1).toFloat()
}

/** Discard hidden time and cap a stalled frame so reopening never jumps the particles. */
internal class FloydSnowClock {
    private var lastNanos: Long? = null
    var elapsed = 0.0; private set
    fun frame(nowNanos: Long): Double {
        lastNanos?.let { elapsed += ((nowNanos - it).coerceAtLeast(0) / 1_000_000_000.0).coerceAtMost(.05) }
        lastNanos = nowNanos
        return elapsed
    }
    fun pause() { lastNanos = null }
}
