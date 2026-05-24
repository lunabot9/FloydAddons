package floydaddons.not.dogshit.mixin;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spoofs the client brand so servers think we are vanilla.
 */
@Mixin(ClientBrandRetriever.class)
public class BrandSpoofMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void floydaddons$spoofBrand(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("vanilla");
    }
}
