"""Storyboard for "Forever", the Eternity Devil's contract power, as pixel art at game resolution (1 px = 1/16 block),
for kortev to approve before anything is built. Same style as eternity_drawings.py (flat anime colours, ink lines),
whose palette and helpers it reuses.

The power: the target is locked in an endless moment. It freezes, and every moment of it shows at once: a line of
copies trailing back along where it came from and on to where it would have gone, fading out. Hits landed on it
are stored. After 3 s the line snaps back into the target and every stored hit goes off at once. The devil's own body
(flesh, eyes, mouths of human teeth) shows up through the ground.

Run: python tools/eternity_contract_drawings.py OUT_DIR
  ->  forever_storyboard.png (5 panels), _forever_storyboard_small.png (about 1200 px wide), forever_closeups.png"""
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageOps

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from eternity_drawings import (FLESH, FLESH_DK, FLESH_HI, FLESH_SH, INK, LABEL, LIP, PAPER, TOOTH, eye,  # noqa: E402
                               font, grin, lobe)

W, H = 224, 96                 # a panel: 14 x 6 blocks
TOP, FACE, FEET = 70, 80, 76   # back edge of the grass, where its front face starts, where figures stand
ZX = 140                       # where the zombie is when it is called
CW, CH, FOOT, CXC = 64, 48, 40, 32   # figure canvas: feet bottom at row FOOT, body centre column CXC

GRASS, GRASS2, GRASS_SH = (104, 176, 74), (92, 160, 66), (66, 124, 52)
DIRT, DIRT_SH, DIRT_HI = (150, 104, 70), (122, 82, 54), (172, 124, 86)
SKY_A, SKY_B = (118, 190, 238), (190, 228, 248)
ZSKIN, ZSKIN_DK = (108, 164, 84), (76, 124, 60)
ZSHIRT, ZSHIRT_DK = (50, 98, 184), (34, 68, 138)
ZPANTS, ZPANTS_DK = (108, 60, 150), (76, 42, 112)
PSKIN, PHAIR = (230, 178, 138), (70, 46, 34)
PSHIRT, PSHIRT_DK, PPANTS, PPANTS_DK = (62, 68, 96), (44, 48, 72), (34, 36, 58), (24, 26, 42)
RED, RED_HI = (226, 40, 52), (255, 206, 160)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ------------------------------------------------------------------ the world: sky, hills, grass blocks over dirt

def backdrop():
    img = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    for y in range(TOP):
        d.line([(0, y), (W, y)], fill=lerp(SKY_A, SKY_B, y / TOP))
    for cx, cy, w in ((34, 18, 26), (118, 12, 32), (196, 24, 22)):          # blocky clouds
        for i, (dx, dy, ww, hh) in enumerate(((0, 0, w, 5), (w * 0.2, -4, w * 0.5, 5), (-w * 0.1, 3, w * 0.8, 4))):
            d.rectangle([cx + dx, cy + dy, cx + dx + ww, cy + dy + hh], fill=(246, 250, 255))
    for x in range(W):                                                     # far hills
        h = 6 + 4 * math.sin(x / 17.0) + 3 * math.sin(x / 7.0 + 1)
        d.line([(x, TOP - h), (x, TOP)], fill=(110, 172, 120))
    rnd = random.Random(2)
    for bx in range(-1, 15):                                               # the top of the grass, a row of blocks
        x0 = bx * 16
        d.rectangle([x0, TOP, x0 + 15, FACE - 1], fill=GRASS if bx % 2 == 0 else GRASS2)
        d.line([(x0, TOP), (x0, FACE - 1)], fill=GRASS_SH)
    d.line([(0, TOP), (W, TOP)], fill=GRASS_SH)
    for _ in range(70):
        x, y = rnd.randrange(W), rnd.randrange(TOP + 1, FACE)
        d.point((x, y), fill=GRASS_SH if rnd.random() < 0.5 else (126, 196, 90))
    d.rectangle([0, FACE, W, H], fill=DIRT)                                # the front face, dirt under a grass fringe
    for bx in range(-1, 15):
        x0 = bx * 16
        d.line([(x0, FACE), (x0, H)], fill=DIRT_SH)
    d.line([(0, FACE + 7), (W, FACE + 7)], fill=DIRT_SH)
    d.line([(0, H - 1), (W, H - 1)], fill=DIRT_SH)
    for _ in range(90):
        x, y = rnd.randrange(W), rnd.randrange(FACE + 3, H)
        d.point((x, y), fill=DIRT_SH if rnd.random() < 0.55 else DIRT_HI)
    for x in range(W):
        d.line([(x, FACE), (x, FACE + 2 + (x * 7 % 3 == 0))], fill=GRASS_SH if x % 5 else GRASS)
    return img


def grade(im, sat=1.0, sepia=0.0, alpha=1.0, tint=None, tint_amt=0.0):
    """Colour-drain a layer: less colour, towards sepia, a pale tint, and see-through."""
    a = im.getchannel('A')
    rgb = ImageEnhance.Color(im.convert('RGB')).enhance(sat)
    if sepia:
        rgb = Image.blend(rgb, ImageOps.colorize(rgb.convert('L'), (34, 22, 16), (255, 236, 196)), sepia)
    if tint:
        rgb = Image.blend(rgb, Image.new('RGB', rgb.size, tint), tint_amt)
    out = rgb.convert('RGBA')
    out.putalpha(a.point(lambda v: int(v * alpha)))
    return out


def drain(base, cx, cy, rx, ry, amt):
    """The air round the target losing its colour: a soft oval of grey-sepia."""
    mask = Image.new('L', base.size, 0)
    ImageDraw.Draw(mask).ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=int(255 * amt))
    mask = mask.filter(ImageFilter.GaussianBlur(min(rx, ry) * 0.3))
    out = base.copy()
    out.paste(grade(base, sat=0.2, sepia=0.8), (0, 0), mask)
    return out


# ------------------------------------------------------------------ the zombie and the player (Minecraft shapes)

def ink_out(layer):
    """A one-pixel ink line round the outside of a figure."""
    a = layer.getchannel('A').point(lambda v: 255 if v > 0 else 0)
    grown = a.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grown = ImageChops.lighter(grown, ImageChops.offset(a, dx, dy))
    ring = Image.new('RGBA', layer.size, INK + (255,))
    ring.putalpha(ImageChops.subtract(grown, a))
    ring.alpha_composite(layer)
    return ring


def limb(d, px, py, ang, length, th, c1, c2=None, split=None):
    a = math.radians(ang)
    dx, dy = math.cos(a), math.sin(a)
    nx, ny = -dy, dx

    def quad(t0, t1, col):
        d.polygon([(px + dx * t0 + nx * th / 2, py + dy * t0 + ny * th / 2),
                   (px + dx * t1 + nx * th / 2, py + dy * t1 + ny * th / 2),
                   (px + dx * t1 - nx * th / 2, py + dy * t1 - ny * th / 2),
                   (px + dx * t0 - nx * th / 2, py + dy * t0 - ny * th / 2)], fill=col + (255,))
    if split:
        quad(0, split, c1)
        quad(split - 0.4, length, c2)
    else:
        quad(0, length, c1)
    return px + dx * length, py + dy * length


def legs(d, s, col_a, col_b):
    for x0, dx, col in ((30, s, col_b), (30, -s, col_a)):                 # far leg back, near leg front
        d.polygon([(x0, 28), (x0 + 3.99, 28), (x0 + 3.99 + dx, 39.99), (x0 + dx, 39.99)], fill=col + (255,))


def zombie(phase=1.0, arm=6.0):
    """Side-on, facing left, arms out in front. phase: where it is in its walk; arm: how high the arms reach."""
    im = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s = 5 * math.sin(phase)
    limb(d, CXC, 18, 180 + arm, 12, 4, ZSHIRT_DK, ZSKIN_DK, 4)            # far arm
    d.polygon([(30, 28), (33.99, 28), (33.99 + s, 39.99), (30 + s, 39.99)], fill=ZPANTS_DK + (255,))   # far leg
    d.rectangle([30, 16, 33, 27], fill=ZSHIRT + (255,))
    d.line([(33, 16), (33, 27)], fill=ZSHIRT_DK + (255,))
    d.polygon([(30, 28), (33.99, 28), (33.99 - s, 39.99), (30 - s, 39.99)], fill=ZPANTS + (255,))     # near leg
    d.rectangle([28, 8, 35, 15], fill=ZSKIN + (255,))
    d.rectangle([28, 8, 35, 9], fill=ZSKIN_DK + (255,))
    d.rectangle([29, 11, 30, 12], fill=(28, 44, 30, 255))
    d.line([(29, 14), (31, 14)], fill=ZSKIN_DK + (255,))
    limb(d, CXC + 1, 19, 180 + arm * 0.7, 12, 4, ZSHIRT, ZSKIN, 4)        # near arm
    return ink_out(im)


def sword(d, hx, hy, ang):
    a = math.radians(ang)
    dx, dy = math.cos(a), math.sin(a)
    limb(d, hx - dx * 2, hy - dy * 2, ang, 5, 2, (120, 78, 48))             # handle
    limb(d, hx + dx * 2, hy + dy * 2 - 0, ang + 90, 3.5, 1.6, (172, 134, 70))   # guard
    limb(d, hx + dx * 2, hy + dy * 2, ang - 90, 3.5, 1.6, (172, 134, 70))
    limb(d, hx + dx * 3, hy + dy * 3, ang, 15, 2.6, (206, 214, 228))        # blade
    limb(d, hx + dx * 3, hy + dy * 3, ang, 15, 1.0, (250, 252, 255))


def player(arm=-8.0, blade=False, back=100.0, point=True, step=0.0):
    """Side-on, facing right, dark clothes. arm: front arm angle (0 = out to the right, negative = up)."""
    im = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    limb(d, CXC, 18, back, 12, 4, PSHIRT_DK, PSKIN, 8)                     # far arm
    d.polygon([(30, 28), (33.99, 28), (33.99 + step, 39.99), (30 + step, 39.99)], fill=PPANTS_DK + (255,))
    d.rectangle([30, 16, 33, 27], fill=PSHIRT + (255,))
    d.line([(30, 16), (30, 27)], fill=PSHIRT_DK + (255,))
    d.line([(33, 17), (33, 21)], fill=(196, 44, 56, 255))                  # a red tie
    d.polygon([(30, 28), (33.99, 28), (33.99 - step, 39.99), (30 - step, 39.99)], fill=PPANTS + (255,))
    d.rectangle([28, 8, 35, 15], fill=PSKIN + (255,))
    d.rectangle([28, 8, 35, 9], fill=PHAIR + (255,))
    d.rectangle([28, 8, 29, 13], fill=PHAIR + (255,))
    d.rectangle([33, 11, 33, 12], fill=(250, 250, 250, 255))
    d.rectangle([34, 11, 34, 12], fill=(40, 52, 96, 255))
    ex, ey = limb(d, CXC + 1, 18, arm, 12, 4, PSHIRT, PSKIN, 8)             # front arm
    if blade:
        sword(d, ex, ey, arm)
    elif point:
        a = math.radians(arm)
        limb(d, ex, ey, arm, 4, 1.4, PSKIN)                                 # a pointing finger
    return ink_out(im)


def put(img, sprite, foot_x, dy=0, dx=0):
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    layer.paste(sprite, (round(foot_x - CXC + dx), FEET - FOOT + dy))
    img.alpha_composite(layer)


# ------------------------------------------------------------------ the devil through the ground

def build_lumps():
    r = random.Random(7)
    out = []
    for j in range(-4, 5):
        near = 1 - abs(j) / 5
        x = ZX + j * 17 + r.randint(-2, 2)
        y = r.choice((75, 77, 78, 76))
        eyed = (j % 2 == 0) or r.random() < 0.5
        out.append(dict(j=j, x=x, y=y, rx=5 + near * 2.5 + r.random(), ry=2.6 + near * 1.2 + r.random() * 0.6,
                        eye=(round(7 + near * 2 + r.random()), round(4 + near * 1.5)) if eyed else None,
                        wake=r.choice((0.55, 0.3, 0.0, 0.7)), seed=r.randrange(1000)))
    return out


LUMPS = build_lumps()
MOUTHS = [(88, 11), (114, 13), (140, 14), (166, 13), (192, 11)]   # (x, width) of the mouths in the front face


def crack_line(d, pts, wide, flesh=True):
    d.line(pts, fill=INK + (255,), width=wide + 2 if wide > 1 else 1)
    if wide > 1 and flesh:
        d.line(pts, fill=FLESH_DK + (255,), width=wide)


def draw_cracks(img, lumps, wide, face_open, seed=3):
    r = random.Random(seed)
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for a, b in zip(lumps, lumps[1:]):                                     # seams along the grass between lumps
        pts = [(a['x'], a['y'])]
        n = max(2, int(abs(b['x'] - a['x']) / 3))
        for i in range(1, n):
            t = i / n
            pts.append((a['x'] + (b['x'] - a['x']) * t, a['y'] + (b['y'] - a['y']) * t + r.choice((-2, -1, 0, 1, 2))))
        pts.append((b['x'], b['y']))
        crack_line(d, pts, wide)
    for lump in lumps[::2]:                                                # short splits running off across the grass
        x, y = lump['x'], lump['y']
        for sgn in (-1, 1):
            if r.random() < 0.6:
                pts = [(x, y)]
                for i in range(1, 4):
                    pts.append((x + sgn * i * 4, y + r.choice((-3, -2, 2, 3)) * (1 if i % 2 else -1) * 0.7))
                crack_line(d, pts, max(1, wide - 1))
    for mx, mw in MOUTHS:                                                  # splits down the front face
        if not any(abs(lump['x'] - mx) < 24 for lump in lumps):
            continue
        pts = [(mx, FACE - 1)]
        for i in range(1, 6):
            pts.append((mx + r.choice((-3, -2, 2, 3)) * (1 if i % 2 else -1) * 0.8, FACE - 1 + i * 3.2))
        crack_line(d, pts, wide if face_open else 1)
    img.alpha_composite(layer)


def look_at(x, y, tx=ZX, ty=58):
    dx, dy = tx - x, ty - y
    n = math.hypot(dx, dy) or 1
    return (dx / n * 1.1, dy / n * 1.1)


def draw_lump(img, lump, scale, eye_mode, eyes_scale=1.0):
    lobe(img, lump['x'], lump['y'], lump['rx'] * scale, lump['ry'] * scale, seed=lump['seed'])
    if lump['eye'] and eye_mode != 'none':
        w, h = lump['eye']
        w, h = max(4, round(w * eyes_scale)), max(3, round(h * eyes_scale))
        lid = {'wake': lump['wake'], 'stare': 0.0, 'shut': 1.0, 'sleepy': 0.8}[eye_mode]
        eye(img, lump['x'], lump['y'] - 1, w, h, look_at(lump['x'], lump['y']), lid)


def draw_mouths(img, mode):
    """Mouths of uneven human teeth in the front face: 'open', 'shut' (bitten), 'small'."""
    for mx, mw in MOUTHS:
        if mode == 'open':
            lobe(img, mx, 89, mw / 2 + 3, 6.5, seed=mx)
            grin(img, mx, 88, mw, 7, tooth=2, tongue=False, seed=mx)
        elif mode == 'shut':
            lobe(img, mx, 89, mw / 2 + 3, 5, seed=mx)
            grin(img, mx, 89, mw, 2, tooth=3 if mw > 12 else 2, tongue=False, seed=mx)
        elif mode == 'small' and mx in (112, 168):
            lobe(img, mx, 90, 4, 2, seed=mx)


def strand(layer, x0, y0, x1, y1, r, bulge=3.0):
    """A thin strand of flesh from a seam up to a copy, wobbling a little."""
    d = ImageDraw.Draw(layer)
    pts = []
    n = 16
    ph = r.random() * 6
    for i in range(n + 1):
        t = i / n
        pts.append((x0 + (x1 - x0) * t + math.sin(t * 5 + ph) * bulge * math.sin(t * math.pi),
                    y0 + (y1 - y0) * t))
    d.line(pts, fill=INK + (255,), width=3)
    d.line(pts, fill=FLESH + (255,), width=1)
    d.ellipse([x1 - 2.5, y1 - 2.5, x1 + 2.5, y1 + 2.5], fill=INK + (255,))
    d.ellipse([x1 - 1.5, y1 - 1.5, x1 + 1.5, y1 + 1.5], fill=FLESH + (255,))


# ------------------------------------------------------------------ marks: stored hits, the burst, dust

def slash(img, cx, cy, ang, length, glow=True):
    """A red slash that hangs on the target and glows."""
    a = math.radians(ang)
    dx, dy = math.cos(a), math.sin(a)
    n = 14
    left, right = [], []
    for i in range(n + 1):
        t = i / n
        hw = math.sin(t * math.pi) ** 0.8 * 2.0
        px, py = cx + dx * length * (t - 0.5), cy + dy * length * (t - 0.5)
        left.append((px - dy * hw, py + dx * hw))
        right.append((px + dy * hw, py - dx * hw))
    poly = left + right[::-1]
    if glow:
        g = Image.new('RGBA', img.size, (0, 0, 0, 0))
        ImageDraw.Draw(g).polygon(poly, fill=(255, 70, 70, 255))
        ImageDraw.Draw(g).line([poly[0], poly[n]], fill=(255, 90, 90, 255), width=3)
        g = g.filter(ImageFilter.GaussianBlur(3.2))
        g.putalpha(g.getchannel('A').point(lambda v: min(255, v * 2)))
        img.alpha_composite(g)
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.polygon([(x + (x - cx) * 0.0, y) for x, y in poly], fill=INK + (255,))
    d.line([left[0], left[n // 2], left[n]], fill=INK + (255,), width=4)
    d.polygon(poly, fill=RED + (255,))
    d.line([(cx + dx * length * (t - 0.5), cy + dy * length * (t - 0.5)) for t in (0.12, 0.88)], fill=RED_HI + (255,),
           width=1)
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    for t, side in ((0.3, 1), (0.65, -1)):                                 # two hairline cracks off the slash
        px, py = cx + dx * length * (t - 0.5), cy + dy * length * (t - 0.5)
        d.line([(px, py), (px - dy * side * 4 + dx * 2, py + dx * side * 4 + dy * 2)], fill=RED + (255,))


def star(d, cx, cy, r_out, r_in, n, fill, rot=0.0, seed=1):
    r = random.Random(seed)
    pts = []
    for i in range(n * 2):
        rr = (r_out * r.uniform(0.75, 1.15)) if i % 2 == 0 else r_in * r.uniform(0.8, 1.2)
        a = rot + math.pi * i / n
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=fill)


def wedge(d, cx, cy, ang, r0, r1, w, fill):
    a = math.radians(ang)
    dx, dy = math.cos(a), math.sin(a)
    d.polygon([(cx + dx * r0 - dy * w, cy + dy * r0 + dx * w), (cx + dx * r1, cy + dy * r1),
               (cx + dx * r0 + dy * w, cy + dy * r0 - dx * w)], fill=fill)


def burst(img, cx, cy, size=1.0, seed=5):
    """Every stored hit going off at once: red impact lines, ink-black streaks, a white flash."""
    r = random.Random(seed)
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for k in range(16):                                                    # red impact lines
        ang = k * 22.5 + r.uniform(-6, 6)
        wedge(d, cx, cy, ang, 12 * size, r.uniform(40, 62) * size, r.uniform(2.2, 3.8) * size, RED + (255,))
    for k in range(7):                                                     # ink-black streaks, long and thin
        ang = k * 51 + r.uniform(-8, 8) + 11
        wedge(d, cx, cy, ang, 14 * size, r.uniform(54, 82) * size, r.uniform(1.4, 2.4) * size, INK + (255,))
    star(d, cx, cy, 34 * size, 14 * size, 11, (255, 226, 190, 255), 0.3, seed)
    star(d, cx, cy, 26 * size, 10 * size, 11, (255, 255, 255, 255), 0.5, seed + 1)
    img.alpha_composite(layer)


def chips(img, cx, cy, r, count, seed=4):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        a = rnd.uniform(0, math.tau)
        dist = rnd.uniform(0.35, 1.0) * r
        x, y = cx + math.cos(a) * dist, cy + math.sin(a) * dist * 0.8
        if y > FEET + 3:
            continue
        col = rnd.choice((DIRT, DIRT_SH, GRASS, FLESH, FLESH_SH))
        s = rnd.choice((1, 2, 2, 3))
        d.rectangle([x, y, x + s, y + s], fill=col + (255,), outline=INK + (255,))


def puff(img, x, y, r):
    lobe(img, x, y, r * 1.2, r, base=(238, 234, 226), shade=(196, 190, 184), hi=(255, 255, 255))


def dizzy(img, cx, cy):
    d = ImageDraw.Draw(img)
    for i, (dx, dy) in enumerate(((-9, 2), (0, -3), (9, 3))):
        x, y = cx + dx, cy + dy
        pts = [(x, y - 4), (x + 1.2, y - 1.2), (x + 4, y), (x + 1.2, y + 1.2), (x, y + 4), (x - 1.2, y + 1.2),
               (x - 4, y), (x - 1.2, y - 1.2)]
        d.polygon(pts, fill=(255, 224, 72, 255), outline=INK + (255,))


# ------------------------------------------------------------------ the five panels

N_COPY = 4
SPACING = 15
FADE = (0.8, 0.58, 0.36, 0.17)
WALK = 1.25                     # the zombie's pose when locked: mid-stride
STEP = 0.9                      # one copy = this much further through the walk cycle


def copy_sprite(phase, k):
    s = grade(zombie(phase, 6 + 2 * math.sin(phase * 2)), sat=0.3, sepia=0.6, alpha=FADE[abs(k) - 1],
              tint=(170, 205, 235), tint_amt=0.3)
    return s


def line_of_copies(img, centre=ZX, spacing=SPACING, phase0=WALK, streaks=False):
    """Moments of the zombie's walk, earlier ones behind it (right) and later ones ahead (left)."""
    for k in range(N_COPY, 0, -1):
        for sgn in (1, -1):                                                # sgn +1 is the past, -1 is the future
            x = centre + sgn * k * spacing
            put(img, copy_sprite(phase0 - sgn * k * STEP, k), x)


def stage_scene(stage):
    r = random.Random(stage * 17)
    bg = backdrop()
    if stage in (2, 3):
        bg = drain(bg, ZX, 56, 96, 62, 0.92)
    img = bg
    near = [lump for lump in LUMPS if abs(lump['j']) <= 2]
    p_x = 28 if stage <= 2 else 108

    if stage == 1:
        lumps, wide, scale, eye_mode = near, 2, 0.85, 'wake'
    elif stage in (2, 3):
        lumps, wide, scale, eye_mode = LUMPS, 3, 1.0, 'stare'
    elif stage == 4:
        lumps, wide, scale, eye_mode = LUMPS, 3, 0.9, 'shut'
    else:
        lumps = [lump for lump in LUMPS if lump['j'] in (-3, -1, 2)]
        wide, scale, eye_mode = 1, 0.38, 'sleepy'
    draw_cracks(img, lumps, wide, stage in (1, 2, 3, 4))
    back = list(lumps)
    front = []
    es = 0.5 if stage == 5 else 1.0
    for lump in sorted(back, key=lambda lu: lu['y']):
        draw_lump(img, lump, scale, eye_mode, es)

    # ---------------------------------------------------------------- the people
    if stage == 1:
        put(img, player(-6), p_x)
        put(img, zombie(WALK, 6), ZX)
        chips(img, ZX, 74, 40, 24)
    elif stage == 2:
        put(img, player(-6), p_x)
        line_of_copies(img)
        put(img, grade(zombie(WALK, 6), sat=0.4, sepia=0.5), ZX)
    elif stage == 3:
        line_of_copies(img)
        put(img, grade(zombie(WALK, 6), sat=0.4, sepia=0.5), ZX)
        put(img, player(-38, blade=True, step=2), p_x)
        # the swing's trail
        d = ImageDraw.Draw(img)
        d.arc([p_x - 22, 18, p_x + 26, 66], 285, 345, fill=(255, 255, 255, 255), width=2)
        d.arc([p_x - 18, 22, p_x + 22, 62], 292, 340, fill=(255, 255, 255, 255), width=1)
    elif stage == 4:
        line_of_copies(img, centre=ZX + 6, spacing=4)
        d = ImageDraw.Draw(img)
        for k in range(1, N_COPY + 1):                                     # the line snapping inward: speed streaks
            for sgn in (1, -1):
                x_far = ZX + 6 + sgn * (k * SPACING + 8)
                x_near = ZX + 6 + sgn * (k * 4 + 3)
                y = 46 + (k * 5) % 22
                d.line([(x_far, y), (x_near, y)], fill=(255, 255, 255, 255), width=1)
                d.line([(x_far - sgn * 4, y + 1), (x_near + sgn * 6, y + 1)], fill=(255, 255, 255, 255), width=1)
        burst(img, ZX + 8, 56, 0.72)
        put(img, player(0, blade=True, step=-2), p_x)
        zs = zombie(0.5, 12).rotate(-30, resample=Image.BICUBIC, center=(CXC, FOOT))
        zs = grade(zs, tint=(255, 255, 255), tint_amt=0.28)
        put(img, zs, ZX + 34, dy=-8)
        chips(img, ZX + 8, 58, 60, 36)
    else:
        put(img, player(60, blade=False, point=False, back=90), p_x)
        lying = zombie(1.0, 20).rotate(-90, resample=Image.NEAREST, expand=True)
        bb = lying.getbbox()
        lx = round(ZX + 36 - lying.width / 2)
        ly = 78 - bb[3]
        layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
        layer.paste(lying, (lx, ly))
        img.alpha_composite(layer)
        dizzy(img, lx + bb[2] - 4, ly + bb[1] - 6)
        for x, y, rr in ((ZX - 6, 73, 4), (ZX + 2, 75, 3), (ZX - 14, 74, 3), (ZX + 78, 73, 4)):
            puff(img, x, y, rr)
    for lump in sorted(front, key=lambda lu: lu['y']):
        draw_lump(img, lump, scale, eye_mode, es)

    # ---------------------------------------------------------------- the line held in place; the stored hits; mouths
    if stage == 2:
        sl = Image.new('RGBA', img.size, (0, 0, 0, 0))
        for k in range(-N_COPY, N_COPY + 1):
            x = ZX - k * SPACING
            for tx, ty in ((x - 6, 60),):
                strand(sl, x + r.uniform(-2, 2), 77, tx, ty, r, 2.0)
        fade = Image.new('RGBA', img.size, (255, 255, 255, 255))
        mask = Image.new('L', img.size, 0)
        md = ImageDraw.Draw(mask)
        for k in range(-N_COPY, N_COPY + 1):
            a = 255 if k == 0 else int(255 * min(1.0, FADE[abs(k) - 1] + 0.4))
            md.rectangle([ZX - k * SPACING - SPACING / 2, 0, ZX - k * SPACING + SPACING / 2, H], fill=a)
        sl.putalpha(ImageChops.multiply(sl.getchannel('A'), mask))
        img.alpha_composite(sl)
        del fade
    if stage == 3:
        for dx, dy, ang, ln in ((-1, -28, -40, 15), (2, -20, 28, 17), (-3, -14, -8, 20), (1, -9, 62, 15),
                                (-2, -23, 8, 12)):
            slash(img, ZX + dx, FEET + dy, ang, ln + 6)
        sl = Image.new('RGBA', img.size, (0, 0, 0, 0))
        for lump in LUMPS:                                                  # seam strands hold the line still
            pass
        draw_mouths(img, 'open')
    elif stage == 4:
        draw_mouths(img, 'shut')
        d = ImageDraw.Draw(img)
        for mx, mw in MOUTHS:                                               # the snap of each bite
            for sgn in (-1, 1):
                x = mx + sgn * (mw / 2 + 7)
                d.line([(x, 84), (x + sgn * 4, 82)], fill=(255, 255, 255, 255), width=1)
                d.line([(x, 89), (x + sgn * 5, 89)], fill=(255, 255, 255, 255), width=1)
    elif stage == 5:
        draw_mouths(img, 'small')
    return img


CAPTIONS = [
    ('1  THE CALL', 'You point. Round the zombie the ground splits and the devil\'s flesh pushes up through the '
     'cracks in low lumps. Eyes open in it, all turning to stare at the zombie.'),
    ('2  LOCKED', 'Colour drains from the zombie and the air round it, and it freezes mid-step. Every moment of its '
     'walk shows at once: copies trailing back the way it came and on ahead toward you, fading into nothing. '
     'Strands of flesh hold the line in place. The eyes never blink.'),
    ('3  STORED HITS', 'Each hit you land does not land. It hangs on the zombie as a glowing red mark that stays. '
     'The devil\'s mouths open along the seams, grinning, as if counting.'),
    ('4  THE MOMENT ENDS', 'After 3 seconds the eyes snap shut, the mouths bite shut, the whole line of copies '
     'snaps into the zombie, and every stored hit goes off at once. The zombie is thrown back.'),
    ('5  GONE', 'The flesh sinks back into the ground, the cracks close, the colour comes back. '
     'The zombie is down, dizzy.'),
]


def wrap(d, text, f, width):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if d.textlength(t, font=f) <= width:
            cur = t
        else:
            lines.append(cur)
            cur = w
    lines.append(cur)
    return lines


def sheet(S=7):
    f_t, f_s = font(40), font(28)
    pad = 24
    pw, ph = W * S, H * S
    probe = ImageDraw.Draw(Image.new('RGB', (10, 10)))
    heights = [64 + 36 * len(wrap(probe, c[1], f_s, pw)) + 20 for c in CAPTIONS]
    total = pad + sum(ph + hh + 10 for hh in heights)
    out = Image.new('RGB', (pw + 2 * pad, total), PAPER)
    d = ImageDraw.Draw(out)
    y = pad
    for stage, (title, text) in enumerate(CAPTIONS, 1):
        out.paste(scene_cache(stage).resize((pw, ph), Image.NEAREST).convert('RGB'), (pad, y))
        d.rectangle([pad - 2, y - 2, pad + pw + 1, y + ph + 1], outline=LABEL, width=2)
        ty = y + ph + 12
        d.text((pad, ty), title, fill=LABEL, font=f_t)
        for line in wrap(d, text, f_s, pw):
            ty += 36 if line is not None else 0
            d.text((pad, ty + 18), line, fill=LABEL, font=f_s)
        y += ph + heights[stage - 1] + 10
    return out


_CACHE = {}


def scene_cache(stage):
    if stage not in _CACHE:
        _CACHE[stage] = stage_scene(stage)
    return _CACHE[stage]


# ------------------------------------------------------------------ the close-ups

def seam_closeup():
    """One flesh seam in the grass at game size: two or three eyes and a grinning mouth."""
    img = backdrop()
    # one seam, three lumps, eyes all looking the same way
    lumps = [dict(x=112, y=76, rx=9, ry=4.6, eye=(10, 6), seed=3, wake=0.0, j=0),
             dict(x=128, y=77, rx=8, ry=4.2, eye=(8, 5), seed=4, wake=0.0, j=1),
             dict(x=143, y=75, rx=7, ry=4, eye=(8, 5), seed=5, wake=0.0, j=2)]
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    r = random.Random(1)
    pts = [(96, 74)]
    for x in range(99, 164, 3):
        pts.append((x, 76 + r.choice((-2, -1, 0, 1, 2))))
    crack_line(d, pts, 3)
    for sx in (123, 135):                                                  # the seam splits the front face too
        crack_line(d, [(sx, FACE - 1)] + [(sx + r.choice((-3, 3)), FACE + 3 * i) for i in range(1, 6)], 3)
    img.alpha_composite(layer)
    for lump in lumps:
        lobe(img, lump['x'], lump['y'], lump['rx'], lump['ry'], seed=lump['seed'])
        w, h = lump['eye']
        eye(img, lump['x'], lump['y'] - 1, w, h, (-0.8, 0.5), 0.0)
    lobe(img, 129, 89, 12, 6, seed=9)
    grin(img, 129, 88, 17, 8, tooth=3, tongue=True, seed=9)
    return img.crop((88, 54, 168, 96))


def closeups():
    S1, S2 = 14, 8
    a = seam_closeup()
    b = scene_cache(2).crop((56, 24, 224, 96))
    f_t, f_s = font(34), font(24)
    pad = 28
    pa = a.resize((a.width * S1, a.height * S1), Image.NEAREST).convert('RGB')
    pb = b.resize((b.width * S2, b.height * S2), Image.NEAREST).convert('RGB')
    w = max(pa.width, pb.width) + 2 * pad
    h = pad + 50 + pa.height + 90 + 50 + pb.height + 70
    out = Image.new('RGB', (w, h), PAPER)
    d = ImageDraw.Draw(out)
    y = pad
    d.text((pad, y), 'ONE FLESH SEAM IN THE GRASS, at game size, 14 times up', fill=LABEL, font=f_t)
    y += 50
    out.paste(pa, (pad, y))
    d.rectangle([pad - 2, y - 2, pad + pa.width + 1, y + pa.height + 1], outline=LABEL, width=2)
    y += pa.height + 12
    blk = 16 * S1
    d.line([(pad, y + 6), (pad + blk, y + 6)], fill=LABEL, width=4)
    d.line([(pad, y), (pad, y + 12)], fill=LABEL, width=3)
    d.line([(pad + blk, y), (pad + blk, y + 12)], fill=LABEL, width=3)
    d.text((pad + blk + 14, y + 6), '= 1 block. The big eye is 0.6 of a block wide, the mouth about one block.',
           fill=LABEL, font=f_s, anchor='lm')
    y += 70
    d.text((pad, y), 'THE LOCKED ZOMBIE\'S LINE OF COPIES, 8 times up', fill=LABEL, font=f_t)
    y += 50
    out.paste(pb, (pad, y))
    d.rectangle([pad - 2, y - 2, pad + pb.width + 1, y + pb.height + 1], outline=LABEL, width=2)
    y += pb.height + 12
    d.text((pad, y), 'Real zombie in the middle, four moments behind it (right) and four ahead (left), each one '
           'step further through the walk and more see-through.', fill=LABEL, font=f_s)
    return out


def main(out):
    os.makedirs(out, exist_ok=True)
    s = sheet()
    s.save(os.path.join(out, 'forever_storyboard.png'))
    small = s.resize((1200, round(s.height * 1200 / s.width)), Image.LANCZOS)
    small.save(os.path.join(out, '_forever_storyboard_small.png'))
    closeups().save(os.path.join(out, 'forever_closeups.png'))
    print('wrote', out)


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'work/eternity_contract_look')
