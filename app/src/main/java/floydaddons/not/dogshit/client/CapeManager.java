package floydaddons.not.dogshit.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Handles loading cape textures from config/capes.
 */
public final class CapeManager {
    private static final ConcurrentMap<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Float> ASPECT_CACHE = new ConcurrentHashMap<>();
    private static final Identifier FALLBACK = Identifier.of("textures/entity/cape/vanilla");
    private static final long IMAGE_SCAN_CACHE_MS = 3_000L;

    private static volatile List<String> cachedImages = List.of();
    private static volatile long lastImageScanMs = 0L;
    private static volatile String lastSelectionKey = "";

    private CapeManager() {}

    public static Path ensureDir() {
        Path dir = FloydAddonsConfig.getConfigDir().resolve("capes");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    public static List<String> listAvailableImages() {
        return listAvailableImages(false);
    }

    public static List<String> listAvailableImages(boolean forceRefresh) {
        Path dir = ensureDir();
        long now = System.currentTimeMillis();
        if (!forceRefresh && now - lastImageScanMs < IMAGE_SCAN_CACHE_MS && !cachedImages.isEmpty()) {
            return cachedImages;
        }

        List<String> names = new ArrayList<>();
        // Cache directory scans to avoid hammering the filesystem during render ticks.
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".png"))
                  .map(p -> p.getFileName().toString())
                  .sorted(String.CASE_INSENSITIVE_ORDER)
                  .forEach(names::add);
        } catch (IOException ignored) {}

        cachedImages = names;
        lastImageScanMs = now;
        return cachedImages;
    }

    public static Identifier getTexture(MinecraftClient mc) {
        String selected = resolveSelection();
        String key = selected.toLowerCase(Locale.ROOT);
        lastSelectionKey = key;
        return CACHE.computeIfAbsent(key, k -> loadTexture(mc, selected));
    }

    /** Aspect ratio width/height of the current cape texture (fallback 2.0). */
    public static float getAspectRatio() {
        String key = lastSelectionKey;
        if (key == null || key.isEmpty()) {
            key = resolveSelection().toLowerCase(Locale.ROOT);
            lastSelectionKey = key;
        }
        return ASPECT_CACHE.getOrDefault(key, 2.0f);
    }

    private static String resolveSelection() {
        String selected = RenderConfig.getSelectedCapeImage();
        if (selected != null && !selected.isEmpty()) {
            Path file = ensureDir().resolve(selected);
            if (Files.isRegularFile(file)) {
                return selected;
            }
        }

        List<String> images = listAvailableImages(true);
        if (!images.isEmpty()) {
            String choice = images.get(0);
            if (!choice.equals(selected)) {
                RenderConfig.setSelectedCapeImage(choice);
                RenderConfig.save();
            }
            return choice;
        }

        return "";
    }

    private static Identifier loadTexture(MinecraftClient mc, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return FALLBACK;
        }
        Path path = ensureDir().resolve(fileName);
        if (!Files.exists(path)) {
            return FALLBACK;
        }
        try {
            NativeImage img = NativeImage.read(Files.newInputStream(path));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "floydaddons_cape_" + fileName, img);
            // Use linear filtering + mipmaps so higher-res capes stay smooth instead of looking blocky.
            tex.setFilter(true, true);
            Identifier id = Identifier.of(FloydAddonsClient.MOD_ID, "cape/" + fileName.toLowerCase().replaceAll("[^a-z0-9._-]", "_"));
            mc.getTextureManager().registerTexture(id, tex);
            float aspect = img.getWidth() / (float) img.getHeight();
            ASPECT_CACHE.put(fileName.toLowerCase(), aspect > 0 ? aspect : 2.0f);
            return id;
        } catch (IOException e) {
            return FALLBACK;
        }
    }
}
