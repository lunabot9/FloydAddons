package gg.floyd.features.impl.cosmetic

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.BooleanSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.modMessage
import gg.floyd.utils.openDirectory
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

object FloydCape : Module(
    name = "Custom Cape",
    category = Category.COSMETIC,
    description = "Floyd cape rendering and selected cape asset.",
    toggled = false,
) {
    var selectedCape by StringSetting("Image", "", 96, desc = "Cape PNG or GIF file in config/floydaddons/images.")
    private val listCapes by ActionSetting("List Capes", desc = "Prints available cape PNG/GIF files in chat.") {
        val capes = availableCapes()
        modMessage(if (capes.isEmpty()) "No cape PNG/GIF files found." else "Available capes:\n${capes.joinToString("\n")}")
    }
    private val previousCape by ActionSetting("Previous Cape", desc = "Selects the previous available cape image.") {
        cycleCape(-1)
    }
    private val nextCape by ActionSetting("Next Cape", desc = "Selects the next available cape image.") {
        cycleCape(1)
    }
    private val openCapeFolder by ActionSetting("Open Cape Folder", desc = "Opens config/floydaddons/images.") {
        modMessage(if (openDirectory(capeDir)) "Opened cape folder." else "Could not open cape folder: $capeDir")
    }
    private val reloadCape by ActionSetting("Reload Cape", desc = "Reloads the selected cape texture.") {
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Reloaded selected cape: $selectedCape")
    }

    private val capeDir: Path get() = CosmeticImages.dir
    private val builtinCape = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "textures/cape/default_cape.png")
    private val dynamicCape = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "cape/custom")
    private var cachedTexture: Identifier? = null
    private var cachedSelection: String? = null
    private var gifTexture: GifTexture? = null
    private var aspect = 2.0f

    @JvmStatic
    fun isActive(): Boolean = enabled

    @JvmStatic
    fun isActiveFor(id: Int): Boolean = isActive() && FloydAddonsMod.mc.player?.id == id

    @JvmStatic
    fun texture(): Identifier {
        gifTexture?.tick()
        val selected = selectedCape
        if (cachedTexture == null || cachedSelection != selected) loadTexture()
        return cachedTexture ?: builtinCape
    }

    @JvmStatic
    fun aspectRatio(): Float = aspect

    @JvmStatic
    fun state(): Map<String, Any?> {
        texture()
        val gif = gifTexture
        return mapOf(
            "enabled" to enabled,
            "active" to isActive(),
            "selectedCape" to selectedCape,
            "availableCapes" to availableCapes(),
            "texture" to (cachedTexture?.toString() ?: builtinCape.toString()),
            "loadedType" to if (gif != null) "gif" else "image",
            "aspect" to aspect,
            "gif" to gif?.state()
        )
    }

    private fun loadTexture() {
        val originalSelection = selectedCape
        try {
            ensureExternalDir()
            gifTexture = null
            val selected = selectedCape.takeIf { it.isNotBlank() }
            val selectedPath = selected?.let { capeDir.resolve(it) }?.takeIf { it.isRegularFile() }
            if (selectedPath != null && selectedPath.fileName.toString().lowercase().endsWith(".gif")) {
                val gif = readGif(selectedPath)
                if (gif != null) {
                    aspect = gif.aspect
                    gifTexture = gif
                    cachedSelection = selectedCape
                    cachedTexture = dynamicCape
                    saveIfSelectionChanged(originalSelection)
                    return
                }
            }

            val image = readSelectedImage() ?: readFirstImage()
            if (gifTexture != null) {
                cachedTexture = dynamicCape
                saveIfSelectionChanged(originalSelection)
                return
            }
            val resolvedImage = image ?: readBundledImage()
            if (resolvedImage == null) {
                aspect = 2.0f
                cachedSelection = selectedCape
                cachedTexture = null
                saveIfSelectionChanged(originalSelection)
                return
            }
            aspect = (resolvedImage.width.toFloat() / resolvedImage.height.toFloat()).takeIf { it > 0f } ?: 2.0f
            FloydAddonsMod.mc.textureManager.register(dynamicCape, LinearCapeTexture({ "floydaddons_cape" }, resolvedImage))
            cachedSelection = selectedCape
            cachedTexture = dynamicCape
            saveIfSelectionChanged(originalSelection)
        } catch (_: Exception) {
            cachedSelection = null
            cachedTexture = null
        }
    }

    @JvmStatic
    fun reload() {
        cachedTexture = null
        cachedSelection = null
        gifTexture = null
    }

    fun availableCapeFiles(): List<String> = availableCapes()

    fun selectCape(name: String): Boolean {
        val cape = availableCapes().firstOrNull { it.equals(name, ignoreCase = true) } ?: return false
        selectedCape = cape
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Selected cape: $selectedCape")
        return true
    }

    fun cycleCapeFile(direction: Int): Boolean {
        val capes = availableCapes()
        if (capes.isEmpty()) {
            modMessage("No cape PNG/GIF files found.")
            return false
        }
        val current = capes.indexOfFirst { it.equals(selectedCape, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        return selectCape(capes[(current + direction).floorMod(capes.size)])
    }

    private fun cycleCape(direction: Int) {
        cycleCapeFile(direction)
    }

    private fun ensureExternalDir() {
        CosmeticImages.ensureSeeded()
    }

    private fun readSelectedImage(): NativeImage? {
        val selected = selectedCape.takeIf { it.isNotBlank() } ?: return null
        val path = capeDir.resolve(selected)
        return if (path.isRegularFile() && path.fileName.toString().lowercase().endsWith(".png")) path.inputStream().use(NativeImage::read) else null
    }

    private fun readFirstImage(): NativeImage? {
        val name = availableCapes().firstOrNull() ?: return null
        selectedCape = name
        val path = capeDir.resolve(name)
        if (name.lowercase().endsWith(".gif")) {
            val gif = readGif(path) ?: return null
            aspect = gif.aspect
            gifTexture = gif
            cachedSelection = selectedCape
            return null
        }
        return path.inputStream().use(NativeImage::read)
    }

    private fun availableCapes(): List<String> {
        ensureExternalDir()
        return Files.list(capeDir).use { stream ->
            stream.filter { it.isRegularFile() && it.fileName.toString().lowercase().let { name -> name.endsWith(".png") || name.endsWith(".gif") } }
                .map { it.fileName.toString() }
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
        }
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun saveIfSelectionChanged(originalSelection: String) {
        if (selectedCape != originalSelection) ModuleManager.saveConfigurations()
    }

    private fun readBundledImage(): NativeImage? =
        FloydAddonsMod.mc.resourceManager.getResource(builtinCape).map { resource ->
            resource.open().use(NativeImage::read)
        }.orElse(null)

    private fun readGif(path: Path): GifTexture? {
        path.inputStream().use { input ->
            ImageIO.createImageInputStream(input).use { imageStream ->
                if (imageStream == null) return null
                val reader = ImageIO.getImageReadersByFormatName("gif").asSequence().firstOrNull() ?: return null
                try {
                    reader.input = imageStream
                    val frameCount = reader.getNumImages(true)
                    if (frameCount <= 0) return null

                    val canvasWidth = reader.getWidth(0)
                    val canvasHeight = reader.getHeight(0)
                    val composite = NativeImage(canvasWidth, canvasHeight, false)
                    val frames = mutableListOf<NativeImage>()
                    val delays = mutableListOf<Int>()

                    for (index in 0 until frameCount) {
                        val metadata = reader.getImageMetadata(index)
                        val raw = reader.read(index) ?: continue
                        val frameX = metadata.intAttribute("imageLeftPosition", 0)
                        val frameY = metadata.intAttribute("imageTopPosition", 0)
                        val disposal = metadata.disposalMethod()
                        val before = if (disposal == "restoreToPrevious") NativeImage(canvasWidth, canvasHeight, false).also { it.copyFrom(composite) } else null

                        composite.drawFrame(raw, frameX, frameY, canvasWidth, canvasHeight)
                        frames.add(NativeImage(canvasWidth, canvasHeight, false).also { it.copyFrom(composite) })
                        delays.add(metadata.delayMs())

                        if (disposal == "restoreToBackgroundColor") {
                            composite.fillRect(frameX, frameY, raw.width, raw.height, 0)
                        } else if (before != null) {
                            composite.copyFrom(before)
                            before.close()
                        }
                    }

                    composite.close()
                    if (frames.isEmpty()) return null

                    val working = NativeImage(canvasWidth, canvasHeight, false)
                    working.copyFrom(frames.first())
                    val texture = LinearCapeTexture({ "floydaddons_cape_gif" }, working)
                    FloydAddonsMod.mc.textureManager.register(dynamicCape, texture)
                    return GifTexture(texture, frames, delays.toIntArray(), canvasWidth.toFloat() / canvasHeight.toFloat())
                } finally {
                    reader.dispose()
                }
            }
        }
    }

    private fun NativeImage.drawFrame(raw: BufferedImage, frameX: Int, frameY: Int, canvasWidth: Int, canvasHeight: Int) {
        for (y in 0 until raw.height) {
            val destY = frameY + y
            if (destY !in 0 until canvasHeight) continue
            for (x in 0 until raw.width) {
                val destX = canvasWidth - 1 - (frameX + x)
                if (destX !in 0 until canvasWidth) continue
                val argb = raw.getRGB(x, y)
                if ((argb ushr 24) == 0) continue
                setPixel(destX, destY, argb)
            }
        }
    }

    private fun IIOMetadata.delayMs(): Int {
        val delay = graphicControlAttribute("delayTime")?.toIntOrNull()?.times(10)
        return delay?.takeIf { it > 0 } ?: 100
    }

    private fun IIOMetadata.disposalMethod(): String =
        graphicControlAttribute("disposalMethod") ?: "none"

    private fun IIOMetadata.intAttribute(name: String, defaultValue: Int): Int =
        treeNode("ImageDescriptor")?.attributes?.named(name)?.nodeValue?.toIntOrNull() ?: defaultValue

    private fun IIOMetadata.graphicControlAttribute(name: String): String? =
        treeNode("GraphicControlExtension")?.attributes?.named(name)?.nodeValue

    private fun IIOMetadata.treeNode(name: String): Node? {
        val format = nativeMetadataFormatName ?: return null
        return getAsTree(format).findNode(name)
    }

    private fun Node.findNode(name: String): Node? {
        if (nodeName == name) return this
        var child = firstChild
        while (child != null) {
            val found = child.findNode(name)
            if (found != null) return found
            child = child.nextSibling
        }
        return null
    }

    private fun NamedNodeMap.named(name: String): Node? = getNamedItem(name)

    private class GifTexture(
        private val texture: DynamicTexture,
        private val frames: List<NativeImage>,
        private val delaysMs: IntArray,
        val aspect: Float
    ) {
        private var frameIndex = 0
        private var lastSwitchMs = System.currentTimeMillis()

        fun tick() {
            if (frames.size <= 1) return
            val delay = delaysMs.getOrElse(frameIndex) { 100 }.takeIf { it > 0 } ?: 100
            val now = System.currentTimeMillis()
            if (now - lastSwitchMs < delay) return
            frameIndex = (frameIndex + 1) % frames.size
            texture.pixels?.copyFrom(frames[frameIndex])
            texture.upload()
            lastSwitchMs = now
        }

        fun state(): Map<String, Any?> = mapOf(
            "frameCount" to frames.size,
            "frameIndex" to frameIndex,
            "currentDelayMs" to delaysMs.getOrElse(frameIndex) { 100 },
            "aspect" to aspect
        )
    }

    private class LinearCapeTexture(label: () -> String, image: NativeImage) : DynamicTexture(label, image) {
        init {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        }
    }
}
