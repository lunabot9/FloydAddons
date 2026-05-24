package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.render.FloydXray;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext", remap = false)
public class XrayIndigoAlphaMixin {
    @Inject(
        method = "bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void floydaddons$modifyIndigoAlpha(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (!FloydXray.isActive()) return;

        int alpha = FloydXray.alpha();
        for (int i = 0; i < 4; i++) {
            int color = quad.color(i);
            quad.color(i, (alpha << 24) | (color & 0x00FFFFFF));
        }
    }
}
