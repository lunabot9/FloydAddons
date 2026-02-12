package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modifies per-vertex alpha in Sodium's block renderer to make blocks
 * transparent when xray is active. Blocks in the xray-opaque list keep
 * their original rendering.
 * <p>
 * Captures the current BlockState via an inject on renderModel(), then
 * redirects the ColorARGB.toABGR() call inside BlockRenderer.bufferQuad().
 * Setting alpha &lt; 255 also naturally prevents Sodium's attemptPassDowngrade()
 * from reverting blocks back to the SOLID pass.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer")
public abstract class XraySodiumAlphaMixin {
    @Unique
    private BlockState floydaddons$currentState;

    @Inject(method = "renderModel", at = @At("HEAD"), remap = false, require = 0)
    private void floydaddons$captureState(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        this.floydaddons$currentState = state;
    }

    @Redirect(
        method = "bufferQuad",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/api/util/ColorARGB;toABGR(I)I"
        ),
        remap = false,
        require = 0
    )
    private int floydaddons$modifyVertexColor(int argb) {
        BlockState state = this.floydaddons$currentState;
        if (RenderConfig.isXrayEnabled() && (state == null || !RenderConfig.isXrayOpaque(state))) {
            int alpha = (int) (RenderConfig.getXrayOpacity() * 255);
            argb = (alpha << 24) | (argb & 0x00FFFFFF);
        }
        // ARGB → ABGR: swap R and B channels, keep A and G
        return (argb & 0xFF00FF00)        |  // A and G unchanged
               ((argb & 0x00FF0000) >> 16) |  // R → B position
               ((argb & 0x000000FF) << 16);   // B → R position
    }
}
