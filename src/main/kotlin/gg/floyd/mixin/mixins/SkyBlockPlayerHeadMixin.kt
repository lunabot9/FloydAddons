package gg.floyd.mixin.mixins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.customData
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.skinId
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks.skyBlockId
import gg.floyd.features.impl.render.FloydSkyBlockPackAssets
import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer
import net.minecraft.core.component.DataComponentType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(PlayerHeadSpecialRenderer::class)
abstract class SkyBlockPlayerHeadMixin {
    @WrapOperation(
        method = ["extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache\u0024RenderInfo;"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;",
        )],
    )
    private fun replaceSkyBlockHeadProfile(
        stack: ItemStack,
        componentType: DataComponentType<*>,
        original: Operation<ResolvableProfile?>,
    ): Any? {
        val currentProfile = original.call(stack, componentType)
        if (!FloydSkyBlockPackDisabler.enabled || stack.isEmpty || stack.`is`(Items.PLAYER_HEAD)) {
            return currentProfile
        }

        val customData = stack.customData
        val skyBlockId = skyBlockId(customData) ?: return currentProfile
        return skinId(customData)?.let(FloydSkyBlockPackAssets.skullProfiles::get)
            ?: FloydSkyBlockPackAssets.skullProfiles[skyBlockId]
            ?: currentProfile
    }
}
