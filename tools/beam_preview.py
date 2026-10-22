"""Draws the box Beam (tools/beam_boxes.py) with his texture in Blender, flat lit and sampled nearest, to look at him without
starting the game: the same cameras as the smooth blueprint's renders (tools/beam_model.py's SHOTS), the body registered with
the blueprint's (the extra nose carries on past where the blueprint's snout ended).

    blender -b --factory-startup --python tools/beam_preview.py -- --tex TEXTURE.png --out DIR [--only side,mouth]

Run from the repository root; the texture is written by tools/gen_chainsaw_textures.py (--preview) or beam_texture.py. The
game's own picture (the beamlook test) is the real check; this is for quick looks while shaping him.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.getcwd(), 'tools'))
import bpy  # noqa: E402
import bmesh  # noqa: E402
from mathutils import Vector  # noqa: E402
import numpy as np  # noqa: E402

import beam_boxes as bb  # noqa: E402
import beam_model as blueprint  # noqa: E402

PX = 16.0


def to_build(p):
    """Model space (pixels, y down) to the blueprint's build space (blocks, y back, z up), registered on the body."""
    x, y, z = p
    return Vector((x / PX, (z + bb.Z0 - bb.NOSE) / PX - 2.5, (bb.HEIGHT - y) / PX))


def build_boxes(tex_path, pose=None):
    bb.build()
    height = bb.pack()
    mats = bb.part_matrices(pose or bb.standing())
    img = bpy.data.images.load(tex_path)
    img.colorspace_settings.name = 'sRGB'
    tw, th = img.size
    mat = bpy.data.materials.new('beam')
    mat.use_nodes = True
    node = mat.node_tree.nodes.new('ShaderNodeTexImage')
    node.image = img
    node.interpolation = 'Closest'
    mat.node_tree.links.new(node.outputs['Color'], mat.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
    mat.node_tree.links.new(node.outputs['Alpha'], mat.node_tree.nodes['Principled BSDF'].inputs['Alpha'])
    mat.blend_method = 'CLIP' if hasattr(mat, 'blend_method') else None
    mesh = bpy.data.meshes.new('beam')
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new('uv')
    for b in bb.BOXES:
        M = mats[b.part]
        for face, (x0, y0, fw, fh) in bb.faces(b).items():
            if fw <= 0 or fh <= 0:
                continue
            quad = []
            for (ci, cj) in ((-0.5, -0.5), (fw - 0.5, -0.5), (fw - 0.5, fh - 0.5), (-0.5, fh - 0.5)):
                lp = bb.pixel(b, face, ci, cj)
                wp = (M @ np.array([lp[0], lp[1], lp[2], 1.0]))[:3]
                quad.append((to_build(wp), ((x0 + ci + 0.5) / tw, 1.0 - (y0 + cj + 0.5) / th)))
            try:
                f = bm.faces.new([bm.verts.new(p) for p, _ in quad])
            except ValueError:
                continue
            for loop, (_, uv) in zip(f.loops, quad):
                loop[uvl].uv = uv
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new('beam', mesh)
    obj.data.materials.append(mat)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    tex = args[args.index('--tex') + 1]
    out = args[args.index('--out') + 1]
    only = args[args.index('--only') + 1].split(',') if '--only' in args else None
    os.makedirs(out, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    blueprint.MATS.clear()
    blueprint.make_materials()
    obj = build_boxes(tex)
    blueprint.build_ground()
    for o in bpy.context.scene.objects:
        if o.get('part') == 'ground' and o.type == 'MESH':
            o.data.transform(blueprint.Matrix.Scale(blueprint.BLOCK, 4))
    obj.data.transform(blueprint.Matrix.Scale(blueprint.BLOCK, 4))
    # ground first so it is not textured
    scene = bpy.context.scene
    blueprint.studio()
    sh = scene.display.shading
    sh.color_type = 'TEXTURE'
    # the ground and its shadow blob use their own diffuse colours: make them solid
    for o in scene.objects:
        if o.get('part') == 'ground':
            o.data.materials.clear()
            m = bpy.data.materials.new('g')
            m.diffuse_color = (0.12, 0.30, 0.09, 1) if o.name != 'blob' else (0.08, 0.22, 0.06, 1)
            o.data.materials.append(m)
    extra = [('head34', (-5.0, -7.0, 3.8), (0.0, -2.0, 2.6), 60, (1500, 1100)),
             ('eyes', (-6.5, -2.4, 3.2), (0.0, -1.6, 2.7), 70, (1500, 1100)),
             ('headside', (-9.0, -1.8, 2.6), (0.0, -1.8, 2.5), 70, (1500, 1100))]
    for name, loc, target, lens, size in blueprint.SHOTS + extra:
        if only and name not in only:
            continue
        scene.render.resolution_x, scene.render.resolution_y = size
        cam = scene.camera
        cam.location = Vector(loc) * blueprint.BLOCK
        blueprint.look_at(cam, Vector(target) * blueprint.BLOCK)
        cam.data.lens = lens
        scene.render.filepath = os.path.join(out, name + '.png')
        bpy.ops.render.render(write_still=True)
        print('rendered', name, flush=True)


main()
