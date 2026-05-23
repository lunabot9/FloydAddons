package floydaddons.not.dogshit.client.gui.v2;

import floydaddons.not.dogshit.client.FloydAddonsClient;
import floydaddons.not.dogshit.client.gui.ClickGuiScreen;
import floydaddons.not.dogshit.client.gui.GuiStyleScreen;
import floydaddons.not.dogshit.client.gui.v2.tab.CosmeticTab;
import floydaddons.not.dogshit.client.gui.v2.tab.NeckHiderTab;
import floydaddons.not.dogshit.client.gui.v2.tab.QolTab;
import floydaddons.not.dogshit.client.gui.v2.tab.RenderTab;
import floydaddons.not.dogshit.client.gui.v2.tab.V2Tab;
import floydaddons.not.dogshit.client.gui.v2.widget.MetallicButton;
import floydaddons.not.dogshit.client.gui.v2.widget.NvgRoundedTextureRenderer;
import floydaddons.not.dogshit.client.gui.v2.widget.SidebarTab;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * V2 hub screen — 480x270 panel centered on the window. Hosts a sidebar (left) and
 * a content pane (right) that displays the active {@link V2Tab}.
 */
public class FloydAddonsV2Screen extends Screen {
    private static final int PANEL_W = V2Theme.CANVAS_W;
    private static final int PANEL_H = V2Theme.CANVAS_H;
    private static final int FRAME_TEX_W = 960;
    private static final int FRAME_TEX_H = 540;
    private static final float FRAME_SCALE_X = FRAME_TEX_W / (float) PANEL_W;
    private static final float FRAME_SCALE_Y = FRAME_TEX_H / (float) PANEL_H;
    private static final int ACTION_BTN_H = 17;
    private static final int CONTENT_X = V2Theme.SIDEBAR_W;
    private static final int CONTENT_W = V2Theme.CANVAS_W - V2Theme.SIDEBAR_W;
    private static final int SCREEN_MARGIN = 8;
    private static final int[] SIDEBAR_TAB_Y = {50, 72, 94, 116};
    private static final int[] SIDEBAR_ACTION_Y = {167, 192, 217};
    private static final String GITHUB_URL = "https://github.com/lunabot9/FloydAddons";

    private static final Identifier FRAME_COSMETIC = figmaFrame("cosmetic");
    private static final Identifier FRAME_COSMETIC_SKIN = figmaFrame("cosmetic_skin");
    private static final Identifier FRAME_COSMETIC_CAPE = figmaFrame("cosmetic_cape");
    private static final Identifier FRAME_COSMETIC_CAPE_POPUP = figmaFrame("cosmetic_cape_popup");
    private static final Identifier FRAME_COSMETIC_CONE = figmaFrame("cosmetic_cone");
    private static final Identifier FRAME_COSMETIC_CONE_DROPDOWN = figmaFrame("cosmetic_cone_dropdown");
    private static final Identifier FRAME_RENDER_HIDERS = figmaFrame("render_hiders");
    private static final Identifier FRAME_RENDER_XRAY = figmaFrame("render_xray");
    private static final Identifier FRAME_NECK_HIDER = figmaFrame("neck_hider");
    private static final Identifier FRAME_QOL = figmaFrame("qol");

    private final List<V2Tab> tabs = new ArrayList<>();
    private final List<SidebarTab> sidebarButtons = new ArrayList<>();
    private final List<MetallicButton> actionButtons = new ArrayList<>();

    private int panelX, panelY;
    private float panelScale = 1f;
    private int activeTabIndex = 0;
    private V2Tab lastActiveTab = null;
    private int cosmeticFrameState = 0;
    private int renderFrameState = 0;
    private Identifier currentFrame = FRAME_COSMETIC;
    private ButtonPress pressedButton = null;

    public FloydAddonsV2Screen() {
        super(Text.literal("FloydAddons"));
        tabs.add(new CosmeticTab());
        tabs.add(new RenderTab());
        tabs.add(new NeckHiderTab());
        tabs.add(new QolTab());
    }

    @Override
    protected void init() {
        super.init();
        recomputeLayout();
        setActiveTab(activeTabIndex, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // No vanilla blur — V2 has its own opaque pane backgrounds.
    }

    private void recomputeLayout() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float fitW = (sw - SCREEN_MARGIN * 2) / (float) PANEL_W;
        float fitH = (sh - SCREEN_MARGIN * 2) / (float) PANEL_H;
        this.panelScale = Math.min(1f, Math.min(fitW, fitH));
        int scaledPanelW = Math.round(PANEL_W * panelScale);
        int scaledPanelH = Math.round(PANEL_H * panelScale);
        this.panelX = (sw - scaledPanelW) / 2;
        this.panelY = (sh - scaledPanelH) / 2;

        rebuildSidebar();
        layoutContent();
    }

    private void rebuildSidebar() {
        sidebarButtons.clear();
        actionButtons.clear();

        int sidebarX = panelX;
        int tabX = sidebarX + 13;
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            SidebarTab btn = new SidebarTab(tabX, panelY + SIDEBAR_TAB_Y[i],
                    tabs.get(i).displayName(), () -> setActiveTab(idx, true));
            btn.setActive(i == activeTabIndex);
            sidebarButtons.add(btn);
        }

        int actionX = sidebarX + 13;
        actionButtons.add(new MetallicButton(actionX, panelY + SIDEBAR_ACTION_Y[0], V2Theme.SIDEBAR_TAB_WIDTH, ACTION_BTN_H, "Edit UI",
                () -> { if (client != null) client.setScreen(new GuiStyleScreen(this)); }));
        actionButtons.add(new MetallicButton(actionX, panelY + SIDEBAR_ACTION_Y[1], V2Theme.SIDEBAR_TAB_WIDTH, ACTION_BTN_H, "Click GUI",
                () -> { if (client != null) client.setScreen(new ClickGuiScreen()); }));
        actionButtons.add(new MetallicButton(actionX, panelY + SIDEBAR_ACTION_Y[2], V2Theme.SIDEBAR_TAB_WIDTH, ACTION_BTN_H, "Github",
                () -> {
                    try {
                        Util.getOperatingSystem().open(URI.create(GITHUB_URL));
                    } catch (Exception ignored) {}
                }));
    }

    private void setActiveTab(int idx, boolean userInitiated) {
        if (idx < 0 || idx >= tabs.size()) return;
        V2Tab next = tabs.get(idx);
        if (lastActiveTab != null && lastActiveTab != next) {
            lastActiveTab.onHidden();
        }
        activeTabIndex = idx;
        for (int i = 0; i < sidebarButtons.size(); i++) {
            sidebarButtons.get(i).setActive(i == activeTabIndex);
        }
        layoutContent();
        if (lastActiveTab != next) {
            next.onShown();
            lastActiveTab = next;
        }
    }

    private void layoutContent() {
        if (tabs.isEmpty()) return;
        int contentX = panelX + CONTENT_X;
        int contentY = panelY;
        int contentW = CONTENT_W;
        int contentH = PANEL_H;
        tabs.get(activeTabIndex).layout(contentX, contentY, contentW, contentH);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        recomputeLayout();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Keep the existing tab implementation alive underneath so hitboxes, drag
        // targets and accordion timing still update. The Figma PNG frame is the
        // visible layer. Clip this logic pass to the rounded panel so transparent
        // PNG corners reveal the world, not the underlay.
        if (!tabs.isEmpty()) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(panelX, panelY);
            ctx.getMatrices().scale(panelScale, panelScale);
            ctx.getMatrices().translate(-panelX, -panelY);
            try {
                renderLogicLayer(ctx, mouseX, mouseY, delta);
            } finally {
                ctx.getMatrices().popMatrix();
            }
        }

        Identifier nextFrame = resolveFrame();
        if (!currentFrame.equals(nextFrame)) {
            currentFrame = nextFrame;
        }
        drawFrame(ctx, currentFrame);
        renderPressedButton(ctx, delta);
    }

    private Identifier resolveFrame() {
        return switch (activeTabIndex) {
            case 0 -> switch (resolveCosmeticFrameState()) {
                case 1 -> FRAME_COSMETIC_SKIN;
                case 4 -> FRAME_COSMETIC_CAPE_POPUP;
                case 2 -> FRAME_COSMETIC_CAPE;
                case 5 -> FRAME_COSMETIC_CONE_DROPDOWN;
                case 3 -> FRAME_COSMETIC_CONE;
                default -> FRAME_COSMETIC;
            };
            case 1 -> resolveRenderFrameState() == 1 ? FRAME_RENDER_XRAY : FRAME_RENDER_HIDERS;
            case 2 -> FRAME_NECK_HIDER;
            case 3 -> FRAME_QOL;
            default -> FRAME_COSMETIC;
        };
    }

    private int resolveCosmeticFrameState() {
        if (!tabs.isEmpty() && tabs.get(0) instanceof CosmeticTab cosmeticTab) {
            cosmeticFrameState = cosmeticTab.figmaFrameState();
        }
        return cosmeticFrameState;
    }

    private int resolveRenderFrameState() {
        if (tabs.size() > 1 && tabs.get(1) instanceof RenderTab renderTab) {
            renderFrameState = renderTab.figmaFrameState();
        }
        return renderFrameState;
    }

    private void drawFrame(DrawContext ctx, Identifier frame) {
        if (NvgRoundedTextureRenderer.beginRoundedClip(panelX, panelY,
                PANEL_W * panelScale, PANEL_H * panelScale, V2Theme.PANE_RADIUS * panelScale)) {
            try {
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(panelX, panelY);
                ctx.getMatrices().scale(panelScale, panelScale);
                try {
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED, frame, 0, 0, 0f, 0f,
                            PANEL_W, PANEL_H, FRAME_TEX_W, FRAME_TEX_H, FRAME_TEX_W, FRAME_TEX_H);
                } finally {
                    ctx.getMatrices().popMatrix();
                }
            } finally {
                NvgRoundedTextureRenderer.endRoundedClip();
            }
            return;
        }

        int r = V2Theme.PANE_RADIUS;
        for (int row = 0; row < PANEL_H; row++) {
            int inset = V2Theme.roundedInset(r, PANEL_H, row);
            ctx.enableScissor(toScreenX(panelX + inset), toScreenY(panelY + row),
                    toScreenX(panelX + PANEL_W - inset), toScreenY(panelY + row + 1));
            try {
                ctx.getMatrices().pushMatrix();
                ctx.getMatrices().translate(panelX, panelY);
                ctx.getMatrices().scale(panelScale, panelScale);
                try {
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED, frame, 0, 0, 0f, 0f,
                            PANEL_W, PANEL_H, FRAME_TEX_W, FRAME_TEX_H, FRAME_TEX_W, FRAME_TEX_H);
                } finally {
                    ctx.getMatrices().popMatrix();
                }
            } finally {
                ctx.disableScissor();
            }
        }
    }

    private static Identifier figmaFrame(String name) {
        return Identifier.of(FloydAddonsClient.MOD_ID, "textures/gui/figma/" + name + "_hi.png");
    }

    private void renderLogicLayer(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int r = V2Theme.PANE_RADIUS;
        renderLogicScissored(ctx, mouseX, mouseY, delta, panelX + r, panelY, PANEL_W - r * 2, r);
        renderLogicScissored(ctx, mouseX, mouseY, delta, panelX, panelY + r, PANEL_W, PANEL_H - r * 2);
        renderLogicScissored(ctx, mouseX, mouseY, delta, panelX + r, panelY + PANEL_H - r, PANEL_W - r * 2, r);
    }

    private void renderLogicScissored(DrawContext ctx, int mouseX, int mouseY, float delta,
                                      int x, int y, int w, int h) {
        ctx.enableScissor(toScreenX(x), toScreenY(y), toScreenX(x + w), toScreenY(y + h));
        try {
            tabs.get(activeTabIndex).render(ctx, mouseX, mouseY, delta);
        } finally {
            ctx.disableScissor();
        }
    }

    private void renderPressedButton(DrawContext ctx, float delta) {
        if (pressedButton == null) return;
        if (!pressedButton.frame.equals(currentFrame)) {
            pressedButton = null;
            return;
        }

        float target = pressedButton.down ? 1f : 0f;
        float speed = pressedButton.down ? 0.45f : 0.35f;
        if (pressedButton.amount < target) {
            pressedButton.amount = Math.min(target, pressedButton.amount + delta * speed);
        } else if (pressedButton.amount > target) {
            pressedButton.amount = Math.max(target, pressedButton.amount - delta * speed);
        }
        if (!pressedButton.down && pressedButton.amount <= 0.01f) {
            pressedButton = null;
            return;
        }

        int offset = Math.round(2f * pressedButton.amount);
        int x = toScreenX(panelX + pressedButton.x);
        int y = toScreenY(panelY + pressedButton.y);
        int w = Math.round(pressedButton.w * panelScale);
        int h = Math.round(pressedButton.h * panelScale);
        ctx.enableScissor(x, y, x + w, y + h);
        try {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(panelX, panelY);
            ctx.getMatrices().scale(panelScale, panelScale);
            try {
                drawFrameRegion(ctx, pressedButton.frame, pressedButton.x, pressedButton.y,
                        pressedButton.w, pressedButton.h, pressedButton.x, pressedButton.y + offset);
            } finally {
                ctx.getMatrices().popMatrix();
            }
            ctx.fill(x, y, x + w, y + h,
                    (Math.round(42 * pressedButton.amount) << 24));
        } finally {
            ctx.disableScissor();
        }
    }

    private void drawFrameRegion(DrawContext ctx, Identifier frame, int srcX, int srcY, int w, int h,
                                 int dstX, int dstY) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, frame, dstX, dstY,
                srcX * FRAME_SCALE_X, srcY * FRAME_SCALE_Y,
                w, h, Math.round(w * FRAME_SCALE_X), Math.round(h * FRAME_SCALE_Y),
                FRAME_TEX_W, FRAME_TEX_H);
    }

    // ---------- Input plumbing ----------

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = toDesignX(click.x());
        double my = toDesignY(click.y());
        int button = click.button();
        if (button == 0) {
            pressedButton = findPressedButton(mx, my);
        }

        // Sidebar — buttons handle press state, action via mouseReleased.
        boolean handled = false;
        for (SidebarTab tab : sidebarButtons) {
            if (tab.mouseClicked(mx, my, button)) handled = true;
        }
        for (MetallicButton btn : actionButtons) {
            if (btn.mouseClicked(mx, my, button)) handled = true;
        }
        if (handled) return true;

        updateFigmaFrameState(mx, my);

        if (!tabs.isEmpty()) {
            if (tabs.get(activeTabIndex).mouseClicked(mx, my, button)) return true;
        }
        return super.mouseClicked(click, ignoresInput);
    }

    private ButtonPress findPressedButton(double mx, double my) {
        int rx = (int) Math.round(mx - panelX);
        int ry = (int) Math.round(my - panelY);
        if (rx < 0 || ry < 0 || rx >= PANEL_W || ry >= PANEL_H) return null;
        Identifier frameAtPress = currentFrame;

        for (int y : SIDEBAR_TAB_Y) {
            if (inside(rx, ry, 13, y, 80, 17)) return new ButtonPress(13, y, 80, 17, frameAtPress);
        }
        for (int y : SIDEBAR_ACTION_Y) {
            if (inside(rx, ry, 13, y, 80, 17)) return new ButtonPress(13, y, 80, 17, frameAtPress);
        }

        if (activeTabIndex == 0) {
            int state = resolveCosmeticFrameState();
            int capeY = state == 1 ? 180 : 93;
            int coneY = state == 1 ? 223 : state == 2 ? 204 : 136;
            if (inside(rx, ry, 121, 51, 337, 38)) return new ButtonPress(121, 51, 337, 38, frameAtPress);
            if (inside(rx, ry, 121, capeY, 337, 38)) return new ButtonPress(121, capeY, 337, 38, frameAtPress);
            if (inside(rx, ry, 121, coneY, 337, 38)) return new ButtonPress(121, coneY, 337, 38, frameAtPress);
            if ((state == 2 || state == 4) && inside(rx, ry, 342, 134, 114, 17)) {
                return new ButtonPress(342, 134, 114, 17, frameAtPress);
            }
        } else if (activeTabIndex == 1) {
            if (inside(rx, ry, 123, 46, 337, 38)) return new ButtonPress(123, 46, 337, 38, frameAtPress);
            if (inside(rx, ry, 123, 89, 337, 38)) return new ButtonPress(123, 89, 337, 38, frameAtPress);
            if (renderFrameState == 1) {
                if (inside(rx, ry, 132, 174, 85, 21)) return new ButtonPress(132, 174, 85, 21, frameAtPress);
                if (inside(rx, ry, 228, 174, 100, 21)) return new ButtonPress(228, 174, 100, 21, frameAtPress);
                if (inside(rx, ry, 123, 203, 337, 38)) return new ButtonPress(123, 203, 337, 38, frameAtPress);
            } else {
                if (inside(rx, ry, 123, 203, 337, 38)) return new ButtonPress(123, 203, 337, 38, frameAtPress);
                if (inside(rx, ry, 123, 249, 337, 21)) return new ButtonPress(123, 249, 337, 21, frameAtPress);
            }
        } else if (activeTabIndex == 2) {
            if (inside(rx, ry, 121, 51, 337, 38)) return new ButtonPress(121, 51, 337, 38, frameAtPress);
            if (inside(rx, ry, 121, 93, 337, 38)) return new ButtonPress(121, 93, 337, 38, frameAtPress);
        }

        return null;
    }

    private void updateFigmaFrameState(double mx, double my) {
        int rx = (int) Math.round(mx - panelX);
        int ry = (int) Math.round(my - panelY);
        if (rx < 0 || ry < 0 || rx >= PANEL_W || ry >= PANEL_H) return;

        if (activeTabIndex == 0) {
            if (inside(rx, ry, 121, 51, 337, 38)) {
                cosmeticFrameState = 0;
            } else if (inside(rx, ry, 121, 93, 337, 38)) {
                cosmeticFrameState = cosmeticFrameState == 2 ? 0 : 2;
            } else if (inside(rx, ry, 121, cosmeticFrameState == 2 ? 204 : 136, 337, 38)) {
                cosmeticFrameState = cosmeticFrameState == 3 ? 0 : 3;
            }
        } else if (activeTabIndex == 1) {
            if (inside(rx, ry, 123, 46, 337, 38)) {
                renderFrameState = 0;
            } else if (inside(rx, ry, 123, 89, 337, 38) || inside(rx, ry, 123, 247, 337, 23)) {
                renderFrameState = 1;
            }
        }
    }

    private static boolean inside(int x, int y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mx = toDesignX(click.x());
        double my = toDesignY(click.y());
        int button = click.button();
        if (button == 0 && pressedButton != null) {
            pressedButton.down = false;
        }

        boolean handled = false;
        for (SidebarTab tab : sidebarButtons) {
            if (tab.mouseReleased(mx, my, button)) handled = true;
        }
        for (MetallicButton btn : actionButtons) {
            if (btn.mouseReleased(mx, my, button)) handled = true;
        }
        if (!tabs.isEmpty()) {
            if (tabs.get(activeTabIndex).mouseReleased(mx, my, button)) handled = true;
        }
        return handled || super.mouseReleased(click);
    }

    private static final class ButtonPress {
        final int x;
        final int y;
        final int w;
        final int h;
        final Identifier frame;
        boolean down = true;
        float amount = 0f;

        ButtonPress(int x, int y, int w, int h, Identifier frame) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.frame = frame;
        }
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!tabs.isEmpty()) {
            if (tabs.get(activeTabIndex).mouseDragged(toDesignX(click.x()), toDesignY(click.y()), click.button(),
                    deltaX / panelScale, deltaY / panelScale)) {
                return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double mx = toDesignX(mouseX);
        double my = toDesignY(mouseY);
        if (mx >= panelX && mx < panelX + PANEL_W && my >= panelY && my < panelY + PANEL_H) {
            if (!tabs.isEmpty() && tabs.get(activeTabIndex).mouseScrolled(mx, my,
                    horizontalAmount, verticalAmount)) {
                return true;
            }
            // The visible V2 shell is a Figma texture. Until matching scrolled
            // Figma states exist, consume wheel input so the hidden interactive
            // layer cannot drift away from the displayed art.
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int toScreenX(int designX) {
        return Math.round(panelX + (designX - panelX) * panelScale);
    }

    private int toScreenY(int designY) {
        return Math.round(panelY + (designY - panelY) * panelScale);
    }

    private double toDesignX(double screenX) {
        return panelX + (screenX - panelX) / panelScale;
    }

    private double toDesignY(double screenY) {
        return panelY + (screenY - panelY) / panelScale;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!tabs.isEmpty()) {
            if (tabs.get(activeTabIndex).keyPressed(input.key(), input.scancode(), input.modifiers())) {
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!tabs.isEmpty()) {
            char ch = input.codepoint() <= 0xFFFF ? (char) input.codepoint() : 0;
            if (tabs.get(activeTabIndex).charTyped(ch, input.modifiers())) {
                return true;
            }
        }
        return super.charTyped(input);
    }
}
