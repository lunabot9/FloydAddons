package gg.floyd.features.impl.pvp.experiments

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/** Integrated-server QA. Inputs travel through the normal Minecraft connection. */
object ExperimentFixture {
    @Volatile private var diagnostics: Map<String, Any?> = emptyMap()
    private var tableFixtureActive = false
    private var tableLabel: java.util.UUID? = null
    val isActive get() = tableFixtureActive || diagnostics["active"] == true
    fun state() = diagnostics
    private var installed = false
    private var active: FixtureMenu? = null
    fun table(ready: Boolean) {
        val mc = Minecraft.getInstance()
        val server = mc.singleplayerServer ?: error("Table fixture requires an integrated server")
        val uuid = mc.player?.uuid ?: error("No player")
        tableFixtureActive = true
        server.execute {
            val player = server.playerList.getPlayer(uuid) ?: return@execute
            val level = player.level()
            val pos = player.blockPosition().offset(0, 0, 2)
            level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand::class.java, player.boundingBox.inflate(10.0))
                .filter { it.uuid == tableLabel }.forEach { it.discard() }
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState(), 3)
            val label = net.minecraft.world.entity.decoration.ArmorStand(level, pos.x + .5, pos.y + 1.2, pos.z + .5)
            label.isInvisible = true; label.isNoGravity = true
            label.customName = Component.literal("Experimentation Table - ${if (ready) "Click to experiment!" else "12h 05m"}")
                .withStyle(if (ready) net.minecraft.ChatFormatting.YELLOW else net.minecraft.ChatFormatting.RED)
            label.isCustomNameVisible = true; label.addTag("floyd_experiment_fixture")
            level.addFreshEntity(label); tableLabel = label.uuid
        }
    }
    fun open(name: String, goalRound: Int = 12) {
        val mc = Minecraft.getInstance()
        val server = mc.singleplayerServer ?: error("Experiment fixtures require an integrated server")
        val uuid = mc.player?.uuid ?: error("No local player")
        val game = ExperimentGame.valueOf(name.uppercase())
        require(goalRound in 3..15)
        if (!installed) {
            installed = true
            ServerTickEvents.END_SERVER_TICK.register { active?.tick() }
        }
        server.execute {
            val player = server.playerList.getPlayer(uuid) ?: return@execute
            active = null
            val label = game.name.lowercase().replaceFirstChar { it.uppercase() }
            fun openGame() {
                player.openMenu(SimpleMenuProvider({ id, inventory, _ ->
                    FixtureMenu(id, inventory, player, game, goalRound).also { active = it }
                }, Component.literal("$label (Metaphysical)")))
            }
            if (game == ExperimentGame.SUPERPAIRS) openGame()
            else {
                tableFixtureActive = true
                val stakes = SimpleContainer(54)
                stakes.setItem(24, stack(item("magenta_dye"), "Metaphysical Experiment", game.name,
                    "${if (game == ExperimentGame.CHRONOMATRON) "Chain" else "Series"} of $goalRound: +3 Clicks", "Click to play!"))
                player.openMenu(SimpleMenuProvider({ id, inventory, _ ->
                    object : ChestMenu(MenuType.GENERIC_9x6, id, inventory, stakes, 6) {
                        override fun clicked(slot: Int, button: Int, input: ContainerInput, player: Player) {
                            if (slot == 24 && button == 0 && input == ContainerInput.PICKUP) openGame()
                            else sendAllDataToRemote()
                        }
                    }
                }, Component.literal("$label Stakes")))
            }
        }
    }
    fun bottles() {
        val mc = Minecraft.getInstance()
        val server = mc.singleplayerServer ?: error("Integrated-server QA only")
        val uuid = mc.player?.uuid ?: error("No player")
        tableFixtureActive = true
        server.execute {
            val player = server.playerList.getPlayer(uuid) ?: return@execute
            active = null
            player.experienceLevel = 0
            player.connection.send(net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0f, 0, 0))
            var consumed = 0
            var invalid = 0
            fun publish() { diagnostics = mapOf("active" to true, "bottleFixture" to true, "inventoryBottlesConsumed" to consumed, "invalidClicks" to invalid) }
            fun open(page: String) {
                val shop = page == "Bottles of Enchanting"
                val rows = if (shop) 4 else 6
                val contents = SimpleContainer(rows * 9)
                if (shop) {
                    contents.setItem(14, stack(Items.EXPERIENCE_BOTTLE, "Titanic Experience Bottle", "Cost", "Titanic Experience Bottle \u2714", "Click to consume!"))
                    contents.setItem(30, stack(Items.ARROW, "Go Back"))
                } else if (page.contains("Stakes")) {
                    contents.setItem(24, stack(item("magenta_dye"), "Metaphysical Experiment", "Starting cost: 100 XP Levels", "Click to play!"))
                    contents.setItem(39, stack(Items.ARROW, "Go Back"))
                } else contents.setItem(50, stack(Items.EXPERIENCE_BOTTLE, "Experience Bottles", "Click to open!"))
                player.openMenu(SimpleMenuProvider({ id, inventory, _ ->
                    object : ChestMenu(if (shop) MenuType.GENERIC_9x4 else MenuType.GENERIC_9x6, id, inventory, contents, rows) {
                        override fun clicked(slot: Int, button: Int, input: ContainerInput, playerInput: Player) {
                            if (button != 0 || input != ContainerInput.PICKUP) { invalid++; publish(); return }
                            when {
                                shop && slot == 14 && consumed == 0 -> {
                                    consumed++; player.giveExperienceLevels(200)
                                    player.connection.send(net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0f, player.totalExperience, player.experienceLevel))
                                    sendAllDataToRemote()
                                }
                                shop && slot == 30 -> open("Experimentation Table")
                                !shop && slot == 39 -> open("Experimentation Table")
                                !shop && slot == 50 -> open("Bottles of Enchanting")
                                else -> { invalid++; sendAllDataToRemote() }
                            }
                            publish()
                        }
                    }
                }, Component.literal(page)))
            }
            open("Superpairs Stakes"); publish()
        }
    }
    private fun item(id: String) = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", id))
    private fun stack(item: Item, name: String, vararg lore: String) = ItemStack(item).also {
        it.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        if (lore.isNotEmpty()) it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal)))
    }
    private class FixtureMenu(id: Int, inventory: net.minecraft.world.entity.player.Inventory,
        val owner: ServerPlayer, val game: ExperimentGame, val goal: Int,
        val board: SimpleContainer = SimpleContainer(54)
    ) : ChestMenu(MenuType.GENERIC_9x6, id, inventory, board, 6) {
        private val positions = listOf(11, 13, 13, 15, 29, 11, 31, 33, 15, 31, 29, 33, 13, 15, 11)
        private val tiles = listOf(11, 13, 15, 29, 31, 33)
        private var round = 1
        private var age = 0
        private var watch = true
        private var cursor = 0
        private var due = -1
        private var completed = 0
        private var correct = 0
        private var invalid = 0
        private var closed = false
        private var rewardMenuOpened = false
        private var addonResultOpened = false
        private var rewardsClaimed = false
        private var clicksLeft = 18
        private var instant = false
        private var first: Int? = null
        private var hideAt = -1
        private var powerupsApplied = 0
        private val revealed = mutableSetOf<Int>()
        private val scouted = mutableSetOf<Int>()
        private var nonBookRevisitsAfterScouting = 0
        private val collected = mutableSetOf<Int>()
        private val pairSlots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23)
        private val rewards = mapOf(
            10 to stack(Items.EXPERIENCE_BOTTLE, "100,000 Enchanting XP"),
            11 to stack(Items.EXPERIENCE_BOTTLE, "200,000 Enchanting XP"),
            12 to stack(Items.ENCHANTED_BOOK, "Enchanted Book", "XX ULTRA-RARE BOOK! XX", "Sharpness VII"),
            13 to stack(Items.EXPERIENCE_BOTTLE, "100,000 Enchanting XP"),
            14 to stack(Items.ENCHANTED_BOOK, "Enchanted Book", "Growth VI"),
            15 to stack(Items.EXPERIENCE_BOTTLE, "200,000 Enchanting XP"),
            16 to stack(Items.ENCHANTED_BOOK, "Enchanted Book", "XX ULTRA-RARE BOOK! XX", "Sharpness VII"),
            19 to stack(Items.ENCHANTED_BOOK, "Enchanted Book", "Growth VI"),
            20 to stack(Items.FEATHER, "Gained +3 Clicks", "Instant powerup!"),
            21 to stack(Items.FEATHER, "Instant Find", "Superpairs Powerup"),
            22 to stack(Items.PLAYER_HEAD, "Nadeshiko Dye"),
            23 to stack(Items.PLAYER_HEAD, "Nadeshiko Dye"))
        init { render(); publish() }
        private fun sequence() = if (game == ExperimentGame.CHRONOMATRON) positions.take(round) else
            (9..44).toList().shuffled(kotlin.random.Random(round)).take(round)
        private fun render() {
            for (slot in 0..53) board.setItem(slot, stack(item("black_stained_glass_pane"), " "))
            if (game == ExperimentGame.SUPERPAIRS) {
                for (slot in pairSlots) board.setItem(slot, if (slot in revealed) rewards.getValue(slot).copy() else
                    stack(item("gray_stained_glass_pane"), if (instant) "Next button is instantly rewarded!" else "?"))
                board.setItem(4, stack(Items.CLOCK, "Remaining Clicks: $clicksLeft"))
            } else {
                val seq = sequence()
                val slots = if (game == ExperimentGame.CHRONOMATRON) tiles else seq
                for (slot in slots) {
                    val index = seq.indexOf(slot)
                    val flash = game == ExperimentGame.CHRONOMATRON && !watch && seq.last() == slot
                    val rendered = when {
                        flash -> stack(item("red_terracotta"), "Color $slot").also { it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true) }
                        game == ExperimentGame.ULTRASEQUENCER && watch -> stack(Items.BONE_MEAL, "${index + 1}").also { it.count = index + 1 }
                        else -> stack(item("red_stained_glass_pane"), "Color $slot")
                    }
                    board.setItem(slot, rendered)
                    if (game == ExperimentGame.CHRONOMATRON) board.setItem(slot + 9, rendered.copy())
                }
                if (game == ExperimentGame.CHRONOMATRON) board.setItem(4, stack(Items.BOOKSHELF, "Round: $round"))
                board.setItem(49, stack(if (watch) Items.GLOWSTONE else Items.CLOCK, if (watch) "Remember the pattern!" else "Timer: 99s"))
            }
            broadcastChanges()
        }
        fun tick() {
            if (closed) {
                if (game == ExperimentGame.SUPERPAIRS && !rewardMenuOpened) openRewards()
                else if (game != ExperimentGame.SUPERPAIRS && ExperimentFlowFixture.active && !addonResultOpened) {
                    addonResultOpened = true; ExperimentFlowFixture.addonResult(owner, game)
                }
                return
            }
            if (owner.containerMenu !== this) return
            age++
            if (game != ExperimentGame.SUPERPAIRS) {
                if (watch && age > 20) {
                    watch = false; age = 0
                } else if (!watch && due >= 0 && age >= due) {
                    completed = round; round++; cursor = 0; watch = true; age = 0; due = -1
                }
            } else if (hideAt >= 0 && age >= hideAt) {
                revealed.retainAll(collected); first = null; hideAt = -1
            }
            render(); publish()
        }
        override fun clicked(slot: Int, button: Int, input: ContainerInput, player: Player) {
            if (button != 0 || input != ContainerInput.PICKUP || closed) { invalid++; publish(); return }
            if (game == ExperimentGame.SUPERPAIRS) pairClick(slot)
            else {
                val seq = sequence()
                if (watch || cursor >= seq.size || slot != seq[cursor]) invalid++
                else { correct++; cursor++; if (cursor == seq.size) due = age + 6 }
            }
            render(); sendAllDataToRemote(); publish()
        }
        private fun pairClick(slot: Int) {
            if (slot !in pairSlots || slot in revealed || hideAt >= 0 || clicksLeft <= 0) { invalid++; return }
            if (scouted.size == pairSlots.size && slot in scouted && rewards[slot]?.item != Items.ENCHANTED_BOOK && slot !in listOf(20, 21)) nonBookRevisitsAfterScouting++
            scouted += slot
            correct++; clicksLeft--; revealed += slot
            if (slot == 20 || slot == 21) {
                powerupsApplied++; collected += slot
                if (slot == 20) clicksLeft += 3 else instant = true
                return
            }
            val key = rewards.getValue(slot)
            fun matches(other: Int) = ItemStack.matches(key, rewards.getValue(other))
            if (instant) {
                instant = false; collected += slot
                pairSlots.firstOrNull { it != slot && it !in collected && matches(it) }?.let { collected += it; revealed += it }
            } else if (first == null) first = slot
            else {
                val previous = first!!; first = null
                if (matches(previous)) collected += listOf(previous, slot) else hideAt = age + 9
            }
            scouted += revealed
            if (collected.size == pairSlots.size) clicksLeft = 0
        }
        override fun removed(player: Player) { closed = true; publish(); super.removed(player) }
        private fun openRewards() {
            rewardMenuOpened = true
            val rewards = SimpleContainer(27)
            rewards.setItem(13, stack(Items.PLAYER_HEAD, "Superpairs", "Experiment over", "Click to claim rewards!"))
            owner.openMenu(SimpleMenuProvider({ id, inventory, _ ->
                object : ChestMenu(MenuType.GENERIC_9x3, id, inventory, rewards, 3) {
                    override fun clicked(slot: Int, button: Int, input: ContainerInput, player: Player) {
                        if (slot == 13 && button == 0 && input == ContainerInput.PICKUP && !rewardsClaimed) {
                            rewardsClaimed = true; ExperimentFlowFixture.claimed(game); publish(); owner.closeContainer()
                        } else { invalid++; publish(); sendAllDataToRemote() }
                    }
                }
            }, Component.literal("Superpairs Rewards")))
            publish()
        }
        private fun publish() {
            diagnostics = mapOf("active" to (!closed || (rewardMenuOpened && !rewardsClaimed)), "game" to game.name, "round" to round, "goalRound" to goal,
                "completedRound" to completed, "earnedBonus" to if (completed >= goal) 3 else 2,
                "correctClicks" to correct, "invalidClicks" to invalid, "closed" to closed,
                "booksCollected" to collected.count { rewards[it]?.item == Items.ENCHANTED_BOOK } / 2,
                "priorityRarePairsCollected" to collected.count { it == 22 || it == 23 } / 2,
                "powerupsApplied" to powerupsApplied, "rewardMenuOpened" to rewardMenuOpened, "rewardsClaimed" to rewardsClaimed,
                "scoutedCards" to scouted.size, "nonBookRevisitsAfterScouting" to nonBookRevisitsAfterScouting, "collectedCards" to collected.size, "clicksLeft" to clicksLeft)
        }
    }
}
