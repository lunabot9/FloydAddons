#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ "${FLOYDADDONS_RUN_RUNTIME_SMOKE:-false}" == "true" ]] &&
    ([[ "${FLOYDADDONS_RUN_LIVE_PREFLIGHT:-false}" == "true" ]] ||
        [[ "${FLOYDADDONS_RUN_LIVE_HYPIXEL:-false}" == "true" ]] ||
        [[ "${FLOYDADDONS_RUN_LIVE_DIAGNOSE:-false}" == "true" ]]); then
    echo "FLOYDADDONS_RUN_RUNTIME_SMOKE cannot be combined with live Hypixel proof flags." >&2
    echo "Runtime smoke launches a non-auth dev client; live proof requires an already-connected Hypixel client." >&2
    exit 1
fi

./gradlew test
./gradlew build

python3 - <<'PY'
import pathlib

for script in (
    pathlib.Path("scripts/verify-live-hypixel-acquisition.py"),
    pathlib.Path("scripts/verify-runtime-scaffold.py"),
    pathlib.Path("scripts/verify-legacy-clickgui-runtime.py"),
    pathlib.Path("scripts/live-install-status.py"),
):
    compile(script.read_text(), str(script), "exec")
PY
python3 scripts/test-live-hypixel-verifier.py
python3 scripts/test-runtime-scaffold-verifier.py
python3 scripts/test-legacy-clickgui-runtime-verifier.py
python3 scripts/test-install-built-jar.py
python3 scripts/test-live-hypixel-status.py
python3 scripts/test-completion-status.py
python3 scripts/test-live-install-status.py
python3 scripts/verify-live-hypixel-acquisition.py --help >/dev/null
python3 scripts/verify-runtime-scaffold.py --help >/dev/null
python3 scripts/verify-legacy-clickgui-runtime.py --help >/dev/null
python3 scripts/completion-status.py --help >/dev/null
python3 scripts/live-install-status.py --help >/dev/null
bash -n scripts/run-runtime-scaffold-smoke.sh
bash -n scripts/install-built-jar.sh
bash -n scripts/live-hypixel-status.sh

live_wait_args=()
if [[ -n "${FLOYDADDONS_LIVE_WAIT_SECONDS:-}" ]]; then
    live_wait_args+=(--wait-seconds "$FLOYDADDONS_LIVE_WAIT_SECONDS")
fi

write_success_proof() {
    local proof_file="$1"
    shift
    mkdir -p "$(dirname "$proof_file")"
    rm -f "$proof_file"
    local tmp_file
    tmp_file="$(mktemp "${proof_file}.tmp.XXXXXX")"
    if "$@" | tee "$tmp_file"; then
        if [[ ! -s "$tmp_file" ]]; then
            echo "live proof command produced an empty JSON artifact: $proof_file" >&2
            rm -f "$tmp_file"
            return 1
        fi
        if ! python3 -m json.tool "$tmp_file" >/dev/null; then
            echo "live proof command produced invalid JSON: $proof_file" >&2
            rm -f "$tmp_file"
            return 1
        fi
        mv "$tmp_file" "$proof_file"
    else
        rm -f "$tmp_file"
        return 1
    fi
}

write_diagnose_artifact() {
    local proof_file="$1"
    shift
    mkdir -p "$(dirname "$proof_file")"
    rm -f "$proof_file"
    local tmp_file
    tmp_file="$(mktemp "${proof_file}.tmp.XXXXXX")"
    set +e
    "$@" | tee "$tmp_file"
    local command_status="${PIPESTATUS[0]}"
    set -e
    if [[ ! -s "$tmp_file" ]]; then
        echo "live diagnose command produced an empty JSON artifact: $proof_file" >&2
        rm -f "$tmp_file"
        return 1
    fi
    if ! python3 -m json.tool "$tmp_file" >/dev/null; then
        echo "live diagnose command produced invalid JSON: $proof_file" >&2
        rm -f "$tmp_file"
        return 1
    fi
    mv "$tmp_file" "$proof_file"
    if [[ "$command_status" -ne 0 ]]; then
        echo "live diagnose recorded not-ready state in $proof_file" >&2
    fi
    return 0
}

python3 - <<'PY'
import json
import pathlib

root = pathlib.Path("src/main/java/com/odtheking/mixin")
mixin = json.loads(pathlib.Path("src/main/resources/floydaddons.mixins.json").read_text())
listed = {
    name
    for section in ("client", "mixins")
    for name in mixin.get(section, [])
}
sources = {
    str(path.relative_to(root)).replace("/", ".")[:-5]
    for path in root.rglob("*.java")
}
missing = sorted(listed - sources)
unlisted = sorted(sources - listed)

if missing or unlisted:
    if missing:
        print("missing active mixin source files:")
        print("\n".join(missing))
    if unlisted:
        print("unlisted active mixin source files:")
        print("\n".join(unlisted))
    raise SystemExit(1)

print("active mixin config exactly matches source files")
PY

old_odin_source_regex='odinclient|OdinClient|1\.8\.9|features.impl.(boss|dungeon|nether|skyblock)|utils.skyblock|hypixelapi|HypixelData|RequestUtils|WebSocketConnection|CataCommand|DevCommand|DungeonWaypoint|PetCommand|PosMsg|Soopy|Termsim|WaypointCommand|AutoSessionID|PersonalBest|ServerUtils|JsonResourceLoader|CustomGUIImpl|DrawContextRenderer|PlayerUtils|createSoundSettings|setClipboardContent|DevModule|isDevModule|containsOneOf|equalsOneOf|matchesOneOf|capitalizeFirst|toFixed|formatTime|romanToInt|getBlockBounds|clickSlot|formatNumber|sendChatMessage|getCenteredText|getChatBreak|Vec2|floorVec|isXZInterceptable|Category\.(DUNGEON|BOSS|NETHER|SKYBLOCK)|val (DUNGEON|BOSS|SKYBLOCK|NETHER)|8Fqsg5xBP3|modmenu\.discord|update_checker|OdinMod|com\.odtheking\.odin\.OdinMod'
if rg -n "$old_odin_source_regex" src/main/kotlin src/main/java src/main/resources build.gradle.kts README.md MIGRATION.md THIRD_PARTY_NOTICES.md; then
    echo "old Odin residue audit failed" >&2
    exit 1
fi

floyd_grouping_source_regex='features\.impl\.qol|features/impl/qol|features\.impl\.player\.FloydSkin|features/impl/player/FloydSkin'
if rg -n "$floyd_grouping_source_regex" src/main/kotlin src/main/java src/main/resources build.gradle.kts; then
    echo "Floyd GUI grouping audit failed" >&2
    exit 1
fi

stale_dirs=(
    src/main/kotlin/com/odtheking/odin/utils/network/hypixelapi
    src/main/kotlin/com/odtheking/odin/utils/ui/widget
    vendor/floydaddons-fabric/build-release
)
for stale_dir in "${stale_dirs[@]}"; do
    if [[ -d "$stale_dir" ]]; then
        echo "stale directory still exists: $stale_dir" >&2
        exit 1
    fi
done

if ! dry_run_add_output="$(git add -n . 2>&1)"; then
    echo "$dry_run_add_output"
    echo "git add dry-run failed" >&2
    exit 1
fi
dry_run_add_matches="$(printf '%s\n' "$dry_run_add_output" | rg 'build-release|__pycache__|\.pyc|logs/|\.kotlin/|CLAUDE\.md|deploy\.sh|\.flt' || true)"
if [[ -n "$dry_run_add_matches" ]]; then
    echo "$dry_run_add_matches"
    echo "git add dry-run would include generated or local-only files" >&2
    exit 1
fi

jar_regex='(^floydaddons\.mixins\.json$|^odin\.mixins\.json$|odinclient|OdinClient|1\.8\.9|assets/odin|com/odtheking/odin/OdinMod|com/odtheking/odin/features/impl/(boss|dungeon|nether|skyblock|qol)|com/odtheking/odin/features/impl/player/FloydSkin|com/odtheking/odin/features/impl/render/(Camera|Etherwarp|GyroWand|HidePlayers|PerformanceHUD|PlayerSize|RenderOptimizer|Shenanigans|Waypoints)|com/odtheking/odin/commands/(Cata|Dev|DungeonWaypoint|Pet|PosMsg|Soopy|Termsim|Waypoint)Command|com/odtheking/odin/utils/(AutoSessionID|PersonalBest|ServerUtils|JsonResourceLoader|PlayerUtils|skyblock|network/hypixelapi|ItemUtils|ui/widget/CustomGUIImpl|render/DrawContextRenderer)|com/odtheking/odin/clickgui/settings/DevModule|com/odtheking/odin/utils/network/WebSocketConnection|com/odtheking/odin/events/GuiEvent)'

runtime_jars=(build/libs/FloydAddons-*.jar)
source_jars=(build/libs/FloydAddons-*-sources.jar)

if [[ ${#source_jars[@]} -ne 1 || ! -f "${source_jars[0]}" ]]; then
    echo "expected exactly one FloydAddons sources jar, found: ${source_jars[*]}" >&2
    exit 1
fi

runtime_only_jars=()
for candidate in "${runtime_jars[@]}"; do
    [[ "$candidate" == *-sources.jar ]] && continue
    [[ -f "$candidate" ]] && runtime_only_jars+=("$candidate")
done

if [[ ${#runtime_only_jars[@]} -ne 1 ]]; then
    echo "expected exactly one FloydAddons runtime jar, found: ${runtime_only_jars[*]}" >&2
    exit 1
fi

python3 - "${runtime_only_jars[0]}" <<'PY'
import json
import sys
import zipfile

jar_path = sys.argv[1]
with zipfile.ZipFile(jar_path) as jar:
    fabric = json.loads(jar.read("fabric.mod.json"))

expected = {
    "id": "floydaddons",
    "version": "0.1.0",
    "name": "Floyd Addons",
    "license": "BSD-3-Clause AND MIT",
    "icon": "assets/floydaddons/icons/taskbar_icon_128x128.png",
    "environment": "client",
}
failures = []
for key, value in expected.items():
    if fabric.get(key) != value:
        failures.append(f"{key}: expected {value!r}, got {fabric.get(key)!r}")
if "contact" in fabric:
    failures.append("unexpected contact block in packaged fabric.mod.json")
if fabric.get("mixins") != ["floydaddons.mixins.json"]:
    failures.append(f"mixins: expected ['floydaddons.mixins.json'], got {fabric.get('mixins')!r}")
client_entrypoints = fabric.get("entrypoints", {}).get("client", [])
if client_entrypoints != [{"adapter": "kotlin", "value": "com.odtheking.odin.FloydAddonsMod"}]:
    failures.append(f"client entrypoints: unexpected {client_entrypoints!r}")
raw = json.dumps(fabric)
for forbidden in ("${", "SkyblockQOLmod", "odinclient", "OdinClient", "update_checker", "modmenu.discord"):
    if forbidden in raw:
        failures.append(f"forbidden packaged metadata token: {forbidden}")

if failures:
    print("\n".join(failures))
    raise SystemExit(1)

print("packaged Fabric metadata matches Floyd-in-Odin scaffold")
PY

python3 - "${source_jars[0]}" <<'PY'
import json
import sys
import zipfile

jar_path = sys.argv[1]
with zipfile.ZipFile(jar_path) as jar:
    fabric = json.loads(jar.read("fabric.mod.json"))

expected = {
    "id": "${mod_id}",
    "version": "${mod_version}",
    "name": "${mod_name}",
    "license": "BSD-3-Clause AND MIT",
    "icon": "assets/floydaddons/icons/taskbar_icon_128x128.png",
    "environment": "client",
}
failures = []
for key, value in expected.items():
    if fabric.get(key) != value:
        failures.append(f"{key}: expected {value!r}, got {fabric.get(key)!r}")
if "contact" in fabric:
    failures.append("unexpected contact block in sources fabric.mod.json")
if fabric.get("mixins") != ["floydaddons.mixins.json"]:
    failures.append(f"mixins: expected ['floydaddons.mixins.json'], got {fabric.get('mixins')!r}")
client_entrypoints = fabric.get("entrypoints", {}).get("client", [])
if client_entrypoints != [{"adapter": "kotlin", "value": "com.odtheking.odin.FloydAddonsMod"}]:
    failures.append(f"client entrypoints: unexpected {client_entrypoints!r}")
raw = json.dumps(fabric)
for forbidden in ("SkyblockQOLmod", "odinclient", "OdinClient", "update_checker", "modmenu.discord"):
    if forbidden in raw:
        failures.append(f"forbidden sources metadata token: {forbidden}")

if failures:
    print("\n".join(failures))
    raise SystemExit(1)

print("sources Fabric metadata matches Floyd-in-Odin scaffold")
PY

python3 - "${runtime_only_jars[0]}" "${source_jars[0]}" <<'PY'
import pathlib
import sys
import zipfile

runtime_jar, source_jar = sys.argv[1:3]
documents = ("LICENSE", "THIRD_PARTY_NOTICES.md", "PROVENANCE.md")
failures = []
for jar_path in (runtime_jar, source_jar):
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        for document in documents:
            packaged_name = f"META-INF/{document}"
            if packaged_name not in names:
                failures.append(f"{jar_path}: missing {packaged_name}")
                continue
            if jar.read(packaged_name) != pathlib.Path(document).read_bytes():
                failures.append(f"{jar_path}: {packaged_name} does not match repository {document}")

if failures:
    print("\n".join(failures))
    raise SystemExit(1)

print("packaged license and provenance documents match repository files")
PY

python3 - "${runtime_only_jars[0]}" <<'PY'
import pathlib
import sys
import zipfile

jar_path = sys.argv[1]
resource_root = pathlib.Path("src/main/resources")
expected = sorted(
    str(path.relative_to(resource_root)).replace("\\", "/")
    for path in resource_root.rglob("*")
    if path.is_file()
)
with zipfile.ZipFile(jar_path) as jar:
    actual = sorted(
        name
        for name in jar.namelist()
        if not name.endswith("/")
        if name == "fabric.mod.json"
        or name == "floydaddons.mixins.json"
        or name.startswith("assets/")
    )

missing = sorted(set(expected) - set(actual))
extra = sorted(set(actual) - set(expected))
if missing or extra:
    if missing:
        print("missing packaged resource files:")
        print("\n".join(missing))
    if extra:
        print("unexpected packaged resource files:")
        print("\n".join(extra))
    raise SystemExit(1)

print("packaged resources exactly match active resource tree")
PY

python3 - "${source_jars[0]}" <<'PY'
import pathlib
import sys
import zipfile

jar_path = sys.argv[1]
resource_root = pathlib.Path("src/main/resources")
source_roots = (pathlib.Path("src/main/kotlin"), pathlib.Path("src/main/java"))
expected_resources = sorted(
    str(path.relative_to(resource_root)).replace("\\", "/")
    for path in resource_root.rglob("*")
    if path.is_file()
)
expected_sources = sorted(
    str(path.relative_to(root)).replace("\\", "/")
    for root in source_roots
    for path in root.rglob("*")
    if path.is_file()
)
expected = sorted(expected_resources + expected_sources)

with zipfile.ZipFile(jar_path) as jar:
    actual = sorted(name for name in jar.namelist() if not name.endswith("/") and not name.startswith("META-INF/"))

missing = sorted(set(expected) - set(actual))
extra = sorted(set(actual) - set(expected))
if missing or extra:
    if missing:
        print("missing sources jar files:")
        print("\n".join(missing))
    if extra:
        print("unexpected sources jar files:")
        print("\n".join(extra))
    raise SystemExit(1)

print("sources jar exactly matches active source and resource trees")
PY

jar_matches="$(jar tf "${runtime_only_jars[0]}" | sort | rg "$jar_regex" || true)"
if [[ "$jar_matches" != "floydaddons.mixins.json" ]]; then
    echo "$jar_matches"
    echo "runtime jar audit failed" >&2
    exit 1
fi

sources_matches="$(jar tf "${source_jars[0]}" | sort | rg "$jar_regex" || true)"
if [[ "$sources_matches" != "floydaddons.mixins.json" ]]; then
    echo "$sources_matches"
    echo "sources jar audit failed" >&2
    exit 1
fi

if [[ "${FLOYDADDONS_RUN_RUNTIME_SMOKE:-false}" == "true" ]]; then
    scripts/run-runtime-scaffold-smoke.sh >/dev/null
fi

if [[ "${FLOYDADDONS_RUN_LIVE_DIAGNOSE:-false}" == "true" ]]; then
    diagnose_file="${FLOYDADDONS_LIVE_DIAGNOSE_PROOF:-logs/live-hypixel-diagnose.json}"
    write_diagnose_artifact "$diagnose_file" python3 scripts/verify-live-hypixel-acquisition.py --diagnose --json ${live_wait_args[@]+"${live_wait_args[@]}"}
fi

if [[ "${FLOYDADDONS_RUN_LIVE_PREFLIGHT:-false}" == "true" ]]; then
    preflight_proof_file="${FLOYDADDONS_LIVE_PREFLIGHT_PROOF:-logs/live-hypixel-preflight.json}"
    write_success_proof "$preflight_proof_file" python3 scripts/verify-live-hypixel-acquisition.py --preflight --json ${live_wait_args[@]+"${live_wait_args[@]}"}
fi

if [[ "${FLOYDADDONS_RUN_LIVE_HYPIXEL:-false}" == "true" ]]; then
    live_proof_file="${FLOYDADDONS_LIVE_HYPIXEL_PROOF:-logs/live-hypixel-proof.json}"
    write_success_proof "$live_proof_file" python3 scripts/verify-live-hypixel-acquisition.py --json ${live_wait_args[@]+"${live_wait_args[@]}"}
fi

if [[ "${FLOYDADDONS_REQUIRE_COMPLETE:-false}" == "true" ]]; then
    python3 scripts/completion-status.py --require-complete --json
fi

echo "floyd-in-odin verification passed"
