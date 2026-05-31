package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.FloydAnimations;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BooleanSupplier;

@Mixin(ToggleKeyMapping.class)
public class AnimationToggleKeyMappingMixin {
    @Redirect(
        method = "setDown",
        at = @At(value = "INVOKE", target = "Ljava/util/function/BooleanSupplier;getAsBoolean()Z")
    )
    private boolean floydaddons$forceHoldMode(BooleanSupplier supplier) {
        if (FloydAnimations.shouldUseClassicClick()) {
            FloydAnimations.recordClassicClick();
            return false;
        }
        return supplier.getAsBoolean();
    }
}
