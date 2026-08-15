package gg.floyd.features.impl.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TreecapitatorLowestBinPriceTest {
    @Test
    fun `parser selects the cheapest valid Treecapitator BIN`() {
        val json = """
            [
              {"tag":"TREECAPITATOR_AXE","startingBid":240000,"bin":true},
              {"tag":"TREECAPITATOR_AXE","startingBid":220000,"bin":true},
              {"tag":"TREECAPITATOR_AXE","startingBid":1,"bin":false},
              {"tag":"JUNGLE_AXE","startingBid":100,"bin":true}
            ]
        """.trimIndent()

        assertEquals(220_000L, TreecapitatorLowestBinPrice.parseLowestBin(json))
    }

    @Test
    fun `parser rejects a response without an active Treecapitator BIN`() {
        assertFailsWith<IllegalStateException> {
            TreecapitatorLowestBinPrice.parseLowestBin(
                """[{"tag":"TREECAPITATOR_AXE","startingBid":0,"bin":true}]""",
            )
        }
    }
}
