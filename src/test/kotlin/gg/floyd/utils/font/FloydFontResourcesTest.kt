package gg.floyd.utils.font

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FloydFontResourcesTest {
    private val resourceRoot: Path = Path.of("src", "main", "resources")

    @Test
    fun `floyd ships a global minecraft default font override`() {
        assertTrue(Files.exists(resourceRoot.resolve("assets/minecraft/font/default.json")))
    }

    @Test
    fun `dedicated floyd font resources remain available`() {
        assertNotNull(resourceRoot.resolve("assets/floydaddons/font/panel.json").takeIf(Files::exists))
        assertNotNull(resourceRoot.resolve("assets/floydaddons/font/clickgui.json").takeIf(Files::exists))
        assertNotNull(resourceRoot.resolve("assets/floydaddons/font/vanilla.json").takeIf(Files::exists))
    }
}
