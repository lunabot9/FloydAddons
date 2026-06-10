package gg.floyd.utils.font

/**
 * CPU-side result of one msdfgen glyph rasterization: the encoded RGBA cell bitmap (already
 * row-flipped to top-down, ready for upload) plus the autoframe transform needed to rebuild the
 * quad extents at bake time. Size-independent — the em-normalized autoframe means the same raster
 * serves any runtime font size, which is what makes the disk cache entries (D12) reusable.
 */
class MsdfGlyphRaster(
    val scale: Double,
    val translateX: Double,
    val translateY: Double,
    val rgba: ByteArray,
)

/**
 * Encode one msdfgen output channel. msdfgen emits UNCLAMPED floats (spike-observed -2.9..4.1
 * across the bundled font); the symmetric distance mapping puts the glyph edge exactly at 0.5, so
 * the encode must clamp to [0,1] and quantize verbatim per channel (D5 normative encode). 0.5 maps
 * to 128 — the shader's `sd - 0.5` edge test depends on that midpoint.
 */
internal fun encodeMsdfChannel(value: Float): Byte =
    Math.round(value.coerceIn(0.0F, 1.0F) * 255.0F).toByte()
