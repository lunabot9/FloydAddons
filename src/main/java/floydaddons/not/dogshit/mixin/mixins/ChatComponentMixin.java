package floydaddons.not.dogshit.mixin.mixins;

import floydaddons.not.dogshit.client.utils.ChatChroma;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"))
    private void floydaddons$beginChatChroma(GuiGraphics guiGraphics, Font font, int currentTick, int mouseX, int mouseY, boolean focused, boolean drawingIndicator, CallbackInfo ci) {
        ChatChroma.INSTANCE.beginRender();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("RETURN"))
    private void floydaddons$endChatChroma(GuiGraphics guiGraphics, Font font, int currentTick, int mouseX, int mouseY, boolean focused, boolean drawingIndicator, CallbackInfo ci) {
        ChatChroma.INSTANCE.endRender();
    }
}
