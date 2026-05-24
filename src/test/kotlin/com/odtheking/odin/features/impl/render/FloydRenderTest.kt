package com.odtheking.odin.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals

class FloydRenderTest {
    @Test
    fun `state exposes Floyd core render settings and effective gates`() {
        val state = FloydRender.state()

        assertEquals(true, state["enabled"])
        assertEquals(false, state["customTime"])
        assertEquals(50f, state["customTimeValue"])
        assertEquals(false, state["shouldUseCustomTime"])
        assertEquals(false, state["customScoreboard"])
        assertEquals(false, state["shouldUseCustomScoreboard"])
        assertEquals(false, state["borderlessWindowed"])
        assertEquals("", state["windowTitle"])
        assertEquals("Minecraft", state["effectiveWindowTitle"])
    }

    @Test
    fun `custom time tick conversion rounds like vendored Floyd`() {
        assertEquals(0L, FloydRender.customTimeTicks(0f))
        assertEquals(12000L, FloydRender.customTimeTicks(50f))
        assertEquals(23999L, FloydRender.customTimeTicks(100f))
        assertEquals(0L, FloydRender.customTimeTicks(-1f))
        assertEquals(23999L, FloydRender.customTimeTicks(101f))
    }
}
