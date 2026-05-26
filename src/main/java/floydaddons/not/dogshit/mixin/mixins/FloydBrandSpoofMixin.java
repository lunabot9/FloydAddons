package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.misc.FloydSpoofClientBrand;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class FloydBrandSpoofMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void floydaddons$spoofBrand(CallbackInfoReturnable<String> cir) {
        if (FloydSpoofClientBrand.INSTANCE.getEnabled()) cir.setReturnValue("vanilla");
    }
}
