package gg.floyd.features.impl.cosmetic

/** Vanilla mob entity ids present in Minecraft 26.1.2's EntityType registry. */
internal object VanillaMobCatalog {
    private val knownMobIds = setOf(
        "allay", "armadillo", "axolotl", "bat", "bee", "blaze", "bogged", "breeze",
        "camel", "camel_husk", "cat", "cave_spider", "chicken", "cod", "copper_golem",
        "cow", "creaking", "creeper", "dolphin", "donkey", "drowned", "elder_guardian",
        "enderman", "endermite", "ender_dragon", "evoker", "fox", "frog", "ghast",
        "happy_ghast", "giant", "glow_squid", "goat", "guardian", "hoglin", "horse",
        "husk", "illusioner", "iron_golem", "llama", "magma_cube", "mooshroom", "mule",
        "nautilus", "ocelot", "panda", "parched", "parrot", "phantom", "pig", "piglin",
        "piglin_brute", "pillager", "polar_bear", "pufferfish", "rabbit", "ravager",
        "salmon", "sheep", "shulker", "silverfish", "skeleton", "skeleton_horse", "slime",
        "sniffer", "snow_golem", "spider", "squid", "stray", "strider", "tadpole",
        "trader_llama", "tropical_fish", "turtle", "vex", "villager", "vindicator",
        "wandering_trader", "warden", "witch", "wither", "wither_skeleton", "wolf",
        "zoglin", "zombie", "zombie_horse", "zombie_nautilus", "zombie_villager",
        "zombified_piglin"
    )

    val ids: List<String> = knownMobIds.sorted()

    private val labelsById = ids.associateWith(::labelForPath)
    private val idsByLabel = labelsById.entries.associate { (id, label) -> label to id }

    val labels: List<String> = ids.map(labelsById::getValue)

    fun idForLabel(label: String): String? = idsByLabel[label]

    fun labelForId(id: String): String? = labelsById[id]

    private fun labelForPath(path: String): String = path
        .split('_')
        .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }
}
