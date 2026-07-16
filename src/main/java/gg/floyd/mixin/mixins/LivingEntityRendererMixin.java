package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.cosmetic.FloydCapeLayer;
import gg.floyd.features.impl.cosmetic.FloydConeHatLayer;
import gg.floyd.features.impl.cosmetic.FloydPlayerModel;
import gg.floyd.features.impl.cosmetic.FloydPlayerModelLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected abstract boolean addLayer(RenderLayer renderLayer);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void floydaddons$addCosmeticLayers(EntityRendererProvider.Context context, EntityModel model, float shadowRadius, CallbackInfo ci) {
        if (!((Object) this instanceof AvatarRenderer<?> renderer)) return;

        RenderLayerParent<AvatarRenderState, PlayerModel> parent = renderer;
        addLayer(new FloydCapeLayer(parent));
        addLayer(new FloydConeHatLayer(parent));
        addLayer(new FloydPlayerModelLayer(parent));
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void floydaddons$hideVanillaPlayerModel(LivingEntityRenderState state, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (!((Object) this instanceof AvatarRenderer<?>) || !(state instanceof AvatarRenderState avatarState)) return;
        if (FloydPlayerModel.isActiveFor(avatarState.id)) cir.setReturnValue(null);
    }
}
