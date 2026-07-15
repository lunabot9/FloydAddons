package gg.floyd.features.impl.misc

import kotlin.test.Test
import kotlin.test.assertTrue

class FloydCalculatorOutlineTest {
    @Test
    fun `calculator outline stays inside right and bottom pip edges`() {
        val bounds = calculatorOutlineBounds(330f, 494f, 2f)
        val halfStroke = 1f

        assertTrue(bounds.x + bounds.width + halfStroke <= 329f)
        assertTrue(bounds.y + bounds.height + halfStroke <= 493f)
    }
}
