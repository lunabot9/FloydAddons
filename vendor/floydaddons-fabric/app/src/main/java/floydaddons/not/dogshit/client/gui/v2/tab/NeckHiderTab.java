package floydaddons.not.dogshit.client.gui.v2.tab;

import floydaddons.not.dogshit.client.config.NickHiderConfig;
import floydaddons.not.dogshit.client.gui.NameMappingsEditorScreen;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.ContentPane;
import floydaddons.not.dogshit.client.gui.v2.widget.MetallicButton;
import floydaddons.not.dogshit.client.gui.v2.widget.ToggleSwitch;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Figma-backed Neck Hider tab. The visible frame has two accordions:
 * Neck Hider, and Other Neck Hider. The bodies retain the previous controls.
 */
public class NeckHiderTab implements V2Tab {

    private static final int HEADER_H = 38;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 8;

    private final ContentPane pane;
    private final List<AccordionRow> rows = new ArrayList<>();
    private final AccordionRow neckRow;
    private final AccordionRow otherNeckRow;

    private final ToggleSwitch enabledToggle;
    private TextFieldWidget nickField;
    private final MetallicButton editNamesButton;
    private final MetallicButton reloadNamesButton;

    public NeckHiderTab() {
        this.pane = new ContentPane(0, 0, 0, 0, "Neck Hider");

        this.enabledToggle = new ToggleSwitch(
                0, 0,
                NickHiderConfig::isEnabled,
                value -> {
                    NickHiderConfig.setEnabled(value);
                    NickHiderConfig.save();
                });

        this.editNamesButton = new MetallicButton(0, 0, 0, BUTTON_HEIGHT, "Edit Names", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                Screen current = mc.currentScreen;
                mc.setScreen(new NameMappingsEditorScreen(current));
            }
        });

        this.reloadNamesButton = new MetallicButton(0, 0, 0, BUTTON_HEIGHT, "Reload Names",
                NickHiderConfig::loadNameMappings);

        this.neckRow = new AccordionRow(0, 0, 0, HEADER_H, "Neck Hider", new NeckHiderBody(this));
        this.otherNeckRow = new AccordionRow(0, 0, 0, HEADER_H, "Other Neck Hider", new OtherNeckHiderBody(this));
        rows.add(neckRow);
        rows.add(otherNeckRow);

        for (AccordionRow row : rows) {
            pane.add(new AccordionChild(row));
        }
    }

    @Override
    public String displayName() {
        return "Neck Hider";
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        pane.setBounds(x, y, w, h);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ensureField();
        pane.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && (insideHeader(neckRow, mx, my) || insideHeader(otherNeckRow, mx, my))) {
            collapseRows();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (nickField != null && nickField.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                persistNickname();
                nickField.setFocused(false);
                return true;
            }
            if (nickField.keyPressed(new KeyInput(keyCode, scanCode, mods))) {
                return true;
            }
        }
        return pane.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (nickField != null && nickField.isFocused() && nickField.charTyped(new CharInput(chr, mods))) {
            return true;
        }
        return pane.charTyped(chr, mods);
    }

    @Override
    public void onShown() {
        collapseRows();
        ensureField();
        if (nickField != null) {
            nickField.setText(NickHiderConfig.getNickname());
        }
    }

    private static boolean insideHeader(AccordionRow row, double mx, double my) {
        return mx >= row.getX() && mx < row.getX() + row.getWidth()
                && my >= row.getY() && my < row.getY() + row.getHeaderHeight();
    }

    private void collapseRows() {
        for (AccordionRow row : rows) {
            row.setExpanded(false);
        }
    }

    @Override
    public void onHidden() {
        persistNickname();
    }

    public int frameState() {
        if (neckRow.isExpanded()) return 1;
        if (otherNeckRow.isExpanded()) return 2;
        return 0;
    }

    private void ensureField() {
        if (nickField == null) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            nickField = new TextFieldWidget(tr, 0, 0, 100, FIELD_HEIGHT, Text.literal("Nickname"));
            nickField.setText(NickHiderConfig.getNickname());
            nickField.setMaxLength(64);
            nickField.setDrawsBackground(false);
            nickField.setEditableColor(V2Theme.TEXT_PRIMARY);
        }
    }

    private void persistNickname() {
        if (nickField == null) return;
        String current = nickField.getText();
        if (current != null && !current.isEmpty() && !current.equals(NickHiderConfig.getNickname())) {
            NickHiderConfig.setNickname(current);
            NickHiderConfig.save();
        }
    }

    private static final class AccordionChild implements ContentPane.Child {
        private final AccordionRow row;

        AccordionChild(AccordionRow row) {
            this.row = row;
        }

        @Override public int getHeight() { return row.getTotalHeight(); }

        @Override
        public void layout(int x, int y, int w) {
            row.setPos(x, y);
            row.setWidth(w);
        }

        @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) { row.render(ctx, mouseX, mouseY, delta); }
        @Override public boolean mouseClicked(double mx, double my, int button) { return row.mouseClicked(mx, my, button); }
        @Override public boolean mouseReleased(double mx, double my, int button) { return row.mouseReleased(mx, my, button); }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return row.mouseDragged(mx, my, button, dx, dy); }
        @Override public boolean mouseScrolled(double mx, double my, double horiz, double vert) { return row.mouseScrolled(mx, my, horiz, vert); }
        @Override public boolean keyPressed(int k, int s, int m) { return row.keyPressed(k, s, m); }
        @Override public boolean charTyped(char c, int m) { return row.charTyped(c, m); }
    }

    private static final class NeckHiderBody implements AccordionRow.Body {
        private static final int PAD = 12;
        private static final int ROW_GAP = 8;
        private static final int LABEL_TOGGLE_GAP = 8;
        private static final int LABEL_FIELD_GAP = 10;
        private static final int HEIGHT = 72;

        private final NeckHiderTab parent;

        NeckHiderBody(NeckHiderTab parent) {
            this.parent = parent;
        }

        @Override public int getHeight() { return HEIGHT; }

        @Override
        public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
            parent.ensureField();
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;

            int row1Y = y + PAD;
            String enabledLabel = "Enabled";
            int enabledLabelW = tr.getWidth(enabledLabel);
            int enabledTextY = row1Y + (V2Theme.TOGGLE_TRACK_H - tr.fontHeight) / 2 + 1;
            ctx.drawText(tr, enabledLabel, x + PAD, enabledTextY, V2Theme.TEXT_PRIMARY, false);
            parent.enabledToggle.setPos(x + PAD + enabledLabelW + LABEL_TOGGLE_GAP, row1Y);
            parent.enabledToggle.render(ctx, mouseX, mouseY, delta);

            int row2Y = row1Y + V2Theme.TOGGLE_TRACK_H + ROW_GAP;
            String nickLabel = "Default Nick";
            int labelW = tr.getWidth(nickLabel);
            int labelY = row2Y + (FIELD_HEIGHT - tr.fontHeight) / 2 + 1;
            ctx.drawText(tr, nickLabel, x + PAD, labelY, V2Theme.TEXT_PRIMARY, false);

            int fieldX = x + PAD + labelW + LABEL_FIELD_GAP;
            int fieldW = Math.max(80, w - (fieldX - x) - PAD);
            parent.nickField.setX(fieldX);
            parent.nickField.setY(row2Y);
            parent.nickField.setWidth(fieldW);
            parent.nickField.setHeight(FIELD_HEIGHT);

            V2Theme.fillRoundedRect(ctx, fieldX, row2Y, fieldW, FIELD_HEIGHT, 4, V2Theme.BG_SIDEBAR);
            V2Theme.drawRoundedBorder(ctx, fieldX, row2Y, fieldW, FIELD_HEIGHT, 4,
                    parent.nickField.isFocused() ? V2Theme.OUTLINE_ACTIVE : V2Theme.METAL_MID);

            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(0f, (FIELD_HEIGHT - tr.fontHeight) / 2f - 1f);
            parent.nickField.render(ctx, mouseX, mouseY, delta);
            ctx.getMatrices().popMatrix();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (parent.enabledToggle.mouseClicked(mx, my, button)) return true;
            parent.ensureField();
            int fx = parent.nickField.getX();
            int fy = parent.nickField.getY();
            int fw = parent.nickField.getWidth();
            boolean inside = mx >= fx && mx < fx + fw && my >= fy && my < fy + FIELD_HEIGHT;
            boolean wasFocused = parent.nickField.isFocused();
            parent.nickField.setFocused(inside);
            if (inside) return true;
            if (wasFocused) parent.persistNickname();
            return false;
        }
    }

    private static final class OtherNeckHiderBody implements AccordionRow.Body {
        private static final int PAD = 12;
        private static final int HEIGHT = 58;

        private final NeckHiderTab parent;

        OtherNeckHiderBody(NeckHiderTab parent) {
            this.parent = parent;
        }

        @Override public int getHeight() { return HEIGHT; }

        @Override
        public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
            int btnW = (w - PAD * 2 - BUTTON_GAP) / 2;
            int btnY = y + PAD;
            parent.editNamesButton.setBounds(x + PAD, btnY, btnW, BUTTON_HEIGHT);
            parent.reloadNamesButton.setBounds(x + PAD + btnW + BUTTON_GAP, btnY,
                    w - PAD * 2 - btnW - BUTTON_GAP, BUTTON_HEIGHT);
            parent.editNamesButton.render(ctx, mouseX, mouseY, delta);
            parent.reloadNamesButton.render(ctx, mouseX, mouseY, delta);

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            String hint = NickHiderConfig.getNameMappings().size() + " mapped names";
            int hintY = btnY + BUTTON_HEIGHT + 7;
            ctx.drawText(tr, hint, x + PAD, hintY, V2Theme.TEXT_SECONDARY, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (parent.editNamesButton.mouseClicked(mx, my, button)) return true;
            return parent.reloadNamesButton.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean edit = parent.editNamesButton.mouseReleased(mx, my, button);
            boolean reload = parent.reloadNamesButton.mouseReleased(mx, my, button);
            return edit || reload;
        }
    }
}
