"""Builds Baron Bomburst's Vulgarian airship, from Chitty Chitty Bang Bang, in Blender.

Needs Blender's Python module (pip install "bpy==4.5.*", on Python 3.11). Run from the repository root:

    python tools/airship_model.py --out DIR              # writes DIR/airship.blend
    python tools/airship_model.py --out DIR --renders    # also renders her flying, from the ground and close up
    python tools/airship_model.py --out DIR --textures   # also writes the painted textures as PNGs

Blender units are blocks (metres), as for Chitty (tools/chitty_model.py, whose helpers this uses). The airship faces
+Y with +X on her right and +Z up. The origin is under the middle of the gondola's keel: she lands on her gondola, and
that is where she is boarded.

The film's airship was a real one, built to fly in 1967: a copy of a 1904 Lebaudy, 112 feet long and 30 across (34
and 9 blocks here). Her envelope is short and deep, the same fore and aft, the pointed tips set above its middle so
that it looks hooked. Its ends are banded in Vulgaria's colours, black and purple on white, and a black griffin, the
arms of Vulgaria, rears on each flank. Under it hang a flat platform, a long open frame of bronze tubes with crossed
fins at its tail, and below that the gondola: black, carved and gilded, with the Baron's B at each end, a drum of rope
at the bow, an engine house at the stern turning two propellers, a helm and benches for six.

Parts that move in the game are objects of their own with their origins on their pivots: the two propellers, the
rudder and elevator, the helm, the rope drum, the hook, the ladder and each of the six bombs in the rack. Empties mark
the seats, where the hook's rope comes out of the gondola, the lamps and the exhaust.
"""
import math
import os
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
import numpy as np
from mathutils import Matrix, Vector
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chitty_model as cm  # noqa: E402  (materials, meshes, lofts, tubes and the render scene)

# --- layout (blocks) ---------------------------------------------------------------------------------

ENV_LENGTH = 34.0
ENV_R = 4.55            # the envelope's radius over its straight middle
ENV_MID_Y = -3.0        # the gondola hangs ahead of the envelope's middle, as on a Lebaudy
ENV_Z = 9.68            # the axis of the middle
ENV_BELLY = 0.93        # the lower half is a little flatter than the top, over the platform
ENV_STRAIGHT = 0.12     # the straight middle, as a fraction of the half length; the rest tapers to the tips
ENV_TIP_RISE = 0.45     # how far above the axis the tips are, in radii: the Lebaudy hook
ENV_NOSE = ENV_MID_Y + ENV_LENGTH / 2
ENV_TAIL = ENV_MID_Y - ENV_LENGTH / 2

# The flat platform under the envelope, pointed at both ends.
PLATFORM = dict(front=8.0, back=-15.0, half_width=3.2, z=5.35)
# The long open frame under the platform, from which the gondola hangs and which carries the tail.
TRUSS = dict(front=8.6, back=-17.6, half_width=1.0, top=5.25, bottom=2.6, panel=1.6, tip_z=3.9)
# The crossed tail: fixed fin and stabiliser, then the rudder and elevator on hinges.
TAIL = dict(lead=-15.9, hinge=-18.3, trail=-20.0, z=3.9, height=1.9, span=3.1)

# The gondola: a scow, flat bottomed with its ends swept up, flaring out to the top.
GON_HALF_LENGTH = 3.6       # along the top
GON_KEEL_HALF = 2.5         # how far the flat of the bottom runs from the middle
GON_END_RISE = 1.0          # how high the bottom has swept up at the very ends
GON_HALF_WIDTH = 1.35       # at the top
GON_BOTTOM_HALF = 1.12      # at the bottom
GON_TOP = 1.2
GON_FLOOR = 0.3
GON_BILGE = 0.16            # the rounding where the sides meet the bottom
RAIL_Z = 1.55
GATE_Y = 0.25               # the gap in the rail on her right where the ladder hangs

ENGINE_HOUSE = dict(back=-3.05, front=-1.75, half_width=1.2, base=0.95, roof=2.3)
PROP = dict(x=2.7, y=-3.2, z=3.0, r=1.4)
HELM = Vector((0.0, 2.45, 1.2))
DRUM = dict(back=2.95, front=4.35, z=1.45, r=0.34)
LINE_OUT = Vector((0.0, 0.3, 0.0))     # where the hook's rope comes out under the keel
BOMB_RACK_Y = -1.32
BENCHES = (1.0, -0.45)

# Where everyone sits: the pilot stands at the helm; three on the front bench, two on the back one either side of
# the rope's hatch.
SEATS = [(0.0, 1.95), (-0.8, 1.0), (0.0, 1.0), (0.8, 1.0), (-0.75, -0.45), (0.75, -0.45)]

GRIFFIN_HEIGHT = 6.6        # on the envelope's flank
GRIFFIN_Y = -1.6

# Vulgaria's colours.
BLACK = (22, 20, 24)
PURPLE = (86, 34, 112)
WHITE = (236, 232, 218)
GOLD = (214, 166, 72)


# --- painted textures ----------------------------------------------------------------------------------

def spline(points, per=10, closed=True):
    """A smooth curve through 2D points (Catmull-Rom)."""
    P = list(points)
    n = len(P)
    out = []
    for i in (range(n) if closed else range(n - 1)):
        p0 = P[(i - 1) % n] if closed else P[max(i - 1, 0)]
        p1 = P[i]
        p2 = P[(i + 1) % n] if closed else P[min(i + 1, n - 1)]
        p3 = P[(i + 2) % n] if closed else P[min(i + 2, n - 1)]
        for k in range(per):
            t = k / per
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t * t * t) for j in (0, 1)))
    if not closed:
        out.append(P[-1])
    return out


def stroke(d, path, w0, w1, fill):
    """A line along a path, its width going from w0 to w1, with round joints."""
    for i in range(len(path) - 1):
        w = w0 + (w1 - w0) * i / max(len(path) - 2, 1)
        d.line([path[i], path[i + 1]], fill=fill, width=max(1, int(round(w))))
        r = w / 2
        x, y = path[i]
        d.ellipse([x - r, y - r, x + r, y + r], fill=fill)


def talons(d, foot, angles, length, width):
    fx, fy = foot
    for a in angles:
        a = math.radians(a)
        tip = (fx + math.cos(a) * length, fy + math.sin(a) * length)
        n = (-math.sin(a) * width / 2, math.cos(a) * width / 2)
        mid = (fx + math.cos(a) * length * 0.6 + n[0] * 0.6, fy + math.sin(a) * length * 0.6 + n[1] * 0.6)
        d.polygon([(fx + n[0], fy + n[1]), (fx - n[0], fy - n[1]), mid, tip], fill=255)
        b = a + math.radians(70)
        d.polygon([tip, (tip[0] + math.cos(b) * width, tip[1] + math.sin(b) * width), mid], fill=255)


def griffin_mask():
    """Vulgaria's griffin, rampant and facing right: an eagle's head, wings and fore-talons on a lion's body, hind legs
    and tail. A 1000 x 1200 mask, 255 where it is black."""
    img = Image.new('L', (1000, 1200), 0)
    d = ImageDraw.Draw(img)
    # The wing, raised behind: a fan of long feathers.
    tips = [(360, 95), (300, 120), (250, 175), (205, 250), (175, 340), (165, 435), (185, 525), (230, 600), (300, 650)]
    outline = [(470, 470), (455, 330), (420, 210)]
    for i, t in enumerate(tips):
        outline.append(t)
        if i + 1 < len(tips):
            mx, my = (t[0] + tips[i + 1][0]) / 2, (t[1] + tips[i + 1][1]) / 2
            outline.append((mx + (440 - mx) * 0.16, my + (480 - my) * 0.16))
    outline += [(380, 640), (430, 560)]
    d.polygon(outline, fill=255)
    for i, t in enumerate(tips[1:-1]):
        root = (430 + i * 3, 480 + i * 6)
        d.line([root, ((t[0] * 2 + root[0]) / 3, (t[1] * 2 + root[1]) / 3)], fill=0, width=6)
    # The lion's body, chest up to the right and haunch down to the left.
    d.polygon(spline([(560, 420), (670, 520), (650, 660), (590, 760), (520, 840), (420, 885), (330, 835), (325, 720),
                      (385, 590), (450, 470)]), fill=255)
    # A broad feathered neck and the eagle's head, its hooked beak open.
    d.polygon(spline([(455, 480), (470, 390), (500, 300), (550, 230), (610, 198), (670, 205), (720, 232), (775, 240),
                      (815, 265), (838, 305), (828, 352), (812, 330), (790, 306), (750, 304), (785, 326), (800, 345),
                      (765, 352), (722, 348), (690, 382), (672, 440), (672, 520), (600, 500)], per=8), fill=255)
    d.polygon([(540, 240), (490, 140), (590, 205)], fill=255)
    d.polygon([(580, 212), (565, 120), (630, 200)], fill=255)
    for k in range(6):
        y, x = 280 + k * 38, 505 - k * 9
        d.polygon([(x + 10, y - 22), (x - 48, y + 6), (x + 10, y + 26)], fill=255)
    # Fore-talons reaching forward: three claws ahead and one behind.
    stroke(d, spline([(650, 500), (740, 500), (800, 470), (840, 450)], 12, False), 78, 44, 255)
    talons(d, (845, 448), [-70, -30, 10, 160], 85, 26)
    stroke(d, spline([(645, 620), (730, 660), (800, 650), (840, 625)], 12, False), 72, 42, 255)
    talons(d, (845, 622), [-45, -5, 35, 140], 80, 24)
    # Hind legs, one planted and one raised; the tail curls up behind to a tuft.
    stroke(d, spline([(440, 820), (480, 940), (430, 1030), (470, 1100)], 12, False), 90, 50, 255)
    d.polygon(spline([(430, 1085), (520, 1080), (560, 1110), (520, 1130), (430, 1125)]), fill=255)
    stroke(d, spline([(540, 790), (640, 860), (650, 960), (700, 1000)], 12, False), 80, 46, 255)
    d.polygon(spline([(670, 980), (740, 975), (770, 1005), (740, 1025), (670, 1020)]), fill=255)
    stroke(d, spline([(350, 830), (240, 880), (170, 820), (190, 720), (160, 640)], 12, False), 40, 20, 255)
    d.polygon(spline([(160, 650), (110, 600), (120, 530), (160, 570), (175, 500), (200, 580), (190, 640)]), fill=255)
    # Its eye, left as the envelope's white.
    d.ellipse([660, 232, 690, 262], fill=0)
    d.ellipse([668, 240, 682, 254], fill=255)
    return np.asarray(img, dtype=np.float32) / 255.0


ENV_TEX = (3072, 2048)      # pixels along the envelope (v) and round it (u)


def envelope_texture():
    """The envelope's doped cotton, laid out with v along her (tail at 0) and u round each section (0 at the top, a
    quarter on her right flank, three quarters on her left): seams between the panels, bands of black and purple on
    white towards each tip, and the griffin on each flank facing the nose."""
    nv, nu = ENV_TEX
    tex = np.empty((nv, nu, 3), np.float32)
    tex[:] = np.array(WHITE, np.float32) / 255
    y = ENV_TAIL + (np.arange(nv) + 0.5) / nv * ENV_LENGTH
    # The bands: from each tip, black and purple in turn, each parted from the next by a narrow white stripe.
    band, gap = 1.75, 0.22

    def bands(dist, count):
        k = np.floor(dist / (band + gap)).astype(int)
        inside = (dist - k * (band + gap)) < band
        colour = np.where((k % 2 == 0)[:, None], np.array(BLACK) / 255, np.array(PURPLE) / 255)
        return np.where((inside & (k < count))[:, None], colour, np.nan)

    for dist, count in ((ENV_NOSE - y, 6), (y - ENV_TAIL, 4)):
        c = bands(dist, count)
        rows = ~np.isnan(c[:, 0])
        tex[rows] = c[rows][:, None, :]
    # Seams: along her between the gores, and round her between the widths of cloth.
    seam = np.zeros((nv, nu), np.float32)
    seam[:, (np.arange(nu) % (nu // 24)) < 2] = 1
    seam[(np.arange(nv) % int(nv / ENV_LENGTH * 1.4)) < 2, :] = 1
    tex *= (1 - 0.07 * seam)[..., None]
    # The griffin on each flank, standing up the envelope and facing forward.
    g = Image.fromarray((griffin_mask() * 255).astype(np.uint8))
    circumference = 2 * math.pi * ENV_R * 0.965
    h_px = int(GRIFFIN_HEIGHT / circumference * nu)
    w_px = int(GRIFFIN_HEIGHT * g.width / g.height / ENV_LENGTH * nv)
    g = np.asarray(g.resize((w_px, h_px), Image.LANCZOS), np.float32) / 255     # (rows down, columns forward)
    v0 = int((GRIFFIN_Y - ENV_TAIL) / ENV_LENGTH * nv - w_px / 2)
    black = np.array(BLACK, np.float32) / 255
    for u_mid, block in ((0.25, g.T), (0.75, g[::-1].T)):
        u0 = int(u_mid * nu - h_px / 2)
        a = block[..., None]
        tex[v0:v0 + w_px, u0:u0 + h_px] = tex[v0:v0 + w_px, u0:u0 + h_px] * (1 - a) + black * a
    rgba = np.concatenate([tex, np.ones((nv, nu, 1), np.float32)], axis=2)
    return rgba[::-1]   # top row first, as cm.image takes it


def curl(cx, cy, r0, turns, sign, a0):
    """A log spiral winding inward from radius r0 about (cx, cy)."""
    pts = []
    n = int(40 * turns)
    for i in range(n + 1):
        t = i / n
        a = a0 + sign * t * turns * 2 * math.pi
        r = r0 * math.exp(-2.2 * t)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def bezier(p0, p1, p2, p3, n=30):
    out = []
    for i in range(n + 1):
        t = i / n
        a, b, c, e = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + e * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + e * p3[1]))
    return out


GON_ART = (2048, 342)       # the side panel's art: 7.2 blocks along by 1.2 high


def gilt(d, path, w0, w1):
    """A gilded moulding: a dark shadow under a gold stroke with a light edge, so it reads as carved."""
    sh = [(x + 2, y + 3) for x, y in path]
    stroke(d, sh, w0 + 1, w1 + 1, (70, 44, 14))
    stroke(d, path, w0, w1, GOLD)
    hi = [(x - 1, y - 1) for x, y in path]
    stroke(d, hi, max(1, w0 * 0.35), max(1, w1 * 0.35), (252, 226, 150))


def scroll(d, a, b, up, size):
    """A C-scroll from a to b: a sweep that curls in at both ends, on the side `up` (-1 above, 1 below)."""
    (ax, ay), (bx, by) = a, b
    mid_bulge = up * size * 0.9
    path = bezier(a, (ax + (bx - ax) * 0.25, ay + mid_bulge), (ax + (bx - ax) * 0.75, by + mid_bulge), b, 40)
    gilt(d, path, 7, 7)
    sgn = 1 if bx > ax else -1
    gilt(d, curl(ax - sgn * size * 0.05, ay - up * size * 0.32, size * 0.32, 1.1, -sgn * up, math.pi / 2 * up), 6, 2)
    gilt(d, curl(bx + sgn * size * 0.05, by - up * size * 0.32, size * 0.32, 1.1, sgn * up, math.pi / 2 * up), 6, 2)


def leaf(d, base, angle, length, width):
    """An acanthus leaf: a pointed blade with a rib."""
    ca, sa = math.cos(angle), math.sin(angle)
    pts = []
    for i in range(13):
        t = i / 12
        w = width * math.sin(math.pi * t) * (1 - 0.3 * t)
        pts.append((base[0] + ca * length * t - sa * w, base[1] + sa * length * t + ca * w))
    for i in range(12, -1, -1):
        t = i / 12
        w = width * math.sin(math.pi * t) * (1 - 0.3 * t)
        pts.append((base[0] + ca * length * t + sa * w, base[1] + sa * length * t - ca * w))
    d.polygon([(x + 2, y + 3) for x, y in pts], fill=(70, 44, 14))
    d.polygon(pts, fill=GOLD)
    d.line([base, (base[0] + ca * length * 0.9, base[1] + sa * length * 0.9)], fill=(150, 108, 40), width=2)


def gondola_art():
    """The gondola's sides as the film has them: black lacquer, carved and gilded. Gilt mouldings top and bottom and
    down the middle; festoons of leaves hung from rosettes along the top; great C-scrolls and acanthus below; and at
    each end an oval cartouche with the Baron's B. Symmetrical, so that it reads right from either side."""
    W, H = GON_ART
    img = Image.new('RGB', (W, H), (14, 13, 16))
    d = ImageDraw.Draw(img)
    # A faint sheen across the lacquer.
    for y in range(H):
        k = int(10 * math.exp(-((y - H * 0.3) / (H * 0.18)) ** 2))
        d.line([(0, y), (W, y)], fill=(14 + k, 13 + k, 17 + k))
    # Mouldings.
    for y, w in ((H * 0.05, 6), (H * 0.11, 3), (H * 0.94, 5)):
        gilt(d, [(0, y), (W, y)], w, w)
    for x in (W / 2 - 8, W / 2 + 8):
        gilt(d, [(x, H * 0.11), (x, H * 0.94)], 4, 4)
    # Festoons along the top band, hung between rosettes.
    step = W / 9
    for i in range(10):
        x = i * step
        for k in range(10):
            a = 2 * math.pi * k / 10
            leaf(d, (x, H * 0.2), a, 13, 5)
        d.ellipse([x - 6, H * 0.2 - 6, x + 6, H * 0.2 + 6], fill=(250, 220, 140))
        if i < 9:
            for k in range(14):
                t = (k + 0.5) / 14
                px = x + step * t
                py = H * 0.2 + math.sin(math.pi * t) * H * 0.2
                leaf(d, (px - 8, py), math.radians(-25 + 50 * t), 22, 7)
    # Below: on each half, a shell in the middle with C-scrolls and leaves running out either way.
    for cx in (W * 0.27, W * 0.73):
        cy = H * 0.66
        for k in range(9):
            a = math.radians(-160 + 140 * k / 8)
            leaf(d, (cx, cy + 22), a, 46, 9)
        d.ellipse([cx - 12, cy + 10, cx + 12, cy + 34], fill=(250, 220, 140))
        for sgn in (-1, 1):
            scroll(d, (cx + sgn * 40, cy + 30), (cx + sgn * 230, cy - 20), 1, 120)
            scroll(d, (cx + sgn * 250, cy + 8), (cx + sgn * 410, cy + 40), -1, 90)
            for k in range(5):
                leaf(d, (cx + sgn * (90 + k * 30), cy + 50 - k * 6), math.radians(-90 + sgn * (30 + 10 * k)), 34, 8)
    # Cartouches with the B at each end.
    try:
        font = ImageFont.truetype('DejaVuSerif-Bold.ttf', 120)
    except OSError:
        font = ImageFont.load_default(size=120)
    for cx in (W * 0.075, W * 0.925):
        cy = H * 0.55
        for k in range(16):
            a = 2 * math.pi * k / 16
            leaf(d, (cx + math.cos(a) * 92, cy + math.sin(a) * 120), a, 30, 9)
        gilt(d, [(cx + math.cos(a) * 92, cy + math.sin(a) * 118) for a in np.linspace(0, 2 * math.pi, 64)], 9, 9)
        d.ellipse([cx - 82, cy - 108, cx + 82, cy + 108], fill=(26, 18, 34))
        d.text((cx + 3, cy + 5), 'B', font=font, fill=(70, 44, 14), anchor='mm')
        d.text((cx, cy), 'B', font=font, fill=GOLD, anchor='mm')
    a = np.asarray(img, np.float32) / 255
    return np.concatenate([a, np.ones(a.shape[:2] + (1,), np.float32)], axis=2)


def tricolour_texture(size=256):
    """Black, purple and white in three bands, for the rudder and elevator."""
    tex = np.empty((size, size, 4), np.float32)
    tex[..., 3] = 1
    for i, c in enumerate((BLACK, PURPLE, WHITE)):
        tex[:, i * size // 3:(i + 1) * size // 3, :3] = np.array(c) / 255
    return tex


def rope_texture(size=256, turns=12):
    """Rope wound on the drum: tan strands with dark grooves winding round."""
    v, u = np.mgrid[0:size, 0:size] / size
    phase = (v * turns + u * 0.4) % 1.0
    lay = (u * 40 + v * 30) % 1.0
    k = 0.75 + 0.25 * np.sin(phase * 2 * math.pi) - 0.12 * (lay < 0.18)
    base = np.array((196, 172, 120)) / 255
    tex = np.ones((size, size, 4), np.float32)
    tex[..., :3] = base * k[..., None]
    return tex


def deck_texture(size=512, planks=12):
    """Teak planks along the gondola, with dark seams."""
    rng = np.random.default_rng(5)
    tex = np.ones((size, size, 4), np.float32)
    base = np.array((150, 96, 52)) / 255
    for i in range(planks):
        k = 0.85 + 0.25 * rng.random()
        tex[:, i * size // planks:(i + 1) * size // planks, :3] = base * k
    tex[:, (np.arange(size) % (size // planks)) < 3, :3] *= 0.45
    grain = 0.06 * np.sin(np.arange(size)[:, None] / size * 90 + rng.random((1, size)) * 20)
    tex[..., :3] *= (1 + grain)[..., None]
    return np.clip(tex, 0, 1)


def grille_texture(size=256, slats=16):
    """The engine house's sides: brass louvres in a dark frame."""
    tex = np.ones((size, size, 4), np.float32)
    tex[..., :3] = np.array((20, 18, 18)) / 255
    pitch = size // slats
    y = np.arange(size) % pitch
    rows = (y > pitch * 0.2) & (y < pitch * 0.7)
    shade = np.linspace(1.0, 0.55, size)[:, None]
    brass = np.array((176, 132, 60)) / 255
    for r in np.nonzero(rows)[0]:
        t = (y[r] - pitch * 0.2) / (pitch * 0.5)
        tex[r, 18:size - 18, :3] = brass * (0.6 + 0.4 * (1 - t)) * shade[r]
    return tex


def write_textures(out):
    for name, rgba in (('envelope', envelope_texture()), ('gondola_side', gondola_art())):
        Image.fromarray((np.clip(rgba[..., :3], 0, 1) * 255).astype(np.uint8)).save(os.path.join(out, name + '.png'))


# --- materials -------------------------------------------------------------------------------------------

def make_materials():
    m = cm.material
    m('envelope', WHITE, rough=0.85, spec=0.15, image=cm.image('envelope', envelope_texture()))
    m('fabric', WHITE, rough=0.85, spec=0.15)
    m('tricolour', WHITE, rough=0.8, spec=0.15, image=cm.image('tricolour', tricolour_texture()))
    m('bronze', (170, 116, 56), metal=1.0, rough=0.32)
    m('brass', (224, 174, 72), metal=1.0, rough=0.2)
    m('iron', (40, 40, 44), metal=0.8, rough=0.5)
    m('cable', (44, 40, 36), rough=0.7)
    m('rope', (150, 122, 82), rough=0.95, spec=0.1)
    m('rope_coil', (196, 172, 120), rough=0.9, image=cm.image('rope_coil', rope_texture()))
    m('gondola_side', (14, 13, 16), rough=0.3, coat=0.4, image=cm.image('gondola_side', gondola_art()))
    m('lacquer', (14, 13, 16), rough=0.3, coat=0.4)
    m('lining', (70, 34, 22), rough=0.4, coat=0.3)
    m('deck', (150, 96, 52), rough=0.55, image=cm.image('deck', deck_texture()))
    m('rail_wood', (52, 26, 16), rough=0.3, coat=0.6)
    m('velvet', PURPLE, rough=0.8)
    m('grille', (20, 18, 18), rough=0.5, image=cm.image('grille', grille_texture()))
    m('prop', (196, 150, 92), rough=0.3, coat=0.8)
    m('bomb', (24, 24, 26), metal=0.6, rough=0.45)
    m('lamp_red', (230, 40, 30), rough=0.1, emit=((255, 60, 40), 3.0))
    m('lamp_green', (40, 210, 90), rough=0.1, emit=((60, 255, 120), 3.0))


# --- the envelope ------------------------------------------------------------------------------------------

def envelope_profile(y):
    """The envelope's radius and the height of its axis at y: straight in the middle, tapering to points at both ends
    along a convex ogive, the axis rising towards each tip."""
    t = (y - ENV_MID_Y) / (ENV_LENGTH / 2)
    a = abs(t)
    if a <= ENV_STRAIGHT:
        return ENV_R * (1 - 0.02 * t * t), ENV_Z
    s = max(0.0, (1 - a) / (1 - ENV_STRAIGHT))     # 1 where the taper starts, 0 at the tip
    r = ENV_R * (1 - 0.02 * ENV_STRAIGHT ** 2) * (1 - (1 - s) ** 1.9) ** 0.85
    z = ENV_Z + ENV_R * ENV_TIP_RISE * (1 - s) ** 2
    return r, z


def envelope_section(r, zc, count=64):
    """A round section, a little flatter underneath; it starts at the top and goes round towards her right."""
    pts = []
    for i in range(count):
        t = math.pi / 2 - 2 * math.pi * i / count
        c, s = math.cos(t), math.sin(t)
        pts.append((r * c, zc + r * s * (1.0 if s > 0 else ENV_BELLY)))
    return pts


def build_envelope():
    m = cm.Mesh()
    ys = []
    n = 120
    for i in range(n + 1):
        # Closer together towards the tips, where the shape turns fastest.
        u = -1 + 2 * i / n
        ys.append(ENV_MID_Y + ENV_LENGTH / 2 * math.sin(math.pi / 2 * u) * 0.9995)
    ys = sorted(set(ys))
    sections = []
    for y in ys:
        r, zc = envelope_profile(y)
        sections.append((y, envelope_section(max(r, 0.02), zc)))
    rings = cm.loft(m, sections, 'envelope', v_scale=1 / ENV_LENGTH)
    # Close each end on its point.
    us = cm.perimeter_uv(sections[0][1])
    for ring, y_tip, v in ((rings[0], ENV_TAIL, 0.0), (rings[-1], ENV_NOSE, 1.0)):
        _, z_tip = envelope_profile(y_tip)
        tip = m.vert((0, y_tip, z_tip))
        k = len(ring)
        for i in range(k):
            j = (i + 1) % k
            m.face([ring[i], ring[j], tip], 'envelope', [(us[i], v), (us[i + 1], v), ((us[i] + us[i + 1]) / 2, v)])
    m.obj('envelope', smooth=40)


def platform_half_width(y):
    p = PLATFORM
    s = (y - p['back']) / (p['front'] - p['back'])
    return p['half_width'] * max(0.0, math.sin(math.pi * s)) ** 0.7


def build_platform():
    """The flat platform under the envelope: doped fabric stretched over a bronze rim and ribs."""
    p = PLATFORM
    m = cm.Mesh()
    n = 40
    ys = [p['back'] + (p['front'] - p['back']) * i / n for i in range(n + 1)]
    top = []
    for y in ys:
        w = max(0.03, platform_half_width(y))
        top.append([m.vert((x, y, p['z'])) for x in (-w, w)])
    for a, b in zip(top, top[1:]):
        m.face([a[0], a[1], b[1], b[0]], 'fabric')
    o = m.obj('platform')
    cm.solidify(o, 0.06)
    m = cm.Mesh()
    rim = [Vector((platform_half_width(y), y, p['z'] - 0.03)) for y in ys]
    rim = rim + [Vector((-v.x, v.y, v.z)) for v in reversed(rim)]
    cm.tube(m, rim, 0.05, 'bronze', seg=8)
    for y in ys[2:-2:3]:
        w = platform_half_width(y)
        cm.tube(m, [Vector((-w, y, p['z'] - 0.05)), Vector((w, y, p['z'] - 0.05))], 0.03, 'bronze', seg=6)
    m.obj('platform_frame', smooth=50)
    # Cables from the envelope's flanks down to the rim, all along.
    m = cm.Mesh()
    for y in ys[2:-2:2]:
        w = platform_half_width(y)
        r, zc = envelope_profile(y)
        for side in (-1, 1):
            a = math.radians(38)
            top_pt = Vector((side * r * math.cos(a), y, zc - r * math.sin(a) * ENV_BELLY))
            cm.tube(m, [top_pt, Vector((side * w, y, p['z']))], 0.022, 'cable', seg=5)
    m.obj('platform_cables')


# --- the frame and its tail ----------------------------------------------------------------------------------

def truss_shape(y):
    """The frame's half width, top and bottom at y: full depth along most of it, drawn to a point at each end."""
    t = TRUSS
    front_taper = max(0.0, min(1.0, (t['front'] - y) / 3.0))
    back_taper = max(0.0, min(1.0, (y - t['back']) / 4.5))
    k = min(front_taper, back_taper)
    k = k * k * (3 - 2 * k)
    w = t['half_width'] * (0.15 + 0.85 * k)
    top = t['tip_z'] + (t['top'] - t['tip_z']) * k
    bottom = t['tip_z'] + (t['bottom'] - t['tip_z']) * k
    return w, top, bottom


def build_truss():
    """A long open girder of bronze tubes: four chords, posts and diagonals in every bay, and wires crossed under it."""
    t = TRUSS
    m = cm.Mesh()
    n = int(round((t['front'] - t['back']) / t['panel']))
    ys = [t['back'] + (t['front'] - t['back']) * i / n for i in range(n + 1)]
    pts = {}
    for y in ys:
        w, top, bottom = truss_shape(y)
        pts[y] = {(sx, sz): Vector((sx * w, y, top if sz > 0 else bottom)) for sx in (-1, 1) for sz in (-1, 1)}
    for key in pts[ys[0]]:
        chord = [pts[y][key] for y in ys]
        cm.tube(m, cm.catmull(chord, per=2), 0.06, 'bronze', seg=8)
    for i, y in enumerate(ys):
        P = pts[y]
        for a, b in (((-1, -1), (-1, 1)), ((1, -1), (1, 1)), ((-1, 1), (1, 1)), ((-1, -1), (1, -1))):
            cm.tube(m, [P[a], P[b]], 0.035, 'bronze', seg=6)
        if i + 1 < len(ys):
            Q = pts[ys[i + 1]]
            flip = i % 2
            for sx in (-1, 1):
                # Warren diagonals down each side.
                cm.tube(m, [P[(sx, 1 if flip else -1)], Q[(sx, -1 if flip else 1)]], 0.03, 'bronze', seg=6)
            # Wires crossed under each bay and over it.
            for sz in (-1, 1):
                cm.tube(m, [P[(-1, sz)], Q[(1, sz)]], 0.012, 'cable', seg=4)
                cm.tube(m, [P[(1, sz)], Q[(-1, sz)]], 0.012, 'cable', seg=4)
    m.obj('truss', smooth=50)


def fin_panel(m, y0, y1, half_h, z, mat, vertical, x=0.0, frame=True):
    """A flat fabric panel on a bronze rim, upright (vertical) or level."""
    if vertical:
        corners = [Vector((x, y0, z - half_h)), Vector((x, y1, z - half_h)), Vector((x, y1, z + half_h)),
                   Vector((x, y0, z + half_h))]
    else:
        corners = [Vector((-half_h, y0, z)), Vector((-half_h, y1, z)), Vector((half_h, y1, z)), Vector((half_h, y0, z))]
    vs = [m.vert(c) for c in corners]
    uvs = [(0, 0), (1, 0), (1, 1), (0, 1)]
    # One face: seen from either side, as Cycles and the game's no-cull layer both draw it.
    m.face(vs, mat, uvs)
    if frame:
        cm.tube(m, corners + [corners[0]], 0.04, 'bronze', seg=6)


def build_tail():
    """The crossed tail at the end of the frame: a fixed fin and stabiliser, and behind them on hinges the rudder and
    elevator, painted in Vulgaria's colours."""
    T = TAIL
    m = cm.Mesh()
    fin_panel(m, T['lead'], T['hinge'], T['height'], T['z'], 'fabric', True)
    fin_panel(m, T['lead'], T['hinge'], T['span'], T['z'], 'fabric', False)
    m.obj('tail_fixed')
    for name, vertical, half in (('rudder', True, T['height']), ('elevator', False, T['span'])):
        m = cm.Mesh()
        # Built about its hinge line, at the origin.
        fin_panel(m, 0.0, T['trail'] - T['hinge'], half, 0.0, 'tricolour', vertical)
        o = m.obj(name, location=(0, T['hinge'], T['z']), part=name)
        o['axis'] = 'z' if vertical else 'x'


# --- the gondola ---------------------------------------------------------------------------------------------

def keel_z(y):
    """The gondola's bottom at y: flat in the middle, sweeping up to each end."""
    a = abs(y)
    if a <= GON_KEEL_HALF:
        return 0.0
    s = min(1.0, (a - GON_KEEL_HALF) / (GON_HALF_LENGTH - GON_KEEL_HALF))
    return GON_END_RISE * (1 - math.sqrt(max(0.0, 1 - s * s)))


def gondola_section(y, inset=0.0, side_pts=10, bilge_pts=5, bottom_pts=6):
    """From the right-hand top edge, down the flared side, round the bilge, across the bottom and up the left."""
    zb = keel_z(y) + inset
    top = GON_TOP
    hw_top = GON_HALF_WIDTH - inset
    hw_bot = GON_BOTTOM_HALF - inset
    rb = min(GON_BILGE, max(0.02, (top - zb) * 0.3))
    right = []
    for i in range(side_pts):
        z = top - (top - zb - rb) * i / side_pts
        right.append((hw_bot + (hw_top - hw_bot) * (z - zb) / max(top - zb, 1e-6), z))
    cx, cz = hw_bot - rb, zb + rb
    for i in range(bilge_pts + 1):
        a = -math.pi / 2 * i / bilge_pts
        right.append((cx + rb * math.cos(a), cz + rb * math.sin(a)))
    bottom = [(cx - 2 * cx * (i + 1) / (bottom_pts + 1), zb) for i in range(bottom_pts)]
    left = [(-x, z) for x, z in reversed(right)]
    return right + bottom + left


def build_gondola():
    """The gondola's hull, painted on its sides and black underneath, lined inside, with a gilt cap along its top
    edge, a planked floor, a rail of dark wood on brass posts and the gate in it for the ladder."""
    n = 48
    ys = [-GON_HALF_LENGTH + 2 * GON_HALF_LENGTH * i / n for i in range(n + 1)]
    m = cm.Mesh()
    sections = [(y, gondola_section(y)) for y in ys]
    cm.loft(m, sections, 'gondola_side', closed=False, cap_start='lacquer', cap_end='lacquer')
    side_slot, under_slot = m.slot('gondola_side'), m.slot('lacquer')
    for f in m.bm.faces:
        f.normal_update()
        nrm = f.normal
        if f.material_index == side_slot and abs(nrm.x) > 0.6:
            right = f.calc_center_median().x > 0
            for loop in f.loops:
                co = loop.vert.co
                u = (co.y + GON_HALF_LENGTH) / (2 * GON_HALF_LENGTH)
                loop[m.uv].uv = (u if right else 1 - u, co.z / GON_TOP)
        else:
            f.material_index = under_slot
    m.obj('gondola_hull', smooth=35)
    # The lining.
    m = cm.Mesh()
    inner = [(y, gondola_section(y, inset=0.06)) for y in ys[1:-1]]
    cm.loft(m, inner, 'lining', closed=False)
    o = m.obj('gondola_lining', recalc=False)
    for p in o.data.polygons:
        p.flip()
    # The cap along the top edge, between hull and lining.
    m = cm.Mesh()
    outline = []
    for y in ys:
        outline.append((y, GON_HALF_WIDTH, GON_HALF_WIDTH - 0.06))
    for side in (-1, 1):
        prev = None
        for y, a, b in outline:
            v = (m.vert((side * a, y, GON_TOP)), m.vert((side * b, y, GON_TOP)))
            if prev:
                m.face([prev[0], v[0], v[1], prev[1]] if side > 0 else [prev[1], v[1], v[0], prev[0]], 'brass')
            prev = v
    for y in (ys[0], ys[-1]):
        cm.add_box(m, (0, y, GON_TOP + 0.0), (2 * GON_HALF_WIDTH, 0.08, 0.04), 'brass')
    m.obj('gondola_cap')
    # The floor.
    m = cm.Mesh()
    half = GON_KEEL_HALF + 0.35
    cm.add_box(m, (0, 0, GON_FLOOR - 0.03), (2 * (GON_BOTTOM_HALF - 0.02), 2 * half, 0.06), 'deck', uv_scale=0.5)
    m.obj('gondola_floor')
    # The rail: posts round the top, a dark wooden rail on them, and the gap for the ladder.
    m = cm.Mesh()
    hl, hw = GON_HALF_LENGTH - 0.08, GON_HALF_WIDTH - 0.04
    path = []
    for i in range(64):
        a = 2 * math.pi * i / 64
        c, s = math.cos(a), math.sin(a)
        # A rectangle with rounded corners.
        path.append(Vector((hw * math.copysign(abs(c) ** 0.18, c), hl * math.copysign(abs(s) ** 0.18, s), RAIL_Z)))
    gate = [i for i, p in enumerate(path) if (p.x > 0 and abs(p.y - GATE_Y) < 0.35) or (p.y > 0 and abs(p.x) < 0.55)]
    runs, run = [], []
    for i in range(len(path) + 1):
        idx = i % len(path)
        if idx in gate:
            if run:
                runs.append(run)
                run = []
        else:
            run.append(path[idx])
    if run:
        runs.append(run)
    for r in runs:
        cm.tube(m, r, 0.05, 'rail_wood', seg=8)
    for i, p in enumerate(path):
        if i % 2 == 0 and i not in gate:
            cm.tube(m, [Vector((p.x, p.y, GON_TOP)), Vector((p.x, p.y, RAIL_Z - 0.04))], 0.022, 'brass', seg=6)
            cm.ellipsoid(m, (p.x, p.y, RAIL_Z), (0.045, 0.045, 0.045), 'brass', seg=8, rings=5)
    m.obj('gondola_rail', smooth=50)


def build_hangers():
    """Bronze tubes from the gondola's top edge up to the frame, in triangles as the film's still shows them, and
    wires from her ends out along the frame."""
    m = cm.Mesh()
    t = TRUSS
    rail = [-3.0, -1.2, 0.6, 2.4]
    chord = [-2.1, -0.3, 1.5, 3.3]
    for side in (-1, 1):
        for i, y in enumerate(rail):
            a = Vector((side * (GON_HALF_WIDTH - 0.05), y, GON_TOP + 0.02))
            for yc in (chord[i - 1] if i > 0 else -3.9, chord[i]):
                w, _, bottom = truss_shape(yc)
                cm.tube(m, [a, Vector((side * w, yc, bottom))], 0.04, 'bronze', seg=6)
        for yg, yt in ((-GON_HALF_LENGTH + 0.1, -8.0), (GON_HALF_LENGTH - 0.1, 6.0)):
            w, _, bottom = truss_shape(yt)
            cm.tube(m, [Vector((side * GON_HALF_WIDTH, yg, GON_TOP)), Vector((side * w, yt, bottom))], 0.015,
                    'cable', seg=4)
    m.obj('hangers', smooth=50)


def build_engine_house():
    """At the stern: a house with louvred sides and a black roof over the engine, an exhaust out of the roof, and
    shafts out to the two propellers on struts either side."""
    e = ENGINE_HOUSE
    m = cm.Mesh()
    hw, y0, y1, base, roof = e['half_width'], e['back'], e['front'], e['base'], e['roof']
    cy, ly = (y0 + y1) / 2, y1 - y0
    h = roof - base
    for side in (-1, 1):
        cm.add_box(m, (side * hw, cy, base + h / 2), (0.06, ly, h), 'grille', uv_scale=1 / ly)
    for y in (y0, y1):
        cm.add_box(m, (0, y, base + h / 2), (2 * hw, 0.06, h), 'grille', uv_scale=1 / (2 * hw))
    cm.add_box(m, (0, cy, roof + 0.04), (2 * hw + 0.16, ly + 0.16, 0.08), 'lacquer')
    for dx in (-hw, hw):
        for dy in (y0, y1):
            cm.add_box(m, (dx, dy, base + h / 2 + 0.04), (0.1, 0.1, h + 0.1), 'brass')
    # The exhaust.
    pipe = [Vector((0.6, y0 + 0.3, roof)), Vector((0.6, y0 + 0.3, roof + 0.5)), Vector((0.6, y0 - 0.05, roof + 0.75))]
    cm.tube(m, cm.catmull(pipe, per=6), 0.07, 'brass', seg=10)
    cm.empty('exhaust', (0.6, y0 - 0.1, roof + 0.78))
    # Shafts and struts out to each propeller.
    P = PROP
    for side in (-1, 1):
        hub = Vector((side * P['x'], P['y'] + 0.25, P['z']))
        cm.tube(m, [Vector((side * hw, P['y'] + 0.25, roof)), Vector((side * hw, P['y'] + 0.25, P['z'])), hub],
                0.045, 'brass', seg=8)
        cm.lathe(m, [(0.0, -0.25), (0.13, -0.25), (0.13, 0.2), (0.0, 0.2)], lambda k: 'brass', axis='y', seg=14,
                 origin=tuple(hub))
        for a in (Vector((side * GON_HALF_WIDTH, -2.3, 0.9)), Vector((side * TRUSS['half_width'], -2.6, TRUSS['bottom'])),
                  Vector((side * GON_HALF_WIDTH, -3.3, 1.0))):
            cm.tube(m, [a, hub], 0.035, 'bronze', seg=6)
    m.obj('engine_house', smooth=40)


def blade(m, length, root, tip, pitch0, pitch1, turn, thick=0.05):
    """One wooden propeller blade out from the hub, turned `turn` radians about the shaft (y): a paddle, widest two
    thirds out, its pitch easing off from pitch0 at the root to pitch1 at the tip (degrees from the plane of the
    disc)."""
    n = 10
    rot = Matrix.Rotation(turn, 3, 'Y')
    rings = []
    for i in range(n + 1):
        t = i / n
        r = root + (length - root) * t
        chord = tip + (0.34 - tip) * math.sin(math.pi * min(1.0, t * 1.3)) if t < 0.95 else tip
        ang = math.radians(pitch0 + (pitch1 - pitch0) * t)
        along = Vector((0, math.sin(ang), math.cos(ang)))       # the chord: round the disc, tipped towards the shaft
        across = Vector((0, math.cos(ang), -math.sin(ang)))
        ring = []
        for c, d in ((-chord / 2, -thick / 2), (chord / 2, -thick / 2), (chord / 2, thick / 2), (-chord / 2, thick / 2)):
            ring.append(m.vert(rot @ (Vector((r, 0, 0)) + along * c + across * d)))
        rings.append(ring)
    for a, b in zip(rings, rings[1:]):
        for k in range(4):
            j = (k + 1) % 4
            m.face([a[k], a[j], b[j], b[k]], 'prop')
    m.face(list(reversed(rings[0])), 'prop')
    m.face(rings[-1], 'prop')


def build_props():
    """The two wooden propellers, each turning about its shaft along her (y) on its own hub; the left one is the
    right one mirrored, so they turn opposite ways."""
    P = PROP
    for side, name in ((1, 'prop_r'), (-1, 'prop_l')):
        m = cm.Mesh()
        for k in (0, 1):
            blade(m, P['r'], 0.12, 0.12, 62, 18, math.pi * k)
        cm.lathe(m, [(0.0, -0.18), (0.15, -0.15), (0.17, 0.0), (0.15, 0.12), (0.0, 0.16)], lambda k: 'brass', axis='y',
                 seg=16)
        o = m.obj(name, location=(side * P['x'], P['y'], P['z']), part=name, smooth=30)
        if side < 0:
            o.scale.x = -1


def build_helm():
    """The ship's wheel at the bow, where the pilot stands: eight spokes with turned handles round a brass hub, on a
    brass column. It turns as she is steered."""
    m = cm.Mesh()
    r = 0.42
    ring = [Vector((math.cos(a) * r, 0, math.sin(a) * r)) for a in np.linspace(0, 2 * math.pi, 41)]
    cm.tube(m, ring, 0.035, 'rail_wood', seg=8)
    for k in range(8):
        a = 2 * math.pi * k / 8
        d = Vector((math.cos(a), 0, math.sin(a)))
        cm.tube(m, [d * 0.06, d * (r + 0.16)], 0.022, 'rail_wood', seg=6)
        cm.ellipsoid(m, tuple(d * (r + 0.17)), (0.03, 0.03, 0.03), 'rail_wood', seg=8, rings=5)
    cm.lathe(m, [(0.0, -0.08), (0.08, -0.08), (0.09, 0.05), (0.0, 0.06)], lambda k: 'brass', axis='y', seg=16)
    m.obj('helm', location=tuple(HELM), part='helm', smooth=40)
    m = cm.Mesh()
    cm.lathe(m, [(0.0, GON_FLOOR), (0.16, GON_FLOOR), (0.07, GON_FLOOR + 0.1), (0.05, HELM.z - 0.1), (0.09, HELM.z - 0.02),
                 (0.0, HELM.z)], lambda k: 'brass', axis='z', seg=16, origin=(HELM.x, HELM.y + 0.15, 0))
    m.obj('helm_column', smooth=40)
    cm.empty('seat_0', (SEATS[0][0], SEATS[0][1], GON_FLOOR))


def build_benches():
    """Two benches across the gondola with purple velvet cushions and backs: three on the front one, two on the
    back one either side of the hatch the hook's rope goes down through."""
    m = cm.Mesh()
    inner = GON_BOTTOM_HALF - 0.04
    for y in BENCHES:
        cm.add_box(m, (0, y, GON_FLOOR + 0.2), (2 * inner, 0.42, 0.4), 'rail_wood')
        cm.add_box(m, (0, y + 0.02, GON_FLOOR + 0.44), (2 * inner - 0.08, 0.4, 0.08), 'velvet')
        cm.add_box(m, (0, y - 0.22, GON_FLOOR + 0.75), (2 * inner, 0.06, 0.62), 'rail_wood')
        cm.add_box(m, (0, y - 0.18, GON_FLOOR + 0.78), (2 * inner - 0.1, 0.04, 0.5), 'velvet')
    # The hatch, and a brass fairlead under the keel where the rope comes out.
    cm.add_box(m, (0, LINE_OUT.y, GON_FLOOR + 0.005), (0.5, 0.5, 0.02), 'brass')
    cm.lathe(m, [(0.06, -0.04), (0.12, -0.04), (0.12, 0.02), (0.06, 0.02)], lambda k: 'brass', axis='z', seg=16,
             origin=tuple(LINE_OUT))
    m.obj('benches')
    for i, (x, y) in enumerate(SEATS[1:], start=1):
        cm.empty('seat_%d' % i, (x, y, GON_FLOOR + 0.42))
    cm.empty('line', tuple(LINE_OUT))


def build_drum():
    """The rope drum at the bow, half out over the end, wound with the hook's rope: brass flanges and caps, and a
    crank either side. It turns as the rope goes out and in."""
    D = DRUM
    m = cm.Mesh()
    length = D['front'] - D['back']
    r = D['r']
    cm.lathe(m, [(0.0, 0.0), (r + 0.1, 0.0), (r + 0.1, 0.05), (r, 0.05), (r, length - 0.05), (r + 0.1, length - 0.05),
                 (r + 0.1, length), (0.12, length + 0.05), (0.0, length + 0.1)],
             lambda k: 'rope_coil' if k == 3 else 'brass', axis='y', seg=24)
    cm.tube(m, [Vector((0, 0.02, 0)), Vector((0, -0.2, 0))], 0.045, 'iron', seg=8)
    for up in (1, -1):
        cm.tube(m, [Vector((0, -0.18, 0)), Vector((0, -0.18, up * 0.3)), Vector((0, -0.36, up * 0.3))], 0.022, 'iron',
                seg=6)
    o = m.obj('drum', location=(0, D['back'], D['z']), part='drum', smooth=40)
    o['axis'] = 'y'
    m = cm.Mesh()
    for y in (D['back'] + 0.15, GON_HALF_LENGTH - 0.12):
        for side in (-1, 1):
            cm.add_box(m, (side * 0.48, y, (GON_TOP + D['z']) / 2), (0.06, 0.12, D['z'] - GON_TOP + 0.1), 'brass')
        cm.add_box(m, (0, y, GON_TOP + 0.02), (1.0, 0.14, 0.04), 'brass')
    m.obj('drum_brackets')


def build_lamps():
    """Navigation lanterns on the bow corners, red to port and green to starboard."""
    for side, mat, name in ((-1, 'lamp_red', 'lamp_l'), (1, 'lamp_green', 'lamp_r')):
        m = cm.Mesh()
        c = Vector((side * (GON_HALF_WIDTH + 0.08), GON_HALF_LENGTH - 0.45, RAIL_Z + 0.12))
        cm.lathe(m, [(0.0, -0.16), (0.1, -0.16), (0.1, -0.12), (0.08, -0.12), (0.08, 0.1), (0.11, 0.1), (0.04, 0.2),
                     (0.0, 0.22)], lambda k: mat if k == 3 else 'brass', axis='z', seg=12, origin=tuple(c))
        cm.add_box(m, (side * (GON_HALF_WIDTH + 0.02), c.y, RAIL_Z), (0.12, 0.04, 0.04), 'brass')
        m.obj(name + '_lantern', smooth=40)
        cm.empty(name, tuple(c))


def build_bombs():
    """Six bombs stood nose down in a rack across the gondola ahead of the engine house: black iron teardrops with
    four fins and a brass fuse on the nose, smaller than a block of TNT. Each is its own part, so the game can take
    them from the rack one by one."""
    m = cm.Mesh()
    inner = GON_BOTTOM_HALF - 0.05
    cm.add_box(m, (0, BOMB_RACK_Y, GON_FLOOR + 0.45), (2 * inner, 0.3, 0.05), 'rail_wood')
    cm.add_box(m, (0, BOMB_RACK_Y, GON_FLOOR + 0.12), (2 * inner, 0.3, 0.05), 'rail_wood')
    for side in (-1, 1):
        cm.add_box(m, (side * (inner - 0.03), BOMB_RACK_Y, GON_FLOOR + 0.27), (0.06, 0.3, 0.54), 'rail_wood')
    m.obj('bomb_rack')
    for i in range(6):
        x = -0.85 + 0.34 * i
        m = cm.Mesh()
        # Built nose down about its middle.
        cm.lathe(m, [(0.0, -0.3), (0.035, -0.29), (0.05, -0.25), (0.11, -0.16), (0.13, -0.05), (0.12, 0.06), (0.08, 0.17),
                     (0.045, 0.24), (0.045, 0.3), (0.0, 0.3)],
                 lambda k: 'brass' if k < 2 else 'bomb', axis='z', seg=16)
        for k in range(4):
            a = math.pi / 4 + k * math.pi / 2
            cm.add_box(m, (math.cos(a) * 0.09, math.sin(a) * 0.09, 0.24), (0.1, 0.012, 0.14), 'bomb',
                       rot=Matrix.Rotation(a, 4, 'Z'))
        cm.tube(m, [Vector((0, 0, 0.3)), Vector((0, 0, 0.36))], 0.014, 'brass', seg=6)
        m.obj('bomb_%d' % i, location=(x, BOMB_RACK_Y, GON_FLOOR + 0.38), part='bomb_%d' % i, smooth=40)


def build_hook():
    """The grabbing hook: an iron pulley block with a brass sheave, and under it a great forged hook with a safety
    latch. Its origin is the top of the block, where the rope ties on."""
    m = cm.Mesh()
    cm.add_box(m, (0, 0, -0.22), (0.1, 0.34, 0.4), 'iron')
    for side in (-1, 1):
        cm.add_box(m, (side * 0.08, 0, -0.22), (0.03, 0.4, 0.46), 'iron')
    cm.lathe(m, [(0.0, -0.05), (0.15, -0.05), (0.15, 0.05), (0.0, 0.05)], lambda k: 'brass', axis='x', seg=16,
             origin=(0, 0, -0.18))
    cm.tube(m, [Vector((0, 0, 0.02)), Vector((0, 0, -0.06))], 0.05, 'iron', seg=8)
    # The hook: down from the block, round the bowl and up to a point.
    path = [Vector((0, 0, -0.42)), Vector((0, 0, -0.7)), Vector((0, 0.04, -0.95)), Vector((0, 0.22, -1.08)),
            Vector((0, 0.38, -0.98)), Vector((0, 0.42, -0.8)), Vector((0, 0.36, -0.68))]
    cm.tube(m, cm.catmull(path, per=6), 0.075, 'iron', seg=10, flare=lambda t: 1.15 - 0.85 * t * t)
    cm.tube(m, [Vector((0, 0.02, -0.6)), Vector((0, 0.34, -0.68))], 0.015, 'iron', seg=5)
    m.obj('hook', part='hook', smooth=40)


def build_ladder(length=5.0):
    """The rope ladder at the gate in her rail: two ropes and wooden rungs, hung from its top so the game can let it
    down and draw it up."""
    m = cm.Mesh()
    for side in (-0.22, 0.22):
        cm.tube(m, [Vector((0, side, 0)), Vector((0, side, -length))], 0.025, 'rope', seg=6)
    k = 0.32
    z = -0.25
    while z > -length + 0.1:
        cm.add_box(m, (0, 0, z), (0.07, 0.5, 0.05), 'rail_wood')
        z -= k
    o = m.obj('ladder', location=(GON_HALF_WIDTH + 0.08, GATE_Y, GON_TOP), part='ladder', smooth=40)
    o['length'] = length
    cm.empty('ladder_top', (GON_HALF_WIDTH + 0.08, GATE_Y, GON_TOP))


def build_rope():
    """The hook's rope, a unit long: the renders stretch it down to the hook (the game draws its own)."""
    m = cm.Mesh()
    cm.tube(m, [Vector((0, 0, 0)), Vector((0, 0, -1))], 0.03, 'rope', seg=8)
    m.obj('rope', location=tuple(LINE_OUT), part='rope')


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    make_materials()
    build_envelope()
    build_platform()
    build_truss()
    build_tail()
    build_gondola()
    build_hangers()
    build_engine_house()
    build_props()
    build_helm()
    build_benches()
    build_drum()
    build_lamps()
    build_bombs()
    build_hook()
    build_ladder()
    build_rope()


# --- poses -------------------------------------------------------------------------------------------------

def pose(spin=0.0, steer=0.0, climb=0.0, hook=None, ladder=0.0, bombs=6):
    """What the game's animation does: the propellers turned by spin (radians), the rudder and helm by steer and the
    elevator by climb (-1 to 1), the hook let down that many blocks below the keel (None: drawn up under her), the
    ladder that far down (0 to 1) and that many bombs left in the rack."""
    drop = 0.12 if hook is None else hook
    for o in bpy.data.objects:
        part = o.get('part', '')
        if part in ('prop_r', 'prop_l'):
            o.rotation_euler = (0, spin * (1 if part == 'prop_r' else -1), 0)
        elif part == 'rudder':
            o.rotation_euler = (0, 0, math.radians(-25) * steer)
        elif part == 'elevator':
            o.rotation_euler = (math.radians(20) * climb, 0, 0)
        elif part == 'helm':
            o.rotation_euler = (0, math.radians(120) * steer, 0)
        elif part == 'drum':
            o.rotation_euler = (0, drop * 2.4, 0)
        elif part == 'hook':
            o.location = LINE_OUT + Vector((0, 0, -drop))
        elif part == 'rope':
            o.scale = (1, 1, max(drop, 0.01))
        elif part == 'ladder':
            o.hide_render = o.hide_viewport = ladder <= 0.0
            o.scale = (1, 1, max(ladder, 0.001))
        elif part.startswith('bomb_'):
            o.hide_render = o.hide_viewport = int(part[5:]) >= bombs


# --- output ------------------------------------------------------------------------------------------------

def render(out, name, cam_loc, look_at, lens=35, lift=0.0, size=(1600, 900),
           samples=int(os.environ.get('AIRSHIP_SAMPLES', '64'))):
    """Renders her from cam_loc towards look_at, raised lift blocks off the ground."""
    scene = bpy.context.scene
    cm.render_scene_setup()
    scene.cycles.samples = samples
    scene.render.resolution_x, scene.render.resolution_y = size
    ground = bpy.data.objects['ground']
    ground.scale = (12, 12, 1)
    grass = cm.MATS['grass'].node_tree.nodes['Principled BSDF']
    grass.inputs['Base Color'].default_value = cm.srgb((74, 84, 62))
    bpy.data.objects['water'].hide_render = True
    cam = scene.camera
    cam.data.clip_end = 2000
    for o in bpy.data.objects:
        if o.get('part') is not None and o.parent is None:
            o.delta_location = (0, 0, lift)
    cam.location = Vector(cam_loc)
    cam.rotation_euler = (Vector(look_at) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = lens
    scene.render.filepath = os.path.join(out, name + '.png')
    bpy.ops.render.render(write_still=True)
    for o in bpy.data.objects:
        o.delta_location = (0, 0, 0)


SHOTS = [
    # name, camera, look at, lens, lift, pose
    ('side', (58, -3, 16), (0, -3, 15), 34, 9.0, dict(spin=0.5, hook=None)),
    ('below', (-14, 26, 0.4), (0, -1, 13), 24, 9.0, dict(spin=0.9)),
    ('three_quarter', (-34, 30, 26), (0, -4, 13), 30, 9.0, dict(spin=1.3, steer=0.4)),
    ('front', (6, 52, 12), (0, 0, 15), 32, 9.0, dict(spin=0.2)),
    ('rear', (-16, -46, 18), (0, -10, 13), 30, 9.0, dict(spin=0.8, steer=-0.5, climb=0.4)),
    ('gondola', (8.2, 3.2, 11.6), (0, 0.2, 10.2), 32, 9.0, dict(spin=0.3)),
    ('gondola_bow', (5.2, 8.6, 11.0), (0, 2.6, 10.3), 30, 9.0, dict(spin=0.3)),
    ('deck', (4.6, -6.4, 13.6), (0, 0.6, 9.8), 26, 9.0, dict(spin=0.6)),
    ('hook_ladder', (11, 9, 4.5), (0, 0, 5.5), 30, 9.0, dict(spin=0.4, hook=6.0, ladder=1.0, bombs=4)),
    ('landed', (16, 18, 3.0), (0, -1, 4.5), 28, 0.0, dict(spin=0.0)),
]


def main():
    args = sys.argv[1:]
    if '--out' not in args:
        print(__doc__)
        return
    out = args[args.index('--out') + 1]
    os.makedirs(out, exist_ok=True)
    if '--textures' in args:
        write_textures(out)
    build()
    pose()
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, 'airship.blend'), check_existing=False)
    if '--renders' in args:
        only = args[args.index('--only') + 1].split(',') if '--only' in args else None
        for name, cam, at, lens, lift, p in SHOTS:
            if only and name not in only:
                continue
            pose(**p)
            render(out, name, cam, at, lens, lift)
        pose()


if __name__ == '__main__':
    main()
