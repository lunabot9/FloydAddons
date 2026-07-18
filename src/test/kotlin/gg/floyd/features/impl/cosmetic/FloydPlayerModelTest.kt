package gg.floyd.features.impl.cosmetic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydPlayerModelTest {
    @Test
    fun `unknown model indexes safely select the first bundled model`() {
        assertEquals("Tung Tung Sahur", FloydPlayerModelSelection.selectedName(0))
        assertEquals("George Floyd", FloydPlayerModelSelection.selectedName(1))
        assertEquals("Jenny", FloydPlayerModelSelection.selectedName(2))
        assertEquals("Minion", FloydPlayerModelSelection.selectedName(3))
        assertEquals("Tung Tung Sahur", FloydPlayerModelSelection.selectedName(99))
    }

    @Test
    fun `tung tung sahur selector credits ImJoyler`() {
        assertTrue(FloydPlayerModelSelection.modelDescriptions.getValue("Tung Tung Sahur").contains("ImJoyler"))
    }

    @Test
    fun `every vanilla mob model is exposed through the player model selector`() {
        assertEquals(89, VanillaMobCatalog.ids.size)
        assertEquals("copper_golem", FloydPlayerModelSelection.vanillaMobId("Minion"))
        assertEquals("allay", FloydPlayerModelSelection.vanillaMobId("Allay"))
        assertEquals("zombified_piglin", FloydPlayerModelSelection.vanillaMobId("Zombified Piglin"))
        assertTrue(FloydPlayerModelSelection.models.contains("Ender Dragon"))
        assertTrue(FloydPlayerModelSelection.models.contains("Copper Golem"))
    }

    @Test
    fun `heads are hidden only when the custom model is active and showing heads is disabled`() {
        assertTrue(FloydPlayerModel.shouldHideHead(customModelActive = true, hasWornHead = true, showHeads = false))
        assertFalse(FloydPlayerModel.shouldHideHead(customModelActive = true, hasWornHead = true, showHeads = true))
        assertFalse(FloydPlayerModel.shouldHideHead(customModelActive = true, hasWornHead = false, showHeads = false))
        assertFalse(FloydPlayerModel.shouldHideHead(customModelActive = false, hasWornHead = true, showHeads = false))
    }

    @Test
    fun `held item is hidden only for an active tung tung model`() {
        assertTrue(FloydPlayerModel.shouldHideHeldItem(customModelActive = true, selectedModel = "Tung Tung Sahur"))
        assertFalse(FloydPlayerModel.shouldHideHeldItem(customModelActive = false, selectedModel = "Tung Tung Sahur"))
        assertFalse(FloydPlayerModel.shouldHideHeldItem(customModelActive = true, selectedModel = "George Floyd"))
        assertFalse(FloydPlayerModel.shouldHideHeldItem(customModelActive = true, selectedModel = "Jenny"))
        assertFalse(FloydPlayerModel.shouldHideHeldItem(customModelActive = true, selectedModel = "Minion"))
    }

    @Test
    fun `minion is treated as a vanilla mob backed model`() {
        assertEquals("copper_golem", FloydPlayerModelSelection.vanillaMobId("Minion"))
        assertEquals("copper_golem", FloydPlayerModelSelection.vanillaMobId(FloydPlayerModelSelection.selectedName(3)))
    }
}
