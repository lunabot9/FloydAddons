package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks;
import gg.floyd.features.impl.render.FloydSkyBlockPackAssets;
import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerHeadSpecialRenderer.class)
public abstract class SkyBlockPlayerHeadMixin {
    @WrapOperation(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object replaceSkyBlockHeadProfile(ItemStack stack, DataComponentType<?> type, Operation<ResolvableProfile> original) {
        var current = original.call(stack, type);
        if (!FloydSkyBlockPackDisabler.INSTANCE.getEnabled() || stack.isEmpty() || stack.is(Items.PLAYER_HEAD)) return current;
        var data = FloydSkyBlockItemFallbacks.INSTANCE.getCustomData(stack);
        var id = FloydSkyBlockItemFallbacks.INSTANCE.skyBlockId(data);
        if (id == null) return current;
        var profiles = FloydSkyBlockPackAssets.INSTANCE.getSkullProfiles();
        var skin = FloydSkyBlockItemFallbacks.INSTANCE.skinId(data);
        var profile = skin == null ? null : profiles.get(skin);
        if (profile == null) profile = profiles.get(id);
        return profile == null ? current : profile;
    }
}
