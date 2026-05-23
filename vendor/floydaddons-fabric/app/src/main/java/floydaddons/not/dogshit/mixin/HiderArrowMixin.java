package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.client.render.entity.feature.StuckArrowsFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides arrows stuck in player models by returning 0 from the arrow count.
 */
@Mixin(StuckArrowsFeatureRenderer.class)
public class HiderArrowMixin {

    @Inject(method = "getObjectCount", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$disableAttachedArrows(CallbackInfoReturnable<Integer> cir) {
        if (HidersConfig.isDisableAttachedArrowsEnabled()) cir.setReturnValue(0);
    }
}
