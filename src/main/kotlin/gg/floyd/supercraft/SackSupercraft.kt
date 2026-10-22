package gg.floyd.supercraft

import com.google.gson.JsonParser
import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import gg.floyd.FloydAddonsMod
import gg.floyd.clickgui.settings.impl.*
import gg.floyd.events.*
import gg.floyd.events.core.on
import gg.floyd.features.Category
import gg.floyd.features.Module
import gg.floyd.features.ModuleManager
import gg.floyd.utils.font.ClickGuiFont
import gg.floyd.utils.modMessage
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.drawString
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ResolvableProfile
import java.util.UUID

object SackSupercraft:Module("Sack Supercraft",category=Category.PVP,
    description="Normal sacks craft only the next direct enchanted tier. Enchanted sacks continue through approved highest tiers. Maximum amounts can use the same materials from other storage.",toggled=true) {
    private val showButton by BooleanSetting("Sack Menu Button",true,desc="Show a Supercraft action in an opened sack.")
    private val autoOpenedSack by BooleanSetting("Auto Craft Opened Sack",false,desc="Start once when you open a recognized sack. Configure protected tiers before enabling this.")
    var keepTiers by StringSetting("Keep At Current Tier","",8192,desc="Never craft these ignored outputs. Existing input-tier selections also retain their protection.").hide()
    private val delay by NumberSetting("Click Delay",400,250,1500,50,desc="Minimum milliseconds between acknowledged recipe clicks.")
    private val stopAction by ActionSetting("Stop Crafting","Stop without sending any further commands or clicks.") { stop("Stopped by user") }
    private data class Icon(val item:String,val model:String?,val texture:String?,val glint:Boolean)
    private data class Catalog(val sacks:Map<String,Set<String>>,val names:Map<String,String>,val recipes:List<SackRecipe>,val icons:Map<String,Icon>)
    private val catalog by lazy {
        val stream=javaClass.getResourceAsStream("/assets/floydaddons/data/sack_supercraft.json") ?: error("Sack recipes missing")
        val json=stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        Catalog(json.getAsJsonObject("sacks").entrySet().associate { (name,sack)->name to sack.asJsonObject.getAsJsonArray("contents").map { it.asString }.toSet() },
            json.getAsJsonObject("names").entrySet().associate { it.key to SackCraftPlanner.displayName(it.value.asString) },
            json.getAsJsonArray("recipes").map { e->val r=e.asJsonObject;SackRecipe(r["output"].asString,r["count"].asLong,r.getAsJsonObject("ingredients").entrySet().associate { it.key to it.value.asLong }) },
            json.getAsJsonObject("icons")?.entrySet()?.associate { (id,e)->val i=e.asJsonObject;id to Icon(i["item"].asString,i["model"]?.asString,i["texture"]?.asString,i["glint"]?.asBoolean ?: false) }.orEmpty())
    }
    private val iconCache=hashMapOf<String,ItemStack>()
    private val targetCache=hashMapOf<String,Set<String>>()
    private enum class Phase { IDLE,SACK_LIST,SACK_CONTENTS,RECIPE,MAXIMUM,RESULT }
    private var phase=Phase.IDLE
    private var status="Open a sack, or use /supercraft <sack name>"
    private var selected=""
    private var steps=emptyList<SackCraftStep>()
    private var stepIndex=0
    private var remaining=0L
    private var sentAt=0L
    private var deadline=0L
    private var nextAt=0L
    private var menu:ChestMenu?=null
    private var items=emptyList<ItemStack>()
    private var revision=0L
    private var sentRevision=0L
    private var inventory=emptyList<ItemStack>()
    private var pendingOutput=0L
    private var baselineOutput=0L
    private var confirmedCrafts=0L
    private var navigationClicks=0
    private var startingExceptions=""
    private var pausedAt=0L
    private var intoSacks=false
    private var maxSelected=false
    private var maxButton=-1
    private var buttonRevision=0L
    private var sentButtonRevision=0L
    private var beforeMaximumCrafts=0L
    private var resourceMaximumCrafts=0L
    private var ledger=linkedMapOf<String,Long>()
    private var normalBudget:MutableMap<String,Long>?=null
    private var runRecipes=emptyList<SackRecipe>()
    private var transactions=0
    private var listRequests=0
    private var lastAutoScreen:net.minecraft.client.gui.screens.Screen?=null
    private val observedSacks=linkedSetOf<String>()
    private fun now()=System.nanoTime()/1_000_000
    private fun clean(s:String)=s.replace(Regex("§."),"").trim()
    private fun text(s:ItemStack)=clean(s.hoverName.string+"\n"+s.get(DataComponents.LORE)?.lines().orEmpty().joinToString("\n") { it.string })
    private fun id(s:ItemStack):String {
        val custom=s.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getString("id")?.orElse("").orEmpty()
        if(custom.isNotBlank())return custom
        return catalog.names.entries.firstOrNull { SackCraftPlanner.displayName(s.hoverName.string).equals(it.value,true) }?.key.orEmpty()
    }
    private fun sackKey(name:String):String? {
        return SackCraftPlanner.sackName(name,catalog.sacks.keys)
    }
    fun itemId(stack:ItemStack):String=id(stack).takeIf { it in catalog.names }.orEmpty()
    fun displayName(id:String):String = catalog.names[id] ?: id
    fun ignoredIds():Set<String> = protected()
    fun toggleIgnored(id:String) { if(id in catalog.names)exception(id,id !in protected()) }
    fun sackName(title:String):String? = sackKey(title)
    fun itemBelongsToSack(sack:String,id:String):Boolean = id in catalog.sacks[sackKey(sack) ?: sack].orEmpty()
    fun upgradeTargets(sack:String):Set<String> {
        val name=sackKey(sack) ?: return emptySet()
        return targetCache.getOrPut(name) {
            val resources=catalog.sacks[name].orEmpty()
            if(name.startsWith("Enchanted ") || name=="Gemstone")SackCraftPlanner.upgradeTargets(resources,SackCraftPlanner.familyRecipes(catalog.recipes),catalog.names.keys)
            else SackCraftPlanner.directRecipes(resources,catalog.recipes).map { it.output }.toSortedSet()
        }
    }
    /** Factual base-item/model/profile metadata, cached once; UI callers receive independent copies. */
    fun targetIcon(id:String):ItemStack {
        if(id !in catalog.names)return ItemStack.EMPTY
        return iconCache.getOrPut(id) {
            val meta=catalog.icons[id]
            val base=when(meta?.item) { "minecraft:skull"->"minecraft:player_head";"minecraft:double_plant"->"minecraft:sunflower";"minecraft:log"->"minecraft:oak_log";"minecraft:log2"->"minecraft:acacia_log";"minecraft:dye"->"minecraft:green_dye";"minecraft:fish"->"minecraft:cod";else->meta?.item ?: "minecraft:paper" }
            val native=meta?.model?.takeIf { it.startsWith("minecraft:") } ?: base
            val item=runCatching { BuiltInRegistries.ITEM.getValue(Identifier.parse(native)) }.getOrNull()?.takeUnless { it==Items.AIR } ?: Items.PAPER
            ItemStack(item).apply {
                set(DataComponents.CUSTOM_NAME,Component.literal(catalog.names.getValue(id)))
                set(DataComponents.CUSTOM_DATA,CustomData.of(CompoundTag().apply { putString("id",id) }))
                if(meta?.glint==true)set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true)
                meta?.model?.let { raw -> runCatching { Identifier.parse(raw) }.getOrNull()?.let { model ->
                    if(model.namespace=="minecraft" || mc.resourceManager.getResource(Identifier.fromNamespaceAndPath(model.namespace,"items/${model.path}.json")).isPresent)set(DataComponents.ITEM_MODEL,model)
                } }
                meta?.texture?.let { texture ->
                    val properties=PropertyMap(ImmutableMultimap.of("textures",Property("textures",texture)))
                    set(DataComponents.PROFILE,ResolvableProfile.createResolved(GameProfile(UUID.nameUUIDFromBytes("floyd-sack:$id".toByteArray()),"FloydSackTarget",properties)))
                }
            }
        }.copy()
    }
    fun isCrafting():Boolean = phase!=Phase.IDLE
    fun names():List<String> = catalog.sacks.keys.sorted()
    fun itemNames():List<String> = catalog.names.values.sorted()
    fun listExceptions() { modMessage("Ignored upgrade outputs: "+protected().joinToString { catalog.names[it] ?: it }.ifEmpty { "none" }) }
    fun listSacks() {
        listRequests++
        fun group(label:String,entries:List<String>,stripEnchanted:Boolean=false) {
            val line=Component.literal("$label: ")
            entries.forEachIndexed { index,name ->
                if(index>0)line.append(" · ")
                val display=if(stripEnchanted)name.removePrefix("Enchanted ") else name
                line.append(Component.literal((if(name in observedSacks)"✓ " else "")+display)
                    .withStyle { it.withUnderlined(true).withClickEvent(ClickEvent.SuggestCommand("/supercraft $name")) })
            }
            modMessage(line)
        }
        val all=names()
        group("Regular sacks",all.filterNot { it.startsWith("Enchanted ") })
        // Keep these last so every enchanted choice remains visible in the collapsed chat too.
        group("Enchanted sacks",all.filter { it.startsWith("Enchanted ") },true)
        modMessage("Click a sack to fill /supercraft. Exceptions: /supercraft except <item>. Stop: /supercraft stop.")
    }
    fun exception(value:String,add:Boolean) {
        val needle=SackCraftPlanner.key(value)
        val item=catalog.names.keys.firstOrNull { it==needle } ?: catalog.names.entries.firstOrNull { SackCraftPlanner.key(it.value)==needle }?.key
        if(item==null) { modMessage("Unknown sack item: $value");return }
        val kept=protected().toMutableSet();if(add)kept+=item else kept-=item
        keepTiers=kept.sorted().joinToString(", ");ModuleManager.saveConfigurations()
        if(phase!=Phase.IDLE)stop("Exceptions changed; start again to use the new plan")
        modMessage(if(add)"Will never craft ${catalog.names[item]}." else "Crafting ${catalog.names[item]} is allowed.")
    }
    private fun protected():Set<String> = keepTiers.split(',').mapNotNull { token ->
        val key=SackCraftPlanner.key(token);catalog.names.keys.firstOrNull { it==key } ?: catalog.names.entries.firstOrNull { SackCraftPlanner.key(it.value)==key }?.key
    }.toSet()
    private fun invalidExceptions()=keepTiers.split(',').map(String::trim).filter { it.isNotEmpty() }.filter { value ->
        val key=SackCraftPlanner.key(value);key !in catalog.names && catalog.names.values.none { SackCraftPlanner.key(it)==key }
    }
    fun start(name:String) {
        if(!enabled) { modMessage("Enable Sack Supercraft in QoL first.");return }
        if(mc.player==null) { modMessage("Open a SkyBlock world first.");return }
        val resolved=sackKey(name)
        if(resolved==null) { modMessage("Unknown or ambiguous sack: $name. Use /supercraft for names.");return }
        if(!allowed()) { modMessage("Sack Supercraft is available in SkyBlock only.");return }
        val invalid=invalidExceptions()
        if(invalid.isNotEmpty()) { modMessage("Fix these unknown protected tiers before crafting: "+invalid.joinToString());return }
        stop("Starting");selected=resolved;confirmedCrafts=0;startingExceptions=keepTiers
        val screen=mc.screen as? AbstractContainerScreen<*>
        if(screen!=null && sackKey(screen.title.string)==selected) {
            attach(screen.menu.containerId);phase=Phase.SACK_CONTENTS;deadline=now()+10000;status="Reading $selected Sack"
        } else {
            phase=Phase.SACK_LIST;status="Opening Sack of Sacks";deadline=now()+12000;send("sacks")
        }
    }
    fun stop(reason:String="Stopped") { phase=Phase.IDLE;status=reason;pendingOutput=0;steps=emptyList();remaining=0;navigationClicks=0;pausedAt=0;maxSelected=false;maxButton=-1 }
    private fun allowed():Boolean {
        if(mc.singleplayerServer!=null)return true
        val host=mc.currentServer?.ip?.substringBefore(':')?.lowercase() ?: return false
        return host=="hypixel.net" || host.endsWith(".hypixel.net")
    }
    // This never connects to a server. In the offline fixture, commands become local fixture intents.
    private fun send(command:String) {
        if(mc.singleplayerServer!=null) { SackSupercraftFixture.command(command);return }
        mc.player?.connection?.sendCommand(command)
    }
    private fun attach(containerId:Int):Boolean {
        val screen=mc.screen as? AbstractContainerScreen<*> ?: return false
        val next=screen.menu as? ChestMenu ?: return false
        if(next.containerId!=containerId || mc.player?.containerMenu!==next)return false
        if(menu!==next) {
            menu=next;items=next.slots.take(next.rowCount*9).map { it.item.copy() }
            inventory=next.slots.drop(next.rowCount*9).map { it.item.copy() };revision++
        }
        return true
    }
    @JvmStatic fun serverSlot(containerId:Int,slot:Int,item:ItemStack) {
        if(!attach(containerId))return
        val n=items.size
        if(slot in items.indices)items=items.toMutableList().also { it[slot]=item.copy() }
        else if(slot-n in inventory.indices)inventory=inventory.toMutableList().also { it[slot-n]=item.copy() }
        else return
        revision++;if(slot==maxButton)buttonRevision++;acknowledge()
    }
    @JvmStatic fun serverContent(containerId:Int,content:List<ItemStack>) {
        if(!attach(containerId))return
        val n=menu!!.rowCount*9;items=content.take(n).map { it.copy() };inventory=content.drop(n).map { it.copy() };revision++;buttonRevision++;acknowledge()
    }
    private fun countInventory(output:String)=inventory.filter { !it.isEmpty && id(it)==output }.sumOf { it.count.toLong() }
    private fun acknowledge() {
        if(phase!=Phase.RESULT || intoSacks || revision<=sentRevision)return
        val recipe=steps.getOrNull(stepIndex)?.recipe ?: return
        val gained=countInventory(recipe.output)-baselineOutput
        if(gained<=0)return
        if(gained<pendingOutput)return // Bulk output can arrive in multiple authoritative slot packets.
        if(gained>pendingOutput) { stop("Unexpected output quantity; stopped to protect your items");return }
        confirmed(gained,recipe)
    }
    private fun confirmed(gained:Long,recipe:SackRecipe) {
        if(gained!=pendingOutput) { stop("Unexpected output quantity; stopped to protect your items");return }
        val crafted=gained/recipe.count;remaining=0;confirmedCrafts+=crafted;pendingOutput=0;transactions++
        recipe.ingredients.forEach { (id,cost)->ledger[id]=maxOf(0,(ledger[id] ?: 0)-Math.multiplyExact(cost,crafted)) }
        normalBudget?.let { budget -> recipe.ingredients.forEach { (id,cost)->budget[id]=maxOf(0,(budget[id] ?: 0)-Math.multiplyExact(cost,crafted)) } }
        ledger[recipe.output]=Math.addExact(ledger[recipe.output] ?: 0,gained)
        phase=Phase.RECIPE;nextAt=now()+delay.toLong();deadline=now()+12000
        status="Confirmed $crafted × ${catalog.names[recipe.output]}"
    }
    private fun click(slot:Int,shift:Boolean=false) {
        val current=menu ?: return
        val player=mc.player ?: return
        if(!current.carried.isEmpty || player.containerMenu!==current || slot !in items.indices) { stop("Menu or cursor changed; stopped");return }
        if(phase==Phase.MAXIMUM || phase==Phase.RESULT) {
            val recipe=steps.getOrNull(stepIndex)?.recipe
            if(recipe==null || recipe !in runRecipes || !SackCraftPlanner.allowed(recipe,protected())) { stop("Ignored upgrade target; stopped before clicking");return }
        }
        sentAt=now();sentRevision=revision;nextAt=sentAt+delay.toLong()
        mc.gameMode?.handleContainerInput(current.containerId,slot,0,if(shift)ContainerInput.QUICK_MOVE else ContainerInput.PICKUP,player)
    }
    private fun buildPlan() {
        val stock=linkedMapOf<String,Long>()
        items.forEach { item -> val key=id(item)
            if(key in catalog.sacks[selected].orEmpty()) {
                val parsed=SackCraftPlanner.sackStock(key,text(item),selected=="Gemstone")
                if(selected=="Gemstone" && parsed.isEmpty()) { stop("Could not read exact Rough, Flawed and Fine counts; stopped before crafting");return }
                stock.putAll(parsed)
            }
        }
        if(stock.isEmpty())return
        val highest=selected.startsWith("Enchanted ") || selected=="Gemstone"
        runRecipes=if(highest)SackCraftPlanner.familyRecipes(catalog.recipes) else SackCraftPlanner.directRecipes(catalog.sacks[selected].orEmpty(),catalog.recipes)
        normalBudget=if(highest)null else stock.toMutableMap()
        val plan=runCatching { if(highest)SackCraftPlanner.plan(stock,runRecipes,protected()) else SackCraftPlanner.planDirect(stock,runRecipes,protected()) }.getOrElse { stop("Could not build a bounded recipe plan");return }
        if(plan.steps.isEmpty()) { stop(if(highest)"No allowed highest-tier upgrades: targets are ignored, amounts are too low, or resources are already upgraded" else "No allowed direct enchanted upgrades: targets are ignored, amounts are too low, or no direct counterpart exists");return }
        observedSacks+=selected;ledger=stock;transactions=0;steps=plan.steps;stepIndex=0;remaining=steps[0].crafts;openRecipe()
    }
    private fun openRecipe() {
        val step=steps.getOrNull(stepIndex) ?: run { stop("Finished: $confirmedCrafts confirmed crafts");modMessage(status);return }
        phase=Phase.RECIPE;navigationClicks=0;maxSelected=false;maxButton=-1;deadline=now()+12000;nextAt=now()+delay.toLong()
        status="Opening ${catalog.names[step.recipe.output]} recipe"
        send("recipe "+catalog.names.getValue(step.recipe.output))
    }
    private fun tick() {
        if(phase==Phase.IDLE)return
        if(mc.player==null || !allowed()) { stop("World changed");return }
        val clock=now()
        if(keepTiers!=startingExceptions) { stop("Exceptions changed; start again to use the new plan");return }
        if(!mc.isWindowActive) { if(pausedAt==0L)pausedAt=clock;return }
        if(pausedAt!=0L) { val elapsed=clock-pausedAt;deadline+=elapsed;nextAt+=elapsed;pausedAt=0 }
        if(clock>deadline) { stop("No server confirmation; stopped without retrying the craft");modMessage(status);return }
        if(clock<nextAt)return
        if(phase==Phase.RECIPE && remaining==0L) {
            if(transactions>=1000) { stop("Reached 1,000 confirmed transactions; start again to continue");return }
            val plan=runCatching { normalBudget?.let { SackCraftPlanner.planDirect(it,runRecipes,protected()) } ?: SackCraftPlanner.plan(ledger,runRecipes,protected()) }.getOrElse { stop("Could not replan confirmed outputs");return }
            if(plan.steps.isEmpty()) { stop("Finished: $confirmedCrafts confirmed crafts");modMessage(status);return }
            steps=plan.steps;stepIndex=0;remaining=steps[0].crafts;openRecipe();return
        }
        val screen=mc.screen as? AbstractContainerScreen<*> ?: return
        if(!attach(screen.menu.containerId))return
        val title=clean(screen.title.string)
        if(phase==Phase.SACK_LIST) {
            if(sackKey(title)==selected) { phase=Phase.SACK_CONTENTS;buildPlan();return }
            if(!title.equals("Sack of Sacks",true) && !title.equals("Sacks",true))return
            items.forEach { sackKey(it.hoverName.string)?.let(observedSacks::add) }
            val matches=items.indices.filter { sackKey(items[it].hoverName.string)==selected }
            if(matches.size!=1) { stop("$selected Sack was not found uniquely in Sack of Sacks");modMessage(status);return }
            phase=Phase.SACK_CONTENTS;click(matches.single());deadline=clock+12000;return
        }
        if(phase==Phase.SACK_CONTENTS) { if(sackKey(title)==selected)buildPlan();return }
        if(phase==Phase.RESULT)return
        val step=steps[stepIndex];val expected=step.recipe.output
        if(!SackCraftPlanner.allowed(step.recipe,protected())) { stop("Ignored upgrade target; stopped before crafting");return }
        // Live recipe titles can be only the output name (e.g. "Enchanted Sugar Cane").
        // Validate the output and explicit Supercraft controls below, not an English title suffix.
        if(items.none { id(it)==expected })return
        val buttons=items.indices.filter { slot ->
            val body=text(items[slot]);val name=clean(items[slot].hoverName.string)
            (name.contains("Supercraft",true) || id(items[slot])==expected) &&
                SupercraftMenuRead.amount(body)!=null
        }
        if(buttons.size==1) {
            val slot=buttons.single();val body=text(items[slot])
            val amount=SupercraftMenuRead.amount(body) ?: run { status="Waiting for an explicit server craft amount";return }
            val costs=SupercraftMenuRead.costs(body,catalog.names)
            if(costs==null || !SupercraftMenuRead.agrees(step.recipe,amount,costs)) { stop("Server recipe differs from the planned ingredients; stopped");return }
            if(!maxSelected) {
                val stack=items[slot]
                if(stack.item!=net.minecraft.world.item.Items.GOLDEN_PICKAXE || !clean(stack.hoverName.string).equals("Supercraft",true)) { status="Waiting for the Supercraft pickaxe";return }
                resourceMaximumCrafts=SupercraftMenuRead.maximumCrafts(body,catalog.names,step.recipe) ?: run { stop("Could not verify available recipe ingredients");return }
                beforeMaximumCrafts=amount.outputs/step.recipe.count
                maxSelected=true;maxButton=slot;sentButtonRevision=buttonRevision;phase=Phase.MAXIMUM
                status="Selecting maximum for ${catalog.names[expected]}";deadline=clock+8000;click(slot,true);return
            }
            if(phase==Phase.MAXIMUM && buttonRevision<=sentButtonRevision)return
            val actualCrafts=amount.outputs/step.recipe.count
            if(actualCrafts==beforeMaximumCrafts && actualCrafts<resourceMaximumCrafts || actualCrafts==1L && resourceMaximumCrafts>1L) {
                stop("Maximum craft amount was not confirmed; stopped before crafting");return
            }
            if(!Regex("(?im)^(?:Left-)?Click to (?:perform )?(?:Supercraft|craft)(?:!|\\.)?$").containsMatchIn(body)) { status="Waiting for the server's craft action";return }
            baselineOutput=countInventory(expected);pendingOutput=amount.outputs;intoSacks=amount.intoSacks
            phase=Phase.RESULT;status="Crafting ${catalog.names[expected]} ($remaining remaining)"
            deadline=clock+8000;click(slot);return
        }
        // Navigation is separate from crafting and also waits for an authoritative menu revision.
        if(revision<=sentRevision)return
        val navigation=items.indices.filter { slot ->
            val stack=items[slot];val name=clean(stack.hoverName.string);val body=text(stack)
            (id(stack)==expected || name.equals("Supercraft",true)) &&
                (body.contains("Click to view recipe",true) || body.contains("Click to open",true))
        }
        if(navigation.size==1 && navigationClicks<3) { navigationClicks++;click(navigation.single()) }
    }
    init {
        on<TickEvent.ClientEnd> { tick() }
        on<ScreenEvent.Open> {
            if(!autoOpenedSack || phase!=Phase.IDLE || screen !is AbstractContainerScreen<*> || screen===lastAutoScreen)return@on
            val sack=sackKey(screen.title.string) ?: return@on
            lastAutoScreen=screen
            mc.execute { if(enabled && autoOpenedSack && phase==Phase.IDLE && mc.screen===screen)start(sack) }
        }
        on<ChatPacketEvent> {
            if(phase!=Phase.RESULT || !intoSacks)return@on
            val match=Regex("^You Supercrafted (.+?)(?:x([0-9,]+))?!$").matchEntire(clean(value)) ?: return@on
            val recipe=steps.getOrNull(stepIndex)?.recipe ?: return@on
            if(!SackCraftPlanner.displayName(match.groupValues[1]).equals(catalog.names[recipe.output],true))return@on
            val amount=match.groupValues[2].takeIf { it.isNotBlank() }?.replace(",","")?.toLongOrNull() ?: 1L
            confirmed(amount,recipe)
        }
        on<WorldEvent.Unload> { stop("World changed");menu=null;items=emptyList();inventory=emptyList() }
        on<ScreenEvent.RenderAfter> {
            if(screen !is AbstractContainerScreen<*>)return@on
            if(phase!=Phase.IDLE)guiGraphics.drawString(ClickGuiFont.font,"Supercraft: $status",8,8,0xFFD6DFEA.toInt(),false)
            if(!showButton || sackKey(screen.title.string)==null)return@on
            val chest=screen.menu as? ChestMenu ?: return@on
            val visible=chest.slots.take(chest.rowCount*9).map { it.item }
            SackSupercraftPanel.render(guiGraphics,screen,visible,ignoredIds(),mouseX,mouseY)
        }
        on<ScreenEvent.MouseClick> {
            if(screen !is AbstractContainerScreen<*>)return@on
            if(showButton && sackKey(screen.title.string)!=null && SackSupercraftPanel.click(screen,click.x(),click.y(),click.button()))cancel()
            else if(phase!=Phase.IDLE)stop("Stopped after manual menu input")
        }
        on<ScreenEvent.MouseScroll> {
            if(screen is AbstractContainerScreen<*> && showButton && sackKey(screen.title.string)!=null && SackSupercraftPanel.scroll(screen,x,y,vertical))cancel()
        }
        on<ScreenEvent.KeyPress> { if(phase!=Phase.IDLE && input.key()==256)stop("Stopped after closing the menu") }
    }
    override fun onDisable() { stop("Disabled");super.onDisable() }
    fun state()=mapOf("phase" to phase.name,"status" to status,"sack" to selected,"step" to stepIndex,"steps" to steps.size,"remainingCrafts" to remaining,
        "confirmedCrafts" to confirmedCrafts,"awaitingOutput" to pendingOutput,"intoSacks" to intoSacks,"revision" to revision,"exceptions" to protected().toList(),"availableSacks" to names(),"seenSacks" to observedSacks.toList(),"listRequests" to listRequests,"transactions" to transactions,"ledger" to ledger.toMap())
}
