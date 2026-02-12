package floydaddons.not.dogshit.client.features.cosmetic;
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
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Handles loading cape textures from config/capes.
 */
public final class CapeManager {
    private static final ConcurrentMap<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, GifTexture> GIF_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Float> ASPECT_CACHE = new ConcurrentHashMap<>();
    private static final Identifier FALLBACK = Identifier.of("textures/entity/cape/vanilla");
    private static final String DEFAULT_CAPE_RESOURCE = "/assets/floydaddons/textures/cape/default_cape.png";
    private static final String DEFAULT_CAPE_NAME = "default_cape.png";
    private static final long IMAGE_SCAN_CACHE_MS = 3_000L;

    private static volatile List<String> cachedImages = List.of();
    private static volatile long lastImageScanMs = 0L;
    private static volatile String lastSelectionKey = "";

    private CapeManager() {}

    public static Path ensureDir() {
        Path dir = FloydAddonsConfig.getConfigDir().resolve("capes");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        extractDefaultIfEmpty(dir);
        return dir;
    }

    private static void extractDefaultIfEmpty(Path dir) {
        Path target = dir.resolve(DEFAULT_CAPE_NAME);
        if (Files.exists(target)) return;
        try (InputStream in = CapeManager.class.getResourceAsStream(DEFAULT_CAPE_RESOURCE)) {
            if (in != null) Files.copy(in, target);
        } catch (IOException ignored) {}
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
            stream.filter(p -> Files.isRegularFile(p))
                  .map(p -> p.getFileName().toString())
                  .filter(n -> {
                      String lower = n.toLowerCase(Locale.ROOT);
                      return lower.endsWith(".png") || lower.endsWith(".gif");
                  })
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
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gif")) {
            Identifier gifId = loadGifTexture(mc, path, fileName);
            return gifId != null ? gifId : FALLBACK;
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

    /** Tick animated GIF capes. Called from the main client tick. */
    public static void tickAnimations() {
        GIF_CACHE.values().forEach(GifTexture::tick);
    }

    private static Identifier loadGifTexture(MinecraftClient mc, Path path, String fileName) {
        String key = fileName.toLowerCase(Locale.ROOT);
        GifTexture cached = GIF_CACHE.get(key);
        if (cached != null) {
            return cached.id;
        }
        GifTexture created = GifTexture.fromFile(mc, path, fileName);
        if (created == null) {
            return null;
        }
        GIF_CACHE.put(key, created);
        ASPECT_CACHE.put(key, created.aspect);
        return created.id;
    }

    /** Simple animated GIF texture wrapper that swaps frames onto a single GPU texture. */
    private static final class GifTexture {
        final Identifier id;
        final NativeImageBackedTexture texture;
        final List<NativeImage> frames;
        final int[] delaysMs;
        final float aspect;

        private int frameIndex = 0;
        private long lastSwitchMs = System.currentTimeMillis();

        private GifTexture(Identifier id, NativeImageBackedTexture texture, List<NativeImage> frames, int[] delaysMs, float aspect) {
            this.id = id;
            this.texture = texture;
            this.frames = frames;
            this.delaysMs = delaysMs;
            this.aspect = aspect > 0 ? aspect : 2.0f;
        }

        static GifTexture fromFile(MinecraftClient mc, Path path, String fileName) {
            try (InputStream in = Files.newInputStream(path);
                 ImageInputStream imageStream = ImageIO.createImageInputStream(in)) {
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                if (!readers.hasNext() || imageStream == null) {
                    return null;
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(imageStream, false);
                    int frameCount = reader.getNumImages(true);
                    if (frameCount <= 0) {
                        return null;
                    }
                    int canvasW = reader.getWidth(0);
                    int canvasH = reader.getHeight(0);
                    NativeImage composite = new NativeImage(NativeImage.Format.RGBA, canvasW, canvasH, false);
                    List<NativeImage> frames = new ArrayList<>(frameCount);
                    List<Integer> delays = new ArrayList<>(frameCount);

                    for (int i = 0; i < frameCount; i++) {
                        IIOMetadata meta = reader.getImageMetadata(i);
                        BufferedImage raw = reader.read(i);
                        if (raw == null) continue;

                        int frameX = getIntAttr(meta, "imageLeftPosition", 0);
                        int frameY = getIntAttr(meta, "imageTopPosition", 0);
                        String disposal = getDisposal(meta);
                        int delayMs = readDelay(meta);

                        NativeImage before = null;
                        if ("restoreToPrevious".equals(disposal)) {
                            before = new NativeImage(NativeImage.Format.RGBA, canvasW, canvasH, false);
                            before.copyFrom(composite);
                        }

                        int rawW = raw.getWidth();
                        int rawH = raw.getHeight();
                        for (int y = 0; y < rawH; y++) {
                            int destY = frameY + y;
                            if (destY < 0 || destY >= canvasH) continue;
                            for (int x = 0; x < rawW; x++) {
                                int destX = canvasW - 1 - (frameX + x); // flip horizontally on the whole canvas
                                if (destX < 0 || destX >= canvasW) continue;
                                int argb = raw.getRGB(x, y);
                                if ((argb >>> 24) == 0) continue;
                                composite.setColorArgb(destX, destY, argb);
                            }
                        }

                        NativeImage snapshot = new NativeImage(NativeImage.Format.RGBA, canvasW, canvasH, false);
                        snapshot.copyFrom(composite);
                        frames.add(snapshot);
                        delays.add(delayMs);

                        // Apply disposal after the frame is displayed to prepare for the next one.
                        if ("restoreToBackgroundColor".equals(disposal)) {
                            composite.fillRect(frameX, frameY, raw.getWidth(), raw.getHeight(), 0);
                        } else if ("restoreToPrevious".equals(disposal) && before != null) {
                            composite.copyFrom(before);
                            before.close();
                        }
                    }
                    if (frames.isEmpty()) {
                        composite.close();
                        return null;
                    }
                    int width = canvasW;
                    int height = canvasH;
                    NativeImage working = new NativeImage(NativeImage.Format.RGBA, width, height, false);
                    working.copyFrom(frames.get(0));
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "floydaddons_cape_" + fileName, working);
                    tex.setFilter(true, true);
                    Identifier id = Identifier.of(FloydAddonsClient.MOD_ID, "cape/" + fileName.toLowerCase().replaceAll("[^a-z0-9._-]", "_"));
                    mc.getTextureManager().registerTexture(id, tex);
                    int[] delayArray = delays.stream().mapToInt(Integer::intValue).toArray();
                    float aspect = width / (float) height;
                    return new GifTexture(id, tex, frames, delayArray, aspect);
                } finally {
                    reader.dispose();
                }
            } catch (IOException e) {
                return null;
            }
        }

        void tick() {
            if (frames.size() <= 1) return;
            long now = System.currentTimeMillis();
            int delay = delaysMs[Math.max(0, Math.min(frameIndex, delaysMs.length - 1))];
            if (delay <= 0) delay = 100; // sensible default if metadata missing
            if (now - lastSwitchMs < delay) return;

            frameIndex = (frameIndex + 1) % frames.size();
            NativeImage next = frames.get(frameIndex);
            NativeImage target = texture.getImage();
            if (next != null && target != null) {
                target.copyFrom(next);
                texture.upload();
            }
            lastSwitchMs = now;
        }

        private static int readDelay(IIOMetadata meta) {
            try {
                String format = meta.getNativeMetadataFormatName();
                if (format == null) return 100;
                Node root = meta.getAsTree(format);
                Node gce = findNode(root, "GraphicControlExtension");
                if (gce != null) {
                    NamedNodeMap attrs = gce.getAttributes();
                    Node delayNode = attrs.getNamedItem("delayTime");
                    if (delayNode != null) {
                        int hundredths = Integer.parseInt(delayNode.getNodeValue());
                        int ms = hundredths * 10;
                        return ms > 0 ? ms : 100;
                    }
                }
            } catch (Exception ignored) {}
            return 100;
        }

        private static String getDisposal(IIOMetadata meta) {
            try {
                String format = meta.getNativeMetadataFormatName();
                if (format == null) return "none";
                Node root = meta.getAsTree(format);
                Node gce = findNode(root, "GraphicControlExtension");
                if (gce != null) {
                    NamedNodeMap attrs = gce.getAttributes();
                    Node disp = attrs.getNamedItem("disposalMethod");
                    if (disp != null) {
                        return disp.getNodeValue();
                    }
                }
            } catch (Exception ignored) {}
            return "none";
        }

        private static int getIntAttr(IIOMetadata meta, String attr, int def) {
            try {
                String format = meta.getNativeMetadataFormatName();
                if (format == null) return def;
                Node root = meta.getAsTree(format);
                Node desc = findNode(root, "ImageDescriptor");
                if (desc != null) {
                    NamedNodeMap attrs = desc.getAttributes();
                    Node node = attrs.getNamedItem(attr);
                    if (node != null) {
                        return Integer.parseInt(node.getNodeValue());
                    }
                }
            } catch (Exception ignored) {}
            return def;
        }

        private static Node findNode(Node node, String name) {
            if (node == null) return null;
            if (name.equals(node.getNodeName())) return node;
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                Node found = findNode(child, name);
                if (found != null) return found;
            }
            return null;
        }

    }
}
