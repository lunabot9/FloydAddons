package floydaddons.not.dogshit.client.features.impl.render

import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module

object FloydCustomScoreboard : Module(
    name = "Custom Scoreboard",
    category = Category.HUD,
    description = "Replaces the vanilla scoreboard with the Floyd HUD version.",
    toggled = false,
) {
    @JvmStatic
    fun shouldUseCustomScoreboard(): Boolean = enabled

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "shouldUseCustomScoreboard" to shouldUseCustomScoreboard()
    )
}
