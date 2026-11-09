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


def hole_r(th):
    """The mouth's ragged outline: a lopsided radius with deep notches (never a ring)."""
    return 0.84 * (1 + 0.27 * np.sin(3 * th + 1.0) + 0.18 * np.sin(5 * th + 2.2) + 0.11 * np.sin(8 * th + 0.5))


def polar(px, pz):
    rho = np.sqrt(px * px + pz * pz)
    th = np.arctan2(pz, px)
    return rho, th


ZONE = 0.55          # the clear ring round the mouth's ragged outline: no arms, heads, ropes or teeth inside it


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


def mass_fn(noise, lumps, ropes, eyes):
    def fn(px, py, pz):
        rho, th = polar(px, pz)
        H = mass_height(noise, px, pz)
        m = np.maximum(np.abs(px), np.abs(pz))
        H = H - 0.7 * np.clip((m - 1.45) / 0.4, 0, 1) ** 1.5
        d = np.maximum(py - H, -py)
        d = np.maximum(d, np.maximum(np.abs(px) - 1.85, np.abs(pz) - 1.85))
        for (c, r) in lumps:
            d = smin(d, ell(px, py, pz, c, r), 0.22)
        d = d + (noise.fbm(px, py, pz, 3.0) - 0.5) * 0.12
        for pts, r0, r1 in ropes:
            d = smin(d, polyline(px, py, pz, pts, r0, r1), 0.1)
        rh = hole_r(th)
        carve = np.maximum(rho - (rh + 0.2), 0.2 - py)
        d = smax(d, -carve, 0.1)
        layers = []
        # sockets for the small red eyes
        sock = []
        for c in eyes:
            sk = sphere(px, py, pz, c, 0.15)
            sock.append(sk)
            d = smax(d, -sk, 0.04)
        layers.append(('flesh', d))
        layers.append(('inner', np.maximum(d, np.maximum(carve - 0.12, py - 1.05))))     # the pit's walls
        layers.append(('inner', np.maximum(d, np.maximum(py - 0.45, rho - (rh + 0.2)))))        # the pit's floor: black
        # far below: a soft dim red light, graded in depth and radius (nested layers, falling emit 0.1..0.45): a faint glow, not a patch
        reg = np.minimum(np.maximum(d, np.maximum(carve - 0.12, py - 1.05)), np.maximum(d, np.maximum(py - 0.45, rho - (rh + 0.2))))
        dep = np.clip((1.05 - py) / 0.85, 0, 1)
        u = np.clip(rho / (rh + 0.2), 0, 1)
        gl = dep ** 2.0 * (1 - u) ** 1.6
        for lv, thr in enumerate((0.12, 0.26, 0.44, 0.66)):
            layers.append(('glow%d' % (lv + 1), np.maximum(reg, thr - gl)))
        for c, sk in zip(eyes, sock):
            layers.append(('inner', np.maximum(d, sk - 0.05)))
            layers.append(('redeye', sphere(px, py, pz, (c[0], c[1] - 0.035, c[2]), 0.075)))
        return layers
    return fn


def paint(ramp=FLESH, base=0.52, noise_f=3.0, special=None):
    def f(ctx):
        return K.standard_paint(ctx, ramp=ramp, base=base, noise_f=noise_f, special=special)
    return f


def build_mass(rng, noise):
    lumps = []
    for i in range(16):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(0.8, 1.7)
        x, z = np.clip(rr * math.cos(a), -1.6, 1.6), np.clip(rr * math.sin(a), -1.6, 1.6)
        lumps.append(((x, rng.uniform(0.85, 1.2), z), (rng.uniform(0.28, 0.5), rng.uniform(0.2, 0.34), rng.uniform(0.28, 0.5))))
    ropes = []
    for i in range(7):                      # draped ropes of flesh over the mass
        a = rng.uniform(0, 2 * math.pi)
        r0 = max(rng.uniform(0.8, 1.0), float(hole_r(np.array(a))) + ZONE + 0.1)
        r1 = max(rng.uniform(1.5, 2.1), r0 + 0.5)
        da = rng.uniform(-0.8, 0.8)
        pts = []
        for k in range(8):
            t = k / 7
            aa = a + da * t + 0.15 * math.sin(t * 9 + i)
            rr = r0 + (r1 - r0) * t
            x = np.clip(rr * math.cos(aa), -1.7, 1.7)
            z = np.clip(rr * math.sin(aa), -1.7, 1.7)
            pts.append((x, mass_height(noise, x, z) + 0.05 + 0.08 * math.sin(t * 7 + i), z))
        ropes.append((pts, rng.uniform(0.07, 0.1), rng.uniform(0.05, 0.08)))
    eyes = []
    tries = 0
    while len(eyes) < 14 and tries < 200:
        tries += 1
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(0.95, 2.0)
        x, z = rr * math.cos(a), rr * math.sin(a)
        if abs(x) > 1.6 or abs(z) > 1.6:
            continue
        rh = hole_r(np.array(a))
        if rr < rh + 0.45:
            continue
        eyes.append((x, mass_height(noise, x, z) - 0.02, z))
    fn = mass_fn(noise, lumps, ropes, eyes)
    g = K.voxelize('mass', (-1.9, 0, -1.9), (1.9, 2.0, 1.9), S8, fn, paint(base=0.4), noise, min_nb=3)
    return g, eyes


# ---------------------------------------------------------------- arms

def arm_spec(rng, ring, tall, thick=1.0, hand=1.0, reach_in=0.0, bend=None):
    """One arm: root on the mass, rising toward the players, bending at the elbow, an open palm-up hand at the top.
    reach_in > 0 leans the forearm over the mouth, < 0 away from it."""
    a = ring[0]
    rr = ring[1]
    x, z = np.clip(rr * math.cos(a), -1.62, 1.62), np.clip(rr * math.sin(a), -1.62, 1.62)
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
        rr = min(rr + max(ov, 0.08), 1.95)
        kw['reach_in'] = min(kw.get('reach_in', 0.0), 0.0) - 0.15
    return sp


def build_arm_part(name, sp, noise, scale=S16, spread=None, curl=None):
    pts = [sp['root'], sp['E'], sp['W'], sp['W'] + sp['fwd'] / np.linalg.norm(sp['fwd']) * 1.0 * sp['hand']]
    lo, hi = A.arm_bounds(pts, 0.35)
    spread = spread or 1.0

    def fn(px, py, pz):
        return [('flesh', A.arm_layers(px, py, pz, sp['root'], sp['E'], sp['W'], sp['fwd'], sp['nrm'], thick=sp['thick'],
                                       hand_size=sp['hand'], spread=spread, noise=noise, curl=curl or (10, 14, 12)))]
    return K.voxelize(name, lo, hi, scale, fn, paint(base=0.66, ramp=COOL if sp['a'] % 2 < 1 else FLESH), noise, keep_specks=True)


def build_fused_arms(rng, noise, n=24):
    """Static: many arms and hands fused into the mass, crowding the lip (1/16 block)."""
    specs = []
    for i in range(n):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(0.85, 1.9)
        rh = float(hole_r(np.array(a)))
        rr = max(rr, rh + ZONE + 0.1)
        specs.append(fit_arm(rng, a, rr, tall=rng.uniform(0.55, 0.9), thick=rng.uniform(0.8, 1.0), hand=rng.uniform(0.9, 1.15),
                             reach_in=rng.uniform(-0.5, 0.2)))

    def fn(px, py, pz):
        d = None
        for sp in specs:
            di = A.arm_layers(px, py, pz, sp['root'], sp['E'], sp['W'], sp['fwd'], sp['nrm'], thick=sp['thick'],
                              hand_size=sp['hand'], spread=1.3, noise=noise,
                              curl=(8, 12, 12))
            d = di if d is None else np.minimum(d, di)
        return [('flesh', d)]
    g = K.voxelize('fused_arms', (-1.9, 0.5, -1.9), (1.9, 2.5, 1.9), S16, fn, paint(base=0.5), noise, keep_specks=True)
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
    ramp = COOL if rng.random() < 0.7 else FLESH
    hg = K.voxelize(name, lo, hi, S16, hf, A.head_paint(F, ramp, base=0.62, special=sp), noise, keep_specks=False)
    jg = K.voxelize(name + '_jaw', lo, hi, S16, jf, A.head_paint(F, ramp, base=0.62), noise, keep_specks=False)
    return hg, jg, F


# ---------------------------------------------------------------- the lip and its teeth

def lip_fn(rng, noise):
    # a few small broken teeth, kept out of the clear ring: they stand at its outer edge, tipped away from the mouth
    t_ang = [0.5, 2.7, 3.0, 4.5, 5.7]
    tooth = []
    for a in t_ang:
        tooth.append((a + rng.uniform(-0.05, 0.05), rng.uniform(0.06, 0.09), rng.uniform(0.05, 0.1), rng.uniform(-8, 8)))

    def fn(px, py, pz):
        rho, th = polar(px, pz)
        rh = hole_r(th)
        yc = 1.18
        rc = rh + 0.17
        q = np.sqrt(((rho - rc) / 0.2) ** 2 + ((py - yc) / 0.16) ** 2) - 1.0
        d = q * 0.16 + (noise.fbm(px, py, pz, 5.0, 2) - 0.5) * 0.1
        layers = [('flesh', d)]
        # the mouth side of the lip is dark, wet
        layers.append(('inner', np.maximum(d, rho - (rh + 0.05))))
        for (a, w, h, tilt) in tooth:
            rt = float(hole_r(np.array(a))) + ZONE + 0.02
            cx, cz = rt * math.cos(a), rt * math.sin(a)
            yb = float(mass_height(noise, cx, cz)) - 0.02
            F = K.Frame((cx, yb, cz), K.facing((math.cos(a), 0, math.sin(a))), 1.0)
            lx, ly, lz = F.to_local(px, py, pz)
            # a tooth is a box standing up, tipped outward; some are snapped short
            ct, st = math.cos(math.radians(-25 + tilt)), math.sin(math.radians(-25 + tilt))
            qx, qy = lx * ct - ly * st, lx * st + ly * ct
            dt = box(qx, qy, lz, (0, h * 0.5, 0), (0.045, h * 0.5, w * 0.5), 0.0)
            layers.append(('tooth', dt))
        return layers
    return fn


def build_lip(rng, noise):
    fn = lip_fn(rng, noise)
    return K.voxelize('lip', (-2.0, 0.8, -2.0), (2.0, 1.7, 2.0), S16, fn, paint(base=0.55), noise, keep_specks=False)


# ---------------------------------------------------------------- grab arms: root, tiling segment, hand

def build_grab(rng, noise, k, a, rr):
    x, z = rr * math.cos(a), rr * math.sin(a)
    out = np.array([math.cos(a), 0, math.sin(a)])
    root = np.array([x, 0.7, z])
    top = root + np.array([0, 2.2, 0]) + out * 0.1 * rng.uniform(-1, 1)
    mid = root + np.array([0, 1.1, 0]) + out * rng.uniform(0.1, 0.3)

    def fn(px, py, pz):
        d = polyline(px, py, pz, K.curve(root, mid, top, 8), 0.125, 0.092, 0.05)
        d = d + (noise.fbm(px, py, pz, 8.0, 2) - 0.5) * 0.035
        return [('flesh', d)]
    lo = np.minimum(root, top) - 0.3
    hi = np.maximum(root, top) + 0.3
    root_g = K.voxelize('grab_%d_root' % k, lo, hi, S16, fn, paint(base=0.52), noise, keep_specks=True)
    # the hand: wrist at `top`, built around its own origin so the game can move it up the shaft
    wr = top.copy()
    fwd = np.array([0, 1.0, 0]) + out * 0.15
    nrm = np.cross(fwd, np.array([-math.sin(a), 0, math.cos(a)]))

    def hfn(px, py, pz):
        d = caps(px, py, pz, wr - np.array([0, 0.2, 0]), wr, 0.092, 0.062, 0.05)
        d = np.minimum(d, sphere(px, py, pz, wr, 0.066))
        d = np.minimum(d, A.hand_layers(px, py, pz, wr, fwd, nrm, 1.15, 1.1, curl=(6, 12, 10)))
        d = d + (noise.fbm(px, py, pz, 8.0, 2) - 0.5) * 0.03
        return [('flesh', d)]
    hg = K.voxelize('grab_%d_hand' % k, wr - 0.45, wr + np.array([0.45, 1.25, 0.45]), S16, hfn, paint(base=0.55), noise, keep_specks=True)
    return root_g, hg, root, top


def build_grab_segment(noise):
    """One tileable half-block of arm (period 0.5 in Y), centred on X = Z = 0, Y from 0 to 0.5: stack these between the root and
    the hand to reach as far up the shaft as the grab needs."""
    L = 0.5

    def fn(px, py, pz):
        d = np.sqrt(px * px + pz * pz) - (0.092 + 0.012 * np.sin(2 * math.pi * py / L * 2 + 1.0)
                                          + 0.008 * np.sin(2 * math.pi * (py / L) * 1 + 2.0 + 3 * np.arctan2(pz, px)))
        return [('flesh', d)]
    g = K.voxelize('grab_segment', (-0.2, 0.0, -0.2), (0.2, L, 0.2), S16, fn, paint(base=0.52), noise, keep_specks=True)
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
    m.add(lip, pivot=(0, 1.18, 0), anim=dict(kind='scale', axis=[0, 1, 0], amp=0.1, period=70, phase=0.0), kind='lip',
          note='the fleshy lip ring with its broken teeth: scale on the XZ plane about the pivot to flex open and shut')
    # hero arms (animated), between the heads
    ring = [(0.2 + k * 0.7 + rng.uniform(-0.1, 0.1), rng.uniform(1.0, 1.8)) for k in range(9)]
    ring += [(math.pi / 4 + k * math.pi / 2, 2.1) for k in range(4)]
    arm_i = 0
    for (a, rr) in ring:
        rr = max(rr, float(hole_r(np.array(a))) + ZONE + 0.15)
        tall = rng.uniform(1.9, 2.7) if rr < 2.0 else rng.uniform(2.2, 2.9)
        sp = fit_arm(rng, a, rr, high_ok=2.0, margin=ZONE + 0.1, tall=tall, thick=rng.uniform(0.8, 0.95), hand=rng.uniform(1.45, 1.8),
                     reach_in=rng.uniform(-0.8, 0.0))
        g = build_arm_part('arm_%02d' % arm_i, sp, noise, spread=rng.uniform(1.2, 1.6), curl=(rng.uniform(3, 8), rng.uniform(6, 12), 8))
        axis = np.cross([0, 1, 0], sp['out'])
        m.add(g, pivot=sp['root'], anim=dict(kind='sway', axis=[round(float(v), 3) for v in axis], axis2=[0, 1, 0],
                                              amp=round(float(rng.uniform(5, 9)), 1), period=int(rng.integers(55, 100)),
                                              phase=round(float(rng.random()), 2)), kind='arm')
        arm_i += 1
    # grab arms
    for k, (a, rr) in enumerate([(0.8, 1.35), (2.4, 1.3), (3.95, 1.4), (5.4, 1.35)]):
        # keep each one beyond the clear ring: slide the angle to where the mouth's outline is narrowest, then the radius out
        best = min((float(hole_r(np.array(a + dd))), dd) for dd in np.linspace(-0.5, 0.5, 21))
        a = a + best[1]
        rr = min(max(rr, best[0] + ZONE + 0.1), 1.62)
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
    for hi_, (a, rr, size) in enumerate([(0.1, 1.05, 1.25), (1.1, 1.25, 1.05), (1.85, 1.1, 1.2), (2.9, 1.2, 1.05), (3.5, 1.05, 1.25),
                                         (4.35, 1.2, 1.1), (5.1, 1.1, 1.2), (5.9, 1.3, 1.0), (2.2, 1.85, 1.0), (4.8, 1.9, 1.0)]):
        rh = float(hole_r(np.array(a)))
        rr = rh + ZONE + 0.45 * size
        seed_h = int(rng.integers(0, 10 ** 6))
        for it in range(6):                 # push the skull outward until none of it is inside the clear ring (the face tips out over it)
            x, z = rr * math.cos(a), rr * math.sin(a)
            c = np.array([np.clip(x, -1.45, 1.45), 1.0 + size * 0.38, np.clip(z, -1.45, 1.45)])
            hrng = np.random.default_rng(seed_h)
            hg, jg, F = build_head_pair('head_%d' % hi_, c, size, a, hrng, noise, eyes='red' if hi_ % 3 == 0 else 'hollow',
                                        open_deg=float(hrng.uniform(34, 46)))
            ov = max(grid_overlap(hg, ZONE - 0.05), grid_overlap(jg, ZONE - 0.05))
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
    for k in range(4):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(1.2, 1.7)
        rr = max(rr, float(hole_r(np.array(a))) + ZONE + 0.15)
        for it in range(10):
            x, z = np.clip(rr * math.cos(a), -1.55, 1.55), np.clip(rr * math.sin(a), -1.55, 1.55)
            p0 = np.array([x, 1.0, z])
            p2 = p0 + np.array([rng.uniform(-0.5, 0.5), rng.uniform(1.4, 2.0), rng.uniform(-0.5, 0.5)])
            p1 = p0 + (p2 - p0) * 0.5 + np.array([rng.uniform(-0.4, 0.4), 0, rng.uniform(-0.4, 0.4)])
            ov = zone_overlap([p0, p1, p2], ZONE + 0.1)
            if ov <= 0:
                break
            rr = min(rr + max(ov, 0.1), 1.9)
        pts = K.curve(p0, p1, p2, 10)

        def rfn(px, py, pz, pts=pts):
            return [('flesh', A.rope_layers(px, py, pz, pts, 0.1, 0.045, noise))]
        lo = np.minimum(p0, p2) - 0.45
        hi = np.maximum(p0, p2) + 0.45
        g = K.voxelize('rope_%d' % k, lo, hi, S16, rfn, paint(base=0.5), noise, keep_specks=True)
        m.add(g, pivot=p0, anim=dict(kind='sway', axis=[1, 0, 0], axis2=[0, 0, 1], amp=9.0, period=int(rng.integers(70, 120)),
                                      phase=round(float(rng.random()), 2)), kind='rope')
    return m
