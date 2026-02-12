package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.CameraConfig;
import floydaddons.not.dogshit.client.FloydAddonsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class CameraMouseMixin {

    @Redirect(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"), require = 0)
    private void floydaddons$redirectLookDirection(net.minecraft.client.network.ClientPlayerEntity player,
                                                    double cursorDeltaX, double cursorDeltaY) {
        if (CameraConfig.isFreecamEnabled()) {
            float yawDelta = (float) cursorDeltaX * 0.15f;
            float pitchDelta = (float) cursorDeltaY * 0.15f;
            CameraConfig.setFreecamYaw(CameraConfig.getFreecamYaw() + yawDelta);
            CameraConfig.setFreecamPitch(CameraConfig.getFreecamPitch() + pitchDelta);
        } else if (CameraConfig.isFreelookEnabled()) {
            float yawDelta = (float) cursorDeltaX * 0.15f;
            float pitchDelta = (float) cursorDeltaY * 0.15f;
            CameraConfig.setFreelookYaw(CameraConfig.getFreelookYaw() + yawDelta);
            CameraConfig.setFreelookPitch(CameraConfig.getFreelookPitch() + pitchDelta);
        } else {
            player.changeLookDirection(cursorDeltaX, cursorDeltaY);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$handleScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CameraConfig.isFreelookEnabled()) {
            float newDist = CameraConfig.getFreelookDistance() - (float) vertical * 0.5f;
            CameraConfig.setFreelookDistance(Math.max(1.0f, Math.min(20.0f, newDist)));
            ci.cancel();
        } else if (CameraConfig.isF5ScrollEnabled()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.getPerspective() != Perspective.FIRST_PERSON) {
                float newDist = CameraConfig.getF5CameraDistance() - (float) vertical * 0.5f;
                CameraConfig.setF5CameraDistance(Math.max(1.0f, Math.min(20.0f, newDist)));
                FloydAddonsConfig.save();
                ci.cancel();
            }
        }
    }
}
