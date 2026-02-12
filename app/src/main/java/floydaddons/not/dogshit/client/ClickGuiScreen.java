package floydaddons.not.dogshit.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OdinClient-style ClickGUI with draggable vertical panels, one per category.
 * Left-click toggles modules, right-click expands sub-settings.
 * All config is accessible inline — no need for separate screens.
 */
public class ClickGuiScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int HEADER_HEIGHT = 24;
    private static final int MODULE_HEIGHT = 22;
    private static final int SETTING_HEIGHT = 18;
    private static final int SEARCH_BAR_HEIGHT = 22;
    private static final int SEARCH_BAR_WIDTH = 200;
    private static final long FADE_DURATION_MS = 120;

    private static final int COLOR_HEADER = 0xFF1A1A1A;
    private static final int COLOR_MODULE = 0xFF2A2A2A;
    private static final int COLOR_MODULE_HOVER = 0xFF353535;
    private static final int COLOR_MODULE_ENABLED = 0xFF2A3A2A;
    private static final int COLOR_SETTING_BG = 0xFF222222;
    private static final int COLOR_SLIDER_BG = 0xFF333333;

    private final Map<ModuleCategory, List<ModuleEntry>> modules = new LinkedHashMap<>();
    private ModuleCategory draggingPanel = null;
    private int dragOffsetX, dragOffsetY;
    private String expandedModule = null;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private ModuleEntry.SliderSetting draggingSlider = null;
    private int draggingSliderX, draggingSliderWidth;

    // Inline text editing state
    private ModuleEntry.TextSetting editingText = null;
    private String textEditBuffer = "";

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
        expandedModule = null;
        searchQuery = "";
        searchFocused = false;
        draggingSlider = null;
        editingText = null;
        textEditBuffer = "";
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
                        new ModuleEntry.ButtonSetting("Edit Blocks",
                                () -> MinecraftClient.getInstance().setScreen(new XrayEditorScreen(self)))
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
                        new ModuleEntry.ColorSetting("Stalk Tracer Color",
                                RenderConfig::getStalkTracerColor, RenderConfig::setStalkTracerColor,
                                RenderConfig::isStalkTracerChromaEnabled, RenderConfig::setStalkTracerChromaEnabled),
                        new ModuleEntry.ButtonSetting("Edit Filters",
                                () -> MinecraftClient.getInstance().setScreen(new MobEspEditorScreen(self)))
                )));

        render.add(new ModuleEntry("Cape", "Custom cape cosmetic",
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

        render.add(new ModuleEntry("Cone Hat", "Floyd cone hat cosmetic",
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

        render.add(new ModuleEntry("Server ID Hider", "Hide server address display",
                RenderConfig::isServerIdHiderEnabled,
                () -> RenderConfig.setServerIdHiderEnabled(!RenderConfig.isServerIdHiderEnabled())));

        render.add(new ModuleEntry("Inventory HUD", "Show inventory overlay",
                RenderConfig::isInventoryHudEnabled,
                () -> RenderConfig.setInventoryHudEnabled(!RenderConfig.isInventoryHudEnabled()),
                List.of(
                        new ModuleEntry.ButtonSetting("Edit Layout",
                                () -> MinecraftClient.getInstance().setScreen(new MoveHudScreen(self)))
                )));

        render.add(new ModuleEntry("Custom Scoreboard", "Styled scoreboard sidebar",
                RenderConfig::isCustomScoreboardEnabled,
                () -> RenderConfig.setCustomScoreboardEnabled(!RenderConfig.isCustomScoreboardEnabled()),
                List.of(
                        new ModuleEntry.ButtonSetting("Edit Layout",
                                () -> MinecraftClient.getInstance().setScreen(new MoveHudScreen(self)))
                )));

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

        // Hiders (merged into Render panel)
        addHiderToggle(render, "No Hurt Camera", "Remove damage camera shake", HidersConfig::isNoHurtCameraEnabled,
                () -> HidersConfig.setNoHurtCameraEnabled(!HidersConfig.isNoHurtCameraEnabled()));
        addHiderToggle(render, "Remove Fire Overlay", "Hide fire screen overlay", HidersConfig::isRemoveFireOverlayEnabled,
                () -> HidersConfig.setRemoveFireOverlayEnabled(!HidersConfig.isRemoveFireOverlayEnabled()));
        addHiderToggle(render, "Disable Hunger Bar", "Hide hunger display", HidersConfig::isDisableHungerBarEnabled,
                () -> HidersConfig.setDisableHungerBarEnabled(!HidersConfig.isDisableHungerBarEnabled()));
        addHiderToggle(render, "Hide Potion Effects", "Hide potion effect icons", HidersConfig::isHidePotionEffectsEnabled,
                () -> HidersConfig.setHidePotionEffectsEnabled(!HidersConfig.isHidePotionEffectsEnabled()));
        addHiderToggle(render, "3rd Person Crosshair", "Show crosshair in 3rd person", HidersConfig::isThirdPersonCrosshairEnabled,
                () -> HidersConfig.setThirdPersonCrosshairEnabled(!HidersConfig.isThirdPersonCrosshairEnabled()));
        addHiderToggle(render, "Hide Entity Fire", "Hide fire on entities", HidersConfig::isHideEntityFireEnabled,
                () -> HidersConfig.setHideEntityFireEnabled(!HidersConfig.isHideEntityFireEnabled()));
        addHiderToggle(render, "Disable Arrows", "Hide arrows stuck in models", HidersConfig::isDisableAttachedArrowsEnabled,
                () -> HidersConfig.setDisableAttachedArrowsEnabled(!HidersConfig.isDisableAttachedArrowsEnabled()));
        addHiderToggle(render, "Remove Falling Blocks", "Hide falling block entities", HidersConfig::isRemoveFallingBlocksEnabled,
                () -> HidersConfig.setRemoveFallingBlocksEnabled(!HidersConfig.isRemoveFallingBlocksEnabled()));
        addHiderToggle(render, "No Explosion Particles", "Hide explosion particles", HidersConfig::isRemoveExplosionParticlesEnabled,
                () -> HidersConfig.setRemoveExplosionParticlesEnabled(!HidersConfig.isRemoveExplosionParticlesEnabled()));
        addHiderToggle(render, "Remove Tab Ping", "Hide ping icons in tab list", HidersConfig::isRemoveTabPingEnabled,
                () -> HidersConfig.setRemoveTabPingEnabled(!HidersConfig.isRemoveTabPingEnabled()));
        addHiderToggle(render, "Hide Ground Arrows", "Hide arrows stuck in ground", HidersConfig::isHideGroundedArrowsEnabled,
                () -> HidersConfig.setHideGroundedArrowsEnabled(!HidersConfig.isHideGroundedArrowsEnabled()));

        // Attack Animation (merged into Render panel)
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

        // ═══════════════════════ PLAYER ═══════════════════════

        List<ModuleEntry> player = new ArrayList<>();

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

    private static void addHiderToggle(List<ModuleEntry> list, String name, String desc,
                                        java.util.function.BooleanSupplier getter, Runnable toggle) {
        list.add(new ModuleEntry(name, desc, getter, toggle));
    }

    // --- Text Edit Support ---

    private void finishTextEdit() {
        if (editingText != null) {
            if (!textEditBuffer.isEmpty()) {
                editingText.setValue(textEditBuffer);
            }
            FloydAddonsConfig.save();
            editingText = null;
            textEditBuffer = "";
        }
    }

    // --- Rendering ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float openProgress = Math.min(1.0f, (Util.getMeasuringTimeMs() - openStartMs) / (float) FADE_DURATION_MS);
        float closeProgress = closing ? Math.min(1.0f, (Util.getMeasuringTimeMs() - closeStartMs) / (float) FADE_DURATION_MS) : 0f;
        if (closing && closeProgress >= 1.0f) {
            super.close();
            return;
        }
        float alpha = closing ? (1.0f - closeProgress) : openProgress;
        if (alpha <= 0f) return;

        // Dark overlay background
        context.fill(0, 0, width, height, applyAlpha(0x88000000, alpha));

        // Search bar
        renderSearchBar(context, mouseX, mouseY, alpha);

        // Render each category panel
        for (var entry : modules.entrySet()) {
            renderPanel(context, entry.getKey(), entry.getValue(), mouseX, mouseY, alpha);
        }
    }

    private void renderSearchBar(DrawContext context, int mouseX, int mouseY, float alpha) {
        int x = (width - SEARCH_BAR_WIDTH) / 2;
        int y = 4;
        context.fill(x, y, x + SEARCH_BAR_WIDTH, y + SEARCH_BAR_HEIGHT, applyAlpha(0xFF1A1A1A, alpha));
        InventoryHudRenderer.drawChromaBorder(context, x - 1, y - 1, x + SEARCH_BAR_WIDTH + 1, y + SEARCH_BAR_HEIGHT + 1, alpha);

        String display = searchQuery.isEmpty() && !searchFocused ? "Search..." : searchQuery + (searchFocused ? "_" : "");
        int textColor = searchQuery.isEmpty() && !searchFocused ? applyAlpha(0xFF888888, alpha) : applyAlpha(0xFFFFFFFF, alpha);
        context.drawTextWithShadow(textRenderer, display, x + 6, y + (SEARCH_BAR_HEIGHT - textRenderer.fontHeight) / 2, textColor);
    }

    private void renderPanel(DrawContext context, ModuleCategory category, List<ModuleEntry> entries,
                             int mouseX, int mouseY, float alpha) {
        ClickGuiConfig.PanelState state = ClickGuiConfig.getState(category);
        int px = state.x;
        int py = state.y;

        List<ModuleEntry> filtered = filterModules(entries);
        int contentHeight = calculateContentHeight(filtered);
        int totalHeight = HEADER_HEIGHT + (state.collapsed ? 0 : contentHeight);

        // Panel background
        context.fill(px, py, px + PANEL_WIDTH, py + totalHeight, applyAlpha(COLOR_HEADER, alpha));

        // Header
        renderHeader(context, category, px, py, mouseX, mouseY, alpha, state.collapsed);

        if (!state.collapsed && !filtered.isEmpty()) {
            // Content area
            int contentTop = py + HEADER_HEIGHT;
            int contentBottom = py + totalHeight;
            context.enableScissor(px, contentTop, px + PANEL_WIDTH, contentBottom);

            int moduleY = contentTop - (int) state.scrollOffset;
            for (ModuleEntry entry : filtered) {
                renderModule(context, entry, px, moduleY, mouseX, mouseY, alpha);
                moduleY += MODULE_HEIGHT;

                if (entry.getName().equals(expandedModule) && entry.hasSettings()) {
                    for (ModuleEntry.SubSetting setting : entry.getSettings()) {
                        renderSetting(context, setting, px, moduleY, mouseX, mouseY, alpha);
                        moduleY += SETTING_HEIGHT;
                    }
                }
            }

            context.disableScissor();
        }

        // Chroma border around entire panel
        InventoryHudRenderer.drawChromaBorder(context, px - 1, py - 1, px + PANEL_WIDTH + 1, py + totalHeight + 1, alpha);
    }

    private void renderHeader(DrawContext context, ModuleCategory category, int x, int y,
                               int mouseX, int mouseY, float alpha, boolean collapsed) {
        boolean hover = mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        int headerColor = applyAlpha(hover ? 0xFF222222 : COLOR_HEADER, alpha);
        context.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, headerColor);

        String name = category.getDisplayName() + (collapsed ? " [+]" : "");
        int textColor = applyAlpha(resolveTextColor(0f), alpha);
        int textX = x + (PANEL_WIDTH - textRenderer.getWidth(name)) / 2;
        int textY = y + (HEADER_HEIGHT - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(textRenderer, name, textX, textY, textColor);
    }

    private void renderModule(DrawContext context, ModuleEntry entry, int px, int y,
                               int mouseX, int mouseY, float alpha) {
        boolean hover = mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= y && mouseY <= y + MODULE_HEIGHT;
        boolean enabled = entry.isEnabled();
        int bgColor;
        if (hover) {
            bgColor = enabled ? applyAlpha(0xFF2A4A2A, alpha) : applyAlpha(COLOR_MODULE_HOVER, alpha);
        } else {
            bgColor = enabled ? applyAlpha(COLOR_MODULE_ENABLED, alpha) : applyAlpha(COLOR_MODULE, alpha);
        }
        context.fill(px, y, px + PANEL_WIDTH, y + MODULE_HEIGHT, bgColor);

        // Module name — respects text color settings
        int nameColor = enabled ? applyAlpha(resolveTextColor(0f), alpha) : applyAlpha(0xFFCCCCCC, alpha);
        context.drawTextWithShadow(textRenderer, entry.getName(), px + 8, y + (MODULE_HEIGHT - textRenderer.fontHeight) / 2, nameColor);

        // Status indicator (functional colors, always green/gray)
        String status = enabled ? "ON" : "OFF";
        int statusColor = enabled ? applyAlpha(0xFF44FF44, alpha) : applyAlpha(0xFF666666, alpha);
        int statusX = px + PANEL_WIDTH - textRenderer.getWidth(status) - 8;
        context.drawTextWithShadow(textRenderer, status, statusX, y + (MODULE_HEIGHT - textRenderer.fontHeight) / 2, statusColor);

        // Expand indicator if module has settings
        if (entry.hasSettings()) {
            String arrow = entry.getName().equals(expandedModule) ? "v" : ">";
            int arrowColor = applyAlpha(0xFF888888, alpha);
            context.drawTextWithShadow(textRenderer, arrow, px + PANEL_WIDTH - 30 - textRenderer.getWidth(status), y + (MODULE_HEIGHT - textRenderer.fontHeight) / 2, arrowColor);
        }
    }

    private void renderSetting(DrawContext context, ModuleEntry.SubSetting setting, int px, int y,
                                int mouseX, int mouseY, float alpha) {
        context.fill(px, y, px + PANEL_WIDTH, y + SETTING_HEIGHT, applyAlpha(COLOR_SETTING_BG, alpha));

        if (setting instanceof ModuleEntry.BooleanSetting boolSetting) {
            String label = "  " + setting.getLabel();
            boolean on = boolSetting.isEnabled();
            int labelColor = on ? applyAlpha(resolveTextColor(0f), alpha) : applyAlpha(0xFFAAAAAA, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 14, y + (SETTING_HEIGHT - textRenderer.fontHeight) / 2, labelColor);

            String val = on ? "ON" : "OFF";
            int valColor = on ? applyAlpha(0xFF44FF44, alpha) : applyAlpha(0xFF666666, alpha);
            context.drawTextWithShadow(textRenderer, val, px + PANEL_WIDTH - textRenderer.getWidth(val) - 8,
                    y + (SETTING_HEIGHT - textRenderer.fontHeight) / 2, valColor);

        } else if (setting instanceof ModuleEntry.SliderSetting slider) {
            String label = "  " + setting.getLabel() + ": " + slider.getFormattedValue();
            context.drawTextWithShadow(textRenderer, label, px + 14, y + 2, applyAlpha(0xFFAAAAAA, alpha));

            int barX = px + 14;
            int barY = y + SETTING_HEIGHT - 5;
            int barW = PANEL_WIDTH - 28;
            context.fill(barX, barY, barX + barW, barY + 3, applyAlpha(COLOR_SLIDER_BG, alpha));
            int fillW = (int) (barW * slider.getNormalized());
            context.fill(barX, barY, barX + fillW, barY + 3, applyAlpha(resolveTextColor(0f), alpha));

        } else if (setting instanceof ModuleEntry.ColorSetting colorSetting) {
            // Label on left, clickable color preview square on right (where ON/OFF would be)
            String label = "  " + setting.getLabel();
            boolean hover = mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= y && mouseY <= y + SETTING_HEIGHT;
            int labelColor = hover ? applyAlpha(resolveTextColor(0f), alpha) : applyAlpha(0xFFAAAAAA, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 14, y + (SETTING_HEIGHT - textRenderer.fontHeight) / 2, labelColor);

            // Color preview square (10x10, right-aligned)
            int sqSize = 10;
            int sqX = px + PANEL_WIDTH - sqSize - 8;
            int sqY = y + (SETTING_HEIGHT - sqSize) / 2;
            int previewColor = applyAlpha(colorSetting.getDisplayColor(), alpha);
            context.fill(sqX, sqY, sqX + sqSize, sqY + sqSize, previewColor);
            InventoryHudRenderer.drawButtonBorder(context, sqX - 1, sqY - 1, sqX + sqSize + 1, sqY + sqSize + 1, alpha);

        } else if (setting instanceof ModuleEntry.ButtonSetting) {
            String label = "  [" + setting.getLabel() + "]";
            boolean hover = mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= y && mouseY <= y + SETTING_HEIGHT;
            int color = hover ? applyAlpha(resolveTextColor(0f), alpha) : applyAlpha(0xFFAAAAAA, alpha);
            context.drawTextWithShadow(textRenderer, label, px + 14, y + (SETTING_HEIGHT - textRenderer.fontHeight) / 2, color);

        } else if (setting instanceof ModuleEntry.CycleSetting cycleSetting) {
            // Show label on left, current value on right — click to cycle
            String label = "  " + setting.getLabel();
            String value = cycleSetting.getSelected();
            if (value == null || value.isEmpty()) value = "None";

            // Truncate value if too long
            int labelWidth = textRenderer.getWidth(label);
            int maxValueW = PANEL_WIDTH - labelWidth - 28;
            if (maxValueW < 20) maxValueW = 20;
            while (textRenderer.getWidth(value) > maxValueW && value.length() > 3) {
                value = value.substring(0, value.length() - 1);
            }
            if (textRenderer.getWidth(value) > maxValueW) value = "..";

            boolean hover = mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= y && mouseY <= y + SETTING_HEIGHT;
            int labelColor = applyAlpha(0xFFAAAAAA, alpha);
            int valueColor = hover ? applyAlpha(resolveTextColor(0f), alpha) : applyAlpha(0xFFCCCCCC, alpha);

            int textY = y + (SETTING_HEIGHT - textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(textRenderer, label, px + 14, textY, labelColor);
            int valueX = px + PANEL_WIDTH - textRenderer.getWidth(value) - 8;
            context.drawTextWithShadow(textRenderer, value, valueX, textY, valueColor);

        } else if (setting instanceof ModuleEntry.TextSetting textSetting) {
            // Show label on left, editable value on right — click to edit
            String label = "  " + setting.getLabel() + ": ";
            boolean editing = textSetting == editingText;
            String displayValue = editing ? textEditBuffer : textSetting.getValue();
            if (displayValue == null) displayValue = "";

            // Truncate display value from the left if too long
            int labelWidth = textRenderer.getWidth(label);
            int maxValW = PANEL_WIDTH - labelWidth - 22;
            if (maxValW < 20) maxValW = 20;
            String suffix = editing ? "_" : "";
            while (textRenderer.getWidth(displayValue + suffix) > maxValW && displayValue.length() > 0) {
                displayValue = displayValue.substring(1);
            }
            displayValue = displayValue + suffix;

            boolean hover = mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= y && mouseY <= y + SETTING_HEIGHT;
            int labelColor = applyAlpha(0xFFAAAAAA, alpha);
            int valueColor = editing
                    ? applyAlpha(0xFFFFFFFF, alpha)
                    : (hover ? applyAlpha(resolveTextColor(0f), alpha) : applyAlpha(0xFFCCCCCC, alpha));

            int textY = y + (SETTING_HEIGHT - textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(textRenderer, label, px + 14, textY, labelColor);
            int valueX = px + 14 + labelWidth;
            context.drawTextWithShadow(textRenderer, displayValue, valueX, textY, valueColor);

            if (editing) {
                // Chroma underline to show active edit
                int lineY = y + SETTING_HEIGHT - 2;
                context.fill(valueX, lineY, px + PANEL_WIDTH - 8, lineY + 1, applyAlpha(resolveTextColor(0f), alpha));
            }
        }
    }

    // --- Input Handling ---

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();

        // Finish any pending text edit on left click
        if (button == 0 && editingText != null) {
            finishTextEdit();
        }

        // Search bar click
        int searchX = (width - SEARCH_BAR_WIDTH) / 2;
        int searchY = 4;
        if (mx >= searchX && mx <= searchX + SEARCH_BAR_WIDTH && my >= searchY && my <= searchY + SEARCH_BAR_HEIGHT) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // Check each panel
        for (var entry : modules.entrySet()) {
            ModuleCategory category = entry.getKey();
            List<ModuleEntry> entries = entry.getValue();
            ClickGuiConfig.PanelState state = ClickGuiConfig.getState(category);
            int px = state.x;
            int py = state.y;

            // Check bounds
            if (mx < px || mx > px + PANEL_WIDTH) continue;

            // Header click — left: start drag, right: toggle collapse
            if (my >= py && my <= py + HEADER_HEIGHT) {
                if (button == 1) {
                    state.collapsed = !state.collapsed;
                    return true;
                }
                draggingPanel = category;
                dragOffsetX = (int) (mx - px);
                dragOffsetY = (int) (my - py);
                return true;
            }

            if (state.collapsed) continue;

            // Module clicks
            List<ModuleEntry> filtered = filterModules(entries);
            int contentHeight = calculateContentHeight(filtered);
            int totalHeight = HEADER_HEIGHT + contentHeight;
            if (my < py + HEADER_HEIGHT || my > py + totalHeight) continue;

            int moduleY = py + HEADER_HEIGHT - (int) state.scrollOffset;
            for (ModuleEntry mod : filtered) {
                if (my >= moduleY && my < moduleY + MODULE_HEIGHT) {
                    if (button == 0) {
                        mod.toggle();
                        FloydAddonsConfig.save();
                    } else if (button == 1 && mod.hasSettings()) {
                        expandedModule = mod.getName().equals(expandedModule) ? null : mod.getName();
                    }
                    return true;
                }
                moduleY += MODULE_HEIGHT;

                // Sub-settings clicks
                if (mod.getName().equals(expandedModule) && mod.hasSettings()) {
                    for (ModuleEntry.SubSetting setting : mod.getSettings()) {
                        if (my >= moduleY && my < moduleY + SETTING_HEIGHT) {
                            if (button == 0) {
                                handleSettingClick(setting, mx, px, moduleY);
                            }
                            return true;
                        }
                        moduleY += SETTING_HEIGHT;
                    }
                }
            }
        }

        return super.mouseClicked(click, ignoresInput);
    }

    private void handleSettingClick(ModuleEntry.SubSetting setting, double mx, int px, int y) {
        if (setting instanceof ModuleEntry.BooleanSetting boolSetting) {
            boolSetting.toggle();
            FloydAddonsConfig.save();
        } else if (setting instanceof ModuleEntry.SliderSetting slider) {
            int barX = px + 14;
            int barW = PANEL_WIDTH - 28;
            float t = (float) Math.max(0, Math.min(1, (mx - barX) / barW));
            slider.setNormalized(t);
            draggingSlider = slider;
            draggingSliderX = barX;
            draggingSliderWidth = barW;
            FloydAddonsConfig.save();
        } else if (setting instanceof ModuleEntry.ColorSetting colorSetting) {
            MinecraftClient.getInstance().setScreen(new ColorPickerScreen(this, setting.getLabel(),
                    colorSetting.getColor(), colorSetting::setColor,
                    colorSetting::isChroma, colorSetting::setChroma));
        } else if (setting instanceof ModuleEntry.ButtonSetting btnSetting) {
            btnSetting.click();
        } else if (setting instanceof ModuleEntry.CycleSetting cycleSetting) {
            cycleSetting.cycleForward();
            FloydAddonsConfig.save();
        } else if (setting instanceof ModuleEntry.TextSetting textSetting) {
            editingText = textSetting;
            String val = textSetting.getValue();
            textEditBuffer = val != null ? val : "";
            searchFocused = false;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mx = click.x();
        double my = click.y();

        // Panel dragging
        if (draggingPanel != null && click.button() == 0) {
            ClickGuiConfig.PanelState state = ClickGuiConfig.getState(draggingPanel);
            state.x = Math.max(0, Math.min(width - PANEL_WIDTH, (int) (mx - dragOffsetX)));
            state.y = Math.max(0, Math.min(height - HEADER_HEIGHT, (int) (my - dragOffsetY)));
            return true;
        }

        // Slider dragging
        if (draggingSlider != null) {
            float t = (float) Math.max(0, Math.min(1, (mx - draggingSliderX) / draggingSliderWidth));
            draggingSlider.setNormalized(t);
            FloydAddonsConfig.save();
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            if (draggingPanel != null) {
                draggingPanel = null;
                FloydAddonsConfig.save();
                return true;
            }
            if (draggingSlider != null) {
                draggingSlider = null;
                return true;
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (var entry : modules.entrySet()) {
            ClickGuiConfig.PanelState state = ClickGuiConfig.getState(entry.getKey());
            int px = state.x;
            int py = state.y;
            List<ModuleEntry> filtered = filterModules(entry.getValue());
            int contentHeight = calculateContentHeight(filtered);
            int totalHeight = HEADER_HEIGHT + contentHeight;

            if (mouseX >= px && mouseX <= px + PANEL_WIDTH && mouseY >= py && mouseY <= py + totalHeight) {
                state.scrollOffset = Math.max(0, Math.min(Math.max(0, contentHeight - 200),
                        state.scrollOffset - (float) verticalAmount * 16));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // Text setting editing takes priority
        if (editingText != null) {
            if (input.key() == 259 && !textEditBuffer.isEmpty()) { // Backspace
                textEditBuffer = textEditBuffer.substring(0, textEditBuffer.length() - 1);
                return true;
            }
            if (input.isEnter()) {
                finishTextEdit();
                return true;
            }
            if (input.isEscape()) {
                editingText = null;
                textEditBuffer = "";
                return true;
            }
            return true; // consume all keys while editing
        }
        if (searchFocused) {
            if (input.key() == 259 && !searchQuery.isEmpty()) { // Backspace
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
            if (input.isEscape()) {
                searchFocused = false;
                searchQuery = "";
                return true;
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (editingText != null && input.codepoint() >= 32) {
            textEditBuffer += input.asString();
            return true;
        }
        if (searchFocused && input.codepoint() >= 32) {
            searchQuery += input.asString();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (closing) return;
        finishTextEdit();
        FloydAddonsConfig.save();
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    // --- Utilities ---

    private List<ModuleEntry> filterModules(List<ModuleEntry> entries) {
        if (searchQuery.isEmpty()) return entries;
        String query = searchQuery.toLowerCase();
        List<ModuleEntry> filtered = new ArrayList<>();
        for (ModuleEntry e : entries) {
            if (e.getName().toLowerCase().contains(query) || e.getDescription().toLowerCase().contains(query)) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    private int calculateContentHeight(List<ModuleEntry> filtered) {
        int h = 0;
        for (ModuleEntry entry : filtered) {
            h += MODULE_HEIGHT;
            if (entry.getName().equals(expandedModule) && entry.hasSettings()) {
                h += entry.getSettings().size() * SETTING_HEIGHT;
            }
        }
        return h;
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Returns the appropriate text color, respecting the user's chroma/color settings. */
    private int resolveTextColor(float offset) {
        if (!RenderConfig.isButtonTextChromaEnabled()) return RenderConfig.getButtonTextColor();
        double time = (System.currentTimeMillis() % 4000) / 4000.0;
        float hue = (float) ((time + offset) % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Opens a folder path in the OS file manager. */
    private static void openPath(java.nio.file.Path path) {
        String target = path.toAbsolutePath().toString();
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", target);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", target);
            } else {
                pb = new ProcessBuilder("xdg-open", target);
            }
            pb.start();
        } catch (Exception ignored) {}
    }
}
