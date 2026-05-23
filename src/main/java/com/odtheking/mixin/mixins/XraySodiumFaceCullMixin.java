package com.odtheking.mixin.mixins;

import com.odtheking.odin.features.impl.render.FloydXray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public class XraySodiumFaceCullMixin {
    @Shadow protected BlockState state;
    @Shadow protected BlockPos pos;
    @Shadow protected BlockAndTintGetter level;

    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$xrayOpaqueBlockCulling(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!FloydXray.isActive() || direction == null || !FloydXray.isOpaque(state)) return;
        BlockState neighbor = level.getBlockState(pos.relative(direction));
        cir.setReturnValue(neighbor.getBlock() == state.getBlock());
    }
}
