#!/usr/bin/env python3
"""Offline tests for completion-status.py."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("completion-status.py")


def load_status():
    spec = importlib.util.spec_from_file_location("completion_status", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class CompletionStatusTest(unittest.TestCase):
    def setUp(self) -> None:
        self.status = load_status()

    def test_reports_incomplete_when_live_proof_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "missing-live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            self.write_ready_install(Path(directory) / "minecraft", build_jar)

            report = self.status.completion_report(runtime, live, Path(directory) / "minecraft", build_jar)

        self.assertFalse(report["complete"])
        self.assertTrue(report["runtimeScaffold"]["ok"])
        self.assertTrue(report["liveInstall"]["ok"])
        self.assertFalse(report["liveHypixel"]["ok"])
        self.assertIn("missing live Hypixel proof", report["remaining"])

    def test_reports_complete_with_runtime_install_and_live_proof(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            live.write_text(self.status.json.dumps(self.live_proof()))
            self.write_ready_install(Path(directory) / "minecraft", build_jar)

            report = self.status.completion_report(runtime, live, Path(directory) / "minecraft", build_jar)

        self.assertTrue(report["complete"])
        self.assertEqual([], report["remaining"])
        self.assertEqual("scoreboard_line", report["liveHypixel"]["lastScanSource"])
        self.assertEqual(3, report["liveHypixel"]["scanHits"])
        self.assertEqual(900, report["liveHypixel"]["lastHitTick"])
        self.assertEqual(1000, report["liveHypixel"]["worldTime"])
        self.assertEqual("Floyd live cache probe fL0YD", report["liveHypixel"]["replacement"])

    def test_reports_live_install_readiness_separately(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "missing-live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            self.write_jar(build_jar, "fabric.mod.json", '{"id":"floydaddons"}')

            report = self.status.completion_report(runtime, live, Path(directory) / "missing-minecraft", build_jar)

        self.assertFalse(report["complete"])
        self.assertFalse(report["liveInstall"]["ok"])
        self.assertIn("live launcher install is not ready", report["remaining"])
        self.assertIn("missing Fabric loader profile version JSON", report["remaining"])
        self.assertIn("missing launcher profile for fabric-loader-1.21.11", report["remaining"])
        self.assertIn("missing floydaddons jar", report["remaining"])

    def test_reports_incomplete_when_install_is_missing_even_with_both_proofs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            live.write_text(self.status.json.dumps(self.live_proof()))
            self.write_jar(build_jar, "fabric.mod.json", '{"id":"floydaddons"}')

            report = self.status.completion_report(runtime, live, Path(directory) / "missing-minecraft", build_jar)

        self.assertFalse(report["complete"])
        self.assertTrue(report["runtimeScaffold"]["ok"])
        self.assertFalse(report["liveInstall"]["ok"])
        self.assertTrue(report["liveHypixel"]["ok"])
        self.assertIn("live launcher install is not ready", report["remaining"])
        self.assertIn("missing Fabric loader profile version JSON", report["remaining"])

    def test_rejects_invalid_live_proof_shape(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            live.write_text(self.status.json.dumps({"ok": True}))
            self.write_ready_install(Path(directory) / "minecraft", build_jar)

            report = self.status.completion_report(runtime, live, Path(directory) / "minecraft", build_jar)

        self.assertFalse(report["complete"])
        self.assertIn("live Hypixel proof does not prove cached replacement", report["remaining"][0])

    def test_rejects_preflight_live_proof_as_completion(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            proof = self.live_proof()
            proof["preflight"] = True
            live.write_text(self.status.json.dumps(proof))
            self.write_ready_install(Path(directory) / "minecraft", build_jar)

            report = self.status.completion_report(runtime, live, Path(directory) / "minecraft", build_jar)

        self.assertFalse(report["complete"])
        self.assertFalse(report["liveHypixel"]["ok"])

    def test_rejects_non_live_scan_source_as_completion(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            proof = self.live_proof()
            proof["lastScanSource"] = "debug_scan"
            live.write_text(self.status.json.dumps(proof))
            self.write_ready_install(Path(directory) / "minecraft", build_jar)

            report = self.status.completion_report(runtime, live, Path(directory) / "minecraft", build_jar)

        self.assertFalse(report["complete"])
        self.assertFalse(report["liveHypixel"]["ok"])

    def test_rejects_stale_live_hit_as_completion(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            proof = self.live_proof()
            proof["worldTime"] = 2000
            proof["lastHitTick"] = 0
            proof["hitAgeTicks"] = 2000
            live.write_text(self.status.json.dumps(proof))
            self.write_ready_install(Path(directory) / "minecraft", build_jar)

            report = self.status.completion_report(runtime, live, Path(directory) / "minecraft", build_jar)

        self.assertFalse(report["complete"])
        self.assertFalse(report["liveHypixel"]["ok"])

    def test_require_complete_exits_nonzero_when_live_proof_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "missing-live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            self.write_ready_install(Path(directory) / "minecraft", build_jar)

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--runtime-proof",
                    str(runtime),
                    "--live-proof",
                    str(live),
                    "--minecraft-root",
                    str(Path(directory) / "minecraft"),
                    "--build-jar",
                    str(build_jar),
                    "--require-complete",
                    "--json",
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )

        self.assertEqual(1, result.returncode)
        self.assertIn('"complete": false', result.stdout)
        self.assertIn("missing live Hypixel proof", result.stdout)

    def test_require_complete_exits_zero_when_both_proofs_are_valid(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory) / "runtime.json"
            live = Path(directory) / "live.json"
            build_jar = Path(directory) / "build" / "FloydAddons-0.1.0.jar"
            runtime.write_text(self.status.json.dumps(self.runtime_proof()))
            live.write_text(self.status.json.dumps(self.live_proof()))
            self.write_ready_install(Path(directory) / "minecraft", build_jar)

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--runtime-proof",
                    str(runtime),
                    "--live-proof",
                    str(live),
                    "--minecraft-root",
                    str(Path(directory) / "minecraft"),
                    "--build-jar",
                    str(build_jar),
                    "--require-complete",
                    "--json",
                ],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('"complete": true', result.stdout)

    @staticmethod
    def runtime_proof() -> dict:
        return {
            "moduleCount": 16,
            "screen": "net.minecraft.client.gui.screens.TitleScreen",
            "serverConnected": False,
            "scaffold": {
                "modId": "floydaddons",
                "activeScaffold": "Odin Fabric module/config/event/ClickGUI",
            },
            "categories": {"QOL": []},
            "localControl": {"running": True},
        }

    @staticmethod
    def live_proof() -> dict:
        return {
            "ok": True,
            "serverIdentity": ["Hypixel", "mc.hypixel.net"],
            "lastScanSource": "scoreboard_line",
            "scanHits": 3,
            "lastHitTick": 900,
            "worldTime": 1000,
            "cachedProbeId": "m28d",
            "hitAgeTicks": 100,
            "replacementChanged": True,
            "replacement": "Floyd live cache probe fL0YD",
        }

    @staticmethod
    def write_ready_install(root: Path, build_jar: Path) -> None:
        version_dir = root / "versions" / "fabric-loader-0.18.5-1.21.11"
        mods_dir = root / "mods"
        version_dir.mkdir(parents=True)
        mods_dir.mkdir()
        build_jar.parent.mkdir(parents=True, exist_ok=True)
        CompletionStatusTest.write_jar(build_jar, "fabric.mod.json", '{"id":"floydaddons"}')
        (version_dir / "fabric-loader-0.18.5-1.21.11.json").write_text("{}")
        (root / "launcher_profiles.json").write_text(json.dumps({
            "profiles": {
                "fabric-loader-1.21.11": {
                    "name": "fabric-loader-1.21.11",
                    "lastVersionId": "fabric-loader-0.18.5-1.21.11",
                    "type": "custom",
                }
            }
        }))
        (mods_dir / "FloydAddons-0.1.0.jar").write_bytes(build_jar.read_bytes())
        for name in ("fabric-api-0.141.3+1.21.11.jar", "fabric-language-kotlin-1.13.10+kotlin.2.3.20.jar"):
            CompletionStatusTest.write_jar(mods_dir / name, "fabric.mod.json", name)

    @staticmethod
    def write_jar(path: Path, name: str, content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(name, content)


if __name__ == "__main__":
    unittest.main()
