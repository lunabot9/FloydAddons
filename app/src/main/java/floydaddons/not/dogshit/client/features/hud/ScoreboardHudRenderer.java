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

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;

/**
 * Custom scoreboard sidebar renderer with chroma styling.
 */
public final class ScoreboardHudRenderer implements HudRenderCallback {

    private static final Comparator<ScoreboardEntry> ENTRY_COMPARATOR =
            Comparator.comparing(ScoreboardEntry::value).reversed()
                    .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private static final int BACKGROUND_COLOR = 0x88000000;
    private static final int LINE_HEIGHT = 9;
    private static final int PADDING = 3;
    private static final int TITLE_PADDING = 2;

    private static int lastWidth = 100;
    private static int lastHeight = 50;

    /** Set by ScoreboardSidebarMixin when vanilla would have rendered. */
    private static boolean vanillaWouldRender = false;

    private ScoreboardHudRenderer() {}

    /** Called from the mixin when vanilla scoreboard rendering reaches the draw method. */
    public static void markVanillaWouldRender() {
        vanillaWouldRender = true;
    }

    public static void register() {
        HudRenderCallback.EVENT.register(new ScoreboardHudRenderer());
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!RenderConfig.isCustomScoreboardEnabled()) return;

        // Only render if the vanilla scoreboard would have rendered this frame.
        // The flag is set by ScoreboardSidebarMixin when the actual draw method
        // is reached — if another mod (or F1/hudHidden) cancelled earlier, the
        // flag stays false and we skip rendering too.
        if (!vanillaWouldRender) return;
        vanillaWouldRender = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();

        // Resolve sidebar objective (team-specific first, then generic SIDEBAR)
        ScoreboardObjective objective = null;
        Team team = scoreboard.getScoreHolderTeam(mc.player.getNameForScoreboard());
        if (team != null) {
            ScoreboardDisplaySlot teamSlot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (teamSlot != null) {
                objective = scoreboard.getObjectiveForSlot(teamSlot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        }
        if (objective == null) return;

        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);
        TextRenderer textRenderer = mc.textRenderer;

        // Collect entries
        List<EntryLine> lines = scoreboard.getScoreboardEntries(objective).stream()
                .filter(e -> !e.hidden())
                .sorted(ENTRY_COMPARATOR)
                .limit(15)
                .map(entry -> {
                    Team entryTeam = scoreboard.getScoreHolderTeam(entry.owner());
                    Text name = Team.decorateName(entryTeam, entry.name());
                    Text score = entry.formatted(numberFormat);
                    return new EntryLine(name, score, textRenderer.getWidth(score));
                })
                .toList();

        if (lines.isEmpty()) return;

        // Remove last line (hypixel.net) — we replace it with "FloydAddons"
        if (lines.size() > 1) {
            lines = lines.subList(0, lines.size() - 1);
        }

        Text title = objective.getDisplayName();
        int titleWidth = textRenderer.getWidth(title);
        String footerText = "FloydAddons";
        int footerWidth = textRenderer.getWidth(footerText);

        // Calculate dimensions
        int colonWidth = textRenderer.getWidth(": ");
        int maxLineWidth = Math.max(titleWidth, footerWidth);
        for (EntryLine line : lines) {
            int lineWidth = textRenderer.getWidth(line.name) + (line.scoreWidth > 0 ? colonWidth + line.scoreWidth : 0);
            if (lineWidth > maxLineWidth) maxLineWidth = lineWidth;
        }

        int boxWidth = maxLineWidth + PADDING * 2;
        int titleBarHeight = LINE_HEIGHT + TITLE_PADDING * 2;
        int footerBarHeight = LINE_HEIGHT + TITLE_PADDING * 2;
        int contentHeight = lines.size() * LINE_HEIGHT;
        int boxHeight = titleBarHeight + contentHeight + footerBarHeight;

        lastWidth = boxWidth;
        lastHeight = boxHeight;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // Default to right-aligned, vertically centered (like vanilla)
        if (RenderConfig.getCustomScoreboardX() < 0 || RenderConfig.getCustomScoreboardY() < 0) {
            RenderConfig.setCustomScoreboardX(sw - boxWidth - 1);
            RenderConfig.setCustomScoreboardY(sh / 2 - boxHeight / 2);
        }

        int x = clamp(RenderConfig.getCustomScoreboardX(), 0, sw - boxWidth);
        int y = clamp(RenderConfig.getCustomScoreboardY(), 0, sh - boxHeight);

        // Background
        int radius = RenderConfig.getHudCornerRadius();
        if (radius > 0) {
            InventoryHudRenderer.fillRoundedRect(context, x, y, boxWidth, boxHeight, radius, BACKGROUND_COLOR);
            InventoryHudRenderer.drawRoundedChromaBorder(context, x - 1, y - 1, boxWidth + 2, boxHeight + 2, radius, 1f);
        } else {
            context.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND_COLOR);
            InventoryHudRenderer.drawChromaBorder(context, x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, 1f);
        }

        // Title (chroma colored, centered)
        int titleX = x + (boxWidth - titleWidth) / 2;
        int titleY = y + TITLE_PADDING;
        context.drawTextWithShadow(textRenderer, title, titleX, titleY, uiTextColor(0f));

        // Entry lines (directly after title, no divider)
        int lineY = y + titleBarHeight;
        int scoreRight = x + boxWidth - PADDING;
        for (EntryLine line : lines) {
            context.drawText(textRenderer, line.name, x + PADDING, lineY, 0xFFFFFFFF, false);
            if (line.scoreWidth > 0) {
                context.drawText(textRenderer, line.score, scoreRight - line.scoreWidth, lineY, 0xFFFFFFFF, false);
            }
            lineY += LINE_HEIGHT;
        }

        // Footer: "FloydAddons" in chroma, centered
        int footerX = x + (boxWidth - footerWidth) / 2;
        int footerY = lineY + TITLE_PADDING;
        context.drawTextWithShadow(textRenderer, footerText, footerX, footerY, uiTextColor(0.5f));
    }

    public static int getLastWidth() { return lastWidth; }
    public static int getLastHeight() { return lastHeight; }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static int uiTextColor(float offset) {
        if (!RenderConfig.isButtonTextChromaEnabled()) return RenderConfig.getButtonTextColor();
        double time = (System.currentTimeMillis() % 4000) / 4000.0;
        float hue = (float) ((time + offset) % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private record EntryLine(Text name, Text score, int scoreWidth) {}
}
