package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import gg.floyd.utils.render.WorldToScreen;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the live world-render matrices so screen-space overlays (e.g. Player ESP overhead
 * icons/health) can project world positions exactly where the GPU renders them. Capturing the
 * actual renderLevel parameters (rather than reconstructing the projection) keeps the overlay
 * locked to the players' heads in 3D. require=0 so a future mapping change degrades gracefully
 * (overlay simply stops rendering) instead of crashing.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void floydaddons$captureRenderMatrices(
        GraphicsResourceAllocator allocator,
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        Matrix4f m1,
        Matrix4f m2,
        Matrix4f m3,
        GpuBufferSlice fogBuffer,
        Vector4f fogColor,
        boolean bl,
        CallbackInfo ci
    ) {
        WorldToScreen.capture(m1, m2, m3, camera.position());
    }
}
