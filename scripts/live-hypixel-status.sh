#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

status_file="${FLOYDADDONS_LIVE_STATUS_FILE:-logs/live-hypixel-status.json}"
mkdir -p "$(dirname "$status_file")"

tmp_file="$(mktemp "${status_file}.tmp.XXXXXX")"
set +e
python3 scripts/verify-live-hypixel-acquisition.py --diagnose --json "$@" >"$tmp_file"
diagnose_status="$?"
set -e

if [[ ! -s "$tmp_file" ]]; then
    echo "live status command produced an empty JSON artifact: $status_file" >&2
    rm -f "$tmp_file"
    exit 1
fi

if ! python3 -m json.tool "$tmp_file" >/dev/null; then
    echo "live status command produced invalid JSON: $status_file" >&2
    rm -f "$tmp_file"
    exit 1
fi

python3 - "$tmp_file" <<'PY'
import json
import subprocess
import sys
from pathlib import Path

path = Path(sys.argv[1])
payload = json.loads(path.read_text())

ps = subprocess.run(
    ["ps", "-axo", "pid=,command="],
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.DEVNULL,
    check=False,
)
processes = []
for line in ps.stdout.splitlines():
    stripped = line.strip()
    if not stripped:
        continue
    pid, _, command = stripped.partition(" ")
    processes.append({"pid": pid, "command": command})

launcher_processes = [
    process for process in processes
    if "Minecraft.app/Contents/MacOS/launcher" in process["command"]
    or "/minecraft/launcher/" in process["command"]
]
game_processes = [
    process for process in processes
    if "net.minecraft.client.main.Main" in process["command"]
    or "fabric-loader" in process["command"]
]
lsof = subprocess.run(
    ["lsof", "-nP", "-iTCP:38765", "-sTCP:LISTEN"],
    text=True,
    stdout=subprocess.PIPE,
    stderr=subprocess.DEVNULL,
    check=False,
)
payload["runtime"] = {
    "launcherRunning": bool(launcher_processes),
    "launcherProcessCount": len(launcher_processes),
    "gameProcessRunning": bool(game_processes),
    "gameProcessCount": len(game_processes),
    "localControlPort": 38765,
    "localControlListening": bool(lsof.stdout.strip()),
}
path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
PY

mv "$tmp_file" "$status_file"
cat "$status_file"
if [[ "$diagnose_status" -ne 0 ]]; then
    echo "live status recorded not-ready state in $status_file" >&2
fi
