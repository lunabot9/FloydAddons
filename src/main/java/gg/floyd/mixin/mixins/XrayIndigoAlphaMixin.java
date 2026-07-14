package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydXray;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public class XrayIndigoAlphaMixin {
    @Inject(
        method = "transform",
        at = @At("RETURN"),
        require = 0
    )
    private void floydaddons$modifyIndigoAlpha(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (!FloydXray.isActive()) return;

        int alpha = FloydXray.alpha();
        for (int i = 0; i < 4; i++) {
            int color = quad.color(i);
            quad.color(i, (alpha << 24) | (color & 0x00FFFFFF));
        }
    }
}
