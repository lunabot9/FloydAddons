package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.camera.FloydCamera;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class CameraLocalPlayerInputMixin {
    @Shadow public ClientInput input;

    @Unique private ClientInput floydaddons$savedInput;

    @Inject(method = "tick", at = @At("HEAD"))
    private void floydaddons$freezeInputDuringFreecam(CallbackInfo ci) {
        if (!FloydCamera.freecamActive()) return;
        floydaddons$savedInput = input;
        input = new ClientInput();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void floydaddons$restoreInputAfterFreecam(CallbackInfo ci) {
        if (floydaddons$savedInput == null) return;
        input = floydaddons$savedInput;
        floydaddons$savedInput = null;
    }
}
