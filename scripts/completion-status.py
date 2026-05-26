#!/usr/bin/env python3
"""Report Floyd-in-Odin completion status from proof artifacts."""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
from pathlib import Path
from typing import Any


DEFAULT_RUNTIME_PROOF = Path("logs/runtime-scaffold-proof.json")
DEFAULT_LIVE_PROOF = Path("logs/live-hypixel-proof.json")
DEFAULT_MINECRAFT_ROOT = Path.home() / "Library/Application Support/minecraft"
LIVE_SCAN_SOURCES = {
    "known_tab",
    "tab_list",
    "tab_list_fallback",
    "scoreboard_title",
    "scoreboard_line",
    "scoreboard_any_title",
    "scoreboard_any_line",
}
MAX_LIVE_HIT_AGE_TICKS = 1200


def current_version() -> str:
    env_version = os.environ.get("FLOYDADDONS_VERSION", "").strip()
    if env_version:
        return env_version.lstrip("v")

    properties = Path("gradle.properties")
    if properties.exists():
        for line in properties.read_text().splitlines():
            if line.startswith("mod_version="):
                return line.split("=", 1)[1].strip().lstrip("v")
    return "2.0.3"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Summarize Floyd-in-Odin completion proof status.")
    parser.add_argument("--runtime-proof", default=str(DEFAULT_RUNTIME_PROOF))
    parser.add_argument("--live-proof", default=str(DEFAULT_LIVE_PROOF))
    parser.add_argument("--minecraft-root", default=str(DEFAULT_MINECRAFT_ROOT))
    parser.add_argument("--build-jar", default=str(Path("build/libs") / f"FloydAddons-{current_version()}.jar"))
    parser.add_argument("--json", action="store_true", help="Print JSON. Text output is the default.")
    parser.add_argument(
        "--require-complete",
        action="store_true",
        help=(
            "Exit nonzero unless runtime scaffold proof, live launcher install "
            "readiness, and live Hypixel proof are complete."
        ),
    )
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    try:
        payload = json.loads(path.read_text())
    except json.JSONDecodeError as exc:
        return {"_invalid": f"invalid JSON: {exc.msg}"}
    if not isinstance(payload, dict):
        return {"_invalid": "JSON root is not an object"}
    return payload


def runtime_status(path: Path) -> dict[str, Any]:
    proof = load_json(path)
    if proof is None:
        return {"ok": False, "path": str(path), "reason": "missing runtime scaffold proof"}
    if "_invalid" in proof:
        return {"ok": False, "path": str(path), "reason": proof["_invalid"]}

    scaffold = proof.get("scaffold")
    categories = proof.get("categories")
    ok = (
        proof.get("moduleCount") == 16
        and proof.get("screen") == "net.minecraft.client.gui.screens.TitleScreen"
        and isinstance(scaffold, dict)
        and scaffold.get("modId") == "floydaddons"
        and scaffold.get("activeScaffold") == "Odin Fabric module/config/event/ClickGUI"
        and isinstance(categories, dict)
        and categories.get("QOL") == []
        and proof.get("localControl", {}).get("running") is True
    )
    return {
        "ok": ok,
        "path": str(path),
        "moduleCount": proof.get("moduleCount"),
        "screen": proof.get("screen"),
        "serverConnected": proof.get("serverConnected"),
        "reason": None if ok else "runtime scaffold proof does not match expected Floyd-in-Odin state",
    }


def live_status(path: Path) -> dict[str, Any]:
    proof = load_json(path)
    if proof is None:
        return {"ok": False, "path": str(path), "reason": "missing live Hypixel proof"}
    if "_invalid" in proof:
        return {"ok": False, "path": str(path), "reason": proof["_invalid"]}

    hit_age = proof.get("hitAgeTicks")
    scan_hits = proof.get("scanHits")
    last_hit_tick = proof.get("lastHitTick")
    world_time = proof.get("worldTime")
    cached_probe_id = proof.get("cachedProbeId")
    ok = (
        proof.get("ok") is True
        and proof.get("preflight") is not True
        and proof.get("diagnose") is not True
        and proof.get("replacementChanged") is True
        and "fL0YD" in str(proof.get("replacement", ""))
        and isinstance(cached_probe_id, str)
        and cached_probe_id.strip() != ""
        and str(cached_probe_id) not in str(proof.get("replacement", ""))
        and proof.get("lastScanSource") in LIVE_SCAN_SOURCES
        and isinstance(scan_hits, int)
        and not isinstance(scan_hits, bool)
        and scan_hits > 0
        and isinstance(last_hit_tick, int)
        and not isinstance(last_hit_tick, bool)
        and last_hit_tick >= 0
        and isinstance(world_time, int)
        and not isinstance(world_time, bool)
        and isinstance(hit_age, int)
        and not isinstance(hit_age, bool)
        and 0 <= hit_age <= MAX_LIVE_HIT_AGE_TICKS
        and world_time - last_hit_tick == hit_age
        and isinstance(proof.get("serverIdentity"), list)
        and any("hypixel" in str(value).lower() for value in proof.get("serverIdentity", []))
    )
    return {
        "ok": ok,
        "path": str(path),
        "serverIdentity": proof.get("serverIdentity"),
        "lastScanSource": proof.get("lastScanSource"),
        "scanHits": proof.get("scanHits"),
        "lastHitTick": proof.get("lastHitTick"),
        "worldTime": proof.get("worldTime"),
        "cachedProbeId": proof.get("cachedProbeId"),
        "hitAgeTicks": proof.get("hitAgeTicks"),
        "replacementChanged": proof.get("replacementChanged"),
        "replacement": proof.get("replacement"),
        "reason": None if ok else "live Hypixel proof does not prove cached replacement from Hypixel acquisition",
    }


def live_install_status(minecraft_root: Path, build_jar: Path = Path("build/libs") / f"FloydAddons-{current_version()}.jar") -> dict[str, Any]:
    script = Path(__file__).with_name("live-install-status.py")
    spec = importlib.util.spec_from_file_location("live_install_status", script)
    if spec is None or spec.loader is None:
        return {"ok": False, "minecraftRoot": str(minecraft_root), "reason": f"could not load {script}"}
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    report = module.status(minecraft_root, build_jar)
    return {
        "ok": report.get("ok") is True,
        "minecraftRoot": report.get("minecraftRoot"),
        "buildJar": report.get("buildJar"),
        "profile": report.get("launcherProfile"),
        "mods": report.get("mods"),
        "reason": None if report.get("ok") is True else "live launcher install is not ready",
        "remaining": report.get("remaining", []),
    }


def completion_report(
    runtime_path: Path,
    live_path: Path,
    minecraft_root: Path = DEFAULT_MINECRAFT_ROOT,
    build_jar: Path = Path("build/libs") / f"FloydAddons-{current_version()}.jar",
) -> dict[str, Any]:
    runtime = runtime_status(runtime_path)
    install = live_install_status(minecraft_root, build_jar)
    live = live_status(live_path)
    complete = runtime["ok"] and install["ok"] and live["ok"]
    return {
        "complete": complete,
        "runtimeScaffold": runtime,
        "liveInstall": install,
        "liveHypixel": live,
        "remaining": [] if complete else remaining_reasons(runtime, install, live),
    }


def remaining_reasons(*statuses: dict[str, Any]) -> list[str]:
    reasons: list[str] = []
    for status in statuses:
        reason = status.get("reason")
        if reason:
            reasons.append(str(reason))
        for detail in status.get("remaining", []):
            reasons.append(str(detail))
    return reasons


def print_text(report: dict[str, Any]) -> None:
    print(f"complete={str(report['complete']).lower()}")
    print(f"runtimeScaffold={str(report['runtimeScaffold']['ok']).lower()} {report['runtimeScaffold']['path']}")
    print(f"liveInstall={str(report['liveInstall']['ok']).lower()} {report['liveInstall']['minecraftRoot']}")
    print(f"liveHypixel={str(report['liveHypixel']['ok']).lower()} {report['liveHypixel']['path']}")
    for reason in report["remaining"]:
        print(f"remaining: {reason}")


def main() -> int:
    args = parse_args()
    report = completion_report(
        Path(args.runtime_proof),
        Path(args.live_proof),
        Path(args.minecraft_root).expanduser(),
        Path(args.build_jar).expanduser(),
    )
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print_text(report)
    if args.require_complete and not report["complete"]:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
