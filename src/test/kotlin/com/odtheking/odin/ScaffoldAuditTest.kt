package com.odtheking.odin

import com.google.gson.JsonParser
import com.odtheking.odin.features.Category
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScaffoldAuditTest {
    private val root = Path.of("").toAbsolutePath()

    @Test
    fun `active build metadata targets FloydAddons on Odin 1_21_11 scaffold`() {
        val properties = Files.readAllLines(root.resolve("gradle.properties"))
            .asSequence()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator < 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
        val fabric = JsonParser.parseString(Files.readString(root.resolve("src/main/resources/fabric.mod.json"))).asJsonObject

        assertEquals("1.21.11", properties["minecraft_version"])
        assertEquals("floydaddons", properties["mod_id"])
        assertEquals("Floyd Addons", properties["mod_name"])
        assertEquals("FloydAddons", properties["archives_base_name"])
        val build = Files.readString(root.resolve("build.gradle.kts"))
        assertTrue(build.contains("base {\n    archivesName.set(property(\"archives_base_name\") as String)\n}"))
        val initializer = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/FloydAddonsMod.kt"))
        assertTrue(initializer.contains("const val MOD_ID = \"${properties["mod_id"]}\""))
        assertTrue(initializer.contains("const val MOD_NAME = \"${properties["mod_name"]}\""))
        assertTrue(initializer.contains("const val MOD_VERSION = \"${properties["mod_version"]}\""))
        assertFalse(fabric.has("contact"), "Active Fabric metadata must not advertise the old Floyd-only fork as this combined repo's source URL.")
        assertEquals("BSD-3-Clause AND MIT", fabric.get("license").asString)
        assertEquals("assets/floydaddons/icons/taskbar_icon_128x128.png", fabric.get("icon").asString)
        assertEquals(listOf("floydaddons.mixins.json"), fabric.getAsJsonArray("mixins").map { it.asString })
        assertEquals(
            "com.odtheking.odin.FloydAddonsMod",
            fabric.getAsJsonObject("entrypoints")
                .getAsJsonArray("client")
                .first()
                .asJsonObject
                .get("value")
                .asString
        )
        assertFalse(fabric.toString().contains("update_checker"))
        assertFalse(fabric.toString().contains("modmenu.discord"))
    }

    @Test
    fun `source provenance records exact Odin and Floyd baselines`() {
        val provenance = Files.readString(root.resolve("PROVENANCE.md"))
        val readme = Files.readString(root.resolve("README.md"))
        val notices = Files.readString(root.resolve("THIRD_PARTY_NOTICES.md"))
        val migration = Files.readString(root.resolve("MIGRATION.md"))
        val completionAudit = Files.readString(root.resolve("COMPLETION_AUDIT.md"))

        for (expected in listOf(
            "https://github.com/odtheking/Odin.git",
            "https://github.com/lunabot9/FloydAddons.git",
            "https://github.com/twaldin/SkyblockQOLmod.git",
            "/Users/twaldin/SkyblockQOLmod",
            "77b66713f74849bbcc05067484e6e85c01c96698",
            "17c5ba3d4fa0185eb689a62f1f6c3de0d6a60b75",
            "vendor/floydaddons-fabric",
            "Odin's module, setting, config, event, and ClickGUI scaffolding",
        )) {
            assertTrue(provenance.contains(expected), "PROVENANCE.md missing $expected")
        }

        assertTrue(readme.contains("See `PROVENANCE.md` for the exact Odin and Floyd baseline commits"))
        assertTrue(notices.contains("Exact source baselines are recorded in `PROVENANCE.md`."))
        assertTrue(notices.contains("The vendored FloydAddons snapshot does not include a standalone license file"))
        assertTrue(notices.contains("its `app/src/main/resources/fabric.mod.json` declares `MIT`"))
        assertTrue(notices.contains("Permission is hereby granted, free of charge"))
        assertTrue(notices.contains("THE SOFTWARE IS PROVIDED \"AS IS\""))
        assertTrue(migration.contains("`upstream-odin/main` is `77b66713f74849bbcc05067484e6e85c01c96698`"))
        assertTrue(migration.contains("`upstream-floyd/main` plus `fork-floyd/main` are `17c5ba3d4fa0185eb689a62f1f6c3de0d6a60b75`"))
        assertTrue(completionAudit.contains("Use Odin 1.21 scaffold, not old OdinClient/1.8.9"))
        assertTrue(completionAudit.contains("Verified by `scripts/run-runtime-scaffold-smoke.sh`."))
        assertTrue(completionAudit.contains("exits only after port `38765` is closed"))
        assertTrue(completionAudit.contains("FLOYDADDONS_RUN_LIVE_HYPIXEL=true"))
        assertTrue(completionAudit.contains("FLOYDADDONS_RUN_LIVE_PREFLIGHT=true"))
        assertTrue(completionAudit.contains("logs/live-hypixel-preflight.json"))
        assertTrue(completionAudit.contains("logs/live-hypixel-proof.json"))
        assertTrue(completionAudit.contains("Live Hypixel server-ID acquisition from Hypixel itself"))
        assertTrue(completionAudit.contains("Not yet verified; this remains the completion blocker."))
        assertTrue(completionAudit.contains("FLOYDADDONS_RUN_LIVE_HYPIXEL=true ./scripts/verify-floyd-in-odin.sh"))
    }

    @Test
    fun `active categories are Floyd gui groups only`() {
        assertEquals(
            listOf("Render", "Hiders", "Player", "Camera", "Cosmetic", "QOL", "Misc"),
            Category.categories.keys.toList()
        )
    }

    @Test
    fun `active module registry is Floyd feature modules plus Odin ClickGUI`() {
        val moduleManager = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/ModuleManager.kt"))
        val moduleConfig = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/config/ModuleConfig.kt"))
        val sidecarConfig = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/config/FloydSidecarConfig.kt"))
        val clickGui = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/ClickGUIModule.kt"))
        val build = Files.readString(root.resolve("build.gradle.kts"))
        val verifier = Files.readString(root.resolve("scripts/verify-floyd-in-odin.sh"))
        val runtimeVerifier = Files.readString(root.resolve("scripts/verify-runtime-scaffold.py"))
        val runtimeVerifierTest = Files.readString(root.resolve("scripts/test-runtime-scaffold-verifier.py"))
        val legacyClickGuiRuntimeVerifier = Files.readString(root.resolve("scripts/verify-legacy-clickgui-runtime.py"))
        val legacyClickGuiRuntimeVerifierTest = Files.readString(root.resolve("scripts/test-legacy-clickgui-runtime-verifier.py"))
        val runtimeSmoke = Files.readString(root.resolve("scripts/run-runtime-scaffold-smoke.sh"))
        val installJar = Files.readString(root.resolve("scripts/install-built-jar.sh"))
        val workflow = Files.readString(root.resolve(".github/workflows/build.yml"))
        val readme = Files.readString(root.resolve("README.md"))
        val expectedRegistrations = listOf(
            "ClickGUIModule",
            "FloydRender",
            "FloydXray",
            "FloydAnimations",
            "FloydHud",
            "FloydMobEsp",
            "FloydHiders",
            "FloydNickHider",
            "FloydPlayerSize",
            "FloydCamera",
            "FloydSkin",
            "FloydCape",
            "FloydConeHat",
            "FloydDiscordPresence",
            "FloydLocalControl",
            "FloydCompatibility",
        )
        for (registration in expectedRegistrations) {
            assertTrue(moduleManager.contains(registration), "Missing $registration registration")
        }
        val registerBlock = Regex("registerModules\\([\\s\\S]*?\\n\\s*\\)")
            .find(moduleManager)
            ?.value
            ?: error("Could not find ModuleManager registerModules block")
        val activeRegistrations = Regex("\\b(?:ClickGUIModule|Floyd[A-Za-z]+)\\b(?=,)")
            .findAll(registerBlock)
            .map { it.value }
            .distinct()
            .toList()
        assertEquals(
            expectedRegistrations,
            activeRegistrations,
            "The active Odin module registry must be exactly the Floyd feature modules plus ClickGUI in Floyd GUI group order."
        )

        val forbiddenRegistrations = listOf(
            "Cata",
            "Dungeon",
            "Kuudra",
            "Skyblock",
            "Nether",
            "Boss",
            "Waypoint",
            "Termsim",
            "Soopy",
        )
        for (registration in forbiddenRegistrations) {
            assertFalse(moduleManager.contains(registration), "Unexpected old Odin registration token: $registration")
        }

        for (expected in listOf(
            "fun state(): Map<String, Any?>",
            "\"moduleCount\" to modules.size",
            "\"categoryCount\" to Category.categories.size",
            "\"configCount\" to configs.size",
            "\"configs\" to configs.map { it.state() }",
            "\"categories\" to Category.categories.values.map",
            "\"modules\" to categoryModules.map",
            "\"settingCount\" to module.settings.size",
        )) {
            assertTrue(moduleManager.contains(expected), "ModuleManager state missing token: $expected")
        }
        for (expected in listOf(
            "fun state(): Map<String, Any?>",
            "\"file\" to file.path",
            "\"exists\" to file.exists()",
            "\"sizeBytes\" to file.length()",
            "\"moduleCount\" to modules.size",
        )) {
            assertTrue(moduleConfig.contains(expected), "ModuleConfig state missing token: $expected")
        }
        for (expected in listOf(
            "fun state(): Map<String, Any?>",
            "\"configDir\" to paths.configDir.toString()",
            "\"preserveFreshNameTemplate\" to preserveFreshNameTemplate",
            "\"preserveFreshMobEspTemplate\" to preserveFreshMobEspTemplate",
            "\"nameMappings\" to fileState(paths.namesPath)",
            "\"xrayOpaque\" to fileState(paths.xrayOpaquePath)",
            "\"mobEsp\" to fileState(paths.mobEspPath)",
        )) {
            assertTrue(sidecarConfig.contains(expected), "FloydSidecarConfig state missing token: $expected")
        }

        for (expected in listOf(
            "fun state(): Map<String, Any?>",
            "\"chatNotifications\" to enableNotification",
            "\"legacyButtonTextColor\" to",
            "\"legacyButtonBorderColor\" to",
            "\"legacyGuiBorderColor\" to",
            "val buttonTextFade by BooleanSetting(\"Button Text Fade\"",
            "val buttonBorderFade by BooleanSetting(\"Button Border Fade\"",
            "val guiBorderFade by BooleanSetting(\"GUI Border Fade\"",
            "\"roundedPanelBottoms\" to roundedPanelBottom",
            "\"panelCount\" to panelSetting.size",
            "\"panels\" to panelSetting.mapValues",
            "private fun topRowPanelGap(availableWidth: Float, panelCount: Int): Float",
            "setting.x = 10f + (Panel.WIDTH + gap) * index",
            "setting.y = 10f",
            "setting.extended = true",
        )) {
            assertTrue(clickGui.contains(expected), "ClickGUI scaffold state missing token: $expected")
        }

        for (expected in listOf(
            "named<Jar>(\"jar\")",
            "tasks.named<Jar>(\"sourcesJar\")",
            "from(listOf(\"LICENSE\", \"THIRD_PARTY_NOTICES.md\", \"PROVENANCE.md\"))",
            "into(\"META-INF\")",
        )) {
            assertTrue(build.contains(expected), "Build missing packaged notice token: $expected")
        }

        for (expected in listOf(
            "floyd_grouping_source_regex",
            "features\\.impl\\.qol",
            "features/impl/qol",
            "features\\.impl\\.player\\.FloydSkin",
            "features/impl/player/FloydSkin",
            "stale_dirs=(",
            "src/main/kotlin/com/odtheking/odin/utils/network/hypixelapi",
            "src/main/kotlin/com/odtheking/odin/utils/ui/widget",
            "vendor/floydaddons-fabric/build-release",
            "git add -n .",
            "git add dry-run failed",
            "dry_run_add_output=\"$(git add -n . 2>&1)\"",
            "git add dry-run would include generated or local-only files",
            "build-release|__pycache__|\\.pyc|logs/|\\.kotlin/|CLAUDE\\.md|deploy\\.sh|\\.flt",
            "com/odtheking/odin/features/impl/(boss|dungeon|nether|skyblock|qol)",
            "com/odtheking/odin/features/impl/player/FloydSkin",
            "AutoSessionID|PersonalBest|ServerUtils|JsonResourceLoader|CustomGUIImpl|DrawContextRenderer|PlayerUtils|createSoundSettings|setClipboardContent|DevModule|isDevModule",
            "containsOneOf|equalsOneOf|matchesOneOf|capitalizeFirst|toFixed|formatTime|romanToInt|getBlockBounds|clickSlot|formatNumber|sendChatMessage|getCenteredText|getChatBreak|Vec2|floorVec|isXZInterceptable",
            "com/odtheking/odin/utils/(AutoSessionID|PersonalBest|ServerUtils|JsonResourceLoader",
            "PlayerUtils",
            "ui/widget/CustomGUIImpl",
            "render/DrawContextRenderer",
            "com/odtheking/odin/clickgui/settings/DevModule",
            "packaged Fabric metadata matches Floyd-in-Odin scaffold",
            "sources Fabric metadata matches Floyd-in-Odin scaffold",
            "packaged license and provenance documents match repository files",
            "META-INF/{document}",
            "active mixin config exactly matches source files",
            "packaged resources exactly match active resource tree",
            "sources jar exactly matches active source and resource trees",
            "scripts/verify-runtime-scaffold.py",
            "scripts/live-install-status.py",
            "scripts/test-runtime-scaffold-verifier.py",
            "python3 scripts/test-install-built-jar.py",
            "python3 scripts/test-live-hypixel-status.py",
            "python3 scripts/test-completion-status.py",
            "python3 scripts/test-live-install-status.py",
            "python3 scripts/completion-status.py --help >/dev/null",
            "python3 scripts/live-install-status.py --help >/dev/null",
            "bash -n scripts/run-runtime-scaffold-smoke.sh",
            "bash -n scripts/install-built-jar.sh",
            "bash -n scripts/live-hypixel-status.sh",
            "FLOYDADDONS_RUN_RUNTIME_SMOKE cannot be combined with live Hypixel proof flags.",
            "Runtime smoke launches a non-auth dev client",
            "FLOYDADDONS_RUN_RUNTIME_SMOKE",
            "scripts/run-runtime-scaffold-smoke.sh >/dev/null",
            "FLOYDADDONS_RUN_LIVE_DIAGNOSE",
            "diagnose_file=\"\${FLOYDADDONS_LIVE_DIAGNOSE_PROOF:-logs/live-hypixel-diagnose.json}\"",
            "write_diagnose_artifact \"\$diagnose_file\"",
            "python3 scripts/verify-live-hypixel-acquisition.py --diagnose --json",
            "FLOYDADDONS_RUN_LIVE_PREFLIGHT",
            "preflight_proof_file=\"\${FLOYDADDONS_LIVE_PREFLIGHT_PROOF:-logs/live-hypixel-preflight.json}\"",
            "python3 scripts/verify-live-hypixel-acquisition.py --preflight --json",
            "FLOYDADDONS_RUN_LIVE_HYPIXEL",
            "live_proof_file=\"\${FLOYDADDONS_LIVE_HYPIXEL_PROOF:-logs/live-hypixel-proof.json}\"",
            "write_success_proof \"\$live_proof_file\"",
            "python3 -m json.tool \"\$tmp_file\" >/dev/null",
            "python3 scripts/verify-live-hypixel-acquisition.py --json",
            "FLOYDADDONS_REQUIRE_COMPLETE",
            "python3 scripts/completion-status.py --require-complete --json",
            "unexpected contact block in packaged fabric.mod.json",
            "unexpected contact block in sources fabric.mod.json",
            "SkyblockQOLmod",
        )) {
            assertTrue(verifier.contains(expected), "Verifier missing grouping audit token: $expected")
        }
        for (expected in listOf(
            "test_installs_runtime_jar_and_replaces_only_old_floyd_jars",
            "test_installs_runtime_dependency_jars_from_explicit_dependency_dir",
            "test_installs_fabric_profile_with_repo_pinned_versions",
            "test_keep_old_jars_preserves_existing_floyd_runtime_jars",
            "test_rejects_non_mods_target",
        )) {
            assertTrue(
                Files.readString(root.resolve("scripts/test-install-built-jar.py")).contains(expected),
                "install-built-jar tests missing token: $expected"
            )
        }
        val liveStatus = Files.readString(root.resolve("scripts/live-hypixel-status.sh"))
        val liveStatusTest = Files.readString(root.resolve("scripts/test-live-hypixel-status.py"))
        val completionStatus = Files.readString(root.resolve("scripts/completion-status.py"))
        val completionStatusTest = Files.readString(root.resolve("scripts/test-completion-status.py"))
        val liveInstallStatus = Files.readString(root.resolve("scripts/live-install-status.py"))
        val liveInstallStatusTest = Files.readString(root.resolve("scripts/test-live-install-status.py"))
        for (expected in listOf(
            "FLOYDADDONS_LIVE_STATUS_FILE",
            "logs/live-hypixel-status.json",
            "python3 scripts/verify-live-hypixel-acquisition.py --diagnose --json",
            "python3 -m json.tool \"\$tmp_file\" >/dev/null",
            "\"launcherRunning\"",
            "\"gameProcessRunning\"",
            "\"localControlListening\"",
            "cat \"\$status_file\"",
            "live status recorded not-ready state",
        )) {
            assertTrue(liveStatus.contains(expected), "live-hypixel-status helper missing token: $expected")
        }
        for (expected in listOf(
            "test_writes_valid_not_ready_status_without_temp_leftovers",
            "test_respects_custom_status_file_env",
            "stdout_payload",
            "localControlPort",
            "control-bridge.json",
        )) {
            assertTrue(liveStatusTest.contains(expected), "live-hypixel-status tests missing token: $expected")
        }
        for (expected in listOf(
            "fabric-loader-0.18.5-1.21.11",
            "fabric-api-0.141.3+1.21.11.jar",
            "fabric-language-kotlin-1.13.10+kotlin.2.3.20.jar",
            "launcher_profiles.json",
            "missing launcher profile for fabric-loader-1.21.11",
            "sha256",
            "validJar",
            "zipfile.is_zipfile",
            "installed FloydAddons jar does not match current build",
            "invalid {name} jar",
        )) {
            assertTrue(liveInstallStatus.contains(expected), "live-install-status helper missing token: $expected")
        }
        for (expected in listOf(
            "test_reports_ready_when_profile_and_runtime_jars_exist",
            "test_reports_missing_profile_and_dependency_jars",
            "test_rejects_installed_floyd_jar_that_does_not_match_build",
            "test_rejects_missing_current_build_jar",
            "test_rejects_invalid_dependency_jar",
            "test_rejects_invalid_current_build_jar",
            "FloydAddons-0.1.0.jar",
        )) {
            assertTrue(liveInstallStatusTest.contains(expected), "live-install-status tests missing token: $expected")
        }
        for (expected in listOf(
            "test_reports_live_install_readiness_separately",
            "test_reports_incomplete_when_install_is_missing_even_with_both_proofs",
            "test_rejects_preflight_live_proof_as_completion",
            "test_rejects_non_live_scan_source_as_completion",
            "test_rejects_stale_live_hit_as_completion",
            "test_require_complete_exits_nonzero_when_live_proof_is_missing",
            "test_require_complete_exits_zero_when_both_proofs_are_valid",
            "report[\"liveHypixel\"][\"scanHits\"]",
            "report[\"liveHypixel\"][\"worldTime\"]",
            "missing Fabric loader profile version JSON",
            "missing floydaddons jar",
            "liveInstall",
            "live launcher install is not ready",
            "--require-complete",
        )) {
            assertTrue(completionStatusTest.contains(expected), "completion-status tests missing token: $expected")
        }
        for (expected in listOf(
            "LIVE_SCAN_SOURCES",
            "MAX_LIVE_HIT_AGE_TICKS",
            "proof.get(\"preflight\") is not True",
            "world_time - last_hit_tick == hit_age",
            "remaining_reasons",
        )) {
            assertTrue(completionStatus.contains(expected), "completion-status helper missing token: $expected")
        }
        for (expected in listOf(
            "FLOYDADDONS_MODS_DIR",
            "target must be a Minecraft mods directory ending in /mods",
            "FLOYDADDONS_SKIP_BUILD",
            "FLOYDADDONS_SKIP_FABRIC_PROFILE",
            "FLOYDADDONS_FABRIC_INSTALLER_JAR",
            "FLOYDADDONS_FABRIC_INSTALLER_VERSION",
            "fabric-loader-\${loader_version}-\${minecraft_version}.json",
            "FLOYDADDONS_KEEP_OLD_JARS",
            "FLOYDADDONS_SKIP_RUNTIME_DEPS",
            "FLOYDADDONS_RUNTIME_DEPS_DIR",
            "FLOYDADDONS_KEEP_OLD_RUNTIME_DEPS",
            "fabric-api-\${fabric_api_version}.jar",
            "fabric-language-kotlin-\${fabric_kotlin_version}.jar",
            "build/libs/FloydAddons-*.jar",
            "install -m 0644",
        )) {
            assertTrue(installJar.contains(expected), "install-built-jar helper missing token: $expected")
        }
        assertTrue(workflow.contains("./scripts/install-built-jar.sh"))
        assertTrue(readme.contains("./scripts/install-built-jar.sh /path/to/.minecraft/mods"))
        assertTrue(
            verifier.indexOf("sources jar exactly matches active source and resource trees") <
                verifier.lastIndexOf("FLOYDADDONS_RUN_RUNTIME_SMOKE"),
            "Opt-in runtime smoke must run after normal source/jar audits."
        )
        assertTrue(
            verifier.lastIndexOf("FLOYDADDONS_RUN_RUNTIME_SMOKE") <
                verifier.lastIndexOf("FLOYDADDONS_RUN_LIVE_HYPIXEL"),
            "Live Hypixel proof must run after the opt-in non-auth runtime smoke."
        )
        assertTrue(
            verifier.lastIndexOf("FLOYDADDONS_RUN_LIVE_HYPIXEL") <
                verifier.indexOf("floyd-in-odin verification passed"),
            "Final success line must come after all requested opt-in proof gates."
        )

        for (expected in listOf(
            "EXPECTED_CATEGORY_MODULES",
            "\"Render\": [\"Click GUI\", \"Render\", \"X-Ray\", \"Animations\", \"HUD\", \"Mob ESP\"]",
            "\"QOL\": []",
            "EXPECTED_SCAFFOLD",
            "\"minecraftVersion\": \"1.21.11\"",
            "bridge.validate_loopback_host(args.host)",
            "bridge.load_bridge(args)",
            "bridge.request_json(args.host, port, token, \"GET\", \"/health\", args.timeout)",
            "bridge.request_json(args.host, port, token, \"GET\", \"/state\", args.timeout)",
            "--require-title-screen",
            "screen expected title screen",
            "misc.localControl",
            "\"settingsEnabled\"",
        )) {
            assertTrue(runtimeVerifier.contains(expected), "Runtime scaffold verifier missing token: $expected")
        }
        for (expected in listOf(
            "test_accepts_current_runtime_scaffold_shape",
            "test_rejects_wrong_minecraft_version",
            "test_rejects_missing_floyd_module_group",
            "test_rejects_non_empty_qol_surface",
            "test_rejects_disabled_local_control_runtime_proof",
            "test_rejects_non_title_screen_when_title_screen_is_required",
        )) {
            assertTrue(runtimeVerifierTest.contains(expected), "Runtime scaffold verifier test missing token: $expected")
        }
        for (expected in listOf(
            "verify_xray_left_click",
            "verify_mob_esp_right_click",
            "\"X-Ray\"",
            "\"Mob ESP\"",
            "\"coordinateSpace\": \"gui\"",
            "X-Ray left click expected false -> true",
            "verify_xray_edit_blocks_extra_input",
            "\"xrayEditBlocksExtraInput\"",
            "xray_opaque_blocks(state)",
            "click_xray_block_input(client, popup_after_expand, submit=False)",
            "click_xray_block_row(client, popup_after_add, test_block, add=False)",
            "X-Ray Edit Blocks add expected",
            "Mob ESP right click must not toggle enabled state",
            "verify_mob_esp_edit_filters_extra_input",
            "\"mobEspEditFiltersExtraInput\"",
            "mob_name_filters(state)",
            "click_mob_filter_input(client, popup_after_expand, \"ADD_MANUAL_NAME\", submit=False)",
            "click_mob_filter_row(client, popup_after_add, test_name, \"REMOVE_NAME\")",
            "Mob ESP Edit Filters add expected",
            "click_popup_setting(client, popup, \"Tracers\")",
            "Mob ESP Tracers popup click did not toggle value",
            "\"tracersPopupClick\"",
            "click_popup_number_at_fraction(client, popup, \"Opacity\"",
            "X-Ray Opacity slider click did not change value",
            "\"opacitySliderClick\"",
            "click_popup_color_chroma(client, popup_after_color_expand, \"Default ESP Color\")",
            "Mob ESP Default ESP Color chroma click did not toggle value",
            "\"defaultEspColorChromaClick\"",
            "verify_instance_title_string_edit",
            "\"Instance Name\"",
            "\"Instance Title\"",
            "Instance Title edit expected",
            "\"instanceTitleStringEdit\"",
            "verify_camera_popup_controls",
            "\"cameraPopupControls\"",
            "\"Freecam\"",
            "Freecam Speed slider click did not change value",
            "\"F5 Customizer\"",
            "F5 Disable Front Cam click did not toggle value",
            "F5 Disable Back Cam click did not toggle value",
            "F5 No Third-Person Clipping click did not toggle value",
            "F5 Scrolling Changes Distance click did not toggle value",
            "F5 Reset F5 Scrolling click did not toggle value",
            "\"booleanClicks\"",
            "F5 Camera Distance slider click did not change value",
            "verify_hiders_no_armor_selector",
            "\"No Armor\"",
            "\"Target\"",
            "popup_setting_options(popup, \"Target\")",
            "No Armor Target selector click did not change value",
            "\"hidersNoArmorSelector\"",
            "verify_player_size_selector_and_number",
            "\"playerSizeSelectorAndNumber\"",
            "\"Player Size\"",
            "Player Size Target selector click did not change value",
            "Player Size X slider click did not change value",
            "Player Size Y slider click did not change value",
            "Player Size Z slider click did not change value",
            "\"ySliderClick\"",
            "\"zSliderClick\"",
            "verify_stalk_target_extra_input",
            "\"Stalk Player\"",
            "\"Tracer Color\"",
            "click_popup_extra(client, popup, \"Target:\")",
            "click_stalk_target_input(client, popup_after_expand, submit=False)",
            "Stalk Player Tracer Color chroma click did not toggle value",
            "\"tracerColorChromaClick\"",
            "Stalk Player Target input expected",
            "\"stalkTargetExtraInput\"",
            "verify_neck_hider_edit_names_extra_input",
            "\"neckHiderEditNamesExtraInput\"",
            "nick_name_mappings(state)",
            "click_name_mapping_entry(client, popup_after_expand, \"ADD_MANUAL_ORIGINAL\")",
            "click_name_mapping_entry(client, popup_before_save, \"ADD_MANUAL_SAVE\")",
            "Neck Hider Edit Names add expected",
            "prepare_cape_verifier_assets(runtime_config_path)",
            "verify_cape_image_cycle_and_action",
            "\"capeImageCycleAndAction\"",
            "\"zz_floyd_verify_a.png\"",
            "Cape Image cycle click did not change value",
            "Cape Open Folder action click was not handled",
            "verify_cone_hat_image_cycle_and_action",
            "\"coneHatImageCycleAndAction\"",
            "\"zz_floyd_cone_verify_a.png\"",
            "Cone Hat Height slider click did not change value",
            "Cone Hat Radius slider click did not change value",
            "Cone Hat Y Offset slider click did not change value",
            "Cone Hat Rotation slider click did not change value",
            "Cone Hat Spin Speed slider click did not change value",
            "\"numberClicks\"",
            "Cone Hat Image cycle click did not change value",
            "Cone Hat Open Folder action click was not handled",
            "verify_custom_skin_popup_controls",
            "\"customSkinPopupControls\"",
            "\"zz_floyd_skin_verify_a.png\"",
            "Custom Skin Self popup click did not toggle value",
            "Custom Skin Skin cycle click did not change value",
            "Custom Skin Open Folder action click was not handled",
            "verify_gui_style_color_picker",
            "\"guiStyleColorPicker\"",
            "Expected legacy GUI page GUI_STYLE",
            "GUI Style Button Text Fade did not toggle value",
            "restore_gui_style_flags(client, before)",
            "verify_render_page_time_controls",
            "\"renderPageTimeControls\"",
            "Expected legacy GUI page RENDER",
            "find_render_entry(state, \"BOOLEAN_TOGGLE\", \"Server ID Hider\")",
            "find_render_entry(state, \"XRAY_TOGGLE\", \"X-Ray\")",
            "find_render_entry(state, \"MODULE_TOGGLE\", \"Mob ESP\")",
            "find_render_entry(state, \"BORDERLESS\", \"Borderless Window\")",
            "Render {label_name} row did not toggle value",
            "Render X-Ray Opacity row did not change value",
            "Render Time row did not change value",
            "verify_xray_page_controls",
            "\"xrayPageControls\"",
            "Expected legacy GUI page XRAY",
            "find_xray_page_entry(state, test_block, add=False)",
            "X-Ray page remove expected",
            "verify_hiders_page_controls",
            "\"hidersPageControls\"",
            "Expected legacy GUI page HIDERS",
            "find_hiders_entry(state, kind, name)",
            "\"removeFireOverlay\"",
            "\"hideEntityFire\"",
            "\"disableAttachedArrows\"",
            "\"removeExplosionParticles\"",
            "\"disableHungerBar\"",
            "\"hidePotionEffects\"",
            "\"thirdPersonCrosshair\"",
            "\"removeFallingBlocks\"",
            "\"removeTabPing\"",
            "Hiders {name} row did not {action} value",
            "verify_animations_page_controls",
            "\"animationsPageControls\"",
            "Expected legacy GUI page ANIMATIONS",
            "find_animations_entry(state, \"TOGGLE_MODULE\", \"\")",
            "Animations module row did not toggle value",
            "find_animations_entry(state, \"TOGGLE_SETTING\", \"Cancel Re-Equip\")",
            "find_animations_entry(state, \"TOGGLE_SETTING\", \"Hide Hand\")",
            "find_animations_entry(state, \"TOGGLE_SETTING\", \"Classic Click\")",
            "Animations Cancel Re-Equip row did not toggle value",
            "Animations Hide Hand row did not toggle value",
            "Animations Classic Click row did not toggle value",
            "verify_mob_esp_page_controls",
            "\"mobEspPageControls\"",
            "Expected legacy GUI page MOB_ESP",
            "find_mob_esp_entry(state, \"TOGGLE\", \"Tracers\")",
            "Mob ESP Tracers page row did not toggle value",
            "find_mob_esp_entry(state, \"TOGGLE\", \"Hitboxes\")",
            "Mob ESP Hitboxes page row did not toggle value",
            "find_mob_esp_entry(state, \"COLOR_PICK\", \"Default ESP Color\")",
            "Mob ESP Default ESP Color page chroma did not toggle value",
            "find_mob_esp_entry(state, \"COLOR_PICK\", \"Tracer Color\")",
            "Mob ESP Stalk Tracer Color page chroma did not toggle value",
            "verify_mob_esp_filters_page_controls",
            "\"mobEspFiltersPageControls\"",
            "Expected legacy GUI page MOB_ESP_FILTERS",
            "find_mob_filter_page_entry(state, \"ADD_MANUAL_NAME\")",
            "Mob ESP Filters page add expected",
            "verify_camera_page_controls",
            "\"cameraPageControls\"",
            "Expected legacy GUI page CAMERA",
            "find_camera_entry(state, \"BOOLEAN_TOGGLE\", \"Disable Front Cam\")",
            "find_camera_entry(state, \"BOOLEAN_TOGGLE\", \"Disable Back Cam\")",
            "find_camera_entry(state, \"BOOLEAN_TOGGLE\", \"No Third-Person Clipping\")",
            "find_camera_entry(state, \"BOOLEAN_TOGGLE\", \"Scrolling Changes Distance\")",
            "find_camera_entry(state, \"BOOLEAN_TOGGLE\", \"Reset F5 Scrolling\")",
            "Camera Disable Front row did not toggle value",
            "Camera Disable Back row did not toggle value",
            "Camera No Third-Person Clipping row did not toggle value",
            "Camera Scrolling Changes Distance row did not toggle value",
            "Camera Reset F5 Scrolling row did not toggle value",
            "verify_cosmetic_page_controls",
            "\"cosmeticPageControls\"",
            "Expected legacy GUI page COSMETIC",
            "find_cosmetic_entry(state, \"TOGGLE_SKIN\", \"Custom Skin\")",
            "find_cosmetic_entry(state, \"TOGGLE_CAPE\", \"Cape\")",
            "find_cosmetic_entry(state, \"TOGGLE_CONE\", \"Cone Hat\")",
            "Cosmetic Custom Skin row did not toggle value",
            "Cosmetic Cape row did not toggle value",
            "Cosmetic Cone Hat row did not toggle value",
            "Cosmetic Player Size Y row did not change value",
            "Cosmetic Player Size Z row did not change value",
            "verify_skin_page_controls",
            "\"skinPageControls\"",
            "Expected legacy GUI page SKIN",
            "find_skin_entry(state, \"TOGGLE\", \"Custom Skin\")",
            "Skin Custom Skin page row did not toggle value",
            "select_skin_dropdown_value(client, state, dropdown_target)",
            "verify_cape_page_controls",
            "\"capePageControls\"",
            "Expected legacy GUI page CAPE",
            "cape_page_button(state, \"next\")",
            "Cape page Next click did not change value",
            "verify_cone_hat_page_controls",
            "\"coneHatPageControls\"",
            "Expected legacy GUI page CONE_HAT",
            "cone_controls(state)",
            "Cone Hat page Height slider did not change value",
            "set_cone_input_value(client, state, 1, radius_target)",
            "set_cone_input_value(client, state, 2, y_offset_target)",
            "set_cone_input_value(client, state, 3, rotation_target)",
            "set_cone_input_value(client, state, 4, spin_target)",
            "Cone Hat page Y Offset input did not change value",
            "Cone Hat page Rotation input did not change value",
            "Cone Hat page Spin Speed input did not change value",
            "verify_player_size_page_controls",
            "\"playerSizePageControls\"",
            "Expected legacy GUI page PLAYER_SIZE",
            "find_nick_hider_entry(state, \"PLAYER_SIZE\")",
            "Player Size page module toggle did not change value",
            "Player Size page Size Z slider did not change value",
            "\"zClickHandled\"",
            "verify_name_mappings_page_controls",
            "\"nameMappingsPageControls\"",
            "Expected legacy GUI page NAME_MAPPINGS",
            "find_name_mapping_page_entry(state, \"ADD_MANUAL\")",
            "Name Mappings add expected",
            "verify_hud_edit_layout_extra",
            "verify_hud_layout_entry(client, \"Inventory HUD\")",
            "verify_hud_layout_entry(client, \"Custom Scoreboard\")",
            "\"hudEditLayoutExtra\"",
            "\"Inventory HUD\"",
            "\"Custom Scoreboard\"",
            "{label} Edit Layout expected HudManager screen",
            "\"com.odtheking.odin.clickgui.HudManager\"",
            "Expected legacy GUI page CLICK_GUI",
        )) {
            assertTrue(legacyClickGuiRuntimeVerifier.contains(expected), "Legacy ClickGUI runtime verifier missing token: $expected")
        }
        for (expected in listOf(
            "test_accepts_old_module_row_click_semantics",
            "test_rejects_mob_right_click_toggle_regression",
            "test_rejects_missing_xray_popup_controls",
            "test_rejects_popup_toggle_regression",
            "test_rejects_slider_regression",
            "test_rejects_xray_edit_blocks_regression",
            "test_rejects_xray_page_regression",
            "test_rejects_color_chroma_regression",
            "test_rejects_mob_edit_filters_regression",
            "test_rejects_string_edit_regression",
            "test_rejects_camera_slider_regression",
            "test_rejects_selector_regression",
            "test_rejects_player_size_slider_regression",
            "test_rejects_stalk_target_extra_input_regression",
            "test_rejects_neck_hider_edit_names_regression",
            "test_rejects_cape_image_cycle_regression",
            "test_rejects_cone_hat_image_cycle_regression",
            "test_rejects_custom_skin_cycle_regression",
            "test_rejects_custom_skin_toggle_regression",
            "test_rejects_gui_style_fade_regression",
            "test_rejects_render_page_time_regression",
            "test_rejects_hiders_page_regression",
            "test_rejects_animations_page_regression",
            "test_rejects_mob_esp_page_regression",
            "test_rejects_camera_page_regression",
            "test_rejects_cosmetic_page_regression",
            "test_rejects_skin_page_regression",
            "test_rejects_cape_page_regression",
            "test_rejects_cone_hat_page_regression",
            "test_rejects_player_size_page_regression",
            "test_rejects_hud_edit_layout_regression",
            "FakeClient",
        )) {
            assertTrue(legacyClickGuiRuntimeVerifierTest.contains(expected), "Legacy ClickGUI runtime verifier test missing token: $expected")
        }
        for (expected in listOf(
            "FLOYDADDONS_DEVAUTH=false ./gradlew runClient --quiet",
            "python3 scripts/verify-runtime-scaffold.py --json --require-title-screen",
            "proof_file=\"\${FLOYD_RUNTIME_SMOKE_PROOF:-logs/runtime-scaffold-proof.json}\"",
            "tee \"\$proof_file\"",
            "kill \"\$client_pid\"",
            "lsof -nP -iTCP:\"\$port\" -sTCP:LISTEN",
            "Port \$port is still listening after client shutdown.",
        )) {
            assertTrue(runtimeSmoke.contains(expected), "Runtime smoke wrapper missing token: $expected")
        }
        for (expected in listOf(
            "./scripts/verify-floyd-in-odin.sh",
            "./scripts/verify-live-hypixel-acquisition.py",
            "./scripts/verify-runtime-scaffold.py",
            "./scripts/verify-legacy-clickgui-runtime.py",
            "./scripts/run-runtime-scaffold-smoke.sh",
        )) {
            assertTrue(workflow.contains(expected), "Workflow missing executable verifier token: $expected")
        }

        for (expected in listOf(
            "- Render: `FloydRender`, `FloydXray`, `FloydAnimations`, `FloydHud`, `FloydMobEsp`",
            "- Hiders: `FloydHiders`",
            "- Player: `FloydNickHider`, `FloydPlayerSize`",
            "- Camera: `FloydCamera`",
            "- Cosmetic: `FloydSkin`, `FloydCape`, `FloydConeHat`",
            "- Misc: `FloydDiscordPresence`, `FloydLocalControl`, `FloydCompatibility`",
            "FLOYDADDONS_RUN_RUNTIME_SMOKE=true",
            "FLOYDADDONS_RUN_LIVE_PREFLIGHT=true",
            "logs/live-hypixel-preflight.json",
            "FLOYDADDONS_RUN_LIVE_HYPIXEL=true",
            "Do not combine the live flags with `FLOYDADDONS_RUN_RUNTIME_SMOKE=true`",
            "scripts/run-runtime-scaffold-smoke.sh",
            "The wrapper waits for `net.minecraft.client.gui.screens.TitleScreen`",
            "logs/runtime-scaffold-proof.json",
        )) {
            assertTrue(readme.contains(expected), "README missing grouped module surface: $expected")
        }
    }

    @Test
    fun `active module categories follow Floyd GUI grouping`() {
        val oldGui = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/ClickGuiScreen.java"))
        val renderTab = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/tab/RenderTab.java"))
        val qolTab = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/tab/QolTab.java"))
        val cosmeticTab = Files.readString(root.resolve("vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/tab/CosmeticTab.java"))
        val render = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydRender.kt"))
        val animations = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydAnimations.kt"))
        val mobEsp = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt"))
        val hud = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydHud.kt"))
        val clickGuiModule = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/ClickGUIModule.kt"))
        val skin = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydSkin.kt"))
        val playerSize = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/player/FloydPlayerSize.kt"))
        val nickHider = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/player/FloydNickHider.kt"))
        val cape = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydCape.kt"))
        val coneHat = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/cosmetic/FloydConeHat.kt"))
        val localControl = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/misc/FloydLocalControl.kt"))

        assertFalse(Files.exists(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/qol")))
        assertFalse(Files.exists(root.resolve("src/test/kotlin/com/odtheking/odin/features/impl/qol")))
        assertTrue(Files.isRegularFile(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydMobEsp.kt")))
        assertTrue(Files.isRegularFile(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/render/FloydHud.kt")))
        assertTrue(oldGui.contains("render.add(new ModuleEntry(\"Mob ESP\""))
        assertTrue(oldGui.contains("render.add(new ModuleEntry(\"Inventory HUD\""))
        assertTrue(oldGui.contains("render.add(new ModuleEntry(\"Custom Scoreboard\""))
        assertTrue(renderTab.contains("new AccordionRow(0, 0, 0, HEADER_H, \"ESP\", new EspRow())"))
        assertTrue(qolTab.contains("Empty QoL tab"))
        assertTrue(oldGui.contains("render.add(new ModuleEntry(\"Attack Animation\""))
        assertTrue(oldGui.contains("render.add(new ModuleEntry(\"Stalk Player\""))
        assertTrue(oldGui.contains("render.add(new ModuleEntry(\"Instance Name\""))
        assertTrue(oldGui.contains("render.add(new ModuleEntry(\"GUI Style\""))
        assertTrue(oldGui.contains("player.add(new ModuleEntry(\"Neck Hider\""))
        assertTrue(oldGui.contains("player.add(new ModuleEntry(\"Player Size\""))
        assertTrue(oldGui.contains("player.add(new ModuleEntry(\"Cape\""))
        assertTrue(cosmeticTab.contains("\"Custom Skin\", skinBody"))
        assertTrue(cosmeticTab.contains("\"Custom Cape\", capeBody"))
        assertTrue(cosmeticTab.contains("\"Cone Hat\", coneHatBody"))
        assertTrue(qolTab.contains("private final ContentPane pane = new ContentPane(0, 0, 0, 0, \"QoL\");"))
        assertFalse(qolTab.contains("pane.add("), "Vendored Floyd QOL tab has no feature rows to port.")
        assertFalse(qolTab.contains("rows.add("), "Vendored Floyd QOL tab has no feature rows to port.")

        assertTrue(render.contains("val windowTitle by StringSetting(\"Instance Title\""))
        assertTrue(animations.contains("object FloydAnimations : Module("))
        assertTrue(animations.contains("name = \"Animations\""))
        assertTrue(animations.contains("val swingDuration by NumberSetting(\"Swing Duration\""))
        assertTrue(animations.contains("\"settings\" to mapOf("))
        assertTrue(animations.contains("\"hideHand\" to hidePlayerHand"))
        assertTrue(animations.contains("\"classicClick\" to classicClick"))
        assertTrue(mobEsp.contains("category = Category.RENDER"))
        assertTrue(mobEsp.contains("fun stalk(name: String)"))
        assertTrue(mobEsp.contains("private val stalkColor by ColorSetting(\"Tracer Color\""))
        assertTrue(mobEsp.contains("\"stalkChroma\" to stalkChroma"))
        assertTrue(hud.contains("category = Category.RENDER"))
        assertTrue(nickHider.contains("name = \"Neck Hider\""))
        assertTrue(nickHider.contains("category = Category.PLAYER"))
        assertTrue(nickHider.contains("\"nameMappings\" to nameMappings.toSortedMap(String.CASE_INSENSITIVE_ORDER)"))
        assertTrue(playerSize.contains("category = Category.PLAYER"))
        assertTrue(clickGuiModule.contains("val clickGUIColor by ColorSetting(\"Color\""))
        assertTrue(clickGuiModule.contains("val roundedPanelBottom by BooleanSetting(\"Rounded Panel Bottoms\""))
        assertTrue(skin.contains("name = \"Custom Skin\""))
        assertTrue(skin.contains("category = Category.COSMETIC"))
        assertTrue(cape.contains("name = \"Custom Cape\""))
        assertTrue(cape.contains("category = Category.COSMETIC"))
        assertTrue(coneHat.contains("category = Category.COSMETIC"))
        assertTrue(localControl.contains("\"mobEsp\" to FloydMobEsp.state()"))
        assertTrue(localControl.contains("\"hud\" to FloydHud.state()"))
        assertTrue(localControl.contains("root[\"qol\"] = emptyMap<String, Any?>()"))
        assertTrue(localControl.contains("\"skin\" to FloydSkin.state()"))
        assertTrue(localControl.contains("\"playerSize\" to FloydPlayerSize.state()"))
    }

    @Test
    fun `old Floyd GUI controls are represented by Odin settings or explicit retired styling`() {
        val oldLabels = oldFloydGuiControlLabels()
        val activeLabels = activeOdinControlLabels()
        val activeSource = textFiles(root.resolve("src/main/kotlin")).joinToString("\n") { Files.readString(it) }

        val representedRenames = mapOf(
            "Attack Animation" to listOf("name = \"Animations\"", "NumberSetting(\"Swing Duration\""),
            "Cape" to listOf("name = \"Custom Cape\""),
            "Custom Time" to listOf("BooleanSetting(\"Time Changer\"", "NumberSetting(\"Time\""),
            "Edit Blocks" to listOf("StringSetting(\"Opaque Block\"", "ActionSetting(\"Add Opaque Block\""),
            "Edit Filters" to listOf("StringSetting(\"Name Filter\"", "StringSetting(\"Type Filter\""),
            "Edit Layout" to listOf("ActionSetting(\"Open HUD Editor\"", "HUD(\"Inventory HUD\"", "HUD(\"Scoreboard HUD\""),
            "Edit Names" to listOf("fun addNameMapping", "fun removeNameMapping", "fun nameMappingsSummary"),
            "F5 Customizer" to listOf("NumberSetting(\"Camera Distance\""),
            "Instance Name" to listOf("StringSetting(\"Instance Title\""),
            "Mob Esp" to listOf("name = \"Mob ESP\""),
            "No Armor" to listOf("SelectorSetting(\"Target\"", "fun shouldHideArmorFor"),
            "Open Folder" to listOf("ActionSetting(\"Open Skin Folder\"", "ActionSetting(\"Open Cape Folder\"", "ActionSetting(\"Open Cone Folder\""),
            "Other Neck Hider" to listOf("MapSetting(\"Name Mappings\"", "fun addNameMapping"),
            "Reload Names" to listOf("\"reloadConfig\" -> ModuleManager.loadConfigurations()", "FloydSidecarConfig.loadExistingSidecars()"),
            "Starred Mobs" to listOf("BooleanSetting(\"Star Mobs\""),
            "Stalk Player" to listOf("fun stalk(name: String)", "ColorSetting(\"Tracer Color\""),
            "Window" to listOf("StringSetting(\"Instance Title\""),
            "Window Title" to listOf("StringSetting(\"Instance Title\"")
        )
        val restoredOldGuiStyleLabels = mapOf(
            "GUI Style" to listOf("Page.GUI_STYLE", "private fun drawGuiStylePage"),
            "Text Color" to listOf("Button Text Color", "Button Text Chroma", "Button Text Fade"),
            "Button Border Color" to listOf("Button Border Color", "Button Border Chroma", "Button Border Fade"),
            "GUI Border Color" to listOf("GUI Border Color", "GUI Border Chroma", "GUI Border Fade")
        )

        for ((label, requiredTokens) in representedRenames) {
            assertTrue(oldLabels.contains(label), "Vendored old GUI no longer exposes expected label: $label")
            for (token in requiredTokens) {
                assertTrue(activeSource.contains(token), "Active Odin source missing $label representation token: $token")
            }
        }
        for ((label, requiredTokens) in restoredOldGuiStyleLabels) {
            assertTrue(oldLabels.contains(label), "Vendored old GUI no longer exposes expected style label: $label")
            for (token in requiredTokens) {
                assertTrue(activeSource.contains(token), "Active Odin source missing restored $label token: $token")
            }
        }

        val coveredLabels = activeLabels + representedRenames.keys + restoredOldGuiStyleLabels.keys
        assertEquals(
            emptyList(),
            (oldLabels - coveredLabels).sorted(),
            "Every old Floyd GUI control label must be an active Odin label, an explicit rename, or a retired old-GUI style control."
        )
    }

    @Test
    fun `active Floyd module files mirror their Floyd GUI package groups`() {
        val expected = listOf(
            FloydModuleFile("render", "FloydRender", "RENDER"),
            FloydModuleFile("render", "FloydXray", "RENDER"),
            FloydModuleFile("render", "FloydAnimations", "RENDER"),
            FloydModuleFile("render", "FloydHud", "RENDER"),
            FloydModuleFile("render", "FloydMobEsp", "RENDER"),
            FloydModuleFile("hiders", "FloydHiders", "HIDERS"),
            FloydModuleFile("player", "FloydNickHider", "PLAYER"),
            FloydModuleFile("player", "FloydPlayerSize", "PLAYER"),
            FloydModuleFile("camera", "FloydCamera", "CAMERA"),
            FloydModuleFile("cosmetic", "FloydSkin", "COSMETIC"),
            FloydModuleFile("cosmetic", "FloydCape", "COSMETIC"),
            FloydModuleFile("cosmetic", "FloydConeHat", "COSMETIC"),
            FloydModuleFile("misc", "FloydDiscordPresence", "MISC"),
            FloydModuleFile("misc", "FloydLocalControl", "MISC"),
            FloydModuleFile("misc", "FloydCompatibility", "MISC"),
        )

        for (module in expected) {
            val path = root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/${module.packageGroup}/${module.objectName}.kt")
            assertTrue(Files.isRegularFile(path), "Expected ${module.objectName} under ${module.packageGroup}")
            val source = Files.readString(path)
            assertTrue(source.contains("package com.odtheking.odin.features.impl.${module.packageGroup}"))
            assertTrue(source.contains("object ${module.objectName} : Module("))
            assertTrue(source.contains("category = Category.${module.category}"))
        }
    }

    @Test
    fun `mixin config exactly references active source files`() {
        val mixin = JsonParser.parseString(Files.readString(root.resolve("src/main/resources/floydaddons.mixins.json"))).asJsonObject
        val listed = listOf("client", "mixins")
            .flatMap { section -> mixin.getAsJsonArray(section).map { it.asString } }
            .toSet()
        val sourceRoot = root.resolve("src/main/java/com/odtheking/mixin")
        val sources = Files.walk(sourceRoot).use { stream ->
            stream
                .filter { it.isRegularFile() && it.extension == "java" }
                .map { sourceRoot.relativize(it).toString().replace('\\', '/').removeSuffix(".java").replace('/', '.') }
                .toList()
                .toSet()
        }

        assertEquals(sources, listed)
    }

    @Test
    fun `active source and resources exclude old Odin gameplay and stale namespaces`() {
        val forbiddenSource = Regex(
            "odinclient|OdinClient|1\\.8\\.9|" +
                "features\\.impl\\.(boss|dungeon|nether|skyblock)|" +
                "utils\\.skyblock|hypixelapi|HypixelData|RequestUtils|WebSocketConnection|" +
                "CataCommand|DevCommand|DungeonWaypoint|PetCommand|PosMsg|Soopy|Termsim|WaypointCommand|" +
                "AutoSessionID|PersonalBest|ServerUtils|JsonResourceLoader|CustomGUIImpl|DrawContextRenderer|PlayerUtils|createSoundSettings|setClipboardContent|DevModule|isDevModule|" +
                "containsOneOf|equalsOneOf|matchesOneOf|capitalizeFirst|toFixed|formatTime|romanToInt|getBlockBounds|clickSlot|formatNumber|" +
                "sendChatMessage|getCenteredText|getChatBreak|Vec2|floorVec|isXZInterceptable|" +
                "Category\\.(DUNGEON|BOSS|NETHER|SKYBLOCK)|val (DUNGEON|BOSS|SKYBLOCK|NETHER)|" +
                "8Fqsg5xBP3|modmenu\\.discord|update_checker|OdinMod|com\\.odtheking\\.odin\\.OdinMod"
        )
        val textRoots = listOf(root.resolve("src/main/kotlin"), root.resolve("src/main/java"), root.resolve("src/main/resources"))
        val offenders = textRoots.flatMap { textFiles(it) }
            .filter { forbiddenSource.containsMatchIn(Files.readString(it)) }
            .map { root.relativize(it).toString() }

        assertEquals(emptyList(), offenders)
        assertFalse(Files.exists(root.resolve("src/main/kotlin/com/odtheking/odin/utils/network/hypixelapi")))
        assertFalse(Files.exists(root.resolve("src/main/kotlin/com/odtheking/odin/utils/ui/widget")))
        assertFalse(Files.exists(root.resolve("src/main/resources/assets/odin")))
        assertTrue(Files.isRegularFile(root.resolve("src/main/resources/assets/floydaddons/textures/gui/floydbg.png")))
    }

    @Test
    fun `legacy Floyd GUI skin is wired over active Odin modules`() {
        val legacyGui = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/clickgui/LegacyFloydClickGUI.kt"))
        val numberSetting = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/clickgui/settings/impl/NumberSetting.kt"))
        val command = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/commands/MainCommand.kt"))
        val localControl = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/features/impl/misc/FloydLocalControl.kt"))

        for (expected in listOf(
            "textures/gui/floydbg.png",
            "private const val hubPanelWidth = 480",
            "private const val hubPanelHeight = 270",
            "private const val fadeDurationMs = 120L",
            "private const val dragBarHeight = 22",
            "private const val chromaSegmentsPerEdge = 16",
            "labels = listOf(\"Cosmetic\", \"Render\", \"Neck Hider\", \"Camera\")",
            "private var currentPage = Page.HUB",
            "private var closeStartMs = 0L",
            "private var closing = false",
            "Page.CAPE -> 240",
            "Page.PLAYER_SIZE -> cosmeticPanelWidth",
            "Page.PLAYER_SIZE -> 220",
            "fun debugState(): Map<String, Any?>",
            "private fun debugPageRows(): List<Map<String, Any?>>",
            "\"label\" to row.row.label()",
            "private data class HitRow(val bounds: Rect, val label: String, val kind: RowKind, val action: (button: Int) -> Unit)",
            "private fun rowsFor(page: Page): List<LegacyRow>",
            "private enum class RowKind",
            "private enum class RowLayout",
            "private fun layoutRows(rows: List<LegacyRow>, contentLeft: Int, contentTop: Int, contentWidth: Int): List<LaidOutRow>",
            "private fun headerRow(label: String): LegacyRow",
            "drawSectionHeader(context, rect, row.label(), alpha)",
            "context.fill(left, top, right, bottom, applyAlpha(0xAA000000.toInt(), alpha))",
            "headerRow(\"X-Ray\")",
            "headerRow(\"Mob ESP\")",
            "headerRow(\"F5 Customizer\")",
            "headerRow(\"Colors\")",
            "headerRow(\"Mappings\")",
            "override fun keyPressed(keyEvent: KeyEvent): Boolean",
            "override fun charTyped(characterEvent: CharacterEvent): Boolean",
            "toggleSettingRow(FloydHiders, \"Server ID Hider\", \"Server ID Hider\")",
            "toggleSettingRow(FloydXray, \"Enabled\", \"X-Ray\")",
            "numberRow(FloydXray, \"Opacity\", \"X-Ray Opacity\")",
            "numberRow(FloydRender, \"Time\", \"Time\", RowLayout.RIGHT)",
            "toggleModuleRow(FloydMobEsp, \"Mob ESP\", RowLayout.LEFT)",
            "runtimeToggleRow(FloydCamera, \"Freecam\", \"Freecam\", RowLayout.LEFT)",
            "numberRow(FloydCamera, \"Speed\", \"Speed\", RowLayout.RIGHT)",
            "toggleSettingRow(FloydCamera, \"Disable Back Cam\", \"Disable Back\", RowLayout.RIGHT)",
            "numberRow(FloydCamera, \"Camera Distance\", \"F5 Distance\")",
            "numberRow(FloydAnimations, \"Swing Duration\", \"Swing Duration\")",
            "numberRow(FloydPlayerSize, \"X\", \"Size X\")",
            "private fun xrayEditorRows(): List<LegacyRow>",
            "private fun openXrayBlockEditor()",
            "private fun drawXrayEditorPage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float)",
            "FloydXray.validOpaqueBlockId(value.trim())",
            "FloydXray.removeOpaqueBlock(id)",
            "FloydXray.addOpaqueBlock(id)",
            "private fun nameMappingRows(): List<LegacyRow>",
            "private fun drawNameMappingInlineAdd(",
            "NameMappingHitKind.SAVE_ADD -> saveInlineNameMappingAdd()",
            "private fun saveInlineNameMappingAdd()",
            "FloydNickHider.addNameMapping(real, fake)",
            "private fun openDefaultNickEditor()",
            "private fun openMappingRealEditor()",
            "actionRow(\"Add Mapping...\")",
            "FloydNickHider.addNameMapping(real, value.trim())",
            "FloydNickHider.addNameMapping(real, FloydNickHider.nickname)",
            "FloydNickHider.removeNameMapping(real)",
            "private fun nearbyBlockSuggestions(): List<String>",
            "private fun onlinePlayerSuggestions(): List<String>",
            "toggleSettingRow(FloydNickHider, \"Enabled\", \"Nick Hider\")",
            "toggleSettingRow(FloydSkin, \"Custom Skin\", \"Custom Skin\", RowLayout.LEFT)",
            "ModuleManager.saveConfigurations()",
            "drawStretchBackground(context",
            "drawChromaBorder(context",
            "drawCentralLabels(context",
            "mc.setScreen(HudManager)",
            "mc.setScreen(ClickGUI)",
            "drawButton(context, v2Button, \"Open V2 UI\", alpha)",
            "currentPage = Page.GUI_STYLE",
            "Page.CLICK_GUI -> \"ClickGUI\"",
            "private const val moduleBrowserSearchWidth = 200",
            "private fun drawModuleBrowserSearch(context: GuiGraphics, alpha: Float)",
            "private fun handleModuleBrowserChromeClick(x: Double, y: Double, button: Int): Boolean",
            "private fun moduleBrowserEntries(category: Category): List<LegacyModuleBrowserEntry>",
            "private fun moduleBrowserModules(category: Category): List<Module>",
            "private fun legacyFloydModuleLabel(module: Module): String",
            "FloydCape -> \"Cape\"",
            "\"displayName\" to it.entry.label",
            "\"legacyKind\" to it.entry.kind.name",
            "private fun legacyFloydEntriesFor(category: Category): List<LegacyModuleBrowserEntry>",
            "moduleEntry(FloydXray)",
            "moduleEntry(FloydMobEsp)",
            "LegacyModuleBrowserEntry(FloydHiders, \"Profile ID Hider\", LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN, \"Profile ID Hider\")",
            "LegacyModuleBrowserEntry(FloydHiders, \"Server ID Hider\", LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN, \"Server ID Hider\")",
            "LegacyModuleBrowserEntry(FloydRender, \"Time Changer\", LegacyModuleBrowserKind.RENDER_BOOLEAN, \"Time Changer\")",
            "LegacyModuleBrowserEntry(FloydMobEsp, \"Stalk Player\", LegacyModuleBrowserKind.RENDER_STALK)",
            "LegacyModuleBrowserEntry(FloydHud, \"Inventory HUD\", LegacyModuleBrowserKind.RENDER_HUD, \"Inventory HUD\")",
            "LegacyModuleBrowserEntry(FloydRender, \"Custom Scoreboard\", LegacyModuleBrowserKind.RENDER_BOOLEAN, \"Custom Scoreboard\")",
            "LegacyModuleBrowserEntry(FloydRender, \"Borderless Window\", LegacyModuleBrowserKind.RENDER_BORDERLESS, \"Borderless Window\")",
            "LegacyModuleBrowserEntry(FloydRender, \"Instance Name\", LegacyModuleBrowserKind.RENDER_INSTANCE_NAME, \"Instance Title\")",
            "LegacyModuleBrowserEntry(ClickGUIModule, \"GUI Style\", LegacyModuleBrowserKind.RENDER_GUI_STYLE)",
            "LegacyModuleBrowserEntry(FloydAnimations, \"Attack Animation\", LegacyModuleBrowserKind.RENDER_ANIMATIONS)",
            "LegacyModuleBrowserEntry(FloydCamera, \"Freecam\", LegacyModuleBrowserKind.CAMERA_FREECAM)",
            "LegacyModuleBrowserEntry(FloydCamera, \"Freelook\", LegacyModuleBrowserKind.CAMERA_FREELOOK)",
            "LegacyModuleBrowserEntry(FloydCamera, \"F5 Customizer\", LegacyModuleBrowserKind.CAMERA_F5)",
            "hiderEntry(\"No Hurt Camera\")",
            "hiderEntry(\"Remove Fire Overlay\")",
            "hiderEntry(\"Disable Hunger Bar\")",
            "hiderEntry(\"Hide Potion Effects\")",
            "hiderEntry(\"3rd Person Crosshair\")",
            "hiderEntry(\"Hide Entity Fire\")",
            "hiderEntry(\"Disable Arrows\")",
            "hiderEntry(\"Remove Falling Blocks\")",
            "hiderEntry(\"No Explosion Particles\")",
            "hiderEntry(\"Remove Tab Ping\")",
            "LegacyModuleBrowserEntry(FloydHiders, \"No Armor\", LegacyModuleBrowserKind.HIDER_NO_ARMOR, \"Target\")",
            "private fun hiderEntry(settingName: String): LegacyModuleBrowserEntry",
            "private fun legacyModuleBrowserSettingsPage(entry: LegacyModuleBrowserEntry): Page?",
            "private fun handleLegacyModuleBrowserPrimaryClick(entry: LegacyModuleBrowserEntry)",
            "LegacyModuleBrowserKind.HIDER_BOOLEAN",
            "LegacyModuleBrowserKind.HIDER_NO_ARMOR",
            "LegacyModuleBrowserKind.RENDER_HIDER_BOOLEAN",
            "LegacyModuleBrowserKind.RENDER_BOOLEAN",
            "LegacyModuleBrowserKind.RENDER_STALK",
            "LegacyModuleBrowserKind.RENDER_HUD",
            "LegacyModuleBrowserKind.RENDER_BORDERLESS",
            "LegacyModuleBrowserKind.RENDER_INSTANCE_NAME",
            "LegacyModuleBrowserKind.RENDER_GUI_STYLE",
            "LegacyModuleBrowserKind.RENDER_ANIMATIONS",
            "FloydMobEsp.stopStalk()",
            "FloydRender.setBorderlessWindowed(next, force = true)",
            "private fun hudSetting(module: Module, name: String): HUDSetting?",
            "listOf(Category.RENDER, Category.HIDERS, Category.PLAYER, Category.CAMERA)",
            "LegacyModuleBrowserEntry(FloydCape, \"Cape\", LegacyModuleBrowserKind.PLAYER_CAPE, \"Enabled\")",
            "LegacyModuleBrowserEntry(FloydConeHat, \"Cone Hat\", LegacyModuleBrowserKind.PLAYER_CONE_HAT, \"Enabled\")",
            "LegacyModuleBrowserEntry(FloydNickHider, \"Neck Hider\", LegacyModuleBrowserKind.PLAYER_NICK_HIDER, \"Enabled\")",
            "LegacyModuleBrowserEntry(FloydSkin, \"Custom Skin\", LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN, \"Custom Skin\")",
            "LegacyModuleBrowserEntry(FloydPlayerSize, \"Player Size\", LegacyModuleBrowserKind.PLAYER_SIZE)",
            "LegacyModuleBrowserKind.PLAYER_CAPE",
            "LegacyModuleBrowserKind.PLAYER_CONE_HAT",
            "LegacyModuleBrowserKind.PLAYER_NICK_HIDER",
            "LegacyModuleBrowserKind.PLAYER_CUSTOM_SKIN",
            "LegacyModuleBrowserKind.PLAYER_SIZE",
            "FloydPlayerSize.togglePlayerSize()",
            "val isPlayerVirtual: Boolean",
            "val isCameraVirtual: Boolean",
            "numberSetting(FloydCamera, \"Speed\")",
            "numberSetting(FloydCamera, \"Distance\")",
            "booleanSetting(FloydCamera, \"Disable Front Cam\")",
            "numberSetting(FloydCamera, \"Camera Distance\")",
            "private fun normalizedModuleBrowserSearch(value: String): String",
            "private data class LegacyModuleBrowserEntry",
            "private enum class LegacyModuleBrowserKind",
            "private data class ModuleBrowserPanelState",
            "private data class ModuleBrowserHeaderHitEntry",
            "moduleBrowserSearchFocused",
            "moduleBrowserSearchQuery",
            "private const val moduleBrowserMaxPanelContentHeight = 240",
            "state.collapsed = !state.collapsed",
            "draggingModuleBrowserCategory = header.category",
            "private fun drawModulePopup(context: GuiGraphics, alpha: Float, popup: ModulePopup)",
            "private fun drawModulePopupPlayerPreview(context: GuiGraphics, alpha: Float, popup: ModulePopup)",
            "InventoryScreen.renderEntityInInventoryFollowsMouse",
            "\"expandedExtraScroll\" to (expandedModulePopupExtra?.let { modulePopupExtraScroll(it) })",
            "private fun modulePopupHasPlayerPreview(popup: ModulePopup): Boolean",
            "\"playerPreviewBounds\" to rectState(modulePopupPlayerPreviewBounds(popup))",
            "private fun modulePopupPlayerPreviewBounds(popup: ModulePopup): Rect",
            "private fun isSameModulePopupTarget(popup: ModulePopup, module: Module, entry: LegacyModuleBrowserEntry?): Boolean",
            "if (isSameModulePopupTarget(current, module, entry))",
            "closeModulePopup()",
            "activeModulePopupActionInput = null",
            "modulePopupMappingFocusedField = null",
            "if (popup.bounds.bottom > height)",
            "popup.bounds = popup.bounds.copy(top = max(4, height - popup.bounds.height - 4))",
            "private fun handleModulePopupClick(x: Double, y: Double, button: Int): Boolean",
            "if (button != 0) return true",
            "if (popup.bounds.contains(mouseX, mouseY))",
            "modulePopupExtraScrolls[extra] = next.coerceIn(0, maxScroll)",
            "private fun modulePopupBoundsContains(x: Double, y: Double): Boolean",
            "private const val modulePopupRowHeight = 18",
            "private const val modulePopupFilterMaxVisibleHeight = 180",
            "private const val modulePopupFilterEntryHeight = 16",
            "private fun closeModulePopup()",
            "private fun finishModulePopupStringEdit()",
            "!modulePopupBoundsContains(point.first, point.second)",
            "private fun openModulePopup(module: Module, rowBounds: Rect, entry: LegacyModuleBrowserEntry? = null)",
            "private fun modulePopupContentAvailable(entry: LegacyModuleBrowserEntry): Boolean",
            "private fun popupVisibleSettings(entry: LegacyModuleBrowserEntry): List<Setting<*>>",
            "private fun modulePopupTitle(popup: ModulePopup): String",
            "private fun modulePopupSettingLabel(popup: ModulePopup, setting: Setting<*>): String",
            "private fun modulePopupExtras(entry: LegacyModuleBrowserEntry): List<ModulePopupExtra>",
            "private fun modulePopupExtrasBeforeSettings(popup: ModulePopup): Boolean",
            "private fun modulePopupExtraVisibleHeight(kind: ModulePopupExtraKind, totalRows: Int): Int",
            "min(modulePopupFilterMaxVisibleHeight, scrollableRows * modulePopupFilterEntryHeight)",
            "private fun modulePopupPinnedRows(kind: ModulePopupExtraKind): Int",
            "private fun modulePopupExtraScrollableRowLimit(kind: ModulePopupExtraKind, pinnedRows: Int): Int",
            "(modulePopupFilterMaxVisibleHeight + modulePopupFilterEntryHeight - 1) / modulePopupFilterEntryHeight",
            "rowHeight: Int = mobFilterEntryHeight",
            "buttonHeight: Int = mobFilterButtonHeight",
            "private const val modulePopupFilterMinWidth = 250",
            "private fun modulePopupHasFilterContent(popup: ModulePopup): Boolean",
            "val minWidth = if (modulePopupHasFilterContent(popup)) modulePopupFilterMinWidth else modulePopupMinWidth",
            ".maxOfOrNull { mc.font.width(it) + 80 }",
            "return max(maxLabel, minWidth)",
            "LegacyModuleBrowserKind.MODULE ->",
            "numberSetting(FloydXray, \"Opacity\")",
            "colorSetting(FloydMobEsp, \"Default ESP Color\")",
            "val next = !FloydXray.isActive()",
            "FloydXray.xrayEnabled = next",
            "FloydXray.rebuildChunks()",
            "FloydMobEsp.toggle()",
            "FloydMobEsp -> FloydMobEsp.enabled",
            "hit.entry.kind == LegacyModuleBrowserKind.MODULE && modulePopupContentAvailable(hit.entry)",
            "LegacyModuleBrowserKind.RENDER_HUD ->",
            "LegacyModuleBrowserKind.RENDER_BORDERLESS ->",
            "colorSetting(ClickGUIModule, \"Button Text Color\")",
            "\"Button Text Color\" -> \"Text Color\"",
            "moduleAction(FloydCape, \"Open Cape Folder\")",
            "moduleAction(FloydConeHat, \"Open Cone Folder\")",
            "moduleAction(FloydSkin, \"Open Skin Folder\")",
            "ModulePopupHitKind.CYCLE_STRING",
            "private fun legacyCycleStringOptions(setting: StringSetting): List<String>?",
            "FloydCape.availableCapeFiles()",
            "FloydConeHat.availableImageFiles()",
            "FloydSkin.availableSkinFiles()",
            "private fun cycleLegacyStringSetting(setting: StringSetting)",
            "private fun modulePopupActionQuery(vararg kinds: ModulePopupExtraKind): String",
            "private fun Iterable<String>.filterModulePopupQuery(query: String): List<String>",
            "resetModulePopupActionScroll(it)",
            "private fun resetModulePopupActionScroll(kind: ModulePopupExtraKind)",
            "private fun resetModulePopupNameMappingScroll()",
            ".filter { (real, _) -> query.isBlank() || real.contains(query, ignoreCase = true) }",
            ".filter { name -> query.isBlank() || name.contains(query, ignoreCase = true) }",
            "private fun stalkTargetSuggestions(): List<String>",
            "mappings[name]?.contains(query, ignoreCase = true) == true",
            "ModulePopupExtraKind.STALK_TARGET -> 1 + stalkTargetSuggestions().size",
            "selectorSetting(FloydHiders, \"Target\")",
            "selectorSetting(FloydPlayerSize, \"Target\")",
            "\"Button Text Color\" -> booleanSetting(ClickGUIModule, \"Button Text Chroma\")",
            "ModulePopupExtra(\"Target: ${'$'}{FloydMobEsp.stalkTarget().ifBlank { \"<none>\" }}\", ModulePopupExtraKind.STALK_TARGET)",
            "private fun openStalkTargetEditor()",
            "if (value.isBlank()) FloydMobEsp.stopStalk() else FloydMobEsp.stalk(value.trim())",
            "private fun popupVisibleSettings(module: Module): List<Setting<*>>",
            "activeModulePopupSlider",
            "private fun modulePopupSliderBounds(row: Rect): Rect",
            "private fun updateModulePopupSlider(target: ModulePopupSliderTarget, x: Double)",
            "expandedModulePopupColor",
            "activeModulePopupColorDrag",
            "activeModulePopupString",
            "modulePopupStringBuffer",
            "private const val modulePopupColorSvSize = 100",
            "private fun drawModulePopupInlineColorPicker(context: GuiGraphics, setting: ColorSetting, row: Rect, alpha: Float)",
            "private fun modulePopupInlineColorSvBounds(row: Rect): Rect",
            "private fun modulePopupInlineColorHueBounds(row: Rect): Rect",
            "modulePopupInlineColorSvBounds(row).let { sv -> Rect(sv.left, sv.bottom + 6, 16, 16) }",
            "private fun modulePopupInlineColorChromaBounds(row: Rect): Rect",
            "private fun modulePopupInlineColorFadeBounds(row: Rect): Rect",
            "private fun modulePopupInlineColorEditFadeBounds(row: Rect): Rect",
            "private fun updateModulePopupColorDrag(x: Double, y: Double)",
            "private fun modulePopupChromaSetting(setting: ColorSetting): BooleanSetting?",
            "private fun modulePopupFadeColorSetting(setting: ColorSetting): ColorSetting?",
            "private fun modulePopupFadeToggleSetting(setting: ColorSetting): BooleanSetting?",
            "private fun modulePopupDebugValue(setting: Setting<*>): Any?",
            "private fun modulePopupDebugOptions(setting: Setting<*>): List<String>?",
            "private fun modulePopupDebugChroma(setting: Setting<*>): Boolean?",
            "private fun modulePopupDebugFade(setting: Setting<*>): Boolean?",
            "setting.selectedOption()",
            "\"options\" to modulePopupDebugOptions(it.setting)",
            "\"chroma\" to modulePopupDebugChroma(it.setting)",
            "\"fade\" to modulePopupDebugFade(it.setting)",
            "module === FloydMobEsp && setting.name in setOf(\"Default Chroma\", \"Stalk Chroma\")",
            "private fun modulePopupExtras(module: Module): List<ModulePopupExtra>",
            "setting is KeybindSetting",
            "setting is MapSetting<*, *, *>",
            "setting is HUDSetting",
            "private fun handleModulePopupExtra(kind: ModulePopupExtraKind)",
            "ModulePopupExtra(\"Edit Blocks\", ModulePopupExtraKind.XRAY_BLOCKS)",
            "ModulePopupExtra(\"Edit Filters\", ModulePopupExtraKind.MOB_FILTERS)",
            "ModulePopupExtra(\"Edit Names\", ModulePopupExtraKind.NAME_MAPPINGS)",
            "ModulePopupExtra(\"Edit Layout\", ModulePopupExtraKind.HUD_LAYOUT)",
            "private data class ModulePopup",
            "private data class ModulePopupHitEntry",
            "private data class ModulePopupSliderTarget",
            "private data class ModulePopupStringTarget",
            "private data class ModulePopupExtra",
            "private data class ModulePopupExtraHitEntry",
            "private enum class ModulePopupHitKind",
            "private enum class ModulePopupExtraKind",
            "private fun drawGuiStylePage(context: GuiGraphics, left: Int, top: Int, bottom: Int, alpha: Float)",
            "openStyleColorEditor(hit.target)",
            "drawButton(context, colorPickerFadeButton(), \"Fade: \${onOff(picker.fadeEnabled)}\", alpha)",
            "drawButton(context, colorPickerEditTargetButton(), if (picker.editingFade) \"Editing: Fade\" else \"Editing: Base\", alpha)",
            "override fun onClose()",
            "if (currentPage == Page.CLICK_GUI) saveModuleBrowserPanelStates()",
            "closing = true",
            "closeStartMs = System.currentTimeMillis()",
            "override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float)",
            "private fun toggleModulePopupChroma(setting: ColorSetting)",
            "if (enabled) modulePopupFadeToggleSetting(setting)?.enabled = false",
            "private fun toggleModulePopupFade(setting: ColorSetting)",
            "if (enabled) modulePopupChromaSetting(setting)?.enabled = false",
            "if (modulePopupChromaSetting(setting)?.enabled == true) return",
            "private var expandedModulePopupExtra: ModulePopupExtraKind? = null",
            "private const val moduleBrowserPanelTop = 30",
            "private const val moduleBrowserPanelMinWidth = 100",
            "private val legacyClickGuiPanelConfigPath: Path",
            "private fun loadModuleBrowserPanelStates()",
            "private fun saveModuleBrowserPanelStates()",
            "Files.writeString(legacyClickGuiPanelConfigPath",
            "saveModuleBrowserPanelStates()",
            "if (currentPage == Page.CLICK_GUI)",
            "context.fill(0, 0, width, height, applyAlpha(0x88000000.toInt(), alpha))",
            "drawButtonBorder(context, state.x - 1, state.y - 1, right + 1, bottom + 1, alpha)",
            "drawButtonBorder(context, rect.left - 1, rect.top - 1, right + 1, bottom + 1, alpha)",
            "private fun drawModuleBrowserBottomTitle(context: GuiGraphics, alpha: Float)",
            "val textScale = 2.0f",
            "context.pose().translate(titleX, titleY)",
            "private fun moduleBrowserDefaultPanelX(index: Int, panelWidth: Int): Int",
            "(10 + index * 210).coerceIn(0, max(0, width - panelWidth))",
            "private fun moduleBrowserPanelWidth(category: Category): Int",
            ".maxOfOrNull { mc.font.width(it) }",
            "return max(moduleBrowserPanelMinWidth, maxTextWidth + 30)",
            "private fun drawModulePopupPlayerPicker(",
            "private fun handleModulePopupPlayerPick(hit: ModulePopupPlayerHitEntry)",
            "FloydMobEsp.stalk(playerName)",
            "private data class ModulePopupPlayerHitEntry",
            "private fun drawModulePopupActionInput(",
            "val addButton = Rect(row.right - 22",
            "if (hit.submit) submitModulePopupActionInput(ModulePopupExtraKind.STALK_TARGET)",
            "private fun drawModulePopupXrayBlocks(",
            "private fun handleModulePopupXrayBlock(hit: ModulePopupXrayHitEntry)",
            "private data class ModulePopupXrayHitEntry",
            "private fun drawModulePopupMobFilters(",
            "private fun handleModulePopupMobFilter(hit: MobFilterHitEntry, x: Double, y: Double)",
            "modulePopupMobFilterHitEntries = mobHits",
            "private fun drawModulePopupNameMappings(",
            "private fun handleModulePopupNameMapping(hit: NameMappingHitEntry, x: Double, y: Double)",
            "modulePopupNameMappingHitEntries = nameHits",
            "ModulePopupExtraKind.XRAY_ADD_BLOCK -> xrayHits?.add",
            "MobFilterHitKind.ADD_MANUAL_NAME ->",
            "MobFilterHitKind.ADD_MANUAL_TYPE ->",
            "NameMappingHitKind.ADD_MANUAL -> openMappingRealEditor()",
            "private fun openMobFilterNameEditor()",
            "private fun openMobFilterTypeEditor()",
            "actionRow(\"Reload Blocks\", RowLayout.RIGHT)",
            "FloydXray.rebuildChunks()",
            "private fun stalkRow(): LegacyRow",
            "FloydMobEsp.stopStalk()",
            "private fun openWindowTitleEditor()",
            "stringSetting(FloydRender, \"Instance Title\")",
            "actionRow(\"Reload Names\")",
            "colorRow(FloydMobEsp, \"Default ESP Color\", \"Default ESP Color\", RowLayout.LEFT)",
            "colorRow(FloydMobEsp, \"Tracer Color\", \"Stalk Tracer Color\", RowLayout.LEFT)",
            "toggleSettingRow(FloydMobEsp, \"Stalk Chroma\", \"Chroma\", RowLayout.RIGHT)",
            "navRow(\"Edit Filters\", Page.MOB_ESP_FILTERS)",
            "Page.MOB_ESP_FILTERS -> \"Mob ESP Filters\"",
            "skinSelectionRow()",
            "selectorRow(FloydPlayerSize, \"Target\", \"Target\", listOf(\"Self\", \"Real Players\", \"All\"))",
            "private fun openColorEditor(module: Module, settingName: String, title: String)",
            "private fun drawColorPicker(context: GuiGraphics, alpha: Float, picker: ColorPickerEditor)",
            "private fun drawColorPickerSv(context: GuiGraphics, picker: ColorPickerEditor, alpha: Float)",
            "private fun handleColorPickerClick(picker: ColorPickerEditor, x: Double, y: Double): Boolean",
            "private fun applyColorPicker(picker: ColorPickerEditor)",
            "private fun cancelColorPicker(picker: ColorPickerEditor)",
            "setting.value = Color(picker.currentColor())",
            "InventoryScreen.renderEntityInInventoryFollowsMouse(",
            "private data class ColorPickerEditor",
            "override fun mouseMoved(mouseX: Double, mouseY: Double)",
            "if (!hasTransientMouseOverride()) clearMouseOverride()",
            "private data class TextEditor",
        )) {
            assertTrue(legacyGui.contains(expected), "Legacy Floyd GUI missing token: $expected")
        }
        assertTrue(!legacyGui.contains("\"Player preview\""), "Cone Hat config must render the old player preview, not placeholder text")
        assertTrue(numberSetting.contains("fun stepNumeric(direction: Int = 1)"))
        assertTrue(numberSetting.contains("fun minNumericValue(): Double"))
        assertTrue(numberSetting.contains("fun maxNumericValue(): Double"))
        val selectorSetting = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/clickgui/settings/impl/SelectorSetting.kt"))
        assertTrue(selectorSetting.contains("fun selectedOption(): String = selected"))
        assertTrue(selectorSetting.contains("fun optionLabels(): List<String> = options.toList()"))
        assertTrue(command.contains("runs {\n        schedule(0) { mc.setScreen(LegacyFloydClickGUI.openHub()) }"))
        assertTrue(command.contains("literal(\"clickgui\")"))
        assertTrue(command.contains("literal(\"legacygui\")"))
        assertTrue(command.contains("literal(\"oldgui\")"))
        assertTrue(command.contains("mc.setScreen(LegacyFloydClickGUI.openHub())"))
        assertTrue(localControl.contains("\"floyd\", \"floydaddons\", \"legacy\", \"legacygui\", \"oldgui\" -> mc.setScreen(LegacyFloydClickGUI.openHub())"))
        assertTrue(localControl.contains("\"/type\""))
        assertTrue(localControl.contains("LegacyFloydClickGUI.debugState()"))
        assertTrue(localControl.contains("private fun handleType(exchange: HttpExchange)"))
        assertTrue(localControl.contains("currentScreen.charTyped(CharacterEvent(codepoint, 0))"))
    }

    @Test
    fun `legacy module browser popups expose old Floyd module controls`() {
        val legacyGui = Files.readString(root.resolve("src/main/kotlin/com/odtheking/odin/clickgui/LegacyFloydClickGUI.kt"))

        val expectedPopupTokens = listOf(
            "FloydXray ->\n                        listOfNotNull(numberSetting(FloydXray, \"Opacity\"))",
            "FloydMobEsp ->\n                        listOfNotNull(\n                            booleanSetting(FloydMobEsp, \"Tracers\")",
            "booleanSetting(FloydMobEsp, \"Hitboxes\")",
            "booleanSetting(FloydMobEsp, \"Star Mobs\")",
            "colorSetting(FloydMobEsp, \"Default ESP Color\")",
            "\"Time Changer\" -> listOfNotNull(numberSetting(FloydRender, \"Time\"))",
            "LegacyModuleBrowserKind.RENDER_STALK ->\n                listOfNotNull(colorSetting(FloydMobEsp, \"Tracer Color\"))",
            "LegacyModuleBrowserKind.RENDER_INSTANCE_NAME ->\n                listOfNotNull(stringSetting(FloydRender, \"Instance Title\"))",
            "colorSetting(ClickGUIModule, \"Button Text Color\")",
            "colorSetting(ClickGUIModule, \"Button Border Color\")",
            "colorSetting(ClickGUIModule, \"GUI Border Color\")",
            "LegacyModuleBrowserKind.RENDER_ANIMATIONS ->\n                popupVisibleSettings(FloydAnimations)",
            "LegacyModuleBrowserKind.HIDER_NO_ARMOR ->\n                listOfNotNull(selectorSetting(FloydHiders, \"Target\"))",
            "stringSetting(FloydCape, \"Image\")",
            "moduleAction(FloydCape, \"Open Cape Folder\")",
            "numberSetting(FloydConeHat, \"Height\")",
            "numberSetting(FloydConeHat, \"Radius\")",
            "numberSetting(FloydConeHat, \"Y Offset\")",
            "numberSetting(FloydConeHat, \"Rotation\")",
            "numberSetting(FloydConeHat, \"Spin Speed\")",
            "stringSetting(FloydConeHat, \"Image\")",
            "moduleAction(FloydConeHat, \"Open Cone Folder\")",
            "LegacyModuleBrowserKind.PLAYER_NICK_HIDER ->\n                listOfNotNull(stringSetting(FloydNickHider, \"Default Nick\"))",
            "booleanSetting(FloydSkin, \"Self\")",
            "booleanSetting(FloydSkin, \"Others\")",
            "stringSetting(FloydSkin, \"Skin\")",
            "moduleAction(FloydSkin, \"Open Skin Folder\")",
            "selectorSetting(FloydPlayerSize, \"Target\")",
            "numberSetting(FloydPlayerSize, \"X\")",
            "numberSetting(FloydPlayerSize, \"Y\")",
            "numberSetting(FloydPlayerSize, \"Z\")",
            "LegacyModuleBrowserKind.CAMERA_FREECAM ->\n                listOfNotNull(numberSetting(FloydCamera, \"Speed\"))",
            "LegacyModuleBrowserKind.CAMERA_FREELOOK ->\n                listOfNotNull(numberSetting(FloydCamera, \"Distance\"))",
            "booleanSetting(FloydCamera, \"Disable Front Cam\")",
            "booleanSetting(FloydCamera, \"Disable Back Cam\")",
            "booleanSetting(FloydCamera, \"No Third-Person Clipping\")",
            "booleanSetting(FloydCamera, \"Scrolling Changes Distance\")",
            "booleanSetting(FloydCamera, \"Reset F5 Scrolling\")",
            "numberSetting(FloydCamera, \"Camera Distance\")",
            "FloydXray -> listOf(ModulePopupExtra(\"Edit Blocks\", ModulePopupExtraKind.XRAY_BLOCKS))",
            "FloydMobEsp -> listOf(ModulePopupExtra(\"Edit Filters\", ModulePopupExtraKind.MOB_FILTERS))",
            "ModulePopupExtra(\"Edit Names\", ModulePopupExtraKind.NAME_MAPPINGS)",
            "ModulePopupExtra(\"Reload Names\", ModulePopupExtraKind.RELOAD_NAMES)",
            "FloydHud -> listOf(ModulePopupExtra(\"Edit Layout\", ModulePopupExtraKind.HUD_LAYOUT))",
            "listOf(ModulePopupExtra(\"Target: ${'$'}{FloydMobEsp.stalkTarget().ifBlank { \"<none>\" }}\", ModulePopupExtraKind.STALK_TARGET))",
            "if (entry.label in setOf(\"Inventory HUD\", \"Custom Scoreboard\")) listOf(ModulePopupExtra(\"Edit Layout\", ModulePopupExtraKind.HUD_LAYOUT)) else emptyList()",
            "\"Open Cape Folder\" -> \"Open Folder\"",
            "\"Open Cone Folder\" -> \"Open Folder\"",
            "\"Open Skin Folder\" -> \"Open Folder\"",
            "\"Button Text Color\" -> \"Text Color\"",
            "\"Default ESP Color\" -> booleanSetting(FloydMobEsp, \"Default Chroma\")",
            "\"Tracer Color\" -> booleanSetting(FloydMobEsp, \"Stalk Chroma\")",
        )

        for (token in expectedPopupTokens) {
            assertTrue(legacyGui.contains(token), "Legacy Floyd module browser popup missing old control token: $token")
        }
    }

    @Test
    fun `active resources are Floyd namespace plus retained Odin scaffold assets only`() {
        val resourcesRoot = root.resolve("src/main/resources")
        val resources = Files.walk(resourcesRoot).use { stream ->
            stream
                .filter { it.isRegularFile() }
                .map { resourcesRoot.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }

        assertEquals(
            listOf(
                "assets/floydaddons/HueGradient.png",
                "assets/floydaddons/MovementIcon.svg",
                "assets/floydaddons/chevron.svg",
                "assets/floydaddons/font.ttf",
                "assets/floydaddons/fonts/inter_regular.ttf",
                "assets/floydaddons/fonts/inter_semibold.ttf",
                "assets/floydaddons/icons/taskbar_icon_128x128.png",
                "assets/floydaddons/icons/taskbar_icon_16x16.png",
                "assets/floydaddons/icons/taskbar_icon_32x32.png",
                "assets/floydaddons/icons/taskbar_icon_48x48.png",
                "assets/floydaddons/lang/en_us.json",
                "assets/floydaddons/shaders/core/round_rect.fsh",
                "assets/floydaddons/shaders/core/round_rect.vsh",
                "assets/floydaddons/shaders/pipeline/round_rect.json",
                "assets/floydaddons/textures/cape/default_cape.png",
                "assets/floydaddons/textures/entity/cone.png",
                "assets/floydaddons/textures/gui/floydbg.png",
                "assets/floydaddons/textures/skin/custom.png",
                "fabric.mod.json",
                "floydaddons.mixins.json",
            ),
            resources
        )
    }

    private fun textFiles(directory: Path): List<Path> =
        Files.walk(directory).use { stream ->
            stream
                .filter { it.isRegularFile() }
                .filter { it.name != "ScaffoldAuditTest.kt" }
                .filter { it.extension in setOf("kt", "java", "json", "kts", "properties", "md") }
                .toList()
        }

    private fun oldFloydGuiControlLabels(): Set<String> {
        val files = listOf(
            "vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/ClickGuiScreen.java",
            "vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/row/AnimationsRow.java",
            "vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/row/EspRow.java",
            "vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/row/HidersRow.java",
            "vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/row/VisualRow.java",
            "vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/row/XrayRow.java",
            "vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/tab/CosmeticTab.java",
            "vendor/floydaddons-fabric/app/src/main/java/floydaddons/not/dogshit/client/gui/v2/tab/NeckHiderTab.java",
        )
        val patterns = listOf(
            Regex("""new ModuleEntry\("([^"]+)""""),
            Regex("""new ToggleEntry\("([^"]+)""""),
            Regex("""new AccordionRow\([^\n]*?"([^"]+)""""),
            Regex("""new ModuleEntry\.[A-Za-z]+Setting\("([^"]+)""""),
            Regex("""drawLabel\(ctx, "([^"]+)""""),
            Regex("""drawSectionHeader\(ctx, "([^"]+)""""),
        )
        return files.flatMap { relative ->
            val source = Files.readString(root.resolve(relative))
            patterns.flatMap { pattern -> pattern.findAll(source).map { it.groupValues[1] }.toList() }
        }.toSet()
    }

    private fun activeOdinControlLabels(): Set<String> {
        val patterns = listOf(
            Regex("""name = "([^"]+)""""),
            Regex("""(?:BooleanSetting|NumberSetting|StringSetting|SelectorSetting|ColorSetting|KeybindSetting|ActionSetting|HUD|MapSetting)\("([^"]+)""""),
        )
        return textFiles(root.resolve("src/main/kotlin/com/odtheking/odin/features")).flatMap { path ->
            val source = Files.readString(path)
            patterns.flatMap { pattern -> pattern.findAll(source).map { it.groupValues[1] }.toList() }
        }.toSet()
    }

    private data class FloydModuleFile(val packageGroup: String, val objectName: String, val category: String)
}
