package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.camera.FloydCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class CameraClientControlMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void floydaddons$blockFreecamAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FloydCamera.freecamActive()) cir.setReturnValue(false);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void floydaddons$blockFreecamUse(CallbackInfo ci) {
        if (FloydCamera.freecamActive()) ci.cancel();
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void floydaddons$blockFreecamBreaking(boolean leftClick, CallbackInfo ci) {
        if (FloydCamera.freecamActive()) ci.cancel();
    }

    @Inject(method = "disconnectFromWorld", at = @At("HEAD"))
    private void floydaddons$disableCameraModesOnDisconnect(Component component, CallbackInfo ci) {
        FloydCamera.disableCameraModes();
    }
}
