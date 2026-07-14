package gg.floyd.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gg.floyd.features.impl.hiders.FloydHiders;
import gg.floyd.features.impl.render.FloydCustomScoreboard;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    // NOTE: do NOT reset the vanilla-scoreboard signal in render() HEAD. The Floyd HUD pass races
    // vanilla's displayScoreboardSidebar within the frame; a per-frame reset there makes the HUD pass
    // read a stale false and the custom scoreboard never draws. The signal self-clears via
    // FloydCustomScoreboard.shouldDrawScoreboardHud()'s objective check when the sidebar disappears.

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void cancelFoodBar(GuiGraphicsExtractor guiGraphics, Player player, int i, int j, CallbackInfo ci) {
        if (FloydHiders.shouldDisableHungerBar()) {
            FloydHiders.recordHungerBar();
            ci.cancel();
        }
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void cancelEffectOverlay(GuiGraphicsExtractor guiGraphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (FloydHiders.shouldHidePotionEffects()) {
            FloydHiders.recordPotionEffects();
            ci.cancel();
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void floydaddons$cancelVanillaScoreboard(GuiGraphicsExtractor guiGraphics, Objective objective, CallbackInfo ci) {
        if (FloydCustomScoreboard.shouldUseCustomScoreboard()) {
            FloydCustomScoreboard.markVanillaScoreboardWouldRender();
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean floydaddons$thirdPersonCrosshair(boolean original) {
        if (!original && FloydHiders.shouldShowThirdPersonCrosshair()) {
            FloydHiders.recordThirdPersonCrosshair();
            return true;
        }
        return original;
    }

}
