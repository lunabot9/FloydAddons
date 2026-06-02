#!/usr/bin/env bash
# encode_title_bg.sh — encode rendered PNG frames into an MP4 preview and/or
# copy a thinned-out set of frames to the Minecraft config directory.
#
# Usage:
#   ./scripts/encode_title_bg.sh [frames_dir] [mc_config_dir]
#
# Defaults:
#   frames_dir    ~/floydaddons_title_frames
#   mc_config_dir ~/.minecraft/config/floydaddons/mainmenu_frames
#
# Env vars:
#   SKIP_MP4=1          skip MP4 preview encode
#   THIN_FACTOR=2       keep every Nth frame when copying to config (saves memory)
#   FPS=24              frame rate for MP4

set -euo pipefail

FRAMES_DIR="${1:-$HOME/floydaddons_title_frames}"
MC_FRAMES_DIR="${2:-$HOME/.minecraft/config/floydaddons/mainmenu_frames}"
FPS="${FPS:-24}"
SKIP_MP4="${SKIP_MP4:-0}"
THIN_FACTOR="${THIN_FACTOR:-1}"

if [[ ! -d "$FRAMES_DIR" ]]; then
    echo "ERROR: frames directory not found: $FRAMES_DIR"
    echo "Run scripts/render_title_bg.py first."
    exit 1
fi

FRAME_COUNT=$(ls "$FRAMES_DIR"/frame_*.png 2>/dev/null | wc -l | tr -d ' ')
if [[ "$FRAME_COUNT" -eq 0 ]]; then
    echo "ERROR: no frame_*.png files found in $FRAMES_DIR"
    exit 1
fi
echo "Found $FRAME_COUNT frames in $FRAMES_DIR"

# ── MP4 preview ───────────────────────────────────────────────────────────────

if [[ "$SKIP_MP4" != "1" ]]; then
    PREVIEW_MP4="$FRAMES_DIR/preview.mp4"
    echo "Encoding preview → $PREVIEW_MP4"
    ffmpeg -y \
        -framerate "$FPS" \
        -pattern_type glob -i "$FRAMES_DIR/frame_*.png" \
        -c:v libx264 -pix_fmt yuv420p \
        -crf 18 -preset slow \
        "$PREVIEW_MP4"
    echo "Preview: $PREVIEW_MP4"
fi

# ── Copy frames to Minecraft config ──────────────────────────────────────────

mkdir -p "$MC_FRAMES_DIR"

if [[ "$THIN_FACTOR" -le 1 ]]; then
    echo "Copying all $FRAME_COUNT frames → $MC_FRAMES_DIR"
    cp "$FRAMES_DIR"/frame_*.png "$MC_FRAMES_DIR/"
else
    echo "Copying every ${THIN_FACTOR}th frame → $MC_FRAMES_DIR  (reduces GPU memory ~${THIN_FACTOR}x)"
    idx=0
    for src in $(ls "$FRAMES_DIR"/frame_*.png | sort); do
        if (( idx % THIN_FACTOR == 0 )); then
            cp "$src" "$MC_FRAMES_DIR/$(basename "$src")"
        fi
        (( idx++ )) || true
    done
    installed=$(ls "$MC_FRAMES_DIR"/frame_*.png 2>/dev/null | wc -l | tr -d ' ')
    echo "Installed $installed frames"
fi

echo ""
echo "Done. Launch Minecraft — Floyd Compatibility → Custom Main Menu must be enabled."
echo "Frames location: $MC_FRAMES_DIR"
