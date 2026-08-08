package gg.floyd.clickgui.settings.impl

import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import kotlin.test.Test
import kotlin.test.assertTrue

class StringSettingTest {
    @Test
    fun `clicking the rendered row focuses the editor and accepts typed text`() {
        val setting = StringSetting("Scoreboard Larp", "", 64, desc = "Test footer")
        setting.updateTextInputLayout(10f, 20f)
        val click = MouseButtonEvent(15.0, 25.0, MouseButtonInfo(0, 0))

        assertTrue(setting.mouseClicked(15f, 25f, click))
        assertTrue(setting.isEditing)
    }
}
