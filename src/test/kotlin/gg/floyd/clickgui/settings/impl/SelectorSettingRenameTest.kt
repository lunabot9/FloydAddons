package gg.floyd.clickgui.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorSettingRenameTest {
    @Test fun `renamed saved pet remains selected and saves its new label`() {
        val setting = SelectorSetting("Pet Model", "Verity", listOf("Verity", "Randy"), "",
            renamedOptions = mapOf("Big D Randy" to "Randy"))
        val gson = Gson()
        setting.read(JsonPrimitive("Big D Randy"), gson)
        assertEquals("Randy", setting.selectedOption())
        assertEquals("Randy", setting.write(gson).asString)
        setting.read(JsonPrimitive("Verity"), gson)
        assertEquals("Verity", setting.selectedOption())
    }
}
