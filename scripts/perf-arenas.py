#!/usr/bin/env python3
"""Builds the reproducible FloydAddons perf stress arenas as saved singleplayer worlds.

Three same-scene arenas (re-openable by name via openWorld for every measurement):
  perfarena-ores : 64x64x64 layer-striped ore cube (8 ore types x 8 cycles; 32768
                   diamond_ore — forces Block Search past its 10k render cap into the
                   per-frame distance-sort path). Player fixed 31 blocks west, facing it.
  perfarena-ents : 50 named armor stands + 50 NoAI husks (husks = daylight-proof zombies;
                   difficulty easy so they don't peaceful-despawn). Player faces the crowd.
  perfarena-hud  : plain flat scene, fixed player position (HUD-panel A/B runs only toggle
                   modules, the scene just has to be identical).

All arenas: creative+cheats flat world, fixed seed, frozen time/weather/ticks, no
natural spawning. Requires the client DISCONNECTED at start (uses the disconnect action
between worlds). Safe to re-run: skips arenas whose world already exists unless --force.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.request
from pathlib import Path
from typing import Any

DEFAULT_INSTANCE = Path.home() / "Library/Application Support/PrismLauncher/instances/floyd pvp/minecraft"
DEFAULT_PORT = 38769
SEED = 38769

ORES = ["iron_ore", "diamond_ore", "gold_ore", "redstone_ore",
        "lapis_ore", "coal_ore", "emerald_ore", "stone"]

FREEZE_RULES = [
    "/gamerule doDaylightCycle false",
    "/gamerule doWeatherCycle false",
    "/gamerule doMobSpawning false",
    "/gamerule doInsomnia false",
    "/gamerule doTraderSpawning false",
    "/gamerule doPatrolSpawning false",
    "/gamerule randomTickSpeed 0",
    "/gamerule doFireTick false",
    "/time set 6000",
]


class Bridge:
    def __init__(self, port: int, instance: Path):
        self.base = f"http://127.0.0.1:{port}"
        token_file = instance / "config/floydaddons/control-bridge.json"
        self.token = json.loads(token_file.read_text())["token"]

    def request(self, method: str, path: str, body: dict[str, Any] | None = None,
                timeout: float = 15.0) -> dict[str, Any]:
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.token}")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode())

    def state(self) -> dict[str, Any]:
        return self.request("GET", "/state")

    def action(self, body: dict[str, Any]) -> dict[str, Any]:
        return self.request("POST", "/action", body)

    def chat(self, message: str) -> None:
        result = self.request("POST", "/chat", {"message": message})
        if not result.get("ok", False):
            raise SystemExit(f"chat failed for {message!r}: {result}")

    def screenshot(self, name: str) -> str:
        return self.request("POST", "/screenshot", {"fileName": name}).get("path", "")

    def wait(self, predicate, what: str, timeout: float = 120.0) -> dict[str, Any]:
        deadline = time.time() + timeout
        while time.time() < deadline:
            state = self.state()
            if predicate(state):
                return state
            time.sleep(2)
        raise SystemExit(f"timed out waiting for {what}")

    def in_world(self, state: dict[str, Any]) -> bool:
        return bool(state.get("connected")) and bool(state.get("server", {}).get("singleplayer"))

    def disconnect(self) -> None:
        if not self.state().get("connected"):
            return
        self.action({"action": "disconnect"})
        self.wait(lambda s: not s.get("connected"), "disconnect")
        time.sleep(2)

    def create_world(self, name: str) -> None:
        self.disconnect()
        self.action({"action": "createFreshWorld", "world": name, "flat": True,
                     "gamemode": "creative", "cheats": True, "seed": SEED})
        self.wait(self.in_world, f"world {name} load", timeout=180)
        time.sleep(4)  # settle chunks before /fill


def world_exists(bridge: Bridge, name: str) -> bool:
    try:
        bridge.action({"action": "createFreshWorld", "world": name, "flat": True,
                       "gamemode": "creative", "cheats": True, "seed": SEED})
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        if "world_already_exists" in body:
            return True
        if "disconnect_before_create_world" in body:
            raise SystemExit("client must be disconnected (or world half-created)")
        raise
    return False  # creation just started!


def freeze(bridge: Bridge) -> None:
    for rule in FREEZE_RULES:
        bridge.chat(rule)
        time.sleep(0.2)


def build_ores(bridge: Bridge) -> None:
    print("building perfarena-ores ...", flush=True)
    bridge.create_world("perfarena-ores")
    freeze(bridge)
    for i, y in enumerate(range(-60, 4)):
        ore = ORES[i % len(ORES)]
        bridge.chat(f"/fill 16 {y} 16 79 {y} 79 minecraft:{ore}")
        time.sleep(0.15)
    bridge.chat("/tp @s -15 -59 48 -90 5")
    time.sleep(1)
    path = bridge.screenshot("perfarena-ores.png")
    print(f"  screenshot: {path}")
    bridge.disconnect()


def build_ents(bridge: Bridge) -> None:
    print("building perfarena-ents ...", flush=True)
    bridge.create_world("perfarena-ents")
    freeze(bridge)
    bridge.chat("/difficulty easy")  # peaceful would despawn the husks
    n = 0
    for row in range(5):
        for col in range(10):
            x = -13 + col * 3
            z = 8 + row * 3
            n += 1
            bridge.chat(f'/summon armor_stand {x} -60 {z} {{CustomName:"Stand {n}",CustomNameVisible:1b,NoGravity:1b}}')
            bridge.chat(f"/summon husk {x + 1} -60 {z + 1} {{NoAI:1b,Silent:1b,PersistenceRequired:1b}}")
            time.sleep(0.1)
    bridge.chat("/tp @s 0 -59 -6 0 5")
    time.sleep(1)
    path = bridge.screenshot("perfarena-ents.png")
    print(f"  screenshot: {path}")
    bridge.disconnect()


HUD_ITEMS = [
    "diamond_sword", "diamond_pickaxe", "diamond_axe", "diamond_shovel", "bow",
    "golden_apple", "ender_pearl", "cobblestone", "oak_planks", "diamond_block",
    "iron_ingot", "gold_ingot", "redstone", "lapis_lazuli", "emerald",
    "coal", "arrow", "bread", "cooked_beef", "torch",
    "obsidian", "tnt", "water_bucket", "lava_bucket", "elytra",
    "netherite_ingot", "blaze_rod", "ghast_tear", "string", "bone",
    "gunpowder", "slime_ball", "egg", "snowball", "oak_log", "glass",
]


def build_hud(bridge: Bridge) -> None:
    print("building perfarena-hud ...", flush=True)
    bridge.create_world("perfarena-hud")
    freeze(bridge)
    # Real sidebar content so Custom Scoreboard has 15 lines to lay out.
    bridge.chat('/scoreboard objectives add perfarena dummy "Perf Arena"')
    bridge.chat("/scoreboard objectives setdisplay sidebar perfarena")
    for i in range(1, 16):
        bridge.chat(f"/scoreboard players set ArenaLine_{i:02d} perfarena {16 - i}")
        time.sleep(0.1)
    # Full inventory (hotbar + 27 main slots) so Inventory HUD draws a full grid.
    for item in HUD_ITEMS:
        bridge.chat(f"/give @s minecraft:{item} 64")
        time.sleep(0.1)
    bridge.chat("/tp @s 0 -59 0 0 0")
    time.sleep(1)
    path = bridge.screenshot("perfarena-hud.png")
    print(f"  screenshot: {path}")
    bridge.disconnect()


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--port", type=int, default=DEFAULT_PORT)
    p.add_argument("--instance", type=Path, default=DEFAULT_INSTANCE)
    p.add_argument("--only", choices=["ores", "ents", "hud"], help="Build just one arena")
    args = p.parse_args()

    bridge = Bridge(args.port, args.instance)
    health = bridge.request("GET", "/health")
    if "/perf" not in health.get("endpoints", []):
        raise SystemExit("bridge does not advertise /perf — old jar running?")

    builders = {"ores": build_ores, "ents": build_ents, "hud": build_hud}
    targets = [args.only] if args.only else ["ores", "ents", "hud"]
    saves = args.instance / "saves"
    for key in targets:
        if (saves / f"perfarena-{key}").exists():
            print(f"perfarena-{key} already exists — skipping (delete the save to rebuild)")
            continue
        builders[key](bridge)
    print("done")


if __name__ == "__main__":
    main()
