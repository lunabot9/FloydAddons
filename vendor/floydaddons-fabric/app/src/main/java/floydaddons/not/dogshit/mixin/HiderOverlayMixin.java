package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels fire overlay when the corresponding hider is enabled.
 */
@Mixin(InGameOverlayRenderer.class)
public class HiderOverlayMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void floydaddons$removeFireOverlay(CallbackInfo ci) {
        if (HidersConfig.isRemoveFireOverlayEnabled()) ci.cancel();
    }
}
