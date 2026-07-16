package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.camera.FloydCamera;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {
    //? if >=26.2 {
    /*@Inject(method = "update", at = @At("HEAD"))
    private void floydaddons$disableSmartCullForFreecam(
        net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
        int viewDistance,
        net.minecraft.client.renderer.state.level.ChunkLoadingRenderState chunkLoadingState,
        CallbackInfo ci
    ) {
        if (FloydCamera.freecamActive()) {
            cameraState.smartCull = false;
        }
    }
    *///?} else {
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean floydaddons$disableSmartCullForFreecam(boolean smartCull) {
        if (FloydCamera.freecamActive()) {
            return false;
        }
        return smartCull;
    }
    //?}
}
