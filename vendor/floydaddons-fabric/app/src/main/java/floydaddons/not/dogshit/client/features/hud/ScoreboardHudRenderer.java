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
import floydaddons.not.dogshit.client.utils.ui.rendering.NVGRenderer;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.ArrayList;
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
    private static final float HUD_TEXT_SIZE = 9f;

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

        // Collect entries — convert to OrderedText and apply nick/server-ID
        // replacements directly.  We must work at the OrderedText level because
        // Team.decorateName() embeds the entry owner string (which may contain
        // literal § chars for uniqueness) between prefix and suffix.  Text-level
        // getString() preserves those § chars and breaks pattern matching, but
        // asOrderedText() runs through TextVisitFactory.visitFormatted() which
        // consumes them, yielding clean text for replacement.
        List<EntryLine> lines = scoreboard.getScoreboardEntries(objective).stream()
                .filter(e -> !e.hidden())
                .sorted(ENTRY_COMPARATOR)
                .limit(15)
                .map(entry -> {
                    Team entryTeam = scoreboard.getScoreHolderTeam(entry.owner());
                    Text rawName = Team.decorateName(entryTeam, entry.name());
                    OrderedText name = NickTextUtil.replaceAllNamesInOrderedText(rawName.asOrderedText());
                    Text score = entry.formatted(numberFormat);
                    return new EntryLine(orderedTextToString(name), score.getString(), textWidth(score.getString()));
                })
                .toList();

        if (lines.isEmpty()) return;

        // Remove last line (hypixel.net) — we replace it with "FloydAddons"
        if (lines.size() > 1) {
            lines = lines.subList(0, lines.size() - 1);
        }

        String title = objective.getDisplayName().getString();
        int titleWidth = textWidth(title);
        String footerText = "FloydAddons";
        int footerWidth = textWidth(footerText);

        // Calculate dimensions
        int colonWidth = textWidth(": ");
        int maxLineWidth = Math.max(titleWidth, footerWidth);
        for (EntryLine line : lines) {
            int lineWidth = textWidth(line.name) + (line.scoreWidth > 0 ? colonWidth + line.scoreWidth : 0);
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

        // Position stores bottom-right corner so scoreboard stays right-anchored
        if (RenderConfig.getCustomScoreboardX() < 0 || RenderConfig.getCustomScoreboardY() < 0) {
            RenderConfig.setCustomScoreboardX(sw - 1);
            RenderConfig.setCustomScoreboardY(sh / 2 + boxHeight / 2);
        }

        int brX = clamp(RenderConfig.getCustomScoreboardX(), boxWidth, sw);
        int brY = clamp(RenderConfig.getCustomScoreboardY(), boxHeight, sh);
        int x = brX - boxWidth;
        int y = brY - boxHeight;

        // Background
        int radius = RenderConfig.getHudCornerRadius();
        if (radius > 0) {
            InventoryHudRenderer.fillRoundedRect(context, x, y, boxWidth, boxHeight, radius, BACKGROUND_COLOR);
            InventoryHudRenderer.drawRoundedChromaBorder(context, x - 1, y - 1, boxWidth + 2, boxHeight + 2, radius, 1f);
        } else {
            context.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND_COLOR);
            InventoryHudRenderer.drawChromaBorder(context, x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, 1f);
        }

        int lineY = y + titleBarHeight;
        renderHudText(title, footerText, titleWidth, footerWidth, x, y, boxWidth, lineY, lines);
    }

    private static void renderHudText(String title, String footerText, int titleWidth, int footerWidth,
                                      int boxX, int boxY, int boxWidth, int lineY, List<EntryLine> lines) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        NVGRenderer.INSTANCE.beginFrame(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        try {
            int titleDrawX = boxX + (boxWidth - titleWidth) / 2;
            int titleDrawY = boxY + TITLE_PADDING;
            drawHudText(title, titleDrawX, titleDrawY, uiTextColor(0f));

            int currentY = lineY;
            int scoreRight = boxX + boxWidth - PADDING;
            for (EntryLine line : lines) {
                drawHudText(line.name(), boxX + PADDING, currentY, 0xFFFFFFFF);
                if (line.scoreWidth() > 0) {
                    drawHudText(line.score(), scoreRight - line.scoreWidth(), currentY, 0xFFFFFFFF);
                }
                currentY += LINE_HEIGHT;
            }

            int footerDrawX = boxX + (boxWidth - footerWidth) / 2;
            drawHudText(footerText, footerDrawX, currentY + TITLE_PADDING, uiTextColor(0.5f));
        } finally {
            NVGRenderer.INSTANCE.endFrame();
        }
    }

    public static int getLastWidth() { return lastWidth; }
    public static int getLastHeight() { return lastHeight; }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static int uiTextColor(float offset) {
        return RenderConfig.getButtonTextLiveColor(offset);
    }

    private static int textWidth(String text) {
        return Math.round(NVGRenderer.INSTANCE.textWidth(text, HUD_TEXT_SIZE, NVGRenderer.INSTANCE.getDefaultFont()));
    }

    private static void drawHudText(String text, float x, float y, int color) {
        NVGRenderer.INSTANCE.text(text, x, y, HUD_TEXT_SIZE, color, NVGRenderer.INSTANCE.getDefaultFont());
    }

    private static String orderedTextToString(OrderedText text) {
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    private record EntryLine(String name, String score, int scoreWidth) {}
}
