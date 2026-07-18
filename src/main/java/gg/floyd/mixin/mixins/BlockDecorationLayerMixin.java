package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.cosmetic.VanillaMobPlayerModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.BlockDecorationLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockDecorationLayer.class)
public abstract class BlockDecorationLayerMixin<S extends EntityRenderState, M extends EntityModel<S>> {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void floydaddons$hideMinionAntenna(PoseStack poseStack, SubmitNodeCollector collector, int light, S renderState, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (VanillaMobPlayerModel.minionTextureFor(renderState) != null) ci.cancel();
    }
}
