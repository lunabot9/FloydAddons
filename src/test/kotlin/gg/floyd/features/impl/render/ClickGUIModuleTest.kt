package gg.floyd.features.impl.render

import gg.floyd.clickgui.Panel
import gg.floyd.utils.Color
import gg.floyd.utils.Color.Companion.brighter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClickGUIModuleTest {
    @Test
    fun `hover brightens the displayed accent instead of the configured base hue`() {
        val displayedPurple = 0xFF9C27B0.toInt()

        assertEquals(Color(displayedPurple).brighter().rgba, ClickGUIModule.hoveredAccentColor(displayedPurple))
    }

    @Test
    fun `bootstrap gui scale stays stable before the Minecraft window exists`() {
        assertEquals(1f, ClickGUIModule.standardGuiScaleFor(1920f, 1080f, 1f))
        assertEquals(1920f, ClickGUIModule.availableWidthForLayout(1920f, 1080f, 1f))
    }

    @Test
    fun `layout width respects the capped gui scale on larger windows`() {
        assertEquals(1.5f, ClickGUIModule.standardGuiScaleFor(3840f, 2160f, 1f))
        assertEquals(2560f, ClickGUIModule.availableWidthForLayout(3840f, 2160f, 1f))
    }

    @Test
    fun `short windows scale the click gui down to keep its content visible`() {
        assertEquals(1f, ClickGUIModule.standardGuiScaleFor(1920f, 600f, 1f))
        assertEquals(1920f, ClickGUIModule.availableWidthForLayout(1920f, 600f, 1f), 0.001f)

        assertEquals(1f, ClickGUIModule.standardGuiScaleFor(854f, 480f, 1f))
        assertEquals(854f, ClickGUIModule.availableWidthForLayout(854f, 480f, 1f))
    }

    @Test
    fun `retina logical points are not double-penalized`() {
        assertEquals(1f, ClickGUIModule.standardGuiScaleFor(1512f, 982f, 2f))
        assertEquals(1512f, ClickGUIModule.availableWidthForLayout(1512f, 982f, 2f))
    }

    @Test
    fun `screenshot layout is centered inside narrow virtual widths`() {
        val centered = ClickGUIModule.centeredPanelLayout(
            linkedMapOf(
                "render" to ClickGUIModule.PanelData(x = 6f, y = 31f, extended = true),
                "pvp" to ClickGUIModule.PanelData(x = 1356f, y = 31f, extended = true),
            ),
            availableWidth = 1708f,
        )

        val minX = centered.minOf { it.value.x }
        val maxRight = centered.maxOf { it.value.x + Panel.WIDTH }
        val leftMargin = minX
        val rightMargin = 1708f - maxRight

        assertEquals(leftMargin, rightMargin, 0.001f)
        assertEquals(120f, leftMargin)
    }

    @Test
    fun `stale wrapped rows reset once the compact layout fits one row`() {
        assertTrue(
            ClickGUIModule.shouldResetStaleWrappedRows(
                currentRows = listOf(10f, 10f, 10f, 332f, 332f, 332f),
                compactRows = List(6) { 10f },
                usesLegacyWrappedColumns = true,
            )
        )
    }

    @Test
    fun `wrapped rows remain when the compact layout also needs wrapping`() {
        assertFalse(
            ClickGUIModule.shouldResetStaleWrappedRows(
                currentRows = listOf(10f, 10f, 332f, 332f),
                compactRows = listOf(10f, 10f, 332f, 332f),
                usesLegacyWrappedColumns = true,
            )
        )
    }

    @Test
    fun `narrow wrapped defaults collapse panels and keep both rows above the footer`() {
        val layout = ClickGUIModule.wrappedPanelLayoutData(
            panels = listOf(
                "Render" to 390f,
                "Hiders" to 280f,
                "Camera" to 120f,
                "Cosmetic" to 240f,
                "QoL" to 180f,
                "Misc" to 260f,
            ),
            availableWidth = 427f,
        )

        assertEquals(listOf(10f, 10f, 10f, 44f, 44f, 44f), layout.values.map { it.y })
        assertTrue(layout.values.none { it.extended })
    }

    @Test
    fun `resetting panel positions preserves collapsed narrow layout state`() {
        val target = linkedMapOf(
            "Render" to ClickGUIModule.PanelData(x = 638f, y = 10f, extended = true),
        )
        val layout = linkedMapOf(
            "Render" to ClickGUIModule.PanelData(x = 8f, y = 10f, extended = false),
            "Misc" to ClickGUIModule.PanelData(x = 8f, y = 44f, extended = false),
        )

        ClickGUIModule.applyPanelLayout(target, layout)

        assertEquals(layout, target)
    }

    @Test
    fun `legacy expanded wrapped fallback resets to collapsed defaults`() {
        assertTrue(
            ClickGUIModule.shouldResetExpandedWrappedFallback(
                usesWrappedFallback = true,
                currentExpanded = List(6) { true },
                expectedExpanded = List(6) { false },
            )
        )
        assertFalse(
            ClickGUIModule.shouldResetExpandedWrappedFallback(
                usesWrappedFallback = true,
                currentExpanded = List(6) { false },
                expectedExpanded = List(6) { false },
            )
        )
        assertFalse(
            ClickGUIModule.shouldResetExpandedWrappedFallback(
                usesWrappedFallback = true,
                currentExpanded = listOf(true, false, false, false, false, false),
                expectedExpanded = List(6) { false },
            )
        )
    }
}
