package gg.floyd.mixin.mixins;

import gg.floyd.clickgui.FloydOneConfig;
import org.polyfrost.oneconfig.internal.ui.sound.UiSoundEvent;
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UiSounds.class, remap = false)
public abstract class FloydUiOpeningSoundMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void floyd$skipOpeningSound(UiSoundEvent event, CallbackInfo ci) {
        if (event == UiSoundEvent.OPEN) {
            FloydOneConfig.recordSuppressedOpeningSound();
            ci.cancel();
        } else if (event == UiSoundEvent.CLOSE) {
            FloydOneConfig.recordSuppressedClosingSound();
            ci.cancel();
        }
    }
}
