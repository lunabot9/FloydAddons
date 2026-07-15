package gg.floyd.mixin.mixins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.customData
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.resolveDynamic
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.skyBlockId
import gg.floyd.features.impl.render.FloydSkyBlockPackAssets
import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(ItemModelResolver::class)
abstract class SkyBlockItemModelMixin {
    @WrapOperation(
        method = ["appendItemLayers"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;",
        )],
    )
    private fun replaceSkyBlockItemModel(
        stack: ItemStack,
        componentType: DataComponentType<*>,
        original: Operation<Identifier>,
    ): Any {
        val currentModel = original.call(stack, componentType)
        if (!FloydSkyBlockPackDisabler.enabled || stack.isEmpty || currentModel.namespace != "hypixel_skyblock") {
            return currentModel
        }

        val customData = stack.customData
        val skyBlockId = skyBlockId(customData)
        val vanillaModel = when {
            skyBlockId != null -> FloydSkyBlockPackAssets.itemModels[skyBlockId]
            customData.contains("quiver_arrow") -> Items.ARROW.components()[DataComponents.ITEM_MODEL]
            else -> null
        } ?: return currentModel

        return skyBlockId?.let { resolveDynamic(it, stack, customData, vanillaModel) } ?: vanillaModel
    }
}
