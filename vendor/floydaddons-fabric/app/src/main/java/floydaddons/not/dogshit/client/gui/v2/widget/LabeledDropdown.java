package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Chevron-down dropdown matching the Custom Skin / Custom Cape frames. Closed: a small
 * rectangular button with the selected label and a 'v' chevron on the right. Open: a
 * popup list rendered ABOVE everything else.
 *
 * <p>Rendering protocol: the parent screen calls {@link #render(DrawContext, int, int, float)}
 * during its normal pass, then AFTER all other content calls {@link #renderPopup(DrawContext, int, int, float)}
 * so the popup list overlays neighboring widgets correctly.
 *
 * <p>Mouse events: the parent forwards events; this widget consumes clicks both inside
 * the closed box (to toggle) and inside the popup (to pick / dismiss).
 */
public class LabeledDropdown {
    private static final int OPTION_H = 14;
    private static final int MAX_VISIBLE_OPTIONS = 6;

    private int x, y, w, h;
    private final Supplier<List<String>> options;
    private final Supplier<String> selected;
    private final Consumer<String> onPick;

    private boolean open = false;
    private int scrollOffset = 0;

    public LabeledDropdown(int x, int y, int w, int h,
                           Supplier<List<String>> options,
                           Supplier<String> selected,
                           Consumer<String> onPick) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.options = options;
        this.selected = selected;
        this.onPick = onPick;
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public List<String> getOptions() {
        return options.get();
    }

    public boolean isHovered(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Render the closed box. The popup is drawn separately via renderPopup. */
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        V2Theme.fillRoundedRect(ctx, x, y, w, h, V2Theme.SMALL_PILL_RADIUS, V2Theme.BG_PANE);
        V2Theme.drawRoundedBorder(ctx, x, y, w, h, V2Theme.SMALL_PILL_RADIUS, V2Theme.METAL_MID);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String label = selected.get();
        if (label == null) label = "";
        // Truncate label to fit
        int reserved = 16; // chevron area
        int maxLabelW = Math.max(0, w - 12 - reserved);
        String shown = trim(label, tr, maxLabelW);

        int ty = y + (h - tr.fontHeight) / 2 + 1;
        ctx.drawText(tr, shown, x + 8, ty, V2Theme.TEXT_PRIMARY, false);

        Text chev = Text.literal(open ? "v" : "v").styled(s -> s.withBold(true));
        int chevW = tr.getWidth(chev);
        ctx.drawText(tr, chev, x + w - 8 - chevW, ty, V2Theme.TEXT_PRIMARY, false);
    }

    /** Render the popup list. Call this AFTER all other widgets so it overlays them. */
    public void renderPopup(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!open) return;
        List<String> opts = options.get();
        if (opts == null || opts.isEmpty()) return;

        int visible = Math.min(MAX_VISIBLE_OPTIONS, opts.size());
        int popupH = visible * OPTION_H + 4;
        int popupY = y + h + 2;
        int popupX = x;

        V2Theme.fillRoundedRect(ctx, popupX, popupY, w, popupH, V2Theme.SMALL_PILL_RADIUS, V2Theme.BG_PANE);
        V2Theme.drawRoundedBorder(ctx, popupX, popupY, w, popupH, V2Theme.SMALL_PILL_RADIUS, V2Theme.METAL_MID);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int firstIdx = scrollOffset;
        int lastIdx = Math.min(opts.size(), firstIdx + visible);

        for (int i = firstIdx; i < lastIdx; i++) {
            int rowY = popupY + 2 + (i - firstIdx) * OPTION_H;
            boolean hover = mouseX >= popupX + 2 && mouseX < popupX + w - 2
                    && mouseY >= rowY && mouseY < rowY + OPTION_H;
            if (hover) {
                ctx.fill(popupX + 2, rowY, popupX + w - 2, rowY + OPTION_H, V2Theme.BG_ROW_HOVER);
            }
            String opt = opts.get(i);
            String shown = trim(opt, tr, w - 12);
            ctx.drawText(tr, shown, popupX + 8, rowY + (OPTION_H - tr.fontHeight) / 2 + 1,
                    V2Theme.TEXT_PRIMARY, false);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (open) {
            // Click on options
            List<String> opts = options.get();
            if (opts != null && !opts.isEmpty()) {
                int visible = Math.min(MAX_VISIBLE_OPTIONS, opts.size());
                int popupY = y + h + 2;
                int firstIdx = scrollOffset;
                int lastIdx = Math.min(opts.size(), firstIdx + visible);
                for (int i = firstIdx; i < lastIdx; i++) {
                    int rowY = popupY + 2 + (i - firstIdx) * OPTION_H;
                    if (mx >= x + 2 && mx < x + w - 2 && my >= rowY && my < rowY + OPTION_H) {
                        if (onPick != null) onPick.accept(opts.get(i));
                        open = false;
                        return true;
                    }
                }
            }
            // Toggle if clicked back on the box
            if (isHovered(mx, my)) {
                open = false;
                return true;
            }
            // Click outside — dismiss
            open = false;
            return false;
        }
        if (isHovered(mx, my)) {
            open = true;
            scrollOffset = 0;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (!open) return false;
        List<String> opts = options.get();
        if (opts == null) return false;
        int popupY = y + h + 2;
        int popupH = Math.min(MAX_VISIBLE_OPTIONS, opts.size()) * OPTION_H + 4;
        if (mx < x || mx >= x + w || my < popupY || my >= popupY + popupH) return false;
        int max = Math.max(0, opts.size() - MAX_VISIBLE_OPTIONS);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - vert));
        return true;
    }

    private static String trim(String s, TextRenderer tr, int maxW) {
        if (s == null) return "";
        if (tr.getWidth(s) <= maxW) return s;
        String suffix = "...";
        int suffixW = tr.getWidth(suffix);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            if (tr.getWidth(sb.toString()) + suffixW > maxW) {
                if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                return sb.toString() + suffix;
            }
        }
        return s;
    }
}
