package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydAnimations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class AnimationLivingEntityMixin {
    @Shadow public boolean swinging;

    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void floydaddons$customSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if (!FloydAnimations.shouldApply()) return;
        if (!((Object) this instanceof LocalPlayer)) return;

        int duration = FloydAnimations.swingTicks();
        if (duration != 6) {
            FloydAnimations.recordCustomSwingDuration();
            cir.setReturnValue(duration);
        }
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void floydaddons$preventSwingRestart(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if (!FloydAnimations.shouldApply()) return;
        if (!((Object) this instanceof LocalPlayer)) return;
        if (FloydAnimations.swingTicks() == 6) return;
        if (swinging) {
            FloydAnimations.recordPreventedSwingRestart();
            ci.cancel();
        }
    }
}
