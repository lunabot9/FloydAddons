#!/usr/bin/env python3
"""
prep_layers.py — Split a single image into 3 compositing layers for the
FloydAddons Blender title-screen pipeline.

Outputs:
    layers/bg.png         dark nebula background (character hole inpainted)
    layers/lightning.png  isolated lightning bolts, RGBA additive overlay
    layers/character.png  foreground person, alpha-composited cutout

Usage:
    python3 scripts/prep_layers.py <image_path> [output_dir]

Requires: Pillow, numpy, rembg[cpu]
"""

import sys
import os
import math
import numpy as np
from PIL import Image, ImageFilter, ImageChops

# ── Args ──────────────────────────────────────────────────────────────────────

IMAGE_PATH = os.path.expanduser(sys.argv[1]) if len(sys.argv) > 1 else os.path.expanduser("~/Downloads/mcbginspo.png")
OUTPUT_DIR = os.path.expanduser(sys.argv[2]) if len(sys.argv) > 2 else os.path.join(os.path.dirname(IMAGE_PATH), "layers")

os.makedirs(OUTPUT_DIR, exist_ok=True)
print(f"[prep] {IMAGE_PATH}  →  {OUTPUT_DIR}")

src = Image.open(IMAGE_PATH).convert("RGBA")
W, H = src.size
src_rgb = src.convert("RGB")

# ── 1. Character cutout via rembg ─────────────────────────────────────────────

print("[prep] running rembg (AI background removal)...")
from rembg import remove
char_rgba = remove(src_rgb)             # RGBA, background = transparent
char_arr  = np.array(char_rgba, dtype=np.float32) / 255.0
alpha_arr = char_arr[:, :, 3]           # [0..1] person mask

# Slightly erode + feather the mask edge so the cutout blends smoothly
mask_img = Image.fromarray((alpha_arr * 255).astype(np.uint8), mode='L')
mask_img = mask_img.filter(ImageFilter.MaxFilter(3))          # fill small holes
mask_img = mask_img.filter(ImageFilter.GaussianBlur(radius=2)) # feather edge
alpha_clean = np.array(mask_img, dtype=np.float32) / 255.0

char_out = np.zeros((H, W, 4), dtype=np.float32)
char_out[:, :, :3] = np.array(src_rgb, dtype=np.float32) / 255.0
char_out[:, :, 3]  = alpha_clean
char_png = Image.fromarray((char_out * 255).astype(np.uint8), mode='RGBA')
char_png.save(os.path.join(OUTPUT_DIR, "character.png"))
print("[prep] character.png saved")

# ── 2. Lightning layer — bright threshold, additive overlay ──────────────────

src_f = np.array(src_rgb, dtype=np.float32) / 255.0

# Lightning = pixels that are much brighter than average local neighbourhood
# Approximate: use luminance threshold + subtract estimated bg colour
lum = 0.299 * src_f[:,:,0] + 0.587 * src_f[:,:,1] + 0.114 * src_f[:,:,2]
# Blur lum to get local average
lum_img    = Image.fromarray((lum * 255).astype(np.uint8))
lum_blurred = np.array(lum_img.filter(ImageFilter.GaussianBlur(radius=25)), dtype=np.float32) / 255.0
# Bright local excess = lightning
excess = np.clip(lum - lum_blurred - 0.05, 0, None)
excess_norm = excess / (excess.max() + 1e-6)

# Lightning mask: smooth threshold at ~30% of max excess
lightning_mask = np.clip(excess_norm / 0.35, 0, 1) ** 0.7

# Exclude the character area so lightning is only behind them
fg_mask = np.clip(alpha_clean * 1.5, 0, 1)
lightning_mask = lightning_mask * (1.0 - fg_mask * 0.85)

# Apply mask to original pixels and push toward blue-white
lightning_rgb = src_f.copy()
# Boost brightness and push to cooler hue
lightning_rgb[:, :, 0] = np.clip(src_f[:,:,0] * 0.85, 0, 1)
lightning_rgb[:, :, 2] = np.clip(src_f[:,:,2] * 1.15 + 0.05, 0, 1)

lightning_out = np.zeros((H, W, 4), dtype=np.float32)
lightning_out[:, :, :3] = lightning_rgb
lightning_out[:, :, 3]  = lightning_mask * 0.92   # additive strength
lightning_png = Image.fromarray((lightning_out * 255).astype(np.uint8), mode='RGBA')
lightning_png.save(os.path.join(OUTPUT_DIR, "lightning.png"))
print("[prep] lightning.png saved")

# ── 3. Background — original with character region softly replaced ────────────

# Fill the character hole by stretching the surrounding bg pixels inward
# Simple inpaint: dilate the bg region progressively to fill
bg_f = src_f.copy()
hole = alpha_clean                          # where character was
hole_img = Image.fromarray((hole * 255).astype(np.uint8), mode='L')

# Multi-pass dilate fill: blur the background into the character hole
bg_fill = bg_f.copy()
for radius in [4, 8, 16, 32, 60]:
    blurred = np.array(
        Image.fromarray((bg_fill * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius)),
        dtype=np.float32
    ) / 255.0
    # Only fill inside the hole
    fill_weight = np.clip(hole * 2.0, 0, 1)[:, :, None]
    bg_fill = bg_fill * (1 - fill_weight) + blurred * fill_weight

# Color grade background slightly darker and more purple for depth
bg_fill[:, :, 0] = np.clip(bg_fill[:,:,0] * 0.80, 0, 1)
bg_fill[:, :, 1] = np.clip(bg_fill[:,:,1] * 0.75, 0, 1)
bg_fill[:, :, 2] = np.clip(bg_fill[:,:,2] * 0.88, 0, 1)

bg_png = Image.fromarray((bg_fill * 255).astype(np.uint8), mode='RGB')
bg_png.save(os.path.join(OUTPUT_DIR, "bg.png"))
print("[prep] bg.png saved")

# ── Preview composite ─────────────────────────────────────────────────────────

preview = Image.fromarray((bg_fill * 255).astype(np.uint8), mode='RGB').convert('RGBA')
# Screen-blend lightning
l_img = Image.fromarray((lightning_out * 255).astype(np.uint8), mode='RGBA')
preview = Image.alpha_composite(preview, l_img)
# Paste character over
preview.paste(char_png, (0, 0), char_png)
preview.convert('RGB').save(os.path.join(OUTPUT_DIR, "preview_composite.jpg"), quality=92)
print(f"[prep] preview_composite.jpg saved → {OUTPUT_DIR}")
print("[prep] done — run render_title_bg.py next")
