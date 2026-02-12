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
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.function.Consumer;

/**
 * GUI style settings. Color picking is opened on a separate screen to avoid overlap.
 */
public class GuiStyleScreen extends Screen {
    private static final int BOX_WIDTH = 260;
    private static final int BOX_HEIGHT = 140;
    private static final int DRAG_BAR_HEIGHT = 16;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_SPACING = 24;
    private static final int LABEL_W = 108;
    private static final int PREVIEW_W = 26;
    private static final int PICK_W = 36;
    private static final long FADE_DURATION_MS = 90;

    private final Screen parent;
    private ButtonWidget textPickButton;
    private ButtonWidget borderPickButton;
    private ButtonWidget guiPickButton;
    private ButtonWidget doneButton;

    private int panelX, panelY;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    private int liveTextColor;
    private int liveButtonBorderColor;
    private int liveGuiBorderColor;

    public GuiStyleScreen(Screen parent) {
        super(Text.literal("GUI Style"));
        this.parent = parent;
    }

    private int leftEdge() { return panelX + 14; }
    private int rowY(int row) { return panelY + 22 + row * ROW_SPACING; }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;
        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;

        int le = leftEdge() + LABEL_W + 6;

        int pickX = le + PREVIEW_W + 8;
        textPickButton = ButtonWidget.builder(Text.literal("Pick"), b -> openPicker(Target.TEXT)).dimensions(pickX, rowY(0), PICK_W, ROW_HEIGHT).build();
        borderPickButton = ButtonWidget.builder(Text.literal("Pick"), b -> openPicker(Target.BUTTON)).dimensions(pickX, rowY(1), PICK_W, ROW_HEIGHT).build();
        guiPickButton = ButtonWidget.builder(Text.literal("Pick"), b -> openPicker(Target.GUI)).dimensions(pickX, rowY(2), PICK_W, ROW_HEIGHT).build();

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> {
            applyChanges();
            close();
        }).dimensions(panelX + (BOX_WIDTH - 96) / 2, panelY + BOX_HEIGHT - 26, 96, ROW_HEIGHT).build();

        addDrawableChild(textPickButton);
        addDrawableChild(borderPickButton);
        addDrawableChild(guiPickButton);
        addDrawableChild(doneButton);
    }

    private void applyChanges() {
        RenderConfig.save();
    }

    @Override
    public void close() {
        if (closing) return;
        applyChanges();
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

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

        liveTextColor = RenderConfig.getButtonTextColor();
        liveButtonBorderColor = RenderConfig.getButtonBorderColor();
        liveGuiBorderColor = RenderConfig.getGuiBorderColor();

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

        drawRow(context, "Button Text", rowY(0), liveTextColor, RenderConfig.isButtonTextChromaEnabled(), guiAlpha);
        drawRow(context, "Button Border", rowY(1), liveButtonBorderColor, RenderConfig.isButtonBorderChromaEnabled(), guiAlpha);
        drawRow(context, "GUI Border", rowY(2), liveGuiBorderColor, RenderConfig.isGuiBorderChromaEnabled(), guiAlpha);

        // Pick buttons
        styleButton(context, textPickButton, guiAlpha, mouseX, mouseY);
        styleButton(context, borderPickButton, guiAlpha, mouseX, mouseY);
        styleButton(context, guiPickButton, guiAlpha, mouseX, mouseY);

        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Title
        String title = "GUI Style";
        int titleWidth = textRenderer.getWidth(title);
        int titleColor = RenderConfig.isButtonTextChromaEnabled()
                ? chromaColor(0f) : liveTextColor;
        context.drawTextWithShadow(textRenderer, title, panelX + (BOX_WIDTH - titleWidth) / 2, panelY + 6,
                applyAlpha(titleColor, guiAlpha));

        matrices.popMatrix();
    }

    private void drawRow(DrawContext context, String label, int y, int previewColor, boolean chroma, float alpha) {
        int labelX = leftEdge();
        int labelColor = RenderConfig.isButtonTextChromaEnabled() ? chromaColor(0f) : liveTextColor;
        context.drawTextWithShadow(textRenderer, label, labelX, y + (ROW_HEIGHT - textRenderer.fontHeight) / 2,
                applyAlpha(labelColor, alpha));

        int previewX = leftEdge() + LABEL_W + 6;
        int previewY = y;
        context.fill(previewX, previewY, previewX + PREVIEW_W, previewY + ROW_HEIGHT, applyAlpha(0xFF222222, alpha));

        // Show cycling chroma color if chroma is enabled, otherwise static color
        int displayColor;
        if (chroma) {
            float hue = (float) ((System.currentTimeMillis() % 4000) / 4000.0);
            displayColor = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000;
        } else {
            displayColor = previewColor;
        }
        context.fill(previewX + 1, previewY + 1, previewX + PREVIEW_W - 1, previewY + ROW_HEIGHT - 1,
                applyAlpha(displayColor, alpha));
        InventoryHudRenderer.drawButtonBorder(context, previewX, previewY, previewX + PREVIEW_W, previewY + ROW_HEIGHT, alpha);
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
        int tw = textRenderer.getWidth(label);
        int tx = bx + (bw - tw) / 2;
        int ty = by + (bh - textRenderer.fontHeight) / 2;
        int color = RenderConfig.isButtonTextChromaEnabled()
                ? applyAlpha(chromaColor((System.currentTimeMillis() % 4000) / 4000f), alpha)
                : applyAlpha(liveTextColor, alpha);
        context.drawTextWithShadow(textRenderer, label, tx, ty, color);
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0 && mouseX >= panelX && mouseX <= panelX + BOX_WIDTH && mouseY >= panelY && mouseY <= panelY + DRAG_BAR_HEIGHT) {
            dragging = true;
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            dragStartPanelX = panelX;
            dragStartPanelY = panelY;
            return true;
        }
        return super.mouseClicked(click, ignoresInput);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging && click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();
            int newX = dragStartPanelX + (int) (mouseX - dragStartMouseX);
            int newY = dragStartPanelY + (int) (mouseY - dragStartMouseY);
            newX = clamp(newX, 0, width - BOX_WIDTH);
            newY = clamp(newY, 0, height - BOX_HEIGHT);
            panelX = newX;
            panelY = newY;
            repositionWidgets();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging && click.button() == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void repositionWidgets() {
        int le = leftEdge() + LABEL_W + 6;
        int pickX = le + PREVIEW_W + 8;
        textPickButton.setX(pickX); textPickButton.setY(rowY(0));
        borderPickButton.setX(pickX); borderPickButton.setY(rowY(1));
        guiPickButton.setX(pickX); guiPickButton.setY(rowY(2));
        doneButton.setX(panelX + (BOX_WIDTH - 96) / 2); doneButton.setY(panelY + BOX_HEIGHT - 26);
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int chromaColor(float offset) {
        return RenderConfig.chromaColor(offset);
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private void openPicker(Target target) {
        int current = switch (target) {
            case TEXT -> RenderConfig.getButtonTextColor();
            case BUTTON -> RenderConfig.getButtonBorderColor();
            case GUI -> RenderConfig.getGuiBorderColor();
        };
        Consumer<Integer> apply = color -> {
            switch (target) {
                case TEXT -> RenderConfig.setButtonTextColor(color);
                case BUTTON -> RenderConfig.setButtonBorderColor(color);
                case GUI -> RenderConfig.setGuiBorderColor(color);
            }
        };
        java.util.function.BooleanSupplier chromaGetter = switch (target) {
            case TEXT -> RenderConfig::isButtonTextChromaEnabled;
            case BUTTON -> RenderConfig::isButtonBorderChromaEnabled;
            case GUI -> RenderConfig::isGuiBorderChromaEnabled;
        };
        Consumer<Boolean> chromaSetter = flag -> {
            switch (target) {
                case TEXT -> RenderConfig.setButtonTextChromaEnabled(flag);
                case BUTTON -> RenderConfig.setButtonBorderChromaEnabled(flag);
                case GUI -> RenderConfig.setGuiBorderChromaEnabled(flag);
            }
        };
        if (client != null) {
            client.setScreen(new ColorPickerScreen(this, target.display, current, apply, chromaGetter, chromaSetter));
        }
    }

    private enum Target {
        TEXT("Button Text"),
        BUTTON("Button Border"),
        GUI("GUI Border");

        final String display;
        Target(String d) { this.display = d; }
    }
}
