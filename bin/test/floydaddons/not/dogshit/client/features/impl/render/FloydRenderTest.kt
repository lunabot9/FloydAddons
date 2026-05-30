package floydaddons.not.dogshit.client.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals

class FloydRenderTest {
    @Test
    fun `state exposes Floyd core render settings and effective gates`() {
        val state = FloydRender.state()

        assertEquals(true, state["enabled"])
        assertEquals(false, state["fullChatChroma"])
        assertEquals(false, state["shouldUseFullChatChroma"])
        assertEquals(false, state["borderlessWindowed"])
        assertEquals(null, state["customTime"])
        assertEquals(null, state["customScoreboard"])
        assertEquals(null, state["windowTitle"])

        val titleState = ClickGUIModule.state()
        assertEquals("", titleState["instanceTitle"])
        assertEquals("Minecraft", titleState["effectiveInstanceTitle"])
    }

    @Test
    fun `custom time tick conversion rounds like vendored Floyd`() {
        assertEquals(0L, FloydTimeChanger.customTimeTicks(0f))
        assertEquals(12000L, FloydTimeChanger.customTimeTicks(50f))
        assertEquals(23999L, FloydTimeChanger.customTimeTicks(100f))
        assertEquals(0L, FloydTimeChanger.customTimeTicks(-1f))
        assertEquals(23999L, FloydTimeChanger.customTimeTicks(101f))
    }
}
