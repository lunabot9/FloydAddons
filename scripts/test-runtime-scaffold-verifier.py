#!/usr/bin/env python3
"""Offline tests for verify-runtime-scaffold.py."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


def load_verifier():
    path = Path(__file__).with_name("verify-runtime-scaffold.py")
    spec = importlib.util.spec_from_file_location("runtime_scaffold_verifier", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class RuntimeScaffoldVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.verifier = load_verifier()

    def test_accepts_current_runtime_scaffold_shape(self) -> None:
        proof = self.verifier.verify_runtime_state(health(), state(), 38765)
        self.assertEqual(True, proof["health"]["ok"])
        self.assertEqual(16, proof["moduleCount"])
        self.assertEqual([], proof["categories"]["QOL"])
        self.assertEqual("floydaddons", proof["scaffold"]["modId"])

    def test_rejects_wrong_minecraft_version(self) -> None:
        bad = state()
        bad["scaffold"]["minecraftVersion"] = "1.8.9"
        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_runtime_state(health(), bad, 38765)
        self.assertIn("scaffold.minecraftVersion", str(raised.exception))

    def test_rejects_missing_floyd_module_group(self) -> None:
        bad = state()
        bad["modules"]["categories"] = [
            category for category in bad["modules"]["categories"] if category["name"] != "Hiders"
        ]
        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_runtime_state(health(), bad, 38765)
        self.assertIn("category modules differ", str(raised.exception))

    def test_rejects_non_empty_qol_surface(self) -> None:
        bad = state()
        bad["qol"] = {"Mob ESP": {}}
        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_runtime_state(health(), bad, 38765)
        self.assertIn("qol expected empty object", str(raised.exception))

    def test_rejects_disabled_local_control_runtime_proof(self) -> None:
        bad = state()
        bad["misc"]["localControl"]["running"] = False
        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_runtime_state(health(), bad, 38765)
        self.assertIn("misc.localControl.running", str(raised.exception))

    def test_rejects_non_title_screen_when_title_screen_is_required(self) -> None:
        bad = state()
        bad["screen"] = "net.minecraft.client.gui.screens.GenericMessageScreen"
        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_runtime_state(health(), bad, 38765, require_title_screen=True)
        self.assertIn("screen expected title screen", str(raised.exception))


def health():
    return {"ok": True, "port": 38765}


def state():
    return {
        "ok": True,
        "screen": "net.minecraft.client.gui.screens.TitleScreen",
        "screenTitle": "Title Screen",
        "server": {"connected": False},
        "qol": {},
        "eventBus": {"subscriberCount": 21},
        "misc": {
            "localControl": {
                "enabled": True,
                "bridgeEnabled": True,
                "running": True,
                "settingsEnabled": True,
                "settingsPort": 38765,
            }
        },
        "scaffold": {
            "modId": "floydaddons",
            "modName": "Floyd Addons",
            "version": "2.0.1",
            "minecraftVersion": "1.21.11",
            "entrypoint": "com.odtheking.odin.FloydAddonsMod",
            "mixinConfig": "floydaddons.mixins.json",
            "resourceNamespace": "floydaddons",
            "activeScaffold": "Odin Fabric module/config/event/ClickGUI",
            "vendoredBehaviorSource": "vendor/floydaddons-fabric",
        },
        "modules": {
            "moduleCount": 16,
            "categories": [
                category("Render", "Click GUI", "Render", "X-Ray", "Animations", "HUD", "Mob ESP"),
                category("Hiders", "Hiders"),
                category("Player", "Neck Hider", "Player Size"),
                category("Camera", "Camera"),
                category("Cosmetic", "Custom Skin", "Custom Cape", "Cone Hat"),
                category("QOL"),
                category("Misc", "Discord Presence", "Local Control", "Floyd Compatibility"),
            ],
        },
    }


def category(name: str, *modules: str):
    return {"name": name, "modules": [{"name": module} for module in modules]}


if __name__ == "__main__":
    unittest.main()
