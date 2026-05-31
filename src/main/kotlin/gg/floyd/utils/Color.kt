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
        get() = if (chroma) {
            (ChromaCache.rgbFor(0f)) or ((this.alphaFloat * 255).toInt() shl 24)
        } else baseRgba

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
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other is Color) {
            return baseRgba == other.baseRgba && chroma == other.chroma
        }
        return false
    }

    fun copy(): Color = Color(this.baseRgba).also { it.chroma = this.chroma }

    private class ColorSerializer : JsonDeserializer<Color>, JsonSerializer<Color> {
        override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext?): Color {
            // Object form { "hex": "#RRGGBBAA", "chroma": bool }; legacy string form "#RRGGBBAA".
            if (json.isJsonObject) {
                val obj = json.asJsonObject
                val hex = obj.get("hex")?.asString ?: "#FFFFFFFF"
                return Color(hex.removePrefix("#")).also {
                    it.chroma = obj.get("chroma")?.asBoolean ?: false
                }
            }
            return Color(json.asString.drop(1))
        }

        override fun serialize(color: Color, type: Type, context: JsonSerializationContext?): JsonElement {
            val obj = JsonObject()
            obj.addProperty("hex", "#${color.hex()}")
            obj.addProperty("chroma", color.chroma)
            return obj
        }
    }

    companion object {
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