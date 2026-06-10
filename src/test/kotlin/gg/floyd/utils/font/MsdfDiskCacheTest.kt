package gg.floyd.utils.font

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MsdfDiskCacheTest {

    private val root: Path = Files.createTempDirectory("floyd-msdf-cache-test")

    @AfterTest
    fun cleanup() {
        root.toFile().deleteRecursively()
    }

    private fun raster(cellSize: Int = CELL): MsdfGlyphRaster = MsdfGlyphRaster(
        scale = 2.5,
        translateX = 0.125,
        translateY = -3.5,
        rgba = ByteArray(cellSize * cellSize * 4) { (it % 251).toByte() },
    )

    // --- key derivation -------------------------------------------------------------------

    @Test
    fun `font key is the sha256 of the font bytes and leaves the buffer untouched`() {
        val buffer = ByteBuffer.wrap("hello".toByteArray())
        val key = MsdfDiskCache.fontKey(buffer)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", key)
        assertEquals(0, buffer.position())
        assertEquals(5, buffer.remaining())
        // Deterministic, and sensitive to the bytes.
        assertEquals(key, MsdfDiskCache.fontKey(ByteBuffer.wrap("hello".toByteArray())))
        assertFalse(key == MsdfDiskCache.fontKey(ByteBuffer.wrap("hellp".toByteArray())))
    }

    @Test
    fun `directory name embeds font key cache version and glyph px size`() {
        val name = MsdfDiskCache.directoryName("abc123", 32)
        assertEquals("abc123-${MsdfDiskCache.CACHE_VERSION}-c32", name)
        // Changing any versioned parameter or the glyph px size must change the directory.
        assertFalse(name == MsdfDiskCache.directoryName("abc123", 48))
        assertFalse(name == MsdfDiskCache.directoryName("def456", 32))
    }

    // --- round trip -----------------------------------------------------------------------

    @Test
    fun `write then read round trips bitmap and metrics`() {
        val cache = assertNotNull(MsdfDiskCache.open(root, "fontkey", CELL))
        val written = raster()
        cache.write(0x41, written)
        val read = assertNotNull(cache.read(0x41))
        assertEquals(written.scale, read.scale)
        assertEquals(written.translateX, read.translateX)
        assertEquals(written.translateY, read.translateY)
        assertContentEquals(written.rgba, read.rgba)
    }

    @Test
    fun `entries survive reopening the cache and are per codepoint`() {
        val cache = assertNotNull(MsdfDiskCache.open(root, "fontkey", CELL))
        cache.write(0x41, raster())
        assertNull(cache.read(0x42))
        val reopened = assertNotNull(MsdfDiskCache.open(root, "fontkey", CELL))
        assertNotNull(reopened.read(0x41))
        // A different font key is a different directory: no cross-font hits.
        val otherFont = assertNotNull(MsdfDiskCache.open(root, "otherkey", CELL))
        assertNull(otherFont.read(0x41))
    }

    @Test
    fun `writes publish atomically leaving no temp files`() {
        val cache = assertNotNull(MsdfDiskCache.open(root, "fontkey", CELL))
        repeat(5) { cache.write(0x41 + it, raster()) }
        val dir = cache.directory()
        val leftovers = dir.listDirectoryEntries().filter { it.fileName.toString().endsWith(".tmp") }
        assertEquals(emptyList(), leftovers)
        assertEquals(5, dir.listDirectoryEntries().size)
    }

    @Test
    fun `mismatched bitmap size is never written`() {
        val cache = assertNotNull(MsdfDiskCache.open(root, "fontkey", CELL))
        cache.write(0x41, raster(cellSize = CELL + 1))
        assertNull(cache.read(0x41))
        assertEquals(emptyList(), cache.directory().listDirectoryEntries())
    }

    // --- corruption ------------------------------------------------------------------------

    @Test
    fun `truncated entry is a miss and self heals`() {
        val cache = assertNotNull(MsdfDiskCache.open(root, "fontkey", CELL))
        cache.write(0x41, raster())
        val entry = cache.directory().listDirectoryEntries().single()
        val bytes = Files.readAllBytes(entry)
        Files.write(entry, bytes.copyOf(bytes.size - 10))
        assertNull(cache.read(0x41))
        assertFalse(entry.exists(), "corrupt entry should be deleted on read")
        // Regenerating (re-writing) after the miss works.
        cache.write(0x41, raster())
        assertNotNull(cache.read(0x41))
    }

    @Test
    fun `corrupted header fields are a miss`() {
        val cache = assertNotNull(MsdfDiskCache.open(root, "fontkey", CELL))
        cache.write(0x41, raster())
        val entry = cache.directory().listDirectoryEntries().single()
        val bytes = Files.readAllBytes(entry)
        bytes[0] = 0x00 // break the magic
        Files.write(entry, bytes)
        assertNull(cache.read(0x41))
    }

    @Test
    fun `entry recorded for another codepoint is a miss`() {
        val cache = assertNotNull(MsdfDiskCache.open(root, "fontkey", CELL))
        cache.write(0x42, raster())
        val entry = cache.directory().listDirectoryEntries().single()
        // Pretend the B entry is the A entry (e.g. torn rename by a broken filesystem).
        Files.move(entry, entry.resolveSibling("u000041.bin"))
        assertNull(cache.read(0x41))
    }

    // --- eviction ---------------------------------------------------------------------------

    @Test
    fun `lru eviction removes oldest font dirs until under budget`() {
        val old = fontDir("old", bytes = 100, ageSeconds = 1000)
        val mid = fontDir("mid", bytes = 100, ageSeconds = 500)
        val fresh = fontDir("fresh", bytes = 100, ageSeconds = 0)

        MsdfDiskCache.evictLru(root, maxBytes = 250)

        assertFalse(old.exists(), "oldest dir should be evicted")
        assertTrue(mid.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `eviction never removes the live font dir`() {
        val keep = fontDir("keep", bytes = 100, ageSeconds = 1000)
        val mid = fontDir("mid", bytes = 100, ageSeconds = 500)
        val fresh = fontDir("fresh", bytes = 100, ageSeconds = 0)

        MsdfDiskCache.evictLru(root, maxBytes = 150, keep = keep)

        assertTrue(keep.exists(), "live dir is never evicted")
        assertFalse(mid.exists())
        assertFalse(fresh.exists())
    }

    @Test
    fun `eviction is a no op under budget or with a missing root`() {
        val dir = fontDir("only", bytes = 100, ageSeconds = 100)
        MsdfDiskCache.evictLru(root, maxBytes = 1000)
        assertTrue(dir.exists())
        MsdfDiskCache.evictLru(root.resolve("does-not-exist"), maxBytes = 1)
    }

    private fun fontDir(name: String, bytes: Int, ageSeconds: Long): Path {
        val dir = Files.createDirectories(root.resolve(name))
        Files.write(dir.resolve("u000041.bin"), ByteArray(bytes))
        Files.setLastModifiedTime(dir, FileTime.fromMillis(System.currentTimeMillis() - ageSeconds * 1000))
        return dir
    }

    private companion object {
        const val CELL = 4
    }
}
