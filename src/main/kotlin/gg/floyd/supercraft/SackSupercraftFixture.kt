package gg.floyd.supercraft

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

/** An integrated-server fixture, never a replacement for actual server GUI compatibility proof. */
object SackSupercraftFixture {
    private var active=false
    private var stock=linkedMapOf<String,Long>()
    private var reject=false
    private var crafts=0
    private var invalid=0
    private var attempts=0
    private var intoSacks=false
    private var selectedCrafts=1L
    private var maxSelections=0
    private var partialPackets=0
    private var extraStock=0L
    private var rawWart=false
    private var closeAfterCraft=false
    private var ignoreMaximum=false
    private var fixtureSack="Enchanted Agronomy"
    private var commands=mutableListOf<String>()
    @Volatile private var diagnostics:Map<String,Any?> = emptyMap()
    fun state()=diagnostics
    private fun publish() { diagnostics=mapOf("active" to active,"crafts" to crafts,"attempts" to attempts,"invalidClicks" to invalid,"stock" to stock.toMap(),"commands" to commands.toList(),"reject" to reject,"intoSacks" to intoSacks,"selectedCrafts" to selectedCrafts,"maxSelections" to maxSelections,"partialPackets" to partialPackets,"extraStock" to extraStock) }
    fun open(rejectCraft:Boolean=false,sacks:Boolean=false,amount:Long=1,stockAmount:Long=321,otherStorage:Long=0,rawAmount:Long=0,sugarAmount:Long=159,closeOnCraft:Boolean=false,ignoreMax:Boolean=false,scenario:String="") {
        val mc=Minecraft.getInstance();val server=mc.singleplayerServer ?: error("Integrated server required")
        require(amount in 1..20000 && stockAmount in 0..10_000_000 && otherStorage in 0..10_000_000 && rawAmount in 0..10_000_000 && sugarAmount in 0..10_000_000)
        server.execute {
            active=true;reject=rejectCraft;crafts=0;invalid=0;attempts=0;intoSacks=sacks;selectedCrafts=amount;maxSelections=0;partialPackets=0;extraStock=otherStorage;rawWart=rawAmount>0;closeAfterCraft=closeOnCraft;ignoreMaximum=ignoreMax;commands=mutableListOf()
            stock=linkedMapOf("ENCHANTED_NETHER_STALK" to stockAmount,"ENCHANTED_SUGAR" to sugarAmount,"NETHER_STALK" to rawAmount)
            fixtureSack=if(rawWart)"Agronomy" else "Enchanted Agronomy"
            when(scenario.uppercase()) {
                "NORMAL_CROPS"->{ fixtureSack="Agronomy";stock=linkedMapOf("WHEAT" to 25600,"DOUBLE_PLANT" to 25600,"SUGAR_CANE" to 25600,"NETHER_STALK" to 25601) }
                "MUSHROOM"->{ fixtureSack="Enchanted Agronomy";stock=linkedMapOf("ENCHANTED_RED_MUSHROOM" to 321) }
                "GEMSTONES"->{ fixtureSack="Gemstone";stock=linkedMapOf("ROUGH_RUBY_GEM" to 6401,"FLAWED_RUBY_GEM" to 80,"FINE_RUBY_GEM" to 80) }
                "GEMSTONE_WEIGHTED"->{ fixtureSack="Gemstone";stock=linkedMapOf("ROUGH_RUBY_GEM" to 2210,"FLAWED_RUBY_GEM" to 196,"FINE_RUBY_GEM" to 7) }
                "NO_UPGRADES"->{ fixtureSack="Bronze Trophy Frog";stock=linkedMapOf() }
                "COMBAT_CHILI"->{ fixtureSack="Combat";stock=linkedMapOf("CHILI_PEPPER" to 973) }
            }
            publish();openSack()
        }
    }
    fun command(value:String) {
        val server=Minecraft.getInstance().singleplayerServer ?: return
        server.execute {
            if(!active)return@execute
            commands+=value;publish()
            when {
                value=="sacks"->openList()
                value.startsWith("recipe ")->openRecipe(value.removePrefix("recipe "))
            }
        }
    }
    private fun item(id:String,name:String,lore:List<String> = emptyList(),base:net.minecraft.world.item.Item=Items.PAPER)=ItemStack(base).apply {
        set(DataComponents.CUSTOM_NAME,Component.literal(name))
        set(DataComponents.CUSTOM_DATA,CustomData.of(CompoundTag().apply { putString("id",id) }))
        set(DataComponents.LORE,ItemLore(lore.map(Component::literal)))
    }
    private fun menu(title:String,contents:SimpleContainer,onClick:(Int,Int,ContainerInput,Player,ChestMenu)->Unit) {
        val mc=Minecraft.getInstance();val server=mc.singleplayerServer ?: return
        val player=server.playerList.getPlayer(mc.player?.uuid ?: return) ?: return
        player.openMenu(SimpleMenuProvider({ containerId,inventory,_ -> object:ChestMenu(MenuType.GENERIC_9x6,containerId,inventory,contents,6) {
            override fun clicked(slot:Int,button:Int,input:ContainerInput,who:Player) { onClick(slot,button,input,who,this);broadcastChanges();sendAllDataToRemote() }
        } },Component.literal(title)))
    }
    private fun openList() {
        val contents=SimpleContainer(54);contents.setItem(11,item("FLOYD_FIXTURE_SACK",sackTitle()))
        menu("Sack of Sacks",contents) { slot,button,input,_,_ ->
            if(slot==11 && button==0 && input==ContainerInput.PICKUP)openSack() else { invalid++;publish() }
        }
    }
    private fun openSack() {
        val contents=SimpleContainer(54)
        if(fixtureSack=="Gemstone") {
            val rough=stock["ROUGH_RUBY_GEM"] ?: 0;val flawed=stock["FLAWED_RUBY_GEM"] ?: 0;val fine=stock["FINE_RUBY_GEM"] ?: 0
            contents.setItem(10,item("ROUGH_RUBY_GEM","Ruby Gemstones",listOf("Rough: $rough ($rough)","Flawed: $flawed (${flawed*80})","Fine: $fine (${fine*6400})","Stored: ${rough+flawed*80+fine*6400}/18.3M"),baseItem("ROUGH_RUBY_GEM")))
        } else stock.entries.forEachIndexed { index,(id,amount)->contents.setItem(10+index,item(id,SackSupercraft.displayName(id),listOf("Stored: $amount/100,000"),baseItem(id))) }
        menu(sackTitle(),contents) { _,_,_,_,_->invalid++;publish() }
    }
    private fun sackTitle()=if(fixtureSack=="Gemstone")"Gemstones Sack" else "Large $fixtureSack Sack"
    private fun baseItem(id:String)=when {
        "MUSHROOM" in id->if("HUGE" in id)Items.RED_MUSHROOM_BLOCK else Items.RED_MUSHROOM
        "NETHER_STALK" in id->Items.NETHER_WART
        "WHEAT" in id->Items.WHEAT
        "HAY" in id->Items.HAY_BLOCK
        "SUNFLOWER" in id || id=="DOUBLE_PLANT"->Items.SUNFLOWER
        "SUGAR_CANE" in id->Items.SUGAR_CANE
        "SUGAR" in id->Items.SUGAR
        id.endsWith("_GEM")->Items.REDSTONE
        else->Items.PAPER
    }
    private fun openRecipe(name:String) {
        val mappings=mapOf("STUFFED_CHILI_PEPPER" to "CHILI_PEPPER","MUTANT_NETHER_STALK" to "ENCHANTED_NETHER_STALK","ENCHANTED_NETHER_STALK" to "NETHER_STALK","ENCHANTED_SUGAR_CANE" to "ENCHANTED_SUGAR","ENCHANTED_SUGAR" to "SUGAR_CANE","ENCHANTED_WHEAT" to "WHEAT","ENCHANTED_SUNFLOWER" to "DOUBLE_PLANT","ENCHANTED_HUGE_MUSHROOM_2" to "ENCHANTED_RED_MUSHROOM","FLAWED_RUBY_GEM" to "ROUGH_RUBY_GEM","FINE_RUBY_GEM" to "FLAWED_RUBY_GEM","FLAWLESS_RUBY_GEM" to "FINE_RUBY_GEM")
        val output=mappings.keys.singleOrNull { SackSupercraft.displayName(it)==name } ?: run { invalid++;publish();return }
        val input=mappings.getValue(output);val inputName=SackSupercraft.displayName(input)
        val cost=if(output.endsWith("_GEM"))80L else 160L
        fun external()=if(input=="ENCHANTED_NETHER_STALK")extraStock else 0L
        val contents=SimpleContainer(54)
        contents.setItem(25,item(output,name,base=baseItem(output)))
        fun pickaxe()=item("FLOYD_FIXTURE_SUPERCRAFT","Supercraft",listOf("✔ ${(stock[input] ?: 0)+external()}/${selectedCrafts*cost} $inputName","Ingredients are taken from your","inventory, sacks, ender chests and","backpacks.","Crafting $selectedCrafts ${if(selectedCrafts==1L)"item" else "items"} into your ${if(intoSacks)"sacks" else "inventory"}!","Right-Click to set amount!","Click to craft!"),Items.GOLDEN_PICKAXE)
        contents.setItem(32,pickaxe())
        contents.setItem(14,item("FLOYD_FIXTURE_DECORATION","Recipe Options",listOf("Choose a recipe."),Items.STONE_PICKAXE))
        // Actual server menus use the output name without a "Recipe" suffix.
        menu(name,contents) { slot,button,clickType,who,current ->
            if(slot==32 && button==0 && clickType==ContainerInput.QUICK_MOVE) {
                if(!ignoreMaximum)selectedCrafts=minOf(((stock[input] ?: 0)+external())/cost,20000)
                maxSelections++;contents.setItem(32,pickaxe());publish();return@menu
            }
            if(slot!=32 || button!=0 || clickType!=ContainerInput.PICKUP) { invalid++;publish();return@menu }
            attempts++
            if(reject) { publish();return@menu }
            val sackStock=stock[input] ?: 0
            val amount=sackStock+external()
            if(amount<selectedCrafts*cost) { invalid++;publish();return@menu }
            val used=selectedCrafts*cost
            stock[input]=maxOf(0,sackStock-used);if(input=="ENCHANTED_NETHER_STALK")extraStock=maxOf(0,extraStock-maxOf(0,used-sackStock));crafts+=selectedCrafts.toInt()
            if(intoSacks) {
                stock[output]=(stock[output] ?: 0)+selectedCrafts
                who.sendSystemMessage(Component.literal("You Supercrafted $name"+(if(selectedCrafts>1)" x$selectedCrafts" else "")+"!"))
            } else {
                var left=selectedCrafts
                while(left>0) {
                    val n=minOf(left,64).toInt();who.inventory.add(item(output,name).apply { count=n });left-=n
                    current.broadcastChanges();if(left>0)partialPackets++
                }
            }
            publish()
            if(closeAfterCraft) { current.broadcastChanges();current.sendAllDataToRemote();(who as? net.minecraft.server.level.ServerPlayer)?.closeContainer() }
        }
    }
}
