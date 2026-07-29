package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.FloydAddonsMod;
import gg.floyd.features.impl.player.FloydPlayerSize;
import gg.floyd.features.impl.cosmetic.FloydSkin;
import gg.floyd.features.impl.cosmetic.FloydPlayerModel;
import gg.floyd.features.impl.cosmetic.LowPolyTungImportedModel;
import gg.floyd.mixin.accessors.LivingEntityRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    private PlayerModel floydaddons$playerModel() {
        EntityModel model = ((LivingEntityRendererAccessor) this).floydaddons$getModel();
        return model instanceof PlayerModel playerModel ? playerModel : null;
    }

    private boolean floydaddons$shouldRenderLowPolyFirstPerson() {
        return FloydPlayerModel.shouldUseLowPolyTungFirstPerson()
            && Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.getMainHandItem().isEmpty();
    }

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

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void floydaddons$renderLowPolyRightHand(
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        Identifier skinTexture,
        boolean sleeveVisible,
        CallbackInfo ci
    ) {
        if (!floydaddons$shouldRenderLowPolyFirstPerson()) return;
        PlayerModel playerModel = floydaddons$playerModel();
        if (playerModel == null) return;
        LowPolyTungImportedModel.renderFirstPersonArm(poseStack, collector, light, playerModel.rightArm, HumanoidArm.RIGHT);
        ci.cancel();
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void floydaddons$renderLowPolyLeftHand(
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        Identifier skinTexture,
        boolean sleeveVisible,
        CallbackInfo ci
    ) {
        if (!floydaddons$shouldRenderLowPolyFirstPerson()) return;
        PlayerModel playerModel = floydaddons$playerModel();
        if (playerModel == null) return;
        LowPolyTungImportedModel.renderFirstPersonArm(poseStack, collector, light, playerModel.leftArm, HumanoidArm.LEFT);
        ci.cancel();
    }

}
