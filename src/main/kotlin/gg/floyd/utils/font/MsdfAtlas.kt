package gg.floyd.utils.font

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.logging.LogUtils
import gg.floyd.FloydAddonsMod
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.Identifier
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * Mod-owned MSDF atlas: 1024x1024 RGBA8 pages of fixed 32px cells with 2px gutters, capped at 8
 * pages. Pages are created lazily at first bake on the render thread, registered with the
 * TextureManager under generation-suffixed ids, filled black (RGB=0, A=255 = "far outside" under
 * the median) so gutters never bleed half-coverage halos under LINEAR sampling, and released in
 * lockstep with the owning provider's close().
 */
class MsdfAtlas {
    companion object {
        const val PAGE_SIZE = 1024
        const val CELL_SIZE = 32
        const val GUTTER = 2
        const val MAX_PAGES = 8
        const val PX_RANGE = 4.0

        private val logger = LogUtils.getLogger()
        private val GENERATIONS = AtomicInteger()
        private val LIVE_PAGES = AtomicInteger()

        @JvmStatic
        fun livePageCount(): Int = LIVE_PAGES.get()
    }

    private val generation = GENERATIONS.incrementAndGet()
    private val pages = ArrayList<Page>()
    private val packer = MsdfShelfPacker(PAGE_SIZE, CELL_SIZE, GUTTER, MAX_PAGES)
    private var warnedPageCap = false
    private var closed = false

    fun pageCount(): Int = pages.size

    /** Render thread only: uploads one [CELL_SIZE]x[CELL_SIZE] RGBA cell, or null at the page cap. */
    fun place(cell: ByteBuffer): Slot? {
        if (closed) return null
        val placement = packer.place()
        if (placement == null) {
            // Page cap (R5): never crash — the caller renders the missing glyph; WARN once.
            if (!warnedPageCap) {
                warnedPageCap = true
                logger.warn("[FloydMSDF] atlas generation {} hit the {}-page cap; further glyphs render as missing", generation, MAX_PAGES)
            }
            return null
        }
        while (pages.size <= placement.page) {
            pages.add(Page(pages.size))
        }
        val page = pages[placement.page]
        RenderSystem.getDevice().createCommandEncoder()
            .writeToTexture(page.getTexture(), cell, NativeImage.Format.RGBA, 0, 0, placement.x, placement.y, CELL_SIZE, CELL_SIZE)
        return Slot(page, placement.x, placement.y)
    }

    /** Render thread only (page list is render-thread-owned): page count + per-page occupancy. */
    fun debugState(): Map<String, Any?> = mapOf(
        "generation" to generation,
        "pageCount" to pages.size,
        "maxPages" to MAX_PAGES,
        "cellsPerPage" to packer.cellsPerPage,
        "atCap" to warnedPageCap,
        "pages" to pages.mapIndexed { index, page ->
            val used = packer.usedOnPage(index)
            mapOf(
                "id" to page.id.toString(),
                "usedCells" to used,
                "capacity" to packer.cellsPerPage,
                "occupancy" to used.toFloat() / packer.cellsPerPage,
            )
        },
    )

    fun close() {
        if (closed) return
        closed = true
        val textureManager = Minecraft.getInstance().textureManager
        for (page in pages) {
            textureManager.release(page.id)
            LIVE_PAGES.decrementAndGet()
        }
        pages.clear()
    }

    class Slot(val page: Page, val x: Int, val y: Int)

    inner class Page(index: Int) : AbstractTexture() {
        val id: Identifier = Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "msdf/g${generation}_$index")
        val normalType: RenderType
        val seeThroughType: RenderType
        val polygonOffsetType: RenderType

        init {
            val device = RenderSystem.getDevice()
            val pageTexture = device.createTexture(
                { "Floyd MSDF $id" },
                GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, PAGE_SIZE, PAGE_SIZE, 1, 1,
            )
            texture = pageTexture
            textureView = device.createTextureView(pageTexture)
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            val fill = MemoryUtilFill.blackOpaque(PAGE_SIZE * PAGE_SIZE)
            try {
                device.createCommandEncoder()
                    .writeToTexture(getTexture(), fill, NativeImage.Format.RGBA, 0, 0, 0, 0, PAGE_SIZE, PAGE_SIZE)
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(fill)
            }
            Minecraft.getInstance().textureManager.register(id, this)
            val linear = Supplier<GpuSampler> { RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR) }
            normalType = RenderType.create(
                "floyd_msdf_text",
                RenderSetup.builder(MsdfPipelines.TEXT).withTexture("Sampler0", id, linear).useLightmap().bufferSize(786432).createRenderSetup(),
            )
            seeThroughType = RenderType.create(
                "floyd_msdf_text_see_through",
                RenderSetup.builder(MsdfPipelines.TEXT_SEE_THROUGH).withTexture("Sampler0", id, linear).useLightmap().createRenderSetup(),
            )
            polygonOffsetType = RenderType.create(
                "floyd_msdf_text_polygon_offset",
                RenderSetup.builder(MsdfPipelines.TEXT_POLYGON_OFFSET).withTexture("Sampler0", id, linear).useLightmap().sortOnUpload().createRenderSetup(),
            )
            LIVE_PAGES.incrementAndGet()
        }
    }
}

internal object MemoryUtilFill {
    /**
     * RGBA(0,0,0,255) clear pattern as a heap array: a strided alpha-store loop the JIT
     * vectorizes, instead of [pixels] bounds-checked 4-byte ByteBuffer.put calls. Pure (no
     * natives) so the pattern is unit-testable.
     */
    fun blackOpaqueBytes(pixels: Int): ByteArray {
        val rgba = ByteArray(pixels * 4)
        var alpha = 3
        while (alpha < rgba.size) {
            rgba[alpha] = -1
            alpha += 4
        }
        return rgba
    }

    /**
     * Page creation happens lazily at the first bake landing on a new page — mid-frame on the
     * render thread — so the 4MB clear must be one bulk copy, not a per-pixel put loop.
     */
    fun blackOpaque(pixels: Int): ByteBuffer {
        val buffer = org.lwjgl.system.MemoryUtil.memAlloc(pixels * 4)
        buffer.put(blackOpaqueBytes(pixels))
        buffer.flip()
        return buffer
    }
}
