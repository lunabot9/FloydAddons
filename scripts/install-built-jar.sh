#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

cd "$(dirname "$0")/.."

mods_dir="${1:-${FLOYDADDONS_MODS_DIR:-}}"
if [[ -z "$mods_dir" ]]; then
    echo "usage: $0 <mods-dir>" >&2
    echo "or set FLOYDADDONS_MODS_DIR=<mods-dir>" >&2
    exit 2
fi

if [[ "$(basename "$mods_dir")" != "mods" ]]; then
    echo "target must be a Minecraft mods directory ending in /mods: $mods_dir" >&2
    exit 2
fi

if [[ "${FLOYDADDONS_SKIP_BUILD:-false}" != "true" ]]; then
    ./gradlew build
fi

mkdir -p "$mods_dir"

property() {
    local key="$1"
    local line
    line="$(rg -n "^${key}=" gradle.properties | head -n 1 || true)"
    if [[ -z "$line" ]]; then
        echo "missing gradle property: $key" >&2
        exit 1
    fi
    printf '%s\n' "${line#*=}"
}

minecraft_root="$(cd "$(dirname "$mods_dir")" && pwd)"
minecraft_version="$(property minecraft_version)"
loader_version="$(property loader_version)"

if [[ "${FLOYDADDONS_SKIP_FABRIC_PROFILE:-false}" != "true" ]]; then
    fabric_installer_version="${FLOYDADDONS_FABRIC_INSTALLER_VERSION:-1.1.1}"
    fabric_installer_jar="${FLOYDADDONS_FABRIC_INSTALLER_JAR:-}"
    if [[ -z "$fabric_installer_jar" ]]; then
        fabric_installer_jar="$(mktemp "${TMPDIR:-/tmp}/fabric-installer.XXXXXX")"
        curl -fL -o "$fabric_installer_jar" "https://maven.fabricmc.net/net/fabricmc/fabric-installer/${fabric_installer_version}/fabric-installer-${fabric_installer_version}.jar"
    elif [[ ! -f "$fabric_installer_jar" ]]; then
        echo "missing Fabric installer jar: $fabric_installer_jar" >&2
        exit 1
    fi
    java -jar "$fabric_installer_jar" client -dir "$minecraft_root" -mcversion "$minecraft_version" -loader "$loader_version"
    echo "$minecraft_root/versions/fabric-loader-${loader_version}-${minecraft_version}/fabric-loader-${loader_version}-${minecraft_version}.json"
fi

runtime_jars=(build/libs/FloydAddons-*.jar)
selected_jars=()
for jar in "${runtime_jars[@]}"; do
    if [[ "$jar" != *-sources.jar ]]; then
        selected_jars+=("$jar")
    fi
done

if [[ "${#selected_jars[@]}" -ne 1 ]]; then
    printf '%s\n' "${selected_jars[@]}" >&2
    echo "expected exactly one FloydAddons runtime jar" >&2
    exit 1
fi

if [[ "${FLOYDADDONS_KEEP_OLD_JARS:-false}" != "true" ]]; then
    find "$mods_dir" -maxdepth 1 -type f -name 'FloydAddons-*.jar' ! -name '*-sources.jar' -delete
fi

install -m 0644 "${selected_jars[0]}" "$mods_dir/"
echo "$mods_dir/$(basename "${selected_jars[0]}")"

if [[ "${FLOYDADDONS_SKIP_RUNTIME_DEPS:-false}" != "true" ]]; then
    fabric_api_version="$(property fabric_api_version)"
    fabric_kotlin_version="$(property fabric_kotlin_version)"
    runtime_deps=(
        "fabric-api-${fabric_api_version}.jar|https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${fabric_api_version}/fabric-api-${fabric_api_version}.jar"
        "fabric-language-kotlin-${fabric_kotlin_version}.jar|https://maven.fabricmc.net/net/fabricmc/fabric-language-kotlin/${fabric_kotlin_version}/fabric-language-kotlin-${fabric_kotlin_version}.jar"
    )

    if [[ "${FLOYDADDONS_KEEP_OLD_RUNTIME_DEPS:-false}" != "true" ]]; then
        find "$mods_dir" -maxdepth 1 -type f \( -name 'fabric-api-*.jar' -o -name 'fabric-language-kotlin-*.jar' \) -delete
    fi

    for dep in "${runtime_deps[@]}"; do
        dep_name="${dep%%|*}"
        dep_url="${dep#*|}"
        dep_source=""
        if [[ -n "${FLOYDADDONS_RUNTIME_DEPS_DIR:-}" ]]; then
            dep_source="${FLOYDADDONS_RUNTIME_DEPS_DIR%/}/$dep_name"
            if [[ ! -f "$dep_source" ]]; then
                echo "missing runtime dependency in FLOYDADDONS_RUNTIME_DEPS_DIR: $dep_source" >&2
                exit 1
            fi
            install -m 0644 "$dep_source" "$mods_dir/$dep_name"
        else
            curl -fL -o "$mods_dir/$dep_name" "$dep_url"
        fi
        echo "$mods_dir/$dep_name"
    done
fi
