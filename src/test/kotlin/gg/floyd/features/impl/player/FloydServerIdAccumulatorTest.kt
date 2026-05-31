package gg.floyd.features.impl.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

class FloydServerIdAccumulatorTest {
    @Test
    fun `full server ids add full and abbreviated replacement targets`() {
        val accumulator = FloydServerIdAccumulator()

        assertTrue(accumulator.scanText("Server: mini28D"))

        assertEquals("mini28d", accumulator.currentId)
        assertEquals(listOf("mini28d", "m28d"), accumulator.cachedIds())
    }

    @Test
    fun `scoreboard abbreviations add abbreviation and possible full prefixes`() {
        val accumulator = FloydServerIdAccumulator()

        assertTrue(accumulator.scanScoreboardText(" m28D "))

        assertEquals("m28d", accumulator.currentId)
        assertEquals(listOf("m28d", "mini28d", "mega28d"), accumulator.cachedIds())
    }

    @Test
    fun `date suffix ids remain supported`() {
        val accumulator = FloydServerIdAccumulator()

        assertTrue(accumulator.scanText("05/19/26 mini28D"))

        assertEquals("mini28d", accumulator.currentId)
        assertEquals("05/19/26 fL0YD", accumulator.replaceDateServerId("05/19/26 mini28D", "fL0YD"))
    }

    @Test
    fun `date suffix replacement only replaces the first word after the date like Floyd`() {
        val accumulator = FloydServerIdAccumulator()

        assertEquals(
            "05/19/26 fL0YD later abc1",
            accumulator.replaceDateServerId("05/19/26 abc1 later abc1", "fL0YD")
        )
    }

    @Test
    fun `non ids do not mutate state`() {
        val accumulator = FloydServerIdAccumulator()

        assertFalse(accumulator.scanScoreboardText("Purse: 100 Coins"))

        assertEquals("", accumulator.currentId)
        assertEquals(emptyList(), accumulator.cachedIds())
    }

    @Test
    fun `sequence replacement preserves styles around changed text`() {
        val red = Style.EMPTY.withColor(ChatFormatting.RED)
        val blue = Style.EMPTY.withColor(ChatFormatting.BLUE)
        val sequence = sequenceOf(
            "Hello " to red,
            "mini28D" to blue,
            "!" to red
        )

        val replaced = FloydNickHider.replaceInSequenceForTest(sequence, "mini28D", "fL0YD")
        val collected = collect(replaced)

        assertEquals("Hello fL0YD!", collected.joinToString("") { it.first })
        assertEquals(red, collected[0].second)
        assertEquals(red, collected[5].second)
        assertEquals(blue, collected[6].second)
        assertEquals(blue, collected[10].second)
        assertEquals(red, collected[11].second)
    }

    @Test
    fun `sequence date suffix replacement only changes the date-following token`() {
        val red = Style.EMPTY.withColor(ChatFormatting.RED)
        val blue = Style.EMPTY.withColor(ChatFormatting.BLUE)
        val sequence = sequenceOf(
            "05/19/26 " to red,
            "abc1" to blue,
            " later abc1" to red
        )

        val styled = FloydNickHider.replaceDateServerIdInSequenceForTest(sequence, "fL0YD")
        val collected = collect(styled)

        assertEquals("05/19/26 fL0YD later abc1", collected.joinToString("") { it.first })
        assertEquals(blue, collected[9].second)
        assertEquals(red, collected[14].second)
    }

    @Test
    fun `component replacement preserves styles inside component segments`() {
        val red = Style.EMPTY.withColor(ChatFormatting.RED)
        val blue = Style.EMPTY.withColor(ChatFormatting.BLUE)
        val component = Component.empty()
            .append(Component.literal("Hello ").withStyle(red))
            .append(Component.literal("mini28D").withStyle(blue))
            .append(Component.literal("!").withStyle(red))

        val replaced = FloydNickHider.replaceInComponentForTest(component, "mini28D", "fL0YD")
        val segments = collect(replaced)

        assertEquals("Hello fL0YD!", replaced.string)
        assertEquals(listOf("Hello " to red, "fL0YD" to blue, "!" to red), segments)
    }

    private fun sequenceOf(vararg chunks: Pair<String, Style>): FormattedCharSequence =
        FormattedCharSequence { visitor ->
            var index = 0
            for ((text, style) in chunks) {
                val codePoints = text.codePoints().iterator()
                while (codePoints.hasNext()) {
                    val codePoint = codePoints.nextInt()
                    if (!visitor.accept(index++, style, codePoint)) return@FormattedCharSequence false
                }
            }
            true
        }

    private fun collect(sequence: FormattedCharSequence): List<Pair<String, Style>> {
        val result = mutableListOf<Pair<String, Style>>()
        sequence.accept { _, style, codePoint ->
            result.add(String(Character.toChars(codePoint)) to style)
            true
        }
        return result
    }

    private fun collect(component: Component): List<Pair<String, Style>> {
        val result = mutableListOf<Pair<String, Style>>()
        component.visit<Unit>(
            { style, content ->
                if (content.isNotEmpty()) result.add(content to style)
                java.util.Optional.empty()
            },
            Style.EMPTY
        )
        return result
    }
}
