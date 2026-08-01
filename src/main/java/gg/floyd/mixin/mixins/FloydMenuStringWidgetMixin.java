package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractStringWidget.class)
public abstract class FloydMenuStringWidgetMixin {

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void floydaddons$suppressVanillaMenuStringWidget(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressWidgetFont(screen)) {
            ci.cancel();
        }
    }
}
