package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.features.impl.hiders.FloydHideWatchdogMessages
import gg.floyd.features.impl.hiders.FloydModHider
import java.nio.file.Path

/**
 * Facade over the per-feature compatibility modules.
 *
 * This used to be a single mega-[Module][gg.floyd.features.Module] that owned every compat toggle.
 * Each feature is now its own top-level module (see [FloydCompatModules.kt], plus
 * [FloydHideWatchdogMessages]/[FloydModHider] under the Hiders category); this object exposes facade
 * methods that read the new modules so the mixins do not have to change.
 */
object FloydCompatibility {

    @JvmStatic fun shouldSpoofClientBrand(): Boolean = FloydSpoofClientBrand.enabled
    @JvmStatic fun shouldHideWatchdogMessages(): Boolean = FloydHideWatchdogMessages.enabled
    @JvmStatic fun shouldUseCustomMainMenu(): Boolean = FloydCustomMainMenu.enabled
    @JvmStatic fun shouldApplyTaskbarIcon(): Boolean = FloydTaskbarIconModule.enabled
    @JvmStatic fun shouldCheckUpdates(): Boolean = FloydUpdateCheckerModule.enabled
    @JvmStatic fun shouldHideLoaderEntry(): Boolean = FloydModHider.enabled
    @JvmStatic fun shouldHideModChannels(): Boolean = FloydModHider.enabled

    fun state(): Map<String, Any?> = mapOf(
        "spoofClientBrand" to FloydSpoofClientBrand.enabled,
        "hideWatchdogMessages" to FloydHideWatchdogMessages.enabled,
        "customMainMenu" to FloydCustomMainMenu.enabled,
        "taskbarIcon" to FloydTaskbarIconModule.enabled,
        "updateChecker" to FloydUpdateCheckerModule.enabled,
        "hideLoaderEntry" to FloydModHider.enabled,
        "hideModChannels" to FloydModHider.enabled,
        "shouldSpoofClientBrand" to shouldSpoofClientBrand(),
        "shouldHideWatchdogMessages" to shouldHideWatchdogMessages(),
        "shouldUseCustomMainMenu" to shouldUseCustomMainMenu(),
        "shouldApplyTaskbarIcon" to shouldApplyTaskbarIcon(),
        "shouldCheckUpdates" to shouldCheckUpdates(),
        "shouldHideLoaderEntry" to shouldHideLoaderEntry(),
        "shouldHideModChannels" to shouldHideModChannels(),
        "updateCheckerState" to FloydUpdateChecker.state()
    )

    @JvmStatic fun configPath(fileName: String): Path = FloydAddonsMod.configFile.toPath().resolve(fileName)
}
