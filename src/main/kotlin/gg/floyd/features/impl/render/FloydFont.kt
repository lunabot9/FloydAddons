package gg.floyd.features.impl.render

import gg.floyd.FloydAddonsMod
import gg.floyd.FloydAddonsMod.mc
import gg.floyd.clickgui.settings.AlwaysActive
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.NumberSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.font.FloydFonts
import gg.floyd.utils.font.OringoFontCatalog
import gg.floyd.utils.modMessage
import gg.floyd.utils.openDirectory
import net.minecraft.client.gui.Font
import net.minecraft.resources.Identifier
import net.minecraft.world.scores.DisplaySlot
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
 * resolved .ttf path they read. When Minecraft Font is OFF, normal game text renders through the
 * live Minecraft/resource-pack font stack untouched so server glyph packs and icons stay intact.
 * Floyd HUD panels use the live `mc.font` whenever they are meant to follow Minecraft's current
 * font stack, rather than a separate "looks like vanilla" clone. Everything here is crash-safe: an
 * invalid/missing selection resolves to null so the provider path falls back to the bundled font,
 * and nothing touches GL/NVGRenderer at init or config load.
 */
@AlwaysActive
object FloydFont : Module(
    name = "Font",
    category = Category.RENDER,
    description = "Choose font presets or custom .ttf files. Scoreboard and inventory HUD always use the custom font.",
    toggled = false,
) {
    val globalCustomFont by BooleanSetting("Global Custom Font", true, desc = "Applies the custom font to normal Minecraft text. Scoreboard, inventory HUD, and ClickGUI always keep their custom font.")
    private val minecraftFont by BooleanSetting("Minecraft Font", true, desc = "Applies the selected custom font to vanilla game text (chat, hotbar, F3, menus) for normal letters while pack emoji/special glyphs still fall through. OFF restores the live Minecraft/resource-pack font stack for normal game text.")
    private val disableTextShadow by BooleanSetting("Disable Text Shadow", false, desc = "Removes the 1px drop shadow under ALL rendered text (vanilla HUD + every Floyd panel) for a cleaner, sharper look. Applies instantly, no reload needed.")
    private var fontDisplaySize by NumberSetting("Font Size", 25.0, 6.0, 50.0, 0.5, desc = "Display size for the global TTF provider. Presets apply their original sizes. Reload resources (F3+T) after manual changes.")
    var selectedFont by StringSetting("Font", "", 96, desc = "Search font presets or custom .ttf files. Selecting a font applies it and reloads resources.")
    private var oringoPreset by StringSetting("Oringo Preset", "", 64, desc = "Saved preset selection.").hide()
    private val listFonts by ActionSetting("List Fonts", desc = "Prints available .ttf files in chat.") {
        modMessage("Available fonts:\n"+fontChoices().filter { it.available }.joinToString("\n") { it.label })
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
    private var lastResolvedMinecraftFontEnabled: Boolean? = null

    /**
     * Whether the selected custom font should style vanilla `minecraft:default` text for normal
     * readable characters. OFF restores the live Minecraft/resource-pack provider stack.
     */
    @JvmStatic
    fun isGlobalCustomFontEnabled(): Boolean =
        enabled && globalCustomFont && minecraftFont

    /** Floyd HUD surfaces that always retain their custom font, independently of this module. */
    enum class PanelFont { SCOREBOARD, INVENTORY }

    fun usesCustomFont(panel: PanelFont): Boolean = when (panel) {
        PanelFont.SCOREBOARD, PanelFont.INVENTORY -> true
    }

    /**
     * The [Font] a HUD panel should draw AND measure with this frame.
     *
     * Always use the dedicated [FloydFonts.panelCustom] stack for custom panels. The shared
     * Minecraft font retains standard baked glyph objects with MSDF rendering counterparts;
     * Floyd's own panels keep MSDF independently of the global override. Both stacks retain special-glyph fallbacks.
     * Render-thread only.
     */
    fun panelFont(panel: PanelFont): Font {
        val custom = usesCustomFont(panel)
        if (!custom) return FloydAddonsMod.mc.font
        return FloydFonts.panelCustom
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
            OringoFontCatalog.presets.firstOrNull { it.id==oringoPreset && OringoFontCatalog.face(it).fileName==name }?.let {
                return OringoFontCatalog.materialize(it,fontDir)
            }
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

    data class FontChoice(val id:String,val label:String,val detail:String,val available:Boolean)

    fun fontChoices():List<FontChoice> = listOf(FontChoice("bundled","Floyd Default (Inter)","Bundled Floyd font",true)) +
        OringoFontCatalog.presets.map { preset ->
            val face=OringoFontCatalog.face(preset)
            FontChoice("oringo:${preset.id}",preset.label,"${face.label} · ${preset.size.toInt()} px"+(if(face.systemFile!=null)" · Windows font" else ""),OringoFontCatalog.available(preset,fontDir))
        } + availableFonts().filterNot { file -> OringoFontCatalog.faces.any { it.fileName==file } }.map { FontChoice(it,it,"Custom .ttf file",true) }

    fun selectedFontChoice():String = OringoFontCatalog.presets.firstOrNull {
        it.id==oringoPreset && OringoFontCatalog.face(it).fileName==selectedFont
    }?.let { "oringo:${it.id}" } ?: selectedFont.ifBlank { "bundled" }

    fun selectedFontLabel():String = fontChoices().firstOrNull { it.id==selectedFontChoice() }?.label ?: selectedFont

    fun selectFont(name: String): Boolean {
        val choice=fontChoices().firstOrNull { it.id.equals(name,true) || it.label.equals(name,true) } ?: return false
        if(!choice.available)return false
        val preset=OringoFontCatalog.presets.firstOrNull { "oringo:${it.id}"==choice.id }
        if(preset!=null) {
            val path=OringoFontCatalog.materialize(preset,fontDir) ?: return false
            selectedFont=path.fileName.toString();oringoPreset=preset.id;fontDisplaySize=preset.size
        } else {
            selectedFont=if(choice.id=="bundled")"" else choice.id;oringoPreset=""
            if(choice.id=="bundled")fontDisplaySize=25.0
        }
        ModuleManager.saveConfigurations()
        reloadResources()
        modMessage("Selected font: ${choice.label}")
        return true
    }

    fun cycleFontFile(direction: Int): Boolean = cycleFont(direction)

    @JvmStatic
    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "globalCustomFont" to globalCustomFont,
        "minecraftFont" to minecraftFont,
        "scoreboardFont" to usesCustomFont(PanelFont.SCOREBOARD),
        "inventoryHudFont" to usesCustomFont(PanelFont.INVENTORY),
        "isGlobalCustomFontEnabled" to isGlobalCustomFontEnabled(),
        "yieldToLiveServerGlyphStack" to shouldYieldToLiveServerGlyphStack(),
        "fontDisplaySize" to fontDisplaySize,
        "runtimeFontSize" to runtimeFontSize(),
        "runtimeFontOversample" to runtimeFontOversample(),
        "selectedFont" to selectedFont,
        "selectedChoice" to selectedFontChoice(),
        "selectedLabel" to selectedFontLabel(),
        "oringoPresets" to fontChoices().filter { it.id.startsWith("oringo:") }.map { mapOf("id" to it.id,"label" to it.label,"detail" to it.detail,"available" to it.available) },
        "availableFonts" to availableFonts(),
        "fontDir" to fontDir.toString(),
        "customFontPath" to customFontPath()?.toString()
    )

    private fun cycleFont(direction: Int): Boolean {
        val fonts = fontChoices().filter { it.available }
        if (fonts.isEmpty()) {
            modMessage("No custom font .ttf files found.")
            return false
        }
        val current = fonts.indexOfFirst { it.id==selectedFontChoice() }.takeIf { it >= 0 } ?: 0
        return selectFont(fonts[(current + direction).floorMod(fonts.size)].id)
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

    private fun shouldYieldToLiveServerGlyphStack(): Boolean {
        val connectedServer = sequenceOf(
            mc.currentServer?.ip,
            mc.connection?.serverData?.ip,
        ).filterNotNull().firstOrNull()?.lowercase()
        if (connectedServer?.contains("hypixel.net") == true) return true

        val sidebarObjective = mc.level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)?.name
        if (sidebarObjective.equals("SBScoreboard", ignoreCase = true)) return true

        return false
    }

    init {
        on<TickEvent.ClientEnd> {
            val resolved = isGlobalCustomFontEnabled()
            val previous = lastResolvedMinecraftFontEnabled
            lastResolvedMinecraftFontEnabled = resolved
            if (previous != null && previous != resolved) reloadResources()
        }
    }
}
