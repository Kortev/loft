"""Beam the shark fiend of the Reze arc in Minecraft model boxes: the layout both the game and the texture painter use.

kortev wanted the smooth Blender Beam (tools/beam_model.py, the blueprint) in the same style as the chainsaw head and
Pochita: model boxes, flat colours painted pixel by pixel. So this module builds him from many boxes (slices along his body,
each a short stack of boxes narrowing toward its top and bottom, so the outline steps smoothly round) sized to the
blueprint's measured sections, and is the one place the boxes are written down:

    python tools/beam_boxes.py --java      writes chainsawman/src/client/java/.../client/BeamLayout.java (the game's boxes)
    python tools/beam_boxes.py --report    prints the sizes, the box count and the texture

tools/gen_chainsaw_textures.py (beam_entity) imports this and paints each box from where it is on him; tools/beam_preview.py
(Blender) draws the boxes with that texture to look at them without the game. Pure Python + numpy.

Space, as the game's models have it, in pixels (16 to a block): x his +x side, y DOWN, the top of his back at y = 0 and the
soles of his feet at y = HEIGHT, z along him: his front toward -z, the middle of his length at z = 0. In the tables below
'u' is the distance back from the tip of his nose and 'h' a height above the ground, which is how the blueprint's sections
were measured (the blueprint's blocks times 16, its u shifted by NOSE: the blueprint's mouth sits at the very front of the
head, kortev wants "more nose on top of it", so the nose carries on NOSE pixels past the front teeth).
"""
import math
import os
import sys

import numpy as np

HEIGHT = 54            # the top of his back above the ground (pixels): 3.4 blocks
Z0 = 46                # u of the middle of him
NOSE = 12              # how far the nose carries on past the front teeth
U_WALL = 44            # where the head ends and the trunk (the chest, the throat's wall) begins
U_HINGE = 43           # the back corner of the mouth, where the lower jaw hinges
HINGE_H = 31           # its height above the ground
JAW_LEN = 30           # the lower jaw, from the hinge to its tip
OPEN_DEG = 38.0        # how far the mouth hangs open (kortev: a bit less than the blueprint's 46)
U_REAR = 70            # where the trunk ends and the rear of his body (which sways) begins
U_STALK = 77           # the stalk of the tail
U_FIN = 82             # the crescent tail fin's hinge
LENGTH = 92            # snout tip to the tail's tip

# --- the body's sections: u, top height, bottom height, half width (pixels) -------------------------------------------------
# In the head the bottom is the lip (the roof of the mouth): from the front teeth at u = NOSE it slopes down to the hinge;
# ahead of that the nose's own underside. The chest (behind the wall) has the belly 13 up.
TOP = [(0, 44), (1, 45.5), (2.5, 48), (5, 50.5), (9, 52.5), (16, 53.5), (26, 54), (30, 54), (44, 54), (50, 54), (58, 52), (63, 48.5),
       (68, 44), (72, 40), (76, 36), (80, 32.5), (84, 29.5), (88, 27.5), (92, 27)]
BOTTOM = [(0, 45), (1, 43.4), (2.5, 42.6), (NOSE, 42.5), (U_HINGE, HINGE_H), (U_WALL - 0.01, HINGE_H), (U_WALL, 15), (48, 13.5),
          (52, 13), (63, 12.5), (68, 13), (72, 14.5), (76, 17), (80, 19.5), (84, 22), (88, 23.5), (92, 24)]
HALF = [(0, 3), (1, 6), (2, 8), (4, 10.5), (7, 12.5), (NOSE, 14.5), (20, 16.5), (30, 17.8), (U_HINGE, 18.7), (50, 19.2),
        (58, 19), (66, 16.5), (72, 13), (76, 10), (80, 7), (84, 4.5), (88, 3), (92, 2)]
SEC_EXP = 2.2          # the sections are superellipses of this exponent, a little squarer than an ellipse

# The lower jaw's sections: distance along it from the hinge, half width, depth below the rim (the blueprint's, in pixels).
JAW = [(0, 15.7, 16.8), (4.8, 16.3, 15.4), (14.4, 14.0, 11.5), (22.4, 10.0, 7.4), (27, 5.4, 4.5), (30, 2.4, 1.9)]

# The line between navy above and mint below on his side (u, height): the blueprint's, in pixels. On the head the line is
# taken a fraction of the way from the lip up to the top, so it rises toward the eyes as the stills have it.
BOUNDARY = [(0, 44.5), (6, 45), (12, 45.5), (20, 45), (30, 44.5), (38, 44), (44, 41), (50, 37), (58, 31), (66, 27), (74, 25),
            (84, 24), (92, 23.5)]
# Eyes: u, height, size (pixels), the front one biggest, in a diagonal up and back from above the corner of the mouth.
EYES = [(35, 45, 7), (42, 49, 6), (49, 51.5, 5)]
EYE_OUT = 2            # how far an eye stands out of his side
# The back's spikes: u of the front of the base, height, length of the base.
SPIKES = [(36, 13, 11), (45, 11, 9), (54, 9, 8), (62, 7, 7), (69, 5.5, 6)]
# The legs, in the order front right, front left, back right, back left: hip u, the knee (out, u, height), the foot (out, u).
LEG_HIP_OUT = 9
LEG_HIP_H = 21
LEGS = {'front': dict(hip_u=51, knee=(35, 48, 44), foot=(29, 43)), 'back': dict(hip_u=66, knee=(35, 71, 44), foot=(29, 76))}
TEXTURE_W = 512


def Z(u):
    return u - Z0


def Yh(h):
    return HEIGHT - h


def interp(table, u):
    return float(np.interp(u, [p[0] for p in table], [p[1] for p in table]))


def sections_at(u):
    return interp(TOP, u), interp(BOTTOM, u), interp(HALF, u)


# --- boxes and parts ------------------------------------------------------------------------------------------------------

class Box:
    def __init__(self, part, kind, x, y, z, w, h, d, **meta):
        self.part = part
        self.kind = kind
        self.x, self.y, self.z = x, y, z   # the min corner, in the part's own frame (relative to its pivot)
        self.w, self.h, self.d = int(round(w)), int(round(h)), int(round(d))
        self.meta = meta
        self.name = None
        self.u = self.v = None             # the texture corner, set by pack()

    @property
    def footprint(self):
        return 2 * self.d + 2 * self.w, self.d + self.h


class Part:
    def __init__(self, name, parent, pivot, rot=(0.0, 0.0, 0.0)):
        self.name, self.parent, self.pivot, self.rot = name, parent, pivot, rot  # rot: pitch, yaw, roll at rest
        self.boxes = []


PARTS = {}
BOXES = []
_ORDER = []


def part(name, parent, pivot, rot=(0.0, 0.0, 0.0)):
    """A part hinged at {pivot} (pixels, in the parent's frame). Given as absolute model coordinates when the chain above it
    has no rotation: the pivot is turned into the parent's frame here."""
    p = Part(name, parent, tuple(pivot), rot)
    PARTS[name] = p
    _ORDER.append(name)
    return p


def absolute_pivot(name):
    """Where a part's pivot is in model space (the chain's translations only: for placing boxes by their model position)."""
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


# --- sections cut into stacks of boxes -------------------------------------------------------------------------------------

def swidth(t):
    """Half width, as a share of the full, of the superellipse at normalised height t (-1 to 1)."""
    t = min(1.0, abs(t))
    return (1.0 - t ** SEC_EXP) ** (1.0 / SEC_EXP)


def rows(bottom, top, half, round_top=True, round_bottom=True):
    """A rounded section as a stack of rows: [(y_bottom, y_top, half_width)] in heights above the ground, integers, widths in
    halves of pixels. The section is a superellipse; where a side isn't rounded (a flat floor, a flat roof) its rows go full
    width to that side."""
    bottom, top = int(round(bottom)), int(round(top))
    h = top - bottom
    if h <= 0:
        return []
    mid = (top + bottom) / 2.0
    a = h / 2.0
    # the row boundaries as shares of the half height, from the centre; fewer for a thin section
    if h >= 30:
        cuts = [0.0, 0.38, 0.66, 0.86, 1.0]
    elif h >= 20:
        cuts = [0.0, 0.46, 0.80, 1.0]
    elif h >= 9:
        cuts = [0.0, 0.55, 1.0]
    else:
        cuts = [0.0, 1.0]
    out = []
    # the upper half
    ys_top = [mid + a * c for c in cuts]
    ys_bot = [mid - a * c for c in cuts]
    if not round_top:
        ys_top = [mid, top]
    if not round_bottom:
        ys_bot = [mid, bottom]
    # unify: boundaries bottom..top
    bounds = sorted(set([round(y) for y in ys_bot + ys_top]))
    bounds[0], bounds[-1] = bottom, top
    bounds = sorted(set(bounds))
    for lo, hi in zip(bounds[:-1], bounds[1:]):
        if hi <= lo:
            continue
        # the row's width: the section's width at the row's middle, but a row that holds the centre is full width
        t0, t1 = (lo - mid) / a, (hi - mid) / a
        if (t0 <= 0.0 <= t1) or (not round_top and t0 >= 0) or (not round_bottom and t1 <= 0):
            share = 1.0
        else:
            near = min(abs(t0), abs(t1))
            far = max(abs(t0), abs(t1))
            share = 0.5 * (swidth(near) + swidth(far))
            if not round_top and t0 >= 0:
                share = 1.0
        w = max(0.5, round(half * share * 2) / 2.0)
        out.append((lo, hi, w))
    return out


def merge(rows_):
    """Adjacent rows with the same width are one."""
    out = []
    for lo, hi, w in rows_:
        if out and out[-1][2] == w and out[-1][1] == lo:
            out[-1] = (out[-1][0], hi, w)
        else:
            out.append((lo, hi, w))
    return out


def depth_for(u, lo=2, hi=4):
    """How deep a slice of the body is at u: shallow where the sections change quickly (so the outline steps no more than a
    pixel or two), deeper where they hardly change."""
    best = 0.0
    for table in (TOP, BOTTOM, HALF):
        best = max(best, abs(interp(table, u + 1.0) - interp(table, u - 1.0)) / 2.0)
    return int(max(lo, min(hi, round(1.6 / max(best, 1e-6)))))


def slices(u0, u1, step_of, fn):
    """Cuts u0..u1 into slices (their depth from {step_of(u)}), each described by fn(u_mid) -> rows (or None), merging
    neighbours that come out the same. Returns [(ua, ub, rows)]."""
    out = []
    u = u0
    while u < u1 - 1e-9:
        d = min(step_of(u), u1 - u)
        r = tuple(merge(fn(u + d / 2.0)))
        if out and out[-1][2] == r and out[-1][1] - out[-1][0] + d <= 8:
            out[-1] = (out[-1][0], u + d, r)
        else:
            out.append((u, u + d, r))
        u += d
    return out


def stack(part_name, kind, ua, ub, rows_, **meta):
    """The boxes of one slice: a row each, centred on x = 0, between u = ua and ub."""
    for lo, hi, w in rows_:
        add_box(part_name, kind, -w, Yh(hi), Z(ua), 2 * w, hi - lo, ub - ua, slice_u=(ua + ub) / 2.0, row=(lo, hi), **meta)


# --- building him ----------------------------------------------------------------------------------------------------------

def build():
    PARTS.clear()
    BOXES.clear()
    _ORDER.clear()
    part('trunk', None, (0.0, 0.0, 0.0))
    part('head', 'trunk', (0.0, Yh(36.0), Z(U_WALL)))
    jaw_tilt = math.atan2(interp(BOTTOM, NOSE) - HINGE_H, U_HINGE - NOSE)  # how far the lip rises toward the front
    part('jaw', 'head', (0.0, Yh(HINGE_H) - Yh(36.0), Z(U_HINGE) - Z(U_WALL)))
    part('pectoral_r', 'trunk', (17.0, Yh(30.0), Z(50)))
    part('pectoral_l', 'trunk', (-17.0, Yh(30.0), Z(50)))
    part('rear', 'trunk', (0.0, Yh(34.0), Z(U_REAR)))
    part('stalk', 'rear', (0.0, 0.0, U_STALK - U_REAR))
    part('tail', 'stalk', (0.0, 0.0, U_FIN - U_STALK))

    # The head: from the nose to the wall, a slice at a time; its underside the lip (flat, the roof of his mouth) from the
    # front teeth back, the nose rounded all round ahead of that.
    def head_rows(u):
        top, bot, half = sections_at(u)
        return rows(bot, top, half, round_top=True, round_bottom=u < NOSE)
    for ua, ub, r in slices(0, U_WALL, lambda u: depth_for(u, 2, 3), head_rows):
        stack('head', 'head', ua, ub, r)
    # The trunk: the chest and back, the wall of the throat at its front.
    def trunk_rows(u):
        top, bot, half = sections_at(u)
        return rows(bot, top, half)
    for ua, ub, r in slices(U_WALL, U_REAR, lambda u: depth_for(u, 2, 4), trunk_rows):
        stack('trunk', 'trunk', ua, ub, r)
    # The rear of the body, narrowing; then the stalk.
    for ua, ub, r in slices(U_REAR, U_STALK, lambda u: depth_for(u, 2, 3), trunk_rows):
        stack('rear', 'rear', ua, ub, r)
    for ua, ub, r in slices(U_STALK, U_FIN, lambda u: 3, trunk_rows):
        stack('stalk', 'stalk', ua, ub, r)

    # The lower jaw: built straight out from its hinge (its rim at y = 0 of the part, forward along -z), turned down at the
    # hinge by the animation; a flat top (the gums and teeth) and a rounded bowl below.
    st = np.array(JAW)
    jaw_u = 0.0
    while jaw_u < JAW_LEN - 1e-9:
        d = min(3.0, JAW_LEN - jaw_u)
        mid = jaw_u + d / 2.0
        hw = float(np.interp(mid, st[:, 0], st[:, 1]))
        dep = float(np.interp(mid, st[:, 0], st[:, 2]))
        for lo, hi, w in merge(rows(-dep, 0, hw, round_top=False, round_bottom=True)):
            add_box('jaw', 'jaw', -w, -hi, -(jaw_u + d), 2 * w, hi - lo, d, local=True, v=mid, row=(lo, hi), depth=dep, hw=hw)
        jaw_u += d
    build_extras(jaw_tilt)
    return jaw_tilt


# How the legs, the jaw, the pectorals stand when he is still (radians): the game's animation adds the stride, the bite and the
# sway to these. Right-hand values; the left is the mirror.
LEG_UP = 0.72          # the upper part's angle up from horizontal at the hip
LEG_BOW = 0.20         # the upper part curves: steeper at the hip by this, flatter at the knee
LEG_SPLAY = {'front': 0.092, 'back': -0.153}   # fore-aft lean of the upper part (yaw), toward the front for the front pair
SHIN_IN = 0.10         # the shin leans in toward the foot
SHIN_FORE = {'front': -0.203, 'back': 0.257}   # and fore or aft (pitch), the feet a little outside the hips
SHIN_BEND = (-0.04, 0.05, 0.08, 0.08, 0.10)   # each shin piece's turn from the last (toward the inside is +): bows out, then hooks in
HEAD_TILT = 0.12       # his head is carried a little up (radians): the snout tilted up, as in the stills
PECTORAL_DOWN = 0.45
PECTORAL_BACK = 0.64
LEG_NAMES = [('leg_fr', 1, 'front'), ('leg_fl', -1, 'front'), ('leg_br', 1, 'back'), ('leg_bl', -1, 'back')]
UPPER = [(18, 8, 10), (16, 6, 8)]                    # the upper part's two boxes: length, thickness, fore-aft width
SHIN = [(5, 11, 10), (4, 10, 8), (3, 9, 6), (3, 7, 4), (2, 5, 2), (1, 2, 1)]   # the shin's four: thickness (in-out), length, fore-aft width
UPPER_LEN = sum(b[0] for b in UPPER)


def build_extras(jaw_tilt):
    # The crab legs: the upper part out sideways and up (two boxes, curving), the shin down and in to a point (four boxes,
    # narrowing to a claw).
    for name, s, end in LEG_NAMES:
        spec = LEGS[end]
        hip = part(name, 'trunk', (s * LEG_HIP_OUT, Yh(LEG_HIP_H), Z(spec['hip_u'])))
        x = 0.0
        parent = name
        for i, (ln, th, wd) in enumerate(UPPER):
            nm = name if i == 0 else name + '_u2'
            if i == 1:
                part(nm, parent, (s * UPPER[0][0], 0.0, 0.0))
            add_box(nm, 'leg_up', 0.0 if s > 0 else -ln, -th / 2.0, -wd / 2.0, ln, th, wd, local=True, side=s, piece=i)
        parent = name + '_u2'
        part(name + "_shin", parent, (s * UPPER[1][0], 0.0, 0.0))
        py = 0.0
        pname = name + '_shin'
        for i, (th, ln, wd) in enumerate(SHIN):
            if i > 0:
                pname = part(name + '_s%d' % (i + 1), pname, (0.0, SHIN[i - 1][1], 0.0)).name
            add_box(pname, 'leg_shin', -th / 2.0, 0.0, -wd / 2.0, th, ln, wd, local=True, side=s, piece=i)

    # The pectoral fins: down and out of his lower sides, swept back, a smaller piece at each tip.
    for name, s in (('pectoral_r', 1), ('pectoral_l', -1)):
        add_box(name, 'pect', 0.0 if s > 0 else -10, 0, -2.5, 10, 1, 5, local=True, side=s)
        part(name + '_tip', name, (s * 10.0, 0.0, 0.0))
        add_box(name + '_tip', 'pect_tip', 0.0 if s > 0 else -6, 0, -1.5, 6, 1, 3, local=True, side=s)

    # The eyes: thin boxes standing out of his side, found where the surface is at that height, painted round.
    for i, (u, h, size) in enumerate(EYES):  # each eye two boxes crossed, so it reads round
        for s in (1, -1):
            width = surface_half(u, h, size)
            depth = EYE_OUT + 1
            x = (width - 1.0) if s > 0 else -(width - 1.0) - depth
            add_box('head', 'eye', x, Yh(h) - size / 2.0, Z(u) - (size - 2) / 2.0, depth, size, size - 2, side=s, size=size, index=i, shape='tall')
            add_box('head', 'eye', x, Yh(h) - (size - 2) / 2.0, Z(u) - size / 2.0, depth, size - 2, size, side=s, size=size, index=i, shape='wide')

    # The row of dark spikes down his back: flat triangles swept back.
    for i, (u, ht, ln) in enumerate(SPIKES):
        part_name = 'trunk' if u < U_REAR else ('rear' if u < U_STALK else 'stalk')
        top = interp(TOP, u + ln / 2.0)
        add_box(part_name, 'spike', 0.0, Yh(top) - ht + 1, Z(u), 0, ht, ln, index=i)

    # The crescent tail fin, in rows from its lower tip to its upper tip, thin across.
    lead = [(91.2, 9.6), (83.2, 15.2), (77.9, 24.8), (79.5, 36.8), (92.0, 51.2)]
    trail = [(91.2, 9.6), (87.5, 16.0), (85.6, 25.3), (86.6, 36.8), (92.0, 51.2)]
    hs = [p[1] for p in lead]
    h = 10
    while h < 51:
        top = min(h + 3, 51)
        mid = (h + top) / 2.0
        a = float(np.interp(mid, hs, [p[0] for p in lead]))
        b = float(np.interp(mid, [p[1] for p in trail], [p[0] for p in trail]))
        ua, ub = int(round(a)), max(int(round(b)), int(round(a)) + 1)
        add_box('tail', 'fin', -1.0, Yh(top), Z(ua), 2, top - h, ub - ua, row=(h, top))
        h = top

    # Teeth: rows of small cream triangles painted on flat planes along the lips (the upper hanging from the roof's edge, the
    # lower rising from the jaw's), and across the front of each jaw.
    lip_h = interp(BOTTOM, NOSE)
    for s in (1, -1):
        zs = [NOSE, 22.5, 33, U_HINGE]
        for a, b in zip(zs[:-1], zs[1:]):
            ln = b - a
            hw_a, hw_b = interp(HALF, a), interp(HALF, b)
            yaw = math.atan2(hw_a - hw_b, ln) * (1 if s > 0 else -1)
            name = 'teeth_up_%s_%d' % ('r' if s > 0 else 'l', int(a))
            lip_b = interp(BOTTOM, b)
            # a part at the back end of the stretch, turned to follow the lip, the plane running forward from it
            part(name, 'head', (s * (hw_b - 1.5), Yh(lip_b) - Yh(36.0), Z(b) - Z(U_WALL)), rot=(-jaw_tilt, yaw, 0.0))
            add_box(name, 'teeth_up', 0.0, 0.0, -ln / math.cos(jaw_tilt), 0, 5, ln / math.cos(jaw_tilt), local=True, side=s)
    add_box('head', 'teeth_front_up', -(interp(HALF, NOSE) - 2.5), Yh(lip_h) , Z(NOSE) + 0.5, 2 * (interp(HALF, NOSE) - 2.5), 4, 0, local=False)
    st = np.array(JAW)
    for s in (1, -1):
        vs = [0, 10, 20, 30]
        for a, b in zip(vs[:-1], vs[1:]):
            hw_a = float(np.interp(a, st[:, 0], st[:, 1]))
            hw_b = float(np.interp(b, st[:, 0], st[:, 1]))
            yaw = math.atan2(hw_a - hw_b, b - a) * (1 if s > 0 else -1)
            name = 'teeth_low_%s_%d' % ('r' if s > 0 else 'l', a)
            part(name, 'jaw', (s * (hw_a - 1.5), 0.0, -a), rot=(0.0, yaw, 0.0))
            add_box(name, 'teeth_low', 0.0, -5.0, -(b - a), 0, 5, b - a, local=True, side=s)
    add_box('jaw', 'teeth_front_low', -3.0, -4.0, -JAW_LEN - 0.5, 6, 4, 0, local=True)


def standing(paint=False):
    """The rotations (pitch, yaw, roll) every part has when he stands still, the game's animation at rest (the jaw hanging open
    the rest angle, the legs in their stance); parts not named keep their rest rotation (the teeth's slants, nothing)."""
    tilt = math.atan2(interp(BOTTOM, NOSE) - HINGE_H, U_HINGE - NOSE)
    pose = {name: PARTS[name].rot for name in PARTS}
    pose['jaw'] = (-tilt + math.radians(OPEN_DEG), 0.0, 0.0)
    if not paint:
        pose['head'] = (-HEAD_TILT, 0.0, 0.0)
    for name, s, end in LEG_NAMES:
        pose[name] = (0.0, s * LEG_SPLAY[end], -s * (LEG_UP + LEG_BOW))
        pose[name + '_u2'] = (0.0, 0.0, s * 2 * LEG_BOW)
        pose[name + '_shin'] = (SHIN_FORE[end], 0.0, s * (LEG_UP - LEG_BOW + SHIN_IN))
        for i in range(1, len(SHIN)):
            pose[name + '_s%d' % (i + 1)] = (0.0, 0.0, s * SHIN_BEND[i - 1])
    for name, s in (('pectoral_r', 1), ('pectoral_l', -1)):
        pose[name] = (0.0, -s * PECTORAL_BACK, s * PECTORAL_DOWN)
    return pose


def rot_matrix(pitch, yaw, roll):
    """The game's order: rotationZYX(roll, yaw, pitch), Rz * Ry * Rx."""
    cx, sx, cy, sy, cz, sz = math.cos(pitch), math.sin(pitch), math.cos(yaw), math.sin(yaw), math.cos(roll), math.sin(roll)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def part_matrices(pose):
    """Each part's 4x4 matrix, its own frame to model space, for the given rotations."""
    out = {}
    for name in _ORDER:
        p = PARTS[name]
        m = np.eye(4)
        m[:3, :3] = rot_matrix(*pose[name])
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


def surface_half(u, h, size):
    """The half width of the head's side where an eye of {size} sits at (u, h): the narrowest of its rows it covers."""
    top, bot, half = sections_at(u)
    best = half
    for lo, hi, w in rows(bot, top, half, round_top=True, round_bottom=u < NOSE):
        if hi > h - size / 2.0 and lo < h + size / 2.0:
            best = min(best, w)
    return best



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
    o.append(" * The script builds him from the smooth blueprint's measured sections as slices along his body, each a short stack of")
    o.append(' * boxes narrowing toward its top and bottom, and lays the texture out for them; {@code tools/gen_chainsaw_textures.py}')
    o.append(" * ({@code beam_entity}) paints it from the same layout. The part tree, each part's pivot and its boxes (texture")
    o.append(" * corner, then the box's corner and size) are all here; {@link BeamModel} poses the parts.")
    o.append(' */')
    o.append('final class BeamLayout {')
    o.append('\tstatic final int TEXTURE_WIDTH = %d;' % TEXTURE_W)
    o.append('\tstatic final int TEXTURE_HEIGHT = %d;' % height)
    o.append('\t/** Pixels: how high the top of his back is above the soles of his feet. */')
    o.append('\tstatic final float HEIGHT = %s;' % fmt(HEIGHT))
    o.append("\t/** The lip's slope up toward the front (radians): the jaw, closed, lies along it. */")
    o.append('\tstatic final float LIP_TILT = %s;' % fmt(math.atan2(interp(BOTTOM, NOSE) - HINGE_H, U_HINGE - NOSE)))
    o.append("\t/** How the mouth hangs open, and how the legs and the pectorals stand when he is still (radians; the right-hand values, the left mirrors them). */")
    for name, val in (('MOUTH_OPEN', math.radians(OPEN_DEG)), ('LEG_UP', LEG_UP), ('LEG_BOW', LEG_BOW), ('SHIN_IN', SHIN_IN),
                      ('PECTORAL_DOWN', PECTORAL_DOWN), ('PECTORAL_BACK', PECTORAL_BACK), ('HEAD_TILT', HEAD_TILT)):
        o.append('\tstatic final float %s = %s;' % (name, fmt(val)))
    o.append('\t/** Index 0 the front pair, 1 the back pair. */')
    o.append('\tstatic final float[] LEG_SPLAY = {%s, %s};' % (fmt(LEG_SPLAY['front']), fmt(LEG_SPLAY['back'])))
    o.append('\tstatic final float[] SHIN_FORE = {%s, %s};' % (fmt(SHIN_FORE['front']), fmt(SHIN_FORE['back'])))
    o.append('\t/** Each shin piece\'s turn from the last, toward the inside. */')
    o.append('\tstatic final float[] SHIN_BEND = {%s};' % ', '.join(fmt(x) for x in SHIN_BEND))
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
    tilt = build()
    height = pack()
    if '--report' in sys.argv:
        print('lip tilt %.1f deg; %d boxes in %d parts; texture %d x %d' % (math.degrees(tilt), len(BOXES), len(PARTS), TEXTURE_W, height))
        for p in _ORDER:
            if PARTS[p].boxes:
                print('  %-16s %3d boxes' % (p, len(PARTS[p].boxes)))
    if '--java' in sys.argv:
        with open(LAYOUT_JAVA, 'w', encoding='utf-8', newline='\n') as f:
            f.write(java())
        print('wrote', LAYOUT_JAVA)


if __name__ == '__main__':
    main()
