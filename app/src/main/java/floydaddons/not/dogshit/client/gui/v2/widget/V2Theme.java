package floydaddons.not.dogshit.client.gui.v2.widget;

import net.minecraft.client.gui.DrawContext;

/**
 * Centralized design tokens for the V2 GUI rebuild. All values are sampled from the
 * Figma frames in {@code /tmp/fig-2x/}. The V2 GUI does not use chroma rainbow accents;
 * everything renders flat metallic/grayscale per the Figma spec.
 */
public final class V2Theme {
    private V2Theme() {}

    // === Layout (canvas is 480x270; sidebar ~33%) ===
    /** Source: Main - QoL.png. */
    public static final int CANVAS_W = 480;
    /** Source: Main - QoL.png. */
    public static final int CANVAS_H = 270;
    /** Source: Main - QoL.png — left sidebar rail width. */
    public static final int SIDEBAR_W = 160;
    /** Source: Main - QoL.png — sidebar tab pill width. */
    public static final int SIDEBAR_TAB_WIDTH = 130;
    /** Source: Main - QoL.png — sidebar tab pill height. */
    public static final int SIDEBAR_TAB_HEIGHT = 22;
    /** Source: Main - QoL.png — vertical gap between sidebar tabs. */
    public static final int SIDEBAR_TAB_GAP = 6;

    /** Source: Main - QoL.png — outer panel corner radius. */
    public static final int PANE_RADIUS = 12;
    /** Source: Hiders.png — accordion / button pill corner radius. */
    public static final int BUTTON_RADIUS = 10;
    /** Source: X-ray render.png — small outline pill (Edit Blocks etc.) radius. */
    public static final int SMALL_PILL_RADIUS = 9;
    /** Source: Cone Hat.png — slider track corner radius. */
    public static final int SLIDER_RADIUS = 7;

    /** Source: Hiders.png — toggle track default size. */
    public static final int TOGGLE_TRACK_W = 24;
    /** Source: Hiders.png — toggle track default size. */
    public static final int TOGGLE_TRACK_H = 12;

    // === Backgrounds ===
    /** Source: Main - QoL.png left rail — very dark near-black. */
    public static final int BG_SIDEBAR    = 0xFF1A1A1A;
    /** Source: Main - QoL.png right pane — slightly lighter dark. */
    public static final int BG_PANE       = 0xFF2E2E2E;
    /** Source: Hiders.png expanded body — between pane and row. */
    public static final int BG_BODY       = 0xFF3A3A3A;

    // === Metallic gradient stops (used by every pill/button/header/slider) ===
    /** Source: Hiders.png — top highlight stop of the metallic gradient. */
    public static final int METAL_TOP     = 0xFFC8C8C8;
    /** Source: Hiders.png — middle stop of the metallic gradient. */
    public static final int METAL_MID     = 0xFF8E8E8E;
    /** Source: Hiders.png — bottom stop of the metallic gradient. */
    public static final int METAL_BOT     = 0xFF6A6A6A;

    /** Convenience: row default mid-color when a flat fill is needed. */
    public static final int BG_ROW_DEFAULT = METAL_MID;
    /** Slightly lighter than default — hover state. */
    public static final int BG_ROW_HOVER   = 0xFFA0A0A0;
    /** Active accordion row body fill (less contrast than the header). */
    public static final int BG_ROW_ACTIVE  = 0xFF707070;

    // === Outlines / dividers ===
    /** Source: Main - QoL.png — selected sidebar tab outline (white). */
    public static final int OUTLINE_ACTIVE = 0xFFFFFFFF;
    /** Source: Main - QoL.png — divider under section title. */
    public static final int DIVIDER        = 0xFFB8B8B8;
    /** Source: X-ray render.png — outlined pill button border. */
    public static final int OUTLINE_BUTTON = 0xFF202020;

    // === Text ===
    /** Source: every frame — main label color. */
    public static final int TEXT_PRIMARY   = 0xFFFFFFFF;
    /** Source: Cosmetic Custom Skin.png — hint "Drop any .png file..." */
    public static final int TEXT_SECONDARY = 0xFFB8B8B8;
    /** Color used for chevron glyphs ('>', 'v') on accordion headers. */
    public static final int TEXT_CHEVRON   = 0xFF1A1A1A;

    // === Toggle ===
    /** Source: Hiders.png — track when off (dark). */
    public static final int TOGGLE_OFF_TRACK = 0xFF555555;
    /** Source: Hiders.png — track when on (lighter, still grayscale). */
    public static final int TOGGLE_ON_TRACK  = 0xFFC8C8C8;
    /** Source: Hiders.png — knob color (light gray dot). */
    public static final int TOGGLE_KNOB      = 0xFFE6E6E6;

    /**
     * Paint the metallic gradient pill background used on every nav button, accordion
     * header and slider track. Implemented as a vertical 3-stop gradient (top→mid→bot)
     * rendered as horizontal strips inside a rounded rect.
     *
     * <p>If radius {@code <= 0}, falls back to a sharp rectangle.
     */
    public static void drawMetallicGradient(DrawContext ctx, int x, int y, int w, int h, int radius) {
        drawMetallicGradient(ctx, x, y, w, h, radius, METAL_TOP, METAL_MID, METAL_BOT);
    }

    /** Variant that accepts custom gradient stops — used for hover/active tints. */
    public static void drawMetallicGradient(DrawContext ctx, int x, int y, int w, int h, int radius,
                                            int top, int mid, int bot) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w / 2, h / 2)));
        int half = h / 2;
        for (int row = 0; row < h; row++) {
            int color;
            if (row < half) {
                color = lerpColor(top, mid, row / (float) Math.max(1, half));
            } else {
                color = lerpColor(mid, bot, (row - half) / (float) Math.max(1, h - half));
            }
            int inset = roundedInset(r, h, row);
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /** Compute per-row inset for a rounded rect of the given height. */
    public static int roundedInset(int radius, int height, int row) {
        if (radius <= 0) return 0;
        if (row < radius) {
            int dy = radius - row;
            return radius - (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
        }
        if (row >= height - radius) {
            int dy = row - (height - radius - 1);
            return radius - (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
        }
        return 0;
    }

    /** Filled rounded rectangle helper (mirrors InventoryHudRenderer.fillRoundedRect). */
    public static void fillRoundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w / 2, h / 2)));
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int row = 0; row < h; row++) {
            int inset = roundedInset(r, h, row);
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /** 1px rounded border. */
    public static void drawRoundedBorder(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w / 2, h / 2)));
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + 1, color);
            ctx.fill(x, y + h - 1, x + w, y + h, color);
            ctx.fill(x, y, x + 1, y + h, color);
            ctx.fill(x + w - 1, y, x + w, y + h, color);
            return;
        }
        for (int row = 0; row < h; row++) {
            int inset = roundedInset(r, h, row);
            if (row < r || row >= h - r) {
                ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            } else {
                ctx.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
                ctx.fill(x + w - inset - 1, y + row, x + w - inset, y + row + 1, color);
            }
        }
    }

    /** Linear-interpolate two ARGB colors. */
    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int oa = (int) (aa + (ba - aa) * t);
        int or = (int) (ar + (br - ar) * t);
        int og = (int) (ag + (bg - ag) * t);
        int ob = (int) (ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /** Multiply alpha channel by [0..1] factor. */
    public static int applyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
