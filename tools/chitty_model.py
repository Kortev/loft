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
TRACK = 0.70          # wheel centres from the middle
FRONT_AXLE = 1.75
REAR_AXLE = -1.70
BOARD_Z = 0.56        # the running boards, from behind the front wing to the rear one
BOARD_FRONT, BOARD_BACK = 1.05, -1.12
# The bonnet: a polished drum tapering to the round radiator, its top level.
BONNET_BACK, BONNET_FRONT = 0.64, 2.12
BONNET_Z0, BONNET_Z1 = 0.99, 1.05   # its axis, rising a little to the front as it narrows
BONNET_R0, BONNET_R1 = 0.47, 0.34
RADIATOR_R = 0.38
# The hull, a varnished boat: (y, half-width at the gunwale, gunwale height, keel height). Its sides flare out from a
# narrow bottom, the keel lifting towards the round stern.
HULL = [
    (0.62, 0.50, 1.30, 0.55),
    (0.40, 0.64, 1.29, 0.53),
    (0.10, 0.71, 1.28, 0.52),
    (-0.50, 0.74, 1.28, 0.52),
    (-1.20, 0.74, 1.30, 0.52),
    (-1.70, 0.74, 1.35, 0.53),
    (-2.05, 0.73, 1.42, 0.56),
    (-2.30, 0.68, 1.48, 0.61),
    (-2.50, 0.59, 1.53, 0.68),
    (-2.64, 0.47, 1.56, 0.77),
    (-2.74, 0.32, 1.58, 0.88),
    (-2.80, 0.16, 1.59, 1.00),
    (-2.82, 0.02, 1.59, 1.10),
]
HULL_NX, HULL_NZ = 1.8, 2.4   # squareness of the sections across and down
HULL_SKIN = 0.035
# The back seat sits in the round of the stern, which wraps round it like a padded pouch; nothing behind it.
POUCH_FROM = -1.50
FRONT_SEAT_Y, REAR_SEAT_Y = -0.15, -1.80
SPARE = (0.70, 0.46, BOARD_Z + 0.01 + WHEEL_R)
# The fans: hinge, panels, length, open angle of the first panel and spread back from it (0 is straight out, positive
# forward), folded angle, dihedral, how far round each folded panel lies from the last, how far apart they stack, and
# how far they draw in when folded (their ribs telescope). Folded, the wings lie under the running boards, the front
# fans under the dumb irons and the tail fans under the hull, their striped edges showing.
WING = dict(hinge=(0.70, 1.18, 0.47), blades=8, length=2.4, open_from=-38, spread=50, fold=-90, dihedral=6, stagger=1.0,
            layer=0.009, tuck=0.85)
CANARD = dict(hinge=(0.56, 2.42, 0.34), blades=3, length=0.45, open_from=55, spread=50, fold=180, dihedral=3, stagger=4.0,
              layer=0.009, tuck=1.0)
TAILFAN = dict(hinge=(0.40, -2.22, 0.55), blades=3, length=0.62, open_from=-20, spread=50, fold=-95, dihedral=8,
               stagger=4.0, layer=0.009, tuck=1.0)
MAST_H = 0.85
ROTOR_R = 0.72
# The float: a pink ring round her, (half-width, half-length) of its middle line, its centre, tube radii.
FLOAT_RING = (1.16, 2.85)
FLOAT_CENTRE = (0.0, -0.20, 0.26)
FLOAT_TUBE = (0.38, 0.20)


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


def plank_texture(planks=18, size=1024, seed=3):
    """The hull's strakes: red and white cedar laid alternately along the car, varnished, with dark seams and rows of
    brass screws; u goes round the hull, v along it."""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:size, 0:size] / size
    p = xx * planks
    idx = np.floor(p).astype(int)
    f = p - idx
    red = np.array([138, 60, 26]) / 255
    white = np.array([178, 104, 48]) / 255
    base = np.where((idx % 2 == 0)[..., None], red, white)
    tone = 0.9 + 0.2 * rng.random(planks + 1)
    # Grain: streaks along each plank, wavering a little, and the odd darker figure.
    phase = rng.random(planks + 1) * 50
    grain = np.sin((f * 4 + np.sin(yy * 7 + phase[idx]) * 0.6) * math.pi * 2 + phase[idx]) * 0.5 + 0.5
    grain = grain ** 5 * 0.25 + rng.normal(0, 1, (1, size)).repeat(size, 0) * 0.02
    fine = rng.normal(0, 0.025, (size, size))
    col = base * (tone[idx] * (1.0 - grain + fine))[..., None]
    # Scarf joints, staggered plank to plank.
    joint = np.abs(((yy + (idx * 0.37) % 1.0) % 1.0) - 0.5) < 0.0015
    seam = (f < 0.035) | joint
    col[seam] = col[seam] * 0.28
    # Brass screws in a row down each seam.
    screw = (np.abs(f - 0.075) < 0.02) & ((((yy * 48) % 1.0) - 0.5) ** 2 < 0.006)
    col[screw] = np.array([0.88, 0.70, 0.34])
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
    return out


def tuft_texture(size=256, cells=6):
    """Deep-buttoned red leather: puffed diamonds, a button in each crossing."""
    yy, xx = np.mgrid[0:size, 0:size] / size * cells
    u = (xx + yy) % 1.0
    v = (xx - yy) % 1.0
    puff = np.sin(u * math.pi) * np.sin(v * math.pi)
    base = np.array([176, 26, 30]) / 255
    col = base[None, None, :] * (0.6 + 0.5 * puff)[..., None]
    d = np.minimum(np.hypot(u - 0.0, v - 0.0), np.minimum(np.hypot(u - 1, v), np.minimum(np.hypot(u, v - 1), np.hypot(u - 1, v - 1))))
    col[d < 0.07] = np.array([0.22, 0.03, 0.04])
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
    return out


def float_texture(size=1024, waves=6):
    """The float's pink rubberised canvas with a wavy band of white edged in green running round it; u runs round the
    ring, v round the tube (0 underneath, 0.25 outside, 0.5 on top)."""
    yy, xx = np.mgrid[0:size, 0:size] / size
    v = 1.0 - yy
    pink = np.array([206, 72, 140]) / 255
    col = pink[None, None, :] * (0.92 + 0.08 * np.sin(v * math.pi * 2))[..., None] * np.ones((size, size, 1))
    centre = 0.40 + 0.035 * np.sin(xx * waves * 2 * math.pi)
    d = np.abs(v - centre)
    col[d < 0.045] = np.array([28, 92, 52]) / 255
    col[d < 0.028] = np.array([244, 244, 238]) / 255
    out = np.ones((size, size, 4))
    out[..., :3] = col
    return out


def pinstripe_texture(size=256):
    """Black lacquer with a fine red line in from each edge; u runs across the panel."""
    yy, xx = np.mgrid[0:size, 0:size] / size
    col = np.ones((size, size, 3)) * np.array([16, 16, 20]) / 255
    line = (np.abs(xx - 0.07) < 0.012) | (np.abs(xx - 0.93) < 0.012)
    col[line] = np.array([178, 24, 24]) / 255
    out = np.ones((size, size, 4))
    out[..., :3] = col
    return out


def honeycomb_texture(size=512, cells=26):
    """The radiator core: little hexagonal cells, dark inside, brass at the walls."""
    yy, xx = np.mgrid[0:size, 0:size] / size * cells
    # Distance to the nearest centre of a hexagonal lattice.
    best = np.full((size, size), 9.0)
    for ox, oy in ((0.0, 0.0), (0.5, math.sqrt(3) / 2)):
        gx = (xx - ox)
        gy = (yy - oy) / math.sqrt(3)
        cx = np.round(gx) + ox
        cy = np.round(gy) * math.sqrt(3) + oy
        best = np.minimum(best, np.hypot(xx - cx, yy - cy))
    wall = best > 0.40
    col = np.ones((size, size, 3)) * np.array([30, 24, 18]) / 255 * (0.6 + 0.8 * best)[..., None]
    col[wall] = np.array([190, 146, 62]) / 255
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
    return out


def make_materials():
    # Polished: the bonnet is a mirror in the film.
    material('aluminium', (220, 224, 230), metal=1.0, rough=0.10)
    material('aluminium_dull', (172, 174, 178), metal=1.0, rough=0.45)
    material('brass', (224, 174, 72), metal=1.0, rough=0.18)
    material('copper', (204, 118, 72), metal=1.0, rough=0.22)
    material('cedar', (150, 70, 32), rough=0.3, image=image('cedar', plank_texture()), coat=0.7)
    material('chrome', (228, 230, 234), metal=1.0, rough=0.06)
    material('walnut', (98, 52, 26), rough=0.3, coat=0.8)
    material('leather', (176, 26, 30), rough=0.5, image=image('tufted', tuft_texture()))
    material('leather_plain', (160, 22, 26), rough=0.45)
    material('carpet', (120, 16, 20), rough=0.95)
    material('strap', (58, 34, 20), rough=0.55)
    material('black', (14, 14, 18), rough=0.18, coat=0.8)
    material('fender', (16, 16, 20), rough=0.18, coat=0.8, image=image('pinstripe', pinstripe_texture()))
    material('chassis', (24, 24, 26), rough=0.6)
    material('rubber', (24, 24, 26), rough=0.85)
    material('red', (198, 26, 24), rough=0.3, coat=0.6)
    # Doped canvas: matte, so it keeps its colour seen edge-on against the sky.
    material('wing_red', (172, 18, 16), rough=0.85, spec=0.08)
    material('wing_yellow', (238, 158, 18), rough=0.85, spec=0.08)
    material('rotor', (70, 66, 62), metal=0.3, rough=0.4)
    material('glass', (220, 235, 240), rough=0.02, glass=True)
    material('lens', (250, 246, 230), rough=0.02, glass=True, ior=1.05)
    material('bulb_glow', (255, 236, 190), rough=0.3, emit=((255, 220, 160), 1.5))
    material('eye', (190, 20, 20), rough=0.2, emit=((255, 40, 30), 0.4))
    material('honeycomb', (40, 34, 28), metal=0.5, rough=0.45, image=image('honeycomb', honeycomb_texture()))
    material('plate', (16, 16, 18), rough=0.4)
    material('letters', (236, 236, 230), rough=0.3)
    material('dial', (236, 230, 210), rough=0.4)
    material('float', (206, 72, 140), rough=0.5, image=image('float_wave', float_texture()))
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

def hull_section(y, hw, zt, zb, inset=0.0, count=72, t0=0.0, t1=math.pi):
    """A hull section from the right gunwale (t = 0) down round the keel (pi / 2) and up to the left (pi)."""
    hw, zb = hw - inset, zb + inset
    pts = []
    for i in range(count + 1):
        t = t0 + (t1 - t0) * i / count
        c, sn = math.cos(t), math.sin(t)
        pts.append((hw * math.copysign(abs(c) ** (2 / HULL_NX), c), zt - (zt - zb) * abs(sn) ** (2 / HULL_NZ)))
    return pts


def hull_at(y):
    """The hull's (half-width, gunwale, keel) at y, between the listed sections."""
    for (y0, *a), (y1, *b) in zip(HULL, HULL[1:]):
        if y1 <= y <= y0:
            k = (y0 - y) / (y0 - y1)
            return tuple(p + (q - p) * k for p, q in zip(a, b))
    return tuple(HULL[0][1:]) if y > HULL[0][0] else tuple(HULL[-1][1:])


def hull_half_width(y, z, inset=0.0):
    """How far out the hull's side is at height z (0 below the keel)."""
    hw, zt, zb = hull_at(y)
    hw, zb = hw - inset, zb + inset
    if z <= zb:
        return 0.0
    s = min(1.0, (zt - z) / (zt - zb)) ** (HULL_NZ / 2)
    return hw * max(0.0, 1 - s * s) ** (0.5 * 2 / HULL_NX)


def build_chassis():
    m = Mesh()
    for s in (-1, 1):
        add_box(m, (s * 0.40, 0.15, 0.44), (0.07, 4.5, 0.11), 'chassis')
        # Leaf springs, dull aluminium like the rest of the running gear.
        for y in (FRONT_AXLE, REAR_AXLE):
            add_box(m, (s * TRACK * 0.78, y, 0.50), (0.06, 0.95, 0.04), 'aluminium_dull')
    add_box(m, (0, 2.32, 0.44), (0.88, 0.07, 0.10), 'chassis')
    add_box(m, (0, FRONT_AXLE, 0.44), (TRACK * 2 - 0.08, 0.07, 0.07), 'aluminium_dull')
    add_box(m, (0, REAR_AXLE, 0.46), (TRACK * 2 - 0.08, 0.08, 0.08), 'aluminium_dull')
    add_box(m, (0, REAR_AXLE, 0.42), (0.24, 0.22, 0.20), 'aluminium_dull')
    add_box(m, (0, 0.2, 0.40), (0.10, 3.6, 0.08), 'chassis')  # the propshaft
    return m.obj('chassis', smooth=30)


def build_radiator():
    """A round brass radiator: the shell, a rim standing proud of a honeycomb core with a brass bar across it, and a
    filler cap and winged mascot on top."""
    zc = BONNET_Z1
    m = Mesh()
    lathe(m, [(BONNET_R1 - 0.01, BONNET_FRONT - 0.005), (RADIATOR_R - 0.005, BONNET_FRONT + 0.015),
              (RADIATOR_R, BONNET_FRONT + 0.04), (RADIATOR_R, 2.28), (RADIATOR_R - 0.012, 2.31),
              (RADIATOR_R - 0.05, 2.31), (RADIATOR_R - 0.06, 2.295)],
          lambda k: 'brass', axis='y', seg=64, origin=(0, 0, zc))
    o = m.obj('radiator', smooth=50)
    m = Mesh()
    core = RADIATOR_R - 0.06
    ring = [m.vert((core * math.cos(2 * math.pi * i / 64), 2.295, zc + core * math.sin(2 * math.pi * i / 64)))
            for i in range(64)]
    f = m.face(list(reversed(ring)), 'honeycomb')
    for loop in f.loops:
        loop[m.uv].uv = (loop.vert.co.x * 2.2 + 0.5, (loop.vert.co.z - zc) * 2.2 + 0.5)
    add_box(m, (0, 2.30, zc), (0.035, 0.02, core * 2), 'brass')
    lathe(m, [(0.0, 2.33), (0.05, 2.325), (0.06, 2.30)], lambda k: 'brass', axis='y', seg=24, origin=(0, 0, zc))
    m.obj('grille', smooth=40)
    m = Mesh()
    top = zc + RADIATOR_R
    lathe(m, [(0.0, 0.0), (0.045, 0.0), (0.05, 0.02), (0.045, 0.05), (0.02, 0.06), (0.0, 0.065)], lambda k: 'brass',
          axis='z', seg=24, origin=(0, 2.21, top - 0.01))
    lathe(m, [(0.0, 0.0), (0.012, 0.0), (0.012, 0.06), (0.025, 0.08), (0.0, 0.11)], lambda k: 'brass',
          axis='z', seg=12, origin=(0, 2.21, top + 0.05))
    for s in (-1, 1):
        add_box(m, (s * 0.045, 2.20, top + 0.14), (0.07, 0.025, 0.012), 'brass', rot=Matrix.Rotation(-s * 0.35, 4, 'Y'))
    m.obj('mascot', smooth=40)
    return o


def bonnet_ring(y):
    """The bonnet's radius and the height of its axis at y."""
    k = (y - BONNET_BACK) / (BONNET_FRONT - BONNET_BACK)
    return BONNET_R0 + (BONNET_R1 - BONNET_R0) * k, BONNET_Z0 + (BONNET_Z1 - BONNET_Z0) * k


def build_bonnet():
    """A polished aluminium drum from the dashboard to the radiator, a brass band round its back, a leather strap with
    a brass buckle round its front, and the hinge line down each side. Its back is the walnut dashboard."""
    m = Mesh()
    sections = []
    for y in np.linspace(BONNET_BACK, BONNET_FRONT, 7):
        r, zc = bonnet_ring(y)
        sections.append((y, [(r * math.cos(t), zc + r * math.sin(t)) for t in
                             (math.pi / 2 - 2 * math.pi * i / 72 for i in range(72))]))
    loft(m, sections, 'aluminium', cap_start='walnut')
    o = m.obj('bonnet', smooth=50)
    m = Mesh()
    r, zc = bonnet_ring(BONNET_BACK)
    lathe(m, [(r - 0.005, BONNET_BACK - 0.01), (r + 0.018, BONNET_BACK), (r + 0.018, BONNET_BACK + 0.045),
              (r - 0.005, BONNET_BACK + 0.055)], lambda k: 'brass', axis='y', seg=72, origin=(0, 0, zc))
    # The strap, over the top from one side to the other.
    y = 1.80
    r, zc = bonnet_ring(y)
    arc = [((r + 0.008) * math.cos(t), zc + (r + 0.008) * math.sin(t)) for t in np.linspace(-0.35, math.pi + 0.35, 40)]
    loft(m, [(y - 0.035, arc), (y + 0.035, arc)], 'strap', closed=False)
    add_box(m, (r * math.cos(0.5) + 0.01, y, zc + r * math.sin(0.5)), (0.03, 0.09, 0.07), 'brass',
            rot=Matrix.Rotation(-0.5, 4, 'Y'))
    # The hinge line along each side.
    for s in (-1, 1):
        r0, z0 = bonnet_ring(BONNET_BACK + 0.06)
        r1, z1 = bonnet_ring(BONNET_FRONT - 0.02)
        tube(m, [Vector((s * (r0 + 0.004) * math.cos(0.12), BONNET_BACK + 0.06, z0 + r0 * math.sin(0.12))),
                 Vector((s * (r1 + 0.004) * math.cos(0.12), BONNET_FRONT - 0.02, z1 + r1 * math.sin(0.12)))],
             0.007, 'brass', seg=6)
    m.obj('bonnet_trim', smooth=40)
    # The dashboard's dials, looking back at the driver, as out of an old aeroplane.
    m = Mesh()
    for x, z, rr in ((-0.22, 1.20, 0.045), (-0.06, 1.24, 0.038), (0.10, 1.20, 0.05), (0.34, 1.02, 0.04), (0.20, 1.06, 0.035)):
        lathe(m, [(0.0, 0.0), (rr + 0.008, 0.0), (rr + 0.008, -0.010), (rr, -0.012), (0.0, -0.008)],
              lambda k: 'dial' if k == 3 else 'brass', axis='y', seg=20, origin=(x, BONNET_BACK - 0.005, z))
    m.obj('dashboard', smooth=40)
    return o


def build_hull():
    """The boat: a planked cedar skin open at the top, lined with red leather, a walnut capping along the gunwale, a
    brass strip down each side, a carpeted floor, and the stern decked over."""
    outer = [(y, hull_section(y, hw, zt, zb)) for y, hw, zt, zb in HULL]
    m = Mesh()
    loft(m, outer, 'cedar', closed=False, v_scale=0.3)
    o = m.obj('hull', smooth=55)
    # The lining: plain red leather forward, deep-buttoned round the pouch at the back, right round to the stern.
    m = Mesh()
    last = HULL[-2][0]
    fore = [y for y, *_ in HULL if y > POUCH_FROM] + [POUCH_FROM]
    aft = [POUCH_FROM] + [y for y, *_ in HULL if POUCH_FROM > y >= last]
    for ys, mat in ((fore, 'leather_plain'), (aft, 'leather')):
        secs = [(y, list(reversed(hull_section(y, *hull_at(y), inset=HULL_SKIN)))) for y in ys]
        loft(m, secs, mat, closed=False, cap_end='leather' if mat == 'leather' else None, v_scale=2.0)
    m.obj('lining', smooth=55)
    # The gunwale: walnut capping from the bow round to the deck, and a rounded rail on it.
    m = Mesh()
    for s in (-1, 1):
        ys = [y for y, *_ in HULL]
        path = []
        for y in ys:
            hw, zt, zb = hull_at(y)
            path.append((s * (hw - HULL_SKIN / 2), y, zt + 0.012))
        tube(m, catmull(path, 6), 0.022, 'walnut', seg=10)
        # A brass rubbing strip a little below the gunwale.
        strip = []
        for y in ys[:-1]:
            hw, zt, zb = hull_at(y)
            z = zt - 0.24
            strip.append((s * (hull_half_width(y, z) + 0.006), y, z))
        tube(m, catmull(strip, 6), 0.009, 'brass', seg=8)
    # A padded roll round the lip of the pouch.
    for s in (-1, 1):
        path = []
        for y in np.linspace(POUCH_FROM + 0.1, HULL[-2][0], 10):
            hw, zt, zb = hull_at(y)
            path.append((s * (hw - HULL_SKIN - 0.03), y, zt - 0.02))
        path.append((0.0, HULL[-2][0] - 0.005, hull_at(HULL[-2][0])[1] - 0.02))
        tube(m, catmull(path, 4), 0.032, 'leather_plain', seg=10)
    m.obj('gunwale', smooth=50)
    # The floor.
    m = Mesh()
    zf = 0.64
    secs = []
    for y in np.linspace(0.58, -2.45, 14):
        w = hull_half_width(y, zf, HULL_SKIN) - 0.005
        secs.append((y, [(w, zf), (0.0, zf), (-w, zf)]))
    loft(m, secs, 'carpet', closed=False)
    m.obj('floor', smooth=None)
    # The bow is closed by a bulkhead behind the dashboard.
    m = Mesh()
    y, hw, zt, zb = HULL[0]
    pts = hull_section(y, hw, zt, zb, count=40)
    f = m.face([m.vert((x, y - 0.012, z)) for x, z in pts], 'walnut')
    box_uv(m, [f])
    m.obj('bulkhead', smooth=None)
    return o


def build_seats():
    def seat(name, center, size, tilt=0.0):
        m = Mesh()
        add_box(m, (0, 0, 0), size, 'leather', uv_scale=2.0)
        o = m.obj(name, location=center, rotation=(math.radians(tilt), 0, 0))
        bevel(o, min(size) * 0.3, 4)
        o.modifiers.new('smooth', 'WEIGHTED_NORMAL')
        return o

    # The front bench, its back standing above the gunwale.
    seat('seat_front', (0, FRONT_SEAT_Y, 0.78), (1.00, 0.46, 0.16))
    seat('seatback_front', (0, FRONT_SEAT_Y - 0.28, 1.12), (1.24, 0.12, 0.62), tilt=12)
    # The back seat: a cushion filling the round of the stern and a thick buttoned back curling round with it.
    m = Mesh()
    zc, rows = 0.78, []
    ys = np.linspace(POUCH_FROM - 0.02, -2.42, 9)
    for y in ys:
        w = hull_half_width(y, zc + 0.08, HULL_SKIN) - 0.03
        rows.append([(w * math.cos(t), y) for t in np.linspace(0, math.pi, 13)])
    tops = [[m.vert((x, y, zc + 0.08)) for x, y in r] for r in rows]
    bots = [[m.vert((x, y, zc - 0.08)) for x, y in r] for r in rows]
    for i in range(len(rows) - 1):
        for k in range(12):
            for grid, flip in ((tops, False), (bots, True)):
                q = [grid[i][k], grid[i][k + 1], grid[i + 1][k + 1], grid[i + 1][k]]
                m.face(list(reversed(q)) if flip else q, 'leather',
                       [(k / 6, i / 4), ((k + 1) / 6, i / 4), ((k + 1) / 6, (i + 1) / 4), (k / 6, (i + 1) / 4)])
        for k in (0, 12):
            q = [bots[i][k], bots[i + 1][k], tops[i + 1][k], tops[i][k]]
            m.face(q if k == 0 else list(reversed(q)), 'leather')
    for i, flip in ((0, True), (len(rows) - 1, False)):
        q = [bots[i][k] for k in range(13)] + [tops[i][k] for k in reversed(range(13))]
        m.face(list(reversed(q)) if flip else q, 'leather')
    o = m.obj('seat_rear', smooth=40)
    bevel(o, 0.03, 3)
    # The back: following the lining round the stern, a hand's breadth in from it, from the cushion to the lip.
    m = Mesh()
    heights = np.linspace(zc + 0.08, 1.46, 6)
    ring = []
    for z in heights:
        width = lambda y: hull_half_width(y, min(z, hull_at(y)[1] - 0.05), HULL_SKIN) - 0.15
        # As far back as the hull is wide enough at this height, the same number of points on every ring.
        end = next(y for y in np.linspace(HULL[-2][0], POUCH_FROM, 200) if width(y) > 0.04)
        right = [(width(y), y) for y in np.linspace(POUCH_FROM + 0.25, end, 12)]
        loop = right + [(0.0, end - 0.03)] + [(-x, y) for x, y in reversed(right)]
        ring.append([m.vert((x, y, z)) for x, y in loop])
    run = perimeter_uv([(v.co.x, v.co.y) for v in ring[0]], closed=False)
    for j in range(len(ring) - 1):
        for k in range(len(ring[j]) - 1):
            m.face([ring[j][k + 1], ring[j][k], ring[j + 1][k], ring[j + 1][k + 1]], 'leather',
                   [(run[k + 1] * 5, j / 2.5), (run[k] * 5, j / 2.5), (run[k] * 5, (j + 1) / 2.5), (run[k + 1] * 5, (j + 1) / 2.5)])
    o = m.obj('seatback_rear', smooth=60)
    solidify(o, 0.09, offset=0.0)
    for name, p in (('seat_driver', (0.30, FRONT_SEAT_Y, 0.86)), ('seat_front_passenger', (-0.30, FRONT_SEAT_Y, 0.86)),
                    ('seat_rear_right', (0.30, REAR_SEAT_Y, 0.86)), ('seat_rear_left', (-0.30, REAR_SEAT_Y, 0.86))):
        empty(name, p)


def build_windscreen():
    """A brass-framed screen standing on the scuttle just behind the bonnet."""
    m = Mesh()
    r = 0.015
    y = BONNET_BACK - 0.05
    for x in (-0.56, 0.56):
        tube(m, [Vector((x, y, 1.27)), Vector((x, y, 1.90))], r, 'brass', seg=10)
    tube(m, [Vector((-0.56, y, 1.90)), Vector((0.56, y, 1.90))], r, 'brass', seg=10)
    tube(m, [Vector((-0.56, y, 1.42)), Vector((0.56, y, 1.42))], r * 0.8, 'brass', seg=10)
    tube(m, [Vector((-0.56, y, 1.66)), Vector((0.56, y, 1.66))], r * 0.6, 'brass', seg=10)
    m.obj('windscreen', smooth=40)
    m = Mesh()
    add_box(m, (0, y, 1.66), (1.10, 0.006, 0.47), 'glass')
    m.obj('glass', part='glass')


def build_steering():
    m = Mesh()
    # The rim round its own axis (local Z), three brass spokes, the boss and the column down into the dash.
    prof = [(0.17 + 0.016 * math.cos(a), 0.016 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 9)[:-1]]
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
        add_box(m, (0, 0.085, -0.005), (0.018, 0.17, 0.008), 'brass', matrix=Matrix.Rotation(a - math.pi / 2, 4, 'Z'))
    lathe(m, [(0.0, 0.01), (0.035, 0.01), (0.035, -0.02), (0.02, -0.03)], lambda k: 'brass', axis='z', seg=16)
    tube(m, [Vector((0, 0, -0.02)), Vector((0, 0, -0.34))], 0.016, 'chassis', seg=10)
    center = Vector((0.30, FRONT_SEAT_Y + 0.30, 1.26))
    dash = Vector((0.30, BONNET_BACK, 1.02))
    axis = (center - dash).normalized()
    tilt = math.atan2(-axis.y, axis.z)
    return m.obj('steering_wheel', location=center, rotation=(tilt, 0, 0), smooth=40, part='steering')


def build_spokes(m, mat):
    for k in range(12):
        a = 2 * math.pi * k / 12
        R = Matrix.Rotation(a, 4, 'X')
        add_box(m, (0, 0, 0.195), (0.032, 0.04, 0.24), mat, matrix=R)


def wheel_mesh(side):
    """A red artillery wheel round the X axis: a black tyre, the felloe, twelve spokes and a brass hub."""
    m = Mesh()
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
    lathe(m, [(0.30, -0.04), (0.345, -0.04), (0.345, 0.04), (0.30, 0.04), (0.30, -0.04)], lambda k: 'red',
          axis='x', seg=seg)
    build_spokes(m, 'red')
    outer = 1 if side > 0 else -1
    lathe(m, [(0.0, -0.07 * outer), (0.085, -0.07 * outer), (0.085, 0.06 * outer), (0.07, 0.08 * outer),
              (0.045, 0.10 * outer), (0.0, 0.11 * outer)], lambda k: 'brass', axis='x', seg=24)
    return m


def build_wheels():
    out = []
    for name, side, y in (('wheel_fr', 1, FRONT_AXLE), ('wheel_fl', -1, FRONT_AXLE),
                          ('wheel_rr', 1, REAR_AXLE), ('wheel_rl', -1, REAR_AXLE)):
        out.append(wheel_mesh(side).obj(name, coll='wheels', location=(side * TRACK, y, WHEEL_R), smooth=45, part=name))
    return out


def build_spare():
    """The spare wheel, stood on the right-hand running board against the scuttle, strapped to a brass bracket."""
    x, y, z = SPARE
    wheel_mesh(1).obj('spare', location=SPARE, smooth=45)
    m = Mesh()
    tube(m, [Vector((x - 0.08, y, z)), Vector((hull_half_width(y, z) - 0.02, y, z))], 0.022, 'brass', seg=10)
    arc = [(0.47 * math.cos(t), z + 0.47 * math.sin(t)) for t in np.linspace(math.radians(60), math.radians(120), 10)]
    loft(m, [(x - 0.035, [(y + a, b) for a, b in arc]), (x + 0.035, [(y + a, b) for a, b in arc])], 'strap', closed=False)
    m.obj('spare_mount', smooth=40)


def guard_path(center, r, a0, a1, lead=(), tail=(), steps=16):
    yc, zc = center
    pts = list(lead)
    for k in range(steps + 1):
        a = math.radians(a0 + (a1 - a0) * k / steps)
        pts.append((yc + r * math.cos(a), zc + r * math.sin(a)))
    return pts + list(tail)


def build_guards():
    """Black wings over the wheels, picked out with a red line, sweeping down into black running boards."""
    m = Mesh()
    for s in (-1, 1):
        x = s * TRACK
        front = guard_path((FRONT_AXLE, WHEEL_R), 0.56, 8, 160,
                           tail=[(BOARD_FRONT + 0.08, BOARD_Z + 0.03), (BOARD_FRONT, BOARD_Z + 0.005)])
        rear = guard_path((REAR_AXLE, WHEEL_R), 0.55, 20, 174,
                          lead=[(BOARD_BACK + 0.06, BOARD_Z + 0.005), (BOARD_BACK, BOARD_Z + 0.03)])
        for path, hub in ((front, FRONT_AXLE), (rear, REAR_AXLE)):
            path = [Vector((0, y, z)) for y, z in path]
            cols = 9
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
                    crown = 0.04 * (1 - u * u)
                    row.append(m.vert((x + u * 0.16, p.y + nrm.y * crown, p.z + nrm.z * crown)))
                grid.append(row)
            for i in range(len(grid) - 1):
                for c in range(cols - 1):
                    m.face([grid[i][c], grid[i][c + 1], grid[i + 1][c + 1], grid[i + 1][c]], 'fender',
                           [(c / (cols - 1), i / 8), ((c + 1) / (cols - 1), i / 8),
                            ((c + 1) / (cols - 1), (i + 1) / 8), (c / (cols - 1), (i + 1) / 8)])
    o = m.obj('guards', smooth=50)
    solidify(o, 0.014, offset=0.0)
    m = Mesh()
    yc, ln = (BOARD_FRONT + BOARD_BACK) / 2, BOARD_FRONT - BOARD_BACK + 0.04
    for s in (-1, 1):
        add_box(m, (s * 0.64, yc, BOARD_Z), (0.46, ln, 0.035), 'black')
        add_box(m, (s * 0.64, yc, BOARD_Z + 0.019), (0.42, ln - 0.06, 0.004), 'rubber')
        add_box(m, (s * 0.868, yc, BOARD_Z), (0.012, ln, 0.04), 'brass')
        for y in (BOARD_FRONT - 0.05, BOARD_BACK + 0.05):
            add_box(m, (s * 0.50, y, (BOARD_Z + 0.42) / 2), (0.04, 0.05, BOARD_Z - 0.42), 'chassis')
    m.obj('boards', smooth=None)
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
    # Two great brass headlamps either side of the radiator on stalks from the front wings, and a coach lamp on each
    # post of the windscreen.
    for s in (-1, 1):
        cx = s * 0.56
        tube(m, [Vector((cx, 2.27, 0.60)), Vector((cx, 2.27, 0.90))], 0.02, 'brass', seg=8)
        lathe(m, [(0.0, 0.0), (0.03, 0.0), (0.03, 0.02), (0.0, 0.02)], lambda k: 'brass', axis='z', seg=12,
              origin=(cx, 2.27, 0.90))
        lamp(m, glass, (cx, 2.30, 1.04), 0.165, 0.14)
        tube(m, [Vector((s * 0.37, 2.22, 0.98)), Vector((s * 0.41, 2.27, 1.0))], 0.012, 'brass', seg=6)
        cl = s * 0.635
        y = BONNET_BACK - 0.05
        lamp(m, glass, (cl, y + 0.01, 1.55), 0.05, 0.06)
        lathe(m, [(0.0, 0.0), (0.012, 0.0), (0.012, 0.08), (0.025, 0.09), (0.0, 0.11)], lambda k: 'brass', axis='z',
              seg=12, origin=(cl, y + 0.01, 1.60))
        add_box(m, ((cl + s * 0.56) / 2, y, 1.55), (0.08, 0.02, 0.025), 'brass')
    m.obj('lamps', smooth=40)
    glass.obj('lamp_glass', smooth=40, part='glass')


def build_exhaust():
    """A great flexible copper pipe out of the right of the bonnet, over the front wing, down outside the spare wheel
    and back along the running board to a brass fishtail by the rear wing."""
    m = Mesh()
    r, zc = bonnet_ring(1.55)
    a = math.radians(20)
    start = (r * math.cos(a) - 0.02, 1.55, zc + r * math.sin(a))
    main = catmull([start, (0.58, 1.46, 1.08), (0.72, 1.30, 0.98), (0.82, 1.10, 0.80), (0.83, 0.92, 0.66),
                    (0.83, 0.50, 0.64), (0.83, -0.30, 0.64), (0.83, -0.82, 0.64)], 8)
    tube(m, main, 0.045, 'copper', seg=16)
    for i in range(3, len(main) - 2, 2):
        t = (main[i + 1] - main[i - 1]).normalized()
        tube(m, [main[i] - t * 0.008, main[i] + t * 0.008], 0.051, 'copper', seg=16)
    lathe(m, [(0.055, -0.015), (0.055, 0.015)], lambda k: 'brass', axis='x', seg=16,
          origin=(start[0] - 0.01, start[1], start[2]))
    tip = catmull([(0.83, -0.82, 0.64), (0.83, -0.90, 0.635), (0.83, -0.98, 0.63)], 4)
    tube(m, tip, 0.045, 'brass', seg=16, flare=lambda t: 1.0 + 0.6 * t * t)
    empty('exhaust', (0.83, -1.02, 0.63))
    return m.obj('exhaust', smooth=50)


def build_horn():
    """The serpent horn: a brass snake from the rubber bulb by the driver's hand along the bonnet, rearing up at the
    front with its jaws open."""
    m = Mesh()
    path = catmull([(0.60, 0.50, 1.36), (0.53, 0.78, 1.38), (0.48, 1.05, 1.40), (0.45, 1.30, 1.44),
                    (0.44, 1.50, 1.52), (0.43, 1.62, 1.60), (0.43, 1.72, 1.62)], 8)
    tube(m, path, 0.014, 'brass', seg=12, flare=lambda t: 1.0 + 1.1 * t ** 1.5)
    lathe(m, [(0.0, -0.05), (0.03, -0.045), (0.045, -0.02), (0.045, 0.02), (0.02, 0.04), (0.012, 0.05)],
          lambda k: 'bulb', axis='y', seg=20, origin=(0.60, 0.45, 1.36))
    head = Vector((0.43, 1.78, 1.63))
    ellipsoid(m, head + Vector((0, 0.0, 0.018)), (0.040, 0.075, 0.022), 'brass', rot=Matrix.Rotation(math.radians(22), 4, 'X'))
    ellipsoid(m, head + Vector((0, -0.005, -0.016)), (0.034, 0.065, 0.016), 'brass', rot=Matrix.Rotation(math.radians(-16), 4, 'X'))
    ellipsoid(m, head + Vector((0, 0.01, 0.0)), (0.026, 0.05, 0.02), 'bulb')
    for s in (-1, 1):
        ellipsoid(m, head + Vector((s * 0.03, -0.02, 0.04)), (0.010, 0.010, 0.010), 'eye', seg=8, rings=6)
    for y in (1.05, 1.30):
        r, zc = bonnet_ring(y)
        add_box(m, (0.38, y, 1.38 if y < 1.2 else 1.42), (0.14, 0.025, 0.025), 'brass')
    return m.obj('horn', smooth=45)


def build_levers():
    """The handbrake and gear lever outside the body behind the spare wheel, in a brass quadrant."""
    m = Mesh()
    y = -0.12
    x = hull_half_width(y, 0.80) + 0.03
    add_box(m, (x, y, 0.76), (0.03, 0.30, 0.13), 'brass')
    for y0, y1, z1, knob in ((y - 0.05, y - 0.12, 1.40, False), (y + 0.06, y + 0.12, 1.30, True)):
        tube(m, [Vector((x + 0.015, y0, 0.72)), Vector((x + 0.03, y1, z1))], 0.012, 'brass', seg=8)
        if knob:
            ellipsoid(m, (x + 0.03, y1, z1 + 0.02), (0.028, 0.028, 0.028), 'black', seg=12, rings=8)
        else:
            tube(m, [Vector((x + 0.03, y1, z1 - 0.10)), Vector((x + 0.03, y1, z1 + 0.01))], 0.019, 'leather_plain', seg=10)
    return m.obj('levers', smooth=40)


def build_plates():
    """GEN 11, under the radiator and under the stern, and the starting handle."""
    m = Mesh()
    add_box(m, (0, 2.36, 0.53), (0.50, 0.012, 0.13), 'plate')
    add_box(m, (0, 2.33, 0.53), (0.05, 0.06, 0.05), 'chassis')
    add_box(m, (0, -2.86, 0.86), (0.50, 0.012, 0.13), 'plate')
    tube(m, [Vector((0, -2.80, 1.05)), Vector((0, -2.85, 0.93))], 0.012, 'brass', seg=6)
    tube(m, [Vector((0, 2.30, 0.42)), Vector((0, 2.44, 0.42))], 0.014, 'chassis', seg=8)
    tube(m, [Vector((0, 2.44, 0.42)), Vector((0, 2.44, 0.31))], 0.012, 'chassis', seg=8)
    tube(m, [Vector((0, 2.44, 0.31)), Vector((0, 2.52, 0.31))], 0.016, 'brass', seg=8)
    m.obj('plates', smooth=None)
    text_object('GEN 11', 0.085, (0, 2.367, 0.53), (math.radians(90), 0, math.radians(180)), 'letters')
    text_object('GEN 11', 0.085, (0, -2.867, 0.86), (math.radians(90), 0, 0), 'letters')


def wedge(m, length, half_angle, mat, spar=False):
    """One panel of a fan: a wedge out along +X from its hinge, its tip bowed out, a rib down the middle. The first
    panel of a big wing carries a chrome spar down its leading edge. Panels overlap their neighbours, so the open fan
    is one striped sail."""
    a = math.radians(half_angle)
    radial = [0.04 + (length - 0.04) * j / 8 for j in range(9)]
    cols = 7
    grid = []
    for j, r in enumerate(radial):
        row = []
        for k in range(cols):
            t = -1 + 2 * k / (cols - 1)
            ang = a * t
            rr = r * (1 - 0.05 * t * t * (j / 8) ** 2)
            row.append(m.vert((rr * math.cos(ang), rr * math.sin(ang), 0.0)))
        grid.append(row)
    for j in range(len(grid) - 1):
        for k in range(cols - 1):
            m.face([grid[j][k], grid[j + 1][k], grid[j + 1][k + 1], grid[j][k + 1]], mat,
                   [(j / 8, k / 6), ((j + 1) / 8, k / 6), ((j + 1) / 8, (k + 1) / 6), (j / 8, (k + 1) / 6)])
    tube(m, [Vector((0.02, 0.0, 0.005)), Vector((length * 0.97, 0.0, 0.005))], 0.007, 'brass', seg=6)
    if spar:
        d = Vector((math.cos(a), math.sin(a), 0.0))
        tube(m, [d * 0.05 + Vector((0, 0, 0.01)), d * length + Vector((0, 0, 0.01))], 0.022, 'chrome', seg=10)
        return d * length
    return None


def fan(name, side, spec, colors, coll, spar=False):
    """A fan of wedges on a hinge; panel i of side s is named <name>_<s>_<i>, its origin on the hinge, with its open
    and folded angles (degrees, 0 straight out, positive forward) and dihedral as properties."""
    objs = []
    blades = spec['blades']
    step = spec['spread'] / max(1, blades - 1)
    hx, hy, hz = spec['hinge']
    tip = None
    for i in range(blades):
        m = Mesh()
        t = wedge(m, spec['length'], step / 2 + 1.0, colors[i % 2], spar=spar and i == 0)
        if t is not None:
            tip = t
        tag = '%s_%s_%d' % (name, 'r' if side > 0 else 'l', i)
        o = m.obj(tag, coll=coll, location=(side * hx, hy, hz + spec['layer'] * i), smooth=None, part=tag)
        solidify(o, 0.008, offset=0.0)
        if side < 0:
            o.scale = (-1, 1, 1)
        o['open_yaw'] = spec['open_from'] - step * i
        # Folded, each panel lies a degree or two round from the one above, so the stack shows its stripes.
        o['fold_yaw'] = spec['fold'] + math.copysign(spec['stagger'] * i, spec['open_from'] - spec['fold'])
        o['tuck'] = spec['tuck']
        o['dihedral'] = spec['dihedral']
        objs.append(o)
    return objs, tip


def build_rotor(wing0, tip, side):
    """The mast at the end of a wing's spar and the two-bladed propeller turning flat on top of it. Both hang off the
    wing's first panel (in its own frame): the mast lies along the spar when the wing is folded and stands up as it
    opens, and the propeller unfolds at its head."""
    tag = 'r' if side > 0 else 'l'
    m = Mesh()
    tube(m, [Vector((0, 0, -0.02)), Vector((0, 0, MAST_H))], 0.016, 'chrome', seg=10)
    lathe(m, [(0.0, -0.05), (0.04, -0.05), (0.035, 0.02), (0.0, 0.03)], lambda k: 'chrome', axis='z', seg=12)
    for z in (0.3, 0.6):
        lathe(m, [(0.024, z - 0.01), (0.024, z + 0.01)], lambda k: 'brass', axis='z', seg=10)
    mast = m.obj('mast_' + tag, coll='wings', smooth=40, part='mast_' + tag)
    mast.parent = wing0
    mast.location = tip
    m = Mesh()
    lathe(m, [(0.0, -0.04), (0.045, -0.04), (0.05, 0.0), (0.03, 0.05), (0.0, 0.07)], lambda k: 'brass', axis='z', seg=16)
    for sign in (1, -1):
        stations = np.linspace(0.05, ROTOR_R, 10)
        rings = []
        for r in stations:
            t = (r - 0.05) / (ROTOR_R - 0.05)
            chord = 0.05 + 0.05 * math.sin(math.pi * min(1.0, t * 1.1)) if t < 0.95 else 0.04
            twist = math.radians(16 - 10 * t)
            thick = 0.016 * (1 - 0.5 * t)
            ring = []
            for x, z in ((-chord / 2, -thick / 2), (chord / 2, -thick / 2), (chord / 2, thick / 2), (-chord / 2, thick / 2)):
                xr = x * math.cos(twist) - z * math.sin(twist)
                zr = x * math.sin(twist) + z * math.cos(twist)
                ring.append(m.vert((r * sign, xr * sign, zr)))
            rings.append(ring)
        for i in range(len(rings) - 1):
            for k in range(4):
                j = (k + 1) % 4
                a, b, c, d = rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]
                m.face([a, b, c, d] if sign > 0 else [d, c, b, a], 'rotor')
        m.face(rings[-1] if sign > 0 else list(reversed(rings[-1])), 'rotor')
    rotor = m.obj('rotor_' + tag, coll='wings', smooth=35, part='rotor_' + tag)
    rotor.parent = mast
    rotor.location = (0, 0, MAST_H)
    return mast, rotor


def build_wings():
    objs = []
    for s in (-1, 1):
        panels, tip = fan('wing', s, WING, ('wing_red', 'wing_yellow'), 'wings', spar=True)
        objs += panels
        build_rotor(panels[0], tip, s)
        objs += fan('canard', s, CANARD, ('wing_yellow', 'wing_red'), 'wings')[0]
        objs += fan('tailwing', s, TAILFAN, ('wing_yellow', 'wing_red'), 'wings')[0]
    return objs


def build_screw():
    m = Mesh()
    tube(m, [Vector((0, 0.0, 0)), Vector((0, 0.36, 0.30))], 0.014, 'brass', seg=8)
    lathe(m, [(0.0, -0.06), (0.03, -0.05), (0.035, 0.0), (0.02, 0.03), (0.0, 0.035)], lambda k: 'brass', axis='y', seg=16)
    for k in range(3):
        a = 2 * math.pi * k / 3
        R = Matrix.Rotation(a, 4, 'Y') @ Matrix.Rotation(math.radians(28), 4, 'Z')
        add_box(m, (0.0, 0.0, 0.10), (0.07, 0.01, 0.13), 'brass', matrix=R)
    return m.obj('screw', coll='floats', location=(0, -2.45, 0.40), smooth=40, part='screw')


def build_float():
    """The great pink float she blows up round herself on the water: a fat ring, rounded-square in plan, with a wavy
    white band edged in green running round it."""
    ax, ay = FLOAT_RING
    rx, rz = FLOAT_TUBE
    n = 2.6
    count, around = 96, 20
    m = Mesh()
    centre = []
    for i in range(count):
        t = 2 * math.pi * i / count
        c, s = math.cos(t), math.sin(t)
        centre.append(Vector((ax * math.copysign(abs(c) ** (2 / n), c), ay * math.copysign(abs(s) ** (2 / n), s), 0.0)))
    run = [0.0]
    for i in range(1, count + 1):
        run.append(run[-1] + (centre[i % count] - centre[i - 1]).length)
    rings = []
    for i, p in enumerate(centre):
        t = (centre[(i + 1) % count] - centre[i - 1]).normalized()
        out = Vector((t.y, -t.x, 0.0))
        if out.dot(p) < 0:
            out = -out
        rings.append([m.vert(p + out * (rx * math.cos(-math.pi / 2 + 2 * math.pi * k / around))
                             + Vector((0, 0, rz * math.sin(-math.pi / 2 + 2 * math.pi * k / around))))
                      for k in range(around)])
    reps = 4
    for i in range(count):
        j = (i + 1) % count
        u0, u1 = run[i] / run[-1] * reps, run[i + 1] / run[-1] * reps
        for k in range(around):
            l = (k + 1) % around
            m.face([rings[i][k], rings[i][l], rings[j][l], rings[j][k]], 'float',
                   [(u0, k / around), (u0, (k + 1) / around), (u1, (k + 1) / around), (u1, k / around)])
    for x in (-1, 1):
        for y in (-1.6, 1.0):
            tube(m, [Vector((x * (ax - 0.25), y, 0.20)), Vector((x * (ax - 0.62), y, 0.36))], 0.018, 'brass', seg=8)
    return m.obj('float', coll='floats', location=FLOAT_CENTRE, smooth=40, part='float')


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    make_materials()
    build_chassis()
    build_radiator()
    build_bonnet()
    build_hull()
    build_seats()
    build_windscreen()
    build_steering()
    build_wheels()
    build_spare()
    build_guards()
    build_lamps()
    build_exhaust()
    build_horn()
    build_levers()
    build_plates()
    build_wings()
    build_screw()
    build_float()


# --- poses -----------------------------------------------------------------------------------------

def side_of(o):
    """1 for a part on her right, -1 on her left, from its name (its world matrix is stale until Blender updates it)."""
    return 1 if '_r_' in o.name + '_' else -1


def pose(mode, spin=0.0):
    """'road', 'flying' or 'water': what the game's animation does, for the renders and exports."""
    fly = mode == 'flying'
    wet = mode == 'water'
    for o in bpy.data.objects:
        part = o.get('part', '')
        if 'open_yaw' in o:
            side = side_of(o)
            yaw = math.radians(o['open_yaw'] if fly else o['fold_yaw'])
            o.rotation_euler = (0, -math.radians(o['dihedral']) * side if fly else 0, yaw * side)
            k = 1.0 if fly else o['tuck']
            o.scale = (k * side, k, 1)
        elif part.startswith('mast_'):
            # Folded, it lies along the spar towards the hinge.
            d = o.location.normalized()
            axis = Vector((0, 0, 1)).cross(-d).normalized()
            o.rotation_mode = 'QUATERNION'
            o.rotation_quaternion = Matrix.Rotation(0 if fly else math.pi / 2, 4, axis).to_quaternion()
        elif part.startswith('rotor_'):
            o.hide_render = o.hide_viewport = not fly
            o.rotation_euler = (0, 0, spin * 2.3)
        elif part.startswith('wheel_'):
            o.rotation_euler = (spin, 0, 0)
        elif part in ('screw', 'float'):
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
        wo.location.z = 0.30
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
        if o.get('part') is not None and o.get('part') != 'marker' and o.parent is None:
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

# The texture: every part unwrapped into one atlas and its look baked into it with Cycles (the sky, the soft shadows
# and the reflections in the metal), so the game draws her as she renders here.
BAKE_SIZE = 2048
BAKE_SAMPLES = int(os.environ.get('CHITTY_BAKE_SAMPLES', '96'))
BAKE_EXPOSURE = 0.0
# See-through materials keep a flat white texel and carry their tint and opacity on the vertices.
GLASS_TINT = {'glass': (220, 235, 240, 70), 'lens': (250, 246, 230, 90)}
# Materials drawn at full brightness in the dark: the lamp bulbs and the serpent's eyes.
GLOW = ('bulb_glow', 'eye')
# Parts the game shades as they turn: the wheels roll, so their bake sees an even sky and the game lights them. Every
# other part carries its light in the texture and is drawn evenly lit (its normals point up).
GAME_LIT = ('wheel_',)


def to_mc(v):
    """Blender (x right, y forward, z up) to Minecraft at yaw 0 (x to her left, y up, z forward)."""
    return (-v[0], v[2], v[1])


def quat_to_mc(q):
    return (-q.x, q.z, q.y, q.w)


def neutral():
    """Every part shown, unturned and full size: the renderer poses them."""
    for o in bpy.data.objects:
        part = o.get('part')
        if part is None:
            continue
        o.hide_render = o.hide_viewport = False
        if 'open_yaw' in o:
            side = side_of(o)
            o.rotation_euler = (0, 0, 0)
            o.scale = (side, 1, 1)
        elif part.startswith('mast_'):
            o.rotation_mode = 'QUATERNION'
            o.rotation_quaternion = (1, 0, 0, 0)
        elif part.startswith(('wheel_', 'rotor_')) or part == 'screw':
            o.rotation_euler = (0, 0, 0)
    bpy.context.view_layer.update()


def part_of(o):
    part = o.get('part')
    if part in (None, 'marker'):
        return None
    if part in ('body', 'glass'):
        return part
    return o.name if part != 'steering' else 'steering_wheel'


def evaluated_mesh(o, deps):
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


def mat_name(me, poly):
    return me.materials[poly.material_index].name if me.materials else 'chassis'


def bake_worlds():
    """A sky for the bake: bright overhead, brightest at the horizon, a darker warm ground; and an even grey one for
    the parts the game lights itself."""
    worlds = {}
    for name, stops in (('sky', [(0.0, (0.20, 0.18, 0.15)), (0.47, (0.30, 0.27, 0.23)), (0.50, (1.10, 1.05, 0.98)),
                                 (0.60, (0.95, 0.98, 1.05)), (1.0, (0.62, 0.72, 0.95))]),
                        ('even', [(0.0, (0.55, 0.55, 0.55)), (1.0, (0.62, 0.62, 0.62))])):
        w = bpy.data.worlds.new('bake_' + name)
        w.use_nodes = True
        nt = w.node_tree
        tc = nt.nodes.new('ShaderNodeTexCoord')
        sep = nt.nodes.new('ShaderNodeSeparateXYZ')
        mapr = nt.nodes.new('ShaderNodeMapRange')
        mapr.inputs['From Min'].default_value = -1.0
        ramp = nt.nodes.new('ShaderNodeValToRGB')
        cr = ramp.color_ramp
        cr.elements[0].position, cr.elements[0].color = stops[0][0], (*stops[0][1], 1)
        cr.elements[1].position, cr.elements[1].color = stops[-1][0], (*stops[-1][1], 1)
        for pos, col in stops[1:-1]:
            e = cr.elements.new(pos)
            e.color = (*col, 1)
        nt.links.new(tc.outputs['Generated'], sep.inputs['Vector'])
        nt.links.new(sep.outputs['Z'], mapr.inputs['Value'])
        nt.links.new(mapr.outputs['Result'], ramp.inputs['Fac'])
        nt.links.new(ramp.outputs['Color'], nt.nodes['Background'].inputs['Color'])
        nt.nodes['Background'].inputs['Strength'].default_value = 1.0
        worlds[name] = w
    return worlds


# How much of the atlas a part gets for its size: more for what is looked at close to (the brass, the bonnet, the
# lettering), less for big plain surfaces and what is out of sight underneath.
TEXEL_WEIGHT = {'chassis': 0.3, 'floor': 0.4, 'bulkhead': 0.3, 'boards': 0.6, 'float': 0.45, 'screw': 0.5,
                'wing_': 0.32, 'canard_': 0.45, 'tailwing_': 0.45, 'mast_': 0.6, 'rotor_': 0.6, 'plate_text': 1.6,
                'plates': 1.3, 'radiator': 1.3, 'grille': 1.3, 'mascot': 1.3, 'lamps': 1.3, 'horn': 1.3, 'dashboard': 1.3,
                'bonnet': 1.15, 'hull': 1.15}


def texel_weight(name):
    for key, w in TEXEL_WEIGHT.items():
        if name == key or (key.endswith('_') and name.startswith(key)):
            return w
    return 1.0


def unwrap(objs):
    """Each object unwrapped on its own, then all their islands scaled alike (by area, then by TEXEL_WEIGHT) and
    packed into one square."""
    view = bpy.context.view_layer
    for o in objs:
        bpy.ops.object.select_all(action='DESELECT')
        o.select_set(True)
        view.objects.active = o
        bpy.ops.object.mode_set(mode='EDIT')
        bpy.ops.mesh.select_all(action='SELECT')
        bpy.ops.uv.smart_project(angle_limit=math.radians(55), island_margin=0.0, area_weight=0.0, scale_to_bounds=False)
        bpy.ops.object.mode_set(mode='OBJECT')
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    view.objects.active = objs[0]
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.select_all(action='SELECT')
    bpy.ops.uv.average_islands_scale()
    bpy.ops.object.mode_set(mode='OBJECT')
    for o in objs:
        w = texel_weight(o.name.split(':', 1)[1])
        if w != 1.0:
            uv = o.data.uv_layers['bake'].data
            co = np.empty(len(uv) * 2, np.float32)
            uv.foreach_get('uv', co)
            uv.foreach_set('uv', co * w)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.select_all(action='SELECT')
    bpy.ops.uv.pack_islands(rotate=True, margin_method='FRACTION', margin=float(os.environ.get('CHITTY_UV_MARGIN', '0.0012')), shape_method=os.environ.get('CHITTY_UV_SHAPE', 'CONCAVE'))
    bpy.ops.object.mode_set(mode='OBJECT')


def bake_pass(name, objs, world, sun, visible):
    """Bakes the objects' look into a fresh image; `visible` also cast shadows and reflect."""
    scene = bpy.context.scene
    img = bpy.data.images.new('bake_' + name, BAKE_SIZE, BAKE_SIZE, alpha=True, float_buffer=True)
    img.generated_color = (0, 0, 0, 0)
    for m in MATS.values():
        node = m.node_tree.nodes.get('bake_target')
        node.image = img
        m.node_tree.nodes.active = node
    show = set(objs) | set(visible)
    for o in scene.objects:
        if o.type in ('MESH', 'CURVE', 'FONT'):
            o.hide_render = o not in show
    sun.hide_render = world.name != 'bake_sky'
    scene.world = world
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.bake(type='COMBINED', margin=0, use_clear=False)
    # Through the same view transform as the renders, out to 8 bits.
    path = os.path.join(bpy.app.tempdir or '/tmp', 'chitty_bake_%s.png' % name)
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    img.save_render(path, scene=scene)
    out = np.array(Image.open(path).convert('RGBA')).astype(np.float32) / 255.0
    print('  baked %-6s %5.1f%% of the atlas' % (name, (out[..., 3] > 0.5).mean() * 100))
    return out


def dilate(rgb, valid, steps=24):
    """Spreads the colour of each island out past its edges, so filtering and mipmaps do not pull in the background."""
    rgb, valid = rgb.copy(), valid.copy()
    for _ in range(steps):
        acc = np.zeros_like(rgb)
        cnt = np.zeros(valid.shape, np.float32)
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
            v = np.roll(np.roll(valid, dy, 0), dx, 1)
            acc += np.roll(np.roll(rgb, dy, 0), dx, 1) * v[..., None]
            cnt += v
        grow = (~valid) & (cnt > 0)
        rgb[grow] = acc[grow] / cnt[grow][:, None]
        valid = valid | grow
    rgb[~valid] = rgb[valid].mean(axis=0)
    return rgb


def free_spot(valid, size=8):
    """The middle of an empty size x size square of the atlas (in UV), for the glass's white texel."""
    h, w = valid.shape
    for y in range(size, h - size, size):
        for x in range(size, w - size, size):
            if not valid[y - size:y + size, x - size:x + size].any():
                return x, y
    raise RuntimeError('no room left in the atlas for the glass')


def export_game(root):
    """Writes the game's mesh (.cbm), its baked texture and the item icon.

    .cbm (little endian): b"CBM2", int32 part count; per part a name (int16 length, UTF-8), its pivot (3 float32) and
    rest rotation (quaternion x, y, z, w; float32) in Minecraft's axes, four float32 for its animation (a fan panel's
    open angle, dihedral, folded angle and how far it draws in when folded; a mast's folded rotation as a quaternion;
    zero otherwise), int32 quad count and four vertices per quad: float32 x, y, z, u, v, then uint8 r, g, b, a and int8
    nx, ny, nz, flags (1: glows). Triangles repeat their last corner. Then int32 marker count and per marker a name and
    a position (3 float32). Masts hang off the first panel of their wing and propellers off their mast: their pivots and
    geometry are in that frame, mirrored with it on the left."""
    import struct
    scene = bpy.context.scene
    neutral()
    deps = bpy.context.evaluated_depsgraph_get()
    objs = [o for o in bpy.data.objects if o.type == 'MESH' and part_of(o)]
    meshes = {o.name: evaluated_mesh(o, deps) for o in objs}

    # Bake copies: the body where it stands, every part that moves on its own away to one side in its resting pose.
    bake_coll = collection('bake')
    copies = {}
    free_i = 0
    for o in objs:
        part = part_of(o)
        if part == 'glass':
            continue
        me = meshes[o.name].copy()
        mw = o.matrix_world.copy()
        if part != 'body':
            mw = Matrix.Translation((60.0 + 6.0 * free_i, 0, 0)) @ mw
            free_i += 1
        me.transform(mw)
        if mw.to_3x3().determinant() < 0:
            # Mirrored: turn the faces back the right way out, or the bake sees them from inside.
            me.flip_normals()
        if me.uv_layers.get('bake') is None:
            me.uv_layers.new(name='bake')
        me.uv_layers.active = me.uv_layers['bake']
        me.uv_layers['bake'].active_render = True
        c = bpy.data.objects.new('bake:' + o.name, me)
        bake_coll.objects.link(c)
        copies[o.name] = c
    unwrap(list(copies.values()))
    copies_uv = {name: [tuple(d.uv) for d in c.data.uv_layers['bake'].data] for name, c in copies.items()}

    # The target image node in every material; the textures keep reading their own UVs.
    for m in MATS.values():
        nt = m.node_tree
        for tex in [n for n in nt.nodes if n.type == 'TEX_IMAGE']:
            if not tex.inputs['Vector'].is_linked:
                uvn = nt.nodes.new('ShaderNodeUVMap')
                uvn.uv_map = 'UVMap'
                nt.links.new(uvn.outputs['UV'], tex.inputs['Vector'])
        node = nt.nodes.new('ShaderNodeTexImage')
        node.name = 'bake_target'
    worlds = bake_worlds()
    sun = bpy.data.objects.new('bake_sun', bpy.data.lights.new('bake_sun', 'SUN'))
    sun.data.energy = 2.2
    sun.data.angle = math.radians(40)
    sun.rotation_euler = (0, 0, 0)
    scene.collection.objects.link(sun)
    saved = {o.name: o.hide_render for o in scene.objects}
    old_world = scene.world
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = BAKE_SAMPLES
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Punchy'
    scene.view_settings.exposure = BAKE_EXPOSURE
    scene.render.bake.margin = 0
    scene.render.bake.use_clear = False

    body = [copies[o.name] for o in objs if part_of(o) == 'body']
    wheels = [copies[o.name] for o in objs if o.name.startswith(GAME_LIT)]
    others = [c for name, c in copies.items() if c not in body and c not in wheels]
    real_wheels = [o for o in objs if o.name.startswith(GAME_LIT)]
    passes = [bake_pass('body', body, worlds['sky'], sun, real_wheels),
              bake_pass('parts', others, worlds['sky'], sun, []),
              bake_pass('wheels', wheels, worlds['even'], sun, [])]
    rgb = np.zeros((BAKE_SIZE, BAKE_SIZE, 3), np.float32)
    valid = np.zeros((BAKE_SIZE, BAKE_SIZE), bool)
    for px in passes:
        mine = (px[..., 3] > 0.5) & ~valid
        rgb[mine] = px[..., :3][mine]
        valid |= mine
    gx, gy = free_spot(valid)
    rgb[gy - 6:gy + 6, gx - 6:gx + 6] = 1.0
    valid[gy - 6:gy + 6, gx - 6:gx + 6] = True
    glass_uv = ((gx + 0.5) / BAKE_SIZE, (gy + 0.5) / BAKE_SIZE)
    rgb = dilate(rgb, valid)
    os.makedirs(os.path.dirname(GAME_TEXTURE), exist_ok=True)
    Image.fromarray((np.clip(rgb, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB').save(GAME_TEXTURE, optimize=True)
    print('  texture %s (%d KB)' % (GAME_TEXTURE, os.path.getsize(GAME_TEXTURE) // 1024))

    for name, hidden in saved.items():
        if name in scene.objects:
            scene.objects[name].hide_render = hidden
    scene.world = old_world
    bpy.data.objects.remove(sun)
    for c in list(bake_coll.objects):
        bpy.data.objects.remove(c)

    parts = {}
    for o in objs:
        parts.setdefault(part_of(o), []).append(o)
    order = ['body', 'glass'] + sorted(k for k in parts if k not in ('body', 'glass'))
    with open(GAME_MESH, 'wb') as f:
        f.write(b'CBM2' + struct.pack('<i', len(order)))
        total = 0
        for name in order:
            group = parts[name]
            merged = name in ('body', 'glass')
            extra = (0.0, 0.0, 0.0, 0.0)
            o = group[0]
            if merged:
                pivot, rot = Vector((0, 0, 0)), (0.0, 0.0, 0.0, 1.0)
            elif o.parent is not None:
                # A mast or propeller: in its parent's frame, mirrored with the wing.
                mirror = Vector(o.parent.scale) if name.startswith('mast_') else Vector(o.parent.parent.scale)
                pivot = Vector((o.location.x * mirror.x, o.location.y, o.location.z))
                rot = (0.0, 0.0, 0.0, 1.0)
                if name.startswith('mast_'):
                    d = o.location.normalized()
                    axis = Vector((0, 0, 1)).cross(-d).normalized()
                    R = Matrix.Rotation(math.pi / 2, 3, axis)
                    if mirror.x < 0:
                        M = Matrix.Diagonal((-1, 1, 1))
                        R = M @ R @ M
                    extra = quat_to_mc(R.to_quaternion())
            else:
                pivot = o.location.copy()
                rot = quat_to_mc(o.rotation_euler.to_quaternion())
                if 'open_yaw' in o:
                    extra = (float(o['open_yaw']), float(o['dihedral']), float(o['fold_yaw']), float(o['tuck']))
            lit = name.startswith(GAME_LIT)
            quads = []
            for o in group:
                me = meshes[o.name]
                copy_uv = None if part_of(o) == 'glass' else copies_uv.get(o.name)
                normals = me.corner_normals
                if merged:
                    mw = o.matrix_world
                    pm = lambda co, mw=mw: mw @ co
                    nm3 = mw.to_3x3().inverted().transposed()
                    nm = lambda n, nm3=nm3: (nm3 @ n).normalized()
                else:
                    sc = Vector(o.scale)
                    if o.parent is not None:
                        w = o.parent.scale if name.startswith('mast_') else o.parent.parent.scale
                        sc = Vector((sc.x * w.x, sc.y, sc.z))
                    pm = lambda co, sc=sc: Vector((co.x * sc.x, co.y * sc.y, co.z * sc.z))
                    nm = lambda n, sc=sc: Vector((n.x / sc.x, n.y / sc.y, n.z / sc.z)).normalized()
                for poly in me.polygons:
                    mat = mat_name(me, poly)
                    tint = GLASS_TINT.get(mat)
                    flags = 1 if mat in GLOW else 0
                    corners = []
                    for li in poly.loop_indices:
                        co = pm(me.vertices[me.loops[li].vertex_index].co)
                        n = nm(Vector(normals[li].vector))
                        if tint:
                            u, v = glass_uv
                            c = tint
                        else:
                            u, v = copy_uv[li]
                            v = 1.0 - v
                            c = (255, 255, 255, 255)
                            if not lit:
                                n = Vector((0, 0, 1))
                        corners.append((to_mc(co), u, v, c, to_mc(n), flags))
                    if len(corners) == 3:
                        corners.append(corners[2])
                    quads.append(corners)
            f.write(struct.pack('<h', len(name)) + name.encode())
            f.write(struct.pack('<3f', *to_mc(pivot)) + struct.pack('<4f', *rot) + struct.pack('<4f', *extra)
                    + struct.pack('<i', len(quads)))
            buf = bytearray()
            for q in quads:
                for (x, y, z), u, v, (r, g, b, a), (nx, ny, nz), fl in q:
                    buf += struct.pack('<5f4B4b', x, y, z, u, v, r, g, b, a,
                                       int(round(nx * 127)), int(round(ny * 127)), int(round(nz * 127)), fl)
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
            ('water', 'water', (5.6, 6.4, 2.6), (0.0, -0.2, 0.5), 38, 0.0, True),
            ('water_top', 'water', (-3.0, 0.8, 9.0), (0.0, -0.3, 0.4), 32, 0.0, True),
            ('road_left', 'road', (-5.2, 4.6, 1.8), (0.0, 0.2, 0.8), 40, 0.0, False),
        ]
        for name, mode, cam, at, lens, lift, water in shots:
            if only and name not in only:
                continue
            pose(mode, spin=0.4)
            render_scene(out, name, cam, at, lens, lift, water)
        pose('road')


if __name__ == '__main__':
    main()
