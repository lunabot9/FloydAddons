package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.render.FloydXray;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public class XrayOcclusionMixin {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void floydaddons$disableOcclusion(BlockPos pos, CallbackInfo ci) {
        if (FloydXray.isActive()) {
            ci.cancel();
        }
    }
}
