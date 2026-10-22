package gg.floyd.supercraft

import gg.floyd.FloydAddonsMod.mc
import gg.floyd.mixin.accessors.SackContainerLayoutAccessor
import gg.floyd.utils.font.ClickGuiFont
import gg.floyd.utils.ui.rendering.NVGPIPRenderer
import gg.floyd.utils.ui.rendering.NVGRenderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.drawString
import net.minecraft.client.gui.renderItem
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import java.lang.ref.WeakReference
import kotlin.math.ceil

/** Local controls only. It never adds or replaces any server-owned inventory slot. */
object SackSupercraftPanel {
    private const val TILE = 21
    private const val GAP = 5
    private const val CELL = TILE + GAP
    private data class Entry(val id:String,val stack:ItemStack)
    private data class Layout(val x:Int,val y:Int,val width:Int,val height:Int,val columns:Int,val rows:Int) {
        val capacity get() = columns * rows
        val gridY get() = y + 46
        val gridInset get() = (width - ((columns - 1) * CELL + TILE)) / 2
        fun contains(px:Double,py:Double) = px>=x && px<x+width && py>=y && py<y+height
    }
    private var lastScreen=WeakReference<AbstractContainerScreen<*>>(null)
    private var page=0
    private var lastState:Map<String,Any?> = emptyMap()
    private fun layout(screen:AbstractContainerScreen<*>):Layout? {
        val access=screen as SackContainerLayoutAccessor
        val width=minOf(132,access.`floyd$sackLeft`()-12)
        if(width<50)return null
        val y=maxOf(4,access.`floyd$sackTop`())
        val columns=maxOf(1,(width-12+GAP)/CELL)
        val targets=SackSupercraft.upgradeTargets(screen.title.string).size
        val contentHeight=64+maxOf(1,ceil(targets.toDouble()/columns).toInt())*CELL
        val height=minOf(maxOf(90,contentHeight),access.`floyd$sackHeight`(),mc.window.guiScaledHeight-y-4)
        if(height<90)return null
        return Layout(access.`floyd$sackLeft`()-8-width,y,width,height,
            columns,maxOf(1,(height-64)/CELL))
    }
    private fun entries(screen:AbstractContainerScreen<*>,items:List<ItemStack>):List<Entry> {
        val sack=SackSupercraft.sackName(screen.title.string) ?: return emptyList()
        val actual=items.asSequence().filter { !it.isEmpty }.associateBy { SackSupercraft.itemId(it) }
        return SackSupercraft.upgradeTargets(sack).map { id ->
            Entry(id,actual[id] ?: SackSupercraft.targetIcon(id))
        }.sortedBy { it.stack.hoverName.string }
    }
    private fun entries(screen:AbstractContainerScreen<*>):List<Entry> {
        val menu=screen.menu as? ChestMenu ?: return emptyList()
        return entries(screen,menu.slots.take(menu.rowCount*9).map { it.item })
    }
    private fun pages(size:Int,layout:Layout)=maxOf(1,ceil(size.toDouble()/layout.capacity).toInt())
    private fun sync(screen:AbstractContainerScreen<*>,size:Int,layout:Layout) {
        if(lastScreen.get()!==screen) { lastScreen=WeakReference(screen);page=0 }
        page=page.coerceIn(0,pages(size,layout)-1)
    }
    fun render(graphics:GuiGraphics,screen:AbstractContainerScreen<*>,items:List<ItemStack>,ignored:Set<String>,mouseX:Int,mouseY:Int) {
        if(SackSupercraft.sackName(screen.title.string)==null) { lastState=mapOf("visible" to false);return }
        val layout=layout(screen) ?: return
        val entries=entries(screen,items);sync(screen,entries.size,layout)
        val font=ClickGuiFont.font
        val (x,y,width,height)=layout
        val hoverButton=entries.isNotEmpty() && mouseX>=x+6 && mouseX<x+width-6 && mouseY>=y+6 && mouseY<y+26
        val visible=entries.drop(page*layout.capacity).take(layout.capacity)
        val multiplier=mc.window.guiScale.toFloat()/NVGRenderer.devicePixelRatio()
        NVGPIPRenderer.draw(graphics,x,y,width,height,multiplier,localCoordinates=true) {
            NVGRenderer.rect(.5f,.5f,width-1f,height-1f,0xF018202B.toInt(),9f)
            NVGRenderer.hollowRect(.7f,.7f,width-1.4f,height-1.4f,.8f,0xFF526171.toInt(),9f)
            NVGRenderer.rect(6f,6f,width-12f,20f,if(hoverButton)0xFF40566B.toInt() else 0xFF2E4052.toInt(),5f)
            for((index,entry) in visible.withIndex()) {
                val tx=layout.gridInset+(index%layout.columns)*CELL
                val ty=46+(index/layout.columns)*CELL
                val hover=mouseX>=x+tx && mouseX<x+tx+TILE && mouseY>=y+ty && mouseY<y+ty+TILE
                val color=when { entry.id in ignored->0xAA4B252E.toInt();hover->0xFF3D4A59.toInt();else->0x88404B58.toInt() }
                NVGRenderer.rect(tx.toFloat(),ty.toFloat(),TILE.toFloat(),TILE.toFloat(),color,4f)
            }
        }
        graphics.nextStratum()
        val label=if(SackSupercraft.isCrafting())"Stop Crafting" else if(width<96)"Craft Sack" else "Supercraft Sack"
        graphics.drawString(font,label,x+(width-font.width(label))/2,y+12,0xFFE7EEF5.toInt(),false)
        val caption=if(width<96)"Ignore items" else "Ignore upgrades"
        if(entries.isEmpty()) {
            val emptyLabel="No upgrades"
            graphics.drawString(font,emptyLabel,x+(width-font.width(emptyLabel))/2,y+49,0xFFBAC7D4.toInt(),false)
        } else graphics.drawString(font,caption,x+7,y+32,0xFFBAC7D4.toInt(),false)
        val tiles=visible.mapIndexed { index,entry ->
            val tx=x+layout.gridInset+(index%layout.columns)*CELL
            val ty=layout.gridY+(index/layout.columns)*CELL
            val blocked=entry.id in ignored
            val hover=mouseX>=tx && mouseX<tx+TILE && mouseY>=ty && mouseY<ty+TILE
            graphics.renderItem(entry.stack.copyWithCount(1),tx+(TILE-16)/2,ty+(TILE-16)/2)
            if(hover)graphics.setComponentTooltipForNextFrame(font,listOf(entry.stack.hoverName,
                Component.literal(if(blocked)"Ignored: this item will not be crafted" else "Click to prevent crafting this upgrade")),mouseX,mouseY)
            mapOf("id" to entry.id,"ignored" to blocked,"x" to tx,"y" to ty)
        }
        if(visible.any { it.id in ignored }) {
            graphics.nextStratum()
            NVGPIPRenderer.draw(graphics,x,y,width,height,multiplier,localCoordinates=true) {
                for((index,entry) in visible.withIndex())if(entry.id in ignored) {
                    val tx=layout.gridInset.toFloat()+(index%layout.columns)*CELL
                    val ty=46f+(index/layout.columns)*CELL
                    for((ax,ay,bx,by) in listOf(floatArrayOf(tx+5,ty+5,tx+16,ty+16),floatArrayOf(tx+16,ty+5,tx+5,ty+16))) {
                        NVGRenderer.line(ax,ay,bx,by,2f,0xFFFF838A.toInt())
                        NVGRenderer.circle(ax,ay,1f,0xFFFF838A.toInt())
                        NVGRenderer.circle(bx,by,1f,0xFFFF838A.toInt())
                    }
                }
            }
        }
        val count=entries.count { it.id in ignored }
        val pageCount=pages(entries.size,layout)
        val footerY=y+height-13
        if(pageCount>1) {
            graphics.drawString(font,"<",x+7,footerY,0xFFDDE7F0.toInt(),false)
            val text="${page+1}/$pageCount"
            graphics.drawString(font,text,x+(width-font.width(text))/2,footerY,0xFFBAC7D4.toInt(),false)
            graphics.drawString(font,">",x+width-13,footerY,0xFFDDE7F0.toInt(),false)
        } else if(entries.isNotEmpty()) {
            graphics.drawString(font,"$count ignored",x+7,footerY,0xFFBAC7D4.toInt(),false)
        }
        lastState=mapOf("visible" to true,"x" to x,"y" to y,"width" to width,"height" to height,
            "containerX" to (screen as SackContainerLayoutAccessor).`floyd$sackLeft`(),"items" to entries.size,
            "ignored" to count,"page" to page,"pages" to pageCount,"tiles" to tiles,
            "buttonX" to x+6,"buttonY" to y+6,"targetsOnly" to true,"rounded" to true,"smoothCross" to true,
            "font" to ClickGuiFont.FONT_ID.toString(),"customFontForced" to true,"tileSize" to TILE,"tileGap" to GAP,
            "emptyLabel" to if(entries.isEmpty())"No upgrades" else null)
    }
    fun click(screen:AbstractContainerScreen<*>,x:Double,y:Double,button:Int):Boolean {
        val sack=SackSupercraft.sackName(screen.title.string) ?: return false
        val layout=layout(screen) ?: return false
        if(!layout.contains(x,y))return false
        if(button!=0)return true
        val entries=entries(screen);sync(screen,entries.size,layout)
        if(entries.isEmpty())return true
        if(y>=layout.y+6 && y<layout.y+26 && x>=layout.x+6 && x<layout.x+layout.width-6) {
            if(SackSupercraft.isCrafting())SackSupercraft.stop("Stopped from sack panel") else SackSupercraft.start(sack)
            return true
        }
        if(y>=layout.y+layout.height-17) {
            if(x<layout.x+28)page=maxOf(0,page-1)
            else if(x>=layout.x+layout.width-28)page=minOf(pages(entries.size,layout)-1,page+1)
            return true
        }
        val col=((x-layout.x-layout.gridInset)/CELL).toInt()
        val row=((y-layout.gridY)/CELL).toInt()
        if(x>=layout.x+layout.gridInset && y>=layout.gridY && col in 0 until layout.columns && row in 0 until layout.rows &&
            (x-layout.x-layout.gridInset)%CELL<TILE && (y-layout.gridY)%CELL<TILE) {
            entries.getOrNull(page*layout.capacity+row*layout.columns+col)?.let { SackSupercraft.toggleIgnored(it.id) }
        }
        return true
    }
    fun scroll(screen:AbstractContainerScreen<*>,x:Double,y:Double,vertical:Double):Boolean {
        if(SackSupercraft.sackName(screen.title.string)==null)return false
        val layout=layout(screen) ?: return false
        if(!layout.contains(x,y))return false
        val entries=entries(screen);sync(screen,entries.size,layout)
        if(vertical!=0.0)page=(page+if(vertical>0)-1 else 1).coerceIn(0,pages(entries.size,layout)-1)
        return true
    }
    fun state():Map<String,Any?> {
        val screen=mc.screen as? AbstractContainerScreen<*>
        return if(screen==null || lastScreen.get()!==screen || SackSupercraft.sackName(screen.title.string)==null)
            mapOf("visible" to false) else lastState
    }
}
