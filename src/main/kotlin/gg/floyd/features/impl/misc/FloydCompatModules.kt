package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.utils.modMessage
import gg.floyd.utils.openDirectory
import java.nio.file.Path

/**
 * Per-feature compatibility modules.
 *
 * These used to be single [BooleanSetting][gg.floyd.clickgui.settings.impl.BooleanSetting]s on the old
 * `FloydCompatibility` mega-module. Each is now a top-level [Module] so the click GUI lists them
 * directly under the Misc category. The plain [FloydCompatibility] object reads these via facades so
 * the mixins do not have to change.
 */
object FloydSpoofClientBrand : Module(
    name = "Spoof Client Brand",
    category = Category.MISC,
    description = "Reports the vanilla client brand.",
    toggled = true,
)

object FloydCustomMainMenu : Module(
    name = "Custom Main Menu",
    category = Category.MISC,
    description = "Uses config/floydaddons/mainmenu.png as the title background.",
    toggled = true,
) {
    private val openFile by ActionSetting("Open File", desc = "Opens the folder holding mainmenu.png.") {
        modMessage(
            if (openDirectory(FloydAddonsMod.configFile.toPath())) "Opened FloydAddons config folder."
            else "Could not open config folder."
        )
    }

    @JvmStatic
    fun mainMenuPath(): Path = FloydAddonsMod.configFile.toPath().resolve("mainmenu.png")
}

object FloydTaskbarIconModule : Module(
    name = "Taskbar Icon",
    category = Category.MISC,
    description = "Applies Floyd's window/taskbar icon.",
    toggled = true,
) {
    override val visibleInGui: Boolean = false

    init {
        on<TickEvent.ClientEnd> {
            FloydTaskbarIcon.applyOnce()
        }
    }
}

object FloydUpdateCheckerModule : Module(
    name = "Update Checker",
    category = Category.MISC,
    description = "Checks FloydAddons releases for this Minecraft version.",
    toggled = true,
) {
    init {
        FloydUpdateChecker.init()
        on<TickEvent.ClientEnd> {
            FloydUpdateChecker.tick()
        }
    }
}
