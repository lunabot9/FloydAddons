package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class FloydMenuSelectionListMixin {

    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void floydaddons$suppressVanillaListBackground(GuiGraphicsExtractor context, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressListBackground(screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
    private void floydaddons$suppressVanillaListSeparators(GuiGraphicsExtractor context, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressListBackground(screen)) {
            ci.cancel();
        }
    }
}
