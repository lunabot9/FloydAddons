package gg.floyd.utils

import java.util.Locale

/**
 * Friendly-name + searchable-token helpers for registry ids (blocks, items, mobs).
 *
 * Pure string utilities with no Minecraft dependency so they can be unit-tested and
 * precomputed at module init. Callers should build a token map once (see [searchTokens])
 * rather than recomputing friendly names in the render loop.
 */
object Identifiers {

    /**
     * "minecraft:diamond_ore" -> "Diamond Ore". The namespace is dropped, underscores
     * become spaces, and each word is title-cased. Ids without a namespace are accepted
     * as-is; a trailing/leading namespace separator is tolerated.
     */
    fun friendlyName(id: String): String {
        val path = id.substringAfter(':', id)
        if (path.isEmpty()) return id
        return path.split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
    }

    /**
     * Lowercased blob containing both the raw id and its friendly name, so a single
     * substring test matches either the registry id ("minecraft:cobblestone") or the
     * human name ("Cobblestone"). Returned strings are already lowercased; compare with
     * an also-lowercased query for case-insensitive matching.
     */
    fun searchBlob(id: String): String =
        (id + " " + friendlyName(id)).lowercase(Locale.ROOT)

    /**
     * Precompute the id -> search-blob map once for a set of ids. Build this at init and
     * reuse it across frames; do NOT call inside the render loop.
     */
    fun searchTokens(ids: Iterable<String>): Map<String, String> =
        ids.associateWith { searchBlob(it) }

    /**
     * Whether [id] matches [query] given a precomputed [tokens] map. Falls back to
     * computing the blob on the fly if the id is missing from the map. A blank query
     * matches everything.
     */
    fun matches(id: String, query: String, tokens: Map<String, String>): Boolean {
        if (query.isBlank()) return true
        val blob = tokens[id] ?: searchBlob(id)
        return blob.contains(query.lowercase(Locale.ROOT))
    }
}
