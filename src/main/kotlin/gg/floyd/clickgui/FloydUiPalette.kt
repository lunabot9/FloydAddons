package gg.floyd.clickgui

/** Plain immutable values keep preset resolution independent of either UI framework. */
data class FloydUiPalette(val colors: Map<String, Int>) {
    operator fun get(name: String): Int = colors.getValue(name)

    companion object {
        const val DEFAULT_PRESET = "Midnight Silver"

        val roles = linkedMapOf(
            "Accent" to "Buttons, enabled controls, and selected categories.",
            "Page Background" to "Main settings page background.",
            "Sidebar Background" to "Category navigation background.",
            "Hover Background" to "Hovered navigation entries and search fields.",
            "Card Background" to "Settings cards and grouped rows.",
            "Control Background" to "Input controls and compact options.",
            "Popup Background" to "Dropdown menus and color picker surfaces.",
            "Border" to "Outer frame and control outlines.",
            "Primary Text" to "Titles, settings names, and normal text.",
            "Secondary Text" to "Descriptions and quieter labels.",
            "Accent Text" to "Text on selected categories and accent buttons.",
            "Control Thumb" to "Slider handles and switch thumbs.",
            "Control Track" to "Slider rails and inactive switches.",
            "Divider" to "The full-height divider between navigation and settings.",
            "Wordmark" to "The FLOYD title at the top of the sidebar.",
            "Shadow" to "Shadows used by popup menus and controls.",
        )

        private fun palette(vararg values: Long): FloydUiPalette {
            require(values.size == roles.size)
            return FloydUiPalette(roles.keys.zip(values.map { it.toInt() }).toMap())
        }

        // Golden Amber is byte-for-byte the original warm Floyd menu palette.
        val presets: Map<String, FloydUiPalette> = linkedMapOf(
            "Midnight Silver" to palette(0xFFCBD5E1, 0xFF181C23, 0xFF12161C, 0xFF303844, 0xFF242B35,
                0xFF2B3340, 0xFF303947, 0xFF525F70, 0xFFF1F5F9, 0xFFAAB6C5, 0xFF182230, 0xFFF8FAFC, 0xFF64748B, 0xFF525F70, 0xFFCBD5E1, 0xFF000000),
            "Golden Amber" to palette(0xFFFFB84D, 0xFF201911, 0xFF1A1510, 0xFF362A1C, 0xFF2A2115,
                0xFF2D251A, 0xFF33281B, 0xFF62503A, 0xFFF4ECDD, 0xFFB9AA95, 0xFF1B1408, 0xFFFFF3DA, 0xFF74624C, 0xFF62503A, 0xFFFFB84D, 0xFF000000),
            "Nebula Violet" to palette(0xFFB9A0FF, 0xFF191322, 0xFF14101C, 0xFF302340, 0xFF241B31,
                0xFF2B2039, 0xFF30223F, 0xFF584369, 0xFFF2EBFF, 0xFFB8A8CA, 0xFF1F1232, 0xFFF3EBFF, 0xFF67537B, 0xFF584369, 0xFFB9A0FF, 0xFF000000),
            "Glacier Blue" to palette(0xFF73CAFF, 0xFF111C26, 0xFF0D161F, 0xFF203849, 0xFF192B39,
                0xFF1D3242, 0xFF20394A, 0xFF3D6075, 0xFFE6F4FF, 0xFF9FB8CA, 0xFF082436, 0xFFE8F8FF, 0xFF4D6E83, 0xFF3D6075, 0xFF73CAFF, 0xFF000000),
            "Emerald Grove" to palette(0xFF71D6AA, 0xFF131E1A, 0xFF101914, 0xFF233B2F, 0xFF1B2D24,
                0xFF21352B, 0xFF263D31, 0xFF426751, 0xFFECF8EF, 0xFFA4BFAA, 0xFF0C2A1C, 0xFFE9FFF1, 0xFF55775F, 0xFF426751, 0xFF71D6AA, 0xFF000000),
            "Rose Quartz" to palette(0xFFF2A3BD, 0xFF22161C, 0xFF1B1217, 0xFF412A34, 0xFF312129,
                0xFF392630, 0xFF412B35, 0xFF734B5B, 0xFFFFEDF3, 0xFFC6A6B2, 0xFF35121F, 0xFFFFEFF4, 0xFF846171, 0xFF734B5B, 0xFFF2A3BD, 0xFF000000),
        )

        val default: FloydUiPalette get() = presets.getValue(DEFAULT_PRESET)
    }
}
