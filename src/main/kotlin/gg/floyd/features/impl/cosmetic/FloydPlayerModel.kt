package gg.floyd.features.impl.cosmetic

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.features.Category
import gg.floyd.features.Module

internal object FloydPlayerModelSelection {
    val models = listOf("Tung Tung Sahur", "George Floyd", "Jenny")

    fun selectedName(index: Int): String = models.getOrElse(index) { models.first() }
}

/**
 * Replaces the local player's rendered body with a Floyd-owned model. Nothing is sent to the
 * server and no entity dimensions, hitboxes, movement, or gameplay state are changed.
 */
object FloydPlayerModel : Module(
    name = "Player Model",
    category = Category.COSMETIC,
    description = "Client-side-only custom model for your player.",
    toggled = false,
) {
    private val model by SelectorSetting(
        "Model",
        FloydPlayerModelSelection.models.first(),
        FloydPlayerModelSelection.models,
        desc = "The custom model shown for your local player."
    )

    @JvmStatic
    fun isActiveFor(id: Int): Boolean = enabled && mc.player?.id == id

    @JvmStatic
    fun selectedModel(): String = FloydPlayerModelSelection.selectedName(model)

    @JvmStatic
    fun isGeorgeFloydModel(): Boolean = selectedModel() == "George Floyd"

    @JvmStatic
    fun isJennyModel(): Boolean = selectedModel() == "Jenny"

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "model" to selectedModel(),
        "localOnly" to true
    )
}
