package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydXray;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class XrayRenderLayersMixin {
    @Inject(method = "forceOpaque", at = @At("HEAD"), cancellable = true)
    private static void floydaddons$keepNonOpaqueBlocksOutOfOpaquePass(boolean cutoutLeaves, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (FloydXray.isActive() && !FloydXray.isOpaque(state)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void floydaddons$trackTesselatedState(
        net.minecraft.client.renderer.block.BlockQuadOutput output,
        float x,
        float y,
        float z,
        net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos,
        BlockState state,
        net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
        long seed,
        CallbackInfo ci
    ) {
        FloydXray.beginBlockTessellation(state);
    }

    @Inject(method = "tesselateBlock", at = @At("RETURN"))
    private void floydaddons$clearTrackedTesselatedState(
        net.minecraft.client.renderer.block.BlockQuadOutput output,
        float x,
        float y,
        float z,
        net.minecraft.client.renderer.block.BlockAndTintGetter level,
        net.minecraft.core.BlockPos pos,
        BlockState state,
        net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
        long seed,
        CallbackInfo ci
    ) {
        FloydXray.endBlockTessellation();
    }
}
