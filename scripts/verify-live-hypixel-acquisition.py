#!/usr/bin/env python3
"""Verify real Hypixel Nick Hider acquisition through the local-control bridge.

This script assumes Minecraft is already running, FloydAddons local-control is
enabled, and the client is already connected to Hypixel. It reads only the
local-control bridge sidecar/token, then queries loopback HTTP endpoints.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


LIVE_SCAN_SOURCES = {
    "known_tab",
    "tab_list",
    "tab_list_fallback",
    "scoreboard_title",
    "scoreboard_line",
    "scoreboard_any_title",
    "scoreboard_any_line",
}
LOOPBACK_HOSTS = {"127.0.0.1", "localhost", "::1"}
DEFAULT_CONFIG_CANDIDATES = (
    Path("config/floydaddons/control-bridge.json"),
    Path("run/config/floydaddons/control-bridge.json"),
)

FULL_ID_RE = re.compile(
    r"(?i)^(mini|mega|lobby|limbo|housing|prototype|node|legacylobby)\d{1,4}[a-z]{0,4}$"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify FloydAddons live Hypixel server-ID acquisition without synthetic scanText."
    )
    parser.add_argument(
        "--config",
        default=os.environ.get("FLOYD_CONTROL_CONFIG"),
        help=(
            "Path to control-bridge.json. Default: auto-detect "
            "config/floydaddons/control-bridge.json or run/config/floydaddons/control-bridge.json"
        ),
    )
    parser.add_argument("--host", default=os.environ.get("FLOYD_CONTROL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=parse_env_port("FLOYD_CONTROL_PORT"))
    parser.add_argument("--token", default=os.environ.get("FLOYD_CONTROL_TOKEN"))
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument(
        "--wait-seconds",
        type=float,
        default=0.0,
        help=(
            "Retry the same proof until this many seconds elapse. "
            "Useful while Minecraft is launching or joining Hypixel. Default: 0"
        ),
    )
    parser.add_argument(
        "--max-hit-age-ticks",
        type=int,
        default=1200,
        help="Maximum allowed age for the live server-ID hit in world ticks. Default: 1200",
    )
    parser.add_argument(
        "--preflight",
        action="store_true",
        help="Validate live Hypixel state and cached server-ID readiness without calling /replace-text.",
    )
    parser.add_argument(
        "--diagnose",
        action="store_true",
        help="Report live-proof readiness and the first missing precondition without calling /replace-text.",
    )
    parser.add_argument("--json", action="store_true", help="Print machine-readable proof JSON on success.")
    return parser.parse_args()


def parse_env_port(name: str) -> int | None:
    raw = os.environ.get(name)
    if not raw:
        return None
    return parse_port_value(name, raw)


def parse_port_value(label: str, value: Any) -> int:
    if isinstance(value, bool):
        raise SystemExit(f"Invalid {label}: {value}")
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise SystemExit(f"Invalid {label}: {value}") from exc


def load_bridge(args: argparse.Namespace) -> tuple[int, str]:
    port = args.port
    explicit_token = args.token is not None
    token = normalize_token(args.token)
    if port is not None:
        validate_port(port)
    if explicit_token and not token:
        raise SystemExit("Explicit --token does not contain a local-control token.")
    if port is not None and token:
        return port, token

    config_path = select_bridge_config_path(args.config)
    if not config_path.exists():
        raise SystemExit(
            f"Missing {config_path}. Start Minecraft/FloydAddons first or pass --port and --token."
        )
    try:
        config = json.loads(config_path.read_text())
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid local-control config JSON in {config_path}: {exc.msg}") from exc
    if not isinstance(config, dict):
        raise SystemExit(f"{config_path} must contain a JSON object.")
    if config.get("enabled") is not True:
        raise SystemExit(f"{config_path} reports local-control enabled=false. Enable FloydLocalControl, then rerun.")
    port = port if port is not None else parse_port_value(f"{config_path} port", config.get("port", 38765))
    token = token or normalize_token(config.get("token", ""))
    if not token:
        raise SystemExit(f"{config_path} does not contain a local-control token.")
    validate_port(port)
    return port, token


def select_bridge_config_path(config: str | None) -> Path:
    if config:
        path = Path(config)
        validate_bridge_config_path(path)
        return path

    candidates = list(DEFAULT_CONFIG_CANDIDATES)
    for path in candidates:
        validate_bridge_config_path(path)
        if path.exists():
            return path

    formatted = ", ".join(str(path) for path in candidates)
    raise SystemExit(
        "Missing FloydAddons local-control sidecar. "
        f"Checked: {formatted}. Start Minecraft/FloydAddons first or pass --config, --port, and --token."
    )


def normalize_token(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def validate_bridge_config_path(path: Path) -> None:
    if path.name != "control-bridge.json":
        raise SystemExit(
            f"Refusing to read non-bridge config path {path}. "
            "Pass the FloydAddons local-control control-bridge.json sidecar."
        )
    provider_part = "ygg" + "drasil"
    forbidden_parts = {"accounts", "auth", "session", "sessions", "tokens", provider_part}
    for part in path.parts:
        normalized = part.lower().replace("-", "_")
        if normalized in forbidden_parts or normalized.endswith("_accounts"):
            raise SystemExit(
                f"Refusing to read control-bridge.json from auth/account-like path {path}. "
                "Pass the FloydAddons local-control sidecar under config/floydaddons."
            )


def validate_port(port: int) -> None:
    if isinstance(port, bool) or port < 1 or port > 65535:
        raise SystemExit(f"Invalid local-control port: {port}")


def validate_loopback_host(host: str) -> None:
    if host not in LOOPBACK_HOSTS:
        raise SystemExit(
            f"Refusing to send the local-control token to non-loopback host {host!r}. "
            "Use 127.0.0.1, localhost, or ::1."
        )


def validate_max_hit_age_ticks(value: int) -> None:
    if isinstance(value, bool) or value < 0:
        raise SystemExit(f"Invalid max hit age ticks: {value}")


def validate_timeout(value: float) -> None:
    if isinstance(value, bool) or value <= 0:
        raise SystemExit(f"Invalid timeout: {value}")


def validate_wait_seconds(value: float) -> None:
    if isinstance(value, bool) or value < 0:
        raise SystemExit(f"Invalid wait seconds: {value}")


def url_host(host: str) -> str:
    if ":" in host and not host.startswith("["):
        return f"[{host}]"
    return host


def request_json(host: str, port: int, token: str, method: str, path: str, timeout: float, body: dict[str, Any] | None = None) -> dict[str, Any]:
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        f"http://{url_host(host)}:{port}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            payload = decode_response(response.read(), method, path)
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"{method} {path} failed with HTTP {exc.code}: {payload}") from exc
    except (TimeoutError, urllib.error.URLError) as exc:
        raise SystemExit(f"Could not reach local-control bridge at {host}:{port}: {exc}") from exc

    try:
        parsed = json.loads(payload)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{method} {path} returned invalid JSON: {exc.msg}") from exc
    if not isinstance(parsed, dict):
        raise SystemExit(f"{method} {path} returned non-object JSON.")
    return parsed


def decode_response(data: bytes, method: str, path: str) -> str:
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise SystemExit(f"{method} {path} returned non-UTF-8 response.") from exc


def int_state_field(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise SystemExit(f"{label} is not an integer: {value}")
    if isinstance(value, int):
        return value
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise SystemExit(f"{label} is not an integer: {value}") from exc


def nested(data: dict[str, Any], *keys: str) -> Any:
    current: Any = data
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def server_identity_values(server: dict[str, Any]) -> list[str]:
    values: list[str] = []
    for key in ("name", "address", "type"):
        value = server.get(key)
        if isinstance(value, str):
            values.append(value)
    for container in ("current", "connection"):
        child = server.get(container)
        if isinstance(child, dict):
            for key in ("name", "address", "type"):
                value = child.get(key)
                if isinstance(value, str):
                    values.append(value)
    return values


def clean_display_id(value: Any) -> str:
    return str(value).replace("\u200b", "").strip().lower()


def choose_cache_probe_id(cached: Any) -> str:
    if not isinstance(cached, list):
        raise SystemExit("Nick Hider serverId.cached is not a list.")
    ids = [clean_display_id(value) for value in cached]
    ids = [value for value in ids if value and any(ch.isdigit() for ch in value)]
    abbreviated = [value for value in ids if not FULL_ID_RE.match(value)]
    if not abbreviated:
        raise SystemExit(
            "No abbreviated cached server ID is available yet. Wait on Hypixel until "
            "scoreboard/tab acquisition has populated an abbreviated ID, then rerun."
        )
    return sorted(abbreviated, key=len)[0]


def acquisition_snapshot(server_id: dict[str, Any], world: dict[str, Any] | None = None) -> str:
    snapshot = {
        "lastScanSource": server_id.get("lastScanSource"),
        "lastScannedText": server_id.get("lastScannedText"),
        "scanHits": server_id.get("scanHits"),
        "lastHitTick": server_id.get("lastHitTick"),
        "cached": server_id.get("cached"),
        "current": server_id.get("current"),
        "tabEntryCount": server_id.get("tabEntryCount"),
        "scoreboardLineCount": server_id.get("scoreboardLineCount"),
        "scoreboard": server_id.get("scoreboard"),
        "worldTime": world.get("time") if isinstance(world, dict) else None,
    }
    return json.dumps(snapshot, indent=2, sort_keys=True)


def verify_live_state(state: dict[str, Any], max_hit_age_ticks: int) -> dict[str, Any]:
    if state.get("ok") is not True:
        raise SystemExit("/state did not report ok=true.")
    local_control = nested(state, "misc", "localControl")
    if not isinstance(local_control, dict):
        raise SystemExit("/state is missing misc.localControl proof.")
    if local_control.get("enabled") is not True or local_control.get("bridgeEnabled") is not True:
        raise SystemExit(f"FloydLocalControl is not enabled in /state: {local_control}")
    if local_control.get("running") is not True or local_control.get("settingsEnabled") is not True:
        raise SystemExit(f"FloydLocalControl is not running from an enabled sidecar in /state: {local_control}")
    if state.get("connected") is not True:
        raise SystemExit("Minecraft is not connected to a world/server.")

    server = state.get("server")
    if not isinstance(server, dict) or server.get("connected") is not True:
        raise SystemExit("/state.server does not report a connected server.")
    identity_values = server_identity_values(server)
    if not any("hypixel" in value.lower() for value in identity_values):
        raise SystemExit(f"Connected server does not look like Hypixel: {identity_values}")

    server_id = nested(state, "playerFeatures", "nickHider", "serverId")
    settings = nested(state, "playerFeatures", "nickHider", "settings")
    world = state.get("world")
    if not isinstance(server_id, dict) or not isinstance(settings, dict):
        raise SystemExit("/state is missing playerFeatures.nickHider.serverId/settings.")
    if not isinstance(world, dict) or isinstance(world.get("time"), bool) or not isinstance(world.get("time"), int):
        raise SystemExit("/state is missing world.time for live hit freshness proof.")
    if settings.get("serverIdHider") is not True:
        raise SystemExit("Server ID Hider is disabled. Enable Hiders -> Server ID Hider, then rerun.")

    source = str(server_id.get("lastScanSource", ""))
    if source not in LIVE_SCAN_SOURCES:
        raise SystemExit(f"Last server-ID scan source is not a live source: {source}\n{acquisition_snapshot(server_id, world)}")
    scan_hits = int_state_field(server_id.get("scanHits", 0), "Nick Hider server-ID scanHits")
    if scan_hits <= 0:
        raise SystemExit(f"Nick Hider has not recorded any live server-ID scan hits yet.\n{acquisition_snapshot(server_id, world)}")
    last_hit_tick = server_id.get("lastHitTick")
    if isinstance(last_hit_tick, bool) or not isinstance(last_hit_tick, int) or last_hit_tick < 0:
        raise SystemExit(f"Nick Hider has not recorded an in-world live hit tick yet.\n{acquisition_snapshot(server_id, world)}")
    current_tick = world["time"]
    hit_age_ticks = current_tick - last_hit_tick
    if hit_age_ticks < 0 or hit_age_ticks > max_hit_age_ticks:
        raise SystemExit(
            "Nick Hider live server-ID hit is too old for proof "
            f"(age={hit_age_ticks} ticks, max={max_hit_age_ticks}).\n{acquisition_snapshot(server_id, world)}"
        )

    try:
        probe_id = choose_cache_probe_id(server_id.get("cached"))
    except SystemExit as exc:
        raise SystemExit(f"{exc}\n{acquisition_snapshot(server_id, world)}") from exc

    return {
        "ok": True,
        "serverIdentity": identity_values,
        "lastScanSource": source,
        "lastScannedText": server_id.get("lastScannedText"),
        "scanHits": server_id.get("scanHits"),
        "lastHitTick": last_hit_tick,
        "worldTime": current_tick,
        "hitAgeTicks": hit_age_ticks,
        "cachedProbeId": probe_id,
    }


def run_preflight_once(args: argparse.Namespace) -> dict[str, Any]:
    port, token = load_bridge(args)

    state = request_json(args.host, port, token, "GET", "/state", args.timeout)
    proof = verify_live_state(state, args.max_hit_age_ticks)
    proof["preflight"] = True
    return proof


def run_once(args: argparse.Namespace) -> dict[str, Any]:
    if args.preflight:
        return run_preflight_once(args)

    port, token = load_bridge(args)
    state = request_json(args.host, port, token, "GET", "/state", args.timeout)
    proof = verify_live_state(state, args.max_hit_age_ticks)

    probe_id = proof["cachedProbeId"]
    probe_text = f"Floyd live cache probe {probe_id}"
    replacement = request_json(
        args.host,
        port,
        token,
        "POST",
        "/replace-text",
        args.timeout,
        {"text": probe_text},
    )
    if replacement.get("ok") is not True:
        raise SystemExit(f"/replace-text did not report ok=true: {replacement}")
    if replacement.get("scanned") is not None:
        raise SystemExit("/replace-text unexpectedly performed a synthetic scan.")
    if "scanned" not in replacement:
        raise SystemExit(f"/replace-text did not report the scanned field: {replacement}")
    if replacement.get("text") != probe_text:
        raise SystemExit(f"/replace-text did not echo the probe text: {replacement}")
    if replacement.get("changed") is not True or "fL0YD" not in str(replacement.get("replaced", "")):
        raise SystemExit(f"Cached server-ID replacement did not fire: {replacement}")

    proof["replacementChanged"] = replacement.get("changed")
    proof["replacement"] = replacement.get("replaced")
    return proof


def diagnose_once(args: argparse.Namespace) -> dict[str, Any]:
    try:
        proof = run_preflight_once(args)
    except SystemExit as exc:
        return {
            "ok": False,
            "diagnose": True,
            "ready": False,
            "error": str(exc),
        }
    proof["diagnose"] = True
    proof["ready"] = True
    return proof


def run_diagnose_with_wait(args: argparse.Namespace) -> dict[str, Any]:
    deadline = time.monotonic() + args.wait_seconds
    while True:
        proof = diagnose_once(args)
        if proof.get("ready") is True:
            return proof
        remaining = deadline - time.monotonic()
        if args.wait_seconds == 0 or remaining <= 0:
            return proof
        time.sleep(min(1.0, remaining))


def run_with_wait(args: argparse.Namespace) -> dict[str, Any]:
    deadline = time.monotonic() + args.wait_seconds
    while True:
        try:
            return run_once(args)
        except SystemExit:
            remaining = deadline - time.monotonic()
            if args.wait_seconds == 0 or remaining <= 0:
                raise
            time.sleep(min(1.0, remaining))


def main() -> int:
    args = parse_args()
    validate_loopback_host(args.host)
    validate_timeout(args.timeout)
    validate_wait_seconds(args.wait_seconds)
    validate_max_hit_age_ticks(args.max_hit_age_ticks)
    if args.diagnose:
        proof = run_diagnose_with_wait(args)
        if args.json:
            print(json.dumps(proof, indent=2, sort_keys=True))
        elif proof.get("ready") is True:
            print("live Hypixel acquisition readiness passed")
            print(f"server identity: {', '.join(proof['serverIdentity'])}")
            print(f"last scan source: {proof['lastScanSource']}")
            print(f"cached probe id: {proof['cachedProbeId']}")
        else:
            print("live Hypixel acquisition readiness failed")
            print(proof["error"])
        return 0 if proof.get("ready") is True else 1

    proof = run_with_wait(args)

    if args.json:
        print(json.dumps(proof, indent=2, sort_keys=True))
    elif args.preflight:
        print("live Hypixel acquisition preflight passed")
        print(f"server identity: {', '.join(proof['serverIdentity'])}")
        print(f"last scan source: {proof['lastScanSource']}")
        print(f"cached probe id: {proof['cachedProbeId']}")
    else:
        print("live Hypixel acquisition verified")
        print(f"server identity: {', '.join(proof['serverIdentity'])}")
        print(f"last scan source: {proof['lastScanSource']}")
        print(f"cached probe id: {proof['cachedProbeId']}")
        print(f"replacement: {proof['replacement']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
