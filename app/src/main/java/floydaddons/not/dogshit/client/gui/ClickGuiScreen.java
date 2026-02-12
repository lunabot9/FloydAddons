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

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
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

    private ModuleEntry.SubSetting expandedFilterSetting = null;
    private String filterSearchQuery = "";
    private boolean filterSearchFocused = false;
    private int filterScrollOffset = 0;
    private int filterMaxScroll = 0;
    private List<String> cachedActiveBlocks = new ArrayList<>();
    private List<String> cachedNearbyBlocks = new ArrayList<>();
    private List<String> cachedMobActiveNames = new ArrayList<>();
    private List<String> cachedMobActiveTypes = new ArrayList<>();
    private List<String> cachedMobNearbyNames = new ArrayList<>();
    private List<String> cachedMobNearbyTypes = new ArrayList<>();
    private final List<FilterHitEntry> filterHitEntries = new ArrayList<>();
    private int filterListTop, filterListBottom, filterListLeft, filterListRight;

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
        expandedFilterSetting = null;
        filterSearchQuery = "";
        filterSearchFocused = false;
        filterScrollOffset = 0;
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

        render.add(new ModuleEntry("Stalk Player", "Track a player with a tracer line",
                StalkManager::isEnabled,
                () -> { if (StalkManager.isEnabled()) StalkManager.disable(); },
                List.of(
                        new ModuleEntry.TextSetting("Target", StalkManager::getTargetName,
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
                () -> RenderConfig.isButtonTextChromaEnabled() || RenderConfig.isButtonBorderChromaEnabled() || RenderConfig.isGuiBorderChromaEnabled(),
                () -> {},
                List.of(
                        new ModuleEntry.ColorSetting("Text Color",
                                RenderConfig::getButtonTextColor, RenderConfig::setButtonTextColor,
                                RenderConfig::isButtonTextChromaEnabled, RenderConfig::setButtonTextChromaEnabled),
                        new ModuleEntry.ColorSetting("Button Border Color",
                                RenderConfig::getButtonBorderColor, RenderConfig::setButtonBorderColor,
                                RenderConfig::isButtonBorderChromaEnabled, RenderConfig::setButtonBorderChromaEnabled),
                        new ModuleEntry.ColorSetting("GUI Border Color",
                                RenderConfig::getGuiBorderColor, RenderConfig::setGuiBorderColor,
                                RenderConfig::isGuiBorderChromaEnabled, RenderConfig::setGuiBorderChromaEnabled)
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
                        new ModuleEntry.ButtonSetting("Edit Names",
                                () -> MinecraftClient.getInstance().setScreen(new NameMappingsEditorScreen(self))),
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
        if (RenderConfig.isButtonBorderChromaEnabled())
            return RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
        return RenderConfig.getButtonBorderColor();
    }

    private int getTextAccent() {
        if (RenderConfig.isButtonTextChromaEnabled())
            return RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
        return RenderConfig.getButtonTextColor();
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
        if ((s instanceof ModuleEntry.BlockFilterSetting || s instanceof ModuleEntry.MobFilterSetting) && s == expandedFilterSetting)
            return POPUP_SETTING_HEIGHT + FILTER_SEARCH_HEIGHT + getFilterVisibleHeight();
        return POPUP_SETTING_HEIGHT;
    }

    private int getFilterVisibleHeight() {
        return Math.min(FILTER_MAX_VISIBLE_HEIGHT, getFilterTotalContentHeight());
    }

    private int getFilterTotalContentHeight() {
        if (expandedFilterSetting instanceof ModuleEntry.BlockFilterSetting) {
            int count = 1 + filterBySearch(cachedActiveBlocks).size() + 1 + filterBySearch(cachedNearbyBlocks).size();
            return count * FILTER_ENTRY_HEIGHT;
        } else if (expandedFilterSetting instanceof ModuleEntry.MobFilterSetting) {
            int count = 1 + filterBySearch(cachedMobActiveNames).size() + 1 + filterBySearch(cachedMobActiveTypes).size()
                    + 1 + filterBySearch(cachedMobNearbyNames).size() + 1 + filterBySearch(cachedMobNearbyTypes).size();
            return count * FILTER_ENTRY_HEIGHT;
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
        // Header background = button border accent color (inverted style)
        context.fill(x, y, x + pw, y + HEADER_HEIGHT, applyAlpha(getButtonBorderAccent(), alpha));
        String name = category.getDisplayName() + (collapsed ? " [+]" : "");
        // Header text = text accent color
        context.drawTextWithShadow(textRenderer, name, x + 8,
                y + (HEADER_HEIGHT - textRenderer.fontHeight) / 2, applyAlpha(getTextAccent(), alpha));
    }

    private void renderModule(DrawContext context, ModuleEntry entry, int px, int y, int pw,
                               int mouseX, int mouseY, float alpha) {
        boolean hover = mouseX >= px && mouseX <= px + pw && mouseY >= y && mouseY <= y + MODULE_HEIGHT;
        if (hover) context.fill(px, y, px + pw, y + MODULE_HEIGHT, applyAlpha(0xFF1A1A1A, alpha));

        // White text normally, text accent when hovered
        int nameColor = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFFFFFFF, alpha);
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
        expandedFilterSetting = null;
        filterSearchQuery = "";
        filterSearchFocused = false;
        filterScrollOffset = 0;
        draggingSV = false;
        draggingHue = false;
    }

    private int calculatePopupWidth(ModuleEntry module) {
        int max = textRenderer.getWidth(module.getName()) + 40;
        boolean hasFilter = false;
        for (ModuleEntry.SubSetting s : module.getSettings()) {
            max = Math.max(max, textRenderer.getWidth(s.getLabel()) + 80);
            if (s instanceof ModuleEntry.BlockFilterSetting || s instanceof ModuleEntry.MobFilterSetting) hasFilter = true;
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

        } else if (setting instanceof ModuleEntry.BlockFilterSetting || setting instanceof ModuleEntry.MobFilterSetting) {
            boolean expanded = setting == expandedFilterSetting;
            String label = "[" + setting.getLabel() + (expanded ? " \u25BE" : " \u25B8") + "]";
            int color = hover ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 10, y + (POPUP_SETTING_HEIGHT - textRenderer.fontHeight) / 2, color);
            if (expanded) {
                int filterY = y + POPUP_SETTING_HEIGHT;
                if (setting instanceof ModuleEntry.BlockFilterSetting)
                    renderInlineBlockFilter(context, px, filterY, pw, alpha, mouseX, mouseY);
                else
                    renderInlineMobFilter(context, px, filterY, pw, alpha, mouseX, mouseY);
            }
        }
    }

    // --- Inline Color Picker ---

    private void renderInlineColorPicker(DrawContext context, ModuleEntry.ColorSetting cs,
                                          int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        int svSize = 100, hueBarW = 10, hueBarH = 100;
        int svX = px + 10, svY = y + 4, hbX = svX + svSize + 8, hbY = svY;

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
        int previewColor = cs.isChroma() ? RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f)
                : (Color.HSBtoRGB(inlineHue, inlineSat, inlineVal) | 0xFF000000);
        context.fill(px + 10, infoY, px + 26, infoY + 16, applyAlpha(previewColor, alpha));
        String hex = "#" + String.format("%06X", previewColor & 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, hex, px + 32, infoY + 4, applyAlpha(0xFFCCCCCC, alpha));
        String chromaLabel = cs.isChroma() ? "Chroma: ON" : "Chroma: OFF";
        int chromaX = px + pw - textRenderer.getWidth(chromaLabel) - 10;
        boolean hoverChroma = mouseX >= chromaX && mouseX <= px + pw - 4 && mouseY >= infoY && mouseY <= infoY + 16;
        context.drawTextWithShadow(textRenderer, chromaLabel, chromaX, infoY + 4,
                hoverChroma ? applyAlpha(getTextAccent(), alpha) : applyAlpha(0xFFAAAAAA, alpha));
    }

    // --- Inline Block Filter ---

    private void renderInlineBlockFilter(DrawContext context, int px, int y, int pw, float alpha, int mouseX, int mouseY) {
        renderFilterSearchBar(context, px, y, pw, alpha);
        int listY = y + FILTER_SEARCH_HEIGHT + 2;
        int visibleH = getFilterVisibleHeight();
        filterListTop = listY; filterListBottom = listY + visibleH; filterListLeft = px; filterListRight = px + pw;
        filterHitEntries.clear();

        List<String> activeF = filterBySearch(cachedActiveBlocks);
        List<String> nearbyF = filterBySearch(cachedNearbyBlocks);
        int totalH = (1 + activeF.size() + 1 + nearbyF.size()) * FILTER_ENTRY_HEIGHT;
        filterMaxScroll = Math.max(0, totalH - FILTER_MAX_VISIBLE_HEIGHT);
        filterScrollOffset = Math.max(0, Math.min(filterScrollOffset, filterMaxScroll));

        context.enableScissor(px + 4, listY, px + pw - 4, filterListBottom);
        int entryY = listY - filterScrollOffset;
        int entryLeft = px + 6, entryRight = px + pw - 6;
        int accent = getButtonBorderAccent();

        context.drawTextWithShadow(textRenderer, "Active Blocks", entryLeft, entryY + 2, applyAlpha(accent, alpha));
        entryY += FILTER_ENTRY_HEIGHT;
        for (String id : activeF) {
            String label = truncateLabel(id, entryRight - entryLeft - FILTER_BTN_W - 6);
            context.drawTextWithShadow(textRenderer, label, entryLeft, entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
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

        entryY += 2;
        context.drawTextWithShadow(textRenderer, "Nearby Blocks", entryLeft, entryY + 2, applyAlpha(accent, alpha));
        entryY += FILTER_ENTRY_HEIGHT;
        for (String id : nearbyF) {
            String label = truncateLabel(id, entryRight - entryLeft - FILTER_BTN_W - 6);
            context.drawTextWithShadow(textRenderer, label, entryLeft, entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
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
        renderFilterSearchBar(context, px, y, pw, alpha);
        int listY = y + FILTER_SEARCH_HEIGHT + 2;
        int visibleH = getFilterVisibleHeight();
        filterListTop = listY; filterListBottom = listY + visibleH; filterListLeft = px; filterListRight = px + pw;
        filterHitEntries.clear();

        List<String> namesF = filterBySearch(cachedMobActiveNames);
        List<String> typesF = filterBySearch(cachedMobActiveTypes);
        List<String> nearNamesF = filterBySearch(cachedMobNearbyNames);
        List<String> nearTypesF = filterBySearch(cachedMobNearbyTypes);
        int totalH = (1 + namesF.size() + 1 + typesF.size() + 1 + nearNamesF.size() + 1 + nearTypesF.size()) * FILTER_ENTRY_HEIGHT;
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
        context.drawTextWithShadow(textRenderer, header, entryLeft, entryY + 2, applyAlpha(accent, alpha));
        entryY += FILTER_ENTRY_HEIGHT;
        for (String key : items) {
            String label = truncateLabel(key, entryRight - entryLeft - FILTER_BTN_W - 6);
            context.drawTextWithShadow(textRenderer, label, entryLeft, entryY + (FILTER_ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
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
        }
        return entryY;
    }

    private void renderFilterSearchBar(DrawContext context, int px, int y, int pw, float alpha) {
        context.fill(px + 6, y, px + pw - 6, y + FILTER_SEARCH_HEIGHT, applyAlpha(0xFF0A0A0A, alpha));
        drawSolidBorder(context, px + 5, y - 1, px + pw - 5, y + FILTER_SEARCH_HEIGHT + 1,
                applyAlpha(getButtonBorderAccent(), alpha));
        String display = filterSearchQuery.isEmpty() && !filterSearchFocused ? "Filter..." : filterSearchQuery + (filterSearchFocused ? "_" : "");
        int textColor = filterSearchQuery.isEmpty() && !filterSearchFocused ? applyAlpha(0xFF888888, alpha) : applyAlpha(0xFFFFFFFF, alpha);
        context.drawTextWithShadow(textRenderer, display, px + 10, y + (FILTER_SEARCH_HEIGHT - textRenderer.fontHeight) / 2, textColor);
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
                String chromaLabel = colorSetting.isChroma() ? "Chroma: ON" : "Chroma: OFF";
                int chromaX = popupX + popupWidth - textRenderer.getWidth(chromaLabel) - 10;
                if (my >= infoY && my <= infoY + 16 && mx >= chromaX) {
                    colorSetting.setChroma(!colorSetting.isChroma()); FloydAddonsConfig.save(); return;
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
                expandedFilterSetting = null; filterSearchFocused = false; filterSearchQuery = ""; filterScrollOffset = 0;
                int c = colorSetting.getColor();
                float[] hsv = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
                inlineHue = hsv[0]; inlineSat = hsv[1]; inlineVal = hsv[2];
            }
        } else if (setting instanceof ModuleEntry.ButtonSetting btn) {
            btn.click();
        } else if (setting instanceof ModuleEntry.CycleSetting cs) {
            cs.cycleForward(); FloydAddonsConfig.save();
        } else if (setting instanceof ModuleEntry.TextSetting ts) {
            editingText = ts; textEditBuffer = ts.getValue() != null ? ts.getValue() : "";
            searchFocused = false; filterSearchFocused = false;
        } else if (setting instanceof ModuleEntry.BlockFilterSetting || setting instanceof ModuleEntry.MobFilterSetting) {
            // Check if clicking the label area or the expanded area
            if (my < settingY + POPUP_SETTING_HEIGHT) {
                // Toggle expansion
                if (setting == expandedFilterSetting) {
                    expandedFilterSetting = null; filterSearchQuery = ""; filterSearchFocused = false; filterScrollOffset = 0;
                } else {
                    expandedFilterSetting = setting; expandedColorSetting = null;
                    filterSearchQuery = ""; filterSearchFocused = false; filterScrollOffset = 0;
                    if (setting instanceof ModuleEntry.BlockFilterSetting) refreshBlockFilterData();
                    else refreshMobFilterData();
                }
            } else if (setting == expandedFilterSetting) {
                int filterAreaY = settingY + POPUP_SETTING_HEIGHT;
                // Search bar click
                if (my < filterAreaY + FILTER_SEARCH_HEIGHT && mx >= popupX + 6 && mx <= popupX + popupWidth - 6) {
                    filterSearchFocused = true; searchFocused = false; return;
                }
                // Filter list button clicks
                for (FilterHitEntry entry : filterHitEntries) {
                    if (mx >= entry.x && mx <= entry.x + entry.w && my >= entry.y && my <= entry.y + entry.h
                            && my >= filterListTop && my <= filterListBottom) {
                        executeFilterAction(entry); return;
                    }
                }
            }
        }
    }

    private void updateInlineSV(double localX, double localY, int svSize, ModuleEntry.ColorSetting cs) {
        inlineSat = (float) Math.max(0, Math.min(1, localX / (svSize - 1)));
        inlineVal = (float) Math.max(0, Math.min(1, 1.0 - localY / (svSize - 1)));
        cs.setColor(Color.HSBtoRGB(inlineHue, inlineSat, inlineVal) | 0xFF000000);
        FloydAddonsConfig.save();
    }

    private void updateInlineHue(double localY, int hueBarH, ModuleEntry.ColorSetting cs) {
        inlineHue = (float) Math.max(0, Math.min(1, localY / (hueBarH - 1)));
        cs.setColor(Color.HSBtoRGB(inlineHue, inlineSat, inlineVal) | 0xFF000000);
        FloydAddonsConfig.save();
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
        if (filterSearchFocused) {
            if (input.key() == 259 && !filterSearchQuery.isEmpty()) { filterSearchQuery = filterSearchQuery.substring(0, filterSearchQuery.length() - 1); return true; }
            if (input.isEscape()) { filterSearchFocused = false; filterSearchQuery = ""; return true; }
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
        cachedMobActiveNames = new ArrayList<>(MobEspManager.getNameFilters());
        cachedMobActiveTypes = new ArrayList<>();
        for (Identifier id : MobEspManager.getTypeFilters()) cachedMobActiveTypes.add(id.toString());
        cachedMobNearbyNames = suggestNearbyEntityNames();
        cachedMobNearbyTypes = suggestNearbyEntityTypes();
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

    private List<String> suggestNearbyEntityNames() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return Collections.emptyList();
        Set<String> active = MobEspManager.getNameFilters();
        Set<String> found = new LinkedHashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
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
            Identifier typeId = EntityType.getId(entity.getType());
            if (!active.contains(typeId)) found.add(typeId.toString());
        }
        return new ArrayList<>(found);
    }

    private void executeFilterAction(FilterHitEntry entry) {
        switch (entry.action) {
            case ADD_BLOCK -> { RenderConfig.addXrayOpaqueBlock(entry.key); FloydAddonsConfig.saveXrayOpaque(); refreshBlockFilterData(); }
            case REMOVE_BLOCK -> { RenderConfig.removeXrayOpaqueBlock(entry.key); FloydAddonsConfig.saveXrayOpaque(); refreshBlockFilterData(); }
            case ADD_MOB_NAME -> { MobEspManager.addNameFilter(entry.key); FloydAddonsConfig.saveMobEsp(); refreshMobFilterData(); }
            case REMOVE_MOB_NAME -> { MobEspManager.removeNameFilter(entry.key); FloydAddonsConfig.saveMobEsp(); refreshMobFilterData(); }
            case ADD_MOB_TYPE -> { MobEspManager.addTypeFilter(entry.key); FloydAddonsConfig.saveMobEsp(); refreshMobFilterData(); }
            case REMOVE_MOB_TYPE -> { MobEspManager.removeTypeFilter(entry.key); FloydAddonsConfig.saveMobEsp(); refreshMobFilterData(); }
        }
    }

    private List<String> filterBySearch(List<String> items) {
        if (filterSearchQuery.isEmpty()) return items;
        String q = filterSearchQuery.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : items) if (s.toLowerCase().contains(q)) result.add(s);
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
        ADD_BLOCK, REMOVE_BLOCK, ADD_MOB_NAME, REMOVE_MOB_NAME, ADD_MOB_TYPE, REMOVE_MOB_TYPE
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
