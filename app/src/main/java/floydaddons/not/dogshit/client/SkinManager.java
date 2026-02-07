package floydaddons.not.dogshit.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.*;

public final class SkinManager {
    private static final Identifier BUILTIN_SKIN = Identifier.of(FloydAddonsClient.MOD_ID, "textures/skin/custom.png");
    private static final Path EXTERNAL_SKIN_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("floydaddons").resolve("custom-skin.png");
    private static Identifier cachedTexture = null;
    private static long lastLoad = 0;
    private static final long RELOAD_MS = 5_000;

    private SkinManager() {}

    public static Identifier getCustomTexture(MinecraftClient mc) {
        if (mc == null) return null;
        long now = System.currentTimeMillis();
        if (cachedTexture == null || now - lastLoad > RELOAD_MS) {
            loadTexture(mc);
            lastLoad = now;
        }
        return cachedTexture;
    }

    /** Location users can drop a PNG to override the built-in skin. */
    public static Path getExternalSkinPath() {
        return EXTERNAL_SKIN_PATH;
    }

    /** Ensures the external folder exists; returns its directory. */
    public static Path ensureExternalDir() {
        Path dir = EXTERNAL_SKIN_PATH.getParent();
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    public static void clearCache() {
        cachedTexture = null;
        lastLoad = 0;
    }

    private static void loadTexture(MinecraftClient mc) {
        try {
            NativeImage image = null;

            // First: user-provided override.
            if (Files.isRegularFile(EXTERNAL_SKIN_PATH)) {
                image = NativeImage.read(Files.newInputStream(EXTERNAL_SKIN_PATH));
            }

            // Second: bundled resource.
            if (image == null) {
                var resource = mc.getResourceManager().getResource(BUILTIN_SKIN);
                if (resource.isPresent()) {
                    image = NativeImage.read(resource.get().getInputStream());
                }
            }

            // Fallback generator.
            if (image == null) {
                image = generateFallbackSkin();
            }

            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "floydaddons_skin", image);
            TextureManager tm = mc.getTextureManager();
            cachedTexture = Identifier.of(FloydAddonsClient.MOD_ID, "skin/custom");
            tm.registerTexture(cachedTexture, tex);
        } catch (Exception e) {
            cachedTexture = null;
        }
    }

    // Simple generated fallback skin so toggles visibly change something even if resource missing.
    private static NativeImage generateFallbackSkin() {
        NativeImage img = new NativeImage(64, 64, false);
        int dark = 0xFF1A1612;
        int skin = 0xFF6A4C36;
        int stripe1 = 0xFF4A4948;
        int stripe2 = 0xFF646464;
        int blue = 0xFF1E223C;
        // base dark
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setColor(x, y, dark);
        // head front
        for (int y = 8; y < 16; y++) for (int x = 20; x < 36; x++) img.setColor(x, y, skin);
        // torso stripes
        for (int y = 20; y < 32; y++) for (int x = 8; x < 56; x++) img.setColor(x, y, (y % 4 < 2) ? stripe2 : stripe1);
        // legs blue
        for (int y = 32; y < 64; y++) for (int x = 12; x < 52; x++) img.setColor(x, y, blue);
        return img;
    }
}
