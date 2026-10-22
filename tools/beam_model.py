"""Builds Beam, the shark fiend of the Reze arc, in Blender: a smooth anime-style model, painted in flat anime colours.

Run from the repository root with Blender (4.3 here; no bpy module and no Pillow needed, only Blender's own numpy):

    blender -b --factory-startup --python tools/beam_model.py -- --out DIR --renders   # preview renders, for approval
    blender -b --factory-startup --python tools/beam_model.py -- --out DIR --bake      # the game's mesh and texture, in DIR
    blender -b --factory-startup --python tools/beam_model.py -- --game                # the same, into chainsawman/ (not run yet)

Add --only name,name to render some of them. The renders show the game's look: the toon texture (painted by
chainsaw_model.py's paint_toon, copied here: flat colour per material, one shade tone, ink on sharp edges, at a chunky
texel size, sampled nearest) on the mesh the game will draw, flat lit, no realistic materials.

Blocks are the build's unit (a player is 1.8 tall); it is scaled to pixels (16 to a block) before the game's copy is made.
Beam is built standing on the ground (z = 0), facing -y, z up, x to his left, the middle of his back at the origin, so the
snout is at y = -2.5 and the tail tip at y = +2.5. 'u' below is the distance back from the snout tip (y = u - 2.5).

What he is, from kortev's still of the movie, the official IMAX poster and Prime 1 Studio's statue of the Reze arc: a huge,
heavy, smoothly rounded shark about 5 blocks from snout to tail, deepest (2.2) just behind the head, about 2 wide, slung
with his belly 0.8 off the ground, navy on top and pale mint over his whole lower half (the line between them crisp and
slightly jagged, rising on the head almost to the eyes). The head is two fifths of him, a blunt rounded snout tilted a
little up, the mouth hanging wide open (the lower jaw its own part, 38 degrees down) with dark red inside, pink gums and
rows of cream triangular teeth along both jaws. Three bulging eyes up each side from above the back corner of the mouth,
the front one biggest. Five navy spikes down his back, gill lines behind the mouth, small pectoral fins, a crescent tail
(the upper lobe longer). And four thin crab legs out of the lower sides of his body: out sideways and up to a high knee
well outside the body, then down and a little in to a claw-like point on the ground.

Parts for the game (each its own object(s), pivot in build blocks listed in PIVOTS): body (with the head's upper jaw, the
eyes, spikes, pectorals), jaw (hinge at the mouth's back corner, swings about x), tail (swings about z at its root), and
leg_{fl,fr,bl,br}_up / _low (hip and knee, swing about x). The rider and the reins are only there to judge proportions.
"""
import math
import os
import struct
import sys
import zlib

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
from mathutils import Matrix, Vector
import numpy as np

BLOCK = 16.0

# --- layout (blocks; feet on z = 0, facing -y, z up; y = u - 2.5 for u back from the snout tip) -------------------------

Y0 = -2.5
LENGTH = 5.0
# The body's sections, along it. Each is a rounded section (a superellipse, SEC_EXP) wider than tall at its widest line
# zm: A above that line to the back, B below it to the belly, HW either side. In the head B is negative: the mouth's roof
# arches up from the lip (the rim, at zm), and the section is a D. From the hinge the throat swells down into the chest.
# u, zm, A, B, HW
BODY = [
    (0.00, 2.62, 0.78, -0.10, 0.70),
    (0.35, 2.20, 0.80, -0.10, 0.70),
    (0.70, 2.00, 1.00, -0.12, 0.90),
    (1.10, 1.90, 1.15, -0.12, 1.00),
    (1.50, 1.84, 1.26, -0.12, 1.06),
    (1.95, 1.80, 1.40, -0.12, 1.10),
    (2.10, 1.80, 1.42, 0.12, 1.07),
    (2.25, 1.80, 1.42, 0.50, 1.05),
    (2.45, 1.78, 1.42, 0.85, 1.02),
    (2.80, 1.74, 1.36, 0.92, 0.97),
    (3.20, 1.64, 1.14, 0.84, 0.84),
    (3.60, 1.54, 0.82, 0.60, 0.66),
    (3.85, 1.50, 0.58, 0.45, 0.50),
    (4.10, 1.52, 0.36, 0.28, 0.35),
    (4.40, 1.55, 0.19, 0.15, 0.20),
    (4.52, 1.55, 0.09, 0.08, 0.10),
]
SEC_EXP = 2.3
SEC_N = 32  # points round a section (even: the rim is at 0 and half way)
SNOUT_R = 0.35  # the snout is rounded off (an ellipse) over this many blocks
U_HINGE = 1.95  # the mouth's back corner
U_WALL = 2.5  # the throat's wall is done by here
# The body ends at BODY_END, where the tail's stem takes over (its root starts earlier, a little inside the body).
BODY_END = 3.85
TAIL_ROOT = 3.5
TAIL_PIVOT_U = 3.6
# The line between navy and mint on his side (u, z), the sawtooth's size and period along him.
BOUNDARY = [(0.0, 2.50), (0.3, 2.52), (0.8, 2.66), (1.3, 2.62), (1.7, 2.50), (2.1, 2.35), (2.6, 2.05), (3.1, 1.90),
            (3.6, 1.72), (4.0, 1.62), (4.6, 1.56)]
JAG = (0.075, 3.1)
# The gill lines behind the mouth: where each starts (u at the top), the top and bottom (z), how far each slants back and
# how much it bows, the line's half width.
GILLS = [2.18, 2.34, 2.50, 2.66]
GILL_Z = (2.42, 1.72)
GILL_SLANT = 0.30
GILL_BOW = 0.07
GILL_W = 0.026
# The lower jaw: its length from the hinge, how far it hangs open against the upper lip (degrees), its half width and the
# depth under the rim at stations along it (v from the hinge), the dish of its floor.
JAW_LEN = 1.85
JAW_OPEN = 46.0
JAW_ST = [(0.0, 0.98, 1.05), (0.3, 1.02, 0.96), (0.9, 0.88, 0.72), (1.4, 0.62, 0.46), (1.7, 0.34, 0.28), (1.85, 0.15, 0.12)]
JAW_DISH = 0.16
JAW_ROUND = 0.28
# Teeth: how many a side (upper, lower), the longest and the base width as part of the spacing.
TEETH_SIDE = (14, 13)
TOOTH_LONG = (0.28, 0.24)
TOOTH_SHORT = 0.17
# Eyes: (u, z, radius), the front one biggest, in a diagonal up and back from above the corner of the mouth.
EYES = [(1.52, 2.52, 0.25), (1.92, 2.78, 0.195), (2.30, 3.00, 0.15)]
# The dorsal spikes: (u, height, base length), the front one biggest, swept back.
SPIKES = [(1.80, 0.80, 0.55), (2.28, 0.64, 0.46), (2.74, 0.52, 0.40), (3.14, 0.42, 0.34), (3.50, 0.32, 0.28)]
SPIKE_SWEEP = 0.9
# The pectorals: root (u, z), tip offset (outward, back, down), chord at the root.
PECTORAL = ((2.30, 2.0), (0.75, 0.55, -0.40), 0.28)
# The tail's fin: the leading (convex) edge and the trailing (concave) edge, from the lower tip to the upper tip: (u, z).
FIN_LEAD = [(4.95, 0.60), (4.45, 0.95), (4.12, 1.55), (4.22, 2.30), (5.00, 3.20)]
FIN_TRAIL = [(4.95, 0.60), (4.72, 1.00), (4.60, 1.58), (4.66, 2.30), (5.00, 3.20)]
FIN_THICK = 0.075
# The legs: for each (front pair, back pair): hip (u, z) and how far in the root starts, knee (out, u, z), foot (out, u).
# They are thin and curved; the upper segment bows up, the shin out.
LEGS = {
    'f': dict(hip=(2.45, 1.14), root_x=0.56, knee=(2.12, 2.25, 2.30), foot=(1.66, 2.00)),
    'b': dict(hip=(3.45, 1.18), root_x=0.40, knee=(2.06, 3.68, 2.30), foot=(1.64, 3.94)),
}
LEG_R = (0.30, 0.19)  # the upper segment's radius at the hip and at the knee
SHIN_W = (0.19, 0.10)  # the shin's half width (fore-aft) and half thickness at the knee; it tapers to a point
# The rider: Denji's stand-in, standing on the middle of the back: feet at these u, knees bent.
RIDER_FEET = (2.15, 2.85)
# Where the chain's ends meet the mouth (u on the lip) and where the hands are (y, z, half apart).
REIN_LIP_U = 1.62
HANDS = (-0.62, 4.0, 0.2)

# --- the look ------------------------------------------------------------------------------------------------------------

# Each material's flat colours in the game (sRGB): light, shadow, ink.
TOON = {
    'navy': ((46, 88, 166), (30, 56, 122), (12, 18, 50)),
    'belly': ((208, 236, 228), (146, 186, 188), (40, 70, 88)),
    'spike': ((26, 40, 100), (16, 26, 68), (8, 10, 30)),
    'gum': ((228, 114, 134), (186, 80, 102), (90, 24, 40)),
    'throat': ((92, 16, 30), (62, 10, 22), (40, 6, 12)),
    'tooth': ((250, 240, 210), (214, 198, 162), (80, 60, 50)),
    'sclera': ((246, 244, 226), (216, 214, 192), (40, 40, 60)),
    'pupil': ((10, 10, 16), (10, 10, 16), (0, 0, 0)),
    'ring': ((12, 18, 44), (12, 18, 44), (0, 0, 0)),
    'skin': ((46, 88, 166), (30, 56, 122), (12, 18, 50)),  # the body's own rule (toon_colour); only its ink is used
}
INK = (12, 18, 50)
# The light the shadow tone is cut against: from in front, above and to his left.
TOON_LIGHT = Vector((0.35, -0.55, 0.75)).normalized()
TOON_SHADOW_AT = 0.12
INK_ANGLE = 38.0
TEXELS_PER_PIXEL = 2.0
# The stand-in's flat colours (light, shadow); not in the game.
STAND_IN = {
    'r_shirt': ((238, 238, 230), (190, 190, 184)),
    'r_pants': ((52, 66, 104), (34, 44, 76)),
    'r_skin': ((236, 178, 146), (200, 140, 112)),
    'r_hair': ((226, 184, 92), (178, 138, 62)),
    'r_shoe': ((232, 232, 226), (180, 180, 176)),
    'r_chain': ((96, 98, 108), (56, 58, 66)),
    'ground': ((96, 150, 84), (96, 150, 84)),
    'blob': ((70, 118, 66), (70, 118, 66)),
}
# Parts for the game: the part it hangs from ('' for the body), the pivot in blocks (build space), the axis it swings about.
PIVOTS = {
    'body': ('', (0.0, 0.0, 3.0), (1, 0, 0)),
    'jaw': ('body', (0.0, U_HINGE + Y0, 1.80), (1, 0, 0)),
    'tail': ('body', (0.0, TAIL_PIVOT_U + Y0, 1.54), (0, 0, 1)),
}
for _side, _sx in (('l', 1), ('r', -1)):
    for _end in ('f', 'b'):
        _l = LEGS[_end]
        _hip = (_sx * _l['root_x'], _l['hip'][0] + Y0, _l['hip'][1])
        _knee = (_sx * _l['knee'][0], _l['knee'][1] + Y0, _l['knee'][2])
        _n = 'leg_%s%s' % (_end, _side)
        PIVOTS[_n + '_up'] = ('body', _hip, (1, 0, 0))
        PIVOTS[_n + '_low'] = (_n + '_up', _knee, (1, 0, 0))

MATS = {}


def Y(u):
    return u + Y0


# --- maths -------------------------------------------------------------------------------------------------------------

def pchip(xs, ys):
    """A smooth curve through the points that does not overshoot (monotone cubic); takes a number or an array."""
    xs = np.array(xs, float)
    ys = np.array(ys, float)
    h = np.diff(xs)
    d = np.diff(ys) / h
    m = np.zeros(len(xs))
    for i in range(1, len(xs) - 1):
        if d[i - 1] * d[i] > 0:
            w1 = 2 * h[i] + h[i - 1]
            w2 = h[i] + 2 * h[i - 1]
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])

    def end(h0, h1, d0, d1):
        s = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
        if np.sign(s) != np.sign(d0):
            return 0.0
        if np.sign(d0) != np.sign(d1) and abs(s) > 3 * abs(d0):
            return 3 * d0
        return s

    if len(xs) > 2:
        m[0] = end(h[0], h[1], d[0], d[1])
        m[-1] = end(h[-1], h[-2], d[-1], d[-2])
    else:
        m[0] = m[-1] = d[0]

    def f(x):
        x = np.clip(np.asarray(x, float), xs[0], xs[-1])
        i = np.clip(np.searchsorted(xs, x, side='right') - 1, 0, len(xs) - 2)
        t = (x - xs[i]) / h[i]
        t2, t3 = t * t, t * t * t
        return ((2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h[i] * m[i] + (-2 * t3 + 3 * t2) * ys[i + 1]
                + (t3 - t2) * h[i] * m[i + 1])

    return f


def smoothstep(a, b, x):
    t = np.clip((np.asarray(x, float) - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


_BODY_T = np.array(BODY)
_zm = pchip(_BODY_T[:, 0], _BODY_T[:, 1])
_A = pchip(_BODY_T[:, 0], _BODY_T[:, 2])
_B = pchip(_BODY_T[:, 0], _BODY_T[:, 3])
_HW = pchip(_BODY_T[:, 0], _BODY_T[:, 4])
_BOUND = pchip([p[0] for p in BOUNDARY], [p[1] for p in BOUNDARY])


def profile(u):
    """The body's section at u: (zm, A, B, HW), the snout rounded off."""
    u = float(u)
    f = 1.0
    if u < SNOUT_R:
        f = math.sqrt(max(0.0, 1.0 - ((SNOUT_R - u) / SNOUT_R) ** 2))
    return float(_zm(u)), float(_A(u)) * f, float(_B(u)) * f, float(_HW(u)) * f


def section(u, scale=1.0, n=SEC_N):
    """The loop of points round the body at u: from the left rim (t = 0) over the back to the right rim and back under."""
    zm, A, B, hw = profile(u)
    pts = []
    for j in range(n):
        t = 2 * math.pi * j / n
        c, s = math.cos(t), math.sin(t)
        x = hw * math.copysign(abs(c) ** (2 / SEC_EXP), c)
        e = abs(s) ** (2 / SEC_EXP)
        z = zm + A * e if s >= 0 else zm - B * e
        pts.append((x * scale, Y(u), zm + (z - zm) * scale))
    return pts


def body_us(u0, u1):
    """The stations along the body between u0 and u1: the snout in an ellipse's steps, finer through the throat."""
    us = []
    if u0 < SNOUT_R:
        for phi in np.linspace(0.16, math.pi / 2, 7):
            us.append(SNOUT_R * (1 - math.cos(phi)))
    us += list(np.linspace(max(u0, SNOUT_R), 1.9, 16)[1:])
    us += list(np.linspace(1.9, 2.65, 16)[1:])
    us += list(np.linspace(2.65, 4.52, 24)[1:])
    return [u for u in us if u0 <= u <= u1 + 1e-9]


# --- building blocks ---------------------------------------------------------------------------------------------------

def new_material(name):
    mat = bpy.data.materials.new(name)
    base = name[:-2] if name.endswith('_s') and name[:-2] in STAND_IN else name
    tones = TOON.get(base) or STAND_IN.get(base) or ((200, 0, 200),)
    light = tones[1] if base != name else tones[0]
    mat.diffuse_color = (*[(c / 255.0) ** 2.2 for c in light], 1.0)
    MATS[name] = mat
    return mat


def make_materials():
    for name in TOON:
        new_material(name)
    for name in STAND_IN:
        new_material(name)
        new_material(name + '_s')


def link(obj, part):
    bpy.context.scene.collection.objects.link(obj)
    obj['part'] = part
    return obj


def finish(bm, smooth, sharp_angle=45.0):
    """Faces facing outward; smooth shaded with a crease wherever the surface turns sharply."""
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    for f in bm.faces:
        f.smooth = smooth
    if smooth:
        for e in bm.edges:
            if len(e.link_faces) == 2 and math.degrees(e.link_faces[0].normal.angle(e.link_faces[1].normal, 0.0)) > sharp_angle:
                e.smooth = False


def mesh_obj(name, bm, mat_names, part, smooth=True, pivot=None):
    finish(bm, smooth)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for m in mat_names:
        me.materials.append(MATS[m])
    obj = bpy.data.objects.new(name, me)
    return link(obj, part)


def loft(sections, cap0=True, cap1=True, classify=None):
    """A closed surface through loops of points (ni x nj), a cell's material from classify(i, j, face) -> index."""
    bm = bmesh.new()
    ni, nj = len(sections), len(sections[0])
    verts = [[bm.verts.new(Vector(p)) for p in row] for row in sections]
    cells = []
    for i in range(ni - 1):
        for j in range(nj):
            j2 = (j + 1) % nj
            f = bm.faces.new((verts[i][j], verts[i][j2], verts[i + 1][j2], verts[i + 1][j]))
            cells.append((i, j, f))
    if cap0:
        bm.faces.new(verts[0][::-1])
    if cap1:
        bm.faces.new(verts[-1])
    if classify:
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        for i, j, f in cells:
            f.material_index = classify(i, j, f)
    return bm


def sweep(spine, wides, a, b, nj=12, classify=None):
    """A tube along the spine points whose section is an ellipse a wide (along the given unit vector) by b thick."""
    secs = []
    for k, p in enumerate(spine):
        p = Vector(p)
        t = (Vector(spine[min(k + 1, len(spine) - 1)]) - Vector(spine[max(k - 1, 0)])).normalized()
        w = Vector(wides[k]) if not isinstance(wides, Vector) else wides
        w = (w - t * w.dot(t)).normalized()
        v = t.cross(w).normalized()
        row = []
        for j in range(nj):
            ang = 2 * math.pi * j / nj
            row.append(p + w * (a[k] * math.cos(ang)) + v * (b[k] * math.sin(ang)))
        secs.append(row)
    return loft(secs, classify=classify)


def bezier(p0, p1, p2, n):
    out = []
    for k in range(n):
        t = k / (n - 1)
        out.append(Vector(p0) * (1 - t) ** 2 + Vector(p1) * 2 * t * (1 - t) + Vector(p2) * t * t)
    return out


def tetra(bm, c, tangent, inward, direction, width, length):
    """A tooth: its base a triangle across the rim (along the tangent, a point inside) and its point out along direction."""
    t = Vector(tangent).normalized()
    b0 = Vector(c) - t * width / 2
    b1 = Vector(c) + t * width / 2
    b2 = Vector(c) + Vector(inward).normalized() * width * 0.55
    apex = Vector(c) + Vector(direction).normalized() * length
    vs = [bm.verts.new(v) for v in (b0, b1, b2, apex)]
    for tri in ((0, 1, 3), (1, 2, 3), (2, 0, 3), (2, 1, 0)):
        bm.faces.new([vs[k] for k in tri])


def rim_teeth(rim, count, lengths, down, width_share=0.5):
    """Teeth hung from a rim polyline (left hinge, round the front, right hinge): count of them evenly along it."""
    rim = [Vector(p) for p in rim]
    seg = [(rim[k + 1] - rim[k]).length for k in range(len(rim) - 1)]
    total = sum(seg)
    centre = sum(rim, Vector()) / len(rim)
    spacing = total / count
    bm = bmesh.new()
    for k in range(count):
        d = spacing * (k + 0.5)
        acc = 0.0
        for s in range(len(seg)):
            if acc + seg[s] >= d:
                break
            acc += seg[s]
        t = (d - acc) / seg[s]
        p = rim[s].lerp(rim[s + 1], t)
        tan = (rim[s + 1] - rim[s]).normalized()
        inward = (centre - p)
        inward.z = 0.0
        inward -= tan * inward.dot(tan)
        if inward.length < 1e-6:
            inward = Vector((0, 1, 0))
        inward.normalize()
        f = (k + 0.5) / count
        edge = min(f, 1 - f) * 2.0
        length = lengths[1] * (0.58 + 0.42 * math.sin(math.pi * min(edge, 1.0)) ** 0.8)
        direction = Vector(down) * 1.0 + inward * 0.12
        base = p + inward * 0.03 - Vector(down) * 0.03
        tetra(bm, base, tan, inward, direction, spacing * width_share, length)
    return bm


# --- the body ----------------------------------------------------------------------------------------------------------

def classify_body(us):
    def classify(i, j, f):
        u = (us[i] + us[i + 1]) / 2
        if j < SEC_N // 2:
            return 0  # skin
        if u < U_HINGE:
            return 1 if j in (SEC_N // 2, SEC_N // 2 + 1, SEC_N - 2, SEC_N - 1) else 2  # gum at the rim, else the roof
        if u < U_WALL and f.calc_center_median().z > 1.15:
            return 2  # the back of the throat
        return 0
    return classify


def build_body():
    us = body_us(0.0, BODY_END)
    secs = [section(u) for u in us]
    bm = loft(secs, classify=classify_body(us))
    mesh_obj('body', bm, ['skin', 'gum', 'throat'], 'body')


def build_tail():
    us = [u for u in body_us(TAIL_ROOT, 4.52)]
    us = sorted(set([TAIL_ROOT] + us))
    secs = []
    for u in us:
        scale = 0.93 + 0.07 * float(smoothstep(TAIL_ROOT, 3.8, u))
        secs.append(section(u, scale))
    bm = loft(secs)
    mesh_obj('tail_stem', bm, ['skin'], 'tail')
    # The crescent fin: a surface between its leading and trailing edges, a lens through (thin at its edges).
    ls = np.linspace(-1, 1, len(FIN_LEAD))
    lu, lz = pchip(ls, [p[0] for p in FIN_LEAD]), pchip(ls, [p[1] for p in FIN_LEAD])
    tu, tz = pchip(ls, [p[0] for p in FIN_TRAIL]), pchip(ls, [p[1] for p in FIN_TRAIL])
    secs = []
    rs = np.linspace(0, 1, 7)
    for s in np.linspace(-0.975, 0.975, 30):
        th = FIN_THICK * (1 - 0.55 * abs(s)) + 0.01
        loop = []
        for side in (1, -1):
            for r in (rs if side == 1 else rs[::-1]):
                if side == -1 and r in (rs[0], rs[-1]):
                    continue
                if side == 1 or True:
                    u = float(lu(s) + (tu(s) - lu(s)) * r)
                    z = float(lz(s) + (tz(s) - lz(s)) * r)
                    x = side * th * (math.sin(math.pi * r) ** 0.7 + 0.12)
                    loop.append((x, Y(u), z))
        secs.append(loop)
    bm = loft(secs)
    mesh_obj('tail_fin', bm, ['navy'], 'tail')


def build_jaw():
    """The lower jaw: built straight from its hinge (the hinge at the origin, running forward along -y, rim at z = 0), then
    turned down JAW_OPEN against the upper lip and put at the hinge."""
    st = np.array(JAW_ST)
    hw = pchip(st[:, 0], st[:, 1])
    dj = pchip(st[:, 0], st[:, 2])
    zm_h = float(_zm(U_HINGE))
    slope = math.degrees(math.atan2(float(_zm(0.0)) - float(_zm(U_HINGE)), U_HINGE))  # the upper lip's tilt up forward
    beta = math.radians(JAW_OPEN - slope)
    M = Matrix.Translation((0, Y(U_HINGE), zm_h)) @ Matrix.Rotation(beta, 4, 'X')
    vs = [0.0]
    vs += list(np.linspace(0.04, JAW_LEN - JAW_ROUND, 10))
    for phi in np.linspace(0.0, math.pi / 2 - 0.16, 6):
        vs.append(JAW_LEN - JAW_ROUND + JAW_ROUND * math.sin(phi) * 1.0)
    vs.append(JAW_LEN - 0.004)
    vs = sorted(set(vs))

    def jaw_section(v):
        f = 1.0
        if v > JAW_LEN - JAW_ROUND:
            f = math.sqrt(max(0.0, 1.0 - ((v - (JAW_LEN - JAW_ROUND)) / JAW_ROUND) ** 2))
        w, d = float(hw(v)) * f, float(dj(v)) * f
        dish = JAW_DISH * f
        pts = []
        for j in range(SEC_N):
            t = 2 * math.pi * j / SEC_N
            c, s = math.cos(t), math.sin(t)
            x = w * math.copysign(abs(c) ** (2 / SEC_EXP), c)
            e = abs(s) ** (2 / SEC_EXP)
            z = -dish * e if s >= 0 else -d * e
            pts.append(M @ Vector((x, -v, z)))
        return pts

    secs = [jaw_section(v) for v in vs]

    def classify(i, j, f):
        if j >= SEC_N // 2:
            return 0  # the underside, mint
        return 1 if j in (0, 1, SEC_N // 2 - 2, SEC_N // 2 - 1) else 2  # gum at the rim, else the floor
    bm = loft(secs, classify=classify)
    mesh_obj('jaw', bm, ['belly', 'gum', 'throat'], 'jaw')
    # Its teeth along the rim, standing up.
    rim = [M @ Vector((float(hw(v)) * (1 if True else 0), -v, 0)) for v in np.linspace(0.18, JAW_LEN - JAW_ROUND, 24)]
    front = []
    for phi in np.linspace(0, math.pi, 14)[1:-1]:
        f = math.sin(phi)
        x = float(hw(JAW_LEN - JAW_ROUND)) * math.cos(phi)
        front.append(M @ Vector((x, -(JAW_LEN - JAW_ROUND) - JAW_ROUND * 0.9 * math.sin(phi) * 0.85, 0)))
    right = [M @ Vector((-float(hw(v)), -v, 0)) for v in np.linspace(JAW_LEN - JAW_ROUND, 0.18, 24)]
    # in order: left (x > 0) from the hinge forward, round the chin, the right back to the hinge
    left = rim
    chain = left + front + right
    count = TEETH_SIDE[1] * 2
    up = M.to_3x3() @ Vector((0, 0, 1))
    bm = rim_teeth(chain, count, (TOOTH_LONG[1] * 0.55, TOOTH_LONG[1]), up)
    mesh_obj('teeth_lower', bm, ['tooth'], 'jaw', smooth=False)


def build_teeth_upper():
    rim = []
    us = np.linspace(U_HINGE - 0.05, 0.28, 40)
    left = [Vector((profile(u)[3], Y(u), profile(u)[0])) for u in us]
    front = []
    for phi in np.linspace(0, math.pi, 16)[1:-1]:
        u = 0.28 - 0.26 * math.sin(phi)
        zm, A, B, hw = profile(max(u, 0.03))
        front.append(Vector((math.cos(phi) * hw, Y(max(u, 0.03)), zm)))
    right = [Vector((-p.x, p.y, p.z)) for p in left[::-1]]
    rim = left + front + right
    bm = rim_teeth(rim, TEETH_SIDE[0] * 2, (TOOTH_LONG[0] * 0.45, TOOTH_LONG[0]), (0, 0, -1))
    mesh_obj('teeth_upper', bm, ['tooth'], 'body', smooth=False)


def surface_x(u, z):
    """How far out the body's side is at u, z (the left side), and the outward direction there."""
    zm, A, B, hw = profile(u)
    n = SEC_EXP
    r = (z - zm) / (A if z >= zm else B)
    x = hw * max(0.0, 1.0 - abs(r) ** n) ** (1 / n)
    # the outward direction: the gradient of the section's implicit surface
    def F(xx, uu, zz):
        zm2, A2, B2, hw2 = profile(uu)
        rr = (zz - zm2) / (A2 if zz >= zm2 else B2)
        return (abs(xx) / hw2) ** n + abs(rr) ** n
    h = 1e-3
    g = Vector(((F(x + h, u, z) - F(x - h, u, z)), (F(x, u + h, z) - F(x, u - h, z)), (F(x, u, z + h) - F(x, u, z - h))))
    g.y = -g.y * 0 + g.y  # u grows with y, so the gradient in y is the gradient in u
    return x, g.normalized()


def build_eyes():
    rings, whites, pupils = bmesh.new(), bmesh.new(), bmesh.new()
    for side in (1, -1):
        for (u, z, r) in EYES:
            x, nrm = surface_x(u, z)
            nrm = Vector((nrm.x * side, nrm.y, nrm.z)).normalized()
            centre = Vector((x * side, Y(u), z))
            # a basis with the outward direction as z, forward (-y) as y
            zz = nrm
            yy = (Vector((0, -1, 0)) - zz * Vector((0, -1, 0)).dot(zz)).normalized()
            xx = yy.cross(zz).normalized()
            basis = Matrix(((xx.x, yy.x, zz.x), (xx.y, yy.y, zz.y), (xx.z, yy.z, zz.z))).to_4x4()

            def ball(bm, radius, flat, push, shift=0.0):
                m = Matrix.Translation(centre + zz * push + yy * shift) @ basis @ Matrix.Diagonal((radius, radius, radius * flat, 1.0))
                bmesh.ops.create_uvsphere(bm, u_segments=18, v_segments=10, radius=1.0, matrix=m)
            ball(rings, r + 0.016, 0.55, -0.005)
            ball(whites, r, 0.55, 0.035)
            ball(pupils, r * 0.62, 0.40, 0.085 * r / 0.27 + 0.035, shift=r * 0.2)
    mesh_obj('eye_ring', rings, ['ring'], 'body')
    mesh_obj('eye_white', whites, ['sclera'], 'body')
    mesh_obj('eye_pupil', pupils, ['pupil'], 'body')


def build_spikes():
    bm = bmesh.new()
    for (u, h, base) in SPIKES:
        zm, A, B, hw = profile(u)
        z0 = zm + A - 0.08
        spine = []
        wides = []
        a, b = [], []
        n = 9
        for k in range(n):
            t = k / (n - 1)
            spine.append(Vector((0, Y(u) + SPIKE_SWEEP * h * (t ** 1.8), z0 + h * t)))
            wides.append(Vector((0, 1, 0)))
            a.append(base / 2 * (1 - t) ** 1.0 + 0.012)
            b.append(0.07 * (1 - t) ** 0.8 + 0.008)
        one = sweep(spine, wides, a, b, nj=8)
        # copy the sweep's faces into the shared bmesh
        vmap = {}
        for v in one.verts:
            vmap[v] = bm.verts.new(v.co)
        for f in one.faces:
            bm.faces.new([vmap[v] for v in f.verts])
        one.free()
    mesh_obj('spikes', bm, ['spike'], 'body')


def build_pectorals():
    (u, z), (out, back, down), chord = PECTORAL
    bm = bmesh.new()
    for side in (1, -1):
        x0, nrm = surface_x(u, z)
        start = Vector((side * (x0 - 0.12), Y(u), z))
        tip = Vector((side * (x0 + out), Y(u) + back, z + down))
        mid = (start + tip) / 2 + Vector((0, 0.05, 0.16))
        spine = bezier(start, mid, tip, 8)
        wides = [Vector((0, 1, 0))] * 8
        a = [chord / 2 * (1 - k / 7) ** 0.9 + 0.012 for k in range(8)]
        b = [0.06 * (1 - k / 7) ** 0.7 + 0.01 for k in range(8)]

        def classify(i, j, f):
            return 0 if f.normal.z > -0.05 else 1
        one = sweep(spine, wides, a, b, nj=10, classify=classify)
        vmap = {}
        for v in one.verts:
            vmap[v] = bm.verts.new(v.co)
        for f in one.faces:
            nf = bm.faces.new([vmap[v] for v in f.verts])
            nf.material_index = f.material_index
        one.free()
    mesh_obj('pectorals', bm, ['navy', 'belly'], 'body')


def leg_points(side, end):
    spec = LEGS[end]
    hip = Vector((side * spec['root_x'], Y(spec['hip'][0]), spec['hip'][1]))
    knee = Vector((side * spec['knee'][0], Y(spec['knee'][1]), spec['knee'][2]))
    foot = Vector((side * spec['foot'][0], Y(spec['foot'][1]), 0.0))
    return hip, knee, foot


def build_legs():
    for side, sname in ((1, 'l'), (-1, 'r')):
        for end in ('f', 'b'):
            hip, knee, foot = leg_points(side, end)
            name = 'leg_%s%s' % (end, sname)
            # The upper segment: out and up, bowed upward; round.
            mid = (hip + knee) / 2 + Vector((-side * 0.05, 0, 0.26))
            spine = bezier(hip, mid, knee, 12)
            wides = [Vector((0, 1, 0))] * 12
            r = [LEG_R[0] + (LEG_R[1] - LEG_R[0]) * (k / 11) ** 0.8 for k in range(12)]
            out = Vector((side * 0.55, 0, 0.85)).normalized()

            def classify_up(i, j, f, out=out):
                return 0 if f.normal.dot(out) > -0.15 else 1
            bm = sweep(spine, wides, r, r, nj=10, classify=classify_up)
            # The knee, a round joint (covers the gap when the shin swings).
            kb = bmesh.new()
            bmesh.ops.create_uvsphere(kb, u_segments=10, v_segments=6, radius=LEG_R[1] * 1.12,
                                      matrix=Matrix.Translation(knee))
            vmap = {}
            for v in kb.verts:
                vmap[v] = bm.verts.new(v.co)
            for f in kb.faces:
                nf = bm.faces.new([vmap[v] for v in f.verts])
                nf.material_index = 0
            kb.free()
            obj = mesh_obj(name + '_up', bm, ['navy', 'belly'], name + '_up')
            # The shin: a thin blade, flat along the fore-aft, tapering to a sharp point on the ground, bowing outward.
            mid = (knee + foot) / 2 + Vector((side * 0.12, 0, 0.05))
            n = 14
            spine = bezier(knee, mid, foot, n)
            wides = [Vector((0, 1, 0))] * n
            a = [SHIN_W[0] * (1 - k / (n - 1)) ** 0.9 * (1 + 0.3 * math.sin(math.pi * k / (n - 1))) + 0.006 for k in range(n)]
            b = [SHIN_W[1] * (1 - k / (n - 1)) ** 0.95 + 0.006 for k in range(n)]
            out2 = Vector((side * 1.0, 0, 0.15)).normalized()

            def classify_low(i, j, f, out2=out2):
                return 0 if f.normal.dot(out2) > -0.25 else 1
            bm = sweep(spine, wides, a, b, nj=10, classify=classify_low)
            mesh_obj(name + '_low', bm, ['navy', 'belly'], name + '_low')


def build_beam():
    build_body()
    build_teeth_upper()
    build_eyes()
    build_spikes()
    build_pectorals()
    build_tail()
    build_jaw()
    build_legs()


# --- the stand-in rider and the reins (only to judge proportions; not in the game) -----------------------------------------

def tone_box(name, verts_fn, mat):
    pass


def box_between(name, p0, p1, w, d, mat, part='rider', up_hint=(1, 0, 0)):
    """A box w x d across, running from p0 to p1; faces take the light or shadow material by where they face."""
    p0, p1 = Vector(p0), Vector(p1)
    axis = (p1 - p0)
    length = axis.length
    zaxis = axis.normalized()
    xaxis = (Vector(up_hint) - zaxis * Vector(up_hint).dot(zaxis)).normalized()
    yaxis = zaxis.cross(xaxis)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = (p0 + p1) / 2 + xaxis * (v.co.x * w) + yaxis * (v.co.y * d) + zaxis * (v.co.z * length)
    bm.normal_update()
    for f in bm.faces:
        f.material_index = 0 if f.normal.dot(TOON_LIGHT) > TOON_SHADOW_AT else 1
    return mesh_obj(name, bm, [mat, mat + '_s'], part, smooth=False)


def two_bone(root, target, l1, l2, pole):
    """The middle joint of a two-bone limb from root to target, bent toward pole."""
    root, target = Vector(root), Vector(target)
    d = target - root
    dist = min(d.length, l1 + l2 - 1e-4)
    dn = d.normalized()
    a = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist)
    h = math.sqrt(max(l1 * l1 - a * a, 0.0))
    perp = (Vector(pole) - dn * Vector(pole).dot(dn)).normalized()
    return root + dn * a + perp * h


def surface_top(u):
    zm, A, B, hw = profile(u)
    return zm + A


def build_rider():
    """Denji's stand-in standing knees bent on the middle of his back, leaning forward, holding the chain reins."""
    f0, f1 = RIDER_FEET
    feet = [Vector((0.0, Y(f0), surface_top(f0) + 0.02)), Vector((0.0, Y(f1), surface_top(f1) + 0.02))]
    xs = (0.15, -0.15)
    hipc = Vector((0.0, (feet[0].y + feet[1].y) / 2 + 0.02, (feet[0].z + feet[1].z) / 2 + 0.62))
    for k, side in enumerate((1, -1)):
        foot = Vector((side * 0.15, feet[k].y, feet[k].z))
        hip = Vector((side * 0.15, hipc.y, hipc.z))
        knee = two_bone(hip, foot + Vector((0, 0, 0.1)), 0.375, 0.375, (0, -1, 0.1))
        box_between('thigh', hip, knee, 0.25, 0.25, 'r_pants', up_hint=(1, 0, 0))
        box_between('shin', knee, foot + Vector((0, 0, 0.1)), 0.25, 0.25, 'r_pants', up_hint=(1, 0, 0))
        box_between('shoe', foot + Vector((0, 0.12, 0.05)), foot + Vector((0, -0.2, 0.05)), 0.26, 0.16, 'r_shoe',
                    up_hint=(1, 0, 0))
    lean = math.radians(24)
    up = Vector((0, -math.sin(lean), math.cos(lean)))
    neck = hipc + up * 0.75
    box_between('torso', hipc, neck, 0.5, 0.26, 'r_shirt', up_hint=(1, 0, 0))
    head_c = neck + up * 0.3
    box_between('head', neck + up * 0.02, neck + up * 0.52, 0.5, 0.5, 'r_skin', up_hint=(1, 0, 0))
    box_between('hair', neck + up * 0.46, neck + up * 0.58, 0.52, 0.52, 'r_hair', up_hint=(1, 0, 0))
    hands_y, hands_z, apart = HANDS
    for side in (1, -1):
        shoulder = neck - up * 0.12 + Vector((side * 0.375, 0, 0))
        hand = Vector((side * apart, hands_y, hands_z))
        elbow = two_bone(shoulder, hand, 0.375, 0.375, (side * 1.0, 0.2, -0.6))
        box_between('upper_arm', shoulder, elbow, 0.25, 0.25, 'r_shirt', up_hint=(0, 1, 0))
        box_between('forearm', elbow, hand, 0.23, 0.23, 'r_skin', up_hint=(0, 1, 0))


def build_reins():
    """The chain from each hand forward over the head to the corner of his mouth, outside his skin."""
    hands_y, hands_z, apart = HANDS
    for side in (1, -1):
        hand = Vector((side * apart, hands_y, hands_z))
        lip = Vector((side * (profile(REIN_LIP_U)[3] + 0.03), Y(REIN_LIP_U), profile(REIN_LIP_U)[0] + 0.03))
        # over the head: a point out beside the head's flank between
        u_mid = 1.15
        zm, A, B, hw = profile(u_mid)
        over = Vector((side * (hw * 0.78 + 0.1), Y(u_mid), zm + A * 0.82))
        pts = []
        for seg in (bezier(hand, (hand + over) / 2, over, 10), bezier(over, (over + lip) / 2 + Vector((side * 0.1, 0, 0)), lip, 10)):
            pts += seg if not pts else seg[1:]
        n = len(pts)
        bm = sweep(pts, [Vector((1, 0, 0))] * n, [0.035] * n, [0.035] * n, nj=6)
        for f in bm.faces:
            f.material_index = 0
        mesh_obj('rein', bm, ['r_chain', 'r_chain_s'], 'rider', smooth=False)


def build_ground():
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=60)
    mesh_obj('ground', bm, ['ground'], 'ground', smooth=False)
    # A soft dark blob where he stands.
    bm = bmesh.new()
    n = 36
    ring = [bm.verts.new((2.2 * math.cos(2 * math.pi * k / n), Y(2.5) * 0 + 0.1 + 2.7 * math.sin(2 * math.pi * k / n), 0.004))
            for k in range(n)]
    bm.faces.new(ring)
    mesh_obj('blob', bm, ['blob'], 'ground', smooth=False)


def build(with_rider=True):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    MATS.clear()
    make_materials()
    build_beam()
    if with_rider:
        build_rider()
        build_reins()
    build_ground()
    # Pivots, for the game's copy (blocks, build space).
    for o in bpy.context.scene.objects:
        o['pivot'] = PIVOTS[o['part']][1] if o.get('part') in PIVOTS else (0, 0, 0)
    # Everything to pixels, sixteen to a block.
    for o in bpy.context.scene.objects:
        o.data.transform(Matrix.Scale(BLOCK, 4))


# --- the game's copy: unwrapped, painted, written ------------------------------------------------------------------------

GAME_ASSETS = os.path.join('chainsawman', 'src', 'client', 'resources', 'assets', 'chainsawman')
SWATCHED = {'tooth', 'sclera', 'pupil', 'ring'}
PAINTED_SHARE = 0.8
SWATCH_TEXELS = 4


def to_game(v, pivot):
    """The build's pixels (x to his left, y backward, z up) to the model space as the entities have it (pixels, x to the
    left, y down, the front toward -z), measured from the pivot."""
    d = v - pivot
    return (d.x, -d.z, d.y)


def game_normal(n):
    return (n.x, -n.z, n.y)


def game_objects():
    return [o for o in bpy.context.scene.objects if o.type == 'MESH' and o.get('part') in PIVOTS and not o.hide_render]


def world_mesh(o):
    me = bpy.data.meshes.new_from_object(o.evaluated_get(bpy.context.evaluated_depsgraph_get()))
    me.transform(o.matrix_world)
    bm = bmesh.new()
    bm.from_mesh(me)
    big = [f for f in bm.faces if len(f.verts) > 4]
    if big:
        bmesh.ops.triangulate(bm, faces=big)
    bm.to_mesh(me)
    bm.free()
    return me


def unwrap(objs):
    """Each object unwrapped on its own, then all their islands scaled alike and packed into one square."""
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
    bpy.ops.uv.pack_islands(rotate=True, margin_method='FRACTION', margin=0.002, shape_method='CONCAVE')
    bpy.ops.object.mode_set(mode='OBJECT')


def dilate(rgb, valid, steps=16):
    """Spreads each island's colour out past its edges, so filtering does not pull in the background."""
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


def material_of(c):
    return c.data.materials[0].name.split('.')[0] if c.data.materials else ''


def gill_marks(u, z, ax):
    """True where a gill line passes (u back from the snout, z up; ax the distance from his middle, ignored)."""
    hit = np.zeros(len(u), bool)
    ts = np.linspace(0.0, 1.0, 18)
    for k, u0 in enumerate(GILLS):
        zz = GILL_Z[0] + (GILL_Z[1] - GILL_Z[0]) * ts
        uu = u0 + GILL_SLANT * ts - GILL_BOW * np.sin(math.pi * ts) * (1 - 0.15 * k)
        dist = np.sqrt((u[:, None] - uu[None, :]) ** 2 + (z[:, None] - zz[None, :]) ** 2).min(axis=1)
        hit |= dist < GILL_W
    return hit


def jag(u):
    period = JAG[1]
    saw = ((u * period) % 1.0) - 0.5
    return JAG[0] * saw + 0.025 * np.sin(u * 9.3 + 1.0)


def toon_colour(mat, pos, normal):
    """The colours of texels of {@code mat} at build-space points {@code pos} (N x 3 pixels) facing {@code normal}."""
    lit = normal @ np.array(TOON_LIGHT) > TOON_SHADOW_AT
    if mat == 'skin':
        b = pos / BLOCK
        u = b[:, 1] - Y0
        low = b[:, 2] < _BOUND(u) + jag(u)
        light = np.where(low[:, None], np.array(TOON['belly'][0], np.float32), np.array(TOON['navy'][0], np.float32))
        shadow = np.where(low[:, None], np.array(TOON['belly'][1], np.float32), np.array(TOON['navy'][1], np.float32))
        rgb = np.where(lit[:, None], light, shadow)
        marks = gill_marks(u, b[:, 2], np.abs(b[:, 0])) & (np.abs(b[:, 0]) > 0.35) & (u > 2.0)
        return np.where(marks[:, None], np.array(INK, np.float32), rgb)
    light, shadow, ink = TOON.get(mat, ((200, 0, 200), (150, 0, 150), (40, 0, 40)))
    return np.where(lit[:, None], np.array(light, np.float32), np.array(shadow, np.float32))


def draw_line(img, a, b, colour):
    """One texel wide line on an image array (rows from the top), from a to b in texel coordinates."""
    n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) * 2 + 2
    xs = np.linspace(a[0], b[0], n).astype(int)
    ys = np.linspace(a[1], b[1], n).astype(int)
    ok = (xs >= 0) & (xs < img.shape[1]) & (ys >= 0) & (ys < img.shape[0])
    img[ys[ok], xs[ok]] = colour


def paint_toon(copies, size):
    """Paints the copies' faces into a size x size texture through their 'bake' UVs: each texel the colour of its material
    where it lands on the model, the shade tone cut against the smooth surface's own normal, then ink along the hard edges.
    Returns an array (rows from the top, 0-255)."""
    rgb = np.zeros((size, size, 3), np.float32)
    valid = np.zeros((size, size), bool)
    inks = []
    for c in copies:
        me = c.data
        cn = np.array([list(n.vector) for n in me.corner_normals], np.float64).reshape(-1, 3)
        bm = bmesh.new()
        bm.from_mesh(me)
        bm.faces.ensure_lookup_table()
        uv_layer = bm.loops.layers.uv['bake']
        mats = [m.name.split('.')[0] for m in me.materials] or ['']
        for e in bm.edges:
            faces = e.link_faces
            sharp = len(faces) < 2 or math.degrees(faces[0].normal.angle(faces[1].normal, 0.0)) > INK_ANGLE
            if not sharp:
                continue
            for f in faces:
                for loop in f.loops:
                    if loop.edge == e:
                        a = loop[uv_layer].uv
                        b = loop.link_loop_next[uv_layer].uv
                        inks.append(((a.x * size, (1 - a.y) * size), (b.x * size, (1 - b.y) * size),
                                     TOON.get(mats[min(f.material_index, len(mats) - 1)], ((0, 0, 0),) * 3)[2]))
        for f in bm.faces:
            mat = mats[min(f.material_index, len(mats) - 1)]
            loops = list(f.loops)
            start = me.polygons[f.index].loop_start
            for k in range(1, len(loops) - 1):
                tri = (loops[0], loops[k], loops[k + 1])
                idx = (start, start + k, start + k + 1)
                uv = np.array([[l[uv_layer].uv.x * size, (1 - l[uv_layer].uv.y) * size] for l in tri], np.float64)
                co = np.array([list(l.vert.co) for l in tri], np.float64)
                nrm = np.array([cn[i] for i in idx], np.float64)
                x0, y0 = np.floor(uv.min(axis=0)).astype(int)
                x1, y1 = np.ceil(uv.max(axis=0)).astype(int)
                x0, y0 = max(x0, 0), max(y0, 0)
                x1, y1 = min(x1, size - 1), min(y1, size - 1)
                if x1 < x0 or y1 < y0:
                    continue
                gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                p = np.stack([gx.ravel(), gy.ravel()], axis=1)
                (ax, ay), (bx, by), (cx, cy) = uv
                det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if abs(det) < 1e-12:
                    continue
                w0 = ((by - cy) * (p[:, 0] - cx) + (cx - bx) * (p[:, 1] - cy)) / det
                w1 = ((cy - ay) * (p[:, 0] - cx) + (ax - cx) * (p[:, 1] - cy)) / det
                w2 = 1 - w0 - w1
                inside = (w0 >= -0.02) & (w1 >= -0.02) & (w2 >= -0.02)
                if not inside.any():
                    continue
                w = np.stack([w0, w1, w2], axis=1)[inside]
                pos = w @ co
                normal = w @ nrm
                normal /= np.maximum(np.linalg.norm(normal, axis=1, keepdims=True), 1e-9)
                col = toon_colour(mat, pos, normal)
                px = p[inside].astype(int)
                rgb[px[:, 1], px[:, 0]] = col
                valid[px[:, 1], px[:, 0]] = True
        bm.free()
    img = np.clip(dilate(rgb / 255.0, valid, steps=4) * 255 + 0.5, 0, 255).astype(np.uint8)
    for a, b, colour in inks:
        draw_line(img, a, b, colour)
    return img


def toon_size(copies):
    """The texture's size (a power of two) that gives about TEXELS_PER_PIXEL texels to a pixel of the model."""
    model, uv = 0.0, 0.0
    for c in copies:
        me = c.data
        layer = me.uv_layers['bake'].data
        for poly in me.polygons:
            model += poly.area
            pts = [layer[li].uv for li in poly.loop_indices]
            uv += abs(sum(pts[i].x * pts[(i + 1) % len(pts)].y - pts[(i + 1) % len(pts)].x * pts[i].y
                          for i in range(len(pts)))) / 2
    want = TEXELS_PER_PIXEL * math.sqrt(model / max(uv, 1e-9))
    return int(2 ** round(math.log2(want)))


def assign_swatches(copies, size):
    """Points every face of the swatched copies at its tone's swatch; returns what to paint where ((x, y, colour) for
    each swatch)."""
    mats = sorted({material_of(c) for c in copies})
    cells = {}
    paint = []
    x0 = int(size * PAINTED_SHARE) + 2
    per_row = max(1, (size - x0) // SWATCH_TEXELS)
    i = 0
    for mat in mats:
        light, shadow, ink = TOON[mat]
        for k, colour in enumerate((light, shadow)):
            cx = x0 + (i % per_row) * SWATCH_TEXELS
            cy = (i // per_row) * SWATCH_TEXELS
            cells[(mat, k)] = ((cx + SWATCH_TEXELS / 2) / size, 1.0 - (cy + SWATCH_TEXELS / 2) / size)
            paint.append((cx, cy, colour))
            i += 1
    for c in copies:
        me = c.data
        mat = material_of(c)
        layer = me.uv_layers['bake'].data
        for poly in me.polygons:
            tone = 0 if np.array(list(poly.normal)) @ np.array(TOON_LIGHT) > TOON_SHADOW_AT else 1
            u, v = cells[(mat, tone)]
            for li in poly.loop_indices:
                layer[li].uv = (u, v)
    return paint, cells


def shrink_uvs(copies, share):
    for c in copies:
        layer = c.data.uv_layers['bake'].data
        co = np.empty(len(layer) * 2, np.float32)
        layer.foreach_get('uv', co)
        co = co * share
        co[1::2] += 1.0 - share
        layer.foreach_set('uv', co)


def save_png(path, rgb):
    """A plain 8-bit RGB PNG (no Pillow here)."""
    h, w, _ = rgb.shape
    raw = b''.join(b'\x00' + rgb[y].tobytes() for y in range(h))

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def make_copies():
    """The game's copies of every game object (evaluated, in pixels), each with its part's name; the originals hidden."""
    scene = bpy.context.scene
    copies = []
    for o in game_objects():
        me = world_mesh(o)
        me.uv_layers.new(name='bake')
        me.uv_layers.active = me.uv_layers['bake']
        me.uv_layers['bake'].active_render = True
        c = bpy.data.objects.new('bake:' + o.name, me)
        scene.collection.objects.link(c)
        c['game_part'] = o['part']
        copies.append(c)
        o.hide_render = True
        o.hide_set(True)
    return copies


def bake_look():
    """Unwraps and paints the copies; returns (copies, texture array, swatched copies, painted copies)."""
    copies = make_copies()
    painted = [c for c in copies if material_of(c) not in SWATCHED]
    swatched = [c for c in copies if material_of(c) in SWATCHED]
    unwrap(painted)
    shrink_uvs(painted, PAINTED_SHARE)
    size = toon_size(painted)
    swatches, cells = assign_swatches(swatched, size)
    img = paint_toon(painted, size)
    for x, y, colour in swatches:
        img[y:y + SWATCH_TEXELS, x:x + SWATCH_TEXELS] = colour
    print('texture %d x %d' % (size, size), flush=True)
    return copies, img


def write_game(copies, img, mesh_path, texture_path):
    """.csm (little endian), the same CSM2 format chainsaw_model.py writes and FormMesh.java reads: b"CSM2", int32 part
    count; per part its name and its bone (each int16 length then UTF-8; for Beam the bone is the part it hangs from, ''
    for the body); a byte, 1 if the part has a pivot, then the pivot and the axis it swings about (six float32: pixels from
    the body's pivot in the entity's model space, and a unit vector); int32 quad count, and four corners per quad: float32
    x, y, z (pixels from the body's pivot, the same model space, so every part is in place at rest), u, v, then int8 nx,
    ny, nz and uint8 flags (bit 0 glows; none do here). Triangles repeat their last corner."""
    body_pivot = Vector(PIVOTS['body'][1]) * BLOCK
    parts = {}
    for c in copies:
        me = c.data
        uv = me.uv_layers['bake'].data
        normals = me.corner_normals
        part = c['game_part']
        for poly in me.polygons:
            corners = []
            for li in poly.loop_indices:
                co = me.vertices[me.loops[li].vertex_index].co
                corners.append((co.copy(), tuple(uv[li].uv), Vector(normals[li].vector)))
            if len(corners) == 3:
                corners.append(corners[2])
            parts.setdefault(part, []).append(corners)
    os.makedirs(os.path.dirname(os.path.abspath(texture_path)), exist_ok=True)
    save_png(texture_path, img)
    with open(mesh_path, 'wb') as f:
        f.write(b'CSM2' + struct.pack('<i', len(parts)))
        for name, quads in parts.items():
            bone, pivot, axis = PIVOTS[name]
            f.write(struct.pack('<h', len(name)) + name.encode() + struct.pack('<h', len(bone)) + bone.encode())
            if name != 'body':
                f.write(struct.pack('<B3f3f', 1, *to_game(Vector(pivot) * BLOCK, body_pivot), *game_normal(Vector(axis))))
            else:
                f.write(struct.pack('<B', 0))
            f.write(struct.pack('<i', len(quads)))
            buf = bytearray()
            for q in quads:
                for co, (u, v), n in q:
                    x, y, z = to_game(co, body_pivot)
                    nx, ny, nz = game_normal(n.normalized())
                    buf += struct.pack('<5f3bB', x, y, z, u, 1.0 - v, int(round(nx * 127)), int(round(ny * 127)),
                                       int(round(nz * 127)), 0)
            f.write(buf)
            print('  %-10s on %-9s %6d quads' % (name, bone or 'nothing', len(quads)))
    print('mesh %s (%d KB), texture %s (%d KB)' % (mesh_path, os.path.getsize(mesh_path) // 1024, texture_path,
                                                    os.path.getsize(texture_path) // 1024))


# --- preview renders (the game's look: the toon texture on the mesh, flat lit) ------------------------------------------

def look_at(obj, target):
    obj.rotation_mode = 'XYZ'
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat('-Z', 'Y').to_euler()


def textured(copies, tex_path):
    img = bpy.data.images.load(tex_path)
    img.colorspace_settings.name = 'sRGB'
    mat = bpy.data.materials.new('beam_texture')
    mat.use_nodes = True
    nt = mat.node_tree
    node = nt.nodes.new('ShaderNodeTexImage')
    node.image = img
    node.interpolation = 'Closest'
    nt.links.new(node.outputs['Color'], nt.nodes['Principled BSDF'].inputs['Base Color'])
    nt.nodes.active = node
    for c in copies:
        c.data.materials.clear()
        c.data.materials.append(mat)
        for poly in c.data.polygons:
            poly.material_index = 0
    return mat


def studio():
    scene = bpy.context.scene
    world = bpy.data.worlds.new('sky')
    world.color = (0.55, 0.72, 0.86)
    scene.world = world
    scene.render.engine = 'BLENDER_WORKBENCH'
    sh = scene.display.shading
    sh.light = 'FLAT'
    sh.color_type = 'TEXTURE'
    sh.show_object_outline = False
    sh.show_cavity = False
    sh.show_shadows = False
    sh.show_specular_highlight = False
    scene.display.render_aa = '16'
    scene.view_settings.view_transform = 'Standard'
    scene.render.image_settings.file_format = 'PNG'
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.clip_start, cam.data.clip_end = 1.0, 4000.0


# name, camera (blocks), target (blocks), lens mm, size
SHOTS = [
    ('side', (-12.5, -7.5, 3.0), (0.0, -0.3, 2.2), 56, (1600, 1100)),
    ('profile', (-16.0, -3.6, 2.3), (0.0, -0.1, 2.5), 60, (1600, 1100)),
    ('front34', (-6.5, -9.0, 4.0), (0.0, -0.8, 2.2), 52, (1500, 1100)),
    ('front', (0.0, -13.0, 2.6), (0.0, 0.0, 2.2), 60, (1100, 1100)),
    ('behind', (3.6, 6.2, 9.0), (0.0, -0.4, 2.4), 40, (1500, 1100)),
    ('mouth', (-4.4, -6.0, 2.8), (0.0, -1.7, 1.9), 55, (1500, 1100)),
    ('full', (-11.0, -10.5, 5.5), (0.0, 0.0, 2.2), 52, (1600, 1100)),
    ('top', (0.0, 0.0, 16.0), (0.0, 0.0, 0.0), 52, (1100, 1100)),
    ('legs', (-8.0, -7.0, 1.4), (0.0, 0.2, 1.3), 52, (1500, 1100)),
    ('back', (0.0, 13.0, 2.8), (0.0, 0.0, 2.2), 60, (1100, 1100)),
]


def renders(out, only, rider=True):
    build(with_rider=rider)
    copies, img = bake_look()
    tex = os.path.join(out, 'beam_texture.png')
    save_png(tex, img)
    textured(copies, tex)
    studio()
    scene = bpy.context.scene
    for name, loc, target, lens, size in SHOTS:
        if only and name not in only:
            continue
        scene.render.resolution_x, scene.render.resolution_y = size
        cam = scene.camera
        cam.location = Vector(loc) * BLOCK
        look_at(cam, Vector(target) * BLOCK)
        cam.data.lens = lens
        scene.render.filepath = os.path.join(out, name + '.png')
        bpy.ops.render.render(write_still=True)
        print('rendered', name, flush=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(os.path.join(out, 'beam.blend')))


def bake(out, game):
    build(with_rider=False)
    copies, img = bake_look()
    if game:
        write_game(copies, img, os.path.join(GAME_ASSETS, 'meshes', 'beam.csm'),
                   os.path.join(GAME_ASSETS, 'textures', 'entity', 'beam.png'))
    else:
        write_game(copies, img, os.path.join(out, 'beam.csm'), os.path.join(out, 'beam.png'))


def main():
    args = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    out = args[args.index('--out') + 1] if '--out' in args else 'build/beam'
    only = args[args.index('--only') + 1].split(',') if '--only' in args else None
    os.makedirs(out, exist_ok=True)
    if '--renders' in args:
        renders(out, only, rider='--no-rider' not in args)
    if '--bake' in args:
        bake(out, False)
    if '--game' in args:
        bake(out, True)


if __name__ == '__main__':
    main()
