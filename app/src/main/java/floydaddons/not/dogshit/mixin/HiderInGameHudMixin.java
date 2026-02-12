package floydaddons.not.dogshit.mixin;

import floydaddons.not.dogshit.client.config.HidersConfig;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides potion effects, hunger bar, and allows third-person crosshair.
 */
@Mixin(InGameHud.class)
public class HiderInGameHudMixin {

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$hidePotionEffects(CallbackInfo ci) {
        if (HidersConfig.isHidePotionEffectsEnabled()) ci.cancel();
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$disableHungerBar(CallbackInfo ci) {
        if (HidersConfig.isDisableHungerBarEnabled()) ci.cancel();
    }

    @Redirect(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"), require = 0)
    private boolean floydaddons$thirdPersonCrosshair(Perspective perspective) {
        return HidersConfig.isThirdPersonCrosshairEnabled() || perspective.isFirstPerson();
    }
}
