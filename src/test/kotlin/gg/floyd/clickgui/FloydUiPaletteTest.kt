package gg.floyd.clickgui

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloydUiPaletteTest {
    @Test fun `all six presets cover every customizable role`() {
        assertEquals(6, FloydUiPalette.presets.size)
        FloydUiPalette.presets.forEach { (name, palette) ->
            assertEquals(FloydUiPalette.roles.keys, palette.colors.keys, name)
            assertTrue(palette.colors.values.all { it ushr 24 == 255 }, "$name has an opaque base")
        }
        assertEquals(6, FloydUiPalette.presets.values.map { it["Accent"] }.distinct().size)
    }

    @Test fun `Golden Amber retains the original Floyd menu colors`() {
        val amber = FloydUiPalette.presets.getValue("Golden Amber")
        assertEquals(0xFFFFB84D.toInt(), amber["Accent"])
        assertEquals(0xFF201911.toInt(), amber["Page Background"])
        assertEquals(0xFF1A1510.toInt(), amber["Sidebar Background"])
        assertEquals(0xFF62503A.toInt(), amber["Border"])
    }

    @Test fun `new installs default to Midnight Silver`() {
        assertEquals(FloydUiPalette.presets.getValue("Midnight Silver"), FloydUiPalette.default)
        assertEquals("Midnight Silver", FloydUiPalette.presets.keys.first())
    }

    @Test fun `preset labels remain readable on their surfaces`() {
        FloydUiPalette.presets.forEach { (name, palette) ->
            listOf("Page Background", "Sidebar Background", "Card Background", "Control Background", "Popup Background").forEach { surface ->
                assertTrue(contrast(palette["Primary Text"], palette[surface]) >= 4.5, "$name primary on $surface")
                assertTrue(contrast(palette["Secondary Text"], palette[surface]) >= 4.5, "$name secondary on $surface")
            }
            assertTrue(contrast(palette["Accent Text"], palette["Accent"]) >= 4.5, "$name selected label")
        }
    }

    private fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color ushr shift) and 255) / 255.0
                return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}
