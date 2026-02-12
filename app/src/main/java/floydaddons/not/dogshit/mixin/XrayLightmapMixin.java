package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightmapTextureManager.class)
public class XrayLightmapMixin {
    @Redirect(method = "update",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
                       ordinal = 0))
    private Object floydaddons$overrideGamma(SimpleOption<?> instance) {
        if (RenderConfig.isXrayEnabled()) {
            return 15.0;
        }
        return instance.getValue();
    }
}
