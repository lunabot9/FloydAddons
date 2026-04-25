package floydaddons.not.dogshit.client.gui.v2.tab;

import floydaddons.not.dogshit.client.config.NickHiderConfig;
import floydaddons.not.dogshit.client.gui.NameMappingsEditorScreen;
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

/**
 * V2 GUI tab for the Neck Hider feature. Lays out a {@link ContentPane} titled
 * "Neck Hider" containing the enabled toggle, the nickname text field, and the
 * Edit / Reload Names buttons.
 */
public class NeckHiderTab implements V2Tab {

    private static final int ROW_HEIGHT = 22;
    private static final int LABEL_GAP = 8;
    private static final int FIELD_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 8;

    private final ContentPane pane;
    private final ToggleSwitch enabledToggle;
    private TextFieldWidget nickField;
    private final MetallicButton editNamesButton;
    private final MetallicButton reloadNamesButton;

    private int paneX;
    private int paneY;
    private int paneW;
    private int paneH;

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

        this.pane.add(new EnabledRow(this));
        this.pane.add(new NicknameRow(this));
        this.pane.add(new ButtonsRow(this));
    }

    @Override
    public String displayName() {
        return "Neck Hider";
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        this.paneX = x;
        this.paneY = y;
        this.paneW = w;
        this.paneH = h;
        this.pane.setBounds(x, y, w, h);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ensureField();
        pane.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return pane.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return pane.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return pane.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        return pane.mouseScrolled(mx, my, horiz, vert);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (nickField != null && nickField.isFocused()) {
            // Enter persists the value
            if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER / KP_ENTER
                NickHiderConfig.setNickname(nickField.getText());
                NickHiderConfig.save();
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
        ensureField();
        if (nickField != null) {
            nickField.setText(NickHiderConfig.getNickname());
        }
    }

    @Override
    public void onHidden() {
        persistNickname();
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
        if (nickField != null) {
            String current = nickField.getText();
            if (current != null && !current.isEmpty()
                    && !current.equals(NickHiderConfig.getNickname())) {
                NickHiderConfig.setNickname(current);
                NickHiderConfig.save();
            }
        }
    }

    // ===== Rows =====

    private static final class EnabledRow implements ContentPane.Child {
        private final NeckHiderTab parent;
        private int x, y, w;

        EnabledRow(NeckHiderTab parent) {
            this.parent = parent;
        }

        @Override
        public int getHeight() {
            return ROW_HEIGHT;
        }

        @Override
        public void layout(int x, int y, int w) {
            this.x = x;
            this.y = y;
            this.w = w;
            int toggleX = x + w - parent.enabledToggle.getWidth();
            int toggleY = y + (ROW_HEIGHT - parent.enabledToggle.getHeight()) / 2;
            parent.enabledToggle.setPos(toggleX, toggleY);
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int textY = y + (ROW_HEIGHT - tr.fontHeight) / 2 + 1;
            ctx.drawText(tr, Text.literal("Neck Hider"), x, textY, V2Theme.TEXT_PRIMARY, false);
            parent.enabledToggle.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return parent.enabledToggle.mouseClicked(mx, my, button);
        }
    }

    private static final class NicknameRow implements ContentPane.Child {
        private final NeckHiderTab parent;
        private int x, y, w;

        NicknameRow(NeckHiderTab parent) {
            this.parent = parent;
        }

        @Override
        public int getHeight() {
            return ROW_HEIGHT;
        }

        @Override
        public void layout(int x, int y, int w) {
            this.x = x;
            this.y = y;
            this.w = w;
            parent.ensureField();
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int labelW = tr.getWidth("Nickname");
            int fieldX = x + labelW + LABEL_GAP;
            int fieldW = Math.max(40, w - labelW - LABEL_GAP);
            int fieldY = y + (ROW_HEIGHT - FIELD_HEIGHT) / 2;
            parent.nickField.setX(fieldX);
            parent.nickField.setY(fieldY);
            parent.nickField.setWidth(fieldW);
            parent.nickField.setHeight(FIELD_HEIGHT);
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            parent.ensureField();
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int textY = y + (ROW_HEIGHT - tr.fontHeight) / 2 + 1;
            ctx.drawText(tr, Text.literal("Nickname"), x, textY, V2Theme.TEXT_PRIMARY, false);

            int fx = parent.nickField.getX();
            int fy = parent.nickField.getY();
            int fw = parent.nickField.getWidth();
            int fh = FIELD_HEIGHT;
            V2Theme.fillRoundedRect(ctx, fx, fy, fw, fh, 4, V2Theme.BG_SIDEBAR);
            V2Theme.drawRoundedBorder(ctx, fx, fy, fw, fh, 4,
                    parent.nickField.isFocused() ? V2Theme.OUTLINE_ACTIVE : V2Theme.METAL_MID);

            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(0f, (fh - tr.fontHeight) / 2f - 1f);
            parent.nickField.render(ctx, mouseX, mouseY, delta);
            ctx.getMatrices().popMatrix();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            parent.ensureField();
            int fx = parent.nickField.getX();
            int fy = parent.nickField.getY();
            int fw = parent.nickField.getWidth();
            int fh = FIELD_HEIGHT;
            boolean inside = mx >= fx && mx < fx + fw && my >= fy && my < fy + fh;
            boolean wasFocused = parent.nickField.isFocused();
            parent.nickField.setFocused(inside);
            if (inside) {
                return true;
            }
            if (wasFocused) {
                parent.persistNickname();
            }
            return false;
        }
    }

    private static final class ButtonsRow implements ContentPane.Child {
        private final NeckHiderTab parent;
        private int x, y, w;

        ButtonsRow(NeckHiderTab parent) {
            this.parent = parent;
        }

        @Override
        public int getHeight() {
            return BUTTON_HEIGHT;
        }

        @Override
        public void layout(int x, int y, int w) {
            this.x = x;
            this.y = y;
            this.w = w;
            int btnW = (w - BUTTON_GAP) / 2;
            parent.editNamesButton.setBounds(x, y, btnW, BUTTON_HEIGHT);
            parent.reloadNamesButton.setBounds(x + btnW + BUTTON_GAP, y, w - btnW - BUTTON_GAP, BUTTON_HEIGHT);
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            parent.editNamesButton.render(ctx, mouseX, mouseY, delta);
            parent.reloadNamesButton.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (parent.editNamesButton.mouseClicked(mx, my, button)) return true;
            return parent.reloadNamesButton.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean a = parent.editNamesButton.mouseReleased(mx, my, button);
            boolean b = parent.reloadNamesButton.mouseReleased(mx, my, button);
            return a || b;
        }
    }
}
