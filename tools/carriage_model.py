"""Builds the Child Catcher's carriage from Chitty Chitty Bang Bang (1968) in Blender.

Needs Blender's Python module, as tools/chitty_model.py does. Run from the repository root:

    python tools/carriage_model.py --out DIR --renders [--only a,b] [--horses 1|2]   # renders for a look
    python tools/carriage_model.py --game                                            # the game's mesh, texture, icon

Blender units are blocks (metres). The carriage faces +Y with +X on its right and +Z up, its wheels on the ground at
Z = 0 and the middle of its wheelbase over the origin; its horse stands in the shafts ahead of it (or a pair stand
either side of a pole, --horses 2). Every part the game moves is its own object with its origin on its pivot, named as
the game knows it: the four wheels; the fore-carriage (front axle, springs, shafts or pole) that turns on its turntable
under the driver's seat, taking the front wheels and the horses with it; the cage door on its hinges; and each horse's
legs, head (with its neck) and tail. Empties mark the driver's seat, the places in the cage and where the driver holds
the reins.

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
# The horse: the middle of its body is HORSE_Y ahead (its rump a little ahead of the deck); a pair stand PAIR apart.
HORSE_Y = 3.30
PAIR = 0.56
# The splinter bar across the front of the fore-carriage, which the horses pull on.
SPLINTER = Vector((0.0, FRONT_AXLE + 0.42, 0.74))

# The horse, built as Minecraft builds its horses (in boxes), a size up and black, about the ground under the middle of
# its body, facing +Y (blocks): its body, legs (turning at their tops), neck and head (pitched forward from the
# withers, turning there), and tail.
BODY = dict(hw=0.33, bottom=0.88, top=1.62, back=-0.86, front=0.86)
LEGS = {'fl': (0.19, 0.62), 'fr': (-0.19, 0.62), 'hl': (0.19, -0.64), 'hr': (-0.19, -0.64)}
LEG_W, LEG_TOP = 0.24, 0.94
NECK_BASE = Vector((0.0, 0.70, 1.38))
NECK_DIR = Vector((0.0, math.sin(math.radians(35)), math.cos(math.radians(35))))
NECK_LEN, NECK_W, NECK_D = 0.95, 0.34, 0.50
POLL = NECK_BASE + NECK_DIR * NECK_LEN
FACE = Vector((0.0, math.cos(math.radians(-42)), math.sin(math.radians(-42))))
HEAD_L, HEAD_W, HEAD_H = 0.52, 0.38, 0.36
MUZZLE = POLL + Vector((0.0, 0.04, -0.06)) + FACE * (HEAD_L + 0.2)
TAIL_ROOT = Vector((0.0, -0.86, 1.52))


def boxed(m, center, size, mat, along=None):
    """A box about center, its length (size[1]) laid along `along` (in her YZ plane) if given."""
    rot = None
    if along is not None:
        rot = Matrix.Rotation(math.atan2(along.z, along.y), 4, 'X')
    cm.add_box(m, tuple(center), size, mat, rot=rot)


def horse_at(i):
    """Where horse i stands (the ground under the middle of its body)."""
    if HORSES == 1:
        return Vector((0.0, HORSE_Y, 0.0))
    return Vector(((PAIR if i == 0 else -PAIR), HORSE_Y, 0.0))


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
    m('coat', (19, 18, 19), rough=0.42, coat=0.15)
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
                       (s * 0.57, HORSE_Y - 0.4, 1.14), (s * 0.53, HORSE_Y + 0.55, 1.23), (s * 0.46, HORSE_Y + 0.95, 1.22)], 6)


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

def build_horse(i):
    """Horse i: black, in harness. Its body (with its collar, saddle, breeching, traces and the reins back to the
    driver's hands) is one part; each leg is a part turning at its top, the head (with the neck, mane, bridle, blinkers
    and the black plume) another turning at the withers, the tail another."""
    at = horse_at(i)
    W = lambda p: at + Vector(p)  # noqa: E731
    b = BODY
    m = cm.Mesh()
    cm.add_box(m, tuple(W((0, (b['front'] + b['back']) / 2, (b['top'] + b['bottom']) / 2))),
               (2 * b['hw'], b['front'] - b['back'], b['top'] - b['bottom']), 'coat')
    # The chest standing a little proud in front.
    cm.add_box(m, tuple(W((0, b['front'] + 0.07, 1.25))), (0.58, 0.14, 0.5), 'coat')
    # The collar round the base of the neck, the brass hames along it.
    d = NECK_DIR
    u = Vector((1.0, 0.0, 0.0))
    v = d.cross(u)
    c = W(NECK_BASE + d * 0.12)
    ring = [c + u * (0.24 * math.cos(a)) + v * (0.31 * math.sin(a))
            for a in np.linspace(0, 2 * math.pi, cm.res(20, least=8, div=2) + 1)]
    cm.tube(m, ring, 0.055, 'leather', seg=10)
    for s in (-1, 1):
        hame = [c + u * (s * 0.28) + v * (0.27 * k) - d * 0.01 for k in (-0.8, -0.3, 0.3, 0.8)]
        cm.tube(m, hame, 0.014, 'brass', seg=8)
        cm.ellipsoid(m, hame[0] - v * 0.04, (0.025, 0.025, 0.025), 'brass', seg=10, rings=6)
    # The saddle pad over the back, its flaps, the girth under the belly, two brass terrets on top.
    ys = at.y + 0.25
    top = b['top']
    cm.add_box(m, (at.x, ys, top + 0.025), (0.5, 0.24, 0.05), 'leather')
    for s in (-1, 1):
        cm.add_box(m, (at.x + s * (b['hw'] + 0.012), ys, top - 0.2), (0.02, 0.22, 0.4), 'leather')
        cm.add_box(m, (at.x + s * (b['hw'] + 0.008), ys, (top - 0.4 + b['bottom']) / 2), (0.016, 0.07, top - 0.4 - b['bottom']),
                   'leather')
        terret = [W((s * 0.12 + 0.035 * math.cos(a), 0.25, top + 0.09 + 0.035 * math.sin(a)))
                  for a in np.linspace(0, 2 * math.pi, cm.res(12, least=6, div=2) + 1)]
        cm.tube(m, terret, 0.008, 'brass', seg=6)
    cm.add_box(m, (at.x, ys, b['bottom'] - 0.01), (2 * b['hw'] + 0.02, 0.07, 0.02), 'leather')
    # The crupper down the spine to the tail; the breeching round the hindquarters, held up by a strap over the croup.
    cm.add_box(m, tuple(W((0, -0.3, top + 0.012))), (0.05, 1.1, 0.024), 'leather')
    for s in (-1, 1):
        cm.add_box(m, tuple(W((s * (b['hw'] + 0.012), -0.6, 1.25))), (0.024, 0.52, 0.06), 'leather')
        cm.add_box(m, tuple(W((s * (b['hw'] + 0.01), -0.62, (1.25 + top) / 2))), (0.02, 0.05, top - 1.25), 'leather')
    cm.add_box(m, tuple(W((0, b['back'] - 0.012, 1.25))), (2 * b['hw'] + 0.05, 0.024, 0.06), 'leather')
    cm.add_box(m, tuple(W((0, -0.62, top + 0.01))), (2 * b['hw'], 0.05, 0.02), 'leather')
    # The traces from the hames back to the swingletree on the splinter bar; in shafts, the tugs that carry them.
    tree = SPLINTER + Vector((at.x, 0.07, 0.0))
    for s in (-1, 1):
        cm.tube(m, cm.catmull([tuple(W((s * 0.30, 0.84, 1.28))), tuple(W((s * (b['hw'] + 0.03), 0.0, 1.2))),
                               tuple(W((s * (b['hw'] + 0.03), -0.8, 1.08))), tuple(tree + Vector((s * 0.29, 0.0, 0.0)))], 4),
                0.02, 'leather', seg=8)
        if HORSES == 1:
            cm.add_box(m, (at.x + s * 0.45, ys, 1.19), (0.24, 0.06, 0.07), 'leather')
    # The reins: from the bit, through the terrets, back to the driver's hands.
    bit = W(MUZZLE - FACE * 0.12 + Vector((0, 0, -0.05)))
    for s in (-1, 1):
        hand = HANDS + Vector((s * 0.03, 0, 0))
        terret = W((s * 0.12, 0.25, top + 0.12))
        cm.tube(m, cm.catmull([tuple(bit + Vector((s * 0.15, 0, 0))), tuple(terret),
                               tuple((terret + hand) * 0.5 - Vector((0, 0, 0.1))), tuple(hand)], 4), 0.009, 'leather', seg=6)
    o = moving(m, 'horse_%d' % i, at)
    o['horse'] = i

    # The legs: a square leg and its hoof, turning at the top.
    for tag, (x, y) in LEGS.items():
        m = cm.Mesh()
        cm.add_box(m, tuple(W((x, y, (LEG_TOP + 0.13) / 2))), (LEG_W, LEG_W, LEG_TOP - 0.13), 'coat')
        cm.add_box(m, tuple(W((x, y + 0.01, 0.07))), (LEG_W + 0.02, LEG_W + 0.04, 0.14), 'hoof')
        moving(m, 'leg_%s_%d' % (tag, i), W((x, y, LEG_TOP)))

    # The neck and head, pitched forward from the withers: neck, mane, head, muzzle, ears, eyes behind blinkers, the
    # bridle and the bit, and the plume on the poll.
    m = cm.Mesh()
    boxed(m, W(NECK_BASE + NECK_DIR * (NECK_LEN / 2)), (NECK_W, NECK_LEN, NECK_D), 'coat', along=NECK_DIR)
    boxed(m, W(NECK_BASE + NECK_DIR * (NECK_LEN / 2) - v * (NECK_D / 2 + 0.04) + NECK_DIR * 0.04), (0.1, NECK_LEN + 0.06, 0.1),
          'mane', along=NECK_DIR)
    head = POLL + Vector((0.0, 0.04, -0.06)) + FACE * (HEAD_L / 2)
    boxed(m, W(head), (HEAD_W, HEAD_L, HEAD_H), 'coat', along=FACE)
    boxed(m, W(MUZZLE - FACE * 0.1), (HEAD_W - 0.08, 0.22, HEAD_H - 0.08), 'muzzle', along=FACE)
    side_up = FACE.cross(u)    # across the face, up out of the forehead
    for s in (-1, 1):
        ear = POLL + Vector((s * 0.12, 0.0, 0.05))
        boxed(m, W(ear + Vector((0, -0.02, 0.07))), (0.08, 0.05, 0.16), 'coat')
        eye = W(head + u * (s * (HEAD_W / 2 + 0.005)) + FACE * -0.06 - side_up * 0.04)
        cm.add_box(m, tuple(eye), (0.012, 0.05, 0.05), 'eye')
        boxed(m, eye + Vector((s * 0.035, 0, 0)) + side_up * -0.0, (0.014, 0.15, 0.13), 'leather', along=FACE)
        cm.add_box(m, tuple(W(POLL + Vector((s * (HEAD_W / 2 + 0.01), 0.06, -0.04)))), (0.016, 0.05, 0.05), 'brass')
        boxed(m, W(POLL + FACE * (HEAD_L / 2 + 0.05) + u * (s * (HEAD_W / 2 + 0.008)) - side_up * 0.06),
              (0.012, HEAD_L + 0.1, 0.03), 'leather', along=FACE)
        cm.lathe(m, [(0.035, -0.008), (0.035, 0.008)], lambda k: 'brass', axis='x', seg=10,
                 origin=tuple(W(MUZZLE - FACE * 0.12 + Vector((s * (HEAD_W / 2 - 0.02), 0, -0.05)))))
    boxed(m, W(POLL + Vector((0, 0.07, -0.02))), (HEAD_W + 0.03, 0.03, 0.035), 'leather')
    boxed(m, W(MUZZLE - FACE * 0.17), (HEAD_W - 0.05, 0.035, HEAD_H - 0.04), 'leather', along=FACE)
    # The plume: a brass holder on the poll and a tall black plume of three, the middle one tallest.
    crown = W(POLL + Vector((0, 0.02, 0.1)))
    cm.add_box(m, tuple(crown), (0.06, 0.06, 0.1), 'brass')
    for tilt, height, lean in ((0.0, 0.48, 0.1), (0.38, 0.36, 0.06), (-0.38, 0.36, 0.06)):
        def plume(mm, height=height, lean=lean):
            limb(mm, Vector((0, 0, 0.04)), Vector((0, lean * 0.4, height * 0.8)),
                 [(0, 0.025, 0.025), (0.25, 0.06, 0.045), (0.65, 0.07, 0.05), (1, 0.055, 0.04)], 'plume', n=2.0)
            limb(mm, Vector((0, lean * 0.4, height * 0.8)), Vector((0, lean, height)),
                 [(0, 0.055, 0.04), (1, 0.012, 0.012)], 'plume', n=2.0)
        placed(m, Matrix.Translation(crown) @ Matrix.Rotation(tilt, 4, 'Y'), plume)
    moving(m, 'head_%d' % i, W(NECK_BASE))

    # The tail: a long box hanging from the top of the rump, swept back a little.
    m = cm.Mesh()
    sweep = Vector((0.0, -math.sin(math.radians(18)), -math.cos(math.radians(18))))
    boxed(m, W(TAIL_ROOT + sweep * 0.42 + Vector((0, -0.04, 0))), (0.18, 0.84, 0.22), 'mane', along=sweep)
    moving(m, 'tail_%d' % i, W(TAIL_ROOT))


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
    for i in range(HORSES):
        build_horse(i)
    build_markers()


# --- poses -------------------------------------------------------------------------------------------------

def pose(door=0.0, steer=0.0, gait=0.0, phase=0.0, spin=0.0):
    """What the game's animation does: the door swung open that far (0 to 1); the fore-carriage, the front wheels and
    the horses turned on the turntable by steer (-1 to 1); the horses' legs (and heads and tails) at that point of
    their stride (phase, radians) by gait (0 standing, 1 a trot); the wheels turned by spin (radians; the front ones
    faster, being smaller)."""
    turn = Matrix.Rotation(math.radians(22) * steer, 4, 'Z')
    for o in bpy.data.objects:
        part = o.get('part', '')
        rest = Vector(o['rest']) if 'rest' in o else None
        if part == 'door':
            o.rotation_euler = (0, 0, math.radians(105) * door)
            continue
        front = part in ('fore_carriage', 'wheel_fl', 'wheel_fr') or part.startswith(('horse_', 'leg_', 'head_', 'tail_'))
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
]

GAME_MESH = 'chitty/src/client/resources/assets/shootingstar/meshes/carriage.cbm'
GAME_TEXTURE = 'chitty/src/client/resources/assets/shootingstar/textures/entity/carriage.png'
ITEM_ICON = 'chitty/src/main/resources/assets/shootingstar/textures/item/carriage.png'


def main():
    args = sys.argv[1:]
    cm.FACET = GAME or '--facet' in args
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
