package floydaddons.not.dogshit.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SkinConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("floydaddons-skin.json");

    private static boolean selfEnabled = false;
    private static boolean othersEnabled = false;

    private SkinConfig() {}

    public static boolean selfEnabled() { return selfEnabled; }
    public static boolean othersEnabled() { return othersEnabled; }
    public static void setSelfEnabled(boolean v) { selfEnabled = v; }
    public static void setOthersEnabled(boolean v) { othersEnabled = v; }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d != null) {
                selfEnabled = d.selfEnabled;
                othersEnabled = d.othersEnabled;
            }
        } catch (IOException ignored) {}
    }

    public static void save() {
        Data d = new Data();
        d.selfEnabled = selfEnabled;
        d.othersEnabled = othersEnabled;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(d, w);
            }
        } catch (IOException ignored) {}
    }

    private static class Data {
        boolean selfEnabled;
        boolean othersEnabled;
        String skinPath;
    }
}
