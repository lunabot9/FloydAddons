package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals

class FloydInventoryHudCountTest {
    @Test
    fun `stack count text follows an in-place count change on the safe HUD path`() {
        val cache = InventoryHudCountTextCache()

        assertEquals("39", cache.text(26, 39))
        assertEquals("63", cache.text(26, 63))
    }
}
