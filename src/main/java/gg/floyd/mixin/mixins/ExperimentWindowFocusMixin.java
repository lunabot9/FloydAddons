package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.platform.Window;
import gg.floyd.events.WindowFocusEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class ExperimentWindowFocusMixin {
    @Inject(method = "onFocus", at = @At("TAIL"))
    private void floyd$experimentFocus(long window, boolean focused, CallbackInfo ci) {
        new WindowFocusEvent(focused).postAndCatch();
    }
}
