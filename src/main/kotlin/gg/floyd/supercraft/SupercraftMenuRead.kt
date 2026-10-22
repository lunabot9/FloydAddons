package gg.floyd.supercraft

data class SupercraftAmount(val outputs:Long,val intoSacks:Boolean)
object SupercraftMenuRead {
    private fun clean(text:String)=text.replace(Regex("§."),"")
    fun amount(text:String):SupercraftAmount? {
        val match=Regex("(?im)^\\s*Crafting ([0-9][0-9,]*) items? into your (sacks|inventory)!?\\s*$").find(clean(text)) ?: return null
        val n=match.groupValues[1].replace(",","").toLongOrNull()?.takeIf { it in 1..1_000_000_000_000L } ?: return null
        return SupercraftAmount(n,match.groupValues[2].equals("sacks",true))
    }
    fun costs(text:String,names:Map<String,String>):Map<String,Long>? {
        val pattern=Regex("^\\s*[✔✓] [0-9,]+/([0-9,]+) (?:\\([0-9,]+x\\) )?(.+?)\\s*$")
        val result=linkedMapOf<String,Long>()
        clean(text).lines().forEach { line ->
            val match=pattern.matchEntire(line) ?: return@forEach
            val name=SackCraftPlanner.displayName(match.groupValues[2])
            val id=names.entries.firstOrNull { it.value.equals(name,true) }?.key ?: return null
            val count=match.groupValues[1].replace(",","").toLongOrNull()?.takeIf { it>0 } ?: return null
            if(id in result)return null
            result[id]=count
        }
        return result.takeIf { it.isNotEmpty() }
    }
    fun agrees(recipe:SackRecipe,amount:SupercraftAmount,costs:Map<String,Long>):Boolean {
        if(amount.outputs%recipe.count!=0L)return false
        val crafts=amount.outputs/recipe.count
        return costs==recipe.ingredients.mapValues { (_,n)->runCatching { Math.multiplyExact(n,crafts) }.getOrElse { return false } }
    }
    fun maximumCrafts(text:String,names:Map<String,String>,recipe:SackRecipe):Long? {
        val pattern=Regex("^\\s*[✔✓] ([0-9,]+)/[0-9,]+ (?:\\([0-9,]+x\\) )?(.+?)\\s*$")
        val owned=linkedMapOf<String,Long>()
        clean(text).lines().forEach { line ->
            val match=pattern.matchEntire(line) ?: return@forEach
            val id=names.entries.singleOrNull { it.value.equals(SackCraftPlanner.displayName(match.groupValues[2]),true) }?.key ?: return null
            val amount=match.groupValues[1].replace(",","").toLongOrNull() ?: return null
            if(id in owned)return null
            owned[id]=amount
        }
        if(owned.keys!=recipe.ingredients.keys)return null
        return recipe.ingredients.minOf { (id,cost)->owned.getValue(id)/cost }
    }
}
