package floydaddons.not.dogshit.client

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class CommandParitySourceTest {
    private val root = Path.of("").toAbsolutePath()

    @Test
    fun `active commands preserve Floyd roots and aliases`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsCommand.java")
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/commands/MainCommand.kt")
        val initializer = source("src/main/kotlin/floydaddons/not/dogshit/client/FloydAddonsMod.kt")

        assertContains(floyd, "dispatcher.register(buildCommand(\"floydaddons\"));")
        assertContains(floyd, "dispatcher.register(buildCommand(\"fa\"));")
        assertContains(floyd, "dispatcher.register(buildStalkCommand());")
        assertContains(active, "Commodore(\"floydaddons\", \"floyd\", \"fa\")")
        assertContains(active, "val stalkCommand = Commodore(\"stalk\")")
        assertContains(initializer, "mainCommand")
        assertContains(initializer, "stalkCommand")
    }

    @Test
    fun `active commands preserve Floyd name xray mob esp stalk and debug surface`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsCommand.java")
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/commands/MainCommand.kt")
        val nickHider = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/player/FloydNickHider.kt")
        val mobEsp = source("src/main/kotlin/floydaddons/not/dogshit/client/features/impl/render/FloydMobEsp.kt")

        for (literal in listOf("name", "self", "other", "remove", "list", "clear")) {
            assertContains(floyd, "literal(\"$literal\")")
            assertContains(active, "literal(\"$literal\")")
        }
        assertContains(floyd, "argument(\"fakename\", StringArgumentType.greedyString())")
        assertContains(active, "runs { fakeName: GreedyString ->")
        assertContains(floyd, "Default nick set to")
        assertContains(active, "\"Default nick set to ${'$'}{fakeName.string}\"")
        assertContains(floyd, "\\u2192")
        assertContains(active, "Added name mapping: ${'$'}ign → ${'$'}{fakeName.string}")
        assertTrue(!active.contains("Updated name mapping"), "Floyd always reports name mappings as added")
        assertContains(floyd, "if (NickHiderConfig.removeNameMapping(ign)) {\n            FloydAddonsConfig.saveNameMappings();")
        assertContains(active, "val removed = FloydNickHider.removeNameMapping(ign)\n                if (removed) ModuleManager.saveConfigurations()")
        assertContains(floyd, "\"\\u00a7e--- Name Mappings ---\"")
        assertContains(floyd, "\"\\u00a77No player mappings configured.\"")
        assertContains(active, "FloydNickHider.nameMappingsSummary()")

        assertContains(floyd, "buildXraySubcommand()")
        assertContains(active, "literal(\"xray\")")
        for (literal in listOf("add", "remove", "list", "clear")) {
            assertContains(active, "literal(\"$literal\")")
        }
        assertContains(floyd, "Identifier block = context.getArgument(\"block\", Identifier.class);")
        assertContains(active, "FloydXray.validOpaqueBlockId(block)")
        assertContains(active, "FloydXray.addOpaqueBlockMessage(blockId, added)")
        assertContains(active, "FloydXray.removeOpaqueBlockMessage(blockId, removed)")
        assertContains(floyd, "if (RenderConfig.addXrayOpaqueBlock(blockId)) {\n            FloydAddonsConfig.saveXrayOpaque();")
        assertContains(floyd, "if (RenderConfig.removeXrayOpaqueBlock(blockId)) {\n            FloydAddonsConfig.saveXrayOpaque();")
        assertContains(active, "if (added) ModuleManager.saveConfigurations()")
        assertContains(active, "if (removed) ModuleManager.saveConfigurations()")
        assertTrue(
            active.indexOf("FloydXray.validOpaqueBlockId(block)") != active.lastIndexOf("FloydXray.validOpaqueBlockId(block)"),
            "Expected both xray add and remove commands to validate block identifiers"
        )
        assertContains(active, "FloydXray.toggleXray()")
        assertTrue(
            !active.contains("val enabled = FloydXray.toggleXray()\n            ModuleManager.saveConfigurations()"),
            "Floyd xray command toggle must remain runtime-only and not persist config"
        )
        assertContains(floyd, "X-Ray \\u00a7fenabled")
        assertContains(active, "\"X-Ray enabled\"")
        assertContains(active, "nearbyBlockSuggestions().ifEmpty")
        assertContains(floyd, "\"\\u00a7e--- Xray Opaque Blocks (\"")
        assertContains(active, "FloydXray.opaqueBlockListSummary()")

        assertContains(floyd, "buildMobEspSubcommand(\"mob-esp\")")
        assertContains(floyd, "buildMobEspSubcommand(\"me\")")
        assertContains(active, "literal(\"mob-esp\")")
        assertContains(active, "literal(\"me\")")
        assertContains(floyd, "Identifier type = context.getArgument(\"type\", Identifier.class);")
        assertContains(active, "FloydMobEsp.validTypeFilterId(entityType)")
        assertTrue(
            Regex("val type = validMobEspTypeArg\\(entityType\\)").findAll(active).count() >= 3,
            "Expected Mob ESP type add, remove, and color commands to validate entity type identifiers"
        )
        assertContains(floyd, "StringArgumentType.greedyString()")
        assertContains(active, "runs { name: GreedyString ->")
        assertContains(floyd, "MobEspManager.addNameFilter(name);")
        assertContains(floyd, "MobEspManager.addTypeFilter(type.toString());")
        assertTrue(!active.contains("Name filter already exists"), "Floyd Mob ESP add-name feedback is unconditional")
        assertTrue(!active.contains("Type filter already exists"), "Floyd Mob ESP add-type feedback is unconditional")
        assertContains(floyd, "if (MobEspManager.removeNameFilter(name)) {\n            FloydAddonsConfig.saveMobEsp();")
        assertContains(floyd, "if (MobEspManager.removeTypeFilter(type.toString())) {\n            FloydAddonsConfig.saveMobEsp();")
        assertTrue(
            Regex("if \\(removed\\) ModuleManager\\.saveConfigurations\\(\\)").findAll(active).count() >= 4,
            "Expected Mob ESP remove commands to save only after a successful removal"
        )
        assertContains(active, "literal(\"reload\")")
        assertContains(active, "literal(\"debug\")")
        assertContains(active, "literal(\"mob-esp-debug\")")
        assertContains(active, "mobEspToggleMessage()")
        assertContains(active, "FloydMobEsp.toggleSummary()")
        assertTrue(
            !active.contains("FloydMobEsp.toggle()\n            ModuleManager.saveConfigurations()"),
            "Floyd Mob ESP command toggles must remain runtime-only and not persist config"
        )
        assertContains(floyd, "Mob ESP config reloaded")
        assertContains(active, "FloydMobEsp.reloadSummary()")
        assertContains(floyd, "if (mc.world == null || mc.player == null) return Command.SINGLE_SUCCESS;")
        assertContains(active, "if (mc.level == null || mc.player == null) return")
        assertContains(floyd, "--- Mob ESP Debug ---")
        assertContains(active, "FloydMobEsp.debugSummary()")
        assertContains(floyd, "\"\\u00a7e--- Mob ESP Filters ---\"")
        assertContains(floyd, "\"\\u00a77No filters configured.\"")
        assertContains(mobEsp, "\"--- Mob ESP Filters ---\"")
        assertContains(mobEsp, "\"No filters configured.\"")
        assertContains(mobEsp, "\"name: ${'$'}name\"")
        assertContains(mobEsp, "\"type: ${'$'}type\"")
        assertContains(floyd, "\"\\u00a77Star mobs: \\u00a7f\"")
        assertContains(active, "FloydMobEsp.filterListSummary()")
        assertContains(active, "FloydSidecarConfig.reloadMobEspEntries()")

        assertContains(floyd, "buildStalkSubcommand(\"stalk\")")
        assertContains(floyd, "buildStalkSubcommand(\"s\")")
        assertContains(active, "literal(\"stalk\")")
        assertContains(active, "literal(\"s\")")
        assertContains(active, "FloydMobEsp.stalk(ign)")
        assertContains(floyd, "Stalking \\u00a7f")
        assertContains(active, "\"Stalking ${'$'}ign\"")
        assertContains(floyd, "Stopped stalking \\u00a7f")
        assertContains(active, "\"Stopped stalking ${'$'}previous\"")
        assertContains(floyd, "Text.literal(\"\\u00a7cUsage: /fa stalk <ign>\")")
        assertContains(active, "\"Usage: /fa stalk <ign>\"")

        assertContains(floyd, "FloydAddonsCommand::debugInfo")
        assertContains(active, "literal(\"server-id-debug\")")
        assertContains(active, "showServerIdDebug()")
        assertContains(floyd, "--- Server ID Hider Debug ---")
        assertContains(nickHider, "--- Server ID Hider Debug ---")
        assertContains(floyd, "Accumulated IDs: ")
        assertContains(nickHider, "Accumulated IDs: (none - nothing will be hidden)")
    }

    @Test
    fun `active command suggestions preserve Floyd live suggestion sources`() {
        val floyd = source("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/FloydAddonsCommand.java")
        val active = source("src/main/kotlin/floydaddons/not/dogshit/client/commands/MainCommand.kt")

        assertContains(floyd, "suggestOnlinePlayers")
        assertContains(active, "onlinePlayerSuggestions()")
        assertContains(active, "mc.connection?.onlinePlayers")
        assertContains(floyd, "suggestNameMappingKeys")
        assertContains(floyd, "NickHiderConfig.getNameMappings().keySet()")
        assertContains(active, "FloydNickHider.mappingIds().toList()")

        assertContains(floyd, "suggestNearbyEntityNames")
        assertContains(active, "nearbyEntityNameSuggestions()")
        assertContains(active, "FloydMobEsp.cachedNpcNameFor(entity)")
        assertContains(floyd, "suggestMobEspNames")
        assertContains(floyd, "MobEspManager.getNameFilters()")
        assertContains(active, "FloydMobEsp.nameFilterIds().toList()")

        assertContains(floyd, "suggestNearbyEntityTypes")
        assertContains(active, "nearbyEntityTypeSuggestions()")
        assertContains(active, "BuiltInRegistries.ENTITY_TYPE.keySet()")
        assertContains(floyd, "suggestMobEspTypes")
        assertContains(floyd, "MobEspManager.getTypeFilters()")
        assertContains(active, "FloydMobEsp.typeFilterIds().toList()")

        assertContains(floyd, "suggestNearbyBlocks")
        assertContains(active, "nearbyBlockSuggestions()")
        assertContains(active, "BuiltInRegistries.BLOCK.keySet()")
        assertContains(floyd, "suggestXrayOpaqueBlocks")
        assertContains(floyd, "RenderConfig.getXrayOpaqueBlocks()")
        assertContains(active, "FloydXray.opaqueBlockIds().toList()")
    }

    private fun source(path: String): String = Files.readString(root.resolve(path)).replace("\r\n", "\n")

    private fun assertContains(source: String, expected: String) {
        assertTrue(source.contains(expected), "Expected source to contain: $expected")
    }
}
