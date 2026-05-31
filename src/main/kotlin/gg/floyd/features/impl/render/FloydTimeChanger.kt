package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module

/**
 * Overrides the world time with a user-controlled value.
 *
 * Un-nested from [FloydRender]'s buried `Time Changer`/`Time` settings into its own module so it can
 * be toggled independently (matches V2 2.0.3). The module's `enabled` flag is the gate.
 */
object FloydTimeChanger : Module(
    name = "Time Changer",
    category = Category.RENDER,
    description = "Overrides the world time with a user-controlled value.",
    toggled = false,
) {
    val timeValue by NumberSetting("Time", 50f, 0f, 100f, 1f, desc = "World time slider used by custom time.")

    init {
        on<TickEvent.ClientEnd> {
            if (shouldUseCustomTime()) applyCustomTimeOverride()
        }
    }

    @JvmStatic
    fun shouldUseCustomTime(): Boolean = enabled

    @JvmStatic
    fun applyCustomTimeOverride() {
        val levelData = mc.level?.levelData ?: return
        val ticks = customTimeTicks(timeValue)
        levelData.setDayTime(ticks)
        levelData.setGameTime(ticks)
    }

    @JvmStatic
    fun customTimeTicks(value: Float): Long =
        Math.round((value.coerceIn(0f, 100f) / 100f) * 23999L).coerceIn(0, 23999).toLong()

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "time" to timeValue,
        "shouldUseCustomTime" to shouldUseCustomTime()
    )
}
