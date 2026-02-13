package floydaddons.not.dogshit.client;
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
                    loadHiders(data);
                    loadCamera(data);
                    loadAnimation(data);
                    ClickGuiConfig.loadFromData(data.clickGuiPanels);
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
        data.playerScaleX = SkinConfig.getPlayerScaleX();
        data.playerScaleY = SkinConfig.getPlayerScaleY();
        data.playerScaleZ = SkinConfig.getPlayerScaleZ();
        data.playerSizeTarget = SkinConfig.getPlayerSizeTarget();
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
        data.buttonTextFadeEnabled = RenderConfig.isButtonTextFadeEnabled();
        data.buttonBorderFadeEnabled = RenderConfig.isButtonBorderFadeEnabled();
        data.guiBorderFadeEnabled = RenderConfig.isGuiBorderFadeEnabled();
        data.customTimeEnabled = RenderConfig.isCustomTimeEnabled();
        data.customTimeValue = RenderConfig.getCustomTimeValue();
        data.guiBorderColor = RenderConfig.getGuiBorderColor();
        data.buttonBorderColor = RenderConfig.getButtonBorderColor();
        data.buttonTextColor = RenderConfig.getButtonTextColor();
        data.guiBorderFadeColor = RenderConfig.getGuiBorderFadeColor();
        data.buttonBorderFadeColor = RenderConfig.getButtonBorderFadeColor();
        data.buttonTextFadeColor = RenderConfig.getButtonTextFadeColor();
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
        data.hudCornerRadius = RenderConfig.getHudCornerRadius();
        // Camera
        data.freecamSpeed = CameraConfig.getFreecamSpeed();
        data.freelookDistance = CameraConfig.getFreelookDistance();
        data.f5DisableFront = CameraConfig.isF5DisableFront();
        data.f5DisableBack = CameraConfig.isF5DisableBack();
        data.f5CameraDistance = CameraConfig.getF5CameraDistance();
        data.f5ScrollEnabled = CameraConfig.isF5ScrollEnabled();
        data.f5ResetOnToggle = CameraConfig.isF5ResetOnToggle();
        // Hiders
        data.hiderNoHurtCamera = HidersConfig.isNoHurtCameraEnabled();
        data.hiderRemoveFireOverlay = HidersConfig.isRemoveFireOverlayEnabled();
        data.hiderDisableHungerBar = HidersConfig.isDisableHungerBarEnabled();
        data.hiderHidePotionEffects = HidersConfig.isHidePotionEffectsEnabled();
        data.hiderThirdPersonCrosshair = HidersConfig.isThirdPersonCrosshairEnabled();
        data.hiderHideEntityFire = HidersConfig.isHideEntityFireEnabled();
        data.hiderDisableAttachedArrows = HidersConfig.isDisableAttachedArrowsEnabled();
        data.hiderRemoveFallingBlocks = HidersConfig.isRemoveFallingBlocksEnabled();
        data.hiderRemoveExplosionParticles = HidersConfig.isRemoveExplosionParticlesEnabled();
        data.hiderRemoveTabPing = HidersConfig.isRemoveTabPingEnabled();
        data.hiderNoArmorMode = HidersConfig.getNoArmorMode();
        // Animations
        data.animEnabled = AnimationConfig.isEnabled();
        data.animPosX = AnimationConfig.getPosX();
        data.animPosY = AnimationConfig.getPosY();
        data.animPosZ = AnimationConfig.getPosZ();
        data.animRotX = AnimationConfig.getRotX();
        data.animRotY = AnimationConfig.getRotY();
        data.animRotZ = AnimationConfig.getRotZ();
        data.animScale = AnimationConfig.getScale();
        data.animSwingDuration = AnimationConfig.getSwingDuration();
        data.animCancelReEquip = AnimationConfig.isCancelReEquip();
        data.animHidePlayerHand = AnimationConfig.isHidePlayerHand();
        data.animClassicClick = AnimationConfig.isClassicClick();
        // ClickGUI panel positions
        data.clickGuiPanels = ClickGuiConfig.toData();

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
        // Backward compat: if XYZ are all default but old playerScale was set, apply uniformly
        if (data.playerScaleX != 1.0f || data.playerScaleY != 1.0f || data.playerScaleZ != 1.0f) {
            SkinConfig.setPlayerScaleX(data.playerScaleX);
            SkinConfig.setPlayerScaleY(data.playerScaleY);
            SkinConfig.setPlayerScaleZ(data.playerScaleZ);
        } else if (data.playerScale != 1.0f && data.playerScale > 0) {
            SkinConfig.setPlayerScale(data.playerScale);
        }
        if (data.playerSizeTarget != null) SkinConfig.setPlayerSizeTarget(data.playerSizeTarget);
    }

    private static void loadHiders(Data data) {
        HidersConfig.setNoHurtCameraEnabled(data.hiderNoHurtCamera);
        HidersConfig.setRemoveFireOverlayEnabled(data.hiderRemoveFireOverlay);
        HidersConfig.setDisableHungerBarEnabled(data.hiderDisableHungerBar);
        HidersConfig.setHidePotionEffectsEnabled(data.hiderHidePotionEffects);
        HidersConfig.setThirdPersonCrosshairEnabled(data.hiderThirdPersonCrosshair);
        HidersConfig.setHideEntityFireEnabled(data.hiderHideEntityFire);
        HidersConfig.setDisableAttachedArrowsEnabled(data.hiderDisableAttachedArrows);
        HidersConfig.setRemoveFallingBlocksEnabled(data.hiderRemoveFallingBlocks);
        HidersConfig.setRemoveExplosionParticlesEnabled(data.hiderRemoveExplosionParticles);
        HidersConfig.setRemoveTabPingEnabled(data.hiderRemoveTabPing);
        if (data.hiderNoArmorMode != null) HidersConfig.setNoArmorMode(data.hiderNoArmorMode);
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
        RenderConfig.setButtonTextFadeEnabled(data.buttonTextFadeEnabled);
        RenderConfig.setButtonBorderFadeEnabled(data.buttonBorderFadeEnabled);
        RenderConfig.setGuiBorderFadeEnabled(data.guiBorderFadeEnabled);
        RenderConfig.setCustomTimeEnabled(data.customTimeEnabled);
        RenderConfig.setCustomTimeValue(data.customTimeValue);
        RenderConfig.setGuiBorderColor(data.guiBorderColor);
        RenderConfig.setButtonBorderColor(data.buttonBorderColor);
        RenderConfig.setButtonTextColor(data.buttonTextColor);
        RenderConfig.setGuiBorderFadeColor(data.guiBorderFadeColor);
        RenderConfig.setButtonBorderFadeColor(data.buttonBorderFadeColor);
        RenderConfig.setButtonTextFadeColor(data.buttonTextFadeColor);
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
        RenderConfig.setHudCornerRadius(data.hudCornerRadius);
    }

    private static void loadAnimation(Data data) {
        AnimationConfig.setEnabled(data.animEnabled);
        AnimationConfig.setPosX(data.animPosX);
        AnimationConfig.setPosY(data.animPosY);
        AnimationConfig.setPosZ(data.animPosZ);
        AnimationConfig.setRotX(data.animRotX);
        AnimationConfig.setRotY(data.animRotY);
        AnimationConfig.setRotZ(data.animRotZ);
        if (data.animScale > 0) AnimationConfig.setScale(data.animScale);
        if (data.animSwingDuration > 0) AnimationConfig.setSwingDuration(data.animSwingDuration);
        AnimationConfig.setCancelReEquip(data.animCancelReEquip);
        AnimationConfig.setHidePlayerHand(data.animHidePlayerHand);
        AnimationConfig.setClassicClick(data.animClassicClick);
    }

    private static void loadCamera(Data data) {
        if (data.freecamSpeed > 0) CameraConfig.setFreecamSpeed(data.freecamSpeed);
        if (data.freelookDistance > 0) CameraConfig.setFreelookDistance(data.freelookDistance);
        CameraConfig.setF5DisableFront(data.f5DisableFront);
        CameraConfig.setF5DisableBack(data.f5DisableBack);
        if (data.f5CameraDistance > 0) CameraConfig.setF5CameraDistance(data.f5CameraDistance);
        CameraConfig.setF5ScrollEnabled(data.f5ScrollEnabled);
        CameraConfig.setF5ResetOnToggle(data.f5ResetOnToggle);
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
        float playerScale = 1.0f; // legacy, kept for backward compat
        float playerScaleX = 1.0f;
        float playerScaleY = 1.0f;
        float playerScaleZ = 1.0f;
        String playerSizeTarget = "Self";
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
        boolean buttonTextFadeEnabled;
        boolean buttonBorderFadeEnabled;
        boolean guiBorderFadeEnabled;
        boolean customTimeEnabled;
        float customTimeValue = 50f;
        int guiBorderColor = RenderConfig.getGuiBorderColor();
        int buttonBorderColor = RenderConfig.getButtonBorderColor();
        int buttonTextColor = RenderConfig.getButtonTextColor();
        int guiBorderFadeColor = RenderConfig.getGuiBorderFadeColor();
        int buttonBorderFadeColor = RenderConfig.getButtonBorderFadeColor();
        int buttonTextFadeColor = RenderConfig.getButtonTextFadeColor();
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
        int hudCornerRadius;
        // Camera
        float freecamSpeed = 1.0f;
        float freelookDistance = 4.0f;
        boolean f5DisableFront;
        boolean f5DisableBack;
        float f5CameraDistance = 4.0f;
        // F5 Customizer toggles default to off
        boolean f5ScrollEnabled;
        boolean f5ResetOnToggle;
        // Hiders
        boolean hiderNoHurtCamera;
        boolean hiderRemoveFireOverlay;
        boolean hiderDisableHungerBar;
        boolean hiderHidePotionEffects;
        boolean hiderThirdPersonCrosshair;
        boolean hiderHideEntityFire;
        boolean hiderDisableAttachedArrows;
        boolean hiderRemoveFallingBlocks;
        boolean hiderRemoveExplosionParticles;
        boolean hiderRemoveTabPing;
        String hiderNoArmorMode;
        // Animations
        boolean animEnabled;
        int animPosX;
        int animPosY;
        int animPosZ;
        int animRotX;
        int animRotY;
        int animRotZ;
        float animScale = 1.0f;
        int animSwingDuration = 6;
        boolean animCancelReEquip;
        boolean animHidePlayerHand;
        boolean animClassicClick;
        // ClickGUI
        Map<String, int[]> clickGuiPanels;
    }
}
