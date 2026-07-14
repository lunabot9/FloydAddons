package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.hiders.FloydHiders;
import gg.floyd.utils.render.WorldToScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void floydaddons$noHurtCamera(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        if (FloydHiders.shouldSuppressHurtCamera()) {
            FloydHiders.recordHurtCamera();
            ci.cancel();
        }
    }

    /**
     * Captures the composed view-bob / hurt-camera transform exactly where vanilla bakes it into the
     * frame's projection: {@code matrix4f.mul(poseStack.last().pose())} in {@code renderLevel}. Here
     * {@code proj} is the pre-bob projection and {@code bob} is {@code poseStack.last().pose()} — the
     * eye-space bob. Recording it lets {@link WorldToScreen#tracerOrigin(float)} invert the bob so
     * tracer origins lock to screen center instead of wobbling. We then perform the original
     * multiplication unchanged so vanilla rendering is untouched. require=0 so a future mapping change
     * degrades gracefully (tracers fall back to the eye-based origin) instead of crashing.
     */
    @Redirect(
        method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;"),
        require = 0
    )
    private Matrix4f floydaddons$captureViewBob(Matrix4f proj, Matrix4fc bob) {
        WorldToScreen.captureBob(bob);
        return proj.mul(bob);
    }
}
