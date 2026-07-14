package gg.floyd.features.impl.misc

import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object FloydMenuScreenStyling {

    /**
     * Whether Floyd should paint its media background behind [screen] instead of the vanilla
     * panorama / blurred world. Targets every "default background" screen — the whole title flow
     * out of game and the pause/options/settings flow in game.
     *
     * Uses only `is` type checks (NOT class-name strings): the shipped jar runs on intermediary
     * mappings where e.g. OptionsScreen is `net.minecraft.class_429`, so a package-name string match
     * silently fails at runtime — the bug that left the in-game options screen unstyled.
     *
     * Excluded: container/inventory and chat screens (they intentionally show the live world), and
     * Floyd's own screens (they draw their own background). The hook fires from
     * `Screen.renderBackground`, so screens that fully override it are unaffected regardless.
     */
    @JvmStatic
    fun shouldReplaceBackground(screen: Screen): Boolean {
        if (!FloydCompatibility.shouldUseCustomMainMenu() || !FloydMenuVideoBackground.hasMedia()) return false
        if (screen is FloydMainMenuScreen) return false
        if (screen is AbstractContainerScreen<*>) return false
        if (screen is ChatScreen) return false
        if (isFloydScreen(screen)) return false
        return true
    }

    /** @return true if the media background was drawn (so the caller cancels the vanilla one). */
    @JvmStatic
    fun renderBackground(context: GuiGraphics): Boolean = FloydMenuVideoBackground.render(context)

    @JvmStatic
    fun renderOverlay(screen: Screen, context: GuiGraphics, partialTick: Float) = Unit

    private fun isFloydScreen(screen: Screen): Boolean =
        screen.javaClass.name.startsWith("gg.floyd.")

    internal fun drawWord(text: String, x: Float, y: Float, size: Float, alpha: Float) {
        NVGRenderer.text(text, x, y, size, colorGray(210, alpha), NVGRenderer.defaultFont)
    }

    private fun colorGray(gray: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        val g = gray.coerceIn(0, 255)
        return (a shl 24) or (g shl 16) or (g shl 8) or g
    }
}
