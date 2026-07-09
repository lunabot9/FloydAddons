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
    fun `delay setting converts seconds into client ticks`() {
        val setting = FloydLoadoutSwapper.delaySetting()
        val original = setting.numericValue()
        try {
            setting.setNumericValue(0.75)
            assertEquals(15, FloydLoadoutSwapper.configuredDelayTicks())
        } finally {
            setting.setNumericValue(original)
        }
    }

    @Test
    fun `auto close defaults to enabled`() {
        assertTrue(FloydLoadoutSwapper.autoCloseSetting().enabled)
    }
}
