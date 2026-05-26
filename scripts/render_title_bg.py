#!/usr/bin/env python3
"""
render_title_bg.py — Ken Burns zoom + lightning pulse via Blender 5.x compositor.

Usage (Blender headless):
    blender --background --python scripts/render_title_bg.py -- <image_path> [output_dir] [frames] [fps] [width] [height]

Defaults:
    image_path  ~/Downloads/mcbginspo.png
    output_dir  ~/floydaddons_title_frames
    frames      72  (3 s at 24 fps)
    fps         24
    width       1920
    height      1080

Requires Blender 4.0+. Tested on Blender 5.1.2.

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
OUT_W        = int(argv[4])                if len(argv) > 4 else 1920
OUT_H        = int(argv[5])                if len(argv) > 5 else 1080

print(f"[floyd-bg] image={IMAGE_PATH}")
print(f"[floyd-bg] out={OUTPUT_DIR}  {TOTAL_FRAMES}f@{FPS}fps  {OUT_W}×{OUT_H}")

if not os.path.exists(IMAGE_PATH):
    sys.exit(f"[floyd-bg] ERROR: image not found: {IMAGE_PATH}")

os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── Scene ─────────────────────────────────────────────────────────────────────

scene = bpy.context.scene
scene.render.resolution_x = OUT_W
scene.render.resolution_y = OUT_H
scene.render.fps = FPS
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode  = 'RGB'
scene.render.use_compositing = True

# ── Compositor node tree (Blender 5.x: node group attached to scene) ─────────

tree = bpy.data.node_groups.new("FloydBG", 'CompositorNodeTree')
tree.interface.new_socket("Image", in_out='OUTPUT', socket_type='NodeSocketColor')
scene.compositing_node_group = tree

nodes = tree.nodes
links = tree.links

img = bpy.data.images.load(os.path.abspath(IMAGE_PATH))

n_img = nodes.new('CompositorNodeImage')
n_img.image = img

# Scale to render size, cropping to fill frame
n_sc = nodes.new('CompositorNodeScale')
n_sc.inputs['Type'].default_value = 'Render Size'

# Ken Burns transform: X/Y offset (pixels) + uniform scale
n_tr = nodes.new('CompositorNodeTransform')

# Glare/bloom on lightning — Strength animated per frame
n_gl = nodes.new('CompositorNodeGlare')
n_gl.inputs['Type'].default_value      = 'Bloom'
n_gl.inputs['Threshold'].default_value = 0.82
n_gl.inputs['Size'].default_value      = 7

# Color balance: push toward electric purple
# Blender 5.x: index 4=Lift color, 6=Gamma color, 8=Gain color  (LGG mode)
n_cb = nodes.new('CompositorNodeColorBalance')
n_cb.inputs['Type'].default_value  = 'Lift/Gamma/Gain'
n_cb.inputs[4].default_value       = (0.96, 0.94, 1.04, 1.0)   # Lift  → blue shadows
n_cb.inputs[6].default_value       = (1.00, 0.96, 1.04, 1.0)   # Gamma → cool mids
n_cb.inputs[8].default_value       = (0.94, 0.90, 1.12, 1.0)   # Gain  → electric highlights

n_out = nodes.new('NodeGroupOutput')

links.new(n_img.outputs['Image'],   n_sc.inputs['Image'])
links.new(n_sc.outputs['Image'],    n_tr.inputs['Image'])
links.new(n_tr.outputs['Image'],    n_gl.inputs['Image'])
links.new(n_gl.outputs['Image'],    n_cb.inputs['Image'])
links.new(n_cb.outputs['Image'],    n_out.inputs['Image'])

# ── Per-frame render loop ─────────────────────────────────────────────────────
# (Updating node inputs per frame is more reliable than keyframing in a
#  node group — keyframe evaluation in Blender compositor groups can be
#  context-dependent when headless.)

def ease_in_out(t):
    return t * t * (3.0 - 2.0 * t)

print(f"[floyd-bg] rendering {TOTAL_FRAMES} frames...")

for f in range(1, TOTAL_FRAMES + 1):
    t  = (f - 1) / max(TOTAL_FRAMES - 1, 1)
    te = ease_in_out(t)

    # Ken Burns: zoom from 1.0 to 1.12, drift right→center, slight rise
    n_tr.inputs['Scale'].default_value = 1.0 + 0.12 * te
    n_tr.inputs['X'].default_value     = 35.0 * (1.0 - te)
    n_tr.inputs['Y'].default_value     = 20.0 * te
    n_tr.inputs['Angle'].default_value = 0.0

    # Lightning pulse: 3 sharp bursts per loop (sin³ so they're narrow)
    tc = (f - 1) / TOTAL_FRAMES
    pulse = max(0.0, math.sin(tc * math.pi * 6)) ** 2
    n_gl.inputs['Strength'].default_value = 0.06 + 0.38 * pulse

    # Render this frame to disk
    scene.frame_set(f)
    scene.render.filepath = os.path.join(OUTPUT_DIR, f"frame_{f:04d}")
    bpy.ops.render.render(write_still=True)

    if f % 12 == 0 or f == TOTAL_FRAMES:
        print(f"  {f}/{TOTAL_FRAMES}")

with open(os.path.join(OUTPUT_DIR, "fps.txt"), "w") as fh:
    fh.write(str(FPS) + "\n")

print(f"[floyd-bg] done — {TOTAL_FRAMES} frames in {OUTPUT_DIR}")
print(f"[floyd-bg] next:  ./scripts/encode_title_bg.sh {OUTPUT_DIR}")
