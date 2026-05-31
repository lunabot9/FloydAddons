package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydCompatibility;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class FloydBrandSpoofMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void floydaddons$spoofBrand(CallbackInfoReturnable<String> cir) {
        if (FloydCompatibility.shouldSpoofClientBrand()) cir.setReturnValue("vanilla");
    }
}
