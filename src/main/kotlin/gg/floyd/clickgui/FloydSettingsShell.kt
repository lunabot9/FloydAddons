package gg.floyd.clickgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.polyfrost.oneconfig.internal.ui.shell.ShellState
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.withOpacityPercent

object FloydSettingsNavigation {
    val categories = listOf("Render", "Hiders", "Camera", "Cosmetic", "QoL", "Misc")
    var selectedCategory by mutableStateOf("Render")
        private set

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
        Text(FloydSettingsNavigation.selectedCategory, color = LocalTheme.current.textColor,
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
        FloydSettingsNavigation.categories.forEach { category ->
            val selected = FloydSettingsNavigation.selectedCategory == category
            Row(
                Modifier.fillMaxWidth().height(42.dp)
                    .background(if (selected) Accent else theme.chipBackground.copy(alpha = 0f), theme.sideBarNavigationEntryShape)
                    .border(1.dp, if (selected) theme.borderColor else theme.borderColor.copy(alpha = 0f), theme.sideBarNavigationEntryShape)
                    .clickable { FloydSettingsNavigation.select(category) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(category, color = if (selected) theme.accentTextColor else theme.textColor,
                    fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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
    // A one-category view removes the stock horizontal tab strip. All property objects are
    // shared with the registered tree so resets, dependencies, editing, and saving stay intact.
    ConfigScreen(FloydOneConfig.categoryTree(category), category, "floydaddons/$category")
}
