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
    private var commands=mutableListOf<String>()
    @Volatile private var diagnostics:Map<String,Any?> = emptyMap()
    fun state()=diagnostics
    private fun publish() { diagnostics=mapOf("active" to active,"crafts" to crafts,"attempts" to attempts,"invalidClicks" to invalid,"stock" to stock.toMap(),"commands" to commands.toList(),"reject" to reject,"intoSacks" to intoSacks,"selectedCrafts" to selectedCrafts,"maxSelections" to maxSelections,"partialPackets" to partialPackets,"extraStock" to extraStock) }
    fun open(rejectCraft:Boolean=false,sacks:Boolean=false,amount:Long=1,stockAmount:Long=321,otherStorage:Long=0,rawAmount:Long=0,sugarAmount:Long=159,closeOnCraft:Boolean=false,ignoreMax:Boolean=false) {
        val mc=Minecraft.getInstance();val server=mc.singleplayerServer ?: error("Integrated server required")
        require(amount in 1..20000 && stockAmount in 0..10_000_000 && otherStorage in 0..10_000_000 && rawAmount in 0..10_000_000 && sugarAmount in 0..10_000_000)
        server.execute { active=true;reject=rejectCraft;crafts=0;invalid=0;attempts=0;intoSacks=sacks;selectedCrafts=amount;maxSelections=0;partialPackets=0;extraStock=otherStorage;rawWart=rawAmount>0;closeAfterCraft=closeOnCraft;ignoreMaximum=ignoreMax;commands=mutableListOf();stock=linkedMapOf("ENCHANTED_NETHER_STALK" to stockAmount,"ENCHANTED_SUGAR" to sugarAmount,"NETHER_STALK" to rawAmount);publish();openSack() }
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
        val contents=SimpleContainer(54);contents.setItem(11,item("LARGE_AGRONOMY_SACK",if(rawWart)"Large Agronomy Sack" else "Large Enchanted Agronomy Sack"))
        menu("Sack of Sacks",contents) { slot,button,input,_,_ ->
            if(slot==11 && button==0 && input==ContainerInput.PICKUP)openSack() else { invalid++;publish() }
        }
    }
    private fun openSack() {
        val contents=SimpleContainer(54)
        contents.setItem(10,item("ENCHANTED_NETHER_STALK","Enchanted Nether Wart",listOf("Stored: ${stock["ENCHANTED_NETHER_STALK"] ?: 0}/100,000")))
        contents.setItem(11,item("ENCHANTED_SUGAR","Enchanted Sugar",listOf("Stored: ${stock["ENCHANTED_SUGAR"] ?: 0}/100,000")))
        if(rawWart)contents.setItem(12,item("NETHER_STALK","Nether Wart",listOf("Stored: ${stock["NETHER_STALK"] ?: 0}/100,000")))
        menu(if(rawWart)"Large Agronomy Sack" else "Large Enchanted Agronomy Sack",contents) { _,_,_,_,_->invalid++;publish() }
    }
    private fun openRecipe(name:String) {
        val input=when(name) { "Mutant Nether Wart"->"ENCHANTED_NETHER_STALK";"Enchanted Nether Wart"->"NETHER_STALK";"Enchanted Sugar Cane"->"ENCHANTED_SUGAR";else->{ invalid++;publish();return } }
        val inputName=when(input) { "ENCHANTED_NETHER_STALK"->"Enchanted Nether Wart";"NETHER_STALK"->"Nether Wart";else->"Enchanted Sugar" }
        val output=when(name) { "Mutant Nether Wart"->"MUTANT_NETHER_STALK";"Enchanted Nether Wart"->"ENCHANTED_NETHER_STALK";else->"ENCHANTED_SUGAR_CANE" }
        fun external()=if(input=="ENCHANTED_NETHER_STALK")extraStock else 0L
        val contents=SimpleContainer(54)
        contents.setItem(25,item(output,name))
        fun pickaxe()=item("FLOYD_FIXTURE_SUPERCRAFT","Supercraft",listOf("✔ ${(stock[input] ?: 0)+external()}/${selectedCrafts*160} $inputName","Ingredients are taken from your","inventory, sacks, ender chests and","backpacks.","Crafting $selectedCrafts ${if(selectedCrafts==1L)"item" else "items"} into your ${if(intoSacks)"sacks" else "inventory"}!","Right-Click to set amount!","Click to craft!"),Items.GOLDEN_PICKAXE)
        contents.setItem(32,pickaxe())
        contents.setItem(14,item("FLOYD_FIXTURE_DECORATION","Recipe Options",listOf("Choose a recipe."),Items.STONE_PICKAXE))
        // Actual server menus use the output name without a "Recipe" suffix.
        menu(name,contents) { slot,button,clickType,who,current ->
            if(slot==32 && button==0 && clickType==ContainerInput.QUICK_MOVE) {
                if(!ignoreMaximum)selectedCrafts=minOf(((stock[input] ?: 0)+external())/160,20000)
                maxSelections++;contents.setItem(32,pickaxe());publish();return@menu
            }
            if(slot!=32 || button!=0 || clickType!=ContainerInput.PICKUP) { invalid++;publish();return@menu }
            attempts++
            if(reject) { publish();return@menu }
            val sackStock=stock[input] ?: 0
            val amount=sackStock+external()
            if(amount<selectedCrafts*160) { invalid++;publish();return@menu }
            val used=selectedCrafts*160
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
