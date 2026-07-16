package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.player.FloydPlayerSize;
import gg.floyd.features.impl.cosmetic.FloydSkin;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"))
    private void scale(AvatarRenderState avatarRenderState, PoseStack poseStack, CallbackInfo ci) {
        if (!FloydPlayerSize.shouldScale(avatarRenderState.id)) return;
        if (FloydPlayerSize.scaleYFor(avatarRenderState.id) < 0.0F) poseStack.translate(0.0F, FloydPlayerSize.negativeScaleYOffsetFor(avatarRenderState.id), 0.0F);
        poseStack.scale(FloydPlayerSize.scaleXFor(avatarRenderState.id), FloydPlayerSize.scaleYFor(avatarRenderState.id), FloydPlayerSize.scaleZFor(avatarRenderState.id));
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
    private void floydaddons$customSkin(AvatarRenderState avatarRenderState, CallbackInfoReturnable<Identifier> cir) {
        if (!FloydSkin.shouldUseCustomSkin(avatarRenderState.id)) return;
        Identifier custom = FloydSkin.customSkinTextureFor(avatarRenderState.id);
        if (custom != null) cir.setReturnValue(custom);
    }

}
