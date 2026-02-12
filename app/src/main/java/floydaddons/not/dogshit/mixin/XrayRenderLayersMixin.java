package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderLayers.class)
public class XrayRenderLayersMixin {
    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void floydaddons$forceTranslucent(BlockState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (RenderConfig.isXrayEnabled() && !RenderConfig.isXrayOpaque(state)) {
            cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
        }
    }

    @Inject(method = "getFluidLayer", at = @At("HEAD"), cancellable = true)
    private static void floydaddons$forceFluidTranslucent(FluidState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (RenderConfig.isXrayEnabled()) {
            cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
        }
    }
}
