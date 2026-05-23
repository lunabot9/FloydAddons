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

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Renders the movable inventory HUD overlay.
 */
public final class InventoryHudRenderer implements HudRenderCallback {
    public static final int COLS = 9;
    public static final int ROWS = 3; // main inventory only
    private static final int BASE_SLOT = 18;

    private static final int BACKGROUND_COLOR = 0x88000000;
    private static boolean moveMode = false;
    private static DragType dragType = DragType.NONE;
    private static boolean prevMouseDown = false;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static int startX, startY, startWidth, startHeight;
    private static double startMouseX, startMouseY;

    private InventoryHudRenderer() {}

    public static void register() {
        HudRenderCallback.EVENT.register(new InventoryHudRenderer());
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickMove(client));
    }

    /**
     * Enter move mode from the settings UI.
     */
    public static void beginMoveMode() {
        moveMode = true;
        dragType = DragType.NONE;
        prevMouseDown = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.mouse.unlockCursor();
        }
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        if (!RenderConfig.isInventoryHudEnabled()) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x = clamp(RenderConfig.getInventoryHudX(), 0, sw - getHudWidth());
        int y = clamp(RenderConfig.getInventoryHudY(), 0, sh - getHudHeight());

        renderInventory(context, mc.player.getInventory(), x, y, 1f, moveMode);
    }

    /**
     * Shared renderer used by both HUD and preview screens.
     */
    public static void renderInventory(DrawContext context, PlayerInventory inv, int x, int y, float alpha, boolean showMoveHint) {
        int bg = applyAlpha(BACKGROUND_COLOR, alpha);
        int radius = RenderConfig.getHudCornerRadius();
        int w = getHudWidth();
        int h = getHudHeight();
        if (radius > 0) {
            fillRoundedRect(context, x, y, w, h, radius, bg);
            drawRoundedChromaBorder(context, x - 1, y - 1, w + 2, h + 2, radius, alpha);
        } else {
            context.fill(x, y, x + w, y + h, bg);
            drawChromaBorder(context, x - 1, y - 1, x + w + 1, y + h + 1, alpha);
        }

        if (inv != null) {
            drawSlots(context, inv, x, y, alpha);
        }

        if (showMoveHint) {
            String hint = "Drag to move | Scroll to resize";
            var tr = MinecraftClient.getInstance().textRenderer;
            int hintWidth = tr.getWidth(hint);
            int hx = x + (getHudWidth() - hintWidth) / 2;
            int hy = y - tr.fontHeight - 4;
            context.drawTextWithShadow(tr, hint, hx, hy, applyAlpha(0xFFFFFFFF, alpha));
        }
    }

    private static void drawSlots(DrawContext context, PlayerInventory inv, int x, int y, float alpha) {
        int[] slotOrder = buildSlotOrder();
        int slotSize = getSlotSize();
        for (int slot = 0; slot < slotOrder.length; slot++) {
            int col = slot % COLS;
            int row = slot / COLS;
        float scale = slotSize >= 16 ? 1f : slotSize / 16f; // never upscale for crispness
            float sx = x + col * slotSize + (slotSize - 16 * scale) / 2f;
            float sy = y + row * slotSize + (slotSize - 16 * scale) / 2f;
            ItemStack stack = inv.getStack(slotOrder[slot]);

            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(sx, sy);
            matrices.scale(scale, scale);
            context.drawItem(stack, 0, 0);
            matrices.popMatrix();

            if (stack.getCount() > 1) {
                var tr = MinecraftClient.getInstance().textRenderer;
                String count = String.valueOf(stack.getCount());
                // Center horizontally at the bottom of the slot (vanilla-style)
                int tx = (int) (sx + (slotSize - tr.getWidth(count)) / 2f + 1);
                int ty = (int) (sy + slotSize - tr.fontHeight - 3);
                context.drawTextWithShadow(tr, count, tx, ty, 0xFFFFFFFF);
            }
        }
    }

    private static int[] buildSlotOrder() {
        int[] order = new int[COLS * ROWS];
        int idx = 0;
        // main inventory 3 rows (slots 9-35)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                order[idx++] = 9 + row * COLS + col;
            }
        }
        return order;
    }

    public static void drawChromaBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        drawAnimatedBorder(context, left, top, right, bottom, alpha,
                RenderConfig.getGuiBorderColor(), RenderConfig.getGuiBorderFadeColor(),
                RenderConfig.isGuiBorderChromaEnabled(), RenderConfig.isGuiBorderFadeEnabled());
    }

    public static void drawButtonBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        drawAnimatedBorder(context, left, top, right, bottom, alpha,
                RenderConfig.getButtonBorderColor(), RenderConfig.getButtonBorderFadeColor(),
                RenderConfig.isButtonBorderChromaEnabled(), RenderConfig.isButtonBorderFadeEnabled());
    }

    private static final int CHROMA_SEGMENTS_PER_EDGE = 12;

    private static void drawAnimatedBorder(DrawContext context, int left, int top, int right, int bottom, float alpha,
                                           int baseColor, int fadeColor,
                                           boolean chromaEnabled, boolean fadeEnabled) {
        if (chromaEnabled) {
            drawRainbowBorder(context, left, top, right, bottom, alpha);
            return;
        }
        if (fadeEnabled) {
            drawCircularFadeBorder(context, left, top, right, bottom, alpha, baseColor, fadeColor);
            return;
        }
        drawSolidBorder(context, left, top, right, bottom, baseColor, alpha);
    }

    private static void drawRainbowBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        int width = right - left;
        int height = bottom - top;
        int perimeter = width * 2 + height * 2;
        if (perimeter <= 0) return;

        // Fixed segment count per edge (~48 total fill calls regardless of size)
        int pos = 0;
        int step;

        // Top edge
        step = Math.max(1, width / CHROMA_SEGMENTS_PER_EDGE);
        for (int x = 0; x < width; x += step, pos += step) {
            int w = Math.min(step, width - x);
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            context.fill(left + x, top, left + x + w, top + 1, c);
        }
        // Right edge
        step = Math.max(1, height / CHROMA_SEGMENTS_PER_EDGE);
        for (int y = 0; y < height; y += step, pos += step) {
            int h = Math.min(step, height - y);
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            context.fill(right - 1, top + y, right, top + y + h, c);
        }
        // Bottom edge
        step = Math.max(1, width / CHROMA_SEGMENTS_PER_EDGE);
        for (int x = width - 1; x >= 0; x -= step, pos += step) {
            int w = Math.min(step, x + 1);
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            context.fill(left + x - w + 1, bottom - 1, left + x + 1, bottom, c);
        }
        // Left edge
        step = Math.max(1, height / CHROMA_SEGMENTS_PER_EDGE);
        for (int y = height - 1; y >= 0; y -= step, pos += step) {
            int h = Math.min(step, y + 1);
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            context.fill(left, top + y - h + 1, left + 1, top + y + 1, c);
        }
    }

    /** Border fade that travels around the perimeter in a loop, similar to chroma but between two colors. */
    private static void drawCircularFadeBorder(DrawContext context, int left, int top, int right, int bottom, float alpha,
                                               int baseColor, int fadeColor) {
        int width = right - left;
        int height = bottom - top;
        int perimeter = width * 2 + height * 2;
        if (perimeter <= 0) return;

        double time = (System.currentTimeMillis() % 8000) / 8000.0;
        // One pixel per perimeter unit for maximal smoothness
        int pos = 0;
        // Top
        for (int x = 0; x < width; x++, pos++) {
            int c = fadeBorderColor(baseColor, fadeColor, pos, perimeter, time, alpha);
            context.fill(left + x, top, left + x + 1, top + 1, c);
        }
        // Right
        for (int y = 0; y < height; y++, pos++) {
            int c = fadeBorderColor(baseColor, fadeColor, pos, perimeter, time, alpha);
            context.fill(right - 1, top + y, right, top + y + 1, c);
        }
        // Bottom
        for (int x = width - 1; x >= 0; x--, pos++) {
            int c = fadeBorderColor(baseColor, fadeColor, pos, perimeter, time, alpha);
            context.fill(left + x, bottom - 1, left + x + 1, bottom, c);
        }
        // Left
        for (int y = height - 1; y >= 0; y--, pos++) {
            int c = fadeBorderColor(baseColor, fadeColor, pos, perimeter, time, alpha);
            context.fill(left, top + y, left + 1, top + y + 1, c);
        }
    }

    private static int fadeBorderColor(int base, int fade, int pos, int perimeter, double time, float alpha) {
        float t = (float) (((pos / (double) perimeter) + time) % 1.0);
        float wave = (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 2));
        int c = RenderConfig.lerpColor(base, fade, wave);
        return applyAlpha(c, alpha);
    }

    private static void drawSolidBorder(DrawContext context, int left, int top, int right, int bottom, int color, float alpha) {
        int c = applyAlpha(color, alpha);
        // Top
        context.fill(left, top, right, top + 1, c);
        // Bottom
        context.fill(left, bottom - 1, right, bottom, c);
        // Left
        context.fill(left, top, left + 1, bottom, c);
        // Right
        context.fill(right - 1, top, right, bottom, c);
    }

    /**
     * Fill a rounded rectangle by drawing rows with insets computed from a circle equation.
     */
    public static void fillRoundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        radius = Math.min(radius, Math.min(w / 2, h / 2));
        // Top rounded region
        for (int row = 0; row < radius; row++) {
            int inset = radius - (int) Math.round(Math.sqrt(radius * radius - (radius - row) * (radius - row)));
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
        // Middle
        ctx.fill(x, y + radius, x + w, y + h - radius, color);
        // Bottom rounded region
        for (int row = 0; row < radius; row++) {
            int inset = radius - (int) Math.round(Math.sqrt(radius * radius - (radius - row) * (radius - row)));
            ctx.fill(x + inset, y + h - radius + row, x + w - inset, y + h - radius + row + 1, color);
        }
    }

    /**
     * Draw a 1px rounded border using per-row insets from a circle equation.
     */
    public static void drawRoundedBorder(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0) {
            drawSolidBorder(ctx, x, y, x + w, y + h, color, 1f);
            return;
        }
        radius = Math.min(radius, Math.min(w / 2, h / 2));
        // Top and bottom rounded arcs
        for (int row = 0; row < radius; row++) {
            int inset = radius - (int) Math.round(Math.sqrt(radius * radius - (radius - row) * (radius - row)));
            // Top row pixels
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            // Bottom row pixels
            ctx.fill(x + inset, y + h - 1 - row, x + w - inset, y + h - row, color);
        }
        // Left and right straight edges
        ctx.fill(x, y + radius, x + 1, y + h - radius, color);
        ctx.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
    }

    /**
     * Draw a chroma-colored rounded border.
     */
    public static void drawRoundedChromaBorder(DrawContext ctx, int x, int y, int w, int h, int radius, float alpha) {
        if (radius <= 0) {
            drawChromaBorder(ctx, x, y, x + w, y + h, alpha);
            return;
        }
        radius = Math.min(radius, Math.min(w / 2, h / 2));
        int perimeter = w * 2 + h * 2;
        if (perimeter <= 0) return;

        // Draw each pixel along the rounded border path with chroma offset
        int pos = 0;
        // Top arc
        for (int row = 0; row < radius; row++) {
            int inset = radius - (int) Math.round(Math.sqrt(radius * radius - (radius - row) * (radius - row)));
            int lineW = w - inset * 2;
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, c);
            pos += lineW;
        }
        // Right edge
        int straightH = h - radius * 2;
        for (int row = 0; row < straightH; row++) {
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            ctx.fill(x + w - 1, y + radius + row, x + w, y + radius + row + 1, c);
            pos++;
        }
        // Bottom arc
        for (int row = radius - 1; row >= 0; row--) {
            int inset = radius - (int) Math.round(Math.sqrt(radius * radius - (radius - row) * (radius - row)));
            int lineW = w - inset * 2;
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            ctx.fill(x + inset, y + h - 1 - row, x + w - inset, y + h - row, c);
            pos += lineW;
        }
        // Left edge
        for (int row = straightH - 1; row >= 0; row--) {
            int c = applyAlpha(chromaColor(pos / (float) perimeter), alpha);
            ctx.fill(x, y + radius + row, x + 1, y + radius + row + 1, c);
            pos++;
        }
    }

    private static int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int chromaColor(float offset) {
        double time = (System.currentTimeMillis() % 4000) / 4000.0;
        float hue = (float) ((time + offset) % 1.0);
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static void tickMove(MinecraftClient mc) {
        if (!moveMode) {
            dragType = DragType.NONE;
            prevMouseDown = false;
            return;
        }
        if (mc == null || mc.player == null || mc.isPaused()) return;
        if (mc.mouse.isCursorLocked()) mc.mouse.unlockCursor();

        double scale = mc.getWindow().getScaleFactor();
        double mx = mc.mouse.getX() / scale;
        double my = mc.mouse.getY() / scale;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int hudX = clamp(RenderConfig.getInventoryHudX(), 0, sw - getHudWidth());
        int hudY = clamp(RenderConfig.getInventoryHudY(), 0, sh - getHudHeight());

        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        if (down && !prevMouseDown) {
            DragType hit = cornerHit(hudX, hudY, mx, my);
            if (hit != DragType.NONE) {
                dragType = hit;
                startMouseX = mx;
                startMouseY = my;
                startX = hudX;
                startY = hudY;
                startWidth = getHudWidth();
                startHeight = getHudHeight();
            } else if (mx >= hudX && mx <= hudX + getHudWidth() && my >= hudY && my <= hudY + getHudHeight()) {
                dragType = DragType.MOVE;
                dragOffsetX = (int) (mx - hudX);
                dragOffsetY = (int) (my - hudY);
            }
        }

        if (down && dragType != DragType.NONE) {
            handleDrag(mc, sw, sh, mx, my);
        }

        if (prevMouseDown && !down) {
            if (dragType != DragType.NONE) {
                RenderConfig.save();
            }
            dragType = DragType.NONE;
            moveMode = false;
            mc.mouse.lockCursor();
        }

        prevMouseDown = down;
    }

    private static void handleDrag(MinecraftClient mc, int sw, int sh, double mx, double my) {
        int hudWidth = getHudWidth();
        int hudHeight = getHudHeight();
        int hudX = clamp(RenderConfig.getInventoryHudX(), 0, sw - hudWidth);
        int hudY = clamp(RenderConfig.getInventoryHudY(), 0, sh - hudHeight);
        switch (dragType) {
            case MOVE -> {
                int newX = clamp((int) (mx - dragOffsetX), 0, sw - hudWidth);
                int newY = clamp((int) (my - dragOffsetY), 0, sh - hudHeight);
                RenderConfig.setInventoryHudX(newX);
                RenderConfig.setInventoryHudY(newY);
            }
            default -> resize(sw, sh, mx, my);
        }
    }

    private static void resize(int sw, int sh, double mx, double my) {
        // resizing disabled
        int minSize = (int) (BASE_SLOT * 1.0f * COLS);
        int maxSize = minSize;
        int newWidth = startWidth;
        int newHeight = startHeight;
        int newX = RenderConfig.getInventoryHudX();
        int newY = RenderConfig.getInventoryHudY();
        switch (dragType) {
            case RESIZE_NE -> {
                newWidth = (int) Math.max(minSize, Math.min(maxSize, mx - startX));
            }
            case RESIZE_SE -> {
                newWidth = (int) Math.max(minSize, Math.min(maxSize, mx - startX));
                newHeight = (int) Math.max(minSize / COLS * ROWS, Math.min(maxSize / COLS * ROWS, my - startY));
            }
            case RESIZE_SW -> {
                newWidth = (int) Math.max(minSize, Math.min(maxSize, (startX + startWidth) - mx));
                newX = startX + startWidth - newWidth;
                newHeight = (int) Math.max(minSize / COLS * ROWS, Math.min(maxSize / COLS * ROWS, my - startY));
            }
            case RESIZE_NW -> {
                newWidth = (int) Math.max(minSize, Math.min(maxSize, (startX + startWidth) - mx));
                newX = startX + startWidth - newWidth;
                newHeight = (int) Math.max(minSize / COLS * ROWS, Math.min(maxSize / COLS * ROWS, (startY + startHeight) - my));
                newY = startY + startHeight - newHeight;
            }
            default -> {}
        }
        float scaleX = newWidth / (float) (BASE_SLOT * COLS);
        float scaleY = newHeight / (float) (BASE_SLOT * ROWS);
        float newScale = clampFloat(Math.min(scaleX, scaleY), 0.6f, 1.6f);
        RenderConfig.setInventoryHudScale(newScale);
        int width = getHudWidth();
        int height = getHudHeight();
        newX = clamp(newX, 0, sw - width);
        newY = clamp(newY, 0, sh - height);
        RenderConfig.setInventoryHudX(newX);
        RenderConfig.setInventoryHudY(newY);
    }

    private static DragType cornerHit(int x, int y, double mx, double my) {
        return DragType.NONE;
    }

    public static int getSlotSize() {
        return Math.max(12, Math.round(BASE_SLOT * RenderConfig.getInventoryHudScale()));
    }

    public static int getHudWidth() {
        return COLS * getSlotSize();
    }

    public static int getHudHeight() {
        return ROWS * getSlotSize();
    }

    private static float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public enum DragType {
        NONE, MOVE, RESIZE_NW, RESIZE_NE, RESIZE_SW, RESIZE_SE
    }

    public static int baseSlot() {
        return BASE_SLOT;
    }
}
