package gg.floyd.utils.render

import gg.floyd.utils.ui.rendering.Image
import gg.floyd.utils.ui.rendering.NVGRenderer
import java.util.Locale

/**
 * Shared, lazy block-icon cache for the list-management widgets (Xray, BlockSearch, etc.).
 *
 * Icons load on first request from "/assets/minecraft/textures/block/{name}.png". Loading
 * is best-effort and never blocks render: a failed or missing texture is cached as a miss
 * so it is attempted only once, and callers draw a gray placeholder box in that case.
 *
 * Multiple blocks can map to the same texture name; pass an explicit [textureName] when the
 * block id does not directly name its texture.
 */
object BlockIconCache {

    // null value = attempted and unavailable; absent key = not yet attempted.
    private val cache = HashMap<String, Image?>()

    /**
     * Returns the loaded NVG image for [id] (e.g. "minecraft:diamond_ore"), or null if the
     * texture is unavailable. Never throws; result is memoized so loading is attempted once.
     * Safe to call every frame from the render loop.
     */
    fun get(id: String, textureName: String = defaultTextureName(id)): Image? {
        if (cache.containsKey(id)) return cache[id]
        val image = try {
            NVGRenderer.createImage("/assets/minecraft/textures/block/$textureName.png")
        } catch (_: Throwable) {
            // Missing/unreadable texture: cache the miss so we don't retry every frame.
            null
        }
        cache[id] = image
        return image
    }

    /** Derive the texture file name from a registry id ("minecraft:diamond_ore" -> "diamond_ore"). */
    fun defaultTextureName(id: String): String =
        id.substringAfter(':', id).lowercase(Locale.ROOT)
}
