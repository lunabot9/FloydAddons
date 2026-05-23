#!/usr/bin/env python3
"""Verify a running FloydAddons client exposes the Odin scaffold state."""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
from pathlib import Path
from typing import Any


EXPECTED_CATEGORY_MODULES = {
    "Render": ["Click GUI", "Render", "X-Ray", "Animations", "HUD", "Mob ESP"],
    "Hiders": ["Hiders"],
    "Player": ["Neck Hider", "Player Size"],
    "Camera": ["Camera"],
    "Cosmetic": ["Custom Skin", "Custom Cape", "Cone Hat"],
    "QOL": [],
    "Misc": ["Discord Presence", "Local Control", "Floyd Compatibility"],
}

EXPECTED_SCAFFOLD = {
    "modId": "floydaddons",
    "modName": "Floyd Addons",
    "version": "0.1.0",
    "minecraftVersion": "1.21.11",
    "entrypoint": "com.odtheking.odin.FloydAddonsMod",
    "mixinConfig": "floydaddons.mixins.json",
    "resourceNamespace": "floydaddons",
    "activeScaffold": "Odin Fabric module/config/event/ClickGUI",
    "vendoredBehaviorSource": "vendor/floydaddons-fabric",
}


def load_bridge_helpers() -> Any:
    path = Path(__file__).with_name("verify-live-hypixel-acquisition.py")
    spec = importlib.util.spec_from_file_location("live_hypixel_verifier", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify a running FloydAddons client is mounted as Floyd behavior on Odin scaffolding."
    )
    parser.add_argument(
        "--config",
        default=os.environ.get("FLOYD_CONTROL_CONFIG", "run/config/floydaddons/control-bridge.json"),
        help="Path to control-bridge.json. Default: run/config/floydaddons/control-bridge.json",
    )
    parser.add_argument("--host", default=os.environ.get("FLOYD_CONTROL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument("--token", default=os.environ.get("FLOYD_CONTROL_TOKEN"))
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument(
        "--require-title-screen",
        action="store_true",
        help="Require /state to report Minecraft's title screen.",
    )
    parser.add_argument("--json", action="store_true", help="Print machine-readable proof JSON on success.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    bridge = load_bridge_helpers()
    bridge.validate_loopback_host(args.host)
    bridge.validate_timeout(args.timeout)
    port, token = bridge.load_bridge(args)

    health = bridge.request_json(args.host, port, token, "GET", "/health", args.timeout)
    state = bridge.request_json(args.host, port, token, "GET", "/state", args.timeout)
    proof = verify_runtime_state(health, state, port, require_title_screen=args.require_title_screen)
    if args.json:
        print(json.dumps(proof, indent=2, sort_keys=True))
    else:
        print("runtime Floyd-in-Odin scaffold verified")
    return 0


def verify_runtime_state(
    health: dict[str, Any],
    state: dict[str, Any],
    expected_port: int,
    require_title_screen: bool = False,
) -> dict[str, Any]:
    failures: list[str] = []

    if health.get("ok") is not True:
        failures.append("/health ok is not true")
    if health.get("port") != expected_port:
        failures.append(f"/health port expected {expected_port}, got {health.get('port')!r}")

    if state.get("ok") is not True:
        failures.append("/state ok is not true")
    if require_title_screen and state.get("screen") != "net.minecraft.client.gui.screens.TitleScreen":
        failures.append(f"screen expected title screen, got {state.get('screen')!r}")

    scaffold = state.get("scaffold")
    if not isinstance(scaffold, dict):
        failures.append("/state scaffold is missing")
        scaffold = {}
    for key, expected in EXPECTED_SCAFFOLD.items():
        if scaffold.get(key) != expected:
            failures.append(f"scaffold.{key} expected {expected!r}, got {scaffold.get(key)!r}")

    modules = state.get("modules")
    if not isinstance(modules, dict):
        failures.append("/state modules is missing")
        modules = {}
    if modules.get("moduleCount") != 16:
        failures.append(f"moduleCount expected 16, got {modules.get('moduleCount')!r}")
    categories = modules.get("categories")
    if not isinstance(categories, list):
        failures.append("modules.categories is missing")
        categories = []

    actual_category_modules: dict[str, list[str]] = {}
    for category in categories:
        if not isinstance(category, dict):
            continue
        name = category.get("name")
        module_entries = category.get("modules", [])
        if isinstance(name, str) and isinstance(module_entries, list):
            actual_category_modules[name] = [
                module.get("name")
                for module in module_entries
                if isinstance(module, dict) and isinstance(module.get("name"), str)
            ]
    if actual_category_modules != EXPECTED_CATEGORY_MODULES:
        failures.append(
            "category modules differ from Floyd GUI grouping: "
            f"expected {EXPECTED_CATEGORY_MODULES!r}, got {actual_category_modules!r}"
        )

    qol = state.get("qol")
    if qol != {}:
        failures.append(f"qol expected empty object, got {qol!r}")

    event_bus = state.get("eventBus")
    if not isinstance(event_bus, dict) or not isinstance(event_bus.get("subscriberCount"), int):
        failures.append("eventBus.subscriberCount is missing")

    local_control = nested(state, "misc", "localControl")
    if not isinstance(local_control, dict):
        failures.append("misc.localControl proof is missing")
        local_control = {}
    for key in ("enabled", "bridgeEnabled", "running", "settingsEnabled"):
        if local_control.get(key) is not True:
            failures.append(f"misc.localControl.{key} expected true, got {local_control.get(key)!r}")
    if local_control.get("settingsPort") != expected_port:
        failures.append(f"misc.localControl.settingsPort expected {expected_port}, got {local_control.get('settingsPort')!r}")

    if failures:
        raise SystemExit("\n".join(failures))

    return {
        "health": {"ok": health.get("ok"), "port": health.get("port")},
        "screen": state.get("screen"),
        "screenTitle": state.get("screenTitle"),
        "serverConnected": nested(state, "server", "connected"),
        "moduleCount": modules.get("moduleCount"),
        "categories": actual_category_modules,
        "qol": qol,
        "eventBusSubscriberCount": event_bus.get("subscriberCount") if isinstance(event_bus, dict) else None,
        "localControl": {
            "enabled": local_control.get("enabled"),
            "bridgeEnabled": local_control.get("bridgeEnabled"),
            "running": local_control.get("running"),
            "settingsEnabled": local_control.get("settingsEnabled"),
            "settingsPort": local_control.get("settingsPort"),
        },
        "scaffold": {key: scaffold.get(key) for key in EXPECTED_SCAFFOLD},
    }


def nested(data: dict[str, Any], *keys: str) -> Any:
    current: Any = data
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


if __name__ == "__main__":
    sys.exit(main())
