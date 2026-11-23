@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package gg.floyd.clickgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.floyd.features.impl.render.FloydFont
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

class FloydFontPickerVisualizer:Visualizer {
    companion object {
        private var diagnostics=emptyMap<String,Any?>()
        private val rows=linkedMapOf<String,Map<String,Any?>>()
        private var button=emptyMap<String,Any?>()
        fun state():Map<String,Any?> = diagnostics+mapOf("button" to button,"rows" to rows.toMap())
    }
    @Composable override fun visualize(prop:Property<*>) {
        val theme=LocalTheme.current
        var expanded by remember(prop) { mutableStateOf(false) }
        var query by remember(prop) { mutableStateOf("") }
        var revision by remember(prop) { mutableIntStateOf(0) }
        val choices=remember(expanded,revision) { FloydFont.fontChoices() }
        val filtered=choices.filter { query.isBlank() || (it.label+" "+it.detail).contains(query,true) }
        DisposableEffect(prop) { onDispose { diagnostics=emptyMap();rows.clear();button=emptyMap() } }
        SideEffect { diagnostics=mapOf("expanded" to expanded,"query" to query,"selected" to FloydFont.selectedFontChoice(),"selectedLabel" to FloydFont.selectedFontLabel(),"oringoPresetCount" to choices.count { it.id.startsWith("oringo:") }) }
        Column(Modifier.width(LocalOptionWidth.current),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp).clip(theme.sideBarNavigationEntryShape)
                .background(theme.componentBackground).border(1.dp,theme.borderColor,theme.sideBarNavigationEntryShape)
                .onGloballyPositioned { val r=it.boundsInWindow();button=mapOf("x" to r.center.x,"y" to r.center.y) }
                .clickable { expanded=!expanded;query="" }.padding(horizontal=10.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(FloydFont.selectedFontLabel(),color=theme.textColor,fontSize=13.sp)
                Text(if(expanded)"▴" else "▾",color=theme.textColorSecondary,fontSize=14.sp)
            }
            if(expanded) {
                Column(Modifier.fillMaxWidth().clip(theme.sideBarNavigationEntryShape).background(theme.popupBackground)
                    .border(1.dp,theme.borderColor,theme.sideBarNavigationEntryShape).padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    BasicTextField(query,{query=it},singleLine=true,textStyle=TextStyle(color=theme.textColor,fontSize=13.sp,fontFamily=theme.typography.family),cursorBrush=SolidColor(theme.textColor),
                        modifier=Modifier.fillMaxWidth().height(32.dp).trackTextInputFocus()
                            .onGloballyPositioned { val r=it.boundsInWindow();button=button+mapOf("searchX" to r.center.x,"searchY" to r.center.y) }
                            .border(1.dp,theme.borderColor,theme.sideBarNavigationEntryShape).padding(8.dp),
                        decorationBox={ input -> Box { if(query.isEmpty())Text("Search presets or custom fonts…",color=theme.textColorSecondary,fontSize=12.sp);input() } })
                    if(filtered.isEmpty())Text("No matching fonts",color=theme.textColorSecondary,fontSize=12.sp)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max=230.dp)) {
                        items(filtered,key={it.id}) { choice ->
                            DisposableEffect(choice.id) { onDispose { rows.remove(choice.id) } }
                            Column(Modifier.fillMaxWidth().clip(theme.sideBarNavigationEntryShape)
                                .background(if(choice.id==FloydFont.selectedFontChoice())theme.chipBackground else androidx.compose.ui.graphics.Color.Transparent)
                                .onGloballyPositioned { val r=it.boundsInWindow();rows[choice.id]=mapOf("x" to r.center.x,"y" to r.center.y,"label" to choice.label,"available" to choice.available) }
                                .clickable(enabled=choice.available) { if(FloydFont.selectFont(choice.id)) { revision++;expanded=false;query="" } }
                                .padding(8.dp)) {
                                Text(choice.label,color=if(choice.available)theme.textColor else theme.textColorSecondary,fontSize=13.sp)
                                Text(if(choice.available)choice.detail else "Install Tahoma or add its .ttf to your fonts folder",color=theme.textColorSecondary,fontSize=11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
