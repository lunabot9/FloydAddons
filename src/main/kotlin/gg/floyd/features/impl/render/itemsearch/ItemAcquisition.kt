package gg.floyd.features.impl.render.itemsearch

import com.google.gson.JsonObject

internal data class ItemAmount(val id: String, val count: String) {
    val stackCount get() = count.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()?.toIntOrNull() ?: 1
    val countLabel: String? get() {
        if (id == "SKYBLOCK_COIN") return ItemSearchFormatting.compact(count)
        val quantity = count.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: count
        return quantity.takeUnless { it == "1" }
    }

    companion object {
        fun parse(raw: String): ItemAmount {
            val colon = raw.lastIndexOf(':')
            return if (colon > 0 && raw.substring(colon + 1).toDoubleOrNull() != null)
                ItemAmount(raw.substring(0, colon), raw.substring(colon + 1)) else ItemAmount(raw, "1")
        }
    }
}

internal data class ItemSource(val owner: SearchItem, val recipe: JsonObject) {
    val kind: String get() = recipe["type"]?.asString ?: "crafting"
    fun text(key: String, fallback: String = "") = recipe[key]?.takeUnless { it.isJsonNull }?.asString ?: fallback
    val drops get() = recipe.getAsJsonArray("drops")?.map { it.asJsonObject }.orEmpty()
    val forgeInputs get() = recipe.getAsJsonArray("inputs")?.map { it.asString }
        ?.filter { it.isNotBlank() }?.map(ItemAmount::parse).orEmpty()
    val forgeDuration get() = text("duration").toLongOrNull()?.takeIf { it > 0 }?.let(ItemSearchFormatting::duration)
    val costs:List<ItemAmount> get() = recipe["cost"]?.let { raw ->
        (if(raw.isJsonArray)raw.asJsonArray.map { it.asString } else listOf(raw.asString))
            .filter { it.isNotBlank() }.map(ItemAmount::parse)
    }.orEmpty()
    val output: ItemAmount get() = when (kind) {
        "npc_shop", "trade" -> ItemAmount.parse(text("result", owner.id))
        "katgrade" -> ItemAmount.parse(text("output", owner.id))
        else -> ItemAmount(text("overrideOutputId", owner.id), text("count", "1"))
    }
}

internal object ItemAcquisition {
    private val index: Map<String, List<ItemSource>> by lazy {
        val result = linkedMapOf<String, MutableList<ItemSource>>()
        fun add(id: String, source: ItemSource) { result.getOrPut(id) { mutableListOf() }.add(source) }
        for (item in ItemSearchCatalog.items) {
            if (item.recipe.isNotEmpty()) {
                val recipe = JsonObject().apply {
                    addProperty("type", "crafting"); addProperty("count", item.count)
                    item.recipe.forEach { (key, value) -> addProperty(key, value) }
                }
                add(item.id, ItemSource(item, recipe))
            }
            for (recipe in item.recipes) {
                val source = ItemSource(item, recipe)
                if (source.kind == "drops") {
                    add(item.id, source)
                    source.drops.map { ItemAmount.parse(it["id"].asString).id }.distinct().filter { it != item.id }.forEach { add(it, source) }
                } else add(source.output.id, source)
            }
        }
        result.mapValues { (_, sources) -> sources.distinctBy { it.owner.id + it.recipe.toString() } }
    }
    fun sources(item: SearchItem): List<ItemSource> {
        val all = index[item.id].orEmpty()
        if(item.name.contains("[Lvl "))return all
        val crafting = all.filter { it.kind == "crafting" }
        return crafting.ifEmpty { all }
    }
    fun canOpen(item:SearchItem)=sources(item).isNotEmpty() || loreSources(item).isNotEmpty()
    fun loreSources(item: SearchItem): List<String> {
        val start = item.lore.indexOfFirst { SearchItem.clean(it).trim() in setOf("source:", "obtained from:") }
        if (start < 0) return item.craftText.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
        return item.lore.drop(start + 1).takeWhile { SearchItem.clean(it).isNotBlank() }.take(12)
    }
}
