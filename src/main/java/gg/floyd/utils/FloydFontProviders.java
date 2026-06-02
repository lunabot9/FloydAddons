package gg.floyd.utils;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import gg.floyd.FloydAddonsMod;
import gg.floyd.features.impl.render.FloydFont;
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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    private FloydFontProviders() {
    }

    /**
     * Rewrites the loaded provider list for the {@code minecraft:default} font according to the
     * Floyd render settings. Other fonts and all non-bundled providers are returned unchanged.
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
        if (!fontId.equals(Minecraft.DEFAULT_FONT)) return loaded;

        boolean enabled = FloydFont.isGlobalCustomFontEnabled();
        Path byoPath = FloydFont.customFontPath();

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
