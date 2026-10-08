"""Builds Baron Bomburst's Vulgarian airship, from Chitty Chitty Bang Bang, in Blender.

Needs Blender's Python module (pip install "bpy==4.5.*", on Python 3.11). Run from the repository root:

    python tools/airship_model.py --out DIR              # writes DIR/airship.blend
    python tools/airship_model.py --out DIR --renders    # also renders her flying, from the ground and close up
    python tools/airship_model.py --out DIR --textures   # also writes the painted textures as PNGs

Blender units are blocks (metres), as for Chitty (tools/chitty_model.py, whose helpers this uses). The airship faces
+Y with +X on her right and +Z up. The origin is under the middle of the gondola's keel: she lands on her gondola, and
that is where she is boarded.

The film's airship was a real one, built to fly in 1967: a copy of a 1904 Lebaudy, 112 feet long and 30 across (34
and 9 blocks here). Her envelope is short and deep, pointed at both ends with the tips set a little high. Its ends are
banded in Vulgaria's colours, purple, black and purple out to a black tip, each band parted by a white stripe, and on
each flank are Vulgaria's arms: a black griffin with its wings raised, standing behind a gold shield.

Under the envelope is a flat platform of dark lattice, and under that, about two fifths of the way back from the nose,
hangs the gondola: small (four blocks long, standing room for eight), a black box carved and gilded with the Baron's
B at the bow, a raked bow, a grey engine section with louvres at the stern and a heavy black rail along the top. Two
propellers stand out behind its stern corners on shafts, each turned by a belt from the engine over a spoked pulley.
A coil of rope hangs over the side at the bow, and a rope ladder from the middle. A narrow girder runs back from over
the gondola to the tail, which hangs under the back of the envelope: a white tailplane with Vulgaria's stripes on
its elevator, and dark triangular fins above and below it ending in the rudder.

Parts that move in the game are objects of their own with their origins on their pivots: the two propellers, the
rudder and elevator, the wheel, the hook, the ladder (one rung, stacked by the game), its roll, the coil of rope and
each of the six bombs. Empties mark the eight places to stand, where the hook's rope comes out under the gondola,
the top of the ladder and the exhaust.
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
GONDOLA_AT = 0.39       # how far back from the nose the gondola hangs, as a fraction of the length
ENV_BELLY = 0.93        # the lower half is a little flatter than the top, over the platform
ENV_STRAIGHT = 0.12     # the straight middle, as a fraction of the half length; the rest tapers to the tips
ENV_TIP_RISE = 0.30     # how far above the axis the tips are, in radii
ENV_NOSE = GONDOLA_AT * ENV_LENGTH
ENV_TAIL = ENV_NOSE - ENV_LENGTH
ENV_MID_Y = (ENV_NOSE + ENV_TAIL) / 2

# The platform under the envelope: a flat frame of dark lattice, pointed at both ends.
PLATFORM = dict(front=3.6, back=-12.6, half_width=3.0, z=3.25)
ENV_Z = PLATFORM['z'] + 0.1 + ENV_R * ENV_BELLY     # the envelope's axis

# The gondola: a black box with a raked bow, the engine section at the stern. Along y its top runs from STERN to BOW;
# its bottom from the foot of the bow (BOW_FOOT) back to where the engine section's floor slopes up (ENGINE_FRONT).
GON_BOW = 2.1
GON_STERN = -2.1
GON_BOW_FOOT = 1.55
GON_ENGINE_FRONT = -1.0
GON_STERN_RISE = 0.45       # how high the bottom has sloped up at the stern
GON_HALF_WIDTH = 0.95
GON_TOP = 1.0
GON_FLOOR = 0.18
BEAM = 0.14                 # the heavy black rail along the top, square
BEAM_OVER = 0.35            # how far it runs on past each end
BAR_Z = 2.3                 # the long bars over the crew's heads, from which the gondola hangs

# The narrow girder from over the gondola back to the tail, and the tail itself.
GIRDER = dict(front=2.0, back=-15.0, half_width=0.4, depth=0.7, top=PLATFORM['z'] - 0.1, tail_top=2.35)
TAIL = dict(lead=-13.8, hinge=-18.6, trail=-19.9, z=1.85, up=2.3, down=2.1, span=2.8)

# The two propellers behind the stern corners, each on a shaft run aft from its bearings, turned by a belt over a
# spoked pulley.
PROP = dict(x=1.85, y=-2.95, z=2.0, r=1.05, bearing_y=-1.3, pulley_y=-2.35, pulley_r=0.36)

WHEEL = Vector((0.0, 1.72, 1.05))
LINE_OUT = Vector((0.0, 0.25, 0.0))    # where the hook's rope comes out under the keel
LINE_MAX = 32.0             # how far the hook's rope and the rope ladder both let down
LADDER_PITCH = 0.32         # from one rung of the ladder to the next
ROPE_SIDE = -1              # the coil and the ladder hang over her left side, as the film shows them
GATE_Y = -0.15              # where the ladder hangs, from the middle of that side
LADDER_TOP = Vector((ROPE_SIDE * (GON_HALF_WIDTH + 0.1), GATE_Y, GON_TOP + BEAM))
COIL_Y = 1.05               # the coil of rope over that side, by the bow
BOMB_RACK_Y = GON_ENGINE_FRONT + 0.16

# Where everyone stands: the pilot at the wheel, then two, two and three across.
PLACES = [(0.0, 1.3), (-0.45, 0.75), (0.45, 0.75), (-0.45, 0.2), (0.45, 0.2), (-0.55, -0.4), (0.0, -0.4),
          (0.55, -0.4)]

EMBLEM_HEIGHT = 5.4         # Vulgaria's arms on the envelope's flank
EMBLEM_Y = -1.6

# Vulgaria's colours.
BLACK = (22, 20, 24)
PURPLE = (92, 36, 104)
WHITE = (236, 232, 218)
GOLD = (214, 166, 72)
SHIELD_GOLD = (222, 170, 40)


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


# The arms' colours, as the frame shows them.
ARMS_BLACK = (22, 20, 24, 255)
ARMS_WING = (70, 68, 72, 255)
ARMS_FEATHER = (112, 110, 114, 255)
ARMS_GOLD = (206, 160, 38, 255)
ARMS_GOLD_LIGHT = (220, 178, 58, 255)
ARMS_SABLE = (58, 54, 46, 255)
ARMS_WHITE = (236, 232, 218, 255)


def emblem(scale=2, box=(96, 186, 676, 1014)):
    """Vulgaria's arms as the airship wears them, redrawn from a frame of the film: a black griffin rearing up behind a
    gold and black quartered shield, facing left. Its eagle's head is turned left with its beak open and a crest swept
    back; one foreleg is raised high with its talons spread, the other reaches over the shield's top to hook its corner;
    its great dark grey wing spreads up to the right, its two longest feathers furthest; a lion's hind leg stands below
    and its tail curls up to a tuft on the right. The shapes are placed on the frame's own grid (the crop enlarged
    twelve times), and drawn at `scale` times that. RGBA."""
    x0, y0, x1, y1 = box
    BLACK, WING, FEATHER, SABLE = ARMS_BLACK, ARMS_WING, ARMS_FEATHER, ARMS_SABLE
    GOLD, GOLD_LIGHT, WHITE = ARMS_GOLD, ARMS_GOLD_LIGHT, ARMS_WHITE
    W, H = (x1 - x0) * scale, (y1 - y0) * scale
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def P(pts):
        return [((x - x0) * scale, (y - y0) * scale) for x, y in pts]

    def poly(pts, fill, smooth=True, outline=None, width=0):
        pts = spline(pts) if smooth else pts
        d.polygon(P(pts), fill=fill)
        if outline:
            d.line(P(pts + [pts[0]]), fill=outline, width=width * scale, joint='curve')

    def line(pts, fill, w):
        d.line(P(pts), fill=fill, width=int(w * scale), joint='curve')

    # The wing, spread up to the right behind the body: a great dark grey fan from the neck down to the haunch, its
    # two longest feathers reaching furthest, the feathers parted by black and paler at their tips.
    wing = [(372, 400), (396, 352), (430, 330), (476, 318), (520, 308), (556, 298), (586, 292), (598, 312), (612, 318),
            (640, 300), (664, 288), (662, 318), (648, 356), (632, 396), (610, 444), (580, 500), (548, 540), (514, 572),
            (488, 604), (470, 560), (440, 500), (404, 444)]
    poly(wing, WING, outline=BLACK, width=3)
    root = (420, 470)
    for tip in ((586, 292), (664, 288), (648, 356), (624, 420), (590, 486), (548, 540)):
        line([((root[0] * 3 + tip[0]) / 4, (root[1] * 3 + tip[1]) / 4), ((root[0] + tip[0] * 7) / 8, (root[1] + tip[1] * 7) / 8)],
             BLACK, 4)
    for tip in ((600, 300), (656, 300), (640, 380), (606, 452), (570, 512)):
        line([((root[0] + tip[0] * 2) / 3, (root[1] + tip[1] * 2) / 3), ((root[0] + tip[0] * 9) / 10, (root[1] + tip[1] * 9) / 10)],
             FEATHER, 6)
    line([(410, 352), (470, 330), (540, 312)], FEATHER, 4)
    # The tail: out of the haunch, round to the right and up to a tuft.
    tail = spline([(490, 800), (536, 814), (568, 798), (580, 756), (578, 712), (580, 676)], 10, False)
    for i in range(len(tail) - 1):
        w = 20 - 8 * i / len(tail)
        line([tail[i], tail[i + 1]], BLACK, w)
    poly([(566, 690), (550, 642), (558, 598), (586, 566), (596, 588), (604, 612), (600, 648), (592, 686)], BLACK)
    poly([(586, 566), (574, 540), (596, 556)], BLACK, smooth=False)
    # The hind leg, standing: haunch, hock, and the paw turned forward.
    leg = [(470, 640), (486, 700), (494, 760), (502, 820), (516, 868), (522, 918), (518, 958), (496, 990), (462, 999),
           (424, 988), (410, 962), (420, 940), (450, 934), (456, 912), (436, 888), (424, 846), (422, 800), (430, 760),
           (424, 736), (406, 740), (404, 700), (430, 664)]
    poly(leg, BLACK)
    for cx in (414, 430, 446):
        poly([(cx, 960), (cx - 16, 966), (cx - 2, 976)], BLACK, smooth=False)
    # The body, rearing: from the neck and the raised foreleg down behind the shield to the haunch, the wing on its back.
    body = [(250, 404), (300, 380), (340, 374), (372, 394), (402, 442), (440, 500), (476, 556), (498, 612), (492, 660),
            (470, 700), (440, 730), (400, 742), (392, 592), (240, 588), (238, 562), (214, 532), (192, 506), (184, 472),
            (184, 432), (196, 404), (232, 402)]
    poly(body, BLACK)
    # The gap under the head, between the neck and the wing's root, where the envelope shows through.
    hole = spline([(338, 404), (356, 392), (384, 396), (400, 418), (394, 444), (370, 454), (346, 446), (334, 426)])
    d.polygon(P(hole), fill=(0, 0, 0, 0))
    # The raised foreleg, an eagle's, straight up the left side, its talons spread at the top.
    poly([(186, 472), (180, 420), (188, 370), (198, 312), (204, 266), (212, 240), (230, 228), (244, 238), (228, 258),
          (218, 292), (214, 332), (220, 370), (240, 398), (252, 406)], BLACK)
    for a, ln in ((-150, 34), (-110, 36), (-70, 32), (150, 22)):
        r = math.radians(a)
        base = (224, 236)
        tip = (base[0] + math.cos(r) * ln, base[1] + math.sin(r) * ln)
        n = (-math.sin(r) * 6, math.cos(r) * 6)
        hook = (tip[0] + math.cos(r + 1.4) * 8, tip[1] + math.sin(r + 1.4) * 8)
        poly([(base[0] + n[0], base[1] + n[1]), (base[0] - n[0], base[1] - n[1]), tip, hook], BLACK, smooth=False)
    # The neck and the eagle's head, turned left: tall and square-browed, a long beak a little open and hooked at its
    # end, one crest of feathers swept back and up behind.
    poly([(342, 384), (360, 350), (372, 318), (376, 286), (380, 254), (396, 232), (424, 226), (448, 236), (460, 262),
          (458, 300), (448, 330), (430, 350), (408, 372), (396, 390)], BLACK)
    poly([(382, 276), (356, 276), (330, 280), (310, 288), (300, 302), (302, 316), (312, 306), (330, 298), (352, 296),
          (380, 298)], BLACK, smooth=False)
    poly([(378, 306), (350, 308), (328, 314), (342, 320), (366, 318), (382, 316)], BLACK, smooth=False)
    poly([(430, 232), (448, 212), (470, 196), (464, 222), (456, 246)], BLACK, smooth=False)
    d.ellipse(P([(398, 262)]) + P([(408, 272)]), fill=(150, 146, 140, 255))
    # The shield, quartered gold and black, edged black, its point a little right of its middle.
    shield = [(118, 588), (388, 588), (388, 700), (386, 790), (378, 868), (364, 930), (334, 970), (286, 992),
              (238, 966), (190, 926), (150, 868), (126, 790), (118, 690)]
    split_x, split_y = 252, 738
    shield_px = P(spline(shield[2:] + [shield[0], shield[1]], 6, True))
    mask = Image.new('L', (W, H), 0)
    ImageDraw.Draw(mask).polygon(shield_px, fill=255)
    quarters = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    q = ImageDraw.Draw(quarters)
    sx, sy = (split_x - x0) * scale, (split_y - y0) * scale
    q.rectangle([0, 0, sx, sy], fill=GOLD)
    q.rectangle([sx, 0, W, sy], fill=SABLE)
    q.rectangle([0, sy, sx, H], fill=SABLE)
    q.rectangle([sx, sy, W, H], fill=GOLD)
    q.polygon(P([(130, 600), (240, 600), (240, 640), (140, 700)]), fill=GOLD_LIGHT)
    img.paste(quarters, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.line(shield_px + [shield_px[0]], fill=BLACK, width=5 * scale, joint='curve')
    # The other foreleg over the shield's top, its talons hooked over the top left corner.
    poly([(252, 548), (210, 560), (172, 566), (140, 566), (122, 574), (122, 590), (144, 594), (182, 592), (244, 594)],
         BLACK)
    for dx in (0, 12, 24):
        poly([(118 + dx, 584), (112 + dx, 604), (124 + dx, 598)], BLACK, smooth=False)
    return img


ENV_TEX = (3072, 2048)      # pixels along the envelope (v) and round it (u)


def envelope_texture():
    """The envelope's doped cotton, laid out with v along her (tail at 0) and u round each section (0 at the top, a
    quarter on her right flank, three quarters on her left): seams between the panels; out from the white middle
    towards each end purple, black and purple bands and a black tip, each parted by a white stripe; and the arms on
    each flank, upright and facing the nose."""
    nv, nu = ENV_TEX
    tex = np.empty((nv, nu, 3), np.float32)
    tex[:] = np.array(WHITE, np.float32) / 255
    y = ENV_TAIL + (np.arange(nv) + 0.5) / nv * ENV_LENGTH
    # Each end: from the tip, the black tip, then purple, black and purple, white stripes between.
    stripe = 0.24
    bands = [(BLACK, 2.6), (PURPLE, 2.2), (BLACK, 2.4), (PURPLE, 2.4)]

    def paint(dist):
        out = np.full((nv, 3), np.nan, np.float32)
        edge = 0.0
        for colour, width in bands:
            rows = (dist >= edge) & (dist < edge + width)
            out[rows] = np.array(colour) / 255
            edge += width + stripe
        return out

    for dist in (ENV_NOSE - y, y - ENV_TAIL):
        c = paint(dist)
        rows = ~np.isnan(c[:, 0])
        tex[rows] = c[rows][:, None, :]
    # Seams: along her between the gores, and round her between the widths of cloth.
    seam = np.zeros((nv, nu), np.float32)
    seam[:, (np.arange(nu) % (nu // 24)) < 2] = 1
    seam[(np.arange(nv) % int(nv / ENV_LENGTH * 1.4)) < 2, :] = 1
    tex *= (1 - 0.07 * seam)[..., None]
    # The arms on each flank, facing the nose on both.
    e = emblem()
    circumference = 2 * math.pi * ENV_R * 0.965
    h_px = int(EMBLEM_HEIGHT / circumference * nu)
    w_px = int(EMBLEM_HEIGHT * e.width / e.height / ENV_LENGTH * nv)
    e = np.asarray(e.resize((w_px, h_px), Image.LANCZOS), np.float32) / 255     # (rows down, columns forward, RGBA)
    v0 = int((EMBLEM_Y - ENV_TAIL) / ENV_LENGTH * nv - w_px / 2)
    for u_mid, block in ((0.25, e[:, ::-1].transpose(1, 0, 2)), (0.75, e[::-1, ::-1].transpose(1, 0, 2))):
        u0 = int(u_mid * nu - h_px / 2)
        a = block[..., 3:4]
        tex[v0:v0 + w_px, u0:u0 + h_px] = tex[v0:v0 + w_px, u0:u0 + h_px] * (1 - a) + block[..., :3] * a
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


def gilt(d, path, w0, w1):
    """A gilded moulding: a dark shadow under a gold stroke with a light edge, so it reads as carved."""
    stroke(d, [(x + 2, y + 3) for x, y in path], w0 + 1, w1 + 1, (70, 44, 14))
    stroke(d, path, w0, w1, GOLD)
    stroke(d, [(x - 1, y - 1) for x, y in path], max(1, w0 * 0.35), max(1, w1 * 0.35), (252, 226, 150))


def scroll(d, a, b, up, size):
    """A C-scroll from a to b: a sweep that curls in at both ends, on the side `up` (-1 above, 1 below)."""
    (ax, ay), (bx, by) = a, b
    bulge = up * size * 0.9
    gilt(d, bezier(a, (ax + (bx - ax) * 0.25, ay + bulge), (ax + (bx - ax) * 0.75, by + bulge), b, 40), 8, 8)
    sgn = 1 if bx > ax else -1
    gilt(d, curl(ax - sgn * size * 0.05, ay - up * size * 0.32, size * 0.32, 1.1, -sgn * up, math.pi / 2 * up), 7, 2)
    gilt(d, curl(bx + sgn * size * 0.05, by - up * size * 0.32, size * 0.32, 1.1, sgn * up, math.pi / 2 * up), 7, 2)


def leaf(d, base, angle, length, width):
    """An acanthus leaf: a pointed blade with a rib."""
    ca, sa = math.cos(angle), math.sin(angle)
    pts = []
    for i in list(range(13)) + list(range(12, -1, -1)):
        t = i / 12
        w = width * math.sin(math.pi * t) * (1 - 0.3 * t) * (1 if len(pts) < 13 else -1)
        pts.append((base[0] + ca * length * t - sa * w, base[1] + sa * length * t + ca * w))
    d.polygon([(x + 2, y + 3) for x, y in pts], fill=(70, 44, 14))
    d.polygon(pts, fill=GOLD)
    d.line([base, (base[0] + ca * length * 0.9, base[1] + sa * length * 0.9)], fill=(150, 108, 40), width=2)


def flower(d, c, r):
    """A gilded rose: petals round a boss."""
    for k in range(7):
        a = 2 * math.pi * k / 7
        leaf(d, c, a, r, r * 0.45)
    d.ellipse([c[0] - r * 0.35, c[1] - r * 0.35, c[0] + r * 0.35, c[1] + r * 0.35], fill=(250, 222, 140))


GON_PANEL = (2048, 660)     # the carved side from the engine section to the bow: 3.1 blocks along by 1.0 high


def palmette(d, c, r, up=-1):
    """A fan shell: leaves spread from a point, opening upward (up=-1) or downward."""
    for k in range(9):
        a = math.radians(-160 + 140 * k / 8) if up < 0 else math.radians(20 + 140 * k / 8)
        leaf(d, c, a, r, r * 0.22)
    d.ellipse([c[0] - r * 0.18, c[1] - r * 0.18, c[0] + r * 0.18, c[1] + r * 0.18], fill=(250, 222, 140))


def gondola_panel(bow_right):
    """One of the gondola's carved sides as the film has it: black lacquer under bold gilt rococo carving. Mouldings top
    and bottom; heavy festoons of leaves hung along the top; at the bow the Baron's B in an oval frame with a great
    rose beside it; in the middle a cartouche framed in C-scrolls and acanthus round a bouquet; and along the bottom a
    band of heavy scrolls curling back to back with fan shells between. The bow is on the right if bow_right, for her
    right side; her left side is the same carving the other way round, its B still reading forwards."""
    W, H = GON_PANEL
    img = Image.new('RGB', (W, H), (14, 13, 16))
    d = ImageDraw.Draw(img)
    for y in range(H):
        k = int(10 * math.exp(-((y - H * 0.3) / (H * 0.2)) ** 2))
        d.line([(0, y), (W, y)], fill=(14 + k, 13 + k, 17 + k))
    for y, w in ((H * 0.035, 10), (H * 0.085, 5), (H * 0.96, 9)):
        gilt(d, [(0, y), (W, y)], w, w)
    # Heavy festoons along the top, hung between rosettes.
    step = W / 7
    for i in range(8):
        x = i * step
        if i < 7:
            for k in range(16):
                t = (k + 0.5) / 16
                leaf(d, (x + step * t - 12, H * 0.15 + math.sin(math.pi * t) * H * 0.16), math.radians(-25 + 50 * t),
                     34, 11)
        flower(d, (x, H * 0.15), 26)
    # The band of scrolls along the bottom: pairs curling back to back, a fan shell between each pair.
    unit = W / 5
    for i in range(5):
        cx = unit * (i + 0.5)
        for sgn in (-1, 1):
            scroll(d, (cx + sgn * 28, H * 0.86), (cx + sgn * unit * 0.46, H * 0.74), -1, 70)
            for k in range(4):
                leaf(d, (cx + sgn * (40 + 26 * k), H * 0.86), math.radians(-90 + sgn * (50 + 10 * k)), 34, 9)
        palmette(d, (cx, H * 0.9), 60)
    # The cartouche in the middle: four C-scrolls framing a bouquet, acanthus at the corners.
    cx, cy = W * 0.44, H * 0.5
    fw, fh = 300, 150
    for sx in (-1, 1):
        scroll(d, (cx + sx * 40, cy - fh), (cx + sx * fw, cy - fh * 0.2), 1, 120)
        scroll(d, (cx + sx * fw, cy + fh * 0.2), (cx + sx * 40, cy + fh), -1, 120)
        for k in range(6):
            leaf(d, (cx + sx * fw, cy), math.radians(90 - sx * 90 + sx * (-60 + 24 * k)), 56, 13)
    gilt(d, [(cx - 40, cy - fh), (cx + 40, cy - fh)], 9, 9)
    gilt(d, [(cx - 40, cy + fh), (cx + 40, cy + fh)], 9, 9)
    palmette(d, (cx, cy - fh - 6), 54, up=-1)
    for k in range(11):
        leaf(d, (cx, cy + 40), math.radians(-170 + 160 * k / 10), 70, 14)
    for fx, fy, fr in ((-40, -10, 26), (36, 0, 24), (0, 30, 30), (-70, 30, 20), (70, 34, 20)):
        flower(d, (cx + fx, cy + fy), fr)
    # Sprays of acanthus either side, between the cartouche and the ends.
    for sx, x0 in ((-1, cx - fw - 40), (1, cx + fw + 40)):
        for k in range(7):
            leaf(d, (x0, cy + 20), math.radians(90 + sx * (-90 + 20 * k) - 90), 60 - 4 * k, 12)
        gilt(d, curl(x0 + sx * 60, cy - 40, 50, 1.2, sx, math.pi), 8, 3)
    # The B's frame at the bow end (drawn here on the right; mirrored below for her left side), a great rose by it.
    bx, by = W * 0.8, H * 0.46
    for k in range(18):
        a = 2 * math.pi * k / 18
        leaf(d, (bx + math.cos(a) * 104, by + math.sin(a) * 140), a, 38, 11)
    gilt(d, [(bx + math.cos(a) * 100, by + math.sin(a) * 136) for a in np.linspace(0, 2 * math.pi, 72)], 13, 13)
    d.ellipse([bx - 88, by - 124, bx + 88, by + 124], fill=(24, 18, 30))
    flower(d, (bx - 190, by + 30), 52)
    for k in range(6):
        leaf(d, (bx - 190, by + 30), math.radians(150 + 22 * k), 90, 18)
    if not bow_right:
        img = img.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        bx = W - bx
        d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype('DejaVuSerif-Bold.ttf', 170)
    except OSError:
        font = ImageFont.load_default(size=170)
    d.text((bx + 4, by + 7), 'B', font=font, fill=(70, 44, 14), anchor='mm')
    d.text((bx, by), 'B', font=font, fill=GOLD, anchor='mm')
    return img


def gondola_art():
    """Both carved sides in one image: her right side on top, her left below."""
    W, H = GON_PANEL
    img = Image.new('RGB', (W, 2 * H))
    img.paste(gondola_panel(True), (0, 0))
    img.paste(gondola_panel(False), (0, H))
    a = np.asarray(img, np.float32) / 255
    return np.concatenate([a, np.ones(a.shape[:2] + (1,), np.float32)], axis=2)


def engine_texture(size=512):
    """The engine section's sides: grey sheet metal, riveted round its edges, with two rows of four tall louvres."""
    img = Image.new('RGB', (size, size), (128, 128, 124))
    d = ImageDraw.Draw(img)
    for y in range(size):
        k = int(12 * math.sin(y / size * math.pi))
        d.line([(0, y), (size, y)], fill=(122 + k, 122 + k, 118 + k))
    for row in range(2):
        for i in range(4):
            x0 = size * (0.14 + 0.19 * i)
            y0 = size * (0.12 + 0.42 * row)
            d.rectangle([x0, y0, x0 + size * 0.09, y0 + size * 0.32], fill=(18, 18, 20))
            d.line([(x0, y0 + size * 0.32), (x0 + size * 0.09, y0 + size * 0.32)], fill=(170, 170, 166), width=3)
    for t in np.linspace(0.03, 0.97, 18):
        for x, y in ((t * size, size * 0.03), (t * size, size * 0.97), (size * 0.03, t * size), (size * 0.97, t * size)):
            d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(90, 90, 88))
    a = np.asarray(img, np.float32) / 255
    return np.concatenate([a, np.ones(a.shape[:2] + (1,), np.float32)], axis=2)


def lattice_texture(size=512, cells=8):
    """The platform under the envelope: a dark frame of girders crossed by netting."""
    img = Image.new('RGB', (size, size), (34, 30, 28))
    d = ImageDraw.Draw(img)
    step = size / cells
    for i in range(cells * 4):
        p = i * step / 2
        d.line([(p, 0), (p - size, size)], fill=(70, 62, 52), width=2)
        d.line([(p - size, 0), (p, size)], fill=(70, 62, 52), width=2)
    for i in range(cells + 1):
        d.line([(i * step, 0), (i * step, size)], fill=(120, 92, 54), width=7)
        d.line([(0, i * step), (size, i * step)], fill=(120, 92, 54), width=7)
    a = np.asarray(img, np.float32) / 255
    return np.concatenate([a, np.ones(a.shape[:2] + (1,), np.float32)], axis=2)


def tricolour_texture(size=256):
    """Purple, white and black in three bands across the chord, for the elevator."""
    tex = np.empty((size, size, 4), np.float32)
    tex[..., 3] = 1
    for i, c in enumerate((PURPLE, WHITE, BLACK)):
        tex[:, i * size // 3:(i + 1) * size // 3, :3] = np.array(c) / 255
    return tex


def deck_texture(size=512, planks=10):
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


def write_textures(out):
    for name, rgba in (('envelope', envelope_texture()), ('gondola_side', gondola_art()), ('engine', engine_texture())):
        Image.fromarray((np.clip(rgba[..., :3], 0, 1) * 255).astype(np.uint8)).save(os.path.join(out, name + '.png'))
    e = emblem()
    bg = Image.new('RGBA', e.size, WHITE + (255,))
    bg.alpha_composite(e)
    bg.convert('RGB').save(os.path.join(out, 'emblem.png'))


# --- materials -------------------------------------------------------------------------------------------

def make_materials():
    m = cm.material
    m('envelope', WHITE, rough=0.85, spec=0.15, image=cm.image('envelope', envelope_texture()))
    m('fabric', WHITE, rough=0.85, spec=0.15)
    m('fin_dark', (46, 44, 48), rough=0.8, spec=0.15)
    m('tricolour', WHITE, rough=0.8, spec=0.15, image=cm.image('tricolour', tricolour_texture()))
    m('lattice', (34, 30, 28), rough=0.7, image=cm.image('lattice', lattice_texture()))
    m('bronze', (150, 104, 54), metal=1.0, rough=0.38)
    m('brass', (224, 174, 72), metal=1.0, rough=0.2)
    m('iron', (40, 40, 44), metal=0.8, rough=0.5)
    m('belt', (30, 24, 20), rough=0.7)
    m('cable', (44, 40, 36), rough=0.7)
    m('rope', (206, 192, 156), rough=0.95, spec=0.1)
    m('gondola_side', (14, 13, 16), rough=0.3, coat=0.4, image=cm.image('gondola_side', gondola_art()))
    m('engine', (128, 128, 124), metal=0.6, rough=0.45, image=cm.image('engine', engine_texture()))
    m('lacquer', (14, 13, 16), rough=0.3, coat=0.4)
    m('beam', (18, 17, 18), rough=0.45, coat=0.2)
    m('lining', (70, 34, 22), rough=0.4, coat=0.3)
    m('deck', (150, 96, 52), rough=0.55, image=cm.image('deck', deck_texture()))
    m('wood', (70, 40, 22), rough=0.4, coat=0.4)
    m('prop', (40, 30, 24), rough=0.35, coat=0.6)
    m('bomb', (24, 24, 26), metal=0.6, rough=0.45)


# --- the envelope ------------------------------------------------------------------------------------------

def envelope_profile(y):
    """The envelope's radius and the height of its axis at y: straight in the middle, tapering to points at both ends
    along a convex ogive, the axis rising a little towards each tip."""
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
    n = 120
    ys = [ENV_MID_Y + ENV_LENGTH / 2 * math.sin(math.pi / 2 * (-1 + 2 * i / n)) * 0.9995 for i in range(n + 1)]
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
    return p['half_width'] * max(0.0, math.sin(math.pi * s)) ** 0.6


def build_platform():
    """The flat platform under the envelope: dark lattice and netting on a rim of girders, hung from the envelope's
    flanks by cables all along."""
    p = PLATFORM
    m = cm.Mesh()
    n = 40
    ys = [p['back'] + (p['front'] - p['back']) * i / n for i in range(n + 1)]
    top = []
    for y in ys:
        w = max(0.03, platform_half_width(y))
        top.append([m.vert((x, y, p['z'])) for x in (-w, w)])
    for a, b in zip(top, top[1:]):
        f = m.face([a[0], a[1], b[1], b[0]], 'lattice')
        cm.box_uv(m, [f], 0.25)
    o = m.obj('platform')
    cm.solidify(o, 0.08)
    m = cm.Mesh()
    rim = [Vector((platform_half_width(y), y, p['z'] - 0.04)) for y in ys]
    rim = rim + [Vector((-v.x, v.y, v.z)) for v in reversed(rim)]
    cm.tube(m, rim, 0.06, 'bronze', seg=8)
    m.obj('platform_rim', smooth=50)
    m = cm.Mesh()
    for y in ys[2:-2:2]:
        w = platform_half_width(y)
        r, zc = envelope_profile(y)
        for side in (-1, 1):
            a = math.radians(30)
            top_pt = Vector((side * r * math.cos(a), y, zc - r * math.sin(a) * ENV_BELLY))
            cm.tube(m, [top_pt, Vector((side * w, y, p['z']))], 0.022, 'cable', seg=5)
    m.obj('platform_cables')


# --- the frame over the gondola, the girder and the tail ---------------------------------------------------------

def build_frame():
    """Bronze tubes from the gondola's rail up to two long bars over the crew's heads, and from the bars up to the
    platform, in triangles as the film shows them; and rigging from the gondola's ends out to the platform."""
    m = cm.Mesh()
    y0, y1 = GON_STERN - BEAM_OVER, GON_BOW + BEAM_OVER
    for side in (-1, 1):
        x = side * GON_HALF_WIDTH
        cm.tube(m, [Vector((x, y0, BAR_Z)), Vector((x, y1, BAR_Z))], 0.05, 'bronze', seg=8)
        rail = [GON_STERN, -0.9, 0.3, 1.5, GON_BOW]
        bar = [-1.5, -0.3, 0.9, 2.1]
        for i, yr in enumerate(rail):
            for yb in (bar[i - 1] if i > 0 else None, bar[i] if i < len(bar) else None):
                if yb is not None:
                    cm.tube(m, [Vector((x, yr, GON_TOP + BEAM)), Vector((x, yb, BAR_Z))], 0.035, 'bronze', seg=6)
        for yb in (-2.2, -0.6, 1.0, 2.4):
            w = platform_half_width(yb) * 0.55
            cm.tube(m, [Vector((x, yb, BAR_Z)), Vector((side * w, yb + 0.6, PLATFORM['z']))], 0.035, 'bronze', seg=6)
            cm.tube(m, [Vector((x, yb, BAR_Z)), Vector((side * GIRDER['half_width'], yb, GIRDER['top']))], 0.03,
                    'bronze', seg=6)
        # Rigging from the ends of the rail out to the platform's rim.
        for ye, yp in ((y1, 3.2), (y0, -6.0), (0.0, 1.5), (0.0, -3.0)):
            cm.tube(m, [Vector((x, ye, GON_TOP + BEAM)), Vector((side * platform_half_width(yp), yp, PLATFORM['z']))],
                    0.014, 'cable', seg=4)
    for y in (y0 + 0.1, y1 - 0.1):
        cm.tube(m, [Vector((-GON_HALF_WIDTH, y, BAR_Z)), Vector((GON_HALF_WIDTH, y, BAR_Z))], 0.04, 'bronze', seg=6)
    m.obj('frame', smooth=50)


def girder_at(y):
    """The girder's top and its depth at y: under the platform as far as the platform's end, then down to the tail,
    tapering as it goes."""
    g = GIRDER
    if y >= PLATFORM['back']:
        return g['top'], g['depth']
    t = (PLATFORM['back'] - y) / (PLATFORM['back'] - g['back'])
    return g['top'] + (g['tail_top'] - g['top']) * t, g['depth'] * (1 - 0.35 * t)


def build_girder():
    """A narrow girder of three bronze chords laced with diagonals, from over the gondola back to the tail."""
    g = GIRDER
    m = cm.Mesh()
    n = 26
    ys = [g['front'] + (g['back'] - g['front']) * i / n for i in range(n + 1)]
    pts = []
    for y in ys:
        top, depth = girder_at(y)
        w = g['half_width'] * (depth / g['depth'])
        pts.append((Vector((0, y, top)), Vector((-w, y, top - depth)), Vector((w, y, top - depth))))
    for k in range(3):
        cm.tube(m, [p[k] for p in pts], 0.045, 'bronze', seg=8)
    for i, p in enumerate(pts):
        cm.tube(m, [p[1], p[2]], 0.025, 'bronze', seg=6)
        if i + 1 < len(pts):
            q = pts[i + 1]
            for a, b in ((p[0], q[1]), (p[0], q[2]), (p[1], q[0]) if i % 2 else (p[2], q[0])):
                cm.tube(m, [a, b], 0.022, 'bronze', seg=5)
    m.obj('girder', smooth=50)


def panel(m, corners, mat, uvs=None):
    """A flat panel of fabric (one face, drawn from either side) with a bronze rim."""
    vs = [m.vert(c) for c in corners]
    m.face(vs, mat, uvs or [(0, 0), (1, 0), (1, 1), (0, 1)][:len(vs)])
    cm.tube(m, list(corners) + [corners[0]], 0.035, 'bronze', seg=6)


def build_tail():
    """The tail at the girder's end, under the back of the envelope: a white tailplane with the elevator hinged
    behind it, painted in Vulgaria's stripes, and dark triangular fins above and below rising to their rear edge,
    where the rudder is hinged. The rudder and elevator turn about their hinges (their origins)."""
    T = TAIL
    m = cm.Mesh()
    z, lead, hinge = T['z'], T['lead'], T['hinge']
    panel(m, [Vector((-T['span'], lead, z)), Vector((T['span'], lead, z)), Vector((T['span'], hinge, z)),
              Vector((-T['span'], hinge, z))], 'fabric')
    panel(m, [Vector((0, lead, z)), Vector((0, hinge, z)), Vector((0, hinge, z + T['up']))], 'fin_dark',
          [(0, 0), (1, 0), (1, 1)])
    panel(m, [Vector((0, lead, z)), Vector((0, hinge, z - T['down'])), Vector((0, hinge, z))], 'fin_dark',
          [(0, 0), (1, 1), (1, 0)])
    # Bracing wires from the fins' tips to the tailplane's corners.
    for zz in (z + T['up'], z - T['down']):
        for side in (-1, 1):
            cm.tube(m, [Vector((0, hinge, zz)), Vector((side * T['span'], hinge, z))], 0.012, 'cable', seg=4)
    m.obj('tail_fixed')
    chord = T['trail'] - hinge
    m = cm.Mesh()
    panel(m, [Vector((0, 0, -T['down'])), Vector((0, chord, -T['down'])), Vector((0, chord, T['up'])),
              Vector((0, 0, T['up']))], 'fin_dark')
    o = m.obj('rudder', location=(0, hinge, z), part='rudder')
    o['axis'] = 'z'
    m = cm.Mesh()
    panel(m, [Vector((-T['span'], 0, 0)), Vector((-T['span'], chord, 0)), Vector((T['span'], chord, 0)),
              Vector((T['span'], 0, 0))], 'tricolour', [(0, 0), (1, 0), (1, 1), (0, 1)])
    o = m.obj('elevator', location=(0, hinge, z), part='elevator')
    o['axis'] = 'x'


# --- the gondola ---------------------------------------------------------------------------------------------

def gondola_outline():
    """Her side's outline in (y, z), from the top of the bow down the raked bow, aft along the bottom, up the
    engine section's sloping floor and the stern."""
    return [(GON_BOW, GON_TOP), (GON_BOW_FOOT, 0.0), (GON_ENGINE_FRONT, 0.0), (GON_STERN, GON_STERN_RISE),
            (GON_STERN, GON_TOP)]


def bottom_at(y):
    """The gondola's bottom at y."""
    if y > GON_BOW_FOOT:
        return GON_TOP * (y - GON_BOW_FOOT) / (GON_BOW - GON_BOW_FOOT)
    if y < GON_ENGINE_FRONT:
        return GON_STERN_RISE * (GON_ENGINE_FRONT - y) / (GON_ENGINE_FRONT - GON_STERN)
    return 0.0


def build_gondola():
    """The gondola: straight black sides carved and gilded from the bow back to the engine section, which is grey
    sheet metal with louvres and covered over; the bow raked, the bottom flat, the stern's floor sloping up. Lined in
    mahogany inside, with a planked floor and the heavy black rail along the top on iron brackets, running on past
    both ends."""
    m = cm.Mesh()
    hw = GON_HALF_WIDTH
    out = gondola_outline()
    H = GON_PANEL[1] / (2 * GON_PANEL[1])   # each side has half the carved image

    def side_uv(y, z, right):
        u = (y - GON_ENGINE_FRONT) / (GON_BOW - GON_ENGINE_FRONT)
        v = z / GON_TOP
        return (u if right else 1 - u, (0.5 + 0.5 * v) if right else 0.5 * v)

    def engine_uv(y, z, right):
        u = (y - GON_STERN) / (GON_ENGINE_FRONT - GON_STERN)
        return (u if right else 1 - u, z / GON_TOP)

    for side in (-1, 1):
        right = side > 0
        carved = [(GON_BOW, GON_TOP), (GON_BOW_FOOT, 0.0), (GON_ENGINE_FRONT, 0.0), (GON_ENGINE_FRONT, GON_TOP)]
        engine = [(GON_ENGINE_FRONT, GON_TOP), (GON_ENGINE_FRONT, 0.0), (GON_STERN, GON_STERN_RISE),
                  (GON_STERN, GON_TOP)]
        for poly, mat, uvf in ((carved, 'gondola_side', side_uv), (engine, 'engine', engine_uv)):
            pts = poly if right else list(reversed(poly))
            vs = [m.vert((side * hw, y, z)) for y, z in pts]
            m.face(vs, mat, [uvf(y, z, right) for y, z in pts])
    # The bow, the bottom, the engine's sloping floor and the stern, joining the two sides.
    for i in range(len(out)):
        (ya, za), (yb, zb) = out[i], out[(i + 1) % len(out)]
        if i == len(out) - 1:
            continue    # the top is open
        mat = 'engine' if ya <= GON_ENGINE_FRONT + 1e-6 and yb <= GON_ENGINE_FRONT + 1e-6 else 'lacquer'
        vs = [m.vert((-hw, ya, za)), m.vert((hw, ya, za)), m.vert((hw, yb, zb)), m.vert((-hw, yb, zb))]
        f = m.face(vs, mat)
        cm.box_uv(m, [f], 0.9)
    m.obj('gondola_hull')
    # The lining, the floor, the engine section's front wall and lid.
    m = cm.Mesh()
    k = 0.05
    bow_at_floor = GON_BOW_FOOT + (GON_BOW - GON_BOW_FOOT) * GON_FLOOR / GON_TOP
    for side in (-1, 1):
        lining = [(GON_BOW - k, GON_TOP), (bow_at_floor - k, GON_FLOOR), (GON_ENGINE_FRONT, GON_FLOOR),
                  (GON_ENGINE_FRONT, GON_TOP)]
        vs = [m.vert((side * (hw - k), y, z)) for y, z in (lining if side < 0 else list(reversed(lining)))]
        f = m.face(vs, 'lining')
        cm.box_uv(m, [f], 1.0)
    rake = math.atan2(GON_BOW - GON_BOW_FOOT, GON_TOP)
    zm = (GON_FLOOR + GON_TOP) / 2
    ym = GON_BOW_FOOT + (GON_BOW - GON_BOW_FOOT) * zm / GON_TOP - k
    cm.add_box(m, (0, ym, zm), (2 * hw - 0.1, 0.02, (GON_TOP - GON_FLOOR) / math.cos(rake)), 'lining',
               rot=Matrix.Rotation(-rake, 4, 'X'))
    floor_end = GON_BOW_FOOT + (GON_BOW - GON_BOW_FOOT) * (GON_FLOOR - 0.06) / GON_TOP - 0.02
    cm.add_box(m, (0, (GON_ENGINE_FRONT + floor_end) / 2, GON_FLOOR - 0.03),
               (2 * hw - 0.1, floor_end - GON_ENGINE_FRONT, 0.06), 'deck', uv_scale=0.5)
    cm.add_box(m, (0, GON_ENGINE_FRONT, (GON_FLOOR + GON_TOP) / 2), (2 * hw - 0.04, 0.05, GON_TOP - GON_FLOOR), 'engine',
               uv_scale=1 / (2 * hw))
    cm.add_box(m, (0, (GON_STERN + GON_ENGINE_FRONT) / 2, GON_TOP - 0.02),
               (2 * hw, GON_ENGINE_FRONT - GON_STERN, 0.04), 'engine', uv_scale=1 / (2 * hw))
    m.obj('gondola_inside')
    # The rail: a heavy square black beam down each side and across each end, on iron brackets, the side beams
    # running on past the ends.
    m = cm.Mesh()
    zb = GON_TOP + BEAM / 2 + 0.01
    y0, y1 = GON_STERN - BEAM_OVER, GON_BOW + BEAM_OVER
    for side in (-1, 1):
        cm.add_box(m, (side * (hw - BEAM / 2 + 0.03), (y0 + y1) / 2, zb), (BEAM, y1 - y0, BEAM), 'beam')
        for y in np.linspace(GON_STERN + 0.15, GON_BOW - 0.15, 7):
            cm.add_box(m, (side * (hw + 0.02), y, GON_TOP - 0.04), (0.03, 0.1, 0.22), 'iron')
        for y in (y0 + 0.06, y1 - 0.06):
            cm.add_box(m, (side * (hw - BEAM / 2 + 0.03), y, zb), (BEAM + 0.02, 0.04, BEAM + 0.02), 'iron')
    for y in (GON_STERN + BEAM / 2, GON_BOW - BEAM / 2):
        cm.add_box(m, (0, y, zb), (2 * hw, BEAM, BEAM), 'beam')
    m.obj('gondola_rail')
    for i, (x, y) in enumerate(PLACES):
        cm.empty('stand_%d' % i, (x, y, GON_FLOOR))
    cm.empty('line', tuple(LINE_OUT))
    cm.empty('exhaust', (0.6, GON_STERN + 0.3, GON_TOP + 0.5))
    m = cm.Mesh()
    pipe = [Vector((0.6, GON_STERN + 0.3, GON_TOP)), Vector((0.6, GON_STERN + 0.3, GON_TOP + 0.35)),
            Vector((0.6, GON_STERN + 0.15, GON_TOP + 0.5))]
    cm.tube(m, cm.catmull(pipe, per=5), 0.05, 'iron', seg=8)
    # The hatch the hook's rope goes down through, and a brass fairlead under the keel.
    cm.add_box(m, (0, LINE_OUT.y, GON_FLOOR + 0.005), (0.36, 0.36, 0.02), 'brass')
    cm.lathe(m, [(0.05, -0.04), (0.1, -0.04), (0.1, 0.02), (0.05, 0.02)], lambda k: 'brass', axis='z', seg=16,
             origin=tuple(LINE_OUT))
    m.obj('gondola_fittings', smooth=40)


def build_coil():
    """The coil of rope hung over her left side at the bow, as the film has it: a long hank of cream rope draped over
    the rail, its loops hanging down the carved side in U's, overlapping and splayed a little along her. It is the
    hook's rope, and grows smaller as the rope goes out."""
    m = cm.Mesh()
    rng = np.random.default_rng(11)
    for k in range(18):
        cy = -0.45 + 0.9 * k / 17 + rng.normal(0, 0.03)
        half = 0.32 + 0.12 * rng.random()          # half the width of the loop, along her
        drop = 0.62 + 0.14 * rng.random()          # how far it hangs below the rail
        x = ROPE_SIDE * (0.05 + 0.012 * (k % 5))
        loop = []
        for t in np.linspace(0, math.pi, 17):
            loop.append(Vector((x, cy + half * math.cos(t), -drop * math.sin(t) ** 0.8)))
        # Over the top of the beam and back.
        loop = [Vector((x - ROPE_SIDE * 0.12, cy + half, 0.06))] + loop + [Vector((x - ROPE_SIDE * 0.12, cy - half, 0.06))]
        cm.tube(m, cm.catmull(loop, per=3), 0.038, 'rope', seg=8)
    m.obj('coil', location=(ROPE_SIDE * GON_HALF_WIDTH, COIL_Y, GON_TOP + BEAM), part='coil', smooth=40)


def blade(m, length, root, tip, pitch0, pitch1, turn, thick=0.05):
    """One propeller blade out from the hub, turned `turn` radians about the shaft (y): a paddle, widest two thirds
    out, its pitch easing off from pitch0 at the root to pitch1 at the tip (degrees from the plane of the disc)."""
    n = 10
    rot = Matrix.Rotation(turn, 3, 'Y')
    rings = []
    for i in range(n + 1):
        t = i / n
        r = root + (length - root) * t
        chord = tip + (0.26 - tip) * math.sin(math.pi * min(1.0, t * 1.3)) if t < 0.95 else tip
        ang = math.radians(pitch0 + (pitch1 - pitch0) * t)
        along = Vector((0, math.sin(ang), math.cos(ang)))
        across = Vector((0, math.cos(ang), -math.sin(ang)))
        rings.append([m.vert(rot @ (Vector((r, 0, 0)) + along * c + across * d))
                      for c, d in ((-chord / 2, -thick / 2), (chord / 2, -thick / 2), (chord / 2, thick / 2),
                                   (-chord / 2, thick / 2))])
    for a, b in zip(rings, rings[1:]):
        for k in range(4):
            j = (k + 1) % 4
            m.face([a[k], a[j], b[j], b[k]], 'prop')
    m.face(list(reversed(rings[0])), 'prop')
    m.face(rings[-1], 'prop')


def build_props():
    """The two propellers behind the stern corners, pushing her along, each on a shaft run aft from two bearings on
    the frame, turned by a belt from the engine over a spoked iron pulley. The propellers and the pulleys turn about
    the shafts (y); the left one is the right one mirrored."""
    P = PROP
    for side, name in ((1, 'prop_r'), (-1, 'prop_l')):
        x = side * P['x']
        # The shaft, its bearings and struts.
        m = cm.Mesh()
        cm.tube(m, [Vector((x, P['bearing_y'], P['z'])), Vector((x, P['y'] + 0.15, P['z']))], 0.05, 'bronze', seg=10)
        for yb in (P['bearing_y'], P['y'] + 0.45):
            cm.lathe(m, [(0.0, -0.1), (0.1, -0.1), (0.1, 0.1), (0.0, 0.1)], lambda k: 'iron', axis='y', seg=12,
                     origin=(x, yb, P['z']))
            cm.tube(m, [Vector((x, yb, P['z'])), Vector((side * GON_HALF_WIDTH, yb, BAR_Z))], 0.035, 'bronze', seg=6)
        for a in ((side * GON_HALF_WIDTH, GON_ENGINE_FRONT, GON_TOP + BEAM), (side * GON_HALF_WIDTH, GON_STERN,
                                                                              GON_TOP + BEAM)):
            cm.tube(m, [Vector(a), Vector((x, P['y'] + 0.45 if a[1] < -1.5 else P['bearing_y'], P['z']))], 0.035,
                    'bronze', seg=6)
        cm.tube(m, [Vector((x, P['bearing_y'], P['z'])), Vector((side * GIRDER['half_width'], P['bearing_y'],
                                                                 GIRDER['top'] - GIRDER['depth']))], 0.03, 'bronze', seg=6)
        # The belt: from a small pulley on the engine section's side up over the big one, both runs straight.
        lo = Vector((side * (GON_HALF_WIDTH + 0.06), P['pulley_y'], 0.62))
        hi = Vector((x, P['pulley_y'], P['z']))
        d = (hi - lo)
        d.normalize()
        n = Vector((0, 1, 0)).cross(d)
        for r_hi, r_lo, s in ((P['pulley_r'], 0.1, 1), (P['pulley_r'], 0.1, -1)):
            cm.tube(m, [lo + n * r_lo * s, hi + n * r_hi * s], 0.02, 'belt', seg=4)
        cm.lathe(m, [(0.0, -0.05), (0.12, -0.05), (0.12, 0.05), (0.0, 0.05)], lambda k: 'iron', axis='y', seg=12,
                 origin=tuple(lo))
        m.obj(name + '_shaft', smooth=40)
        # The pulley: a rim, five spokes and a hub, turning with the propeller.
        m = cm.Mesh()
        r = P['pulley_r']
        rim = [Vector((math.cos(t) * r, 0, math.sin(t) * r)) for t in np.linspace(0, 2 * math.pi, 41)]
        cm.tube(m, rim, 0.04, 'iron', seg=8)
        for k in range(5):
            t = 2 * math.pi * k / 5
            cm.tube(m, [Vector((0, 0, 0)), Vector((math.cos(t) * r, 0, math.sin(t) * r))], 0.022, 'iron', seg=6)
        cm.lathe(m, [(0.0, -0.08), (0.07, -0.08), (0.07, 0.08), (0.0, 0.08)], lambda k: 'iron', axis='y', seg=12)
        m.obj(name + '_pulley', location=(x, P['pulley_y'], P['z']), part=name + '_pulley', smooth=40)
        # The propeller.
        m = cm.Mesh()
        for k in (0, 1):
            blade(m, P['r'], 0.1, 0.1, 60, 16, math.pi * k)
        cm.lathe(m, [(0.0, -0.14), (0.12, -0.12), (0.13, 0.0), (0.11, 0.1), (0.0, 0.13)], lambda k: 'iron', axis='y',
                 seg=16)
        o = m.obj(name, location=(x, P['y'], P['z']), part=name, smooth=30)
        if side < 0:
            o.scale.x = -1


def build_wheel():
    """The steering wheel at the bow, as on a Lebaudy: eight spokes round a brass hub on a brass post, facing the pilot.
    It turns as she is steered."""
    m = cm.Mesh()
    r = 0.28
    ring = [Vector((math.cos(a) * r, 0, math.sin(a) * r)) for a in np.linspace(0, 2 * math.pi, 41)]
    cm.tube(m, ring, 0.028, 'wood', seg=8)
    for k in range(8):
        a = 2 * math.pi * k / 8
        d = Vector((math.cos(a), 0, math.sin(a)))
        cm.tube(m, [d * 0.04, d * (r + 0.1)], 0.018, 'wood', seg=6)
    cm.lathe(m, [(0.0, -0.06), (0.06, -0.06), (0.06, 0.04), (0.0, 0.05)], lambda k: 'brass', axis='y', seg=16)
    m.obj('wheel', location=tuple(WHEEL), part='wheel', smooth=40)
    m = cm.Mesh()
    cm.lathe(m, [(0.0, GON_FLOOR), (0.1, GON_FLOOR), (0.045, GON_FLOOR + 0.08), (0.035, WHEEL.z - 0.06),
                 (0.06, WHEEL.z), (0.0, WHEEL.z + 0.02)], lambda k: 'brass', axis='z', seg=16,
             origin=(WHEEL.x, WHEEL.y + 0.1, 0))
    m.obj('wheel_post', smooth=40)


def build_bombs():
    """Six bombs stood nose down in a rack across the front of the engine section: black iron teardrops with four fins
    and a brass fuse on the nose, smaller than a block of TNT. Each is its own part, so the game can take them from the
    rack one by one."""
    m = cm.Mesh()
    inner = GON_HALF_WIDTH - 0.08
    for z in (GON_FLOOR + 0.1, GON_FLOOR + 0.38):
        cm.add_box(m, (0, BOMB_RACK_Y, z), (2 * inner, 0.22, 0.04), 'wood')
    m.obj('bomb_rack')
    for i in range(6):
        x = -0.7 + 0.28 * i
        m = cm.Mesh()
        cm.lathe(m, [(0.0, -0.25), (0.03, -0.24), (0.04, -0.21), (0.09, -0.13), (0.105, -0.04), (0.1, 0.05),
                     (0.065, 0.14), (0.036, 0.2), (0.036, 0.25), (0.0, 0.25)],
                 lambda k: 'brass' if k < 2 else 'bomb', axis='z', seg=16)
        for k in range(4):
            a = math.pi / 4 + k * math.pi / 2
            cm.add_box(m, (math.cos(a) * 0.075, math.sin(a) * 0.075, 0.2), (0.08, 0.01, 0.11), 'bomb',
                       rot=Matrix.Rotation(a, 4, 'Z'))
        m.obj('bomb_%d' % i, location=(x, BOMB_RACK_Y, GON_FLOOR + 0.33), part='bomb_%d' % i, smooth=40)


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
    path = [Vector((0, 0, -0.42)), Vector((0, 0, -0.7)), Vector((0, 0.04, -0.95)), Vector((0, 0.22, -1.08)),
            Vector((0, 0.38, -0.98)), Vector((0, 0.42, -0.8)), Vector((0, 0.36, -0.68))]
    cm.tube(m, cm.catmull(path, per=6), 0.075, 'iron', seg=10, flare=lambda t: 1.15 - 0.85 * t * t)
    cm.tube(m, [Vector((0, 0.02, -0.6)), Vector((0, 0.34, -0.68))], 0.015, 'iron', seg=5)
    m.obj('hook', part='hook', smooth=40)


def build_ladder():
    """The rope ladder from the middle of her left side, which lets down as far as the hook (LINE_MAX), as the film
    has it hanging there. It is one rung's length of it, two ropes and a wooden rung, which the game stacks down from
    the top as far as it is let out (the renders do the same with an array). It hangs from two iron hooks on the rail;
    drawn up, it lies in the bottom of the gondola, out of sight."""
    m = cm.Mesh()
    for side in (-0.2, 0.2):
        cm.tube(m, [Vector((0, side, 0)), Vector((0, side, -LADDER_PITCH))], 0.025, 'rope', seg=6)
    cm.add_box(m, (0, 0, -LADDER_PITCH / 2), (0.07, 0.46, 0.05), 'wood')
    o = m.obj('ladder', location=tuple(LADDER_TOP), part='ladder', smooth=40)
    o['pitch'] = LADDER_PITCH
    m = cm.Mesh()
    for y in (-0.2, 0.2):
        hook = [Vector((ROPE_SIDE * x, y, z)) for x, z in ((-0.06, 0.0), (0.02, 0.0), (0.06, -0.08), (0.02, -0.14))]
        cm.tube(m, cm.catmull(hook, per=4), 0.018, 'iron', seg=6)
    m.obj('ladder_hooks', location=tuple(LADDER_TOP))
    cm.empty('ladder_top', tuple(LADDER_TOP))


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
    build_frame()
    build_girder()
    build_tail()
    build_gondola()
    build_coil()
    build_props()
    build_wheel()
    build_bombs()
    build_hook()
    build_ladder()
    build_rope()


# --- poses -------------------------------------------------------------------------------------------------

def pose(spin=0.0, steer=0.0, climb=0.0, hook=None, ladder=0.0, bombs=6):
    """What the game's animation does: the propellers and their pulleys turned by spin (radians), the rudder and
    wheel by steer and the elevator by climb (-1 to 1), the hook let down that many blocks below the keel (None:
    drawn up under her), the ladder let down that many blocks (0: drawn up) and that many bombs left in
    the rack."""
    drop = 0.12 if hook is None else hook
    for o in bpy.data.objects:
        part = o.get('part', '')
        if part in ('prop_r', 'prop_l', 'prop_r_pulley', 'prop_l_pulley'):
            o.rotation_euler = (0, spin * (1 if part.startswith('prop_r') else -1), 0)
        elif part == 'rudder':
            o.rotation_euler = (0, 0, math.radians(-25) * steer)
        elif part == 'elevator':
            o.rotation_euler = (math.radians(20) * climb, 0, 0)
        elif part == 'wheel':
            o.rotation_euler = (0, math.radians(120) * steer, 0)
        elif part == 'coil':
            k = 1.0 - 0.55 * min(drop, LINE_MAX) / LINE_MAX
            o.scale = (1, k, k)
        elif part == 'hook':
            o.location = LINE_OUT + Vector((0, 0, -drop))
        elif part == 'rope':
            o.scale = (1, 1, max(drop, 0.01))
        elif part == 'ladder':
            rungs = int(round(min(ladder, LINE_MAX) / LADDER_PITCH))
            o.hide_render = o.hide_viewport = rungs == 0
            arr = o.modifiers.get('rungs') or o.modifiers.new('rungs', 'ARRAY')
            arr.use_relative_offset = False
            arr.use_constant_offset = True
            arr.constant_offset_displace = (0, 0, -LADDER_PITCH)
            arr.count = max(rungs, 1)
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
    # A dull, darker field under her, so that her black and white are not tinted green by a bright lawn.
    cm.MATS['grass'].node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = cm.srgb((74, 84, 62))
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
    ('side', (56, -3.6, 14), (0, -3.6, 13.5), 34, 9.0, dict(spin=0.5)),
    ('film_flight', (-48, 22, 26), (0, -3.6, 13), 34, 9.0, dict(spin=1.0)),
    ('below', (-7, 15, 0.6), (0, -1.5, 11), 22, 9.0, dict(spin=0.9)),
    ('arms', (-17, -1.6, 17.4), (0, -1.6, 16.4), 40, 9.0, dict(spin=0.9)),
    ('three_quarter', (-32, 28, 22), (0, -4, 12), 30, 9.0, dict(spin=1.3, steer=0.4)),
    ('front', (5, 46, 11), (0, 0, 13), 32, 9.0, dict(spin=0.2)),
    ('rear', (-15, -44, 15), (0, -11, 11), 30, 9.0, dict(spin=0.8, steer=-0.5, climb=0.4)),
    ('tail', (10, -10, 12.6), (0, -17, 10.6), 30, 9.0, dict(spin=0.8, steer=-0.4, climb=0.3)),
    ('gondola', (-6.4, 0.9, 9.6), (0, -0.3, 10.2), 30, 9.0, dict(spin=0.3, ladder=1.6)),
    ('gondola_right', (6.4, 1.2, 10.2), (0, -0.1, 10.0), 30, 9.0, dict(spin=0.3)),
    ('props', (-4.6, -6.4, 11.8), (-1.6, -2.4, 10.8), 30, 9.0, dict(spin=0.6)),
    ('deck', (3.6, -4.6, 12.0), (0, 0.3, 9.4), 26, 9.0, dict(spin=0.6)),
    ('hook_ladder', (24, 16, 8.0), (0, 0, 11.0), 30, 20.0, dict(spin=0.4, hook=17.0, ladder=17.0, bombs=4)),
    ('landed', (14, 15, 2.6), (0, -1, 3.8), 28, 0.0, dict(spin=0.0)),
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
