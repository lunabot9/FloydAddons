package floydaddons.not.dogshit.client.gui.v2.widget;

/**
 * Sidebar nav tab — fixed-width MetallicButton specialization. The active tab shows
 * the white outline (handled by {@link MetallicButton#setActive}).
 */
public class SidebarTab extends MetallicButton {
    public SidebarTab(int x, int y, String label, Runnable onClick) {
        super(x, y, V2Theme.SIDEBAR_TAB_WIDTH, V2Theme.SIDEBAR_TAB_HEIGHT, label, onClick);
    }

    public SidebarTab(int x, int y, int height, String label, Runnable onClick) {
        super(x, y, V2Theme.SIDEBAR_TAB_WIDTH, height, label, onClick);
    }
}
