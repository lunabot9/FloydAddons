package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.pvp.FloydAutoClicker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftAutoClickerMixin {
    @Shadow private int missTime;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void floydaddons$prepareAutoClicker(CallbackInfo ci) {
        FloydAutoClicker.beforeVanillaKeybinds();
    }

    @Inject(method = "handleKeybinds", at = @At("RETURN"))
    private void floydaddons$runAutoClicker(CallbackInfo ci) {
        FloydAutoClicker.afterVanillaKeybinds();
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void floydaddons$removeMissCooldownForAutoClicker(CallbackInfoReturnable<Boolean> cir) {
        if (FloydAutoClicker.shouldBypassMissCooldown() && missTime > 0) {
            missTime = 0;
        }
    }
}
