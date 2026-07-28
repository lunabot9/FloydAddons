package gg.floyd.features.impl.render

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.jvm.optionals.getOrNull

object FloydSkyBlockItemFallbacks {
    val ItemStack.customData: CompoundTag
        get() = getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag()

    fun skyBlockId(tag: CompoundTag): String? =
        tag.getString("id").getOrNull()?.replace(":", "-")

    fun skinId(tag: CompoundTag): String? =
        tag.getString("skin").getOrNull()?.replace(":", "-")

    private val diamondSword = Items.DIAMOND_SWORD.itemModel
    private val goldenSword = Items.GOLDEN_SWORD.itemModel
    private val attunedModels = mapOf(
        "FIREDUST_DAGGER" to (1 to goldenSword),
        "BURSTFIRE_DAGGER" to (1 to goldenSword),
        "HEARTFIRE_DAGGER" to (1 to goldenSword),
        "MAWDUST_DAGGER" to (3 to diamondSword),
        "BURSTMAW_DAGGER" to (3 to diamondSword),
        "HEARTMAW_DAGGER" to (3 to diamondSword),
    )
    private val katanas = setOf("VOIDEDGE_KATANA", "VORPAL_KATANA", "ATOMSPLIT_KATANA")
    private val fungiCutters = setOf("FUNGI_CUTTER", "FUNGI_CUTTER_2", "FUNGI_CUTTER_3")

    fun resolveDynamic(
        skyBlockId: String,
        stack: ItemStack,
        customData: CompoundTag,
        fallback: Identifier,
    ): Identifier = when (skyBlockId) {
        in attunedModels -> attunedModels.getValue(skyBlockId).let { (mode, model) ->
            if (customData.getInt("td_attune_mode").orElse(-1) == mode) model else fallback
        }
        in katanas -> if (Minecraft.getInstance().player?.cooldowns?.isOnCooldown(stack) == true) goldenSword else fallback
        in fungiCutters -> when (customData.getString("fungi_cutter_mode").orElse(null)) {
            "RED" -> Items.RED_MUSHROOM.itemModel
            "BROWN" -> Items.BROWN_MUSHROOM.itemModel
            else -> fallback
        }
        else -> fallback
    }

    private val Item.itemModel: Identifier
        get() = components()[DataComponents.ITEM_MODEL]!!
}
