package gg.floyd.features.impl.misc

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class FloydMenuLandscapeShaderTest {
    private val shader = checkNotNull(
        javaClass.getResourceAsStream("/assets/floydaddons/shaders/core/menu_landscape.fsh"),
    ).bufferedReader().use { it.readText() }

    @Test
    fun `menu landscape is a reflective branded ocean under an aurora`() {
        assertContains(shader, "vec4 AuroraCurtain")
        assertContains(shader, "vec3 DefinedStarField")
        assertContains(shader, "vec3 OceanColor")
        assertContains(shader, "vec3 WaterNormal")
        assertContains(shader, "float OceanSurfaceHeight")
    }

    @Test
    fun `website palette and strong reflection remain`() {
        assertContains(shader, "const vec3 SITE_INK = vec3(0.0392, 0.0275, 0.0118)")
        assertContains(shader, "const vec3 SITE_PANEL = vec3(0.1059, 0.0784, 0.0314)")
        assertContains(shader, "const vec3 SITE_AMBER = vec3(1.0000, 0.7216, 0.3020)")
        assertContains(shader, "const vec3 SITE_ORANGE = vec3(0.9686, 0.5098, 0.1176)")
        assertContains(shader, "0.82 + fresnel * 0.16")
    }

    @Test
    fun `ocean waves are displaced and randomized instead of repeating stripes`() {
        assertContains(shader, "float OceanWaveHeight")
        assertContains(shader, "float RandomizedRippleHeight")
        assertContains(shader, "float TraceOceanSurface")
        assertContains(shader, "vec2 warp = vec2(")
        assertContains(shader, "float phaseNoise = Noise")
        assertContains(shader, "float crestNoise = smoothstep(")
        assertContains(shader, "time * 0.28 + warp.y * 1.8) * 0.82")
        assertContains(shader, "float crestHeight = smoothstep(0.52, 1.38, surfaceHeight)")
        assertFalse(shader.contains("float rippleA = pow"))
        assertFalse(shader.contains("vec2 RippleSlope"))
    }

    @Test
    fun `aurora curtains flow and breathe over time`() {
        assertContains(shader, "float flowTime = time * 0.075")
        assertContains(shader, "float streamFlow = Fbm")
        assertContains(shader, "float narrowFolds")
        assertContains(shader, "float breathing = 0.86 + 0.14 * sin")
    }

    @Test
    fun `aurora panorama is periodic without a visible wrap seam`() {
        assertContains(shader, "vec2 panoramaCircle = normalize(direction.xz)")
        assertContains(shader, "panoramaCircle.x * 2.4")
        assertContains(shader, "panoramaCircle.y * 12.4")
        assertContains(shader, "foldedAngle * 3.0")
        assertContains(shader, "foldedAngle * 4.0")
        assertFalse(shader.contains("angle01 * 4.8"))
        assertFalse(shader.contains("foldedAngle * 2.7"))
        assertFalse(shader.contains("foldedAngle * 4.1"))
    }

    @Test
    fun `forest and mountain composition has been removed`() {
        assertFalse(shader.contains("SnowHeight"))
        assertFalse(shader.contains("RaymarchForestScene"))
        assertFalse(shader.contains("PineTree"))
        assertFalse(shader.contains("MountainHeight"))
        assertFalse(shader.contains("ORBIT_RADIUS"))
    }

    @Test
    fun `camera moves straight forward over the waves and keeps looking at the aurora`() {
        assertContains(shader, "vec2 ForwardCameraTrack")
        assertContains(shader, "vec3 CameraPath")
        assertContains(shader, "float travel = time * 4.20")
        assertContains(shader, "return vec2(0.0, travel)")
        assertContains(shader, "float followedWave = mix(localWave, approachingWave, 0.38)")
        assertContains(shader, "return vec3(track.x, followedWave + 5.10, track.y)")
        assertContains(shader, "vec2 pathTangent = vec2(0.0, 1.0)")
        assertContains(shader, "vec2 targetTrack = track + pathTangent * 58.0")
        assertContains(shader, "float targetHeight = targetWave + 27.5")
        assertFalse(shader.contains("float lateral"))
        assertFalse(shader.contains("float crestBias"))
        assertFalse(shader.contains("float lookOrbit"))
        assertFalse(shader.contains("float skyCycle"))
        assertFalse(shader.contains("float skyPan"))
        assertFalse(shader.contains("vec2 OceanCameraTrack"))
        assertFalse(shader.contains("float roll"))
    }

    @Test
    fun `far ocean is filtered and blended continuously into the horizon`() {
        assertContains(shader, "float sampleDistance = mix(0.18, 3.40")
        assertContains(shader, "float farNormalFade = smoothstep(320.0, 1500.0, distanceToWater)")
        assertContains(shader, "distanceFog * 0.62")
        assertContains(shader, "float horizonBlend = 1.0 - smoothstep(0.006, 0.050, -direction.y)")
        assertContains(shader, "return mix(foggedWater, horizonSky, horizonBlend)")
    }
}
