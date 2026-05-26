package com.odtheking.mixin.mixins;

import com.mojang.blaze3d.platform.NativeImage;
import com.odtheking.odin.FloydAddonsMod;
import com.odtheking.odin.features.impl.misc.FloydCompatibility;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(TitleScreen.class)
public class FloydTitleScreenBackgroundMixin {
    @Unique private static List<Identifier> floydaddons$frames;
    @Unique private static boolean floydaddons$triedLoad;
    @Unique private static final int FRAME_MS = 1000 / 24;
    @Unique private static final int MAX_FRAMES = 120;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void floydaddons$renderCustomBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!FloydCompatibility.shouldUseCustomMainMenu()) return;
        List<Identifier> frames = floydaddons$ensureLoaded();
        if (frames == null || frames.isEmpty()) return;

        int frameIndex = (int) ((System.currentTimeMillis() / FRAME_MS) % frames.size());
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, frames.get(frameIndex), 0, 0, 0.0f, 0.0f, width, height, width, height);
        ci.cancel();
    }

    @Unique
    private static List<Identifier> floydaddons$ensureLoaded() {
        if (floydaddons$frames != null) return floydaddons$frames;
        if (floydaddons$triedLoad) return null;
        floydaddons$triedLoad = true;

        Path framesDir = FloydCompatibility.configPath("mainmenu_frames");
        if (Files.isDirectory(framesDir)) {
            List<Identifier> loaded = floydaddons$loadFrameDir(framesDir);
            if (!loaded.isEmpty()) {
                floydaddons$frames = loaded;
                return floydaddons$frames;
            }
        }

        Path staticPath = FloydCompatibility.configPath("mainmenu.png");
        if (Files.exists(staticPath)) {
            Identifier id = floydaddons$loadPng(staticPath, "custom_mainmenu");
            if (id != null) {
                floydaddons$frames = List.of(id);
                return floydaddons$frames;
            }
        }

        return null;
    }

    @Unique
    private static List<Identifier> floydaddons$loadFrameDir(Path dir) {
        try {
            List<Path> pngPaths = Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted()
                    .limit(MAX_FRAMES)
                    .collect(Collectors.toList());
            List<Identifier> ids = new ArrayList<>(pngPaths.size());
            for (int i = 0; i < pngPaths.size(); i++) {
                final int idx = i;
                Identifier id = floydaddons$loadPng(pngPaths.get(i), "mainmenu_frame_" + idx);
                if (id != null) ids.add(id);
            }
            return ids;
        } catch (IOException e) {
            return List.of();
        }
    }

    @Unique
    private static Identifier floydaddons$loadPng(Path path, String texName) {
        try {
            NativeImage image = NativeImage.read(Files.newInputStream(path));
            DynamicTexture tex = new DynamicTexture(() -> "floydaddons_" + texName, image);
            Identifier id = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, texName);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            return id;
        } catch (IOException e) {
            return null;
        }
    }
}
