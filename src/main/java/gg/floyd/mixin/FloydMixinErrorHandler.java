package gg.floyd.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fail-soft safety net for every FloydAddons mixin.
 *
 * <p>When a Floyd mixin cannot be prepared or applied -- almost always because another mod, or a
 * third-party launcher such as Polar, already transformed or pre-loaded the same target class --
 * Mixin's default behaviour for a {@code required} config is to abort startup with a fatal error,
 * turning a single feature-level conflict into a client that will not launch at all.
 *
 * <p>This handler downgrades those fatal errors to warnings: the conflicting mixin is skipped, the
 * rest of FloydAddons still loads, and the affected feature is recorded so the player can be told
 * in-game which functionality is unavailable (see FloydAddonsMod). We cannot "fix" a foreign mod
 * transforming the same bytecode, but we can refuse to crash because of it.
 *
 * <p>Registered via the {@code errorHandlers} array in the Floyd mixin configs.
 */
public final class FloydMixinErrorHandler implements IMixinErrorHandler {
    private static final Logger LOGGER = LogManager.getLogger("FloydAddons");
    private static final List<String> DISABLED_FEATURES = new CopyOnWriteArrayList<>();

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return downgrade(mixin, th);
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
        return downgrade(mixin, th);
    }

    private ErrorAction downgrade(IMixinInfo mixin, Throwable th) {
        String feature = featureName(mixin);
        if (!DISABLED_FEATURES.contains(feature)) DISABLED_FEATURES.add(feature);
        LOGGER.warn(
            "Disabling \"{}\" because another mod or launcher conflicts with it ({}). "
                + "FloydAddons will still load; only this feature is unavailable.",
            feature, th.toString());
        return ErrorAction.WARN;
    }

    private static String featureName(IMixinInfo mixin) {
        String name = mixin == null ? null : mixin.getName();
        if (name == null) return "a FloydAddons feature";
        int dot = name.lastIndexOf('.');
        String simple = dot >= 0 ? name.substring(dot + 1) : name;
        switch (simple) {
            case "FloydFabricLoaderMixin":
                return "Mod Hider (hide FloydAddons from the mod list)";
            case "FloydBrandSpoofMixin":
                return "Client Brand Spoof";
            default:
                return simple.isEmpty() ? "a FloydAddons feature" : simple;
        }
    }

    /** Snapshot of FloydAddons features disabled by a mixin conflict, for the in-game notice. */
    public static List<String> disabledFeatures() {
        return Collections.unmodifiableList(DISABLED_FEATURES);
    }
}
