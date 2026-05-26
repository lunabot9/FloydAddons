#!/usr/bin/env python3
"""Report non-auth live launcher install readiness for FloydAddons."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import zipfile
from pathlib import Path
from typing import Any


def current_version() -> str:
    env_version = os.environ.get("FLOYDADDONS_VERSION", "").strip()
    if env_version:
        return env_version.lstrip("v")

    properties = Path("gradle.properties")
    if properties.exists():
        for line in properties.read_text().splitlines():
            if line.startswith("mod_version="):
                return line.split("=", 1)[1].strip().lstrip("v")
    return "2.0.2"


EXPECTED = {
    "minecraftVersion": "1.21.11",
    "loaderVersion": "0.18.5",
    "fabricApi": "fabric-api-0.141.3+1.21.11.jar",
    "fabricKotlin": "fabric-language-kotlin-1.13.10+kotlin.2.3.20.jar",
    "floydJar": f"FloydAddons-{current_version()}.jar",
    "profileName": "fabric-loader-1.21.11",
    "versionId": "fabric-loader-0.18.5-1.21.11",
}


def parse_args() -> argparse.Namespace:
    default_root = Path.home() / "Library/Application Support/minecraft"
    parser = argparse.ArgumentParser(description="Check FloydAddons live install readiness without reading account files.")
    parser.add_argument("--minecraft-root", default=str(default_root))
    parser.add_argument("--build-jar", default=str(Path("build/libs") / EXPECTED["floydJar"]))
    parser.add_argument("--json", action="store_true", help="Print JSON. Text output is the default.")
    return parser.parse_args()


def file_state(path: Path, require_zip: bool = False) -> dict[str, Any]:
    state = {
        "path": str(path),
        "exists": path.exists(),
        "sizeBytes": path.stat().st_size if path.exists() else None,
    }
    if path.exists() and path.is_file():
        state["sha256"] = sha256(path)
        if require_zip:
            state["validJar"] = zipfile.is_zipfile(path)
    elif require_zip:
        state["validJar"] = False
    return state


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_profiles(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text())
    except json.JSONDecodeError:
        return {}
    profiles = payload.get("profiles")
    return profiles if isinstance(profiles, dict) else {}


def status(minecraft_root: Path, build_jar: Path = Path("build/libs") / EXPECTED["floydJar"]) -> dict[str, Any]:
    version_dir = minecraft_root / "versions" / EXPECTED["versionId"]
    version_json = version_dir / f"{EXPECTED['versionId']}.json"
    mods_dir = minecraft_root / "mods"
    profiles = load_profiles(minecraft_root / "launcher_profiles.json")
    profile = profiles.get(EXPECTED["profileName"])
    profile_ok = (
        isinstance(profile, dict)
        and profile.get("lastVersionId") == EXPECTED["versionId"]
        and profile.get("type") == "custom"
    )
    mods = {
        "floydaddons": file_state(mods_dir / EXPECTED["floydJar"], require_zip=True),
        "fabricApi": file_state(mods_dir / EXPECTED["fabricApi"], require_zip=True),
        "fabricLanguageKotlin": file_state(mods_dir / EXPECTED["fabricKotlin"], require_zip=True),
    }
    build = file_state(build_jar, require_zip=True)
    floyd_matches_build = (
        build["exists"]
        and mods["floydaddons"]["exists"]
        and build.get("sha256") == mods["floydaddons"].get("sha256")
    )
    ok = (
        version_json.exists()
        and profile_ok
        and floyd_matches_build
        and build.get("validJar") is True
        and all(mod["exists"] and (mod["sizeBytes"] or 0) > 0 and mod.get("validJar") is True for mod in mods.values())
    )
    return {
        "ok": ok,
        "minecraftRoot": str(minecraft_root),
        "expected": EXPECTED,
        "fabricVersion": file_state(version_json),
        "buildJar": build,
        "launcherProfile": {
            "exists": isinstance(profile, dict),
            "name": profile.get("name") if isinstance(profile, dict) else None,
            "lastVersionId": profile.get("lastVersionId") if isinstance(profile, dict) else None,
            "type": profile.get("type") if isinstance(profile, dict) else None,
            "ok": profile_ok,
        },
        "mods": mods,
        "remaining": [] if ok else remaining(version_json, profile_ok, mods, build, floyd_matches_build),
    }


def remaining(
    version_json: Path,
    profile_ok: bool,
    mods: dict[str, dict[str, Any]],
    build: dict[str, Any],
    floyd_matches_build: bool,
) -> list[str]:
    missing: list[str] = []
    if not version_json.exists():
        missing.append("missing Fabric loader profile version JSON")
    if not profile_ok:
        missing.append("missing launcher profile for fabric-loader-1.21.11")
    for name, mod in mods.items():
        if not mod["exists"]:
            missing.append(f"missing {name} jar")
        elif (mod["sizeBytes"] or 0) <= 0:
            missing.append(f"empty {name} jar")
        elif mod.get("validJar") is not True:
            missing.append(f"invalid {name} jar")
    if not build["exists"]:
        missing.append("missing current FloydAddons build jar")
    elif build.get("validJar") is not True:
        missing.append("current FloydAddons build jar is not a valid jar")
    elif mods["floydaddons"]["exists"] and not floyd_matches_build:
        missing.append("installed FloydAddons jar does not match current build")
    return missing


def print_text(report: dict[str, Any]) -> None:
    print(f"ok={str(report['ok']).lower()}")
    for reason in report["remaining"]:
        print(f"remaining: {reason}")


def main() -> int:
    args = parse_args()
    report = status(Path(args.minecraft_root).expanduser(), Path(args.build_jar).expanduser())
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print_text(report)
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
