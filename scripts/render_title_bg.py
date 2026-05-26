#!/usr/bin/env python3
"""
render_title_bg.py — Blender compositor script for FloydAddons animated title background.

Applies a Ken Burns zoom + lightning pulse effect to a source image, rendering
PNG frames for use with FloydTitleScreenBackgroundMixin.

Usage:
    blender --background --python scripts/render_title_bg.py -- <image_path> [output_dir] [frames] [fps]

Defaults:
    image_path  ~/Downloads/mcbginspo.png
    output_dir  ~/floydaddons_title_frames
    frames      72  (3 s at 24 fps)
    fps         24

Install rendered frames:
    cp -r ~/floydaddons_title_frames ~/.minecraft/config/floydaddons/mainmenu_frames
"""

import bpy
import math
import os
import sys

# ── Args ──────────────────────────────────────────────────────────────────────

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
IMAGE_PATH   = os.path.expanduser(argv[0]) if len(argv) > 0 else os.path.expanduser("~/Downloads/mcbginspo.png")
OUTPUT_DIR   = os.path.expanduser(argv[1]) if len(argv) > 1 else os.path.expanduser("~/floydaddons_title_frames")
TOTAL_FRAMES = int(argv[2])                if len(argv) > 2 else 72
FPS          = int(argv[3])                if len(argv) > 3 else 24

# Resolution: 1920×1080 fills any screen; render smaller for lighter install.
# Default is 1920×1080. Override with --  <img> <outdir> <frames> <fps> <w> <h>
OUT_W = int(argv[4]) if len(argv) > 4 else 1920
OUT_H = int(argv[5]) if len(argv) > 5 else 1080

print(f"[floyd-bg] image={IMAGE_PATH}  out={OUTPUT_DIR}  {TOTAL_FRAMES}f@{FPS}fps  {OUT_W}x{OUT_H}")

if not os.path.exists(IMAGE_PATH):
    sys.exit(f"[floyd-bg] ERROR: image not found: {IMAGE_PATH}")

os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── Scene setup ───────────────────────────────────────────────────────────────

scene = bpy.context.scene
render = scene.render
render.resolution_x    = OUT_W
render.resolution_y    = OUT_H
render.fps             = FPS
render.use_compositing = True
render.use_sequencer   = False
scene.frame_start      = 1
scene.frame_end        = TOTAL_FRAMES
render.image_settings.file_format  = 'PNG'
render.image_settings.color_mode   = 'RGB'
render.image_settings.compression  = 15      # light PNG compression for speed
render.filepath = os.path.join(OUTPUT_DIR, "frame_####")

# ── Load source image ─────────────────────────────────────────────────────────

img = bpy.data.images.load(IMAGE_PATH)
img.colorspace_settings.name = 'sRGB'

# ── Compositor node tree ──────────────────────────────────────────────────────

scene.use_nodes = True
tree   = scene.node_tree
nodes  = tree.nodes
links  = tree.links
nodes.clear()

# Image source
n_img = nodes.new('CompositorNodeImage')
n_img.image    = img
n_img.location = (-900, 0)

# Scale to render size (fills frame, crops to aspect ratio if needed)
n_scale = nodes.new('CompositorNodeScale')
n_scale.space        = 'RENDER_SIZE'
n_scale.frame_method = 'CROP'
n_scale.location     = (-700, 0)

# Ken Burns: slow zoom-in + slight lateral drift
# Zoom from 1.0→1.14 over the clip; drift 30px right→center, 15px up
n_transform = nodes.new('CompositorNodeTransform')
n_transform.filter_type = 'BICUBIC'
n_transform.location    = (-500, 0)

def _set_key(node_input, value, frame):
    node_input.default_value = value
    node_input.keyframe_insert(data_path='default_value', frame=frame)

for f in range(1, TOTAL_FRAMES + 1):
    t = (f - 1) / max(TOTAL_FRAMES - 1, 1)
    _set_key(n_transform.inputs['Scale'], 1.0 + 0.14 * t, f)
    _set_key(n_transform.inputs['X'],    30.0 * (1.0 - t), f)
    _set_key(n_transform.inputs['Y'],    15.0 * t,          f)
    _set_key(n_transform.inputs['Angle'], 0.0,              f)

# Ease the keyframe curves so the motion feels cinematic (ease-in-out)
for fcurve in scene.animation_data.action.fcurves if scene.animation_data and scene.animation_data.action else []:
    for kp in fcurve.keyframe_points:
        kp.interpolation = 'BEZIER'
        kp.easing = 'EASE_IN_OUT'

# Glare / bloom — pulses 3× per loop to sell the lightning effect
n_glare = nodes.new('CompositorNodeGlare')
n_glare.glare_type = 'BLOOM'
n_glare.threshold  = 0.82
n_glare.size       = 7
n_glare.quality    = 'MEDIUM'
n_glare.location   = (-300, 0)

for f in range(1, TOTAL_FRAMES + 1):
    t = (f - 1) / TOTAL_FRAMES
    # 3 asymmetric pulses: sharp rise, slower decay
    pulse = 0.08 + 0.18 * max(0.0, math.sin(t * math.pi * 6)) ** 2
    _set_key(n_glare.inputs['Mix'] if 'Mix' in n_glare.inputs else n_glare.inputs[0], pulse, f)

# Color balance: push the purple/electric palette
n_color = nodes.new('CompositorNodeColorBalance')
n_color.correction_method = 'LIFT_GAMMA_GAIN'
n_color.lift  = (0.96, 0.94, 1.04, 1.0)   # blue shadows
n_color.gamma = (1.00, 0.96, 1.04, 1.0)   # cool mids
n_color.gain  = (0.94, 0.90, 1.12, 1.0)   # push electric highlights
n_color.location = (-100, 0)

# Output
n_comp = nodes.new('CompositorNodeComposite')
n_comp.location = (150, 0)

# Wire
links.new(n_img.outputs['Image'],        n_scale.inputs['Image'])
links.new(n_scale.outputs['Image'],      n_transform.inputs['Image'])
links.new(n_transform.outputs['Image'],  n_glare.inputs['Image'])
links.new(n_glare.outputs['Image'],      n_color.inputs['Image'])
links.new(n_color.outputs['Image'],      n_comp.inputs['Image'])

# ── Render ────────────────────────────────────────────────────────────────────

print(f"[floyd-bg] rendering {TOTAL_FRAMES} frames → {OUTPUT_DIR}")
bpy.ops.render.render(animation=True)
print(f"[floyd-bg] done. copy output to: ~/.minecraft/config/floydaddons/mainmenu_frames/")
