package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.cosmetic.FloydPlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class HiderHeldItemLayerMixin {
    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void floydaddons$hideTungTungHeldItem(
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        ArmedEntityRenderState state,
        float limbAngle,
        float limbDistance,
        CallbackInfo ci
    ) {
        if (state instanceof AvatarRenderState playerState &&
            FloydPlayerModel.shouldHideHeldItemFor(playerState.id)) {
            ci.cancel();
        }
    }
}
