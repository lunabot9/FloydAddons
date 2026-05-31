package gg.floyd.features.impl.camera

import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.KeybindSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.render.ClickGUIModule
import gg.floyd.utils.moduleToggle
import org.lwjgl.glfw.GLFW

/**
 * Per-feature camera modules.
 *
 * These used to be runtime/boolean settings on the old `FloydCamera` mega-module. Each is now a
 * top-level [Module] so the click GUI lists them directly under the Camera category. The plain
 * [FloydCamera] object keeps all runtime camera state + math and reads these modules via facades so
 * the mixins do not have to change.
 *
 * Freecam and Freelook are transient: they are never persisted as active across restarts so the
 * player never spawns directly into freecam/freelook (see [FloydCamera.resetTransientModes]).
 */
object FloydFreecam : Module(
    name = "Freecam",
    category = Category.CAMERA,
    description = "Detached spectator-style camera.",
) {
    val speed by NumberSetting("Speed", 1.0f, 0.1f, 10.0f, 0.1f, desc = "Movement speed for freecam.")
    private val toggleKey by KeybindSetting("Toggle Freecam", GLFW.GLFW_KEY_UNKNOWN, desc = "Floyd freecam toggle key.").onPress {
        toggle()
        if (ClickGUIModule.enableNotification) moduleToggle(name, enabled)
    }

    override fun onEnable() {
        super.onEnable()
        FloydCamera.beginFreecam()
    }

    override fun onDisable() {
        FloydCamera.endFreecam()
        super.onDisable()
    }
}

object FloydFreelook : Module(
    name = "Freelook",
    category = Category.CAMERA,
    description = "Orbit camera around the player.",
) {
    var distance by NumberSetting("Distance", 4.0f, 1.0f, 20.0f, 0.5f, desc = "Third-person freelook distance.")
    private val toggleKey by KeybindSetting("Toggle Freelook", GLFW.GLFW_KEY_V, desc = "Floyd freelook toggle key.").onPress {
        toggle()
        if (ClickGUIModule.enableNotification) moduleToggle(name, enabled)
    }

    override fun onEnable() {
        super.onEnable()
        FloydCamera.beginFreelook()
    }

    override fun onDisable() {
        FloydCamera.endFreelook()
        super.onDisable()
    }
}

object FloydF5Customizer : Module(
    name = "F5 Customizer",
    category = Category.CAMERA,
    description = "Floyd F5 distance, scroll, and no-clip controls.",
    toggled = true,
) {
    var disableFront by BooleanSetting("Disable Front Cam", false, desc = "Skips front-facing third person when cycling camera.")
    var disableBack by BooleanSetting("Disable Back Cam", false, desc = "Skips back-facing third person when cycling camera.")
    var noClip by BooleanSetting("No Third-Person Clipping", false, desc = "Stops third-person camera distance from being clipped by blocks.")
    var scrollEnabled by BooleanSetting("Scrolling Changes Distance", false, desc = "Mouse wheel changes third-person camera distance.")
    var resetOnToggle by BooleanSetting("Reset F5 Scrolling", false, desc = "Resets third-person camera distance when cycling camera.")
    var f5Distance by NumberSetting("Camera Distance", 4.0f, 1.0f, 20.0f, 0.5f, desc = "Third-person camera distance.")
}
