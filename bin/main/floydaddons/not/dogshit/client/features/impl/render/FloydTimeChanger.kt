package floydaddons.not.dogshit.client.features.impl.render

import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.clickgui.settings.impl.NumberSetting
import floydaddons.not.dogshit.client.events.TickEvent
import floydaddons.not.dogshit.client.events.core.on
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module

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
