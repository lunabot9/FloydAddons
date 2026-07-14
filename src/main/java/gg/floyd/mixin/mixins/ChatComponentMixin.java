package gg.floyd.mixin.mixins;

import gg.floyd.utils.ChatChroma;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the chat-render window so {@link ChatChroma#transform} only animates chroma on chat text
 * (the "FloydAddons" prefix, or the whole line when Full Chat Chroma is on), not all UI text.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void floydaddons$beginChatChroma(GuiGraphicsExtractor guiGraphics, Font font, int currentTick, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean focused, CallbackInfo ci) {
        ChatChroma.INSTANCE.beginRender();
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void floydaddons$endChatChroma(GuiGraphicsExtractor guiGraphics, Font font, int currentTick, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean focused, CallbackInfo ci) {
        ChatChroma.INSTANCE.endRender();
    }
}
