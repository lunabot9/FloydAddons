package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.font.FloydFonts
import gg.floyd.utils.modMessage
import gg.floyd.utils.openDirectory
import net.minecraft.client.gui.Font
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path

/**
 * Owns Floyd's custom-font surfaces and the selected .ttf, mirroring the cosmetic file-browser
 * modules ([gg.floyd.features.impl.cosmetic.FloydSkin] etc.): a `fonts/` dir under the FloydAddons
 * config dir is scanned for .ttf files, with Previous/Next/List/Open Folder/Reload actions and a
 * selected-file [StringSetting].
 *
 * The actual font application stays in [gg.floyd.utils.FloydFontProviders] /
 * [gg.floyd.mixin.mixins.FloydDefaultFontMixin]; this module just exposes the toggle and the
 * resolved .ttf path they read. When Minecraft Font is OFF, or when connected to Hypixel where the
 * live SkyBlock glyph stack needs to win, normal game text renders through the live
 * Minecraft/resource-pack font stack untouched so server glyph packs and icons stay intact.
 * Floyd HUD panels use the live `mc.font` whenever they are meant to follow Minecraft's current
 * font stack, rather than a separate "looks like vanilla" clone. Everything here is crash-safe: an
 * invalid/missing selection resolves to null so the provider path falls back to the bundled font,
 * and nothing touches GL/NVGRenderer at init or config load.
 */
object FloydFont : Module(
    name = "Font",
    category = Category.RENDER,
    description = "Custom font: per-surface toggles (vanilla text, scoreboard, day tracker, inventory HUD) and a .ttf picker from config/floydaddons/fonts.",
    toggled = true,
) {
    val globalCustomFont by BooleanSetting("Global Custom Font", true, desc = "Master switch for every custom-font surface below. OFF = the live Minecraft font on normal text and per-panel custom-font toggles decide Floyd HUD text (the ClickGUI keeps its own pinned font).")
    private val minecraftFont by BooleanSetting("Minecraft Font", true, desc = "Applies the selected custom font to vanilla game text (chat, hotbar, F3, menus) for normal letters while pack emoji/special glyphs still fall through. OFF restores the live Minecraft/resource-pack font stack for normal game text, and Hypixel sessions automatically yield to the live glyph stack to preserve SkyBlock icons.")
    private val scoreboardFont by BooleanSetting("Scoreboard Font", true, desc = "Custom Scoreboard panel uses the custom font. Applies instantly.")
    private val dayTrackerFont by BooleanSetting("Day Tracker Font", true, desc = "Day Tracker panel uses the custom font. Applies instantly.")
    private val inventoryHudFont by BooleanSetting("Inventory HUD Font", true, desc = "Inventory HUD stack counts use the custom font. Applies instantly.")
    private val disableTextShadow by BooleanSetting("Disable Text Shadow", false, desc = "Removes the 1px drop shadow under ALL rendered text (vanilla HUD + every Floyd panel) for a cleaner, sharper look. Applies instantly, no reload needed.")
    private val fontDisplaySize by NumberSetting("Font Size", 25.0, 6.0, 50.0, 0.5, desc = "Display size for the global TTF provider. 25 matches the Font mod default. Reload resources (F3+T) to apply.")
    var selectedFont by StringSetting("Font", "", 96, desc = "Optional .ttf in config/floydaddons/fonts to use instead of the bundled font. Reload resources (F3+T) to apply.")
    private val listFonts by ActionSetting("List Fonts", desc = "Prints available .ttf files in chat.") {
        val fonts = availableFonts()
        modMessage(if (fonts.isEmpty()) "No custom font .ttf files found." else "Available fonts:\n${fonts.joinToString("\n")}")
    }
    private val previousFont by ActionSetting("Previous Font", desc = "Selects the previous available .ttf file.") {
        cycleFont(-1)
    }
    private val nextFont by ActionSetting("Next Font", desc = "Selects the next available .ttf file.") {
        cycleFont(1)
    }
    private val openFontFolder by ActionSetting("Open Font Folder", desc = "Opens config/floydaddons/fonts.") {
        modMessage(if (openDirectory(fontDir)) "Opened font folder." else "Could not open font folder: $fontDir")
    }
    private val reloadFont by ActionSetting("Reload Font", desc = "Reloads font resources to apply the selected .ttf.") {
        ModuleManager.saveConfigurations()
        reloadResources()
        modMessage("Reloading resources to apply font: ${selectedFont.ifBlank { "(bundled)" }}")
    }

    // Resolved lazily so loading this object's class never forces Minecraft/config init (keeps the
    // toggle + selected-file reads GL/MC-safe at module init and config load).
    private val fontDir: Path by lazy { FloydAddonsMod.configFile.toPath().resolve("fonts") }
    private val bundledFont: Identifier by lazy { Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "font.ttf") }
    private var defaultExtracted = false

    /**
     * Whether the selected custom font should style vanilla `minecraft:default` text for normal
     * readable characters. OFF restores the live Minecraft/resource-pack provider stack, and
     * Hypixel sessions also yield to that live stack so SkyBlock glyph packs and emoji render
     * untouched.
     */
    @JvmStatic
    fun isGlobalCustomFontEnabled(): Boolean = enabled && globalCustomFont && minecraftFont

    /** A Floyd HUD panel surface with its own custom-font toggle (see [panelFont]). */
    enum class PanelFont { SCOREBOARD, DAY_TRACKER, INVENTORY }

    fun usesCustomFont(panel: PanelFont): Boolean = enabled && globalCustomFont && when (panel) {
        PanelFont.SCOREBOARD -> scoreboardFont
        PanelFont.DAY_TRACKER -> dayTrackerFont
        PanelFont.INVENTORY -> inventoryHudFont
    }

    /**
     * The [Font] a HUD panel should draw AND measure with this frame.
     *
     * When the panel custom-font toggle is OFF, panels use the live `mc.font` so the disabled path
     * is the actual Minecraft font stack, not Floyd's separate `floydaddons:vanilla` clone.
     *
     * When the panel custom-font toggle is ON and the live Minecraft font is already running
     * Floyd's global custom-font override, panels also use the live `mc.font` so server-pack emoji
     * and special glyph providers still fall through behind the custom letters.
     *
     * Only when a panel wants the custom font while the global Minecraft-font override is OFF (for
     * example, panel custom ON + Minecraft Font OFF) do panels use [FloydFonts.panelCustom].
     * Render-thread only.
     */
    fun panelFont(panel: PanelFont): Font {
        val custom = usesCustomFont(panel)
        if (!custom) return FloydAddonsMod.mc.font
        return if (isGlobalCustomFontEnabled()) FloydAddonsMod.mc.font else FloydFonts.panelCustom
    }

    /** Whether all text drop shadows should be suppressed (drives [gg.floyd.mixin.mixins.FontMixin]). */
    @JvmStatic
    fun isTextShadowDisabled(): Boolean = enabled && disableTextShadow

    /** Minecraft TTF provider size computed like MichiJP's Font mod: display size / 12.5, supersampled and clamped to 20. */
    @JvmStatic
    fun runtimeFontSize(): Float {
        val displaySize = fontDisplaySize.coerceAtLeast(0.5)
        val runtimeSize = (displaySize / 12.5).coerceAtLeast(0.5)
        val baseSize = kotlin.math.ceil(runtimeSize).toInt().coerceAtLeast(1)
        val supersampledSize = kotlin.math.ceil(runtimeSize * 4.0).toInt().coerceAtLeast(baseSize)
        return baseSize.coerceAtLeast(supersampledSize.coerceAtMost(20)).toFloat()
    }

    @JvmStatic
    fun runtimeFontOversample(): Float {
        val displaySize = fontDisplaySize.coerceAtLeast(0.5)
        val runtimeSize = (displaySize / 12.5).coerceAtLeast(0.5)
        // 2x the Font-mod oversample supersamples the glyph atlas (crisper small text) while leaving
        // runtimeFontSize unchanged. CAPPED at 4: at the default display size the uncapped value is 8,
        // which rasterizes ~64px glyph bitmaps that overflow Minecraft's 256x256 FontTexture atlas, so
        // glyphs baked after it fills silently drop to the invisible missing glyph (the "/" and "u"
        // disappearing from chat). Oversample 4 (~32px glyphs) stays crisper than vanilla while fitting.
        return ((runtimeFontSize() / runtimeSize.toFloat()) * 2f).coerceIn(1f, 4f)
    }

    /**
     * Resolved path to the selected .ttf inside `config/floydaddons/fonts`, or null when unset or
     * the file is missing/unreadable. Crash-safe: any resolution failure returns null so callers
     * fall back to the bundled font.
     */
    @JvmStatic
    fun customFontPath(): Path? {
        val name = selectedFont.trim()
        if (name.isEmpty()) return null
        return try {
            val path = fontDir.resolve(name).normalize()
            // Keep the resolved file inside the fonts dir and verify it is a readable .ttf.
            if (!path.startsWith(fontDir)) return null
            if (!name.lowercase().endsWith(".ttf")) return null
            if (Files.isRegularFile(path) && Files.isReadable(path)) path else null
        } catch (_: Exception) {
            null
        }
    }

    fun availableFontFiles(): List<String> = availableFonts()

    fun selectFont(name: String): Boolean {
        val font = availableFonts().firstOrNull { it.equals(name, ignoreCase = true) } ?: return false
        selectedFont = font
        ModuleManager.saveConfigurations()
        reloadResources()
        modMessage("Selected font: $selectedFont")
        return true
    }

    fun cycleFontFile(direction: Int): Boolean = cycleFont(direction)

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "globalCustomFont" to globalCustomFont,
        "minecraftFont" to minecraftFont,
        "scoreboardFont" to scoreboardFont,
        "dayTrackerFont" to dayTrackerFont,
        "inventoryHudFont" to inventoryHudFont,
        "isGlobalCustomFontEnabled" to isGlobalCustomFontEnabled(),
        "fontDisplaySize" to fontDisplaySize,
        "runtimeFontSize" to runtimeFontSize(),
        "runtimeFontOversample" to runtimeFontOversample(),
        "selectedFont" to selectedFont,
        "availableFonts" to availableFonts(),
        "fontDir" to fontDir.toString(),
        "customFontPath" to customFontPath()?.toString()
    )

    private fun cycleFont(direction: Int): Boolean {
        val fonts = availableFonts()
        if (fonts.isEmpty()) {
            modMessage("No custom font .ttf files found.")
            return false
        }
        val current = fonts.indexOfFirst { it.equals(selectedFont, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        selectedFont = fonts[(current + direction).floorMod(fonts.size)]
        ModuleManager.saveConfigurations()
        reloadResources()
        modMessage("Selected font: $selectedFont")
        return true
    }

    private fun ensureExternalDir() {
        Files.createDirectories(fontDir)
    }

    /**
     * Seeds the fonts dir on first scan with the bundled font so the picker is never empty. Pulled
     * from the resource manager lazily (never at init/config load), so it is GL-safe.
     */
    private fun extractDefaultFont() {
        if (defaultExtracted) return
        defaultExtracted = true
        try {
            ensureExternalDir()
            val target = fontDir.resolve("inter.ttf")
            if (Files.exists(target)) return
            FloydAddonsMod.mc.resourceManager.getResource(bundledFont).ifPresent { resource ->
                resource.open().use { input -> Files.copy(input, target) }
            }
        } catch (_: Exception) {
            // Seeding is best-effort; an empty fonts dir simply falls back to the bundled font.
        }
    }

    private fun availableFonts(): List<String> {
        return try {
            extractDefaultFont()
            ensureExternalDir()
            Files.list(fontDir).use { stream ->
                stream.filter { it.isRegularFileSafe() && it.fileName.toString().lowercase().endsWith(".ttf") }
                    .map { it.fileName.toString() }
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Triggers a resource reload (F3+T equivalent) so the new font provider list is rebuilt. */
    private fun reloadResources() {
        try {
            FloydAddonsMod.mc.reloadResourcePacks()
        } catch (_: Exception) {
            // Resources will pick up the new selection on the next manual F3+T if this fails.
        }
    }

    private fun Path.isRegularFileSafe(): Boolean = Files.isRegularFile(this)

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
}
