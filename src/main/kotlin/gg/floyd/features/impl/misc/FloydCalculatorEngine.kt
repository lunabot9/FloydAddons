package gg.floyd.features.impl.misc

import java.math.BigDecimal
import java.util.Locale

internal enum class CalculatorBinaryOperation(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷"),
}

internal data class CalculatorHistoryEntry(val expression: String, val result: String)

/** Stateful, immediate-execution arithmetic used by the Standard calculator screen. */
internal class FloydCalculatorEngine {
    var display: String = "0"
        private set
    var expression: String = ""
        private set
    private val historyEntries = ArrayDeque<CalculatorHistoryEntry>()
    val history: List<CalculatorHistoryEntry> get() = historyEntries.toList()

    private var accumulator: Double? = null
    private var pendingOperation: CalculatorBinaryOperation? = null
    private var lastOperation: CalculatorBinaryOperation? = null
    private var lastOperand: Double? = null
    private var replaceEntry = true
    private var error = false

    fun digit(digit: Int) {
        require(digit in 0..9)
        recoverFromError()
        if (replaceEntry) {
            display = digit.toString()
            replaceEntry = false
            if (pendingOperation == null) expression = ""
            return
        }
        val digitCount = display.count(Char::isDigit)
        if (digitCount >= MAX_ENTRY_DIGITS || display == "0") {
            if (display == "0") display = digit.toString()
            return
        }
        display += digit
    }

    fun decimal() {
        recoverFromError()
        if (replaceEntry) {
            display = "0."
            replaceEntry = false
            if (pendingOperation == null) expression = ""
        } else if ('.' !in display) {
            display += "."
        }
    }

    fun clearEntry() {
        if (error) recoverFromError()
        display = "0"
        replaceEntry = true
    }

    fun clearAll() {
        display = "0"
        expression = ""
        accumulator = null
        pendingOperation = null
        lastOperation = null
        lastOperand = null
        replaceEntry = true
        error = false
    }

    fun backspace() {
        if (error) {
            clearAll()
            return
        }
        if (replaceEntry) return
        display = display.dropLast(1).takeUnless { it.isEmpty() || it == "-" } ?: "0"
    }

    fun percent() {
        recoverFromError()
        val current = currentValue() ?: return
        val value = when (pendingOperation) {
            CalculatorBinaryOperation.ADD, CalculatorBinaryOperation.SUBTRACT ->
                (accumulator ?: 0.0) * current / 100.0
            else -> current / 100.0
        }
        setFiniteValue(value)
        replaceEntry = false
    }

    fun square() {
        val value = currentValue()
        applyUnary("sqr", value?.let { it * it }, "Invalid input")
    }

    fun binary(operation: CalculatorBinaryOperation) {
        recoverFromError()
        val current = currentValue() ?: return
        if (pendingOperation != null && !replaceEntry) {
            val left = accumulator ?: current
            val result = calculate(left, current, pendingOperation!!) ?: return
            accumulator = result
            setFiniteValue(result)
        } else {
            accumulator = current
        }
        pendingOperation = operation
        lastOperation = null
        lastOperand = null
        expression = "${format(accumulator!!)} ${operation.symbol}"
        replaceEntry = true
    }

    fun equals() {
        recoverFromError()
        val operation = pendingOperation
        if (operation != null) {
            val left = accumulator ?: currentValue() ?: return
            val operand = if (replaceEntry) left else currentValue() ?: return
            val result = calculate(left, operand, operation) ?: return
            expression = "${format(left)} ${operation.symbol} ${format(operand)} ="
            setFiniteValue(result)
            recordHistory(expression, display)
            lastOperation = operation
            lastOperand = operand
            pendingOperation = null
            accumulator = null
            replaceEntry = true
            return
        }

        val repeatOperation = lastOperation ?: return
        val operand = lastOperand ?: return
        val left = currentValue() ?: return
        val result = calculate(left, operand, repeatOperation) ?: return
        expression = "${format(left)} ${repeatOperation.symbol} ${format(operand)} ="
        setFiniteValue(result)
        recordHistory(expression, display)
        replaceEntry = true
    }

    fun recallHistory(entry: CalculatorHistoryEntry) {
        display = entry.result
        expression = entry.expression
        accumulator = null
        pendingOperation = null
        lastOperation = null
        lastOperand = null
        replaceEntry = true
        error = false
    }

    fun clearHistory() {
        historyEntries.clear()
    }

    private fun applyUnary(label: String, value: Double?, failure: String) {
        recoverFromError()
        val previous = display
        if (value == null || !value.isFinite()) {
            setError(failure)
            return
        }
        expression = "$label($previous)"
        setFiniteValue(value)
        replaceEntry = false
    }

    private fun calculate(left: Double, right: Double, operation: CalculatorBinaryOperation): Double? {
        val result = when (operation) {
            CalculatorBinaryOperation.ADD -> left + right
            CalculatorBinaryOperation.SUBTRACT -> left - right
            CalculatorBinaryOperation.MULTIPLY -> left * right
            CalculatorBinaryOperation.DIVIDE -> {
                if (right == 0.0) {
                    setError("Cannot divide by zero")
                    return null
                }
                left / right
            }
        }
        if (!result.isFinite()) {
            setError("Overflow")
            return null
        }
        return result
    }

    private fun setFiniteValue(value: Double) {
        if (!value.isFinite()) {
            setError("Overflow")
            return
        }
        display = format(value)
        error = false
    }

    private fun setError(message: String) {
        display = message
        expression = ""
        accumulator = null
        pendingOperation = null
        lastOperation = null
        lastOperand = null
        replaceEntry = true
        error = true
    }

    private fun recoverFromError() {
        if (error) clearAll()
    }

    private fun recordHistory(historyExpression: String, result: String) {
        historyEntries.addFirst(CalculatorHistoryEntry(historyExpression, result))
        while (historyEntries.size > MAX_HISTORY_ENTRIES) historyEntries.removeLast()
    }

    private fun currentValue(): Double? = display.toDoubleOrNull()

    private fun format(value: Double): String {
        if (value == 0.0) return "0"
        val plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        if (plain.length <= MAX_DISPLAY_CHARS) return plain
        return String.format(Locale.ROOT, "%.10E", value)
            .replace("E+", "E")
            .replace(Regex("\\.0+(?=E)"), "")
    }

    private companion object {
        const val MAX_ENTRY_DIGITS = 16
        const val MAX_DISPLAY_CHARS = 18
        const val MAX_HISTORY_ENTRIES = 50
    }
}
