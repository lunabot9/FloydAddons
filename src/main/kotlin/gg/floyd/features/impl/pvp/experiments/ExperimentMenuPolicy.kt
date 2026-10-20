package gg.floyd.features.impl.pvp.experiments

object ExperimentMenuPolicy {
    enum class BottleAction { OWNED, BAZAAR, UNAVAILABLE, OVER_LIMIT }
    fun bottleAction(card: ExperimentCard?, limit: Double): BottleAction {
        if (card == null) return BottleAction.UNAVAILABLE
        val text = card.lore.joinToString(" ")
        if (Regex("could not find|not enough|insufficient|cannot afford|can't afford|missing", RegexOption.IGNORE_CASE).containsMatchIn(text)) return BottleAction.UNAVAILABLE
        // Ironman offers inventory consumption, with no Bazaar price at all.
        if (card.lore.any { it.equals("Click to consume!", true) }) return BottleAction.OWNED
        if (card.lore.none { it.contains("Click to buy now", true) }) return BottleAction.UNAVAILABLE
        val price = bottlePrice(card) ?: return BottleAction.UNAVAILABLE
        return if (price > limit) BottleAction.OVER_LIMIT else BottleAction.BAZAAR
    }
    val tiers = listOf("Beginner", "High", "Grand", "Supreme", "Transcendent", "Metaphysical")
    private val locked = Regex("enchanting level too low|not unlocked|locked|unavailable|already (?:played|completed)|on cooldown", RegexOption.IGNORE_CASE)
    fun highestUnlocked(cards: List<ExperimentCard>): ExperimentCard? = cards
        .filterNot { locked.containsMatchIn(it.lore.joinToString(" ")) }
        .mapNotNull { c -> tiers.indexOfFirst { c.name.startsWith(it, true) }.takeIf { it >= 0 }?.let { it to c } }
        .maxByOrNull { it.first }?.second
    fun levels(card: ExperimentCard) = Regex("([\\d,]+)\\s+(?:XP|Exp(?:erience)?)\\s+Levels?", RegexOption.IGNORE_CASE)
        .find(card.lore.joinToString(" "))?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
    fun charges(card: ExperimentCard?) = card?.lore?.firstNotNullOfOrNull {
        Regex("Charges:\\s*(\\d+)\\s*/\\s*(\\d+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull()
    }
    fun renewalsRemaining(card: ExperimentCard): Int? = card.lore.firstNotNullOfOrNull {
        Regex("You've renewed (\\d+)/(\\d+) charges today!", RegexOption.IGNORE_CASE).find(it)?.let { match ->
            (match.groupValues[2].toInt() - match.groupValues[1].toInt()).coerceAtLeast(0)
        }
    }
    fun bits(card: ExperimentCard): Int? = card.lore.firstNotNullOfOrNull {
        Regex("^([\\d,]+) Bits$", RegexOption.IGNORE_CASE).matchEntire(it)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
    }
    fun insufficientBits(card: ExperimentCard) = Regex("(?:not enough|insufficient|don't have enough|do not have enough|need more) Bits", RegexOption.IGNORE_CASE)
        .containsMatchIn(card.lore.joinToString(" "))
    fun bottlePrice(card: ExperimentCard): Double? {
        val index = card.lore.indexOfFirst { it.equals("Bazaar Price", true) }
        if (index < 0) return null
        return card.lore.drop(index + 1).firstNotNullOfOrNull {
            Regex("^([\\d,.]+) Coins$", RegexOption.IGNORE_CASE).matchEntire(it)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        }
    }
}
