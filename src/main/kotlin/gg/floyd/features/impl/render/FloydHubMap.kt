package gg.floyd.features.impl.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.ActionSetting
import gg.floyd.clickgui.settings.impl.StringSetting
import gg.floyd.events.TickEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.modMessage
import gg.floyd.utils.openDirectory
import net.minecraft.client.renderer.state.MapRenderState
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.level.saveddata.maps.MapId
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

object FloydHubMap : Module(
    name = "Custom Hub Map",
    category = Category.RENDER,
    description = "Replaces large map walls with a custom PNG or GIF.",
    toggled = false,
) {
    private const val MAP_COLUMNS = 13
    private const val MAP_ROWS = 7
    private const val MAP_TILE_SIZE = 128
    private const val TOTAL_TILES = MAP_COLUMNS * MAP_ROWS
    private const val SCAN_INTERVAL_TICKS = 20L
    private const val MAX_GIF_FRAMES = 32

    var selectedImage by StringSetting("Image", "", 96, desc = "PNG or GIF file in config/floydaddons/hub-map.")
    private val openFolder by ActionSetting("Open Folder", desc = "Opens config/floydaddons/hub-map.") {
        modMessage(if (openDirectory(imageDir)) "Opened hub map folder." else "Could not open hub map folder: $imageDir")
    }
    private val reload by ActionSetting("Reload", desc = "Reloads the selected hub map image.") {
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Reloaded selected hub map: ${selectedImage.ifBlank { "(default)" }}")
    }
    private val previousImage by ActionSetting("Previous Image", desc = "Selects the previous available hub map image.") { cycleImage(-1) }
    private val nextImage by ActionSetting("Next Image", desc = "Selects the next available hub map image.") { cycleImage(1) }
    private val listImages by ActionSetting("List Images", desc = "Prints available hub map PNG/GIF files in chat.") {
        val images = availableImages()
        modMessage(if (images.isEmpty()) "No hub map PNG/GIF files found." else "Available hub maps:\n${images.joinToString("\n")}")
    }

    private val imageDir: Path = FloydAddonsMod.configFile.toPath().resolve("hub-map")

    private var cachedSelection: String? = null
    private var tileTextures = emptyList<LinearHubMapTexture>()
    private var gifTiles: GifTileSet? = null
    private var mappedTilesById = emptyMap<Int, Int>()
    private var lastScanTick = -100L
    private var textureGeneration = 0L
    private var tileIdentifiers = buildTileIdentifiers()

    init {
        on<TickEvent.ClientEnd> {
            if (!enabled) {
                mappedTilesById = emptyMap()
                return@on
            }
            ensureTilesLoaded()
            gifTiles?.tick()
            scanForHubMap()
        }
    }

    @JvmStatic
    fun applyRenderState(mapId: MapId, state: MapRenderState) {
        if (!enabled) return
        ensureTilesLoaded()
        val tileIndex = mappedTilesById[mapId.id()] ?: return
        state.texture = tileIdentifiers.getOrNull(tileIndex) ?: return
        state.decorations.clear()
    }

    fun state(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "selectedImage" to selectedImage,
        "availableImages" to availableImages(),
        "mappedTileCount" to mappedTilesById.size,
        "tileTextureCount" to tileTextures.size,
        "gif" to gifTiles?.state()
    )

    fun reload() {
        textureGeneration += 1
        tileIdentifiers = buildTileIdentifiers()
        cachedSelection = null
        gifTiles = null
        tileTextures = emptyList()
    }

    fun availableHubMapFiles(): List<String> = availableImages()

    fun selectImage(name: String): Boolean {
        val image = availableImages().firstOrNull { it.equals(name, ignoreCase = true) } ?: return false
        selectedImage = image
        reload()
        ModuleManager.saveConfigurations()
        modMessage("Selected hub map image: $selectedImage")
        return true
    }

    private fun cycleImage(direction: Int) {
        val images = availableImages()
        if (images.isEmpty()) {
            modMessage("No hub map PNG/GIF files found.")
            return
        }
        val current = images.indexOfFirst { it.equals(selectedImage, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        selectImage(images[(current + direction).floorMod(images.size)])
    }

    private fun ensureTilesLoaded() {
        val selection = selectedImage
        if (cachedSelection == selection && tileTextures.size == TOTAL_TILES) return

        ensureExternalDir()
        gifTiles = null

        textureGeneration += 1
        tileIdentifiers = buildTileIdentifiers()

        val originalSelection = selection
        val selectedPath = selection.takeIf { it.isNotBlank() }?.let(imageDir::resolve)?.takeIf { it.isRegularFile() }
        if (selectedPath != null && selectedPath.fileName.toString().lowercase().endsWith(".gif")) {
            readGif(selectedPath)?.let { gif ->
                tileTextures = gif.textures
                gifTiles = gif
                cachedSelection = selectedImage
                saveIfSelectionChanged(originalSelection)
                return
            }
        }

        val image = readSelectedImage() ?: readFirstImage()
        if (tileTextures.size == TOTAL_TILES && cachedSelection == selectedImage) {
            saveIfSelectionChanged(originalSelection)
            return
        }
        val resolvedImage = image ?: return
        val tiles = sliceScaledImage(resolvedImage)
        tileTextures = registerTiles(tiles)
        cachedSelection = selectedImage
        saveIfSelectionChanged(originalSelection)
    }

    private fun scanForHubMap() {
        val level = FloydAddonsMod.mc.level ?: run {
            mappedTilesById = emptyMap()
            return
        }
        val tick = level.gameTime
        if (tick - lastScanTick < SCAN_INTERVAL_TICKS) return
        lastScanTick = tick

        val frames = level.entitiesForRendering()
            .asSequence()
            .filterIsInstance<ItemFrame>()
            .mapNotNull(::wallCell)
            .toList()

        if (frames.isEmpty()) {
            return
        }

        val bestWall = frames
            .groupBy { FramePlane(it.direction, it.depth) }
            .values
            .mapNotNull(::resolveWallBinding)
            .maxByOrNull { it.size }

        updateMappedTiles(bestWall)
    }

    internal fun clearMappedTilesForTest() {
        mappedTilesById = emptyMap()
    }

    internal fun applyResolvedWallBindingForTest(binding: Map<Int, Int>?) {
        updateMappedTiles(binding)
    }

    private fun updateMappedTiles(bestWall: Map<Int, Int>?) {
        if (bestWall != null) {
            mappedTilesById = bestWall
        }
    }

    private fun wallCell(frame: ItemFrame): WallCell? {
        val stack = frame.item
        if (stack.item != net.minecraft.world.item.Items.FILLED_MAP) return null
        val mapId = stack.get(DataComponents.MAP_ID) ?: return null
        val direction = frame.direction
        if (!direction.axis.isHorizontal) return null
        val pos = frame.blockPosition()
        return WallCell(
            mapId = mapId,
            direction = direction,
            depth = if (direction.axis == Direction.Axis.Z) pos.z else pos.x,
            horizontal = when (direction) {
                Direction.SOUTH -> pos.x
                Direction.NORTH -> -pos.x
                Direction.WEST -> pos.z
                Direction.EAST -> -pos.z
                else -> pos.x
            },
            vertical = pos.y
        )
    }

    private fun resolveWallBinding(cells: List<WallCell>): Map<Int, Int>? {
        if (cells.size < TOTAL_TILES) return null
        val horizontalValues = cells.map { it.horizontal }.distinct().sorted()
        val verticalValues = cells.map { it.vertical }.distinct().sortedDescending()
        if (horizontalValues.size != MAP_COLUMNS || verticalValues.size != MAP_ROWS) return null

        val horizontalIndex = horizontalValues.withIndex().associate { it.value to it.index }
        val verticalIndex = verticalValues.withIndex().associate { it.value to it.index }
        val uniqueGrid = HashSet<Pair<Int, Int>>()
        val bindings = HashMap<Int, Int>()

        for (cell in cells) {
            val column = horizontalIndex[cell.horizontal] ?: continue
            val row = verticalIndex[cell.vertical] ?: continue
            if (!uniqueGrid.add(column to row)) return null
            bindings[cell.mapId.id()] = row * MAP_COLUMNS + column
        }

        return if (bindings.size == TOTAL_TILES) bindings else null
    }

    private fun ensureExternalDir() {
        Files.createDirectories(imageDir)
    }

    private fun readSelectedImage(): BufferedImage? {
        val selected = selectedImage.takeIf { it.isNotBlank() } ?: return null
        val path = imageDir.resolve(selected)
        return if (path.isRegularFile() && path.fileName.toString().lowercase().endsWith(".png")) path.inputStream().use(ImageIO::read) else null
    }

    private fun readFirstImage(): BufferedImage? {
        val name = availableImages().firstOrNull() ?: return null
        selectedImage = name
        val path = imageDir.resolve(name)
        if (name.lowercase().endsWith(".gif")) {
            readGif(path)?.let { gif ->
                tileTextures = gif.textures
                gifTiles = gif
                cachedSelection = selectedImage
            }
            return null
        }
        return if (name.lowercase().endsWith(".png")) path.inputStream().use(ImageIO::read) else null
    }

    private fun availableImages(): List<String> {
        ensureExternalDir()
        return Files.list(imageDir).use { stream ->
            stream.filter { it.isRegularFile() && it.fileName.toString().lowercase().let { name -> name.endsWith(".png") || name.endsWith(".gif") } }
                .map { it.fileName.toString() }
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
        }
    }

    private fun registerTiles(images: List<NativeImage>): List<LinearHubMapTexture> {
        return images.mapIndexed { index, image ->
            LinearHubMapTexture({ "floydaddons_hub_map_$index" }, image).also { texture ->
                FloydAddonsMod.mc.textureManager.register(tileIdentifiers[index], texture)
            }
        }
    }

    private fun sliceScaledImage(image: BufferedImage): List<NativeImage> {
        val scaled = BufferedImage(MAP_COLUMNS * MAP_TILE_SIZE, MAP_ROWS * MAP_TILE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        graphics.useBilinearScaling {
            drawImage(image, 0, 0, scaled.width, scaled.height, null)
        }

        return buildList(TOTAL_TILES) {
            for (row in 0 until MAP_ROWS) {
                for (column in 0 until MAP_COLUMNS) {
                    add(nativeImageFromBuffered(scaled, column * MAP_TILE_SIZE, row * MAP_TILE_SIZE))
                }
            }
        }
    }

    private fun nativeImageFromBuffered(image: BufferedImage, startX: Int, startY: Int): NativeImage {
        val native = NativeImage(MAP_TILE_SIZE, MAP_TILE_SIZE, false)
        val pixels = IntArray(MAP_TILE_SIZE * MAP_TILE_SIZE)
        image.getRGB(startX, startY, MAP_TILE_SIZE, MAP_TILE_SIZE, pixels, 0, MAP_TILE_SIZE)
        for (index in pixels.indices) {
            native.setPixel(index % MAP_TILE_SIZE, index / MAP_TILE_SIZE, pixels[index])
        }
        return native
    }

    private fun readGif(path: Path): GifTileSet? {
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
                    if (frameCount > MAX_GIF_FRAMES) {
                        FloydAddonsMod.logger.warn(
                            "Hub map GIF {} has {} frames; using static first-frame fallback to avoid client lag.",
                            path.fileName,
                            frameCount
                        )
                        val first = reader.read(0) ?: return null
                        val firstComposite = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
                        val firstGraphics = firstComposite.createGraphics()
                        try {
                            firstGraphics.drawImage(first, 0, 0, null)
                        } finally {
                            firstGraphics.dispose()
                        }
                        val tiles = sliceScaledImage(firstComposite)
                        return GifTileSet(registerTiles(tiles), listOf(tiles), intArrayOf(100))
                    }

                    val composite = BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB)
                    val compositeGraphics = composite.createGraphics()
                    val tileFrames = mutableListOf<List<NativeImage>>()
                    val delays = mutableListOf<Int>()

                    for (index in 0 until frameCount) {
                        val metadata = reader.getImageMetadata(index)
                        val raw = reader.read(index) ?: continue
                        val frameX = metadata.intAttribute("imageLeftPosition", 0)
                        val frameY = metadata.intAttribute("imageTopPosition", 0)
                        val disposal = metadata.disposalMethod()
                        val before = if (disposal == "restoreToPrevious") copyImage(composite) else null

                        compositeGraphics.drawImage(raw, frameX, frameY, null)
                        tileFrames += sliceScaledImage(composite)
                        delays += metadata.delayMs()

                        if (disposal == "restoreToBackgroundColor") {
                            compositeGraphics.clearRect(frameX, frameY, raw.width, raw.height)
                        } else if (before != null) {
                            compositeGraphics.drawImage(before, 0, 0, null)
                        }
                    }

                    compositeGraphics.dispose()
                    if (tileFrames.isEmpty()) return null

                    val firstFrameCopies = tileFrames.first().map { frame ->
                        NativeImage(frame.width, frame.height, false).also { it.copyFrom(frame) }
                    }
                    val textures = registerTiles(firstFrameCopies)
                    return GifTileSet(textures, tileFrames, delays.toIntArray())
                } finally {
                    reader.dispose()
                }
            }
        }
    }

    private fun copyImage(image: BufferedImage): BufferedImage {
        val copy = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = copy.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return copy
    }

    private fun Graphics2D.useBilinearScaling(block: Graphics2D.() -> Unit) {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        block()
        dispose()
    }

    private fun saveIfSelectionChanged(originalSelection: String?) {
        if (selectedImage != originalSelection) ModuleManager.saveConfigurations()
    }

    private fun buildTileIdentifiers(): Array<Identifier> {
        val generation = textureGeneration
        return Array(TOTAL_TILES) { index ->
            Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "hub_map/${generation}_$index")
        }
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private data class FramePlane(val direction: Direction, val depth: Int)
    private data class WallCell(val mapId: MapId, val direction: Direction, val depth: Int, val horizontal: Int, val vertical: Int)

    private class GifTileSet(
        val textures: List<LinearHubMapTexture>,
        private val frames: List<List<NativeImage>>,
        private val delaysMs: IntArray,
    ) {
        private var frameIndex = 0
        private var lastSwitchMs = System.currentTimeMillis()

        fun tick() {
            if (frames.size <= 1) return
            val delay = delaysMs.getOrElse(frameIndex) { 100 }.takeIf { it > 0 } ?: 100
            val now = System.currentTimeMillis()
            if (now - lastSwitchMs < delay) return
            frameIndex = (frameIndex + 1) % frames.size
            val nextFrame = frames[frameIndex]
            textures.forEachIndexed { index, texture ->
                texture.pixels?.copyFrom(nextFrame[index])
                texture.upload()
            }
            lastSwitchMs = now
        }

        fun state(): Map<String, Any?> = mapOf(
            "frameCount" to frames.size,
            "frameIndex" to frameIndex,
            "currentDelayMs" to delaysMs.getOrElse(frameIndex) { 100 }
        )
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

    private class LinearHubMapTexture(label: () -> String, image: NativeImage) : DynamicTexture(label, image) {
        init {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        }
    }
}
