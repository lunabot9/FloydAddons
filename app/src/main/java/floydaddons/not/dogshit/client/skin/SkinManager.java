package floydaddons.not.dogshit.client.skin;
import floydaddons.not.dogshit.client.*;
import floydaddons.not.dogshit.client.config.*;
import floydaddons.not.dogshit.client.gui.*;
import floydaddons.not.dogshit.client.features.hud.*;
import floydaddons.not.dogshit.client.features.visual.*;
import floydaddons.not.dogshit.client.features.cosmetic.*;
import floydaddons.not.dogshit.client.features.misc.*;
import floydaddons.not.dogshit.client.esp.*;
import floydaddons.not.dogshit.client.skin.*;
import floydaddons.not.dogshit.client.util.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class SkinManager {
    private static final Identifier BUILTIN_SKIN = Identifier.of(FloydAddonsClient.MOD_ID, "textures/skin/custom.png");
    private static final String DEFAULT_SKIN_NAME = "george-floyd.png";
    private static final Path SKIN_DIR = FloydAddonsConfig.getConfigDir().resolve("skins");
    private static Identifier cachedTexture = null;
    private static long lastLoad = 0;
    private static final long RELOAD_MS = 5_000;
    private static boolean defaultExtracted = false;

    private SkinManager() {}

    public static Identifier getCustomTexture(MinecraftClient mc) {
        if (mc == null) return null;
        if (!SkinConfig.customEnabled()) return null;
        long now = System.currentTimeMillis();
        if (cachedTexture == null || now - lastLoad > RELOAD_MS) {
            loadTexture(mc);
            lastLoad = now;
        }
        return cachedTexture;
    }

    /** Returns the skin folder directory. */
    public static Path getSkinDir() {
        return SKIN_DIR;
    }

    /** Ensures the skin folder exists; returns its directory. */
    public static Path ensureExternalDir() {
        try { Files.createDirectories(SKIN_DIR); } catch (IOException ignored) {}
        return SKIN_DIR;
    }

    /** Lists all .png files in the skin folder, sorted alphabetically. */
    public static List<String> listAvailableSkins() {
        ensureExternalDir();
        List<String> skins = new ArrayList<>();
        try (Stream<Path> files = Files.list(SKIN_DIR)) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".png"))
                 .map(p -> p.getFileName().toString())
                 .sorted(String.CASE_INSENSITIVE_ORDER)
                 .forEach(skins::add);
        } catch (IOException ignored) {}
        return skins;
    }

    public static void clearCache() {
        cachedTexture = null;
        lastLoad = 0;
    }

    /**
     * Extracts the bundled skin to the skins folder as george-floyd.png
     * so end users get a default skin without manual setup.
     * Called at startup and before texture loading.
     */
    public static void extractDefaultSkin(MinecraftClient mc) {
        if (defaultExtracted) return;
        defaultExtracted = true;
        ensureExternalDir();
        Path target = SKIN_DIR.resolve(DEFAULT_SKIN_NAME);
        if (Files.exists(target)) return;
        try {
            var resource = mc.getResourceManager().getResource(BUILTIN_SKIN);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().getInputStream()) {
                    Files.copy(in, target);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void loadTexture(MinecraftClient mc) {
        try {
            extractDefaultSkin(mc);
            NativeImage image = null;

            // Load the selected skin from config
            String selected = SkinConfig.getSelectedSkin();
            if (selected != null && !selected.isEmpty()) {
                Path skinFile = SKIN_DIR.resolve(selected);
                if (Files.isRegularFile(skinFile)) {
                    image = NativeImage.read(Files.newInputStream(skinFile));
                }
            }

            // Fallback: try any .png in the folder
            if (image == null) {
                List<String> available = listAvailableSkins();
                if (!available.isEmpty()) {
                    String first = available.get(0);
                    Path skinFile = SKIN_DIR.resolve(first);
                    image = NativeImage.read(Files.newInputStream(skinFile));
                    // Auto-select it for next time
                    SkinConfig.setSelectedSkin(first);
                }
            }

            // Bundled resource fallback
            if (image == null) {
                var resource = mc.getResourceManager().getResource(BUILTIN_SKIN);
                if (resource.isPresent()) {
                    image = NativeImage.read(resource.get().getInputStream());
                }
            }

            // Generator fallback
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

    private static NativeImage generateFallbackSkin() {
        NativeImage img = new NativeImage(64, 64, false);
        int dark = 0xFF1A1612;
        int skin = 0xFF6A4C36;
        int stripe1 = 0xFF4A4948;
        int stripe2 = 0xFF646464;
        int blue = 0xFF1E223C;
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setColor(x, y, dark);
        for (int y = 8; y < 16; y++) for (int x = 20; x < 36; x++) img.setColor(x, y, skin);
        for (int y = 20; y < 32; y++) for (int x = 8; x < 56; x++) img.setColor(x, y, (y % 4 < 2) ? stripe2 : stripe1);
        for (int y = 32; y < 64; y++) for (int x = 12; x < 52; x++) img.setColor(x, y, blue);
        return img;
    }
}
