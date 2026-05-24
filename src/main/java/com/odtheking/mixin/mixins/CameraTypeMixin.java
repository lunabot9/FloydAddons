package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.camera.FloydCamera;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CameraType.class)
public class CameraTypeMixin {
    @Inject(method = "cycle", at = @At("HEAD"), cancellable = true)
    private void floydaddons$skipDisabledCameraTypes(CallbackInfoReturnable<CameraType> cir) {
        FloydCamera.onCameraCycle();
        if (!FloydCamera.shouldDisableFrontCamera() && !FloydCamera.shouldDisableBackCamera()) return;

        cir.setReturnValue(FloydCamera.nextCameraTypeAfter(
                (CameraType) (Object) this,
                FloydCamera.shouldDisableFrontCamera(),
                FloydCamera.shouldDisableBackCamera()
        ));
    }
}
