package com.odtheking.odin.features.impl.render

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FloydXrayTest {
    @Test
    fun `user xray opaque block ids are validated like Floyd command identifiers`() {
        assertEquals("minecraft:glass", FloydXrayIds.validOpaqueBlockId("minecraft:glass"))
        assertEquals("minecraft:glass", FloydXrayIds.validOpaqueBlockId("Minecraft:Glass"))
        assertEquals("minecraft:glass", FloydXrayIds.validOpaqueBlockId("  minecraft:glass  "))

        assertFailsWith<IllegalArgumentException> {
            FloydXrayIds.validOpaqueBlockId("not a block id")
        }
        assertFailsWith<IllegalArgumentException> {
            FloydXrayIds.validOpaqueBlockId("bad:id:extra")
        }
        assertFailsWith<IllegalArgumentException> {
            FloydXrayIds.validOpaqueBlockId("   ")
        }
    }

    @Test
    fun `invalid user xray opaque block removal ids fail before mutation`() {
        assertFailsWith<IllegalArgumentException> {
            FloydXrayIds.validOpaqueBlockId("not a block id")
        }
        assertFailsWith<IllegalArgumentException> {
            FloydXrayIds.validOpaqueBlockId("bad:id:extra")
        }
    }

    @Test
    fun `xray opaque list summary uses Floyd count header`() {
        val summary = FloydXray.opaqueBlockListSummary()

        assertTrue(summary.startsWith("--- Xray Opaque Blocks ("))
        assertTrue(summary.contains("minecraft:glass"))
    }

    @Test
    fun `xray opaque block ids preserve Floyd insertion order`() {
        val blocks = linkedMapOf(
            "minecraft:zombie_head" to true,
            "minecraft:glass" to true,
            "minecraft:acacia_leaves" to true
        )

        assertEquals(
            listOf("minecraft:zombie_head", "minecraft:glass", "minecraft:acacia_leaves"),
            FloydXrayOpaqueLists.activeIds(blocks).toList()
        )
        assertEquals(
            "--- Xray Opaque Blocks (3) ---\nminecraft:zombie_head\nminecraft:glass\nminecraft:acacia_leaves",
            FloydXrayOpaqueLists.summary(FloydXrayOpaqueLists.activeIds(blocks))
        )
        assertEquals(
            listOf("minecraft:glass", "minecraft:tinted_glass", "minecraft:glass_pane"),
            FloydXrayOpaqueLists.defaultIds().take(3)
        )
    }

    @Test
    fun `xray opaque block feedback uses Floyd command wording`() {
        assertEquals("Added xray opaque block: minecraft:glass", FloydXray.addOpaqueBlockMessage("minecraft:glass", true))
        assertEquals("Block already in opaque list: minecraft:glass", FloydXray.addOpaqueBlockMessage("minecraft:glass", false))
        assertEquals("Removed xray opaque block: minecraft:glass", FloydXray.removeOpaqueBlockMessage("minecraft:glass", true))
        assertEquals("Block not in opaque list: minecraft:glass", FloydXray.removeOpaqueBlockMessage("minecraft:glass", false))
    }

    @Test
    fun `xray alpha conversion preserves Floyd integer truncation`() {
        assertEquals(12, FloydXrayAlpha.alpha(0.05f))
        assertEquals(76, FloydXrayAlpha.alpha(0.3f))
        assertEquals(255, FloydXrayAlpha.alpha(1.0f))
    }

    @Test
    fun `xray editor actions save valid clicks unconditionally like Floyd GUI`() {
        val root = Path.of("").toAbsolutePath()
        val active = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydXray.kt")).replace("\r\n", "\n")
        val floydEditor = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/ClickGuiScreen.java")).replace("\r\n", "\n")
        val floydScreen = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/XrayEditorScreen.java")).replace("\r\n", "\n")

        assertTrue(floydEditor.contains("RenderConfig.addXrayOpaqueBlock(query);\n            FloydAddonsConfig.saveXrayOpaque();"))
        assertTrue(floydEditor.contains("case ADD_BLOCK -> { RenderConfig.addXrayOpaqueBlock(entry.key); FloydAddonsConfig.saveXrayOpaque();"))
        assertTrue(floydEditor.contains("case REMOVE_BLOCK -> { RenderConfig.removeXrayOpaqueBlock(entry.key); FloydAddonsConfig.saveXrayOpaque();"))
        assertTrue(floydScreen.contains("RenderConfig.addXrayOpaqueBlock(entry.blockId);\n                        FloydAddonsConfig.saveXrayOpaque();"))
        assertTrue(floydScreen.contains("RenderConfig.removeXrayOpaqueBlock(entry.blockId);\n                        FloydAddonsConfig.saveXrayOpaque();"))
        assertTrue(active.contains("val added = addOpaqueBlock(blockId)\n        ModuleManager.saveConfigurations()\n        modMessage(addOpaqueBlockMessage(blockId, added))"))
        assertTrue(active.contains("val removed = removeOpaqueBlock(blockId)\n        ModuleManager.saveConfigurations()\n        modMessage(removeOpaqueBlockMessage(blockId, removed))"))
    }
}
