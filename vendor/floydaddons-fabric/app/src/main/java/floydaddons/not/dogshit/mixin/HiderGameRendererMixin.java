package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels hurt camera shake when NoHurtCamera is enabled.
 */
@Mixin(GameRenderer.class)
public class HiderGameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$noHurtCamera(CallbackInfo ci) {
        if (HidersConfig.isNoHurtCameraEnabled()) ci.cancel();
    }
}
