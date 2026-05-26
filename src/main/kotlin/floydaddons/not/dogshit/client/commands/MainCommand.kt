package floydaddons.not.dogshit.client.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import com.github.stivais.commodore.utils.SyntaxException
import floydaddons.not.dogshit.client.FloydAddonsMod.mc
import floydaddons.not.dogshit.client.clickgui.ClickGUI
import floydaddons.not.dogshit.client.clickgui.HudManager
import floydaddons.not.dogshit.client.clickgui.LegacyFloydClickGUI
import floydaddons.not.dogshit.client.config.FloydSidecarConfig
import floydaddons.not.dogshit.client.features.ModuleManager
import floydaddons.not.dogshit.client.features.impl.player.FloydNickHider
import floydaddons.not.dogshit.client.features.impl.render.FloydMobEsp
import floydaddons.not.dogshit.client.features.impl.render.ClickGUIModule
import floydaddons.not.dogshit.client.features.impl.render.FloydXray
import floydaddons.not.dogshit.client.utils.handlers.schedule
import floydaddons.not.dogshit.client.utils.modMessage
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries

val mainCommand = Commodore("floydaddons", "floyd", "fa") {
    runs {
        schedule(0) { mc.setScreen(ClickGUI) }
    }

    literal("edithud").runs {
        schedule(0) { mc.setScreen(HudManager) }
    }

    literal("clickgui").runs {
        schedule(0) { mc.setScreen(ClickGUI) }
    }

    literal("legacygui").runs {
        schedule(0) { mc.setScreen(LegacyFloydClickGUI.openHub()) }
    }

    literal("oldgui").runs {
        schedule(0) { mc.setScreen(LegacyFloydClickGUI.openHub()) }
    }

    literal("reset") {
        literal("module").executable {
            param("moduleName") {
                suggests { ModuleManager.modules.keys.map { it.replace(" ", "_") } }
            }

            runs { moduleName: String ->
                val module = ModuleManager.modules[moduleName.replace("_", " ")]
                    ?: throw SyntaxException("Module not found.")

                module.settings.forEach { (_, setting) -> setting.reset() }
                modMessage("Settings for module ${module.name} have been reset to default values.")
            }
        }

        literal("clickgui").runs {
            ClickGUIModule.resetPositions()
            modMessage("Reset click gui positions.")
        }

        literal("hud").runs {
            HudManager.resetHUDS()
            modMessage("Reset HUD positions.")
        }
    }

    literal("xray") {
        literal("add").executable {
            param("block") {
                suggests { nearbyBlockSuggestions().ifEmpty { BuiltInRegistries.BLOCK.keySet().map { it.toString() } } }
            }
            runs { block: String ->
                val blockId = runCatching { FloydXray.validOpaqueBlockId(block) }
                    .getOrElse { throw SyntaxException(it.message ?: "Invalid block ID.") }
                val added = FloydXray.addOpaqueBlock(blockId)
                if (added) ModuleManager.saveConfigurations()
                modMessage(FloydXray.addOpaqueBlockMessage(blockId, added))
            }
        }

        literal("remove").executable {
            param("block") {
                suggests { FloydXray.opaqueBlockIds().toList() }
            }
            runs { block: String ->
                val blockId = runCatching { FloydXray.validOpaqueBlockId(block) }
                    .getOrElse { throw SyntaxException(it.message ?: "Invalid block ID.") }
                val removed = FloydXray.removeOpaqueBlock(blockId)
                if (removed) ModuleManager.saveConfigurations()
                modMessage(FloydXray.removeOpaqueBlockMessage(blockId, removed))
            }
        }

        literal("list").runs {
            modMessage(FloydXray.opaqueBlockListSummary())
        }

        literal("clear").runs {
            FloydXray.clearOpaqueBlocks()
            ModuleManager.saveConfigurations()
            modMessage("Cleared all xray opaque blocks.")
        }

        runs {
            val enabled = FloydXray.toggleXray()
            modMessage(if (enabled) "X-Ray enabled" else "X-Ray disabled")
        }
    }

    literal("name") {
        literal("self").executable {
            param("fakeName")
            runs { fakeName: GreedyString ->
                FloydNickHider.setSelfNickname(fakeName.string)
                ModuleManager.saveConfigurations()
                modMessage("Default nick set to ${fakeName.string}")
            }
        }

        literal("other").executable {
            param("ign") {
                suggests { onlinePlayerSuggestions() }
            }
            param("fakeName")
            runs { ign: String, fakeName: GreedyString ->
                FloydNickHider.addNameMapping(ign, fakeName.string)
                ModuleManager.saveConfigurations()
                modMessage("Added name mapping: $ign → ${fakeName.string}")
            }
        }

        literal("remove").executable {
            param("ign") {
                suggests { FloydNickHider.mappingIds().toList() }
            }
            runs { ign: String ->
                val removed = FloydNickHider.removeNameMapping(ign)
                if (removed) ModuleManager.saveConfigurations()
                modMessage(if (removed) "Removed name mapping for $ign" else "No mapping found for $ign")
            }
        }

        literal("list").runs {
            modMessage(FloydNickHider.nameMappingsSummary())
        }

        literal("clear").runs {
            FloydNickHider.clearNameMappings()
            ModuleManager.saveConfigurations()
            modMessage("Cleared all name mappings.")
        }
    }

    literal("mob-esp") {
        literal("add") {
            literal("name").executable {
                param("name") {
                    suggests { nearbyEntityNameSuggestions() }
                }
                runs { name: GreedyString ->
                    val filter = name.string
                    FloydMobEsp.addNameFilter(filter)
                    ModuleManager.saveConfigurations()
                    modMessage("Added mob ESP name filter: $filter")
                }
            }
            literal("type").executable {
                param("entityType") {
                    suggests { nearbyEntityTypeSuggestions().ifEmpty { BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() } } }
                }
                runs { entityType: String ->
                    val type = validMobEspTypeArg(entityType)
                    FloydMobEsp.addTypeFilter(type)
                    ModuleManager.saveConfigurations()
                    modMessage("Added mob ESP type filter: $type")
                }
            }
        }

        literal("remove") {
            literal("name").executable {
                param("name") {
                    suggests { FloydMobEsp.nameFilterIds().toList() }
                }
                runs { name: GreedyString ->
                    val filter = name.string
                    val removed = FloydMobEsp.removeNameFilter(filter)
                    if (removed) ModuleManager.saveConfigurations()
                    modMessage(if (removed) "Removed mob ESP name filter: $filter" else "Name filter not found: $filter")
                }
            }
            literal("type").executable {
                param("entityType") {
                    suggests { FloydMobEsp.typeFilterIds().toList() }
                }
                runs { entityType: String ->
                    val type = validMobEspTypeArg(entityType)
                    val removed = FloydMobEsp.removeTypeFilter(type)
                    if (removed) ModuleManager.saveConfigurations()
                    modMessage(if (removed) "Removed mob ESP type filter: $type" else "Type filter not found: $type")
                }
            }
        }

        literal("color") {
            literal("name").executable {
                param("name") {
                    suggests { FloydMobEsp.nameFilterIds().toList() }
                }
                param("hex")
                param("chroma") {
                    suggests { listOf("false", "true") }
                }
                runs { name: String, hex: String, chroma: String ->
                    val chromaValue = parseBooleanArg(chroma)
                    val updated = runCatching { FloydMobEsp.setNameFilterColor(name, hex, chromaValue) }
                        .getOrElse { throw SyntaxException(it.message ?: "Invalid color.") }
                    if (!updated) throw SyntaxException("Name filter not found: $name")
                    ModuleManager.saveConfigurations()
                    modMessage("Updated mob ESP name filter color: $name -> ${FloydMobEsp.colorSummaryForName(name)}")
                }
            }
            literal("type").executable {
                param("entityType") {
                    suggests { FloydMobEsp.typeFilterIds().toList() }
                }
                param("hex")
                param("chroma") {
                    suggests { listOf("false", "true") }
                }
                runs { entityType: String, hex: String, chroma: String ->
                    val type = validMobEspTypeArg(entityType)
                    val chromaValue = parseBooleanArg(chroma)
                    val updated = runCatching { FloydMobEsp.setTypeFilterColor(type, hex, chromaValue) }
                        .getOrElse { throw SyntaxException(it.message ?: "Invalid color.") }
                    if (!updated) throw SyntaxException("Type filter not found: $type")
                    ModuleManager.saveConfigurations()
                    modMessage("Updated mob ESP type filter color: $type -> ${FloydMobEsp.colorSummaryForType(type)}")
                }
            }
        }

        literal("list").runs {
            modMessage(FloydMobEsp.filterListSummary())
        }

        literal("clear").runs {
            FloydMobEsp.clearFilters()
            ModuleManager.saveConfigurations()
            modMessage("Cleared all mob ESP filters.")
        }

        literal("reload").runs {
            reloadMobEspConfig()
        }

        literal("debug").runs {
            showMobEspDebug()
        }

        runs {
            FloydMobEsp.toggle()
            modMessage(mobEspToggleMessage())
        }
    }

    literal("me") {
        literal("add") {
            literal("name").executable {
                param("name") {
                    suggests { nearbyEntityNameSuggestions() }
                }
                runs { name: GreedyString ->
                    val filter = name.string
                    FloydMobEsp.addNameFilter(filter)
                    ModuleManager.saveConfigurations()
                    modMessage("Added mob ESP name filter: $filter")
                }
            }
            literal("type").executable {
                param("entityType") {
                    suggests { nearbyEntityTypeSuggestions().ifEmpty { BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() } } }
                }
                runs { entityType: String ->
                    val type = validMobEspTypeArg(entityType)
                    FloydMobEsp.addTypeFilter(type)
                    ModuleManager.saveConfigurations()
                    modMessage("Added mob ESP type filter: $type")
                }
            }
        }

        literal("remove") {
            literal("name").executable {
                param("name") {
                    suggests { FloydMobEsp.nameFilterIds().toList() }
                }
                runs { name: GreedyString ->
                    val filter = name.string
                    val removed = FloydMobEsp.removeNameFilter(filter)
                    if (removed) ModuleManager.saveConfigurations()
                    modMessage(if (removed) "Removed mob ESP name filter: $filter" else "Name filter not found: $filter")
                }
            }
            literal("type").executable {
                param("entityType") {
                    suggests { FloydMobEsp.typeFilterIds().toList() }
                }
                runs { entityType: String ->
                    val type = validMobEspTypeArg(entityType)
                    val removed = FloydMobEsp.removeTypeFilter(type)
                    if (removed) ModuleManager.saveConfigurations()
                    modMessage(if (removed) "Removed mob ESP type filter: $type" else "Type filter not found: $type")
                }
            }
        }

        literal("color") {
            literal("name").executable {
                param("name") {
                    suggests { FloydMobEsp.nameFilterIds().toList() }
                }
                param("hex")
                param("chroma") {
                    suggests { listOf("false", "true") }
                }
                runs { name: String, hex: String, chroma: String ->
                    val chromaValue = parseBooleanArg(chroma)
                    val updated = runCatching { FloydMobEsp.setNameFilterColor(name, hex, chromaValue) }
                        .getOrElse { throw SyntaxException(it.message ?: "Invalid color.") }
                    if (!updated) throw SyntaxException("Name filter not found: $name")
                    ModuleManager.saveConfigurations()
                    modMessage("Updated mob ESP name filter color: $name -> ${FloydMobEsp.colorSummaryForName(name)}")
                }
            }
            literal("type").executable {
                param("entityType") {
                    suggests { FloydMobEsp.typeFilterIds().toList() }
                }
                param("hex")
                param("chroma") {
                    suggests { listOf("false", "true") }
                }
                runs { entityType: String, hex: String, chroma: String ->
                    val type = validMobEspTypeArg(entityType)
                    val chromaValue = parseBooleanArg(chroma)
                    val updated = runCatching { FloydMobEsp.setTypeFilterColor(type, hex, chromaValue) }
                        .getOrElse { throw SyntaxException(it.message ?: "Invalid color.") }
                    if (!updated) throw SyntaxException("Type filter not found: $type")
                    ModuleManager.saveConfigurations()
                    modMessage("Updated mob ESP type filter color: $type -> ${FloydMobEsp.colorSummaryForType(type)}")
                }
            }
        }

        literal("list").runs {
            modMessage(FloydMobEsp.filterListSummary())
        }

        literal("clear").runs {
            FloydMobEsp.clearFilters()
            ModuleManager.saveConfigurations()
            modMessage("Cleared all mob ESP filters.")
        }

        literal("reload").runs {
            reloadMobEspConfig()
        }

        literal("debug").runs {
            showMobEspDebug()
        }

        runs {
            FloydMobEsp.toggle()
            modMessage(mobEspToggleMessage())
        }
    }

    literal("stalk") {
        executable {
            param("ign") {
                suggests { onlinePlayerSuggestions() }
            }
            runs { ign: String ->
                FloydMobEsp.stalk(ign)
                modMessage("Stalking $ign")
            }
        }

        runs {
            val previous = FloydMobEsp.stopStalk()
            modMessage(if (previous == null) "Usage: /fa stalk <ign>" else "Stopped stalking $previous")
        }
    }

    literal("s") {
        executable {
            param("ign") {
                suggests { onlinePlayerSuggestions() }
            }
            runs { ign: String ->
                FloydMobEsp.stalk(ign)
                modMessage("Stalking $ign")
            }
        }

        runs {
            val previous = FloydMobEsp.stopStalk()
            modMessage(if (previous == null) "Usage: /fa stalk <ign>" else "Stopped stalking $previous")
        }
    }

    literal("server-id-debug").runs {
        showServerIdDebug()
    }

    literal("debug").runs {
        showServerIdDebug()
    }

    literal("mob-esp-debug").runs {
        showMobEspDebug()
    }
}

private fun showServerIdDebug() {
    modMessage(FloydNickHider.debugSummary().trimEnd())
}

private fun reloadMobEspConfig() {
    FloydSidecarConfig.reloadMobEspEntries()
    modMessage(FloydMobEsp.reloadSummary())
}

private fun validMobEspTypeArg(entityType: String): String =
    runCatching { FloydMobEsp.validTypeFilterId(entityType) }
        .getOrElse { throw SyntaxException(it.message ?: "Invalid entity type ID.") }

private fun showMobEspDebug() {
    if (mc.level == null || mc.player == null) return
    FloydMobEsp.enableDebugLabels()
    modMessage(FloydMobEsp.debugSummary())
}

private fun mobEspToggleMessage(): String =
    FloydMobEsp.toggleSummary()

val stalkCommand = Commodore("stalk") {
    executable {
        param("ign") {
            suggests { onlinePlayerSuggestions() }
        }
        runs { ign: String ->
            FloydMobEsp.stalk(ign)
            modMessage("Stalking $ign")
        }
    }

    runs {
        val previous = FloydMobEsp.stopStalk()
        modMessage(if (previous == null) "Usage: /fa stalk <ign>" else "Stopped stalking $previous")
    }
}

private fun parseBooleanArg(value: String): Boolean =
    when (value.lowercase()) {
        "true", "yes", "on", "1", "chroma" -> true
        "false", "no", "off", "0", "static" -> false
        else -> throw SyntaxException("Expected true or false for chroma.")
    }

private fun onlinePlayerSuggestions(): List<String> {
    val names = linkedSetOf<String>()
    mc.connection?.onlinePlayers?.forEach { entry ->
        val name = entry.profile.name
        if (name.matches(Regex("[a-zA-Z0-9_]{3,16}"))) names.add(name)
    }
    if (names.isEmpty()) mc.level?.players()?.mapTo(names) { it.name.string }
    return names.toList()
}

private fun nearbyEntityNameSuggestions(): List<String> {
    val player = mc.player ?: return emptyList()
    val level = mc.level ?: return emptyList()
    val names = linkedSetOf<String>()
    for (entity in level.entitiesForRendering()) {
        if (entity === player || entity.distanceToSqr(player) > 2500.0) continue
        entity.name.string.cleanSuggestionName().takeIf { it.isNotBlank() }?.let(names::add)
        entity.customName?.string?.cleanSuggestionName()?.takeIf { it.isNotBlank() }?.let(names::add)
        FloydMobEsp.cachedNpcNameFor(entity)?.cleanSuggestionName()?.takeIf { it.isNotBlank() }?.let(names::add)
    }
    return names.toList()
}

private fun nearbyEntityTypeSuggestions(): List<String> {
    val player = mc.player ?: return emptyList()
    val level = mc.level ?: return emptyList()
    val types = linkedSetOf<String>()
    for (entity in level.entitiesForRendering()) {
        if (entity === player || entity.distanceToSqr(player) > 2500.0) continue
        types.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString())
    }
    return types.toList()
}

private fun nearbyBlockSuggestions(): List<String> {
    val player = mc.player ?: return emptyList()
    val level = mc.level ?: return emptyList()
    val blocks = linkedSetOf<String>()
    val center = player.blockPosition()
    for (x in -8..8) {
        for (y in -8..8) {
            for (z in -8..8) {
                val state = level.getBlockState(BlockPos(center.x + x, center.y + y, center.z + z))
                if (!state.isAir) blocks.add(BuiltInRegistries.BLOCK.getKey(state.block).toString())
            }
        }
    }
    return blocks.toList()
}

private fun String.cleanSuggestionName(): String =
    replace(Regex("§."), "").trim()
