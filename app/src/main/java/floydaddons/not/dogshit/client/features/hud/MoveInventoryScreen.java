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
 * Move-only screen for the inventory HUD (no resizing).
 */
public class MoveInventoryScreen extends Screen {
    private final Screen parent;
    private int dragOffsetX, dragOffsetY;
    private boolean dragging = false;
    private ButtonWidget doneButton;

    public MoveInventoryScreen(Screen parent) {
        super(Text.literal("Move Inventory HUD"));
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

        int hudW = InventoryHudRenderer.getHudWidth();
        int hudH = InventoryHudRenderer.getHudHeight();
        int hudX = clamp(RenderConfig.getInventoryHudX(), 0, width - hudW);
        int hudY = clamp(RenderConfig.getInventoryHudY(), 0, height - hudH);
        RenderConfig.setInventoryHudX(hudX);
        RenderConfig.setInventoryHudY(hudY);

        InventoryHudRenderer.renderInventory(context, client.player != null ? client.player.getInventory() : null, hudX, hudY, 1f, true);

        String instr = "Drag to move.";
        int tw = textRenderer.getWidth(instr);
        context.drawTextWithShadow(textRenderer, instr, (width - tw) / 2, doneButton.getY() - 18, 0xFFFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0) {
            int hudX = RenderConfig.getInventoryHudX();
            int hudY = RenderConfig.getInventoryHudY();
            int hudW = InventoryHudRenderer.getHudWidth();
            int hudH = InventoryHudRenderer.getHudHeight();
            if (mouseX >= hudX && mouseX <= hudX + hudW && mouseY >= hudY && mouseY <= hudY + hudH) {
                dragging = true;
                dragOffsetX = (int) (mouseX - hudX);
                dragOffsetY = (int) (mouseY - hudY);
                return true;
            }
        }
        return super.mouseClicked(click, ignoresInput);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging && click.button() == 0) {
            int hudW = InventoryHudRenderer.getHudWidth();
            int hudH = InventoryHudRenderer.getHudHeight();
            int newX = clamp((int) (click.x() - dragOffsetX), 0, width - hudW);
            int newY = clamp((int) (click.y() - dragOffsetY), 0, height - hudH);
            RenderConfig.setInventoryHudX(newX);
            RenderConfig.setInventoryHudY(newY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging && click.button() == 0) {
            dragging = false;
            RenderConfig.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
