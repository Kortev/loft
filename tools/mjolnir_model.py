"""Builds Mjölnir, Þ-01, in Blender, and bakes the game's copy of it.

Needs Blender's Python module (pip install "bpy==4.5.*" numpy pillow, on Python 3.11). Run from the repository root:

    python tools/mjolnir_model.py --out DIR --renders   # preview renders and DIR/mjolnir.blend, for approval
    python tools/mjolnir_model.py --game                # bakes the mesh, its textures and the item model's display
    python tools/mjolnir_model.py --out DIR --views     # the baked copy drawn as the game draws it (after --game)
    python tools/mjolnir_model.py --out DIR --sheet     # one contact sheet of every render in DIR

Add --only name,name to render some of them, MJOLNIR_SAMPLES=24 for a quick look, and --fit to print how much of a
slot, an item frame and the first-person screen the hammer takes with the display transforms in DISPLAY.

Blender units are the item's pixels, sixteen to a block. The hammer is built standing up, head on top, in the item's
own frame turned to Blender's axes: x along the head, -y the front (the item's south face, +z in Minecraft, the one an
inventory slot shows) and z up, the item's centre (8, 8, 8) at the origin. The game's copy is laid over on the diagonal
as a tool sits in a slot (head top right, haft down to the bottom left) by turning it 45 degrees about the front-to-back
axis, which is where the old cuboid hammer lay, so the display transforms and HammerRaise (which stands it back up)
keep working.

The design is the mod's own: a head of dark forged iron, blue-black and hammered, about twice as long as it is high
and deep, its edges chamfered and polished silver-bright, a thin silver band round each end; a sunken band of Norse
interlace (a closed four-strand plait) along the middle of each long face, its strands cut with channels of cold
electric blue light, and the thorn rune, Thor's letter, glowing in a sunken panel on each striking face. The haft is
short, as in the myth: a silver collar under the head, dark leather wound on the slant in raised overlapping turns, an
octagonal steel pommel with a ring, and a short leather wrist loop through the ring.
"""
import json
import math
import os
import struct
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
import numpy as np
from mathutils import Matrix, Vector
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# --- layout (pixels; z up, -y the front, the item's centre at the origin) ----------------------------------------

# The head: half its length (x), depth (y) and height (z), how high its middle stands over the item's centre, and
# the chamfer on its edges (a quarter round, polished).
HEAD = (5.3, 2.65, 2.65)
HEAD_Z = 4.5
HEAD_BEVEL = 0.42
HEAD_BEVEL_SEGMENTS = 3
# The silver bands round the head near each end: where their middles are, half their width and how far they stand
# proud of the iron.
BAND_X, BAND_HALF_WIDTH, BAND_RISE = 4.5, 0.25, 0.14
# The interlace on each long face: a closed plait of crossings in KNOT_ROWS rows across and KNOT_COLS columns along
# (both odd, so it closes on itself and goes over and under by turns), KNOT_LENGTH long; it is (rows + 1) / (cols + 1)
# of that high. It lies in a recess this much bigger each way (half length, half height) and this deep.
KNOT_COLS, KNOT_ROWS = 13, 3
KNOT_LENGTH = 7.5
KNOT_HEIGHT = KNOT_LENGTH * (KNOT_ROWS + 1) / (KNOT_COLS + 1)
RECESS = (4.0, 1.32, 0.22)
# The rune panel sunk in each end (half its size, its depth) and the rune's strokes in it.
RUNE_HALF, RUNE_DEPTH = 1.75, 0.2
# Under the head: the collar (radius, height) from inside the head down onto the leather.
COLLAR = [(1.25, 2.30), (1.62, 1.98), (1.62, 1.66), (1.52, 1.55), (1.24, 1.50), (1.24, 1.34), (1.44, 1.27),
          (1.44, 1.02), (1.26, 0.92), (1.18, 0.80), (0.96, 0.68)]
COLLAR_MATS = ['silver', 'silver', 'silver', 'steel', 'steel', 'silver', 'silver', 'silver', 'steel', 'steel']
# The grip: leather from GRIP_TOP down to GRIP_BOTTOM round a core of GRIP_RADIUS, wound in one strap that advances
# GRIP_PITCH a turn; each turn's rows stand out by STRAP_PROFILE (the strap rising slowly to its overlapping edge).
GRIP_TOP, GRIP_BOTTOM = 0.86, -10.08
GRIP_RADIUS = 0.98
GRIP_PITCH = 1.05
GRIP_SEGMENTS = 16
STRAP_PROFILE = (0.015, 0.075, 0.115, 0.13, 0.12)
# The pommel: a silver ferrule over the end of the leather, then an octagonal steel knob.
POMMEL = [(0.94, -9.90), (1.21, -9.98), (1.25, -10.06), (1.25, -10.36), (1.13, -10.45), (1.10, -10.52),
          (1.36, -10.74), (1.43, -10.88), (1.43, -11.32), (1.27, -11.56), (0.78, -11.82), (0.0, -11.95)]
POMMEL_MATS = ['silver', 'silver', 'silver', 'silver', 'steel', 'steel', 'steel', 'steel', 'steel', 'steel', 'steel']
# The ring under the pommel (its middle's height, radius and the radius of its wire), the wrist loop through it (the
# strap's width and thickness), the plane the loop hangs in (turned this far from the front, about the haft) and how
# far it swings out from straight down.
RING_Z, RING_RADIUS, RING_WIRE = -12.32, 0.50, 0.16
STRAP_WIDTH, STRAP_THICK = 0.50, 0.16
LOOP_YAW, LOOP_SWING = 35.0, 12.0

# The light the bake shades the texture with: from overhead, a little from the left end, the same on the front and the
# back (the hammer is seen from both sides as it turns), wrapped round so the sides fall off softly towards the bottom.
BAKE_LIGHT = Vector((-0.25, 0.0, 0.97)).normalized()


def srgb(c):
    """sRGB 0-255 triple to Blender's linear RGBA."""
    lin = [v / 255 / 12.92 if v / 255 <= 0.04045 else ((v / 255 + 0.055) / 1.055) ** 2.4 for v in c]
    return (*lin, 1.0)


def to_srgb(lin):
    """Linear 0..1 to sRGB 0..1."""
    lin = np.clip(lin, 0.0, 1.0)
    return np.where(lin <= 0.0031308, lin * 12.92, 1.055 * np.power(lin, 1 / 2.4) - 0.055)


# The colours the game's texture is painted with (sRGB); the runes' light in the glow texture, from the edge of a
# channel to its middle; and their light in the renders.
DESIGN = {
    'iron': (62, 68, 86), 'iron_floor': (26, 28, 36), 'silver': (206, 212, 224), 'steel': (112, 120, 136),
    'leather': (86, 52, 31), 'strap': (104, 62, 36), 'channel': (26, 56, 112), 'ribbon': (100, 108, 126),
}
GLOW_EDGE = np.array((24, 92, 255)) / 255.0
GLOW_MID = np.array((70, 178, 255)) / 255.0
GLOW_CORE = np.array((205, 238, 255)) / 255.0
GLOW_DEEP, GLOW_RENDER = (20, 90, 255), (140, 205, 255)

# --- the interlace and the rune -----------------------------------------------------------------------------------


def billiard_loops(cols, rows):
    """The plait as closed paths through the points of a lattice (cols + 1) by (rows + 1) cells, running at 45 degrees
    and bouncing off every side of the box, as a ball would. With rows and cols odd every crossing is between a strand
    running up and one running down, and over and under alternate all along each strand."""
    W, H = cols + 1, rows + 1
    seen = set()
    loops = []
    for p0 in [(u, 0) for u in range(1, W, 2)]:
        for d0 in ((1, 1), (-1, 1)):
            if frozenset((p0, (p0[0] + d0[0], p0[1] + d0[1]))) in seen:
                continue
            pts = []
            p, d = p0, d0
            while True:
                pts.append(p)
                q = (p[0] + d[0], p[1] + d[1])
                seen.add(frozenset((p, q)))
                du, dv = d
                if q[0] in (0, W):
                    du = -du
                if q[1] in (0, H):
                    dv = -dv
                p, d = q, (du, dv)
                if p == p0 and d == d0:
                    break
            loops.append(pts)
    return loops


def smooth_loop(points, times=4):
    """A closed path through lattice points rounded off by cubic B-spline subdivision: it keeps straight through the
    crossings and turns in a smooth bight at each bounce."""
    P = np.array(points, float)
    for _ in range(times):
        nxt = np.roll(P, -1, 0)
        out = np.empty((len(P) * 2, 2))
        out[0::2] = (np.roll(P, 1, 0) + 6 * P + nxt) / 8
        out[1::2] = (P + nxt) / 2
        P = out
    return P


def knot_images(tpg=96, ss=3, ribbon=0.5, channel=0.19, gap=0.1):
    """The interlace band, tpg texels to a lattice cell: (ribbon, channel), each 0..1. Ribbons are the strands, raised
    iron; channels run down the middle of each and glow. Where strands cross, the one underneath stops short either side
    of the one over it."""
    W, H = KNOT_COLS + 1, KNOT_ROWS + 1
    s = tpg * ss
    loops = [smooth_loop(p) for p in billiard_loops(KNOT_COLS, KNOT_ROWS)]
    rib = Image.new('L', (W * s, H * s), 0)
    ch = Image.new('L', (W * s, H * s), 0)
    dr, dc = ImageDraw.Draw(rib), ImageDraw.Draw(ch)

    def line(draw, pts, width, value, closed=False):
        xy = [(float(x * s), float((H - y) * s)) for x, y in pts]
        if closed:
            xy = xy + xy[:2]
        draw.line(xy, fill=value, width=int(round(width * s)), joint='curve')

    for P in loops:
        line(dr, P, ribbon, 255, True)
        line(dc, P, channel, 255, True)
    # At each crossing the strand running up (slope +1) is over in odd rows, the one running down in even rows.
    for P in loops:
        t = np.roll(P, -1, 0) - np.roll(P, 1, 0)
        slope = np.sign(t[:, 0] * t[:, 1])
        for u in range(1, W):
            for v in range(1, H):
                if (u + v) % 2 == 0:
                    continue
                over = 1 if v % 2 == 1 else -1
                near = np.hypot(P[:, 0] - u, P[:, 1] - v)
                # The strand underneath is cut back where the one over it passes; the one over is drawn again a
                # little further than the cut, so it ends inside its own unbroken length and leaves no seam.
                for reach, layers in ((0.45, ((dr, ribbon + 2 * gap, 0), (dc, ribbon + 2 * gap, 0))),
                                      (0.62, ((dr, ribbon, 255), (dc, channel, 255)))):
                    idx = np.nonzero((near < reach) & (slope == over))[0]
                    for run in np.split(idx, np.nonzero(np.diff(idx) > 1)[0] + 1) if len(idx) else ():
                        for draw, width, value in layers:
                            line(draw, P[run], width, value)
    size = (W * tpg, H * tpg)
    rib = np.array(rib.filter(ImageFilter.GaussianBlur(ss * 0.7)).resize(size, Image.LANCZOS), float) / 255
    ch = np.array(ch.filter(ImageFilter.GaussianBlur(ss * 0.7)).resize(size, Image.LANCZOS), float) / 255
    return rib, ch


def rune_images(size=420, ss=3):
    """The end panel's channels, 0..1: the thorn rune (a stave with its thorn standing out to the right of its middle),
    cut deep and wide, inside a fine ring."""
    S = size * ss

    def P(x, y):
        return ((x / (2 * RUNE_HALF) + 0.5) * S, (0.5 - y / (2 * RUNE_HALF)) * S)

    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    w = 0.30 / (2 * RUNE_HALF) * S
    stave, top, bottom, point = -0.48, 1.18, -1.18, 0.56
    d.line([P(stave, top), P(stave, bottom)], fill=255, width=int(w))
    d.line([P(stave, 0.64), P(point, 0.0), P(stave, -0.64)], fill=255, width=int(w), joint='curve')
    for x, y in ((stave, top), (stave, bottom), (point, 0.0)):
        cx, cy = P(x, y)
        d.ellipse([cx - w / 2, cy - w / 2, cx + w / 2, cy + w / 2], fill=255)
    ring = 0.11 / (2 * RUNE_HALF) * S
    a, b = P(-1.46, 1.46), P(1.46, -1.46)
    d.ellipse([a[0], a[1], b[0], b[1]], outline=255, width=int(ring))
    out = np.array(img.filter(ImageFilter.GaussianBlur(ss * 0.7)).resize((size, size), Image.LANCZOS), float) / 255
    return out


def halo(channel, radius):
    """How much a point glows: all of a channel, and a soft spill of its light round it (so the pattern still reads
    from across a room, or the width of an inventory slot)."""
    img = Image.fromarray((channel * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
    spill = np.array(img, float) / 255
    return np.maximum(channel, np.clip(spill / max(spill.max(), 1e-6) * 0.5, 0, 0.42))


def panel_textures():
    """The images the knot and rune materials read (albedo with alpha, height, glow), made once."""
    rib, ch = knot_images()
    floor = np.array(srgb(DESIGN['iron_floor'])[:3])
    iron = np.array(srgb(DESIGN['ribbon'])[:3])
    blue = np.array(srgb(DESIGN['channel'])[:3])
    alb = floor * (1 - rib[..., None]) + iron * rib[..., None]
    alb = alb * (1 - ch[..., None]) + blue * ch[..., None]
    knot = {'albedo': alb, 'height': rib * (1 - 0.65 * ch), 'glow': halo(ch, 12)}
    rc = rune_images()
    alb = np.array(srgb(DESIGN['iron'])[:3]) * (1 - rc[..., None]) + blue * rc[..., None]
    rune = {'albedo': alb, 'height': 1 - rc, 'glow': halo(rc, 10)}
    return knot, rune


def image(name, arr, alpha=None):
    """A Blender image from a linear (h, w) or (h, w, 3) array, top row first."""
    if arr.ndim == 2:
        arr = np.repeat(arr[..., None], 3, 2)
    h, w = arr.shape[:2]
    rgba = np.ones((h, w, 4), np.float32)
    rgba[..., :3] = arr
    if alpha is not None:
        rgba[..., 3] = alpha
    img = bpy.data.images.new(name, w, h, alpha=True, float_buffer=True)
    img.colorspace_settings.name = 'Linear Rec.709'
    img.pixels.foreach_set(np.ascontiguousarray(rgba[::-1]).ravel())
    img.pack()
    return img


# --- materials ----------------------------------------------------------------------------------------------------
#
# Each material holds three surfaces and the Material Output is switched between them: 'pbr' for the renders, 'design'
# (an emission of the colour the texture is painted with: hammering, cavities and the bake light already in it) and
# 'glow' (an emission of how much each point glows, for the glow texture).

MATS = {}
SURFACES = {}


class Nodes:
    """Builds a material's node tree: a few helpers for maths on sockets or numbers, and the surfaces it makes."""

    def __init__(self, mat):
        self.nt = mat.node_tree
        self.nt.nodes.clear()
        self.out = self.nt.nodes.new('ShaderNodeOutputMaterial')
        self.outputs = {}

    def new(self, kind, **settings):
        n = self.nt.nodes.new(kind)
        for k, v in settings.items():
            setattr(n, k, v)
        return n

    def link(self, a, b):
        self.nt.links.new(a, b)

    def socket(self, node, ident, output=False):
        for s in (node.outputs if output else node.inputs):
            if s.identifier == ident or s.name == ident:
                return s
        raise KeyError(ident)

    def math(self, op, a, b=None, c=None):
        n = self.new('ShaderNodeMath', operation=op)
        for i, x in enumerate((a, b, c)):
            if x is None:
                continue
            if isinstance(x, (int, float)):
                n.inputs[i].default_value = x
            else:
                self.link(x, n.inputs[i])
        return n.outputs[0]

    def vmath(self, op, a, b=None):
        n = self.new('ShaderNodeVectorMath', operation=op)
        for i, x in enumerate((a, b)):
            if x is None:
                continue
            if isinstance(x, (tuple, list, Vector)):
                n.inputs[i].default_value = tuple(x)
            else:
                self.link(x, n.inputs[i])
        return n.outputs['Value' if op in ('DOT_PRODUCT', 'LENGTH', 'DISTANCE') else 'Vector']

    def rgb_scale(self, color, factor):
        """A colour times a value."""
        n = self.new('ShaderNodeMix', data_type='RGBA', blend_type='MULTIPLY')
        self.socket(n, 'Factor_Float').default_value = 1.0
        if isinstance(color, tuple):
            self.socket(n, 'A_Color').default_value = color
        else:
            self.link(color, self.socket(n, 'A_Color'))
        c = self.new('ShaderNodeCombineColor')
        for i in range(3):
            if isinstance(factor, (int, float)):
                c.inputs[i].default_value = factor
            else:
                self.link(factor, c.inputs[i])
        self.link(c.outputs[0], self.socket(n, 'B_Color'))
        return self.socket(n, 'Result_Color', True)

    def mix(self, fac, a, b):
        n = self.new('ShaderNodeMix', data_type='RGBA')
        if isinstance(fac, (int, float)):
            self.socket(n, 'Factor_Float').default_value = fac
        else:
            self.link(fac, self.socket(n, 'Factor_Float'))
        for ident, x in (('A_Color', a), ('B_Color', b)):
            if isinstance(x, tuple):
                self.socket(n, ident).default_value = x
            else:
                self.link(x, self.socket(n, ident))
        return self.socket(n, 'Result_Color', True)

    def emission(self, name, color, strength=1.0):
        e = self.new('ShaderNodeEmission')
        if isinstance(color, tuple):
            e.inputs['Color'].default_value = color
        else:
            self.link(color, e.inputs['Color'])
        e.inputs['Strength'].default_value = strength
        self.outputs[name] = e.outputs[0]

    def shade(self, normal, low, high, power=1.0):
        """How the bake light falls on a point with this normal: low facing straight away, high facing it, halfway
        between for a side (a wrapped light), the falloff sharpened by power."""
        d = self.math('MULTIPLY_ADD', self.vmath('DOT_PRODUCT', normal, BAKE_LIGHT), 0.5, 0.5)
        if power != 1.0:
            d = self.math('POWER', d, power)
        return self.math('ADD', self.math('MULTIPLY', d, high - low), low)

    def coords(self):
        """The point's place on the object, in its own pixels: textures lie on the hammer, not on its unwrapping."""
        return self.new('ShaderNodeTexCoord').outputs['Object']


def new_material(name):
    """A material and the node builder for it; its surfaces are registered under its name as they are made."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    MATS[name] = m
    n = Nodes(m)
    SURFACES[name] = n.outputs
    return m, n


def principled(n, color, metal, rough, normal=None, emit=None, spec=0.5):
    b = n.new('ShaderNodeBsdfPrincipled')
    if isinstance(color, tuple):
        b.inputs['Base Color'].default_value = color
    else:
        n.link(color, b.inputs['Base Color'])
    if isinstance(metal, (int, float)):
        b.inputs['Metallic'].default_value = metal
    else:
        n.link(metal, b.inputs['Metallic'])
    if isinstance(rough, (int, float)):
        b.inputs['Roughness'].default_value = rough
    else:
        n.link(rough, b.inputs['Roughness'])
    b.inputs['Specular IOR Level'].default_value = spec
    if normal is not None:
        n.link(normal, b.inputs['Normal'])
    if emit is not None:
        color, strength = emit
        n.link(color, b.inputs['Emission Color'])
        n.link(strength, b.inputs['Emission Strength'])
    return b.outputs[0]


def hammered(n, scale=1.15):
    """Hammer dents (a smoothed Voronoi field: each dent a shallow bowl round its own centre, its own shade) and
    pits."""
    co = n.coords()
    vor = n.new('ShaderNodeTexVoronoi', feature='SMOOTH_F1')
    vor.inputs['Scale'].default_value = scale
    vor.inputs['Smoothness'].default_value = 0.6
    vor.inputs['Randomness'].default_value = 0.85
    n.link(co, vor.inputs['Vector'])
    noise = n.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 3.2
    noise.inputs['Detail'].default_value = 4.0
    noise.inputs['Roughness'].default_value = 0.6
    n.link(co, noise.inputs['Vector'])
    pit = n.new('ShaderNodeMapRange')
    pit.inputs['From Min'].default_value = 0.66
    pit.inputs['From Max'].default_value = 0.74
    pit.inputs['To Min'].default_value = 0.0
    pit.inputs['To Max'].default_value = 1.0
    n.link(noise.outputs['Fac'], pit.inputs['Value'])
    tint = n.new('ShaderNodeSeparateColor')
    n.link(vor.outputs['Color'], tint.inputs[0])
    return vor.outputs['Distance'], pit.outputs['Result'], tint.outputs[0], noise.outputs['Fac']


def bump(n, height, strength, distance):
    b = n.new('ShaderNodeBump')
    b.inputs['Strength'].default_value = strength
    b.inputs['Distance'].default_value = distance
    n.link(height, b.inputs['Height'])
    return b.outputs['Normal']


def make_iron():
    m, n = new_material('iron')
    dent, pit, tint, noise = hammered(n)
    normal = bump(n, n.math('POWER', dent, 1.5), 0.45, 0.08)
    # Each dent its own shade, pits darker still.
    pitted = n.math('SUBTRACT', 1.0, n.math('MULTIPLY', pit, 0.5))
    tone = n.math('MULTIPLY', n.math('ADD', n.math('MULTIPLY', tint, 0.3), 0.8), pitted)
    base = n.rgb_scale(srgb((112, 120, 138)), tone)
    rough = n.math('ADD', n.math('MULTIPLY', noise, 0.22), 0.4)
    n.outputs['pbr'] = principled(n, base, 1.0, rough, normal)
    shade = n.shade(normal, 0.58, 1.18)
    var = n.math('MULTIPLY', n.math('ADD', n.math('MULTIPLY', tint, 0.22), 0.89), pitted)
    n.emission('design', n.rgb_scale(srgb(DESIGN['iron']), n.math('MULTIPLY', var, shade)))
    n.emission('glow', (0, 0, 0, 1))


def make_metal(name, pbr, rough, low, high, power):
    """Polished metal: the silver of the edges and bands, the steel of the collar and pommel."""
    m, n = new_material(name)
    co = n.coords()
    noise = n.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 6.0
    noise.inputs['Detail'].default_value = 3.0
    n.link(co, noise.inputs['Vector'])
    normal = bump(n, noise.outputs['Fac'], 0.06, 0.03)
    shine = n.math('ADD', n.math('MULTIPLY', noise.outputs['Fac'], 0.08), rough)
    n.outputs['pbr'] = principled(n, srgb(pbr), 1.0, shine, normal)
    shade = n.shade(n.new('ShaderNodeNewGeometry').outputs['Normal'], low, high, power)
    n.emission('design', n.rgb_scale(srgb(DESIGN[name]), shade))
    n.emission('glow', (0, 0, 0, 1))


def make_leather(name, pbr):
    m, n = new_material(name)
    co = n.coords()
    grain = n.new('ShaderNodeTexNoise')
    grain.inputs['Scale'].default_value = 9.0
    grain.inputs['Detail'].default_value = 6.0
    grain.inputs['Roughness'].default_value = 0.65
    n.link(co, grain.inputs['Vector'])
    blotch = n.new('ShaderNodeTexNoise')
    blotch.inputs['Scale'].default_value = 0.9
    blotch.inputs['Detail'].default_value = 2.0
    n.link(co, blotch.inputs['Vector'])
    normal = bump(n, grain.outputs['Fac'], 0.12, 0.03)
    var = n.math('ADD', n.math('MULTIPLY', blotch.outputs['Fac'], 0.3), 0.85)
    n.outputs['pbr'] = principled(n, n.rgb_scale(srgb(pbr), var), 0.0, 0.52, normal, spec=0.35)
    shade = n.shade(normal, 0.72, 1.12)
    tone = n.math('MULTIPLY', var, n.math('ADD', n.math('MULTIPLY', grain.outputs['Fac'], 0.25), 0.88))
    n.emission('design', n.rgb_scale(srgb(DESIGN[name]), n.math('MULTIPLY', tone, shade)))
    n.emission('glow', (0, 0, 0, 1))


def make_panel(name, tex, mapping):
    """The floor of a sunken panel: the interlace or the rune, from images laid over it in object space."""
    m, n = new_material(name)
    co = n.coords()
    sep = n.new('ShaderNodeSeparateXYZ')
    n.link(co, sep.inputs[0])
    u, v = mapping(n, sep)
    vec = n.new('ShaderNodeCombineXYZ')
    n.link(u, vec.inputs[0])
    n.link(v, vec.inputs[1])
    imgs = {}
    for key, arr in tex.items():
        alpha = np.ones(arr.shape[:2], np.float32)
        node = n.new('ShaderNodeTexImage', interpolation='Cubic', extension='CLIP')
        node.image = image(name + '_' + key, arr, alpha)
        n.link(vec.outputs[0], node.inputs['Vector'])
        imgs[key] = node
    inside = imgs['albedo'].outputs['Alpha']
    floor = srgb(DESIGN['iron_floor'])
    albedo = n.mix(inside, floor, imgs['albedo'].outputs['Color'])
    height = n.math('MULTIPLY', imgs['height'].outputs['Color'], inside)
    glow = n.math('MULTIPLY', imgs['glow'].outputs['Color'], inside)
    normal = bump(n, height, 0.8, 0.12)
    # The renders' light: deep blue where it spills, paler down the channels' middles.
    light = n.mix(n.math('POWER', glow, 2.0), srgb(GLOW_DEEP), srgb(GLOW_RENDER))
    metal = n.math('ADD', n.math('MULTIPLY', height, 0.5), 0.5)
    n.outputs['pbr'] = principled(n, n.rgb_scale(albedo, 2.2), metal, 0.34, normal,
                                  emit=(light, n.math('MULTIPLY', glow, 3.2)))
    shade = n.shade(normal, 0.6, 1.2)
    cavity = n.math('ADD', n.math('MULTIPLY', height, 0.3), 0.7)
    n.emission('design', n.rgb_scale(albedo, n.math('MULTIPLY', shade, cavity)))
    n.emission('glow', n.mix(glow, (0, 0, 0, 1), (1, 1, 1, 1)))


def knot_mapping(n, sep):
    """Along the band as seen from in front of each long face, and up it."""
    side = n.math('MULTIPLY', n.math('SIGN', sep.outputs['Y']), -1.0)
    u = n.math('ADD', n.math('MULTIPLY', n.math('MULTIPLY', sep.outputs['X'], side), 1.0 / KNOT_LENGTH), 0.5)
    v = n.math('ADD', n.math('MULTIPLY', n.math('SUBTRACT', sep.outputs['Z'], HEAD_Z), 1.0 / KNOT_HEIGHT), 0.5)
    return u, v


def rune_mapping(n, sep):
    """Across each end as seen from outside it (so the rune reads the right way round on both), and up it."""
    side = n.math('SIGN', sep.outputs['X'])
    u = n.math('ADD', n.math('MULTIPLY', n.math('MULTIPLY', sep.outputs['Y'], side), 0.5 / RUNE_HALF), 0.5)
    v = n.math('ADD', n.math('MULTIPLY', n.math('SUBTRACT', sep.outputs['Z'], HEAD_Z), 0.5 / RUNE_HALF), 0.5)
    return u, v


def make_materials():
    knot, rune = panel_textures()
    make_iron()
    make_metal('silver', (226, 230, 238), 0.16, 0.42, 1.25, 1.6)
    make_metal('steel', (150, 158, 174), 0.26, 0.5, 1.15, 1.2)
    make_leather('leather', (74, 44, 26))
    make_leather('strap', (92, 55, 32))
    make_panel('knot', knot, knot_mapping)
    make_panel('rune', rune, rune_mapping)


def set_surface(name):
    """Switches every material's output to one of its surfaces: 'pbr', 'design' or 'glow'."""
    for mname, m in MATS.items():
        nt = m.node_tree
        out = next(n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL')
        nt.links.new(SURFACES[mname][name], out.inputs['Surface'])


# --- geometry -----------------------------------------------------------------------------------------------------

class Mesh:
    """A bmesh being built, with a material slot per material used."""

    def __init__(self):
        self.bm = bmesh.new()
        self.slots = []

    def slot(self, mat):
        if mat not in self.slots:
            self.slots.append(mat)
        return self.slots.index(mat)

    def vert(self, p):
        return self.bm.verts.new(p)

    def face(self, verts, mat):
        f = self.bm.faces.new(verts)
        f.material_index = self.slot(mat)
        return f

    def obj(self, name, sharp=40.0, recalc=True, weld=1e-5, is_sharp=None):
        """The mesh as an object, its faces smooth except across edges sharper than `sharp` degrees, where the
        material changes, or where is_sharp(edge) says so."""
        bm = self.bm
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=weld)
        if recalc:
            bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.normal_update()
        for f in bm.faces:
            f.smooth = True
        for e in bm.edges:
            faces = e.link_faces
            if len(faces) != 2:
                e.smooth = False
            else:
                e.smooth = (faces[0].material_index == faces[1].material_index
                            and e.calc_face_angle(math.pi) < math.radians(sharp)
                            and not (is_sharp and is_sharp(e)))
        me = bpy.data.meshes.new(name)
        bm.to_mesh(me)
        bm.free()
        for mat in self.slots:
            me.materials.append(MATS[mat])
        o = bpy.data.objects.new(name, me)
        bpy.context.scene.collection.objects.link(o)
        o['part'] = name
        return o


def rounded_rect(hw, hh, r, seg=4):
    """A rectangle's outline with round corners, anticlockwise from the middle of its right side."""
    pts = []
    for cx, cy, a0 in ((hw - r, hh - r, 0), (-hw + r, hh - r, 90), (-hw + r, -hh + r, 180), (hw - r, -hh + r, 270)):
        for k in range(seg + 1):
            a = math.radians(a0 + 90 * k / seg)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def lathe(m, profile, mats, seg, a0=0.0):
    """A surface of revolution round the haft: profile [(radius, height), ...] from top to bottom."""
    rings = []
    for r, z in profile:
        if r < 1e-9:
            rings.append([m.vert((0, 0, z))])
        else:
            turns = [a0 + 2 * math.pi * i / seg for i in range(seg)]
            rings.append([m.vert((r * math.cos(t), r * math.sin(t), z)) for t in turns])
    # Wound so the faces look outward (the profile runs down, the rings anticlockwise seen from above).
    for k in range(len(rings) - 1):
        A, B = rings[k], rings[k + 1]
        for i in range(seg):
            j = (i + 1) % seg
            if len(B) == 1:
                m.face([A[j], A[i], B[0]], mats[k])
            elif len(A) == 1:
                m.face([A[0], B[j], B[i]], mats[k])
            else:
                m.face([A[i], B[i], B[j], A[j]], mats[k])
    return rings


def build_head():
    """The head: an iron block with its edges rounded off silver, the knot band sunk in each long face and the rune
    panel in each end."""
    hx, hy, hz = HEAD
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = Vector((v.co.x * 2 * hx, v.co.y * 2 * hy, v.co.z * 2 * hz + HEAD_Z))
    kx, kz, kd = RECESS
    cuts = [((x, 0, 0), (1, 0, 0)) for x in (-kx, kx)]
    cuts += [((0, 0, HEAD_Z + z), (0, 0, 1)) for z in (-kz, kz, -RUNE_HALF, RUNE_HALF)]
    cuts += [((0, y, 0), (0, 1, 0)) for y in (-RUNE_HALF, RUNE_HALF)]
    for co, no in cuts:
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=co, plane_no=no)
    IRON, SILVER, KNOT, RUNE = range(4)
    for f in bm.faces:
        f.material_index = IRON
    bm.normal_update()
    edges = [e for e in bm.edges if len(e.link_faces) == 2 and e.calc_face_angle(0) > 1.0]
    verts = list({v for e in edges for v in e.verts})
    res = bmesh.ops.bevel(bm, geom=edges + verts, offset=HEAD_BEVEL, offset_type='OFFSET',
                          segments=HEAD_BEVEL_SEGMENTS, profile=0.5, affect='EDGES', clamp_overlap=True)
    for f in res['faces']:
        f.material_index = SILVER
    bm.normal_update()

    def sink(normal, inside, depth, mat):
        n = Vector(normal)
        faces = [f for f in bm.faces if f.normal.dot(n) > 0.999 and inside(f.calc_center_median())]
        r = bmesh.ops.extrude_face_region(bm, geom=faces)
        # The faces extruded from stay behind, turned over, as the bottom of the extrusion: they go.
        bmesh.ops.delete(bm, geom=faces, context='FACES_ONLY')
        moved = [g for g in r['geom'] if isinstance(g, bmesh.types.BMVert)]
        bmesh.ops.translate(bm, vec=-n * depth, verts=moved)
        bm.normal_update()
        for f in bm.faces:
            c = f.calc_center_median()
            if inside(c) and abs(c.dot(n) - (Vector((hx, hy, hz)).dot(Vector([abs(a) for a in n])) - depth)) < 1e-4 \
                    and f.normal.dot(n) > 0.999:
                f.material_index = mat

    for s in (-1, 1):
        sink((0, s, 0), lambda c: abs(c.x) < kx and abs(c.z - HEAD_Z) < kz, kd, KNOT)
        sink((s, 0, 0), lambda c: abs(c.y) < RUNE_HALF and abs(c.z - HEAD_Z) < RUNE_HALF, RUNE_DEPTH, RUNE)
    # Sinking a face turns its new walls inside out: face everything outward again (the head is closed).
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.normal_update()
    me = bpy.data.meshes.new('head')
    for f in bm.faces:
        f.smooth = True
    for e in bm.edges:
        lf = e.link_faces
        e.smooth = len(lf) == 2 and lf[0].material_index == lf[1].material_index and e.calc_face_angle(math.pi) < 0.6
    bm.to_mesh(me)
    bm.free()
    for name in ('iron', 'silver', 'knot', 'rune'):
        me.materials.append(MATS[name])
    o = bpy.data.objects.new('head', me)
    bpy.context.scene.collection.objects.link(o)
    o['part'] = 'head'
    return o


def build_bands():
    """A silver band round each end of the head, standing a little proud of it, its edges softened; its sides run on
    in under the iron so no gap shows at the head's rounded corners."""
    m = Mesh()
    hy, hz = HEAD[1], HEAD[2]
    outer = rounded_rect(hy + BAND_RISE, hz + BAND_RISE, HEAD_BEVEL + BAND_RISE, 4)
    soft = rounded_rect(hy + BAND_RISE - 0.05, hz + BAND_RISE - 0.05, HEAD_BEVEL + BAND_RISE - 0.05, 4)
    inner = rounded_rect(hy - 0.1, hz - 0.1, HEAD_BEVEL - 0.1, 4)
    for s in (-1, 1):
        x0, x1 = s * BAND_X - BAND_HALF_WIDTH, s * BAND_X + BAND_HALF_WIDTH
        sections = [(x0, inner), (x0, soft), (x0 + 0.05, outer), (x1 - 0.05, outer), (x1, soft), (x1, inner)]
        rings = [[m.vert((x, a, b + HEAD_Z)) for a, b in pts] for x, pts in sections]
        n = len(outer)
        for k in range(len(rings) - 1):
            for i in range(n):
                j = (i + 1) % n
                m.face([rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]], 'silver')
    return m.obj('bands', sharp=50.0, recalc=False)


def build_collar():
    m = Mesh()
    lathe(m, COLLAR, COLLAR_MATS, 20)
    return m.obj('collar', sharp=50.0, recalc=False)


def build_grip():
    """The leather: one strap wound down the haft on the slant, each turn overlapping the last, so the grip is ridged
    with real steps. The mesh's rows run along the strap (helices), STRAP_PROFILE giving each row's height."""
    m = Mesh()
    N, K, p = GRIP_SEGMENTS, len(STRAP_PROFILE), GRIP_PITCH
    rows = int(math.ceil((GRIP_TOP - GRIP_BOTTOM + 2 * p) / p * K)) + K
    edge = m.bm.faces.layers.int.new('strap_edge')
    verts = {}
    for j in range(rows + 1):
        for i in range(N + 1):
            t = 2 * math.pi * i / N
            z = GRIP_TOP + p * (1 - j / K) - p * i / N
            z = min(max(z, GRIP_BOTTOM), GRIP_TOP)
            r = GRIP_RADIUS + STRAP_PROFILE[j % K]
            verts[i, j] = m.vert((r * math.cos(t), r * math.sin(t), z))
    for j in range(rows):
        for i in range(N):
            quad = [verts[i, j], verts[i, j + 1], verts[i + 1, j + 1], verts[i + 1, j]]
            zs = [v.co.z for v in quad]
            if all(abs(z - GRIP_TOP) < 1e-6 for z in zs) or all(abs(z - GRIP_BOTTOM) < 1e-6 for z in zs):
                continue
            # The last row of each turn drops from the strap's edge to the next turn under it.
            m.face(quad, 'leather')[edge] = 1 if j % K == K - 1 else 0

    def step(e):
        return len({f[edge] for f in e.link_faces}) > 1

    # The column at a full turn is the first column a strap further down: weld them. The step is shaded sharp.
    return m.obj('grip', sharp=60.0, recalc=False, weld=1e-4, is_sharp=step)


def build_pommel():
    m = Mesh()
    lathe(m, POMMEL, POMMEL_MATS, 8, a0=math.pi / 8)
    return m.obj('pommel', sharp=30.0, recalc=False)


def loop_frame():
    """The wrist loop's plane: h along it (the way the strap runs through the ring), n across it, and z up."""
    yaw = math.radians(LOOP_YAW)
    h = Vector((math.cos(yaw), math.sin(yaw), 0.0))
    n = Vector((-math.sin(yaw), math.cos(yaw), 0.0))
    return h, n


def build_ring():
    """The ring under the pommel, upright, its hole facing along the loop so the strap runs through it."""
    m = Mesh()
    h, n = loop_frame()
    z = Vector((0, 0, 1))
    c = Vector((0, 0, RING_Z))
    big, small = 16, 6
    rings = []
    for i in range(big):
        a = 2 * math.pi * i / big
        radial = z * math.cos(a) + n * math.sin(a)
        rings.append([m.vert(c + radial * (RING_RADIUS + RING_WIRE * math.cos(2 * math.pi * k / small))
                             + h * (RING_WIRE * math.sin(2 * math.pi * k / small))) for k in range(small)])
    for i in range(big):
        for k in range(small):
            a, b = rings[i], rings[(i + 1) % big]
            m.face([a[k], a[(k + 1) % small], b[(k + 1) % small], b[k]], 'silver')
    return m.obj('ring', sharp=70.0)


def closed_catmull(points, per=6):
    P = [Vector(p) for p in points]
    n = len(P)
    out = []
    for i in range(n):
        p0, p1, p2, p3 = P[i - 1], P[i], P[(i + 1) % n], P[(i + 2) % n]
        for k in range(per):
            t = k / per
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t +
                              (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t))
    return out


def build_loop():
    """The wrist loop: a strap over the ring's bottom bar, its two ends drawn together in a keeper just below it, and
    the loop hanging under that, swung out a little."""
    m = Mesh()
    h, n = loop_frame()
    z = Vector((0, 0, 1))
    bar = Vector((0, 0, RING_Z - RING_RADIUS))
    swing = math.radians(LOOP_SWING)
    # Down the loop's plane, swung out about the bar.
    down = -z * math.cos(swing) + h * math.sin(swing)
    along = h * math.cos(swing) + z * math.sin(swing)

    def at(a, b):
        return bar + along * a + down * -b

    # In the loop's plane, from the bar (a across, b up): over the bar, down the right leg into the keeper, round
    # the bottom of the loop and back up the left leg.
    wrap = RING_WIRE + STRAP_THICK / 2 + 0.01
    over = [(wrap * math.cos(math.radians(a)), wrap * math.sin(math.radians(a)))
            for a in (180, 150, 120, 90, 60, 30, 0)]
    leg = [(wrap, -0.08), (0.16, -0.36), (0.11, -0.52), (0.13, -0.72), (0.36, -1.0), (0.58, -1.42), (0.6, -1.86),
           (0.46, -2.22), (0.24, -2.4)]
    path = closed_catmull([at(a, b) for a, b in over + leg + [(0.0, -2.42)] + [(-a, b) for a, b in reversed(leg)]], 2)
    count = len(path)
    rings = []
    for i, p in enumerate(path):
        t = (path[(i + 1) % count] - path[i - 1]).normalized()
        across = n.cross(t).normalized()
        rings.append([m.vert(p + n * (STRAP_WIDTH / 2 * sa) + across * (STRAP_THICK / 2 * sb))
                      for sa, sb in ((1, 1), (-1, 1), (-1, -1), (1, -1))])
    for i in range(count):
        a, b = rings[i], rings[(i + 1) % count]
        for k in range(4):
            m.face([a[k], b[k], b[(k + 1) % 4], a[(k + 1) % 4]], 'strap')
    # The keeper: a short leather wrap binding the two ends together under the ring.
    kc = at(0.0, -0.55)
    size = (0.12 + STRAP_THICK, 0.13, STRAP_WIDTH / 2 + 0.05)
    corners = [m.vert(kc + along * (sx * size[0]) - down * (sy * size[1]) + n * (sz * size[2]))
               for sx, sy, sz in [(-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                                  (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]]
    for q in ((0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)):
        m.face([corners[i] for i in q], 'leather')
    return m.obj('loop', sharp=50.0)


def build():
    # An empty scene: Blender's startup file has a cube, a lamp and a camera of its own.
    bpy.ops.wm.read_factory_settings(use_empty=True)
    MATS.clear()
    SURFACES.clear()
    make_materials()
    parts = [build_head(), build_bands(), build_collar(), build_grip(), build_pommel(), build_ring(), build_loop()]
    set_surface('pbr')
    return parts


# --- preview renders ----------------------------------------------------------------------------------------------

SAMPLES = int(os.environ.get('MJOLNIR_SAMPLES', '128'))


def look_at(obj, target):
    obj.rotation_mode = 'XYZ'
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat('-Z', 'Y').to_euler()


def studio():
    """A dark studio, made once: a deep blue-black backdrop with a soft band of light round its horizon for the metal
    to show, a warm key light over the left shoulder, a cold rim light behind, a low fill and a lamp overhead."""
    scene = bpy.context.scene
    if 'key' in bpy.data.objects:
        return
    world = bpy.data.worlds.new('studio')
    world.use_nodes = True
    nt = world.node_tree
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    cr = ramp.color_ramp
    stops = [(0.0, (0.004, 0.005, 0.008)), (0.46, (0.012, 0.015, 0.024)), (0.5, (0.09, 0.10, 0.13)),
             (0.56, (0.02, 0.025, 0.04)), (1.0, (0.03, 0.035, 0.05))]
    cr.elements[0].position, cr.elements[0].color = stops[0][0], (*stops[0][1], 1)
    cr.elements[1].position, cr.elements[1].color = stops[-1][0], (*stops[-1][1], 1)
    for pos, col in stops[1:-1]:
        cr.elements.new(pos).color = (*col, 1)
    mapr = nt.nodes.new('ShaderNodeMapRange')
    mapr.inputs['From Min'].default_value = -1.0
    nt.links.new(tc.outputs['Generated'], sep.inputs['Vector'])
    nt.links.new(sep.outputs['Z'], mapr.inputs['Value'])
    nt.links.new(mapr.outputs['Result'], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], nt.nodes['Background'].inputs['Color'])
    scene.world = world
    for name, loc, size, energy, color in (('key', (-34, -40, 30), 26, 26000, (1.0, 0.95, 0.88)),
                                           ('rim', (32, 38, 16), 18, 16000, (0.62, 0.78, 1.0)),
                                           ('fill', (40, -34, -18), 40, 5000, (0.9, 0.92, 1.0)),
                                           ('top', (6, -10, 48), 22, 9000, (1.0, 1.0, 1.0))):
        light = bpy.data.objects.new(name, bpy.data.lights.new(name, 'AREA'))
        light.data.size = size
        light.data.energy = energy
        light.data.color = color
        light.location = loc
        look_at(light, (0, 0, -3))
        scene.collection.objects.link(light)
        light['studio'] = True
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam


def studio_settings(samples):
    scene = bpy.context.scene
    studio()
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.cycles.filter_width = 1.5
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Medium High Contrast'
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = False
    scene.world = bpy.data.worlds['studio']
    for o in scene.objects:
        if o.get('studio'):
            o.hide_render = False
        elif o.get('game'):
            o.hide_render = True
        elif o.get('part'):
            o.hide_render = False
    return scene


def render(out, name, loc, target, lens=85, size=(1000, 1250), ortho=None):
    scene = studio_settings(SAMPLES)
    scene.render.resolution_x, scene.render.resolution_y = size
    cam = scene.camera
    cam.location = Vector(loc)
    look_at(cam, target)
    cam.data.type = 'ORTHO' if ortho else 'PERSP'
    if ortho:
        cam.data.ortho_scale = ortho
    cam.data.sensor_fit = 'AUTO'
    cam.data.lens = lens
    cam.data.clip_start, cam.data.clip_end = 0.5, 500
    scene.render.filepath = os.path.join(out, name + '.png')
    bpy.ops.render.render(write_still=True)
    print('  rendered', name)


HERO_SHOTS = [
    # name, camera, target, lens, size, ortho scale
    ('hero', (-36, -50, -7), (0.2, 0, -3.9), 85, (1000, 1250), None),
    ('side', (0, -90, -4.1), (0, 0, -4.1), 85, (800, 1250), 24.5),
    ('rune_end', (25, -11, 9.5), (5.0, 0, 4.4), 100, (1000, 1000), None),
    ('knotwork', (-7, -23, 11.5), (0.6, -2.4, 4.5), 85, (1250, 800), None),
    ('haft', (16, -26, -9), (0, 0, -9.5), 85, (800, 1000), None),
]


def hero_renders(out, only):
    for name, loc, target, lens, size, ortho in HERO_SHOTS:
        if only and name not in only:
            continue
        render(out, name, loc, target, lens, size, ortho)
    if not only or 'inventory_pbr' in only:
        inventory_pbr(out)


# --- Minecraft's transforms, to see the hammer as the game places it -----------------------------------------------
#
# Matrices here are 4x4 in Minecraft's axes (x east, y up, z south) and blocks, as the game's MatrixStack builds them.
# B2M turns Blender's axes into Minecraft's: an item seen from the front in Blender (from -y) is seen from the south.

B2M = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))
M2B = B2M.transposed()


def T(x, y, z):
    return Matrix.Translation((x, y, z))


def S(x, y=None, z=None):
    return Matrix.Diagonal((x, x if y is None else y, x if z is None else z, 1.0))


def R(axis, degrees):
    return Matrix.Rotation(math.radians(degrees), 4, axis)


# Upright Blender pixels (the item's centre at the origin) to the item's own pixels, laid on the diagonal: turned 45
# degrees about the front-to-back axis so the head goes to the top right.
UPRIGHT_TO_ITEM = T(8, 8, 8) @ B2M @ R('Y', 45)

# The item model's display transforms (rotation in degrees, translation in pixels, scale), as in the model's JSON.
DISPLAY = {
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.5, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.5, 0.5], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_righthand': {'rotation': [0, -75, 30], 'translation': [-1.0, 2.6, -1.2], 'scale': [0.62, 0.62, 0.62]},
    'firstperson_lefthand': {'rotation': [0, 105, -30], 'translation': [-1.0, 2.6, -1.2], 'scale': [0.62, 0.62, 0.62]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1.0, 1.0, 1.0]},
    'gui': {'rotation': [20, -30, 0], 'translation': [0.3, 0.1, 0], 'scale': [0.88, 0.88, 0.88]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3.5, 0], 'scale': [0.48, 0.48, 0.48]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [-1.1, 1.3, 0], 'scale': [1.0, 1.0, 1.0]},
}


def display(name, left=False):
    """Transformation.apply: translate (pixels / 16), rotate X then Y then Z as JOML's rotationXYZ, then scale; a left
    hand mirrors the translation's x and the y and z turns."""
    d = DISPLAY[name]
    rx, ry, rz = d['rotation']
    tx, ty, tz = d['translation']
    if left:
        ry, rz, tx = -ry, -rz, -tx
    return T(tx / 16, ty / 16, tz / 16) @ R('X', rx) @ R('Y', ry) @ R('Z', rz) @ S(*d['scale'])


def item(upright=True):
    """ItemRenderer: after the display transform the model is moved half a block so its centre is at the origin, and
    the builtin renderer draws in blocks (pixels / 16). Upright: from the renders' upright hammer in Blender's axes."""
    m = T(-0.5, -0.5, -0.5) @ S(1 / 16)
    return m @ UPRIGHT_TO_ITEM if upright else m


# The player's model space (y down, pixels / 16) in the world, for a player at the origin facing south (body yaw 0):
# LivingEntityRenderer turns 180 - yaw about y and flips x and y, PlayerEntityRenderer scales by 15/16, and the model
# is lifted 1.501 blocks.
PLAYER = R('Y', 180) @ S(-1, -1, 1) @ S(0.9375) @ T(0, -1.501, 0)
# The right arm holding an item (BipedEntityModel, ArmPose.ITEM): pitch -pi/10, and the idle sway's roll.
ARM_PITCH, ARM_ROLL = -18.0, 3.0


def arm(side=1, pitch=ARM_PITCH, roll=ARM_ROLL):
    """ModelPart.rotate for an arm (1 the right, -1 the left): to its pivot, then rotationZYX(roll, yaw, pitch)."""
    return PLAYER @ T(-5 / 16 * side, 2 / 16, 0) @ R('Z', roll * side) @ R('X', pitch)


def view_matrix(view, upright=True):
    """Where the game draws the item (Minecraft's axes, blocks) in each view: the inventory slot (its centre at the
    origin), the first-person hand (the camera at the origin looking north), the third-person hand of a player at the
    origin facing south, an item bobbing on the ground at the origin, one in an item frame on a wall facing south, and
    HammerRaise's pose with the hammer held high."""
    it = item(upright)
    if view == 'gui':
        return display('gui') @ it
    if view == 'firstperson':
        return T(0.56, -0.52, -0.72) @ display('firstperson_righthand') @ it
    if view == 'firstperson_left':
        return T(-0.56, -0.52, -0.72) @ display('firstperson_lefthand', left=True) @ it
    if view == 'thirdperson':
        return arm() @ R('X', -90) @ R('Y', 180) @ T(1 / 16, 0.125, -0.625) @ display('thirdperson_righthand') @ it
    if view == 'ground':
        return T(0, 0.1 + 0.25 * DISPLAY['ground']['scale'][1], 0) @ R('Y', 35) @ display('ground') @ it
    if view == 'fixed':
        return T(0, 0, 1 / 16 + 0.4375) @ R('Y', 180) @ T(0, 0, 0.4375) @ S(0.5) @ display('fixed') @ it
    if view == 'raise':
        return T(0.18, 0.12, -0.82) @ R('X', 12) @ R('Z', 14) @ R('Y', -35) @ R('Z', 45) @ S(0.62) @ it
    raise ValueError(view)


def inventory_pbr(out):
    """The renders' hammer as an inventory slot sees it, 64 pixels: the camera put where the slot's view is."""
    scene = studio_settings(64)
    inv = (M2B @ view_matrix('gui')).inverted()
    cam = scene.camera
    loc, rot, _ = (inv @ M2B @ T(0, 0, 4)).decompose()
    cam.location = loc
    cam.rotation_mode = 'QUATERNION'
    cam.rotation_quaternion = rot
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = 16.0 * 1.5
    scene.render.resolution_x = scene.render.resolution_y = 384
    scene.render.film_transparent = True
    path = os.path.join(out, 'inventory_pbr_384.png')
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    # A slot is 16 of the 24 pixels across: shrunk with the colour premultiplied, so the edges do not darken.
    Image.open(path).convert('RGBa').resize((96, 96), Image.LANCZOS).convert('RGBA').save(
        os.path.join(out, 'inventory_pbr_64.png'))
    cam.rotation_mode = 'XYZ'
    scene.render.film_transparent = False
    print('  rendered inventory_pbr_64')


def fit_report(parts):
    """How much room the hammer takes in the slot, the frame and the first-person view."""
    deps = bpy.context.evaluated_depsgraph_get()
    pts = []
    for o in parts:
        me = o.evaluated_get(deps).to_mesh()
        pts += [o.matrix_world @ v.co for v in me.vertices]
        o.evaluated_get(deps).to_mesh_clear()
    for view in ('gui', 'fixed'):
        M = view_matrix(view)
        q = np.array([(M @ p)[:2] for p in pts]) * 16
        print('  %-6s x %6.2f..%6.2f  y %6.2f..%6.2f  (pixels from the middle; a slot is +-8)'
              % (view, q[:, 0].min(), q[:, 0].max(), q[:, 1].min(), q[:, 1].max()))
    # The item bobs 0.1 either way of where view_matrix puts it.
    low = min((view_matrix('ground') @ p).y for p in pts) - 0.1
    print('  ground: its lowest point, at the bottom of the bob, is %.3f blocks off the ground' % low)
    for view in ('firstperson', 'raise'):
        M = view_matrix(view)
        f = 1 / math.tan(math.radians(35))
        q = []
        for p in pts:
            c = M @ p
            q.append((c.x / -c.z * f / (16 / 9), c.y / -c.z * f))
        q = np.array(q)
        print('  %-11s screen x %5.2f..%5.2f  y %5.2f..%5.2f  (-1..1 is the screen)'
              % (view, q[:, 0].min(), q[:, 0].max(), q[:, 1].min(), q[:, 1].max()))


# --- the game's copy ----------------------------------------------------------------------------------------------

GAME_MESH = 'src/client/resources/assets/shootingstar/meshes/mjolnir.hbm'
GAME_TEXTURE = 'src/main/resources/assets/shootingstar/textures/item/mjolnir_baked.png'
GAME_GLOW = 'src/main/resources/assets/shootingstar/textures/item/mjolnir_glow.png'
ITEM_MODEL = 'src/main/resources/assets/shootingstar/models/item/mjolnir.json'
BAKE_SIZE = 512
BAKE_SAMPLES = int(os.environ.get('MJOLNIR_BAKE_SAMPLES', '128'))
# How much of the texture each part gets for its size: most for the interlace and runes, which are looked at close
# to, least for the leather.
TEXEL_WEIGHT = {'panels': 1.7, 'head': 1.15, 'bands': 1.0, 'collar': 1.0, 'grip': 0.8, 'pommel': 1.0, 'ring': 0.9,
                'loop': 0.8}
GLOWING = ('knot', 'rune')
# Faces this close in direction are unwrapped together (so a chamfer stays on the face it rounds), and the islands are
# packed this far apart (a fraction of the texture: three texels).
UV_ANGLE = float(os.environ.get('MJOLNIR_UV_ANGLE', '66'))
UV_MARGIN = 0.006


def triangulate_big(me):
    bm = bmesh.new()
    bm.from_mesh(me)
    big = [f for f in bm.faces if len(f.verts) > 4]
    if big:
        bmesh.ops.triangulate(bm, faces=big)
    bm.to_mesh(me)
    bm.free()


def select_only(objs):
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]


def bake_copies(parts):
    """A copy of every part to bake, the head's sunken panels split off from it so they can have more of the
    texture. Each is unwrapped on its own, all their islands scaled alike (by area, then TEXEL_WEIGHT), packed, and
    the lot joined into one object."""
    deps = bpy.context.evaluated_depsgraph_get()
    copies = []
    for o in parts:
        me = bpy.data.meshes.new_from_object(o.evaluated_get(deps))
        triangulate_big(me)
        groups = {o.name: None}
        if o.name == 'head':
            panel = {i for i, m in enumerate(me.materials) if m.name in GLOWING}
            groups = {'head': lambda f, panel=panel: f.material_index not in panel,
                      'panels': lambda f, panel=panel: f.material_index in panel}
        for name, keep in groups.items():
            part = me.copy()
            if keep is not None:
                bm = bmesh.new()
                bm.from_mesh(part)
                bmesh.ops.delete(bm, geom=[f for f in bm.faces if not keep(f)], context='FACES')
                bm.to_mesh(part)
                bm.free()
            uv = part.uv_layers.new(name='bake')
            part.uv_layers.active = uv
            uv.active_render = True
            c = bpy.data.objects.new('bake:' + name, part)
            bpy.context.scene.collection.objects.link(c)
            copies.append(c)
        bpy.data.meshes.remove(me)
    for c in copies:
        select_only([c])
        bpy.ops.object.mode_set(mode='EDIT')
        bpy.ops.mesh.select_all(action='SELECT')
        bpy.ops.uv.smart_project(angle_limit=math.radians(UV_ANGLE), island_margin=0.0, area_weight=0.0,
                                 scale_to_bounds=False)
        bpy.ops.object.mode_set(mode='OBJECT')
    select_only(copies)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.select_all(action='SELECT')
    bpy.ops.uv.average_islands_scale()
    bpy.ops.object.mode_set(mode='OBJECT')
    for c in copies:
        w = TEXEL_WEIGHT[c.name.split(':', 1)[1]]
        uv = c.data.uv_layers['bake'].data
        co = np.empty(len(uv) * 2, np.float32)
        uv.foreach_get('uv', co)
        uv.foreach_set('uv', co * w)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.select_all(action='SELECT')
    bpy.ops.uv.pack_islands(rotate=True, margin_method='FRACTION', margin=UV_MARGIN, shape_method='CONCAVE')
    bpy.ops.object.mode_set(mode='OBJECT')
    # One object: Cycles goes over the whole image once for each object baked.
    select_only(copies)
    bpy.ops.object.join()
    joined = bpy.context.view_layer.objects.active
    joined.name = 'bake'
    return joined


def bake(obj, kind, surface=None):
    """Bakes the object's look into a fresh float image; returns it linear, top row first."""
    img = bpy.data.images.new('bake_' + (surface or kind), BAKE_SIZE, BAKE_SIZE, alpha=True, float_buffer=True)
    img.generated_color = (0, 0, 0, 0)
    if surface:
        set_surface(surface)
    for m in obj.data.materials:
        node = m.node_tree.nodes.get('bake_target')
        if node is None:
            node = m.node_tree.nodes.new('ShaderNodeTexImage')
            node.name = 'bake_target'
        node.image = img
        m.node_tree.nodes.active = node
    select_only([obj])
    bpy.ops.object.bake(type=kind, margin=0, use_clear=False)
    px = np.array(img.pixels[:], np.float32).reshape(BAKE_SIZE, BAKE_SIZE, 4)[::-1]
    print('  baked %-6s %5.1f%% of the texture' % (surface or kind, (px[..., 3] > 0.5).mean() * 100))
    return px


def dilate(rgb, valid, steps=16):
    """Spreads each island's colour out past its edges, so nothing between them shows where texels are rounded."""
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


def glow_colour(m):
    """The runes' light for a channel's depth of glow m (0..1): deep electric blue at its edges, near white down its
    middle."""
    stops = [(0.0, np.zeros(3)), (0.2, GLOW_EDGE * 0.55), (0.55, GLOW_MID), (0.9, GLOW_CORE), (1.0, GLOW_CORE)]
    out = np.zeros(m.shape + (3,))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        t = np.clip((m - a) / (b - a), 0, 1)[..., None]
        out = np.where(((m >= a) & (m <= b))[..., None], ca * (1 - t) + cb * t, out)
    return out


def export_game(parts):
    """Bakes the texture and the glow, and writes the mesh and the item model.

    The texture is the colour the hammer is painted with (its hammering, the light of a soft lamp over the left
    shoulder on its dents and chamfers, the leather's grain) times how open each point is to the sky (ambient
    occlusion: the recesses, the collar's grooves and the leather's turns shadowed). The game shades it again by its
    normals, as it does every entity. The glow texture is black but for the channels of the interlace and the rune."""
    scene = bpy.context.scene
    for o in parts:
        o.hide_render = True
    for o in scene.objects:
        if o.get('studio'):
            o.hide_render = True
    obj = bake_copies(parts)
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = BAKE_SAMPLES
    scene.render.bake.margin = 0
    scene.render.bake.use_clear = False
    world = bpy.data.worlds.new('bake_white')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (1, 1, 1, 1)
    world.light_settings.distance = 1.6
    old_world = scene.world
    scene.world = world
    design = bake(obj, 'EMIT', 'design')
    valid = design[..., 3] > 0.5
    ao = bake(obj, 'AO')[..., 0]
    glow = bake(obj, 'EMIT', 'glow')[..., 0]
    debug = os.environ.get('MJOLNIR_DEBUG')
    if debug:
        for name, arr in (('design', to_srgb(design[..., :3])), ('alpha', design[..., 3]), ('ao', ao), ('glow', glow)):
            Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8)).save(os.path.join(debug, 'raw_%s.png' % name))
    set_surface('pbr')
    scene.world = old_world
    lin = design[..., :3] * (0.22 + 0.78 * np.clip(ao, 0, 1) ** 1.15)[..., None]
    rgb = dilate(to_srgb(lin), valid)
    os.makedirs(os.path.dirname(GAME_TEXTURE), exist_ok=True)
    Image.fromarray((np.clip(rgb, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB').save(GAME_TEXTURE, optimize=True)
    g = glow_colour(np.clip(glow, 0, 1)) * valid[..., None]
    Image.fromarray((np.clip(g, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB').save(GAME_GLOW, optimize=True)
    print('  texture %s (%d KB), glow %s (%d KB)' % (GAME_TEXTURE, os.path.getsize(GAME_TEXTURE) // 1024, GAME_GLOW,
                                                   os.path.getsize(GAME_GLOW) // 1024))
    write_mesh(obj)
    write_model()
    bpy.data.objects.remove(obj)
    for o in parts:
        o.hide_render = False


def write_mesh(obj):
    """Writes the .hbm the game draws (HammerMesh): little endian, b"HBM1", int32 quad count, int32 count of the quads
    at the front that glow (drawn again into the glow layer), then four vertices a quad, each float32 x, y, z in the
    item's pixels (as a JSON item model's elements, laid on the diagonal), float32 u, v (v down), int8 nx, ny, nz
    (normal * 127) and a zero byte. Triangles repeat their last corner."""
    me = obj.data
    uv = me.uv_layers['bake'].data
    normals = me.corner_normals
    rot = UPRIGHT_TO_ITEM.to_3x3()
    quads = []
    for poly in me.polygons:
        corners = []
        for li in poly.loop_indices:
            co = UPRIGHT_TO_ITEM @ me.vertices[me.loops[li].vertex_index].co
            n = (rot @ Vector(normals[li].vector)).normalized()
            u, v = uv[li].uv
            corners.append((tuple(co), (u, 1.0 - v), tuple(n)))
        if len(corners) == 3:
            corners.append(corners[2])
        quads.append((me.materials[poly.material_index].name in GLOWING, corners))
    quads.sort(key=lambda q: not q[0])
    glowing = sum(1 for q in quads if q[0])
    buf = bytearray(b'HBM1' + struct.pack('<ii', len(quads), glowing))
    for _, corners in quads:
        for (x, y, z), (u, v), (nx, ny, nz) in corners:
            buf += struct.pack('<5f4b', x, y, z, u, v, int(round(nx * 127)), int(round(ny * 127)),
                               int(round(nz * 127)), 0)
    os.makedirs(os.path.dirname(GAME_MESH), exist_ok=True)
    with open(GAME_MESH, 'wb') as f:
        f.write(buf)
    print('mjolnir: %d quads (%d glowing) -> %s (%d KB)' % (len(quads), glowing, GAME_MESH, len(buf) // 1024))


def write_model():
    """The item model: drawn by the builtin renderer (HammerRenderer), the flat icon for its particles, lit from the
    front in slots, and placed by DISPLAY in every view."""
    model = {
        'parent': 'builtin/entity',
        'textures': {'particle': 'shootingstar:item/mjolnir'},
        'gui_light': 'front',
        'display': DISPLAY,
    }
    with open(ITEM_MODEL, 'w') as f:
        json.dump(model, f, indent=2)
        f.write('\n')
    print('  model', ITEM_MODEL)


def read_mesh(path):
    """The .hbm read back as the game reads it: [(glows, [(position, uv, normal) * 4])]."""
    with open(path, 'rb') as f:
        data = f.read()
    assert data[:4] == b'HBM1', 'not a hammer mesh'
    count, glowing = struct.unpack_from('<ii', data, 4)
    off = 12
    quads = []
    for q in range(count):
        corners = []
        for _ in range(4):
            x, y, z, u, v, nx, ny, nz, _pad = struct.unpack_from('<5f4b', data, off)
            off += 24
            corners.append(((x, y, z), (u, v), (nx / 127, ny / 127, nz / 127)))
        quads.append((q < glowing, corners))
    assert off == len(data), 'trailing bytes in the mesh'
    return quads


# --- the game's copy, drawn as the game draws it ------------------------------------------------------------------
#
# The baked mesh read back from its file, textured with the baked texture (nearest texel, as the game samples it) and
# shaded as Minecraft's entity shader does: 0.4 + 0.6 times the light of two fixed lamps on its normals, at most 1,
# then the glow added on top. Drawn with no colour management, as the game's framebuffer is.

LEVEL_LIGHTS = [Vector((0.2, 1.0, -0.7)).normalized(), Vector((-0.2, 1.0, 0.7)).normalized()]


def gui_lights(front=True):
    """The two lamps for items in slots, as the model's normals meet them (y up): 'front' (flat) or 'side' (3D)
    lighting, RenderSystem's matrices on the level's lamps. Checked against what the game shows: side lighting leaves a
    block's top brightest, its left face lighter than its right, and front lighting a flat item's face fully lit."""
    if front:
        m = S(1, -1, 1) @ R('Y', -22.5) @ R('X', 135)
    else:
        m = R('Y', math.degrees(1.0821041)) @ R('X', math.degrees(3.2375858)) @ R('Y', -22.5) @ R('X', 135)
    return [(m.to_3x3() @ light).normalized() for light in LEVEL_LIGHTS]


class GameLook:
    """Materials that draw as the game does, their lamps settable per view."""

    def __init__(self):
        self.lamps = []

    def material(self, name, color=None, albedo=None, glow=None):
        m = bpy.data.materials.new('game_' + name)
        m.use_nodes = True
        n = Nodes(m)
        if albedo is not None:
            tex = n.new('ShaderNodeTexImage', interpolation='Closest')
            tex.image = albedo
            n.link(n.new('ShaderNodeUVMap', uv_map='UVMap').outputs[0], tex.inputs['Vector'])
            base = tex.outputs['Color']
        else:
            base = tuple(c / 255 for c in color) + (1.0,)
        geo = n.new('ShaderNodeNewGeometry')
        total = None
        for _ in range(2):
            lamp = n.new('ShaderNodeCombineXYZ')
            self.lamps.append(lamp)
            d = n.math('MAXIMUM', n.vmath('DOT_PRODUCT', geo.outputs['Normal'], lamp.outputs[0]), 0.0)
            total = d if total is None else n.math('ADD', total, d)
        col = n.rgb_scale(base, n.math('MINIMUM', n.math('ADD', n.math('MULTIPLY', total, 0.6), 0.4), 1.0))
        if glow is not None:
            gtex = n.new('ShaderNodeTexImage', interpolation='Closest')
            gtex.image = glow
            n.link(n.new('ShaderNodeUVMap', uv_map='UVMap').outputs[0], gtex.inputs['Vector'])
            add = n.new('ShaderNodeMix', data_type='RGBA', blend_type='ADD')
            n.socket(add, 'Factor_Float').default_value = 1.0
            n.link(col, n.socket(add, 'A_Color'))
            n.link(gtex.outputs['Color'], n.socket(add, 'B_Color'))
            col = n.socket(add, 'Result_Color', True)
        n.emission('surface', col)
        n.link(n.outputs['surface'], n.out.inputs['Surface'])
        return m

    def set_lamps(self, lamps_mc):
        """The lamps, given in Minecraft's axes."""
        for i, lamp in enumerate(self.lamps):
            d = M2B.to_3x3() @ lamps_mc[i % 2]
            for k in range(3):
                lamp.inputs[k].default_value = d[k]


def raw_image(path, name):
    img = bpy.data.images.load(os.path.abspath(path))
    img.name = name
    img.colorspace_settings.name = 'Non-Color'
    return img


def game_hammer(look):
    """The baked mesh read back from its file (in the item's pixels, on the diagonal), with its own normals."""
    quads = read_mesh(GAME_MESH)
    verts, faces, uvs, normals = [], [], [], []
    for _, corners in quads:
        if corners[3] == corners[2]:
            corners = corners[:3]
        faces.append(list(range(len(verts), len(verts) + len(corners))))
        for co, uv, n in corners:
            verts.append(co)
            uvs.append(uv)
            normals.append(Vector(n).normalized())
    me = bpy.data.meshes.new('game_hammer')
    me.from_pydata(verts, [], faces)
    uvl = me.uv_layers.new(name='UVMap')
    for poly in me.polygons:
        poly.use_smooth = True
        for li in poly.loop_indices:
            u, v = uvs[me.loops[li].vertex_index]
            uvl.data[li].uv = (u, 1.0 - v)
    me.normals_split_custom_set_from_vertices(normals)
    me.materials.append(look.material('hammer', albedo=raw_image(GAME_TEXTURE, 'game_albedo'),
                                      glow=raw_image(GAME_GLOW, 'game_glow')))
    o = bpy.data.objects.new('game_hammer', me)
    bpy.context.scene.collection.objects.link(o)
    o['game'] = True
    return o


def game_box(look, name, matrix, lo, hi, color):
    """A box from lo to hi (pixels / 16) through a Minecraft matrix, as the game's cuboids are."""
    corners = [Vector((x, y, z)) / 16 for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
    verts = [tuple(M2B @ matrix @ c) for c in corners]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(look.material(name, color=color))
    o = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(o)
    o['game'] = True
    return o


def game_scene(look):
    """The hammer and what stands round it in the game: a player (Steve's cuboids, holding it in the right hand),
    the grass, and a wall with an item frame."""
    hammer = game_hammer(look)
    skin, shirt, pants, hair = (190, 140, 108), (0, 160, 165), (58, 58, 160), (70, 48, 30)
    player = [('p_head', PLAYER, (-4, -8, -4), (4, 0, 4), skin),
              ('p_hair', PLAYER, (-4.2, -8.2, -4.2), (4.2, -6, 4.2), hair),
              ('p_body', PLAYER, (-4, 0, -2), (4, 12, 2), shirt),
              ('p_rarm', arm(1), (-3, -2, -2), (1, 10, 2), skin),
              ('p_larm', arm(-1, 0.0), (-1, -2, -2), (3, 10, 2), skin),
              ('p_rleg', PLAYER @ T(-1.9 / 16, 12 / 16, 0), (-2, 0, -2), (2, 12, 2), pants),
              ('p_lleg', PLAYER @ T(1.9 / 16, 12 / 16, 0), (-2, 0, -2), (2, 12, 2), pants)]
    people = [game_box(look, *p) for p in player]
    grass = game_box(look, 'grass', Matrix.Identity(4), (-400, -16, -400), (400, 0, 400), (98, 152, 60))
    wall = game_box(look, 'wall', T(0, -0.5, -1), (-48, -24, 0), (48, 24, 16), (122, 122, 122))
    frame = [game_box(look, 'frame', Matrix.Identity(4), (-6, -6, 0), (6, 6, 0.5), (150, 104, 62)),
             game_box(look, 'frame_back', Matrix.Identity(4), (-5, -5, 0.5), (5, 5, 0.6), (122, 84, 50))]
    return hammer, people, grass, [wall] + frame


GAME_VIEWS = [
    # name, the item's view, lamps, sky, camera (Minecraft's axes), target, vertical field of view, size, shows
    ('game_firstperson', 'firstperson', 'level', (120, 167, 255), (0, 0, 0), (0, 0, -1), 70, (960, 540), ()),
    ('game_firstperson_left', 'firstperson_left', 'level', (120, 167, 255), (0, 0, 0), (0, 0, -1), 70, (960, 540), ()),
    ('game_raise', 'raise', 'level', (52, 58, 72), (0, 0, 0), (0, 0, -1), 70, (960, 540), ()),
    ('game_thirdperson', 'thirdperson', 'level', (120, 167, 255), (-1.7, 1.55, 2.3), (-0.3, 1.05, 0.0), 50,
     (960, 540), ('people', 'grass')),
    ('game_thirdperson_back', 'thirdperson', 'level', (120, 167, 255), (-1.2, 1.9, -2.6), (-0.3, 1.0, 0.0), 50,
     (960, 540), ('people', 'grass')),
    ('game_ground', 'ground', 'level', (120, 167, 255), (0.9, 1.0, 1.5), (0, 0.25, 0), 50, (960, 540), ('grass',)),
    ('game_frame', 'fixed', 'level', (120, 167, 255), (0.35, 0.2, 1.35), (0, 0, 0), 50, (960, 540), ('wall',)),
]


def game_views(out, only):
    """The baked copy drawn as the game would draw it in every view, and in an inventory slot at GUI scales 1-3."""
    scene = bpy.context.scene
    studio()
    look = GameLook()
    hammer, people, grass, wall = game_scene(look)
    groups = {'people': people, 'grass': [grass], 'wall': wall}
    for o in scene.objects:
        if o.get('part') or o.get('studio'):
            o.hide_render = True
    sky = bpy.data.worlds.new('game_sky')
    sky.use_nodes = True
    scene.world = sky
    scene.render.engine = 'CYCLES'
    scene.cycles.use_denoising = False
    # Raw: the shader's numbers go straight to the file, as the game's do to its framebuffer.
    scene.view_settings.view_transform = 'Raw'
    scene.view_settings.look = 'None'
    cam = scene.camera
    cam.data.sensor_fit = 'VERTICAL'
    for name, view, lamps, sky_rgb, eye, target, fov, size, shows in GAME_VIEWS:
        if only and name not in only:
            continue
        look.set_lamps(LEVEL_LIGHTS)
        hammer.matrix_world = M2B @ view_matrix(view, upright=False)
        for key, objs in groups.items():
            for o in objs:
                o.hide_render = key not in shows
        sky.node_tree.nodes['Background'].inputs['Color'].default_value = tuple(c / 255 for c in sky_rgb) + (1,)
        cam.data.type = 'PERSP'
        cam.location = M2B @ Vector(eye)
        look_at(cam, M2B @ Vector(target))
        cam.data.angle_y = math.radians(fov)
        cam.data.clip_start, cam.data.clip_end = 0.01, 100
        scene.render.resolution_x, scene.render.resolution_y = size
        scene.render.film_transparent = False
        scene.cycles.samples = 8
        scene.cycles.filter_width = 1.0
        scene.render.filepath = os.path.join(out, name + '.png')
        bpy.ops.render.render(write_still=True)
        print('  rendered', name)
    if not only or 'game_gui' in only:
        for o in people + [grass] + wall:
            o.hide_render = True
        hammer.matrix_world = M2B @ view_matrix('gui', upright=False)
        cam.data.type = 'ORTHO'
        cam.data.ortho_scale = 24 / 16
        cam.location = M2B @ Vector((0, 0, 4))
        look_at(cam, (0, 0, 0))
        scene.render.film_transparent = True
        scene.cycles.samples = 1
        scene.cycles.filter_width = 0.01
        for light in ('front', 'side'):
            look.set_lamps(gui_lights(light == 'front'))
            for k in (1, 2, 3):
                scene.render.resolution_x = scene.render.resolution_y = 24 * k
                scene.render.filepath = os.path.join(out, 'gui_%s_%d.png' % (light, k))
                bpy.ops.render.render(write_still=True)
        slot_pictures(out)
        print('  rendered the slots')
    bpy.data.objects.remove(hammer)


def slot_pictures(out):
    """The slot renders laid on an inventory slot and a hotbar slot, 24 GUI pixels each, blown up 4 times."""
    for light in ('front', 'side'):
        row = []
        for k in (1, 2, 3):
            item_img = Image.open(os.path.join(out, 'gui_%s_%d.png' % (light, k))).convert('RGBA')
            for bg in ('inventory', 'hotbar'):
                s = 24 * k
                base = Image.new('RGBA', (s, s), (198, 198, 198, 255) if bg == 'inventory' else (40, 44, 52, 255))
                d = ImageDraw.Draw(base)
                o = 3 * k
                if bg == 'inventory':
                    d.rectangle([o, o, o + 18 * k - 1, o + 18 * k - 1], fill=(55, 55, 55, 255))
                    d.rectangle([o + k, o + k, o + 18 * k - 1, o + 18 * k - 1], fill=(255, 255, 255, 255))
                    d.rectangle([o + k, o + k, o + 17 * k - 1, o + 17 * k - 1], fill=(139, 139, 139, 255))
                else:
                    d.rectangle([o, o, o + 18 * k - 1, o + 18 * k - 1], fill=(20, 20, 20, 255))
                    d.rectangle([o + k, o + k, o + 17 * k - 1, o + 17 * k - 1], fill=(60, 60, 60, 255))
                base.alpha_composite(item_img)
                # Blown up to 288 pixels: 12, 6 or 4 screen pixels to the game's pixel, all whole.
                row.append(base.resize((288, 288), Image.NEAREST))
        sheet = Image.new('RGBA', (len(row) * 300 - 12, 288), (30, 30, 34, 255))
        for i, im in enumerate(row):
            sheet.paste(im, (i * 300, 0))
        sheet.save(os.path.join(out, 'game_slots_%s.png' % light))


# --- the contact sheet --------------------------------------------------------------------------------------------

SHEET = [
    ('Blender renders', [('hero', 'Hero, three quarters'), ('side', 'Front'), ('rune_end', 'The rune on the end'),
                         ('knotwork', 'The interlace'), ('haft', 'Collar, leather wrap, pommel and loop')]),
    ("As the game draws it (the baked mesh and textures, the entity shader's lighting, the glow added)",
     [('game_firstperson', 'First person'), ('game_firstperson_left', 'First person, left hand'),
      ('game_raise', 'Held high (HammerRaise)'), ('game_thirdperson', 'Third person'),
      ('game_thirdperson_back', 'Third person, from behind'), ('game_ground', 'On the ground'),
      ('game_frame', 'In an item frame')]),
    ('Inventory', [('inventory_pbr_64', 'Rendered: a 64 px slot (with margin)'),
                   ('game_slots_front', 'As the game draws it, GUI scale 1, 2 and 3, on light and dark slots')]),
]


def contact_sheet(out):
    width, row_h, pad = 2600, 520, 18
    try:
        font = ImageFont.load_default(size=22)
        big = ImageFont.load_default(size=30)
    except TypeError:
        font = big = ImageFont.load_default()
    rows = []
    for title, tiles in SHEET:
        imgs = []
        for name, label in tiles:
            path = os.path.join(out, name + '.png')
            if not os.path.exists(path):
                continue
            im = Image.open(path).convert('RGBA')
            h = row_h if im.height > 120 else im.height * 4
            w = int(im.width * h / im.height)
            if w > width - 2 * pad:
                w, h = width - 2 * pad, int(h * (width - 2 * pad) / w)
            imgs.append((im.resize((w, h), Image.LANCZOS if im.height > 120 else Image.NEAREST), label))
        if imgs:
            rows.append((title, imgs))
    lines = []
    for title, imgs in rows:
        line, x = [], pad
        for im, label in imgs:
            if x + im.width > width - pad and line:
                lines.append((title, line))
                title, line, x = None, [], pad
            line.append((im, label))
            x += im.width + pad
        lines.append((title, line))
    height = pad + sum((50 if t else 0) + max(im.height for im, _ in line) + 40 + pad for t, line in lines)
    sheet = Image.new('RGB', (width, height), (16, 18, 24))
    d = ImageDraw.Draw(sheet)
    y = pad
    for title, line in lines:
        if title:
            d.text((pad, y + 8), title, fill=(220, 226, 236), font=big)
            y += 50
        x = pad
        h = max(im.height for im, _ in line)
        for im, label in line:
            bg = Image.new('RGBA', im.size, (16, 18, 24, 255))
            bg.alpha_composite(im)
            sheet.paste(bg.convert('RGB'), (x, y))
            d.text((x, y + h + 8), label, fill=(170, 178, 192), font=font)
            x += im.width + pad
        y += h + 40 + pad
    path = os.path.join(out, 'mjolnir_contact_sheet.png')
    sheet.save(path)
    print('contact sheet ->', path)


def main():
    args = sys.argv[1:]
    only = set(args[args.index('--only') + 1].split(',')) if '--only' in args else None
    out = args[args.index('--out') + 1] if '--out' in args else None
    if out:
        os.makedirs(out, exist_ok=True)
    if out and args == ['--out', out, '--sheet']:
        contact_sheet(out)
        return
    parts = build()
    if '--fit' in args:
        fit_report(parts)
    if '--game' in args:
        export_game(parts)
    if not out:
        return
    if '--renders' in args:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(os.path.join(out, 'mjolnir.blend')), check_existing=False)
        hero_renders(out, only)
    if '--views' in args:
        game_views(out, only)
    if '--sheet' in args:
        contact_sheet(out)


if __name__ == '__main__':
    main()
