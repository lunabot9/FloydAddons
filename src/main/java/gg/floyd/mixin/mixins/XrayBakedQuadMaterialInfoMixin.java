package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydXray;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.resources.model.geometry.BakedQuad$MaterialInfo", remap = false)
public class XrayBakedQuadMaterialInfoMixin {
    @Inject(method = "layer", at = @At("HEAD"), cancellable = true)
    private void floydaddons$forceTranslucentQuadLayer(CallbackInfoReturnable<ChunkSectionLayer> cir) {
        if (FloydXray.shouldForceTranslucentLayer()) {
            cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
        }
    }
}
