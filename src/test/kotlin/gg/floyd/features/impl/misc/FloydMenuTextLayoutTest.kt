package gg.floyd.features.impl.misc

import kotlin.test.Test
import kotlin.test.assertEquals

class FloydMenuTextLayoutTest {
    @Test
    fun `screen titles use the full screen slot instead of deferred text width`() {
        val slot = FloydMenuTextLayout.screenTitleSlot(screenWidth = 960, top = 0, height = 28)

        assertEquals(0, slot.left)
        assertEquals(960, slot.width)
        assertEquals(0, slot.top)
        assertEquals(28, slot.height)
    }

    @Test
    fun `immediate glyph width is centered inside the submitted slot`() {
        assertEquals(
            421.5f,
            FloydMenuTextLayout.centeredLocalX(
                centerX = 480f,
                slotLeft = 0,
                immediateTextWidth = 117f,
            )
        )
    }

    @Test
    fun `ordinary string widgets reserve overflow on both sides`() {
        val slot = FloydMenuTextLayout.widgetSlot(left = 420, top = 12, width = 120, height = 18)

        assertEquals(412, slot.left)
        assertEquals(136, slot.width)
        assertEquals(8, slot.top)
        assertEquals(26, slot.height)
    }
}
