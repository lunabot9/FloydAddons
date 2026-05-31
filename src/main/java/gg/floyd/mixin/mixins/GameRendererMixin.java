package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.FloydAddonsMod;
import gg.floyd.features.impl.hiders.FloydHiders;
import gg.floyd.utils.render.WorldToScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
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

    // Capture the live world-render matrices so screen-space overlays and view-bob-stable
    // tracers can be derived outside the world render pass. matrix4f is the frame projection
    // (bob already multiplied in), poseStack is the composed bob/hurt transform, matrix4f2 is
    // the camera-rotation modelview.
    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/renderer/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
        )
    )
    private void floydaddons$captureRenderMatrices(
        DeltaTracker deltaTracker,
        CallbackInfo ci,
        @Local(ordinal = 0) Matrix4f projection,
        @Local(ordinal = 1) Matrix4f modelView,
        @Local PoseStack bobPoseStack
    ) {
        WorldToScreen.capture(
            projection,
            modelView,
            bobPoseStack.last().pose(),
            FloydAddonsMod.getMc().gameRenderer.getMainCamera().position()
        );
    }
}
