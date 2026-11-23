package gg.floyd.utils.sound

import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.lwjgl.system.MemoryUtil
import kotlin.test.*

class LocalMp3SoundTest {
    @TempDir lateinit var directory: Path

    @Test fun `sound catalog includes Default cycles both directions and ignores non MP3 files`() {
        val catalog = LocalMp3Catalog(directory)
        Files.write(directory.resolve("zeta.MP3"), byteArrayOf(1))
        Files.write(directory.resolve("Alpha.mp3"), byteArrayOf(1))
        Files.write(directory.resolve("ignored.txt"), byteArrayOf(1))
        Files.createDirectory(directory.resolve("folder.mp3"))
        assertEquals(listOf("Default", "Alpha.mp3", "zeta.MP3"), catalog.choices())
        assertEquals("Alpha.mp3", catalog.cycle("Default", 1))
        assertEquals("Default", catalog.cycle("zeta.MP3", 1))
        assertEquals("zeta.MP3", catalog.cycle("Default", -1))
        assertNull(catalog.resolve("../Alpha.mp3"))
        assertNull(catalog.resolve("missing.mp3"))
        assertNull(catalog.resolve("Default"))
        assertEquals(directory.resolve("Alpha.mp3"), catalog.resolve("Alpha.mp3"))
        Files.delete(directory.resolve("Alpha.mp3"))
        assertNull(catalog.resolve("Alpha.mp3"))
    }

    @Test fun `real MP3 streams non silent stereo PCM with aligned buffers and a clean EOF`() {
        val path = directory.resolve("tone.mp3")
        javaClass.getResourceAsStream("/audio/generated-tone.mp3")!!.use { Files.copy(it, path) }
        var total = 0
        var nonSilent = false
        Mp3AudioStream(path).use { stream ->
            assertEquals(44100f, stream.format.sampleRate)
            assertEquals(2, stream.format.channels)
            assertEquals(16, stream.format.sampleSizeInBits)
            repeat(200) {
                val buffer = stream.read(1025)
                try {
                    assertEquals(0, buffer.remaining() % 4)
                    total += buffer.remaining()
                    while (buffer.hasRemaining()) if (buffer.get().toInt() != 0) nonSilent = true
                } finally { MemoryUtil.memFree(buffer) }
            }
        }
        assertTrue(total > 10_000)
        assertTrue(nonSilent)
        Files.delete(path) // stream closed its file handle
    }

    @Test fun `invalid MP3 fails cleanly and releases the file handle`() {
        val path = directory.resolve("broken.mp3")
        Files.write(path, byteArrayOf(0, 1, 2, 3))
        assertFailsWith<IOException> { Mp3AudioStream(path) }
        Files.delete(path)
    }
}
