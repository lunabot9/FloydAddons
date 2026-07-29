package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.cosmetic.LowPolyTungImportedModel;
import gg.floyd.features.impl.cosmetic.FloydPlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class HiderHeldItemLayerMixin {
    @Unique
    private boolean floydaddons$pushedHeldItemOffset;

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

    @Inject(
        method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("HEAD")
    )
    private void floydaddons$offsetLowPolyTungHeldItem(
        ArmedEntityRenderState state,
        ItemStackRenderState itemState,
        ItemStack itemStack,
        HumanoidArm arm,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        CallbackInfo ci
    ) {
        if (!(state instanceof AvatarRenderState playerState) ||
            !FloydPlayerModel.shouldOffsetHeldItemFor(playerState.id) ||
            itemState.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        var offset = LowPolyTungImportedModel.heldItemOffset(arm);
        poseStack.translate((float) offset.x, (float) offset.y, (float) offset.z);
        floydaddons$pushedHeldItemOffset = true;
    }

    @Inject(
        method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("RETURN")
    )
    private void floydaddons$popLowPolyTungHeldItem(
        ArmedEntityRenderState state,
        ItemStackRenderState itemState,
        ItemStack itemStack,
        HumanoidArm arm,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        CallbackInfo ci
    ) {
        if (!floydaddons$pushedHeldItemOffset) return;
        floydaddons$pushedHeldItemOffset = false;
        poseStack.popPose();
    }
}
