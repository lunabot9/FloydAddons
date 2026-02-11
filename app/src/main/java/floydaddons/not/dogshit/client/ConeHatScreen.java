package floydaddons.not.dogshit.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;

public class ConeHatScreen extends Screen {
    private final Screen parent;

    private ConfigSlider heightSlider;
    private ConfigSlider radiusSlider;
    private ConfigSlider yOffsetSlider;
    private ConfigSlider rotationSlider;
    private ButtonWidget openFolderButton;
    private ButtonWidget doneButton;

    private static final int CONTROLS_WIDTH = 310;
    private static final int PREVIEW_WIDTH = 140;
    private static final int BOX_WIDTH = CONTROLS_WIDTH + PREVIEW_WIDTH;
    private static final int BOX_HEIGHT = 220;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 26;
    private static final int SLIDER_W = 166;
    private static final int INPUT_W = 50;
    private static final int INPUT_GAP = 4;
    private static final int FULL_W = SLIDER_W + INPUT_GAP + INPUT_W; // 220
    private static final int DD_W = 148;
    private static final int FOLDER_W = FULL_W - DD_W - INPUT_GAP; // 68

    private int panelX, panelY;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    // Dropdown state
    private boolean dropdownOpen = false;
    private List<String> availableImages = List.of();
    private int dropdownScroll = 0;
    private static final int DROPDOWN_ROW_HEIGHT = 16;
    private static final int DROPDOWN_MAX_VISIBLE = 5;

    // Text input state
    private int editingSlider = -1; // 0=height, 1=radius, 2=yOffset, 3=rotation
    private String editBuffer = "";

    public ConeHatScreen(Screen parent) {
        super(Text.literal("Cone Hat Config"));
        this.parent = parent;
    }

    private int leftEdge() { return panelX + (CONTROLS_WIDTH - FULL_W) / 2; }
    private int rowY(int row) { return panelY + 26 + row * ROW_SPACING; }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;
        editingSlider = -1;

        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;

        refreshImageList();

        int le = leftEdge();

        // Row 0: Height slider
        heightSlider = new ConfigSlider(le, rowY(0), SLIDER_W, ROW_HEIGHT,
                Text.literal(heightLabel()), heightToSlider(RenderConfig.getConeHatHeight())) {
            @Override protected void updateMessage() { setMessage(Text.literal(heightLabel())); }
            @Override protected void applyValue() {
                RenderConfig.setConeHatHeight(sliderToHeight(this.value));
                RenderConfig.save();
            }
        };

        // Row 1: Radius slider
        radiusSlider = new ConfigSlider(le, rowY(1), SLIDER_W, ROW_HEIGHT,
                Text.literal(radiusLabel()), radiusToSlider(RenderConfig.getConeHatRadius())) {
            @Override protected void updateMessage() { setMessage(Text.literal(radiusLabel())); }
            @Override protected void applyValue() {
                RenderConfig.setConeHatRadius(sliderToRadius(this.value));
                RenderConfig.save();
            }
        };

        // Row 2: Y Offset slider
        yOffsetSlider = new ConfigSlider(le, rowY(2), SLIDER_W, ROW_HEIGHT,
                Text.literal(yOffsetLabel()), yOffsetToSlider(RenderConfig.getConeHatYOffset())) {
            @Override protected void updateMessage() { setMessage(Text.literal(yOffsetLabel())); }
            @Override protected void applyValue() {
                RenderConfig.setConeHatYOffset(sliderToYOffset(this.value));
                RenderConfig.save();
            }
        };

        // Row 3: Rotation slider
        rotationSlider = new ConfigSlider(le, rowY(3), SLIDER_W, ROW_HEIGHT,
                Text.literal(rotationLabel()), rotationToSlider(RenderConfig.getConeHatRotation())) {
            @Override protected void updateMessage() { setMessage(Text.literal(rotationLabel())); }
            @Override protected void applyValue() {
                RenderConfig.setConeHatRotation(sliderToRotation(this.value));
                RenderConfig.save();
            }
        };

        // Row 4: Image dropdown (DD_W) + Open folder (FOLDER_W) side by side
        openFolderButton = ButtonWidget.builder(Text.literal("Open folder"), b -> {
            var dir = ConeHatManager.ensureDir();
            openPath(dir);
        }).dimensions(le + DD_W + INPUT_GAP, rowY(4), FOLDER_W, ROW_HEIGHT).build();

        // Done button centered in controls area
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (CONTROLS_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, ROW_HEIGHT)
                .build();

        addDrawableChild(heightSlider);
        addDrawableChild(radiusSlider);
        addDrawableChild(yOffsetSlider);
        addDrawableChild(rotationSlider);
        addDrawableChild(openFolderButton);
        addDrawableChild(doneButton);
    }

    private void refreshImageList() {
        availableImages = ConeHatManager.listAvailableImages();
        dropdownScroll = 0;
    }

    // Slider conversion: height 0.1 - 1.5
    private String heightLabel() { return "Height: " + String.format("%.2f", RenderConfig.getConeHatHeight()); }
    private double heightToSlider(float v) { return (v - 0.1f) / 1.4f; }
    private float sliderToHeight(double s) { return (float)(s * 1.4 + 0.1); }

    // Slider conversion: radius 0.05 - 0.8
    private String radiusLabel() { return "Radius: " + String.format("%.2f", RenderConfig.getConeHatRadius()); }
    private double radiusToSlider(float v) { return (v - 0.05f) / 0.75f; }
    private float sliderToRadius(double s) { return (float)(s * 0.75 + 0.05); }

    // Slider conversion: yOffset -1.5 - 0.5
    private String yOffsetLabel() { return "Y Offset: " + String.format("%.2f", RenderConfig.getConeHatYOffset()); }
    private double yOffsetToSlider(float v) { return (v + 1.5f) / 2.0f; }
    private float sliderToYOffset(double s) { return (float)(s * 2.0 - 1.5); }

    // Slider conversion: rotation 0 - 360
    private String rotationLabel() { return "Rotation: " + Math.round(RenderConfig.getConeHatRotation()) + "\u00B0"; }
    private double rotationToSlider(float v) { return v / 360.0; }
    private float sliderToRotation(double s) { return (float)(s * 360.0); }

    private String currentImageLabel() {
        String sel = RenderConfig.getSelectedConeImage();
        if (sel == null || sel.isEmpty()) return "Default (Floyd.png)";
        return sel;
    }

    // --- Text input editing ---

    private String getSliderValueText(int index) {
        return switch (index) {
            case 0 -> String.format("%.2f", RenderConfig.getConeHatHeight());
            case 1 -> String.format("%.2f", RenderConfig.getConeHatRadius());
            case 2 -> String.format("%.2f", RenderConfig.getConeHatYOffset());
            case 3 -> String.valueOf(Math.round(RenderConfig.getConeHatRotation()));
            default -> "";
        };
    }

    private void startEditing(int index) {
        if (editingSlider >= 0 && editingSlider != index) {
            tryApplyEdit();
        }
        editingSlider = index;
        editBuffer = getSliderValueText(index);
    }

    private void tryApplyEdit() {
        if (editingSlider < 0) return;
        try {
            float val = Float.parseFloat(editBuffer);
            switch (editingSlider) {
                case 0 -> {
                    RenderConfig.setConeHatHeight(val);
                    heightSlider.setSliderValue(heightToSlider(RenderConfig.getConeHatHeight()));
                }
                case 1 -> {
                    RenderConfig.setConeHatRadius(val);
                    radiusSlider.setSliderValue(radiusToSlider(RenderConfig.getConeHatRadius()));
                }
                case 2 -> {
                    RenderConfig.setConeHatYOffset(val);
                    yOffsetSlider.setSliderValue(yOffsetToSlider(RenderConfig.getConeHatYOffset()));
                }
                case 3 -> {
                    RenderConfig.setConeHatRotation(val);
                    rotationSlider.setSliderValue(rotationToSlider(RenderConfig.getConeHatRotation()));
                }
            }
            RenderConfig.save();
        } catch (NumberFormatException ignored) {}
        editingSlider = -1;
    }

    // --- Lifecycle ---

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
        if (editingSlider >= 0) tryApplyEdit();
        RenderConfig.save();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    // --- Input handling ---

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();

        // Finish editing on click outside the active input box
        if (editingSlider >= 0 && click.button() == 0) {
            int inputX = leftEdge() + SLIDER_W + INPUT_GAP;
            int inputY = rowY(editingSlider);
            if (!(mx >= inputX && mx <= inputX + INPUT_W && my >= inputY && my <= inputY + ROW_HEIGHT)) {
                tryApplyEdit();
            }
        }

        // Handle dropdown list clicks
        if (dropdownOpen && click.button() == 0) {
            int ddX = leftEdge();
            int ddTop = rowY(4) + ROW_HEIGHT + 2;
            int ddWidth = DD_W;
            int visibleCount = Math.min(availableImages.size(), DROPDOWN_MAX_VISIBLE);
            int ddHeight = Math.max(DROPDOWN_ROW_HEIGHT, visibleCount * DROPDOWN_ROW_HEIGHT);

            if (mx >= ddX && mx <= ddX + ddWidth && my >= ddTop && my <= ddTop + ddHeight) {
                int rowIndex = (int) ((my - ddTop) / DROPDOWN_ROW_HEIGHT) + dropdownScroll;
                if (rowIndex >= 0 && rowIndex < availableImages.size()) {
                    RenderConfig.setSelectedConeImage(availableImages.get(rowIndex));
                    RenderConfig.save();
                    ConeHatManager.clearCache();
                    dropdownOpen = false;
                    return true;
                }
            }
            dropdownOpen = false;
            return true;
        }

        // Handle input box clicks
        if (click.button() == 0) {
            int inputX = leftEdge() + SLIDER_W + INPUT_GAP;
            for (int i = 0; i < 4; i++) {
                int inputY = rowY(i);
                if (mx >= inputX && mx <= inputX + INPUT_W && my >= inputY && my <= inputY + ROW_HEIGHT) {
                    startEditing(i);
                    return true;
                }
            }
        }

        // Handle dropdown button click
        if (click.button() == 0) {
            int ddBtnX = leftEdge();
            int ddBtnY = rowY(4);
            if (mx >= ddBtnX && mx <= ddBtnX + DD_W && my >= ddBtnY && my <= ddBtnY + ROW_HEIGHT) {
                refreshImageList();
                dropdownOpen = !dropdownOpen;
                return true;
            }
        }

        // Drag bar
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
    public boolean charTyped(CharInput input) {
        if (editingSlider >= 0) {
            int cp = input.codepoint();
            char chr = (char) cp;
            if (chr == '-' || chr == '.' || (chr >= '0' && chr <= '9')) {
                editBuffer += chr;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (editingSlider >= 0) {
            if (input.isEnter()) {
                tryApplyEdit();
                return true;
            }
            if (input.isEscape()) {
                editingSlider = -1;
                return true;
            }
            if (input.key() == 259) { // Backspace
                if (!editBuffer.isEmpty()) {
                    editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                }
                return true;
            }
            return true; // consume other keys while editing
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dropdownOpen && availableImages.size() > DROPDOWN_MAX_VISIBLE) {
            int ddX = leftEdge();
            int ddTop = rowY(4) + ROW_HEIGHT + 2;
            int ddWidth = DD_W;
            int ddHeight = DROPDOWN_MAX_VISIBLE * DROPDOWN_ROW_HEIGHT;
            if (mouseX >= ddX && mouseX <= ddX + ddWidth && mouseY >= ddTop && mouseY <= ddTop + ddHeight) {
                dropdownScroll -= (int) verticalAmount;
                dropdownScroll = Math.max(0, Math.min(dropdownScroll, availableImages.size() - DROPDOWN_MAX_VISIBLE));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
        heightSlider.setX(le);   heightSlider.setY(rowY(0));
        radiusSlider.setX(le);   radiusSlider.setY(rowY(1));
        yOffsetSlider.setX(le);  yOffsetSlider.setY(rowY(2));
        rotationSlider.setX(le); rotationSlider.setY(rowY(3));
        openFolderButton.setX(le + DD_W + INPUT_GAP); openFolderButton.setY(rowY(4));
        doneButton.setX(panelX + (CONTROLS_WIDTH - 100) / 2); doneButton.setY(panelY + BOX_HEIGHT - 30);
    }

    // --- Rendering ---

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

        // Divider between controls and preview
        int divX = panelX + CONTROLS_WIDTH;
        InventoryHudRenderer.drawChromaBorder(context, divX - 1, top + DRAG_BAR_HEIGHT, divX, bottom - 1, guiAlpha);

        // Title centered over controls area
        String title = "Cone Hat Config";
        int titleWidth = textRenderer.getWidth(title);
        context.drawTextWithShadow(textRenderer, title,
                panelX + (CONTROLS_WIDTH - titleWidth) / 2, panelY + 6,
                applyAlpha(chromaColor(0f), guiAlpha));

        // Sliders + input boxes
        renderSliderRow(context, 0, heightSlider, guiAlpha, mouseX, mouseY,
                (float) heightToSlider(RenderConfig.getConeHatHeight()));
        renderSliderRow(context, 1, radiusSlider, guiAlpha, mouseX, mouseY,
                (float) radiusToSlider(RenderConfig.getConeHatRadius()));
        renderSliderRow(context, 2, yOffsetSlider, guiAlpha, mouseX, mouseY,
                (float) yOffsetToSlider(RenderConfig.getConeHatYOffset()));
        renderSliderRow(context, 3, rotationSlider, guiAlpha, mouseX, mouseY,
                (float) rotationToSlider(RenderConfig.getConeHatRotation()));

        // Row 4: dropdown button + open folder button (side by side)
        renderDropdownButton(context, mouseX, mouseY, guiAlpha);
        styleButton(context, openFolderButton, guiAlpha, mouseX, mouseY);

        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Hint
        String hint = "Drop .png into cone-hats folder.";
        int hintW = textRenderer.getWidth(hint);
        context.drawTextWithShadow(textRenderer, hint,
                panelX + (CONTROLS_WIDTH - hintW) / 2, panelY + BOX_HEIGHT - 48,
                applyAlpha(0xFF888888, guiAlpha));

        // "Preview" label
        String previewLabel = "Preview";
        int plW = textRenderer.getWidth(previewLabel);
        context.drawTextWithShadow(textRenderer, previewLabel,
                divX + (PREVIEW_WIDTH - plW) / 2, panelY + 6,
                applyAlpha(chromaColor(0f), guiAlpha));

        // Dropdown expanded list rendered LAST so it draws on top of everything
        if (dropdownOpen) {
            renderDropdownList(context, mouseX, mouseY, guiAlpha);
        }

        matrices.popMatrix();

        // Player preview — rendered outside the scale transform
        if (client != null && client.player != null && guiAlpha > 0.5f) {
            int prevX1 = panelX + CONTROLS_WIDTH + 4;
            int prevX2 = panelX + BOX_WIDTH - 4;
            int prevY1 = panelY + 20;
            int prevY2 = panelY + BOX_HEIGHT - 35;
            int prevCenterX = (prevX1 + prevX2) / 2;
            int prevCenterY = prevY2 - 10;
            InventoryScreen.drawEntity(context, prevX1, prevY1, prevX2, prevY2,
                    35, 0.0625f, prevCenterX, prevCenterY, client.player);
        }
    }

    private void renderSliderRow(DrawContext context, int index, ConfigSlider slider,
                                  float alpha, int mouseX, int mouseY, float pct) {
        // Slider portion
        styleSlider(context, slider, alpha, mouseX, mouseY, pct);

        // Input box to the right
        int inputX = leftEdge() + SLIDER_W + INPUT_GAP;
        int inputY = rowY(index);
        boolean editing = editingSlider == index;
        boolean hover = mouseX >= inputX && mouseX <= inputX + INPUT_W
                && mouseY >= inputY && mouseY <= inputY + ROW_HEIGHT;

        int bgColor = editing ? 0xFF222222 : (hover ? 0xFF444444 : 0xFF333333);
        context.fill(inputX, inputY, inputX + INPUT_W, inputY + ROW_HEIGHT, applyAlpha(bgColor, alpha));

        if (editing) {
            InventoryHudRenderer.drawChromaBorder(context, inputX - 1, inputY - 1,
                    inputX + INPUT_W + 1, inputY + ROW_HEIGHT + 1, alpha);
        } else {
            // Subtle 1px border
            context.fill(inputX, inputY, inputX + INPUT_W, inputY + 1, applyAlpha(0xFF555555, alpha));
            context.fill(inputX, inputY + ROW_HEIGHT - 1, inputX + INPUT_W, inputY + ROW_HEIGHT, applyAlpha(0xFF555555, alpha));
            context.fill(inputX, inputY, inputX + 1, inputY + ROW_HEIGHT, applyAlpha(0xFF555555, alpha));
            context.fill(inputX + INPUT_W - 1, inputY, inputX + INPUT_W, inputY + ROW_HEIGHT, applyAlpha(0xFF555555, alpha));
        }

        String displayText = editing ? editBuffer : getSliderValueText(index);
        // Truncate from the left if too wide
        while (textRenderer.getWidth(displayText) > INPUT_W - 6 && displayText.length() > 1) {
            displayText = displayText.substring(1);
        }

        int textY = inputY + (ROW_HEIGHT - textRenderer.fontHeight) / 2;

        if (editing) {
            int textColor = applyAlpha(0xFFFFFFFF, alpha);
            int tw = textRenderer.getWidth(displayText);
            int textXPos = inputX + INPUT_W - 4 - tw;
            context.drawTextWithShadow(textRenderer, displayText, textXPos, textY, textColor);

            // Blinking cursor
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cursorX = textXPos + tw;
                context.fill(cursorX, inputY + 3, cursorX + 1, inputY + ROW_HEIGHT - 3,
                        applyAlpha(0xFFFFFFFF, alpha));
            }
        } else {
            int textColor = applyAlpha(0xFFCCCCCC, alpha);
            int tw = textRenderer.getWidth(displayText);
            context.drawTextWithShadow(textRenderer, displayText,
                    inputX + (INPUT_W - tw) / 2, textY, textColor);
        }
    }

    private void renderDropdownButton(DrawContext context, int mouseX, int mouseY, float guiAlpha) {
        int ddX = leftEdge();
        int ddY = rowY(4);
        int ddW = DD_W;
        int ddH = ROW_HEIGHT;

        boolean barHover = mouseX >= ddX && mouseX <= ddX + ddW && mouseY >= ddY && mouseY <= ddY + ddH;
        context.fill(ddX, ddY, ddX + ddW, ddY + ddH, applyAlpha(barHover ? 0xFF444444 : 0xFF333333, guiAlpha));
        InventoryHudRenderer.drawChromaBorder(context, ddX - 1, ddY - 1, ddX + ddW + 1, ddY + ddH + 1, guiAlpha);

        String label = currentImageLabel();
        String arrow = dropdownOpen ? " \u25B2" : " \u25BC";
        int maxLabelW = ddW - textRenderer.getWidth(arrow) - 8;
        while (textRenderer.getWidth(label) > maxLabelW && label.length() > 3) {
            label = label.substring(0, label.length() - 1);
        }
        String display = label + arrow;
        int textW = textRenderer.getWidth(display);
        int chroma = applyAlpha(chromaColor((System.currentTimeMillis() % 4000) / 4000f), guiAlpha);
        context.drawTextWithShadow(textRenderer, display,
                ddX + (ddW - textW) / 2, ddY + (ddH - textRenderer.fontHeight) / 2, chroma);
    }

    private void renderDropdownList(DrawContext context, int mouseX, int mouseY, float guiAlpha) {
        int ddX = leftEdge();
        int ddW = DD_W;
        int listTop = rowY(4) + ROW_HEIGHT + 2;
        int chroma = applyAlpha(chromaColor((System.currentTimeMillis() % 4000) / 4000f), guiAlpha);

        if (!availableImages.isEmpty()) {
            int visibleCount = Math.min(availableImages.size(), DROPDOWN_MAX_VISIBLE);
            int listHeight = visibleCount * DROPDOWN_ROW_HEIGHT;

            context.fill(ddX, listTop, ddX + ddW, listTop + listHeight, applyAlpha(0xEE000000, guiAlpha));
            InventoryHudRenderer.drawChromaBorder(context, ddX - 1, listTop - 1,
                    ddX + ddW + 1, listTop + listHeight + 1, guiAlpha);

            String selected = RenderConfig.getSelectedConeImage();
            for (int i = 0; i < visibleCount; i++) {
                int idx = i + dropdownScroll;
                if (idx >= availableImages.size()) break;
                String name = availableImages.get(idx);
                int ry = listTop + i * DROPDOWN_ROW_HEIGHT;
                boolean rowHover = mouseX >= ddX && mouseX <= ddX + ddW
                        && mouseY >= ry && mouseY <= ry + DROPDOWN_ROW_HEIGHT;
                boolean isSel = name.equals(selected);

                if (rowHover) {
                    context.fill(ddX + 1, ry, ddX + ddW - 1, ry + DROPDOWN_ROW_HEIGHT,
                            applyAlpha(0xFF555555, guiAlpha));
                } else if (isSel) {
                    context.fill(ddX + 1, ry, ddX + ddW - 1, ry + DROPDOWN_ROW_HEIGHT,
                            applyAlpha(0xFF3A3A3A, guiAlpha));
                }

                String truncName = name;
                while (textRenderer.getWidth(truncName) > ddW - 10 && truncName.length() > 3) {
                    truncName = truncName.substring(0, truncName.length() - 1);
                }
                int nameColor = isSel ? chroma : applyAlpha(0xFFCCCCCC, guiAlpha);
                context.drawTextWithShadow(textRenderer, truncName,
                        ddX + 5, ry + (DROPDOWN_ROW_HEIGHT - textRenderer.fontHeight) / 2, nameColor);
            }

            if (availableImages.size() > DROPDOWN_MAX_VISIBLE) {
                int scrollBarH = Math.max(4, (int) ((float) visibleCount / availableImages.size() * listHeight));
                int scrollBarY = listTop + (int) ((float) dropdownScroll
                        / (availableImages.size() - visibleCount) * (listHeight - scrollBarH));
                context.fill(ddX + ddW - 3, scrollBarY, ddX + ddW - 1, scrollBarY + scrollBarH,
                        applyAlpha(0xFF888888, guiAlpha));
            }
        } else {
            context.fill(ddX, listTop, ddX + ddW, listTop + DROPDOWN_ROW_HEIGHT,
                    applyAlpha(0xEE000000, guiAlpha));
            InventoryHudRenderer.drawChromaBorder(context, ddX - 1, listTop - 1,
                    ddX + ddW + 1, listTop + DROPDOWN_ROW_HEIGHT + 1, guiAlpha);
            String empty = "No images found";
            int ew = textRenderer.getWidth(empty);
            context.drawTextWithShadow(textRenderer, empty,
                    ddX + (ddW - ew) / 2, listTop + (DROPDOWN_ROW_HEIGHT - textRenderer.fontHeight) / 2,
                    applyAlpha(0xFF888888, guiAlpha));
        }
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

    private void styleSlider(DrawContext context, SliderWidget slider, float alpha, int mouseX, int mouseY, float pct) {
        int bx = slider.getX(), by = slider.getY(), bw = slider.getWidth(), bh = slider.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        context.fill(bx, by, bx + bw, by + bh, applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha));
        int fillW = (int) ((bw - 4) * Math.max(0, Math.min(1, pct)));
        context.fill(bx + 2, by + 2, bx + 2 + fillW, by + bh - 2, applyAlpha(0xFF888888, alpha));
        InventoryHudRenderer.drawChromaBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = slider.getMessage().getString();
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
        } catch (Exception ignored) {}
    }

    /** Slider subclass that exposes value setter for text input sync. */
    private abstract static class ConfigSlider extends SliderWidget {
        ConfigSlider(int x, int y, int w, int h, Text text, double value) {
            super(x, y, w, h, text, value);
        }

        void setSliderValue(double v) {
            this.value = Math.max(0, Math.min(1, v));
            updateMessage();
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {}
    }
}
