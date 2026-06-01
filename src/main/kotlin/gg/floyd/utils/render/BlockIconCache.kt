package gg.floyd.utils.render

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.floyd.utils.ui.rendering.Image
import gg.floyd.utils.ui.rendering.NVGRenderer
import java.util.Locale

/**
 * Shared, lazy block-icon cache for the list-management widgets (Xray, BlockSearch, etc.).
 *
 * An icon is loaded on first request and memoized (including misses), so loading is attempted
 * exactly once per id and is safe to call every frame from the render loop. Resolution walks the
 * vanilla model/blockstate assets on the classpath — never GL — to find a real texture, falling
 * back in order:
 *
 *  1. the block's **item/block model** texture (covers stairs/slabs/walls, command blocks,
 *     reinforced_deepslate, etc. whose textures are not named after the block id), resolving the
 *     `parent` chain and `#placeholder` references the same way the vanilla model loader does;
 *  2. the block's **blockstate** first referenced model, for blocks with no item/block model of
 *     their own (e.g. redstone_wire -> redstone_dust_dot, wall_torch -> torch);
 *  3. a **direct** `block/{name}.png` by id (the original behaviour);
 *  4. the existing **gray placeholder** (a null result; the caller draws the placeholder box).
 *
 * Only step 4 ever reaches GL (via [NVGRenderer.createImage], the existing lazy image path); all
 * resolution above it is pure resource/JSON reading. Every step is wrapped so a malformed or
 * missing asset can never throw into the render thread.
 */
object BlockIconCache {

    // null value = attempted and unavailable; absent key = not yet attempted.
    private val cache = HashMap<String, Image?>()

    // Bounds the model parent walk so a malformed/cyclic parent chain can never spin.
    private const val MAX_PARENT_DEPTH = 16

    /**
     * Returns the loaded NVG image for [id] (e.g. "minecraft:diamond_ore"), or null if no texture
     * could be resolved. Never throws; the result (hit or miss) is memoized so loading is attempted
     * once. Safe to call every frame from the render loop.
     *
     * Pass an explicit [textureName] to force a specific `block/{textureName}.png`; otherwise the
     * texture is resolved from the vanilla model assets, then from the block id directly.
     */
    fun get(id: String, textureName: String? = null): Image? {
        if (cache.containsKey(id)) return cache[id]
        val image = try {
            val resolved = textureName ?: resolveTextureName(id) ?: defaultTextureName(id)
            loadTexture(resolved) ?: loadTexture(defaultTextureName(id))
        } catch (_: Throwable) {
            // Any unexpected failure is treated as a miss so we never retry or crash the render pass.
            null
        }
        cache[id] = image
        return image
    }

    /**
     * Debug-only: the texture resource path [get] would load for [id], or null if no texture
     * resolves. Pure resource reading (no GL) so it is safe to batch over the whole block registry;
     * used by the control bridge's icon-coverage check to find blocks that render without an icon.
     */
    fun debugResolvedPath(id: String): String? {
        val resolved = try { resolveTextureName(id) } catch (_: Throwable) { null }
        for (candidate in listOfNotNull(resolved, defaultTextureName(id))) {
            val rel = if (candidate.contains('/')) candidate else "block/$candidate"
            val path = "/assets/minecraft/textures/$rel.png"
            if (this::class.java.getResource(path) != null) return path
        }
        return null
    }

    /**
     * Loads a block texture png into NanoVG, or null if it is missing/unreadable. A bare name is
     * treated as `block/{name}` (the common case); a category-qualified name (e.g. `item/bamboo_door`
     * for doors/signs/hanging-signs, whose 2D icon lives under `textures/item/`) is loaded as-is,
     * relative to `textures/`.
     */
    private fun loadTexture(textureName: String): Image? = try {
        val rel = if (textureName.contains('/')) textureName else "block/$textureName"
        NVGRenderer.createImage("/assets/minecraft/textures/$rel.png")
    } catch (_: Throwable) {
        null
    }

    /** Derive the texture file name from a registry id ("minecraft:diamond_ore" -> "diamond_ore"). */
    fun defaultTextureName(id: String): String =
        id.substringAfter(':', id).lowercase(Locale.ROOT)

    /**
     * Resolves a `block/`-relative texture name for [id] from the vanilla assets, or null if none
     * is found. Pure resource reading (no GL): item model -> block model -> blockstate.
     */
    private fun resolveTextureName(id: String): String? {
        val name = defaultTextureName(id)
        return resolveFromItemModel(name)
            ?: resolveFromBlockModel(name)
            ?: resolveFromBlockState(name)
    }

    /**
     * Reads `items/{name}.json` and follows `model.model` to either a block model or an item model.
     * Item models expose their flat icon via `layer0`, which is what we want for a 2D list icon.
     */
    private fun resolveFromItemModel(name: String): String? {
        val item = readJson("/assets/minecraft/items/$name.json") ?: return null
        val modelId = item.getAsJsonObject("model")
            ?.getAsJsonPrimitive("model")?.asString ?: return null
        return resolveModelTexture(modelResourcePath(modelId))
    }

    /** Reads `models/block/{name}.json` directly (covers blocks whose item just points back here). */
    private fun resolveFromBlockModel(name: String): String? =
        resolveModelTexture("/assets/minecraft/models/block/$name.json")

    /**
     * For blocks with no model of their own (e.g. redstone_wire, wall_torch), reads the blockstate
     * and resolves the first model it references — enough to pick a representative texture.
     */
    private fun resolveFromBlockState(name: String): String? {
        val state = readJson("/assets/minecraft/blockstates/$name.json") ?: return null
        val modelId = firstModelReference(state) ?: return null
        return resolveModelTexture(modelResourcePath(modelId))
    }

    /** Maps a model id ("minecraft:block/torch", "item/foo", "stairs") to its resource path. */
    private fun modelResourcePath(modelId: String): String {
        val path = stripNamespace(modelId)
        return if (path.contains('/')) "/assets/minecraft/models/$path.json"
        else "/assets/minecraft/models/block/$path.json"
    }

    /**
     * Resolves a single representative texture from a model JSON by merging the `textures` maps of
     * the whole parent chain (child overrides parent), then picking the most icon-worthy key and
     * dereferencing any `#placeholder` indirection. Returns a `block/`-relative texture name.
     */
    private fun resolveModelTexture(modelPath: String): String? {
        val textures = HashMap<String, String>()
        var path: String? = modelPath
        var depth = 0
        // Walk parent -> child applying child last so child entries override; but since we descend
        // from the requested model upward, collect parents first then re-apply the start model.
        val chain = ArrayList<JsonObject>()
        while (path != null && depth < MAX_PARENT_DEPTH) {
            val model = readJson(path) ?: break
            chain.add(model)
            val parent = model.getAsJsonPrimitive("parent")?.asString
            path = parent?.takeUnless { stripNamespace(it).startsWith("builtin/") }?.let { modelResourcePath(it) }
            depth++
        }
        if (chain.isEmpty()) return null
        // Apply from the furthest parent down to the requested model so closer definitions win.
        for (model in chain.asReversed()) {
            val tex = model.getAsJsonObject("textures") ?: continue
            for ((key, value) in tex.entrySet()) {
                value.asStringOrNull()?.let { textures[key] = it }
            }
        }
        if (textures.isEmpty()) return null

        // Prefer a single, front-facing flat texture, then a representative cube face.
        val preferred = listOf("layer0", "particle", "all", "texture", "front", "top", "side", "end")
        val raw = preferred.firstNotNullOfOrNull { textures[it] }
            ?: textures.values.firstOrNull()
            ?: return null
        return dereference(raw, textures)?.let { stripNamespace(it).removePrefix("block/") }
    }

    /** Resolves a `#placeholder` texture value against the model's texture map (bounded). */
    private fun dereference(value: String, textures: Map<String, String>): String? {
        var current = value
        var depth = 0
        while (current.startsWith("#") && depth < MAX_PARENT_DEPTH) {
            current = textures[current.substring(1)] ?: return null
            depth++
        }
        return current.takeUnless { it.startsWith("#") }
    }

    /** Returns the first `"model": "..."` string found anywhere in a blockstate JSON, or null. */
    private fun firstModelReference(element: JsonElement): String? {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.getAsJsonPrimitive("model")?.asString?.let { return it }
                for ((_, value) in obj.entrySet()) firstModelReference(value)?.let { return it }
            }
            element.isJsonArray -> for (value in element.asJsonArray) firstModelReference(value)?.let { return it }
        }
        return null
    }

    private fun readJson(resourcePath: String): JsonObject? = try {
        this::class.java.getResourceAsStream(resourcePath)?.use { stream ->
            JsonParser.parseReader(stream.bufferedReader()).asJsonObject
        }
    } catch (_: Throwable) {
        null
    }

    private fun stripNamespace(id: String): String = id.substringAfter(':', id)

    private fun JsonElement.asStringOrNull(): String? =
        if (isJsonPrimitive && asJsonPrimitive.isString) asString else null
}
