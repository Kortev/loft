"""Measures the smooth Beam blueprint (tools/beam_model.py) for the box version: dumps every part's geometry (pixels, build
space: x to his left, y backward, z up, feet on z = 0) to an .npz, and renders orthographic side, top and front views.

    blender -b --factory-startup --python tools/beam_measure.py -- --out DIR

Run from the repository root. tools/beam_boxes.py reads DIR/geometry.npz.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.getcwd(), 'tools'))
import bpy  # noqa: E402
import numpy as np  # noqa: E402
from mathutils import Vector  # noqa: E402

import beam_model as bm  # noqa: E402


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    out = args[args.index('--out') + 1]
    os.makedirs(out, exist_ok=True)
    bm.build(with_rider=False)
    data = {}
    for o in bm.game_objects():
        me = bm.world_mesh(o)
        v = np.array([tuple(x.co) for x in me.vertices])
        data['v_' + o.name + '__' + o['part']] = v
    np.savez(os.path.join(out, 'geometry.npz'), **data)
    print('parts', sorted(data))
    copies, img = bm.bake_look()
    tex = os.path.join(out, 'ortho_texture.png')
    bm.save_png(tex, img)
    bm.textured(copies, tex)
    bm.studio()
    scene = bpy.context.scene
    cam = scene.camera
    cam.data.type = 'ORTHO'
    for o in list(scene.objects):
        if o.get('part') == 'ground':
            o.hide_render = True
    shots = [('ortho_side', (-80, 0, 2.2), (0, 0, 2.2), 6.0), ('ortho_top', (0, 0, 80), (0, 0, 0), 6.0),
             ('ortho_front', (0, -80, 2.2), (0, 0, 2.2), 6.0)]
    for name, loc, target, scale in shots:
        scene.render.resolution_x = scene.render.resolution_y = 1920
        cam.data.ortho_scale = scale * bm.BLOCK
        cam.location = Vector(loc) * bm.BLOCK
        if name == 'ortho_top':
            cam.rotation_euler = (0, 0, -1.5707963)  # snout to the top of the picture... set below
            bm.look_at(cam, Vector(target) * bm.BLOCK)
            cam.rotation_euler = (0, 0, 0)
        else:
            bm.look_at(cam, Vector(target) * bm.BLOCK * 1.0 + Vector((0, 0, 0)))
        scene.render.filepath = os.path.join(out, name + '.png')
        bpy.ops.render.render(write_still=True)
        print('rendered', name)


main()
