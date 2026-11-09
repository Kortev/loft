"""Block textures for the Eternity Devil's hotel, a new look (Chainsaw Man mod). 16 x 16, painted the way Minecraft textures are:
a few flat tones to an area, a little noise, dark pixels in the creases.

The show's hotel is an ordinary Japanese business hotel, so it is patterned carpet, striped cream wallpaper over a wood
wainscot, brass-plated room doors, warm lamps, a lift with a floor indicator, a reception with a bell and a key board, a
concrete-and-tile front with rows of windows and a made-up name (HOTEL ASAGAO). The rot comes in steps (see STAGES): floors
1-2 clean, 3-4 stained and dim, 5-6 peeling and damp, 7 veins of flesh under the paper, 8 clean again and only wrong. For the
hunt there are a wallpaper eye that opens, a wall that breathes and a wall that bleeds.

Run (system Python with Pillow):

    python tools/hotel_textures.py                 # writes work/eternity_look_v2/textures/*.png and textures_sheet.png

`build()` returns {name: 16 x 16 RGBA image} (the sign strips are in {name: image} too, in tiles of 16), which the render
scripts pack into one atlas.
"""
import os
import random
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:  # inside Blender's Python: Pillow installed beside it
    sys.path.append(os.environ.get('ETERNITY_PIL', r'C:\Users\gamek\blender_pil'))
    from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'work', 'eternity_look_v2')

STAGES = ('clean', 'stained', 'damp', 'veins', 'wrong')
STAGE_OF_FLOOR = {1: 'clean', 2: 'clean', 3: 'stained', 4: 'stained', 5: 'damp', 6: 'damp', 7: 'veins', 8: 'wrong'}

INK = (24, 12, 14)


# --- small painting kit --------------------------------------------------------------------------------------------

def rgb(c):
    return tuple(int(max(0, min(255, v))) for v in c[:3])


def shade(c, f):
    return rgb((c[0] * f, c[1] * f, c[2] * f))


def mix(a, b, t):
    return rgb((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t))


class Tex:
    """A 16 x 16 (or w x h) canvas with a seeded random source."""

    def __init__(self, base, seed=0, w=16, h=16):
        self.w, self.h = w, h
        self.p = [[rgb(base)] * w for _ in range(h)]
        self.r = random.Random(seed)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = rgb(c)

    def get(self, x, y):
        return self.p[y % self.h][x % self.w]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def frame(self, x0, y0, x1, y1, c):
        self.hline(x0, x1, y0, c)
        self.hline(x0, x1, y1, c)
        self.vline(x0, y0, y1, c)
        self.vline(x1, y0, y1, c)

    def noise(self, amount=0.06, p=0.5):
        """Brightens or darkens scattered pixels by a few percent."""
        for y in range(self.h):
            for x in range(self.w):
                if self.r.random() < p:
                    self.p[y][x] = shade(self.p[y][x], 1 + self.r.uniform(-amount, amount))

    def speckle(self, c, n, in_rect=None):
        for _ in range(n):
            if in_rect:
                x = self.r.randint(in_rect[0], in_rect[2])
                y = self.r.randint(in_rect[1], in_rect[3])
            else:
                x, y = self.r.randrange(self.w), self.r.randrange(self.h)
            self.set(x, y, c)

    def blob(self, cx, cy, rx, ry, c, jag=0.35, only=None):
        """An irregular patch."""
        for y in range(int(cy - ry - 2), int(cy + ry + 3)):
            for x in range(int(cx - rx - 2), int(cx + rx + 3)):
                d = ((x - cx) / max(rx, 0.5)) ** 2 + ((y - cy) / max(ry, 0.5)) ** 2
                if d + self.r.uniform(-jag, jag) < 1:
                    if only is None or only(self.get(x, y)):
                        self.set(x, y, c)

    def streak(self, x, y0, y1, c, wob=0.0):
        for y in range(y0, y1 + 1):
            xx = x + (1 if self.r.random() < wob else 0)
            self.set(xx, y, c)

    def line(self, pts, c):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = max(abs(x1 - x0), abs(y1 - y0), 1)
            for i in range(n + 1):
                self.set(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c)

    def mirror_x(self):
        for y in range(self.h):
            for x in range(self.w // 2):
                self.p[y][self.w - 1 - x] = self.p[y][x]

    def image(self):
        img = Image.new('RGB', (self.w, self.h))
        for y in range(self.h):
            for x in range(self.w):
                img.putpixel((x, y), self.p[y][x])
        return img


# A 3 x 5 pixel font, enough for the signs, the plates and the lift's indicator.
FONT = {
    'A': '010101111101101', 'B': '110101110101110', 'C': '011100100100011', 'D': '110101101101110',
    'E': '111100110100111', 'F': '111100110100100', 'G': '011100101101011', 'H': '101101111101101',
    'I': '111010010010111', 'J': '001001001101010', 'K': '101101110101101', 'L': '100100100100111',
    'M': '101111111101101', 'N': '111101101101101', 'O': '010101101101010', 'P': '110101110100100',
    'Q': '010101101110011', 'R': '110101110101101', 'S': '011100010001110', 'T': '111010010010010',
    'U': '101101101101111', 'V': '101101101101010', 'W': '101101111111101', 'X': '101101010101101',
    'Y': '101101010010010', 'Z': '111001010100111',
    '0': '111101101101111', '1': '010110010010111', '2': '110001010100111', '3': '110001010001110',
    '4': '101101111001001', '5': '111100110001110', '6': '011100111101111', '7': '111001010010010',
    '8': '111101111101111', '9': '111101111001110', ' ': '000000000000000', '-': '000000111000000',
}


def text(t, s, x, y, c, scale=1, gap=1):
    """Draws a string on a Tex."""
    for ch in s.upper():
        g = FONT.get(ch, FONT[' '])
        for i, bit in enumerate(g):
            if bit == '1':
                for dy in range(scale):
                    for dx in range(scale):
                        t.set(x + (i % 3) * scale + dx, y + (i // 3) * scale + dy, c)
        x += 3 * scale + gap * scale


def text_width(s, scale=1, gap=1):
    return len(s) * (3 + gap) * scale - gap * scale


# --- colours --------------------------------------------------------------------------------------------------------

CARPET = (98, 30, 36)
CARPET_DARK = (72, 20, 28)
CARPET_PATTERN = (146, 98, 62)
CREAM = (228, 216, 186)
CREAM_STRIPE = (212, 198, 162)
WOOD = (96, 58, 36)
WOOD_DARK = (66, 38, 24)
WOOD_LIGHT = (126, 80, 50)
BRASS = (206, 164, 66)
BRASS_HI = (240, 206, 108)
BRASS_LO = (138, 98, 40)
FLESH = (204, 108, 98)
FLESH_DARK = (150, 64, 66)
FLESH_LIGHT = (226, 140, 126)
VEIN = (176, 70, 82)
BLOOD = (120, 14, 22)
BLOOD_BRIGHT = (168, 22, 30)


# --- the corridor ----------------------------------------------------------------------------------------------------

def carpet(stage, edge=False, seed=1):
    base = CARPET_DARK if edge else CARPET
    pattern = CARPET_PATTERN
    if stage == 'wrong':
        base = (112, 34, 40)
        pattern = (178, 150, 112)
    t = Tex(base, seed)
    if not edge:
        # a repeating lattice of diamonds with a small flower at each crossing
        for y in range(16):
            for x in range(16):
                if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                    t.set(x, y, shade(pattern, 0.62))
        for cx, cy in ((0, 0), (8, 0), (0, 8), (8, 8), (4, 4), (12, 4), (4, 12), (12, 12)):
            t.set(cx, cy, pattern)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                t.set(cx + dx, cy + dy, shade(pattern, 0.8))
        for cx, cy in ((4, 0), (12, 0), (4, 8), (12, 8), (0, 4), (8, 4), (0, 12), (8, 12)):
            t.set(cx, cy, shade(pattern, 0.5))
    else:
        t.hline(0, 15, 3, shade(pattern, 0.8))
        t.hline(0, 15, 12, shade(pattern, 0.8))
        for x in range(0, 16, 4):
            t.set(x, 7, shade(pattern, 0.7))
            t.set(x + 1, 8, shade(pattern, 0.7))
    t.noise(0.0 if stage == 'wrong' else 0.07, 0.6)
    if stage == 'stained':
        t.blob(10, 5, 4, 3, (52, 22, 20), only=lambda c: True)
        t.blob(10, 5, 2, 1.5, (38, 16, 16))
        t.speckle((60, 36, 28), 8)
        _dim(t, 0.82)
    elif stage == 'damp':
        _dim(t, 0.66)
        t.blob(5, 9, 6, 5, (40, 24, 26))
        t.blob(12, 3, 3, 2, (44, 40, 30))
        t.speckle((70, 86, 60), 9)   # mould
        t.speckle((88, 100, 78), 3)
    elif stage == 'veins':
        _dim(t, 0.78)
        t.line([(0, 12), (4, 10), (6, 11), (10, 7), (15, 8)], VEIN)
        t.line([(6, 11), (7, 14), (9, 15)], VEIN)
        t.line([(10, 7), (11, 3), (13, 0)], VEIN)
        t.blob(7, 11, 2, 1.5, FLESH_DARK)
        t.set(7, 11, FLESH_LIGHT)
    return t.image()


def _dim(t, f):
    for y in range(t.h):
        for x in range(t.w):
            t.p[y][x] = shade(t.p[y][x], f)


def wallpaper(stage, seed=2):
    base = CREAM
    stripe = CREAM_STRIPE
    if stage == 'wrong':
        base = (230, 224, 196)
        stripe = (214, 210, 176)
    t = Tex(base, seed)
    for x in range(16):
        if x % 4 in (0, 1):
            t.vline(x, 0, 15, stripe)
    for x in range(1, 16, 4):                     # a small motif down the stripe
        for y in range(2, 16, 8):
            t.set(x, y, shade(stripe, 0.8))
            t.set(x, y + 1, shade(stripe, 0.88))
    t.noise(0.0 if stage == 'wrong' else 0.04, 0.5)
    if stage == 'stained':
        _dim(t, 0.92)
        for x, n in ((3, 10), (4, 7), (9, 13), (13, 6)):
            t.streak(x, 0, n, (190, 164, 114), 0.2)
            t.set(x, n + 1, (150, 120, 80))
        t.blob(9, 14, 4, 2, (170, 140, 96))
    elif stage == 'damp':
        _dim(t, 0.84)
        t.blob(4, 12, 6, 5, (150, 128, 96))                 # damp
        t.blob(4, 13, 3, 3, (112, 96, 72))
        t.blob(12, 3, 4, 3, (92, 112, 86), jag=0.5)          # mould
        t.speckle((60, 76, 54), 6, (9, 0, 15, 6))
        # peeled paper curling off a corner, grey plaster behind it
        for y in range(0, 7):
            for x in range(0, 8 - y):
                t.set(x, y, (146, 138, 126) if (x + y) < 6 else (232, 222, 196))
        t.line([(0, 7), (7, 0)], INK)
        t.line([(0, 8), (8, 0)], (190, 178, 150))
        t.speckle((100, 94, 84), 5, (0, 0, 5, 5))
    elif stage == 'veins':
        for y in range(16):
            for x in range(16):
                t.p[y][x] = mix(t.p[y][x], (222, 170, 160), 0.30)
        t.line([(0, 4), (3, 6), (5, 5), (9, 9), (12, 8), (15, 11)], shade(VEIN, 0.8))
        t.line([(5, 5), (6, 1), (8, 0)], shade(VEIN, 0.8))
        t.line([(9, 9), (8, 13), (10, 15)], shade(VEIN, 0.8))
        t.line([(1, 4), (4, 6)], FLESH_LIGHT)                 # the light side of each vein
        t.line([(6, 6), (9, 10)], FLESH_LIGHT)
        t.blob(4, 5, 1.5, 1, VEIN)
        t.blob(9, 9, 1.5, 1.2, VEIN)
    return t.image()


def wainscot(stage, seed=3):
    base = WOOD
    if stage == 'wrong':
        base = (110, 68, 42)
    t = Tex(base, seed)
    for y in range(16):
        if y % 3 == 1:
            t.hline(0, 15, y, shade(base, 0.9))          # grain
    # raised panels: light along the top and left edge, dark below and right
    t.frame(0, 0, 15, 15, WOOD_DARK)
    t.hline(1, 14, 1, shade(base, 1.25))
    t.vline(1, 1, 14, shade(base, 1.15))
    t.hline(1, 14, 14, shade(base, 0.7))
    t.vline(14, 1, 14, shade(base, 0.75))
    t.frame(3, 3, 12, 12, shade(base, 0.62))
    t.hline(4, 12, 12, shade(base, 1.2))
    t.vline(12, 4, 12, shade(base, 1.15))
    t.noise(0.0 if stage == 'wrong' else 0.07, 0.5)
    if stage == 'wrong':
        t.vline(5, 4, 11, shade(base, 1.35))
        t.vline(6, 4, 11, shade(base, 1.2))
    if stage == 'stained':
        _dim(t, 0.82)
        t.hline(1, 14, 13, (74, 46, 30))
    elif stage == 'damp':
        _dim(t, 0.68)
        t.blob(8, 12, 7, 3, (42, 36, 28))
        t.speckle((104, 124, 100), 8, (1, 2, 14, 12))
        t.speckle((170, 176, 160), 3, (2, 9, 13, 13))
        t.vline(11, 5, 13, INK)                       # a split in the wood
    elif stage == 'veins':
        _dim(t, 0.8)
        t.line([(0, 10), (3, 9), (5, 11), (8, 8), (10, 9), (15, 6)], VEIN)
        t.line([(8, 8), (9, 4), (8, 1)], VEIN)
        t.blob(5, 11, 1.5, 1, FLESH_LIGHT)
    return t.image()


def door_bottom(seed=4):
    t = Tex((84, 48, 30), seed)
    for y in range(16):
        if y % 3 == 1:
            t.hline(0, 15, y, (76, 42, 26))
    t.frame(0, 0, 15, 15, (50, 28, 18))
    t.hline(1, 14, 1, (118, 74, 46))
    t.vline(1, 1, 14, (110, 68, 42))
    t.frame(3, 3, 12, 12, (60, 34, 22))                # the lower panel, sunk
    t.hline(4, 11, 12, (112, 70, 44))
    t.vline(12, 4, 12, (108, 66, 40))
    t.rect(4, 4, 11, 11, (78, 44, 28))
    t.noise(0.06, 0.5)
    # a brass handle and keyhole plate at the top right, a kick plate at the foot
    t.rect(11, 0, 13, 1, BRASS)
    t.set(11, 0, BRASS_HI)
    t.set(13, 1, BRASS_LO)
    t.rect(2, 13, 13, 14, BRASS_LO)
    t.hline(2, 13, 13, BRASS)
    return t.image()


def door_top(number='208', seed=5, stage='clean'):
    t = Tex((84, 48, 30), seed)
    for y in range(16):
        if y % 3 == 1:
            t.hline(0, 15, y, (76, 42, 26))
    t.frame(0, 0, 15, 15, (50, 28, 18))
    t.hline(1, 14, 1, (118, 74, 46))
    t.vline(1, 1, 14, (110, 68, 42))
    t.rect(3, 9, 12, 14, (60, 34, 22))                 # the upper panel, sunk
    t.rect(4, 10, 11, 14, (78, 44, 28))
    t.hline(4, 11, 14, (112, 70, 44))
    t.noise(0.06, 0.5)
    # the brass number plate, with a peephole above it
    t.rect(1, 3, 14, 8, BRASS)
    t.hline(1, 14, 3, BRASS_HI)
    t.vline(1, 3, 8, BRASS_HI)
    t.hline(1, 14, 8, BRASS_LO)
    t.vline(14, 3, 8, BRASS_LO)
    text(t, number, 3, 4, (58, 34, 14))
    t.rect(7, 0, 8, 1, BRASS)
    t.set(7, 0, INK)
    return t.image()


def wall_lamp(stage, seed=6):
    """A warm sconce on the wallpaper; the glow lights the paper round it."""
    wp = wallpaper('clean' if stage != 'dim' else 'stained', seed)
    t = Tex((0, 0, 0), seed)
    for y in range(16):
        for x in range(16):
            t.p[y][x] = wp.getpixel((x, y))
    glow = {'lit': (255, 214, 130), 'dim': (196, 150, 84), 'flicker': (255, 224, 150)}[stage]
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 6) ** 2) ** 0.5
            if d < 7.5:
                amt = (1 - d / 7.5) ** 1.4 * (0.55 if stage != 'dim' else 0.3)
                t.p[y][x] = mix(t.p[y][x], glow, amt)
    t.rect(6, 12, 9, 13, BRASS_LO)                      # a bracket
    t.hline(6, 9, 12, BRASS)
    t.rect(5, 4, 10, 11, glow)                           # the shade
    t.rect(6, 3, 9, 3, glow)
    t.vline(5, 4, 11, shade(glow, 0.8))
    t.vline(10, 4, 11, shade(glow, 0.78))
    t.hline(5, 10, 11, shade(glow, 0.72))
    t.rect(7, 5, 8, 9, mix(glow, (255, 250, 220), 0.7))
    t.set(5, 3, INK)
    t.set(10, 3, INK)
    if stage == 'flicker':
        t.rect(5, 8, 10, 11, shade(glow, 0.45))         # half out
        t.rect(7, 8, 8, 9, shade(glow, 0.6))
    if stage == 'dim':
        t.speckle((70, 50, 30), 8, (5, 4, 10, 11))
    return t.image()


def ceiling_tile(seed=7):
    t = Tex((226, 222, 210), seed)
    t.frame(0, 0, 15, 15, (190, 186, 174))
    for x, y in ((3, 3), (7, 2), (11, 4), (5, 7), (10, 8), (3, 11), (8, 12), (13, 12), (13, 7), (6, 14), (1, 6)):
        t.set(x, y, (178, 174, 162))
    t.noise(0.03, 0.5)
    return t.image()


def ceiling_light(stage, seed=8):
    t = Tex((226, 222, 210), seed)
    t.frame(0, 0, 15, 15, (190, 186, 174))
    glow = {'lit': (255, 236, 176), 'dim': (206, 168, 104), 'flicker': (255, 244, 200), 'off': (92, 84, 72)}[stage]
    t.frame(1, 1, 14, 14, (128, 118, 104))
    t.rect(2, 2, 13, 13, glow)
    t.frame(2, 2, 13, 13, shade(glow, 0.86))
    t.vline(7, 2, 13, shade(glow, 0.9))                 # a cross of ribs
    t.vline(8, 2, 13, shade(glow, 0.9))
    t.hline(2, 13, 7, shade(glow, 0.9))
    t.hline(2, 13, 8, shade(glow, 0.9))
    t.rect(4, 4, 5, 5, mix(glow, (255, 255, 240), 0.6))
    if stage == 'flicker':
        t.rect(8, 8, 13, 13, shade(glow, 0.5))
    if stage == 'dim':
        t.speckle((80, 56, 30), 7, (2, 2, 13, 13))
    return t.image()


# --- the lift --------------------------------------------------------------------------------------------------------

def lift_door(side, seed=9):
    """Brushed steel; two of these side by side with the seam between them."""
    t = Tex((166, 172, 178), seed)
    for y in range(16):
        t.hline(0, 15, y, shade((166, 172, 178), 1 + (0.06 if y % 2 else -0.04)))
    t.noise(0.04, 0.5)
    t.frame(0, 0, 15, 15, (104, 110, 116))
    t.vline(2 if side == 'l' else 13, 2, 13, (190, 196, 202))     # a bright edge
    seam = 15 if side == 'l' else 0
    t.vline(seam, 0, 15, (36, 38, 42))
    t.vline(seam - 1 if side == 'l' else 1, 0, 15, (78, 82, 88))
    return t.image()


def lift_indicator(digit='8', arrow='up', seed=10):
    t = Tex((34, 32, 34), seed)
    t.frame(0, 0, 15, 15, (120, 124, 130))
    t.frame(1, 1, 14, 14, (60, 62, 66))
    t.rect(2, 2, 13, 13, (22, 18, 18))
    lit = (255, 120, 40)
    text(t, digit, 3, 3, lit, scale=2)
    # arrow
    ax = 11
    if arrow == 'up':
        t.set(ax, 4, lit)
        t.hline(ax - 1, ax + 1, 5, lit)
        t.vline(ax, 6, 10, lit)
    else:
        t.set(ax, 11, lit)
        t.hline(ax - 1, ax + 1, 10, lit)
        t.vline(ax, 5, 9, lit)
    t.set(2, 2, (60, 30, 20))
    return t.image()


def lift_buttons(seed=11):
    t = Tex(wallpaper('clean', seed).getpixel((3, 3)), seed)
    for y in range(16):
        for x in range(16):
            t.p[y][x] = wallpaper('clean', seed).getpixel((x, y))
    t.rect(5, 2, 10, 13, (150, 156, 162))
    t.frame(5, 2, 10, 13, (86, 90, 96))
    t.rect(6, 4, 9, 6, (214, 214, 206))
    t.rect(7, 5, 8, 5, (255, 140, 60))                  # the call lit
    t.rect(6, 9, 9, 11, (214, 214, 206))
    t.set(7, 10, (120, 120, 116))
    return t.image()


# --- the lobby -------------------------------------------------------------------------------------------------------

def lobby_floor(variant, seed=12):
    a, b = (210, 204, 190), (158, 152, 142)
    t = Tex(a, seed)
    for y in range(16):
        for x in range(16):
            quad = (x // 8 + y // 8) % 2
            t.set(x, y, a if variant == 0 else b if quad else a)
            if variant == 1:
                t.set(x, y, b if quad else a)
    t.hline(0, 15, 0, (120, 116, 108))
    t.vline(0, 0, 15, (120, 116, 108))
    t.hline(0, 15, 8, (138, 134, 126))
    t.vline(8, 0, 15, (138, 134, 126))
    t.noise(0.03, 0.5)
    t.line([(2, 5), (6, 2)], (232, 228, 216))               # a gloss on the polish
    t.line([(10, 13), (14, 10)], (232, 228, 216))
    return t.image()


def lobby_wall(seed=13):
    t = Tex((216, 202, 176), seed)
    for y in range(16):
        t.hline(0, 15, y, shade((216, 202, 176), 1 + 0.03 * ((y * 7) % 3 - 1)))
    t.line([(0, 4), (5, 6), (9, 4), (15, 7)], (190, 174, 146))
    t.line([(3, 12), (8, 10), (15, 13)], (192, 178, 150))
    t.hline(0, 15, 0, BRASS_LO)
    t.hline(0, 15, 1, BRASS)
    t.noise(0.03, 0.5)
    return t.image()


def desk_front(seed=14):
    t = Tex((88, 52, 32), seed)
    for y in range(16):
        if y % 3 == 0:
            t.hline(0, 15, y, (78, 44, 28))
    for x in (0, 5, 10, 15):
        t.vline(x, 0, 15, (52, 30, 20))
    t.rect(1, 1, 4, 14, (100, 62, 40))
    t.rect(6, 1, 9, 14, (100, 62, 40))
    t.rect(11, 1, 14, 14, (100, 62, 40))
    for x in (1, 6, 11):
        t.vline(x, 1, 14, (126, 82, 52))
    t.hline(0, 15, 0, BRASS)                               # brass trim along the top and foot
    t.hline(0, 15, 15, BRASS_LO)
    t.noise(0.05, 0.5)
    return t.image()


def desk_top(seed=15):
    t = Tex((52, 36, 30), seed)
    t.noise(0.07, 0.6)
    t.hline(0, 15, 0, BRASS)
    t.hline(0, 15, 15, BRASS_LO)
    t.line([(1, 12), (6, 7)], (96, 76, 64))
    t.line([(8, 14), (13, 9)], (88, 70, 58))
    return t.image()


def bell(seed=16):
    t = Tex((0, 0, 0), seed)
    t.rect(0, 0, 15, 15, (52, 36, 30))                      # the desk top it stands on
    t.blob(8, 10, 4.5, 3.8, BRASS, jag=0.1)
    t.rect(3, 12, 12, 13, BRASS_LO)
    t.hline(3, 12, 12, BRASS)
    t.blob(7, 8, 2, 1.5, BRASS_HI, jag=0.05)
    t.rect(7, 5, 8, 6, BRASS_LO)                            # the button
    t.set(7, 5, BRASS_HI)
    return t.image()


def key_board(seed=17):
    t = Tex((70, 42, 28), seed)
    t.frame(0, 0, 15, 15, (44, 26, 18))
    t.noise(0.05, 0.5)
    for row_y in (3, 8, 13):
        for x in (2, 5, 8, 11, 14):
            t.set(x, row_y, BRASS_HI)                       # the hook
            t.rect(x, row_y + 1, x, row_y + 2, BRASS)       # the key
            t.set(x - 0 if x < 15 else x, row_y + 2, (240, 236, 220))
    for row_y in (3, 8, 13):
        for x in (2, 5, 8, 11, 14):
            t.set(x, row_y + 2, (234, 230, 212) if (x + row_y) % 3 else (180, 40, 36))
    return t.image()


def sign_strip(s, tiles, fg, bg, scale=2, seed=18, rim=None):
    """A sign of `tiles` tiles in a row (and 1 tile tall), the text centred."""
    t = Tex(bg, seed, 16 * tiles, 16)
    t.frame(0, 0, 16 * tiles - 1, 15, rim or shade(bg, 0.55))
    t.frame(1, 1, 16 * tiles - 2, 14, shade(bg, 1.2))
    w = text_width(s, scale)
    x = (16 * tiles - w) // 2
    y = (16 - 5 * scale) // 2
    text(t, s, x, y + 1, shade(fg, 0.4), scale)         # a shadow, then the letters
    text(t, s, x, y, fg, scale)
    return t.image()


def split_strip(name, img):
    out = {}
    for i in range(img.width // 16):
        out['%s_%d' % (name, i)] = img.crop((16 * i, 0, 16 * i + 16, 16))
    return out


# --- the outside -----------------------------------------------------------------------------------------------------

def facade_concrete(seed=19):
    t = Tex((170, 170, 164), seed)
    t.hline(0, 15, 0, (122, 122, 118))                      # a panel joint
    t.hline(0, 15, 1, (188, 188, 182))
    t.vline(0, 0, 15, (136, 136, 132))
    for x, y in ((3, 4), (12, 4), (3, 11), (12, 11)):       # tie holes
        t.set(x, y, (96, 98, 98))
    t.noise(0.06, 0.6)
    for x, n in ((6, 9), (9, 5), (14, 12)):
        t.streak(x, 2, n, (146, 146, 140), 0.1)
    return t.image()


def facade_tile(seed=20):
    t = Tex((188, 164, 130), seed)
    for y in range(16):
        for x in range(16):
            if x % 4 == 3 or y % 4 == 3:
                t.set(x, y, (124, 112, 94))
            elif x % 4 == 0 or y % 4 == 0:
                t.set(x, y, (206, 184, 150))
            else:
                t.set(x, y, shade((188, 164, 130), 1 + ((x // 4 * 3 + y // 4 * 5) % 4) * 0.025 - 0.03))
    t.noise(0.04, 0.5)
    return t.image()


def window(stage, seed=21):
    """A room's window: an aluminium frame round dark glass (or glass lit warm behind a curtain)."""
    frame = (176, 180, 184)
    if stage == 'night':
        t = Tex((26, 32, 46), seed)
        t.line([(3, 8), (8, 3)], (62, 74, 98))
        t.line([(4, 12), (11, 5)], (48, 58, 80))
    else:
        t = Tex((255, 212, 124), seed)
        t.rect(2, 2, 6, 13, (236, 196, 150))                 # a curtain
        t.vline(5, 2, 13, (214, 170, 118))
        t.rect(9, 2, 13, 4, (255, 230, 160))
        t.noise(0.04, 0.5)
    t.frame(0, 0, 15, 15, (92, 96, 100))
    t.frame(1, 1, 14, 14, frame)
    t.vline(7, 2, 13, frame)
    t.vline(8, 2, 13, (120, 124, 128))
    t.hline(2, 13, 9, frame) if stage == 'night' else None
    return t.image()


def window_dead(seed=22):
    """The dead hotel by day: dark glass, nothing behind it."""
    t = Tex((30, 36, 44), seed)
    t.line([(2, 7), (7, 2)], (84, 100, 120))
    t.line([(3, 11), (10, 4)], (66, 80, 98))
    t.frame(0, 0, 15, 15, (92, 96, 100))
    t.frame(1, 1, 14, 14, (176, 180, 184))
    t.vline(7, 2, 13, (176, 180, 184))
    t.vline(8, 2, 13, (120, 124, 128))
    return t.image()


def canopy(part, seed=23):
    if part == 'top':
        t = Tex((150, 150, 146), seed)
        t.noise(0.07, 0.6)
        t.frame(0, 0, 15, 15, (118, 118, 114))
    elif part == 'edge':
        t = Tex((214, 212, 204), seed)
        t.rect(0, 5, 15, 9, (122, 30, 38))                     # a deep red band with a brass line
        t.hline(0, 15, 5, BRASS)
        t.hline(0, 15, 9, BRASS_LO)
        t.noise(0.04, 0.5)
    else:
        t = Tex((76, 70, 66), seed)
        for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):      # recessed down-lights
            t.rect(x - 1, y - 1, x + 1, y + 1, (255, 232, 168))
            t.frame(x - 2, y - 2, x + 2, y + 2, (120, 108, 90))
        t.noise(0.04, 0.5)
    return t.image()


def entrance_door(half, seed=24, lit=True):
    """The glass doors: steel frame, push-bar, warm lobby behind."""
    glass = (255, 212, 130) if lit else (24, 30, 40)
    t = Tex(glass, seed)
    if half == 'top':
        t.rect(2, 3, 13, 15, glass)
        t.line([(3, 12), (9, 6)], mix(glass, (255, 255, 255), 0.4))
        t.hline(0, 15, 0, (120, 124, 130))
        t.hline(0, 15, 1, (176, 180, 186))
    else:
        t.line([(3, 9), (7, 5)], mix(glass, (255, 255, 255), 0.4))
        t.rect(2, 2, 13, 3, (176, 180, 186))                    # the push bar
        t.hline(2, 13, 3, (110, 114, 120))
        t.rect(0, 14, 15, 15, (150, 154, 160))
    t.vline(0, 0, 15, (120, 124, 130))
    t.vline(1, 0, 15, (176, 180, 186))
    t.vline(15, 0, 15, (120, 124, 130))
    t.vline(14, 0, 15, (176, 180, 186))
    t.vline(7, 0, 15, (120, 124, 130))
    t.vline(8, 0, 15, (120, 124, 130))
    return t.image()


def pavement(seed=25):
    t = Tex((126, 126, 122), seed)
    t.hline(0, 15, 0, (92, 92, 90))
    t.vline(0, 0, 15, (92, 92, 90))
    t.hline(0, 15, 8, (106, 106, 102))
    t.noise(0.07, 0.6)
    return t.image()


def grass(seed=26):
    t = Tex((84, 132, 56), seed)
    t.noise(0.14, 0.9)
    t.speckle((60, 104, 44), 24)
    t.speckle((110, 156, 70), 14)
    return t.image()


def asphalt(seed=27):
    t = Tex((52, 52, 56), seed)
    t.noise(0.12, 0.8)
    t.speckle((78, 78, 82), 10)
    return t.image()


# --- for the hunt ------------------------------------------------------------------------------------------------------

def wallpaper_eye(openness, seed=28):
    """An eye that opens in the wallpaper: 0 a seam in the paper, 1 a slit, 2 half open, 3 wide."""
    wp = wallpaper('veins' if openness >= 1 else 'clean', seed)
    t = Tex((0, 0, 0), seed)
    for y in range(16):
        for x in range(16):
            t.p[y][x] = wp.getpixel((x, y))
    cx, cy = 7.5, 7.5
    w = (4.5, 5.5, 6.5, 7)[openness]
    h = (0.4, 1.2, 3.0, 4.3)[openness]
    # the pink swollen lids round it
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / (w + 1.6)) ** 2 + ((y - cy) / (h + 2.0)) ** 2
            if d < 1:
                t.p[y][x] = mix(t.p[y][x], FLESH, 0.8) if d > 0.55 else mix(t.p[y][x], FLESH_DARK, 0.7)
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / w) ** 2 + ((y - cy) / max(h, 0.5)) ** 2
            if d < 1:
                inner = (x - cx) ** 2 / 4.0 + (y - cy) ** 2 / 4.0 < 1 and h > 2
                if openness == 0:
                    t.set(x, y, INK)
                elif inner:
                    t.set(x, y, (60, 8, 14))                   # the pupil
                elif ((x - cx) / 3.2) ** 2 + ((y - cy) / 3.2) ** 2 < 1 and h > 2:
                    t.set(x, y, (150, 24, 36))                 # the iris
                else:
                    t.set(x, y, (240, 232, 214) if h > 2 else INK)
    for x in range(16):                                         # lashes of dark along the lids' edges
        for y in range(16):
            d = ((x - cx) / (w + 0.9)) ** 2 + ((y - cy) / (h + 0.9)) ** 2
            if 1 <= d < 1.35 and h > 0.5:
                t.set(x, y, (92, 30, 36))
    if openness >= 2:
        t.set(6, 6, (255, 255, 255))
        t.set(7, 6, (255, 255, 255))
    return t.image()


def wall_bulge(seed=29):
    """A wall that breathes: the paper stretched over something round, the stripes bent round it."""
    t = Tex((0, 0, 0), seed)
    cx, cy = 7.5, 8
    for y in range(16):
        for x in range(16):
            dx, dy = (x - cx) / 6.8, (y - cy) / 6.8
            d = (dx * dx + dy * dy) ** 0.5
            sx = x
            if d < 1:
                sx = int(cx + (x - cx) * (1 + 0.5 * (1 - d)))      # the stripes bulge outward
            c = CREAM_STRIPE if sx % 4 in (0, 1) else CREAM
            if d < 1:
                light = 1.18 - 0.55 * ((dx + dy + 1) / 2) + (0.12 if d > 0.8 else 0)
                c = mix(shade(c, light), FLESH, 0.5 * (1 - d))
            t.set(x, y, c)
    t.line([(5, 5), (8, 4), (10, 6)], shade(FLESH_DARK, 0.9))     # the paper splitting
    t.noise(0.03, 0.5)
    return t.image()


def wall_bleed(stage, seed=30):
    """A wall that bleeds when cut: a slash in the paper and blood running from it (stage 0 a seep, 1 running)."""
    wp = wallpaper('veins', seed)
    t = Tex((0, 0, 0), seed)
    for y in range(16):
        for x in range(16):
            t.p[y][x] = wp.getpixel((x, y))
    t.line([(2, 3), (6, 5), (9, 6), (13, 9)], INK)
    t.line([(2, 4), (6, 6), (9, 7), (13, 10)], BLOOD)
    for x in (4, 7, 9, 12):
        n = (5, 9, 13)[stage] + (x % 3)
        t.streak(x, 5 + (x % 3), min(15, n), BLOOD_BRIGHT if stage else BLOOD, 0.1)
        t.set(x, min(15, n) + 1 if n < 15 else 15, BLOOD)
    if stage:
        t.rect(5, 6, 6, 10, BLOOD)
        t.rect(10, 8, 11, 12, BLOOD_BRIGHT)
        t.speckle(BLOOD, 6, (0, 10, 15, 15))
    t.speckle(BLOOD, 4, (2, 3, 13, 9))
    return t.image()


# --- everything -------------------------------------------------------------------------------------------------------

def build():
    out = {}
    for s in STAGES:
        out['carpet_%s' % s] = carpet(s)
        out['carpet_edge_%s' % s] = carpet(s, edge=True, seed=11)
        out['wallpaper_%s' % s] = wallpaper(s)
        out['wainscot_%s' % s] = wainscot(s)
    out['door_bottom'] = door_bottom()
    for n in ('101', '102', '208', '301', '404', '503', '602', '701', '801', '802', '803'):
        out['door_top_%s' % n] = door_top(n)
    for s in ('lit', 'dim', 'flicker'):
        out['wall_lamp_%s' % s] = wall_lamp(s)
    for s in ('lit', 'dim', 'flicker', 'off'):
        out['ceiling_light_%s' % s] = ceiling_light(s)
    out['ceiling_tile'] = ceiling_tile()
    out['lift_door_l'] = lift_door('l')
    out['lift_door_r'] = lift_door('r')
    out['lift_indicator_8'] = lift_indicator('8', 'up')
    out['lift_indicator_1'] = lift_indicator('1', 'down', 12)
    out['lift_buttons'] = lift_buttons()
    out['lobby_floor'] = lobby_floor(1)
    out['lobby_wall'] = lobby_wall()
    out['desk_front'] = desk_front()
    out['desk_top'] = desk_top()
    out['bell'] = bell()
    out['key_board'] = key_board()
    out.update(split_strip('sign_reception', sign_strip('RECEPTION', 3, (240, 232, 208), (30, 78, 92), 1, rim=(150, 120, 60))))
    out.update(split_strip('sign_hotel', sign_strip('HOTEL ASAGAO', 8, (246, 240, 224), (116, 24, 32), 2, rim=BRASS)))
    out['facade_concrete'] = facade_concrete()
    out['facade_tile'] = facade_tile()
    out['window_night'] = window('night')
    out['window_lit'] = window('lit')
    out['window_dead'] = window_dead()
    out['canopy_top'] = canopy('top')
    out['canopy_edge'] = canopy('edge')
    out['canopy_under'] = canopy('under')
    out['entrance_door_top'] = entrance_door('top')
    out['entrance_door_bottom'] = entrance_door('bottom')
    out['pavement'] = pavement()
    out['grass'] = grass()
    out['asphalt'] = asphalt()
    for i in range(4):
        out['wallpaper_eye_%d' % i] = wallpaper_eye(i)
    out['wall_bulge'] = wall_bulge()
    out['wall_bleed_0'] = wall_bleed(0)
    out['wall_bleed_1'] = wall_bleed(1)
    return out


def contact_sheet(tiles, scale=6, cols=12):
    names = sorted(tiles)
    rows = (len(names) + cols - 1) // cols
    cell = 16 * scale + 8
    sheet = Image.new('RGB', (cols * cell + 8, rows * (cell + 12) + 8), (34, 34, 38))
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        x, y = 8 + (i % cols) * cell, 8 + (i // cols) * (cell + 12)
        sheet.paste(tiles[n].resize((16 * scale, 16 * scale), Image.NEAREST), (x, y))
        d.text((x, y + 16 * scale + 1), n[:17], fill=(190, 190, 196))
    return sheet


def main():
    tiles = build()
    os.makedirs(os.path.join(OUT, 'textures'), exist_ok=True)
    for n, img in tiles.items():
        img.save(os.path.join(OUT, 'textures', n + '.png'))
    contact_sheet(tiles).save(os.path.join(OUT, 'textures_sheet.png'))
    print(len(tiles), 'textures in', os.path.abspath(OUT))


if __name__ == '__main__':
    main()
