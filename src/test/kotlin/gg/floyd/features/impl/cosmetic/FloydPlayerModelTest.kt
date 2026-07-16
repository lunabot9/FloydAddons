package gg.floyd.features.impl.cosmetic

import kotlin.test.Test
import kotlin.test.assertEquals

class FloydPlayerModelTest {
    @Test
    fun `unknown model indexes safely select the first bundled model`() {
        assertEquals("Tung Tung Sahur", FloydPlayerModelSelection.selectedName(0))
        assertEquals("George Floyd", FloydPlayerModelSelection.selectedName(1))
        assertEquals("Jenny", FloydPlayerModelSelection.selectedName(2))
        assertEquals("Tung Tung Sahur", FloydPlayerModelSelection.selectedName(99))
    }
}
