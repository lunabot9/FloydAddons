package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuFontStyling;
import gg.floyd.mixin.accessors.MinecraftFontAccessor;
import gg.floyd.mixin.accessors.ScreenFontAccessor;
import gg.floyd.utils.font.FloydFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class FloydMenuFontMixin {

    @Unique
    private Font floydaddons$previousMinecraftFont;

    @Unique
    private Font floydaddons$previousScreenFont;

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void floydaddons$useVanillaFontForWrappedMenus(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!FloydMenuFontStyling.shouldUseVanillaFont(screen)) return;

        Font menuFont = FloydFonts.INSTANCE.getPanelCustom();
        MinecraftFontAccessor minecraft = (MinecraftFontAccessor) Minecraft.getInstance();
        ScreenFontAccessor self = (ScreenFontAccessor) screen;
        floydaddons$previousMinecraftFont = minecraft.floydaddons$getFont();
        floydaddons$previousScreenFont = self.floydaddons$getFont();
        minecraft.floydaddons$setFont(menuFont);
        self.floydaddons$setFont(menuFont);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void floydaddons$restoreFontAfterWrappedMenus(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
        float partialTick,
        CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (floydaddons$previousMinecraftFont == null || floydaddons$previousScreenFont == null) return;
        if (!FloydMenuFontStyling.shouldUseVanillaFont(screen)) {
            floydaddons$previousMinecraftFont = null;
            floydaddons$previousScreenFont = null;
            return;
        }

        ((MinecraftFontAccessor) Minecraft.getInstance()).floydaddons$setFont(floydaddons$previousMinecraftFont);
        ((ScreenFontAccessor) screen).floydaddons$setFont(floydaddons$previousScreenFont);
        floydaddons$previousMinecraftFont = null;
        floydaddons$previousScreenFont = null;
    }
}
