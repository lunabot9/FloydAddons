package floydaddons.not.dogshit.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple client GUI that mirrors the provided mockup.
 */
public class FloydAddonsScreen extends Screen {
    // Target size 480x270
    private static final float SCALE_X = 480f / 850f;
    private static final float SCALE_Y = 270f / 500f;
    private static final int PANEL_WIDTH = 480;
    private static final int PANEL_HEIGHT = 270;
    private static final int BORDER_COLOR = 0xFFD20078;
    private static final int BORDER_THICKNESS = 1;
    private static final long FADE_DURATION_MS = 90;

    private static final Identifier BACKGROUND = Identifier.of(FloydAddonsClient.MOD_ID, "textures/gui/floyd_user.png");
    private static final Identifier BG_STRETCH = Identifier.of(FloydAddonsClient.MOD_ID, "textures/gui/floyd_user.png"); // background.png equivalent

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;
    private final List<LabelItem> labels = new ArrayList<>();
    private static final String LINK_TEXT = "https://github.com/lunabot9/FloydAddons";
    private static final String LINK_HEADER = "Check out FloydAddons on GitHub!";
    private int linkX, linkY, linkW, linkH;
    private int panelX;
    private int panelY;
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;
    private static final int DRAG_BAR_HEIGHT = 22;
    private static final int STYLE_BTN_W = 64;
    private static final int STYLE_BTN_H = 18;
    private int styleBtnX;
    private int styleBtnY;
    private int hudBtnX;
    private int hudBtnY;
    private int clickGuiBtnX;
    private int clickGuiBtnY;
    private static final int CLICKGUI_BTN_W = 74;
    private static final int CLICKGUI_BTN_H = 18;

    private int scaleX(int value) { return Math.round(value * SCALE_X); }
    private int scaleY(int value) { return Math.round(value * SCALE_Y); }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public FloydAddonsScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        // Always reset to default center when opening
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        labels.clear();
        // positions filled at render-time; placeholder zeroes
        labels.add(new LabelItem("Cosmetic", 0, 0));
        labels.add(new LabelItem("Render", 0, 0));
        labels.add(new LabelItem("Neck Hider", 0, 0));
        labels.add(new LabelItem("Camera", 0, 0));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float openProgress = Math.min(1.0f, (Util.getMeasuringTimeMs() - openStartMs) / (float) FADE_DURATION_MS);
        float closeProgress = closing ? Math.min(1.0f, (Util.getMeasuringTimeMs() - closeStartMs) / (float) FADE_DURATION_MS) : 0f;
        if (closing && closeProgress >= 1.0f) {
            super.close();
            return;
        }
        float guiAlpha = closing ? (1.0f - closeProgress) : openProgress;
        if (guiAlpha <= 0f) {
            return;
        }

        int alphaBorderColor = applyAlpha(BORDER_COLOR, guiAlpha);

        float scale = lerp(0.85f, 1.0f, guiAlpha);
        int centerX = panelX + PANEL_WIDTH / 2;
        int centerY = panelY + PANEL_HEIGHT / 2;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) centerX, (float) centerY);
        matrices.scale(scale, scale);
        matrices.translate((float) -centerX, (float) -centerY);

        int left = panelX;
        int top = panelY;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        // Border forced chroma gradient (seamless around perimeter)
        drawChromaBorderSyncedForced(context, left - BORDER_THICKNESS, top - BORDER_THICKNESS, right + BORDER_THICKNESS, bottom + BORDER_THICKNESS, guiAlpha);

        // Background image stretched
        drawStretchBackground(context, left, top, PANEL_WIDTH, PANEL_HEIGHT, guiAlpha);

        // Style button in top-right corner
        styleBtnX = right - STYLE_BTN_W - 6;
        styleBtnY = top + 4;
        drawStyleButton(context, guiAlpha, mouseX, mouseY);

        // HUD button in top-left corner
        hudBtnX = left + 6;
        hudBtnY = top + 4;
        drawHudButton(context, guiAlpha, mouseX, mouseY);

        // ClickGUI button below HUD button
        clickGuiBtnX = left + 6;
        clickGuiBtnY = top + 4 + STYLE_BTN_H + 4;
        drawClickGuiButton(context, guiAlpha, mouseX, mouseY);

        // Title scaled up 75% with chroma gradient right-to-left
        float titleScale = 1.75f;
        matrices.pushMatrix();
        matrices.scale(titleScale, titleScale);
        String title = "FloydAddons";
        int titleX = (int) (centerX / titleScale);
        int titleY = (int) ((top + scaleY(20)) / titleScale);
        int titleWidth = textRenderer.getWidth(title);
        int xCursor = titleX - titleWidth / 2;
        for (int i = 0; i < title.length(); i++) {
            String ch = title.substring(i, i + 1);
            int charWidth = textRenderer.getWidth(ch);
            float offset = 1f - ((xCursor - (titleX - titleWidth / 2f)) / titleWidth);
            int color = applyAlpha(chromaColorSynced(offset), guiAlpha);
            context.drawTextWithShadow(textRenderer, ch, xCursor, titleY, color);
            xCursor += charWidth;
        }
        super.render(context, mouseX, mouseY, delta);

        matrices.popMatrix();

        // Labels centered in GUI, larger than title, drawn without panel scaling
        float labelScale = 2.52f; // 20% larger than previous
        int fontH = textRenderer.fontHeight;
        int maxW = 0;
        for (LabelItem label : labels) {
            maxW = Math.max(maxW, textRenderer.getWidth(label.text));
        }
        int spacing = (int) (fontH * labelScale + scaleY(20)); // even more spacing
        int groupHeight = labels.size() * spacing;
        int baseY = top + (PANEL_HEIGHT - groupHeight) / 2;

        for (int i = 0; i < labels.size(); i++) {
            LabelItem label = labels.get(i);
            int labelWidth = textRenderer.getWidth(label.text);
            // Center each label individually within the panel width
            int drawX = left + (int) ((PANEL_WIDTH - labelWidth * labelScale) / 2f);
            int drawY = baseY + i * spacing;
            label.x = drawX;
            label.y = drawY;
            label.width = (int) (labelWidth * labelScale);
            label.height = (int) (fontH * labelScale);

            boolean hover = mouseX >= drawX && mouseX <= drawX + labelWidth * labelScale
                    && mouseY >= drawY && mouseY <= drawY + fontH * labelScale;
            float target = hover ? 1f : 0f;
            label.alpha += (target - label.alpha) * 0.2f;

            int base = applyAlpha(0xFFFFFFFF, guiAlpha);
            int hoverColor = applyAlpha(chromaColorSynced(0f), guiAlpha);
            int blended = blendColors(base, hoverColor, label.alpha);

            var m2 = context.getMatrices();
            m2.pushMatrix();
            m2.translate((float) drawX, (float) drawY);
            m2.scale(labelScale, labelScale);
            context.drawTextWithShadow(textRenderer, label.text, 0, 0, blended);
            if (label.alpha > 0.01f) {
                int underlineY = fontH + 1; // move line down ~2px
                int lineColor = blendColors(base, hoverColor, label.alpha);
                int lineWidth = labelWidth;
                int fadeLen = Math.max(1, (int) (lineWidth * 0.35f)); // smoother/longer fade
                for (int x = 0; x < lineWidth; x++) {
                    float tEdge = x < fadeLen ? (x / (float) fadeLen)
                            : (x > lineWidth - fadeLen ? (lineWidth - x) / (float) fadeLen : 1f);
                    tEdge = Math.max(0f, Math.min(1f, tEdge));
                    int c = applyAlpha(lineColor, label.alpha * tEdge);
                    context.fill(x, underlineY, x + 1, underlineY + 1, c);
                }
            }
            m2.popMatrix();
        }

        // GitHub link and header at bottom center
        int headerWidth = textRenderer.getWidth(LINK_HEADER);
        int linkTextWidth = textRenderer.getWidth(LINK_TEXT);
        int baseCenterX = left + PANEL_WIDTH / 2;
        int headerY = bottom - scaleY(34) - 15; // move up 15px
        int linkYLocal = headerY + textRenderer.fontHeight + 10 - 5; // gap, then move link up 5px

        int headerX = baseCenterX - headerWidth / 2;
        int linkXLocal = baseCenterX - linkTextWidth / 2;

        int headerColor = applyAlpha(0xFFFFFFFF, guiAlpha);
        context.drawTextWithShadow(textRenderer, LINK_HEADER, headerX, headerY, headerColor);

        // Link always chroma
        int linkColor = applyAlpha(chromaColorSynced(0f), guiAlpha);
        context.drawTextWithShadow(textRenderer, LINK_TEXT, linkXLocal, linkYLocal, linkColor);

        // remember link bounds for click
        linkX = linkXLocal;
        linkY = linkYLocal;
        linkW = linkTextWidth;
        linkH = fontH;

        if (closing && closeProgress >= 1.0f) {
            super.close();
        }
    }

    @Override
    public void close() {
        if (closing) {
            return;
        }
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Prevent vanilla background blur/fade.
    }

    public boolean mouseClicked(Click click, boolean ignoresInput) {
        if (handleClick(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, ignoresInput);
    }

    private boolean handleClick(double mx, double my, int button) {
        if (button != 0) return false;

        // Style button
        if (mx >= styleBtnX && mx <= styleBtnX + STYLE_BTN_W && my >= styleBtnY && my <= styleBtnY + STYLE_BTN_H) {
            if (client != null) {
                client.setScreen(new GuiStyleScreen(this));
                return true;
            }
        }

        // HUD button
        if (mx >= hudBtnX && mx <= hudBtnX + STYLE_BTN_W && my >= hudBtnY && my <= hudBtnY + STYLE_BTN_H) {
            if (client != null) {
                client.setScreen(new HudScreen(this));
                return true;
            }
        }

        // ClickGUI button
        if (mx >= clickGuiBtnX && mx <= clickGuiBtnX + CLICKGUI_BTN_W && my >= clickGuiBtnY && my <= clickGuiBtnY + CLICKGUI_BTN_H) {
            if (client != null) {
                client.setScreen(new ClickGuiScreen());
                return true;
            }
        }

        // start dragging when clicking the top bar
        if (mx >= panelX && mx <= panelX + PANEL_WIDTH && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
            dragging = true;
            dragStartMouseX = mx;
            dragStartMouseY = my;
            dragStartPanelX = panelX;
            dragStartPanelY = panelY;
            return true;
        }

        // GitHub link
        if (mx >= linkX && mx <= linkX + linkW && my >= linkY && my <= linkY + linkH) {
            try {
                Util.getOperatingSystem().open(new URI(LINK_TEXT));
                return true;
            } catch (Exception ignored) {
            }
        }

        // Labels open sub-screens
        for (LabelItem label : labels) {
            if (mx >= label.x && mx <= label.x + label.width && my >= label.y && my <= label.y + label.height) {
                if ("Neck Hider".equals(label.text)) {
                    if (client != null) {
                        client.setScreen(new NickHiderScreen(this));
                        return true;
                    }
                } else if ("Render".equals(label.text)) {
                    if (client != null) {
                        client.setScreen(new RenderScreen(this));
                        return true;
                    }} else if ("Cosmetic".equals(label.text)) {
                    if (client != null) {
                        client.setScreen(new SkinScreen(this));
                        return true;
                    }
                } else if ("Camera".equals(label.text)) {
                    if (client != null) {
                        client.setScreen(new CameraScreen(this));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging && click.button() == 0) {
            double mouseX = click.x();
            double mouseY = click.y();
            int newX = dragStartPanelX + (int) (mouseX - dragStartMouseX);
            int newY = dragStartPanelY + (int) (mouseY - dragStartMouseY);
            newX = Math.max(0, Math.min(newX, width - PANEL_WIDTH));
            newY = Math.max(0, Math.min(newY, height - PANEL_HEIGHT));
            panelX = newX;
            panelY = newY;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // Synced chroma used for all hover states (faster cycle ~2.5s, always on)
    private int chromaColorSynced(float offset) {
        double time = (System.currentTimeMillis() % 2500) / 2500.0;
        float hue = (float) ((time + offset) % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private void drawChromaBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        // kept for compatibility; falls back to synced forced version
        drawChromaBorderSyncedForced(context, left, top, right, bottom, alpha);
    }

    private static final int CHROMA_SEGMENTS_PER_EDGE = 16;

    private void drawChromaBorderSyncedForced(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        float a = clamp01(alpha);
        int width = right - left;
        int height = bottom - top;
        int perimeter = width * 2 + height * 2;
        if (perimeter <= 0) return;

        int pos = 0;
        int step;

        step = Math.max(1, width / CHROMA_SEGMENTS_PER_EDGE);
        for (int x = 0; x < width; x += step, pos += step) {
            int w = Math.min(step, width - x);
            int c = applyAlpha(chromaColorSynced(pos / (float) perimeter), a);
            context.fill(left + x, top, left + x + w, top + BORDER_THICKNESS, c);
        }
        step = Math.max(1, height / CHROMA_SEGMENTS_PER_EDGE);
        for (int y = 0; y < height; y += step, pos += step) {
            int h = Math.min(step, height - y);
            int c = applyAlpha(chromaColorSynced(pos / (float) perimeter), a);
            context.fill(right - BORDER_THICKNESS, top + y, right, top + y + h, c);
        }
        step = Math.max(1, width / CHROMA_SEGMENTS_PER_EDGE);
        for (int x = width - 1; x >= 0; x -= step, pos += step) {
            int w = Math.min(step, x + 1);
            int c = applyAlpha(chromaColorSynced(pos / (float) perimeter), a);
            context.fill(left + x - w + 1, bottom - BORDER_THICKNESS, left + x + 1, bottom, c);
        }
        step = Math.max(1, height / CHROMA_SEGMENTS_PER_EDGE);
        for (int y = height - 1; y >= 0; y -= step, pos += step) {
            int h = Math.min(step, y + 1);
            int c = applyAlpha(chromaColorSynced(pos / (float) perimeter), a);
            context.fill(left, top + y - h + 1, left + BORDER_THICKNESS, top + y + 1, c);
        }
    }

    private void drawHudButton(DrawContext context, float alpha, int mouseX, int mouseY) {
        int bx = hudBtnX;
        int by = hudBtnY;
        boolean hover = mouseX >= bx && mouseX <= bx + STYLE_BTN_W && mouseY >= by && mouseY <= by + STYLE_BTN_H;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF4A4A4A, alpha);
        context.fill(bx, by, bx + STYLE_BTN_W, by + STYLE_BTN_H, fill);
        drawChromaBorderSyncedForced(context, bx - 1, by - 1, bx + STYLE_BTN_W + 1, by + STYLE_BTN_H + 1, alpha);

        String label = "Edit HUD";
        int tw = textRenderer.getWidth(label);
        int tx = bx + (STYLE_BTN_W - tw) / 2;
        int ty = by + (STYLE_BTN_H - textRenderer.fontHeight) / 2;
        int color = applyAlpha(chromaColorSynced(0f), alpha);
        context.drawTextWithShadow(textRenderer, label, tx, ty, color);
    }

    private void drawClickGuiButton(DrawContext context, float alpha, int mouseX, int mouseY) {
        int bx = clickGuiBtnX;
        int by = clickGuiBtnY;
        boolean hover = mouseX >= bx && mouseX <= bx + CLICKGUI_BTN_W && mouseY >= by && mouseY <= by + CLICKGUI_BTN_H;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF4A4A4A, alpha);
        context.fill(bx, by, bx + CLICKGUI_BTN_W, by + CLICKGUI_BTN_H, fill);
        drawChromaBorderSyncedForced(context, bx - 1, by - 1, bx + CLICKGUI_BTN_W + 1, by + CLICKGUI_BTN_H + 1, alpha);

        String label = "ClickGUI";
        int tw = textRenderer.getWidth(label);
        int tx = bx + (CLICKGUI_BTN_W - tw) / 2;
        int ty = by + (CLICKGUI_BTN_H - textRenderer.fontHeight) / 2;
        int color = applyAlpha(chromaColorSynced(0f), alpha);
        context.drawTextWithShadow(textRenderer, label, tx, ty, color);
    }

    private void drawStyleButton(DrawContext context, float alpha, int mouseX, int mouseY) {
        int bx = styleBtnX;
        int by = styleBtnY;
        boolean hover = mouseX >= bx && mouseX <= bx + STYLE_BTN_W && mouseY >= by && mouseY <= by + STYLE_BTN_H;
        int fill = applyAlpha(hover ? 0xFF666666 : 0xFF4A4A4A, alpha);
        context.fill(bx, by, bx + STYLE_BTN_W, by + STYLE_BTN_H, fill);
        // Border always chroma
        drawChromaBorderSyncedForced(context, bx - 1, by - 1, bx + STYLE_BTN_W + 1, by + STYLE_BTN_H + 1, alpha);

        // Text label
        String label = "Edit UI";
        int tw = textRenderer.getWidth(label);
        int tx = bx + (STYLE_BTN_W - tw) / 2;
        int ty = by + (STYLE_BTN_H - textRenderer.fontHeight) / 2;
        int color = applyAlpha(chromaColorSynced(0f), alpha);
        context.drawTextWithShadow(textRenderer, label, tx, ty, color);
    }

    private void drawStretchBackground(DrawContext context, int x, int y, int w, int h, float alpha01) {
        float a01 = clamp01(alpha01);
        int a = (int) (a01 * 255.0f);
        if (a <= 0) return;

        // Opaque base so semi-transparent pixels at the image edges don't let the game world peek through.
        context.fill(x, y, x + w, y + h, (a << 24)); // black with gui alpha

        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        context.drawTexture(pipeline, BG_STRETCH, x, y, 0, 0, w, h, w, h);

        // No overlay; keep background crisp
    }

    private int blendColors(int from, int to, float t) {
        t = clamp01(t);
        int a1 = (from >>> 24) & 0xFF, r1 = (from >>> 16) & 0xFF, g1 = (from >>> 8) & 0xFF, b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF, r2 = (to >>> 16) & 0xFF, g2 = (to >>> 8) & 0xFF, b2 = to & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawWhiteBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        int c = applyAlpha(0xFFFFFFFF, alpha);
        context.fill(left, top, right, top + BORDER_THICKNESS, c);
        context.fill(left, bottom - BORDER_THICKNESS, right, bottom, c);
        context.fill(left, top, left + BORDER_THICKNESS, bottom, c);
        context.fill(right - BORDER_THICKNESS, top, right, bottom, c);
    }

    private static class LabelItem {
        final String text;
        int x;
        int y;
        int width;
        int height;
        float alpha = 0f;

        LabelItem(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}


