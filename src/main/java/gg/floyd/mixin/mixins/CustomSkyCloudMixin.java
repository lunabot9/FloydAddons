package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydCustomSky;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class CustomSkyCloudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void floyd$hideVanillaClouds(CallbackInfo ci) {
        if (FloydCustomSky.replacesVanillaSky()) ci.cancel();
    }
}
