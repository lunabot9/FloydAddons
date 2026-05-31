package floydaddons.not.dogshit.client.features.impl.render

import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module

object FloydCustomScoreboard : Module(
    name = "Custom Scoreboard",
    category = Category.RENDER,
    description = "Replaces the vanilla scoreboard with the Floyd HUD version.",
    toggled = false,
) {
    init {
        registerFloydHudSettings(
            "Scoreboard HUD",
            "Scoreboard HUD Scale",
            "Scoreboard HUD Corner Radius",
            "Scoreboard HUD Color",
            "Scoreboard HUD Chroma",
            "Scoreboard HUD Fade Color",
            "Scoreboard HUD Fade",
            "Scoreboard HUD Minecraft Font"
        )
    }

    @JvmStatic
    fun shouldUseCustomScoreboard(): Boolean = enabled

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "shouldUseCustomScoreboard" to shouldUseCustomScoreboard()
    )
}
