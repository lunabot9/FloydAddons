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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final Path XRAY_OPAQUE_PATH = CONFIG_DIR.resolve("xray-opaque.json");
    private static final Path MOB_ESP_PATH = CONFIG_DIR.resolve("mob-esp.json");

    private FloydAddonsConfig() {}

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }

    public static Path getNamesPath() {
        return NAMES_PATH;
    }

    public static Path getXrayOpaquePath() {
        return XRAY_OPAQUE_PATH;
    }

    public static Path getMobEspPath() {
        return MOB_ESP_PATH;
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
        loadXrayOpaque();
        loadMobEsp();
    }

    /** Saves all settings to the unified config file. */
    public static void save() {
        ensureDir();
        Data data = new Data();
        // Nick hider
        data.nickHiderEnabled = NickHiderConfig.isEnabled();
        data.nickname = NickHiderConfig.getNickname();
        // Skin
        data.skinCustomEnabled = SkinConfig.customEnabled();
        data.skinSelfEnabled = SkinConfig.selfEnabled();
        data.skinOthersEnabled = SkinConfig.othersEnabled();
        data.selectedSkin = SkinConfig.getSelectedSkin();
        // Render
        data.inventoryHudEnabled = RenderConfig.isInventoryHudEnabled();
        data.inventoryHudX = RenderConfig.getInventoryHudX();
        data.inventoryHudY = RenderConfig.getInventoryHudY();
        data.inventoryHudScale = RenderConfig.getInventoryHudScale();
        data.floydHatEnabled = RenderConfig.isFloydHatEnabled();
        data.capeEnabled = RenderConfig.isCapeEnabled();
        data.coneHatHeight = RenderConfig.getConeHatHeight();
        data.coneHatRadius = RenderConfig.getConeHatRadius();
        data.coneHatYOffset = RenderConfig.getConeHatYOffset();
        data.coneHatRotation = RenderConfig.getConeHatRotation();
        data.coneHatRotationSpeed = RenderConfig.getConeHatRotationSpeed();
        data.buttonTextChromaEnabled = RenderConfig.isButtonTextChromaEnabled();
        data.buttonBorderChromaEnabled = RenderConfig.isButtonBorderChromaEnabled();
        data.guiBorderChromaEnabled = RenderConfig.isGuiBorderChromaEnabled();
        data.guiBorderColor = RenderConfig.getGuiBorderColor();
        data.buttonBorderColor = RenderConfig.getButtonBorderColor();
        data.buttonTextColor = RenderConfig.getButtonTextColor();
        data.selectedCapeImage = RenderConfig.getSelectedCapeImage();
        data.selectedConeImage = RenderConfig.getSelectedConeImage();
        data.customScoreboardEnabled = RenderConfig.isCustomScoreboardEnabled();
        data.customScoreboardX = RenderConfig.getCustomScoreboardX();
        data.customScoreboardY = RenderConfig.getCustomScoreboardY();
        data.serverIdHiderEnabled = RenderConfig.isServerIdHiderEnabled();
        data.xrayOpacity = RenderConfig.getXrayOpacity();
        data.mobEspEnabled = RenderConfig.isMobEspEnabled();
        data.mobEspTracers = RenderConfig.isMobEspTracers();
        data.mobEspHitboxes = RenderConfig.isMobEspHitboxes();
        data.mobEspStarMobs = RenderConfig.isMobEspStarMobs();
        data.defaultEspColor = RenderConfig.getDefaultEspColor();
        data.defaultEspChromaEnabled = RenderConfig.isDefaultEspChromaEnabled();
        data.stalkTracerColor = RenderConfig.getStalkTracerColor();
        data.stalkTracerChromaEnabled = RenderConfig.isStalkTracerChromaEnabled();

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

    /** Loads xray opaque block list. Creates a default file if missing. */
    public static void loadXrayOpaque() {
        ensureDir();
        if (!Files.exists(XRAY_OPAQUE_PATH)) {
            // Write default list
            try (Writer w = Files.newBufferedWriter(XRAY_OPAQUE_PATH)) {
                GSON.toJson(RenderConfig.defaultXrayOpaqueBlocks(), w);
            } catch (IOException ignored) {}
            return;
        }
        try (Reader r = Files.newBufferedReader(XRAY_OPAQUE_PATH)) {
            Type type = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = GSON.fromJson(r, type);
            if (loaded != null) {
                RenderConfig.setXrayOpaqueBlocks(Collections.unmodifiableSet(new LinkedHashSet<>(loaded)));
            }
        } catch (IOException ignored) {}
    }

    /** Loads mob ESP filter entries. Creates a template if missing. */
    public static void loadMobEsp() {
        ensureDir();
        if (!Files.exists(MOB_ESP_PATH)) {
            try {
                List<Map<String, String>> example = List.of(
                        Map.of("name", "Vanquisher"),
                        Map.of("mob", "minecraft:ghast")
                );
                try (Writer w = Files.newBufferedWriter(MOB_ESP_PATH)) {
                    GSON.toJson(example, w);
                }
            } catch (IOException ignored) {}
            MobEspManager.loadFilters(Collections.emptyList());
            return;
        }
        try (Reader r = Files.newBufferedReader(MOB_ESP_PATH)) {
            Type type = new TypeToken<List<Map<String, String>>>() {}.getType();
            List<Map<String, String>> loaded = GSON.fromJson(r, type);
            MobEspManager.loadFilters(loaded);
        } catch (IOException ignored) {
            MobEspManager.loadFilters(Collections.emptyList());
        }
    }

    /** Saves name mappings to the separate JSON file. */
    public static void saveNameMappings() {
        ensureDir();
        try (Writer w = Files.newBufferedWriter(NAMES_PATH)) {
            GSON.toJson(NickHiderConfig.getNameMappings(), w);
        } catch (IOException ignored) {}
    }

    /** Saves mob ESP filter entries to the JSON file. */
    public static void saveMobEsp() {
        ensureDir();
        try (Writer w = Files.newBufferedWriter(MOB_ESP_PATH)) {
            GSON.toJson(MobEspManager.getRawEntries(), w);
        } catch (IOException ignored) {}
    }

    /** Saves xray opaque block list to the JSON file. */
    public static void saveXrayOpaque() {
        ensureDir();
        try (Writer w = Files.newBufferedWriter(XRAY_OPAQUE_PATH)) {
            GSON.toJson(new java.util.ArrayList<>(RenderConfig.getXrayOpaqueBlocks()), w);
        } catch (IOException ignored) {}
    }

    private static void ensureDir() {
        try { Files.createDirectories(CONFIG_DIR); } catch (IOException ignored) {}
    }

    private static void loadNickHider(Data data) {
        if (data.nickname != null && !data.nickname.isEmpty()) NickHiderConfig.setNickname(data.nickname);
        NickHiderConfig.setEnabled(data.nickHiderEnabled);
    }

    private static void loadSkin(Data data) {
        SkinConfig.setCustomEnabled(data.skinCustomEnabled);
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
        RenderConfig.setCapeEnabled(data.capeEnabled);
        if (data.coneHatHeight > 0) RenderConfig.setConeHatHeight(data.coneHatHeight);
        if (data.coneHatRadius > 0) RenderConfig.setConeHatRadius(data.coneHatRadius);
        if (data.coneHatYOffset != 0) RenderConfig.setConeHatYOffset(data.coneHatYOffset);
        RenderConfig.setConeHatRotation(data.coneHatRotation);
        RenderConfig.setConeHatRotationSpeed(data.coneHatRotationSpeed);
        RenderConfig.setButtonTextChromaEnabled(data.buttonTextChromaEnabled);
        RenderConfig.setButtonBorderChromaEnabled(data.buttonBorderChromaEnabled);
        RenderConfig.setGuiBorderChromaEnabled(data.guiBorderChromaEnabled);
        RenderConfig.setGuiBorderColor(data.guiBorderColor);
        RenderConfig.setButtonBorderColor(data.buttonBorderColor);
        RenderConfig.setButtonTextColor(data.buttonTextColor);
        if (data.selectedCapeImage != null) RenderConfig.setSelectedCapeImage(data.selectedCapeImage);
        if (data.selectedConeImage != null) RenderConfig.setSelectedConeImage(data.selectedConeImage);
        RenderConfig.setCustomScoreboardEnabled(data.customScoreboardEnabled);
        RenderConfig.setCustomScoreboardX(data.customScoreboardX);
        RenderConfig.setCustomScoreboardY(data.customScoreboardY);
        RenderConfig.setServerIdHiderEnabled(data.serverIdHiderEnabled);
        if (data.xrayOpacity > 0) RenderConfig.setXrayOpacity(data.xrayOpacity);
        RenderConfig.setMobEspEnabled(data.mobEspEnabled);
        RenderConfig.setMobEspTracers(data.mobEspTracers);
        RenderConfig.setMobEspHitboxes(data.mobEspHitboxes);
        RenderConfig.setMobEspStarMobs(data.mobEspStarMobs);
        RenderConfig.setDefaultEspColor(data.defaultEspColor);
        RenderConfig.setDefaultEspChromaEnabled(data.defaultEspChromaEnabled);
        RenderConfig.setStalkTracerColor(data.stalkTracerColor);
        RenderConfig.setStalkTracerChromaEnabled(data.stalkTracerChromaEnabled);
    }

    private static class Data {
        // Nick hider
        boolean nickHiderEnabled;
        String nickname;
        // Skin
        boolean skinCustomEnabled = true;
        boolean skinSelfEnabled;
        boolean skinOthersEnabled;
        String selectedSkin;
        // Render
        boolean inventoryHudEnabled;
        int inventoryHudX;
        int inventoryHudY;
        float inventoryHudScale;
        boolean floydHatEnabled;
        boolean capeEnabled;
        float coneHatHeight;
        float coneHatRadius;
        float coneHatYOffset;
        float coneHatRotation;
        float coneHatRotationSpeed;
        boolean buttonTextChromaEnabled = true;
        boolean buttonBorderChromaEnabled = true;
        boolean guiBorderChromaEnabled = true;
        int guiBorderColor = RenderConfig.getGuiBorderColor();
        int buttonBorderColor = RenderConfig.getButtonBorderColor();
        int buttonTextColor = RenderConfig.getButtonTextColor();
        String selectedCapeImage;
        String selectedConeImage;
        boolean customScoreboardEnabled;
        int customScoreboardX;
        int customScoreboardY;
        boolean serverIdHiderEnabled;
        float xrayOpacity;
        boolean mobEspEnabled;
        boolean mobEspTracers = true;
        boolean mobEspHitboxes = true;
        boolean mobEspStarMobs = true;
        int defaultEspColor = 0xFFFFFFFF;
        boolean defaultEspChromaEnabled = true;
        int stalkTracerColor = 0xFFFFFFFF;
        boolean stalkTracerChromaEnabled = true;
    }
}
