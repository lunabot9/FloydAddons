@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package gg.floyd.clickgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gg.floyd.features.impl.render.FloydUiTheme
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import kotlin.math.roundToInt

/** Theme editing has its own destination; the hidden module only retains saved settings. */
@Composable
fun FloydThemeEditor() {
    val theme = LocalTheme.current
    // Observe the palette snapshot, including changes made by a color picker or bridge action.
    val snapshot = FloydOneConfigTheme.theme
    val (selectedPreset, custom) = FloydOneConfigTheme.editorSelection
    Row(Modifier.fillMaxSize().padding(start = 26.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(286.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Presets", color = theme.textColor, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("A complete look in one click.", color = theme.textColorSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            val presetIndex = FloydUiPalette.presets.keys.indexOf(selectedPreset).coerceAtLeast(0)
            val density = LocalDensity.current
            val cardBounds = remember { mutableStateMapOf<Int, Pair<Float, Int>>() }
            var cardsRootY by remember { mutableStateOf<Float?>(null) }
            val selectedBounds = cardBounds[presetIndex]
            // Layout rounds row heights and gaps separately at fractional scales. Measure the
            // actual rows so their accumulated rounding cannot displace the moving outline.
            val targetY = if (selectedBounds != null && cardsRootY != null) {
                (selectedBounds.first - cardsRootY!!).roundToInt()
            } else with(density) { (presetIndex * 75).dp.roundToPx() }
            val selectedHeight = selectedBounds?.second ?: with(density) { 67.dp.roundToPx() }
            val selectionY by animateFloatAsState(targetY.toFloat(),
                tween(220, easing = FastOutSlowInEasing), label = "preset selection")
            val selectionAlpha by animateFloatAsState(if (custom) 0f else 1f, tween(160), label = "preset selection visibility")
            Box(Modifier.fillMaxWidth().height(442.dp).onGloballyPositioned { coordinates ->
                cardsRootY = coordinates.positionInRoot().y
            }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FloydUiPalette.presets.entries.forEachIndexed { index, (name, palette) ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val shape = RoundedCornerShape(12.dp)
                Row(Modifier.fillMaxWidth().height(67.dp)
                    .onGloballyPositioned { coordinates ->
                        cardBounds[index] = coordinates.positionInRoot().y to coordinates.size.height
                    }.clip(shape)
                    .background(if (hovered) theme.chipBackground else theme.modCardBackground)
                    .border(1.dp, theme.borderColor, shape)
                    .hoverable(interaction).clickable(interactionSource = interaction, indication = null) {
                        FloydUiTheme.selectPreset(index)
                        FloydOneConfigTheme.sync()
                        FloydOneConfig.refreshSettings()
                    }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Small palette previews retain their own colors even while the menu changes.
                    Box(Modifier.width(38.dp).height(38.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(palette["Page Background"]))
                        .border(1.dp, Color(palette["Border"]), RoundedCornerShape(8.dp))) {
                        Box(Modifier.width(10.dp).fillMaxHeight().background(Color(palette["Sidebar Background"])))
                        Box(Modifier.width(20.dp).height(6.dp).align(Alignment.CenterEnd).padding(end = 3.dp)
                            .clip(RoundedCornerShape(2.dp)).background(Color(palette["Accent"])))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(name, color = theme.textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Accent", "Border", "Secondary Text", "Primary Text").forEach { role ->
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(palette[role])))
                            }
                        }
                    }
                }
            }
            }
                // A single overlay outline glides over the cards rather than swapping borders.
                Box(Modifier.fillMaxWidth().offset {
                    FloydSettingsNavigation.recordPresetHighlightPosition(selectionY / density.density)
                    IntOffset(0, selectionY.roundToInt())
                }
                    .height(with(density) { selectedHeight.toDp() }).alpha(selectionAlpha)
                    .border(2.dp, FloydOneConfigTheme.accent, RoundedCornerShape(12.dp))) {
                    Text("✓", color = FloydOneConfigTheme.accent, fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(if (custom) "Your custom palette is active." else "$selectedPreset is active.",
                color = theme.textColorSecondary, fontSize = 12.sp)
            Text("Custom colors stay saved when trying presets.", color = theme.textColorSecondary, fontSize = 11.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.padding(start = 26.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Make it yours", color = snapshot.textColor, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("Enable Custom Colors, or customize a preset to get started.", color = theme.textColorSecondary, fontSize = 12.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                FloydSettingsList(FloydOneConfig.themeSettingsTree(), "theme-editor", search = false)
            }
        }
    }
}
