"""Anatomy for the Eternity Devil's 3D look: gaunt arms with long-fingered hands, screaming skull heads with a jaw part,
ropes, teeth. Every function returns signed distances (negative inside) for use in kit.voxelize layers."""
import math

import numpy as np

import eternity_3d_kit as K
from eternity_3d_kit import ell, sphere, box, caps, polyline, smin, smax, rotmat


def unit(v):
    v = np.array(v, float)
    return v / np.linalg.norm(v)


# ---------------------------------------------------------------- hands and arms

def hand_layers(px, py, pz, W, fwd, nrm, size=1.0, spread=1.0, curl=(10, 14, 12), floor=0.045, thumb=True, fingers=4,
                lens=(0.25, 0.28, 0.25, 0.2)):
    """An open, long-fingered, gaunt hand at wrist W. fwd: the way the fingers point; nrm: palm normal (the way the palm faces).
    Returns one distance field (flesh)."""
    fwd, nrm = unit(fwd), unit(nrm)
    nrm = unit(nrm - fwd * nrm.dot(fwd))
    side = np.cross(nrm, fwd)               # across the palm
    s = size
    # palm: a flat rounded slab
    c = W + fwd * 0.1 * s
    dx, dy, dz = px - c[0], py - c[1], pz - c[2]
    u = dx * fwd[0] + dy * fwd[1] + dz * fwd[2]
    v = dx * side[0] + dy * side[1] + dz * side[2]
    w = dx * nrm[0] + dy * nrm[1] + dz * nrm[2]
    d = np.sqrt((u / (0.105 * s)) ** 2 + (v / (0.1 * s)) ** 2 + (w / (0.04 * s)) ** 2) - 1.0
    d = d * 0.04 * s
    base = W + fwd * 0.17 * s
    offs = np.linspace(-1.5, 1.5, fingers) if fingers == 4 else np.linspace(-1, 1, fingers)
    for i, o in enumerate(offs):
        ang = o * 11.0 * spread
        f0 = rotmat(nrm, ang) @ fwd
        B = base + side * o * 0.056 * s
        L = lens[i % len(lens)] * s
        segs = (0.45, 0.3, 0.25)
        pts = [B]
        dirv = f0
        for j, sl in enumerate(segs):
            # curl toward the palm side a little more at each joint, and a small lateral drift for a natural, uneven look
            dirv = rotmat(side, -curl[j] * (1.0 + 0.25 * math.sin(i * 1.7 + j))) @ dirv
            pts.append(pts[-1] + dirv * L * sl)
        d = np.minimum(d, polyline(px, py, pz, pts, 0.042 * s, 0.03 * s, floor))
        d = np.minimum(d, sphere(px, py, pz, pts[1], 0.047 * s))      # knuckle
    if thumb:
        f0 = rotmat(nrm, -62.0 * spread) @ fwd
        B = W + fwd * 0.06 * s - side * 0.075 * s
        t1 = B + f0 * 0.15 * s
        f1 = rotmat(side, -18) @ rotmat(nrm, 18) @ f0
        t2 = t1 + f1 * 0.14 * s
        d = np.minimum(d, polyline(px, py, pz, [B, t1, t2], 0.055 * s, 0.036 * s, floor))
    return d


def arm_layers(px, py, pz, S, E, Wr, hand_fwd, hand_nrm, thick=1.0, hand_size=1.0, spread=1.0, noise=None, curl=(10, 14, 12)):
    """A gaunt arm: shoulder S, elbow E, wrist Wr, an open hand. thick scales the limb radii."""
    t = thick
    d = caps(px, py, pz, S, E, 0.125 * t, 0.092 * t, 0.05)
    d = np.minimum(d, caps(px, py, pz, E, Wr, 0.082 * t, 0.058 * t, 0.05))
    d = np.minimum(d, sphere(px, py, pz, E, 0.1 * t))                      # the elbow's knob
    d = np.minimum(d, sphere(px, py, pz, Wr, 0.064 * t))                   # the wrist's knob
    d = np.minimum(d, hand_layers(px, py, pz, Wr, hand_fwd, hand_nrm, hand_size, spread, curl=curl))
    if noise is not None:
        d = d + (noise.fbm(px, py, pz, 9.0, 2) - 0.5) * 0.04 * t              # tendons and lumps
    return d


def arm_bounds(pts, margin):
    pts = np.array(pts, float)
    return pts.min(0) - margin, pts.max(0) + margin


# ---------------------------------------------------------------- the skull head and its jaw
#
# Local coordinates: x forward (the face), y up, z across; the head is about 1 tall: crown +0.5, chin -0.5.

HINGE = np.array([-0.06, -0.2, 0.0])
SOCK_R = 0.105                                              # the eye sockets: deep, round hollows
SOCK_C = {-1: (0.285, 0.07, -0.125), 1: (0.285, 0.07, 0.125)}


def head_paint(F, ramp, base=0.56, special=None, stretch=1.0, vein=0.012):
    """Paint for a skull head or its jaw (voxelize paint fn). Lit brows and cheekbones, and a dark ring of shadow around each hollow
    socket (so the eyes are dark and deep, never pale patches round the eyes), a dark nose hollow and temples."""
    def f(ctx):
        col, em = K.standard_paint(ctx, ramp=ramp, base=base, vein=vein, special=special)
        lx, ly, lz = F.to_local(ctx.px, ctx.py, ctx.pz)
        if stretch != 1.0:
            ly = ly / stretch
        az = np.abs(lz)
        ds = np.sqrt((lx - 0.285) ** 2 + (ly - 0.07) ** 2 + (az - 0.125) ** 2)
        ring = np.clip((ds - SOCK_R) / 0.085, 0.0, 1.0)             # 0 at the socket rim, 1 well away
        mult = 0.28 + 0.72 * ring ** 0.8
        # lit brow ridge and cheekbones
        brow = (lx > 0.2) & (ly > 0.1) & (ly < 0.2) & (ds > SOCK_R + 0.05)
        cheek = (np.sqrt((lx - 0.25) ** 2 + (ly + 0.03) ** 2 + (az - 0.19) ** 2) < 0.075) & (ds > SOCK_R + 0.04)
        lit = np.where(brow | cheek, 1.28, 1.0)
        # temples and the nose hollow stay in shadow
        temple = (az > 0.25) & (ly > 0.1) & (lx < 0.2)
        mult = mult * np.where(temple, 0.8, 1.0) * lit
        solid_flesh = ctx.is_('flesh') if 'flesh' in ctx.names else np.ones(ctx.px.shape, bool)
        mult = np.where(solid_flesh, mult, 1.0)
        return col * mult[..., None], em
    return f


def _arc_teeth(lx, ly, lz, y, n, half_h, w, depth, rng, upper, gap=1.0, breaks=0.25, sx=1.0):
    """A row of teeth along a U-shaped arc: returns (distance, ok). Uneven heights, a few broken."""
    d = None
    for i in range(n):
        t = -1 + 2 * (i + 0.5) / n
        z = t * 0.17 * sx
        x = 0.33 - 0.19 * t * t
        h = half_h * (0.55 if rng.random() < breaks else rng.uniform(0.85, 1.15))
        cy = y + (-h if upper else h) * 1.0
        dd = box(lx, ly, lz, (x, cy, z), (depth, h, w * gap), 0.0)
        d = dd if d is None else np.minimum(d, dd)
    return d


def head_parts(F, rng, open_deg=34.0, gaunt=1.0, eyes='hollow', neck=False, stretch=1.0, teeth_n=7, scream=1.0, lean=0.0):
    """Returns (head_fn, jaw_fn): each fn(px,py,pz) -> list of (material, d) in world space, for the head (everything but the
    lower jaw) and the lower jaw posed open by open_deg about HINGE (a part: it hinges there in the game)."""
    seed = int(rng.integers(0, 10 ** 6))

    def local(px, py, pz):
        lx, ly, lz = F.to_local(px, py, pz)
        if stretch != 1.0:
            ly = ly / stretch
        return lx, ly, lz

    def head_fn(px, py, pz):
        lx, ly, lz = local(px, py, pz)
        r = np.random.default_rng(seed)
        az = np.abs(lz)
        cr = ell(lx, ly, lz, (-0.04, 0.13, 0), (0.4, 0.4, 0.33))
        face = ell(lx, ly, lz, (0.12, -0.1, 0), (0.29, 0.31, 0.25 - 0.03 * gaunt))
        d = smin(cr, face, 0.12)
        # sunken cheeks and temples
        for sz in (-1, 1):
            d = smax(d, -sphere(lx, ly, lz, (0.2, -0.13, sz * 0.27), 0.12 * gaunt), 0.06)
            d = smax(d, -sphere(lx, ly, lz, (0.05, 0.2, sz * 0.31), 0.08 * gaunt), 0.05)
            d = smin(d, ell(lx, ly, lz, (0.25, -0.03, sz * 0.19), (0.1, 0.055, 0.075)), 0.04)      # cheekbone
        d = smin(d, ell(lx, ly, lz, (0.3, 0.13, 0), (0.1, 0.05, 0.27)), 0.05)                       # brow
        d = smin(d, ell(lx, ly, lz, (0.37, -0.06, 0), (0.09, 0.13, 0.04)), 0.04)                   # nose
        sock = []
        for sz in (-1, 1):
            c = SOCK_C[sz]
            sk = sphere(lx, ly, lz, c, SOCK_R)
            sock.append(sk)
            d = smax(d, -sk, 0.015)
        for sz in (-1, 1):
            d = smax(d, -ell(lx, ly, lz, (0.43, -0.15, sz * 0.025), (0.05, 0.03, 0.02)), 0.01)      # nostrils
        # the open mouth: carve the palate cavity
        cav = ell(lx, ly, lz, (0.2, -0.3, 0), (0.2, 0.11, 0.16))
        d = smax(d, -cav, 0.02)
        # upper lip line / gums: a thin rim above the teeth
        # neck stump
        if neck:
            nk = caps(lx, ly, lz, (-0.15, -0.2, 0), (-0.22, -0.5, 0), 0.19, 0.21)
            d = smin(d, nk, 0.1)
        layers = [('flesh', d * F.s)]
        # the inside of the mouth and sockets painted black
        inner_m = np.maximum(d, cav - 0.045)
        layers.append(('inner', inner_m * F.s))
        for sk in sock:
            layers.append(('inner', np.maximum(d, sk - 0.05) * F.s))
        # upper teeth
        ut = _arc_teeth(lx, ly, lz, -0.265, teeth_n, 0.045, 0.021, 0.02, r, True)
        layers.append(('tooth', ut * F.s))
        if eyes == 'red':
            for sz in (-1, 1):
                layers.append(('redeye', sphere(lx, ly, lz, (0.255, 0.065, sz * 0.125), 0.032) * F.s))
        elif eyes == 'rolled':
            for sz in (-1, 1):
                eb = sphere(lx, ly, lz, (0.27, 0.075, sz * 0.125), 0.07)
                layers.append(('sclera', eb * F.s))
                iris = np.maximum(eb, sphere(lx, ly, lz, (0.27, 0.075 + 0.06, sz * 0.125), 0.05))
                layers.append(('iris_red', iris * F.s))
        return layers

    def jaw_fn(px, py, pz):
        lx, ly, lz = local(px, py, pz)
        th = open_deg
        # the open jaw is the closed jaw rotated by -th about the hinge: sample the closed one at the inverse rotation
        c, s = math.cos(math.radians(th)), math.sin(math.radians(th))
        hx_, hy_ = HINGE[0], HINGE[1]
        qx = hx_ + (lx - hx_) * c - (ly - hy_) * s
        qy = hy_ + (lx - hx_) * s + (ly - hy_) * c
        qz = lz
        body = ell(qx, qy, qz, (0.14, -0.37, 0), (0.28, 0.09, 0.2 - 0.02 * gaunt))
        chin = ell(qx, qy, qz, (0.34, -0.4, 0), (0.1, 0.095, 0.1))
        d = smin(body, chin, 0.08)
        for sz in (-1, 1):
            ram = caps(qx, qy, qz, (0.02, -0.34, sz * 0.19), (-0.08, -0.17, sz * 0.2), 0.05, 0.04)
            d = smin(d, ram, 0.05)
        d = smax(d, -ell(qx, qy, qz, (0.2, -0.3, 0), (0.2, 0.08, 0.145)), 0.02)       # hollow behind the teeth
        r = np.random.default_rng(seed + 1)
        lt = _arc_teeth(qx, qy, qz, -0.3, teeth_n, 0.04, 0.02, 0.019, r, False, breaks=0.3)
        layers = [('flesh', d * F.s), ('inner', np.maximum(d, ell(qx, qy, qz, (0.2, -0.3, 0), (0.2, 0.08, 0.145)) - 0.04) * F.s),
                  ('tooth', lt * F.s)]
        return layers

    return head_fn, jaw_fn


def jaw_pivot(F):
    return F.to_world(HINGE)


def jaw_axis(F):
    """World axis to rotate the jaw about (positive angle closes it, as the open pose is baked in; the game uses -angle to open)."""
    return F.dir_world((0, 0, 1))


def head_bounds(F, extra=0.25, neck=False):
    c = F.c
    r = F.s * (0.78 + extra) + (0.5 * F.s if neck else 0)
    return c - r, c + r


# ---------------------------------------------------------------- ropes of flesh

def rope_layers(px, py, pz, pts, r0, r1, noise=None, lumps=0.025):
    d = polyline(px, py, pz, pts, r0, r1, 0.045)
    if noise is not None:
        d = d + (noise.fbm(px, py, pz, 11.0, 2) - 0.5) * lumps * 2
    return d
