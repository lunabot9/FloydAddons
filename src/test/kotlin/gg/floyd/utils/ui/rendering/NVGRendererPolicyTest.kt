package gg.floyd.utils.ui.rendering

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NVGRendererPolicyTest {

    @Test
    fun `default NVG text defers outside Floyd clickgui screens`() {
        assertFalse(
            NVGRenderer.shouldUseImmediateTextPolicy(
                legacyNvgText = false,
                immediateTextOverrideDepth = 0,
                screenClassName = "net.minecraft.client.gui.screens.inventory.ContainerScreen",
            )
        )
    }

    @Test
    fun `Floyd clickgui screens still force immediate NVG text`() {
        assertTrue(
            NVGRenderer.shouldUseImmediateTextPolicy(
                legacyNvgText = false,
                immediateTextOverrideDepth = 0,
                screenClassName = "gg.floyd.clickgui.ClickGUI",
            )
        )
    }

    @Test
    fun `explicit override and legacy flag still force immediate text`() {
        assertTrue(
            NVGRenderer.shouldUseImmediateTextPolicy(
                legacyNvgText = true,
                immediateTextOverrideDepth = 0,
                screenClassName = null,
            )
        )
        assertTrue(
            NVGRenderer.shouldUseImmediateTextPolicy(
                legacyNvgText = false,
                immediateTextOverrideDepth = 1,
                screenClassName = null,
            )
        )
    }
}
