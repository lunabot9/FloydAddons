package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public abstract class FloydMenuOnlineServerEntryMixin {

    @Redirect(
            method = "extractContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
            )
    )
    private void floydaddons$renderServerNameWithNvg(
            GuiGraphicsExtractor context,
            Font font,
            String text,
            int x,
            int y,
            int color) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressWidgetFont(screen)) {
            FloydMenuScreenStyling.queueText(screen, text, x, y, color, 9f);
            return;
        }
        context.text(font, text, x, y, color);
    }

    @Redirect(
            method = "extractContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            )
    )
    private void floydaddons$renderServerStatusWithNvg(
            GuiGraphicsExtractor context,
            Font font,
            Component text,
            int x,
            int y,
            int color) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressWidgetFont(screen)) {
            FloydMenuScreenStyling.queueText(screen, text.getString(), x, y, color, 9f);
            return;
        }
        context.text(font, text, x, y, color);
    }
}
