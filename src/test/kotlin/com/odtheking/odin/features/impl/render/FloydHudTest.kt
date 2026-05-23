package com.odtheking.odin.features.impl.render

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloydHudTest {
    @Test
    fun `custom scoreboard requires vanilla sidebar signal`() {
        FloydHud.resetVanillaScoreboardWouldRender()

        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )

        FloydHud.markVanillaScoreboardWouldRender()
        assertTrue(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
    }

    @Test
    fun `scoreboard gate still blocks disabled custom scoreboard or missing objective`() {
        FloydHud.resetVanillaScoreboardWouldRender()
        FloydHud.markVanillaScoreboardWouldRender()

        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = false,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = true,
                moduleEnabled = true,
                hudEnabled = true
            )
        )

        FloydHud.markVanillaScoreboardWouldRender()
        assertFalse(
            FloydHud.shouldDrawScoreboardHud(
                example = false,
                customScoreboard = true,
                objectivePresent = false,
                moduleEnabled = true,
                hudEnabled = true
            )
        )
    }

    @Test
    fun `inventory HUD stack counts use Floyd centered slot placement`() {
        val root = Path.of("").toAbsolutePath()
        val floyd = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/hud/InventoryHudRenderer.java"))
        val active = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydHud.kt"))

        assertTrue(floyd.contains("return Math.max(12, Math.round(BASE_SLOT * RenderConfig.getInventoryHudScale()));"))
        assertTrue(active.contains("val slotSize = (18 * inventoryHudScale).roundToInt().coerceAtLeast(12)"))
        assertTrue(floyd.contains("int tx = (int) (sx + (slotSize - tr.getWidth(count)) / 2f + 1);"))
        assertTrue(floyd.contains("int ty = (int) (sy + slotSize - tr.fontHeight - 3);"))
        assertTrue(active.contains("val tx = (x + (slotSize - mc.font.width(count)) / 2f + 1).toInt()"))
        assertTrue(active.contains("val ty = (y + slotSize - mc.font.lineHeight - 3).toInt()"))
        assertFalse(active.contains("slotSize - mc.font.width(count) - 1"))
        assertFalse(active.contains("val slotSize = (18 * inventoryHudScale).toInt()"))
    }

    @Test
    fun `scoreboard HUD renderer preserves Floyd sidebar ordering gate and footer`() {
        val root = Path.of("").toAbsolutePath()
        val floyd = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/features/hud/ScoreboardHudRenderer.java"))
        val active = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydHud.kt"))

        assertTrue(floyd.contains("public final class ScoreboardHudRenderer"))
        assertTrue(floyd.contains("Comparator.comparing(ScoreboardEntry::value).reversed()"))
        assertTrue(floyd.contains("if (!vanillaWouldRender) return;"))
        assertTrue(floyd.contains("ScoreboardDisplaySlot.fromFormatting(team.getColor())"))
        assertTrue(floyd.contains("if (lines.size() > 1)"))
        assertTrue(floyd.contains("String footerText = \"FloydAddons\""))

        assertTrue(active.contains("sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() })"))
        assertTrue(active.contains("return if (consumeVanillaSignal) vanillaScoreboardWouldRender.getAndSet(false) else vanillaScoreboardWouldRender.get()"))
        assertTrue(active.contains("DisplaySlot::teamColorToSlot"))
        assertTrue(active.contains("if (lines.size > 1) lines.removeAt(lines.lastIndex)"))
        assertTrue(active.contains("Component.literal(\"FloydAddons\").visualOrderText"))
    }
}
