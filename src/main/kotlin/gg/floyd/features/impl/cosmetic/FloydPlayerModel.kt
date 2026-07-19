package gg.floyd.features.impl.cosmetic

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.SelectorSetting
import gg.floyd.features.Category
import gg.floyd.features.Module

internal object FloydPlayerModelSelection {
    private const val MINION_MODEL = "Minion"
    private const val ORTHODOX_MAN_MODEL = "Orthodox Man"
    private val customModels = listOf("Tung Tung Sahur", "George Floyd", "Jenny", ORTHODOX_MAN_MODEL, MINION_MODEL)
    val models = customModels + VanillaMobCatalog.labels
    val modelDescriptions = mapOf(
        "Tung Tung Sahur" to "Tung Tung Sahur player model created by ImJoyler.",
        ORTHODOX_MAN_MODEL to "A bundled suit-and-hat player model inspired by a reference portrait.",
        MINION_MODEL to "Uses the Copper Golem's height and animations with a Floyd minion texture."
    ) + VanillaMobCatalog.labels.associateWith {
        "Uses Minecraft's built-in mob model, texture, and animations for your player."
    }

    fun selectedName(index: Int): String = models.getOrElse(index) { models.first() }

    fun vanillaMobId(model: String): String? =
        if (model == MINION_MODEL) "copper_golem"
        else VanillaMobCatalog.idForLabel(model)
}

/**
 * Replaces the local player's rendered body with a Floyd-owned model. Nothing is sent to the
 * server and no entity dimensions, hitboxes, movement, or gameplay state are changed.
 */
object FloydPlayerModel : Module(
    name = "Player Model",
    category = Category.COSMETIC,
    description = "Client-side-only custom model for your player. Tung Tung Sahur model by ImJoyler.",
    toggled = false,
) {
    private val model by SelectorSetting(
        "Model",
        FloydPlayerModelSelection.models.first(),
        FloydPlayerModelSelection.models,
        desc = "The custom model shown for your local player.",
        optionDescriptions = FloydPlayerModelSelection.modelDescriptions
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
    fun selectedVanillaMobIdFor(id: Int): String? = FloydPlayerModelSelection.vanillaMobId(selectedModelFor(id))

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
    fun shouldHideHeldItemFor(id: Int): Boolean =
        shouldHideHeldItem(isActiveFor(id), selectedModelFor(id))

    @JvmStatic
    fun shouldHideHeldItem(customModelActive: Boolean, selectedModel: String): Boolean =
        customModelActive && selectedModel == FloydPlayerModelSelection.models.first()

    @JvmStatic
    fun usesBundledLayerFor(id: Int): Boolean = isActiveFor(id) && selectedVanillaMobIdFor(id) == null

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "model" to selectedModel(),
        "vanillaMobId" to FloydPlayerModelSelection.vanillaMobId(selectedModel()),
        "vanillaMobRenderer" to VanillaMobPlayerModel.state(),
        "showHeads" to showHeads,
        "jennyJiggle" to (mc.player?.id?.let(JennyJiggleMotion::stateFor) ?: mapOf("x" to 0f, "y" to 0f, "z" to 0f)),
        "localOnly" to true
    )
}
