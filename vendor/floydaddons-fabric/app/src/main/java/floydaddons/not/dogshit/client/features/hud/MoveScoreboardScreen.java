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
 * Move-only screen for the custom scoreboard HUD.
 */
public class MoveScoreboardScreen extends Screen {
    private final Screen parent;
    private int dragOffsetX, dragOffsetY;
    private boolean dragging = false;
    private ButtonWidget doneButton;

    public MoveScoreboardScreen(Screen parent) {
        super(Text.literal("Move Scoreboard"));
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

        int hudW = ScoreboardHudRenderer.getLastWidth();
        int hudH = ScoreboardHudRenderer.getLastHeight();
        int brX = clamp(RenderConfig.getCustomScoreboardX(), hudW, width);
        int brY = clamp(RenderConfig.getCustomScoreboardY(), hudH, height);
        RenderConfig.setCustomScoreboardX(brX);
        RenderConfig.setCustomScoreboardY(brY);
        int hudX = brX - hudW;
        int hudY = brY - hudH;

        // Draw a preview box showing the scoreboard position
        context.fill(hudX, hudY, hudX + hudW, hudY + hudH, 0x88000000);
        InventoryHudRenderer.drawChromaBorder(context, hudX - 1, hudY - 1, hudX + hudW + 1, hudY + hudH + 1, 1f);

        String label = "Scoreboard";
        int labelWidth = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer, label, hudX + (hudW - labelWidth) / 2, hudY + hudH / 2 - textRenderer.fontHeight / 2, 0xFFFFFFFF);

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
            int hudW = ScoreboardHudRenderer.getLastWidth();
            int hudH = ScoreboardHudRenderer.getLastHeight();
            int hudX = RenderConfig.getCustomScoreboardX() - hudW;
            int hudY = RenderConfig.getCustomScoreboardY() - hudH;
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
            int hudW = ScoreboardHudRenderer.getLastWidth();
            int hudH = ScoreboardHudRenderer.getLastHeight();
            int newX = clamp((int) (click.x() - dragOffsetX), 0, width - hudW);
            int newY = clamp((int) (click.y() - dragOffsetY), 0, height - hudH);
            RenderConfig.setCustomScoreboardX(newX + hudW);
            RenderConfig.setCustomScoreboardY(newY + hudH);
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
