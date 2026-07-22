package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydCompatibility;
import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import gg.floyd.utils.ChromaCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class FloydTitleScreenBackgroundMixin {
    @Shadow private SplashRenderer splash;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void floydaddons$useChromaSplash(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int rgb = ChromaCache.INSTANCE.rgbFor(0.0f);
        Component text = Component.literal("Floyd").withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
        this.splash = new SplashRenderer(text);
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void floydaddons$renderCustomBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!FloydCompatibility.shouldUseCustomMainMenu()) return;
        if (FloydMenuScreenStyling.renderBackground(guiGraphics)) ci.cancel();
    }
}
