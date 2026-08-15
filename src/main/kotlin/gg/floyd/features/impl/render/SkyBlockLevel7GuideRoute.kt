package gg.floyd.features.impl.render

/** A single ordered instruction in the fresh-profile route supplied with the guide. */
internal data class SkyBlockLevel7Step(
    val number: Int,
    val label: String,
    /** Exact manual action the player should perform; this guide never performs it for them. */
    val instruction: String,
    /** Short, user-facing description of the evidence used by the automatic detector. */
    val detection: String,
)

internal data class SkyBlockGuideItem(
    val id: String,
    val name: String,
    val count: Int = 1,
    val enchanted: Boolean = false,
    val lore: List<String> = emptyList(),
)

/**
 * Minecraft-independent view of the signals Hypixel exposes to the client. Keeping the detector
 * policy pure makes all 119 transitions regression-testable without connecting to the server.
 */
internal data class SkyBlockGuideObservation(
    val chat: String = "",
    val screenTitle: String = "",
    val screenItems: List<SkyBlockGuideItem> = emptyList(),
    val inventory: List<SkyBlockGuideItem> = emptyList(),
    val nearbyNames: List<String> = emptyList(),
    val scoreboard: List<String> = emptyList(),
    val skillLevels: Map<String, Int> = emptyMap(),
    val experienceLevel: Int = 0,
    val purse: Long? = null,
    val heldItem: SkyBlockGuideItem? = null,
    val armor: Map<String, SkyBlockGuideItem> = emptyMap(),
    val sneaking: Boolean = false,
    val teleported: Boolean = false,
    val elapsedOnStepMs: Long = 0L,
    val treecapLowestBin: Long? = null,
)

internal data class SkyBlockGuideBaseline(
    val inventory: List<SkyBlockGuideItem> = emptyList(),
    val experienceLevel: Int = 0,
    val purse: Long? = null,
)

/** Ordered route and completion policy for the SkyBlock Level 7 guide. */
internal object SkyBlockLevel7GuideRoute {
    private val instructions = mapOf(
        1 to "In the Hub farm, break wheat, carrots, or potatoes until at least one crop enters your inventory.",
        2 to "Go to the Village Alchemist at 42, 70, -64 and click every crop stack in your inventory to sell it.",
        3 to "Enter the Builder's House and walk up to Wool Weaver at -47, 74, -31.",
        4 to "Right-click Wool Weaver through her introduction until the wool shop inventory is open.",
        5 to "Buy exactly one full stack (64) of White Wool from Wool Weaver.",
        6 to "Go to the Furniture Shop and stand next to the Carpenter at 16, 72, -22.",
        7 to "Right-click the Carpenter through all dialogue and hand him the full stack of 64 White Wool.",
        8 to "Walk to the Lumber Merchant in the Village at -50, 70, -68.",
        9 to "Right-click the Lumber Merchant through his introduction until the Lumberjack Shop opens.",
        10 to "Buy 32 Sticks plus the Rookie Axe, Promising Axe, Sweet Axe, and Efficient Axe.",
        11 to "Walk into the Weaponsmith building and approach the Weaponsmith at -10, 68, -130.",
        12 to "Right-click the Weaponsmith through the introduction until the Weaponsmith shop opens.",
        13 to "Buy the Undead Sword, End Sword, Spider Sword, and Wither Bow; skip the vanilla Diamond Sword and Bow because the Museum will not accept them.",
        14 to "Leave the shop and walk to the Blacksmith in the Village forge.",
        15 to "Right-click the Blacksmith and continue his first-time dialogue until the reforge interface appears.",
        16 to "Return to the Weaponsmith building and stand next to the Mine Merchant at -9, 68, -125.",
        17 to "Right-click the Mine Merchant through the introduction until the Miner Shop opens.",
        18 to "Buy a Rookie Pickaxe, Promising Pickaxe, 18 Coal, and 19 Gold Ingots; skip the Golden Pickaxe because the Museum will not accept it.",
        19 to "Open your crafting grid and place 1 Gold Ingot above 2 Sticks to craft a Golden Shovel.",
        20 to "Manually run /warp hub and wait until the Hub scoreboard and world finish loading.",
        21 to "Walk into Pet Care and approach Bea at 32, 70, -95.",
        22 to "Right-click Bea through her introduction until the Bee pet shop opens.",
        23 to "Craft 2 Coal Blocks and 2 Gold Blocks, then buy the COMMON Bee for those blocks plus 4,999 coins.",
        24 to "Close Bea's shop and right-click the COMMON Bee item in your inventory to add it to your Pet Menu.",
        25 to "Open the SkyBlock Menu, select Pets, and confirm that the Bee appears in the Pets menu.",
        26 to "Click the Bee in the Pets menu so its lore/status shows that it is summoned.",
        27 to "Walk to the Library beside the Auction House and approach the Librarian at -36, 69, -113.",
        28 to "Right-click the Librarian once and continue the enchanting tutorial dialogue.",
        29 to "Right-click the Librarian again until the Librarian shop inventory opens.",
        30 to "Buy 128 Experience Bottles; buy extra only if the bottles do not take you to vanilla XP level 20.",
        31 to "Close the shop, move beside the Library enchantment tables, look at the floor, and begin throwing bottles.",
        32 to "Keep throwing Experience Bottles at your feet until the green vanilla experience number reaches level 20.",
        33 to "Use a Library Enchantment Table, insert the Golden Shovel, select any available enchant, and take it back.",
        34 to "Manually run /warp hub, then walk toward Jamie in the Village at -36, 68, -39 after the teleport completes.",
        35 to "Right-click Jamie, continue the one-time dialogue, and click the Rogue Sword in the Claim Reward menu so it enters your inventory.",
        36 to "Walk to the Museum at -75, 76, 80, right-click Madame Eleanor, and continue until the main Museum donation menu opens.",
        37 to "Donate these 11 Museum-compatible items: Rookie Axe, Promising Axe, Sweet Axe, Efficient Axe, End Sword, Spider Sword, Undead Sword, Wither Bow, Rookie Pickaxe, Promising Pickaxe, and Rogue Sword. Do not donate the Golden Shovel.",
        38 to "Open the Museum Weapons menu, select the donated Rogue Sword, retrieve it, and leave it in your inventory.",
        39 to "Return to the Hub farm and harvest crops continuously until the Farming skill reaches level 10.",
        40 to "Take all remaining farm drops to the Alchemist and sell every crop stack.",
        41 to "Use the farming launch pad at the edge of the Hub to travel to The Barn.",
        42 to "Cross The Barn and use its far launch pad to enter the Mushroom Desert.",
        43 to "Walk to an exposed sand patch in the Mushroom Desert and begin mining it with the Golden Shovel.",
        44 to "Keep mining sand without leaving the island until the Mining skill reaches level 12.",
        45 to "Manually run /warp hub after Mining 12 and wait for the Hub teleport to finish.",
        46 to "Open the Alchemist shop and sell every Sand stack collected in the Mushroom Desert.",
        47 to "Farm, forage, and sell drops until your purse reaches {treecapTarget}, the current Treecapitator lowest BIN provided by SkyCofl.",
        48 to "Manually run /warp hub and walk toward the Auction House after the Hub loads.",
        49 to "Enter the Auction House and right-click an Auction Agent or the Auction Master to open it.",
        50 to "Search the Auction House for Treecapitator, choose the cheapest affordable BIN, and confirm the purchase.",
        51 to "Move the Treecapitator to your hotbar and select that slot so the axe is visibly held.",
        52 to "Walk into the Hub Forest and approach Lumber Jack at -113, 74, -37.",
        53 to "Right-click Lumber Jack, choose the accepting response, and continue until he requests 20 Oak Logs.",
        54 to "Use the Treecapitator on nearby oak trees until your inventory contains at least 20 Oak Logs.",
        55 to "Return to Lumber Jack while keeping at least 20 Oak Logs in your inventory.",
        56 to "Right-click Lumber Jack and continue the dialogue so exactly 20 Oak Logs are handed in.",
        57 to "Click the Promising Axe reward in the Claim Reward menu and move it into your inventory.",
        58 to "Follow the Forest path and use the launch pad to travel to The Park.",
        59 to "At the Birch Park entrance, walk up to Charlie at -278, 80, -18.",
        60 to "Right-click Charlie and continue his dialogue until he asks for 64 Birch Logs.",
        61 to "Chop birch trees in Birch Park until your inventory contains at least 64 Birch Logs.",
        62 to "Return to Charlie while keeping the 64 Birch Logs in your inventory.",
        63 to "Right-click Charlie and continue the dialogue so the 64 Birch Logs are handed in.",
        64 to "Continue talking to Charlie until his Claim Reward menu appears.",
        65 to "Click Charlie's Trousers in the reward menu and place them in your inventory.",
        66 to "Equip Charlie's Trousers in the leggings slot, then drop or sell every leftover route log.",
        67 to "Right-click Charlie again and continue until the Into the Woods quest-completion message appears.",
        68 to "Click the Travel Scroll to The Park reward in Charlie's menu and keep the scroll in your inventory.",
        69 to "Follow the Park path past Birch Park until the scoreboard location changes to Spruce Woods.",
        70 to "Walk up to Kelly in Spruce Woods at -351, 94, 34.",
        71 to "Right-click Kelly, choose the accepting response, and continue until she requests 128 Spruce Logs.",
        72 to "Chop spruce trees until your inventory contains at least 128 Spruce Logs.",
        73 to "Return to Kelly while keeping all 128 Spruce Logs in your inventory.",
        74 to "Right-click Kelly and continue the dialogue so the 128 Spruce Logs are handed in.",
        75 to "Click Kelly's T-Shirt in the reward menu and place it in your inventory.",
        76 to "Equip Kelly's T-Shirt in the chestplate slot, then drop or sell every leftover route log.",
        77 to "Continue along The Park path until the scoreboard location changes to Dark Thicket.",
        78 to "Hold Sneak before approaching Ryan at -365, 102, -91 and remain sneaking beside the cult.",
        79 to "Continue Ryan's dialogue and select the response that joins the Campfire Cult.",
        80 to "Select the WHAT? response when shown and continue until Ryan explains the Trial of Fire.",
        81 to "Step into the campfire and remain inside it for the complete 10-second Trial of Fire.",
        82 to "After surviving the fire, walk back to Ryan without leaving the Dark Thicket.",
        83 to "Right-click Ryan and continue until he gives the Dark Oak Logs task.",
        84 to "Chop dark oak trees until your inventory contains at least 256 Dark Oak Logs.",
        85 to "Return to Ryan while keeping all 256 Dark Oak Logs in your inventory.",
        86 to "Right-click Ryan and continue the dialogue so the 256 Dark Oak Logs are handed in.",
        87 to "Click the Campfire Initiate Badge in the reward menu and place it in your inventory.",
        88 to "Follow The Park path onward until the scoreboard location changes to Savanna Woodland.",
        89 to "Climb to Melody's Plateau and approach Melody at -412, 109, 71.",
        90 to "Right-click Melody and continue until she asks you to repair the harp with 512 Acacia Logs.",
        91 to "Chop acacia trees until your inventory contains at least 512 Acacia Logs.",
        92 to "Return to Melody's Plateau while keeping all 512 Acacia Logs in your inventory.",
        93 to "Right-click Melody and continue the dialogue so the 512 Acacia Logs are handed in.",
        94 to "Click Melody's Shoes in the reward menu and place them in your inventory.",
        95 to "Equip Melody's Shoes in the boots slot.",
        96 to "Stand directly beside the repaired harp on Melody's Plateau, close enough to interact with it.",
        97 to "Right-click the harp so the Melody song-selection menu is open.",
        98 to "Play all 11 songs in order and score at least 90% on each; reopen the menu to verify every Best score.",
        99 to "Follow the Park route past Savanna Woodland until the scoreboard reports Jungle Island.",
        100 to "Chop jungle trees continuously until the Foraging skill reaches level 12.",
        101 to "Take the route from Jungle Island into Galatea and wait until the scoreboard explicitly reports Galatea.",
        102 to "After Galatea has been loaded for at least 2.5 seconds, manually run /warp hub and wait for the Hub.",
        103 to "Earn or sell enough items until the purse line shows at least 100,000 coins.",
        104 to "Open the Alchemist shop and sell every disposable leftover item while keeping your Treecapitator and guide rewards.",
        105 to "Manually run /warp hub once more to reset at Hub spawn before starting the Abiphone route.",
        106 to "Walk to Abiphones & Co. and approach Alda at 66, 71, -59.",
        107 to "Right-click Alda, choose What's an Abiphone?, then choose I guess. and continue to her shop.",
        108 to "Click Abiphone Basic in Alda's shop and confirm the 97,000-coin purchase.",
        109 to "Continue Alda's post-purchase dialogue until she is added to the Abiphone contacts.",
        110 to "Hold the Abiphone Basic and walk to Taylor inside the Fashion Shop at 22, 71, -46.",
        111 to "Right-click Taylor while holding the Abiphone and complete the contact dialogue until Taylor is added.",
        112 to "Manually run /warp hub to return to spawn before heading to the Community Center.",
        113 to "Hold the Abiphone and approach Elizabeth inside the Community Center near -4, 72, -102.",
        114 to "Right-click Elizabeth with the Abiphone and complete the shown contact prompt until she is added.",
        115 to "After adding Elizabeth, stop moving and wait at least two seconds for the Seraphine sequence to become ready.",
        116 to "Keep the Abiphone held and walk from Elizabeth to Clerk Seraphine in the Community Center.",
        117 to "Right-click Clerk Seraphine with the Abiphone and continue until Seraphine is added to contacts.",
        118 to "Keep the Abiphone held and walk to Plumber Joe in the Village at 57, 70, -78.",
        119 to "Right-click Plumber Joe with the Abiphone and continue until the contact-added confirmation appears.",
    )

    private fun step(number: Int, label: String, detection: String) =
        SkyBlockLevel7Step(number, label, requireNotNull(instructions[number]), detection)

    val steps: List<SkyBlockLevel7Step> = listOf(
        step(1, "Farm fields", "Crop items enter inventory"),
        step(2, "Sell crops to Alchemist", "Crop count falls in the Alchemist shop"),
        step(3, "Walk to Wool Weaver", "Wool Weaver is nearby"),
        step(4, "Open Wool Weaver shop", "Wool Weaver shop opens"),
        step(5, "Buy White Wool", "64 White Wool are held"),
        step(6, "Walk to Carpenter", "Carpenter is nearby"),
        step(7, "Unlock Carpentry (give wool)", "Carpentry dialogue or wool hand-in"),
        step(8, "Walk to Lumber Merchant", "Lumber Merchant is nearby"),
        step(9, "Open Lumber Merchant shop", "Lumber Merchant shop opens"),
        step(10, "Buy axes + sticks", "An axe and at least two sticks are held"),
        step(11, "Walk to Weaponsmith", "Weaponsmith is nearby"),
        step(12, "Open Weaponsmith shop", "Weaponsmith shop opens"),
        step(13, "Buy starter weapons", "Starter weapon purchases are observed"),
        step(14, "Walk to Blacksmith", "Blacksmith is nearby"),
        step(15, "Talk to Blacksmith", "Blacksmith dialogue is received"),
        step(16, "Walk to Mine Merchant", "Mine Merchant is nearby"),
        step(17, "Open Mine Merchant shop", "Mine Merchant shop opens"),
        step(18, "Buy pickaxes + coal + gold", "Pickaxe, coal, and gold are held"),
        step(19, "Craft a Golden Shovel", "Golden Shovel enters inventory"),
        step(20, "Warp to hub (Bea)", "Hub teleport is observed"),
        step(21, "Walk to Bea", "Bea is nearby"),
        step(22, "Open Bea's shop", "Bea shop opens"),
        step(23, "Buy Common Bee pet", "Common Bee pet item is held"),
        step(24, "Add Bee pet", "Bee is added to the pet menu"),
        step(25, "Open pet menu", "Pets menu opens with Bee listed"),
        step(26, "Equip Bee pet", "Bee summon/equipped signal is observed"),
        step(27, "Walk to Librarian", "Librarian is nearby"),
        step(28, "Talk to Librarian", "Librarian dialogue is received"),
        step(29, "Open Librarian shop", "Librarian shop opens"),
        step(30, "Buy Experience Bottles", "Experience Bottles enter inventory"),
        step(31, "Walk to XP throw spot", "Bottle use or XP gain starts"),
        step(32, "Throw XP to level 20", "Vanilla experience level reaches 20"),
        step(33, "Enchant Golden Shovel", "Golden Shovel gains an enchantment"),
        step(34, "Warp to hub (Jamie)", "Hub teleport is observed"),
        step(35, "Claim Rogue Sword from Jamie", "Rogue Sword enters inventory"),
        step(36, "Open Museum with Madame Eleanor", "Madame Eleanor dialogue/menu is received"),
        step(37, "Donate eligible items to Museum", "The Rogue Sword donation is observed"),
        step(38, "Retrieve Rogue Sword", "Rogue Sword returns to inventory"),
        step(39, "Farm to Farming 10", "Farming skill reaches level 10"),
        step(40, "Sell leftovers to Alchemist", "Crop count falls in the Alchemist shop"),
        step(41, "Travel to The Barn", "The Barn location is reported"),
        step(42, "Travel to Mushroom Desert", "Mushroom Desert location is reported"),
        step(43, "Walk to the sand approach", "Sand mining begins"),
        step(44, "Mine sand to Mining 12", "Mining skill reaches level 12"),
        step(45, "Warp to hub (sell sand)", "Hub teleport is observed"),
        step(46, "Sell Sand to Alchemist", "Sand count falls in the Alchemist shop"),
        step(47, "Farm coins for Treecapitator", "Purse reaches the live Treecapitator lowest BIN"),
        step(48, "Warp to hub (Auction)", "Hub teleport is observed"),
        step(49, "Walk to Auction Agent", "Auction NPC or Auction House is reached"),
        step(50, "Buy a Treecapitator", "Treecapitator enters inventory"),
        step(51, "Equip the Treecapitator", "Treecapitator is held"),
        step(52, "Walk to Lumber Jack", "Lumber Jack is nearby"),
        step(53, "Talk to Lumber Jack", "Lumber Jack dialogue is received"),
        step(54, "Forage 20 Oak Logs", "20 Oak Logs are held"),
        step(55, "Return to Lumber Jack", "Lumber Jack is nearby"),
        step(56, "Hand in Oak Logs", "Oak Logs leave inventory near Lumber Jack"),
        step(57, "Claim the Iron Axe", "Axe reward claim is observed"),
        step(58, "Travel to The Park", "The Park location is reported"),
        step(59, "Walk to Charlie", "Charlie is nearby"),
        step(60, "Talk to Charlie", "Charlie dialogue is received"),
        step(61, "Forage 64 Birch Logs", "64 Birch Logs are held"),
        step(62, "Return to Charlie", "Charlie is nearby"),
        step(63, "Hand in Birch Logs", "Birch Logs leave inventory near Charlie"),
        step(64, "Talk to Charlie for reward", "Charlie reward dialogue/menu is received"),
        step(65, "Claim Charlie's Trousers", "Charlie's Trousers enter inventory"),
        step(66, "Equip trousers, drop logs", "Trousers equipped and route logs cleared"),
        step(67, "Finish Into the Woods (Charlie)", "Quest completion or equipped reward is observed"),
        step(68, "Claim Travel Scroll to The Park", "Park Travel Scroll enters inventory"),
        step(69, "Travel to Spruce Woods", "Spruce Woods location is reported"),
        step(70, "Walk to Kelly", "Kelly is nearby"),
        step(71, "Talk to Kelly", "Kelly dialogue is received"),
        step(72, "Forage 128 Spruce Logs", "128 Spruce Logs are held"),
        step(73, "Return to Kelly", "Kelly is nearby"),
        step(74, "Hand in Spruce Logs", "Spruce Logs leave inventory near Kelly"),
        step(75, "Claim Kelly's T-Shirt", "Kelly's T-Shirt enters inventory"),
        step(76, "Equip chestplate, drop logs", "T-Shirt equipped and route logs cleared"),
        step(77, "Travel to Dark Thicket", "Dark Thicket location is reported"),
        step(78, "Sneak up on Ryan", "Player sneaks near Ryan"),
        step(79, "Join The Cult", "Cult joining dialogue is received"),
        step(80, "Acknowledge Ryan (WHAT?)", "Ryan acknowledgement dialogue is received"),
        step(81, "Trial of Fire (campfire)", "First campfire trial completion is received"),
        step(82, "Walk back to Ryan", "Ryan is nearby"),
        step(83, "Talk to Ryan (logs task)", "Ryan's next-task dialogue is received"),
        step(84, "Forage 256 Dark Oak Logs", "256 Dark Oak Logs are held"),
        step(85, "Return to Ryan", "Ryan is nearby"),
        step(86, "Hand in Dark Oak Logs", "Dark Oak Logs leave inventory near Ryan"),
        step(87, "Claim Campfire Initiate Badge", "Campfire Initiate Badge is obtained"),
        step(88, "Travel to Savanna Woodland", "Savanna Woodland location is reported"),
        step(89, "Walk to Melody", "Melody is nearby"),
        step(90, "Talk to Melody", "Melody dialogue is received"),
        step(91, "Forage 512 Acacia Logs", "512 Acacia Logs are held"),
        step(92, "Return to Melody", "Melody is nearby"),
        step(93, "Hand in Acacia Logs", "Acacia Logs leave inventory near Melody"),
        step(94, "Claim Melody's Shoes", "Melody's Shoes enter inventory"),
        step(95, "Equip shoes", "Melody's Shoes are equipped"),
        step(96, "Stand on the harp spot", "Harp interaction begins"),
        step(97, "Open the Harp", "Melody/Harp menu opens"),
        step(98, "Play all 11 Harp songs", "All 11 songs show at least 90% completion"),
        step(99, "Travel to Jungle Island", "Jungle Island location is reported"),
        step(100, "Forage to Foraging 12", "Foraging skill reaches level 12"),
        step(101, "Travel to Galatea", "Galatea location is reported"),
        step(102, "Wait, then warp to hub", "Wait completes and Hub teleport is observed"),
        step(103, "Top up to 100k coins", "Purse reaches 100,000 coins"),
        step(104, "Sell inventory to Alchemist", "Inventory falls in the Alchemist shop"),
        step(105, "Warp to hub for Alda", "Hub teleport is observed"),
        step(106, "Walk to Alda", "Alda is nearby"),
        step(107, "Talk to Alda", "Alda dialogue/shop is received"),
        step(108, "Buy the Abiphone", "Abiphone Basic enters inventory"),
        step(109, "Add Alda contact", "Alda appears in Abiphone contacts"),
        step(110, "Walk to Taylor", "Taylor is nearby"),
        step(111, "Add Taylor contact", "Taylor appears in Abiphone contacts"),
        step(112, "Warp to hub (Elizabeth)", "Hub teleport is observed"),
        step(113, "Walk to Elizabeth", "Elizabeth is nearby"),
        step(114, "Add Elizabeth contact", "Elizabeth appears in Abiphone contacts"),
        step(115, "Pause before Seraphine", "Brief pause in the Community Center"),
        step(116, "Walk to Seraphine", "Clerk Seraphine is nearby"),
        step(117, "Add Seraphine contact", "Seraphine appears in Abiphone contacts"),
        step(118, "Stand at Plumber Joe", "Plumber Joe is nearby"),
        step(119, "Add Plumber Joe contact", "Plumber Joe appears in Abiphone contacts"),
    )

    init {
        require(steps.size == 119)
        require(steps.map { it.number } == (1..119).toList())
    }

    fun step(number: Int): SkyBlockLevel7Step? = steps.getOrNull(number - 1)

    /** True when the active step has enough client-visible evidence to advance. */
    fun isComplete(
        number: Int,
        observation: SkyBlockGuideObservation,
        baseline: SkyBlockGuideBaseline,
    ): Boolean = with(Policy(observation, baseline)) {
        when (number) {
            1 -> itemCount(CROPS) > 0
            2 -> sold(CROPS, "alchemist")
            3 -> near("wool weaver") || screen("wool weaver")
            4 -> screen("wool weaver")
            5 -> itemCount(WHITE_WOOL) >= 64
            6 -> near("carpenter") || chat("carpenter")
            7 -> chatAll("carpenter", "carpentry") || decreased(WHITE_WOOL)
            8 -> near("lumber merchant") || screen("lumber merchant", "lumberjack")
            9 -> screen("lumber merchant", "lumberjack")
            10 -> itemCount(STICKS) >= 32 && hasAllItemNames("rookie axe", "promising axe", "sweet axe", "efficient axe")
            11 -> near("weaponsmith") || screen("weaponsmith")
            12 -> screen("weaponsmith")
            13 -> hasAllItemNames("undead sword", "end sword", "spider sword", "wither bow")
            14 -> near("blacksmith") || chat("blacksmith")
            15 -> chat("[npc] blacksmith", "blacksmith:") || screen("reforge", "blacksmith")
            16 -> near("mine merchant", "mining merchant") || screen("mine merchant", "mining merchant")
            17 -> screen("mine merchant", "mining merchant")
            18 -> hasAllItemNames("rookie pickaxe", "promising pickaxe") &&
                materialUnits("coal") >= 18 && materialUnits("gold") >= 19
            19 -> hasItemIdOrName(setOf("GOLDEN_SHOVEL"), setOf("golden shovel"))
            20, 34, 45, 48, 105, 112 -> observation.teleported && location("hub", "village")
            21 -> near("bea") || screen("bea")
            22 -> screen("bea")
            23 -> hasItemIdOrName(setOf("BEE;0"), setOf("bee"))
            24 -> chatAll("bee", "pet menu") || chatAll("bee", "added") || screenItem("bee") && !hasBeeItem()
            25 -> screen("pets") && screenItem("bee")
            26 -> chatAll("bee", "summon") || chatAll("bee", "equipped") || screenLore("bee", "selected pet", "click to despawn")
            27 -> near("librarian") || screen("librarian")
            28 -> chat("[npc] librarian", "librarian:")
            29 -> screen("librarian")
            30 -> itemCount(EXPERIENCE_BOTTLES) >= 128
            31 -> observation.experienceLevel > baseline.experienceLevel || decreased(EXPERIENCE_BOTTLES)
            32 -> observation.experienceLevel >= 20
            33 -> observation.inventory.any { it.enchanted && normalize(it.name).contains("golden shovel") } || chat("enchanted")
            35 -> hasItemIdOrName(ROGUE_SWORD, setOf("rogue sword"))
            36 -> chat("madame eleanor", "golds worth", "goldsworth") || screen("museum")
            37 -> (screen("museum") && decreased(ROGUE_SWORD)) || chatAll("rogue sword", "donat")
            38 -> hasItemIdOrName(setOf("ROGUE_SWORD"), setOf("rogue sword"))
            39 -> skill("farming", 10)
            40 -> sold(CROPS, "alchemist") || (screen("alchemist") && itemCount(CROPS) == 0)
            41 -> location("the barn", "barn")
            42 -> location("mushroom desert")
            43 -> itemCount(SAND) > baselineCount(SAND) || chat("sand")
            44 -> skill("mining", 12)
            46 -> sold(SAND, "alchemist")
            47 -> observation.treecapLowestBin?.let { it > 0L && (observation.purse ?: 0L) >= it } == true
            49 -> near("auction agent", "auction master", "auctioneer") || location("auction house") || screen("auction")
            50 -> hasItemIdOrName(setOf("TREECAPITATOR", "TREECAPITATOR_AXE"), setOf("treecapitator"))
            51 -> held("treecapitator", "TREECAPITATOR", "TREECAPITATOR_AXE")
            52 -> near("lumber jack", "lumberjack") || chat("lumber jack", "lumberjack")
            53 -> chat("[npc] lumber jack", "lumber jack:", "[npc] lumberjack", "lumberjack:")
            54 -> itemCount(OAK_LOGS) >= 20
            55 -> near("lumber jack", "lumberjack")
            56 -> decreased(OAK_LOGS) && nearOrChat("lumber jack", "lumberjack")
            57 -> chatAll("claim", "axe") || nameIncreased("promising axe", "iron axe")
            58 -> location("the park", "birch park")
            59 -> near("charlie") || chat("charlie")
            60 -> chat("[npc] charlie", "charlie:")
            61 -> itemCount(BIRCH_LOGS) >= 64
            62 -> near("charlie")
            63 -> decreased(BIRCH_LOGS) && nearOrChat("charlie")
            64 -> chatAll("charlie", "reward") || screen("claim reward")
            65 -> hasItemIdOrName(setOf("CHARLIE_TROUSERS"), setOf("charlie's trousers", "charlies trousers"))
            66 -> equipped("legs", "charlie") && itemCount(ROUTE_LOGS) == 0
            67 -> chat("into the woods", "quest complete")
            68 -> hasItemName("travel scroll to the park") || chatAll("travel scroll", "park")
            69 -> location("spruce woods")
            70 -> near("kelly") || chat("kelly")
            71 -> chat("[npc] kelly", "kelly:")
            72 -> itemCount(SPRUCE_LOGS) >= 128
            73 -> near("kelly")
            74 -> decreased(SPRUCE_LOGS) && nearOrChat("kelly")
            75 -> hasItemIdOrName(setOf("KELLY_TSHIRT"), setOf("kelly's t-shirt", "kellys t-shirt"))
            76 -> equipped("chest", "kelly") && itemCount(ROUTE_LOGS) == 0
            77 -> location("dark thicket")
            78 -> observation.sneaking && near("ryan")
            79 -> chatAll("cult", "join") || chatAll("cult", "welcome")
            80 -> chat("what?", "what?!", "acknowledge")
            81 -> chatAll("survived", "campfire") || chatAll("trial of fire", "complete")
            82 -> near("ryan")
            83 -> chatAll("ryan", "dark oak") || chatAll("ryan", "logs")
            84 -> itemCount(DARK_OAK_LOGS) >= 256
            85 -> near("ryan")
            86 -> decreased(DARK_OAK_LOGS) && nearOrChat("ryan")
            87 -> hasItemIdOrName(setOf("CAMPFIRE_TALISMAN_1", "CAMPFIRE_INITIATE_BADGE"), setOf("campfire initiate badge")) || chatAll("campfire initiate badge", "here")
            88 -> location("savanna woodland", "savanna")
            89 -> near("melody") || chat("melody")
            90 -> chat("[npc] melody", "melody ♫:", "melody:")
            91 -> itemCount(ACACIA_LOGS) >= 512
            92 -> near("melody")
            93 -> decreased(ACACIA_LOGS) && nearOrChat("melody")
            94 -> hasItemIdOrName(setOf("MELODY_SHOES"), setOf("melody's shoes", "melodys shoes"))
            95 -> equipped("feet", "melody")
            96 -> screen("harp", "melody") || chat("pick a song")
            97 -> screen("harp", "melody") && observation.screenItems.size >= 7
            98 -> allHarpSongsComplete() || hasItemIdOrName(setOf("MELODY_HAIR"), setOf("melody's hair")) || chatAll("every song", "perfect")
            99 -> location("jungle island", "jungle")
            100 -> skill("foraging", 12)
            101 -> location("galatea")
            102 -> observation.elapsedOnStepMs >= 2_500L && observation.teleported && location("hub", "village")
            103 -> (observation.purse ?: 0L) >= 100_000L
            104 -> soldAllAt("alchemist")
            106 -> near("alda") || screen("alda")
            107 -> chat("[npc] alda", "alda:") || screen("alda")
            108 -> hasItemIdOrName(setOf("ABIPHONE_BASIC"), setOf("abiphone basic"))
            109 -> contact("alda")
            110 -> near("taylor") || contact("taylor")
            111 -> contact("taylor")
            113 -> near("elizabeth") || contact("elizabeth")
            114 -> contact("elizabeth")
            115 -> observation.elapsedOnStepMs >= 2_000L && (near("seraphine", "elizabeth") || location("community center"))
            116 -> near("clerk seraphine", "seraphine") || contact("seraphine")
            117 -> contact("clerk seraphine", "seraphine")
            118 -> near("plumber joe") || contact("plumber joe")
            119 -> contact("plumber joe")
            else -> false
        }
    }

    private class Policy(
        private val observation: SkyBlockGuideObservation,
        private val baseline: SkyBlockGuideBaseline,
    ) {
        private val chat = normalize(observation.chat)
        private val title = normalize(observation.screenTitle)
        private val nearby = observation.nearbyNames.map(::normalize)
        private val scoreboard = observation.scoreboard.map(::normalize)

        fun chat(vararg terms: String): Boolean = terms.any { normalize(it) in chat }
        fun chatAll(vararg terms: String): Boolean = terms.all { normalize(it) in chat }
        fun screen(vararg terms: String): Boolean = terms.any { normalize(it) in title }
        fun screenItem(vararg terms: String): Boolean = observation.screenItems.any { item -> terms.any { normalize(it) in normalize(item.name) } }
        fun screenLore(itemTerm: String, vararg loreTerms: String): Boolean = observation.screenItems.any { item ->
            normalize(item.name).contains(normalize(itemTerm)) && item.lore.any { line -> loreTerms.any { normalize(it) in normalize(line) } }
        }
        fun near(vararg terms: String): Boolean = nearby.any { name -> terms.any { normalize(it) in name } }
        fun nearOrChat(vararg terms: String): Boolean = near(*terms) || chat(*terms)
        fun location(vararg terms: String): Boolean = scoreboard.any { line -> terms.any { normalize(it) in line } }
        fun skill(name: String, target: Int): Boolean = (observation.skillLevels[name.lowercase()] ?: 0) >= target

        fun itemCount(match: Set<String>): Int = observation.inventory.sumOf { item -> if (matches(item, match)) item.count else 0 }
        fun baselineCount(match: Set<String>): Int = baseline.inventory.sumOf { item -> if (matches(item, match)) item.count else 0 }
        fun decreased(match: Set<String>): Boolean = baselineCount(match) > itemCount(match)
        fun sold(match: Set<String>, shop: String): Boolean = decreased(match) && (screen(shop) || chat("sold", shop))

        fun hasItemName(vararg terms: String): Boolean = observation.inventory.any { item -> terms.any { normalize(it) in normalize(item.name) } }
        fun hasAllItemNames(vararg terms: String): Boolean = terms.all { term ->
            val wanted = normalize(term)
            observation.inventory.any { item ->
                normalize(item.name) == wanted || normalize(item.id.replace('_', ' ')) == wanted
            }
        }
        fun materialUnits(material: String): Int = observation.inventory.sumOf { item ->
            val id = item.id.uppercase()
            val name = normalize(item.name)
            when {
                id == "${material.uppercase()}_BLOCK" || name.contains("block of ${normalize(material)}") -> item.count * 9
                id == material.uppercase() || id == "${material.uppercase()}_INGOT" || name == normalize(material) || name == "${normalize(material)} ingot" -> item.count
                else -> 0
            }
        }
        fun nameIncreased(vararg terms: String): Boolean {
            fun count(items: List<SkyBlockGuideItem>) = items.sumOf { item ->
                if (terms.any { normalize(it) in normalize(item.name) }) item.count else 0
            }
            return count(observation.inventory) > count(baseline.inventory)
        }
        fun hasItemIdOrName(ids: Set<String>, names: Set<String>): Boolean = observation.inventory.any { item ->
            item.id.uppercase() in ids || names.any { normalize(it) in normalize(item.name) }
        }
        fun held(vararg terms: String): Boolean = observation.heldItem?.let { item ->
            terms.any { normalize(it) in normalize(item.name) || it.uppercase() == item.id.uppercase() }
        } == true
        fun equipped(slot: String, term: String): Boolean = observation.armor[slot]?.let { normalize(it.name).contains(normalize(term)) || normalize(it.id).contains(normalize(term)) } == true

        fun hasBeeItem(): Boolean = observation.inventory.any { item -> item.id.uppercase().startsWith("BEE;") || normalize(item.name).contains("bee") }

        fun contact(vararg names: String): Boolean {
            val normalizedNames = names.map(::normalize)
            val chatMatch = normalizedNames.any { it in chat } && ("contact" in chat) && ("added" in chat || "already" in chat)
            val screenMatch = (screen("abiphone", "contacts")) && observation.screenItems.any { item -> normalizedNames.any { it in normalize(item.name) } }
            return chatMatch || screenMatch
        }

        fun allHarpSongsComplete(): Boolean {
            val completed = observation.screenItems.count { item ->
                HARP_SONGS.any { song -> normalize(item.name).contains(song) } &&
                    item.lore.any { line -> parsePercent(line)?.let { it >= 90 } == true }
            }
            return completed >= HARP_SONGS.size
        }

        fun totalInventoryCount(): Int = observation.inventory.sumOf { it.count }
        fun baselineTotal(): Int = baseline.inventory.sumOf { it.count }
        fun soldAllAt(shop: String): Boolean = (screen(shop) || chat(shop)) && (totalInventoryCount() < baselineTotal() || (observation.purse ?: 0L) > (baseline.purse ?: Long.MAX_VALUE))

        private fun matches(item: SkyBlockGuideItem, match: Set<String>): Boolean {
            val id = item.id.uppercase()
            val name = normalize(item.name)
            return match.any { token -> token.uppercase() == id || normalize(token.replace('_', ' ')) in name }
        }
    }

    internal fun parsePercent(text: String): Int? =
        Regex("""(\d{1,3})\s*%""").find(text)?.groupValues?.get(1)?.toIntOrNull()

    internal fun parsePurse(lines: Iterable<String>): Long? {
        for (line in lines) {
            val match = Regex("""(?i)(?:purse|piggy)\s*:\s*([\d,.]+)\s*([kmb]?)""").find(line) ?: continue
            val base = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            val multiplier = when (match.groupValues[2].lowercase()) {
                "k" -> 1_000.0
                "m" -> 1_000_000.0
                "b" -> 1_000_000_000.0
                else -> 1.0
            }
            return (base * multiplier).toLong()
        }
        return null
    }

    internal fun parseSkillLevel(text: String): Pair<String, Int>? {
        val normalized = normalize(text)
        val match = Regex("""(?:skill level up\s+)?(farming|mining|foraging)\s+([0-9]+|[ivxlcdm]+)""").find(normalized) ?: return null
        val level = match.groupValues[2].toIntOrNull() ?: romanToInt(match.groupValues[2])
        return match.groupValues[1] to level
    }

    internal fun romanToInt(value: String): Int {
        val values = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)
        var total = 0
        var previous = 0
        for (char in value.lowercase().reversed()) {
            val current = values[char] ?: return 0
            if (current < previous) total -= current else total += current
            previous = current
        }
        return total
    }

    internal fun normalize(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD)
        .lowercase()
        .replace(Regex("""\p{M}+"""), "")
        .replace(Regex("""\p{C}"""), "")
        .replace(Regex("""[^a-z0-9%+';:_\-\s?!]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private val CROPS = setOf("WHEAT", "CARROT_ITEM", "CARROT", "POTATO_ITEM", "POTATO", "PUMPKIN", "MELON", "MELON_SLICE", "SEEDS", "WHEAT_SEEDS")
    private val WHITE_WOOL = setOf("WOOL", "WHITE_WOOL", "white wool")
    private val STICKS = setOf("STICK", "stick")
    private val COAL = setOf("COAL", "COAL_BLOCK", "coal", "block of coal")
    private val GOLD = setOf("GOLD_INGOT", "GOLD_BLOCK", "gold ingot", "block of gold")
    private val ROGUE_SWORD = setOf("ROGUE_SWORD", "rogue sword")
    private val EXPERIENCE_BOTTLES = setOf("EXP_BOTTLE", "EXPERIENCE_BOTTLE", "experience bottle")
    private val SAND = setOf("SAND", "sand")
    private val OAK_LOGS = setOf("LOG", "OAK_LOG", "oak log")
    private val BIRCH_LOGS = setOf("LOG:2", "BIRCH_LOG", "birch log")
    private val SPRUCE_LOGS = setOf("LOG:1", "SPRUCE_LOG", "spruce log")
    private val DARK_OAK_LOGS = setOf("LOG_2:1", "DARK_OAK_LOG", "dark oak log")
    private val ACACIA_LOGS = setOf("LOG_2", "ACACIA_LOG", "acacia log")
    private val ROUTE_LOGS = OAK_LOGS + BIRCH_LOGS + SPRUCE_LOGS + DARK_OAK_LOGS + ACACIA_LOGS
    private val HARP_SONGS = listOf(
        "hymn to the joy", "frere jacques", "amazing grace", "brahm's lullaby",
        "happy birthday to you", "greensleeves", "geothermy", "minuet",
        "joy to the world", "godly imagination", "la vie en rose",
    )
}
