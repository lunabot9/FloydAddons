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
        val defaultFont = resourceRoot.resolve("assets/minecraft/font/default.json")
        assertTrue(Files.exists(defaultFont))
        assertTrue(
            Files.readString(defaultFont).contains("\"file\": \"floydaddons:font.ttf\""),
            "minecraft:default must include the Floyd TTF entry that FloydFontProviders replaces with the selected font",
        )
    }

    @Test
    fun `dedicated floyd font resources remain available`() {
        assertNotNull(resourceRoot.resolve("assets/floydaddons/font/panel.json").takeIf(Files::exists))
        assertNotNull(resourceRoot.resolve("assets/floydaddons/font/clickgui.json").takeIf(Files::exists))
        assertNotNull(resourceRoot.resolve("assets/floydaddons/font/vanilla.json").takeIf(Files::exists))
    }

    @Test
    fun `msdf shader preserves gui colors without world lightmap inputs`() {
        val vertexShader = Files.readString(
            resourceRoot.resolve("assets/floydaddons/shaders/core/msdf_text.vsh"),
        )
        val fragmentShader = Files.readString(
            resourceRoot.resolve("assets/floydaddons/shaders/core/msdf_text.fsh"),
        )

        assertTrue(vertexShader.contains("#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)"))
        assertTrue(vertexShader.contains("vertexColor = Color;"))
        assertTrue(fragmentShader.contains("#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)"))
        assertTrue(fragmentShader.contains("fragColor = color;"))
    }
}
