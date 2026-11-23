package gg.floyd.features.impl.render.itemsearch

import gg.floyd.FloydAddonsMod.mc
import net.minecraft.client.gui.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Read-only container view: NEU-style recipe area above the player's inventory. */
internal class ItemSearchDetail(private val parent: Screen, val entry: SearchItem) : Screen(Component.literal("Recipe: ${SearchItem.clean(entry.name)}")) {
    private val sources = ItemAcquisition.sources(entry)
    private var page = 0
    private var scroll = 0
    private var previewKey = ""
    private var preview: LivingEntity? = null
    private data class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val click: () -> Unit)
    private val hits = mutableListOf<Hit>()
    private val pw = 176
    private val ph = 222
    private val left get() = (width - pw) / 2
    private val top get() = (height - ph) / 2
    private val source get() = sources.getOrNull(page)
    private val sourceTitleWidth get() = if(sources.size>1)96 else 160
    private val text = 0xFF404040.toInt()
    private val muted = 0xFF606060.toInt()
    private fun label(g: GuiGraphics, value: String, x: Int, y: Int, color: Int = text) = g.drawString(font, value, x, y, color)
    private fun open(id: String) { ItemSearchCatalog.byId[id]?.takeIf(ItemAcquisition::canOpen)?.let { mc.setScreen(ItemSearchDetail(this, it)) } }
    private fun turn(delta: Int) { page = (page + delta).coerceIn(0, maxOf(0, sources.size - 1)); scroll = 0; previewKey = ""; preview = null }
    override fun extractBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) { g.fill(0, 0, width, height, 0x55000000) }
    private fun frame(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        g.fill(x, y, x+w, y+h, 0xFF101010.toInt())
        g.fill(x+1, y+1, x+w-1, y+h-1, 0xFF555555.toInt())
        g.fill(x+1, y+1, x+w-2, y+3, 0xFFFFFFFF.toInt())
        g.fill(x+1, y+1, x+3, y+h-2, 0xFFFFFFFF.toInt())
        g.fill(x+3, y+3, x+w-3, y+h-3, 0xFFC6C6C6.toInt())
    }
    private fun slot(g: GuiGraphics, x: Int, y: Int, size: Int = 18, selected: Boolean = false) {
        g.fill(x, y, x+size, y+size, 0xFFFFFFFF.toInt())
        g.fill(x, y, x+size-1, y+size-1, 0xFF373737.toInt())
        g.fill(x+1, y+1, x+size-1, y+size-1, if (selected) 0xFFB0B0B0.toInt() else 0xFF8B8B8B.toInt())
    }
    override fun extractRenderState(g: GuiGraphics, mx: Int, my: Int, partialTick: Float) {
        hits.clear()
        frame(g, left, top, pw, ph)
        tile(g,ItemAmount(entry.id,"1"),left+7,top+4,mx,my,false)
        label(g, font.plainSubstrByWidth(entry.name.replace(Regex("§."), ""), 139), left+28, top+7)
        frame(g, left-20, top+3, 17, 17)
        label(g, "<", left-15, top+7)
        hits += Hit(left-20, top+3, 17, 17) { onClose() }
        source?.let { s -> when(s.kind) {
            "crafting" -> crafting(g,s,mx,my)
            "drops" -> drops(g,s,mx,my)
            "katgrade" -> katUpgrade(g,s,mx,my)
            "npc_shop" -> npcPurchase(g,s,mx,my)
            "forge" -> forge(g,s,mx,my)
            "trade" -> trade(g,s,mx,my)
            else -> fallback(g)
        } } ?: fallback(g)
        if (sources.size > 1) {
            frame(g,left+110,top+23,12,14); frame(g,left+146,top+23,12,14)
            label(g,"<",left+113,top+26);label(g,">",left+149,top+26)
            val caption="${page+1}/${sources.size}"
            label(g,caption,left+134-font.width(caption)/2,top+26)
            hits += Hit(left+110,top+23,12,14) { turn(-1) }
            hits += Hit(left+146,top+23,12,14) { turn(1) }
        }
        inventory(g,mx,my)
    }
    private fun crafting(g:GuiGraphics,s:ItemSource,mx:Int,my:Int) {
        label(g,"Crafting",left+8,top+25)
        repeat(9) { i ->
            val raw=s.text("${'A'+i/3}${i%3+1}")
            val x=left+30+i%3*18;val y=top+48+i/3*18
            if(raw.isBlank())slot(g,x,y) else tile(g,ItemAmount.parse(raw),x,y,mx,my)
        }
        arrow(g,left+89,top+65)
        tile(g,s.output,left+124,top+66,mx,my,false)
    }
    private fun arrow(g:GuiGraphics,x:Int,y:Int) {
        g.fill(x,y+5,x+17,y+12,0xFF8B8B8B.toInt())
        repeat(9) { i -> g.fill(x+13+i,y+i,x+14+i,y+17-i,0xFF8B8B8B.toInt()) }
    }
    private fun inventory(g:GuiGraphics,mx:Int,my:Int) {
        label(g,"Inventory",left+8,top+129)
        repeat(36) { i ->
            val index=if(i<27)i+9 else i-27
            val x=left+7+(i%9)*18;val y=top+139+if(i<27)(i/9)*18 else 58
            slot(g,x,y)
            val stack=mc.player?.inventory?.getItem(index) ?: ItemStack.EMPTY
            if(!stack.isEmpty) {
                g.renderItem(stack,x+1,y+1)
                if(stack.count>1) label(g,stack.count.toString(),x+17-font.width(stack.count.toString()),y+10,-1)
                if(mx in x until x+18 && my in y until y+18) {
                    g.fill(x+1,y+1,x+17,y+17,0x55FFFFFF)
                    g.setTooltipForNextFrame(font,stack,mx,my)
                }
            }
        }
    }
    private fun drops(g:GuiGraphics,s:ItemSource,mx:Int,my:Int) {
        label(g,font.plainSubstrByWidth(s.text("name",s.owner.name).replace(Regex("§."),""),sourceTitleWidth),left+8,top+25)
        val mob=mob(s)
        if(mob!=null) {
            val size=(65f/(maxOf(mob.bbHeight,mob.bbWidth)*1.25f)).toInt().coerceAtLeast(1)
            InventoryScreen.extractEntityInInventoryFollowsMouse(g,left+7,top+38,left+73,top+109,size,.0625f,mx.toFloat(),my.toFloat(),mob)
        } else {
            g.pose().pushMatrix();g.pose().translate((left+22).toFloat(),(top+53).toFloat());g.pose().scale(2f,2f)
            g.renderItem(ItemSearchCatalog.icon(s.owner),0,0);g.pose().popMatrix()
        }
        scroll=scroll.coerceIn(0,maxOf(0,s.drops.size-6))
        s.drops.drop(scroll).take(6).forEachIndexed { i,d ->
            val amount=ItemAmount.parse(d["id"].asString)
            val chance=d["chance"]?.asString ?: "Unknown"
            val formatted=ItemSearchFormatting.dropChance(chance)
            val notes=d.getAsJsonArray("extra")?.map { it.asString }.orEmpty()+"§7Base chance: ${formatted.tooltip}"
            val cellX=left+78+i%3*30
            val cellY=top+38+i/3*32
            tile(g,amount,cellX+6,cellY,mx,my,notes=notes,selected=amount.id==entry.id)
            val caption=(formatted.fraction ?: formatted.percentage)+if(formatted.conditions.isNotBlank())"*" else ""
            val scale=minOf(1f,28f/maxOf(1,font.width(caption)))
            g.pose().pushMatrix()
            g.pose().translate(cellX+15f-font.width(caption)*scale/2f,cellY+20f)
            g.pose().scale(scale,scale)
            label(g,caption,0,0)
            g.pose().popMatrix()
        }
        if(mx in left+7 until left+74 && my in top+35 until top+110) {
            val notes=listOf(s.text("name",s.owner.name),"§7Level: ${s.text("level","Unknown")}")+s.recipe.getAsJsonArray("extra")?.map { it.asString }.orEmpty()
            g.setComponentTooltipForNextFrame(font,notes.map(Component::literal),mx,my)
        }
        label(g,if(s.drops.size>6)"Scroll for more" else "Base chances",left+8,top+114,muted)
    }
    private fun mob(s: ItemSource): LivingEntity? {
        val render = s.text("render")
        if (previewKey == render) return preview
        previewKey = render
        val meta = ItemSearchCatalog.mobs.getAsJsonObject(render.removePrefix("@"))
        val entityId = meta?.get("entitytype")?.asString ?: when (render) {
            "Eisengolem" -> "minecraft:iron_golem"; "Dragon" -> "minecraft:ender_dragon"
            else -> "minecraft:" + render.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        }
        val level = mc.level ?: return null
        preview = runCatching { BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entityId)).orElse(null)?.create(level, EntitySpawnReason.COMMAND) as? LivingEntity }.getOrNull()
        meta?.getAsJsonArray("modifiers")?.map { it.asJsonObject }?.filter { it["type"]?.asString == "equipment" }?.forEach { m ->
            mapOf("hand" to EquipmentSlot.MAINHAND, "helmet" to EquipmentSlot.HEAD, "chestplate" to EquipmentSlot.CHEST, "leggings" to EquipmentSlot.LEGS, "feet" to EquipmentSlot.FEET).forEach { (key, slot) ->
                m[key]?.asString?.let { id -> ItemSearchCatalog.byId[id]?.let { preview?.setItemSlot(slot, ItemSearchCatalog.icon(it).copy()) } }
            }
        }
        return preview
    }
    private fun katIngredients(s:ItemSource):List<ItemAmount> =
        s.recipe.getAsJsonArray("items")?.map { ItemAmount.parse(it.asString) }.orEmpty()+
            listOf(ItemAmount.parse(s.text("input")),ItemAmount("SKYBLOCK_COIN",s.text("coins","0")))
    private fun katUpgrade(g:GuiGraphics,s:ItemSource,mx:Int,my:Int) {
        label(g,"Kat Pet Upgrade",left+8,top+25)
        // Kat has a circular ingredient display with the upgraded pet at its center.
        // Keep this separate from the crafting-grid renderer.
        val ingredients=katIngredients(s)
        val cx=left+88;val cy=top+70
        ingredients.forEachIndexed { i,amount ->
            val angle=2.0*Math.PI*i/ingredients.size
            val x=cx+(kotlin.math.cos(angle)*27).toInt()-9
            val y=cy+(kotlin.math.sin(angle)*27).toInt()-9
            tile(g,amount,x,y,mx,my,notes=if(amount.id=="SKYBLOCK_COIN")listOf("§7Base cost before pet-level discounts") else emptyList())
        }
        tile(g,s.output,cx-9,cy-9,mx,my,false,notes=listOf("§7Upgraded pet"),selected=true)
        val sec=s.text("time").toLongOrNull() ?: 0L
        val duration=ItemSearchFormatting.duration(sec).ifEmpty { "Instant" }
        label(g,duration,cx-font.width(duration)/2,top+108,0xFF800080.toInt())
        val coins=s.text("coins","0").toLongOrNull()?.let { String.format(java.util.Locale.US,"%,d",it) } ?: s.text("coins","0")
        val cost="$coins coins (base)"
        label(g,cost,cx-font.width(cost)/2,top+119,muted)
    }
    private fun purchaseSlots(s:ItemSource):List<Pair<Int,Int>> {
        val rows=minOf(4,s.costs.size)
        val startY=maxOf(37,75-(rows*18+maxOf(0,rows-1)*6)/2)
        val columns=(s.costs.size+3)/4
        return s.costs.indices.map { i -> (left+38-maxOf(0,columns-1)*9+i/4*20) to (top+startY+i%4*24) }
    }
    private fun npcPurchase(g:GuiGraphics,s:ItemSource,mx:Int,my:Int) {
        val title="Buy from ${s.owner.name.replace(Regex("§."),"")}"
        label(g,font.plainSubstrByWidth(title,sourceTitleWidth),left+8,top+25)
        val positions=purchaseSlots(s)
        s.costs.forEachIndexed { i,amount ->
            val (x,y)=positions[i]
            tile(g,amount,x,y,mx,my)
        }
        arrow(g,left+89,top+65)
        tile(g,s.output,left+124,top+66,mx,my,false)
    }
    private data class ForgeSlot(val amount: ItemAmount, val x: Int, val y: Int)

    private fun forgeSlots(inputs: List<ItemAmount>): List<ForgeSlot> {
        val offset = scroll.coerceIn(0, maxOf(0, inputs.size - 12))
        return inputs.drop(offset).take(12).mapIndexed { index, amount ->
            val angle = 2 * Math.PI * index / inputs.size
            val (x, y) = when {
                inputs.size == 1 -> 40 to 65
                inputs.size <= 8 -> (40 + (kotlin.math.cos(angle) * 30).toInt()) to
                    (65 + (kotlin.math.sin(angle) * 30).toInt())
                else -> (13 + index % 4 * 18) to (47 + index / 4 * 18)
            }
            ForgeSlot(amount, left + x, top + y)
        }
    }

    private fun forge(g: GuiGraphics, s: ItemSource, mx: Int, my: Int) {
        label(g, "The Forge", left+8, top+21)
        val inputs = s.forgeInputs
        scroll = scroll.coerceIn(0, maxOf(0, inputs.size - 12))
        forgeSlots(inputs).forEach { (amount, x, y) -> tile(g, amount, x, y, mx, my) }
        arrow(g, left+89, top+65)
        tile(g, s.output, left+123, top+65, mx, my, false)
        s.forgeDuration?.let { label(g, it, left+132-font.width(it)/2, top+51, 0xFF800080.toInt()) }
        if (inputs.size > 12) label(g, "Scroll for more", left+8, top+114, muted)
    }

    private fun trade(g:GuiGraphics,s:ItemSource,mx:Int,my:Int) {
        label(g,"Trades Menu",left+8,top+25)
        val inputs=s.costs
        scroll=scroll.coerceIn(0,maxOf(0,inputs.size-9))
        repeat(9) { i ->
            val x=left+30+i%3*18;val y=top+48+i/3*18
            inputs.getOrNull(scroll+i)?.let { tile(g,it,x,y,mx,my) } ?: slot(g,x,y)
        }
        arrow(g,left+89,top+65);tile(g,s.output,left+124,top+66,mx,my,false)
        s.text("time").toLongOrNull()?.let { sec -> label(g,"${sec/3600}h ${(sec%3600)/60}m",left+8,top+114,muted) }
    }
    private fun fallback(g:GuiGraphics) {
        label(g,"How to Obtain",left+8,top+26)
        val hints=ItemAcquisition.loreSources(entry)
        val strings=hints.ifEmpty { listOf("Obtainment unknown") }
        val lines=strings.flatMap { font.split(Component.literal(it),158) }
        scroll=scroll.coerceIn(0,maxOf(0,lines.size-8))
        lines.drop(scroll).take(8).forEachIndexed { i,line->g.drawString(font,line,left+9,top+43+i*10,text) }
    }
    private fun tile(g:GuiGraphics,a:ItemAmount,x:Int,y:Int,mx:Int,my:Int,navigable:Boolean=true,notes:List<String> = emptyList(),selected:Boolean=false) {
        val item=ItemSearchCatalog.byId[a.id];val hover=mx in x until x+18 && my in y until y+18
        slot(g,x,y,selected=selected)
        val stack=item?.let { ItemSearchCatalog.icon(it).copyWithCount(a.stackCount.coerceAtLeast(1)) }
            ?: ItemStack(if(a.id=="SKYBLOCK_COIN")Items.GOLD_NUGGET else Items.PAPER)
        g.renderItem(stack,x+1,y+1)
        a.countLabel?.let { caption ->
            val scale=minOf(1f,18f/maxOf(1,font.width(caption)))
            g.pose().pushMatrix();g.pose().translate(x+17f-font.width(caption)*scale,y+10f);g.pose().scale(scale,scale)
            label(g,caption,0,0,-1);g.pose().popMatrix()
        }
        if(hover) {
            g.fill(x+1,y+1,x+17,y+17,0x55FFFFFF)
            val title=if(a.id=="SKYBLOCK_COIN")"Coins" else a.id
            val tooltip=if(item!=null)Screen.getTooltipFromItem(mc,stack) else listOf(Component.literal(title))
            val extras=listOf("§7Quantity: ${ItemSearchFormatting.exact(a.count)}")+notes
            g.setComponentTooltipForNextFrame(font,tooltip+extras.map(Component::literal),mx,my)
        }
        if(navigable && item!=null)hits+=Hit(x,y,18,18) { open(a.id) }
    }
    fun state():Map<String,Any?> = mapOf("item" to entry.id,"mode" to (source?.kind ?: "obtain"),"page" to page,"pages" to sources.size,
        "source" to source?.owner?.id,"mobName" to source?.takeIf { it.kind=="drops" }?.text("name"),
        "mobPreview" to preview?.type?.let { BuiltInRegistries.ENTITY_TYPE.getKey(it).toString() },"scroll" to scroll,
        "mobDyeColors" to preview?.let { entity -> listOf(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET).associate { slot ->
            slot.name to entity.getItemBySlot(slot).get(DataComponents.DYED_COLOR)?.rgb() } },
        "drops" to source?.drops?.map { d -> val raw=d["chance"]?.asString ?: "Unknown";val chance=ItemSearchFormatting.dropChance(raw)
            mapOf("item" to d["id"].asString,"chance" to raw,"percentage" to chance.percentage,"fraction" to chance.fraction,"tooltip" to chance.tooltip) },
        "backgroundColor" to "#55000000","x" to left,"y" to top,"width" to pw,"height" to ph,
        "recipeOnly" to (source?.kind=="crafting"),"inventoryStyle" to true,"inventorySlots" to 36,"headerVisible" to false,
        "sourceNavigation" to if(sources.size>1)mapOf("position" to "top_right","caption" to "${page+1}/${sources.size}",
            "previousX" to left+110,"nextX" to left+146,"y" to top+23,"width" to 12,"height" to 14) else null,
        "forgeLayout" to source?.takeIf { it.kind=="forge" }?.let { s ->
            val inputs = s.forgeInputs
            mapOf("style" to if (inputs.size <= 8) "circular" else "rows", "craftingGrid" to false,
                "duration" to s.forgeDuration, "result" to s.output.id, "resultLabel" to s.output.countLabel,
                "ingredients" to forgeSlots(inputs).map { (amount, x, y) ->
                    mapOf("item" to amount.id, "label" to amount.countLabel, "x" to x, "y" to y)
                })
        },
        "purchaseLayout" to source?.takeIf { it.kind=="npc_shop" }?.let { s ->
            val positions=purchaseSlots(s)
            mapOf("style" to "npc_shop","craftingGrid" to false,"seller" to s.owner.id,"result" to s.output.id,
                "costs" to s.costs.mapIndexed { i,a -> mapOf("item" to a.id,"count" to a.count,
                    "label" to a.countLabel,
                    "x" to positions[i].first,"y" to positions[i].second) },"resultX" to left+124,"resultY" to top+66)
        },
        "katLayout" to source?.takeIf { it.kind=="katgrade" }?.let { s -> mapOf("style" to "circular","craftingGrid" to false,
            "input" to s.text("input"),"output" to s.output.id,"coins" to s.text("coins"),"seconds" to s.text("time"),
            "coinLabel" to ItemSearchFormatting.compact(s.text("coins")),"coinHover" to ItemSearchFormatting.exact(s.text("coins")),
            "petPreviewMinLevel" to 1,"petPreviewMaxLevel" to 100,"petName" to entry.name,"petLore" to entry.lore,
            "ingredients" to katIngredients(s).map { mapOf("item" to it.id,"count" to it.count) },"centerX" to left+88,"centerY" to top+70) },
        "dropChancesBelowIcons" to true,"dropChancesOnHover" to true,"visibleDropCount" to minOf(6,maxOf(0,(source?.drops?.size ?: 0)-scroll)),
        "dropLabelFormat" to "fraction","dropHoverFormat" to "percentage_and_fraction",
        "recipeGridX" to left+30,"recipeGridY" to top+48,"recipeCell" to 18)
    override fun mouseClicked(event:MouseButtonEvent,doubleClick:Boolean):Boolean {
        if(event.button()==0)hits.firstOrNull { event.x()>=it.x && event.x()<it.x+it.w && event.y()>=it.y && event.y()<it.y+it.h }?.let { it.click();return true }
        return super.mouseClicked(event,doubleClick)
    }
    override fun mouseScrolled(x:Double,y:Double,horizontal:Double,vertical:Double):Boolean { scroll=maxOf(0,scroll-vertical.toInt()*if(source?.kind=="drops")3 else 1);return true }
    override fun onClose() { mc.setScreen(parent) }
    override fun isPauseScreen()=false
}
