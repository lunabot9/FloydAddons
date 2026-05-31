package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydXray;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public class XrayLightTextureMixin {
    @Redirect(
        method = "updateLightTexture",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 2)
    )
    private Object floydaddons$fullBrightGamma(OptionInstance<?> instance) {
        return FloydXray.isActive() ? 15.0 : instance.get();
    }
}
