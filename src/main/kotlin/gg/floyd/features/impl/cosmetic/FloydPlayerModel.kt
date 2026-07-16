package gg.floyd.features.impl.cosmetic

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.BooleanSetting
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

    private val showHeads by BooleanSetting(
        "Show Heads",
        false,
        desc = "Shows equipped player and mob heads while the custom player model is active."
    )

    @JvmStatic
    fun isActiveFor(id: Int): Boolean =
        if (mc.player?.id == id) enabled else FloydSharedCosmetics.appearanceForEntity(id)?.model?.enabled == true

    @JvmStatic
    fun selectedModel(): String = FloydPlayerModelSelection.selectedName(model)

    @JvmStatic
    fun selectedModelFor(id: Int): String =
        if (mc.player?.id == id) selectedModel()
        else FloydSharedCosmetics.appearanceForEntity(id)?.model?.id ?: FloydPlayerModelSelection.models.first()

    @JvmStatic
    fun isGeorgeFloydModel(): Boolean = selectedModel() == "George Floyd"

    @JvmStatic
    fun isJennyModel(): Boolean = selectedModel() == "Jenny"

    @JvmStatic
    fun shouldShowHeads(): Boolean = showHeads

    @JvmStatic
    fun shouldShowHeadsFor(id: Int): Boolean =
        if (mc.player?.id == id) showHeads
        else FloydSharedCosmetics.appearanceForEntity(id)?.model?.showHeads ?: false

    @JvmStatic
    fun shouldHideHead(customModelActive: Boolean, hasWornHead: Boolean, showHeads: Boolean): Boolean =
        customModelActive && hasWornHead && !showHeads

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "model" to selectedModel(),
        "showHeads" to showHeads,
        "jennyJiggle" to (mc.player?.id?.let(JennyJiggleMotion::stateFor) ?: mapOf("x" to 0f, "y" to 0f, "z" to 0f)),
        "localOnly" to true
    )
}
