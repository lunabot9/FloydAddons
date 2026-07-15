package gg.floyd.features.impl.misc

import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydCalculatorScreenInputTest {
    @Test
    fun `calculator consumes spatial navigation keys`() {
        listOf(
            GLFW.GLFW_KEY_W,
            GLFW.GLFW_KEY_A,
            GLFW.GLFW_KEY_S,
            GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_UP,
            GLFW.GLFW_KEY_DOWN,
            GLFW.GLFW_KEY_LEFT,
            GLFW.GLFW_KEY_RIGHT,
            GLFW.GLFW_KEY_TAB,
        ).forEach { assertTrue(calculatorConsumesNavigationKey(it), "Expected key $it to be consumed") }

        assertFalse(calculatorConsumesNavigationKey(GLFW.GLFW_KEY_7))
    }
}
