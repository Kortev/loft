"""Generates block, item and GUI textures for The Shooting Star."""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from noise import fbm3, value3  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'shootingstar')
TEX = os.path.join(ROOT, 'textures')
rng = np.random.default_rng(7)


def save(img, *path):
    full = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full, optimize=True)


def noise2(w, h, scale, seed, octaves=3):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    # Tileable: sample a torus.
    a = xx / w * 2 * np.pi
    b = yy / h * 2 * np.pi
    r = scale / (2 * np.pi)
    return fbm3(np.cos(a) * r + 10, np.sin(a) * r + np.cos(b) * r, np.sin(b) * r, octaves, seed=seed)


def hex_edges(size=16):
    """Distance to the nearest cell edge of a hex lattice that tiles a 16x16 block face."""
    a1 = np.array([8.0, 0.0])
    a2 = np.array([4.0, 8.0])
    pts = []
    for i in range(-3, 5):
        for j in range(-3, 5):
            pts.append(a1 * i + a2 * j)
    pts = np.array(pts)
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float64) + 0.5
    d = np.sqrt((xx[..., None] - pts[:, 0]) ** 2 + (yy[..., None] - pts[:, 1]) ** 2)
    d.sort(axis=-1)
    return d[..., 1] - d[..., 0]


def crust(heat):
    edge = hex_edges()
    line = np.clip(1.4 - edge, 0, 1)
    halo = np.clip(2.6 - edge, 0, 1) * (1 - line)
    n = noise2(16, 16, 6, seed=60 + heat)
    fleck = noise2(16, 16, 16, seed=70 + heat)
    if heat == 3:
        line = np.clip(1.1 - edge, 0, 1)
        halo = np.clip(2.2 - edge, 0, 1) * (1 - line)
        plate = np.array([238, 104, 26]) * (0.8 + 0.35 * n)[..., None]
        plate = plate * (1 - 0.3 * (fleck > 0.64))[..., None]
        glow = np.array([255, 238, 150])
    elif heat == 2:
        plate = np.array([88, 30, 16]) * (0.8 + 0.5 * n)[..., None]
        glow = np.array([255, 138, 36])
    elif heat == 1:
        plate = np.array([40, 24, 22]) * (0.85 + 0.4 * n)[..., None]
        glow = np.array([196, 58, 22])
    else:
        plate = np.array([28, 25, 28]) * (0.8 + 0.5 * n)[..., None]
        plate = plate + (fleck > 0.7)[..., None] * np.array([18, 18, 22])
        glow = np.array([74, 34, 26])
    halo_col = plate * 0.5 + glow * 0.5
    rgb = plate * (1 - line - halo)[..., None] + glow * line[..., None] + halo_col * halo[..., None]
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), 'RGB')


def hull():
    n = noise2(16, 16, 8, seed=80)
    base = np.array([36, 32, 34], dtype=np.float64) * (0.85 + 0.3 * n)[..., None]
    img = base.copy()
    seam = np.array([20, 18, 20])
    img[:, 0] = seam
    img[:, 8] = seam
    img[0, :] = seam
    for (x, y) in ((2, 2), (6, 2), (10, 2), (14, 2), (2, 13), (6, 13), (10, 13), (14, 13)):
        img[y, x] = np.array([70, 64, 66])
    img[1:, 1] = img[1:, 1] * 1.15
    img[1:, 9] = img[1:, 9] * 1.15
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')


def coil():
    n = noise2(16, 16, 8, seed=81)
    img = np.zeros((16, 16, 3))
    for y in range(16):
        for x in range(16):
            if y < 2 or y > 13:
                img[y, x] = np.array([38, 30, 28]) * (0.9 + 0.2 * n[y, x])
            else:
                t = (y - 2) / 11.0
                core = np.array([255, 168, 70]) * (1 - abs(t - 0.5)) * 2
                img[y, x] = np.clip(np.array([214, 86, 22]) + core * 0.35, 0, 255)
                if x % 4 == 0:
                    img[y, x] = img[y, x] * 0.55
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')


def uplink_atlas():
    """16x16 atlas for the 3D uplink model: front (0..6 x 0..9), side (6..10 x 0..9),
    top (0..6 x 9..13), antenna (10..11), tip (11..12), knob (12..14)."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    body = np.array([58, 56, 62, 255])
    dark = np.array([30, 29, 33, 255])
    # front
    img[0:9, 0:6] = body
    img[0, 0:6] = dark
    img[8, 0:6] = dark
    img[0:9, 0] = dark
    img[0:9, 5] = dark
    img[1:4, 1:5] = np.array([150, 18, 22, 255])
    img[2, 2:4] = np.array([236, 70, 60, 255])
    for y in (5, 7):
        for x in (1, 3):
            img[y, x] = np.array([110, 106, 112, 255])
        img[y, 4] = np.array([200, 120, 40, 255])
    # side with grip ridges
    img[0:9, 6:10] = body
    img[0:9, 6] = dark
    img[0:9, 9] = dark
    for y in range(1, 8, 2):
        img[y, 7:9] = dark
    # top/bottom
    img[9:13, 0:6] = np.array([70, 68, 74, 255])
    # antenna, tip, knob
    img[0:16, 10] = np.array([22, 22, 24, 255])
    img[0:16, 11] = np.array([212, 34, 30, 255])
    img[0:16, 12:14] = np.array([44, 42, 46, 255])
    img[0:16, 14:16] = np.array([96, 92, 100, 255])
    return Image.fromarray(img, 'RGBA')


# --- Ω-00 Ginnungagap ---------------------------------------------------------------------------

def shades(v, colours, shares):
    """Paints v in a short palette of flat colours, darkest where v is lowest, each colour covering its share of
    the pixels: the few shades a hand-made block texture uses."""
    rank = np.argsort(np.argsort(v, axis=None, kind='stable'), kind='stable').reshape(v.shape) / (v.size - 1)
    cuts = np.cumsum(shares)[:-1] / np.sum(shares)
    return np.array(colours, dtype=np.float64)[np.digitize(rank, cuts)]


def lattice(across, down, seed, octaves=3):
    """Tileable 16x16 value noise on a wrapping lattice, `across` cells wide and `down` cells tall (each octave
    twice as fine): even blotches with no seam and no diagonal grain, or a grain on purpose, like bark's."""
    r = np.random.default_rng(seed)
    total = np.zeros((16, 16))
    norm = 0.0
    for o in range(octaves):
        amp = 0.5 ** o
        grid = r.random((down * 2 ** o, across * 2 ** o))
        axes = []
        for n in grid.shape:
            t = (np.arange(16) + 0.5) / 16 * n - 0.5
            i = np.floor(t).astype(int)
            f = t - i
            axes.append((i % n, (i + 1) % n, f * f * (3 - 2 * f)))
        (y0, y1, fy), (x0, x1, fx) = axes
        top = grid[y0][:, x0] * (1 - fx) + grid[y0][:, x1] * fx
        bottom = grid[y1][:, x0] * (1 - fx) + grid[y1][:, x1] * fx
        total += amp * (top * (1 - fy)[:, None] + bottom * fy[:, None])
        norm += amp
    return total / norm


# The Genesis Key, laid diagonally like a tool: a square ring bow with a small white gem in a dark setting, a
# three-tone shaft, and two bits below the tip. o outline, w highlight, c body, s shade.
GENESIS_KEY = (
    '................',
    '...........ooo..',
    '..........owcso.',
    '.........owcscso',
    '........owcscso.',
    '.......owcsooo..',
    '......owcscso...',
    '.....owcscso....',
    'ooooowcsooo.....',
    'owwwwcso........',
    'owcoocso........',
    'owowwoso........',
    'ocowcoso........',
    'occoosso........',
    'occcssso........',
    'oooooooo........',
)


def genesis_key():
    """The Genesis Key's item icon, drawn from GENESIS_KEY: pale cyan, white highlights, a dark teal outline."""
    palette = {'o': (11, 59, 68, 255), 'w': (255, 255, 255, 255), 'c': (168, 248, 255, 255), 's': (95, 208, 227, 255)}
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    for y, row in enumerate(GENESIS_KEY):
        for x, ch in enumerate(row):
            if ch in palette:
                img[y, x] = palette[ch]
    return Image.fromarray(img, 'RGBA')


# The Genesis Key in 3D: a model of cuboids (pixels, the key upright with its bow at the bottom and laid over on the
# diagonal the icon is drawn on, so it sits in the hand the way the flat one did) and a little atlas of its colours.
KEY_ATLAS = {'dark': (0, 0), 'pale': (4, 0), 'mid': (8, 0), 'white': (12, 0), 'gem': (0, 4), 'deep': (4, 4)}
KEY_PARTS = [
    # (from, to, face colour, side colour)
    ((4, 0, 7), (12, 1, 9), 'pale', 'mid'),          # the bow: a square ring
    ((4, 6, 7), (12, 7, 9), 'pale', 'mid'),
    ((4, 1, 7), (5, 6, 9), 'pale', 'mid'),
    ((11, 1, 7), (12, 6, 9), 'pale', 'mid'),
    ((5, 1, 7.5), (11, 6, 8.5), 'deep', 'deep'),     # dark glass inside it
    ((6.5, 2.5, 6.75), (9.5, 4.5, 9.25), 'gem', 'white'),  # and the gem set through it
    ((6, 7, 6.5), (10, 8, 9.5), 'dark', 'dark'),     # the collar
    ((7, 8, 7), (9, 15, 9), 'pale', 'mid'),          # the shaft
    ((6.5, 10.5, 6.75), (9.5, 11.25, 9.25), 'white', 'white'),
    ((9, 13.5, 7.25), (12, 15, 8.75), 'pale', 'mid'),  # the bit's teeth
    ((9, 11.75, 7.25), (11, 13, 8.75), 'mid', 'mid'),
    ((7.5, 15, 7.5), (8.5, 16.5, 8.5), 'white', 'white'),  # its point
]


def genesis_key_atlas():
    """The 3D key's colours, each a 4x4 patch: the icon's teal, cyan and white, the gem with a bright heart."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    colours = {'dark': (11, 59, 68), 'pale': (168, 248, 255), 'mid': (95, 208, 227), 'white': (255, 255, 255),
               'deep': (18, 92, 106)}
    for name, (x, y) in KEY_ATLAS.items():
        if name in colours:
            img[y:y + 4, x:x + 4] = (*colours[name], 255)
    gem = np.array([[(140, 240, 255), (200, 252, 255), (200, 252, 255), (140, 240, 255)],
                    [(200, 252, 255), (255, 255, 255), (255, 255, 255), (200, 252, 255)],
                    [(200, 252, 255), (255, 255, 255), (255, 255, 255), (200, 252, 255)],
                    [(140, 240, 255), (200, 252, 255), (200, 252, 255), (140, 240, 255)]])
    img[4:8, 0:4, :3] = gem
    img[4:8, 0:4, 3] = 255
    return Image.fromarray(img, 'RGBA')


def genesis_key_model():
    """The 3D key's model JSON: every part turned 45 degrees about the middle onto the icon's diagonal."""
    def uv(name):
        x, y = KEY_ATLAS[name]
        return [x + 0.5, y + 0.5, x + 3.5, y + 3.5]
    elements = []
    for (a, b, face, side) in KEY_PARTS:
        # Upright with the bow at the bottom; Minecraft's y is up, the icon's diagonal runs bow low-left to point high-right.
        faces = {}
        for f in ('north', 'south'):
            faces[f] = {'uv': uv(face), 'texture': '#key'}
        for f in ('east', 'west', 'up', 'down'):
            faces[f] = {'uv': uv(side), 'texture': '#key'}
        elements.append({'from': list(a), 'to': list(b),
                         'rotation': {'angle': -45, 'axis': 'z', 'origin': [8, 8, 8]}, 'faces': faces})
    return {
        # No parent: item/handheld ends in builtin/generated, which builds the model from layer textures and ignores
        # elements. Its display transforms, which suit a key on this diagonal, are copied here instead.
        'textures': {'key': 'shootingstar:item/genesis_key_3d', 'particle': 'shootingstar:item/genesis_key'},
        'elements': elements,
        'gui_light': 'front',
        'display': {
            'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
            'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
            'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
            'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
            'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1.0, 1.0, 1.0]},
            'gui': {'rotation': [20, -30, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
            'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
        },
    }

def genesis_key_cracked_atlas():
    """The 3D key once it has been turned: the same colours, split by dark cracks, the gem's light gone faint and cold."""
    img = np.array(genesis_key_atlas())
    crack = (11, 59, 68, 255)
    for (x, y) in [(4, 0), (5, 1), (5, 2), (6, 3), (8, 1), (9, 2), (10, 2), (11, 3), (12, 0), (13, 1), (4, 5), (5, 6), (6, 6)]:
        img[y, x] = crack
    img[4:8, 0:4, :3] = (np.array(img[4:8, 0:4, :3], dtype=np.float64) * np.array([0.45, 0.6, 0.75])).astype(np.uint8)
    img[5, 1] = img[6, 2] = crack
    return Image.fromarray(img, 'RGBA')

# The mirror universe's blocks: cyan and teal counterparts of grass, stone, logs and leaves.
MIRROR_BARK = [(12, 64, 72), (28, 108, 119), (44, 142, 154), (88, 200, 212)]


def mirror_grass():
    """The ground of the mirror universe: a grass top in cyan, speckled lighter and darker."""
    r = np.random.default_rng(91)
    v = 0.5 * lattice(4, 4, seed=91) + 0.5 * r.random((16, 16))
    rgb = shades(v, [(22, 152, 152), (40, 200, 196), (59, 232, 226), (112, 244, 236), (176, 252, 246)],
                 [8, 20, 46, 18, 8])
    return Image.fromarray(rgb.astype(np.uint8), 'RGB')


def mirror_stone():
    """Mirror-universe stone: dark blue-teal, mottled with soft blotches."""
    r = np.random.default_rng(101)
    v = 0.75 * lattice(4, 4, seed=101) + 0.25 * r.random((16, 16))
    rgb = shades(v, [(18, 28, 41), (27, 42, 60), (34, 52, 74), (44, 66, 91), (60, 88, 113)], [6, 22, 44, 20, 8])
    return Image.fromarray(rgb.astype(np.uint8), 'RGB')


def mirror_log():
    """Mirror-universe bark: teal in vertical streaks, dark furrows between ridges that catch a lighter cyan."""
    v = 0.75 * lattice(8, 1, seed=94, octaves=2) + 0.25 * lattice(16, 4, seed=95, octaves=1)
    rgb = shades(v, MIRROR_BARK, [20, 46, 22, 12])
    return Image.fromarray(rgb.astype(np.uint8), 'RGB')


def mirror_log_top():
    """The cut end of a mirror-universe log: bark round the edge, pale cyan wood inside with growth rings."""
    yy, xx = np.mgrid[0:16, 0:16].astype(np.float64) + 0.5
    d = (np.abs(xx - 8) ** 3 + np.abs(yy - 8) ** 3) ** (1 / 3)  # squarish rings, like a vanilla log end
    d = d + 0.9 * (lattice(4, 4, seed=96) - 0.5)
    wood = np.array([(70, 178, 190), (126, 222, 230), (150, 236, 242)], dtype=np.float64)[np.digitize(d % 2.6, [0.85, 1.9])]
    wood[d < 1.2] = (88, 196, 206)
    bark = shades(lattice(8, 8, seed=97), MIRROR_BARK[:3], [30, 45, 25])
    edge = np.maximum(np.abs(xx - 8), np.abs(yy - 8)) > 7
    return Image.fromarray(np.where(edge[..., None], bark, wood).astype(np.uint8), 'RGB')


def mirror_leaves():
    """Mirror-universe leaves: cyan clumps with darker hollows between them, fully opaque."""
    r = np.random.default_rng(102)
    v = 0.65 * lattice(4, 4, seed=102) + 0.35 * r.random((16, 16))
    rgb = shades(v, [(14, 82, 94), (26, 128, 144), (47, 184, 201), (92, 216, 228), (156, 240, 246)],
                 [14, 22, 38, 18, 8])
    return Image.fromarray(rgb.astype(np.uint8), 'RGB')


def sphere(size, texture, light=(-0.6, 0.45, 0.66), spin=0.0, tilt=0.15):
    tex = np.asarray(texture.convert('RGB'), dtype=np.float64)
    th, tw = tex.shape[:2]
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float64) + 0.5
    nx = (xx - size / 2) / (size / 2)
    ny = -(yy - size / 2) / (size / 2)
    r2 = nx * nx + ny * ny
    inside = r2 <= 1.0
    nz = np.sqrt(np.clip(1 - r2, 0, 1))
    # tilt about x
    c, s = np.cos(tilt), np.sin(tilt)
    y2 = ny * c - nz * s
    z2 = ny * s + nz * c
    lat = np.arcsin(np.clip(y2, -1, 1))
    lon = np.arctan2(nx, z2) + spin
    u = ((lon / (2 * np.pi) + 0.5) % 1.0) * (tw - 1)
    v = (0.5 - lat / np.pi) * (th - 1)
    col = tex[v.astype(int), u.astype(int)]
    l = np.array(light) / np.linalg.norm(light)
    shade = np.clip(nx * l[0] + ny * l[1] + nz * l[2], 0, 1) * 0.85 + 0.15
    rgb = col * shade[..., None]
    alpha = np.clip((1.0 - np.sqrt(r2)) * size * 0.5, 0, 1) * inside
    out = np.dstack([rgb, alpha * 255])
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


def starfield(w, h, seed, count):
    r = np.random.default_rng(seed)
    img = np.zeros((h, w, 3), dtype=np.float64)
    img[:] = np.array([6, 8, 18])
    for _ in range(count):
        x, y = r.integers(0, w), r.integers(0, h)
        b = 90 + r.integers(0, 165)
        img[y, x] = np.array([b, b, min(255, b + 20)])
    return img


def glow(img, cx, cy, radius, colour, strength=1.0):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / radius
    k = np.clip(1 - d, 0, 1) ** 2 * strength
    img[:] = img + np.array(colour)[None, None, :] * k[..., None]


def line(img, x0, y0, x1, y1, colour, width=1.0, fade=True):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
    for i in range(n):
        t = i / max(1, n - 1)
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        a = (t if fade else 1.0)
        for dy in range(-int(width), int(width) + 1):
            for dx in range(-int(width), int(width) + 1):
                px, py = int(round(x + dx)), int(round(y + dy))
                if 0 <= px < img.shape[1] and 0 <= py < img.shape[0]:
                    k = a * max(0.0, 1 - (dx * dx + dy * dy) ** 0.5 / (width + 0.5))
                    img[py, px] = img[py, px] * (1 - k) + np.array(colour) * k


def ellipse(img, cx, cy, rx, ry, colour, behind=None, start=0.0, end=2 * np.pi):
    steps = int(max(rx, ry) * 12)
    for i in range(steps):
        a = start + (end - start) * i / steps
        x = cx + np.cos(a) * rx
        y = cy + np.sin(a) * ry
        if behind is not None and behind(x, y, a):
            continue
        px, py = int(round(x)), int(round(y))
        if 0 <= px < img.shape[1] and 0 <= py < img.shape[0]:
            img[py, px] = np.array(colour)


def paste(img, sprite, x, y):
    s = np.asarray(sprite, dtype=np.float64)
    h = min(s.shape[0], img.shape[0] - y)
    w = min(s.shape[1], img.shape[1] - x)
    s = s[:h, :w]
    a = s[..., 3:4] / 255.0
    region = img[y:y + h, x:x + w]
    region[:] = region * (1 - a) + s[..., :3] * a


def card(jupiter_map, earth_map):
    w, h = 64, 36
    img = starfield(w, h, 3, 40)
    jup = sphere(24, jupiter_map, spin=0.6)
    cx, cy = 14, 17
    # ring behind the planet
    ring = (255, 120, 36)
    ellipse(img, cx, cy, 17, 5, ring, behind=lambda x, y, a: np.sin(a) > 0)
    paste(img, jup, cx - 12, cy - 12)
    ellipse(img, cx, cy, 17, 5, ring, behind=lambda x, y, a: np.sin(a) <= 0 or ((x - cx) ** 2 + (y - cy) ** 2) < 144)
    # Earth limb bottom right
    earth = sphere(40, earth_map, light=(-0.5, 0.6, 0.6), spin=2.0, tilt=-0.2)
    paste(img, earth.crop((0, 0, 40, 12)), 34, 26)
    # shot across from the breech to the target
    line(img, 30, 14, 52, 27, (255, 214, 160), width=0.6)
    glow(img, 52, 28, 6, (255, 180, 90), 1.4)
    glow(img, 52, 28, 2.5, (255, 255, 230), 2.0)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')


def icon(jupiter_map):
    s = 128
    img = starfield(s, s, 5, 160)
    jup = sphere(92, jupiter_map, spin=0.6)
    cx, cy = 50, 62
    ring = (255, 120, 36)
    for k in range(3):
        ellipse(img, cx, cy, 64 + k * 0.5, 17 + k * 0.3, ring, behind=lambda x, y, a: np.sin(a) > 0)
    paste(img, jup, cx - 46, cy - 46)
    for k in range(3):
        ellipse(img, cx, cy, 64 + k * 0.5, 17 + k * 0.3, ring,
                behind=lambda x, y, a: np.sin(a) <= 0 or ((x - cx) ** 2 + (y - cy) ** 2) < 46 * 46)
    line(img, 92, 56, 122, 104, (255, 220, 170), width=1.2)
    glow(img, 120, 104, 22, (255, 150, 60), 1.0)
    glow(img, 120, 104, 8, (255, 255, 235), 2.0)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')


def main():
    save(hull(), 'block', 'gungnir_hull.png')
    save(coil(), 'block', 'gungnir_coil.png')
    for heat in (1, 2, 3):
        save(crust(heat), 'block', 'molten_crust_%d.png' % heat)
    save(crust(0), 'block', 'fused_crust.png')
    save(uplink_atlas(), 'item', 'gungnir_uplink.png')
    save(genesis_key(), 'item', 'genesis_key.png')
    save(genesis_key_atlas(), 'item', 'genesis_key_3d.png')
    save(genesis_key_cracked_atlas(), 'item', 'genesis_key_cracked_3d.png')
    key = genesis_key_model()
    cracked = genesis_key_model()
    cracked['textures']['key'] = 'shootingstar:item/genesis_key_cracked_3d'
    # Once turned it is cracked: the same key, split, its gem dim. Which one shows is the shootingstar:cracked predicate.
    key['overrides'] = [{'predicate': {'shootingstar:cracked': 1}, 'model': 'shootingstar:item/genesis_key_cracked'}]
    for name, model in (('genesis_key', key), ('genesis_key_cracked', cracked)):
        with open(os.path.join(ROOT, 'models', 'item', name + '.json'), 'w') as f:
            json.dump(model, f, indent=2)
            f.write('\n')
    save(mirror_grass(), 'block', 'mirror_grass.png')
    save(mirror_stone(), 'block', 'mirror_stone.png')
    save(mirror_log(), 'block', 'mirror_log.png')
    save(mirror_log_top(), 'block', 'mirror_log_top.png')
    save(mirror_leaves(), 'block', 'mirror_leaves.png')
    jupiter_map = Image.open(os.path.join(TEX, 'feed', 'jupiter.jpg'))
    earth_map = Image.open(os.path.join(TEX, 'feed', 'earth_day.jpg'))
    save(card(jupiter_map, earth_map), 'gui', 'gungnir_card.png')
    icon(jupiter_map).save(os.path.join(ROOT, 'icon.png'), optimize=True)
    print('textures written')


if __name__ == '__main__':
    main()
