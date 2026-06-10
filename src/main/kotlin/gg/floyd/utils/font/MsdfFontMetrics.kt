package gg.floyd.utils.font

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.utils.FloydFontProviders
import net.minecraft.network.chat.Component
import kotlin.math.floor

/**
 * Float-width text metrics for the mod's own UI surfaces (HUD panels, later the ClickGUI), sourced
 * from the LIVE `FontSet` resolution — never from a raw FreeType face (design D6).
 *
 * [width] sums the same style-aware `BakedGlyph.info().getAdvance(bold)` floats that `Font.width`
 * ceils: it goes through `Font`'s own [net.minecraft.client.StringSplitter], whose width provider
 * is `getGlyphSource(style.getFont()).getGlyph(cp).info().getAdvance(style.isBold())` (Font's
 * constructor). Two properties fall out of that sourcing by construction:
 *  - re-hinting at an arbitrary pixel size is impossible (the advances are the provider's hinted
 *    `round(size*oversample)` metrics, the exact values the renderer draws with), and
 *  - per-codepoint fallback is inherited — symbols like ❈/✪ measure with their unifont advances
 *    exactly as rendered — so measurement cannot diverge from rendering.
 *
 * The pixel mapping is `sizePx / 9`, matching the renderer's `matrix.scale(sizePx / 9)` over the
 * vanilla 9-unit line.
 */
object MsdfFontMetrics {

    /** Vanilla's 9-unit text line height (`Font.lineHeight`); the font-unit space all advances live in. */
    const val LINE_HEIGHT = 9f

    /** Width of [text] in FONT UNITS — the un-ceiled float `Font.width` rounds up, from the live FontSet. */
    fun unitWidth(text: String): Float = mc.font.splitter.stringWidth(text)

    /** Width in pixels of [text] rendered at [sizePx] (a 9-unit line scaled by `sizePx / 9`). */
    fun width(text: String, sizePx: Float): Float = unitWidth(text) * scaleFor(sizePx)

    data class WrappedBounds(val width: Float, val height: Float, val lineCount: Int)

    /**
     * Bounds in pixels of [text] word-wrapped to [maxWidth] px at [sizePx], computed with the SAME
     * `Font.split` splitter that wraps drawn text, with vanilla's 9-unit line height — so a box
     * sized by this always contains the text the renderer wraps into it.
     */
    fun wrappedBounds(text: String, maxWidth: Float, sizePx: Float): WrappedBounds {
        val lines = mc.font.split(Component.literal(text), wrapUnitsFor(maxWidth, sizePx))
        val scale = scaleFor(sizePx)
        var widest = 0f
        for (line in lines) widest = maxOf(widest, mc.font.splitter.stringWidth(line))
        return WrappedBounds(widest * scale, lineHeightPx(lines.size, sizePx), lines.size)
    }

    /** Pixels per font unit at [sizePx]: the renderer's `matrix.scale(sizePx / 9)` mapping. */
    fun scaleFor(sizePx: Float): Float = sizePx / LINE_HEIGHT

    /** Total pixel height of [lineCount] 9-unit lines rendered at [sizePx]. */
    fun lineHeightPx(lineCount: Int, sizePx: Float): Float = lineCount * LINE_HEIGHT * scaleFor(sizePx)

    /** [maxWidthPx] converted to the integer FONT-UNIT budget `Font.split` wraps against (≥ 1). */
    internal fun wrapUnitsFor(maxWidthPx: Float, sizePx: Float): Int {
        val scale = scaleFor(sizePx)
        if (scale <= 0f) return 1
        return floor(maxWidthPx / scale).toInt().coerceAtLeast(1)
    }
}

/**
 * Session-lived measurement cache for the ClickGUI's construction-time width caches. Widths now
 * come from the LIVE FontSet (see [MsdfFontMetrics.width]), so they change on every font reload —
 * [get] re-runs [compute] lazily whenever the font epoch ([FloydFontProviders.fontEpoch], bumped
 * each time the `minecraft:default` provider list is rebuilt) has moved, keeping cached layout
 * widths in lockstep with what the renderer actually draws. Render-thread use only (matching the
 * widgets that own these caches) — no synchronization. [compute] stays as lazy as the `val`/`lazy`
 * it replaces: nothing is measured before the first [get], so converted sites keep their
 * construct-before-GL safety.
 */
class FontEpochCache<T : Any>(
    private val epoch: () -> Int = { FloydFontProviders.fontEpoch() },
    private val compute: () -> T,
) {
    private var cachedEpoch = 0
    private var cached: T? = null

    fun get(): T {
        val currentEpoch = epoch()
        val value = cached
        if (value != null && cachedEpoch == currentEpoch) return value
        val computed = compute()
        cached = computed
        cachedEpoch = currentEpoch
        return computed
    }

    /** Drops the cached value so the next [get] recomputes (for caches whose input text changed). */
    fun invalidate() {
        cached = null
    }
}
