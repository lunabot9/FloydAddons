#!/usr/bin/env python3
"""Offline tests for live-hypixel-status.sh."""

from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "live-hypixel-status.sh"


class LiveHypixelStatusTest(unittest.TestCase):
    def test_writes_valid_not_ready_status_without_temp_leftovers(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            status_file = Path(directory) / "live-status.json"
            missing_config = Path(directory) / "missing" / "control-bridge.json"
            result = self.run_status(status_file, "--config", str(missing_config), "--timeout", "1")

            self.assertEqual(0, result.returncode, result.stderr)
            payload = json.loads(status_file.read_text())
            stdout_payload = json.loads(result.stdout)
            self.assertEqual(payload, stdout_payload)
            self.assertFalse(payload["ok"])
            self.assertTrue(payload["diagnose"])
            self.assertFalse(payload["ready"])
            self.assertIn("Missing", payload["error"])
            self.assertIn("control-bridge.json", payload["error"])
            self.assertIn("runtime", payload)
            self.assertIn("launcherRunning", payload["runtime"])
            self.assertIn("gameProcessRunning", payload["runtime"])
            self.assertEqual(38765, payload["runtime"]["localControlPort"])
            self.assertIn("localControlListening", payload["runtime"])
            self.assertIn("live status recorded not-ready state", result.stderr)
            self.assertEqual([], list(Path(directory).glob("live-status.json.tmp.*")))

    def test_respects_custom_status_file_env(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            status_file = Path(directory) / "nested" / "status.json"
            missing_config = Path(directory) / "missing" / "control-bridge.json"
            result = self.run_status(status_file, "--config", str(missing_config), "--timeout", "1")

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue(status_file.exists())
            payload = json.loads(status_file.read_text())
            self.assertEqual(payload, json.loads(result.stdout))
            self.assertFalse(payload["ready"])
            self.assertIn("runtime", payload)

    def run_status(self, status_file: Path, *args: str) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env["FLOYDADDONS_LIVE_STATUS_FILE"] = str(status_file)
        return subprocess.run(
            [str(SCRIPT), *args],
            cwd=ROOT,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
