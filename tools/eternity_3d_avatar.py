"""The corridor-wall avatar (v8_pass4, built in true 3D): the Eternity Devil's face at the corridor's end, before the tilt.

Frame (blocks): X out from the end wall toward the players (the corridor runs along +X, 3 wide in Z, 3 tall in Y), Y up from the
corridor floor, Z across, centred on the corridor. The face is a giant grin of square cream teeth with two tall slits above it,
screaming skull heads all round, ropy tendrils, grey-brown flesh, swelling out into the torn rooms to either side.
"""
import math

import numpy as np

import eternity_3d_kit as K
import eternity_3d_anatomy as A
from eternity_3d_kit import ell, sphere, box, caps, polyline, smin, smax, S8, S16

FLESH = K.FLESH
COOL = K.FLESH_COOL

YC0 = 1.3           # the grin's centre line (corners rise)
HH0 = 0.6          # half the opening at the middle
ZG = 1.5            # half the grin's width
TOOTH_W = 0.1875
TOOTH_PITCH = 0.25
JAW_HINGE = np.array([0.9, 1.0, 0.0])


def grin_yc(z):
    return YC0 + 0.3 * (z / ZG) ** 2


def grin_hh(z):
    return HH0 * (1 - 0.55 * (z / ZG) ** 2)


def body_lobes():
    return [
        ((1.45, 2.3, 0.0), (1.6, 3.0, 2.7)),
        ((1.1, 2.4, -2.9), (1.25, 2.4, 1.3)), ((1.1, 2.4, 2.9), (1.25, 2.4, 1.3)),
        ((1.2, 4.6, -1.5), (1.2, 1.1, 1.7)), ((1.2, 4.7, 1.5), (1.2, 1.1, 1.7)), ((1.0, 4.5, 0.0), (1.2, 1.2, 1.5)),
        ((1.7, 0.55, -2.2), (1.4, 0.7, 1.2)), ((1.7, 0.55, 2.2), (1.4, 0.7, 1.2)), ((1.9, 0.5, 0.0), (1.2, 0.6, 1.4)),
        ((0.9, 3.8, -3.3), (1.0, 1.8, 1.0)), ((0.9, 3.8, 3.3), (1.0, 1.8, 1.0)),
    ]


def body_raw(noise, ropes):
    lobes = body_lobes()

    def fn(px, py, pz):
        d = None
        for c, r in lobes:
            e = ell(px, py, pz, c, r)
            d = e if d is None else smin(d, e, 0.55)
        d = d + (noise.fbm(px, py, pz, 1.7) - 0.5) * 0.55 + (noise.fbm(px, py, pz, 5.0, 2) - 0.5) * 0.14
        d = np.maximum(d, -px - 0.2)            # runs into the end wall
        for pts, r0, r1 in ropes:
            d = smin(d, polyline(px, py, pz, pts, r0, r1), 0.12)
        return d
    return fn


def surface_at(fn, y, z, x_hi=4.5, step=0.02):
    """The outermost surface x at (y, z) of an SDF fn (no carving): marched from outside."""
    xs = np.arange(x_hi, 0.0, -step)
    d = fn(xs, np.full_like(xs, y), np.full_like(xs, z))
    ins = np.where(d < 0)[0]
    if not len(ins):
        return None
    return float(xs[ins[0]])


def zone_d(px, py, pz):
    """The lower jaw's region (negative inside): below the grin's lower edge, across the grin and a bit more."""
    zc = np.clip(pz, -ZG - 0.2, ZG + 0.2)
    ycut = grin_yc(zc) - grin_hh(zc) + 0.0
    return np.maximum(np.maximum(py - ycut, 0.35 - py), np.maximum(np.abs(pz) - (ZG + 0.35), 1.5 - px))


def slit_carve(px, py, pz, zs):
    """A tall vertical slit like an empty eye socket (negative inside)."""
    yc = 2.85
    w = 0.17 * (1 - 0.45 * ((py - yc) / 0.95) ** 2)
    d2 = np.maximum(np.abs(pz - zs) - w, np.abs(py - yc) - 0.95)
    return np.maximum(d2, 2.1 - px)


def grin_carve(px, py, pz):
    zc = np.clip(pz, -ZG, ZG)
    d2 = np.maximum(np.abs(py - grin_yc(zc)) - grin_hh(zc), np.abs(pz) - ZG)
    return np.maximum(d2, 1.75 - px)


def make_body_fn(noise, ropes, with_jaw):
    raw = body_raw(noise, ropes)

    def fn(px, py, pz):
        d = raw(px, py, pz)
        g = grin_carve(px, py, pz)
        d = smax(d, -g, 0.05)
        sl = [slit_carve(px, py, pz, zs) for zs in (-0.66, 0.66)]
        for s in sl:
            d = smax(d, -s, 0.04)
        zn = zone_d(px, py, pz)
        layers = []
        d_body = np.maximum(d, -zn)
        d_jaw = np.maximum(d, zn)
        dd = d_jaw if with_jaw else d_body
        layers.append(('flesh', dd))
        layers.append(('inner', np.maximum(dd, g - 0.1)))               # the mouth's inside
        for s in sl:
            layers.append(('inner', np.maximum(dd, s - 0.06)))          # inside the slits
        return layers
    return fn


def tooth_layers(px, py, pz, surf_fn, upper, rng):
    """The grin's teeth: two rows of big, square, human teeth, flat to the face."""
    out = None
    n = int(round(2 * ZG / TOOTH_PITCH))
    for i in range(n):
        z = -ZG + (i + 0.5) * TOOTH_PITCH
        yc, hh = grin_yc(z), grin_hh(z)
        xs = surf_fn(yc, z)
        if xs is None:
            continue
        L = 0.4 * (rng.uniform(0.88, 1.1) if rng.random() > 0.12 else rng.uniform(0.62, 0.78))
        w = TOOTH_W / 2
        xc = xs - 0.12
        if upper:
            y1, y0 = yc + hh + 0.06, yc + hh - L
        else:
            y0, y1 = yc - hh - 0.06, yc - hh + L
        d = box(px, py, pz, (xc, (y0 + y1) / 2, z), (0.17, (y1 - y0) / 2, w), 0.0)
        out = d if out is None else np.minimum(out, d)
    return out


def build_skull_spots(raw, rng, n_target=44):
    """Head positions on the body's surface: rejection-sampled over the face's sides, top and bottom."""
    spots = []
    tries = 0
    while len(spots) < n_target and tries < 9000:
        tries += 1
        y = rng.uniform(0.3, 5.4)
        z = rng.uniform(-3.6, 3.6)
        if abs(z) < ZG + 0.45 and 0.75 < y < 2.35:
            continue                       # the grin
        if abs(z) < 2.1 and y < 0.75:
            continue                       # the chin shelf stays clear so the lower row of teeth shows
        if abs(z) < 1.15 and 1.8 < y < 3.9:
            continue                       # the slits
        xs = surface_at(raw, y, z)
        if xs is None or xs < 0.5:
            continue
        s = rng.uniform(0.85, 1.25)
        if any(math.hypot(y - sp['y'], z - sp['z']) < 0.5 * (s + sp['s']) * 0.82 for sp in spots):
            continue
        e = 0.06
        nx = raw(np.array([xs + e]), np.array([y]), np.array([z])) - raw(np.array([xs - e]), np.array([y]), np.array([z]))
        ny = raw(np.array([xs]), np.array([y + e]), np.array([z])) - raw(np.array([xs]), np.array([y - e]), np.array([z]))
        nz = raw(np.array([xs]), np.array([y]), np.array([z + e])) - raw(np.array([xs]), np.array([y]), np.array([z - e]))
        nrm = np.array([nx[0], ny[0], nz[0]])
        nrm /= np.linalg.norm(nrm)
        spots.append(dict(x=xs, y=y, z=z, s=s, n=nrm))
    return spots


def build(seed=21):
    rng = np.random.default_rng(seed)
    noise = K.Noise(17)
    m = K.Model('eternity_avatar', 'X out from the corridor end wall toward the players, Y up from the corridor floor, Z across (centred)')
    # draped ropes on the body and the swaying tendrils
    ropes = []
    for i in range(6):
        z0 = rng.uniform(-3.0, 3.0)
        pts = [(1.9 - 0.3 * abs(z0) / 3, 5.2 - 0.2 * k, z0 + 0.3 * math.sin(k * 1.3 + i)) for k in range(8)]
        ropes.append((pts, 0.1, 0.07))
    raw = body_raw(noise, ropes)
    surf = lambda y, z: surface_at(raw, y, z)
    body_fn = make_body_fn(noise, ropes, False)
    jaw_fn = make_body_fn(noise, ropes, True)

    paint = lambda c: K.standard_paint(c, ramp=FLESH, base=0.58, noise_f=2.6)
    lo, hi = (-0.2, -0.3, -4.0), (3.4, 5.8, 4.0)
    body = K.voxelize('body', lo, hi, S8, body_fn, paint, noise, min_nb=3)
    m.add(body, pivot=(0, 0, 0), kind='static')
    jaw = K.voxelize('jaw', (1.3, 0.2, -2.1), (3.3, 2.0, 2.1), S8, jaw_fn, paint, noise, min_nb=3)
    m.add(jaw, pivot=JAW_HINGE, anim=dict(kind='hinge', axis=[0, 0, 1], amp=7.0, base=0.0, period=90, phase=0.1), kind='jaw',
          note='the grin lower jaw (flesh): baked open a little; a positive angle about Z closes it. Move teeth_lower with it')
    for upper in (True, False):
        trng = np.random.default_rng(seed + (1 if upper else 2))

        def tfn(px, py, pz, upper=upper, trng=trng):
            return [('tooth', tooth_layers(px, py, pz, surf, upper, trng))]
        tg = K.voxelize('teeth_upper' if upper else 'teeth_lower', (1.5, 0.2, -1.7), (3.5, 2.6, 1.7), S16, tfn,
                        lambda c: K.standard_paint(c, ramp=FLESH), noise, keep_specks=True)
        if upper:
            m.add(tg, pivot=(0, 0, 0), kind='static', note='the grin upper row: 12 big square cream teeth, 3 texels wide, 1 apart')
        else:
            m.add(tg, pivot=JAW_HINGE, anim=dict(kind='hinge', axis=[0, 0, 1], amp=7.0, base=0.0, period=90, phase=0.1), kind='jaw_teeth',
                  note='moves with the jaw part (same pivot, axis and angle)')
    # the lit swollen rims of the slits
    for k, zs in enumerate((-0.66, 0.66)):
        sx = surface_at(raw, 2.85, zs)
        rim_pts = []
        for t in np.linspace(0, 2 * math.pi, 22, endpoint=False):
            zz = zs + 0.34 * math.cos(t) * (0.55 + 0.45 * abs(math.sin(t)))
            yy = 2.85 + 1.1 * math.sin(t)
            xx = surface_at(raw, yy, zz) or sx
            rim_pts.append((xx - 0.1, yy, zz))
        rim_pts.append(rim_pts[0])

        def rfn(px, py, pz, pts=rim_pts):
            d = polyline(px, py, pz, pts, 0.1, 0.1, 0.06)
            d = d + (noise.fbm(px, py, pz, 7.0, 2) - 0.5) * 0.08
            return [('rim', d)]

        def rpaint(ctx):
            n = ctx.noise.fbm(ctx.px, ctx.py, ctx.pz, 6.0)
            t = 0.35 + 0.45 * (n - 0.5) * 2 + 0.3 * ctx.normal[..., 0]
            return K.pick(K.RIM_FLESH, t, n), np.full(ctx.px.shape, 0.45)
        g = K.voxelize('slit_rim_%s' % ('L' if k == 0 else 'R'), (sx - 0.7, 1.5, zs - 0.7), (sx + 0.5, 4.2, zs + 0.7), S16, rfn, rpaint, noise,
                       keep_specks=True)
        m.add(g, pivot=(sx, 2.85, zs), anim=dict(kind='scale', axis=[1, 0, 0], amp=0.04, period=60, phase=0.3 * k), kind='slit_rim',
              note='lit swollen rim, drawn glowing (emit ~0.45); pulse or swell it')
    # heads
    spots = build_skull_spots(raw, rng)
    for i, sp in enumerate(spots):
        s = sp['s']
        nrm = sp['n'] + np.array([0.7, rng.uniform(-0.25, 0.25), rng.uniform(-0.3, 0.3)])
        nrm = nrm / np.linalg.norm(nrm)
        R = K.facing(nrm, up=(0, 1, 0))
        # roll about the face direction
        c = np.array([sp['x'], sp['y'], sp['z']]) + nrm * 0.04 * s
        F = K.Frame(c, R, s)
        hf, jf = A.head_parts(F, rng, open_deg=float(rng.uniform(34, 48)), eyes='red' if rng.random() < 0.25 else 'hollow', teeth_n=7)
        lo2, hi2 = A.head_bounds(F)
        ramp = COOL if rng.random() < 0.6 else FLESH
        sp_ = {'sclera': lambda c_, mm: (K.pick(K.SCLERA, 0.8, None), 0.0)}
        hg = K.voxelize('head_%02d' % i, lo2, hi2, S16, hf, lambda cx: K.standard_paint(cx, ramp=ramp, base=0.62, special=sp_), noise)
        jg = K.voxelize('head_%02d_jaw' % i, lo2, hi2, S16, jf, lambda cx: K.standard_paint(cx, ramp=ramp, base=0.62), noise)
        ax = A.jaw_axis(F)
        m.add(hg, pivot=c, anim=dict(kind='sway', axis=[0, 0, 1], axis2=[0, 1, 0], amp=4.0, period=int(rng.integers(70, 120)),
                                      phase=round(float(rng.random()), 2)), kind='head')
        m.add(jg, pivot=A.jaw_pivot(F), anim=dict(kind='hinge', axis=[round(float(v), 3) for v in ax], amp=float(rng.uniform(18, 28)),
                                                   base=0.0, period=int(rng.integers(45, 90)), phase=round(float(rng.random()), 2)),
              kind='jaw', head=i, note='baked open; a positive angle about the axis closes it')
    # tendrils
    for k in range(16):
        z0 = rng.uniform(-3.4, 3.4)
        y0 = rng.uniform(4.0, 5.4)
        xs = surface_at(raw, y0, z0) or 1.2
        p0 = np.array([xs - 0.1, y0, z0])
        p2 = p0 + np.array([rng.uniform(0.3, 0.9), -rng.uniform(1.8, 3.2), rng.uniform(-0.5, 0.5)])
        p1 = p0 + (p2 - p0) * 0.5 + np.array([rng.uniform(0.2, 0.6), 0.2, rng.uniform(-0.4, 0.4)])
        pts = K.curve(p0, p1, p2, 10)

        def tfn(px, py, pz, pts=pts):
            return [('flesh', A.rope_layers(px, py, pz, pts, 0.12, 0.05, noise))]
        g = K.voxelize('tendril_%d' % k, np.minimum(p0, p2) - 0.4, np.maximum(p0, p2) + 0.4, S16, tfn,
                       lambda c: K.standard_paint(c, ramp=FLESH, base=0.5), noise, keep_specks=True)
        m.add(g, pivot=p0, anim=dict(kind='sway', axis=[0, 0, 1], axis2=[0, 1, 0], amp=8.0, period=int(rng.integers(70, 130)),
                                      phase=round(float(rng.random()), 2)), kind='tendril')
    return m
