package floydaddons.not.dogshit.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class SkinScreen extends Screen {
    private final Screen parent;
    private ButtonWidget selfToggle;
    private ButtonWidget othersToggle;
    private ButtonWidget doneButton;
    private ButtonWidget openFolderButton;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 210;
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

    public SkinScreen(Screen parent) {
        super(Text.literal("Skin"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        // Always reset position to center on open
        savedX = (width - BOX_WIDTH) / 2;
        savedY = (height - BOX_HEIGHT) / 2;
        panelX = savedX;
        panelY = savedY;

        selfToggle = ButtonWidget.builder(Text.literal(selfLabel()), b -> {
            SkinConfig.setSelfEnabled(!SkinConfig.selfEnabled());
            b.setMessage(Text.literal(selfLabel()));
            SkinConfig.save();
            SkinManager.clearCache();
        }).dimensions(panelX + (BOX_WIDTH - 220) / 2, panelY + 25, 220, 20).build();

        othersToggle = ButtonWidget.builder(Text.literal(othersLabel()), b -> {
            SkinConfig.setOthersEnabled(!SkinConfig.othersEnabled());
            b.setMessage(Text.literal(othersLabel()));
            SkinConfig.save();
            SkinManager.clearCache();
        }).dimensions(panelX + (BOX_WIDTH - 220) / 2, panelY + 55, 220, 20).build();

        openFolderButton = ButtonWidget.builder(Text.literal("Open skin folder"), b -> {
            var dir = SkinManager.ensureExternalDir();
            try {
                Util.getOperatingSystem().open(dir.toFile());
            } catch (Exception ignored) {}
        }).dimensions(panelX + (BOX_WIDTH - 220) / 2, panelY + 85, 220, 20).build();

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + 150, 100, 20)
                .build();

        addDrawableChild(selfToggle);
        addDrawableChild(othersToggle);
        addDrawableChild(openFolderButton);
        addDrawableChild(doneButton);
    }

    private String selfLabel() { return "Apply to me: " + (SkinConfig.selfEnabled() ? "ON" : "OFF"); }
    private String othersLabel() { return "Others: " + (SkinConfig.othersEnabled() ? "ON" : "OFF"); }

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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // keep game world visible; no blur
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        if (click.button() == 0 && click.x() >= panelX && click.x() <= panelX + BOX_WIDTH && click.y() >= panelY && click.y() <= panelY + DRAG_BAR_HEIGHT) {
            dragging = true;
            dragStartMouseX = click.x();
            dragStartMouseY = click.y();
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
            selfToggle.setX(panelX + (BOX_WIDTH - 220) / 2);
            selfToggle.setY(panelY + 25);
            othersToggle.setX(panelX + (BOX_WIDTH - 220) / 2);
            othersToggle.setY(panelY + 55);
            openFolderButton.setX(panelX + (BOX_WIDTH - 220) / 2);
            openFolderButton.setY(panelY + 85);
            doneButton.setX(panelX + (BOX_WIDTH - 100) / 2);
            doneButton.setY(panelY + 120);
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
        drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        styleButton(context, selfToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, othersToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, openFolderButton, guiAlpha, mouseX, mouseY);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Disclaimer (wrapped, gray), centered between last button and Done
        String disclaimer = "Disclaimer: You must name the skin file custom-sky.png in order for it to load.";
        var lines = textRenderer.wrapLines(Text.literal(disclaimer), BOX_WIDTH - 20);
        int blockHeight = lines.size() * (textRenderer.fontHeight + 2);
        int space = doneButton.getY() - (openFolderButton.getY() + openFolderButton.getHeight());
        int startY = openFolderButton.getY() + openFolderButton.getHeight() + (space - blockHeight) / 2;
        int dy = startY;
        for (var line : lines) {
            int lw = textRenderer.getWidth(line);
            int dx = panelX + (BOX_WIDTH - lw) / 2;
            context.drawTextWithShadow(textRenderer, line, dx, dy, applyAlpha(0xFFBBBBBB, guiAlpha));
            dy += textRenderer.fontHeight + 2;
        }

        matrices.popMatrix();
    }

    private void styleButton(DrawContext context, ButtonWidget button, float alpha, int mouseX, int mouseY) {
        int bx = button.getX();
        int by = button.getY();
        int bw = button.getWidth();
        int bh = button.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha);
        context.fill(bx, by, bx + bw, by + bh, fill);
        drawChromaBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = button.getMessage().getString();
        int textWidth = textRenderer.getWidth(label);
        int tx = bx + (bw - textWidth) / 2;
        int ty = by + (bh - textRenderer.fontHeight) / 2;
        int chroma = chromaColor((System.currentTimeMillis() % 4000) / 4000f);
        context.drawTextWithShadow(textRenderer, label, tx, ty, applyAlpha(chroma, alpha));
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int chromaColor(float offset) {
        double time = (System.currentTimeMillis() % 4000) / 4000.0;
        float hue = (float) ((time + offset) % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private void drawChromaBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        int width = right - left;
        int height = bottom - top;
        int perimeter = width * 2 + height * 2;
        if (perimeter <= 0) return;
        int pos = 0;
        for (int x = 0; x < width; x++, pos++) {
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            context.fill(left + x, top, left + x + 1, top + 1, c);
        }
        for (int y = 0; y < height; y++, pos++) {
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            context.fill(right - 1, top + y, right, top + y + 1, c);
        }
        for (int x = width - 1; x >= 0; x--, pos++) {
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            context.fill(left + x, bottom - 1, left + x + 1, bottom, c);
        }
        for (int y = height - 1; y >= 0; y--, pos++) {
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            context.fill(left, top + y, left + 1, top + y + 1, c);
        }
    }
}
