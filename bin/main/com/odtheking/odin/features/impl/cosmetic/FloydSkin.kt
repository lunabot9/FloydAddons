package com.odtheking.odin.features.impl.cosmetic

import com.mojang.blaze3d.platform.NativeImage
import com.odtheking.odin.FloydAddonsMod
import com.odtheking.odin.FloydAddonsMod.mc
import com.odtheking.odin.clickgui.settings.impl.ActionSetting
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.StringSetting
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.Module
import com.odtheking.odin.features.ModuleManager
import com.odtheking.odin.utils.modMessage
import com.odtheking.odin.utils.openDirectory
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

object FloydSkin : Module(
    name = "Custom Skin",
    category = Category.COSMETIC,
    description = "Floyd custom skin settings.",
    toggled = true,
) {
    val self by BooleanSetting("Self", false, desc = "Applies custom skin to self.")
    val others by BooleanSetting("Others", false, desc = "Applies custom skin to other players.")
    var selectedSkin by StringSetting("Skin", "george-floyd.png", 96, desc = "Skin PNG file in config/floydaddons/skins.")
    private val listSkins by ActionSetting("List Skins", desc = "Prints available skin PNG files in chat.") {
        val skins = availableSkins()
        modMessage(if (skins.isEmpty()) "No custom skin PNG files found." else "Available skins:\n${skins.joinToString("\n")}")
    }
    private val previousSkin by ActionSetting("Previous Skin", desc = "Selects the previous available skin PNG file.") {
        cycleSkin(-1)
    }
    private val nextSkin by ActionSetting("Next Skin", desc = "Selects the next available skin PNG file.") {
        cycleSkin(1)
    }
    private val openSkinFolder by ActionSetting("Open Skin Folder", desc = "Opens config/floydaddons/skins.") {
        modMessage(if (openDirectory(skinDir)) "Opened skin folder." else "Could not open skin folder: $skinDir")
    }
    private val reloadSkin by ActionSetting("Reload Skin", desc = "Reloads the selected custom skin texture.") {
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Reloaded selected skin: $selectedSkin")
    }

    private val skinDir: Path = FloydAddonsMod.configFile.toPath().resolve("skins")
    private val builtinSkin = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "textures/skin/custom.png")
    private val dynamicSkin = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "skin/custom")
    private var cachedTexture: Identifier? = null
    private var lastLoad = 0L
    private var defaultExtracted = false
    private const val RELOAD_MS = 5_000L

    init {
        on<TickEvent.ClientEnd> {
            extractDefaultSkin()
        }
    }

    @JvmStatic
    fun shouldUseCustomSkin(id: Int): Boolean {
        if (!enabled) return false
        val player = mc.player ?: return false
        val isSelf = id == player.id
        return if (isSelf) self else others
    }

    @JvmStatic
    fun customSkinTexture(): Identifier? {
        if (!enabled) return null
        val now = System.currentTimeMillis()
        if (cachedTexture == null || now - lastLoad > RELOAD_MS) {
            loadTexture()
            lastLoad = now
        }
        return cachedTexture
    }

    @JvmStatic
    fun state(): Map<String, Any?> {
        val texture = if (enabled) customSkinTexture() else cachedTexture
        return mapOf(
            "enabled" to enabled,
            "settings" to mapOf(
                "self" to self,
                "others" to others,
                "selectedSkin" to selectedSkin,
                "availableSkins" to availableSkins()
            ),
            "texture" to mapOf(
                "cached" to texture?.toString(),
                "skinDir" to skinDir.toString()
            )
        )
    }

    private fun loadTexture() {
        val originalSelection = selectedSkin
        cachedTexture = try {
            extractDefaultSkin()
            val image = readSelectedSkin() ?: readFirstSkin().also { image ->
                if (image != null && selectedSkin.isBlank()) selectedSkin = firstSkinName().orEmpty()
            } ?: readBundledSkin() ?: generateFallbackSkin()
            mc.textureManager.register(dynamicSkin, DynamicTexture({ "floydaddons_skin" }, image))
            saveIfSelectionChanged(originalSelection)
            dynamicSkin
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun reload() {
        cachedTexture = null
        lastLoad = 0L
    }

    fun availableSkinFiles(): List<String> = availableSkins()

    fun selectSkin(name: String): Boolean {
        val skin = availableSkins().firstOrNull { it.equals(name, ignoreCase = true) } ?: return false
        selectedSkin = skin
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Selected skin: $selectedSkin")
        return true
    }

    private fun cycleSkin(direction: Int) {
        val skins = availableSkins()
        if (skins.isEmpty()) {
            modMessage("No custom skin PNG files found.")
            return
        }
        val current = skins.indexOfFirst { it.equals(selectedSkin, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        val next = (current + direction).floorMod(skins.size)
        selectedSkin = skins[next]
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Selected skin: $selectedSkin")
    }

    private fun ensureExternalDir() {
        Files.createDirectories(skinDir)
    }

    private fun extractDefaultSkin() {
        if (defaultExtracted) return
        defaultExtracted = true
        ensureExternalDir()
        val target = skinDir.resolve("george-floyd.png")
        if (Files.exists(target)) return
        mc.resourceManager.getResource(builtinSkin).ifPresent { resource ->
            resource.open().use { input -> Files.copy(input, target) }
        }
    }

    private fun readSelectedSkin(): NativeImage? {
        val selected = selectedSkin.takeIf { it.isNotBlank() } ?: return null
        val path = skinDir.resolve(selected)
        return if (path.isRegularFile()) path.inputStream().use(NativeImage::read) else null
    }

    private fun readFirstSkin(): NativeImage? {
        val name = firstSkinName() ?: return null
        selectedSkin = name
        return skinDir.resolve(name).inputStream().use(NativeImage::read)
    }

    private fun firstSkinName(): String? =
        availableSkins().firstOrNull()

    private fun saveIfSelectionChanged(originalSelection: String) {
        if (selectedSkin != originalSelection) ModuleManager.saveConfigurations()
    }

    private fun availableSkins(): List<String> {
        ensureExternalDir()
        return Files.list(skinDir).use { stream ->
            stream.filter { it.isRegularFile() && it.fileName.toString().lowercase().endsWith(".png") }
                .map { it.fileName.toString() }
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
        }
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun readBundledSkin(): NativeImage? =
        mc.resourceManager.getResource(builtinSkin).map { resource ->
            resource.open().use(NativeImage::read)
        }.orElse(null)

    private fun generateFallbackSkin(): NativeImage {
        val img = NativeImage(64, 64, false)
        val dark = 0xFF1A1612.toInt()
        val skin = 0xFF6A4C36.toInt()
        val stripe1 = 0xFF4A4948.toInt()
        val stripe2 = 0xFF646464.toInt()
        val blue = 0xFF1E223C.toInt()
        for (y in 0 until 64) for (x in 0 until 64) img.setPixel(x, y, dark)
        for (y in 8 until 16) for (x in 20 until 36) img.setPixel(x, y, skin)
        for (y in 20 until 32) for (x in 8 until 56) img.setPixel(x, y, if (y % 4 < 2) stripe2 else stripe1)
        for (y in 32 until 64) for (x in 12 until 52) img.setPixel(x, y, blue)
        return img
    }
}
