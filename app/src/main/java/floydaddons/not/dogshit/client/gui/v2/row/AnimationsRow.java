package floydaddons.not.dogshit.client.gui.v2.row;

import floydaddons.not.dogshit.client.FloydAddonsConfig;
import floydaddons.not.dogshit.client.config.AnimationConfig;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.Slider;
import floydaddons.not.dogshit.client.gui.v2.widget.ToggleSwitch;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Body for the "Animations" accordion row. Exposes every persistent field on
 * {@link AnimationConfig}: position/rotation sliders, scale, swing duration, and the
 * three boolean toggles. Heights/widths are tuned to live inside the right pane —
 * scrolling is delegated to the enclosing {@link floydaddons.not.dogshit.client.gui.v2.widget.ContentPane}.
 */
public class AnimationsRow implements AccordionRow.Body {
    private static final int ROW_H = 18;
    private static final int ROW_GAP = 4;
    private static final int PAD_TOP = 6;
    private static final int PAD_BOTTOM = 8;
    private static final int LABEL_W = 92;
    private static final int CTRL_GAP = 8;
    private static final int SIDE_PAD = 12;

    private final ToggleSwitch enabledToggle;
    private final Slider posXSlider;
    private final Slider posYSlider;
    private final Slider posZSlider;
    private final Slider rotXSlider;
    private final Slider rotYSlider;
    private final Slider rotZSlider;
    private final Slider scaleSlider;
    private final Slider swingDurationSlider;
    private final ToggleSwitch cancelReEquipToggle;
    private final ToggleSwitch hidePlayerHandToggle;
    private final ToggleSwitch classicClickToggle;

    public AnimationsRow() {
        this.enabledToggle = new ToggleSwitch(0, 0,
                AnimationConfig::isEnabled,
                v -> { AnimationConfig.setEnabled(v); FloydAddonsConfig.save(); });

        this.posXSlider = makeIntSlider(-150, 150,
                () -> AnimationConfig.getPosX(),
                v -> AnimationConfig.setPosX(v));
        this.posYSlider = makeIntSlider(-150, 150,
                () -> AnimationConfig.getPosY(),
                v -> AnimationConfig.setPosY(v));
        this.posZSlider = makeIntSlider(-150, 50,
                () -> AnimationConfig.getPosZ(),
                v -> AnimationConfig.setPosZ(v));

        this.rotXSlider = makeIntSlider(-180, 180,
                () -> AnimationConfig.getRotX(),
                v -> AnimationConfig.setRotX(v));
        this.rotYSlider = makeIntSlider(-180, 180,
                () -> AnimationConfig.getRotY(),
                v -> AnimationConfig.setRotY(v));
        this.rotZSlider = makeIntSlider(-180, 180,
                () -> AnimationConfig.getRotZ(),
                v -> AnimationConfig.setRotZ(v));

        this.scaleSlider = new Slider(0, 0, 0, ROW_H, 0.1, 2.0,
                () -> AnimationConfig.getScale(),
                v -> { AnimationConfig.setScale((float) v); FloydAddonsConfig.save(); })
                .withStep(0.05).withDecimals(2);

        this.swingDurationSlider = makeIntSlider(1, 100,
                () -> AnimationConfig.getSwingDuration(),
                v -> AnimationConfig.setSwingDuration(v));

        this.cancelReEquipToggle = new ToggleSwitch(0, 0,
                AnimationConfig::isCancelReEquip,
                v -> { AnimationConfig.setCancelReEquip(v); FloydAddonsConfig.save(); });
        this.hidePlayerHandToggle = new ToggleSwitch(0, 0,
                AnimationConfig::isHidePlayerHand,
                v -> { AnimationConfig.setHidePlayerHand(v); FloydAddonsConfig.save(); });
        this.classicClickToggle = new ToggleSwitch(0, 0,
                AnimationConfig::isClassicClick,
                v -> { AnimationConfig.setClassicClick(v); FloydAddonsConfig.save(); });
    }

    private Slider makeIntSlider(int min, int max,
                                 java.util.function.IntSupplier getter,
                                 java.util.function.IntConsumer setter) {
        return new Slider(0, 0, 0, ROW_H, min, max,
                () -> getter.getAsInt(),
                v -> { setter.accept((int) Math.round(v)); FloydAddonsConfig.save(); })
                .withStep(1.0).withDecimals(0);
    }

    @Override
    public int getHeight() {
        // 12 rows + top/bottom pad
        int rowCount = 12;
        return PAD_TOP + rowCount * ROW_H + (rowCount - 1) * ROW_GAP + PAD_BOTTOM;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
        int contentX = x + SIDE_PAD;
        int contentW = w - SIDE_PAD * 2;
        int rowY = y + PAD_TOP;

        // Row 0: Enabled (toggle on the right)
        drawLabel(ctx, "Enabled", contentX, rowY);
        positionToggle(enabledToggle, contentX, contentW, rowY);
        enabledToggle.render(ctx, mouseX, mouseY, delta);
        rowY += ROW_H + ROW_GAP;

        // Sliders
        rowY = renderLabeledSlider(ctx, "Pos X", posXSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Pos Y", posYSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Pos Z", posZSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Rot X", rotXSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Rot Y", rotYSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Rot Z", rotZSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Scale", scaleSlider, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledSlider(ctx, "Swing Duration", swingDurationSlider, contentX, contentW, rowY, mouseX, mouseY, delta);

        // Bottom toggles
        rowY = renderLabeledToggle(ctx, "Cancel Re-Equip", cancelReEquipToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        rowY = renderLabeledToggle(ctx, "Hide Player Hand", hidePlayerHandToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
        renderLabeledToggle(ctx, "Classic Click", classicClickToggle, contentX, contentW, rowY, mouseX, mouseY, delta);
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
        positionToggle(toggle, contentX, contentW, rowY);
        toggle.render(ctx, mouseX, mouseY, delta);
        return rowY + ROW_H + ROW_GAP;
    }

    private void positionToggle(ToggleSwitch toggle, int contentX, int contentW, int rowY) {
        int tx = contentX + contentW - toggle.getWidth();
        int ty = rowY + (ROW_H - toggle.getHeight()) / 2;
        toggle.setPos(tx, ty);
    }

    private void drawLabel(DrawContext ctx, String label, int x, int rowY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int ty = rowY + (ROW_H - tr.fontHeight) / 2 + 1;
        ctx.drawText(tr, label, x, ty, V2Theme.TEXT_PRIMARY, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (enabledToggle.mouseClicked(mx, my, button)) return true;
        if (posXSlider.mouseClicked(mx, my, button)) return true;
        if (posYSlider.mouseClicked(mx, my, button)) return true;
        if (posZSlider.mouseClicked(mx, my, button)) return true;
        if (rotXSlider.mouseClicked(mx, my, button)) return true;
        if (rotYSlider.mouseClicked(mx, my, button)) return true;
        if (rotZSlider.mouseClicked(mx, my, button)) return true;
        if (scaleSlider.mouseClicked(mx, my, button)) return true;
        if (swingDurationSlider.mouseClicked(mx, my, button)) return true;
        if (cancelReEquipToggle.mouseClicked(mx, my, button)) return true;
        if (hidePlayerHandToggle.mouseClicked(mx, my, button)) return true;
        if (classicClickToggle.mouseClicked(mx, my, button)) return true;
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        any |= posXSlider.mouseReleased(mx, my, button);
        any |= posYSlider.mouseReleased(mx, my, button);
        any |= posZSlider.mouseReleased(mx, my, button);
        any |= rotXSlider.mouseReleased(mx, my, button);
        any |= rotYSlider.mouseReleased(mx, my, button);
        any |= rotZSlider.mouseReleased(mx, my, button);
        any |= scaleSlider.mouseReleased(mx, my, button);
        any |= swingDurationSlider.mouseReleased(mx, my, button);
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        boolean any = false;
        any |= posXSlider.mouseDragged(mx, my, button, dx, dy);
        any |= posYSlider.mouseDragged(mx, my, button, dx, dy);
        any |= posZSlider.mouseDragged(mx, my, button, dx, dy);
        any |= rotXSlider.mouseDragged(mx, my, button, dx, dy);
        any |= rotYSlider.mouseDragged(mx, my, button, dx, dy);
        any |= rotZSlider.mouseDragged(mx, my, button, dx, dy);
        any |= scaleSlider.mouseDragged(mx, my, button, dx, dy);
        any |= swingDurationSlider.mouseDragged(mx, my, button, dx, dy);
        return any;
    }
}
