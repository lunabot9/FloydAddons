#!/usr/bin/env python3
"""Offline tests for verify-live-hypixel-acquisition.py."""

from __future__ import annotations

import importlib.util
import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from typing import Any


def load_verifier():
    path = Path(__file__).with_name("verify-live-hypixel-acquisition.py")
    spec = importlib.util.spec_from_file_location("live_hypixel_verifier", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class LiveHypixelVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.verifier = load_verifier()

    def test_rejects_non_loopback_host_before_token_use(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_loopback_host("192.0.2.1")
        self.assertIn("non-loopback", str(raised.exception))

    def test_rejects_invalid_port(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_port(70000)
        self.assertIn("Invalid local-control port", str(raised.exception))

    def test_rejects_boolean_port(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_port(True)
        self.assertIn("Invalid local-control port", str(raised.exception))

    def test_rejects_non_numeric_env_port_without_traceback(self) -> None:
        original = os.environ.get("FLOYD_CONTROL_PORT")
        try:
            os.environ["FLOYD_CONTROL_PORT"] = "abc"
            with self.assertRaises(SystemExit) as raised:
                self.verifier.parse_env_port("FLOYD_CONTROL_PORT")
            self.assertIn("Invalid FLOYD_CONTROL_PORT: abc", str(raised.exception))
        finally:
            if original is None:
                os.environ.pop("FLOYD_CONTROL_PORT", None)
            else:
                os.environ["FLOYD_CONTROL_PORT"] = original

    def test_rejects_non_numeric_sidecar_port_without_traceback(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "control-bridge.json"
            path.write_text('{"enabled": true, "port": "abc", "token": "token"}')
            args = type("Args", (), {"port": None, "token": None, "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("Invalid", str(raised.exception))
            self.assertIn("port", str(raised.exception))

    def test_rejects_boolean_sidecar_port_without_treating_it_as_integer(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "control-bridge.json"
            path.write_text('{"enabled": true, "port": true, "token": "token"}')
            args = type("Args", (), {"port": None, "token": None, "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("Invalid", str(raised.exception))
            self.assertIn("port", str(raised.exception))

    def test_auto_detects_normal_launcher_bridge_sidecar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            original_cwd = Path.cwd()
            try:
                os.chdir(directory)
                path = Path("config/floydaddons/control-bridge.json")
                path.parent.mkdir(parents=True)
                path.write_text('{"enabled": true, "port": 38766, "token": "normal-token"}')
                args = type("Args", (), {"port": None, "token": None, "config": None})()

                self.assertEqual((38766, "normal-token"), self.verifier.load_bridge(args))
            finally:
                os.chdir(original_cwd)

    def test_auto_detects_dev_run_bridge_sidecar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            original_cwd = Path.cwd()
            try:
                os.chdir(directory)
                path = Path("run/config/floydaddons/control-bridge.json")
                path.parent.mkdir(parents=True)
                path.write_text('{"enabled": true, "port": 38767, "token": "dev-token"}')
                args = type("Args", (), {"port": None, "token": None, "config": None})()

                self.assertEqual((38767, "dev-token"), self.verifier.load_bridge(args))
            finally:
                os.chdir(original_cwd)

    def test_missing_auto_detected_sidecar_lists_checked_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            original_cwd = Path.cwd()
            try:
                os.chdir(directory)
                args = type("Args", (), {"port": None, "token": None, "config": None})()

                with self.assertRaises(SystemExit) as raised:
                    self.verifier.load_bridge(args)
            finally:
                os.chdir(original_cwd)

        message = str(raised.exception)
        self.assertIn("config/floydaddons/control-bridge.json", message)
        self.assertIn("run/config/floydaddons/control-bridge.json", message)

    def test_rejects_invalid_explicit_port_before_reading_sidecar_token(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "missing-control-bridge.json"
            args = type("Args", (), {"port": 70000, "token": None, "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("Invalid local-control port", str(raised.exception))

    def test_rejects_non_bridge_config_path_before_reading_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "launcher_accounts.json"
            path.write_text('{"token": "should-not-read"}')
            args = type("Args", (), {"port": None, "token": None, "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("Refusing to read non-bridge config path", str(raised.exception))
            self.assertIn("control-bridge.json", str(raised.exception))

    def test_rejects_auth_like_bridge_config_parent_before_reading_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            auth_dir = Path(directory) / "auth"
            auth_dir.mkdir()
            path = auth_dir / "control-bridge.json"
            path.write_text('{"token": "should-not-read"}')
            args = type("Args", (), {"port": None, "token": None, "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("auth/account-like path", str(raised.exception))
            self.assertIn("config/floydaddons", str(raised.exception))

    def test_rejects_blank_explicit_token_with_explicit_port(self) -> None:
        args = type("Args", (), {"port": 38765, "token": "   ", "config": "missing-control-bridge.json"})()
        with self.assertRaises(SystemExit) as raised:
            self.verifier.load_bridge(args)
        self.assertIn("does not contain a local-control token", str(raised.exception))

    def test_rejects_blank_explicit_token_before_sidecar_fallback(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "control-bridge.json"
            path.write_text('{"enabled": true, "port": 38765, "token": "token"}')
            args = type("Args", (), {"port": None, "token": "   ", "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("does not contain a local-control token", str(raised.exception))

    def test_rejects_null_or_blank_sidecar_token(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            null_dir = Path(directory) / "null"
            null_dir.mkdir()
            null_path = null_dir / "control-bridge.json"
            null_path.write_text('{"enabled": true, "port": 38765, "token": null}')
            args = type("Args", (), {"port": None, "token": None, "config": str(null_path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("does not contain a local-control token", str(raised.exception))

            blank_dir = Path(directory) / "blank"
            blank_dir.mkdir()
            blank_path = blank_dir / "control-bridge.json"
            blank_path.write_text('{"enabled": true, "port": 38765, "token": "   "}')
            args = type("Args", (), {"port": None, "token": None, "config": str(blank_path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("does not contain a local-control token", str(raised.exception))

    def test_rejects_malformed_sidecar_json_without_traceback(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "control-bridge.json"
            path.write_text("{bad json")
            args = type("Args", (), {"port": None, "token": None, "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("Invalid local-control config JSON", str(raised.exception))

    def test_rejects_non_object_sidecar_json_without_traceback(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "control-bridge.json"
            path.write_text("[]")
            args = type("Args", (), {"port": None, "token": None, "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("must contain a JSON object", str(raised.exception))

    def test_rejects_disabled_sidecar_before_using_token(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "control-bridge.json"
            path.write_text('{"enabled": false, "port": 38765, "token": "token"}')
            args = type("Args", (), {"port": None, "token": None, "config": str(path)})()
            with self.assertRaises(SystemExit) as raised:
                self.verifier.load_bridge(args)
            self.assertIn("local-control enabled=false", str(raised.exception))

    def test_rejects_negative_max_hit_age(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_max_hit_age_ticks(-1)
        self.assertIn("Invalid max hit age ticks", str(raised.exception))

    def test_rejects_boolean_max_hit_age(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_max_hit_age_ticks(True)
        self.assertIn("Invalid max hit age ticks", str(raised.exception))

    def test_rejects_non_positive_timeout(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_timeout(0)
        self.assertIn("Invalid timeout", str(raised.exception))

    def test_rejects_boolean_timeout(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_timeout(True)
        self.assertIn("Invalid timeout", str(raised.exception))

    def test_rejects_negative_wait_seconds(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_wait_seconds(-1)
        self.assertIn("Invalid wait seconds", str(raised.exception))

    def test_rejects_boolean_wait_seconds(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.verifier.validate_wait_seconds(True)
        self.assertIn("Invalid wait seconds", str(raised.exception))

    def test_formats_ipv6_loopback_host_for_http_url(self) -> None:
        self.assertEqual("[::1]", self.verifier.url_host("::1"))
        self.assertEqual("127.0.0.1", self.verifier.url_host("127.0.0.1"))
        self.assertEqual("localhost", self.verifier.url_host("localhost"))

    def test_request_json_rejects_malformed_json_without_traceback(self) -> None:
        module = self.verifier
        original_urlopen = module.urllib.request.urlopen

        class FakeResponse:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                return False

            @staticmethod
            def read() -> bytes:
                return b"{bad json"

        try:
            module.urllib.request.urlopen = lambda req, timeout: FakeResponse()
            with self.assertRaises(SystemExit) as raised:
                module.request_json("127.0.0.1", 38765, "token", "GET", "/state", 5.0)
            self.assertIn("returned invalid JSON", str(raised.exception))
        finally:
            module.urllib.request.urlopen = original_urlopen

    def test_request_json_rejects_invalid_utf8_without_traceback(self) -> None:
        module = self.verifier
        original_urlopen = module.urllib.request.urlopen

        class FakeResponse:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                return False

            @staticmethod
            def read() -> bytes:
                return b"\xff"

        try:
            module.urllib.request.urlopen = lambda req, timeout: FakeResponse()
            with self.assertRaises(SystemExit) as raised:
                module.request_json("127.0.0.1", 38765, "token", "GET", "/state", 5.0)
            self.assertIn("returned non-UTF-8 response", str(raised.exception))
        finally:
            module.urllib.request.urlopen = original_urlopen

    def test_request_json_rejects_timeout_without_traceback(self) -> None:
        module = self.verifier
        original_urlopen = module.urllib.request.urlopen
        try:
            module.urllib.request.urlopen = lambda req, timeout: (_ for _ in ()).throw(TimeoutError("timed out"))
            with self.assertRaises(SystemExit) as raised:
                module.request_json("127.0.0.1", 38765, "token", "GET", "/state", 5.0)
            self.assertIn("Could not reach local-control bridge", str(raised.exception))
        finally:
            module.urllib.request.urlopen = original_urlopen

    def test_main_rejects_non_loopback_before_loading_bridge_token(self) -> None:
        module = self.verifier
        original_argv = sys.argv
        original_load_bridge = module.load_bridge
        try:
            sys.argv = [
                "verify-live-hypixel-acquisition.py",
                "--host",
                "192.0.2.1",
                "--port",
                "38765",
                "--token",
                "token",
            ]
            module.load_bridge = lambda args: self.fail("load_bridge must not run for non-loopback hosts")
            with self.assertRaises(SystemExit) as raised:
                module.main()
            self.assertIn("non-loopback", str(raised.exception))
        finally:
            sys.argv = original_argv
            module.load_bridge = original_load_bridge

    def test_chooses_abbreviated_cached_probe_id(self) -> None:
        self.assertEqual(
            "m28d",
            self.verifier.choose_cache_probe_id(["m\u200bini28d", "m\u200bega28d", "m\u200b28d"]),
        )

    def test_success_requires_fresh_live_hit_and_no_synthetic_scan(self) -> None:
        proof = self.run_main(
            state=self.good_state(last_hit_tick=1900, world_time=2000),
            replacement={
                "ok": True,
                "text": "Floyd live cache probe m28d",
                "replaced": "Floyd live cache probe fL0YD",
                "changed": True,
                "scanned": None,
            },
        )
        self.assertTrue(proof["ok"])
        self.assertEqual(100, proof["hitAgeTicks"])
        self.assertEqual("m28d", proof["cachedProbeId"])

    def test_preflight_requires_fresh_live_hit_but_does_not_replace_text(self) -> None:
        proof = self.run_main(
            state=self.good_state(last_hit_tick=1900, world_time=2000),
            replacement={},
            extra_args=["--preflight"],
            expected_requests=[("GET", "/state")],
        )
        self.assertTrue(proof["ok"])
        self.assertTrue(proof["preflight"])
        self.assertEqual(100, proof["hitAgeTicks"])
        self.assertEqual("m28d", proof["cachedProbeId"])
        self.assertNotIn("replacement", proof)

    def test_diagnose_success_checks_preflight_only(self) -> None:
        proof = self.run_main(
            state=self.good_state(last_hit_tick=1900, world_time=2000),
            replacement={},
            extra_args=["--diagnose"],
            expected_requests=[("GET", "/state")],
        )
        self.assertTrue(proof["ok"])
        self.assertTrue(proof["preflight"])
        self.assertTrue(proof["diagnose"])
        self.assertTrue(proof["ready"])
        self.assertNotIn("replacement", proof)

    def test_diagnose_reports_first_missing_precondition_as_json(self) -> None:
        module = self.verifier
        original_argv = sys.argv
        original_load_bridge = module.load_bridge
        try:
            sys.argv = [
                "verify-live-hypixel-acquisition.py",
                "--json",
                "--diagnose",
                "--port",
                "38765",
                "--token",
                "token",
            ]
            module.load_bridge = lambda args: (_ for _ in ()).throw(SystemExit("bridge not ready"))
            output = io.StringIO()
            with redirect_stdout(output):
                self.assertEqual(1, module.main())
            proof = module.json.loads(output.getvalue())
            self.assertFalse(proof["ok"])
            self.assertTrue(proof["diagnose"])
            self.assertFalse(proof["ready"])
            self.assertEqual("bridge not ready", proof["error"])
        finally:
            sys.argv = original_argv
            module.load_bridge = original_load_bridge

    def test_wait_seconds_retries_until_preflight_state_is_ready(self) -> None:
        module = self.verifier
        original_argv = sys.argv
        original_load_bridge = module.load_bridge
        original_request_json = module.request_json
        original_sleep = module.time.sleep
        attempts = 0
        sleeps: list[float] = []

        def fake_request_json(host: str, port: int, token: str, method: str, path: str, timeout: float, body: dict[str, Any] | None = None) -> dict[str, Any]:
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise SystemExit("not ready yet")
            return self.good_state(last_hit_tick=1900, world_time=2000)

        try:
            sys.argv = [
                "verify-live-hypixel-acquisition.py",
                "--json",
                "--port",
                "38765",
                "--token",
                "token",
                "--preflight",
                "--wait-seconds",
                "1",
            ]
            module.load_bridge = lambda args: (args.port, args.token)
            module.request_json = fake_request_json
            module.time.sleep = lambda seconds: sleeps.append(seconds)
            output = io.StringIO()
            with redirect_stdout(output):
                self.assertEqual(0, module.main())
            proof = module.json.loads(output.getvalue())
            self.assertTrue(proof["ok"])
            self.assertTrue(proof["preflight"])
            self.assertEqual(2, attempts)
            self.assertTrue(sleeps)
        finally:
            sys.argv = original_argv
            module.load_bridge = original_load_bridge
            module.request_json = original_request_json
            module.time.sleep = original_sleep

    def test_rejects_stale_live_hit(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.run_main(
                state=self.good_state(last_hit_tick=1, world_time=2000),
                replacement={
                    "ok": True,
                    "text": "Floyd live cache probe m28d",
                    "replaced": "Floyd live cache probe fL0YD",
                    "changed": True,
                    "scanned": None,
                },
            )
        self.assertIn("too old for proof", str(raised.exception))

    def test_rejects_non_integer_scan_hits_without_traceback(self) -> None:
        state = self.good_state(last_hit_tick=1900, world_time=2000)
        state["playerFeatures"]["nickHider"]["serverId"]["scanHits"] = "abc"
        with self.assertRaises(SystemExit) as raised:
            self.run_main(
                state=state,
                replacement={
                    "ok": True,
                    "text": "Floyd live cache probe m28d",
                    "replaced": "Floyd live cache probe fL0YD",
                    "changed": True,
                    "scanned": None,
                },
            )
        self.assertIn("Nick Hider server-ID scanHits is not an integer", str(raised.exception))

    def test_rejects_boolean_false_scan_hits_without_masking_as_zero(self) -> None:
        state = self.good_state(last_hit_tick=1900, world_time=2000)
        state["playerFeatures"]["nickHider"]["serverId"]["scanHits"] = False
        with self.assertRaises(SystemExit) as raised:
            self.run_main(
                state=state,
                replacement={
                    "ok": True,
                    "text": "Floyd live cache probe m28d",
                    "replaced": "Floyd live cache probe fL0YD",
                    "changed": True,
                    "scanned": None,
                },
            )
        self.assertIn("Nick Hider server-ID scanHits is not an integer", str(raised.exception))

    def test_rejects_boolean_world_time_without_treating_it_as_integer(self) -> None:
        state = self.good_state(last_hit_tick=0, world_time=2000)
        state["world"]["time"] = True
        with self.assertRaises(SystemExit) as raised:
            self.run_main(
                state=state,
                replacement={
                    "ok": True,
                    "text": "Floyd live cache probe m28d",
                    "replaced": "Floyd live cache probe fL0YD",
                    "changed": True,
                    "scanned": None,
                },
            )
        self.assertIn("/state is missing world.time for live hit freshness proof.", str(raised.exception))

    def test_rejects_boolean_last_hit_tick_without_treating_it_as_integer(self) -> None:
        state = self.good_state(last_hit_tick=1900, world_time=2000)
        state["playerFeatures"]["nickHider"]["serverId"]["lastHitTick"] = True
        with self.assertRaises(SystemExit) as raised:
            self.run_main(
                state=state,
                replacement={
                    "ok": True,
                    "text": "Floyd live cache probe m28d",
                    "replaced": "Floyd live cache probe fL0YD",
                    "changed": True,
                    "scanned": None,
                },
            )
        self.assertIn("Nick Hider has not recorded an in-world live hit tick yet", str(raised.exception))

    def test_requires_explicit_scanned_null_response(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.run_main(
                state=self.good_state(last_hit_tick=1900, world_time=2000),
                replacement={
                    "ok": True,
                    "text": "Floyd live cache probe m28d",
                    "replaced": "Floyd live cache probe fL0YD",
                    "changed": True,
                },
            )
        self.assertIn("did not report the scanned field", str(raised.exception))

    def test_requires_replace_text_ok_true(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.run_main(
                state=self.good_state(last_hit_tick=1900, world_time=2000),
                replacement={
                    "ok": False,
                    "text": "Floyd live cache probe m28d",
                    "replaced": "Floyd live cache probe fL0YD",
                    "changed": True,
                    "scanned": None,
                },
            )
        self.assertIn("/replace-text did not report ok=true", str(raised.exception))

    def test_requires_running_enabled_local_control_state(self) -> None:
        state = self.good_state(last_hit_tick=1900, world_time=2000)
        state["misc"]["localControl"]["running"] = False
        with self.assertRaises(SystemExit) as raised:
            self.run_main(
                state=state,
                replacement={
                    "ok": True,
                    "text": "Floyd live cache probe m28d",
                    "replaced": "Floyd live cache probe fL0YD",
                    "changed": True,
                    "scanned": None,
                },
            )
        self.assertIn("not running from an enabled sidecar", str(raised.exception))

    def run_main(
        self,
        state: dict[str, Any],
        replacement: dict[str, Any],
        extra_args: list[str] | None = None,
        expected_requests: list[tuple[str, str]] | None = None,
    ) -> dict[str, Any]:
        module = self.verifier
        original_argv = sys.argv
        original_load_bridge = module.load_bridge
        original_request_json = module.request_json
        requests: list[tuple[str, str]] = []

        def fake_request_json(host: str, port: int, token: str, method: str, path: str, timeout: float, body: dict[str, Any] | None = None) -> dict[str, Any]:
            requests.append((method, path))
            if method == "GET" and path == "/state":
                return state
            if method == "POST" and path == "/replace-text":
                return replacement
            raise AssertionError(f"unexpected request: {method} {path}")

        try:
            sys.argv = [
                "verify-live-hypixel-acquisition.py",
                "--json",
                "--port",
                "38765",
                "--token",
                "token",
                *(extra_args or []),
            ]
            module.load_bridge = lambda args: (args.port, args.token)
            module.request_json = fake_request_json
            output = io.StringIO()
            with redirect_stdout(output):
                self.assertEqual(0, module.main())
            self.assertEqual(expected_requests or [("GET", "/state"), ("POST", "/replace-text")], requests)
            return module.json.loads(output.getvalue())
        finally:
            sys.argv = original_argv
            module.load_bridge = original_load_bridge
            module.request_json = original_request_json

    @staticmethod
    def good_state(last_hit_tick: int, world_time: int) -> dict[str, Any]:
        return {
            "ok": True,
            "connected": True,
            "server": {
                "connected": True,
                "name": "Hypixel",
                "address": "mc.hypixel.net",
                "type": "OTHER",
            },
            "world": {
                "time": world_time,
            },
            "misc": {
                "localControl": {
                    "enabled": True,
                    "bridgeEnabled": True,
                    "running": True,
                    "settingsEnabled": True,
                },
            },
            "playerFeatures": {
                "nickHider": {
                    "settings": {
                        "serverIdHider": True,
                    },
                    "serverId": {
                        "lastScanSource": "scoreboard_line",
                        "lastScannedText": "m\u200b28d",
                        "scanHits": 1,
                        "lastHitTick": last_hit_tick,
                        "cached": ["m\u200b28d", "m\u200bini28d", "m\u200bega28d"],
                        "current": "m\u200b28d",
                        "tabEntryCount": 80,
                        "scoreboardLineCount": 10,
                        "scoreboard": {},
                    },
                },
            },
        }


if __name__ == "__main__":
    unittest.main()
