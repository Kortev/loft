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
from mathutils import Euler, Matrix, Vector
from PIL import Image, ImageDraw, ImageFilter

# For the game she is faceted, as the airship is, to sit among Minecraft's blocks (FACET: --game, or --facet to render
# her so): her round parts have few flat sides, a flat one on top (sides()); her curves are taken in fewer, longer
# straight runs (res()); her bars and pipes are square, or eight-sided if they are thick, none thinner than THINNEST
# from the middle to a side; her cushions are chamfered rather than rounded; and every face is flat, lit by the game by
# which way it faces. Set in main(), never on import: the airship borrows these helpers and makes her own choices.
FACET = False
THINNEST = 0.016


def res(n, least=6, div=6):
    """How many steps to take along a curve of n steps: n for the round renders, a sixth as many faceted (at least
    `least`)."""
    return n if not FACET else max(least, round(n / div))


def sides(r):
    """How many flat sides a round part of radius r has, faceted: a stalk four, a knob six, a lamp eight, a wheel
    twelve."""
    return 4 if r < 0.02 else 6 if r < 0.06 else 8 if r < 0.25 else 12


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
# The radiator: an egg, narrowing to a round top, widest low down and flat across the foot (half-width, height above
# and depth below the bonnet's axis).
RADIATOR_W, RADIATOR_TOP, RADIATOR_BOT = 0.37, 0.46, 0.33
# The hull, a varnished boat: (y, half-width at the gunwale, gunwale height, keel height). Its sides flare out from a
# narrow bottom, the keel lifting towards the round stern.
HULL = [
    (0.62, 0.50, 1.30, 0.55),
    (0.40, 0.64, 1.29, 0.53),
    (0.10, 0.71, 1.28, 0.52),
    (-0.50, 0.74, 1.28, 0.52),
    (-1.20, 0.74, 1.29, 0.52),
    (-1.70, 0.72, 1.30, 0.54),
    (-2.05, 0.65, 1.31, 0.59),
    (-2.35, 0.54, 1.32, 0.70),
    (-2.60, 0.41, 1.33, 0.83),
    (-2.80, 0.27, 1.34, 0.96),
    (-2.95, 0.14, 1.35, 1.07),
    (-3.06, 0.01, 1.36, 1.18),
]
HULL_NX, HULL_NZ = 1.8, 2.4   # squareness of the sections across and down
# Beside the front seat the top of the hull is cut down in a U, each side, to step in over: (from, to, depth). The hull
# keeps its shape; only its edge comes down.
DOOR = (-0.15, 0.45, 0.17)
HULL_SKIN = 0.035
# Behind the front seat the hull is decked over right to the point of the stern, the deck crowned like a boat's, its
# front edge curving forward at the sides round the back of the front seat. The back seat sits down in an oval well
# in the deck, wood all round it.
DECK_FRONT, DECK_SIDE = -0.50, -0.18   # where the deck's front edge crosses the middle, and meets the gunwales
DECK_CROWN = 0.07
WELL = (-1.40, 0.40, 0.50)             # the well's centre (y), half-width and half-length
FLOOR_Z = 0.66
FRONT_SEAT_Y, REAR_SEAT_Y = -0.10, -1.30
SPARE = (0.70, 0.80, BOARD_Z + 0.02 + WHEEL_R)
# The side wings: hinge, panels, length, open angle of the first panel and spread back from it (0 is straight out,
# positive forward), dihedral, how far apart they stack, and how far they draw in when folded (their ribs telescope).
# Each is one pleated cloth, a hand fan on its side: its back edge always along her side under the running board, its
# panels pleated up and down. Folded, it is closed up to a little of its spread (closed), the pleats standing on edge,
# and dropped (drop, at most) to hang under the running board; opening, the front edge swings out and the pleats
# flatten as they open, all together.
WING = dict(hinge=(0.70, 1.18, 0.50), blades=8, length=2.4, open_from=-38, spread=50, closed=0.07, drop=0.15,
            dihedral=6, layer=0.003, tuck=0.85, scallop=0.10, ribs='seams')
# The nose wing, as in the film: one fan straight out in front of her, four sections, yellow and red, spreading from a
# straight line under the GEN 11 plate (root: its half-width) to five points: the middle one reaching furthest, the
# next pair less far and the outer pair least, the outer sections half as wide as the inner two (tips: each point's
# (out to her left, out from the line)), shallow bat scallops between them. The sections turn about the point behind
# the line where the edges of the sections would meet, opening in a plane tipped down a little at the front (tilt:
# degrees about her length and across her). Folding, it closes up like a hand fan, one fan all the way, and draws
# back in under the plate (tuck: how far), behind the front cross member. It is pleated like the side wings: folded,
# its sections stand on edge, closed up to a little of their spread (closed) and dropped (drop, at most) clear of what is
# above them; opening, they draw out, spread and flatten together.
NOSEFAN = dict(line=(0.0, 2.38, 0.55), heading=90.0, root=0.30, scallop=0.18, tilt=(0, -12), tuck=0.22, layer=0.004,
               tips=[(0.75, 0.59), (0.50, 0.71), (0.0, 0.95), (-0.50, 0.71), (-0.75, 0.59)], ribs=False,
               closed=0.07, drop=0.0)
# The tail wing, from her drawing: one fan straight out behind, under the hamper, five sections, red and yellow,
# spreading from a straight line across the stern to a wide trailing edge drawn in between its corners, which sweep
# back to points; ribs along the seams. Pleated like the nose wing, it closes up and draws in under the stern, dropped
# clear of the hamper.
TAILFAN = dict(line=(0.0, -2.92, 0.58), heading=-90.0, root=0.25, scallop=0.10, tilt=(0, 0), tuck=0.30, layer=0.004,
               tips=[(-0.80, 0.84), (-0.46, 0.62), (-0.15, 0.56), (0.15, 0.56), (0.46, 0.62), (0.80, 0.84)], ribs=True,
               closed=0.07, drop=0.04)
# The pusher propeller behind the point of the stern, up at the deck on a shaft out of the top of the stern, braced
# down to the hull underneath.
TAILPROP = (0.0, -3.40, 1.30)
TAILPROP_R = 0.30
MAST_H = 0.85
ROTOR_R = 0.72
RAFT = dict(half_width=1.55, half_length=3.35, centre=-0.25, bottom=0.10, top=0.42, waves=0.07)


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


def raft_texture(size=1024):
    """The raft's top: magenta rubberised canvas with a dark green band down each side next to the car and a wavy white
    line outside it; u runs out from her middle to the raft's edge, v along her."""
    yy, xx = np.mgrid[0:size, 0:size] / size
    v = 1.0 - yy
    col = np.ones((size, size, 3)) * np.array([196, 48, 128]) / 255
    col *= (0.94 + 0.06 * np.sin(xx * 9.0 + v * 4.0))[..., None]
    green = (xx > 0.50) & (xx < 0.66)
    col[green] = np.array([30, 86, 50]) / 255
    line = np.abs(xx - (0.70 + 0.016 * np.sin(v * 2 * math.pi * 16))) < 0.011
    col[line] = np.array([246, 244, 238]) / 255
    edge = xx > 0.93
    col[edge] *= 0.85
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
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
    """The radiator core: little hexagonal cells, grey metal."""
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
    col = np.ones((size, size, 3)) * np.array([84, 86, 90]) / 255 * (0.7 + 0.6 * best)[..., None]
    col[wall] = np.array([158, 160, 164]) / 255
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
    return out


def wicker_texture(size=256, cells=12):
    """A wicker hamper's weave: over-and-under bands of cane."""
    yy, xx = np.mgrid[0:size, 0:size] / size * cells
    i, j = np.floor(xx).astype(int), np.floor(yy).astype(int)
    fx, fy = xx - i, yy - j
    over = (i + j) % 2 == 0
    shade = np.where(over, np.sin(fx * math.pi), np.sin(fy * math.pi)) ** 0.6
    col = np.array([200, 152, 82]) / 255 * (0.45 + 0.6 * shade)[..., None]
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
    return out


def make_materials():
    # Polished: the bonnet is a mirror in the film.
    material('aluminium', (220, 224, 230), metal=1.0, rough=0.10)
    material('aluminium_dull', (172, 174, 178), metal=1.0, rough=0.45)
    material('brass', (224, 174, 72), metal=1.0, rough=0.18)
    material('copper', (204, 118, 72), metal=1.0, rough=0.22)
    material('cedar', (150, 70, 32), rough=0.35, image=image('cedar', plank_texture()), coat=0.35)
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
    material('wing_red', (200, 28, 22), rough=0.85, spec=0.08)
    material('wing_yellow', (246, 166, 26), rough=0.85, spec=0.08)
    material('prop', (196, 150, 92), rough=0.3, coat=0.8)
    material('glass', (220, 235, 240), rough=0.02, glass=True)
    material('lens', (250, 246, 230), rough=0.02, glass=True, ior=1.05)
    material('bulb_glow', (255, 236, 190), rough=0.3, emit=((255, 220, 160), 1.5))
    material('eye', (190, 20, 20), rough=0.2, emit=((255, 40, 30), 0.4))
    material('honeycomb', (40, 34, 28), metal=0.5, rough=0.45, image=image('honeycomb', honeycomb_texture()))
    material('plate', (16, 16, 18), rough=0.4)
    material('letters', (236, 236, 230), rough=0.3)
    material('dial', (236, 230, 210), rough=0.4)
    material('float', (196, 48, 128), rough=0.55, image=image('raft_top', raft_texture()))
    material('float_side', (60, 14, 40), rough=0.6)
    material('bulb', (120, 26, 26), rough=0.6)
    material('wicker', (200, 152, 82), rough=0.8, image=image('wicker', wicker_texture()))


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

    def obj(self, name, coll='body', location=(0, 0, 0), rotation=(0, 0, 0), smooth=None, part=None, recalc=True):
        """The mesh as an object. Its faces are turned to face outward, unless recalc is off (an open cup facing in, say,
        where Blender's idea of outward is the wrong way)."""
        me = bpy.data.meshes.new(name)
        bmesh.ops.remove_doubles(self.bm, verts=self.bm.verts, dist=1e-6)
        if recalc:
            bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        self.bm.normal_update()
        self.bm.to_mesh(me)
        self.bm.free()
        for mat in self.slots:
            me.materials.append(MATS[mat])
        if smooth is not None and not FACET:
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


def loft(m, sections, mat, closed=True, cap_start=None, cap_end=None, v_scale=1.0, us=None):
    """Joins sections [(y, [(x, z), ...]), ...] (same point count) into a skin. u runs round each section (us, if
    given, says where each point of each section is), v along the car. Caps are filled with the given materials."""
    rings = []
    for y, pts in sections:
        rings.append([m.vert((x, y, z)) for x, z in pts])
    n = len(sections[0][1])
    if us is None:
        us = [perimeter_uv(sections[0][1], closed)] * len(sections)
    y0 = sections[0][0]
    for k in range(len(rings) - 1):
        a, b = rings[k], rings[k + 1]
        ua, ub = us[k], us[k + 1]
        va = (sections[k][0] - y0) * v_scale
        vb = (sections[k + 1][0] - y0) * v_scale
        for i in range(n if closed else n - 1):
            j = i + 1 if i + 1 < len(ua) else 0
            m.face([a[i], a[j % n], b[j % n], b[i]], mat, [(ua[i], va), (ua[j], va), (ub[j], vb), (ub[i], vb)])
    if cap_start:
        f = m.face(list(reversed(rings[0])) if sections[0][0] < sections[-1][0] else rings[0], cap_start)
        box_uv(m, [f])
    if cap_end:
        f = m.face(rings[-1] if sections[0][0] < sections[-1][0] else list(reversed(rings[-1])), cap_end)
        box_uv(m, [f])
    return rings


def lathe(m, profile, mat_of, axis='y', seg=32, origin=(0, 0, 0), a0=0.0):
    """A surface of revolution: profile [(r, a), ...] (radius, distance along the axis) round the given axis. Faceted, it
    has sides() for its widest radius at most, a flat one on top (facing +z, or +y round the z axis)."""
    if FACET:
        seg = min(seg, sides(max(r for r, _ in profile)))
        a0 = math.pi / 2 - math.pi / seg
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


def catmull(points, per=8, keep=False):
    """A smooth path through the points (faceted, a third as many steps between them, unless keep)."""
    if FACET and not keep:
        per = max(1, per // 3)
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
    """A round pipe along a path; flare(t) scales the radius along it. Faceted, it is a bar (facet_bar), broken into
    straight runs where the path turns a corner (a square bar carried round a corner twists flat there)."""
    if FACET:
        pts = [Vector(p) for p in path]
        last = len(pts) - 1
        start = 0
        for i in range(1, last):
            if (pts[i] - pts[i - 1]).angle(pts[i + 1] - pts[i], 0.0) > math.radians(35):
                facet_bar(m, pts[start:i + 1], radius, mat, flare, start / last, i / last)
                start = i
        facet_bar(m, pts[start:], radius, mat, flare, start / last, 1.0)
        return
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


def facet_bar(m, pts, radius, mat, flare=None, t0=0.0, t1=1.0):
    """A faceted tube's run (tube): square, or eight-sided if it is thick, `radius` (THINNEST at least) from its middle
    to each flat side, one side on top where it runs level, its faces carried along without twisting; flare is read
    from t0 to t1 along it."""
    k = 4 if radius < 0.04 else 8
    reach = max(radius, THINNEST) / math.cos(math.pi / k)
    n = len(pts)

    def along(i):
        return (pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)]).normalized()
    d = along(0)
    a = d.cross(Vector((0, 0, 1)) if abs(d.z) < 0.9 else Vector((1, 0, 0))).normalized()
    rings = []
    for i, p in enumerate(pts):
        d = along(i)
        a = (a - d * a.dot(d)).normalized()
        b = d.cross(a)
        r = reach * (flare(t0 + (t1 - t0) * i / max(1, n - 1)) if flare else 1.0)
        rings.append([m.vert(p + (a * math.cos(math.pi / k + 2 * math.pi * j / k) +
                                  b * math.sin(math.pi / k + 2 * math.pi * j / k)) * r) for j in range(k)])
    for i in range(n - 1):
        for j in range(k):
            jj = (j + 1) % k
            m.face([rings[i][j], rings[i][jj], rings[i + 1][jj], rings[i + 1][j]], mat,
                   [(j / k, i / n), ((j + 1) / k, i / n), ((j + 1) / k, (i + 1) / n), (j / k, (i + 1) / n)])
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
    """Rounds an object's edges (faceted, one chamfer)."""
    mod = o.modifiers.new('bevel', 'BEVEL')
    mod.width = width
    mod.segments = 1 if FACET else segments
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


def door_dip(y):
    """How far the hull's edge is cut down at y for the door beside the front seat: a flat-bottomed U whose ends ease
    smoothly into the gunwale."""
    y0, y1, depth = DOOR

    def ease(x):
        x = min(1.0, max(0.0, x))
        return x * x * (3 - 2 * x)
    return depth * ease((y - y0) / 0.16) * ease((y1 - y) / 0.16)


def hull_at(y):
    """The hull's (half-width, gunwale, keel) at y, between the listed sections."""
    for (y0, *a), (y1, *b) in zip(HULL, HULL[1:]):
        if y1 <= y <= y0:
            k = (y0 - y) / (y0 - y1)
            return tuple(p + (q - p) * k for p, q in zip(a, b))
    return tuple(HULL[0][1:]) if y > HULL[0][0] else tuple(HULL[-1][1:])


def hull_edge(y):
    """The height of the hull's top edge at y: the gunwale, cut down at the doors."""
    return hull_at(y)[1] - door_dip(y)


def edge_angle(y, inset=0.0):
    """Where round a hull section (hull_section's t) its edge is, the section cut off at hull_edge: 0 at the
    gunwale."""
    hw, zt, zb = hull_at(y)
    zb += inset
    cut = hull_edge(y)
    if cut >= zt - 1e-6:
        return 0.0
    return math.asin(min(1.0, ((zt - cut) / (zt - zb)) ** (HULL_NZ / 2)))


def hull_cut(y, inset=0.0, count=72):
    """A hull section cut off at hull_edge, and where round the whole section (0 to 1, from the right gunwale) each of
    its points lies: the planks keep their places on a cut section and run on straight past the doors."""
    count = res(count, least=8)
    t0 = edge_angle(y, inset)
    pts = hull_section(y, *hull_at(y), inset=inset, count=count, t0=t0, t1=math.pi - t0)
    dense = hull_section(y, *hull_at(y), inset=inset, count=2000)
    run = perimeter_uv(dense, closed=False)
    ts = np.linspace(0.0, math.pi, 2001)
    us = [float(np.interp(t0 + (math.pi - 2 * t0) * i / count, ts, run)) for i in range(count + 1)]
    return pts, us


def hull_ys():
    """Where the hull is sectioned, front to back: the listed sections and enough more to shape the doors."""
    ys = {y for y, *_ in HULL} | {float(y) for y in np.linspace(DOOR[0], DOOR[1], res(15, least=5))}
    return sorted(ys, reverse=True)


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
        add_box(m, (s * 0.40, 0.10, 0.44), (0.07, 4.4, 0.11), 'chassis')
        # Leaf springs, dull aluminium like the rest of the running gear.
        for y in (FRONT_AXLE, REAR_AXLE):
            add_box(m, (s * TRACK * 0.78, y, 0.50), (0.06, 0.95, 0.04), 'aluminium_dull')
    add_box(m, (0, 2.32, 0.44), (0.88, 0.07, 0.10), 'chassis')
    add_box(m, (0, FRONT_AXLE, 0.44), (TRACK * 2 - 0.08, 0.07, 0.07), 'aluminium_dull')
    add_box(m, (0, REAR_AXLE, 0.46), (TRACK * 2 - 0.08, 0.08, 0.08), 'aluminium_dull')
    add_box(m, (0, REAR_AXLE, 0.42), (0.24, 0.22, 0.20), 'aluminium_dull')
    add_box(m, (0, 0.2, 0.40), (0.10, 3.6, 0.08), 'chassis')  # the propshaft
    return m.obj('chassis', smooth=30)


def radiator_outline(scale=1.0, count=72):
    """The radiator's face, (x, z) about the bonnet's axis: an egg, narrowing to its round top, widest low down and
    flat across its foot, where the GEN 11 plate hangs."""
    count = res(count, least=16, div=4)
    phase = math.pi / count if FACET else 0.0
    pts = []
    for i in range(count):
        t = math.pi / 2 - phase - 2 * math.pi * i / count
        c, sn = math.cos(t), math.sin(t)
        if sn >= 0:
            x, z = RADIATOR_W * c * (1 - 0.16 * sn), RADIATOR_TOP * sn
        else:
            x = RADIATOR_W * math.copysign(abs(c) ** (2 / 5.0), c)
            z = RADIATOR_BOT * math.copysign(abs(sn) ** (2 / 5.0), sn)
        pts.append((x * scale, BONNET_Z1 + z * scale))
    return pts


def build_radiator():
    """The radiator: a brass shell whose rim stands proud round a grey honeycomb core, and a filler cap and winged
    mascot on top."""
    zc = BONNET_Z1
    m = Mesh()
    r1 = BONNET_R1 - 0.01
    around = res(72, least=16, div=4)
    phase = math.pi / around if FACET else 0.0
    neck = [(r1 * math.cos(math.pi / 2 - phase - 2 * math.pi * i / around),
             zc + r1 * math.sin(math.pi / 2 - phase - 2 * math.pi * i / around)) for i in range(around)]
    shell = radiator_outline()
    rim_in = radiator_outline(0.86)
    loft(m, [(BONNET_FRONT - 0.005, neck), (BONNET_FRONT + 0.03, radiator_outline(0.97)), (BONNET_FRONT + 0.05, shell),
             (2.28, shell), (2.31, radiator_outline(0.97)), (2.31, rim_in), (2.295, radiator_outline(0.84))], 'brass')
    o = m.obj('radiator', smooth=50)
    # The core on its own, facing forward whatever Blender would make of a lone flat face (turned in, it bakes black).
    m = Mesh()
    core = radiator_outline(0.84)
    ring = [m.vert((x, 2.295, z)) for x, z in core]
    f = m.face(ring, 'honeycomb')
    f.normal_update()
    if f.normal.y < 0:
        f.normal_flip()
    for loop in f.loops:
        loop[m.uv].uv = (loop.vert.co.x * 2.2 + 0.5, (loop.vert.co.z - zc) * 2.2 + 0.5)
    m.obj('grille_core', recalc=False)
    m = Mesh()
    top = zc + RADIATOR_TOP
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
    # Faceted, as many sides as her brass band's lathe gives it, lined up with them (a flat on top).
    around = sides(BONNET_R0) if FACET else 72
    phase = math.pi / around if FACET else 0.0
    for y in np.linspace(BONNET_BACK, BONNET_FRONT, 7):
        r, zc = bonnet_ring(y)
        sections.append((y, [(r * math.cos(t), zc + r * math.sin(t)) for t in
                             (math.pi / 2 - phase - 2 * math.pi * i / around for i in range(around))]))
    loft(m, sections, 'aluminium', cap_start='walnut')
    o = m.obj('bonnet', smooth=50)
    m = Mesh()
    r, zc = bonnet_ring(BONNET_BACK)
    lathe(m, [(r - 0.005, BONNET_BACK - 0.01), (r + 0.018, BONNET_BACK), (r + 0.018, BONNET_BACK + 0.045),
              (r - 0.005, BONNET_BACK + 0.055)], lambda k: 'brass', axis='y', seg=72, origin=(0, 0, zc))
    # The strap, over the top from one side to the other.
    y = 1.80
    r, zc = bonnet_ring(y)
    arc = [((r + 0.008) * math.cos(t), zc + (r + 0.008) * math.sin(t)) for t in
           np.linspace(-0.35, math.pi + 0.35, res(40, least=12, div=3))]
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
    # The dashboard's dials, looking back at the driver, as out of an old aeroplane, set in the walnut board that closes
    # the front of the cockpit (build_hull's bulkhead, just behind the bonnet's back).
    dash = HULL[0][0] - 0.014
    m = Mesh()
    for x, z, rr in ((-0.22, 1.20, 0.045), (-0.06, 1.24, 0.038), (0.10, 1.20, 0.05), (0.34, 1.02, 0.04), (0.20, 1.06, 0.035)):
        lathe(m, [(0.0, 0.0), (rr + 0.008, 0.0), (rr + 0.008, -0.010), (rr, -0.012), (0.0, -0.008)],
              lambda k: 'dial' if k == 3 else 'brass', axis='y', seg=20, origin=(x, dash, z))
    m.obj('dashboard', smooth=40)
    # Needles on three of them, each its own part turning about its dial's middle (the game turns them): her speed on
    # the big one, her height above the sea on the left, the engine's revs in front of the driver. They point straight
    # up here; the game turns them from three quarters round to the left (nothing) clockwise to the right (full).
    for name, (x, z, rr) in (('needle_speed', (0.10, 1.20, 0.05)), ('needle_height', (-0.22, 1.20, 0.045)),
                             ('needle_revs', (0.34, 1.02, 0.04))):
        m = Mesh()
        length, tail, w = rr * 0.85, rr * 0.25, 0.0045
        outline = [(-w, -tail), (w, -tail), (0.0012, length), (-0.0012, length)]
        front = [m.vert((a, -0.0015, b)) for a, b in outline]
        back = [m.vert((a, 0.0015, b)) for a, b in outline]
        m.face(front, 'red')
        m.face(list(reversed(back)), 'red')
        for k in range(4):
            j = (k + 1) % 4
            m.face([front[k], back[k], back[j], front[j]], 'red')
        lathe(m, [(0.0, -0.004), (0.007, -0.004), (0.007, 0.002), (0.0, 0.002)], lambda k: 'brass', axis='y', seg=10)
        m.obj(name, location=(x, dash - 0.017, z), smooth=None, part=name)
    return o


def deck_z(x, y):
    """The deck's height at (x, y): the gunwale at its edges, crowned in the middle."""
    hw, zt, zb = hull_at(y)
    return zt + DECK_CROWN * max(0.0, 1.0 - (x / max(hw, 1e-3)) ** 2)


def deck_front(x):
    """Where the deck's front edge is at x: furthest back in the middle, curving forward to meet the gunwales."""
    hw = hull_at(DECK_SIDE)[0]
    return DECK_FRONT + (DECK_SIDE - DECK_FRONT) * min(1.0, (x / hw) ** 2)


def deck_outline(count=48):
    """The deck's edge in plan, anticlockwise from the right end of its front edge: down the right gunwale to the point
    of the stern, up the left one, and across the curved front edge."""
    count = res(count, least=8)
    ys = list(np.linspace(DECK_SIDE, HULL[-1][0], count))
    right = [(hull_at(y)[0], y) for y in ys]
    left = [(-x, y) for x, y in reversed(right)]
    hw = hull_at(DECK_SIDE)[0]
    front = [(x, deck_front(x)) for x in np.linspace(-hw, hw, count)][1:-1]
    return right + left + front


def ray_hit(c, d, poly):
    """Where the ray from c along d first leaves the closed polygon."""
    best = None
    for i in range(len(poly)):
        (ax, ay), (bx, by) = poly[i], poly[(i + 1) % len(poly)]
        ex, ey = bx - ax, by - ay
        den = d[0] * ey - d[1] * ex
        if abs(den) < 1e-12:
            continue
        t = ((ax - c[0]) * ey - (ay - c[1]) * ex) / den
        u = ((ax - c[0]) * d[1] - (ay - c[1]) * d[0]) / den
        if t > 1e-6 and -1e-9 <= u <= 1 + 1e-9 and (best is None or t < best):
            best = t
    return (c[0] + d[0] * best, c[1] + d[1] * best)


def well_point(t, grow=0.0):
    yc, a, b = WELL
    return ((a + grow) * math.cos(t), yc + (b + grow) * math.sin(t))


def build_hull():
    """The boat: a planked cedar skin, lined with red leather round the open front cockpit, decked over in planked cedar
    behind it to the point of the stern with the back seat's well let into the deck; a walnut capping along the
    gunwales, a brass strip down each side and a brass cap on the point of the stern."""
    outer, us = [], []
    for y in hull_ys():
        pts, u = hull_cut(y)
        outer.append((y, pts))
        us.append(u)
    m = Mesh()
    loft(m, outer, 'cedar', closed=False, v_scale=0.3, us=us)
    o = m.obj('hull', smooth=55)
    # The lining of the front cockpit, back to the deck.
    m = Mesh()
    ys = [y for y in hull_ys() if y > DECK_SIDE] + [DECK_SIDE, (DECK_SIDE + DECK_FRONT) / 2, DECK_FRONT]
    secs, us = [], []
    for y in ys:
        pts, u = hull_cut(y, HULL_SKIN)
        secs.append((y, list(reversed(pts))))
        us.append([1.0 - x for x in reversed(u)])
    loft(m, secs, 'leather_plain', closed=False, v_scale=2.0, us=us)
    m.obj('lining', smooth=55)
    # The deck: rings from the edge of the well out to the edge of the deck, every point on the crowned surface.
    m = Mesh()
    yc, a, b = WELL
    poly = deck_outline()
    n, rings = res(96, least=12), res(7, least=3)
    grid = []
    for i in range(n):
        t = 2 * math.pi * i / n
        ix, iy = well_point(t, 0.03)
        ox, oy = ray_hit((0.0, yc), (ix, iy - yc), poly)
        row = []
        for k in range(rings + 1):
            f = k / rings
            x, y = ix + (ox - ix) * f, iy + (oy - iy) * f
            row.append(m.vert((x, y, deck_z(x, y))))
        grid.append(row)
    for i in range(n):
        j = (i + 1) % n
        for k in range(rings):
            q = [grid[i][k], grid[j][k], grid[j][k + 1], grid[i][k + 1]]
            m.face(q, 'cedar', [(v.co.x / 1.5 + 0.5, v.co.y * 0.3) for v in q])
    # The deck's front edge drops into the cockpit behind the front seat, a walnut bulkhead inside the lining from the
    # deck down to the floor.
    hw = hull_at(DECK_SIDE)[0]
    edge = [(x, deck_front(x)) for x in np.linspace(-hw + 0.04, hw - 0.04, res(24))]
    rows = res(6, least=2)
    grid = []
    for x, y in edge:
        top = deck_z(x, y) - 0.004
        col = []
        for r in range(rows + 1):
            z = top + (FLOOR_Z - top) * r / rows
            inside = max(0.0, hull_half_width(y, z, HULL_SKIN) - 0.004)
            col.append(m.vert((math.copysign(min(abs(x), inside), x), y, z)))
        grid.append(col)
    for k in range(len(edge) - 1):
        for r in range(rows):
            m.face([grid[k][r + 1], grid[k + 1][r + 1], grid[k + 1][r], grid[k][r]], 'walnut')
    m.obj('deck', smooth=50)
    # The well: a walnut coaming round its lip, buttoned red leather down its sides, carpet at the bottom.
    m = Mesh()
    lip = [well_point(2 * math.pi * i / n, 0.03) for i in range(n)]
    tube(m, [Vector((x, y, deck_z(x, y) + 0.015)) for x, y in lip + lip[:1]], 0.028, 'walnut', seg=10)
    m.obj('coaming', smooth=50)
    m = Mesh()
    walls = []
    for k, f in enumerate(np.linspace(0.0, 1.0, 5)):
        ring = []
        for i in range(n):
            x, y = well_point(2 * math.pi * i / n, 0.03 - 0.04 * f)
            z = deck_z(*lip[i]) + (FLOOR_Z - deck_z(*lip[i])) * f
            ring.append(m.vert((x, y, z)))
        walls.append(ring)
    run = perimeter_uv(lip)
    for k in range(len(walls) - 1):
        for i in range(n):
            j = (i + 1) % n
            m.face([walls[k][i], walls[k][j], walls[k + 1][j], walls[k + 1][i]], 'leather',
                   [(run[i] * 7, k / 2), (run[i + 1] * 7, k / 2), (run[i + 1] * 7, (k + 1) / 2), (run[i] * 7, (k + 1) / 2)])
    f = m.face(list(walls[-1]), 'carpet')
    box_uv(m, [f])
    # The walls face in, towards the seat: left to itself Blender would face them out into the deck.
    m.obj('well', smooth=50, recalc=False)
    # The gunwale: walnut capping from the bow to the point of the stern, and a brass rubbing strip below it.
    m = Mesh()
    for s in (-1, 1):
        ys = hull_ys()
        path = []
        for y in ys:
            z = hull_edge(y)
            path.append((s * max(hull_half_width(y, z - 1e-4) - HULL_SKIN / 2, 0.0), y, z + 0.012))
        tube(m, catmull(path, 3), 0.022, 'walnut', seg=10)
        strip = []
        for y in [y for y, *_ in HULL][:-1]:
            hw, zt, zb = hull_at(y)
            z = zt - 0.24
            strip.append((s * (hull_half_width(y, z) + 0.006), y, z))
        tube(m, catmull(strip, 6), 0.009, 'brass', seg=8)
    y, hw, zt, zb = HULL[-1]
    ellipsoid(m, (0.0, y - 0.01, zt + 0.01), (0.03, 0.05, 0.04), 'brass', seg=12, rings=8)
    m.obj('gunwale', smooth=50)
    # The cockpit floor.
    m = Mesh()
    secs = []
    for y in np.linspace(0.58, DECK_FRONT, 10):
        w = hull_half_width(y, FLOOR_Z, HULL_SKIN) - 0.005
        secs.append((y, [(w, FLOOR_Z), (0.0, FLOOR_Z), (-w, FLOOR_Z)]))
    loft(m, secs, 'carpet', closed=False)
    m.obj('floor', smooth=None)
    # The bow is closed by a bulkhead behind the dashboard.
    m = Mesh()
    y, hw, zt, zb = HULL[0]
    pts = hull_section(y, hw, zt, zb, count=res(72, least=8) if FACET else 40)
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
        if not FACET:
            o.modifiers.new('smooth', 'WEIGHTED_NORMAL')
        return o

    # The front bench, as wide as the hull lets it be at its foot, and its buttoned back curving forward round the
    # sides with the deck's edge, drawn in at the bottom where the hull narrows.
    width = 2 * (hull_half_width(FRONT_SEAT_Y - 0.22, 0.70, HULL_SKIN) - 0.02)
    seat('seat_front', (0, FRONT_SEAT_Y, 0.78), (width, 0.44, 0.16))
    m = Mesh()
    hw = hull_at(DECK_SIDE)[0]
    xs = np.linspace(-hw + 0.10, hw - 0.10, res(25, least=7))
    rings = []
    for z in np.linspace(0.84, 1.40, 5):
        ring = []
        for x in xs:
            y = deck_front(x) + 0.08
            inside = hull_half_width(y, z, HULL_SKIN) - 0.07
            ring.append(m.vert((math.copysign(min(abs(x), inside), x), y, z)))
        rings.append(ring)
    run = perimeter_uv([(x, deck_front(x)) for x in xs], closed=False)
    for j in range(len(rings) - 1):
        for k in range(len(xs) - 1):
            m.face([rings[j][k], rings[j][k + 1], rings[j + 1][k + 1], rings[j + 1][k]], 'leather',
                   [(run[k] * 6, j / 2), (run[k + 1] * 6, j / 2), (run[k + 1] * 6, (j + 1) / 2), (run[k] * 6, (j + 1) / 2)])
    o = m.obj('seatback_front', smooth=60)
    solidify(o, 0.10, offset=0.0)
    m = Mesh()
    tube(m, catmull([(x, deck_front(x) + 0.08, 1.41) for x in (xs if FACET else xs[::4])], 4), 0.05, 'leather', seg=12)
    m.obj('seatroll_front', smooth=50)
    # The back seat: a cushion in the front of the well; the buttoned walls of the well are its back and arms. It is the
    # ejector: its own part, which the game throws up on two springs under it (each a coil a block tall, which the game
    # stretches to the seat's height; at rest they are squashed flat under it).
    m = Mesh()
    yc, a, b = WELL
    zb, zt = FLOOR_Z, 0.86
    rows = []
    for y in np.linspace(yc + b * 0.86, yc - b * 0.30, 9):
        w = (a - 0.03) * math.sqrt(max(0.0, 1 - ((y - yc) / (b - 0.02)) ** 2))
        rows.append([(w * math.cos(t), y) for t in np.linspace(0, math.pi, 13)])
    tops = [[m.vert((x, y, zt)) for x, y in r] for r in rows]
    bots = [[m.vert((x, y, zb)) for x, y in r] for r in rows]
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
    o = m.obj('seat_rear', smooth=40, part='seat_rear')
    bevel(o, 0.03, 3)
    for side, x in (('r', 0.17), ('l', -0.17)):
        m = Mesh()
        coil = [Vector((0.07 * math.cos(t), 0.07 * math.sin(t), t / (2 * math.pi * 7))) for t in
                np.linspace(0.0, 2 * math.pi * 7, 7 * 16 + 1)]
        tube(m, coil, 0.012, 'brass', seg=8)
        m.obj('spring_' + side, location=(x, REAR_SEAT_Y + 0.06, FLOOR_Z), smooth=50, part='spring_' + side)
    for name, p in (('seat_driver', (0.30, FRONT_SEAT_Y, 0.86)), ('seat_front_passenger', (-0.30, FRONT_SEAT_Y, 0.86)),
                    ('seat_rear_right', (0.20, REAR_SEAT_Y, 0.86)), ('seat_rear_left', (-0.20, REAR_SEAT_Y, 0.86))):
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
    around = 4 if FACET else 8
    prof = [(0.17 + 0.016 * math.cos(a), 0.016 * math.sin(a)) for a in
            np.linspace(0, 2 * math.pi, around + 1)[:-1] + (math.pi / around if FACET else 0.0)]
    rings = []
    seg = 12 if FACET else 36
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
    seg = sides(WHEEL_R) if FACET else 48
    a0 = math.pi / 2 - math.pi / seg if FACET else 0.0
    around = 8 if FACET else 12
    prof = []
    for k in range(around):
        phi = 2 * math.pi * k / around + (math.pi / around if FACET else 0.0)
        c, s = math.cos(phi), math.sin(phi)
        prof.append((0.40 + 0.06 * math.copysign(abs(c) ** 0.7, c), 0.058 * math.copysign(abs(s) ** 0.7, s)))
    rings = []
    for i in range(seg):
        t = a0 + 2 * math.pi * i / seg
        rings.append([m.vert((x, r * math.cos(t), r * math.sin(t))) for r, x in prof])
    for i in range(seg):
        j = (i + 1) % seg
        for k in range(len(prof)):
            l = (k + 1) % len(prof)
            m.face([rings[i][k], rings[i][l], rings[j][l], rings[j][k]], 'rubber',
                   [(i / seg, k / around), (i / seg, (k + 1) / around), ((i + 1) / seg, (k + 1) / around),
                    ((i + 1) / seg, k / around)])
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
    """The spare wheel, stood on the right-hand running board hard up against the side of the bonnet just behind the
    front wing, strapped to a brass bracket."""
    x, y, z = SPARE
    wheel_mesh(1).obj('spare', location=SPARE, smooth=45)
    m = Mesh()
    tube(m, [Vector((x - 0.07, y, z)), Vector((bonnet_ring(y)[0] - 0.03, y, z))], 0.022, 'brass', seg=10)
    # The strap over the top of the tyre, round from one side of the bracket to the other.
    arc = [(y + 0.47 * math.cos(t), z + 0.47 * math.sin(t)) for t in np.linspace(math.radians(60), math.radians(120), 10)]
    for k in range(len(arc) - 1):
        (y0, z0), (y1, z1) = arc[k], arc[k + 1]
        q = [m.vert((x - 0.035, y0, z0)), m.vert((x - 0.035, y1, z1)), m.vert((x + 0.035, y1, z1)), m.vert((x + 0.035, y0, z0))]
        m.face(q, 'strap', [(0, k / 9), (0, (k + 1) / 9), (1, (k + 1) / 9), (1, k / 9)])
    m.obj('spare_mount', smooth=40)


def guard_path(center, r, a0, a1, lead=(), tail=(), steps=16):
    steps = res(steps, least=5, div=3)
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
            cols = res(9, least=3, div=3)
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
        # Three brass vent grilles let into each board.
        for y in np.linspace(BOARD_FRONT - 0.35, BOARD_BACK + 0.35, 3):
            add_box(m, (s * 0.66, y, BOARD_Z + 0.022), (0.20, 0.30, 0.006), 'brass')
            for k in range(7):
                add_box(m, (s * 0.66, y - 0.12 + 0.04 * k, BOARD_Z + 0.026), (0.17, 0.012, 0.004), 'chassis')
        for y in (BOARD_FRONT - 0.05, BOARD_BACK + 0.05):
            add_box(m, (s * 0.50, y, (BOARD_Z + 0.42) / 2), (0.04, 0.05, BOARD_Z - 0.42), 'chassis')
    m.obj('boards', smooth=None)
    return o


def ellipsoid(m, center, radii, mat, rot=None, seg=16, rings=10):
    """A squashed sphere, turned by `rot` about its centre (faceted, with sides() round and half as many rings)."""
    if FACET:
        seg = min(seg, sides(max(radii)))
        rings = max(2, min(rings, seg // 2))
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


def spotlight(m, glass, center, r, depth):
    """A coach spotlight facing forward: a brass cone opening out to the front like a searchlight, a chrome reflector,
    a bulb and its glass, and a carrying handle over the top."""
    cx, cy, cz = center
    body = [(0.0, -depth * 0.5), (r * 0.42, -depth * 0.5), (r * 0.48, -depth * 0.4), (r * 0.82, depth * 0.25),
            (r, depth * 0.45), (r * 1.1, depth * 0.48), (r * 1.1, depth * 0.56), (r * 0.94, depth * 0.57)]
    lathe(m, body, lambda k: 'brass', axis='y', seg=28, origin=center)
    lathe(m, [(0.0, -depth * 0.3), (r * 0.4, -depth * 0.28), (r * 0.9, depth * 0.5)], lambda k: 'chrome', axis='y', seg=28,
          origin=center)
    ellipsoid(m, (cx, cy - depth * 0.1, cz), (r * 0.2, r * 0.24, r * 0.2), 'bulb_glow', seg=10, rings=6)
    lathe(glass, [(r * 0.94, depth * 0.56), (r * 0.6, depth * 0.6), (0.0, depth * 0.62)], lambda k: 'lens', axis='y',
          seg=28, origin=center)
    # The handle: a hoop over the top, fore and aft.
    hoop = [Vector((cx, cy - depth * 0.3 + depth * 0.6 * k, cz + r * 0.9 + 0.045 * math.sin(math.pi * k))) for k in
            np.linspace(0.0, 1.0, 9)]
    tube(m, hoop, 0.008, 'brass', seg=8)
    for end in (hoop[0], hoop[-1]):
        tube(m, [end, end - Vector((0, 0, 0.03))], 0.008, 'brass', seg=8)


def build_lamps():
    m = Mesh()
    glass = Mesh()
    # Two great brass headlamps either side of the radiator, set into its rim, on stalks from the front wings; and a
    # spotlight on each post of the windscreen.
    for s in (-1, 1):
        cx = s * 0.44
        tube(m, [Vector((cx, 2.27, 0.60)), Vector((cx, 2.27, 0.97))], 0.02, 'brass', seg=8)
        lathe(m, [(0.0, 0.0), (0.03, 0.0), (0.03, 0.02), (0.0, 0.02)], lambda k: 'brass', axis='z', seg=12,
              origin=(cx, 2.27, 0.95))
        lamp(m, glass, (cx, 2.30, 1.13), 0.165, 0.14)
        cl = s * 0.64
        y = BONNET_BACK - 0.05
        spotlight(m, glass, (cl, y + 0.02, 1.63), 0.085, 0.16)
        # Where the game shines their beams from at night: just inside each glass.
        tag = 'r' if s > 0 else 'l'
        empty('beam_lamp_' + tag, (cx, 2.42, 1.13))
        empty('beam_spot_' + tag, (cl, y + 0.11, 1.63))
        add_box(m, ((cl + s * 0.56) / 2, y, 1.60), (0.08, 0.02, 0.025), 'brass')
    m.obj('lamps', smooth=40)
    glass.obj('lamp_glass', smooth=40, part='glass')


def build_exhaust():
    """Four copper pipes out of the right of the bonnet, sweeping down over the front wing into one great flexible pipe
    that runs down outside the spare wheel and back along the running board to a brass fishtail by the rear wing."""
    m = Mesh()
    run = [(0.60, 1.56, 1.10), (0.71, 1.32, 1.00), (0.80, 1.12, 0.84), (0.83, 0.94, 0.68), (0.83, 0.50, 0.64),
           (0.83, -0.30, 0.64), (0.83, -0.82, 0.64)]
    main = catmull(run, 8)
    tube(m, main, 0.048, 'copper', seg=16)
    # Where the branches meet it: eight steps between its points, as the round renders take it.
    landing = catmull(run, 8, keep=True)
    for i in range(3, len(main) - 2, 2):
        t = (main[i + 1] - main[i - 1]).normalized()
        tube(m, [main[i] - t * 0.008, main[i] + t * 0.008], 0.054, 'copper', seg=16)
    a = math.radians(8)
    for k, (y, to) in enumerate(((1.64, 0), (1.53, 4), (1.42, 8), (1.31, 12))):
        r, zc = bonnet_ring(y)
        p0 = Vector((r * math.cos(a) - 0.01, y, zc + r * math.sin(a)))
        p1 = landing[min(to, len(landing) - 1)]
        mid = Vector((p0.x + 0.10, y - 0.02, p0.z + 0.03))
        tube(m, catmull([tuple(p0), tuple(mid), tuple((mid + p1) / 2 + Vector((0, 0, 0.04))), tuple(p1)], 6),
             0.026, 'copper', seg=12)
        lathe(m, [(0.034, -0.012), (0.034, 0.012)], lambda q: 'brass', axis='x', seg=12, origin=(p0.x, p0.y, p0.z))
    tip = catmull([(0.83, -0.82, 0.64), (0.83, -0.90, 0.635), (0.83, -0.98, 0.63)], 4)
    tube(m, tip, 0.048, 'brass', seg=16, flare=lambda t: 1.0 + 0.6 * t * t)
    empty('exhaust', (0.83, -1.02, 0.63))
    return m.obj('exhaust', smooth=50)


def build_horn():
    """The serpent horn: a brass snake from the rubber bulb by the driver's hand, down behind the spare wheel and along
    the side of the bonnet low over the front wing, well under the exhaust pipes, rearing up a little at the front with
    its jaws open."""
    m = Mesh()
    path = catmull([(0.43, 0.75, 1.28), (0.47, 0.90, 1.12), (0.48, 1.05, 0.95), (0.46, 1.25, 0.87), (0.45, 1.45, 0.86),
                    (0.45, 1.62, 0.89), (0.45, 1.72, 0.95)], 8)
    tube(m, path, 0.014, 'brass', seg=12, flare=lambda t: 1.0 + 1.1 * t ** 1.5)
    lathe(m, [(0.0, -0.05), (0.03, -0.045), (0.045, -0.02), (0.045, 0.02), (0.02, 0.04), (0.012, 0.05)],
          lambda k: 'bulb', axis='y', seg=20, origin=(0.42, 0.70, 1.31))
    head = Vector((0.45, 1.78, 0.97))
    ellipsoid(m, head + Vector((0, 0.0, 0.018)), (0.040, 0.075, 0.022), 'brass', rot=Matrix.Rotation(math.radians(22), 4, 'X'))
    ellipsoid(m, head + Vector((0, -0.005, -0.016)), (0.034, 0.065, 0.016), 'brass', rot=Matrix.Rotation(math.radians(-16), 4, 'X'))
    ellipsoid(m, head + Vector((0, 0.01, 0.0)), (0.026, 0.05, 0.02), 'bulb')
    for s in (-1, 1):
        ellipsoid(m, head + Vector((s * 0.03, -0.02, 0.04)), (0.010, 0.010, 0.010), 'eye', seg=8, rings=6)
    # Two brackets holding it to the side of the bonnet.
    for y, z in ((1.30, 0.865), (1.52, 0.862)):
        r, zc = bonnet_ring(y)
        side = math.sqrt(max(0.0, r * r - (z - zc) ** 2))
        add_box(m, ((side + 0.45) / 2, y, z), (0.45 - side + 0.02, 0.025, 0.025), 'brass')
    return m.obj('horn', smooth=45)


def build_levers():
    """The gear lever and the handbrake outside the body by the driver's door, in a brass quadrant; each its own part,
    pivoting at its foot (the game moves them as she is driven)."""
    y = (DOOR[0] + DOOR[1]) / 2
    x = hull_at(y)[0] + 0.06
    zq = 0.82     # the quadrant's middle, up clear of the exhaust along the running board
    m = Mesh()
    add_box(m, (x, y, zq), (0.03, 0.36, 0.13), 'brass')
    for k in range(7):
        add_box(m, (x + 0.017, y - 0.15 + 0.05 * k, zq + 0.07), (0.006, 0.012, 0.02), 'chassis')
    # The bracket back to the hull's side.
    inner = hull_half_width(y, zq) - 0.02
    for dy in (-0.12, 0.12):
        add_box(m, ((x + inner) / 2, y + dy, zq), (x - inner, 0.03, 0.04), 'brass')
    m.obj('quadrant', smooth=None)
    for name, dy, height, grip in (('lever_gear', 0.07, 0.62, 'knob'), ('lever_brake', -0.07, 0.70, 'grip')):
        m = Mesh()
        tube(m, [Vector((0, 0, 0)), Vector((0.015, 0, height))], 0.012, 'brass', seg=8)
        if grip == 'knob':
            ellipsoid(m, (0.015, 0, height + 0.02), (0.03, 0.03, 0.03), 'black', seg=12, rings=8)
        else:
            tube(m, [Vector((0.015, 0, height - 0.10)), Vector((0.015, 0, height + 0.01))], 0.019, 'leather_plain', seg=10)
        m.obj(name, location=(x + 0.015, y + dy, zq - 0.04), smooth=40, part=name)


def build_plates():
    """GEN 11, under the radiator and on the back of the hamper, and the starting handle."""
    m = Mesh()
    plate_z = BONNET_Z1 - RADIATOR_BOT - 0.075
    add_box(m, (0, 2.315, plate_z), (0.46, 0.012, 0.13), 'plate')
    add_box(m, (0, 2.30, plate_z + 0.075), (0.30, 0.03, 0.02), 'brass')
    add_box(m, (0, -3.175, 0.79), (0.50, 0.012, 0.13), 'plate')
    tube(m, [Vector((0, 2.30, 0.42)), Vector((0, 2.43, 0.42))], 0.014, 'chassis', seg=8)
    m.obj('plates', smooth=None)
    # The starting handle, turning on its shaft as she is started.
    m = Mesh()
    tube(m, [Vector((0, 0, 0)), Vector((0, 0, -0.11))], 0.012, 'chassis', seg=8)
    tube(m, [Vector((0, 0, -0.11)), Vector((0, 0.08, -0.11))], 0.016, 'brass', seg=8)
    m.obj('crank', location=(0, 2.44, 0.42), smooth=None, part='crank')
    text_object('GEN 11', 0.085, (0, 2.322, plate_z), (math.radians(90), 0, math.radians(180)), 'letters')
    text_object('GEN 11', 0.085, (0, -3.182, 0.79), (math.radians(90), 0, 0), 'letters')


def wedge(m, length, half_angle, mat, spar=False, scallop=0.1, rib=True, inner=None):
    """One panel of a fan: a wedge out along +X from its hinge, its tip cut in between its corners (overlapping its
    neighbours, the open fan is one striped sail with a scalloped edge, like an umbrella's), a rib down the middle. The
    first panel of a big wing carries the red box spar down its leading edge. inner, if given, is how far out the panel
    starts at each angle from its middle (radians): a fan cut off along a line short of its hinge."""
    a = math.radians(half_angle)
    cols = 7
    grid = []
    for j in range(9):
        row = []
        for k in range(cols):
            t = -1 + 2 * k / (cols - 1)
            ang = a * t
            r0 = inner(ang) if inner else 0.04
            r = r0 + (length - r0) * j / 8
            rr = r * (1 - scallop * (1 - t * t) * (j / 8) ** 3)
            row.append(m.vert((rr * math.cos(ang), rr * math.sin(ang), 0.0)))
        grid.append(row)
    for j in range(len(grid) - 1):
        for k in range(cols - 1):
            m.face([grid[j][k], grid[j + 1][k], grid[j + 1][k + 1], grid[j][k + 1]], mat,
                   [(j / 8, k / 6), ((j + 1) / 8, k / 6), ((j + 1) / 8, (k + 1) / 6), (j / 8, (k + 1) / 6)])
    # Ribs: down the middle, or along its leading seam (and its trailing one, for the last panel of a fan), so that
    # the fan's ribs are where its points are and the panels between them read as one sail.
    for ang in ([0.0] if rib is True else rib or []):
        d = Vector((math.cos(ang), math.sin(ang), 0.0))
        tube(m, [d * 0.02 + Vector((0, 0, 0.005)), d * length * 0.97 + Vector((0, 0, 0.005))], 0.007, 'brass', seg=6)
    if spar:
        d = Vector((math.cos(a), math.sin(a), 0.0))
        mid = d * (length / 2 + 0.02)
        add_box(m, (mid.x, mid.y, 0.025), (length - 0.02, 0.075, 0.05), 'red', rot=Matrix.Rotation(a, 4, 'Z'))
        return d * length + Vector((0, 0, 0.05))
    return None


def fan(name, side, spec, colors, coll, spar=False):
    """A fan of wedges on a hinge; panel i is named <name>_<s>_<i> (s: r, l, or c for one in the middle), its origin on
    the hinge, with its open and folded angles (degrees, 0 straight out to her right or the side it is on, positive
    forward) and dihedral as properties. A panel folds the short way round."""
    objs = []
    blades = spec['blades']
    step = spec['spread'] / max(1, blades - 1)
    hx, hy, hz = spec['hinge']
    tip = None
    for i in range(blades):
        m = Mesh()
        # A fan cut off along a straight line across it, root in front of the hinge.
        off = math.radians(spec['open_from'] - step * i - (spec['open_from'] - spec['spread'] / 2))
        inner = (lambda ang, off=off: spec['root'] / math.cos(off + ang)) if spec.get('root') else None
        rib = spec.get('ribs', True)
        if rib == 'seams':
            a = math.radians(step / 2)
            rib = ([] if spar and i == 0 else [a]) + ([-a] if i == blades - 1 else [])
        t = wedge(m, spec['length'], step / 2 + (0.4 if spec.get('ribs') == 'seams' else 1.0), colors[i % 2],
                  spar=spar and i == 0, scallop=spec['scallop'], rib=rib, inner=inner)
        if t is not None:
            tip = t
        tag = '%s_%s_%d' % (name, 'c' if side == 0 else 'r' if side > 0 else 'l', i)
        o = m.obj(tag, coll=coll, location=((side or 1) * hx, hy, hz + spec['layer'] * i), smooth=None, part=tag)
        solidify(o, 0.008, offset=0.0)
        if side < 0:
            o.scale = (-1, 1, 1)
        o['open_yaw'] = open_yaw = spec['open_from'] - step * i
        # Folded, the fan closes up onto its back panel, keeping a little of its spread so the pleats keep their order.
        back = spec['open_from'] - spec['spread']
        o['fold_yaw'] = back + (open_yaw - back) * spec['closed']
        o['pleat'] = 1 if i % 2 == 0 else -1
        o['closed'], o['drop'] = spec['closed'], spec['drop']
        o['hinge_z'] = hz + spec['layer'] * i
        o['tuck'] = spec['tuck']
        o['dihedral'] = spec['dihedral']
        o['tilt_along'], o['tilt_across'] = spec.get('tilt', (0, 0))
        objs.append(o)
    return objs, tip


def straight_fan(name, spec, colors, coll):
    """A fan cut off along a straight root line (NOSEFAN, TAILFAN): one section between each pair of points, each its
    own panel named <name>_c_<i> from her left, turning about the point behind the line where the sections' edges
    meet (their hinge), with the same properties as fan()'s. Folded, the fan is closed up onto its middle, pleated,
    and drawn in."""
    lx, ly, lz = spec['line']
    heading = spec['heading']
    w0 = spec['root']
    tips2 = spec['tips']
    # The hinge: where the outer edges, from the ends of the line through the outer points, cross.
    (side0, reach0) = tips2[0]
    d = w0 * reach0 / (abs(side0) - w0)
    # In the fan's own frame: x straight out from the hinge, y to her left (mirrored for a fan pointing back).
    flip = 1.0 if heading > 0 else -1.0
    tips = [Vector((d + reach, side * flip)) for side, reach in tips2]
    roots = [Vector((d, p.y * d / p.x)) for p in tips]
    n = len(tips) - 1
    objs = []
    for i in range(n):
        ta, tb, ra, rb = tips[i], tips[i + 1], roots[i], roots[i + 1]
        mid = math.atan2(ta.y, ta.x) / 2 + math.atan2(tb.y, tb.x) / 2
        back = Matrix.Rotation(-mid, 2)
        chord = (tb - ta).length
        m = Mesh()
        grid = []
        for j in range(9):
            row = []
            for k in range(7):
                t = k / 6
                inner = ra.lerp(rb, t)
                edge = ta.lerp(tb, t)
                # The bat scallop: the edge drawn in towards the hinge between the points.
                edge = edge - edge.normalized() * spec['scallop'] * chord * 4 * t * (1 - t)
                p = back @ inner.lerp(edge, j / 8)
                row.append(m.vert((p.x, p.y, 0.0)))
            grid.append(row)
        for j in range(8):
            for k in range(6):
                m.face([grid[j][k], grid[j + 1][k], grid[j + 1][k + 1], grid[j][k + 1]], colors[i % 2],
                       [(j / 8, k / 6), ((j + 1) / 8, k / 6), ((j + 1) / 8, (k + 1) / 6), (j / 8, (k + 1) / 6)])
        if spec.get('ribs'):
            seams = [(ra, ta)] + ([(rb, tb)] if i == n - 1 else [])
            for r0, r1 in seams:
                a, b = back @ r0, back @ r1
                tube(m, [Vector((a.x, a.y, 0.006)), Vector((b.x * 0.985, b.y * 0.985, 0.006))], 0.006, 'chassis', seg=6)
        tag = '%s_c_%d' % (name, i)
        hinge = (lx, ly - d * flip, lz + spec['layer'] * i)
        o = m.obj(tag, coll=coll, location=hinge, smooth=None, part=tag)
        solidify(o, 0.008, offset=0.0)
        o['open_yaw'] = heading + math.degrees(mid)
        o['fold_yaw'] = heading + math.degrees(mid) * spec['closed']
        o['pleat'] = 1 if i % 2 == 0 else -1
        o['closed'], o['drop'] = spec['closed'], spec['drop']
        o['hinge_z'] = hinge[2]
        o['tuck'] = spec['tuck']
        o['dihedral'] = 0.0
        o['tilt_along'], o['tilt_across'] = spec.get('tilt', (0, 0))
        objs.append(o)
    return objs


def build_rotor(wing0, tip, side):
    """The mast at the end of a wing's spar and the two-bladed propeller turning flat on top of it. Both hang off the
    wing's first panel (in its own frame): the mast lies along the spar when the wing is folded and stands up as it
    opens, and the propeller unfolds at its head."""
    tag = 'r' if side > 0 else 'l'
    m = Mesh()
    tube(m, [Vector((0, 0, -0.02)), Vector((0, 0, MAST_H))], 0.018, 'black', seg=10)
    lathe(m, [(0.0, -0.05), (0.045, -0.05), (0.04, 0.02), (0.0, 0.03)], lambda k: 'black', axis='z', seg=12)
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
                m.face([a, b, c, d] if sign > 0 else [d, c, b, a], 'black')
        m.face(rings[-1] if sign > 0 else list(reversed(rings[-1])), 'black')
    rotor = m.obj('rotor_' + tag, coll='wings', smooth=35, part='rotor_' + tag)
    rotor.parent = mast
    rotor.location = (0, 0, MAST_H)
    return mast, rotor


def build_hamper():
    """A wicker hamper, strapped on a brass rack out from the chassis under the point of the stern."""
    m = Mesh()
    add_box(m, (0, -2.98, 0.80), (0.62, 0.38, 0.28), 'wicker', uv_scale=1.6)
    add_box(m, (0, -2.98, 0.95), (0.64, 0.40, 0.03), 'wicker', uv_scale=1.6)
    for x in (-0.22, 0.22):
        add_box(m, (x, -2.98, 0.81), (0.035, 0.39, 0.31), 'strap')
    m.obj('hamper', smooth=None, part='hamper')
    m = Mesh()
    for x in (-0.26, 0.26):
        tube(m, [Vector((x, -2.05, 0.46)), Vector((x, -2.60, 0.64)), Vector((x, -3.20, 0.64))], 0.014, 'brass', seg=8)
    tube(m, [Vector((-0.26, -3.20, 0.64)), Vector((0.26, -3.20, 0.64))], 0.014, 'brass', seg=8)
    m.obj('rack', smooth=40)


def build_tailprop():
    """The pusher propeller behind the point of the stern: a shaft out of the top of the stern to a gearbox, braced in a
    triangle down to the hull underneath, and the little wooden propeller on its end, which unfolds and turns in
    flight (its own part, turning about her length)."""
    px, py, pz = TAILPROP
    m = Mesh()
    tube(m, [Vector((0, -3.00, pz)), Vector((0, py + 0.03, pz))], 0.018, 'brass', seg=10)
    lathe(m, [(0.0, -2.96), (0.04, -2.97), (0.05, -3.02), (0.045, -3.08), (0.025, -3.10)], lambda k: 'chassis', axis='y',
          seg=14, origin=(0, 0, pz))
    lathe(m, [(0.022, py + 0.10), (0.04, py + 0.08), (0.04, py + 0.03), (0.022, py + 0.02)], lambda k: 'brass', axis='y',
          seg=12, origin=(0, 0, pz))
    for s in (-1, 1):
        tube(m, [Vector((0, py + 0.06, pz - 0.01)), Vector((s * 0.09, -2.80, 1.00))], 0.010, 'chassis', seg=8)
    tube(m, [Vector((0, py + 0.06, pz - 0.01)), Vector((0, -3.02, 1.16))], 0.010, 'chassis', seg=8)
    tube(m, [Vector((-0.09, -2.80, 1.00)), Vector((0.09, -2.80, 1.00))], 0.010, 'chassis', seg=8)
    m.obj('tailprop_mount', smooth=40)
    m = Mesh()
    lathe(m, [(0.0, 0.02), (0.03, 0.02), (0.035, -0.02), (0.02, -0.06), (0.0, -0.08)], lambda k: 'brass', axis='y', seg=14)
    for sign in (1, -1):
        rings = []
        for r in np.linspace(0.025, TAILPROP_R, 8):
            t = (r - 0.025) / (TAILPROP_R - 0.025)
            chord = 0.04 + 0.035 * math.sin(math.pi * min(1.0, t * 1.1))
            twist = math.radians(30 - 18 * t)
            ring = []
            for c, th in ((-chord / 2, -0.007), (chord / 2, -0.007), (chord / 2, 0.007), (-chord / 2, 0.007)):
                x = c * math.cos(twist) - th * math.sin(twist)
                y = c * math.sin(twist) + th * math.cos(twist)
                ring.append(m.vert((x * sign, y, r * sign)))
            rings.append(ring)
        for i in range(len(rings) - 1):
            for k in range(4):
                j = (k + 1) % 4
                q = [rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]]
                m.face(q if sign > 0 else list(reversed(q)), 'prop')
        m.face(rings[-1] if sign > 0 else list(reversed(rings[-1])), 'prop')
    return m.obj('tailprop', coll='wings', location=TAILPROP, smooth=35, part='tailprop')


def build_wings():
    objs = []
    for s in (-1, 1):
        panels, tip = fan('wing', s, WING, ('wing_red', 'wing_yellow'), 'wings', spar=True)
        objs += panels
        build_rotor(panels[0], tip, s)
    objs += straight_fan('nosefan', NOSEFAN, ('wing_yellow', 'wing_red'), 'wings')
    objs += straight_fan('tailfan', TAILFAN, ('wing_red', 'wing_yellow'), 'wings')
    build_tailprop()
    return objs


def build_screw():
    m = Mesh()
    tube(m, [Vector((0, 0.0, 0)), Vector((0, 0.36, 0.30))], 0.014, 'brass', seg=8)
    lathe(m, [(0.0, -0.06), (0.03, -0.05), (0.035, 0.0), (0.02, 0.03), (0.0, 0.035)], lambda k: 'brass', axis='y', seg=16)
    for k in range(3):
        a = 2 * math.pi * k / 3
        R = Matrix.Rotation(a, 4, 'Y') @ Matrix.Rotation(math.radians(28), 4, 'Z')
        add_box(m, (0.0, 0.0, 0.10), (0.07, 0.01, 0.13), 'brass', matrix=R)
    return m.obj('screw', coll='floats', location=(0, -3.68, 0.26), smooth=40, part='screw')


def raft_outline(t):
    """The raft's edge in plan at angle t (0 at her bow): pointed at both ends, full in the middle, its edge waved."""
    w, l, yc = RAFT['half_width'], RAFT['half_length'], RAFT['centre']
    c, sn = math.cos(t), math.sin(t)
    x = math.copysign(w * (sn * sn) ** 0.72, sn)
    y = yc + l * c
    wave = 0.0 if FACET else RAFT['waves'] * (math.sin(17 * t) + 0.45 * math.sin(41 * t + 1.3))
    # Push the edge in and out along its own normal (roughly away from the middle).
    nx, ny = x / (w * w), (y - yc) / (l * l)
    k = math.hypot(nx, ny) or 1.0
    return x + wave * nx / k, y + wave * ny / k


def build_float():
    """The great pink raft she blows up under and round herself on the water: flat on top, pointed at both ends, its
    edge waved and puffed, dark underneath."""
    yc, bottom, top = RAFT['centre'], RAFT['bottom'], RAFT['top']
    n = 48 if FACET else 160
    edge = [raft_outline(2 * math.pi * i / n) for i in range(n)]
    # Rings in from the edge on top, round the puffed edge, and back in underneath.
    profile = [(0.0, top), (0.35, top), (0.65, top), (0.85, top - 0.005), (0.95, top - 0.02), (1.0, top - 0.06),
               (1.025, top - 0.13), (1.02, (top + bottom) / 2 - 0.02), (0.99, bottom + 0.03), (0.94, bottom), (0.0, bottom)]
    m = Mesh()
    rings = []
    for f, z in profile:
        rings.append([m.vert((x * f, yc + (y - yc) * f, z)) for x, y in edge])
    w, l = RAFT['half_width'], RAFT['half_length']
    for k in range(len(rings) - 1):
        mat = 'float' if k < 5 else 'float_side'
        for i in range(n):
            j = (i + 1) % n
            q = [rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]]
            m.face(q, mat, [(min(1.0, abs(v.co.x) / w), (v.co.y - (yc - l)) / (2 * l)) for v in q])
    return m.obj('float', coll='floats', location=(0.0, 0.0, 0.0), smooth=40, part='float')


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
    build_hamper()
    build_wings()
    build_screw()
    build_float()


# --- poses -----------------------------------------------------------------------------------------

def fan_rest(o):
    """The plane a fan panel swings in, as a rotation: tilted about her length, then across her (mirrored on her
    left)."""
    r = (Matrix.Rotation(math.radians(o.get('tilt_along', 0.0)), 3, 'Y')
         @ Matrix.Rotation(math.radians(o.get('tilt_across', 0.0)), 3, 'X'))
    if side_of(o) < 0:
        m = Matrix.Diagonal((-1, 1, 1))
        r = m @ r @ m
    return r


def side_of(o):
    """1 for a part on her right, -1 on her left, from its name (its world matrix is stale until Blender updates it)."""
    return -1 if '_l_' in o.name + '_' else 1


def pose(mode, spin=0.0, wings=None, lift=0.0):
    """'road', 'flying' or 'water': what the game's animation does, for the renders and exports; wings, if given, is
    how far the fans are out (0 to 1, as the game's wing opening), to show them part open, and lift how far the back
    seat stands up on its springs (the ejector)."""
    fly = mode == 'flying'
    wet = mode == 'water'
    if wings is None:
        wings = 1.0 if fly else 0.0

    def clamp(x):
        return min(1.0, max(0.0, x))

    for o in bpy.data.objects:
        part = o.get('part', '')
        if 'pleat' in o:
            # As ChittyRenderer.poseFan: in the fan's plane, tipped up about her length, turned out, and pleated about
            # its own middle.
            side = side_of(o)
            opening = clamp(wings / 0.92)
            opening = opening * opening * (3 - 2 * opening)
            out = clamp(wings / 0.6)
            out = out * out * (3 - 2 * out)
            pleat = math.acos(clamp(o['closed'] + (1.0 - o['closed']) * opening))
            yaw = math.radians(o['fold_yaw'] + (o['open_yaw'] - o['fold_yaw']) * opening)
            swing = (Matrix.Rotation(-math.radians(o['dihedral']) * side * clamp(opening), 3, 'Y')
                     @ Matrix.Rotation(yaw * side, 3, 'Z') @ Matrix.Rotation(-pleat * o['pleat'], 3, 'X'))
            o.rotation_mode = 'QUATERNION'
            o.rotation_quaternion = (fan_rest(o) @ swing).to_quaternion()
            o.location.z = o['hinge_z'] - o['drop'] * math.sin(pleat)
            k = o['tuck'] + (1.0 - o['tuck']) * out
            o.scale = (k * side, k, 1)
        elif part.startswith('mast_'):
            # Folded, it lies along the spar towards the hinge.
            d = o.location.normalized()
            axis = Vector((0, 0, 1)).cross(-d).normalized()
            o.rotation_mode = 'QUATERNION'
            o.rotation_quaternion = Matrix.Rotation(0 if fly else math.pi / 2, 4, axis).to_quaternion()
        elif part.startswith('needle_'):
            reading = {'needle_speed': 0.6, 'needle_height': 0.35, 'needle_revs': 0.75}[part] if fly else 0.0
            o.rotation_euler = (0, math.radians(-135 + 270 * reading), 0)
        elif part == 'seat_rear':
            o.location.z = lift
        elif part.startswith('spring_'):
            o.hide_render = o.hide_viewport = lift < 0.02
            o.scale = (1, 1, max(lift, 0.001))
        elif part.startswith('rotor_'):
            o.hide_render = o.hide_viewport = not fly
            o.rotation_euler = (0, 0, spin * 2.3)
        elif part == 'tailprop':
            o.hide_render = o.hide_viewport = not fly
            o.rotation_euler = (0, spin * 3.1, 0)
        elif part.startswith('wheel_'):
            # On the water they turn sideways and lie flat on the raft, hub caps up.
            side = 1 if o.name.endswith('r') else -1
            o.rotation_euler = (spin, -math.radians(90) * side if wet else 0, 0)
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

GAME_MESH = 'chitty/src/client/resources/assets/shootingstar/meshes/chitty.cbm'
GAME_TEXTURE = 'chitty/src/client/resources/assets/shootingstar/textures/entity/chitty.png'
ITEM_ICON = 'chitty/src/main/resources/assets/shootingstar/textures/item/chitty.png'

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
# Whether the game lights every part, each face by which way it faces, as it lights Minecraft's own things (the
# airship): then the bake holds only every part's colours, a little darker in its nooks, and every part keeps its
# normals.
LIT_ALL = False
# With LIT_ALL, how dark the most shut-in nook is (1: not darkened at all).
SHUT_IN = 0.45
# Polished metal the game shines itself, as you look at it (ChittyShine): its bake carries only how shut in it is (the
# louvres' slots, the radiator's rim), and its normals stay, for the reflection.
SHINE = {'aluminium': 1, 'brass': 2, 'chrome': 3, 'copper': 4, 'aluminium_dull': 5}


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
            o.rotation_mode = 'QUATERNION'
            o.rotation_quaternion = fan_rest(o).to_quaternion()
            o.scale = (side, 1, 1)
            if 'hinge_z' in o:
                o.location.z = o['hinge_z']
        elif part.startswith('mast_'):
            o.rotation_mode = 'QUATERNION'
            o.rotation_quaternion = (1, 0, 0, 0)
        elif part.startswith(('wheel_', 'rotor_', 'lever_', 'needle_')) or part in ('screw', 'tailprop', 'crank'):
            o.rotation_euler = (0, 0, 0)
        elif part == 'seat_rear':
            o.location.z = 0.0
        elif part.startswith('spring_'):
            o.scale = (1, 1, 1)
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


def mirror_of(o):
    """-1 if the fan a part hangs off is mirrored (on her left), else 1."""
    while o.parent is not None:
        o = o.parent
    return -1 if o.scale.x < 0 else 1


def mat_name(me, poly):
    return me.materials[poly.material_index].name if me.materials else 'chassis'


def bake_worlds():
    """A sky for the bake: bright overhead, brightest at the horizon, a darker warm ground; and an even grey one for
    the parts the game lights itself."""
    worlds = {}
    for name, stops in (('sky', [(0.0, (0.20, 0.18, 0.15)), (0.47, (0.30, 0.27, 0.23)), (0.50, (1.10, 1.05, 0.98)),
                                 (0.60, (0.85, 0.88, 0.94)), (1.0, (0.48, 0.56, 0.74))]),
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
                'wing_': 0.32, 'nosefan_': 0.45, 'tailfan_': 0.45, 'mast_': 0.6, 'rotor_': 0.6, 'tailprop': 0.8,
                'spring_': 0.5, 'needle_': 1.3,
                'plate_text': 1.6,
                'plates': 1.3, 'radiator': 1.3, 'grille_core': 1.3, 'hamper': 1.0, 'lever_': 1.3, 'mascot': 1.3, 'lamps': 1.3, 'horn': 1.3, 'dashboard': 1.3,
                'bonnet': 1.15, 'hull': 1.15}


def texel_weight(name):
    for key, w in TEXEL_WEIGHT.items():
        if name == key or (key.endswith('_') and name.startswith(key)):
            return w
    return 1.0


# How the atlas packer may turn each island: None for any way it likes; 'AXIS_ALIGNED' squares each island's edges with
# the atlas, so that a texture drawn pixelated has its pixels in rows along a part's edges, as Minecraft's do.
PACK_ROTATE = None

# Objects whose own UVs are kept for the bake rather than unwrapped afresh (the bake layer starts as a copy of them):
# a surface whose painting needs them, such as the airship's envelope, cylindrical with her arms on its flanks.
KEEP_UV = ()


def unwrap(objs):
    """Each object unwrapped on its own (or its own UVs kept, KEEP_UV), then all their islands scaled alike (by area,
    then by TEXEL_WEIGHT) and packed into one square."""
    view = bpy.context.view_layer
    for o in objs:
        if o.name.split(':', 1)[1] in KEEP_UV:
            continue
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
    turn = {'rotate_method': PACK_ROTATE} if PACK_ROTATE else {}
    bpy.ops.uv.pack_islands(rotate=True, **turn, margin_method='FRACTION', margin=float(os.environ.get('CHITTY_UV_MARGIN', '0.0012')), shape_method=os.environ.get('CHITTY_UV_SHAPE', 'CONCAVE'))
    bpy.ops.object.mode_set(mode='OBJECT')


def joined(objs):
    """The objects joined into the first of them (their UVs and materials kept)."""
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    if len(objs) > 1:
        bpy.ops.object.join()
    return objs[0]


def bake_pass(name, objs, world, sun, visible, kind='COMBINED'):
    """Bakes the objects' look into a fresh image (kind DIFFUSE: their colours alone); `visible` also cast shadows
    and reflect."""
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
    colours = {'pass_filter': {'COLOR'}} if kind == 'DIFFUSE' else {}
    bpy.ops.object.bake(type=kind, **colours, margin=0, use_clear=False)
    # Through the same view transform as the renders, out to 8 bits.
    path = os.path.join(bpy.app.tempdir or '/tmp', 'chitty_bake_%s.png' % name)
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    img.save_render(path, scene=scene)
    out = np.array(Image.open(path).convert('RGBA')).astype(np.float32) / 255.0
    print('  baked %-6s %5.1f%% of the atlas' % (name, (out[..., 3] > 0.5).mean() * 100))
    return out


def plain_metal():
    """Every material made plain (not metal) for a bake of colours, as it was kept to put back (restore_metal)."""
    kept = []
    for m in MATS.values():
        nt = m.node_tree
        b = nt.nodes.get('Principled BSDF')
        if b is None:
            continue
        socket = b.inputs['Metallic']
        source = socket.links[0].from_socket if socket.is_linked else None
        kept.append((nt, socket, socket.default_value, source))
        if source is not None:
            nt.links.remove(socket.links[0])
        socket.default_value = 0.0
    return kept


def restore_metal(kept):
    for nt, socket, value, source in kept:
        socket.default_value = value
        if source is not None:
            nt.links.new(source, socket)


def bake_occlusion(groups, world, sun):
    """How open each texel is to the sky (1) or shut in (towards 0), for the polished metal the game shines."""
    scene = bpy.context.scene
    img = bpy.data.images.new('bake_ao', BAKE_SIZE, BAKE_SIZE, alpha=True, float_buffer=True)
    img.generated_color = (0, 0, 0, 0)
    for m in MATS.values():
        node = m.node_tree.nodes.get('bake_target')
        node.image = img
        m.node_tree.nodes.active = node
    scene.world = world
    world.light_settings.distance = 0.35
    sun.hide_render = True
    samples = scene.cycles.samples
    scene.cycles.samples = 64
    for obj, visible in groups:
        show = {obj} | set(visible)
        for o in scene.objects:
            if o.type in ('MESH', 'CURVE', 'FONT'):
                o.hide_render = o not in show
        bpy.ops.object.select_all(action='DESELECT')
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.bake(type='AO', margin=0, use_clear=False)
    scene.cycles.samples = samples
    px = np.array(img.pixels[:], np.float32).reshape(BAKE_SIZE, BAKE_SIZE, 4)[::-1]
    return px[..., 0]


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
    nx, ny, nz, flags (bit 0: glows; bits 1-3: the polished metal it is, SHINE). Triangles repeat their last corner. Then int32 marker count and per marker a name and
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
    # Where the polished metal lies in the atlas: there the texture holds its occlusion, not a painted reflection.
    metal = Image.new('L', (BAKE_SIZE, BAKE_SIZE), 0)
    draw = ImageDraw.Draw(metal)
    for name, c in copies.items():
        me = c.data
        uv = copies_uv[name]
        for poly in me.polygons:
            if mat_name(me, poly) in SHINE:
                draw.polygon([(uv[li][0] * BAKE_SIZE, (1.0 - uv[li][1]) * BAKE_SIZE) for li in poly.loop_indices], fill=255)
    metal = np.array(metal.filter(ImageFilter.MaxFilter(3))) > 127

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
    # Gentle: straight overhead, a strong sun washes out everything that faces up (the deck went pale grey).
    sun.data.energy = 0.9
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
    # Each pass bakes one object: Cycles goes over the whole image once per object baked, so forty separate parts
    # take forty times as long as the same parts joined.
    body, others, wheels = joined(body), joined(others), joined(wheels)
    kind = 'COMBINED'
    if LIT_ALL:
        # Her colours alone, as they are (metal too, which has no colour of its own to a diffuse bake).
        kind = 'DIFFUSE'
        scene.view_settings.view_transform = 'Standard'
        scene.view_settings.look = 'None'
        metals = plain_metal()
    passes = [bake_pass('body', [body], worlds['sky'], sun, real_wheels, kind),
              bake_pass('parts', [others], worlds['sky'], sun, [], kind),
              bake_pass('wheels', [wheels], worlds['even'], sun, [], kind)]
    rgb = np.zeros((BAKE_SIZE, BAKE_SIZE, 3), np.float32)
    valid = np.zeros((BAKE_SIZE, BAKE_SIZE), bool)
    for px in passes:
        mine = (px[..., 3] > 0.5) & ~valid
        rgb[mine] = px[..., :3][mine]
        valid |= mine
    ao = bake_occlusion([(body, real_wheels), (others, []), (wheels, [])], worlds['even'], sun)
    if LIT_ALL:
        restore_metal(metals)
        # Shut in a little in her corners and nooks: the game lights the rest.
        rgb *= (SHUT_IN + (1.0 - SHUT_IN) * np.clip(ao, 0.0, 1.0))[..., None]
    shut = metal & valid
    rgb[shut] = (0.3 + 0.7 * np.clip(ao[shut], 0, 1) ** 0.8)[:, None]
    print('  polished metal %.1f%% of the atlas' % (shut.mean() * 100))
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
    order = [k for k in ('body', 'glass') if k in parts] + sorted(k for k in parts if k not in ('body', 'glass'))
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
                # A mast or a propeller: in its parent's frame, mirrored with the fan it hangs off.
                mirror = Vector((mirror_of(o), 1, 1))
                pivot = Vector((o.location.x * mirror.x, o.location.y, o.location.z))
                rot = (0.0, 0.0, 0.0, 1.0)
                if name == 'tailprop':
                    extra = (float(o.parent.name.rsplit('_', 1)[1]), 0.0, 0.0, 0.0)
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
                rot = quat_to_mc(o.rotation_quaternion if o.rotation_mode == 'QUATERNION' else o.rotation_euler.to_quaternion())
                if name == 'tailprop':
                    # On the stern, not on a fan.
                    extra = (-1.0, 0.0, 0.0, 0.0)
                if 'open_yaw' in o:
                    extra = (float(o['open_yaw']), float(o['dihedral']), float(o['fold_yaw']), float(o['tuck']))
            lit = LIT_ALL or name.startswith(GAME_LIT)
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
                        sc = Vector((sc.x * mirror_of(o), sc.y, sc.z))
                    pm = lambda co, sc=sc: Vector((co.x * sc.x, co.y * sc.y, co.z * sc.z))
                    nm = lambda n, sc=sc: Vector((n.x / sc.x, n.y / sc.y, n.z / sc.z)).normalized()
                for poly in me.polygons:
                    mat = mat_name(me, poly)
                    tint = GLASS_TINT.get(mat)
                    shine = SHINE.get(mat, 0)
                    flags = (1 if mat in GLOW else 0) | shine << 1
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
                            if not lit and not shine:
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
    global FACET, BAKE_SIZE, LIT_ALL, PACK_ROTATE
    args = sys.argv[1:]
    # Her game build is faceted (and --facet renders her so). Its bake holds her colours alone, a little darker in her
    # nooks, drawn pixelated (ChittyTexture's pixelated option), the game lighting each flat face by which way it faces.
    FACET = '--game' in args or '--facet' in args
    if FACET:
        BAKE_SIZE = int(args[args.index('--atlas') + 1]) if '--atlas' in args else 1024
        LIT_ALL = True
        PACK_ROTATE = 'AXIS_ALIGNED'
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
            ('rear_top', 'road', (-2.6, -5.6, 4.4), (0.0, -1.3, 1.0), 38, 0.0, False),
            ('flying_rear', 'flying', (-4.8, -7.0, 4.4), (0.0, -0.6, 1.6), 30, 1.7, False),
            ('front', 'road', (0.4, 7.6, 1.3), (0.0, 0.0, 0.8), 40, 0.0, False),
            ('flying_front', 'flying', (1.5, 9.5, 3.4), (0.0, 0.0, 1.7), 30, 1.7, False),
            ('nose_photo', 'flying', (0.9, 5.2, 0.8), (-0.3, 2.0, 2.2), 32, 1.7, False),
            ('nose_left', 'flying', (-4.2, 5.4, 2.6), (-0.2, 1.8, 2.0), 32, 1.7, False),
            ('nose_plan', 'flying', (0.0, 2.9, 7.5), (0.0, 2.95, 2.0), 40, 1.7, False),
            ('door_right', 'road', (3.6, 1.6, 1.9), (0.6, 0.5, 1.0), 40, 0.0, False),
            ('door_left', 'road', (-3.8, 0.9, 2.3), (-0.4, -0.2, 1.0), 40, 0.0, False),
            ('spare', 'road', (2.9, 2.9, 2.6), (0.6, 1.0, 1.0), 40, 0.0, False),
            ('unfold_30', 'road@0.3', (-5.2, 4.6, 2.6), (0.0, -0.2, 0.6), 40, 0.0, False),
            ('unfold_60', 'road@0.6', (-5.2, 4.6, 2.6), (0.0, -0.2, 0.6), 40, 0.0, False),
            ('wing_folded', 'road', (-3.2, 2.2, 0.7), (-0.6, -0.2, 0.45), 40, 0.0, False),
            ('nose_folded', 'road', (-1.6, 4.4, 0.9), (0.0, 2.2, 0.55), 40, 0.0, False),
            ('nose_unfold', 'road@0.5', (-2.4, 5.2, 2.0), (0.0, 2.6, 0.55), 40, 0.0, False),
            ('tail_folded', 'road', (-1.8, -5.0, 0.9), (0.0, -2.8, 0.6), 40, 0.0, False),
            ('tail_unfold', 'road@0.5', (-2.6, -5.6, 2.2), (0.0, -3.0, 0.6), 40, 0.0, False),
            ('eject', 'road^0.75', (-2.6, -3.8, 2.4), (0.0, -1.2, 1.1), 40, 0.0, False),
            ('dash', 'flying', (0.05, -0.75, 1.75), (0.05, 0.62, 1.15), 30, 0.0, False),
        ]
        for name, mode, cam, at, lens, lift, water in shots:
            if only and name not in only:
                continue
            # 'road@0.4': on the road with the wings that far out; 'road^0.7': with the back seat thrown that high.
            mode, _, seat = mode.partition('^')
            mode, _, wings = mode.partition('@')
            pose(mode, spin=0.4, wings=float(wings) if wings else None, lift=float(seat) if seat else 0.0)
            render_scene(out, name, cam, at, lens, lift, water)
        pose('road')


if __name__ == '__main__':
    main()
