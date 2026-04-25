package floydaddons.not.dogshit.client.gui.v2.tab;

import floydaddons.not.dogshit.client.config.RenderConfig;
import floydaddons.not.dogshit.client.config.SkinConfig;
import floydaddons.not.dogshit.client.features.cosmetic.CapeManager;
import floydaddons.not.dogshit.client.gui.v2.widget.AccordionRow;
import floydaddons.not.dogshit.client.gui.v2.widget.ContentPane;
import floydaddons.not.dogshit.client.gui.v2.widget.LabeledDropdown;
import floydaddons.not.dogshit.client.gui.v2.widget.MetallicButton;
import floydaddons.not.dogshit.client.gui.v2.widget.Slider;
import floydaddons.not.dogshit.client.gui.v2.widget.ToggleSwitch;
import floydaddons.not.dogshit.client.gui.v2.widget.V2Theme;
import floydaddons.not.dogshit.client.skin.SkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cosmetic tab — Custom Skin / Custom Cape / Cone Hat accordions.
 */
public class CosmeticTab implements V2Tab {
    private static final int HEADER_H = 28;

    private final ContentPane pane;
    private final AccordionRow skinRow;
    private final AccordionRow capeRow;
    private final AccordionRow coneHatRow;
    private final List<AccordionRow> rows = new ArrayList<>();
    private final List<RowChild> rowChildren = new ArrayList<>();

    private final SkinBody skinBody;
    private final CapeBody capeBody;

    public CosmeticTab() {
        this.pane = new ContentPane(0, 0, 0, 0, "Cosmetics");

        this.skinBody = new SkinBody();
        this.capeBody = new CapeBody();
        ConeHatBody coneHatBody = new ConeHatBody();

        this.skinRow = new AccordionRow(0, 0, 0, HEADER_H, "Custom Skin", skinBody);
        this.capeRow = new AccordionRow(0, 0, 0, HEADER_H, "Custom Cape", capeBody);
        this.coneHatRow = new AccordionRow(0, 0, 0, HEADER_H, "Cone Hat", coneHatBody);
        rows.add(skinRow);
        rows.add(capeRow);
        rows.add(coneHatRow);

        for (AccordionRow row : rows) {
            RowChild child = new RowChild(row);
            rowChildren.add(child);
            pane.add(child);
        }
    }

    @Override
    public String displayName() {
        return "Cosmetic";
    }

    @Override
    public void layout(int x, int y, int w, int h) {
        pane.setBounds(x, y, w, h);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Enforce single-expanded invariant before render: if more than one is open,
        // keep the most-recently opened by user input (handled in mouseClicked).
        pane.render(ctx, mouseX, mouseY, delta);

        // Dropdown popups must paint OVER everything else.
        skinBody.renderPopupOverlay(ctx, mouseX, mouseY, delta);
        capeBody.renderPopupOverlay(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Track which rows were expanded BEFORE the click so we know if a header click
        // just opened a previously-closed row.
        boolean[] before = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) before[i] = rows.get(i).isExpanded();

        boolean handled = pane.mouseClicked(mx, my, button);

        // After delegating: if any row newly expanded, collapse the others.
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

    // ---------- Row wrapper that adapts AccordionRow into ContentPane.Child ----------

    private static final class RowChild implements ContentPane.Child {
        private final AccordionRow row;

        RowChild(AccordionRow row) {
            this.row = row;
        }

        @Override public int getHeight() { return row.getTotalHeight(); }

        @Override
        public void layout(int x, int y, int w) {
            row.setPos(x, y);
            row.setWidth(w);
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            row.render(ctx, mouseX, mouseY, delta);
        }

        @Override public boolean mouseClicked(double mx, double my, int button) { return row.mouseClicked(mx, my, button); }
        @Override public boolean mouseReleased(double mx, double my, int button) { return row.mouseReleased(mx, my, button); }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return row.mouseDragged(mx, my, button, dx, dy); }
        @Override public boolean mouseScrolled(double mx, double my, double horiz, double vert) { return row.mouseScrolled(mx, my, horiz, vert); }
        @Override public boolean keyPressed(int k, int s, int m) { return row.keyPressed(k, s, m); }
        @Override public boolean charTyped(char c, int m) { return row.charTyped(c, m); }
    }

    // ---------- Custom Skin body ----------

    private static final class SkinBody implements AccordionRow.Body {
        private static final int PAD = 12;
        private static final int ROW_GAP = 8;
        private static final int LABEL_TOGGLE_GAP = 8;
        private static final int FOLDER_BTN_W = 100;
        private static final int FOLDER_BTN_H = 18;
        private static final int DROPDOWN_W = 110;
        private static final int DROPDOWN_H = 18;
        private static final int HEIGHT = 96;

        private int x, y, w;
        private final ToggleSwitch skinToggle;
        private final ToggleSwitch othersToggle;
        private final MetallicButton openFolderBtn;
        private final LabeledDropdown skinDropdown;

        SkinBody() {
            this.skinToggle = new ToggleSwitch(0, 0, SkinConfig::customEnabled, v -> {
                SkinConfig.setCustomEnabled(v);
                SkinConfig.save();
                SkinManager.clearCache();
            });
            this.othersToggle = new ToggleSwitch(0, 0, SkinConfig::othersEnabled, v -> {
                SkinConfig.setOthersEnabled(v);
                SkinConfig.save();
                SkinManager.clearCache();
            });
            this.openFolderBtn = new MetallicButton(0, 0, FOLDER_BTN_W, FOLDER_BTN_H,
                    "Open Skin Folder", () -> openPath(SkinManager.ensureExternalDir()));
            this.openFolderBtn.setRadius(V2Theme.SMALL_PILL_RADIUS);
            this.skinDropdown = new LabeledDropdown(0, 0, DROPDOWN_W, DROPDOWN_H,
                    SkinManager::listAvailableSkins,
                    SkinConfig::getSelectedSkin,
                    name -> {
                        SkinConfig.setSelectedSkin(name);
                        SkinConfig.save();
                        SkinManager.clearCache();
                    });
        }

        @Override public int getHeight() { return HEIGHT; }

        @Override
        public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
            this.x = x;
            this.y = y;
            this.w = w;

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;

            // Row 1: "Skin" label + toggle  | top-right: Open Skin Folder button
            int row1Y = y + PAD;
            String skinLbl = "Skin";
            int skinLblW = tr.getWidth(skinLbl);
            int skinLblY = row1Y + (V2Theme.TOGGLE_TRACK_H - tr.fontHeight) / 2 + 1;
            ctx.drawText(tr, skinLbl, x + PAD, skinLblY, V2Theme.TEXT_PRIMARY, false);
            skinToggle.setPos(x + PAD + skinLblW + LABEL_TOGGLE_GAP, row1Y);
            skinToggle.render(ctx, mouseX, mouseY, delta);

            int folderX = x + w - PAD - FOLDER_BTN_W;
            openFolderBtn.setBounds(folderX, row1Y - 2, FOLDER_BTN_W, FOLDER_BTN_H);
            openFolderBtn.render(ctx, mouseX, mouseY, delta);

            // Row 2: "Others Skin" + toggle  | dropdown on the right
            int row2Y = row1Y + V2Theme.TOGGLE_TRACK_H + ROW_GAP;
            String otherLbl = "Others Skin";
            int otherLblW = tr.getWidth(otherLbl);
            int otherLblY = row2Y + (V2Theme.TOGGLE_TRACK_H - tr.fontHeight) / 2 + 1;
            ctx.drawText(tr, otherLbl, x + PAD, otherLblY, V2Theme.TEXT_PRIMARY, false);
            othersToggle.setPos(x + PAD + otherLblW + LABEL_TOGGLE_GAP, row2Y);
            othersToggle.render(ctx, mouseX, mouseY, delta);

            int ddX = x + w - PAD - DROPDOWN_W;
            int ddY = row2Y - 3;
            skinDropdown.setPos(ddX, ddY);
            skinDropdown.setSize(DROPDOWN_W, DROPDOWN_H);
            skinDropdown.render(ctx, mouseX, mouseY, delta);

            // Hint text at the bottom
            String hint = "Drop any .png skin file into the skin folder.";
            int hintW = tr.getWidth(hint);
            int hintX = x + (w - hintW) / 2;
            int hintY = y + HEIGHT - PAD - tr.fontHeight + 2;
            ctx.drawText(tr, hint, hintX, hintY, V2Theme.TEXT_SECONDARY, false);
        }

        /** Called by the parent screen AFTER all other rendering. */
        void renderPopupOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
            if (skinDropdown.isOpen()) {
                skinDropdown.renderPopup(ctx, mouseX, mouseY, delta);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (skinDropdown.mouseClicked(mx, my, button)) return true;
            if (skinToggle.mouseClicked(mx, my, button)) return true;
            if (othersToggle.mouseClicked(mx, my, button)) return true;
            if (openFolderBtn.mouseClicked(mx, my, button)) return true;
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return openFolderBtn.mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
            return skinDropdown.mouseScrolled(mx, my, horiz, vert);
        }
    }

    // ---------- Custom Cape body ----------

    private static final class CapeBody implements AccordionRow.Body {
        private static final int PAD = 12;
        private static final int ROW_GAP = 6;
        private static final int LABEL_TOGGLE_GAP = 8;
        private static final int FOLDER_BTN_W = 100;
        private static final int FOLDER_BTN_H = 18;
        private static final int DROPDOWN_W = 110;
        private static final int DROPDOWN_H = 18;
        private static final int HEIGHT = 76;

        private final ToggleSwitch capeToggle;
        private final MetallicButton openFolderBtn;
        private final LabeledDropdown capeDropdown;

        CapeBody() {
            this.capeToggle = new ToggleSwitch(0, 0, RenderConfig::isCapeEnabled, v -> {
                RenderConfig.setCapeEnabled(v);
                RenderConfig.save();
            });
            this.openFolderBtn = new MetallicButton(0, 0, FOLDER_BTN_W, FOLDER_BTN_H,
                    "Open Cape Folder", () -> openPath(CapeManager.ensureDir()));
            this.openFolderBtn.setRadius(V2Theme.SMALL_PILL_RADIUS);
            this.capeDropdown = new LabeledDropdown(0, 0, DROPDOWN_W, DROPDOWN_H,
                    () -> CapeManager.listAvailableImages(false),
                    RenderConfig::getSelectedCapeImage,
                    name -> {
                        RenderConfig.setSelectedCapeImage(name);
                        RenderConfig.save();
                    });
        }

        @Override public int getHeight() { return HEIGHT; }

        @Override
        public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;

            // Open Cape Folder button — top-right
            int folderX = x + w - PAD - FOLDER_BTN_W;
            int folderY = y + PAD;
            openFolderBtn.setBounds(folderX, folderY, FOLDER_BTN_W, FOLDER_BTN_H);
            openFolderBtn.render(ctx, mouseX, mouseY, delta);

            // Toggle row + dropdown below the folder button
            int row1Y = folderY + FOLDER_BTN_H + ROW_GAP;
            String lbl = "Custom Cape";
            int lblW = tr.getWidth(lbl);
            int lblY = row1Y + (V2Theme.TOGGLE_TRACK_H - tr.fontHeight) / 2 + 1;
            ctx.drawText(tr, lbl, x + PAD, lblY, V2Theme.TEXT_PRIMARY, false);
            capeToggle.setPos(x + PAD + lblW + LABEL_TOGGLE_GAP, row1Y);
            capeToggle.render(ctx, mouseX, mouseY, delta);

            int ddX = x + w - PAD - DROPDOWN_W;
            int ddY = row1Y - 3;
            capeDropdown.setPos(ddX, ddY);
            capeDropdown.setSize(DROPDOWN_W, DROPDOWN_H);
            capeDropdown.render(ctx, mouseX, mouseY, delta);

            // Hint
            String hint = "Drop any .png file into the folder.";
            int hintY = y + HEIGHT - PAD - tr.fontHeight + 2;
            ctx.drawText(tr, hint, x + PAD, hintY, V2Theme.TEXT_SECONDARY, false);
        }

        void renderPopupOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
            if (capeDropdown.isOpen()) {
                capeDropdown.renderPopup(ctx, mouseX, mouseY, delta);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (capeDropdown.mouseClicked(mx, my, button)) return true;
            if (capeToggle.mouseClicked(mx, my, button)) return true;
            if (openFolderBtn.mouseClicked(mx, my, button)) return true;
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return openFolderBtn.mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
            return capeDropdown.mouseScrolled(mx, my, horiz, vert);
        }
    }

    // ---------- Cone Hat body ----------

    private static final class ConeHatBody implements AccordionRow.Body {
        private static final int PAD = 12;
        private static final int ROW_H = 14;
        private static final int ROW_GAP = 4;
        private static final int LABEL_W = 80;
        private static final int SLIDER_PAD_LEFT = 6;

        private final List<SliderRow> sliderRows;
        private final int height;

        ConeHatBody() {
            this.sliderRows = new ArrayList<>(Arrays.asList(
                    new SliderRow("Height",
                            new Slider(0, 0, 0, ROW_H, 0.1, 1.5,
                                    () -> RenderConfig.getConeHatHeight(),
                                    v -> { RenderConfig.setConeHatHeight((float) v); RenderConfig.save(); })
                                    .withDecimals(2)),
                    new SliderRow("Radius",
                            new Slider(0, 0, 0, ROW_H, 0.05, 0.8,
                                    () -> RenderConfig.getConeHatRadius(),
                                    v -> { RenderConfig.setConeHatRadius((float) v); RenderConfig.save(); })
                                    .withDecimals(2)),
                    new SliderRow("Y Offset",
                            new Slider(0, 0, 0, ROW_H, -1.5, 0.5,
                                    () -> RenderConfig.getConeHatYOffset(),
                                    v -> { RenderConfig.setConeHatYOffset((float) v); RenderConfig.save(); })
                                    .withDecimals(2)),
                    new SliderRow("Rotation",
                            new Slider(0, 0, 0, ROW_H, 0, 360,
                                    () -> RenderConfig.getConeHatRotation(),
                                    v -> { RenderConfig.setConeHatRotation((float) v); RenderConfig.save(); })
                                    .withStep(1.0).withDecimals(0)),
                    new SliderRow("Rotation Speed",
                            new Slider(0, 0, 0, ROW_H, 0, 360,
                                    () -> RenderConfig.getConeHatRotationSpeed(),
                                    v -> { RenderConfig.setConeHatRotationSpeed((float) v); RenderConfig.save(); })
                                    .withStep(1.0).withDecimals(0))
            ));
            this.height = PAD * 2 + sliderRows.size() * ROW_H + (sliderRows.size() - 1) * ROW_GAP;
        }

        @Override public int getHeight() { return height; }

        @Override
        public void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int yCursor = y + PAD;
            int sliderX = x + PAD + LABEL_W + SLIDER_PAD_LEFT;
            int sliderW = (x + w - PAD) - sliderX;
            for (SliderRow row : sliderRows) {
                int textY = yCursor + (ROW_H - tr.fontHeight) / 2 + 1;
                ctx.drawText(tr, row.label, x + PAD, textY, V2Theme.TEXT_PRIMARY, false);
                row.slider.setPos(sliderX, yCursor);
                row.slider.setSize(sliderW, ROW_H);
                row.slider.render(ctx, mouseX, mouseY, delta);
                yCursor += ROW_H + ROW_GAP;
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            for (SliderRow row : sliderRows) {
                if (row.slider.mouseClicked(mx, my, button)) return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean any = false;
            for (SliderRow row : sliderRows) {
                any |= row.slider.mouseReleased(mx, my, button);
            }
            return any;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            boolean any = false;
            for (SliderRow row : sliderRows) {
                any |= row.slider.mouseDragged(mx, my, button, dx, dy);
            }
            return any;
        }

        private static final class SliderRow {
            final String label;
            final Slider slider;

            SliderRow(String label, Slider slider) {
                this.label = label;
                this.slider = slider;
            }
        }
    }

    // ---------- Folder open helper (cross-platform) ----------

    private static void openPath(Path path) {
        if (path == null) return;
        try {
            File f = path.toAbsolutePath().toFile();
            String target = f.toString();
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", target);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", target);
            } else {
                pb = new ProcessBuilder("sh", "-c", "xdg-open \"" + target + "\" &");
            }
            File devNull = new File(os.contains("win") ? "NUL" : "/dev/null");
            pb.redirectInput(ProcessBuilder.Redirect.from(devNull));
            pb.redirectOutput(ProcessBuilder.Redirect.to(devNull));
            pb.redirectError(ProcessBuilder.Redirect.to(devNull));
            pb.start();
        } catch (Exception ignored) {
        }
    }
}
