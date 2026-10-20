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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.floyd.clickgui.settings.impl.SearchableListSetting
import gg.floyd.features.ModuleManager
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

/** Registry keys stay authoritative; friendly labels and Minecraft models are only the view. */
class FloydSearchableListVisualizer(private val setting: SearchableListSetting) : Visualizer {
    companion object {
        private val diagnostics = linkedMapOf<String, Map<String, Any?>>()
        fun state(): Map<String, Any?> = diagnostics.toMap()
    }
    @Composable override fun visualize(prop: Property<*>) {
        val theme = LocalTheme.current
        var expanded by remember(prop) { mutableStateOf(false) }
        var query by remember(prop) { mutableStateOf("") }
        var revision by remember(prop) { mutableIntStateOf(0) }
        val selected = remember(prop, revision) { setting.selectedOptions() }
        val rows = remember(prop, query, revision, expanded) { if (expanded) setting.searchOptions(query) else emptyList() }
        SideEffect { diagnostics[setting.name] = mapOf("expanded" to expanded, "query" to query, "matches" to rows.size, "firstMatches" to rows.take(8), "selected" to selected.toList()) }
        Column(Modifier.width(LocalOptionWidth.current), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp).clip(theme.sideBarNavigationEntryShape)
                .background(theme.chipBackground).border(1.dp, theme.borderColor, theme.sideBarNavigationEntryShape)
                .clickable { expanded = !expanded }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (selected.isEmpty()) "None selected" else "${selected.size} selected", color = theme.textColor, fontSize = 13.sp)
                Text(if (expanded) "▴" else "▾", color = theme.textColorSecondary, fontSize = 14.sp)
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().clip(theme.sideBarNavigationEntryShape).background(theme.chipBackground)
                    .border(1.dp, theme.borderColor, theme.sideBarNavigationEntryShape).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicTextField(query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = theme.textColor, fontSize = 13.sp, fontFamily = theme.typography.family), cursorBrush = SolidColor(theme.textColor),
                        modifier = Modifier.fillMaxWidth().height(32.dp).trackTextInputFocus()
                            .border(1.dp, theme.borderColor, theme.sideBarNavigationEntryShape).padding(8.dp),
                        decorationBox = { input -> Box { if (query.isEmpty()) Text("Search name or minecraft:id…", color = theme.textColorSecondary, fontSize = 12.sp); input() } })
                    if (rows.isEmpty()) Text("No matches", color = theme.textColorSecondary, fontSize = 12.sp)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(rows, key = { it }) { id ->
                            Row(Modifier.fillMaxWidth().height(48.dp).clickable {
                                setting.replaceSelection(if (id in selected) selected - id else selected + id)
                                ModuleManager.saveConfigurations(); revision++
                            }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(19.dp).border(1.dp, theme.borderColor, theme.sideBarNavigationEntryShape), contentAlignment = Alignment.Center) {
                                    if (id in selected) Text("✓", color = theme.textColor, fontSize = 13.sp)
                                }
                                FloydModelIcon(id, setting.name.contains("mob", true))
                                Column(Modifier.weight(1f)) {
                                    Text(setting.optionLabel(id), color = theme.textColor, fontSize = 13.sp)
                                    Text(id, color = theme.textColorSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class FloydConePreviewVisualizer : Visualizer {
    @Composable override fun visualize(prop: Property<*>) {
        Column(Modifier.width(LocalOptionWidth.current), horizontalAlignment = Alignment.CenterHorizontally) {
            FloydConeModelPreview()
            if (gg.floyd.FloydAddonsMod.mc.player == null)
                Text("Join a world to preview your hat", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
        }
    }
}
