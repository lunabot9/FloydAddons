package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides fire rendering on entities by overriding doesRenderOnFire().
 */
@Mixin(Entity.class)
public class HiderFireEntityMixin {

    @Inject(method = "doesRenderOnFire", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$hideEntityFire(CallbackInfoReturnable<Boolean> cir) {
        if (HidersConfig.isHideEntityFireEnabled()) cir.setReturnValue(false);
    }
}
