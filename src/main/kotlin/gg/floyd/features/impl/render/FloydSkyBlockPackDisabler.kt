package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.modMessage

/**
 * Prevents Hypixel's official SkyBlock server pack from replacing the player's selected visuals.
 *
 * The network mixin acknowledges matching pack pushes as loaded, then cancels Minecraft's normal
 * download path. A lowest-priority copy of Hypixel's model definitions stays mounted so the
 * server's `hypixel_skyblock:*` item references remain resolvable, while item models are mapped
 * back to their vanilla equivalents. Other server resource packs are left untouched.
 */
object FloydSkyBlockPackDisabler : Module(
    name = "SkyBlock Pack Disabler",
    category = Category.RENDER,
    description = "Disables Hypixel's forced SkyBlock texture pack while keeping other server packs enabled.",
    toggled = false,
) {
    private val reloadTexturesAction by ActionSetting(
        "Clear & Reload Textures",
        desc = "Clears the active downloaded server pack and reloads textures.",
    ) {
        clearAndReloadTextures()
    }

    @JvmStatic
    fun shouldDisable(url: String): Boolean =
        enabled && FloydSkyBlockPackPolicy.isOfficialSkyBlockPack(url)

    @JvmStatic
    fun clearAndReloadTextures() {
        val minecraft = FloydAddonsMod.mc
        minecraft.execute {
            minecraft.clearDownloadedResourcePacks()
            minecraft.reloadResourcePacks().whenComplete { _, error ->
                minecraft.execute {
                    if (error == null) {
                        modMessage("Finished a full resource reload (textures, models, fonts, and sounds).")
                    } else {
                        FloydAddonsMod.logger.error("Full resource reload failed", error)
                        modMessage("Resource reload failed; check latest.log for details.")
                    }
                }
            }
        }
    }
}

internal object FloydSkyBlockPackPolicy {
    fun isOfficialSkyBlockPack(url: String): Boolean =
        url.contains("hypixel.net", ignoreCase = true) &&
            url.contains("SkyBlock", ignoreCase = true)
}
