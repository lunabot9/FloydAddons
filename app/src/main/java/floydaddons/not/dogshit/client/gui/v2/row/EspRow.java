package floydaddons.not.dogshit.client.gui.v2.row;

import floydaddons.not.dogshit.client.config.RenderConfig;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.InlineColorPicker;
import floydaddons.not.dogshit.client.gui.v2.widget.ToggleSwitch;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Body for the ESP accordion. Left column: 4 toggles. Right column: 2 inline color
 * pickers stacked vertically (Stalk Color, ESP Color). Source: /tmp/fig-2x/Mob Esp Render.png.
 */
public class EspRow implements AccordionRow.Body {

    private static final int PAD_X = 12;
    private static final int PAD_Y = 8;
    private static final int ROW_H = 16;
    private static final int LABEL_H = 10;
    private static final int PICKER_GAP = 6;

    private final List<ToggleEntry> toggles = new ArrayList<>();
    private final List<ToggleSwitch> switches = new ArrayList<>();

    private final InlineColorPicker stalkPicker;
    private final InlineColorPicker espPicker;

    public EspRow() {
        toggles.add(new ToggleEntry("Mob Esp",
                RenderConfig::isMobEspEnabled, RenderConfig::setMobEspEnabled));
        toggles.add(new ToggleEntry("Tracers",
                RenderConfig::isMobEspTracers, RenderConfig::setMobEspTracers));
        toggles.add(new ToggleEntry("Hitboxes",
                RenderConfig::isMobEspHitboxes, RenderConfig::setMobEspHitboxes));
        toggles.add(new ToggleEntry("Starred Mobs",
                RenderConfig::isMobEspStarMobs, RenderConfig::setMobEspStarMobs));
        for (ToggleEntry e : toggles) switches.add(e.toggle);

        this.stalkPicker = new InlineColorPicker(0, 0,
                RenderConfig::getStalkTracerColor,
                c -> { RenderConfig.setStalkTracerColor(c); RenderConfig.save(); });
        this.espPicker = new InlineColorPicker(0, 0,
                RenderConfig::getDefaultEspColor,
                c -> { RenderConfig.setDefaultEspColor(c); RenderConfig.save(); });
    }

    @Override
    public int getHeight() {
        // 2 pickers stacked vertically dominate the height. Each picker = ~60px,
        // plus a label (~10) and gap. Plus padding.
        int rightColumnH = (LABEL_H + 4 + stalkPicker.getHeight()) * 2 + PICKER_GAP;
        int leftColumnH = ROW_H * 4;
        return PAD_Y * 2 + Math.max(leftColumnH, rightColumnH);
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int innerX = x + PAD_X;
        int innerY = y + PAD_Y;
        int innerW = w - PAD_X * 2;

        // Right side reserves enough width for the picker
        int pickerW = stalkPicker.getWidth();
        int rightX = x + w - PAD_X - pickerW;
        int leftColW = rightX - innerX - 8;
        if (leftColW < 80) leftColW = 80;

        // Left column: 4 toggles
        for (int i = 0; i < toggles.size(); i++) {
            ToggleEntry e = toggles.get(i);
            int rowY = innerY + i * ROW_H;
            int textY = rowY + (ROW_H - tr.fontHeight) / 2;
            ctx.drawText(tr, Text.literal(e.label).styled(s -> s.withBold(true)),
                    innerX, textY, V2Theme.TEXT_PRIMARY, false);
            int toggleX = innerX + leftColW - V2Theme.TOGGLE_TRACK_W - 2;
            int toggleY = rowY + (ROW_H - V2Theme.TOGGLE_TRACK_H) / 2;
            e.toggle.setPos(toggleX, toggleY);
            e.toggle.render(ctx, mouseX, mouseY, delta);
        }

        // Right column: stacked color pickers with labels above
        int stalkLabelY = innerY;
        ctx.drawText(tr, Text.literal("Stalk Color").styled(s -> s.withBold(true)),
                rightX, stalkLabelY, V2Theme.TEXT_PRIMARY, false);
        int stalkPickerY = stalkLabelY + LABEL_H + 4;
        stalkPicker.setPos(rightX, stalkPickerY);
        stalkPicker.render(ctx, mouseX, mouseY, delta);

        int espLabelY = stalkPickerY + stalkPicker.getHeight() + PICKER_GAP;
        ctx.drawText(tr, Text.literal("ESP Color").styled(s -> s.withBold(true)),
                rightX, espLabelY, V2Theme.TEXT_PRIMARY, false);
        int espPickerY = espLabelY + LABEL_H + 4;
        espPicker.setPos(rightX, espPickerY);
        espPicker.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (ToggleSwitch t : switches) {
            if (t.mouseClicked(mx, my, button)) {
                RenderConfig.save();
                return true;
            }
        }
        if (stalkPicker.mouseClicked(mx, my, button)) return true;
        if (espPicker.mouseClicked(mx, my, button)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        any |= stalkPicker.mouseReleased(mx, my, button);
        any |= espPicker.mouseReleased(mx, my, button);
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (stalkPicker.mouseDragged(mx, my, button, dx, dy)) return true;
        if (espPicker.mouseDragged(mx, my, button, dx, dy)) return true;
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (stalkPicker.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (espPicker.keyPressed(keyCode, scanCode, modifiers)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (stalkPicker.charTyped(chr, modifiers)) return true;
        if (espPicker.charTyped(chr, modifiers)) return true;
        return false;
    }

    private static final class ToggleEntry {
        final String label;
        final ToggleSwitch toggle;

        ToggleEntry(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
            this.label = label;
            this.toggle = new ToggleSwitch(0, 0, getter, setter);
        }
    }
}
