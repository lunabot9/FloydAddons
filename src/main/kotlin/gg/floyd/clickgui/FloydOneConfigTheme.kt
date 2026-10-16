package gg.floyd.clickgui

import androidx.compose.ui.graphics.Color
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.internal.ThemeConfig
import org.polyfrost.oneconfig.internal.ui.themes.PolyGlassDark
import org.polyfrost.oneconfig.internal.ui.themes.ThemeRegistry
import org.polyfrost.oneconfig.internal.ui.themes.UIBranding

/** Uses OneConfig's native theme/branding extension points for Floyd's menu palette. */
object FloydOneConfigTheme {
    private const val NAME = "Floyd Main Menu"
    private const val WORDMARK = "assets/floydaddons/brand/floyd-addons.svg"
    private var registered = false

    // Match SITE_INK, SITE_PANEL, and SITE_AMBER in menu_landscape.fsh. The shader
    // deliberately keeps these warm even when an older config still has purple sky values.
    private val sky = 0xFF0A0703.toInt()
    private val horizon = 0xFF1B1408.toInt()
    private val gold = 0xFFFFB84D.toInt()
    private val theme by lazy {
        PolyGlassDark.copy(
            previewImage = "floyd-main-menu",
            name = NAME,
            pageBackground = Color(blend(horizon, 0xFF251E19.toInt(), .55f)),
            sidebarBackground = Color(blend(sky, 0xFF241D18.toInt(), .65f)),
            chipBackground = Color(blend(horizon, 0xFF584735.toInt(), .45f)),
            modCardBackground = Color(blend(horizon, 0xFF42362A.toInt(), .4f)),
            componentBackground = Color(blend(horizon, 0xFF4A4036.toInt(), .4f)),
            popupBackground = Color(blend(horizon, 0xFF48392C.toInt(), .55f)),
            borderColor = Color((gold and 0xFFFFFF) or 0x38000000),
            textColor = Color(0xFFF4ECDD),
            textColorSecondary = Color(0xFFB9AA95),
            accentTextColor = Color(0xFF1B1408),
            controlThumbColor = Color(0xFFFFF3DA),
            favoriteColor = Color(gold),
            branding = UIBranding(WORDMARK),
        ).withControlTrackColor(Color(0xFF74624C))
    }

    fun apply() {
        if (!registered) {
            ThemeRegistry.register(theme)
            registered = true
        }
        ThemeConfig.accentColor = PolyColor(gold)
        if (ThemeConfig.activeTheme != NAME) ThemeRegistry.activate(theme)
    }

    fun state(): Map<String, Any?> = mapOf(
        "name" to ThemeConfig.activeTheme,
        "registered" to registered,
        "wordmark" to WORDMARK,
        "label" to "Floyd Addons",
        "accent" to "#%06X".format(ThemeConfig.accentColor.rawArgb and 0xFFFFFF),
        "menuAccent" to "#%06X".format(gold and 0xFFFFFF),
    )

    private fun blend(a: Int, b: Int, amount: Float): Int {
        fun channel(shift: Int): Int = (((a ushr shift) and 255) * (1f - amount) + ((b ushr shift) and 255) * amount).toInt()
        return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
