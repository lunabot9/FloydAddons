package gg.floyd.utils

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import java.awt.Color.HSBtoRGB
import java.awt.Color.RGBtoHSB
import java.lang.reflect.Type

@JsonAdapter(Color.ColorSerializer::class)
class Color(hue: Float, saturation: Float, brightness: Float, alpha: Float = 1f) {
    constructor(hsb: FloatArray, alpha: Float = 1f) : this(hsb[0], hsb[1], hsb[2], alpha)
    constructor(r: Int, g: Int, b: Int, alpha: Float = 1f) : this(RGBtoHSB(r, g, b, FloatArray(size = 3)), alpha)
    constructor(rgba: Int) : this(rgba.red, rgba.green, rgba.blue, alpha = rgba.alpha / 255f)
    constructor(rgba: Int, alpha: Float) : this(rgba.red, rgba.green, rgba.blue, alpha)
    constructor(hex: String) : this(
        hex.take(2).toInt(16),
        hex.substring(2, 4).toInt(16),
        hex.substring(4, 6).toInt(16),
        hex.substring(6, 8).toInt(16) / 255f
    )

    var hue = hue
        set(value) {
            field = value
            needsUpdate = true
        }

    var saturation = saturation
        set(value) {
            field = value
            needsUpdate = true
        }

    var brightness = brightness
        set(value) {
            field = value
            needsUpdate = true
        }

    var alphaFloat = alpha
        set(value) {
            field = value
            needsUpdate = true
        }

    /**
     * When enabled, [rgba] cycles through the rainbow over time (preserving alpha).
     * Stored on the color itself so chroma lives inside the color picker rather
     * than as a separate sibling toggle.
     */
    var chroma: Boolean = false

    /**
     * When enabled, [rgba] fades over time between [baseRgba] and [fadeColor]. Mutually exclusive
     * with [chroma] in the picker UI. Lives on the color so fade is configured inside the color
     * picker rather than as a separate sibling toggle.
     */
    var fade: Boolean = false

    /**
     * Secondary color blended toward when [fade] is on. Lazily created (defaulting to cyan) so a
     * plain color never recursively allocates a fade color at construction; the nested color's own
     * [chroma]/[fade] are never used.
     */
    @Transient
    private var fadeColorBacking: Color? = null
    var fadeColor: Color
        get() = fadeColorBacking ?: Color(DEFAULT_FADE_RGBA).also { fadeColorBacking = it }
        set(value) { fadeColorBacking = value }

    /** The backing fade color without lazily creating one (for serialization). */
    internal fun fadeColorOrNull(): Color? = fadeColorBacking

    /**
     * Used to tell the [baseRgba] value to update when the HSBA values are changed.
     *
     * @see baseRgba
     */
    @Transient
    private var needsUpdate = true

    @Transient
    private var cachedBaseRgba: Int = 0

    /**
     * RGBA computed from the stored HSBA. Chroma-independent, used for persistence.
     */
    val baseRgba: Int
        get() {
            if (needsUpdate) {
                cachedBaseRgba =
                    (HSBtoRGB(hue, saturation, brightness) and 0X00FFFFFF) or ((this.alphaFloat * 255).toInt() shl 24)
                needsUpdate = false
            }
            return cachedBaseRgba
        }

    /**
     * Display RGBA. Cycles through chroma when [chroma] is enabled, otherwise [baseRgba].
     */
    val rgba: Int
        get() = when {
            chroma -> (ChromaCache.rgbFor(0f)) or ((this.alphaFloat * 255).toInt() shl 24)
            fade -> blendArgb(baseRgba, fadeColor.baseRgba, fadeProgress(0f))
            else -> baseRgba
        }

    inline val red get() = rgba.red
    inline val green get() = rgba.green
    inline val blue get() = rgba.blue
    inline val alpha get() = rgba.alpha

    inline val redFloat get() = red / 255f
    inline val greenFloat get() = green / 255f
    inline val blueFloat get() = blue / 255f

    @OptIn(ExperimentalStdlibApi::class)
    fun hex(includeAlpha: Boolean = true): String {
        val hexString = baseRgba.toHexString(HexFormat.UpperCase)
        return if (includeAlpha) hexString.substring(2) + hexString.take(2)
        else hexString.substring(2)
    }

    /**
     * Checks if color isn't visible.
     * Main use is to prevent rendering when the color is invisible.
     */
    inline val isTransparent: Boolean
        get() = this.alpha == 0

    override fun toString(): String = "Color(red=$red,green=$green,blue=$blue,alpha=$alpha)"

    override fun hashCode(): Int {
        var result = baseRgba
        result = 31 * result + chroma.hashCode()
        result = 31 * result + fade.hashCode()
        if (fade) result = 31 * result + fadeColor.baseRgba
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other is Color) {
            return baseRgba == other.baseRgba && chroma == other.chroma && fade == other.fade &&
                (!fade || fadeColor.baseRgba == other.fadeColor.baseRgba)
        }
        return false
    }

    /** Sets this color's HSBA in place from an ARGB int, preserving the [chroma]/[fade] flags. */
    fun applyRgba(argb: Int) {
        val hsb = RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, FloatArray(3))
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
        alphaFloat = ((argb shr 24) and 0xFF) / 255f
    }

    fun copy(): Color = Color(this.baseRgba).also {
        it.chroma = this.chroma
        it.fade = this.fade
        this.fadeColorBacking?.let { fc -> it.fadeColor = Color(fc.baseRgba) }
    }

    private class ColorSerializer : JsonDeserializer<Color>, JsonSerializer<Color> {
        override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext?): Color {
            // Object form { "hex": "#RRGGBBAA", "chroma", "fade", "fadeColor" }; legacy string "#RRGGBBAA".
            if (json.isJsonObject) {
                val obj = json.asJsonObject
                val hex = obj.get("hex")?.asString ?: "#FFFFFFFF"
                return Color(hex.removePrefix("#")).also {
                    it.chroma = obj.get("chroma")?.asBoolean ?: false
                    it.fade = obj.get("fade")?.asBoolean ?: false
                    obj.get("fadeColor")?.asString?.let { fc -> it.fadeColor = Color(fc.removePrefix("#")) }
                }
            }
            return Color(json.asString.drop(1))
        }

        override fun serialize(color: Color, type: Type, context: JsonSerializationContext?): JsonElement {
            val obj = JsonObject()
            obj.addProperty("hex", "#${color.hex()}")
            obj.addProperty("chroma", color.chroma)
            // Keep plain colors as { hex, chroma } (unchanged on disk); only carry fade fields when used.
            if (color.fade) obj.addProperty("fade", true)
            color.fadeColorOrNull()?.let { obj.addProperty("fadeColor", "#${it.hex()}") }
            return obj
        }
    }

    companion object {
        /** Default secondary color for [fade] (cyan), matching the old Border Fade Color default. */
        const val DEFAULT_FADE_RGBA: Int = 0xFF55FFFF.toInt()

        /** Time-driven fade progress (0..1), phased by [offset]; matches HudPanel's fade curve. */
        private fun fadeProgress(offset: Float): Float {
            val angle = (((System.currentTimeMillis() % 2500L) / 2500f) + offset) * (2f * Math.PI.toFloat())
            return ((kotlin.math.sin(angle) + 1f) * 0.5f).coerceIn(0f, 1f)
        }

        /** Per-channel ARGB lerp from [start] to [end] by [progress] (0..1). */
        private fun blendArgb(start: Int, end: Int, progress: Float): Int {
            val t = progress.coerceIn(0f, 1f)
            val sa = start ushr 24 and 0xFF; val sr = start ushr 16 and 0xFF
            val sg = start ushr 8 and 0xFF; val sb = start and 0xFF
            val ea = end ushr 24 and 0xFF; val er = end ushr 16 and 0xFF
            val eg = end ushr 8 and 0xFF; val eb = end and 0xFF
            val a = (sa + (ea - sa) * t).toInt(); val r = (sr + (er - sr) * t).toInt()
            val g = (sg + (eg - sg) * t).toInt(); val b = (sb + (eb - sb) * t).toInt()
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        inline val Int.red get() = this shr 16 and 0xFF
        inline val Int.green get() = this shr 8 and 0xFF
        inline val Int.blue get() = this and 0xFF
        inline val Int.alpha get() = this shr 24 and 0xFF

        fun Color.brighter(factor: Float = 1.3f): Color {
            return Color(
                hue, saturation, (brightness * factor.coerceAtLeast(1f)).coerceAtMost(1f),
                this.alphaFloat
            )
        }

        fun Color.darker(factor: Float = 0.7f): Color {
            return Color(hue, saturation, brightness * factor, this.alphaFloat)
        }

        fun Color.withAlpha(alpha: Float, newInstance: Boolean = true): Color {
            return if (newInstance) Color(red, green, blue, alpha)
            else {
                this.alphaFloat = alpha
                this
            }
        }

        fun Color.multiplyAlpha(factor: Float): Color {
            return Color(red, green, blue, (alphaFloat * factor).coerceIn(0f, 1f))
        }

        fun Color.hsbMax(): Color {
            return Color(hue, 1f, 1f)
        }
    }
}

/**
 * Memoizes the rotating chroma RGB (`HSBtoRGB(hue, 1, 1)` with a time-driven hue) so the
 * relatively expensive `java.awt.Color.HSBtoRGB` conversion runs at most once per frame per
 * distinct hue offset instead of once per corner / panel / ESP box / line endpoint per frame.
 *
 * The hue is fully determined by the integer `System.currentTimeMillis() % 4000` plus the
 * caller's offset, so caching on that exact time bucket is lossless: the returned RGB is
 * byte-identical to recomputing it, while every call within the same frame (same millisecond
 * bucket) reuses the cached value. The result is the lower 24 bits (`0x00RRGGBB`); callers OR in
 * their own alpha / opaque high byte.
 */
object ChromaCache {
    private const val PERIOD_MS = 4000L

    private class Slot(@JvmField var bucket: Long, @JvmField var rgb: Int)

    private val slots = java.util.concurrent.ConcurrentHashMap<Float, Slot>()

    /** Lower 24 bits (`0x00RRGGBB`) of the rotating chroma color for [offset], cached per frame. */
    fun rgbFor(offset: Float): Int {
        val bucket = System.currentTimeMillis() % PERIOD_MS
        val slot = slots.getOrPut(offset) { Slot(-1L, 0) }
        if (slot.bucket != bucket) {
            val hue = ((bucket / PERIOD_MS.toFloat()) + offset) % 1f
            slot.rgb = HSBtoRGB(hue, 1f, 1f) and 0x00FFFFFF
            slot.bucket = bucket
        }
        return slot.rgb
    }
}

object Colors {

    @JvmField val MINECRAFT_DARK_BLUE = Color(0, 0, 170)
    @JvmField val MINECRAFT_DARK_GREEN = Color(0, 170, 0)
    @JvmField val MINECRAFT_DARK_AQUA = Color(0, 170, 170)
    @JvmField val MINECRAFT_DARK_RED = Color(170, 0, 0)
    @JvmField val MINECRAFT_DARK_PURPLE = Color(170, 0, 170)
    @JvmField val MINECRAFT_GOLD = Color(255, 170, 0)
    @JvmField val MINECRAFT_GRAY = Color(170, 170, 170)
    @JvmField val MINECRAFT_DARK_GRAY = Color(85, 85, 85)
    @JvmField val MINECRAFT_BLUE = Color(85, 85, 255)
    @JvmField val MINECRAFT_GREEN = Color(85, 255, 85)
    @JvmField val MINECRAFT_AQUA = Color(85, 255, 255)
    @JvmField val MINECRAFT_RED = Color(255, 85, 85)
    @JvmField val MINECRAFT_LIGHT_PURPLE = Color(255, 85, 255)
    @JvmField val MINECRAFT_YELLOW = Color(255, 255, 85)
    @JvmField val WHITE = Color(255, 255, 255)
    @JvmField val BLACK = Color(0, 0, 0)
    @JvmField val TRANSPARENT = Color(0, 0, 0, 0f)

    @JvmField val gray38 = Color(38, 38, 38)
    @JvmField val gray26 = Color(26, 26, 26)

    /** Shared accent color modules default to (overridable per module). */
    @JvmField val ACCENT = Color(50, 150, 220)
}