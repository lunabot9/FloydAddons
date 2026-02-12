package floydaddons.not.dogshit.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Mob ESP sub-config screen: tracers, hitboxes, star mobs toggles,
 * default ESP color, stalk tracer color, and edit filters button.
 */
public class MobEspScreen extends Screen {
    private final Screen parent;

    private ButtonWidget tracersToggle;
    private ButtonWidget hitboxesToggle;
    private ButtonWidget starMobsToggle;
    private ButtonWidget editFiltersButton;
    private ButtonWidget doneButton;

    // Color preview rects (drawn manually)
    private static final int COLOR_PREVIEW_SIZE = 16;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 320;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 26;

    private static final int FULL_W = 220;
    private static final int MAIN_W = 148;
    private static final int SECONDARY_W = 68;
    private static final int PAIR_GAP = 4;

    private int panelX, panelY;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    // Color row tracking for click handling
    private int espColorRowY;
    private int stalkColorRowY;
    private int colorPickBtnX;

    public MobEspScreen(Screen parent) {
        super(Text.literal("Mob ESP Config"));
        this.parent = parent;
    }

    private int leftEdge() { return panelX + (BOX_WIDTH - FULL_W) / 2; }
    private int rowY(int row) { return panelY + 26 + row * ROW_SPACING; }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;

        int le = leftEdge();

        // Row 0: "Toggles" header (drawn in render)

        // Row 1: Tracers toggle
        tracersToggle = ButtonWidget.builder(Text.literal(tracersLabel()), b -> {
            RenderConfig.setMobEspTracers(!RenderConfig.isMobEspTracers());
            b.setMessage(Text.literal(tracersLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(1), FULL_W, ROW_HEIGHT).build();

        // Row 2: Hitboxes toggle
        hitboxesToggle = ButtonWidget.builder(Text.literal(hitboxesLabel()), b -> {
            RenderConfig.setMobEspHitboxes(!RenderConfig.isMobEspHitboxes());
            b.setMessage(Text.literal(hitboxesLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(2), FULL_W, ROW_HEIGHT).build();

        // Row 3: Star Mobs toggle
        starMobsToggle = ButtonWidget.builder(Text.literal(starMobsLabel()), b -> {
            RenderConfig.setMobEspStarMobs(!RenderConfig.isMobEspStarMobs());
            b.setMessage(Text.literal(starMobsLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(3), FULL_W, ROW_HEIGHT).build();

        // Row 4: "Colors" header (drawn in render)
        // Rows 5-6: color pickers drawn manually

        // Row 7: Edit Filters
        editFiltersButton = ButtonWidget.builder(Text.literal("Edit Filters"), b -> {
            if (client != null) client.setScreen(new MobEspEditorScreen(this));
        }).dimensions(le, rowY(7), FULL_W, ROW_HEIGHT).build();

        // Done
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, ROW_HEIGHT)
                .build();

        addDrawableChild(tracersToggle);
        addDrawableChild(hitboxesToggle);
        addDrawableChild(starMobsToggle);
        addDrawableChild(editFiltersButton);
        addDrawableChild(doneButton);
    }

    private String tracersLabel() { return "Tracers: " + (RenderConfig.isMobEspTracers() ? "ON" : "OFF"); }
    private String hitboxesLabel() { return "Hitboxes: " + (RenderConfig.isMobEspHitboxes() ? "ON" : "OFF"); }
    private String starMobsLabel() { return "Star Mobs (\u272F): " + (RenderConfig.isMobEspStarMobs() ? "ON" : "OFF"); }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
        RenderConfig.save();
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

        styleButton(context, tracersToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, hitboxesToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, starMobsToggle, guiAlpha, mouseX, mouseY);

        // Section headers
        drawSectionHeader(context, "Toggles", rowY(0), guiAlpha);
        drawSectionHeader(context, "Colors", rowY(4), guiAlpha);

        // Row 5: Default ESP Color
        int le = leftEdge();
        espColorRowY = rowY(5);
        drawColorRow(context, "Default ESP Color", RenderConfig.getDefaultEspColor(),
                RenderConfig.isDefaultEspChromaEnabled(), le, espColorRowY, guiAlpha, mouseX, mouseY);

        // Row 6: Stalk Tracer Color
        stalkColorRowY = rowY(6);
        drawColorRow(context, "Stalk Tracer Color", RenderConfig.getStalkTracerColor(),
                RenderConfig.isStalkTracerChromaEnabled(), le, stalkColorRowY, guiAlpha, mouseX, mouseY);

        colorPickBtnX = le + FULL_W - SECONDARY_W;

        styleButton(context, editFiltersButton, guiAlpha, mouseX, mouseY);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Title
        String title = "Mob ESP Config";
        int titleWidth = textRenderer.getWidth(title);
        int tx = panelX + (BOX_WIDTH - titleWidth) / 2;
        int ty = panelY + 6;
        context.drawTextWithShadow(textRenderer, title, tx, ty, applyAlpha(chromaColor(0f), guiAlpha));
        matrices.popMatrix();
    }

    private void drawColorRow(DrawContext context, String label, int color, boolean chroma,
                               int x, int y, float alpha, int mouseX, int mouseY) {
        // Label text
        int textColor = applyAlpha(chromaColor((System.currentTimeMillis() % 4000) / 4000f), alpha);
        context.drawTextWithShadow(textRenderer, label, x, y + (ROW_HEIGHT - textRenderer.fontHeight) / 2, textColor);

        // Color preview square
        int previewX = x + FULL_W - SECONDARY_W - COLOR_PREVIEW_SIZE - PAIR_GAP;
        int previewY = y + (ROW_HEIGHT - COLOR_PREVIEW_SIZE) / 2;
        int previewColor;
        if (chroma) {
            float hue = (float) ((System.currentTimeMillis() % 4000) / 4000.0);
            previewColor = applyAlpha(java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000, alpha);
        } else {
            previewColor = applyAlpha(color, alpha);
        }
        context.fill(previewX, previewY, previewX + COLOR_PREVIEW_SIZE, previewY + COLOR_PREVIEW_SIZE, previewColor);
        InventoryHudRenderer.drawButtonBorder(context, previewX - 1, previewY - 1,
                previewX + COLOR_PREVIEW_SIZE + 1, previewY + COLOR_PREVIEW_SIZE + 1, alpha);

        // "Pick" button
        int btnX = x + FULL_W - SECONDARY_W;
        boolean hover = mouseX >= btnX && mouseX <= btnX + SECONDARY_W && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha);
        context.fill(btnX, y, btnX + SECONDARY_W, y + ROW_HEIGHT, fill);
        InventoryHudRenderer.drawButtonBorder(context, btnX - 1, y - 1, btnX + SECONDARY_W + 1, y + ROW_HEIGHT + 1, alpha);
        String pickLabel = "Pick";
        int tw = textRenderer.getWidth(pickLabel);
        context.drawTextWithShadow(textRenderer, pickLabel, btnX + (SECONDARY_W - tw) / 2,
                y + (ROW_HEIGHT - textRenderer.fontHeight) / 2, textColor);
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

            int btnX = leftEdge() + FULL_W - SECONDARY_W;

            // ESP color pick button
            if (mx >= btnX && mx <= btnX + SECONDARY_W && my >= espColorRowY && my <= espColorRowY + ROW_HEIGHT) {
                if (client != null) {
                    client.setScreen(new ColorPickerScreen(this, "Default ESP",
                            RenderConfig.getDefaultEspColor(),
                            RenderConfig::setDefaultEspColor,
                            RenderConfig::isDefaultEspChromaEnabled,
                            RenderConfig::setDefaultEspChromaEnabled));
                }
                return true;
            }

            // Stalk tracer color pick button
            if (mx >= btnX && mx <= btnX + SECONDARY_W && my >= stalkColorRowY && my <= stalkColorRowY + ROW_HEIGHT) {
                if (client != null) {
                    client.setScreen(new ColorPickerScreen(this, "Stalk Tracer",
                            RenderConfig.getStalkTracerColor(),
                            RenderConfig::setStalkTracerColor,
                            RenderConfig::isStalkTracerChromaEnabled,
                            RenderConfig::setStalkTracerChromaEnabled));
                }
                return true;
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
        int le = leftEdge();
        tracersToggle.setX(le);    tracersToggle.setY(rowY(1));
        hitboxesToggle.setX(le);   hitboxesToggle.setY(rowY(2));
        starMobsToggle.setX(le);   starMobsToggle.setY(rowY(3));
        editFiltersButton.setX(le); editFiltersButton.setY(rowY(7));
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2); doneButton.setY(panelY + BOX_HEIGHT - 30);
    }

    private void drawSectionHeader(DrawContext context, String text, int y, float alpha) {
        int le = leftEdge();
        int tw = textRenderer.getWidth(text);
        int tx = le + (FULL_W - tw) / 2;
        int ty = y + (ROW_HEIGHT - textRenderer.fontHeight) / 2;
        int lineY = ty + textRenderer.fontHeight / 2;
        int lineColor = applyAlpha(0xFF555555, alpha);
        context.fill(le, lineY, tx - 4, lineY + 1, lineColor);
        context.fill(tx + tw + 4, lineY, le + FULL_W, lineY + 1, lineColor);
        context.drawTextWithShadow(textRenderer, text, tx, ty, applyAlpha(chromaColor(0f), alpha));
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
        if (!(RenderConfig.isButtonTextChromaEnabled())) return RenderConfig.getButtonTextColor();
        return RenderConfig.chromaColor(offset);
    }
}
