package gg.floyd.clickgui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        // OneConfig imports Mod Menu screens automatically. Floyd owns its screen and storage.
        val excluded = org.polyfrost.oneconfig.internal.compat.CompatLoader.nativeLoadedConfigs
        if ("floydaddons" !in excluded) excluded.add("floydaddons")
        return ConfigScreenFactory { FloydOneConfig.createScreen() }
    }
}
