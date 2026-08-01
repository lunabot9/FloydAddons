package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.WorldSelectionList$WorldListEntry")
public abstract class FloydMenuWorldListEntryMixin {

    @Redirect(
            method = "extractContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/StringWidget;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    ordinal = 0
            )
    )
    private void floydaddons$renderWorldNameWithNvg(
            StringWidget widget,
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressWidgetFont(screen)) {
            FloydMenuScreenStyling.queueText(screen, widget.getMessage().getString(), widget.getX(), widget.getY(), 0xE6ECECEC, 9f);
            return;
        }
        widget.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    @Redirect(
            method = "extractContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/StringWidget;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    ordinal = 1
            )
    )
    private void floydaddons$renderWorldMetaWithNvg(
            StringWidget widget,
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressWidgetFont(screen)) {
            FloydMenuScreenStyling.queueText(screen, widget.getMessage().getString(), widget.getX(), widget.getY(), 0xFFA8A8A8, 9f);
            return;
        }
        widget.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    @Redirect(
            method = "extractContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/StringWidget;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    ordinal = 2
            )
    )
    private void floydaddons$renderWorldInfoWithNvg(
            StringWidget widget,
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressWidgetFont(screen)) {
            FloydMenuScreenStyling.queueText(screen, widget.getMessage().getString(), widget.getX(), widget.getY(), 0xFFA8A8A8, 9f);
            return;
        }
        widget.extractRenderState(context, mouseX, mouseY, partialTick);
    }
}
