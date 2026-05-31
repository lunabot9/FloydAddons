package gg.floyd.features.impl.hiders

import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.features.Category
import gg.floyd.features.Module

/**
 * Per-feature hider modules.
 *
 * Each of these used to be a single [BooleanSetting][gg.floyd.clickgui.settings.impl.BooleanSetting]
 * on the old `FloydHiders` mega-module. They are now top-level [Module]s so that the click GUI lists
 * each hider directly under the Hiders category. The plain [FloydHiders] object reads these via facade
 * methods so the mixins do not change.
 */
object FloydNoHurtCamera : Module(
    name = "No Hurt Camera",
    category = Category.HIDERS,
    description = "Suppresses hurt camera shake.",
)

object FloydRemoveFireOverlay : Module(
    name = "Remove Fire Overlay",
    category = Category.HIDERS,
    description = "Suppresses the first-person fire overlay.",
)

object FloydDisableHungerBar : Module(
    name = "Disable Hunger Bar",
    category = Category.HIDERS,
    description = "Hides the hunger bar.",
)

object FloydHidePotionEffects : Module(
    name = "Hide Potion Effects",
    category = Category.HIDERS,
    description = "Suppresses the potion effect HUD.",
)

object FloydThirdPersonCrosshair : Module(
    name = "3rd Person Crosshair",
    category = Category.HIDERS,
    description = "Allows the crosshair outside first person.",
)

object FloydHideEntityFire : Module(
    name = "Hide Entity Fire",
    category = Category.HIDERS,
    description = "Suppresses fire rendering on entities.",
)

object FloydDisableArrows : Module(
    name = "Disable Arrows",
    category = Category.HIDERS,
    description = "Hides arrows stuck in player models.",
)

object FloydRemoveFallingBlocks : Module(
    name = "Remove Falling Blocks",
    category = Category.HIDERS,
    description = "Suppresses falling block entity rendering.",
)

object FloydRemoveExplosionParticles : Module(
    name = "No Explosion Particles",
    category = Category.HIDERS,
    description = "Suppresses explosion particles.",
)

object FloydRemoveTabPing : Module(
    name = "Remove Tab Ping",
    category = Category.HIDERS,
    description = "Hides latency icons from the tab list.",
)

object FloydServerIdHider : Module(
    name = "Server ID Hider",
    category = Category.HIDERS,
    description = "Replaces Hypixel server IDs in rendered text.",
)

object FloydProfileIdHider : Module(
    name = "Profile ID Hider",
    category = Category.HIDERS,
    description = "Replaces profile UUID lines in rendered text.",
    toggled = true,
)

object FloydHideWatchdogMessages : Module(
    name = "Hide Watchdog Messages",
    category = Category.HIDERS,
    description = "Suppresses Hypixel Watchdog announcement spam.",
    toggled = true,
)

object FloydModHider : Module(
    name = "Mod Hider",
    category = Category.HIDERS,
    description = "Hides floydaddons from basic Fabric loader lookups.",
    toggled = true,
)

object FloydNoArmor : Module(
    name = "No Armor",
    category = Category.HIDERS,
    description = "Matches Floyd's armor hiding mode.",
    toggled = true,
) {
    val noArmorMode by SelectorSetting("Target", "Off", listOf("Off", "Self", "Others", "All"), desc = "Matches Floyd's armor hiding mode.")

    fun modeName(): String = when (noArmorMode) {
        1 -> "Self"
        2 -> "Others"
        3 -> "All"
        else -> "Off"
    }

    fun shouldHideArmorFor(entityId: Int): Boolean {
        if (!enabled) return false
        val player = mc.player ?: return false
        val isSelf = entityId == player.id
        return when (modeName()) {
            "Self" -> isSelf
            "Others" -> !isSelf
            "All" -> true
            else -> false
        }
    }
}
