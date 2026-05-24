package com.odtheking.mixin.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.odtheking.odin.features.impl.hiders.FloydHiders;
import com.odtheking.odin.features.impl.render.FloydHud;
import com.odtheking.odin.features.impl.render.FloydRender;
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
        if (FloydHiders.shouldDisableHungerBar()) {
            FloydHiders.recordHungerBar();
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void cancelEffectOverlay(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (FloydHiders.shouldHidePotionEffects()) {
            FloydHiders.recordPotionEffects();
            ci.cancel();
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void floydaddons$cancelVanillaScoreboard(GuiGraphics guiGraphics, Objective objective, CallbackInfo ci) {
        if (FloydRender.shouldUseCustomScoreboard()) {
            FloydHud.markVanillaScoreboardWouldRender();
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean floydaddons$thirdPersonCrosshair(boolean original) {
        if (!original && FloydHiders.shouldShowThirdPersonCrosshair()) {
            FloydHiders.recordThirdPersonCrosshair();
            return true;
        }
        return original;
    }

}
