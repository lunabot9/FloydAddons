package gg.floyd.features.impl.render

import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import kotlin.test.Test
import kotlin.test.assertEquals

class FloydCustomScoreboardFontStyleTest {
    @Test
    fun `brand keeps one stable segment per glyph when fade colors match`() {
        val segments = FloydCustomScoreboard.scoreboardBrandSegments(
            listOf("F", "l", "o", "y", "d"),
        ) { 0xFF55FFFF.toInt() }

        assertEquals(listOf("F", "l", "o", "y", "d"), segments.map { it.text })
        assertEquals(5, segments.size)
    }

    @Test
    fun `scoreboard segments preserve explicit icon font`() {
        val iconFont = FontDescription.Resource(Identifier.fromNamespaceAndPath("hypixel_skyblock", "icons"))
        val iconStyle = Style.EMPTY.withFont(iconFont)
        val source = FormattedCharSequence { visitor ->
            visitor.accept(0, Style.EMPTY, 'A'.code) &&
                visitor.accept(1, iconStyle, 0xE001) &&
                visitor.accept(2, Style.EMPTY, 'B'.code)
        }

        val segments = FloydCustomScoreboard.scoreboardSegments(source)

        assertEquals(listOf(Style.EMPTY.font, iconFont, Style.EMPTY.font), segments.map { it.style.font })
        assertEquals(listOf("A", String(Character.toChars(0xE001)), "B"), segments.map { it.text })

        val renderedFonts = mutableListOf<FontDescription>()
        segments.forEach { segment ->
            segment.formatted().accept { _, style, _ ->
                renderedFonts += style.font
                true
            }
        }
        assertEquals(listOf(Style.EMPTY.font, iconFont, Style.EMPTY.font), renderedFonts)
    }
}
