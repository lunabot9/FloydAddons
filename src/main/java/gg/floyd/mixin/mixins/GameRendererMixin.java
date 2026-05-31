package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.hiders.FloydHiders;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void floydaddons$noHurtCamera(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (FloydHiders.shouldSuppressHurtCamera()) {
            FloydHiders.recordHurtCamera();
            ci.cancel();
        }
    }
}
