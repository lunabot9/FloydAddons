package floydaddons.not.dogshit.client.gui.v2.row;

import floydaddons.not.dogshit.client.FloydAddonsConfig;
import floydaddons.not.dogshit.client.config.RenderConfig;
import floydaddons.not.dogshit.client.gui.XrayEditorScreen;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.MetallicButton;
import floydaddons.not.dogshit.client.gui.v2.widget.ToggleSwitch;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Body for the X-ray accordion. Toggle + Edit/Reload Blocks buttons.
 * Source: /tmp/fig-2x/X-ray render.png.
 */
public class XrayRow implements AccordionRow.Body {

    private static final int PAD_X = 10;
    private static final int PAD_Y = 12;
    private static final int TOGGLE_ROW_H = 17;
    private static final int BUTTON_H = 21;
    private static final int BUTTON_GAP_Y = 12;
    private static final int EDIT_BUTTON_W = 85;
    private static final int RELOAD_BUTTON_W = 100;
    private static final int BUTTON_GAP_X = 11;
    private static final int TOTAL_HEIGHT = 72;

    private final ToggleSwitch xrayToggle;
    private final MetallicButton editButton;
    private final MetallicButton reloadButton;

    public XrayRow() {
        this.xrayToggle = new ToggleSwitch(0, 0,
                RenderConfig::isXrayEnabled,
                v -> {
                    if (RenderConfig.isXrayEnabled() != v) {
                        RenderConfig.toggleXray();
                        RenderConfig.save();
                    }
                });
        this.editButton = new MetallicButton(0, 0, 1, BUTTON_H, "Edit Blocks", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(new XrayEditorScreen(mc.currentScreen));
        }).setRadius(V2Theme.SMALL_PILL_RADIUS).setTextScale(0.95f);
        this.reloadButton = new MetallicButton(0, 0, 1, BUTTON_H, "Reload Blocks",
                FloydAddonsConfig::loadXrayOpaque)
                .setRadius(V2Theme.SMALL_PILL_RADIUS).setTextScale(0.95f);
    }

    @Override
    public int getHeight() {
        return TOTAL_HEIGHT;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int innerX = x + PAD_X;
        int innerY = y + PAD_Y;

        // Row 1: "X-ray" label + toggle (left aligned, like Figma)
        int row1Y = innerY;
        int labelTextY = row1Y + (TOGGLE_ROW_H - tr.fontHeight) / 2;
        Text xrayLabel = Text.literal("X-ray").styled(s -> s.withBold(true));
        ctx.drawText(tr, xrayLabel, innerX, labelTextY, V2Theme.TEXT_PRIMARY, false);
        int labelW = tr.getWidth(xrayLabel);
        int toggleX = innerX + labelW + 8;
        int toggleY = row1Y + (TOGGLE_ROW_H - V2Theme.TOGGLE_TRACK_H) / 2;
        xrayToggle.setPos(toggleX, toggleY);
        xrayToggle.render(ctx, mouseX, mouseY, delta);

        // Row 2: two compact buttons.
        int row2Y = row1Y + TOGGLE_ROW_H + BUTTON_GAP_Y;
        editButton.setBounds(x + PAD_X, row2Y, EDIT_BUTTON_W, BUTTON_H);
        reloadButton.setBounds(x + PAD_X + EDIT_BUTTON_W + BUTTON_GAP_X, row2Y,
                RELOAD_BUTTON_W, BUTTON_H);
        editButton.render(ctx, mouseX, mouseY, delta);
        reloadButton.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (xrayToggle.mouseClicked(mx, my, button)) return true;
        if (editButton.mouseClicked(mx, my, button)) return true;
        if (reloadButton.mouseClicked(mx, my, button)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        any |= editButton.mouseReleased(mx, my, button);
        any |= reloadButton.mouseReleased(mx, my, button);
        return any;
    }
}
