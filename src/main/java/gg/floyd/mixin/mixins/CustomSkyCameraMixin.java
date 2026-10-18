package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import gg.floyd.features.impl.render.FloydCustomSky;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class CustomSkyCameraMixin {
    @Inject(method = "addSkyPass", at = @At("HEAD"))
    private void floyd$captureSkyCamera(FrameGraphBuilder graph, CameraRenderState camera, GpuBufferSlice fog, CallbackInfo ci) {
        FloydCustomSky.beginFrame(camera, camera.viewRotationMatrix);
    }

    @ModifyArg(method = "addSkyPass", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"), index = 0)
    private Runnable floyd$replaceSkyPass(Runnable originalSky) {
        // Replace the complete pass, including resource-pack layers injected by sky mods.
        // Running inside a SkyRenderer draw leaves their later cube passes free to overwrite us.
        return () -> {
            if (!FloydCustomSky.renderSky()) originalSky.run();
        };
    }
}
