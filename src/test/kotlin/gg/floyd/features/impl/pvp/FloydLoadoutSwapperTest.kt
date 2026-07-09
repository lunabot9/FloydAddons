package gg.floyd.features.impl.pvp

import com.mojang.blaze3d.platform.InputConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydLoadoutSwapperTest {

    @Test
    fun `default loadout bindings map 1 through 9 in order`() {
        assertEquals(0, FloydLoadoutSwapper.loadoutIndexFor(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_1)))
        assertEquals(4, FloydLoadoutSwapper.loadoutIndexFor(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_5)))
        assertEquals(8, FloydLoadoutSwapper.loadoutIndexFor(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_9)))
    }

    @Test
    fun `rebinding a loadout key changes which slot index is matched`() {
        val setting = FloydLoadoutSwapper.keySetting(0)
        val original = setting.value
        try {
            val rebound = InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_P)
            setting.value = rebound
            assertEquals(0, FloydLoadoutSwapper.loadoutIndexFor(rebound))
            assertEquals(-1, FloydLoadoutSwapper.loadoutIndexFor(original))
        } finally {
            setting.value = original
        }
    }

    @Test
    fun `loadout title matcher accepts the hypixel menu title`() {
        assertTrue(FloydLoadoutSwapper.isLoadoutScreen("(1/2) Loadouts"))
        assertTrue(FloydLoadoutSwapper.isLoadoutScreen("(1/1) Loadout"))
    }

    @Test
    fun `delay setting converts seconds into milliseconds`() {
        val setting = FloydLoadoutSwapper.delaySetting()
        val original = setting.numericValue()
        try {
            setting.setNumericValue(0.75)
            assertEquals(750L, FloydLoadoutSwapper.configuredBaseDelayMs())
        } finally {
            setting.setNumericValue(original)
        }
    }

    @Test
    fun `randomization is added on top of the base delay`() {
        val delaySetting = FloydLoadoutSwapper.delaySetting()
        val randomizationSetting = FloydLoadoutSwapper.randomizationSetting()
        val originalDelay = delaySetting.numericValue()
        val originalRandomization = randomizationSetting.numericValue()
        try {
            delaySetting.setNumericValue(0.75)
            randomizationSetting.setNumericValue(120.0)
            assertEquals(120L, FloydLoadoutSwapper.configuredRandomizationMs())
            assertEquals(810L, FloydLoadoutSwapper.configuredSelectionDelayMs { maxRandomizationMs ->
                assertEquals(120L, maxRandomizationMs)
                60L
            })
        } finally {
            delaySetting.setNumericValue(originalDelay)
            randomizationSetting.setNumericValue(originalRandomization)
        }
    }

    @Test
    fun `auto close defaults to enabled`() {
        assertTrue(FloydLoadoutSwapper.autoCloseSetting().enabled)
    }
}
