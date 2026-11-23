package gg.floyd.features.impl.render.itemsearch

import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal object ItemSearchFormatting {
    data class DropChance(val percentage:String,val fraction:String?,val conditions:String) {
        val tooltip get()=listOfNotNull(percentage,fraction?.let { "($it)" },conditions.takeIf { it.isNotBlank() }).joinToString(" ")
    }
    fun dropChance(raw:String):DropChance {
        val clean=raw.replace(Regex("[§\uFFFD][0-9A-FK-ORa-fk-or]"),"").trim()
        val match=Regex("^([0-9]+(?:\\.[0-9]+)?)%(.*)$").matchEntire(clean) ?: return DropChance(clean,null,"")
        val percent=match.groupValues[1].toBigDecimal()
        if(percent<BigDecimal.ZERO || percent>BigDecimal.valueOf(100))return DropChance(clean,null,"")
        val probability=percent.movePointLeft(2).stripTrailingZeros()
        val scale=maxOf(0,probability.scale())
        val numerator=probability.setScale(scale).unscaledValue()
        val denominator=java.math.BigInteger.TEN.pow(scale)
        val gcd=numerator.gcd(denominator)
        return DropChance(match.groupValues[1]+"%",exact((numerator/gcd).toString())+"/"+exact((denominator/gcd).toString()),match.groupValues[2].trim())
    }
    fun exact(raw: String): String = raw.toBigDecimalOrNull()?.let {
        DecimalFormat("#,##0.################", DecimalFormatSymbols(Locale.US)).format(it)
    } ?: raw

    fun duration(seconds: Long): String =
        listOf(seconds / 86400 to "d", seconds / 3600 % 24 to "h", seconds / 60 % 60 to "m", seconds % 60 to "s")
            .filter { it.first > 0 }.joinToString(" ") { (value, unit) -> "$value$unit" }

    fun compact(raw: String): String {
        val value=raw.toBigDecimalOrNull() ?: return raw
        val unit=listOf(1_000_000_000_000L to "t",1_000_000_000L to "b",1_000_000L to "m",1_000L to "k")
            .firstOrNull { value.abs()>=BigDecimal.valueOf(it.first) } ?: return exact(raw)
        return value.divide(BigDecimal.valueOf(unit.first),2,RoundingMode.DOWN).stripTrailingZeros().toPlainString()+unit.second
    }

    /** Catalog previews show the two endpoint values rather than a player's pet XP. */
    fun petText(id: String, template: String, petNumbers: JsonObject): String {
        val pet=id.substringBefore(';')
        val rarity=listOf("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC","SPECIAL","VERY_SPECIAL").getOrNull(id.substringAfter(';',"").toIntOrNull() ?: -1)
        val stats=rarity?.let { petNumbers.getAsJsonObject(pet)?.getAsJsonObject(it) }
        fun number(level:String,key:String):String {
            val values=stats?.getAsJsonObject(level)
            val value=if(key.toIntOrNull()!=null)values?.getAsJsonArray("otherNums")?.elementAtOrNull(key.toInt())
                else values?.getAsJsonObject("statNums")?.get(key)
            return value?.asBigDecimal?.stripTrailingZeros()?.toPlainString() ?: "?"
        }
        if(!template.contains('{'))return template
        return Regex("\\{([^{}]+)}").replace(template) { match ->
            val key=match.groupValues[1]
            when {
                key=="LVL" -> "1-Lvl 100"
                else -> "${number("1",key)}-${number("100",key)}"
            }
        }
    }
}
