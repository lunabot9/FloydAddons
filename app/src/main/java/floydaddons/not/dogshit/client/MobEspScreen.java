package floydaddons.not.dogshit.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Mob ESP sub-config screen: tracers, hitboxes, star mobs toggles, Open File, Reload.
 */
public class MobEspScreen extends Screen {
    private final Screen parent;

    private ButtonWidget tracersToggle;
    private ButtonWidget hitboxesToggle;
    private ButtonWidget starMobsToggle;
    private ButtonWidget openFileButton;
    private ButtonWidget reloadButton;
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 186;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 26;

    private static final int FULL_W = 220;
    private static final int HALF_W = 108;
    private static final int PAIR_GAP = 4;

    private int panelX, panelY;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

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

        // Row 0: Tracers toggle
        tracersToggle = ButtonWidget.builder(Text.literal(tracersLabel()), b -> {
            RenderConfig.setMobEspTracers(!RenderConfig.isMobEspTracers());
            b.setMessage(Text.literal(tracersLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(0), FULL_W, ROW_HEIGHT).build();

        // Row 1: Hitboxes toggle
        hitboxesToggle = ButtonWidget.builder(Text.literal(hitboxesLabel()), b -> {
            RenderConfig.setMobEspHitboxes(!RenderConfig.isMobEspHitboxes());
            b.setMessage(Text.literal(hitboxesLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(1), FULL_W, ROW_HEIGHT).build();

        // Row 2: Star Mobs toggle
        starMobsToggle = ButtonWidget.builder(Text.literal(starMobsLabel()), b -> {
            RenderConfig.setMobEspStarMobs(!RenderConfig.isMobEspStarMobs());
            b.setMessage(Text.literal(starMobsLabel()));
            RenderConfig.save();
        }).dimensions(le, rowY(2), FULL_W, ROW_HEIGHT).build();

        // Row 3: Open File + Reload
        openFileButton = ButtonWidget.builder(Text.literal("Open File"), b -> {
            try {
                java.nio.file.Path path = FloydAddonsConfig.getMobEspPath();
                if (!java.nio.file.Files.exists(path)) {
                    FloydAddonsConfig.loadMobEsp();
                }
                openFileInEditor(path);
            } catch (Exception ignored) {}
        }).dimensions(le, rowY(3), HALF_W, ROW_HEIGHT).build();

        reloadButton = ButtonWidget.builder(Text.literal("Reload"), b -> {
            FloydAddonsConfig.loadMobEsp();
        }).dimensions(le + HALF_W + PAIR_GAP, rowY(3), HALF_W, ROW_HEIGHT).build();

        // Done
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, ROW_HEIGHT)
                .build();

        addDrawableChild(tracersToggle);
        addDrawableChild(hitboxesToggle);
        addDrawableChild(starMobsToggle);
        addDrawableChild(openFileButton);
        addDrawableChild(reloadButton);
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
        styleButton(context, openFileButton, guiAlpha, mouseX, mouseY);
        styleButton(context, reloadButton, guiAlpha, mouseX, mouseY);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Title
        String title = "Mob ESP Config";
        int titleWidth = textRenderer.getWidth(title);
        int tx = panelX + (BOX_WIDTH - titleWidth) / 2;
        int ty = panelY + 6;
        context.drawTextWithShadow(textRenderer, title, tx, ty, applyAlpha(chromaColor(0f), guiAlpha));

        matrices.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0 && mx >= panelX && mx <= panelX + BOX_WIDTH
                && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
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
        tracersToggle.setX(le);    tracersToggle.setY(rowY(0));
        hitboxesToggle.setX(le);   hitboxesToggle.setY(rowY(1));
        starMobsToggle.setX(le);   starMobsToggle.setY(rowY(2));
        openFileButton.setX(le);   openFileButton.setY(rowY(3));
        reloadButton.setX(le + HALF_W + PAIR_GAP); reloadButton.setY(rowY(3));
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2); doneButton.setY(panelY + BOX_HEIGHT - 30);
    }

    private void styleButton(DrawContext context, ButtonWidget button, float alpha, int mouseX, int mouseY) {
        int bx = button.getX(), by = button.getY(), bw = button.getWidth(), bh = button.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        context.fill(bx, by, bx + bw, by + bh, applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha));
        InventoryHudRenderer.drawChromaBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
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
        double time = (System.currentTimeMillis() % 4000) / 4000.0;
        float hue = (float) ((time + offset) % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static void openFileInEditor(java.nio.file.Path path) {
        String file = path.toAbsolutePath().toString();
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", file);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", file);
            } else {
                pb = new ProcessBuilder("sh", "-c", "xdg-open \"" + file + "\" &");
            }
            java.io.File devNull = new java.io.File(os.contains("win") ? "NUL" : "/dev/null");
            pb.redirectInput(ProcessBuilder.Redirect.from(devNull));
            pb.redirectOutput(ProcessBuilder.Redirect.to(devNull));
            pb.redirectError(ProcessBuilder.Redirect.to(devNull));
            pb.start();
        } catch (Exception ignored) {}
    }
}
