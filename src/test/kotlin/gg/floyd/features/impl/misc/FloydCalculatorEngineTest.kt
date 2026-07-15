package gg.floyd.features.impl.misc

import kotlin.test.Test
import kotlin.test.assertEquals

class FloydCalculatorEngineTest {
    @Test
    fun `standard mode evaluates operations immediately`() {
        val calculator = FloydCalculatorEngine()
        calculator.digit(2)
        calculator.binary(CalculatorBinaryOperation.ADD)
        calculator.digit(3)
        calculator.binary(CalculatorBinaryOperation.MULTIPLY)
        calculator.digit(4)
        calculator.equals()

        assertEquals("20", calculator.display)
        assertEquals("5 × 4 =", calculator.expression)
    }

    @Test
    fun `equals repeats the last operation`() {
        val calculator = FloydCalculatorEngine()
        calculator.digit(7)
        calculator.binary(CalculatorBinaryOperation.ADD)
        calculator.digit(2)
        calculator.equals()
        calculator.equals()

        assertEquals("11", calculator.display)
        assertEquals("9 + 2 =", calculator.expression)
    }

    @Test
    fun `divide by zero reports error and next digit starts fresh`() {
        val calculator = FloydCalculatorEngine()
        calculator.digit(9)
        calculator.binary(CalculatorBinaryOperation.DIVIDE)
        calculator.digit(0)
        calculator.equals()
        assertEquals("Cannot divide by zero", calculator.display)

        calculator.digit(4)
        assertEquals("4", calculator.display)
    }

    @Test
    fun `percent follows windows addition semantics`() {
        val calculator = FloydCalculatorEngine()
        calculator.digit(2)
        calculator.digit(0)
        calculator.digit(0)
        calculator.binary(CalculatorBinaryOperation.ADD)
        calculator.digit(1)
        calculator.digit(0)
        calculator.percent()
        calculator.equals()

        assertEquals("220", calculator.display)
    }

    @Test
    fun `history stores newest calculations and can recall or clear results`() {
        val calculator = FloydCalculatorEngine()
        calculator.digit(2)
        calculator.binary(CalculatorBinaryOperation.ADD)
        calculator.digit(3)
        calculator.equals()
        calculator.clearAll()
        calculator.digit(9)
        calculator.binary(CalculatorBinaryOperation.DIVIDE)
        calculator.digit(3)
        calculator.equals()

        assertEquals(2, calculator.history.size)
        assertEquals("9 ÷ 3 =", calculator.history[0].expression)
        assertEquals("3", calculator.history[0].result)
        assertEquals("2 + 3 =", calculator.history[1].expression)
        assertEquals("5", calculator.history[1].result)

        calculator.recallHistory(calculator.history[1])
        assertEquals("5", calculator.display)
        calculator.clearHistory()
        assertEquals(emptyList(), calculator.history)
    }
}
