package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.errorMessage
import gg.floyd.utils.infoMessage
import gg.floyd.utils.modMessage
import net.minecraft.resources.Identifier

/**
 * Prevents Hypixel's official SkyBlock server pack from replacing the player's selected visuals.
 *
 * The network mixin acknowledges matching Hypixel SkyBlock pack pushes as loaded, then cancels
 * Minecraft's normal download path. A local repository source mirrors the upstream SkyBlock pack
 * approach: it keeps a cached full pack (or bundled fallback) mounted client-side while Floyd's
 * item, tooltip, and head mixins selectively revert the official SkyBlock overrides.
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

    private val reloadMetadataAction by ActionSetting(
        "Reload Hypixel Pack Cache",
        desc = "Re-downloads the last seen Hypixel SkyBlock pack and reloads resources.",
    ) {
        reloadHypixelItemMetadata()
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

    @JvmStatic
    fun reloadHypixelItemMetadata() {
        if (!enabled) {
            errorMessage("Enable SkyBlock Pack Disabler before reloading Hypixel item metadata.")
            return
        }
        if (!FloydSkyBlockPackAssets.reloadLastSeenLivePack()) {
            errorMessage("No Hypixel SkyBlock pack has been seen yet in this session.")
            return
        }
        infoMessage("Reloading the cached Hypixel SkyBlock pack from the last seen server packet...")
    }
}

internal object FloydSkyBlockPackPolicy {
    fun isOfficialSkyBlockPack(url: String): Boolean =
        url.contains("hypixel.net", ignoreCase = true) &&
            url.contains("SkyBlock", ignoreCase = true)
}

internal object FloydSkyBlockItemModelPolicy {
    fun shouldReplaceCurrentModel(
        currentModel: Identifier?,
        packDisablerEnabled: Boolean,
        stackEmpty: Boolean,
    ): Boolean =
        packDisablerEnabled && !stackEmpty && currentModel?.namespace == "hypixel_skyblock"

    fun resolveBaseModel(
        currentModel: Identifier,
        skyBlockId: String?,
        liveModels: Map<Identifier, Identifier>,
        knownModels: Map<String, Identifier>,
        vanillaItemModel: Identifier,
        quiverArrowModel: Identifier? = null,
    ): Identifier = when {
        skyBlockId != null && knownModels.containsKey(skyBlockId) -> knownModels.getValue(skyBlockId)
        liveModels.containsKey(currentModel) -> liveModels.getValue(currentModel)
        quiverArrowModel != null -> quiverArrowModel
        else -> vanillaItemModel
    }
}
