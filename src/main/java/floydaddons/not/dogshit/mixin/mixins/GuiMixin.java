package floydaddons.not.dogshit.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import floydaddons.not.dogshit.client.features.impl.hiders.FloydDisableHungerBar;
import floydaddons.not.dogshit.client.features.impl.hiders.FloydHidePotionEffects;
import floydaddons.not.dogshit.client.features.impl.hiders.FloydThirdPersonCrosshair;
import floydaddons.not.dogshit.client.features.impl.render.FloydHud;
import floydaddons.not.dogshit.client.features.impl.render.FloydCustomScoreboard;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void cancelFoodBar(GuiGraphics guiGraphics, Player player, int i, int j, CallbackInfo ci) {
        if (FloydDisableHungerBar.shouldDisableHungerBar()) {
            FloydDisableHungerBar.recordHungerBar();
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void cancelEffectOverlay(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (FloydHidePotionEffects.shouldHidePotionEffects()) {
            FloydHidePotionEffects.recordPotionEffects();
            ci.cancel();
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void floydaddons$cancelVanillaScoreboard(GuiGraphics guiGraphics, Objective objective, CallbackInfo ci) {
        if (FloydHud.shouldCancelVanillaScoreboard(FloydCustomScoreboard.shouldUseCustomScoreboard(), objective != null)) {
            FloydHud.markVanillaScoreboardWouldRender();
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean floydaddons$thirdPersonCrosshair(boolean original) {
        if (!original && FloydThirdPersonCrosshair.shouldShowThirdPersonCrosshair()) {
            FloydThirdPersonCrosshair.recordThirdPersonCrosshair();
            return true;
        }
        return original;
    }

}
