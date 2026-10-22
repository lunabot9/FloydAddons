package gg.floyd.supercraft

/** Resource-only recipes: nothing here may turn materials into equipment or spend currency. */
data class SackRecipe(val output:String,val count:Long=1,val ingredients:Map<String,Long>)
data class SackCraftStep(val recipe:SackRecipe,val crafts:Long)
data class SackCraftPlan(val steps:List<SackCraftStep>,val remaining:Map<String,Long>)

object SackCraftPlanner {
    fun key(value:String)=value.replace(Regex("§."),"").trim().uppercase().replace(Regex("[^A-Z0-9]+"),"_").trim('_')
    fun stored(text:String):Long? {
        val clean=text.replace(Regex("§."),"")
        val result=Regex("(?im)^\\s*(?:Stored|Amount|Items Stored):\\s*([0-9][0-9,]*)(?![0-9,.A-Za-z])").find(clean) ?: return null
        return result.groupValues[1].replace(",","").toLongOrNull()?.takeIf { it in 0..1_000_000_000_000L }
    }
    /** Exemptions protect an input tier. Producing that tier is allowed; consuming it is not. */
    fun plan(stock:Map<String,Long>,recipes:List<SackRecipe>,keep:Set<String>):SackCraftPlan {
        require(stock.values.all { it in 0..1_000_000_000_000L }) { "Invalid sack amount" }
        val safe=recipes.filter { r -> r.count>0 && r.ingredients.isNotEmpty() && r.output !in r.ingredients && r.ingredients.values.all { it>0 } && r.ingredients.values.sum()>r.count }
        val quantities=stock.toMutableMap();val steps=mutableListOf<SackCraftStep>()
        fun depth(id:String,seen:Set<String> = emptySet()):Int {
            if(id in seen || seen.size>16)return 0
            return safe.filter { id in it.ingredients && it.ingredients.keys.none(keep::contains) }.maxOfOrNull { 1+depth(it.output,seen+id) } ?: 0
        }
        // Collapse as high as possible before choosing a lower competing use (e.g. wheat vs bread).
        repeat(256) {
            val candidates=safe.mapNotNull { r ->
                if(r.ingredients.keys.any(keep::contains))return@mapNotNull null
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
