package gg.floyd.features.impl.pvp

import gg.floyd.clickgui.settings.impl.*
import gg.floyd.events.ScreenEvent
import gg.floyd.events.TickEvent
import gg.floyd.events.WindowFocusEvent
import gg.floyd.events.WorldEvent
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.impl.pvp.experiments.*
import gg.floyd.utils.font.ClickGuiFont
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.drawString
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack

object FloydAutoExperiments : Module("Auto Experiments", category = Category.PVP,
    description = "Completes Chronomatron, Ultrasequencer, and Superpairs using observed server updates and normal container clicks.") {
    private val clickDelay by NumberSetting("Click Delay", 400, 200, 1500, 25, desc = "Minimum milliseconds between normal clicks. Superpairs also waits for each reveal from the server.", unit = "ms")
    private val booksFirst by BooleanSetting("Prioritize Books", true, desc = "Scout unseen cards first, then match ultra-rare books, other books, and remaining rewards.")
    private val superpairs by BooleanSetting("Superpairs", true, desc = "Play the Superpairs memory game and remember revealed rewards.")
    private val autoAdvance by BooleanSetting("Auto Advance", true, desc = "Select the next experiment and highest unlocked stakes from the open table.")
    private val autoRenew by BooleanSetting("Auto Renew", true, desc = "Spend the displayed XP and Bits to renew exhausted charges when the table offers a renewal.")
    private val autoBuyXp by BooleanSetting("Auto Buy XP", true, desc = "Buy and instantly consume up to two Titanic bottles per refill when experiment or renewal XP is short.")
    private val bottlePriceLimit by NumberSetting("Max Bottle Price", 1000000, 100000, 5000000, 50000, desc = "Maximum Bazaar coins per Titanic bottle.")
    private val autoOpen by BooleanSetting("Auto Open", true, desc = "Open a nearby available Experimentation Table when you look at it. Never moves your camera. Red cooldown text blocks opening.")
    private val autoClaim by BooleanSetting("Claim Rewards", true, desc = "Claim completed experiment rewards through the normal reward menu.")
    private val showStatus by BooleanSetting("Show Status", true, desc = "Show the solver's current activity above experiment menus.")
    private val solver = ExperimentSolver()
    private val focusGuard = ExperimentFocusGuard()
    private var ignoredFocusClicks = 0
    private var blockedMenuClicks = 0
    private val roundReader = ExperimentRoundReader()
    private val stakesGoals = mutableMapOf<ExperimentGame, Int>()
    private val tableOpener = ExperimentTableOpener()
    private var ownedMenu: ChestMenu? = null
    private var ownedScreen: AbstractContainerScreen<*>? = null
    private var title = ""
    private var authoritative = emptyList<ItemStack>()
    private var revision = 0L
    private var cachedRevision = -1L
    private var cachedFrame: ExperimentFrame? = null
    private var enteredAt = 0L
    private var routerSentAt: Long? = null
    private var manualPause = false
    private var lastServerStateId = -1
    private var clicksSent = 0
    private var lastRoundStoppedAt = 0
    private var lastCharges: Int? = null
    private var allSessionsComplete = false
    private var refillLevels: Int? = null
    private var refillBought = 0
    private var refillPendingXp: Int? = null
    private var refillPendingAt = 0L
    private var renewPending = false
    private var tableStageSerial = 0
    private val finishedGames = mutableSetOf<ExperimentGame>()
    var status = "Open the Experimentation Table"; private set
    private val activeTitle = Regex("^(Chronomatron|Ultrasequencer|Superpairs)\\s*\\([^)]*\\)$", RegexOption.IGNORE_CASE)
    private val roundPattern = Regex("Round:\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val clicksPattern = Regex("Remaining Clicks:\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val hiddenPattern = Regex("^(?:\\?|Click (?:any|a second) button!?|Next button is instantly rewarded!?)$", RegexOption.IGNORE_CASE)
    private val formatting = Regex("§.")
    private fun clean(text: String) = formatting.replace(text, "").trim()
    private fun now() = System.nanoTime() / 1_000_000L

    init {
        on<TickEvent.ClientEnd> { tick() }
        on<WorldEvent.Unload> { resetSession() }
        on<WindowFocusEvent> { focusGuard.observe(focused, now()) }
        on<ScreenEvent.MouseClick> {
            if (ownsMenuInput(screen)) {
                blockedMenuClicks++
                if (focusGuard.ignoreClick(mc.isWindowActive, now())) ignoredFocusClicks++
                // Window activation and initial menu clicks can arrive after the focus callback.
                // The enabled solver owns these buttons; do not pass clicks through or latch a pause.
                cancel()
            }
        }
        on<ScreenEvent.MouseRelease> { if (ownsMenuInput(screen)) cancel() }
        on<ScreenEvent.RenderAfter> {
            if (showStatus && screen === ownedScreen) guiGraphics.drawString(ClickGuiFont.font, "Auto Experiments: $status", 8, 8, 0xFFD6DFEA.toInt(), false)
        }
    }

    fun resetSession() {
        focusGuard.reset(mc.window?.isFocused ?: false, now()); ignoredFocusClicks = 0; blockedMenuClicks = 0
        clearMenu(); finishedGames.clear(); stakesGoals.clear(); manualPause = false; lastRoundStoppedAt = 0
        tableOpener.reset()
        lastCharges = null; allSessionsComplete = false; refillLevels = null; refillBought = 0; refillPendingXp = null; renewPending = false
        tableStageSerial = 0
        status = "Open the Experimentation Table"
    }
    private fun clearMenu() {
        ownedMenu = null; ownedScreen = null; authoritative = emptyList(); cachedFrame = null
        cachedRevision = -1; routerSentAt = null; solver.reset(); roundReader.reset()
    }
    override fun onEnable() { super.onEnable(); resetSession() }
    override fun onDisable() { resetSession(); super.onDisable() }

    private fun allowedServer(): Boolean {
        if (ExperimentFixture.isActive && mc.singleplayerServer != null) return true
        val host = mc.currentServer?.ip?.substringBefore(':')?.lowercase().orEmpty()
        return host == "hypixel.net" || host.endsWith(".hypixel.net")
    }
    private fun recognized(name: String) = activeTitle.matches(name) || name.equals("Experimentation Table", true) ||
        name.equals("Experiment Over", true) || name.equals("Superpairs Rewards", true) ||
        name.equals("Bottles of Enchanting", true) || (renewPending && (name.contains("renew", true) || name.contains("confirm", true))) ||
        listOf("Chronomatron", "Ultrasequencer", "Superpairs").any { name.startsWith(it, true) && name.contains("Stakes", true) }

    private fun ownsMenuInput(screen: net.minecraft.client.gui.screens.Screen): Boolean {
        if (!allowedServer()) return false
        val container = screen as? AbstractContainerScreen<*> ?: return false
        return container.menu is ChestMenu && container.menu === mc.player?.containerMenu && recognized(clean(screen.title.string))
    }

    private fun attach(): Boolean {
        val screen = mc.screen as? AbstractContainerScreen<*> ?: run { clearMenu(); return false }
        val menu = screen.menu as? ChestMenu ?: return false
        val name = clean(screen.title.string)
        if (!recognized(name) || (activeTitle.matches(name) && menu.rowCount != 6)) return false
        if (ownedMenu !== menu) {
            clearMenu(); ownedMenu = menu; ownedScreen = screen; title = name
            // Bootstrap only once. All subsequent observations come from authoritative packets,
            // never Minecraft's locally predicted inventory after our clicks.
            authoritative = menu.slots.take(menu.rowCount * 9).map { it.item.copy() }
            enteredAt = now(); revision++
        }
        // Resizing or refocusing may rebuild the screen around the same server menu.
        // Only a new menu invalidates the learned sequence.
        ownedScreen = screen
        return true
    }

    /** Called after vanilla has applied this specific server packet on the client thread. */
    @JvmStatic fun serverSlot(containerId: Int, stateId: Int, slot: Int, stack: ItemStack) {
        if (!enabled || !allowedServer() || !attach() || ownedMenu?.containerId != containerId || slot !in authoritative.indices) return
        authoritative = authoritative.toMutableList().also { it[slot] = stack.copy() }
        lastServerStateId = stateId; revision++
    }
    @JvmStatic fun serverContent(containerId: Int, stateId: Int, items: List<ItemStack>) {
        if (!enabled || !allowedServer() || !attach() || ownedMenu?.containerId != containerId || items.size < authoritative.size) return
        authoritative = items.take(authoritative.size).map { it.copy() }; lastServerStateId = stateId; revision++
    }

    private fun card(index: Int, stack: ItemStack): ExperimentCard {
        val name = clean(stack.hoverName.string)
        val lore = stack.get(DataComponents.LORE)?.lines()?.map { clean(it.string) }.orEmpty()
        val item = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return ExperimentCard(index, item, name, lore,
            highlighted = item.endsWith("_terracotta"), number = ExperimentRoundReader.number(name, stack.count), glint = stack.hasFoil(), hidden = hiddenPattern.matches(name),
            book = item == "minecraft:enchanted_book" || name.contains("Enchanted Book", true) || lore.any { it.contains("ULTRA-RARE BOOK", true) },
            powerup = item == "minecraft:feather" || lore.any { it.contains("powerup", true) } || name.contains("Instant Find", true) || Regex("^(?:Gained )?\\+\\d+ Clicks?", RegexOption.IGNORE_CASE).containsMatchIn(name))
    }
    private fun frame(): ExperimentFrame? {
        if (cachedRevision == revision) return cachedFrame
        cachedRevision = revision
        val game = activeTitle.matchEntire(title)?.groupValues?.get(1)?.uppercase()?.let { ExperimentGame.valueOf(it) }
            ?: run { cachedFrame = null; return null }
        val items = authoritative.mapIndexed(::card)
        val marker = items[49].name
        val phase = when {
            game != ExperimentGame.SUPERPAIRS && items[49].item == "minecraft:glowstone" -> ExperimentPhase.WATCH
            game != ExperimentGame.SUPERPAIRS && items[49].item == "minecraft:clock" -> ExperimentPhase.PLAY
            marker.contains("Remember the pattern", true) -> ExperimentPhase.WATCH
            marker.startsWith("Timer:", true) || marker.contains("Your turn", true) -> ExperimentPhase.PLAY
            else -> ExperimentPhase.UNKNOWN
        }
        val explicitRound = roundPattern.find(items[4].name)?.groupValues?.get(1)?.toIntOrNull()
        val round = roundReader.read(game, phase, explicitRound, items.filter { it.slot in 9..44 })
        val clicks = clicksPattern.find(items[4].name)?.groupValues?.get(1)?.toIntOrNull()
        val bonus = ExperimentBonus.read(listOf(items[4], items[49]).flatMap { listOf(it.name) + it.lore })
        val board = items.filter { it.slot in 9..44 && !authoritative[it.slot].isEmpty &&
            (game != ExperimentGame.SUPERPAIRS || it.hidden || (it.name.isNotBlank() && !it.item.endsWith("_stained_glass_pane"))) }
        return ExperimentFrame("${System.identityHashCode(ownedMenu)}:$title", game, phase, round, revision, board, clicks,
            board.any { it.hidden && it.name.contains("instantly rewarded", true) }, bonus.earned, bonus.goalRound ?: stakesGoals[game]).also { cachedFrame = it }
    }

    private fun tick() {
        focusGuard.observe(mc.isWindowActive, now())
        if (!allowedServer()) return
        if (autoOpen && !allSessionsComplete && mc.screen == null && !manualPause) {
            tableOpener.tick(now(), tableStageSerial, finishedGames.any { it != ExperimentGame.SUPERPAIRS } || (autoRenew && ExperimentGame.SUPERPAIRS in finishedGames))
            status = tableOpener.status
        }
        if (!attach()) return
        if (manualPause) return
        refillPendingXp?.let { before ->
            if ((mc.player?.experienceLevel ?: 0) > before) { refillPendingXp = null; refillBought++ }
            else {
                status = "Waiting for server XP confirmation"
                if (now() - refillPendingAt > 5000) { manualPause = true; status = "XP purchase not confirmed; stopped without retrying" }
                return
            }
        }
        if (renewPending && title.equals("Experimentation Table", true)) {
            val charges = ExperimentMenuPolicy.charges(authoritative.mapIndexed(::card).firstOrNull { it.name.equals("Superpairs", true) })
            if (charges != null && charges > 0) { renewPending = false; routerSentAt = null; finishedGames.clear(); lastCharges = charges }
        }
        routerSentAt?.let { sent ->
            if (now() - sent > 5000) { manualPause = true; status = "Menu did not advance; toggle Auto Experiments off and on to resume" }
            return
        }
        val current = frame()
        if (current == null) {
            if (now() - enteredAt >= 350) routeMenu()
            return
        }
        if (current.game == ExperimentGame.SUPERPAIRS && !superpairs) { status = "Superpairs disabled"; return }
        val action = solver.next(current, now(), clickDelay.toLong(), booksFirst)
        status = solver.status
        // Learn flashes immediately, even while input is paused. Delaying observations at
        // menu entry would lose the first notes before the click cooldown expires.
        if (now() - enteredAt < 350) return
        when (action) {
            is ExperimentAction.Click -> if (click(action.slot)) solver.sent(action, current, now())
            is ExperimentAction.Finished -> {
                if (finishedGames.add(current.game)) tableStageSerial++
                lastRoundStoppedAt = action.round
                if (action.closeMenu) mc.player?.closeContainer()
                else if (autoClaim) {
                    val claim = claimButton()
                    if (claim != null) sendRouterClick(claim)
                    else if (current.game == ExperimentGame.SUPERPAIRS) mc.player?.closeContainer()
                }
            }
            null -> Unit
        }
    }

    private fun click(slot: Int): Boolean {
        val menu = ownedMenu ?: return false
        val player = mc.player ?: return false
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return false
        if (screen !== ownedScreen || screen.menu !== menu || player.containerMenu !== menu ||
            slot !in authoritative.indices || menu.slots[slot].item.isEmpty || !menu.carried.isEmpty || !allowedServer()) return false
        val mode = mc.gameMode ?: return false
        mode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player)
        clicksSent++
        return true
    }
    private fun claimButton(): Int? = authoritative.indices.firstOrNull { index ->
        val c = card(index, authoritative[index]); val text = c.name + " " + c.lore.joinToString(" ")
        (text.contains("Click to claim rewards", true) || c.name.equals("Claim Rewards", true)) && !paidOrLocked(text)
    }
    private fun paidOrLocked(text: String) = Regex("bits|renew|recharge|purchase|not enough|on cooldown|already (?:played|completed)|locked|unavailable", RegexOption.IGNORE_CASE).containsMatchIn(text)
    private fun sendRouterClick(slot: Int) { if (click(slot)) { routerSentAt = now(); status = "Waiting for next experiment menu" } }
    private fun routeMenu() {
        if (autoClaim && (title.equals("Experiment Over", true) || title.equals("Superpairs Rewards", true))) {
            val result = authoritative.mapIndexed(::card).firstOrNull { c -> c.lore.any { it.contains("Click to claim rewards", true) } }
            if (result != null && ExperimentGame.entries.any { it != ExperimentGame.SUPERPAIRS && result.name.equals(it.name, true) && it !in finishedGames }) {
                manualPause = true; status = "Add-on ended before +3 clicks; rewards left unclaimed"; return
            }
            claimButton()?.let { sendRouterClick(it) }; return
        }
        if (!autoAdvance) { status = "Open the next experiment manually"; return }
        val choices = authoritative.mapIndexed(::card)
        if (title.contains("Stakes", true)) {
            val stakesGame = ExperimentGame.entries.firstOrNull { title.startsWith(it.name, true) }
            val highest = ExperimentMenuPolicy.highestUnlocked(choices)
            if (stakesGame != null && highest != null) {
                ExperimentBonus.read(highest.lore).goalRound?.let { stakesGoals[stakesGame] = it }
            }
        }
        if (routeRefill(choices)) return
        if (renewPending && !title.equals("Experimentation Table", true)) {
            val confirm = choices.firstOrNull { it.name.equals("Confirm", true) || it.name.contains("Confirm Renewal", true) }
            if (confirm != null) { sendRouterClick(confirm.slot); return }
            status = "Waiting for a recognized renewal confirmation"; return
        }
        fun available(c: ExperimentCard): Boolean {
            val text = c.name + " " + c.lore.joinToString(" ")
            return !paidOrLocked(text) && c.lore.any { it.contains("Click to", true) }
        }
        if (title.equals("Experimentation Table", true)) {
            val superpairCard = choices.firstOrNull { it.name.equals("Superpairs", true) }
            val charges = ExperimentMenuPolicy.charges(superpairCard)
            if (charges != null) {
                if (lastCharges?.let { charges < it && charges > 0 } == true) finishedGames.clear()
                lastCharges = charges
            }
            for (game in ExperimentGame.entries) {
                if (game in finishedGames || (game == ExperimentGame.SUPERPAIRS && !superpairs)) continue
                val c = choices.firstOrNull { it.name.equals(game.name, true) } ?: continue
                if ((c.name + c.lore.joinToString()).contains("completed", true) || c.lore.any { it.contains("already played", true) }) { finishedGames += game; continue }
                if (available(c)) { sendRouterClick(c.slot); return }
            }
            if (charges == 0) {
                val renew = choices.firstOrNull { it.item == "minecraft:blaze_powder" }
                val text = renew?.let { it.name + " " + it.lore.joinToString(" ") }.orEmpty()
                if (autoRenew && renew != null && ExperimentMenuPolicy.insufficientBits(renew)) {
                    status = "Not enough Bits to renew experiment charges"; return
                }
                if (autoRenew && renew != null && ExperimentMenuPolicy.renewalsRemaining(renew) != 0 && !Regex("maximum|limit reached|no more|already renewed", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                    val cost = ExperimentMenuPolicy.levels(renew)
                    if (cost != null && (mc.player?.experienceLevel ?: 0) < cost) { requestRefill(cost); return }
                    if (renew.lore.any { it.contains("Click to", true) }) {
                        renewPending = true; sendRouterClick(renew.slot); status = "Renewing experiment charges with XP and Bits"; return
                    }
                }
                allSessionsComplete = true; status = "Complete - all available experiment sessions finished"
            } else status = "Waiting for the next available experiment"
        } else if (title.contains("Stakes", true)) {
            val highest = ExperimentMenuPolicy.highestUnlocked(choices)
            if (highest != null) {
                val cost = ExperimentMenuPolicy.levels(highest)
                if (cost != null && (mc.player?.experienceLevel ?: 0) < cost) { requestRefill(cost); return }
                if (available(highest)) sendRouterClick(highest.slot)
            }
            if (routerSentAt == null) status = "No unlocked affordable stakes found"
        }
    }

    private fun requestRefill(levels: Int) {
        if (!autoBuyXp) { status = "Need $levels XP levels; Auto Buy XP is disabled"; return }
        refillLevels = levels; refillBought = 0; status = "Refilling XP for $levels levels"
    }
    private fun routeRefill(choices: List<ExperimentCard>): Boolean {
        val target = refillLevels ?: return false
        val xp = mc.player?.experienceLevel ?: 0
        val back = choices.firstOrNull { it.name.equals("Go Back", true) }
        if (xp >= target) {
            refillLevels = null
            if (title.equals("Bottles of Enchanting", true)) back?.let { sendRouterClick(it.slot) }
            return title.equals("Bottles of Enchanting", true)
        }
        if (title.equals("Bottles of Enchanting", true)) {
            if (refillBought >= 2) { manualPause = true; status = "Two-bottle refill limit reached; more XP is needed"; return true }
            val bottle = choices.firstOrNull { it.name.equals("Titanic Experience Bottle", true) }
            val decision = ExperimentMenuPolicy.bottleAction(bottle, bottlePriceLimit.toDouble())
            if (bottle == null || decision in setOf(ExperimentMenuPolicy.BottleAction.UNAVAILABLE, ExperimentMenuPolicy.BottleAction.OVER_LIMIT)) {
                manualPause = true
                status = if (decision == ExperimentMenuPolicy.BottleAction.OVER_LIMIT) "Titanic price exceeds limit" else "No usable Titanic bottle in inventory or purchasable offer"
                return true
            }
            if (click(bottle.slot)) {
                refillPendingXp = xp; refillPendingAt = now()
                status = if (decision == ExperimentMenuPolicy.BottleAction.OWNED) "Consuming inventory Titanic bottle ${refillBought + 1}/2" else "Buying and consuming Titanic bottle ${refillBought + 1}/2"
            }
        } else {
            val bottles = choices.firstOrNull { it.name.equals("Experience Bottles", true) || it.name.equals("Bottles of Enchanting", true) }
            val button = bottles ?: back
            if (button != null) sendRouterClick(button.slot) else { manualPause = true; status = "Cannot find the table's XP bottle menu" }
        }
        return true
    }

    private fun observedContainer(): Map<String, Any?>? {
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return null
        val menu = screen.menu as? ChestMenu ?: return null
        return mapOf("title" to clean(screen.title.string), "rows" to menu.rowCount,
            "slots" to menu.slots.take(menu.rowCount * 9).mapIndexedNotNull { index, slot ->
                if (slot.item.isEmpty) null else card(index, slot.item).let {
                    mapOf("slot" to index, "name" to it.name, "item" to it.item, "lore" to it.lore)
                }
            })
    }
    fun state(): Map<String, Any?> = mapOf("enabled" to enabled, "status" to status, "menu" to title,
        "windowActive" to mc.isWindowActive, "ignoredFocusClicks" to ignoredFocusClicks, "blockedMenuClicks" to blockedMenuClicks, "autoOpen" to autoOpen, "autoOpenAttempts" to tableOpener.attempts, "autoOpenStatus" to tableOpener.status,
        "allSessionsComplete" to allSessionsComplete, "charges" to lastCharges, "refillTargetLevels" to refillLevels,
        "refillBottlesBought" to refillBought, "renewPending" to renewPending,
        "observedContainer" to observedContainer(),
        "game" to cachedFrame?.game?.name, "round" to cachedFrame?.round, "phase" to cachedFrame?.phase?.name,
        "serverRevision" to revision, "serverStateId" to lastServerStateId, "clicksSent" to clicksSent,
        "awaitingConfirmation" to solver.awaitingConfirmation, "halted" to (solver.halted || manualPause),
        "knownCards" to solver.knownCards, "learnedSequence" to solver.learnedSequence, "lastRoundStoppedAt" to lastRoundStoppedAt,
        "targetSuperpairsBonus" to 3, "earnedBonus" to cachedFrame?.earnedBonus, "bonusGoalRound" to cachedFrame?.bonusGoalRound,
        "finishedGames" to finishedGames.map { it.name }, "clickTransport" to "vanilla-container-input", "fixture" to ExperimentFixture.state(), "flowFixture" to ExperimentFlowFixture.state())
}
