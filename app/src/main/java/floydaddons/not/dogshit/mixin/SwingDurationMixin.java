package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.AnimationConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom swing duration:
 * - Override getHandSwingDuration() so vanilla's own system uses our duration
 * - Prevent swingHand from restarting mid-animation so the swing always finishes
 * - Attacks still register (doAttack runs before swingHand is called)
 */
@Mixin(LivingEntity.class)
public abstract class SwingDurationMixin {

    @Shadow public boolean handSwinging;

    /**
     * Make vanilla use our custom swing duration instead of the default 6.
     * This controls how many ticks the full swing animation takes.
     */
    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$customDuration(CallbackInfoReturnable<Integer> cir) {
        if (!AnimationConfig.isEnabled()) return;
        if (!((Object) this instanceof ClientPlayerEntity)) return;
        int duration = AnimationConfig.getSwingDuration();
        if (duration != 6) {
            cir.setReturnValue(duration);
        }
    }

    /**
     * Prevent swingHand from restarting the animation while already swinging.
     * This ensures the animation always plays through its full cycle.
     * Attack registration happens in doAttack() BEFORE swingHand is called,
     * so canceling here is purely visual — clicks still register as attacks.
     */
    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$preventRestart(Hand hand, CallbackInfo ci) {
        if (!AnimationConfig.isEnabled()) return;
        if (!((Object) this instanceof ClientPlayerEntity)) return;
        if (AnimationConfig.getSwingDuration() == 6) return;
        if (handSwinging) {
            ci.cancel();
        }
    }
}
