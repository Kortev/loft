"""Builds Chitty Chitty Bang Bang in Blender.

Needs Blender's Python module (pip install "bpy==4.5.*", on Python 3.11). Run from the repository root:

    python tools/chitty_model.py --game                 # writes the game's mesh, texture atlas and item icon
    python tools/chitty_model.py --out DIR              # writes DIR/chitty.blend and .glb files of each pose
    python tools/chitty_model.py --out DIR --renders    # also renders the car on the road, flying and afloat

Blender units are blocks (metres). The car faces +Y with +X on its right and +Z up, its wheels on the ground at
Z = 0 and the middle of its wheelbase over the origin. Every part that moves in the game is its own object with its
origin on its pivot, named as the game knows it: the four wheels, the steering wheel, each blade of the wings, the
propeller, the screw and the floats. Empties mark the seats and the exhaust.

The film car: a polished aluminium bonnet behind a brass radiator, a planked cedar boat tail, deep-buttoned maroon
leather, brass lamps and fittings, a great copper exhaust out of the side of the bonnet, red artillery wheels, GEN 11
on the plates. To fly it spreads red and yellow wings, a propeller comes out on the grille and the wheels turn flat;
on the water it blows up a float down each side and drives a screw under its tail.
"""
import math
import os
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
import numpy as np
from mathutils import Matrix, Vector
from PIL import Image

# --- layout (blocks) ---------------------------------------------------------------------------

WHEEL_R = 0.46
TRACK = 0.78          # wheel centres from the middle
FRONT_AXLE = 1.75
REAR_AXLE = -0.95
WING_BLADES = 7
WING_LENGTH = 1.9
CANARD_BLADES = 3
TAIL_BLADES = 3

# The body's sections (superellipses): half-width, centre height, half-height, squareness.
TUB = (0.70, 0.87, 0.42, 4.0)
TUB_CUT = 1.24        # the top of the cockpit sides
BONNET = (0.47, 1.00, 0.37, 5.0)
RADIATOR = (0.50, 1.00, 0.47, 6.0)
TAIL = [  # y, half-width, centre, half-height, squareness: a long tail narrowing to an upright edge
    (-1.45, 0.70, 0.87, 0.42, 4.0),
    (-1.80, 0.68, 0.87, 0.41, 4.0),
    (-2.15, 0.60, 0.87, 0.38, 3.6),
    (-2.45, 0.47, 0.87, 0.34, 3.2),
    (-2.66, 0.31, 0.87, 0.29, 2.8),
    (-2.80, 0.15, 0.87, 0.23, 2.4),
    (-2.87, 0.015, 0.87, 0.16, 2.0),
]
TAIL_END = TAIL[-1][0]


def srgb(hex_or_rgb):
    """sRGB colour (hex string or 0-255 triple) to Blender's linear RGBA."""
    if isinstance(hex_or_rgb, str):
        h = hex_or_rgb.lstrip('#')
        c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    else:
        c = [v / 255 for v in hex_or_rgb]
    lin = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in c]
    return (*lin, 1.0)


# --- materials ---------------------------------------------------------------------------------

MATS = {}
COLORS = {}


def material(name, color, metal=0.0, rough=0.5, image=None, emit=None, glass=False, coat=0.0, bump=None, ior=1.45,
             spec=0.5):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = srgb(color)
    b.inputs['Metallic'].default_value = metal
    b.inputs['Roughness'].default_value = rough
    b.inputs['Specular IOR Level'].default_value = spec
    if coat:
        b.inputs['Coat Weight'].default_value = coat
        b.inputs['Coat Roughness'].default_value = 0.08
    if image is not None:
        tex = nt.nodes.new('ShaderNodeTexImage')
        tex.image = image
        tex.interpolation = 'Closest' if image.size[0] <= 256 else 'Linear'
        nt.links.new(tex.outputs['Color'], b.inputs['Base Color'])
    if emit:
        b.inputs['Emission Color'].default_value = srgb(emit[0])
        b.inputs['Emission Strength'].default_value = emit[1]
    if glass:
        b.inputs['Transmission Weight'].default_value = 1.0
        b.inputs['IOR'].default_value = ior
    if bump is not None:
        kind, scale, strength = bump
        tc = nt.nodes.new('ShaderNodeTexCoord')
        mp = nt.nodes.new('ShaderNodeMapping')
        mp.inputs['Scale'].default_value = scale
        nz = nt.nodes.new('ShaderNodeTexNoise')
        nz.inputs['Detail'].default_value = 6.0
        bp = nt.nodes.new('ShaderNodeBump')
        bp.inputs['Strength'].default_value = strength
        nt.links.new(tc.outputs['Object'], mp.inputs['Vector'])
        nt.links.new(mp.outputs['Vector'], nz.inputs['Vector'])
        nt.links.new(nz.outputs['Fac'], bp.inputs['Height'])
        nt.links.new(bp.outputs['Normal'], b.inputs['Normal'])
    MATS[name] = m
    COLORS[name] = color
    return m


def image(name, rgba):
    """A Blender image from an (h, w, 4) array in sRGB 0..1, top row first."""
    h, w = rgba.shape[:2]
    img = bpy.data.images.new(name, w, h, alpha=True)
    img.pixels.foreach_set(np.ascontiguousarray(rgba[::-1]).astype(np.float32).ravel())
    img.pack()
    return img


def plank_texture(planks=16, size=1024, seed=3):
    """Cedar planking: planks running down the image (along the car), varnished, with dark seams and a few brass
    rivets; u goes round the hull, v along it."""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:size, 0:size] / size
    p = xx * planks
    idx = np.floor(p).astype(int)
    f = p - idx
    base = np.array([124, 58, 28]) / 255
    tone = 0.85 + 0.3 * rng.random(planks + 1)
    # Grain: streaks along each plank, wavering a little.
    phase = rng.random(planks + 1) * 50
    grain = np.sin((f * 3 + np.sin(yy * 9 + phase[idx]) * 0.5) * math.pi * 2 + phase[idx]) * 0.5 + 0.5
    grain = grain ** 4 * 0.22 + rng.normal(0, 1, (1, size)).repeat(size, 0) * 0.025
    fine = rng.normal(0, 0.03, (size, size))
    col = base[None, None, :] * (tone[idx] * (1.0 - grain + fine))[..., None]
    # Butt joints, staggered plank to plank.
    joint = np.abs(((yy + (idx * 0.37) % 1.0) % 1.0) - 0.5) < 0.0018
    seam = (f < 0.045) | joint
    col[seam] *= 0.32
    # Rivets along the seams.
    rivet = (f < 0.06) & ((((yy * 40) % 1.0) - 0.5) ** 2 < 0.004)
    col[rivet] = np.array([0.85, 0.66, 0.30])
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
    return out


def tuft_texture(size=256, cells=6):
    """Deep-buttoned maroon leather: puffed diamonds, a button in each crossing."""
    yy, xx = np.mgrid[0:size, 0:size] / size * cells
    u = (xx + yy) % 1.0
    v = (xx - yy) % 1.0
    puff = np.sin(u * math.pi) * np.sin(v * math.pi)
    base = np.array([118, 24, 34]) / 255
    col = base[None, None, :] * (0.62 + 0.5 * puff)[..., None]
    d = np.minimum(np.hypot(u - 0.0, v - 0.0), np.minimum(np.hypot(u - 1, v), np.minimum(np.hypot(u, v - 1), np.hypot(u - 1, v - 1))))
    col[d < 0.07] = np.array([0.18, 0.04, 0.06])
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
    return out


def stripe_texture(size=512, bands=10):
    """The floats' rubberised canvas: yellow, with red bands round it."""
    yy, xx = np.mgrid[0:size, 0:size] / size
    band = ((yy * bands) % 1.0) < 0.28
    col = np.where(band[..., None], np.array([204, 40, 34]) / 255, np.array([246, 200, 50]) / 255)
    seam = np.abs(((yy * bands) % 1.0) - 0.64) < 0.012
    col[seam] *= 0.8
    out = np.ones((size, size, 4))
    out[..., :3] = col
    return out


def make_materials():
    material('aluminium', (205, 210, 216), metal=1.0, rough=0.3, bump=('noise', (2.0, 2.0, 160.0), 0.04))
    material('brass', (214, 168, 74), metal=1.0, rough=0.25)
    material('copper', (196, 112, 70), metal=1.0, rough=0.3)
    material('cedar', (110, 52, 26), rough=0.38, image=image('cedar', plank_texture()), coat=0.3)
    material('chrome', (225, 228, 232), metal=1.0, rough=0.08)
    material('walnut', (98, 56, 30), rough=0.3, coat=0.8)
    material('leather', (118, 24, 34), rough=0.55, image=image('tufted', tuft_texture()))
    material('leather_plain', (110, 22, 32), rough=0.5)
    material('black', (18, 18, 20), rough=0.25, coat=0.6)
    material('chassis', (24, 24, 26), rough=0.6)
    material('rubber', (26, 26, 28), rough=0.85)
    material('red', (176, 26, 28), rough=0.35, coat=0.5)
    # Doped canvas: matte, so it keeps its colour seen edge-on against the sky.
    material('wing_red', (172, 14, 12), rough=0.9, spec=0.08)
    material('wing_yellow', (234, 160, 6), rough=0.9, spec=0.08)
    material('glass', (220, 235, 240), rough=0.02, glass=True)
    material('lens', (250, 246, 230), rough=0.02, glass=True, ior=1.05)
    material('bulb_glow', (255, 236, 190), rough=0.3, emit=((255, 220, 160), 1.5))
    material('eye', (190, 20, 20), rough=0.2, emit=((255, 40, 30), 0.4))
    material('honeycomb', (40, 34, 28), metal=0.6, rough=0.5)
    material('plate', (16, 16, 18), rough=0.4)
    material('letters', (236, 236, 230), rough=0.3)
    material('dial', (236, 230, 210), rough=0.4)
    material('float', (246, 200, 50), rough=0.45, image=image('float_bands', stripe_texture()))
    material('prop', (176, 118, 66), rough=0.3, coat=0.8)
    material('bulb', (120, 26, 26), rough=0.6)


# --- geometry helpers --------------------------------------------------------------------------

COLLECTIONS = {}


def collection(name):
    if name not in COLLECTIONS:
        c = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(c)
        COLLECTIONS[name] = c
    return COLLECTIONS[name]


class Mesh:
    """A bmesh being built, with a material slot per material used and a UV layer."""

    def __init__(self):
        self.bm = bmesh.new()
        self.uv = self.bm.loops.layers.uv.new('UVMap')
        self.slots = []

    def slot(self, mat):
        if mat not in self.slots:
            self.slots.append(mat)
        return self.slots.index(mat)

    def face(self, verts, mat, uvs=None):
        f = self.bm.faces.new(verts)
        f.material_index = self.slot(mat)
        if uvs:
            for loop, uv in zip(f.loops, uvs):
                loop[self.uv].uv = uv
        return f

    def vert(self, p):
        return self.bm.verts.new(p)

    def obj(self, name, coll='body', location=(0, 0, 0), rotation=(0, 0, 0), smooth=None, part=None):
        me = bpy.data.meshes.new(name)
        bmesh.ops.remove_doubles(self.bm, verts=self.bm.verts, dist=1e-6)
        bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        self.bm.normal_update()
        self.bm.to_mesh(me)
        self.bm.free()
        for mat in self.slots:
            me.materials.append(MATS[mat])
        if smooth is not None:
            me.shade_smooth()
            me.set_sharp_from_angle(angle=math.radians(smooth))
        o = bpy.data.objects.new(name, me)
        o.location = location
        o.rotation_euler = rotation
        collection(coll).objects.link(o)
        o['part'] = part or 'body'
        return o


def box_uv(m, faces, scale=1.0):
    """Projects each face onto the plane its normal points most along."""
    for f in faces:
        n = f.normal
        ax = max(range(3), key=lambda i: abs(n[i]))
        a, b = [(1, 2), (0, 2), (0, 1)][ax]
        for loop in f.loops:
            co = loop.vert.co
            loop[m.uv].uv = (co[a] * scale, co[b] * scale)


def add_box(m, center, size, mat, matrix=None, rot=None, uv_scale=1.0):
    """A box; `rot` turns it about its own centre, then `matrix` moves the result."""
    c = Vector(center)
    sx, sy, sz = (s / 2 for s in size)
    M = matrix or Matrix.Identity(4)
    R = rot.to_3x3() if rot is not None else Matrix.Identity(3)
    vs = [m.vert(M @ (c + R @ Vector((dx * sx, dy * sy, dz * sz))))
          for dx, dy, dz in [(-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                             (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]]
    faces = [m.face([vs[i] for i in q], mat) for q in
             [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]]
    for f in faces:
        f.normal_update()
    box_uv(m, faces, uv_scale)
    return faces


def superellipse(w, zc, h, n, count=48):
    """A closed section in (x, z), starting at the top and going round towards +x."""
    pts = []
    for i in range(count):
        t = math.pi / 2 - 2 * math.pi * i / count
        c, s = math.cos(t), math.sin(t)
        pts.append((w * math.copysign(abs(c) ** (2 / n), c), zc + h * math.copysign(abs(s) ** (2 / n), s)))
    return pts


def arc_param(zc, h, n, z):
    """The angle on the right side of a section where it passes height z."""
    k = max(-1.0, min(1.0, (z - zc) / h))
    return math.asin(math.copysign(abs(k) ** (n / 2), k))


def superellipse_arc(w, zc, h, n, t0, t1, count=64):
    """An open run of a section from angle t0 to t1 (0 is the right side, pi/2 the top)."""
    pts = []
    for i in range(count + 1):
        t = t0 + (t1 - t0) * i / count
        c, sn = math.cos(t), math.sin(t)
        pts.append((w * math.copysign(abs(c) ** (2 / n), c), zc + h * math.copysign(abs(sn) ** (2 / n), sn)))
    return pts


def perimeter_uv(pts, closed=True):
    d = [0.0]
    for i in range(1, len(pts) + (1 if closed else 0)):
        a, b = pts[i - 1], pts[i % len(pts)]
        d.append(d[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    return [x / d[-1] for x in d]


def loft(m, sections, mat, closed=True, cap_start=None, cap_end=None, v_scale=1.0):
    """Joins sections [(y, [(x, z), ...]), ...] (same point count) into a skin. u runs round each section, v along
    the car. Caps are filled with the given materials."""
    rings = []
    for y, pts in sections:
        rings.append([m.vert((x, y, z)) for x, z in pts])
    n = len(sections[0][1])
    us = perimeter_uv(sections[0][1], closed)
    y0 = sections[0][0]
    for k in range(len(rings) - 1):
        a, b = rings[k], rings[k + 1]
        va = (sections[k][0] - y0) * v_scale
        vb = (sections[k + 1][0] - y0) * v_scale
        for i in range(n if closed else n - 1):
            j = (i + 1) % n
            uj = us[i + 1]
            m.face([a[i], a[j], b[j], b[i]], mat, [(us[i], va), (uj, va), (uj, vb), (us[i], vb)])
    if cap_start:
        f = m.face(list(reversed(rings[0])) if sections[0][0] < sections[-1][0] else rings[0], cap_start)
        box_uv(m, [f])
    if cap_end:
        f = m.face(rings[-1] if sections[0][0] < sections[-1][0] else list(reversed(rings[-1])), cap_end)
        box_uv(m, [f])
    return rings


def lathe(m, profile, mat_of, axis='y', seg=32, origin=(0, 0, 0), a0=0.0):
    """A surface of revolution: profile [(r, a), ...] (radius, distance along the axis) round the given axis."""
    ox, oy, oz = origin
    rings = []
    for r, a in profile:
        ring = []
        for i in range(seg if r > 1e-9 else 1):
            t = a0 + 2 * math.pi * i / seg
            c, s = math.cos(t) * r, math.sin(t) * r
            if axis == 'y':
                p = (ox + c, oy + a, oz + s)
            elif axis == 'x':
                p = (ox + a, oy + c, oz + s)
            else:
                p = (ox + c, oy + s, oz + a)
            ring.append(m.vert(p))
        rings.append(ring)
    run = [0.0]
    for k in range(1, len(profile)):
        run.append(run[-1] + math.hypot(profile[k][0] - profile[k - 1][0], profile[k][1] - profile[k - 1][1]))
    run = [x / max(run[-1], 1e-9) for x in run]
    for k in range(len(rings) - 1):
        A, B = rings[k], rings[k + 1]
        mat = mat_of(k)
        for i in range(seg):
            j = (i + 1) % seg
            v0, v1 = run[k], run[k + 1]
            uv = [(i / seg, v0), ((i + 1) / seg, v0), ((i + 1) / seg, v1), (i / seg, v1)]
            if len(A) == 1:
                m.face([A[0], B[j], B[i]], mat, [uv[0], uv[2], uv[3]])
            elif len(B) == 1:
                m.face([A[i], A[j], B[0]], mat, uv[:3])
            else:
                m.face([A[i], A[j], B[j], B[i]], mat, uv)
    return rings


def catmull(points, per=8):
    """A smooth path through the points."""
    P = [Vector(p) for p in points]
    P = [P[0] * 2 - P[1]] + P + [P[-1] * 2 - P[-2]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for k in range(per):
            t = k / per
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t +
                              (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t))
    out.append(P[-2])
    return out


def tube(m, path, radius, mat, seg=12, flare=None):
    """A round pipe along a path; flare(t) scales the radius along it."""
    rings = []
    n = len(path)
    for i, p in enumerate(path):
        t = (path[min(i + 1, n - 1)] - path[max(i - 1, 0)]).normalized()
        ref = Vector((0, 0, 1)) if abs(t.z) < 0.9 else Vector((1, 0, 0))
        a = t.cross(ref).normalized()
        b = t.cross(a).normalized()
        r = radius * (flare(i / (n - 1)) if flare else 1.0)
        rings.append([m.vert(p + (a * math.cos(2 * math.pi * k / seg) + b * math.sin(2 * math.pi * k / seg)) * r)
                      for k in range(seg)])
    for i in range(n - 1):
        for k in range(seg):
            j = (k + 1) % seg
            m.face([rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]], mat,
                   [(k / seg, i / n), ((k + 1) / seg, i / n), ((k + 1) / seg, (i + 1) / n), (k / seg, (i + 1) / n)])
    m.face(list(reversed(rings[0])), mat)
    m.face(rings[-1], mat)


def text_object(text, size, location, rotation, mat, coll='body'):
    cu = bpy.data.curves.new('plate_text', 'FONT')
    cu.body = text
    cu.size = size
    cu.extrude = 0.003
    cu.align_x = 'CENTER'
    cu.align_y = 'CENTER'
    o = bpy.data.objects.new('plate_text', cu)
    collection(coll).objects.link(o)
    me = bpy.data.meshes.new_from_object(o.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    bpy.data.objects.remove(o)
    me.materials.clear()
    me.materials.append(MATS[mat])
    t = bpy.data.objects.new('plate_text', me)
    t.location = location
    t.rotation_euler = rotation
    collection(coll).objects.link(t)
    t['part'] = 'body'
    return t


def bevel(o, width, segments=3, limit=30.0):
    mod = o.modifiers.new('bevel', 'BEVEL')
    mod.width = width
    mod.segments = segments
    mod.limit_method = 'ANGLE'
    mod.angle_limit = math.radians(limit)
    mod.harden_normals = False
    return o


def solidify(o, thickness, offset=-1.0):
    mod = o.modifiers.new('solidify', 'SOLIDIFY')
    mod.thickness = thickness
    mod.offset = offset
    return o


def empty(name, location, coll='markers'):
    e = bpy.data.objects.new(name, None)
    e.location = location
    e.empty_display_size = 0.1
    collection(coll).objects.link(e)
    e['part'] = 'marker'
    return e


# --- the car -------------------------------------------------------------------------------------

def build_chassis():
    m = Mesh()
    for s in (-1, 1):
        add_box(m, (s * 0.48, 0.38, 0.44), (0.07, 4.0, 0.12), 'chassis')
    add_box(m, (0, 2.30, 0.44), (1.02, 0.07, 0.10), 'chassis')
    # Dumb irons up to the front springs, the axles, the differential.
    add_box(m, (0, FRONT_AXLE, 0.43), (1.42, 0.07, 0.07), 'chassis')
    add_box(m, (0, REAR_AXLE, 0.46), (1.46, 0.08, 0.08), 'chassis')
    add_box(m, (0, REAR_AXLE, 0.40), (0.24, 0.22, 0.22), 'chassis')
    add_box(m, (0, 0.7, 0.38), (0.10, 2.9, 0.08), 'chassis')  # the propshaft
    # The floor inside the tub.
    add_box(m, (0, -0.45, 0.485), (1.40, 1.94, 0.03), 'walnut')
    return m.obj('chassis', smooth=30)


def build_radiator():
    m = Mesh()
    sec = superellipse(*RADIATOR, count=64)
    loft(m, [(2.27, sec), (2.45, sec)], 'brass', cap_start='brass', cap_end='brass')
    o = m.obj('radiator', smooth=40)
    bevel(o, 0.012, 2)
    # The core: vertical slats behind the opening of the shell.
    m = Mesh()
    inner = superellipse(0.41, 0.98, 0.39, 6.0, count=64)
    v = [m.vert((x, 2.456, z)) for x, z in inner]
    f = m.face(list(reversed(v)), 'honeycomb')
    box_uv(m, [f])
    for k in range(-15, 16):
        x = k * 0.026
        half = 0.39 * (1 - (abs(x) / 0.41) ** 6) ** (1 / 6) if abs(x) < 0.41 else 0
        if half > 0.02:
            add_box(m, (x, 2.462, 0.98), (0.008, 0.012, 2 * half - 0.01), 'brass')
    m.obj('grille', smooth=None)
    # Filler cap and a winged mascot.
    m = Mesh()
    lathe(m, [(0.0, 0.0), (0.045, 0.0), (0.05, 0.02), (0.045, 0.05), (0.02, 0.06), (0.0, 0.065)], lambda k: 'brass',
          axis='z', seg=24, origin=(0, 2.36, 1.465))
    lathe(m, [(0.0, 0.0), (0.012, 0.0), (0.012, 0.06), (0.025, 0.08), (0.0, 0.11)], lambda k: 'brass',
          axis='z', seg=12, origin=(0, 2.36, 1.525))
    for s in (-1, 1):
        add_box(m, (s * 0.045, 2.35, 1.60), (0.07, 0.025, 0.012), 'brass', rot=Matrix.Rotation(-s * 0.35, 4, 'Y'))
    m.obj('mascot', smooth=40)


def build_bonnet():
    """The bonnet and the scuttle behind it as one aluminium skin; its back is the dashboard."""
    m = Mesh()
    b = superellipse(*BONNET, count=64)
    mid = superellipse(0.62, 0.93, 0.40, 4.5, count=64)
    tub = superellipse(*TUB, count=64)
    loft(m, [(2.27, b), (1.20, b), (0.80, b), (0.68, mid), (0.55, tub)], 'aluminium', cap_start='aluminium',
         cap_end='walnut')
    o = m.obj('bonnet', smooth=50)
    # The dashboard: walnut, with a row of brass-rimmed dials as out of an old aeroplane.
    m = Mesh()
    add_box(m, (0, 0.545, 1.06), (1.30, 0.02, 0.30), 'walnut')
    for x, r in ((-0.50, 0.045), (-0.32, 0.038), (-0.14, 0.05), (0.10, 0.038), (0.56, 0.045)):
        # The face looks back at the driver from inside a brass bezel.
        lathe(m, [(0.0, 0.0), (r + 0.008, 0.0), (r + 0.008, -0.010), (r, -0.012), (0.0, -0.008)],
              lambda k: 'dial' if k == 3 else 'brass', axis='y', seg=20, origin=(x, 0.535, 1.08))
    m.obj('dashboard', smooth=40)
    # Louvres down both sides, the hinge along the top, two leather straps with brass buckles.
    m = Mesh()
    for s in (-1, 1):
        for row, z in enumerate((0.78, 0.90, 1.02)):
            for k in range(12):
                y = 1.06 + k * 0.095
                add_box(m, (s * 0.472, y, z), (0.018, 0.065, 0.018), 'aluminium', rot=Matrix.Rotation(s * 0.5, 4, 'Y'))
    add_box(m, (0, 1.54, 1.372), (0.025, 1.46, 0.012), 'aluminium')
    w, zc, h, n = BONNET
    a = arc_param(zc, h, n, 0.70)
    top = superellipse_arc(w + 0.006, zc, h + 0.006, n, a, math.pi - a, count=40)
    for y in (1.20, 1.95):
        loft(m, [(y - 0.03, top), (y + 0.03, top)], 'leather_plain', closed=False)
        add_box(m, (0.16, y, 1.36), (0.05, 0.07, 0.03), 'brass')
    m.obj('bonnet_trim', smooth=40)
    return o


def build_tub():
    """The cockpit: planked cedar sides open at the top, a leather roll round the opening."""
    # The open section: below the cut, from the top of the right side down, along the bottom and up the left.
    w, zc, h, n = TUB
    a = arc_param(zc, h, n, TUB_CUT)
    open_sec = superellipse_arc(w, zc, h, n, a, -math.pi - a, count=72)
    m = Mesh()
    loft(m, [(-1.45, open_sec), (0.55, open_sec)], 'cedar', closed=False, v_scale=0.3)
    o = m.obj('tub', smooth=50)
    solidify(o, 0.04, offset=0.0)
    # The roll along the top of the sides and across behind the back seat.
    m = Mesh()
    xr = open_sec[0][0]
    for s in (-1, 1):
        tube(m, catmull([(s * xr, 0.56, TUB_CUT + 0.01), (s * xr, -0.4, TUB_CUT + 0.01), (s * xr, -1.40, TUB_CUT + 0.01)], 3),
             0.035, 'leather_plain', seg=10)
    m.obj('roll', smooth=50)
    return o


def build_tail():
    m = Mesh()
    sections = [(y, superellipse(w, zc, h, n, count=64)) for y, w, zc, h, n in TAIL]
    loft(m, sections, 'cedar', cap_start='cedar', cap_end='cedar', v_scale=0.3)
    o = m.obj('tail', smooth=50)
    # A brass strip down the spine of the deck.
    m = Mesh()
    path = [Vector((0, y, zc + h + 0.004)) for y, w, zc, h, n in TAIL[:-1]]
    tube(m, catmull([tuple(p) for p in path], 4), 0.012, 'brass', seg=8)
    m.obj('tail_strip', smooth=50)
    return o


def build_seats():
    def seat(name, center, size, tilt=0.0):
        m = Mesh()
        add_box(m, (0, 0, 0), size, 'leather', uv_scale=2.0)
        o = m.obj(name, location=center, rotation=(math.radians(tilt), 0, 0))
        bevel(o, 0.045, 4)
        o.modifiers.new('smooth', 'WEIGHTED_NORMAL')
        return o

    # Two deep-buttoned benches, the back one under the curve of the tail.
    seat('seat_front', (0, 0.20, 0.66), (1.22, 0.42, 0.16))
    seat('seatback_front', (0, -0.05, 1.00), (1.22, 0.10, 0.54), tilt=12)
    seat('seat_rear', (0, -0.95, 0.66), (1.22, 0.42, 0.16))
    seat('seatback_rear', (0, -1.27, 1.00), (1.24, 0.10, 0.56), tilt=14)
    for name, p in (('seat_driver', (0.34, 0.20, 0.74)), ('seat_front_passenger', (-0.34, 0.20, 0.74)),
                    ('seat_rear_right', (0.32, -0.95, 0.74)), ('seat_rear_left', (-0.32, -0.95, 0.74))):
        empty(name, p)


def build_windscreen():
    m = Mesh()
    r = 0.014
    for x in (-0.63, 0.0, 0.63):
        tube(m, [Vector((x, 0.64, 1.27)), Vector((x, 0.64, 1.82))], r, 'brass', seg=10)
    tube(m, [Vector((-0.63, 0.64, 1.82)), Vector((0.63, 0.64, 1.82))], r, 'brass', seg=10)
    tube(m, [Vector((-0.63, 0.64, 1.31)), Vector((0.63, 0.64, 1.31))], r * 0.8, 'brass', seg=10)
    m.obj('windscreen', smooth=40)
    m = Mesh()
    for s in (-1, 1):
        add_box(m, (s * 0.315, 0.64, 1.565), (0.60, 0.006, 0.49), 'glass')
    m.obj('glass', part='glass')


def build_steering():
    m = Mesh()
    # The rim round its own axis (local Z), three brass spokes, the boss and the column down into the dash.
    prof = [(0.18 + 0.016 * math.cos(a), 0.016 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 9)[:-1]]
    rings = []
    seg = 36
    for i in range(seg):
        t = 2 * math.pi * i / seg
        rings.append([m.vert((r * math.cos(t), r * math.sin(t), z)) for r, z in prof])
    for i in range(seg):
        j = (i + 1) % seg
        for k in range(len(prof)):
            l = (k + 1) % len(prof)
            m.face([rings[i][k], rings[j][k], rings[j][l], rings[i][l]], 'walnut')
    for k in range(3):
        a = math.pi / 2 + 2 * math.pi * k / 3
        add_box(m, (0, 0.09, -0.005), (0.018, 0.18, 0.008), 'brass', matrix=Matrix.Rotation(a - math.pi / 2, 4, 'Z'))
    lathe(m, [(0.0, 0.01), (0.035, 0.01), (0.035, -0.02), (0.02, -0.03)], lambda k: 'brass', axis='z', seg=16)
    tube(m, [Vector((0, 0, -0.02)), Vector((0, 0, -0.32))], 0.016, 'chassis', seg=10)
    center = Vector((0.34, 0.30, 1.12))
    dash = Vector((0.34, 0.55, 0.95))
    axis = (center - dash).normalized()
    tilt = math.atan2(-axis.y, axis.z)
    return m.obj('steering_wheel', location=center, rotation=(tilt, 0, 0), smooth=40, part='steering')


def build_spokes(m, mat):
    for k in range(12):
        a = 2 * math.pi * k / 12
        R = Matrix.Rotation(a, 4, 'X')
        add_box(m, (0, 0, 0.195), (0.03, 0.038, 0.24), mat, matrix=R)


def build_wheel(name, side, y):
    m = Mesh()
    # Tyre: a fat ring round the X axis.
    seg = 48
    prof = []
    for k in range(12):
        phi = 2 * math.pi * k / 12
        c, s = math.cos(phi), math.sin(phi)
        prof.append((0.40 + 0.06 * math.copysign(abs(c) ** 0.7, c), 0.058 * math.copysign(abs(s) ** 0.7, s)))
    rings = []
    for i in range(seg):
        t = 2 * math.pi * i / seg
        rings.append([m.vert((x, r * math.cos(t), r * math.sin(t))) for r, x in prof])
    for i in range(seg):
        j = (i + 1) % seg
        for k in range(len(prof)):
            l = (k + 1) % len(prof)
            m.face([rings[i][k], rings[i][l], rings[j][l], rings[j][k]], 'rubber',
                   [(i / seg, k / 12), (i / seg, (k + 1) / 12), ((i + 1) / seg, (k + 1) / 12), ((i + 1) / seg, k / 12)])
    # Felloe, spokes and hub.
    lathe(m, [(0.30, -0.035), (0.345, -0.035), (0.345, 0.035), (0.30, 0.035), (0.30, -0.035)], lambda k: 'red',
          axis='x', seg=seg)
    build_spokes(m, 'red')
    outer = 1 if side > 0 else -1
    lathe(m, [(0.0, -0.07 * outer), (0.085, -0.07 * outer), (0.085, 0.06 * outer), (0.07, 0.08 * outer),
              (0.045, 0.10 * outer), (0.0, 0.11 * outer)], lambda k: 'brass', axis='x', seg=24)
    return m.obj(name, coll='wheels', location=(side * TRACK, y, WHEEL_R), smooth=45, part=name)


def build_wheels():
    return [build_wheel('wheel_fr', 1, FRONT_AXLE), build_wheel('wheel_fl', -1, FRONT_AXLE),
            build_wheel('wheel_rr', 1, REAR_AXLE), build_wheel('wheel_rl', -1, REAR_AXLE)]


def guard_path(center, r, a0, a1, lead=(), tail=(), steps=14):
    yc, zc = center
    pts = list(lead)
    for k in range(steps + 1):
        a = math.radians(a0 + (a1 - a0) * k / steps)
        pts.append((yc + r * math.cos(a), zc + r * math.sin(a)))
    return pts + list(tail)


def build_guards():
    """Black wings over the wheels: the front ones sweep down and back into the running boards."""
    m = Mesh()
    for s in (-1, 1):
        x = s * TRACK
        front = guard_path((FRONT_AXLE, WHEEL_R), 0.57, 12, 162, tail=[(1.05, 0.64), (0.90, 0.60), (0.78, 0.57), (0.74, 0.56)])
        rear = guard_path((REAR_AXLE, WHEEL_R), 0.56, 22, 172, lead=[(-0.34, 0.56), (-0.40, 0.59)])
        for path, hub in ((front, FRONT_AXLE), (rear, REAR_AXLE)):
            path = [Vector((0, y, z)) for y, z in path]
            cols = 7
            grid = []
            for i, p in enumerate(path):
                t = (path[min(i + 1, len(path) - 1)] - path[max(i - 1, 0)]).normalized()
                nrm = Vector((0, t.z, -t.y))
                # The crown bulges away from the wheel.
                if nrm.dot(p - Vector((0, hub, WHEEL_R))) < 0:
                    nrm = -nrm
                row = []
                for c in range(cols):
                    u = c / (cols - 1) * 2 - 1
                    crown = 0.035 * (1 - u * u)
                    row.append(m.vert((x + u * 0.17, p.y + nrm.y * crown, p.z + nrm.z * crown)))
                grid.append(row)
            for i in range(len(grid) - 1):
                for c in range(cols - 1):
                    m.face([grid[i][c], grid[i][c + 1], grid[i + 1][c + 1], grid[i + 1][c]], 'black',
                           [(c / cols, i / len(grid)), ((c + 1) / cols, i / len(grid)),
                            ((c + 1) / cols, (i + 1) / len(grid)), (c / cols, (i + 1) / len(grid))])
        # The running board: ribbed rubber edged with brass.
        add_box(m, (s * 0.79, 0.20, 0.54), (0.28, 1.14, 0.04), 'rubber')
        add_box(m, (s * 0.935, 0.20, 0.54), (0.012, 1.14, 0.045), 'brass')
    o = m.obj('guards', smooth=50)
    solidify(o, 0.014, offset=0.0)
    return o


def ellipsoid(m, center, radii, mat, rot=None, seg=16, rings=10):
    """A squashed sphere, turned by `rot` about its centre."""
    c = Vector(center)
    R = rot.to_3x3() if rot is not None else Matrix.Identity(3)
    rows = []
    for i in range(1, rings):
        phi = math.pi * i / rings
        rows.append([m.vert(c + R @ Vector((radii[0] * math.sin(phi) * math.cos(2 * math.pi * k / seg),
                                            radii[1] * math.cos(phi),
                                            radii[2] * math.sin(phi) * math.sin(2 * math.pi * k / seg))))
                     for k in range(seg)])
    top = m.vert(c + R @ Vector((0, radii[1], 0)))
    bottom = m.vert(c + R @ Vector((0, -radii[1], 0)))
    for k in range(seg):
        j = (k + 1) % seg
        m.face([top, rows[0][j], rows[0][k]], mat)
        m.face([bottom, rows[-1][k], rows[-1][j]], mat)
        for i in range(len(rows) - 1):
            m.face([rows[i][k], rows[i][j], rows[i + 1][j], rows[i + 1][k]], mat)


def lamp(m, glass, center, r, depth):
    """A brass drum lamp facing forward: the body, a chrome reflector, a bulb and a domed glass."""
    body = [(0.0, -depth), (r * 0.7, -depth), (r * 0.94, -depth * 0.7), (r, -depth * 0.15), (r, depth * 0.62),
            (r * 1.11, depth * 0.7), (r * 1.11, depth * 0.9), (r * 0.94, depth * 0.92)]
    lathe(m, body, lambda k: 'brass', axis='y', seg=32, origin=center)
    lathe(m, [(0.0, -depth * 0.45), (r * 0.4, -depth * 0.36), (r * 0.78, -depth * 0.05), (r * 0.92, depth * 0.55),
              (r * 0.93, depth * 0.8)], lambda k: 'chrome', axis='y', seg=32, origin=center)
    ellipsoid(m, (center[0], center[1] - depth * 0.15, center[2]), (r * 0.16, r * 0.2, r * 0.16), 'bulb_glow', seg=10, rings=6)
    lathe(glass, [(r * 0.94, depth * 0.92), (r * 0.75, depth * 1.0), (r * 0.4, depth * 1.08), (0.0, depth * 1.1)],
          lambda k: 'lens', axis='y', seg=32, origin=center)


def build_lamps():
    m = Mesh()
    glass = Mesh()
    # The lamp bar between the front wings, two great drum headlamps on it, and a coach lamp each side of the screen.
    tube(m, [Vector((-0.82, 2.33, 1.00)), Vector((0.82, 2.33, 1.00))], 0.022, 'brass', seg=10)
    for s in (-1, 1):
        cx = s * 0.71
        tube(m, [Vector((cx, 2.33, 1.00)), Vector((cx, 2.33, 1.06))], 0.02, 'brass', seg=8)
        lamp(m, glass, (cx, 2.35, 1.20), 0.16, 0.13)
        cl = s * 0.765
        lamp(m, glass, (cl, 0.60, 1.24), 0.05, 0.06)
        lathe(m, [(0.0, 0.0), (0.012, 0.0), (0.012, 0.08), (0.025, 0.09), (0.0, 0.11)], lambda k: 'brass', axis='z',
              seg=12, origin=(cl, 0.60, 1.29))
        add_box(m, (s * 0.725, 0.60, 1.14), (0.05, 0.03, 0.14), 'brass')
    m.obj('lamps', smooth=40)
    glass.obj('lamp_glass', smooth=40, part='glass')


def build_exhaust():
    """Four pipes out of the right of the bonnet into one great flexible pipe running back to the rear wing."""
    m = Mesh()
    for k, y in enumerate((2.06, 1.84, 1.62, 1.40)):
        tube(m, catmull([(0.45, y, 1.16), (0.52, y - 0.02, 1.15), (0.56, y - 0.06, 1.06), (0.565, y - 0.10, 0.99)], 6),
             0.026, 'copper', seg=12)
        # A brass collar where each pipe leaves the bonnet.
        lathe(m, [(0.034, -0.012), (0.034, 0.012)], lambda k: 'brass', axis='x', seg=12, origin=(0.47, y, 1.16))
    main = catmull([(0.565, 2.15, 0.98), (0.565, 1.50, 0.97), (0.57, 1.00, 0.95), (0.64, 0.76, 0.80),
                    (0.79, 0.54, 0.63), (0.83, 0.30, 0.61), (0.83, -0.16, 0.61)], 8)
    tube(m, main, 0.048, 'copper', seg=16)
    # Ribs round the flexible pipe.
    for i in range(3, len(main) - 2, 3):
        t = (main[i + 1] - main[i - 1]).normalized()
        tube(m, [main[i] - t * 0.007, main[i] + t * 0.007], 0.054, 'copper', seg=16)
    tip = catmull([(0.83, -0.16, 0.61), (0.83, -0.23, 0.60), (0.84, -0.29, 0.585)], 4)
    tube(m, tip, 0.048, 'brass', seg=16, flare=lambda t: 1.0 + 0.5 * t * t)
    empty('exhaust', (0.84, -0.31, 0.58))
    return m.obj('exhaust', smooth=50)


def build_horn():
    """The serpent horn: a brass snake from the rubber bulb by the driver's hand along the bonnet, rearing up at the
    front with its jaws open."""
    m = Mesh()
    path = catmull([(0.66, 0.54, 1.33), (0.63, 0.78, 1.36), (0.57, 1.02, 1.40), (0.55, 1.20, 1.46),
                    (0.56, 1.32, 1.56), (0.58, 1.40, 1.62), (0.585, 1.50, 1.63)], 8)
    tube(m, path, 0.014, 'brass', seg=12, flare=lambda t: 1.0 + 1.1 * t ** 1.5)
    lathe(m, [(0.0, -0.05), (0.03, -0.045), (0.045, -0.02), (0.045, 0.02), (0.02, 0.04), (0.012, 0.05)],
          lambda k: 'bulb', axis='y', seg=20, origin=(0.66, 0.49, 1.33))
    head = Vector((0.585, 1.56, 1.63))
    ellipsoid(m, head + Vector((0, 0.0, 0.018)), (0.040, 0.075, 0.022), 'brass', rot=Matrix.Rotation(math.radians(22), 4, 'X'))
    ellipsoid(m, head + Vector((0, -0.005, -0.016)), (0.034, 0.065, 0.016), 'brass', rot=Matrix.Rotation(math.radians(-16), 4, 'X'))
    ellipsoid(m, head + Vector((0, 0.01, 0.0)), (0.026, 0.05, 0.02), 'bulb')
    for s in (-1, 1):
        ellipsoid(m, head + Vector((s * 0.03, -0.02, 0.04)), (0.010, 0.010, 0.010), 'eye', seg=8, rings=6)
    # Brackets holding it to the bonnet.
    add_box(m, (0.51, 1.02, 1.36), (0.10, 0.025, 0.025), 'brass')
    add_box(m, (0.51, 1.22, 1.40), (0.08, 0.025, 0.025), 'brass')
    return m.obj('horn', smooth=45)


def build_levers():
    """The handbrake and gear lever outside the body by the driver's right hand, in a brass quadrant."""
    m = Mesh()
    add_box(m, (0.725, 0.20, 0.70), (0.03, 0.34, 0.14), 'brass')
    for y0, y1, z1, knob in ((0.16, 0.08, 1.30, False), (0.26, 0.32, 1.20, True)):
        tube(m, [Vector((0.74, y0, 0.66)), Vector((0.755, y1, z1))], 0.012, 'brass', seg=8)
        if knob:
            ellipsoid(m, (0.755, y1, z1 + 0.02), (0.028, 0.028, 0.028), 'black', seg=12, rings=8)
        else:
            tube(m, [Vector((0.755, y1, z1 - 0.10)), Vector((0.755, y1, z1 + 0.01))], 0.019, 'leather_plain', seg=10)
    return m.obj('levers', smooth=40)


def build_plates():
    """GEN 11, front and back, and the starting handle under the radiator."""
    m = Mesh()
    add_box(m, (0, 2.52, 0.34), (0.52, 0.012, 0.13), 'plate')
    add_box(m, (0, 2.48, 0.34), (0.05, 0.07, 0.05), 'chassis')
    add_box(m, (0, -2.745, 0.535), (0.52, 0.012, 0.13), 'plate')
    add_box(m, (0, -2.73, 0.62), (0.04, 0.04, 0.08), 'chassis')
    tube(m, [Vector((0, 2.38, 0.50)), Vector((0, 2.57, 0.50))], 0.014, 'chassis', seg=8)
    tube(m, [Vector((0, 2.57, 0.50)), Vector((0, 2.57, 0.63))], 0.012, 'chassis', seg=8)
    tube(m, [Vector((0, 2.57, 0.63)), Vector((0, 2.66, 0.63))], 0.016, 'brass', seg=8)
    m.obj('plates', smooth=None)
    text_object('GEN 11', 0.085, (0, 2.527, 0.34), (math.radians(90), 0, math.radians(180)), 'letters')
    text_object('GEN 11', 0.085, (0, -2.752, 0.535), (math.radians(90), 0, 0), 'letters')


def build_rail():
    """A brass rubbing strip along the widest line of the hull, from the scuttle to the point of the tail."""
    m = Mesh()
    w, zc = TUB[0], TUB[1]
    for s in (-1, 1):
        pts = [(s * (w + 0.022), 0.55, zc), (s * (w + 0.022), -1.45, zc)]
        pts += [(s * (tw + 0.022), y, tzc) for y, tw, tzc, h, n in TAIL[1:-1]]
        pts += [(s * 0.02, TAIL_END - 0.01, TAIL[-1][2])]
        tube(m, catmull(pts, 6), 0.013, 'brass', seg=8)
    return m.obj('rail', smooth=50)


def wedge(m, length, half_angle, mat, rib='brass'):
    """One panel of a fan wing: a wedge out along +X from its hinge, its tip bowed out, a brass rib down the middle.
    Panels overlap their neighbours, so the open fan is one striped sail."""
    a = math.radians(half_angle)
    radial = [0.04 + (length - 0.04) * j / 7 for j in range(8)]
    cols = 7
    grid = []
    for j, r in enumerate(radial):
        row = []
        for k in range(cols):
            t = -1 + 2 * k / (cols - 1)
            ang = a * t
            rr = r * (1 - 0.07 * t * t * (j / 7) ** 2)
            row.append(m.vert((rr * math.cos(ang), rr * math.sin(ang), 0.0)))
        grid.append(row)
    for j in range(len(grid) - 1):
        for k in range(cols - 1):
            m.face([grid[j][k], grid[j + 1][k], grid[j + 1][k + 1], grid[j][k + 1]], mat,
                   [(j / 7, k / 6), ((j + 1) / 7, k / 6), ((j + 1) / 7, (k + 1) / 6), (j / 7, (k + 1) / 6)])
    tube(m, [Vector((0.02, 0.0, 0.005)), Vector((length * 0.97, 0.0, 0.005))], 0.008, rib, seg=6)


def fan(name, side, hinge, blades, length, spread, open_from, dihedral, colors, coll):
    """A fan of wedges on a hinge, opening from open_from degrees (forward is positive) back through spread;
    panel i of side s is named <name>_<s>_<i>, its origin on the hinge."""
    objs = []
    step = spread / max(1, blades - 1)
    for i in range(blades):
        m = Mesh()
        wedge(m, length, step / 2 + 2.0, colors[i % 2])
        tag = '%s_%s_%d' % (name, 'r' if side > 0 else 'l', i)
        o = m.obj(tag, coll=coll, location=(hinge[0], hinge[1], hinge[2] + 0.006 * i), smooth=None, part=tag)
        solidify(o, 0.008, offset=0.0)
        if side < 0:
            o.scale = (-1, 1, 1)
        o['open_yaw'] = open_from - step * i
        o['dihedral'] = dihedral
        objs.append(o)
    return objs


def build_wings():
    objs = []
    for s in (-1, 1):
        objs += fan('wing', s, (s * 0.80, 0.18, 0.47), WING_BLADES, WING_LENGTH, 125, 55, 7,
                    ('wing_red', 'wing_yellow'), 'wings')
        objs += fan('canard', s, (s * 0.62, 2.22, 0.56), CANARD_BLADES, 0.75, 45, 35, 4, ('wing_yellow', 'wing_red'), 'wings')
        objs += fan('tailwing', s, (s * 0.54, -2.25, 0.86), TAIL_BLADES, 0.85, 50, -30, 10, ('wing_yellow', 'wing_red'), 'wings')
    return objs


def build_propeller():
    """A two-bladed wooden propeller on a brass spinner, out on the front of the radiator; it turns about local Y."""
    m = Mesh()
    lathe(m, [(0.0, -0.04), (0.08, -0.04), (0.085, 0.02), (0.06, 0.09), (0.03, 0.13), (0.0, 0.15)],
          lambda k: 'brass', axis='y', seg=24)
    for sign in (1, -1):
        stations = np.linspace(0.06, 0.64, 10)
        rings = []
        for r in stations:
            t = (r - 0.06) / 0.58
            chord = 0.07 + 0.11 * math.sin(math.pi * min(1.0, t * 1.15)) if t < 0.95 else 0.05
            twist = math.radians(38 - 26 * t)
            thick = 0.028 * (1 - 0.6 * t)
            pts = [(-chord / 2, -thick / 2), (chord / 2, -thick / 2), (chord / 2, thick / 2), (-chord / 2, thick / 2)]
            ring = []
            for x, y in pts:
                xr = x * math.cos(twist) - y * math.sin(twist)
                yr = x * math.sin(twist) + y * math.cos(twist)
                ring.append(m.vert((xr * sign, yr + 0.03, r * sign)))
            rings.append(ring)
        for i in range(len(rings) - 1):
            for k in range(4):
                j = (k + 1) % 4
                a, b, c, d = rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]
                m.face([a, b, c, d] if sign > 0 else [d, c, b, a], 'prop',
                       [(k / 4, i / 9), ((k + 1) / 4, i / 9), ((k + 1) / 4, (i + 1) / 9), (k / 4, (i + 1) / 9)])
        m.face(rings[-1] if sign > 0 else list(reversed(rings[-1])), 'prop')
        # Brass tipping on the leading edge.
        tip = rings[-2]
        c = sum((v.co for v in tip), Vector()) / 4
        ellipsoid(m, (c.x, c.y, c.z + 0.02 * sign), (0.03, 0.012, 0.035), 'brass', seg=8, rings=5)
    return m.obj('propeller', coll='propeller', location=(0, 2.58, 1.00), smooth=35, part='propeller')


def build_screw():
    m = Mesh()
    tube(m, [Vector((0, 0.0, 0)), Vector((0, 0.36, 0.26))], 0.014, 'brass', seg=8)
    lathe(m, [(0.0, -0.06), (0.03, -0.05), (0.035, 0.0), (0.02, 0.03), (0.0, 0.035)], lambda k: 'brass', axis='y', seg=16)
    for k in range(3):
        a = 2 * math.pi * k / 3
        R = Matrix.Rotation(a, 4, 'Y') @ Matrix.Rotation(math.radians(28), 4, 'Z')
        add_box(m, (0.0, 0.0, 0.10), (0.07, 0.01, 0.13), 'brass', matrix=R)
    return m.obj('screw', coll='propeller', location=(0, -2.50, 0.36), smooth=40, part='screw')


def build_floats():
    """A great float down each side, blown up when the car takes to the water."""
    objs = []
    for s, tag in ((1, 'r'), (-1, 'l')):
        m = Mesh()
        prof = [(0.0, -1.80)]
        for k in range(1, 7):
            a = math.pi / 2 * (1 - k / 6)
            prof.append((0.26 * math.cos(a), -1.55 - 0.25 * math.sin(a)))
        prof += [(0.26, -0.5), (0.26, 0.5), (0.26, 1.40)]
        for k in range(1, 7):
            a = math.pi / 2 * k / 6
            prof.append((0.26 * math.cos(a), 1.40 + 0.28 * math.sin(a)))
        lathe(m, prof, lambda k: 'float', axis='y', seg=28, origin=(s * 0.42, 0.0, -0.02))
        for y in (-0.9, 0.9):
            tube(m, [Vector((s * -0.08, y, 0.12)), Vector((s * 0.30, y, 0.10))], 0.018, 'brass', seg=8)
        o = m.obj('float_' + tag, coll='floats', location=(s * 0.78, 0.0, 0.48), smooth=40, part='float_' + tag)
        objs.append(o)
    return objs


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    make_materials()
    build_chassis()
    build_radiator()
    build_bonnet()
    build_tub()
    build_tail()
    build_seats()
    build_windscreen()
    build_steering()
    build_wheels()
    build_guards()
    build_lamps()
    build_exhaust()
    build_horn()
    build_levers()
    build_plates()
    build_rail()
    build_wings()
    build_propeller()
    build_screw()
    build_floats()


# --- poses -----------------------------------------------------------------------------------------

def pose(mode, spin=0.0):
    """'road', 'flying' or 'water': what the game's animation does, for the renders and exports."""
    fly = mode == 'flying'
    wet = mode == 'water'
    for o in bpy.data.objects:
        part = o.get('part', '')
        if 'open_yaw' in o:
            side = 1 if o.location.x > 0 else -1
            open_yaw = math.radians(o['open_yaw'])
            fold = math.radians(-90)
            yaw = open_yaw if fly else fold
            o.rotation_euler = (0, -math.radians(o['dihedral']) * side if fly else 0, yaw * side)
            sc = 1.0 if fly else 0.15
            o.scale = (sc * side, sc, 1)
            o.hide_render = o.hide_viewport = not fly
        elif part.startswith('wheel_'):
            # Flat to fly, hub caps up.
            side = 1 if o.location.x > 0 else -1
            o.rotation_euler = (spin, -math.radians(90) * side if fly else 0, 0)
        elif part == 'propeller':
            o.hide_render = o.hide_viewport = not fly
            o.rotation_euler = (0, spin * 2.3, 0)
        elif part == 'screw':
            o.hide_render = o.hide_viewport = not wet
        elif part.startswith('float_'):
            o.hide_render = o.hide_viewport = not wet


# --- output ----------------------------------------------------------------------------------------

def render_scene_setup():
    """Cycles, a sky, the sun, grass, water and a camera, made once."""
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Punchy'
    scene.view_settings.exposure = -0.3
    if scene.world is None:
        world = bpy.data.worlds.new('sky')
        world.use_nodes = True
        nt = world.node_tree
        sky = nt.nodes.new('ShaderNodeTexSky')
        sky.sky_type = 'NISHITA'
        sky.sun_elevation = math.radians(38)
        sky.sun_rotation = math.radians(210)
        nt.links.new(sky.outputs['Color'], nt.nodes['Background'].inputs['Color'])
        nt.nodes['Background'].inputs['Strength'].default_value = 0.22
        scene.world = world
        sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
        sun.data.energy = 2.4
        sun.data.angle = math.radians(2.5)
        # Late morning light from ahead and to the right of the car.
        sun.rotation_euler = Vector((-0.45, -0.55, -0.70)).to_track_quat('-Z', 'Y').to_euler()
        scene.collection.objects.link(sun)
        ground = bpy.data.meshes.new('ground')
        bm = bmesh.new()
        bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=60)
        bm.to_mesh(ground)
        bm.free()
        g = bpy.data.objects.new('ground', ground)
        scene.collection.objects.link(g)
        grass = material('grass', (86, 128, 58), rough=1.0)
        ground.materials.append(grass)
        wmesh = bpy.data.meshes.new('water')
        bm = bmesh.new()
        bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=60)
        bm.to_mesh(wmesh)
        bm.free()
        wo = bpy.data.objects.new('water', wmesh)
        wo.location.z = 0.40
        scene.collection.objects.link(wo)
        wm = material('water', (40, 90, 140), rough=0.05)
        wm.node_tree.nodes['Principled BSDF'].inputs['Transmission Weight'].default_value = 0.6
        wmesh.materials.append(wm)
        cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
        scene.collection.objects.link(cam)
        scene.camera = cam


def render_scene(out, name, cam_loc, look_at, lens=40, lift=0.0, water=False, size=(1600, 900),
                 samples=int(os.environ.get('CHITTY_SAMPLES', '96'))):
    scene = bpy.context.scene
    render_scene_setup()
    scene.cycles.samples = samples
    scene.render.resolution_x, scene.render.resolution_y = size
    bpy.data.objects['water'].hide_render = not water
    bpy.data.objects['ground'].hide_render = water
    for o in bpy.data.objects:
        if o.get('part') is not None and o.get('part') != 'marker':
            o.delta_location = (0, 0, lift)
    cam = scene.camera
    cam.location = Vector(cam_loc)
    cam.rotation_euler = (Vector(look_at) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = lens
    scene.render.filepath = os.path.join(out, name + '.png')
    bpy.ops.render.render(write_still=True)
    for o in bpy.data.objects:
        o.delta_location = (0, 0, 0)


# --- the game's copy ---------------------------------------------------------------------------------

GAME_MESH = 'src/client/resources/assets/shootingstar/meshes/chitty.cbm'
GAME_TEXTURE = 'src/client/resources/assets/shootingstar/textures/entity/chitty.png'
ITEM_ICON = 'src/main/resources/assets/shootingstar/textures/item/chitty.png'

# The atlas: a white corner for the plain materials (their colour rides on the vertices) and a tile for each textured
# one, holding that material's texture over the whole range of its UVs.
ATLAS = 1024
FLAT = (0, 0, 16, 16)
TILES = {'cedar': (0, 512, 512, 512), 'leather': (512, 512, 512, 512), 'float': (512, 0, 256, 256)}
SOURCES = {'cedar': plank_texture, 'leather': tuft_texture, 'float': stripe_texture}
ALPHA = {'glass': 70, 'lens': 90}
# The parts the renderer moves; everything else is merged into 'body', and the see-through bits into 'glass'.
FREE_PARTS = ('steering', 'propeller', 'screw')


def to_mc(v):
    """Blender (x right, y forward, z up) to Minecraft at yaw 0 (x west = the car's left... negated, y up, z forward)."""
    return (-v[0], v[2], v[1])


def neutral():
    """Every part shown, unturned and full size: the renderer poses them."""
    for o in bpy.data.objects:
        part = o.get('part')
        if part is None:
            continue
        o.hide_render = o.hide_viewport = False
        if 'open_yaw' in o:
            side = 1 if o.location.x > 0 else -1
            o.rotation_euler = (0, 0, 0)
            o.scale = (side, 1, 1)
        elif part.startswith('wheel_') or part in ('propeller', 'screw'):
            o.rotation_euler = (0, 0, 0)
        elif part.startswith('float_'):
            o.scale = (1, 1, 1)


def part_of(o):
    part = o.get('part')
    if part in (None, 'marker'):
        return None
    if part in ('body', 'glass'):
        return part
    return o.name if part != 'steering' else 'steering_wheel'


def evaluated_quads(o, deps):
    """The object's evaluated mesh with every face of more than four corners cut into triangles."""
    me = bpy.data.meshes.new_from_object(o.evaluated_get(deps))
    bm = bmesh.new()
    bm.from_mesh(me)
    big = [f for f in bm.faces if len(f.verts) > 4]
    if big:
        bmesh.ops.triangulate(bm, faces=big)
    bm.to_mesh(me)
    bm.free()
    return me


def occlusion(meshes, samples=40, reach=0.32):
    """Per-corner ambient occlusion of each (object, mesh) against all of them, in world space."""
    from mathutils.bvhtree import BVHTree
    verts, polys = [], []
    for o, me in meshes:
        base = len(verts)
        mw = o.matrix_world
        verts += [mw @ v.co for v in me.vertices]
        polys += [[base + i for i in p.vertices] for p in me.polygons]
    bvh = BVHTree.FromPolygons(verts, polys)
    rnd = np.random.default_rng(11)
    dirs = []
    for _ in range(samples):
        u, v = rnd.random(), rnd.random()
        r, phi = math.sqrt(u), 2 * math.pi * v
        dirs.append(Vector((r * math.cos(phi), r * math.sin(phi), math.sqrt(max(0.0, 1 - u)))))
    out = {}
    for o, me in meshes:
        mw = o.matrix_world
        nm = mw.to_3x3().inverted().transposed()
        cache = {}
        values = []
        normals = me.corner_normals
        for loop in me.loops:
            n = (nm @ Vector(normals[loop.index].vector)).normalized()
            key = (loop.vertex_index, round(n.x, 2), round(n.y, 2), round(n.z, 2))
            if key not in cache:
                p = mw @ me.vertices[loop.vertex_index].co
                t = n.orthogonal().normalized()
                b = n.cross(t)
                hit = 0.0
                for d in dirs:
                    w = t * d.x + b * d.y + n * d.z
                    loc, _, _, dist = bvh.ray_cast(p + n * 0.004, w, reach)
                    if loc is not None:
                        hit += 1.0 - (dist / reach) ** 2
                cache[key] = max(0.35, 1.0 - hit / samples * 1.1)
            values.append(cache[key])
        out[o.name] = values
    return out


def export_game(root):
    """Writes the game's mesh (.cbm), its texture atlas and the item icon.

    .cbm (little endian): b"CBM1", int32 part count; per part a name (int16 length, UTF-8), its pivot (3 float32) and
    rest rotation (quaternion x, y, z, w; float32) in Minecraft's axes, two float32 for the animation (a wing panel's
    open angle and dihedral, in degrees; zero for anything else), int32 quad count and four vertices per quad:
    float32 x, y, z, u, v, then uint8 r, g, b, a and int8 nx, ny, nz, 0. Triangles repeat their last corner. Then
    int32 marker count and per marker a name and a position (3 float32)."""
    import struct
    neutral()
    deps = bpy.context.evaluated_depsgraph_get()
    objs = [o for o in bpy.data.objects if o.type == 'MESH' and part_of(o)]
    meshes = {o.name: evaluated_quads(o, deps) for o in objs}
    # Occlusion from the car as it stands on the road: wings, propeller and screw neither cast nor take any.
    shading = [(o, meshes[o.name]) for o in objs
               if not ('open_yaw' in o or part_of(o) in ('propeller', 'screw', 'glass'))]
    ao = occlusion(shading)
    # Each textured material's UV range, so its tile can hold exactly that much of its texture.
    ranges = {}
    for o in objs:
        me = meshes[o.name]
        uv = me.uv_layers.active
        for poly in me.polygons:
            mat = me.materials[poly.material_index].name if me.materials else 'chassis'
            if mat in TILES and uv:
                for li in poly.loop_indices:
                    u, v = uv.data[li].uv
                    r = ranges.setdefault(mat, [u, v, u, v])
                    r[0], r[1], r[2], r[3] = min(r[0], u), min(r[1], v), max(r[2], u), max(r[3], v)
    atlas = np.ones((ATLAS, ATLAS, 4))
    for mat, (x0, y0, w, h) in TILES.items():
        src = SOURCES[mat]()
        sh, sw = src.shape[:2]
        u0, v0, u1, v1 = ranges.get(mat, [0, 0, 1, 1])
        uu = u0 + (np.arange(w) + 0.5) / w * (u1 - u0)
        vv = v1 - (np.arange(h) + 0.5) / h * (v1 - v0)      # the tile's top row is the highest v
        px = (np.mod(uu, 1.0) * sw).astype(int) % sw
        py = ((1.0 - np.mod(vv, 1.0)) * sh).astype(int) % sh
        atlas[y0:y0 + h, x0:x0 + w] = src[py[:, None], px[None, :]]
    Image.fromarray((np.clip(atlas, 0, 1) * 255).astype(np.uint8), 'RGBA').save(GAME_TEXTURE, optimize=True)

    parts = {}
    for o in objs:
        parts.setdefault(part_of(o), []).append(o)
    order = ['body', 'glass'] + sorted(k for k in parts if k not in ('body', 'glass'))
    flat_u, flat_v = (FLAT[0] + FLAT[2] / 2) / ATLAS, (FLAT[1] + FLAT[3] / 2) / ATLAS
    with open(GAME_MESH, 'wb') as f:
        f.write(b'CBM1' + struct.pack('<i', len(order)))
        total = 0
        for name in order:
            group = parts[name]
            merged = name in ('body', 'glass')
            extra = (0.0, 0.0)
            if merged:
                pivot, rot = Vector((0, 0, 0)), (0.0, 0.0, 0.0, 1.0)
            else:
                o = group[0]
                pivot = o.location.copy()
                q = o.rotation_euler.to_quaternion()
                rot = (-q.x, q.z, q.y, q.w)
                if 'open_yaw' in o:
                    extra = (float(o['open_yaw']), float(o['dihedral']))
            quads = []
            for o in group:
                me = meshes[o.name]
                uv = me.uv_layers.active
                normals = me.corner_normals
                if merged:
                    mw = o.matrix_world
                    pm = lambda co, mw=mw: mw @ co
                    nm3 = mw.to_3x3().inverted().transposed()
                    nm = lambda n, nm3=nm3: (nm3 @ n).normalized()
                else:
                    sc = Vector(o.scale)
                    pm = lambda co, sc=sc: Vector((co.x * sc.x, co.y * sc.y, co.z * sc.z))
                    nm = lambda n, sc=sc: Vector((n.x / sc.x, n.y / sc.y, n.z / sc.z)).normalized()
                occ = ao.get(o.name)
                for poly in me.polygons:
                    mat = me.materials[poly.material_index].name if me.materials else 'chassis'
                    corners = []
                    for li in poly.loop_indices:
                        co = pm(me.vertices[me.loops[li].vertex_index].co)
                        n = nm(Vector(normals[li].vector))
                        k = occ[li] if occ else 1.0
                        if mat in TILES and uv:
                            x0, y0, w, h = TILES[mat]
                            u0, v0, u1, v1 = ranges[mat]
                            u, v = uv.data[li].uv
                            tu = (x0 + (u - u0) / max(u1 - u0, 1e-6) * w) / ATLAS
                            tv = (y0 + (v1 - v) / max(v1 - v0, 1e-6) * h) / ATLAS
                            rgb = (255, 255, 255)
                        else:
                            tu, tv = flat_u, flat_v
                            rgb = COLORS.get(mat, (255, 0, 255))
                        a = ALPHA.get(mat, 255)
                        c = tuple(int(round(max(0, min(255, ch * k)))) for ch in rgb) + (a,)
                        corners.append((to_mc(co), tu, tv, c, to_mc(n)))
                    if len(corners) == 3:
                        corners.append(corners[2])
                    quads.append(corners)
            f.write(struct.pack('<h', len(name)) + name.encode())
            f.write(struct.pack('<3f', *to_mc(pivot)) + struct.pack('<4f', *rot) + struct.pack('<2f', *extra)
                    + struct.pack('<i', len(quads)))
            buf = bytearray()
            for q in quads:
                for (x, y, z), u, v, (r, g, b, a), (nx, ny, nz) in q:
                    buf += struct.pack('<5f4B4b', x, y, z, u, v, r, g, b, a,
                                       int(round(nx * 127)), int(round(ny * 127)), int(round(nz * 127)), 0)
            f.write(buf)
            total += len(quads)
            print('  %-14s %6d quads' % (name, len(quads)))
        markers = [o for o in bpy.data.objects if o.get('part') == 'marker']
        f.write(struct.pack('<i', len(markers)))
        for o in markers:
            f.write(struct.pack('<h', len(o.name)) + o.name.encode() + struct.pack('<3f', *to_mc(o.location)))
            print('  marker %-20s %s' % (o.name, tuple(round(c, 3) for c in to_mc(o.location))))
    print('chitty: %d quads in %d parts -> %s (%d KB)' % (total, len(order), GAME_MESH, os.path.getsize(GAME_MESH) // 1024))
    for me in meshes.values():
        bpy.data.meshes.remove(me)
    pose('road')


def render_icon():
    """The item: the car side on, a little from the front and above, shrunk to a crisp 32 x 32."""
    scene = bpy.context.scene
    render_scene_setup()
    for o in ('ground', 'water'):
        bpy.data.objects[o].hide_render = True
    scene.render.film_transparent = True
    cam = scene.camera
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = 5.9
    cam.location = Vector((9.0, 3.2, 3.4))
    cam.rotation_euler = (Vector((0.0, -0.05, 0.85)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    scene.render.resolution_x = scene.render.resolution_y = 512
    scene.cycles.samples = 32
    exposure = scene.view_settings.exposure
    scene.view_settings.exposure = 0.3
    path = os.path.join(bpy.app.tempdir or '/tmp', 'chitty_icon.png')
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    # Shrink with the colour premultiplied, so the transparent surround does not darken the edges.
    small = Image.open(path).convert('RGBa').resize((32, 32), Image.LANCZOS).convert('RGBA')
    # Lifted a little so it reads among the vanilla items, which are drawn bright and flat.
    from PIL import ImageEnhance
    rgb = ImageEnhance.Color(ImageEnhance.Brightness(small.convert('RGB')).enhance(1.25)).enhance(1.2)
    small = Image.merge('RGBA', (*rgb.split(), small.split()[3]))
    a = np.array(small)
    a[..., 3] = np.where(a[..., 3] > 100, 255, 0)
    Image.fromarray(a, 'RGBA').save(ITEM_ICON, optimize=True)
    scene.render.film_transparent = False
    scene.view_settings.exposure = exposure
    cam.data.type = 'PERSP'
    print('icon ->', ITEM_ICON, '(full size at %s)' % path)


def export(out):
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, 'chitty.blend'), check_existing=False)
    for mode, name in (('road', 'chitty.glb'), ('flying', 'chitty_flying.glb'), ('water', 'chitty_water.glb')):
        pose(mode)
        for o in bpy.data.objects:
            o.select_set(o.get('part') not in (None, 'marker') and not o.hide_viewport)
        bpy.ops.export_scene.gltf(filepath=os.path.join(out, name), use_selection=True, export_apply=True,
                                  export_format='GLB', export_yup=True)
    pose('road')


def main():
    args = sys.argv[1:]
    build()
    pose('road')
    if '--game' in args:
        export_game(None)
        render_icon()
    if '--out' not in args:
        return
    out = args[args.index('--out') + 1]
    os.makedirs(out, exist_ok=True)
    export(out)
    if '--renders' in args:
        only = args[args.index('--only') + 1].split(',') if '--only' in args else None
        shots = [
            ('road_front', 'road', (4.6, 5.2, 1.7), (0.0, 0.3, 0.75), 40, 0.0, False),
            ('road_rear', 'road', (-4.4, -5.4, 2.0), (0.0, -0.4, 0.8), 40, 0.0, False),
            ('road_side', 'road', (-8.5, 0.1, 1.2), (0.0, 0.0, 0.85), 50, 0.0, False),
            ('cockpit', 'road', (1.6, -3.4, 2.7), (0.0, 0.2, 0.9), 35, 0.0, False),
            ('flying', 'flying', (4.6, 6.4, 6.2), (0.0, -0.3, 1.9), 30, 1.7, False),
            ('flying_below', 'flying', (-5.5, 4.5, 0.8), (0.0, 0.0, 2.3), 30, 2.0, False),
            ('water', 'water', (5.0, 5.6, 2.2), (0.0, 0.0, 0.6), 38, 0.0, True),
        ]
        for name, mode, cam, at, lens, lift, water in shots:
            if only and name not in only:
                continue
            pose(mode, spin=0.4)
            render_scene(out, name, cam, at, lens, lift, water)
        pose('road')


if __name__ == '__main__':
    main()
