package gg.floyd.supercraft

/** Resource-only recipes: nothing here may turn materials into equipment or spend currency. */
data class SackRecipe(val output:String,val count:Long=1,val ingredients:Map<String,Long>)
data class SackCraftStep(val recipe:SackRecipe,val crafts:Long)
data class SackCraftPlan(val steps:List<SackCraftStep>,val remaining:Map<String,Long>)

object SackCraftPlanner {
    /** Gemstone Stored totals are weighted capacity, not a count of the icon's Rough grade. */
    fun sackStock(itemId:String,text:String,gemstones:Boolean):Map<String,Long> {
        if(!gemstones)return stored(text)?.let { mapOf(itemId to it) }.orEmpty()
        val family=Regex("^(?:ROUGH|FLAWED|FINE)_([A-Z_]+)_GEM$").matchEntire(itemId)?.groupValues?.get(1) ?: return emptyMap()
        val counts=linkedMapOf<String,Long>()
        val pattern=Regex("^\\s*(Rough|Flawed|Fine):\\s*([0-9][0-9,]*)(?:\\s+\\([0-9,]+\\))?\\s*$",RegexOption.IGNORE_CASE)
        text.replace(Regex("§."),"").lines().forEach { line ->
            val match=pattern.matchEntire(line) ?: return@forEach
            val id="${match.groupValues[1].uppercase()}_${family}_GEM"
            val count=match.groupValues[2].replace(",","").toLongOrNull()?.takeIf { it in 0..1_000_000_000_000L } ?: return emptyMap()
            if(counts.put(id,count)!=null)return emptyMap()
        }
        return counts.takeIf { it.size==3 }.orEmpty()
    }
    /** Decorative resource-pack glyphs belong in rendering, never server command arguments. */
    fun displayName(value:String):String = buildString {
        value.replace(Regex("§."),"").codePoints().forEach { point ->
            if(Character.getType(point)!=Character.PRIVATE_USE.toInt())appendCodePoint(point)
        }
    }.trim()
    fun key(value:String)=value.replace(Regex("§."),"").trim().uppercase().replace(Regex("[^A-Z0-9]+"),"_").trim('_')
    fun sackName(value:String,names:Set<String>):String? {
        var normalized=key(value).replace(Regex("^(?:SMALL|MEDIUM|LARGE)_"),"").removeSuffix("_SACKS").removeSuffix("_SACK")
        normalized=when(normalized) { "GEMSTONES"->"GEMSTONE";"DWARVEN_MINES"->"DWARVEN";else->normalized }
        return names.singleOrNull { key(it)==normalized }
    }
    private fun compressionRecipes(recipes:List<SackRecipe>)=recipes.filter { r ->
        r.count>0 && r.ingredients.isNotEmpty() && r.output !in r.ingredients && r.ingredients.values.all { it>0 } &&
            runCatching { r.ingredients.values.fold(0L,Math::addExact) }.getOrDefault(0)>r.count
    }
    /** Existing saved input ceilings remain protected; new ignored targets may never be produced. */
    fun allowed(recipe:SackRecipe,ignored:Set<String>)=recipe.output !in ignored && recipe.ingredients.keys.none(ignored::contains)
    private val resourceTerminals=setOf("ABSOLUTE_ENDER_PEARL","BLUE_ICE_HUNK","BOX_OF_SEEDS","CONCENTRATED_STONE","CONDENSED_WATER_LILY","FIGSTONE","HELIXIS","HEMOGLASS","HEMOBOMB","LOTUS_GOLD","LOTUS_SILVER","MAGMA_FISH_GOLD","MAGMA_FISH_SILVER","MANGCORE","MUTANT_NETHER_STALK","POLISHED_PUMPKIN","SOULFLOW","STUFFED_CHILI_PEPPER","SUPER_EGG","VEILSHROOM_BUNCH","WHIPPED_MAGMA_CREAM")
    /** Only direct resource compression families, never alternate food, oil, bait or equipment uses. */
    fun familyRecipes(recipes:List<SackRecipe>):List<SackRecipe> = compressionRecipes(recipes).filter { recipe ->
        recipe.ingredients.size==1 && recipe.output!="ENCHANTED_BREAD" &&
            (recipe.output.startsWith("ENCHANTED_") || recipe.output.startsWith("COMPACTED_") ||
                recipe.output.startsWith("FLAWED_") || recipe.output.startsWith("FINE_") || recipe.output in resourceTerminals)
    }
    fun directRecipes(resources:Set<String>,recipes:List<SackRecipe>):List<SackRecipe> {
        val safe=familyRecipes(recipes)
        val aliases=mapOf("WHEAT" to "ENCHANTED_WHEAT","DOUBLE_PLANT" to "ENCHANTED_SUNFLOWER","SUNFLOWER" to "ENCHANTED_SUNFLOWER","SUGAR_CANE" to "ENCHANTED_SUGAR","NETHER_STALK" to "ENCHANTED_NETHER_STALK","CACTUS" to "ENCHANTED_CACTUS_GREEN","CHILI_PEPPER" to "STUFFED_CHILI_PEPPER")
        return resources.filterNot { it.startsWith("ENCHANTED_") }.mapNotNull { input ->
            val target=aliases[input] ?: "ENCHANTED_$input"
            val candidates=safe.filter { it.ingredients.keys==setOf(input) && (it.output==target || it.output.startsWith("ENCHANTED_") || input.endsWith("_GEM") && it.output.endsWith("_GEM")) }
            candidates.singleOrNull { it.output==target } ?: if(input in aliases)null else candidates.singleOrNull()
        }
    }
    /** Each initial input is spent once; outputs never increase another input's budget. */
    fun planDirect(stock:Map<String,Long>,recipes:List<SackRecipe>,ignored:Set<String>):SackCraftPlan {
        require(stock.values.all { it in 0..1_000_000_000_000L })
        val safe=compressionRecipes(recipes).filter { it.ingredients.size==1 && allowed(it,ignored) }
        fun depth(id:String,seen:Set<String> = emptySet()):Int = if(id in seen || seen.size>16)0 else
            safe.filter { id in it.ingredients }.maxOfOrNull { 1+depth(it.output,seen+id) } ?: 0
        val result=stock.toMutableMap();val steps=mutableListOf<SackCraftStep>()
        // Higher initial gemstone grades are spent before lower grades produce any more of them.
        safe.sortedWith(compareBy<SackRecipe> { depth(it.output) }.thenBy { it.output }).forEach { recipe ->
            val (input,cost)=recipe.ingredients.entries.single()
            val n=(stock[input] ?: 0)/cost
            if(n>0) {
                result[input]=(result[input] ?: 0)-cost*n
                result[recipe.output]=Math.addExact(result[recipe.output] ?: 0,Math.multiplyExact(recipe.count,n))
                steps+=SackCraftStep(recipe,n)
            }
        }
        return SackCraftPlan(steps,result.filterValues { it>0 })
    }
    /** Highest reachable crafting outputs only. No equipment, trades, unpacking, or dangling cycles. */
    fun upgradeTargets(resources:Set<String>,recipes:List<SackRecipe>,resourceIds:Set<String>):Set<String> {
        val safe=compressionRecipes(recipes).filter { it.output in resourceIds && it.ingredients.keys.all(resourceIds::contains) }
        val reachable=resources.intersect(resourceIds).toMutableSet()
        val usable=linkedSetOf<SackRecipe>()
        repeat(resourceIds.size+1) {
            val next=safe.filter { it.ingredients.keys.all(reachable::contains) }
            usable+=next
            val added=next.map { it.output }.filter(reachable::add)
            if(added.isEmpty())return usable.map { it.output }.filter { output -> usable.none { output in it.ingredients } }.toSortedSet()
        }
        return emptySet()
    }
    fun stored(text:String):Long? {
        val clean=text.replace(Regex("§."),"")
        val result=Regex("(?im)^\\s*(?:Stored|Amount|Items Stored):\\s*([0-9][0-9,]*)(?![0-9,.A-Za-z])").find(clean) ?: return null
        return result.groupValues[1].replace(",","").toLongOrNull()?.takeIf { it in 0..1_000_000_000_000L }
    }
    /** Ignored outputs cannot be crafted; older input-tier selections also retain their protection. */
    fun plan(stock:Map<String,Long>,recipes:List<SackRecipe>,keep:Set<String>):SackCraftPlan {
        require(stock.values.all { it in 0..1_000_000_000_000L }) { "Invalid sack amount" }
        val safe=compressionRecipes(recipes).filter { allowed(it,keep) }
        val quantities=stock.toMutableMap();val steps=mutableListOf<SackCraftStep>()
        fun depth(id:String,seen:Set<String> = emptySet()):Int {
            if(id in seen || seen.size>16)return 0
            return safe.filter { id in it.ingredients }.maxOfOrNull { 1+depth(it.output,seen+id) } ?: 0
        }
        // Collapse as high as possible before choosing a lower competing use (e.g. wheat vs bread).
        repeat(256) {
            val candidates=safe.mapNotNull { r ->
                val n=r.ingredients.minOf { (id,cost)->(quantities[id] ?: 0)/cost }
                if(n>0)SackCraftStep(r,n) else null
            }
            val next=candidates.maxWithOrNull(compareBy<SackCraftStep> { depth(it.recipe.output) }
                .thenBy { it.recipe.ingredients.values.sum()/it.recipe.count }.thenBy { it.recipe.output })
                ?: return SackCraftPlan(steps,quantities.filterValues { it>0 })
            val recipe=next.recipe
            recipe.ingredients.forEach { (id,cost)->quantities[id]=(quantities[id] ?: 0)-cost*next.crafts }
            quantities[recipe.output]=Math.addExact(quantities[recipe.output] ?: 0,Math.multiplyExact(recipe.count,next.crafts))
            steps+=next
        }
        error("Recipe graph exceeded the bounded craft plan")
    }
}
