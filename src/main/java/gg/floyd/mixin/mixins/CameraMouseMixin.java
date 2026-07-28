package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.camera.FloydCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(MouseHandler.class)
public class CameraMouseMixin {
    @Inject(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void floydaddons$redirectLookDirection(
        double deltaTime,
        CallbackInfo ci,
        double sensitivity,
        double cubicSensitivity,
        double turnScale,
        double deltaX,
        double deltaY
    ) {
        if (FloydCamera.freecamActive()) {
            FloydCamera.adjustFreecamLook(deltaX, deltaY);
            ci.cancel();
        } else if (FloydCamera.freelookActive()) {
            FloydCamera.adjustFreelook(deltaX, deltaY);
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void floydaddons$handleScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (FloydCamera.freelookActive()) {
            FloydCamera.adjustFreelookDistance(vertical);
            ci.cancel();
            return;
        }

        if (FloydCamera.shouldScrollF5() && Minecraft.getInstance().options.getCameraType() != CameraType.FIRST_PERSON) {
            FloydCamera.adjustF5DistanceAndSave(vertical);
            ci.cancel();
        }
    }
}
