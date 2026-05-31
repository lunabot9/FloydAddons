package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.floyd.features.impl.hiders.FloydHiders;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireOverlay(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
        if (FloydHiders.shouldRemoveFireOverlay()) {
            FloydHiders.recordFireOverlay();
            ci.cancel();
        }
    }
}
