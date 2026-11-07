"""The Eternity Devil and its looping 8th-floor hallway as pixel art at game resolution (1 px = 1/16 block), for kortev
to check before anything is built (the HANDOFF rule: show the look first). Style as Beam's approved drawings: flat
anime colours, dark ink lines, chunky pixels.

Run: python tools/eternity_drawings.py OUT_DIR   ->  eternity_devil.png (front, side, the heart, a player for scale)
                                                    eternity_hallway.png (your view down the hallway, the loop plan,
                                                    the devil grown out of the walls in that view)

The devil (anime season 1, the hotel): a huge mound of folded flesh that plugs the hallway, covered in eyes of every
size and mouths of grinding human teeth, the biggest mouth in the middle (Denji jumps into it), flesh creeping along
the walls, ceiling and floor towards you, tendrils on the carpet. Its heart, also its first small form, is a
figure-eight knot of flesh with an eye and a mouth on each loop and two more mouths where the loops cross."""
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from beam_profile import spline  # noqa: E402

FLESH, FLESH_SH, FLESH_DK, FLESH_HI = (208, 122, 112), (160, 78, 82), (96, 36, 48), (226, 152, 138)
INK = (30, 14, 20)
LIP, MOUTH, TONGUE = (186, 70, 84), (58, 10, 20), (196, 86, 98)
TOOTH, TOOTH_SH = (244, 236, 214), (196, 186, 166)
SCLERA, IRIS, PUPIL = (250, 246, 234), (150, 30, 40), (14, 8, 10)
PAPER, LABEL = (226, 228, 234), (40, 40, 52)

# The hallway (blocks): 5 wide, 4 high, eye height 1.62.
HALF_W, HIGH, EYE = 2.5, 4.0, 1.62
WALL, WALL_SH, WAINSCOT = (226, 214, 184), (200, 188, 160), (150, 112, 78)
RUNNER, RUNNER_SH, RUNNER_EDGE = (150, 34, 40), (128, 28, 34), (206, 168, 92)
FLOOR, FLOOR_SH = (92, 64, 44), (78, 54, 36)
CEIL, CEIL_SH, LIGHT = (236, 234, 226), (214, 212, 204), (255, 252, 230)
DOOR, DOOR_SH, PLATE = (96, 60, 38), (74, 46, 30), (214, 196, 140)
STEEL, STEEL_SH, EXIT_GREEN = (150, 156, 160), (118, 124, 128), (40, 170, 90)


def font(size):
    for name in ('arialbd.ttf', 'arial.ttf', 'DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def outline(layer, colour=INK):
    """Ink every opaque pixel that touches a clear one (4-neighbour), the drawings' line."""
    layer.paste(Image.new('RGBA', layer.size, colour + (255,)), (0, 0), edge_of(layer.getchannel('A')))


def blob_pts(cx, cy, rx, ry, seed, wobble=0.14, n=14):
    rnd = random.Random(seed)
    k = [1 + rnd.uniform(-wobble, wobble) for _ in range(n)]
    pts = [(cx + rx * k[i] * math.cos(2 * math.pi * i / n), cy + ry * k[i] * math.sin(2 * math.pi * i / n))
           for i in range(n)]
    return spline(pts + pts[:3], 4)[4:-1]


def lobe(img, cx, cy, rx, ry, base=FLESH, shade=FLESH_SH, hi=FLESH_HI, seed=None):
    """A lump of flesh, not quite round: shaded underneath, a small sheen, folds, inked round; later lumps sit over
    earlier ones."""
    seed = hash((round(cx * 7), round(cy * 7), round(rx * 7))) if seed is None else seed
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.polygon(blob_pts(cx, cy, rx, ry, seed), fill=shade + (255,))
    d.polygon(blob_pts(cx - rx * 0.04, cy - ry * 0.2, rx * 0.88, ry * 0.74, seed), fill=base + (255,))
    if rx > 4:
        d.ellipse([cx - rx * 0.5, cy - ry * 0.7, cx - rx * 0.25, cy - ry * 0.55], fill=hi + (255,))
        d.arc([cx - rx * 0.8, cy - ry * 0.1, cx + rx * 0.4, cy + ry * 0.8], 200, 330, fill=shade + (255,), width=1)
        d.arc([cx - rx * 0.2, cy - ry * 0.6, cx + rx * 0.9, cy + ry * 0.3], 210, 300, fill=shade + (255,), width=1)
    outline(layer)
    img.alpha_composite(layer)


def almond(cx, cy, w, h):
    return spline([(cx - w / 2, cy), (cx - w / 4, cy - h / 2), (cx + w / 4, cy - h / 2), (cx + w / 2, cy),
                   (cx + w / 4, cy + h / 2), (cx - w / 4, cy + h / 2), (cx - w / 2, cy)], 5)


def eye(img, cx, cy, w, h, look=(0, 0), lid=0.0):
    """An almond eye: a fold of flesh round it, white, a dark red iris and a black pupil looking somewhere;
    lid 0..1 drops a heavy fleshy lid over the top of it."""
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.polygon(almond(cx, cy - 0.5, w + 3, h + 3), fill=FLESH_SH + (255,))
    outline(layer)
    white = Image.new('L', img.size, 0)
    ImageDraw.Draw(white).polygon(almond(cx, cy, w, h), fill=255)
    layer.paste(Image.new('RGBA', img.size, SCLERA + (255,)), (0, 0), white)
    ball = Image.new('RGBA', img.size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(ball)
    ix, iy = round(cx + look[0] * w * 0.22), round(cy + look[1] * h * 0.14)
    if h >= 6:
        ir = round(h * 0.4)
        bd.ellipse([ix - ir, iy - ir, ix + ir, iy + ir], fill=IRIS + (255,))
        bd.rectangle([ix - 1, iy - 1, ix + 1, iy + 1], fill=PUPIL + (255,))
    elif h >= 4:
        bd.rectangle([ix - 1, iy - 1, ix + 1, iy + 1], fill=IRIS + (255,))
        bd.point((ix, iy), fill=PUPIL + (255,))
    else:
        bd.rectangle([ix, iy - 1, ix, iy], fill=PUPIL + (255,))
    if lid > 0:
        bd.rectangle([cx - w, cy - h, cx + w, cy - h / 2 + lid * h - 1], fill=FLESH + (255,))
        bd.line([(cx - w, cy - h / 2 + lid * h), (cx + w, cy - h / 2 + lid * h)], fill=INK + (255,))
    layer.paste(ball, (0, 0), ImageChops.multiply(ball.getchannel('A'), white))
    layer.paste(Image.new('RGBA', img.size, INK + (255,)), (0, 0), edge_of(white))
    img.alpha_composite(layer)


def edge_of(mask):
    """The rim of a mask: its pixels that touch a pixel outside it."""
    a = mask.point(lambda v: 255 if v > 0 else 0)
    shrunk = a.copy()
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        shrunk = ImageChops.multiply(shrunk, ImageChops.offset(a, dx, dy))
    return ImageChops.subtract(a, shrunk)


def grin(img, cx, cy, w, h, tooth=3, tongue=True, vertical=False, seed=1):
    """A mouth of human teeth: a lens-shaped gape (no smile), thick lips, gums, upper and lower rows of uneven flat
    teeth. h small = shut, grinding teeth edge to edge. vertical turns it on its side."""
    if vertical:
        size = int(max(w, h) + 12)
        tmp = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        grin(tmp, size / 2, size / 2, w, h, tooth, tongue, False, seed)
        tmp = tmp.rotate(90)
        img.alpha_composite(tmp, (round(cx - size / 2), round(cy - size / 2)))
        return
    rnd = random.Random(seed)

    def upper(t):
        return cy - 0.3 * h * max(0.0, 1 - t * t) ** 0.7

    def lower(t):
        return cy + 0.7 * h * max(0.0, 1 - t * t) ** 0.8

    n = 24
    top = [(cx + w / 2 * (2 * i / n - 1), upper(2 * i / n - 1)) for i in range(n + 1)]
    bot = [(cx + w / 2 * (2 * i / n - 1), lower(2 * i / n - 1)) for i in range(n, -1, -1)]
    hole = top + bot
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    lw = 2.2 if h > 6 else 1.4
    lip_pts = [(x, y - lw) for x, y in top] + [(x, y + lw + 0.4) for x, y in bot]
    lip_pts = [(cx + (x - cx) * (1 + 4 / w), y) for x, y in lip_pts]
    d.polygon(lip_pts, fill=LIP + (255,))
    outline(layer)
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).polygon(hole, fill=255)
    inner = Image.new('RGBA', img.size, (0, 0, 0, 0))
    di = ImageDraw.Draw(inner)
    di.polygon(hole, fill=MOUTH + (255,))
    if tongue and h > 8:
        di.ellipse([cx - w * 0.2, cy + h * 0.25, cx + w * 0.24, cy + h * 0.95], fill=TONGUE + (255,))
    shut = h <= 4
    x = cx - w / 2 + 1
    k = 0
    while x < cx + w / 2 - 1:
        tw = tooth if shut else max(2, tooth + rnd.choice((-1, 0, 0, 1)))
        t = max(-1.0, min(1.0, (x + tw / 2 - cx) / (w / 2)))
        uy, ly = upper(t), lower(t)
        gap = ly - uy
        up_len = 9 if shut else max(2, min(gap * 0.45, 3 + rnd.random() * 3))
        dn_len = 9 if shut else max(2, min(gap * 0.38, 2 + rnd.random() * 3))
        col = TOOTH if k % 2 == 0 else (236, 224, 198)
        if not shut:
            di.rectangle([x, uy - 1, x + tw - 1, uy], fill=LIP + (255,))          # gum
        di.rectangle([x, uy + (0 if shut else 1), x + tw - 1, uy + up_len], fill=col + (255,))
        di.rectangle([x, ly - dn_len, x + tw - 1, ly + 1], fill=col + (255,))
        di.line([(x + tw - 1, uy), (x + tw - 1, uy + up_len)], fill=TOOTH_SH + (255,))
        di.line([(x + tw - 1, ly - dn_len), (x + tw - 1, ly)], fill=TOOTH_SH + (255,))
        if shut:
            di.line([(x, cy + 0.2 * h), (x + tw - 1, cy + 0.2 * h)], fill=TOOTH_SH + (255,))
        x += tw
        k += 1
    layer.paste(inner, (0, 0), ImageChops.multiply(inner.getchannel('A'), mask))
    img.alpha_composite(layer)


def player(img, x, floor_y, colour=(52, 56, 72)):
    """A Minecraft player's outline for scale: 32 px (2 blocks) tall, 8 wide, arms either side."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, floor_y - 32, x + 7, floor_y - 25], fill=colour)        # head
    d.rectangle([x, floor_y - 24, x + 7, floor_y - 13], fill=colour)        # body
    d.rectangle([x - 4, floor_y - 24, x - 1, floor_y - 13], fill=colour)    # arms
    d.rectangle([x + 8, floor_y - 24, x + 11, floor_y - 13], fill=colour)
    d.rectangle([x, floor_y - 12, x + 7, floor_y - 1], fill=colour)         # legs
    d.line([(x + 3.5, floor_y - 12), (x + 3.5, floor_y - 1)], fill=PAPER)


# ------------------------------------------------------------------ the devil, front: plugs the 5 x 4 hallway (80 x 64)

FW, FH = 80, 64
FRONT_LOBES = [   # back to front: (cx, cy, rx, ry) from the top-left, y down
    (6, 6, 12, 10), (26, 3, 14, 9), (50, 3, 15, 9), (74, 6, 12, 10),
    (2, 26, 10, 14), (78, 26, 10, 14), (16, 16, 13, 11), (64, 16, 13, 11), (40, 14, 15, 11),
    (4, 52, 11, 13), (76, 52, 11, 13), (14, 36, 12, 13), (66, 36, 12, 13),
    (40, 38, 25, 21),                                       # the big middle lump with the main mouth
    (18, 58, 12, 8), (62, 58, 12, 8), (40, 62, 16, 6),
]
FRONT_EYES = [   # (cx, cy, w, h, look, lid)
    (40, 20, 13, 7, (0, 0.4), 0.0), (26, 25, 7, 4, (0.7, 0.2), 0.3), (55, 24, 8, 5, (-0.6, 0.3), 0.0),
    (15, 13, 10, 6, (0.4, 0.5), 0.35), (64, 12, 11, 6, (-0.4, 0.5), 0.0), (39, 8, 6, 4, (0.3, 1), 0.4),
    (31, 12, 4, 3, (1, 0), 0.0), (49, 11, 4, 3, (-1, 0.5), 0.0), (72, 21, 5, 3, (-1, 0), 0.0),
    (5, 30, 6, 4, (1, 0), 0.5), (75, 32, 7, 4, (-1, 0), 0.0), (25, 3, 6, 3, (0, 0.6), 0.0), (52, 3, 7, 4, (0, 0.6), 0.3),
    (9, 49, 5, 3, (0.8, -0.5), 0.0), (70, 47, 6, 4, (-0.8, -0.3), 0.4), (29, 57, 4, 3, (0, -1), 0.0),
    (75, 6, 5, 3, (-1, 1), 0.0), (4, 8, 6, 4, (1, 1), 0.0), (60, 56, 4, 3, (0, -1), 0.0), (20, 31, 3, 2, (0, 0), 0.0),
    (60, 31, 3, 2, (0, 0), 0.0), (46, 56, 3, 2, (0, -1), 0.0),
]


def devil_front():
    img = Image.new('RGBA', (FW, FH), FLESH_DK + (255,))
    for cx, cy, rx, ry in FRONT_LOBES:
        lobe(img, cx, cy, rx, ry)
    grin(img, 40, 37, 38, 16, tooth=3, tongue=False, seed=4)                  # the main mouth, gaping: big enough to jump in
    grin(img, 13, 40, 13, 3, tooth=2, tongue=False, seed=5)      # side mouths, shut, grinding
    grin(img, 72, 15, 12, 3, tooth=2, tongue=False, vertical=True, seed=6)   # one on its side
    grin(img, 67, 40, 11, 3, tooth=2, tongue=False, seed=7)
    grin(img, 40, 59, 18, 3, tooth=2, tongue=False, seed=8)
    for cx, cy, w, h, look, lid in FRONT_EYES:
        eye(img, cx, cy, w, h, look, lid)
    return img


# ------------------------------------------------------------------ the devil, side: hallway walls cut away (176 x 64)

SW = 176


def devil_side():
    img = Image.new('RGBA', (SW, FH), (0, 0, 0, 0))
    rnd = random.Random(8)
    # flesh grown over the ceiling and floor towards you: a sheet thinning out, lumps near the mass, veins past it
    sheet = Image.new('RGBA', (SW, FH), (0, 0, 0, 0))
    ds = ImageDraw.Draw(sheet)
    for top in (True, False):
        edge = []
        for x in range(36, 116, 3):
            t = (x - 36) / 80
            th = 0.6 + t * t * 9 + rnd.uniform(-0.8, 0.8) * t
            edge.append((x, th if top else FH - 1 - th))
        base = 0 if top else FH - 1
        ds.polygon([(36, base)] + edge + [(116, base)], fill=FLESH + (255,))
        for x, y in edge[::2]:
            ds.line([(x, base), (x - 3, (y + base) / 2)], fill=FLESH_SH + (255,))
    outline(sheet)
    img.alpha_composite(sheet)
    for x, y, r in ((96, 5, 4), (104, 6, 5), (88, 3, 3), (98, 58, 4), (106, 57, 5), (90, 60, 3)):
        lobe(img, x, y, r + 1, r)
    d = ImageDraw.Draw(img)
    for i in range(8):
        y = 0 if i % 2 == 0 else FH - 1
        x = 10 + rnd.random() * 26
        d.line([(x, y), (x + 14 + rnd.random() * 10, y)], fill=FLESH_DK + (255,), width=1)
    # the mass
    for cx, cy, rx, ry in [(164, 10, 20, 14), (166, 34, 18, 18), (164, 56, 20, 12), (140, 6, 16, 10),
                           (144, 30, 16, 16), (142, 56, 16, 12), (126, 12, 12, 11), (128, 50, 12, 13),
                           (118, 30, 13, 16), (114, 9, 7, 6), (116, 55, 8, 8)]:
        lobe(img, cx, cy, rx, ry)
    # the main mouth gaping, side-on: thick lips pushed out, teeth in both jaws
    d = ImageDraw.Draw(img)
    d.polygon([(100, 33), (114, 31), (115, 47), (101, 46)], fill=MOUTH + (255,))
    for x in range(101, 112, 2):
        d.rectangle([x, 33, x, 36 + (x % 3)], fill=TOOTH + (255,))
        d.rectangle([x, 42 - (x % 2), x, 45], fill=TOOTH + (255,))
    lobe(img, 104, 30, 8, 4, base=LIP, shade=(146, 50, 62), hi=(206, 104, 114))
    lobe(img, 105, 49, 7, 4, base=LIP, shade=(146, 50, 62), hi=(206, 104, 114))
    # tendrils along the floor, reaching for you, each ending in a little mouth
    tl = Image.new('RGBA', (SW, FH), (0, 0, 0, 0))
    dt = ImageDraw.Draw(tl)
    for y_root, length, ph in ((60, 56, 0.0), (62, 42, 1.3), (59, 32, 2.4)):
        pts = []
        for i in range(length):
            x = 116 - i
            y = y_root - 2 * math.sin(i / 7 + ph) * (i / length) - (i / length) * 2
            pts.append((x, y))
        for i, (x, y) in enumerate(pts):
            r = 2.6 - 1.4 * i / len(pts)
            dt.ellipse([x - r, y - r, x + r, y + r], fill=FLESH + (255,))
        tx, ty = pts[-1]
        dt.ellipse([tx - 2.5, ty - 2.5, tx + 2.5, ty + 2.5], fill=FLESH_SH + (255,))
        dt.line([(tx - 2.5, ty), (tx, ty)], fill=MOUTH + (255,))
    outline(tl)
    img.alpha_composite(tl)
    for e in [(121, 18, 6, 4, (-1, 0)), (130, 6, 7, 4, (-1, 0.5)), (116, 25, 4, 3, (-1, 0)), (124, 57, 5, 3, (-1, -1)),
              (142, 22, 6, 4, (-1, 0)), (149, 46, 5, 3, (-1, 0)), (160, 4, 5, 3, (-1, 1)), (112, 10, 3, 2, (-1, 0)),
              (135, 38, 4, 3, (-1, 0)), (97, 4, 3, 2, (-1, 1)), (103, 58, 3, 2, (-1, -1))]:
        eye(img, *e)
    grin(img, 136, 47, 10, 3, tooth=2, tongue=False, seed=9)
    return img


# ------------------------------------------------------------------ the heart / first small form (48 x 26)

def heart(front=True):
    w, h = 48, 26
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    if front:
        knot = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(knot)
        for cx, seed in ((12, 21), (36, 22)):                   # two thick loops of flesh, not quite round
            d.polygon(blob_pts(cx, 12.5, 11.5, 11.5, seed, 0.08), fill=FLESH + (255,))
            d.arc([cx - 10, 2, cx + 10, 23], 20, 160, fill=FLESH_SH + (255,), width=2)
            d.polygon(blob_pts(cx + (1 if cx < 24 else -1), 13, 4, 3.5, seed + 5, 0.2), fill=(0, 0, 0, 0))
        outline(knot)
        img.alpha_composite(knot)
        band = Image.new('RGBA', (w, h), (0, 0, 0, 0))        # where the loops cross, one band twisted over the other
        db = ImageDraw.Draw(band)
        db.polygon([(18, 2), (25, 2), (31, 23), (24, 23)], fill=FLESH + (255,))
        db.line([(22, 3), (27, 22)], fill=FLESH_SH + (255,))
        outline(band)
        img.alpha_composite(band)
        for cx, look in ((10, (0.6, 0.3)), (38, (-0.6, 0.3))):
            eye(img, cx, 5, 9, 5, look)                        # an eye on top of each loop
            grin(img, cx, 20, 11, 3, tooth=2, tongue=False, seed=cx)   # a mouth under each loop
        grin(img, 22, 8, 7, 3, tooth=2, tongue=False, vertical=True, seed=30)   # two more on the crossing
        grin(img, 27, 17, 7, 3, tooth=2, tongue=False, vertical=True, seed=31)
    else:
        body = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(body)
        d.rounded_rectangle([18, 1, 30, 24], 5, fill=FLESH + (255,))
        d.line([(28, 3), (28, 22)], fill=FLESH_SH + (255,), width=2)
        outline(body)
        img.alpha_composite(body)
        eye(img, 21, 5, 5, 5, (-1, 0.3))
        grin(img, 21, 20, 5, 3, tooth=2, tongue=False)
    return img


# ------------------------------------------------------------------ the hallway in your view (one-point perspective)

VW, VH, F = 256, 144, 103.0      # canvas and focal length in px (about Minecraft's default 70 degree view)
CX, CY = VW / 2, VH / 2


def proj(x, y, z):
    return CX + F * x / z, CY - F * (y - EYE) / z


def quad(d, pts3, fill, edge=None):
    pts = [proj(*p) for p in pts3]
    d.polygon(pts, fill=fill, outline=edge)


def hallway(depth=22.0, end_wall=True):
    img = Image.new('RGBA', (VW, VH), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    z0 = 0.35
    # walls, ceiling, floor
    for s in (-1, 1):
        quad(d, [(s * HALF_W, 0, z0), (s * HALF_W, HIGH, z0), (s * HALF_W, HIGH, depth), (s * HALF_W, 0, depth)], WALL)
        quad(d, [(s * HALF_W, 0, z0), (s * HALF_W, 1, z0), (s * HALF_W, 1, depth), (s * HALF_W, 0, depth)], WAINSCOT)
        quad(d, [(s * HALF_W, 3.75, z0), (s * HALF_W, HIGH, z0), (s * HALF_W, HIGH, depth), (s * HALF_W, 3.75, depth)],
             WALL_SH)
    quad(d, [(-HALF_W, HIGH, z0), (HALF_W, HIGH, z0), (HALF_W, HIGH, depth), (-HALF_W, HIGH, depth)], CEIL)
    quad(d, [(-HALF_W, 0, z0), (HALF_W, 0, z0), (HALF_W, 0, depth), (-HALF_W, 0, depth)], FLOOR)
    quad(d, [(-1.5, 0, z0), (1.5, 0, z0), (1.5, 0, depth), (-1.5, 0, depth)], RUNNER)
    for x in (-1.5, -1.4, 1.4, 1.5):
        d.line([proj(x, 0, z0), proj(x, 0, depth)], fill=RUNNER_EDGE)
    # block seams: across the floor and ceiling each block, along the floor, up the walls
    for zi in range(1, int(depth)):
        z = float(zi)
        d.line([proj(-HALF_W, 0, z), proj(-1.5, 0, z)], fill=FLOOR_SH)
        d.line([proj(1.5, 0, z), proj(HALF_W, 0, z)], fill=FLOOR_SH)
        d.line([proj(-1.4, 0, z), proj(1.4, 0, z)], fill=RUNNER_SH)
        d.line([proj(-HALF_W, HIGH, z), proj(HALF_W, HIGH, z)], fill=CEIL_SH)
        for s in (-1, 1):
            d.line([proj(s * HALF_W, 1, z), proj(s * HALF_W, 3.75, z)], fill=WALL_SH)
    for s in (-1, 1):
        d.line([proj(s * HALF_W, 1, z0), proj(s * HALF_W, 1, depth)], fill=INK)        # top of the skirting
        d.line([proj(s * HALF_W, 0, z0), proj(s * HALF_W, 0, depth)], fill=INK)        # wall meets floor
        d.line([proj(s * HALF_W, HIGH, z0), proj(s * HALF_W, HIGH, depth)], fill=INK)  # wall meets ceiling
    # ceiling lights every 4 blocks, room doors every 4 blocks, the two sides staggered
    for zl in range(2, int(depth), 4):
        quad(d, [(-0.5, HIGH, zl), (0.5, HIGH, zl), (0.5, HIGH, zl + 1), (-0.5, HIGH, zl + 1)], LIGHT, INK)
    for s, start in ((-1, 2), (1, 4)):
        for zd in range(start, int(depth) - 1, 4):
            x = s * HALF_W
            quad(d, [(x, 0, zd - 0.12), (x, 2.12, zd - 0.12), (x, 2.12, zd + 1.12), (x, 0, zd + 1.12)], DOOR_SH, INK)
            quad(d, [(x, 0, zd), (x, 2, zd), (x, 2, zd + 1), (x, 0, zd + 1)], DOOR, INK)
            hz = zd + (0.82 if s < 0 else 0.18)
            d.point(proj(x, 1.0, hz), fill=PLATE)                                        # handle
            pz = zd + 1.3 if s < 0 else zd - 0.5
            quad(d, [(x, 1.5, pz), (x, 1.8, pz), (x, 1.8, pz + 0.25), (x, 1.5, pz + 0.25)], PLATE)  # number plate
    if end_wall:
        quad(d, [(-HALF_W, 0, depth), (-HALF_W, HIGH, depth), (HALF_W, HIGH, depth), (HALF_W, 0, depth)], WALL, INK)
        quad(d, [(-HALF_W, 0, depth), (-HALF_W, 1, depth), (HALF_W, 1, depth), (HALF_W, 0, depth)], WAINSCOT)
        quad(d, [(-0.5, 0, depth), (-0.5, 2, depth), (0.5, 2, depth), (0.5, 0, depth)], STEEL, INK)   # stair door
        quad(d, [(-0.5, 2.3, depth), (-0.5, 2.7, depth), (0.5, 2.7, depth), (0.5, 2.3, depth)], EXIT_GREEN)
    return img


def surface(kind, u, z):
    """A point on a hallway surface: u runs across it (height on a wall, x on the floor or ceiling)."""
    return {'L': (-HALF_W, u, z), 'R': (HALF_W, u, z), 'C': (u, HIGH, z), 'F': (u, 0, z)}[kind]


def flesh_creep(img, z_face, rnd):
    """Flesh grown flat over the walls, ceiling and floor in front of the devil, its edge ragged, lumps and eyes in
    it near the mass, veins running on past it."""
    spans = {'L': (0, HIGH), 'R': (0, HIGH), 'C': (-HALF_W, HALF_W), 'F': (-HALF_W, HALF_W)}
    sheet = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ds = ImageDraw.Draw(sheet)
    for kind, (u0, u1) in spans.items():
        reach = {'F': 1.6, 'C': 2.8}.get(kind, 3.2)
        n = 40
        ph = rnd.random() * 6
        edge = []
        for i in range(n + 1):
            u = u0 + (u1 - u0) * i / n
            z = z_face - reach * (0.9 + 0.1 * math.sin(i * 0.3 + ph)) - rnd.random() * 0.05
            edge.append(surface(kind, u, z))
        poly = [surface(kind, u0, z_face + 0.5)] + edge + [surface(kind, u1, z_face + 0.5)]
        ds.polygon([proj(*p) for p in poly], fill=FLESH + (255,))
        for i in range(0, n + 1, 4):        # folds running back into the mass
            u = u0 + (u1 - u0) * i / n
            ds.line([proj(*edge[i]), proj(*surface(kind, u, z_face))], fill=FLESH_SH + (255,))
        for _ in range(4):                  # veins past the edge
            u = rnd.uniform(u0, u1)
            z1 = z_face - reach - 0.15 - rnd.random() * 0.25
            ds.line([proj(*surface(kind, u, z_face - reach * 0.85)),
                     proj(*surface(kind, u + rnd.uniform(-0.2, 0.2), z1))], fill=FLESH_DK + (255,))
    outline(sheet)
    img.alpha_composite(sheet)


def flesh_lumps(img, z_face, rnd):
    """Lumps and eyes swelling out of the flesh sheet, drawn over the face's edge so it grows out of the walls."""
    spans = {'L': (0, HIGH), 'R': (0, HIGH), 'C': (-HALF_W, HALF_W), 'F': (-HALF_W, HALF_W)}
    for _ in range(46):
        kind = rnd.choice('LLRRCCF')
        u0, u1 = spans[kind]
        z = z_face - rnd.random() ** 2.5 * 1.6
        x, y = proj(*surface(kind, rnd.uniform(u0 + 0.3, u1 - 0.3), z))
        r = F * rnd.uniform(0.2, 0.42) / z
        lobe(img, x, y, r * (0.7 if kind in 'LR' else 1.3), r * (1.3 if kind in 'LR' else 0.7))
    for kind, u, z, look in (('L', 2.6, z_face - 1.6, (1, 0)), ('L', 1.4, z_face - 2.3, (1, 0.3)),
                             ('R', 2.2, z_face - 1.9, (-1, 0)), ('R', 0.9, z_face - 1.2, (-1, -0.3)),
                             ('C', -0.8, z_face - 1.5, (0, 1))):
        x, y = proj(*surface(kind, u, z))
        s = F / z
        eye(img, x, y, max(4, round(s * 0.42)), max(3, round(s * 0.24)), look)


def floor_tendrils(img, z_face):
    d = ImageDraw.Draw(img)
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    dl = ImageDraw.Draw(layer)
    for x_root, x_end, z_end, ph in ((-1.2, -1.6, 2.6, 0.4), (0.9, 1.4, 3.0, 2.0), (0.1, -0.2, 3.6, 1.0)):
        n = 40
        for i in range(n):
            t = i / (n - 1)
            z = z_face - t * (z_face - z_end)
            x = x_root + (x_end - x_root) * t + 0.25 * math.sin(t * 6 + ph)
            sx, sy = proj(x, 0.12, z)
            r = F * (0.16 - 0.08 * t) / z
            dl.ellipse([sx - r, sy - r * 0.7, sx + r, sy + r * 0.7], fill=FLESH + (255,))
        sx, sy = proj(x_end, 0.12, z_end)
        r = F * 0.1 / z_end
        dl.ellipse([sx - r, sy - r * 0.6, sx + r, sy + r * 0.6], fill=FLESH_SH + (255,))
        dl.line([(sx - r * 0.6, sy), (sx + r * 0.6, sy)], fill=MOUTH + (255,))
    outline(layer)
    img.alpha_composite(layer)
    del d


def hallway_with_devil():
    z_face = F * 2 * HALF_W / FW                # the depth where the front drawing is exactly 1 px to 1 px
    img = hallway(depth=z_face, end_wall=False)
    flesh_creep(img, z_face, random.Random(3))
    x0, y0 = proj(-HALF_W, HIGH, z_face)
    face = devil_front()
    ImageDraw.Draw(face).rectangle([0, 0, FW - 1, FH - 1], outline=FLESH_SH + (255,))   # no hard frame round it
    img.alpha_composite(face, (round(x0), round(y0)))
    flesh_lumps(img, z_face, random.Random(5))
    floor_tendrils(img, z_face)
    return img


# ------------------------------------------------------------------ the loop, top down

def plan(scale=14):
    """The 8th floor from above: 32 blocks of hallway, rooms both sides, a stairwell at each end."""
    L, Wd = 32, 5
    pad_x, pad_y = 70, 90
    w, h = pad_x * 2 + (L + 8) * scale, pad_y * 2 + (Wd + 8) * scale
    img = Image.new('RGBA', (w, h), PAPER + (255,))
    d = ImageDraw.Draw(img)
    f_s, f_m = font(15), font(18)
    hx0, hy0 = pad_x + 4 * scale, pad_y + 4 * scale

    def r(x0, y0, x1, y1, fill, edge=INK):
        d.rectangle([hx0 + x0 * scale, hy0 + y0 * scale, hx0 + x1 * scale - 1, hy0 + y1 * scale - 1], fill=fill,
                    outline=edge)
    for i in range(8):                                    # rooms
        r(i * 4, -4, i * 4 + 4, 0, (206, 200, 186))
        r(i * 4, Wd, i * 4 + 4, Wd + 4, (206, 200, 186))
    r(0, 0, L, Wd, FLOOR)
    r(0, 1, L, Wd - 1, RUNNER, None)
    for i in range(8):
        r(i * 4 + 1.5, -0.3, i * 4 + 2.5, 0.3, DOOR, None)
        r(i * 4 + 1.5, Wd - 0.3, i * 4 + 2.5, Wd + 0.3, DOOR, None)
        d.text((hx0 + (i * 4 + 2) * scale, hy0 - 2 * scale), str(801 + i), fill=LABEL, font=f_s, anchor='mm')
        d.text((hx0 + (i * 4 + 2) * scale, hy0 + (Wd + 2) * scale), str(816 - i), fill=LABEL, font=f_s, anchor='mm')
    for x0 in (-4, L):                                    # stairwells
        r(x0, -1, x0 + 4, Wd + 1, (170, 174, 178))
        for k in range(8):
            d.line([(hx0 + (x0 + 0.5) * scale, hy0 + (-0.5 + k * 0.8) * scale),
                    (hx0 + (x0 + 3.5) * scale, hy0 + (-0.5 + k * 0.8) * scale)], fill=STEEL_SH)
        r(x0 + (3.7 if x0 < 0 else 0), 1.5, x0 + (4.3 if x0 < 0 else 0.3), 3.5, STEEL)
    d.text((hx0 - 2 * scale, hy0 + (Wd + 2.6) * scale), 'stairs', fill=LABEL, font=f_s, anchor='mm')
    d.text((hx0 + (L + 2) * scale, hy0 + (Wd + 2.6) * scale), 'stairs', fill=LABEL, font=f_s, anchor='mm')
    # the loop arrows
    ax0, ax1 = hx0 + (L + 2) * scale, hx0 - 2 * scale
    top = pad_y - 30
    d.line([(ax0, hy0 - 4.5 * scale), (ax0, top), (ax1, top), (ax1, hy0 - 4.5 * scale)], fill=(200, 40, 40), width=3)
    d.polygon([(ax1 - 7, hy0 - 4.5 * scale - 10), (ax1 + 7, hy0 - 4.5 * scale - 10), (ax1, hy0 - 4.5 * scale)],
              fill=(200, 40, 40))
    d.text(((ax0 + ax1) / 2, top - 26), 'walk out of either end, or up or down the stairs,', fill=(170, 30, 30),
           font=f_m, anchor='md')
    d.text(((ax0 + ax1) / 2, top - 6), 'and you step out of the other stairwell back onto 8F, with no cut',
           fill=(170, 30, 30), font=f_m, anchor='md')
    bot = h - 24
    d.text((w / 2, bot), 'you, for scale: 1 block = 14 px here', fill=LABEL, font=f_s, anchor='mm')
    pl = Image.new('RGBA', (16, 40), (0, 0, 0, 0))
    player(pl, 4, 36)
    img.alpha_composite(pl.resize((16 * scale // 16 * 2, 40 * scale // 16 * 2), Image.NEAREST),
                       (int(hx0 + 6 * scale), int(hy0 + 1.2 * scale)))
    return img


# ------------------------------------------------------------------ sheets

def panel(img, scale):
    return img.resize((img.width * scale, img.height * scale), Image.NEAREST)


def ruler(d, x, y, blocks, px_per_block, f):
    for b in range(blocks + 1):
        xx = x + b * px_per_block
        d.line([(xx, y), (xx, y + (8 if b % 5 == 0 else 5))], fill=LABEL, width=2)
    d.line([(x, y), (x + blocks * px_per_block, y)], fill=LABEL, width=2)
    d.text((x + blocks * px_per_block + 8, y + 4), f'{blocks} blocks', fill=LABEL, font=f, anchor='lm')


def devil_sheet():
    S = 6
    f_t, f_s = font(22), font(15)
    front, side = devil_front(), devil_side()
    pad, gap = 40, 50
    w = pad * 2 + FW * S + gap + SW * S
    h = pad + 40 + FH * S + 60 + 50 + 33 * S + 80
    sheet = Image.new('RGBA', (w, h), PAPER + (255,))
    d = ImageDraw.Draw(sheet)
    y = pad + 34
    # front, framed as the hallway's end
    d.text((pad, pad), 'FRONT: what you face down the hallway', fill=LABEL, font=f_t)
    sheet.alpha_composite(panel(front, S), (pad, y))
    d.rectangle([pad - 2, y - 2, pad + FW * S + 1, y + FH * S + 1], outline=LABEL, width=2)
    d.text((pad, y + FH * S + 8), 'fills the hallway wall to wall, floor to ceiling (5 x 4 blocks)', fill=LABEL,
           font=f_s)
    # side
    sx = pad + FW * S + gap
    d.text((sx, pad), 'SIDE: hallway walls cut away, you on the left', fill=LABEL, font=f_t)
    room = Image.new('RGBA', (SW, FH), (236, 232, 222, 255))
    ImageDraw.Draw(room).rectangle([0, FH - 1, SW, FH], fill=RUNNER)
    room.alpha_composite(side)
    player(room, 22, FH - 1)
    sheet.alpha_composite(panel(room, S), (sx, y))
    d.rectangle([sx - 2, y - 2, sx + SW * S + 1, y + FH * S + 1], outline=LABEL, width=2)
    d.text((sx, y + FH * S + 8), 'about 5 blocks deep, its flesh creeping 5 more along the ceiling and floor; '
           'tendrils on the carpet reach for you', fill=LABEL, font=f_s)
    ruler(d, sx, y + FH * S + 34, 10, 16 * S, f_s)
    # the heart
    y2 = y + FH * S + 80
    d.text((pad, y2), 'THE HEART: its first small form on the stairs, and the core inside the mass', fill=LABEL,
           font=f_t)
    y3 = y2 + 40 + 7 * S
    hf, hs = heart(True), heart(False)
    sheet.alpha_composite(panel(hf, S), (pad, y3))
    sheet.alpha_composite(panel(hs, S), (pad + 48 * S + 30, y3))
    d.text((pad, y3 + 26 * S + 6), 'front (3 blocks wide)', fill=LABEL, font=f_s)
    d.text((pad + 48 * S + 30, y3 + 26 * S + 6), 'side', fill=LABEL, font=f_s)
    pl = Image.new('RGBA', (16, 34), (0, 0, 0, 0))
    player(pl, 4, 33)
    px = pad + 96 * S + 70
    sheet.alpha_composite(panel(pl, S), (px, y3 + 26 * S - 33 * S))
    d.text((px + 8 * S, y3 + 26 * S + 6), 'you', fill=LABEL, font=f_s, anchor='ma')
    notes = ['Eyes: white with a dark red iris (one source says red eyes: say if they should be red all over).',
             'Teeth: flat human teeth in every mouth, the side ones shut and grinding; Denji jumps into the big one.',
             'In the game: big boxes with flat paint, the way Pochita and Beam v8 are built.']
    for i, n in enumerate(notes):
        d.text((px + 8 * S + 80, y3 + 6 + i * 26), n, fill=LABEL, font=f_s)
    return sheet.convert('RGB')


def hallway_sheet():
    S = 4
    f_t, f_s = font(22), font(15)
    a, b, p = hallway(), hallway_with_devil(), plan()
    pad, gap = 40, 40
    w = max(pad * 2 + VW * S * 2 + gap, p.width + pad * 2)
    h = pad + 36 + VH * S + 50 + 40 + p.height + 20
    sheet = Image.new('RGBA', (w, h), PAPER + (255,))
    d = ImageDraw.Draw(sheet)
    d.text((pad, pad), 'THE 8TH-FLOOR HALLWAY, in your own eyes', fill=LABEL, font=f_t)
    d.text((pad + VW * S + gap, pad), 'ITS DEVIL GROWN OUT OF THE WALLS, same view', fill=LABEL, font=f_t)
    y = pad + 34
    sheet.alpha_composite(panel(a, S), (pad, y))
    sheet.alpha_composite(panel(b, S), (pad + VW * S + gap, y))
    d.text((pad, y + VH * S + 8), 'cream walls over dark wood skirting, a red runner on dark wood, wooden room doors '
           'with number plates, ceiling lights every 4 blocks, a grey stair door under a green exit sign', fill=LABEL,
           font=f_s)
    d.text((pad, y + VH * S + 30), 'the loop: the far stair door (and the stairs) lead straight back here',
           fill=LABEL, font=f_s)
    y2 = y + VH * S + 60
    d.text((pad, y2), 'THE LOOP, from above', fill=LABEL, font=f_t)
    sheet.alpha_composite(p, (pad, y2 + 30))
    return sheet.convert('RGB')


def main(out):
    os.makedirs(out, exist_ok=True)
    devil_sheet().save(os.path.join(out, 'eternity_devil.png'))
    hallway_sheet().save(os.path.join(out, 'eternity_hallway.png'))
    print('wrote', out)


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'work/eternity_look')
