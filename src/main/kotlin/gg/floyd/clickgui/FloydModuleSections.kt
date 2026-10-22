@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package gg.floyd.clickgui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.screens.ConfigListRow
import org.polyfrost.oneconfig.internal.ui.search.ConfigListEntry
import org.polyfrost.oneconfig.internal.ui.search.SettingNode
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.internal.ui.api.settings.BooleanOptionData
import org.polyfrost.oneconfig.internal.ui.components.settings.BooleanOption
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth

private data class ModuleExpansionControl(val key:String,val search:Boolean,val rotation:Float,val placed:(androidx.compose.ui.geometry.Rect)->Unit)
private val LocalModuleExpansionControl=staticCompositionLocalOf<ModuleExpansionControl?> { null }

/** Uses the native switch binding inside the native full-width option card. */
object FloydModuleEnableVisualizer:Visualizer {
    @Composable override fun visualize(prop:Property<*>) {
        val control=LocalModuleExpansionControl.current
        val option=remember(prop) { BooleanOptionData(prop,BooleanOptionData.Style.Switch) }
        if(control==null) { BooleanOption(option);return }
        val optionWidth=LocalOptionWidth.current
        val color=LocalTheme.current.textColorSecondary
        Row(Modifier.width(optionWidth),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End)) {
            CompositionLocalProvider(LocalOptionWidth provides (optionWidth-48.dp)) { BooleanOption(option) }
            Box(Modifier.size(40.dp).onGloballyPositioned { control.placed(it.boundsInWindow()) }
                .semantics { contentDescription="${if(control.rotation>90)"Collapse" else "Expand"} module settings" }
                .clickable(interactionSource=remember { MutableInteractionSource() },indication=null) {
                    FloydModuleExpansion.toggle(control.key,control.search)
                },contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(18.dp).rotate(control.rotation)) {
                    drawLine(color,Offset(size.width*.2f,size.height*.35f),Offset(size.width*.5f,size.height*.65f),2.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(color,Offset(size.width*.5f,size.height*.65f),Offset(size.width*.8f,size.height*.35f),2.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
        }
    }
}

internal data class FloydModuleSection(val name:String,val toggle:ConfigListEntry.Item?,val settings:List<ConfigListEntry.Item>)

internal fun moduleSections(entries:List<ConfigListEntry>):List<FloydModuleSection> =
    entries.filterIsInstance<ConfigListEntry.Item>().groupBy { entry ->
        val node=entry.node
        (if(node is SettingNode.Leaf)node.prop else (node as SettingNode.Accordion).tree).getMetadata<String>("subcategory") ?: "Settings"
    }.map { (name,rows) ->
        val prop=FloydOneConfig.moduleEnableProperty(name)
        val toggle=prop?.let { ConfigListEntry.Item(SettingNode.Leaf(it)) }
        FloydModuleSection(name,toggle,rows.filterNot { (it.node as? SettingNode.Leaf)?.prop===prop })
    }

/** Expansion is view state only; toggling a module never changes whether its settings are open. */
object FloydModuleExpansion {
    private val expanded=mutableStateMapOf<String,Boolean>()
    private val diagnostics=linkedMapOf<String,Map<String,Any?>>()
    private var animationFrames=0
    internal fun isExpanded(key:String,search:Boolean)=expanded[key] ?: search
    internal fun toggle(key:String,search:Boolean) { expanded[key]=!isExpanded(key,search) }
    internal fun record(key:String,value:Map<String,Any?>) {
        diagnostics[key]?.let { if(it["rotation"]!=value["rotation"])animationFrames++ }
        diagnostics[key]=value
    }
    internal fun remove(key:String) { diagnostics.remove(key) }
    fun state():Map<String,Any?> = mapOf("collapsedByDefault" to true,"animationMillis" to 220,"animationFrames" to animationFrames,"visible" to diagnostics.toMap())
}

@Composable internal fun FloydModuleSection(section:FloydModuleSection,search:Boolean,page:String) {
    val theme=LocalTheme.current
    if(section.toggle==null) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(section.name,color=theme.textColor,fontSize=20.sp)
            section.settings.forEach { ConfigListRow(it) }
        }
        return
    }
    val key="${if(search)"search:$page" else "category:$page"}:${section.name}"
    val expanded=FloydModuleExpansion.isExpanded(key,search)
    val rotation by animateFloatAsState(if(expanded)180f else 0f,tween(220))
    var bounds by remember(key) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val arrowBounds=bounds
    DisposableEffect(key) { onDispose { FloydModuleExpansion.remove(key) } }
    SideEffect { FloydModuleExpansion.record(key,mapOf("name" to section.name,"expanded" to expanded,"rotation" to rotation,
        "settingsCount" to section.settings.size,"arrowX" to arrowBounds.center.x,"arrowY" to arrowBounds.center.y,
        "arrowWidth" to arrowBounds.width,"arrowBackground" to false,"fullWidthCard" to true)) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(section.name,color=theme.textColor,fontSize=20.sp)
        CompositionLocalProvider(LocalModuleExpansionControl provides ModuleExpansionControl(key,search,rotation) { bounds=it }) {
            ConfigListRow(section.toggle)
        }
        AnimatedVisibility(expanded,enter=expandVertically(tween(220))+fadeIn(tween(150)),exit=shrinkVertically(tween(220))+fadeOut(tween(120))) {
            Column(Modifier.fillMaxWidth().padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                section.settings.forEach { ConfigListRow(it) }
            }
        }
    }
}
