"""Pochita, built in Blender from the anime stills kortev sent (the chainsaw devil as a little dog), for the game.

What he is, from the stills (kortev: "look at the one of him running, they are much longer shaped"): one soft, long,
low bean of a body, head and body all one piece, the front end (his head) the fullest, sloping down to his rump; four
short stubs of legs that are part of him, not stuck on; under the front a little snout with an open mouth, a fang and
his tongue. A chainsaw's bar comes out of his forehead: its root is deep in his head, and it runs out forward and a
little up, on edge, down the middle of his face to between his eyes (seen from the front it's the dark strip of chain
splitting his face), pale grey with a dark chain of big spikes all round. A big black D handle, flat in section and
rounded, stands across his back, leaning back. A thin black tail curls up out of his rump and ends in a little
triangle (the starter's grip). His eyes are drawn on, as the anime draws them: big round black pupils on white, set
on the sides of his head, a glint in each.

The style is the anime's: flat colour in two tones (his orange, and a reddish brown in shadow, cut hard against a light
from in front and above), black eyes and line, and a black outline round him. The outline is an inverted hull (a
slightly larger black copy of him, turned inside out, of which only the back shows), which the game can draw too.

Run (Blender 4.3 here; see chainsawman/HANDOFF.md):
  blender -b --factory-startup --python tools/pochita_model.py -- --renders --out DIR

--renders draws previews (side, three-quarter, front, back) in that style, to judge against the stills before he goes
into the game.
"""
import math
import os
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
from mathutils import Vector
from mathutils.bvhtree import BVHTree

# Colours as they look on screen (sRGB), light and shadow: his orange (its shadow the anime's reddish brown), the bar,
# its chain, the handle and tail; and the flat ones: the eyes' white, the ink, inside his mouth, his tongue.
ORANGE = ((0.90, 0.47, 0.20), (0.56, 0.25, 0.19))
BAR = ((0.74, 0.77, 0.79), (0.55, 0.58, 0.61))
CHAIN = ((0.25, 0.26, 0.29), (0.16, 0.17, 0.19))
BLACK = ((0.13, 0.13, 0.16), (0.07, 0.07, 0.09))
WHITE = (0.98, 0.98, 0.97)
INK = (0.04, 0.03, 0.03)
MOUTH = (0.30, 0.08, 0.08)
TONGUE = (0.89, 0.47, 0.50)
# Where the light comes from (it cuts the two tones), and where the cut is (0 to 1).
LIGHT = Vector((0.4, -0.55, 0.8)).normalized()
CUT = 0.16
# How thick the outline is (in the model's units; he's about a block and a half long here).
OUTLINE = 0.022


def linear(c):
    return tuple(x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c)


def toon(name, tones):
    """A material in two flat tones, light and shadow, cut hard by the light (Eevee: shader to RGB, a stepped ramp)."""
    m = bpy.data.materials.get(name)
    if m:
        return m
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nodes, links = m.node_tree.nodes, m.node_tree.links
    nodes.clear()
    out = nodes.new('ShaderNodeOutputMaterial')
    diffuse = nodes.new('ShaderNodeBsdfDiffuse')
    rgb = nodes.new('ShaderNodeShaderToRGB')
    ramp = nodes.new('ShaderNodeValToRGB')
    emit = nodes.new('ShaderNodeEmission')
    ramp.color_ramp.interpolation = 'CONSTANT'
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (*linear(tones[1]), 1.0)
    ramp.color_ramp.elements[1].position = CUT
    ramp.color_ramp.elements[1].color = (*linear(tones[0]), 1.0)
    links.new(diffuse.outputs[0], rgb.inputs[0])
    links.new(rgb.outputs[0], ramp.inputs[0])
    links.new(ramp.outputs[0], emit.inputs[0])
    links.new(emit.outputs[0], out.inputs[0])
    m.diffuse_color = (*linear(tones[0]), 1.0)
    return m


def flat(name, colour, cull=False):
    """A material of one flat colour, unlit (the eyes, the line); with {cull}, only its front faces drawn."""
    m = bpy.data.materials.get(name)
    if m:
        return m
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nodes, links = m.node_tree.nodes, m.node_tree.links
    nodes.clear()
    out = nodes.new('ShaderNodeOutputMaterial')
    emit = nodes.new('ShaderNodeEmission')
    emit.inputs[0].default_value = (*linear(colour), 1.0)
    links.new(emit.outputs[0], out.inputs[0])
    m.use_backface_culling = cull
    m.diffuse_color = (*linear(colour), 1.0)
    return m


def link(o):
    bpy.context.scene.collection.objects.link(o)
    return o


def mesh_obj(name, bm, mats, smooth=True):
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for m in mats:
        me.materials.append(m)
    for p in me.polygons:
        p.use_smooth = smooth
    return link(bpy.data.objects.new(name, me))


def outline(o):
    """The black line round {o}: an inverted hull (solidified outward, normals flipped, in the ink, only its back faces
    showing round the edge)."""
    o.data.materials.append(flat('ink', INK, cull=True))
    mod = o.modifiers.new('outline', 'SOLIDIFY')
    mod.thickness = OUTLINE
    mod.offset = 1.0
    mod.use_flip_normals = True
    mod.use_rim = False
    mod.material_offset = len(o.data.materials) - 1
    for m in o.data.materials[:-1]:
        m.use_backface_culling = True


def body():
    """His body, head and legs all one soft piece: a long bean, a full round head at the front, a rump, the snout under
    the front, four stubby legs sunk into his belly; fused into one surface (voxel remesh) and smoothed, so the legs and
    snout grow out of him."""
    parts = [((0.0, 0.05, 0.5), (0.42, 0.72, 0.38)), ((0.0, -0.38, 0.56), (0.44, 0.44, 0.44)), ((0.0, 0.45, 0.47), (0.38, 0.38, 0.38)),
             ((0.0, -0.78, 0.38), (0.15, 0.12, 0.12))]
    for side in (-1, 1):
        for y in (-0.4, 0.45):
            parts.append(((side * 0.24, y, 0.14), (0.12, 0.14, 0.14)))
    bm = bmesh.new()
    for centre, radii in parts:
        piece = bmesh.ops.create_uvsphere(bm, u_segments=32, v_segments=20, radius=1.0)
        bmesh.ops.scale(bm, vec=Vector(radii), verts=piece['verts'])
        bmesh.ops.translate(bm, vec=Vector(centre), verts=piece['verts'])
    me = bpy.data.meshes.new('body')
    bm.to_mesh(me)
    bm.free()
    o = link(bpy.data.objects.new('body', me))
    remesh = o.modifiers.new('fuse', 'REMESH')
    remesh.mode = 'VOXEL'
    remesh.voxel_size = 0.022
    smooth = o.modifiers.new('soften', 'SMOOTH')
    smooth.factor = 1.0
    smooth.iterations = 40
    deps = bpy.context.evaluated_depsgraph_get()
    fused = bpy.data.meshes.new_from_object(o.evaluated_get(deps))
    bpy.data.objects.remove(o)
    for p in fused.polygons:
        p.use_smooth = True
    o = link(bpy.data.objects.new('body', fused))
    fused.materials.append(toon('orange', ORANGE))
    outline(o)
    return o


def stadium(length, height, segments=12):
    """The bar's outline in its own plane (u along, v across): straight sides, the far end round."""
    r = height / 2
    pts = [(0.0, -r), (length - r, -r)]
    for i in range(1, segments):
        a = -math.pi / 2 + math.pi * i / segments
        pts.append((length - r + r * math.cos(a), r * math.sin(a)))
    pts += [(length - r, r), (0.0, r)]
    return pts


def bar():
    """The bar, out of his forehead: its root deep in his head, out forward and a little up, on edge; its chain dark round
    it, a spike on every other link."""
    length, height, thick = 1.15, 0.44, 0.06
    root = Vector((0.0, -0.32, 0.71))
    tilt = math.radians(12)
    along = Vector((0.0, -math.cos(tilt), math.sin(tilt)))
    across = Vector((0.0, math.sin(tilt), math.cos(tilt)))

    def place(u, v, w):
        return root + along * u + across * v + Vector((w, 0.0, 0.0))

    pts = stadium(length, height)
    bm = bmesh.new()
    front = [bm.verts.new(place(u, v, -thick / 2)) for u, v in pts]
    back = [bm.verts.new(place(u, v, thick / 2)) for u, v in pts]
    bm.faces.new(front[::-1])
    bm.faces.new(back)
    for i in range(len(pts) - 1):
        bm.faces.new((front[i], front[i + 1], back[i + 1], back[i]))
    outline(mesh_obj('bar', bm, [toon('bar', BAR)], smooth=False))
    bm = bmesh.new()
    rim = 0.055
    for i in range(len(pts) - 1):
        (u0, v0), (u1, v1) = pts[i], pts[i + 1]
        n = Vector((v1 - v0, -(u1 - u0))).normalized()
        quad = [(u + n.x * o, v + n.y * o) for (u, v) in ((u0, v0), (u1, v1)) for o in (0.0, rim)]
        a = [bm.verts.new(place(u, v, -thick * 0.85)) for u, v in quad]
        b = [bm.verts.new(place(u, v, thick * 0.85)) for u, v in quad]
        for f in ((a[0], a[2], a[3], a[1]), (b[0], b[1], b[3], b[2]), (a[1], a[3], b[3], b[1]), (a[0], b[0], b[2], a[2])):
            bm.faces.new(f)
        if i % 2 == 0:
            mid = ((u0 + u1) / 2, (v0 + v1) / 2)
            tip = (mid[0] + n.x * 0.17, mid[1] + n.y * 0.17)
            s = [bm.verts.new(place(u0 + n.x * rim, v0 + n.y * rim, w)) for w in (-0.025, 0.025)]
            e = [bm.verts.new(place(u1 + n.x * rim, v1 + n.y * rim, w)) for w in (-0.025, 0.025)]
            t = bm.verts.new(place(tip[0], tip[1], 0.0))
            bm.faces.new((s[0], e[0], t))
            bm.faces.new((e[1], s[1], t))
            bm.faces.new((s[1], s[0], t))
            bm.faces.new((e[0], e[1], t))
    outline(mesh_obj('chain', bm, [toon('chain', CHAIN)], smooth=False))


def decal(name, surface, at, toward, radius, mat, squash=1.0, segments=32, rings=5, lift=0.004):
    """A disc drawn onto {surface}, lying on it however it curves: centred where a ray from {at} toward {toward} meets it,
    {radius} across ({squash} its height to its width), every point of it laid back onto the surface and lifted
    {lift} off it (a later decal with more lift lies over an earlier one)."""
    bvh = BVHTree.FromObject(surface, bpy.context.evaluated_depsgraph_get())
    hit, normal, _, _ = bvh.ray_cast(Vector(at), (Vector(toward) - Vector(at)).normalized())
    if hit is None:
        print('decal %s missed' % name)
        return None
    up = Vector((0.0, 0.0, 1.0))
    side = up.cross(normal).normalized()
    up = normal.cross(side).normalized()

    def onto(p):
        found, n, _, _ = bvh.ray_cast(p + normal * 0.3, -normal)
        return (found + n * lift) if found is not None else p + normal * lift

    bm = bmesh.new()
    centre = bm.verts.new(onto(hit))
    grid = []
    for r in range(1, rings + 1):
        k = r / rings
        grid.append([bm.verts.new(onto(hit + side * math.cos(a) * radius * k + up * math.sin(a) * radius * squash * k))
                     for a in (2 * math.pi * i / segments for i in range(segments))])
    for i in range(segments):
        j = (i + 1) % segments
        bm.faces.new((centre, grid[0][i], grid[0][j]))
        for r in range(rings - 1):
            bm.faces.new((grid[r][i], grid[r + 1][i], grid[r + 1][j], grid[r][j]))
    return mesh_obj(name, bm, [mat], smooth=False)


def face(body_obj):
    """Drawn on, as the anime draws them: a big round eye on each side of the front of his head (a big black pupil
    looking ahead, a crescent of white behind and below it, a glint), and the open mouth under his snout with his
    tongue in it and a fang."""
    for side in (-1, 1):
        decal('eye_%d' % side, body_obj, (side * 1.2, -1.3, 0.64), (0.0, -0.38, 0.6), 0.16, flat('white', WHITE))
        decal('pupil_%d' % side, body_obj, (side * 1.2, -1.36, 0.67), (0.0, -0.44, 0.62), 0.125, flat('pupil', INK), lift=0.009)
        decal('glint_%d' % side, body_obj, (side * 1.2, -1.38, 0.73), (0.0, -0.46, 0.68), 0.032, flat('white', WHITE), lift=0.014)
    decal('mouth', body_obj, (0.0, -1.6, 0.3), (0.0, -0.6, 0.33), 0.11, flat('mouth', MOUTH), squash=0.7)
    decal('tongue', body_obj, (0.0, -1.6, 0.27), (0.0, -0.6, 0.3), 0.065, flat('tongue', TONGUE), squash=0.6, lift=0.008)
    decal('fang', body_obj, (0.06, -1.6, 0.36), (0.06, -0.6, 0.36), 0.025, flat('white', WHITE), squash=1.4, segments=3, rings=1, lift=0.01)


def as_mesh(o, name):
    """A curve object turned into a mesh (smooth, outlined)."""
    deps = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(o.evaluated_get(deps))
    bpy.data.objects.remove(o)
    for p in me.polygons:
        p.use_smooth = True
    m = link(bpy.data.objects.new(name, me))
    outline(m)
    return m


def handle():
    """The big black D handle across his back, leaning back: flat in section with rounded edges."""
    w, hgt, y, z0 = 0.3, 0.32, 0.12, 0.84
    curve = bpy.data.curves.new('handle', 'CURVE')
    curve.dimensions = '3D'
    curve.bevel_depth = 0.028
    curve.extrude = 0.035
    curve.bevel_resolution = 3
    curve.twist_mode = 'MINIMUM'
    spline = curve.splines.new('POLY')
    corner = 0.1
    pts = [(-w, y, z0 - 0.1), (-w, y, z0 + hgt - corner), (-w + corner, y, z0 + hgt), (w - corner, y, z0 + hgt),
           (w, y, z0 + hgt - corner), (w, y, z0 - 0.1)]
    spline.points.add(len(pts) - 1)
    for p, (x, yy, z) in zip(spline.points, pts):
        p.co = (x, yy + (z - z0) * 0.65, z, 1.0)
    curve.materials.append(toon('black', BLACK))
    return as_mesh(link(bpy.data.objects.new('handle_curve', curve)), 'handle')


def tail():
    """The thin black tail out of his rump, curling up, and the little triangle at its end."""
    curve = bpy.data.curves.new('tail', 'CURVE')
    curve.dimensions = '3D'
    curve.bevel_depth = 0.022
    curve.bevel_resolution = 2
    spline = curve.splines.new('NURBS')
    pts = [(0.0, 0.8, 0.5), (0.0, 0.98, 0.52), (0.0, 1.08, 0.66), (0.0, 1.1, 0.86), (0.0, 1.07, 0.98)]
    spline.points.add(len(pts) - 1)
    for p, c in zip(spline.points, pts):
        p.co = (*c, 1.0)
    spline.use_endpoint_u = True
    spline.order_u = 3
    curve.materials.append(toon('black', BLACK))
    as_mesh(link(bpy.data.objects.new('tail_curve', curve)), 'tail')
    tri = bpy.data.curves.new('grip', 'CURVE')
    tri.dimensions = '3D'
    tri.bevel_depth = 0.02
    s = tri.splines.new('POLY')
    c = Vector((0.0, 1.07, 0.98))
    corners = [c, c + Vector((0.0, -0.08, 0.13)), c + Vector((0.0, 0.08, 0.13))]
    s.points.add(len(corners) - 1)
    for p, v in zip(s.points, corners):
        p.co = (*v, 1.0)
    s.use_cyclic_u = True
    tri.materials.append(toon('black', BLACK))
    as_mesh(link(bpy.data.objects.new('grip_curve', tri)), 'grip')


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    b = body()
    bar()
    face(b)
    handle()
    tail()


def renders(out):
    scene = bpy.context.scene
    scene.render.engine = 'BLENDER_EEVEE_NEXT'
    scene.view_settings.view_transform = 'Standard'
    scene.render.resolution_x = 760
    scene.render.resolution_y = 600
    world = bpy.data.worlds.new('world')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs[0].default_value = (*linear((0.80, 0.86, 0.95)), 1.0)
    # Lit by the sun alone (so the shadow tone falls where it should), the sky put in behind afterwards.
    world.node_tree.nodes['Background'].inputs[1].default_value = 0.0
    scene.world = world
    scene.render.film_transparent = True
    sun = bpy.data.lights.new('sun', 'SUN')
    sun.energy = 4.0
    sun.angle = 0.0
    # No cast shadows: the two tones go by which way each bit of him faces, as a cel painter's would.
    sun.use_shadow = False
    sun_obj = link(bpy.data.objects.new('sun', sun))
    sun_obj.rotation_euler = (-LIGHT).to_track_quat('-Z', 'Y').to_euler()
    cam_data = bpy.data.cameras.new('cam')
    cam_data.type = 'ORTHO'
    cam_data.ortho_scale = 2.9
    cam = link(bpy.data.objects.new('cam', cam_data))
    scene.camera = cam
    target = Vector((0.0, -0.15, 0.6))
    for name, angle, height in (('side', 90, 0.05), ('three_quarter', 35, 0.22), ('front', 0, 0.08), ('back', 205, 0.3)):
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
