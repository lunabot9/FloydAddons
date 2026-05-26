package floydaddons.not.dogshit.client.features.impl.misc

import floydaddons.not.dogshit.client.FloydAddonsMod
import floydaddons.not.dogshit.client.clickgui.settings.impl.BooleanSetting
import floydaddons.not.dogshit.client.events.TickEvent
import floydaddons.not.dogshit.client.events.core.on
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module
import floydaddons.not.dogshit.client.features.impl.misc.FloydUpdateChecker
import java.nio.file.Path

object FloydCompatibility : Module(
    name = "Floyd Compatibility",
    category = Category.MISC,
    description = "Floyd's low-level compatibility hooks.",
    toggled = true,
) {
    private val spoofClientBrand by BooleanSetting("Spoof Client Brand", true, desc = "Reports the vanilla client brand.")
    private val hideWatchdogMessages by BooleanSetting("Hide Watchdog Messages", true, desc = "Suppresses Hypixel Watchdog announcement spam.")
    private val customMainMenu by BooleanSetting("Custom Main Menu", true, desc = "Uses config/floydaddons/mainmenu.png as the title background.")
    private val taskbarIcon by BooleanSetting("Taskbar Icon", true, desc = "Applies Floyd's window/taskbar icon.")
    private val updateChecker by BooleanSetting("Update Checker", true, desc = "Checks FloydAddons releases for this Minecraft version.")
    private val hideLoaderEntry by BooleanSetting("Hide Loader Entry", true, desc = "Hides floydaddons from basic Fabric loader lookups.")

    init {
        FloydUpdateChecker.init()
        on<TickEvent.ClientEnd> {
            FloydTaskbarIcon.applyOnce()
            FloydUpdateChecker.tick()
        }
    }

    @JvmStatic fun shouldSpoofClientBrand(): Boolean = enabled && spoofClientBrand
    @JvmStatic fun shouldHideWatchdogMessages(): Boolean = enabled && hideWatchdogMessages
    @JvmStatic fun shouldUseCustomMainMenu(): Boolean = enabled && customMainMenu
    @JvmStatic fun shouldApplyTaskbarIcon(): Boolean = enabled && taskbarIcon
    @JvmStatic fun shouldCheckUpdates(): Boolean = enabled && updateChecker
    @JvmStatic fun shouldHideLoaderEntry(): Boolean = enabled && hideLoaderEntry
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "spoofClientBrand" to spoofClientBrand,
        "hideWatchdogMessages" to hideWatchdogMessages,
        "customMainMenu" to customMainMenu,
        "taskbarIcon" to taskbarIcon,
        "updateChecker" to updateChecker,
        "hideLoaderEntry" to hideLoaderEntry,
        "shouldSpoofClientBrand" to shouldSpoofClientBrand(),
        "shouldHideWatchdogMessages" to shouldHideWatchdogMessages(),
        "shouldUseCustomMainMenu" to shouldUseCustomMainMenu(),
        "shouldApplyTaskbarIcon" to shouldApplyTaskbarIcon(),
        "shouldCheckUpdates" to shouldCheckUpdates(),
        "shouldHideLoaderEntry" to shouldHideLoaderEntry(),
        "updateCheckerState" to FloydUpdateChecker.state()
    )
    @JvmStatic fun configPath(fileName: String): Path = FloydAddonsMod.configFile.toPath().resolve(fileName)
}
