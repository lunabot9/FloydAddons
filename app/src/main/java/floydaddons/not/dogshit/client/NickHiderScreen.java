package floydaddons.not.dogshit.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Simple configuration screen for the Nick Hider feature.
 * Shows a single text box for the replacement nickname and a toggle button.
 */
public class NickHiderScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nickField;
    private ButtonWidget toggleButton;
    private TextFieldWidget othersNickField;
    private ButtonWidget toggleOthersButton;
    private ButtonWidget editNamesButton;
    private ButtonWidget reloadNamesButton;
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 260;
    private static final int BOX_HEIGHT = 208;
    private static final long FADE_DURATION_MS = 90;
    private static final float SCALE_START = 0.85f;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static int savedX = -1;
    private static int savedY = -1;
    private int panelX;
    private int panelY;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    public NickHiderScreen(Screen parent) {
        super(Text.literal("Nick Hider"));
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

        int cx = panelX + (BOX_WIDTH - 220) / 2;

        nickField = new TextFieldWidget(textRenderer, cx, panelY + 16, 220, 20, Text.literal("Nickname"));
        nickField.setText(NickHiderConfig.getNickname());
        nickField.setEditableColor(0xFFFFFFFF);
        nickField.setUneditableColor(0xFFFFFFFF);
        nickField.setFocused(true);
        nickField.setDrawsBackground(false);
        nickField.setCentered(true);

        toggleButton = ButtonWidget.builder(Text.literal(toggleLabel()), button -> {
            NickHiderConfig.setEnabled(!NickHiderConfig.isEnabled());
            button.setMessage(Text.literal(toggleLabel()));
            NickHiderConfig.save();
        }).dimensions(cx, panelY + 40, 220, 20).build();

        othersNickField = new TextFieldWidget(textRenderer, cx, panelY + 68, 220, 20, Text.literal("Others Nickname"));
        othersNickField.setText(NickHiderConfig.getOthersNickname());
        othersNickField.setEditableColor(0xFFFFFFFF);
        othersNickField.setUneditableColor(0xFFFFFFFF);
        othersNickField.setDrawsBackground(false);
        othersNickField.setCentered(true);

        toggleOthersButton = ButtonWidget.builder(Text.literal(toggleOthersLabel()), button -> {
            NickHiderConfig.setHideOthersMode(NickHiderConfig.getHideOthersMode().next());
            button.setMessage(Text.literal(toggleOthersLabel()));
            NickHiderConfig.save();
        }).dimensions(cx, panelY + 92, 220, 20).build();

        editNamesButton = ButtonWidget.builder(Text.literal("Edit Names File"), button -> {
            try {
                java.nio.file.Path path = NickHiderConfig.getNamesConfigPath();
                if (!java.nio.file.Files.exists(path)) {
                    NickHiderConfig.loadNameMappings(); // creates template
                }
                openFileInEditor(path);
            } catch (Exception ignored) {
            }
        }).dimensions(cx, panelY + 116, 107, 20).build();

        reloadNamesButton = ButtonWidget.builder(Text.literal("Reload Names"), button -> {
            NickHiderConfig.loadNameMappings();
        }).dimensions(cx + 113, panelY + 116, 107, 20).build();

        doneButton = ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + 178, 100, 20)
                .build();

        addSelectableChild(nickField);
        addDrawableChild(toggleButton);
        addSelectableChild(othersNickField);
        addDrawableChild(toggleOthersButton);
        addDrawableChild(editNamesButton);
        addDrawableChild(reloadNamesButton);
        addDrawableChild(doneButton);
    }

    private String toggleLabel() {
        return "Neck Hider: " + (NickHiderConfig.isEnabled() ? "ON" : "OFF");
    }

    private String toggleOthersLabel() {
        return "Hide Others: " + NickHiderConfig.getHideOthersMode().getLabel();
    }

    @Override
    public void close() {
        requestClose();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // keep world visible; no blur
        float openProgress = Math.min(1.0f, (Util.getMeasuringTimeMs() - openStartMs) / (float) FADE_DURATION_MS);
        float closeProgress = closing ? Math.min(1.0f, (Util.getMeasuringTimeMs() - closeStartMs) / (float) FADE_DURATION_MS) : 0f;
        if (closing && closeProgress >= 1.0f) {
            finishClose();
            return;
        }
        float guiAlpha = closing ? (1.0f - closeProgress) : openProgress;
        if (guiAlpha <= 0f) {
            return;
        }

        float scale = lerp(SCALE_START, 1.0f, guiAlpha);

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) (panelX + BOX_WIDTH / 2), (float) (panelY + BOX_HEIGHT / 2));
        matrices.scale(scale, scale);
        matrices.translate((float) -(panelX + BOX_WIDTH / 2), (float) -(panelY + BOX_HEIGHT / 2));

        // Panel backdrop
        int left = panelX;
        int top = panelY;
        int right = left + BOX_WIDTH;
        int bottom = top + BOX_HEIGHT;
        int baseColor = applyAlpha(0xAA000000, guiAlpha);
        context.fill(left, top, right, bottom, baseColor);

        // Chroma outline similar to main screen
        drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        // Dynamic chroma text and outlines for controls
        int chromaFast = applyAlpha(chromaColor((System.currentTimeMillis() % 4000) / 4000f), guiAlpha);
        int chromaSlow = applyAlpha(chromaColor(((System.currentTimeMillis() % 8000) / 8000f)), guiAlpha);

        // Nick field custom background (solid black) + chroma outline, centered text
        int fieldFill = 0xFF000000;
        renderTextField(context, nickField, fieldFill, guiAlpha, mouseX, mouseY, delta);

        // Toggle button chroma outline + text, flat fill
        styleButtonFlat(context, toggleButton, chromaFast, guiAlpha, mouseX, mouseY);

        // Others nick field
        renderTextField(context, othersNickField, fieldFill, guiAlpha, mouseX, mouseY, delta);

        // Toggle others button
        styleButtonFlat(context, toggleOthersButton, chromaFast, guiAlpha, mouseX, mouseY);

        // Edit/Reload names buttons
        styleButtonFlat(context, editNamesButton, chromaSlow, guiAlpha, mouseX, mouseY);
        styleButtonFlat(context, reloadNamesButton, chromaSlow, guiAlpha, mouseX, mouseY);

        // Mode description hints
        String hint1 = "Config Only = change specific names only";
        String hint2 = "File + Default = change all, override specific";
        String hint3 = "Only affects players in your current lobby";
        int hintColor = applyAlpha(0xFF888888, guiAlpha);
        int hint1X = panelX + (BOX_WIDTH - textRenderer.getWidth(hint1)) / 2;
        int hint2X = panelX + (BOX_WIDTH - textRenderer.getWidth(hint2)) / 2;
        int hint3X = panelX + (BOX_WIDTH - textRenderer.getWidth(hint3)) / 2;
        context.drawTextWithShadow(textRenderer, hint1, hint1X, panelY + 140, hintColor);
        context.drawTextWithShadow(textRenderer, hint2, hint2X, panelY + 150, hintColor);
        context.drawTextWithShadow(textRenderer, hint3, hint3X, panelY + 160, hintColor);

        // Done button chroma outline + text, flat fill
        styleButtonFlat(context, doneButton, chromaSlow, guiAlpha, mouseX, mouseY);

        matrices.popMatrix();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // disable vanilla blur for this config screen
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
            double mouseX = click.x();
            double mouseY = click.y();
            int newX = dragStartPanelX + (int) (mouseX - dragStartMouseX);
            int newY = dragStartPanelY + (int) (mouseY - dragStartMouseY);
            newX = Math.max(0, Math.min(newX, width - BOX_WIDTH));
            newY = Math.max(0, Math.min(newY, height - BOX_HEIGHT));
            panelX = savedX = newX;
            panelY = savedY = newY;
            // also move controls
            int cx = panelX + (BOX_WIDTH - 220) / 2;
            nickField.setX(cx);
            nickField.setY(panelY + 16);
            toggleButton.setX(cx);
            toggleButton.setY(panelY + 40);
            othersNickField.setX(cx);
            othersNickField.setY(panelY + 68);
            toggleOthersButton.setX(cx);
            toggleOthersButton.setY(panelY + 92);
            editNamesButton.setX(cx);
            editNamesButton.setY(panelY + 116);
            reloadNamesButton.setX(cx + 113);
            reloadNamesButton.setY(panelY + 116);
            doneButton.setX(panelX + (BOX_WIDTH - 100) / 2);
            doneButton.setY(panelY + 178);
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

    private void requestClose() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    private void finishClose() {
        NickHiderConfig.setNickname(nickField.getText());
        NickHiderConfig.setOthersNickname(othersNickField.getText());
        NickHiderConfig.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private float lerp(float a, float b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return a + (b - a) * t;
    }

    private void renderTextField(DrawContext context, TextFieldWidget field, int fillColor, float guiAlpha, int mouseX, int mouseY, float delta) {
        context.fill(field.getX(), field.getY(), field.getX() + field.getWidth(), field.getY() + field.getHeight(), applyAlpha(fillColor, guiAlpha));
        drawChromaBorder(context,
                field.getX() - 1, field.getY() - 1,
                field.getX() + field.getWidth() + 1,
                field.getY() + field.getHeight() + 1,
                guiAlpha);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0f, 6f);
        field.render(context, mouseX, mouseY, delta);
        context.getMatrices().popMatrix();
    }

    private void styleButtonFlat(DrawContext context, ButtonWidget button, int chromaColor, float alpha, int mouseX, int mouseY) {
        int bx = button.getX();
        int by = button.getY();
        int bw = button.getWidth();
        int bh = button.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha);
        context.fill(bx, by, bx + bw, by + bh, fill);
        drawChromaBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);

        // Draw centered text with chroma color
        String label = button.getMessage().getString();
        int textWidth = textRenderer.getWidth(label);
        int tx = bx + (bw - textWidth) / 2;
        int ty = by + (bh - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(textRenderer, label, tx, ty, chromaColor);
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

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
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
        } catch (Exception ignored) {
        }
    }

    private int chromaColor(float offset) {
        double time = (System.currentTimeMillis() % 4000) / 4000.0;
        float hue = (float) ((time + offset) % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
