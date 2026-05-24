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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import java.util.*;

/**
 * In-game editor for per-player name mappings (IGN -> fake display name).
 * Shows active mappings with remove buttons and online player suggestions with add buttons.
 */
public class NameMappingsEditorScreen extends Screen {
    private final Screen parent;

    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 360;
    private static final int BOX_HEIGHT = 300;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final float SCALE_START = 0.85f;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_SPACING = 18;
    private static final int CONTENT_PADDING = 8;
    private static final int HEADER_HEIGHT = 14;
    private static final int BUTTON_SIZE = 14;

    private int panelX, panelY;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    private int scrollOffset = 0;

    // Inline add state
    private String addingPlayerName = null;
    private TextFieldWidget addTextField = null;
    private ButtonWidget addSaveButton = null;

    // Name reveal state
    private final Set<String> revealedNames = new HashSet<>();

    // Cached data
    private List<Map.Entry<String, String>> activeMappings = new ArrayList<>();
    private List<String> onlinePlayers = new ArrayList<>();

    public NameMappingsEditorScreen(Screen parent) {
        super(Text.literal("Name Mappings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 28, 100, 20)
                .build();
        addDrawableChild(doneButton);

        refreshData();
        clearAddState();
    }

    private void refreshData() {
        Map<String, String> mappings = NickHiderConfig.getNameMappings();
        activeMappings = new ArrayList<>(mappings.entrySet());
        activeMappings.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        onlinePlayers = suggestOnlinePlayers();
    }

    private void clearAddState() {
        addingPlayerName = null;
        if (addTextField != null) {
            remove(addTextField);
            addTextField = null;
        }
        if (addSaveButton != null) {
            remove(addSaveButton);
            addSaveButton = null;
        }
    }

    private List<String> suggestOnlinePlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return Collections.emptyList();
        Map<String, String> active = NickHiderConfig.getNameMappings();
        String selfName = mc.player != null ? mc.player.getGameProfile().name() : null;
        List<String> result = new ArrayList<>();
        for (var entry : mc.getNetworkHandler().getPlayerList()) {
            if (!isRealPlayerEntry(entry)) continue;
            String name = entry.getProfile().name();
            if (selfName != null && name.equalsIgnoreCase(selfName)) continue;
            if (!active.containsKey(name)) {
                result.add(name);
            }
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
        java.util.UUID id = entry.getProfile().id();
        return id != null && id.version() == 4;
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
        // Draw the 8x8 face region from the skin texture (UV 8,8 to 16,16), scaled to display size
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        context.drawTexture(pipeline, skinTexture, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
    }

    /** Total content height for scroll calculations. */
    private int computeContentHeight() {
        int h = 0;
        // "Active Mappings" header + entries (only if non-empty)
        if (!activeMappings.isEmpty()) {
            h += HEADER_HEIGHT + 4;
            h += activeMappings.size() * ROW_SPACING;
            h += 8; // gap between sections
        }
        // "Online Players" header
        h += HEADER_HEIGHT + 4;
        if (onlinePlayers.isEmpty()) {
            h += ROW_SPACING;
        } else {
            for (String name : onlinePlayers) {
                if (name.equals(addingPlayerName)) {
                    h += ROW_SPACING * 2; // extra row for text field
                } else {
                    h += ROW_SPACING;
                }
            }
        }
        return h;
    }

    private int contentTop() {
        return panelY + 22;
    }

    private int contentBottom() {
        return panelY + BOX_HEIGHT - 34;
    }

    private int contentAreaHeight() {
        return contentBottom() - contentTop();
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No vanilla blur
    }

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

        float scale = SCALE_START + guiAlpha * (1.0f - SCALE_START);

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) (panelX + BOX_WIDTH / 2), (float) (panelY + BOX_HEIGHT / 2));
        matrices.scale(scale, scale);
        matrices.translate((float) -(panelX + BOX_WIDTH / 2), (float) -(panelY + BOX_HEIGHT / 2));

        int left = panelX;
        int top = panelY;
        int right = left + BOX_WIDTH;
        int bottom = top + BOX_HEIGHT;

        // Panel background
        context.fill(left, top, right, bottom, applyAlpha(0xAA000000, guiAlpha));
        InventoryHudRenderer.drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        // Title
        String title = "Name Mappings";
        int titleWidth = textRenderer.getWidth(title);
        int tx = panelX + (BOX_WIDTH - titleWidth) / 2;
        int ty = panelY + 6;
        context.drawTextWithShadow(textRenderer, title, tx, ty, applyAlpha(chromaColor(0f), guiAlpha));

        // Done button
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Scissor for scrollable content
        int cTop = contentTop();
        int cBottom = contentBottom();
        int cLeft = panelX + CONTENT_PADDING;
        int cRight = panelX + BOX_WIDTH - CONTENT_PADDING;

        context.enableScissor(cLeft, cTop, cRight, cBottom);
        NickTextUtil.setSuppressNickReplacement(true);
        try {
            renderContent(context, cLeft, cTop, cRight, guiAlpha, mouseX, mouseY, delta);
        } finally {
            NickTextUtil.setSuppressNickReplacement(false);
        }
        context.disableScissor();

        matrices.popMatrix();
    }

    private void renderContent(DrawContext context, int cLeft, int cTop, int cRight, float guiAlpha, int mouseX, int mouseY, float delta) {
        int y = cTop - scrollOffset;
        int contentWidth = cRight - cLeft;

        // -- Active Mappings Header + entries (only if non-empty) --
        if (!activeMappings.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Active Mappings", cLeft, y + 2, applyAlpha(chromaColor(0.2f), guiAlpha));
            y += HEADER_HEIGHT + 4;

            for (var entry : activeMappings) {
                String ign = entry.getKey();
                String fakeName = entry.getValue();

                // Player head
                int headSize = 8;
                int headX = cLeft + 2;
                int headY = y + (ROW_HEIGHT - headSize) / 2;
                Identifier skin = getPlayerSkinTexture(ign);
                if (skin != null) {
                    drawPlayerHead(context, skin, headX, headY, headSize);
                }

                // "OriginalIGN -> FakeName" (hidden by default)
                boolean revealed = revealedNames.contains(ign);
                String displayIgn = revealed ? ign : "*****";
                String mappingText = displayIgn + " \u2192 " + fakeName;
                int textX = cLeft + 14;
                int textY = y + (ROW_HEIGHT - textRenderer.fontHeight) / 2;
                context.drawTextWithShadow(textRenderer, mappingText, textX, textY, applyAlpha(0xFFFFFFFF, guiAlpha));

                // [-] remove button
                int removeBtnX = cRight - BUTTON_SIZE - 4;
                int removeBtnY = y + (ROW_HEIGHT - BUTTON_SIZE) / 2;
                boolean hoverRemove = mouseX >= removeBtnX && mouseX <= removeBtnX + BUTTON_SIZE
                        && mouseY >= removeBtnY && mouseY <= removeBtnY + BUTTON_SIZE;
                context.fill(removeBtnX, removeBtnY, removeBtnX + BUTTON_SIZE, removeBtnY + BUTTON_SIZE,
                        applyAlpha(hoverRemove ? 0xFFAA3333 : 0xFF883333, guiAlpha));
                InventoryHudRenderer.drawButtonBorder(context, removeBtnX - 1, removeBtnY - 1,
                        removeBtnX + BUTTON_SIZE + 1, removeBtnY + BUTTON_SIZE + 1, guiAlpha);
                String minus = "-";
                int mw = textRenderer.getWidth(minus);
                context.drawTextWithShadow(textRenderer, minus,
                        removeBtnX + (BUTTON_SIZE - mw) / 2, removeBtnY + (BUTTON_SIZE - textRenderer.fontHeight) / 2,
                        applyAlpha(0xFFFFFFFF, guiAlpha));

                y += ROW_SPACING;
            }

            y += 8; // gap
        }

        // -- Online Players Header --
        context.drawTextWithShadow(textRenderer, "Online Players", cLeft, y + 2, applyAlpha(chromaColor(0.5f), guiAlpha));
        y += HEADER_HEIGHT + 4;

        if (onlinePlayers.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No unmapped players online", cLeft + 4, y + 2, applyAlpha(0xFF888888, guiAlpha));
            y += ROW_SPACING;
        } else {
            for (String name : onlinePlayers) {
                // Player head
                int headSize = 8;
                int headX = cLeft + 2;
                int headY = y + (ROW_HEIGHT - headSize) / 2;
                Identifier skin = getPlayerSkinTexture(name);
                if (skin != null) {
                    drawPlayerHead(context, skin, headX, headY, headSize);
                }

                // Player name
                int textX = cLeft + 14;
                int textY = y + (ROW_HEIGHT - textRenderer.fontHeight) / 2;
                context.drawTextWithShadow(textRenderer, name, textX, textY, applyAlpha(0xFFCCCCCC, guiAlpha));

                if (name.equals(addingPlayerName)) {
                    // Show inline text field on the next row
                    y += ROW_SPACING;

                    if (addTextField != null) {
                        int fieldX = cLeft + 14;
                        int fieldY = y;
                        int fieldW = contentWidth - 80;
                        addTextField.setX(fieldX);
                        addTextField.setY(fieldY);
                        addTextField.setWidth(fieldW);

                        // Render text field with background and border
                        context.fill(fieldX, fieldY, fieldX + fieldW, fieldY + ROW_HEIGHT,
                                applyAlpha(0xFF000000, guiAlpha));
                        InventoryHudRenderer.drawButtonBorder(context, fieldX - 1, fieldY - 1,
                                fieldX + fieldW + 1, fieldY + ROW_HEIGHT + 1, guiAlpha);
                        addTextField.render(context, mouseX, mouseY, delta);

                        // Save button
                        if (addSaveButton != null) {
                            int saveBtnX = fieldX + fieldW + 4;
                            int saveBtnY = fieldY;
                            addSaveButton.setX(saveBtnX);
                            addSaveButton.setY(saveBtnY);
                            styleSmallButton(context, addSaveButton, guiAlpha, mouseX, mouseY);
                        }
                    }
                } else {
                    // [+] add button
                    int addBtnX = cRight - BUTTON_SIZE - 4;
                    int addBtnY = y + (ROW_HEIGHT - BUTTON_SIZE) / 2;
                    boolean hoverAdd = mouseX >= addBtnX && mouseX <= addBtnX + BUTTON_SIZE
                            && mouseY >= addBtnY && mouseY <= addBtnY + BUTTON_SIZE;
                    context.fill(addBtnX, addBtnY, addBtnX + BUTTON_SIZE, addBtnY + BUTTON_SIZE,
                            applyAlpha(hoverAdd ? 0xFF33AA33 : 0xFF338833, guiAlpha));
                    InventoryHudRenderer.drawButtonBorder(context, addBtnX - 1, addBtnY - 1,
                            addBtnX + BUTTON_SIZE + 1, addBtnY + BUTTON_SIZE + 1, guiAlpha);
                    String plus = "+";
                    int pw = textRenderer.getWidth(plus);
                    context.drawTextWithShadow(textRenderer, plus,
                            addBtnX + (BUTTON_SIZE - pw) / 2, addBtnY + (BUTTON_SIZE - textRenderer.fontHeight) / 2,
                            applyAlpha(0xFFFFFFFF, guiAlpha));
                }

                y += ROW_SPACING;
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();

        // Drag bar
        if (click.button() == 0 && mx >= panelX && mx <= panelX + BOX_WIDTH
                && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
            dragging = true;
            dragStartMouseX = mx;
            dragStartMouseY = my;
            dragStartPanelX = panelX;
            dragStartPanelY = panelY;
            return true;
        }

        // Check clicks inside content area
        if (click.button() == 0 && mx >= panelX + CONTENT_PADDING && mx <= panelX + BOX_WIDTH - CONTENT_PADDING
                && my >= contentTop() && my <= contentBottom()) {
            if (handleContentClick((int) mx, (int) my)) {
                return true;
            }
        }

        return super.mouseClicked(click, ignoresInput);
    }

    private boolean handleContentClick(int mx, int my) {
        int cLeft = panelX + CONTENT_PADDING;
        int cRight = panelX + BOX_WIDTH - CONTENT_PADDING;
        int y = contentTop() - scrollOffset;

        // Active Mappings (only if non-empty)
        if (!activeMappings.isEmpty()) {
            // Skip header
            y += HEADER_HEIGHT + 4;

            for (var entry : activeMappings) {
                // [-] remove button hit test
                int removeBtnX = cRight - BUTTON_SIZE - 4;
                int removeBtnY = y + (ROW_HEIGHT - BUTTON_SIZE) / 2;
                if (mx >= removeBtnX && mx <= removeBtnX + BUTTON_SIZE
                        && my >= removeBtnY && my <= removeBtnY + BUTTON_SIZE) {
                    NickHiderConfig.removeNameMapping(entry.getKey());
                    FloydAddonsConfig.saveNameMappings();
                    revealedNames.remove(entry.getKey());
                    refreshData();
                    clearAddState();
                    clampScroll();
                    return true;
                }
                // Click on label area toggles name reveal
                int labelLeft = cLeft + 14;
                int labelRight = removeBtnX - 4;
                int labelTop = y + (ROW_HEIGHT - textRenderer.fontHeight) / 2;
                int labelBottom = labelTop + textRenderer.fontHeight;
                if (mx >= labelLeft && mx <= labelRight && my >= labelTop && my <= labelBottom) {
                    String key = entry.getKey();
                    if (!revealedNames.remove(key)) revealedNames.add(key);
                    return true;
                }
                y += ROW_SPACING;
            }

            y += 8; // gap
        }

        // Skip "Online Players" header
        y += HEADER_HEIGHT + 4;

        if (!onlinePlayers.isEmpty()) {
            for (String name : onlinePlayers) {
                if (name.equals(addingPlayerName)) {
                    // Text field row -- clicks handled by widget
                    y += ROW_SPACING * 2;
                } else {
                    // [+] add button hit test
                    int addBtnX = cRight - BUTTON_SIZE - 4;
                    int addBtnY = y + (ROW_HEIGHT - BUTTON_SIZE) / 2;
                    if (mx >= addBtnX && mx <= addBtnX + BUTTON_SIZE
                            && my >= addBtnY && my <= addBtnY + BUTTON_SIZE) {
                        startAdding(name);
                        return true;
                    }
                    y += ROW_SPACING;
                }
            }
        }

        return false;
    }

    private void startAdding(String playerName) {
        clearAddState();
        addingPlayerName = playerName;

        addTextField = new TextFieldWidget(textRenderer, 0, 0, 200, ROW_HEIGHT, Text.literal("Fake name"));
        addTextField.setEditableColor(0xFFFFFFFF);
        addTextField.setUneditableColor(0xFFFFFFFF);
        addTextField.setFocused(true);
        addTextField.setDrawsBackground(false);
        addSelectableChild(addTextField);

        addSaveButton = ButtonWidget.builder(Text.literal("Save"), b -> {
            if (addingPlayerName != null && addTextField != null && !addTextField.getText().isEmpty()) {
                NickHiderConfig.addNameMapping(addingPlayerName, addTextField.getText());
                FloydAddonsConfig.saveNameMappings();
                refreshData();
                clearAddState();
                clampScroll();
            }
        }).dimensions(0, 0, 50, ROW_HEIGHT).build();
        addDrawableChild(addSaveButton);
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
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelX && mouseX <= panelX + BOX_WIDTH
                && mouseY >= contentTop() && mouseY <= contentBottom()) {
            scrollOffset -= (int) (verticalAmount * 10);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, computeContentHeight() - contentAreaHeight());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void repositionWidgets() {
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2);
        doneButton.setY(panelY + BOX_HEIGHT - 28);
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

    private void styleSmallButton(DrawContext context, ButtonWidget button, float alpha, int mouseX, int mouseY) {
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
        return RenderConfig.getButtonTextLiveColor(offset);
    }
}
