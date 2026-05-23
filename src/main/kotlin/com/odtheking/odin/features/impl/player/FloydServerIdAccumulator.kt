package com.odtheking.odin.features.impl.player

import java.util.Locale
import java.util.regex.Pattern

internal class FloydServerIdAccumulator {
    private val seenIds = linkedSetOf<String>()
    var currentId: String = ""
        private set

    fun clear() {
        seenIds.clear()
        currentId = ""
    }

    fun cachedIds(): List<String> = seenIds.toList()

    fun scanText(text: String): Boolean {
        val serverLine = SERVER_LINE_PATTERN.matcher(text)
        if (serverLine.find()) {
            addFullId(serverLine.group(1), serverLine.group(2))
            return true
        }

        val full = FULL_SERVER_ID_PATTERN.matcher(text)
        if (full.find()) {
            addFullId(full.group(1), full.group(2))
            return true
        }

        val afterDate = serverIdAfterDate(text)
        if (afterDate != null) {
            addRawId(afterDate)
            return true
        }

        return false
    }

    fun scanScoreboardText(text: String): Boolean {
        if (scanText(text)) return true

        for (prefix in PREFIXES) {
            val abbreviation = Pattern.compile(
                "(?i)(?:^|\\s)${Pattern.quote(prefix.first().toString())}(\\d{1,4}[a-z]{1,4})(?:\\s|$)"
            )
            val match = abbreviation.matcher(text)
            if (match.find()) {
                addAbbreviatedId(prefix, match.group(1))
                return true
            }
        }

        return false
    }

    fun replaceDateServerId(text: String, replacement: String): String {
        val range = serverIdAfterDateRange(text) ?: return text
        return text.substring(0, range.first) + replacement + text.substring(range.last + 1)
    }

    private fun addFullId(prefix: String, suffix: String) {
        val normalizedPrefix = prefix.lowercase(Locale.ROOT)
        val normalizedSuffix = suffix.lowercase(Locale.ROOT)
        val fullId = normalizedPrefix + normalizedSuffix
        currentId = fullId
        seenIds.add(fullId)

        val abbreviated = normalizedPrefix.first() + normalizedSuffix
        if (!abbreviated.equals(fullId, ignoreCase = true)) seenIds.add(abbreviated)
    }

    private fun addAbbreviatedId(matchedPrefix: String, suffix: String) {
        val normalizedSuffix = suffix.lowercase(Locale.ROOT)
        val abbreviation = matchedPrefix.first().lowercaseChar() + normalizedSuffix
        currentId = abbreviation
        seenIds.add(abbreviation)

        val prefixInitial = matchedPrefix.first().lowercaseChar()
        for (prefix in PREFIXES) {
            if (prefix.first() != prefixInitial) continue
            val expanded = prefix + normalizedSuffix
            if (!expanded.equals(abbreviation, ignoreCase = true)) seenIds.add(expanded)
        }
    }

    private fun addRawId(id: String) {
        val normalized = id.lowercase(Locale.ROOT)
        currentId = normalized
        seenIds.add(normalized)
    }

    private fun serverIdAfterDate(text: String): String? {
        val range = serverIdAfterDateRange(text) ?: return null
        return text.substring(range)
    }

    private fun serverIdAfterDateRange(text: String): IntRange? {
        val match = DATE_PATTERN.matcher(text)
        if (!match.find()) return null
        var start = match.end()
        while (start < text.length && text[start].isWhitespace()) start++
        if (start >= text.length) return null
        var end = start
        while (end < text.length && !text[end].isWhitespace()) end++
        val candidate = text.substring(start, end)
        return if (candidate.any(Char::isDigit)) start until end else null
    }

    companion object {
        private val PREFIXES = listOf("mini", "mega", "lobby", "limbo", "housing", "prototype", "node", "legacylobby")
        val FULL_SERVER_ID_PATTERN: Pattern = Pattern.compile("(?i)(${PREFIXES.joinToString("|")})(\\d{1,4}[a-z]{0,4})")
        val SERVER_LINE_PATTERN: Pattern = Pattern.compile("(?i)Server:\\s*(${PREFIXES.joinToString("|")})(\\d{1,4}[a-z]{0,4})")
        private val DATE_PATTERN: Pattern = Pattern.compile("\\d{2}/\\d{2}/\\d{2}")

        fun replaceIgnoreCase(input: String, find: String, replace: String): String {
            if (find.isEmpty()) return input
            val lowerInput = input.lowercase(Locale.ROOT)
            val lowerFind = find.lowercase(Locale.ROOT)
            if (!lowerInput.contains(lowerFind)) return input

            val out = StringBuilder(input.length)
            var index = 0
            while (index < input.length) {
                val hit = lowerInput.indexOf(lowerFind, index)
                if (hit < 0) {
                    out.append(input, index, input.length)
                    break
                }
                out.append(input, index, hit)
                out.append(replace)
                index = hit + find.length
            }
            return out.toString()
        }
    }
}
