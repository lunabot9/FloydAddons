package floydaddons.not.dogshit.client.gui.v2.row;

import floydaddons.not.dogshit.client.config.HidersConfig;
import floydaddons.not.dogshit.client.config.RenderConfig;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.ToggleSwitch;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Body for the Hiders accordion. Two-column layout of label + ToggleSwitch rows.
 * Left column: 8 toggles, right column: 5 toggles. Source: /tmp/fig-2x/Hiders.png.
 */
public class HidersRow implements AccordionRow.Body {

    private static final int ROW_H = 16;
    private static final int PAD_X = 12;
    private static final int PAD_Y = 8;
    private static final int COL_GAP = 12;

    private static final int TOTAL_HEIGHT = PAD_Y * 2 + ROW_H * 8;

    private final List<ToggleEntry> left = new ArrayList<>();
    private final List<ToggleEntry> right = new ArrayList<>();
    private final List<ToggleSwitch> switches = new ArrayList<>();

    public HidersRow() {
        // Left column
        left.add(new ToggleEntry("No Hurt Camera",
                HidersConfig::isNoHurtCameraEnabled, HidersConfig::setNoHurtCameraEnabled));
        left.add(new ToggleEntry("Remove Fire Overlay",
                HidersConfig::isRemoveFireOverlayEnabled, HidersConfig::setRemoveFireOverlayEnabled));
        left.add(new ToggleEntry("Hide Entity Fire",
                HidersConfig::isHideEntityFireEnabled, HidersConfig::setHideEntityFireEnabled));
        left.add(new ToggleEntry("Disable Arrows",
                HidersConfig::isDisableAttachedArrowsEnabled, HidersConfig::setDisableAttachedArrowsEnabled));
        left.add(new ToggleEntry("No Explosion Particles",
                HidersConfig::isRemoveExplosionParticlesEnabled, HidersConfig::setRemoveExplosionParticlesEnabled));
        left.add(new ToggleEntry("Disable Hunger Bar",
                HidersConfig::isDisableHungerBarEnabled, HidersConfig::setDisableHungerBarEnabled));
        left.add(new ToggleEntry("Hide Potion Effects",
                HidersConfig::isHidePotionEffectsEnabled, HidersConfig::setHidePotionEffectsEnabled));
        left.add(new ToggleEntry("3rd Person Crosshair",
                HidersConfig::isThirdPersonCrosshairEnabled, HidersConfig::setThirdPersonCrosshairEnabled));

        // Right column
        right.add(new ToggleEntry("Remove Falling Blocks",
                HidersConfig::isRemoveFallingBlocksEnabled, HidersConfig::setRemoveFallingBlocksEnabled));
        right.add(new ToggleEntry("Remove Tab Ping",
                HidersConfig::isRemoveTabPingEnabled, HidersConfig::setRemoveTabPingEnabled));
        right.add(new ToggleEntry("No Armor",
                () -> "ALL".equals(HidersConfig.getNoArmorMode()),
                v -> HidersConfig.setNoArmorMode(v ? "ALL" : "OFF")));
        right.add(new ToggleEntry("Server ID Hider",
                RenderConfig::isServerIdHiderEnabled, RenderConfig::setServerIdHiderEnabled));
        right.add(new ToggleEntry("Profile ID Hider",
                RenderConfig::isProfileIdHiderEnabled, RenderConfig::setProfileIdHiderEnabled));

        for (ToggleEntry e : left) switches.add(e.toggle);
        for (ToggleEntry e : right) switches.add(e.toggle);
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
        int colW = (w - PAD_X * 2 - COL_GAP) / 2;

        // Left column
        for (int i = 0; i < left.size(); i++) {
            ToggleEntry e = left.get(i);
            int rowY = innerY + i * ROW_H;
            int textY = rowY + (ROW_H - tr.fontHeight) / 2;
            ctx.drawText(tr, net.minecraft.text.Text.literal(e.label).styled(s -> s.withBold(true)),
                    innerX, textY, V2Theme.TEXT_PRIMARY, false);
            int toggleX = innerX + colW - V2Theme.TOGGLE_TRACK_W - 2;
            int toggleY = rowY + (ROW_H - V2Theme.TOGGLE_TRACK_H) / 2;
            e.toggle.setPos(toggleX, toggleY);
            e.toggle.render(ctx, mouseX, mouseY, delta);
        }

        // Right column
        int rightX = innerX + colW + COL_GAP;
        for (int i = 0; i < right.size(); i++) {
            ToggleEntry e = right.get(i);
            int rowY = innerY + i * ROW_H;
            int textY = rowY + (ROW_H - tr.fontHeight) / 2;
            ctx.drawText(tr, net.minecraft.text.Text.literal(e.label).styled(s -> s.withBold(true)),
                    rightX, textY, V2Theme.TEXT_PRIMARY, false);
            int toggleX = rightX + colW - V2Theme.TOGGLE_TRACK_W - 2;
            int toggleY = rowY + (ROW_H - V2Theme.TOGGLE_TRACK_H) / 2;
            e.toggle.setPos(toggleX, toggleY);
            e.toggle.render(ctx, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (ToggleSwitch t : switches) {
            if (t.mouseClicked(mx, my, button)) {
                HidersConfig.save();
                return true;
            }
        }
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
