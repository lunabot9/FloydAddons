package com.odtheking.odin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveHypixelVerificationScriptTest {
    private val root = Path.of("").toAbsolutePath()

    @Test
    fun `live Hypixel verifier uses only local-control proof surfaces`() {
        val script = Files.readString(root.resolve("scripts/verify-live-hypixel-acquisition.py"))
        val scriptTest = Files.readString(root.resolve("scripts/test-live-hypixel-verifier.py"))
        val gate = Files.readString(root.resolve("scripts/verify-floyd-in-odin.sh"))
        val readme = Files.readString(root.resolve("README.md"))
        val liveProof = Files.readString(root.resolve("LIVE_PROOF.md"))

        assertTrue(script.contains("DEFAULT_CONFIG_CANDIDATES"))
        assertTrue(script.contains("config/floydaddons/control-bridge.json"))
        assertTrue(script.contains("run/config/floydaddons/control-bridge.json"))
        assertTrue(script.contains("Authorization"))
        assertTrue(script.contains("LOOPBACK_HOSTS = {\"127.0.0.1\", \"localhost\", \"::1\"}"))
        assertTrue(script.contains("default=parse_env_port(\"FLOYD_CONTROL_PORT\")"))
        assertTrue(script.contains("def parse_env_port"))
        assertTrue(script.contains("def parse_port_value"))
        assertTrue(script.contains("if isinstance(value, bool):\n        raise SystemExit(f\"Invalid {label}: {value}\")"))
        assertTrue(script.contains("Invalid local-control config JSON in"))
        assertTrue(script.contains("must contain a JSON object"))
        assertTrue(script.contains("explicit_token = args.token is not None"))
        assertTrue(script.contains("def normalize_token"))
        assertTrue(script.contains("def select_bridge_config_path"))
        assertTrue(script.contains("Missing FloydAddons local-control sidecar"))
        assertTrue(script.contains("def validate_bridge_config_path"))
        assertTrue(script.contains("path.name != \"control-bridge.json\""))
        assertTrue(script.contains("Refusing to read non-bridge config path"))
        assertTrue(script.contains("auth/account-like path"))
        assertTrue(script.contains("config/floydaddons"))
        assertTrue(script.contains("Explicit --token does not contain a local-control token."))
        assertTrue(script.contains("if explicit_token and not token:"))
        assertTrue(script.contains("if port is not None:\n        validate_port(port)"))
        assertTrue(script.contains("token = normalize_token(args.token)"))
        assertTrue(script.contains("token = token or normalize_token(config.get(\"token\", \"\"))"))
        assertTrue(script.contains("config.get(\"enabled\") is not True"))
        assertTrue(script.contains("local-control enabled=false"))
        assertTrue(script.contains("parse_port_value(f\"{config_path} port\", config.get(\"port\", 38765))"))
        assertTrue(script.contains("validate_port(port)\n    return port, token"))
        assertTrue(script.contains("def validate_port"))
        assertTrue(script.contains("def validate_loopback_host"))
        assertTrue(script.contains("isinstance(port, bool) or port < 1 or port > 65535"))
        assertTrue(script.contains("validate_max_hit_age_ticks(args.max_hit_age_ticks)"))
        assertTrue(script.contains("def validate_max_hit_age_ticks"))
        assertTrue(script.contains("isinstance(value, bool) or value < 0"))
        assertTrue(script.contains("validate_timeout(args.timeout)"))
        assertTrue(script.contains("def validate_timeout"))
        assertTrue(script.contains("isinstance(value, bool) or value <= 0"))
        assertTrue(script.contains("--wait-seconds"))
        assertTrue(script.contains("def validate_wait_seconds"))
        assertTrue(script.contains("isinstance(value, bool) or value < 0"))
        assertTrue(script.contains("def run_once"))
        assertTrue(script.contains("def run_with_wait"))
        assertTrue(script.contains("time.sleep(min(1.0, remaining))"))
        assertTrue(script.contains("def url_host"))
        assertTrue(script.contains("f\"http://{url_host(host)}:{port}{path}\""))
        assertTrue(script.contains("def decode_response"))
        assertTrue(script.contains("returned non-UTF-8 response"))
        assertTrue(script.contains("returned invalid JSON"))
        assertTrue(script.contains("except (TimeoutError, urllib.error.URLError) as exc"))
        assertTrue(script.contains("except json.JSONDecodeError as exc"))
        assertTrue(script.contains("def int_state_field"))
        assertTrue(script.contains("Nick Hider server-ID scanHits"))
        assertTrue(script.contains("server_id.get(\"scanHits\", 0)"))
        assertTrue(script.contains("Refusing to send the local-control token to non-loopback host"))
        assertTrue(script.contains("if isinstance(port, bool) or port < 1 or port > 65535"))
        assertTrue(script.contains("--max-hit-age-ticks"))
        assertTrue(script.contains("--preflight"))
        assertTrue(script.contains("--diagnose"))
        assertTrue(script.contains("def diagnose_once"))
        assertTrue(script.contains("def run_diagnose_with_wait"))
        assertTrue(script.contains("\"diagnose\": True"))
        assertTrue(script.contains("\"ready\": False"))
        assertTrue(script.contains("def verify_live_state"))
        assertTrue(script.contains("if args.preflight:"))
        assertTrue(script.contains("live Hypixel acquisition preflight passed"))
        assertTrue(script.contains("\"GET\", \"/state\""))
        assertTrue(script.contains("local_control = nested(state, \"misc\", \"localControl\")"))
        assertTrue(script.contains("/state is missing misc.localControl proof."))
        assertTrue(script.contains("FloydLocalControl is not running from an enabled sidecar"))
        assertTrue(script.contains("\"POST\","))
        assertTrue(script.contains("\"/replace-text\""))
        assertTrue(script.contains("\"hypixel\" in value.lower()"))
        for (source in listOf(
            "known_tab",
            "tab_list",
            "tab_list_fallback",
            "scoreboard_title",
            "scoreboard_line",
            "scoreboard_any_title",
            "scoreboard_any_line"
        )) {
            assertTrue(script.contains("\"$source\""))
        }
        assertTrue(script.contains("\"debug_text\"").not())
        assertTrue(script.contains("\"debug_scoreboard\"").not())
        assertTrue(script.contains("source not in LIVE_SCAN_SOURCES"))
        assertTrue(script.contains("def acquisition_snapshot"))
        assertTrue(script.contains("\"lastScanSource\": server_id.get(\"lastScanSource\")"))
        assertTrue(script.contains("\"scoreboardLineCount\": server_id.get(\"scoreboardLineCount\")"))
        assertTrue(script.contains("\"scoreboard\": server_id.get(\"scoreboard\")"))
        assertTrue(script.contains("last_hit_tick = server_id.get(\"lastHitTick\")"))
        assertTrue(script.contains("isinstance(last_hit_tick, bool)"))
        assertTrue(script.contains("Nick Hider has not recorded an in-world live hit tick yet"))
        assertTrue(script.contains("\"lastHitTick\": last_hit_tick"))
        assertTrue(script.contains("world = state.get(\"world\")"))
        assertTrue(script.contains("isinstance(world.get(\"time\"), bool)"))
        assertTrue(script.contains("/state is missing world.time for live hit freshness proof."))
        assertTrue(script.contains("hit_age_ticks = current_tick - last_hit_tick"))
        assertTrue(script.contains("args.max_hit_age_ticks"))
        assertTrue(script.contains("\"hitAgeTicks\": hit_age_ticks"))
        assertTrue(script.contains("{\"text\": probe_text}"))
        assertTrue(script.contains("/replace-text did not report ok=true"))
        assertTrue(script.contains("\"scanned\" not in replacement"))
        assertTrue(script.contains("replacement.get(\"text\") != probe_text"))
        assertFalse(script.contains("\"scanText\""), "The live proof must not seed synthetic scan text.")
        assertTrue(scriptTest.contains("test_success_requires_fresh_live_hit_and_no_synthetic_scan"))
        assertTrue(scriptTest.contains("test_preflight_requires_fresh_live_hit_but_does_not_replace_text"))
        assertTrue(scriptTest.contains("test_diagnose_success_checks_preflight_only"))
        assertTrue(scriptTest.contains("test_diagnose_reports_first_missing_precondition_as_json"))
        assertTrue(scriptTest.contains("expected_requests=[(\"GET\", \"/state\")]"))
        assertTrue(scriptTest.contains("test_rejects_non_numeric_env_port_without_traceback"))
        assertTrue(scriptTest.contains("test_rejects_non_numeric_sidecar_port_without_traceback"))
        assertTrue(scriptTest.contains("test_rejects_boolean_sidecar_port_without_treating_it_as_integer"))
        assertTrue(scriptTest.contains("test_rejects_boolean_port"))
        assertTrue(scriptTest.contains("test_rejects_invalid_explicit_port_before_reading_sidecar_token"))
        assertTrue(scriptTest.contains("test_rejects_non_bridge_config_path_before_reading_json"))
        assertTrue(scriptTest.contains("test_rejects_auth_like_bridge_config_parent_before_reading_json"))
        assertTrue(scriptTest.contains("test_rejects_blank_explicit_token_with_explicit_port"))
        assertTrue(scriptTest.contains("test_rejects_blank_explicit_token_before_sidecar_fallback"))
        assertTrue(scriptTest.contains("test_rejects_null_or_blank_sidecar_token"))
        assertTrue(scriptTest.contains("test_requires_replace_text_ok_true"))
        assertTrue(scriptTest.contains("test_rejects_malformed_sidecar_json_without_traceback"))
        assertTrue(scriptTest.contains("test_rejects_non_object_sidecar_json_without_traceback"))
        assertTrue(scriptTest.contains("test_rejects_disabled_sidecar_before_using_token"))
        assertTrue(scriptTest.contains("test_rejects_negative_max_hit_age"))
        assertTrue(scriptTest.contains("test_rejects_boolean_max_hit_age"))
        assertTrue(scriptTest.contains("test_rejects_non_positive_timeout"))
        assertTrue(scriptTest.contains("test_rejects_boolean_timeout"))
        assertTrue(scriptTest.contains("test_rejects_non_integer_scan_hits_without_traceback"))
        assertTrue(scriptTest.contains("test_rejects_boolean_false_scan_hits_without_masking_as_zero"))
        assertTrue(scriptTest.contains("test_rejects_boolean_world_time_without_treating_it_as_integer"))
        assertTrue(scriptTest.contains("test_rejects_boolean_last_hit_tick_without_treating_it_as_integer"))
        assertTrue(scriptTest.contains("test_formats_ipv6_loopback_host_for_http_url"))
        assertTrue(scriptTest.contains("test_request_json_rejects_malformed_json_without_traceback"))
        assertTrue(scriptTest.contains("test_request_json_rejects_invalid_utf8_without_traceback"))
        assertTrue(scriptTest.contains("test_request_json_rejects_timeout_without_traceback"))
        assertTrue(scriptTest.contains("test_main_rejects_non_loopback_before_loading_bridge_token"))
        assertTrue(scriptTest.contains("test_wait_seconds_retries_until_preflight_state_is_ready"))
        assertTrue(scriptTest.contains("test_rejects_negative_wait_seconds"))
        assertTrue(scriptTest.contains("test_rejects_stale_live_hit"))
        assertTrue(scriptTest.contains("test_auto_detects_normal_launcher_bridge_sidecar"))
        assertTrue(scriptTest.contains("test_auto_detects_dev_run_bridge_sidecar"))
        assertTrue(scriptTest.contains("test_missing_auto_detected_sidecar_lists_checked_paths"))
        assertTrue(scriptTest.contains("test_requires_explicit_scanned_null_response"))
        assertTrue(scriptTest.contains("test_requires_running_enabled_local_control_state"))
        assertTrue(scriptTest.contains("fake_request_json"))
        assertTrue(readme.contains("python3 scripts/verify-live-hypixel-acquisition.py"))
        assertTrue(readme.contains("python3 scripts/verify-live-hypixel-acquisition.py --json"))
        assertTrue(readme.contains("python3 scripts/verify-live-hypixel-acquisition.py --preflight --json"))
        assertTrue(readme.contains("python3 scripts/verify-live-hypixel-acquisition.py --diagnose --json"))
        assertTrue(readme.contains("scripts/live-hypixel-status.sh"))
        assertTrue(readme.contains("FLOYDADDONS_REQUIRE_COMPLETE=true"))
        assertTrue(readme.contains("--wait-seconds"))
        assertTrue(readme.contains("FLOYDADDONS_LIVE_WAIT_SECONDS=<seconds>"))
        assertTrue(readme.contains("See `LIVE_PROOF.md` for the final live-Hypixel proof runbook."))
        assertTrue(readme.contains("relative to the active Minecraft run directory"))
        assertTrue(readme.contains("auto-detects `config/floydaddons/control-bridge.json`"))
        assertTrue(readme.contains("`run/config/floydaddons/control-bridge.json` for this repo's `runClient`"))
        assertFalse(readme.contains("\n scripts/verify-live-hypixel-acquisition.py"))

        for (expected in listOf(
            "FLOYDADDONS_RUN_LIVE_PREFLIGHT=true ./scripts/verify-floyd-in-odin.sh",
            "FLOYDADDONS_RUN_LIVE_HYPIXEL=true ./scripts/verify-floyd-in-odin.sh",
            "FLOYDADDONS_RUN_LIVE_PREFLIGHT=true FLOYDADDONS_LIVE_WAIT_SECONDS=120 ./scripts/verify-floyd-in-odin.sh",
            "FLOYDADDONS_RUN_LIVE_HYPIXEL=true FLOYDADDONS_LIVE_WAIT_SECONDS=120 ./scripts/verify-floyd-in-odin.sh",
            "FLOYDADDONS_RUN_LIVE_DIAGNOSE=true ./scripts/verify-floyd-in-odin.sh",
            "logs/live-hypixel-diagnose.json",
            "scripts/live-hypixel-status.sh",
            "logs/live-hypixel-status.json",
            "logs/live-hypixel-preflight.json",
            "logs/live-hypixel-proof.json",
            "empty or invalid JSON proof artifact behind",
            "The live verifier reads only the FloydAddons local-control sidecar/token",
            "It does not inspect Minecraft account/auth files.",
            "whether the Minecraft launcher is running",
            "Local Control is listening on port `38765`",
        )) {
            assertTrue(liveProof.contains(expected), "LIVE_PROOF.md missing token: $expected")
        }

        assertTrue(gate.contains("live_wait_args=()"))
        assertTrue(gate.contains("FLOYDADDONS_LIVE_WAIT_SECONDS"))
        assertTrue(gate.contains("""live_wait_args+=(--wait-seconds "${'$'}FLOYDADDONS_LIVE_WAIT_SECONDS")"""))
        assertTrue(gate.contains("write_diagnose_artifact()"))
        assertTrue(gate.contains("FLOYDADDONS_RUN_LIVE_DIAGNOSE"))
        assertTrue(gate.contains("FLOYDADDONS_LIVE_DIAGNOSE_PROOF"))
        assertTrue(gate.contains("write_success_proof()"))
        assertTrue(gate.contains("""tmp_file="$(mktemp "${'$'}{proof_file}.tmp.XXXXXX")""""))
        assertTrue(gate.contains("""rm -f "${'$'}proof_file""""))
        assertTrue(gate.contains("live proof command produced an empty JSON artifact"))
        assertTrue(gate.contains("""python3 -m json.tool "${'$'}tmp_file" >/dev/null"""))
        assertTrue(gate.contains("live proof command produced invalid JSON"))
        assertTrue(gate.contains("""mv "${'$'}tmp_file" "${'$'}proof_file""""))
        assertTrue(gate.contains("""--preflight --json ${'$'}{live_wait_args[@]+"${'$'}{live_wait_args[@]}"}"""))
        assertTrue(gate.contains("""--json ${'$'}{live_wait_args[@]+"${'$'}{live_wait_args[@]}"}"""))

        for (forbidden in listOf(
            "launcher_accounts",
            "accessToken",
            "clientToken",
            "yggdrasil",
            ".minecraft"
        )) {
            assertFalse(script.contains(forbidden), "Verifier must not read Minecraft account/auth material: $forbidden")
        }
    }
}
