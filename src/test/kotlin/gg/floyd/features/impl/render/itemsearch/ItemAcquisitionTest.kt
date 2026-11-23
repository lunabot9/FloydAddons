package gg.floyd.features.impl.render.itemsearch

import kotlin.test.*

class ItemAcquisitionTest {
    @Test fun `Oringo purchases retain the pet and all shop costs`() {
        val shop=ItemAcquisition.sources(ItemSearchCatalog.byId.getValue("ELEPHANT;0")).first { it.kind=="npc_shop" }
        assertEquals("ORINGO_NPC",shop.owner.id)
        assertEquals("ELEPHANT;0",shop.output.id)
        assertEquals(listOf(ItemAmount("LOG_2","64"),ItemAmount("SKYBLOCK_COIN","10000")),shop.costs)
        val legendary=ItemAcquisition.sources(ItemSearchCatalog.byId.getValue("ELEPHANT;4")).first { it.kind=="npc_shop" }
        assertEquals(ItemAmount("SKYBLOCK_COIN","15000000"),legendary.costs.last())
        val alternatives=ItemAcquisition.sources(ItemSearchCatalog.byId.getValue("ELEPHANT;4"))
        assertTrue(alternatives.any { it.kind=="katgrade" })
        assertTrue(alternatives.any { it.kind=="npc_shop" })
    }
    @Test fun `unknown acquisition cannot open while recipes and known lore sources can`() {
        assertFalse(ItemAcquisition.canOpen(ItemSearchCatalog.byId.getValue("6_ANNIVERSARY_BARN_SKIN")))
        assertTrue(ItemAcquisition.canOpen(ItemSearchCatalog.byId.getValue("HYPERION")))
        assertTrue(ItemAcquisition.canOpen(ItemSearchCatalog.byId.getValue("AIMING;2")))
        assertTrue(ItemAcquisition.canOpen(ItemSearchCatalog.byId.getValue("SCATHA;3")))
    }
    @Test fun `armor previews preserve their exact repository dye colors`() {
        assertEquals(255, ItemSearchCatalog.byId.getValue("LAPIS_ARMOR_CHESTPLATE").dyeColor)
        assertEquals(15916817, ItemSearchCatalog.byId.getValue("SUPERIOR_DRAGON_CHESTPLATE").dyeColor)
        assertEquals(2748649, ItemSearchCatalog.byId.getValue("WISE_DRAGON_CHESTPLATE").dyeColor)
        assertNull(ItemSearchCatalog.byId.getValue("HYPERION").dyeColor)
    }
    @Test fun `amounts preserve pet ids and fractional coin prices`() {
        assertEquals(ItemAmount("ARMADILLO;1", "2"), ItemAmount.parse("ARMADILLO;1:2"))
        assertEquals(ItemAmount("SKYBLOCK_COIN", "8.5"), ItemAmount.parse("SKYBLOCK_COIN:8.5"))
        assertEquals(ItemAmount("ROTTEN_FLESH", "1"), ItemAmount.parse("ROTTEN_FLESH"))
    }
    @Test fun `craftable items open crafting recipes first`() {
        val sources = ItemAcquisition.sources(ItemSearchCatalog.byId.getValue("HYPERION"))
        assertTrue(sources.isNotEmpty())
        assertTrue(sources.all { it.kind == "crafting" })
        assertEquals("NECRON_BLADE:1", sources.first().text("B2"))
    }
    @Test fun `drop acquisition links items back to complete mob drop tables`() {
        val sources = ItemAcquisition.sources(ItemSearchCatalog.byId.getValue("LAPIS_ARMOR_HELMET"))
        val mob = sources.first { it.kind == "drops" && it.owner.id == "LAPIS_ZOMBIE_MONSTER" }
        assertEquals("1%", mob.drops.first { ItemAmount.parse(it["id"].asString).id == "LAPIS_ARMOR_HELMET" }["chance"].asString)
        assertTrue(mob.drops.any { ItemAmount.parse(it["id"].asString).id == "ROTTEN_FLESH" })
        assertTrue(mob.drops.any { ItemAmount.parse(it["id"].asString).id == "LAPIS_CRYSTAL" })
    }
    @Test fun `source text can describe acquisition without a recipe`() {
        val source = ItemAcquisition.loreSources(ItemSearchCatalog.byId.getValue("AIMING;2"))
        assertTrue(source.any { it.contains("Enchantment Table") })
        assertFalse(source.any { it.contains("Damage") })
    }
    @Test fun `forge acquisition retains ingredients and duration`() {
        val sources = ItemAcquisition.sources(ItemSearchCatalog.byId.getValue("AMBER_MATERIAL"))
        val forge = sources.first { it.kind == "forge" }
        assertEquals("21600", forge.text("duration"))
        assertTrue(forge.recipe.getAsJsonArray("inputs").any { it.asString == "FINE_AMBER_GEM:12" })
    }
    @Test fun `forge decimal amounts display as stack counts and keep short durations`() {
        val engine = ItemSearchCatalog.byId.getValue("AMBER_POLISHED_DRILL_ENGINE")
        val forge = ItemAcquisition.sources(engine).first { it.kind == "forge" }
        assertEquals(listOf("5", "5", "16", null, "32"), forge.forgeInputs.map { it.countLabel })
        assertEquals(listOf(5, 5, 16, 1, 32), forge.forgeInputs.map { it.stackCount })
        assertNull(forge.output.countLabel)
        assertEquals("30s", forge.forgeDuration)
        assertEquals("500k", ItemAmount.parse("SKYBLOCK_COIN:500000.0").countLabel)
    }
}
