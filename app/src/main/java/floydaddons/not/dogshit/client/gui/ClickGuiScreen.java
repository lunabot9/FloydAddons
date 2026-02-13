package floydaddons.not.dogshit.client.gui;
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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClickGuiScreen extends Screen {
    private static final int HEADER_HEIGHT = 24;
    private static final int MODULE_HEIGHT = 20;
    private static final int SEARCH_BAR_HEIGHT = 22;
    private static final int SEARCH_BAR_WIDTH = 200;
    private static final long FADE_DURATION_MS = 120;

    private static final int POPUP_TITLE_HEIGHT = 20;
    private static final int POPUP_SETTING_HEIGHT = 18;
    private static final int POPUP_SLIDER_HEIGHT = 28;
    private static final int POPUP_COLOR_COLLAPSED_HEIGHT = 18;
    private static final int POPUP_COLOR_EXPANDED_HEIGHT = 160;
    private static final int POPUP_PADDING = 6;

    private static final int FILTER_MAX_VISIBLE_HEIGHT = 180;
    private static final int FILTER_ENTRY_HEIGHT = 16;
    private static final int FILTER_SEARCH_HEIGHT = 18;
    private static final int FILTER_BTN_W = 16;
    private static final int FILTER_BTN_H = 14;

    private final Map<ModuleCategory, List<ModuleEntry>> modules = new LinkedHashMap<>();
    private final Map<ModuleCategory, Integer> panelWidths = new EnumMap<>(ModuleCategory.class);
    private ModuleCategory draggingPanel = null;
    private int dragOffsetX, dragOffsetY;
    private String searchQuery = "";
    private boolean searchFocused = false;

    private ModuleEntry.TextSetting editingText = null;
    private String textEditBuffer = "";

    private ModuleEntry popupModule = null;
    private ModuleCategory popupCategory = null;
    private int popupX, popupY, popupWidth, popupHeight;
    private boolean draggingPopup = false;
    private int popupDragOffsetX, popupDragOffsetY;
    private ModuleEntry.SliderSetting draggingSlider = null;
    private int draggingSliderX, draggingSliderWidth;

    private ModuleEntry.ColorSetting expandedColorSetting = null;
    private float inlineHue, inlineSat, inlineVal;
    private boolean draggingSV = false, draggingHue = false;
    private boolean inlineEditingFadeColor = false;

    private ModuleEntry.SubSetting expandedFilterSetting = null;
    private String filterSearchQuery = "";
    private boolean filterSearchFocused = false;
    private int filterScrollOffset = 0;
    private int filterMaxScroll = 0;
    private String pendingNameMappingOriginal = null;
    private List<String> cachedActiveBlocks = new ArrayList<>();
    private List<String> cachedNearbyBlocks = new ArrayList<>();
    private List<String> cachedMobActiveNames = new ArrayList<>();
    private List<String> cachedMobActiveTypes = new ArrayList<>();
    private List<String> cachedMobNearbyNames = new ArrayList<>();
    private List<String> cachedMobNearbyTypes = new ArrayList<>();
    private List<Map.Entry<String, String>> cachedNameMappings = new ArrayList<>();
    private List<String> cachedOnlinePlayers = new ArrayList<>();
    private List<String> cachedStalkPlayers = Collections.emptyList();
    private final Map<String, LivingEntity> cachedMobEntities = new HashMap<>();
    private final List<FilterHitEntry> filterHitEntries = new ArrayList<>();
    private final Set<String> revealedMappingNames = new java.util.HashSet<>();
    private String nameFilterOriginalQuery = "";
    private String nameFilterFakeQuery = "";
    private boolean nameFilterOriginalFocused = false;
    private boolean nameFilterFakeFocused = false;
    private int filterListTop, filterListBottom, filterListLeft, filterListRight;

    // Inline filter color picker state (Mob ESP)
    private String expandedFilterColorKey = null;
    private float filterColorHue, filterColorSat, filterColorVal;
    private boolean filterColorDraggingSV, filterColorDraggingHue;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing;

    public ClickGuiScreen() {
        super(Text.literal("ClickGUI"));
    }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;
        searchQuery = "";
        searchFocused = false;
        draggingSlider = null;
        editingText = null;
        textEditBuffer = "";
        popupModule = null;
        popupCategory = null;
        expandedColorSetting = null;
        inlineEditingFadeColor = false;
        expandedFilterSetting = null;
        filterSearchQuery = "";
        filterSearchFocused = false;
        filterScrollOffset = 0;
        pendingNameMappingOriginal = null;
        expandedFilterColorKey = null;
        filterColorDraggingSV = false;
        filterColorDraggingHue = false;
        revealedMappingNames.clear();
        nameFilterOriginalQuery = "";
        nameFilterFakeQuery = "";
        nameFilterOriginalFocused = false;
        nameFilterFakeFocused = false;
        initModules();
    }

    private void initModules() {
        modules.clear();
        ClickGuiScreen self = this;

        // ═══════════════════════ RENDER ═══════════════════════

        List<ModuleEntry> render = new ArrayList<>();

        render.add(new ModuleEntry("X-Ray", "Toggle X-Ray vision",
                RenderConfig::isXrayEnabled, RenderConfig::toggleXray,
                List.of(
                        new ModuleEntry.SliderSetting("Opacity", RenderConfig::getXrayOpacity,
                                RenderConfig::setXrayOpacity, 0.05f, 1.0f, "%.0f%%") {
                            @Override public String getFormattedValue() { return String.format("%.0f%%", getValue() * 100); }
                        },
                        new ModuleEntry.BlockFilterSetting("Edit Blocks")
                )));

        render.add(new ModuleEntry("Mob ESP", "Highlight mobs through walls",
                RenderConfig::isMobEspEnabled, RenderConfig::toggleMobEsp,
                List.of(
                        new ModuleEntry.BooleanSetting("Tracers", RenderConfig::isMobEspTracers,
                                () -> RenderConfig.setMobEspTracers(!RenderConfig.isMobEspTracers())),
                        new ModuleEntry.BooleanSetting("Hitboxes", RenderConfig::isMobEspHitboxes,
                                () -> RenderConfig.setMobEspHitboxes(!RenderConfig.isMobEspHitboxes())),
                        new ModuleEntry.BooleanSetting("Star Mobs", RenderConfig::isMobEspStarMobs,
                                () -> RenderConfig.setMobEspStarMobs(!RenderConfig.isMobEspStarMobs())),
                        new ModuleEntry.ColorSetting("Default ESP Color",
                                RenderConfig::getDefaultEspColor, RenderConfig::setDefaultEspColor,
                                RenderConfig::isDefaultEspChromaEnabled, RenderConfig::setDefaultEspChromaEnabled),
                        new ModuleEntry.MobFilterSetting("Edit Filters")
                )));

        render.add(new ModuleEntry("Server ID Hider", "Hide server address display",
                RenderConfig::isServerIdHiderEnabled,
                () -> RenderConfig.setServerIdHiderEnabled(!RenderConfig.isServerIdHiderEnabled())));

        render.add(new ModuleEntry("Time Changer", "Client-side time override",
                RenderConfig::isCustomTimeEnabled,
                () -> RenderConfig.setCustomTimeEnabled(!RenderConfig.isCustomTimeEnabled()),
                List.of(new ModuleEntry.SliderSetting("Time", RenderConfig::getCustomTimeValue,
                        RenderConfig::setCustomTimeValue, 0f, 100f, "%.0f"))));

        render.add(new ModuleEntry("Stalk Player", "Track a player with a tracer line",
                StalkManager::isEnabled,
                () -> { if (StalkManager.isEnabled()) StalkManager.disable(); },
                List.of(
                        new ModuleEntry.PlayerPickerSetting("Target", StalkManager::getTargetName,
                                name -> { if (name == null || name.isBlank()) StalkManager.disable(); else StalkManager.setTarget(name); }),
                        new ModuleEntry.ColorSetting("Tracer Color",
                                RenderConfig::getStalkTracerColor, RenderConfig::setStalkTracerColor,
                                RenderConfig::isStalkTracerChromaEnabled, RenderConfig::setStalkTracerChromaEnabled)
                )));

        render.add(new ModuleEntry("Inventory HUD", "Show inventory overlay",
                RenderConfig::isInventoryHudEnabled,
                () -> RenderConfig.setInventoryHudEnabled(!RenderConfig.isInventoryHudEnabled()),
                List.of(new ModuleEntry.ButtonSetting("Edit Layout",
                        () -> MinecraftClient.getInstance().setScreen(new MoveHudScreen(self))))));

        render.add(new ModuleEntry("Custom Scoreboard", "Styled scoreboard sidebar",
                RenderConfig::isCustomScoreboardEnabled,
                () -> RenderConfig.setCustomScoreboardEnabled(!RenderConfig.isCustomScoreboardEnabled()),
                List.of(new ModuleEntry.ButtonSetting("Edit Layout",
                        () -> MinecraftClient.getInstance().setScreen(new MoveHudScreen(self))))));

        render.add(new ModuleEntry("GUI Style", "Customize UI colors and chroma",
                () -> RenderConfig.isButtonTextChromaEnabled() || RenderConfig.isButtonBorderChromaEnabled() || RenderConfig.isGuiBorderChromaEnabled()
                        || RenderConfig.isButtonTextFadeEnabled() || RenderConfig.isButtonBorderFadeEnabled() || RenderConfig.isGuiBorderFadeEnabled(),
                () -> {},
                List.of(
                        new ModuleEntry.FadingColorSetting("Text Color",
                                RenderConfig::getButtonTextColor, RenderConfig::setButtonTextColor,
                                RenderConfig::isButtonTextChromaEnabled, RenderConfig::setButtonTextChromaEnabled,
                                RenderConfig::getButtonTextFadeColor, RenderConfig::setButtonTextFadeColor,
                                RenderConfig::isButtonTextFadeEnabled, RenderConfig::setButtonTextFadeEnabled),
                        new ModuleEntry.FadingColorSetting("Button Border Color",
                                RenderConfig::getButtonBorderColor, RenderConfig::setButtonBorderColor,
                                RenderConfig::isButtonBorderChromaEnabled, RenderConfig::setButtonBorderChromaEnabled,
                                RenderConfig::getButtonBorderFadeColor, RenderConfig::setButtonBorderFadeColor,
                                RenderConfig::isButtonBorderFadeEnabled, RenderConfig::setButtonBorderFadeEnabled),
                        new ModuleEntry.FadingColorSetting("GUI Border Color",
                                RenderConfig::getGuiBorderColor, RenderConfig::setGuiBorderColor,
                                RenderConfig::isGuiBorderChromaEnabled, RenderConfig::setGuiBorderChromaEnabled,
                                RenderConfig::getGuiBorderFadeColor, RenderConfig::setGuiBorderFadeColor,
                                RenderConfig::isGuiBorderFadeEnabled, RenderConfig::setGuiBorderFadeEnabled)
                )));

        render.add(new ModuleEntry("Attack Animation", "Custom held item animations",
                AnimationConfig::isEnabled,
                () -> AnimationConfig.setEnabled(!AnimationConfig.isEnabled()),
                List.of(
                        new ModuleEntry.SliderSetting("Pos X", () -> (float) AnimationConfig.getPosX(),
                                v -> AnimationConfig.setPosX(Math.round(v)), -150f, 150f, "%.0f"),
                        new ModuleEntry.SliderSetting("Pos Y", () -> (float) AnimationConfig.getPosY(),
                                v -> AnimationConfig.setPosY(Math.round(v)), -150f, 150f, "%.0f"),
                        new ModuleEntry.SliderSetting("Pos Z", () -> (float) AnimationConfig.getPosZ(),
                                v -> AnimationConfig.setPosZ(Math.round(v)), -150f, 50f, "%.0f"),
                        new ModuleEntry.SliderSetting("Rot X", () -> (float) AnimationConfig.getRotX(),
                                v -> AnimationConfig.setRotX(Math.round(v)), -180f, 180f, "%.0f"),
                        new ModuleEntry.SliderSetting("Rot Y", () -> (float) AnimationConfig.getRotY(),
                                v -> AnimationConfig.setRotY(Math.round(v)), -180f, 180f, "%.0f"),
                        new ModuleEntry.SliderSetting("Rot Z", () -> (float) AnimationConfig.getRotZ(),
                                v -> AnimationConfig.setRotZ(Math.round(v)), -180f, 180f, "%.0f"),
                        new ModuleEntry.SliderSetting("Scale", AnimationConfig::getScale,
                                AnimationConfig::setScale, 0.1f, 2.0f, "%.2f"),
                        new ModuleEntry.SliderSetting("Swing Duration", () -> (float) AnimationConfig.getSwingDuration(),
                                v -> AnimationConfig.setSwingDuration(Math.round(v)), 1f, 100f, "%.0f"),
                        new ModuleEntry.BooleanSetting("Cancel Re-Equip", AnimationConfig::isCancelReEquip,
                                () -> AnimationConfig.setCancelReEquip(!AnimationConfig.isCancelReEquip())),
                        new ModuleEntry.BooleanSetting("Hide Hand", AnimationConfig::isHidePlayerHand,
                                () -> AnimationConfig.setHidePlayerHand(!AnimationConfig.isHidePlayerHand())),
                        new ModuleEntry.BooleanSetting("Classic Click", AnimationConfig::isClassicClick,
                                () -> AnimationConfig.setClassicClick(!AnimationConfig.isClassicClick()))
                )));

        modules.put(ModuleCategory.RENDER, render);

        // ═══════════════════════ HIDERS ═══════════════════════

        List<ModuleEntry> hiders = new ArrayList<>();
        hiders.add(new ModuleEntry("No Hurt Camera", "Remove damage camera shake",
                HidersConfig::isNoHurtCameraEnabled, () -> HidersConfig.setNoHurtCameraEnabled(!HidersConfig.isNoHurtCameraEnabled())));
        hiders.add(new ModuleEntry("Remove Fire Overlay", "Hide fire screen overlay",
                HidersConfig::isRemoveFireOverlayEnabled, () -> HidersConfig.setRemoveFireOverlayEnabled(!HidersConfig.isRemoveFireOverlayEnabled())));
        hiders.add(new ModuleEntry("Disable Hunger Bar", "Hide hunger display",
                HidersConfig::isDisableHungerBarEnabled, () -> HidersConfig.setDisableHungerBarEnabled(!HidersConfig.isDisableHungerBarEnabled())));
        hiders.add(new ModuleEntry("Hide Potion Effects", "Hide potion effect icons",
                HidersConfig::isHidePotionEffectsEnabled, () -> HidersConfig.setHidePotionEffectsEnabled(!HidersConfig.isHidePotionEffectsEnabled())));
        hiders.add(new ModuleEntry("3rd Person Crosshair", "Show crosshair in 3rd person",
                HidersConfig::isThirdPersonCrosshairEnabled, () -> HidersConfig.setThirdPersonCrosshairEnabled(!HidersConfig.isThirdPersonCrosshairEnabled())));
        hiders.add(new ModuleEntry("Hide Entity Fire", "Hide fire on entities",
                HidersConfig::isHideEntityFireEnabled, () -> HidersConfig.setHideEntityFireEnabled(!HidersConfig.isHideEntityFireEnabled())));
        hiders.add(new ModuleEntry("Disable Arrows", "Hide arrows stuck in models",
                HidersConfig::isDisableAttachedArrowsEnabled, () -> HidersConfig.setDisableAttachedArrowsEnabled(!HidersConfig.isDisableAttachedArrowsEnabled())));
        hiders.add(new ModuleEntry("Remove Falling Blocks", "Hide falling block entities",
                HidersConfig::isRemoveFallingBlocksEnabled, () -> HidersConfig.setRemoveFallingBlocksEnabled(!HidersConfig.isRemoveFallingBlocksEnabled())));
        hiders.add(new ModuleEntry("No Explosion Particles", "Hide explosion particles",
                HidersConfig::isRemoveExplosionParticlesEnabled, () -> HidersConfig.setRemoveExplosionParticlesEnabled(!HidersConfig.isRemoveExplosionParticlesEnabled())));
        hiders.add(new ModuleEntry("Remove Tab Ping", "Hide ping icons in tab list",
                HidersConfig::isRemoveTabPingEnabled, () -> HidersConfig.setRemoveTabPingEnabled(!HidersConfig.isRemoveTabPingEnabled())));
        hiders.add(new ModuleEntry("No Armor", "Hide armor on players",
                HidersConfig::isNoArmorEnabled,
                () -> {
                    String cur = HidersConfig.getNoArmorMode();
                    HidersConfig.setNoArmorMode("OFF".equals(cur) ? "SELF" : "OFF");
                },
                List.of(
                        new ModuleEntry.CycleSetting("Target",
                                () -> List.of("OFF", "SELF", "OTHERS", "ALL"),
                                HidersConfig::getNoArmorMode,
                                m -> { HidersConfig.setNoArmorMode(m); HidersConfig.save(); })
                )));
        modules.put(ModuleCategory.HIDERS, hiders);

        // ═══════════════════════ PLAYER ═══════════════════════

        List<ModuleEntry> player = new ArrayList<>();

        player.add(new ModuleEntry("Cape", "Custom cape cosmetic",
                RenderConfig::isCapeEnabled,
                () -> { RenderConfig.setCapeEnabled(!RenderConfig.isCapeEnabled()); FloydAddonsConfig.save(); },
                List.of(
                        new ModuleEntry.CycleSetting("Image",
                                () -> CapeManager.listAvailableImages(true),
                                RenderConfig::getSelectedCapeImage,
                                img -> { RenderConfig.setSelectedCapeImage(img); RenderConfig.save(); }),
                        new ModuleEntry.ButtonSetting("Open Folder",
                                () -> openPath(CapeManager.ensureDir()))
                )));

        player.add(new ModuleEntry("Cone Hat", "Floyd cone hat cosmetic",
                RenderConfig::isFloydHatEnabled,
                () -> RenderConfig.setFloydHatEnabled(!RenderConfig.isFloydHatEnabled()),
                List.of(
                        new ModuleEntry.SliderSetting("Height", RenderConfig::getConeHatHeight,
                                RenderConfig::setConeHatHeight, 0.1f, 1.5f, "%.2f"),
                        new ModuleEntry.SliderSetting("Radius", RenderConfig::getConeHatRadius,
                                RenderConfig::setConeHatRadius, 0.05f, 0.8f, "%.2f"),
                        new ModuleEntry.SliderSetting("Y Offset", RenderConfig::getConeHatYOffset,
                                RenderConfig::setConeHatYOffset, -1.5f, 0.5f, "%.2f"),
                        new ModuleEntry.SliderSetting("Rotation", RenderConfig::getConeHatRotation,
                                RenderConfig::setConeHatRotation, 0f, 360f, "%.0f"),
                        new ModuleEntry.SliderSetting("Spin Speed", RenderConfig::getConeHatRotationSpeed,
                                RenderConfig::setConeHatRotationSpeed, 0f, 360f, "%.0f"),
                        new ModuleEntry.CycleSetting("Image",
                                ConeHatManager::listAvailableImages,
                                RenderConfig::getSelectedConeImage,
                                img -> { RenderConfig.setSelectedConeImage(img); RenderConfig.save(); ConeHatManager.clearCache(); }),
                        new ModuleEntry.ButtonSetting("Open Folder",
                                () -> openPath(ConeHatManager.ensureDir()))
                )));

        player.add(new ModuleEntry("Neck Hider", "Hide/replace player names",
                NickHiderConfig::isEnabled,
                () -> NickHiderConfig.setEnabled(!NickHiderConfig.isEnabled()),
                List.of(
                        new ModuleEntry.TextSetting("Default Nick", NickHiderConfig::getNickname,
                                nick -> { NickHiderConfig.setNickname(nick); NickHiderConfig.save(); }),
                        new ModuleEntry.NameFilterSetting("Edit Names"),
                        new ModuleEntry.ButtonSetting("Reload Names",
                                () -> NickHiderConfig.loadNameMappings())
                )));

        player.add(new ModuleEntry("Custom Skin", "Enable custom skin system",
                SkinConfig::customEnabled,
                () -> { SkinConfig.setCustomEnabled(!SkinConfig.customEnabled()); FloydAddonsConfig.save(); },
                List.of(
                        new ModuleEntry.BooleanSetting("Self", SkinConfig::selfEnabled,
                                () -> SkinConfig.setSelfEnabled(!SkinConfig.selfEnabled())),
                        new ModuleEntry.BooleanSetting("Others", SkinConfig::othersEnabled,
                                () -> SkinConfig.setOthersEnabled(!SkinConfig.othersEnabled())),
                        new ModuleEntry.CycleSetting("Skin",
                                SkinManager::listAvailableSkins,
                                SkinConfig::getSelectedSkin,
                                skin -> { SkinConfig.setSelectedSkin(skin); SkinConfig.save(); SkinManager.clearCache(); }),
                        new ModuleEntry.ButtonSetting("Open Folder",
                                () -> openPath(SkinManager.ensureExternalDir()))
                )));

        player.add(new ModuleEntry("Player Size", "Change player model scale (XYZ)",
                () -> SkinConfig.getPlayerScaleX() != 1.0f || SkinConfig.getPlayerScaleY() != 1.0f || SkinConfig.getPlayerScaleZ() != 1.0f,
                () -> {
                    if (SkinConfig.getPlayerScaleX() != 1.0f || SkinConfig.getPlayerScaleY() != 1.0f || SkinConfig.getPlayerScaleZ() != 1.0f) {
                        SkinConfig.setPlayerScale(1.0f);
                    } else {
                        SkinConfig.setPlayerScale(2.0f);
                    }
                },
                List.of(
                        new ModuleEntry.CycleSetting("Target",
                                () -> List.of("Self", "Real Players", "All"),
                                SkinConfig::getPlayerSizeTarget,
                                t -> { SkinConfig.setPlayerSizeTarget(t); SkinConfig.save(); }),
                        new ModuleEntry.SliderSetting("X", SkinConfig::getPlayerScaleX,
                                SkinConfig::setPlayerScaleX, -1.0f, 5.0f, "%.1f"),
                        new ModuleEntry.SliderSetting("Y", SkinConfig::getPlayerScaleY,
                                SkinConfig::setPlayerScaleY, -1.0f, 5.0f, "%.1f"),
                        new ModuleEntry.SliderSetting("Z", SkinConfig::getPlayerScaleZ,
                                SkinConfig::setPlayerScaleZ, -1.0f, 5.0f, "%.1f")
                )));

        modules.put(ModuleCategory.PLAYER, player);

        // ═══════════════════════ CAMERA ═══════════════════════

        List<ModuleEntry> camera = new ArrayList<>();
        camera.add(new ModuleEntry("Freecam", "Detached spectator camera",
                CameraConfig::isFreecamEnabled, CameraConfig::toggleFreecam,
                List.of(new ModuleEntry.SliderSetting("Speed", CameraConfig::getFreecamSpeed,
                        CameraConfig::setFreecamSpeed, 0.1f, 10.0f, "%.1f"))));
        camera.add(new ModuleEntry("Freelook", "Orbit camera around player",
                CameraConfig::isFreelookEnabled, CameraConfig::toggleFreelook,
                List.of(new ModuleEntry.SliderSetting("Distance", CameraConfig::getFreelookDistance,
                        CameraConfig::setFreelookDistance, 1.0f, 20.0f, "%.1f"))));
        camera.add(new ModuleEntry("F5 Customizer", "Customize third-person camera",
                () -> CameraConfig.isF5DisableFront() || CameraConfig.isF5DisableBack()
                        || CameraConfig.getF5CameraDistance() != 4.0f || CameraConfig.isF5ScrollEnabled(),
                () -> {},
                List.of(
                        new ModuleEntry.BooleanSetting("Disable Front Cam", CameraConfig::isF5DisableFront,
                                () -> { CameraConfig.setF5DisableFront(!CameraConfig.isF5DisableFront()); FloydAddonsConfig.save(); }),
                        new ModuleEntry.BooleanSetting("Disable Back Cam", CameraConfig::isF5DisableBack,
                                () -> { CameraConfig.setF5DisableBack(!CameraConfig.isF5DisableBack()); FloydAddonsConfig.save(); }),
                        new ModuleEntry.BooleanSetting("Scrolling Changes Distance", CameraConfig::isF5ScrollEnabled,
                                () -> { CameraConfig.setF5ScrollEnabled(!CameraConfig.isF5ScrollEnabled()); FloydAddonsConfig.save(); }),
                        new ModuleEntry.BooleanSetting("Reset F5 Scrolling", CameraConfig::isF5ResetOnToggle,
                                () -> { CameraConfig.setF5ResetOnToggle(!CameraConfig.isF5ResetOnToggle()); FloydAddonsConfig.save(); }),
                        new ModuleEntry.SliderSetting("Camera Distance", CameraConfig::getF5CameraDistance,
                                CameraConfig::setF5CameraDistance, 1.0f, 20.0f, "%.1f")
                )));
        modules.put(ModuleCategory.CAMERA, camera);
    }

    // --- Text Edit ---

    private void finishTextEdit() {
        if (editingText != null) {
            if (!textEditBuffer.isEmpty()) editingText.setValue(textEditBuffer);
            FloydAddonsConfig.save();
            editingText = null;
            textEditBuffer = "";
        }
    }

    // --- Color helpers (respect 3 GUI color settings) ---

    private int getButtonBorderAccent() {
        return RenderConfig.getButtonBorderLiveColor((System.currentTimeMillis() % 4000) / 4000f);
    }

    private int getTextAccent() {
        return RenderConfig.getButtonTextLiveColor((System.currentTimeMillis() % 4000) / 4000f);
    }

    // --- Panel width ---

    private int calculatePanelWidth(ModuleCategory category, List<ModuleEntry> entries) {
        int max = textRenderer.getWidth(category.getDisplayName()) + 30;
        for (ModuleEntry e : filterModules(entries))
            max = Math.max(max, textRenderer.getWidth(e.getName()) + 30);
        return Math.max(max, 100);
    }

    private int getPanelWidth(ModuleCategory category) {
        Integer w = panelWidths.get(category);
        return w != null ? w : 150;
    }

    // --- Setting height ---

    private int getSettingHeight(ModuleEntry.SubSetting s) {
        if (s instanceof ModuleEntry.SliderSetting) return POPUP_SLIDER_HEIGHT;
        if (s instanceof ModuleEntry.ColorSetting cs)
            return cs == expandedColorSetting ? POPUP_COLOR_EXPANDED_HEIGHT : POPUP_COLOR_COLLAPSED_HEIGHT;
        if ((s instanceof ModuleEntry.BlockFilterSetting || s instanceof ModuleEntry.MobFilterSetting || s instanceof ModuleEntry.NameFilterSetting || s instanceof ModuleEntry.PlayerPickerSetting) && s == expandedFilterSetting)
            return POPUP_SETTING_HEIGHT + FILTER_SEARCH_HEIGHT + getFilterVisibleHeight();
        return POPUP_SETTING_HEIGHT;
    }

    private int getFilterVisibleHeight() {
        return Math.min(FILTER_MAX_VISIBLE_HEIGHT, getFilterTotalContentHeight());
    }

    private int getFilterTotalContentHeight() {
        if (expandedFilterSetting instanceof ModuleEntry.BlockFilterSetting) {
            List<String> activeF = filterBySearch(cachedActiveBlocks);
            int count = 0;
            if (!activeF.isEmpty()) count += 1 + activeF.size();
            count += 1 + filterBySearch(cachedNearbyBlocks).size();
            return count * FILTER_ENTRY_HEIGHT;
        } else if (expandedFilterSetting instanceof ModuleEntry.MobFilterSetting) {
            List<String> namesF = filterBySearch(cachedMobActiveNames);
            List<String> typesF = filterBySearch(cachedMobActiveTypes);
            int count = 0;
            if (!namesF.isEmpty()) count += 1 + namesF.size();
            if (!typesF.isEmpty()) count += 1 + typesF.size();
            count += 1 + filterBySearch(cachedMobNearbyNames).size() + 1 + filterBySearch(cachedMobNearbyTypes).size();
            int extra = 0;
            if (expandedFilterColorKey != null) extra = 80;
            return count * FILTER_ENTRY_HEIGHT + extra;
        } else if (expandedFilterSetting instanceof ModuleEntry.NameFilterSetting) {
            List<String> activeKeys = new ArrayList<>();
            for (var e : cachedNameMappings) activeKeys.add(e.getKey());
            List<String> activeF = filterBySearch(activeKeys);
            int count = 0;
            if (!activeF.isEmpty()) count += 1 + activeF.size();
            count += 1 + filterBySearch(cachedOnlinePlayers).size();
            return count * FILTER_ENTRY_HEIGHT;
        } else if (expandedFilterSetting instanceof ModuleEntry.PlayerPickerSetting) {
            return Math.max(1, filterBySearch(cachedStalkPlayers).size()) * FILTER_ENTRY_HEIGHT;
        }
        return 0;
    }

    // --- Rendering ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float openProgress = Math.min(1.0f, (Util.getMeasuringTimeMs() - openStartMs) / (float) FADE_DURATION_MS);
        float closeProgress = closing ? Math.min(1.0f, (Util.getMeasuringTimeMs() - closeStartMs) / (float) FADE_DURATION_MS) : 0f;
        if (closing && closeProgress >= 1.0f) { super.close(); return; }
        float alpha = closing ? (1.0f - closeProgress) : openProgress;
        if (alpha <= 0f) return;

        for (var entry : modules.entrySet())
            panelWidths.put(entry.getKey(), calculatePanelWidth(entry.getKey(), entry.getValue()));

        context.fill(0, 0, width, height, applyAlpha(0x88000000, alpha));
        renderSearchBar(context, mouseX, mouseY, alpha);

        for (var entry : modules.entrySet())
            renderPanel(context, entry.getKey(), entry.getValue(), mouseX, mouseY, alpha);

        if (popupModule != null && popupCategory != null) {
            renderPopup(context, mouseX, mouseY, alpha);
            // Player preview next to Cape/Cone Hat popup
            if (isPlayerPreviewModule(popupModule))
                renderPlayerPreview(context, mouseX, mouseY, alpha);
        }

        // FloydAddons title at bottom
        renderBottomTitle(context, alpha);
    }

    private void renderSearchBar(DrawContext context, int mouseX, int mouseY, float alpha) {
        int x = (width - SEARCH_BAR_WIDTH) / 2;
        int y = 4;
        context.fill(x, y, x + SEARCH_BAR_WIDTH, y + SEARCH_BAR_HEIGHT, applyAlpha(0xFF111111, alpha));
        drawSolidBorder(context, x - 1, y - 1, x + SEARCH_BAR_WIDTH + 1, y + SEARCH_BAR_HEIGHT + 1,
                applyAlpha(getButtonBorderAccent(), alpha));
        String display = searchQuery.isEmpty() && !searchFocused ? "Search..." : searchQuery + (searchFocused ? "_" : "");
        int textColor = searchQuery.isEmpty() && !searchFocused ? applyAlpha(0xFF888888, alpha) : applyAlpha(0xFFFFFFFF, alpha);
        context.drawTextWithShadow(textRenderer, display, x + 6, y + (SEARCH_BAR_HEIGHT - textRenderer.fontHeight) / 2, textColor);
    }

    private void renderPanel(DrawContext context, ModuleCategory category, List<ModuleEntry> entries,
                             int mouseX, int mouseY, float alpha) {
        ClickGuiConfig.PanelState state = ClickGuiConfig.getState(category);
        int px = state.x, py = state.y, pw = getPanelWidth(category);
        List<ModuleEntry> filtered = filterModules(entries);
        int contentHeight = filtered.size() * MODULE_HEIGHT;
        int totalHeight = HEADER_HEIGHT + (state.collapsed ? 0 : contentHeight);

        context.fill(px, py, px + pw, py + totalHeight, applyAlpha(0xFF111111, alpha));
        renderHeader(context, category, px, py, pw, mouseX, mouseY, alpha, state.collapsed);

        if (!state.collapsed && !filtered.isEmpty()) {
            int contentTop = py + HEADER_HEIGHT;
            context.enableScissor(px, contentTop, px + pw, py + totalHeight);
            int moduleY = contentTop - (int) state.scrollOffset;
            for (ModuleEntry entry : filtered) {
                renderModule(context, entry, px, moduleY, pw, mouseX, mouseY, alpha);
                moduleY += MODULE_HEIGHT;
            }
            context.disableScissor();
        }

        drawSolidBorder(context, px - 1, py - 1, px + pw + 1, py + totalHeight + 1,
                applyAlpha(getButtonBorderAccent(), alpha));
    }

    private void renderHeader(DrawContext context, ModuleCategory category, int x, int y, int pw,
                               int mouseX, int mouseY, float alpha, boolean collapsed) {
        boolean hover = mouseX >= x && mouseX <= x + pw && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        context.fill(x, y, x + pw, y + HEADER_HEIGHT, applyAlpha(getButtonBorderAccent(), alpha));
        String name = category.getDisplayName() + (collapsed ? " [+]" : "");
        // White normally, text accent on hover
        int textColor = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFFFFFFF, alpha);
        context.drawTextWithShadow(textRenderer, name, x + 8,
                y + (HEADER_HEIGHT - textRenderer.fontHeight) / 2, textColor);
    }

    private void renderModule(DrawContext context, ModuleEntry entry, int px, int y, int pw,
                               int mouseX, int mouseY, float alpha) {
        boolean hover = mouseX >= px && mouseX <= px + pw && mouseY >= y && mouseY <= y + MODULE_HEIGHT;
        if (hover) context.fill(px, y, px + pw, y + MODULE_HEIGHT, applyAlpha(0xFF1A1A1A, alpha));

        // Enabled modules use text accent color, disabled use white, hover always accent
        int nameColor;
        if (hover) nameColor = applyAlpha(getTextAccent(), alpha);
        else if (entry.isEnabled()) nameColor = applyAlpha(getTextAccent(), alpha);
        else nameColor = applyAlpha(0xFFFFFFFF, alpha);
        context.drawTextWithShadow(textRenderer, entry.getName(), px + 8,
                y + (MODULE_HEIGHT - textRenderer.fontHeight) / 2, nameColor);

        // Dot: accent when enabled, gray when disabled
        int dotSize = 5;
        int dotX = px + pw - dotSize - 8;
        int dotY = y + (MODULE_HEIGHT - dotSize) / 2;
        int dotColor = entry.isEnabled() ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFF555555, alpha);
        context.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, dotColor);
    }

    // --- Popup ---

    private void openPopup(ModuleEntry module, ModuleCategory category, int panelX, int panelY, int panelWidth, int moduleRowY) {
        if (popupModule == module) { closePopup(); return; }
        popupModule = module;
        popupCategory = category;
        expandedColorSetting = null;
        expandedFilterSetting = null;
        editingText = null;
        textEditBuffer = "";
        filterSearchQuery = "";
        filterSearchFocused = false;
        filterScrollOffset = 0;

        popupWidth = calculatePopupWidth(module);
        popupHeight = calculatePopupHeight(module);
        popupX = panelX + panelWidth + 4;
        popupY = moduleRowY;
        if (popupX + popupWidth > width) popupX = panelX - popupWidth - 4;
        if (popupY + popupHeight > height) popupY = height - popupHeight - 4;
        if (popupY < 0) popupY = 4;
    }

    private void closePopup() {
        finishTextEdit();
        popupModule = null;
        popupCategory = null;
        expandedColorSetting = null;
        inlineEditingFadeColor = false;
        expandedFilterSetting = null;
        filterSearchQuery = "";
        filterSearchFocused = false;
        filterScrollOffset = 0;
        pendingNameMappingOriginal = null;
        revealedMappingNames.clear();
        nameFilterOriginalQuery = "";
        nameFilterFakeQuery = "";
        nameFilterOriginalFocused = false;
        nameFilterFakeFocused = false;
        cachedMobEntities.clear();
        draggingSV = false;
        draggingHue = false;
        expandedFilterColorKey = null;
        filterColorDraggingSV = false;
        filterColorDraggingHue = false;
    }

    private int calculatePopupWidth(ModuleEntry module) {
        int max = textRenderer.getWidth(module.getName()) + 40;
        boolean hasFilter = false;
        for (ModuleEntry.SubSetting s : module.getSettings()) {
            max = Math.max(max, textRenderer.getWidth(s.getLabel()) + 80);
            if (s instanceof ModuleEntry.BlockFilterSetting || s instanceof ModuleEntry.MobFilterSetting || s instanceof ModuleEntry.NameFilterSetting || s instanceof ModuleEntry.PlayerPickerSetting) hasFilter = true;
        }
        return Math.max(max, hasFilter ? 250 : 180);
    }

    private int calculatePopupHeight(ModuleEntry module) {
        int h = POPUP_TITLE_HEIGHT;
        for (ModuleEntry.SubSetting s : module.getSettings()) h += getSettingHeight(s);
        return h + POPUP_PADDING;
    }

    private void renderPopup(DrawContext context, int mouseX, int mouseY, float alpha) {
        if (popupModule == null) return;
        popupHeight = calculatePopupHeight(popupModule);
        if (popupY + popupHeight > height) popupY = Math.max(4, height - popupHeight - 4);

        int accent = getButtonBorderAccent();
        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, applyAlpha(0xEE111111, alpha));
        drawSolidBorder(context, popupX - 1, popupY - 1, popupX + popupWidth + 1, popupY + popupHeight + 1,
                applyAlpha(accent, alpha));

        // Title bar: accent background, text accent text
        context.fill(popupX, popupY, popupX + popupWidth, popupY + POPUP_TITLE_HEIGHT, applyAlpha(accent, alpha));
        context.drawTextWithShadow(textRenderer, popupModule.getName(), popupX + 6,
                popupY + (POPUP_TITLE_HEIGHT - textRenderer.fontHeight) / 2, applyAlpha(getTextAccent(), alpha));

        int settingY = popupY + POPUP_TITLE_HEIGHT;
        for (ModuleEntry.SubSetting setting : popupModule.getSettings()) {
            int settingH = getSettingHeight(setting);
            renderPopupSetting(context, setting, popupX, settingY, popupWidth, settingH, mouseX, mouseY, alpha);
            settingY += settingH;
        }
    }

    private void renderPopupSetting(DrawContext context, ModuleEntry.SubSetting setting,
                                     int px, int y, int pw, int h, int mouseX, int mouseY, float alpha) {
        boolean hover = mouseX >= px && mouseX <= px + pw && mouseY >= y && mouseY <= y + Math.min(h, POPUP_SETTING_HEIGHT);

        if (setting instanceof ModuleEntry.BooleanSetting boolSetting) {
            int textColor = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFFFFFFF, alpha);
            context.drawTextWithShadow(textRenderer, setting.getLabel(), px + 10,
                    y + (h - textRenderer.fontHeight) / 2, textColor);
            int dotSize = 5, dotX = px + pw - dotSize - 10, dotY = y + (h - dotSize) / 2;
            context.fill(dotX, dotY, dotX + dotSize, dotY + dotSize,
                    applyAlpha(boolSetting.isEnabled() ? getTextAccent() : 0xFF555555, alpha));

        } else if (setting instanceof ModuleEntry.SliderSetting slider) {
            String label = setting.getLabel() + ": " + slider.getFormattedValue();
            int textColor = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFFFFFFF, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 10, y + 2, textColor);
            int barX = px + 10, barY = y + h - 8, barW = pw - 20;
            context.fill(barX, barY, barX + barW, barY + 4, applyAlpha(0xFF333333, alpha));
            int fillW = (int) (barW * slider.getNormalized());
            context.fill(barX, barY, barX + fillW, barY + 4, applyAlpha(getButtonBorderAccent(), alpha));

        } else if (setting instanceof ModuleEntry.ColorSetting colorSetting) {
            int textColor = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFFFFFFF, alpha);
            context.drawTextWithShadow(textRenderer, setting.getLabel(), px + 10,
                    y + (POPUP_COLOR_COLLAPSED_HEIGHT - textRenderer.fontHeight) / 2, textColor);
            int sqSize = 10, sqX = px + pw - sqSize - 10, sqY = y + (POPUP_COLOR_COLLAPSED_HEIGHT - sqSize) / 2;
            context.fill(sqX, sqY, sqX + sqSize, sqY + sqSize, applyAlpha(colorSetting.getDisplayColor(), alpha));
            drawSolidBorder(context, sqX - 1, sqY - 1, sqX + sqSize + 1, sqY + sqSize + 1, applyAlpha(0xFF666666, alpha));
            if (colorSetting == expandedColorSetting)
                renderInlineColorPicker(context, colorSetting, px, y + POPUP_COLOR_COLLAPSED_HEIGHT, pw, alpha, mouseX, mouseY);

        } else if (setting instanceof ModuleEntry.ButtonSetting) {
            String label = "[" + setting.getLabel() + "]";
            int color = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 10, y + (h - textRenderer.fontHeight) / 2, color);

        } else if (setting instanceof ModuleEntry.CycleSetting cycleSetting) {
            int textColor = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFFFFFFF, alpha);
            context.drawTextWithShadow(textRenderer, setting.getLabel(), px + 10,
                    y + (h - textRenderer.fontHeight) / 2, textColor);
            String value = cycleSetting.getSelected();
            if (value == null || value.isEmpty()) value = "None";
            int maxValW = pw - textRenderer.getWidth(setting.getLabel()) - 30;
            if (maxValW < 20) maxValW = 20;
            while (textRenderer.getWidth(value) > maxValW && value.length() > 3)
                value = value.substring(0, value.length() - 1);
            int valueX = px + pw - textRenderer.getWidth(value) - 10;
            context.drawTextWithShadow(textRenderer, value, valueX,
                    y + (h - textRenderer.fontHeight) / 2,
                    hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha));

        } else if (setting instanceof ModuleEntry.TextSetting textSetting) {
            String label = setting.getLabel() + ": ";
            boolean editing = textSetting == editingText;
            String displayValue = editing ? textEditBuffer : textSetting.getValue();
            if (displayValue == null) displayValue = "";
            int labelW = textRenderer.getWidth(label);
            int maxValW = pw - labelW - 24;
            if (maxValW < 20) maxValW = 20;
            String suffix = editing ? "_" : "";
            while (textRenderer.getWidth(displayValue + suffix) > maxValW && !displayValue.isEmpty())
                displayValue = displayValue.substring(1);
            displayValue += suffix;
            int textColor = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFFFFFFF, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 10, y + (h - textRenderer.fontHeight) / 2, textColor);
            int valueColor = editing ? applyAlpha(0xFFFFFFFF, alpha) : applyAlpha(0xFFAAAAAA, alpha);
            context.drawTextWithShadow(textRenderer, displayValue, px + 10 + labelW,
                    y + (h - textRenderer.fontHeight) / 2, valueColor);
            if (editing) context.fill(px + 10 + labelW, y + h - 2, px + pw - 10, y + h - 1,
                    applyAlpha(getButtonBorderAccent(), alpha));

        } else if (setting instanceof ModuleEntry.PlayerPickerSetting pps) {
            boolean expanded = setting == expandedFilterSetting;
            String currentName = pps.getValue();
            if (currentName == null || currentName.isEmpty()) currentName = "None";
            String label = "[" + setting.getLabel() + ": " + currentName + (expanded ? " \u25BE" : " \u25B8") + "]";
            int color = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 10, y + (POPUP_SETTING_HEIGHT - textRenderer.fontHeight) / 2, color);
            if (expanded) {
                renderInlinePlayerPicker(context, px, y + POPUP_SETTING_HEIGHT, pw, alpha, mouseX, mouseY);
            }

        } else if (setting instanceof ModuleEntry.BlockFilterSetting || setting instanceof ModuleEntry.MobFilterSetting || setting instanceof ModuleEntry.NameFilterSetting) {
            boolean expanded = setting == expandedFilterSetting;
            String label = "[" + setting.getLabel() + (expanded ? " \u25BE" : " \u25B8") + "]";
            int color = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 10, y + (POPUP_SETTING_HEIGHT - textRenderer.fontHeight) / 2, color);
            if (expanded) {
                int filterY = y + POPUP_SETTING_HEIGHT;
                if (setting instanceof ModuleEntry.BlockFilterSetting)
                    renderInlineBlockFilter(context, px, filterY, pw, alpha, mouseX, mouseY);
                else if (setting instanceof ModuleEntry.MobFilterSetting)
                    renderInlineMobFilter(context, px, filterY, pw, alpha, mouseX, mouseY);
                else
                    renderInlineNameFilter(context, px, filterY, pw, alpha, mouseX, mouseY);
            }
        }
    }

    // --- Inline Color Picker ---

    private void renderInlineColorPicker(DrawContext context, ModuleEntry.ColorSetting cs,
                                          int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        int svSize = 100, hueBarW = 10, hueBarH = 100;
        int svX = px + 10, svY = y + 4, hbX = svX + svSize + 8, hbY = svY;
        ModuleEntry.FadingColorSetting fcs = cs instanceof ModuleEntry.FadingColorSetting ? (ModuleEntry.FadingColorSetting) cs : null;
        boolean editingFade = fcs != null && inlineEditingFadeColor;

        if (cs.isChroma()) {
            int flash = RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
            context.fill(svX, svY, svX + svSize, svY + svSize, applyAlpha(0xFF222222, alpha));
            context.fill(svX, svY, svX + svSize, svY + svSize, applyAlpha(flash, alpha * 0.15f));
            context.fill(hbX, hbY, hbX + hueBarW, hbY + hueBarH, applyAlpha(0xFF222222, alpha));
            context.fill(hbX, hbY, hbX + hueBarW, hbY + hueBarH, applyAlpha(flash, alpha * 0.15f));
        } else {
            for (int x = 0; x < svSize; x++) {
                float s = x / (float) (svSize - 1);
                int topC = applyAlpha(Color.HSBtoRGB(inlineHue, s, 1.0f) | 0xFF000000, alpha);
                int botC = applyAlpha(0xFF000000, alpha);
                context.fillGradient(svX + x, svY, svX + x + 1, svY + svSize, topC, botC);
            }
            int cx = svX + (int) (inlineSat * (svSize - 1)), cy = svY + (int) ((1.0f - inlineVal) * (svSize - 1));
            context.fill(cx - 2, cy - 2, cx + 3, cy + 3, applyAlpha(0xFF000000, alpha));
            context.fill(cx - 1, cy - 1, cx + 2, cy + 2, applyAlpha(0xFFFFFFFF, alpha));
            for (int yy = 0; yy < hueBarH; yy++) {
                float h = yy / (float) (hueBarH - 1);
                context.fill(hbX, hbY + yy, hbX + hueBarW, hbY + yy + 1,
                        applyAlpha(Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000, alpha));
            }
            int hyCursor = hbY + (int) (inlineHue * (hueBarH - 1));
            context.fill(hbX - 1, hyCursor - 1, hbX + hueBarW + 1, hyCursor + 2, applyAlpha(0xFFFFFFFF, alpha));
        }

        drawSolidBorder(context, svX - 1, svY - 1, svX + svSize + 1, svY + svSize + 1, applyAlpha(0xFF444444, alpha));
        drawSolidBorder(context, hbX - 1, hbY - 1, hbX + hueBarW + 1, hbY + hueBarH + 1, applyAlpha(0xFF444444, alpha));

        int infoY = svY + svSize + 6;
        int previewColor = cs.isChroma()
                ? RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f)
                : (Color.HSBtoRGB(inlineHue, inlineSat, inlineVal) | 0xFF000000);
        context.fill(px + 10, infoY, px + 26, infoY + 16, applyAlpha(previewColor, alpha));
        String hex = "#" + String.format("%06X", previewColor & 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, hex, px + 32, infoY + 4, applyAlpha(0xFFCCCCCC, alpha));
        String chromaLabel = cs.isChroma() ? "Chroma: ON" : "Chroma: OFF";
        int chromaX = px + pw - textRenderer.getWidth(chromaLabel) - 10;
        boolean hoverChroma = mouseX >= chromaX && mouseX <= px + pw - 4 && mouseY >= infoY && mouseY <= infoY + 16;
        context.drawTextWithShadow(textRenderer, chromaLabel, chromaX, infoY + 4,
                hoverChroma ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha));

        if (fcs != null) {
            int fadeRowY = infoY + 18;
            int sqSize = 12;
            int baseSqX = px + 10;
            int fadeSqX = baseSqX + sqSize + 6;
            int sqY = fadeRowY;

            // Base / Fade preview squares
            context.fill(baseSqX, sqY, baseSqX + sqSize, sqY + sqSize, applyAlpha(fcs.getColor(), alpha));
            context.fill(fadeSqX, sqY, fadeSqX + sqSize, sqY + sqSize, applyAlpha(fcs.getFadeColor(), alpha));
            int selColor = applyAlpha(getButtonBorderAccent(), alpha);
            int dimColor = applyAlpha(0xFF555555, alpha);
            drawSolidBorder(context, baseSqX - 1, sqY - 1, baseSqX + sqSize + 1, sqY + sqSize + 1,
                    editingFade ? dimColor : selColor);
            drawSolidBorder(context, fadeSqX - 1, sqY - 1, fadeSqX + sqSize + 1, sqY + sqSize + 1,
                    editingFade ? selColor : dimColor);

            String fadeLabel = fcs.isFadeEnabled() ? "Fade: ON" : "Fade: OFF";
            int fadeX = px + pw - textRenderer.getWidth(fadeLabel) - 10;
            boolean hoverFade = mouseX >= fadeX && mouseX <= px + pw - 4 && mouseY >= fadeRowY && mouseY <= fadeRowY + 12;
            context.drawTextWithShadow(textRenderer, fadeLabel, fadeX, fadeRowY + 1,
                    hoverFade ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha));
        }
    }

    // --- Inline Block Filter ---

    private void renderInlineBlockFilter(DrawContext context, int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        renderFilterSearchBar(context, px, y, pw, alpha, mouseX, mouseY);
        int listY = y + FILTER_SEARCH_HEIGHT + 2;
        int visibleH = getFilterVisibleHeight();
        filterListTop = listY; filterListBottom = listY + visibleH; filterListLeft = px; filterListRight = px + pw;
        filterHitEntries.clear();

        List<String> activeF = filterBySearch(cachedActiveBlocks);
        List<String> nearbyF = filterBySearch(cachedNearbyBlocks);
        int totalCount = 0;
        if (!activeF.isEmpty()) totalCount += 1 + activeF.size();
        totalCount += 1 + nearbyF.size();
        int totalH = totalCount * FILTER_ENTRY_HEIGHT;
        filterMaxScroll = Math.max(0, totalH - FILTER_MAX_VISIBLE_HEIGHT);
        filterScrollOffset = Math.max(0, Math.min(filterScrollOffset, filterMaxScroll));

        context.enableScissor(px + 4, listY, px + pw - 4, filterListBottom);
        int entryY = listY - filterScrollOffset;
        int entryLeft = px + 6, entryRight = px + pw - 6;
        int accent = getButtonBorderAccent();

        if (!activeF.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Active Blocks", entryLeft, entryY + 2, applyAlpha(0xFFFFFFFF, alpha));
            entryY += FILTER_ENTRY_HEIGHT;
            for (String id : activeF) {
                renderBlockIcon(context, id, entryLeft, entryY);
                int textX = entryLeft + 18;
                String label = truncateLabel(id, entryRight - textX - FILTER_BTN_W - 6);
                context.drawTextWithShadow(textRenderer, label, textX, entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                        applyAlpha(0xFFCCCCCC, alpha));
                int btnX = entryRight - FILTER_BTN_W - 2;
                boolean bh = mouseX >= btnX && mouseX <= btnX + FILTER_BTN_W && mouseY >= entryY && mouseY <= entryY + FILTER_BTN_H
                        && mouseY >= listY && mouseY <= filterListBottom;
                context.fill(btnX, entryY, btnX + FILTER_BTN_W, entryY + FILTER_BTN_H, applyAlpha(bh ? 0xFF993333 : 0xFF553333, alpha));
                context.drawTextWithShadow(textRenderer, "-", btnX + (FILTER_BTN_W - textRenderer.getWidth("-")) / 2,
                        entryY + (FILTER_BTN_H - textRenderer.fontHeight) / 2, applyAlpha(0xFFFFFFFF, alpha));
                if (entryY + FILTER_BTN_H > listY && entryY < filterListBottom)
                    filterHitEntries.add(new FilterHitEntry(btnX, entryY, FILTER_BTN_W, FILTER_BTN_H, id, FilterAction.REMOVE_BLOCK));
                entryY += FILTER_ENTRY_HEIGHT;
            }
        }

        entryY += 2;
        context.drawTextWithShadow(textRenderer, "Nearby Blocks", entryLeft, entryY + 2, applyAlpha(0xFFFFFFFF, alpha));
        entryY += FILTER_ENTRY_HEIGHT;
        for (String id : nearbyF) {
            renderBlockIcon(context, id, entryLeft, entryY);
            int textX = entryLeft + 18;
            String label = truncateLabel(id, entryRight - textX - FILTER_BTN_W - 6);
            context.drawTextWithShadow(textRenderer, label, textX, entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                    applyAlpha(0xFFAAAAAA, alpha));
            int btnX = entryRight - FILTER_BTN_W - 2;
            boolean bh = mouseX >= btnX && mouseX <= btnX + FILTER_BTN_W && mouseY >= entryY && mouseY <= entryY + FILTER_BTN_H
                    && mouseY >= listY && mouseY <= filterListBottom;
            context.fill(btnX, entryY, btnX + FILTER_BTN_W, entryY + FILTER_BTN_H, applyAlpha(bh ? 0xFF339933 : 0xFF335533, alpha));
            context.drawTextWithShadow(textRenderer, "+", btnX + (FILTER_BTN_W - textRenderer.getWidth("+")) / 2,
                    entryY + (FILTER_BTN_H - textRenderer.fontHeight) / 2, applyAlpha(0xFFFFFFFF, alpha));
            if (entryY + FILTER_BTN_H > listY && entryY < filterListBottom)
                filterHitEntries.add(new FilterHitEntry(btnX, entryY, FILTER_BTN_W, FILTER_BTN_H, id, FilterAction.ADD_BLOCK));
            entryY += FILTER_ENTRY_HEIGHT;
        }
        context.disableScissor();
    }

    // --- Inline Mob Filter ---

    private void renderInlineMobFilter(DrawContext context, int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        renderFilterSearchBar(context, px, y, pw, alpha, mouseX, mouseY);
        int listY = y + FILTER_SEARCH_HEIGHT + 2;
        int visibleH = getFilterVisibleHeight();
        filterListTop = listY; filterListBottom = listY + visibleH; filterListLeft = px; filterListRight = px + pw;
        filterHitEntries.clear();

        List<String> namesF = filterBySearch(cachedMobActiveNames);
        List<String> typesF = filterBySearch(cachedMobActiveTypes);
        List<String> nearNamesF = filterBySearch(cachedMobNearbyNames);
        List<String> nearTypesF = filterBySearch(cachedMobNearbyTypes);
        int totalCount = 0;
        if (!namesF.isEmpty()) totalCount += 1 + namesF.size();
        if (!typesF.isEmpty()) totalCount += 1 + typesF.size();
        totalCount += 1 + nearNamesF.size() + 1 + nearTypesF.size();
        int totalH = totalCount * FILTER_ENTRY_HEIGHT;
        if (expandedFilterColorKey != null) totalH += 80;
        filterMaxScroll = Math.max(0, totalH - FILTER_MAX_VISIBLE_HEIGHT);
        filterScrollOffset = Math.max(0, Math.min(filterScrollOffset, filterMaxScroll));

        context.enableScissor(px + 4, listY, px + pw - 4, filterListBottom);
        int entryY = listY - filterScrollOffset;
        int entryLeft = px + 6, entryRight = px + pw - 6;
        int accent = getButtonBorderAccent();

        entryY = renderFilterSection(context, "Active Names", namesF, entryY, entryLeft, entryRight, listY, accent, alpha, mouseX, mouseY, FilterAction.REMOVE_MOB_NAME, false);
        entryY += 2;
        entryY = renderFilterSection(context, "Active Types", typesF, entryY, entryLeft, entryRight, listY, accent, alpha, mouseX, mouseY, FilterAction.REMOVE_MOB_TYPE, false);
        entryY += 2;
        entryY = renderFilterSection(context, "Nearby Names", nearNamesF, entryY, entryLeft, entryRight, listY, accent, alpha, mouseX, mouseY, FilterAction.ADD_MOB_NAME, true);
        entryY += 2;
        renderFilterSection(context, "Nearby Types", nearTypesF, entryY, entryLeft, entryRight, listY, accent, alpha, mouseX, mouseY, FilterAction.ADD_MOB_TYPE, true);

        context.disableScissor();
    }

    private int renderFilterSection(DrawContext context, String header, List<String> items, int entryY,
                                     int entryLeft, int entryRight, int listY, int accent, float alpha,
                                     int mouseX, int mouseY, FilterAction action, boolean isSuggestion) {
        if (!isSuggestion && items.isEmpty()) return entryY;
        boolean isTypeAction = action == FilterAction.ADD_MOB_TYPE || action == FilterAction.REMOVE_MOB_TYPE;
        boolean isNameAction = action == FilterAction.ADD_MOB_NAME || action == FilterAction.REMOVE_MOB_NAME;
        boolean isActiveSection = !isSuggestion;
        context.drawTextWithShadow(textRenderer, header, entryLeft, entryY + 2, applyAlpha(0xFFFFFFFF, alpha));
        entryY += FILTER_ENTRY_HEIGHT;
        for (String key : items) {
            int textX = entryLeft;
            if (isTypeAction || isNameAction) {
                renderMobIcon(context, key, isTypeAction, entryLeft, entryY);
                textX = entryLeft + 18;
            }
            // Color square for active mob entries
            if (isActiveSection) {
                int csqSize = 8;
                int csqX = textX;
                int csqY = entryY + (FILTER_ENTRY_HEIGHT - csqSize) / 2;
                int previewColor = resolveFilterColor(key);
                context.fill(csqX, csqY, csqX + csqSize, csqY + csqSize, applyAlpha(previewColor, alpha));
                drawSolidBorder(context, csqX - 1, csqY - 1, csqX + csqSize + 1, csqY + csqSize + 1, applyAlpha(0xFF555555, alpha));
                if (entryY + FILTER_BTN_H > listY && entryY < filterListBottom)
                    filterHitEntries.add(new FilterHitEntry(csqX, csqY, csqSize, csqSize, key, FilterAction.FILTER_COLOR));
                textX += csqSize + 3;
            }
            String label = truncateLabel(key, entryRight - textX - FILTER_BTN_W - 6);
            context.drawTextWithShadow(textRenderer, label, textX, entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                    applyAlpha(isSuggestion ? 0xFFAAAAAA : 0xFFCCCCCC, alpha));
            int btnX = entryRight - FILTER_BTN_W - 2;
            boolean bh = mouseX >= btnX && mouseX <= btnX + FILTER_BTN_W && mouseY >= entryY && mouseY <= entryY + FILTER_BTN_H
                    && mouseY >= listY && mouseY <= filterListBottom;
            boolean isAdd = isSuggestion;
            context.fill(btnX, entryY, btnX + FILTER_BTN_W, entryY + FILTER_BTN_H,
                    applyAlpha(isAdd ? (bh ? 0xFF339933 : 0xFF335533) : (bh ? 0xFF993333 : 0xFF553333), alpha));
            String btnLabel = isAdd ? "+" : "-";
            context.drawTextWithShadow(textRenderer, btnLabel, btnX + (FILTER_BTN_W - textRenderer.getWidth(btnLabel)) / 2,
                    entryY + (FILTER_BTN_H - textRenderer.fontHeight) / 2, applyAlpha(0xFFFFFFFF, alpha));
            if (entryY + FILTER_BTN_H > listY && entryY < filterListBottom)
                filterHitEntries.add(new FilterHitEntry(btnX, entryY, FILTER_BTN_W, FILTER_BTN_H, key, action));
            entryY += FILTER_ENTRY_HEIGHT;

            // Inline color picker if this entry is expanded
            if (isActiveSection && key.equals(expandedFilterColorKey)) {
                entryY = renderInlineFilterColorPicker(context, key, entryY, entryLeft, entryRight, listY, alpha, mouseX, mouseY);
            }
        }
        return entryY;
    }

    private int resolveFilterColor(String filterKey) {
        int[] colorInfo = MobEspManager.getColorForFilter(filterKey);
        if (colorInfo != null) {
            if (colorInfo[1] == 1) return RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
            return colorInfo[0];
        }
        if (RenderConfig.isDefaultEspChromaEnabled()) return RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
        return RenderConfig.getDefaultEspColor();
    }

    private int renderInlineFilterColorPicker(DrawContext context, String key, int entryY,
                                               int entryLeft, int entryRight, int listY, float alpha,
                                               int mouseX, int mouseY) {
        int[] colorInfo = MobEspManager.getColorForFilter(key);
        boolean isChroma = colorInfo != null && colorInfo[1] == 1;

        int svSize = 60, hueBarW = 8, hueBarH = 60;
        int svX = entryLeft + 4, svY = entryY + 2;
        int hbX = svX + svSize + 6, hbY = svY;

        if (isChroma) {
            int flash = RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
            context.fill(svX, svY, svX + svSize, svY + svSize, applyAlpha(0xFF222222, alpha));
            context.fill(svX, svY, svX + svSize, svY + svSize, applyAlpha(flash, alpha * 0.15f));
            context.fill(hbX, hbY, hbX + hueBarW, hbY + hueBarH, applyAlpha(0xFF222222, alpha));
            context.fill(hbX, hbY, hbX + hueBarW, hbY + hueBarH, applyAlpha(flash, alpha * 0.15f));
        } else {
            for (int x = 0; x < svSize; x++) {
                float s = x / (float) (svSize - 1);
                int topC = applyAlpha(Color.HSBtoRGB(filterColorHue, s, 1.0f) | 0xFF000000, alpha);
                int botC = applyAlpha(0xFF000000, alpha);
                context.fillGradient(svX + x, svY, svX + x + 1, svY + svSize, topC, botC);
            }
            int cx = svX + (int) (filterColorSat * (svSize - 1));
            int cy = svY + (int) ((1.0f - filterColorVal) * (svSize - 1));
            context.fill(cx - 2, cy - 2, cx + 3, cy + 3, applyAlpha(0xFF000000, alpha));
            context.fill(cx - 1, cy - 1, cx + 2, cy + 2, applyAlpha(0xFFFFFFFF, alpha));
            for (int yy = 0; yy < hueBarH; yy++) {
                float h = yy / (float) (hueBarH - 1);
                context.fill(hbX, hbY + yy, hbX + hueBarW, hbY + yy + 1,
                        applyAlpha(Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000, alpha));
            }
            int hyCursor = hbY + (int) (filterColorHue * (hueBarH - 1));
            context.fill(hbX - 1, hyCursor - 1, hbX + hueBarW + 1, hyCursor + 2, applyAlpha(0xFFFFFFFF, alpha));
        }

        drawSolidBorder(context, svX - 1, svY - 1, svX + svSize + 1, svY + svSize + 1, applyAlpha(0xFF444444, alpha));
        drawSolidBorder(context, hbX - 1, hbY - 1, hbX + hueBarW + 1, hbY + hueBarH + 1, applyAlpha(0xFF444444, alpha));

        // Preview + chroma toggle
        int infoY = svY + svSize + 3;
        int previewColor = isChroma ? RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f)
                : (Color.HSBtoRGB(filterColorHue, filterColorSat, filterColorVal) | 0xFF000000);
        context.fill(svX, infoY, svX + 10, infoY + 10, applyAlpha(previewColor, alpha));
        String hex = "#" + String.format("%06X", previewColor & 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, hex, svX + 14, infoY + 1, applyAlpha(0xFFCCCCCC, alpha));
        String chromaLabel = isChroma ? "Chroma: ON" : "Chroma: OFF";
        int chromaX = entryRight - textRenderer.getWidth(chromaLabel) - 6;
        boolean hoverChroma = mouseX >= chromaX && mouseX <= entryRight - 2 && mouseY >= infoY && mouseY <= infoY + 10;
        context.drawTextWithShadow(textRenderer, chromaLabel, chromaX, infoY + 1,
                hoverChroma ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha));

        // Register hit entries
        if (entryY + 80 > listY && entryY < filterListBottom) {
            filterHitEntries.add(new FilterHitEntry(svX, svY, svSize, svSize, key, FilterAction.FILTER_COLOR_SV));
            filterHitEntries.add(new FilterHitEntry(hbX, hbY, hueBarW, hueBarH, key, FilterAction.FILTER_COLOR_HUE));
            filterHitEntries.add(new FilterHitEntry(chromaX, infoY, entryRight - 2 - chromaX, 10, key, FilterAction.FILTER_COLOR_CHROMA));
        }

        return entryY + 80;
    }

    private void renderFilterSearchBar(DrawContext context, int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        int addBtnW = FILTER_BTN_W + 2;
        int addBtnX = px + pw - 6 - addBtnW;
        int textFieldRight = addBtnX - 2;
        context.fill(px + 6, y, textFieldRight, y + FILTER_SEARCH_HEIGHT, applyAlpha(0xFF0A0A0A, alpha));
        drawSolidBorder(context, px + 5, y - 1, textFieldRight + 1, y + FILTER_SEARCH_HEIGHT + 1,
                applyAlpha(getButtonBorderAccent(), alpha));
        String placeholder = pendingNameMappingOriginal != null && expandedFilterSetting instanceof ModuleEntry.NameFilterSetting
                ? "Fake name for " + pendingNameMappingOriginal + "..." : "Filter...";
        String display = filterSearchQuery.isEmpty() && !filterSearchFocused ? placeholder : filterSearchQuery + (filterSearchFocused ? "_" : "");
        int textColor = filterSearchQuery.isEmpty() && !filterSearchFocused ? applyAlpha(0xFF888888, alpha) : applyAlpha(0xFFFFFFFF, alpha);
        context.drawTextWithShadow(textRenderer, display, px + 10, y + (FILTER_SEARCH_HEIGHT - textRenderer.fontHeight) / 2, textColor);
        // [+] add button
        boolean addHover = mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= y && mouseY <= y + FILTER_SEARCH_HEIGHT;
        context.fill(addBtnX, y, addBtnX + addBtnW, y + FILTER_SEARCH_HEIGHT, applyAlpha(addHover ? 0xFF339933 : 0xFF337733, alpha));
        drawSolidBorder(context, addBtnX - 1, y - 1, addBtnX + addBtnW + 1, y + FILTER_SEARCH_HEIGHT + 1,
                applyAlpha(getButtonBorderAccent(), alpha));
        context.drawTextWithShadow(textRenderer, "+", addBtnX + (addBtnW - textRenderer.getWidth("+")) / 2,
                y + (FILTER_SEARCH_HEIGHT - textRenderer.fontHeight) / 2, applyAlpha(0xFFFFFFFF, alpha));
    }

    private void renderDualNameSearchBar(DrawContext context, int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        int innerLeft = px + 6;
        int innerRight = px + pw - 6;
        int addBtnW = FILTER_BTN_W + 2;
        int addBtnX = innerRight - addBtnW;
        int arrowW = textRenderer.getWidth(" \u2192 ");
        int fieldArea = addBtnX - 2 - innerLeft;
        int ogFieldW = (fieldArea - arrowW) / 2;
        int fakeFieldX = innerLeft + ogFieldW + arrowW;
        int fakeFieldW = addBtnX - 2 - fakeFieldX;

        // OG field background
        context.fill(innerLeft, y, innerLeft + ogFieldW, y + FILTER_SEARCH_HEIGHT, applyAlpha(0xFF0A0A0A, alpha));
        drawSolidBorder(context, innerLeft - 1, y - 1, innerLeft + ogFieldW + 1, y + FILTER_SEARCH_HEIGHT + 1,
                applyAlpha(nameFilterOriginalFocused ? getTextAccent() : getButtonBorderAccent(), alpha));
        String ogPlaceholder = "Original name...";
        String ogDisplay = nameFilterOriginalQuery.isEmpty() && !nameFilterOriginalFocused ? ogPlaceholder : nameFilterOriginalQuery + (nameFilterOriginalFocused ? "_" : "");
        int ogColor = nameFilterOriginalQuery.isEmpty() && !nameFilterOriginalFocused ? applyAlpha(0xFF888888, alpha) : applyAlpha(0xFFFFFFFF, alpha);
        context.drawTextWithShadow(textRenderer, ogDisplay, innerLeft + 4, y + (FILTER_SEARCH_HEIGHT - textRenderer.fontHeight) / 2, ogColor);

        // Arrow
        context.drawTextWithShadow(textRenderer, "\u2192", innerLeft + ogFieldW + (arrowW - textRenderer.getWidth("\u2192")) / 2,
                y + (FILTER_SEARCH_HEIGHT - textRenderer.fontHeight) / 2, applyAlpha(0xFFAAAAAA, alpha));

        // Fake name field background
        context.fill(fakeFieldX, y, fakeFieldX + fakeFieldW, y + FILTER_SEARCH_HEIGHT, applyAlpha(0xFF0A0A0A, alpha));
        drawSolidBorder(context, fakeFieldX - 1, y - 1, fakeFieldX + fakeFieldW + 1, y + FILTER_SEARCH_HEIGHT + 1,
                applyAlpha(nameFilterFakeFocused ? getTextAccent() : getButtonBorderAccent(), alpha));
        String fakePlaceholder = "New name...";
        String fakeDisplay = nameFilterFakeQuery.isEmpty() && !nameFilterFakeFocused ? fakePlaceholder : nameFilterFakeQuery + (nameFilterFakeFocused ? "_" : "");
        int fakeColor = nameFilterFakeQuery.isEmpty() && !nameFilterFakeFocused ? applyAlpha(0xFF888888, alpha) : applyAlpha(0xFFFFFFFF, alpha);
        context.drawTextWithShadow(textRenderer, fakeDisplay, fakeFieldX + 4, y + (FILTER_SEARCH_HEIGHT - textRenderer.fontHeight) / 2, fakeColor);

        // [+] add button
        boolean addHover = mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= y && mouseY <= y + FILTER_SEARCH_HEIGHT;
        boolean canAdd = !nameFilterOriginalQuery.trim().isEmpty() && !nameFilterFakeQuery.trim().isEmpty();
        int addBg = canAdd ? (addHover ? 0xFF339933 : 0xFF337733) : (addHover ? 0xFF555555 : 0xFF333333);
        context.fill(addBtnX, y, addBtnX + addBtnW, y + FILTER_SEARCH_HEIGHT, applyAlpha(addBg, alpha));
        drawSolidBorder(context, addBtnX - 1, y - 1, addBtnX + addBtnW + 1, y + FILTER_SEARCH_HEIGHT + 1,
                applyAlpha(getButtonBorderAccent(), alpha));
        context.drawTextWithShadow(textRenderer, "+", addBtnX + (addBtnW - textRenderer.getWidth("+")) / 2,
                y + (FILTER_SEARCH_HEIGHT - textRenderer.fontHeight) / 2, applyAlpha(0xFFFFFFFF, alpha));
    }

    // --- Inline Name Filter ---

    private void renderInlineNameFilter(DrawContext context, int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        NickTextUtil.setSuppressNickReplacement(true);
        try {
        renderDualNameSearchBar(context, px, y, pw, alpha, mouseX, mouseY);
        int listY = y + FILTER_SEARCH_HEIGHT + 2;
        int visibleH = getFilterVisibleHeight();
        filterListTop = listY; filterListBottom = listY + visibleH; filterListLeft = px; filterListRight = px + pw;
        filterHitEntries.clear();

        List<String> mappingKeys = new ArrayList<>();
        for (var e : cachedNameMappings) mappingKeys.add(e.getKey());
        List<String> activeMappingsF = filterNamesByOriginalQuery(mappingKeys);
        List<String> onlineF = filterNamesByOriginalQuery(cachedOnlinePlayers);
        int totalCount = 0;
        if (!activeMappingsF.isEmpty()) totalCount += 1 + activeMappingsF.size();
        totalCount += 1 + onlineF.size();
        int totalH = totalCount * FILTER_ENTRY_HEIGHT;
        filterMaxScroll = Math.max(0, totalH - FILTER_MAX_VISIBLE_HEIGHT);
        filterScrollOffset = Math.max(0, Math.min(filterScrollOffset, filterMaxScroll));

        context.enableScissor(px + 4, listY, px + pw - 4, filterListBottom);
        int entryY = listY - filterScrollOffset;
        int entryLeft = px + 6, entryRight = px + pw - 6;
        int accent = getButtonBorderAccent();

        // Active Mappings header (only if non-empty)
        if (!activeMappingsF.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Active Mappings", entryLeft, entryY + 2, applyAlpha(0xFFFFFFFF, alpha));
            entryY += FILTER_ENTRY_HEIGHT;
            for (String key : activeMappingsF) {
                String fakeName = "";
                for (var e : cachedNameMappings) {
                    if (e.getKey().equals(key)) { fakeName = e.getValue(); break; }
                }
                // Player head
                Identifier skin = getPlayerSkinTexture(key);
                if (skin != null) {
                    drawPlayerHead(context, skin, entryLeft, entryY + (FILTER_ENTRY_HEIGHT - 8) / 2, 8);
                }
                boolean revealed = revealedMappingNames.contains(key);
                String displayKey = revealed ? key : "*****";
                String label = truncateLabel(displayKey + " \u2192 " + fakeName, entryRight - entryLeft - FILTER_BTN_W - 16);
                context.drawTextWithShadow(textRenderer, label, entryLeft + 10, entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                        applyAlpha(0xFFCCCCCC, alpha));
                // Click on label area toggles reveal
                int labelRight = entryRight - FILTER_BTN_W - 6;
                if (entryY + FILTER_ENTRY_HEIGHT > listY && entryY < filterListBottom)
                    filterHitEntries.add(new FilterHitEntry(entryLeft, entryY, labelRight - entryLeft, FILTER_ENTRY_HEIGHT, key, FilterAction.TOGGLE_NAME_REVEAL));
                int btnX = entryRight - FILTER_BTN_W - 2;
                boolean bh = mouseX >= btnX && mouseX <= btnX + FILTER_BTN_W && mouseY >= entryY && mouseY <= entryY + FILTER_BTN_H
                        && mouseY >= listY && mouseY <= filterListBottom;
                context.fill(btnX, entryY, btnX + FILTER_BTN_W, entryY + FILTER_BTN_H, applyAlpha(bh ? 0xFF993333 : 0xFF553333, alpha));
                context.drawTextWithShadow(textRenderer, "-", btnX + (FILTER_BTN_W - textRenderer.getWidth("-")) / 2,
                        entryY + (FILTER_BTN_H - textRenderer.fontHeight) / 2, applyAlpha(0xFFFFFFFF, alpha));
                if (entryY + FILTER_BTN_H > listY && entryY < filterListBottom)
                    filterHitEntries.add(new FilterHitEntry(btnX, entryY, FILTER_BTN_W, FILTER_BTN_H, key, FilterAction.REMOVE_NAME_MAPPING));
                entryY += FILTER_ENTRY_HEIGHT;
            }
        }

        entryY += 2;
        // Online Players header
        context.drawTextWithShadow(textRenderer, "Online Players", entryLeft, entryY + 2, applyAlpha(0xFFFFFFFF, alpha));
        entryY += FILTER_ENTRY_HEIGHT;
        for (String name : onlineF) {
            // Player head
            Identifier skin = getPlayerSkinTexture(name);
            if (skin != null) {
                drawPlayerHead(context, skin, entryLeft, entryY + (FILTER_ENTRY_HEIGHT - 8) / 2, 8);
            }
            String label = truncateLabel(name, entryRight - entryLeft - FILTER_BTN_W - 16);
            context.drawTextWithShadow(textRenderer, label, entryLeft + 10, entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                    applyAlpha(0xFFAAAAAA, alpha));
            int btnX = entryRight - FILTER_BTN_W - 2;
            boolean bh = mouseX >= btnX && mouseX <= btnX + FILTER_BTN_W && mouseY >= entryY && mouseY <= entryY + FILTER_BTN_H
                    && mouseY >= listY && mouseY <= filterListBottom;
            context.fill(btnX, entryY, btnX + FILTER_BTN_W, entryY + FILTER_BTN_H, applyAlpha(bh ? 0xFF339933 : 0xFF335533, alpha));
            context.drawTextWithShadow(textRenderer, "+", btnX + (FILTER_BTN_W - textRenderer.getWidth("+")) / 2,
                    entryY + (FILTER_BTN_H - textRenderer.fontHeight) / 2, applyAlpha(0xFFFFFFFF, alpha));
            if (entryY + FILTER_BTN_H > listY && entryY < filterListBottom)
                filterHitEntries.add(new FilterHitEntry(btnX, entryY, FILTER_BTN_W, FILTER_BTN_H, name, FilterAction.ADD_NAME_MAPPING));
            entryY += FILTER_ENTRY_HEIGHT;
        }
        context.disableScissor();
        } finally {
            NickTextUtil.setSuppressNickReplacement(false);
        }
    }

    // --- Inline Player Picker ---

    private void renderInlinePlayerPicker(DrawContext context, int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        renderFilterSearchBar(context, px, y, pw, alpha, mouseX, mouseY);
        int listY = y + FILTER_SEARCH_HEIGHT + 2;
        int visibleH = getFilterVisibleHeight();
        filterListTop = listY; filterListBottom = listY + visibleH; filterListLeft = px; filterListRight = px + pw;
        filterHitEntries.clear();

        Map<String, String> nameMappings = NickHiderConfig.getNameMappings();
        List<String> playersF = filterBySearchWithMappings(cachedStalkPlayers, nameMappings);
        int totalH = playersF.size() * FILTER_ENTRY_HEIGHT;
        filterMaxScroll = Math.max(0, totalH - FILTER_MAX_VISIBLE_HEIGHT);
        filterScrollOffset = Math.max(0, Math.min(filterScrollOffset, filterMaxScroll));

        context.enableScissor(px + 4, listY, px + pw - 4, filterListBottom);
        int entryY = listY - filterScrollOffset;
        int entryLeft = px + 6, entryRight = px + pw - 6;

        String currentTarget = StalkManager.getTargetName();

        for (String name : playersF) {
            boolean isSelected = name.equalsIgnoreCase(currentTarget);

            // Highlight selected player
            if (isSelected) {
                context.fill(entryLeft - 2, entryY, entryRight + 2, entryY + FILTER_ENTRY_HEIGHT, applyAlpha(getTextAccent(), alpha * 0.12f));
            }

            // Player head
            Identifier skin = getPlayerSkinTexture(name);
            if (skin != null) {
                drawPlayerHead(context, skin, entryLeft, entryY + (FILTER_ENTRY_HEIGHT - 8) / 2, 8);
            }

            // Name — show mapped nick hider name if available
            String displayName = nameMappings.getOrDefault(name, name);
            int textColor = isSelected ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha);
            String label = truncateLabel(displayName, entryRight - entryLeft - FILTER_BTN_W - 16);
            context.drawTextWithShadow(textRenderer, label, entryLeft + 10,
                    entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2, textColor);

            // [+] select button
            int btnX = entryRight - FILTER_BTN_W - 2;
            boolean bh = mouseX >= btnX && mouseX <= btnX + FILTER_BTN_W && mouseY >= entryY && mouseY <= entryY + FILTER_BTN_H
                    && mouseY >= listY && mouseY <= filterListBottom;
            context.fill(btnX, entryY, btnX + FILTER_BTN_W, entryY + FILTER_BTN_H,
                    applyAlpha(bh ? 0xFF339933 : 0xFF335533, alpha));
            String btnLabel = "+";
            context.drawTextWithShadow(textRenderer, btnLabel, btnX + (FILTER_BTN_W - textRenderer.getWidth(btnLabel)) / 2,
                    entryY + (FILTER_BTN_H - textRenderer.fontHeight) / 2, applyAlpha(0xFFFFFFFF, alpha));
            if (entryY + FILTER_BTN_H > listY && entryY < filterListBottom)
                filterHitEntries.add(new FilterHitEntry(btnX, entryY, FILTER_BTN_W, FILTER_BTN_H, name, FilterAction.PICK_PLAYER));
            entryY += FILTER_ENTRY_HEIGHT;
        }
        context.disableScissor();
    }

    private Identifier getPlayerSkinTexture(String playerName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return null;
        for (var entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().name().equalsIgnoreCase(playerName)) {
                return entry.getSkinTextures().body().texturePath();
            }
        }
        return null;
    }

    private void drawPlayerHead(DrawContext context, Identifier skinTexture, int x, int y, int size) {
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        context.drawTexture(pipeline, skinTexture, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
    }

    private void renderBlockIcon(DrawContext context, String blockId, int x, int y) {
        try {
            Block block = Registries.BLOCK.get(Identifier.of(blockId));
            ItemStack stack = new ItemStack(block.asItem());
            if (!stack.isEmpty()) {
                context.getMatrices().pushMatrix();
                float scale = 0.75f;
                context.getMatrices().translate(x, y);
                context.getMatrices().scale(scale, scale);
                context.drawItem(stack, 0, 0);
                context.getMatrices().popMatrix();
            }
        } catch (Exception ignored) {}
    }

    private void renderMobIcon(DrawContext context, String key, boolean isType, int x, int y) {
        try {
            if (isType) {
                LivingEntity entity = cachedMobEntities.get(key);
                if (entity == null && client != null && client.world != null) {
                    EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.of(key));
                    Entity created = entityType.create(client.world, net.minecraft.entity.SpawnReason.COMMAND);
                    if (created instanceof LivingEntity le) {
                        cachedMobEntities.put(key, le);
                        entity = le;
                    }
                }
                if (entity != null) {
                    int iconW = 14, iconH = FILTER_ENTRY_HEIGHT;
                    context.enableScissor(x, y, x + iconW, y + iconH);
                    InventoryScreen.drawEntity(context, x, y, x + iconW, y + iconH,
                            6, 0.0625f, (float)(x + iconW / 2), (float)y, entity);
                    context.disableScissor();
                } else {
                    // Fallback to spawn egg for non-LivingEntity types
                    EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.of(key));
                    SpawnEggItem egg = SpawnEggItem.forEntity(entityType);
                    if (egg != null) {
                        ItemStack stack = new ItemStack(egg);
                        context.getMatrices().pushMatrix();
                        context.getMatrices().translate(x, y);
                        context.getMatrices().scale(0.75f, 0.75f);
                        context.drawItem(stack, 0, 0);
                        context.getMatrices().popMatrix();
                    }
                }
            } else {
                // For name filters, find the actual entity in the world by name
                LivingEntity entity = cachedMobEntities.get(key);
                if (entity == null && client != null && client.world != null) {
                    // Try NpcTracker first (armor-stand-paired NPCs)
                    Entity npcEntity = NpcTracker.findEntityByName(key);
                    if (npcEntity instanceof LivingEntity le) {
                        cachedMobEntities.put(key, le);
                        entity = le;
                    } else {
                        // Search world entities by display name or custom name
                        for (Entity e : client.world.getEntities()) {
                            if (!(e instanceof LivingEntity le)) continue;
                            String displayName = e.getName().getString().replaceAll("\u00a7.", "").trim();
                            if (displayName.equalsIgnoreCase(key)) { cachedMobEntities.put(key, le); entity = le; break; }
                            if (e.hasCustomName() && e.getCustomName() != null) {
                                String custom = e.getCustomName().getString().replaceAll("\u00a7.", "").trim();
                                if (custom.equalsIgnoreCase(key)) { cachedMobEntities.put(key, le); entity = le; break; }
                            }
                        }
                    }
                }
                if (entity != null) {
                    int iconW = 14, iconH = FILTER_ENTRY_HEIGHT;
                    context.enableScissor(x, y, x + iconW, y + iconH);
                    InventoryScreen.drawEntity(context, x, y, x + iconW, y + iconH,
                            6, 0.0625f, (float)(x + iconW / 2), (float)y, entity);
                    context.disableScissor();
                } else {
                    Identifier skin = getPlayerSkinTexture(key);
                    if (skin != null) {
                        drawPlayerHead(context, skin, x, y + (FILTER_ENTRY_HEIGHT - 8) / 2, 8);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // --- Player Preview ---

    private boolean isPlayerPreviewModule(ModuleEntry module) {
        String name = module.getName();
        return "Cape".equals(name) || "Cone Hat".equals(name) || "Custom Skin".equals(name) || "Player Size".equals(name);
    }

    private void renderPlayerPreview(DrawContext context, int mouseX, int mouseY, float alpha) {
        if (client == null || client.player == null || popupModule == null) return;
        int pvW = 60, pvH = 120;
        int pvX = popupX + popupWidth + 4;
        int pvY = popupY;
        if (pvX + pvW > width) pvX = popupX - pvW - 4;
        if (pvY + pvH > height) pvY = height - pvH - 4;
        if (pvY < 0) pvY = 4;
        context.fill(pvX, pvY, pvX + pvW, pvY + pvH, applyAlpha(0x66000000, alpha));
        drawSolidBorder(context, pvX - 1, pvY - 1, pvX + pvW + 1, pvY + pvH + 1, applyAlpha(getButtonBorderAccent(), alpha));

        String modName = popupModule.getName();
        boolean isCape = "Cape".equals(modName);
        boolean isSkin = "Custom Skin".equals(modName) || "Player Size".equals(modName);

        // Strip armor for skin preview
        ItemStack[] savedArmor = null;
        if (isSkin) {
            EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
            savedArmor = new ItemStack[slots.length];
            for (int i = 0; i < slots.length; i++) {
                savedArmor[i] = client.player.getEquippedStack(slots[i]).copy();
                client.player.equipStack(slots[i], ItemStack.EMPTY);
            }
        }

        try {
            if (isCape) {
                // Face backwards: replicate the first overload's logic but with yaw flipped 180
                float centerX = pvX + pvW / 2f;
                float centerY = pvY + pvH / 2f;
                context.enableScissor(pvX, pvY, pvX + pvW, pvY + pvH);
                // Same rotateZ(PI) as vanilla to flip right-side-up; bodyYaw=0 handles backwards
                Quaternionf bodyRot = new Quaternionf().rotateZ((float) Math.PI);
                float o = client.player.getScale();
                Vector3f offset = new Vector3f(0f, client.player.getHeight() / 2f + 0.0625f * o, 0f);
                float scaledSize = 25f / o;
                // Save and set entity facing away
                float savedBodyYaw = client.player.bodyYaw;
                float savedYaw = client.player.getYaw();
                float savedPitch = client.player.getPitch();
                float savedHeadYaw = client.player.headYaw;
                float savedLastHeadYaw = client.player.lastHeadYaw;
                client.player.bodyYaw = 0f;
                client.player.setYaw(0f);
                client.player.setPitch(0f);
                client.player.headYaw = 0f;
                client.player.lastHeadYaw = 0f;
                InventoryScreen.drawEntity(context, pvX, pvY, pvX + pvW, pvY + pvH,
                        scaledSize, offset, bodyRot, null, client.player);
                client.player.bodyYaw = savedBodyYaw;
                client.player.setYaw(savedYaw);
                client.player.setPitch(savedPitch);
                client.player.headYaw = savedHeadYaw;
                client.player.lastHeadYaw = savedLastHeadYaw;
                context.disableScissor();
            } else {
                InventoryScreen.drawEntity(context, pvX, pvY, pvX + pvW, pvY + pvH,
                        25, 0.0625f, (float) mouseX, (float) mouseY, client.player);
            }
        } catch (Exception ignored) {}

        // Restore armor
        if (savedArmor != null) {
            EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
            for (int i = 0; i < slots.length; i++) {
                client.player.equipStack(slots[i], savedArmor[i]);
            }
        }
    }

    // --- FloydAddons text at bottom ---

    private void renderBottomTitle(DrawContext context, float alpha) {
        float textScale = 2.0f;
        String faText = "FloydAddons";
        int faWidth = (int) (textRenderer.getWidth(faText) * textScale);
        float faX = (width - faWidth) / 2f;
        float faY = height - 20;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(faX, faY);
        matrices.scale(textScale, textScale);
        context.drawTextWithShadow(textRenderer, faText, 0, 0, applyAlpha(getTextAccent(), alpha));
        matrices.popMatrix();
    }

    // --- Input Handling ---

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x(), my = click.y();
        int button = click.button();

        if (button == 0 && editingText != null) finishTextEdit();

        // Popup clicks first
        if (popupModule != null && button == 0) {
            if (handlePopupClick(mx, my)) return true;
            if (mx < popupX || mx > popupX + popupWidth || my < popupY || my > popupY + popupHeight) closePopup();
        }

        // Search bar
        int searchX = (width - SEARCH_BAR_WIDTH) / 2, searchY = 4;
        if (mx >= searchX && mx <= searchX + SEARCH_BAR_WIDTH && my >= searchY && my <= searchY + SEARCH_BAR_HEIGHT) {
            searchFocused = true; filterSearchFocused = false; return true;
        } else {
            searchFocused = false;
        }

        // Panels
        for (var entry : modules.entrySet()) {
            ModuleCategory category = entry.getKey();
            ClickGuiConfig.PanelState state = ClickGuiConfig.getState(category);
            int px = state.x, py = state.y, pw = getPanelWidth(category);
            if (mx < px || mx > px + pw) continue;

            if (my >= py && my <= py + HEADER_HEIGHT) {
                if (button == 1) { state.collapsed = !state.collapsed; return true; }
                draggingPanel = category;
                dragOffsetX = (int) (mx - px); dragOffsetY = (int) (my - py);
                return true;
            }
            if (state.collapsed) continue;

            List<ModuleEntry> filtered = filterModules(entry.getValue());
            int totalHeight = HEADER_HEIGHT + filtered.size() * MODULE_HEIGHT;
            if (my < py + HEADER_HEIGHT || my > py + totalHeight) continue;

            int moduleY = py + HEADER_HEIGHT - (int) state.scrollOffset;
            for (ModuleEntry mod : filtered) {
                if (my >= moduleY && my < moduleY + MODULE_HEIGHT) {
                    if (button == 0) {
                        mod.toggle(); FloydAddonsConfig.save();
                        if (mod.hasSettings()) openPopup(mod, category, px, py, pw, moduleY);
                    } else if (button == 1 && mod.hasSettings()) {
                        openPopup(mod, category, px, py, pw, moduleY);
                    }
                    return true;
                }
                moduleY += MODULE_HEIGHT;
            }
        }
        return super.mouseClicked(click, ignoresInput);
    }

    private boolean handlePopupClick(double mx, double my) {
        if (popupModule == null) return false;
        if (mx < popupX || mx > popupX + popupWidth || my < popupY || my > popupY + popupHeight) return false;

        // Title bar drag
        if (my >= popupY && my <= popupY + POPUP_TITLE_HEIGHT) {
            draggingPopup = true;
            popupDragOffsetX = (int) (mx - popupX); popupDragOffsetY = (int) (my - popupY);
            return true;
        }

        // Settings
        int settingY = popupY + POPUP_TITLE_HEIGHT;
        for (ModuleEntry.SubSetting setting : popupModule.getSettings()) {
            int settingH = getSettingHeight(setting);
            if (my >= settingY && my < settingY + settingH) {
                handlePopupSettingClick(setting, mx, my, settingY, settingH);
                return true;
            }
            settingY += settingH;
        }
        return true;
    }

    private void handlePopupSettingClick(ModuleEntry.SubSetting setting, double mx, double my, int settingY, int settingH) {
        if (setting instanceof ModuleEntry.BooleanSetting bs) {
            bs.toggle(); FloydAddonsConfig.save();
        } else if (setting instanceof ModuleEntry.SliderSetting slider) {
            int barX = popupX + 10, barW = popupWidth - 20;
            slider.setNormalized((float) Math.max(0, Math.min(1, (mx - barX) / barW)));
            draggingSlider = slider; draggingSliderX = barX; draggingSliderWidth = barW;
            FloydAddonsConfig.save();
        } else if (setting instanceof ModuleEntry.ColorSetting colorSetting) {
            if (colorSetting == expandedColorSetting) {
                // Handle clicks within expanded color picker
                int svX = popupX + 10, svY = settingY + POPUP_COLOR_COLLAPSED_HEIGHT + 4;
                int svSize = 100, hueBarW = 10, hueBarH = 100;
                int hbX = svX + svSize + 8, hbY = svY, infoY = svY + svSize + 6;
                ModuleEntry.FadingColorSetting fcs = colorSetting instanceof ModuleEntry.FadingColorSetting ? (ModuleEntry.FadingColorSetting) colorSetting : null;
                String chromaLabel = colorSetting.isChroma() ? "Chroma: ON" : "Chroma: OFF";
                int chromaX = popupX + popupWidth - textRenderer.getWidth(chromaLabel) - 10;
                if (my >= infoY && my <= infoY + 16 && mx >= chromaX) {
                    colorSetting.setChroma(!colorSetting.isChroma()); FloydAddonsConfig.save(); return;
                }
                if (fcs != null) {
                    int fadeRowY = infoY + 18;
                    int sqSize = 12;
                    int baseSqX = popupX + 10;
                    int fadeSqX = baseSqX + sqSize + 6;
                    String fadeLabel = fcs.isFadeEnabled() ? "Fade: ON" : "Fade: OFF";
                    int fadeX = popupX + popupWidth - textRenderer.getWidth(fadeLabel) - 10;
                    // Fade toggle
                    if (my >= fadeRowY && my <= fadeRowY + 12 && mx >= fadeX) {
                        fcs.setFadeEnabled(!fcs.isFadeEnabled()); FloydAddonsConfig.save(); return;
                    }
                    // Base/Fade selector squares
                    if (my >= fadeRowY && my <= fadeRowY + sqSize) {
                        if (mx >= baseSqX && mx <= baseSqX + sqSize) {
                            inlineEditingFadeColor = false;
                            loadInlineFromColor(colorSetting.getColor());
                            return;
                        } else if (mx >= fadeSqX && mx <= fadeSqX + sqSize) {
                            inlineEditingFadeColor = true;
                            loadInlineFromColor(fcs.getFadeColor());
                            return;
                        }
                    }
                }
                if (!colorSetting.isChroma() && mx >= svX && mx <= svX + svSize && my >= svY && my <= svY + svSize) {
                    draggingSV = true; updateInlineSV(mx - svX, my - svY, svSize, colorSetting); return;
                }
                if (!colorSetting.isChroma() && mx >= hbX && mx <= hbX + hueBarW && my >= hbY && my <= hbY + hueBarH) {
                    draggingHue = true; updateInlineHue(my - hbY, hueBarH, colorSetting); return;
                }
                if (my < settingY + POPUP_COLOR_COLLAPSED_HEIGHT) expandedColorSetting = null;
            } else {
                expandedColorSetting = colorSetting;
                inlineEditingFadeColor = false;
                expandedFilterSetting = null; filterSearchFocused = false; filterSearchQuery = ""; filterScrollOffset = 0;
                loadInlineFromColor(colorSetting.getColor());
            }
        } else if (setting instanceof ModuleEntry.ButtonSetting btn) {
            btn.click();
        } else if (setting instanceof ModuleEntry.CycleSetting cs) {
            cs.cycleForward(); FloydAddonsConfig.save();
        } else if (setting instanceof ModuleEntry.TextSetting ts) {
            editingText = ts; textEditBuffer = ts.getValue() != null ? ts.getValue() : "";
            searchFocused = false; filterSearchFocused = false;
        } else if (setting instanceof ModuleEntry.PlayerPickerSetting || setting instanceof ModuleEntry.BlockFilterSetting || setting instanceof ModuleEntry.MobFilterSetting || setting instanceof ModuleEntry.NameFilterSetting) {
            // Check if clicking the label area or the expanded area
            if (my < settingY + POPUP_SETTING_HEIGHT) {
                // Toggle expansion
                if (setting == expandedFilterSetting) {
                    expandedFilterSetting = null; filterSearchQuery = ""; filterSearchFocused = false; filterScrollOffset = 0; pendingNameMappingOriginal = null;
                    nameFilterOriginalQuery = ""; nameFilterFakeQuery = ""; nameFilterOriginalFocused = false; nameFilterFakeFocused = false;
                } else {
                    expandedFilterSetting = setting; expandedColorSetting = null;
                    filterSearchQuery = ""; filterSearchFocused = false; filterScrollOffset = 0;
                    nameFilterOriginalQuery = ""; nameFilterFakeQuery = ""; nameFilterOriginalFocused = false; nameFilterFakeFocused = false;
                    if (setting instanceof ModuleEntry.BlockFilterSetting) refreshBlockFilterData();
                    else if (setting instanceof ModuleEntry.MobFilterSetting) refreshMobFilterData();
                    else if (setting instanceof ModuleEntry.PlayerPickerSetting) refreshPlayerPickerData();
                    else refreshNameFilterData();
                }
            } else if (setting == expandedFilterSetting) {
                int filterAreaY = settingY + POPUP_SETTING_HEIGHT;
                // Search bar click
                if (my >= filterAreaY && my < filterAreaY + FILTER_SEARCH_HEIGHT && mx >= popupX + 6 && mx <= popupX + popupWidth - 6) {
                    if (setting instanceof ModuleEntry.NameFilterSetting) {
                        // Dual search bar click handling
                        int innerLeft = popupX + 6;
                        int innerRight = popupX + popupWidth - 6;
                        int addBtnW = FILTER_BTN_W + 2;
                        int addBtnX = innerRight - addBtnW;
                        int arrowW = textRenderer.getWidth(" \u2192 ");
                        int ogFieldW = (addBtnX - 2 - innerLeft - arrowW) / 2;
                        int fakeFieldX = innerLeft + ogFieldW + arrowW;
                        if (mx >= addBtnX) {
                            // [+] button
                            executeDualNameAdd(); return;
                        } else if (mx < innerLeft + ogFieldW) {
                            nameFilterOriginalFocused = true; nameFilterFakeFocused = false;
                            filterSearchFocused = false; searchFocused = false; return;
                        } else if (mx >= fakeFieldX) {
                            nameFilterFakeFocused = true; nameFilterOriginalFocused = false;
                            filterSearchFocused = false; searchFocused = false; return;
                        }
                        return;
                    }
                    int addBtnW = FILTER_BTN_W + 2;
                    int addBtnX = popupX + popupWidth - 6 - addBtnW;
                    if (mx >= addBtnX && !filterSearchQuery.isEmpty()) {
                        executeSearchAdd(setting); return;
                    }
                    filterSearchFocused = true; searchFocused = false; return;
                }
                // Filter list button clicks
                for (FilterHitEntry entry : filterHitEntries) {
                    if (mx >= entry.x && mx <= entry.x + entry.w && my >= entry.y && my <= entry.y + entry.h
                            && my >= filterListTop && my <= filterListBottom) {
                        executeFilterAction(entry, mx, my); return;
                    }
                }
            }
        }
    }

    private void updateInlineSV(double localX, double localY, int svSize, ModuleEntry.ColorSetting cs) {
        inlineSat = (float) Math.max(0, Math.min(1, localX / (svSize - 1)));
        inlineVal = (float) Math.max(0, Math.min(1, 1.0 - localY / (svSize - 1)));
        int newColor = Color.HSBtoRGB(inlineHue, inlineSat, inlineVal) | 0xFF000000;
        if (cs instanceof ModuleEntry.FadingColorSetting fcs && inlineEditingFadeColor) fcs.setFadeColor(newColor);
        else cs.setColor(newColor);
        FloydAddonsConfig.save();
    }

    private void updateInlineHue(double localY, int hueBarH, ModuleEntry.ColorSetting cs) {
        inlineHue = (float) Math.max(0, Math.min(1, localY / (hueBarH - 1)));
        int newColor = Color.HSBtoRGB(inlineHue, inlineSat, inlineVal) | 0xFF000000;
        if (cs instanceof ModuleEntry.FadingColorSetting fcs && inlineEditingFadeColor) fcs.setFadeColor(newColor);
        else cs.setColor(newColor);
        FloydAddonsConfig.save();
    }

    private void loadInlineFromColor(int color) {
        float[] hsv = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        inlineHue = hsv[0]; inlineSat = hsv[1]; inlineVal = hsv[2];
    }

    private int findExpandedColorSettingY() {
        if (popupModule == null || expandedColorSetting == null) return -1;
        int y = popupY + POPUP_TITLE_HEIGHT;
        for (ModuleEntry.SubSetting s : popupModule.getSettings()) {
            if (s == expandedColorSetting) return y;
            y += getSettingHeight(s);
        }
        return -1;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mx = click.x(), my = click.y();
        if (draggingPopup && click.button() == 0) {
            popupX = Math.max(0, Math.min(width - popupWidth, (int) (mx - popupDragOffsetX)));
            popupY = Math.max(0, Math.min(height - popupHeight, (int) (my - popupDragOffsetY)));
            return true;
        }
        if (draggingSV && expandedColorSetting != null) {
            int sy = findExpandedColorSettingY();
            if (sy >= 0) updateInlineSV(mx - (popupX + 10), my - (sy + POPUP_COLOR_COLLAPSED_HEIGHT + 4), 100, expandedColorSetting);
            return true;
        }
        if (draggingHue && expandedColorSetting != null) {
            int sy = findExpandedColorSettingY();
            if (sy >= 0) updateInlineHue(my - (sy + POPUP_COLOR_COLLAPSED_HEIGHT + 4), 100, expandedColorSetting);
            return true;
        }
        if (filterColorDraggingSV && expandedFilterColorKey != null) {
            for (FilterHitEntry fhe : filterHitEntries) {
                if (fhe.action == FilterAction.FILTER_COLOR_SV && fhe.key.equals(expandedFilterColorKey)) {
                    filterColorSat = (float) Math.max(0, Math.min(1, (mx - fhe.x) / (fhe.w - 1)));
                    filterColorVal = (float) Math.max(0, Math.min(1, 1.0 - (my - fhe.y) / (fhe.h - 1)));
                    int color = Color.HSBtoRGB(filterColorHue, filterColorSat, filterColorVal) | 0xFF000000;
                    MobEspManager.setFilterColor(expandedFilterColorKey, color, false);
                    FloydAddonsConfig.saveMobEsp();
                    break;
                }
            }
            return true;
        }
        if (filterColorDraggingHue && expandedFilterColorKey != null) {
            for (FilterHitEntry fhe : filterHitEntries) {
                if (fhe.action == FilterAction.FILTER_COLOR_HUE && fhe.key.equals(expandedFilterColorKey)) {
                    filterColorHue = (float) Math.max(0, Math.min(1, (my - fhe.y) / (fhe.h - 1)));
                    int color = Color.HSBtoRGB(filterColorHue, filterColorSat, filterColorVal) | 0xFF000000;
                    MobEspManager.setFilterColor(expandedFilterColorKey, color, false);
                    FloydAddonsConfig.saveMobEsp();
                    break;
                }
            }
            return true;
        }
        if (draggingSlider != null) {
            draggingSlider.setNormalized((float) Math.max(0, Math.min(1, (mx - draggingSliderX) / draggingSliderWidth)));
            FloydAddonsConfig.save(); return true;
        }
        if (draggingPanel != null && click.button() == 0) {
            ClickGuiConfig.PanelState state = ClickGuiConfig.getState(draggingPanel);
            state.x = Math.max(0, Math.min(width - getPanelWidth(draggingPanel), (int) (mx - dragOffsetX)));
            state.y = Math.max(0, Math.min(height - HEADER_HEIGHT, (int) (my - dragOffsetY)));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            if (draggingPopup) { draggingPopup = false; return true; }
            if (draggingSV || draggingHue) { draggingSV = false; draggingHue = false; return true; }
            if (filterColorDraggingSV || filterColorDraggingHue) { filterColorDraggingSV = false; filterColorDraggingHue = false; return true; }
            if (draggingPanel != null) { draggingPanel = null; FloydAddonsConfig.save(); return true; }
            if (draggingSlider != null) { draggingSlider = null; return true; }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (popupModule != null && mouseX >= popupX && mouseX <= popupX + popupWidth
                && mouseY >= popupY && mouseY <= popupY + popupHeight) {
            if (expandedFilterSetting != null) {
                filterScrollOffset -= (int) (verticalAmount * FILTER_ENTRY_HEIGHT * 2);
                filterScrollOffset = Math.max(0, Math.min(filterScrollOffset, filterMaxScroll));
            }
            return true;
        }
        for (var entry : modules.entrySet()) {
            ClickGuiConfig.PanelState state = ClickGuiConfig.getState(entry.getKey());
            int px = state.x, py = state.y, pw = getPanelWidth(entry.getKey());
            List<ModuleEntry> filtered = filterModules(entry.getValue());
            int totalHeight = HEADER_HEIGHT + filtered.size() * MODULE_HEIGHT;
            if (mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + totalHeight) {
                int contentHeight = filtered.size() * MODULE_HEIGHT;
                state.scrollOffset = Math.max(0, Math.min(Math.max(0, contentHeight - 200),
                        state.scrollOffset - (float) verticalAmount * 16));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (editingText != null) {
            if (input.key() == 259 && !textEditBuffer.isEmpty()) { textEditBuffer = textEditBuffer.substring(0, textEditBuffer.length() - 1); return true; }
            if (input.isEnter()) { finishTextEdit(); return true; }
            if (input.isEscape()) { editingText = null; textEditBuffer = ""; return true; }
            return true;
        }
        if (nameFilterOriginalFocused || nameFilterFakeFocused) {
            if (input.key() == 258) { // Tab key
                if (nameFilterOriginalFocused) { nameFilterOriginalFocused = false; nameFilterFakeFocused = true; }
                else { nameFilterFakeFocused = false; nameFilterOriginalFocused = true; }
                return true;
            }
            if (input.key() == 259) { // Backspace
                if (nameFilterOriginalFocused && !nameFilterOriginalQuery.isEmpty()) nameFilterOriginalQuery = nameFilterOriginalQuery.substring(0, nameFilterOriginalQuery.length() - 1);
                else if (nameFilterFakeFocused && !nameFilterFakeQuery.isEmpty()) nameFilterFakeQuery = nameFilterFakeQuery.substring(0, nameFilterFakeQuery.length() - 1);
                return true;
            }
            if (input.isEnter()) { executeDualNameAdd(); return true; }
            if (input.isEscape()) { nameFilterOriginalFocused = false; nameFilterFakeFocused = false; return true; }
            return true;
        }
        if (filterSearchFocused) {
            if (input.key() == 259 && !filterSearchQuery.isEmpty()) { filterSearchQuery = filterSearchQuery.substring(0, filterSearchQuery.length() - 1); return true; }
            if (input.isEnter() && expandedFilterSetting != null && !filterSearchQuery.isEmpty()) { executeSearchAdd(expandedFilterSetting); return true; }
            if (input.isEscape()) {
                filterSearchFocused = false; filterSearchQuery = "";
                return true;
            }
            return true;
        }
        if (searchFocused) {
            if (input.key() == 259 && !searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); return true; }
            if (input.isEscape()) { searchFocused = false; searchQuery = ""; return true; }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (editingText != null && input.codepoint() >= 32) { textEditBuffer += input.asString(); return true; }
        if (nameFilterOriginalFocused && input.codepoint() >= 32) { nameFilterOriginalQuery += input.asString(); return true; }
        if (nameFilterFakeFocused && input.codepoint() >= 32) { nameFilterFakeQuery += input.asString(); return true; }
        if (filterSearchFocused && input.codepoint() >= 32) { filterSearchQuery += input.asString(); return true; }
        if (searchFocused && input.codepoint() >= 32) { searchQuery += input.asString(); return true; }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (closing) return;
        finishTextEdit(); FloydAddonsConfig.save();
        closing = true; closeStartMs = Util.getMeasuringTimeMs();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    // --- Filter Data ---

    private void refreshBlockFilterData() {
        cachedActiveBlocks = new ArrayList<>(RenderConfig.getXrayOpaqueBlocks());
        cachedNearbyBlocks = suggestNearbyBlocks();
    }

    private void refreshMobFilterData() {
        cachedMobEntities.clear();
        cachedMobActiveNames = new ArrayList<>(MobEspManager.getNameFilters());
        cachedMobActiveTypes = new ArrayList<>();
        for (Identifier id : MobEspManager.getTypeFilters()) cachedMobActiveTypes.add(id.toString());
        cachedMobNearbyNames = suggestNearbyEntityNames();
        cachedMobNearbyTypes = suggestNearbyEntityTypes();
    }

    private void refreshNameFilterData() {
        cachedNameMappings = new ArrayList<>(NickHiderConfig.getNameMappings().entrySet());
        cachedOnlinePlayers = suggestOnlinePlayers();
    }

    private void refreshPlayerPickerData() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null) {
            cachedStalkPlayers = Collections.emptyList();
            return;
        }
        String selfName = mc.player != null ? mc.player.getGameProfile().name() : null;
        List<String> result = new ArrayList<>();
        for (var entry : mc.getNetworkHandler().getPlayerList()) {
            if (!isRealPlayerEntry(entry)) continue;
            String name = entry.getProfile().name();
            if (selfName != null && name.equalsIgnoreCase(selfName)) continue;
            result.add(name);
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        cachedStalkPlayers = result;
    }

    private List<String> suggestOnlinePlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null) return Collections.emptyList();
        Map<String, String> active = NickHiderConfig.getNameMappings();
        String selfName = mc.player != null ? mc.player.getGameProfile().name() : null;
        List<String> result = new ArrayList<>();
        for (var entry : mc.getNetworkHandler().getPlayerList()) {
            if (!isRealPlayerEntry(entry)) continue;
            String name = entry.getProfile().name();
            if (selfName != null && name.equalsIgnoreCase(selfName)) continue;
            if (!active.containsKey(name)) result.add(name);
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private static boolean isRealPlayerEntry(net.minecraft.client.network.PlayerListEntry entry) {
        String name = entry.getProfile().name();
        if (name == null || name.length() < 3 || name.length() > 16) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) return false;
        }
        // Real Mojang players have version 4 (random) UUIDs; server NPCs use version 3 (name-based)
        java.util.UUID id = entry.getProfile().id();
        return id != null && id.version() == 4;
    }

    private List<String> suggestNearbyBlocks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return Collections.emptyList();
        Set<String> active = RenderConfig.getXrayOpaqueBlocks();
        Set<String> found = new LinkedHashSet<>();
        BlockPos center = mc.player.getBlockPos();
        for (int dx = -8; dx <= 8; dx++)
            for (int dy = -8; dy <= 8; dy++)
                for (int dz = -8; dz <= 8; dz++) {
                    BlockState state = mc.world.getBlockState(center.add(dx, dy, dz));
                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (!active.contains(id)) found.add(id);
                }
        return new ArrayList<>(found);
    }

    private boolean isRealPlayer(Entity entity) {
        if (!(entity instanceof PlayerEntity pe)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return false;
        return mc.getNetworkHandler().getPlayerListEntry(pe.getUuid()) != null;
    }

    private List<String> suggestNearbyEntityNames() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return Collections.emptyList();
        Set<String> active = MobEspManager.getNameFilters();
        Set<String> found = new LinkedHashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (isRealPlayer(entity)) continue;
            String name = entity.getName().getString().replaceAll("\u00a7.", "").trim();
            if (!name.isEmpty() && !active.contains(name)) found.add(name);
            if (entity.hasCustomName() && entity.getCustomName() != null) {
                String custom = entity.getCustomName().getString().replaceAll("\u00a7.", "").trim();
                if (!custom.isEmpty() && !active.contains(custom)) found.add(custom);
            }
            String cached = NpcTracker.getCachedName(entity);
            if (cached != null && !cached.isEmpty() && active.stream().noneMatch(f -> f.equalsIgnoreCase(cached)))
                found.add(cached);
        }
        return new ArrayList<>(found);
    }

    private List<String> suggestNearbyEntityTypes() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return Collections.emptyList();
        Set<Identifier> active = MobEspManager.getTypeFilters();
        Set<String> found = new LinkedHashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (isRealPlayer(entity)) continue;
            Identifier typeId = EntityType.getId(entity.getType());
            if (!active.contains(typeId)) found.add(typeId.toString());
        }
        return new ArrayList<>(found);
    }

    private void executeDualNameAdd() {
        String og = nameFilterOriginalQuery.trim();
        String fake = nameFilterFakeQuery.trim();
        if (og.isEmpty() || fake.isEmpty()) return;
        NickHiderConfig.addNameMapping(og, fake);
        FloydAddonsConfig.saveNameMappings();
        nameFilterOriginalQuery = "";
        nameFilterFakeQuery = "";
        nameFilterOriginalFocused = false;
        nameFilterFakeFocused = false;
        refreshNameFilterData();
    }

    private void executeSearchAdd(ModuleEntry.SubSetting setting) {
        String query = filterSearchQuery.trim();
        if (query.isEmpty()) return;
        if (setting instanceof ModuleEntry.BlockFilterSetting) {
            RenderConfig.addXrayOpaqueBlock(query);
            FloydAddonsConfig.saveXrayOpaque();
            refreshBlockFilterData();
        } else if (setting instanceof ModuleEntry.MobFilterSetting) {
            if (query.contains(":")) {
                MobEspManager.addTypeFilter(query);
            } else {
                MobEspManager.addNameFilter(query);
            }
            FloydAddonsConfig.saveMobEsp();
            refreshMobFilterData();
        } else if (setting instanceof ModuleEntry.NameFilterSetting) {
            if (pendingNameMappingOriginal != null) {
                // Step 2: query is the fake name
                NickHiderConfig.addNameMapping(pendingNameMappingOriginal, query);
                FloydAddonsConfig.saveNameMappings();
                pendingNameMappingOriginal = null;
                refreshNameFilterData();
            } else {
                // Step 1: query is the original name, prompt for fake name
                pendingNameMappingOriginal = query;
                filterSearchQuery = "";
                filterSearchFocused = true;
                return;
            }
        } else if (setting instanceof ModuleEntry.PlayerPickerSetting) {
            StalkManager.setTarget(query);
            FloydAddonsConfig.save();
        }
        filterSearchQuery = "";
    }

    private void executeFilterAction(FilterHitEntry entry, double mx, double my) {
        switch (entry.action) {
            case ADD_BLOCK -> { RenderConfig.addXrayOpaqueBlock(entry.key); FloydAddonsConfig.saveXrayOpaque(); refreshBlockFilterData(); }
            case REMOVE_BLOCK -> { RenderConfig.removeXrayOpaqueBlock(entry.key); FloydAddonsConfig.saveXrayOpaque(); refreshBlockFilterData(); }
            case ADD_MOB_NAME -> { MobEspManager.addNameFilter(entry.key); FloydAddonsConfig.saveMobEsp(); refreshMobFilterData(); }
            case REMOVE_MOB_NAME -> { MobEspManager.removeNameFilter(entry.key); FloydAddonsConfig.saveMobEsp(); if (entry.key.equals(expandedFilterColorKey)) expandedFilterColorKey = null; refreshMobFilterData(); }
            case ADD_MOB_TYPE -> { MobEspManager.addTypeFilter(entry.key); FloydAddonsConfig.saveMobEsp(); refreshMobFilterData(); }
            case REMOVE_MOB_TYPE -> { MobEspManager.removeTypeFilter(entry.key); FloydAddonsConfig.saveMobEsp(); if (entry.key.equals(expandedFilterColorKey)) expandedFilterColorKey = null; refreshMobFilterData(); }
            case ADD_NAME_MAPPING -> { nameFilterOriginalQuery = entry.key; nameFilterFakeQuery = ""; nameFilterFakeFocused = true; nameFilterOriginalFocused = false; filterSearchFocused = false; searchFocused = false; }
            case REMOVE_NAME_MAPPING -> { NickHiderConfig.removeNameMapping(entry.key); FloydAddonsConfig.saveNameMappings(); revealedMappingNames.remove(entry.key); refreshNameFilterData(); }
            case TOGGLE_NAME_REVEAL -> { if (!revealedMappingNames.remove(entry.key)) revealedMappingNames.add(entry.key); }
            case PICK_PLAYER -> { StalkManager.setTarget(entry.key); FloydAddonsConfig.save(); }
            case FILTER_COLOR -> {
                if (entry.key.equals(expandedFilterColorKey)) {
                    expandedFilterColorKey = null;
                } else {
                    expandedFilterColorKey = entry.key;
                    int[] ci = MobEspManager.getColorForFilter(entry.key);
                    int initColor = ci != null ? ci[0] : RenderConfig.getDefaultEspColor();
                    float[] hsv = Color.RGBtoHSB((initColor >> 16) & 0xFF, (initColor >> 8) & 0xFF, initColor & 0xFF, null);
                    filterColorHue = hsv[0]; filterColorSat = hsv[1]; filterColorVal = hsv[2];
                }
            }
            case FILTER_COLOR_SV -> {
                int[] ci = MobEspManager.getColorForFilter(entry.key);
                if (ci == null || ci[1] != 1) {
                    filterColorDraggingSV = true;
                    filterColorSat = (float) Math.max(0, Math.min(1, (mx - entry.x) / (entry.w - 1)));
                    filterColorVal = (float) Math.max(0, Math.min(1, 1.0 - (my - entry.y) / (entry.h - 1)));
                    int color = Color.HSBtoRGB(filterColorHue, filterColorSat, filterColorVal) | 0xFF000000;
                    MobEspManager.setFilterColor(entry.key, color, false);
                    FloydAddonsConfig.saveMobEsp();
                }
            }
            case FILTER_COLOR_HUE -> {
                int[] ci = MobEspManager.getColorForFilter(entry.key);
                if (ci == null || ci[1] != 1) {
                    filterColorDraggingHue = true;
                    filterColorHue = (float) Math.max(0, Math.min(1, (my - entry.y) / (entry.h - 1)));
                    int color = Color.HSBtoRGB(filterColorHue, filterColorSat, filterColorVal) | 0xFF000000;
                    MobEspManager.setFilterColor(entry.key, color, false);
                    FloydAddonsConfig.saveMobEsp();
                }
            }
            case FILTER_COLOR_CHROMA -> {
                int[] ci = MobEspManager.getColorForFilter(entry.key);
                boolean wasChroma = ci != null && ci[1] == 1;
                int color = ci != null ? ci[0] : (Color.HSBtoRGB(filterColorHue, filterColorSat, filterColorVal) | 0xFF000000);
                MobEspManager.setFilterColor(entry.key, color, !wasChroma);
                FloydAddonsConfig.saveMobEsp();
            }
        }
    }

    private List<String> filterBySearch(List<String> items) {
        if (filterSearchQuery.isEmpty()) return items;
        String q = filterSearchQuery.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : items) if (s.toLowerCase().contains(q)) result.add(s);
        return result;
    }

    private List<String> filterNamesByOriginalQuery(List<String> items) {
        if (nameFilterOriginalQuery.isEmpty()) return items;
        String q = nameFilterOriginalQuery.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : items) if (s.toLowerCase().contains(q)) result.add(s);
        return result;
    }

    private List<String> filterBySearchWithMappings(List<String> items, Map<String, String> mappings) {
        if (filterSearchQuery.isEmpty()) return items;
        String q = filterSearchQuery.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : items) {
            if (s.toLowerCase().contains(q)) { result.add(s); continue; }
            String mapped = mappings.get(s);
            if (mapped != null && mapped.toLowerCase().contains(q)) result.add(s);
        }
        return result;
    }

    // --- Utilities ---

    private List<ModuleEntry> filterModules(List<ModuleEntry> entries) {
        if (searchQuery.isEmpty()) return entries;
        String query = searchQuery.toLowerCase();
        List<ModuleEntry> filtered = new ArrayList<>();
        for (ModuleEntry e : entries)
            if (e.getName().toLowerCase().contains(query) || e.getDescription().toLowerCase().contains(query))
                filtered.add(e);
        return filtered;
    }

    private String truncateLabel(String label, int maxWidth) {
        if (textRenderer.getWidth(label) <= maxWidth) return label;
        return textRenderer.trimToWidth(label, maxWidth - textRenderer.getWidth("...")) + "...";
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private void drawSolidBorder(DrawContext ctx, int left, int top, int right, int bottom, int color) {
        ctx.fill(left, top, right, top + 1, color);
        ctx.fill(left, bottom - 1, right, bottom, color);
        ctx.fill(left, top, left + 1, bottom, color);
        ctx.fill(right - 1, top, right, bottom, color);
    }

    private static void openPath(java.nio.file.Path path) {
        String target = path.toAbsolutePath().toString();
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("cmd", "/c", "start", "", target);
            else if (os.contains("mac")) pb = new ProcessBuilder("open", target);
            else pb = new ProcessBuilder("xdg-open", target);
            pb.start();
        } catch (Exception ignored) {}
    }

    private enum FilterAction {
        ADD_BLOCK, REMOVE_BLOCK, ADD_MOB_NAME, REMOVE_MOB_NAME, ADD_MOB_TYPE, REMOVE_MOB_TYPE,
        ADD_NAME_MAPPING, REMOVE_NAME_MAPPING, TOGGLE_NAME_REVEAL, PICK_PLAYER,
        FILTER_COLOR, FILTER_COLOR_SV, FILTER_COLOR_HUE, FILTER_COLOR_CHROMA
    }

    private static class FilterHitEntry {
        final int x, y, w, h;
        final String key;
        final FilterAction action;
        FilterHitEntry(int x, int y, int w, int h, String key, FilterAction action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.key = key; this.action = action;
        }
    }
}
