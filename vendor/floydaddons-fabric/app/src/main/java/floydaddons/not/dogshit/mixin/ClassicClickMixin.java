package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.AnimationConfig;
import net.minecraft.client.option.StickyKeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BooleanSupplier;

/**
 * Forces all StickyKeyBindings (attack + use) into hold mode when
 * "Classic Click" is enabled, disabling toggle behavior.
 * This makes clicking behave like 1.8.9: one press = one action.
 */
@Mixin(StickyKeyBinding.class)
public class ClassicClickMixin {

    @Redirect(method = "setPressed",
              at = @At(value = "INVOKE",
                       target = "Ljava/util/function/BooleanSupplier;getAsBoolean()Z"),
              require = 0)
    private boolean floydaddons$forceHoldMode(BooleanSupplier supplier) {
        if (AnimationConfig.isEnabled() && AnimationConfig.isClassicClick()) {
            return false; // Force hold (non-toggle) behavior
        }
        return supplier.getAsBoolean();
    }
}
