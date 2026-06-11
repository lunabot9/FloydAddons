package gg.floyd.utils;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import gg.floyd.FloydAddonsMod;
import gg.floyd.features.impl.render.FloydFont;
import gg.floyd.utils.font.MsdfGlyphProvider;
import gg.floyd.utils.font.MsdfNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.GlyphProviderType;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime control for Floyd's global custom-font override.
 *
 * <p>Floyd ships {@code assets/minecraft/font/default.json} which injects a bundled TTF provider
 * into the vanilla {@code minecraft:default} font. This helper rewrites the provider list as it is
 * loaded so the override can be toggled off (vanilla font) or pointed at a user-supplied .ttf in
 * the Floyd config dir, without editing the resource pack. Everything here is crash-safe: any
 * failure leaves the bundled provider untouched.
 */
public final class FloydFontProviders {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Path inside the bundled resource pack referenced by {@code assets/minecraft/font/default.json}. */
    private static final Identifier BUNDLED_FONT = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "font.ttf");

    /**
     * The ClickGUI's pinned font ({@code assets/floydaddons/font/clickgui.json}): always the
     * bundled provider, never the BYO .ttf, never dropped when the global override is off — the
     * ClickGUI keeps the Floyd font regardless of the Font module. Mirrors
     * {@code gg.floyd.utils.font.ClickGuiFont.FONT_ID} (kept separate so this class stays
     * loadable without Kotlin object init).
     */
    private static final Identifier CLICKGUI_FONT = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "clickgui");

    private static volatile boolean msdfSetupWarned = false;

    /**
     * D1(c) latch: set by a mid-session MSDF glyph-generation failure and consumed by the next
     * {@link #adjustDefaultFont} pass over {@code minecraft:default}, which then emits the vanilla
     * TTF definition instead of the MSDF one. Consuming (rather than sticking) means a later
     * user-initiated reload retries MSDF; if it fails again, another one-shot reload re-forces the
     * TTF fallback. One latch PER FLOYD FONT ({@code minecraft:default} and the ClickGUI font),
     * armed together: a failure can come from either font's provider, and if only the default
     * consumed the latch a failing ClickGUI provider would rebuild as MSDF again, re-fail, and
     * re-schedule reloads forever.
     */
    private static final AtomicBoolean MSDF_DISABLED_UNTIL_RELOAD = new AtomicBoolean(false);

    /** ClickGUI-font half of the D1(c) latch; see {@link #MSDF_DISABLED_UNTIL_RELOAD}. */
    private static final AtomicBoolean MSDF_DISABLED_UNTIL_RELOAD_CLICKGUI = new AtomicBoolean(false);

    /** Debounce for the one-shot failure reload; reset when the latch is consumed. */
    private static final AtomicBoolean MSDF_FAILURE_RELOAD_SCHEDULED = new AtomicBoolean(false);

    /**
     * Monotonic font epoch, bumped every time the {@code minecraft:default} provider list is
     * rebuilt (every font reload: the FloydFont "Reload Font" action, F3+T, font-size /
     * custom-font changes, server resource packs). Session-lived width caches (ClickGUI widgets,
     * see {@code gg.floyd.utils.font.FontEpochCache}) key on this so cached advances never go
     * stale across a mid-session font change.
     */
    private static final AtomicInteger FONT_EPOCH = new AtomicInteger();

    /**
     * The metrics definition + BYO path the live MSDF provider was built with — recorded so the
     * /fontdebug A/B endpoint can build a vanilla {@link TrueTypeGlyphProvider} from the exact
     * same bytes at the exact same size/oversample/shift/skip.
     */
    private static volatile TrueTypeGlyphProviderDefinition lastMsdfMetrics = null;
    private static volatile Path lastMsdfByoPath = null;

    private FloydFontProviders() {
    }

    /**
     * Mid-session per-glyph generation failure (design D1(c)): the failing bake already returned
     * the missing glyph; here we latch MSDF off for the next font reload and schedule that reload
     * once (debounced). Crash-safe — this runs inside the render loop's bake path.
     */
    public static void onMsdfGenerationFailure() {
        try {
            MSDF_DISABLED_UNTIL_RELOAD.set(true);
            MSDF_DISABLED_UNTIL_RELOAD_CLICKGUI.set(true);
            if (MSDF_FAILURE_RELOAD_SCHEDULED.compareAndSet(false, true)) {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(minecraft::reloadResourcePacks);
            }
        } catch (Throwable t) {
            LOGGER.warn("Floyd MSDF failure-reload scheduling failed", t);
        }
    }

    /** Whether the D1(c) latch is currently armed (surfaced by /fontdebug). */
    public static boolean isMsdfDisabledUntilReload() {
        return MSDF_DISABLED_UNTIL_RELOAD.get() || MSDF_DISABLED_UNTIL_RELOAD_CLICKGUI.get();
    }

    /** Current font epoch; changes whenever the default-font provider list is rebuilt. */
    public static int fontEpoch() {
        return FONT_EPOCH.get();
    }

    /**
     * Builds a throwaway vanilla {@link TrueTypeGlyphProvider} over the same font bytes and
     * metrics as the live MSDF provider, for the /fontdebug {@code ?ab=1} advance-parity check
     * (risk R2). The caller must {@code close()} it.
     */
    public static GlyphProvider buildAbComparisonProvider() {
        TrueTypeGlyphProviderDefinition metrics = lastMsdfMetrics;
        if (metrics == null) throw new IllegalStateException("no_msdf_provider_loaded");
        Path byoPath = lastMsdfByoPath;
        return byoPath != null ? loadFromFile(byoPath, metrics) : loadBundled(metrics);
    }

    /**
     * Rewrites the loaded provider list for the {@code minecraft:default} font according to the
     * Floyd render settings, and for the pinned ClickGUI font (settings-independent, see
     * {@link #adjustClickGuiFont}). Other fonts and all non-bundled providers are returned
     * unchanged.
     *
     * @param fontId the font identifier the providers were loaded for
     * @param loaded the list produced by {@code FontManager.loadResourceStack}, where each entry is a
     *               {@code Pair} of a (package-private) builder id and a provider definition; the first
     *               component is preserved untouched via {@link Pair#mapSecond}
     * @return the adjusted list, or {@code loaded} unchanged when no adjustment applies
     */
    public static List<Pair<?, GlyphProviderDefinition.Conditional>> adjustDefaultFont(
            Identifier fontId, List<Pair<?, GlyphProviderDefinition.Conditional>> loaded) {
        if (loaded == null || loaded.isEmpty()) return loaded;
        if (fontId.equals(CLICKGUI_FONT)) return adjustClickGuiFont(loaded);
        if (!fontId.equals(Minecraft.DEFAULT_FONT)) return loaded;
        // The default font's providers are being rebuilt: advances may change, invalidate caches.
        FONT_EPOCH.incrementAndGet();

        boolean enabled = FloydFont.isGlobalCustomFontEnabled();
        Path byoPath = FloydFont.customFontPath();
        // Consume the D1(c) failure latch: this reload forces the vanilla TTF fallback, and the
        // debounce resets so a later failure can schedule another one-shot reload.
        boolean latched = MSDF_DISABLED_UNTIL_RELOAD.getAndSet(false);
        if (latched) {
            MSDF_FAILURE_RELOAD_SCHEDULED.set(false);
            LOGGER.warn("Floyd MSDF disabled for this font reload after a mid-session generation failure; using the TTF provider");
        }
        boolean msdf = enabled && !latched && msdfAvailable();

        List<Pair<?, GlyphProviderDefinition.Conditional>> result = new ArrayList<>(loaded.size());
        for (Pair<?, GlyphProviderDefinition.Conditional> entry : loaded) {
            TrueTypeGlyphProviderDefinition bundled = asBundledTtf(entry.getSecond());
            if (bundled == null) {
                result.add(entry);
                continue;
            }

            if (!enabled) {
                // Drop the bundled provider so the vanilla font is used.
                continue;
            }

            TrueTypeGlyphProviderDefinition runtimeMetrics = withRuntimeMetrics(bundled);
            if (msdf) {
                GlyphProviderDefinition.Conditional msdfReplacement = msdfConditional(entry.getSecond(), runtimeMetrics, byoPath);
                if (msdfReplacement != null) {
                    result.add(entry.mapSecond(ignored -> msdfReplacement));
                    continue;
                }
                // MSDF definition could not be built; fall through to the TTF paths.
            }
            if (byoPath != null) {
                GlyphProviderDefinition.Conditional replacement = byoConditional(entry.getSecond(), runtimeMetrics, byoPath);
                if (replacement != null) {
                    result.add(entry.mapSecond(ignored -> replacement));
                    continue;
                }
                // Replacement could not be built; fall through and keep the bundled provider.
            }
            result.add(entry.mapSecond(ignored -> new GlyphProviderDefinition.Conditional(runtimeMetrics, entry.getSecond().filter())));
        }
        return result;
    }

    /**
     * The ClickGUI font ({@code floydaddons:clickgui}) ignores every Font-module setting: the
     * bundled TTF stays at its authored metrics (no BYO swap, no runtime size, never dropped) and
     * is only upgraded to the MSDF provider when the natives are available — so the ClickGUI keeps
     * the same custom font whether the global override is on, off, or pointed at a user .ttf.
     */
    private static List<Pair<?, GlyphProviderDefinition.Conditional>> adjustClickGuiFont(
            List<Pair<?, GlyphProviderDefinition.Conditional>> loaded) {
        boolean latched = MSDF_DISABLED_UNTIL_RELOAD_CLICKGUI.getAndSet(false);
        if (latched) {
            LOGGER.warn("Floyd MSDF disabled for this ClickGUI-font reload after a mid-session generation failure; using the TTF provider");
        }
        if (latched || !msdfAvailable()) return loaded;

        List<Pair<?, GlyphProviderDefinition.Conditional>> result = new ArrayList<>(loaded.size());
        for (Pair<?, GlyphProviderDefinition.Conditional> entry : loaded) {
            TrueTypeGlyphProviderDefinition bundled = asBundledTtf(entry.getSecond());
            GlyphProviderDefinition.Conditional msdfReplacement =
                    bundled == null ? null : msdfConditional(entry.getSecond(), bundled, null);
            result.add(msdfReplacement != null ? entry.mapSecond(ignored -> msdfReplacement) : entry);
        }
        return result;
    }

    private static TrueTypeGlyphProviderDefinition asBundledTtf(GlyphProviderDefinition.Conditional conditional) {
        if (conditional == null) return null;
        if (!(conditional.definition() instanceof TrueTypeGlyphProviderDefinition ttf)) return null;
        if (ttf.type() != GlyphProviderType.TTF) return null;
        return BUNDLED_FONT.equals(ttf.location()) ? ttf : null;
    }

    private static TrueTypeGlyphProviderDefinition withRuntimeMetrics(TrueTypeGlyphProviderDefinition bundled) {
        return new TrueTypeGlyphProviderDefinition(
                bundled.location(),
                FloydFont.runtimeFontSize(),
                FloydFont.runtimeFontOversample(),
                bundled.shift(),
                bundled.skip());
    }

    /**
     * Whether the MSDF font path can be used: the lwjgl-msdfgen natives must link and initialize.
     * Probing (and any unexpected classloading failure around it) is crash-safe — link failures
     * are {@link Error}s, so everything is caught and reported as "unavailable" instead.
     */
    private static boolean msdfAvailable() {
        try {
            return MsdfNative.probe();
        } catch (Throwable t) {
            if (!msdfSetupWarned) {
                msdfSetupWarned = true;
                LOGGER.warn("Floyd MSDF font path unavailable, keeping the TTF provider", t);
            }
            return false;
        }
    }

    /**
     * Builds the MSDF replacement for the bundled TTF provider: an anonymous definition whose
     * loader constructs {@link MsdfGlyphProvider} from the BYO .ttf (when selected) or the bundled
     * font bytes, at the same runtime metrics as the TTF provider it replaces. Every failure path
     * falls back to the existing TTF providers, so the global font keeps working.
     */
    private static GlyphProviderDefinition.Conditional msdfConditional(
            GlyphProviderDefinition.Conditional original,
            TrueTypeGlyphProviderDefinition metrics,
            Path byoPath) {
        try {
            GlyphProviderDefinition.Loader loader = resourceManager -> {
                try {
                    return loadMsdfProvider(resourceManager, metrics, byoPath);
                } catch (Throwable t) {
                    LOGGER.warn("Floyd MSDF provider failed to load{}", byoPath != null ? " for custom font " + byoPath : "", t);
                    if (byoPath != null) {
                        // D9 chain: custom MSDF -> bundled MSDF -> custom TTF (which itself falls
                        // back to the bundled TTF) -> vanilla. Each step is crash-safe.
                        try {
                            return loadMsdfProvider(resourceManager, metrics, null);
                        } catch (Throwable bundledFailure) {
                            LOGGER.warn("Floyd bundled MSDF fallback also failed, falling back to the TTF chain", bundledFailure);
                        }
                        return loadFromFile(byoPath, metrics);
                    }
                    return loadBundled(metrics);
                }
            };
            GlyphProviderDefinition msdfDefinition = new GlyphProviderDefinition() {
                @Override
                public GlyphProviderType type() {
                    return GlyphProviderType.TTF;
                }

                @Override
                public Either<Loader, Reference> unpack() {
                    return Either.left(loader);
                }
            };
            return new GlyphProviderDefinition.Conditional(msdfDefinition, original.filter());
        } catch (Throwable t) {
            if (!msdfSetupWarned) {
                msdfSetupWarned = true;
                LOGGER.warn("Floyd MSDF definition could not be built, keeping the TTF provider", t);
            }
            return null;
        }
    }

    private static GlyphProvider loadMsdfProvider(
            ResourceManager resourceManager,
            TrueTypeGlyphProviderDefinition metrics,
            Path byoPath) throws IOException {
        ByteBuffer fontBuffer = null;
        try {
            try (InputStream inputStream = byoPath != null
                    ? Files.newInputStream(byoPath)
                    : resourceManager.open(BUNDLED_FONT)) {
                fontBuffer = TextureUtil.readResource(inputStream);
            }
            // Ownership transfers to the provider (it frees the buffer in close(), or itself on a
            // failed construction), so null the local before handing it over.
            ByteBuffer owned = fontBuffer;
            fontBuffer = null;
            MsdfGlyphProvider provider = new MsdfGlyphProvider(
                    owned, metrics.size(), metrics.oversample(),
                    metrics.shift().x(), metrics.shift().y(), metrics.skip());
            lastMsdfMetrics = metrics;
            lastMsdfByoPath = byoPath;
            // D11(d): the ASCII prebake daemon starts only after construction completed, so the
            // thread never observes a half-built provider. CPU bitmaps + cache writes only.
            provider.startPrebake();
            return provider;
        } finally {
            MemoryUtil.memFree(fontBuffer);
        }
    }

    private static GlyphProviderDefinition.Conditional byoConditional(
            GlyphProviderDefinition.Conditional original,
            TrueTypeGlyphProviderDefinition bundled,
            Path byoPath) {
        GlyphProviderDefinition.Loader loader = resourceManager -> loadFromFile(byoPath, bundled);
        GlyphProviderDefinition byoDefinition = new GlyphProviderDefinition() {
            @Override
            public GlyphProviderType type() {
                return GlyphProviderType.TTF;
            }

            @Override
            public Either<Loader, Reference> unpack() {
                return Either.left(loader);
            }
        };
        return new GlyphProviderDefinition.Conditional(byoDefinition, original.filter());
    }

    /**
     * Loads a user-supplied .ttf from disk into a {@link TrueTypeGlyphProvider} using the same
     * FreeType path as vanilla, preserving the bundled provider's size/oversample/shift/skip so the
     * BYO font drops in at the same metrics. Mirrors
     * {@code TrueTypeGlyphProviderDefinition.load(ResourceManager)}.
     */
    private static GlyphProvider loadFromFile(Path byoPath, TrueTypeGlyphProviderDefinition bundled) {
        FT_Face face = null;
        ByteBuffer fontBuffer = null;
        try (InputStream inputStream = Files.newInputStream(byoPath)) {
            fontBuffer = TextureUtil.readResource(inputStream);
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer facePointer = stack.mallocPointer(1);
                    FreeTypeUtil.assertError(
                            FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), fontBuffer, 0L, facePointer),
                            "Initializing font face");
                    face = FT_Face.create(facePointer.get());
                }
                String format = FreeType.FT_Get_Font_Format(face);
                if (!"TrueType".equals(format)) {
                    throw new IllegalStateException("Custom font is not TTF, was " + format);
                }
                FreeTypeUtil.assertError(
                        FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE),
                        "Find unicode charmap");
                return new TrueTypeGlyphProvider(
                        fontBuffer, face, bundled.size(), bundled.oversample(),
                        bundled.shift().x(), bundled.shift().y(), bundled.skip());
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load Floyd custom font '{}', falling back to bundled font", byoPath, exception);
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                if (face != null) FreeType.FT_Done_Face(face);
            }
            MemoryUtil.memFree(fontBuffer);
            // Returning the bundled provider keeps the global font working when the BYO file is invalid.
            return loadBundled(bundled);
        }
    }

    /** Loads the bundled font through the active resource manager as the BYO fallback. */
    private static GlyphProvider loadBundled(TrueTypeGlyphProviderDefinition bundled) {
        try {
            ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            return bundled.unpack().left()
                    .orElseThrow(() -> new IllegalStateException("Bundled font has no loader"))
                    .load(resourceManager);
        } catch (Exception exception) {
            throw new RuntimeException("Floyd bundled font fallback failed", exception);
        }
    }
}
