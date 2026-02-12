package floydaddons.not.dogshit.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-game editor for the X-Ray opaque blocks list.
 * Shows active blocks (removable) and nearby blocks (addable) in a scrollable panel.
 */
public class XrayEditorScreen extends Screen {
    private final Screen parent;

    private static final int BOX_WIDTH = 340;
    private static final int BOX_HEIGHT = 300;
    private static final int DRAG_BAR_HEIGHT = 18;
    private static final long FADE_DURATION_MS = 90;
    private static final int ENTRY_HEIGHT = 20;
    private static final int CONTENT_PADDING = 4;
    private static final int BUTTON_SIZE_W = 18;
    private static final int BUTTON_SIZE_H = 16;
    private static final int HEADER_HEIGHT = 14;

    private int panelX, panelY;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartPanelX, dragStartPanelY;

    private long openStartMs;
    private long closeStartMs;
    private boolean closing = false;

    private ButtonWidget doneButton;

    private List<String> activeBlocks = new ArrayList<>();
    private List<String> nearbyBlocks = new ArrayList<>();

    /** Cached button hit regions for click handling. */
    private final List<ButtonEntry> buttonEntries = new ArrayList<>();

    public XrayEditorScreen(Screen parent) {
        super(Text.literal("X-Ray Blocks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openStartMs = Util.getMeasuringTimeMs();
        closing = false;

        panelX = (width - BOX_WIDTH) / 2;
        panelY = (height - BOX_HEIGHT) / 2;

        doneButton = ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(panelX + (BOX_WIDTH - 100) / 2, panelY + BOX_HEIGHT - 30, 100, 20)
                .build();
        addDrawableChild(doneButton);

        refreshLists();
    }

    private void refreshLists() {
        activeBlocks = new ArrayList<>(RenderConfig.getXrayOpaqueBlocks());
        nearbyBlocks = suggestNearbyBlocks();
        recalcMaxScroll();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void recalcMaxScroll() {
        int totalEntries = 0;
        // "Active Blocks" header
        totalEntries++;
        totalEntries += activeBlocks.size();
        // "Nearby Blocks" header
        totalEntries++;
        totalEntries += nearbyBlocks.size();

        int contentAreaHeight = BOX_HEIGHT - 26 - 40; // title area top, done button area bottom
        int totalContentHeight = totalEntries * ENTRY_HEIGHT + CONTENT_PADDING * 2;
        maxScroll = Math.max(0, totalContentHeight - contentAreaHeight);
    }

    private List<String> suggestNearbyBlocks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return Collections.emptyList();
        Set<String> active = RenderConfig.getXrayOpaqueBlocks();
        Set<String> found = new LinkedHashSet<>();
        BlockPos center = mc.player.getBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState state = mc.world.getBlockState(pos);
                    String id = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (!active.contains(id) && !found.contains(id)) {
                        found.add(id);
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeStartMs = Util.getMeasuringTimeMs();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float openProgress = Math.min(1.0f, (Util.getMeasuringTimeMs() - openStartMs) / (float) FADE_DURATION_MS);
        float closeProgress = closing ? Math.min(1.0f, (Util.getMeasuringTimeMs() - closeStartMs) / (float) FADE_DURATION_MS) : 0f;
        if (closing && closeProgress >= 1.0f) {
            if (client != null) client.setScreen(parent);
            return;
        }
        float guiAlpha = closing ? (1.0f - closeProgress) : openProgress;
        if (guiAlpha <= 0f) return;

        float scale = 0.85f + guiAlpha * 0.15f;

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) (panelX + BOX_WIDTH / 2), (float) (panelY + BOX_HEIGHT / 2));
        matrices.scale(scale, scale);
        matrices.translate((float) -(panelX + BOX_WIDTH / 2), (float) -(panelY + BOX_HEIGHT / 2));

        int left = panelX;
        int top = panelY;
        int right = left + BOX_WIDTH;
        int bottom = top + BOX_HEIGHT;

        // Panel background
        context.fill(left, top, right, bottom, applyAlpha(0xAA000000, guiAlpha));
        InventoryHudRenderer.drawChromaBorder(context, left - 1, top - 1, right + 1, bottom + 1, guiAlpha);

        // Title
        String title = "X-Ray Blocks";
        int titleWidth = textRenderer.getWidth(title);
        int tx = panelX + (BOX_WIDTH - titleWidth) / 2;
        int ty = panelY + 6;
        context.drawTextWithShadow(textRenderer, title, tx, ty, applyAlpha(chromaColor(0f), guiAlpha));

        // Done button
        styleButton(context, doneButton, guiAlpha, mouseX, mouseY);

        // Scrollable content area
        int contentLeft = left + CONTENT_PADDING;
        int contentTop = top + 22;
        int contentRight = right - CONTENT_PADDING;
        int contentBottom = bottom - 38;
        int contentWidth = contentRight - contentLeft;

        context.enableScissor(contentLeft, contentTop, contentRight, contentBottom);

        buttonEntries.clear();
        int y = contentTop + CONTENT_PADDING - scrollOffset;
        float chromaOffset = (System.currentTimeMillis() % 4000) / 4000f;

        // --- Active Blocks header ---
        context.drawTextWithShadow(textRenderer, "Active Blocks", contentLeft + 2, y + 2,
                applyAlpha(chromaColor(chromaOffset), guiAlpha));
        y += ENTRY_HEIGHT;

        // --- Active block entries ---
        for (String id : activeBlocks) {
            int entryY = y;
            // Block item icon
            int iconX = contentLeft + 2;
            int iconY = entryY + (ENTRY_HEIGHT - 16) / 2;
            renderBlockItem(context, id, iconX, iconY);
            // Text label (shifted right for icon)
            String label = id;
            int labelX = contentLeft + 22;
            int maxLabelWidth = contentWidth - BUTTON_SIZE_W - 26;
            if (textRenderer.getWidth(label) > maxLabelWidth) {
                label = textRenderer.trimToWidth(label, maxLabelWidth - textRenderer.getWidth("...")) + "...";
            }
            context.drawTextWithShadow(textRenderer, label, labelX, entryY + (ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                    applyAlpha(0xFFCCCCCC, guiAlpha));

            // [-] button
            int btnX = contentRight - BUTTON_SIZE_W - 2;
            int btnY = entryY;
            boolean hover = mouseX >= btnX && mouseX <= btnX + BUTTON_SIZE_W
                    && mouseY >= btnY && mouseY <= btnY + BUTTON_SIZE_H
                    && mouseY >= contentTop && mouseY <= contentBottom;
            int fill = applyAlpha(hover ? 0xFF993333 : 0xFF773333, guiAlpha);
            context.fill(btnX, btnY, btnX + BUTTON_SIZE_W, btnY + BUTTON_SIZE_H, fill);
            InventoryHudRenderer.drawButtonBorder(context, btnX - 1, btnY - 1, btnX + BUTTON_SIZE_W + 1, btnY + BUTTON_SIZE_H + 1, guiAlpha);
            String btnLabel = "-";
            int btnTw = textRenderer.getWidth(btnLabel);
            context.drawTextWithShadow(textRenderer, btnLabel, btnX + (BUTTON_SIZE_W - btnTw) / 2,
                    btnY + (BUTTON_SIZE_H - textRenderer.fontHeight) / 2,
                    applyAlpha(chromaColor(chromaOffset), guiAlpha));

            // Record button region for click handling
            if (entryY + BUTTON_SIZE_H > contentTop && entryY < contentBottom) {
                buttonEntries.add(new ButtonEntry(btnX, btnY, BUTTON_SIZE_W, BUTTON_SIZE_H, id, false));
            }

            y += ENTRY_HEIGHT;
        }

        // --- Nearby Blocks header ---
        y += 4; // small gap
        context.drawTextWithShadow(textRenderer, "Nearby Blocks", contentLeft + 2, y + 2,
                applyAlpha(chromaColor(chromaOffset + 0.25f), guiAlpha));
        y += ENTRY_HEIGHT;

        // --- Nearby block entries ---
        for (String id : nearbyBlocks) {
            int entryY = y;
            // Block item icon
            int nearIconX = contentLeft + 2;
            int nearIconY = entryY + (ENTRY_HEIGHT - 16) / 2;
            renderBlockItem(context, id, nearIconX, nearIconY);
            // Text label (shifted right for icon)
            String label = id;
            int nearLabelX = contentLeft + 22;
            int maxLabelWidth = contentWidth - BUTTON_SIZE_W - 26;
            if (textRenderer.getWidth(label) > maxLabelWidth) {
                label = textRenderer.trimToWidth(label, maxLabelWidth - textRenderer.getWidth("...")) + "...";
            }
            context.drawTextWithShadow(textRenderer, label, nearLabelX, entryY + (ENTRY_HEIGHT - textRenderer.fontHeight) / 2,
                    applyAlpha(0xFFAAAAAA, guiAlpha));

            // [+] button
            int btnX = contentRight - BUTTON_SIZE_W - 2;
            int btnY = entryY;
            boolean hover = mouseX >= btnX && mouseX <= btnX + BUTTON_SIZE_W
                    && mouseY >= btnY && mouseY <= btnY + BUTTON_SIZE_H
                    && mouseY >= contentTop && mouseY <= contentBottom;
            int fill = applyAlpha(hover ? 0xFF339933 : 0xFF337733, guiAlpha);
            context.fill(btnX, btnY, btnX + BUTTON_SIZE_W, btnY + BUTTON_SIZE_H, fill);
            InventoryHudRenderer.drawButtonBorder(context, btnX - 1, btnY - 1, btnX + BUTTON_SIZE_W + 1, btnY + BUTTON_SIZE_H + 1, guiAlpha);
            String btnLabel = "+";
            int btnTw = textRenderer.getWidth(btnLabel);
            context.drawTextWithShadow(textRenderer, btnLabel, btnX + (BUTTON_SIZE_W - btnTw) / 2,
                    btnY + (BUTTON_SIZE_H - textRenderer.fontHeight) / 2,
                    applyAlpha(chromaColor(chromaOffset), guiAlpha));

            // Record button region for click handling
            if (entryY + BUTTON_SIZE_H > contentTop && entryY < contentBottom) {
                buttonEntries.add(new ButtonEntry(btnX, btnY, BUTTON_SIZE_W, BUTTON_SIZE_H, id, true));
            }

            y += ENTRY_HEIGHT;
        }

        context.disableScissor();

        matrices.popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();

        if (click.button() == 0) {
            // Drag bar
            if (mx >= panelX && mx <= panelX + BOX_WIDTH
                    && my >= panelY && my <= panelY + DRAG_BAR_HEIGHT) {
                dragging = true;
                dragStartMouseX = mx;
                dragStartMouseY = my;
                dragStartPanelX = panelX;
                dragStartPanelY = panelY;
                return true;
            }

            // Content area buttons
            int contentTop = panelY + 22;
            int contentBottom = panelY + BOX_HEIGHT - 38;
            for (ButtonEntry entry : buttonEntries) {
                if (mx >= entry.x && mx <= entry.x + entry.w
                        && my >= entry.y && my <= entry.y + entry.h
                        && my >= contentTop && my <= contentBottom) {
                    if (entry.isAdd) {
                        RenderConfig.addXrayOpaqueBlock(entry.blockId);
                        FloydAddonsConfig.saveXrayOpaque();
                    } else {
                        RenderConfig.removeXrayOpaqueBlock(entry.blockId);
                        FloydAddonsConfig.saveXrayOpaque();
                    }
                    refreshLists();
                    return true;
                }
            }
        }

        return super.mouseClicked(click, ignoresInput);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging && click.button() == 0) {
            int newX = dragStartPanelX + (int) (click.x() - dragStartMouseX);
            int newY = dragStartPanelY + (int) (click.y() - dragStartMouseY);
            newX = Math.max(0, Math.min(newX, width - BOX_WIDTH));
            newY = Math.max(0, Math.min(newY, height - BOX_HEIGHT));
            panelX = newX;
            panelY = newY;
            repositionWidgets();
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * ENTRY_HEIGHT * 3);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    private void repositionWidgets() {
        doneButton.setX(panelX + (BOX_WIDTH - 100) / 2);
        doneButton.setY(panelY + BOX_HEIGHT - 30);
    }

    private void styleButton(DrawContext context, ButtonWidget button, float alpha, int mouseX, int mouseY) {
        int bx = button.getX(), by = button.getY(), bw = button.getWidth(), bh = button.getHeight();
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        context.fill(bx, by, bx + bw, by + bh, applyAlpha(hover ? 0xFF666666 : 0xFF555555, alpha));
        InventoryHudRenderer.drawButtonBorder(context, bx - 1, by - 1, bx + bw + 1, by + bh + 1, alpha);
        String label = button.getMessage().getString();
        int tw = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer, label, bx + (bw - tw) / 2, by + (bh - textRenderer.fontHeight) / 2,
                applyAlpha(chromaColor((System.currentTimeMillis() % 4000) / 4000f), alpha));
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private void renderBlockItem(DrawContext context, String blockId, int x, int y) {
        try {
            Block block = Registries.BLOCK.get(Identifier.of(blockId));
            ItemStack stack = new ItemStack(block.asItem());
            if (!stack.isEmpty()) {
                context.drawItem(stack, x, y);
            }
        } catch (Exception ignored) {}
    }

    private int chromaColor(float offset) {
        if (!RenderConfig.isButtonTextChromaEnabled()) return RenderConfig.getButtonTextColor();
        return RenderConfig.chromaColor(offset);
    }

    /** Represents a clickable button entry in the scrollable content area. */
    private static class ButtonEntry {
        final int x, y, w, h;
        final String blockId;
        final boolean isAdd;

        ButtonEntry(int x, int y, int w, int h, String blockId, boolean isAdd) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.blockId = blockId;
            this.isAdd = isAdd;
        }
    }
}
