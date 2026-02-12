package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modifies per-vertex alpha in Fabric's Indigo terrain renderer.
 * Targets the 1-arg bufferQuad on AbstractTerrainRenderContext, which is the
 * override that handles ALL terrain block quads during chunk building.
 * <p>
 * Injecting at HEAD sets alpha before tinting/shading. Both operations
 * approximately preserve the alpha channel (shading preserves it exactly,
 * tinting preserves it via multiply with 0xFF alpha tint colors).
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext")
public class XrayIndigoMixin {
    @Inject(method = "bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void floydaddons$modifyAlpha(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (RenderConfig.isXrayEnabled()) {
            int alphaInt = (int) (RenderConfig.getXrayOpacity() * 255);
            for (int i = 0; i < 4; i++) {
                int color = quad.color(i);
                quad.color(i, (alphaInt << 24) | (color & 0x00FFFFFF));
            }
        }
    }
}
