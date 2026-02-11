package floydaddons.not.dogshit.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ConeHatManager {
    private static final Identifier BUILTIN_TEXTURE = Identifier.of(FloydAddonsClient.MOD_ID, "textures/entity/cone.png");
    private static final String DEFAULT_IMAGE_NAME = "Floyd.png";
    private static final Path IMAGE_DIR = FloydAddonsConfig.getConfigDir().resolve("cone-hats");
    private static Identifier cachedTexture = null;
    private static String cachedSelection = null;
    private static boolean defaultExtracted = false;

    private ConeHatManager() {}

    public static Identifier getTexture(MinecraftClient mc) {
        if (mc == null) return BUILTIN_TEXTURE;
        String selected = RenderConfig.getSelectedConeImage();
        if (cachedTexture != null && selected.equals(cachedSelection)) {
            return cachedTexture;
        }
        loadTexture(mc);
        return cachedTexture != null ? cachedTexture : BUILTIN_TEXTURE;
    }

    public static Path getImageDir() { return IMAGE_DIR; }

    public static Path ensureDir() {
        try { Files.createDirectories(IMAGE_DIR); } catch (IOException ignored) {}
        return IMAGE_DIR;
    }

    public static List<String> listAvailableImages() {
        ensureDir();
        List<String> images = new ArrayList<>();
        try (Stream<Path> files = Files.list(IMAGE_DIR)) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".png"))
                 .map(p -> p.getFileName().toString())
                 .sorted(String.CASE_INSENSITIVE_ORDER)
                 .forEach(images::add);
        } catch (IOException ignored) {}
        return images;
    }

    public static void clearCache() {
        cachedTexture = null;
        cachedSelection = null;
    }

    public static void extractDefault(MinecraftClient mc) {
        if (defaultExtracted) return;
        defaultExtracted = true;
        ensureDir();
        Path target = IMAGE_DIR.resolve(DEFAULT_IMAGE_NAME);
        if (Files.exists(target)) return;
        try {
            var resource = mc.getResourceManager().getResource(BUILTIN_TEXTURE);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().getInputStream()) {
                    Files.copy(in, target);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void loadTexture(MinecraftClient mc) {
        try {
            extractDefault(mc);
            NativeImage image = null;

            String selected = RenderConfig.getSelectedConeImage();
            if (selected != null && !selected.isEmpty()) {
                Path file = IMAGE_DIR.resolve(selected);
                if (Files.isRegularFile(file)) {
                    image = NativeImage.read(Files.newInputStream(file));
                }
            }

            if (image == null) {
                List<String> available = listAvailableImages();
                if (!available.isEmpty()) {
                    String first = available.get(0);
                    Path file = IMAGE_DIR.resolve(first);
                    image = NativeImage.read(Files.newInputStream(file));
                    RenderConfig.setSelectedConeImage(first);
                    selected = first;
                }
            }

            if (image == null) {
                var resource = mc.getResourceManager().getResource(BUILTIN_TEXTURE);
                if (resource.isPresent()) {
                    image = NativeImage.read(resource.get().getInputStream());
                }
            }

            if (image == null) {
                cachedTexture = null;
                cachedSelection = selected;
                return;
            }

            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "floydaddons_cone", image);
            TextureManager tm = mc.getTextureManager();
            Identifier id = Identifier.of(FloydAddonsClient.MOD_ID, "cone/custom");
            tm.registerTexture(id, tex);
            cachedTexture = id;
            cachedSelection = selected;
        } catch (Exception e) {
            cachedTexture = null;
            cachedSelection = null;
        }
    }
}
