@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package gg.floyd.clickgui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gg.floyd.clickgui.settings.impl.ActionSetting
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.internal.ui.api.settings.TextOptionData
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth
import org.polyfrost.oneconfig.internal.ui.components.settings.TextOption
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

/** Keep the native editable filename and its cycling controls together. */
class FloydFileSelectorVisualizer(private val previous: ActionSetting, private val next: ActionSetting) : Visualizer {
    @Composable
    override fun visualize(prop: Property<*>) {
        var revision by remember(prop) { mutableIntStateOf(0) }
        Row(Modifier.width(LocalOptionWidth.current), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Arrow(false, previous.name) { previous.action(); revision++ }
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalOptionWidth provides (LocalOptionWidth.current - 72.dp)) {
                    // Native text controls remember their value; recreate after a cycle.
                    key(revision) { TextOption(TextOptionData(prop)) }
                }
            }
            Arrow(true, next.name) { next.action(); revision++ }
        }
    }

    @Composable
    private fun Arrow(forward: Boolean, label: String, action: () -> Unit) {
        val theme = LocalTheme.current
        Box(Modifier.size(32.dp).clip(theme.sideBarNavigationEntryShape)
            .background(theme.chipBackground).semantics { contentDescription = label }
            .clickable(onClick = action), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(14.dp)) {
                val from = if (forward) .35f else .65f
                val to = 1f - from
                drawLine(theme.textColor, Offset(size.width * from, size.height * .2f),
                    Offset(size.width * to, size.height * .5f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(theme.textColor, Offset(size.width * to, size.height * .5f),
                    Offset(size.width * from, size.height * .8f), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
