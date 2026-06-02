package gg.floyd.mixin.mixins;

import gg.floyd.utils.ui.rendering.SmoothFloydText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public class GuiGraphicsSmoothTextMixin {
    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void floydaddons$smoothString(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (SmoothFloydText.drawString((GuiGraphics) (Object) this, text, x, y, color, shadow)) ci.cancel();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void floydaddons$smoothComponent(Font font, Component text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (SmoothFloydText.drawString((GuiGraphics) (Object) this, text == null ? null : text.getString(), x, y, color, shadow)) ci.cancel();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void floydaddons$smoothFormatted(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (SmoothFloydText.drawString((GuiGraphics) (Object) this, formattedToString(text), x, y, color, shadow)) ci.cancel();
    }

    @Inject(method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", at = @At("HEAD"), cancellable = true)
    private void floydaddons$smoothCenteredString(Font font, String text, int x, int y, int color, CallbackInfo ci) {
        int left = Math.round(x - SmoothFloydText.textWidth(text) / 2f);
        if (SmoothFloydText.drawString((GuiGraphics) (Object) this, text, left, y, color, true)) ci.cancel();
    }

    @Inject(method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"), cancellable = true)
    private void floydaddons$smoothCenteredComponent(Font font, Component text, int x, int y, int color, CallbackInfo ci) {
        String value = text == null ? null : text.getString();
        int left = Math.round(x - SmoothFloydText.textWidth(value) / 2f);
        if (SmoothFloydText.drawString((GuiGraphics) (Object) this, value, left, y, color, true)) ci.cancel();
    }

    @Inject(method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V", at = @At("HEAD"), cancellable = true)
    private void floydaddons$smoothCenteredFormatted(Font font, FormattedCharSequence text, int x, int y, int color, CallbackInfo ci) {
        String value = formattedToString(text);
        int left = Math.round(x - SmoothFloydText.textWidth(value) / 2f);
        if (SmoothFloydText.drawString((GuiGraphics) (Object) this, value, left, y, color, true)) ci.cancel();
    }

    private static String formattedToString(FormattedCharSequence text) {
        if (text == null) return null;
        StringBuilder builder = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }
}
