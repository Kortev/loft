"""The Eternity Devil as a real 3D model: sculpted as a signed distance field, voxelized at 1/8 block, painted voxel by voxel.

kortev approved the v4 design (composition B: flesh pouring from the torn roof down into one great maw of human teeth
with the heart inside, eyes everywhere, dead skin, the palette and the light) but not its form: it was a painting cut
onto box fronts, stepped and flat from the side. This sculpts the same composition in 3D so it holds from every angle.

    python tools/eternity_devil_sculpt.py preview      # 6 views of the model -> work/eternity_look_v5/sculpt_*.png
    python tools/eternity_devil_sculpt.py build        # writes the game's mesh and parts (see OUT_ASSETS)

Frame (blocks): X out from the back wall toward the players (hotel x = 37 - X), Y up from floor 8's walking surface
(hotel y = 29 + Y), Z across (hotel z = 6.5 + Z, + is south, the player's right as they face the devil). The maw sits on
the corridor's axis, Z = 0.

What it writes for the game (OUT_ASSETS):
  eternity_devil_mesh.bin   the flesh and the heart as quads: only exposed voxel faces, merged where the colour is flat.
                            little-endian: int32 count, then per quad: uint8 dir (0 +X, 1 -X, 2 +Y, 3 -Y, 4 +Z, 5 -Z),
                            uint8 part (0 flesh, 1 heart), int16 x, y, z (corner, 1/16 block), int16 du, dv (size in its
                            plane, 1/16 block; dir X: du along Z, dv along Y; dir Y: du along X, dv along Z; dir Z: du
                            along X, dv along Y), uint8 r, g, b, uint8 pad.
  eternity_devil_parts.json the eyes, the small mouths, the teeth, the heart's pivot, the jaw and breathing fields, hit boxes.
"""
import json
import math
import os
import random
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import eternity_devil_concept as C  # noqa: E402  (the palette, the concept's eyes and mouths)

ROOT = os.path.abspath(os.path.join(HERE, '..'))
OUT = os.path.abspath(os.path.join(ROOT, '..', '..', '..', 'work', 'eternity_look_v5')) \
    if '.claude' in ROOT else os.path.join(ROOT, 'work', 'eternity_look_v5')
OUT_ASSETS = os.path.join(ROOT, 'chainsawman', 'src', 'main', 'resources', 'assets', 'chainsawman', 'models', 'entity')

S = 1.0 / 8.0                         # voxel size, blocks
X0, X1 = -1.0, 6.0      # the back runs into the east wall's thickness (hotel x 37-38): hidden in game, round on a turntable
Y0, Y1 = 0.0, 8.5
Z0, Z1 = -6.5, 6.5      # and into the outer walls' thickness at the sides
NX, NY, NZ = int((X1 - X0) / S), int((Y1 - Y0) / S), int((Z1 - Z0) / S)
MAW_U = 114                            # the concept's maw column

LIGHT = np.array([0.38, 0.82, -0.30])  # the moon: high, a little toward the players and from their left
LIGHT /= np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([1.0, 0.3, 0.0])
HALF /= np.linalg.norm(HALF)


# ---- distance functions (numpy, on arrays of points)

def ellipsoid(px, py, pz, c, r, sag=0.0):
    """A sagging ellipsoid: below its centre it widens (gravity), so a lobe hangs like a full sack."""
    dx, dy, dz = px - c[0], py - c[1], pz - c[2]
    k = 1.0 + sag * np.clip(-dy / r[1], 0, 1.2)
    rx, ry, rz = r[0] * k, r[1], r[2] * k
    k0 = np.sqrt((dx / rx) ** 2 + (dy / ry) ** 2 + (dz / rz) ** 2)
    k1 = np.sqrt((dx / rx ** 2) ** 2 + (dy / ry ** 2) ** 2 + (dz / rz ** 2) ** 2)
    return k0 * (k0 - 1.0) / np.maximum(k1, 1e-6)


def capsule(px, py, pz, a, b, r0, r1):
    a, b = np.array(a, float), np.array(b, float)
    ab = b - a
    t = np.clip(((px - a[0]) * ab[0] + (py - a[1]) * ab[1] + (pz - a[2]) * ab[2]) / ab.dot(ab), 0, 1)
    qx, qy, qz = a[0] + t * ab[0], a[1] + t * ab[1], a[2] + t * ab[2]
    return np.sqrt((px - qx) ** 2 + (py - qy) ** 2 + (pz - qz) ** 2) - (r0 + (r1 - r0) * t)


def chain(px, py, pz, pts, r0, r1):
    n = len(pts) - 1
    d = None
    for i in range(n):
        ra = r0 + (r1 - r0) * i / n
        rb = r0 + (r1 - r0) * (i + 1) / n
        di = capsule(px, py, pz, pts[i], pts[i + 1], ra, rb)
        d = di if d is None else np.minimum(d, di)
    return d


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


def smax(a, b, k):
    return -smin(-a, -b, k)


# ---- the sculpture: composition B in 3D

LOBES = [
    # name, centre (X, Y, Z), radii, sag, blend
    ('back_l', (0.55, 3.4, -3.4), (1.4, 3.6, 2.2), 0.15, 0.5),
    ('back_c', (0.5, 2.8, 0.2), (1.35, 3.0, 2.4), 0.15, 0.5),
    ('back_r', (0.55, 2.6, 3.5), (1.35, 2.8, 2.1), 0.15, 0.5),
    ('roof_l', (0.95, 6.6, -2.7), (1.55, 1.45, 2.4), 0.25, 0.6),
    ('roof_r', (0.7, 5.2, 1.5), (1.2, 0.9, 1.8), 0.25, 0.5),
    ('hang', (1.55, 5.05, -3.3), (1.45, 1.55, 1.9), 0.35, 0.45),
    ('belly', (1.85, 3.55, -3.75), (1.25, 1.0, 1.45), 0.4, 0.5),
    ('mid', (1.65, 3.95, -0.25), (1.3, 0.95, 2.0), 0.3, 0.45),
    ('mid_drip', (1.95, 3.1, 0.95), (0.6, 0.55, 0.7), 0.4, 0.35),
    ('maw', (2.0, 1.75, 0.1), (2.0, 1.75, 2.75), 0.25, 0.45),
    ('maw_r', (1.9, 0.9, 2.3), (1.4, 0.9, 1.1), 0.3, 0.4),
    ('wallR', (1.0, 3.6, 4.6), (1.1, 2.8, 1.05), 0.2, 0.45),
    ('wallR_low', (1.2, 1.5, 4.9), (0.85, 1.05, 0.75), 0.3, 0.4),
    ('spill', (2.2, 0.25, -3.3), (2.2, 0.45, 2.0), 0.0, 0.35),
]
DRIPS = [  # small hanging drops
    ((2.05, 2.55, -4.6), (0.5, 0.6, 0.5)), ((2.25, 2.75, -2.4), (0.42, 0.55, 0.42)),
    ((2.0, 2.4, 1.2), (0.32, 0.45, 0.32)), ((1.4, 4.9, 1.9), (0.5, 0.6, 0.5)),
]
LUMPS = [  # lumps on the lobes (centre, radius): breaks the smooth sacks into flesh
    ((2.6, 5.6, -3.0), 0.45), ((2.4, 4.7, -2.3), 0.38), ((2.7, 4.4, -4.1), 0.4), ((2.8, 3.7, -3.2), 0.35),
    ((2.7, 4.3, -0.9), 0.42), ((2.8, 4.0, 0.6), 0.34), ((2.3, 4.7, 0.2), 0.3),
    ((3.4, 2.9, -1.6), 0.4), ((3.5, 3.0, 1.7), 0.38), ((3.7, 2.5, 2.4), 0.3), ((3.2, 3.2, 0.0), 0.32),
    ((1.8, 5.2, 4.3), 0.36), ((1.9, 3.4, 4.0), 0.4), ((1.5, 6.0, 4.2), 0.4), ((1.9, 7.0, -1.9), 0.45),
    ((1.4, 7.6, -3.4), 0.4), ((1.1, 5.85, 1.0), 0.35), ((1.6, 7.3, -0.6), 0.38), ((3.3, 0.6, -2.4), 0.3), ((3.0, 0.5, -4.2), 0.32),
    ((4.0, 0.35, -4.4), 0.3), ((4.3, 0.33, -2.3), 0.28), ((3.4, 0.35, -5.2), 0.24), ((4.2, 0.3, 2.0), 0.24),
]
SINEWS = [  # stretched across the dark under the hanging lobe: (points, r at ends, r in the middle)
    ([(2.4, 2.2, -4.1), (2.7, 1.3, -3.7), (3.0, 0.45, -3.3)], 0.17, 0.08),
    ([(2.1, 2.3, -4.9), (2.3, 1.2, -4.95), (2.5, 0.4, -4.9)], 0.13, 0.06),
    ([(2.5, 2.5, -2.9), (2.9, 1.6, -2.6), (3.2, 0.9, -2.0)], 0.11, 0.05),
]
FINGERS = [  # the spill reaching along the floor toward the players
    ([(2.6, 0.25, -4.0), (3.7, 0.3, -4.4), (4.7, 0.22, -4.6)], 0.45, 0.28),
    ([(3.0, 0.25, -2.6), (4.0, 0.3, -2.4), (4.9, 0.2, -2.1)], 0.42, 0.25),
    ([(2.4, 0.25, -5.0), (3.2, 0.22, -5.25), (3.8, 0.18, -5.35)], 0.36, 0.2),
    ([(3.6, 0.25, 1.4), (4.3, 0.24, 2.0), (4.8, 0.18, 2.5)], 0.36, 0.2),
]
FOLDS = [  # creases cut into the skin (capsules subtracted)
    ([(2.7, 4.35, -4.9), (2.95, 4.25, -3.6), (2.85, 4.4, -2.4)], 0.07),
    ([(2.75, 3.45, -1.4), (2.95, 3.35, -0.3), (2.75, 3.45, 0.9)], 0.07),
    ([(3.5, 2.95, -2.2), (3.85, 2.9, -1.0), (3.85, 2.95, 1.0), (3.5, 3.0, 2.3)], 0.06),
    ([(2.0, 5.6, 3.9), (2.05, 5.1, 4.6)], 0.06), ([(1.9, 2.6, 4.2), (2.05, 2.2, 4.8)], 0.05),
]
MAW = dict(front_x=3.95, y=1.55, z=0.0, half_w=1.8, half_h=1.32)    # the opening as seen from the corridor
DEAD = [((2.1, 5.9, -2.5), 0.55), ((2.6, 5.5, -2.0), 0.35), ((1.6, 5.5, 4.7), 0.75), ((1.7, 4.6, 4.9), 0.5),
        ((3.0, 0.8, 2.1), 0.35), ((2.4, 4.6, 1.6), 0.3), ((1.0, 6.0, 0.4), 0.5)]
VEINS = [[(2.9, 4.4, -0.2), (2.6, 3.9, 0.5), (2.2, 3.4, 1.4)], [(2.4, 6.2, -2.9), (2.9, 5.5, -3.6), (3.0, 4.9, -3.4)],
         [(3.7, 3.0, -1.0), (3.9, 2.7, -0.1)], [(1.5, 6.0, 4.2), (1.8, 5.0, 4.4), (2.0, 4.2, 4.0)],
         [(2.7, 1.5, 2.6), (3.2, 1.0, 2.9)], [(1.8, 7.2, -2.3), (2.0, 6.6, -1.6), (2.1, 6.0, -0.8)]]

# Eyes beyond the concept's front: on the sides, the top, under the overhangs (ray origin, direction, size in texels).
EXTRA_EYES = [
    ((1.5, 9.0, -3.2), (0, -1, 0), 11), ((1.4, 9.0, -1.4), (0, -1, 0), 7), ((2.5, 9.0, 0.7), (0, -1, 0), 5),
    ((2.3, 9.0, -1.0), (0, -1, 0), 6), ((1.0, 9.0, 1.6), (0, -1, 0), 9),
    ((1.2, 9.0, 3.6), (0, -1, 0), 6), ((2.2, 9.0, -4.6), (0, -1, 0), 6),
    ((2.0, 0.3, -3.6), (0, 1, 0), 8), ((2.6, 0.3, -2.7), (0, 1, 0), 5), ((2.4, 0.3, 0.9), (0, 1, 0), 5),
    ((2.1, 4.0, 6.5), (0, 0, -1), 9), ((2.0, 2.0, 6.5), (0, 0, -1), 6), ((1.5, 5.6, 6.5), (0, 0, -1), 7),
    ((3.0, 1.2, 6.0), (-0.3, 0, -1), 8), ((3.2, 3.8, 6.0), (-0.4, 0, -1), 6),
    ((3.3, 4.6, -6.5), (-0.3, 0, 1), 9), ((3.6, 2.2, -6.5), (-0.4, 0, 1), 7), ((2.5, 6.4, -6.5), (0, 0, 1), 6),
    ((3.0, 2.4, 6.0), (-0.6, 0.2, -1), 5), ((3.2, 3.6, -6.0), (-0.5, 0.2, 1), 5),
    ((0.6, 5.6, 9.0), (0, 0, -1), 10), ((0.4, 1.8, 9.0), (0, 0, -1), 8), ((0.6, 5.8, -9.0), (0, 0, 1), 10),
    ((0.3, 2.2, -9.0), (0, 0, 1), 8), ((-3.0, 5.6, 0.5), (1, 0, 0), 11), ((-3.0, 2.0, -3.6), (1, 0, 0), 9),
    ((1.0, 3.0, 9.0), (0, 0, -1), 7), ((2.6, 6.4, 9.0), (0, 0, -1), 7), ((1.4, 7.4, 9.0), (0, 0, -1), 8), ((2.2, 0.9, 9.0), (0, 0, -1), 6),
    ((1.2, 3.0, -9.0), (0, 0, 1), 7), ((2.4, 6.6, -9.0), (0, 0, 1), 7), ((1.0, 7.6, -9.0), (0, 0, 1), 8), ((2.0, 1.0, -9.0), (0, 0, 1), 6),
    ((1.7, 2.2, 9.0), (0, 0, -1), 7),
    ((2.1, 3.8, 9.0), (0, 0, -1), 8),
    ((2.5, 5.2, 9.0), (0, 0, -1), 7),
    ((2.9, 2.8, 9.0), (0, 0, -1), 6),
    ((1.9, 6.3, 9.0), (0, 0, -1), 6),
    ((2.7, 4.4, 9.0), (0, 0, -1), 6),
    ((1.7, 2.2, -9.0), (0, 0, 1), 7),
    ((2.1, 3.8, -9.0), (0, 0, 1), 8),
    ((2.5, 5.2, -9.0), (0, 0, 1), 7),
    ((2.9, 2.8, -9.0), (0, 0, 1), 6),
    ((1.9, 6.3, -9.0), (0, 0, 1), 6),
    ((2.7, 4.4, -9.0), (0, 0, 1), 6),
    ((-3.0, 3.2, 4.4), (1, 0, 0), 8), ((-3.0, 6.4, -2.8), (1, 0, 0), 7), ((0.0, 9.0, -4.4), (0, -1, 0), 8),
]
EXTRA_MOUTHS = [  # small mouths on the sides and top: ray origin, direction, width and height in texels
    ((1.3, 9.0, -2.2), (0, -1, 0), 14, 8), ((2.0, 3.0, 6.5), (0, 0, -1), 12, 7), ((3.4, 3.4, -6.5), (-0.3, 0, 1), 12, 7),
    ((1.4, 9.0, 2.6), (0, -1, 0), 10, 6), ((0.8, 4.2, 9.0), (0, 0, -1), 12, 7), ((0.8, 3.0, -9.0), (0, 0, 1), 12, 7),
    ((1.6, 5.4, 9.0), (0, 0, -1), 12, 7), ((2.0, 1.8, 9.0), (0, 0, -1), 10, 6), ((1.6, 5.6, -9.0), (0, 0, 1), 12, 7), ((2.2, 1.9, -9.0), (0, 0, 1), 10, 6),
    ((2.3, 2.6, 9.0), (0, 0, -1), 12, 7),
    ((2.6, 4.9, 9.0), (0, 0, -1), 12, 7),
    ((1.8, 3.9, 9.0), (0, 0, -1), 10, 6),
    ((2.3, 2.6, -9.0), (0, 0, 1), 12, 7),
    ((2.6, 4.9, -9.0), (0, 0, 1), 12, 7),
    ((1.8, 3.9, -9.0), (0, 0, 1), 10, 6),
    ((-3.0, 4.5, -2.0), (1, 0, 0), 12, 7), ((-3.0, 3.0, 2.8), (1, 0, 0), 10, 6),
]


def grid():
    xs = X0 + (np.arange(NX) + 0.5) * S
    ys = Y0 + (np.arange(NY) + 0.5) * S
    zs = Z0 + (np.arange(NZ) + 0.5) * S
    return np.meshgrid(xs, ys, zs, indexing='ij')


SEAM = {}


def base_sdf(px, py, pz, keep=False):
    d = None
    ds = []
    for (_, c, r, sag, k) in LOBES:
        e = ellipsoid(px, py, pz, c, r, sag)
        ds.append(e)
        d = e if d is None else smin(d, e, k * 0.7)
    # Where two great lobes meet, the skin folds in: a crease along the seam (and it goes dark in the paint).
    st = np.sort(np.stack(ds), axis=0)
    d1, d2 = st[0], st[1]
    crease = np.exp(-((d2 - d1) / 0.2) ** 2) * np.clip(1 - np.abs(d1) / 0.9, 0, 1)
    d = d + 0.16 * crease
    if keep:
        SEAM['crease'] = crease
    for c, r in DRIPS:
        d = smin(d, ellipsoid(px, py, pz, c, r, 0.3), 0.25)
    for c, r in LUMPS:
        d = smin(d, np.sqrt((px - c[0]) ** 2 + (py - c[1]) ** 2 + (pz - c[2]) ** 2) - r, 0.22)
    for pts, r0, r1 in FINGERS:
        d = smin(d, chain(px, py, pz, pts, r0, r1), 0.3)
    for pts, r in FOLDS:
        d = smax(d, -chain(px, py, pz, pts, r, r), 0.06)
    # the sinews (thin in the middle: two chains meeting)
    for pts, re, rm in SINEWS:
        half1 = chain(px, py, pz, pts[:2], re, rm)
        half2 = chain(px, py, pz, pts[1:], rm, re)
        d = smin(d, np.minimum(half1, half2), 0.12)
    # sits on the floor, pressed against the back wall and the outer walls
    d = np.maximum(d, -py)
    d = smax(d, -(px + 0.9), 0.3)
    d = smax(d, np.abs(pz) - 6.2, 0.6)     # the flanks round off before the outer walls, never a flat cut
    return d


def tongue(px, py, pz):
    m = MAW
    return ellipsoid(px, py, pz, (m['front_x'] - 1.35, m['y'] - 1.12, m['z'] + 0.1), (0.8, 0.32, 0.95), 0.0)


def maw_carve(d, px, py, pz):
    """The great mouth: a lens-shaped opening on the maw lobe's front, a cavity behind it a block and more deep."""
    m = MAW
    opening = ellipsoid(px, py, pz, (m['front_x'] + 0.2, m['y'], m['z']), (0.95, m['half_h'], m['half_w']))
    cavity = ellipsoid(px, py, pz, (m['front_x'] - 1.05, m['y'] - 0.15, m['z']), (1.15, m['half_h'] + 0.1, m['half_w'] - 0.1))
    hole = smin(opening, cavity, 0.25)
    d = smax(d, -hole, 0.08)
    d = smin(d, tongue(px, py, pz), 0.12)      # a tongue lolls on the cavity's floor
    return d, hole


def surface_hit(sdf_fn, o, dirn, tmax=12.0):
    o, dirn = np.array(o, float), np.array(dirn, float)
    dirn /= np.linalg.norm(dirn)
    t = 0.0
    for _ in range(300):
        p = o + dirn * t
        dv = float(sdf_fn(np.array([p[0]]), np.array([p[1]]), np.array([p[2]]))[0])
        if dv < 0.002:
            return p
        t += max(dv * 0.8, 0.01)
        if t > tmax:
            return None
    return None


def normal_at(sdf_fn, p, e=0.04):
    def f(q):
        return float(sdf_fn(np.array([q[0]]), np.array([q[1]]), np.array([q[2]]))[0])
    n = np.array([f(p + [e, 0, 0]) - f(p - [e, 0, 0]), f(p + [0, e, 0]) - f(p - [0, e, 0]),
                  f(p + [0, 0, e]) - f(p - [0, 0, e])])
    return n / max(np.linalg.norm(n), 1e-6)


def place_features():
    """Eyes and small mouths: the concept's own on the front (projected from the corridor onto the surface), and more
    on the sides, the top and under the overhangs."""
    comp = C.comp_pass(len(C.PASSES))
    sd = lambda x, y, z: maw_carve(base_sdf(x, y, z), x, y, z)[0]
    eyes, mouths = [], []
    for (u, v, w, kw) in comp['eyes']:
        o = (8.0, 7.0 - v / 16.0, (u - MAW_U) / 16.0)
        p = surface_hit(sd, o, (-1, 0, 0))
        if p is None:
            continue
        n = normal_at(sd, p)
        eyes.append(dict(pos=p, normal=n, size=w / 16.0, iris=kw.get('iris', 'brown'), gaze=list(kw.get('gaze', (0, 0))),
                         opn=kw.get('opn', 1.0), front=True))
    for e in eyes:   # the big middle eye over the maw made a cartoon face from above: smaller
        if abs(e['pos'][2]) < 0.6 and 4.0 < e['pos'][1] < 4.6:
            e['size'] *= 0.62
    for (o, dirn, w) in EXTRA_EYES:
        p = surface_hit(sd, o, dirn)
        if p is None:
            continue
        n = normal_at(sd, p)
        if any(np.linalg.norm(p - e['pos']) < (w / 16.0 + e['size']) * 0.7 for e in eyes):
            continue
        eyes.append(dict(pos=p, normal=n, size=w / 16.0, iris=random.Random(int(p[1] * 100)).choice(list(C.IRIS)[:5]),
                         gaze=[0, 0], opn=1.0, front=False))
    for m in comp['mouths']:
        if m.get('heart'):
            continue
        ww, hh = (m['h'], m['w']) if m.get('orient') == 'v' else (m['w'], m['h'])
        o = (8.0, 7.0 - m['cy'] / 16.0, (m['cx'] - MAW_U) / 16.0)
        p = surface_hit(sd, o, (-1, 0, 0))
        if p is not None:
            mouths.append(dict(pos=p, normal=normal_at(sd, p), w=ww / 16.0 * 1.5, h=hh / 16.0 * 1.5,
                               vertical=m.get('orient') == 'v'))
    for (o, dirn, w, h) in EXTRA_MOUTHS:
        p = surface_hit(sd, o, dirn)
        if p is not None:
            mouths.append(dict(pos=p, normal=normal_at(sd, p), w=w / 16.0 * 1.5, h=h / 16.0 * 1.5, vertical=False))
    return eyes, mouths


def frame_of(n):
    """A tangent frame on the surface: n out, t across (horizontal where it can be), b up the surface."""
    up = np.array([0, 1.0, 0]) if abs(n[1]) < 0.9 else np.array([1.0, 0, 0])
    t = np.cross(up, n)
    t /= np.linalg.norm(t)
    b = np.cross(n, t)
    return t, b


def full_sdf(px, py, pz, eyes, mouths):
    d = base_sdf(px, py, pz, keep=True)
    d, hole = maw_carve(d, px, py, pz)
    for e in eyes:   # sockets: the eyeball sits in a hollow
        c = e['pos'] - e['normal'] * e['size'] * 0.18
        r = e['size'] * 0.62
        d = smax(d, -(np.sqrt((px - c[0]) ** 2 + (py - c[1]) ** 2 + (pz - c[2]) ** 2) - r), 0.04)
    for m in mouths:  # small mouths: a lens slit across the surface
        n = m['normal']
        t, b = frame_of(n)
        if m['vertical']:
            t, b = b, t
        qx, qy, qz = px - m['pos'][0], py - m['pos'][1], pz - m['pos'][2]
        lt = qx * t[0] + qy * t[1] + qz * t[2]
        lb = qx * b[0] + qy * b[1] + qz * b[2]
        ln = qx * n[0] + qy * n[1] + qz * n[2]
        slit = np.sqrt((lt / (m['w'] / 2)) ** 2 + (lb / (m['h'] / 2)) ** 2 + ((ln + 0.12) / 0.3) ** 2) - 1.0
        d = smax(d, -slit * min(m['w'], m['h']) * 0.5, 0.03)
    return d, hole


# ---- painting

def ramp_index(lit):
    return np.clip(np.floor((lit + 0.05) / 1.1 * 7.5) + 1, 1, 8).astype(int)


def paint(px, py, pz, d, hole):
    """A colour for every solid voxel: light from the moon on the smoothed normal, ambient occlusion from the field,
    quantised to the flesh ramp; crevices near black; wet glints on lump tops; the warm lamps from below; dead skin
    and veins; gums and throat in the maw."""
    gx, gy, gz = np.gradient(d, S)
    nrm = np.sqrt(gx * gx + gy * gy + gz * gz) + 1e-6
    nx, ny, nz = gx / nrm, gy / nrm, gz / nrm
    lit = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
    # occlusion: how much field sits close over the surface along the normal
    occ = np.zeros_like(d)
    dd = ndimage.uniform_filter(np.minimum(d, 0.6), size=7)
    occ = np.clip(-dd * 2.2 + 0.25, 0, 1)
    idx = ramp_index(lit * 0.85 + 0.2 - occ * 0.9)
    # depth into the mass from the front darkens a little (the back hugs the wall in shadow)
    idx = np.where(px < -0.3, np.maximum(idx - 1, 1), idx)
    # low on the floor it lies in the mass's shadow: the spill and its fingers stay dark
    idx = np.where(py < 0.65, np.maximum(np.minimum(idx, 5) - 1, 1), idx)
    spec = nx * HALF[0] + ny * HALF[1] + nz * HALF[2]
    curv = ndimage.laplace(d) / (S * S)
    idx = np.where((spec > 0.975) & (idx >= 6) & (curv > 6.0), 9, idx)
    cr = SEAM.get('crease', np.zeros_like(d))
    idx = np.where(cr > 0.45, np.maximum(idx - 3, 0), idx)
    idx = np.where(cr > 0.75, 0, idx)
    idx = np.where((np.abs(nz) > 0.5) & (py > 0.65) & (px > -0.3) & (occ < 0.5), np.maximum(idx, 4), idx)   # the sides are not in the moon's light but are not black
    idx = np.where(occ > 0.62, np.minimum(idx, 1), idx)
    idx = np.where(occ > 0.8, 0, idx)
    ramp = np.zeros_like(idx)
    for c, r in DEAD:
        ramp = np.where((px - c[0]) ** 2 + (py - c[1]) ** 2 + (pz - c[2]) ** 2 < r * r, 1, ramp)
    fl = np.array(C.FLESH, np.uint8)
    dk = np.array(C.DEAD5, np.uint8)
    col = np.where((ramp == 1)[..., None], dk[idx], fl[idx])
    # warm from the lamps below, on the under-sides low down
    warm = (ny < -0.45) & (py < 2.2) & (idx >= 2)
    col = np.where(warm[..., None], ((col.astype(int) * 3 + np.array(C.WARM)) // 4).astype(np.uint8), col)
    # veins
    for pts in VEINS:
        dv = chain(px, py, pz, pts, 0.05, 0.03)
        vm = (dv < 0.03) & (idx >= 4)
        col = np.where(vm[..., None], np.array(C.VEIN, np.uint8), col)
    # the maw: gums at the lips, throat inside
    m = MAW
    inside = hole < 0.18
    depth_in = m['front_x'] - px
    gum = inside & (depth_in < 0.55)
    col = np.where(gum[..., None], np.array(C.GUM, np.uint8)[np.clip((ny * 1.5 + 1.5).astype(int), 0, 3)], col)
    throat = inside & (depth_in >= 0.55)
    # the throat is wet red going to black only at the very back, lit a little from the opening
    ti = np.clip(idx - 2 - (depth_in > 1.5) * 2, 0, 5)
    tpal = np.array([C.THROAT[0], C.THROAT[1], C.THROAT[2], C.GUM[0], C.GUM[1], C.GUM[2]], np.uint8)
    col = np.where(throat[..., None], tpal[ti], col)
    tg = tongue(px, py, pz) < 0.06
    gi = np.clip(idx // 3, 0, 2)
    tpal2 = np.array([C.THROAT[2], C.GUM[0], C.GUM[1]], np.uint8)
    col = np.where((tg & inside)[..., None], tpal2[gi], col)
    return col


# ---- the heart: its own part, voxelized at 1/16

HS = 1.0 / 16.0


def heart_model():
    """Two ventricles and the apex (dark red, a wet highlight), the aortic arch with three branches and the pulmonary
    trunk (pale), the vena cava (blue), a pale artery down the front groove. Local frame: centre of the body at the
    origin, the same axes as the devil."""
    n = 32
    ax = (np.arange(n) + 0.5) * HS - n * HS / 2
    px, py, pz = np.meshgrid(ax, ax + 0.2, ax, indexing='ij')
    body = smin(ellipsoid(px, py, pz, (0, -0.1, 0.05), (0.42, 0.5, 0.48)),
                ellipsoid(px, py, pz, (0.02, 0.1, -0.22), (0.32, 0.34, 0.3)), 0.12)
    body = smin(body, ellipsoid(px, py, pz, (0.05, -0.55, -0.12), (0.2, 0.22, 0.2)), 0.12)
    arch = chain(px, py, pz, [(0.05, 0.3, 0.02), (0.05, 0.62, 0.0), (0.0, 0.8, 0.18), (-0.1, 0.72, 0.4), (-0.15, 0.45, 0.42)],
                 0.11, 0.1)
    for b in ([(0.0, 0.78, 0.08), (0.0, 0.98, 0.06)], [(-0.04, 0.82, 0.2), (-0.04, 1.0, 0.22)], [(-0.08, 0.76, 0.32), (-0.08, 0.95, 0.36)]):
        arch = np.minimum(arch, chain(px, py, pz, b, 0.05, 0.045))
    trunk = chain(px, py, pz, [(0.12, 0.25, -0.18), (0.14, 0.5, -0.3)], 0.1, 0.09)
    cava = chain(px, py, pz, [(-0.15, 0.2, 0.33), (-0.15, 0.95, 0.33)], 0.08, 0.08)
    solid_b, solid_a, solid_t, solid_c = body < 0, arch < 0, trunk < 0, cava < 0
    solid = solid_b | solid_a | solid_t | solid_c
    d = np.minimum.reduce([body, arch, trunk, cava])
    gx, gy, gz = np.gradient(d, HS)
    nr = np.sqrt(gx * gx + gy * gy + gz * gz) + 1e-6
    lit = (gx * LIGHT[0] + gy * LIGHT[1] + gz * LIGHT[2]) / nr
    col = np.zeros(px.shape + (3,), np.uint8)
    hp = np.array(C.HEART_PAL, np.uint8)
    ap = np.array(C.ART_PAL, np.uint8)
    vp = np.array(C.VEN_PAL, np.uint8)
    hi = np.clip(np.floor((lit + 0.2) / 1.2 * 5) + 1, 1, 5).astype(int)
    spec = (gx * HALF[0] + gy * HALF[1] + gz * HALF[2]) / nr
    hi = np.where(spec > 0.97, 6, hi)
    col = np.where(solid_b[..., None], hp[hi], col)
    ti = np.clip(np.floor((lit + 0.3) / 1.3 * 4), 0, 3).astype(int)
    col = np.where((solid_t & ~solid_b)[..., None], ap[np.maximum(ti - 1, 0)], col)
    col = np.where((solid_a & ~solid_b)[..., None], ap[ti], col)
    col = np.where((solid_c & ~solid_b & ~solid_a)[..., None], vp[ti], col)
    groove = solid_b & (np.abs(pz - (0.05 - (py + 0.1) * 0.35)) < 0.035) & (px > 0.25)
    col = np.where(groove[..., None], ap[2], col)
    return solid, col, (px, py, pz)


# ---- teeth (boxes: a wider root and a narrower tip, a hair crooked)

def make_teeth():
    m = MAW
    rng = random.Random(21)
    teeth = []
    for upper in (True, False):
        z = -m['half_w'] + 0.22
        while z < m['half_w'] - 0.2:
            w = rng.choice((0.19, 0.25, 0.25, 0.31, 0.22))
            zc = z + w / 2
            f = zc / m['half_w']
            rim = m['half_h'] * math.sqrt(max(0.0, 1 - f * f))
            if rim < 0.25:
                z += w + 0.09
                continue
            missing = rng.random() < 0.06
            length = rim * rng.uniform(0.34, 0.6)
            over = abs(zc) < 0.62
            if over:
                length *= 0.55 if upper else 0.8
            y_root = m['y'] + (rim - 0.12) * (1 if upper else -1)
            x_root = m['front_x'] - 0.25 - (1 - math.sqrt(max(0, 1 - f * f))) * 0.5
            if not missing:
                teeth.append(dict(root=[x_root, y_root, zc], width=w, length=round(length, 3), upper=upper,
                                  lean=[rng.uniform(-6, 6), rng.uniform(-8, 8)], shade=rng.choice((3, 3, 2, 3, 2)),
                                  stain=rng.random() < 0.3, over_heart=over))
            z += w + rng.choice((0.03, 0.05, 0.07))
    return teeth


# ---- voxels -> quads

DIRS = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
FACE_SHADE = [0.92, 0.7, 1.0, 0.55, 0.82, 0.82]   # Minecraft-like: tops brightest, bottoms darkest


def greedy_quads(solid, col, scale, origin, part):
    """Exposed faces of a voxel grid, merged into rectangles of one colour per slice."""
    quads = []
    sh = solid.shape
    pad = np.pad(solid, 1)
    keyc = (col[..., 0].astype(np.int32) << 16) | (col[..., 1].astype(np.int32) << 8) | col[..., 2].astype(np.int32)
    for di, (dx, dy, dz) in enumerate(DIRS):
        nb = pad[1 + dx:1 + dx + sh[0], 1 + dy:1 + dy + sh[1], 1 + dz:1 + dz + sh[2]]
        exposed = solid & ~nb
        if part == 0:
            if di == 3:
                exposed &= np.arange(sh[1])[None, :, None] > 0          # nor the floor
        axis = 0 if dx else (1 if dy else 2)
        # in-plane axes (u, v) per the file format
        uax, vax = {0: (2, 1), 1: (0, 2), 2: (0, 1)}[axis]
        shade = FACE_SHADE[di]
        for sl in range(sh[axis]):
            idx = [slice(None)] * 3
            idx[axis] = sl
            ex = exposed[tuple(idx)]
            if not ex.any():
                continue
            kc = keyc[tuple(idx)]
            # arrange as [u, v]
            rem = [a for a in (0, 1, 2) if a != axis]
            if rem.index(uax) == 1:
                ex, kc = ex.T, kc.T
            used = np.zeros_like(ex)
            U, V = ex.shape
            for u in range(U):
                for v in range(V):
                    if not ex[u, v] or used[u, v]:
                        continue
                    k = kc[u, v]
                    dv = 1
                    while v + dv < V and ex[u, v + dv] and not used[u, v + dv] and kc[u, v + dv] == k:
                        dv += 1
                    du = 1
                    while u + du < U and np.all(ex[u + du, v:v + dv]) and not np.any(used[u + du, v:v + dv]) \
                            and np.all(kc[u + du, v:v + dv] == k):
                        du += 1
                    used[u:u + du, v:v + dv] = True
                    corner = [0, 0, 0]
                    corner[axis] = sl + (1 if (dx + dy + dz) > 0 else 0)
                    corner[uax] = u
                    corner[vax] = v
                    rgb = ((k >> 16) & 255, (k >> 8) & 255, k & 255)
                    rgb = tuple(int(min(255, c * shade)) for c in rgb)
                    pos = [origin[i] + corner[i] * scale for i in range(3)]
                    quads.append((di, part, pos, du * scale, dv * scale, rgb))
    return quads


def write_mesh(quads, path):
    with open(path, 'wb') as f:
        f.write(struct.pack('<i', len(quads)))
        for (di, part, pos, du, dv, rgb) in quads:
            f.write(struct.pack('<BBhhhhhBBBB', di, part, *[int(round(c * 16)) for c in pos],
                                int(round(du * 16)), int(round(dv * 16)), *rgb, 0))


# ---- the model

def build_model():
    eyes, mouths = place_features()
    px, py, pz = grid()
    d, hole = full_sdf(px, py, pz, eyes, mouths)
    solid = d < 0
    col = paint(px, py, pz, d, hole)
    # drop specks: a voxel alone is noise
    nbrs = ndimage.convolve(solid.astype(int), np.ones((3, 3, 3), int), mode='constant') - solid
    solid &= nbrs >= 3
    hsolid, hcol, _ = heart_model()
    heart_at = np.array([MAW['front_x'] - 1.15, MAW['y'] - 0.05, 0.05])
    teeth = make_teeth()
    return dict(eyes=eyes, mouths=mouths, solid=solid, col=col, hsolid=hsolid, hcol=hcol, heart_at=heart_at, teeth=teeth)


def tooth_boxes(t):
    """A tooth as two boxes for the preview and the game: root (wider, yellower) and tip (narrower, whiter)."""
    sgn = -1 if t['upper'] else 1
    rx, ry, rz = t['root']
    L, w = t['length'], t['width']
    root_len = L * 0.45
    return [((rx - 0.1, ry + sgn * 0.0, rz - w / 2), (rx + 0.1, ry + sgn * root_len, rz + w / 2), 2),
            ((rx - 0.07, ry + sgn * root_len, rz - w * 0.36), (rx + 0.07, ry + sgn * L, rz + w * 0.36), t['shade'])]


def eye_voxels(e, res):
    """Preview only: an eyeball's front as a disc of voxels (white, iris toward its gaze, pupil)."""
    out = []
    n = e['normal']
    t, b = frame_of(n)
    r = e['size'] / 2
    pal = C.IRIS[e['iris']]
    c = e['pos'] - n * e['size'] * 0.05
    steps = max(2, int(r / res) + 1)
    g = np.array(e['gaze'][:2] + [0])[:2]
    for i in range(-steps, steps + 1):
        for j in range(-steps, steps + 1):
            a, bb = i * res, j * res
            if (a / r) ** 2 + (bb / (r * 0.62 * e['opn'])) ** 2 > 1:
                continue
            p = c + t * a + b * bb
            ia, ib = a / r - g[0] * 0.35, bb / r + g[1] * 0.2
            dd = math.hypot(ia, ib)
            colr = C.SCLERA[0]
            if dd < 0.42:
                colr = pal[1]
            if dd < 0.2:
                colr = C.PUPIL
            out.append((p, colr))
    return out


def preview_grid(md):
    """Everything in one 1/16 grid for the offline views: flesh (upsampled), heart, teeth, eyes, small mouths' teeth."""
    f = 2
    solid = np.repeat(np.repeat(np.repeat(md['solid'], f, 0), f, 1), f, 2)
    col = np.repeat(np.repeat(np.repeat(md['col'], f, 0), f, 1), f, 2)
    res = S / f

    def put_box(a, b, c):
        i0 = [int(math.floor((min(a[k], b[k]) - o) / res)) for k, o in enumerate((X0, Y0, Z0))]
        i1 = [int(math.ceil((max(a[k], b[k]) - o) / res)) for k, o in enumerate((X0, Y0, Z0))]
        i0 = [max(0, v) for v in i0]
        i1 = [min(solid.shape[k], v) for k, v in enumerate(i1)]
        if all(i1[k] > i0[k] for k in range(3)):
            solid[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = True
            col[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = c

    hs, hc = md['hsolid'], md['hcol']
    o = md['heart_at'] - np.array(hs.shape) * HS / 2
    for (i, j, k) in zip(*np.where(hs)):
        p = o + np.array([i, j, k]) * HS
        put_box(p, p + HS, hc[i, j, k])
    for t in md['teeth']:
        for (a, b, sh) in tooth_boxes(t):
            put_box(a, b, C.TOOTH[sh])
    for e in md['eyes']:
        for p, c in eye_voxels(e, res):
            put_box(p - res / 2, p + res / 2, c)
    return solid, col, res


def dda(solid, O, D):
    """Vectorized voxel traversal from a camera outside the grid: each ray starts where it enters the grid's box.
    Returns hit mask, the hit cell, the axis of the face it came through, the step signs."""
    N = D.shape[0]
    sh = np.array(solid.shape)
    Dz = np.where(D == 0, 1e-9, D)
    t1 = (0 - O[None]) / Dz
    t2 = (sh[None] - O[None]) / Dz
    tn = np.max(np.minimum(t1, t2), axis=1)
    tf = np.min(np.maximum(t1, t2), axis=1)
    alive = (tf > np.maximum(tn, 0))
    t0 = np.maximum(tn, 0) + 1e-5
    P = O[None] + t0[:, None] * D
    cell = np.clip(np.floor(P).astype(int), 0, sh - 1)
    step = np.where(D > 0, 1, -1)
    tDelta = np.abs(1 / Dz)
    tMax = np.where(D == 0, np.inf, ((cell + (step > 0)) - O[None]) / Dz)
    axis = np.argmax(np.where(np.minimum(t1, t2) == tn[:, None], 1, 0), axis=1)
    hit = np.zeros(N, bool)
    out_cell = np.zeros((N, 3), int)
    ar = np.arange(N)
    for _ in range(int(sh.sum()) + 4):
        inb = np.all((cell >= 0) & (cell < sh), axis=1)
        alive &= inb
        if not alive.any():
            break
        idx = np.where(alive)[0]
        s = solid[cell[idx, 0], cell[idx, 1], cell[idx, 2]]
        newh = idx[s]
        hit[newh] = True
        out_cell[newh] = cell[newh]
        alive[newh] = False
        a = np.argmin(tMax, axis=1)
        cell[ar, a] += step[ar, a]
        tMax[ar, a] += tDelta[ar, a]
        axis = np.where(alive, a, axis)
    return hit, out_cell, axis, step


def render_view(solid, col, res, cam, look, up=(0, 1, 0), fov=70, size=(270, 480), sky=True):
    """Ray-cast the voxel grid (vectorized DDA). cam/look in devil frame (blocks)."""
    RW, RH = size
    cam, look = np.array(cam, float), np.array(look, float)
    fwd = look - cam
    fwd /= np.linalg.norm(fwd)
    # the devil frame is the hotel's mirrored in x (X = 37 - x), so the screen's right is up x forward here
    right = np.cross(np.array(up, float), fwd)
    right /= np.linalg.norm(right)
    upv = np.cross(fwd, right)
    th = math.tan(math.radians(fov) / 2)
    ys, xs = np.mgrid[0:RH, 0:RW].astype(float) + 0.5
    sx = (xs / RW * 2 - 1) * th * RW / RH
    sy = (1 - ys / RH * 2) * th
    D = fwd[None, None] + sx[..., None] * right[None, None] + sy[..., None] * upv[None, None]
    D = (D / np.linalg.norm(D, axis=2, keepdims=True)).reshape(-1, 3)
    O = (cam - np.array([X0, Y0, Z0])) / res
    ok, cell, axis, step = dda(solid, O, D)
    img = np.zeros((D.shape[0], 3), float)
    alt = D[:, 1]
    img[:] = np.array(C.SKY[1], float) * (1 - np.clip(alt, 0, 1))[:, None] + np.array(C.SKY[0], float) * np.clip(alt, 0, 1)[:, None]
    c = col[cell[:, 0], cell[:, 1], cell[:, 2]].astype(float)
    shade = np.ones(D.shape[0])
    for ax, (sp, sn) in enumerate(((0.92, 0.7), (1.0, 0.55), (0.82, 0.82))):
        sel = axis == ax
        shade[sel] = np.where(step[sel, ax] < 0, sp, sn)
    img[ok] = c[ok] * shade[ok, None]
    # the floor and the back wall, dark, for orientation
    tf = -O[1] / np.where(np.abs(D[:, 1]) < 1e-6, -1e-6, D[:, 1])
    fl = (~ok) & (tf > 0) & (D[:, 1] < 0)
    fp = O[None] + tf[:, None] * D
    chk = ((np.floor(fp[:, 0] * res) + np.floor(fp[:, 2] * res)) % 2 == 0)
    img[fl] = np.where(chk[fl, None], np.array([46, 40, 44]), np.array([38, 33, 37]))
    return np.clip(img, 0, 255).reshape(RH, RW, 3).astype(np.uint8)


VIEWS = [
    ('corridor', (11.5 - 3.5 + 4.0, 1.62, 0.0), (0.0, 2.6, 0.0)),     # 8 blocks from the bulk, eye height
    ('three_left', (7.5, 2.4, -5.0), (1.5, 2.6, 0.0)),
    ('three_right', (7.5, 2.4, 5.0), (1.5, 2.6, 0.0)),
    ('side', (2.6, 3.6, 16.0), (2.6, 3.6, 0.0)),
    ('above', (7.0, 8.5, 0.0), (1.8, 2.2, 0.0)),
    ('below', (6.5, 0.35, 1.0), (2.0, 3.6, 0.0)),
]


def preview(md, name='sculpt'):
    os.makedirs(OUT, exist_ok=True)
    solid, col, res = preview_grid(md)
    tiles = []
    for nm, cam, look in VIEWS:
        if nm == 'side':
            # from the side the flesh against the outer wall would hide the profile: cut there (a section)
            sec = solid.copy()
            sec[:, :, int((4.4 - Z0) / res):] = False
            tiles.append(('side (section, outer wall side cut away)', render_view(sec, col, res, cam, look, fov=40)))
            continue
        tiles.append((nm, render_view(solid, col, res, cam, look)))
    W_, H_ = 270, 480
    sheet = Image.new('RGB', (W_ * 3 + 20, H_ * 2 + 50), (16, 14, 20))
    d = ImageDraw.Draw(sheet)
    for i, (nm, im) in enumerate(tiles):
        x, y = 5 + (i % 3) * (W_ + 5), 25 + (i // 3) * (H_ + 25)
        sheet.paste(Image.fromarray(im), (x, y))
        d.text((x, y - 18), nm, fill=(230, 224, 216), font=C.font(15))
    sheet.save(os.path.join(OUT, name + '.png'))
    return sheet


def build(md):
    os.makedirs(OUT_ASSETS, exist_ok=True)
    quads = greedy_quads(md['solid'], md['col'], S, (X0, Y0, Z0), 0)
    hs = md['hsolid']
    ho = md['heart_at'] - np.array(hs.shape) * HS / 2
    quads += greedy_quads(hs, md['hcol'], HS, tuple(ho), 1)
    write_mesh(quads, os.path.join(OUT_ASSETS, 'eternity_devil_mesh.bin'))

    def v(a):
        return [round(float(x), 4) for x in a]
    parts = dict(
        note='Made by tools/eternity_devil_sculpt.py. Blocks, devil frame: X out from the back wall (hotel x = 37 - X), '
             'Y up from the floor (hotel y = 29 + Y), Z across (hotel z = 6.5 + Z).',
        quads=len(quads),
        eyes=[dict(pos=v(e['pos']), normal=v(e['normal']), size=round(e['size'], 4), iris=[list(c) for c in C.IRIS[e['iris']]],
                   rest_gaze=e['gaze'], open=e['opn']) for e in md['eyes']],
        mouths=[dict(pos=v(m['pos']), normal=v(m['normal']), w=round(m['w'], 4), h=round(m['h'], 4), vertical=m['vertical'])
                for m in md['mouths']],
        teeth=[dict(root=v(t['root']), width=t['width'], length=t['length'], upper=t['upper'], lean=v(t['lean']),
                    root_colour=list(C.TOOTH[2]), tip_colour=list(C.TOOTH[t['shade']]),
                    stain_colour=list(C.TOOTH[0]) if t['stain'] else None, over_heart=t['over_heart'])
               for t in md['teeth']],
        heart=dict(pivot=v(md['heart_at']), part=1),
        maw=dict(MAW, jaw_centre=[MAW['front_x'] - 0.3, MAW['y'] - 0.95, 0.0], jaw_radii=[1.2, 0.8, 2.2], jaw_drop=0.22),
        breathe=dict(centre=[2.0, 1.75, 0.1], radii=[2.4, 2.2, 3.2], amount=0.05, period_ticks=70),
        colours=dict(sclera=[list(c) for c in C.SCLERA], lash=list(C.LASH), pupil=list(C.PUPIL), glint=list(C.GLINT),
                     lid=list(C.FLESH[5]), lid_dark=list(C.FLESH[2]), gum=[list(c) for c in C.GUM]),
    )
    with open(os.path.join(OUT_ASSETS, 'eternity_devil_parts.json'), 'w') as f:
        json.dump(parts, f, indent=1)
    print('build: %d quads (%d flesh voxels), %d eyes, %d small mouths, %d teeth' % (
        len(quads), int(md['solid'].sum()), len(md['eyes']), len(md['mouths']), len(md['teeth'])))


def main():
    args = sys.argv[1:] or ['preview']
    md = build_model()
    if 'preview' in args:
        preview(md)
    if 'build' in args:
        build(md)


if __name__ == '__main__':
    main()
