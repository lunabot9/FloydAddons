#!/usr/bin/env python3
"""Runs the full FloydAddons perf BASELINE TABLE: every suspect feature A/B-measured in
its stress arena via perf-protocol.py, plus the ClickGUI open-vs-closed special case.

Prereqs: client running with /perf bridge, arenas built (perf-arenas.py), client window
focus state left alone for the whole run (~1h). Writes per-run JSON to perf-results/
and a markdown summary table to stdout + perf-results/BASELINE.md.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RESULTS = REPO / "perf-results"

# Load the Bridge class from perf-protocol.py (repo convention: importlib, file has a dash).
_spec = importlib.util.spec_from_file_location("perf_protocol", REPO / "scripts/perf-protocol.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
Bridge = _mod.Bridge
frame_metrics = _mod.frame_metrics

ARENAS: list[dict] = [
    {
        "world": "perfarena-ores",
        # blocksearch command also force-enables the module; the runner disables it below.
        "prepare": ["/fa blocksearch minecraft:diamond_ore"],
        "features": ["Block Search", "X-Ray"],
    },
    {
        "world": "perfarena-ents",
        "prepare": ["/fa mob-esp add type minecraft:husk", "/fa mob-esp add name Stand"],
        "features": ["Mob ESP", "Player ESP"],
    },
    {
        "world": "perfarena-hud",
        "prepare": [],
        "features": ["Custom Scoreboard", "Inventory HUD", "Day Tracker", "HUD"],
        "clickgui": True,
    },
]


def open_world(bridge: Bridge, world: str) -> None:
    state = bridge.get("/state")
    if state.get("connected"):
        bridge.action({"action": "disconnect"})
        deadline = time.time() + 60
        while time.time() < deadline and bridge.get("/state").get("connected"):
            time.sleep(2)
    bridge.action({"action": "openWorld", "world": world})
    deadline = time.time() + 120
    while time.time() < deadline:
        time.sleep(2)
        s = bridge.get("/state")
        if s.get("connected") and s.get("server", {}).get("singleplayer"):
            time.sleep(5)
            return
    raise SystemExit(f"world {world} did not load")


def run_feature(world: str, feature: str, seconds: float, warmup: float, repeats: int) -> Path:
    out = RESULTS / f"{world}--{feature.replace(' ', '_')}.json"
    cmd = [sys.executable, str(REPO / "scripts/perf-protocol.py"),
           "--feature", feature, "--seconds", str(seconds), "--warmup", str(warmup),
           "--repeats", str(repeats), "--json", str(out)]
    print(f"\n### {world} / {feature}", flush=True)
    subprocess.run(cmd, check=True)
    return out


def run_clickgui(bridge: Bridge, world: str, seconds: float, warmup: float, repeats: int) -> Path:
    """ClickGUI is a Screen, not a toggleable module: A=closed, B=open."""
    print(f"\n### {world} / ClickGUI (closed vs open)", flush=True)
    closed_runs, open_runs = [], []
    for i in range(repeats):
        bridge.request("POST", "/screen", {"screen": "close"})
        time.sleep(warmup)
        closed = bridge.perf(seconds)
        closed_runs.append(closed)
        print(f"repeat {i + 1} CLOSED: {json.dumps(frame_metrics(closed))}", flush=True)
        bridge.request("POST", "/screen", {"screen": "clickgui"})
        time.sleep(warmup)
        opened = bridge.perf(seconds)
        open_runs.append(opened)
        print(f"repeat {i + 1} OPEN  : {json.dumps(frame_metrics(opened))}", flush=True)
    bridge.request("POST", "/screen", {"screen": "clickgui"})
    time.sleep(2)
    sections = bridge.perf(min(seconds, 15.0), sections=True)
    bridge.request("POST", "/screen", {"screen": "close"})

    result = {
        "feature": "ClickGUI", "world": world, "seconds": seconds, "repeats": repeats,
        "offRuns": closed_runs, "onRuns": open_runs, "sectionsOn": sections,
        "ab": _mod.summarize_ab([frame_metrics(s) for s in closed_runs],
                                [frame_metrics(s) for s in open_runs]),
    }
    out = RESULTS / f"{world}--ClickGUI.json"
    out.write_text(json.dumps(result, indent=2))
    for key, row in result["ab"].items():
        mark = " <-- FINDING" if row["finding"] else ""
        print(f"{key:>10}: closed {row['offMean']:.3f}  open {row['onMean']:.3f}  "
              f"delta {row['delta']:+.3f}  spread {row['runToRunSpread']:.3f}{mark}")
    return out


def emit_table() -> str:
    rows = []
    for path in sorted(RESULTS.glob("*--*.json")):
        data = json.loads(path.read_text())
        ab = data.get("ab")
        if not ab:
            continue
        world = data.get("world") or path.stem.split("--")[0]
        top_sections = []
        sec = (data.get("sectionsOn") or {}).get("sections") or {}
        for label, s in list(sec.items())[:3]:
            top_sections.append(f"{label} p50={s['p50us']:.0f}us/{s['bytesPerFrame']}B")
        rows.append({
            "feature": data["feature"], "world": world,
            "p50": ab["p50"], "p99": ab["p99"], "alloc": ab["allocMBps"],
            "fps": ab["fps"], "sections": "; ".join(top_sections),
        })
    rows.sort(key=lambda r: abs(r["p50"]["delta"]), reverse=True)
    lines = [
        "| Feature | Arena | Δp50 ms (spread) | Δp99 ms (spread) | Δalloc MB/s | Δfps | finding | top sections (on) |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for r in rows:
        finding = "YES" if (r["p50"]["finding"] or r["p99"]["finding"] or r["alloc"]["finding"]) else "no"
        lines.append(
            f"| {r['feature']} | {r['world']} | {r['p50']['delta']:+.3f} ({r['p50']['runToRunSpread']:.3f}) "
            f"| {r['p99']['delta']:+.3f} ({r['p99']['runToRunSpread']:.3f}) "
            f"| {r['alloc']['delta']:+.2f} ({r['alloc']['runToRunSpread']:.2f}) "
            f"| {r['fps']['delta']:+.1f} | {finding} | {r['sections']} |")
    return "\n".join(lines)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--seconds", type=float, default=30.0)
    p.add_argument("--warmup", type=float, default=10.0)
    p.add_argument("--repeats", type=int, default=3)
    p.add_argument("--only-arena", help="Run just this arena (world name)")
    args = p.parse_args()

    RESULTS.mkdir(exist_ok=True)
    bridge = Bridge(_mod.DEFAULT_PORT, _mod.DEFAULT_INSTANCE)

    for arena in ARENAS:
        if args.only_arena and arena["world"] != args.only_arena:
            continue
        open_world(bridge, arena["world"])
        for cmd in arena["prepare"]:
            bridge.request("POST", "/chat", {"message": cmd})
            time.sleep(1)
        # Quiet background: every measured feature in this arena OFF before any A/B.
        for feature in arena["features"]:
            bridge.set_module(feature, False)
        time.sleep(2)
        for feature in arena["features"]:
            run_feature(arena["world"], feature, args.seconds, args.warmup, args.repeats)
        if arena.get("clickgui"):
            run_clickgui(bridge, arena["world"], args.seconds, args.warmup, args.repeats)

    table = emit_table()
    (RESULTS / "BASELINE.md").write_text(table + "\n")
    print("\n=== BASELINE TABLE (by |delta p50|) ===\n" + table)


if __name__ == "__main__":
    main()
