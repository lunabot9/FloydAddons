package floydaddons.not.dogshit.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Config backing the Render screen (inventory HUD).
 */
public final class RenderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("floydaddons-render.json");

    private static boolean inventoryHudEnabled = false;
    private static int inventoryHudX = 12;
    private static int inventoryHudY = 12;
    private static float inventoryHudScale = 1.1f; // fixed default size (slightly larger)
    private static boolean floydHatEnabled = false; // kept for compatibility but unused

    private RenderConfig() {}

    public static boolean isInventoryHudEnabled() { return inventoryHudEnabled; }
    public static void setInventoryHudEnabled(boolean enabled) { inventoryHudEnabled = enabled; }

    public static int getInventoryHudX() { return inventoryHudX; }
    public static int getInventoryHudY() { return inventoryHudY; }
    public static void setInventoryHudX(int x) { inventoryHudX = x; }
    public static void setInventoryHudY(int y) { inventoryHudY = y; }
    public static float getInventoryHudScale() { return inventoryHudScale; }
    public static void setInventoryHudScale(float scale) { inventoryHudScale = 1.1f; }

    public static boolean isFloydHatEnabled() { return floydHatEnabled; }
    public static void setFloydHatEnabled(boolean v) { floydHatEnabled = v; }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            Data data = GSON.fromJson(r, Data.class);
            if (data != null) {
                inventoryHudEnabled = data.inventoryHudEnabled;
                inventoryHudX = data.inventoryHudX;
                inventoryHudY = data.inventoryHudY;
                inventoryHudScale = 1.1f;
                floydHatEnabled = data.floydHatEnabled;
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        Data data = new Data();
        data.inventoryHudEnabled = inventoryHudEnabled;
        data.inventoryHudX = inventoryHudX;
        data.inventoryHudY = inventoryHudY;
        data.inventoryHudScale = inventoryHudScale;
        data.floydHatEnabled = floydHatEnabled;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, w);
            }
        } catch (IOException ignored) {
        }
    }

    private static class Data {
        boolean inventoryHudEnabled;
        int inventoryHudX;
        int inventoryHudY;
        float inventoryHudScale;
        boolean floydHatEnabled;
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
