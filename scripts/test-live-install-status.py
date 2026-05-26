#!/usr/bin/env python3
"""Offline tests for live-install-status.py."""

from __future__ import annotations

import importlib.util
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).with_name("live-install-status.py")


def current_version() -> str:
    env_version = os.environ.get("FLOYDADDONS_VERSION", "").strip()
    if env_version:
        return env_version.lstrip("v")
    for line in Path("gradle.properties").read_text().splitlines():
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip().lstrip("v")
    return "2.0.2"


def load_status():
    spec = importlib.util.spec_from_file_location("live_install_status", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class LiveInstallStatusTest(unittest.TestCase):
    def setUp(self) -> None:
        self.status_module = load_status()

    def test_reports_ready_when_profile_and_runtime_jars_exist(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_jar = Path(directory) / "build" / f"FloydAddons-{current_version()}.jar"
            self.write_ready_install(root, build_jar)

            report = self.status_module.status(root, build_jar)

        self.assertTrue(report["ok"])
        self.assertEqual([], report["remaining"])
        self.assertTrue(report["launcherProfile"]["ok"])
        self.assertEqual(report["buildJar"]["sha256"], report["mods"]["floydaddons"]["sha256"])

    def test_reports_missing_profile_and_dependency_jars(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_jar = Path(directory) / "build" / f"FloydAddons-{current_version()}.jar"
            build_jar.parent.mkdir()
            self.write_jar(build_jar, "fabric.mod.json", '{"id":"floydaddons"}')
            (root / "mods").mkdir()
            (root / "mods" / f"FloydAddons-{current_version()}.jar").write_bytes(build_jar.read_bytes())

            report = self.status_module.status(root, build_jar)

        self.assertFalse(report["ok"])
        self.assertIn("missing Fabric loader profile version JSON", report["remaining"])
        self.assertIn("missing launcher profile for fabric-loader-1.21.11", report["remaining"])
        self.assertIn("missing fabricApi jar", report["remaining"])
        self.assertIn("missing fabricLanguageKotlin jar", report["remaining"])

    def test_rejects_installed_floyd_jar_that_does_not_match_build(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_jar = Path(directory) / "build" / f"FloydAddons-{current_version()}.jar"
            self.write_ready_install(root, build_jar)
            (root / "mods" / f"FloydAddons-{current_version()}.jar").write_text("stale floyd")

            report = self.status_module.status(root, build_jar)

        self.assertFalse(report["ok"])
        self.assertIn("installed FloydAddons jar does not match current build", report["remaining"])

    def test_rejects_invalid_dependency_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_jar = Path(directory) / "build" / f"FloydAddons-{current_version()}.jar"
            self.write_ready_install(root, build_jar)
            (root / "mods" / "fabric-api-0.141.3+1.21.11.jar").write_text("not a jar")

            report = self.status_module.status(root, build_jar)

        self.assertFalse(report["ok"])
        self.assertIn("invalid fabricApi jar", report["remaining"])

    def test_rejects_invalid_current_build_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_jar = Path(directory) / "build" / f"FloydAddons-{current_version()}.jar"
            self.write_ready_install(root, build_jar)
            build_jar.write_text("not a jar")

            report = self.status_module.status(root, build_jar)

        self.assertFalse(report["ok"])
        self.assertIn("current FloydAddons build jar is not a valid jar", report["remaining"])

    def test_rejects_missing_current_build_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_jar = Path(directory) / "build" / f"FloydAddons-{current_version()}.jar"
            self.write_ready_install(root, build_jar)
            build_jar.unlink()

            report = self.status_module.status(root, build_jar)

        self.assertFalse(report["ok"])
        self.assertIn("missing current FloydAddons build jar", report["remaining"])

    @staticmethod
    def write_ready_install(root: Path, build_jar: Path) -> None:
        version_dir = root / "versions" / "fabric-loader-0.18.5-1.21.11"
        mods_dir = root / "mods"
        version_dir.mkdir(parents=True)
        mods_dir.mkdir()
        build_jar.parent.mkdir(parents=True, exist_ok=True)
        LiveInstallStatusTest.write_jar(build_jar, "fabric.mod.json", '{"id":"floydaddons"}')
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
        (mods_dir / f"FloydAddons-{current_version()}.jar").write_bytes(build_jar.read_bytes())
        for name in ("fabric-api-0.141.3+1.21.11.jar", "fabric-language-kotlin-1.13.10+kotlin.2.3.20.jar"):
            LiveInstallStatusTest.write_jar(mods_dir / name, "fabric.mod.json", name)

    @staticmethod
    def write_jar(path: Path, name: str, content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(name, content)


if __name__ == "__main__":
    unittest.main()
