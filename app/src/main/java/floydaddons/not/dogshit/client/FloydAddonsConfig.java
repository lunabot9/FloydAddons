package floydaddons.not.dogshit.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified config persistence for all FloydAddons settings.
 * Everything lives under .minecraft/config/floydaddons/:
 *   config.json        - all mod settings (nick hider, skin, render)
 *   name-mappings.json - per-player nick replacements
 *   *.png              - custom skin files
 */
public final class FloydAddonsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("floydaddons");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");
    private static final Path NAMES_PATH = CONFIG_DIR.resolve("name-mappings.json");

    private FloydAddonsConfig() {}

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }

    public static Path getNamesPath() {
        return NAMES_PATH;
    }

    /** Loads all settings from the unified config file and the name mappings file. */
    public static void load() {
        ensureDir();
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                Data data = GSON.fromJson(r, Data.class);
                if (data != null) {
                    loadNickHider(data);
                    loadSkin(data);
                    loadRender(data);
                }
            } catch (IOException ignored) {
            }
        }
        loadNameMappings();
    }

    /** Saves all settings to the unified config file. */
    public static void save() {
        ensureDir();
        Data data = new Data();
        // Nick hider
        data.nickHiderEnabled = NickHiderConfig.isEnabled();
        data.nickname = NickHiderConfig.getNickname();
        data.hideOthersMode = NickHiderConfig.getHideOthersMode().name();
        data.othersNickname = NickHiderConfig.getOthersNickname();
        // Skin
        data.skinSelfEnabled = SkinConfig.selfEnabled();
        data.skinOthersEnabled = SkinConfig.othersEnabled();
        data.selectedSkin = SkinConfig.getSelectedSkin();
        // Render
        data.inventoryHudEnabled = RenderConfig.isInventoryHudEnabled();
        data.inventoryHudX = RenderConfig.getInventoryHudX();
        data.inventoryHudY = RenderConfig.getInventoryHudY();
        data.inventoryHudScale = RenderConfig.getInventoryHudScale();
        data.floydHatEnabled = RenderConfig.isFloydHatEnabled();

        try {
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, w);
            }
        } catch (IOException ignored) {
        }
    }

    /** Loads name mappings from the separate JSON file. Creates a template if missing. */
    public static void loadNameMappings() {
        ensureDir();
        if (!Files.exists(NAMES_PATH)) {
            try {
                Map<String, String> example = new LinkedHashMap<>();
                example.put("ExampleIGN", "NewDisplayName");
                try (Writer w = Files.newBufferedWriter(NAMES_PATH)) {
                    GSON.toJson(example, w);
                }
            } catch (IOException ignored) {
            }
            NickHiderConfig.setNameMappingsRaw(Collections.emptyMap());
            return;
        }
        try (Reader r = Files.newBufferedReader(NAMES_PATH)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(r, type);
            if (loaded != null) {
                NickHiderConfig.setNameMappingsRaw(loaded);
            } else {
                NickHiderConfig.setNameMappingsRaw(Collections.emptyMap());
            }
        } catch (IOException ignored) {
            NickHiderConfig.setNameMappingsRaw(Collections.emptyMap());
        }
    }

    private static void ensureDir() {
        try { Files.createDirectories(CONFIG_DIR); } catch (IOException ignored) {}
    }

    private static void loadNickHider(Data data) {
        if (data.nickname != null && !data.nickname.isEmpty()) NickHiderConfig.setNickname(data.nickname);
        NickHiderConfig.setEnabled(data.nickHiderEnabled);
        if (data.hideOthersMode != null) {
            try {
                NickHiderConfig.setHideOthersMode(NickHiderConfig.HideOthersMode.valueOf(data.hideOthersMode));
            } catch (IllegalArgumentException e) {
                NickHiderConfig.setHideOthersMode(NickHiderConfig.HideOthersMode.OFF);
            }
        }
        if (data.othersNickname != null && !data.othersNickname.isEmpty()) NickHiderConfig.setOthersNickname(data.othersNickname);
    }

    private static void loadSkin(Data data) {
        SkinConfig.setSelfEnabled(data.skinSelfEnabled);
        SkinConfig.setOthersEnabled(data.skinOthersEnabled);
        if (data.selectedSkin != null) SkinConfig.setSelectedSkin(data.selectedSkin);
    }

    private static void loadRender(Data data) {
        RenderConfig.setInventoryHudEnabled(data.inventoryHudEnabled);
        RenderConfig.setInventoryHudX(data.inventoryHudX);
        RenderConfig.setInventoryHudY(data.inventoryHudY);
        RenderConfig.setInventoryHudScale(data.inventoryHudScale);
        RenderConfig.setFloydHatEnabled(data.floydHatEnabled);
    }

    private static class Data {
        // Nick hider
        boolean nickHiderEnabled;
        String nickname;
        String hideOthersMode;
        String othersNickname;
        // Skin
        boolean skinSelfEnabled;
        boolean skinOthersEnabled;
        String selectedSkin;
        // Render
        boolean inventoryHudEnabled;
        int inventoryHudX;
        int inventoryHudY;
        float inventoryHudScale;
        boolean floydHatEnabled;
    }
}
