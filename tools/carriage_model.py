"""Builds the Child Catcher's carriage from Chitty Chitty Bang Bang (1968) in Blender.

Needs Blender's Python module, as tools/chitty_model.py does. Run from the repository root:

    python tools/carriage_model.py --out DIR --renders [--only a,b] [--horses 1|2]   # renders for a look
    python tools/carriage_model.py --game                                            # the game's mesh, texture, icon

Blender units are blocks (metres). The carriage faces +Y with +X on its right and +Z up, its wheels on the ground at
Z = 0 and the middle of its wheelbase over the origin; its horse stands in the shafts ahead of it (or a pair either side
of a pole, --horses 2). In the game the horse is a real Minecraft horse, hitched to her; the renders draw one as the
game does (its model's boxes, in the vanilla black coat if MC_HORSE_TEXTURE names it) in the harness the game puts on
it (HARNESS), and are never baked. Every part the game moves is its own object with its origin on its pivot, named as
the game knows it: the four wheels; the fore-carriage (front axle, springs, shafts or pole) that turns on its turntable
under the driver's seat, taking the front wheels (and the hitched horse) with it; and the cage door on its hinges.
Empties mark the driver's seat, the places in the cage and where the driver holds the reins.

The film's wagon, from kortev's two photos (a film still of the children screaming at its window, and one of the two
surviving carriages, in Rothenburg's crime museum; scratchpad brief): a black iron cage on a high flat dray,
straight-sided, with square corner posts and round bars each with a forged collar halfway up, a rail at the eaves and
another a little below it, and a solid roof arched across; the front wall solid weathered planks with a small window of
three bars just behind the driver and an iron strap bolted across it; a cream-framed driver's seat with a black back
at the front of the deck; four wooden wheels with iron tyres, the front ones smaller and cream like the front
running gear, the rear ones dark; shafts for one horse. The door, at the back where the museum stands its steps, is
ours: a barred gate, hinged on her right, with a hasp and a padlock.
"""
import math
import os
import sys

import bpy  # must come first: it provides bmesh and mathutils
import bmesh
import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chitty_model as cm  # noqa: E402  (materials, meshes, lathes, tubes, the render scene and the game export)

GAME = '--game' in sys.argv
HORSES = int(sys.argv[sys.argv.index('--horses') + 1]) if '--horses' in sys.argv else 1

# --- layout (blocks) ---------------------------------------------------------------------------------

# The dray: a flat planked deck, high over its wheels.
DECK_FRONT, DECK_BACK = 1.95, -1.78
DECK_HW = 0.94
DECK_TOP, DECK_T = 1.20, 0.10
# The cage on the deck, from the front wall to the back posts. H: the height of its eaves above the deck.
CAGE_FRONT, CAGE_BACK = 1.00, -1.62
CAGE_HW = 0.875
H = 2.0
POST = 0.065            # the corner posts, square
BAR_R = 0.013           # the round bars
BAR_PITCH = 0.17        # from bar to bar
COLLAR = 0.5            # how far up the bars their collars are (of H)
RAIL2 = 0.83            # the second rail, a little under the eave rail (of H)
ROOF_RISE, ROOF_OVER, ROOF_T = 0.16, 0.08, 0.04
# The front wall: twelve planks; the window over the middle four, between these heights (of H), with three bars.
PLANKS = 12
WIN_PLANKS = 4
WIN_BOT, WIN_TOP = 0.62, 0.82
STRAP_Z = 0.36          # the iron strap across it, above the deck
# The door: in the middle of the back, hinged on its left post as you face it from behind.
DOOR_W = 0.78
# Running gear: where the axles are, the wheels (radius, spokes), the track (wheel centres from the middle).
REAR_AXLE, FRONT_AXLE = -0.98, 0.95
REAR_R, FRONT_R = 0.52, 0.43
REAR_SPOKES, FRONT_SPOKES = 12, 10
TRACK = 0.74
# The fore-carriage turns about its fifth wheel, under the front of the deck.
TURNTABLE = Vector((0.0, FRONT_AXLE, 1.0))
# The driver's box at the front of the deck: where the seat is, its width, depth, height and its back's height.
SEAT_Y, SEAT_W, SEAT_D, SEAT_H, SEAT_BACK = 1.32, 1.20, 0.46, 0.40, 0.50
SEAT_TOP = DECK_TOP + SEAT_H
HANDS = Vector((0.22, SEAT_Y + 0.34, SEAT_TOP + 0.42))   # where the driver holds the reins
# The horse: a real Minecraft horse (a black one) that the game hitches into her shafts: the middle of its body HORSE_Y
# ahead of hers, its rump a little ahead of the deck; a pair (--horses 2) stand PAIR either side of a pole.
HORSE_Y = 3.08
PAIR = 0.56
# The splinter bar across the front of the fore-carriage, which the horse pulls on.
SPLINTER = Vector((0.0, FRONT_AXLE + 0.42, 0.74))

# How the game draws its horses (HorseEntityModel, adult), for the renders: boxes in its model's pixels (y down, the
# horse facing -z, x to its left), each in the frame of the part it hangs on, with its place on the coat's 64 x 64
# texture; drawn 1.1 times the model's size (HorseEntityRenderer). The frames: their pivots in the model and their pitch
# at rest (the head and tail are carried at 30 degrees).
HORSE_SCALE = 1.1
HORSE_FRAMES = {
    'body': ((0, 11, 5), 0.0), 'head': ((0, 4, -12), math.pi / 6), 'tail': ((0, 6, 7), math.pi / 6),
    'leg_hl': ((4, 14, 7), 0.0), 'leg_hr': ((-4, 14, 7), 0.0), 'leg_fl': ((4, 14, -10), 0.0), 'leg_fr': ((-4, 14, -10), 0.0)}
HORSE_BOXES = [  # frame, corner, size, texture offset
    ('body', (-5, -8, -17), (10, 10, 22), (0, 32)),
    ('head', (-2.05, -6, -2), (4, 12, 7), (0, 35)), ('head', (-3, -11, -2), (6, 5, 7), (0, 13)),
    ('head', (-1, -11, 5.01), (2, 16, 2), (56, 36)), ('head', (-2, -11, -7), (4, 5, 5), (0, 25)),
    ('head', (0.55, -13, 4), (2, 3, 1), (19, 16)), ('head', (-2.55, -13, 4), (2, 3, 1), (19, 16)),
    ('tail', (-1.5, 0, 0), (3, 14, 4), (42, 36)),
    ('leg_hl', (-3, -1.01, -1), (4, 11, 4), (48, 21)), ('leg_hr', (-1, -1.01, -1), (4, 11, 4), (48, 21)),
    ('leg_fl', (-3, -1.01, -1.9), (4, 11, 4), (48, 21)), ('leg_fr', (-1, -1.01, -1.9), (4, 11, 4), (48, 21))]
# Her harness on it, which the game draws on the hitched horse as it draws a saddle (boxes in the same frames, in
# pixels): a collar round the base of the neck with brass hames, blinkers, a browband and noseband, the bit, the black
# plume on its poll; a saddle pad with brass terrets and a girth, the tugs that carry the shafts, the crupper and the
# breeching round its quarters. A box may be turned about a pivot of its own (pitch, roll), as the plume's feathers
# are: its corner is then taken from that pivot.
HARNESS = [
    ('head', (-3.0, 1.0, -3.0), (6, 2.5, 1), 'leather'), ('head', (-3.0, 1.0, 5.0), (6, 2.5, 1), 'leather'),
    ('head', (-3.0, 1.0, -2.0), (0.95, 2.5, 7), 'leather'), ('head', (1.95, 1.0, -2.0), (1.05, 2.5, 7), 'leather'),
    ('head', (-3.4, -1.5, -3.4), (0.5, 5, 0.6), 'brass'), ('head', (2.9, -1.5, -3.4), (0.5, 5, 0.6), 'brass'),
    ('head', (-3.5, -2.3, -3.5), (0.7, 0.8, 0.8), 'brass'), ('head', (2.8, -2.3, -3.5), (0.7, 0.8, 0.8), 'brass'),
    ('head', (-3.6, -10.6, -1.8), (0.5, 3.2, 3.2), 'leather'), ('head', (3.1, -10.6, -1.8), (0.5, 3.2, 3.2), 'leather'),
    ('head', (-3.2, -11.2, -2.3), (6.4, 0.8, 0.5), 'leather'),
    ('head', (-3.4, -11.3, -2.4), (0.6, 0.9, 0.6), 'brass'), ('head', (2.8, -11.3, -2.4), (0.6, 0.9, 0.6), 'brass'),
    ('head', (-2.3, -11.3, -5.4), (4.6, 0.3, 0.8), 'leather'), ('head', (-2.3, -6.3, -5.4), (4.6, 0.3, 0.8), 'leather'),
    ('head', (-2.3, -11.0, -5.4), (0.3, 4.7, 0.8), 'leather'), ('head', (2.0, -11.0, -5.4), (0.3, 4.7, 0.8), 'leather'),
    ('head', (-2.5, -8.0, -6.5), (0.4, 1.2, 1.2), 'brass'), ('head', (2.1, -8.0, -6.5), (0.4, 1.2, 1.2), 'brass'),
    ('head', (-0.6, -12.6, 2.4), (1.2, 1.6, 1.2), 'brass'),
    ('head', (-0.8, -9.5, -1.0), (1.6, 9.0, 2.0), 'plume', ((0, -12.6, 3.0), -0.2, 0.0)),
    ('head', (-0.6, -7.5, -0.8), (1.2, 7.0, 1.6), 'plume', ((0, -12.6, 3.0), -0.1, 0.4)),
    ('head', (-0.6, -7.5, -0.8), (1.2, 7.0, 1.6), 'plume', ((0, -12.6, 3.0), -0.1, -0.4)),
    ('body', (-5.5, -8.6, -11.5), (11, 0.6, 4.5), 'leather'),
    ('body', (-5.6, -8.0, -11.0), (0.6, 5.5, 3.5), 'leather'), ('body', (5.0, -8.0, -11.0), (0.6, 5.5, 3.5), 'leather'),
    ('body', (-3.4, -9.8, -9.6), (1.0, 1.2, 0.4), 'brass'), ('body', (2.4, -9.8, -9.6), (1.0, 1.2, 0.4), 'brass'),
    ('body', (-5.45, -2.5, -10.0), (0.45, 4.5, 1.5), 'leather'), ('body', (5.0, -2.5, -10.0), (0.45, 4.5, 1.5), 'leather'),
    ('body', (-5.5, 2.0, -10.0), (11, 0.5, 1.5), 'leather'),
    ('body', (-7.6, -3.4, -10.4), (2.6, 1.0, 2.2), 'leather'), ('body', (5.0, -3.4, -10.4), (2.6, 1.0, 2.2), 'leather'),
    ('body', (-0.5, -8.45, -7.0), (1.0, 0.45, 12.0), 'leather'),
    ('body', (-5.45, -3.5, -2.0), (0.45, 1.0, 7.4), 'leather'), ('body', (5.0, -3.5, -2.0), (0.45, 1.0, 7.4), 'leather'),
    ('body', (-5.45, -3.5, 5.0), (10.9, 1.0, 0.45), 'leather'),
    ('body', (-5.45, -8.5, 0.5), (10.9, 0.45, 1.0), 'leather'),
    ('body', (-5.45, -8.5, 0.5), (0.45, 5.0, 1.0), 'leather'), ('body', (5.0, -8.5, 0.5), (0.45, 5.0, 1.0), 'leather')]
# Where the traces leave the collar and the reins the bit (head frame), and the terrets they run through (body frame).
TRACE_FROM = (3.0, 2.2, 0.5)
BIT = (2.4, -7.4, -6.0)
TERRET = (2.9, -9.2, -9.4)
# The vanilla coat for the renders (not part of the mod: the game draws its own): MC_HORSE_TEXTURE, or a plain black.
HORSE_TEXTURE = os.environ.get('MC_HORSE_TEXTURE', '')


def horse_at(i):
    """Where horse i stands (the ground under the middle of its body)."""
    if HORSES == 1:
        return Vector((0.0, HORSE_Y, 0.0))
    return Vector(((PAIR if i == 0 else -PAIR), HORSE_Y, 0.0))


def model_rx(p, angle):
    """A point in a horse model's pixels turned by pitch `angle` about its x (y down, -z ahead: + tips forward)."""
    x, y, z = p
    c, s = math.cos(angle), math.sin(angle)
    return (x, y * c - z * s, y * s + z * c)


def model_rz(p, angle):
    x, y, z = p
    c, s = math.cos(angle), math.sin(angle)
    return (x * c - y * s, x * s + y * c, z)


def horse_point(at, frame, p, pitches):
    """Where a point (pixels, in one of the horse's frames) is in Blender, for the horse standing at `at`."""
    pivot, _ = HORSE_FRAMES[frame]
    q = model_rx(p, pitches[frame])
    if frame == 'tail':
        q = model_rx(q, pitches['body'])
    m = (pivot[0] + q[0], pivot[1] + q[1], pivot[2] + q[2])
    k = HORSE_SCALE / 16
    return at + Vector((-m[0] * k, -m[2] * k, HORSE_SCALE * 1.501 - m[1] * k))


def horse_box(m, at, frame, corner, size, pitches, mat, uv=None, turn=None):
    """One of a horse model's boxes, with the game's own layout of its faces on the texture (ModelPart.Cuboid)."""
    x0, y0, z0 = corner
    sx, sy, sz = size
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz

    def place(p):
        if turn:
            # A turned box is laid out from its own pivot (its corner taken from there), then turned about it.
            (px, py, pz), pitch, roll = turn
            q = model_rz(model_rx(p, pitch), roll)
            p = (q[0] + px, q[1] + py, q[2] + pz)
        return m.vert(horse_point(at, frame, p, pitches))
    v1, v2, v3, v4 = place((x0, y0, z0)), place((x1, y0, z0)), place((x1, y1, z0)), place((x0, y1, z0))
    v5, v6, v7, v8 = place((x0, y0, z1)), place((x1, y0, z1)), place((x1, y1, z1)), place((x0, y1, z1))
    faces = [[v6, v5, v1, v2], [v3, v4, v8, v7], [v1, v5, v8, v4], [v2, v1, v4, v3], [v6, v2, v3, v7], [v5, v6, v7, v8]]
    if uv is None:
        for f in faces:
            m.face(f, mat)
        return
    u, v = uv
    j, k, l, mm, n, o = u, u + sz, u + sz + sx, u + sz + 2 * sx, u + 2 * sz + sx, u + 2 * sz + 2 * sx
    p_, q, r = v, v + sz, v + sz + sy
    rects = [(k, p_, l, q), (l, q, mm, p_), (j, q, k, r), (k, q, l, r), (l, q, n, r), (n, q, o, r)]
    for f, (u1, v1_, u2, v2_) in zip(faces, rects):
        corners = [(u2, v1_), (u1, v1_), (u1, v2_), (u2, v2_)]
        m.face(f, mat, [(cu / 64, 1 - cv / 64) for cu, cv in corners])


# --- textures ------------------------------------------------------------------------------------------

def board_texture(color, seed=3, size=512, weathered=True):
    """Plank: its grain running up the texture (v), weathered silver with dark cracks along it, or stained."""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:size, 0:size] / size
    base = np.array(color) / 255
    phase = rng.random() * 20
    grain = np.sin((xx * 38 + np.sin(yy * 5 + phase) * 0.8) * math.pi) * 0.5 + 0.5
    grain = grain ** 4 * (0.24 if weathered else 0.15)
    fine = rng.normal(0, 0.035, (size, size))
    streak = rng.normal(0, 1, (1, size)).repeat(size, 0) * 0.04
    col = base[None, None, :] * (1.0 - grain + fine + streak)[..., None]
    if weathered:
        crack = (np.abs(((xx * 11 + np.sin(yy * 9) * 0.05) % 1.0) - 0.5) < 0.006) & (np.sin(yy * 13 + xx * 7) > 0.2)
        col[crack] *= 0.45
    out = np.ones((size, size, 4))
    out[..., :3] = np.clip(col, 0, 1)
    return out


# --- materials -------------------------------------------------------------------------------------------

def make_materials():
    m = cm.material
    m('iron', (30, 30, 33), metal=0.7, rough=0.55)
    m('roof', (36, 36, 38), rough=0.7)
    m('boards', (158, 152, 140), rough=0.9, image=cm.image('boards', board_texture((158, 152, 140), seed=4)))
    m('boards_in', (86, 56, 34), rough=0.8, image=cm.image('boards_in', board_texture((86, 56, 34), 7, weathered=False)))
    m('deck', (62, 50, 38), rough=0.8, image=cm.image('deck', board_texture((62, 50, 38), 9, weathered=False)))
    m('cream', (226, 214, 172), rough=0.45, coat=0.2)
    m('dark_paint', (40, 35, 32), rough=0.5, coat=0.2)
    m('black', (21, 21, 23), rough=0.45, coat=0.25)
    m('tyre', (46, 46, 48), metal=0.6, rough=0.5)
    m('leather', (22, 20, 19), rough=0.55, coat=0.1)
    m('cushion', (32, 27, 25), rough=0.7)
    # The vanilla horse's coat (renders only), drawn pixelated as the game draws it; a plain black without it.
    if HORSE_TEXTURE and os.path.exists(HORSE_TEXTURE):
        from PIL import Image
        rgba = np.asarray(Image.open(HORSE_TEXTURE).convert('RGBA'), dtype=np.float64) / 255.0
        m('horse_coat', (255, 255, 255), rough=0.6, image=cm.image('horse_coat', rgba))
    else:
        m('horse_coat', (24, 22, 22), rough=0.6)
    m('mane', (10, 10, 11), rough=0.9)
    m('hoof', (52, 48, 44), rough=0.7)
    m('muzzle', (46, 42, 42), rough=0.6)
    m('eye', (6, 6, 8), rough=0.08, coat=1.0)
    m('brass', (224, 174, 72), metal=1.0, rough=0.25)
    m('plume', (14, 14, 16), rough=0.95)
    m('whip', (74, 50, 30), rough=0.6)


# --- helpers ---------------------------------------------------------------------------------------------

def moving(m, name, pivot, coll='body', smooth=40):
    """A part the game moves, built where it stands: its origin moved to its pivot."""
    pivot = Vector(pivot)
    bmesh.ops.translate(m.bm, vec=-pivot, verts=list(m.bm.verts))
    o = m.obj(name, coll=coll, location=pivot, smooth=smooth, part=name)
    o['rest'] = tuple(pivot)
    return o


def placed(m, matrix, build):
    """Builds with build(m), then moves what it made by matrix (a plume tilted, say)."""
    m.bm.verts.ensure_lookup_table()
    first = len(m.bm.verts)
    build(m)
    m.bm.verts.ensure_lookup_table()
    bmesh.ops.transform(m.bm, matrix=matrix, verts=[m.bm.verts[i] for i in range(first, len(m.bm.verts))])


def limb(m, a, b, profile, mat, side=Vector((1.0, 0.0, 0.0)), n=2.6, count=16):
    """A rounded limb from a to b: rings of rounded sections ((t, half-width across, half-depth), t from 0 at a to 1
    at b), capped at both ends (faceted, six round, a flat on top)."""
    a, b = Vector(a), Vector(b)
    d = (b - a).normalized()
    u = (side - d * side.dot(d)).normalized()
    v = d.cross(u)
    count = 6 if cm.FACET else count
    turn = math.pi / count if cm.FACET else 0.0
    rings = []
    for t, hw, hd in profile:
        c = a + (b - a) * t
        ring = []
        for k in range(count):
            phi = turn + 2 * math.pi * k / count
            cs, sn = math.cos(phi), math.sin(phi)
            ring.append(m.vert(c + u * (hw * math.copysign(abs(cs) ** (2 / n), cs)) +
                               v * (hd * math.copysign(abs(sn) ** (2 / n), sn))))
        rings.append(ring)
    for i in range(len(rings) - 1):
        for k in range(count):
            j = (k + 1) % count
            m.face([rings[i][k], rings[i][j], rings[i + 1][j], rings[i + 1][k]], mat)
    m.face(list(reversed(rings[0])), mat)
    m.face(rings[-1], mat)


def strip(m, a, b, width, thick, mat, up=Vector((0.0, 0.0, 1.0))):
    """A flat bar or strap from a to b, `width` across (with `up` lying along its width) and `thick` through."""
    a, b = Vector(a), Vector(b)
    d = b - a
    length = d.length
    d.normalize()
    w = (up - d * up.dot(d)).normalized()
    t = d.cross(w)
    rot = Matrix((t, d, w)).transposed().to_4x4()
    cm.add_box(m, (0, 0, 0), (thick, length, width), mat, matrix=Matrix.Translation((a + b) / 2) @ rot)


def leaf_spring(m, x, y, z0, z1, length, mat):
    """An elliptic spring: two bows of leaves meeting at their eyes, the lower on the axle at z0, the upper under the
    body at z1."""
    zm = (z0 + z1) / 2
    steps = cm.res(10, least=4, div=2)
    for z_end, sign in ((z0, -1), (z1, 1)):
        pts = []
        for k in range(steps + 1):
            th = math.pi * k / steps
            pts.append(Vector((x, y - length / 2 * math.cos(th), zm + sign * (abs(z_end - zm)) * math.sin(th))))
        for p, q in zip(pts, pts[1:]):
            strip(m, p, q, 0.06, 0.03, mat, up=Vector((1.0, 0.0, 0.0)))
    for z in (z0, z1):
        cm.add_box(m, (x, y, z), (0.08, 0.06, 0.05), 'iron')


def wheel_mesh(radius, spokes, paint, side):
    """A carriage wheel round the X axis: an iron tyre on a wooden felloe, `spokes` spokes, and a turned hub with an
    iron band and cap, standing out on the outer side (side: +1 her right, -1 her left)."""
    m = cm.Mesh()
    tw, fw = 0.06, 0.055
    r_in = radius - 0.035
    cm.lathe(m, [(r_in, -tw / 2), (radius, -tw / 2), (radius, tw / 2), (r_in, tw / 2), (r_in, -tw / 2)],
             lambda k: 'tyre', axis='x', seg=48)
    r0 = r_in - 0.07
    cm.lathe(m, [(r0, -fw / 2), (r_in, -fw / 2), (r_in, fw / 2), (r0, fw / 2), (r0, -fw / 2)],
             lambda k: paint, axis='x', seg=48)
    hub = 0.085
    for k in range(spokes):
        a = 2 * math.pi * k / spokes
        cm.add_box(m, (0, 0, (hub + r0) / 2), (0.034, 0.03, r0 - hub + 0.03), paint, matrix=Matrix.Rotation(a, 4, 'X'))
    o = side
    cm.lathe(m, [(0.0, -0.10 * o), (0.07, -0.10 * o), (0.095, -0.05 * o), (0.095, 0.05 * o), (0.085, 0.09 * o),
                 (0.055, 0.14 * o), (0.0, 0.15 * o)], lambda k: paint, axis='x', seg=24)
    cm.lathe(m, [(0.098, 0.035 * o), (0.098, 0.06 * o)], lambda k: 'iron', axis='x', seg=24)
    cm.lathe(m, [(0.0, 0.142 * o), (0.045, 0.142 * o), (0.04, 0.165 * o), (0.0, 0.17 * o)], lambda k: 'iron', axis='x',
             seg=16)
    return m


# --- the dray ------------------------------------------------------------------------------------------

def build_deck():
    """The flat planked deck, an iron band round its edge, on bearers lengthwise and across (kept clear of the
    wheels), with the rear springs and axle under it and an iron step at the back."""
    m = cm.Mesh()
    yc, length = (DECK_FRONT + DECK_BACK) / 2, DECK_FRONT - DECK_BACK
    n = 9
    w = 2 * DECK_HW / n
    for i in range(n):
        cm.add_box(m, (-DECK_HW + w * (i + 0.5), yc, DECK_TOP - DECK_T / 2), (w - 0.008, length, DECK_T), 'deck')
    for s in (-1, 1):
        cm.add_box(m, (s * (DECK_HW + 0.012), yc, DECK_TOP - 0.05), (0.024, length + 0.048, 0.1), 'iron')
        cm.add_box(m, (s * 0.55, yc, DECK_TOP - DECK_T - 0.06), (0.1, length - 0.1, 0.12), 'dark_paint')
    for y in (DECK_FRONT + 0.012, DECK_BACK - 0.012):
        cm.add_box(m, (0, y, DECK_TOP - 0.05), (2 * DECK_HW, 0.024, 0.1), 'iron')
    for y in np.linspace(DECK_BACK + 0.2, DECK_FRONT - 0.25, 5):
        cm.add_box(m, (0, y, DECK_TOP - DECK_T - 0.05), (1.2, 0.08, 0.08), 'dark_paint')
    # The upper half of the fifth wheel, under the front of the deck.
    cm.add_box(m, (0, TURNTABLE.y, TURNTABLE.z + 0.05), (1.1, 0.14, 0.06), 'dark_paint')
    # The rear axle on its bed, and an elliptic spring each side up to the bearers.
    cm.add_box(m, (0, REAR_AXLE, REAR_R), (2 * TRACK - 0.14, 0.07, 0.07), 'iron')
    cm.add_box(m, (0, REAR_AXLE, REAR_R + 0.08), (2 * TRACK - 0.28, 0.1, 0.09), 'dark_paint')
    for s in (-1, 1):
        leaf_spring(m, s * 0.55, REAR_AXLE, REAR_R + 0.125, DECK_TOP - DECK_T - 0.12, 0.8, 'iron')
    # An iron step under the back, to put someone up into the cage.
    for s in (-1, 1):
        strip(m, (s * 0.16, DECK_BACK - 0.02, DECK_TOP - 0.1), (s * 0.16, DECK_BACK - 0.08, 0.66), 0.04, 0.012, 'iron',
              up=Vector((1, 0, 0)))
    cm.add_box(m, (0, DECK_BACK - 0.09, 0.65), (0.38, 0.14, 0.02), 'iron')
    m.obj('deck', smooth=None)


def build_wheels():
    for name, side, y, r, spokes, paint in (
            ('wheel_rr', 1, REAR_AXLE, REAR_R, REAR_SPOKES, 'dark_paint'),
            ('wheel_rl', -1, REAR_AXLE, REAR_R, REAR_SPOKES, 'dark_paint'),
            ('wheel_fr', 1, FRONT_AXLE, FRONT_R, FRONT_SPOKES, 'cream'),
            ('wheel_fl', -1, FRONT_AXLE, FRONT_R, FRONT_SPOKES, 'cream')):
        o = wheel_mesh(r, spokes, paint, side).obj(name, coll='wheels', location=(side * TRACK, y, r), smooth=40, part=name)
        o['rest'] = (side * TRACK, y, r)


def shaft_path(s):
    """One of the shafts (s: +1 her right, -1 her left), from the splinter bar forward along the horse's side to just
    past its shoulder, a little bowed out and turned in at the tip."""
    return cm.catmull([(s * 0.58, SPLINTER.y, SPLINTER.z), (s * 0.58, SPLINTER.y + 0.55, 0.86),
                       (s * 0.56, HORSE_Y - 0.4, 1.06), (s * 0.53, HORSE_Y + 0.55, 1.1), (s * 0.47, HORSE_Y + 0.95, 1.1)], 6)


def build_fore_carriage():
    """The front axle on its bed and springs, the bolster and the lower half of the fifth wheel it turns on, the splinter
    bar and swingletree(s), and the shafts for one horse (or the pole between a pair): all turning together on the
    turntable (with the front wheels and the horses, which are their own parts)."""
    m = cm.Mesh()
    cm.add_box(m, (0, FRONT_AXLE, FRONT_R), (2 * TRACK - 0.14, 0.07, 0.07), 'iron')
    cm.add_box(m, (0, FRONT_AXLE, FRONT_R + 0.08), (2 * TRACK - 0.28, 0.1, 0.09), 'cream')
    for s in (-1, 1):
        leaf_spring(m, s * 0.45, FRONT_AXLE, FRONT_R + 0.125, TURNTABLE.z - 0.1, 0.7, 'cream')
    cm.add_box(m, (0, FRONT_AXLE, TURNTABLE.z - 0.07), (1.3, 0.12, 0.07), 'cream')
    ring = [Vector((0.34 * math.cos(a), FRONT_AXLE + 0.34 * math.sin(a), TURNTABLE.z - 0.02))
            for a in np.linspace(0, 2 * math.pi, cm.res(24, least=8, div=3) + 1)]
    cm.tube(m, ring, 0.02, 'iron', seg=8)
    # The perch from the bed forward to the splinter bar.
    for s in (-1, 1):
        strip(m, (s * 0.3, FRONT_AXLE, FRONT_R + 0.08), (s * 0.45, SPLINTER.y, SPLINTER.z), 0.07, 0.06, 'cream')
    if HORSES == 1:
        cm.add_box(m, SPLINTER, (1.24, 0.07, 0.07), 'black')
        cm.add_box(m, SPLINTER + Vector((0, 0.07, 0.0)), (0.62, 0.05, 0.05), 'black')
        for s in (-1, 1):
            cm.tube(m, shaft_path(s), 0.035, 'black', seg=10)
            # The tug stop and a brass tip.
            tip = shaft_path(s)[-1]
            cm.ellipsoid(m, tip, (0.04, 0.04, 0.04), 'brass', seg=10, rings=6)
    else:
        cm.add_box(m, SPLINTER, (1.9, 0.07, 0.07), 'black')
        for s in (-1, 1):
            cm.add_box(m, SPLINTER + Vector((s * PAIR, 0.07, 0.0)), (0.62, 0.05, 0.05), 'black')
        pole = cm.catmull([(0, FRONT_AXLE + 0.3, 0.70), (0, SPLINTER.y + 0.6, 0.86), (0, HORSE_Y, 1.12),
                           (0, HORSE_Y + 1.02, 1.2)], 6)
        cm.tube(m, pole, 0.045, 'black', seg=10)
        cm.lathe(m, [(0.0, 0.0), (0.06, 0.0), (0.055, 0.08), (0.0, 0.1)], lambda k: 'brass', axis='y', seg=12,
                 origin=tuple(pole[-1]))
    moving(m, 'fore_carriage', TURNTABLE)


# --- the cage ---------------------------------------------------------------------------------------------

def bar(m, x, y, z0, z1):
    """A round bar from z0 up to z1, with its forged collar."""
    cm.tube(m, [Vector((x, y, z0)), Vector((x, y, z1))], BAR_R, 'iron', seg=10)
    zc = DECK_TOP + COLLAR * H
    cm.lathe(m, [(BAR_R + 0.003, zc - 0.045), (BAR_R + 0.012, zc - 0.018), (BAR_R + 0.012, zc + 0.018),
                 (BAR_R + 0.003, zc + 0.045)], lambda k: 'iron', axis='z', seg=12, origin=(x, y, 0.0))


def build_cage():
    """The iron cage on the deck: its corner posts, sills and rails, its barred sides and back (the door in the middle
    of the back is its own part), the arched roof, and the planked front wall with its barred window, iron strap and
    latch."""
    m = cm.Mesh()
    z0, z1 = DECK_TOP, DECK_TOP + H
    xp = CAGE_HW - POST / 2
    for x in (-xp, xp):
        for y in (CAGE_FRONT - POST / 2, CAGE_BACK + POST / 2):
            cm.add_box(m, (x, y, (z0 + z1) / 2), (POST, POST, H), 'iron')
    length = CAGE_FRONT - CAGE_BACK
    yc = (CAGE_FRONT + CAGE_BACK) / 2
    for s in (-1, 1):
        cm.add_box(m, (s * xp, yc, z0 + 0.025), (0.05, length, 0.05), 'iron')
        cm.add_box(m, (s * xp, yc, z1 - 0.02), (0.06, length, 0.04), 'iron')
        cm.add_box(m, (s * xp, yc, z0 + RAIL2 * H), (0.018, length, 0.045), 'iron')
    for y in (CAGE_FRONT - POST / 2, CAGE_BACK + POST / 2):
        cm.add_box(m, (0, y, z1 - 0.02), (2 * CAGE_HW, 0.06, 0.04), 'iron')
    cm.add_box(m, (0, CAGE_BACK + POST / 2, z0 + 0.025), (2 * CAGE_HW, 0.05, 0.05), 'iron')
    # The bars down each side.
    span = length - 2 * POST
    n = int(round(span / BAR_PITCH)) - 1
    for s in (-1, 1):
        for y in np.linspace(CAGE_BACK + POST, CAGE_FRONT - POST, n + 2)[1:-1]:
            bar(m, s * xp, y, z0 + 0.05, z1 - 0.04)
    # The back: two bars either side of the door, between its jambs and the posts.
    yb = CAGE_BACK + POST / 2
    for s in (-1, 1):
        cm.add_box(m, (s * (DOOR_W / 2 + 0.025), yb, (z0 + z1) / 2), (0.05, 0.04, H), 'iron')
        cm.add_box(m, (s * (DOOR_W / 2 + CAGE_HW) / 2, yb, z0 + RAIL2 * H), (CAGE_HW - DOOR_W / 2, 0.018, 0.045), 'iron')
        for x in np.linspace(DOOR_W / 2 + 0.05, CAGE_HW - POST, 4)[1:-1]:
            bar(m, s * x, yb, z0 + 0.05, z1 - 0.04)
    # The roof, arched across, on the eave rail; the gables under its arch closed at each end.
    w = CAGE_HW + ROOF_OVER
    across = cm.res(16, least=6, div=2)
    xs = np.linspace(-w, w, across + 1)
    arch = [(x, z1 + ROOF_RISE * (1 - (x / w) ** 2)) for x in xs]
    section = [(x, z + ROOF_T) for x, z in arch] + [(x, z) for x, z in reversed(arch)]
    cm.loft(m, [(CAGE_BACK - ROOF_OVER, section), (CAGE_FRONT + ROOF_OVER, section)], 'roof', cap_start='roof',
            cap_end='roof')
    for y in (CAGE_FRONT - 0.01, CAGE_BACK + 0.01):
        gable = [m.vert((x, y, z)) for x, z in arch if abs(x) <= CAGE_HW] + [m.vert((CAGE_HW, y, z1)), m.vert((-CAGE_HW, y, z1))]
        m.face(gable, 'roof')
    # The front wall: weathered planks outside, dark inside, the window over the middle ones.
    wall = 2 * (CAGE_HW - POST)
    pw = wall / PLANKS
    yo, yi = CAGE_FRONT - 0.014, CAGE_FRONT - 0.038
    lo, hi = z0 + WIN_BOT * H, z0 + WIN_TOP * H
    first = (PLANKS - WIN_PLANKS) // 2
    for k in range(PLANKS):
        x = -wall / 2 + pw * (k + 0.5)
        runs = [(z0, z1 - 0.04)] if not first <= k < first + WIN_PLANKS else [(z0, lo), (hi, z1 - 0.04)]
        for a, b in runs:
            cm.add_box(m, (x, yo, (a + b) / 2), (pw - 0.006, 0.022, b - a), 'boards')
            cm.add_box(m, (x, yi, (a + b) / 2), (pw - 0.004, 0.022, b - a), 'boards_in')
    # The window: an iron frame round it, three bars.
    ww = WIN_PLANKS * pw
    yf = CAGE_FRONT + 0.004
    for z in (lo, hi):
        cm.add_box(m, (0, yf, z), (ww + 0.09, 0.02, 0.045), 'iron')
    for s in (-1, 1):
        cm.add_box(m, (s * (ww / 2 + 0.0225), yf, (lo + hi) / 2), (0.045, 0.02, hi - lo + 0.09), 'iron')
    for x in np.linspace(-ww / 2, ww / 2, 5)[1:-1]:
        cm.tube(m, [Vector((x, yo, lo - 0.02)), Vector((x, yo, hi + 0.02))], 0.012, 'iron', seg=10)
    # The iron strap bolted across it, and the latch rod with its domed top.
    zs = z0 + STRAP_Z
    cm.add_box(m, (0, CAGE_FRONT + 0.002, zs), (wall, 0.012, 0.07), 'iron')
    for x in np.arange(-wall / 2 + 0.06, wall / 2 - 0.03, 0.17):
        cm.ellipsoid(m, (x, CAGE_FRONT + 0.009, zs), (0.014, 0.008, 0.014), 'iron', seg=10, rings=6)
    cm.tube(m, [Vector((0.55, CAGE_FRONT + 0.03, zs - 0.14)), Vector((0.55, CAGE_FRONT + 0.03, zs + 0.42))], 0.012,
            'iron', seg=10)
    cm.ellipsoid(m, (0.55, CAGE_FRONT + 0.03, zs + 0.44), (0.026, 0.026, 0.026), 'iron', seg=12, rings=8)
    for z in (zs - 0.08, zs + 0.3):
        cm.add_box(m, (0.55, CAGE_FRONT + 0.016, z), (0.05, 0.03, 0.02), 'iron')
    m.obj('cage', smooth=40)


def build_door():
    """The cage's door: a barred gate in the back, hinged on her right (on your right as you face it from behind),
    swinging out; a hasp and a padlock on its other side."""
    m = cm.Mesh()
    z0, z1 = DECK_TOP + 0.05, DECK_TOP + H - 0.05
    y = CAGE_BACK + POST / 2 - 0.03
    hx = DOOR_W / 2 - 0.01          # the hinge side (her right, +x)
    for x in (hx - 0.02, -hx + 0.02):
        cm.add_box(m, (x, y, (z0 + z1) / 2), (0.04, 0.03, z1 - z0), 'iron')
    for z in (z0 + 0.02, z1 - 0.02, DECK_TOP + RAIL2 * H, DECK_TOP + 0.45 * H):
        cm.add_box(m, (0, y, z), (DOOR_W - 0.02, 0.025, 0.04), 'iron')
    for x in np.linspace(-hx, hx, 5)[1:-1]:
        bar(m, x, y, z0, z1)
    # Hinges: straps along the gate from knuckles on the post.
    for z in (z0 + 0.25, z1 - 0.3):
        cm.add_box(m, (hx - 0.12, y - 0.02, z), (0.26, 0.012, 0.05), 'iron')
        cm.lathe(m, [(0.022, -0.06), (0.022, 0.06)], lambda k: 'iron', axis='z', seg=10, origin=(hx + 0.012, y, z))
    # The hasp over the jamb, and the padlock through its staple.
    zl = DECK_TOP + 0.95
    cm.add_box(m, (-hx - 0.02, y - 0.022, zl), (0.12, 0.012, 0.05), 'iron')
    cm.add_box(m, (-hx - 0.05, y - 0.075, zl - 0.07), (0.09, 0.04, 0.10), 'iron')
    shackle = [Vector((-hx - 0.08, y - 0.075, zl - 0.02)), Vector((-hx - 0.08, y - 0.075, zl + 0.03)),
               Vector((-hx - 0.05, y - 0.075, zl + 0.06)), Vector((-hx - 0.02, y - 0.075, zl + 0.03)),
               Vector((-hx - 0.02, y - 0.075, zl - 0.02))]
    cm.tube(m, shackle, 0.008, 'iron', seg=8)
    cm.ellipsoid(m, (-hx - 0.05, y - 0.096, zl - 0.08), (0.012, 0.004, 0.018), 'brass', seg=8, rings=4)
    moving(m, 'door', (hx + 0.012, y, DECK_TOP))


# --- the driver's box -----------------------------------------------------------------------------------

def build_seat():
    """The driver's seat at the front of the deck: a black box with a cream frame, a black cushion and back, cream arms
    sweeping down from its back; a sloped footboard at the front edge of the deck; a whip in its socket."""
    m = cm.Mesh()
    y0, y1 = SEAT_Y - SEAT_D / 2, SEAT_Y + SEAT_D / 2
    cm.add_box(m, (0, SEAT_Y, DECK_TOP + SEAT_H / 2), (SEAT_W, SEAT_D, SEAT_H), 'black')
    for s in (-1, 1):
        for y in (y0, y1):
            cm.add_box(m, (s * SEAT_W / 2, y, DECK_TOP + SEAT_H / 2), (0.04, 0.04, SEAT_H), 'cream')
    for y in (y0, y1):
        cm.add_box(m, (0, y, SEAT_TOP - 0.02), (SEAT_W + 0.02, 0.04, 0.04), 'cream')
    cm.add_box(m, (0, SEAT_Y + 0.02, SEAT_TOP + 0.04), (SEAT_W - 0.08, SEAT_D - 0.06, 0.08), 'cushion')
    back = Matrix.Translation((0, y0 + 0.02, SEAT_TOP + SEAT_BACK / 2)) @ Matrix.Rotation(math.radians(8), 4, 'X')
    cm.add_box(m, (0, 0, 0), (SEAT_W - 0.04, 0.04, SEAT_BACK), 'black', matrix=back)
    cm.add_box(m, (0, 0, SEAT_BACK / 2), (SEAT_W + 0.02, 0.05, 0.04), 'cream', matrix=back)
    for s in (-1, 1):
        cm.tube(m, cm.catmull([(s * SEAT_W / 2, y0 + 0.08, SEAT_TOP + SEAT_BACK - 0.02), (s * SEAT_W / 2, y0 + 0.12, SEAT_TOP + 0.30),
                               (s * SEAT_W / 2, SEAT_Y + 0.05, SEAT_TOP + 0.17), (s * SEAT_W / 2, y1 - 0.02, SEAT_TOP + 0.10)], 6),
                0.022, 'cream', seg=10)
    foot = Matrix.Translation((0, DECK_FRONT - 0.1, DECK_TOP + 0.15)) @ Matrix.Rotation(math.radians(28), 4, 'X')
    cm.add_box(m, (0, 0, 0), (SEAT_W + 0.1, 0.03, 0.36), 'black', matrix=foot)
    cm.add_box(m, (0, 0, 0.18), (SEAT_W + 0.14, 0.04, 0.03), 'cream', matrix=foot)
    # The whip in its socket at the driver's right.
    sock = Vector((SEAT_W / 2 + 0.04, y1 - 0.04, SEAT_TOP - 0.12))
    cm.tube(m, [sock, sock + Vector((0, 0, 0.16))], 0.022, 'cream', seg=10)
    top = sock + Vector((0.12, -0.75, 1.35))
    cm.tube(m, [sock + Vector((0, 0, -0.04)), top], 0.011, 'whip', seg=8)
    cm.tube(m, cm.catmull([tuple(top), tuple(top + Vector((0.05, -0.25, 0.05))), tuple(top + Vector((0.1, -0.45, -0.2))),
                           tuple(top + Vector((0.12, -0.5, -0.55)))], 4), 0.005, 'leather', seg=6)
    m.obj('seat', smooth=40)


# --- the horses -----------------------------------------------------------------------------------------

def build_horse_preview(i, gait=0.0, phase=0.0, steer=0.0):
    """For the renders: horse i as the game draws it (the vanilla model in its black coat), in her harness, its legs at
    that point of their stride (the vanilla walk: limb angle `phase`, limb distance `gait`), the traces back to the
    splinter bar and the reins to the driver's hands; turned with the fore-carriage by steer."""
    name = 'horse_preview_%d' % i
    if name in bpy.data.objects:
        bpy.data.objects.remove(bpy.data.objects[name])
    at = horse_at(i)
    t = math.cos(phase * 0.6662 + math.pi)
    pitches = {'body': 0.0, 'head': math.pi / 6 + math.cos(phase * 0.8) * 0.15 * gait * (gait > 0.2),
               'tail': math.pi / 6 + gait * 0.75,
               'leg_hl': -t * 0.5 * gait, 'leg_hr': t * 0.5 * gait, 'leg_fl': t * 0.8 * gait, 'leg_fr': -t * 0.8 * gait}
    m = cm.Mesh()
    for frame, corner, size, uv in HORSE_BOXES:
        horse_box(m, at, frame, corner, size, pitches, 'horse_coat', uv)
    for entry in HARNESS:
        frame, corner, size, mat = entry[:4]
        horse_box(m, at, frame, corner, size, pitches, mat, turn=entry[4] if len(entry) > 4 else None)
    # The traces from the collar back to the swingletree; the reins from the bit, through the terrets, to the driver.
    tree = SPLINTER + Vector((at.x, 0.07, 0.0))
    for s in (-1, 1):
        start = horse_point(at, 'head', (s * TRACE_FROM[0], TRACE_FROM[1], TRACE_FROM[2]), pitches)
        mid = horse_point(at, 'body', (s * 5.4, -3.0, -10.0), pitches)
        cm.tube(m, [start, mid, tree + Vector((-s * 0.29, 0.0, 0.0))], 0.018, 'leather', seg=8)
        bit = horse_point(at, 'head', (s * BIT[0], BIT[1], BIT[2]), pitches)
        terret = horse_point(at, 'body', (s * TERRET[0], TERRET[1], TERRET[2]), pitches)
        hand = HANDS + Vector((-s * 0.03, 0.0, 0.0))
        cm.tube(m, cm.catmull([tuple(bit), tuple(terret), tuple((terret + hand) * 0.5 - Vector((0, 0, 0.12))), tuple(hand)], 4),
                0.008, 'leather', seg=6)
    o = m.obj(name, coll='preview', smooth=None)
    del o['part']       # the renders only: never baked (the game draws its own horse)
    turn = Matrix.Translation(TURNTABLE) @ Matrix.Rotation(math.radians(22) * steer, 4, 'Z') @ Matrix.Translation(-TURNTABLE)
    o.matrix_world = turn
    return o


# --- the sweet-cart disguise ---------------------------------------------------------------------------------

# The film's capture scene: the cage dressed up as a cart giving sweets away, which all falls off as he cracks the whip
# and drives away (no still of it could be found: this is ours, after his cries in the film). Painted boards over the
# bars of each side and of the back, a sign along each side between the rails, a striped valance round the eaves, and
# great lollipops on the roof. Each board, sign, the valance and each lollipop is its own part, which the game throws
# off one by one.
DISGUISE_PANELS = {1: ('LOLLIPOPS', 'TREACLE TARTS', 'ICE CREAMS'), -1: ('CREAM PUFFS', 'CHERRY PIES', 'SWEETS')}
DISGUISE_HEADERS = {1: 'SWEETS FOR GOOD CHILDREN', -1: 'COME AND GET THEM', 0: 'ALL FREE TODAY!'}
PANEL_COLOURS = [((250, 214, 222), (214, 44, 72)), ((214, 236, 250), (36, 92, 190)), ((252, 238, 186), (226, 132, 22))]
LOLLIES = [(-0.55, 0.62, 0.3, 0.15), (0.0, 0.05, 0.62, -0.1), (0.5, -0.6, 0.45, 0.2), (-0.35, -1.2, 0.35, -0.2),
           (0.45, 0.6, 0.38, -0.25)]   # (x, y, height, lean) on the roof
SIGN_FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'


def font(size):
    from PIL import ImageFont
    try:
        return ImageFont.truetype(SIGN_FONT, size)
    except OSError:
        return ImageFont.load_default()


def candy_border(d, w, h, band, a, b):
    """A barber's-pole border of two colours round a w x h picture."""
    for k in range(-h, w + h, band):
        colour = a if (k // band) % 2 == 0 else b
        d.polygon([(k, 0), (k + band, 0), (k + band - h, h), (k - h, h)], fill=colour)


def sign_texture(text, size, paper, ink, stripe):
    """A painted board: a candy-striped border, a pale ground and the words in fat serif capitals, fitted to it."""
    from PIL import Image, ImageDraw
    w, h = size
    img = Image.new('RGB', size, paper)
    d = ImageDraw.Draw(img)
    candy_border(d, w, h, max(8, h // 6), stripe, (255, 255, 255))
    edge = max(6, h // 7)
    d.rectangle((edge, edge, w - edge, h - edge), fill=paper)
    lines = text.split('\n')
    fs = h
    while fs > 8:
        f = font(fs)
        widths = [d.textlength(line, font=f) for line in lines]
        if max(widths) < w - 3 * edge and fs * 1.15 * len(lines) < h - 2.5 * edge:
            break
        fs -= 2
    f = font(fs)
    y = (h - fs * 1.15 * len(lines)) / 2
    for line in lines:
        tw = d.textlength(line, font=f)
        d.text(((w - tw) / 2, y), line, font=f, fill=ink, stroke_width=max(1, fs // 14), stroke_fill=(255, 255, 255))
        y += fs * 1.15
    return np.asarray(img.convert('RGBA'), dtype=np.float64) / 255.0


def panel_texture(text, paper, ink, size=(256, 512)):
    """A tall board: a candy-striped border, a great painted lollipop and its name under it."""
    from PIL import Image, ImageDraw
    w, h = size
    img = Image.new('RGB', size, paper)
    d = ImageDraw.Draw(img)
    candy_border(d, w, h, 26, ink, (255, 255, 255))
    d.rectangle((18, 18, w - 18, h - 18), fill=paper)
    cx, cy, r = w // 2, int(h * 0.36), int(w * 0.32)
    d.rectangle((cx - 7, cy, cx + 7, int(h * 0.74)), fill=(250, 250, 240))
    for k in range(10, 0, -1):
        rr = r * k / 10
        d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=ink if k % 2 else (255, 255, 255))
    words = text.replace(' ', '\n')
    lines = words.split('\n')
    fs = 60
    while fs > 10:
        f = font(fs)
        if max(d.textlength(line, font=f) for line in lines) < w - 44:
            break
        fs -= 2
    f = font(fs)
    y = int(h * 0.77)
    for line in lines:
        tw = d.textlength(line, font=f)
        d.text(((w - tw) / 2, y), line, font=f, fill=ink, stroke_width=2, stroke_fill=(255, 255, 255))
        y += int(fs * 1.1)
    return np.asarray(img.convert('RGBA'), dtype=np.float64) / 255.0


def swirl_texture(a, b, size=256):
    """A lollipop's face: a spiral of two colours."""
    yy, xx = (np.mgrid[0:size, 0:size] - size / 2 + 0.5) / (size / 2)
    r, t = np.hypot(xx, yy), np.arctan2(yy, xx)
    band = ((t / (2 * math.pi) * 3 + r * 4) % 1.0) < 0.5
    out = np.ones((size, size, 4))
    out[..., :3] = np.where(band[..., None], np.array(a) / 255, np.array(b) / 255)
    return out


def board(name, corners, image, outward, back_mat='paint_white', thick=0.025):
    """A painted board, its own part, its picture on the face whose corners are given (bottom left, bottom right,
    top right, top left), turned to face `outward` (read from there), the rest painted plain."""
    mat = name + '_art'
    cm.material(mat, (255, 255, 255), rough=0.55, coat=0.15, image=cm.image(mat, image))
    m = cm.Mesh()
    c = [Vector(p) for p in corners]
    normal = (c[1] - c[0]).cross(c[3] - c[0]).normalized()
    if normal.dot(Vector(outward)) < 0:
        c = [c[1], c[0], c[3], c[2]]
        normal = -normal
    c = [p + normal * thick for p in c]
    front = [m.vert(p) for p in c]
    back = [m.vert(p - normal * thick) for p in c]
    m.face(front, mat, [(0, 0), (1, 0), (1, 1), (0, 1)])
    m.face(list(reversed(back)), back_mat)
    for k in range(4):
        j = (k + 1) % 4
        m.face([front[j], front[k], back[k], back[j]], back_mat)
    centre = sum(c, Vector()) / 4 - normal * thick / 2
    return moving(m, name, centre, coll='disguise', smooth=None)


def disc(m, centre, r, thick, face, edge):
    """A flat round sweet facing along y: its two faces carry the picture across them, its rim is `edge`."""
    n = cm.sides(r) if cm.FACET else 24
    rings = []
    for side in (1, -1):
        ring = []
        for k in range(n):
            a = 2 * math.pi * k / n + (math.pi / n if cm.FACET else 0.0)
            ring.append((m.vert(centre + Vector((r * math.cos(a), side * thick / 2, r * math.sin(a)))), math.cos(a), math.sin(a)))
        rings.append(ring)
    for ring, flip in ((rings[0], False), (rings[1], True)):
        verts = [v for v, _, _ in ring]
        uvs = [(0.5 + 0.5 * c, 0.5 + 0.5 * sn) for _, c, sn in ring]
        m.face(list(reversed(verts)) if flip else verts, face, list(reversed(uvs)) if flip else uvs)
    for k in range(n):
        j = (k + 1) % n
        m.face([rings[0][k][0], rings[0][j][0], rings[1][j][0], rings[1][k][0]], edge)


def build_disguise():
    """The sweet-cart disguise over the cage (its parts named disguise_*, and lolly_*)."""
    cm.material('paint_white', (236, 232, 222), rough=0.55, coat=0.15)
    z0, rail = DECK_TOP + 0.06, DECK_TOP + RAIL2 * H
    eave = DECK_TOP + H
    out = CAGE_HW + 0.04
    span = (CAGE_FRONT - POST) - (CAGE_BACK + POST)
    for s, words in DISGUISE_PANELS.items():
        tag = 'r' if s > 0 else 'l'
        n = len(words)
        pw = span / n
        for k, word in enumerate(words):
            paper, ink = PANEL_COLOURS[k % len(PANEL_COLOURS)]
            ya, yb = CAGE_BACK + POST + pw * k + 0.02, CAGE_BACK + POST + pw * (k + 1) - 0.02
            if s > 0:
                corners = [(out, yb, z0), (out, ya, z0), (out, ya, rail - 0.04), (out, yb, rail - 0.04)]
            else:
                corners = [(-out, ya, z0), (-out, yb, z0), (-out, yb, rail - 0.04), (-out, ya, rail - 0.04)]
            board('disguise_%s%d' % (tag, k), corners, panel_texture(word, paper, ink), (s, 0, 0))
        header = DISGUISE_HEADERS[s]
        ya, yb = CAGE_BACK + POST + 0.02, CAGE_FRONT - POST - 0.02
        zl, zh = rail + 0.02, eave - 0.1
        corners = ([(out, yb, zl), (out, ya, zl), (out, ya, zh), (out, yb, zh)] if s > 0
                   else [(-out, ya, zl), (-out, yb, zl), (-out, yb, zh), (-out, ya, zh)])
        board('disguise_%sh' % tag, corners, sign_texture(header, (1024, 128), (255, 248, 226), (180, 24, 48), (214, 44, 72)),
              (s, 0, 0))
    # The back: a board over the door and its bars (standing clear of the padlock), and the sign over it.
    yb = CAGE_BACK - 0.13
    xa, xb = CAGE_HW - POST - 0.02, -(CAGE_HW - POST - 0.02)
    board('disguise_b', [(xa, yb, z0), (xb, yb, z0), (xb, yb, rail - 0.04), (xa, yb, rail - 0.04)],
          sign_texture('FREE\nSWEETS', (512, 512), (214, 236, 250), (36, 92, 190), (36, 92, 190)), (0, -1, 0))
    board('disguise_bh', [(xa, yb, rail + 0.02), (xb, yb, rail + 0.02), (xb, yb, eave - 0.1), (xa, yb, eave - 0.1)],
          sign_texture(DISGUISE_HEADERS[0], (768, 128), (255, 248, 226), (180, 24, 48), (214, 44, 72)), (0, -1, 0))
    # The valance: a striped, scalloped band hanging from the eaves all round.
    stripes = np.ones((64, 512, 4))
    xs = np.arange(512)
    stripes[..., :3] = np.where(((xs // 32) % 2 == 0)[None, :, None], np.array([214, 44, 72]) / 255, np.array([1.0, 1.0, 1.0]))
    cm.material('valance', (255, 255, 255), rough=0.8, image=cm.image('valance', stripes))
    m = cm.Mesh()
    w = CAGE_HW + ROOF_OVER - 0.01
    yf, yr = CAGE_FRONT + ROOF_OVER - 0.01, CAGE_BACK - ROOF_OVER + 0.01
    ring = [(w, yr), (w, yf), (-w, yf), (-w, yr)]
    run = 0.0
    scallops = cm.res(6, least=2, div=2)
    for (xa, ya), (xb, yb) in zip(ring, ring[1:] + ring[:1]):
        length = math.hypot(xb - xa, yb - ya)
        n = max(2, int(length / 0.25))
        for k in range(n):
            t0, t1 = k / n, (k + 1) / n
            pa = Vector((xa + (xb - xa) * t0, ya + (yb - ya) * t0, eave + 0.02))
            pb = Vector((xa + (xb - xa) * t1, ya + (yb - ya) * t1, eave + 0.02))
            top = [m.vert(pa), m.vert(pb)]
            bottom = []
            for j in range(scallops + 1):
                t = j / scallops
                dip = 0.07 + 0.04 * math.sin(math.pi * t)
                bottom.append(m.vert(pa + (pb - pa) * t - Vector((0, 0, dip))))
            u0, u1 = run / 3.0, (run + length / n) / 3.0
            for j in range(scallops):
                t, tn = j / scallops, (j + 1) / scallops
                ta = m.vert(pa + (pb - pa) * t)
                tb = m.vert(pa + (pb - pa) * tn)
                m.face([ta, tb, bottom[j + 1], bottom[j]], 'valance',
                       [(u0 + (u1 - u0) * t, 1), (u0 + (u1 - u0) * tn, 1), (u0 + (u1 - u0) * tn, 0), (u0 + (u1 - u0) * t, 0)])
            run += length / n
    o = moving(m, 'disguise_valance', (0.0, (yf + yr) / 2, eave), coll='disguise', smooth=None)
    cm.solidify(o, 0.01)
    # The lollipops on the roof: white sticks, spiral faces.
    roof = lambda x: eave + ROOF_RISE * (1 - (x / (CAGE_HW + ROOF_OVER)) ** 2) + ROOF_T  # noqa: E731
    colours = [((214, 44, 72), (255, 255, 255)), ((36, 92, 190), (255, 236, 120)), ((60, 170, 80), (255, 255, 255)),
               ((226, 132, 22), (255, 236, 200)), ((150, 60, 170), (255, 255, 255))]
    for k, (x, y, height, lean) in enumerate(LOLLIES):
        a, b = colours[k % len(colours)]
        cm.material('lolly_%d_face' % k, (255, 255, 255), rough=0.3, coat=0.6, image=cm.image('lolly_%d_face' % k, swirl_texture(a, b)))
        m = cm.Mesh()
        base = Vector((x, y, roof(x)))
        top = base + Vector((lean * height, 0.0, height))
        cm.tube(m, [base, top], 0.022, 'paint_white', seg=8)
        disc(m, top + Vector((0, 0, 0.18)), 0.2, 0.07, 'lolly_%d_face' % k, 'paint_white')
        moving(m, 'lolly_%d' % k, base, coll='disguise', smooth=None)


def build_markers():
    cm.empty('seat_driver', (0.22, SEAT_Y + 0.02, SEAT_TOP + 0.08))
    cm.empty('seat_box', (-0.32, SEAT_Y + 0.02, SEAT_TOP + 0.08))
    for k, (x, y) in enumerate(((0.42, 0.55), (-0.42, 0.55), (0.42, -0.95), (-0.42, -0.95))):
        cm.empty('cage_%d' % k, (x, y, DECK_TOP))
    cm.empty('reins', tuple(HANDS))
    cm.empty('door_out', (0.0, DECK_BACK - 0.7, 0.0))


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    cm.MATS.clear()
    cm.COLLECTIONS.clear()
    make_materials()
    build_deck()
    build_wheels()
    build_fore_carriage()
    build_cage()
    build_door()
    build_seat()
    build_disguise()
    build_markers()


# --- poses -------------------------------------------------------------------------------------------------

def pose(door=0.0, steer=0.0, gait=0.0, phase=0.0, spin=0.0, disguise=False):
    """What the game's animation does: the door swung open that far (0 to 1); the fore-carriage, the front wheels and
    the horses turned on the turntable by steer (-1 to 1); the horses' legs (and heads and tails) at that point of
    their stride (phase, radians) by gait (0 standing, 1 a trot); the wheels turned by spin (radians; the front ones
    faster, being smaller)."""
    turn = Matrix.Rotation(math.radians(22) * steer, 4, 'Z')
    for i in range(HORSES):
        build_horse_preview(i, gait, phase, steer)
    for o in bpy.data.objects:
        part = o.get('part', '')
        if part.startswith(('disguise_', 'lolly_')):
            o.hide_render = o.hide_viewport = not disguise
        rest = Vector(o['rest']) if 'rest' in o else None
        if part == 'door':
            o.rotation_euler = (0, 0, math.radians(105) * door)
            continue
        front = part in ('fore_carriage', 'wheel_fl', 'wheel_fr')
        if rest is None or part == '':
            continue
        yaw = math.radians(22) * steer if front else 0.0
        o.location = (TURNTABLE + turn @ (rest - TURNTABLE)) if front else rest
        roll = 0.0
        if part.startswith('wheel_'):
            roll = spin * (REAR_R / FRONT_R if part.startswith('wheel_f') else 1.0)
        elif part.startswith('leg_'):
            tag = part.split('_')[1]
            diagonal = 0.0 if tag in ('fl', 'hr') else math.pi
            roll = math.radians(28) * gait * math.sin(phase + diagonal) * (1 if tag[0] == 'f' else -0.8)
        elif part.startswith('head_'):
            roll = math.radians(4) * gait * math.sin(2 * phase)
        elif part.startswith('tail_'):
            roll = math.radians(10) * gait * math.sin(phase)
        o.rotation_euler = (roll, 0, yaw)
    bpy.context.view_layer.update()


# --- output ------------------------------------------------------------------------------------------------

def render(out, name, cam_loc, look_at, lens=35, size=(1600, 900), samples=int(os.environ.get('CARRIAGE_SAMPLES', '48'))):
    scene = bpy.context.scene
    cm.render_scene_setup()
    scene.cycles.samples = samples
    scene.render.resolution_x, scene.render.resolution_y = size
    bpy.data.objects['water'].hide_render = True
    cam = scene.camera
    cam.location = Vector(cam_loc)
    cam.rotation_euler = (Vector(look_at) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = lens
    scene.render.filepath = os.path.join(out, name + '.png')
    bpy.ops.render.render(write_still=True)


SHOTS = [
    # name, camera, look at, lens, pose
    ('side', (-12.5, 1.4, 2.0), (0, 1.4, 1.55), 30, {}),
    ('front_three_quarter', (8.0, 10.5, 3.0), (0, 1.3, 1.5), 30, {}),
    ('rear_three_quarter', (5.6, -6.4, 2.1), (0, -0.4, 1.8), 30, {}),
    ('driver_and_window', (2.5, 3.6, 2.6), (0, 1.0, 2.0), 30, {}),
    ('horse', (2.4, 6.6, 2.3), (0, HORSE_Y + 0.6, 1.6), 32, {}),
    ('door_open', (-1.2, -6.2, 2.3), (0, -1.5, 1.8), 32, dict(door=1.0)),
    ('trotting', (-7.5, 7.5, 2.6), (0, 1.4, 1.5), 30, dict(gait=1.0, phase=0.9, spin=1.2, steer=0.35)),
    ('disguised', (8.0, 9.0, 3.2), (0, 0.6, 1.9), 30, dict(disguise=True)),
    ('disguised_left', (-8.6, 0.6, 2.4), (0, -0.2, 2.0), 32, dict(disguise=True)),
    ('disguised_rear', (4.6, -7.0, 2.6), (0, -0.8, 2.1), 30, dict(disguise=True)),
]

GAME_MESH = 'chitty/src/client/resources/assets/shootingstar/meshes/carriage.cbm'
GAME_TEXTURE = 'chitty/src/client/resources/assets/shootingstar/textures/entity/carriage.png'
ITEM_ICON = 'chitty/src/main/resources/assets/shootingstar/textures/item/carriage.png'

# How much of the atlas a part gets for its size: the disguise's lettered boards most (its long signs and its striped
# valance less: the longest island sets how small everything else must be), the deck's planks and what is under her
# least.
TEXEL_WEIGHT = {'disguise_valance': 0.7, 'disguise_lh': 1.4, 'disguise_rh': 1.4, 'disguise_bh': 1.6, 'disguise_': 2.0,
                'lolly_': 1.4, 'seat': 1.4, 'door': 1.2, 'deck': 0.6, 'fore_carriage': 0.8, 'wheel_': 0.9, 'trace': 0.3,
                'rein': 0.3}


def build_straps():
    """For the game: a block's length of trace and of rein, hanging from its top, which the game stretches from point
    to point (the horse's collar to her splinter bar, its bit to the driver's hands)."""
    for name, (w, t) in (('trace', (0.05, 0.016)), ('rein', (0.018, 0.012))):
        m = cm.Mesh()
        cm.add_box(m, (0, 0, -0.5), (w, t, 1.0), 'leather')
        moving(m, name, (0, 0, 0), smooth=None)


def square_aspect():
    """Every material's active texture made a square image before her atlas is unwrapped: Blender's unwrap and its
    packer go by the aspect of a face's active image (turning an island a quarter turn, the packer stretches it by it),
    and her signs are painted on long strips of image. The bake's own target, square too, is made active later."""
    img = bpy.data.images.new('square_aspect', 8, 8)
    for mat in cm.MATS.values():
        nt = mat.node_tree
        node = nt.nodes.new('ShaderNodeTexImage')
        node.image = img
        nt.nodes.active = node


def export_game():
    """Bakes her and writes the game's mesh (.cbm), its texture and the item icon, with Chitty's exporter
    (chitty_model.export_game, whose docstring has the format) pointed at her files: one 1024 atlas of her colours,
    the game lighting each of her flat faces by which way it faces. Every part at rest, the disguise included (the game
    shows it or not); no horse (the game's own is hitched to her)."""
    build_straps()
    cm.GAME_MESH, cm.GAME_TEXTURE, cm.ITEM_ICON = GAME_MESH, GAME_TEXTURE, ITEM_ICON
    cm.BAKE_SIZE = 1024
    cm.BAKE_SAMPLES = int(os.environ.get('CARRIAGE_BAKE_SAMPLES', '64'))
    cm.TEXEL_WEIGHT = TEXEL_WEIGHT
    cm.SHINE, cm.GLOW, cm.GLASS_TINT = {}, (), {}
    cm.GAME_LIT = ('wheel_',)
    cm.LIT_ALL = True
    cm.PACK_ROTATE = 'AXIS_ALIGNED'
    cm.UV_CORRECT_ASPECT = False    # her signs are painted on long strips of image
    cm.neutral = square_aspect
    cm.pose = lambda *a, **k: None
    cm.export_game(None)
    render_icon()


def render_icon():
    """The item: her side on (the cage, the driver's box, the wheels), a little from the front and above, without a
    horse, shrunk to a crisp 32 x 32."""
    from PIL import Image, ImageEnhance
    scene = bpy.context.scene
    cm.render_scene_setup()
    for name in ('ground', 'water'):
        if name in bpy.data.objects:
            bpy.data.objects[name].hide_render = True
    for o in bpy.data.objects:
        if o.get('part', '').startswith(('disguise_', 'lolly_')) or o.name in ('trace', 'rein'):
            o.hide_render = True
    scene.render.film_transparent = True
    cam = scene.camera
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = 4.6
    cam.location = Vector((14.0, 5.0, 4.5))
    cam.rotation_euler = (Vector((0.0, 0.1, 1.6)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    scene.render.resolution_x = scene.render.resolution_y = 512
    scene.cycles.samples = 32
    path = os.path.join(bpy.app.tempdir or '/tmp', 'carriage_icon.png')
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    small = Image.open(path).convert('RGBa').resize((32, 32), Image.LANCZOS).convert('RGBA')
    rgb = ImageEnhance.Color(ImageEnhance.Brightness(small.convert('RGB')).enhance(1.35)).enhance(1.1)
    small = Image.merge('RGBA', (*rgb.split(), small.split()[3]))
    a = np.array(small)
    a[..., 3] = np.where(a[..., 3] > 100, 255, 0)
    os.makedirs(os.path.dirname(ITEM_ICON), exist_ok=True)
    Image.fromarray(a, 'RGBA').save(ITEM_ICON, optimize=True)
    scene.render.film_transparent = False
    cam.data.type = 'PERSP'
    print('icon ->', ITEM_ICON, '(full size at %s)' % path)


def main():
    args = sys.argv[1:]
    cm.FACET = GAME or '--facet' in args
    if GAME:
        build()
        export_game()
        return
    if '--out' not in args:
        print(__doc__)
        return
    out = args[args.index('--out') + 1]
    os.makedirs(out, exist_ok=True)
    build()
    pose()
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out, 'carriage.blend'), check_existing=False)
    if '--renders' in args:
        only = args[args.index('--only') + 1].split(',') if '--only' in args else None
        suffix = '' if HORSES == 1 else '_pair'
        for name, cam, at, lens, p in SHOTS:
            if only and name not in only:
                continue
            pose(**p)
            render(out, name + suffix, cam, at, lens)
        pose()


if __name__ == '__main__':
    main()
