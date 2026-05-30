package floydaddons.not.dogshit.client.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals

class FloydHubMapTest {
    @Test
    fun `hub map keeps the last resolved binding when a scan misses frames`() {
        FloydHubMap.clearMappedTilesForTest()
        FloydHubMap.applyResolvedWallBindingForTest(mapOf(10 to 0, 11 to 1, 12 to 2))

        val afterBinding = FloydHubMap.state()["mappedTileCount"] as Int
        assertEquals(3, afterBinding)

        FloydHubMap.applyResolvedWallBindingForTest(null)

        val afterMiss = FloydHubMap.state()["mappedTileCount"] as Int
        assertEquals(3, afterMiss)
    }
}
