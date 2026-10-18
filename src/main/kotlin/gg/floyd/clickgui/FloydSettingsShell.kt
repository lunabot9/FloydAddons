@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import org.polyfrost.oneconfig.internal.ui.search.ConfigListEntry
import org.polyfrost.oneconfig.internal.ui.search.SettingNode
import org.polyfrost.oneconfig.internal.ui.search.flattenEntries
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import java.util.function.Consumer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.floyd.Branding
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.IconButton
import org.polyfrost.oneconfig.internal.ui.LocalCloseRequest
import org.polyfrost.oneconfig.internal.ui.screens.ConfigListRow
import org.polyfrost.oneconfig.internal.ui.search.buildCategories
import org.polyfrost.oneconfig.internal.ui.search.flattenSearchEntries
import org.polyfrost.oneconfig.internal.ui.search.filterHiddenNodes
import gg.floyd.clickgui.FloydOneConfigTheme.accent as Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

object FloydSettingsNavigation {
    val categories = listOf("Render", "Hiders", "Camera", "Cosmetic", "QoL", "Misc", "Agent")
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf("Render")
        private set
    var themeEditorOpen by mutableStateOf(false)
        private set
    fun openThemeEditor() { themeEditorOpen = true; searchQuery = "" }
    fun closeThemeEditor() { themeEditorOpen = false; searchQuery = "" }
    var highlightOffsetDp = 0f
        private set
    var warmupScrollIndex by mutableIntStateOf(0)
    var firstVisibleRow = 0
        private set
    var scrollOffset = 0
        private set
    fun recordScrollPosition(row: Int, offset: Int) { firstVisibleRow = row; scrollOffset = offset }
    var highlightAnimationFrames = 0
        private set
    var highlightTargetDp = 0f
    var themeButtonOffsetDp = 0f
    var presetHighlightOffsetDp = 0f
        private set
    var presetHighlightAnimationFrames = 0
        private set
    fun recordPresetHighlightPosition(value: Float) {
        if (value != presetHighlightOffsetDp) presetHighlightAnimationFrames++
        presetHighlightOffsetDp = value
    }

    fun recordHighlightPosition(value: Float) {
        if (value != highlightOffsetDp) highlightAnimationFrames++
        highlightOffsetDp = value
    }

    fun select(category: String) {
        require(category in categories)
        selectedCategory = category
        themeEditorOpen = false
        FloydSettingsNavigation.searchQuery = ""
    }
}

@Composable
fun FloydSettingsHeader() {
    val close = LocalCloseRequest.current
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        if (FloydSettingsNavigation.themeEditorOpen) {
            Text("‹ Back", color = LocalTheme.current.textColorSecondary, fontSize = 14.sp,
                modifier = Modifier.clip(LocalTheme.current.buttonShape).clickable { FloydSettingsNavigation.closeThemeEditor() }
                    .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 8.dp, vertical = 6.dp))
        }
        Text(if (FloydSettingsNavigation.themeEditorOpen) "Theme" else if (FloydSettingsNavigation.searchQuery.isBlank()) FloydSettingsNavigation.selectedCategory else "Search results", color = LocalTheme.current.textColor,
            fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (!FloydSettingsNavigation.themeEditorOpen) FloydSettingsSearchField()
        IconButton("close") { close() }
    }
}

/** Floyd owns navigation; the settings remain the native controls backed by Floyd's config. */
@Composable
fun FloydSidebar() {
    val theme = LocalTheme.current
    val divider = FloydOneConfigTheme.divider
    val density = LocalDensity.current
    var categoryTop by remember { mutableStateOf(0.dp) }
    var themeTop by remember { mutableStateOf(0.dp) }
    val target = if (FloydSettingsNavigation.themeEditorOpen) themeTop
        else categoryTop + (50 * FloydSettingsNavigation.categories.indexOf(FloydSettingsNavigation.selectedCategory)).dp
    val selectionOffset by animateDpAsState(targetValue = target,
        animationSpec = tween(220, easing = FastOutSlowInEasing), label = "sidebar selection")
    SideEffect {
        FloydSettingsNavigation.recordHighlightPosition(selectionOffset.value)
        FloydSettingsNavigation.highlightTargetDp = target.value
        FloydSettingsNavigation.themeButtonOffsetDp = themeTop.value
    }
    Box(
        Modifier.fillMaxHeight().width(264.dp)
            .background(theme.sidebarBackground)
            .drawWithContent {
                drawContent()
                drawLine(divider,
                    Offset(size.width - 0.5.dp.toPx(), 0f),
                    Offset(size.width - 0.5.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        // One persistent highlight travels across all destinations, including the footer button.
        Box(Modifier.fillMaxWidth().offset(y = selectionOffset).height(42.dp)
            .background(Accent, theme.sideBarNavigationEntryShape)
            .border(1.dp, theme.borderColor, theme.sideBarNavigationEntryShape))
        Column(Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Branding.DISPLAY_NAME.substringBefore(' ').uppercase(), color = FloydOneConfigTheme.wordmark, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("A D D O N S", color = theme.textColorSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, theme.borderColor, Color.Transparent))))
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height((FloydSettingsNavigation.categories.size * 50 - 8).dp).onGloballyPositioned {
            categoryTop = with(density) { it.positionInParent().y.toDp() }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FloydSettingsNavigation.categories.forEach { category ->
            val selected = !FloydSettingsNavigation.themeEditorOpen && FloydSettingsNavigation.selectedCategory == category
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
        val hudInteraction = remember { MutableInteractionSource() }
        val hudHovered by hudInteraction.collectIsHoveredAsState()
        Row(Modifier.fillMaxWidth().height(42.dp).clip(theme.sideBarNavigationEntryShape)
            .background(if (hudHovered) theme.chipBackground else Color.Transparent)
            .hoverable(hudInteraction)
            .clickable(interactionSource = hudInteraction, indication = null) {
                gg.floyd.FloydAddonsMod.mc.setScreen(HudManager)
            }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Edit HUD", color = theme.textColor, fontSize = 14.sp)
        }
        val editorSelected = FloydSettingsNavigation.themeEditorOpen
        val editorInteraction = remember { MutableInteractionSource() }
        val editorHovered by editorInteraction.collectIsHoveredAsState()
        Row(Modifier.fillMaxWidth().height(42.dp).clip(theme.sideBarNavigationEntryShape)
            .onGloballyPositioned { themeTop = with(density) { it.positionInParent().y.toDp() } }
            .background(if (editorHovered && !editorSelected) theme.chipBackground else Color.Transparent)
            .hoverable(editorInteraction).clickable(interactionSource = editorInteraction, indication = null) { FloydSettingsNavigation.openThemeEditor() }
            .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(15.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (editorSelected) theme.accentTextColor else Accent))
            Text("Theme", color = if (editorSelected) theme.accentTextColor else theme.textColor, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text("${Branding.DISPLAY_NAME} ${Branding.VERSION}", color = theme.textColorSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun FloydSettingsPanel() {
    if (FloydSettingsNavigation.themeEditorOpen) { FloydThemeEditor(); return }
    val category = FloydSettingsNavigation.selectedCategory
    val query = FloydSettingsNavigation.searchQuery
    if (query.isBlank()) {
        FloydSettingsList(FloydOneConfig.categoryTree(category), category, search = false)
    } else {
        key(query) { FloydSettingsList(FloydOneConfig.searchTree(query), query, search = true) }
    }
}

/** Prepare nearby rows before the wheel reveals them, and reuse controls by type. */
@Composable
internal fun FloydSettingsList(tree: Tree, page: String, search: Boolean) {
    val categories = remember(tree) { buildCategories(tree) }
    var displayRevision by remember(tree) { mutableIntStateOf(0) }
    DisposableEffect(tree) {
        val props = tree.map.values.filterIsInstance<Property<*>>()
        val listener = Consumer<Property.Display> { displayRevision++ }
        props.forEach { it.addDisplayListener(listener) }
        onDispose { props.forEach { it.removeDisplayListener(listener) } }
    }
    val entries = remember(categories, displayRevision) {
        val visible = categories.mapNotNull { filterHiddenNodes(it) }
        if (search) flattenSearchEntries(visible) else visible.flatMap { flattenEntries(it) }
    }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No settings match \"$page\"", color = LocalTheme.current.textColorSecondary)
        }
        return
    }
    key(page) {
        val list = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(ahead = 1200.dp, behind = 600.dp))
        if (FloydOneConfig.shouldWarmSettings()) {
            val warmupIndex = FloydSettingsNavigation.warmupScrollIndex
            LaunchedEffect(list, warmupIndex) { list.scrollToItem(warmupIndex.coerceAtMost(entries.lastIndex)) }
        }
        LaunchedEffect(list) {
            snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect {
                FloydSettingsNavigation.recordScrollPosition(it.first, it.second)
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 26.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { entry -> when (entry) {
                    is ConfigListEntry.CategoryHeader -> "category:${entry.title}"
                    is ConfigListEntry.SubcategoryHeader -> "section:${entry.title}"
                    is ConfigListEntry.Item -> "setting:${System.identityHashCode(entry.node.let { node -> if (node is SettingNode.Leaf) node.prop else (node as SettingNode.Accordion).tree })}"
                } }, contentType = { entry -> when (entry) {
                    is ConfigListEntry.Item -> (entry.node as? SettingNode.Leaf)?.prop?.getMetadata<Any>("visualizer") ?: "accordion"
                    else -> entry.javaClass
                } }) { ConfigListRow(it) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 12.dp))
        }
    }
}
