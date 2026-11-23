package gg.floyd.mixin.mixins;

import gg.floyd.events.ScreenEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (new ScreenEvent.Render((Screen) (Object) this, context, mouseX, mouseY).postAndCatch()) ci.cancel();
    }
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void afterRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        new ScreenEvent.RenderAfter((Screen) (Object) this, context, mouseX, mouseY).postAndCatch();
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
    private void beforeTooltips(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        new ScreenEvent.RenderOverlay((Screen) (Object) this, context, mouseX, mouseY).postAndCatch();
    }
}
