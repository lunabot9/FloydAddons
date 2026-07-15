package gg.floyd.utils.media

import com.google.gson.JsonParser
import gg.floyd.features.impl.render.FloydMusicOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WindowsMediaSessionTest {
    @Test
    fun `parses now playing metadata and advances a playing timeline`() {
        val snapshot = WindowsMediaSession.parse(
            """{"available":true,"source":"Spotify.exe","title":"Song","artist":"Artist","album":"Album","positionMs":12000,"durationMs":60000,"playing":true,"artworkKey":"key","artwork":null}""",
            sampledAtMs = 1_000L,
        )

        assertNotNull(snapshot)
        assertEquals("Song", snapshot.title)
        assertEquals("Artist", snapshot.artist)
        assertEquals(14_500L, snapshot.displayedPositionMs(3_500L))
        assertEquals(60_000L, snapshot.displayedPositionMs(100_000L))
    }

    @Test
    fun `repeated stale browser positions do not move playback backwards`() {
        val first = MediaSnapshot(
            available = true,
            artworkKey = "opera|song",
            artworkBase64 = "image",
            positionMs = 82L,
            reportedPositionMs = 82L,
            durationMs = 100_000L,
            playing = true,
            sampledAtMs = 1_000L,
        )
        val second = MediaSnapshot(
            available = true,
            artworkKey = "opera|song",
            positionMs = 82L,
            reportedPositionMs = 82L,
            durationMs = 100_000L,
            playing = true,
            sampledAtMs = 2_000L,
        )

        val reconciled = WindowsMediaSession.reconcile(first, second)

        assertEquals(1_082L, reconciled.positionMs)
        assertEquals(1_582L, reconciled.displayedPositionMs(2_500L))
        assertEquals("image", reconciled.artworkBase64)
    }

    @Test
    fun `a genuine browser seek replaces the interpolated position`() {
        val first = MediaSnapshot(
            available = true,
            artworkKey = "opera|song",
            positionMs = 45_000L,
            reportedPositionMs = 45_000L,
            durationMs = 100_000L,
            playing = true,
            sampledAtMs = 1_000L,
        )
        val seek = MediaSnapshot(
            available = true,
            artworkKey = "opera|song",
            positionMs = 10_000L,
            reportedPositionMs = 10_000L,
            durationMs = 100_000L,
            playing = true,
            sampledAtMs = 2_000L,
        )

        assertEquals(10_000L, WindowsMediaSession.reconcile(first, seek).positionMs)
    }

    @Test
    fun `inactive payload clears the overlay`() {
        val snapshot = WindowsMediaSession.parse("""{"available":false}""", sampledAtMs = 5L)
        assertNotNull(snapshot)
        assertTrue(!snapshot.available)
    }

    @Test
    fun `media controls map to standard Windows virtual keys`() {
        assertEquals(0xAF, WindowsMediaKeys.Action.VOLUME_UP.virtualKey())
        assertEquals(0xAE, WindowsMediaKeys.Action.VOLUME_DOWN.virtualKey())
        assertEquals(0xB0, WindowsMediaKeys.Action.NEXT.virtualKey())
        assertEquals(0xB1, WindowsMediaKeys.Action.PREVIOUS.virtualKey())
        assertEquals(0xB3, WindowsMediaKeys.Action.PLAY_PAUSE.virtualKey())
    }

    @Test
    fun `overlay formats playback time`() {
        assertEquals("0:00", FloydMusicOverlay.formatTime(0L))
        assertEquals("1:23", FloydMusicOverlay.formatTime(83_999L))
        assertEquals("61:01", FloydMusicOverlay.formatTime(3_661_000L))
    }

    @Test
    fun `Windows helper is bundled`() {
        assertNotNull(javaClass.getResource("/assets/floydaddons/media/windows-media-session.ps1"))
    }

    @Test
    fun `Windows helper emits a media sample without turning artwork cleanup into an error`() {
        if (!System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) return
        val script = Files.createTempFile("floyd-media-session-test", ".ps1")
        try {
            javaClass.getResourceAsStream("/assets/floydaddons/media/windows-media-session.ps1")!!.use { input ->
                Files.copy(input, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            val process = ProcessBuilder(
                "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", script.toString(),
            ).start()
            try {
                val executor = Executors.newSingleThreadExecutor()
                val line = try {
                    executor.submit<String?> { process.inputStream.bufferedReader().readLine() }.get(8, TimeUnit.SECONDS)
                } finally {
                    executor.shutdownNow()
                }
                assertNotNull(line)
                assertFalse(line.contains("\"error\""), line)
                val json = JsonParser.parseString(line).asJsonObject
                if (json.get("available")?.asBoolean == true && json.get("thumbnailAvailable")?.asBoolean == true) {
                    assertTrue(json.get("artwork")?.isJsonNull == false, line)
                    assertTrue(json.get("artwork")?.asString?.isNotBlank() == true, line)
                }
            } finally {
                process.destroyForcibly()
            }
        } finally {
            Files.deleteIfExists(script)
        }
    }
}
