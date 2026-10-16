@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package gg.floyd.clickgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.floyd.Branding
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.GlobalSearchBar
import org.polyfrost.oneconfig.internal.ui.components.IconButton
import org.polyfrost.oneconfig.internal.ui.LocalCloseRequest
import org.polyfrost.oneconfig.internal.ui.screens.ConfigScreen
import org.polyfrost.oneconfig.internal.ui.screens.ConfigListRow
import org.polyfrost.oneconfig.internal.ui.search.buildCategories
import org.polyfrost.oneconfig.internal.ui.search.flattenSearchEntries
import org.polyfrost.oneconfig.internal.ui.search.filterHiddenNodes
import org.polyfrost.oneconfig.internal.ui.shell.ShellState
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.withOpacityPercent

object FloydSettingsNavigation {
    val categories = listOf("Render", "Hiders", "Camera", "Cosmetic", "QoL", "Misc")
    var selectedCategory by mutableStateOf("Render")
        private set
    var highlightOffsetDp = 0f
        private set
    var highlightAnimationFrames = 0
        private set

    fun recordHighlightPosition(value: Float) {
        if (value != highlightOffsetDp) highlightAnimationFrames++
        highlightOffsetDp = value
    }

    fun select(category: String) {
        require(category in categories)
        selectedCategory = category
        ShellState.searchQuery = ""
    }
}

@Composable
fun FloydSettingsHeader() {
    val close = LocalCloseRequest.current
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Text(if (ShellState.searchQuery.isBlank()) FloydSettingsNavigation.selectedCategory else "Search results", color = LocalTheme.current.textColor,
            fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        GlobalSearchBar()
        IconButton("close") { close() }
    }
}

/** Floyd owns navigation; the settings remain the native controls backed by Floyd's config. */
@Composable
fun FloydSidebar() {
    val theme = LocalTheme.current
    Column(
        Modifier.fillMaxHeight().width(264.dp)
            .background(theme.sidebarBackground.withOpacityPercent(ShellState.sidebarOpacity))
            .border(1.dp, theme.borderColor)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Branding.DISPLAY_NAME.substringBefore(' ').uppercase(), color = Accent, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("A D D O N S", color = theme.textColorSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Accent.copy(alpha = 0.25f)))
        Spacer(Modifier.height(3.dp))
        val selectionOffset by animateDpAsState(
            targetValue = (50 * FloydSettingsNavigation.categories.indexOf(FloydSettingsNavigation.selectedCategory)).dp,
            animationSpec = tween(220, easing = FastOutSlowInEasing), label = "category highlight")
        SideEffect { FloydSettingsNavigation.recordHighlightPosition(selectionOffset.value) }
        Box(Modifier.fillMaxWidth().height(292.dp)) {
            Box(Modifier.fillMaxWidth().offset(y = selectionOffset).height(42.dp)
                .background(Accent, theme.sideBarNavigationEntryShape)
                .border(1.dp, theme.borderColor, theme.sideBarNavigationEntryShape))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FloydSettingsNavigation.categories.forEach { category ->
            val selected = FloydSettingsNavigation.selectedCategory == category
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                Modifier.fillMaxWidth().height(42.dp)
                    .clip(theme.sideBarNavigationEntryShape)
                    .background(if (hovered && !selected) theme.chipBackground else theme.chipBackground.copy(alpha = 0f))
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { FloydSettingsNavigation.select(category) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(category, modifier = Modifier.offset(y = (-2).dp),
                    color = if (selected) theme.accentTextColor else theme.textColor,
                    fontSize = 15.sp, lineHeight = 15.sp, maxLines = 1, fontWeight = FontWeight.Medium)
            }
        }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("${Branding.DISPLAY_NAME} ${Branding.VERSION}", color = theme.textColorSecondary, fontSize = 11.sp)
    }
}

@Composable
fun FloydSettingsPanel() {
    val category = FloydSettingsNavigation.selectedCategory
    SideEffect {
        ShellState.title = "Floyd Addons"
        ShellState.titleInfoForTitle = null
        ShellState.titleAuthors = null
        ShellState.titleCredits = null
        ShellState.titleVersion = Branding.VERSION
        ShellState.openOriginalScreen = null
    }
    val query = ShellState.searchQuery
    if (query.isBlank()) {
        ConfigScreen(FloydOneConfig.categoryTree(category), category, "floydaddons/$category")
    } else {
        val entries = remember(query) {
            flattenSearchEntries(buildCategories(FloydOneConfig.searchTree(query)).mapNotNull { filterHiddenNodes(it) })
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No settings match \"$query\"", color = LocalTheme.current.textColorSecondary)
            }
        } else {
            key(query) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries) { ConfigListRow(it) }
                }
            }
        }
    }
}
