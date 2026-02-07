package floydaddons.not.dogshit.client;

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
        context.fill(x, y, x + getHudWidth(), y + getHudHeight(), bg);
        drawChromaBorder(context, x - 1, y - 1, x + getHudWidth() + 1, y + getHudHeight() + 1, alpha);

        if (inv != null) {
            drawSlots(context, inv, x, y, alpha);
        }

        if (showMoveHint) {
            String hint = "Drag while Move Inventory is ON";
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
        int width = right - left;
        int height = bottom - top;
        int perimeter = width * 2 + height * 2;
        if (perimeter <= 0) return;
        int pos = 0;
        for (int x = 0; x < width; x++, pos++) {
            context.fill(left + x, top, left + x + 1, top + 1, applyAlpha(chromaColor(pos / (float) perimeter), alpha));
        }
        for (int y = 0; y < height; y++, pos++) {
            context.fill(right - 1, top + y, right, top + y + 1, applyAlpha(chromaColor(pos / (float) perimeter), alpha));
        }
        for (int x = width - 1; x >= 0; x--, pos++) {
            context.fill(left + x, bottom - 1, left + x + 1, bottom, applyAlpha(chromaColor(pos / (float) perimeter), alpha));
        }
        for (int y = height - 1; y >= 0; y--, pos++) {
            context.fill(left, top + y, left + 1, top + y + 1, applyAlpha(chromaColor(pos / (float) perimeter), alpha));
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
