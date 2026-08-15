package gg.floyd.features.impl.misc

import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.ColorSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.events.ScreenEvent
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.render.FloydRender
import gg.floyd.utils.Color
import net.minecraft.client.gui.screens.TitleScreen

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
    description = "Replaces the vanilla title flow with Floyd's animated custom main menu.",
    toggled = true,
) {
    val backgroundSpeed by NumberSetting("Background Speed", 1.0f, 0.2f, 3.0f, 0.05f, desc = "Playback speed of the landscape shader background.")
    val backgroundContrast by NumberSetting("Background Contrast", 1.1f, 0.5f, 2.0f, 0.05f, desc = "Post-process contrast for the landscape shader.")
    val backgroundSaturation by NumberSetting("Background Saturation", 1.3f, 0.0f, 2.5f, 0.05f, desc = "Post-process saturation for the landscape shader.")
    val backgroundBrightness by NumberSetting("Background Brightness", 1.3f, 0.4f, 2.5f, 0.05f, desc = "Post-process brightness for the landscape shader.")
    val backgroundVignette by NumberSetting("Background Vignette", 0.5f, 0.0f, 1.5f, 0.05f, desc = "Edge darkening strength for the landscape shader.")
    val skyTopColor by ColorSetting("Sky Top Color", Color(10, 7, 3), desc = "Upper sky color for the custom main menu shader.")
    val skyHorizonColor by ColorSetting("Sky Horizon Color", Color(27, 20, 8), desc = "Horizon color for the custom main menu shader.")
    val grassPrimaryColor by ColorSetting("Water Primary Color", Color(10, 7, 3), desc = "Primary water color for the custom main menu shader.")
    val grassSecondaryColor by ColorSetting("Water Secondary Color", Color(27, 20, 8), desc = "Secondary water color for the custom main menu shader.")
    val fogColor by ColorSetting("Fog Color", Color(27, 20, 8), desc = "Fog blend color for the custom main menu shader.")
    val sunColor by ColorSetting("Sun Color", Color(255, 184, 77), desc = "Sun and flare color for the custom main menu shader.")

    init {
        on<ScreenEvent.Open> {
            if (!enabled || !FloydCompatibility.shouldUseCustomMainMenu()) return@on
            if (screen is FloydMainMenuScreen) return@on
            if (screen is TitleScreen) mc.setScreen(FloydMainMenuScreen())
        }
        on<TickEvent.ClientEnd> {
            if (!FloydCompatibility.shouldUseCustomMainMenu()) {
                FloydMenuVideoBackground.shutdown()
                return@on
            }
            if (enabled && mc.screen is TitleScreen) {
                mc.setScreen(FloydMainMenuScreen())
                return@on
            }
            FloydMenuVideoBackground.tick()
        }
    }

    // Live toggle without a restart: swap the visible title screen to match the new state.
    override fun onEnable() {
        super.onEnable()
        if (!FloydCompatibility.shouldUseCustomMainMenu()) return
        runCatching {
            if (mc.screen is TitleScreen) mc.setScreen(FloydMainMenuScreen())
        }
    }

    override fun onDisable() {
        super.onDisable()
        runCatching {
            FloydMenuVideoBackground.shutdown()
            if (mc.screen is FloydMainMenuScreen) mc.setScreen(TitleScreen())
        }
    }
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
