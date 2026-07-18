package gg.floyd.features.impl.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FloydDarkModeTest {
    @Test
    fun `overlay color clamps to transparent black at zero percent`() {
        assertEquals(0x00000000, FloydDarkMode.overlayColor(0f))
        assertEquals(0x00000000, FloydDarkMode.overlayColor(-25f))
    }

    @Test
    fun `overlay color scales alpha from percentage`() {
        assertEquals(0x73000000, FloydDarkMode.overlayColor(45f))
        assertEquals(0xFF000000.toInt(), FloydDarkMode.overlayColor(100f))
        assertEquals(0xFF000000.toInt(), FloydDarkMode.overlayColor(175f))
    }
}
