"""The inside of the Eternity Devil: its arena (the room at the foot of the shaft) as one flesh model, and the limbs that come out of it.

Every part is voxels of 1/16 block (Minecraft's own pixel), drawn at scale 1 in the game, so walls, floor, ceiling, throat, collars, stumps,
limbs, hands and the core all share one pixel size. Nothing in the room is a block: the game's walls there are blocks nothing draws
(ArenaFleshBlock) and this is drawn over them (ArenaRenderer).

Frame (blocks): the arena's own: x along its length, z across, y up; the origin at the hotel-local point (Shaft.MID_X, Shaft.FLOOR,
Shaft.MID_Z), the middle under the mouth, on the floor. The arena is x -8.5..8.5, z -5.5..6.5, 7 high; the throat runs up from the ceiling
to the mouth at y 10; the mouth's hollow is x, z -3.5..3.5, y 10..15. The long walls (z) move in a block each in the second and third
rounds, and the end walls (x) thrust in for the squeeze: each wall is its own part, and every part is its own buffer (anim 'none').

    python tools/eternity_devil_3d.py build      # bakes this with the others (BUILDS: 'room'; 'core' at 1/16 too)
    python tools/eternity_3d_room.py sheet       # a small preview sheet of the parts (work/eternity_look_3d/room_sheet.png)
"""
import math
import os

import numpy as np

import eternity_3d_kit as K
import eternity_3d_anatomy as A
from eternity_3d_kit import ell, sphere, caps, polyline, smin, S16

X0, X1 = -8.5, 8.5
Z0, Z1 = -5.5, 6.5
H = 7.0
MOUTH_Y = 10.0
CH = 3.5
CH_Y0, CH_Y1 = 10.0, 15.0
SHAFT_HALF = 2.5
THROAT_R = 1.45

hx = K.hx
# Dark wet red flesh: crisp texels in blotches (big enough runs that the mesh stays small), never smooth.
RED = [hx(c) for c in ('#1c0708', '#2b0b0c', '#3c1012', '#4f1617', '#621e1f', '#762827', '#8a3432', '#9c433e')]
FLOOR_RED = [hx(c) for c in ('#1a0809', '#260d0d', '#341212', '#441918', '#55211f', '#672a27', '#7a3530')]
WET = hx('#c0726a')
VEIN = [hx(c) for c in ('#4e100c', '#7a1c12', '#a32a18')]
GUM = [hx(c) for c in ('#5c1c22', '#7a2a30', '#94383c', '#ab4a4c')]
MEAT = [hx(c) for c in ('#3a0405', '#5e0a0a', '#83140f')]
BONE = [hx(c) for c in ('#b8ab8c', '#d4c9aa', '#e8dfc4')]
GREY = [hx(c) for c in ('#171618', '#242223', '#353233', '#4b4745', '#605b58', '#77716c', '#8e8883', '#a49e98')]

NONE = dict(kind='none', axis=[0, 1, 0])


def surface_paint(ramp=RED, base=0.5, vein_em=0.3):
    """Blotchy flesh by material: 'flesh' (dark red, lighter where it bulges, darker in creases, a few wet highlights), 'vein' (glowing),
    'gum', 'tooth', 'meat', 'bone', 'grey' (the dead skin of the limbs)."""
    def f(ctx):
        n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 0.8, 2)
        n2 = ctx.noise(ctx.px + 3.1, ctx.py, ctx.pz, 2.3)
        t = base + (n - 0.5) * 0.9 - ctx.ao * 0.45 + 0.08 * ctx.normal[..., 1]
        col = K.pick(ramp, t, n2, jitter=0.07)
        em = np.zeros(ctx.px.shape, np.float32)
        wet = (ctx.noise(ctx.px * 1.5, ctx.py * 1.5, ctx.pz * 1.5, 2.0) > 0.88) & (ctx.ao < 0.25) & ctx.is_('flesh')
        col = np.where(wet[..., None], WET, col)
        for m in ctx.names:
            mask = ctx.is_(m)
            if m == 'vein':
                col = np.where(mask[..., None], K.pick(VEIN, 0.55 + (n2 - 0.5) * 0.8, None), col)
                em = np.where(mask, vein_em, em)
            elif m == 'gum':
                col = np.where(mask[..., None], K.pick(GUM, 0.6 + (n - 0.5) - ctx.ao * 0.4, n2, 0.2), col)
            elif m == 'tooth':
                col = np.where(mask[..., None], K.pick(K.TOOTH_YEL, 0.75 - ctx.ao * 0.7 + (n2 - 0.5) * 0.5, None), col)
            elif m == 'meat':
                col = np.where(mask[..., None], K.pick(MEAT, 0.5 + (n2 - 0.5), None), col)
                em = np.where(mask, 0.18, em)
            elif m == 'bone':
                col = np.where(mask[..., None], K.pick(BONE, 0.6 + (n2 - 0.5), None), col)
            elif m == 'grey':
                col = np.where(mask[..., None], K.pick(GREY, 0.62 + (n - 0.5) * 0.7 - ctx.ao * 0.4, n2, 0.15), col)
        return col, em
    return f


def bodies(rng, ulo, uhi, vlo, vhi, n_arms, n_heads):
    """The endless wave of the show's stomach: arms and heads fused into a surface, as (u, v) shapes half sunk in it."""
    arms = []
    for i in range(n_arms):
        u = rng.uniform(ulo, uhi)
        v = rng.uniform(vlo, vhi)
        a = rng.uniform(0, 2 * math.pi)
        pts = []
        for k in range(5):
            pts.append((u, v))
            a += rng.uniform(-0.5, 0.5)
            step = rng.uniform(0.35, 0.6)
            u += math.cos(a) * step
            v += math.sin(a) * step
        arms.append((pts, rng.uniform(0.13, 0.2)))
    heads = [((rng.uniform(ulo, uhi), rng.uniform(vlo, vhi)), rng.uniform(0.28, 0.42)) for _ in range(n_heads)]
    return arms, heads


def relief_layers(noise, u, v, w, arms, heads, amp=0.16, thick=0.9, veins=True, vein_f=0.55, vein_w=0.014, flat_top=False):
    """A wall's layers: solid where w (depth into the room) is under the surface: bulges, fused arms and heads, veins on it."""
    s = amp * (noise.fbm(u * 0.9, v * 0.9, u * 0.0 + 3.0, 1.0) - 0.5) * 2.0
    if flat_top:
        s = -0.03 - 0.12 * np.clip(1.0 - np.abs(noise.fbm(u, v, 5.0 + 0 * u, 1.3) - 0.5) * 10.0, 0.0, 1.0)
    d = w - s
    d = np.maximum(d, -(w + thick))
    if not flat_top:
        for pts, r in arms:
            p3 = [(a, b, 0.0) for a, b in pts]
            d = smin(d, polyline(u, v, w, p3, r, r * 0.75), 0.1)
        for (cu, cv), r in heads:
            d = smin(d, ell(u, v, w, (cu, cv, -0.05), (r, r * 1.2, r * 0.8)), 0.12)
    d = d + (noise.fbm(u * 3.0, v * 3.0, w * 3.0, 1.0) - 0.5) * 0.05
    layers = [('flesh', d)]
    if veins:
        rid = np.abs(noise.fbm(u * vein_f, v * vein_f, 9.0 + 0 * u, 1.0, 2) - 0.5)
        layers.append(('vein', np.maximum(d, np.maximum(rid - vein_w, -d - 0.13))))
    return layers


def wall_part(name, normal, plane, sign, ulo, uhi, vlo, vhi, seed, noise, n_arms=40, n_heads=8):
    """A planar wall: normal 'x' or 'z' (the face at {plane}, the room on the {sign} side), u along it, v up."""
    rng = np.random.default_rng(seed)
    arms, heads = bodies(rng, ulo + 0.5, uhi - 0.5, vlo + 0.3, vhi - 0.3, n_arms, n_heads)

    def fn(px, py, pz):
        if normal == 'z':
            u, w = px, sign * (pz - plane)
        else:
            u, w = pz, sign * (px - plane)
        return relief_layers(noise, u, py, w, arms, heads)
    pad = 0.6
    if normal == 'z':
        lo = (ulo, vlo, min(plane - sign * 1.0, plane + sign * pad))
        hi = (uhi, vhi, max(plane - sign * 1.0, plane + sign * pad))
    else:
        lo = (min(plane - sign * 1.0, plane + sign * pad), vlo, ulo)
        hi = (max(plane - sign * 1.0, plane + sign * pad), vhi, uhi)
    return K.voxelize(name, lo, hi, S16, fn, surface_paint(), noise, min_nb=3)


def floor_veins(rng):
    """The floor's veins (room frame x, z): branching paths over the whole floor, a block or so apart; their lines are lit by the game for
    the attacks' tells, so they are exported with the model."""
    lines = []

    def walk(x, z, a, steps, depth):
        pts = [(x, z)]
        for i in range(steps):
            a += rng.uniform(-0.45, 0.45)
            x += math.cos(a) * 0.3
            z += math.sin(a) * 0.3
            if x < X0 - 1.0 or x > X1 + 1.0 or z < Z0 - 1.0 or z > Z1 + 1.0:
                break
            pts.append((x, z))
            if depth < 2 and rng.random() < 0.07 and steps - i > 6:
                walk(x, z, a + rng.choice([-1.0, 1.0]) * rng.uniform(0.6, 1.1), (steps - i) // 2, depth + 1)
        if len(pts) > 2:
            lines.append(pts)
    # From every wall inward, and a web across the middle.
    for k in range(26):
        x = X0 + (k + 0.5) * (X1 - X0) / 26
        walk(x, Z0 - 0.6, math.pi / 2 + rng.uniform(-0.6, 0.6), 22, 0)
        walk(x + 0.3, Z1 + 0.6, -math.pi / 2 + rng.uniform(-0.6, 0.6), 22, 0)
    for k in range(16):
        z = Z0 + (k + 0.5) * (Z1 - Z0) / 16
        walk(X0 - 0.6, z, rng.uniform(-0.6, 0.6), 30, 0)
        walk(X1 + 0.6, z + 0.3, math.pi + rng.uniform(-0.6, 0.6), 30, 0)
    return lines


def floor_part(noise, lines):
    """The floor: level with the blocks' top (its walking face exactly y 0), creased, the veins in it (2 pixels wide, dim until lit)."""
    segs = []
    for pts in lines:
        for a, b in zip(pts[:-1], pts[1:]):
            segs.append((a, b))
    sa = np.array([s[0] for s in segs], np.float32)
    sb = np.array([s[1] for s in segs], np.float32)

    def fn(px, py, pz):
        layers = relief_layers(noise, px, pz, py, [], [], flat_top=True, veins=False, thick=0.5)
        d = layers[0][1]
        # Distance from each cell to the nearest vein (in x, z), done in chunks.
        best = np.full(px.shape, 9.0, np.float32)
        qx, qz = px[..., None], pz[..., None]
        for i in range(0, len(segs), 64):
            ax, az = sa[i:i + 64, 0], sa[i:i + 64, 1]
            bx, bz = sb[i:i + 64, 0], sb[i:i + 64, 1]
            vx, vz = bx - ax, bz - az
            ll = np.maximum(vx * vx + vz * vz, 1e-6)
            t = np.clip(((qx - ax) * vx + (qz - az) * vz) / ll, 0.0, 1.0)
            dx = qx - (ax + t * vx)
            dz = qz - (az + t * vz)
            best = np.minimum(best, np.sqrt(dx * dx + dz * dz).min(axis=-1))
        vein = np.maximum(d, np.maximum(best - 0.065, -py - 0.1))
        return [('flesh', d), ('vein', vein)]
    # (In slabs along x, to keep the vein search small.)
    return K.voxelize('floor', (X0 - 1.0, -0.5, Z0 - 1.0), (X1 + 1.0, 0.0, Z1 + 1.0), S16, fn, surface_paint(FLOOR_RED, 0.5, 0.22), noise,
                      min_nb=0)


def ceiling_part(noise, seed):
    rng = np.random.default_rng(seed)
    arms, heads = bodies(rng, X0, X1, Z0, Z1, 60, 10)

    def fn(px, py, pz):
        layers = relief_layers(noise, px, pz, H - py, arms, heads, amp=0.22, vein_f=0.5)
        rho = np.sqrt(px * px + pz * pz)
        out = []
        for m, d in layers:
            out.append((m, np.maximum(d, 1.6 - rho)))
        return out
    return K.voxelize('ceiling', (X0 - 1.0, H - 0.6, Z0 - 1.0), (X1 + 1.0, H + 1.0, Z1 + 1.0), S16, fn, surface_paint(), noise, min_nb=3)


def throat_part(noise, seed):
    """The throat: a wet tube up from the ceiling to the mouth, ringed like a windpipe, gums round its lower lip with teeth set in them."""
    rng = np.random.default_rng(seed)
    teeth = []
    n = 22
    for k in range(n):
        a = 2 * math.pi * (k + rng.uniform(-0.25, 0.25)) / n
        r0 = THROAT_R - 0.05
        y0 = H + 0.05 + rng.uniform(-0.05, 0.08)
        ln = rng.uniform(0.28, 0.48)
        r1 = r0 - ln * 0.6
        y1 = y0 - ln * 0.8
        teeth.append(((r0 * math.cos(a), y0, r0 * math.sin(a)), (r1 * math.cos(a), y1, r1 * math.sin(a)), rng.uniform(0.06, 0.09)))

    def fn(px, py, pz):
        rho = np.sqrt(px * px + pz * pz)
        th = np.arctan2(pz, px)
        ring = 0.07 * np.abs(np.sin((py - H) * math.pi / 0.42))
        r_in = THROAT_R - ring + 0.05 * (noise.fbm(th * 2.0, py, 0 * py + 1.0, 1.0) - 0.5) * 2
        d = np.maximum(r_in - rho, rho - (THROAT_R + 0.9))
        d = np.maximum(d, np.maximum(H - 0.25 - py, py - (MOUTH_Y + 0.3)))
        gum = np.sqrt((rho - (THROAT_R - 0.02)) ** 2 + ((py - (H + 0.12)) * 1.3) ** 2) - 0.22
        gum = gum + (noise(px * 4, py * 4, pz * 4, 1.0) - 0.5) * 0.06
        tooth = None
        for a, b, r in teeth:
            t = caps(px, py, pz, a, b, r, 0.025)
            tooth = t if tooth is None else np.minimum(tooth, t)
        rid = np.abs(noise.fbm(th * 1.6, py * 0.7, 2.0 + 0 * py, 1.0, 2) - 0.5)
        vein = np.maximum(d, np.maximum(rid - 0.03, r_in + 0.12 - rho))
        return [('flesh', np.minimum(d, gum)), ('vein', vein), ('gum', gum), ('tooth', tooth)]
    r = THROAT_R + 1.0
    return K.voxelize('throat', (-r, H - 0.6, -r), (r, MOUTH_Y + 0.4, r), S16, fn, surface_paint(base=0.56), noise, min_nb=2)


def chamber_part(noise, seed):
    """The mouth's hollow round the shaft's foot: four walls of flesh and a ring of it overhead round the shaft."""
    rng = np.random.default_rng(seed)
    arms, heads = bodies(rng, -CH, CH, CH_Y0, CH_Y1, 20, 4)

    def fn(px, py, pz):
        m = np.maximum(np.abs(px), np.abs(pz))
        u = np.where(np.abs(px) > np.abs(pz), pz, px)
        w = CH - m
        layers = relief_layers(noise, u, py, w, arms, heads, amp=0.15)
        top = np.maximum(py - (CH_Y1 + 0.6), (CH_Y1 - 0.15 + 0.1 * (noise(px, pz, py, 2.0) - 0.5)) - py)
        top = np.maximum(top, SHAFT_HALF - np.maximum(np.abs(px), np.abs(pz - 0.0)))
        top = np.maximum(top, m - (CH + 0.8))
        out = [(layers[0][0], np.minimum(layers[0][1], top))]
        out.append(('vein', layers[1][1]))
        return out
    r = CH + 0.9
    return K.voxelize('chamber', (-r, CH_Y0 - 0.5, -r), (r, CH_Y1 + 0.7, r), S16, fn, surface_paint(), noise, min_nb=3)


def collar_part(noise, name, r_out, r_in, high, seed):
    """A lip of flesh round where a limb comes out of the room (axis +y, out of the wall), lumpy, veined, its inside dark."""
    def fn(px, py, pz):
        rho = np.sqrt(px * px + pz * pz)
        th = np.arctan2(pz, px)
        lump = 1.0 + 0.12 * np.sin(5 * th + 1.3) + 0.08 * np.sin(9 * th)
        prof = high * np.clip(1.0 - ((rho - (r_in + r_out) / 2) / ((r_out - r_in) / 2)) ** 2, 0.0, 1.0) * lump
        d = np.maximum(py - prof, -py - 0.25)
        d = np.maximum(d, np.maximum(rho - r_out * lump, r_in - rho))
        d = d + (noise(px * 4, py * 4, pz * 4, 1.0) - 0.5) * 0.04
        rid = np.abs(noise.fbm(th * 1.3, rho * 2.0, 4.0 + 0 * rho, 1.0, 2) - 0.5)
        return [('flesh', d), ('vein', np.maximum(d, rid - 0.03))]
    r = r_out * 1.25
    return K.voxelize(name, (-r, -0.3, -r), (r, high + 0.1, r), S16, fn, surface_paint(base=0.62), noise, min_nb=2)


def stump_part(noise, name, radius, length):
    """A torn limb's stump out of the wall (axis +y): grey skin torn ragged at the end, red meat and a white bone, a lip of flesh at its root."""
    def fn(px, py, pz):
        rho = np.sqrt(px * px + pz * pz)
        th = np.arctan2(pz, px)
        tear = length - 0.12 * (0.5 + 0.5 * np.sin(4 * th + 0.7)) - 0.06 * (noise(px * 5, pz * 5, 0 * px + 1.0, 1.0) - 0.5)
        skin = np.maximum(rho - radius * (1.0 - 0.15 * py / length), np.maximum(-py - 0.3, py - tear))
        meat = np.maximum(rho - radius * 0.86, np.maximum(-py, py - (tear - 0.02)))
        meat_face = np.maximum(meat, (tear - 0.1) - py)
        bone = np.maximum(rho - radius * 0.3, np.maximum(-py, py - (tear + 0.06)))
        base = np.maximum(np.sqrt(rho * rho / (radius * 1.9) ** 2 + (py / 0.18) ** 2) - 1.0, -py - 0.2) * radius
        return [('grey', skin), ('flesh', base), ('meat', meat_face), ('bone', bone)]
    r = radius * 2.2
    return K.voxelize(name, (-r, -0.3, -r), (r, length + 0.15, r), S16, fn, surface_paint(base=0.6), noise, min_nb=1)


def segment_part(noise, name, radius, length=0.5):
    """A tileable piece of limb (y 0..length, axis y): grey dead skin, tendons along it and a joint swelling once a tile."""
    def fn(px, py, pz):
        th = np.arctan2(pz, px)
        t = 2 * math.pi * py / length
        r = radius * (1.0 + 0.09 * np.clip(np.sin(3 * th + 0.6), 0, 1) ** 2 + 0.07 * np.cos(t - 0.4) * (0.6 + 0.4 * np.sin(th + 1.0)))
        return [('grey', np.sqrt(px * px + pz * pz) - r)]
    r = radius * 1.4
    return K.voxelize(name, (-r, 0.0, -r), (r, length, r), S16, fn, surface_paint(), noise, keep_specks=True)


GR_WR = 0.17


def hand_part(noise, k=2.1):
    """The limbs' hand, big (the grab hand of the shaft, built k times as large so its pixels are still 1/16): the wrist at the origin,
    the fingers out along +y, the palm toward +x."""
    wr = np.zeros(3)
    fwd = np.array([0.0, 1.0, 0.0])
    tang = np.array([0.0, 0.0, 1.0])
    nrm = np.cross(fwd, tang)

    def fn(px, py, pz):
        x, y, z = px / k, py / k, pz / k
        d = caps(x, y, z, wr - np.array([0, 0.3, 0]), wr + np.array([0, 0.05, 0]), GR_WR, 0.13)
        d = np.minimum(d, sphere(x, y, z, wr + np.array([0, 0.03, 0]), 0.15))
        d = np.minimum(d, A.hand_layers(x, y, z, wr, fwd, nrm, 2.0, 1.25, curl=(5, 9, 8), floor=0.036, fthick=0.55, palm=0.9,
                                        lens=(0.44, 0.5, 0.44, 0.34)))
        d = d * k + (noise.fbm(px, py, pz, 4.0, 2) - 0.5) * 0.05
        return [('grey', d)]
    return K.voxelize('limb_hand', (-0.8 * k, -0.35 * k, -0.8 * k), (0.8 * k, 1.7 * k, 0.8 * k), S16, fn, surface_paint(), noise, keep_specks=True)


def build(seed=17):
    noise = K.Noise(23)
    rng = np.random.default_rng(seed)
    m = K.Model('eternity_room', 'the arena: x along it, z across, y up; origin the middle under the mouth, on the floor (local MID_X, FLOOR, MID_Z)')
    lines = floor_veins(rng)
    m.add(floor_part(noise, lines), anim=NONE, kind='floor')
    m.add(ceiling_part(noise, seed + 1), anim=NONE, kind='ceiling')
    m.add(throat_part(noise, seed + 2), anim=NONE, kind='throat')
    m.add(chamber_part(noise, seed + 3), anim=NONE, kind='chamber')
    m.add(wall_part('wall_x0', 'x', X0, 1, Z0 - 1.1, Z1 + 1.1, -0.4, H + 0.4, seed + 4, noise), anim=NONE, kind='wall', wall=0)
    m.add(wall_part('wall_x1', 'x', X1, -1, Z0 - 1.1, Z1 + 1.1, -0.4, H + 0.4, seed + 5, noise), anim=NONE, kind='wall', wall=1)
    m.add(wall_part('wall_z0', 'z', Z0, 1, X0 - 1.0, X1 + 1.0, -0.4, H + 0.4, seed + 6, noise, 50, 10), anim=NONE, kind='wall', wall=2)
    m.add(wall_part('wall_z1', 'z', Z1, -1, X0 - 1.0, X1 + 1.0, -0.4, H + 0.4, seed + 7, noise, 50, 10), anim=NONE, kind='wall', wall=3)
    m.add(collar_part(noise, 'collar', 0.72, 0.3, 0.28, seed + 8), anim=NONE, kind='collar')
    m.add(collar_part(noise, 'big_collar', 1.25, 0.5, 0.4, seed + 9), anim=NONE, kind='collar')
    m.add(stump_part(noise, 'stump_arm', 0.3, 0.5), anim=NONE, kind='stump')
    m.add(stump_part(noise, 'stump_head', 0.25, 0.4), anim=NONE, kind='stump')
    m.add(segment_part(noise, 'limb_segment', 0.29), anim=NONE, kind='segment')
    m.add(segment_part(noise, 'neck_segment', 0.23), anim=NONE, kind='segment')
    m.add(hand_part(noise), anim=NONE, kind='hand')
    return m, lines


class RoomModel:
    """What tools/eternity_devil_3d.py builds: the model, and the floor's veins written into its json."""


def build_for_game():
    m, lines = build()
    orig = m.write

    def write(out_dir, mesh_name, parts_name, note='', extra=None):
        ex = dict(extra or {})
        ex['floorVeins'] = [[round(v, 3) for p in pts for v in p] for pts in lines]
        return orig(out_dir, mesh_name, parts_name, note=note, extra=ex)
    m.write = write
    return m


def build_core16():
    """The core at the room's pixel size: the same sculpt as eternity_3d_core.py, voxelized at 1/16 block."""
    import eternity_3d_core as C
    C.S32 = S16
    C.S64 = S16
    return C.build()


def sheet():
    from PIL import Image
    import eternity_devil_3d as D
    m, _ = build()
    out = os.path.join(D.OUT, 'room_sheet.png')
    items = D.faces_of(m)
    cams = [K.Camera((0.0, 3.0, -4.8), (0.0, 2.5, 6.5), fov=80, size=(400, 300)),
            K.Camera((-7.5, 1.7, 5.5), (8.5, 1.2, -3.0), fov=75, size=(400, 300)),
            K.Camera((0.0, 1.0, 0.3), (0.0, 9.0, 0.0), fov=75, size=(400, 300)),
            K.Camera((0.0, 14.5, 0.2), (0.0, 0.0, 0.0), fov=70, size=(400, 300))]
    lights = [((0.3, 1.0, 0.2), (1.0, 0.85, 0.8), 0.5)]
    ims = [K.render(items, c, lights, [], ambient=0.75) for c in cams]
    w = sum(i.shape[1] for i in ims[:2])
    canvas = Image.new('RGB', (w, ims[0].shape[0] * 2))
    for i, im in enumerate(ims):
        canvas.paste(Image.fromarray(im), ((i % 2) * 400, (i // 2) * 300))
    canvas.save(out)
    print(out)


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['sheet']:
        sheet()
    else:
        m, lines = build()
        print('room: %d parts, %d voxels, %d vein lines' % (len(m.grids), sum(g.n for g in m.grids), len(lines)))
