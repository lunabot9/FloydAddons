package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gg.floyd.features.impl.render.FloydAnimations;
import gg.floyd.features.impl.render.RotateOnlySwingTransform;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class AnimationItemInHandRendererMixin {
    @Inject(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
        )
    )
    private void floydaddons$applyItemTransforms(
        AbstractClientPlayer player,
        float partialTick,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equipProgress,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int packedLight,
        CallbackInfo ci
    ) {
        if (!FloydAnimations.shouldApply() || hand != InteractionHand.MAIN_HAND) return;

        FloydAnimations.recordItemTransform();
        poseStack.translate(FloydAnimations.xOffset(), FloydAnimations.yOffset(), FloydAnimations.zOffset());
        poseStack.mulPose(Axis.XP.rotationDegrees(FloydAnimations.xRotation()));
        poseStack.mulPose(Axis.YP.rotationDegrees(FloydAnimations.yRotation()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(FloydAnimations.zRotation()));

        float scale = FloydAnimations.itemScale();
        if (scale != 1.0f) poseStack.scale(scale, scale, scale);

        RotateOnlySwingTransform.begin(swingProgress, player.getMainArm());
    }

    @Inject(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            shift = At.Shift.AFTER
        )
    )
    private void floydaddons$clearRotateOnlySwing(
        AbstractClientPlayer player,
        float partialTick,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equipProgress,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int packedLight,
        CallbackInfo ci
    ) {
        if (hand == InteractionHand.MAIN_HAND) RotateOnlySwingTransform.end();
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void floydaddons$hideEmptyMainHand(
        AbstractClientPlayer player,
        float partialTick,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equipProgress,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int packedLight,
        CallbackInfo ci
    ) {
        if (FloydAnimations.shouldHideEmptyMainHand() && hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
            FloydAnimations.recordHideEmptyMainHand();
            ci.cancel();
        }
    }

    @Inject(method = "applyItemArmTransform", at = @At("HEAD"), cancellable = true)
    private void floydaddons$cancelReEquip(PoseStack poseStack, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        if (!FloydAnimations.shouldCancelReEquip()) return;

        FloydAnimations.recordCancelReEquip();
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(direction * 0.56f, -0.52f, -0.72f);
        ci.cancel();
    }

    @WrapOperation(
        method = "swingArm",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"
        )
    )
    private void floydaddons$removeSwingTravel(
        PoseStack poseStack,
        float x,
        float y,
        float z,
        Operation<Void> original
    ) {
        if (FloydAnimations.shouldSuppressSwingMotion()) {
            if (FloydAnimations.shouldUseNoSwing()) {
                FloydAnimations.recordNoSwing();
            } else {
                FloydAnimations.recordRotateOnlySwing();
            }
            return;
        }
        original.call(poseStack, x, y, z);
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void floydaddons$moveSwingRotationToItemSpace(
        PoseStack poseStack,
        HumanoidArm arm,
        float swingProgress,
        CallbackInfo ci
    ) {
        if (FloydAnimations.shouldSuppressSwingMotion()) ci.cancel();
    }
}
