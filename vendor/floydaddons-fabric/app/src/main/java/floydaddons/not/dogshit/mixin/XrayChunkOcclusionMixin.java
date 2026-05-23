package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkOcclusionDataBuilder.class)
public class XrayChunkOcclusionMixin {
    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
    private void floydaddons$disableOcclusion(BlockPos pos, CallbackInfo ci) {
        if (RenderConfig.isXrayEnabled()) {
            ci.cancel();
        }
    }
}
