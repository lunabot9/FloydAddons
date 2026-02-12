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

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class SkinScreen extends Screen {
    private final Screen parent;
    private ButtonWidget customToggle;
    private ButtonWidget customConfigButton;
    private ButtonWidget capeToggle;
    private ButtonWidget capeConfigButton;
    private ButtonWidget coneHatToggle;
    private ButtonWidget coneHatConfigButton;
    private SliderWidget playerSizeXSlider;
    private SliderWidget playerSizeYSlider;
    private SliderWidget playerSizeZSlider;
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 240;
    private static final int BOX_HEIGHT = 340;
    private static final long FADE_DURATION_MS = 90;
    private long openStartMs;
    private boolean closing = false;
    private long closeStartMs;
    private static final int DRAG_BAR_HEIGHT = 18;
    private int panelX, panelY;
    private static int savedX = -1, savedY = -1;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private static final int CONTROL_WIDTH = 220;
    private static final int MAIN_W = 148;
    private static final int SECONDARY_W = 68;
    private static final int PAIR_GAP = 4;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 26; // a bit more breathing room

    public SkinScreen(Screen parent) {
        super(Text.literal("Cosmetic"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        savedX = (width - BOX_WIDTH) / 2;
        savedY = (height - BOX_HEIGHT) / 2;
        panelX = savedX;
        panelY = savedY;

        int cx = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
        // vertically center the stack of eight rows inside the box
        int rowsHeight = ROW_HEIGHT * 8 + ROW_SPACING * 7;
        int cy = panelY + (BOX_HEIGHT - rowsHeight) / 2;

        // Row 0: "Cosmetics" section header (drawn in render)

        customToggle = ButtonWidget.builder(Text.literal(customLabel()), b -> {
            SkinConfig.setCustomEnabled(!SkinConfig.customEnabled());
            b.setMessage(Text.literal(customLabel()));
            SkinConfig.save();
        }).dimensions(cx, cy + ROW_SPACING * 1, MAIN_W, ROW_HEIGHT).build();

        customConfigButton = ButtonWidget.builder(Text.literal("Config"), b -> {
            if (client != null) client.setScreen(new SkinSettingsScreen(this));
        }).dimensions(cx + MAIN_W + PAIR_GAP, cy + ROW_SPACING * 1, SECONDARY_W, ROW_HEIGHT).build();

        capeToggle = ButtonWidget.builder(Text.literal(capeLabel()), b -> {
            RenderConfig.setCapeEnabled(!RenderConfig.isCapeEnabled());
            b.setMessage(Text.literal(capeLabel()));
            RenderConfig.save();
        }).dimensions(cx, cy + ROW_SPACING * 2, MAIN_W, ROW_HEIGHT).build();

        capeConfigButton = ButtonWidget.builder(Text.literal("Config"), b -> {
            if (client != null) client.setScreen(new CapeScreen(this));
        }).dimensions(cx + MAIN_W + PAIR_GAP, cy + ROW_SPACING * 2, SECONDARY_W, ROW_HEIGHT).build();

        coneHatToggle = ButtonWidget.builder(Text.literal(coneHatLabel()), b -> {
            RenderConfig.setFloydHatEnabled(!RenderConfig.isFloydHatEnabled());
            b.setMessage(Text.literal(coneHatLabel()));
            RenderConfig.save();
        }).dimensions(cx, cy + ROW_SPACING * 3, MAIN_W, ROW_HEIGHT).build();

        coneHatConfigButton = ButtonWidget.builder(Text.literal("Config"), b -> {
            if (client != null) client.setScreen(new ConeHatScreen(this));
        }).dimensions(cx + MAIN_W + PAIR_GAP, cy + ROW_SPACING * 3, SECONDARY_W, ROW_HEIGHT).build();

        // Row 4: "Player Size" section header (drawn in render)

        playerSizeXSlider = new SliderWidget(
                cx, cy + ROW_SPACING * 5, CONTROL_WIDTH, ROW_HEIGHT,
                Text.literal(playerSizeXLabel()),
                normalizePlayerScale(SkinConfig.getPlayerScaleX())
        ) {
            @Override protected void updateMessage() { setMessage(Text.literal(playerSizeXLabel())); }
            @Override protected void applyValue() {
                SkinConfig.setPlayerScaleX(denormalizePlayerScale(this.value));
                FloydAddonsConfig.save();
            }
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
        };

        playerSizeYSlider = new SliderWidget(
                cx, cy + ROW_SPACING * 6, CONTROL_WIDTH, ROW_HEIGHT,
                Text.literal(playerSizeYLabel()),
                normalizePlayerScale(SkinConfig.getPlayerScaleY())
        ) {
            @Override protected void updateMessage() { setMessage(Text.literal(playerSizeYLabel())); }
            @Override protected void applyValue() {
                SkinConfig.setPlayerScaleY(denormalizePlayerScale(this.value));
                FloydAddonsConfig.save();
            }
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
        };

        playerSizeZSlider = new SliderWidget(
                cx, cy + ROW_SPACING * 7, CONTROL_WIDTH, ROW_HEIGHT,
                Text.literal(playerSizeZLabel()),
                normalizePlayerScale(SkinConfig.getPlayerScaleZ())
        ) {
            @Override protected void updateMessage() { setMessage(Text.literal(playerSizeZLabel())); }
            @Override protected void applyValue() {
                SkinConfig.setPlayerScaleZ(denormalizePlayerScale(this.value));
                FloydAddonsConfig.save();
            }
            @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
        };

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 24, 100, 20)
                .build();

        addDrawableChild(customToggle);
        addDrawableChild(customConfigButton);
        addDrawableChild(capeToggle);
        addDrawableChild(capeConfigButton);
        addDrawableChild(coneHatToggle);
        addDrawableChild(coneHatConfigButton);
        addDrawableChild(playerSizeXSlider);
        addDrawableChild(playerSizeYSlider);
        addDrawableChild(playerSizeZSlider);
        addDrawableChild(doneButton);
    }

    private String customLabel() { return "Custom Skin: " + (SkinConfig.customEnabled() ? "ON" : "OFF"); }
    private String capeLabel() { return "Cape: " + (RenderConfig.isCapeEnabled() ? "ON" : "OFF"); }
    private String coneHatLabel() { return "Cone Hat: " + (RenderConfig.isFloydHatEnabled() ? "ON" : "OFF"); }
    private String playerSizeXLabel() { return "Size X: " + String.format("%.1f", SkinConfig.getPlayerScaleX()); }
    private String playerSizeYLabel() { return "Size Y: " + String.format("%.1f", SkinConfig.getPlayerScaleY()); }
    private String playerSizeZLabel() { return "Size Z: " + String.format("%.1f", SkinConfig.getPlayerScaleZ()); }
    private double normalizePlayerScale(float v) { return (v - (-1.0f)) / 6.0f; }
    private float denormalizePlayerScale(double n) { return (float)(n * 6.0 - 1.0); }

    @Override
    public void close() {
        requestClose();
    }

    private void requestClose() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
        SkinConfig.save();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();

        // Drag bar
        if (click.button() == 0 && mx >= panelX && mx <= panelX + BOX_WIDTH && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
            dragging = true;
            dragStartMouseX = mx;
            dragStartMouseY = my;
            dragStartPanelX = panelX;
            dragStartPanelY = panelY;
            return true;
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
            panelX = savedX = newX;
            panelY = savedY = newY;
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

    private void repositionWidgets() {
        int cx = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
        int rowsHeight = ROW_HEIGHT * 8 + ROW_SPACING * 7;
        int cy = panelY + (BOX_HEIGHT - rowsHeight) / 2;
        customToggle.setX(cx);                 customToggle.setY(cy + ROW_SPACING * 1);
        customConfigButton.setX(cx + MAIN_W + PAIR_GAP); customConfigButton.setY(cy + ROW_SPACING * 1);
        capeToggle.setX(cx);                   capeToggle.setY(cy + ROW_SPACING * 2);
        capeConfigButton.setX(cx + MAIN_W + PAIR_GAP); capeConfigButton.setY(cy + ROW_SPACING * 2);
        coneHatToggle.setX(cx);                coneHatToggle.setY(cy + ROW_SPACING * 3);
        coneHatConfigButton.setX(cx + MAIN_W + PAIR_GAP); coneHatConfigButton.setY(cy + ROW_SPACING * 3);
        playerSizeXSlider.setX(cx);            playerSizeXSlider.setY(cy + ROW_SPACING * 5);
        playerSizeYSlider.setX(cx);            playerSizeYSlider.setY(cy + ROW_SPACING * 6);
        playerSizeZSlider.setX(cx);            playerSizeZSlider.setY(cy + ROW_SPACING * 7);
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2);
        doneButton.setY(panelY + BOX_HEIGHT - 30);
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

        int baseColor = applyAlpha(0xAA000000, guiAlpha);
        context.fill(left, top, right, bottom, baseColor);
        InventoryHudRenderer.drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        // Buttons
        styleButton(context, customToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, customConfigButton, guiAlpha, mouseX, mouseY);
        styleButton(context, capeToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, capeConfigButton, guiAlpha, mouseX, mouseY);
        styleButton(context, coneHatToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, coneHatConfigButton, guiAlpha, mouseX, mouseY);
        styleSlider(context, playerSizeXSlider, guiAlpha, mouseX, mouseY, SkinConfig.getPlayerScaleX(), -1.0f, 5.0f);
        styleSlider(context, playerSizeYSlider, guiAlpha, mouseX, mouseY, SkinConfig.getPlayerScaleY(), -1.0f, 5.0f);
        styleSlider(context, playerSizeZSlider, guiAlpha, mouseX, mouseY, SkinConfig.getPlayerScaleZ(), -1.0f, 5.0f);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Section headers
        int rowsHeight = ROW_HEIGHT * 8 + ROW_SPACING * 7;
        int cy = panelY + (BOX_HEIGHT - rowsHeight) / 2;
        drawSectionHeader(context, "Cosmetics", cy, guiAlpha);
        drawSectionHeader(context, "Player Size", cy + ROW_SPACING * 4, guiAlpha);

        matrices.popMatrix();
    }

    private void drawSectionHeader(DrawContext context, String text, int y, float alpha) {
        int cx = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
        int tw = textRenderer.getWidth(text);
        int tx = cx + (CONTROL_WIDTH - tw) / 2;
        int ty = y + (ROW_HEIGHT - textRenderer.fontHeight) / 2;
        int lineY = ty + textRenderer.fontHeight / 2;
        int lineColor = applyAlpha(0xFF555555, alpha);
        context.fill(cx, lineY, tx - 4, lineY + 1, lineColor);
        context.fill(tx + tw + 4, lineY, cx + CONTROL_WIDTH, lineY + 1, lineColor);
        int textColor = resolveTextColor((System.currentTimeMillis() % 4000) / 4000f);
        context.drawTextWithShadow(textRenderer, text, tx, ty, applyAlpha(textColor, alpha));
    }

    private void styleButton(DrawContext context, ButtonWidget button, float alpha, int mouseX, int mouseY) {
        int bx = button.getX();
        int by = button.getY();
        int bw = button.getWidth();
        int bh = button.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha);
        context.fill(bx, by, bx + bw, by + bh, fill);
        InventoryHudRenderer.drawButtonBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = button.getMessage().getString();
        int textWidth = textRenderer.getWidth(label);
        int tx = bx + (bw - textWidth) / 2;
        int ty = by + (bh - textRenderer.fontHeight) / 2;
        int chroma = resolveTextColor((System.currentTimeMillis() % 4000) / 4000f);
        context.drawTextWithShadow(textRenderer, label, tx, ty, applyAlpha(chroma, alpha));
    }

    private void styleSlider(DrawContext context, SliderWidget slider, float alpha, int mouseX, int mouseY,
                              float value, float min, float max) {
        int bx = slider.getX(), by = slider.getY(), bw = slider.getWidth(), bh = slider.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        context.fill(bx, by, bx + bw, by + bh, applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha));
        float pct = (value - min) / (max - min);
        int fillW = (int) ((bw - 4) * pct);
        context.fill(bx + 2, by + 2, bx + 2 + fillW, by + bh - 2, applyAlpha(0xFF888888, alpha));
        InventoryHudRenderer.drawButtonBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = slider.getMessage().getString();
        int tw = textRenderer.getWidth(label);
        int chroma = resolveTextColor((System.currentTimeMillis() % 4000) / 4000f);
        context.drawTextWithShadow(textRenderer, label, bx + (bw - tw) / 2, by + (bh - textRenderer.fontHeight) / 2, applyAlpha(chroma, alpha));
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

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
                pb = new ProcessBuilder("sh", "-c", "xdg-open \"" + target + "\" &");
            }
            java.io.File devNull = new java.io.File(os.contains("win") ? "NUL" : "/dev/null");
            pb.redirectInput(ProcessBuilder.Redirect.from(devNull));
            pb.redirectOutput(ProcessBuilder.Redirect.to(devNull));
            pb.redirectError(ProcessBuilder.Redirect.to(devNull));
            pb.start();
        } catch (Exception ignored) {
        }
    }

    private int resolveTextColor(float offset) {
        if (!(RenderConfig.isButtonTextChromaEnabled())) return RenderConfig.getButtonTextColor();
        return RenderConfig.chromaColor(offset);
    }
}
