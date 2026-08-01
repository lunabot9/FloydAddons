package gg.floyd.mixin.mixins;

import gg.floyd.features.impl.misc.FloydMenuScreenStyling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.screens.packs.TransferableSelectionList$PackEntry")
public abstract class FloydMenuPackListEntryMixin {

    @Redirect(
            method = "extractContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/StringWidget;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
            )
    )
    private void floydaddons$renderPackTitleWithNvg(
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
                    target = "Lnet/minecraft/client/gui/components/MultiLineTextWidget;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
            )
    )
    private void floydaddons$renderPackDescriptionWithNvg(
            MultiLineTextWidget widget,
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null && FloydMenuScreenStyling.shouldSuppressWidgetFont(screen)) {
            var lines = widget.getMessage().getString().split("\\R");
            int lineY = widget.getY();
            for (var line : lines) {
                if (!line.isBlank()) {
                    FloydMenuScreenStyling.queueText(screen, line, widget.getX(), lineY, 0xFFA8A8A8, 9f);
                }
                lineY += 9;
            }
            return;
        }
        widget.extractRenderState(context, mouseX, mouseY, partialTick);
    }
}
