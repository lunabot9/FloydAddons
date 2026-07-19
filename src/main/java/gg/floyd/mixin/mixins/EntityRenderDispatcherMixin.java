package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.cosmetic.VanillaMobPlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void floydaddons$useVanillaMobPlayerModel(
        E entity,
        float partialTick,
        CallbackInfoReturnable<EntityRenderState> cir
    ) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        EntityRenderState replacement = VanillaMobPlayerModel.extract(
            (EntityRenderDispatcher) (Object) this,
            player,
            partialTick
        );
        if (replacement != null) cir.setReturnValue(replacement);
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> void floydaddons$markMinionPreviewStates(
        E entity,
        float partialTick,
        CallbackInfoReturnable<EntityRenderState> cir
    ) {
        VanillaMobPlayerModel.onEntityStateExtracted(entity, cir.getReturnValue());
    }
}
