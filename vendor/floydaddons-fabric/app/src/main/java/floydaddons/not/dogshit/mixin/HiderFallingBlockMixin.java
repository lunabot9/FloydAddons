package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.client.render.entity.FallingBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents falling block entities from rendering when RemoveFallingBlocks is enabled.
 */
@Mixin(FallingBlockEntityRenderer.class)
public class HiderFallingBlockMixin {

    @Inject(method = "render*", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$removeFallingBlocks(CallbackInfo ci) {
        if (HidersConfig.isRemoveFallingBlocksEnabled()) ci.cancel();
    }
}
