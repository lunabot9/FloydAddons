package gg.floyd.clickgui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import gg.floyd.features.impl.render.FloydUiTheme
import org.polyfrost.oneconfig.internal.ui.themes.PolyGlassDark
import org.polyfrost.oneconfig.internal.ui.themes.UIBranding

/** A local Compose theme. No registry, ThemeConfig, or native OneConfig preferences are changed. */
object FloydOneConfigTheme {
    private const val NAME = "Floyd Main Menu"
    private const val WORDMARK = "assets/floydaddons/brand/floyd-addons.svg"
    private var palette by mutableStateOf(FloydUiPalette.default)
    private var editorState by mutableStateOf(FloydUiPalette.DEFAULT_PRESET to false)
    val editorSelection get() = editorState
    private var cachedPalette: FloydUiPalette? = null
    private var cachedTheme = PolyGlassDark

    /** Called by our own screen each frame; also catches in-place color/chroma edits. */
    fun sync() {
        val current = FloydUiTheme.palette()
        if (palette != current) palette = current
        editorState = FloydUiTheme.presetName() to FloydUiTheme.customColors
    }

    val theme get(): org.polyfrost.oneconfig.internal.ui.themes.UITheme {
        val current = palette
        if (cachedPalette != current) {
            cachedTheme = PolyGlassDark.copy(
                previewImage = "floyd-main-menu",
                name = NAME,
                pageBackground = Color(current["Page Background"]),
                sidebarBackground = Color(current["Sidebar Background"]),
                chipBackground = Color(current["Hover Background"]),
                modCardBackground = Color(current["Card Background"]),
                componentBackground = Color(current["Control Background"]),
                popupBackground = Color(current["Popup Background"]),
                borderColor = Color(current["Border"]),
                backgroundShape = RoundedCornerShape(18.dp),
                textColor = Color(current["Primary Text"]),
                textColorSecondary = Color(current["Secondary Text"]),
                accentTextColor = Color(current["Accent Text"]),
                controlThumbColor = Color(current["Control Thumb"]),
                favoriteColor = Color(current["Accent"]),
                shadowColor = Color(current["Shadow"]),
                branding = UIBranding(WORDMARK),
            ).withControlTrackColor(Color(current["Control Track"]))
            cachedPalette = current
        }
        return cachedTheme
    }

    val accent get() = Color(palette["Accent"])
    val divider get() = Color(palette["Divider"])
    val wordmark get() = Color(palette["Wordmark"])
    @JvmStatic fun packedAccent(): Long = accent.value.toLong()

    fun state(): Map<String, Any?> = mapOf(
        "name" to NAME,
        "isolated" to true,
        "borderStyle" to "single-color-frame-solid-divider",
        "borderColor" to "#%06X".format(palette["Border"] and 0xFFFFFF),
        "borderWidthDp" to 1,
        "cornerRadiusDp" to 18,
        "wordmark" to WORDMARK,
        "label" to "Floyd Addons",
        "accent" to "#%06X".format(palette["Accent"] and 0xFFFFFF),
        "menuAccent" to "#FFB84D",
        "customization" to FloydUiTheme.state(),
    )
}
