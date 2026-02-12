package floydaddons.not.dogshit.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Hiders settings screen with toggle buttons for all working hider features.
 */
public class HidersScreen extends Screen {
    private final Screen parent;
    private final List<ButtonWidget> toggleButtons = new ArrayList<>();
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 340;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    private static final int FULL_W = 240;

    private int panelX, panelY;
    private boolean draggingPanel = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    public HidersScreen(Screen parent) {
        super(Text.literal("Hiders"));
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

        toggleButtons.clear();

        int le = leftEdge();
        int row = 0;

        toggleButtons.add(addToggle(le, row++, "No Hurt Camera",
                HidersConfig::isNoHurtCameraEnabled,
                () -> HidersConfig.setNoHurtCameraEnabled(!HidersConfig.isNoHurtCameraEnabled())));

        toggleButtons.add(addToggle(le, row++, "Remove Fire Overlay",
                HidersConfig::isRemoveFireOverlayEnabled,
                () -> HidersConfig.setRemoveFireOverlayEnabled(!HidersConfig.isRemoveFireOverlayEnabled())));

        toggleButtons.add(addToggle(le, row++, "Hide Entity Fire",
                HidersConfig::isHideEntityFireEnabled,
                () -> HidersConfig.setHideEntityFireEnabled(!HidersConfig.isHideEntityFireEnabled())));

        toggleButtons.add(addToggle(le, row++, "Disable Arrows",
                HidersConfig::isDisableAttachedArrowsEnabled,
                () -> HidersConfig.setDisableAttachedArrowsEnabled(!HidersConfig.isDisableAttachedArrowsEnabled())));

        toggleButtons.add(addToggle(le, row++, "Hide Ground Arrows",
                HidersConfig::isHideGroundedArrowsEnabled,
                () -> HidersConfig.setHideGroundedArrowsEnabled(!HidersConfig.isHideGroundedArrowsEnabled())));

        toggleButtons.add(addToggle(le, row++, "No Explosion Particles",
                HidersConfig::isRemoveExplosionParticlesEnabled,
                () -> HidersConfig.setRemoveExplosionParticlesEnabled(!HidersConfig.isRemoveExplosionParticlesEnabled())));

        toggleButtons.add(addToggle(le, row++, "Disable Hunger Bar",
                HidersConfig::isDisableHungerBarEnabled,
                () -> HidersConfig.setDisableHungerBarEnabled(!HidersConfig.isDisableHungerBarEnabled())));

        toggleButtons.add(addToggle(le, row++, "Hide Potion Effects",
                HidersConfig::isHidePotionEffectsEnabled,
                () -> HidersConfig.setHidePotionEffectsEnabled(!HidersConfig.isHidePotionEffectsEnabled())));

        toggleButtons.add(addToggle(le, row++, "3rd Person Crosshair",
                HidersConfig::isThirdPersonCrosshairEnabled,
                () -> HidersConfig.setThirdPersonCrosshairEnabled(!HidersConfig.isThirdPersonCrosshairEnabled())));

        toggleButtons.add(addToggle(le, row++, "Remove Falling Blocks",
                HidersConfig::isRemoveFallingBlocksEnabled,
                () -> HidersConfig.setRemoveFallingBlocksEnabled(!HidersConfig.isRemoveFallingBlocksEnabled())));

        toggleButtons.add(addToggle(le, row++, "Remove Tab Ping",
                HidersConfig::isRemoveTabPingEnabled,
                () -> HidersConfig.setRemoveTabPingEnabled(!HidersConfig.isRemoveTabPingEnabled())));

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, ROW_HEIGHT)
                .build();
        addDrawableChild(doneButton);
    }

    private ButtonWidget addToggle(int x, int row, String name,
                                    java.util.function.BooleanSupplier getter, Runnable toggle) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(name + ": " + (getter.getAsBoolean() ? "ON" : "OFF")), b -> {
            toggle.run();
            b.setMessage(Text.literal(name + ": " + (getter.getAsBoolean() ? "ON" : "OFF")));
            FloydAddonsConfig.save();
        }).dimensions(x, rowY(row), FULL_W, ROW_HEIGHT).build();
        addDrawableChild(btn);
        return btn;
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
        FloydAddonsConfig.save();
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

        for (ButtonWidget btn : toggleButtons) {
            styleButton(context, btn, guiAlpha, mouseX, mouseY);
        }
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        String title = "Hiders";
        int titleWidth = textRenderer.getWidth(title);
        context.drawTextWithShadow(textRenderer, title, panelX + (BOX_WIDTH - titleWidth) / 2, panelY + 6, resolveTextColor(guiAlpha, 0f));

        matrices.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
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
            panelX = clamp(dragStartPanelX + (int) (click.x() - dragStartMouseX), 0, width - BOX_WIDTH);
            panelY = clamp(dragStartPanelY + (int) (click.y() - dragStartMouseY), 0, height - BOX_HEIGHT);
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
        for (int i = 0; i < toggleButtons.size(); i++) {
            toggleButtons.get(i).setX(le);
            toggleButtons.get(i).setY(rowY(i));
        }
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
        context.drawTextWithShadow(textRenderer, label, bx + (bw - tw) / 2, by + (bh - textRenderer.fontHeight) / 2, resolveTextColor(alpha, 0f));
    }

    private int resolveTextColor(float alpha, float offset) {
        int base = RenderConfig.isButtonTextChromaEnabled() ? RenderConfig.chromaColor(offset) : RenderConfig.getButtonTextColor();
        return applyAlpha(base, alpha);
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
