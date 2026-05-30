package floydaddons.not.dogshit.client.features.impl.misc

import floydaddons.not.dogshit.client.FloydAddonsMod
import floydaddons.not.dogshit.client.clickgui.settings.impl.ActionSetting
import floydaddons.not.dogshit.client.events.TickEvent
import floydaddons.not.dogshit.client.events.core.on
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module
import floydaddons.not.dogshit.client.utils.modMessage
import floydaddons.not.dogshit.client.utils.openDirectory
import java.nio.file.Path

object FloydSpoofClientBrand : Module(
    name = "Spoof Client Brand",
    category = Category.MISC,
    description = "Reports the vanilla client brand."
) {
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled)
}

object FloydCustomMainMenu : Module(
    name = "Custom Main Menu",
    category = Category.MISC,
    description = "Uses config/floydaddons/mainmenu.png as the title background."
) {
    private val openFile by ActionSetting("Open File", desc = "Opens the folder containing mainmenu.png.") {
        modMessage(if (openDirectory(mainMenuPath().parent)) "Opened main menu folder." else "Could not open main menu folder: ${mainMenuPath().parent}")
    }

    @JvmStatic
    fun mainMenuPath(): Path = FloydAddonsMod.configFile.toPath().resolve("mainmenu.png")

    @JvmStatic
    fun shouldUseCustomMainMenu(): Boolean = enabled

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "shouldUseCustomMainMenu" to shouldUseCustomMainMenu(),
        "mainMenuPath" to mainMenuPath().toString()
    )
}

object FloydTaskbarIconModule : Module(
    name = "Taskbar Icon",
    category = Category.MISC,
    description = "Applies Floyd's window/taskbar icon.",
    toggled = true
) {
    override val visibleInGui: Boolean = false

    init {
        on<TickEvent.ClientEnd> {
            FloydTaskbarIcon.applyOnce()
        }
    }

    fun state(): Map<String, Any?> = mapOf("enabled" to enabled)
}

object FloydUpdateCheckerModule : Module(
    name = "Update Checker",
    category = Category.MISC,
    description = "Checks FloydAddons releases for this Minecraft version."
) {
    init {
        FloydUpdateChecker.init()
    }

    @JvmStatic
    fun shouldCheckUpdates(): Boolean = enabled

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "shouldCheckUpdates" to shouldCheckUpdates(),
        "updateCheckerState" to FloydUpdateChecker.state()
    )
}
