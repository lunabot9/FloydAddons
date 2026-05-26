package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.features.impl.render.FloydAnimations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class AnimationLivingEntityMixin {
    @Unique private int floydaddons$swingRestartCooldown;

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
        if (floydaddons$swingRestartCooldown > 0) {
            FloydAnimations.recordPreventedSwingRestart();
            ci.cancel();
        }
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("RETURN"))
    private void floydaddons$markSwingStart(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if (!FloydAnimations.shouldApply()) {
            floydaddons$swingRestartCooldown = 0;
            return;
        }
        if (!((Object) this instanceof LocalPlayer)) {
            floydaddons$swingRestartCooldown = 0;
            return;
        }
        int duration = FloydAnimations.swingTicks();
        floydaddons$swingRestartCooldown = duration == 6 ? 0 : duration;
    }

    @Inject(method = "updateSwingTime", at = @At("TAIL"))
    private void floydaddons$tickSwingRestartCooldown(CallbackInfo ci) {
        if (!FloydAnimations.shouldApply()) {
            floydaddons$swingRestartCooldown = 0;
            return;
        }
        if (!((Object) this instanceof LocalPlayer)) {
            floydaddons$swingRestartCooldown = 0;
            return;
        }
        if (FloydAnimations.swingTicks() == 6) {
            floydaddons$swingRestartCooldown = 0;
            return;
        }
        if (floydaddons$swingRestartCooldown > 0) {
            floydaddons$swingRestartCooldown--;
        }
    }
}
