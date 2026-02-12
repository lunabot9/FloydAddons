package floydaddons.not.dogshit.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;

public class CapeScreen extends Screen {
    private final Screen parent;
    private List<String> images;
    private int index = 0;

    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private ButtonWidget openFolderButton;
    private ButtonWidget doneButton;

    private static final int BOX_W = 240;
    private static final int BOX_H = 140;
    private int panelX, panelY;
    private boolean closing = false;
    private long openStartMs;
    private long closeStartMs;
    private static final long FADE = 90;

    public CapeScreen(Screen parent) {
        super(Text.literal("Cape Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;
        panelX = (width - BOX_W) / 2;
        panelY = (height - BOX_H) / 2;
        images = CapeManager.listAvailableImages(true);
        index = Math.max(0, images.indexOf(RenderConfig.getSelectedCapeImage()));
        if (index < 0) index = 0;

        int rowY = panelY + 50;
        prevButton = ButtonWidget.builder(Text.literal("<"), b -> cycle(-1))
                .dimensions(panelX + 16, rowY, 30, 18).build();
        nextButton = ButtonWidget.builder(Text.literal(">"), b -> cycle(1))
                .dimensions(panelX + BOX_W - 46, rowY, 30, 18).build();
        openFolderButton = ButtonWidget.builder(Text.literal("Open Folder"), b -> openFolder())
                .dimensions(panelX + (BOX_W - 110) / 2, rowY + 26, 110, 18).build();
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_W - 80) / 2, panelY + BOX_H - 28, 80, 18).build();

        addDrawableChild(prevButton);
        addDrawableChild(nextButton);
        addDrawableChild(openFolderButton);
        addDrawableChild(doneButton);
    }

    private void cycle(int dir) {
        if (images.isEmpty()) return;
        index = (index + dir + images.size()) % images.size();
        RenderConfig.setSelectedCapeImage(images.get(index));
        RenderConfig.save();
    }

    private void openFolder() {
        var dir = CapeManager.ensureDir();
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("explorer", dir.toString());
            else if (os.contains("mac")) pb = new ProcessBuilder("open", dir.toString());
            else pb = new ProcessBuilder("xdg-open", dir.toString());
            pb.start();
        } catch (Exception ignored) {}
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float open = Math.min(1f, (Util.getMeasuringTimeMs() - openStartMs) / (float) FADE);
        float close = closing ? Math.min(1f, (Util.getMeasuringTimeMs() - closeStartMs) / (float) FADE) : 0f;
        float a = closing ? (1f - close) : open;
        if (closing && close >= 1f) {
            if (client != null) client.setScreen(parent);
            return;
        }

        context.fill(panelX, panelY, panelX + BOX_W, panelY + BOX_H, applyAlpha(0xCC000000, a));
        InventoryHudRenderer.drawChromaBorder(context, panelX - 1, panelY - 1, panelX + BOX_W + 1, panelY + BOX_H + 1, a);

        String title = "Cape";
        int tw = textRenderer.getWidth(title);
        context.drawTextWithShadow(textRenderer, title, panelX + (BOX_W - tw) / 2, panelY + 14, applyAlpha(0xFFFFFFFF, a));

        // Show current PNG name centered between the swap arrows
        String current = images.isEmpty() ? "None" : images.get(index);
        int nameWidth = textRenderer.getWidth(current);
        int rowY = panelY + 50;
        int textY = rowY + (18 - textRenderer.fontHeight) / 2;
        int centerX = panelX + BOX_W / 2;
        context.drawTextWithShadow(textRenderer, current, centerX - nameWidth / 2, textY, applyAlpha(0xFFFFFFFF, a));

        // Custom-styled buttons to match the other UIs
        styleButton(context, prevButton, a, mouseX, mouseY);
        styleButton(context, nextButton, a, mouseX, mouseY);
        styleButton(context, openFolderButton, a, mouseX, mouseY);
        styleButton(context, doneButton, a, mouseX, mouseY);
    }

    private int applyAlpha(int color, float alpha) {
        int c = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (c << 24) | (color & 0x00FFFFFF);
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

    private int resolveTextColor(float alpha, float chromaOffset) {
        int base = (RenderConfig.isButtonTextChromaEnabled())
                ? RenderConfig.chromaColor(chromaOffset)
                : RenderConfig.getButtonTextColor();
        return applyAlpha(base, alpha);
    }
}
