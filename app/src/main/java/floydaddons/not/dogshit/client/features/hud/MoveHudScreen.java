package floydaddons.not.dogshit.client.features.hud;
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

/**
 * Unified move screen for both the inventory HUD and scoreboard HUD.
 * Both elements are visible and independently draggable at the same time.
 */
public class MoveHudScreen extends Screen {
    private final Screen parent;
    private int dragOffsetX, dragOffsetY;
    private DragTarget dragTarget = DragTarget.NONE;
    private ButtonWidget doneButton;

    private enum DragTarget { NONE, INVENTORY, SCOREBOARD }

    public MoveHudScreen(Screen parent) {
        super(Text.literal("Move HUD Elements"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnW = 100;
        int btnH = 20;
        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions((width - btnW) / 2, height - btnH - 12, btnW, btnH)
                .build();
        addDrawableChild(doneButton);
    }

    @Override
    public void close() {
        RenderConfig.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        // --- Inventory HUD preview ---
        int invW = InventoryHudRenderer.getHudWidth();
        int invH = InventoryHudRenderer.getHudHeight();
        int invX = clamp(RenderConfig.getInventoryHudX(), 0, width - invW);
        int invY = clamp(RenderConfig.getInventoryHudY(), 0, height - invH);
        RenderConfig.setInventoryHudX(invX);
        RenderConfig.setInventoryHudY(invY);

        InventoryHudRenderer.renderInventory(context, client.player != null ? client.player.getInventory() : null, invX, invY, 1f, true);

        // --- Scoreboard HUD preview ---
        int sbW = ScoreboardHudRenderer.getLastWidth();
        int sbH = ScoreboardHudRenderer.getLastHeight();
        int sbX = clamp(RenderConfig.getCustomScoreboardX(), 0, width - sbW);
        int sbY = clamp(RenderConfig.getCustomScoreboardY(), 0, height - sbH);
        RenderConfig.setCustomScoreboardX(sbX);
        RenderConfig.setCustomScoreboardY(sbY);

        context.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0x88000000);
        InventoryHudRenderer.drawChromaBorder(context, sbX - 1, sbY - 1, sbX + sbW + 1, sbY + sbH + 1, 1f);

        String label = "Scoreboard";
        int labelWidth = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer, label, sbX + (sbW - labelWidth) / 2, sbY + sbH / 2 - textRenderer.fontHeight / 2, 0xFFFFFFFF);

        // --- Instruction text ---
        String instr = "Drag to move | Scroll to resize";
        int tw = textRenderer.getWidth(instr);
        context.drawTextWithShadow(textRenderer, instr, (width - tw) / 2, doneButton.getY() - 18, 0xFFFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0) {
            // Check inventory HUD bounds first
            int invX = RenderConfig.getInventoryHudX();
            int invY = RenderConfig.getInventoryHudY();
            int invW = InventoryHudRenderer.getHudWidth();
            int invH = InventoryHudRenderer.getHudHeight();
            if (mouseX >= invX && mouseX <= invX + invW && mouseY >= invY && mouseY <= invY + invH) {
                dragTarget = DragTarget.INVENTORY;
                dragOffsetX = (int) (mouseX - invX);
                dragOffsetY = (int) (mouseY - invY);
                return true;
            }

            // Check scoreboard HUD bounds
            int sbX = RenderConfig.getCustomScoreboardX();
            int sbY = RenderConfig.getCustomScoreboardY();
            int sbW = ScoreboardHudRenderer.getLastWidth();
            int sbH = ScoreboardHudRenderer.getLastHeight();
            if (mouseX >= sbX && mouseX <= sbX + sbW && mouseY >= sbY && mouseY <= sbY + sbH) {
                dragTarget = DragTarget.SCOREBOARD;
                dragOffsetX = (int) (mouseX - sbX);
                dragOffsetY = (int) (mouseY - sbY);
                return true;
            }
        }
        return super.mouseClicked(click, ignoresInput);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragTarget != DragTarget.NONE && click.button() == 0) {
            if (dragTarget == DragTarget.INVENTORY) {
                int hudW = InventoryHudRenderer.getHudWidth();
                int hudH = InventoryHudRenderer.getHudHeight();
                int newX = clamp((int) (click.x() - dragOffsetX), 0, width - hudW);
                int newY = clamp((int) (click.y() - dragOffsetY), 0, height - hudH);
                RenderConfig.setInventoryHudX(newX);
                RenderConfig.setInventoryHudY(newY);
            } else if (dragTarget == DragTarget.SCOREBOARD) {
                int hudW = ScoreboardHudRenderer.getLastWidth();
                int hudH = ScoreboardHudRenderer.getLastHeight();
                int newX = clamp((int) (click.x() - dragOffsetX), 0, width - hudW);
                int newY = clamp((int) (click.y() - dragOffsetY), 0, height - hudH);
                RenderConfig.setCustomScoreboardX(newX);
                RenderConfig.setCustomScoreboardY(newY);
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragTarget != DragTarget.NONE && click.button() == 0) {
            dragTarget = DragTarget.NONE;
            RenderConfig.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Check if cursor is over inventory HUD
        int invX = RenderConfig.getInventoryHudX();
        int invY = RenderConfig.getInventoryHudY();
        int invW = InventoryHudRenderer.getHudWidth();
        int invH = InventoryHudRenderer.getHudHeight();
        if (mouseX >= invX && mouseX <= invX + invW && mouseY >= invY && mouseY <= invY + invH) {
            float newScale = RenderConfig.getInventoryHudScale() + (float) verticalAmount * 0.1f;
            RenderConfig.setInventoryHudScale(newScale);
            RenderConfig.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
