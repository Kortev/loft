"""The first common devils, pixel-art concepts at true game resolution (16 texels a block): the Zombie Devil (with
its zombies) and the Bat Devil.

The method is the Eternity Devil v4's (tools/eternity_devil_concept.py): compose, don't generate. Every body part
below is a hand-placed shape (an ellipse, a metaball cluster, a bevelled polygon or a tapering tube), and every eye,
tooth, fold, vein and rib is placed by hand in the data. The code only shades the shapes with a small palette (light
from the moon above and to the left), draws the ink line round the silhouette and the seams where one part lies on
another, and stamps the hand-drawn features on top.

The looks follow text descriptions of the anime (season 1):
- the Zombie Devil: a huge limbless corpse torso, arms long gone; an oversized pink brain bulges out where the head
  should be; a large human face grows out of the right side of its chest; organs and viscera hang from it as
  tentacles it stands on and links its zombies with. Its zombies are the people it turned, in their own clothes.
- the Bat Devil: a huge, muscular, human-shaped bat, dark grey, with a white mane round its head, a grotesque face
  with large round eyes and many sharp teeth, a long tongue, big claws, and arms joined to its body by flaps of skin
  that are its wings.

    python tools/common_devils_concept.py             # everything
    python tools/common_devils_concept.py comps       # the three compositions of each devil (front, side, action)
    python tools/common_devils_concept.py pass 2      # one refining pass (zd_pass2, bat_pass2, _phone_pass2)
    python tools/common_devils_concept.py present     # finals, game frames, cuboid plans, _concept_small.png

Writes into work/common_devils/ (git-ignored). No game code. The mock game frames read Minecraft's own textures
from the Loom cache's client jar (flat colours if it isn't there).
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
OUT = os.path.abspath(os.path.join(HERE, '..', 'work', 'common_devils'))
TPB = 16                      # texels per block
NPASS = 4


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*cols):
    return [hx(c) for c in cols]


# ---------------------------------------------------------------------------------------------------------------
# The palette. Each material is a short ramp, darkest first. Index 0 is the seam/ink tone of that material, the
# last index is the wet highlight. Nothing else is ever drawn: the palette stays limited.
# ---------------------------------------------------------------------------------------------------------------
INK = hx('#0a0807')
BG = hx('#0c0c13')
MAT = {
    # the Zombie Devil
    'skin': ramp('#151410', '#26251c', '#3a392b', '#504e3b', '#69664d', '#848061', '#a29d79', '#c2bc97', '#dcd6b4'),
    'rot': ramp('#121309', '#1f2311', '#2f3719', '#404b22', '#54602c', '#6b7836', '#839040', '#9aa64e', '#b2bc66'),
    'gut3': ramp('#1c0508', '#360c12', '#55141c', '#781f26', '#992f33', '#b44843', '#cc6a5c', '#e29482', '#f2bfae'),
    'rot3': ramp('#121008', '#1e1b0e', '#2c2914', '#3b381b', '#4b4823', '#5d5a2c', '#716e37', '#868242', '#9c974f'),
    'bruise': ramp('#140c14', '#22142a', '#33203d', '#46304f', '#5a4062', '#6e5274', '#856a88', '#9c829e', '#b49cb4'),
    'brain': ramp('#25090f', '#46121e', '#6c1f2f', '#943345', '#b84d5c', '#d4707a', '#e7979a', '#f4bdb8', '#fde0d8'),
    'meat': ramp('#170306', '#33090d', '#561116', '#7e1c1f', '#a42c2a', '#c44a3f', '#dc705e', '#f09c86'),
    'gut': ramp('#200810', '#3e1220', '#621f30', '#874048', '#a85c5e', '#c27a74', '#d99a8c', '#ecbca8', '#f8dccb'),
    'bone': ramp('#2e2a20', '#544c3c', '#83775f', '#ada085', '#cfc4a6', '#ebe3c9', '#fbf7ea'),
    'tooth': ramp('#3c3220', '#6e5f3c', '#a28f62', '#c9b98c', '#e6dcb8', '#fbf6e2'),
    'sclera': ramp('#3a302c', '#6e625a', '#a29486', '#cbbfae', '#e8e0d0', '#fbf8f0'),
    'pupil': ramp('#050303', '#140c0a', '#2a1a14'),
    'mouth': ramp('#040102', '#14050a', '#2a0a12', '#45121b'),
    'blood': ramp('#2a0306', '#4c070c', '#740d14', '#9c1a1e', '#c0302c'),
    'vein': ramp('#1a1226', '#2c1e3c', '#3e2c52', '#523c66'),
    # the Bat Devil
    'fur': ramp('#07070a', '#111116', '#1b1b22', '#26262f', '#33333e', '#42424f', '#545462', '#6a6a7a', '#848494'),
    'mane': ramp('#3a3a42', '#5c5c66', '#80808a', '#a2a2ac', '#c2c2ca', '#dcdce2', '#f0f0f4', '#ffffff'),
    'memb': ramp('#08070b', '#110e16', '#1b1622', '#262030', '#322a3e', '#40364e', '#50445e', '#625470'),
    'face': ramp('#0c0a0c', '#171316', '#231d22', '#30282f', '#3e343c', '#4e434c', '#615460', '#786a76', '#928490'),
    'face3': ramp('#120e0f', '#201a1b', '#2f2729', '#3f3538', '#504548', '#63575a', '#786b6d', '#908284', '#aa9c9c'),
    'gum': ramp('#22050c', '#420c18', '#681826', '#902a38', '#b4434e', '#d06a70', '#e89a98'),
    'claw': ramp('#16130f', '#2c2720', '#4a4236', '#6e6452', '#9a8e76', '#c4b89c', '#e8dec4'),
    'beye': ramp('#3a3418', '#6c6230', '#a69a50', '#d2c878', '#ece4a8', '#fcf8dc'),
    # the zombies' clothes
    'suit': ramp('#060608', '#0f0f13', '#18181e', '#22222a', '#2e2e38', '#3c3c48', '#4c4c5a'),
    'shirt': ramp('#3a3a3c', '#5e5e60', '#848484', '#a8a8a4', '#c8c8c0', '#e2e2da', '#f4f4ee'),
    'tie': ramp('#200608', '#3e0c10', '#5e1418', '#801e22', '#a02c2c'),
    'denim': ramp('#0c1220', '#162038', '#203050', '#2c4268', '#3c5680', '#506c98', '#6a86ae'),
    'work': ramp('#1c1408', '#33240e', '#4c3818', '#684e22', '#86682e', '#a4843c', '#c0a04e'),
    'hair': ramp('#060505', '#100d0b', '#1c1612', '#2a211a', '#3a2e24', '#4c3e30'),
    'zskin': ramp('#14161a', '#22262c', '#33383e', '#454b50', '#5a6064', '#70767a', '#888e8e', '#a2a6a2', '#bcbeb6'),
    'shoe': ramp('#050404', '#0e0b0a', '#1a1412', '#2a201c', '#3a2e28'),
}
WET = {'brain', 'meat', 'gut', 'gut3', 'sclera', 'gum', 'blood', 'beye', 'mouth'}

LIGHT = np.array([-0.38, -0.78, 0.50])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0])
HALF = HALF / np.linalg.norm(HALF)


# ---------------------------------------------------------------------------------------------------------------
# Pixel helpers
# ---------------------------------------------------------------------------------------------------------------
def catmull(pts, step=0.2):
    """A smooth curve through hand-placed points (Catmull-Rom), sampled densely."""
    pts = [np.array(p, float) for p in pts]
    if len(pts) < 2:
        return pts
    P = [pts[0] * 2 - pts[1]] + pts + [pts[-1] * 2 - pts[-2]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        n = max(2, int(np.linalg.norm(p2 - p1) / step))
        for k in range(n):
            t = k / n
            t2, t3 = t * t, t * t * t
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                              (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    out.append(P[-2])
    return out


def pixline(pts, smooth=True):
    """Pixel-perfect 1-texel line through the points: 8-connected, no doubled corners."""
    samp = catmull(pts, 0.15) if smooth and len(pts) > 2 else None
    if samp is None:
        samp = []
        for a, b in zip(pts[:-1], pts[1:]):
            n = max(2, int(math.hypot(b[0] - a[0], b[1] - a[1]) / 0.15))
            for k in range(n):
                t = k / n
                samp.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
        samp.append(pts[-1])
    px = []
    for s in samp:
        q = (int(math.floor(s[0])), int(math.floor(s[1])))
        if not px or px[-1] != q:
            px.append(q)
    # drop L-corners (a pixel whose neighbours touch each other diagonally)
    out = []
    for i, q in enumerate(px):
        if 0 < i < len(px) - 1:
            a, b = (out[-1] if out else px[i - 1]), px[i + 1]
            if abs(a[0] - b[0]) == 1 and abs(a[1] - b[1]) == 1:
                continue
        out.append(q)
    return out


class Tf:
    """A rotation about a pivot plus an offset, for posing hand-placed parts."""

    def __init__(self, ang=0.0, piv=(0, 0), off=(0, 0), sx=1.0):
        self.a, self.piv, self.off, self.sx = math.radians(ang), piv, off, sx

    def p(self, x, y):
        dx, dy = (x - self.piv[0]) * self.sx, y - self.piv[1]
        c, s = math.cos(self.a), math.sin(self.a)
        return (self.piv[0] + dx * c - dy * s + self.off[0], self.piv[1] + dx * s + dy * c + self.off[1])

    def ps(self, pts):
        return [self.p(*q) for q in pts]

    def deg(self):
        return math.degrees(self.a)


ID = Tf()


class Canvas:
    """Holds the picture as (material, ramp level) per texel, so the palette stays limited."""

    def __init__(self, w, h, level=1):
        self.w, self.h, self.level = w, h, level
        ys, xs = np.mgrid[0:h, 0:w].astype(float) + 0.5
        self.xs, self.ys = xs, ys
        self.z = np.full((h, w), -1e9)
        self.mat = np.full((h, w), -1, np.int16)
        self.part = np.full((h, w), -1, np.int16)
        self.n = np.zeros((h, w, 3))
        self.lv = np.zeros((h, w), np.int16)
        self.mats = list(MAT.keys())
        self.nparts = 0
        self.rimless = set()
        self.boxes = []

    def mi(self, name):
        return self.mats.index(name)

    # -- shapes: each returns (mask, height)
    def ell(self, cx, cy, rx, ry, rz=6.0, ang=0.0):
        a = math.radians(ang)
        dx, dy = self.xs - cx, self.ys - cy
        u = dx * math.cos(a) + dy * math.sin(a)
        v = -dx * math.sin(a) + dy * math.cos(a)
        d2 = (u / rx) ** 2 + (v / ry) ** 2
        m = d2 < 1
        return m, np.where(m, rz * np.sqrt(np.clip(1 - d2, 0, 1)), 0)

    def blob(self, ells, rz=8.0, T=0.25, prof=0.5):
        f = np.zeros_like(self.xs)
        for e in ells:
            cx, cy, rx, ry = e[:4]
            w = e[4] if len(e) > 4 else 1.0
            ang = math.radians(e[5]) if len(e) > 5 else 0.0
            dx, dy = self.xs - cx, self.ys - cy
            u = dx * math.cos(ang) + dy * math.sin(ang)
            v = -dx * math.sin(ang) + dy * math.cos(ang)
            d2 = (u / (rx * 1.414)) ** 2 + (v / (ry * 1.414)) ** 2
            f += w * np.clip(1 - d2, 0, None) ** 2
        m = f > T
        return m, np.where(m, rz * np.clip((f - T) / (1 - T), 0, 1) ** prof, 0)

    def poly(self, pts, rz=4.0, bevel=3.0):
        im = Image.new('L', (self.w, self.h), 0)
        ImageDraw.Draw(im).polygon([(x, y) for x, y in pts], fill=255)
        m = np.array(im) > 0
        d = ndimage.distance_transform_edt(m)
        t = np.clip(d / bevel, 0, 1)
        return m, np.where(m, rz * np.sqrt(np.clip(1 - (1 - t) ** 2, 0, 1)), 0)

    def tube(self, pts, r0, r1=None, seg=0.0, seglen=4.0, flat=1.0, smooth=True):
        """A tapering tube along a hand-placed path; seg > 0 pinches it into sausage segments (guts)."""
        r1 = r0 if r1 is None else r1
        samp = catmull(pts, 0.4) if smooth and len(pts) > 2 else catmull(pts, 0.4)
        L = [0.0]
        for a, b in zip(samp[:-1], samp[1:]):
            L.append(L[-1] + float(np.linalg.norm(b - a)))
        tot = max(L[-1], 1e-6)
        h = np.zeros_like(self.xs)
        m = np.zeros(self.xs.shape, bool)
        for s, l in zip(samp, L):
            r = r0 + (r1 - r0) * (l / tot)
            if seg > 0:
                r = r * (1 - seg * abs(math.sin(math.pi * l / seglen)) ** 3)
            x0, x1 = int(max(0, s[0] - r - 1)), int(min(self.w, s[0] + r + 2))
            y0, y1 = int(max(0, s[1] - r - 1)), int(min(self.h, s[1] + r + 2))
            if x0 >= x1 or y0 >= y1:
                continue
            d2 = (self.xs[y0:y1, x0:x1] - s[0]) ** 2 + (self.ys[y0:y1, x0:x1] - s[1]) ** 2
            hh = np.sqrt(np.clip(r * r - d2, 0, None)) * flat
            inside = d2 < r * r
            h[y0:y1, x0:x1] = np.maximum(h[y0:y1, x0:x1], hh)
            m[y0:y1, x0:x1] |= inside
        return m, h

    def add(self, shape, base, mat, rim=True):
        m, hgt = shape
        hz = base + hgt
        gy, gx = np.gradient(np.where(m, hgt, 0))
        gx, gy = np.clip(gx, -4, 4), np.clip(gy, -4, 4)
        nrm = np.sqrt(gx * gx + gy * gy + 1)
        win = m & (hz > self.z)
        self.z = np.where(win, hz, self.z)
        self.mat = np.where(win, self.mi(mat), self.mat)
        self.part = np.where(win, self.nparts, self.part)
        for k, c in enumerate((-gx / nrm, -gy / nrm, 1 / nrm)):
            self.n[..., k] = np.where(win, c, self.n[..., k])
        if not rim:
            self.rimless.add(self.nparts)
        self.nparts += 1
        return self.nparts - 1

    # -- shading
    def shade(self, hard=0.0, ink=True, gamma=1.0, falloff=0, soft=False):
        has = self.mat >= 0
        lit = self.n @ LIGHT
        spec = self.n @ HALF
        lens = np.array([len(MAT[k]) for k in self.mats])
        N = np.where(has, lens[np.clip(self.mat, 0, None)], 3)
        u = np.clip((lit + 0.12 - hard * 0.1) / (1.0 + hard * 0.15), 0, 0.999) ** gamma
        lv = 1 + np.floor(u * (N - 2)).astype(int)
        wet = np.isin(self.mat, [self.mi(k) for k in WET])
        lv = np.where(wet & (spec > 0.975), N - 1, lv)
        lv = np.where(~wet & (spec > 0.992), np.minimum(lv + 1, N - 2), lv)
        self.lv = np.where(has, lv, 0).astype(np.int16)
        z, part = self.z, self.part
        # contact shadows: a part lying under a nearer part goes darker just below it (the moon is above)
        sh = np.zeros_like(self.lv)
        for dy in (1, 2, 3):
            for dx in (-1, 0, 1):
                zu = np.roll(np.roll(z, dy, 0), dx, 1)
                pu = np.roll(np.roll(part, dy, 0), dx, 1)
                hit = has & (pu >= 0) & (pu != part) & (zu > z + 2.5)
                sh = np.maximum(sh, np.where(hit, 2 if dy == 1 else 1, 0))
        self.lv = np.where(has, np.maximum(self.lv - sh, 1), 0).astype(np.int16)
        if falloff:
            # the light falls off toward the silhouette, except on the faces turned up to the moon
            dist = ndimage.distance_transform_edt(has)
            fo = has & (dist <= falloff + 1) & (self.n[..., 1] > -0.25)
            self.lv = np.where(fo, np.maximum(self.lv - 1, 1), self.lv).astype(np.int16)
        # seams: the farther part takes a dark line where a nearer part lies on it
        seam = np.zeros(has.shape, bool)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            zn = np.roll(np.roll(z, dy, 0), dx, 1)
            pn = np.roll(np.roll(part, dy, 0), dx, 1)
            mn = np.roll(np.roll(self.mat, dy, 0), dx, 1)
            gap = np.where(soft & (mn == self.mat), 4.0, 1.5)
            seam |= has & (pn >= 0) & (pn != part) & (zn > z + gap)
        self.lv = np.where(seam, 0, self.lv)
        # the silhouette: an ink line, and a moonlit rim one texel inside on top edges
        bg = ~has
        edge = has & ndimage.binary_dilation(bg, iterations=1)
        top = has & ~edge & np.roll(edge & np.roll(bg, 1, 0), 1, 0)
        rimok = ~np.isin(part, list(self.rimless)) if self.rimless else np.ones_like(has)
        self.lv = np.where(top & rimok, np.minimum(self.lv + 2, N - 2), self.lv)
        self.edge = edge
        self.ink = ink

    # -- features, placed by hand after shading
    def put(self, x, y, mat, lv):
        if 0 <= x < self.w and 0 <= y < self.h:
            if mat is not None:
                self.mat[y, x] = self.mi(mat)
            n = len(MAT[self.mats[self.mat[y, x]]]) if self.mat[y, x] >= 0 else 1
            self.lv[y, x] = int(np.clip(lv, 0, n - 1))

    def rel(self, x, y, d, only=None):
        if 0 <= x < self.w and 0 <= y < self.h and self.mat[y, x] >= 0:
            if only is not None and self.mats[self.mat[y, x]] not in only:
                return
            n = len(MAT[self.mats[self.mat[y, x]]])
            self.lv[y, x] = int(np.clip(self.lv[y, x] + d, 0, n - 1))

    def line(self, pts, mat, lv, smooth=True):
        for x, y in pixline(pts, smooth):
            self.put(x, y, mat, lv)

    def crease(self, pts, deep=1, lip=1, smooth=True, only=None, over=0):
        """A fold: a dark line with the lip below it lifted by the moonlight (and the roll above it shaded)."""
        P = pixline(pts, smooth)
        S = set(P)
        for x, y in P:
            if 0 <= x < self.w and 0 <= y < self.h and self.mat[y, x] >= 0:
                if only is not None and self.mats[self.mat[y, x]] not in only:
                    continue
                self.lv[y, x] = max(0, min(self.lv[y, x], deep))
        for x, y in P:
            if (x, y + 1) not in S:
                self.rel(x, y + 1, lip, only)
            if over and (x, y - 1) not in S:
                self.rel(x, y - 1, over, only)

    def remat(self, mask, mat, d=0):
        has = self.mat >= 0
        m = mask & has
        self.mat = np.where(m, self.mi(mat), self.mat)
        n = len(MAT[mat])
        self.lv = np.where(m, np.clip(self.lv + d, 0, n - 1), self.lv).astype(np.int16)

    def sprite(self, rows, x0, y0, legend, flip=False):
        for j, row in enumerate(rows):
            if flip:
                row = row[::-1]
            for i, ch in enumerate(row):
                if ch in legend:
                    v = legend[ch]
                    if v is None:
                        continue
                    if v[0] == '+':
                        self.rel(x0 + i, y0 + j, v[1])
                    else:
                        self.put(x0 + i, y0 + j, v[0], v[1])

    def render(self, bg=True, ink=None):
        img = np.zeros((self.h, self.w, 4), np.uint8)
        if bg:
            img[..., :3] = BG
            img[..., 3] = 255
        has = self.mat >= 0
        for k, name in enumerate(self.mats):
            sel = has & (self.mat == k)
            if not sel.any():
                continue
            pal = np.array(MAT[name], np.uint8)
            img[sel, :3] = pal[np.clip(self.lv[sel], 0, len(pal) - 1)]
            img[sel, 3] = 255
        if (self.ink if ink is None else ink):
            e = has & ndimage.binary_dilation(~has, iterations=1)
            img[e, :3] = INK
        return img

    def alpha(self):
        return self.mat >= 0


def mask_ell(cv, cx, cy, rx, ry, ang=0.0):
    return cv.ell(cx, cy, rx, ry, 1, ang)[0]


# ---------------------------------------------------------------------------------------------------------------
# THE ZOMBIE DEVIL
# A huge limbless corpse torso (about 4.6 blocks tall). The pink brain bulges out of the neck where a head should
# be; a large human face grows out of the right side of its chest (the viewer's left in front); the torn belly lets
# the viscera hang out as tentacles it stands on and reaches with.
# ---------------------------------------------------------------------------------------------------------------
ZW, ZH = 80, 80

# The zombie face on the chest, hand-drawn (17 x 20). Legend below: the head shape is shaded first, then this sits
# on it. '.' leaves the shading, letters set a material/level, '-'/'+' darken or lift.
ZFACE_1 = [
    ".................",
    ".................",
    "....--.....--....",
    "...-ddd...ddd-...",
    "..-kWWWk.kWWWk-..",
    "..kWWpWWkWWpWWk..",
    "..-kWWWk.kWWWk-..",
    "...-kkk...kkk-...",
    ".......-.-.......",
    "........-........",
    "......kk.kk......",
    ".......---.......",
    "....kkkkkkkkk....",
    "...kTTTTTTTTTk...",
    "...kmmmmmmmmmk...",
    "...kmMMMMMMMmk...",
    "...kmmmmmmmmmk...",
    "...ktTtTtTtTtk...",
    "....kkkkkkkkk....",
    ".................",
]


def zface_legend(level):
    return {
        'd': ('+', -2), '-': ('+', -1), '+': ('+', 1),
        'k': ('skin', 0), 'W': ('sclera', 4), 'p': ('pupil', 0),
        'T': ('tooth', 4), 't': ('tooth', 2), 'm': ('mouth', 0), 'M': ('mouth', 2),
    }


def zombie_devil(level=1, view='front'):
    if view == 'side':
        return zombie_devil_side(level)
    action = view == 'action'
    W_ = 112 if action else ZW
    cv = Canvas(W_, ZH, level)
    # the body is posed by a transform: the action pose rears up and twists toward its prey
    T = Tf(-9, (40, 62), (16, -1)) if action else ID
    P = T.p
    da = T.deg()

    # --- back tentacles (planted far behind, darkest)
    back = [
        ([(27, 55), (21, 64), (17, 72), (15, 79)], 3.6, 2.6),
        ([(55, 55), (61, 64), (64, 72), (67, 79)], 3.6, 2.6),
    ]
    if action:
        back = [
            ([(27, 55), (19, 63), (12, 71), (9, 79)], 3.6, 2.6),
            ([(55, 55), (64, 62), (72, 70), (78, 79)], 3.6, 2.6),
        ]
    ox = 16 if action else 0
    root = lambda pts: [P(*pts[0])] + [(x + ox, y) for x, y in pts[1:]]
    for pts, r0, r1 in back:
        cv.add(cv.tube(root(pts), r0, r1, seg=0.22, seglen=4.5), 0, 'gut')

    # --- the torso: one bloated metaball mass, the belly sagging to its right (viewer's left)
    tor = [(40, 33, 22, 8), (39, 44, 25, 13), (37, 51, 22, 9, 0.9), (41, 28, 24, 5, 0.8)]
    cv.add(cv.blob([(P(e[0], e[1]) + e[2:4] + ((e[4] if len(e) > 4 else 1.0), da)) for e in tor], rz=16), 10, 'skin')
    # the arm stumps: rounded shoulders that end in a torn edge
    stumps = [(15, 31, 7, 6), (65, 30, 7, 6)]
    for sx, sy, rx, ry in stumps:
        cv.add(cv.ell(*P(sx, sy), rx, ry, 7, da), 17, 'skin')
    # the neck ring the brain bulges out of
    cv.add(cv.ell(*P(40, 25), 13, 4.5, 5, da), 18, 'skin')
    # --- the brain: two hemispheres, the left one bigger, sitting heavy on the neck
    br = [(32, 14, 11.5, 11), (48, 13, 10.5, 10.5), (40, 20, 13, 6, 0.8)]
    if action:
        br = [(31, 13, 12, 11.5), (48, 12, 11, 11), (40, 20, 13, 6, 0.8)]
    cv.add(cv.blob([(P(e[0], e[1]) + e[2:4] + ((e[4] if len(e) > 4 else 1.0), da)) for e in br], rz=13), 16, 'brain')
    # --- the face: a whole bald head pushing out of the right side of the chest
    fcx, fcy = P(26, 42)
    cv.add(cv.ell(fcx, fcy, 9, 11, 9, da), 24, 'skin')
    # --- the rib wound on the other side of the chest: torn skin, the dark cavity, ribs across it
    rcx, rcy = P(54, 39)
    cv.add(cv.ell(rcx, rcy, 7.5, 6.5, 2, da), 26.5, 'meat')
    # --- the belly wound and the guts hanging from it
    wcx, wcy = P(41, 57)
    cv.add(cv.ell(wcx, wcy, 12, 3.5, 2, da), 25.5, 'meat')
    front = [
        ([(36, 57), (33, 64), (30, 71), (28, 79)], 4.2, 3.2, 0.25),
        ([(45, 58), (47, 65), (49, 72), (51, 79)], 4.2, 3.2, 0.25),
        ([(41, 58), (40, 64), (41, 70), (43, 73)], 2.6, 1.8, 0.18),
        ([(32, 56), (24, 62), (14, 66), (6, 64), (3, 59)], 2.8, 1.4, 0.2),
        ([(50, 57), (60, 63), (69, 69), (76, 68), (79, 64)], 2.8, 1.3, 0.2),
    ]
    if action:
        # two guts thrown up and out like arms, toward the prey; the planted ones spread wide
        front = [
            ([(36, 57), (31, 64), (26, 71), (22, 79)], 4.2, 3.2, 0.25),
            ([(45, 58), (49, 65), (54, 72), (58, 79)], 4.2, 3.2, 0.25),
            ([(41, 58), (40, 64), (42, 69), (45, 72)], 2.6, 1.8, 0.18),
            ([(31, 55), (20, 52), (10, 42), (6, 30), (10, 22)], 3.0, 1.4, 0.2),
            ([(51, 56), (64, 54), (76, 46), (84, 34), (82, 26)], 3.0, 1.4, 0.2),
        ]
    for k, (pts, r0, r1, sg) in enumerate(front):
        cv.add(cv.tube(root(pts), r0, r1, seg=sg, seglen=4.5), 30 - k, 'gut')

    cv.shade(hard=0.3 if level >= 2 else 0.0)

    # ---- features (all hand-placed)
    q = lambda x, y: tuple(int(round(v)) for v in P(x, y))
    # brain: the great fissure and the folds (sulci), each a crease
    fis = [q(40, 3), q(41, 7), q(40, 11), q(41, 15), q(40, 19)]
    cv.line(fis, 'brain', 0)
    for pts in (
        [(26, 7), (29, 9), (33, 7), (36, 9)], [(23, 12), (27, 14), (31, 12), (35, 14), (38, 13)],
        [(24, 18), (28, 17), (31, 20), (35, 18), (38, 20)], [(29, 4), (32, 5), (35, 4)],
        [(44, 6), (47, 8), (51, 6), (54, 9)], [(43, 12), (47, 11), (50, 14), (55, 12), (57, 14)],
        [(44, 18), (48, 17), (52, 19), (56, 17)], [(46, 3), (50, 4)],
    ):
        cv.crease([q(*p) for p in pts], deep=1, lip=1, only={'brain'})
    # the face
    fx, fy = int(round(fcx)) - 8, int(round(fcy)) - 10
    cv.sprite(ZFACE_1, fx, fy, zface_legend(level))
    # the tear round the face where it pushes out of the chest
    # ribs: four curved bones across the dark cavity
    for k, yy in enumerate((35, 38, 41, 44)):
        cv.line([q(48, yy + 1), q(52, yy), q(57, yy), q(61, yy + 1)], 'bone', 4 - (k > 1))
    # stump ends: the torn meat and the bone poking out
    for sx, sy, d in ((15, 31, -1), (65, 30, 1)):
        ex = sx + d * 6
        for yy in range(sy - 3, sy + 4):
            cv.put(*q(ex, yy), 'meat', 3 + (yy < sy))
            cv.put(*q(ex - d, yy), 'meat', 2)
        cv.put(*q(ex + d, sy), 'bone', 5)
        cv.put(*q(ex + d, sy - 1), 'bone', 4)
        cv.put(*q(ex, sy), 'bone', 5)
    # rot patches and veins
    for ex, ey, rx, ry in ((57, 50, 7, 4.5), (17, 45, 3.5, 6), (58, 27, 5, 2.5)):
        cx_, cy_ = P(ex, ey)
        cv.remat(mask_ell(cv, cx_, cy_, rx, ry, da) & (cv.mat == cv.mi('skin')), 'rot')
    for pts in ([(44, 47), (47, 50), (46, 54)], [(47, 50), (51, 52)], [(33, 30), (30, 33), (31, 36)]):
        cv.line([q(*p) for p in pts], 'vein', 2)
    return cv


def zombie_devil_side(level=1):
    """Seen from its right, facing right: the hunched corpse, the brain leaning forward, the face in profile."""
    cv = Canvas(72, ZH, level)
    cv.add(cv.tube([(30, 56), (22, 66), (16, 79)], 3.6, 2.6, seg=0.22, seglen=4.5), 0, 'gut')
    cv.add(cv.blob([(30, 34, 14, 12), (36, 47, 17, 13), (34, 54, 13, 7, 0.9), (26, 28, 10, 6, 0.8)], rz=16), 10, 'skin')
    cv.add(cv.ell(29, 31, 6.5, 6.5, 7), 20, 'skin')
    cv.add(cv.ell(29, 31, 4.5, 4.5, 2), 27.5, 'meat')
    cv.add(cv.blob([(36, 14, 13, 11), (30, 20, 10, 7, 0.8)], rz=13), 16, 'brain')
    cv.add(cv.ell(52, 42, 7, 9, 8, -8), 24, 'skin')
    cv.add(cv.ell(47, 57, 6, 3, 2), 25.5, 'meat')
    for k, (pts, r0, r1) in enumerate((
            ([(46, 58), (50, 66), (54, 73), (56, 79)], 4.2, 3.2),
            ([(42, 58), (38, 66), (34, 73), (32, 79)], 4.2, 3.2),
            ([(49, 57), (58, 61), (66, 60), (70, 55)], 2.6, 1.3))):
        cv.add(cv.tube(pts, r0, r1, seg=0.25, seglen=4.5), 30 - k, 'gut')
    cv.shade(hard=0.3 if level >= 2 else 0.0)
    cv.line([(28, 4), (33, 6), (38, 5), (44, 8)], 'brain', 1)
    for pts in ([(25, 10), (30, 12), (35, 10), (40, 13), (46, 12)], [(24, 17), (29, 16), (33, 19), (38, 17), (45, 19)],
                [(32, 22), (36, 21), (41, 23)]):
        cv.crease(pts, only={'brain'})
    # profile face: brow, sunken eye, nose stub, the open jaw
    prof = [
        "...-...",
        "..-dd..",
        "..kWWk.",
        "..-kk..",
        ".....k.",
        "....kk.",
        "..kkkkk",
        "..kTTTk",
        "..kmmm.",
        "..kmMm.",
        "..ktTtk",
        "...kkk.",
    ]
    cv.sprite(prof, 51, 35, zface_legend(level))
    cv.put(28, 31, 'bone', 5)
    cv.put(29, 31, 'bone', 4)
    cv.put(28, 30, 'bone', 4)
    return cv


# ---------------------------------------------------------------------------------------------------------------
# ITS ZOMBIES: the people it turned, in their own clothes. The vanilla zombie's model and 64x64 skin layout; the
# show's look (grey dead skin, blank eyes, mouths hanging open and bloody), not the vanilla green.
# Front views are 16 x 32 texels: head 8 x 8, body 8 x 12, arms 4 x 12, legs 4 x 12.
# ---------------------------------------------------------------------------------------------------------------
ZOMBIES = {
    # a yakuza in a black suit (the first zombies in the show were yakuza)
    'suit': ([
        "....hhhhhhhh....",
        "....hHHhhhhh....",
        "....hssSSssh....",
        "....skwsSkws....",
        "....sssssssd....",
        "....sdkddkdd....",
        "....sbmmmmbd....",
        "....dsbmbbdd....",
        "ccccCcoboocccccc",
        "ccccCconboCcccec",
        "cCccCcCnbCcccCec",
        "cCcccCconccccCcc",
        "cCcccCconbcccCcc",
        "cCcccccnobcccCcc",
        "cCccccconbcccCcc",
        "cCcccccnoccccCcc",
        "cCcccccoocccccdc",
        "sscccccccccccssd",
        "sbsdcccccccccsrd",
        "ssdd.cccccccdssd",
        "....ppppPppp....",
        "....pPpppPpp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pppp.ppp....",
        "....pppp.ppp....",
        "....zzzz.zzz....",
        "....zZzz.zZz....",
    ], {'c': ('suit', 2), 'C': ('suit', 4), 'e': ('suit', 1), 'o': ('shirt', 5), 'n': ('tie', 3),
        'p': ('suit', 2), 'P': ('suit', 4), 'z': ('shoe', 1), 'Z': ('shoe', 3),
        'h': ('hair', 1), 'H': ('hair', 4)}),
    # a salaryman in shirt-sleeves and a loose tie
    'shirt': ([
        "....hhhhhhhh....",
        "....hhhHhhhh....",
        "....hssSSssh....",
        "....swksSwkd....",
        "....sssSsssd....",
        "....sdkddkdd....",
        "....ssmmmmbd....",
        "....dsbmmbdd....",
        "oOooOoonnooOoooo",
        "oOooOoOnoOooOoeo",
        "oOooOoonbboooOeo",
        "oOooOoonbbboOoeo",
        "oOooooonbbbooOoo",
        "sOoooooobbboooos",
        "ssoooooobboooosd",
        "sdooooooobooooss",
        "sbooooooooooooss",
        "ssdoooooooooodsd",
        "srsdoooooooooksd",
        "ssdd.oooooooodsd",
        "....ppppPppp....",
        "....pPpppPpp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pPpp.Ppp....",
        "....pppp.ppp....",
        "....pppp.ppp....",
        "....zzzz.zzz....",
        "....zZzz.zZz....",
    ], {'o': ('shirt', 4), 'O': ('shirt', 5), 'e': ('shirt', 2), 'n': ('tie', 3), 'p': ('suit', 3),
        'P': ('suit', 5), 'z': ('shoe', 1), 'Z': ('shoe', 3), 'h': ('hair', 2), 'H': ('hair', 5)}),
    # a worker in a stained jacket and jeans
    'work': ([
        "....hhhhhhhh....",
        "....hhHHhhhh....",
        "....hsSSSssh....",
        "....skwSskwd....",
        "....rssSsssd....",
        "....rdkddkdd....",
        "....sbmmmmsd....",
        "....dbmmbbdd....",
        "jJjjJjjsjjjJjjjj",
        "jJjjJjjsjjjJjjej",
        "jJjjJjjjjjjJjjej",
        "jJjjjjjjgjjjJjej",
        "jjjjgjjjjjjjJjjj",
        "jjjjjjjjjgjjjjjj",
        "jjjjjjjjjjjjjjjj",
        "jjjjgjjjjjjjjjdj",
        "jjjjjjjjjjjjjjdj",
        "ssjjjjjjjjjjjssd",
        "sssdjjjjjjjjjsbd",
        "srdd.jjjjjjjdssd",
        "....uuuuUuuu....",
        "....uUuuuUuu....",
        "....uUuu.Uuu....",
        "....uUuu.Uuu....",
        "....uUuu.Ubu....",
        "....uUuu.Uuu....",
        "....uUuu.Uuu....",
        "....uUuu.Uuu....",
        "....uuuu.uuu....",
        "....uuuu.uuu....",
        "....zzzz.zzz....",
        "....zZzz.zZz....",
    ], {'j': ('work', 3), 'J': ('work', 5), 'e': ('work', 1), 'g': ('work', 1), 'u': ('denim', 3),
        'U': ('denim', 5), 'z': ('shoe', 2), 'Z': ('shoe', 4), 'h': ('hair', 2), 'H': ('hair', 4)}),
}
ZCOMMON = {'s': ('zskin', 5), 'S': ('zskin', 7), 'd': ('zskin', 3), 'k': ('zskin', 1), 'w': ('sclera', 4),
           'm': ('mouth', 0), 'b': ('blood', 3), 'r': ('rot', 4), '.': None}


def zombie_front(kind, level=1):
    rows, leg = ZOMBIES[kind]
    cv = Canvas(16, 32, level)
    L = dict(ZCOMMON)
    L.update(leg)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            v = L.get(ch)
            if v is None:
                continue
            cv.mat[j, i] = cv.mi(v[0])
            cv.lv[j, i] = v[1]
    cv.ink = False
    return cv


# ---------------------------------------------------------------------------------------------------------------
# THE BAT DEVIL
# A huge, muscular, human-shaped bat: about 3 blocks tall, the wings about 8.5 blocks across. Dark grey; a white
# mane round the head and over the shoulders; a grotesque, human-like face with large round eyes, a squashed bat
# nose and a wide mouth of sharp teeth with a long tongue; big claws. The arms are the wings' leading edge; the
# membrane runs down to its flanks and legs.
# ---------------------------------------------------------------------------------------------------------------
BW, BH = 144, 64

BFACE_1 = [
    "..................",
    "..-..........-....",
    ".-dd-......-dd-...",
    ".kEEEk....kEEEEk..",
    "kEEEEEk..kEEEEEEk.",
    "kEEpEEk..kEEEpEEk.",
    "kEEEEEk..kEEEEEEk.",
    ".kEEEk....kEEEEk..",
    "..kkk..-..-kkkk...",
    "......kNNk........",
    ".....kn..nk.......",
    "......kkkk........",
    "..kkkkkkkkkkkkkk..",
    ".kTgTgTgTgTgTgTgk.",
    ".kmmmmmmmmmmmmmmk.",
    "..kmmmmGGGmmmmmk..",
    "..ktTtgGGGgtTtTk..",
    "...kkkkGGGkkkkk...",
    ".......kGGk.......",
    "........kk........",
]


def bface_legend(level):
    return {
        '-': ('+', -1), 'd': ('+', -2), 'k': ('face', 0), 'E': ('beye', 4), 'p': ('pupil', 0),
        'N': ('face', 5), 'n': ('face', 0), 'T': ('tooth', 5), 't': ('tooth', 3), 'g': ('gum', 3),
        'm': ('mouth', 0), 'G': ('gum', 4),
    }


def scallop(a, b, sag):
    """The wing's trailing edge between two finger tips: a curve bowed in toward the body."""
    ax, ay = a
    bx, by = b
    mx, my = (ax + bx) / 2, (ay + by) / 2
    dx, dy = bx - ax, by - ay
    L = math.hypot(dx, dy)
    nx, ny = -dy / L, dx / L
    c = (mx + nx * sag, my + ny * sag)
    return [((1 - t) ** 2 * ax + 2 * (1 - t) * t * c[0] + t * t * bx,
             (1 - t) ** 2 * ay + 2 * (1 - t) * t * c[1] + t * t * by) for t in np.linspace(0, 1, 9)]


def wing(cv, side, sh, el, wr, tips, root, base, level, thumb=True):
    """One wing, hand-placed: shoulder, elbow, wrist, the finger tips, and where the membrane joins the body."""
    s = side
    # the membrane: leading edge along the arm, then the scalloped trailing edge between finger tips, then the body
    pts = [sh, el, wr, tips[0]]
    for a, b in zip(tips[:-1], tips[1:]):
        pts += scallop(a, b, -4.0 * s if level < 3 else -4.5 * s)[1:]
    pts += scallop(tips[-1], root, -5.0 * s)[1:]
    m, h = cv.poly(pts, rz=2.5, bevel=6)
    # the membrane sags between the fingers: lower in the middle of each panel
    cv.add((m, h), base, 'memb', rim=True)
    # the bones: upper arm, forearm (thick, muscled), and the long fingers
    cv.add(cv.tube([sh, ((sh[0] + el[0]) / 2, (sh[1] + el[1]) / 2 - 1.5), el], 3.6, 2.6), base + 6, 'fur')
    cv.add(cv.tube([el, ((el[0] + wr[0]) / 2, (el[1] + wr[1]) / 2 - 1), wr], 2.6, 1.8), base + 6, 'fur')
    for t in tips:
        mid = ((wr[0] + t[0]) / 2, (wr[1] + t[1]) / 2 - 1.0)
        cv.add(cv.tube([wr, mid, t], 1.4, 0.8), base + 4, 'fur')
    if thumb:
        cv.add(cv.tube([wr, (wr[0] - 1 * s, wr[1] - 3), (wr[0] - 3 * s, wr[1] - 5)], 1.6, 0.6), base + 7, 'claw')


def bat_devil(level=1, view='front'):
    if view == 'side':
        return bat_devil_side(level)
    if view == 'action':
        return bat_devil_dive(level)
    cv = Canvas(BW, BH, level)
    cx = 72
    # --- wings (left then mirrored right, with small asymmetries so it is alive, not a logo)
    wing(cv, 1, (62, 30), (47, 21), (28, 14), [(9, 6), (3, 22), (11, 37), (25, 44)], (63, 52), 2, level)
    wing(cv, -1, (82, 30), (97, 22), (116, 15), [(135, 7), (141, 23), (133, 38), (119, 45)], (81, 52), 2, level)
    # --- legs: short and bowed, clawed feet
    for s in (1, -1):
        hip = (cx - 5 * s, 46)
        cv.add(cv.tube([hip, (cx - 9 * s, 52), (cx - 7 * s, 58)], 3.6, 2.4), 8, 'fur')
        for k in (-2, 0, 2):
            cv.add(cv.tube([(cx - 7 * s + k, 58), (cx - 7 * s + k * 1.5, 61.5)], 1.0, 0.5), 9, 'claw')
    # --- the body: a hulking chest, a muscled belly
    cv.add(cv.blob([(cx, 36, 11, 8), (cx, 43, 8, 6, 0.9), (cx - 9, 31, 5, 4, 0.7), (cx + 9, 31, 5, 4, 0.7)], rz=10), 10, 'fur')
    cv.add(cv.ell(cx - 5, 35, 5.5, 4.5, 5), 14, 'fur')
    cv.add(cv.ell(cx + 5, 35, 5.5, 4.5, 5), 14, 'fur')
    # --- the mane: a white ruff round the head and over the shoulders
    mane = [(cx, 23, 15, 9), (cx - 9, 27, 8, 6, 0.8), (cx + 9, 27, 8, 6, 0.8), (cx, 13, 12, 7, 0.7)]
    cv.add(cv.blob(mane, rz=9), 16, 'mane')
    # --- ears: tall, pointed, set wide
    cv.add(cv.poly([(61, 13), (55, 1), (58, 1), (67, 9)], rz=3, bevel=2), 17, 'face')
    cv.add(cv.poly([(83, 13), (89, 1), (86, 1), (77, 9)], rz=3, bevel=2), 17, 'face')
    # --- the face
    cv.add(cv.ell(cx, 19, 9.5, 10.5, 8), 22, 'face')
    cv.shade(hard=0.3 if level >= 2 else 0.0)

    # mane tufts round the outside: hand-placed points of hair
    tufts = [(57, 16, -1, -1), (56, 22, -1, 0), (57, 28, -1, 1), (60, 33, -1, 1), (65, 34, 0, 1),
             (87, 16, 1, -1), (88, 22, 1, 0), (87, 28, 1, 1), (84, 33, 1, 1), (79, 34, 0, 1), (66, 7, -1, -1), (78, 7, 1, -1)]
    for x, y, dx, dy in tufts:
        cv.put(x + dx, y + dy, 'mane', 4)
        cv.put(x + 2 * dx, y + 2 * dy, 'mane', 3)
    # hair strands inside the mane
    for pts in ([(62, 22), (60, 26)], [(64, 27), (62, 31)], [(82, 22), (84, 26)], [(80, 27), (82, 31)],
                [(68, 31), (67, 34)], [(76, 31), (77, 34)]):
        cv.line(pts, 'mane', 2)
    # inner ears
    cv.line([(58, 3), (61, 8), (63, 11)], 'gum', 2)
    cv.line([(86, 3), (83, 8), (81, 11)], 'gum', 2)
    cv.sprite(BFACE_1, cx - 9, 9, bface_legend(level))
    # pecs and abs
    cv.crease([(68, 39), (72, 40), (76, 39)], only={'fur'})
    cv.crease([(70, 43), (74, 43)], only={'fur'})
    cv.line([(72, 40), (72, 48)], 'fur', 1)
    # finger joints and membrane veins
    return cv


def bat_devil_side(level=1):
    cv = Canvas(64, BH, level)
    # folded wing: the forearm down its side, fingers folded, membrane draped like a cloak
    cv.add(cv.poly([(22, 24), (12, 30), (8, 46), (12, 56), (22, 52), (30, 40)], rz=3, bevel=5), 2, 'memb')
    cv.add(cv.tube([(26, 26), (16, 30), (12, 44)], 3.0, 2.0), 8, 'fur')
    cv.add(cv.tube([(12, 44), (10, 52)], 1.4, 0.8), 8, 'fur')
    for s in (0,):
        cv.add(cv.tube([(30, 46), (36, 52), (32, 58)], 3.6, 2.4), 6, 'fur')
        for k in (0, 2, 4):
            cv.add(cv.tube([(32 + k, 58), (34 + k * 1.3, 61.5)], 1.0, 0.5), 7, 'claw')
    cv.add(cv.blob([(30, 36, 9, 10), (31, 44, 7, 6, 0.9), (36, 32, 5, 5, 0.7)], rz=10), 10, 'fur')
    cv.add(cv.blob([(30, 22, 11, 9), (34, 30, 8, 6, 0.8)], rz=9), 14, 'mane')
    cv.add(cv.poly([(33, 13), (30, 0), (33, 1), (38, 11)], rz=3, bevel=2), 15, 'face')
    cv.add(cv.ell(40, 19, 8, 9.5, 8), 18, 'face')
    cv.add(cv.ell(47, 21, 3.5, 3, 4), 24, 'face')
    cv.shade(hard=0.3 if level >= 2 else 0.0)
    prof = [
        "..-dd....",
        ".kEEEk...",
        "kEEEpEk..",
        "kEEEEEk..",
        ".kEEEk.kk",
        "..kkk...k",
        "........k",
        ".....kkkk",
        "....kTgTk",
        "....kmmmk",
        ".....kmGG",
        "....ktTk.",
        ".....kk..",
    ]
    cv.sprite(prof, 41, 13, bface_legend(level))
    cv.line([(31, 2), (33, 7), (35, 11)], 'gum', 2)
    return cv


def bat_devil_dive(level=1):
    """Diving at you from above: wings flung up in a V, the face rushing in, mouth wide, tongue out, claws first."""
    cv = Canvas(128, 96, level)
    cx = 64
    wing(cv, 1, (52, 52), (36, 34), (22, 12), [(4, 3), (2, 26), (10, 46), (24, 60)], (54, 66), 2, level)
    wing(cv, -1, (76, 52), (92, 34), (106, 12), [(124, 3), (126, 26), (118, 46), (104, 60)], (74, 66), 2, level)
    # legs trailing behind, claws spread at you
    for s in (1, -1):
        cv.add(cv.tube([(cx - 6 * s, 64), (cx - 12 * s, 74), (cx - 14 * s, 82)], 3.6, 2.6), 8, 'fur')
        for k in (-2, 0, 2):
            cv.add(cv.tube([(cx - 14 * s + k, 82), (cx - 14 * s + k * 1.8, 87)], 1.2, 0.5), 9, 'claw')
    cv.add(cv.blob([(cx, 58, 12, 9), (cx, 66, 8, 6, 0.9)], rz=10), 10, 'fur')
    cv.add(cv.blob([(cx, 44, 17, 11), (cx - 10, 50, 9, 6, 0.8), (cx + 10, 50, 9, 6, 0.8), (cx, 32, 13, 7, 0.7)], rz=9), 16, 'mane')
    cv.add(cv.poly([(53, 33), (44, 18), (47, 18), (58, 28)], rz=3, bevel=2), 17, 'face')
    cv.add(cv.poly([(75, 33), (84, 18), (81, 18), (70, 28)], rz=3, bevel=2), 17, 'face')
    cv.add(cv.ell(cx, 40, 11, 12, 9), 22, 'face')
    cv.shade(hard=0.3 if level >= 2 else 0.0)
    cv.sprite(BFACE_1, cx - 9, 30, bface_legend(level))
    return cv


# ---------------------------------------------------------------------------------------------------------------
# THE REFINED PASSES (2 and on). The front of each devil is the master (it is the texture the cuboids carry); the
# side and the action pose are built from the same parts, so each pass's fixes reach them too.
# ---------------------------------------------------------------------------------------------------------------

# The face on the Zombie Devil's chest, pass 2 on (20 x 26), one of its zombies' grey dead faces: sunken eyes (the
# left wide and staring, the right drooping), no nose left but two slits, a jaw hanging open in a moan, a few hairs.
ZFACE_2 = [
    "....h...h..h...h....",
    "...h...h....h...h...",
    "....................",
    "...-...--..--...-...",
    "....--........--....",
    "..ddddd......ddddd..",
    "..dKKKKd....dKKKKd..",
    "..KkWWwK....KkkkkK..",
    ".dKWWpWK...dKwWpWKd.",
    "..KwWWwK....KwWWwK..",
    "..dKrrKd....dKrrKd..",
    "...-ddd-....-ddd-...",
    ".........-.-........",
    "........k...k.......",
    ".........k.k........",
    "...-.....---.....-..",
    "....-..........-....",
    ".....kgTgTTgTgk.....",
    ".....kTmmmmmmTk.....",
    "....kmmMMMMMMmmk....",
    "....kmMMMMMMMMmk....",
    "....kmmMMMMMMmmk....",
    ".....kTmmmmmmtk.....",
    ".....ktTgtTgtTk.....",
    "......kkkkkkkk......",
    "........----........",
]


def zface2_legend(level):
    return {'d': ('+', -2), '-': ('+', -1), '+': ('+', 1), 'k': ('zskin', 0), 'K': ('zskin', 1),
            'W': ('sclera', 3), 'w': ('sclera', 2), 'p': ('pupil', 0), 'r': ('blood', 3), 'T': ('tooth', 4),
            't': ('tooth', 2), 'g': ('gum', 2), 'm': ('mouth', 0), 'M': ('mouth', 1), 'h': ('hair', 1)}


# The brain's folds, hand-placed meanders (front coordinates). Each is a sulcus: a dark groove, the fold above it
# shaded, the fold below it lit.
# Pass 3: smoother folds, S-curves of varying size instead of a zigzag grid.
SULCI3 = [
    [(21, 9), (24, 7.5), (27, 9), (29, 11), (32, 10), (34, 7.5), (37, 7)],
    [(20, 14), (22, 13), (25, 14.5), (28, 16.5), (31, 15), (33, 12.5), (36, 12), (39, 13)],
    [(22, 20), (25, 19), (28, 20.5), (31, 21), (34, 19), (37, 18.5), (39, 19.5)],
    [(25, 4.5), (28, 3.5), (31, 4)],
    [(29, 11), (28, 13)], [(33, 12.5), (34, 10)], [(25, 14.5), (24, 17)], [(34, 19), (33, 17)],
    [(43, 6), (45, 5), (48, 6.5), (50, 8.5), (53, 7), (55, 5.5), (57, 7)],
    [(42, 11.5), (45, 11), (47, 12.5), (50, 14), (53, 12), (56, 11), (59, 12.5)],
    [(43, 18), (46, 17), (48, 18.5), (51, 19), (54, 17), (57, 17.5)],
    [(46, 2.5), (49, 2), (52, 3)],
    [(50, 8.5), (51, 10.5)], [(47, 12.5), (46, 15)], [(53, 12), (54, 9.5)], [(54, 17), (55, 15)],
]
# Pass 3: torn flaps of neck skin lying over the brain's base, in place of a collar.
FLAPS = [[(27, 25), (29.5, 19.5), (32, 25)], [(32, 24), (35, 20.5), (37.5, 24.5)], [(37, 24.5), (39.5, 22), (42, 24.5)],
         [(42, 24.5), (44.5, 19.5), (47, 24.5)], [(47, 24.5), (50.5, 20.5), (53, 25)]]
SULCI = [
    [(21, 10), (24, 8), (27, 10), (30, 7), (33, 9), (36, 6), (38, 8)],
    [(20, 15), (23, 13), (26, 16), (29, 13), (32, 15), (35, 12), (38, 14)],
    [(21, 20), (25, 18), (27, 21), (31, 19), (34, 21), (37, 18), (39, 20)],
    [(26, 4), (29, 3), (32, 4)],
    [(27, 10), (26, 12)], [(32, 15), (33, 17)], [(30, 7), (30, 5)], [(25, 18), (24, 16)], [(35, 12), (35, 10)],
    [(43, 7), (46, 5), (49, 8), (52, 5), (55, 7), (57, 9)],
    [(42, 12), (45, 10), (48, 13), (51, 10), (54, 12), (58, 13)],
    [(43, 18), (46, 16), (49, 19), (52, 16), (55, 18), (58, 18)],
    [(47, 2), (50, 3), (53, 2)],
    [(49, 8), (49, 10)], [(51, 10), (52, 8)], [(46, 16), (45, 14)], [(52, 16), (53, 14)], [(55, 7), (55, 5)],
]
# Marbling: the dark branching veins that show through a rotting corpse's skin.
MARBLING = [
    [(34, 30), (36, 33), (35, 37)], [(36, 33), (39, 34)],
    [(45, 45), (47, 49), (46, 53)], [(47, 49), (51, 50)],
    [(62, 39), (64, 43), (63, 47)], [(64, 43), (66, 45)],
    [(19, 35), (17, 39)],
]
STRETCH = [[(23, 47), (24, 51)], [(25, 52), (26, 55)], [(54, 46), (53, 50)], [(56, 50), (54, 54)]]


def zd2(level=2, view='front'):
    action = view == 'action'
    W_ = 112 if action else ZW
    cv = Canvas(W_, ZH, level)
    T = Tf(-10, (40, 60), (16, -1)) if action else ID
    P = T.p
    da = T.deg()
    ox = 16 if action else 0
    root = lambda pts: [P(*pts[0])] + [(x + ox, y) for x, y in pts[1:]]
    E = lambda e: P(e[0], e[1]) + tuple(e[2:4]) + ((e[4] if len(e) > 4 else 1.0), da)
    q = lambda x, y: tuple(int(round(v)) for v in P(x, y))

    # --- guts planted behind, darkest
    back = [([(27, 55), (21, 64), (17, 72), (15, 78.5)], 3.8, 2.8), ([(55, 55), (61, 64), (64, 72), (67, 78.5)], 3.8, 2.8)]
    if action:
        back = [([(27, 55), (19, 63), (12, 71), (9, 78.5)], 3.8, 2.8), ([(55, 55), (64, 62), (72, 70), (78, 78.5)], 3.8, 2.8)]
    L3 = level >= 3
    GUT = 'gut3' if L3 else 'gut'
    ROT = 'rot3' if L3 else 'rot'
    for pts, r0, r1 in back:
        cv.add(cv.tube(root(pts), r0, r1, seg=0.25, seglen=4.5), 0, 'meat' if L3 else 'gut')
        cv.add(cv.ell(pts[-1][0] + ox, 78.5, r1 + 2, 1.6, 1.5), 1, 'meat' if L3 else 'gut')
    # --- the corpse: a hunched shoulder mass, the bloated trunk, the belly sagging in front
    if L3:
        # pass 3: a pear, not a box: the shoulders slope from the neck, the belly is widest and sags low
        cv.add(cv.blob([E((40, 30, 19, 6))], rz=12, prof=1.0), 6, 'skin')
        L4 = level >= 4
        cv.add(cv.blob([E(e) for e in ((40, 37, 20, 10), (39, 47, 25, 12), (38, 55, 24, 8))],
                       rz=40 if L4 else 26, prof=0.8), 10, 'skin')
        cv.add(cv.ell(*P(38, 51), 21, 9.5, 22 if L4 else 13, da), 18, 'skin')
        # pass 4: the stumps are bigger, hanging bulges, so the shoulders read
        stumps = ((17, 35, 25, 8, 6.5), (63, 34, -25, 8, 6.5)) if L4 else ((18, 34, 25, 7, 5.5), (62, 33, -25, 7, 5.5))
        for sx, sy, a, rx, ry in stumps:
            cv.add(cv.ell(*P(sx, sy), rx, ry, 9, da + a), 16, 'skin')
    else:
        cv.add(cv.blob([E((40, 30, 27, 7))], rz=14), 6, 'skin')
        cv.add(cv.blob([E(e) for e in ((40, 36, 23, 10), (39, 46, 25, 12), (38, 53, 21, 8))], rz=26), 10, 'skin')
        cv.add(cv.ell(*P(39, 49), 19, 9.5, 12, da), 18, 'skin')
        for sx, sy in ((14, 31), (66, 30)):
            cv.add(cv.ell(*P(sx, sy), 7.5, 6.5, 8, da), 16, 'skin')
    br = [(31, 14, 12, 11.5), (49, 13, 11, 11), (40, 21, 12, 6, 0.8)]
    cv.add(cv.blob([E(e) for e in br], rz=14, prof=0.7 if L3 else 0.5), 15, 'brain')
    if L3:
        for fl in FLAPS:
            cv.add(cv.poly([P(*p) for p in fl], rz=3 if level >= 4 else 2, bevel=1.2), 26 if level >= 4 else 22, 'skin')
    else:
        cv.add(cv.ell(*P(40, 25.5), 12.5, 4.5, 5, da), 21, 'skin')
    fcx, fcy = P(25, 43)
    cv.add(cv.ell(fcx, fcy, 10, 13, 10, da), 24, 'zskin')
    rcx, rcy = P(53, 40) if L3 else P(54, 38)
    cv.add(cv.ell(rcx, rcy, 8, 7, 2, da), 27, 'meat')
    cv.add(cv.ell(*P(41, 59), 11, 3, 2, da), 17.5, 'meat')
    # --- the guts in front
    front = [
        ([(36, 58), (33, 64), (30, 71), (28, 78.5)], 4.4, 3.3, 0.25),
        ([(45, 58), (47, 65), (49, 72), (51, 78.5)], 4.4, 3.3, 0.25),
        ([(41, 59), (40, 64), (41, 70), (43, 73)], 2.6, 1.8, 0.18),
        ([(32, 57), (24, 62), (14, 66), (6, 64), (3, 59)], 3.0, 1.5, 0.2),
        ([(50, 58), (60, 63), (69, 69), (76, 68), (79, 64)], 3.0, 1.4, 0.2),
    ]
    if action:
        front = [
            ([(36, 58), (31, 64), (26, 71), (22, 78.5)], 4.4, 3.3, 0.25),
            ([(45, 58), (49, 65), (54, 72), (58, 78.5)], 4.4, 3.3, 0.25),
            ([(41, 59), (40, 64), (42, 69), (45, 72)], 2.6, 1.8, 0.18),
            ([(31, 59), (18, 64), (8, 56), (4, 42), (8, 30)], 3.4, 1.6, 0.2),
            ([(51, 58), (66, 62), (78, 56), (86, 44), (84, 32)], 3.4, 1.6, 0.2),
        ]
    for k, (pts, r0, r1, sg) in enumerate(front):
        cv.add(cv.tube(root(pts), r0, r1, seg=sg, seglen=4.5), 30 - k, GUT)
        if k < 2:
            cv.add(cv.ell(pts[-1][0] + ox, 78.5, r1 + 2, 1.6, 1.5), 22 - k, GUT)
    # a dark organ hanging out of the wound, and a thin vessel
    cv.add(cv.ell(*P(47, 63), 3.5, 3.2, 3, da), 31, 'meat')
    cv.add(cv.tube(root([(38, 60), (37, 66), (35, 69)]), 1.0, 0.6), 32, 'blood')

    cv.shade(hard=0.5, gamma=1.2, falloff=2 if L3 else 0, soft=level >= 4)

    # ---- features
    # the brain: the great fissure, the folds
    cv.line([q(40, 3), q(41, 7), q(40, 11), q(41, 15), q(40, 20)], 'brain', 0)
    for pts in (SULCI3 if L3 else SULCI):
        cv.crease([q(*p) for p in pts], deep=1, lip=1, over=-1, only={'brain'})
    # the torn collar: flaps of skin pointing up round the brain, a wet red rim where they meet
    if not L3:
        for x0 in (29, 32, 36, 43, 47, 51):
            cv.put(*q(x0, 21), 'skin', 5)
            cv.put(*q(x0 + 1, 21), 'skin', 4)
            cv.put(*q(x0, 20), 'skin', 4)
        for x0 in range(28, 53):
            if x0 % 3:
                cv.put(*q(x0, 22), 'meat', 3)
    else:
        # the wet red rim where the brain bulges out between the flaps
        for x0 in range(27, 54):
            X, Y = q(x0, 24)
            for yy in range(Y - 3, Y + 2):
                if 0 <= yy < cv.h and cv.mat[yy, X] == cv.mi('brain') and cv.mat[yy + 1, X] == cv.mi('skin'):
                    cv.put(X, yy, 'meat', 3 if x0 % 2 else 2)
    # the face, and the skin torn open round it where it pushes out of the chest
    fx, fy = int(round(fcx)) - 10, int(round(fcy)) - 13
    ring = (mask_ell(cv, fcx, fcy, 11.6, 14.4, da) & ~mask_ell(cv, fcx, fcy, 10.4, 13.2, da)
            & (cv.mat == cv.mi('skin')))
    cv.lv = np.where(ring, 0, cv.lv).astype(np.int16)
    low = ring & (cv.ys > fcy + 3)
    cv.remat(low, 'meat')
    cv.lv = np.where(low, 2, cv.lv).astype(np.int16)
    cv.sprite(ZFACE_2, fx, fy, zface2_legend(level))
    # the rib wound: a dark cavity, torn lips, four ribs curving round the body
    cav = mask_ell(cv, rcx, rcy, 7.4, 6.4, da) & (cv.mat == cv.mi('meat'))
    cv.lv = np.where(cav, np.maximum(cv.lv - 2, 0), cv.lv).astype(np.int16)
    lip = (mask_ell(cv, rcx, rcy, 9.2, 8.2, da) & ~mask_ell(cv, rcx, rcy, 8.0, 7.0, da)
           & (cv.mat == cv.mi('skin')))
    cv.lv = np.where(lip & (cv.ys < rcy), np.minimum(cv.lv + 2, 7), cv.lv).astype(np.int16)
    cv.lv = np.where(lip & (cv.ys >= rcy), 1, cv.lv).astype(np.int16)
    for k, yy in enumerate((33.5, 37, 40.5, 44)):
        pts = [(47.5, yy + 1.5), (51, yy), (56, yy - 0.3), (60.5, yy + 1)]
        if L3:
            yy += 2
            pts = [(46.5, yy + 1.5), (50, yy), (55, yy - 0.3), (59, yy + 1.2)]
        cv.line([q(*p) for p in pts], 'bone', 5 - (k > 1))
        cv.line([q(x, y + 1) for x, y in pts], 'bone', 3 - (k > 1))
    # the stumps: torn meat and a broken bone poking out
    if L3:
        # pass 3: the stumps droop; the torn end has a ragged skin edge, the meat, and the broken bone
        for sx, sy, d in (((17, 35, -1), (63, 34, 1)) if level >= 4 else ((18, 34, -1), (62, 33, 1))):
            ex, ey = sx + d * (7 if level >= 4 else 6), sy + 3
            for k in range(-3, 4):
                cv.put(*q(ex + (k > 1) * d, ey + k), 'meat', 3 + (k < 0))
                cv.put(*q(ex - d + (k > 1) * d, ey + k), 'meat', 1 if abs(k) == 3 else 2)
            for bx, by, lv in ((1, 0, 5), (2, 0, 5), (3, 1, 4), (2, -1, 6), (3, -1, 5), (4, 0, 4), (1, 1, 3), (2, 1, 3)):
                cv.put(*q(ex + d * bx, ey + by), 'bone', lv)
            for k in (-4, -2, 1):
                cv.put(*q(ex - d * 2, ey + k), 'skin', 1)
    for sx, sy, d in (() if L3 else ((14, 31, -1), (66, 30, 1))):
        ex = sx + d * 7
        for yy in range(sy - 3, sy + 4):
            cv.put(*q(ex, yy), 'meat', 3 + (yy < sy))
            cv.put(*q(ex - d, yy), 'meat', 2 if abs(yy - sy) < 3 else 1)
        for bx, by, lv in ((1, 0, 5), (2, 0, 5), (2, -1, 4), (3, -1, 6), (1, 1, 3), (2, 1, 3)):
            cv.put(*q(ex + d * bx, sy + by), 'bone', lv)
    # livor mortis: the blood pooled purple in the lowest skin
    if level >= 4:
        # pass 4: only the underside, so it reads as pooled blood, not a purple belt
        liv = mask_ell(cv, *P(39, 62), 24, 4.5, da) & (cv.mat == cv.mi('skin')) & (cv.lv <= 3)
        cv.remat(liv, 'bruise', -1)
        # the fat roll under the chest, folds at the flanks, and the sag of the belly
        cv.crease([q(24, 41), q(30, 42.5), q(37, 42), q(44, 41.5), q(51, 42.5), q(58, 41)], deep=1, lip=1, over=-1,
                  only={'skin', 'rot3'})
        for pts in ([(16, 48), (18, 52), (21, 55)], [(62, 47), (60, 51), (57, 54)], [(22, 57), (30, 59.5), (38, 60)]):
            cv.crease([q(*p) for p in pts], deep=1, lip=1, only={'skin', 'rot3', 'bruise'})
    else:
        cv.remat(mask_ell(cv, *P(39, 60), 23, 6, da) & (cv.mat == cv.mi('skin')), 'bruise')
    # rot round the wounds and at the shoulders
    rots = ((54, 41, 11, 9.5), (17, 47, 3.5, 6)) if level >= 4 else \
        ((54, 41, 11, 9.5), (56, 29, 5, 2.5), (17, 47, 3.5, 6), (14, 36, 4, 4)) if L3 else \
        ((55, 39, 11, 9.5), (58, 27, 6, 2.5), (16, 45, 3.5, 6), (12, 31, 4, 4))
    for ex, ey, rx, ry in rots:
        cv.remat(mask_ell(cv, *P(ex, ey), rx, ry, da) & (cv.mat == cv.mi('skin')), ROT)
    for pts in MARBLING:
        cv.line([q(*p) for p in pts], 'vein', 2)
    for pts in STRETCH:
        for x, y in pixline([q(*p) for p in pts]):
            cv.rel(x, y, 1, only={'skin', 'bruise'})
    # the navel, a dark slit
    cv.put(*q(40, 53), None, 0)
    cv.put(*q(41, 53), None, 0)
    cv.rel(*q(40, 54), 1)
    return cv


def zd2_side(level=2):
    cv = Canvas(72, ZH, level)
    GUT = 'gut3' if level >= 3 else 'gut'
    ROT = 'rot3' if level >= 3 else 'rot'
    cv.add(cv.tube([(30, 56), (22, 66), (16, 78.5)], 3.8, 2.8, seg=0.25, seglen=4.5), 0, GUT)
    cv.add(cv.ell(16, 78.5, 4.6, 1.6, 1.5), 1, GUT)
    cv.add(cv.blob([(28, 32, 13, 11), (35, 46, 17, 13), (33, 54, 13, 7, 0.9), (24, 27, 10, 6, 0.8)], rz=36 if level >= 4 else 24, prof=0.8 if level >= 3 else 0.5), 10, 'skin')
    cv.add(cv.ell(41, 49, 12, 9, 10), 18, 'skin')
    cv.add(cv.ell(28, 31, 6.5, 6.5, 7), 20, 'skin')
    cv.add(cv.ell(28, 31, 4.6, 4.6, 2), 27.5, 'meat')
    cv.add(cv.blob([(34, 14, 13, 11), (28, 20, 10, 7, 0.8)], rz=14), 15, 'brain')
    cv.add(cv.ell(30, 24.5, 9, 4, 5, -12), 21, 'skin')
    cv.add(cv.ell(52, 41, 7, 11, 8, -8), 24, 'zskin')
    cv.add(cv.ell(46, 58, 6, 3, 2), 19, 'meat')
    for k, (pts, r0, r1) in enumerate((
            ([(46, 58), (50, 66), (54, 73), (56, 78.5)], 4.4, 3.3),
            ([(42, 58), (38, 66), (34, 73), (32, 78.5)], 4.4, 3.3),
            ([(49, 57), (58, 61), (66, 60), (70, 55)], 2.8, 1.4))):
        cv.add(cv.tube(pts, r0, r1, seg=0.25, seglen=4.5), 30 - k, GUT)
        if k < 2:
            cv.add(cv.ell(pts[-1][0], 78.5, r1 + 2, 1.6, 1.5), 22 - k, GUT)
    cv.shade(hard=0.5, gamma=1.2, falloff=2 if level >= 3 else 0, soft=level >= 4)
    for pts in ([(25, 9), (29, 7), (33, 9), (37, 6), (42, 8), (45, 11)], [(23, 14), (27, 12), (31, 15), (35, 12), (40, 15), (46, 14)],
                [(24, 19), (28, 17), (31, 20), (36, 18), (41, 20), (46, 19)], [(29, 3), (33, 2), (37, 3)], [(33, 9), (32, 11)],
                [(40, 15), (40, 17)], [(36, 18), (37, 16)]):
        cv.crease(pts, over=-1, only={'brain'})
    prof = [
        "...h....",
        "..h.....",
        "........",
        "...--...",
        "..dddd..",
        "..KWWK..",
        "..KwpK..",
        "..-rr-..",
        "...---..",
        "......k.",
        ".....kk.",
        "........",
        "...kkkkk",
        "...kgTTk",
        "...kmmm.",
        "...kmMm.",
        "...kmMm.",
        "...ktTtk",
        "....kkk.",
    ]
    cv.sprite(prof, 50, 30, zface2_legend(level))
    for bx, by, lv in ((28, 31, 6), (29, 31, 5), (28, 30, 5), (27, 31, 4), (28, 32, 3)):
        cv.put(bx, by, 'bone', lv)
    cv.remat(mask_ell(cv, 36, 60, 18, 5) & (cv.mat == cv.mi('skin')), 'bruise')
    cv.remat(mask_ell(cv, 24, 28, 6, 4) & (cv.mat == cv.mi('skin')), ROT)
    for pts in ([(36, 40), (38, 44), (37, 48)], [(38, 44), (42, 45)], [(22, 36), (20, 41)]):
        cv.line(pts, 'vein', 2)
    return cv


# The Bat Devil's face, pass 2 on (24 x 29): a heavy wrinkled brow, two large round bulging eyes with pin pupils
# (the eyeballs are shaded spheres; this only rings them), a spear-shaped nose leaf over a squashed, upturned bat
# nose, and a mouth cut from cheek to cheek: gums, two rows of needle teeth, longer fangs, the long tongue out.
BFACE_2 = [
    "........................",
    "..-..-.-........-.-..-..",
    "...--.-..-....-..-.--...",
    "..dddddddd....dddddddd..",
    "..d.kkkkk.d..d.kkkkk.d..",
    "..-kEErEEk-..-kEEErEk-..",
    "...kEEEEEk....kEEEEEk...",
    "...kEEEEpk.kk.kpEEEEk...",
    "...kEEEEEk.NN.kEEEEEk...",
    "...kErEEEk.NN.kEEErEk...",
    "..-.kkkkk.kNNk.kkkkk.-..",
    "...-ddd-.kkNNkk.-ddd-...",
    "........kNNNNNNk........",
    ".......kNnnNNnnNk.......",
    "..-.....kkk..kkk.....-..",
    "...-................-...",
    ".kkkkkkkkkkkkkkkkkkkkkk.",
    ".kGGGGGGGGGGGGGGGGGGGGk.",
    ".kTTTTTTTTTTTTTTTTTTTTk.",
    ".kTmTmTmTmTmTmTmTmTmTmk.",
    "..kmTmmmmmmLLmmmmmmTmk..",
    "..kmtmtmtmLLLLmtmtmtmk..",
    "...ktttttLLLLLLtttttk...",
    "....kGGGGLLLLLLGGGGk....",
    "......kkkLLLLLLkkk......",
    ".........LLLLLL.........",
    "..........LLLL..........",
    "..........LLLL..........",
    "...........LL...........",
]
# Diving, the jaw drops further: the same face with the mouth gaping.
BFACE_DIVE = BFACE_2[:19] + [
    "..kmTmmmmmmmmmmmmmmTmk..",
    "..kmmmmmmmmmmmmmmmmmmk..",
    "..kmmmmmmmLLLLmmmmmmmk..",
    "..kmtmtmtLLLLLLmtmtmtk..",
    "...ktttttLLLLLLtttttk...",
    "....kGGGGLLLLLLGGGGk....",
    "......kkkLLLLLLkkk......",
    ".........LLLLLL.........",
    "..........LLLL..........",
    "..........LLLL..........",
    "...........LL...........",
]


def _eyes_rows():
    L = [".kkkkk.", "kEErEEk", "kEEEEEk", "kEEEEpk", "kEEEEEk", "kErEEEk", ".kkkkk."]
    R = [".kkkkk.", "kEEErEk", "kEEEEEk", "kpEEEEk", "kEEEEEk", "kEEErEk", ".kkkkk."]
    G = ["...", "...", ".k.", ".N.", "kNk", "kNk", "kNk"]
    pre = ["..d", "..-", "...", "...", "...", "..-", "..-"]
    suf = ["d.", "-.", "..", "..", "..", "-.", "-."]
    return [a + b + c + d + e for a, b, c, d, e in zip(pre, L, G, R, suf)]


# Pass 3: the face is a tall oval with the mouth inside it (22 x 29): brow, eyes, nose leaf and squashed nose,
# nasolabial folds, then the mouth across the lower third, the chin under it, the tongue hanging over the chin.
BFACE_3 = [
    "......................",
    "...-..-.-....-.-..-...",
    "..-.--..-....-..--.-..",
    "..ddddddd...ddddddd...",
] + _eyes_rows() + [
    "....ddd...kNk..ddd....",
    "........kNNNNNk.......",
    ".......kNnnNnnNk......",
    "..-.....kkk.kkk.....-.",
    "...-...............-..",
    "..kkkkkkkkkkkkkkkkkk..",
    "..kGGGGGGGGGGGGGGGGk..",
    "..kTTTTTTTTTTTTTTTTk..",
    "..kTmTmTmTmTmTmTmTmk..",
    "...kTmmmmmLLmmmmmTk...",
    "...kmtmtmLLLLmtmtmk...",
    "....ktttLLLLLLtttk....",
    ".....kGGLLLLLLGGk.....",
    "......kkLLLLLLkk......",
    "........LLLLLL........",
    ".........LLLL.........",
    ".........LLLL.........",
    "..........LL..........",
]
BFACE_3_DIVE = BFACE_3[:19] + [
    "...kTmmmmmmmmmmmmTk...",
    "...kmmmmmmmmmmmmmmk...",
    "...kmmmmmLLLLmmmmmk...",
    "...kmtmtLLLLLLtmtmk...",
] + BFACE_3[22:]
assert all(len(r) == 22 for r in BFACE_3 + BFACE_3_DIVE)


def bface2_legend(level):
    return {'-': ('+', -1), 'd': ('+', -2), 'k': ('face', 0), 'E': None, 'r': ('blood', 3), 'p': ('pupil', 0),
            'N': ('face', 6), 'n': ('face', 0), 'T': ('tooth', 5), 't': ('tooth', 3), 'G': ('gum', 3),
            'm': ('mouth', 0), 'L': ('gum', 4)}


def bface3_legend(level):
    L = bface2_legend(level)
    L.update({'k': ('face3', 0), 'N': ('face3', 6), 'n': ('face3', 0)})
    return L


def lock(cv, root, tip, w, base, mat='mane', bend=0.0):
    """A lock of hair: a leaf from its root to a sharp tip, bent a little sideways."""
    rx, ry = root
    tx, ty = tip
    dx, dy = tx - rx, ty - ry
    L = math.hypot(dx, dy)
    nx, ny = -dy / L, dx / L
    a, b = [], []
    for t in np.linspace(0, 1, 9):
        hw = w * 0.5 * (math.sin(math.pi * min(1, t * 1.3)) ** 0.6 if t < 0.77 else (1 - t) / 0.23 * 0.7)
        bx = rx + dx * t + nx * bend * math.sin(math.pi * t)
        by = ry + dy * t + ny * bend * math.sin(math.pi * t)
        a.append((bx + nx * hw, by + ny * hw))
        b.append((bx - nx * hw, by - ny * hw))
    return cv.add(cv.poly(a + b[::-1], rz=3, bevel=1.6), base, mat)


BAT_POSES = {
    # body offset, wing joints (shoulder, elbow, wrist, finger tips, membrane root), legs (hip, knee, foot)
    'front': dict(W=BW, H=BH, off=(0, 0), face=BFACE_2,
                  wings=[((58, 33), (44, 23), (28, 15), [(9, 7), (2, 24), (10, 40), (25, 48)], (62, 55)),
                         ((86, 33), (100, 23), (116, 15), [(135, 7), (142, 24), (134, 40), (119, 48)], (82, 55))],
                  legs=[((66, 50), (61, 55), (63, 60)), ((78, 50), (83, 55), (81, 60))]),
    'dive': dict(W=128, H=96, off=(-8, 26), face=BFACE_DIVE,
                 wings=[((50, 59), (38, 38), (24, 12), [(5, 2), (1, 28), (9, 48), (24, 64)], (54, 78)),
                        ((78, 59), (90, 38), (104, 12), [(123, 2), (127, 28), (119, 48), (104, 64)], (74, 78))],
                 legs=[((58, 76), (52, 84), (50, 92)), ((70, 76), (76, 84), (78, 92))]),
}
MANE_BACK = [((62, 16), (52, 9), 4), ((61, 22), (49, 20), 4.5), ((62, 28), (50, 33), 4.5), ((64, 32), (55, 41), 4.5),
             ((68, 34), (63, 44), 4), ((72, 35), (72, 46), 4), ((76, 34), (81, 44), 4), ((80, 32), (89, 41), 4.5),
             ((82, 28), (94, 33), 4.5), ((83, 22), (95, 20), 4.5), ((82, 16), (92, 9), 4), ((67, 12), (63, 4), 3.5),
             ((77, 12), (81, 4), 3.5), ((72, 11), (72, 3), 3.5)]
# Pass 3: a full, shaggy ruff in front of the shoulders, the locks longer and overlapping.
MANE_BACK3 = [((63, 13), (51, 5), 5), ((61, 18), (47, 14), 5.5), ((61, 23), (46, 24), 5.5), ((62, 28), (48, 34), 5.5),
              ((64, 32), (53, 42), 5.5), ((67, 34), (61, 46), 5), ((72, 35), (72, 48), 5), ((77, 34), (83, 46), 5),
              ((80, 32), (91, 42), 5.5), ((82, 28), (96, 34), 5.5), ((83, 23), (98, 24), 5.5), ((83, 18), (97, 14), 5.5),
              ((81, 13), (93, 5), 5), ((68, 11), (63, 2), 4), ((76, 11), (81, 2), 4), ((72, 10), (72, 1), 4),
              ((64, 20), (54, 19), 4), ((80, 20), (90, 19), 4), ((64, 26), (55, 30), 4), ((80, 26), (89, 30), 4)]
MANE_FRONT3 = [((62, 29), (56, 39), 3.5), ((65, 31), (62, 43), 3.5), ((69, 33), (68, 44), 3), ((75, 33), (76, 44), 3),
               ((79, 31), (82, 43), 3.5), ((82, 29), (88, 39), 3.5)]
MANE_FRONT = [((63, 30), (58, 39), 3), ((67, 32), (65, 42), 3), ((77, 32), (79, 42), 3), ((81, 30), (86, 39), 3)]


def bat2(level=2, view='front'):
    if view == 'side':
        return bat2_side(level)
    pose = BAT_POSES['dive' if view == 'action' else 'front']
    cv = Canvas(pose['W'], pose['H'], level)
    L3 = level >= 3
    ox, oy = pose['off']
    B = lambda x, y: (x + ox, y + oy)
    bones = []
    # --- wings: the membrane first, then the long finger bones on it
    for s, (sh, el, wr, tips, rt) in zip((1, -1), pose['wings']):
        pts = [sh, el, wr, tips[0]]
        for a, b in zip(tips[:-1], tips[1:]):
            pts += scallop(a, b, -4.5 * s)[1:]
        pts += scallop(tips[-1], rt, -5.5 * s)[1:]
        cv.add(cv.poly(pts, rz=2.5, bevel=6), 2, 'memb')
        for t in tips:
            mid = ((wr[0] + t[0]) / 2, (wr[1] + t[1]) / 2 - 1.0)
            bones.append(cv.add(cv.tube([wr, mid, t], 1.5, 0.8), 6, 'fur'))
            if not L3:
                cv.add(cv.ell(*mid, 1.9, 1.9, 1.5), 7, 'fur')        # the knuckle
    # --- legs: thick bowed thighs, taloned feet
    for (hip, kn, ft), s in zip(pose['legs'], (1, -1)):
        cv.add(cv.tube([hip, kn, ft], 4.6, 3.2), 8, 'fur')
        for k in (-2, 0, 2):
            cv.add(cv.tube([(ft[0] + k, ft[1] + 1), (ft[0] + k * 1.6 - s * 0.5, ft[1] + 3.5)], 1.3, 0.5), 9, 'claw')
    # --- the hulking body
    if L3:
        # pass 3: one connected, V-shaped muscled trunk; the pecs and abs are folds in it, not balls on it
        cv.add(cv.blob([B(72, 33) + (17, 6, 1.0), B(72, 41) + (13, 9, 1.0), B(72, 49) + (8, 5, 0.9)], rz=16, prof=0.9), 10, 'fur')
    else:
        cv.add(cv.blob([B(72, 32) + (14, 5)], rz=8), 9, 'fur')
        cv.add(cv.blob([B(72, 42) + (13, 10, 1.0), B(72, 50) + (8, 5, 0.9)], rz=14), 10, 'fur')
        for x, y in ((69, 45), (75, 45), (69, 49.5), (75, 49.5)):
            cv.add(cv.ell(*B(x, y), 3, 2.2, 2), 13, 'fur')
        for x in (66, 78):
            cv.add(cv.ell(*B(x, 38), 7, 5, 5), 15, 'fur')
    # --- the arms (the wings' leading edge), thick with muscle
    for s, (sh, el, wr, tips, rt) in zip((1, -1), pose['wings']):
        mid = ((sh[0] + el[0]) / 2, (sh[1] + el[1]) / 2 - 1.5)
        cv.add(cv.tube([sh, mid, el], 4.6, 3.4), 12, 'fur')
        mid2 = ((el[0] + wr[0]) / 2, (el[1] + wr[1]) / 2 - 1)
        cv.add(cv.tube([el, mid2, wr], 3.4, 2.2), 12, 'fur')
        if L3:
            cv.add(cv.tube([wr, (wr[0] - 1.5 * s, wr[1] - 3.5), (wr[0] - 5 * s, wr[1] - 6.5)], 2.2, 0.6), 14, 'claw')
        else:
            cv.add(cv.tube([wr, (wr[0] - 1 * s, wr[1] - 3), (wr[0] - 4 * s, wr[1] - 5.5)], 1.8, 0.6), 14, 'claw')
        cv.add(cv.ell(sh[0], sh[1], 6.5, 6, 7), 16, 'fur')       # the deltoid
    gape = view == 'action'
    if L3:
        # pass 3: the mane in front of the shoulders, broad leaf ears, a tall face with the mouth inside it
        if level >= 4:
            # pass 4: a shaded underlayer of mane fills the gaps between the locks, so the ruff is one mass of hair
            cv.add(cv.blob([B(72, 25) + (17, 10, 1.0), B(72, 36) + (10, 6, 0.8)], rz=6), 21, 'mane')
        for r, t, w in (MANE_BACK3[:13] + MANE_BACK3[16:] if level >= 4 else MANE_BACK3):
            lock(cv, B(*r), B(*t), w, 22)
        cv.add(cv.poly([B(61, 15), B(54, 6), B(53, 0), B(57, 0), B(64, 5), B(68, 10)], rz=3, bevel=2), 24, 'face3')
        cv.add(cv.poly([B(83, 15), B(90, 6), B(91, 0), B(87, 0), B(80, 5), B(76, 10)], rz=3, bevel=2), 24, 'face3')
        if level >= 4:
            # pass 4: a bigger, heavier head with cheekbones, so the face carries at a distance
            cv.add(cv.ell(*B(72, 21), 11.5, 13, 11), 25, 'face3')
            for s in (1, -1):
                cv.add(cv.ell(*B(72 - s * 8, 22), 3.5, 3, 2.5), 28, 'face3')
        else:
            cv.add(cv.ell(*B(72, 21), 10.5, 12.5, 10), 25, 'face3')
        cv.add(cv.ell(*B(72, 28 + (1.5 if gape else 0)), 9, 5 + (1.5 if gape else 0), 4), 30, 'face3')
        cv.add(cv.ell(*B(72, 13.5), 9, 2.8, 3), 31, 'face3')
        for x in (67, 77):
            cv.add(cv.ell(*B(x, 16.5), 3.4, 3.4, 3), 32, 'beye')
        for r, t, w in MANE_FRONT3:
            lock(cv, B(*r), B(*t), w, 29)
    else:
        for r, t, w in MANE_BACK:
            lock(cv, B(*r), B(*t), w, 15)
        cv.add(cv.poly([B(61, 14), B(54, 1), B(57, 0), B(67, 10)], rz=3, bevel=2), 17, 'face')
        cv.add(cv.poly([B(83, 14), B(90, 1), B(87, 0), B(77, 10)], rz=3, bevel=2), 17, 'face')
        for r, t, w in MANE_FRONT:
            lock(cv, B(*r), B(*t), w, 19)
        cv.add(cv.ell(*B(72, 21), 11.5, 11, 9), 22, 'face')
        cv.add(cv.ell(*B(72, 29 + (2 if gape else 0)), 10.5, 5.5 + (1.5 if gape else 0), 4), 25, 'face')
        cv.add(cv.ell(*B(72, 13), 9.5, 3, 3), 27, 'face')
        for x in (66.5, 77.5):
            cv.add(cv.ell(*B(x, 16.5), 3.6, 3.6, 3), 28, 'beye')

    cv.shade(hard=0.5, gamma=1.2, falloff=1 if L3 else 0, soft=level >= 4)
    if level >= 4:
        # pass 4: the bones and muscles stand out from the membrane: the membrane a step darker, the fur a step
        # lighter on its lit side
        mm = cv.mat == cv.mi('memb')
        cv.lv = np.where(mm & ~cv.edge, np.maximum(cv.lv - 1, 1), cv.lv).astype(np.int16)
        fm = (cv.mat == cv.mi('fur')) & (cv.lv >= 3)
        cv.lv = np.where(fm, np.minimum(cv.lv + 1, 7), cv.lv).astype(np.int16)

    # ---- features
    # the membrane sags between the bones: darker in the middle of each panel and toward the trailing edge
    memb = cv.mat == cv.mi('memb')
    bonem = np.isin(cv.part, bones) | (cv.mat == cv.mi('fur'))
    dist = ndimage.distance_transform_edt(~bonem)
    cv.lv = np.where(memb & ~cv.edge, np.clip(cv.lv - np.clip(dist / 6.0, 0, 2).astype(int), 1, 7), cv.lv).astype(np.int16)
    # the veins in the membrane, running out from the arm between the fingers
    for s, (sh, el, wr, tips, rt) in zip((1, -1), pose['wings']):
        for a, b in (zip(tips[:-1], tips[1:]) if not L3 else ()):
            m = ((a[0] + b[0]) / 2 * 0.75 + wr[0] * 0.25, (a[1] + b[1]) / 2 * 0.75 + wr[1] * 0.25)
            for x, y in pixline([wr, m]):
                if 0 <= y < cv.h and 0 <= x < cv.w and cv.mat[y, x] == cv.mi('memb'):
                    cv.rel(x, y, 1)
        for x, y in pixline([el, ((el[0] + rt[0]) / 2, (el[1] + rt[1]) / 2 + 2)]):
            if 0 <= y < cv.h and 0 <= x < cv.w and cv.mat[y, x] == cv.mi('memb'):
                cv.rel(x, y, 1)
    if L3:
        # pass 3: one thin vein per panel, curving from the finger near the wrist out toward the trailing edge
        for s, (sh, el, wr, tips, rt) in zip((1, -1), pose['wings']):
            for a, b in zip(tips[:-1], tips[1:]):
                st = (wr[0] * 0.55 + a[0] * 0.45, wr[1] * 0.55 + a[1] * 0.45)
                en = ((a[0] + b[0]) / 2 * 0.85 + wr[0] * 0.15, (a[1] + b[1]) / 2 * 0.85 + wr[1] * 0.15)
                md = ((st[0] + en[0]) / 2 + s * 1.5, (st[1] + en[1]) / 2 - 1.5)
                for x, y in pixline([st, md, en]):
                    if 0 <= y < cv.h and 0 <= x < cv.w and cv.mat[y, x] == cv.mi('memb'):
                        cv.rel(x, y, 1)
    # tears in the membrane
    tears = ((0, 0), (1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (-1, 0))
    if L3:
        tears = ((0, 0), (1, 1), (2, 1), (3, 2), (4, 3), (2, 2), (1, 2), (0, 1), (5, 3), (2, 3))
    for (x, y), s in zip([(18, 30), (124, 34)] if not gape else [(14, 36), (112, 40)], (1, -1)):
        for dx, dy in tears:
            X, Y = x + dx * s, y + dy
            if cv.mat[Y, X] == cv.mi('memb'):
                cv.mat[Y, X] = -1
    # mane: strands in the locks
    for r, t, w in ((MANE_BACK3[:13] + MANE_BACK3[16:] if level >= 4 else MANE_BACK3) + MANE_FRONT3 if L3
                    else MANE_BACK + MANE_FRONT):
        a = B(*r)
        b = B(*t)
        m = (a[0] * 0.45 + b[0] * 0.55, a[1] * 0.45 + b[1] * 0.55)
        e = (a[0] * 0.2 + b[0] * 0.8, a[1] * 0.2 + b[1] * 0.8)
        for x, y in pixline([m, e], smooth=False):
            if 0 <= y < cv.h and 0 <= x < cv.w and cv.mat[y, x] == cv.mi('mane'):
                cv.rel(x, y, -1)
    if L3:
        # the inner ears, a darker pink-grey hollow with a ridge
        for s in (1, -1):
            inner = [B(72 - s * 11, 13), B(72 - s * 16, 5), B(72 - s * 17, 1.5), B(72 - s * 14, 2), B(72 - s * 9, 7)]
            im = cv.poly(inner, 1, 1)[0] & (cv.mat == cv.mi('face3'))
            cv.remat(im, 'gum')
            cv.lv = np.where(im, 0 if level >= 4 else 1, cv.lv).astype(np.int16)
            cv.line([B(72 - s * 15.5, 3), B(72 - s * 13, 7), B(72 - s * 11, 11)], 'gum', 2 if level >= 4 else 3)
        cv.sprite(BFACE_3_DIVE if gape else BFACE_3, *B(61, 9), bface3_legend(level))
        # the chest: pec folds, the line down the middle, the abs
        cv.crease([B(63, 41), B(67, 43), B(71, 42)], only={'fur'})
        cv.crease([B(73, 42), B(77, 43), B(81, 41)], only={'fur'})
        cv.line([B(72, 42), B(72, 53)], 'fur', 1)
        for y in (46, 49.5):
            cv.crease([B(68, y), B(71, y + 0.5)], only={'fur'})
            cv.crease([B(73, y + 0.5), B(76, y)], only={'fur'})
    else:
        # inner ears
        cv.line([B(57, 3), B(60, 8), B(63, 11)], 'gum', 2)
        cv.line([B(87, 3), B(84, 8), B(81, 11)], 'gum', 2)
        fx, fy = B(60, 9)
        cv.sprite(pose['face'], fx, fy, bface2_legend(level))
        cv.crease([B(66, 42), B(72, 41), B(78, 42)], only={'fur'})
        cv.line([B(72, 41), B(72, 52)], 'fur', 1)
    return cv


def bat2_side(level=2):
    cv = Canvas(72, BH, level)
    F = 'face3' if level >= 3 else 'face'
    # the folded wing hangs from the arm down its side like a cloak
    cv.add(cv.poly([(26, 26), (14, 30), (8, 46), (12, 58), (24, 54), (32, 40)], rz=3, bevel=5), 2, 'memb')
    cv.add(cv.tube([(30, 30), (18, 32), (12, 46)], 4.0, 2.6), 12, 'fur')
    cv.add(cv.tube([(12, 46), (10, 55)], 1.5, 0.8), 8, 'fur')
    cv.add(cv.tube([(12, 46), (14, 56)], 1.5, 0.8), 8, 'fur')
    cv.add(cv.tube([(12, 46), (11, 41), (13, 38)], 1.8, 0.6), 14, 'claw')
    cv.add(cv.tube([(33, 48), (40, 54), (36, 60)], 4.6, 3.2), 6, 'fur')
    for k in (0, 2, 4):
        cv.add(cv.tube([(35 + k, 60.5), (37 + k * 1.4, 63.5)], 1.2, 0.5), 7, 'claw')
    cv.add(cv.blob([(32, 38, 10, 11), (34, 47, 8, 6, 0.9), (40, 34, 6, 5, 0.7)], rz=14), 10, 'fur')
    cv.add(cv.ell(40, 37, 6, 5, 5), 15, 'fur')
    for r, t, w in (((38, 18), (24, 14), 5), ((37, 24), (22, 26), 5), ((37, 30), (24, 37), 5), ((40, 32), (32, 44), 4.5),
                    ((44, 32), (43, 43), 4), ((40, 13), (34, 5), 4)):
        lock(cv, r, t, w, 21 if level >= 3 else 15)
    if level >= 3:
        cv.add(cv.blob([(36, 26, 8, 10), (38, 36, 6, 6, 0.8)], rz=6), 20, 'mane')
    cv.add(cv.poly([(42, 13), (37, 0), (40, 0), (47, 10)], rz=3, bevel=2), 17, F)
    cv.add(cv.ell(47, 21, 9, 10.5, 9), 22, F)
    cv.add(cv.ell(54, 26, 5, 5, 4), 25, F)
    cv.add(cv.ell(50, 13, 7, 3, 3), 27, F)
    cv.add(cv.ell(52, 17, 3.4, 3.6, 3), 28, 'beye')
    cv.shade(hard=0.5, gamma=1.2, soft=level >= 4)
    prof = [
        "...dddd....",
        "..-kkkkk...",
        "...kEEEEk..",
        "...kEEpEk..",
        "...kEEEEk.k",
        "...-kkkkkNk",
        "......kNNNk",
        "........knk",
        "..kkkkkkkk.",
        "..kGGGGGGk.",
        "..kTmTmTmk.",
        "..kmmmmmLL.",
        "...ktttLLL.",
        "....kkkLL..",
        ".......LL..",
    ]
    cv.sprite(prof, 48, 13, bface3_legend(level) if level >= 3 else bface2_legend(level))
    cv.line([(39, 3), (41, 8), (43, 11)], 'gum', 2)
    return cv


# ---------------------------------------------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------------------------------------------
def font(size):
    for f in ('arialbd.ttf', 'arial.ttf', 'DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(f, size)
        except Exception:
            pass
    return ImageFont.load_default()


def up(img, s):
    im = Image.fromarray(img)
    return im.resize((im.width * s, im.height * s), Image.NEAREST)


def phone(img, width):
    """What kortev sees on a phone: nearest-neighbour up, then down to the given width."""
    im = Image.fromarray(img)
    im = im.resize((im.width * 4, im.height * 4), Image.NEAREST)
    return im.resize((width, int(width * im.height / im.width)), Image.LANCZOS)


def sheet(items, title, cols=None, pad=16, bgc=(16, 16, 22)):
    """items: list of (PIL image, caption)."""
    cols = cols or len(items)
    rows = [items[i:i + cols] for i in range(0, len(items), cols)]
    f, ft = font(18), font(24)
    cw = [max(r[i][0].width for r in rows if i < len(r)) for i in range(cols)]
    rh = [max(it[0].height for it in r) + 30 for r in rows]
    Wt = sum(cw) + pad * (cols + 1)
    Ht = sum(rh) + pad * (len(rows) + 1) + 40
    im = Image.new('RGB', (Wt, Ht), bgc)
    d = ImageDraw.Draw(im)
    d.text((pad, 10), title, fill=(230, 230, 230), font=ft)
    y = 50
    for r, h in zip(rows, rh):
        x = pad
        for i, (img, cap) in enumerate(r):
            d.text((x, y), cap, fill=(200, 200, 200), font=f)
            im.paste(img, (x, y + 26), img if img.mode == 'RGBA' else None)
            x += cw[i] + pad
        y += h + pad
    return im


def zombies_strip(level=1, s=4):
    imgs = [zombie_front(k, level).render() for k in ZOMBIES]
    w = sum(i.shape[1] for i in imgs) + 4 * (len(imgs) - 1)
    out = np.zeros((32, w, 4), np.uint8)
    out[..., :3] = BG
    out[..., 3] = 255
    x = 0
    for i in imgs:
        out[:, x:x + 16] = i
        x += 20
    return out


def comps():
    os.makedirs(OUT, exist_ok=True)
    items = []
    for v in ('front', 'side', 'action'):
        img = zombie_devil(1, v).render()
        up(img, 4).save(os.path.join(OUT, 'zd_comp_%s.png' % v))
        items.append((up(img, 4), 'Zombie Devil: %s' % v))
    items.append((up(zombies_strip(1), 4), 'its zombies'))
    sheet(items, 'Zombie Devil: the three compositions (pass 1 engine), 4x').save(os.path.join(OUT, 'zd_comps.png'))
    items = []
    for v in ('front', 'side', 'action'):
        img = bat_devil(1, v).render()
        up(img, 3).save(os.path.join(OUT, 'bat_comp_%s.png' % v))
        items.append((up(img, 3), 'Bat Devil: %s' % v))
    sheet(items, 'Bat Devil: the three compositions, 3x').save(os.path.join(OUT, 'bat_comps.png'))
    # the phone check of the comps
    ph = [(phone(zombie_devil(1, v).render(), 200 if v != 'action' else 280), v) for v in ('front', 'side', 'action')]
    ph += [(phone(bat_devil(1, v).render(), 300 if v != 'side' else 140), v) for v in ('front', 'side', 'action')]
    sheet(ph, 'comps at phone size', cols=3).save(os.path.join(OUT, '_phone_comps.png'))


def zd_view(n, v='front'):
    if n < 2:
        return zombie_devil(n, v)
    return zd2_side(n) if v == 'side' else zd2(n, v)


def bat_view(n, v='front'):
    return bat_devil(n, v) if n < 2 else bat2(n, v)


def do_pass(n):
    os.makedirs(OUT, exist_ok=True)
    zd = zd_view(n).render()
    bt = bat_view(n).render()
    up(zd, 5).save(os.path.join(OUT, 'zd_pass%d.png' % n))
    up(bt, 4).save(os.path.join(OUT, 'bat_pass%d.png' % n))
    zs = zombies_strip(n)
    sheet([(phone(zd, 300), 'Zombie Devil'), (phone(zs, 150), 'zombies'), (phone(bt, 390), 'Bat Devil')],
          'pass %d at phone size' % n).save(os.path.join(OUT, '_phone_pass%d.png' % n))
    # the other two views, built from the same parts, at phone size
    sheet([(phone(zd_view(n, 'side').render(), 200), 'side'), (phone(zd_view(n, 'action').render(), 280), 'action'),
           (phone(bat_view(n, 'side').render(), 160), 'side'), (phone(bat_view(n, 'action').render(), 300), 'dive')],
          'pass %d: the side and action views' % n).save(os.path.join(OUT, '_phone_pass%d_views.png' % n))


def main():
    os.makedirs(OUT, exist_ok=True)
    args = sys.argv[1:]
    if not args or args[0] == 'comps':
        comps()
    if args and args[0] == 'pass':
        do_pass(int(args[1]))
    if not args:
        for n in range(1, NPASS + 1):
            do_pass(n)
    if not args or args[0] == 'present':
        present()


# ---------------------------------------------------------------------------------------------------------------
# The mock game frames: a voxel ray-cast with Minecraft's own textures, at night, portrait 1080 x 1920, and the
# devils as camera-facing cut-out sprites (a mock; the real model will be the cuboids below).
# ---------------------------------------------------------------------------------------------------------------
sys.path.insert(0, HERE)
from eternity_devil_concept import raycast, tex, moon_tex  # noqa: E402  (the v4 concept's ray-caster and texture loader)

GRASS_TINT = np.array([0.50, 0.74, 0.40])
LEAF_TINT = np.array([0.40, 0.62, 0.30])
FINAL = NPASS


def block_tex(name):
    t = tex(name).astype(float)
    if name in ('grass_block_top', 'oak_leaves'):
        t[..., :3] *= GRASS_TINT if name == 'grass_block_top' else LEAF_TINT
    return t


def render_world(g, off, blocks, cam, sprites, light_fn, sky_fn, fname, fog=30.0, RW=360, RH=640):
    """g: voxel ids; blocks: id -> (top, side, bottom) texture names; sprites: list of dicts (rgba, centre, right,
    up, w, h, shade); light_fn(P, axis, step) -> brightness, warm; sky_fn(D) -> colours."""
    O, yaw, pitch, fov = cam
    ys, xs = np.mgrid[0:RH, 0:RW].astype(float) + 0.5
    th = math.tan(math.radians(fov) / 2)
    sx = (xs / RW * 2 - 1) * th * RW / RH
    sy = (1 - ys / RH * 2) * th
    pr, yw = math.radians(pitch), math.radians(yaw)
    fwd = np.array([math.cos(pr) * math.cos(yw), math.sin(pr), math.cos(pr) * math.sin(yw)])
    right = np.array([-math.sin(yw), 0.0, math.cos(yw)])
    upv = np.cross(right, fwd)
    D = fwd[None, None] + sx[..., None] * right[None, None] + sy[..., None] * upv[None, None]
    D = (D / np.linalg.norm(D, axis=2, keepdims=True)).reshape(-1, 3)
    O = np.array(O, float)
    hit, thit, axis, step = raycast(g, off, O, D, maxit=160)
    img = sky_fn(D)
    vox = hit > 0
    Ph = O[None] + thit[:, None] * D
    for b, names in blocks.items():
        sel = vox & (hit == b)
        if not sel.any():
            continue
        for ax in (0, 1, 2):
            s2 = sel & (axis == ax)
            if not s2.any():
                continue
            P = Ph[s2]
            if ax == 1:
                which = np.where(step[s2, 1] < 0, 0, 2)
                fu, fv = P[:, 0] % 1, P[:, 2] % 1
            elif ax == 0:
                which = np.ones(len(P), int)
                fu, fv = P[:, 2] % 1, 1 - (P[:, 1] % 1)
            else:
                which = np.ones(len(P), int)
                fu, fv = P[:, 0] % 1, 1 - (P[:, 1] % 1)
            tu = np.clip((fu * 16).astype(int), 0, 15)
            tv = np.clip((fv * 16).astype(int), 0, 15)
            cols = np.zeros((len(P), 3))
            for k, nm in enumerate(names):
                w_ = which == k
                if w_.any():
                    cols[w_] = block_tex(nm)[tv[w_], tu[w_], :3]
            face = {0: 0.72, 2: 0.86}.get(ax, 1.0)
            if ax == 1:
                face = np.where(step[s2, 1] < 0, 1.0, 0.45)
            br, warm = light_fn(P, ax, step[s2])
            fogk = np.clip(thit[s2] / fog, 0, 1)[:, None]
            c = cols * (face * br)[:, None] + warm
            img[s2] = c * (1 - fogk * 0.75) + img[s2] * fogk * 0.0 + np.array([8, 10, 18])[None] * fogk * 0.75
    depth = np.where(vox, thit, np.inf)
    # the sprites, far to near
    for sp in sorted(sprites, key=lambda q: -np.linalg.norm(np.array(q['c']) - O)):
        C, R, U = np.array(sp['c'], float), np.array(sp['r'], float), np.array(sp['u'], float)
        N = np.cross(R, U)
        dn = D @ N
        t = ((C - O) @ N) / np.where(np.abs(dn) < 1e-9, 1e-9, dn)
        P = O[None] + t[:, None] * D
        L = P - C[None]
        rgba = sp['rgba']
        h, w = rgba.shape[:2]
        u = np.floor(L @ R * TPB + w / 2).astype(int)
        v = np.floor(h / 2 - L @ U * TPB).astype(int)
        ok = (t > 0.1) & (t < depth) & (u >= 0) & (u < w) & (v >= 0) & (v < h)
        uu, vv = np.clip(u, 0, w - 1), np.clip(v, 0, h - 1)
        ok &= rgba[vv, uu, 3] > 0
        fogk = np.clip(t[ok] / fog, 0, 1)[:, None]
        col = rgba[vv[ok], uu[ok], :3].astype(float) * sp.get('shade', 1.0)
        img[ok] = col * (1 - fogk * 0.6) + np.array([8, 10, 18])[None] * fogk * 0.6
        depth = np.where(ok, t, depth)
    img = np.clip(img, 0, 255).reshape(RH, RW, 3).astype(np.uint8)
    cx, cy = RW // 2, RH // 2
    img[cy, cx - 3:cx + 4] = (230, 230, 230)
    img[cy - 3:cy + 4, cx] = (230, 230, 230)
    im = Image.fromarray(img).resize((1080, 1920), Image.NEAREST)
    im.save(os.path.join(OUT, fname))
    return im


def night_sky(D, moon_dir=(0.75, 0.62, -0.25), stars=160, seed=3):
    alt = np.clip(D[:, 1], -1, 1)
    top, hor = np.array([6, 8, 18], float), np.array([20, 26, 46], float)
    img = hor[None] * (1 - np.clip(alt, 0, 1)[:, None]) + top[None] * np.clip(alt, 0, 1)[:, None]
    rng = random.Random(seed)
    for _ in range(stars):
        i = rng.randrange(D.shape[0])
        if alt[i] > 0.15:
            img[i] = (190, 196, 222)
    mt = moon_tex()
    md = np.array(moon_dir, float)
    md /= np.linalg.norm(md)
    mr = np.cross(md, [0, 1.0, 0])
    mr /= np.linalg.norm(mr)
    mu_ = np.cross(mr, md)
    dp = D @ md
    a = (D @ mr) / np.maximum(dp, 1e-6)
    b = (D @ mu_) / np.maximum(dp, 1e-6)
    ms = 0.1
    inm = (dp > 0) & (np.abs(a) < ms) & (np.abs(b) < ms)
    tu = np.clip(((a + ms) / (2 * ms) * mt.shape[1]).astype(int), 0, mt.shape[1] - 1)
    tv = np.clip(((ms - b) / (2 * ms) * mt.shape[0]).astype(int), 0, mt.shape[0] - 1)
    mc = mt[tv, tu]
    lm = inm & (mc[:, 3] > 0)
    img[lm] = img[lm] + mc[lm, :3] * 0.95
    return img


VILLAGE_BLOCKS = {
    1: ('grass_block_top', 'grass_block_side', 'dirt'), 2: ('dirt_path_top', 'dirt_path_side', 'dirt'),
    3: ('cobblestone',) * 3, 4: ('mossy_cobblestone',) * 3, 5: ('oak_planks',) * 3,
    6: ('oak_log_top', 'oak_log', 'oak_log_top'), 7: ('dark_oak_log',) * 3, 8: ('oak_leaves',) * 3,
    9: ('spruce_planks',) * 3, 10: ('cracked_stone_bricks',) * 3, 11: ('dirt',) * 3,
}


def village_world():
    """A ruined village street at night, hand-laid: the path runs ahead, burnt and broken houses either side."""
    X0, Y0, Z0 = -2, -1, -14
    g = np.zeros((34, 16, 29), np.int8)
    S = lambda x, y, z, b: g.__setitem__((x - X0, y - Y0, z - Z0), b)
    for x in range(-2, 32):
        for z in range(-14, 15):
            S(x, -1, z, 11)
            S(x, 0, z, 1)
    for x in range(-2, 32):
        for z in (-1, 0, 1):
            if (x * 7 + z * 3) % 11 != 0:
                S(x, 0, z, 2)
    def house(x0, x1, z0, z1, hts, gaps):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                h = hts.get((x, z), hts['d'])
                for y in range(1, h + 1):
                    if (x, z, y) in gaps:
                        continue
                    if y == 1:
                        S(x, y, z, 4 if (x + z) % 3 == 0 else 3)
                    elif corner:
                        S(x, y, z, 6)
                    else:
                        S(x, y, z, 5 if (x * 5 + z * 3 + y) % 7 else 9)
    # left: a house with its front wall fallen; right: a taller one burnt open, a broken roof beam
    house(5, 10, -8, -3, {'d': 4, (5, -3): 2, (6, -3): 1, (7, -3): 1, (8, -3): 2, (9, -3): 3, (5, -8): 5, (10, -8): 5}, {(7, -8, 3), (8, -8, 3)})
    house(13, 18, 3, 8, {'d': 5, (13, 3): 3, (14, 3): 2, (15, 3): 1, (16, 3): 2, (18, 3): 6, (18, 8): 6}, {(13, 5, 2), (13, 5, 3), (13, 6, 2), (13, 6, 3)})
    for x in range(13, 19):
        if x % 2:
            S(x, 6, 8, 7)
    # rubble on the street edges, a fence post line (as cobble posts), a dead tree and far trees
    for x, z in ((4, -2), (6, -2), (11, 2), (12, 3), (3, 2), (19, -3), (21, 2)):
        S(x, 1, z, 4)
    for x in (2, 4, 6, 8):
        S(x, 1, 3, 9)
    for x, z, h in ((24, -6, 6), (27, 5, 7), (29, -2, 6), (22, 9, 5), (2, -9, 5)):
        for y in range(1, h):
            S(x, y, z, 7)
        for dx in (-2, -1, 0, 1, 2):
            for dz in (-2, -1, 0, 1, 2):
                for y in (h - 1, h, h + 1):
                    if abs(dx) + abs(dz) + (y - h) * 2 < 4 and (dx * 3 + dz * 5 + y) % 5:
                        S(x + dx, y, z + dz, 8)
    return g, np.array([X0, Y0, Z0])


def village_light(P, ax, step):
    moon = 0.50 + 0.10 * (P[:, 1] > 2)
    lantern = np.exp(-((P[:, 0] - 3.5) ** 2 + (P[:, 1] - 2) ** 2 + (P[:, 2] - 3) ** 2) / 8.0) * 0.6
    return moon + lantern, (lantern[:, None] * np.array([50, 26, 6])[None])


def zd_frame():
    zd = zd_view(FINAL).render(bg=False)
    # the devil stands at the end of the street, the horde spread either side of the lane so its face stays clear
    sprites = [dict(rgba=zd, c=(9.6, 1 + ZH / 32, 0.2), r=(0, 0, 1), u=(0, 1, 0), shade=0.78)]
    kinds = list(ZOMBIES)
    for k, (x, z) in enumerate(((5.2, -2.4), (6.0, 2.2), (7.0, -1.9), (7.8, 1.4), (8.6, -3.0), (8.8, 2.9),
                                (8.3, 0.9), (8.9, -2.3))):
        img = zombie_front(kinds[k % 3], FINAL).render(bg=False)
        sprites.append(dict(rgba=img, c=(x, 2.0, z), r=(0, 0, 1), u=(0, 1, 0), shade=0.62))
    g, off = village_world()
    return render_world(g, off, VILLAGE_BLOCKS, ((0.5, 2.62, 0.5), 0, 4, 70), sprites, village_light,
                        lambda D: night_sky(D, (0.7, 0.6, -0.4)), 'zd_game_frame.png', fog=34)


RAVINE_BLOCKS = {1: ('stone',) * 3, 2: ('deepslate',) * 3, 3: ('coal_ore',) * 3, 4: ('iron_ore',) * 3,
                 5: ('gravel',) * 3, 6: ('tuff',) * 3, 7: ('andesite',) * 3, 8: ('grass_block_top', 'grass_block_side', 'dirt'),
                 9: ('dirt',) * 3}


def ravine_world():
    """A ravine running ahead: jagged stone walls 34 blocks high, the floor of gravel, the sky a strip above."""
    X0, Y0, Z0 = -4, -2, -14
    g = np.zeros((40, 40, 29), np.int8)
    for xi in range(40):
        x = xi + X0
        for yi in range(40):
            y = yi + Y0
            # the wall faces wander (hand-tuned waves), the ravine widens toward the top
            wl = -3.5 - 0.06 * max(y, 0) - 1.1 * math.sin(x * 0.7 + y * 0.31) - 0.6 * math.sin(y * 0.9 + 1.3)
            wr = 3.5 + 0.06 * max(y, 0) + 1.0 * math.sin(x * 0.55 - y * 0.27 + 2) + 0.6 * math.sin(y * 0.8)
            for zi in range(29):
                z = zi + Z0
                solid = y <= 0 or z <= wl or z >= wr
                if not solid or y > 34:
                    continue
                if y > 33:
                    b = 8
                elif y > 31:
                    b = 9
                elif y < 4:
                    b = 2 if (x + y + z) % 5 else 6
                else:
                    h = (x * 73 + y * 31 + z * 17) % 97
                    b = 3 if h < 4 else 4 if h < 6 else 7 if h < 14 else 1
                if y <= 0 and wl < z < wr:
                    b = 5 if (x * 3 + z) % 4 else 2
                g[xi, yi, zi] = b
    return g, np.array([X0, Y0, Z0])


def ravine_light(P, ax, step):
    moon = 0.12 + 0.62 * np.clip((P[:, 1] - 4) / 26.0, 0, 1) ** 1.3
    torch = np.exp(-((P[:, 0] - 2.0) ** 2 + (P[:, 1] - 3) ** 2 + (P[:, 2] + 2.5) ** 2) / 14.0) * 0.9
    return moon + torch, torch[:, None] * np.array([60, 32, 8])[None]


def bat_frame():
    bat = bat_view(FINAL, 'action').render(bg=False)
    pitch, yaw = 38, -4
    pr, yw = math.radians(pitch), math.radians(yaw)
    fwd = np.array([math.cos(pr) * math.cos(yw), math.sin(pr), math.cos(pr) * math.sin(yw)])
    right = np.array([-math.sin(yw), 0.0, math.cos(yw)])
    upv = np.cross(right, fwd)
    O = np.array([0.5, 2.62, 0.3])
    C = O + fwd * 9.5 + upv * 0.4 + right * 0.2
    sprites = [dict(rgba=bat, c=tuple(C), r=tuple(right), u=tuple(upv), shade=0.95)]
    g, off = ravine_world()
    return render_world(g, off, RAVINE_BLOCKS, (tuple(O), yaw, pitch, 70), sprites, ravine_light,
                        lambda D: night_sky(D, (0.35, 0.92, -0.15), stars=220), 'bat_game_frame.png', fog=40)


# ---------------------------------------------------------------------------------------------------------------
# The cuboid build plans. Each box is (name, x, y, w, h, z, d, animates) in front-texture texels: x, y, w, h are
# its front face on the painted front; z is how far out from the back it starts and d its depth. The front face of
# every box is UV-mapped to its own rectangle of the painting, so the painting carries across the boxes as one
# picture; side and top faces take a plain tile of the part's material. Bones (the bat's arms and fingers) are
# boxes along the line between two joints.
# ---------------------------------------------------------------------------------------------------------------
ZD_BOXES = [
    ('brain L', 19, 2, 22, 23, 10, 20, 'pulses (scale 1.0-1.04)'), ('brain R', 39, 2, 21, 22, 11, 18, 'pulses'),
    ('neck flaps', 26, 19, 28, 7, 14, 14, ''), ('shoulders', 21, 24, 38, 10, 6, 24, 'hunch on attack'),
    ('chest', 20, 27, 40, 14, 4, 28, ''), ('trunk', 14, 40, 50, 22, 2, 32, 'breathes'),
    ('belly sag', 17, 42, 42, 19, 30, 8, 'wobbles'), ('stump L', 8, 28, 15, 14, 12, 12, 'twitches'),
    ('stump R', 57, 27, 15, 14, 12, 12, 'twitches'), ('face head', 15, 30, 20, 17, 34, 9, 'tilts, turns'),
    ('face jaw', 17, 47, 16, 9, 34, 8, 'opens (moans)'), ('rib plate', 45, 33, 17, 15, 31, 2, ''),
    ('organ', 43, 60, 8, 7, 33, 6, 'swings'),
]


def gut_boxes():
    """The guts: two boxes each, cut from the hand-placed paths of the final front (they swing and walk)."""
    out = []
    paths = [([(27, 55), (21, 64), (17, 72), (15, 78.5)], 3.8), ([(55, 55), (61, 64), (64, 72), (67, 78.5)], 3.8),
             ([(36, 58), (33, 64), (30, 71), (28, 78.5)], 4.4), ([(45, 58), (47, 65), (49, 72), (51, 78.5)], 4.4),
             ([(41, 59), (40, 64), (41, 70), (43, 73)], 2.6), ([(32, 57), (24, 62), (14, 66), (6, 64), (3, 59)], 3.0),
             ([(50, 58), (60, 63), (69, 69), (76, 68), (79, 64)], 3.0)]
    for k, (pts, r) in enumerate(paths):
        half = len(pts) // 2 + 1
        for j, seg in enumerate((pts[:half], pts[half - 1:])):
            xs = [p[0] for p in seg]
            ys = [p[1] for p in seg]
            rr = r * (1 - 0.25 * j)
            x0, y0 = int(min(xs) - rr), int(min(ys) - rr)
            out.append(('gut %d%s' % (k + 1, 'ab'[j]), x0, y0, int(max(xs) + rr) - x0, int(max(ys) + rr) - y0,
                        16 if k > 1 else 2, int(rr * 2), 'sways; planted ones step' if j else 'sways'))
    return out


def bat_boxes():
    pose = BAT_POSES['front']
    out = [('head', 61, 8, 23, 18, 20, 18, 'looks, tilts'), ('jaw', 63, 25, 19, 9, 22, 14, 'opens wide (the air blast)'),
           ('brow', 63, 11, 18, 4, 36, 3, ''), ('eye L', 64, 13, 7, 7, 38, 2, 'blinks (UV)'), ('eye R', 74, 13, 7, 7, 38, 2, 'blinks (UV)'),
           ('ear L', 52, 0, 16, 15, 22, 2, 'twitches'), ('ear R', 76, 0, 16, 15, 22, 2, 'twitches'),
           ('ruff back', 46, 1, 52, 33, 16, 4, ''), ('ruff front', 55, 28, 34, 19, 30, 6, 'ruffles'),
           ('chest', 56, 27, 32, 16, 8, 18, 'breathes'), ('belly', 63, 43, 18, 11, 10, 14, ''),
           ('tongue a', 69, 32, 6, 4, 34, 3, 'flicks'), ('tongue b', 70, 36, 4, 3, 34, 2, 'flicks'),
           ('thigh L', 59, 47, 9, 9, 8, 9, 'tucks in flight'), ('foot L', 59, 56, 9, 8, 9, 8, 'grabs'),
           ('thigh R', 76, 47, 9, 9, 8, 9, 'tucks in flight'), ('foot R', 76, 56, 9, 8, 9, 8, 'grabs')]
    bones = []
    for side, (sh, el, wr, tips, rt) in zip('LR', pose['wings']):
        bones.append(('upper arm ' + side, sh, el, 8, 'flaps (shoulder)'))
        bones.append(('forearm ' + side, el, wr, 6, 'folds (elbow)'))
        for k, t in enumerate(tips):
            bones.append(('finger %d %s' % (k + 1, side), wr, t, 2, 'spreads (wrist)'))
        bones.append(('thumb claw ' + side, wr, (wr[0] - (5 if side == 'L' else -5), wr[1] - 6.5), 3, ''))
        panels = [(el, wr, rt)] + [(wr, a, b) for a, b in zip(tips[:-1], tips[1:])] + [(wr, tips[-1], rt)]
        for k, (a, b, c) in enumerate(panels):
            bones.append(('membrane %d %s' % (k + 1, side), (a, b, c), None, 0, 'a flat quad, follows its bones'))
    return out, bones


def plan_sheet(front, boxes, bones, title, fname, s, tex_w, flats=()):
    """The boxes drawn over the front, the texture layout (each box's net, its front face cut from the painting),
    and the list."""
    h, w = front.shape[:2]
    base = up(front, s).convert('RGB')
    d = ImageDraw.Draw(base)
    f = font(13)
    pal = [(255, 214, 90), (120, 220, 255), (255, 130, 160), (150, 255, 150), (220, 160, 255), (255, 170, 90)]
    for k, (nm, x, y, bw, bh, z, dd, an) in enumerate(boxes):
        c = pal[k % len(pal)]
        d.rectangle([x * s, y * s, (x + bw) * s - 1, (y + bh) * s - 1], outline=c, width=2)
        d.text((x * s + 3, y * s + 2), nm, fill=c, font=f)
    for nm, a, b, th, an in bones:
        if b is None:
            continue
        d.line([a[0] * s, a[1] * s, b[0] * s, b[1] * s], fill=(255, 255, 255), width=max(2, int(th * s / 3)))
        d.ellipse([a[0] * s - 4, a[1] * s - 4, a[0] * s + 4, a[1] * s + 4], outline=(255, 90, 90), width=2)
    # the texture layout: shelf-pack each box's net (Minecraft's box UV: 2(w + d) wide, d + h tall)
    nets = []
    for nm, x, y, bw, bh, z, dd, an in boxes:
        nets.append((nm, x, y, bw, bh, dd))
    for nm, x, y, bw, bh in flats:
        # a flat painted region (the wing membranes: their quads map triangles of it), front face only
        nets.append((nm, x, y, bw // 2, bh, 0))
    nets.sort(key=lambda n: -(n[5] + n[4]))
    texw = tex_w
    sheet_ = np.zeros((512, texw, 4), np.uint8)
    cx = cy = rowh = 0
    placed = []
    for nm, x, y, bw, bh, dd in nets:
        nw, nh = 2 * (bw + dd), dd + bh
        if cx + nw > texw:
            cx, cy, rowh = 0, cy + rowh, 0
        placed.append((nm, cx, cy, bw, bh, dd))
        # the net: top and bottom (side tile), then the four sides, the front face cut from the painting
        sheet_[cy:cy + nh, cx:cx + nw] = (60, 60, 70, 255)
        sheet_[cy:cy + dd, cx + dd:cx + dd + 2 * bw] = (90, 90, 100, 255)
        fw = bw if dd else 2 * bw
        fr = front[y:y + bh, x:x + fw]
        sub = sheet_[cy + dd:cy + dd + bh, cx + dd:cx + dd + fw]
        sub[:fr.shape[0], :fr.shape[1]] = np.where(fr[..., 3:4] > 0, fr, np.array([40, 40, 48, 255], np.uint8))
        cx += nw
        rowh = max(rowh, nh)
    used = cy + rowh
    texh = 1 << int(math.ceil(math.log2(max(used, 16))))
    sheet_ = sheet_[:texh]
    ts = 2
    tim = up(sheet_, ts).convert('RGB')
    td = ImageDraw.Draw(tim)
    for nm, x0, y0, bw, bh, dd in placed:
        td.rectangle([x0 * ts, y0 * ts, (x0 + 2 * (bw + dd)) * ts - 1, (y0 + dd + bh) * ts - 1], outline=(200, 200, 90))
    lines = ['%-16s front %3d x %-3d at (%3d,%3d)  z %2d  depth %2d   %s' % (nm, bw, bh, x, y, z, dd, an)
             for nm, x, y, bw, bh, z, dd, an in boxes]
    for nm, a, b, th, an in bones:
        if b is None:
            lines.append('%-16s %s' % (nm, an))
        else:
            L = math.hypot(b[0] - a[0], b[1] - a[1])
            ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
            lines.append('%-16s %2d x %2d x %3d along %4.0f deg from (%d,%d)   %s' % (nm, th, th, round(L), ang, a[0], a[1], an))
    nb = len(boxes) + sum(1 for b in bones if b[2] is not None)
    nq = sum(1 for b in bones if b[2] is None)
    cap = '%s: %d boxes%s; texture %d x %d' % (title, nb, (' + %d membrane quads' % nq) if nq else '', texw, texh)
    with open(os.path.join(OUT, fname.replace('.png', '.txt')), 'w', encoding='utf-8') as fh:
        fh.write(cap + '\n(front-texture texels, 16 a block; z = out from the back)\n\n' + '\n'.join(lines) + '\n')
    fm = font(12)
    tw = max(base.width + tim.width + 48, 900)
    th_ = max(base.height, tim.height) + 70 + 15 * len(lines)
    out = Image.new('RGB', (tw, th_), (16, 16, 22))
    od = ImageDraw.Draw(out)
    od.text((16, 10), cap, fill=(235, 235, 235), font=font(20))
    out.paste(base, (16, 44))
    out.paste(tim, (base.width + 32, 44))
    od.text((base.width + 32, 44 + tim.height + 4), 'texture layout (2x): each net = top, bottom, then the sides;'
            ' the front face is the painting', fill=(200, 200, 200), font=fm)
    y = 54 + max(base.height, tim.height) + 14
    for ln in lines:
        od.text((16, y), ln, fill=(210, 210, 210), font=fm)
        y += 15
    out.save(os.path.join(OUT, fname))
    return out, nb, nq, (texw, texh)


def zombie_skin(kind, level=FINAL):
    """The vanilla zombie's 64 x 64 layout, filled from the painted front: the fronts as painted, the sides and back
    continuing the clothes and hair."""
    fr = zombie_front(kind, level).render(bg=False)
    sk = np.zeros((64, 64, 4), np.uint8)

    def col(x, y):
        return fr[y, x]

    def fill(x0, y0, w, h, c):
        sk[y0:y0 + h, x0:x0 + w] = c

    # head: front from the painting; sides/back/top hair, the lower sides skin
    sk[8:16, 8:16] = fr[0:8, 4:12]
    for x0 in (0, 16, 24):
        sk[8:16, x0:x0 + 8] = fr[0:8, 4:12][:, ::-1] if x0 == 24 else fr[0:8, 4:12]
        sk[8:11, x0:x0 + 8] = col(5, 0)
        if x0 == 24:
            sk[8:15, x0:x0 + 8] = col(5, 0)
        else:
            sk[11:16, x0:x0 + 8] = col(4, 4)
    fill(8, 0, 8, 8, col(5, 0))
    fill(16, 0, 8, 8, col(11, 7))
    # body
    sk[20:32, 20:28] = fr[8:20, 4:12]
    for x0, w in ((16, 4), (28, 4)):
        sk[20:32, x0:x0 + w] = fr[8:20, 4:8] if x0 == 16 else fr[8:20, 8:12]
    sk[20:32, 32:40] = fr[8:20, 4:12][:, ::-1]
    sk[20:32, 32:40, :3] = (sk[20:32, 32:40, :3] * 0.85).astype(np.uint8)
    fill(20, 16, 8, 4, col(5, 8))
    fill(28, 16, 8, 4, col(5, 19))
    # arms (right at 40,16; left at 32,48) and legs (right at 0,16; left at 16,48)
    for (ax, ay), fx in (((40, 16), 0), ((32, 48), 12)):
        for k in range(4):
            sk[ay + 4:ay + 16, ax + 4 * k:ax + 4 * k + 4] = fr[8:20, fx:fx + 4]
        fill(ax + 4, ay, 4, 4, col(fx + 1, 8))
        fill(ax + 8, ay, 4, 4, col(fx + 1, 19))
    for (lx, ly), fx in (((0, 16), 4), ((16, 48), 8)):
        for k in range(4):
            sk[ly + 4:ly + 16, lx + 4 * k:lx + 4 * k + 4] = fr[20:32, fx:fx + 4]
        fill(lx + 4, ly, 4, 4, col(fx + 1, 20))
        fill(lx + 8, ly, 4, 4, col(fx + 1, 31))
    return sk


def zombies_sheet():
    items = []
    for k in ZOMBIES:
        items.append((up(zombie_front(k).render(), 8), k + ' (front, 8x)'))
    for k in ZOMBIES:
        sk = zombie_skin(k)
        Image.fromarray(sk).save(os.path.join(OUT, 'zombie_skin_%s.png' % k))
        items.append((up(np.where(sk[..., 3:4] > 0, sk, np.array([30, 30, 38, 255], np.uint8)), 4), k + ' skin, 64x64 (4x)'))
    im = sheet(items, 'its zombies: the people it turned, on the vanilla zombie model and skin layout', cols=3)
    im.save(os.path.join(OUT, 'zombies_final.png'))
    return im


def present():
    os.makedirs(OUT, exist_ok=True)
    zd, zs, za = (zd_view(FINAL, v).render() for v in ('front', 'side', 'action'))
    bt, bs, ba = (bat_view(FINAL, v).render() for v in ('front', 'side', 'action'))
    up(zd, 6).save(os.path.join(OUT, 'zd_final_front_6x.png'))
    up(bt, 5).save(os.path.join(OUT, 'bat_final_front_5x.png'))
    sheet([(up(zd, 4), 'front, 5 x 5 blocks'), (up(zs, 4), 'side'), (up(za, 4), 'action: rears up, guts thrown out')],
          'Zombie Devil, final (pass %d), 4x' % FINAL).save(os.path.join(OUT, 'zd_final.png'))
    sheet([(up(bt, 3), 'front: 9 x 4 blocks, wings 8.5'), (up(bs, 3), 'side: wing folded'),
           (up(ba, 3), 'dive: wings up, jaw dropped')],
          'Bat Devil, final (pass %d), 3x' % FINAL).save(os.path.join(OUT, 'bat_final.png'))
    zombies_sheet()
    zf = zd_frame()
    bf = bat_frame()
    zp = plan_sheet(zd_view(FINAL).render(bg=False), ZD_BOXES + gut_boxes(), [], 'Zombie Devil cuboid plan',
                    'zd_cuboid_plan.png', 6, 256)
    bb, bones = bat_boxes()
    bp = plan_sheet(bat_view(FINAL).render(bg=False), bb, bones, 'Bat Devil cuboid plan', 'bat_cuboid_plan.png', 4, 256,
                    flats=[('wing L', 0, 0, 64, 58), ('wing R', 80, 0, 64, 58)])
    small_sheet(zd, bt, zf, bf)
    return zp, bp


def small_sheet(zd, bt, zf, bf):
    """The phone sheet, about 1200 px wide: each devil's final front beside its in-game frame, and the zombies."""
    W_ = 1200
    im = Image.new('RGB', (W_, 1000), (14, 14, 20))
    d = ImageDraw.Draw(im)
    d.text((20, 12), 'Common devils: the Zombie Devil and the Bat Devil (16 texels a block)', fill=(235, 235, 235), font=font(26))
    fr_h = 430
    z = up(zd, 5)
    f1 = zf.resize((int(fr_h * 1080 / 1920), fr_h), Image.LANCZOS)
    im.paste(z, (20, 60))
    im.paste(f1, (20 + z.width + 16, 60))
    zs = up(zombies_strip(FINAL), 5)
    im.paste(zs, (20 + z.width + 16 + f1.width + 16, 60))
    d.text((20 + z.width + 16 + f1.width + 16, 60 + zs.height + 8), 'its zombies: yakuza,\nsalaryman, worker;\n'
           'grey dead skin,\nblank eyes, bloody\nopen mouths', fill=(200, 200, 200), font=font(17))
    d.text((20, 60 + 400 + 6), 'Zombie Devil: 5 x 4.6 blocks', fill=(200, 200, 200), font=font(17))
    y2 = 60 + fr_h + 40
    b = up(bt, 4)
    f2 = bf.resize((int(fr_h * 1080 / 1920), fr_h), Image.LANCZOS)
    im.paste(b, (20, y2 + 40))
    im.paste(f2, (20 + b.width + 16, y2))
    d.text((20, y2 + 40 + b.height + 6), 'Bat Devil: about 3.5 blocks tall, wings 8.5 across', fill=(200, 200, 200), font=font(17))
    d.text((20 + b.width + 16 + f2.width + 16, y2 + 20), 'in game: diving at you\nin a ravine at night',
           fill=(200, 200, 200), font=font(17))
    d.text((20 + z.width + 16, 60 + fr_h + 4), 'in game: a ruined village at night', fill=(200, 200, 200), font=font(15))
    im = im.crop((0, 0, W_, y2 + fr_h + 20))
    im.save(os.path.join(OUT, '_concept_small.png'))
    return im


if __name__ == '__main__':
    main()
