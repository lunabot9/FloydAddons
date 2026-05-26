package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.render.FloydXray;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemBlockRenderTypes.class)
public class XrayRenderLayersMixin {
    @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true)
    private static void floydaddons$forceTranslucentBlocks(BlockState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
        if (FloydXray.isActive() && !FloydXray.isOpaque(state)) {
            cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
        }
    }

    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private static void floydaddons$forceTranslucentFluids(FluidState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
        if (FloydXray.isActive()) {
            cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
        }
    }
}
