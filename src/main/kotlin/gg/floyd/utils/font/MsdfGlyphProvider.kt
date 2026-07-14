package gg.floyd.utils.font

import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.font.UnbakedGlyph
import com.mojang.logging.LogUtils
import gg.floyd.FloydAddonsMod
import gg.floyd.utils.FloydFontProviders
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArraySet
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.font.glyphs.EmptyGlyph
import net.minecraft.client.gui.font.providers.FreeTypeUtil
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Bitmap
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_Matrix
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType
import org.lwjgl.util.msdfgen.MSDFGen
import org.lwjgl.util.msdfgen.MSDFGenBitmap
import org.lwjgl.util.msdfgen.MSDFGenBounds
import org.lwjgl.util.msdfgen.MSDFGenExt
import org.lwjgl.util.msdfgen.MSDFGenMultichannelConfig
import org.lwjgl.util.msdfgen.MSDFGenTransform
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * MSDF glyph provider with vanilla `TrueTypeGlyphProvider` metric parity: advances/bearings come
 * from FreeType hinted loads under vanilla's exact flags at pixel size `round(size*oversample)`,
 * while msdfgen supplies only the visuals. Construction is CPU-only (runs on the FontManager
 * prepare executor); MSDF generation happens lazily under [generationLock] (shared by the render
 * thread and the ASCII prebake daemon — FreeType faces are not thread-safe, D11(d)) and all GPU
 * work happens only inside `bake()` on the render thread. Rasters are memoized per glyph and
 * persisted in the [MsdfDiskCache] so a cache hit skips msdfgen entirely (still uploads). The
 * font ByteBuffer outlives both the metrics FT face and the msdfgen font handle and is freed in
 * [close], which follows the D11(e) teardown order.
 */
class MsdfGlyphProvider(
    fontBuffer: ByteBuffer,
    size: Float,
    private val oversample: Float,
    shiftX: Float,
    shiftY: Float,
    skip: String,
) : GlyphProvider {

    companion object {
        private val logger = LogUtils.getLogger()

        /** FT_LOAD_BITMAP_METRICS_ONLY | FT_LOAD_NO_BITMAP — vanilla TrueTypeGlyphProvider parity. */
        private const val METRIC_LOAD_FLAGS = 0x400008

        /** ASCII prebake range (D11(d)): printable ASCII, intersected with the font cmap. */
        private const val PREBAKE_FIRST = 0x20
        private const val PREBAKE_LAST = 0x7E

        private val INSTANCES = CopyOnWriteArrayList<MsdfGlyphProvider>()
        private val PROVIDER_IDS = AtomicInteger()
        private val GENERATED_GLYPHS = AtomicInteger()
        private val CACHE_HITS = AtomicInteger()
        private val BAKED_GLYPHS = AtomicInteger()

        @JvmStatic
        fun activeInstanceCount(): Int = INSTANCES.size

        /** Live providers, oldest first (at most two, briefly, during a reload). */
        @JvmStatic
        fun instances(): List<MsdfGlyphProvider> = INSTANCES.toList()

        /** Number of glyphs actually rasterized through msdfgen (disk-cache hits excluded). */
        @JvmStatic
        fun generatedGlyphCount(): Int = GENERATED_GLYPHS.get()

        /** Number of glyph rasters served from the disk cache without running msdfgen. */
        @JvmStatic
        fun cacheHitCount(): Int = CACHE_HITS.get()

        /** Number of glyphs uploaded to the atlas (successful bakes). */
        @JvmStatic
        fun bakedGlyphCount(): Int = BAKED_GLYPHS.get()

        private fun checkMsdf(error: Int, what: String) {
            if (error != MSDFGen.MSDF_SUCCESS) {
                throw IllegalStateException("$what failed: msdfgen error $error")
            }
        }
    }

    private val pixelSize = Math.round(size * oversample)
    private val providerId = PROVIDER_IDS.incrementAndGet()
    private var fontMemory: ByteBuffer? = fontBuffer
    private var face: FT_Face? = null
    private var msdfFont = 0L
    private val shiftDeltaX = shiftX * oversample
    private val shiftDeltaY = -shiftY * oversample
    private var unitsPerEm = 0
    private val glyphs = Int2ObjectOpenHashMap<GlyphEntry>()
    private var atlas: MsdfAtlas? = null

    /**
     * The provider generation lock (D11(d)): guards the msdfgen handle + metrics FT face (and the
     * raster memo maps) across the render thread, the reload sweeps and the prebake daemon. The
     * vanilla metrics lock discipline (volatile entry + synchronized double-check) is kept, just
     * widened from the FT face object to this shared lock.
     */
    private val generationLock = Any()

    /** CPU rasters produced (by prebake or a prior bake attempt) but not yet uploaded. */
    private val pendingRasters = Int2ObjectOpenHashMap<MsdfGlyphRaster>()

    /** Codepoints already consumed by bake — the prebake memo never re-produces these. */
    private val rasterConsumed = IntOpenHashSet()

    /**
     * Codepoint -> already-uploaded baked glyph. Vanilla `FontManager.updateOptions` (Force
     * Unicode / Japanese-variant toggles) calls `FontSet.reload` WITHOUT recreating providers, so
     * every visible glyph re-bakes against this same provider. The shelf packer is monotonic
     * (cells are never reclaimed), so re-baking must reuse the cell uploaded by the first bake —
     * otherwise each toggle leaks a full working set of cells until the 8-page cap permanently
     * eats all new glyphs. [FloydMsdfGlyph] is immutable and its atlas page/texture view live
     * exactly as long as this provider, so returning the same instance to every FontSet is safe.
     * Guarded by [generationLock]; cleared (with the atlas it points into) only in [close].
     */
    private val bakedMemo = Int2ObjectOpenHashMap<FloydMsdfGlyph>()

    private var prebakeThread: Thread? = null

    private val cacheRoot: Path? = runCatching {
        FloydAddonsMod.configFile.toPath().resolve("msdf-cache")
    }.getOrNull()
    private val diskCache: MsdfDiskCache?

    @Volatile
    private var closed = false

    @Volatile
    private var warnedBakeFailure = false

    @Volatile
    private var warnedPrebakeFailure = false

    @Volatile
    private var prebakeFinished = false

    init {
        var initialized = false
        try {
            diskCache = cacheRoot?.let { root ->
                runCatching { MsdfDiskCache.open(root, MsdfDiskCache.fontKey(fontBuffer), MsdfAtlas.CELL_SIZE) }.getOrNull()
            }
            synchronized(FreeTypeUtil.LIBRARY_LOCK) {
                MemoryStack.stackPush().use { stack ->
                    val facePointer = stack.mallocPointer(1)
                    FreeTypeUtil.assertError(
                        FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), fontBuffer, 0L, facePointer),
                        "Initializing font face",
                    )
                    face = FT_Face.create(facePointer.get())
                }
                val ftFace = face!!
                FreeTypeUtil.assertError(
                    FreeType.FT_Select_Charmap(ftFace, FreeType.FT_ENCODING_UNICODE),
                    "Find unicode charmap",
                )
                val skipSet = IntArraySet()
                skip.codePoints().forEach(skipSet::add)
                unitsPerEm = ftFace.units_per_EM().toInt()
                FreeType.FT_Set_Pixel_Sizes(ftFace, pixelSize, pixelSize)
                MemoryStack.stackPush().use { stack ->
                    val delta = FreeTypeUtil.setVector(FT_Vector.malloc(stack), shiftX * oversample, -shiftY * oversample)
                    FreeType.FT_Set_Transform(ftFace, null, delta)
                    val glyphIndex = stack.mallocInt(1)
                    var codepoint = FreeType.FT_Get_First_Char(ftFace, glyphIndex)
                    while (true) {
                        val index = glyphIndex.get(0)
                        if (index == 0) break
                        val cp = codepoint.toInt()
                        if (!skipSet.contains(cp)) {
                            glyphs.put(cp, GlyphEntry(index))
                        }
                        codepoint = FreeType.FT_Get_Next_Char(ftFace, codepoint, glyphIndex)
                    }
                }
            }
            MemoryStack.stackPush().use { stack ->
                val fontPointer = stack.mallocPointer(1)
                checkMsdf(MSDFGenExt.msdf_ft_load_font_data(MsdfNative.handle(), fontBuffer, fontPointer), "msdf_ft_load_font_data")
                msdfFont = fontPointer.get(0)
            }
            initialized = true
            INSTANCES.add(this)
        } finally {
            if (!initialized) {
                freeNativeResources()
            }
        }
    }

    /**
     * Starts the async ASCII prebake daemon (D11(d)). Called by the loader AFTER construction
     * completes — never from the ctor — so the thread only ever sees a fully published provider.
     * The thread produces CPU bitmaps + disk-cache entries ONLY; uploads happen at bake.
     */
    fun startPrebake() {
        synchronized(generationLock) {
            if (closed || prebakeThread != null) return
            val thread = Thread({ runPrebake() }, "FloydMSDF-Prebake-$providerId")
            thread.isDaemon = true
            prebakeThread = thread
            thread.start()
        }
    }

    override fun getGlyph(codepoint: Int): UnbakedGlyph? {
        val entry = glyphs.get(codepoint) ?: return null
        var unbaked = entry.glyph
        if (unbaked == null) {
            synchronized(generationLock) {
                unbaked = entry.glyph
                if (unbaked == null) {
                    // Face resolution happens under the generation lock: close() frees the FT
                    // face under the same lock, so a metrics load can never race the teardown.
                    val ftFace = validateFontOpen()
                    unbaked = loadGlyphMetrics(codepoint, ftFace, entry.index)
                    entry.glyph = unbaked
                }
            }
        }
        return unbaked
    }

    override fun getSupportedGlyphs(): IntSet = glyphs.keys

    /**
     * D11(e): set the closed flag under the generation lock, cancel-and-join the prebake, then
     * tear down msdfgen font -> FT face -> font buffer (the process-global msdfgen FT handle is
     * shared and intentionally never destroyed). Atlas pages are released on the render thread
     * (FontManager.apply calls close() there).
     */
    override fun close() {
        val thread: Thread?
        synchronized(generationLock) {
            if (closed) return
            closed = true
            thread = prebakeThread
            prebakeThread = null
        }
        thread?.interrupt()
        if (thread != null) {
            try {
                thread.join()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        synchronized(generationLock) {
            freeNativeResources()
            pendingRasters.clear()
            // The memoized glyphs reference the atlas pages released just below; closed=true
            // already guarantees bakeGlyph can never hand one out again.
            bakedMemo.clear()
        }
        atlas?.close()
        atlas = null
        INSTANCES.remove(this)
    }

    /** Debug snapshot for /fontdebug (page count + occupancy live on the render thread). */
    fun debugState(): Map<String, Any?> = mapOf(
        "providerId" to providerId,
        "closed" to closed,
        "cmapSize" to glyphs.size,
        "pixelSize" to pixelSize,
        "oversample" to oversample,
        "cacheDir" to diskCache?.directory()?.toString(),
        "prebakeStarted" to synchronized(generationLock) { prebakeThread != null || prebakeFinished },
        "prebakeFinished" to prebakeFinished,
        "pendingRasters" to synchronized(generationLock) { pendingRasters.size },
        "memoizedBakes" to synchronized(generationLock) { bakedMemo.size },
        "atlas" to (atlas?.debugState() ?: mapOf("pageCount" to 0, "pages" to emptyList<Any?>())),
    )

    private fun runPrebake() {
        try {
            // One bounded-LRU sweep per provider generation, off-thread, before any new writes.
            val root = cacheRoot
            if (root != null) {
                MsdfDiskCache.evictLru(root, MsdfDiskCache.DEFAULT_MAX_CACHE_BYTES, diskCache?.directory())
            }
            for (codepoint in PREBAKE_FIRST..PREBAKE_LAST) {
                if (closed || Thread.currentThread().isInterrupted) return
                prebakeOne(codepoint)
            }
        } catch (t: Throwable) {
            if (!closed && !warnedPrebakeFailure) {
                warnedPrebakeFailure = true
                logger.warn("[FloydMSDF] ASCII prebake aborted; glyphs will generate lazily at bake", t)
            }
        } finally {
            prebakeFinished = true
        }
    }

    private fun prebakeOne(codepoint: Int) {
        if (!glyphs.containsKey(codepoint)) return
        // Metrics-first mirrors getGlyph: empty outlines (space) are EmptyGlyph and never bake.
        val unbaked = getGlyph(codepoint)
        if (unbaked !is MsdfUnbakedGlyph) return
        synchronized(generationLock) {
            if (closed || pendingRasters.containsKey(codepoint) || rasterConsumed.contains(codepoint)) return
        }
        // Disk I/O stays OUTSIDE the generation lock so cache reads/writes never stall the render
        // thread's metric loads; only the msdfgen call itself needs the lock.
        val cached = diskCache?.read(codepoint)
        val raster: MsdfGlyphRaster
        if (cached != null) {
            CACHE_HITS.incrementAndGet()
            raster = cached
        } else {
            raster = synchronized(generationLock) {
                if (closed || msdfFont == 0L) return
                MemoryStack.stackPush().use { stack -> rasterizeGlyph(codepoint, stack) }
            }
            diskCache?.write(codepoint, raster)
        }
        synchronized(generationLock) {
            if (!closed && !rasterConsumed.contains(codepoint) && !pendingRasters.containsKey(codepoint)) {
                pendingRasters.put(codepoint, raster)
            }
        }
    }

    private fun loadGlyphMetrics(codepoint: Int, ftFace: FT_Face, index: Int): UnbakedGlyph {
        val error = FreeType.FT_Load_Glyph(ftFace, index, METRIC_LOAD_FLAGS)
        if (error != 0) {
            FreeTypeUtil.assertError(error, String.format(Locale.ROOT, "Loading glyph U+%06X", codepoint))
        }
        val slot = ftFace.glyph()
            ?: throw NullPointerException(String.format(Locale.ROOT, "Glyph U+%06X not initialized", codepoint))
        val advance = FreeTypeUtil.x(slot.advance())
        val bitmap = slot.bitmap()
        return if (bitmap.width() > 0 && bitmap.rows() > 0) {
            MsdfUnbakedGlyph(codepoint, GlyphInfo.simple(advance / oversample))
        } else {
            EmptyGlyph(advance / oversample)
        }
    }

    /**
     * Render thread only. Resolves the glyph raster (prebake memo -> disk cache -> msdfgen) and
     * uploads it to the atlas. A disk-cache or memo hit skips msdfgen entirely.
     */
    internal fun bakeGlyph(codepoint: Int, glyphInfo: GlyphInfo): BakedGlyph? {
        val memoized: MsdfGlyphRaster? = synchronized(generationLock) {
            if (closed || msdfFont == 0L) return null
            // Re-bake of an already-uploaded codepoint (FontSet.reload with this provider
            // surviving): reuse the existing atlas cell/UVs instead of placing a new one.
            bakedMemo.get(codepoint)?.let { return it }
            rasterConsumed.add(codepoint)
            pendingRasters.remove(codepoint)
        }
        val raster: MsdfGlyphRaster
        if (memoized != null) {
            raster = memoized
        } else {
            val cached = diskCache?.read(codepoint)
            if (cached != null) {
                CACHE_HITS.incrementAndGet()
                raster = cached
            } else {
                raster = synchronized(generationLock) {
                    if (closed || msdfFont == 0L) return null
                    MemoryStack.stackPush().use { stack -> rasterizeGlyph(codepoint, stack) }
                }
                diskCache?.write(codepoint, raster)
            }
        }
        val baked = uploadGlyph(raster, glyphInfo) ?: return null
        synchronized(generationLock) {
            // At the page cap upload returns null and nothing is memoized (a later bake retries);
            // after close() the early-return above already prevents handing out freed pages.
            if (!closed) bakedMemo.put(codepoint, baked)
        }
        return baked
    }

    /**
     * CPU-only msdfgen rasterization (caller holds [generationLock]). The autoframe/encode math is
     * the live-verified P0 path, unchanged: no orient_contours (FreeType outlines already carry
     * correct winding, and the scanline heuristic INVERTS glyphs with overlapping contours like
     * 't'), and generation goes through msdf_generate_msdf_with_config with overlap support +
     * EDGE_PRIORITY/AT_EDGE error correction (the no-config call leaves both disabled).
     */
    private fun rasterizeGlyph(codepoint: Int, stack: MemoryStack): MsdfGlyphRaster {
        val shapePointer = stack.mallocPointer(1)
        checkMsdf(
            MSDFGenExt.msdf_ft_font_load_glyph(msdfFont, codepoint, MSDFGenExt.MSDF_FONT_SCALING_EM_NORMALIZED, null, shapePointer),
            "msdf_ft_font_load_glyph",
        )
        val shape = shapePointer.get(0)
        try {
            checkMsdf(MSDFGen.msdf_shape_normalize(shape), "msdf_shape_normalize")
            checkMsdf(MSDFGen.msdf_shape_edge_colors_simple(shape, 3.0), "msdf_shape_edge_colors_simple")

            val bounds = MSDFGenBounds.malloc(stack)
            checkMsdf(MSDFGen.msdf_shape_get_bounds(shape, bounds), "msdf_shape_get_bounds")
            val cell = MsdfAtlas.CELL_SIZE
            val boundsLeft = bounds.l()
            val boundsBottom = bounds.b()
            val width = max(bounds.r() - boundsLeft, 1.0e-6)
            val height = max(bounds.t() - boundsBottom, 1.0e-6)
            val pad = MsdfAtlas.PX_RANGE / 2.0 + 1.0
            val scale = min((cell - 2.0 * pad) / width, (cell - 2.0 * pad) / height)
            val translateX = -boundsLeft + pad / scale + ((cell - 2.0 * pad) / scale - width) / 2.0
            val translateY = -boundsBottom + pad / scale + ((cell - 2.0 * pad) / scale - height) / 2.0
            val rangeShapeUnits = MsdfAtlas.PX_RANGE / scale

            val transform = MSDFGenTransform.calloc(stack)
            transform.scale().set(scale, scale)
            transform.translation().set(translateX, translateY)
            transform.distance_mapping().set(-rangeShapeUnits / 2.0, rangeShapeUnits / 2.0)

            val generatorConfig = MSDFGenMultichannelConfig.calloc(stack)
                .overlap_support(MSDFGen.MSDF_TRUE)
                .mode(MSDFGen.MSDF_ERROR_CORRECTION_MODE_EDGE_PRIORITY)
                .distance_check_mode(MSDFGen.MSDF_DISTANCE_CHECK_MODE_AT_EDGE)
                .min_deviation_ratio(1.11111111111111111)
                .min_improve_ratio(1.11111111111111111)

            val bitmap = MSDFGenBitmap.calloc(stack)
            checkMsdf(MSDFGen.msdf_bitmap_alloc(MSDFGen.MSDF_BITMAP_TYPE_MSDF, cell, cell, bitmap), "msdf_bitmap_alloc")
            try {
                checkMsdf(
                    MSDFGen.msdf_generate_msdf_with_config(bitmap, shape, transform, generatorConfig),
                    "msdf_generate_msdf_with_config",
                )
                val pixelsPointer = stack.mallocPointer(1)
                checkMsdf(MSDFGen.msdf_bitmap_get_pixels(bitmap, pixelsPointer), "msdf_bitmap_get_pixels")
                val pixels = MemoryUtil.memFloatBuffer(pixelsPointer.get(0), cell * cell * 3)

                correctSignsAgainstCoverage(codepoint, pixels, cell, scale, translateX, translateY, stack)

                // msdfgen bitmaps are bottom-up: flip rows here so cached + uploaded data are
                // top-down (the y-flip is part of CACHE_VERSION).
                val rgba = ByteArray(cell * cell * 4)
                var out = 0
                for (row in 0 until cell) {
                    val sourceRow = cell - 1 - row
                    for (column in 0 until cell) {
                        val source = (sourceRow * cell + column) * 3
                        rgba[out++] = encodeMsdfChannel(pixels.get(source))
                        rgba[out++] = encodeMsdfChannel(pixels.get(source + 1))
                        rgba[out++] = encodeMsdfChannel(pixels.get(source + 2))
                        rgba[out++] = 255.toByte()
                    }
                }
                GENERATED_GLYPHS.incrementAndGet()
                return MsdfGlyphRaster(scale, translateX, translateY, rgba)
            } finally {
                MSDFGen.msdf_bitmap_free(bitmap)
            }
        } finally {
            MSDFGen.msdf_shape_free(shape)
        }
    }

    /**
     * Sign-corrects the generated field against a FreeType nonzero-winding coverage raster.
     * Variable-font instancing leaves tiny inverted loops at stroke junctions (the bundled
     * Inter's 'M'/'u' apexes); the distance-based median reads those as holes inside the ink.
     * msdf-atlas-gen fixes this with its scanline pass, which the msdfgen C API does not
     * expose — FreeType's rasterizer (nonzero fill, the same authority vanilla TTF rendering
     * uses) provides the ground truth instead. Texels with decisive coverage that disagree
     * with the median are pulled just past the 0.5 threshold; anti-aliased edge texels
     * (mid-gray coverage) are left untouched so real edges keep their gradients.
     */
    private fun correctSignsAgainstCoverage(
        codepoint: Int,
        pixels: java.nio.FloatBuffer,
        cell: Int,
        scale: Double,
        translateX: Double,
        translateY: Double,
        stack: MemoryStack,
    ) {
        val ftFace = face ?: return
        if (unitsPerEm <= 0) return
        val coverage = MemoryUtil.memCalloc(cell * cell)
        try {
            synchronized(FreeTypeUtil.LIBRARY_LOCK) {
                FreeType.FT_Set_Transform(ftFace, null, null)
                try {
                    val loadError = FreeType.FT_Load_Glyph(
                        ftFace,
                        glyphs.get(codepoint)?.index ?: return,
                        FreeType.FT_LOAD_NO_SCALE or FreeType.FT_LOAD_NO_BITMAP,
                    )
                    if (loadError != 0) return
                    val outline = ftFace.glyph()?.outline() ?: return
                    val matrix = FT_Matrix.calloc(stack)
                    val fixedScale = Math.round(65536.0 * 64.0 * scale / unitsPerEm)
                    matrix.xx(fixedScale).yy(fixedScale).xy(0).yx(0)
                    FreeType.FT_Outline_Transform(outline, matrix)
                    FreeType.FT_Outline_Translate(
                        outline,
                        Math.round(translateX * scale * 64.0),
                        Math.round(translateY * scale * 64.0),
                    )
                    val ftBitmap = FT_Bitmap.calloc(stack)
                    val address = ftBitmap.address()
                    MemoryUtil.memPutInt(address + FT_Bitmap.ROWS, cell)
                    MemoryUtil.memPutInt(address + FT_Bitmap.WIDTH, cell)
                    MemoryUtil.memPutInt(address + FT_Bitmap.PITCH, cell)
                    MemoryUtil.memPutAddress(address + FT_Bitmap.BUFFER, MemoryUtil.memAddress(coverage))
                    MemoryUtil.memPutShort(address + FT_Bitmap.NUM_GRAYS, 256.toShort())
                    MemoryUtil.memPutByte(address + FT_Bitmap.PIXEL_MODE, FreeType.FT_PIXEL_MODE_GRAY.toByte())
                    if (FreeType.FT_Outline_Get_Bitmap(FreeTypeUtil.getLibrary(), outline, ftBitmap) != 0) return
                } finally {
                    // Restore the metrics transform (vanilla shift parity, D2).
                    val delta = FreeTypeUtil.setVector(FT_Vector.malloc(stack), shiftDeltaX, shiftDeltaY)
                    FreeType.FT_Set_Transform(ftFace, null, delta)
                }
            }
            for (index in 0 until cell * cell) {
                // msdfgen rows are bottom-up; FT_Outline_Get_Bitmap with positive pitch is top-down.
                val coverageIndex = (cell - 1 - index / cell) * cell + index % cell
                val cov = coverage.get(coverageIndex).toInt() and 0xFF
                if (cov in 64..191) continue
                val inside = cov >= 192
                val base = index * 3
                val r = pixels.get(base)
                val g = pixels.get(base + 1)
                val b = pixels.get(base + 2)
                val median = max(min(r, g), min(max(r, g), b))
                if (inside != median > 0.5f) {
                    val corrected = if (inside) 0.75f else 0.25f
                    pixels.put(base, corrected)
                    pixels.put(base + 1, corrected)
                    pixels.put(base + 2, corrected)
                }
            }
        } finally {
            MemoryUtil.memFree(coverage)
        }
    }

    /** Render thread only: uploads a raster into the atlas and builds the baked glyph. */
    private fun uploadGlyph(raster: MsdfGlyphRaster, glyphInfo: GlyphInfo): FloydMsdfGlyph? {
        val cell = MsdfAtlas.CELL_SIZE
        val liveAtlas = atlas ?: MsdfAtlas().also { atlas = it }
        val buffer = MemoryUtil.memAlloc(raster.rgba.size)
        try {
            buffer.put(raster.rgba)
            buffer.flip()
            val slot = liveAtlas.place(buffer) ?: return null

            val emScale = pixelSize.toDouble() / oversample
            val cellEm = cell / raster.scale
            val left = (emScale * -raster.translateX).toFloat()
            val right = (emScale * (cellEm - raster.translateX)).toFloat()
            val up = (7.0 - emScale * (cellEm - raster.translateY)).toFloat()
            val down = (7.0 + emScale * raster.translateY).toFloat()
            BAKED_GLYPHS.incrementAndGet()
            return FloydMsdfGlyph(
                glyphInfo,
                slot.page,
                slot.x / MsdfAtlas.PAGE_SIZE.toFloat(),
                (slot.x + cell) / MsdfAtlas.PAGE_SIZE.toFloat(),
                slot.y / MsdfAtlas.PAGE_SIZE.toFloat(),
                (slot.y + cell) / MsdfAtlas.PAGE_SIZE.toFloat(),
                left, right, up, down,
            )
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun validateFontOpen(): FT_Face =
        face ?: throw IllegalStateException("Provider already closed")

    private fun freeNativeResources() {
        if (msdfFont != 0L) {
            MSDFGenExt.msdf_ft_font_destroy(msdfFont)
            msdfFont = 0L
        }
        face?.let { ftFace ->
            synchronized(FreeTypeUtil.LIBRARY_LOCK) {
                FreeTypeUtil.checkError(FreeType.FT_Done_Face(ftFace), "Deleting face")
            }
        }
        face = null
        fontMemory?.let { MemoryUtil.memFree(it) }
        fontMemory = null
    }

    private class GlyphEntry(val index: Int) {
        @Volatile
        var glyph: UnbakedGlyph? = null
    }

    private inner class MsdfUnbakedGlyph(
        private val codepoint: Int,
        private val glyphInfo: GlyphInfo,
    ) : UnbakedGlyph {

        override fun info(): GlyphInfo = glyphInfo

        override fun bake(stitcher: UnbakedGlyph.Stitcher): BakedGlyph {
            return try {
                bakeGlyph(codepoint, glyphInfo) ?: stitcher.missing
            } catch (t: Throwable) {
                // D1(c): mid-session generation failure -> missing glyph now, and a one-shot
                // debounced reload that forces the vanilla TTF fallback. Latched once per
                // provider so a stale provider racing the reload cannot re-trigger it.
                if (!warnedBakeFailure) {
                    warnedBakeFailure = true
                    logger.warn("[FloydMSDF] glyph generation failed (U+{}), rendering missing glyph and scheduling a vanilla-font reload", String.format(Locale.ROOT, "%06X", codepoint), t)
                    FloydFontProviders.onMsdfGenerationFailure()
                }
                stitcher.missing
            }
        }
    }
}
