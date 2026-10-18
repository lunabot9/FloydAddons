package gg.floyd.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveTranscriptPreviewTest {
    @Test fun revealsWordsIndividually() {
        var now = 0L
        val preview = LiveTranscriptPreview { now }
        preview.submit("Please search Minecraft updates")
        assertEquals("Please", preview.text())
        now = 65
        assertEquals("Please search", preview.text())
        now = 195
        assertEquals("Please search Minecraft updates", preview.text())
    }

    @Test fun revisesUnstableWordsWithoutRepeatingTheStablePrefix() {
        var now = 0L
        val preview = LiveTranscriptPreview { now }
        preview.submit("search a web")
        now = 200
        assertEquals("search a web", preview.text())
        preview.submit("search the web please")
        assertEquals("search", preview.text())
        now += 65
        assertEquals("search the", preview.text())
        preview.submit("")
        assertEquals("", preview.text())
    }
}
