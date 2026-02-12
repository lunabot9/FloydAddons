package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.CameraConfig;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class CameraClientMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$blockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CameraConfig.isFreecamEnabled()) cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$blockItemUse(CallbackInfo ci) {
        if (CameraConfig.isFreecamEnabled()) ci.cancel();
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$blockBreaking(boolean breaking, CallbackInfo ci) {
        if (CameraConfig.isFreecamEnabled()) ci.cancel();
    }

    @Inject(method = "onDisconnected", at = @At("HEAD"), require = 0)
    private void floydaddons$onDisconnect(CallbackInfo ci) {
        if (CameraConfig.isFreecamEnabled()) CameraConfig.toggleFreecam();
        if (CameraConfig.isFreelookEnabled()) CameraConfig.toggleFreelook();
    }
}
