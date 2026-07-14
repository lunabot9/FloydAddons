package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydXray;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.block.FluidModel", remap = false)
public class XrayFluidModelMixin {
    @Inject(method = "layer", at = @At("HEAD"), cancellable = true)
    private void floydaddons$forceTranslucentFluidLayer(CallbackInfoReturnable<ChunkSectionLayer> cir) {
        if (FloydXray.isActive()) {
            cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
        }
    }
}
