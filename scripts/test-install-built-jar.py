#!/usr/bin/env python3
"""Offline tests for install-built-jar.sh."""

from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "install-built-jar.sh"


def runtime_jar_name() -> str:
    jars = sorted(
        path.name
        for path in (ROOT / "build" / "libs").glob("FloydAddons-*.jar")
        if not path.name.endswith("-sources.jar")
    )
    if len(jars) != 1:
        raise AssertionError(f"expected exactly one FloydAddons runtime jar, got {jars}")
    return jars[0]


class InstallBuiltJarTest(unittest.TestCase):
    def test_rejects_missing_target(self) -> None:
        result = self.run_installer()

        self.assertEqual(2, result.returncode)
        self.assertIn("usage:", result.stderr)

    def test_rejects_non_mods_target(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result = self.run_installer(Path(directory) / "not-mods")

        self.assertEqual(2, result.returncode)
        self.assertIn("ending in /mods", result.stderr)

    def test_installs_runtime_jar_and_replaces_only_old_floyd_jars(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            mods_dir = Path(directory) / "mods"
            mods_dir.mkdir()
            old_floyd = mods_dir / "FloydAddons-old.jar"
            other_mod = mods_dir / "OtherMod.jar"
            source_jar = mods_dir / "FloydAddons-old-sources.jar"
            old_floyd.write_text("old")
            other_mod.write_text("other")
            source_jar.write_text("sources")

            result = self.run_installer(mods_dir)

            self.assertEqual(0, result.returncode, result.stderr)
            installed = Path(result.stdout.strip())
            self.assertTrue(installed.exists())
            self.assertEqual(runtime_jar_name(), installed.name)
            self.assertFalse(old_floyd.exists())
            self.assertTrue(other_mod.exists())
            self.assertTrue(source_jar.exists())

    def test_installs_runtime_dependency_jars_from_explicit_dependency_dir(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mods_dir = root / "mods"
            deps_dir = root / "deps"
            mods_dir.mkdir()
            deps_dir.mkdir()
            fabric_api = deps_dir / "fabric-api-0.141.3+1.21.11.jar"
            fabric_kotlin = deps_dir / "fabric-language-kotlin-1.13.10+kotlin.2.3.20.jar"
            old_fabric_api = mods_dir / "fabric-api-old.jar"
            other_mod = mods_dir / "OtherMod.jar"
            fabric_api.write_text("fabric-api")
            fabric_kotlin.write_text("fabric-kotlin")
            old_fabric_api.write_text("old-api")
            other_mod.write_text("other")

            result = self.run_installer(
                mods_dir,
                {
                    "FLOYDADDONS_SKIP_RUNTIME_DEPS": "false",
                    "FLOYDADDONS_RUNTIME_DEPS_DIR": str(deps_dir),
                },
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertFalse(old_fabric_api.exists())
            self.assertEqual("fabric-api", (mods_dir / fabric_api.name).read_text())
            self.assertEqual("fabric-kotlin", (mods_dir / fabric_kotlin.name).read_text())
            self.assertTrue(other_mod.exists())
            self.assertIn(fabric_api.name, result.stdout)
            self.assertIn(fabric_kotlin.name, result.stdout)

    def test_installs_fabric_profile_with_repo_pinned_versions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mods_dir = root / "minecraft" / "mods"
            fake_bin = root / "bin"
            fake_java = fake_bin / "java"
            fake_installer = root / "fabric-installer.jar"
            fake_args = root / "java-args.txt"
            fake_bin.mkdir()
            fake_installer.write_text("installer")
            fake_java.write_text(
                "#!/bin/sh\n"
                "printf '%s\\n' \"$@\" > \"$FAKE_JAVA_ARGS\"\n"
            )
            fake_java.chmod(0o755)

            result = self.run_installer(
                mods_dir,
                {
                    "FLOYDADDONS_SKIP_FABRIC_PROFILE": "false",
                    "FLOYDADDONS_FABRIC_INSTALLER_JAR": str(fake_installer),
                    "FAKE_JAVA_ARGS": str(fake_args),
                    "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
                },
            )

            self.assertEqual(0, result.returncode, result.stderr)
            java_args = fake_args.read_text().splitlines()
            self.assertEqual("-jar", java_args[0])
            self.assertEqual(str(fake_installer), java_args[1])
            self.assertIn("client", java_args)
            self.assertIn("-dir", java_args)
            self.assertIn(str(root / "minecraft"), java_args)
            self.assertIn("-mcversion", java_args)
            self.assertIn("1.21.11", java_args)
            self.assertIn("-loader", java_args)
            self.assertIn("0.18.5", java_args)
            self.assertIn("fabric-loader-0.18.5-1.21.11.json", result.stdout)

    def test_keep_old_jars_preserves_existing_floyd_runtime_jars(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            mods_dir = Path(directory) / "mods"
            mods_dir.mkdir()
            old_floyd = mods_dir / "FloydAddons-old.jar"
            old_floyd.write_text("old")

            result = self.run_installer(mods_dir, {"FLOYDADDONS_KEEP_OLD_JARS": "true"})

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue(old_floyd.exists())
            self.assertTrue((mods_dir / runtime_jar_name()).exists())

    def run_installer(self, target: Path | None = None, extra_env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env["FLOYDADDONS_SKIP_BUILD"] = "true"
        env["FLOYDADDONS_SKIP_FABRIC_PROFILE"] = "true"
        env["FLOYDADDONS_SKIP_RUNTIME_DEPS"] = "true"
        env["PATH"] = os.pathsep.join(("/usr/bin", "/bin", "/usr/sbin", "/sbin"))
        if extra_env:
            env.update(extra_env)
        command = [str(SCRIPT)]
        if target is not None:
            command.append(str(target))
        return subprocess.run(
            command,
            cwd=ROOT,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
