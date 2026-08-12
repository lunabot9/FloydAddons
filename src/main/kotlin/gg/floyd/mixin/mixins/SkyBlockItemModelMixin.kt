package gg.floyd.mixin.mixins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.customData
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.logUnresolvedModel
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.resolveDynamic
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.skyBlockId
import gg.floyd.features.impl.render.FloydSkyBlockItemModelPolicy
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
        original: Operation<Identifier?>,
    ): Any? {
        val currentModel: Identifier? = original.call(stack, componentType)
        if (!FloydSkyBlockItemModelPolicy.shouldReplaceCurrentModel(
                currentModel = currentModel,
                packDisablerEnabled = FloydSkyBlockPackDisabler.enabled,
                stackEmpty = stack.isEmpty,
            )
        ) {
            return currentModel
        }
        checkNotNull(currentModel)
        val customData = stack.customData
        val skyBlockId = skyBlockId(customData)
        val vanillaItemModel = stack.item.components()[DataComponents.ITEM_MODEL] ?: currentModel
        val liveBaseModel = FloydSkyBlockPackAssets.liveItemBaseModels[currentModel]
        val vanillaModel = FloydSkyBlockItemModelPolicy.resolveBaseModel(
            currentModel = currentModel,
            skyBlockId = skyBlockId,
            liveModels = FloydSkyBlockPackAssets.liveItemBaseModels,
            knownModels = FloydSkyBlockPackAssets.itemModels,
            vanillaItemModel = vanillaItemModel,
            quiverArrowModel = customData
                .takeIf { it.contains("quiver_arrow") }
                ?.let { Items.ARROW.components()[DataComponents.ITEM_MODEL] },
        )

        val resolvedModel = skyBlockId?.let { resolveDynamic(it, stack, customData, vanillaModel) } ?: vanillaModel
        logUnresolvedModel(
            currentModel = currentModel,
            resolvedModel = resolvedModel,
            stack = stack,
            customData = customData,
            skyBlockId = skyBlockId,
            vanillaItemModel = vanillaItemModel,
            liveBaseModel = liveBaseModel,
        )
        return resolvedModel
    }
}
