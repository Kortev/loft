"""Beam the shark fiend of the Reze arc in Minecraft model boxes, built from the two pixel-art drawings kortev approved.

The drawings are the spec (tools/beam_profile.py is the side, tools/beam_front.py the front; both at game resolution, one
drawing pixel = 1/16 block = one model pixel). This module measures them and builds the boxes:

* a side silhouette (the body's outline polygon) and a front one (the half width at each height) give every slice along his
  length its top, its bottom and, by the front view's profile, its width; each slice is stacked as a centre box plus narrower
  steps toward its top and bottom, so he reads round from the front as Pochita's stepped bean does;
* the head is the dome above the mouth (a part of its own, hinged at the mouth's corner), the lower jaw a part hinged at the
  same corner, built in a frame turned along its rim, so the bite can animate; the mouth's walls are a thin dark plate between
  them, and the teeth are rows of planes along the lips painted from the drawings;
* the legs are chains of angled boxes through the drawn hip, knee and foot (the side gives their length and height, the front
  their sideways splay).

    python tools/beam_boxes.py --java      writes chainsawman/src/client/java/.../client/BeamLayout.java (the game's boxes)
    python tools/beam_boxes.py --report    prints the parts, the box count and the texture

tools/gen_chainsaw_textures.py (beam_entity) imports this and paints every box from the drawings (projected onto the boxes
from the side and from the front); tools/beam_check.py lays the boxes' silhouettes over the drawings' and prints the overlap.
Pure Python + numpy + Pillow.

Space, as the game's models have it, in pixels (16 to a block): x his +x side, y DOWN, z along him: his front toward -z, the
middle of him at z = 0 (the drawing's x = XM). y is the drawing's body layer's y (the top of his back at about 7, the spike
tips at 1) and the soles of his feet are at y = HEIGHT: the drawing's ground line (82) is LIFT (12) below the body layer's top.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import beam_front as bf  # noqa: E402
import beam_profile as bp  # noqa: E402

LIFT = bp.LIFT
HEIGHT = bp.GROUND - LIFT      # the soles of his feet in the body layer's y (70): the model's y = 0 is this high above the ground
XM = 75                        # the drawing's x of the middle of him: model z = XM - x
CX = bf.CX                     # the front drawing's centre: model x = front x - CX
X_NOSE = 147                   # one past the snout's last column
X_CORNER = int(bp.CORNER[0])   # the mouth's corner (the drawing's x), where the head and the jaw hinge
Y_CORNER = int(bp.CORNER[1])
X_REAR = 62                    # where the trunk ends and the rear of his body (which sways) begins
X_STALK = 23                   # the tail's stalk begins
X_FIN = 12                     # the crescent fin's hinge
TEXTURE_W = 1024
TOL = 1.4                      # how far a row of a slice may stray from its true width (pixels)
JAW_STEP = 1                   # the jaw is cut into slices this deep (it is turned 21 degrees, so its steps show; fine ones show least)
WBIAS = 0.4                    # boxes are this much narrower than the true width (rounding up showed as a pixel too wide)


def Zd(x):
    """Model z of the drawing's x."""
    return XM - x


# --- the drawings, as masks and pictures ---------------------------------------------------------------------------------------

def _poly(points, size):
    im = Image.new('L', size, 0)
    ImageDraw.Draw(im).polygon(points, fill=255)
    return np.array(im) > 0


SIDE_SIL = _poly(bp.outline(), (bp.W, bp.H))     # [y, x] in the body layer: the body without the open mouth
GAPE = _poly(bp.gape(), (bp.W, bp.H))
_SIDE_IMG = None
_FRONT_IMG = None


def side_img():
    """The body layer of the side drawing, RGBA, [y, x]."""
    global _SIDE_IMG
    if _SIDE_IMG is None:
        _SIDE_IMG = np.array(bp.body())
    return _SIDE_IMG


def front_img():
    global _FRONT_IMG
    if _FRONT_IMG is None:
        _FRONT_IMG = np.array(bf.body())
    return _FRONT_IMG


def _front_half():
    a = np.array(bf.body(extras=False))[:, :, 3] > 0
    out = np.zeros(bf.H)
    for y in range(bf.H):
        xs = np.nonzero(a[y])[0]
        if len(xs):
            out[y] = (xs.max() - xs.min() + 1) / 2.0
    return out


FRONT_HALF = _front_half()
F_TOP, F_BOT = 7.0, 67.0       # the front view's body spans these rows
F_WIDEST = 41.0                # and is widest here


def front_half(y):
    """The front view's half width at layer row y (a float: interpolated between rows' centres)."""
    return float(np.interp(y - 0.5, np.arange(bf.H), FRONT_HALF))


def smooth(t):
    t = min(1.0, max(0.0, t))
    return t * t * (3 - 2 * t)


def taper(x):
    """How much of the front view's width a slice at the drawing's x has: the full width through the shoulders, narrowing to
    the tail's stalk behind, and toward the head ahead (the mouth is about 18 either side of the middle, the shoulders 25),
    the snout rounding off at its tip."""
    if x < X_STALK:
        return 0.26 - 0.06 * (X_STALK - x) / 11.0
    if x < 70:
        return 0.26 + 0.74 * smooth((x - X_STALK) / (70.0 - X_STALK))
    if x <= 84:
        return 1.0
    if x <= X_CORNER:
        return 1.0 - 0.26 * (x - 84) / (X_CORNER - 84.0)
    u = (x - X_CORNER) / float(X_NOSE - X_CORNER)
    tip = max(0.0, (x - 134.0) / 13.0)
    return (0.74 - 0.06 * u) * math.sqrt(max(0.05, 1.0 - tip * tip))


# --- boxes and parts ------------------------------------------------------------------------------------------------------

class Box:
    def __init__(self, part, kind, x, y, z, w, h, d, **meta):
        self.part = part
        self.kind = kind
        self.x, self.y, self.z = x, y, z   # the min corner, in the part's own frame (relative to its pivot)
        self.w, self.h, self.d = int(round(w)), int(round(h)), int(round(d))
        self.meta = meta
        self.u = self.v = None             # the texture corner, set by pack()

    @property
    def footprint(self):
        return 2 * self.d + 2 * self.w, self.d + self.h


class Part:
    def __init__(self, name, parent, pivot, rot=(0.0, 0.0, 0.0)):
        self.name, self.parent, self.pivot, self.rot = name, parent, tuple(pivot), tuple(rot)  # rot: pitch, yaw, roll at rest
        self.boxes = []


PARTS = {}
BOXES = []
_ORDER = []
STATS = {}


def part(name, parent, pivot, rot=(0.0, 0.0, 0.0)):
    """A part hinged at {pivot} (pixels, in the parent's frame)."""
    p = Part(name, parent, pivot, rot)
    PARTS[name] = p
    _ORDER.append(name)
    return p


def absolute_pivot(name):
    """Where a part's pivot is in model space (the chain's translations only: for placing boxes by their model position, so
    only for parts whose parents are not turned)."""
    p = PARTS[name]
    x, y, z = p.pivot
    if p.parent:
        px, py, pz = absolute_pivot(p.parent)
        return px + x, py + y, pz + z
    return x, y, z


def add_box(part_name, kind, x, y, z, w, h, d, local=False, **meta):
    """A box whose min corner is at the model position (x, y, z); {local}: it is in the part's own frame already."""
    if not local:
        ox, oy, oz = absolute_pivot(part_name)
        x, y, z = x - ox, y - oy, z - oz
    b = Box(part_name, kind, x, y, z, w, h, d, **meta)
    PARTS[part_name].boxes.append(b)
    BOXES.append(b)
    return b


# --- slices cut into stacks of boxes --------------------------------------------------------------------------------------

def rows_for(y0, y1, half_of):
    """A slice between rows y0 and y1 (integers, y down) as a stack of rows: [(ya, yb, w)]. {half_of(y)} is the true half width
    at row-centre y; a row runs on while the true width stays within TOL of itself, and its box is as wide as their mean."""
    out = []
    y = y0
    while y < y1:
        lo = hi = half_of(y + 0.5)
        ye = y + 1
        while ye < y1:
            v = half_of(ye + 0.5)
            if max(hi, v) - min(lo, v) > TOL:
                break
            lo, hi = min(lo, v), max(hi, v)
            ye += 1
        w = max(2, int(round((lo + hi) - WBIAS)))
        out.append((y, ye, w))
        y = ye
    return out


def merge(rows):
    out = []
    for ya, yb, w in rows:
        if out and out[-1][2] == w and out[-1][1] == ya:
            out[-1] = (out[-1][0], yb, w)
        else:
            out.append((ya, yb, w))
    return out


def column_runs(x):
    col = np.nonzero(SIDE_SIL[:, x])[0]
    if not len(col):
        return []
    return [(int(r[0]), int(r[-1]) + 1) for r in np.split(col, np.where(np.diff(col) > 1)[0] + 1)]


def slice_extent(xa, xb, run=0):
    """The top and bottom (exclusive) of the body's {run}-th run of columns over x in [xa, xb), averaged and rounded."""
    tops, bots = [], []
    for x in range(xa, xb):
        runs = column_runs(x)
        if len(runs) > run:
            tops.append(runs[run][0])
            bots.append(runs[run][1])
    return int(round(np.mean(tops))), int(round(np.mean(bots)))


def slice_rows(xa, xb, top, bot, span):
    """The rows of a slice from {top} to {bot} using the front view's profile over its span of rows (a pair (y_top, y_bottom) in
    the front view that the slice's own height is stretched over), at the slice's taper."""
    f = taper((xa + xb) / 2.0)
    t0, t1 = span

    def half_of(y):
        t = (y - top) / float(max(1, bot - top))
        return f * front_half(t0 + t * (t1 - t0))
    return merge(rows_for(top, bot, half_of))


def add_slice(part_name, kind, xa, xb, top, bot, span, **meta):
    rows = slice_rows(xa, xb, top, bot, span)
    for ya, yb, w in rows:
        add_box(part_name, kind, -w / 2.0, ya, Zd(xb), w, yb - ya, xb - xa, slice=(xa, xb), row=(ya, yb), **meta)
    return rows


def edges(lo, hi, step):
    out = list(range(lo, hi, step)) + [hi]
    return out


# --- building him ----------------------------------------------------------------------------------------------------------

def rotation(pitch, yaw, roll):
    """The game's order: rotationZYX(roll, yaw, pitch), Rz * Ry * Rx."""
    cx, sx, cy, sy, cz, sz = math.cos(pitch), math.sin(pitch), math.cos(yaw), math.sin(yaw), math.cos(roll), math.sin(roll)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


rot_matrix = rotation


def euler(R):
    """The (pitch, yaw, roll) whose rotation() is R."""
    yaw = -math.asin(max(-1.0, min(1.0, R[2, 0])))
    return (math.atan2(R[2, 1], R[2, 2]), yaw, math.atan2(R[1, 0], R[0, 0]))


def along(d):
    """The (pitch, yaw, roll) that turns a part's +y axis (the way its boxes run) onto the unit vector d, without yaw."""
    d = np.array(d, float)
    d = d / np.linalg.norm(d)
    return (math.asin(max(-1.0, min(1.0, d[2]))), 0.0, math.atan2(-d[0], d[1]))


def fit_jaw():
    """The jaw's rim: the line from the corner through the drawn lower rim, as a direction in the drawing's (x, y)."""
    pts = np.array([p for p in bp.spline(bp.LOWER_RIM) if p[0] >= X_CORNER + 2])
    d = pts - np.array(bp.CORNER)
    slope = float((d[:, 0] * d[:, 1]).sum() / (d[:, 0] ** 2).sum())
    return math.atan(slope)


def build():
    PARTS.clear()
    BOXES.clear()
    _ORDER.clear()
    STATS.clear()
    part('trunk', None, (0.0, 0.0, 0.0))
    part('head', 'trunk', (0.0, float(Y_CORNER), Zd(X_CORNER)))
    theta = fit_jaw()
    STATS['jaw_theta'] = theta
    part('jaw', 'head', (0.0, 0.0, 0.0), rot=(theta, 0.0, 0.0))
    part('rear', 'trunk', (0.0, 34.0, Zd(X_REAR)))
    part('stalk', 'rear', (0.0, 0.0, Zd(X_STALK) - Zd(X_REAR)))
    part('tail', 'stalk', (0.0, 0.0, Zd(X_FIN) - Zd(X_STALK)))

    # The rear of the body, narrowing toward the stalk; the trunk, the chest and back, up to the mouth's corner.
    for xa, xb in zip(edges(X_STALK, X_REAR, 3)[:-1], edges(X_STALK, X_REAR, 3)[1:]):
        top, bot = slice_extent(xa, xb)
        add_slice('rear', 'rear', xa, xb, top, bot, (F_TOP, F_BOT))
    # (the rear's first slice and the head's last carry on 2 px into their neighbours, so a turn at the joint opens no crack)
    top, bot = slice_extent(X_REAR, X_REAR + 2)
    add_slice('rear', 'rear', X_REAR, X_REAR + 2, top, bot, (F_TOP, F_BOT), overlap=True)
    tr = list(range(X_REAR, 104, 3)) + [104, X_CORNER]
    for xa, xb in zip(tr[:-1], tr[1:]):
        top, bot = slice_extent(xa, xb)
        add_slice('trunk', 'trunk', xa, xb, top, bot, (F_TOP, F_BOT))

    # The head: the dome over the mouth, the snout carrying on past it, rounded off at the tip. Its bottom is the roof of the
    # mouth: full width there, so the front view's profile is stretched over the rows from the top to the widest.
    top, bot = slice_extent(X_CORNER - 2, X_CORNER)
    add_slice('head', 'head', X_CORNER - 2, X_CORNER, top, Y_CORNER + 2, (F_TOP, F_WIDEST), overlap=True)
    he = list(range(X_CORNER, 138, 2)) + [138, 140, 142, 144, 146, X_NOSE]
    for xa, xb in zip(he[:-1], he[1:]):
        top, bot = slice_extent(xa, xb)
        add_slice('head', 'head', xa, xb, top, bot, (F_TOP, F_WIDEST))
    build_stalk_and_fin()
    build_jaw(theta)
    build_plate()
    build_eyes()
    build_spikes()
    build_legs()
    build_pectorals()
    build_teeth(theta)
    return theta


def build_stalk_and_fin():
    """The stalk: a short tapering block; the crescent fin: its rows from the drawn polygon, thin across."""
    stalk = _poly(bp.TAIL_STALK, (bp.W, bp.H))
    for xa, xb in ((12, 16), (16, 20), (20, X_STALK + 1)):
        tops, bots = [], []
        for x in range(xa, xb):
            ys = np.nonzero(stalk[:, x])[0]
            if len(ys):
                tops.append(ys.min())
                bots.append(ys.max() + 1)
        top, bot = int(round(np.mean(tops))), int(round(np.mean(bots)))
        w = max(4, int(round(2.0 * taper(xa) * front_half(34.0) * 0.9)))
        add_box('stalk', 'stalk', -w / 2.0, top, Zd(xb), w, bot - top, xb - xa, slice=(xa, xb), row=(top, bot))
    fin = _poly(bp.TAIL_FIN, (bp.W, bp.H))
    ys = np.nonzero(fin.any(axis=1))[0]
    y = int(ys.min())
    while y <= ys.max():
        ye = min(y + 3, int(ys.max()) + 1)
        xs = np.nonzero(fin[y:ye].any(axis=0))[0]
        if len(xs):
            xa, xb = int(xs.min()), int(xs.max()) + 1
            add_box('tail', 'fin', -1.0, y, Zd(xb), 2, ye - y, xb - xa, slice=(xa, xb), row=(y, ye))
        y = ye


def build_jaw(theta):
    """The lower jaw, built in a frame turned along its rim: z runs forward along the rim from the corner (local -z), y down
    away from it, so the boxes stand straight in it. Its sections are the drawing's mask sampled along and across that frame; a
    flat top (the gums and teeth) and a rounded bowl below."""
    dirx, diry = math.cos(theta), math.sin(theta)      # forward along the rim, in the drawing's (x, y)
    nx, ny = -math.sin(theta), math.cos(theta)         # down, away from the rim
    hx, hy = bp.CORNER

    def inside(s, dd):
        x, y = hx + s * dirx + dd * nx, hy + s * diry + dd * ny
        xi, yi = int(math.floor(x)), int(math.floor(y))
        return 0 <= xi < bp.W and 0 <= yi < bp.H and SIDE_SIL[yi, xi] and not GAPE[yi, xi]

    def in_gape(s, dd):
        x, y = hx + s * dirx + dd * nx, hy + s * diry + dd * ny
        xi, yi = int(math.floor(x)), int(math.floor(y))
        return 0 <= xi < bp.W and 0 <= yi < bp.H and GAPE[yi, xi]

    def extent(s):
        """Where the mask is along the normal at s: from the rim (the first inside) to the chin (the last)."""
        dd = -3.0
        while dd < 8.0 and not in_gape(s, dd):
            dd += 0.5
        if dd >= 8.0:
            dd = -1.0
        while dd < 14.0 and not inside(s, dd):
            dd += 0.5
        if dd >= 14.0:
            return 0.0, 0.0
        top = dd
        while dd < 60 and inside(s, dd + 0.5):
            dd += 0.5
        return top, dd + 0.5
    length = 0
    while extent(length + 0.5)[1] > 0 and length < 60:
        length += 1
    STATS['jaw_len'] = length
    ss = list(range(0, length, JAW_STEP)) + [length]
    # the mask's edges are pixel art (stairs of a pixel along a turned frame): smooth the rim and the chin along the jaw so the
    # boxes do not copy that jitter
    raw = np.array([extent(s + 0.5) for s in range(length)])
    pad = np.pad(raw, ((3, 3), (0, 0)), mode='edge')
    smooth_ex = np.array([pad[i:i + 7].mean(axis=0) for i in range(length)])
    for sa, sb in zip(ss[:-1], ss[1:]):
        if sb - sa < 1:
            continue
        ex = smooth_ex[sa:sb]
        top = int(round(np.mean([e[0] for e in ex])))
        bot = int(round(np.mean([e[1] for e in ex])))
        if bot - top < 1:
            continue
        dep = bot - top
        xm = hx + (sa + sb) / 2.0 * dirx
        # in plan the jaw narrows to a rounded front (so, from the front, the lip hangs in a U, lowest at the middle)
        cap = math.sqrt(max(0.05, 1.0 - 0.72 * ((sa + sb) / 2.0 / length) ** 2))
        f = taper(xm) * cap

        def half_of(y, top=top, dep=dep, f=f):
            return f * front_half(F_WIDEST + ((y - top) / float(dep)) * (F_BOT - F_WIDEST))
        for ya, yb, w in merge(rows_for(top, bot, half_of)):
            add_box('jaw', 'jaw', -w / 2.0, ya, -sb, w, yb - ya, sb - sa, local=True, s=(sa, sb), row=(ya, yb), top=top, depth=dep, hw=w / 2.0)


def build_plate():
    """The mouth's inner wall: a thin plate down the middle of the open mouth, so he is not hollow when seen from the side (the
    drawing shows the red inside between the lips). It runs rim to rim, a slice at a time, in the head's frame."""
    xs = list(range(X_CORNER + 1, 137, 3)) + [137]
    for xa, xb in zip(xs[:-1], xs[1:]):
        tops, bots = [], []
        for x in range(xa, xb):
            ys = np.nonzero(GAPE[:, x])[0]
            if len(ys):
                tops.append(ys.min())
                bots.append(ys.max() + 1)
        if not tops:
            continue
        top, bot = int(round(np.mean(tops))), int(round(np.mean(bots)))
        if bot - top < 1:
            continue
        add_box('head', 'plate', -1.0, top, Zd(xb), 2, bot - top, xb - xa, slice=(xa, xb))


def surface_half(x, y, size):
    """The half width of the body's side where an eye of {size} sits at the drawing's (x, y): the narrowest row it covers."""
    z = Zd(x)
    best = 99.0
    for name in ('head', 'trunk'):
        ox, oy, oz = absolute_pivot(name)
        for b in PARTS[name].boxes:
            if b.kind not in ('head', 'trunk') or b.meta.get('overlap') or not b.z + oz <= z <= b.z + oz + b.d:
                continue
            by = b.y + oy
            if by + b.h > y - size / 2.0 and by < y + size / 2.0:
                best = min(best, b.w / 2.0)
    return best


EYE_OUT = 3
EYE_EXTRA = 1.0       # how far an eye box sinks into the head


def build_eyes():
    """Three eyes a side at the drawn places, each two crossed boxes standing out of the head's side so it reads round; they are
    painted from the drawing's discs."""
    for i, (ex, ey, r) in enumerate(bp.EYES):
        size = int(round(2 * r + 1.4))
        surf = surface_half(ex, ey, size)
        owner = 'head' if ex >= X_CORNER else 'trunk'
        for s in (1, -1):
            x = (surf - EYE_EXTRA) if s > 0 else -(surf - EYE_EXTRA) - EYE_OUT
            z0 = Zd(ex)
            add_box(owner, 'eye', x, ey - size / 2.0, z0 - (size - 2) / 2.0 - 0.0, EYE_OUT, size, size - 2, side=s, size=size, index=i, shape='tall',
                    centre=(ex, ey))
            add_box(owner, 'eye', x, ey - (size - 2) / 2.0, z0 - size / 2.0, EYE_OUT, size - 2, size, side=s, size=size, index=i, shape='wide',
                    centre=(ex, ey))


def build_spikes():
    """The back's five spikes, the front one biggest: stacked boxes, narrowing to the tip, swept back as drawn."""
    for i, (spx, h) in enumerate(bp.SPIKES):
        y = 8 + i * 1.6
        cx = bp.sx(spx)
        tri = _poly([(cx - 3, y + 1), (cx + 3, y + 1), (cx - 2, y - h)], (bp.W, bp.H))
        ys = np.nonzero(tri.any(axis=1))[0]
        part_name = 'trunk' if cx >= X_REAR else 'rear'
        rows = []
        yb = int(ys.max()) + 1
        while yb > int(ys.min()):
            ya = max(int(ys.min()), yb - 2)
            xs = np.nonzero(tri[ya:yb].any(axis=0))[0]
            rows.append((ya, yb, int(xs.min()), int(xs.max()) + 1))
            yb = ya
        for k, (ya, yb, xa, xb) in enumerate(rows):
            w = max(1, 5 - k)
            add_box(part_name, 'spike', -w / 2.0, ya, Zd(xb), w, yb - ya, xb - xa, index=i, slice=(xa, xb), row=(ya, yb))


# --- legs -------------------------------------------------------------------------------------------------------------------

# The drawn hip, knee and foot of each leg in model space (right side; the left mirrors it): the side drawing gives the
# position along him and the height, the front drawing how far out.
def _leg_points(side_leg, front_leg):
    (hip, knee, foot, wh, wk) = side_leg
    (fh, fk, ff, _, _) = front_leg
    out = []
    for sp, fp in ((hip, fh), (knee, fk), (foot, ff)):
        out.append((float(fp[0]), (sp[1] + fp[1]) / 2.0 - LIFT, Zd(sp[0])))
    return out, wh, wk


LEG_SPECS = {'front': _leg_points(bp.NEAR_LEGS[0], bf.FRONT_LEGS), 'back': _leg_points(bp.NEAR_LEGS[1], bf.BACK_LEGS)}
LEG_NAMES = [('leg_fr', 1, 'front'), ('leg_fl', -1, 'front'), ('leg_br', 1, 'back'), ('leg_bl', -1, 'back')]
UPPER_STEPS = 4
SHIN_THICK = [6.0, 5.0, 4.0, 3.0, 2.0, 1.0]     # the shin's boxes, from the knee to the claw (times SHIN_SCALE)
UPPER_ADD = 1.0         # the upper blade is this much thicker than the drawing's capsule (a box is square, a capsule round)
SHIN_SCALE = 1.4


def build_legs():
    """Each leg: a hip part (turned by the stride), the upper segment (a chain of angled boxes, hip to knee, broad), the shin
    (hip's child at the knee: a chain narrowing to a point)."""
    legs = {}
    for name, s, end in LEG_NAMES:
        (hip, knee, foot), wh, wk = LEG_SPECS[end]
        H = np.array([s * hip[0], hip[1], hip[2]])
        K = np.array([s * knee[0], knee[1], knee[2]])
        F = np.array([s * foot[0], foot[1], foot[2]])
        part(name, 'trunk', tuple(H))
        up = K - H
        ru = rotation(*along(up))
        lu = float(np.linalg.norm(up))
        part(name + '_up', name, (0.0, 0.0, 0.0), rot=euler(ru))
        sh = F - K
        rs = rotation(*along(sh))
        ls = float(np.linalg.norm(sh))
        rk = ru.T @ rs
        part(name + '_shin', name + '_up', (0.0, lu, 0.0), rot=euler(rk))
        legs[name] = (lu, ls)
        # the upper segment: boxes down its length, thickness wh -> wk
        n = UPPER_STEPS
        for i in range(n):
            y0, y1 = lu * i / n, lu * (i + 1) / n
            t = wh + UPPER_ADD + (wk - wh) * (i + 0.5) / n
            _leg_box(name + '_up', y0, y1 + 0.5 if i < n - 1 else y1, t, 'leg_up', s, i)
        # the shin: its boxes' lengths in steps, each thinner
        n = len(SHIN_THICK)
        for i in range(n):
            y0, y1 = ls * i / n, ls * (i + 1) / n
            _leg_box(name + '_shin', y0, y1 + (0.5 if i < n - 1 else 0.0), SHIN_THICK[i] * SHIN_SCALE * (wk / 6.0), 'leg_shin', s, i)
    STATS['legs'] = legs


def _leg_box(part_name, y0, y1, t, kind, s, i):
    tx, tz = max(1, int(round(t * 0.85))), max(1, int(round(t)))
    add_box(part_name, kind, -tx / 2.0, y0, -tz / 2.0, tx, y1 - y0, tz, local=True, side=s, piece=i)


def build_pectorals():
    """The small low fins out of his lower flank: a thin blade from the drawn base out and down and back to its tip, three boxes
    narrowing (fore-aft width 7, 5, 3), laid along the part's x axis and turned out, down and back."""
    for s in (1, -1):
        name = 'pectoral_r' if s > 0 else 'pectoral_l'
        A = np.array([s * 22.0, 54.0, Zd(84.0)])
        B = np.array([s * 31.0, 60.5, Zd(70.0)])
        d = B - A
        L = float(np.linalg.norm(d))
        d = d / L
        part(name, 'trunk', tuple(A), rot=(0.0, -math.asin(d[2]), math.atan2(d[1], d[0])))
        for i, (wz, th) in enumerate(((7, 2), (5, 1), (3, 1))):
            x0, x1 = L * i / 3, L * (i + 1) / 3
            add_box(name, 'pect', x0, -th / 2.0, -wz / 2.0, (x1 - x0) + (0.5 if i < 2 else 0.0), th, wz, local=True, side=s, piece=i)


# --- teeth -----------------------------------------------------------------------------------------------------------------

TOOTH = 4   # a tooth's height (pixels)


def lip_half(kind, x):
    """Half the width of the lip near {x}: the roof's bottom row (the drawing's x), or the jaw's top row ({x} the distance
    along the rim from the corner)."""
    best = None
    if kind == 'roof':
        for b in PARTS['head'].boxes:
            if b.kind == 'head' and b.meta['slice'][0] <= x < b.meta['slice'][1]:
                if best is None or b.meta['row'][1] > best[0]:
                    best = (b.meta['row'][1], b.w / 2.0)
    else:
        for b in PARTS['jaw'].boxes:
            if b.kind == 'jaw' and b.meta['row'][0] == b.meta['top'] and b.meta['s'][0] <= x < b.meta['s'][1] + 0.001:
                best = (0, b.w / 2.0)
    return best[1] if best else 12.0


def jaw_top(sv):
    """The jaw's rim height (its frame's y) near {sv} along it."""
    for b in PARTS['jaw'].boxes:
        if b.kind == 'jaw' and b.meta['s'][0] <= sv < b.meta['s'][1] + 0.001:
            return b.meta['top']
    return 0


def build_teeth(theta):
    """Rows of teeth as planes along the lips (thin boxes without width, seen from the side) and across the front of each jaw
    (seen from the front); painted from the drawings."""
    # upper lips: planes hanging from the roof, one stretch at a time
    xs = [X_CORNER + 4, 117, 128, 138]
    for s in (1, -1):
        for xa, xb in zip(xs[:-1], xs[1:]):
            top, bot = slice_extent(xa, xb)
            hw = lip_half('roof', (xa + xb) / 2.0) - 1.2
            add_box('head', 'teeth_up', s * hw, bot - 0.5, Zd(xb), 0, TOOTH + 0.5, xb - xa, side=s, slice=(xa, xb))
    jl = STATS['jaw_len']
    ss = [3, 12, 21, jl - 1]
    for s in (1, -1):
        for sa, sb in zip(ss[:-1], ss[1:]):
            hw = lip_half('jaw', (sa + sb) / 2.0) - 1.2
            tp = jaw_top((sa + sb) / 2.0)
            add_box('jaw', 'teeth_low', s * hw, tp - TOOTH, -sb, 0, TOOTH + 0.5, sb - sa, local=True, side=s, s=(sa, sb))
    # across the front of each jaw
    hw = lip_half('roof', 138) - 1.0
    top, bot = slice_extent(136, 139)
    add_box('head', 'teeth_front_up', -hw, bot - 0.5, Zd(138.5), 2 * hw, TOOTH + 0.5, 0, centre=(138.5, bot))
    hwj = lip_half('jaw', jl - 2) - 1.0
    add_box('jaw', 'teeth_front_low', -hwj, jaw_top(jl - 2) - TOOTH, -(jl - 0.5), 2 * hwj, TOOTH, 0, local=True)


# --- the pose and the part matrices -----------------------------------------------------------------------------------------

def standing(paint=False):
    """The rotations (pitch, yaw, roll) every part has when he stands still: the parts' rest rotations."""
    return {name: PARTS[name].rot for name in PARTS}


def part_matrices(pose):
    """Each part's 4x4 matrix, its own frame to model space, for the given rotations."""
    out = {}
    for name in _ORDER:
        p = PARTS[name]
        m = np.eye(4)
        m[:3, :3] = rotation(*pose[name])
        m[:3, 3] = p.pivot
        out[name] = (out[p.parent] @ m) if p.parent else m
    return out


def corners(box, matrix):
    """The eight corners of a box in model space."""
    pts = []
    for dx in (0, box.w):
        for dy in (0, box.h):
            for dz in (0, box.d):
                pts.append((box.x + dx, box.y + dy, box.z + dz, 1.0))
    return (matrix @ np.array(pts).T).T[:, :3]


# --- silhouettes -----------------------------------------------------------------------------------------------------------

def _hull(pts):
    pts = sorted(set(map(tuple, np.round(pts, 4))))
    if len(pts) < 3:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def silhouette(view, pose=None, scale=1, only=None):
    """The boxes' silhouette from the side or the front as a boolean array in the drawing's own pixels (the body layer's y,
    shifted by LIFT to the picture's), at {scale} times finer (then True where the pixel is mostly covered)."""
    pose = pose or standing()
    mats = part_matrices(pose)
    W, H = (bp.W, bp.H) if view == 'side' else (bf.W, bf.H)
    ss = 4
    img = Image.new('L', (W * ss, H * ss), 0)
    dr = ImageDraw.Draw(img)
    for b in BOXES:
        if only and not only(b):
            continue
        c = corners(b, mats[b.part])
        x, y, z = c[:, 0], c[:, 1] + LIFT, c[:, 2]
        if view == 'side':
            col, row = (XM - z) * ss, y * ss
        else:
            col, row = (CX + x) * ss, y * ss
        poly = _hull(list(zip(col, row)))
        if len(poly) >= 3:
            dr.polygon(poly, fill=255)
    a = np.array(img).reshape(H, ss, W, ss).mean(axis=(1, 3)) > 127
    return a


# --- the texture's layout and the box faces' pixels -------------------------------------------------------------------------

def pack():
    """Lays every box's faces out in the texture (shelves, tallest first); returns the texture's height. A box's corner (u, v)
    and its size give its six faces' places, as the game reads them."""
    order = sorted(BOXES, key=lambda b: (-b.footprint[1], -b.footprint[0]))
    x = y = shelf = 0
    for b in order:
        w, h = b.footprint
        if x + w > TEXTURE_W:
            x, y, shelf = 0, y + shelf, 0
        b.u, b.v = x, y
        x += w
        shelf = max(shelf, h)
    height = y + shelf
    return (int(height) + 7) // 8 * 8


def faces(b):
    """The faces of a box in the texture, as the game lays them out: name -> (x, y, w, h) of its region."""
    u, v, w, h, d = b.u, b.v, int(b.w), int(b.h), int(b.d)
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'west': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'east': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}


NORMALS = {'top': (0, -1, 0), 'bottom': (0, 1, 0), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'front': (0, 0, -1), 'back': (0, 0, 1)}


def pixel(b, face, i, j):
    """Where the texture's pixel (i, j) of {face} is on the box, in the box's own frame: the centre of the pixel on that face."""
    if face in ('top', 'bottom'):
        return (b.x + i + 0.5, b.y if face == 'top' else b.y + b.h, b.z + b.d - 0.5 - j)
    if face == 'west':
        return (b.x, b.y + j + 0.5, b.z + b.d - 0.5 - i)
    if face == 'east':
        return (b.x + b.w, b.y + j + 0.5, b.z + i + 0.5)
    if face == 'front':
        return (b.x + i + 0.5, b.y + j + 0.5, b.z)
    return (b.x + b.w - 0.5 - i, b.y + j + 0.5, b.z + b.d)


# --- the game's side: the boxes as Java ----------------------------------------------------------------------------------------

def fmt(x):
    x = round(float(x), 4)
    return ('%d' % x if x == int(x) else '%s' % x) + 'F'


def mouth_open():
    """How far the mouth hangs open in the drawing (radians): the angle between the roof's rim and the jaw's."""
    up = np.array([p for p in bp.spline(bp.UPPER_RIM) if p[0] >= X_CORNER + 2])
    d = up - np.array(bp.CORNER)
    slope = float((d[:, 0] * d[:, 1]).sum() / (d[:, 0] ** 2).sum())
    return fit_jaw() - math.atan(slope)


def java():
    height = pack()
    o = []
    o.append('package io.github.kortev.chainsawman.client;')
    o.append('')
    o.append('import net.minecraft.client.model.ModelPartBuilder;')
    o.append('import net.minecraft.client.model.ModelPartData;')
    o.append('import net.minecraft.client.model.ModelTransform;')
    o.append('')
    o.append('/**')
    o.append(" * Beam's boxes: GENERATED by {@code tools/beam_boxes.py --java}, do not edit (change the script and run it again).")
    o.append(" * The script measures the two drawings kortev approved ({@code tools/beam_profile.py}, {@code tools/beam_front.py}) and")
    o.append(' * builds him from them: slices along his body each a stack of boxes narrowing toward its top and bottom, the head and the')
    o.append(' * lower jaw hinged at the mouth\'s corner, legs of angled boxes through the drawn hip, knee and foot; it lays the texture')
    o.append(" * out for them, and {@code tools/gen_chainsaw_textures.py} ({@code beam_entity}) paints it from the same layout. The part")
    o.append(" * tree, each part's pivot and rest turn and its boxes (texture corner, then the box's corner and size) are all here;")
    o.append(' * {@link BeamModel} poses the parts.')
    o.append(' */')
    o.append('final class BeamLayout {')
    o.append('\tstatic final int TEXTURE_WIDTH = %d;' % TEXTURE_W)
    o.append('\tstatic final int TEXTURE_HEIGHT = %d;' % height)
    o.append('\t/** Pixels: how high the model\'s y = 0 (the spike tips) is above the soles of his feet. */')
    o.append('\tstatic final float HEIGHT = %s;' % fmt(HEIGHT))
    o.append("\t/** How far the mouth hangs open in the drawing (radians): the jaw's rest turn is its frame's turn and this is what it opens from shut. */")
    o.append('\tstatic final float MOUTH_OPEN = %s;' % fmt(mouth_open()))
    o.append("\t/** The jaw's rest turn (radians, its front down), as drawn. */")
    o.append('\tstatic final float JAW_REST = %s;' % fmt(STATS['jaw_theta']))
    o.append("\t/** Each leg's rest turns (pitch, yaw, roll): the upper segment, then the shin from the upper's frame; order front right, front left, back right, back left. */")
    for key, suffix in (('_up', 'UPPER_REST'), ('_shin', 'SHIN_REST')):
        rows = []
        for name, s, end in LEG_NAMES:
            r = PARTS[name + key].rot
            rows.append('{%s, %s, %s}' % (fmt(r[0]), fmt(r[1]), fmt(r[2])))
        o.append('\tstatic final float[][] %s = {%s};' % (suffix, ', '.join(rows)))
    o.append("\t/** The right pectoral fin's rest roll (radians; the left mirrors it): the animation flaps it about this. */")
    o.append('\tstatic final float PECTORAL_ROLL = %s;' % fmt(PARTS['pectoral_r'].rot[2]))
    o.append('')
    o.append('\tprivate BeamLayout() {')
    o.append('\t}')
    o.append('')
    o.append('\tstatic void build(ModelPartData root) {')
    for name in _ORDER:
        p = PARTS[name]
        parent = p.parent or 'root'
        px, py, pz = p.pivot
        if any(abs(r) > 1e-9 for r in p.rot):
            tr = 'ModelTransform.of(%s, %s, %s, %s, %s, %s)' % (fmt(px), fmt(py), fmt(pz), fmt(p.rot[0]), fmt(p.rot[1]), fmt(p.rot[2]))
        else:
            tr = 'ModelTransform.pivot(%s, %s, %s)' % (fmt(px), fmt(py), fmt(pz))
        lines = ['ModelPartBuilder.create()']
        for b in p.boxes:
            lines.append('.uv(%d, %d).cuboid(%s, %s, %s, %d, %d, %d)' % (b.u, b.v, fmt(b.x), fmt(b.y), fmt(b.z), b.w, b.h, b.d))
        if len(lines) <= 3:
            o.append('\t\tModelPartData %s = %s.addChild("%s", %s, %s);' % (name, parent, name, ''.join(lines), tr))
        else:
            o.append('\t\tModelPartData %s = %s.addChild("%s", %s' % (name, parent, name, lines[0]))
            for line in lines[1:]:
                o.append('\t\t\t\t' + line)
            o.append('\t\t\t\t, %s);' % tr)
    o.append('\t}')
    o.append('}')
    return '\n'.join(o) + '\n'


LAYOUT_JAVA = os.path.join('chainsawman', 'src', 'client', 'java', 'io', 'github', 'kortev', 'chainsawman', 'client', 'BeamLayout.java')


def main():
    theta = build()
    height = pack()
    if '--report' in sys.argv:
        print('jaw rim %.1f deg below the horizontal; mouth open %.1f deg; %d boxes in %d parts; texture %d x %d' % (
            math.degrees(theta), math.degrees(mouth_open()), len(BOXES), len(PARTS), TEXTURE_W, height))
        for p in _ORDER:
            if PARTS[p].boxes:
                print('  %-16s %3d boxes' % (p, len(PARTS[p].boxes)))
    if '--java' in sys.argv:
        with open(LAYOUT_JAVA, 'w', encoding='utf-8', newline='\n') as f:
            f.write(java())
        print('wrote', LAYOUT_JAVA)


if __name__ == '__main__':
    main()
