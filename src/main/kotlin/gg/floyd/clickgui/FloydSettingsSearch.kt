package gg.floyd.clickgui

internal object FloydSettingsSearch {
    fun matches(query: String, vararg fields: String?): Boolean {
        fun compact(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
        val needle = compact(query)
        if (needle.isEmpty()) return true
        val values = fields.filterNotNull().map(::compact)
        if (values.any { needle in it }) return true
        val words = query.trim().split(Regex("\\s+")).map(::compact).filter(String::isNotEmpty)
        // A trailing letter in a partially typed name must stay attached to its
        // prefix; "free c" should not match "Freelook" merely because it is in Camera.
        if (words.any { it.length == 1 }) return false
        return words.all { word -> values.any { word in it } }
    }
}
