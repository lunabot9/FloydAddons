package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.cosmetic.VanillaMobPlayerModel;
import net.minecraft.client.renderer.entity.CopperGolemRenderer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolemRenderer.class)
public abstract class CopperGolemRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/CopperGolemRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
    private void floydaddons$useMinionTexture(CopperGolemRenderState renderState, CallbackInfoReturnable<Identifier> cir) {
        Identifier texture = VanillaMobPlayerModel.minionTextureFor(renderState);
        if (texture != null) cir.setReturnValue(texture);
    }
}
