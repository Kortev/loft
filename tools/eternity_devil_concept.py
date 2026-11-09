"""The Eternity Devil, a pixel-art concept painted at true game resolution (16 texels a block).

Two earlier looks failed because they were generated, not composed (a smooth Blender face, then 600 random boxes).
This paints the devil the way a pixel artist would: a few great lobes with clear silhouettes first, then the
crevices between them, then eyes in sockets and mouths of human teeth placed one by one, with one great mouth and
the heart inside it as the focal point. Every lobe, eye, mouth and vein below is placed by hand in the data; the
code only shades them with a limited palette (light from the moon above, a warm glow from the hotel's lamps below).

    python tools/eternity_devil_concept.py            # everything: the three compositions, the passes, the final sheets
    python tools/eternity_devil_concept.py comps      # only the three compositions (comp_A/B/C.png, comps.png)
    python tools/eternity_devil_concept.py pass 3     # only refining pass 3 (pass3.png, _phone_pass3.png)
    python tools/eternity_devil_concept.py export DIR # the final cut up for the game: atlas + layout json
    python tools/eternity_devil_concept.py present    # the final: final_front_6x, final_mouth_heart, final_side,
                                                      # final_cuboid_plan, final_game_frame (1080 x 1920), _concept_small

Writes into work/eternity_look_v4/ (git-ignored). No game code. The mock game frame reads Minecraft's own block
textures from the Loom cache's client jar (flat colours if it isn't there).
"""
import math
import os
import random
import sys
import zipfile
from io import BytesIO

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, '..', 'work', 'eternity_look_v4'))

W, H = 176, 112          # 11 x 7 blocks at 16 texels a block


def hx(s):
    return (int(s[1:3], 16), int(s[3:5], 16), int(s[5:7], 16))


# ---- The palette. Flesh is one ramp of ten (crevice black to wet specular) plus one warm lamp glow.

FLESH = [hx(c) for c in ('#140409', '#2c0911', '#4a0f19', '#6b1820', '#8f2427', '#b13834', '#cc5a4d',
                         '#df8577', '#eeb3a3', '#fbe6dc')]
WARM = hx('#e8935c')
# Dead skin swaps the middle of the ramp for four grey-pinks (the darks are shared).
DEAD = [FLESH[0], FLESH[1], hx('#4a3537'), hx('#4a3537'), hx('#6a5353'), hx('#8a7271'), hx('#8a7271'),
        hx('#ad9896'), hx('#ad9896'), hx('#e6d9d4')]
DEAD3 = [FLESH[0], FLESH[1], hx('#4e3a3c'), hx('#5e4848'), hx('#7a6464'), hx('#968080'), hx('#ad9997'),
         hx('#c4b2ae'), hx('#d6c8c2'), hx('#eee4de')]
DEAD5 = [FLESH[0], FLESH[1], hx('#52393c'), hx('#64474a'), hx('#80605f'), hx('#9c7c7a'), hx('#b29492'),
         hx('#c6aca8'), hx('#d8c4be'), hx('#f0e2dc')]     # pass 5: pinker, so it reads as skin, not stone
VEIN = hx('#5e1f45')
VEIN_LIT = hx('#8a3a62')

SCLERA = [hx('#f3eadf'), hx('#dccdbf'), hx('#b39c92'), hx('#d98a82')]     # light, mid, lid shade, pink corner
BLOOD = hx('#c0343a')
BLOOD_THIN = hx('#d27468')
LASH = hx('#1a0507')
IRIS = {
    'brown': [hx('#2c180e'), hx('#5a361e'), hx('#8c5c32')],
    'hazel': [hx('#36300f'), hx('#6c5a24'), hx('#a08a42')],
    'grey': [hx('#222b38'), hx('#4a5c70'), hx('#8296a8')],
    'green': [hx('#1c2a16'), hx('#3e5c30'), hx('#6e8e4c')],
    'dark': [hx('#0e0806'), hx('#2a1a12'), hx('#4a3020')],
    'milky': [hx('#8c9298'), hx('#b8c0c4'), hx('#dce2e2')],     # a dead eye, clouded over
}
PUPIL = hx('#050203')
GLINT = hx('#ffffff')

GUM = [hx('#4e0f1e'), hx('#862638'), hx('#b44458'), hx('#da7484')]
TOOTH = [hx('#6e5a34'), hx('#a8925c'), hx('#d2c39a'), hx('#ece3c6'), hx('#fbf6e6')]   # stain, shade, cream, white, lit
THROAT = [hx('#040102'), hx('#1c0407'), hx('#3e0a12')]

BG = hx('#0b0910')

# ---- The heart, placed pixel by pixel: the aorta's arch and the pulmonary trunk on top, the vena cava (blue) to
# the right, two ventricles, a pale coronary artery down the front. K outline, D/R/r/h reds, s wet glint, P/p pale
# arteries, V/v the vein, '.' nothing.
HEART_ART = """
.......KKKK.........
......KPPPPK..KKK...
.....KPsPPPPKKVvK...
..KK.KPPKKPPPKVVK...
.KppKKPPK.KPPKVVK...
.KpppKPPK..KPKVVK...
..KppKPPPK.KKKVVK...
..KppKpPPK.KDRVVK...
.KKDDKKPPKKRRRDVDK..
KDRRRDKKPPKRrRRRDDK.
KDRrhhRDKpKrhhrRRDDK
KDRhhshRDRrhshhrRRDK
KDRrhhrRPRrrhhrRRRDK
KDRRrrRRPRRrrrRRRDDK
KDDRRRRRpPRRRRRRRDK.
.KDRRRRRRPRRRRRRDDK.
.KDDRRRRRpPRRRRRDK..
..KDRRRRRRPRRRRDDK..
..KDDRRRRRpPRRRDK...
...KDDRRRRRPRRDDK...
....KDDRRRRpRDDK....
.....KKDDRRRDDK.....
.......KKDDDKK......
.........KKK........
"""
HEART_COL = {'K': hx('#24040a'), 'D': hx('#560c17'), 'R': hx('#861a26'), 'r': hx('#ab2c35'),
             'h': hx('#d45a5c'), 's': hx('#ffd8cc'), 'P': hx('#e6b8b4'), 'p': hx('#b07a82'),
             'V': hx('#3a1c46'), 'v': hx('#6c3c78')}


def heart_sprite():
    rows = [r for r in HEART_ART.strip('\n').split('\n')]
    h, w = len(rows), max(len(r) for r in rows)
    a = np.zeros((h, w, 4), np.uint8)
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c in HEART_COL:
                a[y, x, :3] = HEART_COL[c]
                a[y, x, 3] = 255
    return a


HEART_PAL = [hx('#1c0207'), hx('#420812'), hx('#6a1020'), hx('#94202c'), hx('#bc3a40'), hx('#de6a66'), hx('#ffd8cc')]
ART_PAL = [hx('#4a222c'), hx('#8e5a64'), hx('#c48e94'), hx('#ecc6c0')]     # the pale arteries
VEN_PAL = [hx('#1a0c24'), hx('#361a46'), hx('#58326e'), hx('#86589a')]     # the vena cava


def heart_v2(scale=1.0, simple=False):
    """Pass 1's heart, built rather than typed: two ventricles and the apex shaded like the lobes, the aorta's arch
    and the pulmonary trunk as pale tubes on top, the vena cava behind, pale coronary arteries over the front."""
    S = scale
    Wd, Ht = int(30 * S), int(32 * S)
    ys, xs = np.mgrid[0:Ht, 0:Wd].astype(float) + 0.5
    f = (ellipse_field(xs, ys, 15 * S, 20 * S, 10 * S, 9.5 * S) + ellipse_field(xs, ys, 10 * S, 17 * S, 6 * S, 6 * S)
         + ellipse_field(xs, ys, 19.5 * S, 16.5 * S, 7 * S, 6 * S) + 0.8 * ellipse_field(xs, ys, 11 * S, 27 * S, 4.5 * S, 3.5 * S))
    inside = f > T
    hgt = np.sqrt(np.clip((f - T) / (1 - T), 0, 1)) * 9 * S
    gy, gx = np.gradient(hgt)
    nrm = np.sqrt(gx * gx + gy * gy + 1)
    lit = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nrm
    spec = (-gx * HALF[0] - gy * HALF[1] + HALF[2]) / nrm
    out = np.zeros((Ht, Wd, 4), np.uint8)

    def put(x, y, c):
        if 0 <= x < Wd and 0 <= y < Ht:
            out[y, x, :3] = c
            out[y, x, 3] = 255

    def tube(path, r, pal, shift=0):
        for (x, y), l in tube_pixels([(a * S, b * S) for a, b in path], r * S).items():
            put(x, y, pal[int(np.clip(round(l * 3) + shift, 0, 3))])

    tube([(22, 13), (23, 3)], 1.9, VEN_PAL)
    tube([(16, 14), (16, 7), (18, 3), (22, 2), (25.5, 5), (25.5, 10)], 2.7, ART_PAL)
    edge = inside & ndimage.binary_dilation(~inside, iterations=1)
    for y in range(Ht):
        for x in range(Wd):
            if inside[y, x]:
                i = int(np.clip(np.floor((lit[y, x] + 0.1) / 1.2 * 5) + 1, 1, 5))
                if spec[y, x] > 0.985:
                    i = 6
                if edge[y, x] and (gy[y, x] < 0 or gx[y, x] < -0.3):
                    i = 0
                put(x, y, HEART_PAL[i])
    tube([(11, 16), (10, 9), (7, 5)], 2.2, ART_PAL, -1)
    tube([(10, 10), (13, 6)], 1.4, ART_PAL, -1)
    paths = ([(15, 14), (14, 19), (12, 24), (11, 28)], [(14, 18), (19, 21), (22, 25)], [(18, 13), (23, 15), (26, 19)])
    if simple:
        paths = ([(15, 14), (14, 18), (13, 22), (11, 27)], [(14, 19), (17, 22)])
    for path in paths:
        for j, (x, y) in enumerate(line_pixels([(a * S, b * S) for a, b in path])):
            if 0 <= x < Wd and 0 <= y < Ht and inside[y, x] and not edge[y, x]:
                put(x, y, ART_PAL[2] if not simple or j < 4 else ART_PAL[1])
                if y + 1 < Ht and inside[y + 1, x]:
                    put(x, y + 1, HEART_PAL[1])
    return out


PUL_PAL = [hx('#300a18'), hx('#561628'), hx('#7c2a42'), hx('#a04c64')]     # pulmonary trunk: dark, so the arch leads


def heart_v3(scale=1.0, v5=False, v6=False):
    """Pass 4's heart: what makes a heart read at a glance is the top, so the aortic arch gets its three branches
    standing up, the pulmonary trunk forks in front of it and the blue vena cava rises behind; the body is darker
    with one wet highlight, and a single pale artery runs down the front."""
    S = scale
    Wd, Ht = int(31 * S), int(37 * S)
    ys, xs = np.mgrid[0:Ht, 0:Wd].astype(float) + 0.5
    oy = 5
    f = (ellipse_field(xs, ys, 15 * S, (20 + oy) * S, 10 * S, 9.5 * S) + ellipse_field(xs, ys, 10 * S, (17 + oy) * S, 6 * S, 6 * S)
         + ellipse_field(xs, ys, 19.5 * S, (16.5 + oy) * S, 7 * S, 6 * S)
         + 0.8 * ellipse_field(xs, ys, 11 * S, (27 + oy) * S, 4.5 * S, 3.5 * S))
    inside = f > T
    hgt = np.sqrt(np.clip((f - T) / (1 - T), 0, 1)) * 9 * S
    gy, gx = np.gradient(hgt)
    nrm = np.sqrt(gx * gx + gy * gy + 1)
    lit = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nrm
    spec = (-gx * HALF[0] - gy * HALF[1] + HALF[2]) / nrm
    out = np.zeros((Ht, Wd, 4), np.uint8)

    def put(x, y, c):
        if 0 <= x < Wd and 0 <= y < Ht:
            out[y, x, :3] = c
            out[y, x, 3] = 255

    def tube(path, r, pal, shift=0):
        for (x, y), l in tube_pixels([(a * S, (b + oy) * S) for a, b in path], r * S).items():
            put(x, y, pal[int(np.clip(round(l * 3) + shift, 0, 3))])

    tube([(22.5, 13), (23.5, 1)], 1.9, VEN_PAL)
    for br in ([(18.5, 3), (18, -3.5)], [(21.3, 2), (21.5, -4)], [(24, 3), (25.5, -2.5)]):
        tube(br, 1.0, ART_PAL, -1)
    tube([(16, 14), (16, 7), (18, 3), (22, 2), (25.5, 5), (25.5, 11)], 2.6, ART_PAL)
    edge = inside & ndimage.binary_dilation(~inside, iterations=1)
    for y in range(Ht):
        for x in range(Wd):
            if inside[y, x]:
                i = int(np.clip(np.floor((lit[y, x] + 0.0) / 1.2 * 5) + 1, 1, 5))
                if spec[y, x] > 0.98:
                    i = 6
                if edge[y, x] and (gy[y, x] < 0 or gx[y, x] < -0.3):
                    i = 0
                put(x, y, HEART_PAL[i])
    if v5:
        # Pass 5: the pulmonary trunk short and dark, low on the left, so the pale arch is what you see first;
        # a dark groove between the ventricles with the pale artery in it.
        if v6:
            # Pass 6: a thick short stump, not a thin stalk (that read as an apple's stem).
            tube([(11.5, 16), (9.5, 12.5)], 2.4, ART_PAL, -1)
        else:
            tube([(11, 16), (10, 11), (8, 8)], 1.8, PUL_PAL)
        for (x, y) in line_pixels([(15 * S, (14 + oy) * S), (14 * S, (19 + oy) * S), (12 * S, (25 + oy) * S),
                                   (11 * S, (29 + oy) * S)]):
            if 0 <= x < Wd and 0 <= y < Ht and inside[y, x] and not edge[y, x]:
                put(x, y, HEART_PAL[1])
                if x + 1 < Wd and inside[y, x + 1]:
                    put(x + 1, y, ART_PAL[2] if y < (24 + oy) * S else ART_PAL[1])
        return out
    tube([(11, 16), (10, 9), (7, 4)], 2.3, ART_PAL, -1)
    tube([(10, 10), (13.5, 5)], 1.4, ART_PAL, -1)
    for path in ([(15, 14), (14, 18), (13, 22), (11, 27)], [(14, 19), (18, 22)]):
        for j, (x, y) in enumerate(line_pixels([(a * S, (b + oy) * S) for a, b in path])):
            if 0 <= x < Wd and 0 <= y < Ht and inside[y, x] and not edge[y, x]:
                put(x, y, ART_PAL[2] if j < 5 else ART_PAL[1])
    return out


# ---- Lines without doubled corners (pixel-perfect).

def line_pixels(pts):
    out = []
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            if not out or out[-1] != (x0, y0):
                out.append((x0, y0))
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy
    # Drop the middle pixel of any L-shaped corner.
    clean = []
    for i, p in enumerate(out):
        if 0 < i < len(out) - 1 and clean:
            a, c = clean[-1], out[i + 1]
            if abs(a[0] - c[0]) == 1 and abs(a[1] - c[1]) == 1:
                continue
        clean.append(p)
    return clean


def tube_pixels(path, r, r_mid=None):
    """Pixels of a round tube of radius r along a polyline (r_mid in the middle if given: a sinew thins as it
    stretches), each with its light (0 dark .. 1 lit from above-left)."""
    pts = [np.array(p, float) for p in path]
    seg = [np.hypot(*(b - a)) for a, b in zip(pts, pts[1:])]
    total = max(sum(seg), 1e-6)
    rmax = max(r, r_mid or 0)
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    out = {}
    for y in range(int(min(ys) - rmax - 1), int(max(ys) + rmax + 2)):
        for x in range(int(min(xs) - rmax - 1), int(max(xs) + rmax + 2)):
            q = np.array([x + 0.5, y + 0.5])
            best = None
            acc = 0.0
            for i, (a, b) in enumerate(zip(pts, pts[1:])):
                ab = b - a
                t = np.clip(np.dot(q - a, ab) / max(np.dot(ab, ab), 1e-9), 0, 1)
                d = q - (a + t * ab)
                dl = np.hypot(*d)
                if best is None or dl < best[0]:
                    best = (dl, d, (acc + t * seg[i]) / total)
                acc += seg[i]
            dl, d, tt = best
            rr = r if r_mid is None else r + (r_mid - r) * math.sin(math.pi * tt)
            if dl <= rr:
                o = d / max(rr, 1e-6)
                z = math.sqrt(max(0.0, 1 - min(1.0, o[0] ** 2 + o[1] ** 2)))
                n = np.array([o[0], o[1], z])
                lit = float(np.dot(n, LIGHT))
                out[(x, y)] = max(0.0, min(1.0, (lit + 0.2) / 1.1))
    return out


# ---- The painter.

LIGHT = np.array([-0.30, -0.85, 0.42])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0])
HALF = HALF / np.linalg.norm(HALF)
T = 0.25


def ellipse_field(xs, ys, cx, cy, rx, ry):
    d2 = ((xs - cx) / (rx * 1.414)) ** 2 + ((ys - cy) / (ry * 1.414)) ** 2
    return np.clip(1 - d2, 0, None) ** 2


class Painter:
    """Holds the flesh as ramp indices (so the palette stays limited) and an overlay of feature pixels."""

    def __init__(self, comp, level=0, w=W, h=H):
        self.comp, self.level, self.w, self.h = comp, level, w, h
        ys, xs = np.mgrid[0:h, 0:w].astype(float) + 0.5
        self.xs, self.ys = xs, ys
        self.idx = np.full((h, w), -1, np.int16)
        self.ramp = np.zeros((h, w), np.int8)
        self.lobe = np.full((h, w), -1, np.int16)
        self.zf = np.full((h, w), -1e9)
        self.ny = np.zeros((h, w))
        self.over = np.zeros((h, w, 4), np.uint8)
        self.featmask = np.zeros((h, w), bool)

    # -- lobes
    def lobes(self):
        comp, xs, ys = self.comp, self.xs, self.ys
        n = len(comp['lobes'])
        zs = np.full((n, self.h, self.w), -1e9)
        nx = np.zeros((n, self.h, self.w))
        nyy = np.zeros((n, self.h, self.w))
        nz = np.zeros((n, self.h, self.w))
        for k, L in enumerate(comp['lobes']):
            f = np.zeros_like(xs)
            for e in L['body']:
                cx, cy, rx, ry = e[:4]
                wgt = e[4] if len(e) > 4 else 1.0
                f += wgt * ellipse_field(xs, ys, cx, cy, rx, ry)
            inside = f > T
            hgt = np.sqrt(np.clip((f - T) / (1 - T), 0, 1))
            for b in L.get('bumps', ()):
                bx, by, brx, bry, amp = b
                hgt = hgt + amp * ellipse_field(xs, ys, bx, by, brx, bry)
            surf = hgt * L['rz']
            gy, gx = np.gradient(surf)
            gx, gy = np.clip(gx, -6, 6), np.clip(gy, -6, 6)
            norm = np.sqrt(gx * gx + gy * gy + 1)
            zs[k] = np.where(inside, L['base'] + surf, -1e9)
            nx[k], nyy[k], nz[k] = -gx / norm, -gy / norm, 1 / norm
        best = np.argmax(zs, axis=0)
        has = np.max(zs, axis=0) > -1e8
        self.lobe = np.where(has, best, -1)
        sel = lambda a: np.take_along_axis(a, best[None], 0)[0]
        self.zf = np.where(has, sel(zs), -1e9)
        NX, NY, NZ = sel(nx), sel(nyy), sel(nz)
        self.ny = NY
        lit = NX * LIGHT[0] + NY * LIGHT[1] + NZ * LIGHT[2]
        spec = NX * HALF[0] + NY * HALF[1] + NZ * HALF[2]
        # Quantise the light into the ramp: clean bands, no dithering.
        lv = np.floor((lit + 0.15) / 1.25 * 7) + 1
        if self.level >= 3:
            # Pass 3: harder light (deeper shadows under every lump), so the flesh stops looking like plastic.
            lv = np.floor((lit + 0.02) / 1.15 * 7.5) + 1
        # Depth: what sits further back is darker (the far lobes sink into the dark).
        zmax = np.max(np.where(has, sel(zs), -1e9))
        lv = lv - np.clip((zmax - sel(zs)) / 22.0, 0, 2.5)
        lv = np.clip(np.round(lv), 1, 8).astype(np.int16)
        if self.level >= 3:
            lv = np.where((spec > 0.972) & (lv >= 5), 9, lv)
        else:
            lv = np.where((spec > 0.985) & (lv >= 6), 9, lv)
        self.idx = np.where(has, lv, -1).astype(np.int16)
        self.spec = spec
        # Contact shadows and crevices: a farther lobe goes dark where a nearer one overlaps it, more below
        # (the moon is above), and black in the one-texel seam.
        for k in range(n):
            mine = self.lobe == k
            nearer = has & (self.lobe != k) & (self.zf > -1e8)
            # pixels of other lobes that are in front of lobe k's level here
            near_mask = np.zeros_like(mine)
            for j in range(n):
                if j == k:
                    continue
                other = self.lobe == j
                if not other.any():
                    continue
                # j is nearer than k along their shared edge if its base is higher
                if comp['lobes'][j]['base'] > comp['lobes'][k]['base']:
                    near_mask |= other
            if not near_mask.any():
                continue
            seam = ndimage.binary_dilation(near_mask, iterations=1) & mine
            seam2 = ndimage.binary_dilation(near_mask, iterations=2) & mine & ~seam
            shadow = np.zeros_like(mine)
            for dy in range(1, 7):
                for dx in (-1, 0, 1):
                    shadow |= np.roll(np.roll(near_mask, dy, 0), dx, 1)
            shadow &= mine
            self.idx = np.where(shadow, np.maximum(self.idx - 2, 1), self.idx)
            self.idx = np.where(seam2, np.minimum(self.idx, 1), self.idx)
            self.idx = np.where(seam, 0, self.idx)
        # The silhouette: moon rim on top edges, lamp warmth on low under-edges, dark elsewhere.
        bgm = ~has
        edge = has & ndimage.binary_dilation(bgm, iterations=1)
        top_edge = edge & np.roll(bgm, 1, 0)
        self.idx = np.where(top_edge & (NY < -0.15), np.maximum(self.idx, 7), self.idx)
        self.idx = np.where(edge & ~top_edge & (NY > 0.2), 1, self.idx)
        self.has = has
        self.warm = edge & (NY > 0.35) & (self.ys > self.h * 0.55) & np.roll(bgm, -1, 0)
        if self.level >= 1:
            # Pass 1: the lamps below are a glow, not an orange outline: the under-sides low down lift one step,
            # and only the lowest under-edges keep the warm colour.
            low = has & (NY > 0.45) & (self.ys > self.h * 0.62) & (self.idx >= 1)
            self.idx = np.where(low & ~edge, np.minimum(self.idx + 1, 5), self.idx)
            self.warm = edge & (NY > 0.6) & (self.ys > self.h * 0.8) & np.roll(bgm, -1, 0)

    def dead_skin(self):
        for patch in self.comp.get('dead', ()):
            f = np.zeros_like(self.xs)
            for (cx, cy, rx, ry) in patch:
                f += ellipse_field(self.xs, self.ys, cx, cy, rx, ry)
            self.ramp = np.where((f > T) & self.has, 1, self.ramp)

    def cleanup(self):
        """Orphan pixels (one texel of a band alone in another band) are noise: fold them into their neighbours."""
        a = self.idx.copy()
        for _ in range(2):
            up, dn = np.roll(a, 1, 0), np.roll(a, -1, 0)
            lf, rt = np.roll(a, 1, 1), np.roll(a, -1, 1)
            same = (up == dn) & (dn == lf) & (lf == rt) & (up != a) & (a > 0) & (up > 0) & (a != 9)
            a = np.where(same, up, a)
            # a band pixel touching only one of its own band in 8 neighbours gets the majority
        self.idx = a

    # -- feature helpers
    clip = None
    rec = None

    def put(self, x, y, col):
        if self.clip is not None and 0 <= x < self.w and 0 <= y < self.h and not self.clip[y, x]:
            return
        if 0 <= x < self.w and 0 <= y < self.h:
            self.over[y, x, :3] = col
            self.over[y, x, 3] = 255
            self.featmask[y, x] = True

    def shade(self, x, y, d, lo=1):
        if self.clip is not None and 0 <= x < self.w and 0 <= y < self.h and not self.clip[y, x]:
            return
        if 0 <= x < self.w and 0 <= y < self.h and self.idx[y, x] >= 0 and not self.featmask[y, x]:
            self.idx[y, x] = max(lo, min(9, self.idx[y, x] + d)) if self.idx[y, x] > 0 else self.idx[y, x]

    def set_idx(self, x, y, v):
        if 0 <= x < self.w and 0 <= y < self.h and self.idx[y, x] >= 0 and not self.featmask[y, x]:
            self.idx[y, x] = v

    def local(self, x, y):
        x, y = min(max(x, 0), self.w - 1), min(max(y, 0), self.h - 1)
        return int(max(self.idx[y, x], 1))

    def flesh(self, x, y, i):
        self.put(x, y, FLESH[i] if self.ramp[min(max(y, 0), self.h - 1), min(max(x, 0), self.w - 1)] == 0 else DEAD[i])

    # -- veins
    def veins(self):
        for path in self.comp.get('veins', ()):
            for (x, y) in line_pixels(path):
                if 0 <= x < self.w and 0 <= y < self.h and self.idx[y, x] >= 3 and not self.featmask[y, x]:
                    self.put(x, y, VEIN if self.idx[y, x] >= 5 else FLESH[1])
                    # the raised vein catches the moon on its upper side
                    if 0 <= y - 1 and self.idx[y - 1, x] >= 4 and not self.featmask[y - 1, x]:
                        self.set_idx(x, y - 1, min(self.idx[y - 1, x] + 1, 8))

    # -- folds: a crease (dark) with the skin under it catching the moon
    def folds(self):
        for path in self.comp.get('folds', ()):
            pts = line_pixels(path)
            for (x, y) in pts:
                if 0 <= x < self.w and 0 <= y < self.h and self.idx[y, x] > 1 and not self.featmask[y, x]:
                    self.idx[y, x] = max(1, self.idx[y, x] - 3)
            for (x, y) in pts:
                if 0 <= x < self.w and 0 <= y + 1 < self.h and (x, y + 1) not in pts:
                    if self.idx[y + 1, x] > 1 and not self.featmask[y + 1, x]:
                        self.idx[y + 1, x] = min(8, self.idx[y + 1, x] + 1)

    # -- pocks: pits in the skin (pass 3), placed by hand in a few patches
    def pocks(self):
        for (x, y) in self.comp.get('pocks', ()):
            if 0 <= x < self.w and 0 <= y + 1 < self.h and self.idx[y, x] > 2 and not self.featmask[y, x]:
                self.idx[y, x] = max(1, self.idx[y, x] - 3)
                if self.idx[y + 1, x] > 1 and not self.featmask[y + 1, x]:
                    self.idx[y + 1, x] = min(8, self.idx[y + 1, x] + 1)

    # -- strands: sinews of flesh stretched across the dark gaps
    def strands(self):
        for path, th in self.comp.get('strands', ()):
            if self.level >= 1:
                # Pass 1: shaded tubes (lit on top, dark under), drawn only where they cross the dark.
                # Pass 2: thick where they grow out of the flesh, thin where they stretch.
                if self.level >= 2:
                    tube = tube_pixels(path, th / 2.0 + 1.2, max(0.6, th / 2.0 - 0.6))
                else:
                    tube = tube_pixels(path, th / 2.0 + 0.3)
                for (x, y), lit in tube.items():
                    if 0 <= x < self.w and 0 <= y < self.h and self.idx[y, x] <= 1 and not self.featmask[y, x]:
                        self.put(x, y, FLESH[int(np.clip(round(2 + lit * 4.5), 1, 7))])
                continue
            pts = line_pixels(path)
            for (x, y) in pts:
                for k in range(th):
                    yy = y + k
                    if 0 <= x < self.w and 0 <= yy < self.h and (self.idx[yy, x] <= 1):
                        self.put(x, yy, FLESH[(5 if th > 1 else 4) if k == 0 else 3])

    # -- eyes
    def eye(self, cx, cy, w, opn=1.0, gaze=(0.0, 0.0), iris='brown', tilt=0.0, blood=1, on=None):
        if on is not None:
            # Pass 2: an eye peering out of a crevice, half under the lobe in front of it.
            k = [L['name'] for L in self.comp['lobes']].index(on)
            self.clip = self.lobe == k
        else:
            self.clip = None
        self._eye(cx, cy, w, opn, gaze, iris, tilt, blood)
        self.clip = None

    def _eye(self, cx, cy, w, opn, gaze, iris, tilt, blood):
        hw = w / 2.0
        h = max(2.0, w * 0.5 * opn)
        sclera = set()
        tops, bots = {}, {}
        for x in range(int(math.floor(cx - hw)), int(math.ceil(cx + hw)) + 1):
            u = (x + 0.5 - cx) / hw
            if abs(u) >= 1:
                continue
            prof = (1 - u * u) ** 0.6
            yc = cy + tilt * (x + 0.5 - cx)
            top = yc - h * 0.56 * prof
            bot = yc + h * 0.44 * prof
            ys = [y for y in range(int(math.floor(top)) - 1, int(math.ceil(bot)) + 2) if top <= y + 0.5 <= bot]
            if w <= 4 and not ys:
                ys = [int(math.floor(yc))]
            if ys:
                for y in ys:
                    sclera.add((x, y))
                tops[x], bots[x] = min(ys), max(ys)
        if not sclera:
            return
        xs_ = sorted(tops)
        x0, x1 = xs_[0], xs_[-1]
        # The socket: a hollow round the eye, darker under the brow.
        srx, sry = hw + 2.2 + (1 if w >= 12 else 0), h / 2 + 2.6 + (1 if w >= 12 else 0)
        for y in range(int(cy - sry) - 1, int(cy + sry) + 2):
            for x in range(int(cx - srx) - 1, int(cx + srx) + 2):
                d = ((x + 0.5 - cx) / srx) ** 2 + ((y + 0.5 - cy) / sry) ** 2
                if d <= 1 and (x, y) not in sclera:
                    self.shade(x, y, (-3 if y < cy else -2) if w >= 7 else (-2 if y < cy else -1))
                elif d <= 1.45 and y > cy and w >= 7:
                    self.shade(x, y, +1)      # the cheek of the socket catches light
        # Sclera.
        for (x, y) in sclera:
            c = SCLERA[0]
            if y == tops[x] and w >= 6:
                c = SCLERA[2]
            elif y == tops[x] + 1 and w >= 10:
                c = SCLERA[1]
            elif (x <= x0 + (1 if w >= 8 else 0) or x >= x1 - (1 if w >= 8 else 0)) and w >= 6:
                c = SCLERA[1] if x != x0 else SCLERA[3]
            self.put(x, y, c)
        # Blood vessels from the corners.
        if w >= (9 if self.level >= 1 else 7):
            rng = random.Random(int(cx * 31 + cy * 17))
            nv = 2 + blood + (1 if w >= 12 else 0)
            if self.level >= 1:
                nv = blood + (1 if w >= 12 else 0) + 1
            for i in range(nv):
                left = i % 2 == 0
                sx = x0 + 1 if left else x1 - 1
                sy = rng.randint(tops[sx], bots[sx])
                length = rng.randint(2, max(2, int(w * 0.28)))
                if self.level >= 1:
                    length = rng.randint(1, max(2, int(w * 0.2)))
                px, py = sx, sy
                for _ in range(length):
                    if (px, py) in sclera:
                        self.put(px, py, BLOOD if self.level < 1 else BLOOD_THIN)
                    px += 1 if left else -1
                    if rng.random() < 0.4:
                        py += rng.choice((-1, 1))
        if self.rec is not None:
            drawn = {(x, y): tuple(int(v) for v in self.over[y, x, :3]) for (x, y) in sclera
                     if 0 <= x < self.w and 0 <= y < self.h and (self.clip is None or self.clip[y, x])}
            self.rec['eyes'].append(dict(cx=cx, cy=cy, w=w, opn=opn, gaze=gaze, iris=iris, sclera=drawn,
                                         ri=max(0.75, w * 0.21), h=h))
        # Iris and pupil.
        ri = max(0.75, w * 0.21)
        icx = cx + gaze[0] * max(0.0, hw - ri - 0.7)
        icy = cy + gaze[1] * h * 0.22
        pal = IRIS[iris]
        for (x, y) in sclera:
            dx, dy = x + 0.5 - icx, y + 0.5 - icy
            d = math.hypot(dx, dy)
            if d < ri:
                if ri >= 2.5 and d > ri - 1:
                    c = pal[0]
                elif ri >= 2 and dy > 0.3 and dx > -0.5:
                    c = pal[2]
                else:
                    c = pal[1]
                if y == tops[x] and w >= 6:
                    c = pal[0]
                pr = ri * 0.45 if ri >= 1.6 else 0.6
                if d < pr:
                    c = PUPIL
                self.put(x, y, c)
        if w >= 5:
            gx_, gy_ = int(math.floor(icx - ri * 0.45)), int(math.floor(icy - ri * 0.45))
            if (gx_, gy_) in sclera:
                self.put(gx_, gy_, GLINT)
        # Lids: a lash line, the upper lid's skin with its crease, the wet lower rim.
        if self.level >= 1:
            # Pass 1: the lids take their tone from the flesh round them (no pale-pink frame round every eye).
            for x in xs_:
                t, b = tops[x], bots[x]
                self.put(x, t - 1, LASH)
                lid = 0 if w < 6 else (1 if w < 10 else 2)
                for k in range(lid):
                    loc = self.local(x, t - 2 - k)
                    self.flesh(x, t - 2 - k, min(loc + (2 if k == lid - 1 else 1), 6))
                if w >= 8:
                    self.flesh(x, t - 2 - lid, max(self.local(x, t - 2 - lid) - 2, 0))
                if w >= 7:
                    self.put(x, b + 1, GUM[2] if x not in (x0, x1) else GUM[1])
                    self.flesh(x, b + 2, max(self.local(x, b + 2) - 1, 1))
                else:
                    self.flesh(x, b + 1, max(self.local(x, b + 1) - 1, 1))
            if w >= 9:
                self.put(x0 - 1, tops[x0] - 1, LASH)
                self.put(x1 + 1, tops[x1] - 2, LASH)
            return
        for x in xs_:
            t, b = tops[x], bots[x]
            self.put(x, t - 1, LASH)
            lid = 1 if w < 8 else (2 if w < 13 else 3)
            for k in range(lid):
                self.flesh(x, t - 2 - k, 5 if k == 0 else 7)
            if w >= 8:
                self.flesh(x, t - 2 - lid, 2)
            self.flesh(x, b + 1, 7 if w >= 6 else 6)
            if w >= 9:
                self.flesh(x, b + 2, 4)
        if w >= 8:
            self.put(x0 - 1, tops[x0] - 1 + (0 if tilt >= 0 else 0), LASH)
            self.put(x1 + 1, tops[x1] - 1, LASH)

    # -- mouths
    def mouth(self, cx, cy, w, h, seed, heart=False, orient='h', jag=1.0, teeth=(True, True)):
        rng = random.Random(seed)
        if orient == 'v':
            w, h = h, w
        pad = 4
        LW, LH = int(w) + 2 * pad, int(h) + 2 * pad
        ys, xs = np.mgrid[0:LH, 0:LW].astype(float) + 0.5
        u = (xs - LW / 2) / (w / 2)
        v = (ys - LH / 2) / (h / 2)
        th = np.arctan2(v, u)
        a2, p2 = rng.uniform(0.03, 0.09) * jag, rng.uniform(0, 6.28)
        a3, p3 = rng.uniform(0.02, 0.08) * jag, rng.uniform(0, 6.28)
        a5, p5 = rng.uniform(0.0, 0.05) * jag, rng.uniform(0, 6.28)
        if self.level >= 1 and w < 24:
            a2, a3, a5 = a2 * 1.6, a3 * 1.6, a5 * 2
        R = 1 + a2 * np.cos(2 * th + p2) + a3 * np.cos(3 * th + p3) + a5 * np.cos(5 * th + p5)
        # The top is flatter, the bottom sags.
        vv = np.where(v < 0, v * 1.08, v * 0.94)
        if self.level >= 1 and w < 24:
            # Pass 1: small mouths are torn lenses, pointed at the corners, not boxes.
            vv = vv / np.clip(1 - u * u * 0.55, 0.2, 1)
        m = np.sqrt(u * u + vv * vv) < R
        m = ndimage.binary_opening(m, iterations=1) if w > 8 else m
        out = np.zeros((LH, LW, 4), np.uint8)
        cols = np.where(m.any(axis=0))[0]
        g = 1 if h < 10 else (2 if h < 24 else 3)
        top = {x: np.where(m[:, x])[0].min() for x in cols}
        bot = {x: np.where(m[:, x])[0].max() for x in cols}

        def P(x, y, c):
            if 0 <= x < LW and 0 <= y < LH and m[y, x]:
                out[y, x, :3] = c
                out[y, x, 3] = 255

        # Throat: black, with deep red near the lower gum (it catches a little light) and under the upper one.
        for x in cols:
            for y in range(top[x], bot[x] + 1):
                if not m[y, x]:
                    continue
                c = THROAT[0]
                if bot[x] - g - 2 <= y < bot[x] - g + 1 and h >= 10:
                    c = THROAT[2]
                elif bot[x] - g - 4 <= y and h >= 16:
                    c = THROAT[1]
                elif y <= top[x] + g + 1 and h >= 10:
                    c = THROAT[1]
                P(x, y, c)
        if heart:
            hs = (heart_sprite() if self.level < 1 else heart_v2(scale=1.2 if self.level >= 3 else 1.0, simple=self.level >= 2)
                  if self.level < 4 else heart_v3(1.15, v5=self.level >= 5, v6=self.level >= 6))
            hh, hw_ = hs.shape[:2]
            ox = LW // 2 - hw_ // 2 + 1
            oy = int(LH / 2 - hh * 0.32) if self.level < 1 else int(LH / 2 - hh * 0.45)
            if self.level >= 4:
                oy = int(LH / 2 - hh * 0.52)
            if self.level >= 5:
                oy = int(LH / 2 - hh * 0.42)
            # a dull red glow round it so it reads against the throat
            glow = ndimage.binary_dilation(hs[:, :, 3] > 0, iterations=2)
            for yy in range(hh):
                for xx in range(hw_):
                    if glow[yy, xx]:
                        P(ox + xx, oy + yy, THROAT[2] if hs[yy, xx, 3] == 0 else tuple(hs[yy, xx, :3]))
            gl2 = ndimage.binary_dilation(glow, iterations=1) & ~glow
            for yy in range(hh):
                for xx in range(hw_):
                    if gl2[yy, xx]:
                        P(ox + xx, oy + yy, THROAT[1])
        # Gums, following the opening.
        c0, c1 = cols.min(), cols.max()
        for x in cols:
            if self.level >= 1:
                # Pass 1: no gum in the corners (that made a pink ring, like lips): the corners stay black.
                f_ = (x - c0) / max(1, c1 - c0)
                if f_ < 0.12 or f_ > 0.88:
                    continue
                for k in range(g):
                    P(x, top[x] + k, GUM[0] if k == 0 else GUM[1])
                    P(x, bot[x] - k, GUM[0] if k == 0 else (GUM[2] if k == g - 1 else GUM[1]))
                continue
            for k in range(g):
                yt, yb = top[x] + k, bot[x] - k
                P(x, yt, GUM[0] if k == 0 else (GUM[1] if k < g - 1 else GUM[2]))
                P(x, yb, GUM[3] if k == g - 1 else (GUM[2] if k > 0 else GUM[1]))
        # Teeth: uneven widths, uneven lengths, some crooked, some missing, yellowed at the roots.
        teeth_px = []
        widths = [3, 4, 4, 5, 3, 4] if w >= 36 else ([2, 3, 3, 4, 3] if w >= 16 else [2, 2, 3])
        for upper in (True, False):
            if not teeth[0 if upper else 1]:
                continue
            x = cols.min() + (1 if w < 16 else 2)
            while x < cols.max() - 1:
                tw = rng.choice(widths)
                xc = min(x + tw // 2, cols.max())
                if xc not in top:
                    x += 1
                    continue
                span = bot[xc] - top[xc] + 1 - 2 * g
                if span < 3:
                    x += tw + 1
                    continue
                missing = rng.random() < 0.10 and w >= 16
                tl = max(2, min(int(span * rng.uniform(0.24, 0.40)), span // 2 - (1 if h < 12 else 0)))
                if heart and not upper:
                    tl = max(2, int(tl * 0.8))
                if heart and self.level >= 4:
                    # Pass 4: the teeth part over the heart (short and broken in the middle), so it is framed.
                    fx = (xc - cols.min()) / max(1, cols.max() - cols.min())
                    lo_, hi_ = (0.36, 0.64) if self.level < 6 else (0.30, 0.68)
                    if lo_ < fx < hi_:
                        tl = max(2, int(tl * (0.45 if upper else 0.7) if self.level < 6 else tl * (0.3 if upper else 0.7)))
                lean = rng.choice((0, 0, 0, -1, 1))
                base_c = rng.choice((3, 3, 2, 3, 2))
                stain = rng.random() < 0.3
                cur = []
                if not missing:
                    teeth_px.append((upper, cur))
                    for i in range(tw):
                        xx = x + i
                        if xx not in top:
                            continue
                        for k in range(tl):
                            tip = k == tl - 1
                            if tip and tw >= 3 and (i == 0 or i == tw - 1):
                                continue
                            shift = lean if k >= tl // 2 else 0
                            yy = (top[xx] + g + k) if upper else (bot[xx] - g - k)
                            c = TOOTH[base_c]
                            if i == tw - 1:
                                c = TOOTH[1]
                            elif i == 0 and tw >= 3 and not upper:
                                c = TOOTH[4]
                            elif i == 0 and tw >= 3 and upper and k > 0:
                                c = TOOTH[min(base_c + 1, 4)]
                            if k == 0:
                                c = TOOTH[1] if upper else TOOTH[2]
                            if tip and stain:
                                c = TOOTH[0]
                            P(xx + shift, yy, c)
                            if 0 <= xx + shift < LW and 0 <= yy < LH and m[yy, xx + shift]:
                                cur.append((xx + shift, yy, c))
                x += tw + 1
        if orient == 'v':
            out = np.rot90(out, 1)
            m = np.rot90(m, 1)
            LW, LH = LH, LW
        ox, oy = int(round(cx - LW / 2)), int(round(cy - LH / 2))
        # The flesh round the opening: in shadow above (it overhangs), lit on the lower edge. No lips.
        ring1 = ndimage.binary_dilation(m, iterations=1) & ~m
        ring2 = ndimage.binary_dilation(m, iterations=2) & ~m & ~ring1
        for yy in range(LH):
            for xx in range(LW):
                X, Y = ox + xx, oy + yy
                above = yy < LH / 2
                if ring1[yy, xx]:
                    self.set_idx(X, Y, 0 if above else 2)
                elif ring2[yy, xx]:
                    self.shade(X, Y, -2 if above else -1)
        for yy in range(LH):
            for xx in range(LW):
                if out[yy, xx, 3]:
                    self.put(ox + xx, oy + yy, tuple(out[yy, xx, :3]))
        self.mouth_masks = getattr(self, 'mouth_masks', [])
        self.mouth_masks.append((ox, oy, m))
        if self.rec is not None:
            self.rec['mouths'].append(dict(cx=cx, cy=cy, w=w, h=h, ox=ox, oy=oy, mask=m.copy(), heart=heart,
                                           orient=orient,
                                           teeth=[(up, [(ox + a, oy + b, c) for (a, b, c) in px]) for up, px in teeth_px]))

    # -- the finished image
    def render(self, bg=True):
        img = np.zeros((self.h, self.w, 3), np.uint8)
        img[:] = BG
        if self.level >= 4 and bg:
            img = hotel_backdrop()
        fl = np.array(FLESH, np.uint8)
        dd = np.array(DEAD if self.level < 3 else (DEAD3 if self.level < 5 else DEAD5), np.uint8)
        ok = self.idx >= 0
        ci = np.clip(self.idx, 0, 9)
        col = np.where((self.ramp == 1)[..., None], dd[ci], fl[ci])
        img = np.where(ok[..., None], col, img)
        img = np.where((self.warm & ~self.featmask)[..., None], np.array(WARM, np.uint8), img)
        a = self.over[..., 3:] > 0
        img = np.where(a, self.over[..., :3], img)
        return img


SKY = [hx('#070912'), hx('#0c1020'), hx('#121a30')]
HOTEL = {'paper': hx('#6e5e44'), 'paper2': hx('#5e4f38'), 'paper_d': hx('#3a3024'), 'skirt': hx('#24160e'),
         'door': hx('#2c1c12'), 'door_d': hx('#1a100a'), 'frame': hx('#3c2818'), 'quartz': hx('#8a8680'),
         'quartz_d': hx('#5a5752'), 'conc': hx('#3e4044'), 'conc_d': hx('#2a2b2e'), 'glow': hx('#7a5230'),
         'star': hx('#c8cce0')}


def hotel_backdrop():
    """What shows round and behind the devil, darker than it: the night sky over the torn roof, the broken edge of
    the roof and ceiling slabs (y 32-64, i.e. blocks 4-5 above the floor), and the far wall of the torn rooms
    (the bottom three blocks: striped wallpaper, a skirting board, a door), lit warm and low by a lamp."""
    img = np.zeros((H, W, 3), np.uint8)
    for y in range(H):
        img[y, :] = SKY[0] if y < 12 else (SKY[1] if y < 24 else SKY[2])
    rng = random.Random(4)
    for _ in range(26):
        x, y = rng.randrange(W), rng.randrange(28)
        img[y, x] = HOTEL['star']
    # The slabs: torn into a jagged edge, roof (concrete) over ceiling (quartz).
    for x in range(W):
        jag = int(3 * math.sin(x * 0.37) + 2 * math.sin(x * 0.91 + 1) + (4 if (x // 9) % 3 == 0 else 0))
        top = 32 + jag
        for y in range(max(0, top), 64):
            c = HOTEL['conc'] if y < 48 else HOTEL['quartz']
            if y == top or y == 48:
                c = HOTEL['conc_d'] if y < 48 else HOTEL['quartz_d']
            if (x % 16 == 0) and y > top:
                c = HOTEL['conc_d'] if y < 48 else HOTEL['quartz_d']
            img[y, x] = c
        # The far wall of the rooms.
        for y in range(64, H):
            if y >= 100:
                c = HOTEL['skirt']
            else:
                c = HOTEL['paper2'] if (x // 16) % 3 == 0 else HOTEL['paper']
                if x % 16 == 0:
                    c = HOTEL['paper_d']
            img[y, x] = c
    # A door (one block wide, two tall) and its frame, and the slab line over the wall.
    for y in range(80, H):
        for x in range(28, 46):
            img[y, x] = HOTEL['frame'] if x in (28, 45) or y == 80 else (HOTEL['door'] if (x - 29) % 8 else HOTEL['door_d'])
    img[64, :] = HOTEL['quartz_d']
    # The lamp's warm glow: one band of light along the top of the skirting.
    img[99, :] = HOTEL['glow']
    return img


def focal(p):
    """Pass 6: the light falls off away from the great mouth (one step at the far edges), so the eye goes there."""
    f = p.comp.get('focus')
    if not f:
        return
    d = np.hypot((p.xs - f[0]) / 1.25, p.ys - f[1])
    drop = (d > 70).astype(np.int16) + (d > 105).astype(np.int16)
    ok = p.idx > 1
    p.idx = np.where(ok, np.maximum(p.idx - drop, 1), p.idx).astype(np.int16)


def paint(comp, level=0, rec=None):
    p = Painter(comp, level)
    p.rec = rec
    p.lobes()
    p.dead_skin()
    if level >= 6:
        focal(p)
    p.cleanup()
    p.folds()
    p.pocks()
    p.strands()
    for mo in comp.get('mouths', ()):
        p.mouth(**mo)
    for e in comp.get('eyes', ()):
        cx, cy, w = e[:3]
        kw = e[3] if len(e) > 3 else {}
        p.eye(cx, cy, w, **kw)
    p.veins()
    return p


# ---- The three compositions. Coordinates are texels on the 176 x 112 front (x right, y down, the floor at 112).
# A lobe: 'base' is how far forward it sits, 'rz' how much it bulges; 'body' ellipses make its silhouette,
# 'bumps' raise lumps on it (negative ones are folds).

def E(cx, cy, w, **kw):
    return (cx, cy, w, kw)


COMP_A = dict(
    name='A  the mound',
    lobes=[
        dict(name='back', base=0, rz=18, body=[(88, 66, 90, 46), (26, 40, 30, 30), (72, 22, 36, 24), (126, 24, 34, 26),
                                                (162, 46, 22, 34), (8, 70, 14, 40)],
             bumps=[(48, 30, 10, 8, 0.25), (100, 18, 12, 7, 0.2), (150, 34, 9, 9, 0.25)]),
        dict(name='left', base=20, rz=26, body=[(30, 70, 28, 32), (16, 98, 18, 14), (46, 94, 12, 10, 0.6)],
             bumps=[(24, 58, 9, 7, 0.25), (40, 80, 7, 6, 0.2)]),
        dict(name='right', base=18, rz=26, body=[(150, 68, 26, 34), (162, 98, 16, 14), (134, 94, 10, 9, 0.6)],
             bumps=[(156, 56, 8, 7, 0.25)]),
        dict(name='top', base=24, rz=22, body=[(104, 28, 32, 20), (116, 44, 16, 12, 0.8)],
             bumps=[(96, 22, 9, 6, 0.25), (118, 30, 7, 6, 0.2)]),
        dict(name='front', base=34, rz=30, body=[(86, 88, 38, 24), (62, 102, 20, 11), (112, 104, 20, 9)],
             bumps=[(70, 72, 9, 6, 0.2)]),
    ],
    dead=[[(54, 40, 6, 4), (60, 44, 4, 3)], [(150, 84, 5, 4), (146, 88, 3, 3)]],
    mouths=[
        dict(cx=86, cy=90, w=46, h=28, seed=11, heart=True),
        dict(cx=136, cy=84, w=16, h=9, seed=3),
        dict(cx=42, cy=40, w=16, h=9, seed=5),
        dict(cx=134, cy=46, w=12, h=7, seed=8),
        dict(cx=46, cy=94, w=11, h=7, seed=9),
    ],
    eyes=[
        E(24, 64, 16, gaze=(0.8, 0.1), iris='brown'),
        E(114, 24, 10, gaze=(-0.5, 0.5), iris='grey'), E(126, 32, 7, gaze=(0, 1), iris='hazel'),
        E(120, 40, 5), E(104, 34, 6, gaze=(-1, 0), iris='green'), E(132, 22, 5, opn=0.6),
        E(160, 54, 11, gaze=(-0.6, 0.3), iris='hazel', opn=0.7), E(164, 74, 6, gaze=(-1, 0)), E(160, 96, 5),
        E(68, 18, 8, gaze=(0.4, 0.8), iris='dark'), E(84, 12, 4), E(146, 16, 6, gaze=(-0.4, 0.6)),
        E(12, 40, 8, gaze=(1, 0.2), iris='grey', tilt=0.15),
        E(56, 62, 5), E(64, 100, 6, opn=0.5, gaze=(0.3, 1)), E(114, 104, 5, gaze=(-1, 0)),
        E(94, 56, 7, gaze=(0, 0.6), iris='brown'), E(172, 34, 4), E(14, 100, 6, gaze=(0.6, -0.5)),
    ],
    veins=[[(60, 50), (66, 56), (70, 64), (72, 72)], [(66, 56), (60, 60)], [(104, 60), (110, 66), (122, 70)],
           [(36, 52), (42, 58), (44, 66)], [(140, 34), (146, 42), (150, 48)]],
    folds=[[(70, 80), (64, 78), (58, 74)], [(104, 78), (110, 76), (116, 72)], [(80, 70), (88, 69), (96, 70)],
           [(14, 80), (20, 84), (30, 86)], [(140, 60), (146, 64)], [(92, 40), (100, 42), (108, 41)]],
    strands=[],
)

COMP_B = dict(
    name='B  the avalanche',
    lobes=[
        dict(name='back', base=0, rz=18, body=[(36, 30, 44, 32), (88, 48, 54, 36), (138, 74, 46, 34), (100, 100, 86, 14),
                                                (14, 62, 18, 48), (166, 30, 16, 30)],
             bumps=[(60, 24, 10, 8, 0.25), (120, 46, 10, 8, 0.2)]),
        dict(name='hang', base=16, rz=28, body=[(34, 26, 34, 26), (18, 52, 16, 16), (50, 48, 14, 12, 0.8), (36, 62, 8, 8, 0.5)],
             bumps=[(26, 18, 10, 7, 0.25), (44, 34, 8, 6, 0.2)]),
        dict(name='wallR', base=14, rz=22, body=[(166, 44, 20, 34), (170, 80, 12, 16)],
             bumps=[(162, 34, 6, 7, 0.25)]),
        dict(name='mid', base=20, rz=26, body=[(84, 50, 30, 22), (98, 66, 16, 10, 0.7), (70, 64, 10, 8, 0.5)],
             bumps=[(78, 42, 8, 6, 0.25), (94, 52, 6, 5, 0.15)]),
        dict(name='maw', base=30, rz=32, body=[(116, 84, 40, 26), (146, 98, 20, 13), (92, 100, 20, 11)],
             bumps=[(104, 66, 9, 6, 0.2), (138, 72, 8, 6, 0.2)]),
        dict(name='spill', base=36, rz=10, body=[(36, 104, 36, 9), (64, 109, 20, 5)],
             bumps=[(30, 100, 8, 4, 0.2)]),
    ],
    dead=[[(64, 34, 5, 4), (70, 38, 4, 3), (60, 38, 3, 2)], [(132, 58, 4, 3), (138, 60, 3, 3)], [(42, 104, 6, 2)]],
    mouths=[
        dict(cx=114, cy=88, w=50, h=30, seed=21, heart=True),
        dict(cx=52, cy=50, w=13, h=8, seed=4),
        dict(cx=66, cy=24, w=14, h=8, seed=6),
        dict(cx=166, cy=58, w=9, h=14, seed=7, orient='v'),
        dict(cx=24, cy=104, w=12, h=6, seed=12),
    ],
    eyes=[
        E(24, 26, 14, gaze=(0.7, 0.5), iris='brown'), E(44, 20, 6, gaze=(0.3, 0.9), iris='grey'),
        E(50, 30, 5, gaze=(-0.4, 0.8)), E(40, 32, 4),
        E(16, 52, 7, gaze=(1, 0.2), iris='hazel'),
        E(82, 46, 12, gaze=(0.3, 0.9), iris='green'), E(100, 38, 5, gaze=(-1, 0.3)), E(70, 62, 5, iris='dark'),
        E(118, 22, 5), E(136, 32, 8, gaze=(-0.5, 0.8), iris='brown'), E(108, 30, 4), E(150, 48, 6, gaze=(-1, 0.4)),
        E(144, 70, 10, gaze=(-0.7, 0.5), iris='hazel'), E(152, 96, 5, gaze=(-1, 0)), E(160, 90, 4),
        E(92, 102, 6, gaze=(0.5, -0.8), iris='brown'),
        E(166, 30, 8, gaze=(-0.8, 0.6), iris='grey'), E(170, 84, 5),
        E(50, 104, 5, gaze=(0.5, -1)), E(64, 108, 4), E(8, 82, 6, gaze=(1, -0.2)),
    ],
    veins=[[(60, 28), (64, 33), (70, 32)], [(108, 40), (114, 46), (116, 54)], [(120, 64), (112, 70), (104, 72)],
           [(10, 32), (14, 38), (12, 44)], [(150, 64), (158, 70)]],
    folds=[[(86, 84), (80, 82), (74, 78)], [(142, 90), (148, 88), (154, 84)], [(104, 70), (112, 68), (122, 69)],
           [(70, 54), (78, 58), (90, 58)], [(10, 30), (16, 36), (24, 38)], [(126, 104), (134, 106)]],
    strands=[([(42, 66), (50, 80), (58, 96)], 2), ([(28, 62), (30, 80), (32, 98)], 1), ([(56, 60), (66, 74)], 1)],
)

COMP_C = dict(
    name='C  the hanging',
    lobes=[
        dict(name='ceiling', base=4, rz=20, body=[(88, 4, 96, 18), (30, 12, 32, 16), (148, 12, 34, 16), (88, 20, 30, 10)],
             bumps=[(60, 10, 10, 6, 0.2), (118, 8, 10, 6, 0.2)]),
        dict(name='left', base=16, rz=24, body=[(36, 36, 22, 28), (30, 64, 15, 15), (40, 80, 7, 9, 0.6)],
             bumps=[(30, 28, 7, 6, 0.25)]),
        dict(name='right', base=14, rz=24, body=[(142, 40, 24, 30), (150, 70, 13, 14), (136, 82, 6, 8, 0.6)],
             bumps=[(148, 30, 7, 6, 0.25)]),
        dict(name='pendant', base=24, rz=30, body=[(88, 36, 22, 24), (90, 66, 30, 26), (86, 92, 20, 12)],
             bumps=[(80, 30, 7, 6, 0.2)]),
        dict(name='pile', base=30, rz=12, body=[(88, 108, 60, 9), (22, 109, 22, 6), (156, 109, 22, 6)],
             bumps=[(60, 104, 8, 3, 0.2), (120, 104, 8, 3, 0.2)]),
    ],
    dead=[[(40, 22, 5, 4), (44, 26, 3, 3)], [(146, 56, 4, 4)], [(100, 104, 6, 2)]],
    mouths=[
        dict(cx=90, cy=66, w=46, h=26, seed=31, heart=True, orient='v'),
        dict(cx=40, cy=44, w=14, h=8, seed=13),
        dict(cx=140, cy=50, w=16, h=9, seed=14),
        dict(cx=60, cy=12, w=12, h=6, seed=15),
        dict(cx=150, cy=106, w=12, h=6, seed=16),
    ],
    eyes=[
        E(28, 64, 12, gaze=(0.6, 0.6), iris='brown'), E(42, 76, 5, gaze=(0, 1)), E(26, 30, 7, gaze=(0.6, 0.6), iris='grey'),
        E(152, 70, 10, gaze=(-0.6, 0.6), iris='hazel', opn=0.7), E(138, 80, 5), E(156, 30, 7, gaze=(-0.7, 0.5)),
        E(74, 28, 8, gaze=(0.6, 0.8), iris='green'), E(84, 20, 4), E(98, 32, 5, gaze=(-0.8, 0.3), iris='dark'),
        E(112, 12, 7, gaze=(-0.3, 0.9)), E(126, 18, 5), E(20, 14, 6, opn=0.6), E(166, 16, 5),
        E(72, 50, 5, gaze=(1, 0)), E(108, 52, 6, gaze=(-1, 0.4)), E(70, 90, 6, gaze=(0.6, -0.5), iris='brown'),
        E(112, 104, 5), E(40, 108, 4), E(130, 56, 4),
    ],
    veins=[[(84, 40), (88, 46), (86, 52)], [(40, 52), (36, 58)], [(140, 58), (146, 62)], [(70, 6), (78, 12), (88, 12)]],
    folds=[[(76, 44), (82, 42)], [(100, 44), (106, 46)], [(30, 50), (38, 54)], [(144, 34), (152, 38)]],
    strands=[([(30, 78), (28, 92), (26, 104)], 2), ([(150, 84), (152, 96), (154, 104)], 1), ([(40, 88), (44, 104)], 1),
             ([(58, 40), (66, 44)], 1), ([(112, 46), (120, 42)], 1)],
)

COMPS = {'A': COMP_A, 'B': COMP_B, 'C': COMP_C}


# ---- The refining passes, on composition B. Each pass edits a copy of the data and turns on its painter changes
# (Painter.level), so every pass can be painted again exactly.

def lobe(c, name):
    return next(L for L in c['lobes'] if L['name'] == name)


def mouth_at(c, cx):
    return next(m for m in c['mouths'] if m['cx'] == cx)


def pass1(c):
    """Lumpier, saggier flesh; a bigger great mouth so the heart has room; thicker sinews."""
    mouth_at(c, 114).update(w=52, h=34)
    lobe(c, 'hang')['bumps'] += [(14, 34, 6, 5, 0.15), (30, 42, 7, 5, 0.15), (52, 14, 6, 5, 0.2), (20, 62, 5, 4, 0.15)]
    lobe(c, 'hang')['body'] += [(58, 58, 5, 7, 0.5)]
    lobe(c, 'mid')['bumps'] += [(66, 48, 5, 4, 0.2), (104, 56, 5, 4, 0.15), (90, 64, 6, 4, 0.15)]
    lobe(c, 'mid')['body'] += [(110, 72, 5, 6, 0.5)]
    lobe(c, 'maw')['bumps'] += [(88, 80, 6, 5, 0.2), (152, 84, 6, 5, 0.2), (128, 107, 7, 4, 0.15), (100, 108, 6, 3, 0.15)]
    lobe(c, 'maw')['body'] += [(78, 106, 6, 6, 0.6)]
    lobe(c, 'back')['bumps'] += [(100, 26, 8, 5, 0.2), (152, 62, 7, 5, 0.2), (16, 90, 6, 5, 0.2), (160, 100, 6, 4, 0.15)]
    lobe(c, 'wallR')['bumps'] += [(172, 62, 4, 6, 0.2)]
    c['strands'] = [([(44, 66), (48, 82), (58, 97)], 3), ([(28, 64), (29, 80), (32, 98)], 2), ([(56, 62), (64, 76), (70, 92)], 1)]
    c['folds'] += [[(118, 70), (126, 72)], [(30, 46), (38, 48)], [(160, 104), (168, 102)]]


def pass2(c):
    """Break the pile of round heads: saggier, flatter lobes; eyes off the lobes' centres and peering out of the
    crevices; a real spread of eye sizes (two great ones, clusters of small ones), so the small ones read as eyes."""
    lobe(c, 'hang')['body'] = [(34, 22, 34, 20), (28, 44, 22, 15), (16, 56, 12, 12), (50, 50, 10, 10, 0.8),
                               (38, 64, 7, 8, 0.5), (58, 58, 5, 7, 0.5)]
    lobe(c, 'mid')['body'] = [(88, 52, 32, 17), (106, 64, 16, 10, 0.7), (72, 63, 10, 8, 0.5), (112, 72, 5, 6, 0.5)]
    lobe(c, 'mid')['bumps'] = [(78, 46, 8, 5, 0.25), (96, 50, 6, 4, 0.15), (66, 50, 5, 4, 0.2), (104, 58, 5, 4, 0.15)]
    c['folds'] += [[(12, 40), (22, 44), (34, 46), (44, 43)], [(70, 58), (80, 61), (92, 61)], [(100, 96), (106, 99)]]
    c['eyes'] = [
        # the two great eyes
        E(19, 36, 15, gaze=(0.8, 0.5), iris='brown'),
        E(154, 92, 13, gaze=(-0.8, -0.2), iris='hazel'),
        # medium, alone
        E(98, 44, 10, gaze=(-0.4, 0.8), iris='green', opn=0.75),
        E(166, 36, 10, gaze=(-0.9, 0.5), iris='grey'),
        E(128, 26, 9, gaze=(-0.3, 0.9), iris='dark'),
        E(64, 67, 10, gaze=(0.7, 0.2), iris='brown', on='back'),
        # a cluster of three by the great mouth, looking different ways
        E(90, 70, 7, gaze=(0.9, 0.4), iris='grey'), E(98, 64, 6, gaze=(-0.5, 0.9)), E(84, 63, 5, gaze=(0, -1)),
        # a crop of small ones on the back, high right
        E(140, 42, 6, gaze=(-0.6, 0.6), iris='hazel'), E(147, 37, 5, gaze=(0.5, 0.5)), E(150, 46, 4),
        E(143, 50, 5, gaze=(-1, 0)), E(135, 49, 4),
        # and on top of the hanging lobe
        E(46, 14, 6, gaze=(0.4, 0.9), iris='grey'), E(53, 21, 5, gaze=(-0.6, 0.6)), E(41, 23, 4),
        # alone
        E(80, 22, 7, gaze=(-0.8, 0.3), iris='brown'), E(8, 82, 7, gaze=(1, -0.2), iris='hazel'),
        E(52, 104, 6, gaze=(0.5, -1)), E(172, 80, 5, gaze=(-1, 0)),
    ]


def pass3(c):
    """Wet, pitted, wrinkled skin; eyes big enough to read on a phone; a bigger maw and heart; dead skin that reads
    as dead skin (paler grey, cracked), not dirt."""
    mouth_at(c, 114).update(w=58, h=38, cy=86)
    lobe(c, 'maw')['body'][0] = (116, 82, 42, 28)
    # eye sizes up: nothing under 5 except two pin-eyes in a cluster
    resize = {(140, 42): 8, (147, 37): 6, (150, 46): 5, (143, 50): 6, (135, 49): 5, (46, 14): 8, (53, 21): 6,
              (41, 23): 5, (80, 22): 8, (52, 104): 7, (172, 80): 6, (84, 63): 6, (98, 64): 7, (90, 70): 8}
    eyes = []
    for (x, y, w, kw) in c['eyes']:
        eyes.append((x, y, resize.get((x, y), w), kw))
    c['eyes'] = eyes
    # move the cluster by the maw off the bigger mouth
    c['eyes'] = [e if (e[0], e[1]) not in ((90, 70), (98, 64), (84, 63)) else
                 {(90, 70): (80, 72, 8, dict(gaze=(0.9, 0.4), iris='grey')),
                  (98, 64): (90, 62, 7, dict(gaze=(-0.5, 0.9))),
                  (84, 63): (77, 61, 6, dict(gaze=(0, -1)))}[(e[0], e[1])] for e in c['eyes']]
    c['eyes'] += [E(156, 48, 4), E(132, 40, 4)]
    c['dead'] = [[(58, 30, 7, 5), (64, 35, 5, 4), (52, 34, 4, 3)], [(140, 104, 6, 4), (146, 100, 4, 3)],
                 [(118, 40, 5, 4), (123, 44, 4, 3)], [(30, 104, 6, 3)]]
    c['folds'] += [[(54, 31), (58, 33), (62, 32)], [(56, 36), (61, 38), (66, 37)], [(116, 42), (121, 43)],
                   [(138, 103), (143, 104)], [(10, 64), (16, 66), (22, 65)], [(128, 50), (134, 54)],
                   [(146, 106), (152, 108)], [(160, 64), (166, 68)], [(82, 102), (88, 104)]]
    c['pocks'] = [(26, 12), (30, 15), (24, 17), (33, 11), (100, 30), (104, 33), (108, 28), (96, 34),
                  (124, 100), (128, 103), (132, 99), (120, 104), (6, 50), (9, 56), (5, 60),
                  (160, 20), (164, 24), (70, 44), (74, 47), (66, 46), (142, 66), (146, 70), (138, 70)]


def pass4(c):
    """The heart reads at a glance (the arch and its branches, framed by teeth that part over it); bigger necrotic
    areas so the dead skin reads as a choice, not dirt; the torn hotel behind so the devil has scale and presses
    out through broken slabs."""
    mouth_at(c, 114).update(w=58, h=42, cy=85)
    c['dead'] = [[(58, 28, 9, 7), (66, 34, 6, 5), (50, 33, 5, 4)], [(166, 26, 9, 12), (170, 40, 6, 6)],
                 [(140, 104, 7, 4), (148, 100, 5, 3)], [(118, 40, 5, 4), (123, 44, 4, 3)]]
    c['folds'] += [[(162, 22), (168, 26), (172, 25)], [(160, 32), (166, 34)], [(48, 28), (54, 30)]]


def pass5(c):
    """The heart's arch leads (the pulmonary trunk dark and short); dead skin pinker; the spill along the floor
    stops being one flat sausage: it breaks into fingers of flesh reaching forward, each lumpy; the green eye moves
    off the middle lobe's centre (it was making that lobe a one-eyed head)."""
    lobe(c, 'spill')['body'] = [(30, 104, 26, 8), (60, 107, 14, 6), (8, 106, 10, 7), (80, 109, 9, 4, 0.8),
                                (44, 98, 8, 5, 0.6)]
    lobe(c, 'spill')['bumps'] = [(22, 101, 6, 4, 0.25), (36, 102, 5, 3, 0.2), (58, 105, 5, 3, 0.25), (10, 104, 4, 3, 0.2)]
    c['folds'] += [[(16, 106), (22, 107)], [(46, 106), (52, 107)], [(66, 109), (72, 109)]]
    c['eyes'] = [e if (e[0], e[1]) != (98, 44) else (108, 44, 10, dict(gaze=(-0.6, 0.7), iris='green', opn=0.75))
                 for e in c['eyes']]
    c['pocks'] += [(26, 106), (30, 108), (34, 105), (62, 110)]


def pass6(c):
    """The heart's arch shows (the teeth part wider over it; the pulmonary trunk is a stump, not a stalk); the eye
    in the dead skin is dead too (milky); the light falls off toward the edges so the great mouth leads."""
    c['eyes'] = [e if (e[0], e[1]) != (166, 36) else (166, 36, 10, dict(gaze=(-0.9, 0.5), iris='milky', opn=0.8))
                 for e in c['eyes']]
    c['focus'] = (114, 84)


PASSES = [pass1, pass2, pass3, pass4, pass5, pass6]


def comp_pass(n):
    import copy
    c = copy.deepcopy(COMP_B)
    for f in PASSES[:n]:
        f(c)
    return c


def font(size):
    for f in ('C:/Windows/Fonts/arialbd.ttf', 'C:/Windows/Fonts/arial.ttf'):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def save_scaled(arr, name, s):
    im = Image.fromarray(arr).resize((arr.shape[1] * s, arr.shape[0] * s), Image.NEAREST)
    im.save(os.path.join(OUT, name))
    return im


def comps():
    tiles = []
    for k, c in COMPS.items():
        img = paint(c).render()
        save_scaled(img, 'comp_%s.png' % k, 4)
        tiles.append((c['name'], img))
    s = 3
    sheet = Image.new('RGB', (W * s * 3 + 40, H * s + 50), (20, 18, 22))
    d = ImageDraw.Draw(sheet)
    for i, (nm, img) in enumerate(tiles):
        sheet.paste(Image.fromarray(img).resize((W * s, H * s), Image.NEAREST), (10 + i * (W * s + 10), 40))
        d.text((10 + i * (W * s + 10), 10), nm, fill=(230, 224, 216), font=font(22))
    sheet.save(os.path.join(OUT, 'comps.png'))


# ---- The final concept, presented: front at 6x, the mouth and heart close, a side view, the cuboid plan, and a mock
# game frame (a 1080 x 1920 portrait of the torn-open floor 8, ray-cast with Minecraft's own block textures).

FINAL = None


def final_painter():
    global FINAL
    if FINAL is None:
        n = len(PASSES)
        FINAL = paint(comp_pass(n), level=n)
    return FINAL


# -- the side view: the same lobes seen from the player's left, depth to the right (toward the player).
# z is texels in front of the devil's back plane (the far end of the arena); 1 block = 16.

SIDE = dict(
    name='side',
    lobes=[
        dict(name='back', base=0, rz=14, body=[(8, 62, 16, 52), (16, 16, 14, 14), (8, 100, 14, 14), (22, 40, 8, 10, 0.6)],
             bumps=[(12, 40, 6, 6, 0.2), (10, 84, 6, 6, 0.2)]),
        dict(name='maw', base=4, rz=16, body=[(42, 84, 22, 24), (56, 102, 10, 10, 0.7), (30, 74, 10, 10, 0.6)],
             bumps=[(52, 70, 6, 5, 0.2), (40, 100, 6, 4, 0.2)]),
        dict(name='mid', base=6, rz=14, body=[(34, 50, 16, 14), (46, 60, 8, 7, 0.6)],
             bumps=[(40, 44, 5, 4, 0.2)]),
        dict(name='spill', base=8, rz=6, body=[(46, 106, 20, 6), (66, 108, 12, 4), (78, 110, 6, 3, 0.8)],
             bumps=[(58, 104, 6, 3, 0.2)]),
        dict(name='hang', base=10, rz=16, body=[(26, 22, 20, 20), (32, 44, 15, 12), (42, 56, 6, 7, 0.6)],
             bumps=[(30, 14, 7, 6, 0.25), (38, 32, 5, 4, 0.2)]),
    ],
    dead=[[(18, 26, 5, 6)]],
    eyes=[E(44, 20, 7, gaze=(1, 0.3), iris='brown'), E(46, 40, 5, gaze=(1, 0.5)), E(48, 52, 6, gaze=(1, 0.2), iris='green'),
          E(56, 70, 6, gaze=(1, 0.4), iris='grey'), E(58, 100, 5, gaze=(1, -0.3), iris='hazel'), E(26, 46, 4)],
    folds=[[(24, 30), (32, 34)], [(36, 96), (44, 98)]],
    strands=[([(36, 62), (38, 80), (36, 96)], 3)],
    veins=[],
)


def side_view():
    """The devil from the player's left, the player 11.5 blocks out from the far wall (the mock frame's numbers):
    192 texels deep (12 blocks) x 112 high, the floor slab, the line of the torn ceiling (3 blocks) and roof."""
    SW = 192
    p = Painter(SIDE, level=len(PASSES), w=SW, h=H)
    p.lobes()
    p.dead_skin()
    p.cleanup()
    p.folds()
    p.strands()
    # the maw in profile: a cavity cut into the front of the maw lobe, teeth on both jaws, the heart deep in it
    maw = [L['name'] for L in SIDE['lobes']].index('maw')
    p.clip = p.lobe == maw
    p.mouth(cx=58, cy=86, w=26, h=28, seed=21)
    p.clip = None
    hs = heart_v3(0.55)
    hh, hw_ = hs.shape[:2]
    for yy in range(hh):
        for xx in range(hw_):
            if hs[yy, xx, 3]:
                X, Y = 52 + xx - hw_ // 2, 86 + yy - hh // 2
                if 0 <= X < SW and 0 <= Y < H and p.featmask[Y, X] and tuple(p.over[Y, X, :3]) in THROAT:
                    p.put(X, Y, tuple(hs[yy, xx, :3]))
    for e in SIDE['eyes']:
        p.eye(e[0], e[1], e[2], **e[3])
    img = np.zeros((H, SW, 3), np.uint8)
    img[:] = BG
    # the hotel for scale: sky over the roof line, the torn slabs as dotted lines, the floor
    for y in range(H):
        if y < 48:
            img[y, :] = SKY[1]
    for x in range(0, SW, 3):
        img[64, x] = HOTEL['quartz_d']
        img[48, x] = HOTEL['conc']
    img[H - 1, :] = HOTEL['quartz']
    body = p.render(bg=False)
    m = (p.idx >= 0) | (p.over[..., 3] > 0)
    img = np.where(m[..., None], body, img)
    # the player: two blocks tall at 11.5 blocks, eye 1.62 up
    px = int(11.5 * 16)
    img[H - 32:H - 1, px - 4:px + 4] = (60, 90, 150)
    img[H - 32:H - 24, px - 4:px + 4] = (200, 160, 120)
    img[H - 27, px - 4] = (40, 30, 30)
    return img


def side_sheet():
    img = side_view()
    s = 5
    SWp = img.shape[1]
    im = Image.new('RGB', (SWp * s, img.shape[0] * s + 74), (16, 14, 20))
    im.paste(Image.fromarray(img).resize((SWp * s, img.shape[0] * s), Image.NEAREST), (0, 34))
    d = ImageDraw.Draw(im)
    for b in range(0, 13):
        x = b * 16 * s
        d.line([(x, 34 + H * s), (x, 34 + H * s + 12)], fill=(200, 190, 180), width=2)
        d.text((x + 4, 34 + H * s + 14), str(b), fill=(200, 190, 180), font=font(16))
    eye_y = 34 + int((H - 1.62 * 16) * s)
    d.line([(int(5.0 * 16 * s), eye_y), (int(11.3 * 16 * s), eye_y)], fill=(240, 220, 120), width=2)
    d.text((int(6.6 * 16 * s), eye_y - 24), "the player's eye line, 11.5 blocks out", fill=(240, 220, 120), font=font(17))
    d.text((int(4.6 * 16 * s), 34 + 47 * s), 'roof (torn)', fill=(150, 150, 160), font=font(15))
    d.text((int(4.6 * 16 * s), 34 + 63 * s), 'ceiling (torn)', fill=(150, 150, 160), font=font(15))
    d.text((6, 2), "Side, from the player's left (blocks out from the far wall): the back mass hugs the wall (1 block), the "
           "hanging and middle lobes bulge 2.5-3,\nthe maw 4, the spill runs 5 along the floor; the heart sits a "
           "block inside the teeth.", fill=(230, 224, 216), font=font(15))
    im.save(os.path.join(OUT, 'final_side.png'))
    return im


# -- the cuboid plan: each lobe cut into stepped bands (one box each), drawn over the front.

def cuboid_plan(p):
    boxes = []
    for k, L in enumerate(p.comp['lobes']):
        mask = p.lobe == k
        rows = np.where(mask.any(axis=1))[0]
        if len(rows) == 0:
            continue
        y0, y1 = rows.min(), rows.max()
        n = max(1, int(round((y1 - y0 + 1) / 22)))
        for b in range(n):
            a, c = y0 + (y1 - y0 + 1) * b // n, y0 + (y1 - y0 + 1) * (b + 1) // n - 1
            band = mask[a:c + 1]
            cols = np.where(band.sum(axis=0) > (c - a + 1) * 0.5)[0]
            if len(cols) == 0:
                continue
            zf = p.zf[a:c + 1][band]
            front = float(np.percentile(zf, 70))
            boxes.append(dict(lobe=L['name'], x0=int(cols.min()), x1=int(cols.max()) + 1, y0=int(a), y1=int(c) + 1,
                              z0=float(L['base']) - 6, z1=front))
    return boxes


def plan_sheet(p, img):
    boxes = cuboid_plan(p)
    s = 5
    im = Image.fromarray(img).resize((W * s, H * s), Image.NEAREST).convert('RGBA')
    ov = Image.new('RGBA', im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    pal = {'back': (120, 170, 255), 'hang': (255, 210, 90), 'wallR': (170, 255, 170), 'mid': (255, 140, 220),
           'maw': (255, 255, 255), 'spill': (120, 240, 240)}
    for b in boxes:
        c = pal.get(b['lobe'], (255, 255, 255))
        d.rectangle([b['x0'] * s, b['y0'] * s, b['x1'] * s - 1, b['y1'] * s - 1], outline=c + (255,), width=3)
    # detail boxes: eyes 6+ wide, the great mouth's teeth, the heart
    for (x, y, w, kw) in p.comp['eyes']:
        if w >= 6:
            hh = max(2, w * 0.5 * kw.get('opn', 1.0))
            d.rectangle([(x - w / 2) * s, (y - hh / 2 - 1) * s, (x + w / 2) * s, (y + hh / 2 + 1) * s],
                        outline=(255, 80, 80, 255), width=2)
    im = Image.alpha_composite(im, ov).convert('RGB')
    full = Image.new('RGB', (im.width, im.height + 40), (16, 14, 20))
    full.paste(im, (0, 40))
    ImageDraw.Draw(full).text((8, 10), 'Cuboid plan: %d stepped lobe boxes (colour = lobe; their front faces are cut out '
                              'to the painted silhouette), eye boxes in red' % len(boxes), fill=(230, 224, 216), font=font(16))
    full.save(os.path.join(OUT, 'final_cuboid_plan.png'))
    return boxes


# -- the mock game frame

JAR = os.path.expanduser('~/.gradle/caches/fabric-loom/1.21.8/minecraft-client.jar')
_TEX = {}


def tex(name, fallback=(128, 128, 128)):
    if name in _TEX:
        return _TEX[name]
    a = None
    try:
        with zipfile.ZipFile(JAR) as z:
            data = z.read('assets/minecraft/textures/block/%s.png' % name)
        a = np.array(Image.open(BytesIO(data)).convert('RGBA'))[:16, :16]
    except Exception:
        a = np.zeros((16, 16, 4), np.uint8)
        a[..., :3] = fallback
        a[..., 3] = 255
    _TEX[name] = a
    return a


def moon_tex():
    try:
        with zipfile.ZipFile(JAR) as z:
            data = z.read('assets/minecraft/textures/environment/moon_phases.png')
        im = Image.open(BytesIO(data)).convert('RGBA')
        cw = im.width // 4
        return np.array(im.crop((0, 0, cw, cw)))
    except Exception:
        a = np.zeros((32, 32, 4), np.uint8)
        a[8:24, 8:24] = (230, 230, 210, 255)
        return a


def flesh_tile():
    """A flesh block's face (the spill on the floor, the flesh climbing the wall stumps): five lumps that wrap round
    the tile's edges, shaded with the concept's own ramp, one wet glint."""
    ys, xs = np.mgrid[0:16, 0:16].astype(float) + 0.5
    hgt = np.zeros((16, 16))
    for (cx, cy, r) in ((4, 4, 5), (12, 6, 4.5), (7, 12, 5.5), (14, 14, 3.5), (1, 10, 3)):
        for ox in (-16, 0, 16):
            for oy in (-16, 0, 16):
                d2 = ((xs - cx - ox) ** 2 + (ys - cy - oy) ** 2) / (r * r)
                hgt = np.maximum(hgt, np.sqrt(np.clip(1 - d2, 0, 1)) * r)
    gy, gx = np.gradient(hgt)
    nrm = np.sqrt(gx * gx + gy * gy + 1)
    lit = (-gx * LIGHT[0] - gy * LIGHT[1] + LIGHT[2]) / nrm
    i = np.clip(np.floor((lit + 0.1) / 1.2 * 6) + 1, 1, 7).astype(int)
    i = np.where(hgt < 0.6, 1, i)
    a = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            a[y, x, :3] = FLESH[i[y, x]]
            a[y, x, 3] = 255
    a[3, 3, :3] = FLESH[9]
    a[10, 6, :3] = FLESH[8]
    return a


# block ids: (top, side, bottom)
BLOCKS = {
    1: ('quartz_block_bottom', 'quartz_block_bottom', 'quartz_block_bottom'),    # smooth quartz slab
    2: ('red_wool', 'quartz_block_bottom', 'quartz_block_bottom'),               # carpet on the slab
    3: ('brown_wool', 'quartz_block_bottom', 'quartz_block_bottom'),
    4: ('yellow_wool', 'quartz_block_bottom', 'quartz_block_bottom'),
    5: ('sandstone_top', 'sandstone_top', 'sandstone_top'),                      # smooth sandstone wallpaper
    6: ('cut_sandstone', 'cut_sandstone', 'cut_sandstone'),
    7: ('dark_oak_planks', 'dark_oak_planks', 'dark_oak_planks'),                # skirting
    8: ('light_gray_concrete', 'light_gray_concrete', 'light_gray_concrete'),    # outer wall
    9: ('gray_concrete', 'gray_concrete', 'gray_concrete'),                      # roof
    10: ('black_stained_glass', 'black_stained_glass', 'black_stained_glass'),
    11: ('redstone_lamp_on', 'redstone_lamp_on', 'redstone_lamp_on'),
    12: ('dark_oak_door_bottom', 'dark_oak_door_bottom', 'dark_oak_door_bottom'),
    13: ('dark_oak_door_top', 'dark_oak_door_top', 'dark_oak_door_top'),
    14: ('flesh', 'flesh', 'flesh'),
}


def build_world():
    """Floor 8 as the player stands in it after the devil tore out: player at x 0.5 looking +x down the corridor.
    Corridor cells z -1..1, its walls at z -2 and 2, the rooms out to z -5 and 6, the outer walls at -6 and 7. The
    floor's top is y 0; walls y 0-2; the ceiling slab y 3, the roof y 4, the parapet y 5-6."""
    off = np.array([-12, -2, -8])
    g = np.zeros((30, 10, 18), np.int8)

    def put(i, j, k, b):
        ii, jj, kk = i - off[0], j - off[1], k - off[2]
        if 0 <= ii < g.shape[0] and 0 <= jj < g.shape[1] and 0 <= kk < g.shape[2]:
            g[ii, jj, kk] = b

    for i in range(-12, 18):
        for k in range(-6, 8):
            put(i, -1, k, 1)
        put(i, -1, -1, 4 if i % 4 == 3 else 3)
        put(i, -1, 0, 2)
        put(i, -1, 1, 4 if i % 4 == 3 else 3)
        # outer walls with their windows; the top row torn away past the tear
        for k in (-6, 7):
            for j in range(0, 3):
                win = j >= 1 and (i % 6) in (2, 3, 4)
                if j == 2 and i >= 4 and (i * 7 + k) % 3 != 0:
                    continue
                put(i, j, k, 10 if win else 8)
    # the corridor walls: whole behind the player, torn into stumps ahead of them, gone where the devil is
    # the arena is torn open: whole walls only well behind the player, jagged stumps from just behind them on
    tear = {-3: 2, -2: 3, -1: 1, 0: 2, 1: 1, 2: 1, 3: 2, 4: 2, 5: 1, 6: 1, 7: 0, 8: 1}
    for i in range(-12, 18):
        hgt = 3 if i < -3 else tear.get(i, 0)
        for k in (-2, 2):
            hk = hgt if k == -2 else max(0, hgt - (1 if i in (3, 6) else 0) + (1 if i == 5 else 0))
            for j in range(0, hk):
                put(i, j, k, 7 if j == 0 else (6 if i % 3 == 0 else 5))
    # a door left standing in a torn wall (the front rooms' doors are on the z -2 side)
    put(4, 0, -2, 12)
    put(4, 1, -2, 13)
    put(-6, 0, -2, 12)
    put(-6, 1, -2, 13)
    put(-2, 0, 2, 12)
    put(-2, 1, 2, 13)
    # the walls between rooms: stumps
    for k in list(range(-5, -2)) + list(range(3, 7)):
        for j in range(0, 1 if k in (-5, 6) else 2):
            put(6, j, k, 7 if j == 0 else 5)
        put(0, 0, k, 7)
        put(0, 1, k, 5)
        put(0, 2, k, 5)
    # ceiling and roof: over the player, ripped off in a jagged edge just ahead; broken shelves left at the sides
    for i in range(-12, 18):
        for k in range(-6, 8):
            edge = -1 + (1 if k in (-1, 0, 1) else 0) + (2 if k in (-6, -5, 6, 7) else 0) + \
                (2 if k in (-6, 7) and i % 2 == 0 else 0) + (1 if k in (-4, 4, -2, 3) else 0)
            if i <= edge:
                put(i, 3, k, 1)
                if i <= edge - 1 or k in (-6, 7):
                    put(i, 4, k, 9)
            if k in (-6, 7) and i <= 1:
                put(i, 5, k, 8)
                put(i, 6, k, 8)
    put(-3, 3, 0, 11)
    put(-7, 3, 0, 11)
    # the far end beyond the devil: the corridor goes on, the end wall
    for k in range(-6, 8):
        for j in range(0, 3):
            put(17, j, k, 5)
    # flesh: spilled over the floor and climbing the wall stumps toward the player
    flesh = [(8, 0, -3), (8, 0, 2), (7, 0, 3), (7, 1, 2), (8, 1, 2), (8, 2, 2), (7, 0, -2), (7, 1, -2), (8, 1, -2),
             (6, 0, -4), (7, 0, -4), (8, 0, 4), (9, 0, 5), (9, 1, 5), (9, 0, -5), (8, 1, -5), (8, 0, -5), (6, 0, 4)]
    for (i, j, k) in flesh:
        put(i, j, k, 14)
    return g, off


def raycast(g, off, O, D, maxit=80):
    N = D.shape[0]
    Dz = np.where(D == 0, 1e-9, D)
    pos = O[None, :] - off[None, :]
    cell = np.floor(pos).astype(int).repeat(N, 0) if pos.shape[0] == 1 else np.floor(pos).astype(int)
    step = np.where(D > 0, 1, -1)
    tDelta = np.abs(1 / Dz)
    tMax = np.where(D == 0, np.inf, ((cell + (step > 0)) - pos) / Dz)
    hit = np.zeros(N, np.int8)
    thit = np.full(N, np.inf)
    axis = np.full(N, -1)
    last = np.full(N, -1)
    tcur = np.zeros(N)
    active = np.ones(N, bool)
    sh = np.array(g.shape)
    ar = np.arange(N)
    for _ in range(maxit):
        inb = np.all((cell >= 0) & (cell < sh), axis=1)
        idx = active & inb
        b = np.zeros(N, np.int8)
        b[idx] = g[cell[idx, 0], cell[idx, 1], cell[idx, 2]]
        new = idx & (b > 0)
        hit[new], thit[new], axis[new] = b[new], tcur[new], last[new]
        active &= ~new
        active &= ~(~inb & (tcur > 2))
        if not active.any():
            break
        a = np.argmin(tMax, axis=1)
        tcur = tMax[ar, a]
        cell[ar, a] += step[ar, a]
        tMax[ar, a] += tDelta[ar, a]
        last = a
    return hit, thit, axis, step


def mock_frame():
    p = final_painter()
    front = p.render(bg=False)
    has = (p.idx >= 0) | (p.over[..., 3] > 0)
    # feature pixels that spill past a silhouette (a lid, a sinew) take the depth of the nearest flesh
    _, (iy, ix) = ndimage.distance_transform_edt(p.idx < 0, return_indices=True)
    depth = np.where(p.idx >= 0, p.zf, p.zf[iy, ix] - 1.0)
    # the mouths are holes: their throats sit a block back
    for (ox, oy, m) in getattr(p, 'mouth_masks', []):
        for yy in range(m.shape[0]):
            for xx in range(m.shape[1]):
                X, Y = ox + xx, oy + yy
                if m[yy, xx] and 0 <= X < W and 0 <= Y < H:
                    c = tuple(p.over[Y, X, :3])
                    if c in THROAT or c in HEART_PAL or c in ART_PAL or c in VEN_PAL:
                        depth[Y, X] -= 12 if c in THROAT else 7
    RW, RH = 360, 640
    fov = math.radians(70)
    pitch = math.radians(7)
    yaw = math.radians(0)
    O = np.array([0.5, 1.62, 0.5])
    ys, xs = np.mgrid[0:RH, 0:RW].astype(float) + 0.5
    th = math.tan(fov / 2)
    sx = (xs / RW * 2 - 1) * th * RW / RH
    sy = (1 - ys / RH * 2) * th
    fwd = np.array([math.cos(pitch) * math.cos(yaw), math.sin(pitch), math.cos(pitch) * math.sin(yaw)])
    right = np.array([-math.sin(yaw), 0.0, math.cos(yaw)])
    up = np.cross(right, fwd)
    D = fwd[None, None] + sx[..., None] * right[None, None] + sy[..., None] * up[None, None]
    D = D / np.linalg.norm(D, axis=2, keepdims=True)
    D = D.reshape(-1, 3)
    g, off = build_world()
    hit, thit, axis, step = raycast(g, off, O, D)
    # the devil: a height field standing on its back plane
    XB = 11.5
    # In game the maw should sit on the corridor's axis (a player walking it faces the mouth): the whole devil
    # shifted about 1.6 blocks toward the front rooms.
    MAWU = mouth_at(p.comp, 114)['cx']
    ZMIN = 0.5 - MAWU / 16.0
    dhit = np.full(D.shape[0], np.inf)
    duv = np.zeros((D.shape[0], 2), int)
    fw = D[:, 0] > 0.05
    t0 = (XB - 4.4 - O[0]) / np.where(fw, D[:, 0], 1)
    t1 = (XB - O[0]) / np.where(fw, D[:, 0], 1)
    nst = 220
    alive = fw.copy()
    side = np.zeros(D.shape[0], bool)
    sidepos = np.zeros((D.shape[0], 3))
    xstep = 4.4 / nst
    for i in range(nst + 1):
        t = t0 + (t1 - t0) * i / nst
        P = O[None] + t[:, None] * D
        u = np.floor((P[:, 2] - ZMIN) * 16).astype(int)
        v = np.floor((7 - P[:, 1]) * 16).astype(int)
        ok = alive & (u >= 0) & (u < W) & (v >= 0) & (v < H)
        uu, vv = np.clip(u, 0, W - 1), np.clip(v, 0, H - 1)
        surf = XB - depth[vv, uu] / 16.0
        inside = ok & has[vv, uu] & (P[:, 0] >= surf)
        dhit[inside] = t[inside]
        duv[inside] = np.stack([uu[inside], vv[inside]], 1)
        side[inside] = (P[inside, 0] - surf[inside]) > xstep * 1.5
        sidepos[inside] = P[inside]
        alive &= ~inside
    img = np.zeros((D.shape[0], 3), float)
    # sky: night, stars, Minecraft's square moon up and to the left
    alt = D[:, 1]
    sky = np.array(SKY[1], float)[None] * (1 - alt[:, None]) * 0.9 + np.array(SKY[0], float)[None] * alt[:, None]
    img[:] = sky
    rng = random.Random(9)
    starmask = np.zeros(D.shape[0], bool)
    for _ in range(140):
        starmask[rng.randrange(D.shape[0])] = True
    img[starmask & (alt > 0.3)] = (190, 196, 220)
    mt = moon_tex()
    mdir = np.array([0.81, 0.56, -0.12])
    mdir /= np.linalg.norm(mdir)
    mright = np.cross(mdir, np.array([0, 1.0, 0]))
    mright /= np.linalg.norm(mright)
    mup = np.cross(mright, mdir)
    dp = D @ mdir
    mu = (D @ mright) / np.maximum(dp, 1e-6)
    mv = (D @ mup) / np.maximum(dp, 1e-6)
    ms = 0.11
    inm = (dp > 0) & (np.abs(mu) < ms) & (np.abs(mv) < ms)
    tu = np.clip(((mu + ms) / (2 * ms) * mt.shape[1]).astype(int), 0, mt.shape[1] - 1)
    tv = np.clip(((ms - mv) / (2 * ms) * mt.shape[0]).astype(int), 0, mt.shape[0] - 1)
    mc = mt[tv, tu]
    lit_m = inm & (mc[:, 3] > 0)
    img[lit_m] = img[lit_m] + mc[lit_m, :3] * 0.95     # the sky draws the moon additively
    # blocks
    vox = (hit > 0) & (thit < dhit)
    Ph = O[None] + thit[:, None] * D
    ft = flesh_tile()
    for b, (tt, ts, tb) in BLOCKS.items():
        sel = vox & (hit == b)
        if not sel.any():
            continue
        for ax in (0, 1, 2):
            s2 = sel & (axis == ax)
            if not s2.any():
                continue
            P = Ph[s2]
            if ax == 1:
                down = step[s2, 1] < 0      # ray going down hits a top face
                tname = np.where(down, 0, 2)
                fu, fv = P[:, 0] % 1, P[:, 2] % 1
            elif ax == 0:
                fu, fv = P[:, 2] % 1, 1 - (P[:, 1] % 1)
                tname = np.ones(len(P), int)
            else:
                fu, fv = P[:, 0] % 1, 1 - (P[:, 1] % 1)
                tname = np.ones(len(P), int)
            tu = np.clip((fu * 16).astype(int), 0, 15)
            tv = np.clip((fv * 16).astype(int), 0, 15)
            cols = np.zeros((len(P), 3))
            for which, nm in enumerate((tt, ts, tb)):
                w_ = tname == which
                if not w_.any():
                    continue
                T_ = ft if nm == 'flesh' else tex(nm)
                cols[w_] = T_[tv[w_], tu[w_], :3]
                if nm == 'black_stained_glass':
                    cols[w_] = cols[w_] * 0.5 + np.array([20, 22, 40]) * 0.5
            shade = {0: 0.62, 1: 1.0, 2: 0.8}[ax]
            if ax == 1:
                shade = np.where(step[s2, 1] < 0, 1.0, 0.5)
            else:
                shade = np.full(len(P), shade)
            # night: the moon from above, a warm lamp glow low near the player, darker with distance
            dist = thit[s2]
            light = 0.42 + 0.18 * (P[:, 1] > 2.9) + 0.0 * dist
            lamp = np.exp(-((P[:, 0] - 1.2) ** 2 + (P[:, 1] - 3) ** 2 + (P[:, 2] - 0.5) ** 2) / 10.0) * 0.55
            low = np.clip(1 - P[:, 1] / 2.5, 0, 1) * 0.12
            fall = np.clip(1.15 - dist / 26.0, 0.6, 1.0)
            if b == 11:
                light, lamp, fall = np.ones(len(P)) * 1.1, 0, 1.0
            c = cols * (shade * (light + lamp + low) * fall)[:, None]
            c = c * np.array([1.0, 0.94, 0.86])[None] if b != 14 else c
            warm = (lamp + low)[:, None] * np.array([40, 22, 6])[None] if b != 11 else 0
            img[s2] = c + warm
    dev = np.isfinite(dhit) & (dhit <= thit)
    uv = duv[dev]
    img[dev] = front[uv[:, 1], uv[:, 0]].astype(float) * 0.9
    sd = dev & side
    if sd.any():
        sp = sidepos[sd]
        tu = np.clip((sp[:, 0] * 16).astype(int) % 16, 0, 15)
        tv = np.clip(((7 - sp[:, 1]) * 16).astype(int) % 16, 0, 15)
        uvs = duv[sd]
        img[sd] = ft[tv, tu, :3].astype(float) * 0.22 + front[uvs[:, 1], uvs[:, 0]].astype(float) * 0.38
    img = np.clip(img, 0, 255).reshape(RH, RW, 3).astype(np.uint8)
    # a crosshair, as the game draws it
    cx, cy = RW // 2, RH // 2
    img[cy, cx - 3:cx + 4] = (230, 230, 230)
    img[cy - 3:cy + 4, cx] = (230, 230, 230)
    im = Image.fromarray(img).resize((1080, 1920), Image.NEAREST)
    im.save(os.path.join(OUT, 'final_game_frame.png'))
    return im


def mouth_close(p, img):
    m = mouth_at(p.comp, 114)
    x0, y0 = int(m['cx'] - m['w'] / 2 - 8), int(m['cy'] - m['h'] / 2 - 6)
    x1, y1 = x0 + m['w'] + 16, y0 + m['h'] + 12
    crop = img[max(0, y0):min(H, y1), max(0, x0):min(W, x1)]
    s = 12
    im = Image.fromarray(crop).resize((crop.shape[1] * s, crop.shape[0] * s), Image.NEAREST)
    im.save(os.path.join(OUT, 'final_mouth_heart.png'))
    return im


def present():
    p = final_painter()
    img = p.render()
    save_scaled(img, 'final_front_6x.png', 6)
    mouth = mouth_close(p, img)
    side = side_sheet()
    boxes = plan_sheet(p, img)
    frame = mock_frame()
    with open(os.path.join(OUT, '_boxes.txt'), 'w') as f:
        for b in boxes:
            f.write('%-6s x %3d-%3d  y %3d-%3d  z %5.1f-%5.1f texels   (%.1f x %.1f x %.1f blocks)\n' % (
                b['lobe'], b['x0'], b['x1'], b['y0'], b['y1'], b['z0'], b['z1'],
                (b['x1'] - b['x0']) / 16, (b['y1'] - b['y0']) / 16, (b['z1'] - b['z0']) / 16))
    small_sheet(img, mouth, side, frame)


def small_sheet(img, mouth, side, frame):
    """One sheet about 1200 px wide for kortev's phone: the front big, the game frame beside it, the mouth and side."""
    SW = 1200
    front = Image.fromarray(img).resize((W * 4, H * 4), Image.NEAREST)          # 704 x 448
    fr = frame.resize((252, 448), Image.LANCZOS)
    mo = mouth.resize((int(mouth.width * 300 / mouth.height), 300), Image.NEAREST)
    sd = side.resize((int(side.width * 300 / side.height), 300), Image.LANCZOS)
    sheet = Image.new('RGB', (SW, 40 + 448 + 40 + 300 + 20), (16, 14, 20))
    d = ImageDraw.Draw(sheet)
    d.text((12, 10), 'Eternity Devil v4: composition B, after %d passes' % len(PASSES), fill=(232, 226, 218), font=font(22))
    sheet.paste(front, (12, 40))
    sheet.paste(fr, (12 + 704 + 12, 40))
    d.text((12 + 704 + 12 + 252 + 10, 50), 'front: 11 x 7 blocks\nat 16 texels a block\n\nleft: in game,\nportrait, from\n'
           '8 blocks, eye\nheight', fill=(200, 194, 186), font=font(16))
    y2 = 40 + 448 + 30
    sheet.paste(mo, (12, y2))
    sx = 12 + mo.width + 12
    if sx + sd.width > SW - 12:
        sd = sd.resize((SW - 12 - sx, int(sd.height * (SW - 12 - sx) / sd.width)), Image.LANCZOS)
    sheet.paste(sd, (sx, y2))
    d.text((12, y2 - 22), 'the great mouth and the heart', fill=(200, 194, 186), font=font(16))
    d.text((sx, y2 - 22), 'side: how far it bulges toward you', fill=(200, 194, 186), font=font(16))
    sheet.save(os.path.join(OUT, '_concept_small.png'))


# ---- Export for the game build: the final concept cut into what the cuboid renderer needs.
#
# Frame of the data (all in texels, 16 to the block):
#   u  right across the front (world +z),  v  down from the top of the 7-block front (world y = floor + 7 - v/16),
#   z  out from the devil's back plane toward the players (world x = back plane - z/16).
# The maw's centre column is MAW_U; the build puts it on the corridor's axis.
#
# The atlas (ATLAS_W x ATLAS_H):
#   one 176 x 112 front layer per lobe in rows of four from (0, 0): only that lobe's pixels are opaque, so each lobe's
#   boxes can map their front faces straight onto their layer by (u, v). The eyes' whites and the great mouth's throat
#   are cut out (alpha 0): the eyeball boxes and the maw's cavity sit behind those holes.
#   Then the sprites, shelf-packed below the layers: side tiles, the heart, each eye's white, iris and lid, each tooth.

ATLAS_W, ATLAS_H = 704, 512
BAND = 16          # lobe box rows, texels
SEG = 32           # lobe box columns, texels


def export(out_dir):
    import json
    os.makedirs(out_dir, exist_ok=True)
    n = len(PASSES)
    rec = {'eyes': [], 'mouths': []}
    comp = comp_pass(n)
    p = paint(comp, level=n, rec=rec)
    img = p.render(bg=False)
    flesh_m = p.idx >= 0
    over_m = p.over[..., 3] > 0
    alpha = (flesh_m | over_m)
    # every pixel belongs to a lobe: feature pixels past a silhouette go to the nearest flesh
    _, (iy, ix) = ndimage.distance_transform_edt(~flesh_m, return_indices=True)
    lobe_of = np.where(flesh_m, p.lobe, p.lobe[iy, ix])
    depth = np.where(flesh_m, p.zf, p.zf[iy, ix] - 1.0)
    cut = np.zeros_like(alpha)
    for e in rec['eyes']:
        for (x, y) in e['sclera']:
            cut[y, x] = True
    maw = next(m for m in rec['mouths'] if m['heart'])
    mm = maw['mask']
    for yy in range(mm.shape[0]):
        for xx in range(mm.shape[1]):
            X, Y = maw['ox'] + xx, maw['oy'] + yy
            if mm[yy, xx] and 0 <= X < W and 0 <= Y < H and tuple(int(v) for v in img[Y, X]) not in GUM:
                cut[Y, X] = True
    alpha = alpha & ~cut
    atlas = np.zeros((ATLAS_H, ATLAS_W, 4), np.uint8)
    names = [L['name'] for L in comp['lobes']]
    layers = {}
    for k, nm in enumerate(names):
        lx, ly = (k % 4) * W, (k // 4) * H
        a = alpha & (lobe_of == k)
        atlas[ly:ly + H, lx:lx + W, :3] = np.where(a[..., None], img, 0)
        atlas[ly:ly + H, lx:lx + W, 3] = np.where(a, 255, 0)
        layers[nm] = [lx, ly]
    # sprite shelf
    shelf = {'x': 0, 'y': 2 * H, 'h': 0}

    def place(spr):
        hh, ww = spr.shape[:2]
        if shelf['x'] + ww > ATLAS_W:
            shelf['x'], shelf['y'], shelf['h'] = 0, shelf['y'] + shelf['h'] + 1, 0
        x, y = shelf['x'], shelf['y']
        assert y + hh <= ATLAS_H, 'atlas full'
        atlas[y:y + hh, x:x + ww] = spr
        shelf['x'] += ww + 1
        shelf['h'] = max(shelf['h'], hh)
        return [x, y, ww, hh]

    def solid(col):
        a = np.zeros((16, 16, 4), np.uint8)
        a[..., :3] = col
        a[..., 3] = 255
        return a

    tiles = {}
    ft = flesh_tile()
    tiles['flesh_side'] = place(ft)
    dark = ft.copy()
    dark[..., :3] = (dark[..., :3].astype(float) * 0.6).astype(np.uint8)
    tiles['flesh_side_dark'] = place(dark)
    dead = ft.copy()
    dd = np.array(DEAD5, np.uint8)
    for y in range(16):
        for x in range(16):
            i = FLESH.index(tuple(int(v) for v in ft[y, x, :3])) if tuple(int(v) for v in ft[y, x, :3]) in FLESH else 4
            dead[y, x, :3] = dd[i]
    tiles['dead_side'] = place(dead)
    tiles['throat'] = place(solid(THROAT[0]))
    th = solid(THROAT[1])
    th[12:, :, :3] = THROAT[2]
    tiles['throat_floor'] = place(th)
    gum = solid(GUM[1])
    gum[:2, :, :3] = GUM[0]
    gum[-3:, :, :3] = GUM[2]
    tiles['gum'] = place(gum)
    hs_ = solid(HEART_PAL[2])
    hs_[::4, :, :3] = HEART_PAL[1]
    tiles['heart_side'] = place(hs_)
    tiles['tooth_side'] = place(solid(TOOTH[1]))
    tiles['sclera_side'] = place(solid(SCLERA[1]))
    heart = heart_v3(1.15, v5=True, v6=True)
    tiles['heart'] = place(heart)
    # eyes
    eyes = []
    for e in rec['eyes']:
        px = e['sclera']
        if not px:
            continue
        xs_ = [q[0] for q in px]
        ys_ = [q[1] for q in px]
        x0, x1, y0, y1 = min(xs_), max(xs_), min(ys_), max(ys_)
        sw, sh = x1 - x0 + 1, y1 - y0 + 1
        scl = np.zeros((sh, sw, 4), np.uint8)
        for (x, y), c in px.items():
            scl[y - y0, x - x0, :3] = c
            scl[y - y0, x - x0, 3] = 255
        # fill the box's rectangle round the almond with the shaded white, so a moving iris never shows a gap
        holes = scl[..., 3] == 0
        scl[holes, :3] = SCLERA[2]
        scl[holes, 3] = 255
        ri = e['ri']
        d = max(1, int(math.ceil(ri * 2)))
        iris = np.zeros((d, d, 4), np.uint8)
        pal = IRIS[e['iris']]
        for y in range(d):
            for x in range(d):
                dx, dy = x + 0.5 - d / 2, y + 0.5 - d / 2
                dist = math.hypot(dx, dy)
                if dist < ri or d <= 2:
                    c = pal[1]
                    if ri >= 2.5 and dist > ri - 1:
                        c = pal[0]
                    elif ri >= 2 and dy > 0.3 and dx > -0.5:
                        c = pal[2]
                    if dist < (ri * 0.45 if ri >= 1.6 else 0.6):
                        c = PUPIL
                    iris[y, x, :3] = c
                    iris[y, x, 3] = 255
        if d >= 3:
            g = int(d / 2 - ri * 0.45)
            iris[max(0, g), max(0, g), :3] = GLINT
        lid = np.zeros((sh + 1, sw, 4), np.uint8)
        loc = int(np.clip(np.median([p.idx[min(H - 1, y0 - 2), min(W - 1, max(0, x))] for x in range(x0, x1 + 1)]), 2, 7))
        lid[..., :3] = FLESH[min(loc + 1, 7)]
        lid[0, :, :3] = FLESH[min(loc + 2, 8)]
        lid[-1, :, :3] = LASH
        lid[..., 3] = 255
        k = int(lobe_of[int(e['cy']), int(e['cx'])])
        eyes.append(dict(rect=[x0, y0, sw, sh], center=[e['cx'], e['cy']], width=e['w'], lobe=names[k],
                         surface_z=round(float(depth[int(e['cy']), int(e['cx'])]), 1),
                         rest_gaze=list(e['gaze']), iris=e['iris'], iris_d=d,
                         uv_sclera=place(scl), uv_iris=place(iris), uv_lid=place(lid)))
    # the great mouth: its opening, its teeth (each its own box), its heart
    teeth = []
    for upper, px in maw['teeth']:
        if not px:
            continue
        xs_ = [q[0] for q in px]
        ys_ = [q[1] for q in px]
        x0, x1, y0, y1 = min(xs_), max(xs_), min(ys_), max(ys_)
        spr = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), np.uint8)
        for (x, y, c) in px:
            spr[y - y0, x - x0, :3] = c
            spr[y - y0, x - x0, 3] = 255
        cx_ = (x0 + x1 + 1) / 2
        fx = (cx_ - maw['ox']) / mm.shape[1]
        teeth.append(dict(rect=[x0, y0, x1 - x0 + 1, y1 - y0 + 1], upper=upper, uv=place(spr),
                          over_heart=bool(0.30 < fx < 0.68)))
    hh, hw_ = heart.shape[:2]
    LW, LH = mm.shape[1], mm.shape[0]
    heart_x = maw['ox'] + LW // 2 - hw_ // 2 + 1
    heart_y = maw['oy'] + int(LH / 2 - hh * 0.42)
    open_rows = np.where(mm.any(axis=1))[0]
    open_cols = np.where(mm.any(axis=0))[0]
    maw_rect = [maw['ox'] + int(open_cols.min()), maw['oy'] + int(open_rows.min()),
                int(open_cols.max() - open_cols.min() + 1), int(open_rows.max() - open_rows.min() + 1)]
    maw_z = float(np.percentile(depth[maw_rect[1]:maw_rect[1] + maw_rect[3], maw_rect[0]:maw_rect[0] + maw_rect[2]], 70))
    small = []
    for m in rec['mouths']:
        if m['heart']:
            continue
        rows, cols = np.where(m['mask'].any(axis=1))[0], np.where(m['mask'].any(axis=0))[0]
        r = [m['ox'] + int(cols.min()) - 1, m['oy'] + int(rows.min()) - 1, int(cols.max() - cols.min() + 3),
             int(rows.max() - rows.min() + 3)]
        k = int(lobe_of[int(m['cy']), int(m['cx'])])
        small.append(dict(rect=r, lobe=names[k], surface_z=round(float(depth[int(m['cy']), int(m['cx'])]), 1),
                          vertical=m['orient'] == 'v'))
    # lobe boxes: rows of BAND texels, cut into columns of about SEG, each pushed out to its flesh
    boxes = []
    for k, nm in enumerate(names):
        mask = alpha & (lobe_of == k) | (cut & (lobe_of == k))
        rows = np.where(mask.any(axis=1))[0]
        if not len(rows):
            continue
        for v0 in range(int(rows.min()), int(rows.max()) + 1, BAND):
            band = mask[v0:v0 + BAND]
            cols = np.where(band.any(axis=0))[0]
            if not len(cols):
                continue
            u0, u1 = int(cols.min()), int(cols.max()) + 1
            nseg = max(1, int(round((u1 - u0) / SEG)))
            for sgi in range(nseg):
                a = u0 + (u1 - u0) * sgi // nseg
                b = u0 + (u1 - u0) * (sgi + 1) // nseg
                cell = band[:, a:b]
                if not cell.any():
                    continue
                ys2, xs2 = np.where(cell)
                zf = depth[v0:v0 + BAND, a:b][cell]
                boxes.append(dict(lobe=nm, u0=a + int(xs2.min()), u1=a + int(xs2.max()) + 1, v0=v0 + int(ys2.min()),
                                  v1=v0 + int(ys2.max()) + 1, z_back=float(comp['lobes'][k]['base']) - 4,
                                  z_front=round(float(np.percentile(zf, 75)), 1),
                                  side='dead_side' if nm == 'wallR' else 'flesh_side'))
    data = dict(
        note='Made by tools/eternity_devil_concept.py export. Texels, 16 a block. u right (world +z), v down from the '
             'top of the 7-block front (world y = floor + 7 - v/16), z out from the back plane (world x = back - z/16).',
        size=[W, H], maw_u=maw['cx'], atlas=[ATLAS_W, ATLAS_H], layers=layers, tiles=tiles,
        boxes=boxes, eyes=eyes,
        maw=dict(rect=maw_rect, surface_z=round(maw_z, 1), cavity_depth=16, teeth=teeth,
                 heart=dict(rect=[heart_x, heart_y, hw_, hh], z=round(maw_z - 16 + 4, 1), uv=tiles['heart'])),
        small_mouths=small,
        sinews=[dict(path=[list(q) for q in path], thickness=t) for path, t in comp['strands']],
    )
    Image.fromarray(atlas).save(os.path.join(out_dir, 'eternity_devil.png'))
    with open(os.path.join(out_dir, 'eternity_devil_layout.json'), 'w') as f:
        json.dump(data, f, indent=1, default=lambda o: o.item() if hasattr(o, 'item') else str(o))
    # a preview: the layers put back together, to check nothing was lost
    prev = np.zeros((H, W, 4), np.uint8)
    for k, nm in enumerate(names):
        lx, ly = layers[nm]
        lay = atlas[ly:ly + H, lx:lx + W]
        prev = np.where(lay[..., 3:] > 0, lay, prev)
    Image.fromarray(prev).resize((W * 4, H * 4), Image.NEAREST).save(os.path.join(out_dir, '_export_check.png'))
    print('export: %d lobe boxes, %d eyes, %d teeth, %d small mouths; atlas used to y %d of %d' % (
        len(boxes), len(eyes), len(teeth), len(small), shelf['y'] + shelf['h'], ATLAS_H))


def phone_check(img, name):
    """What kortev sees on a phone: the front about 390 px wide."""
    im = Image.fromarray(img).resize((W * 2, H * 2), Image.NEAREST).resize((390, int(390 * H / W)), Image.LANCZOS)
    im.save(os.path.join(OUT, '_phone_%s.png' % name))


def do_pass(n):
    img = paint(comp_pass(n), level=n).render()
    save_scaled(img, 'pass%d.png' % n, 4)
    phone_check(img, 'pass%d' % n)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    args = sys.argv[1:]
    if not args or args[0] == 'comps':
        comps()
    if args and args[0] == 'pass':
        do_pass(int(args[1]))
    if not args:
        for n in range(1, len(PASSES) + 1):
            do_pass(n)
    if not args or args[0] == 'present':
        present()
    if args and args[0] == 'export':
        export(args[1] if len(args) > 1 else os.path.join(OUT, 'export'))


if __name__ == '__main__':
    main()
