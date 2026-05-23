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

import java.util.List;

/**
 * Detailed custom skin configuration (all skin-related controls).
 */
public class SkinSettingsScreen extends Screen {
    private final Screen parent;
    private ButtonWidget customToggle;
    private ButtonWidget selfToggle;
    private ButtonWidget othersToggle;
    private ButtonWidget openFolderButton;
    private ButtonWidget doneButton;

    private static final int BOX_WIDTH = 320;
    private static final int BOX_HEIGHT = 260;
    private static final long FADE_DURATION_MS = 90;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final int CONTROL_WIDTH = 220;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_SPACING = 26;
    private static final int DROPDOWN_ROW_HEIGHT = 16;
    private static final int DROPDOWN_MAX_VISIBLE = 5;

    private int panelX, panelY;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;
    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    // Dropdown state
    private boolean dropdownOpen = false;
    private List<String> availableSkins = List.of();
    private int dropdownScroll = 0;

    public SkinSettingsScreen(Screen parent) {
        super(Text.literal("Custom Skin Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;

        refreshSkinList();

        int cx = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
        int cy = panelY + 25;

        customToggle = ButtonWidget.builder(Text.literal(customLabel()), b -> {
            SkinConfig.setCustomEnabled(!SkinConfig.customEnabled());
            b.setMessage(Text.literal(customLabel()));
            SkinConfig.save();
            SkinManager.clearCache();
        }).dimensions(cx, cy, CONTROL_WIDTH, ROW_HEIGHT).build();

        selfToggle = ButtonWidget.builder(Text.literal(selfLabel()), b -> {
            SkinConfig.setSelfEnabled(!SkinConfig.selfEnabled());
            b.setMessage(Text.literal(selfLabel()));
            SkinConfig.save();
            SkinManager.clearCache();
        }).dimensions(cx, cy + ROW_SPACING, CONTROL_WIDTH, ROW_HEIGHT).build();

        othersToggle = ButtonWidget.builder(Text.literal(othersLabel()), b -> {
            SkinConfig.setOthersEnabled(!SkinConfig.othersEnabled());
            b.setMessage(Text.literal(othersLabel()));
            SkinConfig.save();
            SkinManager.clearCache();
        }).dimensions(cx, cy + ROW_SPACING * 2, CONTROL_WIDTH, ROW_HEIGHT).build();

        openFolderButton = ButtonWidget.builder(Text.literal("Open skin folder"), b -> {
            var dir = SkinManager.ensureExternalDir();
            openPath(dir);
        }).dimensions(cx, cy + ROW_SPACING * 3, CONTROL_WIDTH, ROW_HEIGHT).build();

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, 20)
                .build();

        addDrawableChild(customToggle);
        addDrawableChild(selfToggle);
        addDrawableChild(othersToggle);
        addDrawableChild(openFolderButton);
        addDrawableChild(doneButton);
    }

    private void refreshSkinList() {
        availableSkins = SkinManager.listAvailableSkins();
        dropdownScroll = 0;
    }

    private String customLabel() { return "Custom Skin: " + (SkinConfig.customEnabled() ? "ON" : "OFF"); }
    private String selfLabel() { return "Apply to me: " + (SkinConfig.selfEnabled() ? "ON" : "OFF"); }
    private String othersLabel() { return "Others: " + (SkinConfig.othersEnabled() ? "ON" : "OFF"); }

    private String currentSkinLabel() {
        String sel = SkinConfig.getSelectedSkin();
        if (sel == null || sel.isEmpty()) return "No skin selected";
        return sel;
    }

    @Override
    public void close() { requestClose(); }

    private void requestClose() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
        SkinConfig.save();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();

        // Dropdown first
        if (dropdownOpen && click.button() == 0) {
            int ddX = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
            int ddTop = dropdownTop();
            int ddWidth = CONTROL_WIDTH;
            int visibleCount = Math.min(availableSkins.size(), DROPDOWN_MAX_VISIBLE);
            int ddHeight = visibleCount * DROPDOWN_ROW_HEIGHT;
            if (mx >= ddX && mx <= ddX + ddWidth && my >= ddTop && my <= ddTop + ddHeight) {
                int rowIndex = (int) ((my - ddTop) / DROPDOWN_ROW_HEIGHT) + dropdownScroll;
                if (rowIndex >= 0 && rowIndex < availableSkins.size()) {
                    SkinConfig.setSelectedSkin(availableSkins.get(rowIndex));
                    SkinConfig.save();
                    SkinManager.clearCache();
                    dropdownOpen = false;
                    return true;
                }
            }
            dropdownOpen = false;
            return true;
        }

        if (click.button() == 0) {
            int ddBtnX = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
            int ddBtnY = dropdownButtonY();
            if (mx >= ddBtnX && mx <= ddBtnX + CONTROL_WIDTH && my >= ddBtnY && my <= ddBtnY + 16) {
                refreshSkinList();
                dropdownOpen = !dropdownOpen;
                return true;
            }
        }

        if (click.button() == 0 && mx >= panelX && mx <= panelX + BOX_WIDTH && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dropdownOpen && availableSkins.size() > DROPDOWN_MAX_VISIBLE) {
            int ddX = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
            int ddTop = dropdownTop();
            int ddWidth = CONTROL_WIDTH;
            int ddHeight = DROPDOWN_MAX_VISIBLE * DROPDOWN_ROW_HEIGHT;
            if (mouseX >= ddX && mouseX <= ddX + ddWidth && mouseY >= ddTop && mouseY <= ddTop + ddHeight) {
                dropdownScroll -= (int) verticalAmount;
                dropdownScroll = Math.max(0, Math.min(dropdownScroll, availableSkins.size() - DROPDOWN_MAX_VISIBLE));
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
        int cx = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
        int cy = panelY + 25;
        customToggle.setX(cx);            customToggle.setY(cy);
        selfToggle.setX(cx);              selfToggle.setY(cy + ROW_SPACING);
        othersToggle.setX(cx);            othersToggle.setY(cy + ROW_SPACING * 2);
        openFolderButton.setX(cx);        openFolderButton.setY(cy + ROW_SPACING * 3);
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2);
        doneButton.setY(panelY + BOX_HEIGHT - 30);
    }

    private int dropdownButtonY() { return panelY + 25 + ROW_SPACING * 4; }
    private int dropdownTop() { return dropdownButtonY() + 16 + 2; }

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

        styleButton(context, customToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, selfToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, othersToggle, guiAlpha, mouseX, mouseY);
        styleButton(context, openFolderButton, guiAlpha, mouseX, mouseY);
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        renderSkinDropdown(context, mouseX, mouseY, guiAlpha);

        String hint = "Drop any .png skin file into the skin folder.";
        int hintWidth = textRenderer.getWidth(hint);
        int hintX = panelX + (BOX_WIDTH - hintWidth) / 2;
        int hintY = panelY + BOX_HEIGHT - 50;
        context.drawTextWithShadow(textRenderer, hint, hintX, hintY, applyAlpha(0xFF888888, guiAlpha));

        matrices.popMatrix();
    }

    private void renderSkinDropdown(DrawContext context, int mouseX, int mouseY, float guiAlpha) {
        int ddX = panelX + (BOX_WIDTH - CONTROL_WIDTH) / 2;
        int ddY = dropdownButtonY();
        int ddW = CONTROL_WIDTH;
        int ddH = 16;

        boolean barHover = mouseX >= ddX && mouseX <= ddX + ddW && mouseY >= ddY && mouseY <= ddY + ddH;
        int barFill = applyAlpha(barHover ? 0xFF444444 : 0xFF333333, guiAlpha);
        context.fill(ddX, ddY, ddX + ddW, ddY + ddH, barFill);
        InventoryHudRenderer.drawChromaBorder(context, ddX - 1, ddY - 1, ddX + ddW + 1, ddY + ddH + 1, guiAlpha);

        String label = currentSkinLabel();
        String arrow = dropdownOpen ? " \u25B2" : " \u25BC";
        int maxLabelWidth = ddW - textRenderer.getWidth(arrow) - 8;
        while (textRenderer.getWidth(label) > maxLabelWidth && label.length() > 3) {
            label = label.substring(0, label.length() - 1);
        }
        if (textRenderer.getWidth(label + arrow) > ddW - 4) {
            label = label.substring(0, Math.max(0, label.length() - 3)) + "...";
        }
        String display = label + arrow;
        int textW = textRenderer.getWidth(display);
        int textX = ddX + (ddW - textW) / 2;
        int textY = ddY + (ddH - textRenderer.fontHeight) / 2;
        int chroma = applyAlpha(resolveTextColor((System.currentTimeMillis() % 4000) / 4000f), guiAlpha);
        context.drawTextWithShadow(textRenderer, display, textX, textY, chroma);

        if (dropdownOpen && !availableSkins.isEmpty()) {
            int listTop = ddY + ddH + 2;
            int visibleCount = Math.min(availableSkins.size(), DROPDOWN_MAX_VISIBLE);
            int listHeight = visibleCount * DROPDOWN_ROW_HEIGHT;

            context.fill(ddX, listTop, ddX + ddW, listTop + listHeight, applyAlpha(0xDD000000, guiAlpha));
            InventoryHudRenderer.drawChromaBorder(context, ddX - 1, listTop - 1, ddX + ddW + 1, listTop + listHeight + 1, guiAlpha);

            String selected = SkinConfig.getSelectedSkin();
            for (int i = 0; i < visibleCount; i++) {
                int idx = i + dropdownScroll;
                if (idx >= availableSkins.size()) break;
                String skinName = availableSkins.get(idx);
                int rowY = listTop + i * DROPDOWN_ROW_HEIGHT;
                boolean rowHover = mouseX >= ddX && mouseX <= ddX + ddW && mouseY >= rowY && mouseY <= rowY + DROPDOWN_ROW_HEIGHT;
                boolean isSelected = skinName.equals(selected);
                if (rowHover) {
                    context.fill(ddX + 1, rowY, ddX + ddW - 1, rowY + DROPDOWN_ROW_HEIGHT, applyAlpha(0xFF555555, guiAlpha));
                } else if (isSelected) {
                    context.fill(ddX + 1, rowY, ddX + ddW - 1, rowY + DROPDOWN_ROW_HEIGHT, applyAlpha(0xFF3A3A3A, guiAlpha));
                }

                String name = skinName;
                while (textRenderer.getWidth(name) > ddW - 10 && name.length() > 3) {
                    name = name.substring(0, name.length() - 1);
                }
                int nameColor = isSelected ? chroma : applyAlpha(0xFFCCCCCC, guiAlpha);
                context.drawTextWithShadow(textRenderer, name, ddX + 5, rowY + (DROPDOWN_ROW_HEIGHT - textRenderer.fontHeight) / 2, nameColor);
            }

            if (availableSkins.size() > DROPDOWN_MAX_VISIBLE) {
                int scrollBarH = Math.max(4, (int) ((float) visibleCount / availableSkins.size() * listHeight));
                int scrollBarY = listTop + (int) ((float) dropdownScroll / (availableSkins.size() - visibleCount) * (listHeight - scrollBarH));
                context.fill(ddX + ddW - 3, scrollBarY, ddX + ddW - 1, scrollBarY + scrollBarH, applyAlpha(0xFF888888, guiAlpha));
            }
        } else if (dropdownOpen && availableSkins.isEmpty()) {
            int listTop = ddY + ddH + 2;
            context.fill(ddX, listTop, ddX + ddW, listTop + DROPDOWN_ROW_HEIGHT, applyAlpha(0xDD000000, guiAlpha));
            InventoryHudRenderer.drawChromaBorder(context, ddX - 1, listTop - 1, ddX + ddW + 1, listTop + DROPDOWN_ROW_HEIGHT + 1, guiAlpha);
            String empty = "No skins found";
            int ew = textRenderer.getWidth(empty);
            context.drawTextWithShadow(textRenderer, empty, ddX + (ddW - ew) / 2, listTop + (DROPDOWN_ROW_HEIGHT - textRenderer.fontHeight) / 2, applyAlpha(0xFF888888, guiAlpha));
        }
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
        int chroma = resolveTextColor((System.currentTimeMillis() % 4000) / 4000f);
        context.drawTextWithShadow(textRenderer, label, tx, ty, applyAlpha(chroma, alpha));
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
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
        } catch (Exception ignored) {
        }
    }

    private int resolveTextColor(float offset) {
        return RenderConfig.getButtonTextLiveColor(offset);
    }
}

