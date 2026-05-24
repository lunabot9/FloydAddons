package floydaddons.not.dogshit.client.gui.v2.row;

import floydaddons.not.dogshit.client.FloydAddonsConfig;
import floydaddons.not.dogshit.client.config.CameraConfig;
import floydaddons.not.dogshit.client.config.RenderConfig;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.Slider;
import floydaddons.not.dogshit.client.gui.v2.widget.ToggleSwitch;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Body for the "Visual" accordion row. Combines the former Render-misc options (custom
 * world time, borderless windowed, custom window title) with the Camera options
 * (Freecam, Freelook, F5 customizer). Tall body — relies on the enclosing
 * {@link floydaddons.not.dogshit.client.gui.v2.widget.ContentPane} for vertical scroll.
 */
public class VisualRow implements AccordionRow.Body {
    private static final int ROW_H = 18;
    private static final int ROW_GAP = 4;
    private static final int SECTION_GAP = 10;
    private static final int PAD_TOP = 6;
    private static final int PAD_BOTTOM = 8;
    private static final int LABEL_W = 110;
    private static final int CTRL_GAP = 8;
    private static final int SIDE_PAD = 12;
    private static final int HEADER_H = 12;

    // --- Custom Time block ---
    private final ToggleSwitch customTimeToggle;
    private final Slider customTimeSlider;

    // --- Window block ---
    private final ToggleSwitch borderlessToggle;
    private final TextFieldWidget windowTitleField;

    // --- Camera block ---
    private final ToggleSwitch freecamToggle;
    private final Slider freecamSpeedSlider;
    private final ToggleSwitch freelookToggle;
    private final Slider freelookDistanceSlider;
    private final ToggleSwitch f5DisableFrontToggle;
    private final ToggleSwitch f5DisableBackToggle;
    private final ToggleSwitch f5NoClipToggle;
    private final ToggleSwitch f5ScrollToggle;
    private final ToggleSwitch f5ResetToggle;
    private final Slider f5DistanceSlider;

    // Last-known geometry of the window-title field (for hit testing in mouseClicked).
    private int titleFieldX, titleFieldY, titleFieldW;

    public VisualRow() {
        // --- Custom Time ---
        this.customTimeToggle = new ToggleSwitch(0, 0,
                RenderConfig::isCustomTimeEnabled,
                v -> { RenderConfig.setCustomTimeEnabled(v); FloydAddonsConfig.save(); });
        this.customTimeSlider = new Slider(0, 0, 0, ROW_H, 0.0, 100.0,
                () -> RenderConfig.getCustomTimeValue(),
                v -> { RenderConfig.setCustomTimeValue((float) v); FloydAddonsConfig.save(); })
                .withStep(1.0).withDecimals(0);

        // --- Window ---
        this.borderlessToggle = new ToggleSwitch(0, 0,
                RenderConfig::isBorderlessWindowed,
                RenderConfig::setBorderlessWindowed); // setBorderlessWindowed already saves
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        this.windowTitleField = new TextFieldWidget(tr, 0, 0, 100, ROW_H, Text.literal("Window Title"));
        this.windowTitleField.setMaxLength(64);
        this.windowTitleField.setEditableColor(0xFFFFFFFF);
        this.windowTitleField.setUneditableColor(0xFFB8B8B8);
        this.windowTitleField.setDrawsBackground(true);
        this.windowTitleField.setText(RenderConfig.getWindowTitle());
        this.windowTitleField.setChangedListener(s -> {
            RenderConfig.setWindowTitle(s);
            FloydAddonsConfig.save();
        });

        // --- Camera ---
        this.freecamToggle = new ToggleSwitch(0, 0,
                CameraConfig::isFreecamEnabled,
                v -> {
                    if (CameraConfig.isFreecamEnabled() != v) CameraConfig.toggleFreecam();
                    FloydAddonsConfig.save();
                });
        this.freecamSpeedSlider = new Slider(0, 0, 0, ROW_H, 0.1, 10.0,
                () -> CameraConfig.getFreecamSpeed(),
                v -> { CameraConfig.setFreecamSpeed((float) v); FloydAddonsConfig.save(); })
                .withStep(0.1).withDecimals(1);

        this.freelookToggle = new ToggleSwitch(0, 0,
                CameraConfig::isFreelookEnabled,
                v -> {
                    if (CameraConfig.isFreelookEnabled() != v) CameraConfig.toggleFreelook();
                    FloydAddonsConfig.save();
                });
        this.freelookDistanceSlider = new Slider(0, 0, 0, ROW_H, 1.0, 20.0,
                () -> CameraConfig.getFreelookDistance(),
                v -> { CameraConfig.setFreelookDistance((float) v); FloydAddonsConfig.save(); })
                .withStep(0.1).withDecimals(1);

        this.f5DisableFrontToggle = new ToggleSwitch(0, 0,
                CameraConfig::isF5DisableFront,
                v -> { CameraConfig.setF5DisableFront(v); FloydAddonsConfig.save(); });
        this.f5DisableBackToggle = new ToggleSwitch(0, 0,
                CameraConfig::isF5DisableBack,
                v -> { CameraConfig.setF5DisableBack(v); FloydAddonsConfig.save(); });
        this.f5NoClipToggle = new ToggleSwitch(0, 0,
                CameraConfig::isF5NoClip,
                v -> { CameraConfig.setF5NoClip(v); FloydAddonsConfig.save(); });
        this.f5ScrollToggle = new ToggleSwitch(0, 0,
                CameraConfig::isF5ScrollEnabled,
                v -> { CameraConfig.setF5ScrollEnabled(v); FloydAddonsConfig.save(); });
        this.f5ResetToggle = new ToggleSwitch(0, 0,
                CameraConfig::isF5ResetOnToggle,
                v -> { CameraConfig.setF5ResetOnToggle(v); FloydAddonsConfig.save(); });
        this.f5DistanceSlider = new Slider(0, 0, 0, ROW_H, 1.0, 20.0,
                () -> CameraConfig.getF5CameraDistance(),
                v -> { CameraConfig.setF5CameraDistance((float) v); FloydAddonsConfig.save(); })
                .withStep(0.1).withDecimals(1);
    }

    @Override
    public int getHeight() {
        // 3 sections, each preceded by a header. Rows per section:
        //   Custom Time: 2 rows (toggle, slider)
        //   Window: 2 rows (borderless toggle, title field)
        //   Camera: 10 rows (freecam, speed, freelook, dist, 5 f5 toggles, f5 dist)
        int rowCount = 2 + 2 + 10;
        int sectionCount = 3;
        return PAD_TOP
                + sectionCount * HEADER_H
                + rowCount * ROW_H
                + (rowCount + sectionCount - 1) * ROW_GAP // small gap between every row+header
                + (sectionCount - 1) * SECTION_GAP
                + PAD_BOTTOM;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
        int contentX = x + SIDE_PAD;
        int contentW = w - SIDE_PAD * 2;
        int rowY = y + PAD_TOP;

        // Custom Time
        rowY = drawSectionHeader(ctx, "Custom Time", contentX, contentW, rowY);
        rowY = renderLabeledToggle(ctx, "Custom Time", customTimeToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Time", customTimeSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY += SECTION_GAP;

        // Window
        rowY = drawSectionHeader(ctx, "Window", contentX, contentW, rowY);
        rowY = renderLabeledToggle(ctx, "Borderless", borderlessToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderTitleField(ctx, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY += SECTION_GAP;

        // Camera
        rowY = drawSectionHeader(ctx, "Camera", contentX, contentW, rowY);
        rowY = renderLabeledToggle(ctx, "Freecam", freecamToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Speed", freecamSpeedSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledToggle(ctx, "Freelook", freelookToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Distance", freelookDistanceSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledToggle(ctx, "Disable F5 Front", f5DisableFrontToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledToggle(ctx, "Disable F5 Back", f5DisableBackToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledToggle(ctx, "F5 NoClip", f5NoClipToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledToggle(ctx, "F5 Scroll", f5ScrollToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledToggle(ctx, "F5 Reset on Toggle", f5ResetToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        renderLabeledSlider(ctx, "F5 Distance", f5DistanceSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
    }

    private int drawSectionHeader(DrawContext ctx, String text, int contentX, int contentW, int rowY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text styled = Text.literal(text).styled(s -> s.withBold(true));
        ctx.drawText(tr, styled, contentX, rowY, V2Theme.TEXT_PRIMARY, false);
        // Thin divider under the header
        int lineY = rowY + tr.fontHeight + 1;
        ctx.fill(contentX, lineY, contentX + contentW, lineY + 1, V2Theme.DIVIDER);
        return rowY + HEADER_H + ROW_GAP;
    }

    private int renderLabeledSlider(DrawContext ctx, String label, Slider slider,
                                    int contentX, int contentW, int rowY,
                                    int mouseX, int mouseY, float delta) {
        drawLabel(ctx, label, contentX, rowY);
        int sliderX = contentX + LABEL_W + CTRL_GAP;
        int sliderW = contentW - LABEL_W - CTRL_GAP;
        slider.setPos(sliderX, rowY);
        slider.setSize(sliderW, ROW_H);
        slider.render(ctx, mouseX, mouseY, delta);
        return rowY + ROW_H + ROW_GAP;
    }

    private int renderLabeledToggle(DrawContext ctx, String label, ToggleSwitch toggle,
                                    int contentX, int contentW, int rowY,
                                    int mouseX, int mouseY, float delta) {
        drawLabel(ctx, label, contentX, rowY);
        int tx = contentX + contentW - toggle.getWidth();
        int ty = rowY + (ROW_H - toggle.getHeight()) / 2;
        toggle.setPos(tx, ty);
        toggle.render(ctx, mouseX, mouseY, delta);
        return rowY + ROW_H + ROW_GAP;
    }

    private int renderTitleField(DrawContext ctx, int contentX, int contentW, int rowY,
                                 int mouseX, int mouseY, float delta) {
        drawLabel(ctx, "Window Title", contentX, rowY);
        int fieldX = contentX + LABEL_W + CTRL_GAP;
        int fieldW = contentW - LABEL_W - CTRL_GAP;
        windowTitleField.setX(fieldX);
        windowTitleField.setY(rowY);
        windowTitleField.setWidth(fieldW);
        windowTitleField.setHeight(ROW_H);
        windowTitleField.render(ctx, mouseX, mouseY, delta);
        titleFieldX = fieldX;
        titleFieldY = rowY;
        titleFieldW = fieldW;
        return rowY + ROW_H + ROW_GAP;
    }

    private void drawLabel(DrawContext ctx, String label, int x, int rowY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int ty = rowY + (ROW_H - tr.fontHeight) / 2 + 1;
        ctx.drawText(tr, label, x, ty, V2Theme.TEXT_PRIMARY, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Toggles + sliders first; they have hover-based hit tests.
        if (customTimeToggle.mouseClicked(mx, my, button)) return true;
        if (customTimeSlider.mouseClicked(mx, my, button)) return true;
        if (borderlessToggle.mouseClicked(mx, my, button)) return true;

        // Window title field: focus on click in bounds, drop focus otherwise.
        boolean inField = mx >= titleFieldX && mx < titleFieldX + titleFieldW
                && my >= titleFieldY && my < titleFieldY + ROW_H;
        if (inField) {
            windowTitleField.setFocused(true);
            return true;
        } else if (windowTitleField.isFocused()) {
            windowTitleField.setFocused(false);
        }

        if (freecamToggle.mouseClicked(mx, my, button)) return true;
        if (freecamSpeedSlider.mouseClicked(mx, my, button)) return true;
        if (freelookToggle.mouseClicked(mx, my, button)) return true;
        if (freelookDistanceSlider.mouseClicked(mx, my, button)) return true;
        if (f5DisableFrontToggle.mouseClicked(mx, my, button)) return true;
        if (f5DisableBackToggle.mouseClicked(mx, my, button)) return true;
        if (f5NoClipToggle.mouseClicked(mx, my, button)) return true;
        if (f5ScrollToggle.mouseClicked(mx, my, button)) return true;
        if (f5ResetToggle.mouseClicked(mx, my, button)) return true;
        if (f5DistanceSlider.mouseClicked(mx, my, button)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        any |= customTimeSlider.mouseReleased(mx, my, button);
        any |= freecamSpeedSlider.mouseReleased(mx, my, button);
        any |= freelookDistanceSlider.mouseReleased(mx, my, button);
        any |= f5DistanceSlider.mouseReleased(mx, my, button);
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        boolean any = false;
        any |= customTimeSlider.mouseDragged(mx, my, button, dx, dy);
        any |= freecamSpeedSlider.mouseDragged(mx, my, button, dx, dy);
        any |= freelookDistanceSlider.mouseDragged(mx, my, button, dx, dy);
        any |= f5DistanceSlider.mouseDragged(mx, my, button, dx, dy);
        return any;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (windowTitleField.isFocused()) {
            return windowTitleField.keyPressed(new KeyInput(keyCode, scanCode, modifiers));
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (windowTitleField.isFocused()) {
            return windowTitleField.charTyped(new CharInput(chr, modifiers));
        }
        return false;
    }
}
