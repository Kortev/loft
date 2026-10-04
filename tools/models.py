"""Builds the uplink feed's meshes in Blender and exports them as .ssm files for the mod.

Needs Blender's Python module (pip install "bpy==4.5.*"). Run from the repository root:

    python tools/models.py                 # writes the .ssm meshes
    python tools/models.py --preview DIR   # also renders a Cycles preview of each mesh into DIR

.ssm layout (little endian): b"SSM1", int32 vertex count, int32 index count, then per vertex
float32 x, y, z, float32 u, v, uint8 ambient occlusion, glow, material, 255, int8 nx, ny, nz, 0;
then uint32 triangle indices. Coordinates are converted to Minecraft's Y-up axes.
"""
import math
import os
import random
import struct
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
from mathutils import Vector, noise
from mathutils.bvhtree import BVHTree

OUT = 'src/client/resources/assets/shootingstar/meshes'

# Material ids, read by the feed's mesh shader.
HULL, FIN, NOSE, FRAME, PANEL, ROCK, FOIL, SOLAR, DISH, EDGE, RUNE, COPPER, SABOT, CABLE, RADIATOR = range(15)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


class Builder:
    """A bmesh with per-face material and glow layers."""

    def __init__(self):
        self.bm = bmesh.new()
        self.mat = self.bm.faces.layers.int.new('mat')
        self.glow = self.bm.faces.layers.float.new('glow')

    def face(self, verts, mat, glow=0.0):
        f = self.bm.faces.new(verts)
        f[self.mat] = mat
        f[self.glow] = glow
        return f

    def vert(self, x, y, z):
        return self.bm.verts.new((x, y, z))

    def box(self, center, size, mat, glow=0.0, rot=0.0):
        """Axis-aligned box (optionally rotated about the Blender Y axis, which is the round's axis)."""
        cx, cy, cz = center
        sx, sy, sz = (s / 2 for s in size)
        c, s = math.cos(rot), math.sin(rot)
        vs = []
        for dx, dy, dz in [(-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                           (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]:
            x, z = dx * sx, dz * sz
            vs.append(self.vert(cx + x * c - z * s, cy + dy * sy, cz + x * s + z * c))
        for q in [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]:
            self.face([vs[i] for i in q], mat, glow)
        return vs

    def to_object(self, name):
        me = bpy.data.meshes.new(name)
        bmesh.ops.remove_doubles(self.bm, verts=self.bm.verts, dist=1e-5)
        bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        self.bm.normal_update()
        self.bm.to_mesh(me)
        self.bm.free()
        obj = bpy.data.objects.new(name, me)
        bpy.context.scene.collection.objects.link(obj)
        return obj


# --- the round ---------------------------------------------------------------------------------

def lathe(b, profile, mat_of, glow_of, seg=48, a0=0.0, a1=2 * math.pi, closed=True):
    """Surface of revolution about the round's axis (Blender -Y is forward). profile: (zz, r) from front to back;
    mat_of(zz)/glow_of(zz) pick the band's material. Returns the rings of vertices."""
    count = seg if closed else seg + 1
    rings = []
    for zz, r in profile:
        if r == 0.0:
            rings.append([b.vert(0, -zz, 0)])
            continue
        ring = []
        for i in range(count):
            a = a0 + (a1 - a0) * i / seg
            ring.append(b.vert(r * math.cos(a), -zz, r * math.sin(a)))
        rings.append(ring)
    for k in range(len(rings) - 1):
        a, c = rings[k], rings[k + 1]
        zm = (profile[k][0] + profile[k + 1][0]) / 2
        mat, glow = mat_of(zm), glow_of(zm)
        n = seg
        for i in range(n):
            j = (i + 1) % count if closed else i + 1
            if len(a) == 1:
                b.face([a[0], c[j], c[i]], mat, glow)
            elif len(c) == 1:
                b.face([a[i], a[j], c[0]], mat, glow)
            else:
                b.face([a[i], a[j], c[j], c[i]], mat, glow)
    return rings


def build_round():
    """Gungnir: a spear. A leaf-shaped tungsten-carbide blade with glowing edges, a socket ring carved with runes,
    a slim tungsten shaft and six small swept fins round a tracer. The sabot that rides the coils is a separate
    mesh (see build_sabot). Nose along Blender -Y (Minecraft +Z), from zz = +5 to the tail at zz = -5."""
    b = Builder()
    shaft = 0.17

    # The blade: a flattened diamond in section, widest just ahead of the socket, a fine bevel along each edge.
    stations = []
    for i in range(29):
        f = i / 28.0
        zz = 2.55 + 2.45 * f
        if zz < 3.2:
            u = (zz - 2.55) / 0.65
            w = 0.215 + (0.47 - 0.215) * math.sin(u * math.pi / 2)
        else:
            u = (zz - 3.2) / 1.8
            w = 0.47 * (1.0 - u ** 1.35) ** 0.85
        t = 0.215 * (1.0 - min(1.0, (zz - 2.55) / 0.6)) + 0.115 * min(1.0, (zz - 2.55) / 0.6)
        t *= (1.0 - max(0.0, zz - 3.2) / 1.8) ** 0.9
        stations.append((zz, max(w, 0.0), max(t, 0.0)))
    section = [(1.0, 0.0), (0.93, 0.16), (0.0, 1.0), (-0.93, 0.16), (-1.0, 0.0), (-0.93, -0.16), (0.0, -1.0), (0.93, -0.16)]
    rings = []
    for zz, w, t in stations:
        if w < 1e-4:
            rings.append([b.vert(0, -zz, 0)])
        else:
            rings.append([b.vert(sx * w, -zz, sz * t) for sx, sz in section])
    for k in range(len(rings) - 1):
        a, c = rings[k], rings[k + 1]
        for i in range(8):
            j = (i + 1) % 8
            # Faces touching the side points (index 0 and 4) are the bevelled cutting edges.
            edge = i in (0, 3, 4, 7)
            mat, glow = (EDGE, 0.6) if edge else (NOSE, 0.0)
            if len(c) == 1:
                b.face([a[i], a[j], c[0]], mat, glow)
            else:
                b.face([a[i], a[j], c[j], c[i]], mat, glow)
    # Close the blade's back onto the socket.
    back = b.vert(0, -2.55, 0)
    for i in range(8):
        b.face([rings[0][(i + 1) % 8], rings[0][i], back], NOSE)

    # Socket: a collar with raised bindings and a band of runes, then the shaft, then the tail.
    def mat_of(zz):
        if 2.13 <= zz <= 2.42:
            return RUNE
        if zz > 1.9:
            return FRAME
        if zz < -4.1:
            return FIN
        return HULL

    def glow_of(zz):
        if 2.13 <= zz <= 2.42:
            return 1.0
        if zz < -4.97:
            return 1.0
        return 0.0

    profile = [(2.56, 0.0), (2.56, 0.2), (2.52, 0.235), (2.46, 0.235), (2.43, 0.215), (2.42, 0.212), (2.13, 0.212),
               (2.12, 0.215), (2.09, 0.235), (2.02, 0.235), (1.98, 0.2), (1.9, shaft)]
    z = 1.9
    while z > -3.9:
        # Fine machining grooves along the shaft.
        profile += [(z - 0.02, shaft), (z - 0.03, shaft - 0.008), (z - 0.06, shaft - 0.008), (z - 0.07, shaft)]
        z -= 0.92
    profile += [(-4.1, shaft), (-4.6, 0.155), (-4.9, 0.13), (-4.97, 0.125), (-4.98, 0.08), (-5.0, 0.07), (-5.0, 0.0)]
    lathe(b, profile, mat_of, glow_of, seg=40)

    # Six thin swept fins with a faintly glowing leading edge.
    for k in range(6):
        ang = k * math.pi / 3 + math.pi / 6
        ca, sa = math.cos(ang), math.sin(ang)

        def at(radius, zz, side):
            t = 0.022 if radius < 0.3 else 0.012
            return b.vert(radius * ca - side * t * sa, -zz, radius * sa + side * t * ca)

        root_front, root_back, tip_front, tip_back = -3.55, -4.86, -4.3, -4.97
        r0, r1 = shaft - 0.01, 0.6
        v = [at(r0, root_front, -1), at(r0, root_back, -1), at(r1, tip_back, -1), at(r1, tip_front, -1),
             at(r0, root_front, 1), at(r0, root_back, 1), at(r1, tip_back, 1), at(r1, tip_front, 1)]
        b.face([v[0], v[1], v[2], v[3]], FIN)
        b.face([v[7], v[6], v[5], v[4]], FIN)
        b.face([v[0], v[3], v[7], v[4]], EDGE, 0.5)   # leading edge
        b.face([v[3], v[2], v[6], v[7]], FIN)
        b.face([v[2], v[1], v[5], v[6]], FIN)
        b.face([v[1], v[0], v[4], v[5]], FIN)
    obj = b.to_object('round')
    smooth(obj, 32)
    return obj


def build_sabot():
    """One of the sabot's three petals: the collar that carries the spear down the accelerator and is shed at the
    muzzle. Wraps the shaft from zz = 1.75 back to -2.05 over 120 degrees (less a seam), with a scoop at the front
    and four copper armature bands the coils push on."""
    b = Builder()
    gap = math.radians(1.2)
    a0, a1 = -math.pi / 3 + gap, math.pi / 3 - gap
    inner = 0.172
    bands = [0.85, 0.15, -0.55, -1.25]

    def mat_of(zz):
        for band in bands:
            if abs(zz - band) < 0.075:
                return COPPER
        return SABOT

    def glow_of(zz):
        for band in bands:
            if abs(zz - band) < 0.075:
                return 1.0
        return 0.0

    outer = [(1.75, 0.2), (1.62, 0.27), (1.45, 0.35), (1.25, 0.41), (1.08, 0.44)]
    for band in bands:
        outer += [(band + 0.09, 0.44), (band + 0.075, 0.425), (band - 0.075, 0.425), (band - 0.09, 0.44)]
    outer += [(-1.6, 0.44), (-1.8, 0.4), (-1.95, 0.33), (-2.05, 0.3)]
    seg = 20
    out_rings = lathe(b, outer, mat_of, glow_of, seg=seg, a0=a0, a1=a1, closed=False)
    in_rings = lathe(b, [(1.75, inner), (-2.05, inner)], lambda zz: SABOT, lambda zz: 0.0, seg=seg, a0=a0, a1=a1,
                     closed=False)
    # Front and back faces between the inner bore and the outer skin.
    for o_ring, i_ring in ((out_rings[0], in_rings[0]), (out_rings[-1], in_rings[-1])):
        for i in range(seg):
            b.face([o_ring[i], o_ring[i + 1], i_ring[i + 1], i_ring[i]], SABOT)
    # The two seam faces along the petal's long edges.
    for idx in (0, seg):
        for k in range(len(out_rings) - 1):
            zz_a, zz_b = outer[k][0], outer[k + 1][0]
            ia = b.vert(inner * math.cos(a0 if idx == 0 else a1), -zz_a, inner * math.sin(a0 if idx == 0 else a1))
            ib = b.vert(inner * math.cos(a0 if idx == 0 else a1), -zz_b, inner * math.sin(a0 if idx == 0 else a1))
            b.face([out_rings[k][idx], out_rings[k + 1][idx], ib, ia], SABOT)
    obj = b.to_object('sabot')
    smooth(obj, 30)
    return obj


# --- the accelerator coil ----------------------------------------------------------------------

def build_coil():
    """One accelerator coil, the beam along Blender Y (Minecraft Z), spaced 1.0 apart so the spine and cables join
    up from coil to coil. Twelve chamfered magnet housings in a ring, each with a narrow emitter strip on its inner
    face that fires as the round goes through; radiator fins at four places that glow after firing; a cable bundle
    over the top, a box truss under it, and status lights."""
    b = Builder()
    n = 12
    r_in, r_out, depth, ch = 0.72, 1.0, 0.28, 0.035
    gap = math.radians(1.8)
    # The housing's section in the (radius, axial) plane, going round.
    section = [(r_in + ch, -depth / 2), (r_out - ch, -depth / 2), (r_out, -depth / 2 + ch), (r_out, depth / 2 - ch),
               (r_out - ch, depth / 2), (r_in + ch, depth / 2), (r_in, depth / 2 - ch), (r_in, -depth / 2 + ch)]
    inner_face = 6  # the face from section[6] to section[7] looks at the beam
    steps = 6
    for i in range(n):
        a0 = i * 2 * math.pi / n - math.pi / n + gap / 2
        a1 = (i + 1) * 2 * math.pi / n - math.pi / n - gap / 2
        rings = []
        for k in range(steps + 1):
            a = a0 + (a1 - a0) * k / steps
            rings.append([b.vert(r * math.cos(a), y, r * math.sin(a)) for r, y in section])
        for k in range(steps):
            strip = 1 <= k <= steps - 2
            for e in range(len(section)):
                f = (e + 1) % len(section)
                if e == inner_face and strip:
                    b.face([rings[k][e], rings[k][f], rings[k + 1][f], rings[k + 1][e]], PANEL, 1.0)
                else:
                    b.face([rings[k][e], rings[k][f], rings[k + 1][f], rings[k + 1][e]], FRAME)
        for ring in (rings[0], rings[-1]):
            b.face(list(ring), FRAME)
        mid = (a0 + a1) / 2
        if i % 2 == 0:
            # A status light on the front face.
            c = Vector((math.cos(mid) * 0.86, depth / 2 + 0.006, math.sin(mid) * 0.86))
            b.box(c, (0.05, 0.012, 0.05), PANEL, 0.5, rot=-mid)
    # Radiator fins on the diagonals.
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        for f in range(3):
            y = -0.16 + f * 0.16
            c = Vector((math.cos(a) * 1.3, y, math.sin(a) * 1.3))
            b.box(c, (0.6, 0.12, 0.018), RADIATOR, 1.0, rot=-a)
        b.box(Vector((math.cos(a) * 1.03, 0, math.sin(a) * 1.03)), (0.1, 0.36, 0.1), FRAME, rot=-a)
    # Cable bundle running over the top from coil to coil.
    for k, off in enumerate((-0.07, 0.0, 0.07)):
        a = math.pi / 2 + off
        cx, cz = math.cos(a) * 1.07, math.sin(a) * 1.07
        seg = 10
        ring0 = [b.vert(cx + 0.032 * math.cos(2 * math.pi * j / seg), -0.5, cz + 0.032 * math.sin(2 * math.pi * j / seg))
                 for j in range(seg)]
        ring1 = [b.vert(cx + 0.032 * math.cos(2 * math.pi * j / seg), 0.5, cz + 0.032 * math.sin(2 * math.pi * j / seg))
                 for j in range(seg)]
        for j in range(seg):
            jn = (j + 1) % seg
            b.face([ring0[j], ring0[jn], ring1[jn], ring1[j]], CABLE)
    b.box(Vector((0, 0, 1.07)), (0.24, 0.08, 0.06), FRAME)  # cable clamp
    # Truss under the ring: two rails along the beam and a strut up to the housing.
    for side in (-0.12, 0.12):
        b.box(Vector((side, 0, -1.32)), (0.06, 1.0, 0.06), FRAME)
    b.box(Vector((0, 0, -1.32)), (0.3, 0.06, 0.05), FRAME)
    b.box(Vector((0, 0, -1.14)), (0.07, 0.07, 0.32), FRAME)
    b.box(Vector((0, 0.3, -1.32)), (0.05, 0.05, 0.05), PANEL, 0.6)  # spine light
    obj = b.to_object('coil')
    smooth(obj, 30)
    return obj


# --- asteroids ---------------------------------------------------------------------------------

def build_asteroid(index):
    rnd = random.Random(1000 + index)
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=4, radius=1.0)
    stretch = Vector((rnd.uniform(0.75, 1.3), rnd.uniform(0.65, 1.0), rnd.uniform(0.7, 1.2)))
    offset = Vector((rnd.uniform(-50, 50), rnd.uniform(-50, 50), rnd.uniform(-50, 50)))
    craters = [(Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized(), rnd.uniform(0.18, 0.42))
               for _ in range(rnd.randint(3, 7))]
    for v in bm.verts:
        d = v.co.normalized()
        h = 1.0 + 0.28 * noise.fractal(d * 1.3 + offset, 1.0, 2.0, 5) + 0.05 * noise.noise(d * 9.0 + offset)
        for c, size in craters:
            ang = d.angle(c)
            if ang < size:
                t = ang / size
                h -= 0.14 * size * (1.0 - t * t) * 2.2
            elif ang < size * 1.35:
                h += 0.05 * size * (1.0 - (ang - size) / (size * 0.35))
        v.co = Vector((d.x * stretch.x, d.y * stretch.y, d.z * stretch.z)) * h
    b = Builder()
    b.bm.free()
    b.bm = bm
    b.mat = bm.faces.layers.int.new('mat')
    b.glow = bm.faces.layers.float.new('glow')
    for f in bm.faces:
        f[b.mat] = ROCK
    obj = b.to_object('asteroid%d' % index)
    smooth(obj, 60)
    return obj


# --- relay satellite ---------------------------------------------------------------------------

def build_relay():
    """The orbital relay that bounces the uplink to Jupiter. Dish faces Blender -Y (Minecraft +Z)."""
    b = Builder()
    b.box((0, 0, 0), (0.7, 0.9, 0.7), FOIL)
    b.box((0, 0.5, 0), (0.5, 0.12, 0.5), FRAME)
    for side in (-1, 1):
        b.box((side * 0.75, 0, 0), (0.5, 0.06, 0.06), FRAME)
        for k in range(3):
            b.box((side * (1.1 + k * 0.72), 0, 0), (0.68, 0.025, 0.9), SOLAR)
    # Dish: a shallow paraboloid on a short mast.
    b.box((0, -0.55, 0), (0.08, 0.3, 0.08), FRAME)
    seg, rings = 32, 6
    center = b.vert(0, -0.75, 0)
    verts = []
    for k in range(1, rings + 1):
        r = 0.62 * k / rings
        y = -0.75 - 0.32 * (r / 0.62) ** 2
        verts.append([b.vert(r * math.cos(2 * math.pi * i / seg), y, r * math.sin(2 * math.pi * i / seg))
                      for i in range(seg)])
    for i in range(seg):
        j = (i + 1) % seg
        b.face([center, verts[0][j], verts[0][i]], DISH)
        for k in range(rings - 1):
            b.face([verts[k][i], verts[k][j], verts[k + 1][j], verts[k + 1][i]], DISH)
    b.box((0, -1.2, 0), (0.06, 0.5, 0.06), FRAME)
    b.box((0, -1.47, 0), (0.1, 0.06, 0.1), PANEL, 1.0)  # emitter
    obj = b.to_object('relay')
    smooth(obj, 30)
    return obj


# --- helpers -----------------------------------------------------------------------------------

def smooth(obj, angle):
    me = obj.data
    me.shade_smooth()
    me.set_sharp_from_angle(angle=math.radians(angle))


def ambient_occlusion(obj, samples=96, reach=None):
    """Per-corner hemisphere occlusion from ray casts against the mesh itself."""
    me = obj.data
    bvh = BVHTree.FromObject(obj, bpy.context.evaluated_depsgraph_get())
    size = max(obj.dimensions)
    reach = reach or size * 0.35
    rnd = random.Random(7)
    dirs = []
    for _ in range(samples):
        u, v = rnd.random(), rnd.random()
        r, phi = math.sqrt(u), 2 * math.pi * v
        dirs.append(Vector((r * math.cos(phi), r * math.sin(phi), math.sqrt(max(0.0, 1 - u)))))
    cache = {}
    result = []
    normals = me.corner_normals
    for loop in me.loops:
        n = Vector(normals[loop.index].vector)
        key = (loop.vertex_index, round(n.x, 3), round(n.y, 3), round(n.z, 3))
        if key not in cache:
            p = me.vertices[loop.vertex_index].co
            t = n.orthogonal().normalized()
            bt = n.cross(t)
            hit = 0.0
            for d in dirs:
                w = t * d.x + bt * d.y + n * d.z
                loc, _, _, dist = bvh.ray_cast(p + n * (size * 0.002), w, reach)
                if loc is not None:
                    hit += 1.0 - (dist / reach) ** 2
            cache[key] = max(0.0, 1.0 - hit / samples * 1.15)
        result.append(cache[key])
    return result


def export(obj, path):
    me = obj.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.triangulate(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    ao = ambient_occlusion(obj)
    mat = me.attributes['mat'].data
    glow = me.attributes['glow'].data
    normals = me.corner_normals
    verts, index, lookup = [], [], {}
    for poly in me.polygons:
        for li in poly.loop_indices:
            loop = me.loops[li]
            co = me.vertices[loop.vertex_index].co
            n = normals[li].vector
            # Blender (x, y, z) with Z up -> Minecraft (x, z, -y) with Y up.
            rec = (round(co.x, 5), round(co.z, 5), round(-co.y, 5),
                   int(round(max(0.0, min(1.0, ao[li])) * 255)), int(round(glow[poly.index].value * 255)),
                   mat[poly.index].value,
                   int(round(n.x * 127)), int(round(n.z * 127)), int(round(-n.y * 127)))
            if rec not in lookup:
                lookup[rec] = len(verts)
                verts.append(rec)
            index.append(lookup[rec])
    with open(path, 'wb') as f:
        f.write(b'SSM1' + struct.pack('<ii', len(verts), len(index)))
        for x, y, z, a, g, m, nx, ny, nz in verts:
            f.write(struct.pack('<fffffBBBBbbbb', x, y, z, 0.0, 0.0, a, g, m, 255, nx, ny, nz, 0))
        f.write(struct.pack('<%dI' % len(index), *index))
    print('%-10s %6d vertices %6d triangles  %s' % (obj.name, len(verts), len(index) // 3, path))


def preview(objs, directory):
    """Cycles renders using the exported attributes, for checking the shapes."""
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 48
    scene.cycles.device = 'CPU'
    scene.render.resolution_x, scene.render.resolution_y = 640, 400
    world = bpy.data.worlds.new('w')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs[0].default_value = (0.02, 0.025, 0.04, 1)
    scene.world = world
    sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
    sun.data.energy = 4.0
    sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(40))
    scene.collection.objects.link(sun)
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    palette = {HULL: (0.05, 0.05, 0.055), FIN: (0.06, 0.055, 0.05), NOSE: (0.08, 0.07, 0.065),
               FRAME: (0.07, 0.07, 0.075), PANEL: (0.4, 0.15, 0.03), ROCK: (0.22, 0.19, 0.16),
               FOIL: (0.6, 0.45, 0.12), SOLAR: (0.03, 0.05, 0.12), DISH: (0.7, 0.7, 0.7)}
    for obj in objs:
        for other in objs:
            other.hide_render = other is not obj
        m = bpy.data.materials.new(obj.name)
        m.use_nodes = True
        nt = m.node_tree
        bsdf = nt.nodes['Principled BSDF']
        attr_mat = nt.nodes.new('ShaderNodeAttribute')
        attr_mat.attribute_name = 'mat'
        attr_glow = nt.nodes.new('ShaderNodeAttribute')
        attr_glow.attribute_name = 'glow'
        ramp = nt.nodes.new('ShaderNodeValToRGB')
        ramp.color_ramp.interpolation = 'CONSTANT'
        els = ramp.color_ramp.elements
        els[0].position, els[0].color = 0.0, (*palette[0], 1)
        els[1].position, els[1].color = 1.0 / 9, (*palette[1], 1)
        for k in range(2, 9):
            e = els.new(k / 9)
            e.color = (*palette[k], 1)
        scale = nt.nodes.new('ShaderNodeMath')
        scale.operation = 'DIVIDE'
        scale.inputs[1].default_value = 9.0
        nt.links.new(attr_mat.outputs['Fac'], scale.inputs[0])
        nt.links.new(scale.outputs[0], ramp.inputs[0])
        nt.links.new(ramp.outputs[0], bsdf.inputs['Base Color'])
        bsdf.inputs['Metallic'].default_value = 0.6
        bsdf.inputs['Roughness'].default_value = 0.45
        bsdf.inputs['Emission Color'].default_value = (1.0, 0.45, 0.1, 1)
        nt.links.new(attr_glow.outputs['Fac'], bsdf.inputs['Emission Strength'])
        obj.data.materials.clear()
        obj.data.materials.append(m)
        size = max(obj.dimensions)
        cam.location = Vector((size * 0.9, -size * 0.75, size * 0.55))
        cam.rotation_euler = (Vector((0, 0, 0)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
        cam.data.lens = 45
        scene.render.filepath = os.path.join(directory, obj.name + '.png')
        bpy.ops.render.render(write_still=True)


def main():
    reset()
    os.makedirs(OUT, exist_ok=True)
    objs = [build_round(), build_sabot(), build_coil(), build_relay()] + [build_asteroid(i) for i in range(4)]
    for obj in objs:
        export(obj, os.path.join(OUT, obj.name + '.ssm'))
    if '--preview' in sys.argv:
        preview(objs, sys.argv[sys.argv.index('--preview') + 1])


if __name__ == '__main__':
    main()
