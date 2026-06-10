#!/usr/bin/env python3
"""A/B measurement protocol for FloydAddons features over the /perf bridge endpoint.

Per repeat: ensure feature OFF -> warmup -> sample /perf (OFF) -> toggle ON via
setModuleEnabled -> warmup -> sample /perf (ON). Repeats give run-to-run variance;
a delta smaller than the cross-repeat spread is NOT a finding. The headline A/B runs
WITHOUT sections (section probes add overhead); one extra sections=1 window runs with
the feature ON for per-section attribution.

The client window focus state must stay constant for the whole run (unfocused MC
throttles to ~9 fps); the script flags samples whose fps looks throttled.

Usage:
  python3 scripts/perf-protocol.py --feature "Block Search" --world perfarena-ores \
      --seconds 30 --warmup 10 --repeats 3 --json out.json
  python3 scripts/perf-protocol.py --ambient --seconds 30   # no toggling, current scene
"""
from __future__ import annotations

import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

DEFAULT_INSTANCE = Path.home() / "Library/Application Support/PrismLauncher/instances/floyd pvp/minecraft"
DEFAULT_PORT = 38769
THROTTLED_FPS = 12.0


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--feature", help="Module name for setModuleEnabled (e.g. 'Block Search')")
    p.add_argument("--ambient", action="store_true", help="No toggling: one sample of the current scene")
    p.add_argument("--world", help="openWorld this saved world first (must exist)")
    p.add_argument("--seconds", type=float, default=30.0)
    p.add_argument("--warmup", type=float, default=10.0)
    p.add_argument("--repeats", type=int, default=3)
    p.add_argument("--port", type=int, default=DEFAULT_PORT)
    p.add_argument("--instance", type=Path, default=DEFAULT_INSTANCE)
    p.add_argument("--sections", action="store_true", default=True,
                   help="Also take one sections=1 attribution window with the feature ON (default)")
    p.add_argument("--no-sections", dest="sections", action="store_false")
    p.add_argument("--json", type=Path, help="Write the full result JSON here")
    args = p.parse_args()
    if not args.ambient and not args.feature:
        p.error("--feature is required unless --ambient")
    if args.repeats < 1:
        p.error("--repeats must be >= 1")
    if args.repeats < 2 and not args.ambient:
        print("WARNING: <2 repeats gives no variance estimate; nothing can be a finding", file=sys.stderr)
    return args


class Bridge:
    def __init__(self, port: int, instance: Path):
        self.base = f"http://127.0.0.1:{port}"
        token_file = instance / "config/floydaddons/control-bridge.json"
        try:
            self.token = json.loads(token_file.read_text())["token"]
        except (OSError, KeyError, json.JSONDecodeError) as e:
            raise SystemExit(f"cannot read bridge token from {token_file}: {e} "
                             f"(is --instance right / has the client started the bridge?)")

    def request(self, method: str, path: str, body: dict[str, Any] | None = None,
                timeout: float = 10.0) -> dict[str, Any]:
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.token}")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")[:300]
            raise SystemExit(f"bridge {method} {path} -> HTTP {e.code}: {detail}")

    def get(self, path: str, timeout: float = 10.0) -> dict[str, Any]:
        return self.request("GET", path, timeout=timeout)

    def action(self, body: dict[str, Any]) -> dict[str, Any]:
        return self.request("POST", "/action", body)

    def perf(self, seconds: float, sections: bool = False) -> dict[str, Any]:
        path = f"/perf?seconds={seconds:g}" + ("&sections=1" if sections else "")
        return self.get(path, timeout=seconds + 20)

    def set_module(self, module: str, enabled: bool) -> None:
        # ModuleManager.modules is keyed by module.name.lowercase().
        result = self.action({"action": "setModuleEnabled", "module": module.lower(), "enabled": enabled})
        if not result.get("ok", False):
            raise SystemExit(f"setModuleEnabled failed: {result}")

    def module_enabled(self, module: str) -> bool:
        state = self.get("/state")
        for category in state["modules"]["categories"]:
            for mod in category["modules"]:
                if mod["name"].lower() == module.lower():
                    return bool(mod["enabled"])
        raise SystemExit(f"module not found in /state: {module}")

    def ensure_world(self, world: str) -> None:
        # Deterministic scene: always disconnect and reopen the REQUESTED world. The bridge does
        # not expose the loaded world's name, so trusting "some singleplayer world is loaded"
        # would silently measure the wrong scene.
        state = self.get("/state")
        if state.get("connected"):
            self.action({"action": "disconnect"})
            deadline = time.time() + 60
            while time.time() < deadline and self.get("/state").get("connected"):
                time.sleep(2)
            time.sleep(2)
        self.action({"action": "openWorld", "world": world})
        deadline = time.time() + 120
        while time.time() < deadline:
            time.sleep(2)
            state = self.get("/state")
            if state.get("connected") and state.get("server", {}).get("singleplayer"):
                time.sleep(3)  # settle chunk load
                return
        raise SystemExit(f"world '{world}' did not load within 120s")


def frame_metrics(sample: dict[str, Any]) -> dict[str, float]:
    fm = sample["frameMs"]
    return {
        "fps": sample["fps"],
        "p50": fm["p50"], "p95": fm["p95"], "p99": fm["p99"], "max": fm["max"],
        "allocMBps": sample["alloc"]["renderThreadBytesPerSecond"] / 1e6,
        "gcCollections": sample["gc"]["collections"],
    }


def spread(values: list[float]) -> float:
    return max(values) - min(values) if values else 0.0


def summarize_ab(off: list[dict[str, float]], on: list[dict[str, float]]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    enough_repeats = len(off) >= 2 and len(on) >= 2
    for key in ("p50", "p95", "p99", "fps", "allocMBps"):
        off_vals = [s[key] for s in off]
        on_vals = [s[key] for s in on]
        delta = statistics.mean(on_vals) - statistics.mean(off_vals)
        variance = max(spread(off_vals), spread(on_vals))
        out[key] = {
            "off": off_vals, "on": on_vals,
            "offMean": round(statistics.mean(off_vals), 4),
            "onMean": round(statistics.mean(on_vals), 4),
            "delta": round(delta, 4),
            "runToRunSpread": round(variance, 4),
            # A finding needs >=2 repeats for a variance estimate; a delta smaller than the
            # cross-repeat spread is noise, not a finding.
            "finding": enough_repeats and abs(delta) > variance,
        }
    return out


def main() -> None:
    args = parse_args()
    bridge = Bridge(args.port, args.instance)

    health = bridge.get("/health")
    if "/perf" not in health.get("endpoints", []):
        raise SystemExit("bridge does not advertise /perf — old jar running?")

    if args.world:
        bridge.ensure_world(args.world)

    result: dict[str, Any] = {
        "feature": args.feature, "world": args.world, "seconds": args.seconds,
        "warmup": args.warmup, "repeats": args.repeats,
        "scaffold": bridge.get("/state").get("scaffold"),
        "startedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
    }

    if args.ambient:
        # Headline numbers come from a NO-sections window (the probes add overhead); the
        # attribution window is separate.
        print(f"ambient sample {args.seconds:g}s ...", flush=True)
        time.sleep(args.warmup)
        sample = bridge.perf(args.seconds)
        result["ambient"] = sample
        if args.sections:
            result["ambientSections"] = bridge.perf(min(args.seconds, 15.0), sections=True)
        print(json.dumps(frame_metrics(sample), indent=2))
        if sample["fps"] < THROTTLED_FPS:
            print(f"WARNING: ambient fps {sample['fps']:.1f} below {THROTTLED_FPS} — window throttled?",
                  file=sys.stderr)
    else:
        initial = bridge.module_enabled(args.feature)
        off_runs: list[dict[str, Any]] = []
        on_runs: list[dict[str, Any]] = []
        try:
            for i in range(args.repeats):
                bridge.set_module(args.feature, False)
                time.sleep(args.warmup)
                off = bridge.perf(args.seconds)
                off_runs.append(off)
                print(f"repeat {i + 1} OFF: {json.dumps(frame_metrics(off))}", flush=True)

                bridge.set_module(args.feature, True)
                time.sleep(args.warmup)
                on = bridge.perf(args.seconds)
                on_runs.append(on)
                print(f"repeat {i + 1} ON : {json.dumps(frame_metrics(on))}", flush=True)

            if args.sections:
                bridge.set_module(args.feature, True)
                time.sleep(2)
                result["sectionsOn"] = bridge.perf(min(args.seconds, 15.0), sections=True)
        finally:
            bridge.set_module(args.feature, initial)

        off_m = [frame_metrics(s) for s in off_runs]
        on_m = [frame_metrics(s) for s in on_runs]
        result["offRuns"] = off_runs
        result["onRuns"] = on_runs
        result["ab"] = summarize_ab(off_m, on_m)

        throttled = [m for m in off_m + on_m if m["fps"] < THROTTLED_FPS]
        if throttled:
            result["warning"] = f"{len(throttled)} sample(s) below {THROTTLED_FPS} fps — window likely unfocused/throttled"
            print(f"WARNING: {result['warning']}", file=sys.stderr)

        print("\n=== A/B summary ===")
        for key, row in result["ab"].items():
            mark = " <-- FINDING" if row["finding"] else ""
            print(f"{key:>10}: off {row['offMean']:.3f}  on {row['onMean']:.3f}  "
                  f"delta {row['delta']:+.3f}  spread {row['runToRunSpread']:.3f}{mark}")

    if args.json:
        args.json.write_text(json.dumps(result, indent=2))
        print(f"\nwrote {args.json}")


if __name__ == "__main__":
    main()
