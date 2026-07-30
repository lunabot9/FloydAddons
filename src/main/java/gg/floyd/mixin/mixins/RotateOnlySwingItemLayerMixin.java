package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.render.RotateOnlySwingTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public class RotateOnlySwingItemLayerMixin {
    @WrapOperation(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V"
        )
    )
    private void floydaddons$rotateAroundItemOrigin(
        @Coerce Object layer,
        PoseStack.Pose pose,
        Operation<Void> original
    ) {
        RotateOnlySwingTransform.applyIfActive(pose);
        original.call(layer, pose);
    }
}
