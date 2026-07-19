package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.cosmetic.VanillaMobPlayerModel;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @SuppressWarnings("unchecked")
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void floydaddons$useSelectedPlayerModelInPreview(
        LivingEntity entity,
        CallbackInfoReturnable<EntityRenderState> cir
    ) {
        if (!(entity instanceof AbstractClientPlayer player)) return;

        LivingEntity previewEntity = VanillaMobPlayerModel.previewEntityFor(player);
        if (previewEntity == player) return;

        EntityRenderDispatcher dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, EntityRenderState> renderer =
            (EntityRenderer<? super LivingEntity, EntityRenderState>) dispatcher.getRenderer(previewEntity);
        EntityRenderState state = renderer.createRenderState(previewEntity, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        VanillaMobPlayerModel.onEntityStateExtracted(previewEntity, state);
        cir.setReturnValue(state);
    }
}
