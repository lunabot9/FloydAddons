package gg.floyd.utils

import gg.floyd.features.impl.render.FloydRender
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence
import java.util.Locale

object ChatChroma {
    private const val FLOYD_LITERAL = "FloydAddons"
    private const val CHROMA_SPEED_MS = 24.0
    private const val CHROMA_STEP_DEGREES = 12.0
    private const val CHROMA_SATURATION = 0.9f
    private const val CHROMA_BRIGHTNESS = 1.0f

    private val renderDepth = ThreadLocal.withInitial { 0 }

    fun beginRender() {
        renderDepth.set(renderDepth.get() + 1)
    }

    fun endRender() {
        renderDepth.set((renderDepth.get() - 1).coerceAtLeast(0))
    }

    fun transform(text: FormattedCharSequence): FormattedCharSequence {
        if (renderDepth.get() <= 0) return text
        return if (FloydRender.shouldUseFullChatChroma()) transformWholeSequence(text) else transformLiteral(text, FLOYD_LITERAL)
    }

    fun applyToStyle(style: Style, index: Int, baseIndex: Int = 0, speedMs: Double = CHROMA_SPEED_MS): Style =
        style.withColor(TextColor.fromRgb(chromaRgb(System.currentTimeMillis(), baseIndex + index, speedMs)))

    private fun transformWholeSequence(text: FormattedCharSequence): FormattedCharSequence {
        val now = System.currentTimeMillis()
        return FormattedCharSequence { sink ->
            text.accept { index, style, codePoint ->
                sink.accept(index, style.withColor(TextColor.fromRgb(chromaRgb(now, index))), codePoint)
            }
        }
    }

    private fun transformLiteral(text: FormattedCharSequence, literal: String): FormattedCharSequence {
        if (literal.isEmpty()) return text
        val codePoints = mutableListOf<Int>()
        val styles = mutableListOf<Style>()
        text.accept { _, style, codePoint ->
            codePoints.add(codePoint)
            styles.add(style)
            true
        }
        if (codePoints.isEmpty()) return text

        val rendered = buildString {
            for (codePoint in codePoints) appendCodePoint(codePoint)
        }
        if (rendered.length != codePoints.size) return text

        val lowerRendered = rendered.lowercase(Locale.ROOT)
        val lowerLiteral = literal.lowercase(Locale.ROOT)
        val chromaIndices = HashSet<Int>()
        var searchIndex = 0
        while (searchIndex < lowerRendered.length) {
            val hit = lowerRendered.indexOf(lowerLiteral, searchIndex)
            if (hit < 0) break
            for (offset in lowerLiteral.indices) chromaIndices.add(hit + offset)
            searchIndex = hit + lowerLiteral.length
        }
        if (chromaIndices.isEmpty()) return text

        val now = System.currentTimeMillis()
        return FormattedCharSequence { sink ->
            for (index in codePoints.indices) {
                val style = if (index in chromaIndices) {
                    styles[index].withColor(TextColor.fromRgb(chromaRgb(now, index)))
                } else {
                    styles[index]
                }
                if (!sink.accept(index, style, codePoints[index])) return@FormattedCharSequence false
            }
            true
        }
    }

    private fun chromaRgb(now: Long, index: Int, speedMs: Double = CHROMA_SPEED_MS): Int {
        val hue = (((now / speedMs) + index * CHROMA_STEP_DEGREES) % 360.0 / 360.0).toFloat()
        return java.awt.Color.HSBtoRGB(hue, CHROMA_SATURATION, CHROMA_BRIGHTNESS) and 0x00FFFFFF
    }
}
