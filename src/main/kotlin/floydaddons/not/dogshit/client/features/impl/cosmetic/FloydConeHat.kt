package floydaddons.not.dogshit.client.features.impl.cosmetic

import com.mojang.blaze3d.platform.NativeImage
import floydaddons.not.dogshit.client.FloydAddonsMod
import floydaddons.not.dogshit.client.clickgui.settings.impl.ActionSetting
import floydaddons.not.dogshit.client.clickgui.settings.impl.NumberSetting
import floydaddons.not.dogshit.client.clickgui.settings.impl.StringSetting
import floydaddons.not.dogshit.client.features.Category
import floydaddons.not.dogshit.client.features.Module
import floydaddons.not.dogshit.client.features.ModuleManager
import floydaddons.not.dogshit.client.utils.modMessage
import floydaddons.not.dogshit.client.utils.openDirectory
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

object FloydConeHat : Module(
    name = "Cone Hat",
    category = Category.COSMETIC,
    description = "Floyd cone hat model rendering.",
    toggled = true,
) {
    var selectedImage by StringSetting("Image", "", 96, desc = "Cone hat PNG file in config/floydaddons/cone-hats.")
    private val listImages by ActionSetting("List Cone Images", desc = "Prints available cone hat PNG files in chat.") {
        val images = availableImages()
        modMessage(if (images.isEmpty()) "No cone hat PNG files found." else "Available cone hat images:\n${images.joinToString("\n")}")
    }
    private val previousImage by ActionSetting("Previous Cone Image", desc = "Selects the previous available cone hat PNG file.") {
        cycleImage(-1)
    }
    private val nextImage by ActionSetting("Next Cone Image", desc = "Selects the next available cone hat PNG file.") {
        cycleImage(1)
    }
    private val openImageFolder by ActionSetting("Open Cone Folder", desc = "Opens config/floydaddons/cone-hats.") {
        modMessage(if (openDirectory(imageDir)) "Opened cone hat folder." else "Could not open cone hat folder: $imageDir")
    }
    private val reloadImage by ActionSetting("Reload Cone Image", desc = "Reloads the selected cone hat texture.") {
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Reloaded selected cone hat image: $selectedImage")
    }
    val height by NumberSetting("Height", 0.45f, 0.1f, 1.5f, 0.05f, desc = "Cone hat height.")
    val radius by NumberSetting("Radius", 0.25f, 0.05f, 0.8f, 0.05f, desc = "Cone hat radius.")
    val yOffset by NumberSetting("Y Offset", -0.5f, -1.5f, 0.5f, 0.05f, desc = "Cone hat vertical offset.")
    val rotation by NumberSetting("Rotation", 0.0f, 0.0f, 360.0f, 1.0f, desc = "Cone hat base rotation.")
    val rotationSpeed by NumberSetting("Spin Speed", 0.0f, 0.0f, 360.0f, 1.0f, desc = "Cone hat rotation speed.")

    private val imageDir: Path = FloydAddonsMod.configFile.toPath().resolve("cone-hats")
    private val builtinTexture = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "textures/entity/cone.png")
    private val dynamicTexture = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "cone/custom")
    private var cachedTexture: Identifier? = null
    private var cachedSelection: String? = null
    private var defaultExtracted = false
    private var spinAngle = 0.0f
    private var lastSpinSampleMs = System.currentTimeMillis()
    private var lastSpinSpeed = 0.0f

    @JvmStatic
    fun isActive(): Boolean = enabled

    @JvmStatic
    fun isActiveFor(id: Int): Boolean = isActive() && FloydAddonsMod.mc.player?.id == id

    @JvmStatic
    fun texture(): Identifier {
        val selected = selectedImage
        if (cachedTexture == null || cachedSelection != selected) loadTexture()
        return cachedTexture ?: builtinTexture
    }

    @JvmStatic fun height(): Float = height
    @JvmStatic fun radius(): Float = radius
    @JvmStatic fun yOffset(): Float = yOffset

    @JvmStatic
    fun currentRotation(): Float {
        val now = System.currentTimeMillis()
        val elapsedSeconds = ((now - lastSpinSampleMs).coerceAtLeast(0L)) / 1000.0f
        if (elapsedSeconds > 0.0f) {
            spinAngle = normalizeRotation(spinAngle + elapsedSeconds * lastSpinSpeed)
            lastSpinSampleMs = now
        }
        lastSpinSpeed = rotationSpeed
        return normalizeRotation(rotation + spinAngle)
    }

    private fun loadTexture() {
        val originalSelection = selectedImage
        cachedTexture = try {
            extractDefault()
            val image = readSelectedImage() ?: readFirstImage() ?: readBundledImage()
            if (image == null) {
                cachedSelection = selectedImage
                saveIfSelectionChanged(originalSelection)
                return
            }
            FloydAddonsMod.mc.textureManager.register(dynamicTexture, DynamicTexture({ "floydaddons_cone" }, image))
            cachedSelection = selectedImage
            saveIfSelectionChanged(originalSelection)
            dynamicTexture
        } catch (_: Exception) {
            cachedSelection = null
            null
        }
    }

    @JvmStatic
    fun reload() {
        cachedTexture = null
        cachedSelection = null
    }

    fun availableImageFiles(): List<String> = availableImages()

    fun selectImage(name: String): Boolean {
        val image = availableImages().firstOrNull { it.equals(name, ignoreCase = true) } ?: return false
        selectedImage = image
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Selected cone hat image: $selectedImage")
        return true
    }

    fun setSetting(name: String, value: Double): Boolean {
        val setting = settings[name] as? NumberSetting<*> ?: return false
        setting.setNumericValue(value)
        ModuleManager.saveConfigurations()
        return true
    }

    @JvmStatic
    fun state(): Map<String, Any?> {
        texture()
        return mapOf(
            "enabled" to enabled,
            "active" to isActive(),
            "selectedImage" to selectedImage,
            "availableImages" to availableImages(),
            "texture" to (cachedTexture?.toString() ?: builtinTexture.toString()),
            "height" to height,
            "radius" to radius,
            "yOffset" to yOffset,
            "rotation" to rotation,
            "rotationSpeed" to rotationSpeed
        )
    }

    private fun cycleImage(direction: Int) {
        val images = availableImages()
        if (images.isEmpty()) {
            modMessage("No cone hat PNG files found.")
            return
        }
        val current = images.indexOfFirst { it.equals(selectedImage, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        val next = (current + direction).floorMod(images.size)
        selectedImage = images[next]
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Selected cone hat image: $selectedImage")
    }

    private fun ensureExternalDir() {
        Files.createDirectories(imageDir)
    }

    private fun extractDefault() {
        if (defaultExtracted) return
        defaultExtracted = true
        ensureExternalDir()
        val target = imageDir.resolve("Floyd.png")
        if (Files.exists(target)) return
        FloydAddonsMod.mc.resourceManager.getResource(builtinTexture).ifPresent { resource ->
            resource.open().use { input -> Files.copy(input, target) }
        }
    }

    private fun readSelectedImage(): NativeImage? {
        val selected = selectedImage.takeIf { it.isNotBlank() } ?: return null
        val path = imageDir.resolve(selected)
        return if (path.isRegularFile()) path.inputStream().use(NativeImage::read) else null
    }

    private fun readFirstImage(): NativeImage? {
        val name = availableImages().firstOrNull() ?: return null
        selectedImage = name
        return imageDir.resolve(name).inputStream().use(NativeImage::read)
    }

    private fun availableImages(): List<String> {
        ensureExternalDir()
        return Files.list(imageDir).use { stream ->
            stream.filter { it.isRegularFile() && it.fileName.toString().lowercase().endsWith(".png") }
                .map { it.fileName.toString() }
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
        }
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun normalizeRotation(value: Float): Float =
        ((value % 360.0f) + 360.0f) % 360.0f

    private fun saveIfSelectionChanged(originalSelection: String) {
        if (selectedImage != originalSelection) ModuleManager.saveConfigurations()
    }

    private fun readBundledImage(): NativeImage? =
        FloydAddonsMod.mc.resourceManager.getResource(builtinTexture).map { resource ->
            resource.open().use(NativeImage::read)
        }.orElse(null)
}
