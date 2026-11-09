"""The growth stages: the lone head (stage 1) and the growing mass (stage 2).

Frames (blocks): X toward the players (the faces look along +X), Y up, Z across; the origin is on the floor under the thing.
  lone head:    a severed gaunt human head about 1.9 blocks tall, hanging jaw, eyes rolled up with red irises, cords from the neck.
  growing mass: two big melted faces, three long-fingered hands pressing out of folds and ropes of flesh (about 3.4 x 2.6 x 1.8).
"""
import math

import numpy as np

import eternity_3d_kit as K
import eternity_3d_anatomy as A
import eternity_3d_shaft as SH
from eternity_3d_kit import ell, sphere, box, caps, polyline, smin, smax, S8, S16

COOL = K.FLESH_COOL
FLESH = K.FLESH


def eye_special():
    def sclera(ctx, m):
        n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 6.0)
        c = K.pick(K.SCLERA, 0.8 - 0.25 * (n - 0.5), n, 0.3)
        rid = np.abs(ctx.noise.fbm(ctx.px + 5, ctx.py * 1.5, ctx.pz, 14.0, 2) - 0.5)
        c = np.where((rid < 0.06)[..., None], K.VEIN, c)
        return c, 0.0

    def iris(ctx, m):
        n = ctx.noise(ctx.px * 3, ctx.py * 3, ctx.pz * 3, 6.0)
        return K.pick(K.IRIS_RED, 0.5 + 0.4 * (n - 0.5), n, 0.4), 0.15
    return {'sclera': sclera, 'iris_red': iris}


def build_head(seed=9):
    rng = np.random.default_rng(seed)
    noise = K.Noise(31)
    m = K.Model('eternity_head', 'X toward the players (the face), Y up, Z across; origin below the head on the floor; neck cords hang to the floor')
    S = 1.9
    F = K.Frame((0.0, 1.45, 0.0), K.facing((1, 0.22, 0), up=(0, 1, 0)), S)
    hf, jf = A.head_parts(F, rng, open_deg=52.0, gaunt=1.25, eyes='rolled', neck=True, teeth_n=8)
    lo, hi = A.head_bounds(F, neck=True)
    lo[1], hi[1] = 0.0, hi[1]
    sp = eye_special()
    paint = lambda c: K.standard_paint(c, ramp=COOL, base=0.58, vein=0.02, noise_f=2.6, special=sp)
    head = K.voxelize('head', lo, hi, S16, hf, paint, noise)
    m.add(head, pivot=F.c, kind='static')
    jaw = K.voxelize('jaw', lo, hi, S16, jf, lambda c: K.standard_paint(c, ramp=COOL, base=0.58, vein=0.02), noise)
    m.add(jaw, pivot=A.jaw_pivot(F), anim=dict(kind='hinge', axis=[round(float(v), 3) for v in A.jaw_axis(F)], amp=14.0, base=0.0,
                                               period=100, phase=0.0), kind='jaw',
          note='the hanging jaw, baked wide open (52 degrees); a positive angle closes it. Let it swing a little as the head moves')
    # cords from the neck: a few ropes that sway
    base = F.to_world((-0.22, -0.66, 0.0))
    for k in range(6):
        off = np.array([rng.uniform(-0.3, 0.3), 0, rng.uniform(-0.3, 0.3)])
        p0 = base + off
        p2 = np.array([p0[0] + rng.uniform(-0.5, 0.5), 0.05, p0[2] + rng.uniform(-0.5, 0.5)])
        p1 = (p0 + p2) / 2 + np.array([rng.uniform(-0.3, 0.3), -0.1, rng.uniform(-0.3, 0.3)])
        pts = K.curve(p0, p1, p2, 10)

        def cfn(px, py, pz, pts=pts):
            return [('flesh', A.rope_layers(px, py, pz, pts, 0.1, 0.055, noise))]
        g = K.voxelize('cord_%d' % k, np.minimum(p0, p2) - 0.4, np.maximum(p0, p2) + 0.4, S16, cfn,
                       lambda c: K.standard_paint(c, ramp=FLESH, base=0.45), noise, keep_specks=True)
        m.add(g, pivot=p0, anim=dict(kind='sway', axis=[1, 0, 0], axis2=[0, 0, 1], amp=7.0, period=int(rng.integers(80, 130)),
                                      phase=round(float(rng.random()), 2)), kind='cord')
    return m


def mass_fn(noise, lumps, ropes, folds):
    def fn(px, py, pz):
        d = None
        for c, r in lumps:
            e = ell(px, py, pz, c, r)
            d = e if d is None else smin(d, e, 0.45)
        d = d + (noise.fbm(px, py, pz, 1.8) - 0.5) * 0.6 + (noise.fbm(px, py, pz, 5.0, 2) - 0.5) * 0.16
        d = np.maximum(d, -py + 0.0)
        for pts, r0, r1 in ropes:
            d = smin(d, polyline(px, py, pz, pts, r0, r1), 0.1)
        for pts, r in folds:
            d = smax(d, -polyline(px, py, pz, pts, r, r), 0.05)       # creases cut into the skin
        return [('flesh', d)]
    return fn


def build_growing(seed=13):
    rng = np.random.default_rng(seed)
    noise = K.Noise(37)
    m = K.Model('eternity_growing', 'X toward the players, Y up, Z across; origin on the floor under the middle')
    lumps = [((0.2, 1.1, 0.0), (0.9, 1.1, 1.7)), ((0.4, 0.7, -1.0), (0.8, 0.7, 0.8)), ((0.4, 0.7, 1.0), (0.8, 0.7, 0.8)),
             ((0.3, 1.9, -0.7), (0.7, 0.7, 0.8)), ((0.3, 1.8, 0.9), (0.7, 0.6, 0.7)), ((0.7, 0.4, 0.0), (0.9, 0.4, 1.4))]
    ropes = []
    for i in range(11):
        z0 = rng.uniform(-1.7, 1.7)
        pts = [(0.7 + 0.15 * math.sin(k + i), 2.3 - 0.3 * k, z0 + 0.25 * math.sin(k * 0.9 + i)) for k in range(8)]
        ropes.append((pts, 0.15, 0.1))
    folds = []
    for i in range(6):
        y0 = rng.uniform(0.5, 2.0)
        z0 = rng.uniform(-1.6, 1.0)
        folds.append(([(1.1, y0 + 0.1 * math.sin(k), z0 + 0.35 * k) for k in range(4)], 0.09))
    mass = K.voxelize('mass', (-0.8, 0.0, -2.0), (1.8, 3.0, 2.0), S8, mass_fn(noise, lumps, ropes, folds),
                      lambda c: K.standard_paint(c, ramp=FLESH, base=0.45, noise_f=2.4), noise, min_nb=3)
    m.add(mass, pivot=(0, 0, 0), kind='static')
    # two big melted faces: stretched, sagging, mouths hanging open, eyes rolled up with red irises
    sp = eye_special()
    for i, (c, size, tilt, st) in enumerate([((1.0, 1.65, -0.45), 1.75, (1, 0.1, -0.2), 1.25), ((1.0, 0.85, 0.85), 1.25, (1, 0.25, 0.3), 1.3)]):
        F = K.Frame(np.array(c), K.facing(tilt, up=(0, 1, 0)), size)
        hf, jf = A.head_parts(F, rng, open_deg=58.0 - 6 * i, gaunt=1.2, eyes='rolled', stretch=st, teeth_n=7)
        lo, hi = A.head_bounds(F, extra=0.35)
        hg = K.voxelize('face_%d' % i, lo, hi, S16, hf, lambda cx: K.standard_paint(cx, ramp=COOL, base=0.6, vein=0.02, special=sp), noise)
        jg = K.voxelize('face_%d_jaw' % i, lo, hi, S16, jf, lambda cx: K.standard_paint(cx, ramp=COOL, base=0.6), noise)
        m.add(hg, pivot=F.c, anim=dict(kind='sway', axis=[0, 0, 1], axis2=[0, 1, 0], amp=3.0, period=120, phase=0.3 * i), kind='face')
        m.add(jg, pivot=A.jaw_pivot(F), anim=dict(kind='hinge', axis=[round(float(v), 3) for v in A.jaw_axis(F)], amp=12.0, base=0.0,
                                                   period=90 + 20 * i, phase=0.2 * i), kind='jaw', head=i,
              note='melted jaw, baked wide open; positive angle closes it')
    # three long-fingered hands pressing out of the flesh
    for i, (root, wr, fwd, nrm) in enumerate([((0.2, 2.0, 0.5), (1.15, 2.25, 0.85), (0.7, 0.55, 0.3), (0.2, 0.9, -0.3)),
                                               ((0.2, 0.5, -1.0), (1.2, 0.95, -1.15), (0.8, 0.5, -0.1), (0.2, 0.9, 0.3)),
                                               ((0.3, 1.5, 1.3), (1.3, 1.65, 1.5), (0.8, 0.4, 0.2), (0.0, 1.0, 0.0))]):
        root, wr, fwd, nrm = [np.array(v, float) for v in (root, wr, fwd, nrm)]
        E = (root + wr) / 2 + np.array([0.0, 0.25, 0.0])

        def hfn(px, py, pz, root=root, wr=wr, fwd=fwd, nrm=nrm, E=E):
            return [('flesh', A.arm_layers(px, py, pz, root, E, wr, fwd, nrm, thick=1.5, hand_size=2.3, spread=1.5, noise=noise,
                                           curl=(6, 10, 10)))]
        pts = [root, E, wr, wr + fwd / np.linalg.norm(fwd) * 1.2]
        lo, hi = A.arm_bounds(pts, 0.4)
        g = K.voxelize('hand_%d' % i, lo, hi, S16, hfn, lambda c: K.standard_paint(c, ramp=COOL if i != 1 else FLESH, base=0.64), noise,
                       keep_specks=True)
        m.add(g, pivot=root, anim=dict(kind='sway', axis=[0, 0, 1], axis2=[1, 0, 0], amp=6.0, period=int(90 + 17 * i), phase=0.3 * i),
              kind='hand')
    # swaying ropes
    for k in range(4):
        z0 = rng.uniform(-1.2, 1.2)
        p0 = np.array([0.8, 2.6, z0])
        p2 = p0 + np.array([rng.uniform(0.0, 0.6), -rng.uniform(1.0, 1.6), rng.uniform(-0.4, 0.4)])
        p1 = (p0 + p2) / 2 + np.array([0.3, 0.1, rng.uniform(-0.3, 0.3)])
        pts = K.curve(p0, p1, p2, 10)

        def rfn(px, py, pz, pts=pts):
            return [('flesh', A.rope_layers(px, py, pz, pts, 0.09, 0.045, noise))]
        g = K.voxelize('rope_%d' % k, np.minimum(p0, p2) - 0.4, np.maximum(p0, p2) + 0.4, S16, rfn,
                       lambda c: K.standard_paint(c, ramp=FLESH, base=0.45), noise, keep_specks=True)
        m.add(g, pivot=p0, anim=dict(kind='sway', axis=[0, 0, 1], axis2=[1, 0, 0], amp=8.0, period=int(rng.integers(80, 130)),
                                      phase=round(float(rng.random()), 2)), kind='rope')
    return m
