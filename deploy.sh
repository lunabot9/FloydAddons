#!/usr/bin/env bash
set -euo pipefail

JAR="app/build/libs/floydaddons-1.0.0.jar"

# Detect branch to pick the right Prism Launcher instance
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main")

case "$BRANCH" in
    1.21.11)
        DEST="/home/twaldin/.local/share/PrismLauncher/instances/taun 1.21.11/minecraft/mods/floydaddons-1.0.0.jar"
        ;;
    *)
        DEST="/home/twaldin/.local/share/PrismLauncher/instances/1.21.10(2)/minecraft/mods/floydaddons-1.0.0.jar"
        ;;
esac

if [ ! -f "$JAR" ]; then
    echo "Build JAR not found. Run ./gradlew build first."
    exit 1
fi

cp "$JAR" "$DEST"
echo "Deployed $(basename "$JAR") -> $DEST"
