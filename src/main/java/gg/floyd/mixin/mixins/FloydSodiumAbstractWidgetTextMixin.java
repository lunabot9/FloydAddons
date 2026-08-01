package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget", remap = false)
public abstract class FloydSodiumAbstractWidgetTextMixin {

    @Inject(method = "drawString(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/lang/String;III)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$queueSodiumStringText(
            GuiGraphicsExtractor context,
            String text,
            int x,
            int y,
            int color,
            CallbackInfo ci) {
        if (!FloydMenuScreenStyling.shouldUseQueuedCustomText(Minecraft.getInstance().screen)) return;
        FloydMenuScreenStyling.queueText(Minecraft.getInstance().screen, text, x, y, color, 9f);
        ci.cancel();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void floydaddons$queueSodiumComponentText(
            GuiGraphicsExtractor context,
            Component text,
            int x,
            int y,
            int color,
            CallbackInfo ci) {
        if (!FloydMenuScreenStyling.shouldUseQueuedCustomText(Minecraft.getInstance().screen)) return;
        FloydMenuScreenStyling.queueText(Minecraft.getInstance().screen, text.getString(), x, y, color, 9f);
        ci.cancel();
    }
}
