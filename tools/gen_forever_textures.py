"""Textures for Forever, the Eternity contract's power (Chainsaw Man mod): the devil's flesh, eyes, mouths, cracks, the stored-hit
slashes, the release burst and the eyes that stare in from a locked player's screen edges. All drawn in the Eternity Devil's own
palette and helpers (tools/eternity_drawings.py: flesh pink with dark ink folds, white eyes with a square iris, lens-shaped gapes
of uneven human teeth).

Run: python tools/gen_forever_textures.py   (writes into chainsawman/src/client/resources/assets/chainsawman/textures)
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import eternity_drawings as ed  # noqa: E402

OUT = os.path.join(HERE, '..', 'chainsawman', 'src', 'client', 'resources', 'assets', 'chainsawman', 'textures')


def save(img, rel):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print('wrote', rel, img.size)


def periodic_noise(size, cells, seed):
    """Value noise that tiles: a size x size grid of floats 0..1."""
    rnd = random.Random(seed)
    lattice = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def smooth(t):
        return t * t * (3 - 2 * t)

    out = [[0.0] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            fx, fy = x / size * cells, y / size * cells
            x0, y0 = int(fx) % cells, int(fy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = smooth(fx - int(fx)), smooth(fy - int(fy))
            a = lattice[y0][x0] * (1 - tx) + lattice[y0][x1] * tx
            b = lattice[y1][x0] * (1 - tx) + lattice[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def flesh(size=64):
    """Meaty irregular flesh: patches of pink, shade and sheen, dark folds, pores, a few red veins; tiles."""
    img = Image.new('RGBA', (size, size), ed.FLESH + (255,))
    px = img.load()
    n1 = periodic_noise(size, 4, 11)
    n2 = periodic_noise(size, 8, 23)
    n3 = periodic_noise(size, 16, 5)
    for y in range(size):
        for x in range(size):
            v = 0.55 * n1[y][x] + 0.3 * n2[y][x] + 0.15 * n3[y][x]
            if v < 0.34:
                c = (92, 28, 40)
            elif v < 0.5:
                c = (132, 46, 56)
            elif v < 0.64:
                c = (168, 72, 74)
            elif v < 0.76:
                c = (192, 96, 90)
            else:
                c = (220, 132, 112)
            px[x, y] = c + (255,)
    rnd = random.Random(7)
    d = ImageDraw.Draw(img)
    # Folds: wandering dark creases (drawn three times over the tile edges so they wrap).
    for _ in range(14):
        x, y = rnd.uniform(0, size), rnd.uniform(0, size)
        ang = rnd.uniform(0, math.tau)
        pts = []
        for _ in range(9):
            pts.append((x, y))
            ang += rnd.uniform(-0.7, 0.7)
            x += math.cos(ang) * rnd.uniform(3, 6)
            y += math.sin(ang) * rnd.uniform(3, 6)
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                d.line([(a + ox, b + oy) for a, b in pts], fill=(52, 14, 26, 255), width=1)
                d.line([(a + ox, b + oy + 1) for a, b in pts], fill=(92, 28, 40, 255), width=1)
    # Veins: thin red lines.
    for _ in range(5):
        x, y = rnd.uniform(0, size), rnd.uniform(0, size)
        ang = rnd.uniform(0, math.tau)
        pts = []
        for _ in range(7):
            pts.append((x, y))
            ang += rnd.uniform(-0.9, 0.9)
            x += math.cos(ang) * rnd.uniform(2, 4)
            y += math.sin(ang) * rnd.uniform(2, 4)
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                d.line([(a + ox, b + oy) for a, b in pts], fill=(120, 20, 36, 255), width=1)
    for _ in range(40):
        px[rnd.randrange(size), rnd.randrange(size)] = ed.FLESH_DK + (255,)
    return img


def eye_sphere(w=128, h=64):
    """An eyeball as a lat/long map: the front is the middle. White with faint red veins, a dark square iris with a black square
    pupil (the Eternity Devil's eyes), a bloodshot rim at the back."""
    img = Image.new('RGBA', (w, h), ed.SCLERA + (255,))
    d = ImageDraw.Draw(img)
    rnd = random.Random(3)
    # Veins creeping in from the back (the left and right edges of the map).
    for side in (0, w - 1):
        for _ in range(9):
            x, y = side, rnd.uniform(4, h - 4)
            for _ in range(rnd.randint(6, 12)):
                nx = x + (1 if side == 0 else -1) * rnd.uniform(1, 3)
                ny = y + rnd.uniform(-2, 2)
                d.line([(x, y), (nx, ny)], fill=(176, 52, 60, 255))
                x, y = nx, ny
    cx, cy = w // 2, h // 2
    iw, ih = 20, 20
    d.rectangle([cx - iw // 2 - 2, cy - ih // 2 - 2, cx + iw // 2 + 1, cy + ih // 2 + 1], fill=(120, 24, 34, 255))
    d.rectangle([cx - iw // 2, cy - ih // 2, cx + iw // 2 - 1, cy + ih // 2 - 1], fill=ed.IRIS + (255,))
    d.rectangle([cx - 4, cy - 4, cx + 3, cy + 3], fill=ed.PUPIL + (255,))
    d.rectangle([cx - iw // 2 + 2, cy - ih // 2 + 2, cx - iw // 2 + 4, cy - ih // 2 + 3], fill=(236, 160, 150, 255))
    return img


def mouth_atlas():
    """A palette atlas for mouths (flat cells, sampled by uv): lip, lip shade, throat, tongue, tooth, tooth shade, gum, ink."""
    cells = [ed.LIP, (150, 48, 64), ed.MOUTH, ed.TONGUE, ed.TOOTH, ed.TOOTH_SH, (176, 60, 74), ed.INK]
    img = Image.new('RGBA', (32, 8), (0, 0, 0, 0))
    for i, c in enumerate(cells):
        ImageDraw.Draw(img).rectangle([i * 4, 0, i * 4 + 3, 7], fill=c + (255,))
    return img


def crack():
    """A strip across a crack: dark ink at the edges, red flesh in the middle, drawn along u."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for y in range(16):
        t = abs(y - 7.5) / 7.5
        if t > 0.5:
            c = ed.INK + (255,)
        elif t > 0.3:
            c = (46, 12, 20, 255)
        elif t > 0.1:
            c = (88, 22, 32, 255)
        else:
            c = (128, 34, 40, 255)
        d.line([(0, y), (15, y)], fill=c)
    return img


def mark():
    """A stored hit: a glowing red slash, hot white at its core (drawn emissive and see-through at its ends)."""
    s = 64
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    glow = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    pts = [(6, 52), (20, 38), (30, 34), (44, 20), (58, 8)]
    for w, col in ((9, (140, 10, 24, 90)), (6, (200, 20, 30, 150)), (3, (255, 60, 52, 235)), (1, (255, 236, 214, 255))):
        gd.line(pts, fill=col, width=w, joint='curve')
    # a ragged second stroke beside it
    gd.line([(14, 58), (30, 44), (36, 46), (52, 28)], fill=(255, 70, 60, 200), width=2)
    img.alpha_composite(glow)
    return img


def burst():
    """The release: a white core with red spikes and black streaks, all flat and sharp."""
    s = 128
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(21)
    c = s / 2
    for i in range(22):
        ang = i / 22 * math.tau + rnd.uniform(-0.1, 0.1)
        length = rnd.uniform(0.62, 1.0) * c
        wid = rnd.uniform(0.05, 0.1)
        col = (214, 24, 36, 255) if i % 3 else ed.INK + (255,)
        tip = (c + math.cos(ang) * length, c + math.sin(ang) * length)
        l = (c + math.cos(ang + wid) * c * 0.18, c + math.sin(ang + wid) * c * 0.18)
        r = (c + math.cos(ang - wid) * c * 0.18, c + math.sin(ang - wid) * c * 0.18)
        d.polygon([l, tip, r], fill=col)
    for i in range(10):
        ang = i / 10 * math.tau + 0.2
        length = rnd.uniform(0.35, 0.65) * c
        tip = (c + math.cos(ang) * length, c + math.sin(ang) * length)
        d.polygon([(c + math.cos(ang + 0.16) * c * 0.1, c + math.sin(ang + 0.16) * c * 0.1), tip,
                   (c + math.cos(ang - 0.16) * c * 0.1, c + math.sin(ang - 0.16) * c * 0.1)], fill=(255, 250, 238, 255))
    d.ellipse([c - 16, c - 16, c + 16, c + 16], fill=(255, 252, 244, 255))
    return img


def eyes_hud():
    """An eye of the devil that stares in from the screen's edge: big almond, white, a square iris, heavy fleshy lids (drawn
    small, shown large with nearest-neighbour)."""
    img = Image.new('RGBA', (48, 28), (0, 0, 0, 0))
    ed.eye(img, 24, 14, 38, 20, look=(0, 0), lid=0.0)
    return img


def main():
    save(flesh(), 'entity/forever_flesh.png')
    save(eye_sphere(), 'entity/forever_eye.png')
    save(mouth_atlas(), 'entity/forever_mouth.png')
    save(crack(), 'entity/forever_crack.png')
    save(mark(), 'entity/forever_mark.png')
    save(burst(), 'entity/forever_burst.png')
    save(eyes_hud(), 'gui/forever_eye.png')


if __name__ == '__main__':
    main()
