package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydBlockSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps {@link FloydBlockSearch}'s per-chunk match index live: every client-side block change
 * patches the index in O(1) instead of forcing a rescan. require=0 so a mapping change degrades to
 * "edits refresh on chunk reload" rather than crashing; the chunk-load indexing is unaffected.
 */
@Mixin(LevelChunk.class)
public class BlockSearchChunkMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"), require = 0)
    private void floydaddons$indexBlockSearch(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getLevel().isClientSide()) {
            FloydBlockSearch.handleClientBlockChange(pos, state);
        }
    }
}
