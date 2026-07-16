package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.cosmetic.FloydPlayerModel;
import gg.floyd.features.impl.hiders.FloydHiders;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
public class HiderHeadLayerMixin<S extends LivingEntityRenderState, M extends EntityModel<S> & HeadedModel> {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$hideHead(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState playerState)) return;

        boolean hideWithArmor = FloydHiders.shouldHideArmorFor(playerState.id);
        boolean hideHead = FloydPlayerModel.shouldHideHead(
            FloydPlayerModel.isActiveFor(playerState.id),
            playerState.wornHeadType != null,
            FloydPlayerModel.shouldShowHeadsFor(playerState.id)
        );
        if (!hideWithArmor && !hideHead) return;

        FloydHiders.recordHeadLayer();
        ci.cancel();
    }
}
