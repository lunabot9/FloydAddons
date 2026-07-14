package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import gg.floyd.utils.render.WorldToScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the live world-render matrices so screen-space overlays (e.g. Player ESP overhead
 * icons/health) can project world positions exactly where the GPU renders them. In 26.1.x the
 * world pass receives the camera rotation matrix directly while the bobbed projection has to be
 * reconstructed from CameraRenderState plus the bob transform captured in GameRendererMixin.
 * require=0 so a future mapping change degrades gracefully instead of crashing.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void floydaddons$captureRenderMatrices(
        GraphicsResourceAllocator allocator,
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        CameraRenderState cameraRenderState,
        Matrix4fc viewRotationMatrix,
        com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer,
        Vector4f fogColor,
        boolean renderSky,
        ChunkSectionsToRender chunkSectionsToRender,
        CallbackInfo ci
    ) {
        WorldToScreen.capture(cameraRenderState, viewRotationMatrix);
    }
}
