package floydaddons.not.dogshit.client.gui.v2.tab;

import floydaddons.not.dogshit.client.gui.v2.row.AnimationsRow;
import floydaddons.not.dogshit.client.gui.v2.row.EspRow;
import floydaddons.not.dogshit.client.gui.v2.row.HidersRow;
import floydaddons.not.dogshit.client.gui.v2.row.VisualRow;
import floydaddons.not.dogshit.client.gui.v2.row.XrayRow;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.ContentPane;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * "Render" tab content. Holds a {@link ContentPane} with five accordion rows:
 * Hiders, X-ray, ESP, Animations, Visual. Only one accordion may be expanded at
 * a time — clicking a collapsed header collapses all siblings.
 *
 * <p>Source: /tmp/fig-2x/Main - Render.png and the per-row frames.
 */
public class RenderTab implements V2Tab {

    private static final int HEADER_H = 28;

    private final ContentPane pane;
    private final List<AccordionRow> rows = new ArrayList<>();

    public RenderTab() {
        this.pane = new ContentPane(0, 0, 0, 0, "Render");

        AccordionRow hiders = new AccordionRow(0, 0, 0, HEADER_H, "Hiders", new HidersRow());
        AccordionRow xray = new AccordionRow(0, 0, 0, HEADER_H, "X-ray", new XrayRow());
        AccordionRow esp = new AccordionRow(0, 0, 0, HEADER_H, "ESP", new EspRow());
        AccordionRow animations = new AccordionRow(0, 0, 0, HEADER_H, "Animations", new AnimationsRow());
        AccordionRow visual = new AccordionRow(0, 0, 0, HEADER_H, "Visual", new VisualRow());

        rows.add(hiders);
        rows.add(xray);
        rows.add(esp);
        rows.add(animations);
        rows.add(visual);

        for (AccordionRow row : rows) {
            pane.add(new AccordionChild(row));
        }
    }

    @Override
    public String displayName() {
        return "Render";
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        pane.setBounds(x, y, w, h);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        pane.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Snapshot expansion state so we can detect newly-opened rows and collapse siblings.
        boolean[] before = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) before[i] = rows.get(i).isExpanded();

        boolean handled = pane.mouseClicked(mx, my, button);

        if (handled) {
            int newlyOpenedIdx = -1;
            for (int i = 0; i < rows.size(); i++) {
                if (!before[i] && rows.get(i).isExpanded()) {
                    newlyOpenedIdx = i;
                    break;
                }
            }
            if (newlyOpenedIdx >= 0) {
                for (int i = 0; i < rows.size(); i++) {
                    if (i != newlyOpenedIdx) rows.get(i).setExpanded(false);
                }
            }
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return pane.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return pane.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        return pane.mouseScrolled(mx, my, horiz, vert);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        return pane.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        return pane.charTyped(chr, mods);
    }

    /** Bridge so an {@link AccordionRow} can live inside a {@link ContentPane}. */
    private static final class AccordionChild implements ContentPane.Child {
        private final AccordionRow row;

        AccordionChild(AccordionRow row) {
            this.row = row;
        }

        @Override
        public int getHeight() {
            return row.getTotalHeight();
        }

        @Override
        public void layout(int x, int y, int w) {
            row.setPos(x, y);
            row.setWidth(w);
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            row.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return row.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return row.mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            return row.mouseDragged(mx, my, button, dx, dy);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
            return row.mouseScrolled(mx, my, horiz, vert);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return row.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return row.charTyped(chr, modifiers);
        }
    }
}
