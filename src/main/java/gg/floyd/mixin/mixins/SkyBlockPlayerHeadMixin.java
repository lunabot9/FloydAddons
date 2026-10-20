package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gg.floyd.features.impl.render.FloydSkyBlockItemFallbacks;
import gg.floyd.features.impl.render.FloydSkyBlockPackAssets;
import gg.floyd.features.impl.render.FloydSkyBlockPackDisabler;
import gg.floyd.features.impl.render.FloydHeadTextureCache;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.WeakHashMap;

@Mixin(PlayerHeadSpecialRenderer.class)
public abstract class SkyBlockPlayerHeadMixin {
    @Shadow @Final private PlayerSkinRenderCache playerSkinRenderCache;
    @Unique private final WeakHashMap<PlayerSkinRenderCache.RenderInfo, PlayerSkinRenderCache.RenderInfo> floydaddons$alphaHeads = new WeakHashMap<>();

    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;", at = @At("RETURN"), cancellable = true)
    private void floydaddons$keepItemHeadAlpha(ItemStack stack, CallbackInfoReturnable<PlayerSkinRenderCache.RenderInfo> cir) {
        var original = cir.getReturnValue();
        if (original == null) return;
        var supplied = stack.get(net.minecraft.core.component.DataComponents.PROFILE);
        if (supplied != null && supplied.skinPatch().body().isPresent()) return;
        var fixed = floydaddons$alphaHeads.get(original);
        if (fixed == null) {
            var patch = FloydHeadTextureCache.patch(original.gameProfile());
            if (patch == null) return;
            fixed = playerSkinRenderCache.new RenderInfo(original.gameProfile(), original.playerSkin(), patch);
            floydaddons$alphaHeads.put(original, fixed);
        }
        cir.setReturnValue(fixed);
    }
    @WrapOperation(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object replaceSkyBlockHeadProfile(ItemStack stack, DataComponentType<?> type, Operation<ResolvableProfile> original) {
        var current = original.call(stack, type);
        if (current != null || !FloydSkyBlockPackDisabler.INSTANCE.getEnabled() || stack.isEmpty() || stack.is(Items.PLAYER_HEAD)) return current;
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
