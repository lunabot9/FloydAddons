package floydaddons.not.dogshit.client.gui.v2.row;

import floydaddons.not.dogshit.client.FloydAddonsConfig;
import floydaddons.not.dogshit.client.config.RenderConfig;
import floydaddons.not.dogshit.client.gui.XrayEditorScreen;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.MetallicButton;
import floydaddons.not.dogshit.client.gui.v2.widget.Slider;
import floydaddons.not.dogshit.client.gui.v2.widget.ToggleSwitch;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Body for the X-ray accordion. Toggle + opacity slider + Edit/Reload Blocks buttons.
 * Source: /tmp/fig-2x/X-ray render.png.
 */
public class XrayRow implements AccordionRow.Body {

    private static final int PAD_X = 12;
    private static final int PAD_Y = 8;
    private static final int ROW_GAP = 6;
    private static final int TOGGLE_ROW_H = 16;
    private static final int SLIDER_H = 14;
    private static final int BUTTON_H = 18;
    private static final int TOTAL_HEIGHT =
            PAD_Y * 2 + TOGGLE_ROW_H + ROW_GAP + SLIDER_H + ROW_GAP + BUTTON_H;

    private final ToggleSwitch xrayToggle;
    private final Slider opacitySlider;
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
        this.opacitySlider = new Slider(0, 0, 1, SLIDER_H,
                0.05, 1.0,
                RenderConfig::getXrayOpacity,
                v -> {
                    RenderConfig.setXrayOpacity((float) v);
                    RenderConfig.save();
                }).withDecimals(2);
        this.editButton = new MetallicButton(0, 0, 1, BUTTON_H, "Edit Blocks", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(new XrayEditorScreen(mc.currentScreen));
        }).setRadius(V2Theme.SMALL_PILL_RADIUS);
        this.reloadButton = new MetallicButton(0, 0, 1, BUTTON_H, "Reload Blocks",
                FloydAddonsConfig::loadXrayOpaque)
                .setRadius(V2Theme.SMALL_PILL_RADIUS);
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
        int innerW = w - PAD_X * 2;

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

        // Row 2: Opacity slider full width with label baked into the value
        int row2Y = row1Y + TOGGLE_ROW_H + ROW_GAP;
        opacitySlider.setPos(innerX, row2Y);
        opacitySlider.setSize(innerW, SLIDER_H);
        opacitySlider.render(ctx, mouseX, mouseY, delta);

        // Row 3: two buttons (Edit Blocks, Reload Blocks)
        int row3Y = row2Y + SLIDER_H + ROW_GAP;
        int btnGap = 8;
        int btnW = (innerW - btnGap) / 2;
        editButton.setBounds(innerX, row3Y, btnW, BUTTON_H);
        reloadButton.setBounds(innerX + btnW + btnGap, row3Y, btnW, BUTTON_H);
        editButton.render(ctx, mouseX, mouseY, delta);
        reloadButton.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (xrayToggle.mouseClicked(mx, my, button)) return true;
        if (opacitySlider.mouseClicked(mx, my, button)) return true;
        if (editButton.mouseClicked(mx, my, button)) return true;
        if (reloadButton.mouseClicked(mx, my, button)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        any |= opacitySlider.mouseReleased(mx, my, button);
        any |= editButton.mouseReleased(mx, my, button);
        any |= reloadButton.mouseReleased(mx, my, button);
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return opacitySlider.mouseDragged(mx, my, button, dx, dy);
    }
}
