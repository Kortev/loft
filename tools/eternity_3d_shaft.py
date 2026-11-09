"""The shaft floor: the Eternity Devil's mass at the bottom of the tilted hotel corridor (the wiki's manga panel: a ragged
dark hole in a floor of hundreds of fused arms and screaming heads reaching up).

Frame (blocks): Y up the shaft, toward the players; X, Z across the 3 x 3 shaft, centred on the mouth; Y = 0 is the mass's
base. The mass's top is about Y 1.2; the mouth is in the middle; hands reach up to about Y 3.8.
"""
import math

import numpy as np

import eternity_3d_kit as K
import eternity_3d_anatomy as A
from eternity_3d_kit import ell, sphere, box, caps, polyline, smin, smax, S8, S16

FLESH = K.FLESH
COOL = K.FLESH_COOL
# a grey that is a hair warmer than blue (R/B 1.0-1.1 at every level): dead grey skin, never tan
GREY = [K.hx(c) for c in ('#171618', '#242223', '#353233', '#4b4745', '#605b58', '#77716c', '#8e8883', '#a49e98')]
PIT = [K.hx(c) for c in ('#040406', '#07070a', '#0c0c10')]          # the mouth's inside: near black, no red


def hole_r(th):
    """The mouth's ragged outline: a lopsided radius with deep notches (never a ring)."""
    return 1.15 * (1 + 0.25 * np.sin(3 * th + 1.0) + 0.16 * np.sin(5 * th + 2.2) + 0.1 * np.sin(8 * th + 0.5))


def polar(px, pz):
    rho = np.sqrt(px * px + pz * pz)
    th = np.arctan2(pz, px)
    return rho, th


ZONE = 0.5           # the clear ring round the mouth's ragged outline: no arms, heads, ropes or teeth inside it


def zone_overlap(pts, margin, ymin=0.9, ymax=99.0):
    """How far (blocks) the worst of these points is inside the clear zone (<= 0: all clear). Only points with ymin < y < ymax count."""
    worst = -9.0
    for p in pts:
        if p[1] < ymin or p[1] > ymax:
            continue
        rho, th = polar(float(p[0]), float(p[2]))
        worst = max(worst, float(hole_r(np.array(th))) + margin - rho)
    return worst


def grid_overlap(g, margin, ymin=0.9):
    idx = np.argwhere(g.solid)
    if not len(idx):
        return -9.0
    c = g.lo + (idx + 0.5) * g.scale
    c = c[c[:, 1] > ymin]
    if not len(c):
        return -9.0
    rho, th = polar(c[:, 0], c[:, 2])
    return float(np.max(hole_r(th) + margin - rho))


def mass_height(noise, x, z):
    return 1.02 + 0.22 * (noise.fbm(x, 0 * x, z, 0.9) - 0.5) * 2


BOX = 2.1            # the mass is 4.2 wide: wider than the 3 x 3 shaft, so a rim of flesh shows round the mouth even at the corners
GC = (-0.25, 0.12)   # the dim red core far down in the pit (x, z)


def mass_fn(noise, lumps, ropes, eyes):
    def fn(px, py, pz):
        rho, th = polar(px, pz)
        H = mass_height(noise, px, pz)
        m = np.maximum(np.abs(px), np.abs(pz))
        H = H - 0.7 * np.clip((m - (BOX - 0.4)) / 0.4, 0, 1) ** 1.5
        d = np.maximum(py - H, -py)
        d = np.maximum(d, np.maximum(np.abs(px) - BOX, np.abs(pz) - BOX))
        for (c, r) in lumps:
            d = smin(d, ell(px, py, pz, c, r), 0.22)
        d = d + (noise.fbm(px, py, pz, 3.0) - 0.5) * 0.12
        for pts, r0, r1 in ropes:
            d = smin(d, polyline(px, py, pz, pts, r0, r1), 0.1)
        rh = hole_r(th)
        carve = np.maximum(rho - rh, 0.2 - py)
        d = smax(d, -carve, 0.1)
        layers = []
        sock = []
        for c in eyes:
            sk = sphere(px, py, pz, c, 0.15)
            sock.append(sk)
            d = smax(d, -sk, 0.04)
        layers.append(('flesh', d))
        wall = np.maximum(d, np.maximum(carve - 0.14, py - 0.85))       # the pit's walls: black
        floor = np.maximum(d, np.maximum(py - 0.45, rho - rh))          # its floor: black
        layers.append(('inner', wall))
        layers.append(('inner', floor))
        # the lit lip: the flesh within about 0.15 block of the outline, orange-red, glowing
        layers.append(('lip_rim', np.maximum(d, np.maximum(carve - 0.16, 0.85 - py))))
        # far below: one small nested core of dim dark red, off centre (not a floor)
        dc = np.sqrt((px - GC[0]) ** 2 + (pz - GC[1]) ** 2)
        for lv, r in enumerate((0.34, 0.26, 0.18, 0.1)):
            layers.append(('glow%d' % (lv + 1), np.maximum(floor, dc - r)))
        for c, sk in zip(eyes, sock):
            layers.append(('inner', np.maximum(d, sk - 0.05)))
            layers.append(('redeye', sphere(px, py, pz, (c[0], c[1] - 0.035, c[2]), 0.075)))
        return layers
    return fn


def _lip_rim_paint(ctx, mask):
    n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 4.0)
    t = 0.55 + (n - 0.5) * 0.9 - ctx.ao * 0.3 + 0.25 * ctx.normal[..., 1]
    return K.pick(K.RIM_FLESH, t, n), 0.3 + 0.2 * np.clip(n * 1.6, 0, 1)


def _pit_paint(ctx, mask):
    n = ctx.noise(ctx.px * 2, ctx.py * 2, ctx.pz * 2, 5.0)
    return K.pick(PIT, 0.1 + n * 0.6, n), 0.0


SPECIAL = {'lip_rim': _lip_rim_paint, 'inner': _pit_paint}


def paint(ramp=GREY, base=0.52, noise_f=3.0, special=None):
    sp = dict(SPECIAL)
    if special:
        sp.update(special)

    def f(ctx):
        return K.standard_paint(ctx, ramp=ramp, base=base, noise_f=noise_f, special=sp)
    return f


def build_mass(rng, noise):
    lumps = []
    for i in range(18):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(0.8, 1.9)
        rr = max(rr, float(hole_r(np.array(a))) + 0.3)
        x, z = np.clip(rr * math.cos(a), -1.9, 1.9), np.clip(rr * math.sin(a), -1.9, 1.9)
        lumps.append(((x, rng.uniform(0.85, 1.2), z), (rng.uniform(0.3, 0.55), rng.uniform(0.2, 0.34), rng.uniform(0.3, 0.55))))
    ropes = []
    for i in range(7):                      # draped ropes of flesh over the mass
        a = rng.uniform(0, 2 * math.pi)
        r0 = float(hole_r(np.array(a))) + ZONE + 0.1
        r1 = r0 + rng.uniform(0.5, 0.9)
        da = rng.uniform(-0.8, 0.8)
        pts = []
        for k in range(8):
            t = k / 7
            aa = a + da * t + 0.15 * math.sin(t * 9 + i)
            rr = r0 + (r1 - r0) * t
            x = np.clip(rr * math.cos(aa), -2.0, 2.0)
            z = np.clip(rr * math.sin(aa), -2.0, 2.0)
            pts.append((x, mass_height(noise, x, z) + 0.05 + 0.08 * math.sin(t * 7 + i), z))
        ropes.append((pts, rng.uniform(0.09, 0.13), rng.uniform(0.06, 0.09)))
    eyes = []
    tries = 0
    while len(eyes) < 6 and tries < 300:
        tries += 1
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(1.2, 2.3)
        x, z = rr * math.cos(a), rr * math.sin(a)
        if abs(x) > 1.9 or abs(z) > 1.9:
            continue
        rh = hole_r(np.array(a))
        if rr < rh + 0.6:
            continue
        eyes.append((x, mass_height(noise, x, z) - 0.02, z))
    fn = mass_fn(noise, lumps, ropes, eyes)
    g = K.voxelize('mass', (-BOX - 0.1, 0, -BOX - 0.1), (BOX + 0.1, 2.0, BOX + 0.1), S8, fn, paint(base=0.4), noise, min_nb=3)
    return g, eyes


# ---------------------------------------------------------------- arms

def arm_spec(rng, ring, tall, thick=1.0, hand=1.0, reach_in=0.0, bend=None):
    """One arm: root on the mass, rising toward the players, bending at the elbow, an open palm-up hand at the top.
    reach_in > 0 leans the forearm over the mouth, < 0 away from it."""
    a = ring[0]
    rr = ring[1]
    x, z = np.clip(rr * math.cos(a), -1.95, 1.95), np.clip(rr * math.sin(a), -1.95, 1.95)
    root = np.array([x, 0.8, z])
    out = np.array([math.cos(a), 0.0, math.sin(a)])
    tang = np.array([-math.sin(a), 0.0, math.cos(a)])
    up = np.array([0.0, 1.0, 0.0])
    h = tall
    inward = -out * reach_in
    E = root + up * (0.55 * h) + out * 0.12 * h * rng.uniform(-1, 1) + tang * rng.uniform(-0.2, 0.2) * h
    ang = math.radians(bend if bend is not None else rng.uniform(25, 55))
    side = (inward + tang * rng.uniform(-0.6, 0.6))
    side = side / max(np.linalg.norm(side), 1e-6) if np.linalg.norm(side) > 1e-6 else tang
    fdir = up * math.cos(ang) + side * math.sin(ang)
    Wr = E + fdir * 0.45 * h
    fwd = fdir + side * rng.uniform(0.0, 0.5) + up * 0.15
    fwd = fwd / np.linalg.norm(fwd)
    roll = math.radians(rng.uniform(-45, 45))
    nrm = up - fwd * up.dot(fwd)
    nrm = nrm / np.linalg.norm(nrm)
    nrm = K.rotmat(fwd, math.degrees(roll)) @ nrm
    return dict(root=root, E=E, W=Wr, fwd=fwd, nrm=nrm, thick=thick, hand=hand, out=out, tang=tang, a=a, h=h)


def arm_pts(sp):
    f = sp['fwd'] / np.linalg.norm(sp['fwd'])
    return [sp['E'], sp['W'], sp['W'] + f * 0.5 * sp['hand'], sp['W'] + f * 1.0 * sp['hand']]


def fit_arm(rng, a, rr, high_ok=None, margin=None, **kw):
    """An arm_spec whose arm and hand stay out of the clear ring round the mouth (the root is pushed outward until they do).
    high_ok: above this height only a smaller margin is asked (a hand may lean over the mouth from beyond the ring)."""
    margin = ZONE + 0.05 if margin is None else margin
    kw = dict(kw)
    sp = None
    for it in range(14):
        x, z = rr * math.cos(a), rr * math.sin(a)
        sp = arm_spec(rng, (a, rr), **kw)
        pts = arm_pts(sp)
        if high_ok is None:
            ov = zone_overlap(pts, margin)
        else:
            ov = max(zone_overlap(pts, margin, 0.9, high_ok), zone_overlap(pts, margin - 0.3, high_ok, 99.0))
        if ov <= 0:
            return sp
        rr = min(rr + max(ov, 0.08), 2.3)
        kw['reach_in'] = min(kw.get('reach_in', 0.0), 0.0) - 0.15
    return sp


def build_arm_part(name, sp, noise, scale=S16, spread=None, curl=None):
    pts = [sp['root'], sp['E'], sp['W'], sp['W'] + sp['fwd'] / np.linalg.norm(sp['fwd']) * 1.0 * sp['hand']]
    lo, hi = A.arm_bounds(pts, 0.35)
    spread = spread or 1.0

    def fn(px, py, pz):
        return [('flesh', A.arm_layers(px, py, pz, sp['root'], sp['E'], sp['W'], sp['fwd'], sp['nrm'], thick=sp['thick'],
                                       hand_size=sp['hand'], spread=spread, noise=noise, curl=curl or (10, 14, 12)))]
    return K.voxelize(name, lo, hi, scale, fn, paint(base=0.66, ramp=GREY), noise, keep_specks=True)


def build_fused_arms(rng, noise, n=9):
    """Static: a few big arms and hands fused into the mass at the corners, kept back from the lip (1/16 block)."""
    specs = []
    for i in range(n):
        a = math.pi / 4 + (i % 4) * math.pi / 2 + rng.uniform(-0.55, 0.55)
        rr = rng.uniform(1.7, 2.1)
        specs.append(fit_arm(rng, a, rr, margin=ZONE - 0.1, tall=rng.uniform(0.8, 1.2), thick=rng.uniform(1.25, 1.6),
                             hand=rng.uniform(1.7, 2.1), reach_in=rng.uniform(-0.5, 0.0)))

    def fn(px, py, pz):
        d = None
        for sp in specs:
            di = A.arm_layers(px, py, pz, sp['root'], sp['E'], sp['W'], sp['fwd'], sp['nrm'], thick=sp['thick'],
                              hand_size=sp['hand'], spread=1.3, noise=noise, curl=(8, 12, 12))
            d = di if d is None else np.minimum(d, di)
        return [('flesh', d)]
    g = K.voxelize('fused_arms', (-BOX - 0.1, 0.5, -BOX - 0.1), (BOX + 0.1, 2.9, BOX + 0.1), S16, fn, paint(base=0.5), noise, keep_specks=True)
    return g


# ---------------------------------------------------------------- heads

def head_spec(rng, ang, rr, size, noise_hint=0):
    x, z = rr * math.cos(ang), rr * math.sin(ang)
    return dict(c=np.array([x, 1.15 + size * 0.28, z]), size=size, ang=ang)


def build_head_pair(name, c, size, ang, rng, noise, eyes='hollow', open_deg=38):
    outv = np.array([math.cos(ang), 0, math.sin(ang)])
    # the face looks up the shaft, tipped outward or inward at random; the head's "up" is away from the mouth
    tip = rng.uniform(-0.35, 0.6)
    dirv = np.array([0, 1.0, 0]) + outv * tip + np.cross([0, 1, 0], outv) * rng.uniform(-0.3, 0.3)
    R = K.facing(dirv, up=outv)
    F = K.Frame(c, R, size)
    hf, jf = A.head_parts(F, rng, open_deg=open_deg, eyes=eyes, teeth_n=7)
    lo, hi = A.head_bounds(F)
    sp = {'sclera': lambda c_, m: (K.pick(K.SCLERA, 0.8, None), 0.0)}
    ramp = GREY
    hg = K.voxelize(name, lo, hi, S16, hf, A.head_paint(F, ramp, base=0.62, special=sp), noise, keep_specks=False)
    jg = K.voxelize(name + '_jaw', lo, hi, S16, jf, A.head_paint(F, ramp, base=0.62), noise, keep_specks=False)
    return hg, jg, F


# ---------------------------------------------------------------- the lip and its teeth

def lip_fn(rng, noise):
    """The fleshy lip: a thin lopsided torus round the outline, orange-red and glowing on its mouth side. No teeth (small clutter)."""
    def fn(px, py, pz):
        rho, th = polar(px, pz)
        rh = hole_r(th)
        yc = 1.1
        rc = rh + 0.1
        q = np.sqrt(((rho - rc) / 0.15) ** 2 + ((py - yc) / 0.15) ** 2) - 1.0
        d = q * 0.15 + (noise.fbm(px, py, pz, 5.0, 2) - 0.5) * 0.08
        return [('flesh', d), ('lip_rim', np.maximum(d, rho - (rh + 0.17)))]
    return fn


def build_lip(rng, noise):
    fn = lip_fn(rng, noise)
    return K.voxelize('lip', (-BOX, 0.8, -BOX), (BOX, 1.5, BOX), S16, fn, paint(base=0.55), noise, keep_specks=False)


# ---------------------------------------------------------------- grab arms: root, tiling segment, hand

GR_SH, GR_WR = 0.2, 0.17           # grab arm radii: shoulder, and wrist/forearm (0.35-0.4 block thick; the game never scales thickness)


def grab_arm_pts(a, rr):
    """Shoulder, elbow (leaning out, a clear bend), forearm midpoint, wrist (root top)."""
    x, z = rr * math.cos(a), rr * math.sin(a)
    out = np.array([math.cos(a), 0, math.sin(a)])
    tang = np.array([-math.sin(a), 0, math.cos(a)])
    S = np.array([x, 0.7, z])
    E = S + np.array([0, 0.85, 0]) + out * 0.34 + tang * 0.08
    M = S + np.array([0, 1.55, 0]) + out * 0.17 + tang * 0.03
    T = S + np.array([0, 2.2, 0]) + out * 0.05
    return S, E, M, T, out, tang


def build_grab(rng, noise, k, a, rr):
    S, E, M, T, out, tang = grab_arm_pts(a, rr)
    root, top = S, T

    def fn(px, py, pz):
        d = caps(px, py, pz, S, E, GR_SH, 0.185)                         # upper arm
        d = np.minimum(d, sphere(px, py, pz, E, 0.205))                  # the elbow
        d = np.minimum(d, sphere(px, py, pz, E + out * 0.13 + np.array([0, 0.03, 0]), 0.1))      # its sharp bone point
        d = np.minimum(d, polyline(px, py, pz, [E, M, T], GR_WR + 0.012, GR_WR))        # forearm
        # sinew: two thin cords laid along the forearm, and a long ridge on the upper arm
        for off in (0.1, -0.09):
            cc = [E + tang * off + out * 0.06, M + tang * off + out * 0.07, T + tang * off + out * 0.05]
            d = np.minimum(d, polyline(px, py, pz, cc, 0.04, 0.033))
        d = np.minimum(d, polyline(px, py, pz, [S + out * 0.16, E + out * 0.15 - tang * 0.04], 0.045, 0.04))
        d = d + (noise.fbm(px, py, pz, 9.0, 2) - 0.5) * 0.05
        return [('flesh', d)]
    lo = np.minimum.reduce([S, E, M, T]) - 0.4
    hi = np.maximum.reduce([S, E, M, T]) + 0.4
    root_g = K.voxelize('grab_%d_root' % k, lo, hi, S16, fn, paint(base=0.5), noise, keep_specks=True)
    # the hand: wrist at `top`, built around its own origin so the game can move it up the shaft: long thin spread fingers
    wr = top.copy()
    fwd = np.array([0, 1.0, 0]) + out * 0.12
    fwd = fwd / np.linalg.norm(fwd)
    nrm = np.cross(fwd, tang)

    def hfn(px, py, pz):
        d = caps(px, py, pz, wr - np.array([0, 0.3, 0]), wr + np.array([0, 0.05, 0]), GR_WR, 0.13)
        d = np.minimum(d, sphere(px, py, pz, wr + np.array([0, 0.03, 0]), 0.15))
        d = np.minimum(d, A.hand_layers(px, py, pz, wr, fwd, nrm, 2.0, 1.25, curl=(5, 9, 8), floor=0.036, fthick=0.55, palm=0.9,
                                        lens=(0.44, 0.5, 0.44, 0.34)))
        d = d + (noise.fbm(px, py, pz, 9.0, 2) - 0.5) * 0.03
        return [('flesh', d)]
    hg = K.voxelize('grab_%d_hand' % k, wr - np.array([0.8, 0.35, 0.8]), wr + np.array([0.8, 1.7, 0.8]), S16, hfn, paint(base=0.55),
                    noise, keep_specks=True)
    return root_g, hg, root, top


def build_grab_segment(noise):
    """One tileable half-block of forearm (period 0.5 in Y), centred on X = Z = 0, Y from 0 to 0.5: stack these between the root and
    the hand to reach as far up the shaft as the grab needs. Radius 0.17 with bone knuckles, sinew ridges and a wrinkle."""
    L = 0.5

    def fn(px, py, pz):
        th = np.arctan2(pz, px)
        t = 2 * math.pi * py / L
        ridge = 0.016 * np.clip(np.sin(3 * th + 0.6), 0, 1) ** 2 + 0.012 * np.clip(np.sin(2 * th + 3.0), 0, 1) ** 2   # tendons along the arm
        knob = 0.014 * np.cos(t - 0.4) * (0.6 + 0.4 * np.sin(th + 1.0))                  # a bone joint swelling once per tile
        wrink = 0.006 * np.sin(2 * t + 2.0)
        r = GR_WR + ridge + knob + wrink
        d = np.sqrt(px * px + pz * pz) - r
        return [('flesh', d)]
    g = K.voxelize('grab_segment', (-0.3, 0.0, -0.3), (0.3, L, 0.3), S16, fn, paint(base=0.5), noise, keep_specks=True)
    return g


# ---------------------------------------------------------------- the whole shaft floor

def build(seed=7):
    rng = np.random.default_rng(seed)
    noise = K.Noise(11)
    m = K.Model('eternity_shaft', 'Y up the shaft toward the players; X, Z across; origin at the mouth, base of the mass at Y=0')
    mass, eyes = build_mass(rng, noise)
    m.add(mass, pivot=(0, 0, 0), kind='static')
    fused = build_fused_arms(rng, noise)
    m.add(fused, pivot=(0, 0, 0), kind='static')
    lip = build_lip(rng, noise)
    m.add(lip, pivot=(0, 1.1, 0), anim=dict(kind='scale', axis=[0, 1, 0], amp=0.1, period=70, phase=0.0), kind='lip',
          note='the fleshy lip ring with its broken teeth: scale on the XZ plane about the pivot to flex open and shut')
    # hero arms (animated): a few big ones, four down the sides where the rim is narrow, one in a corner
    ring = [(k * math.pi / 2 + rng.uniform(-0.25, 0.25), 0.0) for k in range(4)] + [(math.pi * 0.62, 0.0)]
    arm_i = 0
    for (a, _r) in ring:
        rr = float(hole_r(np.array(a))) + 0.3
        tall = rng.uniform(2.3, 2.9)
        sp = fit_arm(rng, a, rr, high_ok=2.0, margin=0.12, tall=tall, thick=rng.uniform(1.3, 1.5), hand=rng.uniform(2.1, 2.5),
                     reach_in=rng.uniform(-0.7, -0.15))
        g = build_arm_part('arm_%02d' % arm_i, sp, noise, spread=rng.uniform(1.2, 1.6), curl=(rng.uniform(3, 8), rng.uniform(6, 12), 8))
        axis = np.cross([0, 1, 0], sp['out'])
        m.add(g, pivot=sp['root'], anim=dict(kind='sway', axis=[round(float(v), 3) for v in axis], axis2=[0, 1, 0],
                                              amp=round(float(rng.uniform(4, 7)), 1), period=int(rng.integers(55, 100)),
                                              phase=round(float(rng.random()), 2)), kind='arm')
        arm_i += 1
    # grab arms
    for k, (a, rr) in enumerate([(1.05, 1.75), (2.1, 1.75), (4.2, 1.75), (5.25, 1.75)]):
        # in the corners, beside the corner heads, beyond the mouth's outline (radius 1.6-1.9)
        rr = min(max(rr, float(hole_r(np.array(a))) + 0.25), 1.9)
        rg, hg, root, top = build_grab(rng, noise, k, a, rr)
        m.add(rg, pivot=root, anim=dict(kind='sway', axis=[1, 0, 0], axis2=[0, 0, 1], amp=3.0, period=90, phase=0.25 * k), kind='grab_root')
        m.add(hg, pivot=top, anim=dict(kind='reach', axis=[0, 1, 0], amp=0.0, period=60, phase=0), kind='grab_hand',
              note='rest: wrist at the top of its root. Slide it up the shaft by L and stack ceil(L/0.5) grab_segment copies '
                   'between the root top and the wrist (segment origin = (x of root top, y, z of root top))',
              root_top=[round(float(v), 4) for v in top])
    seg = build_grab_segment(noise)
    m.add(seg, pivot=(0, 0, 0), kind='grab_segment', note='tileable: Y 0..0.5, centred on X=Z=0; place copies at the arm axis, one every 0.5')
    # heads
    heads = []
    for hi_, (a, rr, size) in enumerate([(0.8, 1.6, 1.6), (2.35, 1.6, 1.5), (3.95, 1.6, 1.7), (5.5, 1.6, 1.45), (4.75, 1.6, 1.4)]):
        rh = float(hole_r(np.array(a)))
        rr = rh + 0.35 + 0.3 * size
        seed_h = int(rng.integers(0, 10 ** 6))
        for it in range(10):                # push the skull outward until none of it is inside the clear ring (the face tips out over it)
            x, z = rr * math.cos(a), rr * math.sin(a)
            c = np.array([np.clip(x, -1.8, 1.8), 1.0 + size * 0.38, np.clip(z, -1.8, 1.8)])
            hrng = np.random.default_rng(seed_h)
            hg, jg, F = build_head_pair('head_%d' % hi_, c, size, a, hrng, noise, eyes='red' if hi_ % 3 == 0 else 'hollow',
                                        open_deg=float(hrng.uniform(40, 50)))
            ov = max(grid_overlap(hg, 0.08), grid_overlap(jg, 0.08))
            if ov <= 0:
                break
            rr += max(ov, 0.06)
        m.add(hg, pivot=c, anim=dict(kind='sway', axis=[round(float(v), 3) for v in np.cross([0, 1, 0], [math.cos(a), 0, math.sin(a)])],
                                      axis2=[0, 1, 0], amp=5.0, period=int(rng.integers(60, 110)), phase=round(float(rng.random()), 2)),
              kind='head')
        pj = A.jaw_pivot(F)
        ax = A.jaw_axis(F)
        m.add(jg, pivot=pj, anim=dict(kind='hinge', axis=[round(float(v), 3) for v in ax], amp=float(rng.uniform(22, 30)), base=0.0,
                                      period=int(rng.integers(50, 90)), phase=round(float(rng.random()), 2)),
              kind='jaw', head=hi_, note='baked open; a positive angle about the axis closes it')
    # swaying ropes
    for k in range(3):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(1.2, 1.7)
        rr = max(rr, float(hole_r(np.array(a))) + ZONE + 0.15)
        for it in range(10):
            x, z = np.clip(rr * math.cos(a), -1.9, 1.9), np.clip(rr * math.sin(a), -1.9, 1.9)
            p0 = np.array([x, 1.0, z])
            p2 = p0 + np.array([rng.uniform(-0.5, 0.5), rng.uniform(1.4, 2.0), rng.uniform(-0.5, 0.5)])
            p1 = p0 + (p2 - p0) * 0.5 + np.array([rng.uniform(-0.4, 0.4), 0, rng.uniform(-0.4, 0.4)])
            ov = zone_overlap([p0, p1, p2], ZONE + 0.1)
            if ov <= 0:
                break
            rr = min(rr + max(ov, 0.1), 2.2)
        pts = K.curve(p0, p1, p2, 10)

        def rfn(px, py, pz, pts=pts):
            return [('flesh', A.rope_layers(px, py, pz, pts, 0.14, 0.07, noise))]
        lo = np.minimum(p0, p2) - 0.45
        hi = np.maximum(p0, p2) + 0.45
        g = K.voxelize('rope_%d' % k, lo, hi, S16, rfn, paint(base=0.5), noise, keep_specks=True)
        m.add(g, pivot=p0, anim=dict(kind='sway', axis=[1, 0, 0], axis2=[0, 0, 1], amp=9.0, period=int(rng.integers(70, 120)),
                                      phase=round(float(rng.random()), 2)), kind='rope')
    return m
