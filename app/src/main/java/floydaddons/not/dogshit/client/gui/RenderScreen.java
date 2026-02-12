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

/**
 * Render settings screen with compact paired-button layout.
 * HUD settings (inventory, scoreboard) have moved to HudScreen.
 */
public class RenderScreen extends Screen {
    private final Screen parent;

    private ButtonWidget serverIdToggle;
    private ButtonWidget xrayToggle;
    private SliderWidget opacitySlider;
    private ButtonWidget editBlocksButton;
    private ButtonWidget reloadBlocksButton;
    private ButtonWidget mobEspToggle;
    private ButtonWidget mobEspConfigButton;
    private ButtonWidget hidersButton;
    private ButtonWidget animationsButton;
    private ButtonWidget stalkButton;
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 330;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 26;

    // Paired button widths: main toggle + gap + secondary
    private static final int FULL_W = 220;
    private static final int MAIN_W = 148;
    private static final int SECONDARY_W = 68;
    private static final int PAIR_GAP = 4;
    private static final int HALF_W = 108;

    private int panelX;
    private int panelY;
    private boolean draggingPanel = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    public RenderScreen(Screen parent) {
        super(Text.literal("Render"));
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

        // Row 0: Server ID Hider
        serverIdToggle = ButtonWidget.builder(Text.literal(serverIdLabel()), b -> {
            RenderConfig.setServerIdHiderEnabled(!RenderConfig.isServerIdHiderEnabled());
            b.setMessage(Text.literal(serverIdLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(0), FULL_W, ROW_HEIGHT).build();

        // Row 1: "X-Ray" header (drawn in render)
        // Row 2: X-Ray toggle
        xrayToggle = ButtonWidget.builder(Text.literal(xrayLabel()), b -> {
            RenderConfig.toggleXray();
            b.setMessage(Text.literal(xrayLabel()));
        }).dimensions(le, rowY(2), FULL_W, ROW_HEIGHT).build();

        // Row 3: Opacity slider
        opacitySlider = new SliderWidget(
                le, rowY(3), FULL_W, ROW_HEIGHT,
                Text.literal(opacityLabel()),
                opacityToSlider(RenderConfig.getXrayOpacity())
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(opacityLabel()));
            }

            @Override
            protected void applyValue() {
                RenderConfig.setXrayOpacity(sliderToOpacity(this.value));
                RenderConfig.save();
            }

            @Override
            public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                // Custom rendering handled by parent screen
            }
        };

        // Row 4: Edit Blocks + Reload Blocks
        editBlocksButton = ButtonWidget.builder(Text.literal("Edit Blocks"), b -> {
            if (client != null) client.setScreen(new XrayEditorScreen(this));
        }).dimensions(le, rowY(4), HALF_W, ROW_HEIGHT).build();

        reloadBlocksButton = ButtonWidget.builder(Text.literal("Reload Blocks"), b -> {
            FloydAddonsConfig.loadXrayOpaque();
            if (RenderConfig.isXrayEnabled()) {
                RenderConfig.rebuildChunks();
            }
        }).dimensions(le + HALF_W + PAIR_GAP, rowY(4), HALF_W, ROW_HEIGHT).build();

        // Row 5: "Mob ESP" header (drawn in render)
        // Row 6: Mob ESP toggle + Config
        mobEspToggle = ButtonWidget.builder(Text.literal(mobEspLabel()), b -> {
            RenderConfig.toggleMobEsp();
            b.setMessage(Text.literal(mobEspLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(6), MAIN_W, ROW_HEIGHT).build();

        mobEspConfigButton = ButtonWidget.builder(Text.literal("Config"), b -> {
            if (client != null) client.setScreen(new MobEspScreen(this));
        }).dimensions(le + MAIN_W + PAIR_GAP, rowY(6), SECONDARY_W, ROW_HEIGHT).build();

        // Row 7: "Other" header (drawn in render)
        // Row 8: Hiders + Attack Animation
        hidersButton = ButtonWidget.builder(Text.literal("Hiders"), b -> {
            if (client != null) client.setScreen(new HidersScreen(this));
        }).dimensions(le, rowY(8), HALF_W, ROW_HEIGHT).build();

        animationsButton = ButtonWidget.builder(Text.literal("Attack Animation"), b -> {
            if (client != null) client.setScreen(new AnimationsScreen(this));
        }).dimensions(le + HALF_W + PAIR_GAP, rowY(8), HALF_W, ROW_HEIGHT).build();

        // Row 9: Stalk Player
        stalkButton = ButtonWidget.builder(Text.literal(stalkLabel()), b -> {
            if (StalkManager.isEnabled()) {
                StalkManager.disable();
            }
            b.setMessage(Text.literal(stalkLabel()));
        }).dimensions(le, rowY(9), FULL_W, ROW_HEIGHT).build();

        // Done
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, ROW_HEIGHT)
                .build();

        addDrawableChild(serverIdToggle);
        addDrawableChild(xrayToggle);
        addDrawableChild(opacitySlider);
        addDrawableChild(editBlocksButton);
        addDrawableChild(reloadBlocksButton);
        addDrawableChild(mobEspToggle);
        addDrawableChild(mobEspConfigButton);
        addDrawableChild(hidersButton);
        addDrawableChild(animationsButton);
        addDrawableChild(stalkButton);
        addDrawableChild(doneButton);
    }

    private String serverIdLabel() { return "Server ID Hider: " + (RenderConfig.isServerIdHiderEnabled() ? "ON" : "OFF"); }
    private String xrayLabel() { return "X-Ray: " + (RenderConfig.isXrayEnabled() ? "ON" : "OFF"); }
    private String opacityLabel() { return "X-Ray Opacity: " + Math.round(RenderConfig.getXrayOpacity() * 100) + "%"; }
    private String mobEspLabel() { return "Mob ESP: " + (RenderConfig.isMobEspEnabled() ? "ON" : "OFF"); }
    private String stalkLabel() {
        if (StalkManager.isEnabled()) return "Stalk: " + StalkManager.getTargetName() + " (click to disable)";
        return "Stalk: OFF (use /fa stalk <name>)";
    }

    private double opacityToSlider(float opacity) {
        return (opacity - 0.05f) / 0.95f;
    }

    private float sliderToOpacity(double slider) {
        return (float)(slider * 0.95 + 0.05);
    }

    @Override
    public void close() {
        requestClose();
    }

    private void requestClose() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
        RenderConfig.save();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // keep world visible; no blur
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

        styleButton(context, serverIdToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, xrayToggle, guiAlpha, mouseX, mouseY);
        styleSlider(context, opacitySlider, guiAlpha, mouseX, mouseY);
        styleButton(context, editBlocksButton, guiAlpha, mouseX, mouseY);
        styleButton(context, reloadBlocksButton, guiAlpha, mouseX, mouseY);
        styleButton(context, mobEspToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, mobEspConfigButton, guiAlpha, mouseX, mouseY);
        styleButton(context, hidersButton, guiAlpha, mouseX, mouseY);
        styleButton(context, animationsButton, guiAlpha, mouseX, mouseY);
        stalkButton.setMessage(Text.literal(stalkLabel()));
        styleButton(context, stalkButton, guiAlpha, mouseX, mouseY);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Section headers
        drawSectionHeader(context, "X-Ray", rowY(1), guiAlpha);
        drawSectionHeader(context, "Mob ESP", rowY(5), guiAlpha);
        drawSectionHeader(context, "Other", rowY(7), guiAlpha);

        // Title
        String title = "Render";
        int titleWidth = textRenderer.getWidth(title);
        int tx = panelX + (BOX_WIDTH - titleWidth) / 2;
        int ty = panelY + 6;
        context.drawTextWithShadow(textRenderer, title, tx, ty, resolveTextColor(guiAlpha, 0f));

        matrices.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0) {
            if (mx >= panelX && mx <= panelX + BOX_WIDTH && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
                draggingPanel = true;
                dragStartMouseX = mx;
                dragStartMouseY = my;
                dragStartPanelX = panelX;
                dragStartPanelY = panelY;
                return true;
            }
        }
        return super.mouseClicked(click, ignoresInput);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click.button() == 0 && draggingPanel) {
            int newX = dragStartPanelX + (int) (click.x() - dragStartMouseX);
            int newY = dragStartPanelY + (int) (click.y() - dragStartMouseY);
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
        if (click.button() == 0 && draggingPanel) {
            draggingPanel = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void repositionWidgets() {
        int le = leftEdge();
        serverIdToggle.setX(le);               serverIdToggle.setY(rowY(0));
        xrayToggle.setX(le);                   xrayToggle.setY(rowY(2));
        opacitySlider.setX(le);                opacitySlider.setY(rowY(3));
        editBlocksButton.setX(le);             editBlocksButton.setY(rowY(4));
        reloadBlocksButton.setX(le + HALF_W + PAIR_GAP); reloadBlocksButton.setY(rowY(4));
        mobEspToggle.setX(le);                 mobEspToggle.setY(rowY(6));
        mobEspConfigButton.setX(le + MAIN_W + PAIR_GAP); mobEspConfigButton.setY(rowY(6));
        hidersButton.setX(le);                 hidersButton.setY(rowY(8));
        animationsButton.setX(le + HALF_W + PAIR_GAP); animationsButton.setY(rowY(8));
        stalkButton.setX(le);                  stalkButton.setY(rowY(9));
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
        context.drawTextWithShadow(textRenderer, text, tx, ty, resolveTextColor(alpha, 0f));
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
        float offset = (System.currentTimeMillis() % 4000) / 4000f;
        context.drawTextWithShadow(textRenderer, label, tx, ty, resolveTextColor(alpha, offset));
    }

    private void styleSlider(DrawContext context, SliderWidget slider, float alpha, int mouseX, int mouseY) {
        int bx = slider.getX();
        int by = slider.getY();
        int bw = slider.getWidth();
        int bh = slider.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha);
        context.fill(bx, by, bx + bw, by + bh, fill);
        float pct = RenderConfig.getXrayOpacity();
        int fillW = (int)((bw - 4) * pct);
        context.fill(bx + 2, by + 2, bx + 2 + fillW, by + bh - 2, applyAlpha(0xFF888888, alpha));
        InventoryHudRenderer.drawButtonBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = slider.getMessage().getString();
        int textWidth = textRenderer.getWidth(label);
        int tx = bx + (bw - textWidth) / 2;
        int ty = by + (bh - textRenderer.fontHeight) / 2;
        float offset = (System.currentTimeMillis() % 4000) / 4000f;
        context.drawTextWithShadow(textRenderer, label, tx, ty, resolveTextColor(alpha, offset));
    }

    private int resolveTextColor(float alpha, float chromaOffset) {
        int base = (RenderConfig.isButtonTextChromaEnabled())
                ? chromaColor(chromaOffset)
                : RenderConfig.getButtonTextColor();
        return applyAlpha(base, alpha);
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int chromaColor(float offset) {
        if (!(RenderConfig.isButtonTextChromaEnabled())) return RenderConfig.getButtonTextColor();
        return RenderConfig.chromaColor(offset);
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

}
