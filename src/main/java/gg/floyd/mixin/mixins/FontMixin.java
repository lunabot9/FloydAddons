package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.render.CustomNameReplacer;
import gg.floyd.features.impl.render.FloydFont;
import gg.floyd.utils.ChatChroma;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public class FontMixin {

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
    private String onPrepareTextString(String text) {
        if (!CustomNameReplacer.isEnabled()) return text;
        return CustomNameReplacer.replaceStringIfNeeded(text);
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence onPrepareTextSequence(FormattedCharSequence text) {
        if (CustomNameReplacer.isEnabled()) {
            text = CustomNameReplacer.replaceSequenceIfNeeded(text);
        }
        return ChatChroma.INSTANCE.transform(text);
    }

    // "Disable Text Shadow": force the dropShadow flag (the first boolean arg of each prepareText overload)
    // to false for every rendered string/sequence, so all text — vanilla HUD and Floyd panels alike —
    // draws flat with no 1px shadow. Both drawInBatch paths funnel through prepareText, so this one hook
    // covers everything.
    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean floyd$noShadowString(boolean dropShadow) {
        return dropShadow && !FloydFont.isTextShadowDisabled();
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean floyd$noShadowSequence(boolean dropShadow) {
        return dropShadow && !FloydFont.isTextShadowDisabled();
    }

    @ModifyVariable(method = "drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence onDrawOutlineSequence(FormattedCharSequence text) {
        if (!CustomNameReplacer.isEnabled()) return text;
        return CustomNameReplacer.replaceSequenceIfNeeded(text);
    }

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String onWidthString(String text) {
        if (!CustomNameReplacer.isEnabled()) return text;
        return CustomNameReplacer.replaceStringIfNeeded(text);
    }

    @ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedText onWidthFormattedText(FormattedText text) {
        if (!CustomNameReplacer.isEnabled()) return text;
        if (text instanceof Component component) {
            Component replaced = CustomNameReplacer.replaceComponentIfNeeded(component);
            if (replaced != null) return replaced;
        }
        return text;
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence onWidthSequence(FormattedCharSequence text) {
        if (!CustomNameReplacer.isEnabled()) return text;
        return CustomNameReplacer.replaceSequenceIfNeeded(text);
    }
}
