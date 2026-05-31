package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydXray;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class XraySodiumAlphaMixin {
    @Unique
    private BlockState floydaddons$currentState;

    @Inject(method = "renderModel", at = @At("HEAD"), require = 0)
    private void floydaddons$captureState(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        this.floydaddons$currentState = state;
    }

    @Redirect(
        method = "bufferQuad",
        at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/api/util/ColorARGB;toABGR(I)I"),
        require = 0
    )
    private int floydaddons$modifyVertexColor(int argb) {
        BlockState state = this.floydaddons$currentState;
        if (FloydXray.isActive() && (state == null || !FloydXray.isOpaque(state))) {
            argb = (FloydXray.alpha() << 24) | (argb & 0x00FFFFFF);
        }
        return toAbgr(argb);
    }

    @Unique
    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16);
    }
}
