package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.events.ScreenEvent
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.render.FloydRender
import gg.floyd.utils.modMessage
import gg.floyd.utils.openDirectory
import net.minecraft.client.gui.screens.TitleScreen
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
    description = "Replaces the vanilla title flow with Floyd's NVG-driven menu and custom media background.",
    toggled = true,
) {
    var mediaPath by StringSetting(
        "Media Path",
        "C:\\Users\\gobsi\\AppData\\Roaming\\ModrinthApp\\profiles\\67\\resourcepacks\\Azusa Swimsuit 1.20.0-1.zip",
        260,
        desc = "Absolute path to the frame-sequence zip, MP4, or image used behind Floyd's custom menus."
    )

    private val openFile by ActionSetting("Open File", desc = "Opens the folder holding the configured main-menu media.") {
        modMessage(
            if (openDirectory(mediaDirectory())) "Opened main-menu media folder."
            else "Could not open main-menu media folder."
        )
    }

    init {
        on<ScreenEvent.Open> {
            if (!enabled) return@on
            if (screen is FloydMainMenuScreen) return@on
            if (screen is TitleScreen && mc.screen === screen) mc.setScreen(FloydMainMenuScreen())
        }
        on<TickEvent.ClientEnd> {
            FloydMenuVideoBackground.tick()
        }
    }

    @JvmStatic
    fun mainMenuPath(): Path = FloydAddonsMod.configFile.toPath().resolve("mainmenu.png")

    @JvmStatic
    fun configuredMediaPath(): Path? = runCatching {
        mediaPath.takeIf { it.isNotBlank() }?.let(Path::of)
    }.getOrNull()

    private fun mediaDirectory(): Path =
        configuredMediaPath()?.parent ?: FloydAddonsMod.configFile.toPath()
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

/**
 * Owns the window-styling toggles that used to live on the old `General`/`Render` module
 * ([FloydRender]). Both settings are pure data holders here; the runtime that actually retitles or
 * borderless-izes the window lives on [FloydRender] (kept as an unregistered backing object), which
 * reads these values and drives GLFW from its per-tick handler — never from setting init/setters.
 */
object FloydWindowModule : Module(
    name = "Window",
    category = Category.MISC,
    description = "Borderless window toggle and custom instance/taskbar title.",
    toggled = true,
) {
    var borderlessWindowed by BooleanSetting("Borderless Window", false, desc = "Matches Floyd's borderless window toggle.")
    val windowTitle by StringSetting("Instance Title", "", 64, desc = "Custom taskbar/window title.")

    init {
        on<TickEvent.ClientEnd> {
            FloydRender.tickWindowState()
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

/**
 * Stops the game pausing (opening the Game Menu) when the window loses focus, by forcing the vanilla
 * `pauseOnLostFocus` option off while enabled. The user's original value is captured on enable and
 * restored on disable; the option is re-pinned each client tick so nothing re-enables the pause.
 * Crash-safe: all option access is guarded.
 */
object FloydFocusLossPrevention : Module(
    name = "Focus Loss Prevention",
    category = Category.MISC,
    description = "Never pauses the game (opens the menu) when the window loses focus.",
    toggled = false,
) {
    // The user's pauseOnLostFocus value before we forced it off; restored on disable.
    private var savedPauseOnLostFocus: Boolean? = null

    init {
        // Only fires while subscribed (i.e. enabled); re-pins the option in case anything resets it.
        on<TickEvent.ClientEnd> { enforce() }
    }

    override fun onEnable() {
        super.onEnable()
        enforce()
    }

    override fun onDisable() {
        super.onDisable()
        restore()
    }

    private fun enforce() {
        runCatching {
            val options = FloydAddonsMod.mc.options
            if (savedPauseOnLostFocus == null) savedPauseOnLostFocus = options.pauseOnLostFocus
            if (options.pauseOnLostFocus) options.pauseOnLostFocus = false
        }
    }

    private fun restore() {
        runCatching {
            savedPauseOnLostFocus?.let { FloydAddonsMod.mc.options.pauseOnLostFocus = it }
        }
        savedPauseOnLostFocus = null
    }
}
