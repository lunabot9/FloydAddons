package gg.floyd.features.impl.misc

import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.PI
import kotlin.math.sin
import java.util.Random

/**
 * NVG-only animated galaxy background for the custom main menu.
 *
 * Everything here is drawn procedurally through NanoVG so the animation keeps updating while the
 * menu is open and the glow stays crisp instead of being baked through Minecraft's GUI path.
 */
object FloydMenuVideoBackground {
    private const val STAR_COUNT = 145
    private val stars = buildStars()

    @JvmStatic
    fun render(context: GuiGraphics): Boolean {
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), 0xFF050913.toInt())
        return true
    }

    @JvmStatic
    fun render(width: Float, height: Float, time: Float) {
        NVGRenderer.rect(0f, 0f, width, height, 0xFF050913.toInt())
        drawAtmosphere(width, height, time)
        for (star in stars) {
            drawStar(width, height, time, star)
        }
        drawVignette(width, height)
        NVGRenderer.rect(0f, 0f, width, height, 0x14000000)
    }

    @JvmStatic
    fun tick() = Unit

    @JvmStatic
    fun shutdown() = Unit

    private fun drawAtmosphere(width: Float, height: Float, time: Float) {
        val drift = sin(time * 0.016f) * width * 0.006f
        NVGRenderer.circle(width * 0.18f + drift, height * 0.26f, height * 0.20f, 0x08192B46)
        NVGRenderer.circle(width * 0.76f - drift * 0.8f, height * 0.34f, height * 0.24f, 0x0714233B)
        NVGRenderer.circle(width * 0.28f - drift * 0.5f, height * 0.74f, height * 0.18f, 0x06111D31)
        NVGRenderer.circle(width * 0.70f + drift * 0.4f, height * 0.78f, height * 0.20f, 0x07172A43)
    }

    private fun drawVignette(width: Float, height: Float) {
        NVGRenderer.circle(width * -0.10f, height * -0.06f, height * 0.36f, 0x12000000)
        NVGRenderer.circle(width * 1.08f, height * 0.10f, height * 0.34f, 0x12000000)
        NVGRenderer.circle(width * -0.08f, height * 1.02f, height * 0.42f, 0x16000000)
        NVGRenderer.circle(width * 1.05f, height * 1.04f, height * 0.40f, 0x18000000)
    }

    private fun drawStar(width: Float, height: Float, time: Float, star: Star) {
        val sway = sin(time * star.swaySpeed + star.phase) * star.swayAmplitude
        val x = wrapRange(star.baseX + sway + time * star.driftX, -0.10f, 1.10f) * width
        val y = wrapRange(star.baseY + time * star.fallSpeed, -0.14f, 1.14f) * height
        if (x < -24f || x > width + 24f || y < -24f || y > height + 24f) return

        val pulse = 0.80f + (0.5f + 0.5f * sin(time * star.twinkleSpeed + star.phase)) * 0.20f
        val alpha = (star.alpha * pulse).toInt().coerceIn(0, 255)
        if (alpha < 12) return

        if (star.hero) {
            NVGRenderer.circle(x, y, star.size * 1.9f, argb((alpha * 0.12f).toInt(), 125, 170, 240))
        }
        NVGRenderer.circle(x, y, star.size, argb(alpha, 238, 245, 255))
    }

    private fun buildStars(): List<Star> {
        val random = Random(0xF10AD0L)
        return List(STAR_COUNT) {
            val hero = random.nextFloat() > 0.84f
            Star(
                baseX = lerp(-0.08f, 1.08f, random.nextFloat()),
                baseY = lerp(-1.10f, 1.10f, random.nextFloat()),
                fallSpeed = if (hero) lerp(0.020f, 0.036f, random.nextFloat()) else lerp(0.010f, 0.024f, random.nextFloat()),
                driftX = lerp(-0.0014f, 0.0014f, random.nextFloat()),
                swaySpeed = lerp(0.45f, 1.10f, random.nextFloat()),
                swayAmplitude = if (hero) lerp(0.010f, 0.024f, random.nextFloat()) else lerp(0.006f, 0.018f, random.nextFloat()),
                twinkleSpeed = lerp(0.30f, 0.90f, random.nextFloat()),
                phase = random.nextFloat() * (PI.toFloat() * 2f),
                alpha = if (hero) lerp(210f, 255f, random.nextFloat()) else lerp(150f, 232f, random.nextFloat()),
                size = if (hero) lerp(3.0f, 4.9f, random.nextFloat()) else lerp(1.35f, 2.65f, random.nextFloat()),
                hero = hero
            )
        }
    }

    private fun wrapRange(value: Float, min: Float, max: Float): Float {
        val span = max - min
        var wrapped = (value - min) % span
        if (wrapped < 0f) wrapped += span
        return min + wrapped
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

    private data class Star(
        val baseX: Float,
        val baseY: Float,
        val fallSpeed: Float,
        val driftX: Float,
        val swaySpeed: Float,
        val swayAmplitude: Float,
        val twinkleSpeed: Float,
        val phase: Float,
        val alpha: Float,
        val size: Float,
        val hero: Boolean
    )
}
