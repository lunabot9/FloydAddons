package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Modifies per-vertex alpha in Sodium's fluid renderer (water, lava)
 * when xray is active. Redirects the ColorARGB.toABGR() call inside
 * DefaultFluidRenderer.updateQuad().
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer")
public class XraySodiumFluidAlphaMixin {
    @Redirect(
        method = "updateQuad",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/api/util/ColorARGB;toABGR(I)I"
        ),
        remap = false,
        require = 0
    )
    private int floydaddons$modifyFluidColor(int argb) {
        if (RenderConfig.isXrayEnabled()) {
            int alpha = (int) (RenderConfig.getXrayOpacity() * 255);
            argb = (alpha << 24) | (argb & 0x00FFFFFF);
        }
        // ARGB → ABGR: swap R and B channels, keep A and G
        return (argb & 0xFF00FF00)        |
               ((argb & 0x00FF0000) >> 16) |
               ((argb & 0x000000FF) << 16);
    }
}
