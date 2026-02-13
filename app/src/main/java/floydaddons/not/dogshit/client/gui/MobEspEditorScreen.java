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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-game editor for mob ESP filters. Shows active name and type filters
 * together, with per-entry color pickers, and nearby entity suggestions.
 */
public class MobEspEditorScreen extends Screen {
    private final Screen parent;

    private static final int BOX_WIDTH = 360;
    private static final int BOX_HEIGHT = 320;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ENTRY_HEIGHT = 20;
    private static final int CONTENT_PADDING = 4;
    private static final int BUTTON_SIZE_W = 18;
    private static final int BUTTON_SIZE_H = 16;
    private static final int COLOR_SQUARE_SIZE = 10;

    private int panelX, panelY;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    private ButtonWidget doneButton;

    private List<String> activeNames = new ArrayList<>();
    private List<String> activeTypes = new ArrayList<>();
    private List<String> nearbyNames = new ArrayList<>();
    private List<String> nearbyTypes = new ArrayList<>();

    private final List<HitEntry> hitEntries = new ArrayList<>();
    private final java.util.HashMap<String, LivingEntity> cachedNameEntities = new java.util.HashMap<>();

    // Inline color picker state
    private String expandedColorKey = null;
    private float pickerHue, pickerSat, pickerVal;
    private boolean pickerDraggingSV, pickerDraggingHue;
    private static final int PICKER_HEIGHT = 110;

    public MobEspEditorScreen(Screen parent) {
        super(Text.literal("Mob ESP Filters"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, 20)
                .build();
        addDrawableChild(doneButton);

        refreshLists();
    }

    private void refreshLists() {
        cachedNameEntities.clear();
        activeNames = new ArrayList<>(MobEspManager.getNameFilters());
        activeTypes = new ArrayList<>();
        for (Identifier id : MobEspManager.getTypeFilters()) {
            activeTypes.add(id.toString());
        }
        nearbyNames = suggestNearbyEntityNames();
        nearbyTypes = suggestNearbyEntityTypes();
        recalcMaxScroll();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void recalcMaxScroll() {
        int totalEntries = 0;
        // Active Names header + entries (only if non-empty)
        if (!activeNames.isEmpty()) {
            totalEntries += 1 + activeNames.size();
        }
        // Active Types header + entries (only if non-empty)
        if (!activeTypes.isEmpty()) {
            totalEntries += 1 + activeTypes.size();
        }
        // gap
        totalEntries += 1;
        // Nearby Entities header + entries
        totalEntries += 1 + nearbyNames.size();
        // Nearby Types header + entries
        totalEntries += 1 + nearbyTypes.size();

        int contentAreaHeight = BOX_HEIGHT - 26 - 40;
        int totalContentHeight = totalEntries * ENTRY_HEIGHT + CONTENT_PADDING * 2;
        // Add picker height if expanded
        if (expandedColorKey != null) {
            totalContentHeight += PICKER_HEIGHT;
        }
        maxScroll = Math.max(0, totalContentHeight - contentAreaHeight);
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
            if (!name.isEmpty() && !active.contains(name)) {
                found.add(name);
            }
            if (entity.hasCustomName() && entity.getCustomName() != null) {
                String custom = entity.getCustomName().getString().replaceAll("\u00a7.", "").trim();
                if (!custom.isEmpty() && !active.contains(custom)) {
                    found.add(custom);
                }
            }
            String cached = NpcTracker.getCachedName(entity);
            if (cached != null && !cached.isEmpty()) {
                boolean alreadyActive = active.stream().anyMatch(f -> f.equalsIgnoreCase(cached));
                if (!alreadyActive) found.add(cached);
            }
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
            if (!active.contains(typeId)) {
                found.add(typeId.toString());
            }
        }
        return new ArrayList<>(found);
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float openProgress = Math.min(1.0f, (Util.getMeasuringTimeMs() - openStartMs) / (float) FADE_DURATION_MS);
        float closeProgress = closing ? Math.min(1.0f, (Util.getMeasuringTimeMs() - closeStartMs) / (float) FADE_DURATION_MS) : 0f;
        if (closing && closeProgress >= 1.0f) {
            if (client != null) client.setScreen(parent);
            return;
        }
        float guiAlpha = closing ? (1.0f - closeProgress) : openProgress;
        if (guiAlpha <= 0f) return;

        float scale = 0.85f + guiAlpha * 0.15f;

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) (panelX + BOX_WIDTH / 2), (float) (panelY + BOX_HEIGHT / 2));
        matrices.scale(scale, scale);
        matrices.translate((float) -(panelX + BOX_WIDTH / 2), (float) -(panelY + BOX_HEIGHT / 2));

        int left = panelX;
        int top = panelY;
        int right = left + BOX_WIDTH;
        int bottom = top + BOX_HEIGHT;

        context.fill(left, top, right, bottom, applyAlpha(0xAA000000, guiAlpha));
        InventoryHudRenderer.drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        // Title
        String title = "Mob ESP Filters";
        int titleWidth = textRenderer.getWidth(title);
        int tx = panelX + (BOX_WIDTH - titleWidth) / 2;
        int ty = panelY + 6;
        context.drawTextWithShadow(textRenderer, title, tx, ty, applyAlpha(chromaColor(0f), guiAlpha));

        // Done button
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Scrollable content area
        int contentLeft = left + CONTENT_PADDING;
        int contentTop = top + 22;
        int contentRight = right - CONTENT_PADDING;
        int contentBottom = bottom - 38;
        int contentWidth = contentRight - contentLeft;

        hitEntries.clear();

        context.enableScissor(contentLeft, contentTop, contentRight, contentBottom);

        int y = contentTop + CONTENT_PADDING - scrollOffset;
        float chromaOffset = (System.currentTimeMillis() % 4000) / 4000f;

        // --- Active Names (only if non-empty) ---
        if (!activeNames.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Active Names", contentLeft + 2, y + 2,
                    applyAlpha(chromaColor(chromaOffset), guiAlpha));
            y += ENTRY_HEIGHT;

            for (String name : activeNames) {
                y = renderActiveEntry(context, name, "name", y, contentLeft, contentRight, contentTop, contentBottom,
                        contentWidth, guiAlpha, mouseX, mouseY, chromaOffset);
            }
        }

        // --- Active Types (only if non-empty) ---
        if (!activeTypes.isEmpty()) {
            y += 4;
            context.drawTextWithShadow(textRenderer, "Active Types", contentLeft + 2, y + 2,
                    applyAlpha(chromaColor(chromaOffset + 0.15f), guiAlpha));
            y += ENTRY_HEIGHT;

            for (String typeId : activeTypes) {
                y = renderActiveEntry(context, typeId, "type", y, contentLeft, contentRight, contentTop, contentBottom,
                        contentWidth, guiAlpha, mouseX, mouseY, chromaOffset);
            }
        }

        // --- Gap ---
        y += 8;

        // --- Nearby Entities ---
        context.drawTextWithShadow(textRenderer, "Nearby Entities", contentLeft + 2, y + 2,
                applyAlpha(chromaColor(chromaOffset + 0.3f), guiAlpha));
        y += ENTRY_HEIGHT;

        for (String name : nearbyNames) {
            y = renderSuggestionEntry(context, name, HitType.ADD_NAME, y, contentLeft, contentRight, contentTop,
                    contentBottom, contentWidth, guiAlpha, mouseX, mouseY, chromaOffset);
        }

        // --- Nearby Types ---
        y += 4;
        context.drawTextWithShadow(textRenderer, "Nearby Types", contentLeft + 2, y + 2,
                applyAlpha(chromaColor(chromaOffset + 0.45f), guiAlpha));
        y += ENTRY_HEIGHT;

        for (String typeId : nearbyTypes) {
            y = renderSuggestionEntry(context, typeId, HitType.ADD_TYPE, y, contentLeft, contentRight, contentTop,
                    contentBottom, contentWidth, guiAlpha, mouseX, mouseY, chromaOffset);
        }

        context.disableScissor();

        matrices.popMatrix();
    }

    private int renderActiveEntry(DrawContext context, String key, String kind, int y,
                                   int contentLeft, int contentRight, int contentTop, int contentBottom,
                                   int contentWidth, float guiAlpha, int mouseX, int mouseY, float chromaOffset) {
        // Entity icon (spawn egg for types, player head for names)
        int iconX = contentLeft + 2;
        int iconY = y + (ENTRY_HEIGHT - 16) / 2;
        renderEntityIcon(context, key, kind, iconX, iconY);

        // Color preview square (shifted right for icon)
        int colorSqX = contentLeft + 22;
        int colorSqY = y + (ENTRY_HEIGHT - COLOR_SQUARE_SIZE) / 2;
        int previewColor = resolveFilterColor(key);
        context.fill(colorSqX, colorSqY, colorSqX + COLOR_SQUARE_SIZE, colorSqY + COLOR_SQUARE_SIZE,
                applyAlpha(previewColor, guiAlpha));
        InventoryHudRenderer.drawButtonBorder(context, colorSqX - 1, colorSqY - 1,
                colorSqX + COLOR_SQUARE_SIZE + 1, colorSqY + COLOR_SQUARE_SIZE + 1, guiAlpha);

        if (y + ENTRY_HEIGHT > contentTop && y < contentBottom) {
            hitEntries.add(new HitEntry(colorSqX, colorSqY, COLOR_SQUARE_SIZE, COLOR_SQUARE_SIZE, key, HitType.COLOR));
        }

        // Text label
        String label = key;
        int labelStartX = colorSqX + COLOR_SQUARE_SIZE + 4;
        int maxLabelWidth = contentWidth - BUTTON_SIZE_W - COLOR_SQUARE_SIZE - 34;
        if (textRenderer.getWidth(label) > maxLabelWidth) {
            label = textRenderer.trimToWidth(label, maxLabelWidth - textRenderer.getWidth("...")) + "...";
        }
        context.drawTextWithShadow(textRenderer, label, labelStartX,
                y + (ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                applyAlpha(0xFFCCCCCC, guiAlpha));

        // [-] button
        int btnX = contentRight - BUTTON_SIZE_W - 2;
        int btnY = y;
        boolean hover = mouseX >= btnX && mouseX <= btnX + BUTTON_SIZE_W
                && mouseY >= btnY && mouseY <= btnY + BUTTON_SIZE_H
                && mouseY >= contentTop && mouseY <= contentBottom;
        context.fill(btnX, btnY, btnX + BUTTON_SIZE_W, btnY + BUTTON_SIZE_H,
                applyAlpha(hover ? 0xFF993333 : 0xFF773333, guiAlpha));
        InventoryHudRenderer.drawButtonBorder(context, btnX - 1, btnY - 1,
                btnX + BUTTON_SIZE_W + 1, btnY + BUTTON_SIZE_H + 1, guiAlpha);
        String btnLabel = "-";
        int btnTw = textRenderer.getWidth(btnLabel);
        context.drawTextWithShadow(textRenderer, btnLabel, btnX + (BUTTON_SIZE_W - btnTw) / 2,
                btnY + (BUTTON_SIZE_H - textRenderer.fontHeight) / 2,
                applyAlpha(chromaColor(chromaOffset), guiAlpha));

        HitType removeType = "name".equals(kind) ? HitType.REMOVE_NAME : HitType.REMOVE_TYPE;
        if (y + BUTTON_SIZE_H > contentTop && y < contentBottom) {
            hitEntries.add(new HitEntry(btnX, btnY, BUTTON_SIZE_W, BUTTON_SIZE_H, key, removeType));
        }

        int nextY = y + ENTRY_HEIGHT;

        // Inline color picker if this entry is expanded
        if (key.equals(expandedColorKey)) {
            nextY = renderInlineFilterColorPicker(context, key, nextY, contentLeft, contentRight,
                    contentTop, contentBottom, guiAlpha, mouseX, mouseY);
        }

        return nextY;
    }

    private int renderSuggestionEntry(DrawContext context, String key, HitType addType, int y,
                                       int contentLeft, int contentRight, int contentTop, int contentBottom,
                                       int contentWidth, float guiAlpha, int mouseX, int mouseY, float chromaOffset) {
        // Entity icon
        String kind = (addType == HitType.ADD_TYPE) ? "type" : "name";
        int sugIconX = contentLeft + 2;
        int sugIconY = y + (ENTRY_HEIGHT - 16) / 2;
        renderEntityIcon(context, key, kind, sugIconX, sugIconY);

        String label = key;
        int maxLabelWidth = contentWidth - BUTTON_SIZE_W - 26;
        if (textRenderer.getWidth(label) > maxLabelWidth) {
            label = textRenderer.trimToWidth(label, maxLabelWidth - textRenderer.getWidth("...")) + "...";
        }
        context.drawTextWithShadow(textRenderer, label, contentLeft + 22,
                y + (ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                applyAlpha(0xFFAAAAAA, guiAlpha));

        // [+] button
        int btnX = contentRight - BUTTON_SIZE_W - 2;
        int btnY = y;
        boolean hover = mouseX >= btnX && mouseX <= btnX + BUTTON_SIZE_W
                && mouseY >= btnY && mouseY <= btnY + BUTTON_SIZE_H
                && mouseY >= contentTop && mouseY <= contentBottom;
        context.fill(btnX, btnY, btnX + BUTTON_SIZE_W, btnY + BUTTON_SIZE_H,
                applyAlpha(hover ? 0xFF339933 : 0xFF337733, guiAlpha));
        InventoryHudRenderer.drawButtonBorder(context, btnX - 1, btnY - 1,
                btnX + BUTTON_SIZE_W + 1, btnY + BUTTON_SIZE_H + 1, guiAlpha);
        String btnLabel = "+";
        int btnTw = textRenderer.getWidth(btnLabel);
        context.drawTextWithShadow(textRenderer, btnLabel, btnX + (BUTTON_SIZE_W - btnTw) / 2,
                btnY + (BUTTON_SIZE_H - textRenderer.fontHeight) / 2,
                applyAlpha(chromaColor(chromaOffset), guiAlpha));

        if (y + BUTTON_SIZE_H > contentTop && y < contentBottom) {
            hitEntries.add(new HitEntry(btnX, btnY, BUTTON_SIZE_W, BUTTON_SIZE_H, key, addType));
        }

        return y + ENTRY_HEIGHT;
    }

    private void renderEntityIcon(DrawContext context, String key, String kind, int x, int y) {
        try {
            if ("type".equals(kind)) {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.of(key));
                SpawnEggItem eggItem = SpawnEggItem.forEntity(entityType);
                if (eggItem != null) {
                    context.drawItem(new ItemStack(eggItem), x, y);
                    return;
                }
            } else {
                // For name filters, find the actual entity in the world by name
                LivingEntity entity = cachedNameEntities.get(key);
                MinecraftClient mc = MinecraftClient.getInstance();
                if (entity == null && mc != null && mc.world != null) {
                    // Try NpcTracker first (armor-stand-paired NPCs)
                    Entity npcEntity = NpcTracker.findEntityByName(key);
                    if (npcEntity instanceof LivingEntity le) {
                        cachedNameEntities.put(key, le);
                        entity = le;
                    } else {
                        // Search world entities by display name or custom name
                        for (Entity e : mc.world.getEntities()) {
                            if (!(e instanceof LivingEntity le)) continue;
                            String displayName = e.getName().getString().replaceAll("\u00a7.", "").trim();
                            if (displayName.equalsIgnoreCase(key)) { cachedNameEntities.put(key, le); entity = le; break; }
                            if (e.hasCustomName() && e.getCustomName() != null) {
                                String custom = e.getCustomName().getString().replaceAll("\u00a7.", "").trim();
                                if (custom.equalsIgnoreCase(key)) { cachedNameEntities.put(key, le); entity = le; break; }
                            }
                        }
                    }
                }
                if (entity != null) {
                    int iconW = 14, iconH = ENTRY_HEIGHT;
                    context.enableScissor(x, y, x + iconW, y + iconH);
                    InventoryScreen.drawEntity(context, x, y, x + iconW, y + iconH,
                            6, 0.0625f, (float)(x + iconW / 2), (float)y, entity);
                    context.disableScissor();
                    return;
                }
            }
            // Fallback: player head
            context.drawItem(new ItemStack(Items.PLAYER_HEAD), x, y);
        } catch (Exception ignored) {}
    }

    private int resolveFilterColor(String filterKey) {
        int[] colorInfo = MobEspManager.getColorForFilter(filterKey);
        if (colorInfo != null) {
            if (colorInfo[1] == 1) {
                return RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
            }
            return colorInfo[0];
        }
        if (RenderConfig.isDefaultEspChromaEnabled()) {
            return RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
        }
        return RenderConfig.getDefaultEspColor();
    }

    private void toggleColorPicker(String filterKey) {
        if (filterKey.equals(expandedColorKey)) {
            expandedColorKey = null;
        } else {
            expandedColorKey = filterKey;
            int[] colorInfo = MobEspManager.getColorForFilter(filterKey);
            int initColor = colorInfo != null ? colorInfo[0] : RenderConfig.getDefaultEspColor();
            float[] hsv = Color.RGBtoHSB((initColor >> 16) & 0xFF, (initColor >> 8) & 0xFF, initColor & 0xFF, null);
            pickerHue = hsv[0];
            pickerSat = hsv[1];
            pickerVal = hsv[2];
        }
        recalcMaxScroll();
    }

    private int renderInlineFilterColorPicker(DrawContext context, String key, int y,
                                               int contentLeft, int contentRight, int contentTop, int contentBottom,
                                               float guiAlpha, int mouseX, int mouseY) {
        int[] colorInfo = MobEspManager.getColorForFilter(key);
        boolean isChroma = colorInfo != null && colorInfo[1] == 1;

        int svSize = 80, hueBarW = 8, hueBarH = 80;
        int svX = contentLeft + 6, svY = y + 4;
        int hbX = svX + svSize + 6, hbY = svY;

        if (isChroma) {
            int flash = RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f);
            context.fill(svX, svY, svX + svSize, svY + svSize, applyAlpha(0xFF222222, guiAlpha));
            context.fill(svX, svY, svX + svSize, svY + svSize, applyAlpha(flash, guiAlpha * 0.15f));
            context.fill(hbX, hbY, hbX + hueBarW, hbY + hueBarH, applyAlpha(0xFF222222, guiAlpha));
            context.fill(hbX, hbY, hbX + hueBarW, hbY + hueBarH, applyAlpha(flash, guiAlpha * 0.15f));
        } else {
            for (int x = 0; x < svSize; x++) {
                float s = x / (float) (svSize - 1);
                int topC = applyAlpha(Color.HSBtoRGB(pickerHue, s, 1.0f) | 0xFF000000, guiAlpha);
                int botC = applyAlpha(0xFF000000, guiAlpha);
                context.fillGradient(svX + x, svY, svX + x + 1, svY + svSize, topC, botC);
            }
            int cx = svX + (int) (pickerSat * (svSize - 1));
            int cy = svY + (int) ((1.0f - pickerVal) * (svSize - 1));
            context.fill(cx - 2, cy - 2, cx + 3, cy + 3, applyAlpha(0xFF000000, guiAlpha));
            context.fill(cx - 1, cy - 1, cx + 2, cy + 2, applyAlpha(0xFFFFFFFF, guiAlpha));
            for (int yy = 0; yy < hueBarH; yy++) {
                float h = yy / (float) (hueBarH - 1);
                context.fill(hbX, hbY + yy, hbX + hueBarW, hbY + yy + 1,
                        applyAlpha(Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000, guiAlpha));
            }
            int hyCursor = hbY + (int) (pickerHue * (hueBarH - 1));
            context.fill(hbX - 1, hyCursor - 1, hbX + hueBarW + 1, hyCursor + 2, applyAlpha(0xFFFFFFFF, guiAlpha));
        }

        InventoryHudRenderer.drawButtonBorder(context, svX - 1, svY - 1, svX + svSize + 1, svY + svSize + 1, guiAlpha);
        InventoryHudRenderer.drawButtonBorder(context, hbX - 1, hbY - 1, hbX + hueBarW + 1, hbY + hueBarH + 1, guiAlpha);

        // Info row: preview + hex + chroma toggle
        int infoY = svY + svSize + 4;
        int previewColor = isChroma ? RenderConfig.chromaColor((System.currentTimeMillis() % 4000) / 4000f)
                : (Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) | 0xFF000000);
        context.fill(svX, infoY, svX + 12, infoY + 12, applyAlpha(previewColor, guiAlpha));
        String hex = "#" + String.format("%06X", previewColor & 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, hex, svX + 16, infoY + 2, applyAlpha(0xFFCCCCCC, guiAlpha));
        String chromaLabel = isChroma ? "Chroma: ON" : "Chroma: OFF";
        int chromaX = contentRight - textRenderer.getWidth(chromaLabel) - 8;
        boolean hoverChroma = mouseX >= chromaX && mouseX <= contentRight - 4 && mouseY >= infoY && mouseY <= infoY + 12;
        context.drawTextWithShadow(textRenderer, chromaLabel, chromaX, infoY + 2,
                hoverChroma ? applyAlpha(chromaColor((System.currentTimeMillis() % 4000) / 4000f), guiAlpha)
                        : applyAlpha(0xFFAAAAAA, guiAlpha));

        // Register hit entries for SV, hue, and chroma
        if (y + PICKER_HEIGHT > contentTop && y < contentBottom) {
            hitEntries.add(new HitEntry(svX, svY, svSize, svSize, key, HitType.PICKER_SV));
            hitEntries.add(new HitEntry(hbX, hbY, hueBarW, hueBarH, key, HitType.PICKER_HUE));
            hitEntries.add(new HitEntry(chromaX, infoY, contentRight - 4 - chromaX, 12, key, HitType.PICKER_CHROMA));
        }

        return y + PICKER_HEIGHT;
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();

        if (click.button() == 0) {
            // Drag bar
            if (mx >= panelX && mx <= panelX + BOX_WIDTH
                    && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
                dragging = true;
                dragStartMouseX = mx;
                dragStartMouseY = my;
                dragStartPanelX = panelX;
                dragStartPanelY = panelY;
                return true;
            }

            int contentTop = panelY + 22;
            int contentBottom = panelY + BOX_HEIGHT - 38;

            for (HitEntry entry : hitEntries) {
                if (mx >= entry.x && mx <= entry.x + entry.w
                        && my >= entry.y && my <= entry.y + entry.h
                        && my >= contentTop && my <= contentBottom) {
                    switch (entry.type) {
                        case COLOR:
                            toggleColorPicker(entry.key);
                            return true;
                        case PICKER_SV:
                            if (!isChromaForKey(entry.key)) {
                                pickerDraggingSV = true;
                                updatePickerSV(mx - entry.x, my - entry.y, entry.w);
                            }
                            return true;
                        case PICKER_HUE:
                            if (!isChromaForKey(entry.key)) {
                                pickerDraggingHue = true;
                                updatePickerHue(my - entry.y, entry.h);
                            }
                            return true;
                        case PICKER_CHROMA: {
                            int[] ci = MobEspManager.getColorForFilter(entry.key);
                            boolean wasChroma = ci != null && ci[1] == 1;
                            int color = ci != null ? ci[0] : (Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) | 0xFF000000);
                            MobEspManager.setFilterColor(entry.key, color, !wasChroma);
                            FloydAddonsConfig.saveMobEsp();
                            return true;
                        }
                        case ADD_NAME:
                            MobEspManager.addNameFilter(entry.key);
                            FloydAddonsConfig.saveMobEsp();
                            refreshLists();
                            return true;
                        case REMOVE_NAME:
                            MobEspManager.removeNameFilter(entry.key);
                            FloydAddonsConfig.saveMobEsp();
                            if (entry.key.equals(expandedColorKey)) expandedColorKey = null;
                            refreshLists();
                            return true;
                        case ADD_TYPE:
                            MobEspManager.addTypeFilter(entry.key);
                            FloydAddonsConfig.saveMobEsp();
                            refreshLists();
                            return true;
                        case REMOVE_TYPE:
                            MobEspManager.removeTypeFilter(entry.key);
                            FloydAddonsConfig.saveMobEsp();
                            if (entry.key.equals(expandedColorKey)) expandedColorKey = null;
                            refreshLists();
                            return true;
                    }
                }
            }
        }

        return super.mouseClicked(click, ignoresInput);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging && click.button() == 0) {
            int newX = dragStartPanelX + (int) (click.x() - dragStartMouseX);
            int newY = dragStartPanelY + (int) (click.y() - dragStartMouseY);
            newX = Math.max(0, Math.min(newX, width - BOX_WIDTH));
            newY = Math.max(0, Math.min(newY, height - BOX_HEIGHT));
            panelX = newX;
            panelY = newY;
            repositionWidgets();
            return true;
        }
        if (pickerDraggingSV && expandedColorKey != null && click.button() == 0) {
            // Find the SV hit entry to get coordinates
            for (HitEntry entry : hitEntries) {
                if (entry.type == HitType.PICKER_SV && entry.key.equals(expandedColorKey)) {
                    updatePickerSV(click.x() - entry.x, click.y() - entry.y, entry.w);
                    return true;
                }
            }
            return true;
        }
        if (pickerDraggingHue && expandedColorKey != null && click.button() == 0) {
            for (HitEntry entry : hitEntries) {
                if (entry.type == HitType.PICKER_HUE && entry.key.equals(expandedColorKey)) {
                    updatePickerHue(click.y() - entry.y, entry.h);
                    return true;
                }
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            if (dragging) { dragging = false; return true; }
            if (pickerDraggingSV) { pickerDraggingSV = false; return true; }
            if (pickerDraggingHue) { pickerDraggingHue = false; return true; }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * ENTRY_HEIGHT * 3);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    private void repositionWidgets() {
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2);
        doneButton.setY(panelY + BOX_HEIGHT - 30);
    }

    private void styleButton(DrawContext context, ButtonWidget button, float alpha, int mouseX, int mouseY) {
        int bx = button.getX(), by = button.getY(), bw = button.getWidth(), bh = button.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        context.fill(bx, by, bx + bw, by + bh, applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha));
        InventoryHudRenderer.drawButtonBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = button.getMessage().getString();
        int tw = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer, label, bx + (bw - tw) / 2, by + (bh - textRenderer.fontHeight) / 2,
                applyAlpha(chromaColor((System.currentTimeMillis() % 4000) / 4000f), alpha));
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int chromaColor(float offset) {
        if (!RenderConfig.isButtonTextChromaEnabled()) return RenderConfig.getButtonTextColor();
        return RenderConfig.chromaColor(offset);
    }

    private boolean isChromaForKey(String key) {
        int[] ci = MobEspManager.getColorForFilter(key);
        return ci != null && ci[1] == 1;
    }

    private void updatePickerSV(double localX, double localY, int svSize) {
        pickerSat = (float) Math.max(0, Math.min(1, localX / (svSize - 1)));
        pickerVal = (float) Math.max(0, Math.min(1, 1.0 - localY / (svSize - 1)));
        if (expandedColorKey != null) {
            int color = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) | 0xFF000000;
            MobEspManager.setFilterColor(expandedColorKey, color, false);
            FloydAddonsConfig.saveMobEsp();
        }
    }

    private void updatePickerHue(double localY, int hueBarH) {
        pickerHue = (float) Math.max(0, Math.min(1, localY / (hueBarH - 1)));
        if (expandedColorKey != null) {
            int color = Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) | 0xFF000000;
            MobEspManager.setFilterColor(expandedColorKey, color, false);
            FloydAddonsConfig.saveMobEsp();
        }
    }

    private enum HitType {
        ADD_NAME, REMOVE_NAME, ADD_TYPE, REMOVE_TYPE, COLOR, PICKER_SV, PICKER_HUE, PICKER_CHROMA
    }

    private static class HitEntry {
        final int x, y, w, h;
        final String key;
        final HitType type;

        HitEntry(int x, int y, int w, int h, String key, HitType type) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.key = key;
            this.type = type;
        }
    }
}
