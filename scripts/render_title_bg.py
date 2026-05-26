#!/usr/bin/env python3
"""
render_title_bg.py — Ken Burns zoom + lightning pulse for FloydAddons title background.

Produces PNG frames for use with FloydTitleScreenBackgroundMixin.
Requires: Pillow, numpy  (pip install Pillow numpy)

Usage:
    python3 scripts/render_title_bg.py [image_path] [output_dir] [frames] [fps] [width] [height]

Defaults:
    image_path  ~/Downloads/mcbginspo.png
    output_dir  ~/floydaddons_title_frames
    frames      72  (3 s at 24 fps)
    fps         24  (stored in metadata only — used by encode_title_bg.sh)
    width       1920
    height      1080

Install rendered frames:
    cp -r ~/floydaddons_title_frames ~/.minecraft/config/floydaddons/mainmenu_frames
"""

import sys
import os
import math
import numpy as np
from PIL import Image, ImageFilter

# ── Args ──────────────────────────────────────────────────────────────────────

argv = sys.argv[1:]
IMAGE_PATH   = os.path.expanduser(argv[0]) if len(argv) > 0 else os.path.expanduser("~/Downloads/mcbginspo.png")
OUTPUT_DIR   = os.path.expanduser(argv[1]) if len(argv) > 1 else os.path.expanduser("~/floydaddons_title_frames")
TOTAL_FRAMES = int(argv[2])                if len(argv) > 2 else 72
FPS          = int(argv[3])                if len(argv) > 3 else 24
OUT_W        = int(argv[4])                if len(argv) > 4 else 1920
OUT_H        = int(argv[5])                if len(argv) > 5 else 1080

print(f"[floyd-bg] {IMAGE_PATH}  →  {OUTPUT_DIR}")
print(f"[floyd-bg] {TOTAL_FRAMES} frames @ {FPS} fps  |  {OUT_W}×{OUT_H}")

if not os.path.exists(IMAGE_PATH):
    sys.exit(f"[floyd-bg] ERROR: image not found: {IMAGE_PATH}")

os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── Load and prep source ──────────────────────────────────────────────────────

src = Image.open(IMAGE_PATH).convert("RGB")
# Upscale source large enough that Ken Burns zoom never hits the edge
# We zoom to 1.14× so we need source at least 1.14 × output size
needed = (int(OUT_W * 1.20), int(OUT_H * 1.20))
src_big = src.resize(needed, Image.LANCZOS)
src_arr = np.array(src_big, dtype=np.float32) / 255.0

# Pre-compute a "lightning glow" layer: extract bright pixels, blur heavily
def make_glow(arr_f32, blur_radius=40, threshold=0.75):
    bright = np.clip(arr_f32 - threshold, 0, None) / (1.0 - threshold)
    bright_img = Image.fromarray((bright * 255).astype(np.uint8))
    blurred = bright_img.filter(ImageFilter.GaussianBlur(radius=blur_radius))
    return np.array(blurred, dtype=np.float32) / 255.0

glow_layer = make_glow(src_arr)

def easeInOut(t):
    return t * t * (3 - 2 * t)

print(f"[floyd-bg] rendering {TOTAL_FRAMES} frames...")

for f in range(TOTAL_FRAMES):
    t = f / max(TOTAL_FRAMES - 1, 1)
    te = easeInOut(t)

    # ── Ken Burns: zoom 1.0 → 1.12, drift toward face (center-right) ─────────
    zoom   = 1.0 + 0.12 * te
    # crop a region of size (OUT_W/zoom × OUT_H/zoom) from src_big
    crop_w = int(OUT_W / zoom)
    crop_h = int(OUT_H / zoom)

    # Drift: start top-left offset, drift toward center of src_big
    max_x = needed[0] - crop_w
    max_y = needed[1] - crop_h
    x0 = int(max_x * (0.3 * (1 - te)))          # drift from 30% offset toward 0
    y0 = int(max_y * (0.15 * (1 - te) + 0.05))  # slight upward drift

    crop = src_arr[y0:y0 + crop_h, x0:x0 + crop_w]
    # Resize crop back to output size (this IS the zoom)
    frame_img = Image.fromarray((crop * 255).astype(np.uint8))
    frame_img = frame_img.resize((OUT_W, OUT_H), Image.LANCZOS)
    frame = np.array(frame_img, dtype=np.float32) / 255.0

    # Crop the glow layer the same way
    glow_crop = glow_layer[y0:y0 + crop_h, x0:x0 + crop_w]
    glow_img  = Image.fromarray((glow_crop * 255).astype(np.uint8))
    glow_img  = glow_img.resize((OUT_W, OUT_H), Image.LANCZOS)
    glow      = np.array(glow_img, dtype=np.float32) / 255.0

    # ── Lightning pulse (3 asymmetric bursts per loop) ────────────────────────
    tc = f / TOTAL_FRAMES
    pulse = max(0.0, math.sin(tc * math.pi * 6)) ** 1.5   # sharp rise, fast decay
    pulse_strength = 0.10 + 0.35 * pulse

    # Add glow (screen blend: 1 - (1-a)(1-b))
    frame = 1.0 - (1.0 - frame) * (1.0 - glow * pulse_strength)

    # ── Color grade: push purple/electric ────────────────────────────────────
    r, g, b = frame[:,:,0], frame[:,:,1], frame[:,:,2]
    r = r * 0.92
    g = g * 0.88
    b = np.clip(b * 1.14, 0, 1)
    # Lift shadows toward indigo
    shadow_mask = 1.0 - frame.mean(axis=2, keepdims=True)
    r = r + shadow_mask[:,:,0] * 0.03
    g = g + shadow_mask[:,:,0] * 0.01
    b = b + shadow_mask[:,:,0] * 0.06
    frame = np.stack([r, g, b], axis=2)

    # ── Vignette ─────────────────────────────────────────────────────────────
    ys = np.linspace(-1, 1, OUT_H)[:, None]
    xs = np.linspace(-1, 1, OUT_W)[None, :]
    vignette = 1.0 - 0.45 * np.clip(xs**2 + ys**2, 0, 1)
    frame = frame * vignette[:, :, None]

    frame = np.clip(frame, 0, 1)

    # ── Save ─────────────────────────────────────────────────────────────────
    out_path = os.path.join(OUTPUT_DIR, f"frame_{f + 1:04d}.png")
    Image.fromarray((frame * 255).astype(np.uint8)).save(out_path, optimize=False)

    if f % 12 == 0 or f == TOTAL_FRAMES - 1:
        print(f"  {f + 1}/{TOTAL_FRAMES}")

# Write FPS hint file for encode script
with open(os.path.join(OUTPUT_DIR, "fps.txt"), "w") as fh:
    fh.write(str(FPS) + "\n")

print(f"[floyd-bg] done — {TOTAL_FRAMES} frames in {OUTPUT_DIR}")
print(f"[floyd-bg] next: run scripts/encode_title_bg.sh to encode preview + install")
