package gg.floyd.agent

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentModelSelectionTest {
    @Test fun accountDefaultWinsOverAdvertisedButUnsupportedMini() {
        val models = JsonParser.parseString("""[
            {"model":"advertised-mini", "isDefault":false, "inputModalities":["text","image"]},
            {"model":"account-default", "isDefault":true, "inputModalities":["text","image"]}
        ]""").asJsonArray
        assertEquals("account-default", AgentModelSelection.preferred(models))
    }

    @Test fun missingDefaultLetsProviderChooseInsteadOfGuessingByName() {
        val models = JsonParser.parseString("""[{"model":"advertised-mini","isDefault":false}]""").asJsonArray
        assertNull(AgentModelSelection.preferred(models))
    }

    @Test fun hiddenDefaultsAreNotSelected() {
        val models = JsonParser.parseString("""[{"model":"hidden","isDefault":true,"hidden":true}]""").asJsonArray
        assertNull(AgentModelSelection.preferred(models))
    }
}
