"""The Eternity Devil's texture atlas (Chainsawman mod), painted with PIL at 16 px per block, to be drawn nearest-filtered on cuboids.

One 256 x 384 atlas, every material a limited palette lit from the top:
  flesh   4 families (pale / red / deep / sick) x 3 pages (top, side, bottom), 64 x 64: bulges, creases with a ridge beside them,
          veins, wet highlights. Faces sample a piece of a page, so a big face and a small one show the same density of detail.
  eyes    8 front plates 16 x 16 (round, transparent corners: sclera, bloodshot veins, iris, pupil), 8 body tiles 16 x 16
  teeth   8 front 8 x 16, 8 side 8 x 16 (ivory to yellow, crooked, chipped, decayed); gums 3 x 32 x 16; throat 32 x 32; tongue 32 x 16
  heart   64 x 64 dark muscle with pale vessels and fat; vessel 16 x 16 (2)

Run: python tools/eternity_devil_atlas.py   (writes eternity_devil.png into chainsawman/src/client/resources/.../textures/entity)
The tile positions are mirrored in EternityDevilRenderer (the Tile constants).
"""
import math
import os
import random

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'chainsawman', 'src', 'client', 'resources', 'assets', 'chainsawman', 'textures', 'entity', 'eternity_devil.png')

W, H = 256, 384

RAMPS = {
    'red': [(30, 6, 10), (70, 10, 18), (112, 18, 28), (150, 34, 42), (184, 62, 66), (212, 98, 98), (234, 138, 130), (248, 184, 168)],
    'pale': [(60, 16, 24), (112, 38, 44), (164, 70, 72), (200, 108, 104), (224, 146, 136), (238, 182, 166), (246, 208, 190), (252, 228, 214)],
    'deep': [(8, 1, 5), (24, 3, 10), (50, 7, 16), (78, 11, 22), (108, 18, 28), (138, 32, 38), (166, 54, 54), (192, 82, 76)],
    'sick': [(26, 9, 20), (56, 17, 38), (94, 29, 55), (134, 48, 68), (166, 80, 84), (194, 116, 102), (212, 148, 116), (226, 178, 128)],
}
VEIN = {'red': (84, 14, 36), 'pale': (120, 44, 80), 'deep': (70, 14, 40), 'sick': (60, 30, 90)}

BAYER = np.array([[0, 2], [3, 1]]) / 4.0 - 0.375


def blur(a, n=1):
    for _ in range(n):
        a = (a + np.roll(a, 1, 0) + np.roll(a, -1, 0) + np.roll(a, 1, 1) + np.roll(a, -1, 1)) / 5.0
    return a


def height_field(size, rnd, bumps):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    h = np.zeros((size, size))
    peaks = []
    for _ in range(bumps):
        cx, cy = rnd.uniform(0, size), rnd.uniform(0, size)
        r = rnd.uniform(6, 15)
        amp = rnd.uniform(0.5, 1.0)
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                d2 = ((xx - cx - ox) ** 2 + (yy - cy - oy) ** 2) / (r * r)
                h += amp * np.exp(-d2 * 1.6)
        peaks.append((cx % size, cy % size, r, amp))
    h = (h - h.min()) / (h.max() - h.min() + 1e-9)
    return h, peaks


def voronoi_edges(size, rnd, cells, width):
    pts = [(rnd.uniform(0, size), rnd.uniform(0, size)) for _ in range(cells)]
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    ds = []
    for (px, py) in pts:
        best = None
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                d = np.sqrt((xx - px - ox) ** 2 + (yy - py - oy) ** 2)
                best = d if best is None else np.minimum(best, d)
        ds.append(best)
    ds = np.sort(np.stack(ds), axis=0)
    return (ds[1] - ds[0]) < width


def walk(img, rnd, x, y, length, color, wobble=0.5, branch=0.08, depth=0, ang=None):
    size = img.shape[0]
    ang = rnd.uniform(0, 6.283) if ang is None else ang
    for _ in range(length):
        ang += rnd.uniform(-wobble, wobble)
        x += math.cos(ang)
        y += math.sin(ang)
        img[int(y) % size, int(x) % size] = color
        if depth < 2 and rnd.random() < branch:
            walk(img, rnd, x, y, length // 2, color, wobble, branch, depth + 1, ang + rnd.choice((-0.8, 0.8)))


def flesh_tile(family, page, seed):
    rnd = random.Random(seed * 131 + page * 7)
    ramp = RAMPS[family]
    size = 64
    h, peaks = height_field(size, rnd, rnd.randint(12, 18))
    cre = voronoi_edges(size, rnd, rnd.randint(3, 4), 1.3)
    keep, _ = height_field(size, rnd, 6)
    cre = cre & (keep < 0.55) & (h < 0.62)
    # Lit from above: the slope facing up is bright, the one facing down is dark.
    lit = h - np.roll(h, (2, 1), (0, 1))
    v = 0.42 * h + 0.58 * (0.5 + lit * 2.4)
    v = blur(v, 1)
    shift = {0: 0.5, 1: 0.0, 2: -1.6}[page]
    idx = np.clip(np.floor(v * 7.4 + shift + 0.5 + np.tile(BAYER, (size // 2, size // 2)) * 0.5), 0, 7).astype(int)
    # Bottom pages are never bright.
    if page == 2:
        idx = np.minimum(idx, 4)
    if page == 1:
        # The side pages darken toward the bottom of the page, as the light does.
        grad = np.linspace(0.7, -1.0, size)[:, None]
        idx = np.clip(np.floor(idx + grad + 0.4), 0, 7).astype(int)
    img = np.zeros((size, size, 3), dtype=np.uint8)
    for i in range(8):
        img[idx == i] = ramp[i]
    # Creases: dark, with a light ridge one pixel above (the light comes from above).
    ridge = np.roll(cre, -1, 0) & ~cre
    img[ridge] = ramp[min(7, 6 if page == 0 else 5)]
    img[cre] = ramp[0 if page != 0 else 1]
    # A few deliberate longer fold lines.
    for _ in range(rnd.randint(2, 4)):
        x, y = rnd.uniform(0, size), rnd.uniform(0, size)
        a = rnd.uniform(-0.6, 0.6) + rnd.choice((0, math.pi))
        for _ in range(rnd.randint(14, 28)):
            a += rnd.uniform(-0.25, 0.25)
            x += math.cos(a)
            y += math.sin(a)
            img[int(y) % size, int(x) % size] = ramp[1]
            img[(int(y) - 1) % size, int(x) % size] = ramp[min(7, 5)] if page == 0 else ramp[4]
    # Veins: thin, purplish.
    if family != 'deep' or page == 0:
        for _ in range(rnd.randint(2, 4)):
            walk(img, rnd, rnd.uniform(0, size), rnd.uniform(0, size), rnd.randint(14, 30), VEIN[family], wobble=0.55, branch=0.07)
    # Wet: a short bright dash at the upper left of the biggest bulges, a pixel under it dark.
    peaks.sort(key=lambda p: -p[3])
    for (cx, cy, r, amp) in peaks[:6]:
        if page == 2:
            break
        sx, sy = int(cx - r * 0.35) % size, int(cy - r * 0.45) % size
        spec = (255, 236, 226) if family != 'sick' else (255, 238, 200)
        img[sy, sx] = spec
        img[sy, (sx + 1) % size] = spec
        img[(sy + 1) % size, sx] = ramp[7]
        if r > 10:
            img[sy, (sx + 2) % size] = ramp[7]
    return Image.fromarray(img, 'RGB').convert('RGBA')


def disc_mask(size, r, cx=None, cy=None):
    cx = (size - 1) / 2 if cx is None else cx
    cy = (size - 1) / 2 if cy is None else cy
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    return np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) <= r


IRIS = [
    # (iris ring, iris, pupil kind, sclera tone, veins)
    ((92, 6, 12), (170, 22, 28), 'round', 0, 7),
    ((120, 80, 10), (222, 176, 40), 'slit', 2, 5),
    ((96, 112, 120), (176, 196, 204), 'small', 0, 4),
    ((10, 2, 4), (40, 10, 14), 'huge', 1, 8),
    ((120, 120, 112), (214, 212, 196), 'dull', 0, 3),
    ((48, 24, 10), (122, 70, 34), 'round', 2, 6),
    ((52, 80, 24), (138, 176, 70), 'slit', 2, 6),
    ((60, 0, 6), (136, 12, 22), 'small', 3, 12),
]
SCLERA = [(238, 228, 208), (230, 214, 204), (232, 218, 160), (226, 150, 144)]


def sclera_shade(x, y, size, tone, rnd):
    """The white of an eye at a pixel: lighter in the middle, redder and darker toward the edge of the disc."""
    c = (size - 1) / 2
    d = math.hypot(x - c, y - c) / (size / 2)
    base = np.array(SCLERA[tone], dtype=float)
    edge = np.array((170, 90, 92), dtype=float)
    dark = np.array((96, 40, 50), dtype=float)
    t = max(0.0, d - 0.45) / 0.55
    col = base * (1 - t) + edge * t
    if d > 0.88:
        col = col * 0.5 + dark * 0.5
    # Light from above: the top of the ball is bright, the bottom shaded.
    col = col * (1.06 - 0.22 * ((y / size) - 0.2))
    return np.clip(col, 0, 255)


def eye_front(v, seed):
    rnd = random.Random(seed)
    ring, iris, pupil, tone, nveins = IRIS[v]
    s = 16
    img = np.zeros((s, s, 4), dtype=np.uint8)
    mask = disc_mask(s, 7.6)
    for y in range(s):
        for x in range(s):
            if mask[y, x]:
                img[y, x, :3] = sclera_shade(x, y, s, tone, rnd)
                img[y, x, 3] = 255
    cx, cy = 7.5 + rnd.choice((-1, 0, 0, 1)), 7.5 + rnd.choice((-1, 0, 1))
    # Veins from the rim toward the iris.
    for _ in range(nveins):
        a = rnd.uniform(0, 6.283)
        r = 7.2
        col = (176, 28, 36) if rnd.random() < 0.75 else (206, 90, 96)
        for _ in range(rnd.randint(3, 6)):
            x, y = int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r))
            if 0 <= x < s and 0 <= y < s and mask[y, x]:
                img[y, x, :3] = col
            a += rnd.uniform(-0.3, 0.3)
            r -= rnd.uniform(0.8, 1.3)
    ir = {'round': 4.6, 'slit': 4.6, 'small': 4.2, 'huge': 5.4, 'dull': 4.6}[pupil]
    im = disc_mask(s, ir, cx, cy)
    inner = disc_mask(s, ir - 1.2, cx, cy)
    for y in range(s):
        for x in range(s):
            if im[y, x]:
                img[y, x, :3] = ring
                img[y, x, 3] = 255
            if inner[y, x]:
                # The iris is lighter at the bottom, as a real one is lit through.
                k = 0.8 + 0.4 * (y - (cy - ir)) / (2 * ir)
                img[y, x, :3] = np.clip(np.array(iris) * k, 0, 255)
    if pupil in ('round', 'dull'):
        pm = disc_mask(s, 2.0, cx, cy)
    elif pupil == 'small':
        pm = disc_mask(s, 1.1, cx, cy)
    elif pupil == 'huge':
        pm = disc_mask(s, 3.6, cx, cy)
    else:
        pm = np.zeros((s, s), bool)
        for y in range(int(cy - 3.6), int(cy + 4.6)):
            for x in range(int(cx), int(cx) + 2):
                if 0 <= y < s and 0 <= x < s:
                    pm[y, x] = True
    img[pm, :3] = (6, 2, 4) if pupil != 'dull' else (80, 80, 76)
    img[pm, 3] = 255
    # The wet highlight: a square dot up and to the left, never on the pupil.
    hx, hy = int(cx - 2), int(cy - 2)
    img[hy, hx, :3] = (255, 252, 246)
    img[hy, hx + 1, :3] = (255, 252, 246)
    img[hy + 1, hx, :3] = (230, 226, 220)
    return img


def eye_body(v, seed):
    rnd = random.Random(seed)
    s = 16
    tone = IRIS[v][3]
    img = np.zeros((s, s, 4), dtype=np.uint8)
    for y in range(s):
        for x in range(s):
            col = np.array(SCLERA[tone], dtype=float) * (1.0 - 0.28 * (y / s)) * (0.88 + 0.12 * math.sin(x * 0.9))
            img[y, x, :3] = np.clip(col, 0, 255)
            img[y, x, 3] = 255
    for _ in range(IRIS[v][4] + 3):
        a = rnd.uniform(0, 6.283)
        x, y = rnd.uniform(0, s), rnd.uniform(0, s)
        for _ in range(rnd.randint(4, 8)):
            a += rnd.uniform(-0.5, 0.5)
            x += math.cos(a)
            y += math.sin(a)
            img[int(y) % s, int(x) % s, :3] = (170, 30, 38)
    return img


TOOTH = [
    # (crown, shade, gum-end tint, flaw)
    ((236, 228, 200), (196, 180, 140), (186, 150, 100), None),
    ((226, 214, 176), (186, 164, 112), (176, 136, 84), None),
    ((218, 196, 124), (176, 148, 84), (160, 112, 64), 'chip'),
    ((238, 232, 212), (204, 192, 160), (190, 160, 120), 'chip'),
    ((200, 170, 100), (150, 118, 66), (130, 90, 50), 'rot'),
    ((232, 220, 188), (190, 172, 128), (180, 140, 90), None),
    ((212, 186, 112), (166, 134, 76), (150, 104, 60), 'rot'),
    ((240, 236, 218), (208, 198, 168), (196, 168, 126), None),
]


def tooth_front(v, seed):
    rnd = random.Random(seed)
    crown, shade, rootc, flaw = TOOTH[v]
    w, h = 8, 16
    img = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            # Rounded at the bottom corners (the free edge), square at the top (the gum).
            if y >= h - 3 and (x == 0 or x == w - 1) and y >= h - 2:
                continue
            if y == h - 1 and (x <= 1 or x >= w - 2):
                continue
            t = y / (h - 1)
            col = np.array(rootc, dtype=float) * (1 - min(1, t * 3.2)) + np.array(crown, dtype=float) * min(1, t * 3.2)
            if t > 0.75:
                col = col * 0.94 + np.array((250, 246, 232)) * 0.06
            if x == 0 or x == w - 1:
                col = np.array(shade, dtype=float) * (0.85 if y > 3 else 0.7)
            elif x == 1:
                col = col * 0.93
            elif x in (2, 3) and 3 < y < h - 2:
                col = col * 1.05 + 6
            img[y, x, :3] = np.clip(col, 0, 255)
            img[y, x, 3] = 255
    # A shadow line where it meets the gum.
    for x in range(w):
        img[0, x, :3] = (120, 40, 50)
    if flaw == 'chip':
        for (x, y) in ((w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2), (w - 1, h - 3)):
            img[y, x, 3] = 0
    if flaw == 'rot':
        for _ in range(7):
            x, y = rnd.randint(1, w - 2), rnd.randint(5, h - 2)
            img[y, x, :3] = (70, 44, 20)
        img[h - 5, 2:5, :3] = (46, 26, 12)
    return img


def tooth_side(v, seed):
    crown, shade, rootc, flaw = TOOTH[v]
    w, h = 8, 16
    img = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            t = y / (h - 1)
            col = np.array(rootc, dtype=float) * (1 - min(1, t * 3.0)) + np.array(shade, dtype=float) * min(1, t * 3.0)
            col = col * (0.82 + 0.1 * (x % 2))
            img[y, x, :3] = np.clip(col, 0, 255)
            img[y, x, 3] = 255
    return img


def gum_tile(v, seed):
    rnd = random.Random(seed)
    base = [(198, 86, 100), (156, 44, 60), (182, 120, 120)][v]
    light = [(236, 132, 140), (200, 76, 88), (214, 160, 154)][v]
    dark = [(130, 36, 52), (84, 16, 30), (116, 70, 76)][v]
    w, h = 32, 16
    img = np.zeros((h, w, 3), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            t = y / (h - 1)
            # From the top (the root) to the bottom (where the teeth come out): the margin is paler, the root darker.
            col = np.array(dark, dtype=float) * (1 - t) * 0.9 + np.array(base, dtype=float) * min(1, t * 1.6)
            if 9 <= y <= 11:
                col = np.array(light, dtype=float) * (0.85 + 0.15 * math.sin(x * 0.7))
            if y >= 13:
                col = col * 0.8
            img[y, x] = np.clip(col, 0, 255)
    for _ in range(3):
        walk(img, rnd, rnd.uniform(0, w), rnd.uniform(0, h), 12, dark, wobble=0.4, branch=0.0)
    for _ in range(5):
        img[rnd.randint(7, 11), rnd.randint(0, w - 1)] = (255, 226, 220)
    return Image.fromarray(img, 'RGB').convert('RGBA')


def throat_tile(seed):
    rnd = random.Random(seed)
    s = 32
    img = np.zeros((s, s, 3), dtype=np.uint8)
    for y in range(s):
        for x in range(s):
            d = math.hypot(x - 15.5, y - 15.5) / 16.0
            k = max(0.0, 1.0 - d)
            ring = 0.5 + 0.5 * math.sin(y * 0.9 + 0.8 * math.sin(x * 0.3))
            col = np.array((4, 0, 2), dtype=float) + k * np.array((40, 4, 12)) * (0.5 + 0.5 * ring)
            img[y, x] = np.clip(col, 0, 255)
    for _ in range(14):
        img[rnd.randint(2, s - 3), rnd.randint(2, s - 3)] = (112, 20, 32)
    return Image.fromarray(img, 'RGB').convert('RGBA')


def tongue_tile(seed):
    rnd = random.Random(seed)
    w, h = 32, 16
    img = np.zeros((h, w, 3), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            t = y / (h - 1)
            col = np.array((168, 44, 56), dtype=float) * (1 - t) + np.array((92, 14, 26), dtype=float) * t
            if abs(x - 15.5) < 1.0:
                col = np.array((70, 8, 18))
            if (x + y * 2) % 5 == 0 and y < 11:
                col = col * 1.18
            img[y, x] = np.clip(col, 0, 255)
    for _ in range(4):
        img[rnd.randint(1, 6), rnd.randint(3, w - 4)] = (240, 160, 160)
    return Image.fromarray(img, 'RGB').convert('RGBA')


def heart_tile(seed):
    rnd = random.Random(seed)
    s = 64
    h, peaks = height_field(s, rnd, 16)
    lit = h - np.roll(h, (2, 1), (0, 1))
    v = blur(0.5 * h + 0.5 * (0.5 + lit * 2.2), 1)
    ramp = [(70, 6, 18), (112, 10, 26), (150, 18, 34), (186, 30, 44), (214, 50, 58), (236, 86, 88), (250, 132, 130), (255, 190, 186)]
    idx = np.clip(np.floor(v * 6.4 + 0.6), 0, 7).astype(int)
    img = np.zeros((s, s, 3), dtype=np.uint8)
    for i in range(8):
        img[idx == i] = ramp[i]
    # Fat: patches of yellowish white along the top.
    for _ in range(0):
        cx, cy = rnd.randint(0, s - 1), rnd.randint(0, 22)
        for _ in range(rnd.randint(18, 40)):
            x, y = cx + rnd.randint(-5, 5), cy + rnd.randint(-3, 3)
            img[y % s, x % s] = (226, 190, 128) if rnd.random() < 0.7 else (200, 150, 96)
    # Vessels: pale, running down, branching, with a dark edge on one side.
    for _ in range(12):
        x, y = rnd.uniform(0, s), rnd.uniform(0, 14)
        a = math.pi / 2 + rnd.uniform(-0.6, 0.6)
        col = (232, 176, 176) if rnd.random() < 0.65 else (150, 120, 190)
        for _ in range(rnd.randint(24, 50)):
            a += rnd.uniform(-0.18, 0.18)
            x += math.cos(a)
            y += math.sin(a)
            yy, xx = int(y) % s, int(x) % s
            img[yy, xx] = col
            img[yy, (xx + 1) % s] = col
            img[(yy + 1) % s, xx] = col
            img[yy, (xx + 2) % s] = (80, 8, 24)
            if rnd.random() < 0.05:
                walk(img, rnd, x, y, 12, col, wobble=0.3, branch=0.0, ang=a + rnd.choice((-0.7, 0.7)))
    for (cx, cy, r, amp) in sorted(peaks, key=lambda p: -p[3])[:7]:
        sx, sy = int(cx - r * 0.35) % s, int(cy - r * 0.45) % s
        img[sy, sx] = (255, 220, 214)
        img[sy, (sx + 1) % s] = (255, 220, 214)
    return Image.fromarray(img, 'RGB').convert('RGBA')


def vessel_tile(v, seed):
    rnd = random.Random(seed)
    s = 16
    img = np.zeros((s, s, 3), dtype=np.uint8)
    base = [(214, 150, 160), (150, 112, 176)][v]
    for y in range(s):
        for x in range(s):
            k = 1.0 - 0.45 * abs(x - 7.5) / 7.5
            col = np.array(base, dtype=float) * k * (0.9 + 0.1 * math.sin(y * 0.8))
            img[y, x] = np.clip(col, 0, 255)
    img[:, 0] = (88, 24, 52)
    img[:, s - 1] = (88, 24, 52)
    for y in range(0, s, 4):
        img[y, 1:s - 1] = np.clip(img[y, 1:s - 1].astype(int) - 30, 0, 255)
    return Image.fromarray(img, 'RGB').convert('RGBA')


def main():
    atlas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for f, fam in enumerate(('pale', 'red', 'deep', 'sick')):
        for page in range(3):
            atlas.paste(flesh_tile(fam, page, 10 + f * 3 + page), (64 * f, 64 * page))
    for v in range(8):
        atlas.paste(Image.fromarray(eye_front(v, 100 + v), 'RGBA'), (16 * v, 192))
        atlas.paste(Image.fromarray(eye_body(v, 200 + v), 'RGBA'), (16 * v, 208))
        atlas.paste(Image.fromarray(tooth_front(v, 300 + v), 'RGBA'), (8 * v, 256))
        atlas.paste(Image.fromarray(tooth_side(v, 400 + v), 'RGBA'), (8 * v, 272))
    for v in range(3):
        atlas.paste(gum_tile(v, 500 + v), (32 * v, 288))
    atlas.paste(throat_tile(600), (96, 288))
    atlas.paste(tongue_tile(700), (128, 288))
    atlas.paste(heart_tile(800), (128, 192))
    atlas.paste(vessel_tile(0, 900), (192, 192))
    atlas.paste(vessel_tile(1, 901), (208, 192))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    atlas.save(OUT)
    print('wrote', os.path.normpath(OUT), atlas.size)
    # A preview, scaled up, for looking at.
    preview = atlas.resize((W * 3, H * 3), Image.NEAREST)
    bg = Image.new('RGBA', preview.size, (40, 40, 48, 255))
    bg.alpha_composite(preview)
    prev = os.environ.get('ATLAS_PREVIEW')
    if prev:
        os.makedirs(os.path.dirname(prev), exist_ok=True)
        bg.convert('RGB').save(prev)
        print('preview', prev)


if __name__ == '__main__':
    main()
