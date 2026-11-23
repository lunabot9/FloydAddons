package gg.floyd.features.impl.render.itemsearch

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import gg.floyd.FloydAddonsMod.mc
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore
import java.util.Locale
import java.util.UUID

internal data class SearchItem(
    val id: String, val name: String, val item: String, val damage: Int,
    val lore: List<String>, val model: String?, val texture: String?,
    val recipe: Map<String, String>, val count: Int,
    val recipes: List<JsonObject>, val craftText: String, val info: List<String>,
    val dyeColor: Int?, val extraAttributes: String = "{}",
) {
    val searchName = clean(name)
    val searchId = id.lowercase(Locale.ROOT)
    val searchLore = clean(lore.joinToString("\n"))
    companion object { fun clean(text: String) = text.replace(Regex("§."), "").lowercase(Locale.ROOT) }
}

internal object ItemSearchCatalog {
    private val data by lazy {
        val stream = javaClass.getResourceAsStream("/assets/floydaddons/data/item_search.json") ?: error("Item catalog missing")
        stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    }
    val mobs: JsonObject get() = data.getAsJsonObject("mobs")
    val items: List<SearchItem> by lazy {
            data.getAsJsonArray("items").map { element ->
                val j = element.asJsonObject
                val id=j["id"].asString
                val pet=j["name"].asString.contains("{LVL}")
                fun display(value:String)=if(pet)ItemSearchFormatting.petText(id,value,data.getAsJsonObject("petnums")) else value
                SearchItem(id, display(j["name"].asString), j["item"].asString, j["damage"].asInt,
                    j.getAsJsonArray("lore").map { display(it.asString) },
                    j["model"]?.takeUnless { it.isJsonNull }?.asString,
                    j["texture"]?.takeUnless { it.isJsonNull }?.asString,
                    j.getAsJsonObject("recipe").entrySet().associate { it.key to it.value.asString }, j["count"].asInt,
                    j.getAsJsonArray("recipes").map { it.asJsonObject }, j["crafttext"].asString, j.getAsJsonArray("info").map { it.asString },
                    j["dyeColor"]?.takeUnless { it.isJsonNull }?.asInt,j["extraAttributes"]?.asString ?: "{}")
            }.sortedWith(compareBy<SearchItem> { it.searchName }.thenBy { it.id })
    }
    val byId by lazy { items.associateBy { it.id } }
    // Only visible icons are materialized. A bounded cache avoids retaining thousands of profiles.
    private val icons = object : LinkedHashMap<String, ItemStack>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ItemStack>?) = size > 512
    }
    fun search(query: String): List<SearchItem> {
        val parsed = ItemSearchQuery(query)
        return items.filter { parsed.matches(it.searchName, it.searchId, it.searchLore) }
    }
    fun clearIcons() = icons.clear()
    fun icon(entry: SearchItem): ItemStack = icons.getOrPut(entry.id) {
        val raw = entry.item.removePrefix("minecraft:")
        val mapped = when (raw) {
            "skull" -> "player_head"
            "dye" -> listOf("ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal").getOrElse(entry.damage) { "ink_sac" }
            "fish" -> listOf("cod", "salmon", "tropical_fish", "pufferfish").getOrElse(entry.damage) { "cod" }
            "log" -> listOf("oak_log", "spruce_log", "birch_log", "jungle_log").getOrElse(entry.damage % 4) { "oak_log" }
            "log2" -> if (entry.damage % 2 == 0) "acacia_log" else "dark_oak_log"
            "double_plant" -> "sunflower"
            "wool", "stained_glass", "stained_glass_pane", "stained_hardened_clay", "carpet" -> {
                val color = listOf("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black").getOrElse(entry.damage) { "white" }
                "${color}_${if (raw == "stained_hardened_clay") "terracotta" else raw}"
            }
            "golden_rail" -> "powered_rail"
            "web" -> "cobweb"
            "reeds" -> "sugar_cane"
            "waterlily" -> "lily_pad"
            "netherbrick" -> "nether_brick"
            "fireworks" -> "firework_rocket"
            "firework_charge" -> "firework_star"
            "enchanted_book" -> "enchanted_book"
            else -> raw
        }
        val native = entry.model?.takeIf { it.startsWith("minecraft:") } ?: "minecraft:$mapped"
        val item = runCatching { BuiltInRegistries.ITEM.getValue(Identifier.parse(native)) }.getOrNull()?.takeUnless { it == Items.AIR } ?: Items.PAPER
        ItemStack(item).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal(entry.name))
            set(DataComponents.LORE,ItemLore(entry.lore.map(Component::literal)))
            set(DataComponents.CUSTOM_DATA, CustomData.of((runCatching { TagParser.parseCompoundFully(entry.extraAttributes) }.getOrNull() ?: CompoundTag()).apply {
                if(!contains("id"))putString("id",entry.id)
                // Modern Hypixel items expose ExtraAttributes directly as custom_data.
                if(entry.name.contains("[Lvl 1-Lvl 100]")) {
                    val rarities=listOf("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC","SPECIAL","VERY_SPECIAL")
                    val tier=rarities.getOrNull(entry.id.substringAfter(';',"").toIntOrNull() ?: -1)
                    if(tier!=null) {
                        putString("id","PET")
                        putString("petInfo",com.google.gson.JsonObject().apply {
                            addProperty("type",entry.id.substringBefore(';'));addProperty("tier",tier)
                            addProperty("exp",0);addProperty("candyUsed",0)
                        }.toString())
                    }
                }
            }))
            entry.dyeColor?.let { set(DataComponents.DYED_COLOR, DyedItemColor(it)) }
            if (entry.item.contains("enchanted") || entry.id.startsWith("ENCHANTED_")) set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            entry.model?.let { model ->
                val identifier = Identifier.tryParse(model)
                if (identifier != null && mc.resourceManager.getResource(Identifier.fromNamespaceAndPath(identifier.namespace, "items/${identifier.path}.json")).isPresent)
                    set(DataComponents.ITEM_MODEL, identifier)
            }
            entry.texture?.let { texture ->
                val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", texture)))
                set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(UUID.nameUUIDFromBytes(entry.id.toByteArray()), "FloydItem", properties)))
            }
        }
    }
}
