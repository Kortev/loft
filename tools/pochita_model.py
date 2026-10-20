"""Pochita, built in Blender from the anime stills kortev sent (the chainsaw devil as a little dog), for the game.

What he is, from the stills: one round bean of a body, head and body all one, taller than wide sitting and widest at
the bottom where his round haunches sit; orange, a darker reddish brown underneath and behind; two big round eyes set
wide on the front, white with a large dark pupil; a small "w" of a snout between them, low; a chainsaw's bar standing
straight out of his forehead, pale grey, its chain dark with spikes all round; a big black D handle arching over the
back of his head, as wide as he is; two stubby front paws in front of his chest, toes marked; and a thin black tail out
of his bottom, ending in a little triangle (the starter's grip).

Run (Blender 4.3 here; see chainsawman/HANDOFF.md):
  blender -b --factory-startup --python tools/pochita_model.py -- --renders --out DIR

--renders draws previews (front, three-quarter, side, back) in flat colours with dark outlines, as the game will
paint him: to judge the shape against the stills before he goes into the game.
"""
import math
import os
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
from mathutils import Matrix, Vector

# Colours (flat, as the anime paints him): his orange, the darker under-colour, the bar, its chain, the handle and tail,
# the eyes.
ORANGE = (0.89, 0.46, 0.20)
UNDER = (0.55, 0.24, 0.17)
BAR = (0.68, 0.71, 0.73)
CHAIN = (0.22, 0.23, 0.25)
BLACK = (0.10, 0.10, 0.12)
WHITE = (0.97, 0.97, 0.96)
PUPIL = (0.07, 0.07, 0.10)
TONGUE = (0.85, 0.42, 0.45)

# His body: a bean, this wide (x), deep (y, front is -y) and tall (z), sitting on the ground.
BODY = (1.0, 0.95, 1.3)
# How high up him the under-colour comes at the back, and at the front.
UNDER_BACK = 0.55
UNDER_FRONT = 0.18


def clear():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def material(name, colour):
    """A flat material of {colour}, given as it looks on screen (sRGB): Blender wants it linear."""
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.diffuse_color = (*[c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in colour], 1.0)
    return m


def obj(name, bm, mats, smooth=True):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for m in mats:
        me.materials.append(m)
    if smooth:
        for p in me.polygons:
            p.use_smooth = True
    o = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(o)
    return o


def ellipsoid(name, centre, radii, mat, segments=32, rings=20):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segments, v_segments=rings, radius=1.0)
    bmesh.ops.scale(bm, vec=Vector(radii), verts=bm.verts)
    bmesh.ops.translate(bm, vec=Vector(centre), verts=bm.verts)
    return obj(name, bm, [mat])


def body():
    """The bean: wider low down (the haunches swell it), narrowing over the head; the under-colour below and behind."""
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=48, v_segments=32, radius=1.0)
    w, d, h = BODY
    for v in bm.verts:
        x, y, z = v.co
        # Bottom flat on the ground and wide; top rounder and a little narrower.
        up = (z + 1.0) / 2.0
        widen = 1.08 - 0.16 * up
        v.co = Vector((x * w / 2 * widen, y * d / 2 * (1.0 - 0.06 * up), (z + 1.0) / 2.0 * h))
        if v.co.z < 0.06:
            v.co.z = 0.06 * (v.co.z / 0.06) ** 2 if v.co.z > 0 else 0.0
    o = obj('body', bm, [material('orange', ORANGE), material('under', UNDER)])
    for p in o.data.polygons:
        c = p.center
        # The under-colour: higher behind than in front.
        line = UNDER_FRONT + (UNDER_BACK - UNDER_FRONT) * (c.y / (d / 2) * 0.5 + 0.5)
        p.material_index = 1 if c.z < line else 0
    return o


def haunches():
    """His round hind legs, swelling out low at either side, with flat feet in front of them."""
    for side in (-1, 1):
        ellipsoid('haunch_%d' % side, (side * 0.42, 0.08, 0.3), (0.24, 0.36, 0.28), material('orange', ORANGE))
        ellipsoid('foot_%d' % side, (side * 0.45, -0.3, 0.05), (0.13, 0.2, 0.06), material('under', UNDER))


def paws():
    """The stubby front legs, down in front of his chest, toes marked by three dark lines."""
    for side in (-1, 1):
        x = side * 0.17
        ellipsoid('leg_%d' % side, (x, -0.4, 0.16), (0.1, 0.11, 0.17), material('orange', ORANGE))
        ellipsoid('paw_%d' % side, (x, -0.47, 0.05), (0.11, 0.1, 0.06), material('orange', ORANGE))
        for t in (-1, 0, 1):
            bm = bmesh.new()
            bmesh.ops.create_cube(bm, size=1.0)
            bmesh.ops.scale(bm, vec=Vector((0.008, 0.05, 0.04)), verts=bm.verts)
            bmesh.ops.translate(bm, vec=Vector((x + t * 0.04, -0.56, 0.05)), verts=bm.verts)
            obj('toe_%d_%d' % (side, t), bm, [material('under', UNDER)], smooth=False)


def face():
    """Two big round eyes set wide, white with a large dark pupil looking ahead; the little snout low between them."""
    w, d, h = BODY
    for side in (-1, 1):
        x = side * 0.27
        ellipsoid('eye_%d' % side, (x, -0.43, 0.86), (0.15, 0.045, 0.15), material('white', WHITE))
        ellipsoid('pupil_%d' % side, (x * 1.02, -0.47, 0.85), (0.105, 0.02, 0.105), material('pupil', PUPIL))
    # The snout: two round cheeks and a dark nose between, with a small tongue beneath (as in the stills).
    for side in (-1, 1):
        ellipsoid('cheek_%d' % side, (side * 0.065, -0.47, 0.62), (0.075, 0.05, 0.06), material('orange', ORANGE))
    ellipsoid('nose', (0.0, -0.5, 0.66), (0.03, 0.02, 0.022), material('pupil', PUPIL))


def stadium(length, height, segments=10):
    """The bar's outline in its own plane (u along, v across): straight sides, the far end round."""
    r = height / 2
    pts = [(0.0, -r), (length - r, -r)]
    for i in range(1, segments):
        a = -math.pi / 2 + math.pi * i / segments
        pts.append((length - r + r * math.cos(a), r * math.sin(a)))
    pts += [(length - r, r), (0.0, r)]
    return pts


def bar():
    """The chainsaw's bar straight out of his forehead, standing on edge, its chain dark round it with spikes."""
    length, height, thick = 0.95, 0.42, 0.05
    root = Vector((0.0, -0.3, 1.12))
    # Out forward and up a little, on edge (its flat faces to the sides).
    tilt = math.radians(28)
    along = Vector((0.0, -math.cos(tilt), math.sin(tilt)))
    across = Vector((0.0, math.sin(tilt), math.cos(tilt)))
    frame = Matrix(((0, along.x, across.x, root.x), (1, along.y, across.y, root.y), (0, along.z, across.z, root.z), (0, 0, 0, 1)))
    frame = Matrix(((1, 0, 0, 0), (0, 1, 0, 0), (0, 0, 1, 0), (0, 0, 0, 1)))
    bm = bmesh.new()
    pts = stadium(length, height)

    def place(u, v, w):
        return root + along * u + across * v + Vector((w, 0.0, 0.0))

    front = [bm.verts.new(place(u, v, -thick / 2)) for u, v in pts]
    back = [bm.verts.new(place(u, v, thick / 2)) for u, v in pts]
    bm.faces.new(front[::-1])
    bm.faces.new(back)
    for i in range(len(pts) - 1):
        bm.faces.new((front[i], front[i + 1], back[i + 1], back[i]))
    obj('bar', bm, [material('bar', BAR)], smooth=False)
    # The chain: a dark rim round the edge, spikes standing out of it.
    bm = bmesh.new()
    rim = 0.05
    for i in range(len(pts) - 1):
        (u0, v0), (u1, v1) = pts[i], pts[i + 1]
        n = Vector((v1 - v0, -(u1 - u0))).normalized()
        quad = []
        for (u, v) in ((u0, v0), (u1, v1)):
            for o in (0.0, rim):
                quad.append((u + n.x * o, v + n.y * o))
        vs = []
        for w in (-thick * 0.8, thick * 0.8):
            vs.append([bm.verts.new(place(u, v, w)) for u, v in quad])
        a, b = vs
        for f in ((a[0], a[2], a[3], a[1]), (b[0], b[1], b[3], b[2]), (a[1], a[3], b[3], b[1]), (a[0], b[0], b[2], a[2])):
            bm.faces.new(f)
        # A spike every other segment.
        if i % 2 == 0:
            mid = ((u0 + u1) / 2, (v0 + v1) / 2)
            tip = (mid[0] + n.x * 0.13, mid[1] + n.y * 0.13)
            s = [bm.verts.new(place(u0 + n.x * rim, v0 + n.y * rim, w)) for w in (-0.02, 0.02)]
            e = [bm.verts.new(place(u1 + n.x * rim, v1 + n.y * rim, w)) for w in (-0.02, 0.02)]
            t = bm.verts.new(place(tip[0], tip[1], 0.0))
            bm.faces.new((s[0], e[0], t))
            bm.faces.new((e[1], s[1], t))
            bm.faces.new((s[1], s[0], t))
    obj('chain', bm, [material('chain', CHAIN)], smooth=False)


def handle():
    """The big black D handle arching over the back of his head, as wide as he is: a thick rounded tube."""
    w = 0.4
    hgt = 0.4
    y = 0.26
    z0 = 1.14
    r = 0.07
    curve = bpy.data.curves.new('handle', 'CURVE')
    curve.dimensions = '3D'
    curve.bevel_depth = r
    curve.bevel_resolution = 4
    spline = curve.splines.new('POLY')
    corner = 0.12
    pts = [(-w, y, z0 - 0.08), (-w, y, z0 + hgt - corner), (-w + corner, y, z0 + hgt), (w - corner, y, z0 + hgt), (w, y, z0 + hgt - corner),
           (w, y, z0 - 0.08)]
    spline.points.add(len(pts) - 1)
    for p, (x, yy, z) in zip(spline.points, pts):
        # Leaning back a little.
        p.co = (x, yy + (z - z0) * 0.25, z, 1.0)
    o = bpy.data.objects.new('handle', curve)
    curve.materials.append(material('black', BLACK))
    bpy.context.scene.collection.objects.link(o)


def tail():
    """The thin black tail out of his bottom behind, curling up, ending in the starter's little triangle."""
    curve = bpy.data.curves.new('tail', 'CURVE')
    curve.dimensions = '3D'
    curve.bevel_depth = 0.022
    curve.bevel_resolution = 2
    spline = curve.splines.new('NURBS')
    pts = [(0.0, 0.42, 0.1), (0.05, 0.62, 0.06), (0.12, 0.8, 0.12), (0.16, 0.9, 0.3), (0.18, 0.92, 0.45)]
    spline.points.add(len(pts) - 1)
    for p, c in zip(spline.points, pts):
        p.co = (*c, 1.0)
    spline.use_endpoint_u = True
    spline.order_u = 3
    o = bpy.data.objects.new('tail', curve)
    curve.materials.append(material('black', BLACK))
    bpy.context.scene.collection.objects.link(o)
    # The triangle at its end.
    tri = bpy.data.curves.new('grip', 'CURVE')
    tri.dimensions = '3D'
    tri.bevel_depth = 0.02
    s = tri.splines.new('POLY')
    c = Vector((0.18, 0.92, 0.45))
    corners = [c, c + Vector((-0.07, 0.0, 0.11)), c + Vector((0.07, 0.0, 0.11))]
    s.points.add(len(corners) - 1)
    for p, v in zip(s.points, corners):
        p.co = (*v, 1.0)
    s.use_cyclic_u = True
    g = bpy.data.objects.new('grip', tri)
    tri.materials.append(material('black', BLACK))
    bpy.context.scene.collection.objects.link(g)


def build():
    clear()
    body()
    haunches()
    paws()
    face()
    bar()
    handle()
    tail()


def renders(out):
    scene = bpy.context.scene
    scene.render.engine = 'BLENDER_WORKBENCH'
    # Colours as painted, not toned down.
    scene.view_settings.view_transform = 'Standard'
    shading = scene.display.shading
    shading.light = 'FLAT'
    shading.color_type = 'MATERIAL'
    shading.show_object_outline = True
    shading.object_outline_color = (0.05, 0.03, 0.03)
    scene.render.film_transparent = False
    scene.world = bpy.data.worlds.new('world')
    scene.world.color = (0.62, 0.74, 0.95)
    scene.render.resolution_x = 600
    scene.render.resolution_y = 700
    cam_data = bpy.data.cameras.new('cam')
    cam_data.type = 'ORTHO'
    cam_data.ortho_scale = 2.6
    cam = bpy.data.objects.new('cam', cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    target = Vector((0.0, -0.1, 0.8))
    for name, angle, height in (('front', 0, 0.0), ('three_quarter', 40, 0.25), ('side', 90, 0.0), ('back', 200, 0.3)):
        a = math.radians(angle)
        pos = target + Vector((math.sin(a) * 6.0, -math.cos(a) * 6.0, height * 6.0))
        cam.location = pos
        cam.rotation_euler = (target - pos).to_track_quat('-Z', 'Y').to_euler()
        scene.render.filepath = os.path.join(out, 'pochita_%s.png' % name)
        bpy.ops.render.render(write_still=True)
        print('render', scene.render.filepath)


def main():
    args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    out = args[args.index('--out') + 1] if '--out' in args else 'build/pochita'
    os.makedirs(out, exist_ok=True)
    build()
    if '--renders' in args:
        renders(out)


if __name__ == '__main__':
    main()
