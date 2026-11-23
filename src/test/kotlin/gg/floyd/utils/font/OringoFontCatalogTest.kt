package gg.floyd.utils.font

import java.awt.Font
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class OringoFontCatalogTest {
    @TempDir lateinit var directory:Path
    @Test fun `all original presets keep their exact faces and sizes including tahoma small alias`() {
        assertEquals(8,OringoFontCatalog.presets.size)
        assertEquals(4,OringoFontCatalog.faces.size)
        assertEquals(listOf(18.0,19.0,20.0,19.0,22.0,22.0,19.0,38.0),OringoFontCatalog.presets.map { it.size })
        assertEquals("roboto-medium",OringoFontCatalog.presets.single { it.id=="tahoma-small" }.face)
        assertEquals("roboto",OringoFontCatalog.presets.single { it.id=="roboto-medium" }.face)
    }
    @Test fun `bundled roboto fonts are valid distinct faces and existing custom files remain untouched`() {
        val regular=OringoFontCatalog.materialize(OringoFontCatalog.presets.first(),directory)!!
        val medium=OringoFontCatalog.materialize(OringoFontCatalog.presets.single { it.id=="roboto-big" },directory)!!
        assertTrue(Files.newInputStream(regular).use { Font.createFont(Font.TRUETYPE_FONT,it) }.family.contains("Roboto"))
        assertTrue(Files.newInputStream(medium).use { Font.createFont(Font.TRUETYPE_FONT,it) }.family.contains("Roboto"))
        assertFalse(Files.readAllBytes(regular).contentEquals(Files.readAllBytes(medium)))
        val custom=byteArrayOf(1,2,3);Files.write(regular,custom)
        assertEquals(regular,OringoFontCatalog.materialize(OringoFontCatalog.presets.first(),directory))
        assertArrayEquals(custom,Files.readAllBytes(regular))
    }
}
