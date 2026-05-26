#!/usr/bin/env python3
"""
render_title_bg.py — Real 3D parallax title screen via Blender 5.x.

Three image planes at different Y depths (background / lightning / character)
with a camera that pushes in and drifts sideways, creating true parallax.
Pulsing eye lights, compositor bloom, depth of field on background.

Usage:
    blender --background --python scripts/render_title_bg.py -- <layers_dir> [output_dir] [frames] [fps] [width] [height]

    layers_dir must contain: bg.png  lightning.png  character.png
    (generate with:  python3 scripts/prep_layers.py <image>)

Defaults:
    output_dir  ~/floydaddons_title_frames
    frames      120  (5 s at 24 fps)
    fps         24
    width       1920
    height      1080

Requires Blender 5.0+.
"""

import bpy
import math
import os
import sys

# ── Args ──────────────────────────────────────────────────────────────────────

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
LAYERS_DIR   = os.path.expanduser(argv[0]) if len(argv) > 0 else os.path.expanduser("~/Downloads/layers")
OUTPUT_DIR   = os.path.expanduser(argv[1]) if len(argv) > 1 else os.path.expanduser("~/floydaddons_title_frames")
TOTAL_FRAMES = int(argv[2])                if len(argv) > 2 else 120
FPS          = int(argv[3])                if len(argv) > 3 else 24
OUT_W        = int(argv[4])                if len(argv) > 4 else 1920
OUT_H        = int(argv[5])                if len(argv) > 5 else 1080

os.makedirs(OUTPUT_DIR, exist_ok=True)
print(f"[floyd-3d] layers={LAYERS_DIR}  out={OUTPUT_DIR}  {TOTAL_FRAMES}f@{FPS}fps  {OUT_W}×{OUT_H}")

for name in ("bg.png", "lightning.png", "character.png"):
    p = os.path.join(LAYERS_DIR, name)
    if not os.path.exists(p):
        sys.exit(f"[floyd-3d] ERROR: missing {p} — run prep_layers.py first")

# ── Scene ─────────────────────────────────────────────────────────────────────

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()

scene = bpy.context.scene
scene.render.engine               = 'BLENDER_EEVEE'
scene.render.resolution_x         = OUT_W
scene.render.resolution_y         = OUT_H
scene.render.fps                  = FPS
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode  = 'RGB'
scene.frame_start                 = 1
scene.frame_end                   = TOTAL_FRAMES

# Black world
world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (0, 0, 0, 1)
world.node_tree.nodes["Background"].inputs[1].default_value = 0.0

# ── Camera ────────────────────────────────────────────────────────────────────
# Camera looks in +Y direction (rotation 90° on X).
# Planes are at Y=0 (char), Y=2 (lightning), Y=5 (bg).
# Camera starts at Y=-4, ends at Y=-2.8 (push-in) with lateral drift.

FOCAL    = 40    # mm — slight wide for drama
CAM_START = (-0.35, -6.0,  0.10)
CAM_END   = ( 0.28, -5.1,  0.05)

# Compute plane height needed to fill frame at START distance (farthest = largest view)
SENSOR_H = 24.0          # mm full-frame default
vfov_half = math.atan(SENSOR_H / (2 * FOCAL))
char_dist = abs(CAM_START[1])          # 6.0
bg_dist   = char_dist + 5.0            # 11.0
lightning_dist = char_dist + 2.0       # 8.0

def fill_height(dist, margin=1.18):
    return 2 * dist * math.tan(vfov_half) * margin

def fill_width(h):
    return h * OUT_W / OUT_H

CHAR_H = fill_height(char_dist)
BG_H   = fill_height(bg_dist, margin=1.35)   # extra margin for parallax range

cam_data = bpy.data.cameras.new("Camera")
cam_data.lens              = FOCAL
cam_data.dof.use_dof       = True
cam_data.dof.focus_distance = char_dist - 0.5     # sharp on character
cam_data.dof.aperture_fstop = 2.2                 # background softly blurred

cam_obj = bpy.data.objects.new("Camera", cam_data)
bpy.context.collection.objects.link(cam_obj)
scene.camera = cam_obj
cam_obj.rotation_euler = (math.radians(90), 0, 0)

# ── Emission plane factory ────────────────────────────────────────────────────

def make_plane(name, img_path, y_depth, plane_h, emit_strength=1.6):
    img = bpy.data.images.load(img_path)
    img.colorspace_settings.name = 'sRGB'

    mat = bpy.data.materials.new(name=name + "_mat")
    mat.use_nodes = True
    mat.blend_method = 'BLEND'

    nt = mat.node_tree
    nt.nodes.clear()

    tex  = nt.nodes.new('ShaderNodeTexImage'); tex.image = img; tex.location = (-400, 0)
    emit = nt.nodes.new('ShaderNodeEmission');                   emit.location = (-100, 50)
    trns = nt.nodes.new('ShaderNodeBsdfTransparent');            trns.location = (-100, -100)
    mix  = nt.nodes.new('ShaderNodeMixShader');                  mix.location  = (180, 0)
    outp = nt.nodes.new('ShaderNodeOutputMaterial');             outp.location = (400, 0)

    emit.inputs['Strength'].default_value = emit_strength
    nt.links.new(tex.outputs['Color'],     emit.inputs['Color'])
    nt.links.new(tex.outputs['Alpha'],     mix.inputs['Fac'])
    nt.links.new(trns.outputs['BSDF'],     mix.inputs[1])
    nt.links.new(emit.outputs['Emission'], mix.inputs[2])
    nt.links.new(mix.outputs['Shader'],    outp.inputs['Surface'])

    aspect   = img.size[0] / max(img.size[1], 1)
    plane_w  = fill_width(plane_h) / aspect  # UV-correct: keeps image un-squished
    # Note: plane is created 2×2, then scaled so it covers fill_width × plane_h
    # We want the plane in world space = fill_width(plane_h) × plane_h
    sx = fill_width(plane_h) / 2.0
    sy = plane_h / 2.0

    bpy.ops.mesh.primitive_plane_add(size=2, location=(0, y_depth, 0))
    obj = bpy.context.active_object
    obj.name = name
    obj.scale = (sx, sy, 1)
    obj.rotation_euler = (math.radians(90), 0, 0)
    obj.data.materials.append(mat)
    return obj

make_plane("bg",        os.path.join(LAYERS_DIR, "bg.png"),        y_depth=5.0,  plane_h=BG_H,   emit_strength=1.4)
make_plane("lightning", os.path.join(LAYERS_DIR, "lightning.png"), y_depth=2.0,  plane_h=fill_height(lightning_dist, 1.25), emit_strength=2.2)
make_plane("character", os.path.join(LAYERS_DIR, "character.png"), y_depth=0.0,  plane_h=CHAR_H, emit_strength=1.7)

# ── Lights ────────────────────────────────────────────────────────────────────

def pulse_light(name, loc, color, base_energy, pulses_per_loop):
    ld = bpy.data.lights.new(name=name, type='POINT')
    ld.color = color
    ld.shadow_soft_size = 0.08
    lo = bpy.data.objects.new(name, ld)
    bpy.context.collection.objects.link(lo)
    lo.location = loc
    for f in range(1, TOTAL_FRAMES + 1):
        t = (f - 1) / TOTAL_FRAMES
        p = max(0.0, math.sin(t * math.pi * 2 * pulses_per_loop)) ** 1.5
        ld.energy = base_energy * (0.55 + 1.1 * p)
        ld.keyframe_insert(data_path='energy', frame=f)
    return lo

# Eyes (slightly in front of character plane, approximate face position)
pulse_light("eye_L", (-0.10, -0.3, 0.42), (0.7, 0.85, 1.0), base_energy=5.5, pulses_per_loop=3)
pulse_light("eye_R", ( 0.11, -0.3, 0.42), (0.7, 0.85, 1.0), base_energy=5.5, pulses_per_loop=3)

# Wide purple lightning fill light — 3 bright bursts per loop
fill_ld = bpy.data.lights.new("lightning_fill", type='AREA')
fill_ld.color = (0.5, 0.38, 1.0)
fill_ld.size  = 5.0
fill_lo = bpy.data.objects.new("lightning_fill", fill_ld)
bpy.context.collection.objects.link(fill_lo)
fill_lo.location     = (0, -2.5, 1.2)
fill_lo.rotation_euler = (math.radians(25), 0, 0)
for f in range(1, TOTAL_FRAMES + 1):
    t = (f - 1) / TOTAL_FRAMES
    burst = max(0.0, math.sin(t * math.pi * 6)) ** 1.8
    fill_ld.energy = 1.2 + 9.0 * burst
    fill_ld.keyframe_insert(data_path='energy', frame=f)

# ── Compositor: RLayers → Bloom → output ─────────────────────────────────────

comp = bpy.data.node_groups.new("Composite", 'CompositorNodeTree')
comp.interface.new_socket("Image", in_out='OUTPUT', socket_type='NodeSocketColor')
scene.compositing_node_group = comp

cn = comp.nodes
cl = comp.links

rl   = cn.new('CompositorNodeRLayers')
gl   = cn.new('CompositorNodeGlare')
cout = cn.new('NodeGroupOutput')

gl.inputs['Type'].default_value      = 'Bloom'
gl.inputs['Threshold'].default_value = 0.78
gl.inputs['Size'].default_value      = 7
gl.inputs['Strength'].default_value  = 0.45

cl.new(rl.outputs['Image'], gl.inputs['Image'])
cl.new(gl.outputs['Image'], cout.inputs['Image'])

# ── Camera keyframes ──────────────────────────────────────────────────────────

def ease_in_out(t):
    return t * t * (3.0 - 2.0 * t)

for f in range(1, TOTAL_FRAMES + 1):
    t  = (f - 1) / max(TOTAL_FRAMES - 1, 1)
    te = ease_in_out(t)

    x = CAM_START[0] + (CAM_END[0] - CAM_START[0]) * te
    y = CAM_START[1] + (CAM_END[1] - CAM_START[1]) * te
    z = CAM_START[2] + (CAM_END[2] - CAM_START[2]) * te
    z += 0.018 * math.sin(t * math.pi * 3)   # subtle breathing float

    cam_obj.location = (x, y, z)
    cam_obj.keyframe_insert(data_path='location', frame=f)

if scene.animation_data and scene.animation_data.action:
    for fc in scene.animation_data.action.fcurves:
        for kp in fc.keyframe_points:
            kp.interpolation = 'BEZIER'
            kp.easing = 'EASE_IN_OUT'

# ── Render ────────────────────────────────────────────────────────────────────

print(f"[floyd-3d] rendering {TOTAL_FRAMES} frames...")
for f in range(1, TOTAL_FRAMES + 1):
    scene.frame_set(f)
    scene.render.filepath = os.path.join(OUTPUT_DIR, f"frame_{f:04d}")
    bpy.ops.render.render(write_still=True)
    if f % 12 == 0 or f == TOTAL_FRAMES:
        print(f"  {f}/{TOTAL_FRAMES}")

with open(os.path.join(OUTPUT_DIR, "fps.txt"), "w") as fh:
    fh.write(str(FPS) + "\n")

print(f"[floyd-3d] done — {TOTAL_FRAMES} frames in {OUTPUT_DIR}")
