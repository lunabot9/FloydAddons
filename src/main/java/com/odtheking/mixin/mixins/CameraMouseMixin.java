package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.camera.FloydCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class CameraMouseMixin {
    @Redirect(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void floydaddons$redirectLookDirection(LocalPlayer player, double deltaX, double deltaY) {
        if (FloydCamera.freecamActive()) {
            FloydCamera.adjustFreecamLook(deltaX, deltaY);
        } else if (FloydCamera.freelookActive()) {
            FloydCamera.adjustFreelook(deltaX, deltaY);
        } else {
            player.turn(deltaX, deltaY);
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
