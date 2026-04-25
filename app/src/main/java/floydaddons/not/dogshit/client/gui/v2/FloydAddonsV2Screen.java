package floydaddons.not.dogshit.client.gui.v2;

import floydaddons.not.dogshit.client.FloydAddonsClient;
import floydaddons.not.dogshit.client.features.hud.HudScreen;
import floydaddons.not.dogshit.client.gui.ClickGuiScreen;
import floydaddons.not.dogshit.client.gui.GuiStyleScreen;
import floydaddons.not.dogshit.client.gui.v2.tab.CosmeticTab;
import floydaddons.not.dogshit.client.gui.v2.tab.NeckHiderTab;
import floydaddons.not.dogshit.client.gui.v2.tab.QolTab;
import floydaddons.not.dogshit.client.gui.v2.tab.RenderTab;
import floydaddons.not.dogshit.client.gui.v2.tab.V2Tab;
import floydaddons.not.dogshit.client.gui.v2.widget.MetallicButton;
import floydaddons.not.dogshit.client.gui.v2.widget.SidebarTab;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
    private static final int SIDEBAR_W = V2Theme.SIDEBAR_W;

    private static final int SIDEBAR_PAD_X = 14;
    private static final int SIDEBAR_PAD_TOP = 10;
    private static final int AVATAR_SIZE = 28;
    private static final int DIVIDER_PAD_X = 12;
    private static final int ACTION_BTN_H = 18;
    private static final String GITHUB_URL = "https://github.com/lunabot9/FloydAddons";

    private static final Identifier AVATAR_TEX = Identifier.of(FloydAddonsClient.MOD_ID, "textures/gui/floyd_user.png");

    private final List<V2Tab> tabs = new ArrayList<>();
    private final List<SidebarTab> sidebarButtons = new ArrayList<>();
    private final List<MetallicButton> actionButtons = new ArrayList<>();

    private int panelX, panelY;
    private int activeTabIndex = 0;
    private V2Tab lastActiveTab = null;

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
        this.panelX = (sw - PANEL_W) / 2;
        this.panelY = (sh - PANEL_H) / 2;

        rebuildSidebar();
        layoutContent();
    }

    private void rebuildSidebar() {
        sidebarButtons.clear();
        actionButtons.clear();

        int sidebarX = panelX;
        int avatarY = panelY + SIDEBAR_PAD_TOP;

        // First divider sits below the avatar block
        int firstDividerY = avatarY + AVATAR_SIZE + 8;

        // Sidebar tabs
        int tabsTopY = firstDividerY + 8;
        int tabX = sidebarX + (SIDEBAR_W - V2Theme.SIDEBAR_TAB_WIDTH) / 2;
        int yCursor = tabsTopY;
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            SidebarTab btn = new SidebarTab(tabX, yCursor, tabs.get(i).displayName(), () -> setActiveTab(idx, true));
            btn.setActive(i == activeTabIndex);
            sidebarButtons.add(btn);
            yCursor += V2Theme.SIDEBAR_TAB_HEIGHT + V2Theme.SIDEBAR_TAB_GAP;
        }

        // Second divider below tabs
        int secondDividerY = yCursor + 4;

        // Action buttons (Edit UI / Edit HUD / Click GUI / Github)
        int actionsTopY = secondDividerY + 8;
        int actionX = sidebarX + (SIDEBAR_W - V2Theme.SIDEBAR_TAB_WIDTH) / 2;
        int aY = actionsTopY;
        actionButtons.add(new MetallicButton(actionX, aY, V2Theme.SIDEBAR_TAB_WIDTH, ACTION_BTN_H, "Edit UI",
                () -> { if (client != null) client.setScreen(new GuiStyleScreen(this)); }));
        aY += ACTION_BTN_H + V2Theme.SIDEBAR_TAB_GAP;
        actionButtons.add(new MetallicButton(actionX, aY, V2Theme.SIDEBAR_TAB_WIDTH, ACTION_BTN_H, "Edit HUD",
                () -> { if (client != null) client.setScreen(new HudScreen(this)); }));
        aY += ACTION_BTN_H + V2Theme.SIDEBAR_TAB_GAP;
        actionButtons.add(new MetallicButton(actionX, aY, V2Theme.SIDEBAR_TAB_WIDTH, ACTION_BTN_H, "Click GUI",
                () -> { if (client != null) client.setScreen(new ClickGuiScreen()); }));
        aY += ACTION_BTN_H + V2Theme.SIDEBAR_TAB_GAP;
        actionButtons.add(new MetallicButton(actionX, aY, V2Theme.SIDEBAR_TAB_WIDTH, ACTION_BTN_H, "Github",
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
        int contentX = panelX + SIDEBAR_W;
        int contentY = panelY;
        int contentW = PANEL_W - SIDEBAR_W;
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
        // Sidebar background — rounded LEFT corners only.
        drawLeftRoundedRect(ctx, panelX, panelY, SIDEBAR_W, PANEL_H, V2Theme.PANE_RADIUS, V2Theme.BG_SIDEBAR);

        // Content pane background — rounded RIGHT corners only.
        drawRightRoundedRect(ctx, panelX + SIDEBAR_W, panelY, PANEL_W - SIDEBAR_W, PANEL_H,
                V2Theme.PANE_RADIUS, V2Theme.BG_PANE);

        renderSidebarHeader(ctx);
        renderSidebarDividers(ctx);

        for (SidebarTab tab : sidebarButtons) {
            tab.render(ctx, mouseX, mouseY, delta);
        }
        for (MetallicButton btn : actionButtons) {
            btn.render(ctx, mouseX, mouseY, delta);
        }

        // V2.0 label bottom-left
        TextRenderer tr = textRenderer;
        ctx.drawText(tr, "V2.0", panelX + SIDEBAR_PAD_X, panelY + PANEL_H - tr.fontHeight - 6,
                V2Theme.TEXT_PRIMARY, false);

        // Active tab content
        if (!tabs.isEmpty()) {
            tabs.get(activeTabIndex).render(ctx, mouseX, mouseY, delta);
        }
    }

    private void renderSidebarHeader(DrawContext ctx) {
        int avatarX = panelX + SIDEBAR_PAD_X;
        int avatarY = panelY + SIDEBAR_PAD_TOP;
        // Use GUI_TEXTURED pipeline for the avatar
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, AVATAR_TEX,
                avatarX, avatarY, 0, 0, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE, AVATAR_SIZE);

        TextRenderer tr = textRenderer;
        Text title = Text.literal("FloydAddons").styled(s -> s.withBold(true));
        int textX = avatarX + AVATAR_SIZE + 6;
        int textY = avatarY + (AVATAR_SIZE - tr.fontHeight) / 2 + 1;
        ctx.drawText(tr, title, textX, textY, V2Theme.TEXT_PRIMARY, false);
    }

    private void renderSidebarDividers(DrawContext ctx) {
        int dx1 = panelX + DIVIDER_PAD_X;
        int dx2 = panelX + SIDEBAR_W - DIVIDER_PAD_X;
        // First divider: between header and tabs
        if (!sidebarButtons.isEmpty()) {
            int y = sidebarButtons.get(0).getY() - 5;
            ctx.fill(dx1, y, dx2, y + 1, V2Theme.DIVIDER);
        }
        // Second divider: between tabs and actions
        if (!actionButtons.isEmpty()) {
            int y = actionButtons.get(0).getY() - 5;
            ctx.fill(dx1, y, dx2, y + 1, V2Theme.DIVIDER);
        }
    }

    /** Rounded only on the left edge — fills with a single solid color. */
    private static void drawLeftRoundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h / 2)));
        for (int row = 0; row < h; row++) {
            int leftInset = V2Theme.roundedInset(r, h, row);
            ctx.fill(x + leftInset, y + row, x + w, y + row + 1, color);
        }
    }

    /** Rounded only on the right edge — fills with a single solid color. */
    private static void drawRightRoundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h / 2)));
        for (int row = 0; row < h; row++) {
            int rightInset = V2Theme.roundedInset(r, h, row);
            ctx.fill(x, y + row, x + w - rightInset, y + row + 1, color);
        }
    }

    // ---------- Input plumbing ----------

    @Override
    public boolean mouseClicked(Click click, boolean ignoresInput) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();

        // Sidebar — buttons handle press state, action via mouseReleased.
        boolean handled = false;
        for (SidebarTab tab : sidebarButtons) {
            if (tab.mouseClicked(mx, my, button)) handled = true;
        }
        for (MetallicButton btn : actionButtons) {
            if (btn.mouseClicked(mx, my, button)) handled = true;
        }
        if (handled) return true;

        if (!tabs.isEmpty()) {
            if (tabs.get(activeTabIndex).mouseClicked(mx, my, button)) return true;
        }
        return super.mouseClicked(click, ignoresInput);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();

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

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!tabs.isEmpty()) {
            if (tabs.get(activeTabIndex).mouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
                return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!tabs.isEmpty()) {
            if (tabs.get(activeTabIndex).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
