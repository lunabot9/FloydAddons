#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

port="${FLOYD_CONTROL_PORT:-38765}"
config="${FLOYD_CONTROL_CONFIG:-run/config/floydaddons/control-bridge.json}"
log_file="${FLOYD_RUNTIME_SMOKE_LOG:-logs/runtime-scaffold-smoke.log}"
proof_file="${FLOYD_RUNTIME_SMOKE_PROOF:-logs/runtime-scaffold-proof.json}"
timeout_seconds="${FLOYD_RUNTIME_SMOKE_TIMEOUT_SECONDS:-90}"

mkdir -p "$(dirname "$log_file")"
mkdir -p "$(dirname "$proof_file")"

if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port $port is already listening. Stop the existing client or set FLOYD_CONTROL_PORT." >&2
    exit 1
fi

cleanup() {
    if [[ -n "${client_pid:-}" ]] && kill -0 "$client_pid" >/dev/null 2>&1; then
        kill "$client_pid" >/dev/null 2>&1 || true
        wait "$client_pid" >/dev/null 2>&1 || true
    fi
    for _ in {1..20}; do
        if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
            return
        fi
        sleep 0.5
    done
    listener_pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "$listener_pids" ]]; then
        kill $listener_pids >/dev/null 2>&1 || true
        sleep 1
    fi
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "Port $port is still listening after client shutdown." >&2
        exit 1
    fi
}
trap cleanup EXIT

rm -f "$log_file" "$proof_file"
FLOYDADDONS_DEVAUTH=false ./gradlew runClient --quiet >"$log_file" 2>&1 &
client_pid=$!

deadline=$((SECONDS + timeout_seconds))
while (( SECONDS < deadline )); do
    if ! kill -0 "$client_pid" >/dev/null 2>&1; then
        echo "runClient exited before local-control became available. Log: $log_file" >&2
        wait "$client_pid" || true
        exit 1
    fi
    if [[ -f "$config" ]] && python3 scripts/verify-runtime-scaffold.py --json --require-title-screen >/dev/null 2>&1; then
        python3 scripts/verify-runtime-scaffold.py --json --require-title-screen | tee "$proof_file"
        exit 0
    fi
    sleep 1
done

echo "Timed out waiting for runtime scaffold proof. Log: $log_file" >&2
exit 1
