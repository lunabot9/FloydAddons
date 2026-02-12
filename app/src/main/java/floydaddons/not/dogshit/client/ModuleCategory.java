package floydaddons.not.dogshit.client;

/**
 * Categories for grouping modules in the ClickGUI.
 */
public enum ModuleCategory {
    RENDER("Render"),
    HIDERS("Hiders"),
    PLAYER("Player"),
    CAMERA("Camera");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
