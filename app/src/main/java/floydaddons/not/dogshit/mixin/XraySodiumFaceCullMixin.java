package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.RenderConfig;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides face culling for xray-opaque blocks (glass, etc.) so they
 * display all faces as if floating in air, except between two blocks of
 * the same type which are still culled.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext")
public class XraySodiumFaceCullMixin {
    @Shadow(remap = false) protected BlockState state;
    @Shadow(remap = false) protected BlockPos pos;
    @Shadow(remap = false) protected BlockRenderView level;

    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void floydaddons$xrayOpaqueBlockCulling(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!RenderConfig.isXrayEnabled() || direction == null) return;
        if (!RenderConfig.isXrayOpaque(state)) return;

        BlockState neighbor = level.getBlockState(pos.offset(direction));
        // Only cull faces between two identical block types
        cir.setReturnValue(neighbor.getBlock() == state.getBlock());
    }
}
