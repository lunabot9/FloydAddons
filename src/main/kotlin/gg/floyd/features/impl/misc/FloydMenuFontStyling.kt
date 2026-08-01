package gg.floyd.features.impl.misc

import net.minecraft.client.gui.screens.Screen

object FloydMenuFontStyling {

    @JvmStatic
    fun shouldUseVanillaFont(screen: Screen): Boolean =
        FloydMenuScreenStyling.shouldSuppressWidgetFont(screen)
}
