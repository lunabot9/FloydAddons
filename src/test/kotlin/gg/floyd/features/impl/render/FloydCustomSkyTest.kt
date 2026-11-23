package gg.floyd.features.impl.render

import gg.floyd.clickgui.settings.impl.SelectorSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import gg.floyd.features.impl.misc.FloydMenuVideoBackground

class FloydCustomSkyTest {
    @Test
    fun `sky history rejects stale frames and changed field of view`() {
        assertTrue(FloydMenuVideoBackground.canReuseCloudHistory(0.016f, 2f, 2.00001f))
        assertFalse(FloydMenuVideoBackground.canReuseCloudHistory(0.3f, 2f, 2f))
        assertFalse(FloydMenuVideoBackground.canReuseCloudHistory(-0.1f, 2f, 2f))
        assertFalse(FloydMenuVideoBackground.canReuseCloudHistory(0.016f, 2f, 2.1f))
        assertFalse(FloydMenuVideoBackground.canReuseCloudHistory(0.016f, Float.NaN, 2f))
        assertFalse(FloydMenuVideoBackground.canReuseCloudHistory(0.016f, 0f, 2f))
    }

    @Test
    fun `cloud nine quality reduces the actual render budget instead of forcing native resolution`() {
        val highScale = FloydMenuVideoBackground.worldSkyResolutionScale(8, 2)
        assertEquals(1f, highScale)
        val balancedScale = FloydMenuVideoBackground.worldSkyResolutionScale(8, 1)
        val performanceScale = FloydMenuVideoBackground.worldSkyResolutionScale(8, 0)
        assertEquals(0.85f, balancedScale)
        assertTrue(balancedScale * balancedScale < highScale * highScale)
        assertTrue(performanceScale < balancedScale)
    }

    @Test
    fun `lighter skies retain native resolution in every quality mode`() {
        for (style in listOf(1, 3, 4, 7)) {
            for (quality in 0..2) assertEquals(1f, FloydMenuVideoBackground.worldSkyResolutionScale(style, quality))
        }
    }

    @Test
    fun `world sky shader never samples artwork or panorama textures`() {
        val source = checkNotNull(javaClass.getResourceAsStream("/assets/floydaddons/shaders/core/menu_landscape.fsh"))
            .bufferedReader().use { it.readText() }
        assertEquals(listOf("u_cloudNoise", "u_cloudHistory"), Regex("uniform\\s+[iu]?sampler\\w*\\s+(\\w+)").findAll(source).map { it.groupValues[1] }.toList(),
            "Only generated lattice noise and prior rendered sky are allowed; no panorama artwork.")
        assertEquals("procedural", FloydCustomSky.state()["renderer"])
        assertEquals(false, FloydCustomSky.state()["usesSkyTextures"])
    }

    @Test
    fun `custom sky is opt in and retains original selections with additional animated styles`() {
        assertFalse(FloydCustomSky.enabled)
        assertEquals("Balanced", FloydCustomSky.state()["quality"])
        val selector = FloydCustomSky.settings["Sky Style"] as SelectorSetting
        val previous = selector.value
        try {
            selector.value = 0
            assertEquals("Golden Aurora", FloydCustomSky.state()["style"])
            selector.value = 1
            assertEquals("Nebula", FloydCustomSky.state()["style"])
            assertEquals(listOf("Golden Aurora", "Nebula", "Emerald Aurora", "Crimson Eclipse", "Sapphire Galaxy", "Rose Clouds", "Midnight Aurora", "Cloud Nine"), selector.optionLabels())
            selector.value = 6
            assertEquals("Midnight Aurora", FloydCustomSky.state()["style"])
            selector.value = 7
            assertEquals("Cloud Nine", FloydCustomSky.state()["style"])
            assertFalse(FloydCustomSky.replacesVanillaSky())
        } finally { selector.value = previous }
    }
}
