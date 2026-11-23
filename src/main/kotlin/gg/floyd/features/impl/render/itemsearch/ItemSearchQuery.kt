package gg.floyd.features.impl.render.itemsearch

import java.util.Locale

/** Independent of Minecraft so search semantics can be regression-tested. */
internal class ItemSearchQuery(query: String) {
    private val groups = query.lowercase(Locale.ROOT).split('|').filter { it.isNotBlank() }.ifEmpty { listOf("") }.map { group ->
        Regex("!?[#@]?(?:\"[^\"]*\"|\\S+)").findAll(group).map { it.value }.toList()
    }

    fun matches(name: String, id: String, lore: String): Boolean = groups.any { terms ->
        terms.all { raw ->
            val excluded = raw.startsWith('!')
            val term = raw.removePrefix("!")
            val field = when (term.firstOrNull()) { '#' -> lore; '@' -> id; else -> "$name $id" }
            val needle = term.removePrefix("#").removePrefix("@").trim('"')
            val found = field.contains(needle)
            if (excluded) !found else found
        }
    }
}
