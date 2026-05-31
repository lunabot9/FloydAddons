package gg.floyd.mixin.mixins;

import com.mojang.blaze3d.platform.NativeImage;
import gg.floyd.FloydAddonsMod;
import gg.floyd.features.impl.misc.FloydCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(TitleScreen.class)
public class FloydTitleScreenBackgroundMixin {
    @Unique private static Identifier floydaddons$customBgId;
    @Unique private static boolean floydaddons$triedLoad;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void floydaddons$renderCustomBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!FloydCompatibility.shouldUseCustomMainMenu()) return;
        if (!ensureLoaded()) return;

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, floydaddons$customBgId, 0, 0, 0.0f, 0.0f, width, height, width, height);
        ci.cancel();
    }

    @Unique
    private static boolean ensureLoaded() {
        if (floydaddons$customBgId != null) return true;
        if (floydaddons$triedLoad) return false;
        floydaddons$triedLoad = true;

        Path path = FloydCompatibility.configPath("mainmenu.png");
        if (!Files.exists(path)) return false;

        try {
            NativeImage image = NativeImage.read(Files.newInputStream(path));
            DynamicTexture texture = new DynamicTexture(() -> "floydaddons_custom_mainmenu", image);
            Identifier id = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "custom_mainmenu");
            Minecraft.getInstance().getTextureManager().register(id, texture);
            floydaddons$customBgId = id;
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
